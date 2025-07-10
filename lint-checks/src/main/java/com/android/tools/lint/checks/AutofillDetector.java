/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_AUTOFILL_HINTS;
import static com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.VALUE_NO;
import static com.android.SdkConstants.VALUE_NO_EXCLUDE_DESCENDANTS;
import static com.android.SdkConstants.VALUE_YES;
import static com.android.SdkConstants.VALUE_YES_EXCLUDE_DESCENDANTS;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AutofillDetector extends LayoutDetector {
    private static final String MESSAGE = "Missing `autofillHints` attribute";

    public static final Issue ISSUE =
            Issue.create(
                            "Autofill",
                            "Use Autofill",
                            "Specify an `autofillHints` attribute when targeting SDK version 26 or "
                                    + "higher or explicitly specify that the view is not important for autofill. "
                                    + "Your app can help an autofill service classify the data correctly by "
                                    + "providing the meaning of each view that could be autofillable, such as "
                                    + "views representing usernames, passwords, credit card fields, email "
                                    + "addresses, etc.\n"
                                    + "\n"
                                    + "The hints can have any value, but it is recommended to use predefined "
                                    + "values like 'username' for a username or 'creditCardNumber' for a credit "
                                    + "card number. For a list of all predefined autofill hint constants, see the "
                                    + "`AUTOFILL_HINT_` constants in the `View` reference at "
                                    + "https://developer.android.com/reference/android/view/View.html.\n"
                                    + "\n"
                                    + "You can mark a view unimportant for autofill by specifying an "
                                    + "`importantForAutofill` attribute on that view or a parent view. See "
                                    + "https://developer.android.com/reference/android/view/View.html#setImportantForAutofill(int).",
                            Category.USABILITY,
                            3,
                            Severity.WARNING,
                            new Implementation(AutofillDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo("https://developer.android.com/guide/topics/text/autofill.html");

    public AutofillDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(EDIT_TEXT);
    }

    @Override
    public void visitElement(@NonNull XmlContext xmlContext, @NonNull Element element) {
        if (xmlContext.getProject().getTargetSdk() < 26) {
            return;
        }

        String attrValue = element.getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);

        if (!attrValue.isEmpty()) {
            if (attrValue.equals(VALUE_NO) || attrValue.equals(VALUE_NO_EXCLUDE_DESCENDANTS)) {
                // The view is explicitly marked not important for autofill.
                return;
            }
            if (attrValue.equals(VALUE_YES) || attrValue.equals(VALUE_YES_EXCLUDE_DESCENDANTS)) {
                // The view is explicitly marked important for autofill. This meanas that anything
                // a parent node declares about descendant autofill behavior is overridden.
                checkForAutofillHints(element, xmlContext);
                return;
            }
        }

        // Check if a parent node has explicitly marked descendants unimportant for autofill.
        boolean checkParent = true;
        Node el = element.getParentNode();
        while (el instanceof Element && checkParent) {
            attrValue = ((Element) el).getAttributeNS(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL);
            if (!attrValue.isEmpty()) {
                // Stop at the first ancestor that defines autofill behavior for descendants.
                checkParent = false;
                if (attrValue.equals(VALUE_NO_EXCLUDE_DESCENDANTS)
                        || attrValue.equals(VALUE_YES_EXCLUDE_DESCENDANTS)) {
                    // The parent has explicitly marked descendants not important for autofill.
                    return;
                }
            }
            el = el.getParentNode();
        }

        // If we get here, it means that neither the view nor a parent has excluded the view from
        // autofill and the view needs to define autofill hints.
        checkForAutofillHints(element, xmlContext);
    }

    private void checkForAutofillHints(Element element, XmlContext xmlContext) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_AUTOFILL_HINTS)) {
            LintFix fix =
                    fix().alternatives(
                                    // TODO: set hints based on id of view. Example, @+id/username
                                    // suggests 'username' as hint value.
                                    fix().set(ANDROID_URI, ATTR_AUTOFILL_HINTS, "")
                                            .caretBegin()
                                            .build(),
                                    fix().set(ANDROID_URI, ATTR_IMPORTANT_FOR_AUTOFILL, "no")
                                            .build());
            xmlContext.report(ISSUE, element, xmlContext.getNameLocation(element), MESSAGE, fix);
        }
    }
}
