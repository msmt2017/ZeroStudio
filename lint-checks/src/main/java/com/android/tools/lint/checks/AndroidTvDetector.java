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

import static com.android.SdkConstants.ANDROIDX_LEANBACK_ARTIFACT;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_USES_FEATURE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY_ALIAS;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;
import static com.android.xml.AndroidManifest.NODE_USES_PERMISSION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.XmlUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Detects various issues for Android TV. */
public class AndroidTvDetector extends Detector implements XmlScanner {
    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidTvDetector.class, Scope.MANIFEST_SCOPE);

    public static final String KEY_FEATURE_NAME = "featureName";

    /** Using hardware unsupported by TV */
    public static final Issue UNSUPPORTED_TV_HARDWARE =
            Issue.create(
                            "UnsupportedTvHardware",
                            "Unsupported TV Hardware Feature",
                            "The `<uses-feature>` element should not require this unsupported TV hardware feature. "
                                    + "Any uses-feature not explicitly marked with `required=\"false\"` is necessary on the "
                                    + "device to be installed on. "
                                    + "Ensure that any features that might prevent it from being installed on a TV device "
                                    + "are reviewed and marked as not required in the manifest.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/tv/start/hardware.html#unsupported-features");

    public static final Issue IMPLIED_TOUCHSCREEN_HARDWARE =
            Issue.create(
                            "ImpliedTouchscreenHardware",
                            "Touchscreen not optional",
                            "Apps require the `android.hardware.touchscreen` feature by default. If you want "
                                    + "your app to be available on TV, you must also explicitly declare that a touchscreen "
                                    + "is not required as follows:\n"
                                    + "`<uses-feature android:name=\"android.hardware.touchscreen\" "
                                    + "android:required=\"false\"/>`",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html");

    /** Missing leanback launcher intent filter */
    public static final Issue MISSING_LEANBACK_LAUNCHER =
            Issue.create(
                            "MissingLeanbackLauncher",
                            "Missing Leanback Launcher Intent Filter",
                            "An application intended to run on TV devices must declare a launcher activity "
                                    + "for TV in its manifest using a `android.intent.category.LEANBACK_LAUNCHER` "
                                    + "intent filter.",
                            Category.CORRECTNESS,
                            8,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/tv/start/start.html#tv-activity");

    /** Missing leanback support */
    public static final Issue MISSING_LEANBACK_SUPPORT =
            Issue.create(
                            "MissingLeanbackSupport",
                            "Missing Leanback Support",
                            "The manifest should declare the use of the Leanback user interface required "
                                    + "by Android TV.\n"
                                    + "\n"
                                    + "To fix this, add\n"
                                    + "```xml\n"
                                    + "`<uses-feature android:name=\"android.software.leanback\"\n"
                                    + "               android:required=\"false\" />`\n"
                                    + "```\n"
                                    + "to your manifest.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/tv/start/start.html#leanback-req");

    /** Permission implies required hardware unsupported by TV */
    public static final Issue PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE =
            Issue.create(
                            "PermissionImpliesUnsupportedHardware",
                            "Permission Implies Unsupported Hardware",
                            "The `<uses-permission>` element should not require a permission that implies an "
                                    + "unsupported TV hardware feature. Google Play assumes that certain hardware related "
                                    + "permissions indicate that the underlying hardware features are required by default. "
                                    + "To fix the issue, consider declaring the corresponding `uses-feature` element with "
                                    + "`required=\"false\"` attribute.",
                            Category.CORRECTNESS,
                            3,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/uses-feature-element.html#permissions");

    /** Missing banner attibute */
    public static final Issue MISSING_BANNER =
            Issue.create(
                            "MissingTvBanner",
                            "TV Missing Banner",
                            "A TV application must provide a home screen banner for each localization if it "
                                    + "includes a Leanback launcher intent filter. The banner is the app launch point that "
                                    + "appears on the home screen in the apps and games rows.",
                            Category.CORRECTNESS,
                            7,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/tv/start/start.html#banner");

    public static final String SOFTWARE_FEATURE_LEANBACK = "android.software.leanback";

    public static final String HARDWARE_FEATURE_TOUCHSCREEN = "android.hardware.touchscreen";

    private static final String CATEGORY_LEANBACK_LAUNCHER =
            "android.intent.category.LEANBACK_LAUNCHER";

    private static final String HARDWARE_FEATURE_CAMERA = "android.hardware.camera";

    private static final String HARDWARE_FEATURE_LOCATION_GPS = "android.hardware.location.gps";

    private static final String HARDWARE_FEATURE_TELEPHONY = "android.hardware.telephony";

    private static final String HARDWARE_FEATURE_BLUETOOTH = "android.hardware.bluetooth";

    private static final String ATTR_BANNER = "banner";

    private static final String HARDWARE_FEATURE_MICROPHONE = "android.hardware.microphone";

    // https://developer.android.com/training/tv/start/hardware.html
    private static final String[] UNSUPPORTED_HARDWARE_FEATURES =
            new String[] {
                HARDWARE_FEATURE_TOUCHSCREEN,
                "android.hardware.faketouch",
                HARDWARE_FEATURE_TELEPHONY,
                HARDWARE_FEATURE_CAMERA,
                HARDWARE_FEATURE_BLUETOOTH,
                "android.hardware.nfc",
                HARDWARE_FEATURE_LOCATION_GPS,
                HARDWARE_FEATURE_MICROPHONE,
                "android.hardware.sensors"
            };

    /** Constructs a new {@link AndroidTvDetector} check */
    public AndroidTvDetector() {}

    /** Used for {@link #MISSING_LEANBACK_LAUNCHER} */
    private boolean mHasLeanbackLauncherActivity;

    /** Used for {@link #MISSING_LEANBACK_SUPPORT} */
    private boolean mHasLeanbackSupport;

    /** Whether the app has a leanback-v7 dependency */
    private boolean mHasLeanbackDependency;

    /** Used for {@link #MISSING_BANNER} */
    private boolean mHasApplicationBanner;

    /** No. of activities that have the leanback intent but dont declare banners */
    private int mLeanbackActivitiesWithoutBanners;

    /** All permissions that imply unsupported tv hardware. */
    private List<String> mUnsupportedHardwareImpliedPermissions;

    /** All Unsupported TV uses features in use by the current manifest. */
    private Set<String> mAllUnsupportedTvUsesFeatures;

    /** Set containing unsupported TV uses-features elements without required="false" */
    private Set<String> mUnsupportedTvUsesFeatures;

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                NODE_APPLICATION,
                NODE_ACTIVITY,
                NODE_ACTIVITY_ALIAS,
                NODE_USES_FEATURE,
                NODE_USES_PERMISSION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mHasLeanbackLauncherActivity = false;
        mHasLeanbackSupport = false;
        mHasApplicationBanner = false;
        mLeanbackActivitiesWithoutBanners = 0;
        mUnsupportedHardwareImpliedPermissions = Lists.newArrayListWithExpectedSize(2);
        mUnsupportedTvUsesFeatures = Sets.newHashSetWithExpectedSize(2);
        mAllUnsupportedTvUsesFeatures = Sets.newHashSetWithExpectedSize(2);

        // Check gradle dependency
        mHasLeanbackDependency =
                context.getProject().isGradleProject()
                        && Boolean.TRUE.equals(
                                context.getProject().dependsOn(ANDROIDX_LEANBACK_ARTIFACT));
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        boolean isTvApp =
                mHasLeanbackSupport || mHasLeanbackDependency || mHasLeanbackLauncherActivity;

        if (isTvApp) {
            XmlContext xmlContext = (XmlContext) context;
            // Report an error if there's not at least one leanback launcher intent filter activity
            if (!mHasLeanbackLauncherActivity && xmlContext.isEnabled(MISSING_LEANBACK_LAUNCHER)) {
                // No launch activity
                Element manifestNode = xmlContext.document.getDocumentElement();
                if (manifestNode != null) {
                    xmlContext.report(
                            MISSING_LEANBACK_LAUNCHER,
                            manifestNode,
                            xmlContext.getNameLocation(manifestNode),
                            "Expecting an activity to have `"
                                    + CATEGORY_LEANBACK_LAUNCHER
                                    + "` intent filter");
                }
            }

            // Report an issue if there is no leanback <uses-feature> tag.
            if (!mHasLeanbackSupport && xmlContext.isEnabled(MISSING_LEANBACK_SUPPORT)) {
                Element manifestNode = xmlContext.document.getDocumentElement();
                if (manifestNode != null) {
                    xmlContext.report(
                            MISSING_LEANBACK_SUPPORT,
                            manifestNode,
                            xmlContext.getNameLocation(manifestNode),
                            "Expecting `<uses-feature android:name=\"android.software.leanback\" "
                                    + "android:required=\"false\" />` tag");
                }
            }

            // Report missing banners
            if (!mHasApplicationBanner // no application banner
                    && mLeanbackActivitiesWithoutBanners > 0 // leanback activity without banner
                    && xmlContext.isEnabled(MISSING_BANNER)) {
                Node applicationElement = getApplicationElement(xmlContext.document);
                if (applicationElement != null) {
                    LintFix fix = fix().set().todo(ANDROID_URI, "banner").build();
                    xmlContext.report(
                            MISSING_BANNER,
                            applicationElement,
                            xmlContext.getNameLocation(applicationElement),
                            "Expecting `android:banner` with the `<application>` tag or each "
                                    + "Leanback launcher activity",
                            fix);
                }
            }

            // Report all unsupported TV hardware uses-feature.
            // These point to all unsupported tv uses features that have not be marked
            // required = false;
            if (!mUnsupportedTvUsesFeatures.isEmpty()
                    && xmlContext.isEnabled(UNSUPPORTED_TV_HARDWARE)) {
                List<Element> usesFeatureElements =
                        findUsesFeatureElements(mUnsupportedTvUsesFeatures, xmlContext.document);
                for (Element element : usesFeatureElements) {
                    Attr attrRequired = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
                    Location location =
                            attrRequired == null
                                    ? xmlContext.getNameLocation(element)
                                    : xmlContext.getLocation(attrRequired);
                    LintFix fix =
                            fix().set(ANDROID_URI, ATTRIBUTE_REQUIRED, VALUE_FALSE)
                                    .autoFix()
                                    .build();
                    xmlContext.report(
                            UNSUPPORTED_TV_HARDWARE,
                            element,
                            location,
                            "Expecting `android:required=\"false\"` for this hardware "
                                    + "feature that may not be supported by all Android TVs",
                            fix);
                }
            }

            // touchscreen is a special implied hardware that is enabled by default
            // unless specifically marked with required="false"
            if (!mAllUnsupportedTvUsesFeatures.contains(HARDWARE_FEATURE_TOUCHSCREEN)
                    && xmlContext.isEnabled(IMPLIED_TOUCHSCREEN_HARDWARE)) {
                Element manifestElement = xmlContext.document.getDocumentElement();
                xmlContext.report(
                        IMPLIED_TOUCHSCREEN_HARDWARE,
                        manifestElement,
                        xmlContext.getNameLocation(manifestElement),
                        "Hardware feature `android.hardware.touchscreen` not explicitly marked "
                                + "as optional ");
            }

            // Report permissions implying unsupported hardware
            if (!mUnsupportedHardwareImpliedPermissions.isEmpty()
                    && xmlContext.isEnabled(PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE)) {

                Collection<String> filteredPermissions =
                        Collections2.filter(
                                mUnsupportedHardwareImpliedPermissions,
                                input -> {
                                    // Filter out all permissions that already have their
                                    // corresponding implied hardware declared in
                                    // the AndroidManifest.xml
                                    String usesFeature =
                                            input != null
                                                    ? getImpliedUnsupportedHardware(input)
                                                    : null;
                                    return usesFeature != null
                                            && !mAllUnsupportedTvUsesFeatures.contains(usesFeature);
                                });

                List<Element> permissionsWithoutUsesFeatures =
                        findPermissionElements(filteredPermissions, xmlContext.document);

                for (Element permissionElement : permissionsWithoutUsesFeatures) {
                    String name = permissionElement.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    String unsupportedHardwareName = getImpliedUnsupportedHardware(name);

                    if (unsupportedHardwareName != null) {
                        String message =
                                String.format(
                                        "Permission exists without corresponding hardware `<uses-feature "
                                                + "android:name=\"%1$s\" required=\"false\">` tag",
                                        unsupportedHardwareName);
                        LintFix fix = fix().data(KEY_FEATURE_NAME, unsupportedHardwareName);
                        xmlContext.report(
                                PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
                                permissionElement,
                                xmlContext.getNameLocation(permissionElement),
                                message,
                                fix);
                    }
                }
            }
        }
    }

    @Nullable
    private static String getImpliedUnsupportedHardware(@NonNull String permission) {
        switch (permission) {
            case "android.permission.BLUETOOTH":
                return HARDWARE_FEATURE_BLUETOOTH;
            case "android.permission.BLUETOOTH_ADMIN":
                return HARDWARE_FEATURE_BLUETOOTH;
            case "android.permission.CAMERA":
                return HARDWARE_FEATURE_CAMERA;
            case "android.permission.RECORD_AUDIO":
                return HARDWARE_FEATURE_MICROPHONE;
            case "android.permission.ACCESS_FINE_LOCATION":
                return HARDWARE_FEATURE_LOCATION_GPS;
            case "android.permission.CALL_PHONE":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.CALL_PRIVILEGED":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.PROCESS_OUTGOING_CALLS":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.READ_SMS":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.RECEIVE_SMS":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.RECEIVE_MMS":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.RECEIVE_WAP_PUSH":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.SEND_SMS":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.WRITE_APN_SETTINGS":
                return HARDWARE_FEATURE_TELEPHONY;
            case "android.permission.WRITE_SMS":
                return HARDWARE_FEATURE_TELEPHONY;
            default:
                return null;
        }
    }

    static List<Element> findPermissionElements(Collection<String> permissions, Document document) {
        Node manifestElement = document.getDocumentElement();
        if (manifestElement == null) {
            return Collections.emptyList();
        }
        List<Element> nodes = new ArrayList<>(permissions.size());
        for (Element child : XmlUtils.getSubTags(manifestElement)) {
            if (TAG_USES_PERMISSION.equals(child.getTagName())
                    && permissions.contains(child.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                nodes.add(child);
            }
        }
        return nodes;
    }

    /**
     * Method to find all matching uses-feature elements in one go. Rather than iterating over the
     * entire list of child nodes only to return the one that match a particular featureName, we use
     * this method to iterate and return all the uses-feature elements of interest in a single
     * iteration of the manifest element's children.
     *
     * @param featureNames The set of all features to look for inside the <code>&lt;manifest&gt;
     *                     </code> node of the document.
     * @param document The document/root node to use for iterating.
     * @return A list of all <code>&lt;uses-feature&gt;</code> elements that match the featureNames.
     */
    static List<Element> findUsesFeatureElements(
            @NonNull Set<String> featureNames, @NonNull Document document) {
        Node manifestElement = document.getDocumentElement();
        if (manifestElement == null) {
            return Collections.emptyList();
        }
        List<Element> nodes = new ArrayList<>(featureNames.size());
        for (Element child : XmlUtils.getSubTags(manifestElement)) {
            if (TAG_USES_FEATURE.equals(child.getTagName())
                    && featureNames.contains(child.getAttributeNS(ANDROID_URI, ATTR_NAME))) {
                nodes.add(child);
            }
        }
        return nodes;
    }

    /**
     * @param document The root of the document.
     * @return The Node pointing to the {@link com.android.xml.AndroidManifest#NODE_APPLICATION} of
     *     the document.
     */
    private static Node getApplicationElement(Document document) {
        Node manifestNode = document.getDocumentElement();
        if (manifestNode != null) {
            return getElementWithTagName(NODE_APPLICATION, manifestNode);
        }
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String elementName = element.getTagName();

        if (NODE_APPLICATION.equals(elementName)) {
            mHasApplicationBanner = element.hasAttributeNS(ANDROID_URI, ATTR_BANNER);
        } else if (NODE_USES_FEATURE.equals(elementName)) {
            // Ensures that unsupported hardware features aren't required.
            Attr name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME);
            if (name != null) {
                String featureName = name.getValue();
                if (isUnsupportedHardwareFeature(featureName)) {
                    mAllUnsupportedTvUsesFeatures.add(featureName);
                    Attr required = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
                    if (required == null || Boolean.parseBoolean(required.getValue())) {
                        mUnsupportedTvUsesFeatures.add(featureName);
                    }
                }
            }

            if (!mHasLeanbackSupport && hasLeanbackSupport(element)) {
                mHasLeanbackSupport = true;
            }
        } else if ((NODE_ACTIVITY.equals(elementName) || NODE_ACTIVITY_ALIAS.equals(elementName))
                && hasLeanbackIntentFilter(element)) {
            mHasLeanbackLauncherActivity = true;
            // Since this activity has a leanback launcher intent filter,
            // Make sure it has a home screen banner
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_BANNER)) {
                mLeanbackActivitiesWithoutBanners++;
            }
        } else if (NODE_USES_PERMISSION.equals(elementName)) {

            // Store all <uses-permission> tags that imply unsupported hardware)
            String permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (getImpliedUnsupportedHardware(permissionName) != null) {
                mUnsupportedHardwareImpliedPermissions.add(permissionName);
            }
        }
    }

    private static boolean hasLeanbackSupport(Element element) {
        assert NODE_USES_FEATURE.equals(element.getTagName()) : element.getTagName();
        return SOFTWARE_FEATURE_LEANBACK.equals(element.getAttributeNS(ANDROID_URI, ATTR_NAME));
    }

    private static boolean isUnsupportedHardwareFeature(@NonNull String featureName) {
        for (String prefix : UNSUPPORTED_HARDWARE_FEATURES) {
            if (featureName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLeanbackIntentFilter(@NonNull Node activityNode) {
        // Visit every intent filter
        for (Element activityChild : XmlUtils.getSubTags(activityNode)) {
            if (NODE_INTENT.equals(activityChild.getNodeName())) {
                for (Element intentFilterChild : XmlUtils.getSubTags(activityChild)) {
                    // Check to see if the category is the leanback launcher
                    String attrName = intentFilterChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (NODE_CATEGORY.equals(intentFilterChild.getNodeName())
                            && CATEGORY_LEANBACK_LAUNCHER.equals(attrName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Assumes that the node is a direct child of the given Node. */
    private static Node getElementWithTagName(@NonNull String tagName, @NonNull Node node) {
        for (Element child : XmlUtils.getSubTags(node)) {
            if (tagName.equals(child.getTagName())) {
                return child;
            }
        }
        return null;
    }
}
