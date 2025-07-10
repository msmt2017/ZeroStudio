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

import com.android.SdkConstants.DOT_JSON
import com.android.SdkConstants.DOT_XML
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.BinaryResourceScanner
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.awt.image.BufferedImage
import java.util.EnumSet
import javax.imageio.ImageIO
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr

class ActivityIconColorDetector : Detector(), SourceCodeScanner, BinaryResourceScanner, XmlScanner {

  companion object {

    @JvmField
    val ISSUE =
      Issue.create(
        id = "ActivityIconColor",
        briefDescription = "Ongoing activity icon is not white",
        explanation =
          """
            The resources passed to `setAnimatedIcon` and `setStaticIcon` should be white \
            with a transparent background, preferably a VectorDrawable or AnimatedVectorDrawable.
            """,
        moreInfo =
          "https://developer.android.com/training/wearables/ongoing-activity#best-practices",
        category = Category.ICONS,
        priority = 4,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            ActivityIconColorDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.BINARY_RESOURCE_FILE, Scope.RESOURCE_FILE),
          ),
      )

    val COLOR_REGEX = """#[a-fA-F\d]{6}""".toRegex()
    const val BLACK = "#000000"
    const val WHITE = "#ffffff"
    const val ONGOING_ACTIVITY_BUILDER_METHOD = "androidx.wear.ongoing.OngoingActivity.Builder"
  }

  data class IconSetterInfo(
    val url: ResourceUrl,
    val element: UElement,
    val location: Location,
    val message: String,
  )

  private val iconSetCalls: MutableMap<String, IconSetterInfo> = mutableMapOf()

  override fun afterCheckRootProject(context: Context) {
    // request the second scanning phase only if icon set calls found
    if (context.phase == 1 && iconSetCalls.isNotEmpty()) {
      context.requestRepeat(this, EnumSet.of(Scope.BINARY_RESOURCE_FILE, Scope.ALL_RESOURCE_FILES))
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("setAnimatedIcon", "setStaticIcon")
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP
  }

  override fun checkBinaryResource(context: ResourceContext) {
    if (context.phase != 2) return
    val iconFileName = context.file.name
    val iconName = iconFileName.substringBeforeLast(".")
    val iconInfo = iconSetCalls[iconName] ?: return
    if (iconFileName.endsWith(DOT_XML) || iconFileName.endsWith(DOT_JSON)) return
    val image: BufferedImage = ImageIO.read(context.file)
    if (!IconDetector.isWhite(image)) {
      context.report(ISSUE, iconInfo.location, iconInfo.message)
    }
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (context.phase != 1) return
    if (method.containingClass?.qualifiedName != ONGOING_ACTIVITY_BUILDER_METHOD) return
    val iconResource = node.getArgumentForParameter(0) ?: return
    val url = ResourceEvaluator.getResource(context.evaluator, iconResource) ?: return
    val iconKind = if (method.name == "setAnimatedIcon") "animated icon" else "static icon"
    iconSetCalls[url.name] =
      IconSetterInfo(
        url = url,
        element = node,
        location = context.getLocation(iconResource),
        message =
          "The $iconKind for an ongoing activity should be white with a transparent background",
      )
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf("fillColor", "strokeColor", "color")
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (context.phase != 2) return
    val iconName = context.file.name.removeSuffixIfPresent(DOT_XML)
    val iconSetterInfo = iconSetCalls[iconName] ?: return
    val fillColor = attribute.value.lowercase()
    if (COLOR_REGEX.matches(fillColor) && fillColor != BLACK && fillColor != WHITE) {
      context.report(ISSUE, context.getValueLocation(attribute), iconSetterInfo.message)
    }
  }
}
