/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import java.io.File

class MissingResourcesPropertiesDetector : Detector(), GradleScanner {

  override fun checkDslPropertyAssignment(
    context: GradleContext,
    property: String,
    value: String,
    parent: String,
    parentParent: String?,
    propertyCookie: Any,
    valueCookie: Any,
    statementCookie: Any,
  ) {
    // For automatic locale config generation, the "resources.properties" file must be in the app
    // module, directly under the "res" folder
    if (context.project.isLibrary) return
    if (property == "generateLocaleConfig" && value == "true") {
      if (context.project.resourceFolders.none { File(it, "resources.properties").exists() }) {
        val incident =
          Incident(
            ISSUE,
            propertyCookie,
            context.getLocation(propertyCookie),
            "Missing resources.properties file",
          )
        context.client.report(context, incident)
      }
    }
  }

  companion object {
    val ISSUE =
      Issue.create(
        id = "MissingResourcesProperties",
        briefDescription = "Missing resources.properties file",
        explanation =
          "When `generateLocaleConfig` is turned on, the default locale must be specified in a resources.properties file.",
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.WARNING,
        implementation =
          Implementation(MissingResourcesPropertiesDetector::class.java, Scope.GRADLE_SCOPE),
        moreInfo = "https://developer.android.com/r/studio-ui/build/automatic-per-app-languages",
        androidSpecific = true,
      )
  }
}
