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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_INTENT
import com.android.SdkConstants.CLASS_INTENT_FILTER
import com.android.SdkConstants.EXT_JAVA
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_PROVIDER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.lint.checks.BroadcastReceiverUtils.BROADCAST_RECEIVER_METHOD_NAMES
import com.android.tools.lint.client.api.TYPE_CLASS
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.resolveManifestName
import com.android.utils.iterator
import com.android.utils.subtag
import com.intellij.psi.PsiMethod
import java.util.TreeSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Element

/**
 * Finds locations in source code where an Intent is created:
 * - with some action A (a string)
 * - without specifying a component (such as an activity, receiver, etc.) name or package name
 * - that is used in a call to sendBroadcast or startActivity
 * - such that there exists a component (such as an activity, receiver, etc.), usually defined in a
 *   manifest, that responds to action A, and the component has android:exported="false"
 *
 * Reports the call where the action is set to A.
 *
 * This scenario most likely indicates that the developer wants to trigger the specific non-exported
 * component, and so they should specify the component (or at least the package) explicitly so that
 * a malicious app cannot intercept the intent.
 *
 * For example:
 *
 * In some Kotlin code:
 * ```kotlin
 * val intent = Intent("some.fake.action.LAUNCH")
 * // Fix: intent.setClassName(/* application ID */ packageName, /* class name */ "test.pkg.TestActivity")
 * startActivity(intent)
 * ```
 *
 * In the manifest:
 * ```
 * <activity android:name=".TestActivity" android:exported="false">
 *   <intent-filter>
 *     <action android:name="some.fake.action.LAUNCH" />
 *   </intent-filter>
 * </activity>
 * ```
 *
 * Note that we should NOT hardcode the application ID (application package name) in quick fixes.
 *
 * Partial results data is:
 * ```
 *  { ACTIONS_SENT_KEY -> Map<String, List<Location>>,    // action name to list (LintMap) of locations
 *    ACTIONS_REGISTERED_NON_EXPORTED_KEY -> Set<String>, // set (LintMap) of action names
 *  }
 * ```
 *
 * ACTIONS_SENT_KEY: the action names used in Intents, and where they are set.
 * ACTIONS_REGISTERED_NON_EXPORTED_KEY: the action names that the app has dynamically (not via a
 * manifest) registered to respond to by calling registerReceiver.
 *
 * Note that we add a suffix to action names that indicates how the action is used. For example,
 * [ACTIVITY_ACTION_SUFFIX] indicates that the action name is used to start an activity. This means,
 * for example, an action/intent that is used to send a broadcast will not trigger a warning for a
 * non-exported activity.
 *
 * Note that services are ignored because an exception is thrown for implicit service intents since
 * Lollipop: https://developer.android.com/about/versions/lollipop/android-5.0-changes#BindService
 */
class UnsafeImplicitIntentDetector : Detector(), SourceCodeScanner {

  override fun getApplicableConstructorTypes() = listOf(CLASS_INTENT)

  override fun getApplicableMethodNames() = BROADCAST_RECEIVER_METHOD_NAMES

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // This is a call to registerReceiver*(...), which allows the app to
    // dynamically register actions that it responds to. We store these actions
    // in our partial results map.

    // Must be a call on Context.
    if (
      !context.evaluator.isMemberInSubClassOf(method, CLASS_CONTEXT) &&
        !context.evaluator.isMemberInSubClassOf(method, CLASS_CONTEXT_COMPAT)
    )
      return

    // If we can't evaluate the flags arg to see that it includes
    // RECEIVER_NOT_EXPORTED then we give up.
    val evaluator = ConstantEvaluator().allowFieldInitializers()
    val flagsArg = UastLintUtils.findArgument(node, TYPE_INT) ?: return
    val flagsVal = evaluator.evaluate(flagsArg) as? Int ?: return
    if ((flagsVal and RECEIVER_NOT_EXPORTED) == 0) return

    // Get the actions registered via the IntentFilter argument.
    val filterArg = UastLintUtils.findArgument(node, CLASS_INTENT_FILTER) ?: return
    val (_, unprotectedActionsList) =
      BroadcastReceiverUtils.checkIsProtectedReceiverAndReturnUnprotectedActions(
        filterArg,
        node,
        context.evaluator,
      )

    // Add all registered actions to the partial results map. Note that we add
    // the broadcast suffix because only actions that are used to send a
    // broadcast should trigger a warning.
    val actionsLintMap =
      context.getPartialResults(ISSUE).map().getOrPutLintMap(ACTIONS_REGISTERED_NON_EXPORTED_KEY)
    for (action in unprotectedActionsList) {
      actionsLintMap.put("$action$BROADCAST_ACTION_SUFFIX", true)
    }
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    // This is an Intent constructor. We will track the Intent to see if it
    // satisfies various conditions.

    // Skip constructors that take a class (and thus create an intent for a
    // specific component).
    if (constructor.parameterList.parameters.any { it.type.canonicalText == TYPE_CLASS }) {
      return
    }

    // Skip the copy constructor.
    if (context.evaluator.parametersMatch(constructor, CLASS_INTENT)) {
      return
    }

    // We will collect all action name strings that we can see flowing into the
    // intent. For example, if we see:
    //
    //  if (c) {
    //    intent.setAction("abc");
    //  } else {
    //    intent.setAction("def");
    //    intent.setAction("ghi");
    //  }
    //
    // then we consider "abc", "def", "ghi". This is better than just taking the
    // last action name "ghi", but there can be false-positives ("def"). This
    // should be acceptable in most cases.
    val actionToLocationsMap: MutableMap<String, MutableList<Location>> = LinkedHashMap()

    fun addActionNameAndLocationFromFirstArg(call: UCallExpression) {
      call.valueArguments.firstOrNull()?.let { argExpr ->
        ConstantEvaluator.evaluateString(context, argExpr, false)?.let { action ->
          // With partial analysis, we must manually check for suppression
          // because the source code and/or AST of this file will not be
          // available when incidents are later reported, which is when
          // automatic suppression is done.
          if (!context.driver.isSuppressed(context, ISSUE, node)) {
            actionToLocationsMap.getOrPut(action) { ArrayList() }.add(context.getLocation(call))
          }
        }
      }
    }

    // Add the initial action name (if it is provided in the constructor, and we
    // can evaluate it).
    addActionNameAndLocationFromFirstArg(node)

    // Note regarding isIntentImplicit: if an intent has its package set then we
    // say it is explicit (even though it is still technically implicit).
    var isIntentImplicit = true
    var isUsedForActivity = false
    var isUsedForBroadcast = false
    val visitor =
      object : DataFlowAnalyzer(setOf(node)) {

        override fun visitElement(node: UElement): Boolean {
          // As soon as we think the Intent might be explicit, we can stop.
          // Returning true stops visiting.
          return !isIntentImplicit
        }

        override fun receiver(call: UCallExpression) {
          when (call.methodName) {
            "setComponent",
            "setClass",
            "setPackage",
            "setClassName" -> {
              isIntentImplicit = false
            }
            "setAction" -> addActionNameAndLocationFromFirstArg(call)
          }
        }

        override fun argument(call: UCallExpression, reference: UElement) {
          when (call.methodName) {
            // TODO: We should track the intent being added to an array and then
            //  detect use of startActivities(...).
            "startActivity" -> {
              isUsedForActivity = true
            }
            "sendBroadcast",
            "sendBroadcastAsUser" -> {
              isUsedForBroadcast = true
            }
          // Ignore methods for starting services; an exception is thrown for
          // implicit service intents since Lollipop.
          }
        }
      }

    val parent = node.getParentOfType(UMethod::class.java) ?: return
    parent.accept(visitor)

    // If the intent is explicit, or we could not see the intent being used then
    // we give up. We will miss cases where the intent is used later (for
    // example, it is returned, passed to some other method, stored to a field,
    // etc.).
    val isUsed = isUsedForActivity || isUsedForBroadcast
    if (!isIntentImplicit || !isUsed) {
      return
    }

    // Add all actions to the partial results map.
    val actionToLocationsLintMap =
      context.getPartialResults(ISSUE).map().getOrPutLintMap(ACTIONS_SENT_KEY)

    fun addActionToLintMap(actionWithSuffix: String, locations: MutableList<Location>) {
      val locationsLintMap = actionToLocationsLintMap.getOrPutLintMap(actionWithSuffix)
      for (location in locations) {
        locationsLintMap.put("${locationsLintMap.size}", location)
      }
    }

    // We add the action name with a suffix that indicates how the intent/action
    // is used. The intent might appear to be used in multiple ways, so we might
    // add the action name more than once with different suffixes.
    for ((action, locations) in actionToLocationsMap) {
      if (isUsedForActivity) {
        addActionToLintMap("$action$ACTIVITY_ACTION_SUFFIX", locations)
      }
      if (isUsedForBroadcast) {
        addActionToLintMap("$action$BROADCAST_ACTION_SUFFIX", locations)
      }
    }
  }

  /**
   * Returns the action names within non-exported components from the manifest [root]. Each action
   * name is mapped to the set of non-exported component names. Note that the action names have the
   * appropriate suffix added indicating the type of use that would trigger the component; for
   * example, [ACTIVITY_ACTION_SUFFIX].
   */
  private fun getActionToNonExportedComponents(
    project: Project,
    root: Element,
  ): Map<String, Set<String>> {
    // E.g.
    // <application ...>
    //   <activity android:name=".TestActivity" android:exported="false" ...>
    //     <intent-filter>
    //       <action android:name="some.action.LAUNCH" />
    //       <action android:name="some.other.action.LAUNCH" />
    //     ...
    val application = root.subtag(TAG_APPLICATION) ?: return emptyMap()
    val result = HashMap<String, MutableSet<String>>()
    for (component in application) {
      // Given the component type (indicated by the tag name), we will add the
      // corresponding suffix to the action name.
      val suffix =
        when (component.tagName) {
          TAG_ACTIVITY,
          TAG_ACTIVITY_ALIAS -> ACTIVITY_ACTION_SUFFIX
          // Ignore services; an exception is thrown for implicit service intents since
          // Lollipop.
          TAG_SERVICE -> ""
          TAG_RECEIVER -> BROADCAST_ACTION_SUFFIX
          TAG_PROVIDER -> "" // Ignore ContentProviders, which are not accessed using Intents.
          else -> ""
        }
      if (suffix.isEmpty()) continue
      val componentName = resolveManifestName(component, project)
      // The default value for android:exported varies; on recent Android
      // versions, it must be specified explicitly. If we can't see
      // android:exported="true" then we assume the component is not
      // exported.
      val notExported =
        component.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED)?.value != VALUE_TRUE
      if (!notExported) continue
      for (intentFilter in component) {
        if (intentFilter.tagName != TAG_INTENT_FILTER) continue
        for (actionTag in intentFilter) {
          if (actionTag.tagName != TAG_ACTION) continue
          val actionName = actionTag.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)?.value ?: continue
          // Use TreeSet to get a sorted list of the components for
          // deterministic reports.
          val nonExportedComponents = result.getOrPut("$actionName$suffix") { TreeSet() }
          nonExportedComponents.add(componentName)
        }
      }
    }
    return result
  }

  override fun afterCheckRootProject(context: Context) {
    // A detector that uses partial results and LintMaps should just work in
    // global analysis mode, with one caveat; checkPartialResults is not called
    // in global analysis mode. Therefore, we call it here if we are in global
    // analysis mode.
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // Combine ACTIONS_SENT_KEY partial results.
    val actionSentToLocationsMap: MutableMap<String, MutableList<Location>> = LinkedHashMap()
    for (perModuleLintMap in partialResults.maps()) {
      val actionToLocationsLintMap = perModuleLintMap.getMap(ACTIONS_SENT_KEY) ?: continue
      for (action in actionToLocationsLintMap) {
        val locationsLintMap = actionToLocationsLintMap.getMap(action) ?: continue
        val locations = actionSentToLocationsMap.getOrPut(action) { ArrayList() }
        for (key in locationsLintMap) {
          val location = locationsLintMap.getLocation(key) ?: continue
          locations.add(location)
        }
      }
    }

    // Combine ACTIONS_REGISTERED_NON_EXPORTED_KEY partial results.
    val actionsRegistered: MutableSet<String> = HashSet()
    for (perModuleLintMap in partialResults.maps()) {
      val actionsLintMap = perModuleLintMap.getMap(ACTIONS_REGISTERED_NON_EXPORTED_KEY) ?: continue
      for (action in actionsLintMap) {
        actionsRegistered.add(action)
      }
    }

    // Get actions for non-exported components from the manifest.
    val mergedManifestDocument = context.mainProject.mergedManifest?.documentElement
    val actionToNonExportedComponents =
      mergedManifestDocument?.let { getActionToNonExportedComponents(context.project, it) }
        ?: emptyMap()

    // Report actions that match non-exported components.
    for ((action, locations) in actionSentToLocationsMap) {
      // Try the manifest components first.
      val nonExportedComponents = actionToNonExportedComponents[action]
      if (nonExportedComponents != null) {
        val firstComponent = nonExportedComponents.firstOrNull() ?: continue
        val message =
          "The intent action `$action` matches the intent filter of a non-exported " +
            "component `$firstComponent` from a manifest. " +
            "If you are trying to invoke this specific component via the action " +
            "then you should make the intent explicit by calling `Intent.set{Component,Class,ClassName}`."

        for (location in locations) {
          context.report(
            Incident(
              ISSUE,
              location,
              message,
              fix()
                .alternatives(
                  buildClassNameQuickFix(location, firstComponent),
                  buildPackageNameQuickFix(location),
                ),
            )
          )
        }
      } else if (actionsRegistered.contains(action)) {
        // No manifest components, but there is a registered receiver.
        val message =
          "The intent action `$action` matches the intent filter of a non-exported " +
            "receiver, registered via a call to `Context.registerReceiver`, or similar. " +
            "If you are trying to invoke this specific receiver via the action " +
            "then you should use `Intent.setPackage(<APPLICATION_ID>)`."

        for (location in locations) {
          context.report(Incident(ISSUE, location, message, buildPackageNameQuickFix(location)))
        }
      }
    }
  }

  private fun buildPackageNameQuickFix(location: Location): LintFix {
    val applicationIdExpression = getApplicationIdExpression(location)
    return LintFix.create()
      .name("Set package name")
      .replace()
      .reformat(true)
      .range(location)
      .end()
      .with(".setPackage($applicationIdExpression)")
      .select(Regex.escape(applicationIdExpression))
      .robot(false) // the application id expression is not necessarily correct
      .build()
  }

  private fun buildClassNameQuickFix(location: Location, componentName: String): LintFix {
    val applicationIdExpression = getApplicationIdExpression(location)
    return LintFix.create()
      .name("Set class name")
      .replace()
      .reformat(true)
      .range(location)
      .end()
      .with(".setClassName($applicationIdExpression, \"$componentName\")")
      .select(Regex.escape(applicationIdExpression))
      .robot(false) // the application id expression is not necessarily correct
      .build()
  }

  companion object {

    private const val ACTIONS_SENT_KEY = "actionsSent"
    private const val ACTIONS_REGISTERED_NON_EXPORTED_KEY = "actionsRegisteredNonExported"

    private const val CLASS_CONTEXT_COMPAT = "androidx.core.content.ContextCompat"

    // Matches Context.RECEIVER_NOT_EXPORTED
    private const val RECEIVER_NOT_EXPORTED = 4

    /** Suffix to add to an action name that is used to start an activity */
    private const val ACTIVITY_ACTION_SUFFIX = " (used to start an activity)"

    /** Suffix to add to an action name that is used to send a broadcast */
    private const val BROADCAST_ACTION_SUFFIX = " (used to send a broadcast)"

    private val IMPLEMENTATION =
      Implementation(UnsafeImplicitIntentDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /**
     * Returns the most common way to get the application id by calling getPackageName(), but of
     * course this method might not be available.
     */
    fun getApplicationIdExpression(location: Location): String =
      if (location.file.extension.lowercase() == EXT_JAVA) {
        "/* TODO: provide the application ID. For example: */ getPackageName()"
      } else {
        "/* TODO: provide the application ID. For example: */ packageName"
      }

    /** Issue describing the problem and pointing to the detector implementation. */
    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "UnsafeImplicitIntentLaunch",
        briefDescription = "Implicit intent matches an internal non-exported component",
        explanation =
          """
                    This intent matches a non-exported component within the same app. \
                    In many cases, the app developer could instead use an explicit Intent \
                    to send messages to their internal components, ensuring that the messages \
                    are safely delivered without exposure to malicious apps on the device. \
                    Using such implicit intents will result in a crash in an upcoming version of Android.
                    """,
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.ERROR,
        androidSpecific = true,
        enabledByDefault = true,
        implementation = IMPLEMENTATION,
      )
  }
}
