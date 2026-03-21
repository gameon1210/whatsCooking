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
    suspend fun getFeedbackForMeal(mealEntryId: Long): List<FeedbackSignal>
}
