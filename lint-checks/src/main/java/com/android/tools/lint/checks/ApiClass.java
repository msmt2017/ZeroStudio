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

import static com.android.SdkConstants.CONSTRUCTOR_NAME;
import static com.android.tools.lint.detector.api.ExtensionSdk.ANDROID_SDK_ID;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.ApiConstraint;
import com.android.utils.Pair;

import com.google.common.collect.Iterables;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a class and its methods/fields.
 *
 * <p>{@link #getSince()} gives the API level it was introduced.
 *
 * <p>{@link #getMethodSince} returns the API level when the method was introduced.
 *
 * <p>{@link #getFieldSince} returns the API level when the field was introduced.
 */
public final class ApiClass extends ApiClassBase {

    /**
     * Removes members whose metadata is the same as the surrounding class.
     *
     * <p>Reduces the database size from 8.56MB down to 3.30M as of API level 33.
     */
    public static final boolean STRIP_MEMBERS = true;

    /**
     * Removes entry data (unless {@link ApiClassBase#includeNames} is set) and just records the
     * hash code instead for faster matching.
     *
     * <p>Reduces the database size from 3.30MB down to 0.94M as of API level 33.
     */
    public static final boolean USE_HASH_CODES = true;

    /**
     * Bit set in member offset indicating that the entries are hash codes. (Only used with {@link
     * #USE_HASH_CODES})
     */
    public static final int USING_HASH_CODE_MASK = 1 << 31;

    private final String mSdks;
    private final int mSince;
    private final int mDeprecatedIn;
    private final int mRemovedIn;

    private final List<Pair<String, Integer>> mSuperClasses = new ArrayList<>();
    private final List<Pair<String, Integer>> mInterfaces = new ArrayList<>();

    private final Map<String, Integer> mFields = new HashMap<>();
    private final Map<String, Integer> mMethods = new HashMap<>();
    private final Map<String, String> mMemberSdks = new HashMap<>();
    /* Deprecated fields and methods and the API levels when they were deprecated. */
    @Nullable private Map<String, Integer> mMembersDeprecatedIn;

    /**
     * Removed fields, methods, superclasses and interfaces and the API levels when they were
     * removed.
     */
    @Nullable private Map<String, Integer> mElementsRemovedIn;

    ApiClass(
            @NonNull String name,
            @Nullable String sdks,
            int since,
            int deprecatedIn,
            int removedIn) {
        super(name);

        // Work around b/206996004 -- wrong API level for SdkExtensions: Should be R, not TIRAMISU
        // This was broken for API level 33 (and fixed as of 34)
        if (name.equals("android/os/ext/SdkExtensions")
                // 8448: inlined ApiParser.toVersionInt(33), unit test (ApiLookupTest.testMinor)
                // verifies this stays working
                && since == 8448) {
            since = ApiParser.toVersionInt(30, 0);
        }

        mSdks = sdks;
        mSince = since;
        mDeprecatedIn = deprecatedIn;
        mRemovedIn = removedIn;
    }

    public String getSdks() {
        return mSdks;
    }

    /**
     * Returns when the class was introduced.
     *
     * <p>Note that this is a packed integer containing both major and minor versions; decode using
     * {@link ApiParser#getMajorVersion(int)} and {@link ApiParser#getMinorVersion(int)}.
     *
     * @return the api level the class was introduced.
     */
    int getSince() {
        return mSince;
    }

    /**
     * Returns the API level the class was deprecated in, or 0 if the class is not deprecated.
     *
     * <p>Note that this is a packed integer containing both major and minor versions; decode using
     * {@link ApiParser#getMajorVersion(int)} and {@link ApiParser#getMinorVersion(int)}.
     *
     * @return the API level the class was deprecated in, or 0 if the class is not deprecated
     */
    int getDeprecatedIn() {
        return mDeprecatedIn;
    }

    /**
     * Returns the API level the class was removed in, or 0 if the class was not removed.
     *
     * <p>Note that this is a packed integer containing both major and minor versions; decode using
     * {@link ApiParser#getMajorVersion(int)} and {@link ApiParser#getMinorVersion(int)}.
     *
     * @return the API level the class was removed in, or 0 if the class was not removed
     */
    int getRemovedIn() {
        return mRemovedIn;
    }

    /**
     * Returns the API level when a field was added, or 0 if it doesn't exist.
     *
     * <p>Note that this is a packed integer containing both major and minor versions; decode using
     * {@link ApiParser#getMajorVersion(int)} and {@link ApiParser#getMinorVersion(int)}.
     *
     * @param name the name of the field.
     * @param info the information about the rest of the API
     */
    int getFieldSince(String name, Api<? extends ApiClassBase> info) {
        // The field can come from this class or from a super class or an interface
        // The value can never be lower than this introduction of this class.
        // When looking at super classes and interfaces, it can never be lower than when the
        // super class or interface was added as a super class or interface to this class.
        // Look at all the values and take the lowest.
        // For instance:
        // This class A is introduced in 5 with super class B.
        // In 10, the interface C was added.
        // Looking for SOME_FIELD we get the following:
        // Present in A in API 15
        // Present in B in API 11
        // Present in C in API 7.
        // The answer is 10, which is when C became an interface
        int apiLevel = getValueWithDefault(mFields, name, 0);

        // TODO: We should switch to ApiConstraints here, and make sure we read sdks= attributes for
        // NODE_IMPLEMENTS and NODE_EXTENDS in ApiParser, and then do this processing on a per-SDK
        // basis and reconstitute the API vectors as necessary.
        // (We can use MultiApiConstraint.describe to merge vectors back into Strings.)

        // Look at the super classes and interfaces.
        ApiClassBase maxFrom = null;
        for (Pair<String, Integer> superClassPair : Iterables.concat(mSuperClasses, mInterfaces)) {
            ApiClassBase superClass = info.getClass(superClassPair.getFirst());
            if (superClass instanceof ApiClass) {
                int i = ((ApiClass) superClass).getFieldSince(name, info);
                if (i != 0) {
                    int tmp = Math.max(superClassPair.getSecond(), i);
                    if (apiLevel == 0 || tmp < apiLevel) {
                        apiLevel = tmp;
                        maxFrom = superClass;
                    }
                }
            }
        }

        if (apiLevel > mSince
                && mSdks != null
                && maxFrom != null
                && ((ApiClass) maxFrom).getMemberSdks(name, info) == null) {
            mMemberSdks.put(
                    name,
                    ANDROID_SDK_ID
                            + ":"
                            + ApiParser.getMajorVersion(apiLevel)
                            + '.'
                            + ApiParser.getMinorVersion(apiLevel));
        }

        return apiLevel;
    }

    /**
     * Returns when a field or a method was deprecated, or 0 if it's not deprecated.
     *
     * <p>Note that this is a packed integer containing both major and minor versions; decode using
     * {@link ApiParser#getMajorVersion(int)} and {@link ApiParser#getMinorVersion(int)}.
     *
     * @param name the name of the field.
     * @param info the information about the rest of the API
     */
    int getMemberDeprecatedIn(@NonNull String name, Api info) {
        // This follows the same logic as getField/getMethod.
        // However, it also incorporates deprecation versions from the class.
        int apiLevel = getValueWithDefault(mMembersDeprecatedIn, name, 0);
        return apiLevel == 0
                ? mDeprecatedIn
                : mDeprecatedIn == 0 ? apiLevel : Math.min(apiLevel, mDeprecatedIn);
    }

    String getMemberSdks(@NonNull String name, Api info) {
        String sdks = mMemberSdks.get(name);
        if (sdks == null) {
            sdks = mSdks;
        }
        return sdks;
    }

    /**
     * Returns the API level when a field or a method was removed, or 0 if it's not removed.
     *
     * <p>Note that this is a packed integer containing both major and minor versions; decode using
     * {@link ApiParser#getMajorVersion(int)} and {@link ApiParser#getMinorVersion(int)}.
     *
     * @param name the name of the field or method
     * @param info the information about the rest of the API
     */
    int getMemberRemovedIn(@NonNull String name, Api info) {
        int removedIn = getMemberRemovedInInternal(name, info);
        return removedIn == Integer.MAX_VALUE ? mRemovedIn : removedIn > 0 ? removedIn : 0;
    }

    /**
     * Returns the API level when a field or a method was removed, or Integer.MAX_VALUE if it's not
     * removed, or -1 if the field or method never existed in this class or its super classes and
     * interfaces.
     *
     * @param name the name of the field or method
     * @param info the information about the rest of the API
     */
    private int getMemberRemovedInInternal(String name, Api<ApiClass> info) {
        int apiLevel = getValueWithDefault(mElementsRemovedIn, name, Integer.MAX_VALUE);
        if (apiLevel == Integer.MAX_VALUE) {
            if (mMethods.containsKey(name) || mFields.containsKey(name)) {
                return mRemovedIn == 0 ? Integer.MAX_VALUE : mRemovedIn;
            }
            apiLevel = -1; // Never existed in this class.
        }

        // Look at the super classes and interfaces.
        for (Pair<String, Integer> superClassPair : Iterables.concat(mSuperClasses, mInterfaces)) {
            String superClassName = superClassPair.getFirst();
            int superClassRemovedIn =
                    getValueWithDefault(mElementsRemovedIn, superClassName, Integer.MAX_VALUE);
            if (superClassRemovedIn > apiLevel) {
                ApiClass superClass = info.getClass(superClassName);
                if (superClass != null) {
                    int i = superClass.getMemberRemovedInInternal(name, info);
                    if (i != -1) {
                        int tmp = Math.min(superClassRemovedIn, i);
                        if (tmp > apiLevel) {
                            apiLevel = tmp;
                        }
                    }
                }
            }
        }

        return apiLevel;
    }

    private int getValueWithDefault(
            @Nullable Map<String, Integer> map, @NonNull String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Integer value = map.get(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Returns the API level when a method was added, or 0 if it doesn't exist. This goes through
     * the super class and interfaces to find method only present there.
     *
     * <p>Note that this is a packed integer containing both major and minor versions; decode using
     * {@link ApiParser#getMajorVersion(int)} and {@link ApiParser#getMinorVersion(int)}.
     *
     * @param methodSignature the method signature
     * @param info the information about the rest of the API
     */
    int getMethodSince(String methodSignature, Api<? extends ApiClassBase> info) {
        // The method can come from this class or from a super class.
        // The value can never be lower than this introduction of this class.
        // When looking at super classes, it can never be lower than when the super class became
        // a super class of this class.
        // Look at all the values and take the lowest.
        // For instance:
        // This class A is introduced in 5 with super class B.
        // In 10, the super class changes to C.
        // Looking for foo() we get the following:
        // Present in A in API 15
        // Present in B in API 11
        // Present in C in API 7.
        // The answer is 10, which is when C became the super class.
        int apiLevel = getValueWithDefault(mMethods, methodSignature, 0);
        // Constructors aren't inherited.
        if (!methodSignature.startsWith(CONSTRUCTOR_NAME)) {
            ApiClassBase maxFrom = null;
            // Look at the super classes and interfaces.
            for (Pair<String, Integer> pair : Iterables.concat(mSuperClasses, mInterfaces)) {
                ApiClassBase superClass = info.getClass(pair.getFirst());
                if (superClass instanceof ApiClass) {
                    int i = ((ApiClass) superClass).getMethodSince(methodSignature, info);
                    if (i != 0) {
                        int tmp = Math.max(pair.getSecond(), i);
                        if (apiLevel == 0 || tmp < apiLevel) {
                            maxFrom = superClass;
                            apiLevel = tmp;
                        }
                    }
                }
            }

            // If the method requires a more recent API level than the current class inheriting
            // it defaults to, but there is an SDKs attribute on the current class, override that
            // SDK
            // attribute for this method.
            if (apiLevel > mSince
                    && mSdks != null
                    && maxFrom != null
                    && ((ApiClass) maxFrom).getMemberSdks(methodSignature, info) == null) {
                mMemberSdks.put(
                        methodSignature,
                        ANDROID_SDK_ID
                                + ":"
                                + ApiParser.getMajorVersion(apiLevel)
                                + '.'
                                + ApiParser.getMinorVersion(apiLevel));
            }
        }

        return apiLevel;
    }

    void addField(String name, String sdks, int since, int deprecatedIn, int removedIn) {
        mFields.put(name, since);
        mMemberSdks.put(name, sdks);
        addToDeprecated(name, deprecatedIn);
        addToRemoved(name, removedIn);
    }

    void addMethod(String name, String sdks, int since, int deprecatedIn, int removedIn) {
        // Strip off the method type at the end to ensure that the code which
        // produces inherited methods doesn't get confused and end up multiple entries.
        // For example, java/nio/Buffer has the method "array()Ljava/lang/Object;",
        // and the subclass java/nio/ByteBuffer has the method "array()[B". We want
        // the lookup on mMethods to associate the ByteBuffer array method to be
        // considered overriding the Buffer method.
        int index = name.indexOf(')');
        if (index != -1) {
            name = name.substring(0, index + 1);
        }
        mMethods.put(name, since);
        mMemberSdks.put(name, sdks);
        addToDeprecated(name, deprecatedIn);
        addToRemoved(name, removedIn);
    }

    void addSuperClass(String superClass, int since, int removedIn) {
        addToArray(mSuperClasses, superClass, since);
        addToRemoved(superClass, removedIn);
    }

    void addInterface(String interfaceClass, int since, int removedIn) {
        addToArray(mInterfaces, interfaceClass, since);
        addToRemoved(interfaceClass, removedIn);
    }

    @NonNull
    public Collection<String> getMethods() {
        return mMethods.keySet();
    }

    @NonNull
    public Collection<String> getFields() {
        return mFields.keySet();
    }

    static void addToArray(List<Pair<String, Integer>> list, String name, int value) {
        // check if we already have that name (at a lower level)
        for (Pair<String, Integer> pair : list) {
            if (name.equals(pair.getFirst())) {
                assert false;
                return;
            }
        }

        list.add(Pair.of(name, value));
    }

    private void addToDeprecated(String name, int deprecatedIn) {
        if (deprecatedIn > 0) {
            if (mMembersDeprecatedIn == null) {
                mMembersDeprecatedIn = new HashMap<>();
            }
            mMembersDeprecatedIn.put(name, deprecatedIn);
        }
    }

    private void addToRemoved(String name, int removedIn) {
        if (removedIn > 0) {
            if (mElementsRemovedIn == null) {
                mElementsRemovedIn = new HashMap<>();
            }
            mElementsRemovedIn.put(name, removedIn);
        }
    }

    /**
     * Returns the set of all methods, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a set containing all the members fields
     */
    Set<String> getAllMethods(Api info) {
        Set<String> members = new HashSet<>(100);
        addAllMethods(info, members, true /*includeConstructors*/);

        return members;
    }

    @NonNull
    List<Pair<String, Integer>> getInterfaces() {
        return mInterfaces;
    }

    @NonNull
    public List<Pair<String, Integer>> getAllInterfaces(Api<? extends ApiClassBase> info) {
        if (!mInterfaces.isEmpty()) {
            List<Pair<String, Integer>> interfaces = new ArrayList<>();
            addAllInterfaces(info, interfaces, getSince(), new HashMap<>());
            return interfaces;
        }
        return mInterfaces;
    }

    private void addAllInterfaces(
            Api<? extends ApiClassBase> info,
            List<Pair<String, Integer>> list,
            int since,
            Map<String, Integer> processed) {
        processed.put(getName(), since);
        since = Math.max(since, getSince());
        addAllInterfaces(info, list, since, processed, mInterfaces, true);
        addAllInterfaces(info, list, since, processed, mSuperClasses, false);
    }

    private void addAllInterfaces(
            Api<? extends ApiClassBase> info,
            List<Pair<String, Integer>> list,
            int since,
            Map<String, Integer> processed,
            List<Pair<String, Integer>> classes,
            boolean isInterface) {
        for (Pair<String, Integer> classAndVersion : classes) {
            String className = classAndVersion.getFirst();
            if (className.equals("java/lang/Object")) {
                continue;
            }
            int sinceMax = Math.max(since, classAndVersion.getSecond());
            if (isInterface) {
                addInterface(className, sinceMax, list);
            }

            Integer processedSince = processed.get(className);
            if (processedSince == null || processedSince > sinceMax) {
                ApiClassBase cls = info.getClass(className);
                if (cls instanceof ApiClass) {
                    ((ApiClass) cls).addAllInterfaces(info, list, sinceMax, processed);
                }
            }
        }
    }

    private void addInterface(String interfaceName, int since, List<Pair<String, Integer>> list) {
        // linear list rather than set because this is expected to be a short list
        for (int i = 0, n = list.size(); i < n; i++) {
            Pair<String, Integer> pair = list.get(i);
            if (pair.getFirst().equals(interfaceName)) {
                if (since < pair.getSecond()) {
                    list.set(i, Pair.of(interfaceName, since));
                }
                return;
            }
        }

        list.add(Pair.of(interfaceName, since));
    }

    @NonNull
    List<Pair<String, Integer>> getSuperClasses() {
        return mSuperClasses;
    }

    private void addAllMethods(Api<ApiClass> info, Set<String> set, boolean includeConstructors) {
        if (includeConstructors) {
            set.addAll(mMethods.keySet());
        } else {
            for (String method : mMethods.keySet()) {
                if (!method.startsWith(CONSTRUCTOR_NAME)) {
                    set.add(method);
                }
            }
        }

        for (Pair<String, Integer> superClass : Iterables.concat(mSuperClasses, mInterfaces)) {
            ApiClass cls = info.getClass(superClass.getFirst());
            if (cls != null) {
                cls.addAllMethods(info, set, false);
            }
        }
    }

    /**
     * Returns the set of all fields, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a set containing all the fields
     */
    Set<String> getAllFields(Api info) {
        Set<String> members = new HashSet<>(100);
        addAllFields(info, members);

        return members;
    }

    private void addAllFields(Api<ApiClass> info, Set<String> set) {
        set.addAll(mFields.keySet());

        for (Pair<String, Integer> superClass : Iterables.concat(mSuperClasses, mInterfaces)) {
            ApiClass cls = info.getClass(superClass.getFirst());
            if (cls == null) {
                throw new RuntimeException(
                        "Incomplete database file: could not resolve super class "
                                + superClass.getFirst()
                                + " from "
                                + getName());
            }
            cls.addAllFields(info, set);
        }
    }

    /**
     * Returns all removed fields, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a collection containing all removed fields
     */
    @NonNull
    Collection<ApiMember> getAllRemovedFields(Api info) {
        Set<String> fields = getAllFields(info);
        if (fields.isEmpty()) {
            return Collections.emptySet();
        }

        List<ApiMember> removedFields = new ArrayList<>();
        for (String fieldName : fields) {
            int removedIn = getMemberRemovedIn(fieldName, info);
            if (removedIn > 0) {
                int since = getFieldSince(fieldName, info);
                assert since > 0;
                int deprecatedIn = getMemberDeprecatedIn(fieldName, info);
                removedFields.add(new ApiMember(fieldName, since, deprecatedIn, removedIn));
            }
        }
        return removedFields;
    }

    /**
     * Returns all removed fields, including inherited ones.
     *
     * @param info the API to look up super classes from
     * @return a collection containing all removed fields
     */
    @NonNull
    Collection<ApiMember> getAllRemovedMethods(Api info) {
        Set<String> methods = getAllMethods(info);
        if (methods.isEmpty()) {
            return Collections.emptySet();
        }

        List<ApiMember> removedMethods = new ArrayList<>();
        for (String methodSignature : methods) {
            int removedIn = getMemberRemovedIn(methodSignature, info);
            if (removedIn > 0) {
                int since = getMethodSince(methodSignature, info);
                assert since > 0;
                int deprecatedIn = getMemberDeprecatedIn(methodSignature, info);
                removedMethods.add(new ApiMember(methodSignature, since, deprecatedIn, removedIn));
            }
        }
        return removedMethods;
    }

    /**
     * Extends the basic format by additionally writing:
     *
     * <pre>
     * 1. One, two or three bytes representing the API levels when the class was introduced,
     * deprecated, and removed, respectively. The third byte is present only if the class
     * was removed. The second byte is present only if the class was deprecated or removed.
     *
     * 2. The number of new super classes and interfaces [1 byte]. This counts only
     * super classes and interfaces added after the original API level of the class.
     *
     * 3. For each super class or interface counted in g,
     *     a. The index of the class [a 3-byte integer]
     *     b. The API level the class/interface was added [1 byte]
     * </pre>
     */
    @Override
    void writeSuperInterfaces(Api<? extends ApiClassBase> info, ByteBuffer buffer) {
        int since = getSince();
        int deprecatedIn = getDeprecatedIn();
        int removedIn = getRemovedIn();
        String sdks = getSdks();
        writeSinceDeprecatedInRemovedIn(info, buffer, since, deprecatedIn, removedIn, sdks);

        List<Pair<String, Integer>> interfaces = getAllInterfaces(info);
        int count = 0;
        if (!interfaces.isEmpty()) {
            for (Pair<String, Integer> pair : interfaces) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    count++;
                }
            }
        }
        List<Pair<String, Integer>> supers = getSuperClasses();
        if (!supers.isEmpty()) {
            for (Pair<String, Integer> pair : supers) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    count++;
                }
            }
        }
        buffer.put((byte) count);
        if (count > 0) {
            for (Pair<String, Integer> pair : supers) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    ApiClassBase superClass = info.getClasses().get(pair.getFirst());
                    assert superClass != null : this;
                    ApiDatabase.put3ByteInt(buffer, superClass.index);
                    writeConstraintReference(info.getSdkIndex(api), buffer, false);
                }
            }
            for (Pair<String, Integer> pair : interfaces) {
                int api = pair.getSecond();
                if (api > getSince()) {
                    ApiClassBase interfaceClass = info.getClasses().get(pair.getFirst());
                    assert interfaceClass != null : this;
                    ApiDatabase.put3ByteInt(buffer, interfaceClass.index);
                    writeConstraintReference(info.getSdkIndex(api), buffer, false);
                }
            }
        }
    }

    private static void writeConstraintReference(
            int index,
            ByteBuffer buffer,
            boolean hasExtraByte) {
        boolean isShort = index >= (1 << 6);
        if (isShort) {
            int left = index >> 8 | ApiDatabase.IS_SHORT_FLAG;
            if (hasExtraByte) {
                left |= ApiDatabase.HAS_EXTRA_BYTE_FLAG;
            }
            buffer.put((byte) left);
            buffer.put((byte) (index & 0xFF));
        } else {
            // We can fit in one byte
            if (hasExtraByte) {
                index |= ApiDatabase.HAS_EXTRA_BYTE_FLAG;
            }
            buffer.put((byte) index);
        }
    }

    @Override
    int computeExtraStorageNeeded(Api<? extends ApiClassBase> info) {
        int estimatedSize = 0;

        initializeMembers(info);

        // Estimate the size of interfaces; this should be
        //  2 + 4 * (getAllInterfaces(info).size());
        // but getAllInterfaces() isn't cheap, and we really don't need
        // this precision here. For the entire SDK, the average number of
        // interfaces is 0.5 interfaces per class -- there are some that
        // inherit quite a few (12 is the current max), but the average
        // is 3237 / 5839. So for now we'll just set aside one interface
        // per class.
        estimatedSize += 6;

        if (getSuperClasses().size() > 1) {
            estimatedSize += 2 + 4 * (getSuperClasses().size());
        }

        for (String member : members) {
            estimatedSize += member.length();
            estimatedSize += 16;
        }

        return estimatedSize;
    }

    void initializeMembers(Api<? extends ApiClassBase> info) {
        Set<String> allMethods = getAllMethods(info);
        Set<String> allFields = getAllFields(info);
        List<String> members = new ArrayList<>(allMethods.size() + allFields.size());

        if (STRIP_MEMBERS) {
            for (String member : allMethods) {
                if (getMethodSince(member, info) != getSince()
                        || getMemberDeprecatedIn(member, info) != getDeprecatedIn()
                        || getMemberRemovedIn(member, info) != getRemovedIn()
                        || getMemberSdks(member, info) != null) {
                    members.add(member);
                }
            }

            for (String member : allFields) {
                if (getFieldSince(member, info) != getSince()
                        || getMemberDeprecatedIn(member, info) != getDeprecatedIn()
                        || getMemberRemovedIn(member, info) != getRemovedIn()
                        || getMemberSdks(member, info) != null) {
                    members.add(member);
                }
            }
        } else {
            members.addAll(allMethods);
            members.addAll(allFields);
        }

        if (USE_HASH_CODES) {
            // Whether we want to force names. If we wanted to be able to
            // return names from the database, we'd need this. Currently, this
            // is only needed for one known case: android.Manifest.permission, so
            // we special case it for that instead of keeping *all* removed
            // APIs (which adds up to >1 MB in entry data, which again
            // is currently unused.)
            // cls.getMemberRemovedIn(member, info) > 0 || cls.getMemberRemovedIn(member, info) > 0
            includeNames = getName().equals("android/Manifest$permission");

            // Also look for hashcode conflicts; that's another reason to use full
            // names. There are no hash code conflicts anywhere in Android 33, and it's
            // unlikely since we only need to have unique hash codes within each single
            // class, but this code is here to make sure the API database doesn't break
            // sometime in the future if a hash code conflict was introduced.
            Set<Integer> hashCodes = new HashSet<>();
            if (ApiClass.USE_HASH_CODES && !includeNames) {
                for (String member : members) {
                    if (!hashCodes.add(ApiLookup.signatureHashCode(member, null))) {
                        includeNames = true;
                        break;
                    }
                }
            }
            if (includeNames) {
                // Alphabetize by member name for binary search on name
                Collections.sort(members);
            } else {
                // Alphabetize by hashcode for binary search on hash codes
                members.sort(Comparator.comparingInt(o -> ApiLookup.signatureHashCode(o, null)));
            }
        } else {
            // Alphabetize by member name for binary search on name
            Collections.sort(members);
        }

        this.members = members;
    }

    /**
     * Writes out the member-entry for a particular member.
     *
     * <p>First it writes out the name of the reference (or, for some entries, just the hash code
     * which uniquely identifies it), and then it writes out the API data for that member -- the
     * {@link ApiConstraint}, and optionally the removed-in and deprecated-in versions.
     */
    @Override
    void writeMemberData(Api<? extends ApiClassBase> info, String member, ByteBuffer buffer) {
        int since;
        if (member.indexOf('(') >= 0) {
            since = getMethodSince(member, info);
        } else {
            since = getFieldSince(member, info);
        }
        if (since == 0) {
            assert false : getName() + ':' + member;
            since = 1;
        }

        int deprecatedIn = getMemberDeprecatedIn(member, info);
        assert deprecatedIn >= 0 : "Invalid deprecatedIn " + deprecatedIn + " for " + member;
        int removedIn = getMemberRemovedIn(member, info);
        assert removedIn >= 0 : "Invalid removedIn " + removedIn + " for " + member;
        String sdks = getMemberSdks(member, info);

        if (USE_HASH_CODES && !includeNames) {
            int hashCode = ApiLookup.signatureHashCode(member, null);
            buffer.putInt(hashCode);
        } else {
            byte[] signature = member.getBytes(StandardCharsets.UTF_8);
            for (byte b : signature) {
                // Make sure all signatures are really just simple ASCII.
                assert b == (b & 0x7f) : member;
                buffer.put(b);
                // Skip types on methods
                if (b == (byte) ')') {
                    break;
                }
            }
            buffer.put((byte) 0);
        }
        writeSinceDeprecatedInRemovedIn(info, buffer, since, deprecatedIn, removedIn, sdks);
    }

    private static void writeSinceDeprecatedInRemovedIn(
            Api<? extends ApiClassBase> info,
            ByteBuffer buffer,
            int since,
            int deprecatedIn,
            int removedIn,
            String sdks) {
        assert deprecatedIn == (deprecatedIn & ApiDatabase.API_MASK); // Must fit in 7 bits.
        assert removedIn == (removedIn & ApiDatabase.API_MASK); // Must fit in 7 bits.

        boolean isDeprecated = deprecatedIn > 0;
        boolean isRemoved = removedIn > 0;
        // Writing "since" and, optionally, "deprecatedIn" and "removedIn".

        short sinceIndex;
        if (sdks != null) {
            sinceIndex = info.getSdkIndex(sdks);
        } else {
            // Convert since API level into sinceIndex
            sinceIndex = info.getSdkIndex(since);
        }
        // Top two bits in from field is (1) is short, and (2) continues with deprecated/removedIn
        boolean sinceShort = sinceIndex >= (1 << 6);
        if (sinceShort) {
            int left = sinceIndex >> 8 | ApiDatabase.IS_SHORT_FLAG;
            if (isDeprecated || isRemoved) {
                left |= ApiDatabase.HAS_EXTRA_BYTE_FLAG;
            }
            buffer.put((byte) left);
            buffer.put((byte) (sinceIndex & 0xFF));
        } else {
            // We can fit in one byte
            if (isDeprecated || isRemoved) {
                sinceIndex |= ApiDatabase.HAS_EXTRA_BYTE_FLAG;
            }
            buffer.put((byte) sinceIndex);
        }

        if (isDeprecated || isRemoved) {
            int deprecatedIndex = info.getSdkIndex(deprecatedIn);
            writeConstraintReference(deprecatedIndex, buffer, isRemoved);
            if (isRemoved) {
                int removedIndex = info.getSdkIndex(removedIn);
                writeConstraintReference(removedIndex, buffer, false);
            }
        }
    }

    /* This code can be used to scan through all the fields and look for fields
       that have moved to a higher class:
            Field android/view/MotionEvent#CREATOR has api=1 but parent android/view/InputEvent provides it as 9
            Field android/provider/ContactsContract$CommonDataKinds$Organization#PHONETIC_NAME has api=5 but parent android/provider/ContactsContract$ContactNameColumns provides it as 11
            Field android/widget/ListView#CHOICE_MODE_MULTIPLE has api=1 but parent android/widget/AbsListView provides it as 11
            Field android/widget/ListView#CHOICE_MODE_NONE has api=1 but parent android/widget/AbsListView provides it as 11
            Field android/widget/ListView#CHOICE_MODE_SINGLE has api=1 but parent android/widget/AbsListView provides it as 11
            Field android/view/KeyEvent#CREATOR has api=1 but parent android/view/InputEvent provides it as 9
       This is used for example in the ApiDetector to filter out warnings which result
       when people follow Eclipse's advice to replace
            ListView.CHOICE_MODE_MULTIPLE
       references with
            AbsListView.CHOICE_MODE_MULTIPLE
       since the latter has API=11 and the former has API=1; since the constant is unchanged
       between the two, and the literal is copied into the class, using the AbsListView
       reference works.
    public void checkFields(Api info) {
        fieldLoop:
        for (String field : mFields.keySet()) {
            Integer since = getField(field, info);
            if (since == null || since == Integer.MAX_VALUE) {
                continue;
            }

            for (Pair<String, Integer> superClass : mSuperClasses) {
                ApiClass cls = info.getClass(superClass.getFirst());
                assert cls != null : superClass.getSecond();
                if (cls != null) {
                    Integer superSince = cls.getField(field, info);
                    if (superSince == Integer.MAX_VALUE) {
                        continue;
                    }

                    if (superSince != null && superSince > since) {
                        String declaredIn = cls.findFieldDeclaration(info, field);
                        System.out.println("Field " + getName() + "#" + field + " has api="
                                + since + " but parent " + declaredIn + " provides it as "
                                + superSince);
                        continue fieldLoop;
                    }
                }
            }

            // Get methods from implemented interfaces as well;
            for (Pair<String, Integer> superClass : mInterfaces) {
                ApiClass cls = info.getClass(superClass.getFirst());
                assert cls != null : superClass.getSecond();
                if (cls != null) {
                    Integer superSince = cls.getField(field, info);
                    if (superSince == Integer.MAX_VALUE) {
                        continue;
                    }
                    if (superSince != null && superSince > since) {
                        String declaredIn = cls.findFieldDeclaration(info, field);
                        System.out.println("Field " + getName() + "#" + field + " has api="
                                + since + " but parent " + declaredIn + " provides it as "
                                + superSince);
                        continue fieldLoop;
                    }
                }
            }
        }
    }

    private String findFieldDeclaration(Api info, String name) {
        if (mFields.containsKey(name)) {
            return getName();
        }
        for (Pair<String, Integer> superClass : mSuperClasses) {
            ApiClass cls = info.getClass(superClass.getFirst());
            assert cls != null : superClass.getSecond();
            if (cls != null) {
                String declaredIn = cls.findFieldDeclaration(info, name);
                if (declaredIn != null) {
                    return declaredIn;
                }
            }
        }

        // Get methods from implemented interfaces as well;
        for (Pair<String, Integer> superClass : mInterfaces) {
            ApiClass cls = info.getClass(superClass.getFirst());
            assert cls != null : superClass.getSecond();
            if (cls != null) {
                String declaredIn = cls.findFieldDeclaration(info, name);
                if (declaredIn != null) {
                    return declaredIn;
                }
            }
        }

        return null;
    }
    */
}
