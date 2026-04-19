package com.familymeal.assistant.ui.weekview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.MealPin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewScreen(
    onNavigateBack: () -> Unit,
    viewModel: WeekViewViewModel = hiltViewModel()
) {
    val weekSlots by viewModel.weekSlots.collectAsState()

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
                    DayCard(slot = slot, onRemovePin = { viewModel.removePin(it) })
                }
            }
        }
    }
}

@Composable
private fun DayCard(slot: DaySlot, onRemovePin: (MealPin) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = slot.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
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
