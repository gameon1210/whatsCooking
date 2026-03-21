package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "meal_member_cross_refs",
    primaryKeys = ["mealEntryId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = MealEntry::class,
            parentColumns = ["id"],
            childColumns = ["mealEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Member::class,
            parentColumns = ["id"],
            childColumns = ["memberId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("memberId")]
)
data class MealMemberCrossRef(
    val mealEntryId: Long,
    val memberId: Long
)
