package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SpicyTolerance { NONE, MILD, FULL }

@Entity(tableName = "members", indices = [Index("isActive")])
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dietType: DietType,
    val birthYear: Int? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    // V2 columns
    val spicyTolerance: SpicyTolerance = SpicyTolerance.FULL,
    val portablePreference: Boolean = false,
    val sundaySpecial: Boolean = false,
    val schoolGoing: Boolean = false
)
