# Family Meal Assistant — MVP Design Spec
**Date:** 2026-03-21
**Status:** Approved by user

---

## 1. Project Overview

A household meal-planning Android app that helps families decide what to cook next. It logs meals with photos, learns household and individual preferences over time, and surfaces ranked suggestions with human-readable reasons. The core differentiator is a hybrid recommendation engine that adapts its weights based on real cooking behavior.

---

## 2. Technical Foundation

| Attribute | Value |
|---|---|
| Platform | Android only |
| Package | `com.familymeal.assistant` |
| minSdk | **33** (user decision — overrides CLAUDE.md which says 26) |
| targetSdk | 36 |
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| Architecture | MVVM + Repository pattern |
| DI | Hilt |
| Local DB | Room (primary store, offline-first) |
| Backend | None for MVP |
| AI | Gemini multimodal API (async, non-blocking) |
| Build | Gradle (KSP for annotation processing) |

---

## 3. Architecture

Four layers with strict directional dependencies: UI → ViewModel → Domain → Data.

### 3.1 UI Layer — Jetpack Compose + Material3
Screens: `HomeScreen`, `AddMealScreen`, `HistoryScreen`, `OnboardingScreen`, `SettingsScreen`, `MemberProfilesScreen`.
All screens observe `sealed class UiState<T> { Loading, Success(data: T), Error(msg: String) }` from their ViewModel.

### 3.2 ViewModel Layer — Hilt-injected
`HomeViewModel`, `AddMealViewModel`, `HistoryViewModel`, `SettingsViewModel`, `OnboardingViewModel`, `MemberProfilesViewModel`.
**Rule:** ViewModels never call DAOs directly — always through Repository interfaces.

### 3.3 Domain Layer — Pure Kotlin (zero Android dependencies)
Fully unit-testable without an emulator or Robolectric.

| Class | Responsibility |
|---|---|
| `RankingEngine` | Scores and ranks candidate meals for a given audience and meal type |
| `WeightAdapter` | Applies nudges to `RankingWeight` values after feedback events |
| `MemberScoreAggregator` | Aggregates per-member feedback into `MemberMealScore` cache |
| `ReasonGenerator` | Produces human-readable reason strings from score components |
| `ImageClassifier` | Fires Gemini API call; returns `Flow<ClassificationResult>` |

### 3.4 Data Layer — Repository implementations + Room + Assets
Five repositories, each exposed as an interface with Hilt-bound implementations:

| Repository | Responsibility |
|---|---|
| `MealRepository` | `MealEntry` + `MealMemberCrossRef` CRUD |
| `MemberRepository` | `Member` CRUD |
| `FeedbackRepository` | `FeedbackSignal` insert + `MemberMealScore` update (in one transaction) |
| `CatalogRepository` | `CatalogMeal` queries + `seedIfEmpty()` |
| `WeightRepository` | `RankingWeight` reads/writes |
| `SettingsRepository` | App settings in `SharedPreferences`: Gemini API key (in `EncryptedSharedPreferences`), `api_key_banner_dismissed` flag, exploration ratio |

---

## 4. Data Model (Room Entities)

### `Member`
```
id: Long (PK, autoGenerate)
name: String
dietType: DietType          // Veg | Egg | NonVeg | Mixed
birthYear: Int?             // optional; used to identify child members for KidsLiked attribution
isActive: Boolean
createdAt: Long
```
**Index:** `isActive`.
**Deletion policy:** Members are never hard-deleted — only deactivated (`isActive = false`). All historical data referencing a deactivated member is preserved as-is.

### `MealEntry`
```
id: Long (PK, autoGenerate)
name: String
photoUri: String?
mealType: MealType          // Breakfast | Lunch | Dinner | Tiffin | Snack
cookedAt: Long              // epoch millis
catalogMealId: Long?        // FK → CatalogMeal; nullable for free-text meals
aiSuggestedName: String?
classificationPending: Boolean
notes: String?
```
**Indexes:** `(mealType, cookedAt)` for History filtering; `cookedAt` for recency scoring.
**FK:** `catalogMealId` references `CatalogMeal(id)` with `onDelete = SET_NULL` (deleting a catalog entry does not delete meal history).
**TTL cleanup:** On app start, `MealRepository.reconcilePendingClassifications()` sets `classificationPending = false` for all entries where `cookedAt < (System.currentTimeMillis() - 24 * 60 * 60 * 1000)` — i.e., entries older than 24 hours that are still flagged pending.

### `MealMemberCrossRef`
```
mealEntryId: Long           // FK → MealEntry(id), onDelete = CASCADE
memberId: Long              // FK → Member(id), onDelete = RESTRICT (members can't be hard-deleted)
primaryKeys = ["mealEntryId", "memberId"]
```
**Index:** `memberId` (for History "filter by member" queries — the composite PK leads on `mealEntryId`, making `memberId`-only lookups a full scan without this index).

### `CatalogMeal`
```
id: Long (PK, autoGenerate)
name: String
cuisine: String
dietType: DietType
mealTypes: String           // comma-separated: "Lunch,Dinner"
tags: String?
isUserAdded: Boolean
```

### `FeedbackSignal`
```
id: Long (PK, autoGenerate)
mealEntryId: Long           // FK → MealEntry(id), onDelete = CASCADE
memberId: Long?             // null = household-level feedback
signalType: FeedbackType    // MakeAgain | GoodForTiffin | KidsLiked | TooMuchWork | NotAHit
createdAt: Long
```
**Indexes:** `mealEntryId`; `memberId`.

### `RankingWeight`
```
signalName: String (PK)     // "recency" | "makeAgain" | "notAHit" | "tooMuchWork" | "tiffin" | "memberMatch"
value: Float                // 0.0–1.0
defaultValue: Float
lastNudgedAt: Long
```
`KidsLiked` has no `RankingWeight` row — it is member-specific and feeds `MemberMealScore` only.

### `MemberMealScore`
```
memberId: Long              // FK → Member(id), onDelete = CASCADE (score cache purged if member ever deleted, though members are not hard-deleted in practice)
catalogMealId: Long         // FK → CatalogMeal(id), onDelete = CASCADE
positiveSignals: Int        // MakeAgain + KidsLiked count for this member
negativeSignals: Int        // NotAHit + TooMuchWork count for this member
timesCooked: Int
lastCookedAt: Long?
primaryKeys = ["memberId", "catalogMealId"]
```
**Scope:** Only populated for `MealEntry` rows with a non-null `catalogMealId`. Free-text meals do not contribute to per-member learning (MVP limitation).

**Household-level feedback attribution:** When a `FeedbackSignal` is saved with `memberId = null`, `MemberMealScore` is updated for all members present in the meal's `MealMemberCrossRef` rows. This update is performed in the same DB transaction as the `FeedbackSignal` insert, inside `FeedbackRepository.saveFeedback()`.

**KidsLiked attribution (Family audience):** When `KidsLiked` is submitted for a Family-audience meal, `MemberMealScore.positiveSignals` is incremented for members in the meal's cross-ref who have a non-null `birthYear` (identified child members). If no members with `birthYear` are present in the cross-ref, all cross-ref members receive the increment. The `FeedbackSignal` row is saved with `memberId = null`.

---

## 5. Ranking Engine — Hybrid Approach

### 5.1 Candidate Pool
Start with all `CatalogMeal` rows. Hard-filter: remove meals incompatible with any member in the selected audience.
- Audience = specific member → filter by that member's `dietType`
- Audience = Family → keep only meals compatible with the most restrictive active member (e.g., if any active member is Veg, exclude NonVeg meals)

### 5.2 Base Score
```
baseScore =
    W_recency      × recencyBonus(daysSinceLastCooked)
  + W_makeAgain    × makeAgainCount(meal, audience)
  − W_notAHit      × notAHitCount(meal, audience)
  − W_tooMuchWork  × effortPenalty(meal, audience)
  + W_tiffin       × tiffinBonus(meal, currentMealType)
  + W_memberMatch  × dietCompatibilityScore(meal, audience)
```

**`recencyBonus(days) = tanh(days / 14)`**
- A meal cooked today (days = 0) → bonus = 0.0 (not promoted)
- A meal last cooked 14 days ago → bonus ≈ 0.96 (strongly promoted)
- A meal never cooked → days is set to a large constant (e.g., 90) → bonus ≈ 1.0
- Higher days = higher bonus, meaning meals not cooked recently are scored higher (anti-repetition)

`KidsLiked` has no term in the base score formula. It influences scoring exclusively through the per-member modifier (Section 5.3).

**Cold-start behavior:** When fewer than 5 meals have been logged, `MemberMealScore` and `FeedbackSignal` tables are sparse and most scores approach zero. The engine still runs the full pipeline; the effective result is a cuisine-diverse diet-filtered ordering. This is acceptable — the catalog is curated and any suggestion is reasonable at cold-start.

### 5.3 Per-Member Modifier
```
memberModifier = avg( (positiveSignals − negativeSignals) / max(timesCooked, 1) )
                 across all active members in audience
memberModifier = clamp(memberModifier, −0.5, +1.0)
adjustedScore  = baseScore × (1 + memberModifier)
```
Source: `MemberMealScore` cache. Meals with no `MemberMealScore` rows use `memberModifier = 0`.

### 5.4 Exploration Budget
- Rank all candidates by `adjustedScore` (descending)
- Fill top `floor(N × (1 − explorationRatio))` slots from the ranked list (exploitation)
- Fill remaining slots by randomly sampling from the diet-compatible catalog, **excluding any meals already selected for exploitation slots**, where the meal was last cooked more than 30 days ago (or never cooked)
- At cold-start (fewer than 5 meals logged), the 30-day threshold is waived and the entire diet-compatible catalog is the exploration pool
- Default `explorationRatio = 0.20`; user-configurable in Settings (range 0.10–0.30)
- `N` is the total number of suggestion cards shown (default: 5)

### 5.5 Reason String
`ReasonGenerator` inspects each score component. Components above their threshold contribute a reason chip:

| Condition | Chip |
|---|---|
| daysSinceLastCooked ≥ 14 | "Not had in N days" |
| makeAgainCount ≥ 2 | "Family loved it N×" |
| Member-specific positive signal | "[Name] likes this" |
| tiffinBonus triggered | "Good for tiffin" |
| Exploration slot | "Trying something new" |

At cold-start, when all scores are near-zero, no reason chips are shown (empty chip list is a valid UI state).

### 5.6 Weight Nudge (after each FeedbackSignal)
Fires asynchronously via `WeightAdapter` after a feedback event is saved:

| Signal | Effect |
|---|---|
| MakeAgain | `W_makeAgain += 0.05` |
| NotAHit | `W_notAHit += 0.05` |
| TooMuchWork | `W_tooMuchWork += 0.05` |
| GoodForTiffin | `W_tiffin += 0.03` |
| KidsLiked | `MemberMealScore[member(s)].positiveSignals++` only — no weight nudge |

**Bounds:** `value = clamp(value + delta, 0.1 × defaultValue, 2.0 × defaultValue)`. User can reset any individual weight to `defaultValue` from Settings.

---

## 6. Navigation & Screen Structure

### 6.1 Onboarding (first-launch only)
1. Household name entry
2. Add members: name + diet type + optional birth year (repeatable, minimum 1 member)
3. Catalog seeded silently in background — user lands on Home immediately

### 6.2 Tab 1 — Home
- Meal type filter chips: Breakfast | Lunch | Dinner | Tiffin | Snack
- Audience selector: Family + one chip per active member
- 3–5 ranked suggestion cards, each showing meal name + reason chips (empty if cold-start)
- Tap a card → **"Mark as Cooked" bottom sheet** collecting:
  - Meal name — pre-filled from the suggestion (read-only on this sheet)
  - Meal type — pre-filled from the active filter chip (editable via dropdown)
  - Audience — pre-filled from the active audience selector (editable)
  - Feedback signal multi-select: MakeAgain | KidsLiked | GoodForTiffin | TooMuchWork | NotAHit (all optional)
  - Confirm button — creates `MealEntry` + `MealMemberCrossRef` rows; triggers weight nudge async

### 6.3 Tab 2 — Add Meal (photo-first)
1. Camera launches on tab tap (gallery fallback via small icon in corner)
2. Photo taken → `MealEntry` saved to Room instantly (`classificationPending = true`)
3. Form shown: name field (placeholder "Tap to name…" with subtle shimmer pulse while classification is in-flight), meal type picker, audience selector
4. Gemini call fires in background coroutine; on success, shimmer stops and name field populates; user can accept or type their own
5. On Gemini failure (error, timeout 10s, or API key absent): shimmer stops, placeholder reverts to "Tap to name…" — no error shown
6. **One-time API key banner:** if Gemini API key is absent, a dismissible banner appears at top of the form: "Add Gemini API key in Settings for auto meal naming." Shown only once — the dismissed state is stored as boolean `api_key_banner_dismissed` in `SharedPreferences` via `SettingsRepository`. After dismissal, the banner never appears again regardless of key state.
7. Save button finalises the entry (`classificationPending = false`, name and meal type written)
8. **All failures are silent:** Gemini error → name field stays editable, no toast; photo failure → entry saves with `photoUri = null`

### 6.4 Tab 3 — History
- Chronological list of `MealEntry` rows (newest first)
- Filter chips: by meal type, by member, by date range
- Entries where `classificationPending = true` and `cookedAt ≥ (now - 24h)` show a subtle pending indicator; entries with `classificationPending = true` but `cookedAt < (now - 24h)` are cleaned up on next app start
- Tap entry → detail view: photo, name, audience, existing feedback chips, option to add new feedback

### 6.5 Settings (⋮ top-bar menu)
- **Members** → navigates to `MemberProfilesScreen` (owned by `MemberProfilesViewModel`): add / edit / deactivate members
- **Ranking weights:** slider per signal (`recency`, `makeAgain`, `notAHit`, `tooMuchWork`, `tiffin`, `memberMatch`), showing live value and default; per-weight Reset button
- **Exploration ratio:** slider 10%–30%, default 20%
- **Gemini API key:** text field (`EncryptedSharedPreferences`), clear button; changes stored via `SettingsRepository`
- **About / version**

---

## 7. AI Classification Flow

`ImageClassifier` wraps the Gemini multimodal API:
- Input: photo URI → reads bytes → sends with prompt: *"Identify the meal in this photo. Return only the meal name, be specific (e.g. 'Dal Makhani' not 'Indian food')."*
- Output: `Flow<ClassificationResult>` where `ClassificationResult = Success(name) | Failure`
- Timeout: 10 seconds; emits `Failure` on timeout
- If API key is absent (checked via `SettingsRepository`), emits `Failure` immediately without a network call
- Called from `AddMealViewModel` in `viewModelScope`; coroutine cancellation does not affect the already-saved `MealEntry`

**UI state contract:**
- In-flight: name field shows shimmer pulse on placeholder text
- Success: shimmer stops, field shows suggested name (user can override by typing)
- Failure / absent key: shimmer stops, field returns to "Tap to name…" placeholder (no error indicator)

---

## 8. Testing Strategy

| Layer | Tool | What's tested |
|---|---|---|
| Domain (RankingEngine, WeightAdapter, MemberScoreAggregator, ReasonGenerator) | JUnit 4 | Score correctness, recencyBonus formula direction, nudge bounds, reason chip generation, exploration deduplication, cold-start near-zero scores, member modifier clamping |
| ImageClassifier | JUnit 4 + mock HTTP client | Non-blocking contract (coroutine cancel doesn't corrupt saved entry); timeout emits Failure; absent API key emits Failure immediately without network call |
| Repositories | Room in-memory DB | CRUD; cascade delete behaviors; `MemberMealScore` updated in same transaction as `FeedbackSignal`; household-level feedback attribution; KidsLiked child-member attribution; TTL cleanup (`reconcilePendingClassifications`) |
| ViewModels | JUnit 4 + Turbine | UiState transitions (Loading → Success / Error); one-time API key banner shown once then never again |
| UI | Compose semantics tests | Suggestion cards render reason chips; feedback sheet pre-fills from active filter/audience; shimmer visible during classification; empty chip list renders without error |

All new features must include unit tests in the `test/` source set before the feature is considered done.

---

## 9. Cold-Start Catalog

- Minimum 50 meals, pan-world (Indian, Italian, Mexican, Japanese, Mediterranean, etc.)
- Covers all 4 diet types: Veg, Egg, NonVeg, Mixed
- Covers all 5 meal types in appropriate combinations
- Bundled as `assets/catalog.json`; loaded into Room on first launch via `CatalogRepository.seedIfEmpty()` — no-op on subsequent launches
- Default ranking weights bundled as `assets/ranking_config.json`; loaded into `RankingWeight` table by `WeightRepository.seedIfEmpty()` — no-op on subsequent launches
- `CatalogMeal.isUserAdded = false` for all seeded meals

---

## 10. Key Constraints & Rules

- Meal save must **never** be blocked by AI classification failure
- `RankingEngine` scoring formula must not be modified without updating `assets/ranking_config.json` defaults
- `MealEntry` schema changes require a Room migration — no destructive migrations
- Ranking weights bounded: `clamp(newValue, 0.1 × defaultValue, 2.0 × defaultValue)`
- Repository interfaces are the only data access path from ViewModels
- `MemberMealScore` must be updated in the same DB transaction as the associated `FeedbackSignal` insert
- `KidsLiked` is intentionally member-specific only — no `RankingWeight` row exists for it
- `MemberMealScore` is only maintained for catalog-linked meals (`catalogMealId != null`)
- Members are never hard-deleted — only deactivated (`isActive = false`); historical data is preserved
- On app start, `MealRepository.reconcilePendingClassifications()` resets `classificationPending = false` for entries where `cookedAt < (now − 24h)`
- Exploration pool meals are always distinct from exploitation pool meals (no duplicate cards)
- Gemini API key is stored in `EncryptedSharedPreferences` and accessed exclusively via `SettingsRepository`
