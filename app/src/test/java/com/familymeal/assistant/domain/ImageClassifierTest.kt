package com.familymeal.assistant.domain

import app.cash.turbine.test
import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.domain.classifier.AiProvider
import com.familymeal.assistant.domain.classifier.AiProviderConfig
import com.familymeal.assistant.domain.classifier.ConfigurableImageClassifier
import com.familymeal.assistant.domain.model.ClassificationResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ImageClassifierTest {

    private lateinit var server: MockWebServer
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var classifier: ConfigurableImageClassifier

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        settingsRepository = mockk()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        classifier = ConfigurableImageClassifier(
            client = client,
            settingsRepository = settingsRepository,
            openAiBaseUrl = server.url("/").toString(),
            claudeBaseUrl = server.url("/").toString(),
            geminiBaseUrl = server.url("/").toString()
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `gemini response emits Success with meal name`() = runTest {
        every { settingsRepository.getAiProviderConfig() } returns AiProviderConfig(
            provider = AiProvider.Gemini,
            model = "gemini-2.0-flash",
            apiKey = "test-key"
        )
        server.enqueue(
            MockResponse()
                .setBody("""{"candidates":[{"content":{"parts":[{"text":"Dal Makhani"}]}}]}""")
                .setResponseCode(200)
        )

        classifier.classifyBytes(byteArrayOf(0x1, 0x2)).test {
            val result = awaitItem()
            assertTrue(result is ClassificationResult.Success)
            assertEquals("Dal Makhani", (result as ClassificationResult.Success).mealName)
            awaitComplete()
        }
    }

    @Test
    fun `chatgpt response emits Success with meal name`() = runTest {
        every { settingsRepository.getAiProviderConfig() } returns AiProviderConfig(
            provider = AiProvider.ChatGpt,
            model = "gpt-4.1-mini",
            apiKey = "test-key"
        )
        server.enqueue(
            MockResponse()
                .setBody("""{"choices":[{"message":{"content":"Paneer Butter Masala"}}]}""")
                .setResponseCode(200)
        )

        classifier.classifyBytes(byteArrayOf(0x1, 0x2)).test {
            val result = awaitItem()
            assertTrue(result is ClassificationResult.Success)
            assertEquals("Paneer Butter Masala", (result as ClassificationResult.Success).mealName)
            awaitComplete()
        }
    }

    @Test
    fun `claude response emits Success with meal name`() = runTest {
        every { settingsRepository.getAiProviderConfig() } returns AiProviderConfig(
            provider = AiProvider.Claude,
            model = "claude-3-5-sonnet-latest",
            apiKey = "test-key"
        )
        server.enqueue(
            MockResponse()
                .setBody("""{"content":[{"text":"Aloo Paratha"}]}""")
                .setResponseCode(200)
        )

        classifier.classifyBytes(byteArrayOf(0x1, 0x2)).test {
            val result = awaitItem()
            assertTrue(result is ClassificationResult.Success)
            assertEquals("Aloo Paratha", (result as ClassificationResult.Success).mealName)
            awaitComplete()
        }
    }

    @Test
    fun `absent API key emits Failure without network call`() = runTest {
        every { settingsRepository.getAiProviderConfig() } returns AiProviderConfig(
            provider = AiProvider.Gemini,
            model = "gemini-2.0-flash",
            apiKey = null
        )

        classifier.classifyBytes(byteArrayOf(0x1, 0x2)).test {
            assertTrue(awaitItem() is ClassificationResult.Failure)
            awaitComplete()
        }
        assertEquals(0, server.requestCount)
    }
}
