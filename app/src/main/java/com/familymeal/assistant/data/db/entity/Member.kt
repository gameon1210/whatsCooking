package com.familymeal.assistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "members", indices = [Index("isActive")])
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dietType: DietType,
    val birthYear: Int? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
