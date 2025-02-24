package com.shriram.barcodeproductscanner.viewmodels

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shriram.barcodeproductscanner.data.AppDatabase
import com.shriram.barcodeproductscanner.data.Product
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CapturedImage(
    val uri: Uri,
    val fileName: String
)

data class ImageCaptureUiState(
    val barcodeNumber: String = "",
    val capturedImages: List<CapturedImage> = emptyList(),
    val selectedImage: CapturedImage? = null,
    val showSuccessMessage: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val showDeleteDialog: CapturedImage? = null,
    val tempImageUri: Uri? = null,
    val shouldLaunchCamera: Boolean = false
)

class ImageCaptureViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageCaptureUiState())
    val uiState: StateFlow<ImageCaptureUiState> = _uiState.asStateFlow()
    private val settingsViewModel: SettingsViewModel = SettingsViewModel()

    fun initialize(barcodeNumber: String, context: Context) {
        _uiState.update { it.copy(barcodeNumber = barcodeNumber) }
        // Load settings and product data
        settingsViewModel.loadSettings(context)
        settingsViewModel.loadProductData(context, barcodeNumber)

        // Create or update product in database
        viewModelScope.launch {
            val product = Product(
                barcode = barcodeNumber,
                lastModified = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).productDao().insertOrUpdateProduct(product)
        }
    }

    fun loadExistingImages(context: Context) {
        viewModelScope.launch {
            val baseName = settingsViewModel.generateImageName(_uiState.value.barcodeNumber)
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("$baseName-%")
            val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                val images = mutableListOf<CapturedImage>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    images.add(CapturedImage(contentUri, displayName))
                }
                val sortedImages = images.sortedBy { img -> img.fileName }
                _uiState.update { 
                    it.copy(
                        capturedImages = sortedImages,
                        shouldLaunchCamera = sortedImages.isEmpty()
                    )
                }
            }
        }
    }

    private fun getNextImageNumber(): Int {
        val baseName = settingsViewModel.generateImageName(_uiState.value.barcodeNumber)
        return if (_uiState.value.capturedImages.isEmpty()) 1
        else {
            _uiState.value.capturedImages
                .mapNotNull { image ->
                    val numberStr = image.fileName
                        .substringAfter("$baseName-")
                        .substringBefore(".")
                    numberStr.toIntOrNull()
                }
                .maxOrNull()?.plus(1) ?: 1
        }
    }

    fun prepareImageCapture(context: Context): Uri? {
        val nextNumber = getNextImageNumber()
        val baseName = settingsViewModel.generateImageName(_uiState.value.barcodeNumber)
        val fileName = "$baseName-$nextNumber.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/ProductScanner"
            )
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        _uiState.update { it.copy(tempImageUri = uri) }
        return uri
    }

    fun handleImageCaptureResult(success: Boolean, context: Context) {
        if (success && _uiState.value.tempImageUri != null) {
            val nextNumber = getNextImageNumber()
            val baseName = settingsViewModel.generateImageName(_uiState.value.barcodeNumber)
            val fileName = "$baseName-$nextNumber.jpg"
            val newImage = CapturedImage(_uiState.value.tempImageUri!!, fileName)
            
            // First update the capturedImages list
            _uiState.update { state ->
                state.copy(
                    capturedImages = (state.capturedImages + newImage).sortedBy { it.fileName },
                    showSuccessMessage = true,
                    tempImageUri = null
                )
            }

            // Then reload images to ensure we have the correct URIs
            viewModelScope.launch {
                delay(500) // Small delay to ensure the file is properly saved
                loadExistingImages(context)
                
                delay(2000)
                _uiState.update { it.copy(showSuccessMessage = false) }
            }
        } else {
            // Delete the temporary file if capture failed or was cancelled
            _uiState.value.tempImageUri?.let { uri ->
                context.contentResolver.delete(uri, null, null)
            }
            _uiState.update { it.copy(tempImageUri = null) }
        }
    }

    fun toggleImageSelection(image: CapturedImage) {
        _uiState.update { state ->
            state.copy(
                selectedImage = if (state.selectedImage?.uri == image.uri) null else image
            )
        }
    }

    fun showDeleteDialog(image: CapturedImage) {
        _uiState.update { it.copy(showDeleteDialog = image) }
    }

    fun hideDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = null) }
    }

    fun deleteImage(context: Context) {
        val imageToDelete = _uiState.value.showDeleteDialog ?: return
        
        // Delete the image file
        context.contentResolver.delete(imageToDelete.uri, null, null)
        
        // Update UI state
        _uiState.update { state ->
            state.copy(
                capturedImages = state.capturedImages.filter { it != imageToDelete },
                showDeleteDialog = null,
                selectedImage = if (state.selectedImage == imageToDelete) null else state.selectedImage
            )
        }

        // Update lastModified in database
        viewModelScope.launch {
            val dao = AppDatabase.getDatabase(context).productDao()
            dao.getProduct(_uiState.value.barcodeNumber).collect { product ->
                if (product != null) {
                    dao.insertOrUpdateProduct(
                        product.copy(lastModified = System.currentTimeMillis())
                    )
                }
            }
        }
    }

    fun showConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun hideConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun clearShouldLaunchCamera() {
        _uiState.update { it.copy(shouldLaunchCamera = false) }
    }
} 