plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "${BuildConfig.packageName}.javac.services"
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.common.kotlin)
    implementation(libs.common.utilcode)
    implementation(libs.google.guava)
    implementation(projects.core.common)
    implementation(projects.modules.logger)
    
    api(projects.subprojects.javac)
    
    testImplementation(libs.tests.junit)
    testImplementation(libs.tests.google.truth)
    testImplementation(libs.tests.robolectric)
}