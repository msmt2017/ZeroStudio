plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "${BuildConfig.packageName}.common"
    
        
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
    api(libs.common.editor)
    api(libs.common.lang3)
    api(libs.common.utilcode)
    api(libs.google.guava)
    api(libs.google.material)

    implementation("androidx.multidex:multidex:2.0.1")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    
    api(libs.androidx.appcompat)
    api(libs.androidx.collection)
    api(libs.androidx.preference)
    api(libs.androidx.vectors)
    api(libs.androidx.animated.vectors)
    
    api(libs.androidx.core.ktx)
    api(libs.common.kotlin)
    
    api(projects.buildInfo)
    api(projects.eventbusAndroid)
    api(projects.eventbusEvents)
    api(projects.lexers)
    api(projects.resources)
    
    api(projects.shared)
    api(projects.logger)
    api(projects.resources)
    api(projects.subprojects.flashbar)

    testImplementation(libs.tests.junit)
    testImplementation(libs.tests.google.truth)
    testImplementation(libs.tests.robolectric)
}
