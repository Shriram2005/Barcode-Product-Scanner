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
import com.shriram.barcodeproductscanner.data.Product
import com.shriram.barcodeproductscanner.data.ProductHistoryRepository
import com.shriram.barcodeproductscanner.utils.ImageUtils
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
    val hasExistingImages: Boolean = false,
    val productCode: String? = null, // Product code from CSV mapping
    val showProductCodeNotFoundError: Boolean = false // Show error when product code not found
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

        // Check for product code mapping
        viewModelScope.launch {
            // Wait for settings to load
            delay(300) // Increased delay to ensure settings are properly loaded
            val productCode = if (settingsViewModel.isProductCodeNamingEnabled() && settingsViewModel.isCsvImported()) {
                settingsViewModel.getProductCode(barcodeNumber)
            } else {
                null
            }

            _uiState.update { it.copy(productCode = productCode) }

            // Show error if product code naming is enabled but no mapping found
            if (settingsViewModel.isProductCodeNamingEnabled() && settingsViewModel.isCsvImported() && productCode == null) {
                _uiState.update { it.copy(showProductCodeNotFoundError = true) }
            }
        }

        // Create or update product in history with timestamp
        viewModelScope.launch {
            val product = Product(
                barcode = barcodeNumber,
                lastModified = System.currentTimeMillis()
            )
            ProductHistoryRepository.getInstance(context).insertOrUpdateProduct(product)
        }

        // Check for existing images immediately
        checkForExistingImages(context, barcodeNumber)
    }
    
    private fun checkForExistingImages(context: Context, barcodeNumber: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Checking for existing images for barcode: $barcodeNumber")

                // Get the current naming pattern and folder
                val useProductCode = settingsViewModel.isProductCodeNamingEnabled() && _uiState.value.productCode != null
                val baseName = getCurrentBaseName()
                val folderPath = ImageUtils.getFolderPath(useProductCode)

                // Build query for the specific folder
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(
                    "$baseName%.jpg",  // Any images that start with the base name
                    "%$folderPath%"    // In the appropriate folder
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
                    Log.d(TAG, "Found ${cursor.count} existing images in folder $folderPath with base name $baseName")
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
                val useProductCode = settingsViewModel.isProductCodeNamingEnabled() && _uiState.value.productCode != null
                val baseName = getCurrentBaseName()
                val folderPath = ImageUtils.getFolderPath(useProductCode)
                
                Log.d(TAG, "Loading existing images for barcode: $barcodeNumber, baseName: $baseName, folder: $folderPath")

                // Build query for the specific folder
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(
                    "$baseName%.jpg",  // Any images that start with the base name
                    "%$folderPath%"    // In the appropriate folder
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
                        Log.d(TAG, "Found image: $displayName with URI: $contentUri in folder $folderPath")
                    }

                    // Update hasExistingImages flag
                    val hasImages = images.isNotEmpty()
                    Log.d(TAG, "Total images found in folder $folderPath: ${images.size}")
                    _uiState.update { it.copy(hasExistingImages = hasImages) }

                    // Custom sorting to handle base name and numbered names
                    val sortedImages = images.sortedWith(compareBy { img ->
                        // Extract the number from the filename or use -1 for base name (to sort it first)
                        when {
                            img.fileName == "$baseName.jpg" -> -1 // Base name files sort first
                            img.fileName.startsWith("$baseName-") -> {
                                val numberStr = img.fileName
                                    .substringAfter("$baseName-")
                                    .substringBefore(".")
                                numberStr.toIntOrNull() ?: Int.MAX_VALUE
                            }
                            else -> Int.MAX_VALUE
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

    // Helper method to get the current base name (barcode or product code)
    private fun getCurrentBaseName(): String {
        val uiState = _uiState.value
        val result = if (settingsViewModel.isProductCodeNamingEnabled() && uiState.productCode != null) {
            uiState.productCode
        } else {
            uiState.barcodeNumber
        }
        Log.d(TAG, "Getting base name: $result (useProductCode=${settingsViewModel.isProductCodeNamingEnabled()}, productCode=${uiState.productCode})")
        return result
    }

    private fun getNextImageName(context: Context): String {
        val images = _uiState.value.capturedImages
        val baseName = getCurrentBaseName()
        val baseNamePattern = "$baseName.jpg"
        val useProductCode = settingsViewModel.isProductCodeNamingEnabled() && _uiState.value.productCode != null
        val folderPath = ImageUtils.getFolderPath(useProductCode)
        
        Log.d(TAG, "Finding next image name with base: $baseName in folder: $folderPath, capturedImages count: ${images.size}")

        // Check if we have any images in our list
        if (images.isEmpty()) {
            // If no images in our list, check MediaStore directly
            val baseNameExists = checkIfFileExistsInFolder(context, baseNamePattern, folderPath)
            Log.d(TAG, "No captured images in list. Base name $baseNamePattern exists in MediaStore: $baseNameExists")
            
            return if (baseNameExists) {
                // Base name exists in MediaStore, find the next available number
                val result = findNextAvailableNumberedFilename(context, baseName, folderPath)
                Log.d(TAG, "Found next available numbered filename: $result")
                result
            } else {
                // Base name doesn't exist, we can use it
                Log.d(TAG, "Using base name as is: $baseNamePattern")
                baseNamePattern
            }
        }
        
        // Check if base name exists in our captured images list
        val hasBaseNameImage = images.any { it.fileName == baseNamePattern }
        
        // Also check if it exists in MediaStore in the specific folder
        val baseNameExistsInMediaStore = checkIfFileExistsInFolder(context, baseNamePattern, folderPath)
        
        // If base name doesn't exist anywhere, use it
        if (!hasBaseNameImage && !baseNameExistsInMediaStore) {
            return baseNamePattern
        }

        // Find the highest number currently in use
        var highestNumber = 0
        
        // Check in our captured images list
        val highestInList = images
            .mapNotNull { image ->
                if (image.fileName.startsWith("$baseName-") && image.fileName.endsWith(".jpg")) {
                    val numberStr = image.fileName
                        .substringAfter("$baseName-")
                        .substringBefore(".")
                    numberStr.toIntOrNull()
                } else {
                    null
                }
            }
            .maxOrNull() ?: 0
            
        highestNumber = highestInList
            
        // Also check for files in MediaStore that may not be in our list
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(
            "$baseName-%.jpg",
            "%$folderPath%"
        )
            
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val displayName = cursor.getString(displayNameColumn)
                    
                    if (displayName.startsWith("$baseName-") && displayName.endsWith(".jpg")) {
                        val numberStr = displayName
                            .substringAfter("$baseName-")
                            .substringBefore(".")
                        val number = numberStr.toIntOrNull() ?: continue
                        
                        if (number > highestNumber) {
                            highestNumber = number
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MediaStore for existing numbered files", e)
        }
        
        return "$baseName-${highestNumber + 1}.jpg"
    }
    
    // Helper method to check if a file with the given name already exists in specified folder
    private fun checkIfFileExistsInFolder(context: Context, fileName: String, folderPath: String): Boolean {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(
            fileName,
            "%$folderPath%"
        )
        
        Log.d(TAG, "Checking if file exists: $fileName in folder $folderPath")
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val exists = cursor.count > 0
                if (exists) {
                    Log.d(TAG, "File $fileName already exists in MediaStore in folder $folderPath")
                } else {
                    Log.d(TAG, "File $fileName does not exist in MediaStore in folder $folderPath")
                }
                return exists
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if file exists: $fileName in folder $folderPath", e)
            // If there's an error checking, assume it doesn't exist
            return false
        }
        
        return false
    }

    // Helper method to find the next available numbered filename in a specific folder
    private fun findNextAvailableNumberedFilename(context: Context, baseName: String, folderPath: String): String {
        var fileNumber = 1
        var fileName = "$baseName-$fileNumber.jpg"
        
        Log.d(TAG, "Finding next available filename starting with $baseName- in folder $folderPath")
        
        while (checkIfFileExistsInFolder(context, fileName, folderPath)) {
            Log.d(TAG, "$fileName exists in folder $folderPath, trying next number")
            fileNumber++
            fileName = "$baseName-$fileNumber.jpg"
            
            if (fileNumber > 100) {
                // Safety check to prevent infinite loop
                Log.w(TAG, "Stopped search after 100 attempts to find unique filename")
                // Generate a truly unique name using timestamp
                fileName = "$baseName-${System.currentTimeMillis()}.jpg"
                break
            }
        }
        
        Log.d(TAG, "Selected filename: $fileName for folder $folderPath")
        return fileName
    }

    fun prepareImageCapture(context: Context): Uri? {
        try {
            // Get the next image name based on the format
            var fileName = getNextImageName(context)
            val useProductCode = settingsViewModel.isProductCodeNamingEnabled() && _uiState.value.productCode != null
            val folderPath = ImageUtils.getFolderPath(useProductCode)
            
            Log.d(TAG, "Preparing image capture with filename: $fileName in folder: $folderPath")

            // Check if a file with this name already exists in MediaStore (double check)
            var fileExists = checkIfFileExistsInFolder(context, fileName, folderPath)
            var attempts = 0
            val maxAttempts = 10
            
            // If file exists, try with incrementing numbers until we find a unique name
            while (fileExists && attempts < maxAttempts) {
                attempts++
                
                // Extract base name and number from fileName
                val baseName = getCurrentBaseName()
                var fileNumber = 1
                
                if (fileName.contains("-")) {
                    val numberStr = fileName.substringAfter("-").substringBefore(".")
                    fileNumber = numberStr.toIntOrNull() ?: 1
                }
                
                // Try a new number
                fileName = if (fileName == "$baseName.jpg") {
                    "$baseName-1.jpg"
                } else {
                    "$baseName-${fileNumber + attempts}.jpg"
                }
                
                Log.d(TAG, "File exists, trying alternative name: $fileName in folder $folderPath")
                fileExists = checkIfFileExistsInFolder(context, fileName, folderPath)
            }
            
            if (attempts >= maxAttempts) {
                // Last resort: use timestamp for truly unique name
                val baseName = getCurrentBaseName()
                fileName = "$baseName-${System.currentTimeMillis()}.jpg"
                Log.d(TAG, "Using timestamp-based filename as last resort: $fileName")
            }
            
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    folderPath
                )
            }

            val uri = try {
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                // If we hit a constraint exception, use timestamp for truly unique name
                Log.e(TAG, "SQLiteConstraintException when creating file: $fileName in folder $folderPath", e)
                val baseName = getCurrentBaseName()
                val timestampFileName = "$baseName-${System.currentTimeMillis()}.jpg"
                Log.d(TAG, "Using timestamp-based filename after error: $timestampFileName")
                
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timestampFileName)
                try {
                    val newUri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )
                    
                    if (newUri != null) {
                        // Update the filename to match the timestamp filename
                        fileName = timestampFileName
                    }
                    newUri
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to create file even with timestamp name", e2)
                    null
                }
            }

            if (uri != null) {
                Log.d(TAG, "Created temp URI: $uri in folder $folderPath")
                _uiState.update { it.copy(tempImageUri = uri, tempImageFileName = fileName) }
                return uri
            } else {
                Log.e(TAG, "Failed to create URI for image capture in folder $folderPath")
                return null
            }
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
                    val baseName = getCurrentBaseName()
                    state.copy(
                        capturedImages = (state.capturedImages + newImage).sortedWith(
                            compareBy { img ->
                                when {
                                    img.fileName == "$baseName.jpg" -> -1 // Base name files sort first
                                    img.fileName.startsWith("$baseName-") -> {
                                        val numberStr = img.fileName
                                            .substringAfter("$baseName-")
                                            .substringBefore(".")
                                        numberStr.toIntOrNull() ?: Int.MAX_VALUE
                                    }
                                    else -> Int.MAX_VALUE
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
            val useProductCode = settingsViewModel.isProductCodeNamingEnabled() && _uiState.value.productCode != null
            val folderPath = ImageUtils.getFolderPath(useProductCode)

            try {
                uris.forEach { sourceUri ->
                    // Generate a unique filename for each selected image
                    val fileName = getNextImageName(context)

                    // Create content values for the new image
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            folderPath
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

        // Update lastModified in product history
        viewModelScope.launch {
            val product = Product(
                barcode = _uiState.value.barcodeNumber,
                lastModified = System.currentTimeMillis()
            )
            ProductHistoryRepository.getInstance(context).insertOrUpdateProduct(product)
        }
    }
    
    private fun renumberImagesAfterDeletion(context: Context) {
        val images = _uiState.value.capturedImages.toMutableList()

        // Use current base name (barcode or product code)
        val baseName = getCurrentBaseName()
        val useProductCode = settingsViewModel.isProductCodeNamingEnabled() && _uiState.value.productCode != null
        val folderPath = ImageUtils.getFolderPath(useProductCode)

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
                            folderPath
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

    fun dismissProductCodeNotFoundError() {
        _uiState.update { it.copy(showProductCodeNotFoundError = false) }
    }
}