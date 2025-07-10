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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.uast.UCallExpression

/**
 * Reports all calls to setCommunicationDevice, unless there is a call to clearCommunicationDevice.
 *
 * CommunicationDeviceDetector is an example of a detector that supports partial analysis by storing
 * data in per module LintMaps. It also demonstrates how such a detector can usually immediately
 * work in global analysis mode by simply overriding [checkMergedProject] and adding a few lines of
 * code. So that this can serve as the "reference example" for such detectors, we include detailed
 * comments explaining how the analysis works.
 *
 * Recall from the lint documentation that, in partial analysis mode, lint runs in two phases:
 * - analyze: where lint analyzes the source code of each module in isolation, and stores some
 *   intermediate data for each module.
 * - report: where lint accesses the previously stored data for all modules and uses this to report
 *   incidents. The source code of modules cannot be accessed at this stage.
 *
 * CommunicationDeviceDetector does the following: if there are no calls to clearCommunicationDevice
 * then report all calls to setCommunicationDevice as incidents. In the analysis phase, the detector
 * can only access the source code for the current module being analyzed. Thus, so that the analysis
 * works correctly in partial analysis mode, the detector stores, per module:
 * - the location of every setCommunicationDevice call
 * - whether the module calls clearCommunicationDevice
 *
 * See [visitMethodCall], below.
 *
 * In the report phase, the detector inspects the intermediate data of every module to check whether
 * _any_ module called clearCommunicationDevice. If not, then the detector gets the location of
 * every setCommunicationDevice call from the intermediate data, and reports each location as an
 * incident. See [checkPartialResults], below.
 *
 * Finally, rather than specializing the check for partial and global analysis modes, the detector
 * just overrides [checkMergedProject] such that if we are in global analysis mode, we call
 * [checkPartialResults]. See [checkMergedProject], below.
 */
class CommunicationDeviceDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(CommunicationDeviceDetector::class.java, EnumSet.of(Scope.ALL_JAVA_FILES))

    /** Calling `setCommunicationDevice()` without `clearCommunicationDevice()` */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "SetAndClearCommunicationDevice",
        briefDescription = "Clearing communication device",
        explanation =
          """
                After selecting the audio device for communication use cases using \
                `setCommunicationDevice(AudioDeviceInfo device)`, the selection is active as long \
                as the requesting application process lives, until `clearCommunicationDevice()` \
                is called or until the device is disconnected. It is therefore important to clear \
                the request when a call ends or the requesting activity or service is stopped or \
                destroyed.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    const val FOUND_CLEAR_COMMUNICATION_DEVICE = "foundClearCommunicationDevice"

    const val MIN_TARGET_SDK_FOR_SET_COMMUNICATION_DEVICE = 31
  }

  override fun getApplicableMethodNames() =
    listOf("setCommunicationDevice", "clearCommunicationDevice")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // Called during the analysis phase. Store intermediate data from the
    // module. Per module intermediate data is stored in a LintMap, with String
    // keys mapping to values of various types.

    // Get the LintMap for the current module being analyzed.
    val map = context.getPartialResults(ISSUE).map()

    if (method.getFqName() == "android.media.AudioManager.setCommunicationDevice") {
      // If we see a call to setCommunicationDevice then store the location in
      // the LintMap. We use keys "0", "1", "2", ...etc., because we just want
      // to store a list of locations. We can do this easily by using
      // "${map.size}" as the key.

      // When later reporting incidents in checkPartialResults, automatic
      // suppression via source code (e.g. via a comment or annotation) will not
      // work because the source code is no longer available in the report
      // phase. Thus, we must manually check if the ISSUE is suppressed in
      // source code now and, if so, skip adding the location.
      if (!context.driver.isSuppressed(context, ISSUE, node)) {
        map.put("${map.size}", context.getLocation(node))
      }
    } else if (method.getFqName() == "android.media.AudioManager.clearCommunicationDevice") {
      // If we see a call to clearCommunicationDevice then we store this fact by
      // adding to the LintMap: "foundClearCommunicationDevice" -> true
      map.put(FOUND_CLEAR_COMMUNICATION_DEVICE, true)
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // Called during the report phase for each Issue (created via Issue.create)
    // handled by your detector. Note that if you have multiple Issues handled
    // by your detector then you should check which Issue is currently being
    // processed via partialResults.issue. In this case, we only have one Issue,
    // so we can assume we are processing the partial results for ISSUE.
    //
    // Implement this method by reading intermediate data from all modules and
    // reporting incidents.
    //
    // Typically, this method is called once (per Issue) for the app module that
    // is being checked, such that context.mainProject is set to the app module;
    // the source code of the app module and dependent library modules will have
    // already been analyzed such that their intermediate data is available via
    // partialResults. However, it is also possible to run lint on a library
    // module, without any app module; the specific library module and dependent
    // library modules will be analyzed, and then this method is called during
    // the report phase with context.mainProject set to the specific library
    // module.

    // If the main project is a library module, we don't want to report any
    // incidents. We essentially only want to process the intermediate data from
    // modules when we are checking an app module because this is the only point
    // where we can see intermediate data for all modules that are part of the
    // final app, so we can see if one of the modules calls
    // clearCommunicationDevice.
    if (context.mainProject.isLibrary) return

    // setCommunicationDevice and clearCommunicationDevice were introduced in
    // API level 31, so don't report any incidents if the app manifest target
    // API level is lower than this.
    if (context.mainProject.targetSdk < MIN_TARGET_SDK_FOR_SET_COMMUNICATION_DEVICE) return

    // Note that this method is also a good place to read
    // context.mainProject.mergedManifest. (If a detector does not need to
    // override checkPartialResults, then the merged manifest can be read in
    // checkMergedProject.) In particular, note that if the detector needs to
    // read the app's merged manifest, there is usually no point in visiting
    // various XML elements of each module's manifest in the analysis phase.

    // If we can find the "foundClearCommunicationDevice" key in _any_ per
    // module LintMap then return early, reporting no incidents.
    for (perModuleLintMap in partialResults.maps()) {
      if (perModuleLintMap.containsKey(FOUND_CLEAR_COMMUNICATION_DEVICE)) {
        return
      }
    }

    // Otherwise, every value in every per module LintMap is a location of a
    // call to setCommunicationDevice that we need to report.
    for (perModuleLintMap in partialResults.maps()) {
      for (key in perModuleLintMap) {
        context.report(
          Incident(context)
            .issue(ISSUE)
            .location(perModuleLintMap.getLocation(key)!!)
            .message("Must call `clearCommunicationDevice()` " + "after `setCommunicationDevice()`")
        )
      }
    }
  }

  override fun checkMergedProject(context: Context) {
    // Called after analysis of the root/main project (usually the app project),
    // in both partial analysis and global analysis modes.

    // When lint is running in global analysis mode (e.g. via Code -> Inspect
    // Code... from within the IDE), one detector instance is used to check all
    // modules, and the source code of all modules is available. Thus, the check
    // could be implemented in a different way (e.g. a boolean field could be
    // set as soon as we visit a call to clearCommunicationDevice, and we could
    // try to stop doing further analysis). However, such specialization is
    // often not worthwhile and makes the detector harder to understand,
    // especially if your analysis is already structured around LintMaps.
    // Furthermore, per module LintMaps are still available in global analysis
    // mode. Thus, a detector that uses LintMaps should "just work" in global
    // analysis mode, with one caveat; checkPartialResults is not called in
    // global analysis mode. Therefore, we call it here if we are in global
    // analysis mode.
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
      // Note: Add a call for each additional Issue (created via Issue.create)
      // that your detector handles.
    }
  }

  private fun PsiElement.getFqName(): String? =
    when (val element = namedUnwrappedElement) {
      is PsiMember ->
        element.getName()?.let { name ->
          val prefix = element.containingClass?.qualifiedName
          (if (prefix != null) "$prefix.$name" else name)
        }
      is KtNamedDeclaration -> element.fqName.toString()
      else -> null
    }
}
