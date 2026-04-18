package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RecommendationEventType { SHOWN, TAPPED, COOKED, IGNORED, SAVED }

@Entity(tableName = "recommendation_events")
data class RecommendationEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogMealId: Long,
    val mealContext: String,       // e.g. "Lunch", "Tiffin"
    val eventType: RecommendationEventType,
    val occurredAt: Long = System.currentTimeMillis(),
    val sessionId: String = ""     // optional grouping by session
)
