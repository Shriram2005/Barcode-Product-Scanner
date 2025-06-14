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
    val includeBarcode: Boolean = true
)

data class SettingsUiState(
    val imagingNamingFormat: ImageNamingFormat = ImageNamingFormat()
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



    // Generate the actual image name using only barcode
    fun generateImageName(barcodeNumber: String): String {
        return barcodeNumber
    }

    // For preview purposes in settings screen
    fun generatePreviewName(barcodeNumber: String): String {
        return barcodeNumber
    }
} 