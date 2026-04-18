package com.familymeal.assistant.data.repository

import androidx.room.withTransaction
import com.familymeal.assistant.data.db.AppDatabase
import com.familymeal.assistant.data.db.dao.CatalogFeedbackCount
import com.familymeal.assistant.data.db.dao.FeedbackDao
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MemberMealScore
import javax.inject.Inject

class FeedbackRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val feedbackDao: FeedbackDao
) : FeedbackRepository {

    private fun resolveTargetMemberIds(
        signal: FeedbackSignal,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>
    ): List<Long> = when {
        signal.memberId != null -> listOf(signal.memberId)
        signal.signalType == FeedbackType.KidsLiked -> childMemberIds.ifEmpty { mealMemberIds }
        else -> mealMemberIds
    }

    override suspend fun saveFeedback(
        signal: FeedbackSignal,
        catalogMealId: Long?,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>
    ) {
        db.withTransaction {
            feedbackDao.insertFeedback(signal)

            if (catalogMealId == null) return@withTransaction

            val targetMemberIds = resolveTargetMemberIds(signal, mealMemberIds, childMemberIds)

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

    override suspend fun removeFeedback(
        signal: FeedbackSignal,
        catalogMealId: Long?,
        mealMemberIds: List<Long>,
        childMemberIds: List<Long>
    ) {
        db.withTransaction {
            feedbackDao.deleteFeedbackById(signal.id)

            if (catalogMealId == null) return@withTransaction

            val targetMemberIds = resolveTargetMemberIds(signal, mealMemberIds, childMemberIds)

            for (memberId in targetMemberIds) {
                val existing = feedbackDao.getMemberMealScore(memberId, catalogMealId) ?: continue
                val updated = when (signal.signalType) {
                    FeedbackType.MakeAgain, FeedbackType.KidsLiked ->
                        existing.copy(positiveSignals = (existing.positiveSignals - 1).coerceAtLeast(0))
                    FeedbackType.NotAHit, FeedbackType.TooMuchWork ->
                        existing.copy(negativeSignals = (existing.negativeSignals - 1).coerceAtLeast(0))
                    FeedbackType.GoodForTiffin -> existing
                }
                feedbackDao.upsertMemberMealScore(updated)
            }
        }
    }

    override suspend fun getFeedbackForMeal(mealEntryId: Long) =
        feedbackDao.getFeedbackForMeal(mealEntryId)

    override suspend fun getFeedbackCounts(catalogMealIds: List<Long>): Map<Long, Map<FeedbackType, Int>> {
        if (catalogMealIds.isEmpty()) return emptyMap()

        return feedbackDao.getFeedbackCountsForCatalogMeals(catalogMealIds)
            .groupBy(CatalogFeedbackCount::catalogMealId)
            .mapValues { (_, counts) ->
                counts.associate { count -> count.signalType to count.count }
            }
    }

    override suspend fun getMemberMealScores(memberIds: List<Long>): Map<Pair<Long, Long>, MemberMealScore> {
        if (memberIds.isEmpty()) return emptyMap()

        return memberIds.distinct()
            .flatMap { memberId -> feedbackDao.getScoresForMember(memberId) }
            .associateBy { it.memberId to it.catalogMealId }
    }
}
