import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
   // alias(chataiLibs.plugins.android.application)
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.compose)
    alias(chataiLibs.plugins.kotlin.serialization)
    alias(chataiLibs.plugins.ksp)
  //  alias(chataiLibs.plugins.google.services)
  //  alias(chataiLibs.plugins.firebase.crashlytics)
}

android {
    namespace = "me.rerere.rikkahub"
    compileSdk = BuildConfig.compileSdk

    defaultConfig {
        // applicationId = "me.rerere.rikkahub"
        // minSdk = 26
        // targetSdk = 36
        // versionCode = 54
        // versionName = "0.8.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
            
        // splits {
            // abi {
                // reset()
                // include("arm64-v8a", "x86_64")
                // isEnable = true
                // isUniversalApk = true
            // }
        // }
    }

    // signingConfigs {
        // create("release") {
            // val localProperties = Properties()
            // val localPropertiesFile = rootProject.file("local.properties")
            
            // if (localPropertiesFile.exists()) {
                // localProperties.load(FileInputStream(localPropertiesFile))
                
                // val storeFilePath = localProperties.getProperty("storeFile")
                // val storePasswordValue = localProperties.getProperty("storePassword")
                // val keyAliasValue = localProperties.getProperty("keyAlias")
                // val keyPasswordValue = localProperties.getProperty("keyPassword")
                
                // if (storeFilePath != null && storePasswordValue != null && 
                    // keyAliasValue != null && keyPasswordValue != null) {
                    // storeFile = file(storeFilePath)
                    // storePassword = storePasswordValue
                    // keyAlias = keyAliasValue
                    // keyPassword = keyPasswordValue
                // }
            // }
        // }
    // }

    // buildTypes {
        // release {
          // //  signingConfig = signingConfigs.getByName("release")
            // isMinifyEnabled = true
         // //   isShrinkResources = true
            // proguardFiles(
                // getDefaultProguardFile("proguard-android-optimize.txt"),
                // "proguard-rules.pro"
            // )
            // // buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            // // buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
         // }
        // // debug {
            // // applicationIdSuffix = ".debug"
            // // buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            // // buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        // // }
    // }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // androidResources {
        // generateLocaleConfig = true
    // }
    // applicationVariants.all {
        // outputs.all {
            // this as com.android.build.gradle.internal.api.ApkVariantOutputImpl

            // val variantName = name
            // val apkName = "rikkahub_" + defaultConfig.versionName  + "_" + variantName + ".apk"

            // outputFileName = apkName
        // }
    // }
    
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
        compilerOptions.optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
        compilerOptions.optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
        compilerOptions.optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
        compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

// ktor服务器
implementation(chataiLibs.ktor.client.okhttp)
implementation(chataiLibs.ktor.server.netty)
implementation(chataiLibs.ktor.serialization.kotlinx.json)
implementation(chataiLibs.ktor.server.content.negotiation)
implementation(chataiLibs.ktor.server.cors)


    implementation(chataiLibs.androidx.core.ktx)
    implementation(chataiLibs.androidx.lifecycle.runtime.ktx)
    implementation(chataiLibs.androidx.work.runtime.ktx)
    implementation(chataiLibs.androidx.ui)
    implementation(chataiLibs.androidx.ui.graphics)
    implementation(chataiLibs.androidx.ui.tooling.preview)

    // Compose
    implementation(chataiLibs.androidx.activity.compose)
    implementation(platform(chataiLibs.androidx.compose.bom))
    implementation(chataiLibs.androidx.navigation.compose)
    implementation(chataiLibs.androidx.material3)

    // Firebase
    implementation(platform(chataiLibs.firebase.bom))
    implementation(chataiLibs.firebase.analytics)
    implementation(chataiLibs.firebase.crashlytics)

    // DataStore
    implementation(chataiLibs.androidx.datastore.preferences)

    // koin
    implementation(platform(chataiLibs.koin.bom))
    implementation(chataiLibs.koin.android)
    implementation(chataiLibs.koin.compose)
    implementation(chataiLibs.koin.androidx.workmanager)

    // jetbrains markdown parser
    implementation(chataiLibs.jetbrains.markdown)

    // okhttp
    implementation(chataiLibs.okhttp)
    implementation(chataiLibs.okhttp.sse)
    implementation(chataiLibs.retrofit)
    implementation(chataiLibs.retrofit.serialization.json)

    // coil
    implementation(chataiLibs.coil.compose)
    implementation(chataiLibs.coil.okhttp)
    implementation(chataiLibs.coil.svg)

    // serialization
    implementation(chataiLibs.kotlinx.serialization.json)

    // zxing
    implementation(chataiLibs.zxing.core)

    // quickie (qrcode scanner)
    implementation(chataiLibs.quickie.bundled)
    implementation(chataiLibs.barcode.scanning)
    implementation(chataiLibs.androidx.camera.core)

    // Room
    implementation(chataiLibs.androidx.room.runtime)
    implementation(chataiLibs.androidx.room.ktx)
    implementation(chataiLibs.androidx.room.paging)
    ksp(chataiLibs.androidx.room.compiler)

    // Paging3
    implementation(chataiLibs.androidx.paging.runtime)
    implementation(chataiLibs.androidx.paging.compose)

    // Apache Commons Text
    implementation(chataiLibs.commons.text)

    // Compose Cropper
    implementation(chataiLibs.ucrop)

    // Toast (Sonner)
    implementation(chataiLibs.sonner)

    // Reorderable (https://github.com/Calvin-LL/Reorderable/)
    implementation(chataiLibs.reorderable)

    // Permission
    implementation(chataiLibs.permissions.compose)

    // lucide icons
    implementation(chataiLibs.lucide.icons)

    // image viewer
    implementation(chataiLibs.image.viewer)

    // JLatexMath
    // https://github.com/rikkahub/jlatexmath-android
    implementation(chataiLibs.jlatexmath)
    implementation(chataiLibs.jlatexmath.font.greek)
    implementation(chataiLibs.jlatexmath.font.cyrillic)

    // mcp
    implementation(chataiLibs.modelcontextprotocol.kotlin.sdk)

    // modules
    implementation(project(":resources"))
    implementation(project(":common"))
    implementation(project(":termux:termux-app"))
    implementation(project(":termux:termux-emulator"))
    implementation(project(":termux:termux-shared"))
    implementation(project(":termux:termux-view"))
    implementation(project(":subprojects:projects"))
    implementation(project(":subprojects:tooling-api-model"))
    implementation(project(":logger"))
    implementation(project(":lookup"))
    
    implementation(project(":chatai:ai"))
    implementation(project(":chatai:highlight"))
    implementation(project(":chatai:search"))
    implementation(project(":chatai:rag"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(kotlin("reflect"))

    // Leak Canary
    debugImplementation(chataiLibs.leakcanary.android)

    // tests
    testImplementation(chataiLibs.junit)
    androidTestImplementation(chataiLibs.androidx.junit)
    androidTestImplementation(chataiLibs.androidx.espresso.core)
    androidTestImplementation(platform(chataiLibs.androidx.compose.bom))
    androidTestImplementation(chataiLibs.androidx.ui.test.junit4)
    debugImplementation(chataiLibs.androidx.ui.tooling)
    debugImplementation(chataiLibs.androidx.ui.test.manifest)
}