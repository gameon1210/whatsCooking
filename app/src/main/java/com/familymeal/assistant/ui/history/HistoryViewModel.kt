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

// V2: grouped by date header for the UI
data class HistoryGroup(
    val label: String,      // "Today", "Yesterday", "3 Apr 2026", etc.
    val meals: List<MealEntry>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val memberRepository: MemberRepository,
    private val feedbackRepository: FeedbackRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter

    // V2: search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

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
        _filter,
        _searchQuery
    ) { allMeals, filter, query ->
        val filtered = allMeals
            .let { if (filter.mealType != null) it.filter { m -> m.mealType == filter.mealType } else it }
            .let { if (query.isBlank()) it else it.filter { m -> m.name.contains(query, ignoreCase = true) } }
        UiState.Success(filtered) as UiState<List<MealEntry>>
    }
    .catch { emit(UiState.Error(it.message ?: "Failed to load history")) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    // V2: grouped view for date-sectioned list
    val groupedMeals: StateFlow<List<HistoryGroup>> = meals
        .map { state ->
            if (state !is UiState.Success) return@map emptyList()
            val todayStart = startOfDay(System.currentTimeMillis())
            val yesterdayStart = todayStart - 86_400_000L
            state.data
                .groupBy { meal ->
                    when {
                        meal.cookedAt >= todayStart -> "Today"
                        meal.cookedAt >= yesterdayStart -> "Yesterday"
                        else -> {
                            val cal = java.util.Calendar.getInstance()
                            cal.timeInMillis = meal.cookedAt
                            val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun",
                                "Jul","Aug","Sep","Oct","Nov","Dec")
                            "${cal.get(java.util.Calendar.DAY_OF_MONTH)} " +
                                "${months[cal.get(java.util.Calendar.MONTH)]} " +
                                "${cal.get(java.util.Calendar.YEAR)}"
                        }
                    }
                }
                .map { (label, meals) -> HistoryGroup(label, meals) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setMealTypeFilter(type: MealType?) {
        _filter.value = _filter.value.copy(mealType = type)
    }

    fun setMemberFilter(memberId: Long?) {
        _filter.value = _filter.value.copy(memberId = memberId)
    }

    fun clearFilters() {
        _filter.value = HistoryFilter()
        _searchQuery.value = ""
    }

    // V2: search
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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

    private fun startOfDay(millis: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
