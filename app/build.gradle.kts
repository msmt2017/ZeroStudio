@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.plugins.AndroidIDEAssetsPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-kapt")
  id("kotlin-parcelize")
  id("androidx.navigation.safeargs.kotlin")

  
    alias(chataiLibs.plugins.google.services)
  alias(chataiLibs.plugins.firebase.crashlytics)
  
  
}

apply {
  plugin(AndroidIDEAssetsPlugin::class.java)
}

android {
  namespace = BuildConfig.packageName

  defaultConfig {
  
    applicationId = BuildConfig.packageName
versionCode = BuildConfig.versionCode
// multiDexEnabled = true

     versionName = BuildConfig.versionName
    vectorDrawables.useSupportLibrary = true
  }

  androidResources {
    generateLocaleConfig = false
  }

  buildTypes {
    release {
      isShrinkResources = true
    }
  }
  
packagingOptions {
        // 排除 Netty 版本冲突文件
        exclude("META-INF/io.netty.versions.properties")
    }
// //用于控制chatai的sdk //如果compilesdk低于35则需要
    // configurations.all {
        // resolutionStrategy {
            // // 使用configurations.all与 compileSdk 34 兼容的依赖版本。
            // force("androidx.work:work-runtime-ktx:2.8.0")
            // force("androidx.work:work-runtime:2.8.0")
            // force("androidx.camera:camera-video:1.4.0")
            // force("androidx.camera:camera-view:1.4.0")
            // force("androidx.camera:camera-lifecycle:1.4.0")
            // force("androidx.camera:camera-camera2:1.4.0")
            // force("androidx.camera:camera-core:1.4.0")
            // force("androidx.navigation:navigation-compose:2.8.0")
            // force("androidx.compose.material3:material3-android:1.3.0")
            // force("androidx.compose.material:material-android:1.7.0")
            // force("androidx.compose.animation:animation-core-android:1.7.0")
            // force("androidx.compose.material:material-ripple-android:1.7.0")
            // force("androidx.compose.animation:animation-android:1.7.0")
            // force("androidx.compose.foundation:foundation-layout-android:1.7.0")
            // force("androidx.compose.foundation:foundation-android:1.7.0")
            // force("androidx.compose.ui:ui-tooling-data-android:1.7.0")
            // force("androidx.compose.ui:ui-text-android:1.7.0")
            // force("androidx.compose.ui:ui-tooling-android:1.7.0")
            // force("androidx.compose.ui:ui-graphics-android:1.7.0")
            // force("androidx.lifecycle:lifecycle-runtime-compose-android:2.8.0")
            // force("androidx.core:core-ktx:1.12.0")
            // force("androidx.core:core:1.12.0")
            // force("androidx.compose.runtime:runtime-saveable-android:1.7.0")
            // force("androidx.lifecycle:lifecycle-viewmodel-compose-android:2.8.0")
            // force("androidx.compose.ui:ui-android:1.7.0")
            // force("androidx.activity:activity:1.8.0")
            // force("androidx.activity:activity-compose:1.8.0")
            // force("androidx.activity:activity-ktx:1.8.0")
        // }
    // }


    signingConfigs {
        create("release") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("signing.properties")
            enableV1Signing = true // 启用 V1 签名
            enableV2Signing = true // 启用 V2 签名 (推荐，Android 7.0+)
            enableV3Signing = true // 启用 V3 签名 (推荐，Android 9+)
            if (localPropertiesFile.exists()) {
                localProperties.load(FileInputStream(localPropertiesFile))
                
                val storeFilePath = localProperties.getProperty("storeFile")
                val storePasswordValue = localProperties.getProperty("storePassword")
                val keyAliasValue = localProperties.getProperty("keyAlias")
                val keyPasswordValue = localProperties.getProperty("keyPassword")
                
                if (storeFilePath != null && storePasswordValue != null && 
                    keyAliasValue != null && keyPasswordValue != null) {
                    storeFile = file(storeFilePath)
                    storePassword = storePasswordValue
                    keyAlias = keyAliasValue
                    keyPassword = keyPasswordValue
                }
            }
        }
    }
    
    buildTypes {
    all{
    signingConfig = signingConfigs.getByName("release")
    }
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            multiDexKeepProguard = file("multidex-main-dex-rules.pro")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            ) // This closing parenthesis was commented out and has been uncommented.
          //  signingConfig = signingConfigs.getByName("release")
        }
        // debug {
            // signingConfig = signingConfigs.getByName("release")
        // }
    }


    
  lint {
    abortOnError = false
    disable.addAll(arrayOf("VectorPath", "NestedWeights", "ContentDescription", "SmallSp"))
  }
}

kapt { arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.AppEventsIndex") } }

dependencies {
  debugImplementation(libs.common.leakcanary)

  // Annotation processors
  kapt(libs.common.glide.ap)
  kapt(libs.google.auto.service)
  kapt(projects.annotationProcessors)

  implementation(libs.common.editor)
  implementation(libs.common.utilcode)
  implementation(libs.common.glide)
  implementation(libs.common.jsoup)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.common.retrofit)
  implementation(libs.common.retrofit.gson)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.gson)
  implementation(libs.google.guava)

  // Git
  implementation(libs.git.jgit)

  // AndroidX
  implementation(libs.androidx.splashscreen)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.cardview)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.coordinatorlayout)
  implementation(libs.androidx.drawer)
  implementation(libs.androidx.grid)
  implementation(libs.androidx.nav.fragment)
  implementation(libs.androidx.nav.ui)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.transition)
  implementation(libs.androidx.vectors)
  implementation(libs.androidx.animated.vectors)
  implementation(libs.androidx.work)
  implementation(libs.androidx.work.ktx)
  implementation(libs.google.material)
  implementation(libs.google.flexbox)

  // Kotlin
  implementation(libs.androidx.core.ktx)
  implementation(libs.common.kotlin)

  // Local projects here
  implementation(projects.actions)
  implementation(projects.buildInfo)
  implementation(projects.common)
  implementation(projects.editor)
  implementation(projects.termux.termuxApp)
  implementation(projects.termux.termuxView)
  implementation(projects.termux.termuxEmulator)
  implementation(projects.termux.termuxShared)
  implementation(projects.eventbus)
  implementation(projects.eventbusAndroid)
  implementation(projects.eventbusEvents)
  implementation(projects.gradlePluginConfig)
  implementation(projects.idestats)
  implementation(projects.subprojects.aaptcompiler)
  implementation(projects.subprojects.appintro)
  implementation(projects.subprojects.javacServices)
  implementation(projects.subprojects.javapoet)
  implementation(projects.subprojects.xmlUtils)
  implementation(projects.subprojects.projects)
  implementation(projects.subprojects.toolingApi)
  implementation(projects.logsender)
  implementation(projects.lsp.api)
  implementation(projects.lsp.java)
  implementation(projects.lsp.xml)
  implementation(projects.lexers)
  implementation(projects.lookup)
  implementation(projects.preferences)
  implementation(projects.resources)
  implementation(projects.treeview)
  implementation(projects.templatesApi)
  implementation(projects.templatesImpl)
  implementation(projects.uidesigner)
  implementation(projects.xmlInflater)
  //chatai
   implementation(project(":chatai:home"))
  // This is to build the tooling-api-impl project before the app is built
  // So we always copy the latest JAR file to assets
  compileOnly(projects.subprojects.toolingApiImpl)

  testImplementation(projects.testing.unit)
  androidTestImplementation(projects.testing.android)
}