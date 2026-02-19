package com.netswiss.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.netswiss.app.service.MockLocationService
import com.netswiss.app.service.SpeedMonitorService
import com.netswiss.app.ui.components.AppCard
import com.netswiss.app.ui.components.SecondaryButton
import com.netswiss.app.ui.theme.GpsGreen
import com.netswiss.app.ui.theme.NetworkBlue
import com.netswiss.app.ui.theme.SpeedOrange
import com.netswiss.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    onNavigateToGps: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToSpeed: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToFirewall: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "NetSwiss",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Network Utility Toolkit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SecondaryButton(
                text = if (isDarkTheme) "Light" else "Dark",
                onClick = onToggleTheme
            )
        }

        Spacer(modifier = Modifier.padding(top = Spacing.sm))

        val tiles = listOf(
            HomeTile(
                title = "Mock GPS",
                subtitle = if (MockLocationService.isRunning) {
                    "Active • %.4f, %.4f".format(
                        MockLocationService.currentLat,
                        MockLocationService.currentLng
                    )
                } else "Spoof device location",
                icon = Icons.Default.LocationOn,
                accent = GpsGreen,
                onClick = onNavigateToGps
            ),
            HomeTile(
                title = "Network",
                subtitle = "Signal & radio info",
                icon = Icons.Default.NetworkCell,
                accent = NetworkBlue,
                onClick = onNavigateToNetwork
            ),
            HomeTile(
                title = "Speed",
                subtitle = if (SpeedMonitorService.isRunning) {
                    "%.1f Mbps".format(SpeedMonitorService.currentSpeedMbps)
                } else "Live monitoring",
                icon = Icons.Default.Speed,
                accent = SpeedOrange,
                onClick = onNavigateToSpeed
            ),
            HomeTile(
                title = "Diagnostics",
                subtitle = "Ping • DNS • Trace",
                icon = Icons.Default.NetworkCheck,
                accent = MaterialTheme.colorScheme.primary,
                onClick = onNavigateToDiagnostics
            ),
            HomeTile(
                title = "Firewall",
                subtitle = "Ad block & app rules",
                icon = Icons.Default.Shield,
                accent = MaterialTheme.colorScheme.secondary,
                onClick = onNavigateToFirewall
            )
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = PaddingValues(bottom = Spacing.xxxl)
        ) {
            items(tiles) { tile ->
                AppCard(onClick = tile.onClick) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = tile.icon,
                                contentDescription = null,
                                tint = tile.accent
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = tile.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = tile.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private data class HomeTile(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accent: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit
)
