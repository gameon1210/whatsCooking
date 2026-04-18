package com.familymeal.assistant.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_pins (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                catalogMealId INTEGER NOT NULL,
                mealName TEXT NOT NULL,
                slotDate INTEGER NOT NULL,
                mealType TEXT NOT NULL,
                isPinned INTEGER NOT NULL DEFAULT 1,
                isLogged INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
