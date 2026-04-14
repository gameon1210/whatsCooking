package com.familymeal.assistant.ui.addmeal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.classifier.ImageClassifier
import com.familymeal.assistant.domain.model.ClassificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ClassificationState {
    object Idle : ClassificationState()
    object InFlight : ClassificationState()
    data class Success(val suggestedName: String) : ClassificationState()
}

@HiltViewModel
class AddMealViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val memberRepository: MemberRepository,
    private val settingsRepository: SettingsRepository,
    private val imageClassifier: ImageClassifier
) : ViewModel() {

    private val _classificationState = MutableStateFlow<ClassificationState>(ClassificationState.Idle)
    val classificationState: StateFlow<ClassificationState> = _classificationState

    private val _showApiKeyBanner = MutableStateFlow(
        !settingsRepository.isApiKeyBannerDismissed() && settingsRepository.getGeminiApiKey() == null
    )
    val showApiKeyBanner: StateFlow<Boolean> = _showApiKeyBanner

    val activeMembers: StateFlow<List<com.familymeal.assistant.data.db.entity.Member>> = flow {
        emit(memberRepository.getActiveMembers())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveMeal(
        photoUri: Uri?,
        mealName: String,
        mealType: MealType,
        memberIds: List<Long>,
        catalogMealId: Long?
    ) {
        viewModelScope.launch {
            val entry = MealEntry(
                name = mealName,
                photoUri = photoUri?.toString(),
                mealType = mealType,
                catalogMealId = catalogMealId,
                classificationPending = photoUri != null
            )
            val savedId = mealRepository.saveMeal(entry, memberIds)

            if (photoUri != null) {
                startClassification(photoUri, savedId)
            }
        }
    }

    fun startClassification(photoUri: Uri, savedMealId: Long) {
        viewModelScope.launch {
            _classificationState.value = ClassificationState.InFlight
            imageClassifier.classify(photoUri)
                .collect { result ->
                    when (result) {
                        is ClassificationResult.Success -> {
                            _classificationState.value = ClassificationState.Success(result.mealName)
                        }
                        is ClassificationResult.Failure -> {
                            _classificationState.value = ClassificationState.Idle
                        }
                    }
                }
        }
    }

    fun dismissApiKeyBanner() {
        settingsRepository.dismissApiKeyBanner()
        _showApiKeyBanner.value = false
    }

    fun resetClassificationState() {
        _classificationState.value = ClassificationState.Idle
    }
}
