package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.MealPin
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPin(pin: MealPin)

    @Query("SELECT * FROM meal_pins WHERE slotDate BETWEEN :startDate AND :endDate ORDER BY slotDate ASC")
    fun getPinsForWeek(startDate: Long, endDate: Long): Flow<List<MealPin>>

    @Query("DELETE FROM meal_pins WHERE id = :id")
    suspend fun clearPin(id: Long)

    @Query("UPDATE meal_pins SET isLogged = 1 WHERE catalogMealId = :catalogMealId AND slotDate = :slotDate")
    suspend fun markAsLogged(catalogMealId: Long, slotDate: Long)
}
