package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.TiffinPlan
import kotlinx.coroutines.flow.Flow

interface TiffinPlanRepository {
    suspend fun savePlan(plan: TiffinPlan)
    fun getActivePlan(): Flow<TiffinPlan?>
    suspend fun clearPlan(id: Long)
}
