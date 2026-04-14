package com.familymeal.assistant.data.db.dao

import androidx.room.*
import com.familymeal.assistant.data.db.entity.RankingWeight
import kotlinx.coroutines.flow.Flow

@Dao
interface RankingWeightDao {
    @Query("SELECT * FROM ranking_weights ORDER BY signalName ASC")
    fun observeAllWeights(): Flow<List<RankingWeight>>

    @Query("SELECT * FROM ranking_weights ORDER BY signalName ASC")
    suspend fun getAllWeights(): List<RankingWeight>

    @Query("SELECT * FROM ranking_weights WHERE signalName = :name")
    suspend fun getWeight(name: String): RankingWeight?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(weights: List<RankingWeight>)

    @Update
    suspend fun update(weight: RankingWeight)

    @Query("SELECT COUNT(*) FROM ranking_weights")
    suspend fun count(): Int
}
