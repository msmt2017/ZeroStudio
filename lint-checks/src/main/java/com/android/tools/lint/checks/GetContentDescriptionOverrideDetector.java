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

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

/**
 * Check that looks for override of getContentDescription() in any class that descends from View.
 */
public class GetContentDescriptionOverrideDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                            "GetContentDescriptionOverride",
                            "Overriding `getContentDescription()` on a View",
                            "Overriding `getContentDescription()` may prevent some accessibility"
                                + " services from properly navigating content exposed by your view."
                                + " Instead, call `setContentDescription()` when the content"
                                + " description needs to be changed.",
                            Category.A11Y,
                            9,
                            Severity.ERROR,
                            new Implementation(
                                    GetContentDescriptionOverrideDetector.class,
                                    Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    /** Constructs a new GetContentDescriptionOverrideDetector check. */
    public GetContentDescriptionOverrideDetector() {}

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(SdkConstants.CLASS_VIEW);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        for (PsiMethod method :
                declaration.getJavaPsi().findMethodsByName("getContentDescription", false)) {
            if (evaluator.getParameterCount(method) == 0) {
                context.report(
                        ISSUE,
                        method,
                        context.getNameLocation(method),
                        "Overriding `getContentDescription()` on a View is not recommended");
            }
        }
    }
}
