# Family Meal Assistant — Full Product Spec (V2.0 → V2.2 + Seasonal Packs)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement the complete Family Meal Assistant product from V2.0 through V2.2, plus Seasonal Packs, closing all gaps in the current codebase against the full product spec dated April 2026.

**Architecture:** MVVM + Hilt + Jetpack Compose + Room (DB v5 after this work). All features are offline-first. Firebase Analytics added as an optional remote event layer. No backend, no auth.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt, Room, Coil 2.7.0, WorkManager, Firebase Analytics (optional), Kotlin Coroutines + Flow.

**Scope explicitly excluded:** Learning-to-Rank ML pipeline, Shared Household Mode / Firestore sync.

---

## Current State Audit (what works, what's broken)

The existing codebase has Room DB v4, all entities, V2 repositories, RankingEngine V2, ReasonGenerator V2, and screen shells for all major screens. The following items are **missing or incorrect** and must be fixed:

| # | Gap | Severity |
|---|-----|----------|
| G-01 | `MealEntry.notes` has no DB migration — ALTER TABLE never ran | P0 |
| G-02 | Quick-reuse chip strip missing from Add Meal **UI** (VM has data, screen does not) | P0 |
| G-03 | Notes text field missing from Add Meal UI | P0 |
| G-04 | No duplicate-save debounce in AddMealViewModel | P0 |
| G-05 | Time-based meal type pre-selection missing from AddMealViewModel | P0 |
| G-06 | "Kid Favorite" / "Made Again" labels missing from History card rows | P0 |
| G-07 | Search debounce (300ms) missing from HistoryViewModel | P0 |
| G-08 | "Cook Again" shortcut missing from MealDetailSheet | P1 |
| G-09 | Category clustering alert ("Several rice meals recently") missing from RecentlyCookedStrip | P1 |
| G-10 | catalog.json missing `category`, `effortLevel`, `leftoverFriendly` fields | P1 |
| G-11 | Tiffin Planner does not exclude meals packed as Tiffin in last 3 days | P1 |
| G-12 | Tiffin Planner does not surface leftover-tagged meal at position 1 | P1 |
| G-13 | Tiffin reminder chip on Home not time-gated to 06:00–10:00 | P1 |
| G-14 | No WorkManager periodic pruning (RecommendationEvent 90d, TiffinPlan 7d, MealPin 30d) | P1 |
| G-15 | IGNORED threshold not in ranking_config.json (hardcoded) | P2 |
| G-16 | Firebase Analytics not integrated | P2 |

---

## Section 1 — Data Layer (DB v5, catalog, config, WorkManager, Analytics)

### 1.1 Migration 4→5: notes column

`MealEntry.notes: String? = null` exists in the entity but was never added to the SQLite table. Add Migration4To5:

```kotlin
// app/src/main/java/com/familymeal/assistant/data/db/migrations/Migration4To5.kt
val Migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meal_entries ADD COLUMN notes TEXT")
    }
}
```

`AppDatabase` bumps to `version = 5`. `MIGRATIONS` array gains `Migration4To5`.

### 1.2 Migration 5→6: Seasonal Packs table

```kotlin
// Migration5To6.kt
val Migration5To6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS seasonal_packs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                packId TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                season TEXT NOT NULL,
                catalogMealIds TEXT NOT NULL,
                activeFrom INTEGER NOT NULL DEFAULT 0,
                activeTo INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}
```

`AppDatabase` bumps to `version = 6`. Add `SeasonalPack` entity, `SeasonalPackDao`.

### 1.3 catalog.json — add category, effortLevel, leftoverFriendly

Every entry in `app/src/main/assets/catalog.json` gains three new fields:

```json
{
  "name": "Dal Makhani",
  "cuisine": "Indian",
  "dietType": "Veg",
  "mealTypes": "Lunch,Dinner",
  "tags": "comfort,slow-cook",
  "category": "lentil",
  "effortLevel": "INVOLVED",
  "leftoverFriendly": true
}
```

**Category taxonomy** (mutually exclusive, pick best fit):
`rice` · `lentil` · `bread` · `egg` · `snack` · `meat` · `pasta` · `soup` · `salad` · `dessert`

**EffortLevel mapping:**
- `QUICK` — ready in under 20 min (e.g., Poha, Upma, Egg Bhurji)
- `MEDIUM` — 20–45 min (e.g., Dal Makhani from can, Pasta, Khichdi)
- `INVOLVED` — 45+ min or complex prep (e.g., Chicken Biryani, Mutton Curry, Chole Bhature)

`CatalogRepositoryImpl.seedCatalog()` reads and maps all three new fields from JSON into `CatalogMeal`.

### 1.4 ranking_config.json — IGNORED threshold fields

Add two configurable fields:

```json
{ "signalName": "ignoredShownThreshold", "defaultValue": 3 },
{ "signalName": "ignoredWindowDays",     "defaultValue": 14 }
```

`WeightMap` gains `ignoredShownThreshold: Int = 3` and `ignoredWindowDays: Int = 14`.
`RankingEngine` reads these from `WeightMap` instead of hardcoding them.
`HomeViewModel.toWeightMap()` maps them from the weights list.

### 1.5 WorkManager periodic cleanup

Add `androidx.work:work-runtime-ktx` dependency (same version as Kotlin coroutines).

**Three workers (all `PeriodicWorkRequest`, interval = 24h, `KEEP` existing policy):**

```kotlin
// RecommendationEventPruneWorker: delete occurredAt < now - 90 days
// TiffinPlanCleanupWorker: delete plannedDate < today - 7 days  
// MealPinCleanupWorker: delete slotDate < today - 30 days
```

Workers are enqueued in `App.onCreate()` (or `MainActivity.onCreate()` with `enqueueUniquePeriodicWork`). Each is a `CoroutineWorker`.

Add `pruneOldEvents(beforeEpoch: Long)` to `RecommendationEventDao` / `RecommendationEventRepository`.
Add `deleteExpiredPlans(beforeEpoch: Long)` to `TiffinPlanDao` / `TiffinPlanRepository`.
Add `deleteOldPins(beforeEpoch: Long)` to `MealPinDao` / `MealPinRepository`.

### 1.6 Firebase Analytics — AnalyticsHelper

Add `com.google.firebase:firebase-analytics-ktx` to build.gradle.kts.
Add `google-services` plugin. (`google-services.json` is added by the developer — not committed to repo.)

```kotlin
// app/src/main/java/com/familymeal/assistant/analytics/AnalyticsHelper.kt
@Singleton
class AnalyticsHelper @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {
    fun logMealLogged(mealType: String, wasQuickReuse: Boolean)
    fun logSuggestionTapped(catalogMealId: Long, reason: String)
    fun logTiffinPlanSaved(catalogMealId: Long)
    fun logFeedbackSubmitted(feedbackType: String)
}
```

Hilt provides `FirebaseAnalytics` via `@Provides` in `AnalyticsModule`. If `google-services.json` is absent (CI/test environment), analytics calls no-op silently — guard with `BuildConfig.DEBUG` flag or a `NoopAnalyticsHelper` binding.

---

## Section 2 — Add Meal V2 (gaps G-01 through G-05)

### 2.1 Quick-reuse chip strip in Add Meal UI

`AddMealViewModel.recentMeals: StateFlow<List<MealEntry>>` already exists (loads last 5). The UI just needs to render it.

**In `AddMealScreen.kt`**, between the top banner and the meal name field, add:

```kotlin
if (recentMeals.isNotEmpty()) {
    Text("Cook again?", style = MaterialTheme.typography.labelMedium)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(recentMeals, key = { it.id }) { entry ->
            SuggestionChip(
                onClick = {
                    mealName = entry.name
                    selectedMealType = entry.mealType   // pre-fill BOTH name AND type (AC-026.2)
                },
                label = { Text(entry.name, maxLines = 1) }
            )
        }
    }
}
```

Edge case: strip hidden entirely when `recentMeals.isEmpty()` (AC-026.1 edge case).

### 2.2 Notes text field

Below the meal name field, add an optional notes field:

```kotlin
OutlinedTextField(
    value = notes,
    onValueChange = { notes = it },
    label = { Text("Notes (optional)") },
    placeholder = { Text("e.g. less spice next time") },
    singleLine = false,
    maxLines = 3,
    modifier = Modifier.fillMaxWidth()
)
```

`notes: String` is a `remember { mutableStateOf("") }` local var. Passed to `viewModel.saveMeal(... notes = notes.trim().ifBlank { null })`.

`AddMealViewModel.saveMeal()` signature gains `notes: String?` and passes it to `MealEntry(notes = notes)`.

### 2.3 Time-based meal type pre-selection

`AddMealViewModel` currently initialises `_selectedMealType` to `MealType.Lunch`. Replace with a private helper that mirrors `HomeViewModel.defaultMealType()`:

```kotlin
private fun defaultMealType(): MealType {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..10  -> MealType.Breakfast
        in 10..11 -> MealType.Tiffin    // AC-025.2: 10:45 → Tiffin
        in 11..15 -> MealType.Lunch
        in 16..18 -> MealType.Snack
        else      -> MealType.Dinner    // AC-025.3: 19:30 → Dinner
    }
}
```

`AddMealScreen.kt` must **not** hardcode `MealType.Lunch` as the initial state. Instead it reads `viewModel.defaultMealType` (exposed as a simple property, not a StateFlow — it's a one-time read at screen open).

### 2.4 Duplicate-save debounce

In `AddMealViewModel`:

```kotlin
private var _isSaving = false

fun saveMeal(...) {
    if (_isSaving) return
    _isSaving = true
    viewModelScope.launch {
        try { /* existing save logic */ }
        finally { _isSaving = false }
    }
}
```

This prevents double-tap creating two `MealEntry` records (spec edge case).

---

## Section 3 — History V2 Polish (gaps G-06 through G-08)

### 3.1 300ms search debounce

In `HistoryViewModel`:

```kotlin
private val _searchQuery = MutableStateFlow("")

// Replace direct combine with debounced flow
val meals: StateFlow<UiState<List<MealEntry>>> = combine(
    baseMeals,
    _filter,
    _searchQuery.debounce(300L)   // ← debounce added here
) { allMeals, filter, query ->
    val filtered = allMeals
        .let { if (filter.mealType != null) it.filter { m -> m.mealType == filter.mealType } else it }
        .let { if (query.isBlank()) it else it.filter { m -> m.name.contains(query, ignoreCase = true) } }
    UiState.Success(filtered) as UiState<List<MealEntry>>
}
```

### 3.2 Meal labels on History card rows

**Label derivation in `HistoryViewModel`:**

```kotlin
data class MealLabel(val text: String, val type: LabelType)
enum class LabelType { KID_FAVORITE, MADE_AGAIN, GOOD_FOR_TIFFIN }

// Derived alongside meals: for each MealEntry, count feedback signals from FeedbackSignal table
suspend fun getLabelsForMeal(mealEntryId: Long): List<MealLabel> {
    val signals = feedbackRepository.getFeedbackForMeal(mealEntryId).groupingBy { it.signalType }.eachCount()
    val labels = mutableListOf<MealLabel>()
    if ((signals[FeedbackType.KidsLiked] ?: 0) >= 3)   labels += MealLabel("Kid Favorite", LabelType.KID_FAVORITE)
    if ((signals[FeedbackType.MakeAgain] ?: 0) >= 2)   labels += MealLabel("Made Again", LabelType.MADE_AGAIN)
    if ((signals[FeedbackType.GoodForTiffin] ?: 0) >= 1) labels += MealLabel("Good for Tiffin", LabelType.GOOD_FOR_TIFFIN)
    return labels.take(2)  // max 2 labels displayed
}
```

Labels are loaded lazily per visible card using a `LaunchedEffect(meal.id)` in `MealHistoryRow`, stored in a `remember` map keyed by meal ID, exposed via a `@Composable` helper or a `labelsMap: StateFlow<Map<Long, List<MealLabel>>>` in the ViewModel (preferred — avoids per-item suspend calls in UI).

**For `labelsMap`:** HistoryViewModel derives it from the `meals` StateFlow — whenever `meals` updates, it collects feedback for each visible meal and emits the map. Use `flatMapLatest` + parallel coroutines.

**In `MealHistoryRow`:** render labels as small `AssistChip` with 4sp spacing below the headline text. Show max 2; if 3+ available, show "+N more" text.

### 3.3 "Cook Again" shortcut in MealDetailSheet

Add `onCookAgain: (MealEntry) -> Unit` callback to `MealDetailSheet`. Render as a filled button above the delete button:

```kotlin
Button(
    onClick = { onCookAgain(meal) },
    modifier = Modifier.fillMaxWidth()
) { Text("Cook Again") }
```

In `HistoryScreen`, wire `onCookAgain` to navigate to Add Meal with the meal pre-filled. Since Add Meal is a separate route, pass the pre-fill data via navigation arguments or a shared `AddMealEntryPoint` sealed class via the NavBackStackEntry saved state handle:

```
HistoryScreen → nav("add_meal?prefillName={name}&prefillType={type}")
AddMealScreen → reads from savedStateHandle
```

`AppNavigation.kt` adds optional `prefillName` and `prefillType` string args to the `add_meal` route.
`AddMealViewModel` reads them from `SavedStateHandle` in its constructor.

---

## Section 4 — Home V2 Polish (gaps G-09, G-13)

### 4.1 Category clustering alert (G-09)

`RecentlyCookedStrip` receives an optional `clusterAlert: String?` parameter.

**In `HomeViewModel`**, add a derived property:

```kotlin
val clusterAlert: StateFlow<String?> = recentMeals.map { meals ->
    if (meals.size < 3) return@map null
    val last5 = meals.take(5)
    // Only consider meals where catalogMealId is non-null (has category)
    val categoryGroups = last5
        .mapNotNull { meal ->
            // Look up category from catalog
            catalog.find { it.id == meal.catalogMealId }?.category
        }
        .groupingBy { it }.eachCount()
    val dominant = categoryGroups.entries.maxByOrNull { it.value }
    if (dominant != null && dominant.value >= 3) "Several ${dominant.key} meals recently"
    else null
}.stateIn(viewModelScope, SharingStarted.Eagerly, null)
```

`catalog` is kept as a private `List<CatalogMeal>` field in HomeViewModel, populated once in `loadSuggestions()`.

`RecentlyCookedStrip` renders the alert as a small italic text line below the strip header when non-null:
```kotlin
clusterAlert?.let {
    Text(it, style = MaterialTheme.typography.labelSmall,
         color = MaterialTheme.colorScheme.tertiary,
         modifier = Modifier.padding(horizontal = 16.dp))
}
```

### 4.2 Time-gated tiffin reminder chip (G-13)

In `HomeScreen`, the tiffin reminder chip already renders when `tomorrowTiffinPin != null`. Add a time-window check:

```kotlin
val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
val showTiffinReminder = tomorrowTiffinPin != null && currentHour in 6..9  // 06:00–10:00
```

Use `remember` with a key of the current minute so it re-evaluates if the app stays open across the hour boundary (alternatively, a `LaunchedEffect` that re-reads the hour periodically is fine for V2).

---

## Section 5 — Tiffin Planner V2 (gaps G-11, G-12)

### 5.1 Exclude last 3 days tiffins (G-11)

`TiffinPlannerViewModel` gains `MealRepository` in its constructor. In `init`, after loading catalog:

```kotlin
val recentTiffinIds: Set<Long?> = mealRepository
    .getMealsSince(System.currentTimeMillis() - 3 * 86_400_000L)
    .filter { it.mealType == MealType.Tiffin }
    .mapNotNull { it.catalogMealId }
    .toSet()

val tiffinSuitable = allMeals.filter { it.tiffinSuitable && it.id !in recentTiffinIds }
```

`MealRepository` needs `getMealsSince(since: Long): List<MealEntry>` — a simple `@Query("SELECT * FROM meal_entries WHERE cookedAt >= :since")` in `MealEntryDao`.

### 5.2 Leftover cue at position 1 (G-12)

`TiffinPlannerViewModel` also queries for a leftover-tagged recent meal. Use a batch feedback query (not N+1):

```kotlin
val leftoverMeal: CatalogMeal? = run {
    val since24h = System.currentTimeMillis() - 86_400_000L
    val recentEntries = mealRepository.getMealsSince(since24h)
    if (recentEntries.isEmpty()) return@run null
    // Batch fetch: one query for all feedback, not one per entry
    val feedbackCounts = feedbackRepository.getFeedbackCounts(recentEntries.map { it.id })
    val leftoverEntry = recentEntries.firstOrNull { entry ->
        (feedbackCounts[entry.id]?.get(FeedbackType.GoodForLeftovers) ?: 0) > 0
    }
    leftoverEntry?.catalogMealId?.let { id -> catalog.find { it.id == id } }
}
```

`FeedbackRepository.getFeedbackCounts(mealEntryIds: List<Long>): Map<Long, Map<FeedbackType, Int>>` — this method already exists on `FeedbackRepositoryImpl` (currently takes `catalogMealIds`). Add an overload (or rename parameter) that accepts `mealEntryIds` and joins via `meal_entries`.

If `leftoverMeal != null`, it is injected at index 0 of `filteredCatalog`. A `_leftoverCue: StateFlow<CatalogMeal?>` is exposed separately so the UI can render the "Use leftovers from last night" label.

`TiffinPlannerScreen` renders the leftover cue card with a distinct teal badge: `"🥡 Use leftovers: ${leftoverMeal.name}"` above the main suggestion list.

Add `FeedbackRepository` to `TiffinPlannerViewModel` constructor; add Hilt binding accordingly.

### 5.3 Plan-to-log flow (existing gap, not yet confirmed)

Verify `TiffinPlannerScreen` has a "Log as cooked" button on the active plan banner that navigates to Add Meal pre-filled with the plan's meal name + type = Tiffin. Use the same `prefillName`/`prefillType` nav arg mechanism from G-08.

---

## Section 6 — Household Preference Profiles (V2.2)

### 6.1 Schema (already done)

`Member` already has `spicyTolerance: SpicyTolerance`, `portablePreference: Boolean`, `schoolGoing: Boolean`, `sundaySpecial: Boolean`. No new migration needed.

### 6.2 Ranking integration

`RankingInput` gains `audienceProfiles: List<Member>` (rename existing `audienceMembers` to `audienceProfiles` for clarity, or just use the existing field — keep the name as-is to avoid churn).

Add modifier computation in `RankingEngine.rank()`, applied after the main score:

```kotlin
// Household profile modifiers
var profileModifier = 0f
val cal = Calendar.getInstance()
val isWeekday = cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.FRIDAY
val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
val isMorning = cal.get(Calendar.HOUR_OF_DAY) in 6..10

// Tags is a comma-separated string — split before matching to avoid substring false positives
// e.g. "very-spicy" should NOT match "spicy" via substring
val tagSet = catalogMeal.tags.split(",").map { it.trim() }.toSet()

audienceMembers.forEach { member ->
    // Spicy intolerant member: suppress spicy catalog meals
    if (member.spicyTolerance == SpicyTolerance.NONE && "spicy" in tagSet) {
        profileModifier -= 0.20f
    }
    // Portable preference: boost tiffin-suitable meals
    if (member.portablePreference && catalogMeal.tiffinSuitable) {
        profileModifier += 0.15f
    }
    // School-going child: boost kid-friendly on weekday mornings
    if (member.schoolGoing && isWeekday && isMorning && catalogMeal.tiffinSuitable) {
        profileModifier += 0.10f
    }
    // Sunday special: boost weekend-tagged meals on Sunday
    if (member.sundaySpecial && isSunday && "weekend" in tagSet) {
        profileModifier += 0.15f
    }
}
finalScore += profileModifier * weights.memberMatch
```

### 6.3 Settings UI — Member Profile Editor

In `SettingsScreen`, each listed member has an "Edit preferences" expansion or a tap-through to a `MemberProfileScreen`:

**`MemberProfileScreen`** (new screen, new route `member_profile/{memberId}`):
- Shows member name (read-only)
- Spicy tolerance: `SegmentedButton` with NONE / MILD / FULL
- Portable preference: `Switch` — "Prefers portable / tiffin meals"
- School-going: `Switch` — "School-going (weekday morning boost)"
- Sunday special: `Switch` — "Likes Sunday specials"

`MemberProfileViewModel` loads the member from `MemberRepository.getMemberById(id)` and updates via `MemberRepository.updateMember(member)`.

`MemberRepository` gets `getMemberById(id: Long): Member?` and `updateMember(member: Member)` methods.
`MemberDao` gets corresponding queries.

---

## Section 7 — Leftover / Repeat-Use Cues (V2.2)

### 7.1 Tiffin Planner surface (covered in Section 5.2)

Already designed. The leftover cue card appears at position 1 with a distinct label.

### 7.2 Home screen leftover reminder chip

In `HomeViewModel`, expose:

```kotlin
// Use batch feedback fetch — same pattern as TiffinPlannerViewModel
private suspend fun detectLeftoverCue(): MealEntry? {
    val since24h = System.currentTimeMillis() - 86_400_000L
    val recentEntries = mealRepository.getMealsSince(since24h)
    if (recentEntries.isEmpty()) return null
    val feedbackCounts = feedbackRepository.getFeedbackCounts(recentEntries.map { it.id })
    return recentEntries.firstOrNull { entry ->
        (feedbackCounts[entry.id]?.get(FeedbackType.GoodForLeftovers) ?: 0) > 0
    }
}

private val _leftoverCue = MutableStateFlow<MealEntry?>(null)
val leftoverCue: StateFlow<MealEntry?> = _leftoverCue
```

`_leftoverCue.value = detectLeftoverCue()` is called inside `loadSuggestions()` so it refreshes whenever suggestions reload.

Refresh `leftoverCue` whenever `loadSuggestions()` is called (combine into the same loading chain).

In `HomeScreen`, below the tiffin reminder chip, show an optional leftover reminder (only in the morning, 06:00–12:00):

```kotlin
if (leftoverCue != null && currentHour in 6..11) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            "🥡 Yesterday's ${leftoverCue!!.name} works great for tiffin",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}
```

### 7.3 Feedback sheet surfaces GoodForLeftovers

`PostSaveFeedbackSheet` already includes `GoodForLeftovers`. No change needed here. `MealDetailSheet` also already has it. ✓

---

## Section 8 — Seasonal Packs (V2.x-B)

### 8.1 Entity & DAO

```kotlin
// SeasonalPack.kt
@Entity(tableName = "seasonal_packs")
data class SeasonalPack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packId: String,           // e.g. "summer_coolers"
    val name: String,             // "Summer Coolers"
    val description: String,
    val season: String,           // "SUMMER" | "MONSOON" | "WINTER" | "ALL_YEAR"
    val catalogMealIds: String,   // JSON array of Long IDs: "[1,2,3]"
    val activeFrom: Int = 0,      // month-of-year (1=Jan) inclusive
    val activeTo: Int = 0         // month-of-year inclusive
)
```

```kotlin
// SeasonalPackDao.kt
@Dao
interface SeasonalPackDao {
    // NOTE: Winter pack (activeFrom=10, activeTo=2) wraps the year boundary.
    // SQL BETWEEN fails for wrapped ranges, so we handle it in the repository layer.
    // DAO returns ALL packs; SeasonalPackRepositoryImpl filters by current month.
    @Query("SELECT * FROM seasonal_packs")
    fun getAllPacks(): Flow<List<SeasonalPack>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPacks(packs: List<SeasonalPack>)
}
```

`SeasonalPackRepositoryImpl` filters active packs in Kotlin:
```kotlin
fun isActiveForMonth(pack: SeasonalPack, month: Int): Boolean {
    if (pack.season == "ALL_YEAR") return true
    return if (pack.activeFrom <= pack.activeTo) {
        month in pack.activeFrom..pack.activeTo
    } else {
        // Wraps year boundary (e.g. Oct–Feb: activeFrom=10, activeTo=2)
        month >= pack.activeFrom || month <= pack.activeTo
    }
}
```

### 8.2 Repository

```kotlin
interface SeasonalPackRepository {
    fun getActivePacks(): Flow<List<SeasonalPackWithMeals>>
}

data class SeasonalPackWithMeals(
    val pack: SeasonalPack,
    val meals: List<CatalogMeal>
)
```

`SeasonalPackRepositoryImpl` gets the current month, queries DAO, then resolves `catalogMealIds` (JSON array) against `CatalogRepository.getAllMeals()`.

### 8.3 Seeding from seasonal_packs.json

```json
// app/src/main/assets/seasonal_packs.json
[
  {
    "packId": "summer_coolers",
    "name": "Summer Coolers",
    "description": "Light, refreshing meals for hot days",
    "season": "SUMMER",
    "activeFrom": 3,
    "activeTo": 6,
    "meals": ["Curd Rice", "Lassi", "Cucumber Raita", "Poha", "Buttermilk Kadhi"]
  },
  {
    "packId": "monsoon_comfort",
    "name": "Monsoon Comfort",
    "description": "Warm, earthy meals for rainy days",
    "season": "MONSOON",
    "activeFrom": 7,
    "activeTo": 9,
    "meals": ["Khichdi", "Pakora", "Masala Chai Snack", "Dal Soup", "Corn Chaat"]
  },
  {
    "packId": "winter_warmers",
    "name": "Winter Warmers",
    "description": "Hearty, nourishing meals for cold weather",
    "season": "WINTER",
    "activeFrom": 10,
    "activeTo": 2,
    "meals": ["Sarson da Saag", "Gajar Halwa", "Methi Thepla", "Makki di Roti", "Panjiri"]
  },
  {
    "packId": "all_year_staples",
    "name": "All-Year Staples",
    "description": "Dependable family favourites any day of the year",
    "season": "ALL_YEAR",
    "activeFrom": 1,
    "activeTo": 12,
    "meals": ["Dal Makhani", "Rajma Chawal", "Khichdi", "Aloo Paratha", "Idli Sambar"]
  }
]
```

**Seeding strategy:** `AppDatabase.Callback.onCreate` seeds seasonal packs from JSON, same pattern as catalog seeding. Pack meals are matched by name to CatalogMeal entries — unmatched names are skipped gracefully.

### 8.4 UI — SeasonalPacksShelf on Home

A new composable `SeasonalPacksShelf` on `HomeScreen`, placed between the Favorites shelf and the Recommendations list.

```kotlin
// Visible only when activePacks.isNotEmpty()
SeasonalPacksShelf(
    packs = seasonalPacks,
    onPackTapped = { pack -> /* navigate to pack detail */ }
)
```

Each pack is a horizontal card showing pack name, season emoji (☀️ 🌧️ ❄️ 🍽️), and a count of meals in the pack.

**Pack detail:** A new bottom sheet `SeasonalPackSheet` showing the pack's meals as a vertical list of `ListItem`. Each meal has a "Cook This" button that opens the Mark As Cooked sheet (reusing `MarkAsCookedSheet`).

**New route:** `seasonal_pack/{packId}` — or implement as a `ModalBottomSheet` triggered directly from `HomeScreen` state, keeping nav simple.

`HomeViewModel` exposes `val seasonalPacks: StateFlow<List<SeasonalPackWithMeals>>` via `SeasonalPackRepository`.

---

## Section 9 — Settings Enhancements

### 9.1 Clear Data

In `SettingsScreen`, add a "Clear all data" option. On tap, show an `AlertDialog`:

> *"This permanently deletes all your meal history, feedback, and preferences. It cannot be undone."*

On confirm, `SettingsViewModel.clearAllData()` calls:
- `mealRepository.deleteAllMeals()`
- `feedbackRepository.deleteAllFeedback()`
- `catalogRepository.resetCatalog()` (deletes custom entries, re-seeds from JSON)
- `memberRepository.deleteAllMembers()`
- `recommendationEventRepository.deleteAllEvents()`
- `settingsRepository.clearAllPreferences()`

Each repository gets a `deleteAll*()` method backed by a `@Query("DELETE FROM table_name")` DAO method.

### 9.2 Member Profile Editor navigation

In `SettingsScreen`, each member row in the members list has an "Edit preferences ›" trailing action that navigates to `MemberProfileScreen` (see Section 6.3).

---

## Section 10 — Analytics Integration

`AnalyticsHelper` is injected into ViewModels that emit events:

| Event | ViewModel | When |
|---|---|---|
| `meal_logged` | `AddMealViewModel.saveMeal()` | After successful `mealRepository.saveMeal()` |
| `suggestion_tapped` | `HomeViewModel.emitTapped()` | When `SuggestionCard` is tapped |
| `tiffin_plan_saved` | `TiffinPlannerViewModel.pinMeal()` | After `tiffinPlanRepository.savePlan()` |
| `feedback_submitted` | `AddMealViewModel.saveFeedback()` and `HistoryViewModel.addFeedback()` | After `feedbackRepository.saveFeedback()` |

Event params: `meal_logged` includes `{ meal_type, was_quick_reuse }`. `suggestion_tapped` includes `{ catalog_meal_id, top_reason }`.

In CI / test environments, `AnalyticsModule` binds a `NoopAnalyticsHelper` that does nothing.

---

## Section 11 — Testing Strategy

**Unit tests (in `test/` source set):**

| Test class | What it covers |
|---|---|
| `Migration4To5Test` | Verifies notes column present post-migration |
| `Migration5To6Test` | Verifies seasonal_packs table created |
| `AddMealViewModelTest` | notes param, duplicate-save guard, time pre-select, quick-reuse |
| `HistoryViewModelTest` | Search debounce (advanceTimeBy 300ms), labels derived correctly |
| `TiffinPlannerViewModelTest` | Recent tiffin exclusion, leftover cue at position 1 |
| `RankingEngineTest` | Profile modifier: spicy suppression, portable boost, school-morning boost |
| `ReasonGeneratorTest` | Existing passing — no changes needed |
| `SeasonalPackRepositoryTest` | Active packs for month, ALL_YEAR always returned |
| `WorkerTest` (Robolectric) | Each pruning worker deletes correct rows |

**Migration tests** use `MigrationTestHelper` with Room's testing artifact.

**All existing 66 tests must continue to pass.**

---

## File Map (created or modified)

### New files
```
data/db/entity/SeasonalPack.kt
data/db/dao/SeasonalPackDao.kt
data/db/migrations/Migration4To5.kt
data/db/migrations/Migration5To6.kt
data/repository/SeasonalPackRepository.kt
data/repository/SeasonalPackRepositoryImpl.kt
data/worker/RecommendationEventPruneWorker.kt
data/worker/TiffinPlanCleanupWorker.kt
data/worker/MealPinCleanupWorker.kt
analytics/AnalyticsHelper.kt
analytics/NoopAnalyticsHelper.kt
di/AnalyticsModule.kt
ui/home/SeasonalPacksShelf.kt
ui/home/SeasonalPackSheet.kt
ui/settings/MemberProfileScreen.kt
ui/settings/MemberProfileViewModel.kt
assets/seasonal_packs.json
test/.../Migration4To5Test.kt
test/.../Migration5To6Test.kt
test/.../TiffinPlannerViewModelTest.kt
test/.../SeasonalPackRepositoryTest.kt
```

### Modified files
```
data/db/AppDatabase.kt                  (version 6, new entities/DAOs, new migrations)
data/db/entity/CatalogMeal.kt          (tiffinSuitable field audit)
data/db/dao/MealEntryDao.kt            (getMealsSince query)
data/db/dao/RecommendationEventDao.kt  (pruneOldEvents)
data/db/dao/TiffinPlanDao.kt           (deleteExpiredPlans)
data/db/dao/MealPinDao.kt              (deleteOldPins)
data/repository/MealRepository.kt      (getMealsSince)
data/repository/MealRepositoryImpl.kt
data/repository/MemberRepository.kt    (getMemberById, updateMember)
data/repository/MemberRepositoryImpl.kt
data/repository/RecommendationEventRepository.kt  (pruneOldEvents)
data/repository/RecommendationEventRepositoryImpl.kt
data/repository/TiffinPlanRepository.kt   (deleteExpiredPlans)
data/repository/TiffinPlanRepositoryImpl.kt
data/repository/MealPinRepository.kt      (deleteOldPins)
data/repository/MealPinRepositoryImpl.kt
data/repository/SettingsRepository.kt    (clearAllPreferences)
data/repository/SettingsRepositoryImpl.kt
data/repository/FeedbackRepository.kt    (deleteAllFeedback)
data/repository/FeedbackRepositoryImpl.kt
di/DatabaseModule.kt                    (new DAOs)
di/RepositoryModule.kt                  (new repos)
domain/engine/RankingEngine.kt          (profile modifiers, configurable IGNORED threshold)
domain/model/WeightMap.kt              (ignoredShownThreshold, ignoredWindowDays)
ui/addmeal/AddMealScreen.kt            (quick-reuse strip, notes field)
ui/addmeal/AddMealViewModel.kt         (notes param, debounce, time pre-select)
ui/history/HistoryViewModel.kt         (debounce, labelsMap)
ui/history/HistoryScreen.kt            (label chips on rows)
ui/history/MealDetailSheet.kt          (Cook Again button)
ui/home/HomeViewModel.kt               (clusterAlert, leftoverCue, seasonalPacks)
ui/home/HomeScreen.kt                  (clustering alert, time-gated chips, leftover chip, seasonal shelf)
ui/home/RecentlyCookedStrip.kt         (clusterAlert param)
ui/tiffin/TiffinPlannerViewModel.kt    (MealRepository, FeedbackRepository, exclusion, leftover)
ui/tiffin/TiffinPlannerScreen.kt       (leftover cue card, plan-to-log)
ui/navigation/AppNavigation.kt         (MemberProfile route, prefill args on add_meal, seasonal pack)
ui/navigation/Screen.kt               (new Screen.MemberProfile, Screen.SeasonalPack)
app/src/main/assets/catalog.json       (category, effortLevel, leftoverFriendly on all entries)
app/src/main/assets/ranking_config.json (ignoredShownThreshold, ignoredWindowDays)
app/build.gradle.kts                   (WorkManager, Firebase Analytics deps)
gradle/libs.versions.toml              (workmanager, firebase-analytics versions)
```

---

## Open Questions Resolved

All open questions from the original spec are resolved as follows, per spec recommendations:

| Question | Decision |
|---|---|
| Firebase Analytics timing | **Include in this phase** (AnalyticsHelper with Noop for CI) |
| IGNORED threshold configurability | **Configurable** via ranking_config.json |
| Privacy policy screen | **Deferred to post-V2.2** — Play Store listing sufficient for now |
| Multilingual catalog | **English-only + Unicode support** — user-entered names in any language |
| Photo deletion on meal delete | **Photos persist** — photo management deferred |
| Hindi / regional language | **Deferred** to post-V2 |
