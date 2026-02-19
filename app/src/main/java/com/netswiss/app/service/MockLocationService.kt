package com.netswiss.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.netswiss.app.MainActivity
import com.netswiss.app.NetSwissApp
import com.netswiss.app.R
import kotlinx.coroutines.*

class MockLocationService : Service() {

    companion object {
        const val TAG = "MockLocationService"
        const val EXTRA_LATITUDE = "extra_lat"
        const val EXTRA_LONGITUDE = "extra_lng"
        const val ACTION_STOP = "com.netswiss.app.STOP_MOCK"
        const val ACTION_UPDATE = "com.netswiss.app.UPDATE_MOCK"
        private const val PROVIDER = LocationManager.GPS_PROVIDER
        private const val NOTIFICATION_ID = 1001

        var isRunning = false
            private set

        var currentLat = 0.0
            private set
        var currentLng = 0.0
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mockJob: Job? = null
    private var providerReady = false
    private var lastNotifyAtMs = 0L
    
    private lateinit var locationManager: LocationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val action = intent?.action
            if (action == ACTION_STOP) {
                stopSelf()
                return START_NOT_STICKY
            }

            val lat = intent?.getDoubleExtra(EXTRA_LATITUDE, currentLat) ?: currentLat
            val lng = intent?.getDoubleExtra(EXTRA_LONGITUDE, currentLng) ?: currentLng
            currentLat = lat
            currentLng = lng

            // First start: promote to foreground and start stable loop once.
            if (!isRunning) {
                isRunning = true
                if (!startForegroundService()) {
                    isRunning = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureProviderReady()
                ensureMockLoop()
            }

            // Update requests should never restart foreground or loop; just refresh coordinates.
            pushMockLocation(currentLat, currentLng)
            maybeUpdateNotification()

            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand crash", e)
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
    }
    
    private fun startForegroundService(): Boolean {
        return try {
            val notif = buildNotification("Mock GPS Active")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start foreground failed", e)
            false
        }
    }

    private fun ensureProviderReady() {
        if (providerReady) return
        setupTestProvider()
        providerReady = true
    }

    private fun ensureMockLoop() {
        if (mockJob?.isActive == true) return
        mockJob = scope.launch {
            while (isActive) {
                pushMockLocation(currentLat, currentLng)
                delay(1000L)
            }
        }
        updateNotification("Mock GPS Active")
    }

    private fun maybeUpdateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAtMs >= 1500L) {
            lastNotifyAtMs = now
            updateNotification("Mock GPS Active")
        }
    }
    
    private fun updateNotification(title: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(title))
    }

    private fun setupTestProvider() {
        try {
            locationManager.removeTestProvider(PROVIDER)
        } catch (_: Exception) {}

        try {
            locationManager.addTestProvider(
                PROVIDER,
                false, false, false, false, false,
                true, true, android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(PROVIDER, true)
        } catch (e: SecurityException) {
            // Permission might be lost
            stopSelf()
        } catch (e: IllegalArgumentException) {
            // Provider likely already exists or other error
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun pushMockLocation(lat: Double, lng: Double) {
        val location = Location(PROVIDER).apply {
            latitude = lat
            longitude = lng
            accuracy = 1.0f
            altitude = 0.0
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            speed = 0.0f
            bearing = 0.0f // Could calculate bearing if needed
        }
        try {
            locationManager.setTestProviderLocation(PROVIDER, location)
        } catch (_: Exception) {}
    }

    private fun buildNotification(title: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NetSwissApp.CHANNEL_MOCK)
            .setSmallIcon(R.drawable.ic_gps)
            .setContentTitle(title)
            .setContentText("%.5f, %.5f".format(currentLat, currentLng))
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_gps, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
        providerReady = false
        try {
            locationManager.setTestProviderEnabled(PROVIDER, false)
            locationManager.removeTestProvider(PROVIDER)
        } catch (_: Exception) {}
    }
}
