package com.familymeal.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.familymeal.assistant.ui.common.InputValidators

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val storedApiKey by viewModel.apiKey.collectAsState()
    var apiKeyText by remember(storedApiKey) { mutableStateOf(storedApiKey.orEmpty()) }
    var showApiKey by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

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
                "This MVP uses Gemini to suggest meal names from photos.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Create your own Gemini API key, then paste it here. The key is stored only on this device using encrypted local storage and is never hardcoded in the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                if (storedApiKey.isNullOrBlank()) "No key saved yet" else "A key is already saved on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = apiKeyText,
                onValueChange = {
                    apiKeyText = it
                    if (showErrors) showErrors = true
                },
                label = { Text("Gemini API Key") },
                isError = showErrors && apiKeyError != null,
                supportingText = {
                    Text(
                        when {
                            showErrors && apiKeyError != null -> apiKeyError
                            else -> "Create the key yourself, then paste it here."
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
                        if (apiKeyError == null) viewModel.saveApiKey(apiKeyText)
                    }
                ) {
                    Text("Save Key")
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
