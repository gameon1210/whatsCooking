# Phase 7: History Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `HistoryViewModel` (meal log with filters by type, member, date range) and `HistoryScreen` (chronological list, filter chips, meal detail view with feedback display and add-feedback capability).

**Architecture:** `HistoryViewModel` exposes a reactive Flow of filtered meals. Filters are stored as `StateFlow` and combined to produce the filtered list. Meal detail is shown in a `ModalBottomSheet`. Existing feedback signals are fetched from `FeedbackRepository` when the detail sheet opens. Entries with `classificationPending = true` and recent timestamp show a subtle pending indicator.

**Tech Stack:** Jetpack Compose, Material3, Hilt, Coroutines + Flow, JUnit 4 + Turbine + MockK

**Prerequisite:** Phases 1–4 complete.

---

## File Map

```
app/src/main/java/com/familymeal/assistant/
  ui/
    history/
      HistoryViewModel.kt
      HistoryScreen.kt
      MealDetailSheet.kt

app/src/test/java/com/familymeal/assistant/
  ui/
    HistoryViewModelTest.kt
```

---

### Task 1: `HistoryViewModel` — write tests first

**Files:**
- Create: `ui/history/HistoryViewModel.kt`
- Create: `app/src/test/.../ui/HistoryViewModelTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.ui

import app.cash.turbine.test
import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.data.repository.*
import com.familymeal.assistant.ui.common.UiState
import com.familymeal.assistant.ui.history.HistoryFilter
import com.familymeal.assistant.ui.history.HistoryViewModel
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mealRepo: MealRepository
    private lateinit var memberRepo: MemberRepository
    private lateinit var feedbackRepo: FeedbackRepository
    private lateinit var vm: HistoryViewModel

    private val meal1 = MealEntry(1, "Dal Makhani", mealType = MealType.Lunch, cookedAt = System.currentTimeMillis() - 86_400_000L)
    private val meal2 = MealEntry(2, "Omelette", mealType = MealType.Breakfast, cookedAt = System.currentTimeMillis() - 2 * 86_400_000L)
    private val alice = Member(1, "Alice", DietType.Veg)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mealRepo = mockk(relaxed = true)
        memberRepo = mockk(relaxed = true)
        feedbackRepo = mockk(relaxed = true)

        every { mealRepo.observeAllMeals() } returns flowOf(listOf(meal1, meal2))
        coEvery { memberRepo.getActiveMembers() } returns listOf(alice)
        coEvery { feedbackRepo.getFeedbackForMeal(any()) } returns emptyList()

        vm = HistoryViewModel(mealRepo, memberRepo, feedbackRepo)
    }

    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun `initial state loads all meals`() = runTest {
        vm.meals.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            assertEquals(2, (state as UiState.Success).data.size)
        }
    }

    @Test
    fun `filterByMealType filters to Lunch only`() = runTest {
        vm.setMealTypeFilter(MealType.Lunch)
        vm.meals.test {
            val state = awaitItem()
            assertTrue(state is UiState.Success)
            val meals = (state as UiState.Success).data
            assertTrue(meals.all { it.mealType == MealType.Lunch })
        }
    }

    @Test
    fun `clearFilters restores all meals`() = runTest {
        vm.setMealTypeFilter(MealType.Breakfast)
        vm.clearFilters()
        vm.meals.test {
            val state = awaitItem()
            assertEquals(2, (state as UiState.Success).data.size)
        }
    }

    @Test
    fun `getFeedbackForMeal returns signal list`() = runTest {
        coEvery { feedbackRepo.getFeedbackForMeal(1L) } returns listOf(
            FeedbackSignal(1, mealEntryId = 1L, signalType = FeedbackType.MakeAgain)
        )
        val signals = vm.getFeedbackForMeal(1L)
        assertEquals(1, signals.size)
        assertEquals(FeedbackType.MakeAgain, signals[0].signalType)
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.HistoryViewModelTest"
```

- [ ] **Step 3: Implement HistoryViewModel.kt**

```kotlin
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
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.HistoryViewModelTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/ui/history/HistoryViewModel.kt \
        app/src/test/java/com/familymeal/assistant/ui/HistoryViewModelTest.kt
git commit -m "feat: implement HistoryViewModel with meal type + member filters"
```

---

### Task 2: `MealDetailSheet` composable

**Files:**
- Create: `ui/history/MealDetailSheet.kt`

- [ ] **Step 1: Create MealDetailSheet.kt**

```kotlin
package com.familymeal.assistant.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealEntry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailSheet(
    meal: MealEntry,
    existingFeedback: List<FeedbackSignal>,
    onAddFeedback: (FeedbackType) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()) }
    val alreadyGiven = existingFeedback.map { it.signalType }.toSet()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(meal.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${meal.mealType.name} · ${dateFormat.format(Date(meal.cookedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (meal.classificationPending && (System.currentTimeMillis() - meal.cookedAt < 86_400_000L)) {
                Text(
                    "Identifying meal…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (alreadyGiven.isNotEmpty()) {
                Text("Feedback given", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(alreadyGiven.toList()) { signal ->
                        AssistChip(onClick = {}, label = { Text(signal.displayName()) })
                    }
                }
            }

            val remaining = FeedbackType.values().filter { it !in alreadyGiven }
            if (remaining.isNotEmpty()) {
                Text("Add feedback", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(remaining) { signal ->
                        SuggestionChip(
                            onClick = { onAddFeedback(signal) },
                            label = { Text(signal.displayName()) }
                        )
                    }
                }
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
git add app/src/main/java/com/familymeal/assistant/ui/history/MealDetailSheet.kt
git commit -m "feat: add MealDetailSheet with existing feedback and add-feedback option"
```

---

### Task 3: `HistoryScreen` composable

**Files:**
- Create: `ui/history/HistoryScreen.kt`
- Modify: `ui/navigation/AppNavigation.kt` (replace History stub)

- [ ] **Step 1: Create HistoryScreen.kt**

```kotlin
package com.familymeal.assistant.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familymeal.assistant.data.db.entity.FeedbackSignal
import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.MealEntry
import com.familymeal.assistant.data.db.entity.MealType
import com.familymeal.assistant.ui.common.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val meals by viewModel.meals.collectAsState()
    val activeMembers by viewModel.activeMembers.collectAsState()
    val filter by viewModel.filter.collectAsState() // expose for UI
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }
    var feedbackForMeal by remember { mutableStateOf<List<FeedbackSignal>>(emptyList()) }

    LaunchedEffect(selectedMeal) {
        selectedMeal?.let {
            feedbackForMeal = viewModel.getFeedbackForMeal(it.id)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("History") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Meal type filters
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filter.mealType == null,
                        onClick = { viewModel.setMealTypeFilter(null) },
                        label = { Text("All") }
                    )
                }
                items(MealType.values().toList()) { type ->
                    FilterChip(
                        selected = filter.mealType == type,
                        onClick = { viewModel.setMealTypeFilter(type) },
                        label = { Text(type.name) }
                    )
                }
            }

            when (val state = meals) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("No meals logged yet")
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.data) { meal ->
                                MealHistoryRow(meal = meal, onClick = { selectedMeal = meal })
                            }
                        }
                    }
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    selectedMeal?.let { meal ->
        MealDetailSheet(
            meal = meal,
            existingFeedback = feedbackForMeal,
            onAddFeedback = { signal ->
                viewModel.addFeedback(meal.id, meal.catalogMealId, signal, emptyList())
                feedbackForMeal = feedbackForMeal + FeedbackSignal(
                    mealEntryId = meal.id, signalType = signal
                )
            },
            onDismiss = { selectedMeal = null }
        )
    }
}

@Composable
private fun MealHistoryRow(meal: MealEntry, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    ListItem(
        headlineContent = { Text(meal.name) },
        supportingContent = {
            Text("${meal.mealType.name} · ${dateFormat.format(Date(meal.cookedAt))}")
        },
        trailingContent = {
            if (meal.classificationPending && System.currentTimeMillis() - meal.cookedAt < 86_400_000L) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    Divider()
}
```

- [ ] **Step 2: Replace History stub in AppNavigation**

```kotlin
composable(Screen.History.route) {
    HistoryScreen()
}
```

- [ ] **Step 3: Build**

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
git add app/src/main/java/com/familymeal/assistant/ui/history/ \
        app/src/main/java/com/familymeal/assistant/ui/navigation/AppNavigation.kt
git commit -m "feat: implement HistoryScreen with filters and meal detail sheet"
```
