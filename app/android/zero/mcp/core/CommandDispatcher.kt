package app.mcp.core

object CommandDispatcher {
    fun dispatch(command: Command): Result<Any> {
        val tool = CommandRegistry.get(command.name)
            ?: return Result.failure(Exception("Unknown command: ${command.name}"))
        return tool.execute(command.params)
    }
} 