package com.shriram.barcodeproductscanner.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shriram.barcodeproductscanner.screens.BarcodeScanScreen
import com.shriram.barcodeproductscanner.screens.ImageCaptureScreen
import com.shriram.barcodeproductscanner.screens.SettingsScreen

sealed class Screen(val route: String) {
    data object BarcodeScanner : Screen("barcode_scanner")
    data object ImageCapture : Screen("image_capture/{barcodeNumber}") {
        fun createRoute(barcodeNumber: String) = "image_capture/$barcodeNumber"
    }
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.BarcodeScanner.route
    ) {
        composable(Screen.BarcodeScanner.route) {
            BarcodeScanScreen(
                onBarcodeDetected = { barcodeNumber ->
                    navController.navigate(Screen.ImageCapture.createRoute(barcodeNumber)) {
                        popUpTo(Screen.BarcodeScanner.route) {
                            saveState = true
                        }
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
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
                    navController.navigate(Screen.BarcodeScanner.route) {
                        popUpTo(Screen.BarcodeScanner.route) {
                            inclusive = true
                        }
                    }
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