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
