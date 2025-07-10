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

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT_ID;
import static com.android.SdkConstants.APP_PREFIX;
import static com.android.SdkConstants.ATTR_FONT_PROVIDER_AUTHORITY;
import static com.android.SdkConstants.ATTR_FONT_PROVIDER_CERTS;
import static com.android.SdkConstants.ATTR_FONT_PROVIDER_PACKAGE;
import static com.android.SdkConstants.ATTR_FONT_PROVIDER_QUERY;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.SUPPORT_LIB_GROUP_ID;
import static com.android.SdkConstants.TAG_FONT;
import static com.android.SdkConstants.TAG_FONT_FAMILY;
import static com.android.tools.lint.detector.api.Lint.coalesce;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.fonts.DownloadableParseResult;
import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontLoader;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.FontQueryParserError;
import com.android.ide.common.fonts.MutableFontDetail;
import com.android.ide.common.fonts.QueryResolver;
import com.android.ide.common.gradle.Module;
import com.android.ide.common.gradle.Version;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.model.LintModelExternalLibrary;
import com.android.tools.lint.model.LintModelLibrary;
import com.android.tools.lint.model.LintModelMavenName;
import com.android.tools.lint.model.LintModelVariant;
import com.android.utils.XmlUtils;

import com.google.common.base.Joiner;
import com.intellij.openapi.util.text.StringUtil;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FontDetector extends ResourceXmlDetector {
    // TODO: Change this to the API version where we don't have to rely on appcompat for
    // downloadable
    // fonts loading at runtime.
    public static final int FUTURE_API_VERSION_WHERE_DOWNLOADABLE_FONTS_WORK_IN_FRAMEWORK =
            SdkVersionInfo.HIGHEST_KNOWN_API + 10;

    public static final String KEY_ARTIFACT_ID = "artifact";
    public static final String KEY_UNEXPECTED_NAMESPACE = "unexpected-ns";
    public static final String KEY_APP_NAMESPACE = "app-namespace";

    private static final Implementation IMPLEMENTATION =
            new Implementation(FontDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue FONT_VALIDATION =
            Issue.create(
                            "FontValidation",
                            "Validation of font files",
                            "Look for problems in various font files.",
                            Category.CORRECTNESS,
                            8,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/text/downloadable-fonts.html")
                    .setAliases(Arrays.asList("FontValidationWarning", "FontValidationError"));

    public static final Module APPSUPPORT_MODULE =
            new Module(SUPPORT_LIB_GROUP_ID, APPCOMPAT_LIB_ARTIFACT_ID);
    private static final Version MIN_APPSUPPORT_VERSION = Version.parse("26.0.0");

    private FontLoader mFontLoader;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.FONT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_FONT_FAMILY, TAG_FONT);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        if (mFontLoader == null) {
            mFontLoader = FontLoader.getInstance(context.getClient().getSdkHome());
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Attr authority = element.getAttributeNodeNS(ANDROID_URI, ATTR_FONT_PROVIDER_AUTHORITY);
        Attr query = element.getAttributeNodeNS(ANDROID_URI, ATTR_FONT_PROVIDER_QUERY);
        Attr androidPackage = element.getAttributeNodeNS(ANDROID_URI, ATTR_FONT_PROVIDER_PACKAGE);
        Attr certs = element.getAttributeNodeNS(ANDROID_URI, ATTR_FONT_PROVIDER_CERTS);

        Attr appAuthority = element.getAttributeNodeNS(AUTO_URI, ATTR_FONT_PROVIDER_AUTHORITY);
        Attr appQuery = element.getAttributeNodeNS(AUTO_URI, ATTR_FONT_PROVIDER_QUERY);
        Attr appPackage = element.getAttributeNodeNS(AUTO_URI, ATTR_FONT_PROVIDER_PACKAGE);
        Attr appCerts = element.getAttributeNodeNS(AUTO_URI, ATTR_FONT_PROVIDER_CERTS);

        Attr firstAndroidAttribute = coalesce(authority, query, androidPackage, certs);
        Attr firstAppAttribute = coalesce(appAuthority, appQuery, appPackage, appCerts);
        List<String> missingAndroidAttributes =
                findMissingAttributes(authority, query, androidPackage, certs);
        List<String> missingAppAttributes =
                findMissingAttributes(appAuthority, appQuery, appPackage, appCerts);

        Element fontTag = XmlUtils.getFirstSubTagByName(element, TAG_FONT);

        boolean downloadableFontFile = coalesce(firstAndroidAttribute, firstAppAttribute) != null;

        if (downloadableFontFile) {
            checkSupportLibraryVersion(context, element);
            if (reportMisplacedFontTag(context, fontTag)) {
                return;
            }
            reportUnexpectedNamespace(context, firstAndroidAttribute, firstAppAttribute);
            FontProvider provider = reportUnknownProvider(context, authority, appAuthority);
            if (provider != null) {
                reportUnknownPackage(context, androidPackage, appPackage, provider);
                reportQueryProblem(context, query, appQuery, provider);
            }
            reportMissingAttribute(
                    context,
                    firstAndroidAttribute,
                    firstAppAttribute,
                    missingAndroidAttributes,
                    missingAppAttributes,
                    provider);
        }
    }

    @NonNull
    private static List<String> findMissingAttributes(
            @Nullable Attr authority,
            @Nullable Attr query,
            @Nullable Attr packageName,
            @Nullable Attr certs) {
        if (authority != null && query != null && packageName != null && certs != null) {
            return Collections.emptyList();
        }
        List<String> missing = new ArrayList<>();
        if (authority == null) {
            missing.add(ATTR_FONT_PROVIDER_AUTHORITY);
        }
        if (query == null) {
            missing.add(ATTR_FONT_PROVIDER_QUERY);
        }
        if (packageName == null) {
            missing.add(ATTR_FONT_PROVIDER_PACKAGE);
        }
        if (certs == null) {
            missing.add(ATTR_FONT_PROVIDER_CERTS);
        }
        return missing;
    }

    private static void checkSupportLibraryVersion(
            @NonNull XmlContext context, @NonNull Element element) {
        LintModelVariant variant = context.getProject().getBuildVariant();
        if (variant == null) {
            return;
        }
        LintModelLibrary library =
                variant.getArtifact().findCompileDependency(APPCOMPAT_LIB_ARTIFACT);
        if (!(library instanceof LintModelExternalLibrary)) {
            return;
        }

        LintModelExternalLibrary extLibrary = (LintModelExternalLibrary) library;

        LintModelMavenName rc = extLibrary.getResolvedCoordinates();
        Version version = Version.parse(rc.getVersion());
        if (version.compareTo(MIN_APPSUPPORT_VERSION) < 0) {
            String message =
                    "Using version "
                            + version
                            + " of the "
                            + APPCOMPAT_LIB_ARTIFACT_ID
                            + " library. Required version for using downloadable fonts: "
                            + MIN_APPSUPPORT_VERSION
                            + " or higher.";
            LintFix fix = LintFix.create().data(KEY_ARTIFACT_ID, APPCOMPAT_LIB_ARTIFACT_ID);
            reportError(context, element, message, context.getNameLocation(element), fix);
        }
    }

    private static boolean reportMisplacedFontTag(
            @NonNull XmlContext context, @Nullable Element fontTag) {
        if (fontTag == null) {
            return false;
        }
        LintFix fix =
                LintFix.create().replace().with("").range(context.getLocation(fontTag)).build();
        reportError(
                context,
                fontTag,
                "A downloadable font cannot have a `<font>` sub tag",
                context.getElementLocation(fontTag),
                fix);
        return true;
    }

    private void reportUnexpectedNamespace(
            @NonNull XmlContext context,
            @Nullable Attr firstAndroidAttribute,
            @Nullable Attr firstAppAttribute) {
        if (context.getProject().getMinSdk()
                        < FUTURE_API_VERSION_WHERE_DOWNLOADABLE_FONTS_WORK_IN_FRAMEWORK
                && firstAndroidAttribute != null) {
            reportUnexpectedNamespace(context, firstAndroidAttribute, true);
        }
        //noinspection ConstantConditions
        if (FUTURE_API_VERSION_WHERE_DOWNLOADABLE_FONTS_WORK_IN_FRAMEWORK < Integer.MAX_VALUE - 1
                && firstAppAttribute != null) {
            reportUnexpectedNamespace(context, firstAppAttribute, false);
        }
    }

    private void reportUnexpectedNamespace(
            @NonNull XmlContext context, @NonNull Attr first, boolean appNamespace) {
        // Report conditionally. The minSdkVersion could be higher when merged in
        // from an app module.
        AndroidVersion minSdk = context.getProject().getMinSdkVersion();
        String message = createUnexpectedAttributeMessage(appNamespace, minSdk);
        LintFix fix = LintFix.create().unset(first.getNamespaceURI(), first.getLocalName()).build();
        Incident incident =
                new Incident(FONT_VALIDATION, message, context.getLocation(first), first, fix)
                        .overrideSeverity(Severity.WARNING);
        context.report(incident, map().put(KEY_UNEXPECTED_NAMESPACE, appNamespace));
    }

    @NonNull
    private static String createUnexpectedAttributeMessage(
            boolean appNamespace, AndroidVersion minSdk) {
        String formatString = "For `minSdkVersion`=%1$d only `%2$s:` attributes should be used";
        String prefix = appNamespace ? APP_PREFIX : ANDROID_NS_NAME;
        return String.format(Locale.US, formatString, minSdk.getApiLevel(), prefix);
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        if (map.containsKey(KEY_UNEXPECTED_NAMESPACE)) {
            // From reportUnexpectedNamespace
            boolean app = map.getBoolean(KEY_UNEXPECTED_NAMESPACE, false);
            AndroidVersion minSdk = context.getMainProject().getMinSdkVersion();
            if (minSdk.getApiLevel()
                    >= FUTURE_API_VERSION_WHERE_DOWNLOADABLE_FONTS_WORK_IN_FRAMEWORK) {
                if (app) {
                    return false;
                }
            } else if (!app) {
                return false;
            }
            String message = createUnexpectedAttributeMessage(app, minSdk);
            incident.setMessage(message);
            return true;
        }

        // From reportMissingAppAttribute
        if (map.containsKey(KEY_APP_NAMESPACE)) {
            boolean app = map.getBoolean(KEY_APP_NAMESPACE, false);
            AndroidVersion minSdk = context.getMainProject().getMinSdkVersion();
            if (minSdk.getFeatureLevel() <= AndroidVersion.VersionCodes.O_MR1) {
                // Flag any app: namespace attributes
                return app;
            } else {
                // Flag any android: namespace attributes
                return !app;
            }
        }
        return false;
    }

    private void reportMissingAttribute(
            @NonNull XmlContext context,
            @Nullable Attr firstAndroidAttribute,
            @Nullable Attr firstAppAttribute,
            @NonNull List<String> missingAndroidAttributes,
            @NonNull List<String> missingAppAttributes,
            @Nullable FontProvider provider) {
        AndroidVersion minSdk = context.getProject().getMinSdkVersion();
        // TODO: <= O_MR1 or < O_MR1?
        if (minSdk.getFeatureLevel() <= AndroidVersion.VersionCodes.O_MR1) {
            if (firstAppAttribute != null && !missingAppAttributes.isEmpty()) {
                reportMissingAppAttribute(
                        context, firstAppAttribute, missingAppAttributes, true, provider);
            }
        }
        if (firstAndroidAttribute != null && !missingAndroidAttributes.isEmpty()) {
            reportMissingAppAttribute(
                    context, firstAndroidAttribute, missingAndroidAttributes, false, provider);
        }
    }

    private void reportMissingAppAttribute(
            @NonNull XmlContext context,
            @NonNull Attr firstFontAttribute,
            @NonNull List<String> missingAttributes,
            boolean appNamespace,
            @Nullable FontProvider provider) {
        @NonNull String namespaceUri = appNamespace ? AUTO_URI : ANDROID_URI;
        @NonNull String namespacePrefix = appNamespace ? APP_PREFIX : ANDROID_NS_NAME;
        String message =
                String.format(
                        "Missing required %1$s: %2$s:%3$s",
                        StringUtil.pluralize("attribute", missingAttributes.size()),
                        namespacePrefix,
                        Joiner.on(", " + namespacePrefix + ":").join(missingAttributes));
        LintFix fix = makeMissingAttributeFix(missingAttributes, namespaceUri, provider);
        Element element = firstFontAttribute.getOwnerElement();
        Incident incident =
                new Incident(
                        FONT_VALIDATION,
                        element,
                        context.getElementLocation(element),
                        message,
                        fix);
        context.report(incident, map().put(KEY_APP_NAMESPACE, appNamespace));
    }

    private LintFix makeMissingAttributeFix(
            @NonNull List<String> missingAttributes,
            @NonNull String namespaceUri,
            @Nullable FontProvider provider) {
        if (provider == null) {
            provider = mFontLoader.findOnlyKnownProvider();
        }

        LintFix.GroupBuilder fix = fix().composite().name("Set missing attributes");
        for (String missingAttribute : missingAttributes) {
            String value = generateNewValue(missingAttribute, provider);
            if (value == null) {
                fix.add(fix().set().todo(namespaceUri, missingAttribute).build());
            } else {
                fix.add(fix().set(namespaceUri, missingAttribute, value).build());
            }
        }
        return fix.build();
    }

    @Nullable
    private static String generateNewValue(
            @NonNull String missingAttribute, @Nullable FontProvider provider) {
        if (provider == null) {
            return null;
        }
        switch (missingAttribute) {
            case ATTR_FONT_PROVIDER_AUTHORITY:
                return provider.getAuthority();
            case ATTR_FONT_PROVIDER_PACKAGE:
                return provider.getPackageName();
            case ATTR_FONT_PROVIDER_CERTS:
                return "@array/" + provider.getCertificateResourceName();
            default:
                return null;
        }
    }

    @Nullable
    private FontProvider reportUnknownProvider(
            @NonNull XmlContext context,
            @Nullable Attr attrAuthority,
            @Nullable Attr attrAppAuthority) {
        String authority = attrAuthority != null ? attrAuthority.getValue() : null;
        String appAuthority = attrAppAuthority != null ? attrAppAuthority.getValue() : null;
        FontProvider provider = null;
        if (authority != null) {
            provider = reportUnknownProvider(context, attrAuthority, authority);
        } else if (appAuthority != null) {
            provider = reportUnknownProvider(context, attrAppAuthority, appAuthority);
        }
        return provider;
    }

    private FontProvider reportUnknownProvider(
            @NonNull XmlContext context, @NonNull Attr attrAuthority, @NonNull String authority) {
        FontProvider provider = mFontLoader.findProvider(authority);
        if (provider != null) {
            return provider;
        }
        LintFix fix = null;
        FontProvider onlyKnownProvider = mFontLoader.findOnlyKnownProvider();
        if (onlyKnownProvider != null) {
            fix =
                    fix().name("Replace with " + onlyKnownProvider.getAuthority())
                            .replace()
                            .text(authority)
                            .with(onlyKnownProvider.getAuthority())
                            .build();
        }
        reportError(
                context,
                attrAuthority,
                "Unknown font provider authority",
                context.getValueLocation(attrAuthority),
                fix);
        return null;
    }

    private static void reportUnknownPackage(
            @NonNull XmlContext context,
            @Nullable Attr attrAndroidPackage,
            @Nullable Attr attrAppPackage,
            @NonNull FontProvider provider) {
        String androidPackage = attrAndroidPackage != null ? attrAndroidPackage.getValue() : null;
        String appPackage = attrAppPackage != null ? attrAppPackage.getValue() : null;
        if (androidPackage != null && !androidPackage.equals(provider.getPackageName())) {
            reportUnknownPackage(context, attrAndroidPackage, androidPackage, provider);
        } else if (appPackage != null && !appPackage.equals(provider.getPackageName())) {
            reportUnknownPackage(context, attrAppPackage, appPackage, provider);
        }
    }

    private static void reportUnknownPackage(
            @NonNull XmlContext context,
            @NonNull Attr attrPackage,
            @NonNull String packageName,
            @NonNull FontProvider provider) {
        if (provider.getPackageName().equals(packageName)) {
            return;
        }
        LintFix fix =
                LintFix.create()
                        .name("Replace with " + provider.getPackageName())
                        .replace()
                        .text(packageName)
                        .with(provider.getPackageName())
                        .build();
        reportError(
                context,
                attrPackage,
                "Unexpected font provider package",
                context.getValueLocation(attrPackage),
                fix);
    }

    private void reportQueryProblem(
            @NonNull XmlContext context,
            @Nullable Attr androidQueryAttr,
            @Nullable Attr appQueryAttr,
            @NonNull FontProvider provider) {
        String androidQuery = androidQueryAttr != null ? androidQueryAttr.getValue() : null;
        String appQuery = appQueryAttr != null ? appQueryAttr.getValue() : null;
        if (androidQuery != null) {
            reportQueryProblem(context, androidQueryAttr, androidQuery, provider);
        } else if (appQuery != null) {
            reportQueryProblem(context, appQueryAttr, appQuery, provider);
        }
    }

    private void reportQueryProblem(
            @NonNull XmlContext context,
            @NonNull Attr queryAttr,
            @NonNull String query,
            @NonNull FontProvider provider) {
        if (query.isEmpty()) {
            LintFix fix =
                    fix().set().todo(queryAttr.getNamespaceURI(), queryAttr.getLocalName()).build();
            reportError(
                    context,
                    queryAttr,
                    "Missing provider query",
                    context.getLocation(queryAttr),
                    fix);
            return;
        }
        try {
            DownloadableParseResult result =
                    QueryResolver.parseDownloadableFont(
                            provider.getAuthority(), XmlUtils.fromXmlAttributeValue(query));
            if (!mFontLoader.fontsLoaded()) {
                return;
            }
            for (String fontName : result.getFonts().keySet()) {
                FontFamily family = mFontLoader.findFont(provider, fontName);
                if (family == null) {
                    reportError(
                            context,
                            queryAttr,
                            "Unknown font: " + fontName,
                            context.getValueLocation(queryAttr),
                            null);
                } else {
                    for (MutableFontDetail detail : result.getFonts().get(fontName)) {
                        FontDetail best = detail.findBestMatch(family.getFonts());
                        if (best != null && detail.match(best) != 0) {
                            LintFix fix = null;
                            if (result.getFonts().size() == 1) {
                                if (!detail.getExact()) {
                                  // Propagate best effort
                                  MutableFontDetail builder = best.toMutableFontDetail();
                                  builder.setExact(false);
                                  best = new FontDetail(best.getFamily(), builder);
                                }
                                boolean isV11 = query.startsWith("name=");
                                String better = isV11 ? best.generateQueryV11() : best.generateQueryV12();

                                fix =
                                        fix().name("Replace with closest font: " + better)
                                                .set(
                                                        queryAttr.getNamespaceURI(),
                                                        queryAttr.getLocalName(),
                                                        better)
                                                .build();
                            }
                            if (detail.getExact()) {
                                reportError(
                                        context,
                                        queryAttr,
                                        "No exact match found for: " + fontName,
                                        context.getValueLocation(queryAttr),
                                        fix);
                            } else {
                                reportWarning(
                                        context,
                                        queryAttr,
                                        "No exact match found for: " + fontName,
                                        context.getValueLocation(queryAttr),
                                        fix);
                            }
                        }
                    }
                }
            }
        } catch (FontQueryParserError ex) {
            reportError(
                    context, queryAttr, ex.getMessage(), context.getValueLocation(queryAttr), null);
        }
    }

    private static void reportError(
            @NonNull XmlContext context,
            @NonNull Node node,
            @NonNull String message,
            @NonNull Location location,
            @Nullable LintFix fix) {
        Incident incident =
                new Incident(FONT_VALIDATION, node, location, message, fix)
                        .overrideSeverity(Severity.ERROR);
        context.report(incident);
    }

    private static void reportWarning(
            @NonNull XmlContext context,
            @NonNull Node node,
            @NonNull String message,
            @NonNull Location location,
            @Nullable LintFix fix) {
        Incident incident =
                new Incident(FONT_VALIDATION, node, location, message, fix)
                        .overrideSeverity(Severity.WARNING);
        context.report(incident);
    }
}
