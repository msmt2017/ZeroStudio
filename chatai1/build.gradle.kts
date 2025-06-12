// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(chataiLibs.plugins.android.application) apply false
    alias(chataiLibs.plugins.kotlin.android) apply false
    alias(chataiLibs.plugins.kotlin.compose) apply false
    alias(chataiLibs.plugins.android.library) apply false
    alias(chataiLibs.plugins.ksp) apply false
    alias(chataiLibs.plugins.google.services) apply false
    alias(chataiLibs.plugins.firebase.crashlytics) apply false
    alias(chataiLibs.plugins.objectbox) apply false
}