package com.familymeal.assistant.domain

import com.familymeal.assistant.domain.classifier.GeminiImageClassifier
import com.familymeal.assistant.domain.model.ClassificationResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test
import java.util.concurrent.TimeUnit

class ImageClassifierTest {

    private lateinit var server: MockWebServer
    private lateinit var classifier: GeminiImageClassifier

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        classifier = GeminiImageClassifier(
            client = client,
            baseUrl = server.url("/").toString(),
            apiKeyProvider = { "test-key" }
        )
    }

    @After fun teardown() = server.shutdown()

    @Test
    fun `success response emits Success with meal name`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"candidates":[{"content":{"parts":[{"text":"Dal Makhani"}]}}]}""")
            .setResponseCode(200))

        classifier.classifyBytes(byteArrayOf(0x1, 0x2)).test {
            val result = awaitItem()
            assertTrue(result is ClassificationResult.Success)
            assertEquals("Dal Makhani", (result as ClassificationResult.Success).mealName)
            awaitComplete()
        }
    }

    @Test
    fun `HTTP 400 emits Failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))
        classifier.classifyBytes(byteArrayOf()).test {
            assertTrue(awaitItem() is ClassificationResult.Failure)
            awaitComplete()
        }
    }

    @Test
    fun `absent API key emits Failure without network call`() = runTest {
        val noKeyClassifier = GeminiImageClassifier(
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            apiKeyProvider = { null }
        )
        noKeyClassifier.classifyBytes(byteArrayOf()).test {
            assertTrue(awaitItem() is ClassificationResult.Failure)
            awaitComplete()
        }
        assertEquals(0, server.requestCount)  // no network call made
    }
}
