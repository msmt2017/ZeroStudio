/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_CLICKABLE;
import static com.android.SdkConstants.ATTR_FOCUSABLE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.detector.api.Constraints.minSdkLessThan;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

/**
 * Check which looks at widgets that are declared as clickable and ensures that they are also
 * declared as focusable so that they can be reached via keyboard navigation.
 */
public class KeyboardNavigationDetector extends LayoutDetector {

    public static final String MESSAGE = "'clickable' attribute found, please also add 'focusable'";

    public static final Issue ISSUE =
            Issue.create(
                    "KeyboardInaccessibleWidget",
                    "Keyboard inaccessible widget",
                    "A widget that is declared to be clickable but not declared to be focusable is not "
                            + "accessible via the keyboard. Please add the `focusable` attribute as well.",
                    Category.A11Y,
                    3 /* priority */,
                    Severity.WARNING,
                    new Implementation(
                            KeyboardNavigationDetector.class, Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_CLICKABLE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (SdkConstants.VALUE_TRUE.equals(
                attribute.getOwnerElement().getAttributeNS(ANDROID_URI, ATTR_FOCUSABLE))) {
            return;
        }
        if (SdkConstants.VALUE_TRUE.equals(attribute.getValue())
                // Starting with O, clickable already implies focusable.
                && context.getProject().getMinSdkVersion().getApiLevel() < 26) {
            LintFix fix = fix().set(ANDROID_URI, ATTR_FOCUSABLE, VALUE_TRUE).build();
            Location location = context.getLocation(attribute);
            Incident incident = new Incident(ISSUE, attribute, location, MESSAGE, fix);
            context.report(incident, minSdkLessThan(26));
        }
    }
}
