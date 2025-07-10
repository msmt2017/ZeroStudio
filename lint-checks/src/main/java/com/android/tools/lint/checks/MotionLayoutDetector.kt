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

import com.android.AndroidXConstants.MOTION_LAYOUT
import com.android.SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.MotionSceneTags.MOTION_SCENE
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/** Various checks for Motion Layout files. */
class MotionLayoutDetector : ResourceXmlDetector() {

  private var referencesRecorded = false
  private var resourceModel: ResourceUsageModel? = null
  private var references: MutableMap<Resource, Location>? = null

  override fun appliesTo(folderType: ResourceFolderType) =
    folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML

  override fun getApplicableElements() =
    listOf(MOTION_LAYOUT.oldName(), MOTION_LAYOUT.newName(), MOTION_SCENE)

  override fun afterCheckRootProject(context: Context) {
    if (!referencesRecorded) {
      return
    }
    val isIncremental = isIncrementalMode(context)
    references?.forEach { (reference, location) ->
      val resource = resourceModel?.getResource(reference.type, reference.name)
      if (!isIncremental && (resource == null || !resource.isDeclared)) {
        // Can only read all MotionScene files when analyzing the entire project.
        context.report(
          INVALID_SCENE_FILE_REFERENCE,
          location,
          "The motion scene file: ${reference.url} doesn't exist",
          fix().name("Create ${reference.url}").data(KEY_URL, reference.url),
        )
      }
    }
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (element.tagName) {
      MOTION_SCENE -> visitMotionScene(context, element)
      else -> visitMotionLayout(context, element)
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun visitMotionScene(context: XmlContext, element: Element) {
    val resourceFolderType = context.resourceFolderType ?: return
    val resourceType = ResourceType.fromFolderName(resourceFolderType.getName()) ?: return
    val name = context.file.nameWithoutExtension
    val model = resourceModel ?: ResourceUsageModel().also { resourceModel = it }
    model.addDeclaredResource(resourceType, name, null, true)
  }

  private fun visitMotionLayout(context: XmlContext, element: Element) {
    val description = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
    if (description == null) {
      val sceneUrl = motionSceneUrlFromMotionLayoutFileName(context)
      context.report(
        INVALID_SCENE_FILE_REFERENCE,
        element,
        context.getNameLocation(element),
        "The attribute: `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is missing",
        fix().name("Create $sceneUrl and set attribute").data(KEY_URL, sceneUrl),
      )
    } else {
      val model = resourceModel ?: ResourceUsageModel().also { resourceModel = it }
      val resource = model.getResourceFromUrl(description.value)
      if (resource != null && resource.type == ResourceType.XML) {
        val references = references ?: mutableMapOf<Resource, Location>().also { references = it }
        references[resource] = context.getValueLocation(description)
        referencesRecorded = true
      } else {
        val sceneUrl = motionSceneUrlFromMotionLayoutFileName(context)
        context.report(
          INVALID_SCENE_FILE_REFERENCE,
          element,
          context.getValueLocation(description),
          "`${description.value}` is an invalid value for $ATTR_CONSTRAINT_LAYOUT_DESCRIPTION",
          fix().name("Create $sceneUrl and set attribute").data(KEY_URL, sceneUrl),
        )
      }
    }
  }

  private fun isIncrementalMode(context: Context): Boolean =
    !context.scope.contains(Scope.ALL_RESOURCE_FILES)

  private fun motionSceneUrlFromMotionLayoutFileName(context: XmlContext): String =
    "@xml/${context.file.nameWithoutExtension}_scene"

  companion object {
    const val KEY_URL = "url"

    private val IMPLEMENTATION =
      Implementation(MotionLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    @JvmField
    val INVALID_SCENE_FILE_REFERENCE =
      Issue.create(
        id = "MotionLayoutInvalidSceneFileReference",
        briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
        explanation =
          """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion \
                scene file.
                """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )
  }
}
