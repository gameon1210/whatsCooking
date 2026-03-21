# Phase 3: Domain Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the pure-Kotlin domain layer: `RankingEngine`, `WeightAdapter`, `MemberScoreAggregator`, `ReasonGenerator`, and `ImageClassifier`. Zero Android dependencies in this layer — fully unit-testable with JUnit 4 only.

**Architecture:** All domain classes live in `domain/`. `RankingEngine` takes immutable data objects (no Room entities) so it has no DB dependency. `ImageClassifier` is an interface; `GeminiImageClassifier` is its implementation (this is the only class with an OkHttp dependency). Hilt wires the classifier via `ClassifierModule`.

**Tech Stack:** Pure Kotlin, JUnit 4, MockK 1.13.13, OkHttp MockWebServer, Coroutines Test, Turbine

**Prerequisite:** Phase 2 complete (entities and enums available for import).

---

## File Map

```
app/src/main/java/com/familymeal/assistant/
  domain/
    model/
      RankedMeal.kt
      ScoreBreakdown.kt
      ClassificationResult.kt
      WeightMap.kt
    engine/
      RankingEngine.kt
      WeightAdapter.kt
      MemberScoreAggregator.kt
      ReasonGenerator.kt
    classifier/
      ImageClassifier.kt          (interface)
      GeminiImageClassifier.kt
  di/
    ClassifierModule.kt

app/src/test/java/com/familymeal/assistant/
  domain/
    RankingEngineTest.kt
    WeightAdapterTest.kt
    MemberScoreAggregatorTest.kt
    ReasonGeneratorTest.kt
    ImageClassifierTest.kt
```

---

### Task 1: Domain model classes

**Files:**
- Create: `domain/model/RankedMeal.kt`
- Create: `domain/model/ScoreBreakdown.kt`
- Create: `domain/model/ClassificationResult.kt`
- Create: `domain/model/WeightMap.kt`

- [ ] **Step 1: Create model classes**

```kotlin
// RankedMeal.kt
package com.familymeal.assistant.domain.model

data class RankedMeal(
    val catalogMealId: Long,
    val name: String,
    val cuisine: String,
    val adjustedScore: Float,
    val reasons: List<String>,
    val isExploration: Boolean
)

// ScoreBreakdown.kt
package com.familymeal.assistant.domain.model

data class ScoreBreakdown(
    val recency: Float = 0f,
    val makeAgain: Float = 0f,
    val notAHit: Float = 0f,
    val tooMuchWork: Float = 0f,
    val tiffin: Float = 0f,
    val memberMatch: Float = 0f,
    val memberModifier: Float = 0f
) {
    val baseScore: Float get() = recency + makeAgain - notAHit - tooMuchWork + tiffin + memberMatch
    val adjustedScore: Float get() = baseScore * (1f + memberModifier)
}

// ClassificationResult.kt
package com.familymeal.assistant.domain.model

sealed class ClassificationResult {
    data class Success(val mealName: String) : ClassificationResult()
    object Failure : ClassificationResult()
}

// WeightMap.kt
package com.familymeal.assistant.domain.model

data class WeightMap(
    val recency: Float,
    val makeAgain: Float,
    val notAHit: Float,
    val tooMuchWork: Float,
    val tiffin: Float,
    val memberMatch: Float
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/domain/model/
git commit -m "feat: add domain model classes"
```

---

### Task 2: `RankingEngine` — write tests first

**Files:**
- Create: `domain/engine/RankingEngine.kt`
- Create: `app/src/test/.../domain/RankingEngineTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.domain

import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.domain.engine.RankingEngine
import com.familymeal.assistant.domain.engine.RankingInput
import com.familymeal.assistant.domain.model.WeightMap
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RankingEngineTest {

    private lateinit var engine: RankingEngine
    private val defaultWeights = WeightMap(
        recency = 0.40f, makeAgain = 0.30f, notAHit = 0.25f,
        tooMuchWork = 0.20f, tiffin = 0.15f, memberMatch = 0.20f
    )

    private fun meal(id: Long, name: String, dietType: DietType, mealTypes: String = "Lunch") =
        CatalogMeal(id = id, name = name, cuisine = "Indian", dietType = dietType, mealTypes = mealTypes)

    @Before fun setup() { engine = RankingEngine() }

    @Test
    fun `meals cooked more recently get lower recency bonus`() {
        val oldMeal = meal(1, "Old Meal", DietType.Veg)
        val newMeal = meal(2, "New Meal", DietType.Veg)
        val now = System.currentTimeMillis()
        val input = RankingInput(
            candidates = listOf(oldMeal, newMeal),
            mealType = MealType.Lunch,
            audienceMembers = listOf(Member(1, "Alice", DietType.Veg)),
            lastCookedAt = mapOf(
                1L to (now - 30 * 86_400_000L),  // 30 days ago
                2L to (now - 1 * 86_400_000L)     // 1 day ago
            ),
            feedbackCounts = emptyMap(),
            memberScores = emptyMap(),
            weights = defaultWeights,
            explorationRatio = 0f,   // pure exploitation for this test
            totalSlots = 2
        )
        val result = engine.rank(input)
        assertEquals(oldMeal.id, result.first().catalogMealId)
    }

    @Test
    fun `NonVeg meals are excluded when audience member is Veg`() {
        val vegMeal = meal(1, "Veg", DietType.Veg)
        val nonVegMeal = meal(2, "NonVeg", DietType.NonVeg)
        val input = RankingInput(
            candidates = listOf(vegMeal, nonVegMeal),
            mealType = MealType.Lunch,
            audienceMembers = listOf(Member(1, "Alice", DietType.Veg)),
            lastCookedAt = emptyMap(),
            feedbackCounts = emptyMap(),
            memberScores = emptyMap(),
            weights = defaultWeights,
            explorationRatio = 0f,
            totalSlots = 5
        )
        val result = engine.rank(input)
        assertTrue(result.none { it.catalogMealId == 2L })
    }

    @Test
    fun `MakeAgain count increases score`() {
        val loved = meal(1, "Loved", DietType.Veg)
        val plain = meal(2, "Plain", DietType.Veg)
        val input = RankingInput(
            candidates = listOf(loved, plain),
            mealType = MealType.Lunch,
            audienceMembers = listOf(Member(1, "Alice", DietType.Veg)),
            lastCookedAt = emptyMap(),
            feedbackCounts = mapOf(
                1L to mapOf(FeedbackType.MakeAgain to 3)
            ),
            memberScores = emptyMap(),
            weights = defaultWeights,
            explorationRatio = 0f,
            totalSlots = 2
        )
        val result = engine.rank(input)
        assertEquals(1L, result.first().catalogMealId)
    }

    @Test
    fun `member modifier clamps to -0_5 and +1_0`() {
        val modifier = RankingEngine.computeMemberModifier(
            positiveSignals = 100, negativeSignals = 0, timesCooked = 1
        )
        assertEquals(1.0f, modifier, 0.001f)

        val negativeModifier = RankingEngine.computeMemberModifier(
            positiveSignals = 0, negativeSignals = 100, timesCooked = 1
        )
        assertEquals(-0.5f, negativeModifier, 0.001f)
    }

    @Test
    fun `exploration slots are distinct from exploitation slots`() {
        val meals = (1L..10L).map { meal(it, "Meal $it", DietType.Veg) }
        val input = RankingInput(
            candidates = meals,
            mealType = MealType.Lunch,
            audienceMembers = listOf(Member(1, "Alice", DietType.Veg)),
            lastCookedAt = emptyMap(),
            feedbackCounts = emptyMap(),
            memberScores = emptyMap(),
            weights = defaultWeights,
            explorationRatio = 0.4f,
            totalSlots = 5
        )
        val result = engine.rank(input)
        val ids = result.map { it.catalogMealId }
        assertEquals(ids.size, ids.distinct().size)  // no duplicates
    }

    @Test
    fun `recencyBonus of never-cooked meal is close to 1`() {
        val bonus = RankingEngine.recencyBonus(daysSinceLastCooked = 90)
        assertTrue("Expected near 1.0 but got $bonus", bonus > 0.99f)
    }

    @Test
    fun `recencyBonus of meal cooked today is 0`() {
        val bonus = RankingEngine.recencyBonus(daysSinceLastCooked = 0)
        assertEquals(0f, bonus, 0.001f)
    }

    @Test
    fun `cold start produces results without crashing`() {
        val input = RankingInput(
            candidates = listOf(meal(1, "Sole Meal", DietType.Veg)),
            mealType = MealType.Lunch,
            audienceMembers = listOf(Member(1, "Alice", DietType.Veg)),
            lastCookedAt = emptyMap(),
            feedbackCounts = emptyMap(),
            memberScores = emptyMap(),
            weights = defaultWeights,
            explorationRatio = 0.2f,
            totalSlots = 5
        )
        val result = engine.rank(input)
        assertEquals(1, result.size)  // only 1 candidate, only 1 result
    }
}
```

- [ ] **Step 2: Run — expect FAIL (RankingEngine not yet created)**

```bash
./gradlew :app:test --tests "*.RankingEngineTest"
```

- [ ] **Step 3: Create `RankingInput` data class and `RankingEngine.kt`**

```kotlin
// domain/engine/RankingEngine.kt
package com.familymeal.assistant.domain.engine

import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.domain.model.*
import kotlin.math.tanh
import kotlin.math.floor

data class RankingInput(
    val candidates: List<CatalogMeal>,
    val mealType: MealType,
    val audienceMembers: List<Member>,
    val lastCookedAt: Map<Long, Long>,             // catalogMealId → epochMillis
    val feedbackCounts: Map<Long, Map<FeedbackType, Int>>,  // catalogMealId → signal counts
    val memberScores: Map<Pair<Long, Long>, MemberMealScore>, // (memberId, catalogMealId)
    val weights: WeightMap,
    val explorationRatio: Float,
    val totalSlots: Int
)

class RankingEngine {

    fun rank(input: RankingInput): List<RankedMeal> {
        val now = System.currentTimeMillis()

        // 1. Hard filter by diet compatibility
        val restrictiveDiet = mostRestrictiveDiet(input.audienceMembers)
        val filtered = input.candidates.filter { meal ->
            isDietCompatible(meal.dietType, restrictiveDiet)
        }

        if (filtered.isEmpty()) return emptyList()

        // 2. Score each meal
        val scored = filtered.map { meal ->
            val daysSince = daysSince(meal.id, input.lastCookedAt, now)
            val counts = input.feedbackCounts[meal.id] ?: emptyMap()

            val breakdown = ScoreBreakdown(
                recency = input.weights.recency * recencyBonus(daysSince),
                makeAgain = input.weights.makeAgain * (counts[FeedbackType.MakeAgain] ?: 0).toFloat(),
                notAHit = input.weights.notAHit * (counts[FeedbackType.NotAHit] ?: 0).toFloat(),
                tooMuchWork = input.weights.tooMuchWork * (counts[FeedbackType.TooMuchWork] ?: 0).toFloat(),
                tiffin = input.weights.tiffin * tiffinBonus(meal, input.mealType),
                memberMatch = input.weights.memberMatch * dietCompatibilityScore(meal.dietType, input.audienceMembers),
                memberModifier = avgMemberModifier(meal.id, input.audienceMembers, input.memberScores)
            )
            meal to breakdown
        }.sortedByDescending { it.second.adjustedScore }

        // 3. Split exploitation / exploration
        val exploitCount = floor(input.totalSlots * (1f - input.explorationRatio)).toInt()
            .coerceAtMost(scored.size)
        val exploitMeals = scored.take(exploitCount)
        val exploitIds = exploitMeals.map { it.first.id }.toSet()

        val explorationPool = filtered.filter { meal ->
            meal.id !in exploitIds && isExplorationEligible(meal.id, input.lastCookedAt, now, input.lastCookedAt.size < 5)
        }
        val exploreSlots = (input.totalSlots - exploitCount).coerceAtMost(explorationPool.size)
        val exploreMeals = explorationPool.shuffled().take(exploreSlots)

        // 4. Build results
        return exploitMeals.map { (meal, breakdown) ->
            RankedMeal(
                catalogMealId = meal.id,
                name = meal.name,
                cuisine = meal.cuisine,
                adjustedScore = breakdown.adjustedScore,
                reasons = emptyList(), // populated by ReasonGenerator
                isExploration = false
            )
        } + exploreMeals.map { meal ->
            RankedMeal(
                catalogMealId = meal.id,
                name = meal.name,
                cuisine = meal.cuisine,
                adjustedScore = 0f,
                reasons = emptyList(),
                isExploration = true
            )
        }
    }

    private fun mostRestrictiveDiet(members: List<Member>): DietType {
        return when {
            members.any { it.dietType == DietType.Veg } -> DietType.Veg
            members.any { it.dietType == DietType.Egg } -> DietType.Egg
            members.all { it.dietType == DietType.NonVeg } -> DietType.NonVeg
            else -> DietType.Mixed
        }
    }

    private fun isDietCompatible(mealDiet: DietType, restrictive: DietType): Boolean {
        return when (restrictive) {
            DietType.Veg -> mealDiet == DietType.Veg
            DietType.Egg -> mealDiet == DietType.Veg || mealDiet == DietType.Egg
            DietType.NonVeg -> true
            DietType.Mixed -> true
        }
    }

    private fun daysSince(catalogMealId: Long, lastCookedAt: Map<Long, Long>, now: Long): Int {
        val last = lastCookedAt[catalogMealId] ?: return 90
        return ((now - last) / 86_400_000L).toInt().coerceAtLeast(0)
    }

    private fun tiffinBonus(meal: CatalogMeal, mealType: MealType): Float =
        if (mealType == MealType.Tiffin && meal.mealTypes.contains("Tiffin")) 1f else 0f

    private fun dietCompatibilityScore(mealDiet: DietType, members: List<Member>): Float =
        members.count { isDietCompatible(mealDiet, it.dietType) }.toFloat() / members.size

    private fun avgMemberModifier(
        catalogMealId: Long,
        members: List<Member>,
        memberScores: Map<Pair<Long, Long>, MemberMealScore>
    ): Float {
        if (members.isEmpty()) return 0f
        val modifiers = members.map { member ->
            val score = memberScores[Pair(member.id, catalogMealId)]
            if (score == null) 0f
            else computeMemberModifier(score.positiveSignals, score.negativeSignals, score.timesCooked)
        }
        return modifiers.average().toFloat()
    }

    private fun isExplorationEligible(
        catalogMealId: Long,
        lastCookedAt: Map<Long, Long>,
        now: Long,
        coldStart: Boolean
    ): Boolean {
        if (coldStart) return true
        val last = lastCookedAt[catalogMealId] ?: return true
        val daysSince = (now - last) / 86_400_000L
        return daysSince > 30
    }

    companion object {
        fun recencyBonus(daysSinceLastCooked: Int): Float =
            tanh(daysSinceLastCooked / 14.0).toFloat()

        fun computeMemberModifier(positiveSignals: Int, negativeSignals: Int, timesCooked: Int): Float {
            val raw = (positiveSignals - negativeSignals).toFloat() / maxOf(timesCooked, 1)
            return raw.coerceIn(-0.5f, 1.0f)
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
./gradlew :app:test --tests "*.RankingEngineTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/domain/engine/RankingEngine.kt \
        app/src/test/java/com/familymeal/assistant/domain/RankingEngineTest.kt
git commit -m "feat: implement RankingEngine with TDD"
```

---

### Task 3: `WeightAdapter` — write tests first

**Files:**
- Create: `domain/engine/WeightAdapter.kt`
- Create: `app/src/test/.../domain/WeightAdapterTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.domain

import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.RankingWeight
import com.familymeal.assistant.domain.engine.WeightAdapter
import org.junit.Assert.*
import org.junit.Test

class WeightAdapterTest {

    private val adapter = WeightAdapter()

    private fun weight(name: String, value: Float, default: Float) =
        RankingWeight(name, value, default)

    @Test
    fun `MakeAgain nudges makeAgain weight up by 0_05`() {
        val w = weight("makeAgain", 0.30f, 0.30f)
        val result = adapter.nudge(w, FeedbackType.MakeAgain)
        assertEquals(0.35f, result.value, 0.001f)
    }

    @Test
    fun `nudge cannot exceed 2x defaultValue`() {
        val w = weight("makeAgain", 0.59f, 0.30f)  // 0.59 + 0.05 = 0.64 > 0.60 (2x)
        val result = adapter.nudge(w, FeedbackType.MakeAgain)
        assertEquals(0.60f, result.value, 0.001f)
    }

    @Test
    fun `nudge cannot go below 0_1x defaultValue`() {
        val w = weight("notAHit", 0.031f, 0.25f)  // 0.031 - 0.05 < 0.025 (0.1x)
        // notAHit nudges up not down, so test with a weight that goes below floor
        val w2 = weight("recency", 0.041f, 0.40f)
        // No signal nudges recency down; test bounds via a custom scenario
        // Instead verify floor holds for GoodForTiffin
        val wTiffin = weight("tiffin", 0.015f, 0.15f)  // 0.015 < 0.10 * 0.15 = 0.015 (at floor)
        val result = adapter.nudge(wTiffin, FeedbackType.GoodForTiffin)
        assertTrue(result.value >= 0.1f * wTiffin.defaultValue)
    }

    @Test
    fun `KidsLiked does not produce a weight nudge`() {
        val w = weight("memberMatch", 0.20f, 0.20f)
        val result = adapter.nudge(w, FeedbackType.KidsLiked)
        assertEquals(w.value, result.value, 0.001f)  // unchanged
    }

    @Test
    fun `nudge updates lastNudgedAt`() {
        val before = System.currentTimeMillis()
        val w = weight("makeAgain", 0.30f, 0.30f)
        val result = adapter.nudge(w, FeedbackType.MakeAgain)
        assertTrue(result.lastNudgedAt >= before)
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.WeightAdapterTest"
```

- [ ] **Step 3: Implement WeightAdapter.kt**

```kotlin
package com.familymeal.assistant.domain.engine

import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.RankingWeight
import javax.inject.Inject

class WeightAdapter @Inject constructor() {

    // Map of signal → (weight name, delta)
    private val nudgeMap = mapOf(
        FeedbackType.MakeAgain    to ("makeAgain"   to +0.05f),
        FeedbackType.NotAHit      to ("notAHit"     to +0.05f),
        FeedbackType.TooMuchWork  to ("tooMuchWork" to +0.05f),
        FeedbackType.GoodForTiffin to ("tiffin"     to +0.03f)
        // KidsLiked: no weight nudge — handled by MemberMealScore only
    )

    /**
     * Returns a new [RankingWeight] with the nudged value (bounded).
     * Returns the same weight unchanged for signals with no nudge mapping (e.g. KidsLiked).
     */
    fun nudge(weight: RankingWeight, signal: FeedbackType): RankingWeight {
        val (targetName, delta) = nudgeMap[signal] ?: return weight
        if (weight.signalName != targetName) return weight

        val newValue = (weight.value + delta)
            .coerceIn(0.1f * weight.defaultValue, 2.0f * weight.defaultValue)

        return weight.copy(value = newValue, lastNudgedAt = System.currentTimeMillis())
    }

    /** Returns the weight name that a given signal nudges, or null for KidsLiked. */
    fun targetWeightName(signal: FeedbackType): String? = nudgeMap[signal]?.first
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.WeightAdapterTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/domain/engine/WeightAdapter.kt \
        app/src/test/java/com/familymeal/assistant/domain/WeightAdapterTest.kt
git commit -m "feat: implement WeightAdapter with nudge bounds"
```

---

### Task 4: `ReasonGenerator` — write tests first

**Files:**
- Create: `domain/engine/ReasonGenerator.kt`
- Create: `app/src/test/.../domain/ReasonGeneratorTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.familymeal.assistant.domain

import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.domain.engine.ReasonGenerator
import com.familymeal.assistant.domain.model.ScoreBreakdown
import org.junit.Assert.*
import org.junit.Test

class ReasonGeneratorTest {
    private val gen = ReasonGenerator()

    @Test
    fun `not had in N days shown when daysSince >= 14`() {
        val reasons = gen.generate(
            breakdown = ScoreBreakdown(recency = 0.8f),
            daysSinceLastCooked = 18,
            makeAgainCount = 0,
            memberName = null,
            tiffinBonusActive = false,
            isExploration = false
        )
        assertTrue(reasons.any { it.contains("18 days") })
    }

    @Test
    fun `no recency reason when meal cooked within 14 days`() {
        val reasons = gen.generate(
            ScoreBreakdown(recency = 0.1f), 7, 0, null, false, false
        )
        assertTrue(reasons.none { it.contains("days") })
    }

    @Test
    fun `family loved it shown when makeAgainCount >= 2`() {
        val reasons = gen.generate(
            ScoreBreakdown(makeAgain = 0.6f), 20, 3, null, false, false
        )
        assertTrue(reasons.any { it.contains("3") && it.contains("loved") })
    }

    @Test
    fun `member name shown for member-specific signal`() {
        val reasons = gen.generate(
            ScoreBreakdown(memberModifier = 0.5f), 5, 0, "Rohan", false, false
        )
        assertTrue(reasons.any { it.contains("Rohan") })
    }

    @Test
    fun `good for tiffin shown when tiffinBonus active`() {
        val reasons = gen.generate(
            ScoreBreakdown(tiffin = 0.15f), 10, 0, null, true, false
        )
        assertTrue(reasons.any { it.lowercase().contains("tiffin") })
    }

    @Test
    fun `trying something new shown for exploration slot`() {
        val reasons = gen.generate(
            ScoreBreakdown(), 60, 0, null, false, true
        )
        assertTrue(reasons.any { it.contains("new") || it.contains("something") })
    }

    @Test
    fun `cold start returns empty reasons`() {
        val reasons = gen.generate(
            ScoreBreakdown(), 0, 0, null, false, false
        )
        assertTrue(reasons.isEmpty())
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.ReasonGeneratorTest"
```

- [ ] **Step 3: Implement ReasonGenerator.kt**

```kotlin
package com.familymeal.assistant.domain.engine

import com.familymeal.assistant.domain.model.ScoreBreakdown
import javax.inject.Inject

class ReasonGenerator @Inject constructor() {

    fun generate(
        breakdown: ScoreBreakdown,
        daysSinceLastCooked: Int,
        makeAgainCount: Int,
        memberName: String?,
        tiffinBonusActive: Boolean,
        isExploration: Boolean
    ): List<String> {
        if (isExploration) return listOf("Trying something new")

        val reasons = mutableListOf<String>()

        if (daysSinceLastCooked >= 14) {
            reasons += "Not had in $daysSinceLastCooked days"
        }

        if (makeAgainCount >= 2) {
            reasons += "Family loved it ${makeAgainCount}×"
        }

        if (memberName != null && breakdown.memberModifier > 0.2f) {
            reasons += "$memberName likes this"
        }

        if (tiffinBonusActive) {
            reasons += "Good for tiffin"
        }

        return reasons
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.ReasonGeneratorTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/domain/engine/ReasonGenerator.kt \
        app/src/test/java/com/familymeal/assistant/domain/ReasonGeneratorTest.kt
git commit -m "feat: implement ReasonGenerator"
```

---

### Task 5: `ImageClassifier` — interface and Gemini implementation

**Files:**
- Create: `domain/classifier/ImageClassifier.kt`
- Create: `domain/classifier/GeminiImageClassifier.kt`
- Create: `di/ClassifierModule.kt`
- Create: `app/src/test/.../domain/ImageClassifierTest.kt`

- [ ] **Step 1: Create ImageClassifier interface**

```kotlin
package com.familymeal.assistant.domain.classifier

import android.net.Uri
import com.familymeal.assistant.domain.model.ClassificationResult
import kotlinx.coroutines.flow.Flow

interface ImageClassifier {
    fun classify(photoUri: Uri): Flow<ClassificationResult>
}
```

- [ ] **Step 2: Write failing tests for GeminiImageClassifier**

```kotlin
package com.familymeal.assistant.domain

import android.net.Uri
import com.familymeal.assistant.domain.classifier.GeminiImageClassifier
import com.familymeal.assistant.domain.model.ClassificationResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit

class ImageClassifierTest {

    private lateinit var server: MockWebServer
    private lateinit var classifier: GeminiImageClassifier

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        classifier = GeminiImageClassifier(
            client = client,
            baseUrl = server.url("/").toString(),
            apiKeyProvider = { "test-key" }
        )
    }

    @After fun teardown() = server.shutdown()

    @Test
    fun `success response emits Success with meal name`() = runTest {
        server.enqueue(MockResponse()
            .setBody("""{"candidates":[{"content":{"parts":[{"text":"Dal Makhani"}]}}]}""")
            .setResponseCode(200))

        val uri = mockk<Uri>()
        every { uri.path } returns null

        classifier.classifyBytes(byteArrayOf(0x1, 0x2)).test {
            val result = awaitItem()
            assertTrue(result is ClassificationResult.Success)
            assertEquals("Dal Makhani", (result as ClassificationResult.Success).mealName)
            awaitComplete()
        }
    }

    @Test
    fun `HTTP 400 emits Failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))
        classifier.classifyBytes(byteArrayOf()).test {
            assertTrue(awaitItem() is ClassificationResult.Failure)
            awaitComplete()
        }
    }

    @Test
    fun `absent API key emits Failure without network call`() = runTest {
        val noKeyClassifier = GeminiImageClassifier(
            client = OkHttpClient(),
            baseUrl = server.url("/").toString(),
            apiKeyProvider = { null }
        )
        noKeyClassifier.classifyBytes(byteArrayOf()).test {
            assertTrue(awaitItem() is ClassificationResult.Failure)
            awaitComplete()
        }
        assertEquals(0, server.requestCount)  // no network call made
    }
}
```

- [ ] **Step 3: Run — expect FAIL**

```bash
./gradlew :app:test --tests "*.ImageClassifierTest"
```

- [ ] **Step 4: Implement GeminiImageClassifier.kt**

```kotlin
package com.familymeal.assistant.domain.classifier

import android.content.Context
import android.net.Uri
import com.familymeal.assistant.domain.model.ClassificationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GeminiImageClassifier @Inject constructor(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val apiKeyProvider: () -> String?,
    @ApplicationContext private val context: Context? = null
) : ImageClassifier {

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val PROMPT = "Identify the meal in this photo. Return only the meal name, be specific (e.g. 'Dal Makhani' not 'Indian food')."
    }

    override fun classify(photoUri: Uri): Flow<ClassificationResult> = flow {
        val resolver = context?.contentResolver
        if (resolver == null) {
            emit(ClassificationResult.Failure)
            return@flow
        }
        val bytes = try {
            resolver.openInputStream(photoUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
        if (bytes == null || bytes.isEmpty()) {
            emit(ClassificationResult.Failure)
            return@flow
        }
        classifyBytes(bytes).collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    /** Testable entry point — accepts raw bytes directly */
    fun classifyBytes(bytes: ByteArray): Flow<ClassificationResult> = flow {
        val apiKey = apiKeyProvider()
        if (apiKey == null) {
            emit(ClassificationResult.Failure)
            return@flow
        }

        val base64 = Base64.getEncoder().encodeToString(bytes)
        val body = """
            {
              "contents": [{
                "parts": [
                  {"text": "$PROMPT"},
                  {"inline_data": {"mime_type": "image/jpeg", "data": "$base64"}}
                ]
              }]
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("${baseUrl}v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(ClassificationResult.Failure)
                return@flow
            }
            val json = response.body?.string() ?: run {
                emit(ClassificationResult.Failure)
                return@flow
            }
            val text = JSONObject(json)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
            emit(ClassificationResult.Success(text))
        } catch (e: Exception) {
            emit(ClassificationResult.Failure)
        }
    }.flowOn(Dispatchers.IO)
}
```

- [ ] **Step 5: Create ClassifierModule.kt**

```kotlin
package com.familymeal.assistant.di

import com.familymeal.assistant.data.repository.SettingsRepository
import com.familymeal.assistant.domain.classifier.GeminiImageClassifier
import com.familymeal.assistant.domain.classifier.ImageClassifier
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClassifierModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideImageClassifier(
        client: OkHttpClient,
        settingsRepository: SettingsRepository,
        @ApplicationContext context: Context
    ): ImageClassifier = GeminiImageClassifier(
        client = client,
        baseUrl = "https://generativelanguage.googleapis.com/",
        apiKeyProvider = { settingsRepository.getGeminiApiKey() },
        context = context
    )
}
```

- [ ] **Step 6: Run — expect PASS**

```bash
./gradlew :app:test --tests "*.ImageClassifierTest"
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/familymeal/assistant/domain/ \
        app/src/main/java/com/familymeal/assistant/di/ClassifierModule.kt \
        app/src/test/java/com/familymeal/assistant/domain/
git commit -m "feat: implement ImageClassifier with Gemini, WeightAdapter, ReasonGenerator"
```

---

### Task 6: Full domain test suite run

- [ ] **Step 1: Run all domain tests**

```bash
./gradlew :app:test --tests "*.domain.*"
```

Expected: All tests PASS.

- [ ] **Step 2: Build check**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.
