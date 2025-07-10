/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.assertionsEnabled
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Splitter
import com.google.common.io.ByteStreams
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays
import java.util.Random
import java.util.WeakHashMap
import kotlin.text.Charsets

/** Database of common typos / misspellings. */
class TypoLookup
private constructor(
  private var data: ByteArray,
  private var indices: IntArray,
  private var wordCount: Int = 0,
) {
  /**
   * Look up whether this word is a typo, and if so, return the typo itself and one or more likely
   * meanings
   *
   * @param text the string containing the word
   * @param begin the index of the first character in the word
   * @param end the index of the first character after the word. Note that the search may extend
   *   **beyond** this index, if for example the word matches a multi-word typo in the dictionary
   * @return a list of the typo itself followed by the replacement strings if the word represents a
   *   typo, and null otherwise
   */
  fun getTypos(text: CharSequence, begin: Int, end: Int): List<String>? {
    assert(end <= text.length)

    if (assertionsEnabled()) {
      for (i in begin until end) {
        val c = text[i]
        if (c.toInt() >= 128) {
          assert(false) { "Call the UTF-8 version of this method instead" }
          return null
        }
      }
    }

    var low = 0
    var high = wordCount - 1
    while (low <= high) {
      val middle = (low + high).ushr(1)
      var offset = indices[middle]

      // Compare the word at the given index.
      val compare = compare(data, offset, 0.toByte(), text, begin, end)

      if (compare == 0) {
        offset = indices[middle]

        // Don't allow matching uncapitalized words, such as "enlish", when
        // the dictionary word is capitalized, "Enlish".
        if (data[offset] != text[begin].code.toByte() && Character.isLowerCase(text[begin])) {
          return null
        }

        // Make sure there is a case match; we only want to allow
        // matching capitalized words to capitalized typos or uncapitalized typos
        //  (e.g. "Teh" and "teh" to "the"), but not uncapitalized words to capitalized
        // typos (e.g. "enlish" to "Enlish").
        var glob: String? = null
        var i = begin
        while (true) {
          val b = data[offset++]
          if (b.toInt() == 0) {
            offset--
            break
          } else if (b == '*'.code.toByte()) {
            var globEnd = i
            while (globEnd < text.length && Character.isLetter(text[globEnd])) {
              globEnd++
            }
            glob = text.subSequence(i, globEnd).toString()
            break
          }
          val c = text[i]
          val cb = c.code.toByte()
          if (b != cb && i > begin) {
            return null
          }
          i++
        }

        return computeSuggestions(indices[middle], offset, glob)
      }

      if (compare < 0) {
        low = middle + 1
      } else {
        high = middle - 1
      }
    }

    return null
  }

  /**
   * Look up whether this word is a typo, and if so, return the typo itself and one or more likely
   * meanings
   *
   * @param utf8Text the string containing the word, encoded as UTF-8
   * @param begin the index of the first character in the word
   * @param end the index of the first character after the word. Note that the search may extend
   *   **beyond** this index, if for example the word matches a multi-word typo in the dictionary
   * @return a list of the typo itself followed by the replacement strings if the word represents a
   *   typo, and null otherwise
   */
  fun getTypos(utf8Text: ByteArray, begin: Int, end: Int): List<String>? {
    assert(end <= utf8Text.size)

    var low = 0
    var high = wordCount - 1
    while (low <= high) {
      val middle = (low + high).ushr(1)
      var offset = indices[middle]

      // Compare the word at the given index.
      val compare = compare(data, offset, 0.toByte(), utf8Text, begin, end)

      if (compare == 0) {
        offset = indices[middle]

        // Don't allow matching uncapitalized words, such as "enlish", when
        // the dictionary word is capitalized, "Enlish".
        if (data[offset] != utf8Text[begin] && isUpperCase(data[offset])) {
          return null
        }

        // Make sure there is a case match; we only want to allow
        // matching capitalized words to capitalized typos or uncapitalized typos
        //  (e.g. "Teh" and "teh" to "the"), but not uncapitalized words to capitalized
        // typos (e.g. "enlish" to "Enlish").
        var glob: String? = null
        var i = begin
        while (true) {
          val b = data[offset++]
          if (b.toInt() == 0) {
            offset--
            break
          } else if (b == '*'.code.toByte()) {
            var globEnd = i
            while (globEnd < utf8Text.size && isLetter(utf8Text[globEnd])) {
              globEnd++
            }
            glob = String(utf8Text, i, globEnd - i, Charsets.UTF_8)
            break
          }
          val cb = utf8Text[i]
          if (b != cb && i > begin) {
            return null
          }
          i++
        }

        return computeSuggestions(indices[middle], offset, glob)
      }

      if (compare < 0) {
        low = middle + 1
      } else {
        high = middle - 1
      }
    }

    return null
  }

  private fun computeSuggestions(begin: Int, initialOffset: Int, glob: String?): List<String> {
    var offset = initialOffset
    var typo = String(data, begin, offset - begin, Charsets.UTF_8)

    if (glob != null) {
      typo = typo.replace("\\*".toRegex(), glob)
    }

    assert(data[offset].toInt() == 0)
    offset++
    var replacementEnd = offset
    while (data[replacementEnd].toInt() != 0) {
      replacementEnd++
    }
    val replacements = String(data, offset, replacementEnd - offset, Charsets.UTF_8)
    val words = ArrayList<String>()
    words.add(typo)

    // The first entry should be the typo itself. We need to pass this back since due
    // to multi-match words and globbing it could extend beyond the initial word range

    for (s in Splitter.on(',').omitEmptyStrings().trimResults().split(replacements)) {
      if (glob != null) {
        // Need to append the glob string to each result
        words.add(s.replace("\\*".toRegex(), glob))
      } else {
        words.add(s)
      }
    }

    return words
  }

  companion object {
    private val NONE = TypoLookup(ByteArray(0), IntArray(0), 0)

    /** String separating misspellings and suggested replacements in the text file. */
    private const val WORD_SEPARATOR = "->"

    private const val FILE_HEADER = "Typo database used by Android lint\u0000"
    private const val BINARY_FORMAT_VERSION = 2
    private const val DEBUG_FORCE_REGENERATE_BINARY = false

    /** Default size to reserve for each API entry when creating byte buffer to build up data. */
    private const val BYTES_PER_ENTRY = 28

    private val instanceMap = WeakHashMap<String, TypoLookup>()

    /**
     * Returns an instance of the Typo database for the given locale
     *
     * @param client the client to associate with this database - used only for logging. The
     *   database object may be shared among repeated invocations, and in that case client used will
     *   be the one originally passed in. In other words, this parameter may be ignored if the
     *   client created is not new.
     * @param locale the locale to look up a typo database for (should be a language code (ISO
     *   639-1, two lowercase character names)
     * @param region the region to look up a typo database for (should be a two letter ISO 3166-1
     *   alpha-2 country code in upper case) language code
     * @return a (possibly shared) instance of the typo database, or null if its data can't be found
     */
    @JvmStatic
    operator fun get(client: LintClient, locale: String, region: String?): TypoLookup? {
      synchronized(TypoLookup::class.java) {
        var key = locale

        if (region != null && region.length == 2) { // skip BCP-47 regions
          // Allow for region-specific dictionaries. See for example
          // http://en.wikipedia.org/wiki/American_and_British_English_spelling_differences
          assert(Character.isUpperCase(region[0]) && Character.isUpperCase(region[1])) { region }
          // Look for typos-en-rUS.txt etc
          key = locale + 'r'.toString() + region
        }

        var db: TypoLookup? = instanceMap[key]
        if (db == null) {
          val name = "typos-$key.txt"
          val path = "/typos/$name"
          val stream: InputStream? = TypoLookup::class.java.getResourceAsStream(path)
          if (stream == null) {
            if (region != null) {
              // Fall back to the generic locale (non-region-specific) database
              return get(client, locale, null)
            }
            db = NONE
          } else {
            db = get(client, stream, name)
            assert(db != null) { name }
          }
          instanceMap[key] = db
        }

        return if (db === NONE) {
          null
        } else {
          db
        }
      }
    }

    /**
     * Returns an instance of the typo database
     *
     * @param client the client to associate with this database - used only for logging
     * @param xmlStream the XML file containing configuration data to use for this database
     * @param name name to use for cache file
     * @return a (possibly shared) instance of the typo database, or null if its data can't be found
     */
    private operator fun get(
      client: LintClient,
      xmlStream: InputStream,
      name: String,
    ): TypoLookup? {
      val cacheDir =
        client.getCacheDir(null, true) ?: return null // should not happen since create=true above

      val binaryData =
        File(
          cacheDir,
          name +
            // Incorporate version number in the filename to avoid upgrade filename
            // conflicts on Windows (such as issue #26663)
            '-'.toString() +
            BINARY_FORMAT_VERSION +
            ".bin",
        )

      @Suppress("ConstantConditionIf")
      if (DEBUG_FORCE_REGENERATE_BINARY) {
        System.err.println(
          "\nTemporarily regenerating binary data unconditionally \nfrom $xmlStream\nto $binaryData"
        )
        if (!createCache(client, xmlStream, binaryData)) {
          return null
        }
      } else if (!binaryData.exists()) {
        if (!createCache(client, xmlStream, binaryData)) {
          return null
        }
      }

      if (!binaryData.exists()) {
        client.log(null, "The typo database file %1\$s does not exist", binaryData)
        return null
      }

      return readData(client, xmlStream, binaryData)
    }

    private fun readData(
      client: LintClient,
      xmlStream: InputStream,
      binaryFile: File?,
    ): TypoLookup? {
      binaryFile ?: return null

      if (!binaryFile.exists()) {
        client.log(null, "%1\$s does not exist", binaryFile)
        return null
      }

      try {
        val b = Files.toByteArray(binaryFile)
        val buffer = ByteBuffer.wrap(b)

        // First skip the header
        val expectedHeader = FILE_HEADER.toByteArray(Charsets.US_ASCII)
        buffer.rewind()
        for (anExpectedHeader in expectedHeader) {
          if (anExpectedHeader != buffer.get()) {
            client.log(
              null,
              "Incorrect file header: not an typo database cache file, or a corrupt cache file",
            )
            return null
          }
        }

        // Read in the format number
        if (buffer.get().toInt() != BINARY_FORMAT_VERSION) {
          // Force regeneration of new binary data with up to date format
          if (createCache(client, xmlStream, binaryFile)) {
            return readData(client, xmlStream, binaryFile) // Recurse
          }

          return null
        }

        val wordCount = buffer.int

        // Read in the word table indices;
        val offsets = IntArray(wordCount)

        // Another idea: I can just store the DELTAS in the file (and add them up
        // when reading back in) such that it takes just ONE byte instead of four!
        for (i in 0 until wordCount) {
          offsets[i] = buffer.int
        }

        // TODO: We only need to keep the data portion here since we've initialized
        // the offset array separately.
        return TypoLookup(b, offsets, wordCount)
      } catch (e: IOException) {
        client.log(e, null)
        return null
      }
    }

    private fun createCache(client: LintClient, xmlStream: InputStream, binaryData: File): Boolean {
      // Read in data
      val lines: Array<String>
      try {
        lines =
          String(ByteStreams.toByteArray(xmlStream), Charsets.UTF_8)
            .split("\n".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
      } catch (e: IOException) {
        client.log(e, "Can't read typo database file")
        return false
      }

      try {
        writeDatabase(binaryData, lines)
        return true
      } catch (ioe: IOException) {
        client.log(ioe, "Can't write typo cache file")
      }

      return false
    }

    /** See the [.readData] for documentation on the data format. */
    @Throws(IOException::class)
    private fun writeDatabase(file: File, lines: Array<String>) {
      /*
       * 1. A file header, which is the exact contents of {@link FILE_HEADER} encoded
       *     as ASCII characters. The purpose of the header is to identify what the file
       *     is for, for anyone attempting to open the file.
       * 2. A file version number. If the binary file does not match the reader's expected
       *     version, it can ignore it (and regenerate the cache from XML).
       */

      // Drop comments etc
      val words = ArrayList<String>(lines.size)
      for (line in lines) {
        if (!line.isEmpty() && Character.isLetter(line[0])) {
          var end = line.indexOf(WORD_SEPARATOR)
          if (end == -1) {
            end = line.trim().length
          }
          val typo = line.substring(0, end).trim()
          val replacements = line.substring(end + WORD_SEPARATOR.length).trim()
          if (replacements.isEmpty()) {
            // We don't support empty replacements
            continue
          }
          val combined = typo + 0.toChar() + replacements

          words.add(combined)
        }
      }

      val wordArrays = arrayOfNulls<ByteArray>(words.size)
      run {
        var i = 0
        val n = words.size
        while (i < n) {
          val word = words[i]
          wordArrays[i] = word.toByteArray(Charsets.UTF_8)
          i++
        }
      }
      // Sort words, using our own comparator to ensure that it matches the
      // binary search in getTypos()
      Arrays.sort<ByteArray>(wordArrays) { o1, o2 -> compare(o1, 0, 0.toByte(), o2, 0, o2.size) }

      val headerBytes = FILE_HEADER.toByteArray(Charsets.US_ASCII)
      val entryCount = wordArrays.size
      val capacity = entryCount * BYTES_PER_ENTRY + headerBytes.size + 5
      val buffer = ByteBuffer.allocate(capacity)
      buffer.order(ByteOrder.BIG_ENDIAN)
      //  1. A file header, which is the exact contents of {@link FILE_HEADER} encoded
      //      as ASCII characters. The purpose of the header is to identify what the file
      //      is for, for anyone attempting to open the file.
      buffer.put(headerBytes)

      //  2. A file version number. If the binary file does not match the reader's expected
      //      version, it can ignore it (and regenerate the cache from XML).
      buffer.put(BINARY_FORMAT_VERSION.toByte())

      //  3. The number of words [1 int]
      buffer.putInt(entryCount)

      //  4. Word offset table (one integer per word, pointing to the byte offset in the
      //       file (relative to the beginning of the file) where each word begins.
      //       The words are always sorted alphabetically.
      val wordOffsetTable = buffer.position()

      // Reserve enough room for the offset table here: we will backfill it with pointers
      // as we're writing out the data structures below
      for (i in 0 until entryCount) {
        buffer.putInt(0)
      }

      var nextEntry = buffer.position()
      var nextOffset = wordOffsetTable

      // 7. Word entry table. Each word entry consists of the word, followed by the byte 0
      //      as a terminator, followed by a comma separated list of suggestions (which
      //      may be empty), or a final 0.
      for (word in wordArrays) {
        buffer.position(nextOffset)
        buffer.putInt(nextEntry)
        nextOffset = buffer.position()
        buffer.position(nextEntry)

        buffer.put(word) // already embeds 0 to separate typo from words
        buffer.put(0.toByte())

        nextEntry = buffer.position()
      }

      val size = buffer.position()
      assert(size <= buffer.limit())
      buffer.mark()

      // Now dump this out as a file
      // There's probably an API to do this more efficiently; TODO: Look into this.
      val b = ByteArray(size)
      buffer.rewind()
      buffer.get(b)
      // Write to a different file and swap it in last minute.
      // This helps in scenarios where multiple simultaneous Gradle
      // threads are attempting to access the file before it's ready.
      val tmp = File(file.path + "." + Random().nextInt())
      tmp.parentFile?.let { if (!it.exists()) it.mkdirs() }
      Files.asByteSink(tmp).write(b)
      if (!tmp.renameTo(file)) {
        tmp.delete()
      }
    }

    /** Comparison function: *only* used for ASCII strings. */
    @VisibleForTesting
    @JvmStatic
    fun compare(
      data: ByteArray,
      offset: Int,
      terminator: Byte,
      s: CharSequence,
      begin: Int,
      initialEnd: Int,
    ): Int {
      var end = initialEnd
      var i = offset
      var j = begin
      while (true) {
        var b = data[i]
        if (b == ' '.code.toByte()) {
          // We've matched up to the space in a split-word typo, such as
          // in German all zu⇒allzu; here we've matched just past "all".
          // Rather than terminating, attempt to continue in the buffer.
          if (j == end) {
            val max = s.length
            if (end < max && s[end] == ' ') {
              // Find next word
              while (end < max) {
                val c = s[end]
                if (!Character.isLetter(c)) {
                  if (c == ' ' && end == j) {
                    end++
                    continue
                  }
                  break
                }
                end++
              }
            }
          }
        }

        if (j == end) {
          break
        }

        if (b == '*'.code.toByte()) {
          // Glob match (only supported at the end)
          return 0
        }
        val c = s[j]
        var cb = c.code.toByte()
        var delta = b - cb
        if (delta != 0) {
          cb = Character.toLowerCase(c).code.toByte()
          if (b != cb) {
            // Ensure that it has the right sign
            b = Character.toLowerCase(b.toInt()).toByte()
            delta = b - cb
            if (delta != 0) {
              return delta
            }
          }
        }
        i++
        j++
      }

      return data[i] - terminator
    }

    /** Comparison function used for general UTF-8 encoded strings. */
    @VisibleForTesting
    @JvmStatic
    fun compare(
      data: ByteArray,
      offset: Int,
      terminator: Byte,
      s: ByteArray,
      begin: Int,
      initialEnd: Int,
    ): Int {
      var end = initialEnd
      var i = offset
      var j = begin
      while (true) {
        var b = data[i]
        if (b == ' '.code.toByte()) {
          // We've matched up to the space in a split-word typo, such as
          // in German all zu⇒allzu; here we've matched just past "all".
          // Rather than terminating, attempt to continue in the buffer.
          // We've matched up to the space in a split-word typo, such as
          // in German all zu⇒allzu; here we've matched just past "all".
          // Rather than terminating, attempt to continue in the buffer.
          if (j == end) {
            val max = s.size
            if (end < max && s[end] == ' '.code.toByte()) {
              // Find next word
              while (end < max) {
                val cb = s[end]
                if (!isLetter(cb)) {
                  if (cb == ' '.code.toByte() && end == j) {
                    end++
                    continue
                  }
                  break
                }
                end++
              }
            }
          }
        }

        if (j == end) {
          break
        }
        if (b == '*'.code.toByte()) {
          // Glob match (only supported at the end)
          return 0
        }
        var cb = s[j]
        var delta = b - cb
        if (delta != 0) {
          cb = toLowerCase(cb)
          b = toLowerCase(b)
          delta = b - cb
          if (delta != 0) {
            return delta
          }
        }

        if (b == terminator || cb == terminator) {
          return delta
        }
        i++
        j++
      }

      return data[i] - terminator
    }

    // "Character" handling for bytes. This assumes that the bytes correspond to Unicode
    // characters in the ISO 8859-1 range, which is are encoded the same way in UTF-8.
    // This obviously won't work to for example uppercase to lowercase conversions for
    // multi byte characters, which means we simply won't catch typos if the dictionaries
    // contain these. None of the currently included dictionaries do. However, it does
    // help us properly deal with punctuation and spacing characters.

    private fun isUpperCase(b: Byte): Boolean {
      return Character.isUpperCase(b.toInt().toChar())
    }

    private fun toLowerCase(b: Byte): Byte {
      return Character.toLowerCase(b.toInt().toChar()).code.toByte()
    }

    @JvmStatic
    fun isLetter(b: Byte): Boolean {
      // Assume that multi byte characters represent letters in other languages.
      // Obviously, it could be unusual punctuation etc but letters are more likely
      // in this context.
      return Character.isLetter(b.toInt().toChar()) || b.toInt() and 0x80 != 0
    }
  }
}
