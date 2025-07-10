/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.SdkConstants.CLASS_PARCELABLE;

import static org.jetbrains.kotlin.lexer.KtTokens.SEALED_KEYWORD;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;

import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.uast.UAnonymousClass;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UastContextKt;

import java.util.Collections;
import java.util.List;

/** Looks for Parcelable classes that are missing a CREATOR field */
public class ParcelDetector extends Detector implements SourceCodeScanner {

    private static final String OLD_PARCELIZE = "kotlinx.android.parcel.Parcelize";
    private static final String NEW_PARCELIZE = "kotlinx.parcelize.Parcelize";
    private static final String JVM_FIELD = "kotlin.jvm.JvmField";

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                            "ParcelCreator",
                            "Missing Parcelable `CREATOR` field",
                            "According to the `Parcelable` interface documentation, \"Classes"
                                + " implementing the Parcelable interface must also have a static"
                                + " field called `CREATOR`, which is an object implementing the"
                                + " `Parcelable.Creator` interface.\"",
                            Category.CORRECTNESS,
                            3,
                            Severity.ERROR,
                            new Implementation(ParcelDetector.class, Scope.JAVA_FILE_SCOPE))
                    .setAndroidSpecific(true)
                    .addMoreInfo(
                            "https://developer.android.com/reference/android/os/Parcelable.html");

    /** Constructs a new {@link ParcelDetector} check */
    public ParcelDetector() {}

    // ---- implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_PARCELABLE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration instanceof UAnonymousClass) {
            // Anonymous classes aren't parcelable
            return;
        }

        // Only applies to concrete classes
        if (declaration.isInterface()) {
            return;
        }
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        // Parceling spans is handled in TextUtils#CHAR_SEQUENCE_CREATOR
        if (context.getEvaluator()
                .implementsInterface(
                        declaration.getJavaPsi(), "android.text.ParcelableSpan", false)) {
            return;
        }

        boolean isKotlin = Lint.isKotlin(declaration.getLang());
        if (isKotlin) {
            if (hasParcelizeAnnotation(declaration)) {
                // Already using @Parcelize: nothing to suggest (and don't warn about missing
                // CREATOR field below)
                return;
            }
            // After b/177856520, @Parcelize is propagated to direct subclasses of a sealed class
            PsiClass[] superClasses = declaration.getJavaPsi().getSupers();
            for (PsiClass superClass : superClasses) {
                UClass parent = UastContextKt.toUElement(superClass, UClass.class);
                if (parent != null && hasParcelizeAnnotation(parent)) {
                    PsiElement parentSourcePsi = parent.getSourcePsi();
                    if (parentSourcePsi instanceof KtClassOrObject) {
                        KtClassOrObject ktClassOrObject = (KtClassOrObject) parentSourcePsi;
                        if (ktClassOrObject.hasModifier(SEALED_KEYWORD)) {
                            return;
                        }
                    }
                }
            }
        }

        PsiField field = declaration.getJavaPsi().findFieldByName("CREATOR", true);
        if (field == null) {
            Location location = context.getNameLocation(declaration);
            String message =
                    "This class implements `Parcelable` but does not "
                            + "provide a `CREATOR` field";
            context.report(ISSUE, declaration, location, message, null);
        } else if (Lint.isKotlin(field.getLanguage()) && !hasCreatorInnerClass(declaration)) {
            // Make sure fields in Kotlin are marked @JvmField
            if (!hasJvmFieldAnnotation(field)) {
                Location location = context.getNameLocation(field);
                LintFix fix =
                        fix().name("Annotate with @JvmField", true)
                                .annotate(JVM_FIELD, context, field, true)
                                .autoFix()
                                .build();
                context.report(
                        ISSUE, field, location, "Field should be annotated with `@JvmField`", fix);
            }
        }
    }

    private static boolean hasCreatorInnerClass(@NonNull UClass declaration) {
        // Might be using a companion object; we can't see that in UAST
        for (UClass inner : declaration.getInnerClasses()) {
            String name = inner.getName();
            if ("CREATOR".equals(name)) {
                // Yes, there is an inner class also called CREATOR;
                // it's likely our CREATOR field was generated from
                // a companion object
                return true;
            }
        }
        return false;
    }

    private static boolean hasParcelizeAnnotation(PsiModifierListOwner owner) {
        return owner.hasAnnotation(OLD_PARCELIZE) || owner.hasAnnotation(NEW_PARCELIZE);
    }

    private static boolean hasJvmFieldAnnotation(PsiModifierListOwner owner) {
        return owner.hasAnnotation(JVM_FIELD);
    }
}
