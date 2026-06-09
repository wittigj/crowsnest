package com.wittighouse.crowsnest

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Quarterdeck HUD — the lock-screen / home-screen task display.
 *
 * Fetches John's task list from quarterdeck.wittighouse.com/api
 * and provides it to the HUD fragment.
 */

// --- Data Classes ---
data class QuarterdeckTask(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("task") val task: String = "",
    @SerializedName("done") val done: Boolean = false,
    @SerializedName("due") val due: String? = null,
    @SerializedName("priority") val priority: String = "normal",
    @SerializedName("notes") val notes: String? = null
)

data class QuarterdeckState(
    @SerializedName("active") val active: List<QuarterdeckTask> = emptyList(),
    @SerializedName("completed_today") val completedToday: Int = 0,
    @SerializedName("total_active") val totalActive: Int = 0,
    @SerializedName("next_action") val nextAction: String? = null,
    @SerializedName("timestamp") val timestamp: Long = 0L
)

/**
 * Callback interface for HUD updates.
 */
interface QuarterdeckListener {
    fun onStateChanged(state: QuarterdeckState)
    fun onError(error: String)
    fun onTasksSynced(message: String)
}

object QuarterdeckHUD {
    private const val TAG = "QuarterdeckHUD"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // Default API endpoint — configurable via SharedPreferences
    fun getApiUrl(): String {
        val prefs = App.instance.getSharedPreferences("config", android.content.Context.MODE_PRIVATE)
        return prefs.getString("quarterdeck_url", "https://quarterdeck.wittighouse.com/api") ?: "https://quarterdeck.wittighouse.com/api"
    }

    fun getApiKey(): String {
        val prefs = App.instance.getSharedPreferences("config", android.content.Context.MODE_PRIVATE)
        return prefs.getString("quarterdeck_key", "") ?: ""
    }

    /** Fetch current Quarterdeck state */
    suspend fun fetchState(): Result<QuarterdeckState> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${getApiUrl()}?t=${System.currentTimeMillis()}"
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()
                response.close()

                if (body != null && response.isSuccessful) {
                    val state: QuarterdeckState = gson.fromJson(body, QuarterdeckState::class.java)
                    Result.success(state)
                } else {
                    Result.failure(IOException("HTTP ${response.code}: ${body ?: "empty"}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /** Mark a task complete */
    suspend fun markDone(taskId: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf(
                    "action" to "task_done",
                    "task_id" to taskId
                ))
                val body = payload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(getApiUrl())
                    .apply {
                        val key = getApiKey()
                        if (key.isNotBlank()) addHeader("Authorization", "Bearer $key")
                    }
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val respBody = response.body?.string()
                response.close()
                if (response.isSuccessful) Result.success(respBody ?: "ok")
                else Result.failure(IOException("Failed: ${response.code}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** Toggle task done status via QuarterdeckSync */
    suspend fun toggleTask(taskId: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val payload = gson.toJson(mapOf(
                    "action" to "toggle_task",
                    "task_id" to taskId
                ))
                val body = payload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(getApiUrl())
                    .apply {
                        val key = getApiKey()
                        if (key.isNotBlank()) addHeader("Authorization", "Bearer $key")
                    }
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val respBody = response.body?.string()
                response.close()
                if (response.isSuccessful) Result.success(respBody ?: "ok")
                else Result.failure(IOException("Failed: ${response.code}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

/** App singleton for context */
class App : android.app.Application() {
    companion object {
        lateinit var instance: App
            private set
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
