package com.familymeal.assistant.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.EffortLevel
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.domain.model.RankedMeal
import com.familymeal.assistant.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTiffinPlanner: () -> Unit = {},
    onNavigateToWeekView: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val selectedMealType by viewModel.selectedMealType.collectAsState()
    val selectedMemberIds by viewModel.selectedMemberIds.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()
    val recentMeals by viewModel.recentMeals.collectAsState()
    val stripCollapsed by viewModel.stripCollapsed.collectAsState()
    val tomorrowTiffinPin by viewModel.tomorrowTiffinPin.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val dependableMeals by viewModel.dependableMeals.collectAsState()
    val effortCap by viewModel.effortCap.collectAsState()

    var sheetMeal by remember { mutableStateOf<RankedMeal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What's Cooking?") },
                actions = {
                    IconButton(onClick = onNavigateToWeekView) {
                        Icon(Icons.Default.DateRange, contentDescription = "Week plan")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        // Single scrollable list — fixed stacked sections previously squeezed
        // the suggestions off small screens.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Meal context switcher
            item {
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
            }

            // Audience switcher
            item {
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
            }

            // V2: effort cap filter
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    item {
                        FilterChip(
                            selected = effortCap == null,
                            onClick = { viewModel.setEffortCap(null) },
                            label = { Text("Any effort") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = effortCap == EffortLevel.QUICK,
                            onClick = { viewModel.setEffortCap(EffortLevel.QUICK) },
                            label = { Text("⚡ Quick") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = effortCap == EffortLevel.MEDIUM,
                            onClick = { viewModel.setEffortCap(EffortLevel.MEDIUM) },
                            label = { Text("⏱ Medium") }
                        )
                    }
                }
            }

            // V2: tomorrow's tiffin reminder chip
            tomorrowTiffinPin?.let { pin ->
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        onClick = onNavigateToTiffinPlanner
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📦", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Tomorrow's tiffin: ${pin.mealName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            } ?: item {
                // Entry point to the planner even when nothing is pinned yet
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    onClick = onNavigateToTiffinPlanner
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Plan tomorrow's tiffin",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // V2: recently cooked strip
            item {
                RecentlyCookedStrip(
                    recentMeals = recentMeals,
                    isCollapsed = stripCollapsed,
                    onToggleCollapse = { viewModel.toggleStripCollapsed() }
                )
            }

            // V2: favorites + dependable meals shelf
            item {
                FavoritesShelf(
                    favorites = favorites,
                    dependableMeals = dependableMeals,
                    onToggleFavorite = { id, currentlyFav -> viewModel.toggleFavorite(id, currentlyFav) },
                    onMealTapped = { /* open detail — future */ }
                )
            }

            // Suggestions
            when (val state = suggestions) {
                is UiState.Loading -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Success -> {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Recommendations", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Top picks for ${selectedMealType.name.lowercase()} — tap one to cook it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.data.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No matching meals — try a different filter",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(state.data, key = { it.catalogMealId }) { meal ->
                        SuggestionCard(
                            meal = meal,
                            onClick = {
                                viewModel.emitTapped(meal.catalogMealId)
                                sheetMeal = meal
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                is UiState.Error -> item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Couldn't load suggestions right now",
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { viewModel.refresh() }) { Text("Retry") }
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
