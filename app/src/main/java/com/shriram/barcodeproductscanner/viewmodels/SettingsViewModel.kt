package com.shriram.barcodeproductscanner.viewmodels

import android.content.Context
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shriram.barcodeproductscanner.data.ProductMappingRepository
import com.shriram.barcodeproductscanner.utils.ImageUtils
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
    val useProductCode: Boolean = false  // New: use product code instead of barcode
)

data class SettingsUiState(
    val imagingNamingFormat: ImageNamingFormat = ImageNamingFormat(),
    val csvImported: Boolean = false,
    val csvMappingCount: Int = 0,
    val showCsvImportDialog: Boolean = false,
    val csvImportError: String? = null,
    val csvImportSuccess: Boolean = false
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val productMappingRepository = ProductMappingRepository()

    companion object {
        private val IMAGE_FORMAT_KEY = stringPreferencesKey("image_format")
        private val CSV_IMPORTED_KEY = booleanPreferencesKey("csv_imported")
        private val CSV_CONTENT_KEY = stringPreferencesKey("csv_content")
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }

    fun loadSettings(context: Context) {
        viewModelScope.launch {
            try {
                val preferences = context.dataStore.data.first()

                // Load image format settings
                val formatJson = preferences[IMAGE_FORMAT_KEY]
                val format = if (formatJson != null) {
                    json.decodeFromString<ImageNamingFormat>(formatJson)
                } else {
                    ImageNamingFormat()
                }

                // Load CSV settings
                val csvImported = preferences[CSV_IMPORTED_KEY] ?: false
                val csvContent = preferences[CSV_CONTENT_KEY]

                // Load CSV mappings if available
                if (csvImported && csvContent != null) {
                    productMappingRepository.loadMappingsFromCsv(csvContent)
                }

                _uiState.value = SettingsUiState(
                    imagingNamingFormat = format,
                    csvImported = csvImported,
                    csvMappingCount = productMappingRepository.getMappingCount()
                )
            } catch (e: Exception) {
                // If there's an error loading settings, keep the default values
                _uiState.value = SettingsUiState()
            }
        }
    }

    fun loadProductData(context: Context, barcode: String) {
        // This method no longer needs to load product data from Room database
        // It's kept for API compatibility but doesn't need to do anything specific
    }

    fun saveSettings(context: Context) {
        viewModelScope.launch {
            try {
                // Check if the naming format has changed
                val oldFormat = try {
                    val preferences = context.dataStore.data.first()
                    val formatJson = preferences[IMAGE_FORMAT_KEY]
                    if (formatJson != null) {
                        json.decodeFromString<ImageNamingFormat>(formatJson)
                    } else {
                        ImageNamingFormat()
                    }
                } catch (e: Exception) {
                    ImageNamingFormat()
                }
                
                val newFormat = _uiState.value.imagingNamingFormat
                val formatChanged = oldFormat.useProductCode != newFormat.useProductCode
                
                // Save the new format
                val formatJson = json.encodeToString(_uiState.value.imagingNamingFormat)
                context.dataStore.edit { preferences ->
                    preferences[IMAGE_FORMAT_KEY] = formatJson
                    preferences[CSV_IMPORTED_KEY] = _uiState.value.csvImported
                }

                // We no longer need to update Room database since we're not using it
                
                // If format was changed significantly, we could potentially move files, 
                // but that's a complex operation that might not be necessary for most users
            } catch (e: Exception) {
                // Handle save error if needed
                Log.e("SettingsViewModel", "Error saving settings: ${e.message}", e)
            }
        }
    }

    fun updateImageNamingFormat(format: ImageNamingFormat) {
        _uiState.value = _uiState.value.copy(
            imagingNamingFormat = format
        )
    }

    fun toggleProductCodeNaming(enabled: Boolean) {
        val currentFormat = _uiState.value.imagingNamingFormat
        val newFormat = currentFormat.copy(useProductCode = enabled)
        _uiState.value = _uiState.value.copy(imagingNamingFormat = newFormat)
    }

    // CSV Import functionality
    fun importCsvFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val csvContent = inputStream?.bufferedReader()?.use { it.readText() }

                if (csvContent != null) {
                    productMappingRepository.loadMappingsFromCsv(csvContent)
                    val mappingCount = productMappingRepository.getMappingCount()

                    if (mappingCount > 0) {
                        // Save CSV content and update state
                        context.dataStore.edit { preferences ->
                            preferences[CSV_CONTENT_KEY] = csvContent
                            preferences[CSV_IMPORTED_KEY] = true
                        }

                        _uiState.value = _uiState.value.copy(
                            csvImported = true,
                            csvMappingCount = mappingCount,
                            csvImportSuccess = true,
                            csvImportError = null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            csvImportError = "No valid product mappings found in CSV file"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        csvImportError = "Failed to read CSV file"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    csvImportError = "Error importing CSV: ${e.message}"
                )
            }
        }
    }

    fun clearCsvImportMessages() {
        _uiState.value = _uiState.value.copy(
            csvImportError = null,
            csvImportSuccess = false
        )
    }

    // Generate the actual image name using barcode or product code
    fun generateImageName(barcodeNumber: String): String {
        val format = _uiState.value.imagingNamingFormat
        return if (format.useProductCode) {
            productMappingRepository.getProductCode(barcodeNumber) ?: barcodeNumber
        } else {
            barcodeNumber
        }
    }

    // For preview purposes in settings screen
    fun generatePreviewName(barcodeNumber: String): String {
        val format = _uiState.value.imagingNamingFormat
        return if (format.useProductCode) {
            "00-00070425" // Example product code
        } else {
            barcodeNumber
        }
    }

    // Get product code for a barcode (used by ImageCaptureViewModel)
    fun getProductCode(barcode: String): String? {
        return productMappingRepository.getProductCode(barcode)
    }

    // Check if product code naming is enabled
    fun isProductCodeNamingEnabled(): Boolean {
        return _uiState.value.imagingNamingFormat.useProductCode
    }

    // Check if CSV is imported
    fun isCsvImported(): Boolean {
        return _uiState.value.csvImported && productMappingRepository.hasMappings()
    }
}