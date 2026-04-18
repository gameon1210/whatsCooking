package com.familymeal.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.engine.*
import com.familymeal.assistant.domain.model.*
import com.familymeal.assistant.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val memberRepository: MemberRepository,
    private val catalogRepository: CatalogRepository,
    private val feedbackRepository: FeedbackRepository,
    private val weightRepository: WeightRepository,
    private val settingsRepository: SettingsRepository,
    private val rankingEngine: RankingEngine,
    private val weightAdapter: WeightAdapter,
    private val reasonGenerator: ReasonGenerator
) : ViewModel() {

    private val _selectedMealType = MutableStateFlow(defaultMealType())
    val selectedMealType: StateFlow<MealType> = _selectedMealType

    // null = Family (all active members); non-null = specific member IDs
    private val _selectedMemberIds = MutableStateFlow<List<Long>?>(null)
    val selectedMemberIds: StateFlow<List<Long>?> = _selectedMemberIds

    private val _suggestions = MutableStateFlow<UiState<List<RankedMeal>>>(UiState.Loading)
    val suggestions: StateFlow<UiState<List<RankedMeal>>> = _suggestions

    val activeMembers: StateFlow<List<Member>> = memberRepository.observeActiveMembers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        combine(_selectedMealType, _selectedMemberIds) { _, _ -> Unit }
            .onEach { loadSuggestions() }
            .launchIn(viewModelScope)
    }

    fun selectMealType(type: MealType) { _selectedMealType.value = type }

    fun selectAudience(memberIds: List<Long>?) { _selectedMemberIds.value = memberIds }

    private fun loadSuggestions() {
        viewModelScope.launch {
            _suggestions.value = UiState.Loading
            try {
                val allMembers = memberRepository.getActiveMembers()
                val audienceMembers = _selectedMemberIds.value
                    ?.mapNotNull { id -> allMembers.find { it.id == id } }
                    ?: allMembers

                val catalog = catalogRepository.getAllMeals()
                val weights = weightRepository.getAllWeights()
                val weightMap = weights.toWeightMap()
                val explorationRatio = settingsRepository.getExplorationRatio()
                val catalogMealIds = catalog.map { it.id }
                val feedbackCounts = feedbackRepository.getFeedbackCounts(catalogMealIds)
                val memberScores = feedbackRepository.getMemberMealScores(audienceMembers.map { it.id })

                val lastCookedAt = buildMap<Long, Long> {
                    catalog.forEach { meal ->
                        mealRepository.getLastCookedForCatalogMeal(meal.id)
                            ?.let { put(meal.id, it.cookedAt) }
                    }
                }

                val input = RankingInput(
                    candidates = catalog,
                    mealType = _selectedMealType.value,
                    audienceMembers = audienceMembers,
                    lastCookedAt = lastCookedAt,
                    feedbackCounts = feedbackCounts,
                    memberScores = memberScores,
                    weights = weightMap,
                    explorationRatio = explorationRatio,
                    totalSlots = 3
                )

                val ranked = rankingEngine.rank(input)
                val enriched = ranked.map { meal ->
                    val memberModifier = audienceMembers
                        .map { member ->
                            memberScores[member.id to meal.catalogMealId]?.let { score ->
                                RankingEngine.computeMemberModifier(
                                    positiveSignals = score.positiveSignals,
                                    negativeSignals = score.negativeSignals,
                                    timesCooked = score.timesCooked
                                )
                            } ?: 0f
                        }
                        .average()
                        .toFloat()
                    val reasons = reasonGenerator.generate(
                        breakdown = ScoreBreakdown(memberModifier = memberModifier),
                        daysSinceLastCooked = daysSince(lastCookedAt[meal.catalogMealId]),
                        makeAgainCount = feedbackCounts[meal.catalogMealId]?.get(FeedbackType.MakeAgain) ?: 0,
                        memberName = if (audienceMembers.size == 1) audienceMembers[0].name else null,
                        tiffinBonusActive = _selectedMealType.value == MealType.Tiffin &&
                            (feedbackCounts[meal.catalogMealId]?.get(FeedbackType.GoodForTiffin) ?: 0) > 0,
                        isExploration = meal.isExploration
                    ).ifEmpty {
                        listOf(defaultReasonFor(_selectedMealType.value))
                    }

                    meal.copy(reasons = reasons)
                }

                _suggestions.value = UiState.Success(enriched)
            } catch (e: Exception) {
                _suggestions.value = UiState.Error(e.message ?: "Failed to load suggestions")
            }
        }
    }

    fun markAsCooked(
        catalogMealId: Long,
        mealName: String,
        mealType: MealType,
        memberIds: List<Long>,
        feedbackSignals: List<FeedbackType>
    ) {
        viewModelScope.launch {
            val entry = MealEntry(
                name = mealName,
                mealType = mealType,
                catalogMealId = catalogMealId
            )
            val mealId = mealRepository.saveMeal(entry, memberIds)

            val allMembers = memberRepository.getActiveMembers()
            val childMemberIds = allMembers
                .filter { it.id in memberIds && it.birthYear != null }
                .map { it.id }

            feedbackSignals.forEach { signal ->
                val feedbackSignal = FeedbackSignal(mealEntryId = mealId, signalType = signal)
                feedbackRepository.saveFeedback(
                    signal = feedbackSignal,
                    catalogMealId = catalogMealId,
                    mealMemberIds = memberIds,
                    childMemberIds = childMemberIds
                )
                weightRepository.getAllWeights().forEach { weight ->
                    val nudged = weightAdapter.nudge(weight, signal)
                    if (nudged.value != weight.value) weightRepository.updateWeight(nudged)
                }
            }

            loadSuggestions()
        }
    }

    private fun List<RankingWeight>.toWeightMap() = WeightMap(
        recency = find { it.signalName == "recency" }?.value ?: 0.40f,
        makeAgain = find { it.signalName == "makeAgain" }?.value ?: 0.30f,
        notAHit = find { it.signalName == "notAHit" }?.value ?: 0.25f,
        tooMuchWork = find { it.signalName == "tooMuchWork" }?.value ?: 0.20f,
        tiffin = find { it.signalName == "tiffin" }?.value ?: 0.15f,
        memberMatch = find { it.signalName == "memberMatch" }?.value ?: 0.20f
    )

    private fun daysSince(lastCookedAt: Long?): Int {
        if (lastCookedAt == null) return 90
        return ((System.currentTimeMillis() - lastCookedAt) / 86_400_000L).toInt().coerceAtLeast(0)
    }

    private fun defaultReasonFor(mealType: MealType): String = when (mealType) {
        MealType.Breakfast -> "Good breakfast option"
        MealType.Lunch -> "Good lunch option"
        MealType.Dinner -> "Good dinner option"
        MealType.Tiffin -> "Easy tiffin option"
        MealType.Snack -> "Nice snack option"
    }

    private fun defaultMealType(): MealType {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> MealType.Breakfast
            in 11..15 -> MealType.Lunch
            in 16..18 -> MealType.Snack
            in 19..23, in 0..4 -> MealType.Dinner
            else -> MealType.Lunch
        }
    }
}
