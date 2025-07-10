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
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PARENT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.ATTR_TILE_MODE;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.DRAWABLE_PREFIX;
import static com.android.SdkConstants.NULL_RESOURCE;
import static com.android.SdkConstants.STYLE_RESOURCE_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_BITMAP;
import static com.android.SdkConstants.TAG_STYLE;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.TRANSPARENT_COLOR;
import static com.android.SdkConstants.VALUE_DISABLED;
import static com.android.ide.common.resources.ResourcesUtil.resourceNameToFieldName;
import static com.android.tools.lint.detector.api.Lint.endsWith;
import static com.android.tools.lint.detector.api.Lint.getMethodName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.ResourceReference;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.android.utils.SdkUtils;
import com.intellij.psi.PsiClass;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Check which looks for overdraw problems where view areas are painted and then painted over,
 * meaning that the bottom paint operation is a waste of time.
 */
public class OverdrawDetector extends LayoutDetector implements SourceCodeScanner {
    private static final String SET_THEME = "setTheme";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "Overdraw",
                    "Overdraw: Painting regions more than once",
                    "If you set a background drawable on a root view, then you should use a "
                            + "custom theme where the theme background is null. Otherwise, the theme background "
                            + "will be painted first, only to have your custom background completely cover it; "
                            + "this is called \"overdraw\".\n"
                            + "\n"
                            + "NOTE: This detector relies on figuring out which layouts are associated with "
                            + "which activities based on scanning the Java code, and it's currently doing that "
                            + "using an inexact pattern matching algorithm. Therefore, it can incorrectly "
                            + "conclude which activity the layout is associated with and then wrongly complain "
                            + "that a background-theme is hidden.\n"
                            + "\n"
                            + "If you want your custom background on multiple pages, then you should consider "
                            + "making a custom theme with your custom background and just using that theme "
                            + "instead of a root element background.\n"
                            + "\n"
                            + "Of course it's possible that your custom drawable is translucent and you want "
                            + "it to be mixed with the background. However, you will get better performance "
                            + "if you pre-mix the background with your drawable and use that resulting image or "
                            + "color as a custom theme background instead.\n",
                    Category.PERFORMANCE,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            OverdrawDetector.class,
                            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE, Scope.ALL_RESOURCE_FILES)));

    /** Mapping from FQN activity names to theme names registered in the manifest */
    private Map<String, String> activityToTheme;

    /** The default theme declared in the manifest, or null */
    private String manifestTheme;

    /** Mapping from layout name (not including {@code @layout/} prefix) to activity FQN */
    private Map<String, List<String>> layoutToActivity;

    /** List of theme names registered in the project which have blank backgrounds */
    private List<String> blankThemes;

    /**
     * List of drawable resources that are not flagged for overdraw (XML drawables except for {@code
     * <bitmap>} drawables without tiling)
     */
    private List<String> validDrawables;

    /**
     * List of pairs of (location, background drawable) corresponding to root elements in layouts
     * that define a given background drawable. These should be checked to see if they are painting
     * on top of a non-transparent theme.
     */
    private List<Pair<Location, String>> rootAttributes;

    /** Constructs a new {@link OverdrawDetector} */
    public OverdrawDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        // Look in layouts for drawable resources
        return super.appliesTo(folderType)
                // and in resource files for theme definitions
                || folderType == ResourceFolderType.VALUES
                // and in drawable files for bitmap tiling modes
                || folderType == ResourceFolderType.DRAWABLE;
    }

    /** Is the given theme a "blank" theme (one not painting its background) */
    private boolean isBlankTheme(String name) {
        if (name.startsWith("@android:style/Theme_")) {
            if (name.contains("NoFrame")
                    || name.contains("Theme_Wallpaper")
                    || name.contains("Theme_Holo_Wallpaper")
                    || name.contains("Theme_Translucent")
                    || name.contains("Theme_Dialog_NoFrame")
                    || name.contains("Theme_Holo_Dialog_Alert")
                    || name.contains("Theme_Holo_Light_Dialog_Alert")
                    || name.contains("Theme_Dialog_Alert")
                    || name.contains("Theme_Panel")
                    || name.contains("Theme_Light_Panel")
                    || name.contains("Theme_Holo_Panel")
                    || name.contains("Theme_Holo_Light_Panel")) {
                return true;
            }
        }

        return blankThemes != null && blankThemes.contains(name);
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (rootAttributes != null) {
            for (Pair<Location, String> pair : rootAttributes) {
                Location location = pair.getFirst();

                Object clientData = location.getClientData();
                if (clientData instanceof Node) {
                    if (context.getDriver().isSuppressed(null, ISSUE, (Node) clientData)) {
                        return;
                    }
                }

                String layoutName = location.getFile().getName();
                if (endsWith(layoutName, DOT_XML)) {
                    layoutName = layoutName.substring(0, layoutName.length() - DOT_XML.length());
                }

                String theme = getTheme(context, layoutName);
                if (theme == null || !isBlankTheme(theme)) {
                    String drawable = pair.getSecond();
                    String message =
                            String.format(
                                    "Possible overdraw: Root element paints background `%1$s` with "
                                            + "a theme that also paints a background (inferred theme is `%2$s`)",
                                    drawable, theme);
                    // TODO: Compute applicable scope node
                    context.report(ISSUE, location, message);
                }
            }
        }
    }

    /** Return the theme to be used for the given layout */
    private String getTheme(Context context, String layoutName) {
        if (activityToTheme != null && layoutToActivity != null) {
            List<String> activities = layoutToActivity.get(layoutName);
            if (activities != null) {
                for (String activity : activities) {
                    String theme = activityToTheme.get(activity);
                    if (theme != null) {
                        return theme;
                    }
                }
            }
        }

        if (manifestTheme != null) {
            return manifestTheme;
        }

        Project project =
                context.isGlobalAnalysis() ? context.getMainProject() : context.getProject();
        int apiLevel = project.getTargetSdk();
        if (apiLevel == -1) {
            apiLevel = project.getMinSdk();
        }

        if (apiLevel >= 11) {
            return "@android:style/Theme.Holo";
        } else {
            return "@android:style/Theme";
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        // Ignore tools:background and any other custom attribute that isn't actually the
        // android View background attribute
        if (!ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return;
        }

        // Only consider the root element's background
        Element documentElement = attribute.getOwnerDocument().getDocumentElement();
        if (documentElement == attribute.getOwnerElement()) {
            // If the drawable is a non-repeated pattern then the overdraw might be
            // intentional since the image isn't covering the whole screen
            String background = attribute.getValue();
            if (validDrawables != null && validDrawables.contains(background)) {
                return;
            }

            if (background.equals(TRANSPARENT_COLOR) || background.equals(NULL_RESOURCE)) {
                return;
            }

            if (background.startsWith("@android:drawable/")) {
                // We haven't had a chance to study the builtin drawables the way we
                // check the project local ones in scanBitmap() and beforeCheckFile(),
                // but many of these are not bitmaps, so ignore these
                return;
            }

            String name = context.file.getName();
            if (name.contains("list_") || name.contains("_item")) {
                // Canonical list_item layout name: don't warn about these, it's
                // pretty common to want to paint custom list item backgrounds
                return;
            }

            if (!context.getProject().getReportIssues()) {
                // If this is a library project not being analyzed, ignore it
                return;
            }

            Location location = context.getLocation(attribute);
            location.setData(attribute);
            if (rootAttributes == null) {
                rootAttributes = new ArrayList<>();
            }
            rootAttributes.add(Pair.of(location, attribute.getValue()));

            String activity = documentElement.getAttributeNS(TOOLS_URI, ATTR_CONTEXT);
            if (activity != null && !activity.isEmpty()) {
                if (activity.startsWith(".")) {
                    activity = context.getProject().getPackage() + activity;
                }
                registerLayoutActivity(SdkUtils.getLayoutName(context.file), activity);
            }
        }
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(
                // Layouts: Look for background attributes on root elements for possible overdraw
                ATTR_BACKGROUND);
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                // Manifest: Look at theme registrations
                TAG_ACTIVITY,
                TAG_APPLICATION,

                // Resource files: Look at theme definitions
                TAG_STYLE,

                // Bitmaps
                TAG_BITMAP);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (endsWith(context.file.getName(), DOT_XML)) {
            // Drawable XML files should not be considered for overdraw, except for <bitmap>'s.
            // The bitmap elements are handled in the scanBitmap() method; it will clear
            // out anything added by this method.
            File parent = context.file.getParentFile();
            ResourceFolderType type = ResourceFolderType.getFolderType(parent.getName());
            if (type == ResourceFolderType.DRAWABLE) {
                if (validDrawables == null) {
                    validDrawables = new ArrayList<>();
                }
                String resource = getDrawableResource(context.file);
                validDrawables.add(resource);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (tag.equals(TAG_STYLE)) {
            scanTheme(element);
        } else if (tag.equals(TAG_ACTIVITY)) {
            scanActivity(context, element);
        } else if (tag.equals(TAG_APPLICATION)) {
            if (element.hasAttributeNS(ANDROID_URI, ATTR_THEME)) {
                manifestTheme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
            }
        } else if (tag.equals(TAG_BITMAP)) {
            scanBitmap(context, element);
        }
    }

    private static String getDrawableResource(File drawableFile) {
        String resource = drawableFile.getName();
        if (endsWith(resource, DOT_XML)) {
            resource = resource.substring(0, resource.length() - DOT_XML.length());
        }
        return DRAWABLE_PREFIX + resource;
    }

    private void scanBitmap(Context context, Element element) {
        String tileMode = element.getAttributeNS(ANDROID_URI, ATTR_TILE_MODE);
        if (!(tileMode.equals(VALUE_DISABLED) || tileMode.isEmpty())) {
            if (validDrawables != null) {
                String resource = getDrawableResource(context.file);
                validDrawables.remove(resource);
            }
        }
    }

    private void scanActivity(Context context, Element element) {
        String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (name.indexOf('$') != -1) {
            name = name.replace('$', '.');
        }
        if (name.startsWith(".")) {
            String pkg = context.getProject().getPackage();
            if (pkg != null && !pkg.isEmpty()) {
                name = pkg + name;
            }
        }

        String theme = element.getAttributeNS(ANDROID_URI, ATTR_THEME);
        if (theme != null && !theme.isEmpty()) {
            if (activityToTheme == null) {
                activityToTheme = new HashMap<>();
            }
            activityToTheme.put(name, resourceNameToFieldName(theme));
        }
    }

    private void scanTheme(Element element) {
        // Look for theme definitions, and record themes that provide a null background.
        String styleName = element.getAttribute(ATTR_NAME);
        String parent = element.getAttribute(ATTR_PARENT);
        if (parent == null) {
            // Eclipse DOM workaround
            parent = "";
        }

        if (parent.isEmpty()) {
            int index = styleName.lastIndexOf('.');
            if (index != -1) {
                parent = styleName.substring(0, index);
            }
        }
        parent = parent.replace('.', '_');

        String resource = STYLE_RESOURCE_PREFIX + resourceNameToFieldName(styleName);

        NodeList items = element.getChildNodes();
        for (int i = 0, n = items.getLength(); i < n; i++) {
            if (items.item(i).getNodeType() == Node.ELEMENT_NODE) {
                Element item = (Element) items.item(i);
                String name = item.getAttribute(ATTR_NAME);
                if (name.equals("android:windowBackground")) {
                    NodeList textNodes = item.getChildNodes();
                    for (int j = 0, m = textNodes.getLength(); j < m; j++) {
                        Node textNode = textNodes.item(j);
                        if (textNode.getNodeType() == Node.TEXT_NODE) {
                            String text = textNode.getNodeValue();
                            String trim = text.trim();
                            if (!trim.isEmpty()) {
                                if (trim.equals(NULL_RESOURCE)
                                        || trim.equals(TRANSPARENT_COLOR)
                                        || validDrawables != null
                                                && validDrawables.contains(trim)) {
                                    if (blankThemes == null) {
                                        blankThemes = new ArrayList<>();
                                    }
                                    blankThemes.add(resource);
                                }
                            }
                        }
                    }

                    return;
                }
            }
        }

        if (isBlankTheme(parent)) {
            if (blankThemes == null) {
                blankThemes = new ArrayList<>();
            }
            blankThemes.add(resource);
        }
    }

    private void registerLayoutActivity(String layout, String classFqn) {
        if (layoutToActivity == null) {
            layoutToActivity = new HashMap<>();
        }
        List<String> list = layoutToActivity.get(layout);
        if (list == null) {
            list = new ArrayList<>();
            layoutToActivity.put(layout, list);
        }
        list.add(classFqn);
    }

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!context.getProject().getReportIssues()) {
            return;
        }
        String name = declaration.getQualifiedName();
        if (name != null) {
            declaration.accept(new OverdrawVisitor(name, declaration));
        }
    }

    private class OverdrawVisitor extends AbstractUastVisitor {
        private final String name;
        private final PsiClass cls;

        public OverdrawVisitor(String name, PsiClass cls) {
            this.name = name;
            this.cls = cls;
        }

        @Override
        public boolean visitClass(UClass node) {
            // Don't go into inner classes
            if (cls.equals(node.getJavaPsi())) {
                return true;
            }

            return super.visitClass(node);
        }

        @Override
        public boolean visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            ResourceReference reference = ResourceReference.get(node);
            if (reference != null && reference.getType() == ResourceType.LAYOUT) {
                registerLayoutActivity(reference.getName(), name);
            }

            return super.visitSimpleNameReferenceExpression(node);
        }

        @Override
        public boolean visitCallExpression(UCallExpression node) {
            if (SET_THEME.equals(getMethodName(node)) && node.getValueArgumentCount() == 1) {
                // Look at argument
                UExpression arg = node.getValueArguments().get(0);
                ResourceReference reference = ResourceReference.get(arg);
                if (reference != null && reference.getType() == ResourceType.STYLE) {
                    String style = reference.getName();
                    if (activityToTheme == null) {
                        activityToTheme = new HashMap<>();
                    }
                    activityToTheme.put(name, STYLE_RESOURCE_PREFIX + style);
                }
            }
            return super.visitCallExpression(node);
        }
    }
}
