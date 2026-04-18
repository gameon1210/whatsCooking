package com.familymeal.assistant.data.repository

import androidx.room.withTransaction
import com.familymeal.assistant.data.db.AppDatabase
import com.familymeal.assistant.data.db.dao.FeedbackDao
import com.familymeal.assistant.data.db.dao.MealEntryDao
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealMemberCrossRef
import com.familymeal.assistant.data.db.entity.MemberMealScore
import com.familymeal.assistant.data.db.entity.MealType
import javax.inject.Inject

class MealRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val mealEntryDao: MealEntryDao,
    private val feedbackDao: FeedbackDao
) : MealRepository {

    override suspend fun saveMeal(entry: MealEntry, memberIds: List<Long>): Long {
        return db.withTransaction {
            val id = mealEntryDao.insertMealEntry(entry)
            val crossRefs = memberIds.map { MealMemberCrossRef(id, it) }
            if (crossRefs.isNotEmpty()) {
                mealEntryDao.insertCrossRefs(crossRefs)
            }

            entry.catalogMealId?.let { catalogMealId ->
                memberIds.forEach { memberId ->
                    val existing = feedbackDao.getMemberMealScore(memberId, catalogMealId)
                        ?: MemberMealScore(memberId = memberId, catalogMealId = catalogMealId)
                    feedbackDao.upsertMemberMealScore(
                        existing.copy(
                            timesCooked = existing.timesCooked + 1,
                            lastCookedAt = entry.cookedAt
                        )
                    )
                }
            }

            id
        }
    }

    override suspend fun updateMeal(entry: MealEntry) = mealEntryDao.updateMealEntry(entry)
    override suspend fun deleteMeal(mealEntryId: Long) = mealEntryDao.deleteMealEntryById(mealEntryId)
    override fun observeAllMeals() = mealEntryDao.observeAllMeals()
    override fun observeMealsByType(type: MealType) = mealEntryDao.observeMealsByType(type)
    override fun observeMealsByMember(memberId: Long) = mealEntryDao.observeMealsByMember(memberId)
    override suspend fun getLastCookedForCatalogMeal(id: Long) = mealEntryDao.getLastCookedForCatalogMeal(id)
    override suspend fun getMemberIdsForMeal(id: Long) = mealEntryDao.getMemberIdsForMeal(id)

    override suspend fun reconcilePendingClassifications() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        mealEntryDao.reconcilePendingClassifications(cutoff)
    }
}
