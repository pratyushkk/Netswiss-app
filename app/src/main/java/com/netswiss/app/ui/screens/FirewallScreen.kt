package com.netswiss.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import com.netswiss.app.service.FirewallService
import com.netswiss.app.ui.components.glass.LiquidGlassButton
import com.netswiss.app.ui.components.glass.LiquidGlassCard
import com.netswiss.app.ui.theme.Spacing
import com.netswiss.app.util.FirewallStore
import kotlinx.coroutines.flow.collectLatest
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

data class FirewallApp(
    val name: String,
    val packageName: String,
    val icon: android.graphics.Bitmap
)

@Composable
fun FirewallScreen(
    paddingValues: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val pm = context.packageManager

    var apps by remember { mutableStateOf<List<FirewallApp>>(emptyList()) }
    var blocked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRunning by remember { mutableStateOf(FirewallService.isRunning) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val intent = Intent(context, FirewallService::class.java)
            ContextCompat.startForegroundService(context, intent)
            isRunning = true
            statusMessage = "Firewall started"
        } else {
            statusMessage = "VPN permission denied"
        }
    }

    LaunchedEffect(Unit) {
        FirewallStore.init(context)
        FirewallStore.blockedPackages.collectLatest {
            blocked = it
            isRunning = FirewallService.isRunning
        }
    }

    LaunchedEffect(Unit) {
        apps = loadUserApps(context, pm)
    }

    // No app list permission prompt. QUERY_ALL_PACKAGES is manifest-only on Android.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .padding(paddingValues)
    ) {
        Text(
            text = "Firewall",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.xxs)
        )
        Text(
            text = "Block internet access per app",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )

        LiquidGlassButton(
            text = if (isRunning) "Stop Firewall" else "Start Firewall",
            onClick = {
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
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        if (apps.isEmpty()) {
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.md)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No apps found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "If this device restricts app list access, restart the app after granting access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            items(apps) { app ->
                val isBlocked = blocked.contains(app.packageName)
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.size(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBlocked,
                            onCheckedChange = { checked ->
                                FirewallStore.setBlocked(context, app.packageName, checked)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun loadUserApps(context: Context, pm: PackageManager): List<FirewallApp> {
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
        .filter { it.packageName != context.packageName }
        .map {
            FirewallApp(
                name = pm.getApplicationLabel(it).toString(),
                packageName = it.packageName,
                icon = it.loadIcon(pm).toBitmap()
            )
        }
        .sortedBy { it.name.lowercase() }
    return apps
}
