package com.familymeal.assistant.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailSheet(
    meal: MealEntry,
    existingFeedback: List<FeedbackSignal>,
    onAddFeedback: (FeedbackType) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()) }
    val alreadyGiven = existingFeedback.map { it.signalType }.toSet()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(meal.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${meal.mealType.name} · ${dateFormat.format(Date(meal.cookedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (meal.classificationPending && (System.currentTimeMillis() - meal.cookedAt < 86_400_000L)) {
                Text(
                    "Identifying meal…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (alreadyGiven.isNotEmpty()) {
                Text("Feedback given", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(alreadyGiven.toList()) { signal ->
                        AssistChip(onClick = {}, label = { Text(signal.displayName()) })
                    }
                }
            }

            val remaining = FeedbackType.entries.filter { it !in alreadyGiven }
            if (remaining.isNotEmpty()) {
                Text("Add feedback", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(remaining) { signal ->
                        SuggestionChip(
                            onClick = { onAddFeedback(signal) },
                            label = { Text(signal.displayName()) }
                        )
                    }
                }
            }
        }
    }
}

private fun FeedbackType.displayName() = when (this) {
    FeedbackType.MakeAgain -> "Make Again"
    FeedbackType.GoodForTiffin -> "Good for Tiffin"
    FeedbackType.KidsLiked -> "Kids Liked"
    FeedbackType.TooMuchWork -> "Too Much Work"
    FeedbackType.NotAHit -> "Not a Hit"
}
