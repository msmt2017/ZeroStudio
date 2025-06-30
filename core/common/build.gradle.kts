plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "${BuildConfig.packageName}.common"
}

dependencies {
    api(libs.common.editor)
    api(libs.common.lang3)
    api(libs.common.utilcode)
    api(libs.google.guava)
    api(libs.google.material)
    
    api(libs.androidx.appcompat)
    api(libs.androidx.collection)
    api(libs.androidx.preference)
    api(libs.androidx.vectors)
    api(libs.androidx.animated.vectors)
    
    api(libs.androidx.core.ktx)
    api(libs.common.kotlin)
    
    api(projects.core.buildInfo)
    api(projects.modules.eventbusAndroid)
    api(projects.modules.eventbusEvents)
    api(projects.editors.lexers)
    api(projects.core.resources)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    // implementation(project("::ANR"))
    api(projects.core.shared)
    api(projects.modules.logger)
    api(projects.core.resources)
    api(projects.subprojects.flashbar)

    testImplementation(libs.tests.junit)
    testImplementation(libs.tests.google.truth)
    testImplementation(libs.tests.robolectric)
}
