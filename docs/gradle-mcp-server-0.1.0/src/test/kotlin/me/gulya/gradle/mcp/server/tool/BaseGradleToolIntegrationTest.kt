package me.gulya.gradle.mcp.server.tool

import me.gulya.gradle.mcp.gradle.GradleService
import org.apache.commons.io.FileUtils // Using Apache Commons IO
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
// No need for java.net.URI anymore

abstract class BaseGradleToolIntegrationTest {

    @TempDir
    lateinit var tempProjectDir: File

    protected lateinit var testProjectPath: String
    protected val gradleService = GradleService()

    @BeforeEach
    fun setupFixture() {
        val sourceFixtureDir = getSourceFixtureDirectory() // Use the updated location logic

        FileUtils.copyDirectory(sourceFixtureDir, tempProjectDir)
        testProjectPath = tempProjectDir.absolutePath
        println("Gradle test fixture copied from ${sourceFixtureDir.absolutePath} to: $testProjectPath")
    }

    /**
     * Locates the test fixture directory relative to the project root.
     * Assumes the test execution working directory is the project root.
     */
    private fun getSourceFixtureDirectory(): File {
        // Get the current working directory, assumed to be the project root
        val projectRootDirPath = System.getProperty("user.dir")
            ?: throw IllegalStateException("Could not determine project root directory (user.dir system property is null).")

        // Define the relative path from the project root to the fixture directory
        val fixtureRelativePath = "src/test/fixtures/test-gradle-project" // <--- Key change: path relative to root

        val sourceFixtureDir = File(projectRootDirPath, fixtureRelativePath)

        // Add validation to give helpful errors if the fixture is missing
        if (!sourceFixtureDir.exists()) {
            throw IllegalStateException(
                "Test Gradle fixture not found at expected external path: ${sourceFixtureDir.absolutePath}. " +
                "Ensure the fixture project exists at '$fixtureRelativePath' relative to the project root (${projectRootDirPath})."
            )
        }
        if (!sourceFixtureDir.isDirectory) {
             throw IllegalStateException(
                 "Test Gradle fixture path exists but is not a directory: ${sourceFixtureDir.absolutePath}"
             )
        }

        println("Resolved source fixture directory: ${sourceFixtureDir.absolutePath}") // Optional: for debugging test setup
        return sourceFixtureDir
    }
}
