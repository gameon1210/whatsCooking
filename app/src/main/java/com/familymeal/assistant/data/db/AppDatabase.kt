package com.familymeal.assistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.familymeal.assistant.data.db.converters.Converters
import com.familymeal.assistant.data.db.dao.*
import com.familymeal.assistant.data.db.entity.*

@Database(
    entities = [
        Member::class,
        MealEntry::class,
        MealMemberCrossRef::class,
        CatalogMeal::class,
        FeedbackSignal::class,
        RankingWeight::class,
        MemberMealScore::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun catalogMealDao(): CatalogMealDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun rankingWeightDao(): RankingWeightDao
}
