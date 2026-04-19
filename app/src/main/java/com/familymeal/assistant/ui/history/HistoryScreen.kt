package com.familymeal.assistant.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.ui.common.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val meals by viewModel.meals.collectAsState()
    val groupedMeals by viewModel.groupedMeals.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }
    var feedbackForMeal by remember { mutableStateOf<List<FeedbackSignal>>(emptyList()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMeal) {
        selectedMeal?.let {
            feedbackForMeal = viewModel.getFeedbackForMeal(it.id)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // V2: search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search meals…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Meal type filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = filter.mealType == null,
                        onClick = { viewModel.setMealTypeFilter(null) },
                        label = { Text("All") }
                    )
                }
                items(MealType.entries) { type ->
                    FilterChip(
                        selected = filter.mealType == type,
                        onClick = { viewModel.setMealTypeFilter(type) },
                        label = { Text(type.name) }
                    )
                }
            }

            // Member filter chips
            if (activeMembers.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filter.memberId == null,
                            onClick = { viewModel.setMemberFilter(null) },
                            label = { Text("Family") }
                        )
                    }
                    items(activeMembers) { member ->
                        FilterChip(
                            selected = filter.memberId == member.id,
                            onClick = { viewModel.setMemberFilter(member.id) },
                            label = { Text(member.name) }
                        )
                    }
                }
            }

            when (val state = meals) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                if (searchQuery.isNotEmpty()) "No meals match \"$searchQuery\""
                                else "No meals logged yet"
                            )
                        }
                    } else {
                        // V2: date-grouped list
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            groupedMeals.forEach { group ->
                                item(key = group.label) {
                                    Text(
                                        text = group.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                    )
                                }
                                items(group.meals, key = { it.id }) { meal ->
                                    MealHistoryRow(
                                        meal = meal,
                                        onClick = { selectedMeal = meal }
                                    )
                                }
                            }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    selectedMeal?.let { meal ->
        MealDetailSheet(
            meal = meal,
            existingFeedback = feedbackForMeal,
            onAddFeedback = { signal ->
                viewModel.addFeedback(meal, signal)
                feedbackForMeal = feedbackForMeal + FeedbackSignal(
                    mealEntryId = meal.id, signalType = signal
                )
            },
            onRemoveFeedback = { signal ->
                viewModel.removeFeedback(meal, signal)
                feedbackForMeal = feedbackForMeal.filterNot { it.id == signal.id }
            },
            onDeleteMeal = { showDeleteConfirmation = true },
            onDismiss = { selectedMeal = null }
        )
    }

    if (showDeleteConfirmation && selectedMeal != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete meal?") },
            text = { Text("This removes the meal from history and clears its feedback labels.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMeal(selectedMeal!!.id)
                        showDeleteConfirmation = false
                        selectedMeal = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MealHistoryRow(meal: MealEntry, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    ListItem(
        leadingContent = {
            // V2: thumbnail using Coil
            if (meal.photoUri != null) {
                AsyncImage(
                    model = meal.photoUri,
                    contentDescription = meal.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = meal.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        headlineContent = { Text(meal.name) },
        supportingContent = {
            // V2: meal type label + time
            Text("${meal.mealType.name} · ${dateFormat.format(Date(meal.cookedAt))}")
        },
        trailingContent = {
            if (meal.classificationPending && System.currentTimeMillis() - meal.cookedAt < 86_400_000L) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
