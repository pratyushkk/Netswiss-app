package com.netswiss.app.util

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File

sealed class InstallStatus {
    data object Queued : InstallStatus()
    data class Installing(val progress: Float) : InstallStatus()
    data object PendingUserAction : InstallStatus()
    data object Success : InstallStatus()
    data class Failure(val message: String) : InstallStatus()
}

object ApkInstaller {

    private const val TAG = "ApkInstaller"
    private const val ACTION_INSTALL_RESULT = "com.netswiss.app.INSTALL_RESULT"

    fun canInstallPackages(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun getInstallPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
    }

    /**
     * Install APK files using PackageInstaller session API.
     *
     * CRITICAL: This registers a BroadcastReceiver to handle STATUS_PENDING_USER_ACTION,
     * which contains an Intent that must be launched as an Activity for the user to
     * confirm the installation. Without this, the install silently fails.
     */
    fun installApks(context: Context, apkFiles: List<File>, callback: (InstallStatus) -> Unit) {
        try {
            callback(InstallStatus.Installing(0f))

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val totalSize = apkFiles.sumOf { it.length() }
            params.setSize(totalSize)

            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            try {
                // Write all APK files into the session
                var written = 0L
                apkFiles.forEachIndexed { index, file ->
                    session.openWrite("split_$index.apk", 0, file.length()).use { out ->
                        file.inputStream().use { input ->
                            val buffer = ByteArray(65536)
                            var len: Int
                            while (input.read(buffer).also { len = it } != -1) {
                                out.write(buffer, 0, len)
                                written += len
                                callback(InstallStatus.Installing(written.toFloat() / totalSize))
                            }
                        }
                        session.fsync(out)
                    }
                }

                // Register a BroadcastReceiver to handle the install result.
                // This is ESSENTIAL — STATUS_PENDING_USER_ACTION contains an Intent
                // that must be launched for the user to see the install confirmation.
                val uniqueAction = "$ACTION_INSTALL_RESULT.$sessionId"
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val status = intent.getIntExtra(
                            PackageInstaller.EXTRA_STATUS,
                            PackageInstaller.STATUS_FAILURE
                        )
                        Log.d(TAG, "Install status: $status for session $sessionId")

                        when (status) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                // Extract the confirmation intent and launch it
                                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                                }
                                if (confirmIntent != null) {
                                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        ctx.startActivity(confirmIntent)
                                        callback(InstallStatus.PendingUserAction)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to launch confirm intent", e)
                                        callback(InstallStatus.Failure("Could not show install dialog: ${e.message}"))
                                        safeUnregister(ctx, this)
                                    }
                                } else {
                                    Log.e(TAG, "No confirm intent in STATUS_PENDING_USER_ACTION")
                                    callback(InstallStatus.Failure("System did not provide install confirmation"))
                                    safeUnregister(ctx, this)
                                }
                            }
                            PackageInstaller.STATUS_SUCCESS -> {
                                callback(InstallStatus.Success)
                                safeUnregister(ctx, this)
                            }
                            else -> {
                                val msg = intent.getStringExtra(
                                    PackageInstaller.EXTRA_STATUS_MESSAGE
                                ) ?: "Installation failed (code $status)"
                                Log.e(TAG, "Install failed: $msg")
                                callback(InstallStatus.Failure(msg))
                                safeUnregister(ctx, this)
                            }
                        }
                    }
                }

                val intentFilter = IntentFilter(uniqueAction)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(receiver, intentFilter)
                }

                // Create the PendingIntent for the session commit
                val resultIntent = Intent(uniqueAction).apply {
                    setPackage(context.packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                // Commit the session — this triggers the broadcast
                session.commit(pendingIntent.intentSender)
                Log.d(TAG, "Session $sessionId committed")

            } catch (e: Exception) {
                Log.e(TAG, "Session error", e)
                try { session.abandon() } catch (_: Exception) {}
                callback(InstallStatus.Failure("Session error: ${e.message}"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            callback(InstallStatus.Failure("Failed to create install session: ${e.message}"))
        }
    }

    private fun safeUnregister(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}
