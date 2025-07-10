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

import static com.android.tools.lint.detector.api.ExtensionSdk.ANDROID_SDK_ID;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.ApiConstraint;
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

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Checks that subclasses of certain APIs are overriding all methods that were abstract in one or
 * more earlier API levels that are still targeted by the minSdkVersion of this project.
 */
public class OverrideConcreteDetector extends Detector implements SourceCodeScanner {
    /** Are previously-abstract methods all overridden? */
    public static final Issue ISSUE =
            Issue.create(
                            "OverrideAbstract",
                            "Not overriding abstract methods on older platforms",
                            "To improve the usability of some APIs, some methods that used to be"
                                + " `abstract` have been made concrete by adding default"
                                + " implementations. This means that when compiling with new"
                                + " versions of the SDK, your code does not have to override these"
                                + " methods.\n"
                                + "\n"
                                + "However, if your code is also targeting older versions of the"
                                + " platform where these methods were still `abstract`, the code"
                                + " will crash. You must override all methods that used to be"
                                + " abstract in any versions targeted by your application's "
                                + "`minSdkVersion`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    OverrideConcreteDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    // This check is currently hardcoded for the specific case of the
    // NotificationListenerService change in API 21. We should consider
    // attempting to infer this information automatically from changes in
    // the API current.txt file and making this detector more database driven,
    // like the API detector.

    private static final String NOTIFICATION_LISTENER_SERVICE_FQN =
            "android.service.notification.NotificationListenerService";
    public static final String STATUS_BAR_NOTIFICATION_FQN =
            "android.service.notification.StatusBarNotification";
    private static final String ON_NOTIFICATION_POSTED = "onNotificationPosted";
    private static final String ON_NOTIFICATION_REMOVED = "onNotificationRemoved";
    private static final int CONCRETE_IN = 21;

    /** Constructs a new {@link OverrideConcreteDetector} */
    public OverrideConcreteDetector() {}

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(NOTIFICATION_LISTENER_SERVICE_FQN);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isAbstract(declaration)) {
            return;
        }

        ApiConstraint minSdk =
                ApiConstraint.Companion.max(
                        context.getProject().getMinSdkVersions(),
                        getTargetApi(context.getEvaluator(), declaration),
                        false);
        ApiConstraint platformConstraint = minSdk.findSdk(ANDROID_SDK_ID, true);
        if (platformConstraint == null || platformConstraint.min() >= CONCRETE_IN) {
            return;
        }

        String[] methodNames = {ON_NOTIFICATION_POSTED, ON_NOTIFICATION_REMOVED};
        for (String methodName : methodNames) {
            boolean found = false;
            for (PsiMethod method : declaration.getJavaPsi().findMethodsByName(methodName, true)) {
                // Make sure it's not the base method, but that it's been defined
                // in a subclass, concretely
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    continue;
                }
                if (NOTIFICATION_LISTENER_SERVICE_FQN.equals(containingClass.getQualifiedName())) {
                    continue;
                }
                // Make sure subclass isn't just defining another abstract definition
                // of the method
                if (evaluator.isAbstract(method)) {
                    continue;
                }
                // Make sure it has the exact right signature
                if (method.getParameterList().getParametersCount() != 1) {
                    continue; // Wrong signature
                }
                if (!evaluator.parameterHasType(method, 0, STATUS_BAR_NOTIFICATION_FQN)) {
                    continue;
                }

                found = true;
                break;
            }

            if (!found) {
                String message =
                        String.format(
                                Locale.US,
                                "Must override `%1$s.%2$s(%3$s)`: Method was abstract until %4$d,"
                                    + " and your `minSdkVersion` is %5$d",
                                NOTIFICATION_LISTENER_SERVICE_FQN,
                                methodName,
                                STATUS_BAR_NOTIFICATION_FQN,
                                CONCRETE_IN,
                                minSdk.min());
                context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
                break;
            }
        }
    }

    @Nullable
    private static ApiConstraint getTargetApi(
            @NonNull JavaEvaluator evaluator, @NonNull UClass node) {
        while (node != null) {
            ApiConstraint targetApi = ApiDetector.getTargetApi(evaluator, node);
            if (targetApi != null) {
                return targetApi;
            }

            node = UastUtils.getParentOfType(node, UClass.class, true);
        }

        return null;
    }
}
