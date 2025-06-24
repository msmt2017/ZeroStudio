import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(chatai.plugins.android.library)
  alias(chatai.plugins.kotlin.android)
  alias(chatai.plugins.kotlin.serialization)
  alias(chatai.plugins.kotlin.compose)
}

android {
  namespace = "me.rerere.ai"
  compileSdk =  BuildConfig.compileSdk

  defaultConfig {
    minSdk = 26

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    compose = true
  }
  tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
  }
}

dependencies {
  implementation(project(":chatai:search"))

  implementation(chatai.androidx.core.ktx)
  implementation(platform(chatai.androidx.compose.bom))
  implementation(chatai.androidx.material3)

  api(chatai.okhttp)
  api(chatai.okhttp.sse)
  api(chatai.okhttp.logging)

  api(chatai.kotlinx.serialization.json)
  api(chatai.kotlinx.coroutines.core)
  api(chatai.kotlinx.datetime)

  testImplementation(chatai.junit)
  androidTestImplementation(chatai.androidx.junit)
  androidTestImplementation(chatai.androidx.espresso.core)
}