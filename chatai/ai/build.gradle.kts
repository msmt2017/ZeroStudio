import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.serialization)
    alias(chataiLibs.plugins.kotlin.compose)
}

android {
    namespace = "me.rerere.ai"
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
    buildFeatures {
        compose = true
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(project(":chatai:search"))

    implementation(chataiLibs.androidx.core.ktx)
    implementation(platform(chataiLibs.androidx.compose.bom))
    implementation(chataiLibs.androidx.material3)

    api(chataiLibs.okhttp)
    api(chataiLibs.okhttp.sse)
    api(chataiLibs.okhttp.logging)

    api(chataiLibs.kotlinx.serialization.json)
    api(chataiLibs.kotlinx.coroutines.core)
    api(chataiLibs.kotlinx.datetime)

    testImplementation(chataiLibs.junit)
    androidTestImplementation(chataiLibs.androidx.junit)
    androidTestImplementation(chataiLibs.androidx.espresso.core)
}