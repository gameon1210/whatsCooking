package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.RankingWeight
import kotlinx.coroutines.flow.Flow

interface WeightRepository {
    suspend fun seedIfEmpty()
    fun observeAllWeights(): Flow<List<RankingWeight>>
    suspend fun getAllWeights(): List<RankingWeight>
    suspend fun updateWeight(weight: RankingWeight)
    suspend fun resetToDefault(signalName: String)
}
