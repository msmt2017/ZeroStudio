plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.devtools.ksp") version libs.versions.ksp
}

android {
    namespace = "${BuildConfig.packageName}.editor"
    
    buildTypes {
        release {
            // minifyEnabled false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}


kapt {
    arguments {
        arg ("eventBusIndex", "${BuildConfig.packageName}.events.EditorEventsIndex")
    }
}

dependencies {
    ksp(projects.core.annotationProcessorsKsp)
    kapt(projects.core.annotationProcessors)
    
    api(libs.androidide.ts)
    api(libs.androidide.ts.java)
    api(libs.androidide.ts.json)
    api(libs.androidide.ts.kotlin)
    api(libs.androidide.ts.log)
    api(libs.androidide.ts.xml)
    api(libs.androidx.collection)
    api(libs.common.editor)
    
    api(projects.editors.editorApi)
    api(projects.editors.editorTreesitter)

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.tracing)
    implementation(libs.androidx.tracing.ktx)

    implementation(libs.common.utilcode)
    
    implementation(libs.google.material)
    
    implementation(projects.core.actions)
    implementation(projects.core.annotations)
    implementation(projects.core.common)
    implementation(projects.modules.eventbusAndroid)
    implementation(projects.modules.eventbusEvents)
    implementation(projects.editors.lexers)
    implementation(projects.core.shared)
    implementation(projects.core.resources)
    
    implementation(projects.lsp.api)
    implementation(projects.lsp.java)
    implementation(projects.lsp.xml)
    
    testImplementation(libs.tests.junit)
    testImplementation(libs.tests.google.truth)
    testImplementation(libs.tests.robolectric)
}
