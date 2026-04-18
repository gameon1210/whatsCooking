package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tiffin_plans")
data class TiffinPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogMealId: Long,
    val mealName: String,
    val plannedDate: Long,           // epoch millis — midnight of the planned day
    val savedAt: Long = System.currentTimeMillis(),
    val loggedMealEntryId: Long? = null  // set after user logs it
)
