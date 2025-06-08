package me.gulya.gradle.mcp.gradle

import me.gulya.gradle.mcp.config.logger
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Encapsulates interactions with the Gradle Tooling API.
 */
class GradleService {
    private val log = logger<GradleService>()

    /**
     * Connects to a Gradle project and executes a block of code with the connection.
     * Ensures the connection is closed afterwards.
     *
     * @param projectPath The absolute path to the Gradle project directory.
     * @param action The code block to execute with the GradleConnection.
     * @return The result of the action block.
     * @throws GradleConnectionException if the connection fails.
     * @throws IllegalStateException if the project path is invalid.
     */
    fun <T> withConnection(projectPath: String, action: (ProjectConnection) -> T): T {
        val projectDir = File(projectPath)
        if (!projectDir.isDirectory) {
            throw IllegalStateException("Project path does not exist or is not a directory: $projectPath")
        }

        val connector = GradleConnector.newConnector()
            .forProjectDirectory(projectDir)

        var connection: ProjectConnection? = null
        try {
            log.debug("Connecting to Gradle project at: {}", projectPath)
            connection = connector.connect()
            return action(connection)
        } catch (e: GradleConnectionException) {
            log.error("Gradle connection failed for path: {}", projectPath, e)
            throw e // Re-throw to be handled by the caller
        } catch (e: IllegalStateException) {
            // Often happens if the project path is invalid or not a Gradle project
            log.error("Error connecting to Gradle (likely invalid project path): {}", projectPath, e)
            throw e // Re-throw
        } catch (e: Exception) {
            log.error("Unexpected error during Gradle connection or action for path: {}", projectPath, e)
            throw e // Re-throw generic exceptions
        } finally {
            try {
                connection?.close()
                log.debug("Closed Gradle connection for: {}", projectPath)
            } catch (e: Exception) {
                log.warn("Error closing Gradle connection for path: {}", projectPath, e)
            }
        }
    }

    /**
     * Fetches a model of a specific type from the Gradle project.
     *
     * @param connection The active GradleConnection.
     * @param modelClass The class of the model to fetch (e.g., GradleProject::class.java).
     * @return The fetched model instance.
     * @throws Exception if fetching the model fails.
     */
    fun <T> fetchModel(connection: ProjectConnection, modelClass: Class<T>): T {
        try {
            log.debug("Fetching Gradle model: {}", modelClass.simpleName)
            return connection.model(modelClass).get()
        } catch (e: Exception) {
            log.error("Failed to fetch Gradle model: {}", modelClass.simpleName, e)
            throw RuntimeException("Failed to fetch Gradle model ${modelClass.simpleName}: ${e.message}", e)
        }
    }

    data class BuildExecutionConfig(
        val tasks: List<String>,
        val arguments: List<String> = emptyList(),
        val jvmArguments: List<String> = emptyList(),
        val environmentVariables: Map<String, String> = emptyMap(),
        val progressListeners: List<Pair<ProgressListener, Set<OperationType>>> = emptyList()
    )

    data class BuildResult(
        val success: Boolean,
        val output: String,
        val errorOutput: String,
        val exception: Throwable? = null
    )

    /**
     * Executes a Gradle build (tasks) with specified configuration.
     *
     * @param connection The active GradleConnection.
     * @param config Configuration for the build execution.
     * @return A BuildResult containing success status, output, and any exception.
     */
    fun executeBuild(connection: ProjectConnection, config: BuildExecutionConfig): BuildResult {
        val buildLauncher = connection.newBuild()
            .forTasks(*config.tasks.toTypedArray())
            .withArguments(*config.arguments.toTypedArray())
            .setJvmArguments(*config.jvmArguments.toTypedArray())
            .setEnvironmentVariables(config.environmentVariables)

        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        buildLauncher.setStandardOutput(outputStream)
        buildLauncher.setStandardError(errorStream)

        config.progressListeners.forEach { (listener, types) ->
            if (types.isEmpty()) {
                log.warn("Progress listener added without specified OperationTypes. It might not receive events.")
                buildLauncher.addProgressListener(listener) // Add without types if set is empty
            } else {
                buildLauncher.addProgressListener(listener, types)
            }
        }

        log.info("Executing Gradle tasks: {} with args: {}", config.tasks, config.arguments)

        var buildException: Throwable? = null
        val success = try {
            buildLauncher.run()
            log.info("Gradle tasks executed successfully: {}", config.tasks)
            true
        } catch (e: BuildException) {
            // BuildException indicates task execution failure
            log.warn("Gradle build failed for tasks: {}. Message: {}", config.tasks, e.message)
            buildException = e
            false
        } catch (e: GradleConnectionException) {
            // Handle cases where connection might fail during execution phase
            log.error("Gradle connection error during build execution for tasks: {}", config.tasks, e)
            buildException = e
            false
        } catch (e: Exception) {
            log.error("Unexpected error during Gradle build execution for tasks: {}", config.tasks, e)
            buildException = e
            false
        }

        val output = outputStream.toString(StandardCharsets.UTF_8)
        val errorOutput = errorStream.toString(StandardCharsets.UTF_8)

        return BuildResult(
            success = success,
            output = output,
            errorOutput = errorOutput,
            exception = buildException
        )
    }
}
