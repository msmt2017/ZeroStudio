plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.compose)
    alias(chataiLibs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.highlight"
    compileSdk = BuildConfig.compileSdk

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
    implementation(platform(chataiLibs.androidx.compose.bom))
    implementation(chataiLibs.androidx.ui)
    implementation(chataiLibs.androidx.ui.graphics)
    implementation(chataiLibs.androidx.ui.tooling.preview)
    implementation(chataiLibs.androidx.material3)
    implementation(chataiLibs.quickjs)
    implementation(chataiLibs.kotlinx.serialization.json)
    implementation(chataiLibs.kotlinx.coroutines.core)
}