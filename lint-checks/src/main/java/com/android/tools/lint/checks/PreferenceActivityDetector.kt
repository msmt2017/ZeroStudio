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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.checks.SecurityDetector.getExplicitExported
import com.android.tools.lint.checks.SecurityDetector.isImplicitlyExportedPreS
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.resolveManifestName
import com.android.tools.lint.detector.api.targetSdkLessThan
import com.android.utils.subtag
import com.android.utils.subtags
import com.intellij.psi.PsiClass
import java.util.EnumSet
import org.jetbrains.uast.UClass
import org.w3c.dom.Element

/** Ensures that PreferenceActivity and its subclasses are never exported. */
class PreferenceActivityDetector : Detector(), XmlScanner, SourceCodeScanner {

  // ---- Implements XmlScanner ----

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_ACTIVITY)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val explicitlyDecided = getExplicitExported(element)
    val implicitlyExportedPreS = explicitlyDecided == null && isImplicitlyExportedPreS(element)
    if (implicitlyExportedPreS || explicitlyDecided == true) {
      val project = context.project
      val className = resolveManifestName(element, project)
      if (className == PREFERENCE_ACTIVITY) {
        val message = "`PreferenceActivity` should not be exported"
        val incident = Incident(ISSUE, element, context.getLocation(element), message)
        if (implicitlyExportedPreS) {
          context.report(incident, targetSdkLessThan(31))
        } else {
          context.report(incident)
        }
      } else {
        val parser = context.client.getUastParser(project)
        val evaluator = parser.evaluator
        val declaration = evaluator.findClass(className.replace('$', '.'))
        if (declaration != null && evaluator.extendsClass(declaration, PREFERENCE_ACTIVITY, true)) {
          val overrides = overridesIsValidFragment(evaluator, declaration)
          val message = "`PreferenceActivity` subclass $className should not be exported"
          val location: Location = context.getLocation(element)
          if (context.driver.isIsolated()) {
            location.secondary = context.getLocation(declaration)
          }
          val incident = Incident(ISSUE, element, location, message)
          context.report(
            incident,
            map().put(KEY_OVERRIDES, overrides).put(KEY_IMPLICIT, implicitlyExportedPreS),
          )
        }
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.targetSdk < 19) return true
    if (map.getBoolean(KEY_IMPLICIT, false) == true && context.mainProject.targetSdk >= 31)
      return true
    return map.getBoolean(KEY_OVERRIDES, false) == false
  }

  // ---- implements SourceCodeScanner ----

  override fun applicableSuperClasses(): List<String> {
    return listOf(PREFERENCE_ACTIVITY)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (!context.project.reportIssues || !context.driver.isIsolated()) {
      return
    }

    val evaluator = context.evaluator
    if (!evaluator.extendsClass(declaration.javaPsi, PREFERENCE_ACTIVITY, false)) {
      return
    }
    val className = declaration.qualifiedName ?: return
    val element = findManifestElement(context, className) ?: return
    val explicitlyDecided: Boolean? = getExplicitExported(element)
    val implicitlyExportedPreS = explicitlyDecided == null && isImplicitlyExportedPreS(element)

    if (implicitlyExportedPreS || explicitlyDecided == true) {
      // Ignore the issue if we target an API greater than 19 and the class in
      // question specifically overrides isValidFragment() and thus knowingly allows
      // valid fragments.
      val overrides = overridesIsValidFragment(evaluator, declaration.javaPsi)
      val message =
        "`PreferenceActivity` subclass $className should not be exported in the manifest"
      // When linting incrementally just in the Java class, place the error on
      // the class itself rather than the export line in the manifest
      val location = context.getNameLocation(declaration)
      val incident = Incident(ISSUE, declaration, location, message)
      context.report(
        incident,
        map().put(KEY_OVERRIDES, overrides).put(KEY_IMPLICIT, implicitlyExportedPreS),
      )
    }
  }

  private fun findManifestElement(context: JavaContext, className: String): Element? {
    val project = if (context.isGlobalAnalysis()) context.mainProject else context.project
    val mergedManifest = project.mergedManifest ?: return null
    val manifest = mergedManifest.documentElement ?: return null
    val application = manifest.subtag(TAG_APPLICATION) ?: return null
    for (element in application.subtags(TAG_ACTIVITY)) {
      val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (className.endsWith(name)) {
        val fqn = resolveManifestName(element, project)
        if (fqn == className) {
          return element
        }
      }
    }

    return null
  }

  private fun overridesIsValidFragment(evaluator: JavaEvaluator, resolvedClass: PsiClass): Boolean {
    for (method in resolvedClass.findMethodsByName(IS_VALID_FRAGMENT, false)) {
      if (evaluator.parametersMatch(method, TYPE_STRING)) {
        return true
      }
    }
    return false
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(
        PreferenceActivityDetector::class.java,
        EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE,
      )

    @JvmField
    val ISSUE =
      Issue.create(
          id = "ExportedPreferenceActivity",
          briefDescription = "PreferenceActivity should not be exported",
          explanation =
            """
                Fragment injection gives anyone who can send your `PreferenceActivity` an intent \
                the ability to load any fragment, with any arguments, in your process.""",
          moreInfo =
            //noinspection LintImplUnexpectedDomain
            "http://securityintelligence.com/new-vulnerability-android-framework-fragment-injection",
          category = Category.SECURITY,
          priority = 8,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
        )
        .addMoreInfo("https://goo.gle/ExportedPreferenceActivity")

    private const val PREFERENCE_ACTIVITY = "android.preference.PreferenceActivity"
    private const val IS_VALID_FRAGMENT = "isValidFragment"
    private const val KEY_OVERRIDES = "overrides"
    private const val KEY_IMPLICIT = "implicit"
  }
}
