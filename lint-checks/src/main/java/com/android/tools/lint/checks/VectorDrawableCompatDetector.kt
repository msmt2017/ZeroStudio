/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_SRC_COMPAT
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.android.SdkConstants.TAG_ANIMATED_VECTOR
import com.android.SdkConstants.TAG_VECTOR
import com.android.ide.common.resources.fileNameToResourceName
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.minSdkLessThan
import java.io.File
import org.w3c.dom.Attr
import org.w3c.dom.Element

/**
 * Finds all the vector drawables and checks references to them in layouts.
 *
 * This detector looks for common mistakes related to AppCompat support for vector drawables, that
 * is:
 * * Using app:srcCompat without useSupportLibrary in build.gradle
 * * Using android:src with useSupportLibrary in build.gradle
 */
class VectorDrawableCompatDetector : ResourceXmlDetector() {
  companion object {
    /** The main issue discovered by this detector */
    @JvmField
    val ISSUE =
      create(
          id = "VectorDrawableCompat",
          briefDescription = "Using VectorDrawableCompat",
          explanation =
            """
                To use VectorDrawableCompat, you need to make two modifications to your project. \
                First, set `android.defaultConfig.vectorDrawables.useSupportLibrary = true` in your \
                `build.gradle` file, and second, use `app:srcCompat` instead of `android:src` to \
                refer to vector drawables.""",
          category = Category.CORRECTNESS,
          priority = 5,
          severity = Severity.ERROR,
          implementation =
            Implementation(
              VectorDrawableCompatDetector::class.java,
              Scope.ALL_RESOURCES_SCOPE,
              Scope.RESOURCE_FILE_SCOPE,
            ),
        )
        .addMoreInfo(
          "https://developer.android.com/guide/topics/graphics/vector-drawable-resources"
        )
        .addMoreInfo(
          "https://medium.com/androiddevelopers/using-vector-assets-in-android-apps-4318fd662eb9"
        )
  }

  /** Whether to skip the checks altogether. */
  private var skipChecks = false

  override fun beforeCheckRootProject(context: Context) {
    skipChecks = context.project.minSdk >= 21

    // TODO(b/249387643): Enable IDE analysis once partial result maps are supported in the IDE.
    if (LintClient.isStudio) {
      skipChecks = true
    }
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return !skipChecks && (folderType == DRAWABLE || folderType == LAYOUT)
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_VECTOR, TAG_ANIMATED_VECTOR)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    // Found a vector or animated vector resource file

    if (context.project.minSdk >= 21 || context.resourceFolderType != DRAWABLE) {
      return
    }
    val usingLibraryVectors =
      context.project.buildVariant?.useSupportLibraryVectorDrawables ?: return

    val name = fileNameToResourceName(context.file.name)

    // We store an explicit true or false in the map instead of
    // just storing true because we're communicating a third thing:
    // presence in the map implies it's a vector icon
    context.getPartialResults(ISSUE).map().put(name, usingLibraryVectors)
  }

  override fun getApplicableAttributes(): Collection<String>? {
    return if (skipChecks) null else listOf(ATTR_SRC, ATTR_SRC_COMPAT)
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (context.resourceFolderType != LAYOUT) {
      return
    }
    val name = attribute.localName
    val namespace = attribute.namespaceURI
    if (
      ATTR_SRC == name && ANDROID_URI != namespace ||
        ATTR_SRC_COMPAT == name && AUTO_URI != namespace
    ) {
      // Not the attribute we are looking for.
      return
    }
    val resourceUrl = ResourceUrl.parse(attribute.value) ?: return

    // Is this a vector icon? If so the boolean value will be true if we're using
    // support library vectors and false otherwise (bitmaps) ?
    // (This works for vectors declared in the same project as well because lint guarantees
    // it will visit the drawable folders before it visits the layout folders)
    val partialResults = context.getPartialResults(ISSUE)
    val drawableName = resourceUrl.name

    var useSupportLibrary: Boolean? = null
    var project: Project = context.project

    // Try to find the drawable name in the current project partial results first.
    val currentProjectPartialResultBool = partialResults.map().getBoolean(drawableName)
    if (currentProjectPartialResultBool != null) {
      useSupportLibrary = currentProjectPartialResultBool
    } else {
      // Search for the drawable name in partial results from dependent projects.
      for (projectToMap in partialResults.filter { it !== context.project }) {
        val partialResultBool = projectToMap.value.getBoolean(drawableName)
        if (partialResultBool != null) {
          useSupportLibrary = partialResultBool
          project = projectToMap.key
          break
        }
      }
    }
    if (useSupportLibrary == null) {
      return
    }

    if (useSupportLibrary && ATTR_SRC == name) {
      val location = context.getNameLocation(attribute)
      val message = "When using VectorDrawableCompat, you need to use `app:srcCompat`"
      val fix =
        fix()
          .replaceAttribute(ANDROID_URI, ATTR_SRC, attribute.value, AUTO_URI, ATTR_SRC_COMPAT)
          .build()
      val incident = Incident(ISSUE, attribute, location, message, fix)
      // Report with minSdk<21 constraint since consuming modules could have a higher
      // minSdkVersion
      context.report(incident, minSdkLessThan(21))
    } else if (!useSupportLibrary && ATTR_SRC_COMPAT == name) {
      val location = context.getNameLocation(attribute)

      var path = FN_BUILD_GRADLE
      val model = project.buildModule
      if (model != null) {
        path = model.modulePath + File.separator + path
      }
      val message =
        "To use VectorDrawableCompat, you need to set " +
          "`android.defaultConfig.vectorDrawables.useSupportLibrary = true` in `$path`"
      val incident = Incident(ISSUE, attribute, location, message)
      context.report(incident, minSdkLessThan(21))
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // We've already used the partial results map
  }
}
