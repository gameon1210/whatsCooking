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

    val meals: StateFlow<UiState<List<MealEntry>>> = combine(
        mealRepository.observeAllMeals(),
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

    fun addFeedback(
        mealEntryId: Long,
        catalogMealId: Long?,
        signalType: FeedbackType,
        memberIds: List<Long>
    ) {
        viewModelScope.launch {
            feedbackRepository.saveFeedback(
                signal = FeedbackSignal(mealEntryId = mealEntryId, signalType = signalType),
                catalogMealId = catalogMealId,
                mealMemberIds = memberIds,
                childMemberIds = emptyList()
            )
        }
    }
}
