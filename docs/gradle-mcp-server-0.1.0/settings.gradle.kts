@file:Suppress("UnstableApiUsage")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "gradle-mcp-server"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.gradle.org/gradle/libs-releases")
        maven("https://jitpack.io")
    }
}
