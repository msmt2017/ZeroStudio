import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // alias(chataiLibs.plugins.android.application)
    alias(chataiLibs.plugins.android.library)
    alias(chataiLibs.plugins.kotlin.android)
    alias(chataiLibs.plugins.kotlin.compose)
    alias(chataiLibs.plugins.kotlin.serialization)
    alias(chataiLibs.plugins.ksp)
    
}

android {
    namespace = "me.rerere.rikkahub"
    compileSdk = BuildConfig.compileSdk

    defaultConfig {
         buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
         buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        //applicationId = "me.rerere.rikkahub"
        
        // minSdk = 26
        // targetSdk = 36
        // versionCode = 45
        // versionName = "0.7.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // splits {
            // abi {
                // reset()
                // include("arm64-v8a", "x86_64")
                // isEnable = true
                // isUniversalApk = true
            // }
        // }
    }

    // buildTypes {
        // release {
            // isMinifyEnabled = true
          // //  isShrinkResources = true
            // proguardFiles(
                // getDefaultProguardFile("proguard-android-optimize.txt"),
                // "proguard-rules.pro"
            // )
            // buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            // buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        // }
        // debug {
          // //  applicationIdSuffix = ".debug"
            // buildConfigField("String", "VERSION_NAME", "\"${android.defaultConfig.versionName}\"")
            // buildConfigField("String", "VERSION_CODE", "\"${android.defaultConfig.versionCode}\"")
        // }
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

    // applicationVariants.all {
        // outputs.all {
            // this as com.android.build.gradle.internal.api.ApkVariantOutputImpl

            // val variantName = name
            // val apkName = "app_" + defaultConfig.versionName  + "_" + variantName + ".apk"

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


//显式依赖 #与隐式二选一依据个人习惯来
// dependencies {
    // // ---------- Core Libraries ----------
    // // Android核心Kotlin扩展，提供Kotlin对Android框架的增强（如Context扩展、资源访问等）
    // implementation("androidx.core:core-ktx:${chataiLibs.versions.coreKtx}") // coreKtx=1.16.0
    
    // // 生命周期组件的Kotlin扩展，用于管理Activity/Fragment的生命周期感知（如ViewModel、LiveData）
    // implementation("androidx.lifecycle:lifecycle-runtime-ktx:${chataiLibs.versions.lifecycleRuntimeKtx}") // 2.9.0
    
    // // WorkManager的Kotlin扩展，用于管理后台任务（如定时任务、网络请求重试等）
    // implementation("androidx.work:work-runtime-ktx:${chataiLibs.versions.workmanager}") // workmanager=2.10.1
    
    // // ---------- Compose UI 基础 ----------
    // // Jetpack Compose UI 核心库，用于构建声明式UI界面
    // implementation("androidx.compose.ui:ui:${chataiLibs.versions.composeBom}") // 从BOM继承版本，composeBom=2025.05.01
    
    // // Compose UI 图形渲染库，提供图形绘制、颜色处理等功能
    // implementation("androidx.compose.ui:ui-graphics:${chataiLibs.versions.composeBom}")
    
    // // Compose UI 工具预览库，仅在调试模式下用于实时预览UI（需配合Android Studio使用）
    // implementation("androidx.compose.ui:ui-tooling-preview:${chataiLibs.versions.composeBom}") {
        // debugOnly() // 对应原配置的debugImplementation逻辑
    // }
    
    // // ---------- Compose 扩展组件 ----------
    // // 将Compose集成到Activity中的支持库，处理Activity与Compose界面的交互
    // implementation("androidx.activity:activity-compose:${chataiLibs.versions.activityCompose}") // 1.10.1
    
    // // Compose物料清单（BOM），统一管理Compose组件版本，避免版本冲突
    // implementation(platform("androidx.compose:compose-bom:${chataiLibs.versions.composeBom}"))
    
    // // Compose导航库，实现页面路由管理、深度链接等功能
    // implementation("androidx.navigation:navigation-compose:${chataiLibs.versions.navigationCompose}") // 2.9.0
    
    // // Material Design 3组件库，提供现代化UI组件（如按钮、卡片、对话框等），当前为Alpha版本
    // implementation("androidx.compose.material3:material3:1.4.0-alpha14") // 原配置中version=1.4.0-alpha14
    
    // // ---------- Firebase 服务 ----------
    // // Firebase物料清单（BOM），统一管理Firebase服务版本
    // implementation(platform("com.google.firebase:firebase-bom:${chataiLibs.versions.firebaseBom}")) // 33.13.0
    
    // // Firebase分析服务，用于收集应用使用数据、用户行为分析
    // implementation("com.google.firebase:firebase-analytics")
    
    // // Firebase崩溃日志收集，实时监控应用崩溃并提供详细堆栈跟踪
    // implementation("com.google.firebase:firebase-crashlytics:${chataiLibs.versions.firebaseCrashlytics}") // 3.0.3
    
    // // ---------- 数据存储 ----------
    // // Android DataStore Preferences API，用于替代SharedPreferences的类型安全数据存储
    // implementation("androidx.datastore:datastore-preferences:${chataiLibs.versions.datastore}") // 1.1.7
    
    // // ---------- Koin 依赖注入 ----------
    // // Koin物料清单（BOM），统一管理Koin组件版本
    // implementation(platform("io.insert-koin:koin-bom:${chataiLibs.versions.koinBom}")) // 4.0.4
    
    // // Koin Android核心库，提供Android上下文的依赖注入支持
    // implementation("io.insert-koin:koin-android")
    
    // // Koin Compose集成库，在Compose界面中使用依赖注入
    // implementation("io.insert-koin:koin-androidx-compose") // 对应toml: koin-androidx-compose
    
    // // Koin与WorkManager集成库，为后台任务注入依赖
    // implementation("io.insert-koin:koin-androidx-workmanager")
    
    // // ---------- 工具与解析库 ----------
    // // JetBrains Markdown解析器，用于解析和渲染Markdown文本（如文档、评论）
    // implementation("org.jetbrains:markdown:${chataiLibs.versions.markdown}") // 0.7.3
    
    // // ---------- 网络请求 ----------
    // // OkHttp核心库，高性能HTTP客户端（支持HTTP/2、连接池、拦截器等）
    // implementation("com.squareup.okhttp3:okhttp:${chataiLibs.versions.okhttp}") // 4.12.0
    
    // // OkHttp Server-Sent Events (SSE) 支持，用于实时服务器推送数据
    // implementation("com.squareup.okhttp3:okhttp-sse:${chataiLibs.versions.okhttp}") // 继承okhttp版本
    
    // // Retrofit REST客户端，用于构建类型安全的API请求
    // implementation("com.squareup.retrofit2:retrofit:${chataiLibs.versions.retrofit}") // 3.0.0
    
    // // Retrofit的Kotlin序列化转换器，使用kotlinx-serialization解析JSON数据
    // implementation("com.squareup.retrofit2:converter-kotlinx-serialization:${chataiLibs.versions.retrofit}") // 继承retrofit版本
    
    // // ---------- 图片加载 ----------
    // // Coil Compose集成库，高效的图片加载库（支持GIF、SVG、内存缓存等）
    // implementation("io.coil-kt.coil3:coil-compose:${chataiLibs.versions.coil}") // 3.2.0
    
    // // Coil的OkHttp网络层，使用OkHttp作为图片下载引擎
    // implementation("io.coil-kt.coil3:coil-network-okhttp:${chataiLibs.versions.coil}") // 3.2.0
    
    // // Coil的SVG支持库，用于渲染矢量图形
    // implementation("io.coil-kt.coil3:coil-svg:${chataiLibs.versions.coil}") // 3.2.0
    
    // // ---------- 序列化 ----------
    // // Kotlin官方JSON序列化库，支持类型安全的JSON解析与生成
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${chataiLibs.versions.serializationJson}") // 1.8.1
    
    // // ---------- 二维码与相机 ----------
    // // ZXing核心库，用于二维码生成与扫描（纯Java实现，跨平台）
    // implementation("com.google.zxing:core:${chataiLibs.versions.zxing}") // 3.5.3
    
    // // Quickie二维码扫描库，封装了ZXing和ML Kit的扫描功能（简化集成流程）
    // implementation("io.github.g00fy2.quickie:quickie-bundled:${chataiLibs.versions.quickieBundled}") // 1.10.0
    
    // // Google ML Kit条形码扫描库，基于机器学习的高性能扫描方案
    // implementation("com.google.mlkit:barcode-scanning:${chataiLibs.versions.barcodeScanning}") // 17.3.0
    
    // // Android CameraX核心库，用于访问相机硬件（支持摄像头预览、图像捕获等）
    // implementation("androidx.camera:camera-core:${chataiLibs.versions.cameraCore}") // 1.5.0-beta01（预览版）
    
    // // ---------- 本地数据库 ----------
    // // Room持久化库核心模块，基于SQLite的类型安全数据库（支持协程、LiveData）
    // implementation("androidx.room:room-runtime:${chataiLibs.versions.room}") // 2.7.1
    
    // // Room的Kotlin扩展，提供更简洁的API（如协程支持、类型转换）
    // implementation("androidx.room:room-ktx:${chataiLibs.versions.room}") // 2.7.1
    
    // // Room与Paging3集成模块，实现数据库分页加载（适用于大数据集）
    // implementation("androidx.room:room-paging:${chataiLibs.versions.room}") // 2.7.1
    
    // // Room编译器插件，用于生成数据库代码（需配合ksp使用）
    // ksp("androidx.room:room-compiler:${chataiLibs.versions.room}") // 2.7.1
    
    // // ---------- 分页加载 ----------
    // // Paging3核心库，实现数据分页加载（支持网络分页、数据库分页）
    // implementation("androidx.paging:paging.runtime:${chataiLibs.versions.paging}") // 3.3.6
    
    // // Paging3 Compose集成库，提供Compose界面的分页组件（如LazyPagingItems）
    // implementation("androidx.paging:paging-compose:${chataiLibs.versions.paging}") // 3.3.6
    
    // // ---------- 通用工具库 ----------
    // // Apache Commons Text，提供字符串处理工具（如编码解码、格式化、相似度计算等）
    // implementation("org.apache.commons:commons-text:${chataiLibs.versions.commonsText}") // 1.13.1
    
    // // uCrop图片裁剪库，支持自定义裁剪区域、缩放、旋转等功能（非原生实现）
    // implementation("com.github.jens-muenker:uCrop-n-Edit:${chataiLibs.versions.ucrop}") // 4.1.0-non-native
    
    // // Sonner Compose Toast库，用于在Compose中显示轻量级提示信息
    // implementation("io.github.brdominguez:compose-sonner:${chataiLibs.versions.sonner}") // 0.3.10
    
    // // Reorderable可排序组件库，支持拖动排序列表项（适用于任务列表、布局调整等场景）
    // implementation("sh.calvin.reorderable:reorderable:${chataiLibs.versions.reorderable}") // 2.4.3
    
    // // Permissions Compose权限请求库，简化Compose中运行时权限申请流程
    // implementation("com.meticha:permissions_compose:${chataiLibs.versions.permissionsCompose}") // 0.0.1+4
    
    // // Lucide Icons图标库，提供海量矢量图标（支持Compose直接渲染）
    // implementation("com.composables:icons-lucide:${chataiLibs.versions.lucideIcons}") // 1.1.0
    
    // // Image Viewer图片查看库，支持缩放、平移、手势操作（适用于预览高清图片）
    // implementation("com.jvziyaoyao.scale:image-viewer:${chataiLibs.versions.imageViewer}") // 1.1.0-alpha.7
    
    // // ---------- 公式渲染 ----------
    // // JLatexMath公式渲染库，用于在Android中显示LaTeX数学公式（支持希腊字母、西里尔字母字体）
    // implementation("com.github.rikkahub.jlatexmath-android:jlatexmath:${chataiLibs.versions.jlatexmath}") // 1.2
    
    // // JLatexMath希腊字母字体扩展
    // implementation("com.github.rikkahub.jlatexmath-android:jlatexmath-font-greek:${chataiLibs.versions.jlatexmath}") // 1.2
    
    // // JLatexMath西里尔字母字体扩展
    // implementation("com.github.rikkahub.jlatexmath-android:jlatexmath-font-cyrillic:${chataiLibs.versions.jlatexmath}") // 1.2
    
    // // ---------- 模块与本地依赖 ----------
    // // 自定义模块：AI功能模块（如机器学习推理、自然语言处理）
    // implementation(project(":chatai:ai"))
    
    // // 自定义模块：文本高亮模块（如代码高亮、关键词标记）
    // implementation(project(":chatai:highlight"))
    
    // // 自定义模块：搜索功能模块（如本地搜索、网络搜索集成）
    // implementation(project(":chatai:search"))
    
    // // 自定义模块：RAG（检索增强生成）模块（结合检索与生成的AI应用）
    // implementation(project(":chatai:rag"))
    
    // // 本地库依赖：手动导入的JAR/AAR包（如闭源库或未发布到Maven的库）
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    
    // // Kotlin反射库，用于在运行时访问类信息（如依赖注入、序列化等场景）
    // implementation(kotlin("reflect"))
    
    // // ---------- 调试工具 ----------
    // // LeakCanary内存泄漏检测库，仅在调试模式下检测Activity/Fragment的内存泄漏
    // debugImplementation("com.squareup.leakcanary:leakcanary-android:${chataiLibs.versions.leakcanary}") // 2.14
    
    // // ---------- 测试依赖 ----------
    // // JUnit4单元测试框架，用于编写Java/Kotlin单元测试
    // testImplementation("junit:junit:${chataiLibs.versions.junit}") // 4.13.2
    
    // // Android JUnit扩展，提供Android环境下的测试工具（如Instrumentation测试）
    // androidTestImplementation("androidx.test.ext:junit:${chataiLibs.versions.junitVersion}") // 1.2.1
    
    // // Espresso UI测试框架，用于编写Android界面的自动化测试
    // androidTestImplementation("androidx.test.espresso:espresso-core:${chataiLibs.versions.espressoCore}") // 3.6.1
    
    // // Compose测试的物料清单，统一管理测试组件版本
    // androidTestImplementation(platform("androidx.compose:compose-bom:${chataiLibs.versions.composeBom}"))
    
    // // Compose UI测试库，支持对Compose界面进行UI测试（如点击、断言等）
    // androidTestImplementation("androidx.compose.ui:ui-test-junit4:${chataiLibs.versions.composeBom}")
    
    // // Compose UI工具库（调试专用），用于在测试中检查界面层级结构
    // debugImplementation("androidx.compose.ui.tooling:ui-tooling:${chataiLibs.versions.composeBom}")
    
    // // Compose UI测试清单库，声明测试所需的权限和组件（仅调试模式需要）
    // debugImplementation("androidx.compose.ui:ui-test-manifest:${chataiLibs.versions.composeBom}")
// }

//引用toml隐式依赖
dependencies {
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

    // modules
   
    implementation(project(":resources"))
    implementation(project(":common"))
    implementation(project(":termux:termux-app"))
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