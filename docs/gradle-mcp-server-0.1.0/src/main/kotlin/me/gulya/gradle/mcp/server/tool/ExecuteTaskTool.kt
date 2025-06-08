package me.gulya.gradle.mcp.server.tool

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.gulya.gradle.mcp.config.logger
import me.gulya.gradle.mcp.gradle.GradleService
import me.gulya.gradle.mcp.inputSchema

class ExecuteTaskTool : GradleTool {
    private val log = logger<ExecuteTaskTool>()
    override val name = "Execute Gradle Task"
    override val description = """
        Executes one or more specified Gradle tasks in a project.
        **Use this for general build lifecycle tasks** (like 'clean', 'build', 'assemble', 'publish') or **custom tasks** defined in the build scripts.
        **DO NOT use this tool to run tests if you need detailed, per-test results and output.** Use the 'Run Gradle Tests' tool for testing instead.

        Allows customization:
        - `tasks`: List of task names to execute (e.g., ['clean', 'build']). Order matters.
        - `arguments`: Optional list of Gradle command-line arguments (e.g., ['--info', '--stacktrace', '-PmyProperty=value', '--rerun-tasks']).
        - `jvmArguments`: Optional list of JVM arguments for the Gradle process itself (e.g., ['-Xmx4g']).
        - `environmentVariables`: Optional map of environment variables for the build (e.g., {"CI": "true"}).

        **Output:** Returns a formatted text response containing:
        - A summary of the request parameters (tasks, arguments, etc.).
        - A final `Status:` line indicating overall `Success` or `Failure`.
        - The combined standard output (stdout) and standard error (stderr) streams captured from the Gradle execution.
        - If the status is `Failure`, additional error details might be included.

        **Check the 'Status' line and carefully review the 'Build Output' section for build logs, warnings, and specific error messages.**
        Note: Executing tasks like 'build' can modify files in the project directory.
        """.trimIndent() // Keep original description

    override val inputSchema = inputSchema {
        requiredProperty("projectPath") {
            type("string")
            description("The absolute path to the root directory of the Gradle project.")
        }
        requiredProperty("tasks") {
            arraySchema { type("string") }
            description("List of Gradle task names to execute in the specified order (e.g., ['clean', 'build']). Use 'Get Gradle Project Info' to find available tasks.")
        }
        optionalProperty("arguments") {
            arraySchema { type("string") }
            description("List of command-line arguments to pass directly to Gradle (e.g., ['--info', '--stacktrace', '-PmyProp=value']).")
        }
        optionalProperty("jvmArguments") {
            arraySchema { type("string") }
            description("List of JVM arguments specifically for the Gradle daemon/process (e.g., ['-Xmx4g', '-Dfile.encoding=UTF-8']).")
        }
        optionalProperty("environmentVariables") {
            type("object")
            attribute("additionalProperties", JsonObject(mapOf("type" to JsonPrimitive("string"))))
            description("Map of environment variables to set for the Gradle build process (e.g., {\"CI\":\"true\", \"MY_API_KEY\":\"secret\"}).")
        }
    }

    override suspend fun execute(
        request: CallToolRequest,
        gradleService: GradleService,
        debug: Boolean
    ): CallToolResult {
        val projectPath = request.arguments.getValue("projectPath").jsonPrimitive.content
        val tasks = request.arguments.getValue("tasks").jsonArray.map { it.jsonPrimitive.content }
        val arguments = request.arguments["arguments"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val jvmArguments = request.arguments["jvmArguments"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val environmentVariables =
            request.arguments["environmentVariables"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }
                ?: emptyMap()

        val executionConfig = GradleService.BuildExecutionConfig(
            tasks = tasks,
            arguments = arguments,
            jvmArguments = jvmArguments,
            environmentVariables = environmentVariables
        )

        try {
            val result = gradleService.withConnection(projectPath) { connection ->
                gradleService.executeBuild(connection, executionConfig)
            }

            val responseText = formatResponse(projectPath, executionConfig, result, debug, arguments)
            return CallToolResult(content = listOf(TextContent(responseText)))

        } catch (e: Exception) {
            log.error("Error setting up or executing Gradle task for path: {}", projectPath, e)
            val errorText = formatSetupError(projectPath, executionConfig, e, debug)
            return CallToolResult(content = listOf(TextContent(errorText)))
        }
    }

    private fun formatResponse(
        projectPath: String,
        config: GradleService.BuildExecutionConfig,
        result: GradleService.BuildResult,
        debug: Boolean,
        originalArgs: List<String> // Need original args to check for --stacktrace
    ): String {
        val combinedOutput = buildString {
            if (result.output.isNotBlank()) {
                appendLine("--- Standard Output ---")
                appendLine(result.output.trimEnd())
            }
            if (result.errorOutput.isNotBlank()) {
                appendLine("--- Standard Error ---")
                appendLine(result.errorOutput.trimEnd())
            }
            if (result.output.isBlank() && result.errorOutput.isBlank()) {
                appendLine("[No output captured from stdout or stderr]")
            }
        }.trim()

        return buildString {
            appendLine("=== Gradle Task Execution Summary ===")
            appendLine("Project Path: $projectPath")
            appendLine("Executed Tasks: ${config.tasks.joinToString(", ")}")
            appendLine("Arguments: ${config.arguments.joinToString(" ")}")
            appendLine("JVM Arguments: ${config.jvmArguments.joinToString(" ")}")
            appendLine(
                "Environment Variables: ${
                    config.environmentVariables.map { "${it.key}=${it.value}" }.joinToString(", ")
                }"
            )
            appendLine("Status: ${if (result.success) "Success" else "Failure"}")
            appendLine()
            appendLine("=== Build Output ===")
            appendLine(combinedOutput)

            if (!result.success) {
                appendLine()
                appendLine("=== Failure Details ===")
                val exception = result.exception
                appendLine("Error: ${exception?.message ?: "Unknown execution error."}")
                if (debug || originalArgs.contains("--stacktrace")) {
                    exception?.let { appendLine("\nStack Trace:\n${it.stackTraceToString()}") }
                } else {
                    appendLine("(Use Gradle argument '--stacktrace' or run server with '--debug' for full stack trace)")
                }
            }
        }.trimIndent()
    }

    private fun formatSetupError(
        projectPath: String,
        config: GradleService.BuildExecutionConfig,
        exception: Exception,
        debug: Boolean
    ): String {
        return """
            === Gradle Task Execution Failed ===
            Project Path: $projectPath
            Attempted Tasks: ${config.tasks.joinToString(", ")}
            Error: Failed during setup, connection, or before task execution could complete.
            Message: ${exception.message}
            ${if (debug) "\nStack Trace:\n${exception.stackTraceToString()}" else ""}
        """.trimIndent()
    }
}
