
@file:Suppress("UnstableApiUsage")

import com.itsaky.androidide.plugins.AndroidIDEPlugin
import com.itsaky.androidide.plugins.conf.configureAndroidModule
import com.itsaky.androidide.plugins.conf.configureJavaModule
import com.itsaky.androidide.plugins.conf.configureMavenPublish
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("build-logic.root-project")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.gradle.publish) apply false
    
    // Add these correctly:
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.auto.license) apply false
    alias(libs.plugins.android.hilt) apply false
    
    // For direct Kotlin plugin applications:
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
}
buildscript {
  dependencies {
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.nav.safe.args.gradle.plugin)
    
    //        id("dagger.hilt.android.plugin")
   // classpath("com.google.dagger:hilt-android-gradle-plugin:2.44")
   
   
  }
}

subprojects {
  // Always load the F-Droid config
  FDroidConfig.load(project)

  afterEvaluate {
    apply { plugin(AndroidIDEPlugin::class.java) }
  }

  project.group = BuildConfig.packageName
  project.version = rootProject.version

  plugins.withId("com.android.application") {
    configureAndroidModule(libs.androidx.lib.desugaring.get())
  }
  plugins.withId("com.android.library") {
    configureAndroidModule(libs.androidx.lib.desugaring.get())
  }
  plugins.withId("java-library") { configureJavaModule() }
  plugins.withId("com.vanniktech.maven.publish.base") { configureMavenPublish() }

  plugins.withId("com.gradle.plugin-publish") {
    configure<GradlePluginDevelopmentExtension> {
      version = project.publishingVersion
    }
  }

  // tasks.withType<KotlinCompile>().configureEach {
    // kotlinOptions.jvmTarget = BuildConfig.javaVersion.toString()
  // }
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(BuildConfig.javaVersion.toString()))
    }
}

}

// tasks.register<Delete>("clean") { delete(rootProject.layout.buildDirectory) }
tasks.named<Delete>("clean") { 
    delete(rootProject.layout.buildDirectory) 
}