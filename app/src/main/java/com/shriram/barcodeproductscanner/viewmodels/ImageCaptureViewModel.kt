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
    val shouldLaunchCamera: Boolean = false,
    val hasExistingImages: Boolean = false,
    val showProductNameDialog: Boolean = false,
    val productName: String = ""
)

class ImageCaptureViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageCaptureUiState())
    val uiState: StateFlow<ImageCaptureUiState> = _uiState.asStateFlow()
    private val settingsViewModel: SettingsViewModel = SettingsViewModel()
    
    // Flag to track if we're prompting for product name
    private var shouldPromptForProductName = false

    fun initialize(barcodeNumber: String, context: Context) {
        _uiState.update { it.copy(barcodeNumber = barcodeNumber) }
        // Load settings and product data
        settingsViewModel.loadSettings(context)
        settingsViewModel.loadProductData(context, barcodeNumber)

        // Load existing product name if available
        viewModelScope.launch {
            val database = AppDatabase.getDatabase(context)
            database.productDao().getProduct(barcodeNumber).collect { product ->
                if (product != null) {
                    _uiState.update { it.copy(productName = product.productName) }
                }
            }
            
            // Create or update product in database with timestamp
            val product = Product(
                barcode = barcodeNumber,
                lastModified = System.currentTimeMillis(),
                // Keep existing product name if any
                productName = _uiState.value.productName
            )
            database.productDao().insertOrUpdateProduct(product)
        }
        
        // Check for existing images immediately
        checkForExistingImages(context, barcodeNumber)
    }
    
    private fun checkForExistingImages(context: Context, barcodeNumber: String) {
        viewModelScope.launch {
            // First, check if we need to get the product name from the database
            val database = AppDatabase.getDatabase(context)
            var productName = ""
            
            // Load the product info first to get the name if it exists
            database.productDao().getProduct(barcodeNumber).collect { product ->
                if (product != null && product.productName.isNotBlank()) {
                    productName = product.productName
                    _uiState.update { it.copy(productName = productName) }
                }
            }
            
            // Generate the potential base names based on settings
            val possibleBaseNames = mutableListOf<String>()
            
            // Always include barcode as a possible name
            possibleBaseNames.add(barcodeNumber)
            
            // If we have a product name and settings include it
            if (productName.isNotBlank() && settingsViewModel.uiState.value.imagingNamingFormat.includeProductName) {
                if (settingsViewModel.uiState.value.imagingNamingFormat.includeBarcode) {
                    // Both enabled: "barcode-productname"
                    possibleBaseNames.add("$barcodeNumber-$productName")
                } else {
                    // Only product name: "productname"
                    possibleBaseNames.add(productName)
                }
            }
            
            // Build the query to search for all possible naming patterns
            val selectionBuilder = StringBuilder()
            val selectionArgs = mutableListOf<String>()
            
            possibleBaseNames.forEachIndexed { index, baseName ->
                if (index > 0) selectionBuilder.append(" OR ")
                selectionBuilder.append("${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?")
                selectionArgs.add("$baseName.jpg") // Exact match
                selectionArgs.add("$baseName-%.jpg") // With number suffix
            }
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selectionBuilder.toString(),
                selectionArgs.toTypedArray(),
                null
            )?.use { cursor ->
                val hasImages = cursor.count > 0
                _uiState.update { it.copy(hasExistingImages = hasImages) }
                
                // Close the cursor properly
                cursor.close()
            }
        }
    }

    fun loadExistingImages(context: Context) {
        viewModelScope.launch {
            val barcodeNumber = _uiState.value.barcodeNumber
            val productName = _uiState.value.productName
            
            // Generate the potential base names based on settings
            val possibleBaseNames = mutableListOf<String>()
            
            // First, what name format are we using currently?
            // Note: currentBaseName logic moved to possibleBaseNames generation below
            
            // Add all possible naming patterns we might have used
            possibleBaseNames.add(barcodeNumber) // Always include barcode
            if (productName.isNotBlank()) {
                possibleBaseNames.add(productName) // Just product name
                possibleBaseNames.add("$barcodeNumber-$productName") // Combined
            }
            
            // Build the query to search for all possible naming patterns
            val selectionBuilder = StringBuilder()
            val selectionArgs = mutableListOf<String>()
            
            possibleBaseNames.forEachIndexed { index, baseName ->
                if (index > 0) selectionBuilder.append(" OR ")
                selectionBuilder.append("${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?")
                selectionArgs.add("$baseName.jpg") // Exact match
                selectionArgs.add("$baseName-%.jpg") // With number suffix
            }
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selectionBuilder.toString(),
                selectionArgs.toTypedArray(),
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
                
                // Update hasExistingImages flag
                val hasImages = images.isNotEmpty()
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
        }
    }

    private fun getNextImageName(): String {
        val barcodeNumber = _uiState.value.barcodeNumber
        val productName = _uiState.value.productName
        val images = _uiState.value.capturedImages
        
        // Get the current settings
        val includeBarcode = settingsViewModel.uiState.value.imagingNamingFormat.includeBarcode
        val includeProductName = settingsViewModel.uiState.value.imagingNamingFormat.includeProductName
        
        // Generate base name using settings
        val baseName = when {
            // Case 1: Only product name is enabled and we have a product name
            includeProductName && !includeBarcode && productName.isNotBlank() -> {
                productName // Use only product name
            }
            // Case 2: Both enabled or only barcode enabled
            else -> {
                settingsViewModel.generateImageName(barcodeNumber, productName)
            }
        }
        
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
        // Get the next image name based on the format
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
                productName = _uiState.value.productName,
                lastModified = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).productDao().insertOrUpdateProduct(product)
        }
    }
    
    private fun renumberImagesAfterDeletion(context: Context) {
        val images = _uiState.value.capturedImages.toMutableList()
        val productName = _uiState.value.productName
        
        // Generate base name using settings
        val baseName = if (settingsViewModel.uiState.value.imagingNamingFormat.includeProductName && productName.isNotBlank()) {
            settingsViewModel.generateImageName(_uiState.value.barcodeNumber, productName)
        } else {
            // If product name setting is off or no product name provided, just use barcode
            _uiState.value.barcodeNumber
        }
        
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

    // Checks if a product name is needed based on settings
    fun needsProductName(): Boolean {
        return settingsViewModel.uiState.value.imagingNamingFormat.includeProductName
    }
    
    fun showConfirmDialog() {
        // Check if we need to prompt for product name based on settings
        shouldPromptForProductName = needsProductName()
        
        if (shouldPromptForProductName) {
            // Show product name dialog first
            _uiState.update { it.copy(showProductNameDialog = true) }
        } else {
            // No dialog, just finish and return
            finishAndSaveImages()
        }
    }
    
    private fun finishAndSaveImages() {
        // This method is just for signaling we're done - actual saving happens in handleImageCaptureResult
        // For future needs, we could add more functionality here
    }

    fun hideConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }
    
    fun showProductNameDialog() {
        _uiState.update { it.copy(showProductNameDialog = true) }
    }
    
    fun hideProductNameDialog() {
        _uiState.update { it.copy(showProductNameDialog = false) }
    }
    
    fun submitProductName(productName: String, context: Context) {
        // Update the UI state
        _uiState.update { it.copy(productName = productName, showProductNameDialog = false) }

        // Save product name to database
        viewModelScope.launch {
            val product = Product(
                barcode = _uiState.value.barcodeNumber,
                productName = productName,
                lastModified = System.currentTimeMillis()
            )
            AppDatabase.getDatabase(context).productDao().insertOrUpdateProduct(product)

            // Rename existing images to include the product name
            renameExistingImagesWithProductName(context, productName)
        }

        // Finish and return without showing confirmation dialog
        finishAndSaveImages()
    }

    private suspend fun renameExistingImagesWithProductName(context: Context, productName: String) {
        val currentImages = _uiState.value.capturedImages.toList()
        if (currentImages.isEmpty()) return

        val barcodeNumber = _uiState.value.barcodeNumber

        // Generate new base name with product name included
        val newBaseName = settingsViewModel.generateImageName(barcodeNumber, productName)

        // Create a list to store renamed images
        val renamedImages = mutableListOf<CapturedImage>()

        try {
            currentImages.forEachIndexed { _, image ->
                val currentFileName = image.fileName

                // Determine the new file name based on the current naming pattern
                val newFileName = when {
                    // If it's the base image (just barcode.jpg)
                    currentFileName == "$barcodeNumber.jpg" -> {
                        if (settingsViewModel.uiState.value.imagingNamingFormat.includeBarcode) {
                            "$newBaseName.jpg"
                        } else {
                            // Only product name enabled
                            "$productName.jpg"
                        }
                    }
                    // If it's a numbered image (barcode-1.jpg, barcode-2.jpg, etc.)
                    currentFileName.startsWith("$barcodeNumber-") && currentFileName.endsWith(".jpg") -> {
                        val numberPart = currentFileName.substringAfter("$barcodeNumber-").substringBefore(".jpg")
                        if (settingsViewModel.uiState.value.imagingNamingFormat.includeBarcode) {
                            "$newBaseName-$numberPart.jpg"
                        } else {
                            // Only product name enabled
                            "$productName-$numberPart.jpg"
                        }
                    }
                    // If it already has the correct naming, keep it
                    else -> currentFileName
                }

                // Only rename if the name actually changes
                if (newFileName != currentFileName) {
                    // Create new file with correct name
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/ProductScanner"
                        )
                    }

                    // Insert new file
                    val newUri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                    )

                    if (newUri != null) {
                        // Copy content from old file to new file
                        context.contentResolver.openInputStream(image.uri)?.use { input ->
                            context.contentResolver.openOutputStream(newUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Delete the old file
                        context.contentResolver.delete(image.uri, null, null)

                        // Add renamed image to list
                        renamedImages.add(CapturedImage(newUri, newFileName))
                    } else {
                        // If renaming failed, keep the original
                        renamedImages.add(image)
                    }
                } else {
                    // No rename needed, keep original
                    renamedImages.add(image)
                }
            }

            // Update the UI state with renamed images
            _uiState.update { it.copy(capturedImages = renamedImages) }

            // Reload images to ensure we have the correct URIs and file names
            delay(500)
            loadExistingImages(context)

        } catch (e: Exception) {
            // If renaming fails, just keep the original images
            // The product name is still saved to the database
        }
    }
}