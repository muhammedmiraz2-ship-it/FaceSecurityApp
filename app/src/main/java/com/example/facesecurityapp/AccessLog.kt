package com.example.facesecurityapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AccessLog {

    private const val PREFS_NAME = "face_security_logs"
    private const val LOGS_KEY = "access_history"
    private const val MAX_LOGS = 100

    fun addLog(context: Context, status: String) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val logs = getLogsArray(
            prefs.getString(LOGS_KEY, null)
        )

        val date = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        val entry = JSONObject().apply {
            put("time", date)
            put("status", status)
        }

        logs.put(entry)

        // Keep only the latest 100 entries.
        val trimmedLogs = JSONArray()
        val start = (logs.length() - MAX_LOGS).coerceAtLeast(0)

        for (i in start until logs.length()) {
            trimmedLogs.put(logs.getJSONObject(i))
        }

        prefs.edit()
            .putString(LOGS_KEY, trimmedLogs.toString())
            .apply()
    }

    fun getLogs(context: Context): List<String> {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val logs = getLogsArray(
            prefs.getString(LOGS_KEY, null)
        )

        return (0 until logs.length()).map { i ->
            val entry = logs.getJSONObject(i)
            "${entry.optString("time")} - ${entry.optString("status")}"
        }.reversed()
    }

    fun clearLogs(context: Context) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .remove(LOGS_KEY)
            .apply()
    }

    private fun getLogsArray(saved: String?): JSONArray {
        return try {
            if (saved.isNullOrBlank()) JSONArray()
            else JSONArray(saved)
        } catch (e: Exception) {
            JSONArray()
        }
    }

}