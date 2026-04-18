package com.familymeal.assistant.domain.classifier

import android.content.Context
import android.net.Uri
import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.domain.model.ClassificationResult
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.Base64
import javax.inject.Inject

class ConfigurableImageClassifier @Inject constructor(
    private val client: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context? = null,
    private val openAiBaseUrl: String = OPENAI_BASE_URL,
    private val claudeBaseUrl: String = CLAUDE_BASE_URL,
    private val geminiBaseUrl: String = GEMINI_BASE_URL
) : ImageClassifier {

    override fun classify(photoUri: Uri): Flow<ClassificationResult> = flow {
        val bytes = withContext(Dispatchers.IO) {
            val resolver = context?.contentResolver ?: return@withContext null
            try {
                resolver.openInputStream(photoUri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
        if (bytes == null || bytes.isEmpty()) {
            emit(ClassificationResult.Failure)
            return@flow
        }
        classifyBytes(bytes).collect { emit(it) }
    }

    fun classifyBytes(bytes: ByteArray): Flow<ClassificationResult> = flow {
        if (bytes.isEmpty()) {
            emit(ClassificationResult.Failure)
            return@flow
        }

        val config = settingsRepository.getAiProviderConfig()
        val apiKey = config.apiKey?.trim().takeUnless { it.isNullOrBlank() }
        if (apiKey == null) {
            emit(ClassificationResult.Failure)
            return@flow
        }

        val result = withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(buildRequest(config, apiKey, bytes)).execute()
                if (!response.isSuccessful) return@withContext ClassificationResult.Failure

                val json = response.body?.string().orEmpty()
                parseResponse(config.provider, json)
            } catch (_: Exception) {
                ClassificationResult.Failure
            }
        }

        emit(result)
    }

    private fun buildRequest(
        config: AiProviderConfig,
        apiKey: String,
        bytes: ByteArray
    ): Request {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val body = when (config.provider) {
            AiProvider.ChatGpt -> buildOpenAiBody(config.model, base64)
            AiProvider.Claude -> buildClaudeBody(config.model, base64)
            AiProvider.Gemini -> buildGeminiBody(base64)
        }

        return when (config.provider) {
            AiProvider.ChatGpt -> Request.Builder()
                .url("${openAiBaseUrl.trimEnd('/')}/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            AiProvider.Claude -> Request.Builder()
                .url("${claudeBaseUrl.trimEnd('/')}/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            AiProvider.Gemini -> Request.Builder()
                .url("${geminiBaseUrl.trimEnd('/')}/v1beta/models/${config.model}:generateContent?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }
    }

    private fun parseResponse(provider: AiProvider, json: String): ClassificationResult = try {
        val root = JsonParser.parseString(json).asJsonObject
        val text = when (provider) {
            AiProvider.ChatGpt -> root
                .getAsJsonArray("choices")
                .firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")
                ?.normalizedText()

            AiProvider.Claude -> root
                .getAsJsonArray("content")
                .firstOrNull()
                ?.asJsonObject
                ?.get("text")
                ?.normalizedText()

            AiProvider.Gemini -> root
                .getAsJsonArray("candidates")
                .firstOrNull()
                ?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.firstOrNull()
                ?.asJsonObject
                ?.get("text")
                ?.normalizedText()
        }

        if (text.isNullOrBlank()) ClassificationResult.Failure else ClassificationResult.Success(text)
    } catch (_: Exception) {
        ClassificationResult.Failure
    }

    private fun buildGeminiBody(base64: String): String = """
        {
          "contents": [{
            "parts": [
              {"text": "$PROMPT"},
              {"inline_data": {"mime_type": "image/jpeg", "data": "$base64"}}
            ]
          }]
        }
    """.trimIndent()

    private fun buildOpenAiBody(model: String, base64: String): String = """
        {
          "model": "$model",
          "messages": [{
            "role": "user",
            "content": [
              {"type": "text", "text": "$PROMPT"},
              {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,$base64"}}
            ]
          }],
          "max_tokens": 60
        }
    """.trimIndent()

    private fun buildClaudeBody(model: String, base64: String): String = """
        {
          "model": "$model",
          "max_tokens": 60,
          "messages": [{
            "role": "user",
            "content": [
              {"type": "text", "text": "$PROMPT"},
              {
                "type": "image",
                "source": {
                  "type": "base64",
                  "media_type": "image/jpeg",
                  "data": "$base64"
                }
              }
            ]
          }]
        }
    """.trimIndent()

    private fun JsonElement.normalizedText(): String? = when {
        isJsonNull -> null
        isJsonPrimitive -> asString.trim()
        else -> toString().trim()
    }

    companion object {
        private const val PROMPT =
            "Identify the meal in this photo. Return only the meal name, be specific (for example 'Dal Makhani' not 'Indian food')."
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val OPENAI_BASE_URL = "https://api.openai.com/"
        private const val CLAUDE_BASE_URL = "https://api.anthropic.com/"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    }
}
