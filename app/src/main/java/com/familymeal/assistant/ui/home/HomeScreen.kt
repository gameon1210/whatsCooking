package com.familymeal.assistant.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.domain.model.RankedMeal
import com.familymeal.assistant.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val selectedMealType by viewModel.selectedMealType.collectAsState()
    val selectedMemberIds by viewModel.selectedMemberIds.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()

    var sheetMeal by remember { mutableStateOf<RankedMeal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What's Cooking?") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(MealType.entries) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { viewModel.selectMealType(type) },
                        label = { Text(type.name) }
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedMemberIds == null,
                        onClick = { viewModel.selectAudience(null) },
                        label = { Text("Family") }
                    )
                }
                items(activeMembers) { member ->
                    FilterChip(
                        selected = selectedMemberIds == listOf(member.id),
                        onClick = { viewModel.selectAudience(listOf(member.id)) },
                        label = { Text(member.name) }
                    )
                }
            }

            when (val state = suggestions) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Recommendations", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "2-3 suggestions for ${selectedMealType.name.lowercase()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(state.data) { meal ->
                            SuggestionCard(meal = meal, onClick = { sheetMeal = meal })
                        }
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Could not load suggestions", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    sheetMeal?.let { meal ->
        MarkAsCookedSheet(
            meal = meal,
            activeMembers = activeMembers,
            preselectedMealType = selectedMealType,
            preselectedMemberIds = selectedMemberIds,
            onConfirm = { mealType, memberIds, feedback ->
                viewModel.markAsCooked(meal.catalogMealId, meal.name, mealType, memberIds, feedback)
                sheetMeal = null
            },
            onDismiss = { sheetMeal = null }
        )
    }
}
