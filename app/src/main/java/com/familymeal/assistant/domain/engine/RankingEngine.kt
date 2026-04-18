package com.familymeal.assistant.domain.engine

import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.domain.model.*
import javax.inject.Inject
import kotlin.math.tanh
import kotlin.math.floor

data class RankingInput(
    val candidates: List<CatalogMeal>,
    val mealType: MealType,
    val audienceMembers: List<Member>,
    val lastCookedAt: Map<Long, Long>,                          // catalogMealId → epochMillis
    val feedbackCounts: Map<Long, Map<FeedbackType, Int>>,      // catalogMealId → signal counts
    val memberScores: Map<Pair<Long, Long>, MemberMealScore>,   // (memberId, catalogMealId)
    val weights: WeightMap,
    val explorationRatio: Float,
    val totalSlots: Int
)

class RankingEngine @Inject constructor() {

    fun rank(input: RankingInput): List<RankedMeal> {
        val now = System.currentTimeMillis()

        // 1. Hard filter by diet compatibility
        val restrictiveDiet = mostRestrictiveDiet(input.audienceMembers)
        val filtered = input.candidates.filter { meal ->
            isDietCompatible(meal.dietType, restrictiveDiet) && supportsMealType(meal, input.mealType)
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
        if (mealType == MealType.Tiffin && supportsMealType(meal, MealType.Tiffin)) 1f else 0f

    private fun supportsMealType(meal: CatalogMeal, mealType: MealType): Boolean =
        meal.mealTypes.split(',')
            .map(String::trim)
            .any { it.equals(mealType.name, ignoreCase = true) }

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
