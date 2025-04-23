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
            val barcodeNumber = _uiState.value.barcodeNumber
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            
            // Search for both the barcode name and barcode-N pattern
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(
                "$barcodeNumber.jpg",  // Exact match for base name
                "$barcodeNumber-%.jpg" // Pattern match for numbered images
            )
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

                // Custom sorting to handle base name and numbered names
                val sortedImages = images.sortedWith(compareBy { img ->
                    // Extract the number from the filename or use -1 for base name (to sort it first)
                    if (img.fileName == "$barcodeNumber.jpg") {
                        -1 // Make the base name file sort first
                    } else {
                        val numberStr = img.fileName
                            .substringAfter("$barcodeNumber-")
                            .substringBefore(".")
                        numberStr.toIntOrNull() ?: Int.MAX_VALUE
                    }
                })

                _uiState.update {
                    it.copy(
                        capturedImages = sortedImages,
                        shouldLaunchCamera = sortedImages.isEmpty()
                    )
                }
            }
        }
    }

    private fun getNextImageName(): String {
        val barcodeNumber = _uiState.value.barcodeNumber
        val images = _uiState.value.capturedImages
        
        // If no images exist, use just the barcode number
        if (images.isEmpty()) {
            return "$barcodeNumber.jpg"
        }
        
        // If the first image should be just the barcode but doesn't exist yet
        val hasBaseNameImage = images.any { it.fileName == "$barcodeNumber.jpg" }
        if (!hasBaseNameImage) {
            return "$barcodeNumber.jpg"
        }
        
        // Otherwise, find the highest number and increment
        val highestNumber = images
            .mapNotNull { image ->
                if (image.fileName == "$barcodeNumber.jpg") {
                    null // Skip the base image
                } else {
                    val numberStr = image.fileName
                        .substringAfter("$barcodeNumber-")
                        .substringBefore(".")
                    numberStr.toIntOrNull()
                }
            }
            .maxOrNull() ?: 0
            
        return "$barcodeNumber-${highestNumber + 1}.jpg"
    }

    fun prepareImageCapture(context: Context): Uri? {
        val fileName = getNextImageName()

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
            val fileName = getNextImageName()
            val newImage = CapturedImage(_uiState.value.tempImageUri!!, fileName)
            
            // First update the capturedImages list
            _uiState.update { state ->
                state.copy(
                    capturedImages = (state.capturedImages + newImage).sortedWith(
                        compareBy { img ->
                            val barcodeNumber = state.barcodeNumber
                            if (img.fileName == "$barcodeNumber.jpg") {
                                -1 // Make the base name file sort first
                            } else {
                                val numberStr = img.fileName
                                    .substringAfter("$barcodeNumber-")
                                    .substringBefore(".")
                                numberStr.toIntOrNull() ?: Int.MAX_VALUE
                            }
                        }
                    ),
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
        
        // Renumber the images if needed
        renumberImagesAfterDeletion(context)

        // Update lastModified in database
        viewModelScope.launch {
            val product = Product(
                barcode = _uiState.value.barcodeNumber,
                lastModified = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).productDao().insertOrUpdateProduct(product)
        }
    }
    
    private fun renumberImagesAfterDeletion(context: Context) {
        val images = _uiState.value.capturedImages.toMutableList()
        val barcodeNumber = _uiState.value.barcodeNumber
        
        // If there are no images or only one image (which would be the base name), no need to renumber
        if (images.size <= 1) return
        
        // If we still have the base image (without suffix) then we only need to renumber the others
        val hasBaseImage = images.any { it.fileName == "$barcodeNumber.jpg" }
        
        viewModelScope.launch {
            // Create a list of properly named images with their current URIs
            val renamedImages = mutableListOf<Pair<CapturedImage, String>>()
            
            if (hasBaseImage) {
                // Keep the base image as is
                val baseImage = images.first { it.fileName == "$barcodeNumber.jpg" }
                images.remove(baseImage)
                renamedImages.add(Pair(baseImage, baseImage.fileName))
            }
            
            // Rename the rest with sequential numbers
            images.forEachIndexed { index, image ->
                val newName = if (index == 0 && !hasBaseImage) {
                    "$barcodeNumber.jpg"
                } else {
                    "$barcodeNumber-${if (hasBaseImage) index else index - 1}.jpg"
                }
                renamedImages.add(Pair(image, newName))
            }
            
            // Apply the renames if needed
            renamedImages.forEach { (image, newName) ->
                if (image.fileName != newName) {
                    // Create a new file with the correct name
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/ProductScanner"
                        )
                    }
                    
                    // Copy the content from old URI to new URI
                    val newUri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    
                    if (newUri != null) {
                        context.contentResolver.openInputStream(image.uri)?.use { input ->
                            context.contentResolver.openOutputStream(newUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Delete the old file
                        context.contentResolver.delete(image.uri, null, null)
                    }
                }
            }
            
            // Reload images to reflect the changes
            delay(500)
            loadExistingImages(context)
        }
    }

    fun clearShouldLaunchCamera() {
        _uiState.update { it.copy(shouldLaunchCamera = false) }
    }

    fun showConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun hideConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }
}