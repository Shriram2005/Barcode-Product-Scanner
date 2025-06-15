package com.shriram.barcodeproductscanner.viewmodels

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shriram.barcodeproductscanner.R
import com.shriram.barcodeproductscanner.data.Product
import com.shriram.barcodeproductscanner.data.ProductHistoryRepository
import com.shriram.barcodeproductscanner.utils.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data class to represent a product with images in the history tabs
 */
data class ImageProduct(
    val identifier: String,  // Barcode or product code
    val lastModified: Long,  // Get from the most recent image's file timestamp
    val images: List<Uri>,   // All images for this product
    val isPrimaryImage: Boolean = false // True for barcode images, false for product code images
)

enum class HistoryTab {
    BARCODE, PRODUCT_CODE
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val barcodeProducts: List<ImageProduct> = emptyList(),
    val productCodeProducts: List<ImageProduct> = emptyList(),
    val searchResults: List<ImageProduct> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val error: String? = null,
    val selectedTab: HistoryTab = HistoryTab.BARCODE
) {
    // Get current products based on selected tab and search state
    val currentProducts: List<ImageProduct>
        get() = when {
            isSearching && searchQuery.isNotEmpty() -> searchResults
            selectedTab == HistoryTab.BARCODE -> barcodeProducts
            else -> productCodeProducts
        }
}

class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun loadProducts(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Load products from barcode folder
                val barcodeProducts = loadProductsFromFolder(context, false)
                
                // Load products from product code folder
                val productCodeProducts = loadProductsFromFolder(context, true)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    barcodeProducts = barcodeProducts,
                    productCodeProducts = productCodeProducts
                )
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error loading products: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    private suspend fun loadProductsFromFolder(context: Context, useProductCode: Boolean): List<ImageProduct> {
        // Get all products with images from the folder
        val productsMap = ImageUtils.getAllProductImages(context, useProductCode)
        
        // Convert to ImageProduct objects sorted by last modified date
        return productsMap.map { (identifier, uris) ->
            // Get the last modified timestamp from the file if possible
            val lastModified = try {
                val projection = arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
                // Just get the timestamp of the first (most recent) image
                val uri = uris.firstOrNull() ?: return@map ImageProduct(identifier, 0, uris, !useProductCode)
                
                context.contentResolver.query(
                    uri, 
                    projection, 
                    null, 
                    null, 
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                        if (columnIndex >= 0) {
                            cursor.getLong(columnIndex) * 1000L // Convert to milliseconds
                        } else 0L
                    } else 0L
                } ?: 0L
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error getting file date: ${e.message}")
                0L
            }
            
            ImageProduct(identifier, lastModified, uris, !useProductCode)
        }.sortedByDescending { it.lastModified }
    }

    fun deleteProduct(identifier: String, useProductCode: Boolean, context: Context) {
        viewModelScope.launch {
            try {
                Log.d("HistoryViewModel", "Starting deletion of images for identifier: $identifier, useProductCode: $useProductCode")
                
                // Delete images from the appropriate folder
                val imagesDeleted = ImageUtils.deleteFromMediaStore(context, identifier, 
                    ImageUtils.getFolderPath(useProductCode))
                
                // Show toast if images were deleted
                if (imagesDeleted > 0) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.images_deleted), 
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Refresh the list
                loadProducts(context)
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error deleting product: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteAllProducts(context: Context) {
        viewModelScope.launch {
            try {
                // Delete all images from both folders
                val imagesDeleted = ImageUtils.deleteAllProductImages(context)
                
                if (imagesDeleted) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.all_images_deleted), 
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Update UI state immediately
                _uiState.value = _uiState.value.copy(
                    barcodeProducts = emptyList(),
                    productCodeProducts = emptyList()
                )
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error deleting all products: ${e.message}", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun toggleSearch() {
        _uiState.value = _uiState.value.copy(
            isSearching = !_uiState.value.isSearching,
            searchQuery = "",
            searchResults = emptyList()
        )
        searchJob?.cancel()
    }

    fun updateSearchQuery(query: String, context: Context) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        // Cancel previous search job
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }

        // Debounce search
        searchJob = viewModelScope.launch {
            delay(300) // Wait 300ms before searching
            searchProducts(query, context)
        }
    }

    private suspend fun searchProducts(query: String, context: Context) {
        try {
            val useProductCode = _uiState.value.selectedTab == HistoryTab.PRODUCT_CODE
            val searchResults = ImageUtils.searchProducts(context, query, useProductCode)
            
            // Convert to ImageProduct objects
            val results = searchResults.map { (identifier, uris) ->
                val lastModified = try {
                    val projection = arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
                    val uri = uris.firstOrNull() ?: return@map ImageProduct(identifier, 0, uris, !useProductCode)
                    
                    context.contentResolver.query(
                        uri, 
                        projection, 
                        null, 
                        null, 
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                            if (columnIndex >= 0) {
                                cursor.getLong(columnIndex) * 1000L // Convert to milliseconds
                            } else 0L
                        } else 0L
                    } ?: 0L
                } catch (e: Exception) {
                    0L
                }
                
                ImageProduct(identifier, lastModified, uris, !useProductCode)
            }.sortedByDescending { it.lastModified }

            _uiState.value = _uiState.value.copy(searchResults = results)
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "Error searching products: ${e.message}")
            _uiState.value = _uiState.value.copy(error = e.message)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }
    
    fun selectTab(tab: HistoryTab) {
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            isSearching = false,
            searchQuery = "",
            searchResults = emptyList()
        )
    }
}
