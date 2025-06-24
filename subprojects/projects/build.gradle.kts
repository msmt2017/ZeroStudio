plugins {
  id("com.android.library")
  id("kotlin-android")
  id("kotlin-parcelize")
  id("kotlin-kapt")
}

android {
  namespace = "${BuildConfig.packageName}.projects"
}

kapt {
  arguments {
    arg("eventBusIndex", "${BuildConfig.packageName}.events.ProjectsApiEventsIndex")
  }
}

dependencies {

  kapt(projects.core.annotationProcessors)
  kapt(libs.google.auto.service)

  api(projects.modules.eventbus)
  api(projects.modules.eventbusEvents)
  api(projects.subprojects.toolingApi)

  implementation(projects.core.common)
  implementation(projects.modules.logger)
  implementation(projects.modules.lookup)
  implementation(projects.core.shared)
  implementation(projects.subprojects.javacServices)
  implementation(projects.subprojects.xmlUtils)

  implementation(libs.common.io)
  implementation(libs.common.kotlin.coroutines.android)
  implementation(libs.google.auto.service.annotations)
  implementation(libs.google.guava)

  testImplementation(projects.modules.testing.tooling)
}