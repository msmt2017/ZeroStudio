/*
 * Copyright (C) 2014 The Android Open Source Project
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
import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.ATTR_SHOW_AS_ACTION;
import static com.android.SdkConstants.AUTO_URI;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Attr;

/**
 * Check that the right namespace is used for app compat menu items
 *
 * <p>Using app:showAsAction instead of android:showAsAction leads to problems, but isn't caught by
 * the API Detector since it's not in the Android namespace.
 */
public class AppCompatResourceDetector extends ResourceXmlDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "AppCompatResource",
                    "Menu namespace",
                    "When using the appcompat library, menu resources should refer to the "
                            + "`showAsAction` (or `actionViewClass`, or `actionProviderClass`) in the "
                            + "`app:` namespace, not the `android:` namespace.\n"
                            + "\n"
                            + "Similarly, when **not** using the appcompat library, you should be using "
                            + "the `android:showAsAction` (or `actionViewClass`, or `actionProviderClass`) "
                            + "attribute.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(AppCompatResourceDetector.class, Scope.RESOURCE_FILE_SCOPE));

    public static final String ATTR_ACTION_VIEW_CLASS = "actionViewClass";
    public static final String ATTR_ACTION_PROVIDER_CLASS = "actionProviderClass";

    /** Constructs a new {@link AppCompatResourceDetector} */
    public AppCompatResourceDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(
                ATTR_SHOW_AS_ACTION, ATTR_ACTION_PROVIDER_CLASS, ATTR_ACTION_VIEW_CLASS);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Project project = context.getProject();
        Boolean appCompat = project.dependsOn(APPCOMPAT_LIB_ARTIFACT);
        String localName = attribute.getLocalName();
        if (ANDROID_URI.equals(attribute.getNamespaceURI())) {
            if (context.getFolderVersion() >= 14) {
                return;
            }
            if (appCompat == Boolean.TRUE) {
                LintFix fix =
                        fix().replaceAttribute(
                                        attribute,
                                        AUTO_URI,
                                        attribute.getLocalName(),
                                        attribute.getValue())
                                .build();
                String message =
                        String.format(
                                "Should use `app:%1$s` with the appcompat library with "
                                        + "`xmlns:app=\"http://schemas.android.com/apk/res-auto\"`",
                                localName);
                context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
            }
        } else {
            if (appCompat == Boolean.FALSE) {

                LintFix fix =
                        fix().replaceAttribute(
                                        attribute,
                                        ANDROID_URI,
                                        attribute.getLocalName(),
                                        attribute.getValue())
                                .build();

                String message =
                        String.format(
                                "Should use `android:%1$s` when not using the appcompat library",
                                localName);

                context.report(ISSUE, attribute, context.getLocation(attribute), message, fix);
            }
        }
    }
}
