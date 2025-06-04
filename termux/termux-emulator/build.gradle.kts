plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.termux.emulator"
    ndkVersion = BuildConfig.ndkVersion

    defaultConfig {
        // 移除 externalNativeBuild 配置，因为不再需要编译时的 cFlags
        // externalNativeBuild {
        //     ndkBuild {
        //         cFlags += arrayOf("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
        //     }
        // }
    }

    // 移除 externalNativeBuild 配置，因为不再编译 C++ 代码
    // externalNativeBuild {
    //     ndkBuild {
    //         path = file("src/main/jni/Android.mk")
    //     }
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType(Test::class.java) {
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(projects.testing.unit)
}