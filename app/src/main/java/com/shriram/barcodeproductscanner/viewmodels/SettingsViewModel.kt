package com.shriram.barcodeproductscanner.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shriram.barcodeproductscanner.data.AppDatabase
import com.shriram.barcodeproductscanner.data.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class ImageNamingFormat(
    val includeBarcode: Boolean = true,
    val includeProductName: Boolean = false,
    val productName: String = ""
)

data class SettingsUiState(
    val imagingNamingFormat: ImageNamingFormat = ImageNamingFormat(),
    val isEditing: Boolean = false
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var currentProduct: Product? = null

    companion object {
        private val IMAGE_FORMAT_KEY = stringPreferencesKey("image_format")
        private val json = Json { 
            ignoreUnknownKeys = true 
            prettyPrint = true
        }
    }

    fun loadSettings(context: Context) {
        viewModelScope.launch {
            try {
                val preferences = context.dataStore.data.first()
                val formatJson = preferences[IMAGE_FORMAT_KEY]
                if (formatJson != null) {
                    val format = json.decodeFromString<ImageNamingFormat>(formatJson)
                    _uiState.value = SettingsUiState(imagingNamingFormat = format)
                }
            } catch (e: Exception) {
                // If there's an error loading settings, keep the default values
                _uiState.value = SettingsUiState()
            }
        }
    }

    fun loadProductData(context: Context, barcode: String) {
        val dao = AppDatabase.getDatabase(context).productDao()
        viewModelScope.launch {
            dao.getProduct(barcode).collect { product ->
                currentProduct = product
                if (product != null) {
                    updateImageNamingFormat(
                        _uiState.value.imagingNamingFormat.copy(
                            productName = product.productName
                        )
                    )
                }
            }
        }
    }

    fun saveSettings(context: Context) {
        viewModelScope.launch {
            try {
                val formatJson = json.encodeToString(_uiState.value.imagingNamingFormat)
                context.dataStore.edit { preferences ->
                    preferences[IMAGE_FORMAT_KEY] = formatJson
                }

                // Save product data if we have a current product
                currentProduct?.let { product ->
                    val updatedProduct = product.copy(
                        productName = _uiState.value.imagingNamingFormat.productName,
                        lastModified = System.currentTimeMillis()
                    )
                    AppDatabase.getDatabase(context).productDao()
                        .insertOrUpdateProduct(updatedProduct)
                }
            } catch (e: Exception) {
                // Handle save error if needed
            }
        }
    }

    fun updateImageNamingFormat(format: ImageNamingFormat) {
        _uiState.value = _uiState.value.copy(
            imagingNamingFormat = format
        )
    }

    fun toggleEditing() {
        _uiState.value = _uiState.value.copy(
            isEditing = !_uiState.value.isEditing
        )
    }

    fun generateImageName(barcodeNumber: String): String {
        val format = _uiState.value.imagingNamingFormat
        val parts = mutableListOf<String>()

        // Add barcode only if it's enabled
        if (format.includeBarcode) {
            parts.add(barcodeNumber)
        }

        // Add product name if enabled and not blank
        if (format.includeProductName && format.productName.isNotBlank()) {
            parts.add(format.productName)
        }

        // If no parts are added (both disabled or product name is blank), use a default name
        // If only product name is enabled but blank, use barcode as fallback
        return when {
            parts.isEmpty() -> barcodeNumber
            format.includeProductName && !format.includeBarcode && format.productName.isNotBlank() -> format.productName
            else -> parts.joinToString("-")
        }
    }
} 