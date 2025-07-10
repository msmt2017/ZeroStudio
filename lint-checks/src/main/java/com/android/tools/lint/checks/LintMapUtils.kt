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

import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location

fun LintMap.getOrPutLintMap(key: String): LintMap {
  val result = this.getMap(key)
  if (result != null) return result
  val newMap = LintMap()
  this.put(key, newMap)
  return newMap
}

/**
 * Returns a Sequence of [Location], assuming the [LintMap] contains key-value pairs, where each key
 * is some arbitrary String (often "0", "1", "2", etc.) and each value is a [Location].
 */
fun LintMap.asLocationSequence() = sequence {
  for (key in this@asLocationSequence) {
    yield(this@asLocationSequence.getLocation(key)!!)
  }
}

/**
 * Appends a location to the [LintMap], assuming the [LintMap] contains key-value pairs, where each
 * key is "0", "1", "2", etc. and each value is a [Location].
 */
fun LintMap.appendLocation(location: Location): LintMap {
  this.put("${this.size}", location)
  return this
}
