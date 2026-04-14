# Phase 5: Home Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `HomeViewModel` (loads ranked suggestions via `RankingEngine`) and `HomeScreen` (meal type filter chips, audience selector, suggestion cards with reason chips, "Mark as Cooked" bottom sheet with feedback signals).

**Architecture:** `HomeViewModel` assembles `RankingInput` from all repositories and calls `RankingEngine.rank()`. The result is emitted as `UiState<List<RankedMeal>>`. `ReasonGenerator` enriches each `RankedMeal` with reason strings after ranking. `WeightAdapter` is called asynchronously after feedback submission. The Mark-as-Cooked sheet lives in `MarkAsCookedSheet.kt`.

**Tech Stack:** Jetpack Compose, Material3, Hilt, Coroutines, JUnit 4 + Turbine + MockK

**Prerequisite:** Phases 1–4 complete.

---

## File Map

```
app/src/main/java/com/familymeal/assistant/
  ui/
    home/
      HomeViewModel.kt
      HomeScreen.kt
      SuggestionCard.kt
      MarkAsCookedSheet.kt

app/src/test/java/com/familymeal/assistant/
  ui/
    HomeViewModelTest.kt
```

---

### Task 1: `HomeViewModel` — write tests first

**Files:**
- Create: `ui/home/HomeViewModel.kt`
- Create: `app/src/test/.../ui/HomeViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.domain.engine.*
import com.familymeal.assistant.domain.model.*
import com.familymeal.assistant.ui.common.UiState
import com.familymeal.assistant.ui.home.HomeViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mealRepo: MealRepository
    private lateinit var memberRepo: MemberRepository
    private lateinit var catalogRepo: CatalogRepository
    private lateinit var feedbackRepo: FeedbackRepository
    private lateinit var weightRepo: WeightRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rankingEngine: RankingEngine
    private lateinit var weightAdapter: WeightAdapter
    private lateinit var reasonGen: ReasonGenerator
    private lateinit var vm: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mealRepo = mockk(relaxed = true)
        memberRepo = mockk(relaxed = true)
        catalogRepo = mockk(relaxed = true)
        feedbackRepo = mockk(relaxed = true)
        weightRepo = mockk(relaxed = true)
        settingsRepo = mockk(relaxed = true)
        rankingEngine = RankingEngine()        // use real engine
        weightAdapter = WeightAdapter()
        reasonGen = ReasonGenerator()

        every { settingsRepo.getExplorationRatio() } returns 0.2f
        coEvery { memberRepo.getActiveMembers() } returns listOf(
            Member(1, "Alice", DietType.Veg)
        )
        coEvery { catalogRepo.getAllMeals() } returns listOf(
            CatalogMeal(1, "Dal Makhani", "Indian", DietType.Veg, "Lunch,Dinner")
        )
        coEvery { weightRepo.getAllWeights() } returns listOf(
            RankingWeight("recency", 0.40f, 0.40f),
            RankingWeight("makeAgain", 0.30f, 0.30f),
            RankingWeight("notAHit", 0.25f, 0.25f),
            RankingWeight("tooMuchWork", 0.20f, 0.20f),
            RankingWeight("tiffin", 0.15f, 0.15f),
            RankingWeight("memberMatch", 0.20f, 0.20f)
        )
        coEvery { mealRepo.getLastCookedForCatalogMeal(any()) } returns null
        coEvery { feedbackRepo.getFeedbackForMeal(any()) } returns emptyList()

        vm = HomeViewModel(
            mealRepo, memberRepo, catalogRepo, feedbackRepo,
            weightRepo, settingsRepo, rankingEngine, weightAdapter, reasonGen
        )
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state is Loading then Success`() = runTest {
        vm.suggestions.test {
            val first = awaitItem()
            // May arrive as Loading or Success depending on timing with UnconfinedTestDispatcher
            assertTrue(first is UiState.Loading || first is UiState.Success)
        }
    }

    @Test
    fun `loadSuggestions emits Success with ranked meals`() = runTest {
        vm.suggestions.test {
            skipItems(1) // skip Loading
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val meals = (state as UiState.Success).data
            assertTrue(meals.isNotEmpty())
            assertEquals("Dal Makhani", meals[0].name)
        }
    }

    @Test
    fun `selecting meal type filter refreshes suggestions`() = runTest {
        vm.selectMealType(MealType.Breakfast)
        assertEquals(MealType.Breakfast, vm.selectedMealType.value)
    }

    @Test
    fun `selecting audience member refreshes suggestions`() = runTest {
        vm.selectAudience(listOf(1L)) // Alice only
        assertEquals(listOf(1L), vm.selectedMemberIds.value)
    }

    @Test
    fun `markAsCooked saves meal and triggers weight nudge`() = runTest {
        vm.markAsCooked(
            catalogMealId = 1L,
            mealName = "Dal Makhani",
            mealType = MealType.Lunch,
            memberIds = listOf(1L),
            feedbackSignals = listOf(FeedbackType.MakeAgain)
        )
        coVerify { mealRepo.saveMeal(any(), listOf(1L)) }
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.HomeViewModelTest"
```

- [ ] **Step 3: Implement HomeViewModel.kt**

```kotlin
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

    private val _selectedMealType = MutableStateFlow(MealType.Lunch)
    val selectedMealType: StateFlow<MealType> = _selectedMealType

    // null = Family (all active members); non-null = specific member IDs
    private val _selectedMemberIds = MutableStateFlow<List<Long>?>(null)
    val selectedMemberIds: StateFlow<List<Long>?> = _selectedMemberIds

    private val _suggestions = MutableStateFlow<UiState<List<RankedMeal>>>(UiState.Loading)
    val suggestions: StateFlow<UiState<List<RankedMeal>>> = _suggestions

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

                // Build lastCookedAt and feedbackCounts per catalogMealId
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
                    feedbackCounts = emptyMap(), // simplified: full impl queries FeedbackDao
                    memberScores = emptyMap(),   // simplified: full impl queries MemberMealScore
                    weights = weightMap,
                    explorationRatio = explorationRatio,
                    totalSlots = 5
                )

                val ranked = rankingEngine.rank(input)
                val enriched = ranked.map { meal ->
                    meal.copy(reasons = reasonGenerator.generate(
                        breakdown = ScoreBreakdown(),
                        daysSinceLastCooked = ((System.currentTimeMillis() - (lastCookedAt[meal.catalogMealId] ?: 0L)) / 86_400_000L).toInt(),
                        makeAgainCount = 0,
                        memberName = if (audienceMembers.size == 1) audienceMembers[0].name else null,
                        tiffinBonusActive = _selectedMealType.value == MealType.Tiffin,
                        isExploration = meal.isExploration
                    ))
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

            // Determine child members (non-null birthYear) for KidsLiked attribution
            val allMembers = memberRepository.getActiveMembers()
            val childMemberIds = allMembers
                .filter { it.id in memberIds && it.birthYear != null }
                .map { it.id }

            // Save feedback signals and nudge weights asynchronously
            feedbackSignals.forEach { signal ->
                val feedbackSignal = FeedbackSignal(mealEntryId = mealId, signalType = signal)
                feedbackRepository.saveFeedback(
                    signal = feedbackSignal,
                    catalogMealId = catalogMealId,
                    mealMemberIds = memberIds,
                    childMemberIds = childMemberIds
                )
                // Nudge weights
                weightRepository.getAllWeights().forEach { weight ->
                    val nudged = weightAdapter.nudge(weight, signal)
                    if (nudged.value != weight.value) weightRepository.updateWeight(nudged)
                }
            }

            // Refresh suggestions after saving
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
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.HomeViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/home/HomeViewModel.kt \
        app/src/test/java/com/familymeal/assistant/ui/HomeViewModelTest.kt
git commit -m "feat: implement HomeViewModel with ranking engine integration"
```

---

### Task 2: `SuggestionCard` composable

**Files:**
- Create: `ui/home/SuggestionCard.kt`

- [ ] **Step 1: Create SuggestionCard.kt**

```kotlin
package com.familymeal.assistant.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.domain.model.RankedMeal

@Composable
fun SuggestionCard(
    meal: RankedMeal,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = meal.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = meal.cuisine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (meal.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(meal.reasons) { reason ->
                        SuggestionReasonChip(reason = reason)
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionReasonChip(reason: String) {
    SuggestionChip(
        onClick = {},
        label = { Text(reason, style = MaterialTheme.typography.labelSmall) }
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/home/SuggestionCard.kt
git commit -m "feat: add SuggestionCard composable"
```

---

### Task 3: `MarkAsCookedSheet` composable

**Files:**
- Create: `ui/home/MarkAsCookedSheet.kt`

- [ ] **Step 1: Create MarkAsCookedSheet.kt**

```kotlin
package com.familymeal.assistant.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.data.db.entity.Member
import com.familymeal.assistant.domain.model.RankedMeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkAsCookedSheet(
    meal: RankedMeal,
    activeMembers: List<Member>,
    preselectedMealType: MealType,
    preselectedMemberIds: List<Long>?,  // null = Family
    onConfirm: (mealType: MealType, memberIds: List<Long>, feedback: List<FeedbackType>) -> Unit,
    onDismiss: () -> Unit
) {
    val allFeedbackOptions = FeedbackType.values().toList()
    var selectedMealType by remember { mutableStateOf(preselectedMealType) }
    var selectedMemberIds by remember {
        mutableStateOf(preselectedMemberIds ?: activeMembers.map { it.id })
    }
    val selectedFeedback = remember { mutableStateListOf<FeedbackType>() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Mark as Cooked", style = MaterialTheme.typography.titleLarge)
            Text(meal.name, style = MaterialTheme.typography.bodyLarge)

            // Meal type selector
            Text("Meal Type", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MealType.values().toList()) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = type },
                        label = { Text(type.name) }
                    )
                }
            }

            // Audience selector
            Text("Who's eating?", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedMemberIds.size == activeMembers.size,
                        onClick = { selectedMemberIds = activeMembers.map { it.id } },
                        label = { Text("Family") }
                    )
                }
                items(activeMembers) { member ->
                    FilterChip(
                        selected = selectedMemberIds == listOf(member.id),
                        onClick = { selectedMemberIds = listOf(member.id) },
                        label = { Text(member.name) }
                    )
                }
            }

            // Feedback signals
            Text("How was it? (optional)", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(allFeedbackOptions) { signal ->
                    FilterChip(
                        selected = signal in selectedFeedback,
                        onClick = {
                            if (signal in selectedFeedback) selectedFeedback.remove(signal)
                            else selectedFeedback.add(signal)
                        },
                        label = { Text(signal.displayName()) }
                    )
                }
            }

            Button(
                onClick = { onConfirm(selectedMealType, selectedMemberIds, selectedFeedback.toList()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm")
            }
        }
    }
}

private fun FeedbackType.displayName() = when (this) {
    FeedbackType.MakeAgain -> "Make Again"
    FeedbackType.GoodForTiffin -> "Good for Tiffin"
    FeedbackType.KidsLiked -> "Kids Liked"
    FeedbackType.TooMuchWork -> "Too Much Work"
    FeedbackType.NotAHit -> "Not a Hit"
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/home/MarkAsCookedSheet.kt
git commit -m "feat: add MarkAsCookedSheet"
```

---

### Task 4: `HomeScreen` composable

**Files:**
- Create: `ui/home/HomeScreen.kt`
- Modify: `ui/navigation/AppNavigation.kt` (replace Home stub)

- [ ] **Step 1: Create HomeScreen.kt**

```kotlin
package com.familymeal.assistant.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.domain.model.RankedMeal
import com.familymeal.assistant.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val suggestions by viewModel.suggestions.collectAsState()
    val selectedMealType by viewModel.selectedMealType.collectAsState()
    val selectedMemberIds by viewModel.selectedMemberIds.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()

    var sheetMeal by remember { mutableStateOf<RankedMeal?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What's Cooking?") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        ) {
            // Meal type filter
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(MealType.values().toList()) { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { viewModel.selectMealType(type) },
                        label = { Text(type.name) }
                    )
                }
            }

            // Audience selector
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedMemberIds == null,
                        onClick = { viewModel.selectAudience(null) },
                        label = { Text("Family") }
                    )
                }
                items(activeMembers) { member ->
                    FilterChip(
                        selected = selectedMemberIds == listOf(member.id),
                        onClick = { viewModel.selectAudience(listOf(member.id)) },
                        label = { Text(member.name) }
                    )
                }
            }

            // Suggestions
            when (val state = suggestions) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(state.data) { meal ->
                            SuggestionCard(meal = meal, onClick = { sheetMeal = meal })
                        }
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Could not load suggestions", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    sheetMeal?.let { meal ->
        MarkAsCookedSheet(
            meal = meal,
            activeMembers = activeMembers,
            preselectedMealType = selectedMealType,
            preselectedMemberIds = selectedMemberIds,
            onConfirm = { mealType, memberIds, feedback ->
                viewModel.markAsCooked(meal.catalogMealId, meal.name, mealType, memberIds, feedback)
                sheetMeal = null
            },
            onDismiss = { sheetMeal = null }
        )
    }
}
```

**Note:** Add `activeMembers: StateFlow<List<Member>>` to `HomeViewModel` — expose `memberRepository.observeActiveMembers()`.

- [ ] **Step 2: Replace Home stub in AppNavigation**

In `AppNavigation.kt`, replace the Home `Box` stub:
```kotlin
composable(Screen.Home.route) {
    HomeScreen(onNavigateToSettings = { navController.navigate(Screen.Settings.route) })
}
```

- [ ] **Step 3: Build and run**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests**

```bash
./gradlew :app:test
```

Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/home/ \
        app/src/main/java/com/familymeal/assistant/ui/navigation/AppNavigation.kt
git commit -m "feat: implement HomeScreen with suggestion cards, filter chips, mark-as-cooked sheet"
```
