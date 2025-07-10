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
package com.android.tools.lint.checks

import com.android.tools.lint.checks.TargetSdkCheckResult.Expired
import com.android.tools.lint.checks.TargetSdkCheckResult.Expiring
import com.android.tools.lint.checks.TargetSdkCheckResult.NoIssue
import com.android.tools.lint.checks.TargetSdkCheckResult.NotLatest
import com.android.tools.lint.checks.TargetSdkRequirements.MINIMUM_TARGET_SDK_VERSION
import com.android.tools.lint.checks.TargetSdkRequirements.MINIMUM_TARGET_SDK_VERSION_YEAR
import com.android.tools.lint.checks.TargetSdkRequirements.MINIMUM_WEAR_TARGET_SDK_VERSION
import com.android.tools.lint.checks.TargetSdkRequirements.PREVIOUS_MINIMUM_TARGET_SDK_VERSION
import com.android.tools.lint.checks.TargetSdkRequirements.PREVIOUS_WEAR_MINIMUM_TARGET_SDK_VERSION
import com.android.tools.lint.detector.api.Context
import java.util.Calendar

object TargetSdkRequirements {
  /**
   * The minimum API required of the year [MINIMUM_TARGET_SDK_VERSION_YEAR]. See
   * https://developer.android.com/google/play/requirements/target-sdk.
   */
  const val MINIMUM_TARGET_SDK_VERSION = 33

  /**
   * The minimum API required for wear app of the year [MINIMUM_TARGET_SDK_VERSION_YEAR]. See
   * https://developer.android.com/google/play/requirements/target-sdk.
   */
  const val MINIMUM_WEAR_TARGET_SDK_VERSION = 30

  /** The API requirement the previous year. */
  const val PREVIOUS_MINIMUM_TARGET_SDK_VERSION = 31

  /** The API requirement the previous year. */
  const val PREVIOUS_WEAR_MINIMUM_TARGET_SDK_VERSION = 30

  /**
   * The year that the API requirement of [MINIMUM_TARGET_SDK_VERSION] and
   * [MINIMUM_WEAR_TARGET_SDK_VERSION] is enforced.
   */
  const val MINIMUM_TARGET_SDK_VERSION_YEAR = 2023
}

sealed interface TargetSdkCheckResult {

  data class Expired(
    val requiredVersion: Int,
    val message: String =
      "Google Play requires that apps target API level $requiredVersion or higher.",
  ) : TargetSdkCheckResult

  data class Expiring(
    val requiredVersion: Int,
    val message: String =
      "Google Play will soon require that apps target API " +
        "level $requiredVersion or higher. This will be required for new apps and updates " +
        "starting on August 31, $MINIMUM_TARGET_SDK_VERSION_YEAR.",
  ) : TargetSdkCheckResult

  data class NotLatest(val highestVersion: Int) : TargetSdkCheckResult {
    val message: String
      get() =
        "Not targeting the latest versions of Android; compatibility " +
          "modes apply. Consider testing and updating this version. " +
          "Consult the `android.os.Build.VERSION_CODES` javadoc for details."
  }

  object NoIssue : TargetSdkCheckResult {
    const val message: String = ""
  }
}

/**
 * Starting on August 31, 2023, the apps are required to use API 33 or higher, except for Wear OS
 * apps, which must target 30 See https://developer.android.com/google/play/requirements/target-sdk
 */
fun checkTargetSdk(context: Context, nowCalendar: Calendar, version: Int): TargetSdkCheckResult {
  val isWearProject = WearDetector.isWearProject(context)
  val minimumTargetSdkVersion =
    if (isWearProject) MINIMUM_WEAR_TARGET_SDK_VERSION else MINIMUM_TARGET_SDK_VERSION
  val previousMinimumTargetSdkVersion =
    if (isWearProject) PREVIOUS_WEAR_MINIMUM_TARGET_SDK_VERSION
    else PREVIOUS_MINIMUM_TARGET_SDK_VERSION
  val sdkEnforceDate =
    Calendar.getInstance().apply { set(MINIMUM_TARGET_SDK_VERSION_YEAR, Calendar.AUGUST, 31) }

  return when {
    // If the version is at least the minimum then there's no enforcement issue
    version >= minimumTargetSdkVersion -> {
      val highest = context.client.highestKnownApiLevel
      if (version < highest && !isWearProject) NotLatest(highest) else NoIssue
    }
    // Doesn't meet this year requirement after deadline (August 31 for 2023)
    nowCalendar.after(sdkEnforceDate) -> Expired(minimumTargetSdkVersion)
    // If you're not meeting the previous year's requirement, also enforce with error severity
    version < previousMinimumTargetSdkVersion -> Expired(previousMinimumTargetSdkVersion)
    else -> Expiring(minimumTargetSdkVersion)
  }
}
