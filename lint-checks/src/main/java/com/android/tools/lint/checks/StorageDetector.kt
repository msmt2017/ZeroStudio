/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Checks related to using the StorageManager APIs correctly. */
class StorageDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> {
    return listOf("getUsableSpace")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "java.io.File")) {
      return
    }

    // See if we're already referencing getAllocatableBytes in the same compilation unit
    var found = false
    context.uastFile?.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          if (node.methodName == "getAllocatableBytes") {
            found = true
          }
          return super.visitCallExpression(node)
        }
      }
    )
    if (!found) {
      val location = context.getCallLocation(node, false, false)
      val message =
        "Consider also using `StorageManager#getAllocatableBytes` and " +
          "`allocateBytes` which will consider clearable cached data"
      context.report(ISSUE, node, location, message)
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "UsableSpace",
        briefDescription = "Using getUsableSpace()",
        explanation =
          """
                When you need to allocate disk space for large files, consider using the new \
                `allocateBytes(FileDescriptor, long)` API, which will automatically clear \
                cached files belonging to other apps (as needed) to meet your request.

                When deciding if the device has enough disk space to hold your new data, \
                call `getAllocatableBytes(UUID)` instead of using `getUsableSpace()`, since \
                the former will consider any cached data that the system is willing to \
                clear on your behalf.

                Note that these methods require API level 26. If your app is running on \
                older devices, you will probably need to use both APIs, conditionally switching \
                on `Build.VERSION.SDK_INT`. Lint only looks in the same compilation unit to \
                see if you are already using both APIs, so if it warns even though you are \
                already using the new API, consider moving the calls to the same file or \
                suppressing the warning.
                """,
        category = Category.PERFORMANCE,
        priority = 3,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(StorageDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }
}
