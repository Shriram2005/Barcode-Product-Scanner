package com.shriram.barcodeproductscanner.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shriram.barcodeproductscanner.R
import com.shriram.barcodeproductscanner.screens.*

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object BarcodeScanner : Screen("barcode_scanner")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object ImageCapture : Screen("image_capture/{barcodeNumber}") {
        fun createRoute(barcodeNumber: String) = "image_capture/$barcodeNumber"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home, Icons.Default.Home, stringResource(R.string.home)),
        BottomNavItem(Screen.BarcodeScanner, Icons.Default.QrCodeScanner, stringResource(R.string.scan)),
        BottomNavItem(Screen.History, Icons.Default.History, stringResource(R.string.history))
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Check if current screen should show bottom navigation
    val showBottomNav = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.BarcodeScanner.route,
        Screen.History.route
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                            onClick = {
                                navController.navigate(item.screen.route) {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToScanner = {
                        navController.navigate(Screen.BarcodeScanner.route)
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.History.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToProduct = { barcodeNumber ->
                        navController.navigate(Screen.ImageCapture.createRoute(barcodeNumber))
                    }
                )
            }

            composable(Screen.BarcodeScanner.route) {
                BarcodeScanScreen(
                    onBarcodeDetected = { barcodeNumber ->
                        navController.navigate(Screen.ImageCapture.createRoute(barcodeNumber))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToProduct = { barcodeNumber ->
                        navController.navigate(Screen.ImageCapture.createRoute(barcodeNumber))
                    }
                )
            }

            composable(
                route = Screen.ImageCapture.route
            ) { backStackEntry ->
                val barcodeNumber = backStackEntry.arguments?.getString("barcodeNumber") ?: return@composable
                ImageCaptureScreen(
                    barcodeNumber = barcodeNumber,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}