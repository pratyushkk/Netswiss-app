package com.netswiss.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.netswiss.app.MainActivity
import com.netswiss.app.NetSwissApp

class SpeedMonitorService : Service() {

    companion object {
        private const val TAG = "SpeedMonitor"
        const val ACTION_STOP = "com.netswiss.app.STOP_SPEED"
        private const val NOTIFICATION_ID = 1002
        private const val UPDATE_INTERVAL_MS = 1000L

        @Volatile var isRunning = false
            private set
        @Volatile var currentDownloadMbps = 0.0
            private set
        @Volatile var currentUploadMbps = 0.0
            private set
        @Volatile var currentSpeedMbps = 0.0
            private set
        @Volatile var peakSpeedMbps = 0.0
            private set
        @Volatile var peakUploadMbps = 0.0
            private set
        @Volatile var totalBytes = 0L
            private set
        @Volatile var totalTxBytes = 0L
            private set
        @Volatile var sessionStartTime = 0L
            private set
    }

    private var handler: Handler? = null
    private var previousRxBytes = 0L
    private var previousTxBytes = 0L
    private var previousTime = 0L

    private val updateRunnable = object : Runnable {
        override fun run() {
            try {
                updateSpeed()
            } catch (e: Exception) {
                Log.e(TAG, "Speed update error", e)
            }
            handler?.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            previousRxBytes = TrafficStats.getTotalRxBytes()
            previousTxBytes = TrafficStats.getTotalTxBytes()
            previousTime = System.currentTimeMillis()
            sessionStartTime = previousTime
            totalBytes = 0L
            totalTxBytes = 0L
            peakSpeedMbps = 0.0
            peakUploadMbps = 0.0
            currentSpeedMbps = 0.0
            currentDownloadMbps = 0.0
            currentUploadMbps = 0.0

            val notification = buildNotification(0.0, 0.0)

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            handler?.post(updateRunnable)
            isRunning = true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun updateSpeed() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()

        val deltaRxBytes = currentRxBytes - previousRxBytes
        val deltaTxBytes = currentTxBytes - previousTxBytes
        val deltaTime = currentTime - previousTime

        if (deltaTime > 0) {
            if (deltaRxBytes >= 0) {
                currentDownloadMbps = (deltaRxBytes * 8.0) / (deltaTime * 1000.0)
                currentSpeedMbps = currentDownloadMbps
                totalBytes += deltaRxBytes
                if (currentDownloadMbps > peakSpeedMbps) {
                    peakSpeedMbps = currentDownloadMbps
                }
            }
            if (deltaTxBytes >= 0) {
                currentUploadMbps = (deltaTxBytes * 8.0) / (deltaTime * 1000.0)
                totalTxBytes += deltaTxBytes
                if (currentUploadMbps > peakUploadMbps) {
                    peakUploadMbps = currentUploadMbps
                }
            }
        }

        previousRxBytes = currentRxBytes
        previousTxBytes = currentTxBytes
        previousTime = currentTime

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(currentDownloadMbps, currentUploadMbps))
        } catch (e: Exception) {
            Log.e(TAG, "Notification update error", e)
        }
    }

    /**
     * Create a bitmap icon for the status bar.
     * Use a single large value for legibility at small icon sizes.
     */
    private fun createSpeedIcon(downMbps: Double, upMbps: Double): IconCompat {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 120f // Very large for status bar legibility
            isFakeBoldText = true
        }
        val strokePaint = Paint(fillPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            color = Color.BLACK
        }

        // Helper to format speed compactly
        fun formatCompact(mbps: Double): String {
            return if (mbps >= 1000) {
                "%.1fG".format(mbps / 1000)
            } else if (mbps >= 100) {
                "%.0f".format(mbps)
            } else if (mbps >= 10) {
                "%.0f".format(mbps)
            } else if (mbps >= 1) {
                "%.1f".format(mbps)
            } else {
                // Kbps but concise
                val kbps = mbps * 1000
                if (kbps >= 1000) "1M" // Should be covered by above, but safe fallback
                else if (kbps >= 100) "%.0f".format(kbps) // 500
                else "%.0f".format(kbps) // 50
            }
        }

        // Single large value for visibility in the status bar
        val text = formatCompact(downMbps)
        val y = size * 0.68f
        canvas.drawText(text, size / 2f, y, strokePaint)
        canvas.drawText(text, size / 2f, y, fillPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun buildNotification(downMbps: Double, upMbps: Double): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SpeedMonitorService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val downText = formatSpeed(downMbps)
        val upText = formatSpeed(upMbps)

        val builder = NotificationCompat.Builder(this, NetSwissApp.CHANNEL_SPEED)
            .setContentTitle("↓ $downText  ↑ $upText")
            .setContentText("Peak ↓ ${formatSpeed(peakSpeedMbps)} ↑ ${formatSpeed(peakUploadMbps)} | Total: ${formatBytes(totalBytes + totalTxBytes)}")
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // Use dynamic bitmap icon showing speed in status bar
        try {
            val icon = createSpeedIcon(downMbps, upMbps)
            builder.setSmallIcon(icon)
        } catch (e: Exception) {
            builder.setSmallIcon(android.R.drawable.ic_menu_info_details)
        }

        return builder.build()
    }

    private fun formatSpeed(mbps: Double): String {
        return if (mbps < 1.0) {
            "%.0f Kbps".format(mbps * 1000)
        } else {
            "%.2f Mbps".format(mbps)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler?.removeCallbacks(updateRunnable)
        handler = null
    }
}
