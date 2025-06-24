package android.zero.mcp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.zero.mcp.R
import android.zero.mcp.server.KtorMcpServer
import android.zero.mcp.utils.Logger
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*

/**
 * 一个持久化的前台服务，用于托管 Ktor MCP 服务器。
 */
class KtorMcpService : Service() {

    private val serverJob = Job()
    // 为服务器操作创建一个专用的 CoroutineScope
    private val serverScope = CoroutineScope(Dispatchers.IO + serverJob)

    private var ktorServer: KtorMcpServer? = null
    private var serverEngine: NettyApplicationEngine? = null

    companion object {
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "mcp_server_channel"
        private const val CHANNEL_NAME = "MCP Server Service"
        
        // 服务器配置
        const val SERVER_PORT = 8080
        const val SERVER_HOST = "0.0.0.0"
    }

    override fun onCreate() {
        super.onCreate()
        Logger.log("KtorMcpService: onCreate - Service creating.")
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("KtorMcpService: onStartCommand - Service starting.")
        
        when (intent?.action) {
            "START_SERVER" -> startServer()
            "STOP_SERVER" -> stopServer()
            else -> startServer() // 默认启动服务器
        }
        
        // 如果服务被杀死，系统会尝试重新启动它
        return START_STICKY
    }

    private fun startServer() {
        serverScope.launch {
            try {
                if (ktorServer == null) {
                    Logger.log("KtorMcpService: Creating new Ktor MCP server...")
                    ktorServer = KtorMcpServer(this@KtorMcpService)
                }
                
                if (serverEngine == null || !serverEngine!!.application.isActive) {
                    Logger.log("KtorMcpService: Starting Ktor server on $SERVER_HOST:$SERVER_PORT...")
                    serverEngine = ktorServer!!.start(SERVER_PORT, SERVER_HOST)
                    Logger.log("KtorMcpService: Ktor server started successfully.")
                    
                    // 更新通知
                    updateNotification("MCP服务器运行中 - $SERVER_HOST:$SERVER_PORT")
                } else {
                    Logger.log("KtorMcpService: Server already running.")
                }
                
            } catch (e: Exception) {
                Logger.log("!!! KtorMcpService: Error starting Ktor server: ${e.message}")
                e.printStackTrace()
                updateNotification("MCP服务器启动失败: ${e.message}")
                
                // 如果启动失败，尝试停止服务以允许系统稍后重启
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        serverScope.launch {
            try {
                Logger.log("KtorMcpService: Stopping Ktor server...")
                ktorServer?.stop()
                serverEngine = null
                ktorServer = null
                Logger.log("KtorMcpService: Ktor server stopped.")
                updateNotification("MCP服务器已停止")
            } catch (e: Exception) {
                Logger.log("!!! KtorMcpService: Error stopping Ktor server: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        Logger.log("KtorMcpService: onDestroy - Service destroying.")
        stopServer()
        serverJob.cancel() // 取消所有协程
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 我们不提供绑定，所以返回 null
        return null
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // 为 Android 8.0+ 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                CHANNEL_NAME, 
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "MCP服务器后台服务通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Server")
            .setContentText("MCP服务器正在启动...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Logger.log("KtorMcpService: Service is now in foreground.")
    }
    
    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Server")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
            
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
