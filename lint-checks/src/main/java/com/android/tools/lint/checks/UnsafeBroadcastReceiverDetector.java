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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.CLASS_BROADCASTRECEIVER;
import static com.android.SdkConstants.CLASS_CONTEXT;
import static com.android.SdkConstants.CLASS_INTENT;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.utils.XmlUtils.getFirstSubTagByName;
import static com.android.utils.XmlUtils.getSubTagsByName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.XmlUtils;

import com.google.common.collect.Sets;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;

import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastFacade;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class UnsafeBroadcastReceiverDetector extends Detector
        implements SourceCodeScanner, XmlScanner {

    // TODO: Use the new merged manifest model

    /* Description of check implementations:
     *
     * UnsafeProtectedBroadcastReceiver check
     *
     * If a receiver is declared in the application manifest that has an intent-filter
     * with an action string that matches a protected-broadcast action string,
     * then if that receiver has an onReceive method, ensure that the method calls
     * getAction at least once.
     *
     * With this check alone, false positives will occur if the onReceive method
     * passes the received intent to another method that calls getAction.
     * We look for any calls to aload_2 within the method bytecode, which could
     * indicate loading the inputted intent onto the stack to use in a call
     * to another method. In those cases, still report the issue, but
     * report in the description that the finding may be a false positive.
     * An alternative implementation option would be to omit reporting the issue
     * at all when a call to aload_2 exists.
     *
     * UnprotectedSMSBroadcastReceiver check
     *
     * If a receiver is declared in AndroidManifest that has an intent-filter
     * with action string SMS_DELIVER or SMS_RECEIVED, ensure that the
     * receiver requires callers to have the BROADCAST_SMS permission.
     *
     * It is possible that the receiver may check the sender's permission by
     * calling checkCallingPermission, which could cause a false positive.
     * However, application developers should still be encouraged to declare
     * the permission requirement in the manifest where it can be easily
     * audited.
     *
     * Future work: Add checks for other action strings that should require
     * particular permissions be checked, such as
     * android.provider.Telephony.WAP_PUSH_DELIVER
     *
     * Note that neither of these checks address receivers dynamically created at runtime,
     * only ones that are declared in the application manifest.
     */

    public static final Issue ACTION_STRING =
            Issue.create(
                            "UnsafeProtectedBroadcastReceiver",
                            "Unsafe Protected `BroadcastReceiver`",
                            "`BroadcastReceiver`s that declare an intent-filter for a"
                                + " protected-broadcast action string must check that the received"
                                + " intent's action string matches the expected value, otherwise it"
                                + " is possible for malicious actors to spoof intents.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    UnsafeBroadcastReceiverDetector.class,
                                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                                    Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo("https://goo.gle/UnsafeProtectedBroadcastReceiver");

    public static final Issue BROADCAST_SMS =
            Issue.create(
                            "UnprotectedSMSBroadcastReceiver",
                            "Unprotected SMS `BroadcastReceiver`",
                            "BroadcastReceivers that declare an intent-filter for `SMS_DELIVER` or"
                                + " `SMS_RECEIVED` must ensure that the caller has the"
                                + " `BROADCAST_SMS` permission, otherwise it is possible for"
                                + " malicious actors to spoof intents.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            new Implementation(
                                    UnsafeBroadcastReceiverDetector.class, Scope.MANIFEST_SCOPE))
                    .addMoreInfo("https://goo.gle/UnprotectedSMSBroadcastReceiver");

    private Set<String> mReceiversWithProtectedBroadcastIntentFilter = null;

    public UnsafeBroadcastReceiverDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (TAG_RECEIVER.equals(tag)) {
            String name = Lint.resolveManifestName(element, context.getProject());
            String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            // If no permission attribute, then if any exists at the application
            // element, it applies
            if (permission.isEmpty()) {
                Element parent = (Element) element.getParentNode();
                permission = parent.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
            }
            Element filter = getFirstSubTagByName(element, TAG_INTENT_FILTER);
            if (filter != null) {
                for (Element action : getSubTagsByName(filter, TAG_ACTION)) {
                    String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (("android.provider.Telephony.SMS_DELIVER".equals(actionName)
                                    || "android.provider.Telephony.SMS_RECEIVED".equals(actionName))
                            && !"android.permission.BROADCAST_SMS".equals(permission)) {
                        LintFix fix =
                                fix().set(
                                                ANDROID_URI,
                                                ATTR_PERMISSION,
                                                "android.permission.BROADCAST_SMS")
                                        .build();
                        context.report(
                                BROADCAST_SMS,
                                element,
                                context.getNameLocation(element),
                                "BroadcastReceivers that declare an intent-filter for "
                                        + "`SMS_DELIVER` or `SMS_RECEIVED` must ensure that the "
                                        + "caller has the `BROADCAST_SMS` permission, otherwise it "
                                        + "is possible for malicious actors to spoof intents",
                                fix);
                    } else if (BroadcastReceiverUtils.isProtectedBroadcast(actionName)) {
                        if (mReceiversWithProtectedBroadcastIntentFilter == null) {
                            mReceiversWithProtectedBroadcastIntentFilter = Sets.newHashSet();
                        }
                        mReceiversWithProtectedBroadcastIntentFilter.add(name);
                    }
                }
            }
        }
    }

    private Set<String> getReceiversWithProtectedBroadcastIntentFilter(@NonNull Context context) {
        if (mReceiversWithProtectedBroadcastIntentFilter == null) {
            mReceiversWithProtectedBroadcastIntentFilter = Sets.newHashSet();
            if (!context.getScope().contains(Scope.MANIFEST)) {
                // Compute from merged manifest
                Project mainProject = context.getMainProject();
                Document mergedManifest = mainProject.getMergedManifest();
                if (mergedManifest != null && mergedManifest.getDocumentElement() != null) {
                    Element application =
                            getFirstSubTagByName(
                                    mergedManifest.getDocumentElement(), TAG_APPLICATION);
                    if (application != null) {
                        for (Element element : XmlUtils.getSubTags(application)) {
                            if (TAG_RECEIVER.equals(element.getTagName())) {
                                Element filter = getFirstSubTagByName(element, TAG_INTENT_FILTER);
                                if (filter != null) {
                                    for (Element action : getSubTagsByName(filter, TAG_ACTION)) {
                                        String actionName =
                                                action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                                        if (BroadcastReceiverUtils.isProtectedBroadcast(
                                                actionName)) {
                                            String name = Lint.resolveManifestName(element, mainProject);
                                            mReceiversWithProtectedBroadcastIntentFilter.add(name);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return mReceiversWithProtectedBroadcastIntentFilter;
    }

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_BROADCASTRECEIVER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String name = declaration.getName();
        if (name == null) {
            // anonymous classes can't be the ones referenced in the manifest
            return;
        }
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (!getReceiversWithProtectedBroadcastIntentFilter(context).contains(qualifiedName)) {
            return;
        }
        JavaEvaluator evaluator = context.getEvaluator();
        for (PsiMethod method : declaration.getJavaPsi().findMethodsByName("onReceive", false)) {
            if (evaluator.parametersMatch(method, CLASS_CONTEXT, CLASS_INTENT)) {
                checkOnReceive(context, method);
            }
        }
    }

    private static void checkOnReceive(@NonNull JavaContext context, @NonNull PsiMethod method) {
        // Search for call to getAction but also search for references to aload_2,
        // which indicates that the method is making use of the received intent in
        // some way.
        //
        // If the onReceive method doesn't call getAction but does make use of
        // the received intent, it is possible that it is passing it to another
        // method that might be performing the getAction check, so we warn that the
        // finding may be a false positive. (An alternative option would be to not
        // report a finding at all in this case.)
        PsiParameter parameter = method.getParameterList().getParameters()[1];
        OnReceiveVisitor visitor = new OnReceiveVisitor(context.getEvaluator(), parameter);
        UastFacade.INSTANCE.getMethodBody(method).accept(visitor);
        if (!visitor.getCallsGetAction()) {
            String report;
            if (!visitor.getUsesIntent()) {
                report =
                        "This broadcast receiver declares an intent-filter for a protected"
                            + " broadcast action string, which can only be sent by the system, not"
                            + " third-party applications. However, the receiver's `onReceive`"
                            + " method does not appear to call `getAction` to ensure that the"
                            + " received Intent's action string matches the expected value,"
                            + " potentially making it possible for another actor to send a spoofed"
                            + " intent with no action string or a different action string and cause"
                            + " undesired behavior.";
            } else {
                // An alternative implementation option is to not report a finding at all in
                // this case, if we are worried about false positives causing confusion or
                // resulting in developers ignoring other lint warnings.
                report =
                        "This broadcast receiver declares an intent-filter for a protected "
                                + "broadcast action string, which can only be sent by the system, "
                                + "not third-party applications. However, the receiver's onReceive "
                                + "method does not appear to call getAction to ensure that the "
                                + "received Intent's action string matches the expected value, "
                                + "potentially making it possible for another actor to send a "
                                + "spoofed intent with no action string or a different action "
                                + "string and cause undesired behavior. In this case, it is "
                                + "possible that the onReceive method passed the received Intent "
                                + "to another method that checked the action string. If so, this "
                                + "finding can safely be ignored.";
            }
            Location location = context.getNameLocation(method);
            context.report(ACTION_STRING, method, location, report);
        }
    }

    private static class OnReceiveVisitor extends AbstractUastVisitor {
        @NonNull private final JavaEvaluator mEvaluator;
        @Nullable private final PsiParameter mParameter;
        private boolean mCallsGetAction;
        private boolean mUsesIntent;

        public OnReceiveVisitor(@NonNull JavaEvaluator context, @Nullable PsiParameter parameter) {
            mEvaluator = context;
            mParameter = parameter;
        }

        public boolean getCallsGetAction() {
            return mCallsGetAction;
        }

        public boolean getUsesIntent() {
            return mUsesIntent;
        }

        @Override
        public boolean visitCallExpression(@NonNull UCallExpression node) {
            if (!mCallsGetAction && UastExpressionUtils.isMethodCall(node)) {
                PsiMethod method = node.resolve();
                if (method != null
                        && "getAction".equals(method.getName())
                        && mEvaluator.isMemberInSubClassOf(method, CLASS_INTENT, false)) {
                    mCallsGetAction = true;
                }
            }

            return super.visitCallExpression(node);
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            if (!mUsesIntent && mParameter != null) {
                PsiElement resolved = node.resolve();
                if (mParameter.isEquivalentTo(resolved)) {
                    mUsesIntent = true;
                }
            }
            return super.visitSimpleNameReferenceExpression(node);
        }
    }
}
