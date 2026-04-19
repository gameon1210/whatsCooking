package com.familymeal.assistant.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.data.db.entity.EffortLevel
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meal.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = meal.cuisine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // V2: effort badge
                EffortBadge(effort = meal.effortLevel)
            }
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
private fun EffortBadge(effort: EffortLevel) {
    val (label, color) = when (effort) {
        EffortLevel.QUICK -> "⚡ Quick" to MaterialTheme.colorScheme.primaryContainer
        EffortLevel.MEDIUM -> "⏱ Medium" to MaterialTheme.colorScheme.surfaceVariant
        EffortLevel.INVOLVED -> "🍳 Involved" to MaterialTheme.colorScheme.tertiaryContainer
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SuggestionReasonChip(reason: String) {
    SuggestionChip(
        onClick = {},
        label = { Text(reason, style = MaterialTheme.typography.labelSmall) }
    )
}
