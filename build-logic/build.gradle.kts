plugins {
    `kotlin-dsl`
}

group = "zero" 


gradlePlugin {
    plugins {
        create("androidIdeConventions") {
            id = "androidide.conventions"
            implementationClass = "zero.AndroidIdeConventionsPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    
    implementation("com.android.tools.build:gradle:8.9.2") 
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
}