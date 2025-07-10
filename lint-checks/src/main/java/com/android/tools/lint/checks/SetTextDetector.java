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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UastBinaryOperator;

/** Checks for errors related to TextView#setText and internationalization */
public class SetTextDetector extends Detector implements SourceCodeScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(SetTextDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Constructing SimpleDateFormat without an explicit locale */
    public static final Issue SET_TEXT_I18N =
            Issue.create(
                            "SetTextI18n",
                            "TextView Internationalization",
                            "When calling `TextView#setText`\n"
                                    + "* Never call `Number#toString()` to format numbers; it will not handle fraction "
                                    + "separators and locale-specific digits properly. Consider using `String#format` "
                                    + "with proper format specifications (`%d` or `%f`) instead.\n"
                                    + "* Do not pass a string literal (e.g. \"Hello\") to display text. Hardcoded "
                                    + "text can not be properly translated to other languages. Consider using Android "
                                    + "resource strings instead.\n"
                                    + "* Do not build messages by concatenating text chunks. Such messages can not be "
                                    + "properly translated.",
                            Category.I18N,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/resources/localization.html")
                    .setAndroidSpecific(true);

    private static final String METHOD_NAME = "setText";
    private static final String TO_STRING_NAME = "toString";
    private static final String CHAR_SEQUENCE_CLS = "java.lang.CharSequence";
    private static final String NUMBER_CLS = "java.lang.Number";
    private static final String TEXT_VIEW_CLS = "android.widget.TextView";

    // Pattern to match string literal that require translation. These are those having word
    // characters in it.
    private static final String WORD_PATTERN = ".*\\w{2,}.*";

    /** Constructs a new {@link SetTextDetector} */
    public SetTextDetector() {}

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList(METHOD_NAME);
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInSubClassOf(method, TEXT_VIEW_CLS, false)) {
            return;
        }
        if (method.getParameterList().getParametersCount() > 0
                && evaluator.parameterHasType(method, 0, CHAR_SEQUENCE_CLS)) {
            checkNode(context, call.getValueArguments().get(0));
        }
    }

    private static void checkNode(@NonNull JavaContext context, @Nullable UElement node) {
        if (node instanceof ULiteralExpression) {
            Object value = ((ULiteralExpression) node).getValue();
            if (value instanceof String && value.toString().matches(WORD_PATTERN)) {
                context.report(
                        SET_TEXT_I18N,
                        node,
                        context.getLocation(node),
                        "String literal in `setText` can not be translated. Use Android "
                                + "resources instead.");
            }
        } else if (node instanceof UCallExpression) {
            PsiMethod calledMethod = ((UCallExpression) node).resolve();
            if (calledMethod != null && TO_STRING_NAME.equals(calledMethod.getName())) {
                PsiClass containingClass = calledMethod.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                PsiClass superClass = containingClass.getSuperClass();
                if (superClass != null && NUMBER_CLS.equals(superClass.getQualifiedName())) {
                    context.report(
                            SET_TEXT_I18N,
                            node,
                            context.getLocation(node),
                            "Number formatting does not take into account locale settings. "
                                    + "Consider using `String.format` instead.");
                }
            }
        } else if (node instanceof UQualifiedReferenceExpression) {
            UQualifiedReferenceExpression expression = (UQualifiedReferenceExpression) node;
            checkNode(context, expression.getReceiver());
            checkNode(context, expression.getSelector());
        } else if (node instanceof UPolyadicExpression) {
            UPolyadicExpression expression = (UPolyadicExpression) node;
            if (expression.getOperator() == UastBinaryOperator.PLUS
                    && expression.getOperands().size() > 1) {
                // (UAST will model KtStringTemplateExpression as a polyadic expression with one
                // operand even when there are no substitutions...)
                context.report(
                        SET_TEXT_I18N,
                        node,
                        context.getLocation(node),
                        "Do not concatenate text displayed with `setText`. "
                                + "Use resource string with placeholders.");
            }
            for (UExpression operand : expression.getOperands()) {
                checkNode(context, operand);
            }
        } else if (node instanceof UParenthesizedExpression) {
            checkNode(context, ((UParenthesizedExpression) node).getExpression());
        }
    }
}
