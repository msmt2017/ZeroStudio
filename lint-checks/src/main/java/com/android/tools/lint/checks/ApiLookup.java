/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks;

import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_DATA;
import static com.android.SdkConstants.FD_PLATFORM_TOOLS;
import static com.android.tools.lint.checks.ApiClass.STRIP_MEMBERS;
import static com.android.tools.lint.checks.ApiClass.USE_HASH_CODES;
import static com.android.tools.lint.checks.ApiClass.USING_HASH_CODE_MASK;
import static com.android.tools.lint.detector.api.ExtensionSdk.ANDROID_SDK_ID;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.ApiConstraint;
import com.android.tools.lint.detector.api.ApiConstraint.MultiSdkApiConstraint;
import com.android.tools.lint.detector.api.ExtensionSdk;
import com.android.tools.lint.detector.api.ExtensionSdkRegistry;
import com.android.tools.lint.detector.api.JavaContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import kotlin.text.Charsets;
import kotlin.text.StringsKt;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Database for API checking: Allows quick lookup of a given class, method or field to see in which
 * API level it was introduced, and possibly deprecated and/or removed.
 *
 * <p>This class is optimized for quick bytecode lookup used in conjunction with the ASM library: It
 * has lookup methods that take internal JVM signatures, and for a method call for example it
 * processes the owner, name and description parameters separately the way they are provided from
 * ASM.
 *
 * <p>The {@link Api} class provides access to the full Android API along with version information,
 * initialized from an XML file.
 *
 * <p>When creating the memory data structure it performs a few other steps to help memory:
 *
 * <ul>
 *   <li>It strips out the method return types (which takes the binary size down from about 4.7M to
 *       4.0M)
 *   <li>It strips out all APIs that have since=1, since the lookup only needs to find classes,
 *       methods and fields that have an API level *higher* than 1. This drops the memory use down
 *       from 4.0M to 1.7M.
 * </ul>
 */
public class ApiLookup extends ApiDatabase {
    // This is really a name, not a path, but it's used externally (such as in metalava)
    // so we won't change it.
    public static final String XML_FILE_PATH = "api-versions.xml"; // relative to the SDK data/ dir

    /** Database moved from platform-tools to SDK in API level 26 */
    public static final int SDK_DATABASE_MIN_VERSION = 26;

    private static final int API_LOOKUP_BINARY_FORMAT_VERSION = 1;
    private static final int CLASS_HEADER_MEMBER_OFFSETS = 1;
    private static final int CLASS_HEADER_API = 2;
    private static final int CLASS_HEADER_DEPRECATED = 3;
    private static final int CLASS_HEADER_REMOVED = 4;
    private static final int CLASS_HEADER_INTERFACES = 5;

    @VisibleForTesting static final boolean DEBUG_FORCE_REGENERATE_BINARY = false;

    private static final Map<AndroidVersion, SoftReference<ApiLookup>> instances = new HashMap<>();

    /** The API database this lookup is based on */
    @Nullable public final File xmlFile;

    private static final String API_DATABASE_BINARY_PATH_PROPERTY =
            "android.lint.api-database-binary-path";

    static String overrideDbBinaryPath = System.getProperty(API_DATABASE_BINARY_PATH_PROPERTY);

    /**
     * Returns an instance of the API database
     *
     * @param client the client to associate with this database - used only for logging. The
     *     database object may be shared among repeated invocations, and in that case client used
     *     will be the one originally passed in. In other words, this parameter may be ignored if
     *     the client created is not new.
     * @return a (possibly shared) instance of the API database, or null if its data can't be found
     * @deprecated Use {@link #get(LintClient, IAndroidTarget)} instead, specifying an explicit SDK
     *     target to use
     */
    @Deprecated
    @Nullable
    public static ApiLookup get(@NonNull LintClient client) {
        return get(client, null);
    }

    /**
     * Returns an instance of the API database
     *
     * @param client the client to associate with this database - used only for logging. The
     *     database object may be shared among repeated invocations, and in that case client used
     *     will be the one originally passed in. In other words, this parameter may be ignored if
     *     the client created is not new.
     * @param target the corresponding Android target, if known
     * @return a (possibly shared) instance of the API database, or null if its data can't be found.
     * @throws UnsupportedVersionException if the associated API database is using an incompatible
     *     (i.e. future) format, and you need to upgrade lint to work with this version of the SDK.
     */
    @Nullable
    public static ApiLookup get(@NonNull LintClient client, @Nullable IAndroidTarget target)
            throws UnsupportedVersionException {
        synchronized (ApiLookup.class) {
            AndroidVersion version = target != null ? target.getVersion() : AndroidVersion.DEFAULT;
            SoftReference<ApiLookup> reference = instances.get(version);
            ApiLookup db = reference != null ? reference.get() : null;
            if (db == null) {
                String versionKey = null;
                // Fallbacks: Allow the API database to be read from a custom location
                String env = System.getenv("LINT_API_DATABASE");
                if (env == null) {
                    env = System.getProperty("LINT_API_DATABASE");
                }
                File file = null;
                if (env != null) {
                    file = new File(env);
                    if (!file.exists()) {
                        file = null;
                    }
                } else {
                    // Look for annotations.zip or api-versions.xml: these used to ship with the
                    // platform-tools, but were (in API 26) moved over to the API platform.
                    // Look for the most recent version, falling back to platform-tools if
                    // necessary.

                    target =
                            target != null
                                    ? target
                                    : client.getLatestSdkTarget(SDK_DATABASE_MIN_VERSION, true);
                    if (target != null) {
                        File folder = new File(target.getLocation());
                        File database = new File(folder, FD_DATA + File.separator + XML_FILE_PATH);
                        if (database.isFile()) {
                            // compileSdkVersion 26 and later
                            file = database;
                            version = target.getVersion();
                            versionKey = version.getApiStringWithExtension();
                            int revision = target.getRevision();
                            if (revision != 1) {
                                versionKey = versionKey + "rev" + revision;
                            }
                        }
                    }

                    if (file == null) {
                        // Fallback to looking in the old location: platform-tools/api/<name>
                        // under the SDK
                        // Look in platform-tools instead, where it historically was located.

                        File database =
                                new File(
                                        client.getSdkHome(),
                                        FD_PLATFORM_TOOLS
                                                + File.separator
                                                + "api"
                                                + File.separator
                                                + XML_FILE_PATH);
                        if (database.exists()) {
                            file = database;
                            // Which version of the platform is installed. Here we used to look up
                            // which exact version of platform-tools is installed, in order to
                            // properly
                            // version databases based on changes to the api-versions.xml file under
                            // platform-tools. However, doing this is actually pointless, because
                            // for
                            // several years now, the api-version.xml files have been bundled with
                            // the platform,
                            // not the platform-tools, and we have not updated the fallback
                            // api-versions.xml
                            // in platform-tools, so we might as well continue using the exact same
                            // database
                            // forever.
                            versionKey = "old-30.0.5"; // platform-versions
                        }

                        // Fallback for compatibility reasons; metalava for example
                        // locates the file by providing a custom lint client which
                        // overrides findResource to point to the right file
                        if (file == null) {
                            file = client.findResource(XML_FILE_PATH);
                        }
                    }
                }

                if (file == null) {
                    return null;
                } else {
                    if (versionKey == null) {
                        // We don't know the version for files coming out of a platform
                        // build or a custom file pointed to by an environment variable;
                        // in that case just use a hash of the path and the timestamp to
                        // make sure the database is updated if the file changes (and the
                        // path has to prevent the unlikely scenario of two different
                        // databases having the same timestamp
                        HashFunction hashFunction = Hashing.farmHashFingerprint64();
                        //noinspection UnstableApiUsage
                        versionKey =
                                hashFunction
                                        .newHasher()
                                        .putLong(file.lastModified())
                                        .putString(file.getPath(), Charsets.UTF_8)
                                        .hash()
                                        .toString();
                    }

                    try {
                        db = get(client, file, versionKey);
                    } catch (UnsupportedVersionException e) {
                        e.target = target;
                        // only throw the first time
                        ApiLookup none = new ApiLookup(client, null, null);
                        instances.put(version, new SoftReference<>(none));
                        throw e;
                    }
                }
                instances.put(version, new SoftReference<>(db));
            } else if (db.mData == null) {
                return null;
            }

            return db;
        }
    }

    /**
     * Like {@link #get(LintClient, IAndroidTarget)}, but does not throw {@link
     * UnsupportedVersionException} for incompatible files; instead it logs a warning to the log and
     * returns null.
     */
    @Nullable
    public static ApiLookup getOrNull(@NonNull LintClient client, @Nullable IAndroidTarget target) {
        try {
            return get(client, target);
        } catch (UnsupportedVersionException e) {
            client.log(null, e.getDisplayMessage(client));
            return null;
        }
    }

    @VisibleForTesting
    @NonNull
    static String getCacheFileName(@NonNull String xmlFileName, @NonNull String platformVersion) {
        // Incorporate version number in the filename to avoid upgrade filename
        // conflicts on Windows (such as issue #26663)
        return StringsKt.removeSuffix(xmlFileName, DOT_XML)
                + '-'
                + getBinaryFormatVersion(API_LOOKUP_BINARY_FORMAT_VERSION)
                + '-'
                + platformVersion.replace(' ', '_')
                + ".bin";
    }

    /**
     * Returns an instance of the API database
     *
     * @param client the client to associate with this database - used only for logging
     * @param xmlFile the XML file containing configuration data to use for this database
     * @param version the version of the Android platform this API database is associated with
     * @return a (possibly shared) instance of the API database, or null if its data can't be found
     * @throws UnsupportedVersionException if the associated API database is using an incompatible
     *     (i.e. future) format, and you need to upgrade lint to work with this version of the SDK.
     */
    private static ApiLookup get(
            @NonNull LintClient client, @NonNull File xmlFile, @NonNull String version)
            throws UnsupportedVersionException {
        if (!xmlFile.exists()) {
            client.log(null, "The API database file %1$s does not exist", xmlFile);
            return null;
        }

        if (overrideDbBinaryPath != null) {
            File binaryData = new File(overrideDbBinaryPath);
            if (!binaryData.isFile()) {
                throw new IllegalArgumentException(
                        String.format(
                                Locale.US,
                                "API database binary file specified by system property %s not"
                                        + " found: %s",
                                API_DATABASE_BINARY_PATH_PROPERTY,
                                binaryData));
            }
            return new ApiLookup(client, null, binaryData);
        }
        File cacheDir = client.getCacheDir(null, true);
        if (cacheDir == null) {
            cacheDir = xmlFile.getParentFile();
        }
        File binaryData = new File(cacheDir, getCacheFileName(xmlFile.getName(), version));

        if (DEBUG_FORCE_REGENERATE_BINARY) {
            System.err.println(
                    "\nTemporarily regenerating binary data unconditionally \nfrom "
                            + xmlFile
                            + "\nto "
                            + binaryData);
            if (!cacheCreator(xmlFile).create(client, binaryData)) {
                return null;
            }
        } else if (!binaryData.exists()
                || binaryData.lastModified() < xmlFile.lastModified()
                || binaryData.length() == 0) {
            if (!cacheCreator(xmlFile).create(client, binaryData)) {
                return null;
            }
        }

        if (!binaryData.exists()) {
            client.log(null, "The API database file %1$s does not exist", binaryData);
            return null;
        }

        return new ApiLookup(client, xmlFile, binaryData);
    }

    private static CacheCreator cacheCreator(File xmlFile) throws UnsupportedVersionException {
        return (client, binaryData) -> {
            long begin = WRITE_STATS ? System.currentTimeMillis() : 0;

            Api<ApiClass> info;
            try {
                byte[] bytes = client.readBytes(xmlFile);
                info = Api.parseApi(new ByteArrayInputStream(bytes));
            } catch (UnsupportedVersionException e) {
                // Don't pass through to the regular RuntimeException logging below
                e.apiFile = xmlFile;
                throw e;
            } catch (RuntimeException | IOException e) {
                client.log(e, "Can't read API file " + xmlFile.getAbsolutePath());
                return false;
            }

            if (WRITE_STATS) {
                long end = System.currentTimeMillis();
                System.out.println("Reading XML data structures took " + (end - begin) + " ms");
            }

            try {
                writeDatabase(client, binaryData, info, API_LOOKUP_BINARY_FORMAT_VERSION, xmlFile);
                return true;
            } catch (IOException e) {
                client.log(e, "Can't write API cache file");
            }

            return false;
        };
    }

    /** Use one of the {@link #get} factory methods instead. */
    ApiLookup(@NonNull LintClient client, @Nullable File xmlFile, @Nullable File binaryFile) {
        this.xmlFile = xmlFile;
        if (binaryFile != null) {
            readData(
                    client,
                    binaryFile,
                    xmlFile != null ? cacheCreator(xmlFile) : null,
                    API_LOOKUP_BINARY_FORMAT_VERSION);
            initializeApiConstraints();
        }
    }

    private final List<ApiConstraint> apiConstraints = new ArrayList<>();
    private ExtensionSdkRegistry registry;

    private void initializeApiConstraints() {
        if (mData == null) {
            return;
        }
        int offset = sdkIndexOffset;
        int count = get2ByteInt(mData, offset);
        offset += 2;
        for (int i = 0; i < count; i++) {
            int first = get4ByteInt(mData, offset);
            offset += 4;
            int second = get4ByteInt(mData, offset);
            offset += 4;

            if (second == -1) {
                // API level only
                // Already a packed version code
                apiConstraints.add(getApiConstraint(first, ANDROID_SDK_ID));
            } else {
                List<ApiConstraint.SdkApiConstraint> apis = new ArrayList<>();
                apis.add(getApiConstraint(second, first));

                while (true) {
                    int sdk = get4ByteInt(mData, offset);
                    offset += 4;
                    if (sdk == -1) {
                        break;
                    }

                    int version = get4ByteInt(mData, offset);
                    offset += 4;
                    assert (version != -1);
                    apis.add(getApiConstraint(version, sdk));
                }

                apiConstraints.add(MultiSdkApiConstraint.Companion.create(apis, true));
            }
        }

        // Read ExtensionSdk table
        int extensionCount = get4ByteInt(mData, offset);
        offset += 4;
        List<ExtensionSdk> sdks = new ArrayList<>();
        for (int i = 0; i < extensionCount; i++) {
            int length = get4ByteInt(mData, offset);
            offset += 4;
            String s = new String(mData, offset, length, Charsets.UTF_8);
            offset += length;
            sdks.add(ExtensionSdk.Companion.deserialize(s));
        }
        registry = new ExtensionSdkRegistry(sdks);
    }

    private static ApiConstraint.SdkApiConstraint getApiConstraint(int versionInt, int sdkId) {
        int major = ApiParser.getMajorVersion(versionInt);
        int minor = ApiParser.getMinorVersion(versionInt);
        return ApiConstraint.atLeast(major, minor, sdkId);
    }

    /**
     * @deprecated Use {@link #getClassVersions} instead to properly handle APIs which can now
     *     appear in multiple SDK extensions.
     */
    @Deprecated
    public int getClassVersion(@NonNull String className) {
        return getClassVersions(className).min();
    }

    /**
     * Returns the API version required by the given class reference, or -1 if this is not a known
     * API class. Note that it may return -1 for classes introduced in version 1; internally the
     * database only stores version data for version 2 and up.
     *
     * @param className the internal name of the class, e.g. its fully qualified name (as returned
     *     by Class.getName())
     * @return the minimum API version the method is supported for, or -1 if it's unknown <b>or
     *     version 1</b>
     */
    @NonNull
    public ApiConstraint getClassVersions(@NonNull String className) {
        if (mData != null) {
            return getClassVersions(findClass(className));
        }

        return ApiConstraint.UNKNOWN;
    }

    private ApiConstraint getClassVersions(int classNumber) {
        if (classNumber >= 0) {
            int offset = seekClassData(classNumber, CLASS_HEADER_API);
            int api = getApiLevel(offset, CLASS_HEADER_API);
            if (api > 0) {
                return apiConstraints.get(api);
            }
        }
        return ApiConstraint.UNKNOWN;
    }

    /**
     * @deprecated Use {@link #getClassVersions} instead to properly handle APIs which can now
     *     appear in multiple SDK extensions.
     */
    @Deprecated
    public int getValidCastVersion(@NonNull String sourceClass, @NonNull String destinationClass) {
        return getValidCastVersions(sourceClass, destinationClass).min();
    }

    /**
     * Returns the API version required to perform the given cast, or -1 if this is valid for all
     * versions of the class (or, if these are not known classes or if the cast is not valid at
     * all.)
     *
     * <p>Note also that this method should only be called for interfaces that are actually
     * implemented by this class or extending the given super class (check elsewhere); it doesn't
     * distinguish between interfaces implemented in the initial version of the class and interfaces
     * not implemented at all.
     *
     * @param sourceClass the internal name of the class, e.g. its fully qualified name (as returned
     *     by Class.getName())
     * @param destinationClass the class to cast the sourceClass to
     * @return the minimum API version the method is supported for, or 1 or -1 if it's unknown
     */
    @NonNull
    public ApiConstraint getValidCastVersions(
            @NonNull String sourceClass, @NonNull String destinationClass) {
        if (mData != null) {
            int classNumber = findClass(sourceClass);
            if (classNumber >= 0) {
                int interfaceNumber = findClass(destinationClass);
                if (interfaceNumber >= 0) {
                    int offset = seekClassData(classNumber, CLASS_HEADER_INTERFACES);
                    int interfaceCount = mData[offset++];
                    for (int i = 0; i < interfaceCount; i++) {
                        int clsNumber = get3ByteInt(mData, offset);
                        offset += 3;
                        int api = mData[offset++];
                        if (clsNumber == interfaceNumber) {
                            return apiConstraints.get(api);
                        }
                    }
                    return getClassVersions(classNumber);
                }
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * @deprecated Use {@link #getClassDeprecatedInVersions} instead to properly handle APIs which
     *     can now appear in multiple SDK extensions.
     */
    @Deprecated
    public int getClassDeprecatedIn(@NonNull String className) {
        ApiConstraint versions = getClassDeprecatedInVersions(className);
        if (versions != ApiConstraint.UNKNOWN) {
            return versions.min();
        } else {
            return -1;
        }
    }

    /**
     * Returns the API version the given class was deprecated in, or -1 if the class is not
     * deprecated.
     *
     * @param className the internal name of the method's owner class, e.g. its fully qualified name
     *     (as returned by Class.getName())
     * @return the API version the API was deprecated in, or -1 if it's unknown <b>or version 0</b>
     */
    @NonNull
    public ApiConstraint getClassDeprecatedInVersions(@NonNull String className) {
        if (mData != null) {
            int classNumber = findClass(className);
            if (classNumber >= 0) {
                int offset = seekClassData(classNumber, CLASS_HEADER_DEPRECATED);
                if (offset < 0) {
                    // Not deprecated
                    return ApiConstraint.UNKNOWN;
                }
                int deprecatedIn = Byte.toUnsignedInt(mData[offset]) & API_MASK;

                return deprecatedIn > 0 ? apiConstraints.get(deprecatedIn) : ApiConstraint.UNKNOWN;
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * @deprecated Use {@link #getClassRemovedInVersions} instead to properly handle APIs which can
     *     now appear in multiple SDK extensions.
     */
    @Deprecated
    public int getClassRemovedIn(@NonNull String className) {
        ApiConstraint versions = getClassRemovedInVersions(className);
        if (versions != ApiConstraint.UNKNOWN) {
            return versions.min();
        } else {
            return -1;
        }
    }

    /**
     * Returns the API version the given class was removed in, or -1 if the class was not removed.
     *
     * @param className the internal name of the method's owner class, e.g. its fully qualified name
     *     (as returned by Class.getName())
     * @return the API version the API was removed in, or -1 if it's unknown <b>or version 0</b>
     */
    @NonNull
    public ApiConstraint getClassRemovedInVersions(@NonNull String className) {
        if (mData != null) {
            int classNumber = findClass(className);
            if (classNumber >= 0) {
                int offset = seekClassData(classNumber, CLASS_HEADER_REMOVED);
                if (offset < 0) {
                    // Not removed
                    return ApiConstraint.UNKNOWN;
                }
                int removedIn = Byte.toUnsignedInt(mData[offset]) & API_MASK;
                return removedIn > 0 ? apiConstraints.get(removedIn) : ApiConstraint.UNKNOWN;
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * Returns true if the given owner class is known in the API database.
     *
     * @param className the internal name of the class, e.g. its fully qualified name (as returned
     *     by Class.getName(), but with '.' replaced by '/' (and '$' for inner classes)
     * @return true if this is a class found in the API database
     */
    public boolean containsClass(@NonNull String className) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            return findClass(className) >= 0;
        }

        return false;
    }

    /**
     * @deprecated Use {@link #getClassVersions} instead to properly handle APIs which can now
     *     appear in multiple SDK extensions.
     */
    @Deprecated
    public int getMethodVersion(@NonNull String owner, @NonNull String name, @NonNull String desc) {
        return getMethodVersions(owner, name, desc).min();
    }

    /**
     * Returns the API version required by the given method call. The method is referred to by its
     * {@code owner}, {@code name} and {@code desc} fields. If the method is unknown it returns -1.
     * Note that it may return -1 for classes introduced in version 1; internally the database only
     * stores version data for version 2 and up.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @param desc the method's descriptor - see {@link org.objectweb.asm.Type}
     * @return the minimum API version the method is supported for, or -1 if it's unknown
     */
    @NonNull
    public ApiConstraint getMethodVersions(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int api = findMember(classNumber, name, desc);
                if (api < 0) {
                    if (STRIP_MEMBERS) {
                        return getClassVersions(classNumber);
                    } else {
                        return ApiConstraint.UNKNOWN;
                    }
                }
                return apiConstraints.get(api);
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * @deprecated Use {@link #getClassDeprecatedInVersions} instead to properly handle APIs which
     *     can now appear in multiple SDK extensions.
     */
    @Deprecated
    public int getMethodDeprecatedIn(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        ApiConstraint versions = getMethodDeprecatedInVersions(owner, name, desc);
        if (versions != ApiConstraint.UNKNOWN) {
            return versions.min();
        } else {
            return -1;
        }
    }

    /**
     * Returns the API version the given call was deprecated in, or -1 if the method is not
     * deprecated.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @param desc the method's descriptor - see {@link org.objectweb.asm.Type}
     * @return the API version the API was deprecated in, or -1 if the method is not deprecated
     */
    @NonNull
    public ApiConstraint getMethodDeprecatedInVersions(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int deprecatedIn = findMemberDeprecatedIn(classNumber, name, desc);
                return deprecatedIn <= 0 ? ApiConstraint.UNKNOWN : apiConstraints.get(deprecatedIn);
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * @deprecated Use {@link #getMethodRemovedInVersions} instead to properly handle APIs which can
     *     now appear in multiple SDK extensions.
     */
    @Deprecated
    public int getMethodRemovedIn(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        ApiConstraint versions = getMethodRemovedInVersions(owner, name, desc);
        if (versions != ApiConstraint.UNKNOWN) {
            return versions.min();
        } else {
            return -1;
        }
    }

    /**
     * Returns the API version the given call was removed in, or -1 if the method was not removed.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @param desc the method's descriptor - see {@link org.objectweb.asm.Type}
     * @return the API version the API was removed in, or -1 if the method was not removed
     */
    @NonNull
    public ApiConstraint getMethodRemovedInVersions(
            @NonNull String owner, @NonNull String name, @NonNull String desc) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int removedIn = findMemberRemovedIn(classNumber, name, desc);
                return removedIn <= 0 ? ApiConstraint.UNKNOWN : apiConstraints.get(removedIn);
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * Returns all removed fields of the given class and all its super classes and interfaces.
     *
     * <p><b>NOTE: This method only works for a limited set of APIs</b>; it is not a general purpose
     * lookup utility. {@link #getFieldRemovedInVersions} works in the general case.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @return the removed fields, or null if the owner class was not found
     */
    @Nullable
    public Collection<ApiMember> getRemovedFields(@NonNull String owner) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                return getRemovedMembers(classNumber, false);
            }
        }
        return null;
    }

    /**
     * Returns all removed methods of the given class and all its super classes and interfaces.
     *
     * @deprecated This method is a no-op (it was unused for years but had some overhead in the
     *     database which has been removed). Note that you *can* still use {@link
     *     #getMethodRemovedInVersions}.
     */
    @Deprecated
    @Nullable
    public Collection<ApiMember> getRemovedMethods(@NonNull String owner) {
        return null;
    }

    /**
     * Returns all removed methods or fields depending on the value of the {@code method} parameter.
     *
     * @param classNumber the index of the class
     * @param methods true to return methods, false to return fields.
     * @return all removed methods or fields
     */
    @NonNull
    private Collection<ApiMember> getRemovedMembers(int classNumber, boolean methods) {
        int curr = seekClassData(classNumber, CLASS_HEADER_MEMBER_OFFSETS);

        // 3 bytes for first offset
        int start = get3ByteInt(mData, curr);
        curr += 3;

        int length = get2ByteInt(mData, curr);
        if (length == 0) {
            return Collections.emptyList();
        }

        List<ApiMember> result = null;
        int end = start + length;
        for (int index = start; index < end; index++) {
            int offset = mIndices[index];
            boolean methodSignatureDetected = false;
            int i;
            for (i = offset; i < mData.length; i++) {
                byte b = mData[i];
                if (b == 0) {
                    break;
                }
                if (b == '(') {
                    methodSignatureDetected = true;
                }
            }
            if (i >= mData.length) {
                assert false;
                break;
            }
            if (methodSignatureDetected != methods) {
                continue;
            }
            int endOfSignature = i++;
            int since = Byte.toUnsignedInt(mData[i++]);
            if ((since & HAS_EXTRA_BYTE_FLAG) != 0) {
                int deprecatedIn = Byte.toUnsignedInt(mData[i++]);
                if ((deprecatedIn & HAS_EXTRA_BYTE_FLAG) != 0) {
                    int removedIn = Byte.toUnsignedInt(mData[i]);
                    if (removedIn != 0) {
                        StringBuilder sb = new StringBuilder(endOfSignature - offset);
                        for (i = offset; i < endOfSignature; i++) {
                            sb.append((char) Byte.toUnsignedInt(mData[i]));
                        }
                        since &= API_MASK;
                        deprecatedIn &= API_MASK;
                        if (result == null) {
                            result = new ArrayList<>();
                        }
                        result.add(new ApiMember(sb.toString(), since, deprecatedIn, removedIn));
                    } else {
                        assert false;
                    }
                }
            }
        }
        return result == null ? Collections.emptyList() : result;
    }

    /**
     * @deprecated Use {@link #getClassVersions} instead to properly handle APIs which can now
     *     appear in multiple SDK extensions.
     */
    @Deprecated
    public int getFieldVersion(@NonNull String owner, @NonNull String name) {
        return getFieldVersions(owner, name).min();
    }

    /**
     * Returns the API version required to access the given field, or -1 if this is not a known API
     * method. Note that it may return -1 for classes introduced in version 1; internally the
     * database only stores version data for version 2 and up.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @return the minimum API version the method is supported for, or -1 if it's unknown
     */
    @NonNull
    public ApiConstraint getFieldVersions(@NonNull String owner, @NonNull String name) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int api = findMember(classNumber, name, null);
                if (api < 0) {
                    if (STRIP_MEMBERS) {
                        return getClassVersions(classNumber);
                    } else {
                        return ApiConstraint.UNKNOWN;
                    }
                }
                return apiConstraints.get(api);
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * @deprecated Use {@link #getFieldDeprecatedInVersions} instead to properly handle APIs which
     *     can now appear in multiple SDK extensions.
     */
    @Deprecated
    public int getFieldDeprecatedIn(@NonNull String owner, @NonNull String name) {
        ApiConstraint versions = getFieldDeprecatedInVersions(owner, name);
        if (versions != ApiConstraint.UNKNOWN) {
            return versions.min();
        } else {
            return -1;
        }
    }

    /**
     * Returns the API version the given field was deprecated in, or -1 if the field is not
     * deprecated.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @return the API version the API was deprecated in, or -1 if the field is not deprecated
     */
    @NonNull
    public ApiConstraint getFieldDeprecatedInVersions(@NonNull String owner, @NonNull String name) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int deprecatedIn = findMemberDeprecatedIn(classNumber, name, null);
                return deprecatedIn <= 0 ? ApiConstraint.UNKNOWN : apiConstraints.get(deprecatedIn);
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * @deprecated Use {@link #getFieldRemovedInVersions} instead to properly handle APIs which can
     *     now appear in multiple SDK extensions.
     */
    @Deprecated
    public int getFieldRemovedIn(@NonNull String owner, @NonNull String name) {
        ApiConstraint versions = getFieldRemovedInVersions(owner, name);
        if (versions != ApiConstraint.UNKNOWN) {
            return versions.min();
        } else {
            return -1;
        }
    }

    /**
     * Returns the API version the given field was removed in, or -1 if the field was not removed.
     *
     * @param owner the internal name of the field's owner class, e.g. its fully qualified name (as
     *     returned by Class.getName())
     * @param name the method's name
     * @return the API version the API was removed in, or -1 if the field was not removed
     */
    @NonNull
    public ApiConstraint getFieldRemovedInVersions(@NonNull String owner, @NonNull String name) {
        //noinspection VariableNotUsedInsideIf
        if (mData != null) {
            int classNumber = findClass(owner);
            if (classNumber >= 0) {
                int removedIn = findMemberRemovedIn(classNumber, name, null);
                return removedIn <= 0 ? ApiConstraint.UNKNOWN : apiConstraints.get(removedIn);
            }
        }

        return ApiConstraint.UNKNOWN;
    }

    /**
     * Returns true if the given owner (in VM format) is relevant to the database. This allows quick
     * filtering out of owners that won't return any data for the various {@code #getFieldVersion}
     * etc methods.
     *
     * @param owner the owner to look up
     * @return true if the owner might be relevant to the API database
     */
    public boolean isRelevantOwner(@NonNull String owner) {
        return findClass(owner) >= 0;
    }

    /**
     * Returns true if the given package is a valid Java package supported in any version of
     * Android.
     *
     * @param classOrPackageName the name of a package or a class
     * @param packageNameLength the length of the package part of the name
     * @return true if the package is included in one or more versions of Android
     */
    public boolean isValidJavaPackage(@NonNull String classOrPackageName, int packageNameLength) {
        return findContainer(classOrPackageName, packageNameLength, true) >= 0;
    }

    /**
     * Checks if the two given class or package names are equal or differ only by separators.
     * Separators '.', '/', and '$' are considered equivalent.
     */
    public static boolean equivalentName(@NonNull String name1, @NonNull String name2) {
        int len1 = name1.length();
        int len2 = name2.length();
        if (len1 != len2) {
            return false;
        }
        for (int i = 0; i < len1; i++) {
            if (normalizeSeparator(name1.charAt(i)) != normalizeSeparator(name2.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the beginning part of the given class or package name is equal to the given prefix,
     * or differs only by separators. Separators '.', '/', and '$' are considered equivalent.
     */
    public static boolean startsWithEquivalentPrefix(
            @NonNull String classOrPackageName, @NonNull String prefix) {
        return equivalentFragmentAtOffset(classOrPackageName, 0, prefix);
    }

    /**
     * Checks if the substring of the given class or package name at the given offset is equal to
     * the given fragment or differs only by separators. Separators '.', '/', and '$' are considered
     * equivalent.
     */
    public static boolean equivalentFragmentAtOffset(
            @NonNull String classOrPackageName, int offset, @NonNull String fragment) {
        int prefixLength = fragment.length();
        if (offset < 0 || offset > classOrPackageName.length() - prefixLength) {
            return false;
        }
        for (int prefixOffset = 0; prefixOffset < prefixLength; prefixOffset++) {
            if (normalizeSeparator(classOrPackageName.charAt(offset++))
                    != normalizeSeparator(fragment.charAt(prefixOffset))) {
                return false;
            }
        }
        return true;
    }

    private static char normalizeSeparator(char c) {
        if (c == '/' || c == '$') {
            c = '.';
        }
        return c;
    }

    /**
     * Computes the hashcode of the given signature string. If s2 is not null, include it as well as
     * if it had been appended to s1 (this is done such that we don't have to concatenate strings
     * just to compute their hashcode). This will omit the type suffix, e.g. in
     * "toString()Ljava/lang/String;" the String return type is not included. We also treat / and $
     * as a "."
     */
    // Computes hashcode of two strings
    public static int signatureHashCode(@NonNull String s1, @Nullable String s2) {
        int h = 0;
        int n1 = s1.lastIndexOf(')');
        if (n1 == -1) {
            n1 = s1.length();
        }
        for (int i = 0; i < n1; i++) {
            char c = normalizeSeparator(s1.charAt(i));
            // This works because our signatures are always iso latin characters
            h = 31 * h + (c & 0xff);
        }

        if (s2 != null) {
            int n2 = s2.lastIndexOf(')');
            if (n2 == -1) {
                n2 = s2.length();
            }
            for (int i = 0; i < n2; i++) {
                char c = normalizeSeparator(s2.charAt(i));
                // This works because our signatures are always iso latin characters
                h = 31 * h + (c & 0xff);
            }
        }

        return h & ~(1 << 31);
    }

    private int findMember(int classNumber, @NonNull String name, @Nullable String desc) {
        return findMember(classNumber, name, desc, CLASS_HEADER_API);
    }

    private int findMemberDeprecatedIn(
            int classNumber, @NonNull String name, @Nullable String desc) {
        return findMember(classNumber, name, desc, CLASS_HEADER_DEPRECATED);
    }

    private int findMemberRemovedIn(int classNumber, @NonNull String name, @Nullable String desc) {
        return findMember(classNumber, name, desc, CLASS_HEADER_REMOVED);
    }

    private int seekClassData(int classNumber, int field) {
        int offset = mIndices[classNumber];
        offset += mData[offset] & 0xFF;
        if (field == CLASS_HEADER_MEMBER_OFFSETS) {
            return offset;
        }
        offset += 5; // 3 bytes for start, 2 bytes for length
        if (field == CLASS_HEADER_API) {
            return offset;
        }
        byte sinceFirst = mData[offset];
        if ((sinceFirst & IS_SHORT_FLAG) != 0) {
            // not reassigning sinceFirst; the HAS_EXTRA_BYTE_FLAG for the
            // whole short is packed in the first byte
            offset++;
        }
        boolean hasDeprecatedIn = (sinceFirst & HAS_EXTRA_BYTE_FLAG) != 0;
        boolean hasRemovedIn = false;
        offset++;
        if (field == CLASS_HEADER_DEPRECATED) {
            return hasDeprecatedIn ? offset : -1;
        } else if (hasDeprecatedIn) {
            byte deprecatedFirst = mData[offset];
            hasRemovedIn = (deprecatedFirst & HAS_EXTRA_BYTE_FLAG) != 0;
            offset++;
            if ((deprecatedFirst & IS_SHORT_FLAG) != 0) {
                offset++;
            }
        }
        if (field == CLASS_HEADER_REMOVED) {
            return hasRemovedIn ? offset : -1;
        } else if (hasRemovedIn) {
            byte removedFirst = mData[offset];
            if ((removedFirst & IS_SHORT_FLAG) != 0) {
                offset++;
            }
            offset++;
        }
        assert field == CLASS_HEADER_INTERFACES;
        return offset;
    }

    private int seekMemberData(int classNumber, @NonNull String name, @Nullable String desc) {
        int curr = seekClassData(classNumber, CLASS_HEADER_MEMBER_OFFSETS);

        // 3 bytes for first offset
        int low = get3ByteInt(mData, curr);
        curr += 3;

        int length = get2ByteInt(mData, curr);
        if (length == 0) {
            return -1;
        }
        int high = low + length;

        boolean useHashCodes = USE_HASH_CODES && (mIndices[low] & USING_HASH_CODE_MASK) != 0;
        if (useHashCodes) {
            int hashCode = signatureHashCode(name, desc);

            while (low < high) {
                int middle = (low + high) >>> 1;
                int offset = mIndices[middle];
                offset = offset & ~(1 << 31);

                int currentHashCode = get4ByteInt(mData, offset);

                if (DEBUG_SEARCH) {
                    System.out.println(
                            "Comparing string "
                                    + (name + (desc != null ? desc : ""))
                                    + " (hash code "
                                    + hashCode
                                    + ") with entry at "
                                    + offset
                                    + " which has hash code "
                                    + currentHashCode);
                }

                int compare = currentHashCode - hashCode;
                if (compare == 0) {
                    offset += 4;
                    return offset;
                }

                if (compare < 0) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }

            return -1;
        }

        while (low < high) {
            int middle = (low + high) >>> 1;
            int offset = mIndices[middle];

            if (DEBUG_SEARCH) {
                System.out.println(
                        "Comparing string "
                                + (name + (desc != null ? desc : ""))
                                + " with entry at "
                                + offset
                                + ": "
                                + dumpEntry(offset));
            }

            int compare;

            if (desc != null) {
                // Method
                int nameLength = name.length();
                compare = compare(mData, offset, (byte) '(', name, 0, nameLength);
                if (compare == 0) {
                    offset += nameLength;
                    int argsEnd = desc.indexOf(')');
                    // Only compare up to the ) -- after that we have a return value in the
                    // input description, which isn't there in the database.
                    compare = compare(mData, offset, (byte) ')', desc, 0, argsEnd);
                    if (compare == 0) {
                        if (DEBUG_SEARCH) {
                            System.out.println("Found " + dumpEntry(offset));
                        }

                        offset += argsEnd + 1;

                        if (mData[offset++] == 0) {
                            // Yes, terminated argument list
                            return offset;
                        }
                    }
                }
            } else {
                // Field
                int nameLength = name.length();
                compare = compare(mData, offset, (byte) 0, name, 0, nameLength);
                if (compare == 0) {
                    offset += nameLength;
                    if (mData[offset++] == 0) {
                        // Yes, terminated argument list
                        return offset;
                    }
                }
            }

            if (compare < 0) {
                low = middle + 1;
            } else if (compare > 0) {
                high = middle;
            } else {
                assert false; // compare == 0 already handled above
                return -1;
            }
        }

        return -1;
    }

    private int findMember(
            int classNumber, @NonNull String name, @Nullable String desc, int apiLevelField) {
        int offset = seekMemberData(classNumber, name, desc);
        if (offset == -1) {
            return -1;
        }
        return getApiLevel(offset, apiLevelField);
    }

    private int getApiLevel(int offset, int apiLevelField) {
        int api = Byte.toUnsignedInt(mData[offset]);
        if (apiLevelField == CLASS_HEADER_API) {
            if ((api & IS_SHORT_FLAG) != 0) {
                // It's packed into a short
                int second = Byte.toUnsignedInt(mData[++offset]);
                return (api & (API_MASK & ~IS_SHORT_FLAG)) << 8 | second;
            }

            return api & API_MASK;
        }
        if ((api & HAS_EXTRA_BYTE_FLAG) == 0) {
            return -1;
        } else if ((api & IS_SHORT_FLAG) != 0) {
            // We used two bytes for the API level
            offset++;
        }
        api = Byte.toUnsignedInt(mData[++offset]);
        if (apiLevelField == CLASS_HEADER_DEPRECATED) {
            if ((api & IS_SHORT_FLAG) != 0) {
                // It's packed into a short
                int second = Byte.toUnsignedInt(mData[++offset]);
                int value = (api & (API_MASK & ~IS_SHORT_FLAG)) << 8 | second;
                return value == 0 ? -1 : value;
            }
            api &= API_MASK;
            return api == 0 ? -1 : api;
        }
        if ((api & IS_SHORT_FLAG) != 0) {
            // We used two bytes for the API level
            offset++;
        }
        assert apiLevelField == CLASS_HEADER_REMOVED;
        if ((api & HAS_EXTRA_BYTE_FLAG) == 0 || apiLevelField != CLASS_HEADER_REMOVED) {
            return -1;
        }
        api = Byte.toUnsignedInt(mData[++offset]);
        if ((api & IS_SHORT_FLAG) != 0) {
            // It's packed into a short
            int second = Byte.toUnsignedInt(mData[++offset]);
            int value = (api & (API_MASK & ~IS_SHORT_FLAG)) << 8 | second;
            return value == 0 ? -1 : value;
        }
        api &= API_MASK;
        return api == 0 ? -1 : api;
    }

    public String getSdkName(int sdkId) {
        return getSdkName(sdkId, false);
    }

    public String getSdkName(int sdkId, boolean shortName) {
        ExtensionSdk sdk = registry.find(sdkId);
        if (sdk != null) {
            if (shortName) {
                String name = sdk.getShortName();
                if (name != null) {
                    return name;
                }
            }
            return sdk.getName();
        }

        return ExtensionSdk.Companion.getSdkExtensionField(sdkId, false);
    }

    public String getSdkExtensionField(int sdkId, boolean fullyQualified) {
        ExtensionSdk sdk = registry.find(sdkId);
        if (sdk != null) {
            return sdk.getSdkExtensionField(fullyQualified);
        }

        return ExtensionSdk.Companion.getSdkExtensionField(sdkId, fullyQualified);
    }

    public static String getSdkExtensionField(
            @NonNull JavaContext context, int sdkId, boolean fullyQualified) {
        return getSdkExtensionField(
                ApiLookup.getOrNull(context.getClient(), context.getProject().getBuildTarget()),
                sdkId,
                fullyQualified);
    }

    public static String getSdkExtensionField(
            @Nullable ApiLookup lookup, int sdkId, boolean fullyQualified) {
        if (lookup != null) {
            return lookup.getSdkExtensionField(sdkId, fullyQualified);
        }
        return ExtensionSdk.Companion.getSdkExtensionField(sdkId, true);
    }

    /** Clears out any existing lookup instances */
    @VisibleForTesting
    static void dispose() {
        instances.clear();
    }

    public static boolean create(
            @NonNull LintClient client, @NonNull File apiFile, @NonNull File outputFile) {
        if (!outputFile.getParentFile().isDirectory()) {
            boolean ok = outputFile.getParentFile().mkdirs();
            if (!ok) {
                return false;
            }
        }

        try {
            boolean ok = cacheCreator(apiFile).create(client, outputFile);
            if (ok) {
                client.log(null, "Created API database file " + outputFile);
            }
            return ok;
        } catch (UnsupportedVersionException e) {
            client.log(null, e.getDisplayMessage(client));
            client.log(null, "(Database file: " + apiFile.getPath() + ")");
            return false;
        }
    }

    /**
     * Exception thrown if the underlying database file is using a newer format than the latest one
     * supported by the API parser and machinery.
     */
    public static class UnsupportedVersionException extends RuntimeException {
        public final int requested;
        public final int maxSupported;
        @Nullable public File apiFile;
        @Nullable public IAndroidTarget target;

        UnsupportedVersionException(int requested, int maxSupported) {
            super(
                    "Unsupported API database version "
                            + requested
                            + "; max supported is "
                            + maxSupported);
            this.requested = requested;
            this.maxSupported = maxSupported;
        }

        public String getDisplayMessage(@NonNull LintClient client) {
            StringBuilder sb = new StringBuilder();
            if (target != null) {
                sb.append(target.getName());
            } else {
                sb.append("API database");
            }
            sb.append(" requires a newer version of ")
                    .append(client.getClientDisplayName())
                    .append(" than ")
                    .append(client.getClientDisplayRevision());
            return sb.toString();
        }
    }
}
