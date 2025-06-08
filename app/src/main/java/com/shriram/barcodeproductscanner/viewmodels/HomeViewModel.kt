package com.shriram.barcodeproductscanner.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shriram.barcodeproductscanner.data.AppDatabase
import com.shriram.barcodeproductscanner.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalProducts: Int = 0,
    val recentProducts: List<Product> = emptyList(),
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun loadData(context: Context) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val database = AppDatabase.getDatabase(context)
                val productDao = database.productDao()
                
                // Get total products count
                val totalProducts = productDao.getProductCount()
                
                // Get recent products (last 10, ordered by lastModified)
                val recentProducts = productDao.getRecentProducts(10)
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    totalProducts = totalProducts,
                    recentProducts = recentProducts
                )
            } catch (e: Exception) {
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
