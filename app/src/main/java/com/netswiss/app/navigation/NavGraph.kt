package com.netswiss.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netswiss.app.ui.screens.HomeScreen
import com.netswiss.app.ui.screens.MockGpsScreen
import com.netswiss.app.ui.screens.NetworkScreen
import com.netswiss.app.ui.screens.SpeedScreen
import com.netswiss.app.ui.screens.FirewallScreen
import com.netswiss.app.ui.screens.DiagnosticsScreen
import com.netswiss.app.ui.theme.surfaceColorAtElevation

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Gps : Screen("gps", "GPS", Icons.Filled.LocationOn, Icons.Outlined.LocationOn)
    data object Network : Screen("network", "Network", Icons.Filled.NetworkCell, Icons.Outlined.NetworkCell)
    data object Speed : Screen("speed", "Speed", Icons.Filled.Speed, Icons.Outlined.Speed)
    data object Diagnostics : Screen("diagnostics", "Diag", Icons.Filled.NetworkCheck, Icons.Filled.NetworkCheck)
    data object Firewall : Screen("firewall", "Firewall", Icons.Filled.Shield, Icons.Outlined.Shield)
    // Radar removed
}

val bottomNavItems = listOf(Screen.Home, Screen.Gps, Screen.Network, Screen.Speed, Screen.Diagnostics, Screen.Firewall)

@Composable
fun NavGraph(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val haptic = LocalHapticFeedback.current

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        modifier = Modifier.height(72.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                    ) {
                        listOf(Screen.Home, Screen.Gps, Screen.Network, Screen.Speed).forEach { screen ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true

                            val scale by animateFloatAsState(
                                targetValue = if (selected) 1.04f else 0.96f,
                                label = "navIconScale"
                            )

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.label,
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .graphicsLayer(scaleX = scale, scaleY = scale)
                                    )
                                },
                                label = { Text(screen.label) },
                                selected = selected,
                                alwaysShowLabel = false,
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToGps = {
                        navController.navigate(Screen.Gps.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToNetwork = {
                        navController.navigate(Screen.Network.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSpeed = {
                        navController.navigate(Screen.Speed.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToDiagnostics = {
                        navController.navigate(Screen.Diagnostics.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToFirewall = {
                        navController.navigate(Screen.Firewall.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            composable(Screen.Gps.route) { MockGpsScreen(paddingValues = innerPadding) }
            composable(Screen.Network.route) { NetworkScreen(modifier = Modifier.padding(innerPadding)) }
            composable(Screen.Speed.route) { SpeedScreen(modifier = Modifier.padding(innerPadding)) }
            composable(Screen.Diagnostics.route) { DiagnosticsScreen(paddingValues = innerPadding) }
            composable(Screen.Firewall.route) { FirewallScreen(paddingValues = innerPadding) }
        }
    }
}
