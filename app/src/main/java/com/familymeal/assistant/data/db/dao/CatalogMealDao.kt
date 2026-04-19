package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.CatalogMeal
import kotlinx.coroutines.flow.Flow

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

    // V2: Favorites
    @Query("UPDATE catalog_meals SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT * FROM catalog_meals WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(): Flow<List<CatalogMeal>>

    // V2: Dependable meals — cooked ≥3 times with ≥minMakeAgain signals
    @Query(
        """
        SELECT cm.* FROM catalog_meals cm
        INNER JOIN meal_entries me ON me.catalogMealId = cm.id
        INNER JOIN feedback_signals fs ON fs.mealEntryId = me.id AND fs.signalType = 'MakeAgain'
        GROUP BY cm.id
        HAVING COUNT(DISTINCT me.id) >= 3 AND COUNT(fs.id) >= :minMakeAgain
        ORDER BY cm.name ASC
        """
    )
    fun getDependableMeals(minMakeAgain: Int = 2): Flow<List<CatalogMeal>>
}
