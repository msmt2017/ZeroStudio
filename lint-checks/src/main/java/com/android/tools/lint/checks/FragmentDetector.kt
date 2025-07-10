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

package com.android.tools.lint.checks

import com.android.AndroidXConstants.CLASS_V4_FRAGMENT
import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UClass

/**
 * Checks that Fragment subclasses can be instantiated via Class.newInstance: the class is public,
 * static, and has a public null constructor.
 *
 * This helps track down issues like
 * http://stackoverflow.com/questions/8058809/fragment-activity-crashes-on-screen-rotate (and
 * countless duplicates)
 */
class FragmentDetector : Detector(), SourceCodeScanner {
  companion object {
    /** Are fragment subclasses instantiatable? */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ValidFragment",
        briefDescription = "Fragment not instantiatable",
        explanation =
          """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
                """,
        category = Category.CORRECTNESS,
        androidSpecific = true,
        priority = 6,
        severity = Severity.ERROR,
        moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
        implementation = Implementation(FragmentDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }

  // ---- implements SourceCodeScanner ----

  override fun applicableSuperClasses(): List<String> {
    // Note: We are deliberately NOT including: CLASS_V4_FRAGMENT.newName() here:
    // androidx Fragments are allowed to use non-default constructors (see issue 119675579)
    return listOf(CLASS_FRAGMENT, CLASS_V4_FRAGMENT.oldName())
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (declaration is UAnonymousClass) {
      context.report(
        ISSUE,
        declaration,
        context.getNameLocation(declaration),
        "Fragments should be static such that they can be re-instantiated by the system, and anonymous classes are not static",
      )
      return
    }

    val evaluator = context.evaluator
    if (evaluator.isAbstract(declaration)) {
      return
    }

    if (!evaluator.isPublic(declaration)) {
      context.report(
        ISSUE,
        declaration,
        context.getNameLocation(declaration),
        "This fragment class should be public (${declaration.qualifiedName})",
      )
      return
    }

    if (declaration.javaPsi.containingClass != null && !evaluator.isStatic(declaration)) {
      context.report(
        ISSUE,
        declaration,
        context.getNameLocation(declaration),
        "This fragment inner class should be static (${declaration.qualifiedName})",
      )
      return
    }

    var hasDefaultConstructor = false
    var hasConstructor = false
    for (constructor in declaration.javaPsi.constructors) {
      hasConstructor = true
      if (constructor.parameterList.parametersCount == 0) {
        if (evaluator.isPublic(constructor)) {
          hasDefaultConstructor = true
        } else {
          val location = context.getNameLocation(constructor)
          context.report(ISSUE, constructor, location, "The default constructor must be public")
          return
        }
      } else {
        val location = context.getNameLocation(constructor)
        // TODO: Use separate issue for this which isn't an error
        val message =
          "Avoid non-default constructors in fragments: use a default constructor plus `Fragment#setArguments(Bundle)` instead"
        context.report(ISSUE, constructor, location, message)
      }
    }

    if (!hasDefaultConstructor && hasConstructor) {
      val message =
        "This fragment should provide a default constructor (a public constructor with no arguments) (`${declaration.qualifiedName}`)"
      context.report(ISSUE, declaration, context.getNameLocation(declaration), message)
    }
  }
}
