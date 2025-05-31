plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.objectbox)
}

android {
    namespace = "me.rerere.rag"
    compileSdk = BuildConfig.compileSdk

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}


// dependencies {
    // // jsoup HTML解析库，用于解析、操作HTML文档（如网络爬虫、数据提取）
    // implementation("org.jsoup:jsoup:${chataiLibs.versions.jsoup}") // jsoup=1.20.1
// }

dependencies {
    implementation(chataiLibs.jsoup)
}