package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.classifier.AiProvider
import com.familymeal.assistant.ui.common.UiState
import com.familymeal.assistant.ui.settings.SettingsViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var weightRepo: WeightRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var vm: SettingsViewModel

    private val weights = listOf(
        RankingWeight("recency", 0.40f, 0.40f),
        RankingWeight("makeAgain", 0.30f, 0.30f)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        weightRepo = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        every { weightRepo.observeAllWeights() } returns flowOf(weights)
        every { settingsRepo.getExplorationRatio() } returns 0.20f
        every { settingsRepo.getAiProvider() } returns AiProvider.Gemini
        every { settingsRepo.getAiModel() } returns "gemini-2.0-flash"
        every { settingsRepo.getAiApiKey() } returns null
        vm = SettingsViewModel(weightRepo, settingsRepo)
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `weights state loads from repository`() = runTest {
        vm.weights.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            assertEquals(2, (state as UiState.Success).data.size)
        }
    }

    @Test
    fun `updateWeight calls repository`() = runTest {
        val updated = weights[0].copy(value = 0.50f)
        vm.updateWeight(updated)
        coVerify { weightRepo.updateWeight(updated) }
    }

    @Test
    fun `resetWeight calls repository resetToDefault`() = runTest {
        vm.resetWeight("recency")
        coVerify { weightRepo.resetToDefault("recency") }
    }

    @Test
    fun `setExplorationRatio updates settings`() {
        vm.setExplorationRatio(0.25f)
        verify { settingsRepo.setExplorationRatio(0.25f) }
        assertEquals(0.25f, vm.explorationRatio.value, 0.001f)
    }

    @Test
    fun `explorationRatio clamped to 0_10 to 0_30`() {
        vm.setExplorationRatio(0.50f)
        assertEquals(0.30f, vm.explorationRatio.value, 0.001f)
        vm.setExplorationRatio(0.05f)
        assertEquals(0.10f, vm.explorationRatio.value, 0.001f)
    }

    @Test
    fun `saveAiConfig calls settingsRepository and updates state`() {
        vm.saveAiConfig(AiProvider.Claude, "claude-3-5-sonnet-latest", "my-test-key")
        verify { settingsRepo.setAiProvider(AiProvider.Claude) }
        verify { settingsRepo.setAiModel("claude-3-5-sonnet-latest") }
        verify { settingsRepo.setAiApiKey("my-test-key") }
        assertEquals(AiProvider.Claude, vm.aiProvider.value)
        assertEquals("claude-3-5-sonnet-latest", vm.aiModel.value)
        assertEquals("my-test-key", vm.apiKey.value)
    }

    @Test
    fun `clearApiKey calls settingsRepository and clears state`() {
        every { settingsRepo.getAiApiKey() } returns "saved-key"
        vm = SettingsViewModel(weightRepo, settingsRepo)
        vm.clearApiKey()
        verify { settingsRepo.clearAiApiKey() }
        assertNull(vm.apiKey.value)
    }
}
