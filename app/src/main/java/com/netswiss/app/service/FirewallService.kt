package com.netswiss.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.netswiss.app.MainActivity
import com.netswiss.app.NetSwissApp
import com.netswiss.app.util.FirewallStore
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FirewallService : VpnService() {

    companion object {
        private const val TAG = "FirewallService"
        private const val NOTIFICATION_ID = 1201
        const val ACTION_STOP = "com.netswiss.app.STOP_FIREWALL"

        @Volatile
        var isRunning = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnInterface: Closeable? = null
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FirewallStore.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopFirewall()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(FirewallStore.blockedPackages.value.size)
            )
            if (collectJob == null) {
                collectJob = scope.launch {
                    FirewallStore.blockedPackages.collectLatest { blocked ->
                        updateFirewallRules(blocked.toList())
                        updateNotification(blocked.size)
                    }
                }
            }
            isRunning = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start firewall", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    fun updateFirewallRules(blockedPackages: List<String>) {
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }

        val builder = Builder()
            .setSession("NetSwiss Firewall")
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")

        val effectiveBlocked = blockedPackages
            .asSequence()
            .filter { it.isNotBlank() && it != packageName }
            .distinct()
            .toList()

        effectiveBlocked.forEach { pkg ->
            try {
                builder.addAllowedApplication(pkg)
            } catch (_: Exception) {
                // ignore bad packages
            }
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            Log.w(TAG, "VPN establish returned null")
        }
    }

    private fun updateNotification(blockedCount: Int) {
        val notification = buildNotification(blockedCount)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(blockedCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FirewallService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NetSwissApp.CHANNEL_FIREWALL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Firewall active")
            .setContentText("Blocked apps: $blockedCount")
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop Firewall", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        collectJob?.cancel()
        collectJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
    }

    override fun onRevoke() {
        stopFirewall()
    }

    private fun stopFirewall() {
        isRunning = false
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
