plugins {
  alias(chatai.plugins.android.library)
  alias(chatai.plugins.kotlin.android)
  alias(chatai.plugins.kotlin.serialization)
  alias(chatai.plugins.kotlin.compose)
}

android {
  namespace = "me.rerere.search"
  compileSdk =  BuildConfig.compileSdk

  defaultConfig {
    minSdk = 23

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
  buildFeatures {
    compose = true
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
}

dependencies {
  implementation(chatai.okhttp)
  implementation(chatai.kotlinx.serialization.json)
  implementation(chatai.kotlinx.coroutines.core)
  implementation(platform(chatai.androidx.compose.bom))
  implementation(chatai.androidx.material3)
  api(chatai.jsoup)
}