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
    private val feedbackRepository: FeedbackRepository,
    private val settingsRepository: SettingsRepository,
    private val imageClassifier: ImageClassifier
) : ViewModel() {

    private val _classificationState = MutableStateFlow<ClassificationState>(ClassificationState.Idle)
    val classificationState: StateFlow<ClassificationState> = _classificationState

    private val _showApiKeyBanner = MutableStateFlow(
        !settingsRepository.isApiKeyBannerDismissed() && settingsRepository.getAiApiKey() == null
    )
    val showApiKeyBanner: StateFlow<Boolean> = _showApiKeyBanner

    val activeMembers: StateFlow<List<Member>> = flow {
        emit(memberRepository.getActiveMembers())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // V2: recent meals for quick-re-add strip
    private val _recentMeals = MutableStateFlow<List<MealEntry>>(emptyList())
    val recentMeals: StateFlow<List<MealEntry>> = _recentMeals

    // V2: post-save feedback prompt
    private val _showPostSaveFeedback = MutableStateFlow(false)
    val showPostSaveFeedback: StateFlow<Boolean> = _showPostSaveFeedback

    private var _lastSavedMealId: Long? = null
    val lastSavedMealId: Long? get() = _lastSavedMealId

    private var _lastSavedCatalogMealId: Long? = null
    val lastSavedCatalogMealId: Long? get() = _lastSavedCatalogMealId

    init {
        viewModelScope.launch {
            _recentMeals.value = mealRepository.getLastNMeals(5)
        }
    }

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
            _lastSavedMealId = savedId
            _lastSavedCatalogMealId = catalogMealId

            if (photoUri != null) {
                startClassification(photoUri, savedId)
            }

            // V2: prompt for quick feedback after save
            _showPostSaveFeedback.value = true
            _recentMeals.value = mealRepository.getLastNMeals(5)
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

    // V2: save a quick feedback signal right after logging a meal
    fun saveFeedback(mealEntryId: Long, feedbackType: FeedbackType) {
        val catalogMealId = _lastSavedCatalogMealId ?: return
        viewModelScope.launch {
            val signal = FeedbackSignal(mealEntryId = mealEntryId, signalType = feedbackType)
            feedbackRepository.saveFeedback(
                signal = signal,
                catalogMealId = catalogMealId,
                mealMemberIds = emptyList(),
                childMemberIds = emptyList()
            )
        }
    }

    fun dismissPostSaveFeedback() {
        _showPostSaveFeedback.value = false
    }

    fun dismissApiKeyBanner() {
        settingsRepository.dismissApiKeyBanner()
        _showApiKeyBanner.value = false
    }

    fun resetClassificationState() {
        _classificationState.value = ClassificationState.Idle
    }
}
