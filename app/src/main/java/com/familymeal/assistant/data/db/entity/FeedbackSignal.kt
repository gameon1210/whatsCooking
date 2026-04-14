package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feedback_signals",
    foreignKeys = [
        ForeignKey(
            entity = MealEntry::class,
            parentColumns = ["id"],
            childColumns = ["mealEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mealEntryId"), Index("memberId")]
)
data class FeedbackSignal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealEntryId: Long,
    val memberId: Long? = null,
    val signalType: FeedbackType,
    val createdAt: Long = System.currentTimeMillis()
)
