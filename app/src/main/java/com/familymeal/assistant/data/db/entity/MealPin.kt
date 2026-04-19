package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meal_pins")
data class MealPin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogMealId: Long,
    val mealName: String,
    val slotDate: Long,            // epoch millis — midnight of the pinned day
    val mealType: MealType,
    val isPinned: Boolean = true,
    val isLogged: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
