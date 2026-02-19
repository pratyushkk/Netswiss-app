package com.netswiss.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.netswiss.app.ui.components.glass.LiquidGlassButton
import com.netswiss.app.ui.theme.Spacing
import com.netswiss.app.service.MockLocationService
import com.netswiss.app.service.SpeedMonitorService
import com.netswiss.app.ui.components.FeatureCard
import com.netswiss.app.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToGps: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToSpeed: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NetSwiss",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassButton(
                text = if (isDarkTheme) "Light" else "Dark",
                onClick = onToggleTheme
            )
        }
        Text(
            text = "Network Utility Toolkit",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xl)
        )

        // Mock GPS Card
        FeatureCard(
            title = "Mock GPS",
            subtitle = if (MockLocationService.isRunning) {
                "Active — %.4f, %.4f".format(
                    MockLocationService.currentLat,
                    MockLocationService.currentLng
                )
            } else "Spoof device location",
            icon = Icons.Default.LocationOn,
            accentColor = GpsGreen,
            isActive = MockLocationService.isRunning,
            modifier = Modifier.padding(bottom = Spacing.sm)
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            LiquidGlassButton(
                text = "Configure",
                onClick = onNavigateToGps,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Network Card
        FeatureCard(
            title = "Network Mode",
            subtitle = "5G/LTE switcher & signal info",
            icon = Icons.Default.NetworkCell,
            accentColor = NetworkBlue,
            modifier = Modifier.padding(bottom = Spacing.sm)
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            LiquidGlassButton(
                text = "View Signal",
                onClick = onNavigateToNetwork,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Speed Monitor Card
        FeatureCard(
            title = "Speed Monitor",
            subtitle = if (SpeedMonitorService.isRunning) {
                "%.2f Mbps".format(SpeedMonitorService.currentSpeedMbps)
            } else "Live download speed",
            icon = Icons.Default.Speed,
            accentColor = SpeedOrange,
            isActive = SpeedMonitorService.isRunning,
            modifier = Modifier.padding(bottom = Spacing.sm)
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            LiquidGlassButton(
                text = "Monitor",
                onClick = onNavigateToSpeed,
                modifier = Modifier.fillMaxWidth()
            )
        }

    }
}
