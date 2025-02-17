package com.shriram.barcodeproductscanner.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shriram.barcodeproductscanner.screens.BarcodeScanScreen
import com.shriram.barcodeproductscanner.screens.ImageCaptureScreen

sealed class Screen(val route: String) {
    data object BarcodeScanner : Screen("barcode_scanner")
    data object ImageCapture : Screen("image_capture/{barcodeNumber}") {
        fun createRoute(barcodeNumber: String) = "image_capture/$barcodeNumber"
    }
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
                    navController.navigateUp()
                }
            )
        }
    }
} 