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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_SINGLE_LINE;
import static com.android.SdkConstants.VALUE_1;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.detector.api.Constraints.minSdkLessThan;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Collection;
import java.util.Collections;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

/** Checks for the scenario which triggers https://issuetracker.google.com/issues/36950033 */
public class EllipsizeMaxLinesDetector extends LayoutDetector {
    private static final Implementation IMPLEMENTATION =
            new Implementation(EllipsizeMaxLinesDetector.class, Scope.RESOURCE_FILE_SCOPE);

    /** Combining maxLines and ellipsize */
    public static final Issue ISSUE =
            Issue.create(
                            "EllipsizeMaxLines",
                            "Combining Ellipsize and Maxlines",
                            "Combining `ellipsize` and `maxLines=1` can lead to crashes on some devices. "
                                    + "Earlier versions of lint recommended replacing `singleLine=true` with "
                                    + "`maxLines=1` but that should not be done when using `ellipsize`.",
                            Category.CORRECTNESS,
                            4,
                            Severity.ERROR,
                            IMPLEMENTATION)
                    //noinspection LintImplBadUrl -- old bug, fewer digits than usual
                    .addMoreInfo("https://issuetracker.google.com/issues/36950033");

    public static final String ATTR_ELLIPSIZE = "ellipsize";
    public static final String ATTR_LINES = "lines";
    public static final String ATTR_MAX_LINES = "maxLines";

    /** Constructs a new {@link EllipsizeMaxLinesDetector} */
    public EllipsizeMaxLinesDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_ELLIPSIZE);
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if ("end".equals(attribute.getValue())) {
            // Crash doesn't happen with ellipsize="end"
            // There may be other types that are okay but bug reports vary in details
            // on this (see comments in 36950033 and 37128179 implicating at least
            // both start and middle; I haven't chased down the actual platform bug)
            return;
        }

        if (context.getProject().getMinSdk() >= 23) {
            // This is not a problem as of Android 6.0:
            // https://issuetracker.google.com/36950033
            return;
        }

        Element element = attribute.getOwnerElement();
        Attr lines = element.getAttributeNodeNS(ANDROID_URI, ATTR_LINES);
        Attr maxLines = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_LINES);
        if (lines != null && VALUE_1.equals(lines.getValue())
                || maxLines != null && VALUE_1.equals(maxLines.getValue())) {
            Attr other = lines != null ? lines : maxLines;
            Location location = context.getLocation(other);
            location.setSecondary(context.getLocation(attribute));

            LintFix fix =
                    fix().name("Replace with singleLine=\"true\"")
                            .composite(
                                    fix().set(ANDROID_URI, ATTR_SINGLE_LINE, VALUE_TRUE).build(),
                                    fix().unset(ANDROID_URI, other.getLocalName()).build())
                            .autoFix();

            Incident incident =
                    new Incident(
                            ISSUE,
                            attribute,
                            location,
                            String.format(
                                    "Combining `ellipsize=%1$s` and `%2$s=%3$s` can lead to "
                                            + "crashes. Use `singleLine=true` instead.",
                                    element.getAttributeNS(ANDROID_URI, ATTR_ELLIPSIZE),
                                    other.getLocalName(),
                                    other.getValue()),
                            fix);
            // Fixed as of Android M
            context.report(incident, minSdkLessThan(23));
        }
    }
}
