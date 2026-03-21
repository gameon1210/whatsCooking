package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.dao.MealEntryDao
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealMemberCrossRef
import com.familymeal.assistant.data.db.entity.MealType
import javax.inject.Inject

class MealRepositoryImpl @Inject constructor(
    private val mealEntryDao: MealEntryDao
) : MealRepository {

    override suspend fun saveMeal(entry: MealEntry, memberIds: List<Long>): Long {
        val id = mealEntryDao.insertMealEntry(entry)
        val crossRefs = memberIds.map { MealMemberCrossRef(id, it) }
        if (crossRefs.isNotEmpty()) mealEntryDao.insertCrossRefs(crossRefs)
        return id
    }

    override suspend fun updateMeal(entry: MealEntry) = mealEntryDao.updateMealEntry(entry)
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
