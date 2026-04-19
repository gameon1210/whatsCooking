package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.dao.TiffinPlanDao
import com.familymeal.assistant.data.db.entity.TiffinPlan
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TiffinPlanRepositoryImpl @Inject constructor(
    private val dao: TiffinPlanDao
) : TiffinPlanRepository {
    override suspend fun savePlan(plan: TiffinPlan) = dao.upsertPlan(plan)
    override fun getActivePlan(): Flow<TiffinPlan?> = dao.getActivePlan()
    override suspend fun clearPlan(id: Long) = dao.clearPlan(id)
}
