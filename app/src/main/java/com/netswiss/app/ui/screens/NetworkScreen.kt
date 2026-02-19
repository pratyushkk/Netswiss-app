package com.netswiss.app.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netswiss.app.ui.components.FeatureCard
import com.netswiss.app.ui.components.StatItem
import com.netswiss.app.ui.components.glass.LiquidGlassButton
import com.netswiss.app.ui.components.glass.LiquidGlassCard
import com.netswiss.app.ui.theme.*
import com.netswiss.app.util.NetworkInfoProvider
import com.netswiss.app.util.NetworkState

@Composable
fun NetworkScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val networkProvider = remember { NetworkInfoProvider(context) }
    val networkState by networkProvider.networkState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            networkProvider.startListening()
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            networkProvider.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            networkProvider.stopListening()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            text = "Network Mode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Spacing.xxs)
        )
        Text(
            text = "Signal info & radio settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.lg)
        )

        // Signal Card
        SignalCard(networkState = networkState)

        Spacer(modifier = Modifier.height(Spacing.md))

        // Radio Info Launcher
        FeatureCard(
            title = "Radio Settings",
            subtitle = "Launch hidden RadioInfo activity",
            icon = Icons.Default.Settings,
            accentColor = NetworkBlue
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            LiquidGlassButton(
                text = "Open Radio Settings",
                onClick = {
                    try {
                        val intent = Intent().apply {
                            component = ComponentName(
                                "com.android.phone",
                                "com.android.phone.settings.RadioInfo"
                            )
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback: try alternate package
                        try {
                            val intent = Intent(Intent.ACTION_MAIN).apply {
                                setClassName("com.android.settings", "com.android.settings.RadioInfo")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "RadioInfo is not available on this device",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Network details
        FeatureCard(
            title = "Network Details",
            icon = Icons.Default.Info,
            accentColor = NetworkBlue
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Type",
                    value = networkState.networkType,
                    valueColor = NetworkBlue
                )
                StatItem(
                    label = "Strength",
                    value = if (networkState.signalStrengthDbm > -900) {
                        "${networkState.signalStrengthDbm} dBm"
                    } else "N/A",
                    valueColor = getSignalColor(networkState.signalStrengthDbm)
                )
                StatItem(
                    label = "Status",
                    value = if (networkState.isConnected) "Connected" else "Disconnected",
                    valueColor = if (networkState.isConnected) SignalExcellent else SignalNone
                )
            }
        }
    }
}

@Composable
private fun SignalCard(networkState: NetworkState) {
    val signalColor = getSignalColor(networkState.signalStrengthDbm)

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(Spacing.xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large network type display
            Text(
                text = networkState.networkType,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = signalColor
            )

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Signal strength bar
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = Spacing.sm)
            ) {
                val bars = getSignalBars(networkState.signalStrengthDbm)
                for (i in 1..5) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = Spacing.xxs)
                            .width(12.dp)
                            .height((8 + i * 8).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (i <= bars) signalColor
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                    )
                }
            }

            // dBm value
            if (networkState.signalStrengthDbm > -900) {
                Text(
                    text = "${networkState.signalStrengthDbm} dBm",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // Signal quality label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(signalColor.copy(alpha = 0.12f))
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            ) {
                Text(
                    text = getSignalQuality(networkState.signalStrengthDbm),
                    style = MaterialTheme.typography.labelLarge,
                    color = signalColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun getSignalColor(dbm: Int): Color {
    return when {
        dbm > -70 -> SignalExcellent
        dbm > -85 -> SignalGood
        dbm > -100 -> SignalFair
        dbm > -110 -> SignalPoor
        else -> SignalNone
    }
}

private fun getSignalBars(dbm: Int): Int {
    return when {
        dbm > -70 -> 5
        dbm > -85 -> 4
        dbm > -95 -> 3
        dbm > -105 -> 2
        dbm > -115 -> 1
        else -> 0
    }
}

private fun getSignalQuality(dbm: Int): String {
    return when {
        dbm > -70 -> "Excellent"
        dbm > -85 -> "Good"
        dbm > -100 -> "Fair"
        dbm > -110 -> "Poor"
        dbm > -900 -> "No Signal"
        else -> "Unknown"
    }
}
