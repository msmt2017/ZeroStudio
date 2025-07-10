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

import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.ide.common.resources.LocaleManager
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.util.PathString
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.ResourceRepositoryScope.PROJECT_ONLY
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceFolderScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import java.io.IOException
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Checks for problems related to the LocaleConfig definitions in Android 13. */
class LocaleConfigDetector : Detector(), XmlScanner, ResourceFolderScanner {
  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.XML
  }

  // When processing a manifest file, validate that the locale config attribute corresponds to al
  // the locales
  override fun getApplicableAttributes(): Collection<String> = listOf("localeConfig")

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (attribute.namespaceURI != ANDROID_URI) return
    val url = ResourceUrl.parse(attribute.value) ?: return
    if (url.type != ResourceType.XML || url.isFramework || url.isTheme) return

    val client = context.client
    val resources =
      if (context.isGlobalAnalysis()) client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
      else client.getResources(context.project, PROJECT_ONLY)
    val namespace = context.project.resourceNamespace
    val configs: List<ResourceItem> = resources.getResources(namespace, url.type, url.name)
    for (config in configs) {
      val path = config.source ?: continue
      val configLanguages = getConfigLanguages(client, path) ?: continue
      if (configLanguages.isEmpty()) {
        // No locales? Unlikely config file; probably an error in parsing or format
        continue
      }
      val actualLocales = resources.getStringLocales()
      for (actualLocale in actualLocales) {
        val language = actualLocale.language ?: continue
        if (!configLanguages.contains(language)) {
          val desc = LocaleManager.getLanguageName(language)?.let { "$language ($it)" } ?: language
          val message =
            "The language `$desc` is present in this project, but not declared in the `localeConfig` resource"
          context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            message,
            createFix(context, path, language),
          )
        }
      }
    }
  }

  private fun createFix(context: XmlContext, path: PathString, language: String): LintFix? {
    val file = path.toFile() ?: return null
    val parser = context.parser
    val contents = context.client.readFile(file)
    val document = parser.parseXml(contents, file) ?: return null
    val prefix = document.lookupPrefix(ANDROID_URI) ?: ANDROID_NS_NAME
    var curr = document.documentElement?.firstChild ?: return null
    var location: Location? = null
    while (true) {
      if (curr is Element && curr.getAttributeNS(ANDROID_URI, ATTR_NAME) > language) {
        location = parser.getLocation(file, curr)
        break
      }

      curr = curr.nextSibling ?: break
    }

    val replacement = "<locale $prefix:name=\"$language\"/>"
    val fix = fix().name("Add $language to ${file.name}").replace()
    if (location == null) {
      fix
        .range(parser.getLocation(file, XmlUtils.getSubTags(document.documentElement).last()))
        .end()
        .with("\n    $replacement")
    } else {
      val start = location.start!!.offset
      var offset = start - 1
      while (offset > 0) {
        if (contents[offset] == '\n') {
          break
        }
        offset--
      }
      val indent = contents.substring(offset, start)
      fix.beginning().with(replacement + indent).range(location)
    }

    return fix.build()
  }

  private fun getConfigLanguages(client: LintClient, file: PathString?): Set<String>? {
    file ?: return null
    try {
      val parser = client.createXmlPullParser(file) ?: return null
      val languages = mutableSetOf<String>()
      while (true) {
        val event = parser.next()
        if (event == XmlPullParser.START_TAG) {
          val language: String? = parser.getAttributeValue(ANDROID_URI, ATTR_NAME)
          if (!language.isNullOrEmpty()) {
            languages.add(language.substringBefore('-'))
          }
        } else if (event == XmlPullParser.END_DOCUMENT) {
          return languages
        }
      }
    } catch (ignore: XmlPullParserException) {
      // Users might be editing these files in the IDE; don't flag
    } catch (ignore: IOException) {
      // Users might be editing these files in the IDE; don't flag
    }
    return null
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(
        LocaleConfigDetector::class.java,
        Scope.MANIFEST_AND_RESOURCE_SCOPE,
        Scope.RESOURCE_FILE_SCOPE,
      )

    /** Are all translations included in the localeConfig? */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "UnusedTranslation",
        briefDescription = "Unused Translation",
        explanation =
          """
              If an application defines a translation for a language which is not included in \
              the app's `localeConfig` file (when declared in the manifest), that language will \
              be "unused"; it will not be presented to the user. Usually this means you have \
              forgotten to include it in the locale config file.
              """,
        category = Category.MESSAGES,
        priority = 2,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        moreInfo = "https://developer.android.com/about/versions/13/features/app-languages",
      )
  }
}
