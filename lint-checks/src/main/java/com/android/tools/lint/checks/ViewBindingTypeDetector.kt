/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_VIEW_BINDING_TYPE
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.TAG_LAYOUT
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VIEW_TAG
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.checks.ViewTypeDetector.Companion.findViewForTag
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.ResourceRepositoryScope.PROJECT_ONLY
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.formatList
import com.android.tools.lint.detector.api.stripIdPrefix
import com.android.utils.SdkUtils.fileNameToResourceName
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser

/** Detect issues related to misusing the tools:viewBindingType attribute. */
class ViewBindingTypeDetector : LayoutDetector(), XmlScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(ViewBindingTypeDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "ViewBindingType",
        briefDescription = "`tools:viewBindingType` issues",
        explanation =
          "All issues related to using the View Binding `tools:viewBindingType` attribute.",
        category = Category.CORRECTNESS,
        priority = 1,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableAttributes(): Collection<String> = listOf(ATTR_VIEW_BINDING_TYPE)

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (attribute.namespaceURI != TOOLS_URI) {
      return
    }
    val isDataBindingLayout = context.document.documentElement.tagName == TAG_LAYOUT
    if (isDataBindingLayout) {
      context.report(
        Incident(
          ISSUE,
          "`tools:viewBindingType` is not applicable in data binding layouts.",
          context.getLocation(attribute),
        )
      )
    } else {
      val element = attribute.ownerElement
      val tagName = element.tagName
      if (tagName == TAG_INCLUDE) {
        context.report(
          Incident(
            ISSUE,
            "`tools:viewBindingType` is not applicable on `<$tagName>` tags.",
            context.getLocation(attribute),
          )
        )
      } else {
        val typeTag = attribute.value
        val evaluator = context.evaluator
        val psiClass = findViewForTag(typeTag, evaluator)
        if (psiClass == null || !evaluator.extendsClass(psiClass, CLASS_VIEW)) {
          context.report(
            Incident(
              ISSUE,
              "`tools:viewBindingType` (`$typeTag`) must refer to a class that inherits from `$CLASS_VIEW`",
              context.getLocation(attribute),
            )
          )
        } else {
          // If here, the attribute is locally valid and defined in a valid location
          val idAttribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_ID)
          if (idAttribute == null) {
            context.report(
              Incident(
                ISSUE,
                "`tools:viewBindingType` should be defined on a tag that also defines an `android:id`. Otherwise, its value won't have any effect.",
                context.getLocation(attribute),
              )
            )
          } else {
            // Make sure this type definition is valid
            val tagView = element.toTagOrClass()
            val typeClass = findViewForTag(typeTag, evaluator)?.qualifiedName
            val tagClass = findViewForTag(tagView, evaluator)
            if (
              typeClass != null && tagClass != null && !evaluator.extendsClass(tagClass, typeClass)
            ) {
              context.report(
                Incident(
                  ISSUE,
                  "`tools:viewBindingType` (`$typeTag`) is not compatible (i.e. a match or superclass) with its tag (`$tagView`).",
                  context.getLocation(attribute),
                )
              )
            } else {
              // Make sure the binding type is consistent for this id across variations of this
              // layout
              checkConsistentAcrossLayouts(context, idAttribute, evaluator, element)
            }
          }
        }
      }
    }
  }

  private fun checkConsistentAcrossLayouts(
    context: XmlContext,
    idAttribute: Attr,
    evaluator: JavaEvaluator,
    element: Element,
  ) {
    val client = context.client
    val resources =
      if (context.isGlobalAnalysis()) client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
      else client.getResources(context.project, PROJECT_ONLY)
    val resourceUrl = ResourceUrl.parse(idAttribute.value)
    if (resourceUrl != null && resourceUrl.type == ResourceType.ID && !resourceUrl.isFramework) {
      val id = resourceUrl.name
      val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.ID, id)
      if (items.size > 1) {
        val layout = context.toLayoutName()
        val bindingTypes = mutableSetOf<String>()
        for (item in items) {
          val fileName = item.source?.fileName ?: continue
          if (fileNameToResourceName(fileName) == layout) {
            getViewBindingTypesForId(context, item)?.let { bindingTypes.addAll(it) }
          }
        }
        if (bindingTypes.size > 1) {
          val views =
            bindingTypes.map { findViewForTag(it, evaluator)?.qualifiedName ?: it }.toSortedSet()
          val location = context.getLocation(element)
          attachLocations(context, location, id, layout)
          context.report(
            Incident(
              ISSUE,
              "`tools:viewBindingType` is not defined consistently, with the following types resolved across layouts: ${views.joinToString { "`$it`" }}",
              location,
            )
          )
        }
      }
    }
  }

  private fun attachLocations(context: XmlContext, location: Location, id: String, layout: String) {
    if (Scope.checkSingleFile(context.driver.scope)) {
      // No need to do this work in the editor when these wouldn't be shown anyway
      return
    }

    // Compute linked locations to make it easier to find the conflicts
    val locations = mutableListOf<Location>()
    layoutToBindingIdPairs?.forEach { (path: PathString, u: Multimap<String, String>) ->
      if (fileNameToResourceName(path.fileName) == layout) {
        val types = mutableSetOf<String>()
        for ((k, v) in u.entries()) {
          if (id == k) {
            types.add(v)
          }
        }

        if (types.isNotEmpty()) {
          val file = path.toFile()
          //noinspection FileComparisons
          if (file != null && file != context.file) {
            val l = Location.create(file)
            val list = formatList(types.sorted())
            l.setMessage("Using `$ATTR_VIEW_BINDING_TYPE` $list here", false)
            locations.add(l)
          }
        }
      }
    }
    var prev = location
    for (l in locations.sortedBy { it.file }) {
      prev.secondary = l
      prev = l
    }
  }

  /** Get the XML layout name (e.g. "activity_main") from the current context. */
  private fun Context.toLayoutName() = fileNameToResourceName(file.name)

  /**
   * Assuming this element represents a view tag, return either the tag itself or the value of the
   * "class" attribute if this is a <view> tag.
   *
   * In other words, "<EditText>" and "<view class='EditText'>" both return "EditText" here.
   */
  private fun Element.toTagOrClass(): String {
    return if (tagName == VIEW_TAG) {
      getAttribute(ATTR_CLASS).takeIf { it.isNotEmpty() } ?: CLASS_VIEW
    } else {
      tagName
    }
  }

  /** Like [Element.toTagOrClass] for an [XmlPullParser] for the current element */
  private fun XmlPullParser.toTagOrClass(): String {
    val tag = name
    return if (tag == VIEW_TAG) getAttributeValue(null, ATTR_CLASS) ?: CLASS_VIEW else tag
  }

  private val Context.evaluator
    get() = client.getUastParser(project).evaluator

  /**
   * Given a resource [item], returns the set of binding types found for the given id resource item
   * (including implicit binding items, e.g. the corresponding tag)
   */
  private fun getViewBindingTypesForId(context: Context, item: ResourceItem): Collection<String>? {
    val source = item.source ?: return null
    val map = getViewBindingTypesForId(context, source) ?: return null
    return map.get(item.name)
  }

  // Cache for getViewBindingTypesForId
  private var layoutToBindingIdPairs: MutableMap<PathString, Multimap<String, String>>? = null

  private fun getViewBindingTypesForId(
    context: Context,
    file: PathString,
  ): Multimap<String, String>? {
    if (!file.fileName.endsWith(DOT_XML)) {
      return null
    }
    val cache =
      layoutToBindingIdPairs
        ?: mutableMapOf<PathString, Multimap<String, String>>().also {
          this.layoutToBindingIdPairs = it
        }
    var map: Multimap<String, String>? = cache[file]
    if (map == null) {
      map = ArrayListMultimap.create()
      cache[file] = map
      try {
        val parser = context.client.createXmlPullParser(file)
        if (parser != null) {
          addViewBindingTypesForId(parser, map)
        }
      } catch (ignore: Exception) {
        // Parsing or I/O errors -- users might be editing these files in the IDE so don't flag
      }
    }
    return map
  }

  private fun addViewBindingTypesForId(parser: XmlPullParser, map: Multimap<String, String>) {
    while (true) {
      val event = parser.next()
      if (event == XmlPullParser.START_TAG) {
        var id = parser.getAttributeValue(ANDROID_URI, ATTR_ID) ?: continue
        val binding =
          parser.getAttributeValue(TOOLS_URI, ATTR_VIEW_BINDING_TYPE) ?: parser.toTagOrClass()
        if (id.isNotEmpty() && binding.isNotEmpty()) {
          @Suppress("DEPRECATION")
          id = stripIdPrefix(id)
          if (!map.containsEntry(id, binding)) {
            map.put(id, binding)
          }
        }
      } else if (event == XmlPullParser.END_DOCUMENT) {
        return
      }
    }
  }
}
