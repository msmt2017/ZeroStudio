/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_DISCARD
import com.android.SdkConstants.ATTR_KEEP
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_SHRINK_MODE
import com.android.SdkConstants.ATTR_VIEW_BINDING_IGNORE
import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_RESOURCES
import com.android.SdkConstants.TAG_DATA
import com.android.SdkConstants.TAG_LAYOUT
import com.android.SdkConstants.TOOLS_PREFIX
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.ide.common.resources.usage.ResourceUsageModel
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintClient.Companion.isStudio
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.BinaryResourceScanner
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.ResourceContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.getBaseName
import com.android.tools.lint.detector.api.guessGradleLocation
import com.android.tools.lint.detector.api.isFileBasedResourceType
import com.android.tools.lint.detector.api.typeFromPsi
import com.android.tools.lint.model.LintModelResourceField
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils
import com.google.common.io.Files
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import java.io.File
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Finds unused resources. */
class UnusedResourceDetector :
  ResourceXmlDetector(), SourceCodeScanner, BinaryResourceScanner, XmlScanner {
  private val model = UnusedResourceDetectorUsageModel()
  private var projectUsesViewBinding = false

  /**
   * Map of data binding / view binding Binding classes (simple names, not fully qualified names) to
   * corresponding layout resource names (e.g. ActivityMainBinding -> "activity_main.xml")
   *
   * This map is created lazily only once it encounters a relevant layout file, since a significant
   * enough number of modules don't use data binding or view binding.
   */
  private var bindingClasses: MutableMap<String, String>? = null

  private fun addDynamicResources(context: Context) {
    val project = context.project
    val variant = project.buildVariant
    if (variant != null) {
      recordManifestPlaceHolderUsages(variant.manifestPlaceholders)
      addDynamicResources(project, variant.resValues)
    }
  }

  private fun recordManifestPlaceHolderUsages(manifestPlaceholders: Map<String, String>) {
    for (value in manifestPlaceholders.values) {
      ResourceUsageModel.markReachable(model.getResourceFromUrl(value))
    }
  }

  private fun addDynamicResources(
    project: Project,
    resValues: Map<String, LintModelResourceField>,
  ) {
    val resFields = resValues.values
    if (resFields.isNotEmpty()) {
      val location = guessGradleLocation(project)
      for (field in resFields) {
        val type =
          ResourceType.fromClassName(field.type)
            // Highly unlikely. This would happen if in the future we add
            // some new ResourceType, that the Gradle plugin (and the user's
            // Gradle file is creating) and it's an older version of Studio which
            // doesn't yet have this ResourceType in its enum.
            ?: continue
        val resource = model.declareResource(type, field.name, null) as LintResource
        resource.recordLocation(project, location)
      }
    }
  }

  override fun beforeCheckEachProject(context: Context) {
    projectUsesViewBinding = context.project.buildVariant?.buildFeatures?.viewBinding ?: false
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // Only report unused resources when checking an app project.
    if (context.project.isLibrary && SKIP_LIBRARIES.getValue(context)) {
      return
    }

    // We need to pull the resource graph along because a reference in
    // main could then result in a bunch of other resources being
    // references
    // For example, let's say @layout/foo includes @layout/bar, and in
    // the library both are unused. Then in the app module, we have a
    // reference to @layout/foo. We can't just filter out the explicitly
    // referenced resources when analyzing the app module; we have to
    // apply the resource graph such that we also notice this implies
    // @layout/bar is used.
    val model =
      partialResults
        .asSequence()
        .mapNotNull { (_, map) -> ResourceUsageModel.deserialize(map.getString(KEY_MODEL, "")) }
        .reduceOrNull { acc, model -> acc.apply { merge(model) } } ?: return

    for (resource in findUnused(context, model)) {
      val field = resource.field
      val message = "The resource `$field` appears to be unused"

      // Each module (possibly) provides a declaration location for the resource.
      val locations =
        // partialResults is essentially a list of LintMaps (one per module).
        partialResults
          .maps()
          .asSequence()
          // For each module's LintMap, get the location for field (if present).
          .mapNotNull { lintMap -> lintMap.getLocation(field) }
          .ifEmpty { sequenceOf(Location.create(context.project.dir)) }

      val fix = fix().data(KEY_RESOURCE_FIELD, field)
      for (location in locations) {
        context.report(Incident(getIssue(resource), location, message, fix))
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    when (context.phase) {
      1 -> {
        val project = context.project

        // Look for source sets that aren't part of the active variant;
        // we need to make sure we find references in those source sets as well
        // such that we don't incorrectly remove resources that are
        // used by some other source set.
        // In Gradle etc we don't need to do this (and in large projects it's expensive)
        if (sIncludeInactiveReferences && !project.isLibrary && isStudio) {
          project.buildVariant?.let(::addInactiveReferences)
        }
        addDynamicResources(context)
        val unused = findUnused(context, model).toSet()
        if (unused.isNotEmpty()) {
          model.unused = unused
          // Request another pass, and in the second pass we'll gather location
          // information for all declaration locations we've found
          context.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE)
        } else if (!context.isGlobalAnalysis()) {
          // No unused resources here, but still may have unused resources when
          // merging in library partial results, and in that case we'll need
          // the resource reference graph to cross check
          storeSerializedModel(context)
        }
      }
      2 -> {
        if (model.unused.isEmpty()) return

        // Final pass: we may have marked a few resource declarations with tools:ignore; we don't
        // check that on every single element, only those first thought to be unused. We don't just
        // remove the elements explicitly marked as unused, we revisit everything transitively such
        // that resources referenced from the ignored/kept resource are also kept.
        var unused = model.findUnused(model.unused.toList()).filterIsInstance<LintResource>()

        // For file based resources (such as R.layout) where we have no declaration locations, try
        // to find the file based on the filename, and use this as the declaration location, as it
        // has a simple mapping from the resource name (though the presence of qualifiers like -land
        // etc. makes it a little tricky if there's no base file provided).
        for (resource in unused) {
          // Only consider resources without a location that are file-based.
          if (
            resource.location.isNotEmpty() ||
              resource.type == null ||
              !isFileBasedResourceType(resource.type)
          ) {
            continue
          }

          val type = resource.type
          val name = resource.name
          // folders in alphabetical order such that we process
          // base folders first: we want the locations in base folder order
          val folders =
            context.project.resourceFolders
              .asSequence()
              .flatMap { it.listFilesOrEmpty() }
              .filter { it.name.startsWith(type.getName()) }
              .sortedBy(File::getName)
          val files =
            folders
              .flatMap { it.listFilesOrEmpty().sorted() }
              .filter { it.name.startsWith(name) && it.name.startsWith(".", name.length) }

          for (file in files) {
            resource.recordLocation(context.project, Location.create(file))
          }
        }

        // If we are in global analysis mode and one or more library modules were skipped then
        // filter out resources with no declaration locations; the resource was very probably
        // defined in one of the skipped library modules.
        if (context.isGlobalAnalysis() && context.driver.projects.any { !it.reportIssues }) {
          unused = unused.filter { it.location.isNotEmpty() }
        } else {
          // Otherwise, for resources that still don't have a declaration location, set a default
          // location.
          for (resource in unused) {
            if (resource.location.isEmpty()) {
              resource.recordLocation(context.project, Location.create(context.project.dir))
            }
          }
        }

        if (context.isGlobalAnalysis()) {
          // In global analysis mode, we have analyzed the root module and dependencies, so report
          // the unused resources.
          if (context.mainProject.isLibrary && SKIP_LIBRARIES.getValue(context)) {
            return
          }
          for (resource in unused) {
            val field = resource.field
            val message = "The resource `$field` appears to be unused"
            // Lint fix data for the IDE which will start the resource removal
            // refactoring with this resource field preselected
            val fix = fix().data(KEY_RESOURCE_FIELD, field)
            for (location in resource.location.values) {
              context.report(Incident(getIssue(resource), location, message, fix))
            }
          }
        } else {
          // When doing partial analysis, we are just analyzing a module in isolation. Instead of
          // reporting unused resources, just store the usage model for the current module, and the
          // location of each resource (from the current module) that appears to be unused (but is
          // likely to end up being used by another module).
          val lintMap = storeSerializedModel(context)
          for (resource in unused) {
            resource.location[context.project]?.let { location ->
              lintMap.put(resource.field, location)
            }
          }
        }
      }
      else -> error("Phase ${context.phase} not expected")
    }
  }

  private fun storeSerializedModel(context: Context): LintMap {
    // We don't need resource values; this is only used when shrinking resources
    // by looking at compiled code and needing to map back from inlined R constants
    val includeValues = false
    val serialized = model.serialize(includeValues)
    return context.getPartialResults(ISSUE).map().put(KEY_MODEL, serialized)
  }

  private fun recordInactiveJavaReferences(resDir: File) {
    fun recordFile(file: File, recordCode: (String) -> Unit) = withParsingErrorTolerated {
      recordCode(Files.asCharSource(file, Charsets.UTF_8).read())
    }
    for (file in resDir.listFilesOrEmpty()) {
      when {
        file.isDirectory -> recordInactiveJavaReferences(file)
        file.path.endsWith(DOT_JAVA) -> recordFile(file, model::tokenizeJavaCode)
        file.path.endsWith(DOT_KT) -> recordFile(file, model::tokenizeKotlinCode)
      }
    }
  }

  private fun recordInactiveXmlResources(resDir: File) {
    for (folder in resDir.listFilesOrEmpty()) {
      ResourceFolderType.getFolderType(folder.name)?.let { recordInactiveXmlResources(it, folder) }
    }
  }

  // Used for traversing resource folders *outside* of the normal Gradle variant
  // folders: these are not necessarily on the project path, so we don't have PSI files
  // for them
  private fun recordInactiveXmlResources(folderType: ResourceFolderType, folder: File) {
    for (file in folder.listFilesOrEmpty()) {
      withParsingErrorTolerated {
        when {
          SdkUtils.endsWithIgnoreCase(file.path, DOT_XML) -> {
            val xml = Files.asCharSource(file, Charsets.UTF_8).read()
            val document = XmlUtils.parseDocument(xml, true)
            model.visitXmlDocument(file, folderType, document)
          }
          else -> model.visitBinaryResource(folderType, file)
        }
      }
    }
  }

  // Tolerate parsing errors etc in these files; they're user
  // sources, and this is even for inactive source sets.
  private fun withParsingErrorTolerated(run: () -> Unit) =
    try {
      run()
    } catch (_: Throwable) {}

  private fun addInactiveReferences(active: LintModelVariant) {
    fun Collection<File>.forEachDir(record: (File) -> Unit) =
      asSequence().filter(File::isDirectory).forEach(record)
    for (provider in active.module.getInactiveSourceProviders(active)) {
      provider.resDirectories.forEachDir(::recordInactiveXmlResources)
      provider.javaDirectories.forEachDir(::recordInactiveJavaReferences)
    }
  }

  // override global appliesTo to check unused resources in the RAW folder
  override fun appliesTo(folderType: ResourceFolderType) = true

  // ---- Implements BinaryResourceScanner ----
  override fun checkBinaryResource(context: ResourceContext) =
    try {
      model.context = context
      model.visitBinaryResource(context.resourceFolderType, context.file)
    } finally {
      model.context = null
    }

  // ---- Implements XmlScanner ----
  override fun visitDocument(context: XmlContext, document: Document) {
    try {
      model.xmlContext = context
      model.context = context
      val folderType = context.resourceFolderType
      model.visitXmlDocument(context.file, folderType, document)

      // Data binding layout? If so look for usages of the binding class too
      val root = document.documentElement
      if (root != null && folderType == ResourceFolderType.LAYOUT) {
        when {
          // Data Binding layouts have a root <layout> tag
          TAG_LAYOUT == root.tagName -> {
            if (bindingClasses == null) bindingClasses = hashMapOf()

            // By default, a data binding class name is derived from the name of the XML
            // file, but this can be overridden with a custom name using the
            // {@code <data class="..." />} attribute.
            val fileName = context.file.name
            val resourceName = getBaseName(fileName)

            tailrec fun bindingClassFrom(data: Element?): String? =
              when (data) {
                null -> null
                else -> {
                  val bindingClass = data.getAttribute(ATTR_CLASS)
                  when {
                    bindingClass.isNotEmpty() ->
                      bindingClass.substring(bindingClass.lastIndexOf('.') + 1)
                    else -> bindingClassFrom(XmlUtils.getNextTagByName(data, TAG_DATA))
                  }
                }
              }

            val bindingClass =
              bindingClassFrom(XmlUtils.getFirstSubTagByName(root, TAG_DATA))
                ?: (resourceName.toClassName(postfix = "Binding"))

            bindingClasses!![bindingClass] = resourceName
          }
          // ViewBinding always derives its name from the layout file. However, a layout
          // file should be skipped if the root tag contains the "viewBindingIgnore=true"
          // attribute.
          projectUsesViewBinding -> {
            val ignoreAttribute = root.getAttributeNS(TOOLS_URI, ATTR_VIEW_BINDING_IGNORE)
            if (VALUE_TRUE != ignoreAttribute) {
              if (bindingClasses == null) bindingClasses = hashMapOf()
              val fileName = context.file.name
              val resourceName = getBaseName(fileName)
              val bindingClass = resourceName.toClassName(postfix = "Binding")
              bindingClasses!![bindingClass] = resourceName
            }
          }
        }
      }
    } finally {
      model.xmlContext = null
      model.context = null
    }
  }

  // ---- implements SourceCodeScanner ----
  override fun appliesToResourceRefs() = true

  override fun visitResourceReference(
    context: JavaContext,
    node: UElement,
    type: ResourceType,
    name: String,
    isFramework: Boolean,
  ) {
    if (!isFramework) {
      ResourceUsageModel.markReachable(model.addResource(type, name, null))
    }
  }

  override fun getApplicableUastTypes() =
    listOf(
      UCallableReferenceExpression::class.java,
      UCallExpression::class.java,
      UField::class.java,
      USimpleNameReferenceExpression::class.java,
      UQualifiedReferenceExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext): UElementHandler? =
    // If using data binding / view binding, we also have to look for references to the
    // Binding classes which could be implicit usages of layout resources
    when (val bindingClasses = bindingClasses) {
      null -> null
      else ->
        object : UElementHandler() {

          private fun <C : PsiClass> visitClass(
            psiClass: C?,
            getBindingClassName: (C) -> String? = PsiClass::getName,
          ) {
            if (psiClass != null && isBindingClass(context.evaluator, psiClass)) {
              bindingClasses[getBindingClassName(psiClass)]?.let { resourceName ->
                ResourceUsageModel.markReachable(
                  model.getResource(ResourceType.LAYOUT, resourceName)
                )
              }
            }
          }

          override fun visitCallExpression(node: UCallExpression) =
            visitClass(node.resolve()?.containingClass)

          override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
            when (val resolved = node.resolve()) {
              is PsiClass -> visitClass(resolved) { node.identifier }
              is PsiField ->
                if (resolved.containingClass?.name in bindingClasses) {
                  ResourceUsageModel.markReachable(
                    model.getResource(ResourceType.ID, resolved.name)
                  )
                }
            }
          }

          override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) =
            visitClass((node.resolve() as? PsiMember)?.containingClass)

          override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
            // referencing a binding class's field marks the corresponding id as reachable
            val className = (node.receiver.getExpressionType() as? PsiClassType)?.className
            if (className in bindingClasses) {
              val id = node.resolvedName
              if (id != null) {
                ResourceUsageModel.markReachable(model.getResource(ResourceType.ID, id))
              }
            }
          }

          override fun visitField(node: UField) {
            val classType = node.typeFromPsi as? PsiClassType
            visitClass(classType?.resolve())
            // When using property delegation, the field type will not be the binding class.
            // It will be a delegate type with a type argument, so check that type argument too.
            classType?.parameters?.forEach { typeArgument ->
              visitClass((typeArgument as? PsiClassType)?.resolve())
            }
          }

          private fun isBindingClass(evaluator: JavaEvaluator, binding: PsiClass) =
            evaluator.extendsClass(binding, "android.databinding.ViewDataBinding", true) ||
              evaluator.extendsClass(binding, "androidx.databinding.ViewDataBinding", true) ||
              evaluator.extendsClass(binding, "androidx.viewbinding.ViewBinding", true)
        }
    }

  private class LintResource(type: ResourceType?, name: String?, value: Int) :
    ResourceUsageModel.Resource(type, name, value) {

    /**
     * Declaration location. We store this per module to ensure that global analysis results match
     * partial analysis results. In partial analysis mode, the map will only ever contain a location
     * for the current module.
     */
    val location: MutableMap<Project, Location> = LinkedHashMap()

    fun recordLocation(project: Project, location: Location) {
      // There can be duplicate resources within a module (for example, a resource per language).
      // Trying to store all of these can lead to an explosion of locations, and is not particularly
      // helpful for the user. We just replace the existing location, but only if the new location
      // has a shorter resource directory name; this heuristic favors default resources (resource
      // directories without qualifiers).

      val existingLocation = this.location[project]
      // If we do not have a location, then store it.
      if (existingLocation == null) {
        this.location[project] = location
        return
      }

      // Otherwise, if the new resource directory name is shorter than the existing, store the new
      // location.

      fun Location.resDirName(): String? {
        val dir = this.file.parentFile ?: return null
        val resDir = dir.parentFile ?: return null
        if (resDir.name != FD_RESOURCES) return null
        return dir.name
      }

      fun Location.resDirNameShorterThan(location: Location): Boolean {
        val left = this.resDirName() ?: return false
        val right = location.resDirName() ?: return false
        return left.length < right.length
      }

      if (location.resDirNameShorterThan(existingLocation)) {
        this.location[project] = location
      }
    }
  }

  private class UnusedResourceDetectorUsageModel : ResourceUsageModel() {
    var xmlContext: XmlContext? = null
    var context: Context? = null
    var unused: Set<Resource> = hashSetOf()

    override fun createResource(type: ResourceType, name: String, realValue: Int) =
      LintResource(type, name, realValue)

    override fun readText(file: File) =
      context?.client?.readFile(file)?.toString() ?: super.readText(file)

    public override fun declareResource(type: ResourceType, name: String, node: Node?): Resource? {
      if (name.isEmpty()) {
        return null
      }
      val resource = super.declareResource(type, name, node) as LintResource
      context?.let { context ->
        resource.isDeclared = context.project.reportIssues
        val xmlContext = xmlContext
        if (context.phase == 2 && unused.contains(resource)) {
          when {
            xmlContext != null &&
              xmlContext.driver.isSuppressed(xmlContext, getIssue(resource), node) ->
              resource.isKeep = true
            // For positions we try to use the name node rather than the
            // whole declaration element
            node == null || xmlContext == null ->
              resource.recordLocation(context.project, Location.create(context.file))
            else ->
              resource.recordLocation(
                context.project,
                xmlContext.getLocation((node as? Element)?.getAttributeNode(ATTR_NAME) ?: node),
              )
          }
        }
        if (type == ResourceType.RAW && isKeepFile(name, xmlContext)) {
          // Don't flag raw.keep: these are used for resource shrinking
          // keep lists
          //    https://developer.android.com/studio/build/shrink-code.html
          resource.isReachable = true
        }
      }
      return resource
    }

    companion object {
      private fun isKeepFile(name: String, xmlContext: XmlContext?) =
        if ("keep" == name) {
          true
        } else if (
          xmlContext?.document?.documentElement == null ||
            xmlContext.document.documentElement.firstChild != null
        ) {
          false
        } else {
          val attributes = xmlContext.document.documentElement.attributes
          (0 until attributes.length).any { i ->
            val attr = attributes.item(i)
            val nodeName = attr.nodeName
            if (
              !nodeName.startsWith(XMLNS_PREFIX) &&
                !nodeName.startsWith(TOOLS_PREFIX) &&
                TOOLS_URI != attr.namespaceURI
            ) {
              return@isKeepFile false
            } else {
              nodeName.endsWith(ATTR_SHRINK_MODE) ||
                nodeName.endsWith(ATTR_DISCARD) ||
                nodeName.endsWith(ATTR_KEEP)
            }
          }
        }
    }
  }

  companion object {
    const val KEY_RESOURCE_FIELD = "field"
    private const val KEY_MODEL = "model"
    // TODO: Switch to configuration property!
    private const val EXCLUDE_TESTS_PROPERTY = "lint.unused-resources.exclude-tests"
    private const val INCLUDE_TESTS_PROPERTY = "lint.unused-resources.include-tests"

    private val IMPLEMENTATION =
      EnumSet.of(
          Scope.MANIFEST,
          Scope.ALL_RESOURCE_FILES,
          Scope.ALL_JAVA_FILES,
          Scope.BINARY_RESOURCE_FILE,
        )
        .let { scopeSet ->
          // Whether to include test sources in the scope. Currently true but controllable
          // with a couple of flags.
          if (
            VALUE_TRUE == System.getProperty(INCLUDE_TESTS_PROPERTY) ||
              VALUE_FALSE != System.getProperty(EXCLUDE_TESTS_PROPERTY)
          ) {
            scopeSet.add(Scope.TEST_SOURCES)
          }
          Implementation(UnusedResourceDetector::class.java, scopeSet)
        }

    @JvmField
    val SKIP_LIBRARIES =
      BooleanOption(
        "skip-libraries",
        "Whether the unused resource check should skip reporting unused resources in libraries",
        true,
        """
        Many libraries will declare resources that are part of the library surface; other \
        modules depending on the library will also reference the resources. To avoid reporting \
        all these resources as unused (in the context of a library), the unused resource check \
        normally skips reporting unused resources in libraries. Instead, run the unused resource \
        check on the consuming app module (along with `checkDependencies=true`).

        However, there are cases where you want to check that all the resources declared in \
        a library are used; in that case, you can disable the skip option.
        """,
      )

    private const val EXCLUDING_TESTS_EXPLANATION =
      """
                The unused resource check can ignore tests. If you want to include \
                resources that are only referenced from tests, consider packaging them \
                in a test source set instead.

                You can include test sources in the unused resource check by setting \
                the system property \
                $INCLUDE_TESTS_PROPERTY \
                =true, and to \
                exclude them (usually for performance reasons), use \
                $EXCLUDE_TESTS_PROPERTY \
                =true.
                """

    /** Unused resources (other than ids). */
    @JvmField
    val ISSUE =
      Issue.create(
          id = "UnusedResources",
          briefDescription = "Unused resources",
          explanation =
            """
                Unused resources make applications larger and slow down builds.

                $EXCLUDING_TESTS_EXPLANATION,
                """,
          category = Category.PERFORMANCE,
          priority = 3,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
        )
        .setOptions(listOf(SKIP_LIBRARIES))

    /** Unused id's */
    @JvmField
    val ISSUE_IDS =
      Issue.create(
        id = "UnusedIds",
        briefDescription = "Unused id",
        explanation =
          """
                This resource id definition appears not to be needed since it is not referenced \
                from anywhere. Having id definitions, even if unused, is not necessarily a bad \
                idea since they make working on layouts and menus easier, so there is not a \
                strong reason to delete these.

                $EXCLUDING_TESTS_EXPLANATION
                """,
        category = Category.PERFORMANCE,
        priority = 1,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        enabledByDefault = false,
      )

    /**
     * Whether the resource detector will look for inactive resources (e.g. resource and code
     * references in source sets that are not the primary/active variant)
     */
    @JvmField var sIncludeInactiveReferences = true

    private fun findUnused(
      context: Context,
      model: ResourceUsageModel,
    ): Sequence<ResourceUsageModel.Resource> {
      model.processToolsAttributes()
      val idEnabled = context.isEnabled(ISSUE_IDS)
      return model
        .findUnused()
        .asSequence()
        .filter(ResourceUsageModel.Resource::isDeclared)
        // Remove id's if the user has disabled reporting issue ids
        .filter { idEnabled || it.type != ResourceType.ID }
    }

    private fun getIssue(resource: ResourceUsageModel.Resource) =
      if (resource.type != ResourceType.ID) ISSUE else ISSUE_IDS

    // Copy from android.databinding.tool.util.ParserHelper:
    fun String.toClassName(postfix: String): String =
      split("[_-]".toRegex())
        .dropLastWhile { it.isEmpty() }
        .joinToString(separator = "", postfix = postfix, transform = ::capitalize)

    // Copy from android.databinding.tool.util.StringUtils: using
    // this instead of IntelliJ's more flexible method to ensure
    // we compute the same names as data-binding generated code
    private fun capitalize(string: String): String {
      val ch = string.firstOrNull()
      return when {
        ch == null -> string
        Character.isTitleCase(ch) -> string
        else -> ch.titlecaseChar().toString() + string.substring(1)
      }
    }
  }
}

private fun File.listFilesOrEmpty(): List<File> =
  when (val files = listFiles()) {
    null -> listOf()
    else -> files.asList()
  }
