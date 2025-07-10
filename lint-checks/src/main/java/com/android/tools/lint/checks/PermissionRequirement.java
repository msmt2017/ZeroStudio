/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.checks.AnnotationDetector.ATTR_ALL_OF;
import static com.android.tools.lint.checks.AnnotationDetector.ATTR_ANY_OF;
import static com.android.tools.lint.checks.AnnotationDetector.ATTR_CONDITIONAL;
import static com.android.tools.lint.detector.api.UastLintUtils.getAnnotationBooleanValue;
import static com.android.tools.lint.detector.api.UastLintUtils.getAnnotationStringValue;
import static com.android.tools.lint.detector.api.UastLintUtils.getAnnotationStringValues;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UAnnotation;
import org.objectweb.asm.tree.AnnotationNode;

/**
 * A permission requirement is a boolean expression of permission names that a caller must satisfy
 * for a given Android API.
 */
public abstract class PermissionRequirement {
    public static final String ATTR_PROTECTION_LEVEL = "protectionLevel";
    public static final String VALUE_DANGEROUS = "dangerous";

    private final int firstApi;
    private final int lastApi;
    private final boolean conditional;

    public static final PermissionRequirement NONE =
            new PermissionRequirement(false, 1, Integer.MAX_VALUE) {
                @Override
                public boolean isSatisfied(@NonNull PermissionHolder available) {
                    return true;
                }

                @Override
                public boolean appliesTo(@NonNull PermissionHolder available) {
                    return false;
                }

                @Override
                public boolean isRevocable(@NonNull PermissionHolder revocable) {
                    return false;
                }

                @Override
                public String toString() {
                    return "None";
                }

                @Override
                protected void addMissingPermissions(
                        @NonNull PermissionHolder available, @NonNull Set<String> result) {}

                @Override
                protected void addRevocablePermissions(
                        @NonNull Set<String> result, @NonNull PermissionHolder revocable) {}

                @Nullable
                @Override
                public IElementType getOperator() {
                    return null;
                }

                @Override
                public boolean contains(@NotNull String permission) {
                    return false;
                }

                @NonNull
                @Override
                public Iterable<PermissionRequirement> getChildren() {
                    return Collections.emptyList();
                }
            };

    private PermissionRequirement(boolean conditional, int firstApi, int lastApi) {
        this.conditional = conditional;
        this.firstApi = firstApi;
        this.lastApi = lastApi;
    }

    /**
     * Creates a permission requirement on a single specific, unconditional permission which applies
     * to all API levels
     */
    public static PermissionRequirement create(@NonNull String permission) {
        return new Single(permission, false, 1, Integer.MAX_VALUE);
    }

    @NonNull
    public static PermissionRequirement create(@NonNull UAnnotation annotation) {
        boolean conditional = getAnnotationBooleanValue(annotation, ATTR_CONDITIONAL, false);

        int firstApi = 1;
        int lastApi = Integer.MAX_VALUE;

        String range = getAnnotationStringValue(annotation, "apis");
        if (range != null) {
            // Currently only support the syntax "a..b" where a and b are inclusive end points
            // and where "a" and "b" are optional
            int index = range.indexOf("..");
            if (index != -1) {
                try {
                    if (index > 0) {
                        firstApi = Integer.parseInt(range.substring(0, index));
                    }
                    if (index + 2 < range.length()) {
                        lastApi = Integer.parseInt(range.substring(index + 2));
                    }
                } catch (NumberFormatException ignore) {
                }
            }
        }

        String value = getAnnotationStringValue(annotation, ATTR_VALUE);
        if (value != null && !value.isEmpty()) {
            return new Single(value, conditional, firstApi, lastApi);
        }

        String[] anyOf = getAnnotationStringValues(annotation, ATTR_ANY_OF);
        if (anyOf != null) {
            if (anyOf.length > 1) {
                return new Many(JavaTokenType.OROR, anyOf, conditional, firstApi, lastApi);
            } else if (anyOf.length == 1) {
                return new Single(anyOf[0], conditional, firstApi, lastApi);
            }
        }

        String[] allOf = getAnnotationStringValues(annotation, ATTR_ALL_OF);
        if (allOf != null) {
            if (allOf.length > 1) {
                return new Many(JavaTokenType.ANDAND, allOf, conditional, firstApi, lastApi);
            } else if (allOf.length == 1) {
                return new Single(allOf[0], conditional, firstApi, lastApi);
            }
        }

        return NONE;
    }

    /** Converts from an ASM List<Object> to a String[] */
    @Nullable
    private static String[] getAsmArray(@Nullable Object value) {
        if (value instanceof List) {
            List<String> permissions = new ArrayList<>();
            //noinspection rawtypes
            for (Object v : ((List) value)) {
                permissions.add(v.toString());
            }
            return permissions.toArray(new String[0]);
        }

        return null;
    }

    @NonNull
    public static PermissionRequirement create(@NonNull AnnotationNode annotation) {
        String value = null;
        String[] anyOf = null;
        String[] allOf = null;
        boolean conditional = false;
        int firstApi = 1;
        int lastApi = Integer.MAX_VALUE;
        List<Object> values = annotation.values;
        if (values == null || values.size() % 2 == 1) {
            return NONE;
        }
        for (int i = 0; i < values.size(); i += 2) {
            Object k = values.get(i);
            Object v = values.get(i + 1);
            if (ATTR_VALUE.equals(k)) {
                value = v.toString();
            } else if (ATTR_ANY_OF.equals(k)) {
                anyOf = getAsmArray(v);
            } else if (ATTR_ALL_OF.equals(k)) {
                allOf = getAsmArray(v);
            } else if (ATTR_CONDITIONAL.equals(k)) {
                if (v == Boolean.TRUE || VALUE_TRUE.equals(v)) {
                    conditional = true;
                }
            }
        }
        if (value != null && !value.isEmpty()) {
            return new Single(value, conditional, firstApi, lastApi);
        } else if (anyOf != null) {
            if (anyOf.length > 1) {
                return new Many(JavaTokenType.OROR, anyOf, conditional, firstApi, lastApi);
            } else if (anyOf.length == 1) {
                return new Single(anyOf[0], conditional, firstApi, lastApi);
            }
        } else if (allOf != null) {
            if (allOf.length > 1) {
                return new Many(JavaTokenType.ANDAND, allOf, conditional, firstApi, lastApi);
            } else if (allOf.length == 1) {
                return new Single(allOf[0], conditional, firstApi, lastApi);
            }
        }

        return NONE;
    }

    /**
     * Returns false if this permission does not apply given the specified minimum and target sdk
     * versions
     *
     * @param available the permission holder which also knows the min and target versions
     * @return true if this permission requirement applies for the given versions
     */
    protected boolean appliesTo(@NonNull PermissionHolder available) {
        AndroidVersion minSdkVersion = available.getMinSdkVersion();
        if (minSdkVersion.getFeatureLevel() > lastApi) {
            return false;
        }

        AndroidVersion targetSdkVersion = available.getTargetSdkVersion();
        return targetSdkVersion.getFeatureLevel() >= firstApi;
    }

    /**
     * Returns the level of the last applicable API level for this permission requirement, if the
     * requirement no longer applies. Returns {@link Integer#MAX_VALUE} if the permission is not
     * specific to a range and applies for all current API levels.
     *
     * @return the last applicable API level, or {@link Integer#MAX_VALUE} if applies anywhere.
     */
    public int getLastApplicableApi() {
        return lastApi;
    }

    /**
     * Returns the level of the first applicable API level, or 1 if the requirement does not have a
     * specific API range.
     *
     * @return the first applicable API level
     */
    public int getFirstApplicableApi() {
        return firstApi;
    }

    /**
     * Returns whether this requirement is conditional, meaning that there are some circumstances in
     * which the requirement is not necessary. For example, consider {@code
     * android.app.backup.BackupManager.dataChanged(java.lang.String)} . Here the {@code
     * android.permission.BACKUP} is required but only if the argument is not your own package.
     *
     * <p>This is used to handle permissions differently between the "missing" and "unused" checks.
     * When checking for missing permissions, we err on the side of caution: if you are missing a
     * permission, but the permission is conditional, you may not need it so we may not want to
     * complain. However, when looking for unused permissions, we don't want to flag the conditional
     * permissions as unused since they may be required.
     *
     * @return true if this requirement is conditional
     */
    public boolean isConditional() {
        return conditional;
    }

    /**
     * Returns whether this requirement is for a single permission (rather than a boolean expression
     * such as one permission or another.)
     *
     * @return true if this requirement is just a simple permission name
     */
    public boolean isSingle() {
        return true;
    }

    /**
     * Whether the permission requirement is satisfied given the set of granted permissions
     *
     * @param available the available permissions
     * @return true if all permissions specified by this requirement are available
     */
    public abstract boolean isSatisfied(@NonNull PermissionHolder available);

    /**
     * Generates a String representation of this permission requirement which can be used to
     * recreate the permission requirement via {@link #deserialize}
     */
    @NonNull
    public String serialize() {
        return serialize(this);
    }

    /** Describes the missing permissions (e.g. "P1, P2 and P3") */
    public String describeMissingPermissions(@NonNull PermissionHolder available) {
        return "";
    }

    /** Returns the missing permissions (e.g. {"P1", "P2", "P3"} */
    public Set<String> getMissingPermissions(@NonNull PermissionHolder available) {
        Set<String> result = Sets.newHashSet();
        addMissingPermissions(available, result);
        return result;
    }

    protected abstract void addMissingPermissions(
            @NonNull PermissionHolder available, @NonNull Set<String> result);

    /** Returns the permissions in the requirement that are revocable */
    public Set<String> getRevocablePermissions(@NonNull PermissionHolder revocable) {
        Set<String> result = Sets.newHashSet();
        addRevocablePermissions(result, revocable);
        return result;
    }

    protected abstract void addRevocablePermissions(
            @NonNull Set<String> result, @NonNull PermissionHolder revocable);

    /**
     * Returns whether this permission is revocable
     *
     * @param revocable the set of revocable permissions
     * @return true if a user can revoke the permission
     */
    public abstract boolean isRevocable(@NonNull PermissionHolder revocable);

    /**
     * For permission requirements that combine children, the operator to combine them with; null
     * for leaf nodes
     */
    @Nullable
    public abstract IElementType getOperator();

    /** Returns true if this permission requirement involves the given permission name */
    public abstract boolean contains(@NonNull String permission);

    /** Returns nested requirements, combined via {@link #getOperator()} */
    @NonNull
    public abstract Iterable<PermissionRequirement> getChildren();

    /** Require a single permission */
    private static class Single extends PermissionRequirement {
        public final String name;

        public Single(@NonNull String name, boolean conditional, int firstApi, int lastApi) {
            super(conditional, firstApi, lastApi);
            this.name = name;
        }

        @Override
        public boolean isRevocable(@NonNull PermissionHolder revocable) {
            return revocable.isRevocable(name) || isRevocableSystemPermission(name);
        }

        @Nullable
        @Override
        public IElementType getOperator() {
            return null;
        }

        @Override
        public boolean contains(@NotNull String permission) {
            return name.equals(permission);
        }

        @NonNull
        @Override
        public Iterable<PermissionRequirement> getChildren() {
            return Collections.emptyList();
        }

        @Override
        public boolean isSingle() {
            return true;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean isSatisfied(@NonNull PermissionHolder available) {
            return available.hasPermission(name) || !appliesTo(available);
        }

        @Override
        public String describeMissingPermissions(@NonNull PermissionHolder available) {
            return isSatisfied(available) ? "" : name;
        }

        @Override
        protected void addMissingPermissions(
                @NonNull PermissionHolder available, @NonNull Set<String> missing) {
            if (!isSatisfied(available)) {
                missing.add(name);
            }
        }

        @Override
        protected void addRevocablePermissions(
                @NonNull Set<String> result, @NonNull PermissionHolder revocable) {
            if (isRevocable(revocable)) {
                result.add(name);
            }
        }
    }

    protected static void appendOperator(StringBuilder sb, IElementType operator) {
        sb.append(' ');
        if (operator == JavaTokenType.ANDAND) {
            sb.append("and");
        } else if (operator == JavaTokenType.OROR) {
            sb.append("or");
        } else {
            assert operator == JavaTokenType.XOR : operator;
            sb.append("xor");
        }
        sb.append(' ');
    }

    /** Require a series of permissions, all with the same operator. */
    private static class Many extends PermissionRequirement {
        public final IElementType operator;
        public final List<PermissionRequirement> permissions;

        public Many(
                @NonNull IElementType operator,
                String[] names,
                boolean conditional,
                int firstApi,
                int lastApi) {
            super(conditional, firstApi, lastApi);
            assert operator == JavaTokenType.OROR || operator == JavaTokenType.ANDAND : operator;
            assert names.length >= 2;
            this.operator = operator;
            this.permissions = Lists.newArrayListWithExpectedSize(names.length);
            for (String name : names) {
                permissions.add(new Single(name, conditional, firstApi, lastApi));
            }
        }

        @Override
        public boolean isSingle() {
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append(permissions.get(0));

            for (int i = 1; i < permissions.size(); i++) {
                appendOperator(sb, operator);
                sb.append(permissions.get(i));
            }

            return sb.toString();
        }

        @Override
        public boolean isSatisfied(@NonNull PermissionHolder available) {
            if (operator == JavaTokenType.ANDAND) {
                for (PermissionRequirement requirement : permissions) {
                    if (!requirement.isSatisfied(available) && requirement.appliesTo(available)) {
                        return false;
                    }
                }
                return true;
            } else {
                assert operator == JavaTokenType.OROR : operator;
                for (PermissionRequirement requirement : permissions) {
                    if (requirement.isSatisfied(available) || !requirement.appliesTo(available)) {
                        return true;
                    }
                }
                return false;
            }
        }

        @Override
        public String describeMissingPermissions(@NonNull PermissionHolder available) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (PermissionRequirement requirement : permissions) {
                if (!requirement.isSatisfied(available)) {
                    if (first) {
                        first = false;
                    } else {
                        appendOperator(sb, operator);
                    }
                    sb.append(requirement.describeMissingPermissions(available));
                }
            }
            return sb.toString();
        }

        @Override
        protected void addMissingPermissions(
                @NonNull PermissionHolder available, @NonNull Set<String> missing) {
            for (PermissionRequirement requirement : permissions) {
                if (!requirement.isSatisfied(available)) {
                    requirement.addMissingPermissions(available, missing);
                }
            }
        }

        @Override
        protected void addRevocablePermissions(
                @NonNull Set<String> result, @NonNull PermissionHolder revocable) {
            for (PermissionRequirement requirement : permissions) {
                requirement.addRevocablePermissions(result, revocable);
            }
        }

        @Override
        public boolean isRevocable(@NonNull PermissionHolder revocable) {
            // TODO: Pass in the available set of permissions here, and if
            // the operator is JavaTokenType.OROR, only return revocable=true
            // if an unsatisfied permission is also revocable. In other words,
            // if multiple permissions are allowed, and some of them are satisfied and
            // not revocable the overall permission requirement is not revocable.
            for (PermissionRequirement requirement : permissions) {
                if (requirement.isRevocable(revocable)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        @Override
        public IElementType getOperator() {
            return operator;
        }

        @Override
        public boolean contains(@NotNull String permission) {
            for (PermissionRequirement requirement : permissions) {
                if (requirement.contains(permission)) {
                    return true;
                }
            }
            return false;
        }

        @NonNull
        @Override
        public Iterable<PermissionRequirement> getChildren() {
            return permissions;
        }
    }

    @Nullable
    private static String getSerializationPrefix(@NonNull PermissionRequirement requirement) {
        boolean conditional = requirement.conditional;
        int firstApi = requirement.getFirstApplicableApi();
        int lastApi = requirement.getLastApplicableApi();
        if (firstApi != 1 || lastApi != Integer.MAX_VALUE) {
            StringBuilder sb = new StringBuilder();
            sb.append(firstApi);
            sb.append("..");
            if (lastApi != Integer.MAX_VALUE) {
                sb.append(lastApi);
            }
            sb.append(';');
            if (conditional) {
                sb.append('?');
            }
            return sb.toString();
        } else if (conditional) {
            return "?";
        }
        return null;
    }

    /**
     * Generates a String representation of this permission requirement which can be used to
     * recreate the permission requirement via {@link #deserialize}
     */
    @NonNull
    public static String serialize(@NonNull PermissionRequirement requirement) {
        String prefix = getSerializationPrefix(requirement);
        if (requirement instanceof Single) {
            String name = ((Single) requirement).name;
            if (prefix != null) {
                return prefix + name;
            } else {
                return name;
            }
        } else if (requirement instanceof Many) {
            Many r = (Many) requirement;
            StringBuilder sb = new StringBuilder(100);
            if (prefix != null) {
                sb.append(prefix);
            }
            IElementType operator = r.operator;
            if (operator == JavaTokenType.ANDAND) {
                sb.append('&');
            } else if (operator == JavaTokenType.OROR) {
                sb.append('|');
            } else {
                assert operator == JavaTokenType.XOR : operator;
                sb.append('^');
            }
            boolean first = true;
            for (PermissionRequirement single : r.permissions) {
                // We don't support arbitrary nesting yet (limitation of the
                // @PermissionRequirement annotation which can't nest).
                assert single instanceof Single;
                if (first) {
                    first = false;
                } else {
                    sb.append(',');
                }
                sb.append(single.serialize());
            }
            return sb.toString();
        } else {
            assert requirement == NONE;
            return "";
        }
    }

    /** Reconstitutes a permission requirement from a previously {@link #serialize()} string */
    @NonNull
    public static PermissionRequirement deserialize(@NonNull String s) {
        if (s.isEmpty()) {
            return NONE;
        }
        int firstApi = 1;
        int lastApi = Integer.MAX_VALUE;
        boolean conditional = false;
        int start = 0;
        char operatorChar = s.charAt(start++);
        if (Character.isDigit(operatorChar)) {
            // Range
            int firstEnd = s.indexOf('.');
            int lastStart = firstEnd + 2;
            int lastEnd = s.indexOf(';', lastStart);
            firstApi = Integer.parseInt(s.substring(0, firstEnd));
            if (lastStart != lastEnd) {
                lastApi = Integer.parseInt(s.substring(lastStart, lastEnd));
            }
            start = lastEnd + 1;
            operatorChar = s.charAt(start++);
        }
        if (operatorChar == '?') {
            conditional = true;
            operatorChar = s.charAt(start++);
        }
        IElementType operator;
        if (operatorChar == '&') {
            operator = JavaTokenType.ANDAND;
        } else if (operatorChar == '|') {
            operator = JavaTokenType.OROR;
        } else if (operatorChar == '^') {
            operator = JavaTokenType.XOR;
        } else {
            start--;
            String name;
            if (start > 0) {
                name = s.substring(start);
            } else {
                name = s;
            }
            return new PermissionRequirement.Single(name, conditional, firstApi, lastApi);
        }

        List<String> names = new ArrayList<>();
        while (true) {
            int end = s.indexOf(',', start);
            if (s.charAt(start) == '?') {
                start++;
            }
            if (end == -1) {
                names.add(s.substring(start));
                break;
            } else {
                names.add(s.substring(start, end));
                start = end + 1;
            }
        }
        return new PermissionRequirement.Many(
                operator, names.toArray(new String[0]), conditional, firstApi, lastApi);
    }

    /**
     * Returns true if the given permission name is a revocable permission for targetSdkVersion &ge;
     * 23
     *
     * @param name permission name
     * @return true if this is a revocable permission
     */
    public static boolean isRevocableSystemPermission(@NonNull String name) {
        return Arrays.binarySearch(REVOCABLE_PERMISSION_NAMES, name) >= 0;
    }

    @VisibleForTesting
    static final String[] REVOCABLE_PERMISSION_NAMES =
            new String[] {
                "android.permission.ACCEPT_HANDOVER",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_MEDIA_LOCATION",
                "android.permission.ACTIVITY_RECOGNITION",
                "android.permission.ANSWER_PHONE_CALLS",
                "android.permission.BLUETOOTH_ADVERTISE",
                "android.permission.BLUETOOTH_CONNECT",
                "android.permission.BLUETOOTH_SCAN",
                "android.permission.BODY_SENSORS",
                "android.permission.BODY_SENSORS_BACKGROUND",
                "android.permission.CALL_PHONE",
                "android.permission.CAMERA",
                "android.permission.NEARBY_WIFI_DEVICES",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.PROCESS_OUTGOING_CALLS",
                "android.permission.READ_CALENDAR",
                "android.permission.READ_CALL_LOG",
                "android.permission.READ_CELL_BROADCASTS",
                "android.permission.READ_CONTACTS",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.READ_MEDIA_AUDIO",
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
                "android.permission.READ_PHONE_NUMBERS",
                "android.permission.READ_PHONE_STATE",
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_MMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.RECEIVE_WAP_PUSH",
                "android.permission.RECORD_AUDIO",
                "android.permission.SEND_SMS",
                "android.permission.USE_SIP",
                "android.permission.UWB_RANGING",
                "android.permission.WRITE_CALENDAR",
                "android.permission.WRITE_CALL_LOG",
                "android.permission.WRITE_CONTACTS",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "com.android.voicemail.permission.ADD_VOICEMAIL",
            };
}
