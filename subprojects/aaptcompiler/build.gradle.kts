plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "${BuildConfig.packageName}.aaptcompiler"
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.common.kotlin)
    implementation(libs.androidx.collection)
    implementation(projects.logger)
    implementation(projects.subprojects.jaxp)
    
    api(libs.aapt2.annotations)
    api(libs.aapt2.common)
    api(libs.aapt2.proto)
    api(libs.google.protobuf)
    api(projects.subprojects.layoutlibApi)
    
    testImplementation(libs.tests.junit)
    testImplementation(libs.tests.robolectric)
    testImplementation(libs.tests.google.truth)
    testImplementation(projects.shared)
}