/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.LintClient.Companion.isUnitTest
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.xml.AndroidManifest
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.text.Charsets.UTF_8
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Check if the App Link which needs auto verification is correctly set. */
class AppLinksAutoVerifyDetector : Detector(), XmlScanner {
  /* Maps website host url to a future task which will send HTTP request to fetch the JSON file
   * and also return the status code during the fetching process. */
  private val mFutures: MutableMap<String, Future<HttpResult>> = Maps.newHashMap()

  /* Maps website host url to host attribute in AndroidManifest.xml. */
  private val mJsonHost: MutableMap<String, Attr> = Maps.newHashMap()

  override fun visitDocument(context: XmlContext, document: Document) {
    // This check sends http request. Only done in batch mode.

    if (!context.scope.contains(Scope.ALL_JAVA_FILES)) {
      return
    }

    if (document.documentElement != null) {
      val intents = getTags(document.documentElement, AndroidManifest.NODE_INTENT)
      if (!needAutoVerification(intents)) {
        return
      }

      for (intent in intents) {
        val actionView =
          hasNamedSubTag(intent, AndroidManifest.NODE_ACTION, "android.intent.action.VIEW")
        val browsableCategory =
          hasNamedSubTag(intent, AndroidManifest.NODE_CATEGORY, "android.intent.category.BROWSABLE")
        if (!actionView || !browsableCategory) {
          continue
        }
        mJsonHost.putAll(getJsonUrl(context, intent))
      }
    }

    val results = getJsonFileAsync(context.client)

    val packageName = context.project.getPackage()
    for ((key, value) in results) {
      if (value == null) {
        continue
      }
      val host = mJsonHost[key] ?: continue
      val jsonPath = key + JSON_RELATIVE_PATH
      when (value.mStatus) {
        HttpURLConnection.HTTP_OK -> {
          val packageNames = getPackageNameFromJson(value.mJsonFile)
          if (!packageNames.contains(packageName)) {
            reportError(
              context,
              host,
              context.getLocation(host),
              String.format(
                "This host does not support app links to your app. Checks the Digital Asset Links JSON file: %s",
                jsonPath,
              ),
            )
          }
        }
        STATUS_HTTP_CONNECT_FAIL ->
          reportWarning(
            context,
            host,
            context.getLocation(host),
            String.format("Connection to Digital Asset Links JSON file %s fails", jsonPath),
          )
        STATUS_MALFORMED_URL ->
          reportError(
            context,
            host,
            context.getLocation(host),
            String.format(
              "Malformed URL of Digital Asset Links JSON file: %s. An unknown protocol is specified",
              jsonPath,
            ),
          )
        STATUS_UNKNOWN_HOST ->
          reportWarning(
            context,
            host,
            context.getLocation(host),
            String.format(
              "Unknown host: %s. Check if the host exists, and check your network connection",
              key,
            ),
          )
        STATUS_NOT_FOUND ->
          reportError(
            context,
            host,
            context.getLocation(host),
            String.format("Digital Asset Links JSON file %s is not found on the host", jsonPath),
          )
        STATUS_WRONG_JSON_SYNTAX ->
          reportError(
            context,
            host,
            context.getLocation(host),
            String.format("%s has incorrect JSON syntax", jsonPath),
          )
        STATUS_JSON_PARSE_FAIL ->
          reportError(
            context,
            host,
            context.getLocation(host),
            String.format("Parsing JSON file %s fails", jsonPath),
          )
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP -> {}
        else ->
          reportWarning(
            context,
            host,
            context.getLocation(host),
            String.format(
              "HTTP request for Digital Asset Links JSON file %1\$s fails. HTTP response code: %2\$s",
              jsonPath,
              value.mStatus,
            ),
          )
      }
    }
  }

  private fun reportWarning(context: XmlContext, node: Node, location: Location, message: String) {
    val incident = Incident(ISSUE, node, location, message)
    incident.overrideSeverity(Severity.WARNING)
    context.report(incident)
  }

  private fun reportError(context: XmlContext, node: Node, location: Location, message: String) {
    val incident = Incident(ISSUE, node, location, message)
    incident.overrideSeverity(Severity.ERROR)
    context.report(incident)
  }

  /**
   * Gets all the Digital Asset Links JSON file asynchronously.
   *
   * @return The map between the host url and the HTTP result.
   */
  private fun getJsonFileAsync(client: LintClient): Map<String, HttpResult?> {
    val executorService = Executors.newCachedThreadPool()
    for ((key) in mJsonHost) {
      val future =
        executorService.submit<HttpResult> { getJson(client, key + JSON_RELATIVE_PATH, 0) }
      mFutures[key] = future
    }
    executorService.shutdown()

    val jsons: MutableMap<String, HttpResult?> = Maps.newHashMap()
    for ((key, value) in mFutures) {
      try {
        jsons[key] = value.get()
      } catch (e: Exception) {
        if (isUnitTest) {
          val cause = e.cause
          if (cause is Error) {
            throw (cause as Error?)!!
          } else {
            throw IllegalArgumentException("Network failure", cause)
          }
        }
        jsons[key] = null
      }
    }
    return jsons
  }

  /* For storing the result of getting Digital Asset Links Json File */
  @VisibleForTesting
  internal class HttpResult
  @VisibleForTesting
  constructor(
    /* HTTP response code or others errors related to HTTP connection, JSON file parsing. */
    val mStatus: Int,
    val mJsonFile: JsonElement?,
  )

  companion object {
    private val IMPLEMENTATION =
      Implementation(AppLinksAutoVerifyDetector::class.java, Scope.MANIFEST_SCOPE)

    @JvmField
    val ISSUE: Issue =
      create(
          "AppLinksAutoVerify",
          "App Links Auto Verification Failure",
          "Ensures that app links are correctly set and associated with website.",
          Category.CORRECTNESS,
          5,
          Severity.ERROR,
          IMPLEMENTATION,
        )
        .addMoreInfo("https://g.co/appindexing/applinks")
        .setAliases(mutableListOf("AppLinksAutoVerifyError", "AppLinksAutoVerifyWarning"))
        .setEnabledByDefault(false)

    private const val ATTRIBUTE_AUTO_VERIFY = "autoVerify"
    private const val JSON_RELATIVE_PATH = "/.well-known/assetlinks.json"

    @VisibleForTesting val STATUS_HTTP_CONNECT_FAIL: Int = -1

    @VisibleForTesting val STATUS_MALFORMED_URL: Int = -2

    @VisibleForTesting val STATUS_UNKNOWN_HOST: Int = -3

    @VisibleForTesting val STATUS_NOT_FOUND: Int = -4

    @VisibleForTesting val STATUS_WRONG_JSON_SYNTAX: Int = -5

    @VisibleForTesting val STATUS_JSON_PARSE_FAIL: Int = -6

    /**
     * Gets all the tag elements with a specific tag name, within a parent tag element.
     *
     * @param element The parent tag element.
     * @return List of tag elements found.
     */
    private fun getTags(element: Element, tagName: String): List<Element> {
      val tagList: MutableList<Element> = Lists.newArrayList()
      if (element.tagName.equals(tagName, ignoreCase = true)) {
        tagList.add(element)
      } else {
        val children = element.childNodes
        for (i in 0 until children.length) {
          val child = children.item(i)
          if (child is Element) {
            tagList.addAll(getTags(child, tagName))
          }
        }
      }
      return tagList
    }

    /**
     * Checks if auto verification is needed. i.e. any intent tag element's autoVerify attribute is
     * set to true.
     *
     * @param intents The intent tag elements.
     * @return true if auto verification is needed.
     */
    private fun needAutoVerification(intents: List<Element>): Boolean {
      for (intent in intents) {
        if (
          intent.getAttributeNS(SdkConstants.ANDROID_URI, ATTRIBUTE_AUTO_VERIFY) ==
            SdkConstants.VALUE_TRUE
        ) {
          return true
        }
      }
      return false
    }

    /**
     * Checks if the element has a sub tag with specific name and specific name attribute.
     *
     * @param element The tag element.
     * @param tagName The name of the sub tag.
     * @param nameAttrValue The value of the name attribute.
     * @return If the element has such a sub tag.
     */
    private fun hasNamedSubTag(element: Element, tagName: String, nameAttrValue: String): Boolean {
      val children = element.getElementsByTagName(tagName)
      for (i in 0 until children.length) {
        val e = children.item(i) as Element
        if (
          e.getAttributeNS(SdkConstants.ANDROID_URI, AndroidManifest.ATTRIBUTE_NAME) ==
            nameAttrValue
        ) {
          return true
        }
      }
      return false
    }

    /**
     * Gets the urls of all the host from which Digital Asset Links JSON files will be fetched.
     *
     * @param intent The intent tag element.
     * @return List of JSON file urls.
     */
    private fun getJsonUrl(context: XmlContext, intent: Element): Map<String, Attr> {
      val schemes: MutableList<String> = Lists.newArrayList()
      val hosts: MutableList<Attr> = Lists.newArrayList()
      val dataTags = intent.getElementsByTagName(AndroidManifest.NODE_DATA)
      for (k in 0 until dataTags.length) {
        val dataTag = dataTags.item(k) as Element
        val scheme = dataTag.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SCHEME)
        if (scheme == "http" || scheme == "https") {
          schemes.add(scheme)
        }
        if (dataTag.hasAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_HOST)) {
          val host = dataTag.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_HOST)
          hosts.add(host)
        }
      }
      val urls: MutableMap<String, Attr> = Maps.newHashMap()
      for (scheme in schemes) {
        for (host in hosts) {
          var hostname = host.value
          if (hostname!!.startsWith(SdkConstants.MANIFEST_PLACEHOLDER_PREFIX)) {
            hostname = resolvePlaceHolder(context, hostname)
            if (hostname == null) {
              continue
            }
          }
          urls["$scheme://$hostname"] = host
        }
      }
      return urls
    }

    private fun resolvePlaceHolder(context: XmlContext, hostname: String): String? {
      assert(hostname.startsWith(SdkConstants.MANIFEST_PLACEHOLDER_PREFIX))
      val variant = context.project.buildVariant
      if (variant != null) {
        val placeHolders = variant.manifestPlaceholders
        val name =
          hostname.substring(
            SdkConstants.MANIFEST_PLACEHOLDER_PREFIX.length,
            hostname.length - SdkConstants.MANIFEST_PLACEHOLDER_SUFFIX.length,
          )
        return placeHolders[name]
      }
      return null
    }

    /**
     * Gets the Digital Asset Links JSON file on the website host.
     *
     * @param url The URL of the host on which JSON file will be fetched.
     */
    private fun getJson(client: LintClient, url: String, redirectsCount: Int): HttpResult {
      try {
        val urlObj = URL(url)
        val urlConnection =
          client.openConnection(urlObj, 3000) as? HttpURLConnection
            ?: return HttpResult(STATUS_HTTP_CONNECT_FAIL, null)
        val connection = urlConnection
        try {
          val status = connection.responseCode
          if (
            status == HttpURLConnection.HTTP_MOVED_PERM ||
              status == HttpURLConnection.HTTP_MOVED_TEMP
          ) {
            if (redirectsCount < 3) {
              val newUrl = connection.getHeaderField("Location")
              if (newUrl != null && newUrl != url) {
                return getJson(client, newUrl, redirectsCount + 1)
              }
            }
            return HttpResult(status, null)
          }

          val inputStream = connection.inputStream ?: return HttpResult(status, null)
          val response = String(inputStream.readAllBytes(), UTF_8)
          inputStream.close()
          try {
            @Suppress("deprecation") val jsonFile = JsonParser().parse(response)
            return HttpResult(status, jsonFile)
          } catch (e: JsonSyntaxException) {
            return HttpResult(STATUS_WRONG_JSON_SYNTAX, null)
          } catch (e: RuntimeException) {
            return HttpResult(STATUS_JSON_PARSE_FAIL, null)
          }
        } finally {
          connection.disconnect()
        }
      } catch (e: MalformedURLException) {
        return HttpResult(STATUS_MALFORMED_URL, null)
      } catch (e: UnknownHostException) {
        return HttpResult(STATUS_UNKNOWN_HOST, null)
      } catch (e: FileNotFoundException) {
        return HttpResult(STATUS_NOT_FOUND, null)
      } catch (e: IOException) {
        return HttpResult(STATUS_HTTP_CONNECT_FAIL, null)
      }
    }

    /**
     * Gets the package names of all the apps from the Digital Asset Links JSON file.
     *
     * @param element The JsonElement of the json file.
     * @return All the package names.
     */
    private fun getPackageNameFromJson(element: JsonElement?): List<String?> {
      val packageNames: MutableList<String?> = Lists.newArrayList()
      if (element is JsonArray) {
        val jsonArray = element
        for (i in 0 until jsonArray.size()) {
          val app = jsonArray[i]
          if (app is JsonObject) {
            val target = app.getAsJsonObject("target")
            if (target != null) {
              // Checks namespace to ensure it is an app statement.
              val namespace = target["namespace"]
              val packageName = target["package_name"]
              if (namespace != null && namespace.asString == "android_app" && packageName != null) {
                packageNames.add(packageName.asString)
              }
            }
          }
        }
      }
      return packageNames
    }
  }
}
