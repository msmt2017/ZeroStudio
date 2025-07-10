/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.TAG_VECTOR;
import static com.android.tools.lint.checks.VectorDetector.isVectorGenerationSupported;
import static com.android.tools.lint.checks.VectorDetector.usingSupportLibVectors;
import static com.android.tools.lint.client.api.ResourceRepositoryScope.ALL_DEPENDENCIES;
import static com.android.tools.lint.detector.api.Constraints.minSdkLessThan;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceUrl;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/** Looks for issues with long vector paths */
public class VectorPathDetector extends ResourceXmlDetector {
    /** Paths that are too long */
    public static final Issue PATH_LENGTH =
            Issue.create(
                    "VectorPath",
                    "Long vector paths",
                    "Using long vector paths is bad for performance. There are several ways to "
                            + "make the `pathData` shorter:\n"
                            + "* Using less precision\n"
                            + "* Removing some minor details\n"
                            + "* Using the Android Studio vector conversion tool\n"
                            + "* Rasterizing the image (converting to PNG)",
                    Category.PERFORMANCE,
                    5,
                    Severity.WARNING,
                    new Implementation(VectorPathDetector.class, Scope.RESOURCE_FILE_SCOPE));

    /** Path validation */
    public static final Issue PATH_VALID =
            Issue.create(
                            "InvalidVectorPath",
                            "Invalid vector paths",
                            "This check ensures that vector paths are valid. For example, it makes "
                                    + "sure that the numbers are not using scientific notation (such as 1.0e3) "
                                    + "which can lead to runtime crashes on older devices. As another example, "
                                    + "it flags numbers like `.5` which should be written as `0.5` instead to "
                                    + "avoid crashes on some pre-Marshmallow devices.",
                            Category.CORRECTNESS,
                            5,
                            Severity.ERROR,
                            new Implementation(VectorPathDetector.class, Scope.RESOURCE_FILE_SCOPE))
                    //noinspection LintImplBadUrl -- old bug, fewer digits than usual
                    .addMoreInfo("https://issuetracker.google.com/37008268");

    // Arbitrary limit suggested in https://code.google.com/p/android/issues/detail?id=235219
    static final int MAX_PATH_DATA_LENGTH = 800;

    /** Constructs a new {@link VectorPathDetector} */
    public VectorPathDetector() {}

    @Nullable
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("pathData");
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith(PREFIX_RESOURCE_REF)) {
            ResourceUrl url = ResourceUrl.parse(value);
            if (url == null || url.isFramework()) {
                return;
            }
            LintClient client = context.getClient();
            Project project = context.getProject();
            ResourceRepository repository = client.getResources(project, ALL_DEPENDENCIES);
            List<ResourceItem> item =
                    repository.getResources(ResourceNamespace.TODO(), url.type, url.name);
            if (item.isEmpty()) {
                return;
            }
            ResourceValue resourceValue = item.get(0).getResourceValue();
            if (resourceValue == null) {
                return;
            }
            value = resourceValue.getValue();
            if (value == null) {
                return;
            }
        }

        // Make sure this is in a vector; pathData can occur in transition files too!
        Element root = attribute.getOwnerDocument().getDocumentElement();
        if (root == null || !root.getTagName().equals(TAG_VECTOR)) {
            return;
        }

        validatePath(context, attribute, value);

        int length = countPathLength(value);
        if (length < MAX_PATH_DATA_LENGTH) {
            return;
        }

        // Only warn if we're not already rasterizing
        if (isRasterizingVector(context)) {
            return;
        }

        String message =
                String.format(
                        Locale.getDefault(),
                        "Very long vector path (%1$d characters), which is bad for "
                                + "performance. Considering reducing precision, removing minor details or "
                                + "rasterizing vector.",
                        length);
        Incident incident =
                new Incident(PATH_LENGTH, attribute, context.getValueLocation(attribute), message);
        context.report(incident);
    }

    private int countPathLength(String value) {
        int count = 0;
        boolean prevSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isSpace = Character.isWhitespace(c);
            if (!isSpace || !prevSpace) {
                count++;
            }
            prevSpace = isSpace;
        }
        return count;
    }

    private static boolean isRasterizingVector(XmlContext context) {
        // If minSdkVersion >= 21, we're not generating compatibility vector icons
        Project project = context.getProject();
        if (project.getMinSdkVersion().getFeatureLevel() >= 21) {
            return false;
        }

        // If this vector asset is in a -v21 folder, we're not generating vector assets
        if (context.getFolderVersion() >= 21) {
            return false;
        }

        // Not using a plugin that supports vector image generation?
        if (!isVectorGenerationSupported(context.getProject())) {
            return false;
        }

        if (usingSupportLibVectors(project)) {
            return false;
        }

        // Vector generation is only done for Gradle projects
        return project.isGradleProject();
    }

    /**
     * Check the given path data and throw a number format exception (containing the exact invalid
     * string) if it finds a problem
     */
    public void validatePath(
            @NonNull XmlContext context, @NonNull Attr attribute, @NonNull String path) {
        if (context.getProject().getMinSdkVersion().getFeatureLevel() >= 23) {
            // Fixed in lollipop
            return;
        }
        int start = 0;
        int end = 1;
        while (end < path.length()) {
            end = findNextStart(path, end);
            int trimStart = start;
            while (trimStart < end && Character.isWhitespace(path.charAt(trimStart))) {
                trimStart++;
            }
            int trimEnd = end;
            while (trimEnd > trimStart + 1 && Character.isWhitespace(path.charAt(trimEnd - 1))) {
                trimEnd--;
            }
            if (trimEnd > trimStart) {
                checkFloats(context, attribute, path, trimStart, trimEnd);
            }
            start = end;
            end++;
        }
    }

    // This code is based on the path parser in the platform. However, it focuses
    // on just validating the numbers - and since it doesn't have to build up actual
    // path nodes etc the code was simplified significantly such that it doesn't really
    // resemble the original code.
    // (frameworks/support/compat/java/android/support/v4/graphics/PathParser.java)

    private void checkFloats(
            @NonNull XmlContext context,
            @NonNull Attr attribute,
            @NonNull String s,
            int start,
            int end) {
        if (s.charAt(start) == 'z' || s.charAt(start) == 'Z') {
            return;
        }
        int startPosition = start + 1;
        while (startPosition < end) {
            int currentIndex = startPosition;
            boolean foundSeparator = false;
            boolean endWithNegOrDot = false;
            boolean hasExponential = false;
            boolean secondDot = false;
            boolean isExponential = false;
            for (; currentIndex < end; currentIndex++) {
                boolean isPrevExponential = isExponential;
                isExponential = false;
                char currentChar = s.charAt(currentIndex);
                switch (currentChar) {
                    case ' ':
                    case ',':
                    case '\t':
                    case '\n':
                        foundSeparator = true;
                        break;
                    case '-':
                        if (currentIndex != startPosition && !isPrevExponential) {
                            foundSeparator = true;
                            endWithNegOrDot = true;
                        }
                        break;
                    case '.':
                        if (!secondDot) {
                            secondDot = true;
                        } else {
                            foundSeparator = true;
                            endWithNegOrDot = true;
                        }
                        break;
                    case 'e':
                    case 'E':
                        hasExponential = isExponential = true;
                        break;
                }
                if (foundSeparator) {
                    break;
                }
            }

            // Scientific notation not supported everywhere
            if (hasExponential
                    && startPosition < currentIndex
                    // This was fixed in Lollipop
                    && context.getProject().getMinSdkVersion().getFeatureLevel() < 21) {
                String number = s.substring(startPosition, currentIndex);

                String converted = null;
                try {
                    // See http://stackoverflow.com/questions/16098046/
                    //    how-to-print-double-value-without-scientific-notation-using-java
                    // for an explanation for why we do this big workaround
                    DecimalFormat df =
                            new DecimalFormat(
                                    "0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                    // DecimalFormat.DOUBLE_FRACTION_DIGITS = 340 but is package private
                    df.setMaximumFractionDigits(340);
                    converted = df.format(Double.parseDouble(number));
                } catch (NumberFormatException ignore) {
                }

                String message =
                        String.format(
                                "Avoid scientific notation (`%1$s`) in vector "
                                        + "paths because it can lead to crashes on some devices.",
                                number);
                if (converted != null) {
                    message += " Use `" + converted + "` instead.";
                }
                reportInvalidPathData(
                        context,
                        attribute,
                        s,
                        startPosition,
                        currentIndex,
                        number,
                        converted,
                        message,
                        "Replace scientific notation",
                        21);
            }

            if ((s.startsWith(".", startPosition)
                    || s.startsWith("-.", startPosition)
                            // This was fixed in Marshmallow
                            && context.getProject().getMinSdkVersion().getFeatureLevel() < 23)) {
                String number = s.substring(startPosition, currentIndex);
                String replace =
                        number.startsWith(".") ? ("0" + number) : ("-0." + number.substring(2));

                String message =
                        String.format(
                                "Use %1$s instead of %2$s to avoid crashes on some devices",
                                replace, number);

                if (number.startsWith(".")
                        && startPosition > 0
                        && Character.isDigit(s.charAt(startPosition - 1))) {
                    // Some SVG files pack numbers adjacent to each other without a separating
                    // space when the dot implies a space, e.g. "1.2.3" is the number "1.2"
                    // followed by ".3" (0.3). If we just replace the ".3" with "0.3" we end
                    // up with "1.20.3", which is the same as before: 1.20 and 0.3. We need
                    // a separating space here: "1.2 0.3"
                    replace = " " + replace;
                }

                reportInvalidPathData(
                        context,
                        attribute,
                        s,
                        startPosition,
                        currentIndex,
                        number,
                        replace,
                        message,
                        "Add leading 0",
                        23);
            }

            int endPosition = currentIndex;
            if (endWithNegOrDot) {
                // Keep the '-' or '.' sign with next number.
                startPosition = endPosition;
            } else {
                startPosition = endPosition + 1;
            }
        }
    }

    private void reportInvalidPathData(
            @NonNull XmlContext context,
            @NonNull Attr attribute,
            @NonNull String s,
            int startPosition,
            int currentIndex,
            @NonNull String number,
            @Nullable String replace,
            @NonNull String message,
            @NonNull String sharedName,
            int minApiLessThan) {
        Location location = context.getValueLocation(attribute);
        Position startPos = location.getStart();
        assert startPos != null;

        // If the attribute contains the literal value (not a resource
        // reference) point to the specific location
        LintFix lintFix = null;
        if (s.equals(attribute.getValue())) {
            location =
                    Location.create(
                            context.file,
                            context.getContents(),
                            startPos.getOffset() + startPosition,
                            startPos.getOffset() + currentIndex);
            if (replace != null) {
                lintFix =
                        fix().name("Replace with " + replace.trim())
                                .sharedName(sharedName)
                                .replace()
                                .text(number)
                                .with(replace)
                                .range(location)
                                .autoFix()
                                .build();
            }
        }

        Incident incident = new Incident(PATH_VALID, attribute, location, message, lintFix);
        context.report(incident, minSdkLessThan(minApiLessThan));
    }

    private static int findNextStart(String s, int end) {
        while (end < s.length()) {
            char c = s.charAt(end);
            if ((((c - 'A') * (c - 'Z') <= 0) || ((c - 'a') * (c - 'z') <= 0))
                    && c != 'e'
                    && c != 'E') {
                return end;
            }
            end++;
        }
        return end;
    }
}
