plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.compose)
    alias(chataiLibs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.highlight"
    compileSdk = 34

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



//显式依赖
// dependencies {
    // // ---------- Compose 版本管理 ----------
    // // Compose 物料清单（BOM），统一管理 Compose 组件版本，避免版本冲突
    // implementation(platform("androidx.compose:compose-bom:${chataiLibs.versions.composeBom}")) // composeBom=2025.05.01
    
    // // ---------- Compose UI 基础组件 ----------
    // // Jetpack Compose UI 核心库，用于构建声明式 UI 界面（版本由 BOM 管理）
    // implementation("androidx.compose.ui:ui") // 对应 toml: androidx-ui
    
    // // Compose UI 图形渲染库，提供颜色、路径、位图等图形处理能力（版本由 BOM 管理）
    // implementation("androidx.compose.ui:ui-graphics") // 对应 toml: androidx-ui-graphics
    
    // // Compose UI 工具预览库，仅在调试模式下支持实时 UI 预览（需配合 Android Studio）
    // implementation("androidx.compose.ui:ui-tooling-preview") {
        // debugOnly() // 标记为调试专用依赖
    // } // 对应 toml: androidx-ui-tooling-preview
    
    // // ---------- Compose 扩展组件 ----------
    // // Material Design 3 组件库，提供现代化 UI 元素（如按钮、导航栏），当前为 Alpha 版本
    // implementation("androidx.compose.material3:material3:1.4.0-alpha14") // 对应 toml: androidx-material3
    
    // // ---------- JavaScript 引擎 ----------
    // // QuickJS Android 封装库，用于在 Android 中执行 JavaScript 代码（如脚本引擎、动态逻辑支持）
    // implementation("wang.harlon.quickjs:wrapper-android:${chataiLibs.versions.quickjs}") // quickjs=3.2.0
    
    // // ---------- Kotlin 标准库扩展 ----------
    // // Kotlin 官方 JSON 序列化库，支持类型安全的 JSON 解析与生成（如网络数据反序列化）
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${chataiLibs.versions.serializationJson}") // serialization-json=1.8.1
    
    // // Kotlin 协程核心库，提供协程调度、取消机制等基础功能（异步编程的核心依赖）
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${chataiLibs.versions.kotlinCoroutinesCore}") // kotlin-coroutines-core=1.10.2
// }

//toml隐式
dependencies {
    implementation(platform(chataiLibs.androidx.compose.bom))
    implementation(chataiLibs.androidx.ui)
    implementation(chataiLibs.androidx.ui.graphics)
    implementation(chataiLibs.androidx.ui.tooling.preview)
    implementation(chataiLibs.androidx.material3)
    implementation(chataiLibs.quickjs)
    implementation(chataiLibs.kotlinx.serialization.json)
    implementation(chataiLibs.kotlinx.coroutines.core)
}