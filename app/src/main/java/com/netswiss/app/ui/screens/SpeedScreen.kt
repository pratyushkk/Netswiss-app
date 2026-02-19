package com.netswiss.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netswiss.app.service.SpeedMonitorService
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.components.PrimaryButton
import com.netswiss.app.ui.components.SegmentedControl
import com.netswiss.app.ui.components.StatItem
import com.netswiss.app.ui.components.StatusBadge
import com.netswiss.app.ui.theme.SpeedOrange
import com.netswiss.app.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class SpeedUnit(val label: String) {
    AUTO("Auto"),
    KBPS("Kbps"),
    MBPS("Mbps"),
    GBPS("Gbps")
}

@Composable
fun SpeedScreen(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isMonitoring by remember { mutableStateOf(SpeedMonitorService.isRunning) }
    var downloadSpeed by remember { mutableDoubleStateOf(SpeedMonitorService.currentDownloadMbps) }
    var uploadSpeed by remember { mutableDoubleStateOf(SpeedMonitorService.currentUploadMbps) }
    var peakDown by remember { mutableDoubleStateOf(SpeedMonitorService.peakSpeedMbps) }
    var peakUp by remember { mutableDoubleStateOf(SpeedMonitorService.peakUploadMbps) }
    var totalData by remember { mutableLongStateOf(SpeedMonitorService.totalBytes) }
    var totalTx by remember { mutableLongStateOf(SpeedMonitorService.totalTxBytes) }
    var selectedUnit by remember { mutableStateOf(SpeedUnit.AUTO) }

    // Poll service state every second
    LaunchedEffect(isMonitoring) {
        while (isMonitoring) {
            downloadSpeed = SpeedMonitorService.currentDownloadMbps
            uploadSpeed = SpeedMonitorService.currentUploadMbps
            peakDown = SpeedMonitorService.peakSpeedMbps
            peakUp = SpeedMonitorService.peakUploadMbps
            totalData = SpeedMonitorService.totalBytes
            totalTx = SpeedMonitorService.totalTxBytes
            isMonitoring = SpeedMonitorService.isRunning
            delay(1000)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isMonitoring = startSpeedServiceSafe(context)
        } else {
            Toast.makeText(context, "Notification permission required", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            text = "Speed Monitor",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Real-time network speed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.sm)
        )

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = "Display Unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentedControl(
                    options = SpeedUnit.values().map { it.label },
                    selectedIndex = SpeedUnit.values().indexOf(selectedUnit),
                    onSelected = { selectedUnit = SpeedUnit.values()[it] }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeedDisplay(
                    title = "Download",
                    value = downloadSpeed,
                    unit = selectedUnit,
                    accent = SpeedOrange
                )
                SpeedDisplay(
                    title = "Upload",
                    value = uploadSpeed,
                    unit = selectedUnit,
                    accent = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        AnimatedVisibility(visible = isMonitoring) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Session Stats",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        StatusBadge(text = "Live", color = SpeedOrange)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Peak Download",
                            value = formatSpeedWithUnit(peakDown, selectedUnit),
                            valueColor = SpeedOrange
                        )
                        StatItem(
                            label = "Peak Upload",
                            value = formatSpeedWithUnit(peakUp, selectedUnit),
                            valueColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    DividerLine()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(label = "Data Downloaded", value = formatBytes(totalData))
                        StatItem(label = "Data Uploaded", value = formatBytes(totalTx))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // Latency Monitor
        var isPingEnabled by remember { mutableStateOf(false) }
        var latencyMs by remember { mutableStateOf("--") }
        var jitterMs by remember { mutableStateOf("--") }
        var isStable by remember { mutableStateOf(true) }

        LaunchedEffect(isPingEnabled) {
            if (isPingEnabled) {
                com.netswiss.app.util.PingManager.startPingMonitor().collect { result ->
                    if (result.latencyMs >= 0) {
                        latencyMs = "%.0f ms".format(result.latencyMs)
                        jitterMs = "%.1f ms".format(result.jitterMs)
                        isStable = result.isStable
                    } else {
                        latencyMs = "Timeout"
                        jitterMs = "--"
                        isStable = false
                    }
                }
            } else {
                latencyMs = "--"
                jitterMs = "--"
                isStable = true
            }
        }

        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Latency Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isPingEnabled,
                        onCheckedChange = { isPingEnabled = it }
                    )
                }
                Text(
                    text = "Real-time Ping (8.8.8.8)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isPingEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Latency",
                            value = latencyMs,
                            valueColor = if (isStable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        StatItem(
                            label = "Jitter",
                            value = jitterMs,
                            valueColor = if (isStable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                        StatItem(
                            label = "Stability",
                            value = if (isStable) "Good" else "Poor",
                            valueColor = if (isStable) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        PrimaryButton(
            text = if (isMonitoring) "Stop Monitoring" else "Start Monitoring",
            onClick = {
                if (isMonitoring) {
                    try {
                        context.stopService(Intent(context, SpeedMonitorService::class.java))
                    } catch (_: Exception) {}
                    isMonitoring = false
                    downloadSpeed = 0.0
                    uploadSpeed = 0.0
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        isMonitoring = startSpeedServiceSafe(context)
                    }
                }
            }
        )
    }
}

@Composable
private fun SpeedDisplay(
    title: String,
    value: Double,
    unit: SpeedUnit,
    accent: androidx.compose.ui.graphics.Color
) {
    val display = getSpeedDisplayValue(value, unit)
    val animatedInt by animateIntAsState(targetValue = (display * 10).roundToInt(), label = "speedValue")
    val displayText = "%.1f".format(animatedInt / 10f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicText(
            text = displayText,
            style = TextStyle(
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accent
            )
        )
        Text(
            text = getSpeedUnitLabel(value, unit),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DividerLine() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

private fun startSpeedServiceSafe(context: Context): Boolean {
    return try {
        val intent = Intent(context, SpeedMonitorService::class.java)
        ContextCompat.startForegroundService(context, intent)
        true
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to start: ${e.message}", Toast.LENGTH_SHORT).show()
        false
    }
}

private fun getSpeedDisplayValue(mbps: Double, unit: SpeedUnit): Double {
    return when (unit) {
        SpeedUnit.AUTO -> {
            when {
                mbps < 1.0 -> mbps * 1000
                mbps >= 1000 -> mbps / 1000
                else -> mbps
            }
        }
        SpeedUnit.KBPS -> mbps * 1000
        SpeedUnit.MBPS -> mbps
        SpeedUnit.GBPS -> mbps / 1000
    }
}

private fun getSpeedUnitLabel(mbps: Double, unit: SpeedUnit): String {
    return when (unit) {
        SpeedUnit.AUTO -> {
            when {
                mbps < 1.0 -> "Kbps"
                mbps >= 1000 -> "Gbps"
                else -> "Mbps"
            }
        }
        SpeedUnit.KBPS -> "Kbps"
        SpeedUnit.MBPS -> "Mbps"
        SpeedUnit.GBPS -> "Gbps"
    }
}

private fun formatSpeedWithUnit(mbps: Double, unit: SpeedUnit): String {
    val value = getSpeedDisplayValue(mbps, unit)
    return "%.2f %s".format(value, getSpeedUnitLabel(mbps, unit))
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
