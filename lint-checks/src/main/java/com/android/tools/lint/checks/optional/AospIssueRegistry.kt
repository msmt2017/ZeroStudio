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
package com.android.tools.lint.checks.optional

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/**
 * Lint checks meant for analyzing the Android platform sources itself. These checks shouldn't be
 * part of the default Android lint distribution, but they're included here to make them easily
 * available from a number of different contexts (AOSP platform builds, google3, ASfP IDE) without
 * having to have separate jar artifacts.
 */
class AospIssueRegistry : IssueRegistry() {
  override val issues: List<Issue> = listOf(FlaggedApiDetector.ISSUE)

  override val vendor: Vendor = AOSP_VENDOR

  override val api: Int
    get() = CURRENT_API

  public override fun cacheable(): Boolean {
    // See BuiltinIssueRegistry.cacheable
    return LintClient.isStudio
  }
}
