package com.familymeal.assistant.ui.addmeal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.classifier.ImageClassifier
import com.familymeal.assistant.domain.model.ClassificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

    val activeMembers: StateFlow<List<Member>> = memberRepository.observeActiveMembers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // V2: recent meals for quick-re-add strip
    private val _recentMeals = MutableStateFlow<List<MealEntry>>(emptyList())
    val recentMeals: StateFlow<List<MealEntry>> = _recentMeals

    // V2: post-save feedback prompt
    private val _showPostSaveFeedback = MutableStateFlow(false)
    val showPostSaveFeedback: StateFlow<Boolean> = _showPostSaveFeedback

    private var _lastSavedMealId: Long? = null
    val lastSavedMealId: Long? get() = _lastSavedMealId

    private var _lastSavedMealName = MutableStateFlow("")
    val lastSavedMealName: StateFlow<String> = _lastSavedMealName

    private var _lastSavedCatalogMealId: Long? = null
    val lastSavedCatalogMealId: Long? get() = _lastSavedCatalogMealId

    private var lastSavedMemberIds: List<Long> = emptyList()
    private var savedEntryAwaitingClassification: MealEntry? = null
    private var classificationJob: Job? = null

    init {
        viewModelScope.launch {
            _recentMeals.value = mealRepository.getLastNMeals(5)
        }
    }

    /**
     * Starts AI classification as soon as a photo is captured/selected,
     * BEFORE save, so the suggestion can pre-fill the meal name field.
     * Save never depends on this finishing (FR-007).
     */
    fun classifyPhoto(photoUri: Uri) {
        classificationJob?.cancel()
        classificationJob = viewModelScope.launch {
            _classificationState.value = ClassificationState.InFlight
            imageClassifier.classify(photoUri)
                .catch { _classificationState.value = ClassificationState.Idle }
                .collect { result ->
                    when (result) {
                        is ClassificationResult.Success -> {
                            _classificationState.value = ClassificationState.Success(result.mealName)
                            finishPendingClassification(result.mealName)
                        }
                        is ClassificationResult.Failure -> {
                            _classificationState.value = ClassificationState.Idle
                            finishPendingClassification(null)
                        }
                    }
                }
        }
    }

    fun saveMeal(
        photoUri: Uri?,
        mealName: String,
        mealType: MealType,
        memberIds: List<Long>,
        notes: String?,
        catalogMealId: Long?
    ) {
        viewModelScope.launch {
            val stillClassifying = _classificationState.value is ClassificationState.InFlight
            val entry = MealEntry(
                name = mealName,
                photoUri = photoUri?.toString(),
                mealType = mealType,
                catalogMealId = catalogMealId,
                notes = notes?.takeIf { it.isNotBlank() },
                classificationPending = stillClassifying
            )
            val savedId = mealRepository.saveMeal(entry, memberIds)
            _lastSavedMealId = savedId
            _lastSavedMealName.value = mealName
            _lastSavedCatalogMealId = catalogMealId
            lastSavedMemberIds = memberIds

            // If classification is still running, remember the entry so the
            // result can be written back when it completes.
            savedEntryAwaitingClassification =
                if (stillClassifying) entry.copy(id = savedId) else null

            // V2: prompt for quick feedback after save
            _showPostSaveFeedback.value = true
            _recentMeals.value = mealRepository.getLastNMeals(5)
        }
    }

    /** Writes the classification result back to an already-saved entry. */
    private fun finishPendingClassification(suggestedName: String?) {
        val saved = savedEntryAwaitingClassification ?: return
        savedEntryAwaitingClassification = null
        viewModelScope.launch {
            mealRepository.updateMeal(
                saved.copy(
                    aiSuggestedName = suggestedName,
                    classificationPending = false
                )
            )
        }
    }

    // V2: save a quick feedback signal right after logging a meal.
    // catalogMealId is nullable — manually logged meals must still record feedback.
    fun saveFeedback(mealEntryId: Long, feedbackType: FeedbackType) {
        viewModelScope.launch {
            val childMemberIds = activeMembers.value
                .filter { it.id in lastSavedMemberIds && it.birthYear != null }
                .map { it.id }
            feedbackRepository.saveFeedback(
                signal = FeedbackSignal(mealEntryId = mealEntryId, signalType = feedbackType),
                catalogMealId = _lastSavedCatalogMealId,
                mealMemberIds = lastSavedMemberIds,
                childMemberIds = childMemberIds
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
        classificationJob?.cancel()
        _classificationState.value = ClassificationState.Idle
    }
}
