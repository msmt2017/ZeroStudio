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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;

public class PowerManagerDetector extends Detector implements Detector.UastScanner {
    private static final String POWER_MANAGER_CLASS_NAME = "android.os.PowerManager";

    private static final Implementation IMPLEMENTATION =
            new Implementation(PowerManagerDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_WAKE_LOCK_TAG =
            Issue.create(
                            "InvalidWakeLockTag",
                            "Invalid Wake Lock Tag",
                            "Wake Lock tags must follow the naming conventions defined in the"
                                    + "`PowerManager` documentation.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true)
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/os/PowerManager.html");

    /** Constructs a new {@link PowerManagerDetector} */
    public PowerManagerDetector() {}

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, POWER_MANAGER_CLASS_NAME)) {
            return;
        }

        List<UExpression> expressions = call.getValueArguments();
        if (expressions.size() < 2) {
            return;
        }

        UElement secondArgumentExpression = expressions.get(1);
        String value = ConstantEvaluator.evaluateString(context, secondArgumentExpression, false);
        if (value == null) {
            return;
        }

        String error = getErrorForTagName(value);

        if (error != null) {
            context.report(INVALID_WAKE_LOCK_TAG, call, context.getLocation(call), error);
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("newWakeLock");
    }

    @Nullable
    private static String getErrorForTagName(String tagName) {
        if (tagName.isEmpty()) {
            return "Tag name should not be empty to make wake lock problems easier to debug";
        }

        if (isReservedTagName(tagName)) {
            return String.format(
                    "`%1$s` is a reserved platform tag name and cannot be used", tagName);
        }

        if (tagName.indexOf(':') == -1) {
            String message =
                    "Tag name should use a unique prefix followed by a colon "
                            + "(found %1$s). For instance `myapp:mywakelocktag`. This will help with debugging";
            return String.format(message, tagName);
        }

        return null;
    }

    /** Tags with wrapped with stars are used by the platform, e.g. *alarm* or *job*\/myjob. */
    private static boolean isReservedTagName(@NonNull String tagName) {
        if (tagName.length() < 2) {
            return false;
        }
        return tagName.charAt(0) == '*' && tagName.indexOf('*', 1) != -1;
    }
}
