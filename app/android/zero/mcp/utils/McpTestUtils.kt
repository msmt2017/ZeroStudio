package android.zero.mcp.utils

import android.content.Context
import android.zero.mcp.McpRequest
import android.zero.mcp.services.McpServiceManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * MCP 测试工具
 * 用于验证 MCP 系统的各项功能
 */
object McpTestUtils {
    
    /**
     * 运行完整的 MCP 系统测试
     */
    fun runFullTest(context: Context): TestResult {
        val result = TestResult()
        
        try {
            Logger.log("开始 MCP 系统测试...")
            
            // 1. 测试服务管理器
            testServiceManager(context, result)
            
            // 2. 测试服务器连接
            testServerConnection(context, result)
            
            // 3. 测试工具调用
            testToolCalls(context, result)
            
            // 4. 测试事件系统
            testEventSystem(context, result)
            
            Logger.log("MCP 系统测试完成: ${result.summary()}")
            
        } catch (e: Exception) {
            Logger.log("测试过程中发生错误: ${e.message}")
            result.addError("测试异常", e.message ?: "未知错误")
        }
        
        return result
    }
    
    /**
     * 测试服务管理器
     */
    private fun testServiceManager(context: Context, result: TestResult) {
        try {
            Logger.log("测试服务管理器...")
            
            val serviceManager = McpServiceManager.getInstance(context)
            
            // 测试启动服务
            serviceManager.startMcpService()
            result.addSuccess("服务管理器", "服务启动成功")
            
            // 等待服务启动
            Thread.sleep(2000)
            
        } catch (e: Exception) {
            result.addError("服务管理器", e.message ?: "未知错误")
        }
    }
    
    /**
     * 测试服务器连接
     */
    private fun testServerConnection(context: Context, result: TestResult) {
        try {
            Logger.log("测试服务器连接...")
            
            val serviceManager = McpServiceManager.getInstance(context)
            
            runBlocking {
                val isConnected = serviceManager.connectToServer()
                if (isConnected) {
                    result.addSuccess("服务器连接", "连接成功")
                } else {
                    result.addError("服务器连接", "连接失败")
                }
            }
            
        } catch (e: Exception) {
            result.addError("服务器连接", e.message ?: "未知错误")
        }
    }
    
    /**
     * 测试工具调用
     */
    private fun testToolCalls(context: Context, result: TestResult) {
        try {
            Logger.log("测试工具调用...")
            
            val serviceManager = McpServiceManager.getInstance(context)
            val client = serviceManager.getClient()
            
            if (client == null) {
                result.addError("工具调用", "客户端未连接")
                return
            }
            
            runBlocking {
                // 测试获取服务器信息
                val info = client.getServerInfo()
                if (info != null) {
                    result.addSuccess("工具调用", "获取服务器信息成功")
                } else {
                    result.addError("工具调用", "获取服务器信息失败")
                }
                
                // 测试获取工具列表
                val tools = client.getToolsList()
                if (tools != null) {
                    result.addSuccess("工具调用", "获取工具列表成功")
                } else {
                    result.addError("工具调用", "获取工具列表失败")
                }
                
                // 测试 TabFile 工具
                testTabFileTools(client, result)
            }
            
        } catch (e: Exception) {
            result.addError("工具调用", e.message ?: "未知错误")
        }
    }
    
    /**
     * 测试 TabFile 工具
     */
    private suspend fun testTabFileTools(client: android.zero.mcp.client.McpClient, result: TestResult) {
        try {
            // 测试获取当前文件
            val request = McpRequest(
                id = "test_1",
                method = "TabFile.getCurrentFile"
            )
            
            val response = client.sendRequest(request)
            if (response is android.zero.mcp.McpResponse) {
                result.addSuccess("TabFile工具", "getCurrentFile 调用成功")
            } else {
                result.addError("TabFile工具", "getCurrentFile 调用失败")
            }
            
        } catch (e: Exception) {
            result.addError("TabFile工具", e.message ?: "未知错误")
        }
    }
    
    /**
     * 测试事件系统
     */
    private fun testEventSystem(context: Context, result: TestResult) {
        try {
            Logger.log("测试事件系统...")
            
            val serviceManager = McpServiceManager.getInstance(context)
            val eventFlow = serviceManager.connectToEvents()
            
            if (eventFlow != null) {
                result.addSuccess("事件系统", "事件流创建成功")
            } else {
                result.addError("事件系统", "事件流创建失败")
            }
            
        } catch (e: Exception) {
            result.addError("事件系统", e.message ?: "未知错误")
        }
    }
    
    /**
     * 快速连接测试
     */
    fun quickConnectionTest(context: Context): Boolean {
        return try {
            val serviceManager = McpServiceManager.getInstance(context)
            
            runBlocking {
                serviceManager.startMcpService()
                Thread.sleep(1000)
                serviceManager.connectToServer()
            }
            
        } catch (e: Exception) {
            Logger.log("快速连接测试失败: ${e.message}")
            false
        }
    }
    
    /**
     * 测试结果类
     */
    data class TestResult(
        val successes: MutableList<TestItem> = mutableListOf(),
        val errors: MutableList<TestItem> = mutableListOf()
    ) {
        
        fun addSuccess(category: String, message: String) {
            successes.add(TestItem(category, message))
            Logger.log("✅ $category: $message")
        }
        
        fun addError(category: String, message: String) {
            errors.add(TestItem(category, message))
            Logger.log("❌ $category: $message")
        }
        
        fun summary(): String {
            val total = successes.size + errors.size
            val successRate = if (total > 0) (successes.size * 100 / total) else 0
            return "总计: $total, 成功: ${successes.size}, 失败: ${errors.size}, 成功率: $successRate%"
        }
        
        fun isAllSuccess(): Boolean = errors.isEmpty()
        
        fun getDetailedReport(): String {
            val report = StringBuilder()
            report.appendLine("=== MCP 系统测试报告 ===")
            report.appendLine("成功项目 (${successes.size}):")
            successes.forEach { item ->
                report.appendLine("  ✅ ${item.category}: ${item.message}")
            }
            
            if (errors.isNotEmpty()) {
                report.appendLine("失败项目 (${errors.size}):")
                errors.forEach { item ->
                    report.appendLine("  ❌ ${item.category}: ${item.message}")
                }
            }
            
            report.appendLine("总结: ${summary()}")
            return report.toString()
        }
    }
    
    /**
     * 测试项目类
     */
    data class TestItem(
        val category: String,
        val message: String
    )
} 