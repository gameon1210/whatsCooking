package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ranking_weights")
data class RankingWeight(
    @PrimaryKey val signalName: String,
    val value: Float,
    val defaultValue: Float,
    val lastNudgedAt: Long = 0L
)
