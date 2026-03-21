package com.familymeal.assistant.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.domain.model.RankedMeal

@Composable
fun SuggestionCard(
    meal: RankedMeal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = meal.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = meal.cuisine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (meal.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(meal.reasons) { reason ->
                        SuggestionReasonChip(reason = reason)
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionReasonChip(reason: String) {
    SuggestionChip(
        onClick = {},
        label = { Text(reason, style = MaterialTheme.typography.labelSmall) }
    )
}
