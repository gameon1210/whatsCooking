package com.familymeal.assistant.data.repository

import android.content.Context
import com.familymeal.assistant.data.db.dao.CatalogMealDao
import com.familymeal.assistant.data.db.entity.CatalogMeal
import com.familymeal.assistant.data.db.entity.DietType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CatalogRepositoryImpl @Inject constructor(
    private val catalogMealDao: CatalogMealDao,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : CatalogRepository {

    override suspend fun seedIfEmpty() {
        if (catalogMealDao.count() > 0) return
        val json = context.assets.open("catalog.json").bufferedReader().readText()
        val type = object : TypeToken<List<CatalogMeal>>() {}.type
        val meals: List<CatalogMeal> = gson.fromJson(json, type)
        catalogMealDao.insertAll(meals)
    }

    override suspend fun getAllMeals() = catalogMealDao.getAllMeals()

    override suspend fun getMealsByDietTypes(allowed: List<DietType>) =
        catalogMealDao.getMealsByDietTypes(allowed.map { it.name })

    override suspend fun addUserMeal(meal: CatalogMeal) = catalogMealDao.insert(meal)
    override suspend fun getById(id: Long) = catalogMealDao.getById(id)
}
