plugins {
    // id("maven-publish")
    // kotlin("jvm")
        id("com.android.library")
    id("kotlin-android")
    // id("org.jetbrains.kotlin.jvm")
    // id("kotlin-language-server.publishing-conventions")
    // id("kotlin-language-server.kotlin-conventions")
}


android {
namespace = "org.javacs.kt.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        // targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        
    }

    // buildTypes {
        // release {
            // isMinifyEnabled = false 
            // proguardFiles(
                // getDefaultProguardFile("proguard-android-optimize.txt"),
                // "proguard-rules.pro" 
            // )
        // }
    // }

    kotlinOptions {
        jvmTarget = "11" 
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// repositories {
    // mavenCentral()
// }

dependencies {
    // dependencies are constrained to versions defined
    // in /platform/build.gradle.kts
    implementation(platform(project(":core:KotlinLsp:platform")))

    implementation(kotlin("stdlib"))
    implementation(ktlsp.org.jetbrains.exposed.core)
    implementation(ktlsp.org.jetbrains.exposed.dao)
    testImplementation(ktlsp.hamcrest.all)
    testImplementation(ktlsp.junit.junit)
    testImplementation(projects.testing.commonTest)
  testImplementation(projects.testing.lspTest)

  // androidTestImplementation(projects.testing.androidTest)
  // androidTestImplementation(projects.utilities.shared)
}
kotlin {
    jvmToolchain(11)
}

// publishing {
    // repositories {
        // maven {
            // name = "GitHubPackages"
            // url = uri("https://maven.pkg.github.com/fwcd/kotlin-language-server")
            // credentials {
                // username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USERNAME")
                // password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_PASSWORD")
            // }
        // }
    // }

    // publications {
        // register("gpr", MavenPublication::class) {
            // from(components["java"])
        // }
    // }
// }
