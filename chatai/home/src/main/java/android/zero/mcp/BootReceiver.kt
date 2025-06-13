package com.example.ktormcpapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 接收设备启动完成的广播，用于自动启动 KtorService。
 */
class BootReceiver : BroadcastReceiver() {
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "设备已启动，尝试启动 KtorService。")
            // 启动 KtorService
            val serviceIntent = Intent(context, KtorService::class.java).apply {
                action = "START_SERVER"
                // 可以从 SharedPreferences 加载上次使用的端口
                putExtra("PORT", 8080) // 默认使用 8080 端口，或者从配置中读取
            }

            // 对于 Android O (API 26) 及以上，使用 startForegroundService 启动前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "KtorService 启动命令已发送。")
        }
    }
}
