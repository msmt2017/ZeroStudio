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
import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.ATTR_ROUND_ICON;
import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_GIF;
import static com.android.SdkConstants.DOT_JPEG;
import static com.android.SdkConstants.DOT_JPG;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.DRAWABLE_FOLDER;
import static com.android.SdkConstants.DRAWABLE_HDPI;
import static com.android.SdkConstants.DRAWABLE_LDPI;
import static com.android.SdkConstants.DRAWABLE_MDPI;
import static com.android.SdkConstants.DRAWABLE_PREFIX;
import static com.android.SdkConstants.DRAWABLE_XHDPI;
import static com.android.SdkConstants.DRAWABLE_XXHDPI;
import static com.android.SdkConstants.MIPMAP_FOLDER;
import static com.android.SdkConstants.MIPMAP_PREFIX;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_ACTIVITY_ALIAS;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;
import static com.android.tools.lint.detector.api.Constraints.minSdkAtLeast;
import static com.android.tools.lint.detector.api.Constraints.minSdkLessThan;
import static com.android.tools.lint.detector.api.Lint.endsWith;
import static com.android.tools.lint.detector.api.Lint.getMethodName;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.client.api.UastParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastUtils;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastVisitor;
import org.w3c.dom.Element;

/**
 * Checks for common icon problems, such as wrong icon sizes, placing icons in the density
 * independent drawable folder, etc.
 */
public class IconDetector extends Detector implements XmlScanner, SourceCodeScanner {

    // TODO: Use the new merged manifest model

    private static final boolean INCLUDE_LDPI;

    private static final String KEY_MIN_API = "minSdk";

    static {
        boolean includeLdpi = false;

        String value = System.getenv("ANDROID_LINT_INCLUDE_LDPI");
        if (value != null) {
            includeLdpi = Boolean.valueOf(value);
        }
        INCLUDE_LDPI = includeLdpi;
    }

    /** Pattern for the expected density folders to be found in the project */
    private static final Pattern DENSITY_PATTERN =
            Pattern.compile(
                    "^drawable-(nodpi|xxxhdpi|xxhdpi|xhdpi|hdpi|mdpi"
                            + (INCLUDE_LDPI ? "|ldpi" : "")
                            + ")$");

    /** Pattern for icon names that include their dp size as part of the name */
    private static final Pattern DP_NAME_PATTERN = Pattern.compile(".+_(\\d+)dp\\.[a-zA-Z]+");

    /** Cache for {@link #getRequiredDensityFolders(Context)} */
    private List<String> cachedRequiredDensities;
    /** Cache key for {@link #getRequiredDensityFolders(Context)} */
    private Project cachedDensitiesForProject;

    // TODO: Convert this over to using the Density enum and FolderConfiguration
    // for qualifier lookup
    private static final String[] DENSITY_QUALIFIERS =
            new String[] {
                "-ldpi", "-mdpi", "-hdpi", "-xhdpi", "-xxhdpi", "-xxxhdpi",
            };

    /**
     * Scope needed to detect the types of icons (which involves scanning .java files, the manifest,
     * menu files etc to see how icons are used
     */
    private static final EnumSet<Scope> ICON_TYPE_SCOPE =
            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE, Scope.MANIFEST);

    private static final Implementation IMPLEMENTATION_JAVA =
            new Implementation(IconDetector.class, ICON_TYPE_SCOPE);

    private static final Implementation IMPLEMENTATION_RES_ONLY =
            new Implementation(IconDetector.class, Scope.ALL_RESOURCES_SCOPE);

    /** Wrong icon size according to published conventions */
    public static final Issue ICON_EXPECTED_SIZE =
            Issue.create(
                            "IconExpectedSize",
                            "Icon has incorrect size",
                            "There are predefined sizes (for each density) for launcher icons. You "
                                    + "should follow these conventions to make sure your icons fit in with the "
                                    + "overall look of the platform.",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION_JAVA)
                    // Still some potential false positives:
                    .setEnabledByDefault(false)
                    .addMoreInfo("https://d.android.com/r/studio-ui/designer/material/iconography");

    /** Inconsistent dip size across densities */
    public static final Issue ICON_DIP_SIZE =
            Issue.create(
                    "IconDipSize",
                    "Icon density-independent size validation",
                    "Checks the all icons which are provided in multiple densities, all compute to "
                            + "roughly the same density-independent pixel (`dip`) size. This catches errors where "
                            + "images are either placed in the wrong folder, or icons are changed to new sizes "
                            + "but some folders are forgotten.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION_RES_ONLY);

    /** Images in res/drawable folder */
    public static final Issue ICON_LOCATION =
            Issue.create(
                            "IconLocation",
                            "Image defined in density-independent drawable folder",
                            "The res/drawable folder is intended for density-independent graphics such as "
                                    + "shapes defined in XML. For bitmaps, move it to `drawable-mdpi` and consider "
                                    + "providing higher and lower resolution versions in `drawable-ldpi`, `drawable-hdpi` "
                                    + "and `drawable-xhdpi`. If the icon **really** is density independent (for example "
                                    + "a solid color) you can place it in `drawable-nodpi`.",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION_RES_ONLY)
                    .addMoreInfo(
                            "https://developer.android.com/guide/practices/screens_support.html");

    /** Missing density versions of image */
    public static final Issue ICON_DENSITIES =
            Issue.create(
                            "IconDensities",
                            "Icon densities validation",
                            "Icons will look best if a custom version is provided for each of the "
                                    + "major screen density classes (low, medium, high, extra high). "
                                    + "This lint check identifies icons which do not have complete coverage "
                                    + "across the densities.\n"
                                    + "\n"
                                    + "Low density is not really used much anymore, so this check ignores "
                                    + "the ldpi density. To force lint to include it, set the environment "
                                    + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                                    + "current density usage, see "
                                    + "https://developer.android.com/about/dashboards",
                            Category.ICONS,
                            4,
                            Severity.WARNING,
                            IMPLEMENTATION_RES_ONLY)
                    .addMoreInfo(
                            "https://developer.android.com/guide/practices/screens_support.html");

    /** Missing density folders */
    public static final Issue ICON_MISSING_FOLDER =
            Issue.create(
                            "IconMissingDensityFolder",
                            "Missing density folder",
                            "Icons will look best if a custom version is provided for each of the "
                                    + "major screen density classes (low, medium, high, extra-high, extra-extra-high). "
                                    + "This lint check identifies folders which are missing, such as `drawable-hdpi`.\n"
                                    + "\n"
                                    + "Low density is not really used much anymore, so this check ignores "
                                    + "the ldpi density. To force lint to include it, set the environment "
                                    + "variable `ANDROID_LINT_INCLUDE_LDPI=true`. For more information on "
                                    + "current density usage, see "
                                    + "https://developer.android.com/about/dashboards",
                            Category.ICONS,
                            3,
                            Severity.WARNING,
                            IMPLEMENTATION_RES_ONLY)
                    .addMoreInfo(
                            "https://developer.android.com/guide/practices/screens_support.html");

    /** Using .gif bitmaps */
    public static final Issue GIF_USAGE =
            Issue.create(
                            "GifUsage",
                            "Using `.gif` format for bitmaps is discouraged",
                            "The `.gif` file format is discouraged. Consider using `.png` (preferred) "
                                    + "or `.jpg` (acceptable) instead.",
                            Category.ICONS,
                            5,
                            Severity.WARNING,
                            IMPLEMENTATION_RES_ONLY)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/resources/drawable-resource.html#Bitmap");

    /** Duplicated icons across different names */
    public static final Issue DUPLICATES_NAMES =
            Issue.create(
                    "IconDuplicates",
                    "Duplicated icons under different names",
                    "If an icon is repeated under different names, you can consolidate and just "
                            + "use one of the icons and delete the others to make your application smaller. "
                            + "However, duplicated icons usually are not intentional and can sometimes point "
                            + "to icons that were accidentally overwritten or accidentally not updated.",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION_RES_ONLY);

    /** Duplicated contents across configurations for a given name */
    public static final Issue DUPLICATES_CONFIGURATIONS =
            Issue.create(
                    "IconDuplicatesConfig",
                    "Identical bitmaps across various configurations",
                    "If an icon is provided under different configuration parameters such as "
                            + "`drawable-hdpi` or `-v11`, they should typically be different. This detector "
                            + "catches cases where the same icon is provided in different configuration folder "
                            + "which is usually not intentional.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION_RES_ONLY);

    /** Icons appearing in both -nodpi and a -Ndpi folder */
    public static final Issue ICON_NODPI =
            Issue.create(
                    "IconNoDpi",
                    "Icon appears in both `-nodpi` and dpi folders",
                    "Bitmaps that appear in `drawable-nodpi` folders will not be scaled by the "
                            + "Android framework. If a drawable resource of the same name appears **both** in "
                            + "a `-nodpi` folder as well as a dpi folder such as `drawable-hdpi`, then "
                            + "the behavior is ambiguous and probably not intentional. Delete one or the "
                            + "other, or use different names for the icons.",
                    Category.ICONS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION_RES_ONLY);

    /** Drawables provided as both .9.png and .png files */
    public static final Issue ICON_MIX_9PNG =
            Issue.create(
                    "IconMixedNinePatch",
                    "Clashing PNG and 9-PNG files",
                    "If you accidentally name two separate resources `file.png` and `file.9.png`, "
                            + "the image file and the nine patch file will both map to the same drawable "
                            + "resource, `@drawable/file`, which is probably not what was intended.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    IMPLEMENTATION_RES_ONLY);

    /** Icons appearing as both drawable xml files and bitmaps */
    public static final Issue ICON_XML_AND_PNG =
            Issue.create(
                    "IconXmlAndPng",
                    "Icon is specified both as `.xml` file and as a bitmap",
                    "If a drawable resource appears as an `.xml` file in the `drawable/` folder, "
                            + "it's usually not intentional for it to also appear as a bitmap using the "
                            + "same name; generally you expect the drawable XML file to define states "
                            + "and each state has a corresponding drawable bitmap.",
                    Category.ICONS,
                    7,
                    Severity.WARNING,
                    IMPLEMENTATION_RES_ONLY);

    /** Wrong filename according to the format */
    public static final Issue ICON_EXTENSION =
            Issue.create(
                    "IconExtension",
                    "Icon format does not match the file extension",
                    "Ensures that icons have the correct file extension (e.g. a `.png` file is "
                            + "really in the PNG format and not for example a GIF file named `.png`).",
                    Category.ICONS,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION_RES_ONLY);

    /** Wrong color of notification icon. */
    public static final Issue ICON_COLORS =
            Issue.create(
                    "IconColors",
                    "Icon colors do not follow the recommended visual style",
                    "Notification icons and Action Bar icons should only white and shades of gray. "
                            + "See the Android Design Guide for more details. "
                            + "Note that the way Lint decides whether an icon is an action bar icon or "
                            + "a notification icon is based on the filename prefix: `ic_menu_` for "
                            + "action bar icons, `ic_stat_` for notification icons etc. These correspond "
                            + "to the naming conventions documented in "
                            + "https://d.android.com/r/studio-ui/designer/material/iconography",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION_JAVA);

    /** Wrong launcher icon shape */
    public static final Issue ICON_LAUNCHER_SHAPE =
            Issue.create(
                    "IconLauncherShape",
                    "The launcher icon shape should use a distinct silhouette",
                    "According to the Android Design Guide "
                            + "(https://d.android.com/r/studio-ui/designer/material/iconography) "
                            + "your launcher icons should \"use a distinct silhouette\", "
                            + "a \"three-dimensional, front view, with a slight perspective as if viewed "
                            + "from above, so that users perceive some depth.\"\n"
                            + "\n"
                            + "The unique silhouette implies that your launcher icon should not be a filled "
                            + "square.",
                    Category.ICONS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION_JAVA);

    /** Raster image is required for notification icon at API < 21. */
    public static final Issue NOTIFICATION_ICON_COMPATIBILITY =
            Issue.create(
                    "NotificationIconCompatibility",
                    "Notification Icon Compatibility",
                    "Notification icons should define a raster image to support Android versions below 5.0 (API 21). "
                            + "Note that the way Lint decides whether an icon is a notification icon is based on the filename prefix "
                            + "`ic_stat_`. This corresponds to the naming convention documented in "
                            + "https://d.android.com/r/studio-ui/designer/material/iconography",
                    Category.CORRECTNESS,
                    6,
                    Severity.WARNING,
                    IMPLEMENTATION_JAVA);

    /** Switch to webp? */
    public static final Issue WEBP_ELIGIBLE =
            Issue.create(
                            "ConvertToWebp",
                            "Convert to WebP",
                            "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                                    + "it supports transparency and lossless conversion as well. Note that there is a "
                                    + "quickfix in the IDE which lets you perform conversion.\n"
                                    + "\n"
                                    + "Previously, launcher icons were required to be in the PNG format but that "
                                    + "restriction is no longer there, so lint now flags these.",
                            Category.ICONS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION_JAVA)
                    // Actual performance being benchmarked
                    .setEnabledByDefault(false);

    /** Webp unsupported? */
    public static final Issue WEBP_UNSUPPORTED =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, such as lossless "
                            + "encoding and transparency, requires Android 4.2.1 (API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION_JAVA);

    /** Constructs a new {@link IconDetector} check */
    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        launcherIcons = null;
        actionBarIcons = null;
        notificationIcons = null;
        roundIcons = null;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            // If this is a library project not being analyzed, ignore it
            return;
        }

        checkResourceFolder(context, context.getProject());
    }

    private void checkResourceFolder(Context context, @NonNull Project project) {
        // The resource files corresponding  of the notification icons. The keys are icon names.
        // The values are the icon files most compatible with old Android versions.
        Map<String, File> notificationIconFiles = null;

        List<File> resourceFolders = project.getResourceFolders();
        for (File res : resourceFolders) {
            File[] folders = res.listFiles();
            if (folders != null) {
                boolean checkFolders =
                        context.isEnabled(ICON_DENSITIES)
                                || context.isEnabled(ICON_MISSING_FOLDER)
                                || context.isEnabled(ICON_NODPI)
                                || context.isEnabled(ICON_MIX_9PNG)
                                || context.isEnabled(ICON_XML_AND_PNG);
                boolean checkDipSizes = context.isEnabled(ICON_DIP_SIZE);
                boolean checkDuplicates =
                        context.isEnabled(DUPLICATES_NAMES)
                                || context.isEnabled(DUPLICATES_CONFIGURATIONS);
                boolean checkWebp = context.isEnabled(WEBP_ELIGIBLE);

                Map<File, Dimension> pixelSizes = null;
                Map<File, Long> fileSizes = null;
                if (checkDipSizes || checkDuplicates || checkWebp) {
                    pixelSizes = new HashMap<>();
                    fileSizes = new HashMap<>();
                }
                if (context.isEnabled(NOTIFICATION_ICON_COMPATIBILITY)
                        && context.getProject().getMinSdk() < 21) {
                    notificationIconFiles = new HashMap<>();
                }
                Map<File, Set<String>> folderToNames = new HashMap<>();
                Map<File, Set<String>> nonDpiFolderNames = new HashMap<>();
                for (File folder : folders) {
                    String folderName = folder.getName();
                    if (folderName.startsWith(DRAWABLE_FOLDER)
                            || folderName.startsWith(MIPMAP_FOLDER)) {
                        File[] files = folder.listFiles();
                        if (files != null) {
                            checkDrawableDir(
                                    context,
                                    folder,
                                    files,
                                    pixelSizes,
                                    fileSizes,
                                    notificationIconFiles);

                            if (checkFolders && DENSITY_PATTERN.matcher(folderName).matches()) {
                                Set<String> names = new HashSet<>(files.length);
                                for (File f : files) {
                                    String name = f.getName();
                                    if (isDrawableFile(name)) {
                                        names.add(name);
                                    }
                                }
                                folderToNames.put(folder, names);
                            } else if (checkFolders) {
                                Set<String> names = new HashSet<>(files.length);
                                for (File f : files) {
                                    String name = f.getName();
                                    if (isDrawableFile(name)) {
                                        names.add(name);
                                    }
                                }
                                nonDpiFolderNames.put(folder, names);
                            }
                        }
                    }
                }

                if (checkDipSizes) {
                    checkDipSizes(context, pixelSizes);
                }

                if (checkDuplicates) {
                    checkDuplicates(context, pixelSizes, fileSizes);
                }

                if (checkFolders && !folderToNames.isEmpty()) {
                    checkDensities(context, res, folderToNames, nonDpiFolderNames);
                }

                // Report webp-conversion-eligible images
                // We only check for 18 here, which supports transparency and lossless
                // encoding. We could also offer this for API 15, but in that case we'd need to
                // (1) skip images with alpha, and (2) make it clear that only lossy conversion
                // should be used.
                // (It's Android 4.2.1 which starts supporting transparent WebP, whereas
                // API level 17 corresponds to 4.2 and API level 18 corresponds to 4.3; we can't
                // use API level 17 to determine whether WebP is safe since that also includes
                // 4.2.0)

                if (checkWebp) {
                    // (1) See if we have any png or jpeg images
                    // (2) Use the location of the largest such image
                    File largest = null;
                    long size = 0;
                    for (Map.Entry<File, Long> entry : fileSizes.entrySet()) {
                        File f = entry.getKey();
                        String name = f.getName();
                        if (endsWithIgnoreCase(name, DOT_PNG) && !endsWithIgnoreCase(name, DOT_9PNG)
                                || endsWithIgnoreCase(name, DOT_JPG)
                                || endsWithIgnoreCase(name, DOT_JPEG)) {
                            String baseName = getBaseName(name);
                            if (isAdaptiveIconLayer(baseName)) {
                                continue;
                            }

                            Long sizeLong = entry.getValue();
                            if (sizeLong != null && sizeLong > size) {
                                size = sizeLong;
                                largest = f;
                            }
                        }
                    }

                    if (largest != null) {
                        Location location = Location.create(largest);
                        String message =
                                "One or more images in this project can be converted to "
                                        + "the WebP format which typically results in smaller file sizes, "
                                        + "even for lossless conversion";
                        Incident incident = new Incident(WEBP_ELIGIBLE, location, message);
                        context.report(incident, minSdkAtLeast(18));
                    }
                }
            }
        }

        if (notificationIconFiles != null) {
            for (Map.Entry<String, File> entry : notificationIconFiles.entrySet()) {
                File file = entry.getValue();
                if (!SdkUtils.isBitmapFile(file)) {
                    String message =
                            String.format(
                                    "Notification icon %1$s has to have a raster image to support "
                                            + "Android versions below 5.0 (API 21)",
                                    entry.getKey());
                    Location location = Location.create(file);
                    Incident incident =
                            new Incident(NOTIFICATION_ICON_COMPATIBILITY, location, message);
                    context.report(incident, minSdkLessThan(21));
                }
            }
        }
    }

    /**
     * Like {@link SdkUtils#isBitmapFile(File)} but (a) operates on Strings instead of files and (b)
     * also considers XML drawables as images
     */
    public static boolean isDrawableFile(String name) {
        // endsWith(name, DOT_PNG) is also true for endsWith(name, DOT_9PNG)
        return endsWith(name, DOT_PNG)
                || endsWith(name, DOT_JPG)
                || endsWith(name, DOT_GIF)
                || endsWith(name, DOT_XML)
                || endsWith(name, DOT_JPEG)
                || endsWith(name, DOT_WEBP);
    }

    // This method looks for duplicates in the assets. This uses two pieces of information
    // (file sizes and image dimensions) to quickly reject candidates, such that it only
    // needs to check actual file contents on a small subset of the available files.
    private static void checkDuplicates(
            Context context, Map<File, Dimension> pixelSizes, Map<File, Long> fileSizes) {
        Map<Long, Set<File>> sameSizes = new HashMap<>();
        Map<Long, File> seenSizes = new HashMap<>(fileSizes.size());
        for (Map.Entry<File, Long> entry : fileSizes.entrySet()) {
            File file = entry.getKey();
            Long size = entry.getValue();
            if (seenSizes.containsKey(size)) {
                Set<File> set = sameSizes.get(size);
                if (set == null) {
                    set = new HashSet<>();
                    set.add(seenSizes.get(size));
                    sameSizes.put(size, set);
                }
                set.add(file);
            } else {
                seenSizes.put(size, file);
            }
        }

        if (sameSizes.isEmpty()) {
            return;
        }

        // Now go through the files that have the same size and check to see if we can
        // split them apart based on image dimensions
        // Note: we may not have file sizes on all the icons; in particular,
        // we don't have file sizes for ninepatch files.
        Collection<Set<File>> candidateLists = sameSizes.values();
        for (Set<File> candidates : candidateLists) {
            Map<Dimension, Set<File>> sameDimensions = new HashMap<>(candidates.size());
            List<File> noSize = new ArrayList<>();
            for (File file : candidates) {
                Dimension dimension = pixelSizes.get(file);
                if (dimension != null) {
                    Set<File> set = sameDimensions.get(dimension);
                    if (set == null) {
                        set = new HashSet<>();
                        sameDimensions.put(dimension, set);
                    }
                    set.add(file);
                } else {
                    noSize.add(file);
                }
            }

            // Files that we have no dimensions for must be compared against everything
            Collection<Set<File>> sets = sameDimensions.values();
            if (!noSize.isEmpty()) {
                if (!sets.isEmpty()) {
                    for (Set<File> set : sets) {
                        set.addAll(noSize);
                    }
                } else {
                    // Must just test the noSize elements against themselves
                    HashSet<File> noSizeSet = new HashSet<>(noSize);
                    sets = Collections.singletonList(noSizeSet);
                }
            }

            // Map from file to actual byte contents of the file.
            // We store this in a map such that for repeated files, such as noSize files
            // which can appear in multiple buckets, we only need to read them once
            Map<File, byte[]> fileContents = new HashMap<>();

            // Now we're ready for the final check where we actually check the
            // bits. We have to partition the files into buckets of files that
            // are identical.
            for (Set<File> set : sets) {
                if (set.size() < 2) {
                    continue;
                }

                // Read all files in this set and store in map
                for (File file : set) {
                    byte[] bits = fileContents.get(file);
                    if (bits == null) {
                        try {
                            bits = context.getClient().readBytes(file);
                            fileContents.put(file, bits);
                        } catch (IOException e) {
                            context.log(e, null);
                        }
                    }
                }

                // Map where the key file is known to be equal to the value file.
                // After we check individual files for equality this will be used
                // to look for transitive equality.
                Map<File, File> equal = new HashMap<>();

                // Now go and compare all the files. This isn't an efficient algorithm
                // but the number of candidates should be very small

                List<File> files = new ArrayList<>(set);
                Collections.sort(files);
                for (int i = 0; i < files.size() - 1; i++) {
                    for (int j = i + 1; j < files.size(); j++) {
                        File file1 = files.get(i);
                        File file2 = files.get(j);
                        byte[] contents1 = fileContents.get(file1);
                        byte[] contents2 = fileContents.get(file2);
                        if (contents1 == null || contents2 == null) {
                            // File couldn't be read: ignore
                            continue;
                        }
                        if (contents1.length != contents2.length) {
                            // Sizes differ: not identical.
                            // This shouldn't happen since we've already partitioned based
                            // on File.length(), but just make sure here since the file
                            // system could have lied, or cached a value that has changed
                            // if the file was just overwritten
                            continue;
                        }
                        boolean same = true;
                        for (int k = 0; k < contents1.length; k++) {
                            if (contents1[k] != contents2[k]) {
                                same = false;
                                break;
                            }
                        }
                        if (same) {
                            equal.put(file1, file2);
                        }
                    }
                }

                if (!equal.isEmpty()) {
                    Map<File, Set<File>> partitions = new HashMap<>();
                    List<Set<File>> sameSets = new ArrayList<>();
                    for (Map.Entry<File, File> entry : equal.entrySet()) {
                        File file1 = entry.getKey();
                        File file2 = entry.getValue();
                        Set<File> set1 = partitions.get(file1);
                        Set<File> set2 = partitions.get(file2);
                        if (set1 != null) {
                            set1.add(file2);
                        } else if (set2 != null) {
                            set2.add(file1);
                        } else {
                            set = new HashSet<>();
                            sameSets.add(set);
                            set.add(file1);
                            set.add(file2);
                            partitions.put(file1, set);
                            partitions.put(file2, set);
                        }
                    }

                    // We've computed the partitions of equal files. Now sort them
                    // for stable output.
                    List<List<File>> lists = new ArrayList<>();
                    for (Set<File> same : sameSets) {
                        assert !same.isEmpty();
                        ArrayList<File> sorted = new ArrayList<>(same);
                        Collections.sort(sorted);
                        lists.add(sorted);
                    }
                    // Sort overall partitions by the first item in each list
                    lists.sort((list1, list2) -> list1.get(0).compareTo(list2.get(0)));

                    // Allow one specific scenario of duplicated icon contents:
                    // Checking in different size icons (within a single density
                    // folder). For now the only pattern we recognize is the
                    // one advocated by the material design icons:
                    //   https://github.com/google/material-design-icons
                    // where the pattern is foo_<N>dp.png. (See issue 74584 for more.)
                    ListIterator<List<File>> iterator = lists.listIterator();
                    while (iterator.hasNext()) {
                        List<File> list = iterator.next();
                        boolean remove = true;
                        for (File file : list) {
                            String name = file.getName();
                            if (!DP_NAME_PATTERN.matcher(name).matches()) {
                                // One or more pattern in this list does not
                                // conform to the dp naming pattern, so
                                remove = false;
                                break;
                            }
                        }
                        if (remove) {
                            iterator.remove();
                        }
                    }

                    for (List<File> sameFiles : lists) {
                        Location location = null;
                        boolean sameNames = true;
                        String lastName = null;
                        for (File file : sameFiles) {
                            if (lastName != null && !lastName.equals(file.getName())) {
                                sameNames = false;
                            }
                            lastName = file.getName();
                            // Chain locations together
                            Location linkedLocation = location;
                            location = Location.create(file);
                            location.setSecondary(linkedLocation);
                        }

                        if (sameNames) {
                            StringBuilder sb = new StringBuilder(sameFiles.size() * 16);
                            for (File file : sameFiles) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(file.getParentFile().getName());
                            }
                            String message =
                                    String.format(
                                            "The `%1$s` icon has identical contents in the following configuration folders: %2$s",
                                            lastName, sb.toString());
                            if (location != null) {
                                Location curr = location;
                                while (curr != null) {
                                    // Suppressed? Check all alias paths
                                    if (context.getConfiguration()
                                            .isIgnored(
                                                    context,
                                                    DUPLICATES_CONFIGURATIONS,
                                                    curr,
                                                    message)) {
                                        return;
                                    }

                                    curr = curr.getSecondary();
                                }

                                context.report(DUPLICATES_CONFIGURATIONS, location, message);
                            }
                        } else {
                            StringBuilder sb = new StringBuilder(sameFiles.size() * 16);
                            for (File file : sameFiles) {
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(file.getName());
                            }
                            String message =
                                    String.format(
                                            "The following unrelated icon files have identical contents: %1$s",
                                            sb.toString());

                            Location curr = location;
                            while (curr != null) {
                                // Suppressed? Check all alias paths
                                if (context.getConfiguration()
                                        .isIgnored(context, DUPLICATES_NAMES, curr, message)) {
                                    return;
                                }

                                curr = curr.getSecondary();
                            }

                            context.report(DUPLICATES_NAMES, location, message);
                        }
                    }
                }
            }
        }
    }

    // This method checks the given map from resource file to pixel dimensions for each
    // such image and makes sure that the normalized dip sizes across all the densities
    // are mostly the same.
    private static void checkDipSizes(Context context, Map<File, Dimension> pixelSizes) {
        // Partition up the files such that I can look at a series by name. This
        // creates a map from filename (such as foo.png) to a list of files
        // providing that icon in various folders: drawable-mdpi/foo.png, drawable-hdpi/foo.png
        // etc.
        Map<String, List<File>> nameToFiles = new HashMap<>();
        for (File file : pixelSizes.keySet()) {
            String name = file.getName();
            List<File> list = nameToFiles.get(name);
            if (list == null) {
                list = new ArrayList<>();
                nameToFiles.put(name, list);
            }
            list.add(file);
        }

        ArrayList<String> names = new ArrayList<>(nameToFiles.keySet());
        Collections.sort(names);

        // We have to partition the files further because it's possible for the project
        // to have different configurations for an icon, such as this:
        //   drawable-large-hdpi/foo.png, drawable-large-mdpi/foo.png,
        //   drawable-hdpi/foo.png, drawable-mdpi/foo.png,
        //    drawable-hdpi-v11/foo.png and drawable-mdpi-v11/foo.png.
        // In this case we don't want to compare across categories; we want to
        // ensure that the drawable-large-{density} icons are consistent,
        // that the drawable-{density}-v11 icons are consistent, and that
        // the drawable-{density} icons are consistent.

        // Map from name to list of map from parent folder to list of files
        Map<String, Map<String, List<File>>> configMap = new HashMap<>();
        for (Map.Entry<String, List<File>> entry : nameToFiles.entrySet()) {
            String name = entry.getKey();
            List<File> files = entry.getValue();
            for (File file : files) {
                //noinspection ConstantConditions
                String parentName = file.getParentFile().getName();
                // Strip out the density part
                int index = -1;
                for (String qualifier : DENSITY_QUALIFIERS) {
                    index = parentName.indexOf(qualifier);
                    if (index != -1) {
                        parentName =
                                parentName.substring(0, index)
                                        + parentName.substring(index + qualifier.length());
                        break;
                    }
                }
                if (index == -1) {
                    // No relevant qualifier found in the parent directory name,
                    // e.g. it's just "drawable" or something like "drawable-nodpi".
                    continue;
                }

                Map<String, List<File>> folderMap = configMap.get(name);
                if (folderMap == null) {
                    folderMap = new HashMap<>();
                    configMap.put(name, folderMap);
                }
                // Map from name to a map from parent folder to files
                List<File> list = folderMap.get(parentName);
                if (list == null) {
                    list = new ArrayList<>();
                    folderMap.put(parentName, list);
                }
                list.add(file);
            }
        }

        for (String name : names) {
            // List<File> files = nameToFiles.get(name);
            Map<String, List<File>> configurations = configMap.get(name);
            if (configurations == null) {
                // Nothing in this configuration: probably only found in drawable/ or
                // drawable-nodpi etc directories.
                continue;
            }

            for (Map.Entry<String, List<File>> entry : configurations.entrySet()) {
                List<File> files = entry.getValue();

                // Ensure that all the dip sizes are *roughly* the same
                Map<File, Dimension> dipSizes = new HashMap<>();
                int dipWidthSum = 0; // Incremental computation of average
                int dipHeightSum = 0; // Incremental computation of average
                int count = 0;
                for (File file : files) {
                    //noinspection ConstantConditions
                    String folderName = file.getParentFile().getName();
                    float factor = getMdpiScalingFactor(folderName);
                    if (factor > 0) {
                        Dimension size = pixelSizes.get(file);
                        if (size == null) {
                            continue;
                        }
                        Dimension dip =
                                new Dimension(
                                        Math.round(size.width / factor),
                                        Math.round(size.height / factor));
                        dipWidthSum += dip.width;
                        dipHeightSum += dip.height;
                        dipSizes.put(file, dip);
                        count++;

                        String fileName = file.getName();
                        Matcher matcher = DP_NAME_PATTERN.matcher(fileName);
                        if (matcher.matches()) {
                            String dpString = matcher.group(1);
                            int dp = Integer.parseInt(dpString);
                            // We're not sure whether the dp size refers to the width
                            // or the height, so check both. Allow a little bit of rounding
                            // slop.
                            if (Math.abs(dip.width - dp) > 2 || Math.abs(dip.height - dp) > 2) {
                                // Unicode 00D7 is the multiplication sign
                                String message =
                                        String.format(
                                                ""
                                                        + "Suspicious file name `%1$s`: The implied %2$s `dp` "
                                                        + "size does not match the actual `dp` size "
                                                        + "(pixel size %3$d\u00D7%4$d in a `%5$s` folder "
                                                        + "computes to %6$d\u00D7%7$d `dp`)",
                                                fileName,
                                                dpString,
                                                size.width,
                                                size.height,
                                                folderName,
                                                dip.width,
                                                dip.height);
                                context.report(ICON_DIP_SIZE, Location.create(file), message);
                            }
                        }
                    }
                }
                if (count == 0) {
                    // Icons in drawable/ and drawable-nodpi/
                    continue;
                }
                int meanWidth = dipWidthSum / count;
                int meanHeight = dipHeightSum / count;

                // Compute standard deviation?
                int squareWidthSum = 0;
                int squareHeightSum = 0;
                for (Dimension size : dipSizes.values()) {
                    squareWidthSum += (size.width - meanWidth) * (size.width - meanWidth);
                    squareHeightSum += (size.height - meanHeight) * (size.height - meanHeight);
                }
                double widthStdDev = Math.sqrt(squareWidthSum / (double) count);
                double heightStdDev = Math.sqrt(squareHeightSum / (double) count);

                if ((widthStdDev > (meanWidth / 10)) || (heightStdDev > meanHeight)) {
                    StringBuilder sb = new StringBuilder(100);
                    sb.append("The image `")
                            .append(name)
                            .append(
                                    "` varies significantly in its "
                                            + "density-independent (dip) size across the various density "
                                            + "versions: ");

                    Location location = null;

                    // Sort entries by decreasing dip size
                    List<Map.Entry<File, Dimension>> entries = new ArrayList<>();
                    for (Map.Entry<File, Dimension> entry2 : dipSizes.entrySet()) {
                        entries.add(entry2);
                    }
                    entries.sort(
                            (e1, e2) -> {
                                Dimension d1 = e1.getValue();
                                Dimension d2 = e2.getValue();
                                if (d1.width != d2.width) {
                                    return d2.width - d1.width;
                                }
                                if (d1.height != d2.height) {
                                    return d2.height - d1.height;
                                }

                                String path1 = e1.getKey().getPath();
                                String path2 = e2.getKey().getPath();
                                return path1.compareTo(path2);
                            });

                    List<String> examples = Lists.newArrayList();
                    for (Map.Entry<File, Dimension> entry2 : entries) {
                        File file = entry2.getKey();

                        // Chain locations together
                        Location linkedLocation = location;
                        location = Location.create(file);
                        location.setSecondary(linkedLocation);
                        Dimension dip = entry2.getValue();
                        Dimension px = pixelSizes.get(file);
                        String example =
                                Lint.getFileNameWithParent(context.getClient(), file)
                                        + ": "
                                        + String.format(
                                                "%1$dx%2$d dp (%3$dx%4$d px)",
                                                dip.width, dip.height, px.width, px.height);
                        examples.add(example);
                    }
                    if (location != null) {
                        Collections.sort(examples);
                        sb.append(Joiner.on(", ").join(examples));
                        context.report(ICON_DIP_SIZE, location, sb.toString());
                    }
                }
            }
        }
    }

    private void checkDensities(
            Context context,
            File res,
            Map<File, Set<String>> folderToNames,
            Map<File, Set<String>> nonDpiFolderNames) {
        // TODO: Is there a way to look at the manifest and figure out whether
        // all densities are expected to be needed?
        // Note: ldpi is probably not needed; it has very little usage
        // (about 2%; http://developer.android.com/resources/dashboard/screens.html)
        // TODO: Use the matrix to check out if we can eliminate densities based
        // on the target screens?

        Set<String> definedDensities = new HashSet<>();
        for (File f : folderToNames.keySet()) {
            definedDensities.add(f.getName());
        }

        // Look for missing folders -- if you define say drawable-mdpi then you
        // should also define -hdpi and -xhdpi.
        if (context.isEnabled(ICON_MISSING_FOLDER)) {
            List<String> missing = new ArrayList<>();
            List<String> requiredDensityFolders = getRequiredDensityFolders(context);

            boolean foundSome = false;
            for (String density : requiredDensityFolders) {
                if (!definedDensities.contains(density)) {
                    missing.add(density);
                } else {
                    // Make sure the we have at least one of the folders required
                    // (e.g. we might only have mipmap folders as well as drawable/ or
                    // drawable-nodpi)
                    foundSome = true;
                }
            }

            if (!missing.isEmpty() && foundSome) {
                Collections.sort(missing);
                context.report(
                        ICON_MISSING_FOLDER,
                        Location.create(res),
                        String.format(
                                "Missing density variation folders in `%1$s`: %2$s",
                                context.getProject().getDisplayPath(res),
                                Lint.formatList(missing, -1)));
            }
        }

        if (context.isEnabled(ICON_NODPI)) {
            Set<String> noDpiNames = new HashSet<>();
            for (Map.Entry<File, Set<String>> entry : folderToNames.entrySet()) {
                if (isNoDpiFolder(entry.getKey())) {
                    noDpiNames.addAll(entry.getValue());
                }
            }
            if (!noDpiNames.isEmpty()) {
                // Make sure that none of the nodpi names appear in a non-nodpi folder
                Set<String> inBoth = new HashSet<>();
                List<File> files = new ArrayList<>();
                for (Map.Entry<File, Set<String>> entry : folderToNames.entrySet()) {
                    File folder = entry.getKey();
                    String folderName = folder.getName();
                    if (!isNoDpiFolder(folder)) {
                        assert DENSITY_PATTERN.matcher(folderName).matches();
                        Set<String> overlap = nameIntersection(noDpiNames, entry.getValue());
                        inBoth.addAll(overlap);
                        for (String name : overlap) {
                            files.add(new File(folder, name));
                        }
                    }
                }

                if (!inBoth.isEmpty()) {
                    List<String> list = new ArrayList<>(inBoth);
                    Collections.sort(list);

                    // Chain locations together
                    Location location = chainLocations(files);

                    context.report(
                            ICON_NODPI,
                            location,
                            String.format(
                                    "The following images appear in both `-nodpi` and in a density folder: %1$s",
                                    Lint.formatList(
                                            list, context.getDriver().isAbbreviating() ? 10 : -1)));
                }
            }
        }

        if (context.isEnabled(ICON_MIX_9PNG)) {
            checkMixedNinePatches(context, folderToNames);
        }

        if (context.isEnabled(ICON_XML_AND_PNG)) {
            Map<File, Set<String>> folderMap = Maps.newHashMap(folderToNames);
            folderMap.putAll(nonDpiFolderNames);
            Set<String> xmlNames = Sets.newHashSetWithExpectedSize(100);
            Set<String> bitmapNames = Sets.newHashSetWithExpectedSize(100);

            for (Map.Entry<File, Set<String>> entry : folderMap.entrySet()) {
                Set<String> names = entry.getValue();
                String folderName = entry.getKey().getName();
                for (String name : names) {
                    if (endsWith(name, DOT_XML)) {
                        // Ignore .xml files in version qualifier folders
                        if (!folderName.contains("-v")) {
                            xmlNames.add(name);
                        }
                    } else if (isDrawableFile(name)) {
                        bitmapNames.add(name);
                    }
                }
            }
            if (!xmlNames.isEmpty() && !bitmapNames.isEmpty()) {
                // Make sure that none of the density-independent names appear in a density folder
                Set<String> overlap = nameIntersection(xmlNames, bitmapNames);
                if (!overlap.isEmpty()) {
                    Multimap<String, File> map = ArrayListMultimap.create();
                    Set<String> bases = Sets.newHashSetWithExpectedSize(overlap.size());
                    for (String name : overlap) {
                        bases.add(Lint.getBaseName(name));
                    }

                    for (String base : bases) {
                        for (Map.Entry<File, Set<String>> entry : folderMap.entrySet()) {
                            File folder = entry.getKey();
                            for (String n : entry.getValue()) {
                                if (base.equals(Lint.getBaseName(n))) {
                                    map.put(base, new File(folder, n));
                                }
                            }
                        }
                    }
                    List<String> sorted = new ArrayList<>(map.keySet());
                    Collections.sort(sorted);
                    for (String name : sorted) {
                        List<File> lists = Lists.newArrayList(map.get(name));
                        Location location = chainLocations(lists);

                        List<String> fileNames = Lists.newArrayList();
                        boolean seenXml = false;
                        boolean seenNonXml = false;
                        for (File f : lists) {
                            boolean isXml = endsWith(f.getPath(), DOT_XML);
                            if (isXml && !seenXml) {
                                fileNames.add(context.getProject().getDisplayPath(f));
                                seenXml = true;
                            } else if (!isXml && !seenNonXml) {
                                fileNames.add(context.getProject().getDisplayPath(f));
                                seenNonXml = true;
                            }
                        }

                        Collections.sort(fileNames);
                        context.report(
                                ICON_XML_AND_PNG,
                                location,
                                String.format(
                                        "The following images appear both as density independent `.xml` files and as bitmap files: %1$s",
                                        Lint.formatList(
                                                fileNames,
                                                context.getDriver().isAbbreviating() ? 10 : -1)));
                    }
                }
            }
        }

        if (context.isEnabled(ICON_DENSITIES)) {
            // Look for folders missing some of the specific assets
            Set<String> allNames = new HashSet<>();
            for (Map.Entry<File, Set<String>> entry : folderToNames.entrySet()) {
                if (!isNoDpiFolder(entry.getKey())) {
                    Set<String> names = entry.getValue();
                    allNames.addAll(names);
                }
            }

            for (Map.Entry<File, Set<String>> entry : folderToNames.entrySet()) {
                File file = entry.getKey();
                if (isNoDpiFolder(file)) {
                    continue;
                }
                Set<String> names = entry.getValue();
                if (names.size() != allNames.size()) {
                    List<String> delta = new ArrayList<>(nameDifferences(allNames, names));
                    if (delta.isEmpty()) {
                        continue;
                    }
                    Collections.sort(delta);
                    String foundIn = "";
                    if (delta.size() == 1) {
                        // Produce list of where the icon is actually defined
                        List<String> defined = new ArrayList<>();
                        String name = delta.get(0);
                        for (Map.Entry<File, Set<String>> e : folderToNames.entrySet()) {
                            if (e.getValue().contains(name)) {
                                defined.add(e.getKey().getName());
                            }
                        }
                        if (!defined.isEmpty()) {
                            Collections.sort(defined);
                            foundIn =
                                    String.format(
                                            " (found in %1$s)",
                                            Lint.formatList(
                                                    defined,
                                                    context.getDriver().isAbbreviating() ? 5 : -1));
                        }
                    }

                    // Irrelevant folder?
                    String folder = file.getName();
                    if (!getRequiredDensityFolders(context).contains(folder)) {
                        continue;
                    }

                    context.report(
                            ICON_DENSITIES,
                            Location.create(file),
                            String.format(
                                    "Missing the following drawables in `%1$s`: %2$s%3$s",
                                    folder,
                                    Lint.formatList(
                                            delta, context.getDriver().isAbbreviating() ? 5 : -1),
                                    foundIn));
                }
            }
        }
    }

    private List<String> getRequiredDensityFolders(@NonNull Context context) {
        if (cachedRequiredDensities == null || context.getProject() != cachedDensitiesForProject) {
            cachedDensitiesForProject = context.getProject();
            cachedRequiredDensities = Lists.newArrayListWithExpectedSize(10);

            List<String> applicableDensities = context.getProject().getApplicableDensities();
            if (applicableDensities != null) {
                cachedRequiredDensities.addAll(applicableDensities);
            } else {
                if (INCLUDE_LDPI) {
                    cachedRequiredDensities.add(DRAWABLE_LDPI);
                }
                cachedRequiredDensities.add(DRAWABLE_MDPI);
                cachedRequiredDensities.add(DRAWABLE_HDPI);
                cachedRequiredDensities.add(DRAWABLE_XHDPI);
                cachedRequiredDensities.add(DRAWABLE_XXHDPI);
                // xxxhdpi is not required - only required for launchers
                // From http://developer.android.com/guide/practices/screens_support.html:
                // "Note: the drawable-xxxhdpi qualifier is necessary only to provide a launcher
                //  icon that can appear larger than usual on an xxhdpi device. You do not need
                // to provide xxxhdpi assets for all your app's images."
            }
        }

        return cachedRequiredDensities;
    }

    /**
     * Compute the difference in names between a and b. This is not just Sets.difference(a, b)
     * because we want to make the comparisons <b>without file extensions</b> and return the result
     * <b>with</b>..
     */
    private static Set<String> nameDifferences(Set<String> a, Set<String> b) {
        Set<String> names1 = new HashSet<>(a.size());
        for (String s : a) {
            names1.add(Lint.getBaseName(s));
        }
        Set<String> names2 = new HashSet<>(b.size());
        for (String s : b) {
            names2.add(Lint.getBaseName(s));
        }

        names1.removeAll(names2);

        if (!names1.isEmpty()) {
            // Map filenames back to original filenames with extensions
            Set<String> result = new HashSet<>(names1.size());
            for (String s : a) {
                if (names1.contains(Lint.getBaseName(s))) {
                    result.add(s);
                }
            }
            for (String s : b) {
                if (names1.contains(Lint.getBaseName(s))) {
                    result.add(s);
                }
            }

            return result;
        }

        return Collections.emptySet();
    }

    /**
     * Compute the intersection in names between a and b. This is not just Sets.intersection(a, b)
     * because we want to make the comparisons <b>without file extensions</b> and return the result
     * <b>with</b>.
     */
    private static Set<String> nameIntersection(Set<String> a, Set<String> b) {
        Set<String> names1 = new HashSet<>(a.size());
        for (String s : a) {
            names1.add(Lint.getBaseName(s));
        }
        Set<String> names2 = new HashSet<>(b.size());
        for (String s : b) {
            names2.add(Lint.getBaseName(s));
        }

        names1.retainAll(names2);

        if (!names1.isEmpty()) {
            // Map filenames back to original filenames with extensions
            Set<String> result = new HashSet<>(names1.size());
            for (String s : a) {
                if (names1.contains(Lint.getBaseName(s))) {
                    result.add(s);
                }
            }
            for (String s : b) {
                if (names1.contains(Lint.getBaseName(s))) {
                    result.add(s);
                }
            }

            return result;
        }

        return Collections.emptySet();
    }

    private static boolean isNoDpiFolder(File file) {
        return file.getName().contains("-nodpi");
    }

    private Map<File, BufferedImage> imageCache;

    @Nullable
    private BufferedImage getImage(@Nullable File file) throws IOException {
        if (file == null) {
            return null;
        }
        if (imageCache == null) {
            imageCache = Maps.newHashMap();
        } else {
            BufferedImage image = imageCache.get(file);
            if (image != null) {
                return image;
            }
        }

        BufferedImage image = ImageIO.read(file);
        imageCache.put(file, image);

        return image;
    }

    private void checkDrawableDir(
            Context context,
            File folder,
            File[] files,
            Map<File, Dimension> pixelSizes,
            Map<File, Long> fileSizes,
            @Nullable Map<String, File> notificationIconsCompatibility) {
        String folderName = folder.getName();
        if (folderName.equals(DRAWABLE_FOLDER)) {
            for (File file : files) {
                String name = file.getName();
                //noinspection StatementWithEmptyBody
                if (name.endsWith(DOT_XML)) {
                    // pass - most common case, avoids checking other extensions
                } else if (endsWith(name, DOT_PNG)
                        || endsWith(name, DOT_JPG)
                        || endsWith(name, DOT_JPEG)
                        || endsWith(name, DOT_WEBP)
                        || endsWith(name, DOT_GIF)) {
                    context.report(
                            ICON_LOCATION,
                            Location.create(file),
                            String.format(
                                    "Found bitmap drawable `res/drawable/%1$s` in "
                                            + "densityless folder",
                                    file.getName()));
                }
            }
        }

        if (context.isEnabled(GIF_USAGE)) {
            for (File file : files) {
                String name = file.getName();
                if (endsWith(name, DOT_GIF)) {
                    context.report(
                            GIF_USAGE,
                            Location.create(file),
                            "Using the `.gif` format for bitmaps is discouraged");
                }
            }
        }

        if (context.isEnabled(ICON_EXTENSION)) {
            for (File file : files) {
                String path = file.getPath();
                if (isDrawableFile(path) && !endsWith(path, DOT_XML)) {
                    checkExtension(context, file);
                }
            }
        }

        if (context.isEnabled(ICON_COLORS)) {
            for (File file : files) {
                String name = file.getName();

                if (isDrawableFile(name) && !endsWith(name, DOT_XML) && !endsWith(name, DOT_9PNG)) {
                    String baseName = getBaseName(name);
                    boolean isActionBarIcon = isActionBarIcon(context, folderName, baseName, file);
                    if (isActionBarIcon || isNotificationIcon(baseName)) {
                        Dimension size = checkColor(context, file, isActionBarIcon);

                        // Store dimension for size check if we went to the trouble of reading image
                        if (size != null && pixelSizes != null) {
                            pixelSizes.put(file, size);
                        }
                    }
                }
            }
        }

        if (context.isEnabled(ICON_LAUNCHER_SHAPE)) {
            for (File file : files) {
                String name = file.getName();
                if (isLauncherIcon(folderName, getBaseName(name))) {
                    if (!endsWith(name, DOT_XML) && !endsWith(name, DOT_9PNG)) {
                        checkLauncherShape(context, folderName, file);
                    }
                }
            }
        }

        // Check icon sizes
        if (context.isEnabled(ICON_EXPECTED_SIZE)) {
            checkExpectedSizes(context, folder, files);
        }

        if (pixelSizes != null || fileSizes != null) {
            for (File file : files) {
                // TODO: Combine this check with the check for expected sizes such that
                // I don't check file sizes twice!
                String fileName = file.getName();

                if (endsWith(fileName, DOT_PNG)
                        || endsWith(fileName, DOT_JPG)
                        || endsWith(fileName, DOT_JPEG)
                        || endsWith(fileName, DOT_WEBP)) {
                    // Only scan .png files (except 9-patch png's) and jpg files for
                    // dip sizes. Duplicate checks can also be performed on ninepatch files.
                    if (pixelSizes != null
                            && !endsWith(fileName, DOT_9PNG)
                            && !pixelSizes.containsKey(file)) { // already read by checkColor?
                        Dimension size = getSize(file);
                        pixelSizes.put(file, size);
                    }
                    if (fileSizes != null) {
                        fileSizes.put(file, file.length());
                    }
                }
            }
        }

        // Check version compatibility of notification icons.
        if (notificationIconsCompatibility != null) {
            checkNotificationIconsCompatibility(files, notificationIconsCompatibility);
        }

        if (context.isEnabled(WEBP_UNSUPPORTED) && files.length > 0) {
            checkWebpSupported(context, files);
        }

        imageCache = null;
    }

    private void checkNotificationIconsCompatibility(
            @Nullable File[] files, @NonNull Map<String, File> notificationIconsCompatibility) {
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            String baseName = getBaseName(name);
            if (isNotificationIcon(baseName)) {
                File mostCompatibleFile = notificationIconsCompatibility.get(baseName);
                if (mostCompatibleFile == null || !SdkUtils.isBitmapFile(mostCompatibleFile)) {
                    notificationIconsCompatibility.put(baseName, file);
                }
            }
        }
    }

    private void checkWebpSupported(@NonNull Context context, @NonNull File[] files) {
        // all files in this folder have the same folder minSdkVersion
        int minSdk =
                Math.max(
                        context.getProject().getMinSdk(),
                        context.getDriver().getResourceFolderVersion(files[0]));
        if (minSdk >= 18) {
            return;
        }

        for (File file : files) {
            String path = file.getPath();
            if (!endsWithIgnoreCase(path, DOT_WEBP)) {
                continue;
            }
            String name = file.getName();
            String baseName = getBaseName(name);
            if (isAdaptiveIconLayer(baseName)) {
                Location location = Location.create(file);
                String message = "Adaptive icon bitmaps must be in PNG format";
                Incident incident = new Incident(WEBP_UNSUPPORTED, location, message);
                context.report(incident, minSdkLessThan(18));
                continue;
            }

            WebpHeader header = WebpHeader.getWebpHeader(file);
            if (header != null && header.format != null) {
                boolean simpleFormat = "VP8".equals(header.format);
                int required = simpleFormat ? 15 : 18;
                if (required > minSdk) {
                    Location location = Location.create(file);
                    String message =
                            simpleFormat
                                    ? "WebP requires Android 4.0 (API 15)"
                                    : "WebP extended or lossless format requires Android 4.2.1 (API 18)";
                    message += "; current minSdkVersion is %1$s";
                    Incident incident = new Incident(WEBP_UNSUPPORTED, location, message);
                    // With folder versions, could be higher than the app minSdkVersion
                    context.report(incident, map().put(KEY_MIN_API, minSdk));
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        assert incident.getIssue() == WEBP_UNSUPPORTED;
        int minSdk = context.getMainProject().getMinSdk();
        if (minSdk < 18) {
            // include minSdkVersion in the message
            String message = incident.getMessage();
            assert message.contains("%");
            int fileMin = map.getInt(KEY_MIN_API, 1);
            int actualMinSdk = Math.max(minSdk, fileMin);
            incident.setMessage(String.format(message, actualMinSdk));
            return true;
        }

        return false;
    }

    /** Check that launcher icons do not fill every pixel in the image */
    private void checkLauncherShape(Context context, String folderName, File file) {
        try {
            BufferedImage image = getImage(file);
            if (image != null) {
                if (isRoundIcon(folderName, getBaseName(file.getName()))) {
                    if (!isRound(image)) {
                        String message =
                                "Launcher icon used as round icon did not have a "
                                        + "circular shape";
                        context.report(ICON_LAUNCHER_SHAPE, Location.create(file), message);
                        return;
                    }
                }

                // TODO: see if the shape is rectangular but inset from outer rectangle; if so
                // that's probably not right either!
                for (int y = 0, height = image.getHeight(); y < height; y++) {
                    for (int x = 0, width = image.getWidth(); x < width; x++) {
                        int rgb = image.getRGB(x, y);
                        if ((rgb & 0xFF000000) == 0) {
                            return;
                        }
                    }
                }

                String message =
                        "Launcher icons should not fill every pixel of their square "
                                + "region; see the design guide for details";
                context.report(ICON_LAUNCHER_SHAPE, Location.create(file), message);
            }
        } catch (IOException e) {
            // Pass: ignore files we can't read
        }
    }

    private static boolean isRound(@NonNull BufferedImage image) {
        // Simple algorithm: compute radius and center of the launcher icon;
        // then compute a mask for it and then diff it with a drawing of a circle

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;

        int imageHeight = image.getHeight();
        int imageWidth = image.getWidth();
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int rgb = image.getRGB(x, y);
                if ((rgb & 0xFF000000) != 0) {
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                }
            }
        }

        int shapeWidth = maxX - minX + 1;
        int shapeHeight = maxY - minY + 1;

        // The shape width and height should be roughly equal; it's supposed to be
        // a circle, not an oval
        if (Math.abs(shapeWidth - shapeHeight) > imageWidth / 10) {
            return false;
        }

        BufferedImage circle = new BufferedImage(imageWidth, imageHeight, TYPE_INT_ARGB);
        Graphics graphics = circle.getGraphics();
        graphics.fillOval(minX, minY, shapeWidth, shapeHeight);
        graphics.dispose();

        final int threshold = 64;

        int different = 0;
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int sourceAlpha = (image.getRGB(x, y) & 0xFF000000) >>> 24;
                int circleAlpha = (circle.getRGB(x, y) & 0xFF000000) >>> 24;
                if (sourceAlpha > 0 && sourceAlpha < threshold) {
                    // Don't compare pixels in the alpha area
                    continue;
                }
                boolean original = sourceAlpha < threshold;
                boolean cir = circleAlpha < threshold;
                if (original != cir) {
                    different++;
                }
            }
        }

        long total = (long) imageHeight * imageWidth;
        double percentDifferent = 100 * different / (double) total;

        // Allow 4% difference or less -- mainly to account for anti-aliasing edge differences
        return percentDifferent < 4;
    }

    public static boolean isWhite(BufferedImage image) {
        for (int y = 0, height = image.getHeight(); y < height; y++) {
            for (int x = 0, width = image.getWidth(); x < width; x++) {
                int rgb = image.getRGB(x, y);
                // If the pixel is not completely transparent, insist that
                // its RGB channel must be white (with any alpha value)
                if ((rgb & 0xFF000000) != 0 && (rgb & 0xFFFFFF) != 0xFFFFFF) {
                    int r = (rgb & 0xFF0000) >>> 16;
                    int g = (rgb & 0x00FF00) >>> 8;
                    int b = (rgb & 0x0000FF);
                    if (r == g && r == b) {
                        // If the pixel is not white, it might be because of
                        // anti-aliasing. In that case, at least one neighbor
                        // should be of a different color
                        if (x < width - 1 && rgb != image.getRGB(x + 1, y)) {
                            continue;
                        }
                        if (x > 0 && rgb != image.getRGB(x - 1, y)) {
                            continue;
                        }
                        if (y < height - 1 && rgb != image.getRGB(x, y + 1)) {
                            continue;
                        }
                        if (y > 0 && rgb != image.getRGB(x, y - 1)) {
                            continue;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check whether the icons in the file are okay. Also return the image size if known (for use by
     * other checks)
     */
    private Dimension checkColor(Context context, File file, boolean isActionBarIcon) {
        try {
            BufferedImage image = getImage(file);
            if (image != null) {
                if (isActionBarIcon) {
                    checkPixels:
                    for (int y = 0, height = image.getHeight(); y < height; y++) {
                        for (int x = 0, width = image.getWidth(); x < width; x++) {
                            int rgb = image.getRGB(x, y);
                            if ((rgb & 0xFF000000) != 0) { // else: transparent
                                int r = (rgb & 0xFF0000) >>> 16;
                                int g = (rgb & 0x00FF00) >>> 8;
                                int b = (rgb & 0x0000FF);
                                if (r != g || r != b) {
                                    String message =
                                            "Action Bar icons should use a single gray "
                                                    + "color (`#333333` for light themes (with 60%/30% "
                                                    + "opacity for enabled/disabled), and `#FFFFFF` with "
                                                    + "opacity 80%/30% for dark themes";
                                    context.report(ICON_COLORS, Location.create(file), message);
                                    break checkPixels;
                                }
                            }
                        }
                    }
                } else {
                    // else: transparent
                    if (!isWhite(image)) {
                        String message = "Notification icons must be entirely white";
                        Location location = Location.create(file);

                        String name = getBaseName(file.getName());
                        UElement usage =
                                notificationIcons != null ? notificationIcons.get(name) : null;
                        if (usage != null) {
                            LintClient client = context.getClient();
                            Project project = context.getProject();
                            UastParser parser = client.getUastParser(project);
                            Location secondary = parser.createLocation(usage);
                            secondary.setMessage("Icon used in notification here");
                            location.setSecondary(secondary);
                        }

                        context.report(ICON_COLORS, location, message);
                    }
                }
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            // Pass: ignore files we can't read
        }

        return null;
    }

    private static void checkExtension(Context context, File file) {
        try {
            ImageInputStream input = ImageIO.createImageInputStream(file);
            if (input != null) {
                try {
                    Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext() && !endsWithIgnoreCase(file.getPath(), DOT_WEBP)) {
                        // Check WEBP: No decoder available outside of Android Studio
                        // See if it's a WEBP file without a webp extension
                        //noinspection VariableNotUsedInsideIf
                        if (WebpHeader.getWebpHeader(file) != null) {
                            String extension = file.getName();
                            extension = extension.substring(extension.lastIndexOf('.') + 1);
                            String message =
                                    String.format(
                                            "Misleading file extension; named `.%1$s` but the "
                                                    + "file format is `%2$s`",
                                            extension, "webp");
                            Location location = Location.create(file);
                            context.report(ICON_EXTENSION, location, message);
                        }
                    }

                    while (readers.hasNext()) {
                        ImageReader reader = readers.next();
                        try {
                            reader.setInput(input);

                            // Check file extension
                            String formatName = reader.getFormatName();
                            if (formatName != null && !formatName.isEmpty()) {
                                String path = file.getPath();
                                int index = path.lastIndexOf('.');
                                String extension = path.substring(index + 1).toLowerCase(Locale.US);

                                if (!formatName.equalsIgnoreCase(extension)) {
                                    if (endsWith(path, DOT_JPG) && formatName.equals("JPEG")) {
                                        return;
                                    }
                                    String message =
                                            String.format(
                                                    "Misleading file extension; named `.%1$s` but the "
                                                            + "file format is `%2$s`",
                                                    extension, formatName);
                                    Location location = Location.create(file);
                                    context.report(ICON_EXTENSION, location, message);
                                }
                                break;
                            }
                        } finally {
                            reader.dispose();
                        }
                    }
                } finally {
                    input.close();
                }
            }
        } catch (IOException e) {
            // Pass -- we can't handle all image types, warn about those we can
        }
    }

    // Like SdkUtils.fileNameToResourceName, but for files like .svn it returns "" rather than
    // ".svn"
    private static String getBaseName(String name) {
        String baseName = name;
        int index = baseName.indexOf('.');
        if (index != -1) {
            baseName = baseName.substring(0, index);
        }

        return baseName;
    }

    private static void checkMixedNinePatches(
            Context context, Map<File, Set<String>> folderToNames) {
        Set<String> conflictSet = null;

        for (Map.Entry<File, Set<String>> entry : folderToNames.entrySet()) {
            Set<String> baseNames = new HashSet<>();
            Set<String> names = entry.getValue();
            for (String name : names) {
                assert isDrawableFile(name) : name;
                String base = getBaseName(name);
                if (baseNames.contains(base)) {
                    String ninepatch = base + DOT_9PNG;
                    String png = base + DOT_PNG;
                    if (names.contains(ninepatch) && names.contains(png)) {
                        if (conflictSet == null) {
                            conflictSet = new HashSet<>();
                        }
                        conflictSet.add(base);
                    }
                } else {
                    baseNames.add(base);
                }
            }
        }

        if (conflictSet == null || conflictSet.isEmpty()) {
            return;
        }

        Map<String, List<File>> conflicts = null;
        for (Map.Entry<File, Set<String>> entry : folderToNames.entrySet()) {
            File dir = entry.getKey();
            Set<String> names = entry.getValue();
            for (String name : names) {
                assert isDrawableFile(name) : name;
                String base = getBaseName(name);
                if (conflictSet.contains(base)) {
                    if (conflicts == null) {
                        conflicts = Maps.newHashMap();
                    }
                    List<File> files = conflicts.get(base);
                    if (files == null) {
                        files = Lists.newArrayList();
                        conflicts.put(base, files);
                    }
                    files.add(new File(dir, name));
                }
            }
        }

        assert conflicts != null && !conflicts.isEmpty() : conflictSet;
        List<String> names = new ArrayList<>(conflicts.keySet());
        Collections.sort(names);
        for (String name : names) {
            List<File> files = conflicts.get(name);
            assert files != null : name;
            Location location = chainLocations(files);

            String message =
                    String.format(
                            "The files `%1$s.png` and `%1$s.9.png` clash; both "
                                    + "will map to `@drawable/%1$s`",
                            name);
            context.report(ICON_MIX_9PNG, location, message);
        }
    }

    private static Location chainLocations(List<File> files) {
        // Chain locations together
        Collections.sort(files);
        Location location = null;
        for (File file : files) {
            Location linkedLocation = location;
            location = Location.create(file);
            location.setSecondary(linkedLocation);
        }
        return location;
    }

    private void checkExpectedSizes(Context context, File folder, File[] files) {
        if (files == null || files.length == 0) {
            return;
        }

        String folderName = folder.getName();
        int folderVersion = context.getDriver().getResourceFolderVersion(files[0]);
        FolderConfiguration folderConfig = FolderConfiguration.getConfigForFolder(folderName);

        for (File file : files) {
            String name = file.getName();

            // TODO: Look up exact app icon from the manifest rather than simply relying on
            // the naming conventions described here:
            //
            // http://developer.android.com/guide/practices/ui_guidelines/icon_design.html#design-tips
            // See if we can figure out other types of icons from usage too.

            String baseName = getBaseName(name);

            if (isLauncherIcon(folderName, baseName)) {
                // TODO: Update according to
                // https://developer.android.com/guide/practices/ui_guidelines/icon_design_adaptive
                // Launcher icons
                checkSize(context, folderName, file, 48, 48, true, folderConfig);
            } else if (isActionBarIcon(folderName, baseName)) {
                checkSize(context, folderName, file, 32, 32, true, folderConfig);
            } else if (name.startsWith("ic_dialog_")) {
                // Dialog
                checkSize(context, folderName, file, 32, 32, true, folderConfig);
            } else if (name.startsWith("ic_tab_")) {
                // Tab icons
                checkSize(context, folderName, file, 32, 32, true, folderConfig);
            } else if (isNotificationIcon(baseName)) {
                // Notification icons
                checkSize(context, folderName, file, 24, 24, true, folderConfig);
            } else if (isAdaptiveIconLayer(baseName)) {
                checkSize(context, folderName, file, 108, 108, true, folderConfig);
            } else if (name.startsWith("ic_menu_")) {
                // Menu icons (<=2.3 only: Replaced by action bar icons (ic_action_ in 3.0).
                // However the table halfway down the page on
                // http://developer.android.com/guide/practices/ui_guidelines/icon_design.html
                // and the README in the icon template download says that convention is ic_menu
                checkSize(context, folderName, file, 32, 32, true, folderConfig);
            }
            // TODO: ListView icons?
        }
    }

    private static float getMdpiScalingFactor(String folderName) {
        // Can't do startsWith(DRAWABLE_MDPI) because the folder could
        // be something like "drawable-sw600dp-mdpi".
        if (folderName.contains("-mdpi")) {
            return 1.0f;
        } else if (folderName.contains("-hdpi")) {
            return 1.5f;
        } else if (folderName.contains("-xhdpi")) {
            return 2.0f;
        } else if (folderName.contains("-xxhdpi")) {
            return 3.0f;
        } else if (folderName.contains("-xxxhdpi")) {
            return 4.0f;
        } else if (folderName.contains("-ldpi")) {
            return 0.75f;
        } else {
            return 0f;
        }
    }

    private static void checkSize(
            Context context,
            String folderName,
            File file,
            int mdpiWidth,
            int mdpiHeight,
            boolean exactMatch,
            @Nullable FolderConfiguration folderConfig) {
        String fileName = file.getName();
        // Only scan .png files (except 9-patch png's) and jpg files
        if (!((endsWith(fileName, DOT_PNG) && !endsWith(fileName, DOT_9PNG))
                || endsWith(fileName, DOT_WEBP)
                || endsWith(fileName, DOT_JPG)
                || endsWith(fileName, DOT_JPEG))) {
            return;
        }

        int width;
        int height;
        // Use 3:4:6:8 scaling ratio to look up the other expected sizes
        if (folderName.startsWith(DRAWABLE_MDPI)) {
            width = mdpiWidth;
            height = mdpiHeight;
        } else if (folderName.startsWith(DRAWABLE_HDPI)) {
            // Perform math using floating point; if we just do
            //   width = mdpiWidth * 3 / 2;
            // then for mdpiWidth = 25 (as in notification icons on pre-GB) we end up
            // with width = 37, instead of 38 (with floating point rounding we get 37.5 = 38)
            width = Math.round(mdpiWidth * 3.f / 2);
            height = Math.round(mdpiHeight * 3f / 2);
        } else if (folderName.startsWith(DRAWABLE_XHDPI)) {
            width = mdpiWidth * 2;
            height = mdpiHeight * 2;
        } else if (folderName.startsWith(DRAWABLE_XXHDPI)) {
            width = mdpiWidth * 3;
            height = mdpiWidth * 3;
        } else if (folderName.startsWith(DRAWABLE_LDPI)) {
            width = Math.round(mdpiWidth * 3f / 4);
            height = Math.round(mdpiHeight * 3f / 4);
        } else if (folderConfig != null
                && folderConfig.getDensityQualifier() != null
                && !folderConfig.getDensityQualifier().hasFakeValue()) {
            Density density = folderConfig.getDensityQualifier().getValue();
            if (density == null) {
                return;
            }
            width = mdpiWidth * density.getDpiValue() / Density.DEFAULT_DENSITY;
            height = mdpiHeight * density.getDpiValue() / Density.DEFAULT_DENSITY;
        } else {
            return;
        }

        Dimension size = getSize(file);
        if (size != null) {
            if (exactMatch && (size.width != width || size.height != height)) {
                context.report(
                        ICON_EXPECTED_SIZE,
                        Location.create(file),
                        "Incorrect icon size for `"
                                + Lint.getFileNameWithParent(context.getClient(), file)
                                + "`: "
                                + String.format(
                                        "expected %1$dx%2$d, but was %3$dx%4$d",
                                        width, height, size.width, size.height));
            } else if (!exactMatch && (size.width > width || size.height > height)) {
                context.report(
                        ICON_EXPECTED_SIZE,
                        Location.create(file),
                        "Incorrect icon size for `"
                                + Lint.getFileNameWithParent(context.getClient(), file)
                                + "`: "
                                + String.format(
                                        "icon size should be at most %1$dx%2$d, but "
                                                + "was %3$dx%4$d",
                                        width, height, size.width, size.height));
            }
        }
    }

    @Nullable
    public static Dimension getSize(@NonNull File file) {
        try {
            ImageInputStream input = ImageIO.createImageInputStream(file);
            if (input != null) {
                // Apparently there are concurrency issues inside this class, so
                // don't try to access it in parallel
                synchronized (ImageIO.class) {
                    try {
                        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                        if (readers.hasNext()) {
                            ImageReader reader = readers.next();
                            try {
                                reader.setInput(input);
                                return new Dimension(reader.getWidth(0), reader.getHeight(0));
                            } finally {
                                reader.dispose();
                            }
                        }
                    } finally {
                        input.close();
                    }
                }
            }

            // WEBP custom file header decoding, not available via ImageReaders outside
            // of Android Studio since that requires native code around libwebp
            if (endsWithIgnoreCase(file.getPath(), DOT_WEBP)) {
                WebpHeader header = WebpHeader.getWebpHeader(file);
                if (header != null && header.width > 0) {
                    return new Dimension(header.width, header.height);
                }
            }

            // Fallback: read the image using the normal means
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            } else {
                return null;
            }
        } catch (IOException e) {
            // Pass -- we can't handle all image types, warn about those we can
            return null;
        }
    }

    private static class WebpHeader {
        public String format;
        int width;
        int height;

        @Nullable
        private static WebpHeader getWebpHeader(@NonNull File file) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                // WEBP header:
                //
                //   0                   1                   2                   3
                //   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                //  |      'R'      |      'I'      |      'F'      |      'F'      |
                //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                //  |                           File Size                           |
                //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                //  |      'W'      |      'E'      |      'B'      |      'P'      |
                //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

                //noinspection DuplicateCondition
                if (is.read() != 'R'
                        || is.read() != 'I'
                        || is.read() != 'F'
                        || is.read() != 'F'
                        || is.read() == -1
                        || is.read() == -1
                        || is.read() == -1
                        || is.read() == -1
                        || is.read() != 'W'
                        || is.read() != 'E'
                        || is.read() != 'B'
                        || is.read() != 'P') {
                    return null;
                }

                // See https://developers.google.com/speed/webp/docs/riff_container

                if (is.read() != 'V' || is.read() != 'P' || is.read() != '8') {
                    return null;
                }

                // Found WEBP header. API level 15 is required for the simple format; extended
                // or lossless formats require API 18.
                int format = is.read();
                WebpHeader data = new WebpHeader();

                if (format == 'L') {
                    data.format = "VP8L";

                    // https://developers.google.com/speed/webp/docs/webp_lossless_bitstream_specification#2_riff_header
                    // So far we've read up to number 5.
                    // "5. A little-endian 32-bit value of the number of bytes in the lossless
                    //     stream."
                    for (int i = 0; i < 4; i++) {
                        //noinspection ResultOfMethodCallIgnored
                        is.read();
                    }

                    // "6. One byte signature 0x2f."
                    if (is.read() == 0x2f) { // signature
                        // "The first 28 bits of the bitstream specify the width and height of the
                        //  image. Width and height are decoded as 14-bit integers as follows:
                        //  int image_width = ReadBits(14) + 1;
                        //  int image_height = ReadBits(14) + 1;"
                        int byte1 = is.read();
                        int byte2 = is.read();
                        int byte3 = is.read();
                        int byte4 = is.read();
                        if (is.read() != -1) {
                            data.width = ((byte2 & 0b111111) << 8 | byte1) + 1;
                            data.height =
                                    ((byte4 & 0b1111) << 10
                                                    | byte3 << 2
                                                    | (byte2 & 0b11000000) >> 6)
                                            + 1;
                        } // else already reached end somehow: invalid file
                    }
                    return data;
                } else if (format == 'X') {
                    // VP8X - extended file format
                    data.format = "VP8X";

                    //     0                   1                   2                   3
                    //     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    //    |                   WebP file header (12 bytes)                 |
                    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    //    |                      ChunkHeader('VP8X')                      |
                    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    //    |Rsv|I|L|E|X|A|R|                   Reserved                    |
                    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    //    |          Canvas Width Minus One               |             ...
                    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                    //    ...  Canvas Height Minus One    |
                    //    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

                    // Skip flags
                    for (int i = 0; i < 8; i++) {
                        //noinspection ResultOfMethodCallIgnored
                        is.read();
                    }

                    int byte1 = is.read();
                    int byte2 = is.read();
                    int byte3 = is.read();
                    int byte4 = is.read();
                    int byte5 = is.read();
                    int byte6 = is.read();

                    if (is.read() != -1) {
                        data.width = (byte1 | byte2 << 8 | byte3 << 16) + 1;
                        data.height = (byte4 | byte5 << 8 | byte6 << 16) + 1;
                    } // else already reached end somehow: invalid file

                    return data;
                } else if (format == ' ') {
                    data.format = "VP8";

                    // https://tools.ietf.org/html/rfc6386#section-9

                    for (int i = 0; i < 7; i++) {
                        //noinspection ResultOfMethodCallIgnored
                        is.read();
                    }

                    //     ---- Begin code block --------------------------------------
                    //
                    //       Start code byte 0     0x9d
                    //       Start code byte 1     0x01
                    //       Start code byte 2     0x2a
                    //
                    //       16 bits      :     (2 bits Horizontal Scale << 14) | Width (14 bits)
                    //       16 bits      :     (2 bits Vertical Scale << 14) | Height (14 bits)
                    //
                    //       ---- End code block ----------------------------------------

                    if (is.read() == 0x9d && is.read() == 0x01 && is.read() == 0x2a) {
                        int byte1 = is.read();
                        int byte2 = is.read();
                        int byte3 = is.read();
                        int byte4 = is.read();
                        if (is.read() != -1) {
                            data.width = byte1 | ((byte2 & 0b111111) << 8);
                            data.height = byte3 | ((byte4 & 0b111111) << 8);
                        } // else already reached end somehow: invalid file
                    }

                    return data;
                } else {
                    return null;
                }
            } catch (IOException ignore) {
            }
            return null;
        }
    }

    private Map<String, UElement> notificationIcons;
    /**
     * Set of names of @drawable resources that represent action bar icons, <b>or</b>, if the icons
     * is a @mipmap icon, the resource url (@mipmap/name).
     */
    private Set<String> actionBarIcons;
    /**
     * Set of names of @drawable resources that represent launcher icons, <b>or</b>, if the icons is
     * a @mipmap icon, the resource url (@mipmap/name).
     */
    private Set<String> launcherIcons;

    private Multimap<String, String> menuToIcons;
    /**
     * Set of names of @drawable resources that represent round icons, <b>or</b>, if the icons is
     * a @mipmap icon, the resource url (@mipmap/name).
     */
    private Set<Object> roundIcons;

    private boolean isLauncherIcon(@NonNull String folderName, @NonNull String name) {
        assert name.indexOf('.') == -1 : name; // Should supply base name

        // Naming convention
        //noinspection SimplifiableIfStatement
        if (name.startsWith("ic_launcher") && !isAdaptiveIconLayer(name)) {
            return true;
        }

        if (launcherIcons != null) {
            if (folderName.startsWith(MIPMAP_FOLDER)) {
                name = MIPMAP_PREFIX + name;
            }
            return launcherIcons.contains(name);
        }

        return false;
    }

    private boolean isRoundIcon(@NonNull String folderName, @NonNull String name) {
        assert name.indexOf('.') == -1 : name; // Should supply base name

        // Naming convention
        //noinspection SimplifiableIfStatement
        if (name.endsWith("_round")) {
            return true;
        }

        if (roundIcons != null) {
            if (folderName.startsWith(MIPMAP_FOLDER)) {
                name = MIPMAP_PREFIX + name;
            }
            return roundIcons.contains(name);
        }

        return false;
    }

    private boolean isNotificationIcon(String name) {
        assert name.indexOf('.') == -1; // Should supply base name

        // Naming convention
        //noinspection SimplifiableIfStatement
        if (name.startsWith("ic_stat_")) {
            return true;
        }

        return notificationIcons != null && notificationIcons.containsKey(name);
    }

    private boolean isActionBarIcon(@NonNull String folderName, @NonNull String name) {
        assert name.indexOf('.') == -1; // Should supply base name

        // Naming convention
        //noinspection SimplifiableIfStatement
        if (name.startsWith("ic_action_")) {
            return true;
        }

        if (actionBarIcons != null) {
            if (folderName.startsWith(MIPMAP_FOLDER)) {
                name = MIPMAP_PREFIX + name;
            }
            return actionBarIcons.contains(name);
        }

        return false;
    }

    private boolean isActionBarIcon(Context context, String folderName, String name, File file) {
        if (isActionBarIcon(folderName, name)) {
            return true;
        }

        // As of Android 3.0 ic_menu_ are action icons
        //noinspection SimplifiableIfStatement,RedundantIfStatement
        if (file != null && name.startsWith("ic_menu_")) {
            // Naming convention
            return true;
        }

        return false;
    }

    /**
     * Checks if the file looks like a foreground or background layer of an adaptive launcher icon.
     */
    private static boolean isAdaptiveIconLayer(@NonNull String name) {
        return name.startsWith("ic_launcher")
                && (name.endsWith("_foreground") || name.endsWith("_background"));
    }

    // XML detector: Skim manifest and menu files

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(
                // Manifest
                TAG_APPLICATION,
                TAG_ACTIVITY,
                TAG_ACTIVITY_ALIAS,
                TAG_SERVICE,
                TAG_PROVIDER,
                TAG_RECEIVER,

                // Menu
                TAG_ITEM);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String icon = element.getAttributeNS(ANDROID_URI, ATTR_ICON);
        addIcon(context, element, icon);
        icon = element.getAttributeNS(ANDROID_URI, ATTR_ROUND_ICON);
        String key = addIcon(context, element, icon);
        if (key != null) {
            if (roundIcons == null) {
                roundIcons = Sets.newHashSet();
            }
            roundIcons.add(key);
        }
    }

    @Nullable
    private String addIcon(
            @NonNull XmlContext context, @NonNull Element element, @Nullable String icon) {
        if (icon == null || icon.isEmpty()) {
            return null;
        }

        if (icon.startsWith(DRAWABLE_PREFIX)) {
            icon = icon.substring(DRAWABLE_PREFIX.length());
        } else if (!icon.startsWith(MIPMAP_PREFIX)) {
            // Store mipmaps as @mipmap/name instead of just name to avoid
            // confusing @drawable/foo and @mipmap/foo as representing the same icon
            return null;
        }

        String tagName = element.getTagName();
        if (tagName.equals(TAG_ITEM)) {
            if (menuToIcons == null) {
                menuToIcons = ArrayListMultimap.create();
            }
            String menu = getBaseName(context.file.getName());
            menuToIcons.put(menu, icon);
        } else if (tagName.equals(TAG_ACTIVITY)
                || tagName.equals(TAG_ACTIVITY_ALIAS)
                || tagName.equals(TAG_APPLICATION)) {
            // Manifest tags: launcher icons
            if (launcherIcons == null) {
                launcherIcons = Sets.newHashSet();
            }
            launcherIcons.add(icon);
        }

        return icon;
    }

    // ---- implements SourceCodeScanner ----

    private static final String NOTIFICATION_CLASS = "android.app.Notification";
    private static final String NOTIFICATION_BUILDER_CLASS = "android.app.Notification.Builder";
    private static final String NOTIFICATION_COMPAT_BUILDER_CLASS =
            "android.support.v4.app.NotificationCompat.Builder";
    private static final String SET_SMALL_ICON = "setSmallIcon";
    private static final String ON_CREATE_OPTIONS_MENU = "onCreateOptionsMenu";

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(2);
        types.add(UCallExpression.class);
        types.add(UMethod.class);
        return types;
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new NotificationFinder(context);
    }

    private final class NotificationFinder extends UElementHandler {
        private final JavaContext context;

        private NotificationFinder(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitMethod(@NonNull UMethod method) {
            if (ON_CREATE_OPTIONS_MENU.equals(method.getName())) {
                // Gather any R.menu references found in this method
                method.accept(new MenuFinder());
            }
        }

        @Override
        public void visitCallExpression(@NonNull UCallExpression node) {
            if (UastExpressionUtils.isConstructorCall(node)) {
                visitConstructorCall(node);
            }
        }

        private void visitConstructorCall(@NonNull UCallExpression node) {
            UReferenceExpression classReference = node.getClassReference();
            if (classReference == null) {
                return;
            }
            PsiElement resolved = classReference.resolve();
            if (!(resolved instanceof PsiClass)) {
                return;
            }
            String typeName = ((PsiClass) resolved).getQualifiedName();
            if (NOTIFICATION_CLASS.equals(typeName)) {
                List<UExpression> args = node.getValueArguments();
                if (args.size() == 3) {
                    if (args.get(0) instanceof UReferenceExpression && handleSelect(args.get(0))) {
                        return;
                    }

                    ResourceUrl url =
                            ResourceEvaluator.getResource(context.getEvaluator(), args.get(0));
                    if (url != null
                            && (url.type == ResourceType.DRAWABLE
                                    || url.type == ResourceType.COLOR
                                    || url.type == ResourceType.MIPMAP)) {
                        if (notificationIcons == null) {
                            notificationIcons = Maps.newHashMap();
                        }
                        notificationIcons.put(url.name, node);
                    }
                }
            } else if (NOTIFICATION_BUILDER_CLASS.equals(typeName)
                    || NOTIFICATION_COMPAT_BUILDER_CLASS.equals(typeName)) {
                UMethod method = UastUtils.getParentOfType(node, UMethod.class, true);
                if (method != null) {
                    SetIconFinder finder = new SetIconFinder();
                    method.accept(finder);
                }
            }
        }
    }

    private boolean handleSelect(UElement select) {
        ResourceUrl url = ResourceEvaluator.getResourceConstant(select);
        if (url != null && url.type == ResourceType.DRAWABLE && !url.isFramework()) {
            if (notificationIcons == null) {
                notificationIcons = Maps.newHashMap();
            }
            notificationIcons.put(url.name, select);

            return true;
        }

        return false;
    }

    private final class SetIconFinder extends AbstractUastVisitor {
        @Override
        public boolean visitCallExpression(@NonNull UCallExpression expression) {
            if (UastExpressionUtils.isMethodCall(expression)) {
                if (SET_SMALL_ICON.equals(getMethodName(expression))) {
                    List<UExpression> arguments = expression.getValueArguments();
                    if (arguments.size() == 1 && arguments.get(0) instanceof UReferenceExpression) {
                        handleSelect(arguments.get(0));
                    }
                }
            }
            return super.visitCallExpression(expression);
        }

        @Override
        public boolean visitClass(UClass node) {
            // Skip anonymous inner classes
            return node instanceof UAnonymousClass || super.visitClass(node);
        }
    }

    private final class MenuFinder extends AbstractUastVisitor {
        @Override
        public boolean visitSimpleNameReferenceExpression(USimpleNameReferenceExpression node) {
            ResourceUrl url = ResourceEvaluator.getResourceConstant(node);
            if (url != null && url.type == ResourceType.MENU && !url.isFramework()) {
                // Reclassify icons in the given menu as action bar icons
                if (menuToIcons != null) {
                    Collection<String> icons = menuToIcons.get(url.name);
                    if (icons != null) {
                        if (actionBarIcons == null) {
                            actionBarIcons = Sets.newHashSet();
                        }
                        actionBarIcons.addAll(icons);
                    }
                }
            }

            return super.visitSimpleNameReferenceExpression(node);
        }

        @Override
        public boolean visitClass(UClass node) {
            // Skip anonymous inner classes
            return node instanceof UAnonymousClass || super.visitClass(node);
        }
    }
}
