/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.SdkConstants.ANDROID_PREFIX
import com.android.SdkConstants.ANDROID_THEME_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_AUTOFILL_HINTS
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_FOREGROUND
import com.android.SdkConstants.ATTR_FULL_BACKUP_CONTENT
import com.android.SdkConstants.ATTR_HEIGHT
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_IMPORTANT_FOR_AUTOFILL
import com.android.SdkConstants.ATTR_LABEL_FOR
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_ROUND_ICON
import com.android.SdkConstants.ATTR_TARGET_API
import com.android.SdkConstants.ATTR_TEXT_ALIGNMENT
import com.android.SdkConstants.ATTR_TEXT_IS_SELECTABLE
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.ATTR_WIDTH
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.SdkConstants.FQCN_FRAME_LAYOUT
import com.android.SdkConstants.FQCN_TARGET_API
import com.android.SdkConstants.FRAME_LAYOUT
import com.android.SdkConstants.PREFIX_ANDROID
import com.android.SdkConstants.TAG
import com.android.SdkConstants.TAG_ANIMATED_VECTOR
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.TAG_VECTOR
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_TAG
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.resourceNameToFieldName
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.sdklib.SdkVersionInfo
import com.android.tools.lint.checks.ApiLookup.UnsupportedVersionException
import com.android.tools.lint.checks.ApiLookup.equivalentName
import com.android.tools.lint.checks.ApiLookup.startsWithEquivalentPrefix
import com.android.tools.lint.checks.DesugaredMethodLookup.Companion.canBeDesugaredLater
import com.android.tools.lint.checks.DesugaredMethodLookup.Companion.isDesugaredClass
import com.android.tools.lint.checks.DesugaredMethodLookup.Companion.isDesugaredField
import com.android.tools.lint.checks.DesugaredMethodLookup.Companion.isDesugaredMethod
import com.android.tools.lint.checks.RtlDetector.ATTR_SUPPORTS_RTL
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.ResourceReference
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.ResourceRepositoryScope.PROJECT_ONLY
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.ApiConstraint.Companion.isInfinity
import com.android.tools.lint.detector.api.ApiConstraint.Companion.max
import com.android.tools.lint.detector.api.ApiLevel
import com.android.tools.lint.detector.api.ApiLevel.Companion.isFullSdkInt
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext.Companion.getFqcn
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceFolderScanner
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.SourceSetType
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getLongAttribute
import com.android.tools.lint.detector.api.VersionChecks
import com.android.tools.lint.detector.api.VersionChecks.Companion.REQUIRES_API_ANNOTATION
import com.android.tools.lint.detector.api.VersionChecks.Companion.REQUIRES_EXTENSION_ANNOTATION
import com.android.tools.lint.detector.api.VersionChecks.Companion.SDK_INT
import com.android.tools.lint.detector.api.VersionChecks.Companion.SDK_INT_FULL
import com.android.tools.lint.detector.api.VersionChecks.Companion.getTargetApiAnnotation
import com.android.tools.lint.detector.api.VersionChecks.Companion.getTargetApiForAnnotation
import com.android.tools.lint.detector.api.VersionChecks.Companion.getVersionCheckConditional
import com.android.tools.lint.detector.api.VersionChecks.Companion.isPrecededByVersionCheckExit
import com.android.tools.lint.detector.api.VersionChecks.Companion.isRequiresApiAnnotation
import com.android.tools.lint.detector.api.VersionChecks.Companion.isTargetAnnotation
import com.android.tools.lint.detector.api.VersionChecks.Companion.isWithinVersionCheckConditional
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.android.tools.lint.detector.api.asCall
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.getChildren
import com.android.tools.lint.detector.api.getInternalMethodName
import com.android.tools.lint.detector.api.isInlined
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.minSdkAtLeast
import com.android.tools.lint.detector.api.minSdkLessThan
import com.android.tools.lint.detector.api.resolveOperator
import com.android.tools.lint.detector.api.typeFromPsi
import com.android.utils.XmlUtils
import com.android.utils.usLocaleCapitalize
import com.intellij.psi.CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE
import com.intellij.psi.CommonClassNames.JAVA_LANG_OBJECT
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import java.io.IOException
import java.util.EnumSet
import kotlin.math.max
import org.jetbrains.kotlin.analysis.decompiled.light.classes.KtLightClassForDecompiledDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightElementBase
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastSpecialExpressionKind
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.util.isInstanceCheck
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.util.isTypeCast
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Looks for usages of APIs that are not supported in all the versions targeted by this application
 * (according to its minimum API requirement in the manifest).
 */
class ApiDetector : ResourceXmlDetector(), SourceCodeScanner, ResourceFolderScanner {
  private var apiDatabase: ApiLookup? = null
  private var invalidDatabaseFormatError: String? = null

  override fun beforeCheckRootProject(context: Context) {
    if (apiDatabase == null && invalidDatabaseFormatError == null) {
      try {
        apiDatabase = ApiLookup.get(context.client, context.project.buildTarget)
        // We can't look up the minimum API required by the project here:
        // The manifest file hasn't been processed yet in the -before- project hook.
        // For now, it's initialized lazily in getMinSdk(Context), but the
        // lint infrastructure should be fixed to parse manifest file up front.
      } catch (e: UnsupportedVersionException) {
        invalidDatabaseFormatError =
          e.getDisplayMessage(context.client) + ": Lint API checks unavailable."
      }
    }
  }

  // ---- Implements XmlScanner ----

  override fun getApplicableElements(): Collection<String> {
    return XmlScannerConstants.ALL
  }

  override fun getApplicableAttributes(): Collection<String> {
    return XmlScannerConstants.ALL
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val apiDatabase = apiDatabase ?: return

    var attributeApiLevel: ApiConstraint = ApiConstraint.ALL
    val namespace = attribute.namespaceURI
    if (ANDROID_URI == namespace) {
      val name = attribute.localName
      if (
        name != ATTR_LAYOUT_WIDTH &&
          name != ATTR_LAYOUT_HEIGHT &&
          name != ATTR_ID &&
          (!isAttributeOfGradientOrGradientItem(attribute) && name != "fillType" ||
            !context.project.dependsOnAppCompat())
      ) {
        val owner = "android/R\$attr"
        attributeApiLevel = apiDatabase.getFieldVersions(owner, name)
        val minSdk = getMinSdk(context) ?: return
        if (
          attributeApiLevel != ApiConstraint.UNKNOWN &&
            !(minSdk.isAtLeast(attributeApiLevel) ||
              context.folderVersion.isAtLeast(attributeApiLevel) ||
              getLocalMinSdk(attribute.ownerElement).isAtLeast(attributeApiLevel) ||
              isBenignUnusedAttribute(name) ||
              isAlreadyWarnedDrawableFile(context, attribute, attributeApiLevel))
        ) {
          if (RtlDetector.isRtlAttributeName(name) || ATTR_SUPPORTS_RTL == name) {
            // No need to warn for example that
            //  "layout_alignParentEnd will only be used in API level 17 and higher"
            // since we have a dedicated RTL lint rule dealing with those attributes
            //
            // However, paddingStart in particular is known to cause crashes
            // when used on TextViews (and subclasses of TextViews), on some
            // devices, because vendor specific attributes conflict with the
            // later-added framework resources, and these are apparently read
            // by the text views.
            //
            // However, as of build tools 23.0.1 aapt works around this by packaging
            // the resources differently. At this point everyone is using a newer
            // version of aapt.
          } else {
            val location = context.getLocation(attribute)
            val localName = attribute.localName
            var message =
              "Attribute `$localName` is only used in API level ${attributeApiLevel.minString()} and higher (current min is %1\$s)"

            // Supported by appcompat
            if ("fontFamily" == localName) {
              if (context.project.dependsOnAppCompat()) {
                val prefix = XmlUtils.lookupNamespacePrefix(attribute, AUTO_URI, "app", false)
                message += " Did you mean `$prefix:fontFamily` ?"
              }
            }
            report(context, UNUSED, attribute, location, message, attributeApiLevel, minSdk)
          }
        }
      }
      // Special case:
      // the dividers attribute is present in API 1, but it won't be read on older
      // versions, so don't flag the common pattern
      //    android:divider="?android:attr/dividerHorizontal"
      // since this will work just fine. See issue 36992041 for more.
      if (name == "divider") {
        return
      }

      if (name == ATTR_THEME && VIEW_INCLUDE == attribute.ownerElement.tagName) {
        // Requires API 23
        val minSdk = getMinSdk(context) ?: return
        if (!(minSdk.isAtLeast(API_23) || context.folderVersion.isAtLeast(API_23))) {
          val location = context.getLocation(attribute)
          val message =
            "Attribute `android:theme` is only used by `<include>` tags in API level 23 and higher (current min is %1\$s)"
          report(context, UNUSED, attribute, location, message, API_23, minSdk)
        }
      }

      if (
        name == ATTR_FOREGROUND &&
          context.resourceFolderType == ResourceFolderType.LAYOUT &&
          !isFrameLayout(context, attribute.ownerElement.tagName, true)
      ) {
        // Requires API 23, unless it's a FrameLayout
        val minSdk = getMinSdk(context) ?: return
        if (!(minSdk.isAtLeast(API_23) || context.folderVersion.isAtLeast(API_23))) {
          val location = context.getLocation(attribute)
          val message =
            "Attribute `android:foreground` has no effect on API levels lower than 23 (current min is %1\$s)"
          report(context, UNUSED, attribute, location, message, API_23, minSdk)
        }
      }
    } else if (TOOLS_URI == namespace) {
      val name = attribute.localName
      if (name == ATTR_TARGET_API) {
        val targetApiString = attribute.value
        val api = ApiLevel.getMinConstraint(targetApiString, ANDROID_SDK_ID)
        if (api != null) {
          val message = "Unnecessary; `SDK_INT` is always >= ${api.minString()}"
          val fix =
            fix()
              .replace()
              .all()
              .with("")
              .range(context.getLocation(attribute))
              .name("Delete ${attribute.name}")
              .build()
          context.report(
            Incident(OBSOLETE_SDK, message, context.getLocation(attribute), attribute, fix),
            minSdkAtLeast(api),
          )
          return
        }
      }
    }

    val value = attribute.value
    var owner: String? = null
    var name: String? = null
    val prefix: String?
    if (value.startsWith(ANDROID_PREFIX)) {
      prefix = ANDROID_PREFIX
    } else if (value.startsWith(ANDROID_THEME_PREFIX)) {
      prefix = ANDROID_THEME_PREFIX
      if (context.resourceFolderType == ResourceFolderType.DRAWABLE) {
        val api = API_21
        val minSdk = getMinSdk(context) ?: return
        if (
          !(minSdk.isAtLeast(api) ||
            context.folderVersion.isAtLeast(api) ||
            getLocalMinSdk(attribute.ownerElement).isAtLeast(api))
        ) {
          val location = context.getLocation(attribute)
          val message =
            "Using theme references in XML drawables requires API level ${api.minString()} (current min is %1\$s)"
          report(context, UNSUPPORTED, attribute, location, message, api, minSdk)
          // Don't flag individual theme attribute requirements here, e.g. once
          // we've told you that you need at least v21 to reference themes, we don't
          // need to also tell you that ?android:selectableItemBackground requires
          // API level 11
          return
        }
      }
    } else if (
      value.startsWith(PREFIX_ANDROID) &&
        ATTR_NAME == attribute.name &&
        TAG_ITEM == attribute.ownerElement.tagName &&
        attribute.ownerElement.parentNode != null &&
        TAG_STYLE == attribute.ownerElement.parentNode.nodeName
    ) {
      owner = "android/R\$attr"
      name = value.substring(PREFIX_ANDROID.length)
      prefix = null
    } else if (
      value.startsWith(PREFIX_ANDROID) &&
        ATTR_PARENT == attribute.name &&
        TAG_STYLE == attribute.ownerElement.tagName
    ) {
      owner = "android/R\$style"
      name = resourceNameToFieldName(value.substring(PREFIX_ANDROID.length))
      prefix = null
    } else {
      return
    }

    if (owner == null) {
      // Convert @android:type/foo into android/R$type and "foo"
      val index = value.indexOf('/', prefix?.length ?: 0)
      when {
        index >= 0 -> {
          owner = "android/R$" + value.substring(prefix?.length ?: 0, index)
          name = resourceNameToFieldName(value.substring(index + 1))
        }
        value.startsWith(ANDROID_THEME_PREFIX) -> {
          owner = "android/R\$attr"
          name = value.substring(ANDROID_THEME_PREFIX.length)
        }
        else -> return
      }
    }
    name ?: return
    val api = apiDatabase.getFieldVersions(owner, name)
    if (api == ApiConstraint.UNKNOWN) {
      return
    }
    val minSdk = getMinSdk(context) ?: return
    if (
      !(minSdk.isAtLeast(api) ||
        context.folderVersion.isAtLeast(api) ||
        getLocalMinSdk(attribute.ownerElement).isAtLeast(api))
    ) {
      // Don't complain about resource references in the tools namespace,
      // such as for example "tools:layout="@android:layout/list_content",
      // used only for designtime previews
      if (TOOLS_URI == namespace) {
        return
      }

      when {
        attributeApiLevel == ApiConstraint.UNKNOWN -> return
        attributeApiLevel.isAtLeast(api) -> {
          // The attribute will only be *read* on platforms >= attributeApiLevel.
          // If this isn't lower than the attribute reference's API level, it
          // won't be a problem
        }
        !minSdk.isAtLeast(attributeApiLevel) -> {
          val attributeName = attribute.localName
          val location = context.getLocation(attribute)
          val message =
            "`$name` requires API level ${api.minString()} (current min is %1\$s), but note " +
              "that attribute `$attributeName` is only used in API level " +
              "${attributeApiLevel.minString()} and higher"
          report(context, UNSUPPORTED, attribute, location, message, api, minSdk)
        }
        else -> {
          if (api.min() == 17 && RtlDetector.isRtlAttributeName(name)) {
            val old = RtlDetector.convertNewToOld(name)
            if (name != old) {
              val parent = attribute.ownerElement
              if (TAG_ITEM == parent.tagName) {
                // Is the same style also defining the other, older attribute?
                for (item in getChildren(parent.parentNode)) {
                  val v = item.getAttribute(ATTR_NAME)
                  if (v.endsWith(old)) {
                    return
                  }
                }
              } else if (parent.hasAttributeNS(ANDROID_URI, old)) {
                return
              }
            }
          }

          val location = context.getLocation(attribute)
          val minString = api.minString()
          val message = "`$value` requires API level $minString (current min is %1\$s)"
          report(context, UNSUPPORTED, attribute, location, message, api, minSdk)
        }
      }
    }
  }

  private fun isAttributeOfGradientOrGradientItem(attribute: Attr): Boolean {
    val element = attribute.ownerElement
    if (element.nodeName == "gradient") {
      return true
    }
    if (element.nodeName == "item") {
      return element.parentNode?.localName == "gradient"
    }
    return false
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val apiDatabase = apiDatabase ?: return
    var tag: String = element.tagName

    val folderType = context.resourceFolderType
    if (folderType != ResourceFolderType.LAYOUT) {
      if (folderType == ResourceFolderType.DRAWABLE) {
        checkElement(context, element, TAG_VECTOR, API_21, "1.4.0", UNSUPPORTED)
        checkElement(context, element, TAG_RIPPLE, API_21, null, UNSUPPORTED)
        checkElement(context, element, TAG_ANIMATED_SELECTOR, API_21, null, UNSUPPORTED)
        checkElement(context, element, TAG_ANIMATED_VECTOR, API_21, null, UNSUPPORTED)
        checkElement(context, element, "drawable", API_24, null, UNSUPPORTED)
        if ("layer-list" == tag) {
          checkLevelList(context, element)
        } else if (tag.contains(".")) {
          checkElement(context, element, tag, API_24, null, UNSUPPORTED)
        }
      }
      if (element.parentNode.nodeType != Node.ELEMENT_NODE) {
        // Root node
        return
      }
      val childNodes = element.childNodes
      var i = 0
      val n = childNodes.length
      while (i < n) {
        val textNode = childNodes.item(i)
        if (textNode.nodeType == Node.TEXT_NODE) {
          var text = textNode.nodeValue
          if (text.contains(ANDROID_PREFIX)) {
            text = text.trim()
            // Convert @android:type/foo into android/R$type and "foo"
            val index = text.indexOf('/', ANDROID_PREFIX.length)
            if (index != -1) {
              val typeString = text.substring(ANDROID_PREFIX.length, index)
              if (ResourceType.fromXmlValue(typeString) != null) {
                val owner = "android/R$$typeString"
                val name = resourceNameToFieldName(text.substring(index + 1))
                val api = apiDatabase.getFieldVersions(owner, name)
                if (api != ApiConstraint.UNKNOWN) {
                  val minSdk = getMinSdk(context) ?: return
                  if (
                    !(minSdk.isAtLeast(api) ||
                      context.folderVersion.isAtLeast(api) ||
                      getLocalMinSdk(element).isAtLeast(api))
                  ) {
                    val location = context.getLocation(textNode)
                    val message =
                      "`$text` requires API level ${api.minString()} (current min is %1\$s)"
                    report(context, UNSUPPORTED, element, location, message, api, minSdk)
                  }
                }
              }
            }
          }
        }
        i++
      }
    } else {
      if (VIEW_TAG == tag) {
        tag = element.getAttribute(ATTR_CLASS) ?: return
        if (tag.isEmpty()) {
          return
        }
      } else {
        // TODO: Complain if <tag> is used at the root level!
        checkElement(context, element, TAG, API_21, null, UNUSED)
      }

      // Check widgets to make sure they're available in this version of the SDK.
      if (tag.indexOf('.') != -1) {
        // Custom views aren't in the index
        return
      }
      var fqn = "android/widget/$tag"
      if (tag == "TextureView") {
        fqn = "android/view/TextureView"
      }
      // TODO: Consider other widgets outside of android.widget.*
      val api = apiDatabase.getClassVersions(fqn)
      if (api == ApiConstraint.UNKNOWN) {
        return
      }
      val minSdk = getMinSdk(context) ?: return
      if (!(minSdk.isAtLeast(api) || context.folderVersion.isAtLeast(api))) {
        val localMinSdk = getLocalMinSdk(element)
        if (!localMinSdk.isAtLeast(api)) {
          val location = context.getNameLocation(element)
          val message =
            "View requires API level ${api.minString()} (current min is %1\$s): `<$tag>`"
          report(context, UNSUPPORTED, element, location, message, api, localMinSdk)
        }
      }
    }
  }

  /**
   * Checks whether the given element is the given tag, and if so, whether it satisfied the minimum
   * version that the given tag is supported in.
   */
  private fun checkLevelList(context: XmlContext, element: Element) {
    var curr: Node? = element.firstChild
    while (curr != null) {
      if (curr.nodeType == Node.ELEMENT_NODE && TAG_ITEM == curr.nodeName) {
        val e = curr as Element
        if (
          e.hasAttributeNS(ANDROID_URI, ATTR_WIDTH) || e.hasAttributeNS(ANDROID_URI, ATTR_HEIGHT)
        ) {
          val attributeApiLevel = API_23 // Using width and height on layer-list children requires M
          val minSdk = getMinSdk(context) ?: return
          if (
            !(minSdk.isAtLeast(attributeApiLevel) ||
              context.folderVersion.isAtLeast(attributeApiLevel) ||
              getLocalMinSdk(element).isAtLeast(attributeApiLevel))
          ) {
            for (attributeName in arrayOf(ATTR_WIDTH, ATTR_HEIGHT)) {
              val attribute = e.getAttributeNodeNS(ANDROID_URI, attributeName) ?: continue
              val location = context.getLocation(attribute)
              val message =
                "Attribute `${attribute.localName}` is only used in API level ${attributeApiLevel.minString()} and higher (current min is %1\$s)"
              report(context, UNUSED, attribute, location, message, attributeApiLevel, minSdk)
            }
          }
        }
      }
      curr = curr.nextSibling
    }
  }

  /**
   * Checks whether the given element is the given tag, and if so, whether it satisfied the minimum
   * version that the given tag is supported in.
   */
  private fun checkElement(
    context: XmlContext,
    element: Element,
    tag: String,
    api: ApiConstraint,
    gradleVersion: String?,
    issue: Issue,
    useName: Boolean = false,
  ) {
    var realTag = tag
    if (realTag == element.tagName) {
      val minSdk = getMinSdk(context) ?: return
      if (
        !(minSdk.isAtLeast(api) ||
          context.folderVersion.isAtLeast(api) ||
          getLocalMinSdk(element).isAtLeast(api) ||
          featureProvidedByGradle(context, gradleVersion))
      ) {
        var location = context.getNameLocation(element)

        // For the <drawable> tag we report it against the class= attribute
        if ("drawable" == realTag) {
          val attribute = element.getAttributeNode(ATTR_CLASS) ?: return
          location = context.getLocation(attribute)
          realTag = attribute.value
        }

        var message: String
        if (issue === UNSUPPORTED) {
          message = "`<$realTag>` requires API level ${api.minString()} (current min is %1\$s)"
          if (gradleVersion != null) {
            message += " or building with Android Gradle plugin $gradleVersion or higher"
          } else if (realTag.contains(".") && !useName) {
            message =
              "Custom drawables requires API level ${api.minString()} (current min is %1\$s)"
          }
        } else {
          assert(issue === UNUSED) { issue }
          message =
            "`<$realTag>` is only used in API level ${api.minString()} and higher (current min is %1\$s)"
        }
        report(context, issue, element, location, message, api, minSdk)
      }
    }
  }

  private fun getMinSdk(context: Context): ApiConstraint? {
    val project = if (context.isGlobalAnalysis()) context.mainProject else context.project
    return if (!project.isAndroidProject) {
      // Don't flag API checks in non-Android projects
      null
    } else {
      project.minSdkVersions
    }
  }

  // ---- implements SourceCodeScanner ----

  override fun applicableAnnotations(): List<String> {
    return listOf(
      REQUIRES_API_ANNOTATION.oldName(),
      REQUIRES_API_ANNOTATION.newName(),
      REQUIRES_EXTENSION_ANNOTATION,
      ANDROIDX_SDK_SUPPRESS_ANNOTATION,
      FQCN_TARGET_API,
      ROBO_ELECTRIC_CONFIG_ANNOTATION,
    )
  }

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return when (type) {
      AnnotationUsageType.METHOD_CALL,
      AnnotationUsageType.METHOD_REFERENCE,
      AnnotationUsageType.FIELD_REFERENCE,
      AnnotationUsageType.CLASS_REFERENCE,
      AnnotationUsageType.ANNOTATION_REFERENCE,
      AnnotationUsageType.EXTENDS,
      AnnotationUsageType.DEFINITION,
      AnnotationUsageType.XML_REFERENCE -> true
      else -> false
    }
  }

  override fun inheritAnnotation(annotation: String): Boolean {
    return false
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val annotation = annotationInfo.annotation
    val member = usageInfo.referenced as? PsiMember
    val api: ApiConstraint =
      if (
        !isRequiresApiAnnotation(annotationInfo.qualifiedName) || usageInfo.annotations.size == 1
      ) {
        getApiLevel(context, annotation, annotationInfo.qualifiedName) ?: return
      } else {
        if (
          usageInfo.type != AnnotationUsageType.DEFINITION &&
            usageInfo.anyCloser { isRequiresApiAnnotation(it.qualifiedName) }
        ) {
          return
        }
        var constraint: ApiConstraint? = null
        val target = annotationInfo.annotated
        usageInfo.annotations.forEach { info ->
          val qualifiedName = info.qualifiedName
          if (info.annotated === target && isRequiresApiAnnotation(qualifiedName)) {
            val apiLevel = getApiLevel(context, info.annotation, qualifiedName)
            if (apiLevel != null) {
              if (constraint != null) {
                if (constraint?.findSdk(apiLevel.sdkId) == null) {
                  // When there are multiple API requirement annotations on the same target,
                  // they're *all* required/available!
                  constraint =
                    max(constraint, apiLevel, either = !REPEATED_API_ANNOTATION_REQUIRES_ALL)
                }
              } else {
                constraint = apiLevel
              }
            }
          }
        }
        constraint ?: return
      }
    val qualifiedName = annotation.qualifiedName ?: return
    var minSdk = getMinSdk(context) ?: return
    val evaluator = context.evaluator
    if (usageInfo.type == AnnotationUsageType.DEFINITION) {
      // This check applies only to platform SDK_INT checks.
      // (Later we could extend it to handle nested @RequiresExtension annotations,
      // and redundant ones based on manifest-declared extensions, but that seems lower priority.)
      if (api.getSdk() != ANDROID_SDK_ID) {
        return
      }
      val fix =
        fix()
          .replace()
          .all()
          .with("")
          .range(context.getLocation(annotation))
          .name("Delete @${qualifiedName.substringAfterLast('.')}")
          .build()

      val (targetAnnotation, target) =
        getTargetApiAnnotation(evaluator, element.uastParent?.uastParent)
      if (target != null && !api.isAtLeast(target)) {
        val outerAnnotation =
          "@${targetAnnotation?.qualifiedName?.substringAfterLast('.')}(${target.minString()})"
        val message =
          "Unnecessary; `SDK_INT` is always >= ${target.minString()} from outer annotation (`$outerAnnotation`)"
        context.report(
          Incident(OBSOLETE_SDK, message, context.getLocation(annotation), annotation, fix)
        )
      } else {
        val message = "Unnecessary; `SDK_INT` is always >= ${api.minString()}"
        context.report(
          Incident(OBSOLETE_SDK, message, context.getLocation(annotation), annotation, fix),
          minSdkAtLeast(api.min()),
        )
      }
      return
    }
    if (!isRequiresApiAnnotation(qualifiedName)) {
      // These two annotations do not propagate the requirement outwards to callers
      return
    }

    val (suppressed, localMinSdk) = getSuppressed(context, api, element, minSdk)
    if (suppressed) {
      return
    }

    // If the constraint implies a higher SDK_INT, bump up the value here to include in the
    // error message. This will make it clearer to users that the SDK_INT is being taken into
    // account, yet still isn't high enough.
    minSdk = max(minSdk, localMinSdk)

    val (targetAnnotation, _) = getTargetApiAnnotation(evaluator, element)
    if (
      targetAnnotation != null &&
        isSurroundedByHigherTargetAnnotation(evaluator, targetAnnotation, api)
    ) {
      // Make sure we aren't interpreting a redundant local @RequireApi(x) annotation
      // as implying the API level can be x here if there is an *outer* annotation
      // with a higher API level (we flag those above using [OBSOLETE_SDK_LEVEL] but
      // since that's a warning and this type is an error, make sure we don't have
      // false positives.
      return
    }

    // A @RequiresApi annotation on a class means that the class is present
    // (unlike a class reference to an Android runtime class with an API
    // requirement). It's normally safe to refer to these in instanceof checks,
    // casts, etc -- it's usage of methods or fields within the class that is
    // unsafe. The annotation is normally placed on the class itself such that
    // it recursively applies to all methods and fields within.
    //
    // However, there are exceptions. If your class extends a runtime class
    // with a higher API level than the minSdkVersion, then even an instanceof
    // check will crash. Therefore, in order to avoid complaining about the
    // usually-safe references to local classes, but still warn about unsafe
    // usages, here we'll check all the super types of the annotated class and
    // if any of them exceed the minimumSdkVersion requirement, then we'll
    // complain about the class reference.
    if (usageInfo.type == AnnotationUsageType.CLASS_REFERENCE) {
      val parent = element.uastParent
      val type =
        if (parent is UBinaryExpressionWithType) {
          parent.type
        } else if (element is UClassLiteralExpression) {
          element.type
        } else null
      if (type != null) {
        val apiDatabase = apiDatabase ?: return
        val cls = evaluator.getTypeClass(type) ?: return
        var max: ApiConstraint = ApiConstraint.ALL
        val superClasses = InheritanceUtil.getSuperClasses(cls)
        for (superClass in superClasses) {
          val superClassQualifiedName = superClass.qualifiedName ?: continue
          if (superClassQualifiedName == JAVA_LANG_OBJECT) continue
          val versions = apiDatabase.getClassVersions(superClassQualifiedName)
          if (versions == ApiConstraint.UNKNOWN) {
            continue
          }
          max = max and versions
        }
        if (minSdk.isAtLeast(max)) {
          return
        }
      }
    }

    if (
      element is UCallExpression &&
        member is PsiMethod &&
        isSuperCallFromOverride(evaluator, element, member)
    ) {
      // Don't flag calling super.X() in override fun X()
      return
    }

    val location: Location
    val fqcn: String?
    if (
      element is UCallExpression &&
        element.kind != UastCallKind.METHOD_CALL &&
        element.classReference != null
    ) {
      val classReference = element.classReference!!
      location = context.getRangeLocation(element, 0, classReference, 0)
      fqcn = classReference.resolvedName ?: member?.name ?: ""
    } else {
      location = context.getNameLocation(element)
      fqcn = member?.name ?: ""
    }
    val type =
      when (usageInfo.type) {
        AnnotationUsageType.EXTENDS -> "Extending $fqcn"
        AnnotationUsageType.ANNOTATION_REFERENCE,
        AnnotationUsageType.CLASS_REFERENCE -> "Class"
        AnnotationUsageType.METHOD_RETURN,
        AnnotationUsageType.METHOD_OVERRIDE -> "Method"
        AnnotationUsageType.VARIABLE_REFERENCE,
        AnnotationUsageType.FIELD_REFERENCE -> "Field"
        else -> "Call"
      }
    val field = usageInfo.referenced
    val issue =
      if (field is PsiField && isInlined(field, context.evaluator)) {
        if (
          isBenignConstantUsage(
            context.evaluator,
            field,
            element,
            field.name,
            field.containingClass?.qualifiedName ?: "",
          )
        ) {
          return
        }
        INLINED
      } else {
        UNSUPPORTED
      }

    ApiVisitor(context).report(issue, element, location, type, fqcn, api, minSdk)
  }

  override fun visitAnnotationUsage(
    context: XmlContext,
    reference: Node,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val annotation = annotationInfo.annotation
    val qualifiedName = annotationInfo.qualifiedName
    if (isRequiresApiAnnotation(qualifiedName)) {
      val api = getApiLevel(context, annotation, qualifiedName) ?: return
      val className = (usageInfo.referenced as? PsiClass)?.qualifiedName ?: return
      val minSdk = getMinSdk(context) ?: return
      val element =
        reference as? Element
          ?: (reference as? Attr)?.ownerElement
          ?: reference.parentNode as? Element
          ?: reference.ownerDocument.documentElement
      if (
        !(minSdk.isAtLeast(api) ||
          context.folderVersion.isAtLeast(api) ||
          getLocalMinSdk(element).isAtLeast(api))
      ) {
        val message = "`<$className>` requires API level ${api.minString()} (current min is %1\$s)"
        report(context, UNSUPPORTED, element, context.getLocation(element), message, api, minSdk)
      }
    }
  }

  /**
   * If you're simply calling super.X from method X, even if method X is in a higher API level than
   * the minSdk, we're generally safe; that method should only be called by the framework on the
   * right API levels. (There is a danger of somebody calling that method locally in other contexts,
   * but this is hopefully unlikely.)
   */
  private fun isSuperCallFromOverride(
    evaluator: JavaEvaluator,
    call: UCallExpression,
    method: PsiMethod,
  ): Boolean {
    val receiver = call.receiver
    if (receiver is USuperExpression) {
      val containingMethod = call.getContainingUMethod()?.javaPsi
      if (
        containingMethod != null &&
          getInternalMethodName(method) == containingMethod.name &&
          evaluator.areSignaturesEqual(method, containingMethod) &&
          // We specifically exclude constructors from this check, because we
          // do want to flag constructors requiring the new API level; it's
          // highly likely that the constructor is called by local code, so
          // you should specifically investigate this as a developer
          !method.isConstructor
      ) {
        return true
      }
    }
    return false
  }

  /**
   * Is there an outer annotation (outside [annotation] that specifies an api level requirement of
   * [atLeast])?
   */
  private fun isSurroundedByHigherTargetAnnotation(
    evaluator: JavaEvaluator,
    annotation: UAnnotation?,
    atLeast: ApiConstraint,
    isApiLevelAnnotation: (String) -> Boolean = VersionChecks.Companion::isTargetAnnotation,
  ): Boolean {
    var curr = annotation ?: return false
    while (true) {
      val (outer, target) =
        getTargetApiAnnotation(evaluator, curr.uastParent?.uastParent, isApiLevelAnnotation)
      if (target != null && target.isAtLeast(atLeast)) {
        return true
      }
      curr = outer ?: return false
    }
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (apiDatabase == null) {
      if (invalidDatabaseFormatError != null) {
        // Attach the error to the first important source element (skipping comments and imports
        // etc)
        firstSourceElement(context.uastFile)?.let { declaration ->
          val message = invalidDatabaseFormatError!!
          context.report(UNSUPPORTED, declaration, context.getLocation(declaration), message)
        }
      }
      return null
    }
    val project = if (context.isGlobalAnalysis()) context.mainProject else context.project
    return if (project.isAndroidProject) {
      ApiVisitor(context)
    } else {
      null
    }
  }

  private fun firstSourceElement(file: UFile?): PsiElement? {
    file ?: return null
    val first = file.sourcePsi.firstChild
    var curr = first
    while (curr is PsiWhiteSpace) {
      curr = curr.nextSibling
    }
    return curr
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(
      USimpleNameReferenceExpression::class.java,
      ULocalVariable::class.java,
      UTryExpression::class.java,
      UBinaryExpressionWithType::class.java,
      UBinaryExpression::class.java,
      UUnaryExpression::class.java,
      UCallExpression::class.java,
      UClass::class.java,
      UMethod::class.java,
      UForEachExpression::class.java,
      UClassLiteralExpression::class.java,
      USwitchExpression::class.java,
      UCallableReferenceExpression::class.java,
      UArrayAccessExpression::class.java,
      UAnnotation::class.java,
    )
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val mainProject = context.mainProject
    val mainMinSdk =
      if (!mainProject.isAndroidProject) {
        // Don't flag API checks in non-Android projects
        return false
      } else {
        mainProject.minSdkVersions
      }

    val requires = map.getApiConstraint(KEY_REQUIRES_API) ?: return false
    if (mainMinSdk.isAtLeast(requires)) {
      return false
    }

    val desugaring = map.getInt(KEY_DESUGAR, null)?.let { Desugaring.fromConstant(it) }
    var libraryDesugaring = false
    if ((desugaring == null || desugaring == Desugaring.JAVA_8_LIBRARY)) {
      // See if library desugaring is turned on in the main project
      if (mainProject.isDesugaring(Desugaring.JAVA_8_LIBRARY)) {
        libraryDesugaring = true
        val owner = map.getString(KEY_OWNER, null)
        if (owner != null && canBeDesugaredLater(owner)) {
          val name = map.getString(KEY_NAME)
          val desc = map.getString(KEY_DESC)
          val sourceSet =
            map.getString(KEY_SOURCE_SET)?.let { SourceSetType.valueOf(it) } ?: SourceSetType.MAIN

          if (name != null && desc != null) {
            if (isDesugaredMethod(owner, name, desc, sourceSet, mainProject, null)) {
              return false
            }
          } else if (name != null) {
            if (isDesugaredField(owner, name, sourceSet, mainProject, null)) {
              return false
            }
          } else {
            val lookup = DesugaredMethodLookup.getLookup(mainProject, sourceSet)
            if (lookup.isDesugaredClass(owner)) {
              return false
            } else {
              val message = map.getString(KEY_MESSAGE, "") ?: ""
              if (message.startsWith("Implicit cast ")) {
                if (lookup.isClassPartiallyDesugared(owner)) {
                  // For implicit casts we're also okay with classes that are partially
                  // mentioned; the assumption is it's probably okay
                  return false
                }
              }
            }
          }
        }
      }
    } else if (mainProject.isDesugaring(desugaring)) {
      return false
    }

    // The known minimum API constraints at the call site (e.g. due to SDK_INT checks etc.)
    // which can be higher than the minSdkVersion both there and in the consuming module
    // (but never as high as the requirement), so take the max.
    val target = map.getApiConstraint(KEY_MIN_API) ?: return false
    val maxMinSdk = if (!target.isEmpty()) max(target, mainMinSdk) else mainMinSdk
    // Display the current minimum; there can be multiple minimums when the API
    // is available in many versions, so pick the first one.
    // If we don't have any versions of the given SDK, list that as version "0".
    // (This is different from the Android SDK where you always have at least version 1.)
    val minSdk =
      maxMinSdk.findSdk(requires.getConstraints().first().getSdk(), true)?.minString() ?: "0"

    // Update the minSdkVersion included in the message
    val formatString = map.getString(KEY_MESSAGE) ?: return false
    val message = String.format(formatString, minSdk)
    incident.message = message

    if (!libraryDesugaring) {
      val owner = map.getString(KEY_OWNER, null)
      if (owner != null && canBeDesugaredLater(owner)) {
        val name = map.getString(KEY_NAME)
        val desc = map.getString(KEY_DESC)
        val lookup = DesugaredMethodLookup.getBundledLibraryDesugaringRules(mainProject)
        var isDesugarable = false
        if (name != null && desc != null) {
          if (lookup.isDesugaredMethod(owner, name, desc)) {
            isDesugarable = true
          }
        } else if (name != null) {
          if (lookup.isDesugaredField(owner, name)) {
            isDesugarable = true
          }
        } else if (lookup.isDesugaredClass(owner)) {
          isDesugarable = true
        }
        if (isDesugarable) {
          val index = message.indexOf(" (")
          if (index != -1) {
            incident.message =
              message.substring(0, index) +
                ", or core library desugaring" +
                message.substring(index)
          }
        }
      }
    }

    return true
  }

  private fun report(
    context: XmlContext,
    issue: Issue,
    scope: Node,
    location: Location,
    message: String,
    api: ApiConstraint,
    minSdk: ApiConstraint,
  ) {
    assert(message.contains("%1\$s"))
    val incident =
      Incident(
        issue = issue,
        message = "", // always formatted in accept before reporting
        location = location,
        scope = scope,
        fix = apiLevelFix(api, minSdk),
      )
    val map =
      map().apply {
        put(KEY_REQUIRES_API, api)
        put(KEY_MESSAGE, message)

        assert(minSdk !== ApiConstraint.UNKNOWN)
        val element =
          when (scope) {
            is Attr -> scope.ownerElement
            is Element -> scope
            else -> scope.ownerDocument.documentElement
          }
        put(KEY_MIN_API, max(minSdk, getLocalMinSdk(element)))
      }
    context.report(incident, map)
  }

  private inner class ApiVisitor(private val context: JavaContext) : UElementHandler() {

    fun report(
      issue: Issue,
      node: UElement,
      location: Location,
      type: String,
      sig: String,
      requires: ApiConstraint,
      minSdk: ApiConstraint,
      fix: LintFix? = null,
      owner: String? = null,
      name: String? = null,
      desc: String? = null,
      desugaring: Desugaring? = null,
      original: String? = null,
    ) {
      val missing = minSdk.firstMissing(requires) ?: requires
      val apiLevel = getApiLevelString(missing, context)
      val typeString = type.usLocaleCapitalize()
      val sdk = missing.getSdk()
      var formatString =
        if (sdk == ANDROID_SDK_ID)
          "$typeString requires API level $apiLevel (current min is %1\$s): `$sig`"
        else {
          val sdkString =
            apiDatabase?.getSdkName(sdk) ?: ExtensionSdk.getSdkExtensionField(sdk, false)
          "$typeString requires version $apiLevel of ${if (sdkString[0].isDigit()) "SDK $sdkString" else "the $sdkString SDK"} " +
            "(current min is %1\$s): `$sig`"
        }
      if (original != null) {
        formatString += " (called from `$original`)"
      }

      var suggestedFix = fix ?: apiLevelFix(missing, minSdk)

      var location = location
      if (
        owner == "java.util.List" &&
          (name == "removeFirst" || name == "removeLast") &&
          node is UCallExpression
      ) {
        location = context.getCallLocation(node, includeReceiver = false, includeArguments = true)
        val replacement = getRemoveReplacementSource(name, node)
        val display = getRemoveReplacementSourceDisplay(replacement)
        formatString +=
          " (Prior to API level 35, this call would resolve to a Kotlin stdlib extension function. You can use `$display` instead.)"

        suggestedFix =
          fix()
            .alternatives(createRemoveFirstFix(location, name, replacement, display), suggestedFix)
      }

      report(
        issue,
        node,
        location,
        formatString,
        suggestedFix,
        owner,
        name,
        desc,
        missing,
        minSdk,
        desugaring,
      )
    }

    private fun report(
      issue: Issue,
      node: UElement,
      location: Location,
      formatString: String, // one parameter: minSdkVersion
      fix: LintFix? = null,
      owner: String? = null,
      name: String? = null,
      desc: String? = null,
      requires: ApiConstraint,
      min: ApiConstraint,
      desugaring: Desugaring? = null,
    ) {
      val incident =
        Incident(
          issue = issue,
          message = "", // always formatted in accept() before reporting
          location = location,
          scope = node,
          fix = fix,
        )
      val map =
        map().apply {
          put(KEY_REQUIRES_API, requires)
          put(KEY_MIN_API, max(min, getTargetApi(node)))
          put(KEY_MESSAGE, formatString)
          if (owner != null && canBeDesugaredLater(owner)) {
            put(KEY_OWNER, owner)
            if (name != null) {
              put(KEY_NAME, name)
              if (desc != null) {
                put(KEY_DESC, desc)
              }
            }
            val sourceSetType = context.sourceSetType
            if (sourceSetType != SourceSetType.INVALID && sourceSetType != SourceSetType.MAIN) {
              put(KEY_SOURCE_SET, sourceSetType.name)
            }
          }
          if (desugaring != null) {
            put(KEY_DESUGAR, desugaring.constant)
          }
        }
      context.report(incident, map)
    }

    override fun visitAnnotation(node: UAnnotation) {
      if (node.qualifiedName.isInjectAnnotationName()) {
        val anchor = node.getParentOfType<UAnnotated>()
        if (anchor != null) {
          checkInjection(anchor)
        }
      }
    }

    private fun checkInjection(element: UElement?) {
      element ?: return
      if (element is UMethod) {
        val node = element
        for (parameter in node.uastParameters) {
          checkInjectionType(parameter, parameter.typeFromPsi)
        }
        if (element.sourcePsi is KtProperty) {
          checkInjectionType(element, element.returnType)
        }
      } else if (element is UField) {
        checkInjectionType(element, element.typeFromPsi)
      }
    }

    private fun checkInjectionType(element: UElement, type: PsiType?) {
      val classType = type as? PsiClassType ?: return
      val cls = classType.resolve() ?: return
      val owner = cls.qualifiedName ?: return
      if (apiDatabase?.containsClass(owner) == false) {
        // See if it's an injected method
        for (constructor in cls.constructors) {
          @Suppress("ExternalAnnotations")
          if (constructor.annotations.any { it.qualifiedName.isInjectAnnotationName() }) {
            for (injectedParameter in constructor.parameterList.parameters) {
              val type = injectedParameter.type as? PsiClassType ?: continue
              // report the error back on the original call site referencing this injected
              // parameter
              // (which could be in bytecode)
              checkClassReference(element, type)
            }
          }
        }
      } else {
        checkClassReference(element, classType)
      }
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
      val resolved = node.resolve()
      if (resolved is PsiField) {
        checkField(node, resolved)
      }
    }

    override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
      val resolved = node.resolve()
      if (resolved is PsiMethod) {
        checkMethodReference(node, resolved)
      }
    }

    private fun checkMethodReference(expression: UReferenceExpression, method: PsiMethod) {
      val apiDatabase = apiDatabase ?: return

      val containingClass = method.containingClass ?: return
      val evaluator = context.evaluator
      val owner = evaluator.getQualifiedName(containingClass) ?: return // Couldn't resolve type
      if (!apiDatabase.containsClass(owner)) {
        return
      }

      val name = getInternalMethodName(method)
      val desc =
        evaluator.getMethodDescription(
          method,
          false,
          false,
        ) // Couldn't compute description of method for some reason; probably
          // failure to resolve parameter types
          ?: return

      val api = apiDatabase.getMethodVersions(owner, name, desc)
      if (api == ApiConstraint.UNKNOWN) {
        return
      }
      var minSdk = getMinSdk(context) ?: return
      val (suppressed, localMinSdk) = getSuppressed(context, api, expression, minSdk)
      if (suppressed) {
        return
      }

      // Builtin R8 desugaring, such as rewriting compare calls (see b/36390874)
      if (
        canBeDesugaredLater(owner) &&
          isDesugaredMethod(
            owner,
            name,
            desc,
            context.sourceSetType,
            if (context.driver.isGlobalAnalysis()) context.mainProject else context.project,
            containingClass,
          )
      ) {
        return
      }

      minSdk = max(minSdk, localMinSdk)

      val signature = expression.asSourceString()
      val location = context.getLocation(expression)
      report(
        UNSUPPORTED,
        expression,
        location,
        "Method reference",
        signature,
        api,
        minSdk,
        null,
        owner,
        name,
        desc,
      )
    }

    override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
      if (node.isTypeCast()) {
        visitTypeCastExpression(node)
      } else if (node.isInstanceCheck()) {
        val typeReference = node.typeReference
        if (typeReference != null) {
          val type = typeReference.type
          if (type is PsiClassType) {
            checkClassReference(typeReference, type)
          }
        }
      }
    }

    private fun visitTypeCastExpression(expression: UBinaryExpressionWithType) {
      val operand = expression.operand
      val operandType = operand.getExpressionType()
      val castType = expression.type
      if (castType == operandType) {
        return
      }
      if (operandType !is PsiClassType) {
        return
      }
      if (castType !is PsiClassType) {
        return
      }

      val typeReference = expression.typeReference
      if (typeReference != null) {
        if (!checkClassReference(typeReference, castType)) {
          // Found problem with cast type itself: don't bother also warning
          // about problem with LHS
          return
        }
      }

      checkCast(expression, operandType, castType, implicit = false)
    }

    private fun checkClassReference(node: UElement, classType: PsiClassType): Boolean {
      val apiDatabase = apiDatabase ?: return true
      val evaluator = context.evaluator
      val expressionOwner = evaluator.getQualifiedName(classType) ?: return true
      val api = apiDatabase.getClassVersions(expressionOwner)
      if (api == ApiConstraint.UNKNOWN) {
        return true
      }
      var minSdk = getMinSdk(context) ?: return true
      val (suppressed, localMinSdk) = getSuppressed(context, api, node, minSdk)
      if (suppressed) {
        return true
      }
      minSdk = max(minSdk, localMinSdk)

      val location = context.getLocation(node)
      report(
        UNSUPPORTED,
        node,
        location,
        "Class",
        expressionOwner,
        api,
        minSdk,
        null,
        expressionOwner,
      )
      return false
    }

    private fun checkCast(
      node: UElement,
      classType: PsiClassType,
      interfaceType: PsiClassType,
      implicit: Boolean = true,
    ) {
      if (classType == interfaceType) {
        return
      }
      val evaluator = context.evaluator
      val classTypeInternal = evaluator.getQualifiedName(classType)
      val interfaceTypeInternal = evaluator.getQualifiedName(interfaceType)
      checkCast(node, classTypeInternal, interfaceTypeInternal, implicit)
    }

    private fun checkCast(
      node: UElement,
      classType: String?,
      interfaceType: String?,
      implicit: Boolean,
    ) {
      if (interfaceType == null || classType == null) {
        return
      }
      if (equivalentName(interfaceType, "java/lang/Object")) {
        return
      }

      val apiDatabase = apiDatabase ?: return
      val api = apiDatabase.getValidCastVersions(classType, interfaceType)
      if (api == ApiConstraint.UNKNOWN) {
        return
      }

      var minSdk = getMinSdk(context) ?: return
      if (minSdk.isAtLeast(api)) {
        return
      }

      val (suppressed, localMinSdk) = getSuppressed(context, api, node, minSdk)
      if (suppressed) {
        return
      }
      minSdk = max(minSdk, localMinSdk)

      if (node is UReferenceExpression) {
        val resolved = node.tryResolve()
        if (resolved is PsiField) {
          val owner = resolved.containingClass?.qualifiedName
          if (owner != null) {
            val versions = apiDatabase.getFieldVersions(owner, resolved.name)
            if (!minSdk.isAtLeast(versions)) {
              return
            }
          }
        } else if (resolved is PsiMethod) {
          val owner = resolved.containingClass?.qualifiedName
          if (owner != null) {
            val desc = context.evaluator.getMethodDescription(resolved, false, false)
            if (desc != null) {
              val versions =
                apiDatabase.getMethodVersions(owner, getInternalMethodName(resolved), desc)
              if (!minSdk.isAtLeast(versions)) {
                return
              }
            }
          }
        }
      }

      // Also see if this cast has been explicitly checked for
      var curr = node
      while (true) {
        when (curr) {
          is UIfExpression -> {
            if (node.isUastChildOf(curr.thenExpression, true)) {
              val condition = curr.condition.skipParenthesizedExprDown()
              if (condition is UBinaryExpressionWithType) {
                val type = condition.type
                // Explicitly checked with surrounding instanceof check
                if (
                  type is PsiClassType && context.evaluator.getQualifiedName(type) == interfaceType
                ) {
                  return
                }
              }
            }
          }
          is USwitchClauseExpressionWithBody -> {
            if (node.isUastChildOf(curr.body, true)) {
              for (case in curr.caseValues) {
                val condition = case.skipParenthesizedExprDown()
                if (condition is UBinaryExpressionWithType) {
                  val type = condition.type
                  if (
                    type is PsiClassType &&
                      context.evaluator.getQualifiedName(type) == interfaceType
                  ) {
                    return
                  }
                }
              }
            }
          }
          is UMethod -> {
            break
          }
        }
        curr = curr.uastParent ?: break
      }

      if (
        canBeDesugaredLater(classType) &&
          isDesugaredClass(
            classType,
            context.sourceSetType,
            if (context.driver.isGlobalAnalysis()) context.mainProject else context.project,
          ) ||
          // D8 library desugaring does seem to handle these, but they're missing
          // from the database; see b/381126163
          (classType == "java.nio.file.attribute.UserPrincipal" ||
            classType == "java.nio.file.attribute.GroupPrincipal") &&
            context.project.desugaring.contains(Desugaring.JAVA_8_LIBRARY)
      ) {
        return
      }

      var locationNode = node
      var implicitCast = implicit

      if (interfaceType == JAVA_LANG_AUTO_CLOSEABLE || interfaceType == "java.io.Closeable") {
        val selector = node.uastParent?.findSelector()
        if (selector is UCallExpression && selector.methodIdentifier?.name == "use") {
          // For the "use" method, instead of showing the cast error on the left
          // hand side expression (which use will implicitly cast), move the warning
          // to the "use" call instead.
          implicitCast = true
          locationNode = selector.methodIdentifier ?: selector
        }
      }

      val castType = if (implicitCast) "Implicit cast" else "Cast"
      val location = context.getLocation(locationNode)

      val message: String
      val to = interfaceType.substringAfterLast('.')
      val from = classType.substringAfterLast('.')
      message =
        if (interfaceType == classType) {
          "$castType to `$to` requires API level ${api.minString()} (current min is %1\$s)"
        } else {
          "$castType from `$from` to `$to` requires API level ${api.minString()} (current min is %1\$s)"
        }

      report(
        UNSUPPORTED,
        locationNode,
        location,
        message,
        apiLevelFix(api, minSdk),
        owner = classType,
        requires = api,
        min = minSdk,
      )
    }

    override fun visitMethod(node: UMethod) {
      val containingClass = node.javaPsi.containingClass

      // API check for default methods
      if (
        containingClass != null &&
          containingClass.isInterface &&
          // (unless using desugar which supports this for all API levels)
          !context.project.isDesugaring(Desugaring.INTERFACE_METHODS)
      ) {
        val methodModifierList = node.javaPsi.modifierList
        if (
          methodModifierList.hasExplicitModifier(PsiModifier.DEFAULT) ||
            methodModifierList.hasExplicitModifier(PsiModifier.STATIC)
        ) {
          val api = API_24 // minSdk for default methods
          val minSdk = getMinSdk(context) ?: return

          if (!isSuppressed(context, api, node, minSdk)) {
            val location = context.getLocation(node)
            val desc =
              if (methodModifierList.hasExplicitModifier(PsiModifier.DEFAULT)) "Default method"
              else "Static interface method"
            report(
              UNSUPPORTED,
              node,
              location,
              desc,
              containingClass.name + "#" + node.name,
              api,
              minSdk,
              null,
              containingClass.qualifiedName,
              desugaring = Desugaring.INTERFACE_METHODS,
            )
          }
        }
      }
    }

    private fun String?.isInjectAnnotationName(): Boolean {
      return this != null && endsWith("Inject")
    }

    override fun visitClass(node: UClass) {
      // Check for repeatable and type annotations
      if (
        node.isAnnotationType &&
          // Desugar adds support for type annotations
          !context.project.isDesugaring(Desugaring.TYPE_ANNOTATIONS)
      ) {
        val evaluator = context.evaluator
        for (annotation in evaluator.getAnnotations(node, false)) {
          val name = annotation.qualifiedName
          if ("java.lang.annotation.Repeatable" == name) {
            val api = API_24 // minSdk for repeatable annotations
            val minSdk = getMinSdk(context) ?: return
            if (!isSuppressed(context, api, node, minSdk)) {
              val location = context.getLocation(annotation)
              val min = max(minSdk, getTargetApi(node))
              val incident =
                Incident(
                  issue = UNSUPPORTED,
                  message = "", // always formatted in accept() before reporting
                  location = location,
                  scope = annotation,
                  fix = apiLevelFix(api, min),
                )
              val map =
                map().apply {
                  put(KEY_REQUIRES_API, api)
                  put(KEY_MIN_API, min)
                  put(
                    KEY_MESSAGE,
                    "Repeatable annotation requires API level ${api.minString()} (current min is %1\$s)",
                  )
                  put(KEY_DESUGAR, Desugaring.TYPE_ANNOTATIONS.constant)
                }
              context.report(incident, map)
            }
          }
        }
      }

      // Check super types
      for (typeReferenceExpression in node.uastSuperTypes) {
        val type = typeReferenceExpression.type
        if (type is PsiClassType) {
          val cls = type.resolve()
          if (cls != null) {
            checkClass(typeReferenceExpression, cls)
          }
        }
      }
    }

    override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
      val type = node.type
      if (type is PsiClassType) {
        val lhs = node.expression
        val locationElement = lhs ?: node
        checkClassType(locationElement, type, null)
      }
    }

    private fun checkClassType(element: UElement, classType: PsiClassType, descriptor: String?) {
      val owner = context.evaluator.getQualifiedName(classType)
      val fqcn = classType.canonicalText
      if (owner != null) {
        checkClass(element, descriptor, owner, fqcn)
      }
    }

    private fun checkClass(element: UElement, cls: PsiClass) {
      val owner = context.evaluator.getQualifiedName(cls) ?: return
      val fqcn = cls.qualifiedName
      if (fqcn != null) {
        checkClass(element, null, owner, fqcn)
      }
    }

    private fun checkClass(element: UElement, descriptor: String?, owner: String, fqcn: String) {
      val apiDatabase = apiDatabase ?: return
      val api = apiDatabase.getClassVersions(owner)
      if (api == ApiConstraint.UNKNOWN) {
        return
      }
      var minSdk = getMinSdk(context) ?: return
      val (suppressed, localMinSdk) = getSuppressed(context, api, element, minSdk)
      if (suppressed) {
        return
      }
      minSdk = max(minSdk, localMinSdk)

      // It's okay to reference classes from annotations
      if (element.getParentOfType<UElement>(UAnnotation::class.java) != null) {
        return
      }

      val location = context.getNameLocation(element)
      val desc = descriptor ?: "Class"
      report(UNSUPPORTED, element, location, desc, fqcn, api, minSdk, null, owner)
    }

    override fun visitForEachExpression(node: UForEachExpression) {
      // The for each method will implicitly call iterator() on the
      // Iterable that is used in the for each loop; make sure that
      // the API level for that

      val apiDatabase = apiDatabase ?: return
      val value = node.iteratedValue

      val evaluator = context.evaluator
      val type = value.getExpressionType()
      if (type is PsiClassType) {
        val expressionOwner = evaluator.getQualifiedName(type) ?: return
        val api = apiDatabase.getClassVersions(expressionOwner)
        if (api == ApiConstraint.UNKNOWN) {
          return
        }
        var minSdk = getMinSdk(context) ?: return
        val (suppressed, localMinSdk) = getSuppressed(context, api, node, minSdk)
        if (suppressed) {
          return
        }
        minSdk = max(minSdk, localMinSdk)

        val location = context.getLocation(value)
        var message =
          "The type of the for loop iterated value is " +
            "${type.canonicalText}, which requires API level ${api.minString()}" +
            " (current min is %1\$s)"

        // Add specific check ConcurrentHashMap#keySet and add workaround text.
        // This was an unfortunate incompatible API change in Open JDK 8, which is
        // not an issue for the Android SDK but is relevant if you're using a
        // Java library.
        if (value is UQualifiedReferenceExpression) {
          if ("keySet" == value.resolvedName) {
            val keySet = value.resolve()
            if (keySet is PsiMethod) {
              val containingClass = keySet.containingClass
              if (
                containingClass != null &&
                  "java.util.concurrent.ConcurrentHashMap" == containingClass.qualifiedName
              ) {
                message +=
                  "; to work around this, add an explicit cast to `(Map)` before the `keySet` call."
              }
            }
          }
        }
        report(
          UNSUPPORTED,
          node,
          location,
          message,
          apiLevelFix(api, minSdk),
          expressionOwner,
          name = "iterator",
          desc = "()",
          requires = api,
          min = minSdk,
        )
      }
    }

    override fun visitCallExpression(node: UCallExpression) {
      val method = node.resolve()
      if (method != null) {
        visitCall(method, node, node)
      }
    }

    private fun visitCall(method: PsiMethod, call: UCallExpression, reference: UElement) {
      val apiDatabase = apiDatabase ?: return
      val containingClass = method.containingClass ?: return
      val parameterList = method.parameterList
      if (parameterList.parametersCount > 0) {
        val parameters = parameterList.parameters
        for (i in parameters.indices) {
          val parameterType = parameters[i].type
          if (parameterType is PsiClassType) {
            val argument = call.getArgumentForParameter(i) ?: continue
            checkArgumentCast(argument, parameterType)
          } else if (parameterType is PsiEllipsisType) {
            val argument = call.getArgumentForParameter(i) ?: continue
            val elementType = parameterType.componentType as? PsiClassType ?: continue
            if (argument is UExpressionList && argument.kind == UastSpecialExpressionKind.VARARGS) {
              for (expression in argument.expressions) {
                checkArgumentCast(expression, elementType)
              }
            } else {
              checkArgumentCast(argument, elementType)
            }
          }
        }
      }

      val fromBinary =
        method is PsiCompiledElement ||
          containingClass is PsiCompiledElement ||
          containingClass is KtLightClassForDecompiledDeclaration

      if (!fromBinary && method !is KtLightElementBase) {
        // We're only checking the Android SDK below, which should
        // be provided as binary (android.jar) and if we're actually
        // running on sources we don't want to perform this check
        return
      }

      val evaluator = context.evaluator
      val owner = evaluator.getQualifiedName(containingClass) ?: return // Couldn't resolve type

      val name = getInternalMethodName(method)

      if (!apiDatabase.containsClass(owner)) {
        if (
          name == "get" &&
            owner.endsWith("Provider") &&
            method.parameterList.isEmpty &&
            (method.returnType as? PsiClassType)?.resolve() is PsiTypeParameter
        ) {
          // Dependency injection via provider?
          val type = call.getExpressionType() as? PsiClassType
          if (type != null) {
            checkClassReference(call, type)
          }
        } else {
          handleKotlinExtensionMethods(name, owner, call, evaluator, method, reference)
        }
        return
      }

      val desc =
        evaluator.getMethodDescription(
          method,
          includeName = false,
          includeReturn = false,
        ) // Couldn't compute description of method for some reason; probably
          // failure to resolve parameter types
          ?: return

      visitCall(method, call, reference, containingClass, owner, name, desc)
    }

    private fun checkArgumentCast(argument: UExpression, parameterType: PsiClassType) {
      val argumentType = argument.getExpressionType()
      if (
        argumentType == null ||
          parameterType == argumentType ||
          argumentType !is PsiClassType ||
          parameterType.rawType() == argumentType.rawType()
      ) {
        return
      }
      checkCast(argument, argumentType, parameterType, implicit = true)
    }

    private fun visitCall(
      method: PsiMethod,
      call: UCallExpression,
      reference: UElement,
      containingClass: PsiClass,
      owner: String,
      name: String,
      desc: String,
      original: String? = null,
    ) {
      val apiDatabase = apiDatabase ?: return
      val evaluator = context.evaluator
      if (
        startsWithEquivalentPrefix(owner, "java/text/SimpleDateFormat") &&
          name == CONSTRUCTOR_NAME &&
          desc != "()V"
      ) {
        val minSdk = getMinSdk(context) ?: return
        checkSimpleDateFormat(context, call, minSdk)
      } else if (
        name == "loadAnimator" &&
          owner == "android.animation.AnimatorInflater" &&
          desc == "(Landroid.content.Context;I)"
      ) {
        checkAnimator(context, call)
      }

      var api = apiDatabase.getMethodVersions(owner, name, desc)
      if (api == ApiConstraint.UNKNOWN) {
        return
      }
      var minSdk = getMinSdk(context) ?: return
      if (minSdk.isAtLeast(api)) {
        return
      }

      var fqcn = containingClass.qualifiedName

      val receiver =
        if (call.isMethodCall()) {
          call.receiver
        } else {
          null
        }

      // The lint API database contains two optimizations:
      // First, all members that were available in API 1 are omitted from the database,
      // since that saves about half of the size of the database, and for API check
      // purposes, we don't need to distinguish between "doesn't exist" and "available
      // in all versions".

      // Second, all inherited members were inlined into each class, so that it doesn't
      // have to do a repeated search up the inheritance chain.
      //
      // Unfortunately, in this custom PSI detector, we look up the real resolved method,
      // which can sometimes have a different minimum API.
      //
      // For example, SQLiteDatabase had a close() method from API 1. Therefore, calling
      // SQLiteDatabase is supported in all versions. However, it extends SQLiteClosable,
      // which in API 16 added "implements Closable". In this detector, if we have the
      // following code:
      //     void test(SQLiteDatabase db) { db.close }
      // here the call expression will be the close method on type SQLiteClosable. And
      // that will result in an API requirement of API 16, since the close method it now
      // resolves to is in API 16.
      //
      // To work around this, we can now look up the type of the call expression ("db"
      // in the above, but it could have been more complicated), and if that's a
      // different type than the type of the method, we look up *that* method from
      // lint's database instead. Furthermore, it's possible for that method to return
      // "-1" and we can't tell if that means "doesn't exist" or "present in API 1", we
      // then check the package prefix to see whether we know it's an API method whose
      // members should all have been inlined.
      if (call.isMethodCall()) {
        if (
          receiver != null &&
            receiver !is UThisExpression &&
            receiver.sourcePsi !is PsiSuperExpression
        ) {
          val receiverType = receiver.getExpressionType()
          if (receiverType is PsiClassType) {
            val containingType = context.evaluator.getClassType(containingClass)
            val inheritanceChain = getInheritanceChain(receiverType, containingType)
            if (inheritanceChain != null) {
              for (type in inheritanceChain) {
                val expressionOwner = evaluator.getQualifiedName(type)
                if (expressionOwner != null && expressionOwner != owner) {
                  val specificApi = apiDatabase.getMethodVersions(expressionOwner, name, desc)
                  if (specificApi == ApiConstraint.UNKNOWN) {
                    if (apiDatabase.isRelevantOwner(expressionOwner)) {
                      return
                    }
                  } else if (minSdk.isAtLeast(specificApi)) {
                    return
                  } else {
                    // For example, for Bundle#getString(String,String) the API level
                    // is 12, whereas for BaseBundle#getString(String,String) the API
                    // level is 21. If the code specified a Bundle instead of
                    // a BaseBundle, report the Bundle level in the error message
                    // instead.
                    if (!(specificApi.isAtLeast(api))) {
                      api = specificApi
                      fqcn = type.canonicalText
                    }
                  }
                }
              }
            }
          }
        } else {
          // Unqualified call; need to search in our super hierarchy
          var cls: PsiClass? = null
          val receiverType = call.receiverType
          if (receiverType is PsiClassType) {
            cls = receiverType.resolve()
          }

          if (receiver is UThisExpression || receiver is USuperExpression) {
            val pte = receiver as UInstanceExpression
            val resolved = pte.resolve()
            if (resolved is PsiClass) {
              cls = resolved
            }
          }

          while (cls != null) {
            if (cls is PsiAnonymousClass) {
              // If it's an unqualified call in an anonymous class, we need to
              // rely on the resolve method to find out whether the method is
              // picked up from the anonymous class chain or any outer classes
              var found = false
              val anonymousBaseType = cls.baseClassType
              val anonymousBase = anonymousBaseType.resolve()
              if (anonymousBase != null && anonymousBase.isInheritor(containingClass, true)) {
                cls = anonymousBase
                found = true
              } else {
                val surroundingBaseType =
                  PsiTreeUtil.getParentOfType(cls, PsiClass::class.java, true)
                if (
                  surroundingBaseType != null &&
                    surroundingBaseType.isInheritor(containingClass, true)
                ) {
                  cls = surroundingBaseType
                  found = true
                }
              }
              if (!found) {
                break
              }
            }
            val expressionOwner = evaluator.getQualifiedName(cls)
            if (expressionOwner == null || equivalentName(expressionOwner, "java/lang/Object")) {
              break
            }
            val specificApi = apiDatabase.getMethodVersions(expressionOwner, name, desc)
            if (specificApi == ApiConstraint.UNKNOWN) {
              if (apiDatabase.isRelevantOwner(expressionOwner)) {
                break
              }
            } else if (minSdk.isAtLeast(specificApi)) {
              return
            } else {
              if (!(specificApi.isAtLeast(api))) {
                api = specificApi
                fqcn = cls.qualifiedName
              }
              break
            }
            cls = cls.superClass
          }
        }
      }

      val (suppressed, localMinSdk) = getSuppressed(context, api, reference, minSdk)
      if (suppressed) {
        return
      }
      minSdk = max(minSdk, localMinSdk)

      if (receiver != null || call.isMethodCall()) {
        var target: PsiClass? = null
        if (!method.isConstructor) {
          if (receiver != null) {
            val type = receiver.getExpressionType()
            if (type is PsiClassType) {
              target = type.resolve()
            }
          } else {
            target = call.getContainingUClass()?.javaPsi
          }
        }

        // Look to see if there's a possible local receiver
        if (target != null) {
          val methods = target.findMethodsBySignature(method, true)
          if (methods.size > 1) {
            for (m in methods) {
              //noinspection LintImplPsiEquals
              if (method != m) {
                val provider = m.containingClass
                if (provider != null) {
                  val methodOwner = evaluator.getQualifiedName(provider)
                  if (methodOwner != null) {
                    val methodApi = apiDatabase.getMethodVersions(methodOwner, name, desc)
                    if (methodApi == ApiConstraint.UNKNOWN || minSdk.isAtLeast(methodApi)) {
                      val interfaceRequirement =
                        if (provider.isInterface) {
                          apiDatabase.getValidCastVersions(owner, methodOwner)
                        } else {
                          ApiConstraint.UNKNOWN
                        }
                      if (
                        interfaceRequirement == ApiConstraint.UNKNOWN ||
                          minSdk.isAtLeast(interfaceRequirement)
                      ) {
                        return
                      }
                      // TODO: It would be nice to incorporate this constraint in the message
                      // somehow (e.g. "method is available via AutoCloseable interface, but that
                      // requires SDK INT >= 31").
                    }
                  }
                }
              }
            }
          }
        }

        if (isSuperCallFromOverride(evaluator, call, method)) {
          // Don't flag calling super.X() in override fun X()
          return
        }
      }

      // Builtin R8 desugaring, such as rewriting compare calls (see b/36390874)
      if (
        canBeDesugaredLater(owner) &&
          isDesugaredMethod(
            owner,
            name,
            desc,
            context.sourceSetType,
            if (context.driver.isGlobalAnalysis()) context.mainProject else context.project,
            containingClass,
          )
      ) {
        // Special case some limitations in desugaring
        handleNioDesugaringSpecialCases(owner, name, minSdk, call)
        return
      }

      var desugaring: Desugaring? = null

      // These methods are not included in the R8 backported list so handle them manually the way R8
      // seems to
      if (
        owner == "java.lang.Throwable" &&
          (name == "addSuppressed" && desc == "(Ljava.lang.Throwable;)" ||
            name == "getSuppressed" && desc == "()")
      ) {
        if (context.project.isDesugaring(Desugaring.TRY_WITH_RESOURCES)) {
          return
        } else {
          desugaring = Desugaring.TRY_WITH_RESOURCES
        }
      }

      val signature: String =
        if (fqcn == null) {
          name
        } else if (CONSTRUCTOR_NAME == name) {
          if (isKotlin(reference.lang)) {
            "$fqcn()"
          } else {
            "new $fqcn"
          }
        } else {
          "$fqcn${'#'}$name"
        }

      val nameIdentifier = call.methodIdentifier
      val location =
        if (call.isConstructorCall() && call.classReference != null) {
          context.getRangeLocation(call, 0, call.classReference!!, 0)
        } else if (nameIdentifier != null) {
          context.getLocation(nameIdentifier)
        } else {
          context.getLocation(reference)
        }
      report(
        UNSUPPORTED,
        reference,
        location,
        "Call",
        signature,
        api,
        minSdk,
        null,
        owner,
        name,
        desc,
        desugaring,
        original,
      )
    }

    /**
     * Even though various java.nio calls are backported using core library desugaring, there are
     * some important limitations documented in
     * https://developer.android.com/studio/write/java11-nio-support-table#java-nio-customizations
     *
     * This method looks for calls affected by these limitations.
     */
    private fun handleNioDesugaringSpecialCases(
      owner: String,
      name: String,
      minSdk: ApiConstraint,
      call: UCallExpression,
    ) {
      if (!owner.startsWith("java.nio.") || minSdk.isAtLeast(API_26)) {
        return
      }

      when {
        owner == "java.nio.channels.AsynchronousFileChannel" ->
          reportNioIssue("Using `AsynchronousFileChannel` is not supported".byLibDesugaring(), call)
        name == "newWatchService" && owner == "java.nio.file.FileSystem" ->
          reportNioIssue("Using a `WatchService` is not supported".byLibDesugaring(), call)
        name == "getUserPrincipalLookupService" && owner == "java.nio.file.FileSystem" ||
          owner == "java.nio.file.attribute.UserPrincipalLookupService" ||
          owner == "java.nio.file.attribute.PosixFilePermissions" ||
          (name == "getPosixFilePermissions" || name == "setPosixFilePermissions") &&
            owner == "java.nio.file.Files" ->
          reportNioIssue("POSIX file features are not supported".byLibDesugaring(), call)
        owner == "java.nio.file.spi.FileSystemProvider" &&
          (name == "createLink" || name == "createSymbolicLink") ->
          reportNioIssue("Creating links is not supported".byLibDesugaring(), call)
        name == "copy" && owner == "java.nio.file.Files" -> {
          for (argument in call.valueArguments) {
            val resolved = argument.tryResolve()
            if (resolved is PsiField) {
              val fieldName = resolved.name
              if (fieldName == "ATOMIC_MOVE") {
                reportNioIssue(
                  "file move with the `ATOMIC_MOVE` option uses file renaming. Linux does not guarantee renaming to be atomic, though it usually is, and the desugaring implementation will have the semantics of the OS."
                    .withLibDesugaring(),
                  argument,
                  warnOnly = true,
                )
              } else if (fieldName == "COPY_ATTRIBUTES") {
                reportNioIssue(
                  "file copy with the `COPY_ATTRIBUTES` option does not copy attributes, though it is usually not important to copy basic attributes"
                    .withLibDesugaring(),
                  argument,
                  warnOnly = true,
                )
              }
            }
          }
        }
        name == "open" && owner == "java.nio.channels.FileChannel" -> {
          for (argument in call.valueArguments) {
            val resolved = argument.tryResolve()
            if (resolved is PsiField) {
              val fieldName = resolved.name
              if (fieldName == "SPARSE") {
                reportNioIssue("Option `SPARSE` is ignored".byLibDesugaring(), argument)
              } else if (fieldName == "DELETE_ON_CLOSE") {
                reportNioIssue(
                  "`DELETE_ON_CLOSE` is only partially supported; the file is only closed when FileChannel is closed"
                    .withLibDesugaring(),
                  argument,
                  warnOnly = true,
                )
              }
            }
          }
        }
      }

      for (argument in call.valueArguments) {
        val type = argument.getExpressionType()
        if (type is PsiClassType && type.canonicalText == "java.nio.file.attribute.FileAttribute") {
          reportNioIssue(
            "`FileAttribute` arguments are ignored".withLibDesugaring(),
            argument,
            warnOnly = true,
          )
        }
      }
    }

    /**
     * Even though various java.nio calls are backported using core library desugaring, there are
     * some important limitations documented in
     * https://developer.android.com/studio/write/java11-nio-support-table#java-nio-customizations
     *
     * This method looks for exception catch clauses affected by these limitations.
     */
    private fun handleNioDesugaringSpecialCases(
      owner: String,
      minSdk: ApiConstraint,
      tryExpression: UTryExpression,
      typeReference: UTypeReferenceExpression,
    ) {
      if (!owner.startsWith("java.nio.") || minSdk.isAtLeast(API_26)) {
        return
      }
      val signature = owner
      if (signature == "java.nio.file.NoSuchFileException") {
        // Make sure we aren't already looking for java.io.Exception!
        for (catchClause in tryExpression.catchClauses) {
          for (typeReference in catchClause.typeReferences) {
            if (typeReference.type.canonicalText == "java.io.IOException") {
              return
            }
          }
        }

        reportNioIssue(
          "When using core library desugaring on API levels lower than 26, file operations sometimes raise " +
            "`java.io.FileNotFoundException` instead of `java.nio.file.NoSuchFileException` when a file is " +
            "not found. They are both subclasses of `java.io.IOException`, so the issue can be mitigated " +
            "by catching `java.io.IOException` instead.",
          typeReference,
        )
      }
    }

    /** Shared error message suffix for the various NIO API level <= 26 limitations */
    private fun String.byLibDesugaring(): String =
      "$this by core library desugaring on API levels lower than 26"

    /** Shared error message prefix for the various NIO API level <= 26 limitations */
    private fun String.withLibDesugaring(): String =
      "With core library desugaring on API levels lower than 26, $this"

    /** Reports an issue related to java.nio limitations ([NIO_DESUGARING]) for the given call */
    private fun reportNioIssue(message: String, call: UCallExpression, warnOnly: Boolean = false) {
      reportNioIssue(message, call, warnOnly, context.getCallLocation(call, true, false))
    }

    /** Reports an issue related to java.nio limitations ([NIO_DESUGARING]) for the given element */
    private fun reportNioIssue(
      message: String,
      element: UElement,
      warnOnly: Boolean = false,
      location: Location = context.getLocation(element),
    ) {
      val incident = Incident(NIO_DESUGARING, element, location, message)
      if (warnOnly) {
        incident.overrideSeverity(Severity.WARNING)
      }
      context.report(incident, minSdkLessThan(API_26))
    }

    private fun handleKotlinExtensionMethods(
      name: String,
      owner: String,
      call: UCallExpression,
      evaluator: JavaEvaluator,
      method: PsiMethod,
      reference: UElement,
    ) {
      // Not a method in the API database, but it could be an extension method
      // decorating one of the SDK methods.
      when {
        owner == "kotlin.collections.jdk8.CollectionsJDK8Kt" -> {
          if (name == "getOrDefault") {
            // See Collections.kt in the Kotlin stdlib collections.jdk8 package
            // It's really owner = "java.util.Map"
            checkKotlinStdlibAlias(
              call,
              evaluator,
              "java.util.Map",
              name,
              "(Ljava/lang/Object;Ljava/lang/Object;)",
              method,
              reference,
              "kotlin.collections.Map#getOrDefault",
            )
          } else if (name == "remove") {
            // See Collections.kt in the Kotlin stdlib collections.jdk8 package
            // It's really owner = "java.util.Map"
            checkKotlinStdlibAlias(
              call,
              evaluator,
              "java.util.Map",
              name,
              "(Ljava/lang/Object;Ljava/lang/Object;)",
              method,
              reference,
              "kotlin.collections.Map#remove",
            )
          }
        }
        owner == "kotlin.text.jdk8.RegexExtensionsJDK8Kt" && name == "get" -> {
          val desc =
            evaluator.getMethodDescription(method, includeName = false, includeReturn = false)
          // Turns around and calls Matcher.start(String) which requires API 26
          if (desc == "(Lkotlin.text.MatchGroupCollection;Ljava.lang.String;)") {
            checkKotlinStdlibAlias(
              call,
              evaluator,
              "java.util.regex.Matcher",
              "start",
              "(Ljava/lang/String;)",
              method,
              reference,
              "kotlin.text.MatchGroupCollection#get(String)",
            )
          }
        }
        owner == "kotlin.text.MatchNamedGroupCollection" && name == "get" -> {
          val desc =
            evaluator.getMethodDescription(method, includeName = false, includeReturn = false)
          // Turns around and calls Matcher.start(String) which requires API 26
          if (desc == "(Ljava.lang.String;)") {
            checkKotlinStdlibAlias(
              call,
              evaluator,
              "java.util.regex.Matcher",
              "start",
              "(Ljava/lang/String;)",
              method,
              reference,
              "kotlin.text.MatchNamedGroupCollection#get",
            )
          }
        }
        (owner == "kotlin.collections.CollectionsKt__MutableCollectionsKt" ||
          owner == "kotlin.collections.CollectionsKt") &&
          (name == "removeFirst" || name == "removeLast") &&
          !call.isAliased() -> {
          val replacement = getRemoveReplacementSource(name, call)
          val display = getRemoveReplacementSourceDisplay(replacement)
          val location =
            context.getCallLocation(call, includeReceiver = false, includeArguments = true)
          val incident =
            Incident(
              UNSUPPORTED,
              call,
              location,
              "This Kotlin extension function will be hidden by `java.util.SequencedCollection` starting in API 35: `$name`. " +
                "When this source code is recompiled against API level 35, it will crash on older levels. " +
                "You can avoid this by using `$display` instead.\n",
              createRemoveFirstFix(location, name, replacement, display),
            )
          context.report(incident.overrideSeverity(Severity.WARNING))
        }
      }
    }

    /** Whether this method call is calling the method by something other than its real name */
    private fun UCallExpression.isAliased(): Boolean {
      return methodIdentifier?.name != methodName
    }

    private fun checkKotlinStdlibAlias(
      call: UCallExpression,
      evaluator: JavaEvaluator,
      aliasClassName: String,
      aliasMethodName: String,
      aliasDesc: String,
      method: PsiMethod,
      reference: UElement,
      original: String?,
    ) {
      val matcherClass = evaluator.findClass(aliasClassName) ?: return
      visitCall(
        method,
        call,
        reference,
        matcherClass,
        aliasClassName,
        aliasMethodName,
        aliasDesc,
        original = original,
      )
    }

    private fun checkAnimator(context: JavaContext, call: UCallExpression) {
      val resourceParameter = call.valueArguments[1]
      val resource = ResourceReference.get(resourceParameter) ?: return
      if (resource.`package` == ANDROID_PKG) {
        return
      }

      val api = API_21
      val minSdk = getMinSdk(context) ?: return
      if (minSdk.isAtLeast(api)) {
        return
      }

      if (
        isWithinVersionCheckConditional(context, call, api) ||
          isPrecededByVersionCheckExit(context, call, api)
      ) {
        return
      }

      // See if the associated resource references propertyValuesHolder, and if so
      // suggest switching to AnimatorInflaterCompat.loadAnimator.
      val client = context.client
      val resources =
        if (context.isGlobalAnalysis()) client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
        else client.getResources(context.project, PROJECT_ONLY)
      val items = resources.getResources(ResourceNamespace.TODO(), resource.type, resource.name)
      val paths = items.asSequence().mapNotNull { it.source }.toSet()
      for (path in paths) {
        try {
          val parser = client.createXmlPullParser(path) ?: continue
          while (true) {
            val event = parser.next()
            if (event == XmlPullParser.START_TAG) {
              val name = parser.name ?: continue
              if (name == ATTR_PROPERTY_VALUES_HOLDER) {
                // It's okay if in a -v21+ folder
                path.toFile()?.parentFile?.name?.let { nae ->
                  FolderConfiguration.getConfigForFolder(nae)?.let { config ->
                    val versionQualifier = config.versionQualifier
                    if (versionQualifier != null && versionQualifier.version.isAtLeast(api)) {
                      return
                    }
                  }
                }

                context.report(
                  UNSUPPORTED,
                  call,
                  context.getLocation(call),
                  "The resource `${resource.type}.${resource.name}` includes " +
                    "the tag `$ATTR_PROPERTY_VALUES_HOLDER` which causes crashes " +
                    "on API < ${api.minString()}. Consider switching to " +
                    "`AnimatorInflaterCompat.loadAnimator` to safely load the " +
                    "animation.",
                )
                return
              }
            } else if (event == XmlPullParser.END_DOCUMENT) {
              return
            }
          }
        } catch (ignore: XmlPullParserException) {
          // Users might be editing these files in the IDE; don't flag
        } catch (ignore: IOException) {
          // Users might be editing these files in the IDE; don't flag
        }
      }
    }

    override fun visitLocalVariable(node: ULocalVariable) {
      val initializer = node.uastInitializer ?: return
      val initializerType = initializer.getExpressionType() as? PsiClassType ?: return
      val interfaceType = node.typeFromPsi

      if (interfaceType !is PsiClassType) {
        return
      }

      if (initializerType == interfaceType) {
        return
      }

      checkCast(initializer, initializerType, interfaceType, implicit = false)
    }

    override fun visitArrayAccessExpression(node: UArrayAccessExpression) {
      val method = node.resolveOperator() ?: return
      val call = node.asCall(method)
      visitCall(method, call, node)
    }

    override fun visitUnaryExpression(node: UUnaryExpression) {
      // Overloaded operators
      val method = node.resolveOperator()
      if (method != null) {
        val call = node.asCall(method)
        visitCall(method, call, node)
      }
    }

    override fun visitBinaryExpression(node: UBinaryExpression) {
      // Overloaded operators
      val method = node.resolveOperator()
      if (method != null) {
        val call = node.asCall(method)
        visitCall(method, call, node)
      }

      val operator = node.operator
      if (operator is UastBinaryOperator.AssignOperator) {
        // Plain assignment: check casts
        val rExpression = node.rightOperand
        val rhsType = rExpression.getExpressionType() as? PsiClassType ?: return

        val interfaceType = node.leftOperand.getExpressionType()
        if (interfaceType !is PsiClassType) {
          return
        }

        if (rhsType == interfaceType) {
          return
        }

        checkCast(rExpression, rhsType, interfaceType, implicit = true)
      }
    }

    private fun isAtLeast(minSdk: ApiConstraint, node: UElement, api: ApiConstraint): Boolean {
      return minSdk.isAtLeast(api) || getTargetApi(node)?.isAtLeast(api) ?: false
    }

    override fun visitTryExpression(node: UTryExpression) {
      val resourceList = node.resourceVariables

      if (
        resourceList.isNotEmpty() &&
          // (unless using desugar which supports this for all API levels)
          !context.project.isDesugaring(Desugaring.TRY_WITH_RESOURCES)
      ) {

        val api = API_19 // minSdk for try with resources
        var minSdk = getMinSdk(context) ?: return

        if (!isAtLeast(minSdk, node, api)) {
          val (suppressed, localMinSdk) = getSuppressed(context, api, node, minSdk)
          if (suppressed) {
            return
          }
          minSdk = max(minSdk, localMinSdk)

          // Create location range for the resource list
          val first = resourceList[0]
          val last = resourceList[resourceList.size - 1]
          val location = context.getRangeLocation(first, 0, last, 0)

          val message =
            "Try-with-resources requires API level ${api.minString()} (current min is %1\$s)"
          report(
            UNSUPPORTED,
            first,
            location,
            message,
            requires = api,
            min = minSdk,
            desugaring = Desugaring.TRY_WITH_RESOURCES,
          )
        }
      }

      // Check close-availability on the API which may have been introduced at a later API level
      for (resource in resourceList) {
        val classType = TypeConversionUtil.erasure(resource.type) as? PsiClassType ?: continue
        val psiClass = classType.resolve() ?: continue
        val name = "close"
        val desc = "()"
        val closeMethod =
          psiClass.findMethodsByName(name, true).firstOrNull { !it.hasParameters() } ?: continue
        val containingClass = closeMethod.containingClass
        val owner = containingClass?.qualifiedName ?: continue
        val api = apiDatabase?.getMethodVersions(owner, name, desc) ?: continue
        if (api == ApiConstraint.UNKNOWN) {
          continue
        }
        var minSdk = getMinSdk(context) ?: continue
        if (minSdk.isAtLeast(api)) {
          continue
        }
        val (suppressed, localMinSdk) = getSuppressed(context, api, node, minSdk)
        if (suppressed) {
          continue
        }

        // R8 rewrites many auto close methods now:
        if (
          canBeDesugaredLater(owner) &&
            isDesugaredMethod(
              owner,
              name,
              desc,
              context.sourceSetType,
              if (context.driver.isGlobalAnalysis()) context.mainProject else context.project,
              containingClass,
            )
        ) {
          return
        }

        minSdk = max(minSdk, localMinSdk)
        val location = context.getLocation(resource as UElement)
        val message =
          "Implicit `${classType.name}.close()` call from try-with-resources requires API level ${api.minString()} (current min is %1\$s)"
        report(
          UNSUPPORTED,
          resource,
          location,
          message,
          apiLevelFix(api, minSdk),
          classType.canonicalText,
          requires = api,
          min = minSdk,
        )
      }

      for (catchClause in node.catchClauses) {
        // Special case reflective operation exception which can be implicitly used
        // with multi-catches: see issue 153406
        val required = API_19
        var minSdk = getMinSdk(context) ?: return
        val typeReferences = catchClause.typeReferences
        if (!minSdk.isAtLeast(required) && isMultiCatchReflectiveOperationException(catchClause)) {
          // No -- see 131349148: Dalvik: java.lang.VerifyError
          val (suppressed, localMinSdk) = getSuppressed(context, API_19, typeReferences[0], minSdk)
          if (suppressed) {
            return
          }
          minSdk = max(minSdk, localMinSdk)

          val message =
            "Multi-catch with these reflection exceptions requires API level 19 (current min is %1\$s) " +
              "because they get compiled to the common but new super type `ReflectiveOperationException`. " +
              "As a workaround either create individual catch statements, or catch `Exception`."

          val location = getCatchParametersLocation(context, catchClause)
          report(
            UNSUPPORTED,
            location.source as? UElement ?: node,
            location,
            message,
            apiLevelFix(required, minSdk),
            min = minSdk,
            requires = required,
          )
          continue
        }

        for (typeReference in typeReferences) {
          checkCatchTypeElement(node, typeReference, typeReference.type)
        }
      }
    }

    private fun checkCatchTypeElement(
      statement: UTryExpression,
      typeReference: UTypeReferenceExpression,
      type: PsiType?,
    ) {
      val apiDatabase = apiDatabase ?: return
      var resolved: PsiClass? = null
      if (type is PsiClassType) {
        resolved = type.resolve()
      }
      if (resolved != null) {
        val signature = context.evaluator.getQualifiedName(resolved) ?: return
        val api = apiDatabase.getClassVersions(signature)
        if (api == ApiConstraint.UNKNOWN) {
          return
        }
        val minSdk = getMinSdk(context) ?: return
        if (minSdk.isAtLeast(api)) {
          return
        }

        val containingClass: UClass? = statement.getContainingUClass()
        if (containingClass != null) {
          val target =
            if (minSdk.isAtLeast(API_19)) {
              getTargetApi(statement)
            } else {
              // We only consider @RequiresApi annotations for filtering applicable
              // minSdkVersion here, not @TargetApi or @SdkSuppress since we need to
              // communicate outwards that this is a problem; class loading alone, not
              // just executing the code, is enough to trigger a crash.
              getTargetApi(containingClass, ::isRequiresApiAnnotation)
            }
          if (target != null && target.isAtLeast(api)) {
            return
          }
        }

        if (
          canBeDesugaredLater(signature) &&
            isDesugaredClass(
              signature,
              context.sourceSetType,
              if (context.driver.isGlobalAnalysis()) context.mainProject else context.project,
            )
        ) {
          handleNioDesugaringSpecialCases(signature, minSdk, statement, typeReference)

          return
        }

        // Don't use getSuppressed to pick up a higher minSdkVersion from SDK_INT checks here;
        // on art we're dealing with class loading verification before it runs those evaluations.
        if (isSuppressed(context, api, typeReference, minSdk)) {
          // Normally having a surrounding version check is enough, but on Dalvik
          // just loading the class, whether or not the try statement is ever
          // executed will result in a crash, so the only way to prevent the
          // crash there is to never load the class; e.g. mark the whole class
          // with @RequiresApi:
          if (!minSdk.isAtLeast(API_19)) {
            // TODO: Look for RequiresApi on the class
            val location = context.getLocation(typeReference)
            val fqcn = resolved.qualifiedName
            val apiLevel = getApiLevelString(api, context)
            val apiMessage =
              "${"Exception".usLocaleCapitalize()} requires API level $apiLevel (current min is %1\$s): `${fqcn ?: ""}`"
            val message =
              "$apiMessage, and having a surrounding/preceding version " +
                "check **does not** help since prior to API level 19, just " +
                "**loading** the class will cause a crash. Consider marking the " +
                "surrounding class with `RequiresApi(19)` to ensure that the " +
                "class is never loaded except when on API 19 or higher."
            val fix = fix().data(KEY_REQUIRES_API, api, KEY_REQUIRE_CLASS, true)
            if (context.driver.isSuppressed(context, UNSUPPORTED, typeReference as UElement)) {
              return
            }

            report(
              UNSUPPORTED,
              typeReference,
              location,
              message,
              fix,
              signature,
              requires = API_19,
              min = minSdk,
            )
            return
          } else {
            // On ART we're good.
            return
          }
        }

        val location = context.getLocation(typeReference)
        val fqcn = resolved.qualifiedName
        val fix =
          if (minSdk.isAtLeast(API_19)) {
            fix().data(KEY_REQUIRES_API, api)
          } else {
            fix().data(KEY_REQUIRES_API, api, KEY_REQUIRE_CLASS, true)
          }
        report(
          UNSUPPORTED,
          typeReference,
          location,
          "Exception",
          fqcn ?: "",
          api,
          minSdk,
          fix,
          signature,
        )
      }
    }

    private fun getTargetApi(
      scope: UElement?,
      isApiLevelAnnotation: (String) -> Boolean = ::isTargetAnnotation,
    ): ApiConstraint? {
      return getTargetApi(context.evaluator, scope, isApiLevelAnnotation)
    }

    override fun visitSwitchExpression(node: USwitchExpression) {
      val expression = node.expression
      if (expression != null) {
        val type = expression.getExpressionType()
        if (type is PsiClassType) {
          checkClassType(expression, type, "Enum for switch")
        }
      }
    }

    /**
     * Checks a Java source field reference. Returns true if the field is known regardless of
     * whether it's an invalid field or not.
     */
    private fun checkField(node: UElement, field: PsiField) {
      val apiDatabase = apiDatabase ?: return
      val name = field.name
      val containingClass = field.containingClass ?: return
      val evaluator = context.evaluator
      var owner = evaluator.getQualifiedName(containingClass) ?: return

      val isSdkInt = SDK_INT == name
      val isSdkIntFull = SDK_INT_FULL == name
      if ((isSdkInt || isSdkIntFull) && "android.os.Build.VERSION" == owner) {
        checkObsoleteSdkVersion(context, node, expectFull = isSdkIntFull)
        return
      }

      var api = apiDatabase.getFieldVersions(owner, name)
      if (api == ApiConstraint.UNKNOWN) {
        return
      }
      var minSdk = getMinSdk(context) ?: return
      if (!isAtLeast(minSdk, node, api)) {
        // Only look for compile time constants. See JLS 15.28 and JLS 13.4.9.
        val issue = if (isInlined(field, evaluator)) INLINED else UNSUPPORTED
        if (issue == UNSUPPORTED) {
          // Declaring enum constants are safe; they won't be called on older
          // platforms.
          val parent = skipParenthesizedExprUp(node.uastParent)
          if (parent is USwitchClauseExpression) {
            val conditions = parent.caseValues

            if (conditions.contains(node)) {
              return
            }
          }
        } else if (issue == INLINED && isBenignConstantUsage(evaluator, field, node, name, owner)) {
          return
        }

        if (owner == "java.lang.annotation.ElementType") {
          // TYPE_USE and TYPE_PARAMETER annotations cannot be referenced
          // on older devices, but it's typically fine to declare these
          // annotations since they're normally not loaded at runtime; they're
          // meant for static analysis.
          val parent: UDeclaration? =
            node.getParentOfType(parentClass = UDeclaration::class.java, strict = true)
          if (parent is UClass && parent.isAnnotationType) {
            return
          }
        }

        val (suppressed, localMinSdk) = getSuppressed(context, api, node, minSdk)
        if (suppressed) {
          return
        }
        minSdk = max(minSdk, localMinSdk)

        // Look to see if it's a field reference for a specific sub class
        // or interface which defined the field or constant at an earlier
        // API level.
        //
        // For example, for api 28/29 and android.app.TaskInfo,
        // A number of fields were moved up from ActivityManager.RecentTaskInfo
        // to the new class TaskInfo in Q; however, these field are almost
        // always accessed via ActivityManager#taskInfo which is still
        // a RecentTaskInfo so this code works prior to Q. If you explicitly
        // access it as a TaskInfo the class reference itself will be
        // flagged by lint. (The platform change was in
        // Change-Id: Iaf1731002196bb89319de141a05ab92a7dcb2928)
        // We can't just unconditionally exit here, since there are existing
        // API requirements on various fields in the TaskInfo subclasses,
        // so try to pick out the real type.
        val parent = node.uastParent
        if (parent is UQualifiedReferenceExpression) {
          val receiver = parent.receiver
          val specificOwner =
            receiver.getExpressionType()?.canonicalText
              ?: (receiver as? UReferenceExpression)?.getQualifiedName()
          val specificApi =
            if (specificOwner != null) apiDatabase.getFieldVersions(specificOwner, name)
            else ApiConstraint.UNKNOWN
          if (specificApi != ApiConstraint.UNKNOWN && specificOwner != null) {
            if (!specificApi.isAtLeast(api)) {
              // Make sure the error message reflects the correct (lower)
              // minSdkVersion if we have a more specific match on the field
              // type
              api = specificApi
              owner = specificOwner
            }
            if (!isAtLeast(minSdk, node, specificApi)) {
              if (isSuppressed(context, specificApi, node, minSdk)) {
                return
              }
            } else {
              return
            }
          } else {
            if (
              specificOwner == "android.app.TaskInfo" &&
                (specificApi.min() == 28 || specificApi.min() == 29)
            ) {
              return
            }
          }
        }

        if (issue == INLINED && node.getParentOfType<UAnnotation>() != null) {
          // Using static fields in annotations is safe -- for example,
          // @RequiresPermission(anddroid.permission.SOME_NEW_PERMISSION)
          return
        }

        // R8 desugaring, such as rewriting NIO field references (see b/36390874)
        if (
          canBeDesugaredLater(owner) &&
            isDesugaredField(
              owner,
              name,
              context.sourceSetType,
              if (context.driver.isGlobalAnalysis()) context.mainProject else context.project,
              containingClass,
            )
        ) {
          return
        }

        // If the reference is a qualified expression, don't just highlight the
        // field name itself; include the qualifiers too
        var locationNode = node

        // But only include expressions to the left; for example, if we're
        // trying to highlight the field "OVERLAY" in
        //     PorterDuff.Mode.OVERLAY.hashCode()
        // we should *not* include the .hashCode() suffix
        while (
          locationNode.uastParent is UQualifiedReferenceExpression &&
            (locationNode.uastParent as UQualifiedReferenceExpression).selector === locationNode
        ) {
          locationNode = locationNode.uastParent ?: node
        }

        val location = context.getLocation(locationNode)
        val fqcn = getFqcn(owner) + '#'.toString() + name
        report(issue, node, location, "Field", fqcn, api, minSdk, null, owner, name)
      }
    }
  }

  private fun getApiLevelString(requires: ApiConstraint, context: JavaContext): String {
    // For preview releases, don't show the API level as a number; show it using
    // a version code
    val level = requires.fromInclusive()
    if (requires.getSdk() == ANDROID_SDK_ID) {
      if (
        level > SdkVersionInfo.HIGHEST_KNOWN_STABLE_API &&
          level > context.project.buildSdk &&
          context.project.buildTarget?.version?.isPreview == true
      ) {
        return SdkVersionInfo.getCodeName(level) ?: requires.toString()
      } else if (level == SdkVersionInfo.CUR_DEVELOPMENT) {
        return "CUR_DEVELOPMENT/" + SdkVersionInfo.CUR_DEVELOPMENT.toString()
      }
    }

    return requires.minString()
  }

  private fun checkObsoleteSdkVersion(context: JavaContext, node: UElement, expectFull: Boolean) {
    val binary = node.getParentOfType(UBinaryExpression::class.java, true)
    if (binary != null) {
      // Compute the cumulative effects of the app's minSdkVersion, any surrounding
      // @RequiresApi annotations on classes and methods, and local "if (SDK_INT)" checks.
      var environmentConstraint =
        getMinSdk(context)
          // Only applies to SDK 0
          ?.findSdk(ANDROID_SDK_ID) ?: return

      // Note that we do NOT use the app's minSdkVersion here; the library's
      // minSdkVersion should be increased instead since it's possible that
      // this library is used elsewhere with a lower minSdkVersion than the
      // main min sdk, and deleting these calls would cause crashes in
      // that usage.
      val constraint =
        getVersionCheckConditional(binary, context.client, context.evaluator, context.project)
          ?: return
      val sdkId = constraint.getSdk()
      if (sdkId == ANDROID_SDK_ID) {
        val value = binary.rightOperand.evaluate()
        if (value is Number) {
          val constant = value.toInt()
          if (!isInfinity(constant) && expectFull != isFullSdkInt(constant)) {
            val rhs = binary.rightOperand.skipParenthesizedExprDown().sourcePsi?.text ?: ""
            val message =
              if (expectFull) {
                "The API level (`$rhs`) appears to be a full SDK int (encoding major and minor versions), so it should be compared with `SDK_INT_FULL`, not `SDK_INT`"
              } else {
                "The API level (`$rhs`) appears to be a plain SDK int, so it should be compared with `SDK_INT`, not `SDK_INT`, or you should switch the API level to a full SDK constant"
              }
            val lhs = binary.leftOperand
            val resolved = lhs.tryResolve()
            val fix =
              if (resolved is PsiField) {
                fix()
                  .name(if (expectFull) "Switch to `SDK_INT`" else "Switch to `SDK_INT_FULL`")
                  .replace()
                  .range(context.getLocation(lhs))
                  .all()
                  .with(
                    if (expectFull) "android.os.Build.VERSION.SDK_INT"
                    else "android.os.Build.VERSION.SDK_INT_FULL"
                  )
                  .shortenNames()
                  .build()
                // TODO: Consider helping switch Build.VERSION_CODE constants over to equivalent
                // VERSION_CODE_FULL constants (matching all with the same prefix before _)
              } else {
                null
              }
            context.report(WRONG_SDK_INT, binary, context.getLocation(binary), message, fix)
          }
        }

        // Merge in knowledge about SDK_INT from surrounding if-SDK_INT checks.
        val outer =
          VersionChecks.Companion.getOuterVersionCheckConstraint(context, binary)?.findSdk(sdkId)

        if (outer != null) {
          environmentConstraint = environmentConstraint and outer
        }

        // Merge in annotation knowledge
        val target = getTargetApiAnnotation(context.evaluator, binary, ::isTargetAnnotation).second
        if (target != null) {
          environmentConstraint = environmentConstraint and target
        }

        val both = constraint and environmentConstraint
        val always = both.alwaysAtLeast(environmentConstraint)
        val never = both.not().alwaysAtLeast(environmentConstraint)
        if (!(always || never)) {
          return
        }
        val sdkInt = "SDK_INT${if (expectFull) "_FULL" else ""}"
        val message =
          when {
            both.isEmpty() && (outer != null || target != null) ||
              !environmentConstraint.isOpenEnded() -> {
              val source = binary.sourcePsi?.text ?: binary.asSourceString()
              val constraintString = environmentConstraint.toString().replace("API level ", "")
              val suffix =
                if (!both.isEmpty()) {
                  " (`$sdkInt` $constraintString)"
                } else {
                  ""
                }
              "Unnecessary; `$source` is never true here$suffix"
            }
            always -> "Unnecessary; `$sdkInt` is always >= ${environmentConstraint.minString()}"
            else -> "Unnecessary; `$sdkInt` is never < ${environmentConstraint.minString()}"
          }
        context.report(
          Incident(
            OBSOLETE_SDK,
            message,
            context.getLocation(binary),
            binary,
            LintFix.create().data(KEY_CONDITIONAL, always),
          )
        )
      }
    }
  }

  override fun checkFolder(context: ResourceContext, folderName: String) {
    val folderVersion = context.folderVersion
    val minSdkVersion = context.project.minSdkVersion
    if (folderVersion > 1 && folderVersion <= minSdkVersion.featureLevel) {
      // Same comment as checkObsoleteSdkVersion: We limit this check
      // to the library's minSdkVersion, not the app minSdkVersion,
      // since encouraging to combine these resources can lead to
      // crashes
      val folderConfig = FolderConfiguration.getConfigForFolder(folderName) ?: error(context.file)
      folderConfig.versionQualifier = null
      val resourceFolderType = context.resourceFolderType ?: error(context.file)
      val newFolderName = folderConfig.getFolderName(resourceFolderType)
      val message =
        "This folder configuration (`v$folderVersion`) is unnecessary; " +
          "`minSdkVersion` is ${minSdkVersion.apiString}. " +
          "Merge all the resources in this folder " +
          "into `$newFolderName`."
      context.report(
        Incident(
          OBSOLETE_SDK,
          message,
          Location.create(context.file),
          fix()
            .data(
              KEY_FILE,
              context.file,
              KEY_FOLDER_NAME,
              newFolderName,
              KEY_REQUIRES_API,
              minSdkVersion.apiLevel,
            ),
        )
      )
    }
  }

  companion object {
    const val KEY_FILE = "file"
    const val KEY_REQUIRES_API = "requiresApi"
    const val KEY_FOLDER_NAME = "folderName"
    const val KEY_CONDITIONAL = "conditional"
    const val KEY_REQUIRE_CLASS = "requireClass"
    const val KEY_MIN_API = "minSdk"
    private const val KEY_MESSAGE = "message"
    private const val KEY_OWNER = "owner"
    private const val KEY_NAME = "name"
    private const val KEY_DESC = "desc"
    private const val KEY_DESUGAR = "desugar"
    private const val KEY_SOURCE_SET = "sourceSet"

    private const val SDK_SUPPRESS_ANNOTATION = "android.support.test.filters.SdkSuppress"
    private const val ANDROIDX_SDK_SUPPRESS_ANNOTATION = "androidx.test.filters.SdkSuppress"
    private const val ROBO_ELECTRIC_CONFIG_ANNOTATION = "org.robolectric.annotation.Config"
    private const val ATTR_PROPERTY_VALUES_HOLDER = "propertyValuesHolder"

    /**
     * Whether repeated @RequiresApi/@RequiresExtension annotation means that *all* requirements are
     * required instead of *any*. This is a parameter rather than just deleting it because we'll
     * probably bring back the ability to also specify or semantics (with a meta annotation).
     */
    const val REPEATED_API_ANNOTATION_REQUIRES_ALL = true

    private val JAVA_IMPLEMENTATION = Implementation(ApiDetector::class.java, Scope.JAVA_FILE_SCOPE)
    private val NOT_SUPPRESSED: Pair<Boolean, ApiConstraint?> =
      Pair(false, null) // return value from [getSuppressed]
    private val SUPPRESSED: Pair<Boolean, ApiConstraint?> =
      Pair(true, null) // return value from [getSuppressed]

    private val API_9: ApiConstraint.SdkApiConstraint = ApiConstraint.get(9)
    private val API_19: ApiConstraint.SdkApiConstraint = ApiConstraint.get(19)
    private val API_21: ApiConstraint.SdkApiConstraint = ApiConstraint.get(21)
    private val API_23: ApiConstraint.SdkApiConstraint = ApiConstraint.get(23)
    private val API_24: ApiConstraint.SdkApiConstraint = ApiConstraint.get(24)
    private val API_26: ApiConstraint.SdkApiConstraint = ApiConstraint.get(26)

    /** Accessing an unsupported API. */
    @JvmField
    val UNSUPPORTED =
      Issue.create(
        id = "NewApi",
        briefDescription = "Calling new methods on older versions",
        explanation =
          """
                This check scans through all the Android API calls in the application and \
                warns about any calls that are not available on **all** versions targeted by \
                this application (according to its minimum SDK attribute in the manifest).

                If you really want to use this API and don't need to support older devices \
                just set the `minSdkVersion` in your `build.gradle` or `AndroidManifest.xml` \
                files.

                If your code is **deliberately** accessing newer APIs, and you have ensured \
                (e.g. with conditional execution) that this code will only ever be called on \
                a supported platform, then you can annotate your class or method with the \
                `@TargetApi` annotation specifying the local minimum SDK to apply, such as \
                `@TargetApi(11)`, such that this check considers 11 rather than your manifest \
                file's minimum SDK as the required API level.

                If you are deliberately setting `android:` attributes in style definitions, \
                make sure you place this in a `values-v`*NN* folder in order to avoid running \
                into runtime conflicts on certain devices where manufacturers have added \
                custom attributes whose ids conflict with the new ones on later platforms.

                Similarly, you can use tools:targetApi="11" in an XML file to indicate that \
                the element will only be inflated in an adequate context.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation =
          Implementation(
            ApiDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST),
            Scope.JAVA_FILE_SCOPE,
            Scope.RESOURCE_FILE_SCOPE,
            Scope.MANIFEST_SCOPE,
          ),
      )

    /** Accessing an inlined API on older platforms. */
    @JvmField
    val INLINED =
      Issue.create(
        id = "InlinedApi",
        briefDescription = "Using inlined constants on older versions",
        explanation =
          """
                This check scans through all the Android API field references in the \
                application and flags certain constants, such as static final integers and \
                Strings, which were introduced in later versions. These will actually be \
                copied into the class files rather than being referenced, which means that \
                the value is available even when running on older devices. In some cases \
                that's fine, and in other cases it can result in a runtime crash or \
                incorrect behavior. It depends on the context, so consider the code carefully \
                and decide whether it's safe and can be suppressed or whether the code needs \
                to be guarded.

                If you really want to use this API and don't need to support older devices \
                just set the `minSdkVersion` in your `build.gradle` or `AndroidManifest.xml` \
                files.

                If your code is **deliberately** accessing newer APIs, and you have ensured \
                (e.g. with conditional execution) that this code will only ever be called on \
                a supported platform, then you can annotate your class or method with the \
                `@TargetApi` annotation specifying the local minimum SDK to apply, such as \
                `@TargetApi(11)`, such that this check considers 11 rather than your manifest \
                file's minimum SDK as the required API level.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = JAVA_IMPLEMENTATION,
      )

    /** Attribute unused on older versions. */
    @JvmField
    val UNUSED =
      Issue.create(
        id = "UnusedAttribute",
        briefDescription = "Attribute unused on older versions",
        explanation =
          """
                This check finds attributes set in XML files that were introduced in a version \
                newer than the oldest version targeted by your application (with the \
                `minSdkVersion` attribute).

                This is not an error; the application will simply ignore the attribute. \
                However, if the attribute is important to the appearance or functionality of \
                your application, you should consider finding an alternative way to achieve the \
                same result with only available attributes, and then you can optionally create \
                a copy of the layout in a layout-vNN folder which will be used on API NN or \
                higher where you can take advantage of the newer attribute.

                Note: This check does not only apply to attributes. For example, some tags can \
                be unused too, such as the new `<tag>` element in layouts introduced in API 21.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation =
          Implementation(
            ApiDetector::class.java,
            EnumSet.of(Scope.RESOURCE_FILE, Scope.RESOURCE_FOLDER),
            Scope.RESOURCE_FILE_SCOPE,
            Scope.RESOURCE_FOLDER_SCOPE,
          ),
      )

    /** Obsolete SDK_INT version check. */
    @JvmField
    val OBSOLETE_SDK =
      Issue.create(
        id = "ObsoleteSdkInt",
        briefDescription = "Obsolete SDK_INT Version Check",
        explanation =
          """
                This check flags version checks that are not necessary, because the \
                `minSdkVersion` (or surrounding known API level) is already at least as high \
                as the version checked for.

                Similarly, it also looks for resources in `-vNN` folders, such as `values-v14` \
                where the version qualifier is less than or equal to the `minSdkVersion`, \
                where the contents should be merged into the best folder.
                """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = JAVA_IMPLEMENTATION,
      )

    /** Mismatched SDK_INT and SDK_INT_FULL comparisons. */
    @JvmField
    val WRONG_SDK_INT =
      Issue.create(
        id = "WrongSdkInt",
        briefDescription = "Mismatched SDK_INT or SDK_INT_FULL",
        explanation =
          """
          The `SDK_INT` constant can be used to check what the current API level is. \
          The `SDK_INT_FULL` constant also contains this information, but it also \
          carries additional information about minor versions between major releases, \
          and cannot be compared directly with the normal API levels.

          You should typically compare `SDK_INT` with the constants in `Build.VERSION_CODES`, \
          and `SDK_INT_FULL` with the constants in `Build.VERSION_CODES_FULL`. This lint check \
          flags suspicious combinations of these comparisons.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = JAVA_IMPLEMENTATION,
      )

    /** Obsolete SDK_INT version check. */
    @JvmField
    val NIO_DESUGARING =
      Issue.create(
        id = "NioDesugaring",
        briefDescription = "Unsupported `java.nio` operations",
        explanation =
          """
          Core library desugaring handles most of the `java.nio` APIs, but \
          prior to API level 26, a handful of APIs are not fully supported.

          This is detailed in the documentation at \
          https://developer.android.com/studio/write/java11-nio-support-table#java-nio-customizations .
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = JAVA_IMPLEMENTATION,
      )

    private const val TAG_RIPPLE = "ripple"
    private const val TAG_ANIMATED_SELECTOR = "animated-selector"

    private fun isFrameLayout(
      context: XmlContext,
      tagName: String,
      defaultValue: Boolean,
    ): Boolean {
      if (tagName.indexOf('.') == -1) {
        // There are a bunch of built in tags that extend FrameLayout:
        // ScrollView, ViewAnimator, etc.
        val sdkInfo = context.client.getSdkInfo(context.project)
        return sdkInfo.isSubViewOf(FRAME_LAYOUT, tagName)
      }

      // Custom views: we're not sure
      val parser = context.client.getUastParser(context.project)
      val evaluator = parser.evaluator
      val psiClass = evaluator.findClass(tagName) ?: return defaultValue
      return evaluator.extendsClass(psiClass, FQCN_FRAME_LAYOUT, false)
    }

    private fun apiLevelFix(api: ApiConstraint, minSdk: ApiConstraint): LintFix {
      return LintFix.create().data(KEY_REQUIRES_API, api, KEY_MIN_API, minSdk)
    }

    private fun getRemoveReplacementSource(name: String?, node: UElement): String {
      return if (name == "removeFirst") {
        "removeAt(0)"
      } else {
        val receiver = (node as? UCallExpression)?.receiver?.sourcePsi?.text
        if (receiver != null) {
          "removeAt($receiver.lastIndex)"
        } else {
          "removeAt(lastIndex)"
        }
      }
    }

    private fun getRemoveReplacementSourceDisplay(source: String): String {
      val max = 50
      if (source.contains("\n") || source.length > max) {
        val start = source.indexOf('(') + 1
        val end = source.lastIndexOf('.')
        var receiver = source.substring(start, end).lines().joinToString("") { it.trim() }
        if (receiver.length > max - 10) {
          receiver = "list"
        }
        return source.substring(0, start) + receiver + source.substring(end)
      }
      return source
    }

    private fun createRemoveFirstFix(
      range: Location,
      name: String?,
      replacement: String,
      replacementDisplay: String,
    ): LintFix {
      val replaceFix =
        LintFix.create()
          // the replacement is missing ")", so manually create the display name
          .name("Replace with $replacementDisplay")
          .replace()
          .pattern("$name\\s*\\(")
          .with(replacement.removeSuffix(")"))
          .range(range)
          .reformat(replacement.contains("\n"))
          .build()
      return replaceFix
    }

    /**
     * Returns true if this attribute is in a drawable document with one of the root tags that
     * require API 21.
     */
    private fun isAlreadyWarnedDrawableFile(
      context: XmlContext,
      attribute: Attr,
      attributeApiLevel: ApiConstraint,
    ): Boolean {
      // Don't complain if it's in a drawable file where we've already
      // flagged the root drawable type as being unsupported
      if (
        context.resourceFolderType == ResourceFolderType.DRAWABLE && attributeApiLevel.min() == 21
      ) {
        var element: Element? = attribute.ownerElement
        while (element != null) {
          // Can't just look at the root document tag: in the middle of the hierarchy
          // we could have a virtual root via <aapt:attr>
          val root = element.tagName
          if (
            TAG_RIPPLE == root ||
              TAG_VECTOR == root ||
              TAG_ANIMATED_VECTOR == root ||
              TAG_ANIMATED_SELECTOR == root
          ) {
            return true
          }
          val parentNode = element.parentNode
          if (parentNode is Element) {
            element = parentNode
          } else {
            break
          }
        }
      }

      return false
    }

    /**
     * Is the given attribute a "benign" unused attribute, one we probably don't need to flag to the
     * user as not applicable on all versions? These are typically attributes which add some nice
     * platform behavior when available, but that are not critical and developers would not
     * typically need to be aware of to try to implement workarounds on older platforms.
     */
    fun isBenignUnusedAttribute(name: String): Boolean {
      return when (name) {
        ATTR_LABEL_FOR,
        ATTR_TEXT_IS_SELECTABLE,
        ATTR_FULL_BACKUP_CONTENT,
        ATTR_TEXT_ALIGNMENT,
        ATTR_ROUND_ICON,
        ATTR_IMPORTANT_FOR_AUTOFILL,
        ATTR_AUTOFILL_HINTS,
        "foregroundServiceType",
        "autofilledHighlight",
        "requestLegacyExternalStorage",
        // This attribute is only used by Android 12 and above, and is safely ignored by Android 11
        // and below. ManifestDetector reports a warning if "fullBackupContent" is also missing
        // (used for Android 11 and below).
        "dataExtractionRules",

        // The following attributes are benign because aapt2 will rewrite them
        // into the safe alternatives; e.g. paddingHorizontal gets rewritten as
        // paddingLeft and paddingRight; this is done in aapt2's
        // ResourceFileFlattener::ResourceFileFlattener
        "paddingHorizontal",
        "paddingVertical",
        "layout_marginHorizontal",
        "layout_marginVertical" -> true
        else -> false
      }
    }

    private fun checkSimpleDateFormat(
      context: JavaContext,
      call: UCallExpression,
      minSdk: ApiConstraint,
    ) {
      if (minSdk.isAtLeast(API_24)) {
        // Already OK
        return
      }

      val expressions = call.valueArguments
      if (expressions.isEmpty()) {
        return
      }
      val argument = expressions[0]
      var warned: MutableList<Char>? = null
      var checked: Char = 0.toChar()
      val constant =
        when (argument) {
          is ULiteralExpression -> argument.value
          is UInjectionHost ->
            argument.evaluateToString()
              ?: ConstantEvaluator().allowUnknowns().evaluate(argument)
              ?: return
          else -> ConstantEvaluator().allowUnknowns().evaluate(argument) ?: return
        }
      if (constant is String) {
        var isEscaped = false
        for (index in 0 until constant.length) {
          when (val c = constant[index]) {
            '\'' -> isEscaped = !isEscaped
            // Gingerbread
            'L',
            'c',
            // Nougat
            'Y',
            'X',
            'u' -> {
              if (!isEscaped && c != checked && (warned == null || !warned.contains(c))) {
                val api = if (c == 'L' || c == 'c') API_9 else API_24
                if (minSdk.isAtLeast(api)) {
                  checked = c
                } else if (
                  isWithinVersionCheckConditional(context, argument, api) ||
                    isPrecededByVersionCheckExit(context, argument, api)
                ) {
                  checked = c
                } else {
                  var end = index + 1
                  while (end < constant.length && constant[end] == c) {
                    end++
                  }

                  val location =
                    if (argument is ULiteralExpression) {
                      context.getRangeLocation(argument, index, end - index)
                    } else if (
                      argument is UInjectionHost &&
                        argument is UPolyadicExpression &&
                        argument.operator == UastBinaryOperator.PLUS &&
                        argument.operands.size == 1 &&
                        argument.operands.first() is ULiteralExpression
                    ) {
                      context.getRangeLocation(argument.operands[0], index, end - index)
                    } else {
                      context.getLocation(argument)
                    }

                  val incident =
                    Incident(
                      issue = UNSUPPORTED,
                      scope = call,
                      location = location,
                      message = "", // always formatted in accept() before reporting
                      fix = apiLevelFix(api, minSdk),
                    )
                  val map =
                    LintMap().apply {
                      put(KEY_REQUIRES_API, api)
                      put(KEY_MIN_API, minSdk)
                      put(
                        KEY_MESSAGE,
                        "The pattern character '$c' requires API level ${api.minString()} (current min is %1\$s) : \"`$constant`\"",
                      )
                    }
                  context.report(incident, map)
                  val list = warned ?: ArrayList<Char>().also { warned = it }
                  list.add(c)
                }
              }
            }
          }
        }
      }
    }

    /**
     * Returns the minimum SDK to use in the given element context, or -1 if no `tools:targetApi`
     * attribute was found.
     *
     * @param element the element to look at, including parents
     * @return the API level to use for this element, or -1
     */
    private fun getLocalMinSdk(element: Element): ApiConstraint {
      var current = element

      while (true) {
        val targetApi = current.getAttributeNS(TOOLS_URI, ATTR_TARGET_API)
        if (targetApi.isNotEmpty()) {
          ApiLevel.getMinConstraint(targetApi, ANDROID_SDK_ID)?.let {
            return it
          }
        }

        val parent = current.parentNode
        if (parent != null && parent.nodeType == Node.ELEMENT_NODE) {
          current = parent as Element
        } else {
          break
        }
      }

      return ApiConstraint.ALL
    }

    /**
     * Checks if the current project supports features added in `minGradleVersion` version of the
     * Android gradle plugin.
     *
     * @param context Current context.
     * @param minGradleVersionString Version in which support for a given feature was added, or null
     *   if it's not supported at build time.
     */
    private fun featureProvidedByGradle(
      context: XmlContext,
      minGradleVersionString: String?,
    ): Boolean {
      if (minGradleVersionString == null) {
        return false
      }

      val gradleModelVersion = context.project.gradleModelVersion
      if (gradleModelVersion != null) {
        val minVersion = AgpVersion.tryParse(minGradleVersionString)
        if (minVersion != null && gradleModelVersion.compareIgnoringQualifiers(minVersion) >= 0) {
          return true
        }
      }
      return false
    }

    /**
     * Checks whether the given instruction is a benign usage of a constant defined in a later
     * version of Android than the application's `minSdkVersion`.
     *
     * @param evaluator evaluator for annotation lookup
     * @param node the instruction to check
     * @param name the name of the constant
     * @param owner the field owner
     * @return true if the given usage is safe on older versions than the introduction level of the
     *   constant
     */
    fun isBenignConstantUsage(
      evaluator: JavaEvaluator,
      field: PsiField,
      node: UElement?,
      name: String,
      owner: String,
    ): Boolean {
      if (equivalentName(owner, "android.os.Build.VERSION_CODES")) {
        // These constants are required for compilation, not execution
        // and valid code checks it even on older platforms
        return true
      }
      if (equivalentName(owner, "android.os.Build.VERSION") && name == "SDK_INT") {
        return true
      }
      if (equivalentName(owner, "android.view.ViewGroup.LayoutParams") && name == "MATCH_PARENT") {
        return true
      }
      if (
        equivalentName(owner, "android.widget.AbsListView") &&
          (name == "CHOICE_MODE_NONE" ||
            name == "CHOICE_MODE_MULTIPLE" ||
            name == "CHOICE_MODE_SINGLE")
      ) {
        // android.widget.ListView#CHOICE_MODE_MULTIPLE and friends have API=1,
        // but in API 11 it was moved up to the parent class AbsListView.
        // Referencing AbsListView#CHOICE_MODE_MULTIPLE technically requires API 11,
        // but the constant is the same as the older version, so accept this without
        // warning.
        return true
      }

      // Gravity#START and Gravity#END are okay; these were specifically written to
      // be backwards compatible (by using the same lower bits for START as LEFT and
      // for END as RIGHT)
      if (equivalentName(owner, "android.view.Gravity") && ("START" == name || "END" == name)) {
        return true
      }

      if (
        equivalentName(owner, "android.app.PendingIntent") &&
          ("FLAG_MUTABLE" == name || "FLAG_IMMUTABLE" == name)
      ) {
        return true
      }

      if (equivalentName(owner, "android.provider.MediaStore.MediaColumns")) {
        // Various constants were moved up to the MediaStore but are correctly handled; see
        // b/154635330
        return true
      }

      if (node == null) {
        return false
      }

      // It's okay to reference the constant as a case constant (since that
      // code path won't be taken) or in a condition of an if statement
      var curr = node.uastParent
      while (curr != null) {
        if (curr is USwitchClauseExpression) {
          val caseValues = curr.caseValues
          for (condition in caseValues) {
            if (node.isUastChildOf(condition, false)) {
              return true
            }
          }
          return false
        } else if (curr is UIfExpression) {
          val condition = curr.condition
          return node.isUastChildOf(condition, false)
        } else if (curr is UMethod || curr is UClass) {
          break
        }
        curr = curr.uastParent
      }

      return false
    }

    /**
     * Returns the first (in DFS order) inheritance chain connecting the two given classes.
     *
     * @param derivedClass the derived class
     * @param baseClass the base class
     * @return The first found inheritance chain connecting the two classes, or `null` if the
     *   classes are not related by inheritance. The `baseClass` is not included in the returned
     *   inheritance chain, which will be empty if the two classes are the same.
     */
    private fun getInheritanceChain(
      derivedClass: PsiClassType,
      baseClass: PsiClassType?,
    ): List<PsiClassType>? {
      if (derivedClass == baseClass) {
        return emptyList()
      }
      val chain = getInheritanceChain(derivedClass, baseClass, HashSet(), 0)
      chain?.reverse()
      return chain
    }

    private fun getInheritanceChain(
      derivedClass: PsiClassType,
      baseClass: PsiClassType?,
      visited: HashSet<PsiType>,
      depth: Int,
    ): MutableList<PsiClassType>? {
      if (derivedClass == baseClass) {
        return ArrayList(depth)
      }

      for (type in derivedClass.superTypes) {
        if (visited.add(type) && type is PsiClassType) {
          val chain = getInheritanceChain(type, baseClass, visited, depth + 1)
          if (chain != null) {
            chain.add(derivedClass)
            return chain
          }
        }
      }
      return null
    }

    fun isSuppressed(
      context: JavaContext,
      api: ApiConstraint,
      element: UElement,
      minSdk: ApiConstraint,
    ): Boolean {
      if (minSdk.isAtLeast(api)) {
        return true
      }
      val target = getTargetApi(context.evaluator, element)
      if (target != null && target.isAtLeast(api)) {
        return true
      }

      val driver = context.driver
      return driver.isSuppressed(context, UNSUPPORTED, element) ||
        driver.isSuppressed(context, INLINED, element) ||
        isWithinVersionCheckConditional(context, element, api) ||
        isPrecededByVersionCheckExit(context, element, api)
    }

    /**
     * Like [isSuppressed] but in addition to returning whether the element is suppressed, also
     * returns the new effective minSdkVersion, if found. This makes it possible to have the error
     * messages include not just the module-wide minSdk, but any locally inferred SDK_INT
     * constraints.
     */
    fun getSuppressed(
      context: JavaContext,
      api: ApiConstraint,
      element: UElement,
      minSdk: ApiConstraint,
    ): Pair<Boolean, ApiConstraint?> {
      if (minSdk.isAtLeast(api)) {
        return SUPPRESSED
      }
      val target = getTargetApi(context.evaluator, element)
      if (target != null && target.isAtLeast(api)) {
        return SUPPRESSED
      }

      val driver = context.driver
      if (
        driver.isSuppressed(context, UNSUPPORTED, element) ||
          driver.isSuppressed(context, INLINED, element)
      ) {
        return SUPPRESSED
      }

      val min = max(minSdk, target)

      var constraint = VersionChecks.Companion.getOuterVersionCheckConstraint(context, element)
      if (constraint != null) {
        // empty: concluded this will never be any API levels (e.g. inside something like SDK_INT >
        // 5 && SDK_INT <= 5)
        constraint = if (constraint.isEmpty()) constraint else max(constraint, min)
        val suppressed = constraint.isAtLeast(api)
        // Return the new SDK_INT implied by the version checks here

        if (!suppressed && isPrecededByVersionCheckExit(context, element, api)) {
          return SUPPRESSED
        }
        val known = max(min, constraint)
        return Pair(suppressed, known)
      }

      if (isPrecededByVersionCheckExit(context, element, api)) {
        return SUPPRESSED
      }

      if (target != null) {
        return Pair(false, min)
      }

      return NOT_SUPPRESSED
    }

    @Deprecated(
      "Use getTargetApi(JavaEvaluator, ...) passing in for example context.evaluator",
      ReplaceWith("getTargetApi(evaluator,scope,isApiLevelAnnotation)"),
    )
    @JvmOverloads
    @JvmStatic
    fun getTargetApi(
      scope: UElement?,
      isApiLevelAnnotation: (String) -> Boolean = ::isTargetAnnotation,
    ): ApiConstraint? {
      return getTargetApi(null, scope, isApiLevelAnnotation)
    }

    @JvmStatic
    @JvmOverloads
    fun getTargetApi(
      evaluator: JavaEvaluator?,
      scope: UElement?,
      isApiLevelAnnotation: (String) -> Boolean = ::isTargetAnnotation,
    ): ApiConstraint? {
      var current = scope
      while (current != null) {
        if (current is UAnnotated) {
          val targetApi = getTargetApiForAnnotated(current, isApiLevelAnnotation)
          if (targetApi != null) {
            // Note that we don't combine inherited ApiConstraints the way we do for an
            // SDK_INT check. Instead, lint will warn if you have redundant inner declarations.
            // This is deliberate such that if you for example have @RequiresApi(31) on a class
            // and then you put a @RequiresExtension(S) on a particular method, we don't compute
            // a combined requirement of [@RequiresApi(31),@RequiresExtension(S)] on the method.
            return targetApi
          }
        }
        if (current is UFile && evaluator != null) {
          // Also consult any package annotations
          val pkg = evaluator.getPackage(current.javaPsi ?: current.sourcePsi)
          if (pkg != null) {
            for (psiAnnotation in pkg.annotations) {
              val annotation =
                UastFacade.convertElement(psiAnnotation, null) as? UAnnotation ?: continue
              val target = getTargetApiForAnnotation(annotation, isApiLevelAnnotation)
              if (target != null) {
                return target
              }
            }
          }

          break
        }
        current = current.uastParent
      }

      return null
    }

    @JvmStatic
    fun getApiLevel(
      context: Context,
      annotation: UAnnotation,
      qualifiedName: String,
    ): ApiConstraint.SdkApiConstraint? {
      var api =
        when (qualifiedName) {
          REQUIRES_API_ANNOTATION.oldName(),
          REQUIRES_API_ANNOTATION.newName() -> {
            val api = getLongAttribute(annotation, ATTR_VALUE, -1).toInt()
            if (api == -1) {
              // @RequiresApi has two aliasing attributes: api and value
              getLongAttribute(annotation, "api", -1).toInt()
            } else {
              api
            }
          }
          FQCN_TARGET_API -> getLongAttribute(annotation, ATTR_VALUE, -1).toInt()
          REQUIRES_EXTENSION_ANNOTATION -> {
            val sdkId = getLongAttribute(annotation, "extension", -1).toInt()
            val version = getLongAttribute(annotation, "version", -1).toInt()
            if (sdkId != -1 && version != -1) {
              val minor = getLongAttribute(annotation, "minor", 0).toInt()
              return if (minor > 0) {
                ApiConstraint.atLeast(version, minor, sdkId)
              } else {
                ApiConstraint.atLeast(version, sdkId)
              }
            } else {
              return null
            }
          }
          ROBO_ELECTRIC_CONFIG_ANNOTATION -> getLongAttribute(annotation, "minSdk", -1).toInt()
          SDK_SUPPRESS_ANNOTATION,
          ANDROIDX_SDK_SUPPRESS_ANNOTATION -> {
            val fromCodeName = getLongAttribute(annotation, "codeName", -1)
            getLongAttribute(annotation, ATTR_MIN_SDK_VERSION, fromCodeName).toInt()
          }
          else -> return null
        }

      if (api == SdkVersionInfo.CUR_DEVELOPMENT) {
        val version = context.project.buildTarget?.version
        if (version != null && version.isPreview) {
          return ApiConstraint.get(version.featureLevel, ANDROID_SDK_ID)
        }
        // Special value defined in the Android framework to indicate current development
        // version. This is different from the tools where we use current stable + 1 since
        // that's the anticipated version.
        @Suppress("KotlinConstantConditions")
        api =
          if (SdkVersionInfo.HIGHEST_KNOWN_API > SdkVersionInfo.HIGHEST_KNOWN_STABLE_API) {
            SdkVersionInfo.HIGHEST_KNOWN_API
          } else {
            SdkVersionInfo.HIGHEST_KNOWN_API + 1
          }

        // Try to match it up by codename
        val value =
          annotation.findDeclaredAttributeValue(ATTR_VALUE)
            ?: annotation.findDeclaredAttributeValue("api")
        val name = (value?.javaPsi as? PsiReferenceExpression)?.referenceName
        if (name?.length == 1) {
          api = max(api, SdkVersionInfo.getApiByBuildCode(name, true))
        }
      }
      return if (api == -1) {
        null
      } else {
        ApiLevel(api).atLeast()
      }
    }

    /**
     * Returns the API level for the given AST node if specified with an `@TargetApi` annotation.
     *
     * @param annotated the annotated element to check
     * @return the target API level, or -1 if not specified
     */
    private fun getTargetApiForAnnotated(
      annotated: UAnnotated?,
      isApiLevelAnnotation: (String) -> Boolean,
    ): ApiConstraint? {
      if (annotated == null) {
        return null
      }

      // Combine all target level annotations at the given element since we may have
      // multiple for different SDKs.

      var constraint: ApiConstraint? = null
      //noinspection AndroidLintExternalAnnotations
      for (annotation in annotated.uAnnotations) {
        val target = getTargetApiForAnnotation(annotation, isApiLevelAnnotation)
        if (target != null) {
          constraint =
            if (constraint != null) {
              // When there are multiple annotations, we deliberately treat them *all* as
              // required!
              max(target, constraint, either = !REPEATED_API_ANNOTATION_REQUIRES_ALL)
            } else {
              target
            }
        }
      }

      return constraint
    }

    fun getCatchParametersLocation(context: JavaContext, catchClause: UCatchClause): Location {
      val types = catchClause.typeReferences
      if (types.isEmpty()) {
        return Location.NONE
      }

      val first = context.getLocation(types[0])
      if (types.size < 2) {
        return first
      }

      val last = context.getLocation(types[types.size - 1])
      val file = first.file
      val start = first.start
      val end = last.end

      return if (start == null) {
        Location.create(file)
      } else Location.create(file, start, end).withSource(types[0])
    }

    fun isMultiCatchReflectiveOperationException(catchClause: UCatchClause): Boolean {
      val types = catchClause.types
      if (types.size < 2) {
        return false
      }

      for (t in types) {
        if (!isSubclassOfReflectiveOperationException(t)) {
          return false
        }
      }

      return true
    }

    private const val REFLECTIVE_OPERATION_EXCEPTION = "java.lang.ReflectiveOperationException"

    private fun isSubclassOfReflectiveOperationException(type: PsiType): Boolean {
      for (t in type.superTypes) {
        if (REFLECTIVE_OPERATION_EXCEPTION == t.canonicalText) {
          return true
        }
      }
      return false
    }
  }
}

private fun Int.isAtLeast(other: ApiConstraint): Boolean {
  if (this == -1) {
    return false
  }
  return ApiConstraint.get(this).isAtLeast(other)
}
