plugins {
    `java-library` // Apply a plugin to get standard tasks like build, clean, test
}

repositories {
    mavenCentral() // Needed for test dependencies
}

// Define a custom task for testing ExecuteTaskTool
tasks.register("customTask") {
    group = "Custom"
    description = "A simple custom task for testing."
    doLast {
        println("Output from customTask in root project.")
    }
}

java {
    // Use a Java version compatible with your project setup if needed
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// Configure testing for RunTestsTool
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.test {
    useJUnitPlatform()
    // Ensure output is captured by Tooling API listeners
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    // Force tests to run even if no source changes
    outputs.upToDateWhen { false }
}
