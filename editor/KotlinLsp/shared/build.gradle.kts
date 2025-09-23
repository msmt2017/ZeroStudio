plugins {
    id("com.android.library")
    id("kotlin-android")

}


android {
namespace = "org.javacs.kt.shared"
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
dependencies {
       api(ktlsp.org.jetbrains.kotlin.stdlib){
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
        // api(ktlsp.org.jetbrains.kotlin.compiler)
        api(ktlsp.org.jetbrains.kotlin.ktscompiler){
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
        api(ktlsp.org.jetbrains.kotlin.kotlin.scripting.jvm.host){
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
        // api(ktlsp.org.jetbrains.kotlin.kts.jvm.host.unshaded){
        // exclude(group = "com.google.guava", module = "guava")
        // exclude(group = "org.jline", module = "jline")
        // exclude(group = "net.java.dev.jna", module = "jna-platform")
        // exclude(group = "net.java.dev.jna", module = "jna")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-jvm-host-unshaded")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-compiler-embeddable")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-embeddable")
        // exclude(group = "org.jetbrains.kotlin", module = "kotlin-scripting-compiler-impl-embeddable")
        // }
        api(ktlsp.org.jetbrains.kotlin.sam.with.receiver.compiler.plugin){
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
        api(ktlsp.org.jetbrains.kotlin.reflect){
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
        
        api(ktlsp.org.jetbrains.fernflower)
        api(ktlsp.org.jetbrains.exposed.jdbc)
        
        implementation(ktlsp.hamcrest.all)
        implementation(ktlsp.junit.junit)
        implementation(ktlsp.org.eclipse.lsp4j.lsp4j)
        implementation(ktlsp.org.eclipse.lsp4j.jsonrpc)
        implementation(ktlsp.com.h2database.h2)
        implementation(ktlsp.com.google.guava.guava)
        implementation(ktlsp.com.beust.jcommander)
        implementation(ktlsp.org.openjdk.jmh.core)
        implementation(ktlsp.org.openjdk.jmh.generator.annprocess)
        implementation(ktlsp.org.xerial.sqlite.jdbc)
        
    // 依赖平台定义的版本约束
    // implementation(platform(project(":editor:KotlinLsp:platform")))
    
    // 核心依赖
    implementation(kotlin("stdlib"))
    implementation(ktlsp.org.jetbrains.exposed.core)
    implementation(ktlsp.org.jetbrains.exposed.dao)

    // --- 测试依赖 ---
    testImplementation(ktlsp.hamcrest.all)
    testImplementation(ktlsp.junit.junit)
    testImplementation(projects.testing.commonTest)
    testImplementation(projects.testing.lspTest)
    androidTestImplementation(projects.testing.androidTest)
    androidTestImplementation(projects.utilities.shared)
}

kotlin {
    jvmToolchain(11)
}

