import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.serialization)
    alias(chataiLibs.plugins.kotlin.compose)
}

android {
    namespace = "me.rerere.ai"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

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
    buildFeatures {
        compose = true
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}


// dependencies {
    // // ---------- 项目模块依赖 ----------
    // // 自定义搜索功能模块（如本地搜索、网络搜索逻辑实现）
    // implementation(project(":chatai:search"))
    
    // // ---------- Android 核心库 ----------
    // // Android核心Kotlin扩展，提供Kotlin对Android框架的增强（如Context扩展、资源访问等）
    // implementation("androidx.core:core-ktx:${chataiLibs.versions.coreKtx}") // coreKtx=1.16.0
    
    // // ---------- Compose UI 组件 ----------
    // // Compose物料清单（BOM），统一管理Compose组件版本，避免版本冲突
    // implementation(platform("androidx.compose:compose-bom:${chataiLibs.versions.composeBom}")) // composeBom=2025.05.01
    
    // // Material Design 3组件库，提供现代化UI组件（如按钮、卡片、对话框等），当前为Alpha版本
    // implementation("androidx.compose.material3:material3:1.4.0-alpha14") // 来自toml: androidx-material3
    
    // // ---------- 网络请求与日志 ----------
    // // OkHttp核心库，高性能HTTP客户端（支持HTTP/2、连接池、拦截器等）
    // api("com.squareup.okhttp3:okhttp:${chataiLibs.versions.okhttp}") // okhttp=4.12.0
    
    // // OkHttp Server-Sent Events (SSE) 支持，用于实时服务器推送数据
    // api("com.squareup.okhttp3:okhttp-sse:${chataiLibs.versions.okhttp}") // 继承OkHttp版本
    
    // // OkHttp日志拦截器，用于调试时打印HTTP请求/响应日志（仅在debug模式建议启用）
    // api("com.squareup.okhttp3:logging-interceptor:${chataiLibs.versions.okhttp}") // 对应toml: okhttp-logging
    
    // // ---------- Kotlin 标准库扩展 ----------
    // // Kotlin官方JSON序列化库，支持类型安全的JSON解析与生成
    // api("org.jetbrains.kotlinx:kotlinx-serialization-json:${chataiLibs.versions.serializationJson}") // serialization-json=1.8.1
    
    // // Kotlin协程核心库，提供协程调度、取消机制等基础功能
    // api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${chataiLibs.versions.kotlinCoroutinesCore}") // kotlin-coroutines-core=1.10.2
    
    // // Kotlin日期时间库，提供跨平台的日期时间处理API（替代Java 8 Time API）
    // api("org.jetbrains.kotlinx:kotlinx-datetime:${chataiLibs.versions.kotlinxDatetime}") // kotlinx-datetime=0.6.2
    
    // // ---------- 测试依赖 ----------
    // // JUnit4单元测试框架，用于编写Java/Kotlin单元测试
    // testImplementation("junit:junit:${chataiLibs.versions.junit}") // junit=4.13.2
    
    // // Android JUnit扩展，提供Android环境下的测试工具（如Instrumentation测试）
    // androidTestImplementation("androidx.test.ext:junit:${chataiLibs.versions.junitVersion}") // junitVersion=1.2.1
    
    // // Espresso UI测试框架，用于编写Android界面的自动化测试（如点击、断言视图状态）
    // androidTestImplementation("androidx.test.espresso:espresso-core:${chataiLibs.versions.espressoCore}") // espressoCore=3.6.1
// }


dependencies {
    implementation(project(":chatai:search"))

    implementation(chataiLibs.androidx.core.ktx)
    implementation(platform(chataiLibs.androidx.compose.bom))
    implementation(chataiLibs.androidx.material3)

    api(chataiLibs.okhttp)
    api(chataiLibs.okhttp.sse)
    api(chataiLibs.okhttp.logging)

    api(chataiLibs.kotlinx.serialization.json)
    api(chataiLibs.kotlinx.coroutines.core)
    api(chataiLibs.kotlinx.datetime)

    testImplementation(chataiLibs.junit)
    androidTestImplementation(chataiLibs.androidx.junit)
    androidTestImplementation(chataiLibs.androidx.espresso.core)
}