/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.AndroidXConstants.FLOATING_ACTION_BUTTON;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_HINT;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_ACCESSIBILITY;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.VALUE_NO;
import static com.android.tools.lint.detector.api.LintFix.TODO;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/**
 * Check which looks for accessibility problems like missing content descriptions
 *
 * <p>TODO: Resolve styles and don't warn where styles are defining the content description (though
 * this seems unusual; content descriptions are not typically generic enough to put in styles)
 */
public class AccessibilityDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "ContentDescription",
                            "Image without `contentDescription`",
                            "Non-textual widgets like ImageViews and ImageButtons should use the "
                                    + "`contentDescription` attribute to specify a textual description of "
                                    + "the widget such that screen readers and other accessibility tools "
                                    + "can adequately describe the user interface.\n"
                                    + "\n"
                                    + "Note that elements in application screens that are purely decorative "
                                    + "and do not provide any content or enable a user action should not "
                                    + "have accessibility content descriptions. In this case, set their "
                                    + "descriptions to `@null`. If your app's minSdkVersion is 16 or higher, "
                                    + "you can instead set these graphical elements' "
                                    + "`android:importantForAccessibility` attributes to `no`.\n"
                                    + "\n"
                                    + "Note that for text fields, you should not set both the `hint` and the "
                                    + "`contentDescription` attributes since the hint will never be shown. Just "
                                    + "set the `hint`.",
                            Category.A11Y,
                            3,
                            Severity.WARNING,
                            new Implementation(
                                    AccessibilityDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/ui/accessibility/apps#special-cases");

    /** Constructs a new {@link AccessibilityDetector} */
    public AccessibilityDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                IMAGE_BUTTON,
                IMAGE_VIEW,
                FLOATING_ACTION_BUTTON.oldName(),
                FLOATING_ACTION_BUTTON.newName());
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_CONTENT_DESCRIPTION);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();
        if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
            Incident incident =
                    new Incident(
                            ISSUE,
                            "Do not set both `contentDescription` and `hint`: the `contentDescription` "
                                    + "will mask the `hint`",
                            context.getLocation(attribute));
            context.report(incident);
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)) {
            // Ignore views that are explicitly not important for accessibility
            String importantForAccessibility =
                    element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY);
            if (VALUE_NO.equals(importantForAccessibility)) {
                return;
            }
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "Missing `contentDescription` attribute on image",
                    createFix(!importantForAccessibility.isEmpty()));
        } else {
            Attr attributeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION);
            String attribute = attributeNode.getValue();
            if (attribute.isEmpty() || attribute.equals(TODO)) {
                context.report(
                        ISSUE,
                        attributeNode,
                        context.getLocation(attributeNode),
                        "Empty `contentDescription` attribute on image",
                        createFix(true));
            }
        }
    }

    private LintFix createFix(boolean setContentFixOnly) {
        LintFix setFix = fix().set().todo(ANDROID_URI, ATTR_CONTENT_DESCRIPTION).build();
        if (setContentFixOnly) {
            return setFix;
        } else {
            LintFix notImportant =
                    fix().set(ANDROID_URI, ATTR_IMPORTANT_FOR_ACCESSIBILITY, "no").build();
            return fix().alternatives(setFix, notImportant);
        }
    }
}
