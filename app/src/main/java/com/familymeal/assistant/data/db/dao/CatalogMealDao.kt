package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.CatalogMeal

@Dao
interface CatalogMealDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(meals: List<CatalogMeal>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(meal: CatalogMeal): Long

    @Query("SELECT * FROM catalog_meals ORDER BY name ASC")
    suspend fun getAllMeals(): List<CatalogMeal>

    @Query("SELECT * FROM catalog_meals WHERE dietType IN (:allowedDietTypes) ORDER BY name ASC")
    suspend fun getMealsByDietTypes(allowedDietTypes: List<String>): List<CatalogMeal>

    @Query("SELECT COUNT(*) FROM catalog_meals")
    suspend fun count(): Int

    @Query("SELECT * FROM catalog_meals WHERE id = :id")
    suspend fun getById(id: Long): CatalogMeal?
}
