package com.shriram.barcodeproductscanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE barcode = :barcode")
    fun getProduct(barcode: String): Flow<Product?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProduct(product: Product)

    @Query("SELECT * FROM products ORDER BY lastModified DESC")
    fun getAllProducts(): Flow<List<Product>>
} 