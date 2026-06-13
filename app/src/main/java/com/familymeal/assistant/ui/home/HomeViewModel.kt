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
import java.util.concurrent.TimeUnit
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
    private val reasonGenerator: ReasonGenerator,
    private val recommendationEventRepository: RecommendationEventRepository,
    private val mealPinRepository: MealPinRepository
) : ViewModel() {

    private val _selectedMealType = MutableStateFlow(defaultMealType())
    val selectedMealType: StateFlow<MealType> = _selectedMealType

    // null = Family (all active members); non-null = specific member IDs
    private val _selectedMemberIds = MutableStateFlow<List<Long>?>(null)
    val selectedMemberIds: StateFlow<List<Long>?> = _selectedMemberIds

    private val _effortCap = MutableStateFlow<EffortLevel?>(null)
    val effortCap: StateFlow<EffortLevel?> = _effortCap

    private val _suggestions = MutableStateFlow<UiState<List<RankedMeal>>>(UiState.Loading)
    val suggestions: StateFlow<UiState<List<RankedMeal>>> = _suggestions

    private val _recentMeals = MutableStateFlow<List<MealEntry>>(emptyList())
    val recentMeals: StateFlow<List<MealEntry>> = _recentMeals

    private val _stripCollapsed = MutableStateFlow(settingsRepository.getRecentlyCookedStripCollapsed())
    val stripCollapsed: StateFlow<Boolean> = _stripCollapsed

    val activeMembers: StateFlow<List<Member>> = memberRepository.observeActiveMembers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<CatalogMeal>> = catalogRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dependableMeals: StateFlow<List<CatalogMeal>> = catalogRepository.getDependableMeals()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Tomorrow's tiffin pin for reminder chip on Home
    val tomorrowTiffinPin: StateFlow<MealPin?> = run {
        val start = tomorrowMidnight()
        val end = start + 86_400_000L
        mealPinRepository.getPinsForWeek(start, end)
            .map { pins -> pins.find { it.mealType == MealType.Tiffin && !it.isLogged } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    }

    init {
        combine(_selectedMealType, _selectedMemberIds, _effortCap) { _, _, _ -> Unit }
            .onEach { loadSuggestions() }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _recentMeals.value = mealRepository.getLastNMeals(7)
        }
    }

    // Dedup SHOWN analytics events: emit once per meal+context per session,
    // otherwise every filter change inflates shownCount and meals get
    // implicit-suppressed after 3 Home loads without any user action.
    private val shownEmitted = mutableSetOf<Pair<Long, String>>()

    fun selectMealType(type: MealType) { _selectedMealType.value = type }
    fun selectAudience(memberIds: List<Long>?) { _selectedMemberIds.value = memberIds }

    fun refresh() { loadSuggestions() }

    fun setEffortCap(cap: EffortLevel?) { _effortCap.value = cap }

    fun toggleStripCollapsed() {
        val next = !_stripCollapsed.value
        _stripCollapsed.value = next
        settingsRepository.setRecentlyCookedStripCollapsed(next)
    }

    fun toggleFavorite(catalogMealId: Long, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            catalogRepository.updateFavorite(catalogMealId, !currentlyFavorite)
        }
    }

    fun emitTapped(catalogMealId: Long) {
        viewModelScope.launch {
            recommendationEventRepository.insertEvent(
                RecommendationEvent(
                    catalogMealId = catalogMealId,
                    mealContext = _selectedMealType.value.name,
                    eventType = RecommendationEventType.TAPPED
                )
            )
        }
    }

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

                // V2: implicit signals + busy context
                val implicitSignals = catalog.associate { meal ->
                    meal.id to recommendationEventRepository.getSignals(meal.id)
                }

                val cal = Calendar.getInstance()
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
                val busyContext = isWeekday && _selectedMealType.value in listOf(MealType.Breakfast, MealType.Tiffin)

                val input = RankingInput(
                    candidates = catalog,
                    mealType = _selectedMealType.value,
                    audienceMembers = audienceMembers,
                    lastCookedAt = lastCookedAt,
                    feedbackCounts = feedbackCounts,
                    memberScores = memberScores,
                    weights = weightMap,
                    explorationRatio = explorationRatio,
                    totalSlots = 3,
                    implicitSignals = implicitSignals,
                    effortCap = _effortCap.value,
                    busyContext = busyContext
                )

                val ranked = rankingEngine.rank(input)

                // V2: emit SHOWN events (once per meal+context per session)
                val context = _selectedMealType.value.name
                ranked.forEach { rankedMeal ->
                    if (shownEmitted.add(rankedMeal.catalogMealId to context)) {
                        launch {
                            recommendationEventRepository.insertEvent(
                                RecommendationEvent(
                                    catalogMealId = rankedMeal.catalogMealId,
                                    mealContext = context,
                                    eventType = RecommendationEventType.SHOWN
                                )
                            )
                        }
                    }
                }

                val enriched = ranked.map { meal ->
                    val catalogMeal = catalog.find { it.id == meal.catalogMealId }
                    val reasons = reasonGenerator.generate(
                        breakdown = meal.breakdown,
                        daysSinceLastCooked = daysSince(lastCookedAt[meal.catalogMealId]),
                        makeAgainCount = feedbackCounts[meal.catalogMealId]?.get(FeedbackType.MakeAgain) ?: 0,
                        memberName = if (audienceMembers.size == 1) audienceMembers[0].name else null,
                        tiffinBonusActive = _selectedMealType.value == MealType.Tiffin,
                        tiffinKidFavorite = (feedbackCounts[meal.catalogMealId]?.get(FeedbackType.KidsLiked) ?: 0) > 0,
                        isExploration = meal.isExploration,
                        effortLevel = catalogMeal?.effortLevel,
                        busyContext = busyContext,
                        implicitLiftActive = (implicitSignals[meal.catalogMealId]?.tappedCount ?: 0) > 0
                    ).ifEmpty {
                        listOf(defaultReasonFor(_selectedMealType.value))
                    }
                    meal.copy(
                        reasons = reasons,
                        effortLevel = catalogMeal?.effortLevel ?: com.familymeal.assistant.data.db.entity.EffortLevel.MEDIUM
                    )
                }

                _suggestions.value = UiState.Success(enriched)

                // Refresh recent meals strip
                _recentMeals.value = mealRepository.getLastNMeals(7)
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

            // V2: emit COOKED event
            recommendationEventRepository.insertEvent(
                RecommendationEvent(
                    catalogMealId = catalogMealId,
                    mealContext = mealType.name,
                    eventType = RecommendationEventType.COOKED
                )
            )

            // If this meal was pinned for today, mark the pin as logged so
            // Week View and the tiffin reminder reflect reality.
            mealPinRepository.markAsLogged(catalogMealId, todayMidnight())

            loadSuggestions()
        }
    }

    private fun List<RankingWeight>.toWeightMap() = WeightMap(
        recency = find { it.signalName == "recency" }?.value ?: 0.35f,
        makeAgain = find { it.signalName == "makeAgain" }?.value ?: 0.30f,
        notAHit = find { it.signalName == "notAHit" }?.value ?: 0.25f,
        tooMuchWork = find { it.signalName == "tooMuchWork" }?.value ?: 0.20f,
        tiffin = find { it.signalName == "tiffin" }?.value ?: 0.20f,
        memberMatch = find { it.signalName == "memberMatch" }?.value ?: 0.25f
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

    private fun todayMidnight(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun tomorrowMidnight(): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
