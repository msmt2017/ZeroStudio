plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.termux.view"
    ndkVersion = BuildConfig.ndkVersion
        buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-temp-debug.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(projects.resources)
    api(projects.termux.termuxEmulator)

    testImplementation(projects.testing.unit)
}