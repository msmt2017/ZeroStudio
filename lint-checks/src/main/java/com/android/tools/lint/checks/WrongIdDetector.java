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

import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.VALUE_ID;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.tools.lint.checks.RequiredAttributeDetector.PERCENT_RELATIVE_LAYOUT;
import static com.android.tools.lint.client.api.ResourceRepositoryScope.ALL_DEPENDENCIES;
import static com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES;
import static com.android.tools.lint.detector.api.Lint.editDistance;
import static com.android.tools.lint.detector.api.Lint.isSameResourceFile;
import static com.android.tools.lint.detector.api.Lint.stripIdPrefix;

import com.android.AndroidXConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.XmlUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Checks for duplicate ids within a layout and within an included layout */
public class WrongIdDetector extends LayoutDetector {
    private static final Implementation IMPLEMENTATION =
            new Implementation(WrongIdDetector.class, Scope.RESOURCE_FILE_SCOPE);

    /** Ids bound to widgets in any of the layout files */
    private final Set<String> mGlobalIds = new HashSet<>(100);

    /** Ids bound to widgets in the current layout file */
    private Set<String> mFileIds;

    /** Ids declared in a value's file, e.g. {@code <item type="id" name="foo"/>} */
    private Set<String> mDeclaredIds;

    /**
     * Location handles for the various id references that were not found as defined in the same
     * layout, to be checked after the whole project has been scanned
     */
    private Map<String, Handle> mHandles;

    /**
     * Issues to be reported for id references unless we determine that the id was undefined after
     * checking the whole project
     */
    private Map<String, String> mPendingNotSibling;

    /**
     * List of RelativeLayout elements in the current layout (and percent layouts, and constraint
     * layouts, etc -- any elements that have constraints among their children with id references
     */
    private List<Element> mRelativeLayouts;

    /** Reference to an unknown id */
    @SuppressWarnings("unchecked")
    public static final Issue UNKNOWN_ID =
            Issue.create(
                    "UnknownId",
                    "Reference to an unknown id",
                    "The `@+id/` syntax refers to an existing id, or creates a new one if it has "
                            + "not already been defined elsewhere. However, this means that if you have a "
                            + "typo in your reference, or if the referred view no longer exists, you do not "
                            + "get a warning since the id will be created on demand. This check catches "
                            + "errors where you have renamed an id without updating all of the references to "
                            + "it.",
                    Category.CORRECTNESS,
                    8,
                    Severity.FATAL,
                    new Implementation(
                            WrongIdDetector.class,
                            Scope.ALL_RESOURCES_SCOPE,
                            Scope.RESOURCE_FILE_SCOPE));

    /** Reference to an id that is not a sibling */
    public static final Issue NOT_SIBLING =
            Issue.create(
                    "NotSibling",
                    "Invalid Constraints",
                    "Layout constraints in a given `ConstraintLayout` or `RelativeLayout` should "
                            + "reference other views within the same relative layout (but not itself!)",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** An ID declaration which is not valid */
    public static final Issue INVALID =
            Issue.create(
                    "InvalidId",
                    "Invalid ID declaration",
                    "An id definition **must** be of the form `@+id/yourname`. The tools have not "
                            + "rejected strings of the form `@+foo/bar` in the past, but that was an error, "
                            + "and could lead to tricky errors because of the way the id integers are assigned.\n"
                            + "\n"
                            + "If you really want to have different \"scopes\" for your id's, use prefixes "
                            + "instead, such as `login_button1` and `login_button2`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.FATAL,
                    IMPLEMENTATION);

    /** Reference to an id that is not in the current layout */
    public static final Issue UNKNOWN_ID_LAYOUT =
            Issue.create(
                    "UnknownIdInLayout",
                    "Reference to an id that is not in the current layout",
                    "The `@+id/` syntax refers to an existing id, or creates a new one if it has "
                            + "not already been defined elsewhere. However, this means that if you have a "
                            + "typo in your reference, or if the referred view no longer exists, you do not "
                            + "get a warning since the id will be created on demand.\n"
                            + "\n"
                            + "This is sometimes intentional, for example where you are referring to a view "
                            + "which is provided in a different layout via an include. However, it is usually "
                            + "an accident where you have a typo or you have renamed a view without updating "
                            + "all the references to it.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(WrongIdDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Constructs a duplicate id check */
    public WrongIdDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ID);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ImmutableSet.of(
                RELATIVE_LAYOUT,
                TAG_ITEM,
                PERCENT_RELATIVE_LAYOUT,
                AndroidXConstants.CLASS_CONSTRAINT_LAYOUT.oldName(),
                AndroidXConstants.CLASS_CONSTRAINT_LAYOUT.newName());
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mFileIds = new HashSet<>();
        mRelativeLayouts = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mRelativeLayouts != null) {
            if (!context.getProject().getReportIssues()) {
                // If this is a library project not being analyzed, ignore it
                return;
            }

            for (Element layout : mRelativeLayouts) {
                checkLayout(context, layout);
            }
        }

        mFileIds = null;

        if (!context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
            checkHandles(context);
        }
    }

    private void checkLayout(@NonNull Context context, Element layout) {
        Set<String> ids = Sets.newHashSetWithExpectedSize(20);
        for (Element child : XmlUtils.getSubTags(layout)) {
            String id = child.getAttributeNS(ANDROID_URI, ATTR_ID);
            if (id != null && !id.isEmpty()) {
                ids.add(id);
            } else if (VIEW_INCLUDE.equals(child.getTagName())) {
                String included = child.getAttribute(ATTR_LAYOUT);
                if (!addIncludedIds(context, ids, included)) {
                    // There's an include tag and something went wrong looking
                    // up the corresponding layout: don't attempt to enforce
                    // sibling lookup here.
                    mHandles = null;
                    return;
                }
            }
        }

        boolean isConstraintLayout =
                AndroidXConstants.CLASS_CONSTRAINT_LAYOUT.isEquals(layout.getTagName());

        for (Element element : XmlUtils.getSubTags(layout)) {
            String selfId = stripIdPrefix(element.getAttributeNS(ANDROID_URI, ATTR_ID));

            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Attr attr = (Attr) attributes.item(i);
                String value = attr.getValue();
                if (value.startsWith(NEW_ID_PREFIX) || value.startsWith(ID_PREFIX)) {
                    String localName = attr.getLocalName();
                    if (localName != null
                            && localName.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)
                            && (ANDROID_URI.equals(attr.getNamespaceURI())
                                    || AUTO_URI.equals(attr.getNamespaceURI()))) {
                        checkIdReference(
                                context, layout, ids, isConstraintLayout, selfId, attr, value);
                    }
                } else if (isConstraintLayout
                        && CONSTRAINT_REFERENCED_IDS.equals(attr.getLocalName())) {
                    Splitter splitter = Splitter.on(',').trimResults().omitEmptyStrings();
                    for (String id : splitter.split(value)) {
                        checkIdReference(context, layout, ids, true, selfId, attr, id);
                    }
                }
            }
        }
    }

    private static boolean addIncludedIds(
            @NonNull Context context, @NonNull Set<String> ids, @NonNull String included) {
        if (included.isEmpty()) {
            return true;
        }

        LintClient client = context.getClient();
        boolean full = context.isGlobalAnalysis();
        Project project = full ? context.getMainProject() : context.getProject();
        ResourceRepository resources = client.getResources(project, LOCAL_DEPENDENCIES);
        List<ResourceItem> layouts =
                resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT, included);
        if (layouts.isEmpty()) {
            return false;
        }
        ResourceItem layout = layouts.get(0);
        PathString source = layout.getSource();
        if (source == null) {
            return false;
        }
        try {
            XmlPullParser parser = client.createXmlPullParser(source);
            if (parser != null) {
                addIncludedIds(parser, ids);
            } else {
                return false;
            }
        } catch (XmlPullParserException | IOException e) {
            // Users might be editing these files in the IDE; don't flag
            return false;
        }
        return true;
    }

    private static void addIncludedIds(@NonNull XmlPullParser parser, @NonNull Set<String> ids)
            throws XmlPullParserException, IOException {
        int depth = -1;
        while (true) {
            int event = parser.next();
            if (event == XmlPullParser.START_TAG) {
                depth++;
                if (depth == 0 && !VIEW_MERGE.equals(parser.getName())) {
                    // Not a <merge>: just add the id on the root attribute; that will
                    // be the element inserted at the <include> side
                    String id = parser.getAttributeValue(ANDROID_URI, ATTR_ID);
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                    return;
                } // else: <merge>: add all children at depth 1
                if (depth == 1) {
                    String id = parser.getAttributeValue(ANDROID_URI, ATTR_ID);
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                depth--;
            } else if (event == XmlPullParser.END_DOCUMENT) {
                return;
            }
        }
    }

    private void checkIdReference(
            @NonNull Context context,
            Element layout,
            Set<String> siblingIds,
            boolean isConstraintLayout,
            String selfId,
            Attr attr,
            String id) {
        if (!idDefined(mFileIds, id)) {
            // Stash a reference to this id and location such that
            // we can check after the *whole* layout has been processed,
            // since it's too early to conclude here that the id does
            // not exist (you are allowed to have forward references)
            XmlContext xmlContext = (XmlContext) context;
            Handle handle = xmlContext.createLocationHandle(attr);
            handle.setClientData(attr);

            if (mHandles == null) {
                mHandles = new LinkedHashMap<>();
                mPendingNotSibling = new HashMap<>();
            }
            mHandles.put(id, handle);
        }
        // Check siblings. TODO: Look for cycles!
        if (siblingIds.contains(id)) {
            // Make sure it's not pointing to self
            if (!ATTR_ID.equals(attr.getLocalName())
                    && !selfId.isEmpty()
                    && id.endsWith(selfId)
                    && stripIdPrefix(id).equals(selfId)) {
                String message =
                        String.format(
                                "Cannot be relative to self: id=%1$s, %2$s=%3$s",
                                selfId, attr.getLocalName(), selfId);
                reportNotSiblingIfKnownId(context, id, attr, message);
            }
            return;
        }
        if (id.startsWith(NEW_ID_PREFIX)) {
            if (siblingIds.contains(ID_PREFIX + stripIdPrefix(id))) {
                return;
            }
        } else if (id.startsWith(ID_PREFIX)) {
            if (siblingIds.contains(NEW_ID_PREFIX + stripIdPrefix(id))) {
                return;
            }
        } else if (siblingIds.contains(NEW_ID_PREFIX + id) || siblingIds.contains(ID_PREFIX + id)) {
            return;
        }
        if (isConstraintLayout) {
            // A reference to the ConstraintLayout from a child is valid
            String parentId = stripIdPrefix(layout.getAttributeNS(ANDROID_URI, ATTR_ID));
            if (parentId.equals(stripIdPrefix(id))) {
                return;
            }
        }
        if (context.isEnabled(NOT_SIBLING)) {
            String message =
                    String.format(
                            "`%1$s` is not a sibling in the same `%2$s`",
                            id, isConstraintLayout ? "ConstraintLayout" : "RelativeLayout");
            reportNotSiblingIfKnownId(context, id, attr, message);
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
            checkHandles(context);
        }
    }

    private void reportNotSiblingIfKnownId(
            @NonNull Context context,
            @NonNull String id,
            @NonNull Node attr,
            @NonNull String message) {
        if (mHandles != null && mHandles.containsKey(id)) {
            // Potentially unknown ID, store the issue for later
            mPendingNotSibling.put(id, message);
        } else {
            // ID is known, just report the problem
            XmlContext xmlContext = (XmlContext) context;
            Location location = xmlContext.getLocation(attr);
            xmlContext.report(NOT_SIBLING, attr, location, message);
        }
    }

    private void checkHandles(@NonNull Context context) {
        if (mHandles != null) {
            boolean checkSameLayout = context.isEnabled(UNKNOWN_ID_LAYOUT);
            boolean checkExists = context.isEnabled(UNKNOWN_ID);
            boolean projectScope = context.getScope().contains(Scope.ALL_RESOURCE_FILES);
            for (Map.Entry<String, Handle> pair : mHandles.entrySet()) {
                String id = pair.getKey();
                Handle handle = pair.getValue();
                boolean isBound =
                        projectScope
                                ? idDefined(mGlobalIds, id)
                                : idDefined(context, id, context.file);
                LintClient client = context.getClient();
                if (!isBound && checkExists) {
                    boolean isDeclared = idDefined(mDeclaredIds, id);
                    id = stripIdPrefix(id);
                    String suggestionMessage;
                    Set<String> spellingDictionary = createSpellingDictionary();
                    // TODO: Do this in general, even from batch mode!
                    if (!projectScope) {
                        Project project = context.getProject();
                        ResourceRepository resources =
                                client.getResources(project, LOCAL_DEPENDENCIES);
                        spellingDictionary =
                                Sets.newHashSet(
                                        resources
                                                .getResources(
                                                        ResourceNamespace.TODO(), ResourceType.ID)
                                                .keySet());
                        spellingDictionary.remove(id);
                    }
                    List<String> suggestions = getSpellingSuggestions(id, spellingDictionary);
                    if (suggestions.size() > 1) {
                        suggestionMessage =
                                String.format(
                                        " Did you mean one of {%2$s} ?",
                                        id, Joiner.on(", ").join(suggestions));
                    } else if (!suggestions.isEmpty()) {
                        suggestionMessage =
                                String.format(" Did you mean %2$s ?", id, suggestions.get(0));
                    } else {
                        suggestionMessage = "";
                    }
                    String message;
                    if (isDeclared) {
                        message =
                                String.format(
                                        "The id \"`%1$s`\" is defined but not assigned to any views.%2$s",
                                        id, suggestionMessage);
                    } else {
                        message =
                                String.format(
                                        "The id \"`%1$s`\" is not defined anywhere.%2$s",
                                        id, suggestionMessage);
                    }
                    report(context, UNKNOWN_ID, handle, message);
                } else if (checkSameLayout
                        && (!projectScope || isBound)
                        && id.startsWith(NEW_ID_PREFIX)) {
                    // The id was defined, but in a different layout. Usually not intentional
                    // (might be referring to a random other view that happens to have the same
                    // name.)
                    report(
                            context,
                            UNKNOWN_ID_LAYOUT,
                            handle,
                            String.format(
                                    "The id \"`%1$s`\" is not referring to any views in this layout",
                                    stripIdPrefix(id)));
                } else if (mPendingNotSibling.containsKey(id)) {
                    String message = mPendingNotSibling.get(id);
                    context.report(NOT_SIBLING, handle.resolve(), message);
                }
            }
        }
    }

    private Set<String> createSpellingDictionary() {
        Set<String> dictionary = new HashSet<>();
        mGlobalIds.stream()
                .filter(id -> id.startsWith(NEW_ID_PREFIX))
                .forEach(id -> dictionary.add(stripIdPrefix(id)));
        if (mDeclaredIds != null) {
            mDeclaredIds.forEach(id -> dictionary.add(stripIdPrefix(id)));
        }
        return dictionary;
    }

    private static void report(Context context, Issue issue, Handle handle, String message) {
        Location location = handle.resolve();
        Object clientData = handle.getClientData();
        if (clientData instanceof Node) {
            if (context.getDriver().isSuppressed(null, issue, (Node) clientData)) {
                return;
            }
        }

        context.report(issue, location, message);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (tagName.equals(TAG_ITEM)) {
            String type = element.getAttribute(ATTR_TYPE);
            if (VALUE_ID.equals(type)) {
                String name = element.getAttribute(ATTR_NAME);
                if (!name.isEmpty()) {
                    if (mDeclaredIds == null) {
                        mDeclaredIds = Sets.newHashSet();
                    }
                    mDeclaredIds.add(NEW_ID_PREFIX + name);
                    mGlobalIds.add(NEW_ID_PREFIX + name);
                }
            }
        } else {
            assert tagName.equals(RELATIVE_LAYOUT)
                    || tagName.equals(PERCENT_RELATIVE_LAYOUT)
                    || CLASS_CONSTRAINT_LAYOUT.isEquals(tagName);
            if (mRelativeLayouts == null) {
                mRelativeLayouts = new ArrayList<>();
            }
            mRelativeLayouts.add(element);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (context.getResourceFolderType() != ResourceFolderType.LAYOUT) {
            return;
        }
        if (!Objects.equals(attribute.getNamespaceURI(), ANDROID_URI)) {
            return;
        }
        assert attribute.getName().equals(ATTR_ID) || attribute.getLocalName().equals(ATTR_ID);
        String id = attribute.getValue();
        if (id.startsWith("@*android:") && !LintClient.isGradle()) {
            // Avoid complaining about private resources when analyzing the Android framework
            id = "@" + id.substring(2);
        }
        mFileIds.add(id);
        mGlobalIds.add(id);

        if (id.equals(NEW_ID_PREFIX) || id.equals(ID_PREFIX)) {
            String message = "Invalid id: missing value";
            context.report(INVALID, attribute, context.getLocation(attribute), message);
        } else if (id.startsWith("@+")
                        && !id.startsWith(NEW_ID_PREFIX)
                        && !id.startsWith("@+android:id/")
                || id.startsWith(NEW_ID_PREFIX) && id.indexOf('/', NEW_ID_PREFIX.length()) != -1) {
            int nameStart = id.startsWith(NEW_ID_PREFIX) ? NEW_ID_PREFIX.length() : 2;
            String suggested = NEW_ID_PREFIX + id.substring(nameStart).replace('/', '_');
            String message =
                    String.format(
                            "ID definitions **must** be of the form `@+id/name`; try using `%1$s`",
                            suggested);
            context.report(INVALID, attribute, context.getLocation(attribute), message);
        } else if (!id.startsWith(NEW_ID_PREFIX)
                && !id.startsWith(ID_PREFIX)
                && !id.startsWith("@android:id/")) {
            String message = "Invalid id; ID definitions **must** be of the form `@+id/name`";
            ResourceUrl parse = ResourceUrl.parse(id);
            if (parse != null) {
                message += "; did you mean `@+id/" + parse.name + "`?";
            }
            context.report(INVALID, attribute, context.getLocation(attribute), message);
        }
    }

    private static boolean idDefined(Set<String> ids, String id) {
        if (ids == null) {
            return false;
        }
        boolean definedLocally = ids.contains(id);
        if (!definedLocally) {
            if (id.startsWith(NEW_ID_PREFIX)) {
                return ids.contains(ID_PREFIX + id.substring(NEW_ID_PREFIX.length()));
            } else if (id.startsWith(ID_PREFIX)) {
                return ids.contains(NEW_ID_PREFIX + id.substring(ID_PREFIX.length()));
            } else {
                return ids.contains(NEW_ID_PREFIX + id);
            }
        }

        return true;
    }

    private boolean idDefined(@NonNull Context context, @NonNull String id, @Nullable File notIn) {
        LintClient client = context.getClient();
        Project project = context.getProject();
        ResourceRepository resources = client.getResources(project, ALL_DEPENDENCIES);
        List<ResourceItem> items =
                resources.getResources(
                        ResourceNamespace.TODO(), ResourceType.ID, stripIdPrefix(id));
        for (ResourceItem item : items) {
            PathString source = item.getSource();
            if (source != null) {
                String parentName = source.getParentFileName();
                if (parentName != null && parentName.startsWith(FD_RES_VALUES)) {
                    if (mDeclaredIds == null) {
                        mDeclaredIds = Sets.newHashSet();
                    }
                    mDeclaredIds.add(id);
                    continue;
                }

                // Ignore definitions in the given file. This is used to ignore
                // matches in the same file as the reference, since the reference
                // is often expressed as a definition.
                if (!isSameResourceFile(source.toFile(), notIn)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static List<String> getSpellingSuggestions(String id, Collection<String> ids) {
        int maxDistance = id.length() >= 4 ? 2 : 1;

        // Look for typos and try to match with custom views and android views
        Multimap<Integer, String> matches = ArrayListMultimap.create(2, 10);
        int count = 0;
        if (!ids.isEmpty()) {
            for (String matchWith : ids) {
                matchWith = stripIdPrefix(matchWith);
                int distance = editDistance(id, matchWith, maxDistance);
                if (distance <= maxDistance) {
                    matches.put(distance, matchWith);
                }

                if (count++ > 100) {
                    // Make sure that for huge projects we don't completely grind to a halt
                    break;
                }
            }
        }

        for (int i = 0; i < maxDistance; i++) {
            Collection<String> strings = matches.get(i);
            if (strings != null && !strings.isEmpty()) {
                List<String> suggestions = new ArrayList<>(strings);
                Collections.sort(suggestions);
                return suggestions;
            }
        }

        return Collections.emptyList();
    }
}
