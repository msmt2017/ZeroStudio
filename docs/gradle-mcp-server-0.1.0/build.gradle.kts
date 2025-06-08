plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.gradleShadow)
    alias(libs.plugins.palantirGitVersion)
    application
}

group = "me.gulya.gradle"

application {
    mainClass = "me.gulya.gradle.mcp.MainKt"
}

dependencies {
    implementation(libs.gradleToolingApi)
    runtimeOnly(libs.logbackClassic)

    implementation(libs.anthropicMcpKotlinSdk)

    testImplementation(libs.kotlinTest)
    testImplementation(libs.commonsIo)
    testImplementation(libs.assertjCore)
    testImplementation(platform(libs.striktBom))
    testImplementation(libs.striktCore)
    testImplementation(libs.striktJvm)
    testImplementation(libs.kotlinxCoroutinesTest)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xdebug",
            "-Xmulti-dollar-interpolation",
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
