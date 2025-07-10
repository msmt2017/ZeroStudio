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

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_ABOVE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BASELINE;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_END;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_PARENT_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING;
import static com.android.SdkConstants.ATTR_LAYOUT_BELOW;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_HORIZONTAL;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_IN_PARENT;
import static com.android.SdkConstants.ATTR_LAYOUT_CENTER_VERTICAL;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN;
import static com.android.SdkConstants.ATTR_LAYOUT_COLUMN_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_END;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_START;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW;
import static com.android.SdkConstants.ATTR_LAYOUT_ROW_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_SPAN;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_END_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_LEFT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_RIGHT_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_TO_START_OF;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_LAYOUT_X;
import static com.android.SdkConstants.ATTR_LAYOUT_Y;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.GRID_LAYOUT;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TABLE_ROW;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.SdkInfo;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.Handle;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Looks for layout params on views that are "obsolete" - may have made sense when the view was
 * added but there is a different layout parent now which does not use the given layout params.
 */
public class ObsoleteLayoutParamsDetector extends LayoutDetector {
    /** Usage of deprecated views or attributes */
    public static final Issue ISSUE =
            Issue.create(
                    "ObsoleteLayoutParam",
                    "Obsolete layout params",
                    "The given layout_param is not defined for the given layout, meaning it has no "
                            + "effect. This usually happens when you change the parent layout or move view "
                            + "code around without updating the layout params. This will cause useless "
                            + "attribute processing at runtime, and is misleading for others reading the "
                            + "layout so the parameter should be removed.",
                    Category.PERFORMANCE,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /**
     * Set of layout parameter names that are considered valid no matter what so no other checking
     * is necessary - such as layout_width and layout_height.
     */
    private static boolean isValid(@NonNull String attribute) {
        switch (attribute) {
                // Available (mostly) everywhere: No check
            case ATTR_LAYOUT_WIDTH:
            case ATTR_LAYOUT_HEIGHT:

                // The layout_gravity isn't "global" but it's defined on many of the most
                // common layouts (FrameLayout, LinearLayout and GridLayout) so we don't
                // currently check for it. In order to do this we'd need to make the map point
                // to lists rather than individual layouts or we'd need a bunch of special cases
                // like the one done for layout_column below.
            case ATTR_LAYOUT_GRAVITY:

                // From ViewGroup.MarginLayoutParams
            case ATTR_LAYOUT_MARGIN_LEFT:
            case ATTR_LAYOUT_MARGIN_START:
            case ATTR_LAYOUT_MARGIN_RIGHT:
            case ATTR_LAYOUT_MARGIN_END:
            case ATTR_LAYOUT_MARGIN_TOP:
            case ATTR_LAYOUT_MARGIN_BOTTOM:
            case ATTR_LAYOUT_MARGIN:
                return true;
            default:
                return false;
        }
    }

    /**
     * Mapping from a layout parameter name (local name only) to the defining ViewGroup. Note that
     * it's possible for the same name to be defined by multiple ViewGroups - but it turns out this
     * is extremely rare (the only examples are layout_column defined by both TableRow and
     * GridLayout, and layout_gravity defined by many layouts) so rather than handle this with every
     * single layout attribute pointing to a list, this is just special cased instead.
     */
    @Nullable
    private static String getLayoutForAttribute(@NonNull String attribute) {
        switch (attribute) {

                // Absolute Layout
            case ATTR_LAYOUT_X:
                return ABSOLUTE_LAYOUT;
            case ATTR_LAYOUT_Y:
                return ABSOLUTE_LAYOUT;

                // Linear Layout
            case ATTR_LAYOUT_WEIGHT:
                return LINEAR_LAYOUT;

                // Grid Layout
            case ATTR_LAYOUT_COLUMN:
                return GRID_LAYOUT;
            case ATTR_LAYOUT_COLUMN_SPAN:
                return GRID_LAYOUT;
            case ATTR_LAYOUT_ROW:
                return GRID_LAYOUT;
            case ATTR_LAYOUT_ROW_SPAN:
                return GRID_LAYOUT;

                // Table Layout
                // ATTR_LAYOUT_COLUMN is defined for both GridLayout and TableLayout,
                // so we don't want to do
                //    case ATTR_LAYOUT_COLUMN: return TABLE_ROW;
                // here since it would wipe out the above GridLayout registration.
                // Since this is the only case where there is a conflict (in addition to
                // layout_gravity
                // which is defined in many places), rather than making the map point to lists
                // this specific case is just special cased below, look for ATTR_LAYOUT_COLUMN.

            case ATTR_LAYOUT_SPAN:
                return TABLE_ROW;

                // Relative Layout
            case ATTR_LAYOUT_ALIGN_LEFT:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_START:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_RIGHT:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_END:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_TOP:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_BOTTOM:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_PARENT_TOP:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_PARENT_BOTTOM:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_PARENT_LEFT:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_PARENT_START:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_PARENT_RIGHT:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_PARENT_END:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ALIGN_BASELINE:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_CENTER_IN_PARENT:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_CENTER_VERTICAL:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_CENTER_HORIZONTAL:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_TO_RIGHT_OF:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_TO_END_OF:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_TO_LEFT_OF:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_TO_START_OF:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_BELOW:
                return RELATIVE_LAYOUT;
            case ATTR_LAYOUT_ABOVE:
                return RELATIVE_LAYOUT;
        }
        return null;
    }

    /**
     * Map from an included layout to all the including contexts (each including context is a pair
     * of a file containing the include to the parent tag at the included location)
     */
    private Map<String, List<Pair<File, String>>> mIncludes;

    /**
     * List of pending include checks. When a layout parameter attribute is found on a root element,
     * or on a child of a {@code merge} root tag, then we want to check across layouts whether the
     * including context (the parent of the include tag) is valid for this attribute. We cannot
     * check this immediately because we are processing the layouts in an arbitrary order so the
     * included layout may be seen before the including layout and so on. Therefore, we stash these
     * attributes to be checked after we're done. Each pair is a pair of an attribute name to be
     * checked, and the file that attribute is referenced in.
     */
    private final List<Pair<String, Location.Handle>> mPending = new ArrayList<>();

    /** Constructs a new {@link ObsoleteLayoutParamsDetector} */
    public ObsoleteLayoutParamsDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(VIEW_INCLUDE);
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String name = attribute.getLocalName();
        if (name != null
                && name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)
                && ANDROID_URI.equals(attribute.getNamespaceURI())) {
            if (isValid(name)) {
                return;
            }

            String parent = getLayoutForAttribute(name);
            if (parent != null) {
                Element viewElement = attribute.getOwnerElement();
                Node layoutNode = viewElement.getParentNode();
                if (layoutNode == null || layoutNode.getNodeType() != Node.ELEMENT_NODE) {
                    // This is a layout attribute on a root element; this presumably means
                    // that this layout is included so check the included layouts to make
                    // sure at least one included context is valid for this layout_param.
                    // We can't do that yet since we may be processing the include tag to
                    // this layout after the layout itself. Instead, stash a work order...
                    if (context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
                        Location.Handle handle = context.createLocationHandle(attribute);
                        handle.setClientData(attribute);
                        mPending.add(Pair.of(name, handle));
                    }

                    return;
                }

                String parentTag = ((Element) layoutNode).getTagName();
                if (parentTag.equals(VIEW_MERGE)) {
                    // This is a merge which means we need to check the including contexts,
                    // wherever they are. This has to be done after all the files have been
                    // scanned since we are not processing the files in any particular order.
                    if (context.getScope().contains(Scope.ALL_RESOURCE_FILES)) {
                        Location.Handle handle = context.createLocationHandle(attribute);
                        handle.setClientData(attribute);
                        mPending.add(Pair.of(name, handle));
                    }

                    return;
                }

                if (!isValidParamForParent(context, name, parent, parentTag)) {
                    if (name.equals(ATTR_LAYOUT_COLUMN)
                            && isValidParamForParent(context, name, TABLE_ROW, parentTag)) {
                        return;
                    }
                    context.report(
                            ISSUE,
                            attribute,
                            context.getLocation(attribute),
                            String.format(
                                    "Invalid layout param in a `%1$s`: `%2$s`", parentTag, name));
                }
            } else {
                // We could warn about unknown layout params but this might be brittle if
                // new params are added or if people write custom ones; this is just a log
                // for us to track these and update the check as necessary:
                // context.client.log(null,
                //    String.format("Unrecognized layout param '%1$s'", name));
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String layout = element.getAttribute(ATTR_LAYOUT);
        if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) { // Ignore @android:layout/ layouts
            layout = layout.substring(LAYOUT_RESOURCE_PREFIX.length());

            Node parent = element.getParentNode();
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                String tag = parent.getNodeName();
                if (tag.indexOf('.') == -1 && !tag.equals(VIEW_MERGE)) {
                    if (!context.getProject().getReportIssues()) {
                        // If this is a library project not being analyzed, ignore it
                        return;
                    }

                    if (mIncludes == null) {
                        mIncludes = new HashMap<>();
                    }
                    List<Pair<File, String>> includes = mIncludes.get(layout);
                    if (includes == null) {
                        includes = new ArrayList<>();
                        mIncludes.put(layout, includes);
                    }
                    includes.add(Pair.of(context.file, tag));
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mIncludes == null) {
            return;
        }

        for (Pair<String, Location.Handle> pending : mPending) {
            Handle handle = pending.getSecond();
            Location location = handle.resolve();
            File file = location.getFile();
            String layout = file.getName();
            if (layout.endsWith(DOT_XML)) {
                layout = layout.substring(0, layout.length() - DOT_XML.length());
            }

            List<Pair<File, String>> includes = mIncludes.get(layout);
            if (includes == null) {
                // Nobody included this file
                continue;
            }

            String name = pending.getFirst();
            String parent = getLayoutForAttribute(name);
            if (parent == null) {
                continue;
            }

            boolean isValid = false;
            for (Pair<File, String> include : includes) {
                String parentTag = include.getSecond();
                if (isValidParamForParent(context, name, parent, parentTag)) {
                    isValid = true;
                    break;
                } else if (!isValid
                        && name.equals(ATTR_LAYOUT_COLUMN)
                        && isValidParamForParent(context, name, TABLE_ROW, parentTag)) {
                    isValid = true;
                    break;
                }
            }

            if (!isValid) {
                Object clientData = handle.getClientData();
                if (clientData instanceof Node) {
                    if (context.getDriver().isSuppressed(null, ISSUE, (Node) clientData)) {
                        return;
                    }
                }

                StringBuilder sb = new StringBuilder(40);
                for (Pair<File, String> include : includes) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    File from = include.getFirst();
                    String parentTag = include.getSecond();
                    sb.append(
                            String.format(
                                    "included from within a `%1$s` in `%2$s`",
                                    parentTag,
                                    Lint.getFileNameWithParent(context.getClient(), from)));
                }
                String message =
                        String.format("Invalid layout param '`%1$s`' (%2$s)", name, sb.toString());
                // TODO: Compute applicable scope node
                context.report(ISSUE, location, message);
            }
        }
    }

    /**
     * Checks whether the given layout parameter name is valid for the given parent tag assuming it
     * has the given current parent tag
     */
    private static boolean isValidParamForParent(
            Context context, String name, String parent, String parentTag) {
        if (parentTag.indexOf('.') != -1 || parentTag.equals(VIEW_TAG)) {
            // Custom tag: We don't know whether it extends one of the builtin
            // types where the layout param is valid, so don't complain
            return true;
        }

        SdkInfo sdk = context.getSdkInfo();

        if (!parentTag.equals(parent)) {
            String tag = sdk.getParentViewName(parentTag);
            while (tag != null) {
                if (tag.equals(parent)) {
                    return true;
                }
                tag = sdk.getParentViewName(tag);
            }

            return false;
        }

        return true;
    }
}
