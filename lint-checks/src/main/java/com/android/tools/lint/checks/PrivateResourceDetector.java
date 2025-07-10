/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_REF_PREFIX;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_INTEGER_ARRAY;
import static com.android.SdkConstants.TAG_PLURALS;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.SdkConstants.TAG_STRING_ARRAY;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.ide.common.resources.ResourcesUtil.resourceNameToFieldName;
import static com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES;
import static com.android.tools.lint.detector.api.Lint.getBaseName;
import static com.android.tools.lint.detector.api.Lint.isXmlFile;
import static com.android.utils.SdkUtils.isBitmapFile;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.repository.ResourceVisibilityLookup;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiPackage;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Check which looks for access of private resources. */
public class PrivateResourceDetector extends ResourceXmlDetector implements SourceCodeScanner {
    /** Attribute for overriding a resource */
    private static final String ATTR_OVERRIDE = "override";

    private static final String KEY_URL = "url";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    PrivateResourceDetector.class,
                    Scope.JAVA_AND_RESOURCE_FILES,
                    Scope.JAVA_FILE_SCOPE,
                    Scope.RESOURCE_FILE_SCOPE);

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "PrivateResource",
                    "Using private resources",
                    "Private resources should not be referenced; the may not be present everywhere, and "
                            + "even where they are they may disappear without notice.\n"
                            + "\n"
                            + "To fix this, copy the resource into your own project instead.",
                    Category.CORRECTNESS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** List of resource URLs overriding private resources locally */
    private List<String> overriding;

    /** Constructs a new detector */
    public PrivateResourceDetector() {}

    // ---- implements SourceCodeScanner ----

    @Override
    public boolean appliesToResourceRefs() {
        return true;
    }

    @Override
    public void visitResourceReference(
            @NonNull JavaContext context,
            @NonNull UElement node,
            @NonNull ResourceType resourceType,
            @NonNull String name,
            boolean isFramework) {
        if (!isFramework && isPrivate(context, resourceType, name)) {
            // See if it's a local package reference
            boolean foreignPackage = false;
            boolean globalAnalysis = context.isGlobalAnalysis();
            if (node instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) node).resolve();
                if (resolved instanceof PsiField) {
                    PsiPackage pkg = context.getEvaluator().getPackage(resolved);
                    if (pkg != null) {
                        String pkgName = pkg.getQualifiedName();
                        if (!(pkgName.equals(context.getProject().getPackage())
                                || globalAnalysis
                                        && pkgName.equals(context.getMainProject().getPackage()))) {
                            foreignPackage = true;
                        }
                    }
                }
            }

            // See if this is resource we're overriding locally
            if (!foreignPackage) {
                if (globalAnalysis && referencedInMain(context, resourceType, name)) {
                    return;
                }

                if (overriding != null && overriding.contains(resourceType + ":" + name)) {
                    return;
                }
            }

            String message = createUsageErrorMessage(context, resourceType, name);
            Incident incident = new Incident(ISSUE, node, context.getLocation(node), message);
            if (globalAnalysis) {
                context.report(incident);
            } else {
                context.report(incident, map().put(KEY_URL, "@" + resourceType + "/" + name));
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        String urlString = map.getString(KEY_URL, null);
        if (urlString != null) {
            ResourceUrl url = ResourceUrl.parse(urlString);
            return url != null && !referencedInMain(context, url.type, url.name);
        }

        return false;
    }

    private static boolean referencedInMain(
            @NonNull Context context, @NonNull ResourceType resourceType, @NonNull String name) {
        LintClient client = context.getClient();
        Project mainProject = context.getMainProject();
        ResourceRepository repository = client.getResources(mainProject, LOCAL_DEPENDENCIES);
        return repository.hasResources(ResourceNamespace.TODO(), resourceType, name);
    }

    // ---- Implements XmlScanner ----

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    /** Check resource references: accessing a private resource from an upstream library? */
    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getNodeValue();
        ResourceUrl url = ResourceUrl.parse(value);
        if (isPrivate(context, url)) {
            String message = createUsageErrorMessage(context, url.type, url.name);
            context.report(ISSUE, attribute, context.getValueLocation(attribute), message);
        }
    }

    /** Check resource definitions: overriding a private resource from an upstream library? */
    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                TAG_STYLE,
                TAG_RESOURCES,
                TAG_ARRAY,
                TAG_STRING_ARRAY,
                TAG_INTEGER_ARRAY,
                TAG_PLURALS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (TAG_RESOURCES.equals(element.getTagName())) {
            for (Element item : XmlUtils.getSubTags(element)) {
                Attr nameAttribute = item.getAttributeNode(ATTR_NAME);
                if (nameAttribute != null) {
                    String name = resourceNameToFieldName(nameAttribute.getValue());
                    ResourceType resourceType = ResourceType.fromXmlTag(item);
                    if (resourceType != null && isPrivate(context, resourceType, name)) {
                        if (overriding == null) {
                            overriding = Lists.newArrayList();
                        }
                        overriding.add(resourceType.getName() + ":" + name);

                        if (!VALUE_TRUE.equals(item.getAttributeNS(TOOLS_URI, ATTR_OVERRIDE))) {
                            String message =
                                    createOverrideErrorMessage(context, resourceType, name);
                            Location location = context.getValueLocation(nameAttribute);
                            context.report(ISSUE, nameAttribute, location, message);
                        }
                    }
                }
            }
        } else {
            assert TAG_STYLE.equals(element.getTagName())
                    || TAG_ARRAY.equals(element.getTagName())
                    || TAG_PLURALS.equals(element.getTagName())
                    || TAG_INTEGER_ARRAY.equals(element.getTagName())
                    || TAG_STRING_ARRAY.equals(element.getTagName());
            for (Element item : XmlUtils.getSubTags(element)) {
                checkChildRefs(context, item);
            }
        }
    }

    private static boolean isPrivate(Context context, ResourceType type, String name) {
        if (type == ResourceType.ID) {
            // No need to complain about "overriding" id's. There's no harm
            // in doing so. (This avoids warning about cases like for example
            // appcompat's (private) @id/title resource, which would otherwise
            // flag any attempt to create a resource named title in the user's
            // project.
            return false;
        }

        ResourceVisibilityLookup lookup = context.getProject().getResourceVisibility();
        return lookup.isPrivate(type, name);
    }

    private static boolean isPrivate(@NonNull Context context, @Nullable ResourceUrl url) {
        return url != null && !url.isFramework() && isPrivate(context, url.type, url.name);
    }

    private static void checkChildRefs(@NonNull XmlContext context, Element item) {
        // Look for ?attr/ and @dimen/foo etc references in the item children
        NodeList childNodes = item.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getNodeValue();

                int index = text.indexOf(ATTR_REF_PREFIX);
                if (index != -1) {
                    String name = text.substring(index + ATTR_REF_PREFIX.length()).trim();
                    if (isPrivate(context, ResourceType.ATTR, name)) {
                        String message = createUsageErrorMessage(context, ResourceType.ATTR, name);
                        context.report(ISSUE, item, context.getLocation(child), message);
                    }
                } else {
                    for (int j = 0, m = text.length(); j < m; j++) {
                        char c = text.charAt(j);
                        if (c == '@') {
                            ResourceUrl url = ResourceUrl.parse(text.trim());
                            if (isPrivate(context, url)) {
                                String message =
                                        createUsageErrorMessage(context, url.type, url.name);
                                context.report(ISSUE, item, context.getLocation(child), message);
                            }
                            break;
                        } else if (!Character.isWhitespace(c)) {
                            break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File file = context.file;
        if (!isXmlFile(file) && !isBitmapFile(file)) {
            return;
        }
        String parentName = file.getParentFile().getName();
        int dash = parentName.indexOf('-');
        if (dash != -1 || FD_RES_VALUES.equals(parentName)) {
            return;
        }
        ResourceFolderType folderType = ResourceFolderType.getFolderType(parentName);
        if (folderType == null) {
            return;
        }
        List<ResourceType> types = FolderTypeRelationship.getRelatedResourceTypes(folderType);
        if (types.isEmpty()) {
            return;
        }
        ResourceType type = types.get(0);
        String resourceName = resourceNameToFieldName(getBaseName(file.getName()));
        if (isPrivate(context, type, resourceName)) {
            String message = createOverrideErrorMessage(context, type, resourceName);
            Location location = Location.create(file);
            context.report(ISSUE, location, message);
        }
    }

    private static String createOverrideErrorMessage(
            @NonNull Context context, @NonNull ResourceType type, @NonNull String name) {
        String libraryName = getLibraryName(context, type, name);
        return String.format(
                "Overriding `@%1$s/%2$s` which is marked as private in %3$s. If "
                        + "deliberate, use tools:override=\"true\", otherwise pick a "
                        + "different name.",
                type, name, libraryName);
    }

    private static String createUsageErrorMessage(
            @NonNull Context context, @NonNull ResourceType type, @NonNull String name) {
        String libraryName = getLibraryName(context, type, name);
        return String.format(
                "The resource `@%1$s/%2$s` is marked as private in %3$s", type, name, libraryName);
    }

    /** Pick a suitable name to describe the library defining the private resource */
    @NonNull
    private static String getLibraryName(
            @NonNull Context context, @NonNull ResourceType type, @NonNull String name) {
        ResourceVisibilityLookup lookup = context.getProject().getResourceVisibility();
        String library = lookup.getPrivateIn(type, name);
        if (library != null) {
            return library;
        } else {
            return "the library";
        }
    }
}
