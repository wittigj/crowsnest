package com.wittighouse.crowsnest

import android.app.Notification
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Listens for Gotify push notifications from CPT-Hook.
 * When a Quarterdeck nudge arrives, it opens the Quarterdeck PWA or
 * shows a rich notification with the current task.
 *
 * This runs alongside StatusService — one reports state, one receives nudges.
 */
class GotifyReceiver : Service() {

    companion object {
        const val CHANNEL_ID = "crowsnest_nudge_channel"
        const val NOTIFICATION_ID = 2
        private const val TAG = "GotifyReceiver"
        var isRunning = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // long poll
        .build()
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val prefs = getSharedPreferences("config", MODE_PRIVATE)
        val gotifyUrl = prefs.getString("gotify_url", "") ?: ""
        val gotifyToken = prefs.getString("gotify_token", "") ?: ""

        if (gotifyUrl.isBlank() || gotifyToken.isBlank()) {
            Log.w(TAG, "Gotify not configured — skipping push listener")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createSilentNotification())
        startPolling(gotifyUrl, gotifyToken)
        return START_STICKY
    }

    private fun startPolling(baseUrl: String, token: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            Log.d(TAG, "Starting Gotify SSE stream: $baseUrl")
            while (isActive) {
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/stream?token=$token")
                        .header("Accept", "text/event-stream")
                        .build()

                    val response = client.newCall(request).execute()
                    val source = response.body?.source() ?: continue

                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (line.startsWith("data: ")) {
                            val message = line.removePrefix("data: ")
                            handlePushMessage(message)
                        }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.w(TAG, "SSE stream interrupted: ${e.message} — reconnecting in 10s")
                }
                delay(10_000)
            }
        }
    }

    private fun handlePushMessage(message: String) {
        try {
            val orgJson = org.json.JSONObject(message)
            val title = orgJson.optString("title", "Quarterdeck")
            val body = orgJson.optString("message", "")
            val priority = orgJson.optInt("priority", 3)

            Log.d(TAG, "Push received: $title — $body")
            showNudge(title, body, priority)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse push message", e)
            showNudge("Quarterdeck", message, 3)
        }
    }

    private fun showNudge(title: String, body: String, priority: Int) {
        val notifPriority = when {
            priority <= 3 -> NotificationManager.IMPORTANCE_HIGH
            priority <= 5 -> NotificationManager.IMPORTANCE_DEFAULT
            else -> NotificationManager.IMPORTANCE_LOW
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "quarterdeck")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(notifPriority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createSilentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CrowsNest Listener")
            .setContentText("Listening for Quarterdeck nudges")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Quarterdeck Nudges",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Working memory compensation reminders"
            enableVibration(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        pollJob?.cancel()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
