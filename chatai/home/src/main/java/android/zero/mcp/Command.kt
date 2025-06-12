package android.zero.mcp

sealed class Command {
    abstract val id: String
    abstract val contextId: String?
}

data class ExecuteCommand(
    override val id: String,
    override val contextId: String?,
    val type: String,
    val args: Map<String, String>
) : Command() {
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
}

data class QueryCommand(
    override val id: String,
    override val contextId: String?,
    val query: String
) : Command()