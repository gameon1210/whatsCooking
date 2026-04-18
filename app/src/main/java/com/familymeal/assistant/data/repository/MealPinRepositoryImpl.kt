package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.dao.MealPinDao
import com.familymeal.assistant.data.db.entity.MealPin
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MealPinRepositoryImpl @Inject constructor(
    private val dao: MealPinDao
) : MealPinRepository {
    override suspend fun upsertPin(pin: MealPin) = dao.upsertPin(pin)
    override fun getPinsForWeek(startDate: Long, endDate: Long) = dao.getPinsForWeek(startDate, endDate)
    override suspend fun clearPin(id: Long) = dao.clearPin(id)
    override suspend fun markAsLogged(catalogMealId: Long, slotDate: Long) =
        dao.markAsLogged(catalogMealId, slotDate)
}
