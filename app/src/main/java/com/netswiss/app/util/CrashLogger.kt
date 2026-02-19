package com.netswiss.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val FILE_NAME = "crash_log.txt"
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Log to both Logcat and File
    fun log(context: Context, tag: String, message: String) {
        // 1. Standard Logcat
        Log.d(tag, message)

        // 2. Write to File (Append mode)
        try {
            val file = File(context.filesDir, FILE_NAME)
            val timestamp = DATE_FORMAT.format(Date())
            val entry = "$timestamp [$tag] $message\n"
            
            FileWriter(file, true).use { writer ->
                writer.append(entry)
            }
        } catch (e: Exception) {
            Log.e("CrashLogger", "Failed to write log", e)
        }
    }

    // Log Exceptions
    fun logException(context: Context, tag: String, message: String, throwable: Throwable) {
        val stackTrace = Log.getStackTraceString(throwable)
        log(context, tag, "$message\n$stackTrace")
        Log.e(tag, message, throwable)
    }

    // Read logs for display
    fun getLogs(context: Context): String {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.readText()
            } else {
                "No logs found."
            }
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    // Clear logs
    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.writeText("") // overwrite with empty
            }
        } catch (e: Exception) {
            Log.e("CrashLogger", "Failed to clear logs", e)
        }
    }
}
