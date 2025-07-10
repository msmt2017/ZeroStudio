/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("Lint")

package com.android.tools.lint.checks

import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.pickLibraryVariableName
import com.android.ide.common.repository.pickVersionVariableName
import com.android.tools.lint.client.api.LintTomlDocument
import com.android.tools.lint.client.api.LintTomlLiteralValue
import com.android.tools.lint.client.api.LintTomlMapValue
import com.android.tools.lint.client.api.LintTomlValue
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import java.util.TreeSet
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UPolyadicExpression

// Various TOML-related utilities used by GradleDetector, but placed in their own
// compilation unit since GradleDetector is getting massive.

/** The libraries node in a Gradle versions catalog file */
const val VC_LIBRARIES = "libraries"
/** The plugins node in a Gradle versions catalog file */
const val VC_PLUGINS = "plugins"
/** The versions node in a Gradle versions catalog file */
const val VC_VERSIONS = "versions"

/**
 * Given a key-value pair in the `\[libraries]` block in a Gradle versions catalog, returns the
 * corresponding group:artifact:version string, along with a reference to the `\[versions]`
 * declaration value node. Or null, if this is not a gradle library declaration or if its coordinate
 * cannot be resolved.
 */
fun getLibraryFromTomlEntry(
  versions: LintTomlMapValue?,
  library: LintTomlValue,
): Pair<String, LintTomlValue>? {
  if (library is LintTomlLiteralValue) {
    val coordinate = library.getActualValue()?.toString()?.trim() ?: return null
    return Pair(coordinate, library)
  }
  if (library !is LintTomlMapValue) return null
  val versionNode = getVersion(library, versions) ?: return null
  val version = versionNode.getActualValue()?.toString()?.trim() ?: return null

  val module = library["module"]
  val artifact =
    if (module is LintTomlLiteralValue) {
      module.getActualValue()?.toString()?.trim() ?: return null
    } else {
      val group = library["group"]?.getActualValue()?.toString()?.trim() ?: return null
      val name = library["name"]?.getActualValue()?.toString()?.trim() ?: return null
      "$group:$name"
    }
  return Pair("$artifact:$version", versionNode)
}

fun getPluginFromTomlEntry(
  versions: LintTomlMapValue?,
  library: LintTomlValue,
): Pair<String, LintTomlValue>? {
  if (library is LintTomlLiteralValue) {
    val coordinate = library.getActualValue()?.toString()?.trim() ?: return null
    return Pair(coordinate, library)
  }
  if (library !is LintTomlMapValue) return null
  val versionNode = getVersion(library, versions) ?: return null
  val version = versionNode.getActualValue()?.toString()?.trim() ?: return null

  val id = library["id"]
  if (id is LintTomlLiteralValue) {
    val artifact = id.getActualValue()?.toString()?.trim() ?: return null
    return Pair("$artifact:$version", versionNode)
  }
  return null
}

private fun getVersion(library: LintTomlMapValue, versionsMap: LintTomlMapValue?): LintTomlValue? {
  val version = library["version"] ?: return null
  when (version) {
    is LintTomlLiteralValue -> return version
    is LintTomlMapValue -> {
      val ref = version["ref"]
      val richVersion =
        if (ref != null) {
          versionsMap ?: return null
          val versionVariable = ref.getActualValue()?.toString()?.trim() ?: return null
          val referenced = versionsMap[versionVariable] ?: return null
          if (referenced !is LintTomlMapValue) {
            return referenced
          }
          referenced
        } else {
          version
        }
      // Rich version; could be specifying require, strictly, prefer, reject, rejectAll.
      return richVersion.first()
    }
    else -> return null
  }
}

private fun CharSequence.findNextLineStart(start: Int): Int {
  val lineEnd = indexOf('\n', start)
  if (lineEnd != -1) {
    return lineEnd + 1
  }
  return -1
}

fun createMoveToTomlFix(
  context: GradleContext,
  librariesMap: LintTomlValue,
  dependency: Dependency,
  valueCookie: Any,
  versionVar: String?,
): Pair<String?, LintFix>? {
  if (librariesMap !is LintTomlMapValue) return null
  val document = librariesMap.getDocument()
  val versionsMap = document.getValue(VC_VERSIONS) as? LintTomlMapValue

  var artifactLibrary: LintTomlValue? = null
  var artifactVersionNode: LintTomlValue? = null
  var artifactVersion: Version? = null

  val version = dependency.version
  val richVersionIdentifier =
    version?.let {
      val identifier = it.toIdentifier()
      if (identifier.isNullOrBlank()) {
        return null // return null only if version is invalid
      } else identifier
    }
  for ((key, library) in librariesMap.getMappedValues()) {
    val (coordinate, versionNode) = getLibraryFromTomlEntry(versionsMap, library) ?: continue
    val c = Dependency.parse(coordinate)
    if (c.group == null || c.version == null) continue
    if (c.group == dependency.group && c.name == dependency.name) {
      // We already have this dependency in the TOML file!
      if (c.version == version) {
        // It even matches by version! Just switch the dependency over to it!
        val fix =
          createSwitchToLibraryFix(document, library, key, context, valueCookie, safe = true)
            ?: return null
        return "Use the existing version catalog reference (`${fix.replacement}`) instead" to fix
      } else if (
        artifactLibrary == null ||
          artifactVersion?.let { v -> c.version?.lowerBound?.let { it > v } } == true
      ) {
        // There could be multiple declaration of this library (for different versions); pick the
        // highest one
        artifactLibrary = library
        artifactVersionNode = versionNode
        artifactVersion = c.version?.lowerBound
      }
    }
  }

  if (artifactLibrary != null) {
    // We don't have an exact match in the version catalog, but we *do* have the artifact there.
    // This means we can offer some options:

    // (1) Insert new library definition for this exact version
    val addNew =
      createAddNewCatalogLibrary(
        dependency,
        versionsMap,
        librariesMap,
        document,
        context,
        valueCookie,
        versionVar,
        includeVersionInKey = true,
      )
    // (2) Change this gradle dependency reference to instead point to the version catalog
    val key = artifactLibrary.getKey()!!
    val switchToVersion =
      createSwitchToLibraryFix(
        document,
        artifactLibrary,
        key,
        context,
        valueCookie,
        libraryVersion = artifactVersion,
        safe = false,
      )
    val fix = LintFix.create().alternatives(addNew, switchToVersion)
    val artifact = "${dependency.group}:${dependency.name}"
    val message =
      "Use version catalog instead ($artifact is already available as `$key`, but using version $artifactVersion instead)"
    return message to fix
  }

  val existingVersionNode = if (versionVar != null) versionsMap?.get(versionVar) else null
  val existingVersion = existingVersionNode?.getActualValue()?.toString()

  val matchExistingVar = findExistingVariable(dependency, versionsMap, librariesMap)?.key

  // We didn't find this artifact in the version catalog at all; offer to add it.
  if (existingVersion == null || existingVersion == richVersionIdentifier) {
    val fix =
      LintFix.create()
        .alternatives(
          if (matchExistingVar != null) {
            // One of the existing version variables matches this exact revision; offer to re-use
            // it.
            createAddNewCatalogLibrary(
              dependency,
              versionsMap,
              librariesMap,
              document,
              context,
              valueCookie,
              matchExistingVar,
              allowExistingVersionVar = true,
              overrideMessage =
                "Replace with new library catalog declaration, reusing version variable $matchExistingVar",
            )
          } else {
            null
          },
          createAddNewCatalogLibrary(
            dependency,
            versionsMap,
            librariesMap,
            document,
            context,
            valueCookie,
            versionVar,
            true,
            autoFix = true,
            independent = false,
          ) ?: return null,
        )
    return null to fix
  } else {
    // The same version variable already exists, but has a different version.
    // Create three alternatives: Reusing the existing version variable, change version variable to
    // the new version, or create a new version variable. Only the last option is safe, but probably
    // usually not
    // what people want.
    val fix =
      LintFix.create()
        .alternatives(
          createAddNewCatalogLibrary(
            dependency,
            versionsMap,
            librariesMap,
            document,
            context,
            valueCookie,
            versionVar,
            allowExistingVersionVar = true,
            overrideMessage =
              "Replace with new library catalog declaration, reusing version variable $versionVar (version=$existingVersion)",
            autoFix = false,
          ),
          createChangeVersionFix(richVersionIdentifier, existingVersionNode),
          createAddNewCatalogLibrary(
            dependency,
            versionsMap,
            librariesMap,
            document,
            context,
            valueCookie,
            versionVar,
            allowExistingVersionVar = false,
          ),
        )
    return null to fix
  }
}

/**
 * Given a dependency coordinate, looks through existing version variables and decides whether one
 * of them should be offered as the version variable to be used for this dependency.
 *
 * This will be true if (1) the version matches exactly, and (2) if the group matches exactly.
 */
private fun findExistingVariable(
  dependency: Dependency,
  versions: LintTomlMapValue?,
  libraries: LintTomlMapValue?,
): Map.Entry<String, LintTomlValue>? {
  versions ?: return null
  libraries ?: return null
  val revision = dependency.version?.toIdentifier() ?: return null
  val group = dependency.group ?: return null
  for (versionEntry in versions.getMappedValues()) {
    val versionNode = versionEntry.value
    val value = versionNode.getActualValue()
    if (value == revision) {
      // See if this variable matches the group of any libraries
      for (entry in libraries.getMappedValues()) {
        val library = entry.value as? LintTomlMapValue ?: continue
        val libraryVersion = getVersion(library, versions) ?: return null
        if (libraryVersion != versionNode) {
          continue
        }

        val module = library["module"]
        if (module is LintTomlLiteralValue) {
          val artifact = module.getActualValue()?.toString()?.trim()
          if (artifact != null) {
            val index = artifact.indexOf(':')
            if (index != -1 && group.regionMatches(0, artifact, 0, index)) {
              return versionEntry
            }
          }
        } else {
          val libraryGroup = library["group"]?.getActualValue()?.toString()?.trim()
          if (libraryGroup == group) {
            return versionEntry
          }
        }
      }
    }
  }

  return null
}

/** Creates fix which changes the version variable in [versionNode] to [version] */
private fun createChangeVersionFix(version: String?, versionNode: LintTomlValue): LintFix? =
  version?.let {
    LintFix.create()
      .name("Change ${versionNode.getKey()} to $version")
      .replace()
      .range(versionNode.getLocation())
      .all()
      .with("\"$version\"")
      .build()
  }

/**
 * Creates fix which creates a new version catalog entry (library and version name) for the given
 * [dependency] library
 */
private fun createAddNewCatalogLibrary(
  dependency: Dependency,
  versionsMap: LintTomlMapValue?,
  librariesMap: LintTomlMapValue,
  document: LintTomlDocument,
  context: GradleContext,
  valueCookie: Any,
  versionVar: String?,
  allowExistingVersionVar: Boolean = false,
  includeVersionInKey: Boolean = false,
  overrideMessage: String? = null,
  autoFix: Boolean = true,
  independent: Boolean = autoFix,
): LintFix? {
  // TODO: Be smarter about version variables:
  //   (1) use the same naming convention
  //   (2) for related libraries, offer to "reuse" it? (e.g. for related kotlin libraries. Be
  // careful here.)
  val versionVariable =
    pickVersionVariableName(
      dependency,
      versionsMap?.getMappedValues(),
      versionVar,
      allowExistingVersionVar,
    )
  val libraryVariable =
    pickLibraryVariableName(dependency, librariesMap.getMappedValues(), includeVersionInKey)

  val source = document.getSource()
  val versionVariableFix: LintFix?
  val usedVariable: String?
  if (versionsMap == null || !versionsMap.contains(versionVariable)) {
    versionVariableFix =
      createAddVersionFix(versionsMap, source, document, versionVariable, dependency)
    usedVariable = if (versionVariableFix == null) null else versionVariable
  } else {
    versionVariableFix = null
    usedVariable = versionVariable
  }
  val insertLibraryFix =
    createInsertLibraryFix(
      librariesMap,
      source,
      libraryVariable,
      dependency,
      usedVariable,
      document,
    ) ?: return null
  val gradleFix =
    createReplaceWithLibraryReferenceFix(document, context, valueCookie, libraryVariable, true)

  return LintFix.create()
    .name(overrideMessage ?: "Replace with new library catalog declaration for $libraryVariable")
    .composite(
      // Insert versions variable declaration
      versionVariableFix,
      // Insert library declaration
      insertLibraryFix,
      // Update build.gradle to link to it
      gradleFix,
    )
    .autoFix(autoFix, independent)
}

/**
 * Creates a fix which replaces the build.gradle dependency (at [valueCookie] with the given
 * [library] reference in the version catalog
 */
private fun createSwitchToLibraryFix(
  document: LintTomlDocument,
  library: LintTomlValue,
  key: String,
  context: GradleContext,
  valueCookie: Any,
  libraryVersion: Version? = null,
  safe: Boolean,
): LintFix.ReplaceString? {
  val variableName = library.getFullKey()?.removePrefix("libraries.") ?: return null
  return createReplaceWithLibraryReferenceFix(
    document,
    context,
    valueCookie,
    variableName,
    safe,
    "Replace with existing version catalog reference `$key`${libraryVersion?.let { " (version $it)"} ?: ""}",
  )
}

private fun createReplaceWithLibraryReferenceFix(
  document: LintTomlDocument,
  context: GradleContext,
  valueCookie: Any,
  libraryVariable: String,
  safe: Boolean,
  name: String? = null,
): LintFix.ReplaceString {
  val catalogName = getCatalogName(document)
  // https://docs.gradle.org/current/userguide/platforms.html#sub:mapping-aliases-to-accessors
  val gradleKey = StringBuilder()
  for (i in libraryVariable.indices) {
    val c = libraryVariable[i]
    if (
      c == '-' ||
        c == '_' &&
          (i == 0 ||
            !libraryVariable[i].isDigit() &&
              (i == libraryVariable.length - 1 || !libraryVariable[i + 1].isDigit()))
    ) {
      gradleKey.append('.')
    } else {
      gradleKey.append(c)
    }
  }
  val replacement = "$catalogName.$gradleKey"
  val range = getGradleDependencyStringLocation(context, valueCookie)
  return LintFix.create()
    .replace()
    .range(range)
    .all()
    .with(replacement)
    .autoFix(safe, safe)
    .apply { if (name != null) name(name) }
    .build() as LintFix.ReplaceString
}

private fun getGradleDependencyStringLocation(context: GradleContext, valueCookie: Any): Location {
  var sourcePsi = (valueCookie as? UElement)?.sourcePsi
  // Even simple "test" string literal is mapped to a polyadic expression
  // after KTIJ-27448 (ui injection host)
  if (
    sourcePsi is KtStringTemplateExpression &&
      (valueCookie as? UPolyadicExpression)?.operands?.size == 1
  ) {
    // Unwrap the polyadic expression
    sourcePsi = valueCookie.operands.single().sourcePsi
  }
  if (sourcePsi is KtLiteralStringTemplateEntry) {
    // In UAST we end up with the element for the contents within the string, not the whole String
    // element
    val argument = sourcePsi.getParentOfType<KtValueArgument>(true)
    if (argument != null) {
      return context.getLocation(argument)
    }
  }

  return context.getLocation(valueCookie)
}

/**
 * Attempts to return the name of the Gradle version catalog for this TOML file.
 *
 * For now, we're just guessing based on the TOML file name. But there's no guarantee that the
 * filename in `gradle/` corresponds to the catalogName in gradle build files: as an example, see
 * https://docs.gradle.org/current/userguide/platforms.html#sec:importing-catalog-from-file This is
 * intractable in general; settings files can contain arbitrary code.
 */
private fun getCatalogName(document: LintTomlDocument) =
  document.getFile().name.substringBefore('.')

/**
 * Checks whether this map is in **mostly** alphabetical order. Initially, we were only using the
 * alphabetical insert location if the list was in a completely correct alphabetical order, but in
 * reality there are many cases where the list looks mostly alphabetical but one or two elements are
 * inserted slightly incorrectly, and in this case we'd fall back to appending to the end. Here we
 * return true if the list is *mostly* alphabetical, and then we'll pick the best effort insertion
 * point in that case.
 */
private fun LintTomlMapValue.isInAlphabeticalOrder(): Boolean {
  var lastKey = ""
  var correctCount = 0
  val map = getMappedValues()
  val keyCount = map.size
  if (keyCount <= 3) { // tiny lists -- let's try to insert in alphabetical order.
    return true
  }
  for ((key, _) in map) {
    if (key.compareTo(lastKey, ignoreCase = true) >= 0) {
      correctCount++
    }
    lastKey = key
  }
  // If 90% of the keys are in the right place, let's call that alphabetical order.
  return (correctCount - keyCount) <= 3 || correctCount * 100 / keyCount >= 90
}

/**
 * For the given ordered [map] of key/values, *if* the list of keys is alphabetical, then return the
 * first value which comes *after* the [name] in alphabetical order.
 */
private fun getBeforeIfAlphabeticOrder(mapOwner: LintTomlMapValue?, name: String): LintTomlValue? {
  mapOwner ?: return null
  if (!mapOwner.isInAlphabeticalOrder()) {
    return null
  }
  for ((key, value) in mapOwner.getMappedValues()) {
    if (key.compareTo(name, ignoreCase = true) >= 0) {
      return value
    }
  }
  return null
}

private fun createAddVersionFix(
  versionsMap: LintTomlMapValue?,
  source: CharSequence,
  document: LintTomlDocument,
  versionVariable: String,
  dependency: Dependency,
): LintFix? {
  val versionIdentifier = dependency.version?.toIdentifier() ?: return null
  val separator = if (spaceAroundEquals(versionsMap)) " = " else "="
  val variableDeclaration = "$versionVariable$separator\"$versionIdentifier\"\n"

  // Is the list already in alphabetical order?
  val independent = true
  val before = getBeforeIfAlphabeticOrder(versionsMap, versionVariable)
  val versionInsertOffset: Int =
    if (before != null) {
      before.getKeyStartOffset()
    } else {
      // No, place it last
      val lastVersion = versionsMap?.last()
      if (lastVersion != null) {
        source.findNextLineStart(lastVersion.getEndOffset()).let {
          if (it == -1) source.length else it
        }
      } else {
        val versionsIndex = source.indexOf("[versions]")
        if (versionsIndex != -1) {
          // There's only a [versions] entry in the document, but no version variables; insert the
          // first one
          source.findNextLineStart(versionsIndex)
        } else {
          // We *could* insert a new [versions] block here. But in batch mode
          // this is problematic (the quickfixes would all insert the same thing),
          // and more importantly, if the user *does* have a [libraries] block but
          // no [versions] block, they may prefer to just use explicit version=
          // instead of version.ref=, so we'll just do that.
          //    variableDeclaration = "[versions]\n$variableDeclaration"
          //    independent = false
          //    // Insert at the top
          //    0
          return null
        }
      }
    }
  return LintFix.create()
    .replace()
    .range(Location.create(document.getFile(), source, versionInsertOffset, versionInsertOffset))
    .beginning()
    .with(variableDeclaration)
    .autoFix(true, independent)
    .build()
}

private fun createInsertLibraryFix(
  librariesMap: LintTomlMapValue,
  source: CharSequence,
  libraryVariable: String,
  dependency: Dependency,
  versionVariable: String?,
  document: LintTomlDocument,
): LintFix? {
  val before = getBeforeIfAlphabeticOrder(librariesMap, libraryVariable)
  var libraryInsertOffset: Int =
    if (before != null) {
      before.getKeyStartOffset()
    } else {
      val lastLibrary = librariesMap.last()
      if (lastLibrary != null) {
        source.findNextLineStart(lastLibrary.getEndOffset())
      } else {
        val librariesIndex = source.indexOf("[libraries]")
        if (librariesIndex != -1) {
          // There's only a [libraries] entry in the document, but no version variables; insert the
          // first one
          source.findNextLineStart(librariesIndex)
        } else {
          // There's no existing [libraries] entry in the version catalog.
          // This shouldn't happen; we shouldn't have considered the project having version
          // catalogs if there is no [libraries] node in the TOML model
          return null
        }
      }
    }

  var prefix = ""
  var suffix = ""

  if (libraryInsertOffset == -1) {
    // Not found in the [libraries] section; put it before the next section
    val nextSection = librariesMap.next()
    if (nextSection != null) {
      libraryInsertOffset = nextSection.getKeyStartOffset()
      if (libraryInsertOffset == -1) {
        libraryInsertOffset = nextSection.getStartOffset()
      }
      source[libraryInsertOffset]
      // Back up over blank lines between the sections
      while (libraryInsertOffset > 0 && source[libraryInsertOffset - 1] != '\n') {
        libraryInsertOffset--
      }
      prefix = ""
      suffix = "\n"
    } else {
      // Nothing left in the document; place it at the end
      libraryInsertOffset = source.length
    }
  }

  if (libraryInsertOffset > 0 && source[libraryInsertOffset - 1] != '\n') {
    prefix = "\n"
  } else {
    suffix = "\n"
  }

  val version =
    if (versionVariable != null) {
      "version.ref = \"$versionVariable\""
    } else {
      val richVersion = dependency.version
      richVersion?.let {
        val identifier = it.toIdentifier()
        if (identifier.isNullOrBlank()) return null // for invalid version
        else "version = \"$it\""
      }
    }
  val group = dependency.group ?: return null
  val versionWithSeparator = version?.let { ", $it" } ?: ""
  val moduleDeclaration =
    "$prefix$libraryVariable = { module = \"$group:${dependency.name}\"$versionWithSeparator }$suffix"
  return LintFix.create()
    .replace()
    .range(Location.create(document.getFile(), source, libraryInsertOffset, libraryInsertOffset))
    .beginning()
    .with(moduleDeclaration)
    .autoFix()
    .build()
}

private fun spaceAroundEquals(versionsMap: LintTomlMapValue?): Boolean {
  versionsMap ?: return true
  val first = versionsMap.first()
  val keyStart = first?.getKeyStartOffset() ?: return true
  val valueStart = first.getStartOffset()
  val indexOf = first.getDocument().getSource().indexOf(" =", keyStart)
  return indexOf != -1 && indexOf < valueStart
}

/**
 * For the given `\[libraries]` table, pick a new/unique library name key which represents the given
 * [gc] coordinate, and is unique, and ideally matches the existing naming style.
 */
@VisibleForTesting
fun pickLibraryVariableName(
  dependency: Dependency,
  libraries: Map<String, LintTomlValue>,
  includeVersionInKey: Boolean,
): String {
  val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)
  for ((key, _) in libraries) {
    reserved.add(key)
  }

  val reservedQuickfixNames = getReservedQuickfixNames(VC_LIBRARIES)
  for (key in reservedQuickfixNames) {
    reserved.add(key)
  }

  val suggestion = pickLibraryVariableName(dependency, includeVersionInKey, reserved)
  reservedQuickfixNames.add(suggestion)

  return suggestion
}

/**
 * Whether this map of versions or library keys contains the given [name], with case-insensitive
 * matching.
 */
private fun LintTomlMapValue?.contains(name: String): Boolean {
  this ?: return false
  return getMappedValues().any { it.key.equals(name, ignoreCase = true) }
}

/**
 * Picks a suitable name for the version variable to use for [dependency] which is unique and
 * ideally follows the existing naming style. Ensures that the suggested name does not conflict with
 * an existing versions listed in the [versionsMap].
 *
 * If the optional [versionVar] name is provided, this is a preferred name to use. It will only use
 * that name if it is not already in use, **or**, if [allowExistingVersionVar] is set to true.
 */
@VisibleForTesting
fun pickVersionVariableName(
  dependency: Dependency,
  versionsMap: Map<String, LintTomlValue>?,
  versionVar: String? = null,
  allowExistingVersionVar: Boolean = false,
): String {
  if (allowExistingVersionVar && versionVar != null) {
    return versionVar
  }

  val reserved = TreeSet(String.CASE_INSENSITIVE_ORDER)

  if (versionsMap != null) {
    for ((key, _) in versionsMap) {
      reserved.add(key)
    }
  }

  val reservedQuickfixNames = getReservedQuickfixNames(VC_VERSIONS)
  for (key in reservedQuickfixNames) {
    reserved.add(key)
  }

  // Is the gradle coordinate *already* using a variable name? If so, use that!
  // (we do this *outside* of the reservedQuickfix check because for multiple
  // dependencies all referencing the same variable we want to make the same
  // suggestion over and over.
  if (versionVar != null && !reserved.contains(versionVar)) {
    return versionVar
  }

  val suggestion = pickVersionVariableName(dependency, reserved)
  reservedQuickfixNames.add(suggestion)

  return suggestion
}

private fun getReservedQuickfixNames(key: String): MutableSet<String> {
  val reservedQuickfixNames =
    GradleDetector.reservedQuickfixNames
      ?: mutableMapOf<String, MutableSet<String>>().also {
        GradleDetector.reservedQuickfixNames = it
      }
  return reservedQuickfixNames[key]
    ?: mutableSetOf<String>().also { reservedQuickfixNames[key] = it }
}
