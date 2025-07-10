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
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.ide.common.resources.ResourcesUtil.resourceNameToFieldName;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This detector identifies cases where a resource is defined multiple times in the same resource
 * folder
 */
public class DuplicateResourceDetector extends ResourceXmlDetector {

    /** The main issue discovered by this detector */
    @SuppressWarnings("unchecked")
    public static final Issue ISSUE =
            Issue.create(
                    "DuplicateDefinition",
                    "Duplicate definitions of resources",
                    "You can define a resource multiple times in different resource folders; that's how "
                            + "string translations are done, for example. However, defining the same resource "
                            + "more than once in the same resource folder is likely an error, for example "
                            + "attempting to add a new resource without realizing that the name is already used, "
                            + "and so on.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            DuplicateResourceDetector.class,
                            // We should be able to do this incrementally!
                            Scope.ALL_RESOURCES_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    public static final Implementation IMPLEMENTATION_XML =
            new Implementation(DuplicateResourceDetector.class, Scope.RESOURCE_FILE_SCOPE);

    /** Wrong resource value type */
    public static final Issue TYPE_MISMATCH =
            Issue.create(
                    "ReferenceType",
                    "Incorrect reference types",
                    "When you generate a resource alias, the resource you are pointing to must be "
                            + "of the same type as the alias",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    IMPLEMENTATION_XML);

    private static final String PRODUCT = "product";
    private Map<ResourceType, Set<String>> mTypeMap;
    private Map<ResourceType, List<Pair<String, Location.Handle>>> mLocations;
    private File mParent;

    /** Constructs a new {@link DuplicateResourceDetector} */
    public DuplicateResourceDetector() {}

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_NAME);
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        File parent = context.file.getParentFile();
        if (!parent.equals(mParent)) {
            mParent = parent;
            mTypeMap = Maps.newEnumMap(ResourceType.class);
            mLocations = Maps.newEnumMap(ResourceType.class);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        Element element = attribute.getOwnerElement();

        if (element.hasAttribute(PRODUCT)) {
            return;
        }

        String tag = element.getTagName();
        String typeString = tag;
        String parentTag = element.getParentNode().getNodeName();
        if (tag.equals(TAG_ITEM)) {
            typeString = element.getAttribute(ATTR_TYPE);
            if (typeString.isEmpty()) {
                if (parentTag.equals(ResourceType.STYLE.getName())
                        && isFirstElementChild(element)) {
                    checkUniqueNames(context, (Element) element.getParentNode());
                }
                return;
            }
        }
        ResourceType type = ResourceType.fromXmlTag(element);
        if (type == null) {
            return;
        } else if (type == ResourceType.PUBLIC) {
            // We can't easily check public declarations since it's not as simple as
            // just looking up the type attribute and switching the ResourceType to it;
            // that would treat <dimen name="foo"> and <public name="foo" type="dimen">
            // as an actual duplicate name. A simple way to do it would be to change the
            // name, e.g. by prefixing "public-" to it, but that would require restructuring
            // the code a bit, and we'd need to remove it when displaying the conflicts --
            // and most importantly, there really isn't a good reason to do it; a public
            // declaration has no value, so there's no chance of creating conflicting
            // definitions.
            return;
        }

        if (type == ResourceType.ATTR && parentTag.equals(SdkConstants.TAG_DECLARE_STYLEABLE)) {
            if (isFirstElementChild(element)) {
                checkUniqueNames(context, (Element) element.getParentNode());
            }
            return;
        }

        if (!parentTag.equals(TAG_RESOURCES)) {
            // Ignore run time resource overlays (RRO)
            return;
        }

        NodeList children = element.getChildNodes();
        int childCount = children.getLength();
        for (int i = 0; i < childCount; i++) {
            Node child = children.item(i);
            short nodeType = child.getNodeType();
            if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
                String text = child.getNodeValue();
                for (int j = 0, length = text.length(); j < length; j++) {
                    char c = text.charAt(j);
                    if (c == '@') {
                        if (!text.regionMatches(false, j + 1, typeString, 0, typeString.length())
                                && context.isEnabled(TYPE_MISMATCH)) {
                            ResourceUrl url = ResourceUrl.parse(text.trim());
                            if (url != null
                                    && url.type != type
                                    &&
                                    // colors and mipmaps can apparently be used as drawables
                                    !(type == ResourceType.DRAWABLE
                                            && (url.type == ResourceType.COLOR
                                                    || url.type == ResourceType.MIPMAP))
                                    // Macro resources can reference any other resource type
                                    && type != ResourceType.MACRO
                                    // Any resource can reference a macro
                                    && url.type != ResourceType.MACRO) {
                                LintFix fix =
                                        fix().replace()
                                                .pattern("(@.*/)")
                                                .with("@" + type + "/")
                                                .build();
                                String message =
                                        "Unexpected resource reference type; "
                                                + "expected value of type `@"
                                                + type
                                                + "/`";
                                context.report(
                                        TYPE_MISMATCH,
                                        element,
                                        context.getLocation(child),
                                        message,
                                        fix);
                            }
                        }
                        break;
                    } else if (!Character.isWhitespace(c)) {
                        break;
                    }
                }
                break;
            }
        }

        Set<String> names =
                mTypeMap.computeIfAbsent(type, k -> Sets.newHashSetWithExpectedSize(40));

        String name = attribute.getValue();
        String originalName = name;
        // AAPT will flatten the namespace, turning dots, dashes and colons into _
        name = resourceNameToFieldName(name);

        if (names.contains(name)) {
            String message = String.format("`%1$s` has already been defined in this folder", name);
            if (!name.equals(originalName)) {
                message += " (`" + name + "` is equivalent to `" + originalName + "`)";
            }

            FolderConfiguration configuration = getConfiguration(context.file);
            Location location = context.getLocation(attribute);
            List<Pair<String, Handle>> list = mLocations.get(type);
            for (Pair<String, Handle> pair : list) {
                if (name.equals(pair.getFirst())) {
                    Location secondary = pair.getSecond().resolve();
                    if (configuration == null) {
                        configuration = getConfiguration(secondary.getFile());
                    }
                    secondary.setMessage("Previously defined here");
                    location.setSecondary(secondary);
                }
            }

            if (configuration != null) {
                message +=
                        ". If you are trying to create qualified resources, they should be created in separate directories, "
                                + "such as `"
                                + configuration.getFolderName(ResourceFolderType.VALUES)
                                + "`. See "
                                + "https://developer.android.com/guide/topics/resources/providing-resources.html#AlternativeResources.";
            }

            context.report(ISSUE, attribute, location, message);
        } else {
            names.add(name);
            List<Pair<String, Handle>> list =
                    mLocations.computeIfAbsent(type, k -> Lists.newArrayList());
            Location.Handle handle = context.createLocationHandle(attribute);
            list.add(Pair.of(name, handle));
        }
    }

    @Nullable
    private static FolderConfiguration getConfiguration(@NonNull File file) {
        String fileBaseName = Lint.getBaseName(file.getName());
        int dash = fileBaseName.indexOf('-');
        if (dash != -1) {
            return FolderConfiguration.getConfigForFolder(fileBaseName);
        }
        return null;
    }

    private static void checkUniqueNames(XmlContext context, Element parent) {
        List<Element> items = Lint.getChildren(parent);
        if (items.size() > 1) {
            Set<String> names = Sets.newHashSet();
            for (Element item : items) {
                Attr nameNode = item.getAttributeNode(ATTR_NAME);
                if (nameNode != null) {
                    String name = nameNode.getValue();
                    if (names.contains(name) && context.isEnabled(ISSUE)) {
                        Location location = context.getLocation(nameNode);
                        for (Element prevItem : items) {
                            Attr attribute = prevItem.getAttributeNode(ATTR_NAME);
                            if (attribute != null && name.equals(attribute.getValue())) {
                                assert prevItem != item;
                                Location prev = context.getLocation(prevItem);
                                prev.setMessage("Previously defined here");
                                location.setSecondary(prev);
                                break;
                            }
                        }
                        String message =
                                String.format(
                                        "`%1$s` has already been defined in this `<%2$s>`",
                                        name, parent.getTagName());
                        context.report(ISSUE, nameNode, location, message);
                    }
                    names.add(name);
                }
            }
        }
    }

    private static boolean isFirstElementChild(Node node) {
        node = node.getPreviousSibling();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                return false;
            }
            node = node.getPreviousSibling();
        }

        return true;
    }
}
