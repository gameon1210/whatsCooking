package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MemberMealScore

data class CatalogFeedbackCount(
    val catalogMealId: Long,
    val signalType: FeedbackType,
    val count: Int
)

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeedback(signal: FeedbackSignal): Long

    @Query("DELETE FROM feedback_signals WHERE id = :feedbackId")
    suspend fun deleteFeedbackById(feedbackId: Long)

    @Query("SELECT * FROM feedback_signals WHERE mealEntryId = :mealEntryId")
    suspend fun getFeedbackForMeal(mealEntryId: Long): List<FeedbackSignal>

    @Query(
        """
        SELECT me.catalogMealId AS catalogMealId, fs.signalType AS signalType, COUNT(fs.id) AS count
        FROM feedback_signals fs
        INNER JOIN meal_entries me ON me.id = fs.mealEntryId
        WHERE me.catalogMealId IN (:catalogMealIds)
        GROUP BY me.catalogMealId, fs.signalType
        """
    )
    suspend fun getFeedbackCountsForCatalogMeals(catalogMealIds: List<Long>): List<CatalogFeedbackCount>

    // MemberMealScore upsert
    @Query("SELECT * FROM member_meal_scores WHERE memberId = :memberId AND catalogMealId = :catalogMealId")
    suspend fun getMemberMealScore(memberId: Long, catalogMealId: Long): MemberMealScore?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemberMealScore(score: MemberMealScore)

    @Query("SELECT * FROM member_meal_scores WHERE memberId = :memberId")
    suspend fun getScoresForMember(memberId: Long): List<MemberMealScore>

    @Query("SELECT * FROM member_meal_scores WHERE catalogMealId = :catalogMealId")
    suspend fun getScoresForCatalogMeal(catalogMealId: Long): List<MemberMealScore>
}
