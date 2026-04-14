package com.familymeal.assistant.data.repository

import androidx.room.withTransaction
import com.familymeal.assistant.data.db.AppDatabase
import com.familymeal.assistant.data.db.dao.FeedbackDao
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MemberMealScore
import javax.inject.Inject

class FeedbackRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val feedbackDao: FeedbackDao
) : FeedbackRepository {

    override suspend fun saveFeedback(
        signal: FeedbackSignal,
        catalogMealId: Long?,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>
    ) {
        db.withTransaction {
            feedbackDao.insertFeedback(signal)

            if (catalogMealId == null) return@withTransaction

            // Determine which members to update
            val targetMemberIds: List<Long> = when {
                signal.memberId != null -> listOf(signal.memberId)
                signal.signalType == FeedbackType.KidsLiked -> {
                    childMemberIds.ifEmpty { mealMemberIds }
                }
                else -> mealMemberIds
            }

            for (memberId in targetMemberIds) {
                val existing = feedbackDao.getMemberMealScore(memberId, catalogMealId)
                    ?: MemberMealScore(memberId, catalogMealId)

                val updated = when (signal.signalType) {
                    FeedbackType.MakeAgain, FeedbackType.KidsLiked ->
                        existing.copy(positiveSignals = existing.positiveSignals + 1)
                    FeedbackType.NotAHit, FeedbackType.TooMuchWork ->
                        existing.copy(negativeSignals = existing.negativeSignals + 1)
                    FeedbackType.GoodForTiffin -> existing // no score change; handled by weight nudge
                }
                feedbackDao.upsertMemberMealScore(updated)
            }
        }
    }

    override suspend fun getFeedbackForMeal(mealEntryId: Long) =
        feedbackDao.getFeedbackForMeal(mealEntryId)
}
