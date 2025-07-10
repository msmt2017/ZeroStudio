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

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.LocationType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kotlin.text.StringsKt;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Check which makes sure that a network-security-config descriptor file is valid and logical. */
public class NetworkSecurityConfigDetector extends ResourceXmlDetector {

    public static final Implementation IMPLEMENTATION =
            new Implementation(NetworkSecurityConfigDetector.class, Scope.RESOURCE_FILE_SCOPE);

    /** Validate the entire network-security-config descriptor. */
    public static final Issue ISSUE =
            Issue.create(
                            "NetworkSecurityConfig",
                            "Valid Network Security Config File",
                            "Ensures that a `<network-security-config>` file, which is pointed to by an "
                                    + "`android:networkSecurityConfig` attribute in the manifest file, is valid",
                            Category.CORRECTNESS,
                            5,
                            Severity.FATAL,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/preview/features/security-config.html");

    /** Validate the pin-set expiration attribute and warn if the expiry is in the near future. */
    public static final Issue PIN_SET_EXPIRY =
            Issue.create(
                            "PinSetExpiry",
                            "Validate `<pin-set>` expiration attribute",
                            "Ensures that the `expiration` attribute of the `<pin-set>` element is valid and has "
                                    + "not already expired or is expiring soon",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/preview/features/security-config.html");

    /** No backup pin specified. */
    public static final Issue MISSING_BACKUP_PIN =
            Issue.create(
                            "MissingBackupPin",
                            "Missing Backup Pin",
                            "It is highly recommended to declare a backup `<pin>` element. "
                                    + "Not having a second pin defined can cause connection failures when the "
                                    + "particular site certificate is rotated and the app has not yet been updated.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/preview/features/security-config.html");

    /** Base configuration allows cleartext by default. */
    public static final Issue INSECURE_CONFIGURATION =
            Issue.create(
                            "InsecureBaseConfiguration",
                            "Insecure Base Configuration",
                            "Permitting cleartext traffic could allow eavesdroppers to intercept data sent "
                                    + "by your app, which impacts the privacy of your users. Consider only allowing "
                                    + "encrypted traffic by setting the `cleartextTrafficPermitted` tag to `false`.",
                            Category.SECURITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://goo.gle/InsecureBaseConfiguration")
                    .addMoreInfo(
                            "https://developer.android.com/preview/features/security-config.html");

    /** Allows user-provided certificates to validate of secure connections. */
    public static final Issue ACCEPTS_USER_CERTIFICATES =
            Issue.create(
                            "AcceptsUserCertificates",
                            "Allowing User Certificates",
                            "Allowing user certificates could allow eavesdroppers to intercept data sent by your app, "
                                    + "which could impact the privacy of your users. Consider nesting your app's "
                                    + "`trust-anchors` inside a `<debug-overrides>` element to make sure they are only "
                                    + "available when `android:debuggable` is set to `true`.",
                            Category.SECURITY,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://goo.gle/AcceptsUserCertificates")
                    .addMoreInfo(
                            "https://developer.android.com/training/articles/security-config#TrustingDebugCa");

    public static final String ATTR_DIGEST = "digest";

    private static final String TAG_NETWORK_SECURITY_CONFIG = "network-security-config";
    private static final String TAG_BASE_CONFIG = "base-config";
    private static final String TAG_DOMAIN_CONFIG = "domain-config";
    private static final String TAG_DEBUG_OVERRIDES = "debug-overrides";
    private static final String TAG_DOMAIN = "domain";
    private static final String TAG_PIN_SET = "pin-set";
    private static final String TAG_TRUST_ANCHORS = "trust-anchors";
    private static final String TAG_CERTIFICATES = "certificates";
    private static final String TAG_PIN = "pin";

    private static final String ATTR_SRC = "src";
    private static final String ATTR_INCLUDE_SUBDOMAINS = "includeSubdomains";
    private static final String ATTR_EXPIRATION = "expiration";
    private static final String ATTR_CLEARTEXT_TRAFFIC_PERMITTED = "cleartextTrafficPermitted";

    private static final String PIN_DIGEST_ALGORITHM = "SHA-256";
    // SHA 256 bit = 32 bytes
    private static final int PIN_DECODED_DIGEST_LEN_SHA_256 = 32;
    private static final Set<String> VALID_CONFIG_TAGS =
            ImmutableSet.of(TAG_DOMAIN, TAG_TRUST_ANCHORS, TAG_PIN_SET, TAG_DOMAIN_CONFIG);
    public static final Set<String> VALID_BASE_TAGS =
            ImmutableSet.of(TAG_DOMAIN_CONFIG, TAG_BASE_CONFIG, TAG_DEBUG_OVERRIDES);
    private static final String UNEXPECTED_ELEMENT_MESSAGE = "Unexpected element `<%1$s>`";
    private static final String ALREADY_DECLARED_MESSAGE = "Already declared here";

    /** Constructs a new {@link NetworkSecurityConfigDetector} */
    public NetworkSecurityConfigDetector() {}

    /**
     * Keep track of whether the debug-overrides element was seen in one of the
     * network-security-config files.
     *
     * <p>Context: When an app is debuggable, a file named $config_resource$_debug.xml is also
     * looked up by framework to check for debug overrides.
     */
    private Location.Handle mDebugOverridesHandle;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.XML;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mDebugOverridesHandle = null;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return;
        }
        if (!TAG_NETWORK_SECURITY_CONFIG.equals(root.getTagName())) {
            return;
        }

        Location.Handle baseConfigHandle = null;
        Map<String, Node> seenDomains2Nodes = Maps.newHashMap();

        // 0 or 1 of <base-config>
        // Any number of <domain-config>
        // 0 or 1 of <debug-overrides>
        for (Element child : XmlUtils.getSubTags(root)) {
            String tagName = child.getTagName();
            if (TAG_BASE_CONFIG.equals(tagName)) {
                if (baseConfigHandle != null) {
                    reportExceeded(context, TAG_BASE_CONFIG, child, baseConfigHandle);
                } else {
                    baseConfigHandle = context.createLocationHandle(child);
                    handleConfigElement(context, child, seenDomains2Nodes);
                    handleBaseConfigElement(context, child);
                }
            } else if (TAG_DEBUG_OVERRIDES.equals(tagName)) {
                if (mDebugOverridesHandle != null) {
                    reportExceeded(context, TAG_DEBUG_OVERRIDES, child, mDebugOverridesHandle);
                } else {
                    mDebugOverridesHandle = context.createLocationHandle(child);
                    handleConfigElement(context, child, seenDomains2Nodes);
                }
            } else if (TAG_DOMAIN_CONFIG.equals(tagName)) {
                handleConfigElement(context, child, seenDomains2Nodes);
            } else {
                // It's possible to check for only the tags that can appear
                // by looking at `seenBaseConfig` and `seenDebugOverrides` but that may
                // be unnecessary. We can let the developer first fix the spelling
                // and then revalidate the values to check for duplicates (according to rules).
                if (!checkForTyposInTags(context, child, VALID_BASE_TAGS)) {
                    context.report(
                            ISSUE,
                            child,
                            context.getNameLocation(child),
                            String.format(UNEXPECTED_ELEMENT_MESSAGE, tagName));
                }
            }
        }
    }

    private void handleConfigElement(
            XmlContext context, Element config, @NonNull Map<String, Node> seenDomainsToLocations) {
        String configName = config.getTagName();
        boolean isDomainConfig = TAG_DOMAIN_CONFIG.equals(configName);
        String message = "`%1$s` element not allowed in `%2$s`";
        // Assumption: Multiple trust-anchors and pinSetNode elements are not allowed within
        // a single domain-config. Nested domain-config elements can still have them.
        Node trustAnchorsNode = null;
        Node pinSetNode = null;

        checkForTyposInAttributes(context, config, ATTR_CLEARTEXT_TRAFFIC_PERMITTED, false);

        for (Element node : XmlUtils.getSubTags(config)) {
            String tagName = node.getTagName();
            if (TAG_DOMAIN.equals(tagName)) {
                if (!isDomainConfig) {
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            String.format(message, TAG_DOMAIN, configName));
                } else {
                    checkForTyposInAttributes(context, node, ATTR_INCLUDE_SUBDOMAINS, true);
                    String domainName = node.getTextContent().trim().toLowerCase(Locale.US);
                    if (seenDomainsToLocations.containsKey(domainName)) {
                        String duplicateMessage = "Duplicate domain names are not allowed";
                        Node previousNode = seenDomainsToLocations.get(domainName);
                        context.report(
                                ISSUE,
                                node.getFirstChild(),
                                context.getLocation(node.getFirstChild())
                                        .withSecondary(
                                                context.getLocation(previousNode),
                                                ALREADY_DECLARED_MESSAGE),
                                duplicateMessage);
                    } else {
                        seenDomainsToLocations.put(domainName, node.getFirstChild());
                    }
                }
            } else if (TAG_TRUST_ANCHORS.equals(tagName)) {
                if (trustAnchorsNode != null) {
                    String anchorMessage = "Multiple `<trust-anchors>` elements are not allowed";
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node)
                                    .withSecondary(
                                            context.getNameLocation(trustAnchorsNode),
                                            ALREADY_DECLARED_MESSAGE),
                            anchorMessage);
                } else {
                    trustAnchorsNode = node;
                    handleTrustAnchors(context, node);
                }
            } else if (TAG_DOMAIN_CONFIG.equals(tagName)) {
                if (!isDomainConfig) {
                    // If the parent is any config other than a domain-config report an error
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            String.format(
                                    "Nested `<domain-config>` elements are not allowed in `%1$s`",
                                    configName));
                } else {
                    handleConfigElement(context, node, seenDomainsToLocations);
                }
            } else if (TAG_PIN_SET.equals(tagName)) {
                if (!isDomainConfig) {
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node),
                            String.format(message, TAG_PIN_SET, configName));
                }
                if (pinSetNode != null) {
                    String pinSetMessage = "Multiple `<pin-set>` elements are not allowed";
                    context.report(
                            ISSUE,
                            node,
                            context.getNameLocation(node)
                                    .withSecondary(
                                            context.getNameLocation(pinSetNode),
                                            ALREADY_DECLARED_MESSAGE),
                            pinSetMessage);
                } else {
                    pinSetNode = node;
                    handlePinSet(context, node);
                }
            } else {
                // Note: Only typos are marked as errors here to be forward compatible
                // where new elements are added here.
                checkForTyposInTags(context, node, VALID_CONFIG_TAGS);
            }
        }

        if (isDomainConfig && seenDomainsToLocations.isEmpty()) {
            context.report(
                    ISSUE,
                    config,
                    context.getNameLocation(config),
                    "No `<domain>` elements in `<domain-config>`");
        }
    }

    /**
     * The following checks happen in addition to the {@link #handleConfigElement(XmlContext,
     * Element, Map)} checks, but are specific to the {@code <base-config>} element.
     */
    private void handleBaseConfigElement(XmlContext context, Element node) {
        if (node.hasAttribute(ATTR_CLEARTEXT_TRAFFIC_PERMITTED)) {
            Attr cleartextTrafficAttribute =
                    node.getAttributeNode(ATTR_CLEARTEXT_TRAFFIC_PERMITTED);
            if (cleartextTrafficAttribute.getValue().equals("true")) {
                Location attributeLocation = context.getValueLocation(cleartextTrafficAttribute);
                LintFix fix = fix().replace().range(attributeLocation).with("false").build();
                context.report(
                        INSECURE_CONFIGURATION,
                        node,
                        attributeLocation,
                        "Insecure Base Configuration",
                        fix);
            }
        }

        for (Element child : XmlUtils.getSubTags(node)) {
            if (TAG_TRUST_ANCHORS.equals(child.getTagName())) {
                for (Element grandchild : XmlUtils.getSubTags(child)) {
                    if (TAG_CERTIFICATES.equals(grandchild.getTagName())) {
                        Attr sourceIdAttr = grandchild.getAttributeNode(ATTR_SRC);
                        if (sourceIdAttr != null && "user".equals(sourceIdAttr.getValue())) {
                            context.report(
                                    ACCEPTS_USER_CERTIFICATES,
                                    grandchild,
                                    context.getLocation(grandchild),
                                    "The Network Security Configuration allows the use of user certificates in the release version of your app");
                        }
                    }
                }
            }
        }
    }

    private static void handlePinSet(XmlContext context, Element node) {

        if (node.hasAttribute(ATTR_EXPIRATION)) {
            Attr expirationAttr = node.getAttributeNode(ATTR_EXPIRATION);
            String message = null;
            try {
                LocalDate date =
                        LocalDate.parse(
                                expirationAttr.getValue(), DateTimeFormatter.ISO_LOCAL_DATE);
                // If the pin-set has already expired report a warning.
                LocalDate now = LocalDate.now();
                if (date.isBefore(now)) {
                    message = "`pin-set` has already expired";
                } else if (date.isBefore(now.plusDays(10))) {
                    // OR if the pin-set will expire within 10 days from now
                    message = "`pin-set` is expiring soon";
                }
            } catch (DateTimeParseException e) {
                context.report(
                        ISSUE,
                        expirationAttr,
                        context.getValueLocation(expirationAttr),
                        "Invalid expiration in `pin-set`");
            }

            if (message != null) {
                context.report(
                        PIN_SET_EXPIRY,
                        expirationAttr,
                        context.getValueLocation(expirationAttr),
                        message);
            }
        } else {
            checkForTyposInAttributes(context, node, ATTR_EXPIRATION, false);
        }

        int pinElementCount = 0;
        boolean foundTyposInPin = false;
        for (Element child : XmlUtils.getSubTags(node)) {
            String tagName = child.getTagName();
            if (TAG_PIN.equals(tagName)) {
                pinElementCount += 1;
                if (child.hasAttribute(ATTR_DIGEST)) {
                    Attr digestAttr = child.getAttributeNode(ATTR_DIGEST);
                    if (!PIN_DIGEST_ALGORITHM.equalsIgnoreCase(digestAttr.getValue())) {
                        String values = Lint.formatList(getSupportedPinDigestAlgorithms(), 2);
                        LintFix.GroupBuilder fixBuilder = LintFix.create().group();
                        for (String algorithm : getSupportedPinDigestAlgorithms()) {
                            fixBuilder.add(
                                    LintFix.create()
                                            .name(
                                                    String.format(
                                                            "Set digest to \"%1$s\"", algorithm))
                                            .replace()
                                            .all()
                                            .with(algorithm)
                                            .build());
                        }
                        LintFix fix = fixBuilder.build();

                        context.report(
                                ISSUE,
                                digestAttr,
                                context.getValueLocation(digestAttr),
                                String.format(
                                        "Invalid digest algorithm. Supported digests: `%1$s`",
                                        values),
                                fix);
                    }
                } else {
                    checkForTyposInAttributes(context, child, ATTR_DIGEST, true);
                }
                Node digestNode;
                if (!child.hasChildNodes()
                        || (digestNode = child.getFirstChild()) == null
                        || digestNode.getNodeType() != Node.TEXT_NODE) {
                    // missing text node
                    context.report(ISSUE, child, context.getLocation(child), "Missing pin digest");
                } else {
                    try {
                        // Validate the actual data
                        byte[] decodedDigest =
                                Base64.getDecoder().decode(digestNode.getNodeValue());
                        if (decodedDigest.length != PIN_DECODED_DIGEST_LEN_SHA_256) {
                            // incorrect digest length
                            String message =
                                    String.format(
                                            Locale.getDefault(),
                                            "Decoded digest length `%1$d` does not match expected "
                                                    + "length for `%2$s` of `%3$d`",
                                            decodedDigest.length,
                                            PIN_DIGEST_ALGORITHM,
                                            PIN_DECODED_DIGEST_LEN_SHA_256);
                            context.report(
                                    ISSUE, digestNode, context.getLocation(digestNode), message);
                        }
                    } catch (Exception ex) {
                        context.report(
                                ISSUE,
                                digestNode,
                                context.getLocation(digestNode),
                                "Invalid pin digest");
                    }
                }
            } else {
                foundTyposInPin |=
                        checkForTyposInTags(context, child, Collections.singleton(TAG_PIN));
            }
        }
        // Let the developer fix the typos before we can ascertain that the pin is missing
        if (!foundTyposInPin) {
            if (pinElementCount == 0) {
                context.report(
                        ISSUE, node, context.getNameLocation(node), "Missing `<pin>` element(s)");
            } else if (pinElementCount == 1) {
                // We should probably check to see if both hashes are the same here.
                context.report(
                        MISSING_BACKUP_PIN,
                        node,
                        context.getNameLocation(node),
                        "A backup `<pin>` declaration is highly recommended");
            }
        }
    }

    private static void handleTrustAnchors(XmlContext context, Element node) {
        for (Element child : XmlUtils.getSubTags(node)) {
            if (TAG_CERTIFICATES.equals(child.getTagName())) {
                if (!child.hasAttribute(ATTR_SRC)) {
                    checkForTyposInAttributes(context, child, ATTR_SRC, true);
                } else {
                    Attr sourceIdAttr = child.getAttributeNode(ATTR_SRC);
                    String sourceId = sourceIdAttr.getValue();
                    ResourceUrl resourceUrl = ResourceUrl.parse(sourceId);
                    // The value should be either "system", "user" or a resource Id
                    if (resourceUrl == null
                            && !"user".equals(sourceId)
                            && !"system".equals(sourceId)) {
                        context.report(
                                ISSUE,
                                sourceIdAttr,
                                context.getValueLocation(sourceIdAttr),
                                "Unknown certificates `src` attribute. "
                                        + "Expecting `system`, `user` or an @resource value");
                    }
                }
            } else {
                checkForTyposInTags(context, child, Collections.singleton(TAG_CERTIFICATES));
            }
        }
    }

    private static boolean checkForTyposInTags(
            XmlContext context, Element node, Collection<String> validPossibleTags) {
        String tagName = node.getTagName();
        List<String> suggestions = generateTypoSuggestions(tagName, validPossibleTags);
        if (suggestions != null) {
            assert !suggestions.isEmpty();
            String suggestionString;
            if (suggestions.size() == 1) {
                suggestionString = suggestions.get(0);
            } else if (suggestions.size() == 2) {
                suggestionString =
                        String.format("%1$s or %2$s", suggestions.get(0), suggestions.get(1));
            } else {
                suggestionString = Lint.formatList(suggestions, -1);
            }
            String message =
                    String.format(
                            "Misspelled tag `<%1$s>`: Did you mean `%2$s`?",
                            tagName, suggestionString);

            List<LintFix> fixes = new ArrayList<>();
            for (String suggestion : suggestions) {
                fixes.add(
                        LintFix.create()
                                .renameTag(
                                        tagName,
                                        suggestion,
                                        context.getLocation(node, LocationType.ALL))
                                .build());
            }
            LintFix fix = LintFix.create().alternatives(fixes.toArray(new LintFix[0]));
            context.report(ISSUE, node, context.getNameLocation(node), message, fix);
            return true;
        }
        return false;
    }

    private static void checkForTyposInAttributes(
            XmlContext context, Element node, String attrName, boolean requiredAttribute) {
        if (node.hasAttribute(attrName)) {
            return;
        }
        List<String> suggestions = null;
        NamedNodeMap attributes = node.getAttributes();
        boolean foundSpellingError = false;
        Set<String> validAttributeNames = Collections.singleton(attrName);
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();
            if (!StringsKt.isBlank(nodeName)) {
                suggestions = generateTypoSuggestions(nodeName, validAttributeNames);
            }
            if (suggestions != null && suggestions.size() == 1) {
                String suggestion = suggestions.get(0);
                String nodeValue = attr.getNodeValue();
                LintFix fix =
                        LintFix.create()
                                .replaceAttribute((Attr) attr, null, suggestion, nodeValue)
                                .name("Replace with `" + suggestion + "`")
                                .build();
                context.report(
                        ISSUE,
                        attr,
                        context.getNameLocation(attr),
                        String.format(
                                "Misspelled attribute `%1$s`: Did you mean `%2$s`?",
                                nodeName, attrName),
                        fix);
                foundSpellingError = true;
            }
        }
        if (!foundSpellingError && requiredAttribute) {
            LintFix fix = LintFix.create().set().todo(null, attrName).build();
            context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    String.format("Missing `%1$s` attribute", attrName),
                    fix);
        }
    }

    private static List<String> generateTypoSuggestions(
            @NonNull String name, @NonNull Collection<String> validAttributeNames) {
        List<String> suggestions = null;
        for (String suggestion : validAttributeNames) {
            if (Lint.isEditableTo(suggestion, name, 3)) {
                if (suggestions == null) {
                    suggestions = new ArrayList<>(validAttributeNames.size());
                }
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    private static void reportExceeded(
            XmlContext context,
            String elementName,
            Element element,
            @NonNull Location.Handle handle) {
        context.report(
                ISSUE,
                element,
                context.getNameLocation(element)
                        .withSecondary(handle.resolve(), ALREADY_DECLARED_MESSAGE),
                String.format("Expecting at most 1 `<%1$s>`", elementName));
    }

    /**
     * Used by the IDE for quick fixes.
     *
     * @return supported pin digest algorithms
     */
    public static List<String> getSupportedPinDigestAlgorithms() {
        return Collections.singletonList(PIN_DIGEST_ALGORITHM);
    }
}
