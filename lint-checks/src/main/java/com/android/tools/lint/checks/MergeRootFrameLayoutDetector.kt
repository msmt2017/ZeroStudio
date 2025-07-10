/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_FOREGROUND
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_LAYOUT_GRAVITY
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_PADDING
import com.android.SdkConstants.ATTR_PADDING_BOTTOM
import com.android.SdkConstants.ATTR_PADDING_LEFT
import com.android.SdkConstants.ATTR_PADDING_RIGHT
import com.android.SdkConstants.ATTR_PADDING_TOP
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.SET_CONTENT_VIEW_METHOD
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.SdkConstants.VALUE_TRUE
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.toAndroidReferenceViaResolve
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getStyleAttributes
import com.android.tools.lint.detector.api.isRootElement
import com.android.utils.Pair
import com.android.utils.SdkUtils
import com.intellij.psi.PsiMethod
import java.util.ArrayList
import java.util.EnumSet
import java.util.HashSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Checks whether a root FrameLayout can be replaced with a `<merge>` tag. */
class MergeRootFrameLayoutDetector : LayoutDetector(), SourceCodeScanner {
  /**
   * Set of layouts that we want to enable the warning for. We only warn for `<FrameLayout>`'s that
   * are the root of a layout included from another layout, or directly referenced via a
   * `setContentView` call.
   */
  private var allowedLayouts: MutableSet<String>? = null

  /**
   * Set of pending [layout, location] pairs where the given layout is a FrameLayout that perhaps
   * should be replaced by a `<merge>` tag (if the layout is included or set as the content view.
   * This must be processed after the whole project has been scanned since the set of includes etc
   * can be encountered after the included layout.
   */
  private var pending: MutableList<Pair<String, Location.Handle>>? = null

  override fun afterCheckRootProject(context: Context) {
    val pending = pending ?: return
    val allowedLayouts = allowedLayouts ?: return

    for (pair in pending) {
      val layout = pair.first
      if (allowedLayouts.contains(layout)) {
        val handle = pair.second
        val clientData = handle.clientData
        if (clientData is Node) {
          if (context.driver.isSuppressed(null, ISSUE, clientData as Node?)) {
            continue
          }
        }
        val location = handle.resolve()
        context.report(ISSUE, location, "This `<FrameLayout>` can be replaced with a `<merge>` tag")
      }
    }
  }

  // Implements XmlScanner

  override fun getApplicableElements(): Collection<String> {
    return listOf(VIEW_INCLUDE, FRAME_LAYOUT)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val tag = element.tagName
    if (tag == VIEW_INCLUDE) {
      var layout = element.getAttribute(ATTR_LAYOUT) // NOTE: Not in android: namespace
      if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) { // Layouts but not those in @android:layout/
        layout = layout.substring(LAYOUT_RESOURCE_PREFIX.length)
        allowLayout(layout)
      }
    } else {
      assert(tag == FRAME_LAYOUT)
      if (
        isRootElement(element) &&
          (isWidthFillParent(element) && isHeightFillParent(element) ||
            !element.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_GRAVITY)) &&
          !element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND) &&
          !element.hasAttributeNS(ANDROID_URI, ATTR_FOREGROUND) &&
          element.getAttributeNS(ANDROID_URI, ATTR_FITS_SYSTEM_WINDOWS) != VALUE_TRUE &&
          !hasPadding(element)
      ) {
        if (!context.project.reportIssues) {
          // If this is a library project not being analyzed, ignore it
          return
        }

        element.getAttributeNode(ATTR_STYLE)?.value?.let { url ->
          // Root frame theme defines it
          val styles =
            getStyleAttributes(
              context.project,
              context.client,
              url,
              ANDROID_URI,
              ATTR_FITS_SYSTEM_WINDOWS,
            )
          if (styles != null && styles.any { it.value == VALUE_TRUE }) {
            return
          }
        }

        val layout = SdkUtils.getLayoutName(context.file)
        val handle = context.createLocationHandle(element)
        handle.clientData = element
        val pending = pending ?: ArrayList<Pair<String, Location.Handle>>().also { pending = it }
        pending.add(Pair.of(layout, handle))
      }
    }
  }

  private fun allowLayout(layout: String) {
    val allowedLayouts = allowedLayouts ?: HashSet<String>().also { allowedLayouts = it }
    allowedLayouts.add(layout)
  }

  // implements SourceCodeScanner

  override fun getApplicableMethodNames(): List<String> {
    return listOf(SET_CONTENT_VIEW_METHOD)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val expressions = node.valueArguments
    if (expressions.size == 1) {
      val reference = toAndroidReferenceViaResolve(expressions[0])
      if (reference != null && reference.type == ResourceType.LAYOUT) {
        allowLayout(reference.name)
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      create(
        id = "MergeRootFrame",
        briefDescription = "FrameLayout can be replaced with `<merge>` tag",
        explanation =
          """
                If a `<FrameLayout>` is the root of a layout and does not provide background or padding \
                etc, it can often be replaced with a `<merge>` tag which is slightly more efficient. \
                Note that this depends on context, so make sure you understand how the `<merge>` tag \
                works before proceeding.
                """,
        category = Category.PERFORMANCE,
        priority = 4,
        severity = Severity.WARNING,
        moreInfo =
          "https://android-developers.googleblog.com/2009/03/android-layout-tricks-3-optimize-by.html",
        implementation =
          Implementation(
            MergeRootFrameLayoutDetector::class.java,
            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
          ),
      )

    private const val ATTR_FITS_SYSTEM_WINDOWS = "fitsSystemWindows"

    private fun isFillParent(element: Element, dimension: String): Boolean {
      val width = element.getAttributeNS(ANDROID_URI, dimension)
      return width == VALUE_MATCH_PARENT || width == VALUE_FILL_PARENT
    }

    private fun isWidthFillParent(element: Element): Boolean {
      return isFillParent(element, ATTR_LAYOUT_WIDTH)
    }

    private fun isHeightFillParent(element: Element): Boolean {
      return isFillParent(element, ATTR_LAYOUT_HEIGHT)
    }

    private fun hasPadding(root: Element): Boolean {
      return root.hasAttributeNS(ANDROID_URI, ATTR_PADDING) ||
        root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_LEFT) ||
        root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_RIGHT) ||
        root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_TOP) ||
        root.hasAttributeNS(ANDROID_URI, ATTR_PADDING_BOTTOM)
    }
  }
}
