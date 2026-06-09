package com.wittighouse.crowsnest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class StatusService : Service() {

    companion object {
        const val CHANNEL_ID = "phone_status_channel"
        const val NOTIFICATION_ID = 1
        var isRunning = false
        var currentMusic: String? = null
        var currentMusicArtist: String? = null
        var lastUploadResult: String = "未上报"
        var statusMessage: String = "" // 状态签名
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var intervalMs = 60000L
    private var wakeLock: PowerManager.WakeLock? = null
    private var uploadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        StatusCollector.initStepCounter(this)
        
        // 获取 WakeLock 防止 CPU 休眠
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PhoneStatus::UploadWakeLock"
        )
        wakeLock?.acquire()
        Log.d("StatusService", "WakeLock 已获取")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        // 最小间隔 5 秒，防止过于频繁的上报
        val interval = maxOf(prefs.getInt("interval", 60), 5)
        intervalMs = (interval * 1000).toLong()
        Log.d("StatusService", "上报间隔设置为: ${intervalMs}ms (${interval}秒)")
        statusMessage = prefs.getString("status_message", "") ?: ""
        startForeground(NOTIFICATION_ID, createNotification())
        startUploadLoop()
        
        return START_STICKY
    }

    private fun startUploadLoop() {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            Log.d("StatusService", "启动前台循环上报")
            while (isActive) {
                uploadStatus()
                delay(intervalMs)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        uploadJob?.cancel()
        
        // 释放 WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("StatusService", "WakeLock 已释放")
            }
        }
        
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "状态同步服务", NotificationManager.IMPORTANCE_LOW)
        channel.description = "保持后台运行以同步手机状态"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("状态同步运行中")
            .setContentText("正在同步手机状态到网页")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun uploadStatus() {
        try {
            val prefs = getSharedPreferences("config", MODE_PRIVATE)
            val apiUrl = prefs.getString("api_url", "") ?: return
            val apiKey = prefs.getString("api_key", "") ?: return
            if (apiUrl.isEmpty() || apiKey.isEmpty()) return

            val packageName = StatusCollector.getCurrentApp(this)
            val topApps = StatusCollector.getTodayTopApps(this)
            
            // 获取位置信息（手机端完成地理编码，直接返回城市名）
            var locationCity: String? = null
            var locationSource: String? = null
            try {
                val locationResult = StatusCollector.getLocation(this)
                if (locationResult != null) {
                    locationCity = locationResult.city
                    locationSource = locationResult.source
                }
            } catch (e: Exception) {
                Log.e("StatusService", "获取位置失败", e)
            }

            val status = mutableMapOf<String, Any?>(
                "app" to packageName,
                "appName" to StatusCollector.getAppDisplayName(packageName),
                "battery" to StatusCollector.getBatteryLevel(this),
                "charging" to StatusCollector.isCharging(this),
                "batteryTemp" to StatusCollector.getBatteryTemperature(this),
                "wifi" to StatusCollector.isWifiConnected(this),
                "wifiName" to StatusCollector.getWifiName(this),
                "bluetooth" to StatusCollector.isBluetoothEnabled(this),
                "screen" to StatusCollector.isScreenOn(this),
                "networkType" to StatusCollector.getNetworkType(this),
                "device" to StatusCollector.getDeviceName(),
                "music" to currentMusic,
                "musicArtist" to currentMusicArtist,
                "volume" to StatusCollector.getVolume(this),
                "brightness" to StatusCollector.getBrightness(this),
                "storage" to StatusCollector.getStorageInfo(),
                "memory" to StatusCollector.getMemoryInfo(this),
                // 新增趣味数据
                "steps" to StatusCollector.todaySteps,
                "screenTime" to StatusCollector.getTodayScreenTime(this),
                "gameTime" to StatusCollector.getTodayGameTime(this),
                "mood" to StatusCollector.getCurrentMood(packageName),
                "isLateNight" to StatusCollector.isLateNight(),
                "statusMessage" to statusMessage,
                "topApps" to topApps.map { mapOf("name" to it.first, "minutes" to it.second) }
            )

            // 位置信息：手机端已完成解析
            if (locationCity != null) {
                status["location"] = locationCity
                Log.d("StatusService", "位置: $locationCity (来源: $locationSource)")
            }

            // 调试信息
            Log.d("StatusService", "========== 状态上报 ==========")
            Log.d("StatusService", "当前App包名: $packageName")
            Log.d("StatusService", "当前App名称: ${StatusCollector.getAppDisplayName(packageName)}")
            Log.d("StatusService", "正在播放: $currentMusic")
            Log.d("StatusService", "播放艺术家: $currentMusicArtist")
            Log.d("StatusService", "电量: ${status["battery"]}% 充电: ${status["charging"]}")
            Log.d("StatusService", "================================")

            val json = gson.toJson(status)
            Log.d("StatusService", "Uploading: $json")

            val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            lastUploadResult = if (response.code == 200) {
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                "✓ 上报成功 $time"
            } else {
                "✗ 失败(${response.code})"
            }
            response.close()
        } catch (e: Exception) {
            lastUploadResult = "✗ ${e.message?.take(30) ?: "未知错误"}"
            Log.e("StatusService", "Upload failed", e)
        }
    }
}
