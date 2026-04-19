package com.familymeal.assistant.domain.model

import com.familymeal.assistant.data.db.entity.EffortLevel

data class RankedMeal(
    val catalogMealId: Long,
    val name: String,
    val cuisine: String,
    val adjustedScore: Float,
    val reasons: List<String>,
    val isExploration: Boolean,
    val breakdown: ScoreBreakdown = ScoreBreakdown(),  // V2: full breakdown for ReasonGenerator
    val effortLevel: EffortLevel = EffortLevel.MEDIUM  // V2: for effort badge in UI
)
