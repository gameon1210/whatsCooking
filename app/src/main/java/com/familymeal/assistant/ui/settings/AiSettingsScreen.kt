package com.familymeal.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.domain.classifier.AiProvider
import com.familymeal.assistant.ui.common.InputValidators

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val storedProvider by viewModel.aiProvider.collectAsState()
    val storedModel by viewModel.aiModel.collectAsState()
    val storedApiKey by viewModel.apiKey.collectAsState()
    var selectedProvider by remember(storedProvider) { mutableStateOf(storedProvider) }
    var modelText by remember(storedModel) { mutableStateOf(storedModel) }
    var apiKeyText by remember(storedApiKey) { mutableStateOf(storedApiKey.orEmpty()) }
    var showApiKey by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val modelError = InputValidators.modelError(modelText)
    val apiKeyError = InputValidators.apiKeyError(apiKeyText)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Setup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Choose which LLM provider should identify meals from photos.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Provider, model, and secret key are editable here. The secret key is stored only on this device using encrypted local storage and is never hardcoded in the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                if (storedApiKey.isNullOrBlank()) {
                    "No secret key saved yet."
                } else {
                    "${storedProvider.displayName} is configured on this device."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Provider", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiProvider.entries) { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = {
                            val previousProvider = selectedProvider
                            selectedProvider = provider
                            if (modelText.isBlank() || modelText == previousProvider.defaultModel) {
                                modelText = provider.defaultModel
                            }
                        },
                        label = { Text(provider.displayName) }
                    )
                }
            }

            OutlinedTextField(
                value = modelText,
                onValueChange = { modelText = it },
                label = { Text("Model") },
                supportingText = {
                    Text(
                        when {
                            showErrors && modelError != null -> modelError
                            else -> "Example: ${selectedProvider.defaultModel}"
                        }
                    )
                },
                isError = showErrors && modelError != null,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKeyText,
                onValueChange = {
                    apiKeyText = it
                },
                label = { Text("Secret key") },
                isError = showErrors && apiKeyError != null,
                supportingText = {
                    Text(
                        when {
                            showErrors && apiKeyError != null -> apiKeyError
                            else -> "Paste the ${selectedProvider.displayName} secret key for the selected model."
                        }
                    )
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        showErrors = true
                        val currentModelError = InputValidators.modelError(modelText)
                        val currentApiKeyError = InputValidators.apiKeyError(apiKeyText)
                        if (currentModelError == null && currentApiKeyError == null) {
                            viewModel.saveAiConfig(selectedProvider, modelText, apiKeyText)
                        }
                    }
                ) {
                    Text("Save")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.clearApiKey()
                        apiKeyText = ""
                        showErrors = false
                    }
                ) {
                    Text("Clear")
                }
                TextButton(onClick = onNavigateBack) {
                    Text("Done")
                }
            }
        }
    }
}
