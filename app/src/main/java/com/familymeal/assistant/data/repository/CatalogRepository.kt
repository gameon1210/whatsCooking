package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.CatalogMeal
import com.familymeal.assistant.data.db.entity.DietType
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    suspend fun seedIfEmpty()
    suspend fun getAllMeals(): List<CatalogMeal>
    suspend fun getMealsByDietTypes(allowed: List<DietType>): List<CatalogMeal>
    suspend fun addUserMeal(meal: CatalogMeal): Long
    suspend fun getById(id: Long): CatalogMeal?
    // V2
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
    fun getFavorites(): Flow<List<CatalogMeal>>
    fun getDependableMeals(minMakeAgain: Int = 2): Flow<List<CatalogMeal>>
}
