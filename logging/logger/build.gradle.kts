

import com.itsaky.androidide.plugins.NoDesugarPlugin

@Suppress("JavaPluginLanguageLevel")
plugins {
    id("java-library")
    id("com.vanniktech.maven.publish.base")

    
}

apply {
    plugin(NoDesugarPlugin::class.java)
}



description = "AndroidIDE Logging Framework"

dependencies {
    compileOnly(projects.utilities.frameworkStubs)
    


    api(libs.logging.logback.core)
    api(libs.logging.logback.classic) {
        // logback classic depends on upstream logback-core
        // we exclude it and use our own from logback-android
        exclude(group = "ch.qos.logback", module = "logback-core")
    }

    implementation(projects.utilities.buildInfo)

    testImplementation(libs.tests.junit)
    testImplementation(libs.tests.google.truth)
}

