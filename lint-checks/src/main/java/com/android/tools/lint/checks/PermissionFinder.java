/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.SdkConstants.CLASS_INTENT;
import static com.android.tools.lint.checks.AnnotationDetectorKt.PERMISSION_ANNOTATION;
import static com.android.tools.lint.checks.AnnotationDetectorKt.PERMISSION_ANNOTATION_READ;
import static com.android.tools.lint.checks.AnnotationDetectorKt.PERMISSION_ANNOTATION_WRITE;
import static com.android.tools.lint.checks.PermissionDetector.AOSP_PERMISSION_ANNOTATION;
import static com.android.tools.lint.client.api.AndroidPlatformAnnotations.toPlatformAnnotation;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.support.AndroidxName;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.UastLintUtils;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiVariable;
import java.util.List;
import org.jetbrains.uast.UAnnotation;
import org.jetbrains.uast.UBinaryExpressionWithType;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.UNamedExpression;
import org.jetbrains.uast.UParenthesizedExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UastFacade;
import org.jetbrains.uast.UastLiteralUtils;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.util.UastExpressionUtils;

/** Utility for locating permissions required by an intent or content resolver */
public class PermissionFinder {

    /**
     * Operation that has a permission requirement -- such as a method call, a content resolver read
     * or write operation, an intent, etc.
     */
    public enum Operation {
        CALL,
        ACTION,
        READ,
        WRITE;

        /** Prefix to use when describing a name with a permission requirement */
        public String prefix() {
            switch (this) {
                case ACTION:
                    return "by intent";
                case READ:
                    return "to read";
                case WRITE:
                    return "to write";
                case CALL:
                default:
                    return "by";
            }
        }
    }

    /** A permission requirement given a name and operation */
    public static class Result {
        @NonNull public final PermissionRequirement requirement;
        @NonNull public final String name;
        @NonNull public final Operation operation;

        public Result(
                @NonNull Operation operation,
                @NonNull PermissionRequirement requirement,
                @NonNull String name) {
            this.operation = operation;
            this.requirement = requirement;
            this.name = name;
        }
    }

    /**
     * Searches for a permission requirement for the given parameter in the given call
     *
     * @param context context which can look up annotations etc
     * @param operation the operation to look up
     * @param parameter the parameter which contains the value which implies the permission
     * @return the result with the permission requirement, or null if nothing is found
     */
    @Nullable
    public static Result findRequiredPermissions(
            @NonNull JavaContext context,
            @NonNull Operation operation,
            @NonNull UElement parameter) {

        // To find the permission required by an intent, we proceed in 3 steps:
        // (1) Locate the parameter in the start call that corresponds to
        //     the Intent
        //
        // (2) Find the place where the intent is initialized, and figure
        //     out the action name being passed to it.
        //
        // (3) Find the place where the action is defined, and look for permission
        //     annotations on that action declaration!

        return new PermissionFinder(context, operation).search(parameter);
    }

    private PermissionFinder(@NonNull JavaContext context, @NonNull Operation operation) {
        mContext = context;
        mOperation = operation;
    }

    @NonNull private final JavaContext mContext;
    @NonNull private final Operation mOperation;

    @Nullable
    public Result search(@NonNull UElement node) {
        if (UastLiteralUtils.isNullLiteral(node)) {
            return null;
        } else if (node instanceof UIfExpression) {
            UIfExpression expression = (UIfExpression) node;
            if (expression.getThenExpression() != null) {
                Result result = search(expression.getThenExpression());
                if (result != null) {
                    return result;
                }
            }
            if (expression.getElseExpression() != null) {
                return search(expression.getElseExpression());
            }
        } else if (UastExpressionUtils.isTypeCast(node)) {
            UBinaryExpressionWithType cast = (UBinaryExpressionWithType) node;
            UExpression operand = cast.getOperand();
            return search(operand);
        } else if (node instanceof UParenthesizedExpression) {
            UParenthesizedExpression parens = (UParenthesizedExpression) node;
            UExpression expression = parens.getExpression();
            return search(expression);
        } else if (UastExpressionUtils.isConstructorCall(node) && mOperation == Operation.ACTION) {
            // Identifies "new Intent(argument)" calls and, if found, continues
            // resolving the argument instead looking for the action definition
            UCallExpression call = (UCallExpression) node;
            UReferenceExpression classReference = call.getClassReference();
            String type =
                    classReference != null ? UastUtils.getQualifiedName(classReference) : null;
            if (CLASS_INTENT.equals(type)) {
                List<UExpression> expressions = call.getValueArguments();
                if (!expressions.isEmpty()) {
                    UExpression action = expressions.get(0);
                    if (action != null) {
                        return search(action);
                    }
                }
            }
            return null;
        } else if (node instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) node).resolve();
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                if (mOperation == Operation.ACTION) {
                    JavaEvaluator evaluator = mContext.getEvaluator();
                    UAnnotation annotation =
                            evaluator.getAnnotation(
                                    field,
                                    PERMISSION_ANNOTATION.oldName(),
                                    PERMISSION_ANNOTATION.newName(),
                                    AOSP_PERMISSION_ANNOTATION);

                    if (annotation != null) {
                        return getPermissionRequirement(field, annotation);
                    }
                } else if (mOperation == Operation.READ || mOperation == Operation.WRITE) {
                    AndroidxName fqn =
                            mOperation == Operation.READ
                                    ? PERMISSION_ANNOTATION_READ
                                    : PERMISSION_ANNOTATION_WRITE;

                    JavaEvaluator evaluator = mContext.getEvaluator();
                    UAnnotation annotation =
                            evaluator.getAnnotation(field, fqn.oldName(), fqn.newName());
                    if (annotation == null) {
                        String s = toPlatformAnnotation(fqn.newName());
                        annotation = evaluator.getAnnotation(field, s);
                    }
                    if (annotation != null) {
                        List<UNamedExpression> values = annotation.getAttributeValues();
                        UExpression o = values.size() == 1 ? values.get(0).getExpression() : null;
                        if (o instanceof UCallExpression
                                && o.getSourcePsi() instanceof PsiAnnotation) {
                            PsiAnnotation nestedPsi = (PsiAnnotation) o.getSourcePsi();
                            UAnnotation nested =
                                    (UAnnotation)
                                            UastFacade.INSTANCE.convertElement(
                                                    nestedPsi, null, UAnnotation.class);
                            if (nested != null) {
                                String qualifiedName = nested.getQualifiedName();
                                if (PERMISSION_ANNOTATION.isEquals(qualifiedName)
                                        || AOSP_PERMISSION_ANNOTATION.equals(qualifiedName)) {
                                    return getPermissionRequirement(field, nested);
                                }
                            }
                        } else {
                            // The complex annotations used for read/write cannot be
                            // expressed in the external annotations format, so they're inlined.
                            // (See Extractor.AnnotationData#write).
                            //
                            // Instead we've inlined the fields of the annotation on the
                            // outer one:
                            return getPermissionRequirement(field, annotation);
                        }
                    }
                } else {
                    assert false : mOperation;
                }
            }

            if (resolved instanceof PsiVariable) {
                PsiVariable variable = (PsiVariable) resolved;
                UExpression lastAssignment = UastLintUtils.findLastAssignment(variable, node);

                if (lastAssignment != null) {
                    return search(lastAssignment);
                }
            }
        }

        return null;
    }

    @NonNull
    private Result getPermissionRequirement(
            @NonNull PsiField field, @NonNull UAnnotation annotation) {
        PermissionRequirement requirement = PermissionRequirement.create(annotation);
        PsiClass containingClass = field.getContainingClass();
        String name =
                containingClass != null
                        ? containingClass.getName() + "." + field.getName()
                        : field.getName();
        return new Result(mOperation, requirement, name);
    }
}
