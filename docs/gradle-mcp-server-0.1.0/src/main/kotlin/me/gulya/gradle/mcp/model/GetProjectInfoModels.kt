package me.gulya.gradle.mcp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class InfoCategory {
    @SerialName("buildStructure") BUILD_STRUCTURE,
    @SerialName("tasks") TASKS,
    @SerialName("environment") ENVIRONMENT,
    @SerialName("projectDetails") PROJECT_DETAILS
}

@Serializable
data class SimpleGradleProject(
    val name: String,
    val path: String,
    @SerialName("is_root") val isRoot: Boolean = false,
)

@Serializable
data class BuildStructureInfo(
    @SerialName("root_project_name") val rootProjectName: String,
    @SerialName("root_project_path_gradle") val rootProjectPathGradle: String,
    @SerialName("build_identifier_path") val buildIdentifierPath: String,
    val subprojects: List<SimpleGradleProject>
)

@Serializable
data class TaskInfo(
    val name: String,
    val path: String,
    val description: String? = null
)

@Serializable
data class EnvironmentInfo(
    @SerialName("gradle_version") val gradleVersion: String,
    @SerialName("java_home") val javaHome: String,
    @SerialName("jvm_arguments") val jvmArguments: List<String>
)

@Serializable
data class ProjectDetailsInfo(
    val name: String,
    val path: String,
    val description: String? = null,
    @SerialName("build_script_path") val buildScriptPath: String? = null
)

@Serializable
data class GradleProjectInfoResponse(
    @SerialName("requested_path") val requestedPath: String,
    @SerialName("build_structure") val buildStructure: BuildStructureInfo? = null,
    val tasks: List<TaskInfo>? = null,
    val environment: EnvironmentInfo? = null,
    @SerialName("root_project_details") val rootProjectDetails: ProjectDetailsInfo? = null,
    val errors: List<String>? = null
)
