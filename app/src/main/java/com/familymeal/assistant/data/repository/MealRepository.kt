package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealType
import kotlinx.coroutines.flow.Flow

interface MealRepository {
    suspend fun saveMeal(entry: MealEntry, memberIds: List<Long>): Long
    suspend fun updateMeal(entry: MealEntry)
    fun observeAllMeals(): Flow<List<MealEntry>>
    fun observeMealsByType(type: MealType): Flow<List<MealEntry>>
    fun observeMealsByMember(memberId: Long): Flow<List<MealEntry>>
    suspend fun getLastCookedForCatalogMeal(catalogMealId: Long): MealEntry?
    suspend fun getMemberIdsForMeal(mealEntryId: Long): List<Long>
    suspend fun reconcilePendingClassifications()
}
