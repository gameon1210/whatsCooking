package com.familymeal.assistant.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.data.db.entity.CatalogMeal

@Composable
fun FavoritesShelf(
    favorites: List<CatalogMeal>,
    dependableMeals: List<CatalogMeal>,
    onToggleFavorite: (catalogMealId: Long, currentlyFavorite: Boolean) -> Unit,
    onMealTapped: (CatalogMeal) -> Unit,
    modifier: Modifier = Modifier
) {
    if (favorites.isEmpty() && dependableMeals.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (favorites.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Favourites", style = MaterialTheme.typography.labelMedium)
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(favorites, key = { it.id }) { meal ->
                    MealPill(
                        meal = meal,
                        isFavorite = true,
                        onToggleFavorite = { onToggleFavorite(meal.id, true) },
                        onTap = { onMealTapped(meal) }
                    )
                }
            }
        }

        if (dependableMeals.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Family picks", style = MaterialTheme.typography.labelMedium)
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                items(dependableMeals, key = { it.id }) { meal ->
                    MealPill(
                        meal = meal,
                        isFavorite = meal.isFavorite,
                        onToggleFavorite = { onToggleFavorite(meal.id, meal.isFavorite) },
                        onTap = { onMealTapped(meal) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MealPill(
    meal: CatalogMeal,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onTap: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onTap)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = meal.name,
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove favourite" else "Add favourite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
