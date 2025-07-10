/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/** Finds methods which look like constructors but aren't */
class WrongConstructorDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
          id = "NotConstructor",
          briefDescription = "Not a Constructor",
          explanation =
            """
                This check catches methods that look like they were intended to be constructors, \
                but aren't.
                """,
          category = Category.CORRECTNESS,
          priority = 4,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
        )
        .setAliases(listOf("MethodNameSameAsClassName")) // IntelliJ inspection
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        if (node.isConstructor || node.sourcePsi is KtConstructor<*>) {
          // KtConstructor<*> check is workaround for b/206982645
          return
        }
        // Synthetic constructor for Java record
        if (node.returnType?.canonicalText == "record") {
          return
        }
        val methodName = node.name
        if (!methodName[0].isUpperCase() || context.evaluator.isStatic(node.javaPsi)) {
          return
        }
        val containingClass = node.uastParent as? UClass ?: return // direct parent classes only
        val sourcePsi = containingClass.sourcePsi ?: return // skip package level functions
        if (sourcePsi is KtObjectDeclaration) {
          return
        }

        @Suppress("UElementAsPsi") // UClass should get a name property
        val className = containingClass.name ?: return
        if (className == node.name) {
          context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Method ${node.name} looks like a constructor but is a normal method",
          )
        }
      }
    }
  }
}
