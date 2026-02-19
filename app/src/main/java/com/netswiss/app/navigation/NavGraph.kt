package com.netswiss.app.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.netswiss.app.ui.components.glass.LiquidGlassBackground

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
    data object Firewall : Screen("firewall", "Firewall", Icons.Filled.Shield, Icons.Outlined.Shield)
    // Radar removed
}

val bottomNavItems = listOf(Screen.Home, Screen.Gps, Screen.Network, Screen.Speed, Screen.Firewall)

@Composable
fun NavGraph(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Box(modifier = Modifier.fillMaxSize()) {
        LiquidGlassBackground()
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
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
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
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
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
                composable(Screen.Gps.route) { MockGpsScreen() }
                composable(Screen.Network.route) { NetworkScreen() }
                composable(Screen.Speed.route) { SpeedScreen() }
                composable(Screen.Firewall.route) { FirewallScreen(paddingValues = innerPadding) }
            }
        }
    }
}
