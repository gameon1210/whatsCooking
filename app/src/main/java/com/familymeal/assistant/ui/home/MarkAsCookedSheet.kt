package com.familymeal.assistant.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.domain.model.RankedMeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkAsCookedSheet(
    meal: RankedMeal,
    activeMembers: List<Member>,
    preselectedMealType: MealType,
    preselectedMemberIds: List<Long>?,  // null = Family
    onConfirm: (mealType: MealType, memberIds: List<Long>, feedback: List<FeedbackType>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMealType by remember { mutableStateOf(preselectedMealType) }
    var selectedMemberIds by remember {
        mutableStateOf(preselectedMemberIds ?: activeMembers.map { it.id })
    }
    val selectedFeedback = remember { mutableStateListOf<FeedbackType>() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Mark as Cooked", style = MaterialTheme.typography.titleLarge)
            Text(meal.name, style = MaterialTheme.typography.bodyLarge)

            Text("Meal Type", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MealType.entries) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        label = { Text(type.name) }
                    )
                }
            }

            Text("Who's eating?", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedMemberIds.size == activeMembers.size,
                        onClick = { selectedMemberIds = activeMembers.map { it.id } },
                        label = { Text("Family") }
                    )
                }
                items(activeMembers) { member ->
                    FilterChip(
                        selected = selectedMemberIds == listOf(member.id),
                        onClick = { selectedMemberIds = listOf(member.id) },
                        label = { Text(member.name) }
                    )
                }
            }

            Text("How was it? (optional)", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FeedbackType.entries) { signal ->
                    FilterChip(
                        selected = signal in selectedFeedback,
                        onClick = {
                            if (signal in selectedFeedback) selectedFeedback.remove(signal)
                            else selectedFeedback.add(signal)
                        },
                        label = { Text(signal.displayName()) }
                    )
                }
            }

            Button(
                onClick = { onConfirm(selectedMealType, selectedMemberIds, selectedFeedback.toList()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm")
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
