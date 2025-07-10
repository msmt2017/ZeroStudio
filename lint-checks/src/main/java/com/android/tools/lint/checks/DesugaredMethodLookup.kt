/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.lint.detector.api.Desugaring
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.SourceSetType
import com.android.utils.SdkUtils.urlToFile
import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.util.Arrays
import org.jetbrains.annotations.TestOnly

/** This class provides lookup for R8 method descriptors of its backported methods. */
class DesugaredMethodLookup(val methodDescriptors: Array<String>, val names: Set<String>) {
  constructor(
    methodDescriptors: Array<String>
  ) : this(methodDescriptors, extractNames(methodDescriptors))

  private fun isDesugaredName(name: String): Boolean {
    return names.contains(name)
  }

  fun isDesugaredMethod(owner: String, name: String, desc: String): Boolean {
    // Callers shouldn't include return types on descriptors
    assert(desc.lastIndexOf(')') + 1 == desc.length)
    val signatureComparator: Comparator<String> = Comparator { o1, _ ->
      // We're not really comparing the two lambda parameters;
      // we're looking at the combination of o1 and the pair of
      // parameters passed in
      compare(owner, name, desc, o1)
    }
    val index = Arrays.binarySearch(methodDescriptors, "placeholder", signatureComparator)
    if (index < 0 && name == "<init>") {
      // Constructors seem to be missing from the desugaring files;
      // see b/347167978 so work around that with the assumption that
      // constructors are supported if any methods in the class are desugared.
      // (This is a temporary workaround until updated desugaring
      // files contain constructors *and* are widely used, since users
      // have to update their core library desugaring library versions.)
      if (isClassPartiallyDesugared(owner)) {
        return true
      }
    }
    return index >= 0
  }

  /**
   * The [isDesugaredClass] method returns true if a class is **fully** desugared. But there are
   * scenarios where we want to know if a class is at least partially desugared. This method returns
   * true in that case.
   */
  fun isClassPartiallyDesugared(owner: String): Boolean {
    val target = owner.replace('.', '/')
    return Arrays.binarySearch(methodDescriptors, "placeholder") { o1, _ ->
      // We're not really comparing the two lambda parameters;
      // we're looking at the combination of o1 and the pair of
      // parameters passed in
      if (o1.startsWith(target) && (o1 == target || o1[target.length] == '#')) {
        0
      } else {
        o1.compareTo(target)
      }
    } >= 0
  }

  fun isDesugaredField(owner: String, name: String): Boolean {
    val signatureComparator: Comparator<String> = Comparator { o1, _ ->
      compare(owner, name, "", o1)
    }
    return Arrays.binarySearch(methodDescriptors, "placeholder", signatureComparator) >= 0
  }

  fun isDesugaredClass(owner: String): Boolean {
    val signatureComparator: Comparator<String> = Comparator { o1, _ -> compare(owner, "", "", o1) }
    return Arrays.binarySearch(methodDescriptors, "placeholder", signatureComparator) >= 0
  }

  @VisibleForTesting
  fun compare(owner: String, name: String, desc: String, combined: String): Int {
    var ownerIndex = 0
    var hadSeparator = false
    var nameIndex = 0
    var descIndex = 0
    // We don't include the return type in description strings
    val lastParen = combined.lastIndexOf(')')
    val combinedLength = if (lastParen == -1) combined.length else lastParen + 1

    fun getNext(): Char {
      return when {
        ownerIndex < owner.length -> owner[ownerIndex++]
        !hadSeparator -> '#'.also { hadSeparator = true }
        nameIndex < name.length -> name[nameIndex++]
        descIndex < desc.length -> desc[descIndex++]
        else -> 0.toChar()
      }
    }

    var i = 0
    while (true) {
      val c = getNext()
      val d = combined[i++]
      if (c != d) {
        if (!c.isSymbolSeparator() || !d.isSymbolSeparator()) {
          return d - c
        }
      }
      if (i == combinedLength) {
        // Allow matching just on class name, or method name, or on full descriptor
        return if (lastParen == -1 && desc.isNotEmpty() && combined.indexOf('#') != -1) {
          // We're comparing a method call with a field reference that matches class and name,
          // but don't treat these as the same
          1
        } else if (
          (ownerIndex == owner.length) &&
            (nameIndex == 0 || nameIndex == name.length) &&
            (descIndex == 0 || descIndex == desc.length)
        ) {
          0
        } else {
          -1
        }
      }
    }
  }

  /** Consider a.b.C.D to be equivalent to the internal name a/b/C$D. */
  private fun Char.isSymbolSeparator() = this == '/' || this == '$' || this == '.'

  companion object {
    /** Given an array of method descriptors, returns a set of names of the methods. */
    private fun extractNames(methodDescriptors: Array<String>): Set<String> {
      return methodDescriptors
        .asSequence()
        .mapNotNull {
          // Extract names -- ignore lines without # (since those are just class names)
          // and strip off the method signature on methods (field names do not have descriptor
          // suffixes)
          if (it.contains('#')) {
            it.substringAfter('#').substringBefore('(')
          } else null
        }
        .toSet()
    }

    /**
     * Get library desugaring rules for the given project. Note that the project may not have
     * library desugaring configured; this computes the default set of desugared methods that
     * *would* be used for this project if enabled. The usecase for this is to look up existing
     * violations and suggest turning on core library desugaring if the violating API is part of the
     * desugaring API surface.
     */
    fun getBundledLibraryDesugaringRules(project: Project): DesugaredMethodLookup {
      val sourceSetType = SourceSetType.MAIN
      val lookup = project.getClientProperty<DesugaredMethodLookup>(sourceSetType)
      if (lookup != null) {
        // Already computed
        return lookup
      }
      val lines = getBundledLibraryDesugaringRules(project.minSdk)
      val newLookup = DesugaredMethodLookup(lines.toTypedArray())
      project.putClientProperty(sourceSetType, newLookup)
      return newLookup
    }

    fun getBundledLibraryDesugaringRules(minSdk: Int): List<String> {
      // To update this prebuilt, go to maven.google.com and search for
      //   desugar_jdk_libs_configuration_nio
      // and download the jar file, and you can extract the text files within.
      // Diff them to figure out the delta for any higher SDK versions and handle
      // that similarly to what is done below.
      return DesugaredMethodLookup::class
        .java
        .getResourceAsStream("/desugared_apis_30_1.txt")
        ?.let { inputStream ->
          // merge both built-in checks and the extra desugaring ones
          // we have to merge carefully since we sometimes have to drop
          // entries from one when the other list fully supports the whole
          // class. For example, the built-in rules backport a handful
          // of individual methods in java/util/Objects, but in the library
          // desugaring list, the whole class is included, so we should
          // drop the individual entries.
          val lines = defaultDesugaredMethods.toMutableList()
          lines.addAll(inputStream.bufferedReader(Charsets.UTF_8).readLines())

          if (minSdk >= 21) {
            lines.add("java/util/Collection#parallelStream()Ljava/util/stream/Stream;")
            lines.add("java/util/stream/BaseStream#parallel()Ljava/util/stream/BaseStream;")
            lines.add("java/util/stream/DoubleStream#parallel()Ljava/util/stream/BaseStream;")
            lines.add("java/util/stream/IntStream#parallel()Ljava/util/stream/BaseStream;")
            lines.add("java/util/stream/LongStream#parallel()Ljava/util/stream/BaseStream;")
          }

          lines.sort()

          assert(lines.isNotEmpty() && !lines[0].endsWith('\r'))
          removeMembersFromSupportedClasses(lines)
        } ?: emptyList()
    }

    /**
     * Checks whether the method for the given [owner], [name] and internal [desc] string is
     * desugared. If [project] is not null, provides the surrounding context for the lookup (which
     * should take into account build system configuration like which version of d8/r8 is used and
     * the corresponding desugaring list.) If [containingClass] is not null, it's the [PsiClass]
     * corresponding to [owner], which can be used for hierarchy search.
     */
    fun isDesugaredMethod(
      owner: String,
      name: String,
      desc: String,
      sourceSetType: SourceSetType,
      project: Project? = null,
      containingClass: PsiClass? = null,
    ): Boolean {
      val lookup = getLookup(project, sourceSetType)

      if (lookup.isDesugaredMethod(owner, name, desc)) {
        return true
      }

      if (containingClass != null && lookup.isDesugaredName(name)) {
        for (superClass in InheritanceUtil.getSuperClasses(containingClass)) {
          if (lookup.isDesugaredMethod(superClass.qualifiedName ?: continue, name, desc)) {
            return true
          }
        }
      }
      return false
    }

    /**
     * Checks whether the field for the given [owner] and [name] is desugared. If [project] is not
     * null, provides the surrounding context for the lookup (which should take into account build
     * system configuration like which version of d8/r8 is used and the corresponding desugaring
     * list.) If [containingClass] is not null, it's the [PsiClass] corresponding to [owner], which
     * can be used for hierarchy search.
     */
    fun isDesugaredField(
      owner: String,
      name: String,
      sourceSetType: SourceSetType,
      project: Project? = null,
      containingClass: PsiClass? = null,
    ): Boolean {
      val lookup = getLookup(project, sourceSetType)
      if (lookup.isDesugaredField(owner, name)) {
        return true
      }
      if (containingClass != null && lookup.isDesugaredName(name)) {
        for (superClass in InheritanceUtil.getSuperClasses(containingClass)) {
          if (lookup.isDesugaredField(superClass.qualifiedName ?: continue, name)) {
            return true
          }
        }
      }
      return false
    }

    /**
     * Checks whether the given [owner] is fully desugared. If [project] is not null, provides the
     * surrounding context for the lookup (which should take into account build system configuration
     * like which version of d8/r8 is used and the corresponding desugaring list.)
     */
    fun isDesugaredClass(
      owner: String,
      sourceSetType: SourceSetType,
      project: Project? = null,
    ): Boolean {
      return getLookup(project, sourceSetType).isDesugaredClass(owner)
    }

    /**
     * Looks up the [DesugaredMethodLookup] instance to use for analysis in the given project, or if
     * null (or if dealing with an older project definition not specifying desugaring files), falls
     * back to the default.
     */
    fun getLookup(project: Project?, sourceSetType: SourceSetType): DesugaredMethodLookup {
      if (project != null) {
        val model = project.buildVariant
        if (model != null) {
          val modelArtifact =
            when (sourceSetType) {
              SourceSetType.MAIN -> model.mainArtifact
              SourceSetType.INSTRUMENTATION_TESTS -> model.androidTestArtifact
              SourceSetType.TEST_FIXTURES -> model.testFixturesArtifact
              else -> null
            }
          val desugaredMethodsFiles =
            modelArtifact?.desugaredMethodsFiles?.ifEmpty { model.desugaredMethodsFiles }
              // fallback to non source specific desugared method files
              ?: model.desugaredMethodsFiles
          if (desugaredMethodsFiles.isNotEmpty()) { // otherwise talking to older version of AGP
            val lookup = project.getClientProperty<DesugaredMethodLookup>(sourceSetType)
            if (lookup != null) {
              return lookup
            }
            val newLookup = createDesugaredMethodLookup(desugaredMethodsFiles)
            project.putClientProperty(sourceSetType, newLookup)
            return newLookup
          }
        } else if (project.desugaring.contains(Desugaring.JAVA_8_LIBRARY)) {
          // In other build systems we don't get access to this data, but we've bundled a recent
          // copy of what R8 will use in this jar's resources
          val lookup = project.getClientProperty<DesugaredMethodLookup>(sourceSetType)
          if (lookup != null) {
            // Already computed
            return lookup
          }
          val lines = getBundledLibraryDesugaringRules(project.minSdk)
          if (lines != null) {
            val newLookup = DesugaredMethodLookup(lines.toTypedArray())
            project.putClientProperty(sourceSetType, newLookup)
            return newLookup
          }
        }
      }
      return lookup
    }

    /**
     * Sets the set of back-ported methods to be used for analysis to the descriptors from the given
     * [paths]. Returns null if everything is okay, and otherwise returns the first path that could
     * not be processed (e.g. file doesn't exist, insufficient permissions, etc.)
     */
    fun setDesugaredMethods(paths: List<String>): String? {
      val lines = ArrayList<String>(1024)
      for (path in paths) {
        try {
          if (path == "none") {
            // deliberately providing an empty list
          } else if (path.startsWith("jar:")) {
            val url = URL(path)
            val connection = url.openConnection() as JarURLConnection
            connection.jarFile.use { jarFile ->
              jarFile.getInputStream(connection.jarEntry).bufferedReader().forEachLine {
                if (it.isNotBlank()) {
                  lines.add(it)
                }
              }
            }
          } else {
            val file =
              if (path.startsWith("file:")) {
                urlToFile(URL(path))
              } else {
                File(path)
              }
            if (!file.isFile) {
              return path
            }
            file.forEachLine {
              if (it.isNotBlank()) {
                lines.add(it)
              }
            }
          }
        } catch (throwable: IOException) {
          return path
        }
      }
      lines.sort()
      // make sure the files aren't Windows line separator encoded or that the line sequence methods
      // handles it gracefully
      assert(lines.isEmpty() || !lines[0].endsWith('\r'))

      lookup = DesugaredMethodLookup(lines.distinct().toTypedArray())
      return null
    }

    /** Creates a new [DesugaredMethodLookup] for the given collection of files. */
    fun createDesugaredMethodLookup(files: Collection<File>): DesugaredMethodLookup {
      assert(files.isNotEmpty())
      var lines: MutableList<String> = ArrayList<String>(1024)
      for (file in files) {
        file.forEachLine {
          if (it.isNotBlank()) {
            lines.add(it)
          }
        }
      }
      lines.sort()

      if (files.size > 1 && lines.isNotEmpty()) {
        lines = removeMembersFromSupportedClasses(lines)
      }

      // make sure the files aren't Windows line separator encoded or that the line sequence methods
      // handles it gracefully
      assert(lines.isNotEmpty() && !lines[0].endsWith('\r'))

      return DesugaredMethodLookup(lines.toTypedArray())
    }

    /**
     * Remove any entries from the signature list for an individual method or field if the whole
     * class is already listed as fully supported. (These would confuse the binary search.)
     */
    private fun removeMembersFromSupportedClasses(lines: MutableList<String>): MutableList<String> {
      if (lines.isNotEmpty()) {
        val filtered = ArrayList<String>(lines.size)
        var prev = ""
        for (line in lines) {
          if (prev == line) {
            continue
          }
          if (prev > line) {
            error("Expected sorted file")
          }
          if (prev.indexOf('#') == -1) {
            // last line was a class; see if this line is a member of that class; if so, drop it
            if (line.startsWith(prev) && line.length > prev.length && line[prev.length] == '#') {
              // Skip this line (and don't update prev; there could be more members of this class
              continue
            }
          }
          filtered.add(line)
          prev = line
        }
        return filtered
      }
      return lines
    }

    /**
     * Returns the lookup to the default state. This is temporary; once we switch to this being
     * initialized from the lint model there will be no static state here.
     */
    @TestOnly
    fun reset() {
      lookup = DesugaredMethodLookup(defaultDesugaredMethods)
    }

    /**
     * Returns true if this looks like a reference that can be desugared in a consuming library.
     * This captures the rough packages related to library desugaring (but can also return true for
     * packages that are not included).
     */
    fun canBeDesugaredLater(owner: String?): Boolean {
      owner ?: return false
      assert(!owner.contains('/'))
      return owner.startsWith("java.") || owner.startsWith("android.") || owner.startsWith("sun.")
    }

    @get:VisibleForTesting
    val defaultDesugaredMethods =
      arrayOf(
        /* Created by:
          java -cp $ANDROID_HOME/cmdline-tools/latest/lib/r8.jar com.android.tools.r8.BackportedMethodList --min-api 15  \
            | awk '{ print "\"" $1 "\"," }' | sort

          To see what the current R8 versions look like, take a look at the current versions of approximately these files:
          ~/.gradle/caches/transforms-4/0063bc0c1ece4814f21912d525d7518f/transformed/desugar_jdk_libs_configuration-2.0.4-desugar-lint.txt
          ~/.gradle/caches/transforms-4/c8c6867a4d70102efbb28ab1570d3f86/transformed/desugar_jdk_libs_configuration_nio-2.0.4-desugar-lint.txt
          ~/.gradle/caches/transforms-4/f50c18fdc39cd3bc3c68b631a611e0e3/transformed/D8BackportedDesugaredMethods.txt
        */
        "android/content/ContentProviderClient#close()V",
        "android/content/res/TypedArray#close()V",
        "android/drm/DrmManagerClient#close()V",
        "android/media/MediaDrm#close()V",
        "android/media/MediaMetadataRetriever#close()V",
        "android/util/SparseArray#set(ILjava/lang/Object;)V",
        "java/lang/Boolean#compare(ZZ)I",
        "java/lang/Boolean#hashCode(Z)I",
        "java/lang/Boolean#logicalAnd(ZZ)Z",
        "java/lang/Boolean#logicalOr(ZZ)Z",
        "java/lang/Boolean#logicalXor(ZZ)Z",
        "java/lang/Byte#compare(BB)I",
        "java/lang/Byte#compareUnsigned(BB)I",
        "java/lang/Byte#hashCode(B)I",
        "java/lang/Byte#toUnsignedInt(B)I",
        "java/lang/Byte#toUnsignedLong(B)J",
        "java/lang/CharSequence#compare(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)I",
        "java/lang/Character#compare(CC)I",
        "java/lang/Character#hashCode(C)I",
        "java/lang/Character#toString(I)Ljava/lang/String;",
        "java/lang/Double#hashCode(D)I",
        "java/lang/Double#isFinite(D)Z",
        "java/lang/Double#max(DD)D",
        "java/lang/Double#min(DD)D",
        "java/lang/Double#sum(DD)D",
        "java/lang/Float#hashCode(F)I",
        "java/lang/Float#isFinite(F)Z",
        "java/lang/Float#max(FF)F",
        "java/lang/Float#min(FF)F",
        "java/lang/Float#sum(FF)F",
        "java/lang/Integer#compare(II)I",
        "java/lang/Integer#compareUnsigned(II)I",
        "java/lang/Integer#divideUnsigned(II)I",
        "java/lang/Integer#hashCode(I)I",
        "java/lang/Integer#max(II)I",
        "java/lang/Integer#min(II)I",
        "java/lang/Integer#parseInt(Ljava/lang/CharSequence;III)I",
        "java/lang/Integer#parseUnsignedInt(Ljava/lang/CharSequence;III)I",
        "java/lang/Integer#parseUnsignedInt(Ljava/lang/String;)I",
        "java/lang/Integer#parseUnsignedInt(Ljava/lang/String;I)I",
        "java/lang/Integer#remainderUnsigned(II)I",
        "java/lang/Integer#sum(II)I",
        "java/lang/Integer#toUnsignedLong(I)J",
        "java/lang/Integer#toUnsignedString(I)Ljava/lang/String;",
        "java/lang/Integer#toUnsignedString(II)Ljava/lang/String;",
        "java/lang/Long#compare(JJ)I",
        "java/lang/Long#compareUnsigned(JJ)I",
        "java/lang/Long#divideUnsigned(JJ)J",
        "java/lang/Long#hashCode(J)I",
        "java/lang/Long#max(JJ)J",
        "java/lang/Long#min(JJ)J",
        "java/lang/Long#parseLong(Ljava/lang/CharSequence;III)J",
        "java/lang/Long#parseUnsignedLong(Ljava/lang/CharSequence;III)J",
        "java/lang/Long#parseUnsignedLong(Ljava/lang/String;)J",
        "java/lang/Long#parseUnsignedLong(Ljava/lang/String;I)J",
        "java/lang/Long#remainderUnsigned(JJ)J",
        "java/lang/Long#sum(JJ)J",
        "java/lang/Long#toUnsignedString(J)Ljava/lang/String;",
        "java/lang/Long#toUnsignedString(JI)Ljava/lang/String;",
        "java/lang/Math#absExact(I)I",
        "java/lang/Math#absExact(J)J",
        "java/lang/Math#addExact(II)I",
        "java/lang/Math#addExact(JJ)J",
        "java/lang/Math#decrementExact(I)I",
        "java/lang/Math#decrementExact(J)J",
        "java/lang/Math#floorDiv(II)I",
        "java/lang/Math#floorDiv(JI)J",
        "java/lang/Math#floorDiv(JJ)J",
        "java/lang/Math#floorMod(II)I",
        "java/lang/Math#floorMod(JI)I",
        "java/lang/Math#floorMod(JJ)J",
        "java/lang/Math#incrementExact(I)I",
        "java/lang/Math#incrementExact(J)J",
        "java/lang/Math#multiplyExact(II)I",
        "java/lang/Math#multiplyExact(JI)J",
        "java/lang/Math#multiplyExact(JJ)J",
        "java/lang/Math#multiplyFull(II)J",
        "java/lang/Math#multiplyHigh(JJ)J",
        "java/lang/Math#negateExact(I)I",
        "java/lang/Math#negateExact(J)J",
        "java/lang/Math#nextDown(D)D",
        "java/lang/Math#nextDown(F)F",
        "java/lang/Math#subtractExact(II)I",
        "java/lang/Math#subtractExact(JJ)J",
        "java/lang/Math#toIntExact(J)I",
        "java/lang/Short#compare(SS)I",
        "java/lang/Short#compareUnsigned(SS)I",
        "java/lang/Short#hashCode(S)I",
        "java/lang/Short#toUnsignedInt(S)I",
        "java/lang/Short#toUnsignedLong(S)J",
        "java/lang/StrictMath#absExact(I)I",
        "java/lang/StrictMath#absExact(J)J",
        "java/lang/StrictMath#addExact(II)I",
        "java/lang/StrictMath#addExact(JJ)J",
        "java/lang/StrictMath#decrementExact(I)I",
        "java/lang/StrictMath#decrementExact(J)J",
        "java/lang/StrictMath#floorDiv(II)I",
        "java/lang/StrictMath#floorDiv(JI)J",
        "java/lang/StrictMath#floorDiv(JJ)J",
        "java/lang/StrictMath#floorMod(II)I",
        "java/lang/StrictMath#floorMod(JI)I",
        "java/lang/StrictMath#floorMod(JJ)J",
        "java/lang/StrictMath#incrementExact(I)I",
        "java/lang/StrictMath#incrementExact(J)J",
        "java/lang/StrictMath#multiplyExact(II)I",
        "java/lang/StrictMath#multiplyExact(JI)J",
        "java/lang/StrictMath#multiplyExact(JJ)J",
        "java/lang/StrictMath#multiplyFull(II)J",
        "java/lang/StrictMath#multiplyHigh(JJ)J",
        "java/lang/StrictMath#negateExact(I)I",
        "java/lang/StrictMath#negateExact(J)J",
        "java/lang/StrictMath#nextDown(D)D",
        "java/lang/StrictMath#nextDown(F)F",
        "java/lang/StrictMath#subtractExact(II)I",
        "java/lang/StrictMath#subtractExact(JJ)J",
        "java/lang/StrictMath#toIntExact(J)I",
        "java/lang/String#isBlank()Z",
        "java/lang/String#join(Ljava/lang/CharSequence;Ljava/lang/Iterable;)Ljava/lang/String;",
        "java/lang/String#join(Ljava/lang/CharSequence;[Ljava/lang/CharSequence;)Ljava/lang/String;",
        "java/lang/String#repeat(I)Ljava/lang/String;",
        "java/lang/String#strip()Ljava/lang/String;",
        "java/lang/String#stripLeading()Ljava/lang/String;",
        "java/lang/String#stripTrailing()Ljava/lang/String;",
        "java/lang/reflect/Method#getParameterCount()I",
        "java/math/BigDecimal#stripTrailingZeros()Ljava/math/BigDecimal;",
        "java/util/Collections#emptyEnumeration()Ljava/util/Enumeration;",
        "java/util/Collections#emptyIterator()Ljava/util/Iterator;",
        "java/util/Collections#emptyListIterator()Ljava/util/ListIterator;",
        "java/util/List#copyOf(Ljava/util/Collection;)Ljava/util/List;",
        "java/util/List#of()Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
        "java/util/List#of([Ljava/lang/Object;)Ljava/util/List;",
        "java/util/Map#copyOf(Ljava/util/Map;)Ljava/util/Map;",
        "java/util/Map#entry(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map\$Entry;",
        "java/util/Map#of()Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Map;",
        "java/util/Map#ofEntries([Ljava/util/Map\$Entry;)Ljava/util/Map;",
        "java/util/Objects#checkFromIndexSize(III)I",
        "java/util/Objects#checkFromIndexSize(JJJ)J",
        "java/util/Objects#checkFromToIndex(III)I",
        "java/util/Objects#checkFromToIndex(JJJ)J",
        "java/util/Objects#checkIndex(II)I",
        "java/util/Objects#checkIndex(JJ)J",
        "java/util/Objects#compare(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Comparator;)I",
        "java/util/Objects#deepEquals(Ljava/lang/Object;Ljava/lang/Object;)Z",
        "java/util/Objects#equals(Ljava/lang/Object;Ljava/lang/Object;)Z",
        "java/util/Objects#hash([Ljava/lang/Object;)I",
        "java/util/Objects#hashCode(Ljava/lang/Object;)I",
        "java/util/Objects#isNull(Ljava/lang/Object;)Z",
        "java/util/Objects#nonNull(Ljava/lang/Object;)Z",
        "java/util/Objects#requireNonNull(Ljava/lang/Object;)Ljava/lang/Object;",
        "java/util/Objects#requireNonNull(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;",
        "java/util/Objects#requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "java/util/Objects#toString(Ljava/lang/Object;)Ljava/lang/String;",
        "java/util/Objects#toString(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;",
        "java/util/Set#copyOf(Ljava/util/Collection;)Ljava/util/Set;",
        "java/util/Set#of()Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/Set#of([Ljava/lang/Object;)Ljava/util/Set;",
        "java/util/concurrent/atomic/AtomicReference#compareAndSet(Ljava/lang/Object;Ljava/lang/Object;)Z",
        "java/util/concurrent/atomic/AtomicReferenceArray#compareAndSet(ILjava/lang/Object;Ljava/lang/Object;)Z",
        "java/util/concurrent/atomic/AtomicReferenceFieldUpdater#compareAndSet(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z",
        "sun/misc/Unsafe#compareAndSwapObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
      )

    /**
     * Temporarily mutable such that we can set this from a command line flag instead of
     * initializing it via the lint model (while we're still working out how this is best passed --
     * as strings, files, shipped as resource files in r8 that the lint model points to, etc.)
     */
    var lookup: DesugaredMethodLookup = DesugaredMethodLookup(defaultDesugaredMethods, emptySet())
  }
}
