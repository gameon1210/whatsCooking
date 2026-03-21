package com.familymeal.assistant.di

import android.content.Context
import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.domain.classifier.GeminiImageClassifier
import com.familymeal.assistant.domain.classifier.ImageClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideImageClassifier(
        client: OkHttpClient,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): ImageClassifier = GeminiImageClassifier(
        client = client,
        baseUrl = "https://generativelanguage.googleapis.com/",
        apiKeyProvider = { settingsRepository.getGeminiApiKey() },
        context = context
    )
}
