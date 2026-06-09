package com.wittighouse.crowsnest

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

/**
 * Syncs tasks between the phone and Quarterdeck on CPT-Hook.
 *
 * Directions:
 *   Phone → CPT-Hook:  POST /api/report  (phone state)
 *   CPT-Hook → Phone:  GET /api           (returns task list for PWA / lock screen)
 *
 * Offline mode: caches last-known tasks locally, syncs when back online.
 */
object QuarterdeckSync {

    private const val TAG = "QuarterdeckSync"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Reports phone state to CPT-Hook. Returns true on success. */
    suspend fun reportState(apiUrl: String, state: Map<String, Any?>, apiKey: String = ""): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(state)
                Log.d(TAG, "Reporting to $apiUrl")

                val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(apiUrl)
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                response.close()
                success
            } catch (e: Exception) {
                Log.e(TAG, "State report failed: ${e.message}")
                false
            }
        }
    }

    /** Fetches current task list from Quarterdeck. Returns raw JSON or null. */
    suspend fun fetchTasks(apiUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$apiUrl?t=${System.currentTimeMillis()}")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()
                response.close()
                body
            } catch (e: Exception) {
                Log.e(TAG, "Task fetch failed: ${e.message}")
                null
            }
        }
    }

    /** Marks a task as done on CPT-Hook. This is a convenience method — the phone doesn't
     *  directly modify quarterdeck, it reports completions via the status report. */
    suspend fun markTaskDone(apiUrl: String, taskId: Int, apiKey: String = ""): Boolean {
        return reportState(apiUrl, mapOf(
            "action" to "task_done",
            "task_id" to taskId
        ), apiKey)
    }
}
