package me.gulya.gradle.mcp.server.tool

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.gulya.gradle.mcp.config.logger
import me.gulya.gradle.mcp.gradle.GradleService
import me.gulya.gradle.mcp.inputSchema
import me.gulya.gradle.mcp.model.BuildStructureInfo
import me.gulya.gradle.mcp.model.EnvironmentInfo
import me.gulya.gradle.mcp.model.GradleProjectInfoResponse
import me.gulya.gradle.mcp.model.InfoCategory
import me.gulya.gradle.mcp.model.ProjectDetailsInfo
import me.gulya.gradle.mcp.model.SimpleGradleProject
import me.gulya.gradle.mcp.model.TaskInfo
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.gradle.GradleBuild
import java.util.concurrent.CopyOnWriteArrayList

class GetProjectInfoTool : GradleTool {
    private val log = logger<GetProjectInfoTool>()
    override val name = "Get Gradle Project Info"
    override val description = """
        Retrieves specific details about a Gradle project, returning structured JSON.
        Allows requesting only the necessary information for better efficiency.

        Available information categories to request via `requestedInfo`:
        - "buildStructure": Root project name, path, build identifier, and list of subprojects (name and path). Requires fetching `GradleBuild`.
        - "tasks": List of tasks available in the root project (name, path, description). Requires fetching `GradleProject`.
        - "environment": Gradle version, Java home, JVM arguments. Requires fetching `BuildEnvironment`.
        - "projectDetails": Root project's name, Gradle path, description, and build script path. Requires fetching `GradleProject`.

        If `requestedInfo` is not provided, ALL categories will be fetched and returned.
        Provide an empty array `[]` for `requestedInfo` to request no specific data (useful for just validating the path).

        Output: A JSON object containing keys corresponding to the requested categories.
        Includes an `errors` field if fetching specific parts failed.
        """.trimIndent() // Keep description from original

    override val inputSchema = inputSchema {
        requiredProperty("projectPath") {
            type("string")
            description("The absolute path to the root directory of the Gradle project.")
        }
        optionalProperty("requestedInfo") {
            arraySchema {
                type("string")
                description("List of information categories to retrieve. Valid values: ${InfoCategory.entries.joinToString { "'${it.name.lowercase()}'" }}. If omitted, all are returned.")
                // Example of explicit enum if needed by schema generator:
                attribute("enum", JsonArray(InfoCategory.entries.map { JsonPrimitive(it.name) }))
            }
            description("Specifies which categories of information to fetch. If omitted or null, all categories are fetched.")
        }
    }

    override suspend fun execute(
        request: CallToolRequest,
        gradleService: GradleService,
        debug: Boolean
    ): CallToolResult {
        val projectPath = request.arguments.getValue("projectPath").jsonPrimitive.content
        val requestedCategoriesStrings = request.arguments["requestedInfo"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            ?.toSet()

        val effectiveCategories = determineEffectiveCategories(requestedCategoriesStrings)
        if (debug) log.debug("Requesting project info for '{}', categories: {}", projectPath, effectiveCategories)

        val baseResponse = GradleProjectInfoResponse(requestedPath = projectPath)
        val errors = CopyOnWriteArrayList<String>()
        val fetchedResults = mutableMapOf<InfoCategory, Any?>()

        try {
            gradleService.withConnection(projectPath) { connection ->
                // Determine which models to fetch based on requested categories
                val needsGradleProject =
                    effectiveCategories.contains(InfoCategory.TASKS) || effectiveCategories.contains(InfoCategory.PROJECT_DETAILS)
                val needsGradleBuild = effectiveCategories.contains(InfoCategory.BUILD_STRUCTURE)
                val needsBuildEnvironment = effectiveCategories.contains(InfoCategory.ENVIRONMENT)

                var rootProjectModel: GradleProject? = null
                if (needsGradleProject) {
                    try {
                        rootProjectModel = gradleService.fetchModel(connection, GradleProject::class.java)
                        if (effectiveCategories.contains(InfoCategory.TASKS)) fetchedResults[InfoCategory.TASKS] =
                            rootProjectModel
                        if (effectiveCategories.contains(InfoCategory.PROJECT_DETAILS)) fetchedResults[InfoCategory.PROJECT_DETAILS] =
                            rootProjectModel
                    } catch (e: Exception) {
                        val msg = "Failed to fetch GradleProject model: ${e.message}"
                        log.error(msg, e)
                        if (effectiveCategories.contains(InfoCategory.TASKS)) errors.add("Failed to fetch tasks: ${e.message}")
                        if (effectiveCategories.contains(InfoCategory.PROJECT_DETAILS)) errors.add("Failed to fetch project details: ${e.message}")
                    }
                }

                if (needsGradleBuild) {
                    try {
                        fetchedResults[InfoCategory.BUILD_STRUCTURE] =
                            gradleService.fetchModel(connection, GradleBuild::class.java)
                    } catch (e: Exception) {
                        val msg = "Failed to fetch GradleBuild model: ${e.message}"
                        log.error(msg, e)
                        errors.add("Failed to fetch build structure: ${e.message}")
                    }
                }

                if (needsBuildEnvironment) {
                    try {
                        fetchedResults[InfoCategory.ENVIRONMENT] =
                            gradleService.fetchModel(connection, BuildEnvironment::class.java)
                    } catch (e: Exception) {
                        val msg = "Failed to fetch BuildEnvironment model: ${e.message}"
                        log.error(msg, e)
                        errors.add("Failed to fetch environment info: ${e.message}")
                    }
                }
            } // End withConnection block

            // Populate response DTO from fetched results
            val finalResponse = populateResponseDto(baseResponse, fetchedResults, errors)

            val jsonResponse = json.encodeToString(finalResponse)
            if (debug) log.debug("Gradle Project Info Response:\n{}", jsonResponse)
            return CallToolResult(content = listOf(TextContent(jsonResponse)))

        } catch (e: GradleConnectionException) {
            log.error("Gradle connection error for path: {}", projectPath, e)
            val errorResponse =
                baseResponse.copy(errors = (errors + "Gradle connection failed for '$projectPath': ${e.message}").toList())
            return CallToolResult(content = listOf(TextContent(json.encodeToString(errorResponse))))
        } catch (e: IllegalStateException) {
            log.error("Error getting project info (likely invalid project path): {}", projectPath, e)
            val errorResponse =
                baseResponse.copy(errors = (errors + "Failed to get info for '$projectPath' (is it a valid Gradle project?): ${e.message}").toList())
            return CallToolResult(content = listOf(TextContent(json.encodeToString(errorResponse))))
        } catch (e: Exception) {
            log.error("Unexpected error getting Gradle project info for path: {}", projectPath, e)
            val errorResponse =
                baseResponse.copy(errors = (errors + "Unexpected error retrieving info for '$projectPath': ${e.message}").toList())
            return CallToolResult(content = listOf(TextContent(json.encodeToString(errorResponse))))
        }
    }

    private fun determineEffectiveCategories(requested: Set<String>?): Set<InfoCategory> {
        return requested?.mapNotNull {
            try {
                // Handle case-insensitivity and potential JSON quotes if needed
                val normalized = it.trim().removeSurrounding("\"")
                InfoCategory.valueOf(normalized.uppercase())
            } catch (e: Exception) {
                log.warn("Ignoring invalid requestedInfo category: '{}'", it)
                null
            }
        }?.toSet() ?: InfoCategory.entries.toSet() // Default to all if null or empty
    }

    private fun populateResponseDto(
        base: GradleProjectInfoResponse,
        fetched: Map<InfoCategory, Any?>,
        errors: List<String>
    ): GradleProjectInfoResponse {
        return base.copy(
            buildStructure = fetched[InfoCategory.BUILD_STRUCTURE]?.let { model ->
                val build = model as GradleBuild
                BuildStructureInfo(
                    rootProjectName = build.rootProject.name,
                    rootProjectPathGradle = build.rootProject.path,
                    buildIdentifierPath = build.buildIdentifier.rootDir.absolutePath,
                    subprojects = build.projects.map { SimpleGradleProject(it.name, it.path, it.path == ":") }
                )
            },
            tasks = fetched[InfoCategory.TASKS]?.let { model ->
                val project = model as GradleProject
                project.tasks.map { TaskInfo(it.name, it.path, it.description) }
            },
            environment = fetched[InfoCategory.ENVIRONMENT]?.let { model ->
                val env = model as BuildEnvironment
                EnvironmentInfo(
                    gradleVersion = env.gradle.gradleVersion,
                    javaHome = env.java.javaHome.absolutePath,
                    jvmArguments = env.java.jvmArguments ?: emptyList()
                )
            },
            rootProjectDetails = fetched[InfoCategory.PROJECT_DETAILS]?.let { model ->
                val project = model as GradleProject
                ProjectDetailsInfo(
                    name = project.name,
                    path = project.path,
                    description = project.description,
                    buildScriptPath = project.buildScript.sourceFile?.absolutePath
                )
            },
            errors = errors.toList().ifEmpty { null }
        )
    }
}
