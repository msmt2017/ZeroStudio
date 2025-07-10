/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.SdkConstants.DOT_KTS;
import static com.android.SdkConstants.DOT_PROPERTIES;
import static com.android.SdkConstants.DOT_XML;
import static com.android.utils.CharSequences.indexOf;
import static com.android.utils.CharSequences.startsWith;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.OtherFileScanner;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import java.util.EnumSet;

/** Looks for merge markers left behind in the source files. */
public class MergeMarkerDetector extends Detector implements OtherFileScanner {
    /** Packaged private key files */
    public static final Issue ISSUE =
            Issue.create(
                    "MergeMarker",
                    "Code contains merge marker",
                    "Many version control systems leave unmerged files with markers such as <<< in "
                            + "the source code. This check looks for these markers, which are sometimes "
                            + "accidentally left in, particularly in resource files where they don't "
                            + "break compilation.",
                    Category.CORRECTNESS,
                    8,
                    Severity.ERROR,
                    new Implementation(MergeMarkerDetector.class, Scope.OTHER_SCOPE));

    private static final String[] targetFileTypes = {DOT_GRADLE, DOT_KTS, DOT_PROPERTIES, DOT_XML};

    /** Constructs a new {@link MergeMarkerDetector} check */
    public MergeMarkerDetector() {}

    // ---- Implements OtherFileScanner ----

    @NonNull
    @Override
    public EnumSet<Scope> getApplicableFiles() {
        return Scope.OTHER_SCOPE;
    }

    @Override
    public void run(@NonNull Context context) {
        if (!context.getProject().getReportIssues()) {
            // If this is a library project not being analyzed, ignore it
            return;
        }

        boolean shouldCheckFile = false;
        String path = context.file.getPath();
        for (String ext : targetFileTypes) {
            if (path.endsWith(ext)) {
                shouldCheckFile = true;
                break;
            }
        }
        if (!shouldCheckFile) {
            return;
        }

        CharSequence source = context.getContents();
        if (source == null) {
            return;
        }
        int length = source.length();
        int offset = 0;
        while (true) {
            offset = indexOf(source, '\n', offset);
            if (offset == -1 || offset == length - 1) {
                break;
            }
            offset++;
            char peek = source.charAt(offset);
            if (peek == '<' || peek == '=' || peek == '>') {
                if (startsWith(source, "<<<<<<< ", offset)
                        || startsWith(source, "=======\n", offset)
                        || startsWith(source, ">>>>>>> ", offset)) {
                    Location location = Location.create(context.file, source, offset, offset + 7);
                    context.report(ISSUE, location, "Missing merge marker?");
                }
            }
        }
    }
}
