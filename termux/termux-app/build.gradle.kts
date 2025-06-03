@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
}

// 移除 TerminalBootstrapPackagesPlugin 相关代码

val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7" // 默认值: "apt-android-7"

android {
    namespace = "com.termux"
    ndkVersion = BuildConfig.ndkVersion
    compileSdk = 34

    defaultConfig {
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"" + packageVariant + "\"") // 用于 TermuxApplication 类
        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = BuildConfig.packageName
        manifestPlaceholders["TERMUX_APP_NAME"] = "AndroidIDE"
    }

    lint.disable += "ProtectedPermissions"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

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