plugins {
        id("com.android.library")
    id("kotlin-android")
    // alias(libs.plugins.kotlin.jvm)
    alias(ktlsp.plugins.com.github.jk1.tcdeps)
    alias(ktlsp.plugins.com.jaredsburrows.license)
      
}

android {
    namespace = "org.javacs.kt"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    kotlinOptions {
        jvmTarget = "11" 
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

kotlin {
    jvmToolchain(11)
}

dependencies {
     
     implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar", "*.aar"))))
    // 依赖平台定义的版本约束
    implementation(platform(project(":core:KotlinLsp:platform")))
    annotationProcessor(platform(project(":core:KotlinLsp:platform")))
    implementation(project(":core:KotlinLsp:shared"))

    // LSP4J 库，用于语言服务器协议，运行时需要
    implementation(ktlsp.org.eclipse.lsp4j.lsp4j)
    implementation(ktlsp.org.eclipse.lsp4j.jsonrpc)

    // --- 编译时依赖 (Compile-Only) ---
    compileOnly(ktlsp.org.jetbrains.kotlin.compiler)
    compileOnly(ktlsp.org.jetbrains.kotlin.ktscompiler)
    compileOnly(ktlsp.org.jetbrains.kotlin.kts.jvm.host.unshaded)
    compileOnly(ktlsp.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin)
    compileOnly(ktlsp.org.jetbrains.kotlin.reflect)
    compileOnly(ktlsp.org.jetbrains.fernflower)
    compileOnly(ktlsp.com.github.fwcd.ktfmt)
    compileOnly(ktlsp.com.beust.jcommander)
    
    // 数据库和持久化
    implementation(ktlsp.org.jetbrains.exposed.core)
    implementation(ktlsp.org.jetbrains.exposed.dao)
    implementation(ktlsp.org.jetbrains.exposed.jdbc)
    implementation(ktlsp.com.h2database.h2)
    implementation(ktlsp.org.xerial.sqlite.jdbc)
    
    // --- 测试依赖 ---
    testImplementation(ktlsp.hamcrest.all)
    testImplementation(ktlsp.junit.junit)
    testImplementation(ktlsp.org.openjdk.jmh.core)
    testImplementation(projects.testing.commonTest)
    testImplementation(projects.testing.lspTest)
    androidTestImplementation(projects.testing.androidTest)
    androidTestImplementation(projects.utilities.shared)
    
    // 脚本宿主，同样只在编译时需要
    testCompileOnly(kotlin("scripting-jvm-host"))
    compileOnly(kotlin("scripting-jvm-host"))
    
    annotationProcessor(ktlsp.org.openjdk.jmh.generator.annprocess)
    
    implementation(projects.core.lspApi)
    implementation(projects.core.lspModels)
    implementation(projects.core.projects)
}
