package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.TiffinPlan
import kotlinx.coroutines.flow.Flow

@Dao
interface TiffinPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: TiffinPlan)

    // Returns the most recently saved plan (there is at most one active plan)
    @Query("SELECT * FROM tiffin_plans ORDER BY savedAt DESC LIMIT 1")
    fun getActivePlan(): Flow<TiffinPlan?>

    @Query("DELETE FROM tiffin_plans WHERE id = :id")
    suspend fun clearPlan(id: Long)

    @Query("DELETE FROM tiffin_plans")
    suspend fun clearAll()
}
