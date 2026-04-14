package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meal_entries",
    foreignKeys = [
        ForeignKey(
            entity = CatalogMeal::class,
            parentColumns = ["id"],
            childColumns = ["catalogMealId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["mealType", "cookedAt"]),
        Index(value = ["cookedAt"]),
        Index(value = ["catalogMealId"])
    ]
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoUri: String? = null,
    val mealType: MealType,
    val cookedAt: Long = System.currentTimeMillis(),
    val catalogMealId: Long? = null,
    val aiSuggestedName: String? = null,
    val classificationPending: Boolean = false,
    val notes: String? = null
)
