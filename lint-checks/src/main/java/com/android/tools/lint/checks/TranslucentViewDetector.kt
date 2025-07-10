/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.CLASS_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.resolveManifestName
import com.android.utils.XmlUtils
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Attr
import org.w3c.dom.Element

const val ATTR_SCREEN_ORIENTATION = "screenOrientation"

/** Detects potential bugs such as the one described in b/b/33483680. */
class TranslucentViewDetector : Detector(), XmlScanner, SourceCodeScanner {
  companion object Issues {
    /** Mixing Translucency and Orientation. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "TranslucentOrientation",
        briefDescription = "Mixing screenOrientation and translucency",
        explanation =
          """
            Specifying a fixed screen orientation with a translucent theme isn't supported \
            on apps with `targetSdkVersion` O or greater since there can be an another activity \
            visible behind your activity with a conflicting request.

            For example, your activity requests landscape and the visible activity behind \
            your translucent activity request portrait. In this case the system can only \
            honor one of the requests and currently prefers to honor the request from \
            non-translucent activities since there is nothing visible behind them.

            Devices running platform version O or greater will throw an exception in your \
            app if this state is detected.
            """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            TranslucentViewDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
          ),
      )
  }

  private var interestingActivities: MutableList<String>? = null
  private var interestingThemes: MutableList<String>? = null
  private var defaultTheme: String? = null

  override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_SCREEN_ORIENTATION)

  override fun getApplicableElements(): Collection<String> = listOf(TAG_STYLE)

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  private fun isScreenOrientationFixed(orientation: String): Boolean {
    return orientation.endsWith("portrait", ignoreCase = true) ||
      orientation.endsWith("landscape", ignoreCase = true)
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    // If anyone specifies screenOrientation (other than "unspecified"), then
    // write down the theme applied on this activity.  (If theme is not specified,
    // look it up in the activity or worst of all, use default in manifest)
    if (ANDROID_URI != attribute.namespaceURI) {
      return
    }

    val value = attribute.value
    if (!isScreenOrientationFixed(value)) {
      return
    }

    val activity = attribute.ownerElement
    if (activity == null || !activity.hasAttributeNS(ANDROID_URI, ATTR_NAME)) {
      return
    }
    val name = resolveManifestName(activity, context.project)

    val theme = activity.getAttributeNS(ANDROID_URI, ATTR_THEME)
    if (theme.isBlank()) {
      val application = activity.parentNode as? Element
      defaultTheme = getThemeName(application?.getAttributeNS(ANDROID_URI, ATTR_THEME))
      addActivity(name)
    } else {
      addTheme(getThemeName(theme))
    }
  }

  private fun addActivity(name: String) {
    val activities =
      interestingActivities
        ?: run {
          val newList = mutableListOf<String>()
          interestingActivities = newList
          newList
        }
    activities.add(name)
  }

  private fun addTheme(theme: String?) {
    theme ?: return
    val themes =
      interestingThemes
        ?: run {
          val newList = mutableListOf<String>()
          interestingThemes = newList
          newList
        }
    themes.add(theme)
  }

  private fun getThemeName(themeUrl: String?): String? {
    themeUrl ?: return null

    // TODO(namespaces)
    val theme = ResourceUrl.parse(themeUrl)
    return if (theme?.type == ResourceType.STYLE && !theme.isFramework) {
      theme.name
    } else {
      null
    }
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val themes = interestingThemes

    if (themes == null && defaultTheme == null) {
      return
    }

    // TODO: Need full map to chase parent pointers etc
    val themeName = element.getAttribute(ATTR_NAME)
    if (themeName == defaultTheme || themes != null && themes.contains(themeName)) {
      // Look at children
      var curr = XmlUtils.getFirstSubTagByName(element, TAG_ITEM)
      while (curr != null) {
        val attributeName = curr.getAttribute(ATTR_NAME)
        if (attributeName == "android:windowIsFloating" || attributeName == "windowIsTranslucent") {
          val attributeNode = curr.getAttributeNode(ATTR_NAME)
          val location = context.getValueLocation(attributeNode)
          val message = "Should not specify screen orientation with translucent or floating theme"
          context.report(Incident(ISSUE, curr, location, message), map().put(ATTR_THEME, themeName))
          break
        }

        curr = XmlUtils.getNextTagByName(curr, TAG_ITEM)
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.targetSdk < AndroidVersion.VersionCodes.O) {
      return false
    }

    // TODO: Consider going about this from the other end: if we find
    // a theme occurrence, THEN look up merged manifest to see if it's referenced
    // from an interesting manifest location!
    val mergedManifest = context.mainProject.mergedManifest ?: return false
    val application = XmlUtils.getFirstSubTagByName(mergedManifest.documentElement, TAG_APPLICATION)
    var currentActivity = XmlUtils.getFirstSubTag(application)
    var last = incident.location
    val theme = map.getString(ATTR_THEME, "")
    while (currentActivity != null) {
      val attr = currentActivity.getAttributeNodeNS(ANDROID_URI, ATTR_SCREEN_ORIENTATION)
      // TODO - pick the one that doesn't specify unspecified (and ideally
      // map back to the same activity that contributed the activity,
      // which is why it might make sense to compute forwards instead.)
      if (attr != null) {
        val name = getThemeName(currentActivity.getAttributeNS(ANDROID_URI, ATTR_THEME))
        if (name == theme || name == null) {
          val secondary = context.getLocation(attr, LocationType.VALUE)
          last.secondary = secondary
          last = secondary
        }
      }

      currentActivity = XmlUtils.getNextTag(currentActivity)
    }

    return true
  }

  // In Java/Kotlin files, look for setTheme() calls in activities listed in
  // interestingActivities

  override fun getApplicableMethodNames(): List<String> {
    return listOf("setTheme")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val activities = interestingActivities ?: return
    val uClass = node.getParentOfType(UClass::class.java, true) ?: return
    if (!activities.contains(uClass.qualifiedName)) {
      return
    }

    if (!context.evaluator.inheritsFrom(uClass.javaPsi, CLASS_ACTIVITY, false)) {
      return
    }
    val arguments = node.valueArguments
    if (arguments.size != 1) {
      return
    }
    val reference = ResourceReference.get(arguments[0])
    if (reference?.type == ResourceType.STYLE && reference.`package` != ANDROID_PKG) {
      addTheme(reference.name)

      // We've already processed resources, so handle it in a second pass
      context.driver.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE)
    }
  }
}
