package com.familymeal.assistant.domain.model

data class ScoreBreakdown(
    val recency: Float = 0f,
    val makeAgain: Float = 0f,
    val notAHit: Float = 0f,
    val tooMuchWork: Float = 0f,
    val tiffin: Float = 0f,
    val memberMatch: Float = 0f,
    val memberModifier: Float = 0f
) {
    val baseScore: Float get() = recency + makeAgain - notAHit - tooMuchWork + tiffin + memberMatch
    val adjustedScore: Float get() = baseScore * (1f + memberModifier)
}
