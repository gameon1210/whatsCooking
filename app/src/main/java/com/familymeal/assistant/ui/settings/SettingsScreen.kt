package com.familymeal.assistant.ui.settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.familymeal.assistant.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val weightsState by viewModel.weights.collectAsState()
    val explorationRatio by viewModel.explorationRatio.collectAsState()
    val aiProvider by viewModel.aiProvider.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Household", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedButton(
                    onClick = onNavigateToMembers,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage Members")
                }
            }

            item { HorizontalDivider() }
            item {
                Text("AI Meal Naming", style = MaterialTheme.typography.titleMedium)
            }
            item {
                ListItem(
                    headlineContent = { Text("AI setup") },
                    supportingContent = {
                        Text(
                            if (apiKey.isNullOrBlank()) {
                                "Choose provider, model, and secret key."
                            } else {
                                "${aiProvider.displayName} · $aiModel"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open AI setup"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToAiSettings)
                )
            }

            item { HorizontalDivider() }
            item {
                Text("Discovery Ratio", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Column {
                    Text(
                        "${(explorationRatio * 100).toInt()}% of suggestions are discoveries",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = explorationRatio,
                        onValueChange = { viewModel.setExplorationRatio(it) },
                        valueRange = 0.10f..0.30f,
                        steps = 3
                    )
                }
            }

            item { HorizontalDivider() }
            item {
                Text("Ranking Weights", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text(
                    "These adapt automatically as the app learns. You can reset any weight to its default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (val state = weightsState) {
                is UiState.Loading -> item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Success -> {
                    items(state.data) { weight ->
                        WeightSliderRow(
                            weight = weight,
                            onValueChange = { newValue ->
                                viewModel.updateWeight(weight.copy(value = newValue))
                            },
                            onReset = { viewModel.resetWeight(weight.signalName) }
                        )
                    }
                }
                is UiState.Error -> item {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }

            item { HorizontalDivider() }
            item {
                Text("About", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Text("What's Cooking? · v1.2.0", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WeightSliderRow(
    weight: RankingWeight,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    val min = 0.1f * weight.defaultValue
    val max = 2.0f * weight.defaultValue

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(weight.signalName, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("%.2f".format(weight.value), style = MaterialTheme.typography.labelSmall)
                if (weight.value != weight.defaultValue) {
                    TextButton(onClick = onReset, contentPadding = PaddingValues(4.dp)) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Slider(
            value = weight.value.coerceIn(min, max),
            onValueChange = onValueChange,
            valueRange = min..max
        )
    }
}
