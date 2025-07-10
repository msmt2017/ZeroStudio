/*
 * Copyright (C) 2016 - 2018 The Android Open Source Project
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

import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_BARRIER
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_FLOW
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GROUP
import com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_GUIDELINE
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.AndroidXConstants.MOTION_LAYOUT
import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.isLayoutMarkerTag
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Check which looks for potential errors in declarations of ConstraintLayout, such as under
 * specifying constraints.
 */
class ConstraintLayoutDetector : LayoutDetector() {
  override fun getApplicableElements(): Collection<String> {
    return setOf(
      CONSTRAINT_LAYOUT.oldName(),
      CONSTRAINT_LAYOUT.newName(),
      MOTION_LAYOUT.oldName(),
      MOTION_LAYOUT.newName(),
    )
  }

  override fun visitElement(context: XmlContext, element: Element) {
    // In MotionLayouts you can specify the constraints elsewhere.
    // Note that MotionLayoutDetector performs additional validation.
    if (element.hasAttributeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)) {
      return
    }

    // Views that are constrained by Flow do not require additional constraint.
    var child = element.firstChild
    // List of views that are Flow-constrained.
    val flowList = ArrayList<String>()
    while (child != null) {
      if (child.nodeType != Node.ELEMENT_NODE) {
        child = child.nextSibling
        continue
      }

      val elementTagName = (child as Element).tagName
      if (CLASS_CONSTRAINT_LAYOUT_FLOW.isEquals(elementTagName)) {
        val attributes = child.attributes
        for (i in 0 until attributes.length) {
          val attribute = attributes.item(i)
          val name = attribute.localName ?: continue
          val value = attribute.nodeValue
          if (name.contains("constraint_referenced_ids")) {
            flowList.addAll(value.split(","))
          }
        }
      }
      child = child.nextSibling
    }

    // Ensure that all the children have been constrained horizontally and vertically
    child = element.firstChild
    while (child != null) {
      if (child.nodeType != Node.ELEMENT_NODE) {
        child = child.nextSibling
        continue
      }
      val layout = child as Element
      val elementTagName = layout.tagName
      if (
        CLASS_CONSTRAINT_LAYOUT_GUIDELINE.isEquals(elementTagName) ||
          // Groups do not need to be constrained
          CLASS_CONSTRAINT_LAYOUT_GROUP.isEquals(elementTagName) ||
          // Don't flag includes; they might have the right constraints inside.
          TAG_INCLUDE == elementTagName ||
          // <requestFocus/>, <tag/>, etc should not have constraint attributes
          isLayoutMarkerTag(elementTagName)
      ) {
        child = child.getNextSibling()
        continue
      } else if (
        elementTagName.isNotBlank() &&
          CLASS_CONSTRAINT_LAYOUT_BARRIER.isEquals(elementTagName) &&
          scanForBarrierConstraint(layout)
      ) {
        // The Barrier has the necessary layout constraints.
        // This element is constrained correctly.
        break
      }
      var isConstrainedHorizontally = false
      var isConstrainedVertically = false
      val attributes = layout.attributes
      for (i in 0 until attributes.length) {
        val attribute = attributes.item(i)
        val name = attribute.localName ?: continue

        // If the id is in the Flow, it's already constrained.
        if (ATTR_ID == name) {
          val value = attribute.nodeValue.split("/").last()
          if (flowList.contains(value)) {
            isConstrainedHorizontally = true
            isConstrainedVertically = true
            break
          }
        }
        if (!name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX) || name.endsWith("_creator")) {
          continue
        }
        if (
          ATTR_LAYOUT_WIDTH == name && VALUE_MATCH_PARENT == attribute.nodeValue ||
            name.endsWith("toLeftOf") ||
            name.endsWith("toRightOf") ||
            name.endsWith("toStartOf") ||
            name.endsWith("toEndOf") ||
            name.endsWith("toCenterX")
        ) {
          isConstrainedHorizontally = true
          if (isConstrainedVertically) {
            break
          }
        } else if (
          ATTR_LAYOUT_HEIGHT == name && VALUE_MATCH_PARENT == attribute.nodeValue ||
            name.endsWith("toTopOf") ||
            name.endsWith("toBottomOf") ||
            name.endsWith("toCenterY") ||
            name.endsWith("toBaselineOf")
        ) {
          isConstrainedVertically = true
          if (isConstrainedHorizontally) {
            break
          }
        }
      }
      if (!isConstrainedHorizontally || !isConstrainedVertically) {
        // Don't complain if the element doesn't specify absolute x/y - that's
        // when it gets confusing
        val message: String =
          when {
            isConstrainedVertically ->
              "This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint"
            isConstrainedHorizontally ->
              "This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint"
            else ->
              "This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints"
          }
        context.report(ISSUE, layout, context.getNameLocation(layout), message)
      }
      child = child.getNextSibling()
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "MissingConstraints",
        briefDescription = "Missing Constraints in ConstraintLayout",
        explanation =
          """
                    The layout editor allows you to place widgets anywhere on the canvas, \
                    and it records the current position with designtime attributes (such as \
                    `layout_editor_absoluteX`). These attributes are **not** applied at \
                    runtime, so if you push your layout on a device, the widgets may appear \
                    in a different location than shown in the editor. To fix this, make sure \
                    a widget has both horizontal and vertical constraints by dragging from \
                    the edge connections.
                    """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation =
          Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
        androidSpecific = true,
      )

    /**
     * @param element to scan
     * @return true if barrier specific constraint is set. False otherwise.
     */
    private fun scanForBarrierConstraint(element: Element): Boolean {
      val attributes = element.attributes
      for (i in 0 until attributes.length) {
        val attribute = attributes.item(i)
        val name = attribute.localName ?: continue
        if (name.endsWith("barrierDirection")) {
          return true
        }
      }
      return false
    }
  }
}
