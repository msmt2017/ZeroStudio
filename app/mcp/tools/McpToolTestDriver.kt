package android.zero.mcp.test

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import kotlinx.coroutines.runBlocking

/**
 * 测试工具：用于在不启动完整 Ktor 服务的前提下，模拟调用 MCP 工具类进行调试
 */
object McpToolTestDriver {

    /**
     * 执行指定 MCP 工具
     * @param tool MCP 工具实例
     * @param params 参数 map
     * @param method 调用名称（通常为 tool 名称）
     * @param id 请求 ID，可选
     */
    fun executeTool(tool: McpTool, params: Map<String, String>, method: String = "testTool", id: String? = "test") {
        val request = McpRequest(
            jsonrpc = "2.0",
            id = id,
            method = method,
            params = params
        )

        val result: McpResponse = runBlocking {
            tool.invoke(request)
        }

        println("\n== MCP TOOL EXECUTION RESULT ==")
        println("Result: ${result.result ?: result.error}")
        println("ID: ${result.id}, jsonrpc: ${result.jsonrpc}")
        println("================================\n")
    }
}
