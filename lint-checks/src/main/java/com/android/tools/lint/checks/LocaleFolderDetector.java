/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static com.android.ide.common.resources.configuration.FolderConfiguration.QUALIFIER_SPLITTER;
import static com.android.ide.common.resources.configuration.LocaleQualifier.BCP_47_PREFIX;
import static com.android.tools.lint.detector.api.Constraints.minSdkLessThan;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceContext;
import com.android.tools.lint.detector.api.ResourceFolderScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.uast.UCallExpression;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks for errors related to locale handling */
public class LocaleFolderDetector extends Detector
        implements ResourceFolderScanner, SourceCodeScanner, ClassScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(LocaleFolderDetector.class, Scope.RESOURCE_FOLDER_SCOPE);

    /** Using a locale folder that is not consulted */
    public static final Issue DEPRECATED_CODE =
            Issue.create(
                            "LocaleFolder",
                            "Wrong locale name",
                            "From the `java.util.Locale` documentation:\n"
                                    + "\"Note that Java uses several deprecated two-letter codes. The Hebrew (\"he\") "
                                    + "language code is rewritten as \"iw\", Indonesian (\"id\") as \"in\", and "
                                    + "Yiddish (\"yi\") as \"ji\". This rewriting happens even if you construct your "
                                    + "own Locale object, not just for instances returned by the various lookup methods.\n"
                                    + "\n"
                                    + "Because of this, if you add your localized resources in for example `values-he` "
                                    + "they will not be used, since the system will look for `values-iw` instead.\n"
                                    + "\n"
                                    + "To work around this, place your resources in a `values` folder using the "
                                    + "deprecated language code instead.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://developer.android.com/reference/java/util/Locale.html")
                    .setAndroidSpecific(true);

    /** Using a region that might not be a match for the given language */
    public static final Issue WRONG_REGION =
            Issue.create(
                            "WrongRegion",
                            "Suspicious Language/Region Combination",
                            "Android uses the letter codes ISO 639-1 for languages, and the letter codes "
                                    + "ISO 3166-1 for the region codes. In many cases, the language code and the "
                                    + "country where the language is spoken is the same, but it is also often not "
                                    + "the case. For example, while 'se' refers to Sweden, where Swedish is spoken, "
                                    + "the language code for Swedish is **not** `se` (which refers to the Northern "
                                    + "Sami language), the language code is `sv`. And similarly the region code for "
                                    + "`sv` is El Salvador.\n"
                                    + "\n"
                                    + "This lint check looks for suspicious language and region combinations, to help "
                                    + "catch cases where you've accidentally used the wrong language or region code. "
                                    + "Lint knows about the most common regions where a language is spoken, and if "
                                    + "a folder combination is not one of these, it is flagged as suspicious.\n"
                                    + "\n"
                                    + "Note however that it may not be an error: you can theoretically have speakers "
                                    + "of any language in any region and want to target that with your resources, so "
                                    + "this check is aimed at tracking down likely mistakes, not to enforce a specific "
                                    + "set of region and language combinations.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    public static final Issue USE_ALPHA_2 =
            Issue.create(
                            "UseAlpha2",
                            "Using 3-letter Codes",
                            "For compatibility with earlier devices, you should only use 3-letter language "
                                    + "and region codes when there is no corresponding 2 letter code.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .addMoreInfo("https://tools.ietf.org/html/bcp47")
                    .setAndroidSpecific(true);

    public static final Issue INVALID_FOLDER =
            Issue.create(
                            "InvalidResourceFolder",
                            "Invalid Resource Folder",
                            "This lint check looks for a folder name that is not a valid resource folder "
                                    + "name; these will be ignored and not packaged by the Android Gradle build plugin.\n"
                                    + "\n"
                                    + "Note that the order of resources is very important; for example, you can't specify "
                                    + "a language before a network code.\n"
                                    + "\n"
                                    + "Similarly, note that to use 3 letter region codes, you have to use "
                                    + "a special BCP 47 syntax: the prefix b+ followed by the BCP 47 language tag but "
                                    + "with `+` as the individual separators instead of `-`. Therefore, for the BCP 47 "
                                    + "language tag `nl-ABW` you have to use `b+nl+ABW`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/resources/providing-resources.html")
                    .addMoreInfo("https://tools.ietf.org/html/bcp47")
                    .setAndroidSpecific(true);

    /**
     * Crashes if using 3-letter resources in an app *and* calling AssetManager#getLocales()
     * directly or indirectly
     */
    public static final Issue GET_LOCALES =
            Issue.create(
                            "GetLocales",
                            "Locale crash",
                            "This check looks for usage of Lollipop-style locale folders "
                                    + "(e.g. 3 letter language codes, or BCP 47 qualifiers) combined "
                                    + "with an `AssetManager#getLocales()` call. This leads to crashes",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    LocaleFolderDetector.class,
                                    EnumSet.of(
                                            Scope.RESOURCE_FOLDER,
                                            Scope.JAVA_FILE,
                                            Scope.JAVA_LIBRARIES),
                                    // In IDE: won't have JAVA_LIBRARIES scope (no bytecode
                                    // analysis)
                                    EnumSet.of(Scope.RESOURCE_FOLDER, Scope.JAVA_FILE)))
                    .setAndroidSpecific(true);

    private static final String KEY_MESSAGE = "message";

    private Map<String, File> mBcp47Folders;

    private List<File> threeLetterLocales;

    /** Constructs a new {@link LocaleFolderDetector} */
    public LocaleFolderDetector() {}

    // ---- Implements ResourceFolderScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void checkFolder(@NonNull ResourceContext context, @NonNull String folderName) {
        LocaleQualifier locale =
                context.getFolderConfiguration() != null
                        ? context.getFolderConfiguration().getLocaleQualifier()
                        : null;
        if (locale != null && locale.hasLanguage()) {
            final String language = locale.getLanguage();
            assert language != null; // hasLanguage() above.
            String replace = null;
            switch (language) {
                case "he":
                    replace = "iw";
                    break;
                case "id":
                    replace = "in";
                    break;
                case "yi":
                    replace = "ji";
                    break;
            }
            // Note: there is also fil⇒tl

            if (language.length() >= 3) {
                if (threeLetterLocales == null) {
                    threeLetterLocales = Lists.newArrayList();
                }
                threeLetterLocales.add(context.file);
            }

            if (replace != null) {
                // TODO: Check for suppress somewhere other than lint.xml?
                String message =
                        String.format(
                                "The locale folder \"`%1$s`\" should be "
                                        + "called \"`%2$s`\" instead; see the "
                                        + "`java.util.Locale` documentation",
                                language, replace);
                context.report(
                        new Incident(DEPRECATED_CODE, Location.create(context.file), message));
            }

            if (language.length() == 3) {
                String languageAlpha2 =
                        LocaleManager.getLanguageAlpha2(language.toLowerCase(Locale.US));
                if (languageAlpha2 != null) {
                    String message =
                            String.format(
                                    "For compatibility, should use 2-letter "
                                            + "language codes when available; use `%1$s` instead of `%2$s`",
                                    languageAlpha2, language);
                    context.report(
                            new Incident(USE_ALPHA_2, Location.create(context.file), message));
                }
            }

            String region = locale.getRegion();
            if (region != null
                    && locale.hasRegion()
                    && region.length() == 3
                    && !Character.isDigit(region.charAt(0))) {
                String regionAlpha2 = LocaleManager.getRegionAlpha2(region.toUpperCase(Locale.UK));
                if (regionAlpha2 != null) {
                    String message =
                            String.format(
                                    "For compatibility, should use 2-letter "
                                            + "region codes when available; use `%1$s` instead of `%2$s`",
                                    regionAlpha2, region);
                    context.report(
                            new Incident(USE_ALPHA_2, Location.create(context.file), message));
                }
            }

            if (region != null && region.length() == 2) {
                List<String> relevantRegions = LocaleManager.getRelevantRegions(language);
                if (!relevantRegions.isEmpty()
                        && !relevantRegions.contains(region)
                        && LocaleManager.isValidRegionCode(region)) {
                    List<String> sortedRegions = sortRegions(language, relevantRegions);
                    List<String> suggestions = Lists.newArrayList();
                    for (String code : sortedRegions) {
                        suggestions.add(code + " (" + LocaleManager.getRegionName(code) + ")");
                    }

                    String message =
                            String.format(
                                    "Suspicious language and region combination %1$s (%2$s) "
                                            + "with %3$s (%4$s): language %5$s is usually "
                                            + "paired with: %6$s",
                                    language,
                                    LocaleManager.getLanguageName(language),
                                    region,
                                    LocaleManager.getRegionName(region),
                                    language,
                                    Joiner.on(", ").join(suggestions));
                    context.report(
                            new Incident(WRONG_REGION, Location.create(context.file), message));
                }
            }
        }

        FolderConfiguration config = FolderConfiguration.getConfigForFolder(folderName);
        if (ResourceFolderType.getFolderType(folderName) != null && config == null) {
            String message =
                    "Invalid resource folder: make sure qualifiers appear in the "
                            + "correct order, are spelled correctly, etc.";
            String bcpSuggestion = suggestBcp47Correction(folderName);
            if (bcpSuggestion != null) {
                message =
                        String.format(
                                "Invalid resource folder; did you mean `%1$s` ?", bcpSuggestion);
            }
            context.report(new Incident(INVALID_FOLDER, Location.create(context.file), message));
        } else if (locale != null
                && folderName.contains(BCP_47_PREFIX)
                && config != null
                && config.getLocaleQualifier() != null) {
            if (mBcp47Folders == null) {
                mBcp47Folders = Maps.newHashMap();
            }
            if (!mBcp47Folders.containsKey(folderName)) {
                mBcp47Folders.put(folderName, context.file);
            }
        }
    }

    /**
     * Look at the given folder name and see if it looks like an unintentional attempt to use
     * 3-letter language codes or region codes, and if so, suggest a replacement.
     *
     * @param folderName a folder name
     * @return a suggestion, or null
     */
    @Nullable
    @VisibleForTesting
    static String suggestBcp47Correction(String folderName) {
        String language = null;
        String region = null;
        Iterator<String> iterator = QUALIFIER_SPLITTER.split(folderName).iterator();
        // Skip folder type
        if (!iterator.hasNext()) {
            return null;
        }
        iterator.next();

        while (iterator.hasNext()) {
            String segment = iterator.next();
            String original = segment;
            int length = segment.length();
            if (language != null) {
                // Only look for region
                segment = segment.toUpperCase(Locale.US);
                if (length == 3) {
                    if (original.charAt(0) == 'r'
                            && Character.isUpperCase(original.charAt(1))
                            && LocaleManager.isValidRegionCode(segment.substring(1))) {
                        region = segment.substring(1);
                        break;
                    } else if (Character.isDigit(original.charAt(0))) {
                        region = segment;
                    } else if (LocaleManager.isValidRegionCode(segment)) {
                        region = segment;
                    }
                } else if (length == 4
                        && original.charAt(0) == 'r'
                        && Character.isUpperCase(original.charAt(1))) {
                    if (LocaleManager.isValidRegionCode(segment.substring(1))) {
                        region = segment.substring(1);
                        break;
                    }
                }
            } else {
                segment = segment.toLowerCase(Locale.US);
                if ("car".equals(segment)) { // "car" is a valid value for UI mode
                    return null;
                }
                if (LocaleManager.isValidLanguageCode(segment)) {
                    language = segment;
                }
            }
        }

        if (language != null) {
            if (language.length() == 3) {
                String better = LocaleManager.getLanguageAlpha2(language);
                if (better != null) {
                    language = better;
                }
            }
            if (region != null) {
                if (region.length() == 3 && !Character.isDigit(region.charAt(0))) {
                    String better = LocaleManager.getRegionAlpha2(region);
                    if (better != null) {
                        region = better;
                    }
                }
                return BCP_47_PREFIX + language + '+' + region;
            }
            return BCP_47_PREFIX + language;
        }

        return null;
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        // Ensure that if a language has multiple scripts, either minSdkVersion >= 21 or
        // at most one folder does not have -v21 among the script options
        if (mBcp47Folders != null
                && !context.getProject().getMinSdkVersion().isAtLeast(21)) {
            Map<FolderConfiguration, File> configToFile = Maps.newHashMap();
            Multimap<String, FolderConfiguration> languageToConfigs = ArrayListMultimap.create();
            for (String folderName : mBcp47Folders.keySet()) {
                FolderConfiguration config = FolderConfiguration.getConfigForFolder(folderName);
                assert config != null : folderName; // we checked before adding to mBcp47Folders
                LocaleQualifier locale = config.getLocaleQualifier();
                assert locale != null : folderName;
                configToFile.put(config, mBcp47Folders.get(folderName));
                String key = locale.getLanguage();
                assert key != null; // we checked before adding to mBcp47Folders
                if (locale.hasRegion()) {
                    key = key + '_' + locale.getRegion();
                }
                languageToConfigs.put(key, config);
            }

            for (String language : languageToConfigs.keySet()) {
                Collection<FolderConfiguration> configs = languageToConfigs.get(language);
                if (configs.size() <= 1) {
                    // No conflict
                    // TODO: Warn if you specify a script and don't provide a fallback?
                    continue;
                }
                // Count folders that do not specify -v21 and that don't vary in anything other
                // than script
                List<FolderConfiguration> candidates =
                        Lists.newArrayListWithExpectedSize(configs.size());
                for (FolderConfiguration config : configs) {
                    if (config.getVersionQualifier() != null
                            && config.getVersionQualifier().getVersion() >= 21) {
                        continue;
                    }
                    // See if sets anything *other* than the locale qualifier
                    boolean localeOnly = true;
                    for (int i = 0, n = FolderConfiguration.getQualifierCount(); i < n; i++) {
                        ResourceQualifier qualifier = config.getQualifier(i);
                        if (ResourceQualifier.isValid(qualifier)
                                && !(qualifier instanceof LocaleQualifier)) {
                            localeOnly = false;
                            break;
                        }
                    }
                    if (!localeOnly) {
                        continue;
                    }
                    candidates.add(config);
                }

                if (candidates.size() > 1) {
                    Collections.sort(candidates); // ensure stable test output
                    Location location = null;
                    List<String> folderNames = Lists.newArrayList();
                    for (int i = candidates.size() - 1; i >= 0; i--) {
                        FolderConfiguration config = candidates.get(i);
                        File dir = configToFile.get(config);
                        assert dir != null : config;
                        Location secondary = location;
                        location = Location.create(dir);
                        location.setSecondary(secondary);
                        folderNames.add(dir.getName());
                    }
                    String message =
                            String.format(
                                    "Multiple locale folders for language `%1$s` map to a single folder in versions < API 21: %2$s",
                                    language, Joiner.on(", ").join(folderNames));
                    assert location != null; // because we will iterate at least once in the loop
                    Incident incident = new Incident(INVALID_FOLDER, location, message);
                    context.report(incident, minSdkLessThan(21));
                }
            }
        }
    }

    /**
     * Sort the "usually combined with" regions such that the preferred region for the language is
     * first, followed by the default primary region (if not the same, followed by the same letter
     * codes, followed by alphabetical order.
     */
    private static List<String> sortRegions(
            @NonNull final String language, @NonNull List<String> regions) {
        List<String> sortedRegions = Lists.newArrayList(regions);
        final String primary = LocaleManager.getDefaultLanguageRegion(language);
        sortedRegions.sort(
                (r1, r2) -> {
                    int rank1 = r1.equals(primary) ? 1 : 2;
                    int rank2 = r2.equals(primary) ? 1 : 2;
                    int delta = rank1 - rank2;
                    if (delta == 0) {
                        delta = r1.compareTo(r2);
                    }
                    return delta;
                });
        return sortedRegions;
    }

    // implements SourceCodeScanner

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("getLocales");
    }

    @Override
    public void visitMethodCall(
            @NonNull JavaContext context,
            @NonNull UCallExpression call,
            @NonNull PsiMethod method) {
        if (threeLetterLocales == null || context.getProject().getMinSdk() >= 21) {
            return;
        }

        if (!context.getEvaluator()
                .isMemberInSubClassOf(method, "android.content.res.AssetManager", false)) {
            return;
        }

        Location location = context.getLocation(call);
        Location prev = location;
        List<String> folderNames = Lists.newArrayListWithCapacity(threeLetterLocales.size());
        Collections.sort(threeLetterLocales);
        for (File file : threeLetterLocales) {
            prev = Location.create(file).withSecondary(prev, "Locale folder here", false);
            folderNames.add(file.getName());
        }
        Incident incident = new Incident(GET_LOCALES, call, location, "");
        String formatString =
                "The app will crash on platforms older than v21 "
                        + "(minSdkVersion is %1$d) because `AssetManager#getLocales` is called and it "
                        + "contains one or more v21-style (3-letter or BCP47 locale) folders: "
                        + Joiner.on(", ").join(folderNames);
        context.report(incident, map().put(KEY_MESSAGE, formatString));
    }

    // Implements ClassScanner

    private boolean foundGetLocaleCall;

    @SuppressWarnings("rawtypes") // ASM API
    @Override
    public void checkClass(@NonNull final ClassContext context, @NonNull ClassNode classNode) {
        if (threeLetterLocales == null
                || context.getProject().getMinSdk() >= 21
                || foundGetLocaleCall) {
            return;
        }

        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;

            InsnList nodes = method.instructions;

            for (int i = 0, n = nodes.size(); i < n; i++) {
                AbstractInsnNode instruction = nodes.get(i);
                int type = instruction.getType();
                if (type == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode node = (MethodInsnNode) instruction;
                    if (node.owner.equals("android/content/res/AssetManager")
                            && node.name.equals("getLocales")
                            && node.desc.equals("()[Ljava/lang/String;")) {
                        foundGetLocaleCall = true;

                        // Report all the locations where this happens
                        Collections.sort(threeLetterLocales);
                        File jarFile = context.getJarFile();
                        LintClient client = context.getClient();
                        for (File file : threeLetterLocales) {
                            String jarPath =
                                    jarFile != null
                                            ? Lint.getFileNameWithParent(client, jarFile)
                                            : "?";
                            Location location = Location.create(file);
                            Incident incident = new Incident(GET_LOCALES, location, "");
                            String formatString =
                                    "The app will crash on platforms older "
                                            + "than v21 (minSdkVersion is %1$d) because"
                                            + " `AssetManager#getLocales()` is called (from the library "
                                            + "jar file "
                                            + jarPath
                                            + ") and this folder resource name only works "
                                            + "on v21 or later with that call present in the app";
                            context.report(incident, map().put(KEY_MESSAGE, formatString));
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean filterIncident(
            @NonNull Context context, @NonNull Incident incident, @NonNull LintMap map) {
        assert (incident.getIssue() == GET_LOCALES);
        if (context.getMainProject().getMinSdk() < 21) {
            int minSdk = context.getMainProject().getMinSdk();
            assert minSdk < 21;
            String formatString = map.getString(KEY_MESSAGE, "");
            String message = String.format(Locale.getDefault(), formatString, minSdk);
            incident.setMessage(message);
            return true;
        }
        return false;
    }
}
