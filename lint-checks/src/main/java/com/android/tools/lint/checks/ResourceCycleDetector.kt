/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_PREFIX
import com.android.SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_FONT
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.COLOR_RESOURCE_PREFIX
import com.android.SdkConstants.DRAWABLE_PREFIX
import com.android.SdkConstants.FONT_PREFIX
import com.android.SdkConstants.ID_PREFIX
import com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.STYLE_RESOURCE_PREFIX
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DIMEN
import com.android.SdkConstants.TAG_FONT
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.TAG_STYLE
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getBaseName
import com.android.utils.XmlUtils
import com.google.common.base.Joiner
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Multimaps
import com.google.common.collect.Sets
import java.util.TreeMap
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Checks for cycles in resource definitions. */
class ResourceCycleDetector : ResourceXmlDetector() {

  /**
   * For each resource type, a map from a key (style name, layout name, color name, etc) to a value
   * (parent style, included layout, referenced color, etc). Note that we only initialize this if we
   * are in "batch mode" (not editor incremental mode) since we allow this detector to also run
   * incrementally to look for trivial chains (e.g. of length 1).
   */
  private var mReferences: MutableMap<ResourceType, Multimap<String, String>>? = null

  /**
   * If in batch analysis and cycles were found, in phase 2 this map should be initialized with
   * locations for declaration definitions of the keys and values in [.mReferences]
   */
  private var mLocations: MutableMap<ResourceType, Multimap<String, Location>>? = null

  /**
   * If in batch analysis and cycles were found, for each resource type this is a list of chains
   * (where each chain is a list of keys as described in [.mReferences])
   */
  private var mChains: MutableMap<ResourceType, MutableList<MutableList<String>>>? = null

  override fun beforeCheckRootProject(context: Context) {
    // In incremental mode, or checking all files (full lint analysis) ? If the latter,
    // we should store state and look for deeper cycles
    if (context.scope.contains(Scope.ALL_RESOURCE_FILES)) {
      mReferences = Maps.newEnumMap(ResourceType::class.java)
    }
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES ||
      folderType == ResourceFolderType.FONT ||
      folderType == ResourceFolderType.COLOR ||
      folderType == ResourceFolderType.DRAWABLE ||
      folderType == ResourceFolderType.LAYOUT
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(VIEW_INCLUDE, TAG_STYLE, TAG_COLOR, TAG_ITEM, TAG_FONT, TAG_STRING, TAG_DIMEN)
  }

  override fun getApplicableAttributes(): Collection<String> = ALL

  private fun recordReference(type: ResourceType, from: String, to: String) {
    if (to.isEmpty() || to.startsWith(ANDROID_PREFIX)) {
      return
    }

    val map = getTypeMap(type) ?: return

    val name =
      if (to[0] == '@') {
        val index = to.indexOf('/')
        if (index != -1) {
          to.substring(index + 1)
        } else {
          to
        }
      } else {
        to
      }

    map.put(from, name)
  }

  private fun getTypeMap(type: ResourceType): Multimap<String, String>? {
    val references = mReferences ?: return null

    val map: Multimap<String, String>? = references[type]
    if (map != null) {
      return map
    }

    // Multimap which preserves insert order (for predictable output order)
    val newMap: Multimap<String, String> =
      Multimaps.newListMultimap(TreeMap()) { Lists.newArrayListWithExpectedSize<String>(6) }
    references[type] = newMap

    return newMap
  }

  private fun recordLocation(context: XmlContext, node: Node, type: ResourceType, from: String) {
    // Cycles were already found; we're now in phase 2 looking up specific
    // locations
    val map = getLocationMap(type) ?: return
    val location = context.getLocation(node)
    map.put(from, location)
  }

  private fun getLocationMap(type: ResourceType): Multimap<String, Location>? {
    val locations = mLocations ?: return null

    val map: Multimap<String, Location>? = locations[type]
    if (map != null) {
      return map
    }

    // Multimap which preserves insert order (for predictable output order)
    val newMap: Multimap<String, Location> = ArrayListMultimap.create(30, 4)
    locations[type] = newMap
    return newMap
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val tagName = element.tagName
    if (tagName == TAG_ITEM) {
      if (mReferences == null) {
        // Nothing to do in incremental mode
        return
      }
      val folderType = context.resourceFolderType
      if (folderType == ResourceFolderType.VALUES) {
        // Aliases
        val typeNode = element.getAttributeNode(ATTR_TYPE)
        if (typeNode != null) {
          val typeName = typeNode.value
          val type = ResourceType.fromXmlValue(typeName)
          val nameNode = element.getAttributeNode(ATTR_NAME)
          if (type != null && nameNode != null) {
            val childNodes = element.childNodes
            var i = 0
            val n = childNodes.length
            while (i < n) {
              val child = childNodes.item(i)
              if (child.nodeType == Node.TEXT_NODE) {
                val text = child.nodeValue
                var k = 0
                val max = text.length
                while (k < max) {
                  val c = text[k]
                  if (Character.isWhitespace(c)) {
                    break
                  } else if (c == '@' && text.startsWith(type.getName(), k + 1)) {
                    val to = text.trim()
                    if (mReferences != null) {
                      val name = nameNode.value
                      if (mLocations != null) {
                        recordLocation(context, child, type, name)
                      } else {
                        recordReference(type, name, to)
                      }
                    }
                  } else {
                    break
                  }
                  k++
                }
              }
              i++
            }
          }
        }
      } else if (folderType == ResourceFolderType.COLOR) {
        val color = element.getAttributeNS(ANDROID_URI, ATTR_COLOR)
        if (color != null && color.startsWith(COLOR_RESOURCE_PREFIX)) {
          val currentColor = getBaseName(context.file.name)
          handleReference(
            context,
            element,
            ResourceType.COLOR,
            currentColor,
            color.substring(COLOR_RESOURCE_PREFIX.length),
          )
        }
      } else if (folderType == ResourceFolderType.DRAWABLE) {
        val drawable = element.getAttributeNS(ANDROID_URI, ATTR_DRAWABLE)
        if (drawable != null && drawable.startsWith(DRAWABLE_PREFIX)) {
          val currentColor = getBaseName(context.file.name)
          handleReference(
            context,
            element,
            ResourceType.DRAWABLE,
            currentColor,
            drawable.substring(DRAWABLE_PREFIX.length),
          )
        }
      }
    } else if (tagName == TAG_STYLE) {
      val nameNode = element.getAttributeNode(ATTR_NAME)
      // Look for recursive style parent declarations
      val parentNode = element.getAttributeNode(ATTR_PARENT)
      if (parentNode != null && nameNode != null) {
        val name = nameNode.value
        val parent = parentNode.value
        if (
          parent.startsWith(STYLE_RESOURCE_PREFIX) &&
            parent.startsWith(name, STYLE_RESOURCE_PREFIX.length) &&
            parent.startsWith(".", STYLE_RESOURCE_PREFIX.length + name.length)
        ) {
          if (context.isEnabled(CYCLE) && context.driver.phase == 1) {
            context.report(
              CYCLE,
              parentNode,
              context.getLocation(parentNode),
              "Potential cycle: `$name` is the implied parent of `${
                            parent.substring(STYLE_RESOURCE_PREFIX.length)}` and " +
                "this defines the opposite",
            )
          }
          // Don't record this reference; we don't want to double report this
          // as a chain, since this error is more helpful
          return
        }
        if (!parent.isEmpty() && !parent.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
          val parentName = parent.substring(parent.lastIndexOf('/') + 1)
          handleReference(context, parentNode, ResourceType.STYLE, name, parentName)

          if (parent.startsWith(PREFIX_RESOURCE_REF) && !parent.contains("style/")) {
            context.report(
              CYCLE,
              parentNode,
              context.getLocation(parentNode),
              "Invalid parent reference: expected a @style",
            )
          }
        }
      } else if (mReferences != null && nameNode != null) {
        val name = nameNode.value
        val index = name.lastIndexOf('.')
        if (index > 0) {
          val parent = name.substring(0, index)
          if (mReferences != null) {
            if (mLocations != null) {
              val node = element.getAttributeNode(ATTR_NAME)
              recordLocation(context, node, ResourceType.STYLE, name)
            } else {
              recordReference(ResourceType.STYLE, name, parent)
            }
          }
        }
      }

      if (context.isEnabled(CRASH) && context.driver.phase == 1) {
        for (item in XmlUtils.getSubTags(element)) {
          if ("android:id" == item.getAttribute(ATTR_NAME)) {
            checkCrashItem(context, item)
          }
        }
      }
    } else if (tagName == VIEW_INCLUDE) {
      val layoutNode = element.getAttributeNode(ATTR_LAYOUT)
      if (layoutNode != null) {
        val layout = layoutNode.value
        if (layout.startsWith(LAYOUT_RESOURCE_PREFIX)) {
          val currentLayout = getBaseName(context.file.name)
          handleReference(context, layoutNode, ResourceType.LAYOUT, currentLayout, layout)
        }
      }
    } else if (tagName == TAG_COLOR || tagName == TAG_STRING || tagName == TAG_DIMEN) {
      val childNodes = element.childNodes
      var i = 0
      val n = childNodes.length
      while (i < n) {
        val child = childNodes.item(i)
        if (child.nodeType == Node.TEXT_NODE) {
          val text = child.nodeValue
          var k = 0
          val max = text.length
          while (k < max) {
            val c = text[k]
            if (Character.isWhitespace(c)) {
              break
            } else if (c == '@' && text.startsWith(tagName, k + 1)) {
              val to = text.trim().substring(tagName.length + 2)
              val name = element.getAttribute(ATTR_NAME)
              val type = ResourceType.fromXmlTagName(tagName)
              if (type != null) {
                handleReference(context, child, type, name, to)
              }
            } else {
              break
            }
            k++
          }
        }
        i++
      }
    } else if (tagName == TAG_FONT) {
      val text = element.getAttributeNodeNS(ANDROID_URI, ATTR_FONT)
      if (text != null && text.value.startsWith(FONT_PREFIX)) {
        val font = text.value.trim().substring(FONT_PREFIX.length)
        val currentFont = getBaseName(context.file.name)
        handleReference(context, text, ResourceType.FONT, currentFont, font)
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    // No references? Incremental analysis in a single file only; nothing to do
    val references = this.mReferences ?: return

    val phase = context.driver.phase
    if (phase == 1) {
      // Perform DFS of each resource type and look for cycles
      for ((type, map) in references) {
        findCycles(context, type, map)
      }
    } else {
      assert(phase == 2)
      // Emit cycle report
      val chainsMap = mChains ?: return
      val locationMap = mLocations ?: return

      for ((type, chains) in chainsMap) {
        val locations = locationMap[type] ?: ArrayListMultimap.create() // Unlikely.
        for (chain in chains) {
          var location: Location? = null
          assert(!chain.isEmpty())
          var i = 0
          val n = chain.size
          while (i < n) {
            val item = chain[i]
            val itemLocations = locations.get(item)
            if (!itemLocations.isEmpty()) {
              val itemLocation = itemLocations.iterator().next()
              val next = chain[(i + 1) % chain.size]
              val label =
                ("Reference from @" +
                  type.getName() +
                  "/" +
                  item +
                  " to " +
                  type.getName() +
                  "/" +
                  next +
                  " here")
              itemLocation.message = label
              itemLocation.secondary = location
              location = itemLocation
            }
            i++
          }

          if (location == null) {
            location = Location.create(context.project.dir)
          } else {
            // Break off chain
            var curr = location.secondary
            while (curr != null) {
              val next = curr.secondary
              if (next === location) {
                curr.secondary = null
                break
              }
              curr = next
            }
          }

          val message =
            String.format(
              "%1\$s Resource definition cycle: %2\$s",
              type.displayName,
              Joiner.on(" => ").join(chain),
            )

          context.report(CYCLE, location, message)
        }
      }
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val resourceFolderType = context.resourceFolderType
    if (resourceFolderType == null || resourceFolderType == ResourceFolderType.VALUES) {
      // Null resource type means manifest, and there are no cycles there.
      // Within values there are special considerations (for example around styles)
      // and this is all handled from visitElement
      return
    }

    val value = attribute.value
    if (
      value.isEmpty() ||
        !value.startsWith(PREFIX_RESOURCE_REF) ||
        value.startsWith(NEW_ID_PREFIX) || // id's can't have cycles
        value.startsWith(ID_PREFIX)
    ) {
      return
    }

    // Optimization to avoid parsing URLs for the very common case where the referenced
    // resource is unrelated to the current folder type (e.g. a drawable reference
    // in a layout file etc)
    val types = FolderTypeRelationship.getRelatedResourceTypes(resourceFolderType)
    val primary = types[0].getName() // Guaranteed to not be the primary type, not the id
    if (!value.regionMatches(1, primary, 0, primary.length, false)) {
      return
    }

    val url = ResourceUrl.parse(value) ?: return

    // We don't need to check !url.framework here since our optimization above
    // already made sure the resource types matched *and* there was no "android:" prefix
    // before the resource type

    // Ensure that we're referring to the same resource type here; e.g. we're not complaining
    // that @layout/foo references @drawable/foo
    if (!types.contains(url.type)) {
      return
    }

    if (TOOLS_URI == attribute.namespaceURI) {
      // tools attribute references to resources aren't real resource references,
      // and sometimes are intentionally cyclic, such as tools:showIn
      return
    }

    val from = getBaseName(context.file.name)
    handleReference(context, attribute, url.type, from, url.name)
  }

  private fun handleReference(
    context: XmlContext,
    node: Node,
    type: ResourceType,
    from: String,
    to: String,
  ) {
    if (from == to) {
      // Report immediately; don't record
      if (context.isEnabled(CYCLE) && context.driver.phase == 1) {

        context.report(
          CYCLE,
          node,
          context.getLocation(node),
          "${type.displayName} `$to` should not ${
                    when (type) {
                        ResourceType.LAYOUT -> "include"
                        ResourceType.STYLE -> "extend"
                        else -> "reference"
                    }
                    } itself",
        )
      }
    } else if (mReferences != null) {
      if (mLocations != null) {
        recordLocation(context, node, type, from)
      } else {
        recordReference(type, from, to)
      }
    }
  }

  private fun findCycles(context: Context, type: ResourceType, map: Multimap<String, String>) {
    val visiting = Sets.newHashSet<String>()
    val visited = Sets.newHashSetWithExpectedSize<String>(map.size())
    val seen = Sets.newHashSetWithExpectedSize<String>(map.size())
    for (from in map.keySet()) {
      if (seen.contains(from)) {
        continue
      }
      val chain = dfs(map, from, visiting, visited)
      if (chain != null && chain.size > 2) { // size 1 chains are handled directly
        seen.addAll(chain)
        chain.reverse()
        val chains: MutableMap<ResourceType, MutableList<MutableList<String>>> =
          mChains
            ?: run {
              val newMap =
                Maps.newEnumMap<ResourceType, MutableList<MutableList<String>>>(
                  ResourceType::class.java
                )
              mChains = newMap
              mLocations = Maps.newEnumMap(ResourceType::class.java)
              context.driver.requestRepeat(this, Scope.RESOURCE_FILE_SCOPE)
              newMap
            }

        val list = chains[type]
        if (list == null) {
          chains[type] = mutableListOf(chain)
        } else {
          list.add(chain)
        }
      }
    }
  }

  private fun checkCrashItem(context: XmlContext, item: Element) {
    val childNodes = item.childNodes
    var i = 0
    val n = childNodes.length
    while (i < n) {
      val child = childNodes.item(i)
      if (child.nodeType == Node.TEXT_NODE) {
        val text = child.nodeValue

        var k = 0
        val max = text.length
        while (k < max) {
          val c = text[k]
          when {
            Character.isWhitespace(c) -> return
            text.startsWith(NEW_ID_PREFIX, k) -> {
              val name = text.trim().substring(NEW_ID_PREFIX.length)
              val message =
                ("This construct can potentially crash `aapt` during a " +
                  "build. Change `@+id/" +
                  name +
                  "` to `@id/" +
                  name +
                  "` and define " +
                  "the id explicitly using " +
                  "`<item type=\"id\" name=\"" +
                  name +
                  "\"/>` instead.")
              context.report(CRASH, item, context.getLocation(item), message)
            }
            else -> return
          }
          k++
        }
      }
      i++
    }
  }

  // ----- Cycle detection -----

  private fun dfs(
    map: Multimap<String, String>,
    from: String,
    visiting: MutableSet<String>,
    visited: MutableSet<String>,
  ): MutableList<String>? {
    visiting.add(from)
    visited.add(from)

    val targets = map.get(from)
    if (targets != null && !targets.isEmpty()) {
      for (target in targets) {
        if (visiting.contains(target)) {
          val chain = Lists.newArrayList<String>()
          chain.add(target)
          chain.add(from)
          return chain
        } else if (visited.contains(target)) {
          continue
        }
        val chain = dfs(map, target, visiting, visited)
        if (chain != null) {
          chain.add(from)
          return chain
        }
      }
    }

    visiting.remove(from)

    return null
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    /** Style parent cycles, resource alias cycles, layout include cycles, etc. */
    @JvmField
    val CYCLE =
      Issue.create(
        id = "ResourceCycle",
        briefDescription = "Cycle in resource definitions",
        explanation =
          """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions.""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Parent cycles. */
    @JvmField
    val CRASH =
      Issue.create(
        id = "AaptCrash",
        briefDescription = "Potential AAPT crash",
        explanation =
          """
                Defining a style which sets `android:id` to a dynamically generated id can \
                cause many versions of `aapt`, the resource packaging tool, to crash. \
                To work around this, declare the id explicitly with \
                `<item type="id" name="..." />` instead.""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )
  }
}
