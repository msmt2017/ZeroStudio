/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.SdkConstants.ANDROIDX_MATERIAL_PKG
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement

class WearMaterialThemeDetector : WearDetector(), SourceCodeScanner {

  companion object {
    val IMPLEMENTATION =
      Implementation(WearMaterialThemeDetector::class.java, Scope.JAVA_FILE_SCOPE)

    val ISSUE =
      Issue.create(
        id = "WearMaterialTheme",
        briefDescription = "Using not non-Wear `MaterialTheme` in a Wear OS project",
        explanation =
          """
        Wear projects should use `androidx.wear.compose.material.MaterialTheme` instead of `androidx.compose.material.MaterialTheme`
      """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement?>> {
    if (!isWearProject) return emptyList()
    return listOf<Class<out UElement?>>(UImportStatement::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return ImportVisitor(context)
  }

  private class ImportVisitor(private val context: JavaContext) : UElementHandler() {
    override fun visitImportStatement(statement: UImportStatement) {
      val importedClass = statement.importReference?.sourcePsi?.text ?: return
      if (importedClass == ANDROIDX_MATERIAL_PKG + "MaterialTheme") {

        val location = context.getLocation(statement)
        context.report(
          ISSUE,
          statement,
          location,
          "Don't use `androidx.compose.material.MaterialTheme` in a Wear OS project; use `androidx.wear.compose.material.MaterialTheme` instead",
        )
      }
    }
  }
}
