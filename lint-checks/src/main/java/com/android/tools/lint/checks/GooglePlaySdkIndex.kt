/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.ide.common.repository.NetworkCache
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_ADS
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_DECEPTIVE_BEHAVIOR
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_DEVICE_AND_NETWORK_ABUSE
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MALWARE
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_MOBILE_UNWANTED_SOFTWARE
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_PERMISSIONS
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_UNKNOWN
import com.android.tools.lint.checks.LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy.SDK_POLICY_USER_DATA
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_CROSS_APP_SCRIPTING
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_FILE_BASED_XSS
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_FRAGMENT_INJECTION
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_IMPLICIT_INTERNAL_INTENT
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_IMPLICIT_PENDING_INTENT
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_INTENT_REDIRECTION
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_INTENT_SCHEME_HIJACKING
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_JS_INTERFACE_INJECTION
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_LEAKED_GCP_KEYS
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_PATH_TRAVERSAL
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_SQL_INJECTION
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_CIPHER_MODE
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_ENCRYPTION
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_HOSTNAME_VERIFIER
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_SSL_ERROR_HANDLER
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_TRUST_MANAGER
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_WEBVIEW_OAUTH
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_UNSPECIFIED
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_VULNERABLE_LIBS
import com.android.tools.lint.checks.LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType.SDK_SECURITY_VULNERABILITY_TYPE_ZIP_PATH_TRAVERSAL
import com.android.tools.lint.detector.api.LintFix
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import org.jetbrains.annotations.VisibleForTesting

/** Provides information about libraries from the Google Play SDK Index. */
abstract class GooglePlaySdkIndex(cacheDir: Path? = null) :
  NetworkCache(
    GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_URL,
    GOOGLE_PLAY_SDK_INDEX_KEY,
    cacheDir,
    cacheExpiryHours = TimeUnit.DAYS.toHours(GOOGLE_PLAY_SDK_CACHE_EXPIRY_INTERVAL_DAYS).toInt(),
  ) {
  companion object {
    const val SDK_INDEX_SNAPSHOT_TEST_BASE_URL_ENV_VAR = "SDK_INDEX_TEST_BASE_URL"
    private const val DEFAULT_SDK_INDEX_SNAPSHOT_BASE_URL = "https://dl.google.com/play-sdk/index/"
    // DEFAULT_SHOW_NOTES_FROM_DEVELOPER should match
    // StudioFlags.SHOW_SDK_INDEX_NOTES_FROM_DEVELOPER for consistency between CLI and AS
    const val DEFAULT_SHOW_NOTES_FROM_DEVELOPER = true
    // DEFAULT_SHOW_RECOMMENDED_VERSIONS should match
    // StudioFlags.SHOW_SDK_INDEX_RECOMMENDED_VERSIONS for consistency between CLI and AS
    const val DEFAULT_SHOW_RECOMMENDED_VERSIONS = true
    // DEFAULT_SHOW_DEPRECATION_ISSUES should match
    // StudioFlags.SHOW_SDK_INDEX_DEPRECATION_ISSUES for consistency between CLI and AS
    const val DEFAULT_SHOW_DEPRECATION_ISSUES = true
    const val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_FILE = "snapshot.gz"
    const val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE = "sdk-index-offline-snapshot.proto.gz"
    val GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_URL =
      System.getenv(SDK_INDEX_SNAPSHOT_TEST_BASE_URL_ENV_VAR) ?: DEFAULT_SDK_INDEX_SNAPSHOT_BASE_URL
    const val GOOGLE_PLAY_SDK_INDEX_KEY = "sdk_index"
    const val GOOGLE_PLAY_SDK_CACHE_EXPIRY_INTERVAL_DAYS = 7L
    const val GOOGLE_PLAY_SDK_INDEX_URL = "https://developer.android.com/distribute/sdk-index"
    const val VIEW_DETAILS_MESSAGE = "View details in Google Play SDK Index"
    val POLICY_TYPE_TO_TEXT =
      mapOf(
        SDK_POLICY_UNKNOWN to "unknown",
        SDK_POLICY_ADS to "Ads",
        SDK_POLICY_DEVICE_AND_NETWORK_ABUSE to "Device and Network Abuse",
        SDK_POLICY_DECEPTIVE_BEHAVIOR to "Deceptive Behavior",
        SDK_POLICY_USER_DATA to "User Data",
        SDK_POLICY_PERMISSIONS to "Permissions",
        SDK_POLICY_MOBILE_UNWANTED_SOFTWARE to "Mobile Unwanted Software",
        SDK_POLICY_MALWARE to "Malware",
      )

    data class VulnerabilityDescription(
      val name: String,
      val description: String,
      val link: String?,
    )

    val SECURITY_VULNERABILITY_TYPE_TO_TEXT =
      mapOf(
        SDK_SECURITY_VULNERABILITY_TYPE_UNSPECIFIED to
          VulnerabilityDescription(
            name = "unspecified",
            description = "contains unspecified vulnerability issues",
            link = null,
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_TRUST_MANAGER to
          VulnerabilityDescription(
            name = "Unsafe TrustManager",
            description = "contains an unsafe implementation of the X509TrustManager interface",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_HOSTNAME_VERIFIER to
          VulnerabilityDescription(
            name = "Unsafe HostnameVerifier",
            description =
              "contains an unsafe implementation of the interfaces HostnameVerifier or X509HostnameVerifier",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_SSL_ERROR_HANDLER to
          VulnerabilityDescription(
            name = "Unsafe SSL Error Handler",
            description = "contains an unsafe implementation of the onReceivedSslError handler",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_ZIP_PATH_TRAVERSAL to
          VulnerabilityDescription(
            name = "Zip Path Traversal",
            description = "contains unsafe unzipping patterns",
            link = "https://support.google.com/faqs/answer/9294009",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_WEBVIEW_OAUTH to
          VulnerabilityDescription(
            name = "Unsafe OAuth via WebView",
            description = "uses WebView for authentication, which is not recommended",
            link = "https://support.google.com/faqs/answer/12284343",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_CIPHER_MODE to
          VulnerabilityDescription(
            name = "Unsafe Encryption Mode Usage",
            description = "contains encryption employing the less secure mode AES/ECB",
            link = "https://support.google.com/faqs/answer/10046138",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_UNSAFE_ENCRYPTION to
          VulnerabilityDescription(
            name = "Unsafe Cryptographic Encryption",
            description = "contains unsafe encryption patterns",
            link = "https://support.google.com/faqs/answer/9450925",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_IMPLICIT_PENDING_INTENT to
          VulnerabilityDescription(
            name = "Implicit PendingIntent",
            description = "contains an Implicit PendingIntent issue",
            link = "https://support.google.com/faqs/answer/10437428",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_IMPLICIT_INTERNAL_INTENT to
          VulnerabilityDescription(
            name = "Implicit Internal Intent",
            description = "contains an Implicit Internal Intent issue",
            link = "https://support.google.com/faqs/answer/10437428",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_CROSS_APP_SCRIPTING to
          VulnerabilityDescription(
            name = "Cross-App Scripting",
            description = "may be vulnerable to WebView Cross-App Scripting",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_FILE_BASED_XSS to
          VulnerabilityDescription(
            name = "File Based XSS",
            description = "may be vulnerable to File-based Cross-Site Scripting",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_INTENT_SCHEME_HIJACKING to
          VulnerabilityDescription(
            name = "Intent Scheme Hijacking",
            description = "may be vulnerable to Intent-Scheme Hijacking",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_JS_INTERFACE_INJECTION to
          VulnerabilityDescription(
            name = "JavaScript Interface Injection",
            description = "may be vulnerable to JavaScript Interface Injection",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_INTENT_REDIRECTION to
          VulnerabilityDescription(
            name = "Intent Redirection",
            description = "may be vulnerable to Intent Redirection",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_FRAGMENT_INJECTION to
          VulnerabilityDescription(
            name = "Fragment Injection",
            description =
              "contains an unsafe PreferenceActivity implementation that may be vulnerable to Fragment Injection",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_PATH_TRAVERSAL to
          VulnerabilityDescription(
            name = "ContentProvider Path Traversal",
            description = "may be vulnerable to ContentProvider Path Traversal",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_SQL_INJECTION to
          VulnerabilityDescription(
            name = "ContentProvider SQL Injection",
            description = "may be vulnerable to ContentProvider SQL Injection",
            link = "https://support.google.com/googleplay/android-developer/answer/9888379",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_LEAKED_GCP_KEYS to
          VulnerabilityDescription(
            name = "Leaked GCP keys",
            description = "contains exposed Google Cloud Platform (GCP) API key(s)",
            link = "https://support.google.com/faqs/answer/9287711",
          ),
        SDK_SECURITY_VULNERABILITY_TYPE_VULNERABLE_LIBS to
          VulnerabilityDescription(
            name = "Known Vulnerable Library (JS)",
            description = "contains one or more JavaScript libraries with known security issues",
            link = "https://support.google.com/faqs/answer/9464300",
          ),
      )
  }

  private lateinit var lastReadResult: ReadDataResult
  private var initialized: Boolean = false
  private var status: GooglePlaySdkIndexStatus = GooglePlaySdkIndexStatus.NOT_READY
  private val libraryToSdk = HashMap<String, LibraryToSdk>()
  var showNotesFromDeveloper = DEFAULT_SHOW_NOTES_FROM_DEVELOPER
  var showRecommendedVersions = DEFAULT_SHOW_RECOMMENDED_VERSIONS
  var showDeprecationIssues = DEFAULT_SHOW_DEPRECATION_ISSUES

  /**
   * Read Index snapshot (locally if it is not old and remotely if old and network is available) and
   * store results in maps for later consumption.
   */
  fun initialize() {
    initialize(null)
  }

  private fun readIndexData(readFunction: () -> InputStream?): ReadDataResult {
    var readDataErrorType = ReadDataErrorType.DATA_FUNCTION_EXCEPTION
    try {
      val rawData = readFunction.invoke()
      if (rawData != null) {
        readDataErrorType = ReadDataErrorType.GZIP_EXCEPTION
        val gzipData = GZIPInputStream(rawData)
        readDataErrorType = ReadDataErrorType.INDEX_PARSE_EXCEPTION
        val index = Index.parseFrom(gzipData)
        readDataErrorType =
          if (index != null) ReadDataErrorType.NO_ERROR
          else ReadDataErrorType.INDEX_PARSE_NULL_ERROR
        return ReadDataResult(index, readDataErrorType, exception = null)
      }
    } catch (exception: Exception) {
      return ReadDataResult(index = null, readDataErrorType, exception)
    }
    return ReadDataResult(
      index = null,
      readDataErrorType = ReadDataErrorType.DATA_FUNCTION_NULL_ERROR,
      exception = null,
    )
  }

  @VisibleForTesting
  fun initialize(overriddenData: InputStream? = null) {
    synchronized(this) {
      if (initialized) {
        return
      }
      initialized = true
      status = GooglePlaySdkIndexStatus.NOT_READY
    }
    var index: Index? = null
    val indexDataSource: DataSourceType
    // Read from cache/network
    if (overriddenData != null) {
      // Do not check for exceptions, calling from a test
      lastReadSourceType = DataSourceType.TEST_DATA
      indexDataSource = lastReadSourceType
      index = Index.parseFrom(overriddenData)
    } else {
      lastReadResult = readIndexData { findData(GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_FILE) }
      if (lastReadResult.index != null) {
        // Using data from cache
        indexDataSource = lastReadSourceType
        index = lastReadResult.index
      } else {
        if (lastReadSourceType != DataSourceType.DEFAULT_DATA) {
          lastReadSourceType = DataSourceType.UNKNOWN_SOURCE
          val offlineResult = readIndexData {
            readDefaultData(GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE)
          }
          if (offlineResult.index != null) {
            indexDataSource = DataSourceType.DEFAULT_DATA
            index = offlineResult.index
          } else {
            indexDataSource = DataSourceType.UNKNOWN_SOURCE
            logErrorInDefaultData(offlineResult)
          }
        } else {
          indexDataSource = DataSourceType.UNKNOWN_SOURCE
          logErrorInDefaultData(lastReadResult)
        }
      }
    }
    if (index != null) {
      setMaps(index)
      status = GooglePlaySdkIndexStatus.READY
      logIndexLoadedCorrectly(indexDataSource)
    }
  }

  /** Tells if the Index is ready to be used (loaded and maps generated) */
  fun isReady() = status == GooglePlaySdkIndexStatus.READY

  /**
   * Does this library have policy issues?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has compliant
   *   issues.
   */
  fun isLibraryNonCompliant(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val isNonCompliant =
      getLabels(groupId, artifactId, versionString)?.hasPolicyIssuesInfo() == true
    if (isNonCompliant) {
      logNonCompliant(groupId, artifactId, versionString, buildFile)
    }
    return isNonCompliant
  }

  /**
   * Is this library marked as outdated?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has been marked
   *   as outdated.
   */
  fun isLibraryOutdated(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val isOutdated = getLabels(groupId, artifactId, versionString)?.hasOutdatedIssueInfo() == true
    if (isOutdated) {
      logOutdated(groupId, artifactId, versionString, buildFile)
    }
    return isOutdated
  }

  /**
   * Does this library have critical issues?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has critical
   *   issues reported by its authors.
   */
  fun hasLibraryCriticalIssues(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val hasCriticalIssues =
      getLabels(groupId, artifactId, versionString)?.hasCriticalIssueInfo() == true
    if (hasCriticalIssues) {
      logHasCriticalIssues(groupId, artifactId, versionString, buildFile)
    }
    return hasCriticalIssues
  }

  /**
   * Does this library have security vulnerabilities?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has security
   *   vulnerabilities reported.
   */
  fun hasLibraryVulnerabilityIssues(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val hasVulnerabilities =
      getLabels(groupId, artifactId, versionString)?.hasSecurityVulnerabilitiesInfo() == true
    if (hasVulnerabilities) {
      logVulnerability(groupId, artifactId, versionString, buildFile)
    }
    return hasVulnerabilities
  }

  /**
   * Has this library been deprecated?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version currently used, for logging purposes
   * @param buildFile: build file in which this dependency is declared, for logging purposes
   * @return true if the index has information about this particular version, and it has been
   *   labeled with [LibraryDeprecation].
   */
  fun isLibraryDeprecated(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): Boolean {
    val library = getLibrary(groupId, artifactId) ?: return false
    val deprecation = library.deprecation
    if (deprecation != null && deprecation.deprecationTimestampSeconds > 0) {
      logDeprecated(groupId, artifactId, versionString, buildFile)
      return showDeprecationIssues
    }
    return false
  }

  /**
   * Does this library have blocking issues?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @return true if the index has information about this particular version, and it has been
   *   labeled with blocking severity.
   */
  fun hasLibraryBlockingIssues(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): Boolean {
    val labels = getLabels(groupId, artifactId, versionString) ?: return false
    val severity = labels.severity
    return severity == LibraryVersionLabels.Severity.BLOCKING_SEVERITY
  }

  /**
   * Does this library have any issues that would result in an error or warning?
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version to check
   * @return true if the index has information about this particular version, and it has issues that
   *   will cause an error or warning. (Any blocking issue is an error, non-blocking outdated,
   *   policy or vulnerability issues are warnings; deprecated libraries will cause a warning if no
   *   blocking issues exist (error otherwise))
   */
  fun hasLibraryErrorOrWarning(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): Boolean {
    val library = getLibrary(groupId, artifactId)
    return library?.deprecation.isDeprecated() ||
      library?.getVersion(versionString)?.versionLabels.hasErrorOrWarning()
  }

  /**
   * Get URL for the SDK associated to this library
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @return the associated URL or null if there is none or flag for links to SDK is not enabled
   */
  fun getSdkUrl(groupId: String, artifactId: String): String? {
    if (!isReady()) {
      return null
    }
    val library = getLibrary(groupId, artifactId) ?: return null
    if (library.sdk.indexAvailability == Sdk.IndexAvailability.NOT_AVAILABLE) return null
    return library.sdk.indexUrl
  }

  /** Get latest version known by the SDK Index for a particular library (if available) */
  fun getLatestVersion(groupId: String, artifactId: String): String? {
    if (!isReady()) {
      return null
    }
    return getLibrary(groupId, artifactId)?.getLatestVersion()
  }

  private fun getLabels(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): LibraryVersionLabels? {
    if (!isReady()) {
      return null
    }
    val libraryVersion = getLibraryVersion(groupId, artifactId, versionString) ?: return null
    return libraryVersion.versionLabels
  }

  private fun getLibraryVersion(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): LibraryVersion? {
    val coordinate = createCoordinateString(groupId, artifactId)
    val sdk = libraryToSdk[coordinate] ?: return null
    return sdk.getVersion(versionString)
  }

  private fun getLibrary(groupId: String, artifactId: String): LibraryToSdk? {
    val coordinate = createCoordinateString(groupId, artifactId)
    return libraryToSdk[coordinate]
  }

  private fun setMaps(index: Index) {
    libraryToSdk.clear()
    val sdkList = index.sdksList
    for (sdk in sdkList) {
      for (library in sdk.librariesList) {
        val coordinate =
          createCoordinateString(
            library.libraryId.mavenId.groupId,
            library.libraryId.mavenId.artifactId,
          )
        val currentLibrary = LibraryToSdk(coordinate, sdk)
        // Add SDK deprecation issues
        val deprecation = library.libraryDeprecation
        // Store the information regardless of [showDeprecationIssues] to allow tests to switch the
        // flag after initialization
        if (deprecation != null && deprecation.deprecationTimestampSeconds > 0) {
          currentLibrary.deprecation = deprecation
        }
        for (version in library.versionsList) {
          currentLibrary.addLibraryVersion(version.versionString, version)
        }
        libraryToSdk[currentLibrary.libraryId] = currentLibrary
      }
    }
  }

  private fun createCoordinateString(groupId: String, artifactId: String) = "$groupId:$artifactId"

  private enum class GooglePlaySdkIndexStatus {
    NOT_READY,
    READY,
  }

  private class LibraryToSdk(val libraryId: String, val sdk: Sdk) {
    private val versionToLibraryVersion = HashMap<String, LibraryVersion>()
    private var latestVersion: String? = null
    var deprecation: LibraryDeprecation? = null

    fun addLibraryVersion(versionString: String, libraryVersion: LibraryVersion) {
      versionToLibraryVersion[versionString] = libraryVersion
      if (libraryVersion.isLatestVersion) {
        latestVersion = versionString
      }
    }

    fun getVersion(versionString: String): LibraryVersion? {
      return versionToLibraryVersion[versionString]
    }

    fun getLatestVersion(): String? {
      return latestVersion
    }
  }

  override fun readDefaultData(relative: String): InputStream? {
    // This function is called only if there were errors while reading the cached data, log that
    // error if the previous source was known
    if (lastReadSourceType != DataSourceType.UNKNOWN_SOURCE) {
      // Log reason for not being able to use cached file
      logCachingError(lastReadResult, lastReadSourceType)
    }
    // We only have a single file, return it no matter what relative string is used
    return GooglePlaySdkIndex::class
      .java
      .getResourceAsStream("/$GOOGLE_PLAY_SDK_INDEX_SNAPSHOT_RESOURCE")
  }

  /**
   * Generates a LintFix that opens a browser to the given library.
   *
   * @param groupId: group id for library coordinates
   * @param artifactId: artifact id for library coordinates
   * @param versionString: version of the library (only used for logging)
   * @param buildFile: build file where this library is being used
   * @return a link to the SDK url this library belongs to if the index has information about it
   */
  open fun generateSdkLinkLintFix(
    groupId: String,
    artifactId: String,
    versionString: String,
    buildFile: File?,
  ): LintFix? {
    val url = getSdkUrl(groupId, artifactId)
    return if (url.isNullOrBlank()) null else LintFix.ShowUrl(VIEW_DETAILS_MESSAGE, null, url)
  }

  /** Generate a message for a library that has blocking policy issues */
  fun generateBlockingPolicyMessages(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): List<String> {
    val recommendedVersions = getPolicyRecommendedVersions(groupId, artifactId, versionString)
    return getPolicyLabels(getLabels(groupId, artifactId, versionString)).map { label ->
      "**[Prevents app release in Google Play Console]** $groupId:$artifactId version $versionString has $label issues that will block publishing of your app to Play Console$recommendedVersions"
    }
  }

  /** Generate a message for a library that has policy issues */
  fun generatePolicyMessages(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): List<String> {
    val recommendedVersions = getPolicyRecommendedVersions(groupId, artifactId, versionString)
    return getPolicyLabels(getLabels(groupId, artifactId, versionString)).map { label ->
      "$groupId:$artifactId version $versionString has $label issues that will block publishing of your app to Play Console in the future$recommendedVersions"
    }
  }

  /** Generate a message for a library that has blocking critical issues */
  fun generateBlockingCriticalMessage(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    val note = getNoteFromDeveloper(groupId, artifactId, versionString)
    return "**[Prevents app release in Google Play Console]** $groupId:$artifactId version $versionString has been reported as problematic by its author and will block publishing of your app to Play Console$note"
  }

  /** Generate a message for a library that has non-blocking critical issues */
  fun generateCriticalMessage(groupId: String, artifactId: String, versionString: String): String {
    val note = getNoteFromDeveloper(groupId, artifactId, versionString)
    return "$groupId:$artifactId version $versionString has an associated message from its author$note"
  }

  /** Generate a message for a library that has blocking outdated issues */
  fun generateBlockingOutdatedMessage(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    val recommendedVersions = getOutdatedRecommendedVersions(groupId, artifactId, versionString)
    return "**[Prevents app release in Google Play Console]** $groupId:$artifactId version $versionString has been reported as outdated by its author and will block publishing of your app to Play Console$recommendedVersions"
  }

  /** Generate a message for a library that has non-blocking outdated issues */
  fun generateOutdatedMessage(groupId: String, artifactId: String, versionString: String): String {
    val recommendedVersions = getOutdatedRecommendedVersions(groupId, artifactId, versionString)
    return "$groupId:$artifactId version $versionString has been reported as outdated by its author$recommendedVersions"
  }

  /**
   * Generate a list of messages for a library that has vulnerability issues, with a link for more
   * information (can be null)
   */
  fun generateVulnerabilityMessages(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): List<VulnerabilityDescription> {
    return getVulnerabilityLabels(getLabels(groupId, artifactId, versionString)).map { message ->
      VulnerabilityDescription(
        message.name,
        description = "$groupId:$artifactId version $versionString ${message.description}.",
        message.link,
      )
    }
  }

  fun generateDeprecatedMessage(groupId: String, artifactId: String): String {
    val library = getLibrary(groupId, artifactId)
    val sdkName = library?.sdk?.sdkName
    val identification =
      if (sdkName.isNullOrBlank()) {
        "$groupId:$artifactId"
      } else {
        "$sdkName ($groupId:$artifactId)"
      }
    val alternatives = getDeprecatedAlternatives(library)
    return "$identification has been deprecated by its developer. Consider updating to an alternative SDK before publishing a new release." +
      alternatives
  }

  /**
   * Generate a list of versions that the library owner has recommended to use instead of the passed
   * version.
   */
  fun recommendedVersions(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): Collection<LibraryVersionRange> {
    val recommendations = LinkedHashSet<LibraryVersionRange>()
    val labels = getLabels(groupId, artifactId, versionString)
    if (labels != null) {
      labels.policyIssuesInfo.recommendedVersionsList?.filterNotNull()?.forEach {
        recommendations.add(it)
      }
      labels.outdatedIssueInfo.recommendedVersionsList?.filterNotNull()?.forEach {
        recommendations.add(it)
      }
    }
    return recommendations
  }

  protected open fun logHasCriticalIssues(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logNonCompliant(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logOutdated(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logVulnerability(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logDeprecated(
    groupId: String,
    artifactId: String,
    versionString: String,
    file: File?,
  ) {}

  protected open fun logCachingError(readResult: ReadDataResult, dataSourceType: DataSourceType) {}

  protected open fun logErrorInDefaultData(readResult: ReadDataResult) {}

  protected open fun logIndexLoadedCorrectly(dataSourceType: DataSourceType) {}

  protected enum class ReadDataErrorType {
    NO_ERROR,
    // Function used to get the data caused an exception
    DATA_FUNCTION_EXCEPTION,
    // Function used to get the data returned null
    DATA_FUNCTION_NULL_ERROR,
    // There was an exception while decompressing the raw data
    GZIP_EXCEPTION,
    // Exception while parsing decompressed data
    INDEX_PARSE_EXCEPTION,
    // Resulted Index was null after parsing
    INDEX_PARSE_NULL_ERROR,
  }

  protected class ReadDataResult(
    val index: Index?,
    val readDataErrorType: ReadDataErrorType,
    val exception: Exception?,
  )

  @VisibleForTesting fun getLastReadSource() = lastReadSourceType

  private fun getPolicyLabels(labels: LibraryVersionLabels?): List<String> {
    val defaultLabel = "policy"
    val policyViolations = extractPolicyViolations(labels)
    val result = mutableListOf<String>()
    var hasUnknown = false
    for (violation in policyViolations) {
      if (POLICY_TYPE_TO_TEXT.containsKey(violation)) {
        result.add("${POLICY_TYPE_TO_TEXT[violation]} policy")
      } else {
        hasUnknown = true
      }
    }
    if (hasUnknown || result.isEmpty()) {
      result.add(defaultLabel)
    }
    return result
  }

  private fun extractPolicyViolations(
    labels: LibraryVersionLabels?
  ): Set<LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy> {
    val result = mutableSetOf<LibraryVersionLabels.PolicyIssuesInfo.SdkPolicy>()
    if (labels == null || !labels.hasPolicyIssuesInfo()) {
      return result
    }
    val types = labels.policyIssuesInfo.violatedSdkPoliciesList
    if (types != null) {
      result.addAll(types)
    }
    return result
  }

  private fun getNoteFromDeveloper(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    if (!showNotesFromDeveloper) return ""
    val labels = getLabels(groupId, artifactId, versionString) ?: return ""
    val criticalIssue = labels.criticalIssueInfo ?: return ""
    val message = criticalIssue.description
    if (message.isNullOrBlank()) return ""
    return ".\n**Note:** $message"
  }

  private fun getOutdatedRecommendedVersions(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    if (!showRecommendedVersions) return ""
    val labels = getLabels(groupId, artifactId, versionString) ?: return ""
    val outdatedIssue = labels.outdatedIssueInfo ?: return ""
    return generateRecommendedList(
      outdatedIssue.recommendedVersionsList,
      isThirdPartyLibrary(groupId, artifactId),
    )
  }

  private fun getPolicyRecommendedVersions(
    groupId: String,
    artifactId: String,
    versionString: String,
  ): String {
    if (!showRecommendedVersions) return ""
    val labels = getLabels(groupId, artifactId, versionString) ?: return ""
    val policyIssue = labels.policyIssuesInfo ?: return ""
    return generateRecommendedList(
      policyIssue.recommendedVersionsList,
      isThirdPartyLibrary(groupId, artifactId),
    )
  }

  private fun isThirdPartyLibrary(groupId: String, artifactId: String): Boolean {
    // Check first if there is information available in the SDK Index itself
    val coordinate = createCoordinateString(groupId, artifactId)
    val sdk = libraryToSdk[coordinate]
    if (sdk != null) {
      return !sdk.sdk.isGoogleOwned
    }
    // Not possible to tell from the Index data, assume everything is 3rd party
    return true
  }

  private fun generateRecommendedList(
    listOfVersions: List<LibraryVersionRange?>?,
    isThirdParty: Boolean,
  ): String {
    val ranges =
      (listOfVersions ?: return "").filterNotNull().joinToString("\n") { range ->
        if (range.upperBound.isNullOrBlank()) {
          "  - ${range.lowerBound} or higher"
        } else if (range.upperBound != range.lowerBound) {
          "  - From ${range.lowerBound} to ${range.upperBound}"
        } else {
          "  - ${range.lowerBound}"
        }
      }
    if (ranges.isEmpty()) return ""
    return ".\nThe library author recommends using versions:\n$ranges\n${
      if (isThirdParty) "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app."
      else ""
    }"
  }

  private fun getVulnerabilityLabels(
    labels: LibraryVersionLabels?
  ): List<VulnerabilityDescription> {
    val defaultDetails = "has unspecified vulnerability issues"
    val defaultName = "unspecified vulnerability"
    val vulnerabilities = extractVulnerabilities(labels)
    val result = mutableListOf<VulnerabilityDescription>()
    var hasUnknown = false
    for (vulnerability in vulnerabilities) {
      val details = SECURITY_VULNERABILITY_TYPE_TO_TEXT.getOrDefault(vulnerability, null)
      if (details != null) {
        result.add(details)
      } else {
        hasUnknown = true
      }
    }
    if (hasUnknown || result.isEmpty()) {
      result.add(VulnerabilityDescription(defaultName, defaultDetails, link = null))
    }
    return result
  }

  private fun extractVulnerabilities(
    labels: LibraryVersionLabels?
  ): Set<LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType> {
    val result =
      mutableSetOf<LibraryVersionLabels.SecurityVulnerabilitiesInfo.SdkSecurityVulnerabilityType>()
    if (labels == null || !labels.hasSecurityVulnerabilitiesInfo()) {
      return result
    }
    val types = labels.securityVulnerabilitiesInfo.vulnerabilitiesList
    if (types != null) {
      result.addAll(types)
    }
    return result
  }

  private fun getDeprecatedAlternatives(library: LibraryToSdk?): String {
    val deprecation = library?.deprecation
    if (deprecation == null) {
      return ""
    }
    val alternatives =
      deprecation.alternativeLibrariesList.mapNotNull { generateAlternativeName(it) }
    if (alternatives.isEmpty()) {
      return ""
    }
    return alternatives.joinToString(
      prefix = "\nThe developer has recommended these alternatives:\n```\n - ",
      separator = "\n - ",
      postfix = "\n```",
    )
  }

  private fun generateAlternativeName(library: AlternativeLibrary): String? {
    val name = library.sdkName
    val identifier = library.mavenSdkId
    if (name.isNullOrBlank()) {
      if (
        identifier != null &&
          (!identifier.groupId.isNullOrBlank()) &&
          (!identifier.artifactId.isNullOrBlank())
      ) {
        return "${identifier.groupId}:${identifier.artifactId}"
      }
      return null
    } else {
      if (
        identifier != null &&
          (!identifier.groupId.isNullOrBlank()) &&
          (!identifier.artifactId.isNullOrBlank())
      ) {
        return "$name (${identifier.groupId}:${identifier.artifactId})"
      }
      return name
    }
  }

  private fun LibraryVersionLabels?.hasErrorOrWarning(): Boolean {
    if (this == null) {
      return false
    }
    return this.severity == LibraryVersionLabels.Severity.BLOCKING_SEVERITY ||
      this.hasOutdatedIssueInfo() ||
      this.hasPolicyIssuesInfo() ||
      this.hasSecurityVulnerabilitiesInfo()
  }

  private fun LibraryDeprecation?.isDeprecated(): Boolean {
    if (!showDeprecationIssues) {
      return false
    }
    if (this == null) {
      return false
    }
    return this.deprecationTimestampSeconds > 0
  }
}
