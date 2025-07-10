/*
 * Copyright (C) 2019 The Android Open Source Project
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

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Represents a class and its hidden methods/fields, which are not part of the public SDK.
 *
 * Each member has an attached reflective access [Restriction], corresponding to the platform's
 * runtime behavior (e.g. a reflective call to a denied method is forbidden on all API versions, a
 * call to a maybe allowed method will be allowed with a warning, etc.)
 */
class PrivateApiClass(name: String) : ApiClassBase(name) {

  private val fields = HashMap<String, Restriction>()
  private val methods = HashMap<String, Restriction>()

  internal fun addField(name: String, restriction: Restriction) {
    fields[name] = restriction
  }

  internal fun addMethod(name: String, restriction: Restriction) {
    methods[name] = restriction
  }

  internal override fun computeExtraStorageNeeded(info: Api<out ApiClassBase>): Int {
    var estimatedSize = 0
    members = ArrayList<String>(fields.size + methods.size)
    members.addAll(fields.keys)
    members.addAll(methods.keys)
    members.sort()
    includeNames = true
    for (member in members) {
      estimatedSize += member.length
      estimatedSize += 8
    }
    return estimatedSize
  }

  internal override fun writeSuperInterfaces(info: Api<out ApiClassBase>, buffer: ByteBuffer) {
    // We don't store the class hierarchy.
  }

  internal override fun writeMemberData(
    info: Api<out ApiClassBase>,
    member: String,
    buffer: ByteBuffer,
  ) {
    val payload = (if (member.indexOf('(') >= 0) methods[member] else fields[member]) ?: return

    val signature = member.toByteArray(StandardCharsets.UTF_8)
    for (b in signature) {
      // Make sure all signatures are really just simple ASCII.
      assert(b.toInt() == (b.toInt() and 0x7f)) { member }
      buffer.put(b)
      // Skip types on methods
      if (b == ')'.code.toByte()) {
        break
      }
    }
    buffer.put(0.toByte())

    assert(payload.encoding == (payload.encoding and ApiDatabase.API_MASK)) // Must fit in 7 bits.
    buffer.put(payload.encoding.toByte())
  }
}

enum class Restriction(val encoding: Int) {
  UNKNOWN(0),
  ALLOW(1),
  DENY(2),
  MAYBE(3),
  MAYBE_MAX_O(4),
  MAYBE_MAX_P(5),
  MAYBE_MAX_Q(6),
  MAYBE_MAX_R(7),
}

fun decode(encoding: Int): Restriction =
  when (encoding) {
    1 -> Restriction.ALLOW
    2 -> Restriction.DENY
    3 -> Restriction.MAYBE
    4 -> Restriction.MAYBE_MAX_O
    5 -> Restriction.MAYBE_MAX_P
    6 -> Restriction.MAYBE_MAX_Q
    7 -> Restriction.MAYBE_MAX_R
    else -> Restriction.UNKNOWN
  }
