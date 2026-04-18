package com.familymeal.assistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.familymeal.assistant.data.db.converters.Converters
import com.familymeal.assistant.data.db.dao.*
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.db.migrations.Migration1To2
import com.familymeal.assistant.data.db.migrations.Migration2To3
import com.familymeal.assistant.data.db.migrations.Migration3To4

@Database(
    entities = [
        Member::class,
        MealEntry::class,
        MealMemberCrossRef::class,
        CatalogMeal::class,
        FeedbackSignal::class,
        RankingWeight::class,
        MemberMealScore::class,
        TiffinPlan::class,
        RecommendationEvent::class,
        MealPin::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun mealEntryDao(): MealEntryDao
    abstract fun catalogMealDao(): CatalogMealDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun rankingWeightDao(): RankingWeightDao
    abstract fun tiffinPlanDao(): TiffinPlanDao
    abstract fun recommendationEventDao(): RecommendationEventDao
    abstract fun mealPinDao(): MealPinDao

    companion object {
        val MIGRATIONS = arrayOf(Migration1To2, Migration2To3, Migration3To4)
    }
}
