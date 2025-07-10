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

object PendingIntentUtils {
  val GET_METHOD_NAMES = listOf("getActivity", "getActivities", "getBroadcast", "getService")
  const val CLASS = "android.app.PendingIntent"
  const val GET_ARGUMENT_POSITION_FLAG = 3
  const val GET_ARGUMENT_POSITION_INTENT = 2
  const val FLAG_NO_CREATE = 1 shl 29
  const val FLAG_UPDATE_CURRENT = 1 shl 27
  const val FLAG_IMMUTABLE = 1 shl 26
  const val FLAG_MUTABLE = 1 shl 25
  const val FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT = 1 shl 24

  const val FLAG_IMMUTABLE_STR = "android.app.PendingIntent.FLAG_IMMUTABLE"
  const val FLAG_MUTABLE_STR = "android.app.PendingIntent.FLAG_MUTABLE"
  const val FLAG_NO_CREATE_STR = "android.app.PendingIntent.FLAG_NO_CREATE"
}
