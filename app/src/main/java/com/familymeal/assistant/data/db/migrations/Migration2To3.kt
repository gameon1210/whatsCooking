package com.familymeal.assistant.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE members ADD COLUMN spicyTolerance TEXT NOT NULL DEFAULT 'FULL'")
        db.execSQL("ALTER TABLE members ADD COLUMN portablePreference INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE members ADD COLUMN sundaySpecial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE members ADD COLUMN schoolGoing INTEGER NOT NULL DEFAULT 0")
    }
}
