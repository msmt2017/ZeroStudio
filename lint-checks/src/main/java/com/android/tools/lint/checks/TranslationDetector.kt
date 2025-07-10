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

import com.android.SdkConstants.ATTR_LOCALE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TRANSLATABLE
import com.android.SdkConstants.FD_RES_VALUES
import com.android.SdkConstants.TAG_RESOURCES
import com.android.SdkConstants.TAG_STRING_ARRAY
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_FALSE
import com.android.ide.common.resources.LocaleManager
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceUpdateTracerService
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.VersionQualifier
import com.android.ide.common.resources.resourceNameToFieldName
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.VALUES
import com.android.resources.ResourceType
import com.android.resources.ResourceType.AAPT
import com.android.resources.ResourceType.ARRAY
import com.android.resources.ResourceType.DRAWABLE
import com.android.resources.ResourceType.ID
import com.android.resources.ResourceType.MIPMAP
import com.android.resources.ResourceType.PLURALS
import com.android.resources.ResourceType.PUBLIC
import com.android.resources.ResourceType.STRING
import com.android.resources.ResourceType.STYLE
import com.android.resources.ResourceType.STYLEABLE
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.detector.api.BinaryResourceScanner
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceFolderScanner
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.getLocaleAndRegion
import com.android.utils.SdkUtils.fileNameToResourceName
import com.android.utils.SdkUtils.isServiceKey
import com.android.utils.XmlUtils
import com.google.common.collect.Maps
import com.google.common.collect.Sets
import java.util.EnumSet
import java.util.Locale
import java.util.SortedSet
import java.util.TreeSet
import kotlin.collections.set
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Checks for incomplete translations - e.g. keys that are only present in some locales but not all.
 *
 * TODO <ul> <li> Port to Kotlin before the following changes: <li> Remove all the batch mode
 * handling here; instead of accumulating all strings we can now limit the analysis directly to
 * references from the current strings file being analyzed -- as long as we don't duplicate the
 * work, e.g. we only do the analysis from the base folder config, except for extra-strings. </ul>
 */
class TranslationDetector : Detector(), XmlScanner, ResourceFolderScanner, BinaryResourceScanner {
  /** The names of resources, for each resource type, defined in the base folder. */
  private val baseNames: MutableMap<ResourceType, MutableSet<String>> =
    Maps.newEnumMap(ResourceType::class.java)

  /** The names of resources, for each resource type, defined in a non-base folder. */
  private val nonBaseNames: MutableMap<ResourceType, MutableSet<String>> =
    Maps.newEnumMap(ResourceType::class.java)

  /** For missing strings, a map from the string name to the set of locales where it's missing. */
  private var missingMap: MutableMap<String, Set<String>>? = null

  /** In incremental mode, a cache for the set of locales in the module. */
  private var locales: Set<String>? = null

  /** In batch mode, the in-progress view of the set of locales we've come across in pass 1. */
  private var pendingLocales: MutableSet<String>? = null

  /** In batch mode, the set of strings we've come across marked as translatable=false. */
  private var nonTranslatable: MutableSet<String>? = null

  /** In batch mode, the translations: a map from each string name to the set of locales. */
  private var translations: MutableMap<String, MutableSet<String>>? = null

  private fun ignoreFile(context: Context) =
    context.file.name.startsWith("donottranslate") ||
      ResourceUsageModel.isAnalyticsFile(context.file) ||
      !context.project.reportIssues

  override fun afterCheckRootProject(context: Context) {
    if (context.phase == 2) {
      return
    }

    processMissingTranslations(context)
    processExtraTranslations(context)
  }

  private fun processMissingTranslations(context: Context) {
    val nameToLocales = translations
    if (
      nameToLocales?.isNotEmpty() == true && context.isEnabled(MISSING) && pendingLocales != null
    ) {
      val allLocales = filterLocalesByResConfigs(context.project, pendingLocales!!)

      // TODO: Complain if we have languages that only have region specific folders
      // Check to make sure

      // Map from key name to error message to show at that location
      val localeCount = allLocales.size
      val nonTranslatable = nonTranslatable ?: emptySet()
      for (type in baseNames.keys) {
        for (key in baseNames[type] ?: return) {
          val locales = nameToLocales[key] ?: emptySet()
          if (locales.size < localeCount) {
            // Missing translations!
            if (nonTranslatable.contains(key)) {
              // ...but that's fine for non-translatable strings!
              continue
            }
            val missing = Sets.difference(allLocales, locales)
            val map =
              missingMap
                ?: run {
                  val map = HashMap<String, Set<String>>()
                  missingMap = map
                  map
                }
            map[key] = missing
          }
        }
      }

      if (missingMap != null) {
        context.driver.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE)
      }
    }
  }

  private fun processExtraTranslations(context: Context) {
    if (nonBaseNames.isNotEmpty()) {
      // See if we have any strings that aren't translated
      for (type in nonBaseNames.keys) {
        val base = baseNames[type] ?: emptySet()
        for (name in nonBaseNames[type]!!) {
          if (!base.contains(name)) {
            // Found at least one resource in the non-base folders that
            // does not have a basename: request a second pass to report these
            context.driver.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE)
            return
          }
        }
      }
    }
  }

  override fun checkFolder(context: ResourceContext, folderName: String) {
    if (
      context.driver.scope.contains(Scope.ALL_RESOURCE_FILES) &&
        context.driver.phase == 1 &&
        context.resourceFolderType == ResourceFolderType.VALUES &&
        // Only count locales from non-reporting libraries
        context.project.reportIssues
    ) {
      val language = getLanguageTagFromFolder(folderName) ?: return
      if (pendingLocales == null) {
        pendingLocales = HashSet()
      }
      pendingLocales?.add(language)
    }
  }

  override fun checkBinaryResource(context: ResourceContext) {
    val folderType = context.resourceFolderType ?: return
    val file = context.file
    if (folderType != VALUES) {
      // Record resource for the whole file
      val types = FolderTypeRelationship.getRelatedResourceTypes(folderType)
      val type = types[0]
      assert(type != ID) { folderType }
      val name = fileNameToResourceName(file.name)

      visitResource(context, type, name, name, null, null)
    }
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    if (ignoreFile(context)) {
      return
    }

    val folderType = context.resourceFolderType ?: return

    val root = document.documentElement
    val translatable: Attr? = root?.getAttributeNode(ATTR_TRANSLATABLE)
    if (translatable?.value == VALUE_FALSE) {
      if (
        context.file.parentFile?.name != FD_RES_VALUES &&
          // Ensure that we're really in a locale folder, not just some non-default
          // folder (for example, values-en is a locale folder, values-v19 is not)
          getLocaleAndRegion(context.file.parentFile.name) != null
      ) {
        reportTranslatedUntranslatable(context, null, root, translatable, true)
      }
      return
    }

    val file = context.file
    if (folderType != VALUES) {
      // Record resource for the whole file
      val types = FolderTypeRelationship.getRelatedResourceTypes(folderType)
      val type = types[0]
      assert(type != ID) { folderType }
      val name = fileNameToResourceName(file.name)

      visitResource(context, type, name, name, root, null)
    } else {
      // For value files, and drawables and colors etc also pull in resource
      // references inside the context.file
      root ?: return
      if (root.tagName != TAG_RESOURCES) {
        return
      }

      val defaultLocale = run {
        val attribute = root.getAttributeNS(TOOLS_URI, ATTR_LOCALE)
        when {
          attribute.isNotBlank() -> attribute
          else -> null
        }
      }

      if (defaultLocale != null && context.driver.phase == 1) {
        val parentFolderName = context.file.parentFile.name
        val folderLanguage = getLanguageTagFromFolder(parentFolderName)
        if (folderLanguage != null) {
          val defaultLanguage = defaultLocale.substringBefore("-")
          if (folderLanguage != defaultLanguage) {
            context.report(
              MISSING,
              context.getValueLocation(root.getAttributeNodeNS(TOOLS_URI, ATTR_LOCALE)),
              "Suspicious `tools:locale` declaration of language `$defaultLanguage`; the parent folder `$parentFolderName` implies language $folderLanguage",
            )
          }
        }
      }

      // Visit top level children within the resource file
      var child = XmlUtils.getFirstSubTag(root)
      while (child != null) {
        val type = ResourceType.fromXmlTag(child)
        if (type != null && type != ID && type != PUBLIC && type != AAPT) {
          val originalName = child.getAttribute(ATTR_NAME)
          if (originalName.isNullOrBlank()) {
            if (!child.hasAttribute(ATTR_NAME)) {
              val fix = fix().set().todo(null, ATTR_NAME).build()
              context.report(
                MISSING,
                child,
                context.getLocation(child),
                "Missing `name` attribute in `<${child.tagName}>` declaration",
                fix,
              )
            }
          } else {
            val name = resourceNameToFieldName(originalName)
            visitResource(context, type, name, originalName, child, defaultLocale)
          }
        }
        child = XmlUtils.getNextTag(child)
      }
    }
  }

  private fun visitResource(
    context: ResourceContext,
    type: ResourceType,
    name: String,
    originalName: String,
    element: Element?,
    defaultLocale: String?,
  ) {
    when (type) {
      MIPMAP,
      // Extra translation checks don't apply to some of the resource types
      // (It generally does apply to styleables, but we're avoiding reporting those
      // for now since these often extend library styles
      STYLE,
      STYLEABLE -> return
      else -> {
        val folderName = context.file.parentFile.name
        // Determine if we're in IDE mode or in batch mode
        if (context.driver.scope.contains(Scope.ALL_RESOURCE_FILES)) {
          batchVisitResource(type, folderName, context, name, element, defaultLocale)
        } else {
          incrementalVisitResource(
            context,
            type,
            name,
            originalName,
            element,
            folderName,
            defaultLocale,
          )
        }
      }
    }
  }

  // On-the-fly analysis in the IDE
  private fun incrementalVisitResource(
    context: ResourceContext,
    type: ResourceType,
    name: String,
    originalName: String,
    element: Element?,
    folderName: String,
    defaultLocale: String?,
  ) {
    // Incremental mode
    val client = context.client
    // TODO: Get rid of the non-incremental version! One complication is how to flag
    // collate results and how to avoid repeatedly reporting them. There's also
    // a difference in how batch treats non-value locales; e.g. if you have layout-ja,
    // the resource repository includes "ja" as one of the targeted languages.
    if (!context.isGlobalAnalysis()) {
      return
    }
    val resources = client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
    val namespace = context.project.resourceNamespace
    // See https://issuetracker.google.com/147213347
    var items: List<ResourceItem> = resources.getResources(namespace, type, originalName)
    if (items.isEmpty() && originalName != name) { // name contains .'s, -'s, etc
      items = resources.getResources(namespace, type, name)
    }
    if (items.isEmpty()) {
      // Dump resource trace to help investigate b/167583128
      ResourceUpdateTracerService.getInstance()?.dumpTrace(null)

      // Something is wrong with the resource repository; can't analyze here
      client.log(
        Severity.ERROR,
        null,
        "Resource repository is out-of-date:" +
          " Could not find resource $originalName of type $type in namespace $namespace",
      )
      return
    }
    val hasDefault = items.filter { isDefaultFolder(it.configuration, null) }.any()
    if (!hasDefault) {
      reportExtraResource(type, name, context, element)
    } else if (
      (type == STRING || type == PLURALS) &&
        !folderName.contains('-') &&
        element != null &&
        context is XmlContext
    ) {
      // Incrementally check for missing translations

      if (handleNonTranslatable(name, element, context, true)) {
        return
      }
      // In default folder, flag any strings missing translations
      if (locales == null) {
        locales =
          filterLocalesByResConfigs(
            context.project,
            resources
              .getStringLocales()
              .mapNotNull {
                if (it.hasLanguage()) {
                  it.language
                } else {
                  defaultLocale
                }
              }
              .toSet(),
          )
      }
      val locales = locales!!.toHashSet()

      for (item in items) {
        val qualifiers = run {
          val s = item.configuration.qualifierString
          if (defaultLocale != null && s.isEmpty()) {
            defaultLocale.substringBefore('-')
          } else {
            s.substringBefore('-')
          }
        }

        val language = getLanguageTagFromQualifiers(qualifiers) ?: continue
        locales.remove(language)
      }

      if (locales.isNotEmpty()) {
        reportMissingTranslation(name, context, element, locales)
      }
    }
  }

  // Batch analysis of resources
  private fun batchVisitResource(
    type: ResourceType,
    folderName: String,
    context: ResourceContext,
    name: String,
    element: Element?,
    defaultLocale: String?,
  ) {
    // Batch mode
    val isDefault = isDefaultFolder(context.getFolderConfiguration(), folderName)
    if (isDefault) {
      // Base folder
      if (context.phase == 1) {
        // Default folder: record the sets of names in the default folder
        val names =
          baseNames[type]
            ?: run {
              val set = mutableSetOf<String>()
              baseNames[type] = set
              set
            }
        names.add(name)
      } else if (element != null && (type == STRING || type == PLURALS) && context is XmlContext) {
        // Second pass: that means we're reporting already determined
        // missing translations
        val missingFrom = missingMap?.get(name)
        if (missingFrom != null) {
          reportMissingTranslation(name, context, element, missingFrom)
        }
      }

      if ((type == STRING || type == PLURALS) && element != null && context is XmlContext) {
        if (defaultLocale != null) {
          val language = getLanguageTagFromQualifiers(defaultLocale)
          if (language != null) {
            recordTranslation(name, language)
          }
        }

        handleNonTranslatable(name, element, context, true)
      }
    } else {
      // Non-base folder
      if (context.phase == 1) {
        val names =
          nonBaseNames[type]
            ?: run {
              val set = mutableSetOf<String>()
              nonBaseNames[type] = set
              set
            }
        names.add(name)

        if ((type == STRING || type == PLURALS) && element != null && context is XmlContext) {
          if (handleNonTranslatable(name, element, context, false)) {
            return
          }

          val language =
            getLanguageTagFromFolder(folderName)
              ?: getLanguageTagFromQualifiers(defaultLocale)
              ?: ""

          recordTranslation(name, language)
        }
      } else {
        // We report extra resources in pass 2. Even though
        // resource folders are processed alphabetically so
        // that we always see the base folder before the overlays,
        // drawables introduce a complication, e.g. "drawable-da-mdpi"
        // will be visited before "drawable-mdpi" even though the
        // latter is a "base" folder. Therefore we need to visit
        // all folders in pass 1 before we have the complete set of
        // base names.
        if (baseNames[type]?.contains(name) != true) {
          reportExtraResource(type, name, context, element)
        }
      }
    }
  }

  /**
   * Determines whether for the sake of this check the given folder is a default folder. Normally
   * this would mean that it has no resource qualifiers, but (a) we allow density qualifiers (since
   * the resource system will pick among them and (b) we allow version qualifiers since it's a
   * common practice to create version specific resources only used from themes (and indirectly
   * theme drawables) dedicates to a specific platform version, e.g. Material-theme only theme
   * resources, and we don't want false positives in this area.
   */
  private fun isDefaultFolder(configuration: FolderConfiguration?, folderName: String?): Boolean {
    val config: FolderConfiguration =
      when {
        configuration != null -> configuration
        folderName != null -> {
          if (!folderName.contains('-')) {
            return true
          }

          // Cheap underestimate (some false positives, like -vrheadset will look
          // like a version qualifier, which is why we do a more extensive test below)
          if (!folderName.contains("dpi") && !folderName.contains("-v")) {
            return false
          }

          FolderConfiguration.getConfigForFolder(folderName) ?: return false
        }
        else -> {
          assert(false)
          return true
        }
      }

    return !config.any { it !is DensityQualifier && it !is VersionQualifier }
  }

  private fun recordTranslation(name: String, language: String) {
    val translations =
      translations
        ?: run {
          translations = HashMap()
          translations!!
        }

    val languages =
      translations[name]
        ?: run {
          val set = HashSet<String>()
          translations[name] = set
          set
        }

    languages.add(language)
  }

  private fun handleNonTranslatable(
    name: String,
    element: Element,
    context: XmlContext,
    isDefaultFolder: Boolean,
  ): Boolean {
    val translatable: Attr? = element.getAttributeNode(ATTR_TRANSLATABLE)
    if (translatable != null && !translatable.value!!.toBoolean()) {
      if (
        !isDefaultFolder &&
          // Ensure that we're really in a locale folder, not just some non-default
          // folder (for example, values-en is a locale folder, values-v19 is not)
          getLocaleAndRegion(context.file.parentFile.name) != null
      ) {
        reportTranslatedUntranslatable(context, name, element, translatable, true)
      }
      recordTranslatable(context, name)
      return true
    } else if (
      (isServiceKey(name) ||
        // Older versions of the templates shipped with these not marked as
        // non-translatable; don't flag them
        name == "google_maps_key" ||
        name == "google_maps_key_instructions")
    ) {
      // Mark translatable, but don't flag it as an error do have these translatable
      //  in other folders
      recordTranslatable(context, name)
      return true
    } else if (
      !isDefaultFolder &&
        nonTranslatable?.contains(name) == true &&
        getLocaleAndRegion(context.file.parentFile.name) != null
    ) {
      reportTranslatedUntranslatable(
        context,
        name,
        element,
        element.getAttributeNode(ATTR_NAME) ?: element,
        false,
      )
    }
    return false
  }

  private fun recordTranslatable(context: XmlContext, name: String) {
    if (context.driver.scope.contains(Scope.ALL_RESOURCE_FILES)) {
      // Batch mode: record that this is a non-translatable string
      if (nonTranslatable == null) {
        nonTranslatable = mutableSetOf()
      }
      nonTranslatable!!.add(name)
    }
  }

  private fun reportTranslatedUntranslatable(
    context: XmlContext,
    name: String?,
    element: Element,
    locationNode: Node,
    translatableDefinedLocally: Boolean,
  ) {
    val language = getLanguageTagFromFolder(context.file.parentFile.name) ?: return

    // Check to make sure it's not suppressed with the older flag, EXTRA,
    // which this issue used to be reported under.
    if (context.driver.isSuppressed(context, EXTRA, locationNode)) {
      return
    }

    val languageDescription = getLanguageDescription(language)
    val message =
      when {
        name == null ->
          "This resource folder is marked as non-translatable yet is in a translated resource folder ($languageDescription)"
        translatableDefinedLocally ->
          "The resource string \"$name\" is marked as translatable=\"false\", but is translated to $languageDescription here"
        else ->
          "The resource string \"$name\" has been marked as translatable=\"false\" elsewhere (usually in the `values` folder), but is translated to $languageDescription here"
      }
    val fix =
      fix()
        .name("Remove translation")
        .replace()
        .range(context.getLocation(element))
        .with("")
        .build()
    context.report(
      TRANSLATED_UNTRANSLATABLE,
      locationNode,
      context.getLocation(locationNode),
      message,
      fix,
    )
  }

  private fun reportExtraResource(
    type: ResourceType,
    name: String,
    context: ResourceContext,
    element: Element?,
  ) {
    // Found resource in folder that isn't present in the base folder;
    // this can lead to a crash
    val parentFolder = context.file.parentFile.name
    val message =
      when (type) {
        STRING -> "\"$name\" is translated here but not found in default locale"
        DRAWABLE ->
          "The drawable \"$name\" in $parentFolder has no declaration in " +
            "the base `drawable` folder or in a `drawable-`*density*`dpi` " +
            "folder; this can lead to crashes when the drawable is queried in " +
            "a configuration that does not match this qualifier"
        else -> {
          val typeName = type.getName()
          val baseFolder = context.resourceFolderType?.getName()
          "The $typeName \"$name\" in $parentFolder has no declaration in " +
            "the base `$baseFolder` folder; this can lead to crashes " +
            "when the resource is queried in a configuration that " +
            "does not match this qualifier"
        }
      }

    if (element != null && context is XmlContext) {
      // Offer quickfix only for resource item values for now, not whole files
      // (which would require additional to LintFix infrastructure)
      val fix =
        if (context.resourceFolderType == VALUES) {
          val fixLabel =
            if (type == STRING || type == PLURALS) {
              "Remove translation"
            } else {
              "Remove resource override"
            }
          fix().name(fixLabel).replace().range(context.getLocation(element)).with("").build()
        } else {
          null
        }
      // Use the ExtraTranslation id for string related problems (historical) and
      // the new MissingDefaultResource for everything else
      val issue =
        if (type == STRING || type == ARRAY && element.tagName == TAG_STRING_ARRAY) EXTRA
        else MISSING_BASE
      val location = context.getElementLocation(element, attribute = ATTR_NAME)
      context.report(issue, element, location, message, fix)
    } else {
      // Non-XML violation: bitmaps in drawable folders
      val location = Location.create(context.file)
      context.report(MISSING_BASE, location, message)
    }
  }

  private fun reportMissingTranslation(
    name: String,
    context: XmlContext,
    element: Element,
    missingFrom: Set<String>,
  ) {
    // Found resource in folder that isn't present in the base folder;
    // this can lead to a crash
    val separator = if (missingFrom.size == 2) " or " else ", "
    val localeList = missingFrom.joinToString(separator = separator) { getLanguageDescription(it) }
    val message = "\"$name\" is not translated in $localeList"
    val locationNode = element.getAttributeNode(ATTR_NAME) ?: element
    val fix = fix().name("Mark non-translatable").set(null, ATTR_TRANSLATABLE, VALUE_FALSE).build()

    context.report(MISSING, element, context.getLocation(locationNode), message, fix)
  }

  /** Look up the language for the given folder name. */
  private fun getLanguageTagFromFolder(name: String): String? {
    val locale = getLocaleTagFromFolder(name) ?: return null
    val index = locale.indexOf('-')
    return if (index != -1) {
      locale.substring(0, index)
    } else {
      locale
    }
  }

  /** Look up the locale for the given folder name. */
  private fun getLocaleTagFromFolder(name: String): String? {
    if (name == FD_RES_VALUES) {
      return null
    }

    val configuration = FolderConfiguration.getConfigForFolder(name)
    if (configuration != null) {
      val locale = configuration.localeQualifier
      if (locale != null && !locale.hasFakeValue()) {
        return locale.tag
      }
    }

    return null
  }

  /** Look up the language for the given qualifiers. */
  private fun getLanguageTagFromQualifiers(name: String?): String? {
    name ?: return null
    val locale = getLocaleTagFromQualifiers(name) ?: return null
    val index = locale.indexOf('-')
    return if (index != -1) {
      locale.substring(0, index)
    } else {
      locale
    }
  }

  /** Look up the locale for the given qualifiers. */
  private fun getLocaleTagFromQualifiers(name: String): String? {
    assert(!name.startsWith(FD_RES_VALUES))
    if (name.isEmpty()) {
      return null
    }

    val configuration = FolderConfiguration.getConfigForQualifierString(name)
    if (configuration != null) {
      val locale = configuration.localeQualifier
      if (locale != null && !locale.hasFakeValue()) {
        return locale.tag
      }
    }

    return null
  }

  private fun filterLocalesByResConfigs(project: Project, locales: Set<String>): Set<String> {
    val configLanguages = getResConfigLanguages(project) ?: return locales
    return locales.intersect(configLanguages)
  }

  private fun getResConfigLanguages(project: Project): List<String>? {
    val variant = project.buildVariant ?: return null
    val resourceConfigurations = variant.resourceConfigurations
    if (resourceConfigurations.isEmpty()) {
      return null
    }
    return resourceConfigurations
      .filter { resConfig ->
        // Look for languages; these are of length 2. (ResConfigs
        // can also refer to densities, etc.)
        resConfig.length == 2
      }
      .sorted()
      .toList()
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(
        TranslationDetector::class.java,
        EnumSet.of(
          Scope.ALL_RESOURCE_FILES,
          Scope.RESOURCE_FILE,
          Scope.RESOURCE_FOLDER,
          Scope.BINARY_RESOURCE_FILE,
        ),
        Scope.RESOURCE_FILE_SCOPE,
      )

    /** Are all translations complete? */
    @JvmField
    val MISSING =
      Issue.create(
        id = "MissingTranslation",
        briefDescription = "Incomplete translation",
        explanation =
          """
                If an application has more than one locale, then all the strings declared \
                in one language should also be translated in all other languages.

                If the string should **not** be translated, you can add the attribute \
                `translatable="false"` on the `<string>` element, or you can define all \
                your non-translatable strings in a resource file called \
                `donottranslate.xml`. Or, you can ignore the issue with a \
                `tools:ignore="MissingTranslation"` attribute.

                You can tell lint (and other tools) which language is the default language \
                in your `res/values/` folder by specifying `tools:locale="languageCode"` \
                for the root `<resources>` element in your resource file. \
                (The `tools` prefix refers to the namespace declaration \
                `http://schemas.android.com/tools`.)""",
        category = Category.MESSAGES,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** Are there extra translations that are "unused" (appear only in specific languages) ? */
    @JvmField
    val EXTRA =
      Issue.create(
        id = "ExtraTranslation",
        briefDescription = "Extra translation",
        explanation =
          """
                If a string appears in a specific language translation file, but there is \
                no corresponding string in the default locale, then this string is probably \
                unused. (It's technically possible that your application is only intended \
                to run in a specific locale, but it's still a good idea to provide a fallback.)

                Note that these strings can lead to crashes if the string is looked up on \
                any locale not providing a translation, so it's important to clean them up.""",
        category = Category.MESSAGES,
        priority = 6,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Are there extra resources that are "unused" (appear only in non-default folders) ? */
    @JvmField
    val MISSING_BASE =
      Issue.create(
        id = "MissingDefaultResource",
        briefDescription = "Missing Default",
        explanation =
          """
                If a resource is only defined in folders with qualifiers like `-land` or \
                `-en`, and there is no default declaration in the base folder (`layout` or \
                `values` etc), then the app will crash if that resource is accessed on a \
                device where the device is in a configuration missing the given qualifier.

                As a special case, drawables do not have to be specified in the base folder; \
                if there is a match in a density folder (such as `drawable-mdpi`) that image \
                will be used and scaled. Note however that if you  only specify a drawable in \
                a folder like `drawable-en-hdpi`, the app will crash in non-English locales.

                There may be scenarios where you have a resource, such as a `-fr` drawable, \
                which is only referenced from some other resource with the same qualifiers \
                (such as a `-fr` style), which itself has safe fallbacks. However, this still \
                makes it possible for somebody to accidentally reference the drawable and \
                crash, so it is safer to create a default fallback in the base folder. \
                Alternatively, you can suppress the issue by adding \
                `tools:ignore="MissingDefaultResource"` on the element.

                (This scenario frequently happens with string translations, where you might \
                delete code and the corresponding resources, but forget to delete a \
                translation. There is a dedicated issue id for that scenario, with the id \
                `ExtraTranslation`.)""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Are there extra translations that are "unused" (appear only in specific languages) ? */
    @JvmField
    val TRANSLATED_UNTRANSLATABLE =
      Issue.create(
        id = "Untranslatable",
        briefDescription = "Translated Untranslatable",
        explanation =
          """
                Strings can be marked with `translatable=false` to indicate that they are not \
                intended to be translated, but are present in the resource file for other \
                purposes (for example for non-display strings that should vary by some other \
                configuration qualifier such as screen size or API level).

                There are cases where translators accidentally translate these strings anyway, \
                and lint will flag these occurrences with this lint check.""",
        category = Category.MESSAGES,
        priority = 6,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    @JvmStatic
    fun getLanguageDescription(locale: String): String {
      val index = locale.indexOf('-')
      var regionCode: String? = null
      var languageCode = locale
      if (index != -1) {
        regionCode = locale.substring(index + 1).uppercase(Locale.US)
        languageCode = locale.substring(0, index).lowercase(Locale.US)
      }

      var languageName = LocaleManager.getLanguageName(languageCode)
      return if (languageName != null) {
        if (regionCode != null) {
          val regionName = LocaleManager.getRegionName(regionCode)
          if (regionName != null) {
            languageName = "$languageName: $regionName"
          }
        }

        String.format("\"%1\$s\" (%2\$s)", locale, languageName)
      } else {
        '"'.toString() + locale + '"'.toString()
      }
    }
  }
}

/** Returns the sorted list of locales used in the resources. */
fun ResourceRepository.getStringLocales(): SortedSet<LocaleQualifier> {
  // As an optimization we just look for values since that's typically where
  // the languages are defined -- not on layouts, menus, etc -- especially if there
  // are no translations for it.
  val locales = TreeSet<LocaleQualifier>()

  val visitor =
    object : ResourceVisitor {
      override fun visit(item: ResourceItem): ResourceVisitor.VisitResult {
        val locale = item.configuration.localeQualifier
        if (locale != null) {
          locales.add(locale)
        }
        return ResourceVisitor.VisitResult.CONTINUE
      }

      override fun shouldVisitResourceType(resourceType: ResourceType): Boolean {
        return resourceType == STRING || resourceType == PLURALS
      }
    }

  for (repository in leafResourceRepositories) {
    repository.accept { visitor.visit(it) }
  }

  return locales
}
