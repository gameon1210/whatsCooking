package com.familymeal.assistant.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryFilter(
    val mealType: MealType? = null,
    val memberId: Long? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val memberRepository: MemberRepository,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter

    val activeMembers: StateFlow<List<Member>> = flow {
        emit(memberRepository.getActiveMembers())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val baseMeals = _filter
        .map { it.memberId }
        .distinctUntilChanged()
        .flatMapLatest { memberId ->
            if (memberId == null) {
                mealRepository.observeAllMeals()
            } else {
                mealRepository.observeMealsByMember(memberId)
            }
        }

    val meals: StateFlow<UiState<List<MealEntry>>> = combine(
        baseMeals,
        _filter
    ) { allMeals, filter ->
        val filtered = allMeals
            .let { if (filter.mealType != null) it.filter { m -> m.mealType == filter.mealType } else it }
        UiState.Success(filtered) as UiState<List<MealEntry>>
    }
    .catch { emit(UiState.Error(it.message ?: "Failed to load history")) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    fun setMealTypeFilter(type: MealType?) {
        _filter.value = _filter.value.copy(mealType = type)
    }

    fun setMemberFilter(memberId: Long?) {
        _filter.value = _filter.value.copy(memberId = memberId)
    }

    fun clearFilters() {
        _filter.value = HistoryFilter()
    }

    suspend fun getFeedbackForMeal(mealEntryId: Long): List<FeedbackSignal> =
        feedbackRepository.getFeedbackForMeal(mealEntryId)

    fun addFeedback(meal: MealEntry, signalType: FeedbackType) {
        viewModelScope.launch {
            val memberIds = mealRepository.getMemberIdsForMeal(meal.id)
            val childMemberIds = memberRepository.getActiveMembers()
                .filter { it.id in memberIds && it.birthYear != null }
                .map { it.id }
            feedbackRepository.saveFeedback(
                signal = FeedbackSignal(mealEntryId = meal.id, signalType = signalType),
                catalogMealId = meal.catalogMealId,
                mealMemberIds = memberIds,
                childMemberIds = childMemberIds
            )
        }
    }

    fun removeFeedback(meal: MealEntry, signal: FeedbackSignal) {
        viewModelScope.launch {
            val memberIds = mealRepository.getMemberIdsForMeal(meal.id)
            val childMemberIds = memberRepository.getActiveMembers()
                .filter { it.id in memberIds && it.birthYear != null }
                .map { it.id }
            feedbackRepository.removeFeedback(
                signal = signal,
                catalogMealId = meal.catalogMealId,
                mealMemberIds = memberIds,
                childMemberIds = childMemberIds
            )
        }
    }

    fun deleteMeal(mealEntryId: Long) {
        viewModelScope.launch {
            mealRepository.deleteMeal(mealEntryId)
        }
    }
}
