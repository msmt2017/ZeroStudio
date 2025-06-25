package android.zero.mcp.policy

/**
 * 工具执行策略管理器：为每个 MCP 工具定义是否需要确认、允许自动执行、限制模型调用等
 */
object ToolExecutionPolicy {

    data class Policy(
        val allowAutoExecute: Boolean = true,
        val requireConfirmation: Boolean = false,
        val allowedModel: String? = null // 可限制为 "gpt-4", "claude", "local" 等
    )

    private val policyMap = mutableMapOf<String, Policy>()

    fun setPolicy(method: String, policy: Policy) {
        policyMap[method] = policy
    }

    fun getPolicy(method: String): Policy =
        policyMap[method] ?: Policy()

    fun isExecutable(method: String, model: String? = null): Boolean {
        val policy = getPolicy(method)
        return when {
            policy.allowedModel != null && policy.allowedModel != model -> false
            else -> true
        }
    }

    fun isAutoExecutable(method: String): Boolean = getPolicy(method).allowAutoExecute
    fun needsConfirmation(method: String): Boolean = getPolicy(method).requireConfirmation

    fun dumpPolicies(): String =
        policyMap.entries.joinToString("\n\n") { (method, p) ->
            "🔧 $method\n✅ 自动执行: ${p.allowAutoExecute}\n🔒 需要确认: ${p.requireConfirmation}\n🤖 限定模型: ${p.allowedModel ?: "不限"}"
        }

    init {
        // 默认策略设置
        setPolicy("shell.execute", Policy(allowAutoExecute = false, requireConfirmation = true))
        setPolicy("File.delete", Policy(allowAutoExecute = false, requireConfirmation = true))
        setPolicy("task.runTask", Policy(allowAutoExecute = true, requireConfirmation = false))
    }
}
