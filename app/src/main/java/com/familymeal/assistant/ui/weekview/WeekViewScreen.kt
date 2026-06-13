package com.familymeal.assistant.ui.weekview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.MealPin
import com.familymeal.assistant.data.db.entity.MealType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewScreen(
    onNavigateBack: () -> Unit,
    viewModel: WeekViewViewModel = hiltViewModel()
) {
    val weekSlots by viewModel.weekSlots.collectAsState()
    val filteredCatalog by viewModel.filteredCatalog.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Day being planned via the add-pin sheet (null = sheet hidden)
    var planningSlot by remember { mutableStateOf<DaySlot?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("This Week") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (weekSlots.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(weekSlots, key = { it.epochMillis }) { slot ->
                    DayCard(
                        slot = slot,
                        onRemovePin = { viewModel.removePin(it) },
                        onAddPin = { planningSlot = slot }
                    )
                }
            }
        }
    }

    planningSlot?.let { slot ->
        AddPinSheet(
            dayLabel = slot.label,
            searchQuery = searchQuery,
            onSearchChange = { viewModel.setSearchQuery(it) },
            catalog = filteredCatalog.map { it.id to it.name },
            onPin = { catalogMealId, mealName, mealType ->
                viewModel.pinMeal(mealName, catalogMealId, slot.epochMillis, mealType)
                viewModel.setSearchQuery("")
                planningSlot = null
            },
            onDismiss = {
                viewModel.setSearchQuery("")
                planningSlot = null
            }
        )
    }
}

@Composable
private fun DayCard(
    slot: DaySlot,
    onRemovePin: (MealPin) -> Unit,
    onAddPin: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onAddPin, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Plan a meal for ${slot.label}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (slot.pins.isEmpty()) {
                Text(
                    "Nothing planned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                slot.pins.forEach { pin ->
                    PinRow(pin = pin, onRemove = { onRemovePin(pin) })
                }
            }
        }
    }
}

@Composable
private fun PinRow(pin: MealPin, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pin.mealName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${pin.mealType.name}${if (pin.isLogged) " · Logged ✓" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (pin.isLogged) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!pin.isLogged) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPinSheet(
    dayLabel: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    catalog: List<Pair<Long, String>>,
    onPin: (catalogMealId: Long, mealName: String, mealType: MealType) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMealType by remember { mutableStateOf(MealType.Dinner) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Plan a meal — $dayLabel", style = MaterialTheme.typography.titleLarge)

            Text("Meal type", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MealType.entries) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        label = { Text(type.name) }
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search meals…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                if (catalog.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No meals found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(catalog, key = { it.first }) { (id, name) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            trailingContent = {
                                TextButton(onClick = { onPin(id, name, selectedMealType) }) {
                                    Text("Pin")
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
