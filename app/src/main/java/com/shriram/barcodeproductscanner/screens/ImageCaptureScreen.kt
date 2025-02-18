package com.shriram.barcodeproductscanner.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CapturedImage(
    val uri: Uri,
    val fileName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCaptureScreen(
    barcodeNumber: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var capturedImages by remember { mutableStateOf(listOf<CapturedImage>()) }
    var showDeleteDialog by remember { mutableStateOf<CapturedImage?>(null) }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

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
                // Extract number from filename (e.g., "barcode-1.jpg" -> 1)
                val numberStr = image.fileName.substringAfterLast("-")
                    .substringBefore(".")
                numberStr.toIntOrNull()
            }
            .maxOrNull()?.plus(1) ?: 1
    }

    fun copyBarcodeToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Barcode", barcodeNumber)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Barcode copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Image") },
            text = { Text("Are you sure you want to delete this image?") },
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
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Finish Capturing?") },
            text = { Text("You have captured ${capturedImages.size} images. Do you want to finish?") },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    onNavigateBack()
                }) {
                    Text("Yes, Finish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Continue Capturing")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Product Images")
                        Text(
                            text = "Barcode: $barcodeNumber",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .clickable { copyBarcodeToClipboard() }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Camera Preview with Captured Images
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black)
            ) {
                // Main Camera Preview Container
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Camera Preview Box with fixed size
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.90f) // Take 90% of screen width
                            .aspectRatio(1f) // Keep it square
                            .clip(MaterialTheme.shapes.small)
                            .border(
                                2.dp,
                                Color.White.copy(alpha = 0.5f),
                                MaterialTheme.shapes.small
                            )
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        // Square camera preview
                        AndroidView(
                            factory = { context ->
                                PreviewView(context).apply {
                                    layoutParams = android.view.ViewGroup.LayoutParams(
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.small)
                        ) { view ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder()
                                    .setTargetResolution(android.util.Size(2000, 2000))
                                    .build()
                                preview.setSurfaceProvider(view.surfaceProvider)

                                imageCapture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                    .setTargetResolution(android.util.Size(2000, 2000))
                                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                                    .setJpegQuality(100)
                                    .build()

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageCapture
                                    )
                                } catch (e: Exception) {
                                    Log.e("ImageCaptureScreen", "Use case binding failed", e)
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    }
                }


                // Bottom Panel with Controls and Images
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 0f,
                                endY = 300f
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Captured Images Horizontal List
                    if (capturedImages.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
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
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.shapes.small
                                        )
                                        .clip(MaterialTheme.shapes.small)
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
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(
                                                Color.Black.copy(alpha = 0.7f),
                                                MaterialTheme.shapes.small
                                            )
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete image",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Camera Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${capturedImages.size} images",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    val imageCapture = imageCapture ?: return@FloatingActionButton
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

                                    val outputOptions = ImageCapture.OutputFileOptions
                                        .Builder(
                                            context.contentResolver,
                                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                            contentValues
                                        )
                                        .setMetadata(
                                            ImageCapture.Metadata().apply {
                                                isReversedHorizontal = false
                                            }
                                        )
                                        .build()

                                    imageCapture.takePicture(
                                        outputOptions,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                output.savedUri?.let { uri ->
                                                    capturedImages =
                                                        (capturedImages + CapturedImage(
                                                            uri,
                                                            fileName
                                                        ))
                                                            .sortedBy { it.fileName }
                                                }
                                                scope.launch {
                                                    showSuccessMessage = true
                                                    delay(2000)
                                                    showSuccessMessage = false
                                                }
                                            }

                                            override fun onError(exc: ImageCaptureException) {
                                                Log.e("ImageCapture", "Image capture failed", exc)
                                                Toast.makeText(
                                                    context,
                                                    "Failed to save image: ${exc.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "Take photo",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            if (capturedImages.isNotEmpty()) {
                                Button(
                                    onClick = { showConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Text("Finish")
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
                            Text("Image saved successfully!")
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