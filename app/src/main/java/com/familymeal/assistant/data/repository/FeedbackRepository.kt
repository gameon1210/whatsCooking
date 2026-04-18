package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.FeedbackSignal

interface FeedbackRepository {
    // Saves the feedback AND updates MemberMealScore in one transaction
    suspend fun saveFeedback(
        signal: FeedbackSignal,
        catalogMealId: Long?,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>  // members with non-null birthYear in the meal's cross-refs
    )

    suspend fun removeFeedback(
        signal: FeedbackSignal,
        catalogMealId: Long?,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>
    )

    suspend fun getFeedbackForMeal(mealEntryId: Long): List<FeedbackSignal>
    suspend fun getFeedbackCounts(catalogMealIds: List<Long>): Map<Long, Map<com.familymeal.assistant.data.db.entity.FeedbackType, Int>>
    suspend fun getMemberMealScores(memberIds: List<Long>): Map<Pair<Long, Long>, com.familymeal.assistant.data.db.entity.MemberMealScore>
}
