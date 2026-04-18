package com.familymeal.assistant.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.familymeal.assistant.domain.classifier.AiProvider
import com.familymeal.assistant.domain.classifier.AiProviderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    override fun getAiProvider(): AiProvider =
        AiProvider.fromStoredValue(prefs.getString(KEY_AI_PROVIDER, null))

    override fun setAiProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider.name).apply()
    }

    override fun getAiModel(): String =
        prefs.getString(KEY_AI_MODEL, null)?.takeIf { it.isNotBlank() } ?: getAiProvider().defaultModel

    override fun setAiModel(model: String) {
        prefs.edit().putString(KEY_AI_MODEL, model.trim()).apply()
    }

    override fun getAiApiKey(): String? =
        encryptedPrefs.getString(KEY_AI_API, encryptedPrefs.getString(KEY_GEMINI_API, null))

    override fun setAiApiKey(key: String) {
        encryptedPrefs.edit()
            .putString(KEY_AI_API, key)
            .remove(KEY_GEMINI_API)
            .apply()
    }

    override fun clearAiApiKey() {
        encryptedPrefs.edit()
            .remove(KEY_AI_API)
            .remove(KEY_GEMINI_API)
            .apply()
    }

    override fun getAiProviderConfig(): AiProviderConfig = AiProviderConfig(
        provider = getAiProvider(),
        model = getAiModel(),
        apiKey = getAiApiKey()
    )

    override fun getExplorationRatio(): Float = prefs.getFloat(KEY_EXPLORATION, 0.20f)
    override fun setExplorationRatio(ratio: Float) = prefs.edit().putFloat(KEY_EXPLORATION, ratio).apply()

    override fun isApiKeyBannerDismissed(): Boolean = prefs.getBoolean(KEY_BANNER_DISMISSED, false)
    override fun dismissApiKeyBanner() = prefs.edit().putBoolean(KEY_BANNER_DISMISSED, true).apply()

    override fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    override fun markOnboardingComplete() = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()

    companion object {
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_AI_MODEL = "ai_model"
        private const val KEY_AI_API = "ai_api_key"
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_EXPLORATION = "exploration_ratio"
        private const val KEY_BANNER_DISMISSED = "api_key_banner_dismissed"
        private const val KEY_ONBOARDING_DONE = "onboarding_complete"
    }
}
