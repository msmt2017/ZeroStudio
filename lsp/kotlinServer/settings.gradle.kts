pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/kotlin.bintray.com/kotlin-plugin")
    }
}

rootProject.name = "kotlinServer"

include(
    "platform",
    "shared",
    "server"
)
