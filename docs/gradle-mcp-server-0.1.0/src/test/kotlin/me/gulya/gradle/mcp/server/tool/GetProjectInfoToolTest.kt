package me.gulya.gradle.mcp.server.tool

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.gulya.gradle.mcp.model.GradleProjectInfoResponse
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.any
import strikt.assertions.contains
import strikt.assertions.doesNotContain
import strikt.assertions.endsWith
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue
import strikt.assertions.map
import java.io.File

class GetProjectInfoToolTest : BaseGradleToolIntegrationTest() {

    private val tool = GetProjectInfoTool()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun createRequest(requestedInfo: List<String>? = null): CallToolRequest {
        val args = buildJsonObject {
            put("projectPath", JsonPrimitive(testProjectPath))
            requestedInfo?.let {
                put("requestedInfo", JsonArray(it.map { JsonPrimitive(it) }))
            }
        }
        // Use 'id' as fixed previously
        return CallToolRequest(name = "getInfoTestId-${requestedInfo?.joinToString("-") ?: "all"}", arguments = args)
    }

    // Helper to extract text safely
    private fun extractText(result: io.modelcontextprotocol.kotlin.sdk.CallToolResult): String {
        lateinit var text: String
        assertDoesNotThrow({
            val content = result.content.firstOrNull()
            expectThat(content).isNotNull().isA<TextContent>()
            text = (content as TextContent).text!!
        }, "Should be able to extract text content")
        return text
    }

    @Test
    fun `should retrieve all project info from fixture`() = runTest { // Use runTest
        val request = createRequest()
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("GetProjectInfoTool Response (All):\n$responseText") // Print after extraction

        // Decode after extracting string
        val response = json.decodeFromString<GradleProjectInfoResponse>(responseText)

        // --- Use Strikt assertions ---
        expectThat(response.errors).isNull()
        expectThat(response.requestedPath).isEqualTo(testProjectPath)

        // Build Structure Assertions
        expectThat(response.buildStructure).isNotNull().and {
            get { rootProjectName }.isEqualTo("test-fixture-project")
            // Compare canonical paths to handle symlinks like /var -> /private/var
            get { buildIdentifierPath }.isEqualTo(tempProjectDir.canonicalPath)
            get { subprojects }.hasSize(2).and {
                any {
                    // Check root project properties directly chained
                    get { name }.isEqualTo("test-fixture-project")
                    get { path }.isEqualTo(":")
                    get { isRoot }.isTrue()
                }
                any {
                    // Check submodule properties directly chained
                    get { name }.isEqualTo("submodule")
                    get { path }.isEqualTo(":submodule")
                    get { isRoot }.isFalse()
                }
            }
        }

        // Tasks Assertions
        expectThat(response.tasks).isNotNull().and {
            map { it.name }.contains("build", "clean", "test", "customTask")
            map { it.name }.doesNotContain("subCustomTask")
            any {
                and { // nested 'and' is fine here as it's checking properties of a single task found by 'any'
                    get { name }.isEqualTo("customTask")
                    get { description }.isEqualTo("A simple custom task for testing.")
                }
            }
        }

        // Root Project Details Assertions
        expectThat(response.rootProjectDetails).isNotNull().and {
            get { name }.isEqualTo("test-fixture-project")
            get { path }.isEqualTo(":")
            // Use endsWith for build script path check - less sensitive to /private/var vs /var
            get { buildScriptPath }.isNotNull().and { // Keep isNotNull
                endsWith("build.gradle.kts")
            }
        }
    }

    @Test
    fun `should retrieve only tasks and environment info from fixture`() = runTest { // Use runTest
        val request = createRequest(listOf("tasks", "environment"))
        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("GetProjectInfoTool Response (Tasks, Env):\n$responseText") // Print after extraction

        // Decode after extracting string
        val response = json.decodeFromString<GradleProjectInfoResponse>(responseText)

        // --- Use Strikt assertions ---
        expectThat(response.errors).isNull()
        expectThat(response.tasks).isNotNull()
        expectThat(response.environment).isNotNull()
        expectThat(response.buildStructure).isNull()
        expectThat(response.rootProjectDetails).isNull()
    }

    @Test
    fun `should handle invalid project path gracefully`() = runTest { // Use runTest
        // Use File for path construction
        val invalidPath = File(tempProjectDir, "non-existent-dir").absolutePath
        val requestArgs = buildJsonObject { put("projectPath", JsonPrimitive(invalidPath)) }
        // Use 'id'
        val request = CallToolRequest(name = "getInfoTestId-invalid", arguments = requestArgs)

        val result = tool.execute(request, gradleService, false)

        // --- Extract text content safely ---
        val responseText = extractText(result)
        println("GetProjectInfoTool Response (Invalid Path):\n$responseText") // Print after extraction

        // Decode after extracting string
        val response = json.decodeFromString<GradleProjectInfoResponse>(responseText)

        expectThat(response.errors).isNotNull().and {
            hasSize(1) // Expecting one specific error message
            any { // Check the contents of the single error message
                contains("Failed to get info for")
                contains("is it a valid Gradle project?")
                contains("Project path does not exist or is not a directory")
            }
        }
        expectThat(response.buildStructure).isNull()
        expectThat(response.tasks).isNull()
        expectThat(response.environment).isNull()
        expectThat(response.rootProjectDetails).isNull()
    }
}
