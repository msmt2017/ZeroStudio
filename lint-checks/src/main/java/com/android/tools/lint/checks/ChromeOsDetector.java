/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED;
import static com.android.xml.AndroidManifest.ATTRIBUTE_RESIZEABLE_ACTIVITY;
import static com.android.xml.AndroidManifest.ATTRIBUTE_SCREEN_ORIENTATION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_USES_PERMISSION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.NotLibraryProject;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Detects various issues for Chrome OS devices. */
public class ChromeOsDetector extends Detector implements XmlScanner {
    private static final Implementation XML_IMPLEMENTATION =
            new Implementation(ChromeOsDetector.class, Scope.MANIFEST_SCOPE);

    public static final String KEY_FEATURE_NAME = "featureName";

    /** Using hardware unsupported by Chrome OS devices */
    public static final Issue UNSUPPORTED_CHROME_OS_HARDWARE =
            Issue.create(
                            "UnsupportedChromeOsHardware",
                            "Unsupported Chrome OS Hardware Feature",
                            "The `<uses-feature>` element should not require this unsupported large screen "
                                    + "hardware feature. Any <uses-feature> not explicitly marked with `required=\"false\"` "
                                    + "is necessary on the device to be installed on. Ensure that any features that might "
                                    + "prevent it from being installed on a ChromeOS, large screen, or foldable device are "
                                    + "reviewed and marked as not required in the manifest.",
                            Category.CHROME_OS,
                            6,
                            Severity.ERROR,
                            XML_IMPLEMENTATION)
                    .setEnabledByDefault(false)
                    .addMoreInfo(
                            "https://developer.android.com/topic/arc/manifest.html#incompat-entries");

    /** Permission implies required hardware unsupported by Chrome OS */
    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create(
                            "PermissionImpliesUnsupportedChromeOsHardware",
                            "Permission Implies Unsupported Chrome OS Hardware",
                            "The `<uses-permission>` element should not require a permission that implies an "
                                    + "unsupported large screen hardware feature. Google Play assumes that certain "
                                    + "hardware related permissions indicate that the underlying hardware features "
                                    + "are required by default. To fix the issue, consider declaring the corresponding "
                                    + "<uses-feature> element with `required=\"false\"` attribute.",
                            Category.CHROME_OS,
                            3,
                            Severity.ERROR,
                            XML_IMPLEMENTATION)
                    .setEnabledByDefault(true)
                    .addMoreInfo(
                            "https://developer.android.com/topic/arc/manifest.html#implied-features");

    /** Setting activities to be non-resizable */
    public static final Issue NON_RESIZEABLE_ACTIVITY =
            Issue.create(
                            "NonResizeableActivity",
                            "Activity is set to be non-resizeable",
                            "The `<activity>` element should be allowed to be resized to allow "
                                    + "users to take advantage of the multi-window environments available "
                                    + "on larger screen Android devices.\n"
                                    + "\n"
                                    + "To fix the issue, consider declaring the corresponding "
                                    + "activity element with `resizableActivity=\"true\"` attribute.",
                            Category.CORRECTNESS,
                            4,
                            Severity.WARNING,
                            XML_IMPLEMENTATION)
                    .setEnabledByDefault(true)
                    .addMoreInfo("https://developer.android.com/topic/arc/window-management");

    /** Setting activities to be non-resizable */
    public static final Issue SETTING_ORIENTATION_ON_ACTIVITY =
            Issue.create(
                            "LockedOrientationActivity",
                            "Incompatible screenOrientation value",
                            "The `<activity>` element should not be locked to any orientation so "
                                    + "that users can take advantage of the multi-window environments and "
                                    + "larger screens available on Android. To fix the issue, consider declaring the "
                                    + "corresponding activity element with `screenOrientation=\"unspecified\"`"
                                    + "or `fullSensor` attribute.",
                            Category.CORRECTNESS,
                            4,
                            Severity.WARNING,
                            XML_IMPLEMENTATION)
                    .setEnabledByDefault(true)
                    .addMoreInfo("https://developer.android.com/topic/arc/window-management");

    private static final String HARDWARE_FEATURE_CAMERA = "android.hardware.camera";

    private static final String HARDWARE_FEATURE_CAMERA_ANY = "android.hardware.camera.any";

    private static final String HARDWARE_FEATURE_CAMERA_AUTOFOCUS =
            "android.hardware.camera.autofocus";

    private static final String HARDWARE_FEATURE_TELEPHONY = "android.hardware.telephony";

    private static final String ANDROID_PERMISSION_CAMERA = "android.permission.CAMERA";

    // https://developer.android.com/topic/arc/manifest.html#incompat-entries
    private static final String[] UNSUPPORTED_HARDWARE_FEATURES =
            new String[] {
                HARDWARE_FEATURE_CAMERA,
                HARDWARE_FEATURE_CAMERA_AUTOFOCUS,
                "android.hardware.camera.capability.manual_post_processing",
                "android.hardware.camera.capability.manual_sensor",
                "android.hardware.camera.capability.raw",
                "android.hardware.camera.flash",
                "android.hardware.camera.level.full",
                "android.hardware.consumerir",
                "android.hardware.location.gps",
                "android.hardware.nfc",
                "android.hardware.nfc.hce",
                "android.hardware.sensor.barometer",
                HARDWARE_FEATURE_TELEPHONY,
                "android.hardware.telephony.cdma",
                "android.hardware.telephony.gsm",
                "android.hardware.touchscreen",
                "android.hardware.type.automotive",
                "android.hardware.type.television",
                "android.hardware.usb.accessory",
                "android.hardware.usb.host",
                // Partially-supported, only on some Chrome OS devices.
                "android.hardware.sensor.accelerometer",
                "android.hardware.sensor.compass",
                "android.hardware.sensor.gyroscope",
                "android.hardware.sensor.light",
                "android.hardware.sensor.proximity",
                "android.hardware.sensor.stepcounter",
                "android.hardware.sensor.stepdetector",
                // Software features not currently supported on Chrome OS devices.
                "android.software.app_widgets",
                "android.software.device_admin",
                "android.software.home_screen",
                "android.software.input_methods",
                "android.software.leanback",
                "android.software.live_wallpaper",
                "android.software.live_tv",
                "android.software.managed_users",
                "android.software.midi",
                "android.software.sip",
                "android.software.sip.voip"
            };

    private static final Set<String> UNSUPPORTED_ORIENTATIONS =
            new HashSet<>(
                    Arrays.asList("portrait", "reversePortrait", "sensorPortrait", "userPortrait"));

    /** Constructs a new {@link ChromeOsDetector} check */
    public ChromeOsDetector() {}

    /** All permissions that imply unsupported Chrome OS hardware. */
    private List<String> unsupportedHardwareImpliedPermissions;

    /** All Unsupported Chrome OS uses features in use by the current manifest. */
    private Set<String> allUnsupportedChromeOsUsesFeatures;

    /** Set containing unsupported Chrome OS uses-features elements without required="false" */
    private Set<String> unsupportedChromeOsUsesFeatures;

    /** Set containing all non resizrable activities */
    private Set<String> nonResizeableActivities;

    /** Set containing all orientationLocked activities */
    private Set<String> lockedOrientationActivities;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_USES_PERMISSION, NODE_ACTIVITY);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        unsupportedHardwareImpliedPermissions = Lists.newArrayListWithExpectedSize(2);
        unsupportedChromeOsUsesFeatures = Sets.newHashSetWithExpectedSize(2);
        allUnsupportedChromeOsUsesFeatures = Sets.newHashSetWithExpectedSize(2);
        nonResizeableActivities = Sets.newHashSetWithExpectedSize(2);
        lockedOrientationActivities = Sets.newHashSetWithExpectedSize(2);
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        XmlContext xmlContext = (XmlContext) context;

        // Report all unsupported Chrome OS hardware uses-feature.
        // These point to all unsupported Chrome OS uses features that have not be marked
        // required = false;
        if (!unsupportedChromeOsUsesFeatures.isEmpty()
                && xmlContext.isEnabled(UNSUPPORTED_CHROME_OS_HARDWARE)) {
            List<Element> usesFeatureElements =
                    AndroidTvDetector.findUsesFeatureElements(
                            unsupportedChromeOsUsesFeatures, xmlContext.document);
            for (Element element : usesFeatureElements) {
                Attr attrRequired = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
                Location location =
                        attrRequired == null
                                ? xmlContext.getNameLocation(element)
                                : xmlContext.getLocation(attrRequired);
                LintFix fix =
                        fix().set(ANDROID_URI, ATTRIBUTE_REQUIRED, VALUE_FALSE).autoFix().build();
                Incident incident =
                        new Incident(
                                UNSUPPORTED_CHROME_OS_HARDWARE,
                                element,
                                location,
                                "Expecting `android:required=\"false\"` for this hardware "
                                        + "feature that may not be supported by all Chrome OS "
                                        + "devices",
                                fix);
                xmlContext.report(incident, new NotLibraryProject());
            }
        }

        // Report permissions implying unsupported hardware
        if (!unsupportedHardwareImpliedPermissions.isEmpty()
                && xmlContext.isEnabled(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)) {
            Predicate<String> p =
                    (String input) -> {
                        // Filter out all permissions that already have their corresponding
                        // implied hardware declared in the AndroidManifest.xml.
                        String usesFeature =
                                input != null ? getImpliedUnsupportedHardware(input) : null;
                        return usesFeature != null
                                && !allUnsupportedChromeOsUsesFeatures.contains(usesFeature);
                    };

            Collection<String> filteredPermissions =
                    unsupportedHardwareImpliedPermissions.stream()
                            .filter(p)
                            .collect(Collectors.toCollection(HashSet::new));

            List<Element> permissionsWithoutUsesFeatures =
                    AndroidTvDetector.findPermissionElements(
                            filteredPermissions, xmlContext.document);

            for (Element permissionElement : permissionsWithoutUsesFeatures) {
                String name = permissionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                String[] unsupportedHardwareNames = new String[2];
                unsupportedHardwareNames[0] = getImpliedUnsupportedHardware(name);

                for (String unsupportedHardwareName : unsupportedHardwareNames) {
                    if (unsupportedHardwareName != null) {
                        // Currently the permissions automatically assumes a 1 to 1 ratio between
                        // the uses-feature android.hardware.camera to the Camera permission. This
                        // last check looks through the entire `uses-feature` set and determines
                        // if the developer has added either `android.hardware.camera` or
                        // `android.hardware.camera.any` as either of these can be acceptable for
                        // the purposes of this lint check.
                        if (unsupportedHardwareName.equals(HARDWARE_FEATURE_CAMERA)
                                && (allUnsupportedChromeOsUsesFeatures.contains(
                                                HARDWARE_FEATURE_CAMERA_ANY)
                                        || allUnsupportedChromeOsUsesFeatures.contains(
                                                HARDWARE_FEATURE_CAMERA))) {
                            continue;
                        }
                        String message =
                                String.format(
                                        "Permission exists without corresponding hardware `<uses-feature "
                                                + "android:name=\"%1$s\" required=\"false\">` tag",
                                        unsupportedHardwareName);
                        LintFix fix = fix().data(KEY_FEATURE_NAME, unsupportedHardwareName);
                        Incident incident =
                                new Incident(
                                        PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                                        permissionElement,
                                        xmlContext.getNameLocation(permissionElement),
                                        message,
                                        fix);
                        xmlContext.report(incident, new NotLibraryProject());
                    }
                }
            }
        }

        if (!nonResizeableActivities.isEmpty() && xmlContext.isEnabled(NON_RESIZEABLE_ACTIVITY)) {
            generateNonResizeableActivityReports(xmlContext);
        }

        if (!lockedOrientationActivities.isEmpty()
                && xmlContext.isEnabled(SETTING_ORIENTATION_ON_ACTIVITY)) {
            generateLockedOrientationActivityReports(xmlContext);
        }
    }

    @Nullable
    private static String getImpliedUnsupportedHardware(@NonNull String permission) {
        switch (permission) {
            case ANDROID_PERMISSION_CAMERA:
                return HARDWARE_FEATURE_CAMERA;
            case "android.permission.CALL_PHONE":
            case "android.permission.CALL_PRIVILEGED":
            case "android.permission.MODIFY_PHONE_STATE":
            case "android.permission.PROCESS_OUTGOING_CALLS":
            case "android.permission.READ_SMS":
            case "android.permission.RECEIVE_SMS":
            case "android.permission.RECEIVE_MMS":
            case "android.permission.WRITE_SMS":
            case "android.permission.WRITE_APN_SETTINGS":
            case "android.permission.SEND_SMS":
            case "android.permission.RECEIVE_WAP_PUSH":
                return HARDWARE_FEATURE_TELEPHONY;
            default:
                return null;
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String elementName = element.getTagName();

        if (NODE_USES_FEATURE.equals(elementName)) {
            // Ensures that unsupported hardware features aren't required.
            Attr name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (name != null) {
                String featureName = name.getValue();
                if (isUnsupportedHardwareFeature(featureName)) {
                    allUnsupportedChromeOsUsesFeatures.add(featureName);
                    Attr required = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
                    if (required == null || Boolean.parseBoolean(required.getValue())) {
                        unsupportedChromeOsUsesFeatures.add(featureName);
                    }
                }
            }
        } else if (NODE_USES_PERMISSION.equals(elementName)) {
            // Store all <uses-permission> tags that imply unsupported hardware
            String permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (getImpliedUnsupportedHardware(permissionName) != null) {
                unsupportedHardwareImpliedPermissions.add(permissionName);
            }
        } else if (NODE_ACTIVITY.equals(elementName)) {
            Attr resizeableActivity =
                    element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_RESIZEABLE_ACTIVITY);
            if (resizeableActivity != null
                    && !Boolean.parseBoolean(resizeableActivity.getValue())) {
                nonResizeableActivities.add(element.getAttributeNS(ANDROID_URI, ATTR_NAME));
            }

            Attr activityOrientation =
                    element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_SCREEN_ORIENTATION);
            if (activityOrientation != null
                    && UNSUPPORTED_ORIENTATIONS.contains(activityOrientation.getValue())) {
                lockedOrientationActivities.add(element.getAttributeNS(ANDROID_URI, ATTR_NAME));
            }
        }
    }

    private static boolean isUnsupportedHardwareFeature(@NonNull String featureName) {
        for (String prefix : UNSUPPORTED_HARDWARE_FEATURES) {
            if (featureName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void generateNonResizeableActivityReports(@NonNull XmlContext xmlContext) {
        List<Element> activityElements =
                findActivityElements(nonResizeableActivities, xmlContext.document);

        for (Element element : activityElements) {
            Attr attrResizableActivity =
                    element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_RESIZEABLE_ACTIVITY);
            Location location =
                    attrResizableActivity == null
                            ? xmlContext.getNameLocation(element)
                            : xmlContext.getLocation(attrResizableActivity);
            LintFix fix =
                    fix().name("Set resizeableActivity=\"true\"")
                            .set(ANDROID_URI, ATTRIBUTE_RESIZEABLE_ACTIVITY, VALUE_TRUE)
                            .autoFix()
                            .build();
            Incident incident =
                    new Incident(
                            NON_RESIZEABLE_ACTIVITY,
                            element,
                            location,
                            "Expecting `android:resizeableActivity=\"true\"` for this activity "
                                    + "so the user can take advantage of the multi-window environment on "
                                    + "all devices. NOTE: beginning with Android 12, "
                                    + "all apps run in multi-window mode on large screen devices.",
                            fix);
            xmlContext.report(incident, new NotLibraryProject());
        }
    }

    private void generateLockedOrientationActivityReports(@NonNull XmlContext xmlContext) {
        List<Element> activityElements =
                findActivityElements(lockedOrientationActivities, xmlContext.document);

        for (Element element : activityElements) {
            Attr attrResizableActivity =
                    element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_SCREEN_ORIENTATION);
            Location location =
                    attrResizableActivity == null
                            ? xmlContext.getNameLocation(element)
                            : xmlContext.getLocation(attrResizableActivity);
            LintFix fix =
                    fix().name("Set screenOrientation=\"fullSensor\"")
                            .set(ANDROID_URI, ATTRIBUTE_SCREEN_ORIENTATION, "fullSensor")
                            .autoFix()
                            .build();
            Incident incident =
                    new Incident(
                            SETTING_ORIENTATION_ON_ACTIVITY,
                            element,
                            location,
                            "Expecting `android:screenOrientation=\"unspecified\"` or `\"fullSensor\"` for this activity "
                                    + "so the user can use the application in any orientation and provide a great experience on "
                                    + "Chrome OS devices",
                            fix);
            xmlContext.report(incident, new NotLibraryProject());
        }
    }

    @NonNull
    private static List<Element> findActivityElements(
            @NonNull Set<String> activityNames, @NonNull Document document) {
        List<Element> nodes = new ArrayList<>(activityNames.size());
        Element manifestElement = document.getDocumentElement();

        Element applicationElement = null;

        for (Element child : XmlUtils.getSubTags(manifestElement)) {
            if (NODE_APPLICATION.equals(child.getTagName())) {
                applicationElement = child;
            }
        }
        for (Element applicationChild : XmlUtils.getSubTags(applicationElement)) {
            if (NODE_ACTIVITY.equals(applicationChild.getTagName())
                    && activityNames.contains(
                            applicationChild.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                nodes.add(applicationChild);
            }
        }
        return nodes;
    }
}
