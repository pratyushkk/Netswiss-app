package com.netswiss.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.netswiss.app.service.FirewallService
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.components.PrimaryButton
import com.netswiss.app.ui.components.SectionHeader
import com.netswiss.app.ui.theme.Spacing
import com.netswiss.app.util.FirewallStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

data class FirewallApp(
    val name: String,
    val packageName: String
)

@Composable
fun FirewallScreen(
    paddingValues: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var apps by remember { mutableStateOf<List<FirewallApp>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var blocked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRunning by remember { mutableStateOf(FirewallService.isRunning) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastActivatedAt by remember { mutableStateOf<Long?>(null) }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val intent = Intent(context, FirewallService::class.java)
            ContextCompat.startForegroundService(context, intent)
            isRunning = true
            statusMessage = "Firewall started"
            lastActivatedAt = System.currentTimeMillis()
        } else {
            statusMessage = "VPN permission denied"
        }
    }

    LaunchedEffect(Unit) {
        FirewallStore.init(context)
    }

    LaunchedEffect(Unit) {
        FirewallStore.blockedPackages.collectLatest { blockedPackages ->
            blocked = blockedPackages
        }
    }

    LaunchedEffect(Unit) {
        isLoadingApps = true
        apps = withContext(Dispatchers.IO) {
            loadUserApps(context, pm)
        }
        isLoadingApps = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isRunning = FirewallService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            contentPadding = PaddingValues(bottom = Spacing.xxxl * 2),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                SectionHeader(title = "Firewall")
            }
            item {
                Text(
                    text = "Per-app internet control",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                PrimaryButton(
                    text = if (isRunning) "Stop Firewall" else "Start Firewall",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isRunning) {
                            val stopIntent = Intent(context, FirewallService::class.java).apply {
                                action = FirewallService.ACTION_STOP
                            }
                            ContextCompat.startForegroundService(context, stopIntent)
                            statusMessage = "Firewall stopped"
                            isRunning = false
                        } else {
                            val prepareIntent = VpnService.prepare(context)
                            if (prepareIntent != null) {
                                vpnLauncher.launch(prepareIntent)
                            } else {
                                val intent = Intent(context, FirewallService::class.java)
                                ContextCompat.startForegroundService(context, intent)
                                statusMessage = "Firewall started"
                                isRunning = true
                                lastActivatedAt = System.currentTimeMillis()
                            }
                        }
                    }
                )
            }

            if (!statusMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = statusMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = if (isRunning) "Protection Active" else "Protection Inactive",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isRunning && lastActivatedAt != null) {
                                val startedAt = lastActivatedAt ?: 0L
                                "Started at ${DateFormat.format("hh:mm:ss a", startedAt)}"
                            } else {
                                "Start firewall to apply app rules."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader(title = "Installed Apps")
            }

            if (isLoadingApps) {
                item {
                    AppCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Loading apps...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else if (apps.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            text = "No third-party apps found.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(
                    items = apps,
                    key = { it.packageName }
                ) { app ->
                    FirewallAppRow(
                        app = app,
                        isBlocked = blocked.contains(app.packageName),
                        isRunning = isRunning,
                        onBlockedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            FirewallStore.setBlocked(context, app.packageName, checked)
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.md)
        )
    }
}

@Composable
private fun FirewallAppRow(
    app: FirewallApp,
    isBlocked: Boolean,
    isRunning: Boolean,
    onBlockedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(width = 64, height = 64)
                .asImageBitmap()
        }.getOrNull()
    }

    AppCard(contentPadding = PaddingValues(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                )
            }
            Spacer(modifier = Modifier.size(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isBlocked,
                onCheckedChange = onBlockedChange,
                enabled = isRunning
            )
        }
    }
}

private fun loadUserApps(context: Context, pm: PackageManager): List<FirewallApp> {
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .asSequence()
        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        .filter { it.packageName != context.packageName }
        .map {
            FirewallApp(
                name = pm.getApplicationLabel(it).toString(),
                packageName = it.packageName
            )
        }
        .sortedBy { it.name.lowercase() }
        .toList()
}
