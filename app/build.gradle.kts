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
  alias(chatai.plugins.google.services)
  alias(chatai.plugins.firebase.crashlytics)
}

apply {
  plugin(AndroidIDEAssetsPlugin::class.java)
}

android {
  namespace = BuildConfig.packageName

  defaultConfig {
    applicationId = BuildConfig.packageName
    vectorDrawables.useSupportLibrary = true
  }

  androidResources {
    generateLocaleConfig = false
  }


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
    }
  }

  kotlinOptions {
    jvmTarget = "11"

  }

  lint {
    abortOnError = false
    disable.addAll(arrayOf("VectorPath", "NestedWeights", "ContentDescription", "SmallSp"))
  }
}

kapt { arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.AppEventsIndex") } }

dependencies {

// // 核心库（必选，提供Debug监控看板基础功能）
    // implementation("cn.hikyson.godeye:godeye-core:+")
    
    // // 监控核心库（Debug模式必选，3.4.0+版本移除no-op空实现）
    // debugImplementation("cn.hikyson.godeye:godeye-monitor:+")
    // // 注意：release版本无需添加监控依赖
    
    // // 可选功能模块（根据需求添加）
    // implementation("cn.hikyson.godeye:godeye-okhttp:+")  // OkHttp网络监控
    // implementation("cn.hikyson.godeye:godeye-xcrash:+") // Crash崩溃监控（必须添加）
    
    // // 内存泄漏监控（Debug专用，与LeakCanary互斥）
    // debugImplementation("cn.hikyson.godeye:godeye-leakcanary:+")

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


// ktor服务器
implementation(chatai.ktor.client.okhttp)
implementation(chatai.ktor.server.netty)
implementation(chatai.ktor.serialization.kotlinx.json)
implementation(chatai.ktor.server.content.negotiation)
implementation(chatai.ktor.server.cors)

  debugImplementation(libs.common.leakcanary)

  // Annotation processors
  kapt(libs.common.glide.ap)
  kapt(libs.google.auto.service)
  kapt(projects.core.annotationProcessors)

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
  implementation(libs.androidx.documentfile)
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
  implementation(projects.core.actions)
  implementation(projects.core.buildInfo)
  implementation(projects.core.common)
  implementation(projects.editors.editor)
  implementation(projects.termux.termuxApp)
  implementation(projects.termux.termuxView)
  implementation(projects.termux.termuxEmulator)
  implementation(projects.termux.termuxShared)
  implementation(projects.modules.eventbus)
  implementation(projects.modules.eventbusAndroid)
  implementation(projects.modules.eventbusEvents)
  implementation(projects.modules.gradlePluginConfig)
  implementation(projects.modules.idestats)
  implementation(projects.subprojects.aaptcompiler)
  implementation(projects.subprojects.appintro)
  implementation(projects.subprojects.javacServices)
  implementation(projects.subprojects.javapoet)
  implementation(projects.subprojects.xmlUtils)
  implementation(projects.subprojects.projects)
  implementation(projects.subprojects.toolingApi)
  implementation(projects.modules.logsender)
  implementation(projects.lsp.api)
  implementation(projects.lsp.java)
  implementation(projects.lsp.xml)
  implementation(projects.editors.lexers)
  implementation(projects.modules.lookup)
  implementation(projects.modules.preferences)
  implementation(projects.core.resources)
  implementation(projects.editors.treeview)
  implementation(projects.core.templatesApi)
  implementation(projects.core.templatesImpl)
  implementation(projects.editors.uidesigner)
  implementation(projects.editors.xmlInflater)
   implementation(project(":chatai:home"))
       implementation(project(":chatai:ai"))
    implementation(project(":chatai:highlight"))
    implementation(project(":chatai:search"))
    implementation(project(":chatai:rag"))
    implementation(project(":modules:MTDataFilesProvider"))
    // implementation(project("::ANR"))
  // This is to build the tooling-api-impl project before the app is built
  // So we always copy the latest JAR file to assets
  compileOnly(projects.subprojects.toolingApiImpl)

  testImplementation(projects.modules.testing.unit)
  androidTestImplementation(projects.modules.testing.android)
}