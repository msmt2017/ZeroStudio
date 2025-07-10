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

import com.android.sdklib.IAndroidTarget
import com.android.tools.lint.checks.ApiDatabase.CacheCreator
import com.android.tools.lint.client.api.LintClient
import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.net.URL

class PrivateApiLookup
private constructor(client: LintClient, binaryFile: File, cacheCreator: CacheCreator) :
  ApiDatabase() {

  init {
    readData(client, binaryFile, cacheCreator, PRIVATE_API_BINARY_FORMAT_VERSION)
  }

  fun getMethodRestriction(owner: String, name: String, desc: String): Restriction {
    if (mData != null) {
      val classNumber = findClass(owner)
      if (classNumber >= 0) {
        return decode(findMember(classNumber, name, desc))
      }
    }
    return Restriction.UNKNOWN
  }

  fun getFieldRestriction(owner: String, name: String): Restriction {
    if (mData != null) {
      val classNumber = findClass(owner)
      if (classNumber >= 0) {
        return decode(findMember(classNumber, name, null))
      }
    }
    return Restriction.UNKNOWN
  }

  private fun findMember(classNumber: Int, name: String, desc: String?): Int {
    var curr = seekClassData(classNumber)

    // 3 bytes for first offset
    var low = get3ByteInt(mData, curr)
    curr += 3

    val length = get2ByteInt(mData, curr)
    if (length == 0) {
      return -1
    }
    var high = low + length

    while (low < high) {
      val middle = (low + high).ushr(1)
      var offset = mIndices[middle]

      if (DEBUG_SEARCH) {
        println("Comparing string $name$desc with entry at $offset: " + dumpEntry(offset))
      }

      var compare: Int
      if (desc != null) {
        // Method
        val nameLength = name.length
        compare = compare(mData, offset, '('.code.toByte(), name, 0, nameLength)
        if (compare == 0) {
          offset += nameLength
          val argsEnd = desc.indexOf(')')
          // Only compare up to the ) -- after that we have a return value in the
          // input description, which isn't there in the database.
          compare = compare(mData, offset, ')'.code.toByte(), desc, 0, argsEnd)
          if (compare == 0) {
            if (DEBUG_SEARCH) {
              println("Found " + dumpEntry(offset))
            }

            offset += argsEnd + 1

            if (mData[offset++].toInt() == 0) {
              // Yes, terminated argument list: get the API level
              return mData[offset].toInt()
            }
          }
        }
      } else {
        // Field
        val nameLength = name.length
        compare = compare(mData, offset, 0.toByte(), name, 0, nameLength)
        if (compare == 0) {
          offset += nameLength
          if (mData[offset++].toInt() == 0) {
            // Yes, terminated argument list: get the API level
            return mData[offset].toInt()
          }
        }
      }

      if (compare < 0) {
        low = middle + 1
      } else if (compare > 0) {
        high = middle
      } else {
        assert(false) // compare == 0 already handled above
        return -1
      }
    }
    return -1
  }

  private fun seekClassData(classNumber: Int): Int {
    val offset = mIndices[classNumber]
    return offset + (mData[offset].toInt() and 0xFF)
  }

  companion object {
    @VisibleForTesting internal const val DEBUG_FORCE_REGENERATE_BINARY = false

    private const val API_FILE_PATH = "private-apis.txt" // relative to Lint's resources dir
    private const val PRIVATE_API_BINARY_FORMAT_VERSION = 0

    private fun getCacheFileName(fileName: String, buildNumber: String?): String =
      buildString(100) {
        if (fileName.endsWith(".txt")) {
          append(fileName.substring(0, fileName.length - 4))
        } else {
          append(fileName)
        }

        // Incorporate version number in the filename to avoid upgrade filename
        // conflicts on Windows (such as issue #26663)
        append('-').append(getBinaryFormatVersion(PRIVATE_API_BINARY_FORMAT_VERSION))

        if (buildNumber != null) {
          append('-').append(buildNumber.replace(' ', '_'))
        }

        append(".bin")
      }

    private fun cacheCreator(input: URL) = CacheCreator { client, binaryData ->
      val begin = if (WRITE_STATS) System.currentTimeMillis() else 0

      val info: Api<PrivateApiClass>
      try {
        info = Api.parseHiddenApi(input)
      } catch (e: RuntimeException) {
        client.log(e, "Can't read private API file")
        return@CacheCreator false
      }

      if (WRITE_STATS) {
        val end = System.currentTimeMillis()
        println("Reading private API data took " + (end - begin) + " ms")
      }

      try {
        writeDatabase(client, binaryData, info, PRIVATE_API_BINARY_FORMAT_VERSION, null)
        return@CacheCreator true
      } catch (t: Throwable) {
        client.log(t, "Can't write private API cache file")
      }

      false
    }

    /**
     * Returns an instance of the private API database
     *
     * @param client the client to associate with this database - used only for logging
     * @param target the associated Android target, if known
     * @return a (possibly shared) instance of the API database, or null if its data can't be found
     */
    private fun get(client: LintClient, target: IAndroidTarget?): PrivateApiLookup? {
      val stream = PrivateApiLookup::class.java.classLoader.getResource(API_FILE_PATH)
      if (stream == null) {
        client.log(null, "The API database file $API_FILE_PATH could not be found")
        return null
      }

      // The first line contains the build number for the API data, to be used in the name of
      // the binary file.
      val version = stream.openStream().use { it.bufferedReader(Charsets.UTF_8).readLine() }
      // TODO: Check that we don't end up locking Lint's jar file on Windows

      val cacheDir = client.getCacheDir(null, true)
      if (cacheDir == null) {
        client.log(null, "Can't create cache dir")
        return null
      }

      val binaryData = File(cacheDir, getCacheFileName(API_FILE_PATH, version))
      val cache = cacheCreator(stream)

      if (DEBUG_FORCE_REGENERATE_BINARY) {
        System.err.println(
          "\nTemporarily regenerating binary data unconditionally \n" +
            "from $stream\nto $binaryData"
        )
        if (!cache.create(client, binaryData)) {
          return null
        }
      } else if ((!binaryData.exists() || binaryData.length() == 0L)) {
        if (!cache.create(client, binaryData)) {
          return null
        }
      }

      if (!binaryData.exists()) {
        client.log(null, "The API database file %1\$s does not exist", binaryData)
        return null
      }

      return PrivateApiLookup(client, binaryData, cache)
    }

    fun get(client: LintClient): PrivateApiLookup? {
      synchronized(PrivateApiLookup::class.java) {
        return get(client, null)
      }
    }
  }
}
