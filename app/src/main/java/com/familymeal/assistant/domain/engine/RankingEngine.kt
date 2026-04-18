package com.familymeal.assistant.domain.engine

import com.familymeal.assistant.data.db.entity.*
import com.familymeal.assistant.domain.model.*
import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.tanh

data class RankingInput(
    val candidates: List<CatalogMeal>,
    val mealType: MealType,
    val audienceMembers: List<Member>,
    val lastCookedAt: Map<Long, Long>,                          // catalogMealId → epochMillis
    val feedbackCounts: Map<Long, Map<FeedbackType, Int>>,      // catalogMealId → signal counts
    val memberScores: Map<Pair<Long, Long>, MemberMealScore>,   // (memberId, catalogMealId)
    val weights: WeightMap,
    val explorationRatio: Float,
    val totalSlots: Int,
    // V2 additions
    val implicitSignals: Map<Long, ImplicitSignalSummary> = emptyMap(),
    val effortCap: EffortLevel? = null,    // null = no cap; QUICK = exclude INVOLVED
    val busyContext: Boolean = false       // true on weekday Breakfast/Tiffin
)

class RankingEngine @Inject constructor() {

    fun rank(input: RankingInput): List<RankedMeal> {
        val now = System.currentTimeMillis()

        // 1. Hard filters
        val restrictiveDiet = mostRestrictiveDiet(input.audienceMembers)
        val ignoredIds = input.implicitSignals
            .filter { (_, s) -> s.shownCount >= 3 && s.tappedCount == 0 }
            .keys

        val filtered = input.candidates.filter { meal ->
            isDietCompatible(meal.dietType, restrictiveDiet)
                && supportsMealType(meal, input.mealType)
                && effortCapAllows(meal.effortLevel, input.effortCap)
                && notAHitFilter(meal.id, input.feedbackCounts)
        }

        if (filtered.isEmpty()) return emptyList()

        // 2. Score each meal
        val scored = filtered.map { meal ->
            val daysSince = daysSince(meal.id, input.lastCookedAt, now)
            val counts = input.feedbackCounts[meal.id] ?: emptyMap()
            val signals = input.implicitSignals[meal.id]

            // Slot-repeat penalty: same meal cooked within 48 h
            val hoursAgo = hoursSinceCookedAt(meal.id, input.lastCookedAt, now)
            val slotRepeatPenalty = if (hoursAgo != null && hoursAgo < 48) 0.40f else 0f

            // Effort adjustments for busy context
            val effortBonus = if (input.busyContext && meal.effortLevel == EffortLevel.QUICK) 0.15f else 0f
            val effortPenalty = if (input.busyContext && meal.effortLevel == EffortLevel.INVOLVED) 0.20f else 0f

            // Weekday factor amplifies tooMuchWork penalty
            val weekdayFactor = if (input.busyContext) 1.5f else 1f

            // Implicit lift / suppression
            val implicitLift = if ((signals?.tappedCount ?: 0) > 0) 0.10f else 0f
            val implicitSuppress = if (meal.id in ignoredIds) 0.15f else 0f

            val breakdown = ScoreBreakdown(
                recency = input.weights.recency * recencyBonus(daysSince),
                makeAgain = input.weights.makeAgain * (counts[FeedbackType.MakeAgain] ?: 0).toFloat(),
                notAHit = input.weights.notAHit * (counts[FeedbackType.NotAHit] ?: 0).toFloat(),
                tooMuchWork = input.weights.tooMuchWork * (counts[FeedbackType.TooMuchWork] ?: 0).toFloat() * weekdayFactor,
                tiffin = input.weights.tiffin * tiffinBonus(meal, input.mealType),
                memberMatch = input.weights.memberMatch * dietCompatibilityScore(meal.dietType, input.audienceMembers),
                memberModifier = avgMemberModifier(meal.id, input.audienceMembers, input.memberScores)
            )

            val finalScore = breakdown.adjustedScore + effortBonus - effortPenalty +
                implicitLift - implicitSuppress - slotRepeatPenalty

            Triple(meal, breakdown, finalScore)
        }.sortedByDescending { it.third }

        // 3. Split exploitation / exploration
        val exploitCount = floor(input.totalSlots * (1f - input.explorationRatio)).toInt()
            .coerceAtMost(scored.size)
        val exploitMeals = scored.take(exploitCount)
        val exploitIds = exploitMeals.map { it.first.id }.toSet()

        val explorationPool = filtered.filter { meal ->
            meal.id !in exploitIds &&
                isExplorationEligible(meal.id, input.lastCookedAt, now, input.lastCookedAt.size < 5)
        }
        val exploreSlots = (input.totalSlots - exploitCount).coerceAtMost(explorationPool.size)
        val exploreMeals = explorationPool.shuffled().take(exploreSlots)

        // 4. Build results
        return exploitMeals.map { (meal, breakdown, finalScore) ->
            RankedMeal(
                catalogMealId = meal.id,
                name = meal.name,
                cuisine = meal.cuisine,
                adjustedScore = finalScore,
                reasons = emptyList(), // populated by ReasonGenerator in ViewModel
                isExploration = false,
                breakdown = breakdown
            )
        } + exploreMeals.map { meal ->
            RankedMeal(
                catalogMealId = meal.id,
                name = meal.name,
                cuisine = meal.cuisine,
                adjustedScore = 0f,
                reasons = emptyList(),
                isExploration = true,
                breakdown = ScoreBreakdown()
            )
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun effortCapAllows(effortLevel: EffortLevel, cap: EffortLevel?): Boolean {
        if (cap == null) return true
        return !(cap == EffortLevel.QUICK && effortLevel == EffortLevel.INVOLVED)
    }

    private fun notAHitFilter(catalogMealId: Long, feedbackCounts: Map<Long, Map<FeedbackType, Int>>): Boolean {
        val count = feedbackCounts[catalogMealId]?.get(FeedbackType.NotAHit) ?: 0
        return count < 3 // hard exclude meals with ≥3 NotAHit signals
    }

    private fun hoursSinceCookedAt(catalogMealId: Long, lastCookedAt: Map<Long, Long>, now: Long): Long? {
        val last = lastCookedAt[catalogMealId] ?: return null
        return (now - last) / 3_600_000L
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

    private fun dietCompatibilityScore(mealDiet: DietType, members: List<Member>): Float {
        if (members.isEmpty()) return 1f
        return members.count { isDietCompatible(mealDiet, it.dietType) }.toFloat() / members.size
    }

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
