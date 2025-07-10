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
import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.detector.api.Constraints.notLibraryProject;
import static com.android.tools.lint.detector.api.Constraints.targetSdkAtLeast;
import static com.android.xml.AndroidManifest.ATTRIBUTE_REQUIRED;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_METADATA;
import static com.android.xml.AndroidManifest.NODE_USES_FEATURE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Constraint;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.XmlUtils;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class WearStandaloneAppDetector extends Detector implements XmlScanner {

    public static final Implementation IMPLEMENTATION =
            new Implementation(WearStandaloneAppDetector.class, Scope.MANIFEST_SCOPE);

    /** Invalid meta-data or missing wear standalone app flag */
    public static final Issue INVALID_WEAR_FEATURE_ATTRIBUTE =
            Issue.create(
                            "InvalidWearFeatureAttribute",
                            "Invalid attribute for Wear uses-feature",
                            "For the `android.hardware.type.watch` uses-feature, android:required=\"false\" "
                                    + "is disallowed. A single APK for Wear and non-Wear devices is not supported.\n",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/wearables/apps/packaging.html");

    /** Invalid meta-data or missing wear standalone app flag */
    public static final Issue WEAR_STANDALONE_APP_ISSUE =
            Issue.create(
                            "WearStandaloneAppFlag",
                            "Invalid or missing Wear standalone app flag",
                            "Wearable apps should specify whether they can work standalone, without a phone app. "
                                    + "Add a valid meta-data entry for `com.google.android.wearable.standalone` to "
                                    + "your application element and set the value to `true` or `false`.\n"
                                    + "```xml\n"
                                    + "<meta-data android:name=\"com.google.android.wearable.standalone\"\n"
                                    + "           android:value=\"true\"/>`\n"
                                    + "```\n",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/training/wearables/apps/packaging.html");

    public static final String WEARABLE_STANDALONE_ATTR = "com.google.android.wearable.standalone";

    // Quickfix extras to identify the kind of error within the WEAR_STANDALONE_APP_ISSUE issue
    // from the Studio side.
    public static final String KEY_QFX_EXTRA_MISSING_META_DATA = "metadata";

    /** Constructs a new {@link WearStandaloneAppDetector} check */
    public WearStandaloneAppDetector() {}

    /** Whether we saw &lt;uses-feature android:name="android.hardware.type.watch" /&gt; */
    private boolean sawWearUsesFeature;

    /** Whether we saw the standalone meta-data element for wear */
    private boolean sawStandaloneMetadata;

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        sawWearUsesFeature = false;
        sawStandaloneMetadata = false;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(NODE_USES_FEATURE, NODE_METADATA);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        String attrName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);

        if (NODE_USES_FEATURE.equals(tagName)) {
            if ("android.hardware.type.watch".equals(attrName)) {
                sawWearUsesFeature = true;
                Attr requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_REQUIRED);
                if (requiredAttr != null && !Boolean.parseBoolean(requiredAttr.getValue())) {
                    // required=false is not supported for the android.hardware.type.watch feature
                    context.report(
                            INVALID_WEAR_FEATURE_ATTRIBUTE,
                            requiredAttr,
                            context.getLocation(requiredAttr),
                            "`android:required=\"false\"` is not supported for this feature");
                }
            }
        } else if (NODE_METADATA.equals(tagName)
                && WEARABLE_STANDALONE_ATTR.equals(attrName)
                && element.getParentNode() != null
                && element.getParentNode().getNodeName().equals(NODE_APPLICATION)) {
            sawStandaloneMetadata = true;
            // validate android:value
            Attr valueAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_VALUE);

            if (valueAttr == null) {
                LintFix fix = fix().set(ANDROID_URI, ATTR_VALUE, VALUE_TRUE).build();
                context.report(
                        WEAR_STANDALONE_APP_ISSUE,
                        element,
                        context.getNameLocation(element),
                        "Missing `android:value` attribute",
                        fix);
            } else {
                String value = valueAttr.getValue();
                if (value == null
                        || (!value.equalsIgnoreCase(VALUE_TRUE)
                                && !value.equalsIgnoreCase(VALUE_FALSE))) {
                    LintFix fixes =
                            fix().alternatives(
                                            fix().replace().with(VALUE_TRUE).build(),
                                            fix().replace().with(VALUE_FALSE).build());
                    context.report(
                            WEAR_STANDALONE_APP_ISSUE,
                            valueAttr,
                            context.getValueLocation(valueAttr),
                            "Expecting a boolean value for attribute `android:value`",
                            fixes);
                }
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (sawWearUsesFeature
                && !sawStandaloneMetadata
                && context.getProject().getTargetSdk() >= 23) {
            XmlContext xmlContext = (XmlContext) context;
            Element root = xmlContext.document.getDocumentElement();
            Element application = XmlUtils.getFirstSubTagByName(root, NODE_APPLICATION);
            if (application != null) {
                Incident incident =
                        new Incident(
                                WEAR_STANDALONE_APP_ISSUE,
                                application,
                                xmlContext.getNameLocation(application),
                                "Missing `<meta-data android:name="
                                        + "\"com.google.android.wearable.standalone\" ../>` element",
                                fix().data(KEY_QFX_EXTRA_MISSING_META_DATA, true));
                Constraint constraint = targetSdkAtLeast(23).and(notLibraryProject());
                xmlContext.report(incident, constraint);
            }
        }
    }
}
