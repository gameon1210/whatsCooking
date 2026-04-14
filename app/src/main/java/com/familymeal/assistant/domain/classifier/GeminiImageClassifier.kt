package com.familymeal.assistant.domain.classifier

import android.content.Context
import android.net.Uri
import com.familymeal.assistant.domain.model.ClassificationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GeminiImageClassifier @Inject constructor(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKeyProvider: () -> String?,
    @ApplicationContext private val context: Context? = null
) : ImageClassifier {

    companion object {
        private const val PROMPT = "Identify the meal in this photo. Return only the meal name, be specific (e.g. 'Dal Makhani' not 'Indian food')."
    }

    override fun classify(photoUri: Uri): Flow<ClassificationResult> = flow {
        val bytes = withContext(Dispatchers.IO) {
            val resolver = context?.contentResolver ?: return@withContext null
            try {
                resolver.openInputStream(photoUri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
        }
        if (bytes == null || bytes.isEmpty()) {
            emit(ClassificationResult.Failure)
            return@flow
        }
        classifyBytes(bytes).collect { emit(it) }
    }

    /** Testable entry point — accepts raw bytes directly */
    fun classifyBytes(bytes: ByteArray): Flow<ClassificationResult> = flow {
        val apiKey = apiKeyProvider()
        if (apiKey == null) {
            emit(ClassificationResult.Failure)
            return@flow
        }

        val base64 = Base64.getEncoder().encodeToString(bytes)
        val body = """
            {
              "contents": [{
                "parts": [
                  {"text": "$PROMPT"},
                  {"inline_data": {"mime_type": "image/jpeg", "data": "$base64"}}
                ]
              }]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("${baseUrl}v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val result = withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext ClassificationResult.Failure
                val json = response.body?.string() ?: return@withContext ClassificationResult.Failure
                val text = JsonParser.parseString(json)
                    .asJsonObject
                    .getAsJsonArray("candidates")
                    .get(0).asJsonObject
                    .getAsJsonObject("content")
                    .getAsJsonArray("parts")
                    .get(0).asJsonObject
                    .get("text").asString
                    .trim()
                ClassificationResult.Success(text)
            } catch (e: Exception) {
                ClassificationResult.Failure
            }
        }
        emit(result)
    }
}
