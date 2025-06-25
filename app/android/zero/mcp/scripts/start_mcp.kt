package android.zero.mcp.scripts

import android.content.Context
import android.zero.mcp.McpServer
import android.zero.mcp.McpInitManager
import android.zero.mcp.services.McpServiceManager
import android.zero.mcp.utils.McpTestUtils
import android.zero.mcp.utils.Logger

/**
 * MCP 系统启动脚本
 * 提供快速启动和测试功能
 */
object McpStartupScript {
    
    /**
     * 启动 MCP 系统
     */
    fun startMcpSystem(context: Context, enableTest: Boolean = false): Boolean {
        return try {
            Logger.log("=== 启动 MCP 系统 ===")
            
            // 1. 初始化 MCP 服务器
            val mcpServer = McpServer.getInstance()
            McpInitManager.initialize(context, mcpServer)
            Logger.log("✅ MCP 服务器初始化完成")
            
            // 2. 启动 MCP 服务
            val serviceManager = McpServiceManager.getInstance(context)
            serviceManager.startMcpService()
            Logger.log("✅ MCP 服务启动完成")
            
            // 3. 等待服务启动
            Thread.sleep(3000)
            
            // 4. 连接到服务器
            val isConnected = kotlinx.coroutines.runBlocking {
                serviceManager.connectToServer()
            }
            
            if (isConnected) {
                Logger.log("✅ MCP 客户端连接成功")
            } else {
                Logger.log("❌ MCP 客户端连接失败")
                return false
            }
            
            // 5. 运行测试（可选）
            if (enableTest) {
                Logger.log("=== 运行系统测试 ===")
                val testResult = McpTestUtils.runFullTest(context)
                Logger.log(testResult.getDetailedReport())
                
                if (!testResult.isAllPassed()) {
                    Logger.log("⚠️ 部分测试失败，但系统仍可运行")
                }
            }
            
            Logger.log("=== MCP 系统启动完成 ===")
            true
            
        } catch (e: Exception) {
            Logger.log("❌ MCP 系统启动失败: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 停止 MCP 系统
     */
    fun stopMcpSystem(context: Context) {
        try {
            Logger.log("=== 停止 MCP 系统 ===")
            
            // 1. 停止服务
            val serviceManager = McpServiceManager.getInstance(context)
            serviceManager.stopMcpService()
            Logger.log("✅ MCP 服务已停止")
            
            // 2. 清理资源
            McpInitManager.cleanup(context)
            Logger.log("✅ 资源清理完成")
            
            Logger.log("=== MCP 系统已停止 ===")
            
        } catch (e: Exception) {
            Logger.log("❌ 停止 MCP 系统时发生错误: ${e.message}")
        }
    }
    
    /**
     * 重启 MCP 系统
     */
    fun restartMcpSystem(context: Context, enableTest: Boolean = false): Boolean {
        Logger.log("=== 重启 MCP 系统 ===")
        
        stopMcpSystem(context)
        Thread.sleep(2000)
        
        return startMcpSystem(context, enableTest)
    }
    
    /**
     * 检查系统状态
     */
    fun checkSystemStatus(context: Context): SystemStatus {
        return try {
            val serviceManager = McpServiceManager.getInstance(context)
            
            val connectionState = serviceManager.connectionState.value
            val isConnected = kotlinx.coroutines.runBlocking {
                serviceManager.checkConnection()
            }
            
            val client = serviceManager.getClient()
            val hasClient = client != null
            
            SystemStatus(
                connectionState = connectionState,
                isConnected = isConnected,
                hasClient = hasClient,
                serverUrl = serviceManager.serverUrl.value
            )
            
        } catch (e: Exception) {
            SystemStatus(
                connectionState = McpServiceManager.ConnectionState.FAILED,
                isConnected = false,
                hasClient = false,
                serverUrl = "unknown",
                error = e.message
            )
        }
    }
    
    /**
     * 系统状态类
     */
    data class SystemStatus(
        val connectionState: McpServiceManager.ConnectionState,
        val isConnected: Boolean,
        val hasClient: Boolean,
        val serverUrl: String,
        val error: String? = null
    ) {
        fun isHealthy(): Boolean = isConnected && hasClient && error == null
        
        fun getStatusString(): String {
            return when {
                isHealthy() -> "健康"
                connectionState == McpServiceManager.ConnectionState.CONNECTING -> "连接中"
                connectionState == McpServiceManager.ConnectionState.FAILED -> "连接失败"
                connectionState == McpServiceManager.ConnectionState.DISCONNECTED -> "未连接"
                else -> "未知状态"
            }
        }
    }
    
    /**
     * 快速诊断
     */
    fun quickDiagnosis(context: Context): DiagnosisResult {
        val result = DiagnosisResult()
        
        try {
            // 1. 检查系统状态
            val status = checkSystemStatus(context)
            result.addCheck("系统状态", status.isHealthy(), status.getStatusString())
            
            // 2. 检查服务连接
            val serviceManager = McpServiceManager.getInstance(context)
            val isConnected = kotlinx.coroutines.runBlocking {
                serviceManager.checkConnection()
            }
            result.addCheck("服务连接", isConnected, if (isConnected) "正常" else "失败")
            
            // 3. 检查工具可用性
            val client = serviceManager.getClient()
            if (client != null) {
                val toolsAvailable = kotlinx.coroutines.runBlocking {
                    try {
                        val tools = client.getToolsList()
                        tools != null
                    } catch (e: Exception) {
                        false
                    }
                }
                result.addCheck("工具可用性", toolsAvailable, if (toolsAvailable) "正常" else "失败")
            } else {
                result.addCheck("工具可用性", false, "客户端未连接")
            }
            
        } catch (e: Exception) {
            result.addCheck("诊断过程", false, "诊断失败: ${e.message}")
        }
        
        return result
    }
    
    /**
     * 诊断结果类
     */
    data class DiagnosisResult(
        val checks: MutableList<DiagnosisCheck> = mutableListOf()
    ) {
        fun addCheck(name: String, passed: Boolean, message: String) {
            checks.add(DiagnosisCheck(name, passed, message))
        }
        
        fun isAllPassed(): Boolean = checks.all { it.passed }
        
        fun getSummary(): String {
            val total = checks.size
            val passed = checks.count { it.passed }
            return "诊断结果: $passed/$total 项通过"
        }
        
        fun getDetailedReport(): String {
            val report = StringBuilder()
            report.appendLine("=== MCP 系统诊断报告 ===")
            
            checks.forEach { check ->
                val status = if (check.passed) "✅" else "❌"
                report.appendLine("$status ${check.name}: ${check.message}")
            }
            
            report.appendLine("总结: ${getSummary()}")
            return report.toString()
        }
    }
    
    /**
     * 诊断检查项类
     */
    data class DiagnosisCheck(
        val name: String,
        val passed: Boolean,
        val message: String
    )
} 