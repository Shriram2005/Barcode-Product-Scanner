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

    @Query("SELECT * FROM products ORDER BY lastModified DESC LIMIT :limit")
    suspend fun getRecentProducts(limit: Int): List<Product>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("SELECT * FROM products WHERE barcode LIKE '%' || :query || '%' OR productName LIKE '%' || :query || '%' ORDER BY lastModified DESC")
    suspend fun searchProducts(query: String): List<Product>

    @Query("DELETE FROM products WHERE barcode = :barcode")
    suspend fun deleteProduct(barcode: String)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}