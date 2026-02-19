package com.netswiss.app.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

data class PingResult(
    val latencyMs: Double,
    val jitterMs: Double,
    val isStable: Boolean
)

object PingManager {
    private const val TAG = "PingManager"
    private const val TARGET_HOST = "8.8.8.8"
    private const val PING_INTERVAL_MS = 1000L
    private const val JITTER_THRESHOLD_MS = 50.0
    private const val HISTORY_SIZE = 10

    fun startPingMonitor(): Flow<PingResult> = flow {
        val latencyHistory = ArrayDeque<Double>(HISTORY_SIZE)
        
        while (true) {
            val latency = executePing()
            if (latency != null) {
                // Update history
                if (latencyHistory.size >= HISTORY_SIZE) {
                    latencyHistory.removeFirst()
                }
                latencyHistory.addLast(latency)

                // Calculate Jitter: Average absolute difference of consecutive latencies
                val jitter = calculateJitter(latencyHistory)
                val isStable = jitter < JITTER_THRESHOLD_MS

                emit(PingResult(latency, jitter, isStable))
            } else {
                // Ping failed or timeout
                emit(PingResult(-1.0, 0.0, false))
            }
            delay(PING_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    private fun executePing(): Double? {
        return try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 $TARGET_HOST")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var timeMs: Double? = null
            
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("time=") == true) {
                    // Extract time value: "time=14.5 ms"
                    val parts = line!!.split("time=")
                    if (parts.size > 1) {
                        val timePart = parts[1].split(" ")[0]
                        timeMs = timePart.toDoubleOrNull()
                    }
                }
            }
            process.waitFor()
            timeMs
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed", e)
            null
        }
    }

    private fun calculateJitter(history: ArrayDeque<Double>): Double {
        if (history.size < 2) return 0.0
        
        var totalDiff = 0.0
        for (i in 1 until history.size) {
            totalDiff += abs(history[i] - history[i - 1])
        }
        return totalDiff / (history.size - 1)
    }
}
