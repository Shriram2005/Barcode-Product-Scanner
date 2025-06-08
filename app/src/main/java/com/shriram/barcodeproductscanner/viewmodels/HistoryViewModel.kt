package com.shriram.barcodeproductscanner.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shriram.barcodeproductscanner.data.AppDatabase
import com.shriram.barcodeproductscanner.data.Product
import com.shriram.barcodeproductscanner.utils.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val allProducts: List<Product> = emptyList(),
    val searchResults: List<Product> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val error: String? = null
) {
    // Backward compatibility
    val products: List<Product>
        get() = if (isSearching && searchQuery.isNotEmpty()) searchResults else allProducts
}

class HistoryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun loadProducts(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val database = AppDatabase.getDatabase(context)
                val productDao = database.productDao()

                productDao.getAllProducts().collect { products ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        allProducts = products
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun deleteProduct(barcode: String, context: Context) {
        viewModelScope.launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val productDao = database.productDao()

                // Delete associated images first
                ImageUtils.deleteProductImages(context, barcode)

                // Then delete the product from database
                productDao.deleteProduct(barcode)

                // Refresh the list
                loadProducts(context)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteAllProducts(context: Context) {
        viewModelScope.launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val productDao = database.productDao()

                // Delete all associated images first
                ImageUtils.deleteAllProductImages(context)

                // Then delete all products from database
                productDao.deleteAllProducts()

                // Update UI state immediately
                _uiState.value = _uiState.value.copy(allProducts = emptyList())
            } catch (e: Exception) {
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
            val database = AppDatabase.getDatabase(context)
            val productDao = database.productDao()

            val results = productDao.searchProducts(query.trim())

            _uiState.value = _uiState.value.copy(searchResults = results)
        } catch (e: Exception) {
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
}
