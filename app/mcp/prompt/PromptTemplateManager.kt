package android.zero.mcp.prompt

/**
 * Prompt 模板管理器：为每个工具注册提示词模板，用于指导 LLM 如何调用该工具
 */
object PromptTemplateManager {

    private val templateMap = mutableMapOf<String, String>()

    /**
     * 绑定提示模板
     */
    fun registerTemplate(method: String, prompt: String) {
        templateMap[method] = prompt
    }

    /**
     * 获取某工具的模板
     */
    fun getTemplate(method: String): String? = templateMap[method]

    /**
     * 移除某个模板
     */
    fun removeTemplate(method: String) {
        templateMap.remove(method)
    }

    /**
     * 导出所有提示词信息
     */
    fun dumpAll(): String =
        templateMap.entries.joinToString("\n\n") { (method, prompt) ->
            "🔧 $method\n📌 $prompt"
        }

    init {
        // 默认模板注册示例
        registerTemplate(
            "File.search",
            "请帮我在当前工作区的 src 或 java 目录下搜索包含关键词 \"关键字\" 的文件内容。"
        )
        registerTemplate(
            "task.runTask",
            "请帮我运行 Gradle 的构建任务，如 :assembleDebug 或 :clean"
        )
        registerTemplate(
            "shell.execute",
            "请以终端方式执行命令，例如 ls /data 或 pm list packages"
        )
    }
}
