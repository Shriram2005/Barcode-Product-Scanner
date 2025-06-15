package com.shriram.barcodeproductscanner.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.shriram.barcodeproductscanner.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val BARCODE_FOLDER = "ProductScanner/Barcodes"
    private const val PRODUCT_CODE_FOLDER = "ProductScanner/ProductCodes"

    /**
     * Get the appropriate folder path based on naming format
     */
    fun getFolderPath(useProductCode: Boolean): String {
        return Environment.DIRECTORY_PICTURES + "/" + 
            if (useProductCode) PRODUCT_CODE_FOLDER else BARCODE_FOLDER
    }

    /**
     * Public method to delete images from MediaStore in a specific folder
     * Returns the number of images deleted
     */
    suspend fun deleteFromMediaStore(context: Context, prefix: String, folderPath: String): Int {
        return withContext(Dispatchers.IO) {
            var deletedCount = 0
            
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf(
                    "%$folderPath%",
                    "$prefix%.jpg"
                )
                
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                
                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    
                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val name = it.getString(nameColumn)
                        
                        // Check if the filename starts with the prefix
                        if (name.startsWith(prefix)) {
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            
                            val deleted = context.contentResolver.delete(uri, null, null)
                            if (deleted > 0) {
                                deletedCount++
                                Log.d(TAG, "Deleted image from MediaStore: $name in folder $folderPath")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting from MediaStore", e)
            }
            
            deletedCount
        }
    }

    /**
     * Delete all images associated with a specific barcode/product
     */
    suspend fun deleteProductImages(context: Context, barcode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0
                
                // Get the product code if available
                val settingsViewModel = SettingsViewModel()
                settingsViewModel.loadSettings(context)
                val productCode = settingsViewModel.getProductCode(barcode)
                
                Log.d(TAG, "Deleting images for barcode: $barcode, product code: $productCode")
                
                // Delete barcode-named images from the barcode folder
                deletedCount += deleteFromMediaStore(context, barcode, BARCODE_FOLDER)
                deletedCount += deleteFromExternalStorage(barcode, BARCODE_FOLDER)
                
                // Delete product code-named images from product code folder if available
                if (productCode != null) {
                    deletedCount += deleteFromMediaStore(context, productCode, PRODUCT_CODE_FOLDER)
                    deletedCount += deleteFromExternalStorage(productCode, PRODUCT_CODE_FOLDER)
                }
                
                Log.d(TAG, "Deleted $deletedCount images for barcode: $barcode")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting images for barcode: $barcode", e)
                false
            }
        }
    }

    /**
     * Delete images from external storage in a specific folder
     */
    private fun deleteFromExternalStorage(prefix: String, folderPath: String): Int {
        var deletedCount = 0
        
        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val targetFolder = File(picturesDir, folderPath)
            
            if (targetFolder.exists() && targetFolder.isDirectory) {
                val files = targetFolder.listFiles { file ->
                    file.isFile && file.name.startsWith(prefix) && 
                    (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png"))
                }
                
                files?.forEach { file ->
                    if (file.delete()) {
                        deletedCount++
                        Log.d(TAG, "Deleted image from external storage: ${file.name} in folder $folderPath")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting from external storage", e)
        }
        
        return deletedCount
    }

    /**
     * Get all images for a specific barcode from a specific folder
     */
    suspend fun getProductImagesFromFolder(context: Context, prefix: String, useProductCode: Boolean): List<Uri> {
        return withContext(Dispatchers.IO) {
            val images = mutableListOf<Uri>()
            
            try {
                // Determine which folder to look in
                val folderPath = getFolderPath(useProductCode)
                
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf(
                    "%$folderPath%",
                    "$prefix%.jpg"
                )
                
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )
                
                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    
                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val name = it.getString(nameColumn)
                        
                        if (name.startsWith(prefix)) {
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            images.add(uri)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting product images", e)
            }
            
            images
        }
    }
    
    /**
     * Get all scanned products with images (barcodes or product codes)
     * Returns a map of identifier to URIs
     */
    suspend fun getAllProductImages(context: Context, useProductCode: Boolean): Map<String, List<Uri>> {
        return withContext(Dispatchers.IO) {
            val productsMap = mutableMapOf<String, MutableList<Uri>>()
            
            try {
                // Determine which folder to look in
                val folderPath = getFolderPath(useProductCode)
                
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$folderPath%")
                
                val cursor = context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )
                
                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    
                    while (it.moveToNext()) {
                        val id = it.getLong(idColumn)
                        val name = it.getString(nameColumn)
                        val uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        
                        // Extract the product identifier (barcode or product code)
                        // It will be either the full filename (for base images) or everything before the first dash
                        val identifier = if (name.contains("-")) {
                            name.substring(0, name.indexOf("-"))
                        } else {
                            name.substring(0, name.lastIndexOf("."))
                        }
                        
                        // Add to the map
                        if (!productsMap.containsKey(identifier)) {
                            productsMap[identifier] = mutableListOf()
                        }
                        productsMap[identifier]!!.add(uri)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting all product images", e)
            }
            
            // Sort each product's images - first the base image, then numbered images in order
            productsMap.forEach { (identifier, uris) ->
                productsMap[identifier] = uris.sortedWith(compareBy { uri ->
                    val filename = uri.lastPathSegment?.substringAfterLast("/") ?: ""
                    if (filename == "$identifier.jpg") {
                        -1 // Base image first
                    } else if (filename.startsWith("$identifier-") && filename.endsWith(".jpg")) {
                        val numberStr = filename.substringAfter("-").substringBefore(".")
                        numberStr.toIntOrNull() ?: Int.MAX_VALUE
                    } else {
                        Int.MAX_VALUE
                    }
                }).toMutableList()
            }
            
            productsMap
        }
    }

    /**
     * Delete all images in both folders
     */
    suspend fun deleteAllProductImages(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0
                
                // Delete from both folders
                deletedCount += deleteAllImagesFromFolder(context, BARCODE_FOLDER)
                deletedCount += deleteAllImagesFromFolder(context, PRODUCT_CODE_FOLDER)
                
                Log.d(TAG, "Deleted $deletedCount total images from both folders")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting all images", e)
                false
            }
        }
    }
    
    private suspend fun deleteAllImagesFromFolder(context: Context, folderPath: String): Int {
        var deletedCount = 0
        
        // Delete from MediaStore
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$folderPath%")
            
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    
                    val deleted = context.contentResolver.delete(uri, null, null)
                    if (deleted > 0) {
                        deletedCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting from MediaStore", e)
        }
        
        // Delete from external storage
        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val folderFile = File(picturesDir, folderPath)
            
            if (folderFile.exists() && folderFile.isDirectory) {
                folderFile.listFiles()?.forEach { file ->
                    if (file.isFile && file.delete()) {
                        deletedCount++
                    }
                }
                
                // Try to delete empty directory
                if (folderFile.listFiles()?.isEmpty() == true) {
                    folderFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting from external storage", e)
        }
        
        return deletedCount
    }

    /**
     * Delete images for a given barcode using its product code
     */
    suspend fun deleteProductImagesUsingProductCode(context: Context, barcode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0
                
                // Get the product code
                val settingsViewModel = SettingsViewModel()
                settingsViewModel.loadSettings(context)
                val productCode = settingsViewModel.getProductCode(barcode)
                
                if (productCode != null) {
                    Log.d(TAG, "Deleting images for product code: $productCode (barcode: $barcode)")
                    
                    // Delete from product code folder
                    deletedCount += deleteFromMediaStore(context, productCode, PRODUCT_CODE_FOLDER)
                    deletedCount += deleteFromExternalStorage(productCode, PRODUCT_CODE_FOLDER)
                    
                    Log.d(TAG, "Deleted $deletedCount images for product code: $productCode")
                    deletedCount > 0
                } else {
                    Log.d(TAG, "No product code found for barcode: $barcode")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting images for barcode using product code: $barcode", e)
                false
            }
        }
    }
    
    /**
     * Search for products that match a query string
     */
    suspend fun searchProducts(context: Context, query: String, useProductCode: Boolean): Map<String, List<Uri>> {
        val allProducts = getAllProductImages(context, useProductCode)
        return allProducts.filterKeys { key ->
            key.contains(query, ignoreCase = true)
        }
    }
}
