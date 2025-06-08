// src/test/kotlin/me/gulya/gradle/mcp/server/tool/ExecuteTaskToolTest.kt
package me.gulya.gradle.mcp.server.tool

// Use JUnit 5 for basic structure and @Test, @BeforeEach etc.
// No longer need Executable explicitly
// --- Import Strikt assertions ---
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsIgnoringCase
import strikt.assertions.isA
import strikt.assertions.isFalse
import strikt.assertions.isNotNull

class ExecuteTaskToolTest : BaseGradleToolIntegrationTest() {

    private val tool = ExecuteTaskTool()

    private fun createRequest(
        tasks: List<String>,
        arguments: List<String>? = null,
        jvmArguments: List<String>? = null,
        environmentVariables: Map<String, String>? = null
    ): CallToolRequest {
        val args = buildJsonObject {
            put("projectPath", JsonPrimitive(testProjectPath)) // Use dynamic path
            put("tasks", JsonArray(tasks.map { JsonPrimitive(it) }))
            arguments?.let { put("arguments", JsonArray(it.map { JsonPrimitive(it) })) }
            jvmArguments?.let { put("jvmArguments", JsonArray(it.map { JsonPrimitive(it) })) }
            environmentVariables?.let {
                put("environmentVariables", JsonObject(it.mapValues { JsonPrimitive(it.value) }))
            }
        }
        val requestName = "execTestId-${tasks.joinToString("-")}"
        return CallToolRequest(name = requestName, arguments = args)
    }

    // Helper to extract text safely using Strikt for validation
    private fun extractText(result: io.modelcontextprotocol.kotlin.sdk.CallToolResult): String {
        // Use Strikt to validate the content and extract text
        // If assertions fail here, the test will fail as expected.
        val content = result.content.firstOrNull()
        expectThat(content)
            .describedAs("Result content") // Add description for better error messages
            .isNotNull()
            .isA<TextContent>()

        // Type cast is safe here due to the assertion above
        return (content as TextContent).text.toString()
    }


    @Test
    fun `should execute customTask successfully`() = runTest { // Use runTest
        val request = createRequest(tasks = listOf("customTask"))
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("ExecuteTaskTool Response (customTask):\n$responseText")

        // --- Use Strikt assertions ---
        expectThat(responseText).contains("=== Gradle Task Execution Summary ===")
        expectThat(responseText).contains("Project Path: $testProjectPath")
        expectThat(responseText).contains("Executed Tasks: customTask")
        expectThat(responseText).contains("Status: Success")
        expectThat(responseText).contains("=== Build Output ===")
        expectThat(responseText).contains("Output from customTask in root project.")
        // FIX: Check for absence using contains().isFalse()
        expectThat(responseText).get { contains("=== Failure Details ===") }.isFalse()
    }

    @Test
    fun `should execute clean task successfully`() = runTest { // Use runTest
        // First build something so clean has work to do
        tool.execute(createRequest(tasks = listOf("build")), gradleService, false)
        // Then run clean
        val request = createRequest(tasks = listOf("clean"))
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("ExecuteTaskTool Response (clean):\n$responseText")

        // --- Use Strikt assertions ---
        expectThat(responseText).contains("Executed Tasks: clean")
        expectThat(responseText).contains("Status: Success")
        expectThat(responseText).contains("BUILD SUCCESSFUL")
    }

    @Test
    fun `should execute task in submodule successfully`() = runTest { // Use runTest
        val request = createRequest(tasks = listOf(":submodule:subCustomTask"))
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("ExecuteTaskTool Response (subCustomTask):\n$responseText")

        // --- Use Strikt assertions ---
        expectThat(responseText).contains("Executed Tasks: :submodule:subCustomTask")
        expectThat(responseText).contains("Status: Success")
        expectThat(responseText).contains("Output from subCustomTask.")
    }

    @Test
    fun `should report failure for non-existent task`() = runTest { // Use runTest
        val request = createRequest(tasks = listOf("nonExistentTask"))
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("ExecuteTaskTool Response (nonExistentTask):\n$responseText")

        // --- Use Strikt assertions ---
        expectThat(responseText).contains("Executed Tasks: nonExistentTask")
        expectThat(responseText).contains("Status: Failure")
        expectThat(responseText).contains("=== Failure Details ===")
        expectThat(responseText).containsIgnoringCase("Task 'nonExistentTask' not found")
        expectThat(responseText).contains("Use Gradle argument '--stacktrace'")
    }

    @Test
    fun `should include stack trace on failure when debug is true`() = runTest { // Use runTest
        val request = createRequest(tasks = listOf("nonExistentTask"))
        val result = tool.execute(request, gradleService, true) // debug = true

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("ExecuteTaskTool Response (nonExistentTask, debug=true):\n$responseText")

        // --- Use Strikt assertions ---
        expectThat(responseText).contains("Status: Failure")
        expectThat(responseText).contains("=== Failure Details ===")
        expectThat(responseText).containsIgnoringCase("Task 'nonExistentTask' not found")
        expectThat(responseText).contains("Stack Trace:")
        expectThat(responseText).contains("org.gradle.execution.TaskSelectionException")
    }

    @Test
    fun `should include stack trace on failure when --stacktrace argument is used`() = runTest { // Use runTest
        val request = createRequest(
            tasks = listOf("nonExistentTask"),
            arguments = listOf("--stacktrace") // Add --stacktrace argument
        )
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("ExecuteTaskTool Response (nonExistentTask, --stacktrace):\n$responseText")

        // --- Use Strikt assertions ---
        expectThat(responseText).contains("Status: Failure")
        expectThat(responseText).contains("=== Failure Details ===")
        expectThat(responseText).containsIgnoringCase("Task 'nonExistentTask' not found")
        expectThat(responseText).contains("Stack Trace:")
        expectThat(responseText).contains("org.gradle.execution.TaskSelectionException")
    }

    @Test
    fun `should execute test task with failure and report environment variables`() = runTest { // Use runTest
        // This test runs the 'test' task using ExecuteTaskTool.
        // Since the fixture contains a failing test, the overall execution should fail.
        // We cannot use --tests for filtering here via ExecuteTaskTool arguments.
        // We *can* check if general arguments (like -P) and env vars are passed.
        val request = createRequest(
            tasks = listOf("test"),
            arguments = listOf("-PmyTestProp=argValue"), // Example of a general argument
            environmentVariables = mapOf("TEST_ENV_VAR" to "fixture_value")
        )
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("ExecuteTaskTool Response (test task, expect failure):\n$responseText")

        // --- Use Strikt assertions ---
        expectThat(responseText).contains("Executed Tasks: test")
        expectThat(responseText).contains("Arguments: -PmyTestProp=argValue") // Check general arg
        expectThat(responseText).contains("Environment Variables: TEST_ENV_VAR=fixture_value")
        // Expect FAILURE because rootFailingTest runs and isn't filtered
        expectThat(responseText).contains("Status: Failure")
        expectThat(responseText).contains("=== Build Output ===")
        // Check output from BOTH tests is present, as filtering didn't happen
        expectThat(responseText).contains("STDOUT from rootPassingTest")
        expectThat(responseText).contains("STDERR from rootFailingTest")
        // Check failure details are present
        expectThat(responseText).contains("=== Failure Details ===")
        // Check for a common test failure indicator in the output/error
        expectThat(responseText).contains("BUILD FAILED") // Gradle's overall failure message
        expectThat(responseText).contains("There were failing tests") // Test task specific failure message
    }
}
