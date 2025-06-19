@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
}

val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7" // 默认值: "apt-android-7"

android {
    namespace = "com.termux"
    ndkVersion = BuildConfig.ndkVersion

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


    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
