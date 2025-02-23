package com.shriram.barcodeproductscanner.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val includeDateTime: Boolean = false,
    val includeCustomText: Boolean = false,
    val customText: String = "",
    val productName: String = "",
    val separator: String = "-"
)

data class SettingsUiState(
    val imagingNamingFormat: ImageNamingFormat = ImageNamingFormat(),
    val isEditing: Boolean = false
)

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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

    fun saveSettings(context: Context) {
        viewModelScope.launch {
            try {
                val formatJson = json.encodeToString(_uiState.value.imagingNamingFormat)
                context.dataStore.edit { preferences ->
                    preferences[IMAGE_FORMAT_KEY] = formatJson
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

        if (format.includeBarcode) {
            parts.add(barcodeNumber)
        }
        if (format.includeProductName && format.productName.isNotBlank()) {
            parts.add(format.productName)
        }
        if (format.includeDateTime) {
            parts.add(java.time.LocalDateTime.now().toString().replace(":", "-"))
        }
        if (format.includeCustomText && format.customText.isNotBlank()) {
            parts.add(format.customText)
        }

        return if (parts.isEmpty()) barcodeNumber else parts.joinToString(format.separator)
    }
} 