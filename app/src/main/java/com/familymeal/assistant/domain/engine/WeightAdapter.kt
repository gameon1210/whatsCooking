package com.familymeal.assistant.domain.engine

import com.familymeal.assistant.data.db.entity.FeedbackType
import com.familymeal.assistant.data.db.entity.RankingWeight
import javax.inject.Inject

class WeightAdapter @Inject constructor() {

    // Map of signal → (weight name, delta)
    private val nudgeMap = mapOf(
        FeedbackType.MakeAgain     to ("makeAgain"   to +0.05f),
        FeedbackType.NotAHit       to ("notAHit"     to +0.05f),
        FeedbackType.TooMuchWork   to ("tooMuchWork" to +0.05f),
        FeedbackType.GoodForTiffin to ("tiffin"      to +0.03f)
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
