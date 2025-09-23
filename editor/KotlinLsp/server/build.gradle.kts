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

configurations.all {
    resolutionStrategy {
        force(ktlsp.org.jetbrains.kotlin.stdlib)
        force(ktlsp.hamcrest.all)
        force(ktlsp.junit.junit)
        force(ktlsp.org.eclipse.lsp4j.lsp4j)
        force(ktlsp.org.eclipse.lsp4j.jsonrpc)
        force(ktlsp.org.jetbrains.kotlin.compiler)
        force(ktlsp.org.jetbrains.kotlin.kotlin.scripting.jvm.host)
        force(ktlsp.org.jetbrains.kotlin.ktscompiler)
        force(ktlsp.org.jetbrains.kotlin.kts.jvm.host.unshaded)
        force(ktlsp.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin)
        force(ktlsp.org.jetbrains.kotlin.reflect)
        force(ktlsp.org.jetbrains.exposed.core)
        force(ktlsp.org.jetbrains.exposed.dao)
        force(ktlsp.org.jetbrains.exposed.jdbc)
        force(ktlsp.com.google.guava.guava)
        
    }
          exclude(group = "org.hamcrest", module = "hamcrest-core")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-sam-with-receiver-compiler-plugin")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-script-runtime")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-common")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler(")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlinx-coroutines-core-jvm")
}

dependencies {
     
    //kotlin
    api(ktlsp.org.jetbrains.kotlin.kotlin.scripting.jvm.host){
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "org.jline", module = "jline")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        }
    api(ktlsp.org.jetbrains.kotlin.stdlib){
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "org.jline", module = "jline")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        }
    // api(ktlsp.org.jetbrains.kotlin.compiler)
    // api(ktlsp.org.jetbrains.kotlin.compiler.embeddable)
    api(ktlsp.org.jetbrains.kotlin.ktscompiler){
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "org.jline", module = "jline")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        }
    // api(ktlsp.org.jetbrains.kotlin.kts.jvm.host.unshaded){
        // exclude(group = "com.google.guava", module = "guava")
        // exclude(group = "org.jline", module = "jline")
        // exclude(group = "net.java.dev.jna", module = "jna-platform")
        // exclude(group = "net.java.dev.jna", module = "jna")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        // }
    api(ktlsp.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin){
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "org.jline", module = "jline")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        }
    api(ktlsp.org.jetbrains.kotlin.reflect){
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "org.jline", module = "jline")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        }
       
    api(ktlsp.hamcrest.all)
    api(ktlsp.junit.junit)
    api(ktlsp.com.google.guava.guava)
    implementation(ktlsp.com.github.fwcd.ktfmt){
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "org.jline", module = "jline")
        exclude(group = "net.java.dev.jna", module = "jna-platform")
        exclude(group = "net.java.dev.jna", module = "jna")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        }
    api(ktlsp.com.beust.jcommander)
    api(ktlsp.org.openjdk.jmh.core)
     
    // LSP4J 库，用于语言服务器协议，运行时需要
    implementation(ktlsp.org.eclipse.lsp4j.lsp4j)
    implementation(ktlsp.org.eclipse.lsp4j.jsonrpc)
    
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
    // testCompileOnly(kotlin("scripting-jvm-host"))
    // compileOnly(kotlin("scripting-jvm-host"))
    
    api(ktlsp.org.openjdk.jmh.generator.annprocess)
    annotationProcessor(ktlsp.org.openjdk.jmh.generator.annprocess)
    
    //本地资源
    implementation(projects.core.lspApi)
    implementation(projects.core.lspModels)
    implementation(projects.core.projects)
    implementation(projects.core.common)
    // implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar", "*.aar"))))
    implementation(project(":editor:KotlinLsp:shared"))
    implementation("zerostudio:kotlin-psitype:2.1.0")
    implementation(files("lib/kotlin-compiler-2.0.0.jar"))
    implementation(ktlsp.org.jetbrains.fernflower) //本地仓库资源：gradle/libs
    
}
