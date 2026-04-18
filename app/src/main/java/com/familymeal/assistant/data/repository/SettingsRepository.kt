package com.familymeal.assistant.data.repository

import com.familymeal.assistant.domain.classifier.AiProvider
import com.familymeal.assistant.domain.classifier.AiProviderConfig

interface SettingsRepository {
    fun getAiProvider(): AiProvider
    fun setAiProvider(provider: AiProvider)
    fun getAiModel(): String
    fun setAiModel(model: String)
    fun getAiApiKey(): String?
    fun setAiApiKey(key: String)
    fun clearAiApiKey()
    fun getAiProviderConfig(): AiProviderConfig
    fun getExplorationRatio(): Float         // default 0.20
    fun setExplorationRatio(ratio: Float)
    fun isApiKeyBannerDismissed(): Boolean
    fun dismissApiKeyBanner()
    fun isOnboardingComplete(): Boolean
    fun markOnboardingComplete()
}
