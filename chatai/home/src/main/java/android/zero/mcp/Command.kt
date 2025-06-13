// File: Command.kt
package android.zero.mcp

sealed class Command {
    abstract val id: String
    abstract val contextId: String?
}

// Generic command for the Operator
data class ExecuteCommand(
    override val id: String,
    override val contextId: String?,
    val type: String,
    val args: Map<String, String>
) : Command()

// Specific commands created by the Interpreter
data class FileCommand(
    override val id: String,
    override val contextId: String?,
    val action: String,
    val args: Map<String, String>
) : Command()

data class ShellCommand(
    override val id: String,
    override val contextId: String?,
    val command: String,
    val args: List<String>
) : Command()

data class GradleCommand(
    override val id: String,
    override val contextId: String?,
    val task: String,
    val args: List<String>
) : Command()

data class TaskCommand(
    override val id: String,
    override val contextId: String?,
    val action: String,
    val args: Map<String, String>
) : Command()

data class TabFileCommand(
    override val id: String,
    override val contextId: String?,
    val action: String,
    val args: Map<String, String>
) : Command()


data class QueryCommand(
    override val id: String,
    override val contextId: String?,
    val query: String
) : Command()