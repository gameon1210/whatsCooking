package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.MemberMealScore

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFeedback(signal: FeedbackSignal): Long

    @Query("SELECT * FROM feedback_signals WHERE mealEntryId = :mealEntryId")
    suspend fun getFeedbackForMeal(mealEntryId: Long): List<FeedbackSignal>

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
