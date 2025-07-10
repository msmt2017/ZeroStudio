/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier.BCP_47_PREFIX
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getBaseName
import com.android.utils.XmlUtils.getPreviousTagByName
import com.android.utils.iterator
import com.android.utils.next
import com.android.utils.subtag
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Sets
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Check which makes sure that an actions.xml file is correct. See XmltreeActionsSchemaParser in the
 * dev tools codebase to check behavior enforced in the dev console.
 */
class ActionsXmlDetector : ResourceXmlDetector() {
  private var foundProblem = false

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.XML
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    foundProblem = false
    val documentElement = document.documentElement
    val root = documentElement ?: return
    if (TAG_ACTIONS != root.tagName) {
      if (TAG_ACTION == root.tagName) {
        wrongParent(context, root, TAG_ACTIONS)
      }
      return
    }

    checkActions(context, documentElement)

    if (context.isGlobalAnalysis()) {
      // In the IDE we can access the merged manifest directly
      if (context.project.isLibrary) {
        return
      }
      if (isMissingFromManifest(context, getBaseName(context.file.name))) {
        val incident = createIncident(root, context, getBaseName(context.file.name))
        context.report(incident)
      }
    } else {
      if (foundProblem) {
        return
      }

      val incident = createIncident(root, context, getBaseName(context.file.name))
      context.report(incident, map().put(KEY_ID, getBaseName(context.file.name)))
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.isLibrary) {
      return false
    }
    val actionResourceName = map.getString(KEY_ID, null) ?: return false
    return isMissingFromManifest(context, actionResourceName)
  }

  private fun isMissingFromManifest(context: Context, actionResourceName: String): Boolean {
    val mainProject = context.mainProject
    val mergedManifest = mainProject.mergedManifest ?: return false
    val root = mergedManifest.documentElement ?: return false
    val application = root.subtag(TAG_APPLICATION) ?: return false
    if (application.firstChild == null) {
      return false
    }
    var metadata = application.subtag(TAG_META_DATA)
    while (metadata != null) {
      val name = metadata.getAttributeNS(ANDROID_URI, ATTR_NAME)
      if (name == "com.google.android.actions") {
        val resource = metadata.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
        val url = ResourceUrl.parse(resource)
        if (url != null && url.name == actionResourceName) {
          // Found the resource declaration
          return false
        }
      }
      metadata = metadata.next(TAG_META_DATA)
    }

    return true
  }

  private fun createIncident(
    actions: Element,
    context: XmlContext,
    actionResourceName: String,
  ): Incident {
    return Incident(
      ISSUE,
      actions,
      context.getElementLocation(actions),
      "This action resource should be registered in the manifest under the " +
        "`<application>` tag as " +
        "`<meta-data android:name=\"com.google.android.actions\" " +
        "android:resource=\"@xml/$actionResourceName\" />`",
    )
  }

  private fun checkActions(context: XmlContext, actions: Element) {
    for (action in actions) {
      checkParent(context, action) ?: return
      val tag = action.tagName
      if (tag == TAG_ACTION) {
        checkAction(context, action)
      } // else, like dev console, ignore unrecognized elements.
    }

    // Make sure the locales listed in the locale string are valid locales
    val localeNode = actions.getAttributeNode("supportedLocales")
    if (localeNode != null) {
      val locales = localeNode.value
      var index = 0
      locales.split(",").forEach { locale ->
        val bcp = BCP_47_PREFIX + locale.trim().replace("-", "+")
        LocaleQualifier.getQualifier(bcp)
          ?: run {
            val loc = context.getValueLocation(localeNode)
            var start = loc.start ?: return
            // Adjust offset to attribute value
            start = DefaultPosition(start.line, start.column + index, start.offset + index)
            val end =
              DefaultPosition(
                start.line,
                start.column + locale.length,
                start.offset + locale.length,
              )
            val location = Location.create(context.file, start, end)
            val message = "Invalid BCP-47 locale qualifier `$locale`"
            report(context, actions, location, message)
            return
          }
        index += locale.length + 1
      }
    }
  }

  private fun checkAction(context: XmlContext, action: Element) {
    checkParent(context, action) ?: return
    // Make sure name is defined
    checkRequiredAttribute(context, action, ATTR_INTENT_NAME) ?: return

    var atLeastOneFulfillment = false
    var atLeastOneEntitySetReference = false
    var parameterNames: MutableSet<String>? = null
    var foundNonRequiredTemplate = false
    for (child in action) {
      val tag = child.tagName
      if (tag == TAG_ACTION) {
        nestingNotAllowed(context, child)
        return
      }
      checkParent(context, child) ?: return
      when (tag) {
        TAG_PARAMETER -> {
          if (parameterNames == null) {
            parameterNames = mutableSetOf()
          }
          val hasEntitySetReference = checkParameter(context, child, parameterNames)
          if (hasEntitySetReference) {
            atLeastOneEntitySetReference = true
          }
        }
        TAG_FULFILLMENT -> {
          checkFulfillment(context, child)
          atLeastOneFulfillment = true
          if (!templateRequiresParameters(child)) {
            foundNonRequiredTemplate = true
          }
        }
        else -> {
          // Like dev console, ignore unrecognized elements.
        }
      }
    }

    // Make sure we have at least one fulfillment
    if (!atLeastOneFulfillment) {
      if (!atLeastOneEntitySetReference) {
        report(
          context,
          action,
          context.getElementLocation(action),
          "`<action>` must declare a `<fulfillment>` or a `<parameter>` with an `<entity-set-reference>`",
        )
      }
    } else if (!foundNonRequiredTemplate) {
      report(
        context,
        action,
        context.getElementLocation(action),
        "At least one <fulfillment> `$ATTR_URL_TEMPLATE` must not be required",
      )
    }
  }

  /**
   * Returns whether the template on this template requires parameters; this is true if the template
   * has one or more required parameters.
   */
  private fun templateRequiresParameters(fulfillment: Element): Boolean {
    for (child in fulfillment) {
      if (child.tagName == TAG_PARAMETER_MAPPING) {
        val required = child.getAttribute(ATTR_REQUIRED)
        if (required == VALUE_TRUE) {
          return true
        }
      }
    }

    return false
  }

  private fun checkParameter(
    context: XmlContext,
    parameter: Element,
    parameterNames: MutableSet<String>,
  ): Boolean {
    checkParent(context, parameter) ?: return false

    // Make sure name is defined
    checkRequiredAttribute(context, parameter, ATTR_NAME) ?: return false

    // "type" is optional for built-in parameters

    // Make sure name is unique
    val name = checkRequiredAttribute(context, parameter, ATTR_NAME) ?: return false
    checkNotAlreadyPresent(
      name,
      ATTR_NAME,
      parameterNames,
      context,
      parameter,
      TAG_ACTION,
      TAG_PARAMETER,
    )

    var hasEntitySetReference = false
    for (child in parameter) {
      val tag = child.tagName
      if (tag == TAG_PARAMETER) {
        nestingNotAllowed(context, child)
        return false
      }
      checkParent(context, child) ?: return false
      if (tag == TAG_ENTITY_SET_REFERENCE) {
        checkEntitySetReference(context, child)
        hasEntitySetReference = true
      }
    }

    return hasEntitySetReference
  }

  private fun checkEntitySetReference(context: XmlContext, entitySetReference: Element) {
    checkRequiredAttribute(context, entitySetReference, ATTR_URL_FILTER) ?: return
  }

  private fun checkFulfillment(context: XmlContext, fulfillment: Element) {
    checkParent(context, fulfillment) ?: return
    val urlTemplate =
      checkRequiredAttribute(context, fulfillment, ATTR_URL_TEMPLATE, allowReference = false)
        ?: return

    val templateParameters = getUriTemplateParameters(urlTemplate)

    val intentParameterNames = mutableSetOf<String>()
    val urlParameters = mutableSetOf<String>()
    for (child in fulfillment) {
      val tag = child.tagName
      if (tag == TAG_FULFILLMENT) {
        nestingNotAllowed(context, child)
        return
      }
      checkParent(context, child) ?: return
      when (tag) {
        TAG_PARAMETER_MAPPING -> {
          val parameter = checkParameterMapping(context, child, intentParameterNames, urlParameters)
          if (
            parameter != null &&
              !templateParameters.contains(parameter) &&
              !parameter.startsWith(PREFIX_RESOURCE_REF) &&
              // special built-in parameter
              parameter != VAR_URL
          ) {
            report(
              context,
              child,
              context.getElementLocation(child),
              "The parameter `$parameter` is not present in the `$ATTR_URL_TEMPLATE`",
            )
          }
        }
        else -> {
          // Like dev console, ignore unrecognized elements.
        }
      }
    }

    // See if we should have a built-in "url" parameter: this happens if there
    // is at least one parameter with an entity reference
    if (
      templateParameters.contains(VAR_URL) &&
        !urlParameters.contains(VAR_URL) &&
        // See if any parameter in the action defines an entity reference. We can't just
        // keep track of this during iteration and pass it in here, we have to search up
        // from here since the parameter can be defined before or after the fulfillment tag.
        hasEntitySetReference(fulfillment.parentNode as Element)
    ) {
      urlParameters.add(VAR_URL)
    }

    // Make sure the parameters are fully mapped
    val missing = Sets.difference(templateParameters, urlParameters)
    if (missing.isNotEmpty()) {
      val attributeLocation =
        context.getValueLocation(fulfillment.getAttributeNode(ATTR_URL_TEMPLATE))
      val message =
        if (missing.size == 1) {
          "The parameter ${missing.first()} is not defined as a `<$TAG_PARAMETER_MAPPING>` element below"
        } else {
          "The parameters ${missing.joinToString(separator = " and ") { it }
                    } are not defined as `<$TAG_PARAMETER_MAPPING>` elements below"
        }
      report(context, fulfillment, attributeLocation, message)
    }
  }

  /**
   * Returns true if the given action tag defines at least one entity reference in one of its
   * parameters.
   */
  private fun hasEntitySetReference(action: Element): Boolean {
    assert(action.tagName == TAG_ACTION)

    for (parameter in action) {
      if (parameter.tagName == TAG_PARAMETER) {
        if (parameter.subtag(TAG_ENTITY_SET_REFERENCE) != null) {
          return true
        }
      }
    }

    return false
  }

  /**
   * Checks parameter mapping parameter and returns the url parameter name (or null if not declared
   * or any other validation error was found)
   */
  private fun checkParameterMapping(
    context: XmlContext,
    parameterMapping: Element,
    intentParams: MutableSet<String>,
    urlParameters: MutableSet<String>,
  ): String? {
    val intentParameter =
      checkRequiredAttribute(
        context,
        parameterMapping,
        ATTR_INTENT_PARAMETER,
        allowReference = false,
      ) ?: return null

    checkParent(context, parameterMapping) ?: return null

    val urlParameter =
      checkRequiredAttribute(context, parameterMapping, ATTR_URL_PARAMETER, allowReference = false)
        ?: return null
    urlParameters.add(urlParameter)

    checkNotAlreadyPresent(
      intentParameter,
      ATTR_INTENT_PARAMETER,
      intentParams,
      context,
      parameterMapping,
      TAG_FULFILLMENT,
      TAG_PARAMETER_MAPPING,
    ) ?: return null

    return urlParameter
  }

  /**
   * Checks that this element has the expected parent; this catches cases where you cut & paste
   * fragments into the wrong place.
   *
   * Returns null if a problem was found, otherwise true. Using null here allows callers to use
   * Kotlin's elvis operator to quickly bail if an error was found (since we don't want to add
   * multiple errors once one has been found.
   */
  private fun checkParent(context: XmlContext, element: Element): Boolean? {
    val tag = element.tagName
    val expectedParent =
      when (tag) {
        TAG_ACTION -> TAG_ACTIONS
        TAG_ACTION_DISPLAY -> TAG_ACTION
        TAG_PARAMETER,
        TAG_FULFILLMENT -> TAG_ACTION
        TAG_PARAMETER_MAPPING -> TAG_FULFILLMENT
        TAG_ENTITY_SET_REFERENCE -> TAG_PARAMETER
        else -> return true
      }
    val actualParent = element.parentNode?.nodeName ?: return true

    if (expectedParent != actualParent) {
      wrongParent(context, element, expectedParent)
      return null
    } else if (tag == actualParent) {
      nestingNotAllowed(context, element)
      return null
    }

    return true
  }

  private fun wrongParent(context: XmlContext, element: Element, expected: String) {
    report(
      context,
      element,
      context.getNameLocation(element),
      "`<${element.tagName}>` must be inside `<$expected>`",
    )
  }

  private fun nestingNotAllowed(context: XmlContext, element: Element) {
    report(
      context,
      element,
      context.getNameLocation(element),
      "Nesting `<${element.tagName}>` is not allowed",
    )
  }

  /**
   * Checks that the given name is not already in the given names set; if so reports a duplicate
   * error. Returns null or true for the same reason as documented in [checkParent].
   */
  private fun checkNotAlreadyPresent(
    name: String?,
    nameAttribute: String,
    parameterNames: MutableSet<String>,
    context: XmlContext,
    parameter: Element,
    parentTag: String,
    nameTag: String,
  ): Boolean? {
    name ?: return true

    val duplicate = !parameterNames.add(name)
    if (duplicate) {
      val location = context.getLocation(parameter.getAttributeNode(nameAttribute))

      var prev = getPreviousTagByName(parameter, parameter.tagName)
      while (prev != null) {
        val attr = prev.getAttributeNode(nameAttribute)
        if (attr?.value == name) {
          location.secondary = context.getLocation(attr)
          break
        }
        prev = getPreviousTagByName(prev, parameter.tagName)
      }

      report(
        context,
        parameter,
        location,
        "`<$parentTag>` contains two `<$nameTag>` elements with the same $nameAttribute, `$name`",
      )
      return null
    }
    return true
  }

  /**
   * Checks that the given attribute is present on the element. Optionally allows allows to
   * allow/disallow blank values, and resource references. Returns null or the actual resource value
   * found for the same reason as documented in [checkParent].
   */
  private fun checkRequiredAttribute(
    context: XmlContext,
    element: Element,
    attribute: String,
    allowBlank: Boolean = false,
    allowReference: Boolean = true,
  ): String? {
    val value = element.getAttribute(attribute)
    if (value != null && (allowBlank || !value.isBlank())) {
      if (!allowReference && value.startsWith(PREFIX_RESOURCE_REF)) {
        report(
          context,
          element,
          context.getLocation(element.getAttributeNode(attribute)),
          "`$attribute` must be a value, not a reference",
        )
      }

      return value
    }
    val fix = LintFix.create().set().todo(null, attribute).build()
    report(
      context,
      element,
      context.getElementLocation(element),
      "Missing required attribute `$attribute`",
      fix,
    )
    return null
  }

  private fun report(
    context: XmlContext,
    element: Element,
    location: Location,
    message: String,
    fix: LintFix? = null,
  ) {
    foundProblem = true
    context.report(Incident(ISSUE, element, location, message, fix))
  }

  companion object {
    /** Validation of `<actions>` XML elements. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ValidActionsXml",
        briefDescription = "Invalid Action Descriptor",
        explanation = "Ensures that an actions XML file is properly formed",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.FATAL,
        implementation = Implementation(ActionsXmlDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
        // Disabled by default for now because the grammar is actively evolving: b/132733887
        enabledByDefault = false,
      )

    private const val KEY_ID = "id"
    private const val TAG_ACTIONS = "actions"
    private const val TAG_ACTION = "action"
    private const val TAG_ACTION_DISPLAY = "action-display"
    private const val TAG_PARAMETER = "parameter"
    private const val TAG_FULFILLMENT = "fulfillment"
    private const val TAG_PARAMETER_MAPPING = "parameter-mapping"
    private const val TAG_ENTITY_SET_REFERENCE = "entity-set-reference"
    private const val ATTR_URL_TEMPLATE = "urlTemplate"
    private const val ATTR_URL_FILTER = "urlFilter"
    private const val ATTR_URL_PARAMETER = "urlParameter"
    private const val ATTR_INTENT_PARAMETER = "intentParameter"
    private const val ATTR_INTENT_NAME = "intentName"
    private const val ATTR_RESOURCE = "resource"
    private const val VAR_URL = "url"

    // Lightweight parsing of https://tools.ietf.org/html/rfc6570 to
    // extract the parameter names for URL templates level 4.
    @VisibleForTesting
    fun getUriTemplateParameters(urlTemplate: String): Set<String> {
      val length = urlTemplate.length
      val variables = mutableSetOf<String>()
      for (begin in 0 until length) {
        val c = urlTemplate[begin]
        if (c == '{') {
          val end = urlTemplate.indexOf('}', begin + 1)
          if (end != -1) {
            addVariables(variables, urlTemplate, begin, end + 1)
          }
        }
      }

      return variables
    }

    /**
     * Adds in any variables found in the { } section of the URL template. This is handling the
     * range from [from] inclusive to [to] inclusive in string [s] assuming the content is an
     * "expression" according to RFC 6570.
     */
    private fun addVariables(variables: MutableSet<String>, s: String, from: Int, to: Int) {
      if (from > to - 2) { // empty
        return
      }

      // From https://tools.ietf.org/html/rfc6570#section-2.3
      //    expression    =  "{" [ operator ] variable-list "}"
      //    operator      =  op-level2 / op-level3 / op-reserve
      //    op-level2     =  "+" / "#"
      //    op-level3     =  "." / "/" / ";" / "?" / "&"
      //    op-reserve    =  "=" / "," / "!" / "@" / "|"
      @Suppress("MoveVariableDeclarationIntoWhen") val operator = s[from + 1]
      val hasOperator =
        when (operator) {
          '+',
          '#' -> true // op-level2
          '.',
          '/',
          ';',
          '?',
          '&' -> true // op-level3
          '=',
          ',',
          '!',
          '@',
          '|' -> true // op-reserve
          else -> false
        }
      var offset = from + if (hasOperator) 2 else 1
      while (offset < to) {
        // Find end of varspec
        val beginVarSpec = offset
        var endVarSpec = beginVarSpec
        var variableEnd = -1
        while (endVarSpec < to) {
          //    variable-list =  varspec *( "," varspec )
          //    varspec       =  varname [ modifier-level4 ]
          //    varname       =  varchar *( ["."] varchar )
          //    varchar       =  ALPHA / DIGIT / "_" / pct-encoded
          //    modifier-level4 =  prefix / explode
          //    prefix        =  ":" max-length
          //    max-length    =  %x31-39 0*3DIGIT   ; positive integer < 10000
          //    explode       =  "*"
          val c = s[endVarSpec]
          if (c == ',' || c == '}') {
            if (variableEnd == -1) {
              variableEnd = endVarSpec
            }
            break
          } else if (c == ':' || c == '*') {
            variableEnd = endVarSpec
          }
          endVarSpec++
        }

        if (variableEnd > beginVarSpec) {
          val variable = s.substring(beginVarSpec, variableEnd)
          variables.add(variable)
        }

        offset = endVarSpec + 1
      }
    }
  }
}
