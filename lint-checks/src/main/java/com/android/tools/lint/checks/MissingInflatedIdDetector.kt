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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.checks.ViewTypeDetector.Companion.FIND_VIEW_BY_ID
import com.android.tools.lint.checks.ViewTypeDetector.Companion.REQUIRE_VIEW_BY_ID
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
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.stripIdPrefix
import com.intellij.psi.PsiMethod
import java.io.IOException
import java.util.EnumSet
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Detector for finding layout inflation paired with a find/require view by id looking for an id not
 * in that layout.
 *
 * TODO: Instead of just making sure that the id is found in at least *one* of the overridden
 *   layouts, make sure that it's present in *all* the layouts (and if not, list which ones it's
 *   missing from). If the view is looked up via `requireViewById`, this is an unconditional error.
 *   Otherwise, see whether we're null checking the result.
 */
class MissingInflatedIdDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> =
    listOf(FIND_VIEW_BY_ID, REQUIRE_VIEW_BY_ID)

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val layout = findLayout(context, node)?.name ?: return
    val id = getFirstArgAsResource(node, context)?.name ?: return

    val globalAnalysis = context.isGlobalAnalysis()
    val resources =
      if (globalAnalysis) context.client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
      else context.client.getResources(context.project, PROJECT_ONLY)
    val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT, layout)

    if (items.isNotEmpty()) {
      if (layoutMissingId(context, items, id)) {
        val incident = createIncident(context, node, layout, id)
        context.report(incident)
      }
      return
    }

    // Didn't find the resource
    if (globalAnalysis) {
      return
    }

    // We're in partial analysis, so the resource is most likely defined in a library;
    // this means we have to defer the work.

    // In partial analysis mode, we must handle suppression now, manually.
    if (context.driver.isSuppressed(context, ISSUE, node)) {
      return
    }

    val map =
      map().apply {
        put(KEY_LAYOUT, layout)
        put(KEY_ID, id)
      }
    context.report(createIncident(context, node, layout, id), map)
  }

  /**
   * Checks whether **all** the [layouts] are missing the given [id]. (It's okay for some of the
   * layouts to miss it; e.g. a portrait orientation layout could intentionally be skipping a
   * widget.)
   */
  private fun layoutMissingId(context: Context, layouts: List<ResourceItem>, id: String): Boolean {
    return layouts.isNotEmpty() && layouts.none { definesId(context, it.source, id) }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val layout = map[KEY_LAYOUT] ?: return false
    val id = map[KEY_ID] ?: return false

    val resources = context.client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
    val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.LAYOUT, layout)
    return layoutMissingId(context, items, id)
  }

  private fun createIncident(
    context: JavaContext,
    node: UCallExpression,
    layout: String,
    id: String,
  ): Incident {
    val message = "`@layout/$layout` does not contain a declaration with id `$id`"
    val idArgument = node.valueArguments.first()
    val incident = Incident(ISSUE, idArgument, context.getLocation(idArgument), message)
    return incident
  }

  /**
   * From a `findByViewId` call, try to locate the layout resource it is inflating from. E.g. in an
   * activity, if we simply call `findViewById(id)`, it's probably a preeeding `setContentView`
   * call; if it's something like `root.findViewById`, see if we can find inflation of the root
   * view.
   */
  private fun findLayout(context: JavaContext, call: UCallExpression): ResourceUrl? {
    val receiver = call.receiver?.skipParenthesizedExprDown()
    if (receiver != null) {
      val variable = receiver.tryResolve()?.toUElement() as? ULocalVariable ?: return null
      val inflation = variable.uastInitializer?.findSelector() as? UCallExpression ?: return null
      if (inflation.methodName != "inflate") return null
      return getFirstArgAsResource(inflation, context)
    } else {
      // See if there's some local reference to setting the content view here.
      val block = call.getParentOfType<UBlockExpression>(true) ?: return null
      for (expression in block.expressions) {
        val setContentView = expression.skipParenthesizedExprDown() as? UCallExpression ?: continue
        if (setContentView.methodIdentifier?.name != "setContentView") continue
        return getFirstArgAsResource(setContentView, context)
      }
      return null
    }
  }

  /**
   * For a call like `inflate(R.layout.foo, null)` or `setContentView(R.layout.foo)`, returns
   * `@layout/foo`. Deliberately ignores resources like `android.R.id.some_id` since we don't want
   * to initialize the resource repository for all the framework resources.
   */
  private fun getFirstArgAsResource(
    setContentView: UCallExpression,
    context: JavaContext,
  ): ResourceUrl? {
    val resourceArgument =
      setContentView.valueArguments.firstOrNull()?.skipParenthesizedExprDown() ?: return null
    val url = ResourceEvaluator.getResource(context.evaluator, resourceArgument) ?: return null
    return if (!url.isFramework) url else null
  }

  /**
   * Returns true if the given layout [file] contains a definition of the given [targetId], **and**
   * does not contain an `<include>` tag.
   */
  private fun definesId(context: Context, file: PathString?, targetId: String): Boolean {
    file ?: return true
    val parser =
      try {
        context.client.createXmlPullParser(file) ?: return true
      } catch (ignore: IOException) {
        return true
      }
    try {
      while (true) {
        val event = parser.next()
        if (event == XmlPullParser.START_TAG) {
          if (parser.name == VIEW_INCLUDE) {
            // this layout contains an <include> tag; in that case we're not certain
            // so just assume the id exists
            return true
          }
          val id: String? = parser.getAttributeValue(ANDROID_URI, ATTR_ID)
          @Suppress("DEPRECATION")
          if (id != null && id.endsWith(targetId) && stripIdPrefix(id) == targetId) {
            return true
          }
        } else if (event == XmlPullParser.END_DOCUMENT) {
          return false
        }
      }
    } catch (ignore: XmlPullParserException) {
      // Users might be editing these files in the IDE; don't flag
      return true
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "MissingInflatedId",
        briefDescription = "ID not found in inflated resource",
        explanation =
          """
          Checks calls to layout inflation and makes sure that the referenced ids \
          are found in the corresponding layout (or at least one of them, if the \
          layout has multiple configurations.)
          """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation =
          Implementation(
            MissingInflatedIdDetector::class.java,
            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES),
            Scope.JAVA_FILE_SCOPE,
          ),
      )

    private const val KEY_LAYOUT = "layout"
    private const val KEY_ID = "id"
  }
}
