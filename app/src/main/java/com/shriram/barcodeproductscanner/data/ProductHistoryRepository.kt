package com.shriram.barcodeproductscanner.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.productHistoryDataStore by preferencesDataStore("product_history")

/**
 * Repository that manages product history using DataStore
 * Replaces Room database functionality
 */
class ProductHistoryRepository(private val context: Context) {
    
    companion object {
        private val PRODUCTS_KEY = stringPreferencesKey("products")
        private val json = Json { ignoreUnknownKeys = true }
        
        @Volatile
        private var INSTANCE: ProductHistoryRepository? = null
        
        fun getInstance(context: Context): ProductHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ProductHistoryRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Get a specific product by barcode
     */
    fun getProduct(barcode: String): Flow<Product?> {
        return context.productHistoryDataStore.data.map { preferences ->
            val productsJson = preferences[PRODUCTS_KEY] ?: return@map null
            try {
                val products: List<Product> = json.decodeFromString(productsJson)
                products.find { it.barcode == barcode }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Add or update a product in the history
     */
    suspend fun insertOrUpdateProduct(product: Product) {
        context.productHistoryDataStore.edit { preferences ->
            val existingJson = preferences[PRODUCTS_KEY]
            val products = if (existingJson != null) {
                try {
                    val existingProducts: List<Product> = json.decodeFromString(existingJson)
                    val filtered = existingProducts.filter { it.barcode != product.barcode }
                    filtered + product
                } catch (e: Exception) {
                    listOf(product)
                }
            } else {
                listOf(product)
            }
            
            preferences[PRODUCTS_KEY] = json.encodeToString(products)
        }
    }
    
    /**
     * Get all products ordered by lastModified
     */
    fun getAllProducts(): Flow<List<Product>> {
        return context.productHistoryDataStore.data.map { preferences ->
            val productsJson = preferences[PRODUCTS_KEY] ?: return@map emptyList()
            try {
                val products: List<Product> = json.decodeFromString(productsJson)
                products.sortedByDescending { it.lastModified }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get recent products with a limit
     */
    suspend fun getRecentProducts(limit: Int): List<Product> {
        val preferences = context.productHistoryDataStore.data.first()
        val productsJson = preferences[PRODUCTS_KEY] ?: return emptyList()
        
        return try {
            val products: List<Product> = json.decodeFromString(productsJson)
            products.sortedByDescending { it.lastModified }.take(limit)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get total count of products
     */
    suspend fun getProductCount(): Int {
        val preferences = context.productHistoryDataStore.data.first()
        val productsJson = preferences[PRODUCTS_KEY] ?: return 0
        
        return try {
            val products: List<Product> = json.decodeFromString(productsJson)
            products.size
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Search products by barcode
     */
    suspend fun searchProducts(query: String): List<Product> {
        val preferences = context.productHistoryDataStore.data.first()
        val productsJson = preferences[PRODUCTS_KEY] ?: return emptyList()
        
        return try {
            val products: List<Product> = json.decodeFromString(productsJson)
            products
                .filter { it.barcode.contains(query, ignoreCase = true) }
                .sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Delete a product by barcode
     */
    suspend fun deleteProduct(barcode: String) {
        context.productHistoryDataStore.edit { preferences ->
            val existingJson = preferences[PRODUCTS_KEY] ?: return@edit
            
            try {
                val existingProducts: List<Product> = json.decodeFromString(existingJson)
                val updatedProducts = existingProducts.filter { it.barcode != barcode }
                preferences[PRODUCTS_KEY] = json.encodeToString(updatedProducts)
            } catch (e: Exception) {
                // Handle error but don't crash
            }
        }
    }
    
    /**
     * Delete all products
     */
    suspend fun deleteAllProducts() {
        context.productHistoryDataStore.edit { preferences ->
            preferences.remove(PRODUCTS_KEY)
        }
    }
} 