plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
}

android {
    namespace = "me.rerere.mcp"
    compileSdk = BuildConfig.compileSdk

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
}

dependencies {
    implementation(chataiLibs.modelcontextprotocol.kotlin.sdk)
    implementation(chataiLibs.androidx.core.ktx)
    testImplementation(chataiLibs.junit)
    androidTestImplementation(chataiLibs.androidx.junit)
    androidTestImplementation(chataiLibs.androidx.espresso.core)
}