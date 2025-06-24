plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.termux.emulator"
    ndkVersion = BuildConfig.ndkVersion

    defaultConfig {
    }
sourceSets {getByName("main") {  jniLibs.srcDirs("src/main/jniLibs") }  }
    packaging.jniLibs.useLegacyPackaging = true
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
    testImplementation(projects.modules.testing.unit)
}
