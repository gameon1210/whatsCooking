package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_meals")
data class CatalogMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cuisine: String,
    val dietType: DietType,
    val mealTypes: String,       // comma-separated MealType names e.g. "Lunch,Dinner"
    val tags: String? = null,    // comma-separated e.g. "quick,festive"
    val isUserAdded: Boolean = false
)
