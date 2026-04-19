package com.familymeal.assistant.ui.tiffin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.CatalogMeal
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TiffinPlannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: TiffinPlannerViewModel = hiltViewModel()
) {
    val activePlan by viewModel.activePlan.collectAsState()
    val filteredCatalog by viewModel.filteredCatalog.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val dateFormat = remember { SimpleDateFormat("EEEE, d MMM", Locale.getDefault()) }
    val tomorrowLabel = remember { dateFormat.format(Date(viewModel.tomorrowMillis)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiffin Planner") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tomorrow's plan header
            Text(
                text = "Tomorrow's tiffin — $tomorrowLabel",
                style = MaterialTheme.typography.titleMedium
            )

            // Active plan card
            if (activePlan != null) {
                val plan = activePlan!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = plan.mealName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (plan.loggedMealEntryId != null) "Logged ✓" else "Pinned for tomorrow",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { viewModel.clearPlan() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove plan",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No tiffin planned yet — pick one below",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()
            Text("Pick a meal", style = MaterialTheme.typography.labelMedium)

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search catalog…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Catalog list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filteredCatalog.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No meals found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filteredCatalog, key = { it.id }) { meal ->
                        TiffinMealRow(
                            meal = meal,
                            isPinned = activePlan?.catalogMealId == meal.id,
                            onPin = { viewModel.pinMeal(meal) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TiffinMealRow(
    meal: CatalogMeal,
    isPinned: Boolean,
    onPin: () -> Unit
) {
    ListItem(
        headlineContent = { Text(meal.name) },
        supportingContent = {
            Text("${meal.cuisine} · ${meal.dietType.name}")
        },
        trailingContent = {
            if (isPinned) {
                AssistChip(
                    onClick = {},
                    label = { Text("Pinned") }
                )
            } else {
                OutlinedButton(onClick = onPin) {
                    Text("Pin")
                }
            }
        },
        modifier = Modifier.clickable(onClick = onPin)
    )
    HorizontalDivider()
}
