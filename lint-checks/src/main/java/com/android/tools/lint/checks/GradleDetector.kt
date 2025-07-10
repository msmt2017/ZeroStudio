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

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION
import com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.SdkConstants.currentPlatform
import com.android.ide.common.gradle.Component
import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.RichVersion
import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.repository.GoogleMavenRepository
import com.android.ide.common.repository.GoogleMavenRepository.Companion.MAVEN_GOOGLE_CACHE_DIR_KEY
import com.android.ide.common.repository.MavenRepositories
import com.android.io.CancellableFileIo
import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SdkVersionInfo.LOWEST_ACTIVE_API
import com.android.tools.lint.checks.GooglePlaySdkIndex.Companion.GOOGLE_PLAY_SDK_INDEX_KEY
import com.android.tools.lint.checks.GooglePlaySdkIndex.Companion.GOOGLE_PLAY_SDK_INDEX_URL
import com.android.tools.lint.checks.GooglePlaySdkIndex.Companion.VulnerabilityDescription
import com.android.tools.lint.checks.GradleDetector.Companion.COMPATIBILITY
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintTomlDocument
import com.android.tools.lint.client.api.LintTomlMapValue
import com.android.tools.lint.client.api.LintTomlValue
import com.android.tools.lint.client.api.TomlContext
import com.android.tools.lint.client.api.TomlScanner
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Constraint
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleContext.Companion.getIntLiteralValue
import com.android.tools.lint.detector.api.GradleContext.Companion.getStringLiteralValue
import com.android.tools.lint.detector.api.GradleContext.Companion.isNonNegativeInteger
import com.android.tools.lint.detector.api.GradleContext.Companion.isStringLiteral
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.getLanguageLevel
import com.android.tools.lint.detector.api.guessGradleLocation
import com.android.tools.lint.detector.api.isNumberString
import com.android.tools.lint.detector.api.readUrlData
import com.android.tools.lint.detector.api.readUrlDataAsString
import com.android.tools.lint.model.LintModelArtifactType
import com.android.tools.lint.model.LintModelDependency
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelLibrary
import com.android.tools.lint.model.LintModelMavenName
import com.android.tools.lint.model.LintModelModuleType
import com.android.utils.XmlUtils
import com.android.utils.appendCapitalized
import com.android.utils.iterator
import com.android.utils.usLocaleCapitalize
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.collect.ArrayListMultimap
import com.intellij.pom.java.LanguageLevel.JDK_1_7
import com.intellij.pom.java.LanguageLevel.JDK_1_8
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.file.Path
import java.util.Calendar
import java.util.Collections
import java.util.EnumSet
import java.util.function.Predicate
import kotlin.text.Charsets.UTF_8
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

/** Checks Gradle files for potential errors. */
open class GradleDetector : Detector(), GradleScanner, TomlScanner, XmlScanner {

  protected open val gradleUserHome: File
    get() {
      // See org.gradle.initialization.BuildLayoutParameters
      var gradleUserHome: String? = System.getProperty("gradle.user.home")
      if (gradleUserHome == null) {
        gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (gradleUserHome == null) {
          gradleUserHome = System.getProperty("user.home") + File.separator + ".gradle"
        }
      }

      return File(gradleUserHome)
    }

  private var artifactCacheHome: File? = null

  /**
   * If incrementally editing a single build.gradle file, tracks whether we've already transitively
   * checked GMS versions such that we don't flag the same error on every single dependency
   * declaration.
   */
  private var mCheckedGms: Boolean = false

  /**
   * If incrementally editing a single build.gradle file, tracks whether we've already transitively
   * checked wearable library versions such that we don't flag the same error on every single
   * dependency declaration.
   */
  private var mCheckedWearableLibs: Boolean = false

  /**
   * If incrementally editing a single build.gradle file, tracks whether we've already applied
   * kotlin-android plugin.
   */
  private var mAppliedKotlinAndroidPlugin: Boolean = false

  /**
   * If incrementally editing a single build.gradle file, tracks whether we've already applied
   * kotlin-kapt plugin.
   */
  private var mAppliedKotlinKaptPlugin: Boolean = false

  /**
   * If incrementally editing a single build.gradle file, tracks whether we've already applied the
   * KSP plugin.
   */
  private var mAppliedKspPlugin: Boolean = false

  /**
   * If incrementally editing a single build.gradle file, tracks whether we have applied a java
   * plugin (e.g. application, java-library)
   */
  private var mAppliedJavaPlugin: Boolean = false

  data class JavaPluginInfo(val cookie: Any)

  private var mJavaPluginInfo: JavaPluginInfo? = null

  private var mDeclaredSourceCompatibility: Boolean = false
  private var mDeclaredTargetCompatibility: Boolean = false

  /**
   * If incrementally editing a single build.gradle file, tracks whether we have declared the google
   * maven repository in the buildscript block.
   *
   * Because there are many ways to declare repositories for plugin resolution (including e.g. in
   * pluginManagement declarations in settings files), we track whether we have seen anything at all
   * in buildscript repositories; if we haven't, we don't know whether the google maven repository
   * is actually visible to the project.
   */
  private var mDeclaredGoogleMavenRepository: Boolean = false
  private var mDeclaredBuildscriptRepository: Boolean = false

  data class AgpVersionCheckInfo(
    val newerVersion: Version,
    val newerVersionIsSafe: Boolean,
    val safeReplacement: Version?,
    val dependency: Dependency,
    val isResolved: Boolean,
    val cookie: Any,
  )

  /** Stores information for a check of the Android gradle plugin dependency version. */
  private var agpVersionCheckInfo: AgpVersionCheckInfo? = null

  private val blockedDependencies = HashMap<Project, BlockedDependencies>()

  // ---- Implements XmlScanner ----

  override fun getApplicableAttributes(): Collection<String>? {
    return listOf(ATTR_TARGET_SDK_VERSION)
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (attribute.namespaceURI != ANDROID_URI) {
      return
    }

    val element = attribute.ownerElement
    val target = attribute.value
    try {
      val targetSdkVersion = target.toInt()
      val location = context.getLocation(attribute)
      when (
        val tsdk =
          checkTargetSdk(
            context,
            ManifestDetector.calendar ?: Calendar.getInstance(),
            targetSdkVersion,
          )
      ) {
        is TargetSdkCheckResult.Expired -> {
          context.report(
            EXPIRED_TARGET_SDK_VERSION,
            element,
            location,
            tsdk.message,
            targetSdkLintFix(targetSdkVersion, tsdk.requiredVersion),
          )
        }
        is TargetSdkCheckResult.Expiring -> {
          context.report(
            EXPIRING_TARGET_SDK_VERSION,
            element,
            location,
            tsdk.message,
            targetSdkLintFix(targetSdkVersion, tsdk.requiredVersion),
          )
        }
        is TargetSdkCheckResult.NotLatest -> {
          if (context.isEnabled(TARGET_NEWER)) {
            context.report(
              TARGET_NEWER,
              element,
              location,
              tsdk.message,
              targetSdkLintFix(targetSdkVersion, tsdk.highestVersion),
            )
          }
        }
        is TargetSdkCheckResult.NoIssue -> {}
      }
    } catch (ignore: NumberFormatException) {
      // Ignore: AAPT will enforce this.
    }
  }

  private fun targetSdkLintFix(current: Int, target: Int) =
    if (LintClient.isStudio) {
      fix().data("currentTargetSdkVersion", current)
    } else {
      fix()
        .name("Update targetSdkVersion to $target")
        .set(ANDROID_URI, ATTR_TARGET_SDK_VERSION, target.toString())
        .build()
    }

  // ---- Implements GradleScanner ----

  private fun checkOctal(context: GradleContext, value: String, cookie: Any) {
    // (This will never be the case in KTS; if you try to insert "010" as an integer in Kotlin, you
    // get a compiler error, "Unsupported [literal prefixes and suffixes]".)
    if (
      value.length >= 2 &&
        value[0] == '0' &&
        (value.length > 2 || value[1] >= '8' && isNonNegativeInteger(value)) &&
        context.isEnabled(ACCIDENTAL_OCTAL)
    ) {
      var message =
        "The leading 0 turns this number into octal which is probably not what was intended"
      message +=
        try {
          val numericValue = java.lang.Long.decode(value)
          " (interpreted as $numericValue)"
        } catch (exception: NumberFormatException) {
          " (and it is not a valid octal number)"
        }

      report(context, cookie, ACCIDENTAL_OCTAL, message)
    }
  }

  /** Called with for example "android", "defaultConfig", "minSdkVersion", "7" */
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
    if (
      parent == "defaultConfig" || (isPrivacySandboxSdk(context.project) && parent == "android")
    ) {
      if (property == "targetSdkVersion" || property == "targetSdk") {
        val version = getSdkVersion(value, valueCookie)
        if (version > 0 && version < context.client.highestKnownApiLevel) {
          when (val tsdk = checkTargetSdk(context, calendar ?: Calendar.getInstance(), version)) {
            is TargetSdkCheckResult.Expired -> {
              // Don't report if already suppressed with EXPIRING
              val alreadySuppressed =
                context.containsCommentSuppress() &&
                  context.isSuppressedWithComment(statementCookie, EXPIRED_TARGET_SDK_VERSION)

              if (!alreadySuppressed) {
                report(
                  context,
                  statementCookie,
                  EXPIRED_TARGET_SDK_VERSION,
                  tsdk.message,
                  fix().data("currentTargetSdkVersion", version).takeIf { LintClient.isStudio },
                )
              }
            }
            is TargetSdkCheckResult.Expiring -> {
              report(
                context,
                statementCookie,
                EXPIRING_TARGET_SDK_VERSION,
                tsdk.message,
                fix().data("currentTargetSdkVersion", version).takeIf { LintClient.isStudio },
              )
            }
            is TargetSdkCheckResult.NotLatest -> {
              val highest = tsdk.highestVersion
              val label = "Update targetSdkVersion to $highest"
              val fix =
                if (LintClient.isStudio) {
                  fix().data("currentTargetSdkVersion", version)
                } else {
                  fix().name(label).replace().text(value).with(highest.toString()).build()
                }
              report(context, statementCookie, TARGET_NEWER, tsdk.message, fix)
            }
            is TargetSdkCheckResult.NoIssue -> {}
          }
        }
        if (version > 0) {
          if (LintClient.isStudio) {
            //noinspection FileComparisons
            if (lastTargetSdkVersion == -1 || lastTargetSdkVersionFile != context.file) {
              lastTargetSdkVersion = version
              lastTargetSdkVersionFile = context.file
            } else if (version > lastTargetSdkVersion) {
              val message =
                "It looks like you just edited the `targetSdkVersion` from $lastTargetSdkVersion to $version in the editor. " +
                  "Be sure to consult the documentation on the behaviors that change as result of this. " +
                  "The Android SDK Upgrade Assistant can help with safely migrating."
              report(
                context,
                statementCookie,
                EDITED_TARGET_SDK_VERSION,
                message,
                fix().data("currentTargetSdkVersion", version),
              )
            }
          }
        } else {
          checkIntegerAsString(context, value, statementCookie, valueCookie)
        }
      } else if (property == "minSdkVersion" || property == "minSdk") {
        val version = getSdkVersion(value, valueCookie)
        if (version > 0) {
          checkMinSdkVersion(context, version, statementCookie)
        } else {
          checkIntegerAsString(context, value, statementCookie, valueCookie)
        }
      }

      if (value.startsWith("0")) {
        checkOctal(context, value, valueCookie)
      }

      if (
        property == "versionName" ||
          property == "versionCode" && !isNonNegativeInteger(value) ||
          !isStringLiteral(value)
      ) {
        // Method call -- make sure it does not match one of the getters in the
        // configuration!
        if (value == "getVersionCode" || value == "getVersionName") {
          val message =
            "Bad method name: pick a unique method name which does not " +
              "conflict with the implicit getters for the defaultConfig " +
              "properties. For example, try using the prefix compute- " +
              "instead of get-."
          report(context, statementCookie, GRADLE_GETTER, message)
        }
      } else if (property == "packageName") {
        val message = "Deprecated: Replace 'packageName' with 'applicationId'"
        val fix =
          fix()
            .name("Replace 'packageName' with 'applicationId'", true)
            .replace()
            .text("packageName")
            .with("applicationId")
            .autoFix()
            .build()
        report(context, propertyCookie, DEPRECATED, message, fix)
      }
      if (
        property == "versionCode" &&
          context.isEnabled(HIGH_APP_VERSION_CODE) &&
          isNonNegativeInteger(value)
      ) {
        val version = getIntLiteralValue(value, -1)
        if (version >= VERSION_CODE_HIGH_THRESHOLD) {
          val message = "The 'versionCode' is very high and close to the max allowed value"
          report(context, statementCookie, HIGH_APP_VERSION_CODE, message)
        }
      }
    } else if (
      (property == "compileSdkVersion" || property == "compileSdk") && parent.startsWith("android")
    ) {
      var version = -1
      if (isStringLiteral(value)) {
        // Try to resolve values like "android-O"
        val hash = getStringLiteralValue(value, valueCookie)
        if (hash != null && !isNumberString(hash)) {
          if (property == "compileSdk") {
            val message =
              "`compileSdk` does not support strings; did you mean `compileSdkPreview` ?"
            val fix = fix().replace().text("compileSdk").with("compileSdkPreview").build()
            report(context, statementCookie, STRING_INTEGER, message, fix)
          }

          val platformVersion = AndroidTargetHash.getPlatformVersion(hash)
          if (platformVersion != null) {
            version = platformVersion.featureLevel
          }
        }
      } else {
        version = getIntLiteralValue(value, -1)
      }
      if (version <= 0) {
        checkIntegerAsString(context, value, statementCookie, valueCookie)
      } else if (version < HIGHEST_KNOWN_STABLE_API) {
        val message =
          "A newer version of `compileSdkVersion` than $version is available: $HIGHEST_KNOWN_STABLE_API"
        val fix =
          fix()
            .name("Set compileSdkVersion to $HIGHEST_KNOWN_STABLE_API")
            .replace()
            .text(version.toString())
            .with(HIGHEST_KNOWN_STABLE_API.toString())
            .build()
        val clientProperties =
          getClientProperties()?.apply { put(KEY_COORDINATE, "compileSdkVersion") }
        report(
          context,
          statementCookie,
          DEPENDENCY,
          message,
          fix,
          clientProperties = clientProperties,
        )
      }
    } else if (parent == "plugins") {
      val plugin =
        when (property) {
          "id" -> getStringLiteralValue(value, valueCookie)
          "alias" -> getPluginFromVersionCatalog(value, context)?.coordinates?.substringBefore(':')
          else -> null
        }

      when (plugin) {
        null -> {
          // Ignore, we couldn't find a plugin ID
        }
        "kotlin-android",
        "org.jetbrains.kotlin.android" -> {
          mAppliedKotlinAndroidPlugin = true
        }
        "kotlin-kapt",
        "org.jetbrains.kotlin.kapt" -> {
          mAppliedKotlinKaptPlugin = true
        }
        "com.google.devtools.ksp" -> {
          mAppliedKspPlugin = true
        }
        in JAVA_PLUGIN_IDS -> {
          mAppliedJavaPlugin = true
          mJavaPluginInfo = JavaPluginInfo(statementCookie)
        }
        OLD_APP_PLUGIN_ID,
        OLD_LIB_PLUGIN_ID -> {
          val isOldAppPlugin = OLD_APP_PLUGIN_ID == plugin
          val replaceWith = if (isOldAppPlugin) APP_PLUGIN_ID else LIB_PLUGIN_ID
          val message = "'$plugin' is deprecated; use '$replaceWith' instead"
          val fix =
            fix()
              .sharedName("Replace plugin")
              .replace()
              .text(plugin)
              .with(replaceWith)
              .autoFix()
              .build()
          report(context, valueCookie, DEPRECATED, message, fix)
        }
      }
    } else if (parentParent == "plugins" && property == "version") {
      val version = getStringLiteralValue(value, valueCookie)
      if (version != null) {
        val gradleCoordinate = "$parent:$parent.gradle.plugin:$version"
        val dependency = Dependency.parse(gradleCoordinate)
        // Check dependencies without the PSI read lock, because we
        // may need to make network requests to retrieve version info.
        context.driver.runLaterOutsideReadAction {
          checkDependency(context, dependency, false, valueCookie, statementCookie)
        }
      }
    } else if (parent == "dependencies" || parent == "declarativeDependencies") {
      if (value.startsWith("files") && value.matches("^files\\(['\"].*[\"']\\)$".toRegex())) {
        val path = value.substring("files('".length, value.length - 2)
        if (path.contains("\\\\")) {
          val fix = fix().replace().text(path).with(path.replace("\\\\", "/")).build()
          val message = "Do not use Windows file separators in .gradle files; use / instead"
          report(context, valueCookie, PATH, message, fix)
        } else if (path.startsWith("/") || File(path.replace('/', File.separatorChar)).isAbsolute) {
          val message = "Avoid using absolute paths in .gradle files"
          report(context, valueCookie, PATH, message)
        }
        checkDeprecatedConfigurations(property, context, propertyCookie)
      } else {
        var dependencyString = getStringLiteralValue(value, valueCookie)
        if (
          dependencyString == null &&
            (listOf("platform", "testFixtures", "enforcedPlatform").any {
              value.startsWith("$it(")
            } && value.endsWith(")"))
        ) {
          val argumentString = value.substring(value.indexOf('(') + 1, value.length - 1)
          dependencyString =
            if (valueCookie is UCallExpression && valueCookie.valueArguments.size == 1) {
              getStringLiteralValue(argumentString, valueCookie.valueArguments.first())
            } else {
              getStringLiteralValue(argumentString, valueCookie)
            }
        }
        if (dependencyString == null) {
          dependencyString = getNamedDependency(value)
        }
        // If the dependency is a GString (i.e. it uses Groovy variable substitution,
        // with a $variable_name syntax) then don't try to parse it.
        if (dependencyString != null) {
          var dependency: Dependency? = Dependency.parse(dependencyString)
          var isResolved = false
          if (dependency != null && dependency.version?.toIdentifier()?.contains("$") == true) {
            if (
              value.startsWith("'") && value.endsWith("'") && context.isEnabled(NOT_INTERPOLATED)
            ) {
              val message =
                "It looks like you are trying to substitute a " +
                  "version variable, but using single quotes ('). For Groovy " +
                  "string interpolation you must use double quotes (\")."
              val fix =
                fix()
                  .name("Replace single quotes with double quotes")
                  .replace()
                  .text(value)
                  .with("\"" + value.substring(1, value.length - 1) + "\"")
                  .build()
              report(context, statementCookie, NOT_INTERPOLATED, message, fix)
            }

            dependency = resolveCoordinate(context, property, dependency)
            isResolved = true
          } else if (dependency?.version?.toIdentifier()?.let { !value.contains(it) } != false) {
            isResolved = true
          }
          if (dependency != null) {
            if (
              dependency.version?.run { require ?: strictly }?.toIdentifier()?.endsWith("+") == true
            ) {
              val message =
                "Avoid using + in version numbers; can lead " +
                  "to unpredictable and unrepeatable builds (" +
                  dependencyString +
                  ")"
              val fix =
                fix()
                  .data(
                    KEY_COORDINATE,
                    dependency.toString(),
                    KEY_REVISION,
                    dependency.version?.toIdentifier(),
                  )
              report(context, valueCookie, PLUS, message, fix)
            }

            val tomlLibraries = context.getTomlValue(VC_LIBRARIES)
            if (
              tomlLibraries != null &&
                !context.file.name.startsWith("settings.gradle") &&
                !context.file.name.endsWith(".gradle.dcl") &&
                !dependencyString.contains("+") &&
                (!dependencyString.contains("$") || isResolved) &&
                dependency.group?.isNotBlank() == true
            ) {
              val versionVar = getVersionVariable(value)
              val result =
                createMoveToTomlFix(context, tomlLibraries, dependency, valueCookie, versionVar)
              if (result != null) {
                val message = result.first ?: "Use version catalog instead"
                val fix = result.second
                report(context, valueCookie, SWITCH_TO_TOML, message, fix)
              }
            }

            // Check dependencies without the PSI read lock, because we
            // may need to make network requests to retrieve version info.
            context.driver.runLaterOutsideReadAction {
              checkDependency(context, dependency, isResolved, valueCookie, statementCookie)
            }
          }
          if (
            hasLifecycleAnnotationProcessor(dependencyString) && targetJava8Plus(context.project)
          ) {
            report(
              context,
              valueCookie,
              LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8,
              "Use the Lifecycle Java 8 API provided by the " +
                "`lifecycle-common` library instead of Lifecycle annotations " +
                "for faster incremental build.",
              null,
            )
          }
          checkAnnotationProcessorOnCompilePath(property, dependencyString, context, propertyCookie)
        }
        checkDeprecatedConfigurations(property, context, propertyCookie)

        // If we haven't managed to parse the dependency yet, try getting it from version catalog
        var libTomlValue: LintTomlValue? = null
        if (dependencyString == null) {
          val dependencyFromVc = getDependencyFromVersionCatalog(value, context)
          if (dependencyFromVc != null) {
            dependencyString = dependencyFromVc.coordinates
            libTomlValue = dependencyFromVc.tomlValue
          }
        }

        if (dependencyString != null) {
          if (property == "kapt") {
            checkKaptUsage(dependencyString, libTomlValue, context, statementCookie)
          }
          checkForBomUsageWithoutPlatform(property, dependencyString, value, context, valueCookie)
        }
      }
    } else if (property == "packageNameSuffix") {
      val message = "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"
      val fix =
        fix()
          .name("Replace 'packageNameSuffix' with 'applicationIdSuffix'", true)
          .replace()
          .text("packageNameSuffix")
          .with("applicationIdSuffix")
          .autoFix()
          .build()
      report(context, propertyCookie, DEPRECATED, message, fix)
    } else if (property == "applicationIdSuffix") {
      val suffix = getStringLiteralValue(value, valueCookie)
      if (suffix != null && !suffix.startsWith(".")) {
        val message = "Application ID suffix should probably start with a \".\""
        report(context, statementCookie, PATH, message)
      }
    } else if (
      (property == "minSdkVersion" || property == "minSdk") &&
        parent == "dev" &&
        "21" == value &&
        // Don't flag this error from Gradle; users invoking lint from Gradle may
        // still want dev mode for command line usage
        LintClient.CLIENT_GRADLE != LintClient.clientName
    ) {
      report(
        context,
        statementCookie,
        DEV_MODE_OBSOLETE,
        "You no longer need a `dev` mode to enable multi-dexing during development, and this can break API version checks",
      )
    } else if (
      parent == "dataBinding" && ((property == "enabled" || property == "isEnabled")) ||
        (parent == "buildFeatures" && property == "dataBinding")
    ) {
      // Note: "enabled" is used by build.gradle and "isEnabled" is used by build.gradle.kts
      if (value == SdkConstants.VALUE_TRUE) {
        if (mAppliedKotlinAndroidPlugin && !mAppliedKotlinKaptPlugin) {
          val message =
            "If you plan to use data binding in a Kotlin project, you should apply the kotlin-kapt plugin."
          report(context, statementCookie, DATA_BINDING_WITHOUT_KAPT, message, null)
        }
      }
    } else if ((parent == "" || parent == "java") && property == "sourceCompatibility") {
      mDeclaredSourceCompatibility = true
    } else if ((parent == "" || parent == "java") && property == "targetCompatibility") {
      mDeclaredTargetCompatibility = true
    } else if (
      property == "include" && parent == "abi" || property == "abiFilters" && parent == "ndk"
    ) {
      checkForChromeOSAbiSplits(context, valueCookie, value)
    } else if (parent == "toolchain" && property == "languageVersion") {
      mDeclaredSourceCompatibility = true
      mDeclaredTargetCompatibility = true
    }
  }

  /**
   * Given a dependency string, returns the name of the version variable, if any, assuming it's a
   * single variable which represents the whole revision. For example, for `foo:bar:$version` and
   * `foo:bar:${version}` and `foo:bar:${version}@jar` it would return "version". For `foo:bar:1.0`
   * or `foo:bar:${version}-alpha` it would return null.
   */
  private fun getVersionVariable(dependency: String): String? {
    if (!dependency.contains("\$")) {
      return null
    }
    var value = dependency.removeSurrounding("'").removeSurrounding("\"").substringAfterLast(':')
    if (value.startsWith("\$")) {
      if (value.startsWith("\${")) {
        val end = value.indexOf('}')
        if (end == -1 || end < value.length - 1 && value[end + 1] != '@') {
          return null
        }
        value = value.removePrefix("\${").removeSuffix("}")
      } else {
        value = value.removePrefix("\$")
      }
    } else {
      return null
    }
    if (value.all { it.isLetter() }) {
      return value
    } else {
      return null
    }
  }

  /**
   * For ChromeOS performance, we want to check if a developer has turned on abiSplits or abiFilters
   * as they target specific ABIs. If the developer has included `x86_64` no warning will show.
   * However, if it is missing, the warning will pop up.
   *
   * If the user has not included `abiSplits` or `abiFilters` this logic will not be called.
   */
  private fun checkForChromeOSAbiSplits(context: GradleContext, valueCookie: Any, value: String) {
    val abis = value.split(',')
    var hasX8664 = false
    for (i in abis.indices) {
      if (abis[i].contains("\"x86_64\"") || abis[i].contains("\'x86_64\'")) {
        hasX8664 = true
      }
    }

    val message: String? =
      if (!hasX8664) {
        "Missing x86_64 ABI support for ChromeOS"
      } else {
        null
      }

    message?.let { m -> report(context, valueCookie, CHROMEOS_ABI_SUPPORT, m) }
  }

  private enum class DeprecatedConfiguration(
    private val deprecatedName: String,
    private val replacementName: String,
  ) {
    COMPILE("compile", "implementation"),
    PROVIDED("provided", "compileOnly"),
    APK("apk", "runtimeOnly");

    private val deprecatedSuffix: String = deprecatedName.usLocaleCapitalize()
    private val replacementSuffix: String = replacementName.usLocaleCapitalize()

    fun matches(configurationName: String): Boolean {
      return configurationName == deprecatedName || configurationName.endsWith(deprecatedSuffix)
    }

    fun replacement(configurationName: String): String {
      return if (configurationName == deprecatedName) {
        replacementName
      } else {
        configurationName.removeSuffix(deprecatedSuffix) + replacementSuffix
      }
    }
  }

  private fun checkDeprecatedConfigurations(
    configuration: String,
    context: GradleContext,
    propertyCookie: Any,
  ) {
    if (context.project.gradleModelVersion?.isAtLeastIncludingPreviews(3, 0, 0) == false) {
      // All of these deprecations were made in AGP 3.0.0
      return
    }

    for (deprecatedConfiguration in DeprecatedConfiguration.values()) {
      if (deprecatedConfiguration.matches(configuration)) {
        // Compile was replaced by API and Implementation, but only suggest API if it was used
        if (
          deprecatedConfiguration == DeprecatedConfiguration.COMPILE &&
            suggestApiConfigurationUse(context.project, configuration)
        ) {
          val implementation: String
          val api: String
          if (configuration == "compile") {
            implementation = "implementation"
            api = "api"
          } else {
            val prefix = configuration.removeSuffix("Compile")
            implementation = "${prefix}Implementation"
            api = "${prefix}Api"
          }

          val message =
            "`$configuration` is deprecated; " +
              "replace with either `$api` to maintain current behavior, " +
              "or `$implementation` to improve build performance " +
              "by not sharing this dependency transitively."
          val apiFix =
            fix()
              .name("Replace '$configuration' with '$api'")
              .family("Replace compile with api")
              .replace()
              .text(configuration)
              .with(api)
              .autoFix()
              .build()
          val implementationFix =
            fix()
              .name("Replace '$configuration' with '$implementation'")
              .family("Replace compile with implementation")
              .replace()
              .text(configuration)
              .with(implementation)
              .autoFix()
              .build()

          val fixes =
            fix()
              .alternatives()
              .name("Replace '$configuration' with '$api' or '$implementation'")
              .add(apiFix)
              .add(implementationFix)
              .build()

          report(context, propertyCookie, DEPRECATED_CONFIGURATION, message, fixes)
        } else {
          // Unambiguous replacement case
          val replacement = deprecatedConfiguration.replacement(configuration)
          val message = "`$configuration` is deprecated; replace with `$replacement`"
          val fix =
            fix()
              .name("Replace '$configuration' with '$replacement'")
              .family("Replace deprecated configurations")
              .replace()
              .text(configuration)
              .with(replacement)
              .autoFix()
              .build()
          report(context, propertyCookie, DEPRECATED_CONFIGURATION, message, fix)
        }
      }
    }
  }

  private fun checkAnnotationProcessorOnCompilePath(
    configuration: String,
    dependency: String,
    context: GradleContext,
    propertyCookie: Any,
  ) {
    for (compileConfiguration in CompileConfiguration.values()) {
      if (compileConfiguration.matches(configuration) && isCommonAnnotationProcessor(dependency)) {
        val replacement: String = compileConfiguration.replacement(configuration)
        val fix =
          fix()
            .name("Replace $configuration with $replacement")
            .family("Replace compile classpath with annotationProcessor")
            .replace()
            .text(configuration)
            .with(replacement)
            .autoFix()
            .build()
        val message =
          "Add annotation processor to processor path using `$replacement`" +
            " instead of `$configuration`"
        report(context, propertyCookie, ANNOTATION_PROCESSOR_ON_COMPILE_PATH, message, fix)
      }
    }
  }

  private fun checkMinSdkVersion(context: GradleContext, version: Int, valueCookie: Any) {
    if (version in 1 until LOWEST_ACTIVE_API) {
      val message =
        "The value of minSdkVersion is too low. It can be incremented " +
          "without noticeably reducing the number of supported devices."

      val label = "Update minSdkVersion to $LOWEST_ACTIVE_API"
      val fix =
        fix()
          .name(label)
          .replace()
          .text(version.toString())
          .with(LOWEST_ACTIVE_API.toString())
          .build()
      report(context, valueCookie, MIN_SDK_TOO_LOW, message, fix)
    }
  }

  private fun checkIntegerAsString(
    context: GradleContext,
    value: String,
    cookie: Any,
    valueCookie: Any,
  ) {
    // When done developing with a preview platform you might be tempted to switch from
    //     compileSdkVersion 'android-G'
    // to
    //     compileSdkVersion '19'
    // but that won't work; it needs to be
    //     compileSdkVersion 19
    val string = getStringLiteralValue(value, valueCookie)
    if (isNumberString(string)) {
      val message = "Use an integer rather than a string here (replace $value with just $string)"
      val fix = fix().name("Replace with integer", true).replace().text(value).with(string).build()
      report(context, cookie, STRING_INTEGER, message, fix)
    }
  }

  override fun checkMethodCall(
    context: GradleContext,
    statement: String,
    parent: String?,
    parentParent: String?,
    namedArguments: Map<String, String>,
    unnamedArguments: List<String>,
    cookie: Any,
  ) {
    val plugin = namedArguments["plugin"]
    if (statement == "apply" && parent == null) {
      val isOldAppPlugin = OLD_APP_PLUGIN_ID == plugin
      if (isOldAppPlugin || OLD_LIB_PLUGIN_ID == plugin) {
        val replaceWith = if (isOldAppPlugin) APP_PLUGIN_ID else LIB_PLUGIN_ID
        val message = "'$plugin' is deprecated; use '$replaceWith' instead"
        val fix =
          fix()
            .sharedName("Replace plugin")
            .replace()
            .text(plugin)
            .with(replaceWith)
            .autoFix()
            .build()
        report(context, cookie, DEPRECATED, message, fix)
      }

      if (plugin == "kotlin-android") {
        mAppliedKotlinAndroidPlugin = true
      }
      if (plugin == "kotlin-kapt") {
        mAppliedKotlinKaptPlugin = true
      }
      if (plugin == "com.google.devtools.ksp") {
        mAppliedKspPlugin = true
      }
      if (JAVA_PLUGIN_IDS.contains(plugin)) {
        mAppliedJavaPlugin = true
        mJavaPluginInfo = JavaPluginInfo(cookie)
      }
    }
    if (parent == "repositories" && parentParent == "buildscript") {
      if (statement == "google") {
        mDeclaredGoogleMavenRepository = true
      }
      mDeclaredBuildscriptRepository = true
    }
    if (statement == "jcenter" && parent == "repositories") {
      val message =
        "JCenter Maven repository is no longer receiving updates: newer library versions may be available elsewhere"
      val replaceFix =
        fix()
          .name("Replace with mavenCentral")
          .replace()
          .text("jcenter")
          .with("mavenCentral")
          .build()
      val deleteFix =
        fix().name("Delete this repository declaration").replace().all().with("").build()
      report(
        context,
        cookie,
        JCENTER_REPOSITORY_OBSOLETE,
        message,
        fix().alternatives(replaceFix, deleteFix),
      )
    }
  }

  // Important: This is called without the PSI read lock, since it may make network requests.
  // Any interaction with PSI or issue reporting should be wrapped in a read action.
  private fun checkDependency(
    context: Context,
    dependency: Dependency,
    isResolved: Boolean,
    cookie: Any,
    statementCookie: Any,
  ) {
    val version = dependency.version?.lowerBound ?: return
    val groupId = dependency.group ?: return
    val artifactId = dependency.name
    val richVersionIdentifier = dependency.version?.toIdentifier() ?: return
    var safeReplacement: Version? = null
    var newerVersion: Version? = null

    val sdkIndex = getGooglePlaySdkIndex(context.client)
    val versionFilter = getUpgradeVersionFilter(context, groupId, artifactId, version)
    val recommendedVersions = sdkIndex.recommendedVersions(groupId, artifactId, version.toString())
    val sdkIndexFilter =
      getGooglePlaySdkIndexFilter(context, groupId, artifactId, recommendedVersions, sdkIndex)
    fun Predicate<Version>?.and(other: Predicate<Version>?): Predicate<Version>? =
      when {
        this != null && other != null -> this.and(other)
        else -> this ?: other
      }
    val filter = versionFilter.and(sdkIndexFilter)

    when (groupId) {
      GMS_GROUP_ID,
      FIREBASE_GROUP_ID,
      GOOGLE_SUPPORT_GROUP_ID,
      ANDROID_WEAR_GROUP_ID -> {
        // Play services

        checkPlayServices(context, dependency, version, cookie, statementCookie)
      }
      "com.android.base",
      "com.android.application",
      "com.android.library",
      "com.android.test",
      "com.android.instant-app",
      "com.android.feature",
      "com.android.dynamic-feature",
      "com.android.settings",
      "com.android.tools.build" -> {
        if ("gradle" == artifactId || "$groupId.gradle.plugin" == artifactId) {
          if (checkGradlePluginDependency(context, dependency, statementCookie)) {
            return
          }

          // If it's available in maven.google.com, fetch latest available version
          newerVersion =
            newerVersion maxAgpOrNull getGoogleMavenRepoVersion(context, dependency, filter)

          // Compare with what's in the Gradle cache, except when lint is invoked from
          // Gradle (because checking the Gradle cache is incompatible with Gradle task
          // cacheability).
          if (!LintClient.isGradle) {
            newerVersion = newerVersion maxAgpOrNull findCachedNewerVersion(dependency, filter)
          }

          // Don't just offer the latest available version, but if that is more than
          // a micro-level different, and there is a newer micro version of the
          // version that the user is currently using, offer that one as well as it
          // may be easier to upgrade to.
          if (
            newerVersion != null &&
              !version.isPreview &&
              newerVersion != version &&
              (version.major != newerVersion.major || version.minor != newerVersion.minor)
          ) {
            safeReplacement =
              getGoogleMavenRepoVersion(context, dependency) { filterVersion ->
                filterVersion.major != null &&
                  filterVersion.major == version.major &&
                  filterVersion.minor != null &&
                  filterVersion.minor == version.minor &&
                  filterVersion.micro?.let { m -> version.micro?.let { m > it } } == true &&
                  !filterVersion.isPreview &&
                  filterVersion < newerVersion!! &&
                  !filterVersion.isSnapshot
              }
          }
          if (newerVersion != null && newerVersion.isAgpNewerThan(dependency)) {
            agpVersionCheckInfo =
              AgpVersionCheckInfo(
                newerVersion,
                newerVersion.major == version.major && newerVersion.minor == version.minor,
                safeReplacement,
                dependency,
                isResolved,
                cookie,
              )
            maybeReportAgpVersionIssue(context)
          }
          return
        }
      }
      "com.android.tools" -> {
        // Only desugar_jdk_libs has version 1; desugar_jdk_libs_nio and desugar_jdk_libs_minimal
        // starts with v2
        if (
          artifactId == "desugar_jdk_libs" &&
            checkCoreLibraryDesugaringCompatibility(context, dependency, cookie)
        ) {
          return
        }
      }
      "com.google.guava" -> {
        // TODO: 24.0-android
        if ("guava" == artifactId) {
          newerVersion = getNewerVersion(version, 21, 0)
        }
      }
      "com.google.code.gson" -> {
        if ("gson" == artifactId) {
          newerVersion = getNewerVersion(version, 2, 8, 2)
        }
      }
      "org.apache.httpcomponents" -> {
        if ("httpclient" == artifactId) {
          newerVersion = getNewerVersion(version, 4, 5, 5)
        }
      }
      "com.squareup.okhttp3" -> {
        if ("okhttp" == artifactId) {
          newerVersion = getNewerVersion(version, 3, 10, 0)
        }
      }
      "com.github.bumptech.glide" -> {
        if ("glide" == artifactId) {
          newerVersion = getNewerVersion(version, 3, 7, 0)
        }
      }
      "io.fabric.tools" -> {
        if ("gradle" == artifactId) {
          if (Version.parse("1.21.6").isNewerThan(dependency)) {
            val fix = getUpdateDependencyFix(richVersionIdentifier, "1.22.1")
            report(
              context,
              statementCookie,
              DEPENDENCY,
              "Use Fabric Gradle plugin version 1.21.6 or later to " +
                "improve Instant Run performance (was $richVersionIdentifier)",
              fix,
            )
          } else {
            // From
            // https://s3.amazonaws.com/fabric-artifacts/public/io/fabric/tools/gradle/maven-metadata.xml
            newerVersion = getNewerVersion(version, 1, 25, 1)
          }
        }
      }
      "com.bugsnag" -> {
        if ("bugsnag-android-gradle-plugin" == artifactId) {
          if (version < Version.parse("2.1.2")) {
            val fix = getUpdateDependencyFix(richVersionIdentifier, "2.4.1")
            report(
              context,
              statementCookie,
              DEPENDENCY,
              "Use BugSnag Gradle plugin version 2.1.2 or later to " +
                "improve Instant Run performance (was $richVersionIdentifier)",
              fix,
            )
          } else {
            // From http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.bugsnag%22%20AND
            // %20a%3A%22bugsnag-android-gradle-plugin%22
            newerVersion = getNewerVersion(version, 3, 2, 5)
          }
        }
      }

      // https://issuetracker.google.com/120098460
      "org.robolectric" -> {
        if ("robolectric" == artifactId && currentPlatform() == PLATFORM_WINDOWS) {
          if (version < Version.parse("4.2.1")) {
            val fix = getUpdateDependencyFix(richVersionIdentifier, "4.2.1")
            report(
              context,
              cookie,
              DEPENDENCY,
              "Use robolectric version 4.2.1 or later to " +
                "fix issues with parsing of Windows paths",
              fix,
            )
          }
        }
      }

      // TODO: This is a hotfix to suppress Kotlin version warnings in Compose projects
      // (b/194313332)
      //  and it should be removed eventually.
      "org.jetbrains.kotlin" -> {
        if (artifactId == "kotlin-gradle-plugin") {
          return
        }
      }
    }

    checkForKtxExtension(context, groupId, artifactId, version, cookie)

    val blockedDependencies = blockedDependencies[context.project]
    if (blockedDependencies != null) {
      val path = blockedDependencies.checkDependency(groupId, artifactId, true)
      if (path != null) {
        val message = getBlockedDependencyMessage(path)
        val fix = fix().name("Delete dependency").replace().all().build()
        // Provisional: have to check consuming app's targetSdkVersion
        report(context, statementCookie, DUPLICATE_CLASSES, message, fix, partial = true)
      }
    }

    // Network check for really up to date libraries? Only done in batch mode.
    var issue = DEPENDENCY
    if (
      !Scope.checkSingleFile(context.scope) &&
        context.isEnabled(REMOTE_VERSION) &&
        // Common but served from maven.google.com so no point to
        // ping other maven repositories about these
        !getGoogleMavenRepository(context.client).hasGroupId(groupId)
    ) {
      val latest =
        getLatestVersionFromRemoteRepo(
          context.client,
          dependency,
          filter,
          dependency.version?.lowerBound?.isPreview ?: true,
        )
      if (latest != null && version < latest) {
        newerVersion = latest
        issue = REMOTE_VERSION
      }

      val group = dependency.group
      if (group != null && dependency.isGradlePlugin()) {
        val pluginVersion =
          getLatestVersionFromGradlePluginPortal(
            context.client,
            group,
            filter,
            dependency.version?.lowerBound?.isPreview ?: true,
          )
        if (pluginVersion != null && version < pluginVersion) {
          newerVersion = pluginVersion
          issue = REMOTE_VERSION
        }
      }
    }

    // Compare with what's in the Gradle cache.
    newerVersion = newerVersion maxOrNull findCachedNewerVersion(dependency, filter)

    // If it's available in maven.google.com, fetch latest available version.
    newerVersion = newerVersion maxOrNull getGoogleMavenRepoVersion(context, dependency, filter)

    // And also consider what is in the SDK Index
    newerVersion = newerVersion maxOrNull getSdkIndexVersion(sdkIndex, groupId, artifactId, filter)

    val hasSdkIndexIssues =
      generateAndReportSdkIndexIssues(
        sdkIndex,
        groupId,
        artifactId,
        version,
        richVersionIdentifier,
        if (!isResolved) newerVersion else null,
        context,
        cookie,
      )

    if (
      newerVersion != null &&
        version > Version.prefixInfimum("0") &&
        newerVersion.isNewerThan(dependency)
    ) {
      val versionString = newerVersion.toString()
      var isCustomMessage = true
      var message =
        if (
          dependency.group == "androidx.slidingpanelayout" && dependency.name == "slidingpanelayout"
        ) {
          "Upgrade `androidx.slidingpanelayout` for keyboard and mouse support"
        } else if (
          dependency.group == "androidx.compose.foundation" && dependency.name == "foundation"
        ) {
          "Upgrade `androidx.compose.foundation` for keyboard and mouse support"
        } else {
          getNewerVersionAvailableMessage(dependency, versionString, null).also {
            isCustomMessage = false
          }
        }

      // Add details for play-services-maps.
      if (
        dependency.group == "com.google.android.gms" &&
          dependency.name == "play-services-maps" &&
          Version.parse("18.2.0").let { version < it && newerVersion >= it }
      ) {
        message +=
          ". Upgrading to at least 18.2.0 is highly recommended to take advantage of the new renderer, " +
            "which supports customization options like map styling, 3D tiles, " +
            "and is more reliable, with better support going forward."
        isCustomMessage = true
      }

      // Only show update message if there is a custom message or SDK Index issues not present
      // (b/301316600)
      if (isCustomMessage || !hasSdkIndexIssues) {
        // A quick fix to change the current version was already displayed if there were SDK Index
        // issues, no need to repeat that fix
        val fix =
          if (!isResolved && !hasSdkIndexIssues)
            getUpdateDependencyFix(richVersionIdentifier, versionString)
          else null

        val clientProperties =
          getClientProperties()?.apply { put(KEY_COORDINATE, "$groupId:$artifactId") }

        report(context, cookie, issue, message, fix, clientProperties = clientProperties)
      }
    }
  }

  private fun getClientProperties(): LintMap? =
    if (recordClientProperties) {
      LintMap()
    } else {
      null
    }

  private fun generateAndReportSdkIndexIssues(
    sdkIndex: GooglePlaySdkIndex,
    groupId: String,
    artifactId: String,
    version: Version,
    richVersionIdentifier: String,
    newerVersion: Version?,
    context: Context,
    cookie: Any,
  ): Boolean {
    var reported = false
    if (sdkIndex.isReady()) {
      val versionString = version.toString()
      val buildFile = context.file
      val isBlocking = sdkIndex.hasLibraryBlockingIssues(groupId, artifactId, versionString)
      val severity =
        if (isBlocking) {
          Severity.ERROR
        } else {
          Severity.WARNING
        }
      // Report all SDK Index issues without grouping them following this order (b/316038712):
      //  - Deprecated (first since this applies to all versions and changing to a different version
      // will not remove this issue)
      //  - Policy
      //  - Critical (if blocking)
      //  - Vulnerability
      //  - Outdated
      if (sdkIndex.isLibraryDeprecated(groupId, artifactId, versionString, buildFile)) {
        val fix =
          generateSdkIndexFixes(
            groupId,
            artifactId,
            version,
            richVersionIdentifier,
            null, // Do not suggest a new version
            buildFile,
            sdkIndex,
          )
        val message = sdkIndex.generateDeprecatedMessage(groupId, artifactId)
        reported =
          report(
            context,
            cookie,
            PLAY_SDK_INDEX_DEPRECATED,
            message,
            fix,
            overrideSeverity = severity,
          ) || reported
      }
      if (sdkIndex.isLibraryNonCompliant(groupId, artifactId, versionString, buildFile)) {
        val fix =
          generateSdkIndexFixes(
            groupId,
            artifactId,
            version,
            richVersionIdentifier,
            newerVersion,
            buildFile,
            sdkIndex,
          )
        val messages =
          if (isBlocking) {
            sdkIndex.generateBlockingPolicyMessages(groupId, artifactId, versionString)
          } else {
            sdkIndex.generatePolicyMessages(groupId, artifactId, versionString)
          }
        for (message in messages) {
          reported =
            report(
              context,
              cookie,
              PLAY_SDK_INDEX_NON_COMPLIANT,
              message,
              fix,
              overrideSeverity = severity,
            ) || reported
        }
      }
      if (
        isBlocking &&
          sdkIndex.hasLibraryCriticalIssues(groupId, artifactId, versionString, buildFile)
      ) {
        // Messages from developer that are not-blocking are not shown in lint
        val fix =
          generateSdkIndexFixes(
            groupId,
            artifactId,
            version,
            richVersionIdentifier,
            newerVersion,
            buildFile,
            sdkIndex,
          )
        val message = sdkIndex.generateBlockingCriticalMessage(groupId, artifactId, versionString)
        reported =
          report(context, cookie, RISKY_LIBRARY, message, fix, overrideSeverity = severity) ||
            reported
      }
      if (sdkIndex.hasLibraryVulnerabilityIssues(groupId, artifactId, versionString, buildFile)) {
        val messages = sdkIndex.generateVulnerabilityMessages(groupId, artifactId, versionString)
        for (message in messages) {
          val fix =
            generateSdkIndexFixes(
              groupId,
              artifactId,
              version,
              richVersionIdentifier,
              newerVersion,
              buildFile,
              sdkIndex,
              vulnerabilityMessage = message,
            )
          reported =
            report(
              context,
              cookie,
              PLAY_SDK_INDEX_VULNERABILITY,
              message.description,
              fix,
              overrideSeverity = severity,
            ) || reported
        }
      }
      if (sdkIndex.isLibraryOutdated(groupId, artifactId, versionString, buildFile)) {
        val fix =
          generateSdkIndexFixes(
            groupId,
            artifactId,
            version,
            richVersionIdentifier,
            newerVersion,
            buildFile,
            sdkIndex,
          )
        val message =
          if (isBlocking) {
            sdkIndex.generateBlockingOutdatedMessage(groupId, artifactId, versionString)
          } else {
            sdkIndex.generateOutdatedMessage(groupId, artifactId, versionString)
          }
        reported =
          report(context, cookie, DEPRECATED_LIBRARY, message, fix, overrideSeverity = severity) ||
            reported
      }
    }
    return reported
  }

  private fun generateSdkIndexFixes(
    groupId: String,
    artifactId: String,
    currentVersion: Version,
    currentVersionIdentifier: String,
    newerVersion: Version?,
    buildFile: File,
    sdkIndex: GooglePlaySdkIndex,
    vulnerabilityMessage: VulnerabilityDescription? = null,
  ): LintFix? {
    val fixes = fix().group()
    var empty = true
    if ((newerVersion != null) && (newerVersion > currentVersion)) {
      fixes.add(getUpdateDependencyFix(currentVersionIdentifier, newerVersion.toString()))
      empty = false
    }
    val vulnerabilityLink = vulnerabilityMessage?.link
    if (!vulnerabilityLink.isNullOrBlank()) {
      fixes.add(
        LintFix.ShowUrl(
          "Learn more about ${vulnerabilityMessage.name} vulnerability",
          null,
          vulnerabilityLink,
        )
      )
      empty = false
    }
    val viewMoreLink =
      sdkIndex.generateSdkLinkLintFix(groupId, artifactId, currentVersion.toString(), buildFile)
    if (viewMoreLink != null) {
      fixes.add(viewMoreLink)
      empty = false
    }
    if (empty) {
      return null
    }
    return fixes.build()
  }

  private fun checkDuplication(
    context: Context,
    dependencies: Map<LintTomlValue, Dependency>,
    extractor: (Dependency) -> String, // we can compare libraries by group:name and plugins by group
  ) {
    dependencies.entries
      .toList()
      .groupBy { entry -> extractor(entry.value) }
      .filter { entry -> entry.value.size > 1 }
      .forEach { entry ->
        entry.value.forEach { tuple ->
          val tomlValue = tuple.key
          report(
            context,
            tomlValue,
            MULTIPLE_VERSIONS_DEPENDENCY,
            "There are multiple dependencies ${entry.key} but with different version",
          )
        }
      }
  }

  private fun getGooglePlaySdkIndexFilter(
    context: Context,
    groupId: String,
    artifactId: String,
    recommendedVersions: Collection<LibraryVersionRange>,
    sdkIndex: GooglePlaySdkIndex?,
  ): Predicate<Version>? {
    return sdkIndex?.let {
      // Filter out versions with SDK Index errors or warnings (b/301295995)
      Predicate { v ->
        it.isReady() &&
          (!it.hasLibraryErrorOrWarning(groupId, artifactId, v.toString())) &&
          isRecommendedVersion(v, recommendedVersions)
      }
    }
  }

  private fun isRecommendedVersion(
    version: Version,
    recommendedVersions: Collection<LibraryVersionRange>,
  ): Boolean {
    if (recommendedVersions.isEmpty()) {
      return true
    }
    for (range in recommendedVersions) {
      val lowerVersion = Version.parse(range.lowerBound)
      if (version < lowerVersion) {
        continue
      }
      if (range.upperBound.isNullOrBlank()) {
        return true
      }
      val upperVersion = Version.parse(range.upperBound)
      if (version <= upperVersion) {
        return true
      }
    }
    return false
  }

  /**
   * Returns a predicate that encapsulates version constraints for the given library, or null if
   * there are no constraints.
   */
  private fun getUpgradeVersionFilter(
    context: Context,
    groupId: String,
    artifactId: String,
    version: Version,
  ): Predicate<Version>? {
    if (
      (groupId == "com.android.tools.build" || ALL_PLUGIN_IDS.contains(groupId)) &&
        LintClient.isStudio
    ) {
      val clientRevision = context.client.getClientRevision() ?: return null
      val ideVersion = Version.parse(clientRevision)
      // TODO(b/145606749): this assumes that the IDE version and the AGP version are directly
      //  comparable
      return Predicate { v ->
        // Any higher IDE version that matches major and minor
        // (e.g. from 3.3.0 offer 3.3.2 but not 3.4.0)
        ((v.major == ideVersion.major && v.minor == ideVersion.minor) ||
          // Also allow matching latest current existing major/minor version
          (v.major == version.major && v.minor == version.minor))
      }
    }

    // Some special cases for specific artifacts that were versioned
    // incorrectly (using a string suffix to delineate separate branches
    // whereas Gradle will just use an alphabetical sort on these). See
    // 171369798 for an example.

    // These cases must be considered before the generic logic related to not
    // upgrading to other versions outside a preview series, because these
    // pseudo-version strings look like preview versions even though they're not.
    if (groupId == "com.google.guava") {
      val suffix = version.toString()
      val jre = Predicate<Version> { v -> v.toString().endsWith("-jre") }
      val android = Predicate<Version> { v -> v.toString().endsWith("-android") }
      val neither = Predicate<Version> { v -> !v.toString().endsWith("-jre") }
      return when {
        suffix.endsWith("-jre") -> jre
        suffix.endsWith("-android") -> android
        else -> neither
      }
    } else if (groupId == "org.jetbrains.kotlinx" && artifactId.contains("kotlinx-coroutines")) {
      val suffix = version.toString()
      return when {
        suffix.contains("-native-mt-2") ->
          Predicate<Version> { v -> v.toString().contains("-native-mt-2") }
        suffix.contains("-native-mt") ->
          Predicate<Version> { v ->
            v.toString().run { contains("native-mt") && !contains("native-mt-2") }
          }
        else -> Predicate<Version> { v -> !v.toString().contains("-native-mt") }
      }
    }

    if (version.major != null) {
      // version.major not being null is something of a pun, but sensible anyway:
      // if the whole version is non-numeric, the concept of "the current preview
      // series" doesn't really exist.  It also guards against the fact that the
      // "revision" that we've parsed into a Version isn't known to be a version,
      // and in fact has more of the character of a RichVersion.
      val infimum = version.previewInfimum
      val supremum = version.previewSupremum
      if (infimum != null && supremum != null) {
        return Predicate { v -> (if (v.isPreview) (infimum < v && v < supremum) else true) }
      }
    }

    return null
  }

  /** Home in the Gradle cache for artifact caches. */
  @Suppress("MemberVisibilityCanBePrivate") // overridden in the IDE
  protected fun getArtifactCacheHome(): File {
    return artifactCacheHome
      ?: run {
        val home =
          File(
            gradleUserHome,
            "caches" + File.separator + "modules-2" + File.separator + "files-2.1",
          )
        artifactCacheHome = home
        home
      }
  }

  private fun findCachedNewerVersion(
    dependency: Dependency,
    filter: Predicate<Version>?,
  ): Version? {
    val group = dependency.group ?: return null
    val versionDir =
      getArtifactCacheHome().toPath().resolve(group + File.separator + dependency.name)
    val f =
      when {
        dependency.group?.startsWith("commons-") == true &&
          dependency.name?.startsWith("commons-") == true &&
          dependency.name == dependency.group -> {
          // For a (long) while, users could get this spurious recommendation of an "upgrade" to
          // commons-io, commons-codec etc to this very old version (with a very high version
          // number). This recommendation is no longer given as of mid-2023, except if a
          // user has previously installed it and the version is lurking in their Gradle cache.
          // We need filter out all versions that has 8 digits in major version like: 20030203
          val commonsFilter: Predicate<Version> = Predicate { v -> !v.isOldApacheCommonsVersion() }
          filter?.and(commonsFilter) ?: commonsFilter
        }
        else -> filter
      }
    val noSnapshotFilter: (Version) -> Boolean = { candidate ->
      !candidate.isSnapshot && (f == null || f.test(candidate))
    }
    return if (CancellableFileIo.exists(versionDir)) {
      val name = dependency.name
      val richVersion = dependency.version
      val allowPreview =
        when {
          richVersion == null -> true
          group == "com.google.guava" || name == "kotlinx-coroutines-core" -> true
          else -> MavenRepositories.isPreview(Component(group, name, richVersion.lowerBound))
        }
      MavenRepositories.getHighestVersion(versionDir, noSnapshotFilter, allowPreview)
    } else null
  }

  private fun Version.isOldApacheCommonsVersion() = major?.toString()?.length == 8

  private fun checkCoreLibraryDesugaringCompatibility(
    context: Context,
    dependency: Dependency,
    cookie: Any,
  ): Boolean {
    val version = dependency.version
    if (version != null && version.lowerBound.major == 1) {
      val compileTarget = context.project.buildModule?.compileTarget ?: return false
      val targetVersion = AndroidTargetHash.getPlatformVersion(compileTarget) ?: return false
      if (targetVersion.isAtLeast(35)) {
        val minimum = RichVersion.parse("2.+")
        val artifact = dependency.name
        val group = dependency.group!!
        val query = Dependency(group, artifact, minimum)
        val filter = getUpgradeVersionFilter(context, group, query.name, minimum.lowerBound)
        val suggestedVersion = getGoogleMavenRepoVersion(context, query, filter)
        val message =
          "Core library desugaring runtime library version ${version.lowerBound} does not " +
            "support `compileSdk=35` or later; please upgrade to version $suggestedVersion"
        val fix =
          version.toIdentifier()?.let { getUpdateDependencyFix(it, suggestedVersion.toString()) }
        report(context, cookie, CORE_LIB_DESUGARING_V2, message, fix)
        return true
      }
    }
    return false
  }

  // Important: This is called without the PSI read lock, since it may make network requests.
  // Any interaction with PSI or issue reporting should be wrapped in a read action.
  private fun checkGradlePluginDependency(
    context: Context,
    dependency: Dependency,
    cookie: Any,
  ): Boolean {
    val minimum = Version.parse(GRADLE_PLUGIN_MINIMUM_VERSION)
    val dependencyVersion = dependency.version ?: return false
    if (dependencyVersion.lowerBound >= minimum) return false
    if (!dependencyVersion.contains(minimum)) {
      val query = Dependency("com.android.tools.build", "gradle", RichVersion.require(minimum))
      val recommended =
        Version.parse(GRADLE_PLUGIN_RECOMMENDED_VERSION).let { recommended ->
          getGoogleMavenRepoVersion(context, query, null)?.takeIf { it > recommended }
            ?: recommended
        }
      val message =
        "You must use a newer version of the Android Gradle plugin. The " +
          "minimum supported version is " +
          GRADLE_PLUGIN_MINIMUM_VERSION +
          " and the recommended version is " +
          recommended
      report(context, cookie, GRADLE_PLUGIN_COMPATIBILITY, message)
      return true
    }
    return false
  }

  private fun checkPlayServices(
    context: Context,
    dependency: Dependency,
    version: Version,
    cookie: Any,
    statementCookie: Any,
  ) {
    val groupId = dependency.group ?: return
    val artifactId = dependency.name
    val richVersion = dependency.version ?: return
    val richVersionIdentifier = richVersion.toIdentifier() ?: return

    // 5.2.08 is not supported; special case and warn about this
    if (Version.parse("5.2.08") == version && context.isEnabled(COMPATIBILITY)) {
      // This specific version is actually a preview version which should
      // not be used (https://code.google.com/p/android/issues/detail?id=75292)
      val maxVersion =
        Version.parse("10.2.1").let { v ->
          getGoogleMavenRepoVersion(context, dependency, null)?.takeIf { it > v } ?: v
        }
      val fix = getUpdateDependencyFix(richVersionIdentifier, maxVersion.toString())
      val message =
        "Version `5.2.08` should not be used; the app " +
          "can not be published with this version. Use version `$maxVersion` " +
          "instead."
      reportFatalCompatibilityIssue(context, cookie, message, fix)
    }

    if (
      context.isEnabled(BUNDLED_GMS) &&
        PLAY_SERVICES_V650.group == dependency.group &&
        PLAY_SERVICES_V650.name == dependency.name &&
        (richVersion.lowerBound >= PLAY_SERVICES_V650.version ||
          richVersion.contains(PLAY_SERVICES_V650.version))
    ) {
      // Play services 6.5.0 is the first version to allow un-bundling, so if the user is
      // at or above 6.5.0, recommend un-bundling
      val message = "Avoid using bundled version of Google Play services SDK."
      report(context, cookie, BUNDLED_GMS, message)
    }

    if (GMS_GROUP_ID == groupId && "play-services-appindexing" == artifactId) {
      val message =
        "Deprecated: Replace '" +
          GMS_GROUP_ID +
          ":play-services-appindexing:" +
          richVersionIdentifier +
          "' with 'com.google.firebase:firebase-appindexing:10.0.0' or above. " +
          "More info: http://firebase.google.com/docs/app-indexing/android/migrate"
      val fix =
        fix()
          .name("Replace with Firebase")
          .replace()
          .text("$GMS_GROUP_ID:play-services-appindexing:$richVersionIdentifier")
          .with("com.google.firebase:firebase-appindexing:10.2.1")
          .build()
      report(context, cookie, DEPRECATED, message, fix)
    }

    if (GMS_GROUP_ID == groupId && "play-services-fido" == artifactId) {
      report(
        context,
        cookie,
        DEPENDENCY,
        "Prefer to migrate to the Credential Manager API (androidx.credentials:credentials)",
        fix().url("https://developer.android.com/identity/sign-in/fido2-migration").build(),
      )
      return
    }

    if (GMS_GROUP_ID == groupId || FIREBASE_GROUP_ID == groupId) {
      if (!mCheckedGms) {
        mCheckedGms = true
        // Incremental analysis only? If so, tie the check to
        // a specific GMS play dependency if only, such that it's highlighted
        // in the editor
        if (!context.scope.contains(Scope.ALL_RESOURCE_FILES) && context.isGlobalAnalysis()) {
          // Incremental editing: try flagging them in this file!
          checkConsistentPlayServices(context, cookie)
        }
      }
    } else {
      if (!mCheckedWearableLibs) {
        mCheckedWearableLibs = true
        // Incremental analysis only? If so, tie the check to
        // a specific GMS play dependency if only, such that it's highlighted
        // in the editor
        if (!context.scope.contains(Scope.ALL_RESOURCE_FILES) && context.isGlobalAnalysis()) {
          // Incremental editing: try flagging them in this file!
          checkConsistentWearableLibraries(context, cookie, statementCookie)
        }
      }
    }
  }

  private fun checkConsistentPlayServices(context: Context, cookie: Any?) {
    checkConsistentLibraries(context, cookie, GMS_GROUP_ID, FIREBASE_GROUP_ID)
  }

  private fun checkConsistentWearableLibraries(
    context: Context,
    cookie: Any?,
    statementCookie: Any?,
  ) {
    // Make sure we have both
    //   compile 'com.google.android.support:wearable:2.0.0-alpha3'
    //   provided 'com.google.android.wearable:wearable:2.0.0-alpha3'
    val project = context.mainProject
    if (!project.isGradleProject) {
      return
    }
    val supportVersions = HashSet<String>()
    val wearableVersions = HashSet<String>()
    for (library in getAllLibraries(project).filterIsInstance<LintModelExternalLibrary>()) {
      val coordinates = library.resolvedCoordinates
      if (
        WEARABLE_ARTIFACT_ID == coordinates.artifactId &&
          GOOGLE_SUPPORT_GROUP_ID == coordinates.groupId
      ) {
        supportVersions.add(coordinates.version)
      }

      // Claims to be non-null but may not be after a failed gradle sync
      if (
        WEARABLE_ARTIFACT_ID == coordinates.artifactId &&
          ANDROID_WEAR_GROUP_ID == coordinates.groupId
      ) {
        if (!library.provided) {
          var message = "This dependency should be marked as `compileOnly`, not `compile`"
          if (statementCookie != null) {
            reportFatalCompatibilityIssue(context, statementCookie, message)
          } else {
            val location = getDependencyLocation(context, coordinates)
            if (location.start == null) {
              message =
                "The $ANDROID_WEAR_GROUP_ID:$WEARABLE_ARTIFACT_ID dependency should be marked as `compileOnly`, not `compile`"
            }
            reportFatalCompatibilityIssue(context, location, message)
          }
        }
        wearableVersions.add(coordinates.version)
      }
    }

    if (supportVersions.isNotEmpty()) {
      if (wearableVersions.isEmpty()) {
        val list = ArrayList(supportVersions)
        val first = Collections.min(list)
        val message =
          "Project depends on $GOOGLE_SUPPORT_GROUP_ID:$WEARABLE_ARTIFACT_ID:$first, " +
            "so it must also depend (as a provided dependency) on " +
            "$ANDROID_WEAR_GROUP_ID:$WEARABLE_ARTIFACT_ID:$first"
        if (cookie != null) {
          reportFatalCompatibilityIssue(context, cookie, message)
        } else {
          val location =
            getDependencyLocation(context, GOOGLE_SUPPORT_GROUP_ID, WEARABLE_ARTIFACT_ID, first)
          reportFatalCompatibilityIssue(context, location, message)
        }
      } else {
        // Check that they have the same versions
        if (supportVersions != wearableVersions) {
          val sortedSupportVersions = ArrayList(supportVersions)
          sortedSupportVersions.sort()
          val supportedWearableVersions = ArrayList(wearableVersions)
          supportedWearableVersions.sort()
          val message =
            String.format(
              "The wearable libraries for %1\$s and %2\$s " +
                "must use **exactly** the same versions; found %3\$s " +
                "and %4\$s",
              GOOGLE_SUPPORT_GROUP_ID,
              ANDROID_WEAR_GROUP_ID,
              if (sortedSupportVersions.size == 1) sortedSupportVersions[0]
              else sortedSupportVersions.toString(),
              if (supportedWearableVersions.size == 1) supportedWearableVersions[0]
              else supportedWearableVersions.toString(),
            )
          if (cookie != null) {
            reportFatalCompatibilityIssue(context, cookie, message)
          } else {
            val location =
              getDependencyLocation(
                context,
                GOOGLE_SUPPORT_GROUP_ID,
                WEARABLE_ARTIFACT_ID,
                sortedSupportVersions[0],
                ANDROID_WEAR_GROUP_ID,
                WEARABLE_ARTIFACT_ID,
                supportedWearableVersions[0],
              )
            reportFatalCompatibilityIssue(context, location, message)
          }
        }
      }
    }
  }

  private fun getAllLibraries(project: Project): List<LintModelLibrary> {
    return project.buildVariant?.mainArtifact?.dependencies?.getAll() ?: emptyList()
  }

  private fun checkConsistentLibraries(
    context: Context,
    cookie: Any?,
    groupId: String,
    groupId2: String?,
  ) {
    // Make sure we're using a consistent version across all play services libraries
    // (b/22709708)

    val project = context.mainProject
    val versionToCoordinate = ArrayListMultimap.create<String, LintModelMavenName>()
    val allLibraries = getAllLibraries(project).filterIsInstance<LintModelExternalLibrary>()
    for (library in allLibraries) {
      val coordinates = library.resolvedCoordinates
      if (
        (coordinates.groupId == groupId || coordinates.groupId == groupId2) &&
          // Historically the multidex library ended up in the support package but
          // decided to do its own numbering (and isn't tied to the rest in terms
          // of implementation dependencies)
          !coordinates.artifactId.startsWith("multidex") &&
          // Renderscript has stated in b/37630182 that they are built and
          // distributed separate from the rest and do not have any version
          // dependencies
          !coordinates.artifactId.startsWith("renderscript") &&
          // Similarly firebase job dispatcher doesn't follow normal firebase version
          // numbering
          !coordinates.artifactId.startsWith("firebase-jobdispatcher") &&
          // The Android annotations library is decoupled from the rest and doesn't
          // need to be matched to the other exact support library versions
          coordinates.artifactId != "support-annotations"
      ) {
        versionToCoordinate.put(coordinates.version, coordinates)
      }
    }

    val versions = versionToCoordinate.keySet()
    if (versions.size > 1) {
      val sortedVersions = ArrayList(versions)
      sortedVersions.sortWith(Collections.reverseOrder())
      val c1 = findFirst(versionToCoordinate.get(sortedVersions[0]))
      val c2 = findFirst(versionToCoordinate.get(sortedVersions[1]))

      // For GMS, the synced version requirement ends at version 14
      if (groupId == GMS_GROUP_ID || groupId == FIREBASE_GROUP_ID) {
        // c2 is the smallest of all the versions; if it is at least 14,
        // they all are
        val version = Version.parse(c2.version)
        if (version.major?.let { it >= 14 } != false) {
          return
        }
      }

      // Not using toString because in the IDE, these are model proxies which display garbage output
      val example1 = c1.groupId + ":" + c1.artifactId + ":" + c1.version
      val example2 = c2.groupId + ":" + c2.artifactId + ":" + c2.version
      val groupDesc = if (GMS_GROUP_ID == groupId) "gms/firebase" else groupId
      val message =
        "All " +
          groupDesc +
          " libraries must use the exact same " +
          "version specification (mixing versions can lead to runtime crashes). " +
          "Found versions " +
          Joiner.on(", ").join(sortedVersions) +
          ". " +
          "Examples include `" +
          example1 +
          "` and `" +
          example2 +
          "`"

      if (cookie != null) {
        reportNonFatalCompatibilityIssue(context, cookie, message)
      } else {
        val location = getDependencyLocation(context, c1, c2)
        reportNonFatalCompatibilityIssue(context, location, message)
      }
    }
  }

  override fun beforeCheckRootProject(context: Context) {
    val project = context.project
    blockedDependencies[project] = BlockedDependencies(project)
  }

  override fun afterCheckRootProject(context: Context) {
    val project = context.project
    // Check for disallowed dependencies
    checkBlockedDependencies(context, project)
    if (!LintClient.isGradle) {
      // In the IDE, in tests, etc, we can run the detectors repeatedly,
      // and we don't want the reserved names to accumulate. In Gradle however
      // we do want to make sure that we assign unique names, even across
      // modules.
      reservedQuickfixNames = null
    }
  }

  private fun checkLibraryConsistency(context: Context) {
    checkConsistentPlayServices(context, null)
    checkConsistentWearableLibraries(context, null, null)
  }

  override fun visitTomlDocument(context: TomlContext, document: LintTomlDocument) {
    // Look for version catalogs
    val versionNodeDependencySet = mutableSetOf<Pair<LintTomlValue, Dependency>>()
    val libraries = document.getValue(VC_LIBRARIES) as? LintTomlMapValue
    if (libraries != null) {
      val versions = document.getValue(VC_VERSIONS) as? LintTomlMapValue
      val dependencyToElement = mutableMapOf<LintTomlValue, Dependency>()
      for ((_, library) in libraries.getMappedValues()) {
        val (coordinate, versionNode) = getLibraryFromTomlEntry(versions, library) ?: continue
        val dependency = Dependency.parse(coordinate)
        dependencyToElement[library] = dependency
        if (!versionNodeDependencySet.contains(versionNode to dependency)) {
          versionNodeDependencySet.add(versionNode to dependency)
          // Check dependencies without the PSI read lock, because we
          // may need to make network requests to retrieve version info.
          context.driver.runLaterOutsideReadAction {
            checkDependency(context, dependency, false, versionNode, library)
          }
        }
      }
      checkDuplication(context, dependencyToElement) { dep: Dependency ->
        dep.group + ":" + dep.name
      }
    }

    val plugins = document.getValue(VC_PLUGINS) as? LintTomlMapValue
    if (plugins != null) {
      val versions = document.getValue(VC_VERSIONS) as? LintTomlMapValue
      val dependencyToElement = mutableMapOf<LintTomlValue, Dependency>()
      for ((_, plugin) in plugins.getMappedValues()) {
        val (coordinate, versionNode) = getPluginFromTomlEntry(versions, plugin) ?: continue
        val group = coordinate.substringBefore(':')
        val gradleCoordinate = "$group:$group.gradle.plugin:${coordinate.substringAfterLast(':')}"
        val dependency = Dependency.parse(gradleCoordinate)
        dependencyToElement[plugin] = dependency
        if (!versionNodeDependencySet.contains(versionNode to dependency)) {
          versionNodeDependencySet.add(versionNode to dependency)
          // Check dependencies without the PSI read lock, because we
          // may need to make network requests to retrieve version info.
          context.driver.runLaterOutsideReadAction {
            checkDependency(context, dependency, false, versionNode, plugin)
          }
        }
      }
      checkDuplication(context, dependencyToElement) { dep: Dependency -> dep.group ?: "" }
    }
  }

  override fun afterCheckFile(context: Context) {
    maybeReportJavaTargetCompatibilityIssue(context)
    maybeReportAgpVersionIssue(context)
  }

  private fun maybeReportJavaTargetCompatibilityIssue(context: Context) {
    if (mAppliedJavaPlugin && !(mDeclaredSourceCompatibility && mDeclaredTargetCompatibility)) {
      val file = context.file
      val contents = context.client.readFile(file).toString()
      val message =
        when {
          mDeclaredTargetCompatibility -> "no Java sourceCompatibility directive"
          mDeclaredSourceCompatibility -> "no Java targetCompatibility directive"
          else -> "no Java language level directives"
        }
      val fixDisplayName =
        when {
          mDeclaredTargetCompatibility -> "Insert sourceCompatibility directive for JDK8"
          mDeclaredSourceCompatibility -> "Insert targetCompatibility directive for JDK8"
          else -> "Insert JDK8 language level directives"
        }
      val insertion =
        when {
          // Note that these replacement texts must be valid in both Groovy and KotlinScript Gradle
          // files
          mDeclaredTargetCompatibility -> "\njava.sourceCompatibility = JavaVersion.VERSION_1_8"
          mDeclaredSourceCompatibility -> "\njava.targetCompatibility = JavaVersion.VERSION_1_8"
          else ->
            """

                    java {
                        sourceCompatibility = JavaVersion.VERSION_1_8
                        targetCompatibility = JavaVersion.VERSION_1_8
                    }
                """
              .trimIndent()
        }
      val fix =
        fix()
          .replace()
          .name(fixDisplayName)
          .range(Location.create(context.file, contents, 0, contents.length))
          .end()
          .with(insertion)
          .build()
      report(context, mJavaPluginInfo!!.cookie, JAVA_PLUGIN_LANGUAGE_LEVEL, message, fix)
    }
  }

  private fun maybeReportAgpVersionIssue(context: Context) {
    // b/144442233: hide check for outdated AGP if we are reasonably sure google()
    // is not in plugin resolution repositories
    if (mDeclaredGoogleMavenRepository || !mDeclaredBuildscriptRepository) {
      agpVersionCheckInfo?.let {
        val versionString = it.newerVersion.toString()
        val currentIdentifier = it.dependency.version?.toIdentifier()
        val message =
          getNewerVersionAvailableMessage(it.dependency, versionString, it.safeReplacement)
        val fix =
          when {
            it.isResolved -> null
            currentIdentifier == null -> null
            else ->
              getUpdateDependencyFix(
                currentIdentifier,
                versionString,
                it.newerVersionIsSafe,
                it.safeReplacement,
              )
          }
        report(context, it.cookie, AGP_DEPENDENCY, message, fix)
      }
    }
  }

  private fun checkKaptUsage(
    dependency: String,
    libTomlValue: LintTomlValue?,
    context: GradleContext,
    statementCookie: Any,
  ) {
    // Drop version, leaving "group:module"
    val module = dependency.substringBeforeLast(':')
    // See if we have a KSP replacement
    val replacement =
      annotationProcessorsWithKspReplacements[module] ?: return // No replacement to offer

    val fix =
      if (!mAppliedKspPlugin) {
        // KSP plugin not applied yet in this module, point to docs on how to enable it
        fix()
          .name(
            "Learn about how to enable KSP and use the KSP processor for this dependency instead"
          )
          .url("https://developer.android.com/studio/build/migrate-to-ksp")
          .build()
      } else {
        if (libTomlValue != null) { // Dependency is from version catalog
          val declaredWithGroupAndName = (libTomlValue as? LintTomlMapValue)?.get("group") != null
          val catalogFix =
            if (declaredWithGroupAndName) {
              val (oldGroup, oldName) = module.split(":")
              val (newGroup, newName) = replacement.split(":")
              fix()
                .replace()
                .range(libTomlValue.getLocation())
                .pattern("((.*)$oldGroup(.*)$oldName(.*))")
                .with("\\k<2>$newGroup\\k<3>$newName\\k<4>")
                .build()
            } else {
              fix()
                .replace()
                .range(libTomlValue.getLocation())
                .text(module)
                .with(replacement)
                .build()
            }
          val usageFix = fix().replace().text("kapt").with("ksp").build()

          fix().name("Replace usage of kapt with KSP").composite(catalogFix, usageFix)
        } else { // Dependency is declared locally in the build.gradle file
          // Fix within just build.gradle file for locally declared dependency
          fix()
            .name("Replace usage of kapt with KSP")
            .replace()
            .pattern("((.*)kapt(.*)$module(.*))")
            .with("\\k<2>ksp\\k<3>$replacement\\k<4>")
            .build()
        }
      }

    report(
      context = context,
      cookie = statementCookie,
      issue = KAPT_USAGE_INSTEAD_OF_KSP,
      message =
        "This library supports using KSP instead of kapt," +
          " which greatly improves performance. Learn more: " +
          "https://developer.android.com/studio/build/migrate-to-ksp",
      fix = fix,
    )
  }

  /**
   * Checks to see if a KTX extension is available for the given library. If so, we offer a
   * suggestion to switch the dependency to the KTX version. See
   * https://developer.android.com/kotlin/ktx for details.
   *
   * This should be called outside of a read action, since it may trigger network requests.
   */
  private fun checkForKtxExtension(
    context: Context,
    groupId: String,
    artifactId: String,
    version: Version,
    cookie: Any,
  ) {
    if (!mAppliedKotlinAndroidPlugin) return
    if (artifactId.endsWith("-ktx")) return
    if (cookie is LintTomlValue) return

    val mavenName = "$groupId:$artifactId"
    if (!libraryHasKtxExtension(mavenName)) {
      return
    }

    val artifact = context.project.buildVariant?.artifact ?: return
    // Make sure the Kotlin stdlib is used by the main artifact (not just by tests).
    if (artifact.type != LintModelArtifactType.MAIN) {
      return
    }
    artifact.findCompileDependency("org.jetbrains.kotlin:kotlin-stdlib") ?: return

    // Make sure the KTX extension exists for this version of the library.
    val repository = getGoogleMavenRepository(context.client)
    repository.findVersion(
      groupId,
      "$artifactId-ktx",
      filter = { it == version },
      allowPreview = true,
    ) ?: return

    // Note: once b/155974293 is fixed, we can check whether the KTX extension is
    // already a direct dependency. If it is, then we could offer a slightly better
    // warning message along the lines of: "There is no need to declare this dependency
    // because the corresponding KTX extension pulls it in automatically."

    val msg = "Add suffix `-ktx` to enable the Kotlin extensions for this library"
    val fix =
      fix()
        .name("Replace with KTX dependency")
        .replace()
        .text(mavenName)
        .with("$mavenName-ktx")
        .build()
    report(context, cookie, KTX_EXTENSION_AVAILABLE, msg, fix)
  }

  private fun checkForBomUsageWithoutPlatform(
    property: String,
    dependency: String,
    value: String,
    context: GradleContext,
    valueCookie: Any,
  ) {
    if (listOf("platform", "enforcedPlatform").any { value.startsWith("$it(") }) {
      return
    }
    if (
      dependency.substringBeforeLast(':') in commonBoms &&
        (CompileConfiguration.IMPLEMENTATION.matches(property) ||
          CompileConfiguration.API.matches(property))
    ) {
      val message = "BOM should be added with a call to platform()"
      val fix =
        fix()
          .name("Add platform() to BOM declaration", true)
          .replace()
          .text(value)
          .with("platform($value)")
          .build()
      report(context, valueCookie, BOM_WITHOUT_PLATFORM, message, fix)
    }
  }

  /**
   * Report any blocked dependencies that weren't found in the build.gradle source file during
   * processing (we don't have accurate position info at this point)
   */
  private fun checkBlockedDependencies(context: Context, project: Project) {
    val blockedDependencies = blockedDependencies[project] ?: return
    val dependencies = blockedDependencies.getForbiddenDependencies()
    if (dependencies.isNotEmpty()) {
      for (path in dependencies) {
        val message = getBlockedDependencyMessage(path)
        val projectDir = context.project.dir
        val gc =
          path[0].findLibrary()?.let {
            if (it is LintModelExternalLibrary) {
              it.resolvedCoordinates
            } else null
          }
        val location =
          if (gc != null) {
            getDependencyLocation(context, gc.groupId, gc.artifactId, gc.version)
          } else {
            val mavenName = path[0].artifactName
            guessGradleLocation(context.client, projectDir, mavenName)
          }
        context.report(Incident(DUPLICATE_CLASSES, location, message), map())
      }
    }
    this.blockedDependencies.remove(project)
  }

  private fun report(
    context: Context,
    cookie: Any,
    issue: Issue,
    message: String,
    fix: LintFix? = null,
    partial: Boolean = false,
    overrideSeverity: Severity? = null,
    constraint: Constraint? = null,
    clientProperties: LintMap? = null,
  ): Boolean {
    // Some methods in GradleDetector are run without the PSI read lock in order
    // to accommodate network requests, so we grab the read lock here.
    var reportCreated = false
    context.client.runReadAction(
      Runnable {
        val enabled = context.isEnabled(issue)
        if (enabled && context is GradleContext) {
          val location = context.getLocation(cookie)
          val incident = Incident(issue, cookie, location, message, fix)
          overrideSeverity?.let { incident.overrideSeverity(it) }
          incident.clientProperties = clientProperties
          if (constraint != null) {
            context.report(incident, constraint)
          } else if (partial) {
            context.report(incident, map())
          } else {
            context.report(incident)
          }
          reportCreated = true
        } else if (enabled && context is TomlContext) {
          val location = context.getLocation(cookie)
          val start = location.start?.offset ?: 0
          val checkComments = context.containsCommentSuppress()
          if (checkComments && context.isSuppressedWithComment(start, issue)) {
            return@Runnable
          }
          val incident = Incident(issue, location, message, fix)
          overrideSeverity?.let { incident.overrideSeverity(it) }
          incident.clientProperties = clientProperties
          if (constraint != null) {
            context.report(incident, constraint)
          } else if (partial) {
            context.report(incident, map())
          } else {
            context.report(incident)
          }
          reportCreated = true
        }
      }
    )
    return reportCreated
  }

  /**
   * Normally, all warnings reported for a given issue will have the same severity, so it isn't
   * possible to have some of them reported as errors and others as warnings. And this is
   * intentional, since users should get to designate whether an issue is an error or a warning (or
   * ignored for that matter).
   *
   * However, for [COMPATIBILITY] we want to treat some issues as fatal (breaking the build) but not
   * others. To achieve this we tweak things a little bit. All compatibility issues are now marked
   * as fatal, and if we're *not* in the "fatal only" mode, all issues are reported as before (with
   * severity fatal, which has the same visual appearance in the IDE as the previous severity,
   * "error".) However, if we're in a "fatal-only" build, then we'll stop reporting the issues that
   * aren't meant to be treated as fatal. That's what this method does; issues reported to it should
   * always be reported as fatal. There is a corresponding method,
   * [reportNonFatalCompatibilityIssue] which can be used to report errors that shouldn't break the
   * build; those are ignored in fatal-only mode.
   */
  private fun reportFatalCompatibilityIssue(context: Context, cookie: Any, message: String) {
    report(context, cookie, COMPATIBILITY, message)
  }

  private fun reportFatalCompatibilityIssue(
    context: Context,
    cookie: Any,
    message: String,
    fix: LintFix?,
  ) {
    report(context, cookie, COMPATIBILITY, message, fix)
  }

  /** See [reportFatalCompatibilityIssue] for an explanation. */
  private fun reportNonFatalCompatibilityIssue(
    context: Context,
    cookie: Any,
    message: String,
    lintFix: LintFix? = null,
  ) {
    if (context.driver.fatalOnlyMode) {
      return
    }

    report(context, cookie, COMPATIBILITY, message, lintFix)
  }

  private fun reportFatalCompatibilityIssue(context: Context, location: Location, message: String) {
    // Some methods in GradleDetector are run without the PSI read lock in order
    // to accommodate network requests, so we grab the read lock here.
    context.client.runReadAction { context.report(COMPATIBILITY, location, message) }
  }

  /** See [reportFatalCompatibilityIssue] for an explanation. */
  private fun reportNonFatalCompatibilityIssue(
    context: Context,
    location: Location,
    message: String,
  ) {
    if (context.driver.fatalOnlyMode) {
      return
    }

    // Some methods in GradleDetector are run without the PSI read lock in order
    // to accommodate network requests, so we grab the read lock here.
    context.client.runReadAction { context.report(COMPATIBILITY, location, message) }
  }

  private fun getSdkVersion(value: String, valueCookie: Any): Int {
    var version = 0
    if (isStringLiteral(value)) {
      val codeName = getStringLiteralValue(value, valueCookie)
      if (codeName != null) {
        if (isNumberString(codeName)) {
          // Don't access numbered strings; should be literal numbers (lint will warn)
          return -1
        }
        val androidVersion = SdkVersionInfo.getVersion(codeName, null)
        if (androidVersion != null) {
          version = androidVersion.featureLevel
        }
      }
    } else {
      version = getIntLiteralValue(value, -1)
    }
    return version
  }

  // TODO(b/279886738): resolving a Dependency against the project's artifacts should
  //  from a theoretical point of view return a Component.  However, here, we're not
  //  really *conceptually* resolving a Dependency, because what this is actually used
  //  for is to guess what the value of a version variable in an interpolated String
  //  might be, and rather than model variables and their values, we pull the resolved
  //  version and hope for the best.  For our purposes, that's not completely wrong.
  @SuppressWarnings("ExpensiveAssertion")
  private fun resolveCoordinate(
    context: GradleContext,
    property: String,
    dependency: Dependency,
  ): Dependency? {
    fun Component.toDependency() = Dependency(group, name, RichVersion.require(version))
    assert(dependency.version?.toIdentifier()?.contains("$") ?: false) {
      dependency.version.toString()
    }

    val project = context.project
    val variant = project.buildVariant
    if (variant != null) {
      val artifact =
        when {
          property.startsWith("androidTest") -> variant.androidTestArtifact
          property.startsWith("testFixtures") -> variant.testFixturesArtifact
          property.startsWith("test") -> variant.testArtifact
          else -> variant.mainArtifact
        } ?: return null
      for (library in artifact.dependencies.getAll()) {
        if (library is LintModelExternalLibrary) {
          val mc = library.resolvedCoordinates
          if (mc.groupId == dependency.group && mc.artifactId == dependency.name) {
            val version = Version.parse(mc.version)
            return Component(mc.groupId, mc.artifactId, version).toDependency()
          }
        }
      }
    }
    return null
  }

  private fun isPrivacySandboxSdk(project: Project): Boolean =
    project.buildModule?.type == LintModelModuleType.PRIVACY_SANDBOX_SDK

  /** True if the given project uses the legacy http library. */
  private fun usesLegacyHttpLibrary(project: Project): Boolean {
    val model = project.buildModule ?: return false
    for (file in model.bootClassPath) {
      if (file.endsWith("org.apache.http.legacy.jar")) {
        return true
      }
    }

    return false
  }

  private fun getUpdateDependencyFix(
    currentVersion: String,
    suggestedVersion: String,
    suggestedVersionIsSafe: Boolean = false,
    safeReplacement: Version? = null,
  ): LintFix {
    val fix =
      fix()
        .name("Change to $suggestedVersion")
        .sharedName("Update versions")
        .replace()
        .text(currentVersion)
        .with(suggestedVersion)
        .autoFix(suggestedVersionIsSafe, suggestedVersionIsSafe)
        .build()
    return if (safeReplacement != null) {
      val stableVersion = safeReplacement.toString()
      val stableFix =
        fix()
          .name("Change to $stableVersion")
          .sharedName("Update versions")
          .replace()
          .text(currentVersion)
          .with(stableVersion)
          .autoFix()
          .build()
      fix().alternatives(fix, stableFix)
    } else {
      fix
    }
  }

  /**
   * Returns the "group:artifact" address of a dependency, unless it's a Gradle plugin in which case
   * it returns the plugin id.
   */
  private fun Dependency.id(): String {
    return if (isGradlePlugin()) {
      group!!
    } else {
      "$group:$name"
    }
  }

  private fun Dependency.isGradlePlugin(): Boolean =
    group != null && name.endsWith(".gradle.plugin") && name == "$group.gradle.plugin"

  private fun getNewerVersionAvailableMessage(
    dependency: Dependency,
    version: String,
    stable: Version?,
  ): String {
    val message = StringBuilder()
    with(message) {
      append("A newer version of ")
      append(dependency.id())
      append(" than ")
      append("${dependency.version}")
      append(" is available: ")
      append(version)
      if (stable != null) {
        append(". (There is also a newer version of ")
        append(stable.major.toString())
        append(".")
        append(stable.minor.toString())
        // \uD835\uDC65 is 𝑥, unicode for Mathematical Italic Small X
        append(".\uD835\uDC65 available, if upgrading to ")
        append(version)
        append(" is difficult: ")
        append(stable.toString())
        append(")")
      }
    }
    return message.toString()
  }

  private fun findFirst(coordinates: Collection<LintModelMavenName>): LintModelMavenName {
    return Collections.min(coordinates) { o1, o2 -> o1.toString().compareTo(o2.toString()) }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val issue = incident.issue
    if (issue === DUPLICATE_CLASSES) {
      return context.mainProject.minSdk < 23 || usesLegacyHttpLibrary(context.mainProject)
    } else {
      error(issue.id)
    }
  }

  override fun checkMergedProject(context: Context) {
    if (context.isGlobalAnalysis() && context.driver.isIsolated()) {
      // Already performed on occurrences in the file being edited
      return
    }
    checkLibraryConsistency(context)
  }

  private fun getBlockedDependencyMessage(path: List<LintModelDependency>): String {
    val direct = path.size == 1
    val message: String
    val resolution =
      "Solutions include " +
        "finding newer versions or alternative libraries that don't have the " +
        "same problem (for example, for `httpclient` use `HttpUrlConnection` or " +
        "`okhttp` instead), or repackaging the library using something like " +
        "`jarjar`."
    if (direct) {
      message =
        "`${path[0].getArtifactId()}` defines classes that conflict with classes now provided by Android. $resolution"
    } else {
      val sb = StringBuilder()
      var first = true
      for (library in path) {
        if (first) {
          first = false
        } else {
          sb.append(" \u2192 ") // right arrow
        }
        val coordinates = library.artifactName
        sb.append(coordinates)
      }
      sb.append(") ")
      val chain = sb.toString()
      message =
        "`${path[0].getArtifactId()}` depends on a library " +
          "(${path[path.size - 1].artifactName}) which defines " +
          "classes that conflict with classes now provided by Android. $resolution " +
          "Dependency chain: $chain"
    }
    return message
  }

  private fun getNewerVersion(version1: Version, major: Int, minor: Int, micro: Int): Version? =
    Version.parse("$major.$minor.$micro").takeIf {
      version1 > Version.prefixInfimum("0") && version1 < it
    }

  private fun getNewerVersion(version1: Version, major: Int, minor: Int): Version? =
    Version.parse("$major.$minor").takeIf { version1 > Version.prefixInfimum("0") && version1 < it }

  private var googleMavenRepository: GoogleMavenRepository? = null
  private var googlePlaySdkIndex: GooglePlaySdkIndex? = null

  private fun getGoogleMavenRepoVersion(
    context: Context,
    dependency: Dependency,
    filter: Predicate<Version>?,
  ): Version? {
    val repository = getGoogleMavenRepository(context.client)
    return repository.findVersion(dependency, filter, dependency.explicitlyIncludesPreview)
  }

  private fun getSdkIndexVersion(
    sdkIndex: GooglePlaySdkIndex,
    groupId: String,
    artifactId: String,
    filter: Predicate<Version>?,
  ): Version? {
    val latestVersion = sdkIndex.getLatestVersion(groupId, artifactId) ?: return null
    val parsedVersion = Version.parse(latestVersion)
    val isValid = filter?.test(parsedVersion) ?: true
    if (isValid && !parsedVersion.isPreview) {
      return parsedVersion
    }
    return null
  }

  fun getGoogleMavenRepository(client: LintClient): GoogleMavenRepository {
    return googleMavenRepository
      ?: run {
        val cacheDir = client.getCacheDir(MAVEN_GOOGLE_CACHE_DIR_KEY, true)
        val repository =
          object : GoogleMavenRepository(cacheDir?.toPath()) {

            public override fun readUrlData(
              url: String,
              timeout: Int,
              lastModified: Long,
            ): ReadUrlDataResult = readUrlData(client, url, timeout, lastModified)

            public override fun error(throwable: Throwable, message: String?) =
              client.log(throwable, message)
          }

        googleMavenRepository = repository
        repository
      }
  }

  private fun getGooglePlaySdkIndex(client: LintClient): GooglePlaySdkIndex {
    return googlePlaySdkIndex
      ?: run {
        val cacheDir = client.getCacheDir(GOOGLE_PLAY_SDK_INDEX_KEY, true)
        val repository = playSdkIndexFactory(cacheDir?.toPath(), client)
        googlePlaySdkIndex = repository
        repository
      }
  }

  companion object {
    private var lastTargetSdkVersion: Int = -1
    private var lastTargetSdkVersionFile: File? = null

    /** If you invoke the target SDK version migration assistant, stop flagging edits. */
    fun stopFlaggingTargetSdkEdits() {
      lastTargetSdkVersion = Integer.MAX_VALUE
      lastTargetSdkVersionFile = null
    }

    /**
     * Whether to attach extra client properties to some of these incidents; this is intended to be
     * used when lint is running in the IDE in special contexts and to communicate extra information
     * to IDE-side quickfixes and refactorings without resorting to parsing error messages.
     */
    var recordClientProperties = false

    /** Calendar to use to look up the current time (used by tests to set specific time). */
    var calendar: Calendar? = null

    const val KEY_COORDINATE = "coordinate"
    const val KEY_REVISION = "revision"

    private const val VC_LIBRARY_PREFIX = "libs."
    private const val VC_PLUGIN_PREFIX = "libs.plugins."

    private val IMPLEMENTATION = Implementation(GradleDetector::class.java, Scope.GRADLE_SCOPE)
    private val IMPLEMENTATION_WITH_TOML =
      Implementation(
        GradleDetector::class.java,
        Scope.GRADLE_AND_TOML_SCOPE,
        Scope.GRADLE_SCOPE,
        Scope.TOML_SCOPE,
      )
    private val IMPLEMENTATION_WITH_MANIFEST =
      Implementation(
        GradleDetector::class.java,
        EnumSet.of(Scope.GRADLE_FILE, Scope.MANIFEST),
        Scope.GRADLE_SCOPE,
        Scope.MANIFEST_SCOPE,
      )

    /** Obsolete dependencies. */
    @JvmField
    val DEPENDENCY =
      Issue.create(
        id = "GradleDependency",
        briefDescription = "Obsolete Gradle Dependency",
        explanation =
          """
                This detector looks for usages of libraries where the version you are using \
                is not the current stable release. Using older versions is fine, and there \
                are cases where you deliberately want to stick with an older version. \
                However, you may simply not be aware that a more recent version is \
                available, and that is what this lint check helps find.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION_WITH_TOML,
      )

    /** Project imports a dependency with different versions. */
    @JvmField
    val MULTIPLE_VERSIONS_DEPENDENCY =
      Issue.create(
        id = "SimilarGradleDependency",
        briefDescription = "Multiple Versions Gradle Dependency",
        explanation =
          """
                This detector looks for usages of libraries when name and group are the same \
                but versions are different. Using multiple versions in big project is fine, \
                and there are cases where you deliberately want to stick with such approach. \
                However, you may simply not be aware that this situation happens, and that is \
                what this lint check helps find.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.INFORMATIONAL,
        implementation = IMPLEMENTATION_WITH_TOML,
      )

    /**
     * Using a gradle group:artifact:id directly instead of placing it in the version catalog TOML
     * file
     */
    @JvmField
    val SWITCH_TO_TOML =
      Issue.create(
        id = "UseTomlInstead",
        briefDescription = "Use TOML Version Catalog Instead",
        explanation =
          """
                If your project is using a `libs.versions.toml` file, you should place \
                all Gradle dependencies in the TOML file. This lint check looks for \
                version declarations outside of the TOML file and suggests moving them \
                (and in the IDE, provides a quickfix to performing the operation automatically).
                """,
        category = Category.PRODUCTIVITY,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION_WITH_TOML,
      )

    /** Using version 1.x of the core library desugaring with Android 15 or later */
    @JvmField
    val CORE_LIB_DESUGARING_V2 =
      Issue.create(
        id = "CoreLibDesugaringV1",
        briefDescription = "Android 15 requires `desugar_jdk_libs` 2.+",
        explanation =
          """
          Core library desugaring with `compileSdk` 35 or later (Android 15) \
          requires using version 2.+ of the core library desugaring libraries. \
          The code may compile successfully, but can crash at runtime.
          """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION_WITH_TOML,
        androidSpecific = true,
      )

    /** A dependency on an obsolete version of the Android Gradle Plugin. */
    @JvmField
    val AGP_DEPENDENCY =
      Issue.create(
        id = "AndroidGradlePluginVersion",
        briefDescription = "Obsolete Android Gradle Plugin Version",
        explanation =
          """
                This detector looks for usage of the Android Gradle Plugin where the version \
                you are using is not the current stable release. Using older versions is fine, \
                and there are cases where you deliberately want to stick with an older version. \
                However, you may simply not be aware that a more recent version is available, \
                and that is what this lint check helps find.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION_WITH_TOML,
      )

    /** Deprecated Gradle constructs. */
    @JvmField
    val DEPRECATED =
      Issue.create(
        id = "GradleDeprecated",
        briefDescription = "Deprecated Gradle Construct",
        explanation =
          """
                This detector looks for deprecated Gradle constructs which currently work \
                but will likely stop working in a future update.""",
        category = Category.CORRECTNESS,
        priority = 6,
        androidSpecific = true,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Deprecated Gradle configurations. */
    @JvmField
    val DEPRECATED_CONFIGURATION =
      Issue.create(
        id = "GradleDeprecatedConfiguration",
        briefDescription = "Deprecated Gradle Configuration",
        explanation =
          """
                Some Gradle configurations have been deprecated since Android Gradle Plugin 3.0.0 \
                and will be removed in a future version of the Android Gradle Plugin.
             """,
        category = Category.CORRECTNESS,
        moreInfo = "https://d.android.com/r/tools/update-dependency-configurations",
        priority = 6,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Incompatible Android Gradle plugin. */
    @JvmField
    val GRADLE_PLUGIN_COMPATIBILITY =
      Issue.create(
        id = "GradlePluginVersion",
        briefDescription = "Incompatible Android Gradle Plugin",
        explanation =
          """
                Not all versions of the Android Gradle plugin are compatible with all \
                versions of the SDK. If you update your tools, or if you are trying to \
                open a project that was built with an old version of the tools, you may \
                need to update your plugin version number.""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Invalid or dangerous paths. */
    @JvmField
    val PATH =
      Issue.create(
        id = "GradlePath",
        briefDescription = "Gradle Path Issues",
        explanation =
          """
                Gradle build scripts are meant to be cross platform, so file paths use \
                Unix-style path separators (a forward slash) rather than Windows path \
                separators (a backslash). Similarly, to keep projects portable and \
                repeatable, avoid using absolute paths on the system; keep files within \
                the project instead. To share code between projects, consider creating \
                an android-library and an AAR dependency""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Constructs the IDE support struggles with. */
    @JvmField
    val IDE_SUPPORT =
      Issue.create(
        id = "GradleIdeError",
        briefDescription = "Gradle IDE Support Issues",
        explanation =
          """
                Gradle is highly flexible, and there are things you can do in Gradle \
                files which can make it hard or impossible for IDEs to properly handle \
                the project. This lint check looks for constructs that potentially \
                break IDE support.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** Using + in versions. */
    @JvmField
    val PLUS =
      Issue.create(
        id = "GradleDynamicVersion",
        briefDescription = "Gradle Dynamic Version",
        explanation =
          """
                Using `+` in dependencies lets you automatically pick up the latest \
                available version rather than a specific, named version. However, \
                this is not recommended; your builds are not repeatable; you may have \
                tested with a slightly different version than what the build server \
                used. (Using a dynamic version as the major version number is more \
                problematic than using it in the minor version position.)""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Accidentally calling a getter instead of your own methods. */
    @JvmField
    val GRADLE_GETTER =
      Issue.create(
        id = "GradleGetter",
        briefDescription = "Gradle Implicit Getter Call",
        explanation =
          """
                Gradle will let you replace specific constants in your build scripts \
                with method calls, so you can for example dynamically compute a version \
                string based on your current version control revision number, rather \
                than hardcoding a number.

                When computing a version name, it's tempting to for example call the \
                method to do that `getVersionName`. However, when you put that method \
                call inside the `defaultConfig` block, you will actually be calling the \
                Groovy getter for the `versionName` property instead. Therefore, you \
                need to name your method something which does not conflict with the \
                existing implicit getters. Consider using `compute` as a prefix instead \
                of `get`.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Using incompatible versions. */
    @JvmField
    val COMPATIBILITY =
      Issue.create(
        id = "GradleCompatible",
        briefDescription = "Incompatible Gradle Versions",
        explanation =
          """
                There are some combinations of libraries, or tools and libraries, that \
                are incompatible, or can lead to bugs. One such incompatibility is \
                compiling with a version of the Android support libraries that is not \
                the latest version (or in particular, a version lower than your \
                `targetSdkVersion`).""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.FATAL,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Using a string where an integer is expected. */
    @JvmField
    val STRING_INTEGER =
      Issue.create(
        id = "StringShouldBeInt",
        briefDescription = "String should be int",
        explanation =
          """
                The properties `compileSdkVersion`, `minSdkVersion` and `targetSdkVersion` \
                are usually numbers, but can be strings when you are using an add-on (in \
                the case of `compileSdkVersion`) or a preview platform (for the other two \
                properties).

                However, you can not use a number as a string (e.g. "19" instead of 19); \
                that will result in a platform not found error message at build/sync \
                time.""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Attempting to use substitution with single quotes. */
    @JvmField
    val NOT_INTERPOLATED =
      Issue.create(
        id = "NotInterpolated",
        briefDescription = "Incorrect Interpolation",
        explanation =
          """
                To insert the value of a variable, you can use `${"$"}{variable}` inside a \
                string literal, but **only** if you are using double quotes!""",
        moreInfo = "https://www.groovy-lang.org/syntax.html#_string_interpolation",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** A newer version is available on a remote server. */
    @JvmField
    val REMOTE_VERSION =
      Issue.create(
        id = "NewerVersionAvailable",
        briefDescription = "Newer Library Versions Available",
        explanation =
          """
                This detector checks with a central repository to see if there are newer \
                versions available for the dependencies used by this project. This is \
                similar to the `GradleDependency` check, which checks for newer versions \
                available in the Android SDK tools and libraries, but this works with any \
                MavenCentral dependency, and connects to the library every time, which \
                makes it more flexible but also **much** slower.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION_WITH_TOML,
        enabledByDefault = false,
      )

    /** The API version is set too low. */
    @JvmField
    val MIN_SDK_TOO_LOW =
      Issue.create(
        id = "MinSdkTooLow",
        briefDescription = "API Version Too Low",
        explanation =
          """
                The value of the `minSdkVersion` property is too low and can be \
                incremented without noticeably reducing the number of supported \
                devices.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
        enabledByDefault = false,
      )

    /** Accidentally using octal numbers. */
    @JvmField
    val ACCIDENTAL_OCTAL =
      Issue.create(
        id = "AccidentalOctal",
        briefDescription = "Accidental Octal",
        explanation =
          """
                In Groovy, an integer literal that starts with a leading 0 will be \
                interpreted as an octal number. That is usually (always?) an accident \
                and can lead to subtle bugs, for example when used in the `versionCode` \
                of an app.""",
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val BUNDLED_GMS =
      Issue.create(
        id = "UseOfBundledGooglePlayServices",
        briefDescription = "Use of bundled version of Google Play services",
        explanation =
          """
                Google Play services SDK's can be selectively included, which enables a \
                smaller APK size. Consider declaring dependencies on individual Google \
                Play services SDK's. If you are using Firebase API's \
                (https://firebase.google.com/docs/android/setup), Android Studio's \
                Tools → Firebase assistant window can automatically add just the \
                dependencies needed for each feature.""",
        moreInfo = "https://developers.google.com/android/guides/setup#split",
        category = Category.PERFORMANCE,
        priority = 4,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Using a versionCode that is very high. */
    @JvmField
    val HIGH_APP_VERSION_CODE =
      Issue.create(
        id = "HighAppVersionCode",
        briefDescription = "VersionCode too high",
        explanation =
          """
                The declared `versionCode` is an Integer. Ensure that the version number is \
                not close to the limit. It is recommended to monotonically increase this \
                number each minor or major release of the app. Note that updating an app \
                with a versionCode over `Integer.MAX_VALUE` is not possible.""",
        moreInfo = "https://developer.android.com/studio/publish/versioning.html",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Dev mode is no longer relevant. */
    @JvmField
    val DEV_MODE_OBSOLETE =
      Issue.create(
        id = "DevModeObsolete",
        briefDescription = "Dev Mode Obsolete",
        explanation =
          """
                In the past, our documentation recommended creating a `dev` product flavor \
                with has a minSdkVersion of 21, in order to enable multidexing to speed up \
                builds significantly during development.

                That workaround is no longer necessary, and it has some serious downsides, \
                such as breaking API access checking (since the true `minSdkVersion` is no \
                longer known).

                In recent versions of the IDE and the Gradle plugin, the IDE automatically \
                passes the API level of the connected device used for deployment, and if \
                that device is at least API 21, then multidexing is automatically turned \
                on, meaning that you get the same speed benefits as the `dev` product \
                flavor but without the downsides.""",
        category = Category.PERFORMANCE,
        priority = 2,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Duplicate HTTP classes. */
    @JvmField
    val DUPLICATE_CLASSES =
      Issue.create(
        id = "DuplicatePlatformClasses",
        briefDescription = "Duplicate Platform Classes",
        explanation =
          """
                There are a number of libraries that duplicate not just functionality \
                of the Android platform but using the exact same class names as the ones \
                provided in Android -- for example the apache http classes. This can \
                lead to unexpected crashes.

                To solve this, you need to either find a newer version of the library \
                which no longer has this problem, or to repackage the library (and all \
                of its dependencies) using something like the `jarjar` tool, or finally, \
                rewriting the code to use different APIs (for example, for http code, \
                consider using `HttpUrlConnection` or a library like `okhttp`).""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.FATAL,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /**
     * Reserved variable names used by [pickLibraryVariableName] and [pickVersionVariableName]
     * suggesting library and version variable names; we need to make sure we keep track of previous
     * suggestions made such that we don't have multiple quickfixes making the same suggestion and
     * creating a clash if all fixes are applied.
     */
    var reservedQuickfixNames: MutableMap<String, MutableSet<String>>? = null

    /** targetSdkVersion about to expire */
    @JvmField
    val EXPIRING_TARGET_SDK_VERSION =
      Issue.create(
          id = "ExpiringTargetSdkVersion",
          briefDescription = "TargetSdkVersion Soon Expiring",
          explanation =
            """
                Configuring your app or sdk to target a recent API level ensures that users benefit \
                from significant security and performance improvements, while still allowing \
                your app or sdk to run on older Android versions (down to the `minSdkVersion`).

                To update your `targetSdkVersion`, follow the steps from \
                "Meeting Google Play requirements for target API level", \
                https://developer.android.com/distribute/best-practices/develop/target-sdk.html
                """,
          category = Category.COMPLIANCE,
          priority = 8,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = IMPLEMENTATION_WITH_MANIFEST,
        )
        .addMoreInfo(
          "https://support.google.com/googleplay/android-developer/answer/113469#targetsdk"
        )
        .addMoreInfo(
          "https://developer.android.com/distribute/best-practices/develop/target-sdk.html"
        )

    /** targetSdkVersion no longer supported */
    @JvmField
    val EXPIRED_TARGET_SDK_VERSION =
      Issue.create(
          id = "ExpiredTargetSdkVersion",
          briefDescription = "TargetSdkVersion No Longer Supported",
          moreInfo =
            "https://support.google.com/googleplay/android-developer/answer/113469#targetsdk",
          explanation =
            """
                Configuring your app or sdk to target a recent API level ensures that users benefit \
                from significant security and performance improvements, while still allowing \
                your app to run on older Android versions (down to the `minSdkVersion`).

                To update your `targetSdkVersion`, follow the steps from \
                "Meeting Google Play requirements for target API level", \
                https://developer.android.com/distribute/best-practices/develop/target-sdk.html
                """,
          category = Category.COMPLIANCE,
          priority = 8,
          severity = Severity.FATAL,
          androidSpecific = true,
          implementation = IMPLEMENTATION_WITH_MANIFEST,
        )
        .addMoreInfo(
          "https://developer.android.com/distribute/best-practices/develop/target-sdk.html"
        )

    /** Using a targetSdkVersion that isn't recent */
    @JvmField
    val TARGET_NEWER =
      Issue.create(
          id = "OldTargetApi",
          briefDescription = "Target SDK attribute is not targeting latest version",
          explanation =
            """
                When your application or sdk runs on a version of Android that is more recent than your \
                `targetSdkVersion` specifies that it has been tested with, various compatibility modes \
                kick in. This ensures that your application continues to work, but it may look out of \
                place. For example, if the `targetSdkVersion` is less than 14, your app may get an \
                option button in the UI.

                To fix this issue, set the `targetSdkVersion` to the highest available value. Then test \
                your app to make sure everything works correctly. You may want to consult the \
                compatibility notes to see what changes apply to each version you are adding support \
                for: https://developer.android.com/reference/android/os/Build.VERSION_CODES.html as well \
                as follow this guide:
                https://developer.android.com/distribute/best-practices/develop/target-sdk.html
                """,
          category = Category.CORRECTNESS,
          priority = 6,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION_WITH_MANIFEST,
        )
        .addMoreInfo(
          "https://developer.android.com/distribute/best-practices/develop/target-sdk.html"
        )
        .addMoreInfo("https://developer.android.com/reference/android/os/Build.VERSION_CODES.html")

    /** targetSdkVersion was manually edited */
    @JvmField
    val EDITED_TARGET_SDK_VERSION =
      Issue.create(
        id = "EditedTargetSdkVersion",
        briefDescription = "Manually Edited TargetSdkVersion",
        explanation =
          """
        Updating the `targetSdkVersion` of an app is seemingly easy: just increment the \
        `targetSdkVersion` number in the manifest file!

        But that's not actually safe. The `targetSdkVersion` controls a wide range of \
        behaviors that change from release to release, and to update, you should carefully \
        consult the documentation to see what has changed, how your app may need to adjust, \
        and then of course, carefully test everything.

        In new versions of Android Studio, there is a special migration assistant, available \
        from the tools menu (and as a quickfix from this lint warning) which analyzes your \
        specific app and filters the set of applicable migration steps to those needed for \
        your app.

        This lint check does something very simple: it just detects whether it looks like \
        you've manually edited the targetSdkVersion field in a build.gradle file. Obviously, \
        as part of doing the above careful steps, you may end up editing the value, which \
        would trigger the check -- and it's safe to ignore it; this lint check *only* runs \
        in the IDE, not from the command line; it's sole purpose to bring *awareness* to the \
        (many) developers who haven't been aware of this issue and have just bumped the \
        targetSdkVersion, recompiled, and uploaded their updated app to the Google Play Store, \
        sometimes leading to crashes or other problems on newer devices.
        """,
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Using a deprecated library. */
    @JvmField
    val DEPRECATED_LIBRARY =
      Issue.create(
        id = "OutdatedLibrary",
        briefDescription = "Outdated Library",
        explanation =
          """
                Your app is using an outdated version of a library. This may cause violations \
                of Google Play policies (see https://play.google.com/about/monetization-ads/ads/) \
                and/or may affect your app’s visibility on the Play Store.

                Please try updating your app with an updated version of this library, or remove \
                it from your app.
                """,
        category = Category.COMPLIANCE,
        priority = 5,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION_WITH_TOML,
        moreInfo = GOOGLE_PLAY_SDK_INDEX_URL,
      )

    /** Using data binding with Kotlin but not Kotlin annotation processing. */
    @JvmField
    val DATA_BINDING_WITHOUT_KAPT =
      Issue.create(
        id = "DataBindingWithoutKapt",
        briefDescription = "Data Binding without Annotation Processing",
        moreInfo = "https://kotlinlang.org/docs/reference/kapt.html",
        explanation =
          """
                Apps that use Kotlin and data binding should also apply the kotlin-kapt plugin.
                """,
        category = Category.CORRECTNESS,
        priority = 1,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Using Lifecycle annotation processor with java8. */
    @JvmField
    val LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8 =
      Issue.create(
        id = "LifecycleAnnotationProcessorWithJava8",
        briefDescription = "Lifecycle Annotation Processor with Java 8 Compile Option",
        moreInfo = "https://d.android.com/r/studio-ui/lifecycle-release-notes",
        explanation =
          """
                For faster incremental build, switch to the Lifecycle Java 8 API with these steps:

                First replace
                ```gradle
                annotationProcessor "androidx.lifecycle:lifecycle-compiler:*version*"
                kapt "androidx.lifecycle:lifecycle-compiler:*version*"
                ```
                with
                ```gradle
                implementation "androidx.lifecycle:lifecycle-common-java8:*version*"
                ```
                Then remove any `OnLifecycleEvent` annotations from `Observer` classes \
                and make them implement the `DefaultLifecycleObserver` interface.
                """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Using a vulnerable library. */
    @JvmField
    val RISKY_LIBRARY =
      Issue.create(
          id = "RiskyLibrary",
          briefDescription = "Libraries with Privacy or Security Risks",
          explanation =
            """
                Your app is using a version of a library that has been identified by \
                the library developer as a potential source of privacy and/or security risks. \
                This may be a violation of Google Play policies (see \
                https://play.google.com/about/monetization-ads/ads/) and/or affect your app’s \
                visibility on the Play Store.

                When available, the individual error messages from lint will include details \
                about the reasons for this advisory.

                Please try updating your app with an updated version of this library, or remove \
                it from your app.
            """,
          category = Category.SECURITY,
          priority = 4,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = IMPLEMENTATION_WITH_TOML,
          moreInfo = GOOGLE_PLAY_SDK_INDEX_URL,
        )
        .addMoreInfo("https://goo.gle/RiskyLibrary")

    @JvmField
    val ANNOTATION_PROCESSOR_ON_COMPILE_PATH =
      Issue.create(
        id = "AnnotationProcessorOnCompilePath",
        briefDescription = "Annotation Processor on Compile Classpath",
        explanation =
          """
               This dependency is identified as an annotation processor. Consider adding it to the \
               processor path using `annotationProcessor` instead of including it to the \
               compile path.
            """,
        category = Category.PERFORMANCE,
        priority = 8,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val KTX_EXTENSION_AVAILABLE =
      Issue.create(
        id = "KtxExtensionAvailable",
        briefDescription = "KTX Extension Available",
        explanation =
          """
                Android KTX extensions augment some libraries with support for modern Kotlin \
                language features like extension functions, extension properties, lambdas, named \
                parameters, coroutines, and more.

                In Kotlin projects, use the KTX version of a library by replacing the \
                dependency in your `build.gradle` file. For example, you can replace \
                `androidx.fragment:fragment` with `androidx.fragment:fragment-ktx`.
            """,
        category = Category.PRODUCTIVITY,
        priority = 4,
        severity = Severity.INFORMATIONAL,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
        moreInfo = "https://developer.android.com/kotlin/ktx",
      )

    @JvmField
    val KAPT_USAGE_INSTEAD_OF_KSP =
      Issue.create(
        id = "KaptUsageInsteadOfKsp",
        briefDescription = "Kapt usage should be replaced with KSP",
        explanation =
          """
                KSP is a more efficient replacement for kapt. For libraries that support both, \
                KSP should be used to improve build times.
            """,
        category = Category.PERFORMANCE,
        priority = 4,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
        moreInfo = "https://developer.android.com/studio/build/migrate-to-ksp",
      )

    @JvmField
    val BOM_WITHOUT_PLATFORM =
      Issue.create(
        id = "BomWithoutPlatform",
        briefDescription = "Using a BOM without platform call",
        explanation =
          """
          When including a BOM, the dependency's coordinates must be wrapped \
          in a call to `platform()` for Gradle to interpret it correctly.
          """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION_WITH_TOML,
        moreInfo = "https://developer.android.com/r/tools/gradle-bom-docs",
      )

    @JvmField
    val JAVA_PLUGIN_LANGUAGE_LEVEL =
      Issue.create(
        id = "JavaPluginLanguageLevel",
        briefDescription = "No Explicit Java Language Level Given",
        explanation =
          """
                In modules using plugins deriving from the Gradle `java` plugin (e.g. \
                `java-library` or `application`), the java source and target compatibility \
                default to the version of the JDK being used to run Gradle, which may cause \
                compatibility problems with Android (or other) modules.

                You can specify an explicit sourceCompatibility and targetCompatibility in this \
                module to maintain compatibility no matter which JDK is used to run Gradle.
            """,
        category = Category.INTEROPERABILITY,
        priority = 6,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val JCENTER_REPOSITORY_OBSOLETE =
      Issue.create(
        id = "JcenterRepositoryObsolete",
        briefDescription = "JCenter Maven repository is read-only",
        explanation =
          """
                The JCenter Maven repository is no longer accepting submissions of Maven \
                artifacts since 31st March 2021.  Ensure that the project is configured \
                to search in repositories with the latest versions of its dependencies.
            """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        moreInfo = "https://developer.android.com/r/tools/jcenter-end-of-service",
      )

    @JvmField
    val PLAY_SDK_INDEX_NON_COMPLIANT =
      Issue.create(
        id = "PlaySdkIndexNonCompliant",
        briefDescription = "Library has policy issues in SDK Index",
        explanation =
          "This library version has policy issues that will block publishing in the Google Play Store.",
        category = Category.COMPLIANCE,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION_WITH_TOML,
        moreInfo = GOOGLE_PLAY_SDK_INDEX_URL,
        androidSpecific = true,
      )

    @JvmField
    val PLAY_SDK_INDEX_VULNERABILITY =
      Issue.create(
        id = "PlaySdkIndexVulnerability",
        briefDescription = "Library has vulnerability issues in SDK Index",
        explanation =
          "This library version has vulnerability issues that could block publishing in the Google Play Store.",
        category = Category.COMPLIANCE,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION_WITH_TOML,
        moreInfo = GOOGLE_PLAY_SDK_INDEX_URL,
        androidSpecific = true,
      )

    @JvmField
    val PLAY_SDK_INDEX_GENERIC_ISSUES =
      Issue.create(
        id = "PlaySdkIndexGenericIssues",
        briefDescription = "Library has issues in SDK Index",
        explanation =
          "This library version has issues that could block publishing in the Google Play Store.",
        category = Category.COMPLIANCE,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION_WITH_TOML,
        moreInfo = GOOGLE_PLAY_SDK_INDEX_URL,
        androidSpecific = true,
      )

    @JvmField
    val PLAY_SDK_INDEX_DEPRECATED =
      Issue.create(
        id = "PlaySdkIndexDeprecated",
        briefDescription = "Library is marked as deprecated in SDK Index",
        explanation =
          "This library has been deprecated, please consider updating to an alternative SDK before publishing a new release.",
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION_WITH_TOML,
        moreInfo = GOOGLE_PLAY_SDK_INDEX_URL,
        androidSpecific = true,
      )

    @JvmField
    val CHROMEOS_ABI_SUPPORT =
      Issue.create(
        id = "ChromeOsAbiSupport",
        briefDescription = "Missing ABI Support for ChromeOS",
        explanation =
          """
                To properly support ChromeOS, your Android application should have an x86 and/or x86_64 binary \
                as part of the build configuration. To fix the issue, ensure your files are properly optimized \
                for ARM; the binary translator will then ensure compatibility with x86. Alternatively, add an \
                `abiSplit` for x86 within your `build.gradle` file and create the required x86 dependencies.
            """,
        category = Category.CHROME_OS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        moreInfo = "https://developer.android.com/ndk/guides/abis",
        androidSpecific = true,
      )

    /** Gradle plugin IDs based on the Java plugin. */
    val JAVA_PLUGIN_IDS =
      listOf("java", "java-library", "application").flatMap { listOf(it, "org.gradle.$it") }

    /** The Gradle plugin ID for Android applications. */
    const val APP_PLUGIN_ID = "com.android.application"

    /** The Gradle plugin ID for Android libraries. */
    const val LIB_PLUGIN_ID = "com.android.library"

    /** Previous plugin id for applications. */
    const val OLD_APP_PLUGIN_ID = "android"

    /** Previous plugin id for libraries. */
    const val OLD_LIB_PLUGIN_ID = "android-library"

    /** All the plugin ids from the Android Gradle Plugin */
    val ALL_PLUGIN_IDS =
      setOf(
        "com.android.base",
        "com.android.application",
        "com.android.library",
        "com.android.test",
        "com.android.instant-app",
        "com.android.feature",
        "com.android.dynamic-feature",
        "com.android.settings",
      )

    /** Group ID for GMS. */
    const val GMS_GROUP_ID = "com.google.android.gms"

    const val FIREBASE_GROUP_ID = "com.google.firebase"
    const val GOOGLE_SUPPORT_GROUP_ID = "com.google.android.support"
    const val ANDROID_WEAR_GROUP_ID = "com.google.android.wearable"
    private const val WEARABLE_ARTIFACT_ID = "wearable"

    private val PLAY_SERVICES_V650 = Component.parse("$GMS_GROUP_ID:play-services:6.5.0")

    /**
     * Threshold to consider a versionCode very high and issue a warning.
     * https://developer.android.com/studio/publish/versioning.html indicates that the highest value
     * accepted by Google Play is 2100000000.
     */
    private const val VERSION_CODE_HIGH_THRESHOLD = 2000000000

    /** Returns the best guess for where a dependency is declared in the given project. */
    fun getDependencyLocation(context: Context, c: LintModelMavenName): Location {
      return getDependencyLocation(context, c.groupId, c.artifactId, c.version)
    }

    /** Returns the best guess for where a dependency is declared in the given project. */
    fun getDependencyLocation(
      context: Context,
      groupId: String,
      artifactId: String,
      version: String,
    ): Location {
      val client = context.client
      val projectDir = context.project.dir
      val withoutQuotes = "$groupId:$artifactId:$version"
      var location = guessGradleLocation(client, projectDir, withoutQuotes)
      if (location.start != null) return location
      // Try with just the group+artifact (relevant for example when using
      // version variables)
      location = guessGradleLocation(client, projectDir, "$groupId:$artifactId:")
      if (location.start != null) return location
      // Just the artifact -- important when using the other dependency syntax,
      // e.g. variations of
      //   group: 'comh.android.support', name: 'support-v4', version: '21.0.+'
      location = guessGradleLocation(client, projectDir, artifactId)
      if (location.start != null) return location
      // just the group: less precise but better than just the gradle file
      location = guessGradleLocation(client, projectDir, groupId)
      return location
    }

    /** Returns the best guess for where two dependencies are declared in a project. */
    fun getDependencyLocation(
      context: Context,
      address1: LintModelMavenName,
      address2: LintModelMavenName,
    ): Location {
      return getDependencyLocation(
        context,
        address1.groupId,
        address1.artifactId,
        address1.version,
        address2.groupId,
        address2.artifactId,
        address2.version,
      )
    }

    /** Returns the best guess for where two dependencies are declared in a project. */
    fun getDependencyLocation(
      context: Context,
      groupId1: String,
      artifactId1: String,
      version1: String,
      groupId2: String,
      artifactId2: String,
      version2: String,
      message: String? = null,
    ): Location {
      val location1 = getDependencyLocation(context, groupId1, artifactId1, version1)
      val location2 = getDependencyLocation(context, groupId2, artifactId2, version2)
      //noinspection FileComparisons
      if (location2.start != null || location1.file != location2.file) {
        location1.secondary = location2
        message?.let { location2.message = it }
      }
      return location1
    }

    fun getLatestVersionFromGradlePluginPortal(
      client: LintClient,
      pluginId: String,
      filter: Predicate<Version>?,
      allowPreview: Boolean,
    ): Version? {
      val pluginPath = pluginId.replace(".", "/")
      val url =
        "https://plugins.gradle.org/m2/$pluginPath/$pluginId.gradle.plugin/maven-metadata.xml"
      val updates =
        try {
          readUrlDataAsString(client, url, 20000)
        } catch (e: IOException) {
          client.log(
            null,
            "Could not connect to %1\$s to get the latest available version for plugin %2\$s",
            url,
            pluginId,
          )
          null
        }
      if (
        // for missing dependencies it answers with a json document
        updates != null && !updates.startsWith("{")
      ) {
        val document = XmlUtils.parseDocumentSilently(updates, false)
        if (document != null) {
          val versionsList = document.getElementsByTagName("versions")
          val versions = mutableListOf<Version>()

          for (i in 0 until versionsList.length) {
            val element = versionsList.item(i) as Element
            for (child in element) {
              if (child.tagName == "version") {
                val s = child.textContent
                if (s.isNotBlank()) {
                  val revision = Version.parse(s)
                  versions.add(revision)
                }
              }
            }
          }

          return versions
            .filter { filter == null || filter.test(it) }
            .filter { allowPreview || !it.isPreview }
            .maxOrNull()
        }
      }
      return null
    }

    /** TODO: Cache these results somewhere! */
    @JvmStatic
    fun getLatestVersionFromRemoteRepo(
      client: LintClient,
      dependency: Dependency,
      filter: Predicate<Version>?,
      allowPreview: Boolean,
    ): Version? {
      val group = dependency.group ?: return null
      val name = dependency.name
      val richVersion = dependency.version ?: return null
      val query = StringBuilder()
      val encoding = UTF_8.name()
      var allowPreview = allowPreview
      try {
        query.append("https://search.maven.org/solrsearch/select?q=g:%22")
        query.append(URLEncoder.encode(group, encoding))
        query.append("%22+AND+a:%22")
        query.append(URLEncoder.encode(name, encoding))
      } catch (e: UnsupportedEncodingException) {
        return null
      }

      query.append("%22&core=gav")
      if (group == "com.google.guava" || name == "kotlinx-coroutines-core") {
        // These libraries aren't releasing previews in their version strings;
        // instead, the suffix is used to indicate different variants (JRE vs Android,
        // JVM vs Kotlin Native).  Turn on allowPreview for the search.
        allowPreview = true
      } else if (filter == null && allowPreview) {
        query.append("&rows=1")
      }
      query.append("&wt=json")

      val response: String?
      try {
        response = readUrlDataAsString(client, query.toString(), 20000)
        if (response == null) {
          return null
        }
      } catch (e: IOException) {
        client.log(
          null,
          "Could not connect to maven central to look up the latest " +
            "available version for %1\$s",
          dependency,
        )
        return null
      }

      // Sample response:
      //    {
      //        "responseHeader": {
      //            "status": 0,
      //            "QTime": 0,
      //            "params": {
      //                "fl": "id,g,a,v,p,ec,timestamp,tags",
      //                "sort": "score desc,timestamp desc,g asc,a asc,v desc",
      //                "indent": "off",
      //                "q": "g:\"com.google.guava\" AND a:\"guava\"",
      //                "core": "gav",
      //                "wt": "json",
      //                "rows": "1",
      //                "version": "2.2"
      //            }
      //        },
      //        "response": {
      //            "numFound": 37,
      //            "start": 0,
      //            "docs": [{
      //                "id": "com.google.guava:guava:17.0",
      //                "g": "com.google.guava",
      //                "a": "guava",
      //                "v": "17.0",
      //                "p": "bundle",
      //                "timestamp": 1398199666000,
      //                "tags": ["spec", "libraries", "classes", "google", "code"],
      //                "ec": ["-javadoc.jar", "-sources.jar", ".jar", "-site.jar", ".pom"]
      //            }]
      //        }
      //    }

      // Look for version info:  This is just a cheap skim of the above JSON results.
      var index = response.indexOf("\"response\"")
      val versions = mutableListOf<Version>()
      while (index != -1) {
        index = response.indexOf("\"v\":", index)
        if (index != -1) {
          index += 4
          val start = response.indexOf('"', index) + 1
          val end = response.indexOf('"', start + 1)
          if (start in 0 until end) {
            val substring = response.substring(start, end)
            val revision = Version.parse(substring)
            versions.add(revision)
          }
        }
      }

      return versions
        .filter { filter == null || filter.test(it) }
        .filter { allowPreview || !it.isPreview }
        .maxOrNull()
    }

    private data class VersionCatalogDependency(
      val coordinates: String,
      val tomlValue: LintTomlValue,
    )

    /**
     * For the given library reference [expression] in the "libs.some.library.name" format, returns
     * the fully resolved coordinates of the library (including the version) and the corresponding
     * library declaration value in the version catalog.
     */
    private fun getDependencyFromVersionCatalog(
      expression: String,
      context: GradleContext,
    ): VersionCatalogDependency? {
      if (!expression.startsWith(VC_LIBRARY_PREFIX)) return null

      // Remove the "libs." prefix
      val libName = expression.substring(VC_LIBRARY_PREFIX.length)

      // Find current library declaration in catalog, accounting for the declaration
      // possibly using - and _ characters in the name
      val library =
        (context.getTomlValue(VC_LIBRARIES) as? LintTomlMapValue)
          ?.getMappedValues()
          ?.asIterable()
          ?.find { it.key.replace('-', '.').replace('_', '.') == libName }
          ?.value ?: return null

      // Find full coordinates of lib, including version
      val versions = context.getTomlValue(VC_VERSIONS) as? LintTomlMapValue
      val (coordinate, _) = getLibraryFromTomlEntry(versions, library) ?: return null

      return VersionCatalogDependency(coordinate, library)
    }

    /**
     * For the given plugin reference [expression] in the "libs.plugins.some.plugin.name" format,
     * returns the fully resolved coordinates of the plugin (including the version) and the
     * corresponding plugin declaration value in the version catalog.
     */
    private fun getPluginFromVersionCatalog(
      expression: String,
      context: GradleContext,
    ): VersionCatalogDependency? {
      if (!expression.startsWith(VC_PLUGIN_PREFIX)) return null

      // Remove the "libs.plugins." prefix
      val pluginName = expression.substring(VC_PLUGIN_PREFIX.length)

      // Find current plugin declaration in catalog, accounting for the declaration
      // possibly using - and _ characters in the name
      val plugin =
        (context.getTomlValue(VC_PLUGINS) as? LintTomlMapValue)
          ?.getMappedValues()
          ?.asIterable()
          ?.find { it.key.replace('-', '.').replace('_', '.') == pluginName }
          ?.value ?: return null

      // Find full coordinates of plugin, including version
      val versions = context.getTomlValue(VC_VERSIONS) as? LintTomlMapValue
      val (coordinate, _) = getPluginFromTomlEntry(versions, plugin) ?: return null

      return VersionCatalogDependency(coordinate, plugin)
    }

    // Convert a long-hand dependency, like
    //    group: 'com.android.support', name: 'support-v4', version: '21.0.+'
    // into an equivalent short-hand dependency, like
    //   com.android.support:support-v4:21.0.+
    @JvmStatic
    fun getNamedDependency(expression: String): String? {
      // if (value.startsWith("group: 'com.android.support', name: 'support-v4', version:
      // '21.0.+'"))
      if (expression.indexOf(',') != -1 && expression.contains("version:")) {
        var artifact: String? = null
        var group: String? = null
        var version: String? = null
        val splitter = Splitter.on(',').omitEmptyStrings().trimResults()
        for (property in splitter.split(expression)) {
          val colon = property.indexOf(':')
          if (colon == -1) {
            return null
          }
          var quote = '\''
          var valueStart = property.indexOf(quote, colon + 1)
          if (valueStart == -1) {
            quote = '"'
            valueStart = property.indexOf(quote, colon + 1)
          }
          if (valueStart == -1) {
            // For example, "transitive: false"
            continue
          }
          valueStart++
          val valueEnd = property.indexOf(quote, valueStart)
          if (valueEnd == -1) {
            return null
          }
          val value = property.substring(valueStart, valueEnd)
          when {
            property.startsWith("group:") -> group = value
            property.startsWith("name:") -> artifact = value
            property.startsWith("version:") -> version = value
          }
        }

        if (artifact != null && group != null && version != null) {
          return "$group:$artifact:$version"
        }
      }

      return null
    }

    private fun suggestApiConfigurationUse(project: Project, configuration: String): Boolean {
      return when {
        configuration.startsWith("test") || configuration.startsWith("androidTest") -> false
        else ->
          when (project.type) {
            LintModelModuleType.APP ->
              // Applications can only generally be consumed if there are dynamic features
              // (Ignoring the test-only project for this purpose)
              project.hasDynamicFeatures()
            LintModelModuleType.LIBRARY -> true
            LintModelModuleType.JAVA_LIBRARY -> true
            LintModelModuleType.FEATURE,
            LintModelModuleType.DYNAMIC_FEATURE -> true
            LintModelModuleType.TEST -> false
            LintModelModuleType.INSTANT_APP -> false
            LintModelModuleType.PRIVACY_SANDBOX_SDK -> false
            LintModelModuleType.FUSED_LIBRARY -> false
          }
      }
    }

    private fun targetJava8Plus(project: Project): Boolean {
      return getLanguageLevel(project, JDK_1_7).isAtLeast(JDK_1_8)
    }

    private fun hasLifecycleAnnotationProcessor(dependency: String) =
      dependency.contains("android.arch.lifecycle:compiler") ||
        dependency.contains("androidx.lifecycle:lifecycle-compiler")

    private fun isCommonAnnotationProcessor(dependency: String): Boolean =
      when (val index = dependency.lastIndexOf(":")) {
        -1 -> false
        else -> dependency.substring(0, index) in commonAnnotationProcessors
      }

    private enum class CompileConfiguration(private val compileConfigName: String) {
      API("api"),
      COMPILE("compile"),
      IMPLEMENTATION("implementation"),
      COMPILE_ONLY("compileOnly");

      private val annotationProcessor = "annotationProcessor"
      private val compileConfigSuffix = compileConfigName.usLocaleCapitalize()

      fun matches(configurationName: String): Boolean {
        return configurationName == compileConfigName ||
          configurationName.endsWith(compileConfigSuffix)
      }

      fun replacement(configurationName: String): String {
        return if (configurationName == compileConfigName) {
          annotationProcessor
        } else {
          configurationName.removeSuffix(compileConfigSuffix).appendCapitalized(annotationProcessor)
        }
      }
    }

    private val commonAnnotationProcessors: Set<String> =
      setOf(
        "com.jakewharton:butterknife-compiler",
        "com.github.bumptech.glide:compiler",
        "androidx.databinding:databinding-compiler",
        "com.google.dagger:dagger-compiler",
        "com.google.auto.service:auto-service",
        "android.arch.persistence.room:compiler",
        "android.arch.lifecycle:compiler",
        "io.realm:realm-annotations-processor",
        "com.google.dagger:dagger-android-processor",
        "androidx.room:room-compiler",
        "com.android.databinding:compiler",
        "androidx.lifecycle:lifecycle-compiler",
        "org.projectlombok:lombok",
        "com.google.auto.value:auto-value",
        "org.parceler:parceler",
        "com.github.hotchemi:permissionsdispatcher-processor",
        "com.alibaba:arouter-compiler",
        "org.androidannotations:androidannotations",
        "com.github.Raizlabs.DBFlow:dbflow-processor",
        "frankiesardo:icepick-processor",
        "org.greenrobot:eventbus-annotation-processor",
        "com.ryanharter.auto.value:auto-value-gson",
        "io.objectbox:objectbox-processor",
        "com.arello-mobile:moxy-compiler",
        "com.squareup.dagger:dagger-compiler",
        "io.realm:realm-android",
        "com.bluelinelabs:logansquare-compiler",
        "com.tencent.tinker:tinker-android-anno",
        "com.raizlabs.android:DBFlow-Compiler",
        "com.google.auto.factory:auto-factory",
        "com.airbnb:deeplinkdispatch-processor",
        "com.alipay.android.tools:androidannotations",
        "org.permissionsdispatcher:permissionsdispatcher-processor",
        "com.airbnb.android:epoxy-processor",
        "org.immutables:value",
        "com.github.stephanenicolas.toothpick:toothpick-compiler",
        "com.mindorks.android:placeholderview-compiler",
        "com.github.frankiesardo:auto-parcel-processor",
        "com.hannesdorfmann.fragmentargs:processor",
        "com.evernote:android-state-processor",
        "org.mapstruct:mapstruct-processor",
        "com.iqiyi.component.router:qyrouter-compiler",
        "com.iqiyi.component.mm:mm-compiler",
        "dk.ilios:realmfieldnameshelper",
        "com.lianjia.common.android.router2:compiler",
        "com.smile.gifshow.annotation:invoker_processor",
        "com.f2prateek.dart:dart-processor",
        "com.sankuai.waimai.router:compiler",
        "org.qiyi.card:card-action-compiler",
        "com.iqiyi.video:eventbus-annotation-processor",
        "ly.img.android.pesdk:build-processor",
        "org.apache.logging.log4j:log4j-core",
        "com.github.jokermonn:permissions4m",
        "com.arialyy.aria:aria-compiler",
        "com.smile.gifshow.annotation:provide_processor",
        "com.smile.gifshow.annotation:preference_processor",
        "com.smile.gifshow.annotation:plugin_processor",
        "org.inferred:freebuilder",
        "com.smile.gifshow.annotation:router_processor",
      )

    // From https://kotlinlang.org/docs/ksp-overview.html#supported-libraries
    private val annotationProcessorsWithKspReplacements: Map<String, String> =
      mapOf(
        // Note: this is the only dependency where coordinates actually have to be updated
        "com.github.bumptech.glide:compiler" to "com.github.bumptech.glide:ksp",
        "androidx.room:room-compiler" to "androidx.room:room-compiler",
        "com.squareup.moshi:moshi-kotlin-codegen" to "com.squareup.moshi:moshi-kotlin-codegen",
        "com.github.liujingxing.rxhttp:rxhttp-compiler" to
          "com.github.liujingxing.rxhttp:rxhttp-compiler",
        "se.ansman.kotshi:compiler" to "se.ansman.kotshi:compiler",
        "com.linecorp.lich:savedstate-compiler" to "com.linecorp.lich:savedstate-compiler",
        "io.github.amrdeveloper:easyadapter-compiler" to
          "io.github.amrdeveloper:easyadapter-compiler",
        "com.airbnb:deeplinkdispatch-processor" to "com.airbnb:deeplinkdispatch-processor",
        "com.airbnb.android:epoxy-processor" to "com.airbnb.android:epoxy-processor",
        "com.airbnb.android:paris-processor" to "com.airbnb.android:paris-processor",
      )

    private val commonBoms: Set<String> =
      setOf(
        // Google
        "androidx.compose:compose-bom",
        "com.google.firebase:firebase-bom",
        // JetBrains
        "org.jetbrains.kotlin:kotlin-bom",
        "org.jetbrains.kotlinx:kotlinx-coroutines-bom",
        "io.ktor:ktor-bom",
        // Network and serialization
        "com.squareup.okio:okio-bom",
        "com.squareup.okhttp3:okhttp-bom",
        "com.squareup.wire:wire-bom",
        "com.fasterxml.jackson:jackson-bom",
        "io.grpc:grpc-bom",
        "org.http4k:http4k-bom",
        "org.http4k:http4k-connect-bom",
        // Testing
        "org.junit:junit-bom",
        "io.kotest:kotest-bom",
        "io.cucumber:cucumber-bom",
        // Others
        "io.arrow-kt:arrow-stack",
        "io.sentry:sentry-bom",
        "dev.chrisbanes.compose:compose-bom",
        "org.ow2.asm:asm-bom",
        "software.amazon.awssdk:bom",
        "com.walletconnect:android-bom",
      )

    private fun libraryHasKtxExtension(mavenName: String): Boolean {
      // From https://developer.android.com/kotlin/ktx/extensions-list.
      return when (mavenName) {
        "androidx.activity:activity",
        "androidx.collection:collection",
        "androidx.core:core",
        "androidx.dynamicanimation:dynamicanimation",
        "androidx.fragment:fragment",
        "androidx.lifecycle:lifecycle-livedata-core",
        "androidx.lifecycle:lifecycle-livedata",
        "androidx.lifecycle:lifecycle-reactivestreams",
        "androidx.lifecycle:lifecycle-runtime",
        "androidx.lifecycle:lifecycle-viewmodel",
        "androidx.navigation:navigation-runtime",
        "androidx.navigation:navigation-fragment",
        "androidx.navigation:navigation-ui",
        "androidx.paging:paging-common",
        "androidx.paging:paging-runtime",
        "androidx.paging:paging-rxjava2",
        "androidx.palette:palette",
        "androidx.preference:preference",
        "androidx.slice:slice-builders",
        "androidx.sqlite:sqlite",
        "com.google.android.play:core" -> true
        else -> false
      }
    }

    @JvmStatic
    var playSdkIndexFactory: (Path?, LintClient) -> GooglePlaySdkIndex =
      { path: Path?, client: LintClient ->
        val index =
          object : GooglePlaySdkIndex(path) {
            public override fun readUrlData(url: String, timeout: Int, lastModified: Long) =
              readUrlData(client, url, timeout, lastModified)

            override fun error(throwable: Throwable, message: String?) {
              client.log(throwable, message)
            }
          }
        index.initialize()
        index
      }
  }
}

private infix fun <T : Comparable<T>> T?.maxOrNull(other: T?): T? =
  when {
    this == null -> other
    other == null -> this
    else -> if (this > other) this else other
  }

private infix fun Version?.maxAgpOrNull(other: Version?): Version? =
  (this?.let { AgpVersion.tryParse(it.toString()) } maxOrNull
      other?.let { AgpVersion.tryParse(it.toString()) })
    ?.let { Version.parse(it.toString()) }

/**
 * This exists to smooth over the fact that we represent the Version of a prefix matcher as the
 * least possible version that would match, but we want here to find newer versions that would not
 * match (e.g. if [dependency] has a version specification of 1.0.+ we should return false for a
 * [Version] of 1.0.2, but true for a [Version] of 1.1.0.
 *
 * A clearer implementation fix for this is to have two Version getters for GradleCoordinate:
 * getLowerBoundVersion and getUpperBoundVersion (both of which are computable) and to use the
 * appropriate one in the right context (in most of this file, the upper bound).
 */
private fun Version?.isNewerThan(dependency: Dependency): Boolean {
  val richVersion = dependency.version
  val maybeSingleton = dependency.explicitSingletonVersion
  return when {
    this == null -> false
    richVersion == null -> true
    maybeSingleton != null -> this > maybeSingleton
    richVersion.lowerBound > this -> false
    else -> !richVersion.contains(this)
  }
}

private fun Version?.isAgpNewerThan(dependency: Dependency): Boolean {
  val richVersion = dependency.version
  val maybeSingleton =
    dependency.explicitSingletonVersion?.let { AgpVersion.tryParse(it.toString()) }
  val thisAgpVersion = this?.let { AgpVersion.tryParse(it.toString()) }
  val lowerBoundAgpVersion = richVersion?.lowerBound?.let { AgpVersion.tryParse(it.toString()) }
  return when {
    this == null || thisAgpVersion == null -> false
    richVersion == null -> true
    maybeSingleton != null -> thisAgpVersion > maybeSingleton
    // these are an approximation.  If we can parse the version's lower bound as
    // an AgpVersion, good, use it to compare; it's probably a pseudo-singleton.
    // If we can't, then fall back to the Gradle-based version containment logic.
    lowerBoundAgpVersion != null && lowerBoundAgpVersion > thisAgpVersion -> false
    else -> !richVersion.contains(this)
  }
}
