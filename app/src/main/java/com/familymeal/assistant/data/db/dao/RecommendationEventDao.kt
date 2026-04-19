package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.RecommendationEvent

@Dao
interface RecommendationEventDao {
    @Insert
    suspend fun insertEvent(event: RecommendationEvent)

    @Query(
        """
        SELECT catalogMealId,
            SUM(CASE WHEN eventType = 'SHOWN' THEN 1 ELSE 0 END) AS shownCount,
            SUM(CASE WHEN eventType = 'TAPPED' THEN 1 ELSE 0 END) AS tappedCount,
            SUM(CASE WHEN eventType = 'COOKED' THEN 1 ELSE 0 END) AS cookedCount,
            MAX(CASE WHEN eventType = 'IGNORED' THEN occurredAt ELSE NULL END) AS lastIgnoredAt
        FROM recommendation_events
        WHERE catalogMealId = :catalogMealId
        GROUP BY catalogMealId
        """
    )
    suspend fun getSignals(catalogMealId: Long): ImplicitSignalRow?

    @Query(
        """
        SELECT DISTINCT catalogMealId FROM recommendation_events
        WHERE eventType = 'SHOWN'
        AND occurredAt > :sinceEpoch
        GROUP BY catalogMealId
        HAVING SUM(CASE WHEN eventType = 'SHOWN' THEN 1 ELSE 0 END) >= :shownThreshold
            AND SUM(CASE WHEN eventType = 'TAPPED' THEN 1 ELSE 0 END) = 0
        """
    )
    suspend fun getIgnoredMealIds(shownThreshold: Int, sinceEpoch: Long): List<Long>
}

data class ImplicitSignalRow(
    val catalogMealId: Long,
    val shownCount: Int,
    val tappedCount: Int,
    val cookedCount: Int,
    val lastIgnoredAt: Long?
)
