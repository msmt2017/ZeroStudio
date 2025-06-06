

import com.itsaky.androidide.build.config.BuildConfig

@Suppress("JavaPluginLanguageLevel")
plugins {
  id("com.github.johnrengelman.shadow") version "8.1.1"
  id("java-library")
  id("kotlin-kapt")
  id("org.jetbrains.kotlin.jvm")
}



tasks.withType<Jar> {
  manifest { attributes("Main-Class" to "${BuildConfig.packageName}.tooling.impl.Main") }
}

tasks.register("deleteExistingJarFiles") {
  delete {
    delete(project.layout.buildDirectory.dir("libs"))
  }
}

tasks.register("copyJar") {
  doLast {
    val libsDir = project.layout.buildDirectory.dir("libs")

    copy {
      from(libsDir)
      into(libsDir)
      include("*-all.jar")
      rename { "tooling-api-all.jar" }
    }
  }
}

project.tasks.getByName("jar") {
  dependsOn("deleteExistingJarFiles")
  finalizedBy("shadowJar")
}

project.tasks.getByName("shadowJar") {
  finalizedBy("copyJar")
}

dependencies {
  kapt(libs.google.auto.service)

  api(projects.tooling.api)

    implementation(libs.logging.logback.core)
    implementation(libs.logging.logback.classic) 

  implementation(projects.utilities.buildInfo)
  implementation(projects.utilities.shared)

  implementation(libs.common.jkotlin)
  implementation(libs.google.auto.service.annotations)
  implementation(libs.xml.xercesImpl)
  implementation(libs.xml.apis)
  implementation(libs.tooling.gradleApi)

  testImplementation(projects.testing.gradleToolingTest)

  runtimeOnly(libs.tooling.slf4j)
}
