package com.shriram.barcodeproductscanner.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush.Companion.verticalGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shriram.barcodeproductscanner.R
import com.shriram.barcodeproductscanner.custom_composable.bounceClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CapturedImage(
    val uri: Uri,
    val fileName: String
)

private fun copyBarcodeToClipboard(context: Context, barcodeNumber: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Barcode", barcodeNumber)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.barcode_copied), Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCaptureScreen(
    barcodeNumber: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var showSuccessMessage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var capturedImages by remember { mutableStateOf(listOf<CapturedImage>()) }
    var showDeleteDialog by remember { mutableStateOf<CapturedImage?>(null) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImage by remember { mutableStateOf<CapturedImage?>(null) }

    LaunchedEffect(Unit) {
        // Load existing images for this barcode
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("$barcodeNumber-%")
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
            capturedImages = images.sortedBy { it.fileName }
        }
    }

    // Function to get the next image number
    fun getNextImageNumber(): Int {
        if (capturedImages.isEmpty()) return 1

        return capturedImages
            .mapNotNull { image ->
                val numberStr = image.fileName.substringAfterLast("-")
                    .substringBefore(".")
                numberStr.toIntOrNull()
            }
            .maxOrNull()?.plus(1) ?: 1
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempImageUri != null) {
            val fileName = "$barcodeNumber-${getNextImageNumber()}.jpg"
            capturedImages = (capturedImages + CapturedImage(tempImageUri!!, fileName))
                .sortedBy { it.fileName }
            scope.launch {
                showSuccessMessage = true
                delay(2000)
                showSuccessMessage = false
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.image_save_failed, "Camera returned no image"),
                Toast.LENGTH_SHORT
            ).show()
        }
        tempImageUri = null
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_image)) },
            text = { Text(stringResource(R.string.delete_confirmation)) },
            confirmButton = {
                Button(
                    onClick = {
                        val imageToDelete = showDeleteDialog
                        if (imageToDelete != null) {
                            context.contentResolver.delete(imageToDelete.uri, null, null)
                            capturedImages = capturedImages.filter { it != imageToDelete }
                        }
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.finish_capturing)) },
            text = { Text(stringResource(R.string.finish_capturing_message, capturedImages.size)) },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.yes_finish))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.continue_capturing))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.product_images))
                        Text(
                            text = stringResource(R.string.barcode_label, barcodeNumber),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .clickable { copyBarcodeToClipboard(context, barcodeNumber) }
                                .padding(vertical = 4.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (capturedImages.isNotEmpty()) {
                            showConfirmDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Main Content Container
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Preview Box with fixed size
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.small)
                                .border(
                                    2.dp,
                                    Color.White.copy(alpha = 0.5f),
                                    MaterialTheme.shapes.small
                                )
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            // Show selected image, latest image, or placeholder
                            if (selectedImage != null) {
                                AsyncImage(
                                    model = selectedImage!!.uri,
                                    contentDescription = "Selected image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (capturedImages.isNotEmpty()) {
                                AsyncImage(
                                    model = capturedImages.last().uri,
                                    contentDescription = "Latest captured image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }

                    // Bottom Panel with Controls and Images
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.8f)
                                    ),
                                    startY = 0f,
                                    endY = 100f
                                )
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Captured Images Horizontal List
                        if (capturedImages.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    items = capturedImages,
                                    key = { it.uri.toString() }
                                ) { image ->
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .border(
                                                1.dp,
                                                if (selectedImage?.uri == image.uri)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                                else MaterialTheme.colorScheme.primary,
                                                MaterialTheme.shapes.small
                                            )
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                selectedImage =
                                                    if (selectedImage?.uri == image.uri) null else image
                                            }
                                    ) {
                                        AsyncImage(
                                            model = image.uri,
                                            contentDescription = "Captured image",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        IconButton(
                                            onClick = { showDeleteDialog = image },
                                            modifier = Modifier
                                                .bounceClick()
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.4f),
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .padding(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete image",
                                                tint = Color.White,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Camera Controls
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            // Image count on the left
                            Text(
                                text = stringResource(R.string.images_count, capturedImages.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )

                            // Center the camera button
                            FloatingActionButton(
                                onClick = {
                                    val nextNumber = getNextImageNumber()
                                    val fileName = "$barcodeNumber-$nextNumber.jpg"

                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                        put(
                                            MediaStore.MediaColumns.RELATIVE_PATH,
                                            Environment.DIRECTORY_PICTURES + "/ProductScanner"
                                        )
                                    }

                                    tempImageUri = context.contentResolver.insert(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        contentValues
                                    )

                                    tempImageUri?.let { uri ->
                                        cameraLauncher.launch(uri)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(64.dp)
                                    .align(Alignment.Center)
                                    .bounceClick()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = stringResource(R.string.take_photo),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            // Finish button on the right
                            if (capturedImages.isNotEmpty()) {
                                Button(
                                    onClick = { showConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(start = 16.dp)
                                        .bounceClick()
                                ) {
                                    Text(
                                        text = stringResource(R.string.finish),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Success Message Overlay
                AnimatedVisibility(
                    visible = showSuccessMessage,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Text(stringResource(R.string.image_saved))
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun ImageCaptureScreenPreview() {
    ImageCaptureScreen(
        barcodeNumber = "1234567890",
        onNavigateBack = {}
    )
} 