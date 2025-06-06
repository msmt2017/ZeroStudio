
@file:Suppress("UnstableApiUsage")



import com.itsaky.androidide.build.config.BuildConfig
//import com.itsaky.androidide.plugins.TerminalBootstrapPackagesPlugin

plugins {
    id("com.android.library")
    id("kotlin-android")
}

// apply {
    // plugin(TerminalBootstrapPackagesPlugin::class.java)
// }



val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7" // Default: "apt-android-7"

android {
    namespace = "com.termux"
    ndkVersion = BuildConfig.ndkVersion

    defaultConfig {

        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"" + packageVariant + "\"") // Used by TermuxApplication class

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = BuildConfig.packageName
        manifestPlaceholders["TERMUX_APP_NAME"] = "AndroidIDE"

        // externalNativeBuild {
            // ndkBuild {
                // cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            // }
        // }
    }

    // externalNativeBuild {
        // ndkBuild {
            // path = file("src/main/cpp/Android.mk")
        // }
    // }
    // 配置 JNI 库打包
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // 指定 JNI 库的源集路径
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    lint.disable += "ProtectedPermissions"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

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
    implementation(projects.core.resources)
    implementation(projects.termux.view)
    implementation(projects.termux.shared)
    implementation(projects.utilities.preferences)

    testImplementation(projects.testing.unitTest)
}

tasks.register("versionName") {
    doLast {
        print(project.rootProject.version)
    }
}