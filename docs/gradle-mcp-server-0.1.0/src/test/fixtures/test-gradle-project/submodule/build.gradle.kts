plugins {
    `java-library`
}

tasks.register("subCustomTask") {
    group = "Custom"
    description = "A simple custom task in submodule."
    doLast {
        println("Output from subCustomTask.")
    }
}

// Add a dependency to ensure build task does something
dependencies {
    implementation("com.google.guava:guava:32.1.3-jre")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
