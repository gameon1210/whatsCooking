package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "member_meal_scores",
    primaryKeys = ["memberId", "catalogMealId"],
    indices = [Index("catalogMealId")],
    foreignKeys = [
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CatalogMeal::class,
            parentColumns = ["id"],
            childColumns = ["catalogMealId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemberMealScore(
    val memberId: Long,
    val catalogMealId: Long,
    val positiveSignals: Int = 0,
    val negativeSignals: Int = 0,
    val timesCooked: Int = 0,
    val lastCookedAt: Long? = null
)
