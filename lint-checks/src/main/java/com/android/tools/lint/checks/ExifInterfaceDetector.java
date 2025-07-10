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
import com.android.tools.lint.client.api.JavaEvaluator;
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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UImportStatement;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UVariable;

import java.util.ArrayList;
import java.util.List;

/** Checks for errors related to the Exif Interface */
public class ExifInterfaceDetector extends Detector implements SourceCodeScanner {

    public static final String EXIF_INTERFACE = "ExifInterface";
    public static final String OLD_EXIF_INTERFACE = "android.media.ExifInterface";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ExifInterfaceDetector.class, Scope.JAVA_FILE_SCOPE);

    /** Using android.media.ExifInterface */
    public static final Issue ISSUE =
            Issue.create(
                            "ExifInterface",
                            "Using `android.media.ExifInterface`",
                            "The `android.media.ExifInterface` implementation has some known"
                                + " security bugs in older versions of Android. There is a new"
                                + " implementation available of this library in the support"
                                + " library, which is preferable.",
                            Category.CORRECTNESS,
                            6,
                            Severity.WARNING,
                            IMPLEMENTATION)
                    .setAndroidSpecific(true);

    /** Constructs a new {@link ExifInterfaceDetector} */
    public ExifInterfaceDetector() {}

    // ---- implements SourceCodeScanner ----

    private static void fix(
            @NonNull JavaContext context,
            @NonNull UElement reference,
            @NonNull PsiElement referenced) {
        // Can't just check for PsiMember, because we explicitly don't want to include PsiClass
        // (which is also a PsiMember)
        if (referenced instanceof PsiMethod || referenced instanceof PsiField) {
            referenced = ((PsiMember) referenced).getContainingClass();
        }
        if (referenced instanceof PsiClass) {
            String qualifiedName = ((PsiClass) referenced).getQualifiedName();
            if (qualifiedName != null && OLD_EXIF_INTERFACE.equals(qualifiedName)) {
                replace(context, reference);
            }
        }
    }

    private static void replace(@NonNull JavaContext context, @NonNull UElement reference) {
        UElement locationNode = reference;
        //noinspection ConstantConditions
        while (locationNode.getUastParent() instanceof UReferenceExpression) {
            locationNode = locationNode.getUastParent();
        }

        Location location = context.getLocation(locationNode);
        String message = getErrorMessage(context.getEvaluator());
        context.report(ISSUE, reference, location, message);
    }

    private static void replace(@NonNull JavaContext context, @NonNull PsiElement reference) {
        Location location = context.getLocation(reference);
        String message = getErrorMessage(context.getEvaluator());
        context.report(ISSUE, reference, location, message);
    }

    @NonNull
    private static String getErrorMessage(JavaEvaluator evaluator) {
        PsiClass supportClass = evaluator.findClass("android.support.media.ExifInterface");
        return "Avoid using `android.media.ExifInterface`; use `"
                + (supportClass != null
                        ? "android.support.media.ExifInterface"
                        : "androidx.exifinterface.media.ExifInterface")
                + "` instead";
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        List<Class<? extends UElement>> types = new ArrayList<>(3);
        types.add(UQualifiedReferenceExpression.class);
        types.add(UImportStatement.class);
        types.add(UVariable.class);
        return types;
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return new MyVisitor(context);
    }

    private static final class MyVisitor extends UElementHandler {

        private final JavaContext context;

        private MyVisitor(JavaContext context) {
            this.context = context;
        }

        @Override
        public void visitQualifiedReferenceExpression(@NonNull UQualifiedReferenceExpression node) {
            UElement selector = node.getSelector();
            if (selector instanceof USimpleNameReferenceExpression) {
                USimpleNameReferenceExpression name = (USimpleNameReferenceExpression) selector;
                if (EXIF_INTERFACE.equals(name.getIdentifier())) {
                    PsiElement resolved = node.resolve();
                    if (resolved != null) {
                        fix(context, node, resolved);
                    }
                }
            }
        }

        @Override
        public void visitImportStatement(@NonNull UImportStatement node) {
            // We don't get UQualifiedReferenceExpressions for import statements so
            // visit these separately
            UElement importReference = node.getImportReference();
            if (importReference != null) {
                PsiElement resolved = node.resolve();
                // Can't just check PsiMember because we don't want to include PsiClass which
                // is also a PsiMember
                if (resolved instanceof PsiField) {
                    resolved = ((PsiField) resolved).getContainingClass();
                } else if (resolved instanceof PsiMethod) {
                    resolved = ((PsiMethod) resolved).getContainingClass();
                }
                if (resolved instanceof PsiClass) {
                    PsiClass cls = (PsiClass) resolved;
                    if (EXIF_INTERFACE.equals(cls.getName())
                            && OLD_EXIF_INTERFACE.equals(cls.getQualifiedName())) {
                        fix(context, importReference, resolved);
                    }
                }
            }
        }

        @Override
        public void visitVariable(@NonNull UVariable node) {
            // We don't get UQualifiedReferenceExpressions for fully qualified type
            // references in variable declarations, so visit these separately

            // PSI workaround: node.getTypeReference just returns null. Operate on PSI
            // type element instead for now since UVariable has a PSI getTypeElement
            // accessor.
            PsiVariable variable = (PsiVariable) node.getJavaPsi();
            PsiTypeElement typeElement = null;
            if (variable != null) {
                typeElement = variable.getTypeElement();
            }
            if (typeElement != null) {
                PsiJavaCodeReferenceElement referenceElement =
                        typeElement.getInnermostComponentReferenceElement();
                if (referenceElement != null
                        && EXIF_INTERFACE.equals(referenceElement.getReferenceName())
                        && OLD_EXIF_INTERFACE.equals(referenceElement.getQualifiedName())
                        && referenceElement.textContains('.')) {
                    replace(context, referenceElement);
                }
            }
        }
    }
}
