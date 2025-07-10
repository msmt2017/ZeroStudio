/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.tools.lint.checks.ApiClass.USE_HASH_CODES;
import static com.android.tools.lint.checks.ApiClass.USING_HASH_CODE_MASK;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.ExtensionSdk;

import com.google.common.io.Files;

import kotlin.text.Charsets;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Database for API checking, providing efficient lookup for a given class, method or field.
 *
 * <p>This class provides a binary cache around an API to make initialization faster and to require
 * fewer objects. It creates a binary cache data structure which fits in a single byte array,
 * meaning that to open the database you can just read in the byte array and go. On one particular
 * machine, this takes about 30-50 ms versus seconds for the full parse. It also helps memory by
 * placing everything in a compact byte array instead of needing separate strings (2 bytes per
 * character in a char[] for the 25k method entries, 11k field entries and 6k class entries) - and
 * it also avoids the same number of Map.Entry objects.
 *
 * <p>Note: It stores the strings as single bytes, since all the JVM signatures are in ASCII.
 */
public class ApiDatabase {
    protected static final String FILE_HEADER = "API database used by Android lint\000";

    protected static final boolean DEBUG_SEARCH = false;
    protected static final boolean WRITE_STATS = false;

    public static final int HAS_EXTRA_BYTE_FLAG = 1 << 7;
    public static final int IS_SHORT_FLAG = 1 << 6;
    public static final int API_MASK = ~HAS_EXTRA_BYTE_FLAG;

    private static final int BINARY_FORMAT_VERSION = 18;

    protected byte[] mData;
    protected int[] mIndices;
    protected int containerCount;
    protected int sdkIndexOffset;

    @FunctionalInterface
    interface CacheCreator {
        boolean create(LintClient client, File binaryData);
    }

    /**
     * Computes the binary format version
     *
     * <p>This is a byte, the first 3 bits of which are from the {@param majorBinaryFormatVersion}
     * specified by subclasses (which can change the representation of the custom API attributes),
     * and the remaining 5 bits are a minor version from this class ({@code BINARY_FORMAT_VERSION}),
     * which controls the binary representation of most of the Api.
     *
     * @param majorBinaryFormatVersion which must fit in 3 bits (therefore less than 8)
     * @return the computed binary format version
     */
    public static int getBinaryFormatVersion(int majorBinaryFormatVersion) {
        assert (majorBinaryFormatVersion & 0x07) == majorBinaryFormatVersion;
        return (majorBinaryFormatVersion << 5) + BINARY_FORMAT_VERSION;
    }

    /**
     * Database format:
     *
     * <pre>
     * (Note: all numbers are big endian; the format uses 1, 2, 3 and 4 byte integers.)
     *
     * 1. A file header, which is the exact contents of {@link #FILE_HEADER} encoded
     *     as ASCII characters. The purpose of the header is to identify what the file
     *     is for, for anyone attempting to open the file.
     * 2. A file version number. If the binary file does not match the reader's expected
     *     version, it can ignore it (and regenerate the cache from XML).
     *
     * 3. The index table. When the data file is read, this is used to initialize the
     *    {@link #mIndices} array. The index table is built up like this:
     *    a. The number of index entries (e.g. number of elements in the {@link #mIndices} array)
     *        [a 4-byte integer]
     *    b. The number of java/javax packages [a 4-byte integer]
     *    c. Offsets to the container entries, one for each package or a class containing inner
     *       classes [a 4-byte integer].
     *    d. Offsets to the class entries, one for each class [a 4-byte integer].
     *    e. Offsets to the member entries, one for each member [a 4-byte integer]. Note that
     *       if a member entry name is stored as a hash code instead of a full name and
     *       description, the highest level bit (1 << 31) is set in the starting index.
     *
     * 4. The member entries -- one for each member. A given class entry will point to the
     *    first and last members in the index table above, and the offset of a given member
     *    is pointing to the offset of these entries.
     *    a. The name and description (except for the return value) of the member, in JVM format
     *       (e.g. for toLowerCase(char) we'd have "toLowerCase(C)". This is converted into
     *       UTF_8 representation as bytes [n bytes, the length of the byte representation of
     *       the description). NOTE: If the high level bit (1 << 31) in the starting offset is set,
     *       then a 4-byte hashcode is stored instead of the name and description to uniquely
     *       identify the name and description. The hash coding algorithm used is
     *       {@link ApiLookup#signatureHashCode(String, String)}.
     *    b. A terminating 0 byte [1 byte].
     *    c. A sequence of bytes representing custom data attached to this entry, to be interpreted
     *       by the consumer.
     *       All bytes except the last one have the top bit ({@link #HAS_EXTRA_BYTE_FLAG}) set.
     *
     * 5. The class entries -- one for each class.
     *    a. The index within this class entry where the metadata (other than the name)
     *       can be found. [1 byte]. This means that if you know a class by its number,
     *       you can quickly jump to its metadata without scanning through the string to
     *       find the end of it, by just adding this byte to the current offset and
     *       then you're at the data described below for (d).
     *    b. The name of the class (just the base name, not the package), as encoded as a
     *       UTF-8 string. [n bytes]
     *    c. A terminating 0 [1 byte].
     *    d. The index in the index table (3) of the first member in the class [a 3-byte integer.]
     *    e. The number of members in the class [a 2-byte integer].
     *    f. Custom metadata associated with the class.
     *
     * 6. The container entries -- one for each package and for each class containing inner classes.
     *    a. The name of the package or the outer class [n bytes].
     *    b. A terminating 0 for packages, or 1 for outer classes [1 byte].
     *    c. The index in the index table (3) of the first class in the package or the first inner
     *       class [a 3-byte integer.]
     *    d. The number of classes in the package or the number of inner classes in the outer class
     *       [a 2-byte integer].
     *
     * 7. The API vector table.
     *    a. The number of API constraint vectors as an integer.
     *    b. For each vector, if there are multiple SDKs, then a series of pairs of integers,
     *       one for each SDK included in the vector, where the first integer is the SDK id
     *       and the second is the version. This is followed by an integer -1 as a terminator.
     *       Otherwise, the API constraint vector represents a single API level: an int followed
     *       by a -1 integer terminator.
     *
     * 8. The SDK table -- one for each extension SDK.
     *    a. The number of extension SDKs as an integer.
     *    b. For each integer, the serialized form of each {@link ExtensionSdk}, as encoded
     *       by {@link ExtensionSdk.Companion#serialize(ExtensionSdk)}, encoded as UTF-8,
     *       and then stored as first the number of bytes in the string (as an integer)
     *       followed by the UTF-8 encoded bytes.
     * </pre>
     */
    protected void readData(
            @NonNull LintClient client,
            @NonNull File binaryFile,
            @Nullable CacheCreator cacheCreator,
            int majorBinaryFormatVersion) {
        if (!binaryFile.exists()) {
            client.log(null, "%1$s does not exist", binaryFile);
            return;
        }
        long start = WRITE_STATS ? System.currentTimeMillis() : 0;
        try {
            byte[] b = Files.toByteArray(binaryFile);

            // First skip the header
            int offset = 0;
            byte[] expectedHeader = FILE_HEADER.getBytes(Charsets.US_ASCII);
            for (byte anExpectedHeader : expectedHeader) {
                if (anExpectedHeader != b[offset++]) {
                    client.log(
                            null,
                            "Incorrect file header: not an API database cache "
                                    + "file, or a corrupt cache file");
                    return;
                }
            }

            // Read in the format number.
            if (b[offset++] != getBinaryFormatVersion(majorBinaryFormatVersion)) {
                if (cacheCreator == null) {
                    throw new IllegalStateException(
                            "API database binary file uses an incompatible format version: "
                                    + binaryFile);
                }
                // Force regeneration of new binary data with up to date format.
                if (cacheCreator.create(client, binaryFile)) {
                    readData(client, binaryFile, cacheCreator, majorBinaryFormatVersion); // Recurse
                }

                return;
            }

            int indexCount = get4ByteInt(b, offset);
            offset += 4;
            containerCount = get4ByteInt(b, offset);
            offset += 4;
            sdkIndexOffset = get4ByteInt(b, offset);
            offset += 4;

            mIndices = new int[indexCount];
            for (int i = 0; i < indexCount; i++) {
                // TODO: Pack the offsets: They increase by a small amount for each entry, so
                // no need to spend 4 bytes on each. These will need to be processed when read
                // back in anyway, so consider storing the offset -deltas- as single bytes and
                // adding them up cumulatively in readData().
                mIndices[i] = get4ByteInt(b, offset);
                offset += 4;
            }
            mData = b;
            // TODO: We only need to keep the data portion here since we've initialized
            // the offset array separately.
            // TODO: Investigate (profile) accessing the byte buffer directly instead of
            // accessing a byte array.

            if (WRITE_STATS) {
                long end = System.currentTimeMillis();
                System.out.println("\nRead API database in " + (end - start) + " milliseconds.");
                System.out.print("Size of data table: " + mData.length + " bytes");
                System.out.println(
                        String.format(Locale.US, " (%.3gMB)", mData.length / (1024. * 1024.)));
            }
        } catch (Throwable e) {
            client.log(null, "Failure reading binary cache file %1$s", binaryFile.getPath());
            client.log(
                    null,
                    "Please delete the file and restart the IDE/lint: %1$s",
                    binaryFile.getPath());
            client.log(e, null);
        }
    }

    /**
     * See the {@link #readData(LintClient, File, CacheCreator, int)} for documentation on the data
     * format.
     */
    protected static void writeDatabase(
            @NonNull LintClient client,
            @NonNull File file,
            @NonNull Api<? extends ApiClassBase> info,
            int majorBinaryFormatVersion,
            @Nullable File xmlFile)
            throws IOException {
        Map<String, ? extends ApiClassBase> classMap = info.getClasses();

        List<ApiClassOwner<? extends ApiClassBase>> containers =
                new ArrayList<>(info.getContainers().values());
        Collections.sort(containers);

        // Compute members of each class that must be included in the database; we can
        // skip those that have the same since-level as the containing class. And we
        // also need to keep those entries that are marked deprecated or removed.
        int estimatedSize = 0;
        for (ApiClassOwner<? extends ApiClassBase> container : containers) {
            estimatedSize += 4; // offset entry
            estimatedSize += container.getName().length() + 20; // Container entry.

            for (ApiClassBase cls : container.getClasses()) {
                estimatedSize += 4; // offset entry
                estimatedSize += cls.getName().length() + 20; // Class entry.

                estimatedSize += cls.computeExtraStorageNeeded(info);
            }

            // Ensure that the classes are sorted.
            Collections.sort(container.getClasses());
        }

        // 2 integers per simple API level, 2 integers per SDK + 1 per complex API level.
        // We don't have a count of all the API levels yet so this is an approximation.
        estimatedSize += 120 * 16;
        // 120 bytes per SDK for name strings.
        estimatedSize += 20 * 120;

        // Write header
        ByteBuffer buffer = ByteBuffer.allocate(estimatedSize);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(FILE_HEADER.getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) getBinaryFormatVersion(majorBinaryFormatVersion));

        int indexCountOffset = buffer.position();
        int indexCount = 0;

        buffer.putInt(0); // placeholder

        // Write the number of containers in the containers index.
        buffer.putInt(containers.size());

        int sdkTableOffsetIndex = buffer.position();
        buffer.putInt(0); // placeholder

        // Write container index.
        int newIndex = buffer.position();
        for (ApiClassOwner<? extends ApiClassBase> container : containers) {
            container.indexOffset = newIndex;
            newIndex += 4;
            indexCount++;
        }

        // Write class index.
        for (ApiClassOwner<? extends ApiClassBase> container : containers) {
            for (ApiClassBase cls : container.getClasses()) {
                cls.indexOffset = newIndex;
                cls.index = indexCount;
                newIndex += 4;
                indexCount++;
            }
        }

        // Compute member indices.
        for (ApiClassOwner<? extends ApiClassBase> container : containers) {
            for (ApiClassBase cls : container.getClasses()) {
                if (cls.members != null && !cls.members.isEmpty()) {
                    cls.memberOffsetBegin = newIndex;
                    cls.memberIndexStart = indexCount;
                    for (String ignored : cls.members) {
                        newIndex += 4;
                        indexCount++;
                    }
                    cls.memberOffsetEnd = newIndex;
                    cls.memberIndexLength = indexCount - cls.memberIndexStart;
                } else {
                    cls.memberOffsetBegin = -1;
                    cls.memberOffsetEnd = -1;
                    cls.memberIndexStart = -1;
                    cls.memberIndexLength = 0;
                }
            }
        }

        // Fill in the earlier index count.
        buffer.position(indexCountOffset);
        buffer.putInt(indexCount);
        buffer.position(newIndex);

        // Write member entries.
        for (ApiClassOwner<? extends ApiClassBase> container : containers) {
            for (ApiClassBase apiClass : container.getClasses()) {
                int index = apiClass.memberOffsetBegin;
                List<String> members = apiClass.members;
                for (int i = 0; i < members.size(); i++) {
                    String member = members.get(i);
                    // Update member offset to point to this entry
                    int start = buffer.position();
                    buffer.position(index);

                    if (USE_HASH_CODES && i == 0 && !apiClass.includeNames) {
                        // Marker bit for first index: use hash codes for this class.
                        // Integer offsets was generous; we really only need 3 bytes to represent
                        // offsets in our table so this bit is available.
                        buffer.putInt(start | USING_HASH_CODE_MASK);
                    } else {
                        buffer.putInt(start);
                    }
                    index = buffer.position();
                    buffer.position(start);

                    apiClass.writeMemberData(info, member, buffer);
                }
                assert index == apiClass.memberOffsetEnd : apiClass.memberOffsetEnd;
            }
        }

        // Write class entries. These are written together, rather than
        // being spread out among the member entries, in order to have
        // reference locality (search that a binary search through the classes
        // are likely to look at entries near each other.)
        for (ApiClassOwner<? extends ApiClassBase> container : containers) {
            List<? extends ApiClassBase> classes = container.getClasses();
            for (ApiClassBase cls : classes) {
                int index = buffer.position();
                buffer.position(cls.indexOffset);
                buffer.putInt(index);
                buffer.position(index);
                String name = cls.getSimpleName();
                int pos = name.lastIndexOf('$');
                if (pos > 0) {
                    name = name.substring(pos + 1);
                }

                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                assert nameBytes.length < 254 : name;
                buffer.put((byte) (nameBytes.length + 2)); // 2: terminating 0, and this byte itself
                buffer.put(nameBytes);
                buffer.put((byte) 0);

                // 3 bytes for beginning, 2 bytes for *length*
                put3ByteInt(buffer, cls.memberIndexStart);
                put2ByteInt(buffer, cls.memberIndexLength);

                ApiClassBase apiClass = classMap.get(cls.getName());
                assert apiClass != null : cls.getName();
                apiClass.writeSuperInterfaces(info, buffer);
            }
        }

        for (ApiClassOwner<? extends ApiClassBase> container : containers) {
            int index = buffer.position();
            buffer.position(container.indexOffset);
            buffer.putInt(index);
            buffer.position(index);

            byte[] bytes = container.getName().getBytes(StandardCharsets.UTF_8);
            buffer.put(bytes);
            buffer.put(container.isClass() ? (byte) 1 : (byte) 0);

            List<? extends ApiClassBase> classes = container.getClasses();
            if (classes.isEmpty()) {
                put3ByteInt(buffer, 0);
                put2ByteInt(buffer, 0);
            } else {
                // 3 bytes for beginning, 2 bytes for *length*
                int firstClassIndex = classes.get(0).index;
                int classCount = classes.get(classes.size() - 1).index - firstClassIndex + 1;
                put3ByteInt(buffer, firstClassIndex);
                put2ByteInt(buffer, classCount);
            }
        }

        // Write ApiConstraints table
        int sdkTableOffset = buffer.position();

        // Fill in the earlier index count.
        buffer.position(sdkTableOffsetIndex);
        buffer.putInt(sdkTableOffset);
        buffer.position(sdkTableOffset);

        // Write API constraint vectors
        List<String> sdks = info.getSdks();
        int sdkCount = sdks.size();
        put2ByteInt(buffer, sdkCount);
        for (String encoded : sdks) {
            if (encoded.contains(":")) {
                // Example 1: "0:33,1000000:3,33:3" - vector of SDKs and API levels
                for (String segment : encoded.split(",")) {
                    int colon = segment.indexOf(':');
                    assert colon != -1 : segment;
                    int sdk = Integer.parseInt(segment.substring(0, colon));
                    int version = ApiParser.toVersionInt(segment.substring(colon + 1));
                    buffer.putInt(sdk);
                    buffer.putInt(version);
                }
                buffer.putInt(-1); // terminator
            } else {
                // Example 2: "31" - simple API level
                // ALREADY a version int
                int api = ApiParser.toVersionInt(encoded);
                buffer.putInt(api);
                buffer.putInt(-1); // terminator
            }
        }

        // Write SDK names
        List<ExtensionSdk> extensionSdks = info.getExtensionSdks();
        buffer.putInt(extensionSdks.size());
        for (ExtensionSdk sdk : extensionSdks) {
            String s = ExtensionSdk.Companion.serialize(sdk);
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        }

        int size = buffer.position();
        assert size <= buffer.limit();
        buffer.mark();

        if (WRITE_STATS) {
            System.out.print("Actual binary size: " + size + " bytes");
            System.out.println(String.format(Locale.US, " (%.3gMB)", size / (1024. * 1024.)));
        }

        // Now dump this out as a file
        // There's probably an API to do this more efficiently; TODO: Look into this.
        byte[] b = new byte[size];
        buffer.rewind();
        buffer.get(b);
        if (file.exists()) {
            boolean deleted = file.delete();
            assert deleted : file;
        }

        // Write to a different file and swap it in last minute.
        // This helps in scenarios where multiple simultaneous Gradle
        // threads are attempting to access the file before it's ready.
        File tmp = new File(file.getPath() + "." + new Random().nextInt());
        Files.asByteSink(tmp).write(b);
        if (!tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static boolean warnedInvalidData = false;

    protected static int get4ByteInt(@NonNull byte[] data, int offset) {
        byte b1 = data[offset++];
        byte b2 = data[offset++];
        byte b3 = data[offset++];
        byte b4 = data[offset];
        // The byte data is always big endian.
        return (b1 & 0xFF) << 24 | (b2 & 0xFF) << 16 | (b3 & 0xFF) << 8 | (b4 & 0xFF);
    }

    protected static void put3ByteInt(@NonNull ByteBuffer buffer, int value) {
        // Big endian
        byte b3 = (byte) (value & 0xFF);
        value >>>= 8;
        byte b2 = (byte) (value & 0xFF);
        value >>>= 8;
        byte b1 = (byte) (value & 0xFF);
        buffer.put(b1);
        buffer.put(b2);
        buffer.put(b3);
    }

    protected static void put2ByteInt(@NonNull ByteBuffer buffer, int value) {
        // Big endian
        byte b2 = (byte) (value & 0xFF);
        value >>>= 8;
        byte b1 = (byte) (value & 0xFF);
        buffer.put(b1);
        buffer.put(b2);
    }

    protected static int get3ByteInt(@NonNull byte[] mData, int offset) {
        byte b1 = mData[offset++];
        byte b2 = mData[offset++];
        byte b3 = mData[offset];
        // The byte data is always big endian.
        return (b1 & 0xFF) << 16 | (b2 & 0xFF) << 8 | (b3 & 0xFF);
    }

    protected static int get2ByteInt(@NonNull byte[] data, int offset) {
        byte b1 = data[offset++];
        byte b2 = data[offset];
        // The byte data is always big endian.
        return (b1 & 0xFF) << 8 | (b2 & 0xFF);
    }

    // For debugging only
    protected String dumpEntry(int offset) {
        if (DEBUG_SEARCH) {
            StringBuilder sb = new StringBuilder(200);
            for (int i = offset; i < mData.length; i++) {
                byte b = mData[i];
                if (b == 0 || b == 1) {
                    break;
                }
                char c = (char) Byte.toUnsignedInt(b);
                sb.append(c);
            }

            return sb.toString();
        } else {
            return "<disabled>";
        }
    }

    protected static int compare(
            byte[] data, int offset, byte terminator, String s, int sOffset, int max) {
        int i = offset;
        int j = sOffset;
        for (; j < max; i++, j++) {
            byte b = data[i];
            char c = s.charAt(j);
            if (c == '.' && (b == '/' || b == '$')) { // '.' matches both '/' and '$'.
                continue;
            }
            // TODO: Check somewhere that the strings are purely in the ASCII range.
            // If not, they will not match the database.
            byte cb = (byte) c;
            int delta = b - cb;
            if (delta != 0) {
                return delta;
            }
        }

        byte b = data[i];
        if (terminator == 1 && b == 0) { // Terminator 1 matches both 0 and 1.
            return 0;
        }
        return b - terminator;
    }

    /** Returns the container index of the given package or class, or -1 if it is unknown. */
    protected int findContainer(
            @NonNull String packageOrClassName, int containerNameLength, boolean packageOnly) {
        // The index array contains class indexes from 0 to classCount and
        // member indices from classCount to mIndices.length.
        int low = 0;
        int high = containerCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            int offset = mIndices[middle];

            if (DEBUG_SEARCH) {
                System.out.println(
                        "Comparing string \""
                                + packageOrClassName.substring(0, containerNameLength)
                                + "\" with entry at "
                                + offset
                                + ": "
                                + dumpEntry(offset));
            }

            byte terminator = packageOnly ? (byte) 0 : (byte) 1;
            int c = compare(mData, offset, terminator, packageOrClassName, 0, containerNameLength);
            if (c == 0) {
                if (DEBUG_SEARCH) {
                    System.out.println("Found " + dumpEntry(offset));
                }
                return middle;
            }

            if (c < 0) {
                low = middle + 1;
            } else {
                assert c > 0;
                high = middle;
            }
        }

        return -1;
    }

    /** Returns the class number of the given class, or -1 if it is unknown. */
    protected int findClass(@NonNull String className) {
        int lastSeparator = lastIndexOfDotOrSlashOrDollar(className);
        int containerNameLength = lastSeparator >= 0 ? lastSeparator : 0;
        int containerNumber = findContainer(className, containerNameLength, false);
        if (containerNumber < 0) {
            return -1;
        }
        int classNameLength = className.length();
        int classNameOffset = lastSeparator + 1;

        int curr = mIndices[containerNumber];
        // Skip the name of the container.
        while ((mData[curr] & ~1) != 0) { // Iterate until encountering 0 or 1.
            curr++;
        }
        curr++;

        // 3 bytes for first offset.
        int low = get3ByteInt(mData, curr);
        curr += 3;

        int length = get2ByteInt(mData, curr);
        int high = low + length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            int offset = mIndices[middle];
            offset++; // Skip the byte which points to the metadata after the name.

            if (DEBUG_SEARCH) {
                System.out.println(
                        "Comparing string "
                                + className.substring(0, classNameLength)
                                + " with entry at "
                                + offset
                                + ": "
                                + dumpEntry(offset));
            }

            int c = compare(mData, offset, (byte) 0, className, classNameOffset, classNameLength);
            if (c == 0) {
                if (DEBUG_SEARCH) {
                    System.out.println("Found " + dumpEntry(offset));
                }
                return middle;
            }

            if (c < 0) {
                low = middle + 1;
            } else {
                assert c > 0;
                high = middle;
            }
        }

        return -1;
    }

    private static int lastIndexOfDotOrSlashOrDollar(@NonNull String className) {
        for (int i = className.length(); --i >= 0; ) {
            char c = className.charAt(i);
            if (c == '.' || c == '/' || c == '$') {
                return i;
            }
        }
        return -1;
    }
}
