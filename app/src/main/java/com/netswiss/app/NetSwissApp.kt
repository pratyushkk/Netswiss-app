package com.netswiss.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class NetSwissApp : Application() {

    companion object {
        const val CHANNEL_SPEED = "speed_live"
        const val CHANNEL_MOCK = "mock_location"
        const val CHANNEL_FIREWALL = "firewall"
    }

    override fun onCreate() {
        super.onCreate()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log to file first (synchronous write)
            try {
                com.netswiss.app.util.CrashLogger.logException(
                    applicationContext, 
                    "CRASH_TRAP", 
                    "FATAL EXCEPTION: ${thread.name}", 
                    throwable
                )
            } catch (e: Exception) {
                // Last ditch effort to log to logcat
                android.util.Log.e("CRASH_TRAP", "Failed to write crash log", e)
            }
            
            android.util.Log.e("CRASH_REPORT", "FATAL EXCEPTION: ${thread.name}", throwable)
            // Trigger default handler to actually crash the app (so system knows)
            System.exit(1) 
        }

        // Global OSMDroid Configuration
        org.osmdroid.config.Configuration.getInstance().load(
            this, getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )
        // Set user agent to avoid bans
        org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Clean up any old channels
        manager.deleteNotificationChannel("speed_monitor")
        manager.deleteNotificationChannel("speed_monitor_v2")

        // IMPORTANCE_HIGH = always shows icon in status bar on all devices including Samsung
        val speedChannel = NotificationChannel(
            CHANNEL_SPEED,
            "Live Speed",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows real-time network speed in status bar"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        val mockChannel = NotificationChannel(
            CHANNEL_MOCK,
            "Mock Location",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mock GPS location active"
            setShowBadge(false)
        }

        val firewallChannel = NotificationChannel(
            CHANNEL_FIREWALL,
            "Firewall",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Firewall active"
            setShowBadge(false)
        }

        manager.createNotificationChannel(speedChannel)
        manager.createNotificationChannel(mockChannel)
        manager.createNotificationChannel(firewallChannel)
    }
}
