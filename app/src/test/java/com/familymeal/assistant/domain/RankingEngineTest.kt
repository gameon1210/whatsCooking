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
            explorationRatio = 0f,
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
