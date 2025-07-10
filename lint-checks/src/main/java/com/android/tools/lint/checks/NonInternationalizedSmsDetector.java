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
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;

/** Detector looking for text messages sent to an unlocalized phone number. */
public class NonInternationalizedSmsDetector extends Detector implements SourceCodeScanner {
    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "UnlocalizedSms",
                            "SMS phone number missing country code",
                            "SMS destination numbers must start with a country code or the application code "
                                    + "must ensure that the SMS is only sent when the user is in the same country as "
                                    + "the receiver.",
                            Category.CORRECTNESS,
                            5,
                            Severity.WARNING,
                            new Implementation(
                                    NonInternationalizedSmsDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    /** Constructs a new {@link NonInternationalizedSmsDetector} check */
    public NonInternationalizedSmsDetector() {}

    // ---- implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        List<String> methodNames = new ArrayList<>(2);
        methodNames.add("sendTextMessage");
        methodNames.add("sendMultipartTextMessage");
        return methodNames;
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        if (call.getReceiver() == null) {
            // "sendTextMessage"/"sendMultipartTextMessage" in the code with no operand
            return;
        }

        List<UExpression> args = call.getValueArguments();
        if (args.size() != 5) {
            return;
        }
        UExpression destinationAddress = args.get(0);
        if (!(destinationAddress instanceof ULiteralExpression)) {
            return;
        }
        Object literal = ((ULiteralExpression) destinationAddress).getValue();
        if (!(literal instanceof String)) {
            return;
        }
        String number = (String) literal;
        if (number.startsWith("+")) {
            return;
        }
        context.report(
                ISSUE,
                call,
                context.getLocation(destinationAddress),
                "To make sure the SMS can be sent by all users, please start the SMS number "
                        + "with a + and a country code or restrict the code invocation to people in the "
                        + "country you are targeting");
    }
}
