package com.wittighouse.crowsnest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", true)
            
            if (autoStart) {
                Log.d("BootReceiver", "开机启动：延迟 10 秒后启动服务")
                // 延迟启动，等待系统完全启动后再启动前台服务
                // Android 12+ 限制从 BroadcastReceiver 直接启动前台服务
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val serviceIntent = Intent(context, StatusService::class.java)
                        ContextCompat.startForegroundService(context, serviceIntent)
                        Log.d("BootReceiver", "服务启动成功")
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "服务启动失败", e)
                    }
                }, 10000) // 10 秒延迟
            }
        }
    }
}
