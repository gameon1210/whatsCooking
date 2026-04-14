package com.familymeal.assistant.domain.model

data class WeightMap(
    val recency: Float,
    val makeAgain: Float,
    val notAHit: Float,
    val tooMuchWork: Float,
    val tiffin: Float,
    val memberMatch: Float
)
