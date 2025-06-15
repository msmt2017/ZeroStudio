package android.zero.mcp.cli

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.registry.McpToolRegistry
import kotlinx.coroutines.runBlocking

/**
 * 命令行入口：用于通过 JVM 启动 CLI 工具测试 MCP 工具
 * 示例运行：  kotlin -classpath ... McpCliKt tool.methodName key1=value1 key2=value2
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("⚠️ 请输入要测试的工具方法名，例如 TabFile.getCursor")
        return
    }

    val method = args[0]
    val params = args.drop(1).mapNotNull {
        val parts = it.split("=")
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()

    val tool: McpTool? = McpToolRegistry.getRegisteredTool(method)
    if (tool == null) {
        println("❌ 工具未找到: $method")
        return
    }

    val request = McpRequest(
        id = "cli-test",
        method = method,
        params = params.toMutableMap()
    )

    val response: McpResponse = runBlocking {
        tool.invoke(request)
    }

    println("\n✅ MCP CLI 调用结果:")
    println("Result: ${response.result ?: response.error}")
    println("======================================")
}