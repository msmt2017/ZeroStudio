/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;

/** Looks for invocations of android.webkit.WebSettings.setJavaScriptEnabled. */
public class SetJavaScriptEnabledDetector extends Detector implements SourceCodeScanner {
    /** Invocations of setJavaScriptEnabled */
    public static final Issue ISSUE =
            Issue.create(
                            "SetJavaScriptEnabled",
                            "Using `setJavaScriptEnabled`",
                            "Your code should not invoke `setJavaScriptEnabled` if you are not sure that "
                                    + "your app really requires JavaScript support.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    SetJavaScriptEnabledDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://goo.gle/SetJavaScriptEnabled")
                    .addMoreInfo("https://developer.android.com/training/articles/security-tips")
                    .setAndroidSpecific(true);

    /** Constructs a new {@link SetJavaScriptEnabledDetector} check */
    public SetJavaScriptEnabledDetector() {}

    // ---- implements SourceCodeScanner ----

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        List<UExpression> arguments = call.getValueArguments();
        if (arguments.size() == 1) {
            Object constant = ConstantEvaluator.evaluate(context, arguments.get(0));
            if (constant != null && !Boolean.FALSE.equals(constant)) {
                context.report(
                        ISSUE,
                        call,
                        context.getLocation(call),
                        "Using `setJavaScriptEnabled` can introduce XSS vulnerabilities "
                                + "into your application, review carefully");
            }
        }
    }

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setJavaScriptEnabled");
    }
}
