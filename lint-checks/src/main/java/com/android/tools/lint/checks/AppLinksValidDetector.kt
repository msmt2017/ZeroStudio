/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.SdkConstants.ANDROID_NS_NAME
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ALLOW
import com.android.SdkConstants.ATTR_AUTO_VERIFY
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_FRAGMENT
import com.android.SdkConstants.ATTR_FRAGMENT_ADVANCED_PATTERN
import com.android.SdkConstants.ATTR_FRAGMENT_PATTERN
import com.android.SdkConstants.ATTR_FRAGMENT_PREFIX
import com.android.SdkConstants.ATTR_FRAGMENT_SUFFIX
import com.android.SdkConstants.ATTR_HOST
import com.android.SdkConstants.ATTR_IGNORE
import com.android.SdkConstants.ATTR_MIME_TYPE
import com.android.SdkConstants.ATTR_ORDER
import com.android.SdkConstants.ATTR_PATH
import com.android.SdkConstants.ATTR_PATH_ADVANCED_PATTERN
import com.android.SdkConstants.ATTR_PATH_PATTERN
import com.android.SdkConstants.ATTR_PATH_PREFIX
import com.android.SdkConstants.ATTR_PATH_SUFFIX
import com.android.SdkConstants.ATTR_PORT
import com.android.SdkConstants.ATTR_PRIORITY
import com.android.SdkConstants.ATTR_QUERY
import com.android.SdkConstants.ATTR_QUERY_ADVANCED_PATTERN
import com.android.SdkConstants.ATTR_QUERY_PATTERN
import com.android.SdkConstants.ATTR_QUERY_PREFIX
import com.android.SdkConstants.ATTR_QUERY_SUFFIX
import com.android.SdkConstants.ATTR_SCHEME
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.PREFIX_THEME_REF
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_DATA
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_URI_RELATIVE_FILTER_GROUP
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_0
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceUrl
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_ADVANCED_GLOB
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_LITERAL
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_PREFIX
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_SIMPLE_GLOB
import com.android.tools.lint.checks.AndroidPatternMatcher.PATTERN_SUFFIX
import com.android.tools.lint.checks.AppLinksValidDetector.Companion.ElementWrapper
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.Companion.TODO
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.isDataBindingExpression
import com.android.tools.lint.detector.api.isManifestPlaceHolderExpression
import com.android.tools.lint.detector.api.resolvePlaceHolders
import com.android.utils.XmlUtils
import com.android.utils.XmlUtils.fromXmlAttributeValue
import com.android.utils.XmlUtils.toXmlAttributeValue
import com.android.utils.iterator
import com.android.utils.subtagCount
import com.android.xml.AndroidManifest.ATTRIBUTE_NAME
import com.google.common.base.Joiner
import java.lang.Character.isUpperCase
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern
import kotlin.text.contains
import kotlin.text.split
import org.jetbrains.annotations.VisibleForTesting
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Checks for invalid app links URLs */
class AppLinksValidDetector : Detector(), XmlScanner {
  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS, TAG_INTENT_FILTER)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (val tag = element.tagName) {
      TAG_INTENT_FILTER -> checkIntentFilter(context, element)
      TAG_ACTIVITY,
      TAG_ACTIVITY_ALIAS -> checkActivity(context, element)
      else -> error("Unhandled tag $tag")
    }
  }

  /**
   * Reports incidents for intent filter [intentFilter]. Note that intent filters inside an
   * <activity> (as opposed to those inside a <service>, <receiver>, etc.) have additional
   * restrictions/requirements, which are not checked here.
   */
  private fun checkIntentFilter(context: XmlContext, intentFilter: Element) {
    val intentFilterData = getIntentFilterData(ElementWrapper(intentFilter, context))
    val namespace = intentFilter.lookupPrefix(ANDROID_URI) ?: ANDROID_NS_NAME

    // --- Check "splitting" (Recommend splitting a data tag into multiple tags with individual
    // attributes ---
    if (intentFilterData.dataTags.dataTagElements.size <= 1) return
    for (dataTag in intentFilterData.dataTags) {
      val node = (dataTag as? ElementWrapper)?.element ?: continue
      val location = context.getLocation(node)
      val indentAmount = location.start?.column ?: 4
      val subTags = mutableListOf<String>()
      dataTag.getAttributeWrapper(ATTR_SCHEME)?.rawValue?.let {
        subTags.add("""<data $namespace:scheme="$it" />""")
      }
      dataTag.getAttributeWrapper(ATTR_HOST)?.rawValue?.let { host ->
        val port = dataTag.getAttributeWrapper(ATTR_PORT)?.rawValue
        val portIfNeeded = if (port == null) "" else """ $namespace:port="$port""""
        subTags.add("""<data $namespace:host="$host"$portIfNeeded />""")
      }
      for (pathAttribute in PATH_ATTRIBUTES) {
        dataTag.getAttributeWrapper(pathAttribute)?.rawValue?.let {
          subTags.add("""<data $namespace:$pathAttribute="$it" />""")
        }
      }
      if (subTags.size > 1) {
        context.report(
          INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES,
          node,
          context.getLocation(node),
          "Consider splitting $TAG_DATA tag into multiple tags with individual" +
            " attributes to avoid confusion",
          fix()
            .replace()
            .with(subTags.joinToString("\n" + indentation(indentAmount)))
            .robot(true)
            // This quick-fix copies data elements, so it may be affected by other quick-fixes or
            // generate new problems.
            .independent(false)
            .build(),
        )
      }
    }
  }

  private fun checkActivity(context: XmlContext, element: Element) {
    val infos =
      XmlUtils.getSubTagsByName(element, TAG_INTENT_FILTER).map {
        handleIntentFilterInActivity(context, it)
      }
    // Check that if any intent filter uses ACTION_VIEW, the activity is exported
    val isExported =
      element.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED)?.value.let {
        it.isNullOrBlank() || it == VALUE_TRUE
      }
    if (!isExported && infos.any { it.actionSet.contains(ACTION_VIEW) }) {
      // Report error if the activity supporting action view is not exported.
      reportUrlError(
        context,
        element,
        context.getLocation(element),
        "Activity supporting ACTION_VIEW is not exported",
      )
    }
    // Check that any tools:testUrl tags match at least one intent filter
    var current = XmlUtils.getFirstSubTagByName(element, TAG_VALIDATION)
    while (current != null) {
      if (TOOLS_URI == current.namespaceURI) {
        val testUrlAttr = current.getAttributeNode("testUrl")
        if (testUrlAttr == null) {
          val message = "Expected `testUrl` attribute"
          reportUrlError(context, current, context.getLocation(current), message)
        } else {
          val testUrlString = testUrlAttr.value
          try {
            val testUrl = URL(testUrlString)
            val reason = checkTestUrlMatchesAtLeastOneInfo(testUrl, infos)
            if (reason != null) {
              reportTestUrlFailure(
                context,
                testUrlAttr,
                context.getValueLocation(testUrlAttr),
                reason,
              )
            }
          } catch (e: MalformedURLException) {
            val message = "Invalid test URL: " + e.localizedMessage
            reportTestUrlFailure(
              context,
              testUrlAttr,
              context.getValueLocation(testUrlAttr),
              message,
            )
          }
        }
      } else {
        reportTestUrlFailure(
          context,
          current,
          context.getNameLocation(current),
          "Validation nodes should be in the `tools:` namespace to " +
            "ensure they are removed from the manifest at build time",
        )
      }
      current = XmlUtils.getNextTagByName(current, TAG_VALIDATION)
    }
  }

  private fun reportUrlError(
    context: XmlContext,
    node: Node,
    location: Location,
    message: String,
    quickfixData: LintFix? = null,
  ) {
    // Validation errors were reported here before
    if (context.driver.isSuppressed(context, _OLD_ISSUE_URL, node)) {
      return
    }
    context.report(VALIDATION, node, location, message, quickfixData)
  }

  private fun reportUrlWarning(
    context: XmlContext,
    node: Node,
    location: Location,
    message: String,
    quickfixData: LintFix? = null,
  ) {
    context.report(APP_LINK_WARNING, node, location, message, quickfixData)
  }

  private fun reportTestUrlFailure(
    context: XmlContext,
    node: Node,
    location: Location,
    message: String,
  ) {
    context.report(TEST_URL, node, location, message)
  }

  /**
   * Given a test URL and a list of [IntentFilterData], this method checks whether the URL matches,
   * and if so returns null, otherwise returning the reason for the mismatch.
   *
   * @param testUrl the URL to test
   * @param infos the URL information
   * @return null for a match, otherwise the failure reason
   */
  @VisibleForTesting
  fun checkTestUrlMatchesAtLeastOneInfo(testUrl: URL, infos: List<IntentFilterData>): String? {
    val reasons = mutableListOf<String>()
    for (info in infos) {
      val reason = info.match(testUrl) ?: return null // found a match
      if (!reasons.contains(reason)) {
        reasons.add(reason)
      }
    }
    return if (reasons.isNotEmpty()) {
      "Test URL " + Joiner.on(" or ").join(reasons)
    } else {
      null
    }
  }

  /**
   * Processes an intent filter element ([intentFilter]), inside an activity element, in the
   * [XmlContext] [context].
   *
   * Note that intent filters inside an <activity> (as opposed to those inside a <service>,
   * <receiver>, etc.) have additional restrictions/requirements. As a result, this function is only
   * called for intent filters inside an activity, whereas [checkIntentFilter] is called for all
   * intent filters (including those inside a <service>, <receiver>, etc.).
   *
   * @return the [IntentFilterData] for this intent filter
   */
  private fun handleIntentFilterInActivity(
    context: XmlContext,
    intentFilter: Element,
  ): IntentFilterData {
    val intentFilterData = getIntentFilterData(ElementWrapper(intentFilter, context))

    // --- Check "missing data node" (Intent filter has ACTION_VIEW & CATEGORY_BROWSABLE, but no
    // data node) ---
    val hasActionView = intentFilterData.actionSet.contains(ACTION_VIEW)
    val hasCategoryBrowsable = intentFilterData.categorySet.contains(CATEGORY_BROWSABLE)

    // If this intent has ACTION_VIEW and CATEGORY_BROWSABLE, but doesn't have a data node, it's
    // a likely mistake.
    // Also, don't report this error if autoVerify is true. If autoVerify is true, then we provide a
    // quick-fix to help the user populate the necessary data elements (see below).
    if (
      intentFilterData.dataTags.isEmpty &&
        hasCategoryBrowsable &&
        intentFilterData.autoVerify != VALUE_TRUE
    ) {
      reportUrlError(
        context,
        intentFilter,
        context.getLocation(intentFilter),
        "Missing data element",
      )
    }

    // --- Check mimeType ---
    for (mimeTypeElement in intentFilterData.dataTags.mimeTypeElements) {
      val node = (mimeTypeElement as? AttrWrapper)?.attr ?: continue
      val value = mimeTypeElement.substitutedValue ?: continue
      // Ignore cases where resolving string resource or manifest placeholder failed,
      // so we're still using the raw string.
      if (isSubstituted(value)) continue
      if (value.any(Char::isUpperCase)) {
        val resolvedValueMessage =
          if (mimeTypeElement.rawValue != value) {
            " (without placeholders, value is `$value`)"
          } else {
            ""
          }
        reportUrlError(
          context,
          node,
          context.getValueLocation(node),
          "Mime-type matching is case sensitive and should only use lower-case characters$resolvedValueMessage",
        )
      }
    }

    // --- Check scheme ---
    for (schemeElement in intentFilterData.dataTags.schemeElements) {
      val node = (schemeElement as? AttrWrapper)?.attr ?: continue
      val value = schemeElement.substitutedValue ?: continue
      // Ignore cases where resolving string resource or manifest placeholder failed,
      // so we're still using the raw string.
      if (isSubstituted(value)) continue
      if (value.isBlank()) {
        reportUrlError(context, node, context.getLocation(node), "`${node.name}` cannot be empty")
        continue
      }
      if (value.endsWith(":")) {
        reportUrlError(
          context,
          node,
          context.getValueLocation(node),
          "Don't include trailing colon in the `scheme` declaration",
        )
      } else if (value.any(Char::isUpperCase)) {
        reportUrlError(
          context,
          node,
          context.getValueLocation(node),
          "Scheme matching is case sensitive and should only " + "use lower-case characters",
        )
      }
      // Scheme matching also does URI decoding. However, any <a> tag where the scheme has a % or
      // a whitespace is treated as a relative path, so unexpected behavior may occur if the
      // developer attempts to create a custom scheme with non-URI characters in it.
      // However, it's difficult for us to be sure that an intent filter is intended to be used as
      // a deeplink unless the intent filter is also an applink (i.e. has autoVerify="true"), so
      // we don't want to unnecessarily enforce that scheme attributes can't have non-URI
      // characters.
      // (If autoVerify="true", then scheme must be http(s), so URI-encoded characters are not
      // allowed anyway.)
    }

    // --- Check host ---

    // Note that while whitespace is not allowed in URIs, Android performs URI decoding.
    // Thus, `scheme://hello%20world` matches an intent filter with android:host="hello world".
    // So it is OK for hosts to have whitespace characters.
    for (hostElement in intentFilterData.dataTags.hostElements) {
      val node = (hostElement as? AttrWrapper)?.attr ?: continue
      val value = hostElement.substitutedValue ?: continue
      // Ignore cases where resolving string resource or manifest placeholder failed,
      // so we're still using the raw string.
      if (isSubstituted(value)) continue

      if (value.isBlank()) {
        reportUrlError(context, node, context.getLocation(node), "`${node.name}` cannot be empty")
        continue
      }
      if (value.lastIndexOf('*') > 0) {
        reportUrlError(
          context,
          node,
          context.getValueLocation(node),
          "The host wildcard (`*`) can only be the first character",
        )
      } else if (value.any(Char::isUpperCase)) {
        reportUrlError(
          context,
          node,
          context.getValueLocation(node),
          "Host matching is case sensitive and should only use lower-case characters",
        )
      }
      if (
        intentFilterData.autoVerify == VALUE_TRUE && !isSubstituted(value) && !value.contains(".")
      ) {
        reportUrlError(
          context,
          node,
          context.getValueLocation(node),
          "Android App Links' `host` attributes must be valid web domains",
        )
      }
    }

    // --- Check port ---
    for (portElement in intentFilterData.dataTags.portElements) {
      val node = (portElement as? AttrWrapper)?.attr ?: continue
      val value = portElement.substitutedValue ?: continue
      // Ignore cases where resolving string resource or manifest placeholder failed,
      // so we're still using the raw string.
      if (isSubstituted(value)) continue
      if (value.isBlank()) {
        reportUrlError(context, node, context.getLocation(node), "`${node.name}` cannot be empty")
        continue
      }
      try {
        val port = value.toInt() // might also throw number exc
        if (port < 1 || port > 65535) {
          throw NumberFormatException()
        }
      } catch (e: NumberFormatException) {
        reportUrlError(context, node, context.getValueLocation(node), "not a valid port number")
      }
    }

    // --- Check that port is not by itself ---
    for (dataTag in intentFilterData.dataTags) {
      val port = dataTag.getAttributeWrapper(ATTR_PORT)
      if (port?.substitutedValue != null && dataTag.getAttributeWrapper(ATTR_HOST) == null) {
        val node = (port as? AttrWrapper)?.attr ?: continue
        // The port *only* takes effect if it's specified on the *same* XML
        // element as the host (this isn't true for the other attributes,
        // which can be spread out across separate <data> elements)
        reportUrlError(
          context,
          node,
          context.getValueLocation(node),
          "The port must be specified in the same `<data>` " + "element as the `host`",
        )
      }
    }

    // --- Check path attributes ---
    for (dataTag in intentFilterData.dataTags) {
      for (path in PATH_ATTRIBUTES.mapNotNull { dataTag.getAttributeWrapper(it) }) {
        handlePath(intentFilterData, intentFilter, dataTag, path, context)
      }
    }
    // --- Check uri-relative-filter-groups ---
    for (group in intentFilterData.uriRelativeFilterGroups) {
      suggestRemovingAttributes(
        context,
        intentFilter,
        group.dataTagInfo,
        listOf(ATTR_SCHEME, ATTR_HOST, ATTR_PORT, ATTR_MIME_TYPE),
        "Attributes not starting with `path`, `query`, or `fragment` in `uri-relative-filter-group` are ignored",
      ) {
        it in PATH_ATTRIBUTES ||
          it in QUERY_ATTRIBUTES ||
          it in FRAGMENT_ATTRIBUTES ||
          it == ATTR_IGNORE
      }
      for (dataTag in group.dataTagInfo) {
        for (path in PATH_ATTRIBUTES.mapNotNull { dataTag.getAttributeWrapper(it) }) {
          handlePath(intentFilterData, intentFilter, dataTag, path, context)
        }
      }
      for (query in group.queryElements) {
        val node = (query as? AttrWrapper) ?: continue
        if (node.substitutedValue.isNullOrBlank()) {
          context.report(
            URI_RELATIVE_FILTER_GROUP,
            node.attr,
            context.getLocation(node.attr),
            "`${node.name}` cannot be empty",
          )
        }
      }
      for (fragment in group.fragmentElements) {
        val node = (fragment as? AttrWrapper) ?: continue
        if (node.substitutedValue.isNullOrBlank()) {
          context.report(
            URI_RELATIVE_FILTER_GROUP,
            node.attr,
            context.getLocation(node.attr),
            "`${node.name}` cannot be empty",
          )
        }
      }
    }

    // --- Check whether this is "almost an app link" ---
    // i.e. intent filter has everything we would expect from an app link but doesn't have
    // autoVerify
    if (
      // These are all the conditions we expect from app links
      intentFilterData.dataTags.schemes.isNotEmpty() &&
        intentFilterData.dataTags.schemes.all(::isWebScheme) &&
        intentFilterData.dataTags.hostPortPairs.isNotEmpty() &&
        hasActionView &&
        hasCategoryBrowsable &&
        intentFilterData.categorySet.contains(CATEGORY_DEFAULT) &&
        // If intent filter already has autoVerify=true, the intent filter is correct.
        intentFilterData.autoVerify != VALUE_TRUE &&
        // If developer manually specified autoVerify=false, don't generate an error.
        intentFilterData.autoVerify != VALUE_FALSE
    ) {
      // Just highlight the <intent-filter> tag, instead of the whole intent filter
      reportUrlWarning(
        context,
        intentFilter,
        context.getNameLocation(intentFilter),
        """This intent filter has the format of an Android App Link but is \
            |missing the `autoVerify` attribute; add `android:autoVerify="true"` \
            |to ensure your domain will be validated and enable App Link-related \
            |Lint warnings. If you do not want clicked URLs to bring the user to \
            |your app, remove the `android.intent.category.BROWSABLE` category, or \
            |set `android:autoVerify="false"` to make it clear this is not intended \
            |to be an Android App Link."""
          .trimMargin(),
        fix().set(ANDROID_URI, ATTR_AUTO_VERIFY, VALUE_TRUE).autoFix().build(),
      )
    }

    // Validation
    // autoVerify means this is an Android App Link:
    // https://developer.android.com/training/app-links#android-app-links
    if (hasAutoVerifyButInvalidAppLink(intentFilterData)) {
      // Construct content which includes all necessary elements + attributes for app links.
      val contentToInsert = StringBuilder()
      // Keep track of human-readable names of what we needed to add
      val insertionDescriptions = mutableListOf<String>()

      val innerIndent =
        XmlUtils.getFirstSubTag(intentFilter)?.let { context.getLocation(it).start?.column }
          ?: ((context.getLocation(intentFilter).start?.column ?: 0) + DEFAULT_INDENT_AMOUNT)
      val newLineAndInnerIndent = "\n" + indentation(innerIndent)
      val namespace = intentFilter.lookupPrefix(ANDROID_URI) ?: ANDROID_NS_NAME

      if (!intentFilterData.actionSet.contains(ACTION_VIEW)) {
        contentToInsert
          .append(newLineAndInnerIndent)
          .append("""<$TAG_ACTION $namespace:$ATTRIBUTE_NAME="$ACTION_VIEW" />""")
        insertionDescriptions.add("VIEW action")
      }
      for (categoryName in sequenceOf(CATEGORY_BROWSABLE, CATEGORY_DEFAULT)) {
        if (!intentFilterData.categorySet.contains(categoryName)) {
          contentToInsert
            .append(newLineAndInnerIndent)
            .append("""<$TAG_CATEGORY $namespace:$ATTRIBUTE_NAME="$categoryName" />""")
          insertionDescriptions.add(
            "${categoryName.substringAfter("android.intent.category.")} category"
          )
        }
      }
      // Provide a quick-fix to add schemes
      if (
        intentFilterData.dataTags.schemes.none { isSubstituted(it) || isWebScheme(it) } &&
          // If an existing "needs scheme" fix comes from a host with no scheme, don't create a
          // redundant fix
          !(intentFilterData.dataTags.schemes.isEmpty() &&
            intentFilterData.dataTags.hostPortPairs.isNotEmpty())
      ) {
        contentToInsert
          .append(newLineAndInnerIndent)
          .append("""<$TAG_DATA $namespace:$ATTR_SCHEME="$HTTP" />""")
          .append(newLineAndInnerIndent)
          .append("""<$TAG_DATA $namespace:$ATTR_SCHEME="$HTTPS" />""")
        insertionDescriptions.add("`http(s)` scheme")
      }
      val needsHostFix =
        intentFilterData.dataTags.hostPortPairs.isEmpty() &&
          // If an existing "needs host" fix comes from a port with no host, don't create a
          // redundant fix
          intentFilterData.dataTags.dataTagElements.none {
            it.getAttributeWrapper(ATTR_PORT) != null && it.getAttributeWrapper(ATTR_HOST) == null
          }
      if (needsHostFix) {
        contentToInsert
          .append(newLineAndInnerIndent)
          .append("""<$TAG_DATA $namespace:$ATTR_HOST="$TODO" />""")
        insertionDescriptions.add("`host` attribute")
      }

      // The first child is the (potentially whitespace) element immediately after
      // <intent-filter ... >
      val firstChildStart = context.getLocation(intentFilter.firstChild).start
      // If the content to insert ended up being empty, don't report an issue. (This will happen if
      // there's a "more fundamental" issue with the intent filter, such as a port with no
      // host, and we don't want to report redundant issues in these cases.)
      if (contentToInsert.isNotEmpty() && firstChildStart != null) {
        val message =
          when (insertionDescriptions.size) {
            1 ->
              "${insertionDescriptions.single()} is missing, but is required for Android App Links"
            2 ->
              "${insertionDescriptions.first()} and ${insertionDescriptions.last()} are missing, but are required for Android App Links"
            else ->
              "Several elements/attributes (such as ${insertionDescriptions.first()}) required for Android App Links are missing"
          }
        val fixName =
          when (insertionDescriptions.size) {
            1 -> "Add ${insertionDescriptions.single()}"
            2 -> "Add ${insertionDescriptions.first()} and ${insertionDescriptions.last()}"
            else -> "Add missing elements/attributes"
          }

        val fix =
          fix()
            .name(fixName)
            .replace()
            .with(contentToInsert.toString())
            .apply {
              if (needsHostFix) {
                select(TODO)
              }
            }
            .range(
              Location.create(context.file, firstChildStart, firstChildStart)
                .withSource(intentFilter)
            )
            // If the host is required, the user needs to fill it in. Otherwise, no user input is
            // needed.
            .robot(!needsHostFix)
            .independent(true)
            .build()

        reportUrlError(
          context,
          intentFilter,
          context.getNameLocation(intentFilter),
          message,
          // If we are in Studio then add quick-fix data so that Studio adds the
          // "Launch App Links Assistant" quick-fix.
          if (LintClient.isStudio) {
            fix().group(fix().data(KEY_SHOW_APP_LINKS_ASSISTANT, true), fix)
          } else {
            fix
          },
        )
      }

      // If the intent filter has a MIME type, suggest deleting it
      suggestRemovingAttributes(
        context,
        intentFilter,
        intentFilterData.dataTags,
        listOf(ATTR_MIME_TYPE),
        "MIME types prevent Android App Links from matching",
      ) {
        it != ATTR_MIME_TYPE
      }

      // --- Check for splitting web and non-web schemes ---
      val webSchemes = intentFilterData.dataTags.schemes.filter { isWebScheme(it) }
      val customSchemes = intentFilterData.dataTags.schemes.filterNot { isWebScheme(it) }
      if (
        webSchemes.isNotEmpty() &&
          customSchemes.isNotEmpty() &&
          intentFilterData.dataTags.hostPortPairs.isNotEmpty()
      ) {
        // Intent filter contains both web and non-web schemes
        val intentFilterIndentAmount = context.getLocation(intentFilter).start?.column ?: 0
        val intentFilterChildIndentAmount =
          ((intentFilterData.dataTags.dataTagElements.first() as? ElementWrapper)?.element)?.let {
            context.getLocation(it).start?.column
          } ?: DEFAULT_INDENT_AMOUNT
        val indentDiff = intentFilterChildIndentAmount - intentFilterIndentAmount
        val startIndent = indentation(intentFilterIndentAmount)
        val intentFilterChildIndent = indentation(intentFilterChildIndentAmount)
        val namespace = intentFilter.lookupPrefix(ANDROID_URI) ?: ANDROID_NS_NAME

        val webSchemeIntentFilterText = StringBuilder("<")
        // We know that this intentFilter has autoVerify, so copy it over
        copyTagWithAttributes(intentFilter, webSchemeIntentFilterText)
        webSchemeIntentFilterText.append(">")
        val customSchemeIntentFilterText = StringBuilder("<$TAG_INTENT_FILTER")
        for (i in 0 until intentFilter.attributes.length) {
          val item = intentFilter.attributes.item(i)
          if (item.nodeName.endsWith(ATTR_AUTO_VERIFY)) continue
          customSchemeIntentFilterText.append(" ")
          customSchemeIntentFilterText.append(item.nodeName)
          customSchemeIntentFilterText.append("=\"")
          customSchemeIntentFilterText.append(xmlEscape(item.nodeValue))
          customSchemeIntentFilterText.append('"')
        }
        customSchemeIntentFilterText.append(">")
        for (subTag in intentFilter) {
          // If the tag is a data tag, IntentFilterData already has all the needed information.
          // Therefore, we only copy non-data tags.
          if (subTag.tagName != TAG_DATA) {
            for (sb in sequenceOf(webSchemeIntentFilterText, customSchemeIntentFilterText)) {
              sb.append("\n")
              sb.append(intentFilterChildIndent)
              recursivelyCopy(subTag, intentFilterChildIndentAmount, indentDiff, sb)
            }
          }
        }
        for (scheme in webSchemes.sorted()) {
          webSchemeIntentFilterText.append("\n")
          webSchemeIntentFilterText.append(intentFilterChildIndent)
          webSchemeIntentFilterText.append("""<data $namespace:scheme="$scheme" />""")
        }
        for (scheme in customSchemes.sorted()) {
          customSchemeIntentFilterText.append("\n")
          customSchemeIntentFilterText.append(intentFilterChildIndent)
          customSchemeIntentFilterText.append("""<data $namespace:scheme="$scheme" />""")
        }
        val intentFilterTextAfterSchemes = StringBuilder()
        for ((host, port) in
          intentFilterData.dataTags.hostPortPairs.sortedWith(
            compareBy<Pair<String?, String?>> { it.first }.thenBy { it.second }
          )) {
          intentFilterTextAfterSchemes.append("\n")
          intentFilterTextAfterSchemes.append(intentFilterChildIndent)
          intentFilterTextAfterSchemes.append("""<data $namespace:host="$host"""")
          if (!port.isNullOrBlank()) {
            intentFilterTextAfterSchemes.append(""" $namespace:port="$port"""")
          }
          intentFilterTextAfterSchemes.append(" />")
        }
        for (path in intentFilterData.dataTags.rawPaths.sorted()) {
          intentFilterTextAfterSchemes.append("\n")
          intentFilterTextAfterSchemes.append(intentFilterChildIndent)
          intentFilterTextAfterSchemes.append(
            """<data $namespace:${path.attributeName}="${xmlEscape(path.attributeValue)}" />"""
          )
        }
        for (mimeType in intentFilterData.dataTags.rawMimeTypes.sorted()) {
          intentFilterTextAfterSchemes.append("\n")
          intentFilterTextAfterSchemes.append(intentFilterChildIndent)
          intentFilterTextAfterSchemes.append("""<data $namespace:mimeType="$mimeType" />""")
        }
        intentFilterTextAfterSchemes.append("\n")
        intentFilterTextAfterSchemes.append(startIndent)
        intentFilterTextAfterSchemes.append("</$TAG_INTENT_FILTER>")

        val replacementText = webSchemeIntentFilterText
        replacementText.append(intentFilterTextAfterSchemes)
        replacementText.append("\n")
        replacementText.append(startIndent)
        replacementText.append(customSchemeIntentFilterText)
        replacementText.append(intentFilterTextAfterSchemes)
        // We store the incidents in partial results so that we can check the merged manifest before
        // reporting them.
        // Because we report the incidents in checkPartialResults, automatic suppression doesn't
        // work.
        if (!context.driver.isSuppressed(context, APP_LINK_SPLIT_TO_WEB_AND_CUSTOM, intentFilter)) {
          // Make a string of comma-separated hosts.
          // We extract the hosts and put them in a set so that host1 will not appear twice if it
          // appears in both (host1, port1) and (host1, port2). This is because ports do not matter
          // for domain verification.
          // Also note that commas cannot appear in valid domain names.
          val hosts =
            intentFilterData.dataTags.hostPortPairs
              .mapTo(mutableSetOf()) { it.first }
              .joinToString(",")

          val map = context.getPartialResults(APP_LINK_SPLIT_TO_WEB_AND_CUSTOM).map()
          map.put(
            "${map.size}",
            map()
              // Serialization and deserialization of Incidents is not allowed, so we store all the
              // information that we'll need to create and report the Incident (from
              // checkPartialResults).
              .put(KEY_SPLIT_TO_WEB_AND_CUSTOM_NAME_LOCATION, context.getNameLocation(intentFilter))
              .put(KEY_SPLIT_TO_WEB_AND_CUSTOM_REPLACEMENT_RANGE, context.getLocation(intentFilter))
              .put(KEY_SPLIT_TO_WEB_AND_CUSTOM_REPLACEMENT_TEXT, replacementText.toString())
              .put(KEY_SPLIT_TO_WEB_AND_CUSTOM_HOSTS, hosts),
          )
        }
      }
    }

    val showMissingSchemeCheck =
      intentFilterData.dataTags.schemes.isEmpty() &&
        (intentFilterData.dataTags.paths.isNotEmpty() ||
          intentFilterData.dataTags.hostPortPairs.isNotEmpty())

    val firstData =
      (intentFilterData.dataTags.firstOrNull() as? ElementWrapper)?.element
        ?: return intentFilterData

    // --- Check "missing scheme" ---
    // If there are hosts, paths, or ports, then there should be a scheme.
    // We insist on this because hosts, paths, and ports will be ignored if there is no explicit
    // scheme, which makes the intent filter very misleading.
    if (showMissingSchemeCheck) {
      val fix =
        if (intentFilterData.dataTags.hostPortPairs.isEmpty()) {
          // If there are no hosts, ask the user to specify the scheme.
          fix().set().todo(ANDROID_URI, ATTR_SCHEME).independent(true)
        } else {
          // If there's at least one host, it's likely they want http(s), so we can prompt them with
          // http.
          fix().set().todo(ANDROID_URI, ATTR_SCHEME, "http").independent(true)
        }
      reportUrlError(
        context,
        firstData,
        context.getLocation(firstData),
        "At least one `scheme` must be specified",
        fix.build(),
      )
    }

    // --- Check "missing URI" (This intent filter has a view action but no URI) ---
    // We only show this check if the "missing scheme check" isn't already showing, because they're
    // very similar.
    val showMissingUriCheck =
      hasActionView &&
        // Per documentation
        //   https://developer.android.com/guide/topics/manifest/data-element.html
        // "If the filter has a data type set (the mimeType attribute) but no scheme, the
        //  content: and file: schemes are assumed."
        intentFilterData.dataTags.schemes.isEmpty() &&
        intentFilterData.dataTags.mimeTypes.isEmpty()
    if (!showMissingSchemeCheck && showMissingUriCheck) {
      reportUrlError(
        context,
        firstData,
        context.getLocation(firstData),
        "VIEW actions require a URI",
        fix()
          .alternatives(
            fix().set().todo(ANDROID_URI, ATTR_SCHEME).build(),
            fix().set().todo(ANDROID_URI, ATTR_MIME_TYPE).build(),
          ),
      )
    }

    // --- Check "missing host" (Hosts are required when a path is used. ) ---
    // We insist on this because paths will be ignored if there is no host, which makes the intent
    // filter very misleading.
    if (
      intentFilterData.dataTags.paths.isNotEmpty() &&
        intentFilterData.dataTags.hostPortPairs.isEmpty()
    ) {
      val fix = LintFix.create().set().todo(ANDROID_URI, ATTR_HOST).build()
      reportUrlError(
        context,
        firstData,
        context.getLocation(firstData),
        "At least one `host` must be specified",
        fix,
      )
    }

    // --- Check "view + browsable" (If this intent filter has an ACTION_VIEW action, and it has a
    // http URL but doesn't have BROWSABLE, it may be a mistake, so we will report a warning.) ---
    if (
      hasActionView && intentFilterData.dataTags.schemes.any(::isWebScheme) && !hasCategoryBrowsable
    ) {
      reportUrlError(
        context,
        intentFilter,
        context.getLocation(intentFilter),
        "Activity supporting ACTION_VIEW is not set as BROWSABLE",
      )
    }
    return intentFilterData
  }

  /**
   * Performs checks on path* tags. Note that the parent can be <data> or
   * <uri-relative-filter-group>.
   */
  private fun handlePath(
    intentFilterData: IntentFilterData,
    intentFilter: Element,
    dataTag: TagWrapper,
    path: AttributeWrapper,
    context: XmlContext,
  ) {
    // Note that while whitespace is not allowed in URIs, Android performs URI decoding.
    // Thus, `scheme://host/hello%20world` matches an intent filter with android:path="hello
    // world".
    // So it is OK for paths to have whitespace characters.
    val attribute = (path as? AttrWrapper)?.attr ?: return
    val name = path.name
    val rawValue = path.rawValue
    val substitutedValue = path.substitutedValue
    if (rawValue.isNullOrBlank() || substitutedValue.isNullOrBlank()) {
      reportUrlError(
        context,
        attribute,
        context.getLocation(attribute),
        "`${attribute.name}` cannot be empty",
      )
      return
    }
    // Ignore cases where resolving string resource or manifest placeholder failed,
    // so we're still using the raw string.
    if (isSubstituted(substitutedValue)) return
    if (!substitutedValue.startsWith("/") && name in setOf(ATTR_PATH, ATTR_PATH_PREFIX)) {
      val fix = LintFix.create().replace().text(rawValue).with("/$rawValue").build()
      reportUrlError(
        context,
        attribute,
        context.getValueLocation(attribute),
        "`${attribute.name}` attribute should start with `/`, but it is `$substitutedValue`",
        fix,
      )
    }
    if (
      !(substitutedValue.startsWith("/") || substitutedValue.startsWith(".*")) &&
        name == ATTR_PATH_PATTERN
    ) {
      reportUrlError(
        context,
        attribute,
        context.getValueLocation(attribute),
        "`${attribute.name}` attribute should start with `/` or `.*`, but it is `$substitutedValue`",
      )
    }

    // --- Check for query parameters (?) and URI fragments (#) ---

    // Query parameters (?) and URI fragments (#) are not included in the intent when the user
    // clicks on the URI from the browser.
    // To be safe, we'll only perform this check if the intent filter has autoVerify.
    if (intentFilterData.autoVerify == VALUE_TRUE) {
      // Android matches query params separately.
      // Also, the query string, as specified in the Android manifest, should not be
      // URI-encoded.
      // That is, query="param=value!" matches ?param=value! and ?param=value%21.
      // query="param=value%21" matches neither ?param=value! nor ?param=value%21.
      val queryParameters =
        substitutedValue
          .split('?')
          .let { if (it.size == 2) it[1] else "" }
          .substringBefore('#')
          .splitToSequence("&amp;", "&")
          .filter { it.isNotBlank() }
          .toSet()
      // Android treats the fragment as a single section.
      val fragmentInUri =
        substitutedValue.split('#').let { if (it.size == 2) it[1] else "" }.substringBefore('?')

      if (queryParameters.isNotEmpty() || fragmentInUri.isNotBlank()) {
        // The query/fragment string, as specified in the Android manifest, should not be
        // URI-encoded.
        // See getQueryAndFragmentParameters below.
        val namespace = intentFilter.lookupPrefix(ANDROID_URI) ?: ANDROID_NS_NAME
        val queries = queryParameters.sorted().map { """<data $namespace:query="$it" />""" }
        val pathBeforeQueryAndFragment = substitutedValue.split('?', '#')[0]
        val dataElement = (dataTag as? ElementWrapper)?.element ?: return
        val parent = dataElement.parentNode as? Element ?: return
        val dataIndent = context.getLocation(dataElement).start?.column ?: DEFAULT_INDENT_AMOUNT
        val newLineAndDataIndent = "\n" + indentation(dataIndent)
        val indexOfThisPath = dataTag.attributes.indexOfFirst { it.rawValue == path.rawValue }
        val otherAttributesInThisDataTag =
          dataTag.attributes.toMutableList().apply {
            removeAt(indexOfThisPath)
            sortBy { it.name }
          }
        val otherAttributesText =
          when {
            otherAttributesInThisDataTag.isEmpty() -> ""
            else ->
              "<data " +
                otherAttributesInThisDataTag.joinToString(" ") {
                  "$namespace:${it.name}=\"${it.rawValue}\""
                } +
                " />$newLineAndDataIndent"
          }
        val message =
          when (parent.tagName) {
            TAG_URI_RELATIVE_FILTER_GROUP ->
              "`path` attributes do not support query parameters or fragments"
            else ->
              "App link matching does not support query parameters or fragments, " +
                "unless using `<uri-relative-filter-group>` (introduced in Android 15)"
          }
        val fixText =
          when (parent.tagName) {
            TAG_URI_RELATIVE_FILTER_GROUP -> {
              val newLineAndIndentedFragment =
                when (fragmentInUri) {
                  "" -> ""
                  else -> """$newLineAndDataIndent<data $namespace:fragment="$fragmentInUri" />"""
                }
              """$otherAttributesText<data $namespace:$name="$pathBeforeQueryAndFragment" />""" +
                concatenateWithIndent(queries, newLineAndDataIndent) +
                newLineAndIndentedFragment
            }
            TAG_INTENT_FILTER -> {
              val parentIndent = context.getLocation(parent).start?.column ?: 0
              val startingIndent = indentation(dataIndent)
              val innerIndentAmount = 2 * dataIndent - parentIndent
              val newLineAndInnerIndent = "\n" + indentation(innerIndentAmount)
              val newLineAndIndentedFragment =
                when (fragmentInUri) {
                  "" -> ""
                  else -> """$newLineAndInnerIndent<data $namespace:fragment="$fragmentInUri" />"""
                }
              "$otherAttributesText<uri-relative-filter-group>" +
                """$newLineAndInnerIndent<data $namespace:$name="$pathBeforeQueryAndFragment" />""" +
                concatenateWithIndent(queries, newLineAndInnerIndent) +
                newLineAndIndentedFragment +
                "\n$startingIndent</uri-relative-filter-group>"
            }
            else -> null
          }

        reportUrlError(
          context,
          dataElement,
          context.getLocation(dataElement),
          message,
          if (
            (context.project.buildSdk < VersionCodes.VANILLA_ICE_CREAM) ||
              (context.project.targetSdk < VersionCodes.VANILLA_ICE_CREAM) ||
              fixText == null
          ) {
            null
          } else {
            // This quick-fix copies data elements, so it may be affected by other quick-fixes or
            // generate new problems.
            fix().replace().with(fixText).robot(true).independent(false).build()
          },
        )
      }
    }
    // --- Check for ? in pathPattern and pathAdvancedPattern ---
    // Neither pathPattern nor pathAdvancedPattern supports ? as a regex character:
    // https://developer.android.com/guide/topics/manifest/data-element
    if (
      substitutedValue.contains("?") && name in setOf(ATTR_PATH_PATTERN, ATTR_PATH_ADVANCED_PATTERN)
    ) {
      reportUrlError(
        context,
        attribute,
        context.getValueLocation(attribute),
        "$name does not support `?` as a Regex character",
      )
    }
  }

  private fun suggestRemovingAttributes(
    context: XmlContext,
    intentFilter: Element,
    dataTagInfo: DataTagInfo,
    attrNames: Collection<String>,
    message: String,
    isPermitted: (String) -> Boolean,
  ) {
    for (dataTag in dataTagInfo) {
      val dataElement = (dataTag as? ElementWrapper)?.element ?: continue
      // Quick-fixes to delete everything other than path*, query*, fragment*
      val (permitted, notPermitted) = dataTag.attributes.partition { isPermitted(it.name) }

      if (permitted.isEmpty()) {
        if (notPermitted.isNotEmpty()) {
          context.report(
            URI_RELATIVE_FILTER_GROUP,
            dataElement,
            context.getLocation(dataElement),
            message,
            fix().replace().with("").autoFix().build(),
          )
        }
      } else { // permitted.isNotEmpty()
        if (notPermitted.isNotEmpty()) {
          val namespace = intentFilter.lookupPrefix(ANDROID_URI) ?: ANDROID_NS_NAME
          context.report(
            URI_RELATIVE_FILTER_GROUP,
            dataElement,
            context.getLocation(dataElement),
            message,
            // We need to put () around the whole thing to make it a capturing group;
            // otherwise only the first capturing group gets replaced.
            fix()
              .replace()
              .pattern("""($namespace:(${attrNames.joinToString("|")})=(("[^"]*")|('[^']*')))""")
              .with("")
              .repeatedly(true)
              .reformat(true)
              .autoFix()
              .build(),
          )
        }
      }
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.project.isLibrary) return
    if (partialResults.issue != APP_LINK_SPLIT_TO_WEB_AND_CUSTOM) return

    // Analyze the merged manifest to see which hosts request autoVerify
    val mergedManifestDocumentElement =
      context.mainProject.mergedManifest?.documentElement ?: return

    val hostsThatRequestAutoVerify = mutableSetOf<String>()
    for (node in mergedManifestDocumentElement) {
      if (node.tagName == TAG_APPLICATION) {
        for (applicationChild in node) {
          if (applicationChild.tagName !in setOf(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS)) continue
          // Note: even activities which are not exported or enabled count for domain verification:
          // b/271035636
          for (activityChild in applicationChild) {
            if (activityChild.tagName != TAG_INTENT_FILTER) continue
            val data = getIntentFilterData(ElementWrapper(activityChild, context))
            if (isValidAppLink(data)) {
              hostsThatRequestAutoVerify.addAll(data.dataTags.hostPortPairs.map { it.first })
            }
          }
        }
      }
    }

    for (lintMap in partialResults.maps()) {
      for (incidentIdx in lintMap) {
        val partialResult = lintMap.getMap(incidentIdx) ?: continue
        val hosts =
          partialResult.getString(KEY_SPLIT_TO_WEB_AND_CUSTOM_HOSTS)?.split(",") ?: continue
        if (hosts.any { it !in hostsThatRequestAutoVerify }) {
          val nameLocation =
            partialResult.getLocation(KEY_SPLIT_TO_WEB_AND_CUSTOM_NAME_LOCATION) ?: continue
          val replacementRange =
            partialResult.getLocation(KEY_SPLIT_TO_WEB_AND_CUSTOM_REPLACEMENT_RANGE) ?: continue
          val replacementText =
            partialResult.getString(KEY_SPLIT_TO_WEB_AND_CUSTOM_REPLACEMENT_TEXT) ?: continue
          context.report(
            Incident(context)
              .issue(APP_LINK_SPLIT_TO_WEB_AND_CUSTOM)
              .location(nameLocation)
              .message("Split your `http(s)` and custom schemes into separate intent filters")
              .fix(
                fix()
                  .replace()
                  .with(replacementText.toString())
                  .range(replacementRange)
                  .robot(true)
                  // This quick-fix copies data elements, so it may be affected by other quick-fixes
                  // or generate new problems.
                  .independent(false)
                  .build()
              )
          )
        }
      }
    }
  }

  override fun checkMergedProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(APP_LINK_SPLIT_TO_WEB_AND_CUSTOM))
    }
  }

  companion object {
    internal const val ACTION_VIEW = "android.intent.action.VIEW"
    internal const val CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE"
    internal const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
    // Per documentation
    //   https://developer.android.com/guide/topics/manifest/data-element.html
    // "If the filter has a data type set (the mimeType attribute) but no scheme, the
    //  content: and file: schemes are assumed."
    internal val IMPLICIT_SCHEMES = setOf("file", "content")
    internal val PATH_ATTRIBUTES =
      listOf(
        ATTR_PATH,
        ATTR_PATH_PREFIX,
        ATTR_PATH_PATTERN,
        ATTR_PATH_ADVANCED_PATTERN,
        ATTR_PATH_SUFFIX,
      )
    internal val QUERY_ATTRIBUTES =
      listOf(
        ATTR_QUERY,
        ATTR_QUERY_PREFIX,
        ATTR_QUERY_PATTERN,
        ATTR_QUERY_ADVANCED_PATTERN,
        ATTR_QUERY_SUFFIX,
      )
    internal val FRAGMENT_ATTRIBUTES =
      listOf(
        ATTR_FRAGMENT,
        ATTR_FRAGMENT_PREFIX,
        ATTR_FRAGMENT_PATTERN,
        ATTR_FRAGMENT_ADVANCED_PATTERN,
        ATTR_FRAGMENT_SUFFIX,
      )
    private const val HTTP = "http"
    private const val HTTPS = "https"
    private const val DEFAULT_INDENT_AMOUNT = 4

    private fun indentation(indentAmount: Int) = " ".repeat(indentAmount)

    private fun replaceUrlWithValue(context: XmlContext?, str: String): String {
      if (context == null) {
        return str
      }
      val client = context.client
      val url = ResourceUrl.parse(str)
      if (url == null || url.isFramework) {
        return str
      }
      val project = context.project
      val resources = client.getResources(project, ResourceRepositoryScope.ALL_DEPENDENCIES)
      val items = resources.getResources(ResourceNamespace.TODO(), url.type, url.name)
      if (items.isEmpty()) {
        return str
      }
      val resourceValue = items[0].resourceValue ?: return str
      return if (resourceValue.value == null) str else resourceValue.value!!
    }

    internal fun attrToAndroidPatternMatcher(attr: String): Int {
      return when (attr) {
        ATTR_PATH,
        ATTR_QUERY,
        ATTR_FRAGMENT -> PATTERN_LITERAL
        ATTR_PATH_PREFIX,
        ATTR_QUERY_PREFIX,
        ATTR_FRAGMENT_PREFIX -> PATTERN_PREFIX
        ATTR_PATH_PATTERN,
        ATTR_QUERY_PATTERN,
        ATTR_FRAGMENT_PATTERN -> PATTERN_SIMPLE_GLOB
        ATTR_PATH_ADVANCED_PATTERN,
        ATTR_QUERY_ADVANCED_PATTERN,
        ATTR_FRAGMENT_ADVANCED_PATTERN -> PATTERN_ADVANCED_GLOB
        ATTR_PATH_SUFFIX,
        ATTR_QUERY_SUFFIX,
        ATTR_FRAGMENT_SUFFIX -> PATTERN_SUFFIX
        else ->
          throw AssertionError(
            "Input was required to be one of the <data> path attributes, but was $attr"
          )
      }
    }

    fun androidPatternMatcherToAttr(androidPatternMatcherConstant: Int): String {
      return when (androidPatternMatcherConstant) {
        PATTERN_LITERAL -> ATTR_PATH
        PATTERN_PREFIX -> ATTR_PATH_PREFIX
        PATTERN_SIMPLE_GLOB -> ATTR_PATH_PATTERN
        PATTERN_ADVANCED_GLOB -> ATTR_PATH_ADVANCED_PATTERN
        PATTERN_SUFFIX -> ATTR_PATH_SUFFIX
        else ->
          throw AssertionError(
            "Input was required to be an AndroidPatternMatcher constant but was $androidPatternMatcherConstant"
          )
      }
    }

    fun isWebScheme(scheme: String): Boolean {
      return scheme == HTTP || scheme == HTTPS
    }

    data class Path(val attributeValue: String, val attributeName: String) : Comparable<Path> {
      fun toPatternMatcher(): AndroidPatternMatcher {
        return AndroidPatternMatcher(attributeValue, attrToAndroidPatternMatcher(attributeName))
      }

      override fun compareTo(other: Path): Int {
        if (this.attributeName < other.attributeName) return -1
        return (this.attributeValue.compareTo(other.attributeValue))
      }

      override fun toString(): String {
        // Don't update this, since that would change AppLinksValidDetector's messages.
        return when (attributeName) {
          ATTR_PATH -> "literal "
          ATTR_PATH_PREFIX -> "prefix "
          ATTR_PATH_PATTERN -> "glob "
          ATTR_PATH_SUFFIX -> "suffix "
          ATTR_PATH_ADVANCED_PATTERN -> "advanced "
          else ->
            throw AssertionError(
              "Expected attributeName to be a path attribute but was $attributeName"
            )
        } + attributeValue
      }
    }

    data class Query(val attributeValue: String, val attributeName: String) : Comparable<Query> {
      private val patternTypeString =
        when (attributeName) {
          ATTR_QUERY -> "LITERAL"
          ATTR_QUERY_PREFIX -> "PREFIX"
          ATTR_QUERY_PATTERN -> "GLOB"
          ATTR_QUERY_ADVANCED_PATTERN -> "ADVANCED_GLOB"
          ATTR_QUERY_SUFFIX -> "SUFFIX"
          else -> "UNKNOWN"
        }

      override fun compareTo(other: Query): Int {
        return compareValuesBy(this, other, { it.attributeName }, { it.attributeValue })
      }

      fun match(queryString: String): Boolean {
        // See
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/UriRelativeFilter.java;l=150;drc=36303b838229bcd21b0d72278dd6879497bc285b
        val patternMatcher =
          AndroidPatternMatcher(attributeValue, attrToAndroidPatternMatcher(attributeName))
        var paramsToMatch = queryString.split("&amp;", "&")
        if (paramsToMatch.size == 1) paramsToMatch = queryString.split(';')
        return paramsToMatch.any { patternMatcher.match(URLDecoder.decode(it, Charsets.UTF_8)) }
      }

      // See
      // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/UriRelativeFilter.java;l=212;drc=53969265f52c07741c3dba5365be231ce3c55817
      override fun toString(): String {
        return "UriRelativeFilter { uriPart = QUERY, patternType = $patternTypeString, filter = $attributeValue }"
      }
    }

    data class Fragment(val attributeValue: String, val attributeName: String) :
      Comparable<Fragment> {
      private val patternTypeString =
        when (attributeName) {
          ATTR_FRAGMENT -> "LITERAL"
          ATTR_FRAGMENT_PREFIX -> "PREFIX"
          ATTR_FRAGMENT_PATTERN -> "GLOB"
          ATTR_FRAGMENT_ADVANCED_PATTERN -> "ADVANCED_GLOB"
          ATTR_FRAGMENT_SUFFIX -> "SUFFIX"
          else -> "UNKNOWN"
        }

      override fun compareTo(other: Fragment): Int {
        return compareValuesBy(this, other, { it.attributeName }, { it.attributeValue })
      }

      fun match(fragmentString: String): Boolean {
        return AndroidPatternMatcher(attributeValue, attrToAndroidPatternMatcher(attributeName))
          .match(URLDecoder.decode(fragmentString, Charsets.UTF_8))
      }

      // See
      // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/UriRelativeFilter.java;l=212;drc=53969265f52c07741c3dba5365be231ce3c55817
      override fun toString(): String {
        return "UriRelativeFilter { uriPart = FRAGMENT, patternType = $patternTypeString, filter = $attributeValue }"
      }
    }

    interface TagWrapper {
      val name: String
      val subTags: Iterable<TagWrapper>
      val attributes: Iterable<AttributeWrapper>

      /** Get the [AttributeWrapper] containing an attribute node (name=value). */
      fun getAttributeWrapper(attrName: String): AttributeWrapper?
    }

    interface AttributeWrapper {
      /** The attribute name. */
      val name: String

      /** The attribute value without applying string substitutions. */
      val rawValue: String?

      /** The attribute value after applying string substitutions. */
      val substitutedValue: String?
    }

    class ElementWrapper(val element: Element, private val context: Context) : TagWrapper {
      override val name: String = element.tagName

      // Use asSequence for performance improvement
      override val subTags = XmlUtils.getSubTags(element).map { ElementWrapper(it, context) }

      override val attributes =
        (0 until element.attributes.length).mapNotNull { idx ->
          (element.attributes.item(idx) as? Attr)?.let { AttrWrapper(it, context) }
        }

      override fun getAttributeWrapper(attrName: String): AttrWrapper? =
        element.getAttributeNodeNS(ANDROID_URI, attrName)?.let { AttrWrapper(it, context) }
    }

    class AttrWrapper(val attr: Attr, private val context: Context) : AttributeWrapper {
      override val name: String =
        attr.localName /* Exclude the namespace prefix */ ?: attr.name ?: ""

      override val rawValue: String? by
        lazy(LazyThreadSafetyMode.NONE) { attr.value?.let { xmlEscape(it) } }

      override val substitutedValue: String? by
        lazy(LazyThreadSafetyMode.NONE) {
          val attrValue = attr.value ?: return@lazy null
          val value = xmlEscape(attrValue)
          // If we're not in an XML Context, just return the fallback.
          // The most common scenario for this is that we're looking at the merged manifest.
          if (context !is XmlContext) return@lazy value
          if (value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF)) {
            return@lazy replaceUrlWithValue(context, value)
          }
          // Return `value` as a fallback so we still return something when resolution fails
          return@lazy resolvePlaceHolders(context.project, value) ?: value
        }
    }

    data class IntentFilterData(
      val autoVerify: String?,
      val order: Int,
      val priority: Int,
      val actions: List<AttributeWrapper>,
      val categories: List<AttributeWrapper>,
      val dataTags: DataTagInfo,
      // uri-relative-filter-groups need to be a list because they're evaluated in declaration order
      val uriRelativeFilterGroups: List<UriRelativeFilterGroup>,
    ) {
      val actionSet by
        lazy(LazyThreadSafetyMode.NONE) { actions.mapTo(mutableSetOf()) { it.substitutedValue } }
      val categorySet by
        lazy(LazyThreadSafetyMode.NONE) { categories.mapTo(mutableSetOf()) { it.substitutedValue } }

      /**
       * Matches a URL against this info, and returns null if successful or the failure reason if
       * not a match
       *
       * @param testUrl the URL to match
       * @return null for a successful match or the failure reason
       */
      fun match(testUrl: URL): String? {
        val schemesIncludingImplicitSchemes =
          when {
            dataTags.schemes.isEmpty() && dataTags.mimeTypes.isNotEmpty() -> IMPLICIT_SCHEMES
            else -> dataTags.schemes
          }
        val schemeOk =
          schemesIncludingImplicitSchemes.any { scheme: String ->
            scheme == testUrl.protocol || isSubstituted(scheme)
          }
        if (!schemeOk) {
          return "did not match scheme ${Joiner.on(", ").join(dataTags.schemes)}"
        }
        if (dataTags.hostPortPairs.isNotEmpty()) {
          val hostOk =
            dataTags.hostPortPairs.any { (host, port) ->
              host.let { matchesHost(testUrl.host, it) || isSubstituted(it) } &&
                (port?.let { testUrl.port.toString() == port || isSubstituted(port) } != false)
            }
          if (!hostOk) {
            return "did not match${if (dataTags.hostPortPairs.size > 1) " any of" else ""} ${
              dataTags.hostPortPairs.joinToString(", ") { (host, port) -> if (port != null) "host+port $host:$port" else "host $host" }
            }"
          }
        }
        if (dataTags.paths.isNotEmpty() || uriRelativeFilterGroups.isNotEmpty()) {
          val testPath = testUrl.path

          // First, check to see whether any paths specified in the intent-filter's direct <data>
          // children match the URI.
          // This is evaluated before any URI relative filter group is evaluated.
          val pathMatchFound =
            dataTags.paths.any {
              isSubstituted(it.attributeValue) || it.toPatternMatcher().match(testPath)
            }
          if (pathMatchFound) return null // OK

          // uri-relative-filter-groups are evaluated in order, so we find the first matching one
          val matchedUriRelativeFilter = uriRelativeFilterGroups.firstOrNull { it.match(testUrl) }
          // If there was a match,
          if (matchedUriRelativeFilter != null) {
            return if (matchedUriRelativeFilter.allow) {
              // If the rule was an allow rule, the testUrl matches, so everything is OK
              null
            } else {
              // If the rule was an exclusion rule, the testUrl will match it and be excluded, so we
              // report an error
              return "matched exclusion rule $matchedUriRelativeFilter"
            }
          }

          val sb = StringBuilder()
          dataTags.paths.forEach { sb.append("path ").append(it).append(", ") }
          uriRelativeFilterGroups.forEach { sb.append(it).append(", ") }
          if (sb.endsWith(", ")) {
            sb.setLength(sb.length - 2)
          }
          var message = "did not match $sb"
          if (
            dataTags.paths.any {
              !isSubstituted(it.attributeValue) && it.attributeValue.any(::isUpperCase)
            } || uriRelativeFilterGroups.any { it.hasUppercase } || testPath.any(::isUpperCase)
          ) {
            message += " Note that matching is case sensitive."
          }
          return message
        }
        return null // OK
      }

      /**
       * Check whether a given host matches the hostRegex. The hostRegex could be a regular host
       * name, or it could contain only one '*', such as *.example.com, where '*' matches any string
       * whose length is at least 1.
       *
       * @param actualHost The actual host we want to check.
       * @param hostPattern The criteria host, which could contain a '*'.
       * @return Whether the actualHost matches the hostRegex
       */
      private fun matchesHost(actualHost: String, hostPattern: String): Boolean {
        // Per https://developer.android.com/guide/topics/manifest/data-element.html
        // the asterisk must be the first character
        return if (!hostPattern.startsWith("*")) {
          actualHost == hostPattern
        } else
          try {
            val pattern = Regex(".*${Pattern.quote(hostPattern.substring(1))}")
            actualHost.matches(pattern)
          } catch (_: Throwable) {
            // Make sure we don't fail to compile the regex, though with the quote call
            // above this really shouldn't happen
            false
          }
      }
    }

    /** Information collected from all data tags within the parent. */
    data class DataTagInfo(val dataTagElements: List<TagWrapper>) : Iterable<TagWrapper> {
      val isEmpty = dataTagElements.isEmpty()
      val schemeElements by
        lazy(LazyThreadSafetyMode.NONE) {
          dataTagElements.mapNotNull { it.getAttributeWrapper(ATTR_SCHEME) }
        }
      val schemes by
        lazy(LazyThreadSafetyMode.NONE) {
          schemeElements.mapNotNullTo(mutableSetOf()) { it.substitutedValue }
        }
      val rawSchemes by
        lazy(LazyThreadSafetyMode.NONE) {
          schemeElements.mapNotNullTo(mutableSetOf()) { it.rawValue }
        }
      val hostElements by
        lazy(LazyThreadSafetyMode.NONE) {
          dataTagElements.mapNotNull { it.getAttributeWrapper(ATTR_HOST) }
        }
      val portElements by
        lazy(LazyThreadSafetyMode.NONE) {
          dataTagElements.mapNotNull { it.getAttributeWrapper(ATTR_PORT) }
        }
      val hostPortPairs by
        lazy(LazyThreadSafetyMode.NONE) {
          dataTagElements.mapNotNullTo(mutableSetOf()) {
            val host =
              it.getAttributeWrapper(ATTR_HOST)?.substitutedValue ?: return@mapNotNullTo null
            Pair(host, it.getAttributeWrapper(ATTR_PORT)?.substitutedValue)
          }
        }
      val rawHostPortPairs by
        lazy(LazyThreadSafetyMode.NONE) {
          dataTagElements.mapNotNull {
            val host = it.getAttributeWrapper(ATTR_HOST)?.rawValue ?: return@mapNotNull null
            Pair(host, it.getAttributeWrapper(ATTR_PORT)?.rawValue)
          }
        }
      val pathElements by
        lazy(LazyThreadSafetyMode.NONE) {
          val result = mutableSetOf<AttributeWrapper>()
          for (subTag in dataTagElements) {
            for (value in PATH_ATTRIBUTES) {
              subTag.getAttributeWrapper(value)?.let { result.add(it) }
            }
          }
          result
        }
      val paths by
        lazy(LazyThreadSafetyMode.NONE) {
          pathElements.mapNotNullTo(mutableSetOf()) { attr ->
            attr.substitutedValue?.let { Path(attributeValue = it, attributeName = attr.name) }
          }
        }
      val rawPaths by
        lazy(LazyThreadSafetyMode.NONE) {
          pathElements.mapNotNullTo(mutableSetOf()) { attr ->
            attr.rawValue?.let { Path(attributeValue = it, attributeName = attr.name) }
          }
        }
      val mimeTypeElements by
        lazy(LazyThreadSafetyMode.NONE) {
          dataTagElements.mapNotNull { it.getAttributeWrapper(ATTR_MIME_TYPE) }
        }
      val mimeTypes by
        lazy(LazyThreadSafetyMode.NONE) {
          mimeTypeElements.mapNotNullTo(mutableSetOf()) { it.substitutedValue }
        }
      val rawMimeTypes by
        lazy(LazyThreadSafetyMode.NONE) {
          mimeTypeElements.mapNotNullTo(mutableSetOf()) { it.rawValue }
        }

      override fun iterator(): Iterator<TagWrapper> = dataTagElements.iterator()
    }

    data class UriRelativeFilterGroup(private val tagWrapper: TagWrapper) {
      val allow: Boolean by
        lazy(LazyThreadSafetyMode.NONE) {
          tagWrapper.getAttributeWrapper(ATTR_ALLOW)?.substitutedValue.let {
            it == null || it == VALUE_TRUE
          }
        }
      val dataTagInfo: DataTagInfo by
        lazy(LazyThreadSafetyMode.NONE) {
          DataTagInfo(tagWrapper.subTags.filter { it.name == TAG_DATA })
        }
      val queries: Set<Query> by
        lazy(LazyThreadSafetyMode.NONE) {
          queryElements.mapNotNullTo(mutableSetOf()) { attr ->
            attr.substitutedValue?.let { Query(attributeValue = it, attributeName = attr.name) }
          }
        }
      val rawQueries: Set<Query> by
        lazy(LazyThreadSafetyMode.NONE) {
          queryElements.mapNotNullTo(mutableSetOf()) { attr ->
            attr.rawValue?.let { Query(attributeValue = it, attributeName = attr.name) }
          }
        }
      val queryElements: Set<AttributeWrapper> by
        lazy(LazyThreadSafetyMode.NONE) {
          val result = mutableSetOf<AttributeWrapper>()
          for (subTag in tagWrapper.subTags.filter { it.name == TAG_DATA }) {
            for (value in QUERY_ATTRIBUTES) {
              subTag.getAttributeWrapper(value)?.let { result.add(it) }
            }
          }
          result
        }
      val fragments: Set<Fragment> by
        lazy(LazyThreadSafetyMode.NONE) {
          fragmentElements.mapNotNullTo(mutableSetOf()) { attr ->
            attr.substitutedValue?.let { Fragment(attributeValue = it, attributeName = attr.name) }
          }
        }
      val rawFragments: Set<Fragment> by
        lazy(LazyThreadSafetyMode.NONE) {
          fragmentElements.mapNotNullTo(mutableSetOf()) { attr ->
            attr.rawValue?.let { Fragment(attributeValue = it, attributeName = attr.name) }
          }
        }
      val fragmentElements: Set<AttributeWrapper> by
        lazy(LazyThreadSafetyMode.NONE) {
          val result = mutableSetOf<AttributeWrapper>()
          for (subTag in tagWrapper.subTags.filter { it.name == TAG_DATA }) {
            for (value in FRAGMENT_ATTRIBUTES) {
              subTag.getAttributeWrapper(value)?.let { result.add(it) }
            }
          }
          result
        }
      val hasUppercase by
        lazy(LazyThreadSafetyMode.NONE) {
          dataTagInfo.paths.any {
            !isSubstituted(it.attributeValue) && it.attributeValue.any(::isUpperCase)
          } ||
            queries.any {
              !isSubstituted(it.attributeValue) && it.attributeValue.any(::isUpperCase)
            } ||
            fragments.any {
              !isSubstituted(it.attributeValue) && it.attributeValue.any(::isUpperCase)
            }
        }

      /** Returns whether this matches the path + query + fragment part of [url]. */
      fun match(url: URL): Boolean {
        val pathOk =
          dataTagInfo.paths.all {
            isSubstituted(it.attributeValue) ||
              (url.path != null && it.toPatternMatcher().match(url.path))
          }
        if (!pathOk) return false
        val queryOk =
          queries.all {
            isSubstituted(it.attributeValue) || (url.query != null && it.match(url.query))
          }
        if (!queryOk) return false
        val fragmentOk =
          fragments.all {
            isSubstituted(it.attributeValue) || (url.ref != null && it.match(url.ref))
          }
        return fragmentOk
      }

      // See
      // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/UriRelativeFilterGroup.java;l=225;drc=53969265f52c07741c3dba5365be231ce3c55817
      override fun toString(): String {
        val sb =
          StringBuilder("UriRelativeFilterGroup { allow = ")
            .append(allow)
            .append(", uri_filters = ")
        dataTagInfo.paths.forEach { sb.append(it).append(", ") }
        queries.forEach { sb.append(it).append(", ") }
        fragments.forEach { sb.append(it).append(", ") }
        if (sb.endsWith(", ")) sb.setLength(sb.length - 2)
        sb.append(" }")
        return sb.toString()
      }
    }

    fun getIntentFilterData(intentFilter: TagWrapper): IntentFilterData {
      val autoVerify = intentFilter.getAttributeWrapper(ATTR_AUTO_VERIFY)?.substitutedValue
      val order =
        intentFilter
          .getAttributeWrapper(ATTR_ORDER)
          ?.substitutedValue
          ?.ifEmpty { VALUE_0 }
          ?.toIntOrNull() ?: 0
      val priority =
        intentFilter
          .getAttributeWrapper(ATTR_PRIORITY)
          ?.substitutedValue
          ?.ifEmpty { VALUE_0 }
          ?.toIntOrNull() ?: 0
      val actions = mutableListOf<AttributeWrapper>()
      val categories = mutableListOf<AttributeWrapper>()
      val dataTagElements = mutableListOf<TagWrapper>()
      val uriRelativeFilterGroups = mutableListOf<UriRelativeFilterGroup>()
      for (subTag in intentFilter.subTags) {
        when (subTag.name) {
          TAG_ACTION -> subTag.getAttributeWrapper(ATTRIBUTE_NAME)?.let { actions.add(it) }
          TAG_CATEGORY -> subTag.getAttributeWrapper(ATTRIBUTE_NAME)?.let { categories.add(it) }
          TAG_DATA -> dataTagElements.add(subTag)
          TAG_URI_RELATIVE_FILTER_GROUP ->
            uriRelativeFilterGroups.add(UriRelativeFilterGroup(subTag))
        }
      }
      return IntentFilterData(
        autoVerify,
        order,
        priority,
        actions,
        categories,
        DataTagInfo(dataTagElements),
        uriRelativeFilterGroups,
      )
    }

    fun hasAutoVerifyButInvalidAppLink(intentFilter: TagWrapper): Boolean {
      return hasAutoVerifyButInvalidAppLink(getIntentFilterData(intentFilter))
    }

    fun hasAutoVerifyButInvalidAppLink(data: IntentFilterData): Boolean {
      return data.autoVerify == VALUE_TRUE &&
        (!hasElementsRequiredForAppLinks(data) || !hasNoElementsForbiddenForAppLinks(data))
    }

    private fun hasElementsRequiredForAppLinks(data: IntentFilterData): Boolean {
      return data.actionSet.contains(ACTION_VIEW) &&
        data.categorySet.contains(CATEGORY_DEFAULT) &&
        data.categorySet.contains(CATEGORY_BROWSABLE) &&
        data.dataTags.schemes.any { isSubstituted(it) || isWebScheme(it) } &&
        data.dataTags.hostPortPairs.isNotEmpty()
    }

    private fun hasNoElementsForbiddenForAppLinks(data: IntentFilterData): Boolean {
      return data.dataTags.schemes.all { isSubstituted(it) || isWebScheme(it) } &&
        data.dataTags.mimeTypes.isEmpty()
    }

    private fun isValidAppLink(data: IntentFilterData): Boolean {
      return data.autoVerify == VALUE_TRUE &&
        hasElementsRequiredForAppLinks(data) &&
        hasNoElementsForbiddenForAppLinks(data)
    }

    private fun concatenateWithIndent(
      inputs: List<String>,
      newLineAndIndentString: String,
    ): String {
      return when {
        inputs.isEmpty() -> ""
        else -> inputs.joinToString(newLineAndIndentString, prefix = newLineAndIndentString)
      }
    }

    private fun copyTagWithAttributes(element: Element, sb: StringBuilder = StringBuilder()) {
      sb.append(element.tagName)
      for (i in 0 until element.attributes.length) {
        val item = element.attributes.item(i)
        sb.append(" ")
        sb.append(item.nodeName)
        sb.append("=\"")
        sb.append(item.nodeValue)
        sb.append('"')
      }
    }

    private fun recursivelyCopy(
      element: Element,
      startingIndentAmount: Int,
      indentDiff: Int,
      sb: StringBuilder = StringBuilder(),
    ) {
      sb.append("<")
      copyTagWithAttributes(element, sb)
      if (element.subtagCount() == 0) {
        sb.append(" />")
        return
      }
      sb.append(">")
      for (subTag in element) {
        sb.append("\n")
        for (i in 0 until (startingIndentAmount + indentDiff)) {
          sb.append(" ")
        }
        recursivelyCopy(subTag, startingIndentAmount + indentDiff, indentDiff, sb)
      }
      sb.append("\n")
      for (i in 0 until startingIndentAmount) {
        sb.append(" ")
      }
      sb.append("</${element.tagName}>")
    }

    /** @return an XML-escaped version of [s]. */
    private fun xmlEscape(s: String): String {
      // Decoding and re-encoding allows s to have XML escape sequences in it.
      // e.g. a&amp;b -> a&b -> a&amp;b, not a&amp;b -> a&amp;amp;b
      return toXmlAttributeValue(fromXmlAttributeValue(s))
    }

    private val IMPLEMENTATION =
      Implementation(AppLinksValidDetector::class.java, Scope.MANIFEST_SCOPE)

    @JvmField
    val TEST_URL =
      Issue.create(
        id = "TestAppLink",
        briefDescription = "Unmatched URLs",
        explanation =
          """
                Using one or more `tools:validation testUrl="some url"/>` elements in your manifest allows \
                the link attributes in your intent filter to be checked for matches.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val VALIDATION =
      Issue.create(
          id = "AppLinkUrlError",
          briefDescription = "URI invalid",
          explanation =
            """
                Ensure your intent filter has the documented elements for deep links, web links, or Android \
                App Links.""",
          category = Category.CORRECTNESS,
          priority = 5,
          moreInfo = "https://developer.android.com/training/app-links",
          severity = Severity.ERROR,
          implementation = IMPLEMENTATION,
        )
        .addMoreInfo("https://g.co/AppIndexing/AndroidStudio")

    @JvmField
    val URI_RELATIVE_FILTER_GROUP =
      Issue.create(
        id = "AppLinkUriRelativeFilterGroupError",
        briefDescription = "URI relative filter group invalid",
        explanation = """Ensure that your URI relative filter group is correctly configured.""",
        category = Category.CORRECTNESS,
        priority = 5,
        moreInfo =
          "https://developer.android.com/guide/topics/manifest/uri-relative-filter-group-element?utm_source=lint",
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val APP_LINK_WARNING =
      Issue.create(
          id = "AppLinkWarning",
          briefDescription = "App Link warning",
          explanation =
            """From Android 12, intent filters that use the HTTP and HTTPS schemes will no longer \
              bring the user to your app when the user clicks a link, unless the intent filter is \
              an Android App Link. Such intent filters must include certain elements, and at least \
              one Android App Link for each domain must have `android:autoVerify="true"` to verify \
              ownership of the domain. We recommend adding `android:autoVerify="true"` to any intent \
              filter that is intended to be an App Link, in case the other App Links are modified.""",
          category = Category.CORRECTNESS,
          priority = 5,
          moreInfo = "https://developer.android.com/training/app-links",
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
        )
        .addMoreInfo("https://g.co/AppIndexing/AndroidStudio")

    /**
     * Only used for compatibility issue lookup (the driver suppression check takes an issue, not an
     * id)
     */
    private val _OLD_ISSUE_URL =
      Issue.create(
        id = "GoogleAppIndexingUrlError",
        briefDescription = "?",
        explanation = "?",
        category = Category.USABILITY,
        priority = 5,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** Multiple attributes in an intent filter data declaration */
    @JvmField
    @Suppress("LintImplUnexpectedDomain")
    val INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES =
      Issue.create(
        id = "IntentFilterUniqueDataAttributes",
        briefDescription = "Data tags should only declare unique attributes",
        explanation =
          """
                `<intent-filter>` `<data>` tags should only declare a single unique attribute \
                (i.e. scheme OR host, but not both). This better matches the runtime behavior of \
                intent filters, as they combine all of the declared data attributes into a single \
                matcher which is allowed to handle any combination across attribute types.

                For example, the following two `<intent-filter>` declarations are the same:
                ```xml
                <intent-filter>
                    <data android:scheme="http" android:host="example.com" />
                    <data android:scheme="https" android:host="example.org" />
                </intent-filter>
                ```

                ```xml
                <intent-filter>
                    <data android:scheme="http"/>
                    <data android:scheme="https"/>
                    <data android:host="example.com" />
                    <data android:host="example.org" />
                </intent-filter>
                ```

                They both handle all of the following:
                * http://example.com
                * https://example.com
                * http://example.org
                * https://example.org

                The second one better communicates the combining behavior and is clearer to an \
                external reader that one should not rely on the scheme/host being self contained. \
                It is not obvious in the first that http://example.org is also matched, which can \
                lead to confusion (or incorrect behavior) with a more complex set of schemes/hosts.

                Note that this does not apply to host + port, as those must be declared in the same \
                `<data>` tag and are only associated with each other.
                """,
        category = Category.CORRECTNESS,
        severity = Severity.WARNING,
        moreInfo = "https://developer.android.com/guide/components/intents-filters",
        implementation = IMPLEMENTATION,
      )

    /**
     * Intent filter with autoVerify uses both web and custom schemes, and there is at least one
     * host where no other intent filters trigger domain verification (i.e. there are no other
     * intent filters with autoVerify, only web schemes, and the host).
     */
    @JvmField
    val APP_LINK_SPLIT_TO_WEB_AND_CUSTOM =
      Issue.create(
        id = "AppLinkSplitToWebAndCustom",
        briefDescription = "Android App links should only use http(s) schemes",
        explanation =
          """
          In order for Android App Links to open in your app, Android must perform domain \
          verification. However, Android only sends domain verification requests for \
          `<intent-filter>`s that only contain http(s) schemes.

          To ensure correct behavior, please split your http(s) schemes and other schemes \
          into two different `<intent-filter>`s.
        """,
        category = Category.CORRECTNESS,
        severity = Severity.ERROR,
        moreInfo =
          "https://developer.android.com/training/app-links/verify-android-applinks#add-intent-filters",
        implementation = IMPLEMENTATION,
      )

    private const val TAG_VALIDATION = "validation"

    private const val KEY_SPLIT_TO_WEB_AND_CUSTOM_NAME_LOCATION =
      "SPLIT_TO_WEB_AND_CUSTOM_NAME_LOCATION"
    private const val KEY_SPLIT_TO_WEB_AND_CUSTOM_REPLACEMENT_RANGE =
      "SPLIT_TO_WEB_AND_CUSTOM_RANGE"
    private const val KEY_SPLIT_TO_WEB_AND_CUSTOM_REPLACEMENT_TEXT =
      "SPLIT_TO_WEB_AND_CUSTOM_REPLACEMENT_TEXT"
    private const val KEY_SPLIT_TO_WEB_AND_CUSTOM_HOSTS = "SPLIT_TO_WEB_AND_CUSTOM_HOSTS"
    const val KEY_SHOW_APP_LINKS_ASSISTANT = "SHOW_APP_LINKS_ASSISTANT"

    // Path matchers use the order used in
    // https://developer.android.com/guide/topics/manifest/data-element
    // <scheme>://<host>:<port>[<path>|<pathPrefix>|<pathPattern>|<pathSuffix>|<pathAdvancedPattern>]
    private val INTENT_FILTER_DATA_SORT_REFERENCE =
      listOf(
        ATTR_SCHEME,
        ATTR_HOST,
        ATTR_PORT,
        ATTR_PATH,
        ATTR_PATH_PREFIX,
        ATTR_PATH_PATTERN,
        ATTR_PATH_SUFFIX,
        ATTR_PATH_ADVANCED_PATTERN,
        ATTR_MIME_TYPE,
      )

    private fun isSubstituted(expression: String): Boolean {
      return isDataBindingExpression(expression) || isManifestPlaceHolderExpression(expression)
    }
  }

  override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    if (issue == VALIDATION && old == "Missing URL" && new == "VIEW actions require a URI")
      return true // See commit 406811b
    if (
      issue == VALIDATION &&
        old == "Missing required elements/attributes for Android App Links" &&
        new.matches(
          Regex(
            "(.* is missing, but is required for Android App Links)|" +
              "(.* and .* are missing, but are required for Android App Links)|" +
              "(Several elements/attributes \\(such as .*\\) required for Android App Links are missing)"
          )
        )
    )
      return true
    return super.sameMessage(issue, new, old)
  }
}
