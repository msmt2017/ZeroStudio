plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.serialization)
    alias(chataiLibs.plugins.kotlin.compose)
}

android {
    namespace = "me.rerere.search"
    compileSdk = BuildConfig.compileSdk

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    implementation(chataiLibs.okhttp)
    implementation(chataiLibs.kotlinx.serialization.json)
    implementation(chataiLibs.kotlinx.coroutines.core)
    implementation(platform(chataiLibs.androidx.compose.bom))
    implementation(chataiLibs.androidx.material3)
    api(chataiLibs.jsoup)
}