
@file:Suppress("UnstableApiUsage")



import com.itsaky.androidide.build.config.BuildConfig
import com.itsaky.androidide.plugins.TerminalBootstrapPackagesPlugin

plugins {
    id("com.android.library")
    id("kotlin-android")
}

apply {
    plugin(TerminalBootstrapPackagesPlugin::class.java)
}



val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7" // Default: "apt-android-7"

android {
    namespace = "com.termux"
    ndkVersion = BuildConfig.ndkVersion

    defaultConfig {

        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"" + packageVariant + "\"") // Used by TermuxApplication class

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = BuildConfig.packageName
        manifestPlaceholders["TERMUX_APP_NAME"] = "AndroidIDE"

externalNativeBuild {
    cmake {
        arguments += "-DPROJECT_BUILD_DIR=${project.buildDir}"
    }
}
      externalNativeBuild {
            // cmake {
                // arguments += "-DPROJECT_BUILD_DIR=${project.buildDir}"
            // }
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            }
        }

    }

    // externalNativeBuild {
        // ndkBuild {
            // path = file("src/main/cpp/Android.mk")
        // }
    // }
    externalNativeBuild {

    ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
        
        // cmake {
            // path = file("src/main/cpp/CMakeLists.txt")
            // version = "3.30.5"
        // }
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