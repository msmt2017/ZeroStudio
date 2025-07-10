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

import java.io.InputStream

class PrivateApiParser {

  val classes = HashMap<String, PrivateApiClass>(24000)
  val containers = HashMap<String, ApiClassOwner<PrivateApiClass>>(7000)

  fun parse(stream: InputStream) {
    stream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
      val items = line.split(",", "->")
      if (items.size < 3) return@forEachLine

      val className = items[0].fromSignature()

      // Skip adding $$Lambda entries and allowed items.
      if (className.contains("${"$$"}Lambda")) return@forEachLine
      val restriction = parse(items[2])
      if (restriction == Restriction.ALLOW || restriction == Restriction.UNKNOWN) return@forEachLine

      val clazz = classes.getOrPut(className) { addClass(className) }
      val member = items[1]
      when {
        member.contains(":") -> { // field
          val parts = member.split(":")
          clazz.addField(parts[0], restriction)
        }
        member.contains("(") -> { // method
          clazz.addMethod(member.substring(0, member.indexOf(')') + 1), restriction)
        }
        else -> System.out.println("Unrecognized entry: $line")
      }
    }
  }

  private fun addClass(name: String): PrivateApiClass {
    // There should not be any duplicates.
    var cls: PrivateApiClass? = classes[name]
    assert(cls == null)
    cls = PrivateApiClass(name)

    val containerName = cls.containerName
    val len = containerName.length
    val isClass = len < name.length && name[len] == '$'
    var container: ApiClassOwner<PrivateApiClass>? = containers[containerName]
    if (container == null) {
      container = ApiClassOwner(containerName, isClass)
      containers[containerName] = container
    } else if (container.isClass != isClass) {
      throw RuntimeException("\"$containerName\" is both a package and a class")
    }
    container.addClass(cls)

    return cls
  }
}

private fun String.fromSignature(): String =
  if (startsWith("L") && endsWith(";")) substring(1, length - 1) else this

private fun parse(name: String): Restriction =
  when (name) {
    "SDK" -> Restriction.ALLOW
    "BLOCKED" -> Restriction.DENY
    "UNSUPPORTED" -> Restriction.MAYBE
    "MAX_TARGET_O" -> Restriction.MAYBE_MAX_O
    "MAX_TARGET_P" -> Restriction.MAYBE_MAX_P
    "MAX_TARGET_Q" -> Restriction.MAYBE_MAX_Q
    "MAX_TARGET_R" -> Restriction.MAYBE_MAX_R
    else -> Restriction.UNKNOWN
  }
