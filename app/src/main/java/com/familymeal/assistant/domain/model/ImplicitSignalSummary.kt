package com.familymeal.assistant.domain.model

data class ImplicitSignalSummary(
    val catalogMealId: Long,
    val shownCount: Int = 0,
    val tappedCount: Int = 0,
    val cookedCount: Int = 0,
    val lastIgnoredAt: Long? = null
)
