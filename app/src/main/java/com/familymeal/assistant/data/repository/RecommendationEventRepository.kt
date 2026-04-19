package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.RecommendationEvent
import com.familymeal.assistant.domain.model.ImplicitSignalSummary

interface RecommendationEventRepository {
    suspend fun insertEvent(event: RecommendationEvent)
    suspend fun getSignals(catalogMealId: Long): ImplicitSignalSummary
    suspend fun getIgnoredMealIds(shownThreshold: Int = 3, withinDays: Int = 14): List<Long>
}
