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

    private var ktorServer: NettyApplicationEngine? = null

    companion object {
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "mcp_server_channel"
        private const val CHANNEL_NAME = "MCP Server Service"
    }

    override fun onCreate() {
        super.onCreate()
        Logger.log("KtorMcpService: onCreate - Service creating.")
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log("KtorMcpService: onStartCommand - Service starting.")
        serverScope.launch {
            if (ktorServer == null || !ktorServer!!.application.isActive) {
                startKtorServer()
            }
        }
        // 如果服务被杀死，系统会尝试重新启动它
        return START_STICKY
    }

    private fun startKtorServer() {
        Logger.log("KtorMcpService: Attempting to start Ktor server...")
        try {
            // 在 serverScope 中启动 Ktor 服务器
            ktorServer = KtorMcpServer.start()
            Logger.log("KtorMcpService: Ktor server started successfully on port 8080.")
        } catch (e: Exception) {
            Logger.log("!!! KtorMcpService: Error starting Ktor server: ${e.message}")
            e.printStackTrace()
            // 如果启动失败，尝试停止服务以允许系统稍后重启
            stopSelf()
        }
    }

    private fun stopKtorServer() {
        Logger.log("KtorMcpService: Stopping Ktor server...")
        // 优雅地停止服务器，等待最多5秒
        ktorServer?.stop(1000, 5000)
        ktorServer = null
        Logger.log("KtorMcpService: Ktor server stopped.")
    }

    override fun onDestroy() {
        Logger.log("KtorMcpService: onDestroy - Service destroying.")
        // 清理资源
        stopKtorServer()
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
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MCP Server")
            .setContentText("mcpServer正在后台运行...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 替换为你的应用图标
            .setOngoing(true) // 使通知不可滑动清除
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Logger.log("KtorMcpService: Service is now in foreground.")
    }
}
