package com.shriram.barcodeproductscanner.data

import kotlinx.serialization.Serializable

/**
 * Simple data class to represent a product that has been scanned
 */
@Serializable
data class Product(
    val barcode: String,
    val lastModified: Long = System.currentTimeMillis()
)