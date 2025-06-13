package com.example.ktormcpapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 后台运行 Ktor 服务器的 Android 服务。
 * 作为前台服务运行，以便在通知栏显示。
 */
class KtorService : Service() {

    private val TAG = "KtorService"
    private val CHANNEL_ID = "KtorServiceChannel"
    private val NOTIFICATION_ID = 1
    private val ktorServerManager = KtorServerManager()
    private var serviceJob: Job? = null // 用于管理协程的 Job

    // 可以在这里定义一个 Binder 接口，以便 Activity 可以与服务通信
    // 但对于简单的启动/停止操作，使用 Intent 传递数据可能更方便
    override fun onBind(intent: Intent?): IBinder? {
        return null // 不提供绑定服务
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KtorService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KtorService onStartCommand")

        val action = intent?.action
        val port = intent?.getIntExtra("PORT", 8080) ?: 8080 // 获取传递的端口

        when (action) {
            "START_SERVER" -> {
                if (!ktorServerManager.isRunning()) {
                    startKtorServer(port)
                } else {
                    Log.d(TAG, "服务器已在运行，无需重复启动。")
                    // 更新通知以反映当前状态
                    updateNotification("Ktor 服务器正在运行...", "IP: ${ktorServerManager.getLocalIpAddress()}, 端口: $port")
                }
            }
            "STOP_SERVER" -> {
                stopKtorServer()
            }
            else -> {
                // 如果服务被系统重新创建，但没有明确的 START_SERVER 命令，
                // 我们可以尝试在没有明确命令时重新启动服务器，或者等待用户从 UI 启动。
                // 这里我们选择等待用户启动，以避免不必要的资源消耗。
                Log.d(TAG, "KtorService 收到未知命令或无命令，保持待命。")
                stopSelf() // 如果没有明确指令，停止自身，让系统根据需要处理
            }
        }

        // START_STICKY 意味着如果系统杀死服务，它会在内存可用时尝试重新创建，
        // 但不会重新传递最后一个 Intent。适合不需要保存状态的服务。
        return START_STICKY
    }

    private fun startKtorServer(port: Int) {
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            val localIp = ktorServerManager.getLocalIpAddress()
            ktorServerManager.startServer(
                port = port,
                host = "0.0.0.0", // 监听所有接口，包括内网 IP
                onStarted = { actualHost, actualPort ->
                    val message = "Ktor 服务器正在运行..."
                    val details = "IP: $localIp, 端口: $actualPort"
                    Log.i(TAG, "$message $details")
                    updateNotification(message, details)
                },
                onError = { e ->
                    val message = "Ktor 服务器启动失败: ${e.message}"
                    Log.e(TAG, message)
                    updateNotification("服务器启动失败", message)
                    stopForeground(STOP_FOREGROUND_REMOVE) // 停止前台服务并移除通知
                    stopSelf() // 停止服务
                }
            )
        }
    }

    private fun stopKtorServer() {
        serviceJob?.cancel() // 取消正在运行的协程
        ktorServerManager.stopServer {
            Log.i(TAG, "Ktor 服务器已停止。")
            updateNotification("Ktor 服务器已停止", "点击启动服务器")
            // 对于停止状态，可以考虑不再是前台服务，从而移除通知
            stopForeground(STOP_FOREGROUND_REMOVE) // 停止前台服务并移除通知
            stopSelf() // 停止服务
        }
    }

    /**
     * 创建通知渠道，Android 8.0 (API 26) 及以上版本需要。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Ktor Server Channel",
                NotificationManager.IMPORTANCE_DEFAULT // 默认重要性，允许用户在设置中修改
            ).apply {
                description = "通知 Ktor 服务器的运行状态"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * 更新或创建通知。
     * @param title 通知标题
     * @param text 通知内容
     */
    private fun updateNotification(title: String, text: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE // 确保 PendingIntent 是不可变的
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // 设置小图标
            .setContentIntent(pendingIntent)
            .setOngoing(ktorServerManager.isRunning()) // 如果服务器在运行，通知将是持续的（不可滑动消除）
            .build()

        // 启动前台服务，并将通知与服务关联
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KtorService onDestroy")
        stopKtorServer() // 确保服务销毁时服务器也被停止
        serviceJob?.cancel() // 确保所有协程任务被取消
    }
}
