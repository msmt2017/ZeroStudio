/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.CLASS_SERVICE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_SERVICE;
import static org.jetbrains.uast.UastUtils.skipParenthesizedExprDown;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.UastLintUtils;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClassLiteralExpression;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Checks looking for issues related to the JobScheduler API */
public class JobSchedulerDetector extends Detector implements SourceCodeScanner {

    @SuppressWarnings("unchecked")
    public static final Implementation IMPLEMENTATION =
            new Implementation(JobSchedulerDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Issues that negatively affect battery life */
    public static final Issue ISSUE =
            Issue.create(
                            "JobSchedulerService",
                            "JobScheduler problems",
                            "This check looks for various common mistakes in using the "
                                    + "JobScheduler API: the service class must extend `JobService`, "
                                    + "the service must be registered in the manifest and the registration "
                                    + "must require the permission `android.permission.BIND_JOB_SERVICE`.",
                            Category.CORRECTNESS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/topic/performance/scheduling.html")
                    .setAndroidSpecific(true);

    private static final String CLASS_JOB_SERVICE = "android.app.job.JobService";

    /** Constructs a new {@link JobSchedulerDetector} */
    public JobSchedulerDetector() {}

    @Nullable
    @Override
    public List<String> getApplicableConstructorTypes() {
        return Collections.singletonList("android.app.job.JobInfo.Builder");
    }

    @Override
    public void visitConstructor(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod constructor) {
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }
        UExpression componentName = skipParenthesizedExprDown(arguments.get(1));
        if (componentName instanceof UReferenceExpression) {
            PsiElement resolved = ((UReferenceExpression) componentName).resolve();
            if (resolved instanceof PsiVariable) {
                componentName =
                        UastLintUtils.findLastAssignment((PsiVariable) resolved, componentName);
                if (componentName != null) {
                    componentName = skipParenthesizedExprDown(componentName);
                }
            }
        }
        if (!(componentName instanceof UCallExpression)) {
            return;
        }
        UCallExpression call = (UCallExpression) componentName;
        arguments = call.getValueArguments();
        if (arguments.size() < 2) {
            return;
        }
        UExpression typeReference = skipParenthesizedExprDown(arguments.get(1));
        if (!(typeReference instanceof UClassLiteralExpression)) {
            return;
        }
        UClassLiteralExpression classRef = (UClassLiteralExpression) typeReference;
        PsiType serviceType = classRef.getType();
        if (!(serviceType instanceof PsiClassType)) {
            return;
        }
        PsiClass serviceClass = ((PsiClassType) serviceType).resolve();
        if (serviceClass == null) {
            return;
        }
        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.inheritsFrom(serviceClass, CLASS_SERVICE, false)
                && !evaluator.inheritsFrom(serviceClass, CLASS_JOB_SERVICE, false)) {
            String message =
                    String.format(
                            "Scheduled job class %1$s must extend `android.app.job.JobService`",
                            serviceClass.getName());
            context.report(ISSUE, componentName, context.getLocation(componentName), message);
        } else {
            if (context.getDriver().isSuppressed(context, ISSUE, typeReference)) {
                return;
            }
            String fqcn = serviceType.getCanonicalText();
            Location location = context.getLocation(classRef);
            if (context.isGlobalAnalysis()) {
                ensureBindServicePermission(context, fqcn, location);
            } else {
                context.getPartialResults(ISSUE).map().put(fqcn, location);
            }
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        for (LintMap map : partialResults.maps()) {
            for (String fqcn : map) {
                Location location = map.getLocation(fqcn);
                if (location != null) {
                    ensureBindServicePermission(context, fqcn, location);
                }
            }
        }
    }

    private static void ensureBindServicePermission(
            @NonNull Context context, @NonNull String fqcn, @NonNull Location location) {
        // Make sure the app has
        //    android:permission="android.permission.BIND_JOB_SERVICE"
        // as well.

        Project project = context.getMainProject();
        Document mergedManifest = project.getMergedManifest();
        if (mergedManifest == null) {
            return;
        }
        Element manifest = mergedManifest.getDocumentElement();
        if (manifest == null) {
            return;
        }
        Element application = XmlUtils.getFirstSubTagByName(manifest, TAG_APPLICATION);
        if (application == null) {
            return;
        }

        Element service = XmlUtils.getFirstSubTagByName(application, TAG_SERVICE);

        while (service != null) {
            String name = Lint.resolveManifestName(service, project).replace('$', '.');
            if (fqcn.equals(name)) {
                // Check that it has the desired permission
                String permission = service.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
                if (!"android.permission.BIND_JOB_SERVICE".equals(permission)) {
                    // Also report the manifest location, if possible
                    LintClient client = context.getClient();
                    Location secondary = client.findManifestSourceLocation(service);
                    if (secondary != null) {
                        location =
                                location.withSecondary(
                                        secondary, "Service declaration here", false);
                    }

                    context.report(
                            ISSUE,
                            location,
                            "The manifest registration for this service does not declare "
                                    + "`android:permission=\"android.permission.BIND_JOB_SERVICE\"`");
                }
                return;
            }

            service = XmlUtils.getNextTagByName(service, TAG_SERVICE);
        }

        // Service not found in the manifest; flag it
        context.report(ISSUE, location, "Did not find a manifest registration for this service");
    }
}
