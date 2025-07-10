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

import com.android.annotations.NonNull;
import com.android.tools.lint.client.api.UElementHandler;
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
import com.intellij.psi.PsiElement;
import java.util.Collections;
import java.util.List;
import org.jetbrains.kotlin.psi.KtImportDirective;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;

/**
 * Checks for "import android.R", which seems to be a common source of confusion (see for example
 * http://stackoverflow.com/questions/885009/r-cannot-be-resolved-android-error and many other
 * forums).
 *
 * <p>The root cause is probably this (from http://source.android.com/source/using-eclipse.html) :
 *
 * <blockquote>
 *
 * Note: Eclipse sometimes likes to add an import android.R statement at the top of your files that
 * use resources, especially when you ask eclipse to sort or otherwise manage imports. This will
 * cause your make to break. Look out for these erroneous import statements and delete them.
 *
 * </blockquote>
 */
public class WrongImportDetector extends Detector implements SourceCodeScanner {
    /** Is android.R being imported? */
    public static final Issue ISSUE =
            Issue.create(
                            "SuspiciousImport",
                            "'`import android.R`' statement",
                            "Importing `android.R` is usually not intentional; it sometimes happens when "
                                    + "you use an IDE and ask it to automatically add imports at a time when your "
                                    + "project's R class it not present.\n"
                                    + "\n"
                                    + "Once the import is there you might get a lot of \"confusing\" error messages "
                                    + "because of course the fields available on `android.R` are not the ones you'd "
                                    + "expect from just looking at your own `R` class.",
                            Category.CORRECTNESS,
                            9,
                            Severity.WARNING,
                            new Implementation(WrongImportDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true);

    /** Constructs a new {@link WrongImportDetector} check */
    public WrongImportDetector() {}

    // ---- implements SourceCodeScanner ----

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UImportStatement.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new ImportVisitor(context);
    }

    private static class ImportVisitor extends UElementHandler {
        private final JavaContext context;

        public ImportVisitor(JavaContext context) {
            super();
            this.context = context;
        }

        @Override
        public void visitImportStatement(@NonNull UImportStatement statement) {
            PsiElement resolved = statement.resolve();
            if (resolved instanceof PsiClass) {
                String qualifiedName = ((PsiClass) resolved).getQualifiedName();
                if ("android.R".equals(qualifiedName)) {

                    PsiElement psi = statement.getSourcePsi();
                    if (psi instanceof KtImportDirective) {
                        // Importing android.R as an alias is totally fine
                        if (((KtImportDirective) psi).getAlias() != null) {
                            return;
                        }
                    }

                    Location location = context.getLocation(statement);
                    context.report(
                            ISSUE,
                            statement,
                            location,
                            "Don't include `android.R` here; use a fully qualified name for "
                                    + "each usage instead");
                }
            }
        }
    }
}
