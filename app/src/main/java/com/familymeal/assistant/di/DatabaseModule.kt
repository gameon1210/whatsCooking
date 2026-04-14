package com.familymeal.assistant.di

import android.content.Context
import androidx.room.Room
import com.familymeal.assistant.data.db.AppDatabase
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "family_meal.db")
            .build()

    @Provides fun provideMemberDao(db: AppDatabase) = db.memberDao()
    @Provides fun provideMealEntryDao(db: AppDatabase) = db.mealEntryDao()
    @Provides fun provideCatalogMealDao(db: AppDatabase) = db.catalogMealDao()
    @Provides fun provideFeedbackDao(db: AppDatabase) = db.feedbackDao()
    @Provides fun provideRankingWeightDao(db: AppDatabase) = db.rankingWeightDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
