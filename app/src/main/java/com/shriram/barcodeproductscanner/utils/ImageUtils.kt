package com.shriram.barcodeproductscanner.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val PRODUCT_SCANNER_FOLDER = "ProductScanner"

    /**
     * Delete all images associated with a specific barcode/product
     */
    suspend fun deleteProductImages(context: Context, barcode: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0
                
                // Delete from MediaStore (Android 10+)
                deletedCount += deleteFromMediaStore(context, barcode)
                
                // Delete from external storage (fallback for older versions)
                deletedCount += deleteFromExternalStorage(barcode)
                
                Log.d(TAG, "Deleted $deletedCount images for barcode: $barcode")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting images for barcode: $barcode", e)
                false
            }
        }
    }

    /**
     * Delete images from MediaStore (Android 10+)
     */
    private fun deleteFromMediaStore(context: Context, barcode: String): Int {
        var deletedCount = 0
        
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
            )
            
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(
                "%$PRODUCT_SCANNER_FOLDER%",
                "$barcode%"
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
                    
                    // Check if the filename starts with the barcode
                    if (name.startsWith(barcode)) {
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        
                        val deleted = context.contentResolver.delete(uri, null, null)
                        if (deleted > 0) {
                            deletedCount++
                            Log.d(TAG, "Deleted image from MediaStore: $name")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting from MediaStore", e)
        }
        
        return deletedCount
    }

    /**
     * Delete images from external storage (fallback)
     */
    private fun deleteFromExternalStorage(barcode: String): Int {
        var deletedCount = 0
        
        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val productScannerDir = File(picturesDir, PRODUCT_SCANNER_FOLDER)
            
            if (productScannerDir.exists() && productScannerDir.isDirectory) {
                val files = productScannerDir.listFiles { file ->
                    file.isFile && file.name.startsWith(barcode) && 
                    (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png"))
                }
                
                files?.forEach { file ->
                    if (file.delete()) {
                        deletedCount++
                        Log.d(TAG, "Deleted image from external storage: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting from external storage", e)
        }
        
        return deletedCount
    }

    /**
     * Get all images for a specific barcode
     */
    suspend fun getProductImages(context: Context, barcode: String): List<Uri> {
        return withContext(Dispatchers.IO) {
            val images = mutableListOf<Uri>()
            
            try {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf(
                    "%$PRODUCT_SCANNER_FOLDER%",
                    "$barcode%"
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
                        
                        if (name.startsWith(barcode)) {
                            val uri = Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id.toString()
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
     * Delete all images in the ProductScanner folder
     */
    suspend fun deleteAllProductImages(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var deletedCount = 0
                
                // Delete from MediaStore
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$PRODUCT_SCANNER_FOLDER%")
                
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
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        
                        val deleted = context.contentResolver.delete(uri, null, null)
                        if (deleted > 0) {
                            deletedCount++
                        }
                    }
                }
                
                // Delete from external storage (fallback)
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val productScannerDir = File(picturesDir, PRODUCT_SCANNER_FOLDER)
                
                if (productScannerDir.exists() && productScannerDir.isDirectory) {
                    val files = productScannerDir.listFiles()
                    files?.forEach { file ->
                        if (file.isFile && file.delete()) {
                            deletedCount++
                        }
                    }
                    
                    // Try to delete the directory if it's empty
                    if (productScannerDir.listFiles()?.isEmpty() == true) {
                        productScannerDir.delete()
                    }
                }
                
                Log.d(TAG, "Deleted $deletedCount total images")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting all images", e)
                false
            }
        }
    }
}
