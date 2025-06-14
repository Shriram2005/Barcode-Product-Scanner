package com.shriram.barcodeproductscanner.viewmodels

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
    val tempImageFileName: String? = null, // Store the filename for consistency
    val shouldLaunchCamera: Boolean = false,
    val hasExistingImages: Boolean = false
)

class ImageCaptureViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageCaptureUiState())
    val uiState: StateFlow<ImageCaptureUiState> = _uiState.asStateFlow()
    private val settingsViewModel: SettingsViewModel = SettingsViewModel()

    companion object {
        private const val TAG = "ImageCaptureViewModel"
    }

    // Flag to track if we're prompting for product name


    fun initialize(barcodeNumber: String, context: Context) {
        _uiState.update { it.copy(barcodeNumber = barcodeNumber) }
        // Load settings and product data
        settingsViewModel.loadSettings(context)
        settingsViewModel.loadProductData(context, barcodeNumber)

        // Create or update product in database with timestamp
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(context)
            val product = Product(
                barcode = barcodeNumber,
                lastModified = System.currentTimeMillis()
            )
            database.productDao().insertOrUpdateProduct(product)
        }
        
        // Check for existing images immediately
        checkForExistingImages(context, barcodeNumber)
    }
    
    private fun checkForExistingImages(context: Context, barcodeNumber: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Checking for existing images for barcode: $barcodeNumber")

                // Build the query to search for barcode-based naming patterns in ProductScanner folder
                val selection = "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?) AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(
                    "$barcodeNumber.jpg", // Exact match
                    "$barcodeNumber-%.jpg", // With number suffix
                    "%ProductScanner%" // In ProductScanner folder
                )

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
                )

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val hasImages = cursor.count > 0
                    Log.d(TAG, "Found ${cursor.count} existing images")
                    _uiState.update { it.copy(hasExistingImages = hasImages) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for existing images", e)
            }
        }
    }

    fun loadExistingImages(context: Context) {
        viewModelScope.launch {
            try {
                val barcodeNumber = _uiState.value.barcodeNumber
                Log.d(TAG, "Loading existing images for barcode: $barcodeNumber")

                // Build the query to search for barcode-based naming patterns in ProductScanner folder
                val selection = "(${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?) AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(
                    "$barcodeNumber.jpg", // Exact match
                    "$barcodeNumber-%.jpg", // With number suffix
                    "%ProductScanner%" // In ProductScanner folder
                )

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH
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
                        Log.d(TAG, "Found image: $displayName with URI: $contentUri")
                    }

                    // Update hasExistingImages flag
                    val hasImages = images.isNotEmpty()
                    Log.d(TAG, "Total images found: ${images.size}")
                    _uiState.update { it.copy(hasExistingImages = hasImages) }

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
                            // Only set shouldLaunchCamera to true if there are no images and we didn't already know about existing images
                            shouldLaunchCamera = sortedImages.isEmpty() && !it.hasExistingImages
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading existing images", e)
            }
        }
    }

    private fun getNextImageName(): String {
        val barcodeNumber = _uiState.value.barcodeNumber
        val images = _uiState.value.capturedImages

        // Use barcode as base name
        val baseName = barcodeNumber

        // If no images exist, use just the base name
        if (images.isEmpty()) {
            return "$baseName.jpg"
        }

        // Check if base image exists (without number suffix)
        val baseNamePattern = "$baseName.jpg"
        val hasBaseNameImage = images.any { it.fileName == baseNamePattern }
        if (!hasBaseNameImage) {
            return baseNamePattern
        }

        // Otherwise, find the highest number and increment
        val highestNumber = images
            .mapNotNull { image ->
                if (image.fileName == baseNamePattern) {
                    null // Skip the base image
                } else if (image.fileName.startsWith("$baseName-") && image.fileName.endsWith(".jpg")) {
                    val numberStr = image.fileName
                        .substringAfter("$baseName-")
                        .substringBefore(".")
                    numberStr.toIntOrNull()
                } else {
                    null // Different naming pattern, skip
                }
            }
            .maxOrNull() ?: 0

        return "$baseName-${highestNumber + 1}.jpg"
    }

    fun prepareImageCapture(context: Context): Uri? {
        try {
            // Get the next image name based on the format
            val fileName = getNextImageName()
            Log.d(TAG, "Preparing image capture with filename: $fileName")

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

            Log.d(TAG, "Created temp URI: $uri")
            _uiState.update { it.copy(tempImageUri = uri, tempImageFileName = fileName) }
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing image capture", e)
            return null
        }
    }

    fun handleImageCaptureResult(success: Boolean, context: Context) {
        try {
            Log.d(TAG, "Handling image capture result: success=$success")

            if (success && _uiState.value.tempImageUri != null && _uiState.value.tempImageFileName != null) {
                val tempUri = _uiState.value.tempImageUri!!
                val fileName = _uiState.value.tempImageFileName!! // Use the stored filename for consistency
                Log.d(TAG, "Image captured successfully with filename: $fileName, URI: $tempUri")

                val newImage = CapturedImage(tempUri, fileName)

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
                        tempImageUri = null,
                        tempImageFileName = null
                    )
                }

                // Then reload images to ensure we have the correct URIs
                viewModelScope.launch {
                    delay(1000) // Increased delay to ensure the file is properly saved
                    Log.d(TAG, "Reloading images after capture")
                    loadExistingImages(context)

                    delay(2000)
                    _uiState.update { it.copy(showSuccessMessage = false) }
                }
            } else {
                Log.w(TAG, "Image capture failed or was cancelled")
                // Delete the temporary file if capture failed or was cancelled
                _uiState.value.tempImageUri?.let { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                        Log.d(TAG, "Deleted temporary image file")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting temporary image file", e)
                    }
                }
                _uiState.update { it.copy(tempImageUri = null, tempImageFileName = null) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling image capture result", e)
            // Clean up on error
            _uiState.value.tempImageUri?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (deleteError: Exception) {
                    Log.e(TAG, "Error deleting temp file during error cleanup", deleteError)
                }
            }
            _uiState.update { it.copy(tempImageUri = null, tempImageFileName = null) }
        }
    }

    fun handleGalleryImagesSelected(uris: List<Uri>, context: Context) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val newImages = mutableListOf<CapturedImage>()

            try {
                uris.forEach { sourceUri ->
                    // Generate a unique filename for each selected image
                    val fileName = getNextImageName()

                    // Create content values for the new image
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/ProductScanner"
                        )
                    }

                    // Insert the new image into MediaStore
                    val newUri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )

                    if (newUri != null) {
                        // Copy the image content from gallery to our app folder
                        context.contentResolver.openInputStream(sourceUri)?.use { input ->
                            context.contentResolver.openOutputStream(newUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Add to our list
                        newImages.add(CapturedImage(newUri, fileName))

                        // Update the captured images list after each image to get correct next name
                        _uiState.update { state ->
                            state.copy(capturedImages = state.capturedImages + CapturedImage(newUri, fileName))
                        }
                    }
                }

                // Show success message
                _uiState.update { it.copy(showSuccessMessage = true) }

                // Reload images to ensure proper sorting and URIs
                delay(500)
                loadExistingImages(context)

                // Hide success message after delay
                delay(2000)
                _uiState.update { it.copy(showSuccessMessage = false) }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling gallery images selection", e)
                // Handle error - could show error message to user
                // For now, just ensure we don't crash
            }
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

        // Use barcode as base name
        val baseName = _uiState.value.barcodeNumber
        
        // If there are no images or only one image (which would be the base name), no need to renumber
        if (images.size <= 1) return
        
        // If we still have the base image (without suffix) then we only need to renumber the others
        val baseFileName = "$baseName.jpg"
        val hasBaseImage = images.any { it.fileName == baseFileName }
        
        viewModelScope.launch {
            // Create a list of properly named images with their current URIs
            val renamedImages = mutableListOf<Pair<CapturedImage, String>>()
            
            if (hasBaseImage) {
                // Keep the base image as is
                val baseImage = images.first { it.fileName == baseFileName }
                images.remove(baseImage)
                renamedImages.add(Pair(baseImage, baseImage.fileName))
            }
            
            // Rename the rest with sequential numbers
            images.forEachIndexed { index, image ->
                val newName = if (index == 0 && !hasBaseImage) {
                    baseFileName
                } else {
                    "$baseName-${if (hasBaseImage) index else index - 1}.jpg"
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
        // No dialog needed, just finish and return
        finishAndSaveImages()
    }

    private fun finishAndSaveImages() {
        // This method is just for signaling we're done - actual saving happens in handleImageCaptureResult
        // For future needs, we could add more functionality here
    }

    // Debug method to manually refresh images
    fun debugRefreshImages(context: Context) {
        Log.d(TAG, "Debug: Manually refreshing images")
        loadExistingImages(context)
    }

    fun hideConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }
}