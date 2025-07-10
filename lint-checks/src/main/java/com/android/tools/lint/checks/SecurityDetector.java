/*
 * Copyright (C) 2011 The Android Open Source Project
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
import static com.android.SdkConstants.ATTR_EXPORTED;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PATH;
import static com.android.SdkConstants.ATTR_PATH_PATTERN;
import static com.android.SdkConstants.ATTR_PATH_PREFIX;
import static com.android.SdkConstants.ATTR_PERMISSION;
import static com.android.SdkConstants.ATTR_READ_PERMISSION;
import static com.android.SdkConstants.ATTR_WRITE_PERMISSION;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_GRANT_PERMISSION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_PATH_PERMISSION;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.tools.lint.detector.api.Constraints.targetSdkLessThan;
import static com.android.tools.lint.detector.api.Lint.getMethodName;
import static com.android.tools.lint.detector.api.LintFix.TODO;
import static com.android.xml.AndroidManifest.NODE_ACTION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.XmlUtils;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Checks that exported services request a permission. */
public class SecurityDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final Implementation IMPLEMENTATION_MANIFEST =
            new Implementation(SecurityDetector.class, Scope.MANIFEST_SCOPE);

    private static final Implementation IMPLEMENTATION_JAVA =
            new Implementation(SecurityDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Exported services */
    public static final Issue EXPORTED_SERVICE =
            Issue.create(
                            "ExportedService",
                            "Exported service does not require permission",
                            "Exported services (services which either set `exported=true` or contain "
                                    + "an intent-filter and do not specify `exported=false`) should define a "
                                    + "permission that an entity must have in order to launch the service "
                                    + "or bind to it. Without this, any application can use this service.",
                            Category.SECURITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION_MANIFEST)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/ExportedService");

    /** Exported content providers */
    public static final Issue EXPORTED_PROVIDER =
            Issue.create(
                            "ExportedContentProvider",
                            "Content provider does not require permission",
                            "Content providers are exported by default and any application on the "
                                    + "system can potentially use them to read and write data. If the content "
                                    + "provider provides access to sensitive data, it should be protected by "
                                    + "specifying `export=false` in the manifest or by protecting it with a "
                                    + "permission that can be granted to other applications.",
                            Category.SECURITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION_MANIFEST)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/ExportedContentProvider");

    /** Exported receivers */
    public static final Issue EXPORTED_RECEIVER =
            Issue.create(
                            "ExportedReceiver",
                            "Receiver does not require permission",
                            "Exported receivers (receivers which either set `exported=true` or contain "
                                    + "an intent-filter and do not specify `exported=false`) should define a "
                                    + "permission that an entity must have in order to launch the receiver "
                                    + "or bind to it. Without this, any application can use this receiver.",
                            Category.SECURITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION_MANIFEST)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/ExportedReceiver");

    /** Content provides which grant all URIs access */
    public static final Issue OPEN_PROVIDER =
            Issue.create(
                            "GrantAllUris",
                            "Content provider shares everything",
                            "The `<grant-uri-permission>` element allows specific paths to be shared. "
                                    + "This detector checks for a path URL of just '/' (everything), which is "
                                    + "probably not what you want; you should limit access to a subset.",
                            Category.SECURITY,
                            7,
                            Severity.WARNING,
                            IMPLEMENTATION_MANIFEST)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/GrantAllUris");

    /** Using java.io.File.setReadable(true, false) to set file world-readable */
    public static final Issue SET_READABLE =
            Issue.create(
                            "SetWorldReadable",
                            "`File.setReadable()` used to make file world-readable",
                            "Setting files world-readable is very dangerous, and likely to cause security "
                                    + "holes in applications. It is strongly discouraged; instead, applications should "
                                    + "use more formal mechanisms for interactions such as `ContentProvider`, "
                                    + "`BroadcastReceiver`, and `Service`.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION_JAVA)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/SetWorldReadable");

    /** Using java.io.File.setWritable(true, false) to set file world-writable */
    public static final Issue SET_WRITABLE =
            Issue.create(
                            "SetWorldWritable",
                            "`File.setWritable()` used to make file world-writable",
                            "Setting files world-writable is very dangerous, and likely to cause security "
                                    + "holes in applications. It is strongly discouraged; instead, applications should "
                                    + "use more formal mechanisms for interactions such as `ContentProvider`, "
                                    + "`BroadcastReceiver`, and `Service`.",
                            Category.SECURITY,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION_JAVA)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/SetWorldWritable");

    /** Using the world-writable flag */
    public static final Issue WORLD_WRITEABLE =
            Issue.create(
                            "WorldWriteableFiles",
                            "`openFileOutput()` with `MODE_WORLD_WRITEABLE`",
                            "There are cases where it is appropriate for an application to write "
                                    + "world writeable files, but these should be reviewed carefully to "
                                    + "ensure that they contain no private data, and that if the file is "
                                    + "modified by a malicious application it does not trick or compromise "
                                    + "your application.",
                            Category.SECURITY,
                            4,
                            Severity.WARNING,
                            IMPLEMENTATION_JAVA)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/WorldWriteableFiles");

    /** Using the world-readable flag */
    public static final Issue WORLD_READABLE =
            Issue.create(
                            "WorldReadableFiles",
                            "`openFileOutput()` with `MODE_WORLD_READABLE`",
                            "There are cases where it is appropriate for an application to write "
                                    + "world readable files, but these should be reviewed carefully to "
                                    + "ensure that they contain no private data that is leaked to other "
                                    + "applications.",
                            Category.SECURITY,
                            4,
                            Severity.WARNING,
                            IMPLEMENTATION_JAVA)
                    .setAndroidSpecific(true)
                    .addMoreInfo("https://goo.gle/WorldReadableFiles");

    private static final String FILE_CLASS = "java.io.File";

    /** Constructs a new {@link SecurityDetector} check */
    public SecurityDetector() {}

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_SERVICE, TAG_GRANT_PERMISSION, TAG_PROVIDER, TAG_ACTIVITY, TAG_RECEIVER);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        switch (tag) {
            case TAG_SERVICE:
                checkService(context, element);
                break;
            case TAG_GRANT_PERMISSION:
                checkGrantPermission(context, element);
                break;
            case TAG_PROVIDER:
                checkProvider(context, element);
                break;
            case TAG_RECEIVER:
                checkReceiver(context, element);
                break;
        }
    }

    /**
     * Returns true if the given manifest element explicitly set exported to true, false if it's
     * explicitly set to false, and null if the exported attribute is not specified.
     */
    @Nullable
    public static Boolean getExplicitExported(@NonNull Element element) {
        // Used to check whether an activity, service or broadcast receiver is exported.
        String exportValue = element.getAttributeNS(ANDROID_URI, ATTR_EXPORTED);
        if (!exportValue.isEmpty()) {
            return Boolean.parseBoolean(exportValue);
        } else {
            return null;
        }
    }

    /**
     * Specifying an intent filter implicitly exports the parent activity if it does not explicitly
     * set the exported attribute. As of Android 12, activities are never implicitly exported. (This
     * method will not check for an explicit {@code exported} attribute, so to check whether an
     * element is exported, call both methods (and filter by the targetSdkVersion for S).
     */
    public static boolean isImplicitlyExportedPreS(@NonNull Element element) {
        // Starting in Android S, the exported flag will always be specified,
        // so no need for this inference.
        for (Element child : XmlUtils.getSubTags(element)) {
            if (child.getTagName().equals(TAG_INTENT_FILTER)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isUnprotectedByPermission(Element element) {
        // Used to check whether an activity, service or broadcast receiver are
        // protected by a permission.
        String permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
        if (TODO.equals(permission)) { // temporary quickfix state: don't accept as solved
            return true;
        }
        if (permission.isEmpty()) {
            Node parent = element.getParentNode();
            if (parent.getNodeType() == Node.ELEMENT_NODE
                    && parent.getNodeName().equals(TAG_APPLICATION)) {
                Element application = (Element) parent;
                permission = application.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
                return permission.isEmpty();
            }
        }

        return false;
    }

    /**
     * See https://labs.mwrinfosecurity.com/blog/android-wear-security-analysis for information
     * about why an exported declaration of WearListenerService does not need a permission. (Since
     * the service itself checks to see if the caller is Play services)
     *
     * @param element The service element to be checked.
     * @return whether a service has an `intent-filter` action pointing to the ones supported by
     *     WearListenerService.
     */
    private static boolean isWearableListenerServiceAction(@NonNull Element element) {
        // Checks whether a service has an action for a WearableListenerService
        // see
        // developers.google.com/android/reference/com/google/android/gms/wearable/WearableListenerService
        // for details on the applicable actions.
        for (Element child : XmlUtils.getSubTags(element)) {
            if (child.getTagName().equals(TAG_INTENT_FILTER)) {
                for (Element innerChild : XmlUtils.getSubTags(child)) {
                    if (innerChild.getTagName().equals(NODE_ACTION)) {
                        String name = innerChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if ("com.google.android.gms.wearable.BIND_LISTENER".equals(name)
                                // deprecated
                                || "com.google.android.gms.wearable.DATA_CHANGED".equals(name)
                                || "com.google.android.gms.wearable.MESSAGE_RECEIVED".equals(name)
                                || "com.google.android.gms.wearable.CAPABILITY_CHANGED".equals(name)
                                || "com.google.android.gms.wearable.CHANNEL_EVENT".equals(name)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean isStandardReceiver(Element element) {
        // Play Services also the following receiver which we'll consider standard
        // in the sense that it doesn't require a separate permission
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if ("com.google.android.gms.tagmanager.InstallReferrerReceiver".equals(name)) {
            return true;
        }

        // Checks whether a broadcast receiver receives a standard Android action
        for (Element child : XmlUtils.getSubTags(element)) {
            if (child.getTagName().equals(TAG_INTENT_FILTER)) {
                for (Element innerChild : XmlUtils.getSubTags(child)) {
                    if (innerChild.getTagName().equals(NODE_ACTION)) {
                        String categoryString = innerChild.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        return categoryString.startsWith("android.");
                    }
                }
            }
        }

        return false;
    }

    private static void checkReceiver(XmlContext context, Element element) {
        Boolean explicitlyExported = getExplicitExported(element);
        boolean exported =
                explicitlyExported != null && explicitlyExported
                        || explicitlyExported == null && isImplicitlyExportedPreS(element);
        if (exported && isUnprotectedByPermission(element) && !isStandardReceiver(element)) {
            // No declared permission for this exported receiver: complain
            LintFix fix = LintFix.create().set().todo(ANDROID_URI, ATTR_PERMISSION).build();
            Incident incident =
                    new Incident(
                            EXPORTED_RECEIVER,
                            element,
                            context.getNameLocation(element),
                            "Exported receiver does not require permission",
                            fix);
            if (explicitlyExported == null) {
                // Implicitly exported; only an issue if targetSdkVersion < 31
                context.report(incident, targetSdkLessThan(31));
            } else {
                context.report(incident);
            }
        }
    }

    private static void checkService(XmlContext context, Element element) {
        Boolean explicitlyExported = getExplicitExported(element);
        boolean exported =
                explicitlyExported != null && explicitlyExported
                        || explicitlyExported == null && isImplicitlyExportedPreS(element);
        if (exported
                && isUnprotectedByPermission(element)
                && !isWearableListenerServiceAction(element)) {
            // No declared permission for this exported service: complain
            LintFix fix1 = LintFix.create().set().todo(ANDROID_URI, ATTR_PERMISSION).build();
            LintFix fix2 = LintFix.create().set(ANDROID_URI, ATTR_EXPORTED, VALUE_FALSE).build();
            LintFix both = LintFix.create().alternatives(fix1, fix2);

            Incident incident =
                    new Incident(
                            EXPORTED_SERVICE,
                            element,
                            context.getNameLocation(element),
                            "Exported service does not require permission",
                            both);
            if (explicitlyExported == null) {
                // Implicitly exported; only an issue if targetSdkVersion < 31
                context.report(incident, targetSdkLessThan(31));
            } else {
                context.report(incident);
            }
        }
    }

    private static void checkGrantPermission(XmlContext context, Element element) {
        Attr path = element.getAttributeNodeNS(ANDROID_URI, ATTR_PATH);
        Attr prefix = element.getAttributeNodeNS(ANDROID_URI, ATTR_PATH_PREFIX);
        Attr pattern = element.getAttributeNodeNS(ANDROID_URI, ATTR_PATH_PATTERN);

        String msg = "Content provider shares everything; this is potentially dangerous";
        if (path != null && path.getValue().equals("/")) {
            context.report(OPEN_PROVIDER, path, context.getLocation(path), msg);
        }
        if (prefix != null && prefix.getValue().equals("/")) {
            context.report(OPEN_PROVIDER, prefix, context.getLocation(prefix), msg);
        }
        if (pattern != null
                && (pattern.getValue().equals("/")
                /* || pattern.getValue().equals(".*")*/ )) {
            context.report(OPEN_PROVIDER, pattern, context.getLocation(pattern), msg);
        }
    }

    private static void checkProvider(XmlContext context, Element element) {
        String exportValue = element.getAttributeNS(ANDROID_URI, ATTR_EXPORTED);
        // Content providers are exported by default
        boolean exported = true;
        if (!exportValue.isEmpty()) {
            exported = Boolean.parseBoolean(exportValue);
        }

        if (exported) {
            // Just check for some use of permissions. Other Lint checks can check the validity
            // of the permissions. We'll accept the permission, readPermission, or writePermission
            // attributes on the provider element, or a path-permission element.
            String permission = element.getAttributeNS(ANDROID_URI, ATTR_READ_PERMISSION);
            if (permission.isEmpty()) {
                permission = element.getAttributeNS(ANDROID_URI, ATTR_WRITE_PERMISSION);
                if (permission.isEmpty()) {
                    permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION);
                    if (permission.isEmpty()) {
                        // No permission attributes? Check for path-permission.

                        // TODO: Add a Lint check to ensure the path-permission is good, similar to
                        // the grant-uri-permission check.
                        boolean hasPermission = false;
                        for (Element child : XmlUtils.getSubTags(element)) {
                            String tag = child.getTagName();
                            if (tag.equals(TAG_PATH_PERMISSION)) {
                                hasPermission = true;
                                break;
                            }
                        }

                        Node parent = element.getParentNode();
                        if (parent != null && !parent.getNodeName().equals(TAG_APPLICATION)) {
                            // Ignore <provider> in queries and other non-application contexts
                            return;
                        }

                        if (SliceDetector.Companion.isSliceProvider(element)) {
                            return;
                        }

                        // 163364847: Don't flag provider elements that are there to remove
                        // merged provider from library
                        if ("remove".equals(element.getAttributeNS(TOOLS_URI, "node"))) {
                            return;
                        }

                        if (!hasPermission) {
                            LintFix fix =
                                    LintFix.create()
                                            .set(ANDROID_URI, ATTR_EXPORTED, VALUE_FALSE)
                                            .build();
                            context.report(
                                    EXPORTED_PROVIDER,
                                    element,
                                    context.getNameLocation(element),
                                    "Exported content providers can provide access to "
                                            + "potentially sensitive data",
                                    fix);
                        }
                    }
                }
            }
        }
    }

    // ---- Implements SourceCodeScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                // These are the API calls that can accept a
                // MODE_WORLD_READABLE/MODE_WORLD_WRITEABLE
                // argument.
                "openFileOutput",
                "getSharedPreferences",
                "getDir",
                // These API calls can be used to set files world-readable or world-writable
                "setReadable",
                "setWritable");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        List<UExpression> args = node.getValueArguments();
        String methodName = getMethodName(node);
        if (context.getEvaluator().isMemberInSubClassOf(method, FILE_CLASS, false)) {
            // Report calls to java.io.File.setReadable(true, false) or
            // java.io.File.setWritable(true, false)
            if ("setReadable".equals(methodName)) {
                if (args.size() == 2
                        && Boolean.TRUE.equals(ConstantEvaluator.evaluate(context, args.get(0)))
                        && Boolean.FALSE.equals(ConstantEvaluator.evaluate(context, args.get(1)))) {
                    context.report(
                            SET_READABLE,
                            node,
                            context.getLocation(node),
                            "Setting file permissions to world-readable can be "
                                    + "risky, review carefully");
                }
            } else if ("setWritable".equals(methodName)) {
                if (args.size() == 2
                        && Boolean.TRUE.equals(ConstantEvaluator.evaluate(context, args.get(0)))
                        && Boolean.FALSE.equals(ConstantEvaluator.evaluate(context, args.get(1)))) {
                    context.report(
                            SET_WRITABLE,
                            node,
                            context.getLocation(node),
                            "Setting file permissions to world-writable can be "
                                    + "risky, review carefully");
                }
            }
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(USimpleNameReferenceExpression.class);
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new IdentifierVisitor(context);
    }

    private static class IdentifierVisitor extends UElementHandler {
        private final JavaContext context;

        public IdentifierVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitSimpleNameReferenceExpression(
                @NonNull USimpleNameReferenceExpression node) {
            String name = node.getIdentifier();
            if ("MODE_WORLD_WRITEABLE".equals(name)) {
                Location location = context.getLocation(node);
                context.report(
                        WORLD_WRITEABLE,
                        node,
                        location,
                        "Using `MODE_WORLD_WRITEABLE` when creating files can be "
                                + "risky, review carefully");
            } else if ("MODE_WORLD_READABLE".equals(name)) {
                Location location = context.getLocation(node);
                context.report(
                        WORLD_READABLE,
                        node,
                        location,
                        "Using `MODE_WORLD_READABLE` when creating files can be "
                                + "risky, review carefully");
            }
        }
    }
}
