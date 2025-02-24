package com.shriram.barcodeproductscanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey
    val barcode: String,
    val productName: String = "",
    val lastModified: Long = System.currentTimeMillis()
) 