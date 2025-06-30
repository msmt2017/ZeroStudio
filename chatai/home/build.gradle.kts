import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
//  alias(chatai.plugins.android.application)
  alias(chatai.plugins.android.library)
  alias(chatai.plugins.kotlin.android)
  alias(chatai.plugins.kotlin.compose)
  alias(chatai.plugins.kotlin.serialization)
  alias(chatai.plugins.ksp)

}

android {
  namespace = "me.rerere.rikkahub"
  compileSdk =  BuildConfig.compileSdk

  defaultConfig {
//    applicationId = "me.rerere.rikkahub"
//    minSdk = 26
//    targetSdk = 36
//    versionCode = 66
//    versionName = "1.0.2"
    buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
    buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

//    splits {
//      abi {
//        reset()
//        include("arm64-v8a", "x86_64")
//        isEnable = true
//        isUniversalApk = true
//      }
//    }
  }

//  signingConfigs {
//    create("release") {
//      val localProperties = Properties()
//      val localPropertiesFile = rootProject.file("local.properties")
//
//      if (localPropertiesFile.exists()) {
//        localProperties.load(FileInputStream(localPropertiesFile))
//
//        val storeFilePath = localProperties.getProperty("storeFile")
//        val storePasswordValue = localProperties.getProperty("storePassword")
//        val keyAliasValue = localProperties.getProperty("keyAlias")
//        val keyPasswordValue = localProperties.getProperty("keyPassword")
//
//        if (storeFilePath != null && storePasswordValue != null &&
//          keyAliasValue != null && keyPasswordValue != null
//        ) {
//          storeFile = file(storeFilePath)
//          storePassword = storePasswordValue
//          keyAlias = keyAliasValue
//          keyPassword = keyPasswordValue
//        }
//      }
//    }
//  }
buildTypes {
        release {
        
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
//  buildTypes {
//    release {
//      signingConfig = signingConfigs.getByName("release")
//      isMinifyEnabled = true
//      isShrinkResources = true
//      proguardFiles(
//        getDefaultProguardFile("proguard-android-optimize.txt"),
//        "proguard-rules.pro"
//      )
//      buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
//      buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
//    }
//    debug {
//      applicationIdSuffix = ".debug"
//      buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
//      buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
//    }
//  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
//  androidResources {
//    generateLocaleConfig = true
//  }
//  applicationVariants.all {
//    outputs.all {
//      this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
//
//      val variantName = name
//      val apkName = "rikkahub_" + defaultConfig.versionName + "_" + variantName + ".apk"
//
//      outputFileName = apkName
//    }
//  }
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
    compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
    compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
    compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
    compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(chatai.androidx.fragment.ktx)
  implementation(chatai.androidx.core.ktx)
  implementation(chatai.androidx.lifecycle.runtime.ktx)
  implementation(chatai.androidx.work.runtime.ktx)
  implementation(chatai.androidx.ui)
  implementation(chatai.androidx.ui.graphics)
  implementation(chatai.androidx.ui.tooling.preview)
  implementation(chatai.androidx.browser)

  // Compose
  implementation(chatai.androidx.activity.compose)
  implementation(platform(chatai.androidx.compose.bom))
  implementation(chatai.androidx.navigation.compose)
  implementation(chatai.androidx.material3)

  // Firebase
  implementation(platform(chatai.firebase.bom))
  implementation(chatai.firebase.analytics)
  implementation(chatai.firebase.crashlytics)

  // DataStore
  implementation(chatai.androidx.datastore.preferences)

  // koin
  implementation(platform(chatai.koin.bom))
  implementation(chatai.koin.android)
  implementation(chatai.koin.compose)
  implementation(chatai.koin.androidx.workmanager)

  // jetbrains markdown parser
  implementation(chatai.jetbrains.markdown)

  // okhttp
  implementation(chatai.okhttp)
  implementation(chatai.okhttp.sse)
  implementation(chatai.retrofit)
  implementation(chatai.retrofit.serialization.json)

  // pebble (template engine)
  implementation(chatai.pebble)

  // coil
  implementation(chatai.coil.compose)
  implementation(chatai.coil.okhttp)
  implementation(chatai.coil.svg)

  // serialization
  implementation(chatai.kotlinx.serialization.json)

  // zxing
  implementation(chatai.zxing.core)

  // quickie (qrcode scanner)
  implementation(chatai.quickie.bundled)
  implementation(chatai.barcode.scanning)
  implementation(chatai.androidx.camera.core)

  // Room
  implementation(chatai.androidx.room.runtime)
  implementation(chatai.androidx.room.ktx)
  implementation(chatai.androidx.room.paging)
  ksp(chatai.androidx.room.compiler)

  // Paging3
  implementation(chatai.androidx.paging.runtime)
  implementation(chatai.androidx.paging.compose)

  // WebDav
  implementation(chatai.dav4jvm) {
    exclude(group = "org.ogce", module = "xpp3")
  }

  // Apache Commons Text
  implementation(chatai.commons.text)

  // Compose Cropper
  implementation(chatai.ucrop)

  // Toast (Sonner)
  implementation(chatai.sonner)

  // Reorderable (https://github.com/Calvin-LL/Reorderable/)
  implementation(chatai.reorderable)

  // Permission
  implementation(chatai.permissions.compose)

  // lucide icons
  implementation(chatai.lucide.icons)

  // image viewer
  implementation(chatai.image.viewer)

  // JLatexMath
  // https://github.com/rikkahub/jlatexmath-android
  implementation(chatai.jlatexmath)
  implementation(chatai.jlatexmath.font.greek)
  implementation(chatai.jlatexmath.font.cyrillic)

  // mcp
  implementation(chatai.modelcontextprotocol.kotlin.sdk)

  // modules
    implementation(project(":core:resources"))
    implementation(project(":core:common"))
    implementation(project(":termux:termux-app"))
    
  implementation(project(":chatai:ai"))
  implementation(project(":chatai:highlight"))
  implementation(project(":chatai:search"))
  implementation(project(":chatai:rag"))
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
  implementation(kotlin("reflect"))

  // Leak Canary
  debugImplementation(chatai.leakcanary.android)

  // tests
  testImplementation(chatai.junit)
  androidTestImplementation(chatai.androidx.junit)
  androidTestImplementation(chatai.androidx.espresso.core)
  androidTestImplementation(platform(chatai.androidx.compose.bom))
  androidTestImplementation(chatai.androidx.ui.test.junit4)
  debugImplementation(chatai.androidx.ui.tooling)
  debugImplementation(chatai.androidx.ui.test.manifest)
}