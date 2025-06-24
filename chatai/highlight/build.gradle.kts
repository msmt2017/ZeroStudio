plugins {
  alias(chatai.plugins.android.library)
  alias(chatai.plugins.kotlin.android)
  alias(chatai.plugins.kotlin.compose)
  alias(chatai.plugins.kotlin.serialization)
}

android {
  namespace = "me.rerere.highlight"
  compileSdk =  BuildConfig.compileSdk

  defaultConfig {
    minSdk = 24

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
  implementation(platform(chatai.androidx.compose.bom))
  implementation(chatai.androidx.ui)
  implementation(chatai.androidx.ui.graphics)
  implementation(chatai.androidx.ui.tooling.preview)
  implementation(chatai.androidx.material3)
  implementation(chatai.quickjs)
  implementation(chatai.kotlinx.serialization.json)
  implementation(chatai.kotlinx.coroutines.core)
}