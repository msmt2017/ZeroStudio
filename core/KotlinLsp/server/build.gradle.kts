plugins {
    
        id("com.android.library")
    id("kotlin-android")
    // alias(libs.plugins.kotlin.jvm)
    
    alias(ktlsp.plugins.com.github.jk1.tcdeps)
    alias(ktlsp.plugins.com.jaredsburrows.license)
    // kotlin("jvm")
    // id("maven-publish")
    // id("application")
      

}

android {
namespace = "org.javacs.kt"
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

// val debugPort = 8000
// val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"

// val serverMainClassName = "org.javacs.kt.MainKt"
// val applicationName = "kotlin-language-server"

// application {
    // mainClass.set(serverMainClassName)
    // description = "Code completions, diagnostics and more for Kotlin"
    // applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$version")
    // applicationDistribution.into("bin") {
        // filePermissions { unix(755) }
    // }
// }
kotlin {
    jvmToolchain(11)
}



dependencies {

   //本地maven文件
   implementation("org.jetbrains:fernflower:1.0")
implementation(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar", "*.aar"))))

    // dependencies are constrained to versions defined
    // in /platform/build.gradle.kts
    implementation(platform(project(":core:KotlinLsp:platform")))
    annotationProcessor(platform(project(":core:KotlinLsp:platform")))
    implementation(project(":core:KotlinLsp:shared"))
  
    implementation(ktlsp.org.eclipse.lsp4j.lsp4j)
    implementation(ktlsp.org.eclipse.lsp4j.jsonrpc)

    implementation(kotlin("compiler"))
    implementation(kotlin("scripting-compiler"))
    implementation(kotlin("scripting-jvm-host-unshaded"))
    implementation(kotlin("sam-with-receiver-compiler-plugin"))
    implementation(kotlin("reflect"))
    implementation(ktlsp.org.jetbrains.fernflower)
    implementation(ktlsp.org.jetbrains.exposed.core)
    implementation(ktlsp.org.jetbrains.exposed.dao)
    implementation(ktlsp.org.jetbrains.exposed.jdbc)
    implementation(ktlsp.com.h2database.h2)
    implementation(ktlsp.com.github.fwcd.ktfmt)
    implementation(ktlsp.com.beust.jcommander)
    implementation(ktlsp.org.xerial.sqlite.jdbc)

    testImplementation(ktlsp.hamcrest.all)
    testImplementation(ktlsp.junit.junit)
    testImplementation(ktlsp.org.openjdk.jmh.core)
    
  testImplementation(projects.testing.commonTest)
  testImplementation(projects.testing.lspTest)
  androidTestImplementation(projects.testing.androidTest)
  androidTestImplementation(projects.utilities.shared)
  
    // See
    // https://github.com/JetBrains/kotlin/blob/65b0a5f90328f4b9addd3a10c6f24f3037482276/libraries/examples/scripting/jvm-embeddable-host/build.gradle.kts#L8
    compileOnly(kotlin("scripting-jvm-host"))
    testCompileOnly(kotlin("scripting-jvm-host"))

    annotationProcessor(ktlsp.org.openjdk.jmh.generator.annprocess)
}

configurations.forEach { config -> config.resolutionStrategy { preferProjectModules() } }

// tasks.startScripts { applicationName = "kotlin-language-server" }

// tasks.register<Exec>("fixFilePermissions") {
    // // When running on macOS or Linux the start script
    // // needs executable permissions to run.

    // onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    // commandLine(
            // "chmod",
            // "+x",
            // "${tasks.installDist.get().destinationDir}/bin/kotlin-language-server"
    // )
// }

// tasks.register<JavaExec>("debugRun") {
    // mainClass.set(serverMainClassName)
    // classpath(sourceSets.main.get().runtimeClasspath)
    // standardInput = System.`in`

    // jvmArgs(debugArgs)
    // doLast { println("Using debug port $debugPort") }
// }

// tasks.register<CreateStartScripts>("debugStartScripts") {
    // applicationName = "kotlin-language-server"
    // mainClass.set(serverMainClassName)
    // outputDir = tasks.installDist.get().destinationDir.toPath().resolve("bin").toFile()
    // classpath = tasks.startScripts.get().classpath
    // defaultJvmOpts = listOf(debugArgs)
// }

// tasks.register<Sync>("installDebugDist") {
    // dependsOn("installDist")
    // finalizedBy("debugStartScripts")
// }

// tasks.withType<Test>() {
    // testLogging {
        // events("failed")
        // exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    // }
// }

// tasks.installDist { finalizedBy("fixFilePermissions") }

// tasks.build { finalizedBy("installDist") }

