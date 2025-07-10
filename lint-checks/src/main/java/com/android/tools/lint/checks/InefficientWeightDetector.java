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
import static com.android.SdkConstants.ATTR_BASELINE_ALIGNED;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_ORIENTATION;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RADIO_GROUP;
import static com.android.SdkConstants.SPACE;
import static com.android.SdkConstants.VALUE_FALSE;
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_HORIZONTAL;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_VERTICAL;
import static com.android.SdkConstants.VIEW;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_TAG;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.tools.lint.client.api.SdkInfo;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Checks whether a layout_weight is declared inefficiently. */
public class InefficientWeightDetector extends LayoutDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(InefficientWeightDetector.class, Scope.RESOURCE_FILE_SCOPE);

    /** Can a weight be replaced with 0dp instead for better performance? */
    public static final Issue INEFFICIENT_WEIGHT =
            Issue.create(
                    "InefficientWeight",
                    "Inefficient layout weight",
                    "When only a single widget in a `LinearLayout` defines a weight, it is more "
                            + "efficient to assign a width/height of `0dp` to it since it will absorb all "
                            + "the remaining space anyway. With a declared width/height of `0dp` it "
                            + "does not have to measure its own size first.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Are weights nested? */
    public static final Issue NESTED_WEIGHTS =
            Issue.create(
                    "NestedWeights",
                    "Nested layout weights",
                    "Layout weights require a widget to be measured twice. When a `LinearLayout` with "
                            + "non-zero weights is nested inside another `LinearLayout` with non-zero weights, "
                            + "then the number of measurements increase exponentially.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Should a LinearLayout set android:baselineAligned? */
    public static final Issue BASELINE_WEIGHTS =
            Issue.create(
                    "DisableBaselineAlignment",
                    "Missing `baselineAligned` attribute",
                    "When a `LinearLayout` is used to distribute the space proportionally between "
                            + "nested layouts, the baseline alignment property should be turned off to "
                            + "make the layout computation faster.",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    /** Using 0dp on the wrong dimension */
    public static final Issue WRONG_0DP =
            Issue.create(
                    "Suspicious0dp",
                    "Suspicious 0dp dimension",
                    "Using 0dp as the width in a horizontal `LinearLayout` with weights is a useful "
                            + "trick to ensure that only the weights (and not the intrinsic sizes) are used "
                            + "when sizing the children.\n"
                            + "\n"
                            + "However, if you use 0dp for the opposite dimension, the view will be invisible. "
                            + "This can happen if you change the orientation of a layout without also flipping "
                            + "the `0dp` dimension in all the children.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /** Missing explicit orientation */
    public static final Issue ORIENTATION =
            Issue.create(
                    "Orientation",
                    "Missing explicit orientation",
                    "The default orientation of a `LinearLayout` is horizontal. It's pretty easy to "
                            + "believe that the layout is vertical, add multiple children to it, and wonder "
                            + "why only the first child is visible (when the subsequent children are "
                            + "off screen to the right). This lint rule helps pinpoint this issue by "
                            + "warning whenever a `LinearLayout` is used with an implicit orientation "
                            + "and multiple children.\n"
                            + "\n"
                            + "It also checks for empty LinearLayouts without an `orientation` attribute "
                            + "that also defines an `id` attribute. This catches the scenarios where "
                            + "children will be added to the `LinearLayout` dynamically. ",
                    Category.CORRECTNESS,
                    2,
                    Severity.ERROR,
                    IMPLEMENTATION);

    /**
     * Map from element to whether that element has a non-zero linear layout weight or has an
     * ancestor which does
     */
    private final Map<Node, Boolean> mInsideWeight = new IdentityHashMap<>();

    /** Constructs a new {@link InefficientWeightDetector} */
    public InefficientWeightDetector() {}

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(LINEAR_LAYOUT);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        List<Element> children = Lint.getChildren(element);
        // See if there is exactly one child with a weight
        boolean multipleWeights = false;
        Element weightChild = null;
        boolean checkNesting = context.isEnabled(NESTED_WEIGHTS);
        for (Element child : children) {
            if (child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                if (weightChild != null) {
                    // More than one child defining a weight!
                    multipleWeights = true;
                } else //noinspection ConstantConditions
                if (!multipleWeights) {
                    weightChild = child;
                }

                if (checkNesting) {
                    mInsideWeight.put(child, Boolean.TRUE);

                    Boolean inside = mInsideWeight.get(element);
                    if (inside == null) {
                        mInsideWeight.put(element, Boolean.FALSE);
                    } else if (inside) {
                        Attr sizeNode = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                        context.report(
                                NESTED_WEIGHTS,
                                sizeNode,
                                context.getLocation(sizeNode),
                                "Nested weights are bad for performance");
                        // Don't warn again
                        checkNesting = false;
                    }
                }
            }
        }

        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (children.size() >= 2
                && (orientation == null || orientation.isEmpty())
                && context.isEnabled(ORIENTATION)) {
            // See if at least one of the children, except the last one, sets layout_width
            // to match_parent (or fill_parent), in an implicitly horizontal layout, since
            // that might mean the last child won't be visible. This is a source of confusion
            // for new Android developers.
            boolean maxWidthSet = false;
            Iterator<Element> iterator = children.iterator();
            while (iterator.hasNext()) {
                Element child = iterator.next();
                if (!iterator.hasNext()) { // Don't check the last one
                    break;
                }
                String width = child.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
                if (VALUE_MATCH_PARENT.equals(width) || VALUE_FILL_PARENT.equals(width)) {
                    // Also check that weights are not set here; this affects the computation
                    // a bit and the child may not fill up the whole linear layout
                    if (!child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT)) {
                        maxWidthSet = true;
                        break;
                    }
                }
            }
            if (maxWidthSet && !element.hasAttribute(ATTR_STYLE)) {
                String message =
                        "Wrong orientation? No orientation specified, and the default "
                                + "is horizontal, yet this layout has multiple children where at "
                                + "least one has `layout_width=\"match_parent\"`";
                context.report(
                        ORIENTATION,
                        element,
                        context.getNameLocation(element),
                        message,
                        createOrientationFixes());
            }
        } else if (children.isEmpty()
                && (orientation == null || orientation.isEmpty())
                && context.isEnabled(ORIENTATION)
                && element.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            boolean ignore;
            if (element.hasAttribute(ATTR_STYLE)) {
                List<ResourceValue> values =
                        Lint.getStyleAttributes(
                                context.isGlobalAnalysis()
                                        ? context.getMainProject()
                                        : context.getProject(),
                                context.getClient(),
                                element.getAttribute(ATTR_STYLE),
                                ANDROID_URI,
                                ATTR_ORIENTATION,
                                false);
                ignore = values == null || !values.isEmpty();
            } else {
                ignore = false;
            }
            if (!ignore) {
                String message =
                        "No orientation specified, and the default is horizontal. "
                                + "This is a common source of bugs when children are added dynamically.";
                context.report(
                        ORIENTATION,
                        element,
                        context.getNameLocation(element),
                        message,
                        createOrientationFixes());
            }
        }

        if (context.isEnabled(BASELINE_WEIGHTS)
                && weightChild != null
                && !VALUE_VERTICAL.equals(orientation)
                && !element.hasAttributeNS(ANDROID_URI, ATTR_BASELINE_ALIGNED)) {
            // See if all the children are layouts
            boolean allChildrenAreLayouts = !children.isEmpty();
            SdkInfo sdkInfo = context.getClient().getSdkInfo(context.getProject());
            for (Element child : children) {
                String tagName = child.getTagName();
                if (!(sdkInfo.isLayout(tagName)
                                // RadioGroup is a layout, but one which possibly should be base
                                // aligned
                                && !tagName.equals(RADIO_GROUP)
                        // Consider <fragment> tags as layouts for the purposes of this check
                        || VIEW_FRAGMENT.equals(tagName)
                        // Ditto for <include> tags
                        || VIEW_INCLUDE.equals(tagName))) {
                    allChildrenAreLayouts = false;
                }
            }
            if (allChildrenAreLayouts) {
                LintFix fix =
                        fix().set(ANDROID_URI, ATTR_BASELINE_ALIGNED, VALUE_FALSE)
                                .autoFix()
                                .build();
                context.report(
                        BASELINE_WEIGHTS,
                        element,
                        context.getNameLocation(element),
                        "Set `android:baselineAligned=\"false\"` on this element for better performance",
                        fix);
            }
        }

        if (context.isEnabled(INEFFICIENT_WEIGHT) && weightChild != null && !multipleWeights) {
            String dimension;
            if (VALUE_VERTICAL.equals(orientation)) {
                dimension = ATTR_LAYOUT_HEIGHT;
            } else {
                dimension = ATTR_LAYOUT_WIDTH;
            }
            Attr sizeNode = weightChild.getAttributeNodeNS(ANDROID_URI, dimension);
            String size = sizeNode != null ? sizeNode.getValue() : "(undefined)";
            if (sizeNode == null && weightChild.hasAttribute(ATTR_STYLE)) {
                String style = weightChild.getAttribute(ATTR_STYLE);
                List<ResourceValue> sizes =
                        Lint.getStyleAttributes(
                                context.isGlobalAnalysis()
                                        ? context.getMainProject()
                                        : context.getProject(),
                                context.getClient(),
                                style,
                                ANDROID_URI,
                                dimension,
                                false);
                if (sizes != null) {
                    for (ResourceValue value : sizes) {
                        String v = value.getValue();
                        if (v != null) {
                            size = v;
                            if (v.startsWith("0")) {
                                break;
                            }
                        }
                    }
                }
            }
            if (!size.startsWith("0")) {
                String msg =
                        String.format(
                                "Use a `%1$s` of `0dp` instead of `%2$s` for better performance",
                                dimension, size);
                context.report(
                        INEFFICIENT_WEIGHT,
                        weightChild,
                        context.getElementLocation(weightChild, sizeNode, null, null),
                        msg);
            }
        }

        if (context.isEnabled(WRONG_0DP)) {
            checkWrong0Dp(context, element, children);
        }
    }

    @NonNull
    private LintFix createOrientationFixes() {
        LintFix horizontal =
                fix().name("Set orientation=\"horizontal\" (default)")
                        .set(ANDROID_URI, ATTR_ORIENTATION, VALUE_HORIZONTAL)
                        .build();
        LintFix vertical =
                fix().name("Set orientation=\"vertical\" (changes layout)")
                        .set(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL)
                        .build();
        return fix().alternatives(horizontal, vertical);
    }

    private static void checkWrong0Dp(XmlContext context, Element element, List<Element> children) {
        boolean isVertical = false;
        String orientation = element.getAttributeNS(ANDROID_URI, ATTR_ORIENTATION);
        if (VALUE_VERTICAL.equals(orientation)) {
            isVertical = true;
        }

        for (Element child : children) {
            String tagName = child.getTagName();
            if (tagName.equals(VIEW)) {
                // Might just used for spacing
                return;
            }
            if (tagName.indexOf('.') != -1 || tagName.equals(VIEW_TAG)) {
                // Custom views might perform their own dynamic sizing or ignore the layout
                // attributes all together
                return;
            }

            boolean hasWeight = child.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT);

            Attr widthNode = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            Attr heightNode = child.getAttributeNodeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            boolean noWidth = false;
            boolean noHeight = false;
            if (widthNode != null && widthNode.getValue().startsWith("0")) {
                noWidth = true;
            }
            if (heightNode != null && heightNode.getValue().startsWith("0")) {
                noHeight = true;
            } else if (!noWidth) {
                return;
            }

            if (child.getTagName().equals(SPACE)) {
                return;
            }

            // If you're specifying 0dp for both the width and height you are probably
            // trying to hide it deliberately
            if (noWidth && noHeight) {
                return;
            }

            if (noWidth) {
                if (!hasWeight) {
                    context.report(
                            WRONG_0DP,
                            widthNode,
                            context.getLocation(widthNode),
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_weight`");
                } else if (isVertical) {
                    context.report(
                            WRONG_0DP,
                            widthNode,
                            context.getLocation(widthNode),
                            "Suspicious size: this will make the view invisible, probably "
                                    + "intended for `layout_height`");
                }
            } else {
                if (!hasWeight) {
                    context.report(
                            WRONG_0DP,
                            widthNode,
                            context.getLocation(heightNode),
                            "Suspicious size: this will make the view invisible, should be "
                                    + "used with `layout_weight`");
                } else if (!isVertical) {
                    context.report(
                            WRONG_0DP,
                            widthNode,
                            context.getLocation(heightNode),
                            "Suspicious size: this will make the view invisible, probably "
                                    + "intended for `layout_width`");
                }
            }
        }
    }
}
