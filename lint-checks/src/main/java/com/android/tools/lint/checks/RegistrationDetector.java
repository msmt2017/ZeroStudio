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

import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.SdkConstants.CLASS_APPLICATION;
import static com.android.SdkConstants.CLASS_BROADCASTRECEIVER;
import static com.android.SdkConstants.CLASS_CONTENTPROVIDER;
import static com.android.SdkConstants.CLASS_SERVICE;
import static com.android.SdkConstants.TAG_ACTIVITY;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_PROVIDER;
import static com.android.SdkConstants.TAG_RECEIVER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintMap;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.PartialResult;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.utils.XmlUtils;

import com.google.common.collect.Maps;

import org.jetbrains.uast.UAnnotated;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Checks for missing manifest registrations for activities, services etc and also makes sure that
 * they are registered with the correct tag
 */
public class RegistrationDetector extends LayoutDetector implements SourceCodeScanner {
    /** Unregistered activities and services */
    public static final Issue ISSUE =
            Issue.create(
                            "Registered",
                            "Class is not registered in the manifest",
                            "Activities, services and content providers should be registered in the"
                                + " `AndroidManifest.xml` file using `<activity>`, `<service>` and"
                                + " `<provider>` tags.\n"
                                + "\n"
                                + "If your activity is simply a parent class intended to be"
                                + " subclassed by other \"real\" activities, make it an abstract"
                                + " class.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            new Implementation(RegistrationDetector.class, Scope.JAVA_FILE_SCOPE))
                    .addMoreInfo(
                            "https://developer.android.com/guide/topics/manifest/manifest-intro.html")
                    .setAndroidSpecific(true)
                    // Temporary workaround for
                    // https://code.google.com/p/android/issues/detail?id=227579
                    // The real solution is to have a merged manifest, which is coming
                    .setEnabledByDefault(false);

    protected Map<String, String> mManifestRegistrations;

    private static final String KEY_LOCATION = "location";
    private static final String KEY_ID = "id";

    /** Constructs a new {@link RegistrationDetector} */
    public RegistrationDetector() {}

    @Nullable
    private Map<String, String> getManifestRegistrations(@NonNull Project mainProject) {
        if (mManifestRegistrations == null) {
            Document mergedManifest = mainProject.getMergedManifest();
            if (mergedManifest == null || mergedManifest.getDocumentElement() == null) {
                return null;
            }
            mManifestRegistrations = Maps.newHashMap();
            Element application =
                    XmlUtils.getFirstSubTagByName(
                            mergedManifest.getDocumentElement(), TAG_APPLICATION);
            if (application != null) {
                registerElement(mainProject, application);
                for (Element c : XmlUtils.getSubTags(application)) {
                    registerElement(mainProject, c);
                }
            }
        }

        return mManifestRegistrations;
    }

    private void registerElement(Project project, Element c) {
        String fqcn = Lint.resolveManifestName(c, project);
        String tag = c.getTagName();
        String frameworkClass = tagToClass(tag);
        if (frameworkClass != null) {
            mManifestRegistrations.put(fqcn, frameworkClass);
            if (fqcn.indexOf('$') != -1) {
                // The internal name contains a $ which means it's an inner class.
                // The conversion from fqcn to internal name is a bit ambiguous:
                // "a.b.C.D" usually means "inner class D in class C in package a.b".
                // However, it can (see issue 31592) also mean class D in package "a.b.C".
                // Place *both* of these possibilities in the registered map, since this
                // is only used to check that an activity is registered, not the other way
                // (so it's okay to have entries there that do not correspond to real classes).
                fqcn = fqcn.replace('$', '.');
                mManifestRegistrations.put(fqcn, frameworkClass);
            }
        }
    }

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                // Common super class for Activity, ContentProvider, Service, Application
                // (as well as some other classes not registered in the manifest, such as
                // Fragment and VoiceInteractionSession)
                "android.content.ComponentCallbacks2", CLASS_BROADCASTRECEIVER);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass cls) {
        // If a library project provides additional activities, it is not an error to
        // not register all of those here
        if (context.getProject().isLibrary()) {
            return;
        }

        if (context.isTestSource()) {
            // Don't flag activities registered in test source sets
            return;
        }

        if (cls.getName() == null) {
            // anonymous class; can't be registered
            return;
        }

        JavaEvaluator evaluator = context.getEvaluator();
        if (evaluator.isAbstract(cls) || evaluator.isPrivate(cls)) {
            // Abstract classes do not need to be registered, and
            // private classes are clearly not intended to be registered
            return;
        }

        String rightTag = getTag(evaluator, cls);
        if (rightTag == null) {
            // some non-registered Context, such as a BackupAgent
            return;
        }

        if (rightTag.equals(TAG_RECEIVER)) {
            // Receivers can be registered in code; don't flag these.
            return;
        }

        String className = cls.getQualifiedName();
        if (className == null) {
            return;
        }

        if (!context.isGlobalAnalysis()) {
            if (!context.getDriver().isSuppressed(context, ISSUE, (UAnnotated) cls)) {
                LintMap map = new LintMap();
                map.put(KEY_LOCATION, context.getNameLocation(cls));
                map.put(KEY_ID, rightTag);
                context.getPartialResults(ISSUE).map().put(className, map);
            }
            return;
        }

        Map<String, String> manifestRegistrations =
                getManifestRegistrations(context.getMainProject());
        if (manifestRegistrations != null) {
            String framework = manifestRegistrations.get(className);
            if (framework == null) {
                Location location = context.getNameLocation(cls);
                String message = getMissingMessage(className, rightTag);
                context.report(ISSUE, cls, location, message);
            }
            // Checking that the registered classes extends the right type is handled
            // by the MissingClassDetector
        }
    }

    @Override
    public void checkPartialResults(
            @NonNull Context context, @NonNull PartialResult partialResults) {
        Map<String, String> manifestRegistrations =
                getManifestRegistrations(context.getMainProject());
        if (manifestRegistrations != null) {
            for (LintMap map : partialResults.maps()) {
                for (String className : map) {
                    String framework = manifestRegistrations.get(className);
                    if (framework == null) {
                        LintMap m = map.getMap(className);
                        if (m != null) {
                            Location location = m.getLocation(KEY_LOCATION);
                            String rightTag = m.getString(KEY_ID, null);
                            if (location != null && rightTag != null) {
                                String message = getMissingMessage(className, rightTag);
                                context.report(new Incident(ISSUE, location, message));
                            }
                        }
                    }
                }
            }
        }
    }

    @NonNull
    private static String getMissingMessage(@NonNull String className, @NonNull String tag) {
        return String.format("The `<%1$s> %2$s` is not registered in the manifest", tag, className);
    }

    private static String getTag(@NonNull JavaEvaluator evaluator, @NonNull UClass cls) {
        String tag = null;
        for (String s : sClasses) {
            if (evaluator.extendsClass(cls.getJavaPsi(), s, false)) {
                tag = classToTag(s);
                break;
            }
        }
        return tag;
    }

    /** The manifest tags we care about */
    private static final String[] sTags =
            new String[] {TAG_ACTIVITY, TAG_SERVICE, TAG_RECEIVER, TAG_PROVIDER, TAG_APPLICATION
                // Keep synchronized with {@link #sClasses}
            };

    /** The corresponding framework classes that the tags in {@link #sTags} should extend */
    private static final String[] sClasses =
            new String[] {
                CLASS_ACTIVITY,
                CLASS_SERVICE,
                CLASS_BROADCASTRECEIVER,
                CLASS_CONTENTPROVIDER,
                CLASS_APPLICATION
                // Keep synchronized with {@link #sTags}
            };

    /** Looks up the corresponding framework class a given manifest tag's class should extend */
    private static String tagToClass(String tag) {
        for (int i = 0, n = sTags.length; i < n; i++) {
            if (sTags[i].equals(tag)) {
                return sClasses[i];
            }
        }

        return null;
    }

    /** Looks up the tag a given framework class should be registered with */
    protected static String classToTag(String className) {
        for (int i = 0, n = sClasses.length; i < n; i++) {
            if (sClasses[i].equals(className)) {
                return sTags[i];
            }
        }

        return null;
    }
}
