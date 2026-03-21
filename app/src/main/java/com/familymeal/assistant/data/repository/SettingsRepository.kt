package com.familymeal.assistant.data.repository

interface SettingsRepository {
    fun getGeminiApiKey(): String?
    fun setGeminiApiKey(key: String)
    fun clearGeminiApiKey()
    fun getExplorationRatio(): Float         // default 0.20
    fun setExplorationRatio(ratio: Float)
    fun isApiKeyBannerDismissed(): Boolean
    fun dismissApiKeyBanner()
    fun isOnboardingComplete(): Boolean
    fun markOnboardingComplete()
}
