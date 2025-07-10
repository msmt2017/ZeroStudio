/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.SdkConstants.FD_JNI
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.Version
import com.android.ide.common.pagealign.hasElfMagicNumber
import com.android.ide.common.pagealign.is16kAligned
import com.android.ide.common.pagealign.readElfMinimumLoadSectionAlignment
import com.android.tools.lint.checks.GradleDetector.Companion.getNamedDependency
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintTomlDocument
import com.android.tools.lint.client.api.LintTomlMapValue
import com.android.tools.lint.client.api.TomlContext
import com.android.tools.lint.client.api.TomlScanner
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleContext.Companion.getStringLiteralValue
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope.Companion.GRADLE_AND_TOML_SCOPE
import com.android.tools.lint.detector.api.Scope.Companion.GRADLE_SCOPE
import com.android.tools.lint.detector.api.Scope.Companion.TOML_SCOPE
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.model.LintModelAndroidLibrary
import com.android.tools.lint.model.LintModelMavenName
import java.io.File

/** Looks for problems with transitive libraries not being properly 16 KB aligned. */
class PageAlignmentDetector : Detector(), GradleScanner, TomlScanner {
  /**
   * Incidents found by looking at transitive dependencies; we'll look at Gradle and TOML version
   * declaration to see if they're in this collection such that we can associate the error with a
   * user source file; for the ones we can't find (if they for example belong to a transitive
   * dependency that you don't reference directly in a source file) we'll just report them after the
   * fact in [afterCheckRootProject] pointing to the broken .so file itself as the error location.
   */
  private var reportCoordinates: MutableMap<LintModelMavenName, Incident>? = null

  override fun beforeCheckRootProject(context: Context) {
    // This lint check has two separate paths; one for running in the IDE,
    // and the other for running in batch mode. The below is for the batch
    // mode, where we collect all dependencies from the dependency graph;
    // in the IDE we'll only look up dependencies we come across in the same
    // file.
    if (context.driver.isIsolated()) {
      return
    }
    val project = context.project
    val variant = project.buildVariant
    if (variant != null) {
      val artifact = variant.artifact
      val dependencies = artifact.dependencies
      for (androidLibrary in dependencies.getAll()) {
        if (androidLibrary is LintModelAndroidLibrary) {
          val unalignedLibrary = getUnalignedLibraryFile(androidLibrary)
          if (unalignedLibrary != null) {
            val coordinates = androidLibrary.resolvedCoordinates
            val incident = createIncident(unalignedLibrary, coordinates)
            recordIncident(coordinates, incident)
          }
        }
      }
    }
  }

  private fun createIncident(sharedLibrary: File, coordinates: LintModelMavenName): Incident {
    val location = Location.create(sharedLibrary)
    val libraryName = "${sharedLibrary.parentFile?.name}/${sharedLibrary.name}"
    val message = "The native library `$libraryName` (from `$coordinates`) is not 16 KB aligned"
    val incident = Incident(ISSUE, location, message)
    return incident
  }

  private fun recordIncident(coordinate: LintModelMavenName, incident: Incident) {
    // We don't report the incident right away; instead, we record the
    // coordinate and look for it in dependency declarations in gradle and
    // version catalogs, and if it matches, we'll report the error there (and
    // remove it from this list). Any unreported errors at the end are reported
    // from afterCheckRootProject.
    val targetList =
      reportCoordinates
        ?: mutableMapOf<LintModelMavenName, Incident>().also { reportCoordinates = it }
    targetList.put(coordinate, incident)
  }

  override fun afterCheckRootProject(context: Context) {
    // Handle any incidents we didn't find dependency declarations for
    val targets = reportCoordinates ?: return
    if (targets.isEmpty()) {
      return
    }
    val iterator = targets.iterator()
    while (iterator.hasNext()) {
      val (_, incident) = iterator.next()
      context.report(incident)
      iterator.remove()
    }
    reportCoordinates = null
  }

  override fun checkDslPropertyAssignment(
    context: GradleContext,
    property: String,
    value: String,
    parent: String,
    parentParent: String?,
    propertyCookie: Any,
    valueCookie: Any,
    statementCookie: Any,
  ) {
    val targets = reportCoordinates
    if (!context.driver.isIsolated() && targets == null) {
      return
    }
    if (parent == "dependencies" || parent == "declarativeDependencies") {
      val dependencyString = getStringLiteralValue(value, valueCookie) ?: getNamedDependency(value)
      if (dependencyString != null) {
        val dependency = Dependency.parse(dependencyString)
        val groupId = dependency.group ?: return
        val artifactId = dependency.name
        if (targets == null) {
          // isolated: check in IDE dependencies
          checkArtifactReference(context, groupId, dependency.name) {
            context.getLocation(valueCookie)
          }
        } else {
          for ((coordinate, incident) in targets) {
            if (coordinate.groupId == groupId && coordinate.artifactId == artifactId) {
              incident.location = context.getLocation(valueCookie)
              context.report(incident)
              targets.remove(coordinate)
              return
            }
          }
        }
      }
    }
  }

  override fun visitTomlDocument(context: TomlContext, document: LintTomlDocument) {
    val targets = reportCoordinates
    if (!context.driver.isIsolated() && targets == null) {
      return
    }
    val libraries = document.getValue(VC_LIBRARIES) as? LintTomlMapValue
    if (libraries != null) {
      val versions = document.getValue(VC_VERSIONS) as? LintTomlMapValue
      // Batch: we've recorded problems in [reportCoordinates]; try to
      // map these to dependencies we find anywhere, including transitive
      // dependencies
      for ((_, library) in libraries.getMappedValues()) {
        val (coordinate, _) = getLibraryFromTomlEntry(versions, library) ?: continue
        val dependency = Dependency.parse(coordinate)
        val groupId = dependency.group ?: continue
        val artifactId = dependency.name
        if (targets != null) {
          for ((coordinate, incident) in targets) {
            if (coordinate.groupId == groupId && coordinate.artifactId == artifactId) {
              incident.location = context.getLocation(library)
              context.report(incident)
              targets.remove(coordinate)
              return
            }
          }
        } else {
          // In the IDE: Just check dependencies directly
          checkArtifactReference(context, groupId, artifactId) { context.getLocation(library) }
        }
      }
    }
  }

  private fun checkArtifactReference(
    context: Context,
    groupId: String?,
    artifactId: String,
    locationProvider: () -> Location,
  ) {
    groupId ?: return

    val full = context.isGlobalAnalysis()
    val project = if (full) context.mainProject else context.project

    val androidLibrary = project.buildVariant?.artifact?.findCompileDependency(groupId, artifactId)
    if (androidLibrary is LintModelAndroidLibrary) {
      val unalignedLibrary = getUnalignedLibraryFile(androidLibrary)
      if (unalignedLibrary != null) {
        val coordinates = androidLibrary.resolvedCoordinates
        val incident = createIncident(unalignedLibrary, coordinates)
        incident.location = locationProvider()
        context.report(incident)
      }
    }
  }

  companion object {
    private val ALIGNED =
      File("16kb-aligned") // map value representing 16 KB aligned; not a real file
    private val alignedCache = HashMap<LintModelMavenName, File>()

    /**
     * Given a [library] definition, returns null if the library is properly 16 KB aligned, or
     * otherwise the first (alphabetically) shared library file for this [library] that is not
     * properly aligned.
     */
    private fun getUnalignedLibraryFile(library: LintModelAndroidLibrary): File? {
      val coordinate: LintModelMavenName = library.resolvedCoordinates

      val group = coordinate.groupId
      val artifact = coordinate.artifactId
      val version = coordinate.version
      if (isSafe(group, artifact, version)) {
        return null
      }

      // We only cache in the IDE (for on-the-fly analysis as the user is editing;
      // don't do this from AGP since this is not thread safe.)
      val useCache = LintClient.isStudio
      if (useCache) {
        val cached = alignedCache[coordinate]
        if (cached != null) {
          @Suppress("FileComparisons") return if (cached === ALIGNED) null else cached
        }
        alignedCache[coordinate] = ALIGNED
      }
      val folder = library.folder
      val jniFolder = File(folder, FD_JNI)
      if (jniFolder.isDirectory) {
        var unalignedLibrary: File? = null
        abiFolderLoop@ for (abiFolder in jniFolder.listFiles().sorted()) {
          for (sharedLibrary in abiFolder.listFiles().sorted()) {
            if (sharedLibrary.isFile) {
              val input = sharedLibrary.inputStream().buffered()
              input.use {
                if (hasElfMagicNumber(input)) {
                  val minimumLoadSectionAlignment = readElfMinimumLoadSectionAlignment(input)
                  if (
                    minimumLoadSectionAlignment != -1L && !is16kAligned(minimumLoadSectionAlignment)
                  ) {
                    unalignedLibrary = sharedLibrary
                  }
                }
              }
              if (unalignedLibrary != null) {
                break@abiFolderLoop
              }
            }
          }
        }
        if (useCache && unalignedLibrary != null) {
          alignedCache[coordinate] = unalignedLibrary
        }
        return unalignedLibrary
      }
      return null
    }

    /**
     * Returns whether the given group+artifact+version Google maven artifact is known to be safe
     * (e.g. does not have a 16 KB alignment problem).
     *
     * This is based on scanning all libraries on gmaven across all versions and looking for
     * alignment problems.
     *
     * This of course only knows about the libraries up until the time of scanning, but the
     * assumption is that as of now, all future libraries are correctly compiled, so this just
     * avoids doing a lot of unnecessary I/O on packages in the gmaven names space unless they're
     * for known older versions.
     *
     * Note that this method returning false doesn't mean that the library is known to be
     * incompatible; in that case, the lint check should look. (For example, the incompatibility
     * could be in one of the ABIs that are filtered out by this app --
     * https://developer.android.com/build/configure-apk-splits#configure-abi-split
     */
    fun isSafe(group: String, artifact: String, version: String): Boolean {
      val lastBroken =
        when (group) {
          "androidx.appsearch" ->
            if (artifact == "appsearch-local-storage") "1.1.0-alpha03" else null
          "androidx.datastore" -> if (artifact == "datastore-core-android") "1.1.0-beta01" else null
          "androidx.graphics" -> {
            when (artifact) {
              "graphics-core" -> "1.0.0-beta01"
              "graphics-path" -> "1.0.0-beta02"
              else -> null
            }
          }
          "androidx.tracing" -> if (artifact == "tracing-perfetto-binary") "1.0.0" else null
          "com.crashlytics.sdk.android" -> if (artifact == "crashlytics-ndk") "2.1.1" else null
          "com.google.ai.edge.litert" -> {
            when (artifact) {
              "litert-gpu" -> "1.0.1"
              "litert" -> "1.0.1"
              else -> null
            }
          }
          "com.google.android.games" -> if (artifact == "memory-advice") "0.24" else null
          "com.google.android.gms" -> {
            when (artifact) {
              "play-services-tflite-java" -> "16.2.0-beta02"
              "play-services-vision-face-contour-internal" -> "16.1.0"
              "play-services-vision-image-labeling-internal" -> "16.1.0"
              else -> null
            }
          }
          "com.google.android.libraries.navigation" ->
            if (artifact == "navigation") "5.2.5" else null
          "com.google.ar" -> if (artifact == "core") "1.42.0" else null
          "com.google.ar.sceneform" -> {
            when (artifact) {
              "animation" -> "1.17.1"
              "assets" -> "1.17.1"
              "core" -> "1.17.1"
              "filament-android" -> "1.17.1"
              else -> null
            }
          }
          "com.google.firebase" -> {
            when (artifact) {
              "firebase-crashlytics-ndk" -> "19.0.1"
              "firebase-ml-natural-language-language-id-model" -> "20.0.8"
              "firebase-ml-natural-language-smart-reply-model" -> "20.0.8"
              "firebase-ml-natural-language-translate-model" -> "20.0.9"
              "firebase-ml-vision-barcode-model" -> "16.1.2"
              "firebase-ml-vision-internal-vkp" -> "17.0.2"
              else -> null
            }
          }
          "com.google.mediapipe" -> {
            when (artifact) {
              "solution-core" -> "0.10.20"
              "tasks-audio" -> "0.20230731"
              "tasks-genai" -> "0.10.20"
              "tasks-text" -> "0.20230731"
              "tasks-vision-image-generator" -> "0.10.20"
              "tasks-vision" -> "0.20230731"
              else -> null
            }
          }
          "com.google.mlkit" -> {
            when (artifact) {
              "barcode-scanning" -> "17.2.0"
              "digital-ink-recognition" -> "18.1.0"
              "entity-extraction" -> "16.0.0-beta5"
              "language-id" -> "17.0.5"
              "mediapipe-internal" -> "17.0.0-beta9"
              "smart-reply" -> "17.0.3"
              "text-recognition-bundled-common" -> "16.0.0"
              "translate" -> "17.0.2"
              "vision-internal-vkp" -> "18.2.2"
              else -> null
            }
          }
          "org.chromium.net" -> if (artifact == "cronet-embedded") "119.6045.31" else null
          else -> null
        }
      if (lastBroken == null) {
        // The above list includes all cases of maven.google.com libraries that contain
        // JNI libraries where at least one version has code that is not 16 KB aligned.
        // That means that if we're presented with any OTHER package from AndroidX,
        // it's safe, and we don't have to go look on disk.
        // (If we wanted a really accurate list we can use GoogleMavenRepository,
        // which stores all the relevant group id's in the master-index file, but
        // it's not required; this is just an optimization path to avoid I/O for *most*
        // libraries.)
        return group.startsWith("androidx.") ||
          group.startsWith("com.google.") ||
          group.startsWith("com.android.") ||
          group == "org.chromium.net" ||
          group.startsWith("com.crashlytics.")
      } else {
        val parsedVersion = Version.parse(version)
        val parsedLastBrokenVersion = Version.parse(lastBroken)
        return parsedVersion > parsedLastBrokenVersion
      }
    }

    @JvmField
    val ISSUE =
      Issue.create(
        id = "Aligned16KB",
        briefDescription = "Native library dependency not 16 KB aligned",
        explanation =
          """
          Android has traditionally used 4 KB memory page sizes. However, to support \
          future devices that only work with 16 KB aligned libraries apps containing \
          native libraries need to be built with 16 KB alignment.

          Apps with 4 KB aligned native libraries may not work correctly on devices \
          requiring 16 KB alignment. To ensure compatibility and future-proof your \
          app, it is strongly recommended that your native libraries are aligned to 16 \
          KB boundaries.

          If your app uses any NDK libraries, directly or indirectly through an SDK, \
          you should rebuild your app to meet this recommendation. Make sure all \
          native libraries within your application, including those from dependencies, \
          are built with 16 KB page alignment.

          This lint check looks at all native libraries that your app depends on. If \
          any are found to be aligned to 4 KB instead of 16 KB, you will need to \
          address this.

          When a library is flagged, first try to update to a newer version that \
          supports 16 KB alignment. If an updated version is not available, contact \
          the library vendor to ask about their plans for 16 KB support and request a \
          compatible version. Updating your libraries proactively will help ensure \
          your app works properly on a wider range of devices.
          """,
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            PageAlignmentDetector::class.java,
            GRADLE_AND_TOML_SCOPE,
            GRADLE_SCOPE,
            TOML_SCOPE,
          ),
        androidSpecific = true,
        moreInfo = "https://developer.android.com/guide/practices/page-sizes",
      )
  }
}
