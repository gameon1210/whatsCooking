package com.familymeal.assistant.ui.addmeal

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.data.db.entity.FeedbackType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostSaveFeedbackSheet(
    mealName: String,
    mealEntryId: Long,
    onFeedback: (mealEntryId: Long, FeedbackType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "How did $mealName go?",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Quick tap — helps future suggestions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val signals = listOf(
                FeedbackType.MakeAgain to "Make Again 👍",
                FeedbackType.GoodForTiffin to "Good for Tiffin 📦",
                FeedbackType.KidsLiked to "Kids Liked It 🧒",
                FeedbackType.TooMuchWork to "Too Much Work 😓",
                FeedbackType.NotAHit to "Not a Hit 👎",
                FeedbackType.GoodForLeftovers to "Good for Leftovers 🥡"
            )

            signals.chunked(2).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { (type, label) ->
                        OutlinedButton(
                            onClick = {
                                onFeedback(mealEntryId, type)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    // Fill empty slot in odd row
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip")
            }
        }
    }
}
