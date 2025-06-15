package com.shriram.barcodeproductscanner.viewmodels

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shriram.barcodeproductscanner.utils.ImageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalProducts: Int = 0,
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadData(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                // Count products in both barcode and product code folders
                val barcodeProductsMap = ImageUtils.getAllProductImages(context, false)
                val productCodeProductsMap = ImageUtils.getAllProductImages(context, true)
                
                // Calculate total unique products by combining both maps
                val totalProducts = (barcodeProductsMap.keys + productCodeProductsMap.keys).size
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalProducts = totalProducts
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error loading data: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refreshData(context: Context) {
        loadData(context)
    }
}
