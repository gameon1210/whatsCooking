package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.dao.RecommendationEventDao
import com.familymeal.assistant.data.db.entity.RecommendationEvent
import com.familymeal.assistant.domain.model.ImplicitSignalSummary
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RecommendationEventRepositoryImpl @Inject constructor(
    private val dao: RecommendationEventDao
) : RecommendationEventRepository {

    override suspend fun insertEvent(event: RecommendationEvent) = dao.insertEvent(event)

    override suspend fun getSignals(catalogMealId: Long): ImplicitSignalSummary {
        val row = dao.getSignals(catalogMealId)
        return ImplicitSignalSummary(
            catalogMealId = catalogMealId,
            shownCount = row?.shownCount ?: 0,
            tappedCount = row?.tappedCount ?: 0,
            cookedCount = row?.cookedCount ?: 0,
            lastIgnoredAt = row?.lastIgnoredAt
        )
    }

    override suspend fun getIgnoredMealIds(shownThreshold: Int, withinDays: Int): List<Long> {
        val sinceEpoch = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(withinDays.toLong())
        return dao.getIgnoredMealIds(shownThreshold, sinceEpoch)
    }
}
