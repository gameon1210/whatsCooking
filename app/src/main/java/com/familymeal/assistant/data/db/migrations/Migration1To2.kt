package com.familymeal.assistant.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // New tables
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tiffin_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                catalogMealId INTEGER NOT NULL,
                mealName TEXT NOT NULL,
                plannedDate INTEGER NOT NULL,
                savedAt INTEGER NOT NULL,
                loggedMealEntryId INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recommendation_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                catalogMealId INTEGER NOT NULL,
                mealContext TEXT NOT NULL,
                eventType TEXT NOT NULL,
                occurredAt INTEGER NOT NULL,
                sessionId TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )

        // CatalogMeal new columns
        db.execSQL("ALTER TABLE catalog_meals ADD COLUMN category TEXT")
        db.execSQL("ALTER TABLE catalog_meals ADD COLUMN effortLevel TEXT NOT NULL DEFAULT 'MEDIUM'")
        db.execSQL("ALTER TABLE catalog_meals ADD COLUMN leftoverFriendly INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE catalog_meals ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}
