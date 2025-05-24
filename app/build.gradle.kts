@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.plugins.AndroidIDEAssetsPlugin

plugins {
  id("com.android.application")
  id("kotlin-android")
  id("kotlin-kapt")
  id("kotlin-parcelize")
  id("androidx.navigation.safeargs.kotlin")
  
  //  id("com.google.devtools.ksp")
   // id("dagger.hilt.android.plugin")
    
  //  id("com.mikepenz.aboutlibraries.plugin")
  //  id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.auto.license)
    alias(libs.plugins.compose.compiler)
    
}


apply {
  plugin(AndroidIDEAssetsPlugin::class.java)
}

android {
  namespace = BuildConfig.packageName

  defaultConfig {
    applicationId = BuildConfig.packageName
     versionCode = 202505020
     versionName = "v20250520"
    
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables.useSupportLibrary = true

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

  }

  androidResources {
    generateLocaleConfig = true
  }
  
sourceSets {
        getByName("main") {
          //  java.srcDirs("src/main/java", "src/main/kotlin")
            //在src/main下支持java和kotlin两种文件夹同时存在的混合开发配置
             java.srcDirs("src/main/java")
             kotlin.srcDirs("src/main/kotlin")
        }
    }
    
  buildTypes {
    release {
    isMinifyEnabled = true
      isShrinkResources = true
    }
    
  }

}

kapt { arguments { arg("eventBusIndex", "${BuildConfig.packageName}.events.AppEventsIndex") } }

dependencies {
  debugImplementation(libs.common.leakcanary) //内存泄露管理sdk //Memory Leak Management SDK


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

  /**Androidx start **/
  implementation ("androidx.window:window:1.3.0")
// Java扩展支持
implementation ("androidx.window:window-java:1.3.0")

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
  
     // Compose 相关
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    // DataStore
    implementation(libs.androidx.datastore)
    
  // google sdk 
    implementation(libs.google.material)
  implementation(libs.google.flexbox)
   /**Androidx end **/
   
    // AI/ML 相关
    implementation(libs.gemini)
    implementation(libs.openai)
    
    // Ktor 相关
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.core)
    implementation(libs.ktor.engine)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.serialization)
    
    // Markdown
    implementation(libs.compose.markdown)
    
        // License page UI
    implementation(libs.auto.license.core)
    implementation(libs.auto.license.ui)
    
    // Room 数据库
    implementation(libs.room)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    // Serialization
    implementation(libs.kotlin.serialization)
   
  // Kotlin
  implementation(libs.androidx.core.ktx)
  implementation(libs.common.kotlin)
  
  /**hilt start **/
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation)
    kapt(libs.hilt.compiler)
    
      /**hilt end **/
      
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

  // This is to build the tooling-api-impl project before the app is built
  // So we always copy the latest JAR file to assets
  compileOnly(projects.subprojects.toolingApiImpl)

  testImplementation(projects.testing.unit)
  androidTestImplementation(projects.testing.android)
  
  // load 本地[.jar][.aar]sdk 
  implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

  
}

aboutLibraries {
    // Remove the "generated" timestamp to allow for reproducible builds
    excludeFields = arrayOf("generated")
}