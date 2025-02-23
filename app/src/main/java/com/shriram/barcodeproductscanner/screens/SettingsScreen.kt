package com.shriram.barcodeproductscanner.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shriram.barcodeproductscanner.R
import com.shriram.barcodeproductscanner.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val format = uiState.imagingNamingFormat

    LaunchedEffect(Unit) {
        viewModel.loadSettings(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.saveSettings(context)
                        onNavigateBack()
                        Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.image_naming_format),
                style = MaterialTheme.typography.titleLarge
            )

            // Preview of the current format
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.preview),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = viewModel.generateImageName("123456789"),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Format options
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Barcode Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_barcode))
                        Switch(
                            checked = format.includeBarcode,
                            onCheckedChange = {
                                viewModel.updateImageNamingFormat(format.copy(includeBarcode = it))
                            }
                        )
                    }

                    // Product Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_product_name))
                        Switch(
                            checked = format.includeProductName,
                            onCheckedChange = {
                                viewModel.updateImageNamingFormat(format.copy(includeProductName = it))
                            }
                        )
                    }

                    if (format.includeProductName) {
                        OutlinedTextField(
                            value = format.productName,
                            onValueChange = {
                                viewModel.updateImageNamingFormat(format.copy(productName = it))
                            },
                            label = { Text(stringResource(R.string.product_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Date Time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_date_time))
                        Switch(
                            checked = format.includeDateTime,
                            onCheckedChange = {
                                viewModel.updateImageNamingFormat(format.copy(includeDateTime = it))
                            }
                        )
                    }

                    // Custom Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.include_custom_text))
                        Switch(
                            checked = format.includeCustomText,
                            onCheckedChange = {
                                viewModel.updateImageNamingFormat(format.copy(includeCustomText = it))
                            }
                        )
                    }

                    if (format.includeCustomText) {
                        OutlinedTextField(
                            value = format.customText,
                            onValueChange = {
                                viewModel.updateImageNamingFormat(format.copy(customText = it))
                            },
                            label = { Text(stringResource(R.string.custom_text)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Separator
                    OutlinedTextField(
                        value = format.separator,
                        onValueChange = {
                            viewModel.updateImageNamingFormat(format.copy(separator = it))
                        },
                        label = { Text(stringResource(R.string.separator)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
} 