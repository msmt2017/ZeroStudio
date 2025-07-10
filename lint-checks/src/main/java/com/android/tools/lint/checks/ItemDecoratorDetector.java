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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import com.intellij.psi.PsiClass;

import org.jetbrains.uast.UClass;

import java.util.Collections;
import java.util.List;

/** Looks for copy/paste versions of the divider item decorator. */
public class ItemDecoratorDetector extends Detector implements SourceCodeScanner {

    /** Copy/pasted item decorator code */
    public static final Issue ISSUE =
            Issue.create(
                            "DuplicateDivider",
                            "Unnecessary Divider Copy",
                            "Older versions of the RecyclerView library did not include a divider"
                                + " decorator, but one was provided as a sample in the support"
                                + " demos. This divider class has been widely copy/pasted into"
                                + " various projects.\n"
                                + "\n"
                                + "In recent versions of the support library, the divider decorator"
                                + " is now included, so you can replace custom copies with the"
                                + " \"built-in\" version,"
                                + " `android.support.v7.widget.DividerItemDecoration`.",
                            Category.PERFORMANCE,
                            4,
                            Severity.WARNING,
                            new Implementation(ItemDecoratorDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    /** Constructs a new {@link ItemDecoratorDetector} */
    public ItemDecoratorDetector() {}

    // ---- Implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList("android.support.v7.widget.RecyclerView.ItemDecoration");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String name = declaration.getName();
        if (name == null || !name.equals("DividerItemDecoration")) {
            return;
        }
        PsiClass psiClass = declaration.getJavaPsi();

        if (psiClass.findFieldByName("HORIZONTAL_LIST", false) == null
                || psiClass.findFieldByName("VERTICAL_LIST", false) == null) {
            return;
        }

        // Don't warn if this is the actual support library being compiled
        String qualifiedName = declaration.getQualifiedName();
        if ("android.support.v7.widget.DividerItemDecoration".equals(qualifiedName)) {
            return;
        }

        Location location = context.getNameLocation(declaration);
        context.report(
                ISSUE,
                declaration,
                location,
                "Replace with `android.support.v7.widget.DividerItemDecoration`?");
    }
}
