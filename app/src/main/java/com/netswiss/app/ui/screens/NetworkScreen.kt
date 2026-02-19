package com.netswiss.app.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.components.PrimaryButton
import com.netswiss.app.ui.components.StatItem
import com.netswiss.app.ui.theme.NetworkBlue
import com.netswiss.app.ui.theme.SignalExcellent
import com.netswiss.app.ui.theme.SignalFair
import com.netswiss.app.ui.theme.SignalGood
import com.netswiss.app.ui.theme.SignalNone
import com.netswiss.app.ui.theme.SignalPoor
import com.netswiss.app.ui.theme.Spacing
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
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Signal info & radio settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.md)
        )

        // Signal Card
        SignalCard(networkState = networkState)

        Spacer(modifier = Modifier.height(Spacing.md))

        // Radio Info Launcher
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Radio Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Launch hidden RadioInfo activity",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryButton(
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
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // Network details
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Network Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
}

@Composable
private fun SignalCard(networkState: NetworkState) {
    val signalColor = getSignalColor(networkState.signalStrengthDbm)

    AppCard(
        modifier = Modifier.fillMaxWidth(),
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
            androidx.compose.foundation.layout.Box(
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
