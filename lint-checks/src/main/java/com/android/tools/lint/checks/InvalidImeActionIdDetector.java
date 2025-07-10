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
import static com.android.SdkConstants.ATTR_IME_ACTION_ID;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;

/**
 * Check android:imeActionId for valid values, as defined by <a
 * href="https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html">EditorInfo</a>
 */
public class InvalidImeActionIdDetector extends LayoutDetector {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "InvalidImeActionId",
                            "Invalid imeActionId declaration",
                            "`android:imeActionId` should not be a resource ID such as "
                                    + "`@+id/resName`. It must be an integer constant, or an integer resource "
                                    + "reference, as defined in `EditorInfo`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    InvalidImeActionIdDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/view/inputmethod/EditorInfo.html");

    /** Constructs a new {@link InvalidImeActionIdDetector} check */
    public InvalidImeActionIdDetector() {}

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_IME_ACTION_ID);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        String value = attribute.getValue();
        ResourceUrl url = ResourceUrl.parse(value);
        if (url == null) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Integer.parseInt(value);
            } catch (Throwable ex) {
                context.report(
                        ISSUE,
                        attribute,
                        context.getLocation(attribute),
                        String.format("\"%1$s\" is not an integer", value));
            }
        } else if (url.type != ResourceType.INTEGER) {
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Invalid resource type, expected integer value");
        }
    }
}
