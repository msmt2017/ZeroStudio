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

import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.tools.lint.checks.AppCompatCustomViewDetectorKt.dependsOnAppCompat;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import com.intellij.psi.PsiMethod;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastUtils;

import java.util.Arrays;
import java.util.List;

public class AppCompatCallDetector extends Detector implements SourceCodeScanner {
    public static final Issue ISSUE =
            Issue.create(
                            "AppCompatMethod",
                            "Using Wrong AppCompat Method",
                            "When using the appcompat library, there are some methods you should be"
                                + " calling instead of the normal ones; for example,"
                                + " `getSupportActionBar()` instead of `getActionBar()`. This lint"
                                + " check looks for calls to the wrong method.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            new Implementation(AppCompatCallDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://developer.android.com/topic/libraries/support-library/")
                    .setAndroidSpecific(true)
                    .setEnabledByDefault(false);

    private static final String GET_ACTION_BAR = "getActionBar";
    private static final String START_ACTION_MODE = "startActionMode";
    private static final String SET_PROGRESS_BAR_VIS = "setProgressBarVisibility";
    private static final String SET_PROGRESS_BAR_IN_VIS = "setProgressBarIndeterminateVisibility";
    private static final String SET_PROGRESS_BAR_INDETERMINATE = "setProgressBarIndeterminate";
    private static final String REQUEST_WINDOW_FEATURE = "requestWindowFeature";

    public AppCompatCallDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                GET_ACTION_BAR,
                START_ACTION_MODE,
                SET_PROGRESS_BAR_VIS,
                SET_PROGRESS_BAR_IN_VIS,
                SET_PROGRESS_BAR_INDETERMINATE,
                REQUEST_WINDOW_FEATURE);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        if (dependsOnAppCompat(context.getProject(), false)
                && isAppCompatActivityCall(context, node, method)) {
            String name = method.getName();
            String replace = null;
            if (GET_ACTION_BAR.equals(name)) {
                replace = "getSupportActionBar";
            } else if (START_ACTION_MODE.equals(name)) {
                replace = "startSupportActionMode";
            } else if (SET_PROGRESS_BAR_VIS.equals(name)) {
                replace = "setSupportProgressBarVisibility";
            } else if (SET_PROGRESS_BAR_IN_VIS.equals(name)) {
                replace = "setSupportProgressBarIndeterminateVisibility";
            } else if (SET_PROGRESS_BAR_INDETERMINATE.equals(name)) {
                replace = "setSupportProgressBarIndeterminate";
            } else if (REQUEST_WINDOW_FEATURE.equals(name)) {
                replace = "supportRequestWindowFeature";
            }

            if (replace != null) {
                String message =
                        String.format("Should use `%1$s` instead of `%2$s` name", replace, name);
                LintFix fix =
                        fix().name("Replace with " + replace + "()")
                                .replace()
                                .text(name)
                                .with(replace)
                                .build();
                context.report(ISSUE, node, context.getLocation(node), message, fix);
            }
        }
    }

    private static boolean isAppCompatActivityCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isMemberInSubClassOf(method, CLASS_ACTIVITY, false)) {
            // Make sure that the calling context is a subclass of ActionBarActivity;
            // we don't want to flag these calls if they are in non-appcompat activities
            // such as PreferenceActivity (see b.android.com/58512)
            UClass cls = UastUtils.getParentOfType(node, UClass.class, true);
            return cls != null
                    && (evaluator.extendsClass(
                                    cls.getJavaPsi(),
                                    "androidx.appcompat.app.AppCompatActivity",
                                    false)
                            || evaluator.extendsClass(
                                    cls.getJavaPsi(),
                                    "android.support.v7.app.AppCompatActivity",
                                    false));
        }
        return false;
    }
}
