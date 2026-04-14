package com.familymeal.assistant.domain.model

data class RankedMeal(
    val catalogMealId: Long,
    val name: String,
    val cuisine: String,
    val adjustedScore: Float,
    val reasons: List<String>,
    val isExploration: Boolean
)
