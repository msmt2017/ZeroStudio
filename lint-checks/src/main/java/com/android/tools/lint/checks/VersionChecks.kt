/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Project
import org.jetbrains.uast.UElement

/**
 * Deprecated; here to help with forwarding to new locations in the API package for temporary
 * backwards compatibility. Deprecated in 7.3, planned removal in 7.4 or 7.5.
 */
class VersionChecks {
  companion object {
    @Deprecated(
      message =
        "Use com.android.tools.lint.detector.api.VersionChecks.isWithinVersionCheckConditional instead",
      replaceWith =
        ReplaceWith(
          "com.android.tools.lint.detector.api.VersionChecks.isWithinVersionCheckConditional(context, element, api, lowerBound)"
        ),
    )
    @JvmStatic
    @JvmOverloads
    fun isWithinVersionCheckConditional(
      context: JavaContext,
      element: UElement,
      api: Int,
      lowerBound: Boolean = true,
    ): Boolean {
      return com.android.tools.lint.detector.api.VersionChecks.isWithinVersionCheckConditional(
        context,
        element,
        api,
        lowerBound,
      )
    }

    @Deprecated(
      message =
        "Use com.android.tools.lint.detector.api.VersionChecks.isWithinVersionCheckConditional instead",
      replaceWith =
        ReplaceWith(
          "com.android.tools.lint.detector.api.VersionChecks.isWithinVersionCheckConditional(client, evaluator, element, api, lowerBound)"
        ),
    )
    @JvmStatic
    @JvmOverloads
    fun isWithinVersionCheckConditional(
      client: LintClient,
      evaluator: JavaEvaluator,
      element: UElement,
      api: Int,
      lowerBound: Boolean = true,
    ): Boolean {
      return com.android.tools.lint.detector.api.VersionChecks.isWithinVersionCheckConditional(
        client,
        evaluator,
        element,
        api,
        lowerBound,
      )
    }

    @Deprecated(
      message =
        "Use com.android.tools.lint.detector.api.VersionChecks.isPrecededByVersionCheckExit instead",
      replaceWith =
        ReplaceWith(
          "com.android.tools.lint.detector.api.VersionChecks.isPrecededByVersionCheckExit(context, element, api)"
        ),
    )
    @JvmStatic
    fun isPrecededByVersionCheckExit(context: JavaContext, element: UElement, api: Int): Boolean {
      return com.android.tools.lint.detector.api.VersionChecks.isPrecededByVersionCheckExit(
        context,
        element,
        api,
      )
    }

    @Deprecated(
      message =
        "Use com.android.tools.lint.detector.api.VersionChecks.isPrecededByVersionCheckExit instead",
      replaceWith =
        ReplaceWith(
          "com.android.tools.lint.detector.api.VersionChecks.isPrecededByVersionCheckExit(client, evaluator, element, api, project)"
        ),
    )
    @JvmStatic
    fun isPrecededByVersionCheckExit(
      client: LintClient,
      evaluator: JavaEvaluator,
      element: UElement,
      api: Int,
      project: Project? = null,
    ): Boolean {
      return com.android.tools.lint.detector.api.VersionChecks.isPrecededByVersionCheckExit(
        client,
        evaluator,
        element,
        api,
        project,
      )
    }
  }
}
