@file:Suppress("UnstableApiUsage")

//import com.itsaky.androidide.plugins.TerminalBootstrapPackagesPlugin

plugins {
    id("com.android.library")
    id("kotlin-android")
}

//apply {
//    plugin(TerminalBootstrapPackagesPlugin::class.java)
//}

val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7" // Default: "apt-android-7"

android {
    namespace = "com.termux"
    ndkVersion = BuildConfig.ndkVersion

    defaultConfig {
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"" + packageVariant + "\"") // Used by TermuxApplication class
        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = BuildConfig.packageName
        manifestPlaceholders["TERMUX_APP_NAME"] = "AndroidIDE"
    }

    lint.disable += "ProtectedPermissions"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {getByName("main") {  jniLibs.srcDirs("src/main/jniLibs") }  }
    packaging.jniLibs.useLegacyPackaging = true
    
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.androidx.drawer)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.viewpager)
    implementation(libs.google.material)
    implementation(libs.google.guava)
    implementation(libs.common.markwon.core)
    implementation(libs.common.markwon.extStrikethrough)
    implementation(libs.common.markwon.linkify)
    implementation(libs.common.markwon.recycler)

    implementation(projects.core.common)
    implementation(projects.modules.preferences)
    implementation(projects.core.resources)
    implementation(projects.termux.termuxView)
    implementation(projects.termux.termuxShared)

    testImplementation(projects.modules.testing.unit)
}

tasks.register("versionName") {
    doLast {
        print(project.rootProject.version)
    }
}