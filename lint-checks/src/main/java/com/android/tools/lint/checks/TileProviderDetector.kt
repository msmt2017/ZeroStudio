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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.SdkConstants.ATTR_RESOURCE
import com.android.SdkConstants.DRAWABLE_PREFIX
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.BinaryResourceScanner
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import java.io.IOException
import java.util.EnumSet
import javax.imageio.ImageIO
import org.w3c.dom.Element
import org.w3c.dom.Node

class TileProviderDetector : WearDetector(), XmlScanner, BinaryResourceScanner {
  companion object Issues {

    const val MIN_PREVIEW_SIZE = 384

    val IMPLEMENTATION =
      Implementation(
        TileProviderDetector::class.java,
        EnumSet.of(Scope.BINARY_RESOURCE_FILE, Scope.MANIFEST),
      )

    @JvmField
    val TILE_PROVIDER_PERMISSIONS =
      Issue.create(
        id = "TileProviderPermissions",
        briefDescription = "TileProvider does not set permission",
        explanation =
          """
                TileProviders should require the `com.google.android.wearable.permission.BIND_TILE_PROVIDER` \
                permission to prevent arbitrary apps from binding to it.
            """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = Implementation(TileProviderDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
      )

    @JvmField
    val SQUARE_AND_ROUND_TILE_PREVIEWS =
      Issue.create(
          id = "SquareAndRoundTilePreviews",
          briefDescription = "TileProvider does not have round and square previews",
          explanation =
            """
                Tile projects should specify preview resources for different screen shapes. \
                The preview resource is specified in the manifest under tile service. \
                And you have to make sure they have resources for different screen shapes.
            """,
          category = Category.ICONS,
          priority = 6,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
          androidSpecific = true,
        )
        .addMoreInfo(
          "https://developer.android.com/design/ui/wear/guides/surfaces/tiles#tile-previews"
        )

    @JvmField
    val TILE_PREVIEW_IMAGE_FORMAT =
      Issue.create(
          id = "TilePreviewImageFormat",
          briefDescription = "Tile preview is not compliant with standards",
          explanation =
            """
                Tile projects should specify preview resources with aspect ratio 1:1 and at least ${MIN_PREVIEW_SIZE}px by ${MIN_PREVIEW_SIZE}px in size.
            """,
          category = Category.ICONS,
          priority = 6,
          severity = Severity.ERROR,
          implementation = IMPLEMENTATION,
          androidSpecific = true,
        )
        .addMoreInfo(
          "https://developer.android.com/design/ui/wear/guides/surfaces/tiles#tile-previews"
        )

    const val BIND_TILE_PROVIDER_PERMISSION =
      "com.google.android.wearable.permission.BIND_TILE_PROVIDER"
    const val TILES_PREVIEW_ATTR_NAME = "androidx.wear.tiles.PREVIEW"
    const val BIND_TILE_PROVIDER_ACTION = "androidx.wear.tiles.action.BIND_TILE_PROVIDER"

    class TagIterator(element: Element, val tag: String) : Iterator<Element> {
      private var subElement: Element? = getFirstSubTagByName(element, tag)

      override fun hasNext(): Boolean = subElement != null

      override fun next(): Element {
        val tmp = subElement
        subElement = getNextTagByName(subElement, this.tag)
        return tmp!!
      }
    }

    private fun Element.subElementsByTag(tag: String): Iterator<Element> {
      return TagIterator(this, tag)
    }
  }

  data class IconInfo(
    val issueScope: Node,
    val issueLocation: Location,
    var foundRoundPreview: Boolean = false,
    var foundSquarePreview: Boolean = false,
    var wrongAspectRatio: Boolean = false,
    var smallImageSize: Boolean = false,
  )

  private val foundIcons: MutableMap<String, IconInfo> = mutableMapOf()

  override fun beforeCheckRootProject(context: Context) {
    super.beforeCheckRootProject(context)
    foundIcons.clear()
  }

  override fun afterCheckRootProject(context: Context) {
    for ((_, metadata) in this.foundIcons) {
      // Only report if one of them is missing. If both are missing, this will already be flagged as
      // a missing resource.
      if (metadata.foundRoundPreview xor metadata.foundSquarePreview) {
        context.report(
          Incident(
            SQUARE_AND_ROUND_TILE_PREVIEWS,
            metadata.issueScope,
            metadata.issueLocation,
            "Tiles need a preview asset in both drawable-round and drawable",
          )
        )
      }
      if (metadata.wrongAspectRatio) {
        context.report(
          Incident(
            TILE_PREVIEW_IMAGE_FORMAT,
            metadata.issueScope,
            metadata.issueLocation,
            "Tile previews should have 1:1 aspect ratio",
          )
        )
      }
      if (metadata.smallImageSize) {
        context.report(
          Incident(
            TILE_PREVIEW_IMAGE_FORMAT,
            metadata.issueScope,
            metadata.issueLocation,
            "Tile previews should be at least ${MIN_PREVIEW_SIZE}px by ${MIN_PREVIEW_SIZE}px",
          )
        )
      }
    }
  }

  override fun getApplicableElements() = listOf(TAG_SERVICE)

  override fun visitElement(context: XmlContext, element: Element) {
    for (intentFilter in element.subElementsByTag(TAG_INTENT_FILTER)) {
      for (action in intentFilter.subElementsByTag(TAG_ACTION)) {
        if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == BIND_TILE_PROVIDER_ACTION) {
          checkTileProvider(context, element)
          checkResourcesWithIcons(context, element)
        }
      }
    }
  }

  override fun appliesTo(folderType: ResourceFolderType) =
    isWearProject && folderType == ResourceFolderType.DRAWABLE

  private fun getImageDimensions(context: Context): Pair<Int, Int> {
    val readers = ImageIO.getImageReadersBySuffix(context.file.extension)
    while (readers.hasNext()) {
      val reader = readers.next()
      try {
        reader.input = ImageIO.createImageInputStream(context.file)
        val width = reader.getWidth(reader.minIndex)
        val height = reader.getHeight(reader.minIndex)
        return Pair(width, height)
      } catch (e: IOException) {
        context.log(e, "Error reading file: " + context.file.name)
      } finally {
        reader.dispose()
      }
    }
    return Pair(0, 0)
  }

  override fun checkBinaryResource(context: ResourceContext) {
    val iconName = context.file.nameWithoutExtension
    // Only check icons that are identified in the manifest as tile previews
    val metadata = foundIcons[iconName] ?: return

    // Check aspect ratio and size of tile previews
    val size = getImageDimensions(context)
    if (size.first != size.second) {
      metadata.wrongAspectRatio = true
    }
    if (size.first < MIN_PREVIEW_SIZE || size.second < MIN_PREVIEW_SIZE) {
      metadata.smallImageSize = true
    }

    val dirName = context.file.parentFile.name
    if (dirName.startsWith("drawable")) {
      if (dirName.contains("round")) {
        metadata.foundRoundPreview = true
      } else {
        metadata.foundSquarePreview = true
      }
    }
  }

  private fun checkResourcesWithIcons(context: XmlContext, service: Element) {
    var foundMetaData: Element? = null
    for (metaData in service.subElementsByTag(TAG_META_DATA)) {
      if (metaData.getAttributeNS(ANDROID_URI, ATTR_NAME) == TILES_PREVIEW_ATTR_NAME) {
        foundMetaData = metaData
        val iconAttr = foundMetaData.getAttributeNodeNS(ANDROID_URI, ATTR_RESOURCE) ?: continue
        val iconUrl = iconAttr.value
        if (!iconUrl.startsWith(DRAWABLE_PREFIX)) continue
        val iconName = iconUrl.substring(DRAWABLE_PREFIX.length)
        this.foundIcons[iconName] =
          IconInfo(issueScope = foundMetaData, issueLocation = context.getValueLocation(iconAttr))
      }
    }
    if (foundMetaData == null) {
      context.report(
        Incident(
          SQUARE_AND_ROUND_TILE_PREVIEWS,
          service,
          context.getLocation(service),
          "Tiles need preview assets",
        )
      )
    }
  }

  private fun checkTileProvider(context: XmlContext, service: Element) {
    val permission = service.getAttributeNS(ANDROID_URI, ATTR_PERMISSION)
    if (permission != BIND_TILE_PROVIDER_PERMISSION) {
      val fix =
        fix()
          .set()
          .attribute(ATTR_PERMISSION)
          .value(BIND_TILE_PROVIDER_PERMISSION)
          .android()
          .name(
            if (permission.isEmpty()) "Add BIND_TILE_PROVIDER permission"
            else "Change permission to BIND_TILE_PROVIDER"
          )
          .build()
      context.report(
        Incident(
          TILE_PROVIDER_PERMISSIONS,
          service,
          context.getNameLocation(service),
          "TileProvider does not specify BIND_TILE_PROVIDER permission",
          fix,
        )
      )
    }
  }
}
