package com.familymeal.assistant.data.repository

import android.content.Context
import com.familymeal.assistant.data.db.dao.RankingWeightDao
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

data class WeightConfig(val signalName: String, val defaultValue: Float)

class WeightRepositoryImpl @Inject constructor(
    private val dao: RankingWeightDao,
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WeightRepository {

    override suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        val json = context.assets.open("ranking_config.json").bufferedReader().readText()
        val type = object : TypeToken<List<WeightConfig>>() {}.type
        val configs: List<WeightConfig> = gson.fromJson(json, type)
        dao.insertAll(configs.map { RankingWeight(it.signalName, it.defaultValue, it.defaultValue) })
    }

    override fun observeAllWeights() = dao.observeAllWeights()
    override suspend fun getAllWeights() = dao.getAllWeights()
    override suspend fun updateWeight(weight: RankingWeight) = dao.update(weight)
    override suspend fun resetToDefault(signalName: String) {
        val w = dao.getWeight(signalName) ?: return
        dao.update(w.copy(value = w.defaultValue, lastNudgedAt = System.currentTimeMillis()))
    }
}
