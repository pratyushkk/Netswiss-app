package com.netswiss.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netswiss.app.service.SpeedMonitorService
import com.netswiss.app.ui.components.FeatureCard
import com.netswiss.app.ui.components.StatItem
import com.netswiss.app.ui.components.glass.LiquidGlassButton
import com.netswiss.app.ui.components.glass.LiquidGlassCard
import com.netswiss.app.ui.theme.Spacing
import com.netswiss.app.ui.theme.SpeedOrange
import kotlinx.coroutines.delay

enum class SpeedUnit(val label: String) {
    AUTO("Auto"),
    KBPS("Kbps"),
    MBPS("Mbps"),
    GBPS("Gbps")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.xxs)
        )
        Text(
            text = "Real-time network speed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        // Unit Selector
        LiquidGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.md),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(Spacing.sm)
        ) {
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Text(
                    text = "Display Unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xs, start = Spacing.xxs)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    SpeedUnit.values().forEach { unit ->
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            label = {
                                Text(
                                    unit.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedUnit == unit) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpeedOrange,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // Download & Upload Speed Card
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Download
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "↓ Download",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = getSpeedValue(downloadSpeed, selectedUnit),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 42.sp),
                        fontWeight = FontWeight.Bold,
                        color = SpeedOrange
                    )
                    Text(
                        text = getSpeedUnitLabel(downloadSpeed, selectedUnit),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(Spacing.xxxl * 2)
                )

                // Upload
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "↑ Upload",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = getSpeedValue(uploadSpeed, selectedUnit),
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 42.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = getSpeedUnitLabel(uploadSpeed, selectedUnit),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Stats
        if (isMonitoring) {
            FeatureCard(
                title = "Session Stats",
                icon = Icons.Default.Info,
                accentColor = SpeedOrange,
                isActive = true
            ) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Row 1: Peak Speeds
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

                    // Divider using Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                    )

                    // Row 2: Data Usage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "Data Downloaded",
                            value = formatBytes(totalData)
                        )
                        StatItem(
                            label = "Data Uploaded",
                            value = formatBytes(totalTx)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

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

        FeatureCard(
            title = "Latency Monitor",
            icon = Icons.Default.NetworkCheck,
            accentColor = if (isStable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            isActive = isPingEnabled
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Real-time Ping (8.8.8.8)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = isPingEnabled,
                        onCheckedChange = { isPingEnabled = it }
                    )
                }

                if (isPingEnabled) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
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
                            valueColor = if (isStable) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Toggle Button
        LiquidGlassButton(
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
            },
            modifier = Modifier
                .fillMaxWidth()
        )
    }
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

/** Convert Mbps to the selected unit and return the numeric value string */
private fun getSpeedValue(mbps: Double, unit: SpeedUnit): String {
    return when (unit) {
        SpeedUnit.AUTO -> {
            if (mbps < 1.0) "%.0f".format(mbps * 1000)
            else if (mbps >= 1000) "%.2f".format(mbps / 1000)
            else "%.2f".format(mbps)
        }
        SpeedUnit.KBPS -> "%.0f".format(mbps * 1000)
        SpeedUnit.MBPS -> "%.2f".format(mbps)
        SpeedUnit.GBPS -> "%.4f".format(mbps / 1000)
    }
}

/** Get the unit label for the selected unit */
private fun getSpeedUnitLabel(mbps: Double, unit: SpeedUnit): String {
    return when (unit) {
        SpeedUnit.AUTO -> {
            if (mbps < 1.0) "Kbps"
            else if (mbps >= 1000) "Gbps"
            else "Mbps"
        }
        SpeedUnit.KBPS -> "Kbps"
        SpeedUnit.MBPS -> "Mbps"
        SpeedUnit.GBPS -> "Gbps"
    }
}

/** Format speed with its unit label combined */
private fun formatSpeedWithUnit(mbps: Double, unit: SpeedUnit): String {
    return "${getSpeedValue(mbps, unit)} ${getSpeedUnitLabel(mbps, unit)}"
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
