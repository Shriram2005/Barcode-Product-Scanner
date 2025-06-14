package com.shriram.barcodeproductscanner.ui.theme

import androidx.compose.ui.graphics.Color

// Simple, Clean Color Scheme - Light Theme
val Primary = Color(0xFF2196F3)        // Simple blue
val OnPrimary = Color(0xFFFFFFFF)      // White
val PrimaryContainer = Color(0xFFE3F2FD) // Light blue
val OnPrimaryContainer = Color(0xFF1565C0) // Dark blue

val Secondary = Color(0xFF757575)       // Simple gray
val OnSecondary = Color(0xFFFFFFFF)     // White
val SecondaryContainer = Color(0xFFF5F5F5) // Light gray
val OnSecondaryContainer = Color(0xFF424242) // Dark gray

val Tertiary = Color(0xFF4CAF50)        // Simple green
val OnTertiary = Color(0xFFFFFFFF)      // White
val TertiaryContainer = Color(0xFFE8F5E8) // Light green
val OnTertiaryContainer = Color(0xFF2E7D32) // Dark green

val Error = Color(0xFFF44336)           // Simple red
val OnError = Color(0xFFFFFFFF)         // White
val ErrorContainer = Color(0xFFFFEBEE)  // Light red
val OnErrorContainer = Color(0xFFD32F2F) // Dark red

val Background = Color(0xFFFFFFFF)      // Pure white
val OnBackground = Color(0xFF212121)    // Dark gray
val Surface = Color(0xFFFFFFFF)         // Pure white
val OnSurface = Color(0xFF212121)       // Dark gray
val SurfaceVariant = Color(0xFFFAFAFA)  // Very light gray
val OnSurfaceVariant = Color(0xFF616161) // Medium gray

// Simple, Clean Color Scheme - Dark Theme
val PrimaryDark = Color(0xFF64B5F6)     // Light blue
val OnPrimaryDark = Color(0xFF0D47A1)   // Dark blue
val PrimaryContainerDark = Color(0xFF1976D2) // Medium blue
val OnPrimaryContainerDark = Color(0xFFE3F2FD) // Very light blue

val SecondaryDark = Color(0xFF9E9E9E)    // Light gray
val OnSecondaryDark = Color(0xFF212121)  // Dark gray
val SecondaryContainerDark = Color(0xFF616161) // Medium gray
val OnSecondaryContainerDark = Color(0xFFF5F5F5) // Light gray

val TertiaryDark = Color(0xFF81C784)     // Light green
val OnTertiaryDark = Color(0xFF1B5E20)   // Dark green
val TertiaryContainerDark = Color(0xFF388E3C) // Medium green
val OnTertiaryContainerDark = Color(0xFFE8F5E8) // Very light green

val ErrorDark = Color(0xFFEF5350)        // Light red
val OnErrorDark = Color(0xFFB71C1C)      // Dark red
val ErrorContainerDark = Color(0xFFD32F2F) // Medium red
val OnErrorContainerDark = Color(0xFFFFEBEE) // Very light red

val BackgroundDark = Color(0xFF121212)    // Very dark gray
val OnBackgroundDark = Color(0xFFE0E0E0) // Light gray
val SurfaceDark = Color(0xFF1E1E1E)      // Dark gray
val OnSurfaceDark = Color(0xFFE0E0E0)    // Light gray
val SurfaceVariantDark = Color(0xFF2C2C2C) // Medium dark gray
val OnSurfaceVariantDark = Color(0xFFBDBDBD) // Medium light gray

// Scanner specific colors
val ScannerOverlay = Color(0x80000000)
val ScannerFrame = Color(0xFFFFFFFF)
val ScannerLine = Color(0xFF4CAF50)
val ScannerSuccess = Color(0xFF4CAF50)
val ScannerError = Color(0xFFD32F2F)

// Legacy colors for compatibility
val Purple80 = PrimaryDark
val PurpleGrey80 = SurfaceVariantDark
val Pink80 = TertiaryDark

val Purple40 = Primary
val PurpleGrey40 = SurfaceVariant
val Pink40 = Tertiary