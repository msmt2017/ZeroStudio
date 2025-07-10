/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.MotionSceneAttributes.ATTR_CUSTOM_ATTRIBUTE_NAME
import com.android.SdkConstants.MotionSceneTags.CONSTRAINT
import com.android.SdkConstants.MotionSceneTags.CONSTRAINT_SET
import com.android.SdkConstants.MotionSceneTags.CUSTOM_ATTRIBUTE
import com.android.SdkConstants.MotionSceneTags.KEY_ATTRIBUTE
import com.android.SdkConstants.MotionSceneTags.KEY_CYCLE
import com.android.SdkConstants.MotionSceneTags.KEY_FRAME_SET
import com.android.SdkConstants.MotionSceneTags.KEY_POSITION
import com.android.SdkConstants.MotionSceneTags.KEY_TIME_CYCLE
import com.android.SdkConstants.MotionSceneTags.KEY_TRIGGER
import com.android.SdkConstants.MotionSceneTags.LAYOUT
import com.android.SdkConstants.MotionSceneTags.MOTION
import com.android.SdkConstants.MotionSceneTags.MOTION_SCENE
import com.android.SdkConstants.MotionSceneTags.ON_CLICK
import com.android.SdkConstants.MotionSceneTags.ON_SWIPE
import com.android.SdkConstants.MotionSceneTags.PROPERTY_SET
import com.android.SdkConstants.MotionSceneTags.STATE
import com.android.SdkConstants.MotionSceneTags.STATE_SET
import com.android.SdkConstants.MotionSceneTags.TRANSFORM
import com.android.SdkConstants.MotionSceneTags.TRANSITION
import com.android.SdkConstants.MotionSceneTags.VARIANT
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils
import com.android.utils.iterator
import org.w3c.dom.Element

/** Various checks for MotionScene files. */
class MotionSceneDetector : ResourceXmlDetector() {

  private val customAttributeNames = mutableSetOf<String>()

  override fun appliesTo(folderType: ResourceFolderType) = folderType == ResourceFolderType.XML

  override fun getApplicableElements() = listOf(MOTION_SCENE)

  override fun visitElement(context: XmlContext, element: Element) {
    for (child in element) {
      when (child.tagName) {
        CONSTRAINT_SET -> visitConstraintSet(context, child)
        TRANSITION -> visitTransition(context, child)
        STATE_SET -> visitStateSet(context, child)
      }
    }
  }

  private fun visitConstraintSet(context: XmlContext, element: Element) {
    for (child in element) {
      when (child.tagName) {
        CONSTRAINT -> visitConstraint(context, child)
      }
    }
  }

  private fun visitConstraint(context: XmlContext, element: Element) {
    customAttributeNames.clear()
    for (child in element) {
      when (child.tagName) {
        LAYOUT -> visitLayout(context, child)
        MOTION -> visitMotion(context, child)
        PROPERTY_SET -> visitPropertySet(context, child)
        TRANSFORM -> visitTransform(context, child)
        CUSTOM_ATTRIBUTE -> visitCustomAttribute(context, child)
      }
    }
    // TODO: Check that the required attribute android:id is present
    // TODO: Check that there are not multiple constraints with the same id
  }

  private fun visitLayout(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
    // TODO: Check that only layout attributes are specified
  }

  private fun visitMotion(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
    // TODO: Check that only motion attributes are specified
  }

  private fun visitPropertySet(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
    // TODO: Check that only property set attributes are specified
  }

  private fun visitTransform(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
    // TODO: Check that only transform attributes are specified
  }

  private fun visitCustomAttribute(context: XmlContext, element: Element) {
    val name = element.getAttributeNS(AUTO_URI, ATTR_CUSTOM_ATTRIBUTE_NAME)
    if (name.isNullOrEmpty()) {
      context.report(
        MOTION_SCENE_FILE_VALIDATION_ERROR,
        element,
        context.getNameLocation(element),
        "`$ATTR_CUSTOM_ATTRIBUTE_NAME` should be defined",
        fix().set().todo(AUTO_URI, ATTR_CUSTOM_ATTRIBUTE_NAME).build(),
      )
    } else if (!customAttributeNames.add(name)) {
      context.report(
        MOTION_SCENE_FILE_VALIDATION_ERROR,
        element,
        context.getNameLocation(element),
        "The custom attribute `$name` was specified multiple times",
        fix()
          .name("Delete this custom attribute")
          .replace()
          .with("")
          .range(context.getLocation(element))
          .build(),
      )
    }
  }

  private fun visitTransition(context: XmlContext, element: Element) {
    checkMultipleOnClicks(context, element)
    for (child in element) {
      when (child.tagName) {
        KEY_FRAME_SET -> visitKeyFrameSet(context, child)
        ON_SWIPE -> visitOnSwipe(context, child)
        ON_CLICK -> visitOnClick(context, child)
      }
    }
  }

  private fun checkMultipleOnClicks(context: XmlContext, element: Element) {
    XmlUtils.getSubTags(element)
      .filter { it.tagName == ON_CLICK }
      .drop(1)
      .forEach { onClickElement ->
        context.report(
          MOTION_SCENE_FILE_VALIDATION_ERROR,
          onClickElement,
          context.getNameLocation(onClickElement),
          "Can only have one `$ON_CLICK` per `$TRANSITION`",
          fix()
            .name("Delete additional $ON_CLICK")
            .replace()
            .with("")
            .range(context.getLocation(onClickElement))
            .build(),
        )
      }
  }

  private fun visitKeyFrameSet(context: XmlContext, element: Element) {
    for (child in element) {
      when (child.tagName) {
        KEY_ATTRIBUTE -> visitKeyAttribute(context, child)
        KEY_POSITION -> visitKeyPosition(context, child)
        KEY_CYCLE -> visitKeyCycle(context, child)
        KEY_TIME_CYCLE -> visitKeyTimeCycle(context, child)
        KEY_TRIGGER -> visitKeyTrigger(context, child)
      }
    }
  }

  private fun visitKeyAttribute(context: XmlContext, element: Element) {
    customAttributeNames.clear()
    for (child in element) {
      when (child.tagName) {
        CUSTOM_ATTRIBUTE -> visitCustomAttribute(context, child)
      }
    }
  }

  private fun visitKeyPosition(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
  }

  private fun visitKeyCycle(context: XmlContext, element: Element) {
    customAttributeNames.clear()
    for (child in element) {
      when (child.tagName) {
        CUSTOM_ATTRIBUTE -> visitCustomAttribute(context, child)
      }
    }
  }

  private fun visitKeyTimeCycle(context: XmlContext, element: Element) {
    customAttributeNames.clear()
    for (child in element) {
      when (child.tagName) {
        CUSTOM_ATTRIBUTE -> visitCustomAttribute(context, child)
      }
    }
  }

  private fun visitKeyTrigger(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
  }

  private fun visitOnSwipe(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
  }

  private fun visitOnClick(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
  }

  private fun visitStateSet(context: XmlContext, element: Element) {
    for (child in element) {
      when (child.tagName) {
        STATE -> visitState(context, child)
      }
    }
  }

  private fun visitState(context: XmlContext, element: Element) {
    for (child in element) {
      when (child.tagName) {
        VARIANT -> visitVariant(context, child)
      }
    }
  }

  private fun visitVariant(context: XmlContext, element: Element) {
    checkNoSubTags(context, element)
  }

  private fun checkNoSubTags(context: XmlContext, element: Element) {
    for (subTag in element) {
      context.report(
        MOTION_SCENE_FILE_VALIDATION_ERROR,
        subTag,
        context.getNameLocation(subTag),
        "`${element.tagName}` can not have any child tags",
        fix()
          .name("Delete ${subTag.tagName}")
          .replace()
          .with("")
          .range(context.getLocation(subTag))
          .build(),
      )
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(MotionSceneDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    @JvmField
    val MOTION_SCENE_FILE_VALIDATION_ERROR =
      Issue.create(
        id = "MotionSceneFileValidationError",
        briefDescription = "Validation errors in `MotionScene` files",
        explanation =
          """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                This check performs various serious correctness checks in a motion scene file.
                """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )
  }
}
