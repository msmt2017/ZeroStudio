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

import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.tools.lint.checks.CleanupDetector.MOTION_EVENT_CLS;
import static com.android.tools.lint.detector.api.Lint.getMethodName;

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
import com.android.tools.lint.detector.api.TypeEvaluator;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULambdaExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USuperExpression;
import org.jetbrains.uast.UastFacade;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks that views that override View#onTouchEvent also implement View#performClick and call
 * performClick when click detection occurs.
 */
public class ClickableViewAccessibilityDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                            "ClickableViewAccessibility",
                            "Accessibility in Custom Views",
                            "If a `View` that overrides `onTouchEvent` or uses an `OnTouchListener`"
                                + " does not also implement `performClick` and call it when clicks"
                                + " are detected, the `View` may not handle accessibility actions"
                                + " properly. Logic handling the click actions should ideally be"
                                + " placed in `View#performClick` as some accessibility services"
                                + " invoke `performClick` when a click action should occur.",
                            Category.A11Y,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    ClickableViewAccessibilityDetector.class,
                                    Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    private static final String PERFORM_CLICK = "performClick";
    public static final String ON_TOUCH_EVENT = "onTouchEvent";
    private static final String ON_TOUCH = "onTouch";
    private static final String CLASS_ON_TOUCH_LISTENER = "android.view.View.OnTouchListener";

    /** Constructs a new {@link ClickableViewAccessibilityDetector} */
    public ClickableViewAccessibilityDetector() {}

    // ---- implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("setOnTouchListener");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInSubClassOf(method, CLASS_VIEW, false)) {
            return;
        }
        if (!evaluator.parametersMatch(method, CLASS_ON_TOUCH_LISTENER)) {
            return;
        }

        PsiType type = TypeEvaluator.evaluate(node.getReceiver());
        if (!(type instanceof PsiClassType)) {
            return;
        }
        PsiClass viewClass = ((PsiClassType) type).resolve();
        if (viewClass == null) {
            return;
        }

        // As of Android O findViewById is of generic type and the type resolver returns
        // a type parameter; we really want the resolved typed instead
        if (viewClass instanceof PsiTypeParameter) {
            type = node.getReceiverType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            viewClass = ((PsiClassType) type).resolve();
            if (viewClass == null) {
                return;
            }
        }

        // Ignore abstract classes.
        if (evaluator.isAbstract(viewClass)) {
            return;
        }

        PsiMethod performClick = findPerformClickMethod(viewClass);
        //noinspection VariableNotUsedInsideIf
        if (performClick == null) {
            String message =
                    String.format(
                            "Custom view `%1$s` has `setOnTouchListener` called on it but does not "
                                    + "override `performClick`",
                            describeClass(viewClass));
            context.report(ISSUE, node, context.getLocation(node), message);
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        // return Collections.singletonList("android.view.View.OnTouchListener");
        return Arrays.asList(CLASS_VIEW, CLASS_ON_TOUCH_LISTENER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        // Ignore abstract classes.
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isAbstract(declaration)) {
            return;
        }

        if (!evaluator.implementsInterface(declaration.getJavaPsi(), CLASS_VIEW, true)) {
            checkOnTouchListener(context, declaration);
        } else {
            checkCustomView(context, declaration);
        }
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull ULambdaExpression lambda) {
        // This must be an on-touch listener (view is not an interface so we can't supply a
        // lambda)
        checkOnTouchListenerLambda(context, lambda);
    }

    private static void checkCustomView(@NonNull JavaContext context, @NonNull UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass psiClass = declaration.getJavaPsi();

        PsiMethod onTouchEvent = null;
        PsiMethod[] onTouchEvents = psiClass.findMethodsByName(ON_TOUCH_EVENT, false);
        for (PsiMethod method : onTouchEvents) {
            if (evaluator.parametersMatch(method, MOTION_EVENT_CLS)) {
                onTouchEvent = method;
                break;
            }
        }

        PsiMethod performClick = findPerformClickMethod(psiClass);

        // Check if we override onTouchEvent.
        if (onTouchEvent != null) {
            // Ensure that we also override performClick.
            //noinspection VariableNotUsedInsideIf
            if (performClick == null) {
                String message =
                        String.format(
                                "Custom view %1$s overrides `onTouchEvent` but not `performClick`",
                                describeClass(psiClass));
                context.report(ISSUE, onTouchEvent, context.getNameLocation(onTouchEvent), message);
            } else {
                // If we override performClick, ensure that it is called inside onTouchEvent.
                UastFacade uastContext = UastFacade.INSTANCE;
                UElement uastMethod = uastContext.convertElement(onTouchEvent, null, UMethod.class);
                if (uastMethod != null && !performsClick(uastMethod)) {
                    String message =
                            String.format(
                                    "%1$s should call %2$s when a click is detected",
                                    describeMethod(ON_TOUCH_EVENT, psiClass),
                                    describeMethod(PERFORM_CLICK, psiClass));
                    context.report(
                            ISSUE, onTouchEvent, context.getNameLocation(onTouchEvent), message);
                }
            }
        }

        // Ensure that, if performClick is implemented, performClick calls super.performClick.
        if (performClick != null) {
            // If we override performClick, ensure that it is called inside onTouchEvent.
            UastFacade uastContext = UastFacade.INSTANCE;
            UElement uastMethod = uastContext.convertElement(performClick, null, UMethod.class);
            if (uastMethod != null && !performsClickCallsSuper(uastMethod)) {
                String message =
                        String.format(
                                "%1$s should call `super#performClick`",
                                describeMethod(PERFORM_CLICK, declaration.getJavaPsi()));
                context.report(ISSUE, performClick, context.getNameLocation(performClick), message);
            }
        }
    }

    private static void checkOnTouchListener(
            @NonNull JavaContext context, @NonNull UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        PsiClass psiClass = declaration.getJavaPsi();

        // Just an OnTouchListener? onTouch must call performClick
        PsiMethod[] onTouchMethods = psiClass.findMethodsByName(ON_TOUCH, false);
        for (PsiMethod method : onTouchMethods) {
            if (evaluator.parametersMatch(method, CLASS_VIEW, MOTION_EVENT_CLS)) {
                UastFacade uastContext = UastFacade.INSTANCE;
                UElement uastMethod = uastContext.convertElement(method, null, UMethod.class);
                if (uastMethod != null && !performsClick(uastMethod)) {
                    String message =
                            String.format(
                                    "%1$s should call `View#performClick` when a click is detected",
                                    describeMethod(ON_TOUCH, psiClass));
                    context.report(ISSUE, method, context.getNameLocation(method), message);
                }

                break;
            }
        }
    }

    private static void checkOnTouchListenerLambda(
            @NonNull JavaContext context, @NonNull ULambdaExpression lambda) {
        if (!performsClick(lambda.getBody())) {
            String message =
                    String.format(
                            "%1$s lambda should call `View#performClick` when a click is detected",
                            describeMethod(ON_TOUCH, null));
            context.report(ISSUE, lambda, context.getNameLocation(lambda), message);
        }
    }

    @Nullable
    private static PsiMethod findPerformClickMethod(PsiClass clz) {
        PsiMethod performClick = null;
        PsiMethod[] performClicks = clz.findMethodsByName(PERFORM_CLICK, false);
        for (PsiMethod method : performClicks) {
            if (method.getParameterList().getParametersCount() == 0) {
                performClick = method;
                break;
            }
        }
        return performClick;
    }

    @NonNull
    private static String describeClass(@NonNull PsiClass declaration) {
        String name = declaration.getName();
        if (name != null) {
            return '`' + name + '`';
        }

        return "anonymous class";
    }

    private static String describeMethod(@NonNull String methodName, @Nullable PsiClass inClass) {
        if (inClass != null) {
            String name = inClass.getName();
            if (name != null) {
                return '`' + name + '#' + methodName + '`';
            }
        }

        return '`' + methodName + '`';
    }

    private static boolean performsClick(UElement element) {
        PerformsClickVisitor visitor = new PerformsClickVisitor();
        element.accept(visitor);
        return visitor.performsClick();
    }

    private static boolean performsClickCallsSuper(UElement element) {
        PerformsClickCallsSuperVisitor visitor = new PerformsClickCallsSuperVisitor();
        element.accept(visitor);
        return visitor.callsSuper();
    }

    private static class PerformsClickVisitor extends AbstractUastVisitor {

        private boolean performsClick;

        public PerformsClickVisitor() {}

        @Override
        public boolean visitCallExpression(UCallExpression node) {
            // There are also methods like performContextClick and performLongClick
            // which also perform accessibility work, but they seem to have different
            // semantics than the intended onTouch to perform click check
            if (PERFORM_CLICK.equals(getMethodName(node)) && node.getValueArgumentCount() == 0) {
                // TODO: Check receiver?
                performsClick = true;
            }
            return super.visitCallExpression(node);
        }

        public boolean performsClick() {
            return performsClick;
        }
    }

    private static class PerformsClickCallsSuperVisitor extends AbstractUastVisitor {

        private boolean callsSuper;

        public PerformsClickCallsSuperVisitor() {}

        @Override
        public boolean visitSuperExpression(USuperExpression node) {
            UElement parent = UastUtils.skipParenthesizedExprUp(node.getUastParent());
            if (parent instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) parent).resolve();
                if (resolved instanceof PsiMethod
                        && PERFORM_CLICK.equals(((PsiMethod) resolved).getName())) {
                    callsSuper = true;
                    return true;
                }
            }

            return false;
        }

        public boolean callsSuper() {
            return callsSuper;
        }
    }
}
