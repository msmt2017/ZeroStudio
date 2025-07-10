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

import com.android.AndroidXConstants.CLASS_V4_FRAGMENT
import com.android.SdkConstants.ANDROID_PKG_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_FRAGMENT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CLASS_ACTIVITY
import com.android.SdkConstants.CLASS_APPLICATION
import com.android.SdkConstants.CLASS_BROADCASTRECEIVER
import com.android.SdkConstants.CLASS_CONTENTPROVIDER
import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.SdkConstants.CLASS_SERVICE
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_HEADER
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_PROVIDER
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.SdkConstants.VIEW_TAG
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.ANIM
import com.android.resources.ResourceFolderType.ANIMATOR
import com.android.resources.ResourceFolderType.COLOR
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.FONT
import com.android.resources.ResourceFolderType.INTERPOLATOR
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.MENU
import com.android.resources.ResourceFolderType.MIPMAP
import com.android.resources.ResourceFolderType.NAVIGATION
import com.android.resources.ResourceFolderType.RAW
import com.android.resources.ResourceFolderType.TRANSITION
import com.android.resources.ResourceFolderType.VALUES
import com.android.resources.ResourceFolderType.XML
import com.android.tools.lint.checks.AppCompatResourceDetector.ATTR_ACTION_PROVIDER_CLASS
import com.android.tools.lint.checks.AppCompatResourceDetector.ATTR_ACTION_VIEW_CLASS
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient.Companion.isStudio
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getInternalName
import com.android.tools.lint.detector.api.hasImplicitDefaultConstructor
import com.android.tools.lint.detector.api.resolvePlaceHolders
import com.android.utils.SdkUtils.endsWith
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import java.util.Locale
import org.jetbrains.uast.UClass
import org.jetbrains.uast.getUastParentOfType
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Checks to ensure that classes referenced in the manifest actually exist and are included. */
class MissingClassDetector : LayoutDetector(), ClassScanner {
  /**
   * Prevent checking the same class more than once since it can be referenced repeatedly. The value
   * in the map is true if the class is okay and false if it is not.
   */
  private var checkedClasses: MutableMap<String, Boolean> = mutableMapOf()

  // ---- Implements XmlScanner ----

  override fun getApplicableElements(): Collection<String> {
    return ALL
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean =
    folderType == VALUES ||
      folderType == LAYOUT ||
      folderType == XML ||
      folderType == DRAWABLE ||
      folderType == MENU ||
      folderType == TRANSITION

  override fun visitElement(context: XmlContext, element: Element) {
    val tag = element.tagName

    @Suppress("NON_EXHAUSTIVE_WHEN")
    when (context.resourceFolderType) {
      null -> { // Manifest file
        if (
          TAG_APPLICATION == tag ||
            TAG_ACTIVITY == tag ||
            TAG_SERVICE == tag ||
            TAG_RECEIVER == tag ||
            TAG_PROVIDER == tag
        ) {
          val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
          val pkg =
            context.document.documentElement.getAttributeNode(ATTR_PACKAGE)?.value
              ?: context.project.getPackage()
          val className = resolvePlaceHolders(context.project, attr.value) ?: return
          if (className.isEmpty()) return
          checkClassReference(
            context,
            pkg,
            className,
            attr,
            element,
            requireInstantiatable = true,
            expectedParent =
              when (tag) {
                TAG_ACTIVITY -> CLASS_ACTIVITY
                TAG_SERVICE -> CLASS_SERVICE
                TAG_RECEIVER -> CLASS_BROADCASTRECEIVER
                TAG_PROVIDER -> CLASS_CONTENTPROVIDER
                TAG_APPLICATION -> CLASS_APPLICATION
                else -> null
              },
          )
        }
      }
      VALUES -> {
        // Check class name in analytics references
        // Only look for fully qualified tracker names in analytics files
        if (tag == TAG_STRING && endsWith(context.file.path, "analytics.xml")) {
          val attr = element.getAttributeNode(ATTR_NAME) ?: return
          checkClassReference(context, null, attr.value, attr, element)
        }
      }
      LAYOUT -> {
        when {
          tag.indexOf('.') > 0 -> {
            checkClassReference(
              context,
              null,
              tag,
              element,
              element,
              // Already doing hierarchy checks in Studio, don't duplicate effort
              expectedParent = if (isStudio) null else CLASS_VIEW,
            )
          }
          tag == VIEW_TAG -> {
            val attr = element.getAttributeNode(ATTR_CLASS) ?: return
            checkClassReference(
              context,
              null,
              attr.value,
              attr,
              element,
              expectedParent = if (isStudio) null else CLASS_VIEW,
            )
          }
          tag == VIEW_FRAGMENT -> {
            val attr =
              element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                ?: element.getAttributeNode(ATTR_CLASS)
                ?: return
            checkClassReference(
              context,
              null,
              attr.value,
              attr,
              element,
              requireInstantiatable = true,
              expectedParent = CLASS_FRAGMENT,
            )
          }
        }
      }
      DRAWABLE -> {
        when {
          tag.indexOf('.') > 0 -> {
            checkClassReference(context, null, tag, element, element)
          }
          tag == "drawable" -> {
            val attr = element.getAttributeNode(ATTR_CLASS) ?: return
            checkClassReference(
              context,
              null,
              attr.value,
              attr,
              element,
              expectedParent = "android.graphics.drawable.Drawable",
            )
          }
        }
      }
      TRANSITION -> {
        if (tag == "transition" || tag == "pathMotion") {
          val attr = element.getAttributeNode(ATTR_CLASS) ?: return
          val expectedParent =
            if (tag == "transition") "android.transition.Transition"
            else "android.transition.PathMotion"
          checkClassReference(
            context,
            null,
            attr.value,
            attr,
            element,
            expectedParent = expectedParent,
          )
        }
      }
      XML -> {
        if (tag == TAG_HEADER) {
          val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_FRAGMENT) ?: return
          checkClassReference(
            context,
            null,
            attr.value,
            attr,
            element,
            requireInstantiatable = true,
            expectedParent = CLASS_FRAGMENT,
          )
        }
      }
      MENU -> {
        if (tag == TAG_ITEM) {
          val view =
            element.getAttributeNodeNS(AUTO_URI, ATTR_ACTION_VIEW_CLASS)
              ?: element.getAttributeNodeNS(ANDROID_URI, ATTR_ACTION_VIEW_CLASS)
          if (view != null) {
            checkClassReference(
              context,
              null,
              view.value,
              view,
              element,
              expectedParent = CLASS_VIEW,
            )
          }
          val provider =
            element.getAttributeNodeNS(AUTO_URI, ATTR_ACTION_PROVIDER_CLASS)
              ?: element.getAttributeNodeNS(ANDROID_URI, ATTR_ACTION_PROVIDER_CLASS)
              ?: return
          // Consider checking for one of the action provider parent classes
          checkClassReference(context, null, provider.value, provider, element)
        }
      }
      ANIM,
      ANIMATOR,
      COLOR,
      FONT,
      INTERPOLATOR,
      MIPMAP,
      NAVIGATION,
      RAW -> {}
    }
  }

  private fun checkClassReference(
    context: XmlContext,
    pkg: String?,
    className: String,
    classNameNode: Node,
    element: Element,
    requireInstantiatable: Boolean = false,
    expectedParent: String? = null,
  ) {
    if (className.isEmpty()) {
      return
    }
    val fqcn: String
    val dotIndex = className.indexOf('.')
    if (dotIndex <= 0) {
      if (pkg == null) {
        return // not a manifest file; no implicit package
      }
      fqcn =
        if (dotIndex == 0) {
          pkg + className
        } else {
          // According to the <activity> manifest element documentation, this is not
          // valid (http://developer.android.com/guide/topics/manifest/activity-element.html)
          // but it appears in manifest files and appears to be supported by the runtime
          // so handle this in code as well:
          "$pkg.$className"
        }
    } else {
      // else: the class name is already a fully qualified class name
      fqcn = className
    }

    if (fqcn.startsWith(ANDROID_PKG_PREFIX) || fqcn.startsWith("com.android.internal.")) {
      return
    }

    var ok = checkedClasses[fqcn]
    if (ok == null) {
      val parser = context.client.getUastParser(context.project)
      val evaluator = parser.evaluator
      val cls = evaluator.findClass(fqcn.replace('$', '.'))
      if (cls != null) {
        ok = true
        if (requireInstantiatable) {
          checkInstantiatable(context, evaluator, cls, fqcn, classNameNode)
        }
        checkInnerClassReference(context, cls, className, classNameNode, element)
        if (expectedParent != null) {
          checkExpectedParent(context, evaluator, classNameNode, cls, expectedParent)
        }
      } else {
        ok = false
      }
      checkedClasses[fqcn] = ok
    }
    if (ok == false) {
      val location = getRefLocation(context, classNameNode)
      reportMissing(location, fqcn, context)
    }
  }

  private fun checkExpectedParent(
    context: XmlContext,
    evaluator: JavaEvaluator,
    nameNode: Node,
    cls: PsiClass,
    expectedParent: String,
  ) {
    if (!evaluator.inheritsFrom(cls, expectedParent, false)) {
      if (expectedParent == CLASS_FRAGMENT) {
        checkExpectedParent(context, evaluator, nameNode, cls, CLASS_V4_FRAGMENT.oldName())
        return
      } else if (expectedParent == CLASS_V4_FRAGMENT.oldName()) {
        checkExpectedParent(context, evaluator, nameNode, cls, CLASS_V4_FRAGMENT.newName())
        return
      }

      // Safety check: make sure we can actually resolve the super classes; if not, we
      // can have false positives. This can happen when you extend Kotlin classes in
      // a different module with a source dependency (e.g. in Gradle with checkDependencies
      // true) since those source files aren't fed to the top down analyzer. See b/158128960.
      // TODO: This code is not really reliable; we should probably come up with a util function
      //  that checks inheritance and returns "unsure" if there are resolution errors along the
      //  way.
      val superClass = cls.superClass ?: return
      val superTypes = cls.superTypes ?: return

      if (superTypes.firstOrNull()?.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) == true) {
        // We still might see this when the super class can't be resolved (at least for K1 with a
        // wrapped KtClass). The UAST implementation still seems to work (i.e. it does not eagerly
        // try to resolve the class and revert to Object if resolution fails), although the first
        // element of uastSuperTypes is not necessarily the super class (vs. an interface). We
        // return early if any of the uastSuperTypes are invalid.
        if (cls.getUastParentOfType<UClass>()?.uastSuperTypes?.any { !it.type.isValid } == true) {
          return
        }
      }

      if (superTypes.isNotEmpty()) {
        val name = superTypes[0].className
        if (name != null && name != superClass.name) {
          return
        }
      }
      var curr = superClass
      while (true) {
        val qualifiedName = curr.qualifiedName
        if (qualifiedName == expectedParent) {
          return
        } else if (qualifiedName == CommonClassNames.JAVA_LANG_OBJECT) {
          break
        }
        curr = curr.superClass ?: return
      }

      val message =
        if (expectedParent.contains("Fragment")) {
          "`${cls.name}` must be a fragment"
        } else {
          "`${cls.name}` must extend $expectedParent"
        }
      context.report(INSTANTIATABLE, getRefLocation(context, nameNode), message)
    }
  }

  private fun checkInnerClassReference(
    context: XmlContext,
    cls: PsiClass,
    className: String,
    nameNode: Node,
    element: Element,
  ) {
    val name = cls.name
    if (cls.containingClass == null || name == null || className.contains("$")) {
      return
    }
    val full = getInternalName(cls)?.replace('/', '.') ?: return
    val fixed = full.substring(full.length - className.length)
    val message = "Use '$' instead of '.' for inner classes; replace \"$className\" with \"$fixed\""
    val location = getRefLocation(context, nameNode)
    val fix = fix().replace().text(className).with(fixed).autoFix().build()
    context.report(INNERCLASS, element, location, message, fix)
  }

  /** Make sure [cls] is instantiatable. */
  private fun checkInstantiatable(
    context: XmlContext,
    evaluator: JavaEvaluator,
    cls: PsiClass,
    fqcn: String,
    nameNode: Node,
  ) {
    if (evaluator.isPrivate(cls)) {
      val message = "This class should be public (`$fqcn`)"
      context.report(INSTANTIATABLE, getRefLocation(context, nameNode), message)
    } else if (cls.containingClass != null && !evaluator.isStatic(cls)) {
      val message = "This inner class should be static (`$fqcn`)"
      context.report(INSTANTIATABLE, getRefLocation(context, nameNode), message)
    } else {
      val constructors = cls.constructors
      if (constructors.isEmpty() && hasImplicitDefaultConstructor(cls)) {
        return
      }
      for (constructor in cls.constructors) {
        if (constructor.hasAnnotation("javax.inject.Inject")) {
          // Assume instantiable via a custom AppComponentFactory / dependency injection.
          return
        }
      }
      for (constructor in cls.constructors) {
        if (constructor.parameterList.isEmpty) {
          if (evaluator.isPrivate(constructor)) {
            val message = "The default constructor must be public in `$fqcn`"
            context.report(INSTANTIATABLE, getRefLocation(context, nameNode), message)
            return
          } else {
            return
          }
        }
      }

      val message =
        "This class should provide a default constructor (a public constructor with no arguments) (`$fqcn`)"
      context.report(INSTANTIATABLE, getRefLocation(context, nameNode), message)
    }
  }

  private fun getRefLocation(context: XmlContext, nameNode: Node): Location {
    return if (nameNode is Attr) {
      context.getValueLocation(nameNode)
    } else {
      context.getLocation(nameNode)
    }
  }

  private fun reportMissing(location: Location, fqcn: String, context: Context) {
    val parentFile = location.file.parentFile
    val target =
      if (parentFile != null) {
        val parent = parentFile.name
        when (val type = ResourceFolderType.getFolderType(parent)) {
          null -> "manifest"
          LAYOUT -> "layout file"
          XML -> "preference header file"
          VALUES -> "analytics file"
          else -> {
            "${type.getName().lowercase(Locale.US)} file"
          }
        }
      } else {
        "the manifest"
      }
    val message =
      "Class referenced in the $target, `$fqcn`, was not found in the project or the libraries"
    context.report(MISSING, location, message)
  }

  companion object {
    val IMPLEMENTATION =
      Implementation(
        MissingClassDetector::class.java,
        Scope.MANIFEST_AND_RESOURCE_SCOPE,
        Scope.MANIFEST_SCOPE,
        Scope.RESOURCE_FILE_SCOPE,
      )

    /** Manifest or layout referenced classes missing from the project or libraries. */
    @JvmField
    val MISSING =
      Issue.create(
          id = "MissingClass",
          briefDescription = "Missing registered class",
          explanation =
            """
                    If a class is referenced in the manifest or in a layout file, it must \
                    also exist in the project (or in one of the libraries included by the \
                    project. This check helps uncover typos in registration names, or \
                    attempts to rename or move classes without updating the XML references \
                    properly.
                    """,
          category = Category.CORRECTNESS,
          priority = 8,
          severity = Severity.ERROR,
          moreInfo = "https://developer.android.com/guide/topics/manifest/manifest-intro.html",
          androidSpecific = true,
          implementation = IMPLEMENTATION,
        )
        .setAliases(listOf("MissingRegistered"))

    /** Are activity, service, receiver etc subclasses instantiatable? */
    @JvmField
    val INSTANTIATABLE =
      Issue.create(
        id = "Instantiatable",
        briefDescription = "Registered class is not instantiatable",
        explanation =
          """
          Activities, services, broadcast receivers etc. registered in the \
          manifest file (or for custom views, in a layout file) must be \
          "instantiatable" by the system, which means that the class must \
          be public, it must have an empty public constructor, and if it's an \
          inner class, it must be a static inner class.

          If you use a custom `AppComponentFactory` to instantiate app \
          components yourself, consider disabling this Lint issue in order \
          to avoid false positives.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.FATAL,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Is the right character used for inner class separators? */
    @JvmField
    val INNERCLASS =
      Issue.create(
        id = "InnerclassSeparator",
        briefDescription = "Inner classes should use `${"$"}` rather than `.`",
        explanation =
          """
                    When you reference an inner class in a manifest file, you must use '$' \
                    instead of '.' as the separator character, i.e. Outer${"$"}Inner instead of \
                    Outer.Inner.

                    (If you get this warning for a class which is not actually an inner class, \
                    it's because you are using uppercase characters in your package name, which \
                    is not conventional.)
                    """,
        category = Category.CORRECTNESS,
        priority = 3,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }
}
