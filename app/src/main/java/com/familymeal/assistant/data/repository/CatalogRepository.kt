package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.CatalogMeal
import com.familymeal.assistant.data.db.entity.DietType

interface CatalogRepository {
    suspend fun seedIfEmpty()
    suspend fun getAllMeals(): List<CatalogMeal>
    suspend fun getMealsByDietTypes(allowed: List<DietType>): List<CatalogMeal>
    suspend fun addUserMeal(meal: CatalogMeal): Long
    suspend fun getById(id: Long): CatalogMeal?
}
