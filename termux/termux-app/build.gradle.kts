@file:Suppress("UnstableApiUsage")

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
                // Available arguments are inside ${SDK}/cmake/.../android.toolchain.cmake file
                // 使用双引号 " 包裹字符串
                arguments(
                    "-DANDROID_STL=c++_static",
                    // 以下是添加的 CMake CFLAGS 等效配置
                    "-DCMAKE_C_FLAGS=\"-std=c11 -Wall -Wextra -Werror -Os -fno-stack-protector -Wl,--gc-sections\""
                    // 添加的 CFLAGS 配置结束
                )
            }
        }
    }


    externalNativeBuild {
        cmake {
            // 使用 = 运算符进行属性赋值
            version = "3.25.1"
            path = file("src/main/cpp/CMakeLists.txt") // path 属性通常需要一个 File 对象
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

    implementation(projects.common)
    implementation(projects.preferences)
    implementation(projects.resources)
    implementation(projects.termux.termuxView)
    implementation(projects.termux.termuxShared)

    testImplementation(projects.testing.unit)
}

tasks.register("versionName") {
    doLast {
        print(project.rootProject.version)
    }
}
