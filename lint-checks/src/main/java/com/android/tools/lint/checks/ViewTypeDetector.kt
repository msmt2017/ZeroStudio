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
import com.android.SdkConstants.ANDROID_VIEW_PKG
import com.android.SdkConstants.ANDROID_WEBKIT_PKG
import com.android.SdkConstants.ANDROID_WIDGET_PREFIX
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TAG
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.ID_PREFIX
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_MERGE
import com.android.SdkConstants.VIEW_TAG
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.util.PathString
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.ResourceRepositoryScope.PROJECT_ONLY
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getLanguageLevel
import com.google.common.base.Joiner
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel.JDK_1_7
import com.intellij.pom.java.LanguageLevel.JDK_1_8
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.util.TypeConversionUtil
import java.io.IOException
import java.util.EnumSet
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.w3c.dom.Attr
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Detector for finding inconsistent usage of views and casts. */
open class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

  private val idToViewTag = HashMap<String, Any>(50)

  private var fileIdMap: MutableMap<PathString, Multimap<String, String>>? = null

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.LAYOUT
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(ATTR_ID, ATTR_TAG)
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val value = attribute.value
    val id =
      when {
        value.startsWith(ID_PREFIX) -> value.substring(ID_PREFIX.length)
        value.startsWith(NEW_ID_PREFIX) -> value.substring(NEW_ID_PREFIX.length)
        // keep tags in the same map for simplicity but add prefix such that we don't
        // accidentally mix tags and id names together
        ATTR_TAG == attribute.localName -> TAG_NAME_PREFIX + value
        else -> return // usually some @android:id where we don't enforce casts
      }

    val view = run {
      var cls = attribute.ownerElement.tagName
      if (cls == VIEW_TAG) {
        cls = attribute.ownerElement.getAttribute(ATTR_CLASS)
      } else if (cls == VIEW_FRAGMENT) {
        if (ATTR_TAG != attribute.localName) {
          // For <fragment> tags we only want to store tag associations;
          // it's quite common to programmatically add/remove fragments
          // using id's, as well as also have that id on a container layout
          // view as a default, and in that case the get call might look
          // like an invalid cast.
          return
        }

        cls = attribute.ownerElement.getAttribute(ATTR_CLASS)
      } else if (attribute.ownerElement.hasAttributeNS(ANDROID_URI, ATTR_NAME)) {
        cls = attribute.ownerElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
      }
      cls = cls.replace('$', '.')
      if (cls.isEmpty()) {
        return
      }
      cls
    }

    val existing = idToViewTag[id]
    if (existing == null) {
      idToViewTag[id] = view
    } else if (existing is String) {
      val existingString = existing as String?
      if (existingString != view) {
        // Convert to list
        val list = ArrayList<String>(2)
        list.add(existing)
        list.add(view)
        idToViewTag[id] = list
      }
    } else if (existing is MutableList<*>) {
      @Suppress("UNCHECKED_CAST") val list = existing as MutableList<String>
      if (!list.contains(view)) {
        list.add(view)
      }
    }
  }

  // ---- Implements SourceCodeScanner ----

  override fun getApplicableMethodNames(): List<String> {
    return listOf(
      FIND_VIEW_BY_ID,
      REQUIRE_VIEW_BY_ID,
      FIND_FRAGMENT_BY_TAG,
      // "findFragmentById": Disabled for now. This leads to a lot
      // of false positives. See the support library demos for example.
      // What happens is that one layout tag, such as a <FrameLayout>
      // may specify an id, such as R.id.details.
      // Then, elsewhere, there is fragment code to lazily add and replace
      // fragments, and these use the id to look it up (and fragments
      // can use the id of a layout container as well as the id of a
      // previously registered fragment, which is why we get these mismatches.)
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val client = context.client
    val current = skipParenthesizedExprUp(node) ?: return
    var parent = skipParenthesizedExprUp(current.uastParent)

    val errorNode: UElement
    val castType: PsiClassType

    when (parent) {
      is UBinaryExpressionWithType -> {
        val cast = parent
        val type = cast.type as? PsiClassType ?: return
        castType = type
        errorNode = cast.skipParenthesizedExprDown()
      }
      is UExpression -> {
        if (parent is UCallExpression) {
          val c = parent
          checkMissingCast(context, node, c)
          return
        }

        if (parent is UQualifiedReferenceExpression) {
          val ref = parent
          if (ref.selector.skipParenthesizedExprDown() !== current) {
            return
          }

          parent = skipParenthesizedExprUp(parent.uastParent)
        }

        if (
          parent is UBinaryExpressionWithType ||
            // Implicit cast?
            (parent is UBinaryExpression && parent.operator == UastBinaryOperator.ASSIGN)
        ) {
          val type = (parent as UExpression).getExpressionType() as? PsiClassType ?: return
          castType = type
          errorNode = parent
        } else {
          return
        }
      }
      is UVariable -> {
        // Implicit cast?
        val variable = parent
        val type = variable.type as? PsiClassType ?: return
        castType = type
        errorNode = parent
      }
      else -> return
    }

    val castTypeClass = castType.canonicalText
    if (
      castTypeClass == CLASS_VIEW ||
        castTypeClass == "kotlin.Unit" ||
        castTypeClass == "android.app.Fragment" ||
        castTypeClass == "android.support.v4.app.Fragment" ||
        castTypeClass == "androidx.fragment.app.Fragment"
    ) {
      return
    }

    val methodName = node.methodName
    val findView = FIND_VIEW_BY_ID == methodName || REQUIRE_VIEW_BY_ID == methodName
    val findTag = !findView && FIND_FRAGMENT_BY_TAG == methodName

    val args = node.valueArguments
    if (args.size == 1) {
      val first = args[0].skipParenthesizedExprDown()
      var tag: String? = null
      var id: String? = null
      if (findTag) {
        tag = ConstantEvaluator.evaluateString(context, first, false) ?: return
        tag = TAG_NAME_PREFIX + tag
      } else {
        val resourceUrl = ResourceEvaluator.getResource(context.evaluator, first)
        if (
          resourceUrl != null && resourceUrl.type == ResourceType.ID && !resourceUrl.isFramework
        ) {
          id = resourceUrl.name
        }
      }
      if (id != null || tag != null) {
        // We can't search for tags in the resource repository incrementally
        if (id != null) {
          val resources =
            if (context.isGlobalAnalysis())
              client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
            else client.getResources(context.project, PROJECT_ONLY)
          val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.ID, id)
          if (items.isNotEmpty()) {
            val compatible = HashSet<String>()
            for (item in items) {
              val tags = getViewTags(context, item)
              if (tags != null) {
                compatible.addAll(tags)
              }
            }
            if (compatible.isNotEmpty()) {
              val layoutTypes = ArrayList(compatible)
              checkCompatible(
                context,
                castType,
                castTypeClass,
                null,
                layoutTypes,
                errorNode,
                first,
                items,
                findView,
              )
            }
          }
        } else {
          val types = idToViewTag[tag]
          if (types is String) {
            checkCompatible(
              context,
              castType,
              castTypeClass,
              types,
              null,
              errorNode,
              first,
              null,
              false,
            )
          } else if (types is List<*>) {
            @Suppress("UNCHECKED_CAST") val layoutTypes = types as List<String>
            checkCompatible(
              context,
              castType,
              castTypeClass,
              null,
              layoutTypes,
              errorNode,
              first,
              null,
              false,
            )
          }
        }
      }
    }
  }

  private fun checkMissingCast(
    context: JavaContext,
    findViewByIdCall: UCallExpression,
    surroundingCall: UCallExpression,
  ) {
    // This issue only applies in Java, not Kotlin etc - and for language level 1.8 and above
    val languageLevel = getLanguageLevel(surroundingCall, JDK_1_7)
    if (languageLevel.isLessThan(JDK_1_8)) {
      return
    }

    var selector: UElement = surroundingCall
    var parent: UQualifiedReferenceExpression =
      skipParenthesizedExprUp(surroundingCall.uastParent) as? UQualifiedReferenceExpression
        ?: return
    val parentParent: UElement = skipParenthesizedExprUp(parent.uastParent) ?: return
    if (
      parentParent is UQualifiedReferenceExpression &&
        parentParent.receiver.skipParenthesizedExprDown() === parent
    ) {
      selector = parent
      parent = parentParent
    }
    if (parent.receiver.skipParenthesizedExprDown() !== selector) {
      return
    }

    val valueArguments = surroundingCall.valueArguments
    var parameterIndex = -1
    var i = 0
    val n = valueArguments.size
    while (i < n) {
      if (findViewByIdCall == valueArguments[i].skipParenthesizedExprDown()) {
        parameterIndex = i
      }
      i++
    }
    if (parameterIndex == -1) {
      return
    }

    val resolvedMethod = surroundingCall.resolve() ?: return
    val parameters = resolvedMethod.parameterList.parameters
    if (parameterIndex >= parameters.size) {
      return
    }

    val parameterType = parameters[parameterIndex].type as? PsiClassType ?: return
    if (parameterType.resolve() !is PsiTypeParameter) return
    val erasure = TypeConversionUtil.erasure(parameterType)
    if (erasure == null || erasure.canonicalText == CLASS_VIEW) {
      return
    }

    val returnType = resolvedMethod.returnType as? PsiClassType ?: return
    if (returnType.resolve() !is PsiTypeParameter) {
      return
    }

    val callName = findViewByIdCall.methodName ?: return
    val fix =
      LintFix.create()
        .replace()
        .name("Add cast")
        .text(callName)
        .shortenNames()
        .reformat(true)
        .with("(android.view.View)$callName")
        .build()

    context.report(
      ADD_CAST,
      findViewByIdCall,
      context.getLocation(findViewByIdCall),
      "Add explicit cast here; won't compile with Java language level 1.8 without it",
      fix,
    )
  }

  protected open fun getViewTags(context: Context, item: ResourceItem): Collection<String>? {
    // Check view tag in this file.
    val source = item.source ?: return null
    val map = getIdToTagsIn(context, source) ?: return null // This is cached
    return map.get(item.name)
  }

  private fun getIdToTagsIn(context: Context, file: PathString): Multimap<String, String>? {
    if (!file.fileName.endsWith(DOT_XML)) {
      return null
    }
    val fileIdMap =
      fileIdMap
        ?: run {
          val list = HashMap<PathString, Multimap<String, String>>()
          fileIdMap = list
          list
        }
    var map: Multimap<String, String>? = fileIdMap[file]
    if (map == null) {
      map = ArrayListMultimap.create()
      fileIdMap[file] = map
      try {
        val parser = context.client.createXmlPullParser(file)
        if (parser != null) {
          addTags(parser, map)
        }
      } catch (ignore: XmlPullParserException) {
        // Users might be editing these files in the IDE; don't flag
      } catch (ignore: IOException) {
        // Users might be editing these files in the IDE; don't flag
      }
    }
    return map
  }

  private fun addTags(parser: XmlPullParser, map: Multimap<String, String>) {
    while (true) {
      val event = parser.next()
      if (event == XmlPullParser.START_TAG) {
        var id: String? = parser.getAttributeValue(ANDROID_URI, ATTR_ID)
        if (!id.isNullOrEmpty()) {
          id = ResourceUrl.parse(id)?.name
          var tag = parser.name ?: continue
          if (tag == VIEW_TAG || tag == VIEW_FRAGMENT) {
            tag = parser.getAttributeValue(null, ATTR_CLASS) ?: continue
            if (tag.isEmpty()) {
              continue
            }
          } else if (tag == VIEW_MERGE || tag == VIEW_INCLUDE) {
            continue
          }
          tag = tag.replace('$', '.')
          if (!map.containsEntry(id, tag)) {
            map.put(id, tag)
          }
        }
      } else if (event == XmlPullParser.END_DOCUMENT) {
        return
      }
    }
  }

  /** Check if the view and cast type are compatible. */
  private fun checkCompatible(
    context: JavaContext,
    castType: PsiClassType,
    castTypeClass: String,
    tag: String?,
    tags: List<String>?,
    node: UElement,
    resourceReference: UExpression,
    items: List<ResourceItem>?,
    findView: Boolean,
  ) {
    assert(tag == null || tags == null) { tag!! + tags!! } // Should only specify one or the other

    // Common case: they match: quickly check for this and fail if not
    if (castTypeClass == tag || tags != null && tags.contains(castTypeClass)) {
      return
    }

    val castClass = castType.resolve()

    var compatible = true
    if (findView) {
      if (tag != null) {
        if (!context.sdkInfo.isSubViewOf(castTypeClass, tag)) {
          compatible = false
        }
      } else if (tags != null) { // always true
        compatible = false
        for (type in tags) {
          if (type == castTypeClass || context.sdkInfo.isSubViewOf(castTypeClass, type)) {
            compatible = true
            break
          }
        }
      }
    } else {
      compatible = castClass == null // Otherwise we can't use class to check either
    }

    // Use real classes to handle checks
    if (castClass != null && !compatible) {
      if (tag != null) {
        if (isCompatible(context, castClass, tag)) {
          return
        }
      } else if (tags != null) { // always true
        for (t in tags) {
          if (isCompatible(context, castClass, t)) {
            return
          }
        }
      }
    }

    if (compatible) {
      return
    }

    // Not compatible: report error

    val displayTag = tag ?: Joiner.on("|").join(tags!!)

    var sampleLayout: String? = null
    if (items != null && (tags == null || tags.size == 1)) {
      for (item in items) {
        val t = getViewTags(context, item)
        if (t != null && t.contains(displayTag)) {
          val source = item.source
          if (source != null) {
            val parentName = source.parentFileName
            sampleLayout =
              if (item.configuration.isDefault || parentName == null) {
                source.fileName
              } else {
                parentName + "/" + source.fileName
              }
            break
          }
        }
      }
    }

    val incompatibleTag = castTypeClass.substring(castTypeClass.lastIndexOf('.') + 1)

    val type = if (findView) "layout" else "fragment"
    val verb = if (findView) "was" else "referenced"
    if (node !is UBinaryExpressionWithType) {
      val location: Location
      if (node is UVariable && node.typeReference != null) {
        location = context.getLocation(node.typeReference!!)
        location.secondary = createSecondary(context, displayTag, resourceReference, sampleLayout)
      } else {
        location = context.getLocation(node)
      }
      val message = "Unexpected implicit cast to `$incompatibleTag`: $type tag $verb `$displayTag`"
      context.report(WRONG_VIEW_CAST, node, location, message)
    } else {
      val location = context.getLocation(node)
      if (sampleLayout != null) {
        location.secondary = createSecondary(context, displayTag, resourceReference, sampleLayout)
      }
      val message = "Unexpected cast to `$incompatibleTag`: $type tag $verb `$displayTag`"
      val fix = createCastFix(node, displayTag, context)
      context.report(WRONG_VIEW_CAST, node, location, message, fix)
    }
  }

  private fun createCastFix(
    node: UBinaryExpressionWithType,
    displayTag: String,
    context: JavaContext,
  ): LintFix? {
    val typeReference = node.typeReference ?: return null
    val className =
      if (displayTag.contains('.')) displayTag.replace('$', '.')
      else findViewForTag(displayTag, context)?.qualifiedName ?: return null
    return fix()
      .replace()
      .all()
      .with(className)
      .name("Cast to $displayTag")
      .range(context.getLocation(typeReference))
      .reformat(true)
      .shortenNames()
      .build()
  }

  private fun createSecondary(
    context: JavaContext,
    tag: String,
    resourceReference: UExpression,
    sampleLayout: String?,
  ): Location {
    val secondary = context.getLocation(resourceReference)
    if (sampleLayout != null) {
      val article =
        if (
          tag.indexOf('.') == -1 &&
            tag.indexOf('|') == -1 &&
            // We don't have widgets right now which start with a silent consonant
            StringUtil.isVowel(Character.toLowerCase(tag[0]))
        )
          "an"
        else "a"
      secondary.message = "Id bound to $article `$tag` in `$sampleLayout`"
    }
    return secondary
  }

  private fun isCompatible(context: JavaContext, castClass: PsiClass, tag: String): Boolean {
    return findViewForTag(tag, context)?.isInheritor(castClass, true)
      // If can't find class, just assume it's compatible since we don't want false positives
      ?: true
  }

  private fun findViewForTag(tag: String, context: JavaContext): PsiClass? {
    return findViewForTag(tag, context.evaluator)
  }

  companion object {
    // See framework's PhoneLayoutInflater: these are the prefixes
    // that don't need fully qualified names in layouts
    private val viewPackages = arrayOf(ANDROID_WIDGET_PREFIX, ANDROID_VIEW_PKG, ANDROID_WEBKIT_PKG)

    /** Maps from a layout xml [tag] to the corresponding view class if possible */
    fun findViewForTag(tag: String, evaluator: JavaEvaluator): PsiClass? {
      var cls: PsiClass? = null
      if (tag.indexOf('.') == -1) {
        for (prefix in viewPackages) {
          cls = evaluator.findClass(prefix + tag)

          if (cls != null) {
            break
          }
        }
      } else {
        cls = evaluator.findClass(tag)
      }
      return cls
    }

    /** Mismatched view types. */
    @JvmField
    val WRONG_VIEW_CAST =
      Issue.create(
        id = "WrongViewCast",
        briefDescription = "Mismatched view type",
        explanation =
          """
                Keeps track of the view types associated with ids and if it finds a usage \
                of the id in the Java code it ensures that it is treated as the same type.""",
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation =
          Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.ALL_JAVA_FILES),
            Scope.JAVA_FILE_SCOPE,
          ),
      )

    /** Mismatched view types. */
    @JvmField
    val ADD_CAST =
      Issue.create(
        id = "FindViewByIdCast",
        briefDescription = "Add Explicit Cast",
        explanation =
          """
                In Android O, the `findViewById` signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.""",
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    const val FIND_VIEW_BY_ID = "findViewById"
    const val REQUIRE_VIEW_BY_ID = "requireViewById"
    const val FIND_FRAGMENT_BY_TAG = "findFragmentByTag"
    private const val TAG_NAME_PREFIX = ":tag:"
  }
}
