/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License",;
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

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.LintClient.Companion.isStudio
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.google.common.annotations.VisibleForTesting
import java.util.Collections.unmodifiableList
import java.util.EnumSet

/** Registry which provides a list of checks to be performed on an Android project. */
open class BuiltinIssueRegistry : IssueRegistry() {

  override val vendor: Vendor = AOSP_VENDOR

  companion object {
    /**
     * Reset the registry such that it recomputes its available issues.
     *
     * NOTE: This is only intended for testing purposes.
     */
    @VisibleForTesting
    @JvmStatic
    fun reset() {
      IssueRegistry.reset()
    }

    private val builtinIssues: List<Issue> =
      unmodifiableList(
        listOf(
          AccessibilityDetector.ISSUE,
          AccessibilityForceFocusDetector.ISSUE,
          AccessibilityViewScrollActionsDetector.ISSUE,
          AccessibilityWindowStateChangedDetector.ISSUE,
          ActionsXmlDetector.ISSUE,
          ActivityIconColorDetector.ISSUE,
          AddJavascriptInterfaceDetector.ISSUE,
          AlarmDetector.EXACT_ALARM,
          AlarmDetector.SCHEDULE_EXACT_ALARM,
          AlarmDetector.SHORT_ALARM,
          AllCapsDetector.ISSUE,
          AllowAllHostnameVerifierDetector.ISSUE,
          AlwaysShowActionDetector.ISSUE,
          AndroidAutoDetector.INVALID_USES_TAG_ISSUE,
          AndroidAutoDetector.MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH,
          AndroidAutoDetector.MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE,
          AndroidAutoDetector.MISSING_ON_PLAY_FROM_SEARCH,
          AndroidTvDetector.IMPLIED_TOUCHSCREEN_HARDWARE,
          AndroidTvDetector.MISSING_BANNER,
          AndroidTvDetector.MISSING_LEANBACK_LAUNCHER,
          AndroidTvDetector.MISSING_LEANBACK_SUPPORT,
          AndroidTvDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
          AndroidTvDetector.UNSUPPORTED_TV_HARDWARE,
          AnnotationDetector.ANNOTATION_USAGE,
          AnnotationDetector.FLAG_STYLE,
          AnnotationDetector.INSIDE_METHOD,
          AnnotationDetector.SWITCH_TYPE_DEF,
          AnnotationDetector.UNIQUE,
          AnnotationDetector.USE_REQUIRES_API,
          ApiDetector.INLINED,
          ApiDetector.OBSOLETE_SDK,
          ApiDetector.NIO_DESUGARING,
          ApiDetector.UNSUPPORTED,
          ApiDetector.UNUSED,
          ApiDetector.WRONG_SDK_INT,
          AppBundleLocaleChangesDetector.ISSUE,
          AppCompatCallDetector.ISSUE,
          AppCompatCustomViewDetector.ISSUE,
          AppCompatResourceDetector.ISSUE,
          AppLinksAutoVerifyDetector.ISSUE,
          AppLinksValidDetector.APP_LINK_WARNING,
          AppLinksValidDetector.APP_LINK_SPLIT_TO_WEB_AND_CUSTOM,
          AppLinksValidDetector.INTENT_FILTER_UNIQUE_DATA_ATTRIBUTES,
          AppLinksValidDetector.TEST_URL,
          AppLinksValidDetector.URI_RELATIVE_FILTER_GROUP,
          AppLinksValidDetector.VALIDATION,
          ArrayInitDetector.ISSUE,
          ArraySizeDetector.INCONSISTENT,
          AssertDetector.EXPENSIVE,
          AssertDetector.SIDE_EFFECT,
          AutofillDetector.ISSUE,
          BadHostnameVerifierDetector.ISSUE,
          BatteryDetector.ISSUE,
          BidirectionalTextDetector.BIDI_SPOOFING,
          BinderGetCallingInMainThreadDetector.ISSUE,
          BottomAppBarDetector.ISSUE,
          BuildListDetector.ISSUE,
          ButtonDetector.BACK_BUTTON,
          ButtonDetector.CASE,
          ButtonDetector.ORDER,
          ButtonDetector.STYLE,
          ByteOrderMarkDetector.BOM,
          C2dmDetector.ISSUE,
          CallSuperDetector.ISSUE,
          CanvasSizeDetector.ISSUE,
          CheckResultDetector.CHECK_PERMISSION,
          CheckResultDetector.CHECK_RESULT,
          ChildCountDetector.ADAPTER_VIEW_ISSUE,
          ChildCountDetector.SCROLLVIEW_ISSUE,
          ChildInNonViewGroupDetector.CHILD_IN_NON_VIEW_GROUP_ISSUE,
          ChromeOsDetector.NON_RESIZEABLE_ACTIVITY,
          ChromeOsDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE,
          ChromeOsDetector.SETTING_ORIENTATION_ON_ACTIVITY,
          ChromeOsDetector.UNSUPPORTED_CHROME_OS_HARDWARE,
          ChromeOsSourceDetector.CHROMEOS_ON_CONFIGURATION_CHANGED,
          ChromeOsSourceDetector.UNSUPPORTED_CAMERA_FEATURE,
          ChromeOsSourceDetector.UNSUPPORTED_LOCKED_ORIENTATION,
          CipherGetInstanceDetector.DEPRECATED_PROVIDER,
          CipherGetInstanceDetector.ISSUE,
          CleanupDetector.APPLY_SHARED_PREF,
          CleanupDetector.COMMIT_FRAGMENT,
          CleanupDetector.RECYCLE_RESOURCE,
          CleanupDetector.SHARED_PREF,
          ClickableViewAccessibilityDetector.ISSUE,
          CommentDetector.EASTER_EGG,
          CommentDetector.STOP_SHIP,
          CommunicationDeviceDetector.ISSUE,
          ConstraintLayoutDetector.ISSUE,
          CordovaVersionDetector.ISSUE,
          CredentialManagerDependencyDetector.CREDENTIAL_DEP,
          CredentialManagerDigitalAssetLinkDetector.ISSUE,
          CredentialManagerMisuseDetector.ISSUE,
          CredentialManagerSignInWithGoogleDetector.ISSUE,
          CustomViewDetector.ISSUE,
          CutPasteDetector.ISSUE,
          DataBindingDetector.ESCAPE_XML,
          DateFormatDetector.DATE_FORMAT,
          DateFormatDetector.WEEK_YEAR,
          DefaultEncodingDetector.ISSUE,
          DeletedProviderDetector.ISSUE,
          DeprecatedSinceApiDetector.ISSUE,
          DeprecationDetector.ISSUE,
          DiffUtilDetector.ISSUE,
          DiscouragedDetector.ISSUE,
          DosLineEndingDetector.ISSUE,
          DuplicateIdDetector.CROSS_LAYOUT,
          DuplicateIdDetector.WITHIN_LAYOUT,
          DuplicateResourceDetector.ISSUE,
          DuplicateResourceDetector.TYPE_MISMATCH,
          EllipsizeMaxLinesDetector.ISSUE,
          EmptySuperDetector.ISSUE,
          ExifInterfaceDetector.ISSUE,
          ExportedFlagDetector.ISSUE,
          ExtraTextDetector.ISSUE,
          FileEndsWithDetector.ISSUE,
          FineLocationDetector.ISSUE,
          FirebaseAnalyticsDetector.INVALID_NAME,
          FirebaseMessagingDetector.MISSING_TOKEN_REFRESH,
          FontDetector.FONT_VALIDATION,
          ForegroundServicePermissionDetector.ISSUE_PERMISSION,
          ForegroundServiceTypesDetector.ISSUE_TYPE,
          FragmentDetector.ISSUE,
          FullBackupContentDetector.ISSUE,
          GestureBackNavDetector.ISSUE,
          GetContentDescriptionOverrideDetector.ISSUE,
          GradleDetector.ACCIDENTAL_OCTAL,
          GradleDetector.AGP_DEPENDENCY,
          GradleDetector.ANNOTATION_PROCESSOR_ON_COMPILE_PATH,
          GradleDetector.BOM_WITHOUT_PLATFORM,
          GradleDetector.BUNDLED_GMS,
          GradleDetector.CHROMEOS_ABI_SUPPORT,
          GradleDetector.COMPATIBILITY,
          GradleDetector.CORE_LIB_DESUGARING_V2,
          GradleDetector.DATA_BINDING_WITHOUT_KAPT,
          GradleDetector.DEPENDENCY,
          GradleDetector.DEPRECATED,
          GradleDetector.DEPRECATED_CONFIGURATION,
          GradleDetector.DEPRECATED_LIBRARY,
          GradleDetector.DEV_MODE_OBSOLETE,
          GradleDetector.DUPLICATE_CLASSES,
          GradleDetector.EDITED_TARGET_SDK_VERSION,
          GradleDetector.EXPIRED_TARGET_SDK_VERSION,
          GradleDetector.EXPIRING_TARGET_SDK_VERSION,
          GradleDetector.GRADLE_GETTER,
          GradleDetector.GRADLE_PLUGIN_COMPATIBILITY,
          GradleDetector.HIGH_APP_VERSION_CODE,
          GradleDetector.IDE_SUPPORT,
          GradleDetector.JAVA_PLUGIN_LANGUAGE_LEVEL,
          GradleDetector.JCENTER_REPOSITORY_OBSOLETE,
          GradleDetector.KAPT_USAGE_INSTEAD_OF_KSP,
          GradleDetector.KTX_EXTENSION_AVAILABLE,
          GradleDetector.LIFECYCLE_ANNOTATION_PROCESSOR_WITH_JAVA8,
          GradleDetector.MIN_SDK_TOO_LOW,
          GradleDetector.MULTIPLE_VERSIONS_DEPENDENCY,
          GradleDetector.NOT_INTERPOLATED,
          GradleDetector.PATH,
          GradleDetector.PLAY_SDK_INDEX_DEPRECATED,
          GradleDetector.PLAY_SDK_INDEX_GENERIC_ISSUES,
          GradleDetector.PLAY_SDK_INDEX_NON_COMPLIANT,
          GradleDetector.PLAY_SDK_INDEX_VULNERABILITY,
          GradleDetector.PLUS,
          GradleDetector.REMOTE_VERSION,
          GradleDetector.RISKY_LIBRARY,
          GradleDetector.STRING_INTEGER,
          GradleDetector.SWITCH_TO_TOML,
          GradleDetector.TARGET_NEWER,
          GridLayoutDetector.ISSUE,
          HandlerDetector.ISSUE,
          HardcodedDebugModeDetector.ISSUE,
          HardcodedValuesDetector.ISSUE,
          HardwareIdDetector.ISSUE,
          HighSensorSamplingRateDetector.ISSUE,
          IconDetector.DUPLICATES_CONFIGURATIONS,
          IconDetector.DUPLICATES_NAMES,
          IconDetector.GIF_USAGE,
          IconDetector.ICON_COLORS,
          IconDetector.ICON_DENSITIES,
          IconDetector.ICON_DIP_SIZE,
          IconDetector.ICON_EXPECTED_SIZE,
          IconDetector.ICON_EXTENSION,
          IconDetector.ICON_LAUNCHER_SHAPE,
          IconDetector.ICON_LOCATION,
          IconDetector.ICON_MISSING_FOLDER,
          IconDetector.ICON_MIX_9PNG,
          IconDetector.ICON_NODPI,
          IconDetector.ICON_XML_AND_PNG,
          IconDetector.NOTIFICATION_ICON_COMPATIBILITY,
          IconDetector.WEBP_ELIGIBLE,
          IconDetector.WEBP_UNSUPPORTED,
          IgnoreWithoutReasonDetector.ISSUE,
          IncludeDetector.ISSUE,
          IndentationDetector.ISSUE,
          InefficientWeightDetector.BASELINE_WEIGHTS,
          InefficientWeightDetector.INEFFICIENT_WEIGHT,
          InefficientWeightDetector.NESTED_WEIGHTS,
          InefficientWeightDetector.ORIENTATION,
          InefficientWeightDetector.WRONG_0DP,
          InstantAppDetector.ISSUE,
          IntentDetector.ISSUE,
          IntentWillNullActionDetector.ISSUE,
          InternalInsetResourceDetector.ISSUE,
          InteroperabilityDetector.KOTLIN_PROPERTY,
          InteroperabilityDetector.LAMBDA_LAST,
          InteroperabilityDetector.NO_HARD_KOTLIN_KEYWORDS,
          InteroperabilityDetector.PLATFORM_NULLNESS,
          InvalidImeActionIdDetector.ISSUE,
          InvalidNotificationIdDetector.ISSUE,
          InvalidPackageDetector.ISSUE,
          ItemDecoratorDetector.ISSUE,
          IteratorDetector.ISSUE,
          JavaPerformanceDetector.PAINT_ALLOC,
          JavaPerformanceDetector.USE_SPARSE_ARRAY,
          JavaPerformanceDetector.USE_VALUE_OF,
          JavaScriptInterfaceDetector.ISSUE,
          JobSchedulerDetector.ISSUE,
          KeyboardNavigationDetector.ISSUE,
          KotlinNullnessAnnotationDetector.ISSUE,
          KotlincFE10Detector.ISSUE,
          LabelForDetector.ISSUE,
          LayoutConsistencyDetector.INCONSISTENT_IDS,
          LayoutInflationDetector.ISSUE,
          LeakDetector.ISSUE,
          LeanbackWifiUsageDetector.ISSUE,
          LintDetectorDetector.CHECK_URL,
          LintDetectorDetector.DOLLAR_STRINGS,
          LintDetectorDetector.ID,
          LintDetectorDetector.MISSING_DOC_EXAMPLE,
          LintDetectorDetector.PSI_COMPARE,
          LintDetectorDetector.TEXT_FORMAT,
          LintDetectorDetector.TRIM_INDENT,
          LintDetectorDetector.UNEXPECTED_DOMAIN,
          LintDetectorDetector.USE_KOTLIN,
          LintDetectorDetector.USE_UAST,
          LocaleConfigDetector.ISSUE,
          LocaleDetector.FINAL_LOCALE,
          LocaleDetector.STRING_LOCALE,
          LocaleFolderDetector.DEPRECATED_CODE,
          LocaleFolderDetector.GET_LOCALES,
          LocaleFolderDetector.INVALID_FOLDER,
          LocaleFolderDetector.USE_ALPHA_2,
          LocaleFolderDetector.WRONG_REGION,
          LogDetector.CONDITIONAL,
          LogDetector.LONG_TAG,
          LogDetector.WRONG_TAG,
          ManifestDetector.APP_INDEXING_SERVICE,
          ManifestDetector.APPLICATION_ICON,
          ManifestDetector.DATA_EXTRACTION_RULES,
          ManifestDetector.DEVICE_ADMIN,
          ManifestDetector.DUPLICATE_ACTIVITY,
          ManifestDetector.DUPLICATE_USES_FEATURE,
          ManifestDetector.GRADLE_OVERRIDES,
          ManifestDetector.ILLEGAL_REFERENCE,
          ManifestDetector.MIPMAP,
          ManifestDetector.MOCK_LOCATION,
          ManifestDetector.MULTIPLE_USES_SDK,
          ManifestDetector.ORDER,
          ManifestDetector.REDUNDANT_LABEL,
          ManifestDetector.SET_VERSION,
          ManifestDetector.UNIQUE_PERMISSION,
          ManifestDetector.WEARABLE_BIND_LISTENER,
          ManifestDetector.WRONG_PARENT,
          ManifestPermissionAttributeDetector.ISSUE,
          ManifestResourceDetector.ISSUE,
          ManifestTypoDetector.ISSUE,
          MediaBrowserServiceCompatVersionDetector.ISSUE,
          MergeMarkerDetector.ISSUE,
          MergeRootFrameLayoutDetector.ISSUE,
          MissingClassDetector.INNERCLASS,
          MissingClassDetector.INSTANTIATABLE,
          MissingClassDetector.MISSING,
          MissingIdDetector.ISSUE,
          MissingInflatedIdDetector.ISSUE,
          MissingPrefixDetector.MISSING_NAMESPACE,
          MissingResourcesPropertiesDetector.ISSUE,
          MonochromeLauncherIconDetector.ISSUE,
          MotionLayoutDetector.INVALID_SCENE_FILE_REFERENCE,
          MotionLayoutIdDetector.MISSING_ID,
          MotionSceneDetector.MOTION_SCENE_FILE_VALIDATION_ERROR,
          NamespaceDetector.CUSTOM_VIEW,
          NamespaceDetector.REDUNDANT,
          NamespaceDetector.RES_AUTO,
          NamespaceDetector.TYPO,
          NamespaceDetector.UNUSED,
          NegativeMarginDetector.ISSUE,
          NestedScrollingWidgetDetector.ISSUE,
          NetworkSecurityConfigDetector.ACCEPTS_USER_CERTIFICATES,
          NetworkSecurityConfigDetector.INSECURE_CONFIGURATION,
          NetworkSecurityConfigDetector.ISSUE,
          NetworkSecurityConfigDetector.MISSING_BACKUP_PIN,
          NetworkSecurityConfigDetector.PIN_SET_EXPIRY,
          NfcTechListDetector.ISSUE,
          NonConstantResourceIdDetector.NON_CONSTANT_RESOURCE_ID,
          NonInternationalizedSmsDetector.ISSUE,
          NoOpDetector.ISSUE,
          NotificationPermissionDetector.ISSUE,
          NotificationTrampolineDetector.ACTIVITY,
          NotificationTrampolineDetector.TRAMPOLINE,
          ObjectAnimatorDetector.BROKEN_PROPERTY,
          ObjectAnimatorDetector.MISSING_KEEP,
          ObsoleteLayoutParamsDetector.ISSUE,
          OnClickDetector.ISSUE,
          OpenForTestingDetector.ISSUE,
          OverdrawDetector.ISSUE,
          OverrideConcreteDetector.ISSUE,
          OverrideDetector.ISSUE,
          PackageVisibilityDetector.QUERY_ALL_PACKAGES_PERMISSION,
          PackageVisibilityDetector.QUERY_PERMISSIONS_NEEDED,
          PageAlignmentDetector.ISSUE,
          ParcelDetector.ISSUE,
          PendingIntentMutableFlagDetector.ISSUE,
          PendingIntentMutableImplicitDetector.ISSUE,
          PermissionDetector.MISSING_PERMISSION,
          PermissionErrorDetector.CUSTOM_PERMISSION_TYPO,
          PermissionErrorDetector.KNOWN_PERMISSION_ERROR,
          PermissionErrorDetector.PERMISSION_NAMING_CONVENTION,
          PermissionErrorDetector.RESERVED_SYSTEM_PERMISSION,
          PermissionErrorDetector.SYSTEM_PERMISSION_TYPO,
          PictureInPictureDetector.ISSUE,
          SelectedPhotoAccessDetector.ISSUE,
          PluralsDetector.EXTRA,
          PluralsDetector.IMPLIED_QUANTITY,
          PluralsDetector.MISSING,
          PowerManagerDetector.INVALID_WAKE_LOCK_TAG,
          PreferenceActivityDetector.ISSUE,
          PrivateApiDetector.BLOCKED_PRIVATE_API,
          PrivateApiDetector.DISCOURAGED_PRIVATE_API,
          PrivateApiDetector.PRIVATE_API,
          PrivateApiDetector.SOON_BLOCKED_PRIVATE_API,
          PrivateKeyDetector.ISSUE,
          PrivateResourceDetector.ISSUE,
          ProguardDetector.SPLIT_CONFIG,
          ProguardDetector.WRONG_KEEP,
          PropertyFileDetector.ESCAPE,
          PropertyFileDetector.HTTP,
          PropertyFileDetector.PROXY_PASSWORD,
          ProviderPermissionDetector.PROVIDER_READ_PERMISSION_ONLY,
          PublicKeyCredentialDetector.ISSUE,
          PxUsageDetector.DP_ISSUE,
          PxUsageDetector.IN_MM_ISSUE,
          PxUsageDetector.PX_ISSUE,
          PxUsageDetector.SMALL_SP_ISSUE,
          RangeDetector.RANGE,
          ReadParcelableDetector.ISSUE,
          RecyclerViewDetector.CLEAR_ALL_DATA,
          RecyclerViewDetector.DATA_BINDER,
          RecyclerViewDetector.FIXED_POSITION,
          RegisterReceiverFlagDetector.RECEIVER_EXPORTED_FLAG,
          RegistrationDetector.ISSUE,
          RelativeOverlapDetector.ISSUE,
          RemoteViewDetector.ISSUE,
          RequiredAttributeDetector.ISSUE,
          RequiredFeatureDetector.ISSUE,
          RequiresFeatureDetector.REQUIRES_FEATURE,
          ResourceCycleDetector.CRASH,
          ResourceCycleDetector.CYCLE,
          ResourcePrefixDetector.ISSUE,
          ResourceTypeDetector.COLOR_USAGE,
          ResourceTypeDetector.HALF_FLOAT,
          ResourceTypeDetector.RESOURCE_TYPE,
          RestrictedEnvironmentBlockedCallDetector.ISSUE,
          RestrictionsDetector.ISSUE,
          RestrictToDetector.RESTRICTED,
          RestrictToDetector.TEST_VISIBILITY,
          ReturnThisDetector.ISSUE,
          RtlDetector.COMPAT,
          RtlDetector.ENABLED,
          RtlDetector.SYMMETRY,
          RtlDetector.USE_START,
          SamDetector.ISSUE,
          ScopedStorageDetector.ISSUE,
          ScrollViewChildDetector.ISSUE,
          SdCardDetector.ISSUE,
          SdkIntDetector.ISSUE,
          SdkSuppressDetector.ISSUE,
          SecretDetector.ISSUE,
          SecureRandomDetector.ISSUE,
          SecureRandomGeneratorDetector.ISSUE,
          SecurityDetector.EXPORTED_PROVIDER,
          SecurityDetector.EXPORTED_RECEIVER,
          SecurityDetector.EXPORTED_SERVICE,
          SecurityDetector.OPEN_PROVIDER,
          SecurityDetector.SET_READABLE,
          SecurityDetector.SET_WRITABLE,
          SecurityDetector.WORLD_READABLE,
          SecurityDetector.WORLD_WRITEABLE,
          ServiceCastDetector.ISSUE,
          ServiceCastDetector.WIFI_MANAGER,
          ServiceCastDetector.WIFI_MANAGER_UNCERTAIN,
          SetJavaScriptEnabledDetector.ISSUE,
          SetTextDetector.SET_TEXT_I18N,
          SharedPrefsDetector.ISSUE,
          ShortcutUsageDetector.ISSUE,
          SignatureOrSystemDetector.ISSUE,
          SliceDetector.ISSUE,
          SplashScreenDetector.ISSUE,
          SQLiteDetector.ISSUE,
          SslCertificateSocketFactoryDetector.CREATE_SOCKET,
          SslCertificateSocketFactoryDetector.GET_INSECURE,
          StartDestinationDetector.ISSUE,
          StateListDetector.ISSUE,
          StorageDetector.ISSUE,
          StringAuthLeakDetector.AUTH_LEAK,
          StringCasingDetector.DUPLICATE_STRINGS,
          StringEscapeDetector.STRING_ESCAPING,
          StringFormatDetector.ARG_COUNT,
          StringFormatDetector.ARG_TYPES,
          StringFormatDetector.INVALID,
          StringFormatDetector.POTENTIAL_PLURAL,
          StringFormatDetector.TRIVIAL,
          SyntheticAccessorDetector.ISSUE,
          SystemPermissionsDetector.ISSUE,
          TextFieldDetector.ISSUE,
          TextViewDetector.ISSUE,
          TextViewDetector.SELECTABLE,
          ThreadDetector.THREAD,
          TileProviderDetector.TILE_PROVIDER_PERMISSIONS,
          TileProviderDetector.SQUARE_AND_ROUND_TILE_PREVIEWS,
          TileServiceActivityDetector.START_ACTIVITY_AND_COLLAPSE_DEPRECATED,
          TileProviderDetector.TILE_PREVIEW_IMAGE_FORMAT,
          TitleDetector.ISSUE,
          ToastDetector.ISSUE,
          TooManyViewsDetector.TOO_DEEP,
          TooManyViewsDetector.TOO_MANY,
          TraceSectionDetector.UNCLOSED_TRACE,
          TranslationDetector.EXTRA,
          TranslationDetector.MISSING,
          TranslationDetector.MISSING_BASE,
          TranslationDetector.TRANSLATED_UNTRANSLATABLE,
          TranslucentViewDetector.ISSUE,
          TrimDetector.ISSUE,
          TypedefDetector.TYPE_DEF,
          TypoDetector.ISSUE,
          TypographyDetector.DASHES,
          TypographyDetector.ELLIPSIS,
          TypographyDetector.FRACTIONS,
          TypographyDetector.OTHER,
          TypographyDetector.QUOTES,
          UastImplementationDetector.ISSUE,
          UElementAsPsiDetector.ISSUE,
          UnsafeBroadcastReceiverDetector.ACTION_STRING,
          UnsafeBroadcastReceiverDetector.BROADCAST_SMS,
          UnsafeImplicitIntentDetector.ISSUE,
          UnsafeIntentLaunchDetector.ISSUE,
          UnsafeNativeCodeDetector.LOAD,
          UnsafeNativeCodeDetector.UNSAFE_NATIVE_CODE_LOCATION,
          UnusedResourceDetector.ISSUE,
          UnusedResourceDetector.ISSUE_IDS,
          UnsafeFilenameDetector.ISSUE,
          UseCompoundDrawableDetector.ISSUE,
          UselessViewDetector.USELESS_LEAF,
          UselessViewDetector.USELESS_PARENT,
          Utf8Detector.ISSUE,
          UseKtxDetector.USE_KTX,
          VectorDetector.ISSUE,
          VectorDrawableCompatDetector.ISSUE,
          VectorPathDetector.PATH_LENGTH,
          VectorPathDetector.PATH_VALID,
          ViewBindingTypeDetector.ISSUE,
          ViewConstructorDetector.ISSUE,
          ViewHolderDetector.ISSUE,
          ViewTypeDetector.ADD_CAST,
          ViewTypeDetector.WRONG_VIEW_CAST,
          WakelockDetector.ISSUE,
          WakelockDetector.TIMEOUT,
          WearableConfigurationActionDetector.ACTION_DUPLICATE,
          WearableConfigurationActionDetector.CONFIGURATION_ACTION,
          WearBackNavigationDetector.ISSUE,
          WearPasswordInputDetector.ISSUE,
          WatchFaceForAndroidXDetector.ISSUE,
          WatchFaceEditorDetector.ISSUE,
          WearMaterialThemeDetector.ISSUE,
          WearRecentsDetector.ISSUE,
          WearSplashScreenDetector.ISSUE,
          WearStandaloneAppDetector.INVALID_WEAR_FEATURE_ATTRIBUTE,
          WearStandaloneAppDetector.WEAR_STANDALONE_APP_ISSUE,
          WebViewApiAvailabilityDetector.ISSUE,
          WebViewClientDetector.PROCEEDS_ON_RECEIVED_SSL_ERROR,
          WebViewDetector.ISSUE,
          WindowExtensionsDetector.ISSUE,
          WorkManagerDetector.ISSUE,
          WrongCallDetector.ISSUE,
          WrongCaseDetector.WRONG_CASE,
          WrongCommentTypeDetector.ISSUE,
          WrongConstructorDetector.ISSUE,
          WrongIdDetector.INVALID,
          WrongIdDetector.NOT_SIBLING,
          WrongIdDetector.UNKNOWN_ID,
          WrongIdDetector.UNKNOWN_ID_LAYOUT,
          WrongImportDetector.ISSUE,
          WrongLocationDetector.ISSUE,
          WrongThreadInterproceduralDetector.ISSUE,
          X509TrustManagerDetector.IMPLEMENTS_CUSTOM,
          X509TrustManagerDetector.TRUSTS_ALL,
        )
      )
  }

  init {
    for (issue in builtinIssues) {
      @Suppress("LeakingThis")
      issue.registry = this
    }
  }

  public override fun cacheable(): Boolean {
    // In the IDE, cache across incremental runs; here, lint is never run in parallel
    // Outside of the IDE, typically in Gradle, we don't want this caching since
    // lint can run in parallel and this caching can be incorrect;
    // see for example issue 77891711
    return isStudio
  }

  override val issues: List<Issue>
    get() = builtinIssues

  override val deletedIssues: List<String> =
    listOf(
      // Off by default for a while; unlikely to be turned on (and this is
      // just an awareness check which is unlikely to be enabled by those
      // who could benefit from it)
      "GoogleAppIndexingWarning",

      // Implementation not correct and would require rewrite to fix, not worth it
      "GoogleAppIndexingApiWarning",

      // Deleted a while back when restrictions were removed on launcher icons
      "IconLauncherFormat",

      // No longer relevant, only applied to minSdk < 14
      "ViewTag",

      // No longer relevant, only applied to minSdk < 9
      "FieldGetter",

      // Renamed to MissingClass
      "MissingRegistered",

      // Combined into FontValidation
      "FontValidationWarning",
      "FontValidationError",

      // Combined into AppLinksAutoVerify
      "AppLinksAutoVerifyError",
      "AppLinksAutoVerifyWarning",

      // Noisy check which is misleading after recent backup changes
      "AllowBackup",

      // No longer the recommendation -- b/201700393
      "MediaCapabilities",

      // No longer the recommendation -- b/216662628
      "UnpackedNativeCode",

      // Deleted; no longer needed thanks to d8
      "Assert",

      // No longer relevant with manifest merging, and has been
      // a no-op forever for Gradle where these attributes are
      // specified in build files rather than the manifest and
      // injected at build time.
      "UsesMinSdkAttributes",

      // This error doesn't get flagged anymore because it was relying
      // on the API database being newer than the compileSdkVersion,
      // and for the last few years we've tied these together; you
      // always use the API database from the platform you're targeting.
      "Override",

      // Obsolete at this point (and the associated learn-more
      // URL is now unavailable, see b/259295923)
      "PackageManagerGetSignatures",
    )

  override fun getIssueCapacity(scope: EnumSet<Scope>): Int {
    return if (scope == Scope.ALL) {
      issues.size
    } else {
      var initialSize = 15
      when {
        scope.contains(Scope.RESOURCE_FILE) -> initialSize += 117
        scope.contains(Scope.ALL_RESOURCE_FILES) -> initialSize += 12
      }
      when {
        scope.contains(Scope.JAVA_FILE) -> initialSize += 250
        scope.contains(Scope.CLASS_FILE) -> initialSize += 16
        scope.contains(Scope.MANIFEST) -> initialSize += 100
        scope.contains(Scope.GRADLE_FILE) -> initialSize += 40
      }
      initialSize
    }
  }

  override val api: Int
    get() = CURRENT_API
}
