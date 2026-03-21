package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealMemberCrossRef
import com.familymeal.assistant.data.db.entity.MealType
import kotlinx.coroutines.flow.Flow

@Dao
interface MealEntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMealEntry(entry: MealEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<MealMemberCrossRef>)

    @Update
    suspend fun updateMealEntry(entry: MealEntry)

    @Query("SELECT * FROM meal_entries ORDER BY cookedAt DESC")
    fun observeAllMeals(): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE mealType = :type ORDER BY cookedAt DESC")
    fun observeMealsByType(type: MealType): Flow<List<MealEntry>>

    @Query("""
        SELECT me.* FROM meal_entries me
        INNER JOIN meal_member_cross_refs mmcr ON me.id = mmcr.mealEntryId
        WHERE mmcr.memberId = :memberId
        ORDER BY me.cookedAt DESC
    """)
    fun observeMealsByMember(memberId: Long): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entries WHERE catalogMealId = :catalogMealId ORDER BY cookedAt DESC LIMIT 1")
    suspend fun getLastCookedForCatalogMeal(catalogMealId: Long): MealEntry?

    @Query("SELECT memberId FROM meal_member_cross_refs WHERE mealEntryId = :mealEntryId")
    suspend fun getMemberIdsForMeal(mealEntryId: Long): List<Long>

    @Query("""
        UPDATE meal_entries SET classificationPending = 0
        WHERE classificationPending = 1 AND cookedAt < :cutoffMillis
    """)
    suspend fun reconcilePendingClassifications(cutoffMillis: Long)
}
