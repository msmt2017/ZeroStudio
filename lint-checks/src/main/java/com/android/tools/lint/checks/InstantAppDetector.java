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
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TAG_USES_PERMISSION;
import static com.android.SdkConstants.TAG_USES_SDK;
import static com.android.utils.BuildScriptUtil.findGradleBuildFile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.model.LintModelModuleType;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Checks related to instant apps. FIXME: This needs to be refactored to support the feature plugin.
 */
public class InstantAppDetector extends ResourceXmlDetector implements SourceCodeScanner {

    @SuppressWarnings("unchecked")
    public static final Implementation IMPLEMENTATION =
            new Implementation(
                    InstantAppDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                    Scope.MANIFEST_SCOPE,
                    Scope.JAVA_FILE_SCOPE);

    /** Instant App related issues */
    public static final Issue ISSUE =
            Issue.create(
                    "InstantApps",
                    "Instant App Issues",

                    // TODO: Need full explanation here
                    "This issue flags code that will not work correctly in Instant Apps",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Constructs a new {@link InstantAppDetector} */
    public InstantAppDetector() {}

    /**
     * Checks whether the source is part of an Instant App module -- or a module included from an
     * instant app module
     */
    private static boolean isInstantApp(@NonNull Context context) {
        return isInstantApp(context.getProject());
    }

    /** Checks whether the given project is an instant app module */
    private static boolean isInstantApp(@NonNull Project project) {
        return project.getType() == LintModelModuleType.INSTANT_APP;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_PROVIDER, TAG_RECEIVER, TAG_SERVICE, TAG_USES_SDK, TAG_USES_PERMISSION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        switch (tag) {
            case TAG_PROVIDER:
            case TAG_RECEIVER:
            case TAG_SERVICE:
                {
                    report(
                            ISSUE,
                            context,
                            element,
                            "Instant Apps are not allowed to export services, receivers, "
                                    + "and providers");
                    break;
                }

                // This check is probably not relevant anymore
            case TAG_USES_SDK:
                {
                    Attr targetSdkVersionNode =
                            element.getAttributeNodeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
                    if (targetSdkVersionNode != null) {
                        String target = targetSdkVersionNode.getValue();
                        AndroidVersion version = SdkVersionInfo.getVersion(target, null);
                        if (version != null && version.getFeatureLevel() < 23) {
                            report(
                                    ISSUE,
                                    context,
                                    targetSdkVersionNode,
                                    "Instant Apps must target API 23+");
                        }
                    }
                    break;
                }

            case TAG_USES_PERMISSION:
                {
                    String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    switch (name) {
                        case "android.permission.ACCESS_NETWORK_STATE":
                        case "android.permission.ACCESS_WIFI_STATE":
                        case "android.permission.INTERNET":
                        case "android.permission.WAKE_LOCK":
                        case "android.permission.VIBRATE":
                        case "android.permission.ACCESS_COARSE_LOCATION":
                        case "android.permission.ACCESS_FINE_LOCATION":
                        case "android.permission.RECORD_AUDIO":
                            // Still being considered: READ_GSERVICES, GET_ACCOUNTS
                            return;

                        default:
                            if (!name.startsWith("android.permission.")) {
                                if (name.endsWith(".permission.C2D_MESSAGE")) {
                                    report(
                                            ISSUE,
                                            context,
                                            element,
                                            "Instant Apps are not allowed to use Google Cloud "
                                                    + "Messaging (GCM)");
                                }
                                return;
                            }
                            report(
                                    ISSUE,
                                    context,
                                    element,
                                    "This permission is not allowed for Instant Apps");
                    }

                    break;
                }
        }
    }

    @Override
    public void checkMergedProject(@NonNull Context context) {
        checkMergedManifest(context);
    }

    private static void checkMergedManifest(@NonNull Context context) {
        if (context.getProject() != context.getMainProject()) {
            return;
        }

        // Check whether there is a merged targetSdkVersion that is lower than 23. If so
        // report it.

        if (!isInstantApp(context)) {
            return;
        }

        Project project = context.getMainProject();
        Document mergedManifest = project.getMergedManifest();
        if (mergedManifest == null) {
            return;
        }
        Element root = mergedManifest.getDocumentElement();
        if (root == null) {
            return;
        }
        checkMultipleLauncherActivities(context, root);
        if (context.getScope().contains(Scope.GRADLE_FILE)) {
            checkMergedTargetSdkVersion(context, project, root);
        }
    }

    private static void checkMultipleLauncherActivities(@NonNull Context context, Element root) {
        Element application = XmlUtils.getFirstSubTagByName(root, TAG_APPLICATION);
        if (application == null) {
            return;
        }
        Element activity = XmlUtils.getFirstSubTagByName(application, TAG_ACTIVITY);
        Element launchableActivity = null;
        while (activity != null) {
            if (ManifestDetector.isLaunchableActivity(activity)) {
                if (launchableActivity == null) {
                    // First one
                    launchableActivity = activity;
                } else {
                    // More than one found: complain
                    LintClient client = context.getClient();

                    Pair<File, ? extends Node> source = client.findManifestSourceNode(activity);
                    if (source != null) {
                        XmlParser parser = client.getXmlParser();
                        // Don't search for the category tag directly; the manifest merger
                        // does not create a unique key for it across activities, so we end
                        // up always returning the first one. If we instead search for the
                        // activity, we'll get the correct unique node, and then we can simply
                        // search for the category node under the source node.
                        Node locationNode =
                                ManifestDetector.findLaunchableCategoryNode(
                                        (Element) source.getSecond());
                        if (locationNode == null) {
                            locationNode = activity;
                        }
                        Location location = parser.getLocation(source.getFirst(), locationNode);
                        Pair<File, ? extends Node> original =
                                client.findManifestSourceNode(launchableActivity);
                        if (original != null && original.getSecond() != source.getSecond()) {
                            locationNode =
                                    ManifestDetector.findLaunchableCategoryNode(
                                            (Element) original.getSecond());
                            if (locationNode == null) {
                                locationNode = original.getSecond();
                            }
                            Location secondary =
                                    parser.getLocation(original.getFirst(), locationNode);
                            secondary.setMessage("Other launchable activity here");
                            location.setSecondary(secondary);
                        }

                        context.report(
                                ISSUE,
                                location,
                                "Instant Apps are not allowed to have multiple "
                                        + "launchable activities");
                    }
                }
            }

            activity = XmlUtils.getNextTagByName(activity, TAG_ACTIVITY);
        }
    }

    private static void checkMergedTargetSdkVersion(
            @NonNull Context context, Project project, Element root) {
        // Look up targetSdkVersion from the merged manifest to make sure we also pick up
        // on any Gradle-overrides
        Element usesSdk = XmlUtils.getFirstSubTagByName(root, TAG_USES_SDK);
        if (usesSdk == null) {
            return;
        }
        Attr targetSdkVersionNode =
                usesSdk.getAttributeNodeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION);
        if (targetSdkVersionNode != null) {
            String target = targetSdkVersionNode.getValue();
            AndroidVersion version = SdkVersionInfo.getVersion(target, null);
            if (version != null && version.getFeatureLevel() < 23) {
                File dir = project.getDir();
                File gradle = project.isGradleProject() ? findGradleBuildFile(dir) : null;
                Location location =
                        Location.create(gradle != null && gradle.isFile() ? gradle : dir);
                context.report(
                        ISSUE, location, "Instant Apps must target API 23+ (was " + version + ")");
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "notify", "registerReceiver", "getMacAddress", "getAddress", "getLong");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        switch (method.getName()) {
            case "notify":
                if (evaluator.isMemberInClass(method, "android.app.NotificationManager")) {
                    report(
                            ISSUE,
                            context,
                            call,
                            "Instant Apps are not allowed to create notifications");
                }
                break;
            case "registerReceiver":
                if (evaluator.isMemberInClass(method, "android.content.Context")) {
                    report(
                            ISSUE,
                            context,
                            call,
                            "Instant Apps are not allowed to listen to broadcasts from "
                                    + "system or other apps");
                }
                break;
            case "getMacAddress":
                if (evaluator.isMemberInClass(method, "android.net.wifi.WifiInfo")) {
                    report(ISSUE, context, call, getPlaceHolderError("Mac Addresses"));
                }
                break;
            case "getAddress":
                if (evaluator.isMemberInClass(method, "android.bluetooth.BluetoothAdapter")) {
                    report(ISSUE, context, call, getPlaceHolderError("Mac Addresses"));
                }
                break;
            case "getLong":
                if (evaluator.isMemberInClass(method, "com.google.android.gsf.Gservices")) {
                    List<UExpression> arguments = call.getValueArguments();
                    if (arguments.size() == 3) {
                        Object key = ConstantEvaluator.evaluate(context, arguments.get(1));
                        if ("android_id".equals(key)) {
                            report(ISSUE, context, call, getPlaceHolderError("Android Id"));
                        }
                    }
                }
                break;
            default:
                assert false;
        }
    }

    private static String getPlaceHolderError(String type) {
        return "Instant Apps accessing \"" + type + "\" will get a XXX value";
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SERIAL", "ANDROID_ID");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        if (!(referenced instanceof PsiField)) {
            return;
        }
        PsiClass containingClass = ((PsiField) referenced).getContainingClass();
        if (containingClass == null) {
            return;
        }
        String qualifiedName = containingClass.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        switch (qualifiedName) {
            case "android.os.Build":
                if ("SERIAL".equals(reference.getResolvedName())) {
                    report(ISSUE, context, reference, getPlaceHolderError("Build Serial"));
                }
                break;
            case "android.provider.Settings.Secure":
                if ("ANDROID_ID".equals(reference.getResolvedName())) {
                    report(
                            ISSUE,
                            context,
                            reference,
                            getPlaceHolderError("Settings.Secure Android Id"));
                }
                break;
        }
    }

    private static void report(
            @NonNull Issue issue,
            @NonNull XmlContext context,
            @NonNull Node node,
            @NonNull String message) {
        if (isInstantApp(context)) {
            context.report(issue, node, context.getLocation(node), message);
        }
    }

    private static void report(
            @NonNull Issue issue,
            @NonNull JavaContext context,
            @NonNull UElement element,
            @NonNull String message) {
        if (isInstantApp(context)) {
            context.report(issue, element, context.getLocation(element), message);
        }
    }
}
