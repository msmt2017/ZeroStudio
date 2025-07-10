/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LocationType;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import org.w3c.dom.Element;

/**
 * Check which looks for missing wrong case usage for certain layout tags.
 *
 * <p>TODO: Generalize this to handling spelling errors in general.
 */
public class WrongCaseDetector extends LayoutDetector {
    /** Using the wrong case for layout tags */
    public static final Issue WRONG_CASE =
            Issue.create(
                            "WrongCase",
                            "Wrong case for view tag",
                            "Most layout tags, such as `<Button>`, refer to actual view classes and are therefore "
                                    + "capitalized. However, there are exceptions such as `<fragment>` and `<include>`. This "
                                    + "lint check looks for incorrect capitalizations.",
                            Category.CORRECTNESS,
                            4,
                            Severity.FATAL,
                            new Implementation(WrongCaseDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    .addMoreInfo("https://developer.android.com/guide/components/fragments.html");

    /** Constructs a new {@link WrongCaseDetector} */
    public WrongCaseDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("Fragment", "RequestFocus", "Include", "Merge");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        String correct = Character.toLowerCase(tag.charAt(0)) + tag.substring(1);
        context.report(
                WRONG_CASE,
                element,
                context.getNameLocation(element),
                String.format("Invalid tag `<%1$s>`; should be `<%2$s>`", tag, correct),
                LintFix.create()
                        .renameTag(tag, correct, context.getLocation(element, LocationType.ALL))
                        .build());
    }
}
