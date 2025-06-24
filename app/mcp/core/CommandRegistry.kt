package app.mcp.core

interface McpCommandTool {
    val meta: CommandMeta
    fun execute(params: Map<String, String>): Result<Any>
}

data class CommandMeta(
    val name: String,
    val description: String,
    val params: List<CommandParam>
)

data class CommandParam(
    val name: String,
    val description: String,
    val required: Boolean = false
)

object CommandRegistry {
    private val tools = mutableMapOf<String, McpCommandTool>()
    fun register(tool: McpCommandTool) { tools[tool.meta.name] = tool }
    fun get(name: String) = tools[name]
    fun all() = tools.values
} 