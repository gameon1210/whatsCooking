package com.familymeal.assistant.di

import com.familymeal.assistant.data.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindMealRepository(impl: MealRepositoryImpl): MealRepository
    @Binds @Singleton abstract fun bindMemberRepository(impl: MemberRepositoryImpl): MemberRepository
    @Binds @Singleton abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository
    @Binds @Singleton abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository
    @Binds @Singleton abstract fun bindWeightRepository(impl: WeightRepositoryImpl): WeightRepository
    @Binds @Singleton abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
