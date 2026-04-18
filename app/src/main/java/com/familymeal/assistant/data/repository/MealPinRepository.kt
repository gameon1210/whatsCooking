package com.familymeal.assistant.data.repository

import com.familymeal.assistant.data.db.entity.MealPin
import kotlinx.coroutines.flow.Flow

interface MealPinRepository {
    suspend fun upsertPin(pin: MealPin)
    fun getPinsForWeek(startDate: Long, endDate: Long): Flow<List<MealPin>>
    suspend fun clearPin(id: Long)
    suspend fun markAsLogged(catalogMealId: Long, slotDate: Long)
}
