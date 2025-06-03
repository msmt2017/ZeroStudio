@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.plugins.AndroidIDEAssetsPlugin

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
  
      versionCode = 202505021
     versionName = "v20250521"
     
    vectorDrawables.useSupportLibrary = true
  }

  androidResources {
    generateLocaleConfig = false
  }

  
  
kotlinOptions {
        jvmTarget = "11"
apiVersion = "2.1"
languageVersion = "2.1"

    }
    
    signingConfigs {
        create("myCustomSigning") { // Changed name for clarity, you can keep "release" if you prefer
            // Store credentials securely in gradle.properties
            // Make sure these properties are defined in your project's gradle.properties
            // or in your ~/.gradle/gradle.properties
            storeFile = file(providers.gradleProperty("MY_RELEASE_STORE_FILE").get())
            storePassword = providers.gradleProperty("MY_RELEASE_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("MY_RELEASE_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("MY_RELEASE_KEY_PASSWORD").get()

            enableV1Signing = true // 启用 V1 签名
            enableV2Signing = true // 启用 V2 签名 (推荐，Android 7.0+)
            enableV3Signing = true // 启用 V3 签名 (推荐，Android 9+)
            // Ensure the path in MY_RELEASE_STORE_FILE is correct and accessible.
            // Based on your error, it looks like it should be:
            // MY_RELEASE_STORE_FILE=/data/data/com.termux/files/home/AndroidIDE-2.7.1-beta/app/docs/zero_studio_signa.keystore
        }
    }
    buildTypes {
    release {
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("myCustomSigning")
    }
    debug {
signingConfig = signingConfigs.getByName("myCustomSigning")
        }
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