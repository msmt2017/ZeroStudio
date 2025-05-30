plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.serialization)
    alias(chataiLibs.plugins.kotlin.compose)
}

android {
    namespace = "me.rerere.search"
    compileSdk = 34

    defaultConfig {
        minSdk = 23

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
    buildFeatures {
        compose = true
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
    // // ---------- 网络请求 ----------
    // // OkHttp核心库，高性能HTTP客户端（支持HTTP/2、连接池、拦截器等）
    // implementation("com.squareup.okhttp3:okhttp:${chataiLibs.versions.okhttp}") // okhttp=4.12.0
    
    // // ---------- 序列化与协程 ----------
    // // Kotlin官方JSON序列化库，支持类型安全的JSON解析与生成（如网络数据反序列化）
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${chataiLibs.versions.serializationJson}") // serialization-json=1.8.1
    
    // // Kotlin协程核心库，提供协程调度、取消机制等基础功能（异步编程的核心依赖）
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${chataiLibs.versions.kotlinCoroutinesCore}") // kotlin-coroutines-core=1.10.2
    
    // // ---------- Compose 组件 ----------
    // // Compose物料清单（BOM），统一管理Compose组件版本，避免版本冲突
    // implementation(platform("androidx.compose:compose-bom:${chataiLibs.versions.composeBom}")) // composeBom=2025.05.01
    
    // // Material Design 3组件库，提供现代化UI组件（如按钮、卡片、对话框等），当前为Alpha版本
    // implementation("androidx.compose.material3:material3:1.4.0-alpha14") // 对应toml: androidx-material3
    
    // // ---------- HTML解析（传递依赖） ----------
    // // jsoup HTML解析库，用于解析、操作HTML文档（如网络爬虫、数据提取，api作用域允许传递给下游模块）
    // api("org.jsoup:jsoup:${chataiLibs.versions.jsoup}") // jsoup=1.20.1
// }


dependencies {
    implementation(chataiLibs.okhttp)
    implementation(chataiLibs.kotlinx.serialization.json)
    implementation(chataiLibs.kotlinx.coroutines.core)
    implementation(platform(chataiLibs.androidx.compose.bom))
    implementation(chataiLibs.androidx.material3)
    api(chataiLibs.jsoup)
}