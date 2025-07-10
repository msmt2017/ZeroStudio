/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_PKG
import com.android.SdkConstants.REQUEST_FOCUS
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_MERGE
import com.android.SdkConstants.VIEW_PKG_PREFIX
import com.android.SdkConstants.WIDGET_PKG_PREFIX
import com.android.ide.common.rendering.api.ResourceNamespace.TODO
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType.LAYOUT
import com.android.tools.lint.checks.RtlDetector.getFolderVersion
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.ResourceRepositoryScope.PROJECT_ONLY
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.io.IOException
import java.util.SortedSet
import kotlin.math.max
import org.jetbrains.uast.UCallExpression
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Checks related to RemoteViews. */
class RemoteViewDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(RemoteViewDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Unsupported views in a remote view. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "RemoteViewLayout",
        briefDescription = "Unsupported View in RemoteView",
        explanation =
          """
            In a `RemoteView`, only some layouts and views are allowed.
            """,
        moreInfo = "https://developer.android.com/reference/android/widget/RemoteViews",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    private const val KEY_LAYOUT = "layout"
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf("android.widget.RemoteViews")
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    val arguments = node.valueArguments
    if (arguments.size != 2) return
    val argument = arguments[1]
    val resource = ResourceReference.get(argument) ?: return
    if (resource.`package` == ANDROID_PKG || resource.type != LAYOUT) {
      return
    }

    val client = context.client
    val globalAnalysis = context.isGlobalAnalysis()
    val resources =
      if (globalAnalysis) client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
      else client.getResources(context.project, PROJECT_ONLY)

    // See if the associated resource references propertyValuesHolder, and if so
    // suggest switching to AnimatorInflaterCompat.loadAnimator.
    val layout = resource.name
    val items = resources.getResources(TODO(), LAYOUT, layout)
    if (items.isNotEmpty()) {
      checkLayouts(context, layout, items, node, null)
    } else if (!globalAnalysis) {
      // didn't find the layout locally; that means it might be sitting in a module
      // dependency. Enqueue this check for the reporting phase.
      if (!context.driver.isSuppressed(context, ISSUE, node)) {
        context.report(createIncident(context, node, ""), map().apply { put(KEY_LAYOUT, layout) })
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val layout = map[KEY_LAYOUT] ?: return false
    val client = context.client
    val resources = client.getResources(context.project, LOCAL_DEPENDENCIES)
    val items = resources.getResources(TODO(), LAYOUT, layout)
    return items.isNotEmpty() && checkLayouts(context, layout, items, null, incident)
  }

  /**
   * Checks the [layouts] that are known to be used with a `RemoteView` to make sure they only
   * reference views safe with remote views. Reports true if a problem is reported. If [node] is not
   * null, the error will be reported on that AST call expression, otherwise it will be applied to
   * the [incident] (used for partial analysis).
   */
  private fun checkLayouts(
    context: Context,
    layoutName: String,
    layouts: List<ResourceItem>,
    node: UCallExpression?,
    incident: Incident?,
  ): Boolean {
    var tags: MutableSet<String>? = null
    val paths = layouts.asSequence().mapNotNull { it.source }.toSet()
    for (path in paths) {
      val min = max(context.project.minSdk, getFolderVersion(path.rawPath))
      try {
        val parser = context.client.createXmlPullParser(path) ?: continue
        while (true) {
          val event = parser.next()
          if (event == XmlPullParser.START_TAG) {
            val tag = parser.name ?: continue
            if (!isSupportedTag(tag, min)) {
              (tags ?: HashSet<String>().also { tags = it }).add(tag)
            }
          } else if (event == XmlPullParser.END_DOCUMENT) {
            break
          }
        }
      } catch (ignore: XmlPullParserException) {
        // Users might be editing these files in the IDE; don't flag
      } catch (ignore: IOException) {
        // Users might be editing these files in the IDE; don't flag
      }
    }
    val sorted = tags?.toSortedSet()
    if (sorted != null) {
      if (incident != null) {
        incident.message = createErrorMessage(layoutName, sorted)
      } else if (node != null) {
        val message = createErrorMessage(layoutName, sorted)
        context.report(createIncident(context, node, message))
      }
      return true
    }
    return false
  }

  private fun createIncident(context: Context, node: UCallExpression, message: String) =
    Incident(ISSUE, node, context.getLocation(node), message)

  private fun createErrorMessage(layoutName: String, sorted: SortedSet<String>) =
    "`@layout/${layoutName}` includes views not allowed in a `RemoteView`: ${sorted.joinToString()}"

  private fun isSupportedTag(tag: String, min: Int): Boolean {
    if (tag.startsWith(VIEW_PKG_PREFIX) || tag.startsWith(WIDGET_PKG_PREFIX)) {
      return isSupportedTag(tag.substringAfterLast('.'), min)
    }
    return when (tag) {
      "AdapterViewFlipper",
      "FrameLayout",
      "GridLayout",
      "GridView",
      "LinearLayout",
      "ListView",
      "RelativeLayout",
      "StackView",
      "ViewFlipper",
      "AnalogClock",
      "Button",
      "Chronometer",
      "ImageButton",
      "ImageView",
      "ProgressBar",
      "TextClock",
      "TextView" -> true
      "CheckBox",
      "Switch",
      "RadioButton",
      "RadioGroup" -> min >= 31

      // These are not listed in the docs for RemoteView, but are annotated with
      // @RemoteView in the source code:
      "AbsoluteLayout",
      "ViewStub" -> true // b/2541651, fixed in 2012

      // meta tags handled by inflater
      VIEW_MERGE,
      REQUEST_FOCUS,
      VIEW_INCLUDE -> true
      else -> false
    }
  }
}
