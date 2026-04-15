package com.familymeal.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.data.repository.WeightRepository
import com.familymeal.assistant.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val weights: StateFlow<UiState<List<RankingWeight>>> = weightRepository.observeAllWeights()
        .map<List<RankingWeight>, UiState<List<RankingWeight>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load weights")) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    private val _explorationRatio = MutableStateFlow(settingsRepository.getExplorationRatio())
    val explorationRatio: StateFlow<Float> = _explorationRatio

    private val _apiKey = MutableStateFlow(settingsRepository.getGeminiApiKey())
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    fun updateWeight(weight: RankingWeight) {
        viewModelScope.launch { weightRepository.updateWeight(weight) }
    }

    fun resetWeight(signalName: String) {
        viewModelScope.launch { weightRepository.resetToDefault(signalName) }
    }

    fun setExplorationRatio(ratio: Float) {
        val clamped = ratio.coerceIn(0.10f, 0.30f)
        _explorationRatio.value = clamped
        settingsRepository.setExplorationRatio(clamped)
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        settingsRepository.setGeminiApiKey(trimmed)
        _apiKey.value = trimmed
    }

    fun clearApiKey() {
        settingsRepository.clearGeminiApiKey()
        _apiKey.value = null
    }
}
