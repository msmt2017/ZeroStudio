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

import static com.android.tools.lint.detector.api.Lint.getMethodName;
import static org.jetbrains.uast.UastUtils.skipParenthesizedExprDown;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UDeclarationsExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.ULocalVariable;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UReturnExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

public class FirebaseAnalyticsDetector extends Detector implements SourceCodeScanner {

    private static final int EVENT_NAME_MAX_LENGTH = 40;
    private static final int EVENT_PARAM_NAME_MAX_LENGTH = 40;
    private static final Implementation IMPLEMENTATION =
            new Implementation(FirebaseAnalyticsDetector.class, Scope.JAVA_FILE_SCOPE);

    public static final Issue INVALID_NAME =
            Issue.create(
                            "InvalidAnalyticsName",
                            "Invalid Analytics Name",
                            "Event names and parameters must follow the naming conventions defined in the"
                                    + "`FirebaseAnalytics#logEvent()` documentation.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://firebase.google.com/docs/reference/android/com/google/firebase/analytics/FirebaseAnalytics#logEvent(java.lang.String,%20android.os.Bundle)")
                    .setAndroidSpecific(true);

    /** Constructs a new {@link FirebaseAnalyticsDetector} */
    public FirebaseAnalyticsDetector() {}

    // This list is taken from:
    // https://developers.google.com/android/reference/com/google/firebase/analytics/FirebaseAnalytics.Event
    private static boolean isReservedEventName(@NonNull String name) {
        switch (name) {
            case "app_clear_data":
            case "app_uninstall":
            case "app_update":
            case "error":
            case "first_open":
            case "in_app_purchase":
            case "notification_dismiss":
            case "notification_foreground":
            case "notification_open":
            case "notification_receive":
            case "os_update":
            case "session_start":
            case "user_engagement":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        String firebaseAnalytics = "com.google.firebase.analytics.FirebaseAnalytics";
        if (!context.getEvaluator().isMemberInClass(method, firebaseAnalytics)) {
            return;
        }

        List<UExpression> expressions = call.getValueArguments();
        if (expressions.size() < 2) {
            return;
        }

        UElement firstArgumentExpression = expressions.get(0);
        String value = ConstantEvaluator.evaluateString(context, firstArgumentExpression, false);
        if (value == null) {
            return;
        }

        String error = getErrorForEventName(value);

        if (error != null) {
            context.report(INVALID_NAME, call, context.getLocation(call), error);
        }

        UExpression secondParameter = skipParenthesizedExprDown(expressions.get(1));
        if (secondParameter == null) {
            return;
        }
        List<BundleModification> bundleModifications =
                getBundleModifications(context, secondParameter);

        if (bundleModifications != null && !bundleModifications.isEmpty()) {
            validateEventParameters(context, bundleModifications, call);
        }
    }

    private static void validateEventParameters(
            JavaContext context, List<BundleModification> parameters, UCallExpression call) {
        for (BundleModification bundleModification : parameters) {
            String error = getErrorForEventParameterName(bundleModification.mName);
            if (error != null) {
                Location location = context.getLocation(call);
                location.withSecondary(context.getLocation(bundleModification.mLocation), error);
                context.report(
                        INVALID_NAME,
                        call,
                        location,
                        "Bundle with invalid Analytics event parameters passed to `logEvent`");
            }
        }
    }

    @Nullable
    private static List<BundleModification> getBundleModifications(
            JavaContext context, UExpression secondParameter) {
        PsiType type = secondParameter.getExpressionType();
        if (type != null && !type.getCanonicalText().equals(SdkConstants.CLASS_BUNDLE)) {
            return null;
        }

        if (secondParameter instanceof UCallExpression) {
            return Collections.emptyList();
        }

        List<BundleModification> modifications = null;

        if (secondParameter instanceof UReferenceExpression) {
            UReferenceExpression bundleReference = (UReferenceExpression) secondParameter;
            modifications = BundleModificationFinder.find(context, bundleReference);
        }

        return modifications;
    }

    /**
     * Given a reference to an instance of Bundle, find the putString method calls that modify the
     * bundle.
     *
     * <p>This will recursively search across files within the project.
     */
    private static class BundleModificationFinder extends AbstractUastVisitor {

        private final String mBundleReference;
        private final JavaContext mContext;
        private final List<BundleModification> mParameters = new ArrayList<>();

        private BundleModificationFinder(
                JavaContext context, UReferenceExpression bundleReference) {
            mContext = context;
            mBundleReference = bundleReference.asSourceString();
        }

        @Override
        public boolean visitDeclarationsExpression(UDeclarationsExpression statement) {
            for (UElement element : statement.getDeclarations()) {
                if (!(element instanceof ULocalVariable)) {
                    continue;
                }

                ULocalVariable local = (ULocalVariable) element;
                String name = local.getName();

                if (name == null || !name.equals(mBundleReference)) {
                    continue;
                }

                UExpression initializer = local.getUastInitializer();
                if (initializer == null) {
                    continue;
                }
                initializer = skipParenthesizedExprDown(initializer);
                PsiMethod resolvedMethod;
                if (initializer instanceof UCallExpression) {
                    resolvedMethod = ((UCallExpression) initializer).resolve();
                } else if (initializer instanceof UReferenceExpression) {
                    PsiElement resolved = ((UReferenceExpression) initializer).resolve();
                    if (resolved instanceof PsiMethod) {
                        resolvedMethod = (PsiMethod) resolved;
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }
                if (resolvedMethod != null) {
                    UReferenceExpression returnReference =
                            ReturnReferenceExpressionFinder.find(Lint.getUMethod(resolvedMethod));
                    if (returnReference != null) {
                        addParams(find(mContext, returnReference));
                    }
                }
            }

            return super.visitDeclarationsExpression(statement);
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression expression) {
            checkMethodCall(expression);
            return super.visitCallExpression(expression);
        }

        private void checkMethodCall(UCallExpression expression) {
            String method = getMethodName(expression);
            if (method == null
                    || (!method.equals("putString")
                            && !method.equals("putLong")
                            && !method.equals("putDouble"))) {
                return;
            }

            UExpression token = expression.getReceiver();
            if (token != null) {
                token = skipParenthesizedExprDown(token);
            }
            if (token == null || !mBundleReference.equals(token.asSourceString())) {
                return;
            }

            List<UExpression> expressions = expression.getValueArguments();
            String evaluatedName =
                    ConstantEvaluator.evaluateString(mContext, expressions.get(0), false);

            if (evaluatedName != null) {
                addParam(evaluatedName, expressions.get(1).asSourceString(), expression);
            }
        }

        private void addParam(String key, String value, UCallExpression location) {
            mParameters.add(new BundleModification(key, value, location));
        }

        private void addParams(Collection<BundleModification> bundleModifications) {
            mParameters.addAll(bundleModifications);
        }

        @NonNull
        static List<BundleModification> find(
                JavaContext context, UReferenceExpression bundleReference) {
            BundleModificationFinder scanner =
                    new BundleModificationFinder(context, bundleReference);
            UMethod enclosingMethod = UastUtils.getParentOfType(bundleReference, UMethod.class);
            if (enclosingMethod == null) {
                return Collections.emptyList();
            }
            enclosingMethod.accept(scanner);
            return scanner.mParameters;
        }
    }

    /** Given a method, find the last `return` expression that returns a reference. */
    private static class ReturnReferenceExpressionFinder extends AbstractUastVisitor {

        private UReferenceExpression mReturnReference = null;

        @Override
        public boolean visitReturnExpression(UReturnExpression statement) {
            UExpression returnExpression = statement.getReturnExpression();
            if (returnExpression != null) {
                returnExpression = skipParenthesizedExprDown(returnExpression);
            }
            if (returnExpression instanceof UReferenceExpression) {
                mReturnReference = (UReferenceExpression) returnExpression;
            }

            return super.visitReturnExpression(statement);
        }

        @Nullable
        static UReferenceExpression find(@Nullable UMethod method) {
            if (method == null) {
                return null;
            }
            ReturnReferenceExpressionFinder finder = new ReturnReferenceExpressionFinder();
            method.accept(finder);
            return finder.mReturnReference;
        }
    }

    private static class BundleModification {

        public final String mName;

        @SuppressWarnings("unused")
        public final String mValue;

        public final UCallExpression mLocation;

        public BundleModification(String name, String value, UCallExpression location) {
            mName = name;
            mValue = value;
            mLocation = location;
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("logEvent");
    }

    @Nullable
    private static String getErrorForEventName(String eventName) {
        if (eventName.length() > EVENT_NAME_MAX_LENGTH) {
            String message = "Analytics event name must be less than %1$d characters (found %2$d)";
            return String.format(message, EVENT_NAME_MAX_LENGTH, eventName.length());
        }

        if (eventName.isEmpty()) {
            return "Analytics event name cannot be empty";
        }

        if (!Character.isAlphabetic(eventName.charAt(0))) {
            String message =
                    "Analytics event name must start with an alphabetic character (found %1$s)";
            return String.format(message, eventName);
        }

        String message =
                "Analytics event name must only consist of letters, numbers and "
                        + "underscores (found %1$s)";
        for (int i = 0; i < eventName.length(); i++) {
            char character = eventName.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return String.format(message, eventName);
            }
        }

        if (eventName.startsWith("firebase_")) {
            return "Analytics event name should not start with `firebase_`";
        }

        if (isReservedEventName(eventName)) {
            return String.format(
                    "`%1$s` is a reserved Analytics event name and cannot be used", eventName);
        }

        return null;
    }

    @Nullable
    private static String getErrorForEventParameterName(String eventParameterName) {
        if (eventParameterName.length() > EVENT_PARAM_NAME_MAX_LENGTH) {
            String message =
                    "Analytics event parameter name must be %1$d characters or less (found %2$d)";
            return String.format(message, EVENT_PARAM_NAME_MAX_LENGTH, eventParameterName.length());
        }

        if (eventParameterName.isEmpty()) {
            return "Analytics event parameter name cannot be empty";
        }

        if (!Character.isAlphabetic(eventParameterName.charAt(0))) {
            String message =
                    "Analytics event parameter name must start with an alphabetic "
                            + "character (found %1$s)";
            return String.format(message, eventParameterName);
        }

        String message =
                "Analytics event name must only consist of letters, numbers and "
                        + "underscores (found %1$s)";
        for (int i = 0; i < eventParameterName.length(); i++) {
            char character = eventParameterName.charAt(i);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return String.format(message, eventParameterName);
            }
        }

        if (eventParameterName.startsWith("firebase_")) {
            return "Analytics event parameter name cannot be start with `firebase_`";
        }

        return null;
    }
}
