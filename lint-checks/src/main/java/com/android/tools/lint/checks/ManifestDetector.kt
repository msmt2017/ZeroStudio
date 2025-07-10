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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ALLOW_BACKUP
import com.android.SdkConstants.ATTR_FULL_BACKUP_CONTENT
import com.android.SdkConstants.ATTR_ICON
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_MIN_SDK_VERSION
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_TARGET_SDK_VERSION
import com.android.SdkConstants.ATTR_VERSION_CODE
import com.android.SdkConstants.ATTR_VERSION_NAME
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.DRAWABLE_PREFIX
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_PERMISSION
import com.android.SdkConstants.TAG_PERMISSION_GROUP
import com.android.SdkConstants.TAG_PROVIDER
import com.android.SdkConstants.TAG_QUERIES
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_LIBRARY
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_SDK
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.gradle.Version
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.repository.MavenRepositories
import com.android.ide.common.repository.SdkMavenRepository
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.tools.lint.model.LintModelExternalLibrary
import com.android.tools.lint.model.LintModelVariant
import com.android.utils.XmlUtils
import com.android.utils.iterator
import com.android.utils.usLocaleCapitalize
import com.android.utils.visitAttributes
import com.android.xml.AndroidManifest
import java.io.File
import java.io.File.separator
import java.util.Calendar
import org.intellij.lang.annotations.Language
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Checks for issues in AndroidManifest files such as declaring elements in the wrong order. */
class ManifestDetector : Detector(), XmlScanner {
  companion object {
    private val IMPLEMENTATION = Implementation(ManifestDetector::class.java, Scope.MANIFEST_SCOPE)

    /** Calendar to use to look up the current time (used by tests to set specific time. */
    var calendar: Calendar? = null

    /** Wrong order of elements in the manifest */
    @JvmField
    val ORDER =
      Issue.create(
        id = "ManifestOrder",
        briefDescription = "Incorrect order of elements in manifest",
        explanation =
          """
                The `<application>` tag should appear after the elements which declare which version you need, \
                which features you need, which libraries you need, and so on. In the past there have been subtle \
                bugs (such as themes not getting applied correctly) when the `<application>` tag appears before \
                some of these other elements, so it's best to order your manifest in the logical dependency \
                order.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Using multiple `<uses-sdk>` elements */
    @JvmField
    val MULTIPLE_USES_SDK =
      Issue.create(
        id = "MultipleUsesSdk",
        briefDescription = "Multiple `<uses-sdk>` elements in the manifest",
        explanation =
          """
                The `<uses-sdk>` element should appear just once; the tools will **not** merge the contents \
                of all the elements so if you split up the attributes across multiple elements, only one of \
                them will take effect. To fix this, just merge all the attributes from the various elements \
                into a single <uses-sdk> element.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.FATAL,
        moreInfo = "https://developer.android.com/guide/topics/manifest/uses-sdk-element.html",
        implementation = IMPLEMENTATION,
      )

    /** Missing a `<uses-sdk>` element */
    @JvmField
    val WRONG_PARENT =
      Issue.create(
        id = "WrongManifestParent",
        briefDescription = "Wrong manifest parent",
        explanation =
          """
                The `<uses-library>` element should be defined as a direct child of the `<application>` \
                tag, not the `<manifest>` tag or an `<activity>` tag. Similarly, a `<uses-sdk>` tag must \
                be declared at the root level, and so on. This check looks for incorrect declaration \
                locations in the manifest, and complains if an element is found in the wrong place.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.FATAL,
        moreInfo = "https://developer.android.com/guide/topics/manifest/manifest-intro.html",
        implementation = IMPLEMENTATION,
      )

    /** Missing a `<uses-sdk>` element */
    @JvmField
    val DUPLICATE_ACTIVITY =
      Issue.create(
        id = "DuplicateActivity",
        briefDescription = "Activity registered more than once",
        explanation =
          """
                An activity should only be registered once in the manifest. If it is accidentally \
                registered more than once, then subtle errors can occur, since attribute declarations \
                from the two elements are not merged, so you may accidentally remove previous \
                declarations.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Not specifying data extraction rules */
    @JvmField
    val DATA_EXTRACTION_RULES =
      Issue.create(
          "DataExtractionRules",
          briefDescription = "Missing data extraction rules",
          explanation =
            """
                Before Android 12, the attributes `android:allowBackup` and `android:fullBackupContent` \
                were used to configure all forms of backup, including cloud backups, device-to-device \
                transfers and adb backup.

                In Android 12 and higher, these attributes have been deprecated and will only apply \
                to cloud backups. You should instead use the attribute `android:dataExtractionRules`, \
                specifying an `@xml` resource that configures which files to back up, for cloud backups \
                and for device-to-device transfers, separately. If your `minSdkVersion` supports older \
                versions, you'll still want to specify an `android:fullBackupContent` resource if the default \
                behavior is not right for your app.
                """,
          category = Category.SECURITY,
          priority = 3,
          moreInfo = "https://developer.android.com/about/versions/12/backup-restore#xml-changes",
          implementation = IMPLEMENTATION,
        )
        .addMoreInfo("https://goo.gle/DataExtractionRules")

    /** Conflicting permission names */
    @JvmField
    val UNIQUE_PERMISSION =
      Issue.create(
        id = "UniquePermission",
        briefDescription = "Permission names are not unique",
        explanation =
          """
                The unqualified names or your permissions must be unique. The reason for this is that at \
                build time, the `aapt` tool will generate a class named `Manifest` which contains a field \
                for each of your permissions. These fields are named using your permission unqualified names \
                (i.e. the name portion after the last dot).

                If more than one permission maps to the same field name, that field will arbitrarily name \
                just one of them.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Using a resource for attributes that do not allow it */
    @JvmField
    val SET_VERSION =
      Issue.create(
        id = "MissingVersion",
        briefDescription = "Missing application name/version",
        explanation =
          """
                You should define the version information for your application.

                `android:versionCode`: An integer value that represents the version of the application code, \
                relative to other versions.

                `android:versionName`: A string value that represents the release version of the application \
                code, as it should be shown to users.
                """,
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.WARNING,
        moreInfo = "https://developer.android.com/studio/publish/versioning#appversioning",
        implementation = IMPLEMENTATION,
      )

    /** Using a resource for attributes that do not allow it */
    @JvmField
    val ILLEGAL_REFERENCE =
      Issue.create(
        id = "IllegalResourceRef",
        briefDescription = "Name and version must be integer or string, not resource",
        explanation =
          """
                For the `versionCode` attribute, you have to specify an actual integer literal; you cannot \
                use an indirection with a `@dimen/name` resource. Similarly, the `versionName` attribute \
                should be an actual string, not a string resource url.
                """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Declaring a uses-feature multiple time */
    @JvmField
    val DUPLICATE_USES_FEATURE =
      Issue.create(
        id = "DuplicateUsesFeature",
        briefDescription = "Feature declared more than once",
        explanation = "A given feature should only be declared once in the manifest.",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Not explicitly defining application icon */
    @JvmField
    val APPLICATION_ICON =
      Issue.create(
        id = "MissingApplicationIcon",
        briefDescription = "Missing application icon",
        explanation =
          """
                You should set an icon for the application as whole because there is no default. This \
                attribute must be set as a reference to a drawable resource containing the image (for \
                example `@drawable/icon`).
                """,
        category = Category.ICONS,
        priority = 5,
        severity = Severity.WARNING,
        moreInfo = "https://developer.android.com/studio/publish/preparing#publishing-configure",
        implementation = IMPLEMENTATION,
      )

    /** Malformed Device Admin */
    @JvmField
    val DEVICE_ADMIN =
      Issue.create(
        id = "DeviceAdmin",
        briefDescription = "Malformed Device Admin",
        explanation =
          """
                If you register a broadcast receiver which acts as a device admin, you must also register \
                an `<intent-filter>` for the action `android.app.action.DEVICE_ADMIN_ENABLED`, without any \
                `<data>`, such that the device admin can be activated/deactivated.

                To do this, add
                ```xml
                `<intent-filter>`
                    `<action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />`
                `</intent-filter>`
                ```
                to your `<receiver>`.
                """,
        category = Category.CORRECTNESS,
        priority = 7,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Using a mock location in a non-debug-specific manifest file */
    @JvmField
    val MOCK_LOCATION =
      Issue.create(
        id = "MockLocation",
        briefDescription = "Using mock location provider in production",
        explanation =
          """
                Using a mock location provider (by requiring the permission `android.permission.ACCESS_MOCK_LOCATION`) should **only** be done in debug builds (or from tests). In Gradle projects, that means you should only request this permission in a test or debug source set specific manifest file.

                To fix this, create a new manifest file in the debug folder and move the `<uses-permission>` element there. A typical path to a debug manifest override file in a Gradle project is src/debug/AndroidManifest.xml.
                """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Defining a value that is overridden by Gradle */
    @JvmField
    val GRADLE_OVERRIDES =
      Issue.create(
        id = "GradleOverrides",
        briefDescription = "Value overridden by Gradle build script",
        explanation =
          """
                The value of (for example) `minSdkVersion` is only used if it is not specified in the \
                `build.gradle` build scripts. When specified in the Gradle build scripts, the manifest \
                value is ignored and can be misleading, so should be removed to avoid ambiguity.
                """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Using drawable rather than mipmap launcher icons */
    @JvmField
    val MIPMAP =
      Issue.create(
        id = "MipmapIcons",
        briefDescription = "Use Mipmap Launcher Icons",
        explanation =
          """
                Launcher icons should be provided in the `mipmap` resource directory. This is the same as \
                the `drawable` resource directory, except resources in the `mipmap` directory will not get \
                stripped out when creating density-specific APKs.

                In certain cases, the Launcher app may use a higher resolution asset (than would normally \
                be computed for the device) to display large app shortcuts. If drawables for densities \
                other than the device's resolution have been stripped out, then the app shortcut could \
                appear blurry.

                To fix this, move your launcher icons from `drawable-`dpi to `mipmap-`dpi and change \
                references from @drawable/ and R.drawable to @mipmap/ and R.mipmap.

                In Android Studio this lint warning has a quickfix to perform this automatically.
                """,
        category = Category.ICONS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Uses Wear Bind Listener which is deprecated */
    @JvmField
    val WEARABLE_BIND_LISTENER =
      Issue.create(
        id = "WearableBindListener",
        briefDescription = "Usage of Android Wear BIND_LISTENER is deprecated",
        explanation =
          """
                BIND_LISTENER receives all Android Wear events whether the application needs them or not. \
                This can be inefficient and cause applications to wake up unnecessarily. With Google Play \
                Services 8.2.0 or later it is recommended to use a more efficient combination of manifest \
                listeners and api-based live listeners filtered by action, path and/or path prefix.
                """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.FATAL,
        moreInfo =
          "https://android-developers.googleblog.com/2016/04/deprecation-of-bindlistener.html",
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val APP_INDEXING_SERVICE =
      Issue.create(
        id = "AppIndexingService",
        briefDescription = "App Indexing Background Services",
        explanation =
          """
                Apps targeting Android 8.0 or higher can no longer rely on background services while \
                listening for updates to the on-device index. Use a `BroadcastReceiver` for the \
                `UPDATE_INDEX` intent to continue supporting indexing in your app.
                """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        moreInfo =
          "https://firebase.google.com/docs/app-indexing/android/personal-content#add-a-broadcast-receiver-to-your-app",
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val REDUNDANT_LABEL =
      Issue.create(
        id = "RedundantLabel",
        briefDescription = "Redundant label on activity",
        explanation =
          """
                When an activity does not have a label attribute, it will use the one from the application tag. \
                Since the application has already specified the same label, the label on this activity can be omitted.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    @JvmStatic
    fun isLaunchableActivity(activity: Element): Boolean =
      findLaunchableCategoryNode(activity) != null

    @JvmStatic
    fun findLaunchableCategoryNode(activity: Element): Attr? {
      if (TAG_ACTIVITY != activity.tagName) {
        return null
      }
      for (filter in activity) {
        if (filter.tagName == TAG_INTENT_FILTER) {
          for (category in filter) {
            if (category.tagName == TAG_CATEGORY) {
              val attribute = category.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
              if (attribute != null && attribute.value == "android.intent.category.LAUNCHER") {
                return attribute
              }
            }
          }
        }
      }

      return null
    }

    /** Permission name of mock location permission */
    const val MOCK_LOCATION_PERMISSION = "android.permission.ACCESS_MOCK_LOCATION"

    private val MIN_WEARABLE_GMS_VERSION = Version.parse("8.2.0")
    private const val PLAY_SERVICES_WEARABLE =
      GradleDetector.GMS_GROUP_ID + ":play-services-wearable"
    private const val ATTR_DATA_EXTRACTION_RULES = "dataExtractionRules"
  }

  private var seenApplication = false

  /** Number of times we've seen the <uses-sdk> element </uses-sdk> */
  private var seenUsesSdk = 0

  /** Activities we've encountered */
  private var activities: MutableSet<String>? = null

  /** Features we've encountered */
  private var usesFeatures: MutableSet<String>? = null

  override fun beforeCheckFile(context: Context) {
    seenApplication = false
    seenUsesSdk = 0
    activities = null
    usesFeatures = null
  }

  override fun afterCheckFile(context: Context) {
    val xmlContext = context as XmlContext
    val element = xmlContext.document.documentElement ?: return
    checkDocumentElement(xmlContext, element)
  }

  /**
   * Should we look at `<application>` tags in the source file? If true, yes, analyze source
   * elements, if false, look at the merged manifest instead.
   */
  private fun onlyCheckSourceManifest(context: Context): Boolean {
    // When analyzing a single file in the IDE, limit the search to
    // the current manifest in the source editor. This is useful
    // because in the IDE, the merged manifest sometimes lags behind
    // the typing a bit (it's not updated immediately upon typing)
    // so we're making sure that we're reflecting the current
    // reality. For some merged manifest operations we really can't
    // limit ourselves to the current manifest because it depends
    // heavily on elements merged from other manifest files (such
    // as permission declarations) but that's not a factor for this
    // check.
    return context.driver.isIsolated()
  }

  private fun checkApplication(context: Context) {
    if (onlyCheckSourceManifest(context)) {
      // Single file analysis: Already done by XML visitor
      return
    }
    val mainProject = context.mainProject
    val mergedManifest = mainProject.mergedManifest ?: return
    val root = mergedManifest.documentElement ?: return
    val application = XmlUtils.getFirstSubTagByName(root, TAG_APPLICATION)
    // Just an injected <application/> node from the manifest merger?
    if (
      application == null || application.firstChild == null && application.attributes.length == 0
    ) {
      return
    }
    checkApplication(context, application)
  }

  /**
   * Checks that the main `<application>` tag specifies both an icon and allowBackup, possibly
   * merged from some upstream dependency
   */
  private fun checkApplication(context: Context, application: Element) {
    if (context.project.isLibrary) {
      return
    }
    checkBackup(context, application)
    checkIcon(application, context)
  }

  private fun checkBackup(context: Context, application: Element) {
    if (!context.isEnabled(DATA_EXTRACTION_RULES)) {
      return
    }
    val allowBackupNode = application.getAttributeNodeNS(ANDROID_URI, ATTR_ALLOW_BACKUP)
    val dataExtractionRules =
      application.getAttributeNodeNS(ANDROID_URI, ATTR_DATA_EXTRACTION_RULES)
    val fullBackupNode = application.getAttributeNodeNS(ANDROID_URI, ATTR_FULL_BACKUP_CONTENT)

    val project = context.mainProject
    val min = project.minSdk
    val target = project.targetSdk
    if (min < 31 && target >= 31) {
      if (allowBackupNode?.value == VALUE_FALSE && dataExtractionRules == null) {
        val fix = createDataExtractionRulesFix(context, fullBackupNode)
        reportFromManifest(
          context,
          DATA_EXTRACTION_RULES,
          allowBackupNode,
          "The attribute `android:allowBackup` is deprecated from Android 12 and higher and may be removed " +
            "in future versions. Consider adding the attribute `android:dataExtractionRules` specifying " +
            "an `@xml` resource which configures cloud backups and device transfers on Android 12 " +
            "and higher.",
          LocationType.VALUE,
          fix,
        )
      } else if (fullBackupNode != null && dataExtractionRules == null) {
        val fix = createDataExtractionRulesFix(context, fullBackupNode)
        reportFromManifest(
          context,
          DATA_EXTRACTION_RULES,
          fullBackupNode,
          "The attribute `android:fullBackupContent` is deprecated from Android 12 and higher " +
            "and may be removed in future versions. Consider adding the attribute " +
            "`android:dataExtractionRules` specifying an `@xml` resource which configures cloud " +
            "backups and device transfers on Android 12 and higher.",
          LocationType.VALUE,
          fix,
        )
      } else if (dataExtractionRules != null && fullBackupNode == null) {
        reportFromManifest(
          context,
          DATA_EXTRACTION_RULES,
          dataExtractionRules,
          "The attribute `android:dataExtractionRules` only applies for Android 12 and higher; since " +
            "`minSdkVersion` is API $min you should also set `android:fullBackupContent`",
          LocationType.VALUE,
        )
      }
    } else if (min >= 31 && dataExtractionRules == null) {
      if (
        allowBackupNode != null && fullBackupNode == null && allowBackupNode.value == VALUE_TRUE
      ) {
        reportFromManifest(
          context,
          DATA_EXTRACTION_RULES,
          allowBackupNode,
          "The attribute `android:allowBackup` is deprecated from Android 12 and the default " +
            "allows backup",
          LocationType.VALUE,
          fix().unset(ANDROID_URI, ATTR_ALLOW_BACKUP).build(),
        )
      } else if (allowBackupNode != null) {
        val fix = createDataExtractionRulesFix(context, fullBackupNode)
        reportFromManifest(
          context,
          DATA_EXTRACTION_RULES,
          allowBackupNode,
          "The attribute `android:allowBackup` is deprecated from Android 12 and may be " +
            "removed in future versions. Consider adding the attribute `android:dataExtractionRules` " +
            "specifying an `@xml` resource which configures backups and device transfers on " +
            "Android 12 and higher.",
          LocationType.VALUE,
          fix,
        )
      }
      if (fullBackupNode != null) {
        val fix = createDataExtractionRulesFix(context, fullBackupNode)
        reportFromManifest(
          context,
          DATA_EXTRACTION_RULES,
          fullBackupNode,
          "The attribute `android:fullBackupContent` is deprecated from Android 12 and higher " +
            "and may be removed in future versions. Consider adding the attribute " +
            "`android:dataExtractionRules` specifying an `@xml` resource which configures backups " +
            "and device transfers on Android 12 and higher.",
          LocationType.VALUE,
          fix,
        )
      }
    }
    /*
    // TEMPORARILY DISABLED: According to the backup team, these flags are still consulted
    // for *cloud* backups. See b/181338786#comment28
    else if (min >= 31) {
        assert(dataExtractionRules != null)
        if (allowBackupNode != null) {
            reportFromManifest(
                context,
                DATA_EXTRACTION_RULES,
                allowBackupNode,
                "This attribute is unused; `dataExtractionRules` will take precedence since `minSdkVersion` is 31 or higher",
                LocationType.VALUE,
            )
        }
        if (fullBackupNode != null) {
            reportFromManifest(
                context,
                DATA_EXTRACTION_RULES,
                fullBackupNode,
                "This attribute is unused; `dataExtractionRules` will take precedence since `minSdkVersion` is 31 or higher",
                LocationType.VALUE,
            )
        }
    }
     */
  }

  private fun getExtraction(client: LintClient, xmlFile: File): String? {
    val parser = client.xmlParser
    val xml = xmlFile.readText()
    val root = parser.parseXml(xml, xmlFile)?.documentElement ?: return null
    val first = root.firstChild ?: return null
    val last = root.lastChild ?: return null
    val rootStart = parser.getNodeStartOffset(client, xmlFile, root)
    val firstStart = parser.getNodeStartOffset(client, xmlFile, first)
    val lastEnd = parser.getNodeEndOffset(client, xmlFile, last)
    if (rootStart == -1 || firstStart == -1 || lastEnd == -1) {
      return null
    }
    val removeAttributes = mutableListOf<Attr>()
    val clientSideEncryption =
      root.visitAttributes {
        it.name == "requireFlags" && it.value.contains("clientSideEncryption")
      }

    val prefix = xml.substring(0, rootStart)
    var childContent = xml.substring(firstStart, lastEnd)
    if (clientSideEncryption) {
      for (attr in removeAttributes.reversed()) {
        val start = parser.getNodeStartOffset(client, xmlFile, attr)
        val end = parser.getNodeEndOffset(client, xmlFile, attr)
        if (start != -1 && end != -1) {
          childContent =
            childContent.substring(0, start - firstStart) + childContent.substring(end - firstStart)
        }
      }
    }
    val indented = childContent.lines().joinToString("\n") { "    $it" }.removePrefix("    ")
    var descriptor =
      prefix +
        "<data-extraction-rules>\n    <cloud-backup" +
        (if (clientSideEncryption) " disableIfNoEncryptionCapabilities=\"true\"" else "") +
        ">\n" +
        indented.trimEnd().removePrefix("\n") +
        "\n    </cloud-backup>\n</data-extraction-rules>"

    // Re-parse our modified file to get up to date offsets and insert comments
    // in the D2D rules
    parser.parseXml(descriptor, xmlFile)?.documentElement?.let { doc ->
      val commentOut = mutableListOf<Element>()
      doc.visitAttributes {
        if (it.name == "requireFlags" && it.value.contains("deviceToDeviceTransfer")) {
          commentOut.add(it.ownerElement)
        }
        false
      }

      for (element in commentOut.reversed()) {
        val start = parser.getNodeStartOffset(client, xmlFile, element)
        val end = parser.getNodeEndOffset(client, xmlFile, element)
        if (start != -1 && end != -1) {
          descriptor =
            descriptor.substring(0, start) +
              "<!-- " +
              descriptor.substring(start, end) +
              " -->" +
              descriptor.substring(end)
        }
      }
    }

    return descriptor
  }

  @Language("XML")
  private fun getDataExtractionFileContent(context: Context, fullBackupNode: Attr?): String {
    // If there's a full backup node use and migrate it
    fullBackupNode
      ?.value
      ?.let { ResourceUrl.parse(it) }
      ?.let { url ->
        val client = context.client
        val project = context.project
        val resources = client.getResources(project, ResourceRepositoryScope.LOCAL_DEPENDENCIES)
        val item =
          resources.getResources(ResourceNamespace.TODO(), url.type, url.name).firstOrNull()?.source
        item?.toFile()?.let { file ->
          getExtraction(client, file)?.let {
            return it
          }
        }
      }

    // Fallback: no previous descriptor, or the descriptor couldn't be read or parsed; just use
    // a default template:

    @Suppress("UnnecessaryVariable") // here so we can annotate it with @Language("XML")
    @Language("XML")
    val descriptor =
      """
            <?xml version="1.0" encoding="utf-8"?>
            <!--
               Sample data extraction rules file; uncomment and customize as necessary.
               See https://developer.android.com/about/versions/12/backup-restore#xml-changes
               for details.
            -->
            <data-extraction-rules>
                <cloud-backup>
                    <!--
                    TODO: Use <include> and <exclude> to control what is backed up.
                    The domain can be file, database, sharedpref, external or root.
                    Examples:

                    <include domain="file" path="file_to_include"/>
                    <exclude domain="file" path="file_to_exclude"/>
                    <include domain="file" path="include_folder"/>
                    <exclude domain="file" path="include_folder/file_to_exclude"/>
                    <exclude domain="file" path="exclude_folder"/>
                    <include domain="file" path="exclude_folder/file_to_include"/>

                    <include domain="sharedpref" path="include_shared_pref1.xml"/>
                    <include domain="database" path="db_name/file_to_include"/>
                    <exclude domain="database" path="db_name/include_folder/file_to_exclude"/>
                    <include domain="external" path="file_to_include"/>
                    <exclude domain="external" path="file_to_exclude"/>
                    <include domain="root" path="file_to_include"/>
                    <exclude domain="root" path="file_to_exclude"/>
                    -->
                </cloud-backup>
                <!--
                <device-transfer>
                    <include .../>
                    <exclude .../>
                </device-transfer>
                -->
            </data-extraction-rules>
            """
        .trimIndent()

    return descriptor
  }

  private fun createDataExtractionRulesFix(context: Context, fullBackupNode: Attr?): LintFix? {
    val project = context.project
    val folder = project.resourceFolders.firstOrNull() ?: return null
    val name = "data_extraction_rules"
    val file = File(folder, "xml$separator$name$DOT_XML")
    if (file.exists()) {
      return null
    }

    val descriptor = getDataExtractionFileContent(context, fullBackupNode)
    val select =
      if (descriptor.contains("TODO:")) " ()TODO:"
      else if (descriptor.contains("<include")) "()<include" else "()"
    val createFix = fix().newFile(file, descriptor).select(select).build()
    val setAttributeFix = fix().set(ANDROID_URI, ATTR_DATA_EXTRACTION_RULES, "@xml/$name").build()
    return fix().name("Create $name.xml").composite(createFix, setAttributeFix)
  }

  private fun checkIcon(application: Element, context: Context) {
    if (
      !application.hasAttributeNS(ANDROID_URI, ATTR_ICON) && context.isEnabled(APPLICATION_ICON)
    ) {
      val fix = fix().set(ANDROID_URI, ATTR_ICON, "@mipmap/").caretEnd().build()
      reportFromManifest(
        context,
        APPLICATION_ICON,
        application,
        "Should explicitly set `android:icon`, there is no default",
        LocationType.NAME,
        fix,
      )
    }
  }

  private fun checkDocumentElement(context: XmlContext, element: Element) {
    val codeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_VERSION_CODE)
    if (
      codeNode != null &&
        codeNode.value.startsWith(PREFIX_RESOURCE_REF) &&
        context.isEnabled(ILLEGAL_REFERENCE)
    ) {
      context.report(
        ILLEGAL_REFERENCE,
        element,
        context.getLocation(codeNode),
        "The `android:versionCode` cannot be a resource url, it must be " + "a literal integer",
      )
    } else if (
      codeNode == null &&
        context.isEnabled(SET_VERSION) &&
        !context.project.isLibrary &&
        // Not required in Gradle projects; typically defined in build.gradle instead
        // and inserted at build time
        !context.project.isGradleProject
    ) {
      val fix = fix().set().todo(ANDROID_URI, ATTR_VERSION_CODE).build()
      context.report(
        SET_VERSION,
        element,
        context.getNameLocation(element),
        "Should set `android:versionCode` to specify the application version",
        fix,
      )
    }
    val nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_VERSION_NAME)
    if (
      nameNode == null &&
        context.isEnabled(SET_VERSION) &&
        !context.project.isLibrary &&
        // Not required in Gradle projects; typically defined in build.gradle instead
        // and inserted at build time

        !context.project.isGradleProject
    ) {
      val fix = fix().set().todo(ANDROID_URI, ATTR_VERSION_NAME).build()
      context.report(
        SET_VERSION,
        element,
        context.getNameLocation(element),
        "Should set `android:versionName` to specify the application version",
        fix,
      )
    }
    val pkgNode = element.getAttributeNode(ATTR_PACKAGE)
    if (pkgNode != null) {
      val pkg = pkgNode.value
      if (pkg.contains("\${") && context.project.isGradleProject) {
        context.report(
          GRADLE_OVERRIDES,
          pkgNode,
          context.getLocation(pkgNode),
          "Cannot use placeholder for the package in the manifest; " +
            "set `applicationId` in `build.gradle` instead",
        )
      }
    }
  }

  private fun reportFromManifest(
    context: Context,
    issue: Issue,
    node: Node?,
    message: String,
    type: LocationType,
    fix: LintFix? = null,
  ) {
    val location = context.getLocation(node, type)
    if (location.start == null) {
      // Couldn't find a specific location in the merged manifest. That means
      // that none of the manifests contained this tag -- which means
      // we're reporting an issue on <application> in an Android project
      // with no <application>; we don't want that.
      return
    }
    val incident = Incident(issue, message, location, fix)
    context.report(incident)
    if (context.isGlobalAnalysis()) {
      incident.project(context.mainProject)
    }
  }

  private fun checkOverride(context: XmlContext, element: Element, attributeName: String) {
    val project = context.project
    val attribute = element.getAttributeNodeNS(ANDROID_URI, attributeName)
    if (attribute != null && context.isEnabled(GRADLE_OVERRIDES)) {
      val variant = project.buildVariant
      if (variant != null) {
        val gradleValue =
          when {
            ATTR_MIN_SDK_VERSION == attributeName -> {
              if (element.hasAttributeNS(TOOLS_URI, "overrideLibrary")) {
                // The manifest may be setting a minSdkVersion here to deliberately
                // let the manifest merger know that a library dependency's manifest
                // with a higher value is okay: this value wins. The manifest merger
                // should really be taking the Gradle file into account instead,
                // but for now we filter these out; http://b.android.com/186762
                return
              }
              val minSdkVersion = variant.minSdkVersion
              minSdkVersion?.apiString
            }
            ATTR_TARGET_SDK_VERSION == attributeName -> {
              val targetSdkVersion = variant.targetSdkVersion
              targetSdkVersion?.apiString
            }
            else -> {
              assert(false) { attributeName }
              return
            }
          }
        if (gradleValue != null) {
          val manifestValue = attribute.value
          val message =
            "This `$attributeName` value (`$manifestValue`) is not used; it is " +
              "always overridden by the value specified in the Gradle build " +
              "script (`$gradleValue`)"
          context.report(GRADLE_OVERRIDES, attribute, context.getLocation(attribute), message)
        }
      }
    }
  }

  // ---- Implements XmlScanner ----

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      TAG_APPLICATION,
      TAG_USES_PERMISSION,
      TAG_PERMISSION,
      "permission-tree",
      "permission-group",
      TAG_USES_SDK,
      "uses-configuration",
      TAG_USES_FEATURE,
      "supports-screens",
      "compatible-screens",
      "supports-gl-texture",
      TAG_USES_LIBRARY,
      TAG_ACTIVITY,
      TAG_SERVICE,
      TAG_PROVIDER,
      TAG_RECEIVER,
    )
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val tag = element.tagName
    val parentNode = element.parentNode
    val isReceiver = tag == TAG_RECEIVER
    if (isReceiver) {
      checkDeviceAdmin(context, element)
    }
    if (tag == TAG_USES_LIBRARY || tag == TAG_ACTIVITY || tag == TAG_SERVICE || isReceiver) {
      if (TAG_APPLICATION != parentNode.nodeName && context.isEnabled(WRONG_PARENT)) {
        context.report(
          WRONG_PARENT,
          element,
          context.getNameLocation(element),
          "The `<$tag>` element must be a direct child of the <application> element",
        )
      }
      if (tag == TAG_ACTIVITY) {
        val nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
        if (nameNode != null) {
          var name = nameNode.value
          if (name.isNotEmpty()) {
            val pkg =
              context.document.documentElement.getAttributeNode(ATTR_PACKAGE)?.value
                ?: context.project.getPackage()
            if (name[0] == '.') {
              name = pkg + name
            } else if (name.indexOf('.') == -1) {
              name = "$pkg.$name"
            }
            val activities = this.activities ?: mutableSetOf<String>().also { this.activities = it }
            if (!activities.add(name)) {
              val message = "Duplicate registration for activity `$name`"
              context.report(DUPLICATE_ACTIVITY, element, context.getLocation(nameNode), message)
            }
          }
        }
        checkMipmapIcon(context, element)
        checkLabel(context, element)
      } else if (tag == TAG_SERVICE && context.project.isGradleProject) {
        if (context.project.targetSdk >= 26) {
          for (child in XmlUtils.getSubTagsByName(element, TAG_INTENT_FILTER)) {
            for (innerChild in XmlUtils.getSubTagsByName(child, AndroidManifest.NODE_ACTION)) {
              val attr = innerChild.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
              if (attr != null && "com.google.firebase.appindexing.UPDATE_INDEX" == attr.value) {
                val message =
                  "`UPDATE_INDEX` is configured as a service in your app, " +
                    "which is no longer supported for the API level you're targeting. " +
                    "Use a `BroadcastReceiver` instead."
                val incident =
                  Incident(APP_INDEXING_SERVICE, attr, context.getLocation(attr), message)
                context.report(incident, targetSdkAtLeast(26))
                break
              }
            }
          }
        }
        var bindListenerAttr: Attr? = null
        for (child in XmlUtils.getSubTagsByName(element, TAG_INTENT_FILTER)) {
          for (innerChild in XmlUtils.getSubTagsByName(child, AndroidManifest.NODE_ACTION)) {
            val attr = innerChild.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            if (attr != null && "com.google.android.gms.wearable.BIND_LISTENER" == attr.value) {
              bindListenerAttr = attr
              break
            }
          }
        }
        if (bindListenerAttr == null) {
          return
        }
        // Ensure that the play-services-wearable version dependency is >= 8.2.0
        val variant = context.project.buildVariant
        if (variant != null && hasWearableGmsDependency(variant)) {
          context.report(
            WEARABLE_BIND_LISTENER,
            bindListenerAttr,
            context.getLocation(bindListenerAttr),
            "The `com.google.android.gms.wearable.BIND_LISTENER`" + " action is deprecated",
          )
          return
        }

        // It's possible they are using an older version of play services so
        // check the build version and report an error if compileSdkVersion >= 24
        val sdkHome = context.client.getSdkHome()
        if (context.project.buildSdk >= 24 && sdkHome != null) {
          val repository = SdkMavenRepository.GOOGLE.getRepositoryLocation(sdkHome.toPath(), true)
          var message =
            "The `com.google.android.gms.wearable.BIND_LISTENER`" +
              " action is deprecated. Please upgrade to the latest version" +
              " of play-services-wearable 8.2.0 or later"
          if (repository != null) {
            val max =
              MavenRepositories.getHighestInstalledVersion(
                GradleDetector.GMS_GROUP_ID,
                "play-services-wearable",
                repository,
                null,
                false,
              )
            if (max != null && max.version > MIN_WEARABLE_GMS_VERSION) {
              message =
                "The `com.google.android.gms.wearable.BIND_LISTENER` " +
                  "action is deprecated. Please upgrade to the latest available" +
                  " version of play-services-wearable: `${max.version}`"
            }
          }
          val location = context.getLocation(bindListenerAttr)
          context.report(WEARABLE_BIND_LISTENER, bindListenerAttr, location, message)
        }
      }
      return
    }
    if (tag == TAG_PROVIDER) {
      if (
        TAG_APPLICATION != parentNode.nodeName &&
          TAG_QUERIES != parentNode.nodeName &&
          context.isEnabled(WRONG_PARENT)
      ) {
        context.report(
          WRONG_PARENT,
          element,
          context.getNameLocation(element),
          "The `<$tag>` element must be a direct child of the `<application>` element or the `<queries>` element",
        )
      }
      return
    }
    if (
      parentNode !== element.ownerDocument.documentElement &&
        tag.indexOf(':') == -1 &&
        context.isEnabled(WRONG_PARENT)
    ) {
      context.report(
        WRONG_PARENT,
        element,
        context.getNameLocation(element),
        "The `<$tag>` element must be a direct child of the `<manifest>` root element",
      )
    }
    if (tag == TAG_USES_SDK) {
      seenUsesSdk++
      if (seenUsesSdk == 2) { // Only warn when we encounter the first one
        val location = context.getNameLocation(element)

        // Link up *all* encountered locations in the document
        val elements = element.ownerDocument.getElementsByTagName(TAG_USES_SDK)
        var secondary: Location? = null
        for (i in elements.length - 1 downTo 0) {
          val e = elements.item(i) as Element
          if (e !== element) {
            val l = context.getNameLocation(e)
            l.secondary = secondary
            l.message = "Also appears here"
            secondary = l
          }
        }
        location.secondary = secondary
        if (context.isEnabled(MULTIPLE_USES_SDK)) {
          context.report(
            MULTIPLE_USES_SDK,
            element,
            location,
            "There should only be a single `<uses-sdk>` element in the manifest:" +
              " merge these together",
          )
        }
        return
      }
      if (element.hasAttributeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)) {
        val codeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_MIN_SDK_VERSION)
        if (
          codeNode != null &&
            codeNode.value.startsWith(PREFIX_RESOURCE_REF) &&
            context.isEnabled(ILLEGAL_REFERENCE)
        ) {
          context.report(
            ILLEGAL_REFERENCE,
            element,
            context.getLocation(codeNode),
            "The `android:minSdkVersion` cannot be a resource url, it must be " +
              "a literal integer (or string if a preview codename)",
          )
        }
        checkOverride(context, element, ATTR_MIN_SDK_VERSION)
      }
      if (element.hasAttributeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)) {
        checkOverride(context, element, ATTR_TARGET_SDK_VERSION)
      }
      val nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_TARGET_SDK_VERSION)
      if (
        nameNode != null &&
          nameNode.value.startsWith(PREFIX_RESOURCE_REF) &&
          context.isEnabled(ILLEGAL_REFERENCE)
      ) {
        context.report(
          ILLEGAL_REFERENCE,
          element,
          context.getLocation(nameNode),
          "The `android:targetSdkVersion` cannot be a resource url, it must be " +
            "a literal integer (or string if a preview codename)",
        )
      }
    }
    if (tag == TAG_PERMISSION || tag == TAG_PERMISSION_GROUP) {
      // Outside of the IDE we'll do this in processMergedProject instead at reporting time
      if (context.isGlobalAnalysis()) {
        ensureUniquePermission(context)
      }
    }
    if (tag == TAG_USES_PERMISSION) {
      val name = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
      if (
        name != null &&
          name.value == MOCK_LOCATION_PERMISSION &&
          context.project.buildModule != null &&
          !isDebugOrTestManifest(context, context.file) &&
          context.isEnabled(MOCK_LOCATION)
      ) {
        val message =
          ("Mock locations should only be requested in a test or " +
            "debug-specific manifest file (typically `src/debug/AndroidManifest.xml`)")
        val location = context.getLocation(name)
        context.report(MOCK_LOCATION, element, location, message)
      }
    }
    if (tag == TAG_APPLICATION) {
      seenApplication = true
      if (element.hasAttributeNS(ANDROID_URI, ATTR_ICON)) {
        checkMipmapIcon(context, element)
      }
      if (onlyCheckSourceManifest(context)) {
        checkApplication(context, element)
      }
    } else if (seenApplication) {
      if (context.isEnabled(ORDER)) {
        context.report(
          ORDER,
          element,
          context.getNameLocation(element),
          "`<$tag>` tag appears after `<application>` tag",
        )
      }

      // Don't complain for *every* element following the <application> tag
      seenApplication = false
    }
    if (tag == TAG_USES_FEATURE) {
      val nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
      if (nameNode != null) {
        val name = nameNode.value
        if (name.isNotEmpty()) {
          val usesFeatures =
            this.usesFeatures ?: mutableSetOf<String>().also { this.usesFeatures = it }
          if (!usesFeatures.add(name)) {
            val message = "Duplicate declaration of uses-feature `$name`"
            context.report(DUPLICATE_USES_FEATURE, element, context.getLocation(nameNode), message)
          }
        }
      }
    }
  }

  private var checkedUniquePermissions = false

  private fun ensureUniquePermission(context: Context) {
    // Only check this for the first encountered manifest permission tag; it will consult
    // the merged manifest to perform a global check and report errors it finds, so we don't
    // need to repeat that for each sibling permission element
    if (checkedUniquePermissions) {
      return
    }
    checkedUniquePermissions = true
    val mainProject = context.mainProject
    val mergedManifest =
      mainProject
        .mergedManifest // This only happens when there is a parse error, for example if user
        // is editing the manifest in the IDE and it's currently invalid
        ?: return
    lookForNonUniqueNames(context, mainProject, mergedManifest, "permission", TAG_PERMISSION)
    lookForNonUniqueNames(
      context,
      mainProject,
      mergedManifest,
      "permission group",
      TAG_PERMISSION_GROUP,
    )
  }

  override fun checkMergedProject(context: Context) {
    ensureUniquePermission(context)
    checkApplication(context)
  }

  private fun lookForNonUniqueNames(
    context: Context,
    mainProject: Project,
    mergedManifest: Document,
    humanReadableName: String,
    tagName: String,
  ) {
    var nameToFull: MutableMap<String, String>? = null
    val root = mergedManifest.documentElement ?: return
    for (element in root) {
      if (element.tagName != tagName || manifestMergerSkips(element)) {
        continue
      }
      val nameNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: continue
      var name = nameNode.value
      val base = name.substring(name.lastIndexOf('.') + 1)
      val pkg =
        mergedManifest.documentElement.getAttributeNode(ATTR_PACKAGE)?.value
          ?: mainProject.getPackage()

      if (!mainProject.isLibrary && pkg != null && name.contains("\${applicationId}")) {
        name = name.replace("\${applicationId}", pkg)
      }
      if (name.contains("\${")) {
        // Unknown manifest placeholder: don't try to enforce uniqueness; we don't
        // know whether the values turn out to be identical
        continue
      }

      val map: MutableMap<String, String> =
        if (nameToFull != null) {
          if (nameToFull.containsKey(base) && name != nameToFull[base]) {
            val prevName = nameToFull[base]
            val location = context.getLocation(nameNode, LocationType.ALL)
            val siblings = element.parentNode.childNodes
            var i = 0
            val n = siblings.length
            while (i < n) {
              val node = siblings.item(i)
              if (node === element) {
                break
              } else if (node.nodeType == Node.ELEMENT_NODE) {
                val sibling = node as Element
                if (sibling.tagName == tagName) {
                  if (prevName == sibling.getAttributeNS(ANDROID_URI, ATTR_NAME)) {
                    val no = sibling.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
                    val prevLocation = context.getLocation(no, LocationType.VALUE)
                    prevLocation.message = "Previous $humanReadableName here"
                    location.secondary = prevLocation
                    break
                  }
                }
              }
              i++
            }
            val message =
              "${humanReadableName.usLocaleCapitalize()} name `$base` is not unique (appears in both `$prevName` and `$name`)"
            val incident = Incident(UNIQUE_PERMISSION, element, location, message)
            context.report(incident)
            if (context.isGlobalAnalysis()) {
              incident.project(context.mainProject)
            }
          }
          nameToFull
        } else {
          mutableMapOf<String, String>().also { nameToFull = it }
        }
      map[base] = name
    }
  }

  /**
   * Returns true if the manifest merger will skip this element due to a tools:node action attribute
   */
  private fun manifestMergerSkips(element: Element): Boolean {
    val operation = element.getAttributeNodeNS(TOOLS_URI, "node")
    if (operation != null) {
      val action = operation.value
      if (action.startsWith("remove") || action == "replace") {
        return true
      }
    }
    return false
  }

  // Method to check if the app has a gms wearable dependency that
  // matches the specific criteria i.e >= MIN_WEARABLE_GMS_VERSION
  private fun hasWearableGmsDependency(variant: LintModelVariant): Boolean {
    val library =
      variant.artifact.findCompileDependency(PLAY_SERVICES_WEARABLE) as? LintModelExternalLibrary
        ?: return false
    val mc = library.resolvedCoordinates
    val version = Version.parse(mc.version)
    return version >= MIN_WEARABLE_GMS_VERSION
  }

  private fun checkMipmapIcon(context: XmlContext, element: Element) {
    val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_ICON) ?: return
    val icon = attribute.value
    if (icon.startsWith(DRAWABLE_PREFIX)) {
      if (TAG_ACTIVITY == element.tagName && !isLaunchableActivity(element)) {
        return
      }
      if (
        context.isEnabled(MIPMAP) && // Only complain if this app is skipping some densities
          context.project.applicableDensities != null
      ) {
        context.report(
          MIPMAP,
          element,
          context.getLocation(attribute),
          "Should use `@mipmap` instead of `@drawable` for launcher icons",
        )
      }
    }
  }

  private fun checkLabel(context: XmlContext, activity: Element) {
    val labelAttribute = activity.getAttributeNodeNS(ANDROID_URI, ATTR_LABEL) ?: return
    val applicationElement = activity.parentNode as? Element ?: return
    if (applicationElement.nodeName != TAG_APPLICATION) return
    val applicationLabel = applicationElement.getAttributeNS(ANDROID_URI, ATTR_LABEL) ?: return
    if (labelAttribute.value == applicationLabel) {
      val fix = fix().unset(ANDROID_URI, ATTR_LABEL).build()
      context.report(
        REDUNDANT_LABEL,
        context.getLocation(labelAttribute),
        "Redundant label can be removed",
        fix,
      )
    }
  }

  /**
   * Returns true iff the given manifest file is in a debug-specific source set, or a test source
   * set
   */
  private fun isDebugOrTestManifest(context: XmlContext, manifestFile: File): Boolean {
    val variant = context.project.buildVariant
    if (variant != null) {
      for (provider in variant.sourceProviders) {
        if (provider.isDebugOnly() || provider.isTest()) {
          //noinspection FileComparisons
          if (provider.manifestFiles.any { it == manifestFile }) {
            return true
          }
        }
      }
    }
    return false
  }

  private fun checkDeviceAdmin(context: XmlContext, element: Element) {
    var requiredIntentFilterFound = false
    var deviceAdmin = false
    var locationNode: Attr? = null
    for (child in XmlUtils.getSubTags(element)) {
      val tagName = child.tagName
      if (tagName == TAG_INTENT_FILTER && !requiredIntentFilterFound) {
        var dataFound = false
        var actionFound = false
        for (filterChild in XmlUtils.getSubTags(child)) {
          val filterTag = filterChild.tagName
          if (filterTag == AndroidManifest.NODE_ACTION) {
            val name = filterChild.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if ("android.app.action.DEVICE_ADMIN_ENABLED" == name) {
              actionFound = true
            }
          } else if (filterTag == AndroidManifest.NODE_DATA) {
            dataFound = true
          }
        }
        if (actionFound && !dataFound) {
          requiredIntentFilterFound = true
        }
      } else if (tagName == AndroidManifest.NODE_METADATA) {
        val valueNode = child.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
        if (valueNode != null) {
          val name = valueNode.value
          if ("android.app.device_admin" == name) {
            deviceAdmin = true
            locationNode = valueNode
          }
        }
      }
    }
    if (deviceAdmin && !requiredIntentFilterFound && context.isEnabled(DEVICE_ADMIN)) {
      context.report(
        DEVICE_ADMIN,
        locationNode,
        context.getLocation(locationNode),
        "You must have an intent filter for action " + "`android.app.action.DEVICE_ADMIN_ENABLED`",
      )
    }
  }
}
