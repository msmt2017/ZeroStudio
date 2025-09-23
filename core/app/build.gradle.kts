
@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.desugaring.utils.JavaIOReplacements.applyJavaIOReplacements
import com.itsaky.androidide.plugins.AndroidIDEAssetsPlugin
import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
  id("com.itsaky.androidide.core-app")
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-kapt")
  id("kotlin-parcelize")
  id("androidx.navigation.safeargs.kotlin")
  id("com.itsaky.androidide.desugaring")
  alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

apply {
  plugin(AndroidIDEAssetsPlugin::class.java)
}

buildscript {
  dependencies {
    classpath(libs.logging.logback.core)
    classpath(libs.composite.desugaringCore)
  }
}

android {
  namespace = BuildConfig.packageName

  defaultConfig {
    applicationId = BuildConfig.packageName
    vectorDrawables.useSupportLibrary = true
  }

  androidResources {
    generateLocaleConfig = true
  }


    signingConfigs {
        create("all") {
            val localProperties = Properties()
            val localPropertiesFile = rootProject.file("signing.properties")
            enableV1Signing = true // 启用 V1 签名
            enableV2Signing = true // 启用 V2 签名 (推荐，Android 7.0+)
            enableV3Signing = true // 启用 V3 签名 (推荐，Android 9+)
            enableV4Signing = true
            
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
      signingConfig = signingConfigs.getByName("all")
      }
  
    debug{
    isShrinkResources = false
    }
    release {
      isShrinkResources = true
      
    }
  }

packagingOptions {
        resources {
        pickFirsts.add("**/*.kotlin_builtins")
        pickFirsts.add("kotlin/kotlin.kotlin_builtins")
        exclude("THIRD-PARTY")
        }
    }
    
  lint {
    abortOnError = false
    disable.addAll(arrayOf("VectorPath", "NestedWeights", "ContentDescription", "SmallSp"))
  }
  
  
    
}

kapt {
  arguments {
    arg("eventBusIndex", "${BuildConfig.packageName}.events.AppEventsIndex")
  }
}

desugaring {
  replacements {
    includePackage(
      "org.eclipse.jgit",
    )

    applyJavaIOReplacements()
  }
}
configurations.all {
    resolutionStrategy {
        force(ktlsp.org.jetbrains.kotlin.stdlib)
        force(ktlsp.hamcrest.all)
        force(ktlsp.junit.junit)
        force(ktlsp.org.eclipse.lsp4j.lsp4j)
        force(ktlsp.org.eclipse.lsp4j.jsonrpc)
        force(ktlsp.org.jetbrains.kotlin.compiler)
        
        force(ktlsp.org.jetbrains.kotlin.kotlin.scripting.jvm.host)
        force(ktlsp.org.jetbrains.kotlin.ktscompiler)
        force(ktlsp.org.jetbrains.kotlin.kts.jvm.host.unshaded)
        force(ktlsp.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin)
        force(ktlsp.org.jetbrains.kotlin.reflect)
        force(ktlsp.org.jetbrains.kotlin.jvm)
    }
}

dependencies {

  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

  debugImplementation(libs.common.leakcanary)

  // Annotation processors
  kapt(libs.common.glide.ap)
  kapt(libs.google.auto.service)
  kapt(projects.annotation.processors)

  implementation(libs.common.editor)
  implementation(libs.common.utilcode)
  implementation(libs.common.glide)
  implementation(libs.common.jsoup)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.common.retrofit)
  implementation(libs.common.retrofit.gson)
  implementation(libs.common.charts)
  implementation(libs.common.hiddenApiBypass)
  implementation(libs.aapt2.common)

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
  implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")

  // Dependencies in composite build
  implementation(libs.composite.appintro)
  implementation(libs.composite.desugaringCore)
  implementation(libs.composite.javapoet)
  
  // implementation(libs.ec4j.core)
  // implementation(libs.org.dom4j)
  //kotlin格式化
  // implementation("com.facebook:ktfmt:0.54"){
  // exclude(group = "com.google.googlejavaformat", module = "google-java-format")
  // }
  // implementation(libs.ktlint.rule.engine.core)
  // implementation(libs.ktlint.rule.core)
  // implementation(libs.ktlint.standard)
  // implementation(libs.ktlint.engine)
  // java格式化
  // implementation(libs.google.java.format) //网络仓库官方
   implementation(libs.composite.googleJavaFormat) //使用本地格式化模块

    // LSP4J 库，用于语言服务器协议，运行时需要
    implementation(ktlsp.org.eclipse.lsp4j.lsp4j)
    implementation(ktlsp.org.eclipse.lsp4j.jsonrpc)

  // Local projects here
  implementation(projects.core.actions)
  implementation(projects.core.common)
  implementation(projects.core.indexingApi)
  implementation(projects.core.indexingCore)
  implementation(projects.core.lspApi)
  implementation(projects.core.projects)
  implementation(projects.core.resources)
  implementation(projects.editor.impl)
  implementation(projects.editor.lexers)
  implementation(projects.event.eventbus)
  implementation(projects.event.eventbusAndroid)
  implementation(projects.event.eventbusEvents)
  implementation(projects.java.javacServices)
  implementation(projects.java.lsp)
  implementation(projects.logging.idestats)
  implementation(projects.logging.logsender)
  implementation(projects.termux.application)
  implementation(projects.termux.view)
  implementation(projects.termux.emulator)
  implementation(projects.termux.shared)
  implementation(projects.tooling.api)
  implementation(projects.tooling.pluginConfig)
  implementation(projects.utilities.buildInfo)
  implementation(projects.utilities.lookup)
  implementation(projects.utilities.preferences)
  implementation(projects.utilities.templatesApi)
  implementation(projects.utilities.templatesImpl)
  implementation(projects.utilities.treeview)
  implementation(projects.utilities.uidesigner)
  implementation(projects.utilities.xmlInflater)
  implementation(projects.xml.aaptcompiler)
  implementation(projects.xml.lsp)
  implementation(projects.xml.utils)
   // implementation(project(":chatai:home"))
    // implementation(project(":chatai:ai"))
    // implementation(project(":chatai:highlight"))
    // implementation(project(":chatai:search"))
    // implementation(project(":chatai:rag"))
   implementation(project(":modules:MTDataFilesProvider"))
    implementation(project(":editor:KotlinLsp:server"))
    // implementation(project(":editor:KotlinLsp:adapter"))


  // This is to build the tooling-api-impl project before the app is built
  // So we always copy the latest JAR file to assets
  compileOnly(projects.tooling.impl)
  
  testImplementation("org.conscrypt:conscrypt-openjdk:2.5.2")
  testImplementation(projects.testing.unitTest)
  androidTestImplementation(projects.testing.androidTest)
  
  //注解依赖库
  annotationProcessor("com.google.auto.service:auto-service:1.1.1")
  compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
  
}
