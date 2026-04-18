package com.familymeal.assistant.domain.engine

import com.familymeal.assistant.data.db.entity.EffortLevel
import com.familymeal.assistant.domain.model.ScoreBreakdown
import javax.inject.Inject

class ReasonGenerator @Inject constructor() {

    fun generate(
        breakdown: ScoreBreakdown,
        daysSinceLastCooked: Int,
        makeAgainCount: Int,
        memberName: String?,
        tiffinBonusActive: Boolean,
        isExploration: Boolean,
        tiffinKidFavorite: Boolean = false,
        effortLevel: EffortLevel? = null,
        busyContext: Boolean = false,
        categoryRunLabel: String? = null,    // e.g. "rice" if diversity bonus active
        implicitLiftActive: Boolean = false  // user previously tapped this suggestion
    ): List<String> {
        if (isExploration) return listOf("Something different — hasn't come up in a while")

        val reasons = mutableListOf<String>()

        if (daysSinceLastCooked >= 14) {
            reasons += "Not had in $daysSinceLastCooked days — a good time to bring it back"
        }

        if (makeAgainCount >= 2) {
            reasons += "Family voted Make Again ${makeAgainCount}×"
        }

        if (memberName != null && breakdown.memberModifier > 0.3f) {
            reasons += "$memberName has consistently liked this"
        } else if (memberName != null && breakdown.memberModifier > 0.2f) {
            reasons += "$memberName likes this"
        }

        if (tiffinBonusActive && tiffinKidFavorite) {
            reasons += "Packed before and the kids liked it"
        } else if (tiffinBonusActive) {
            reasons += "Good for tiffin"
        }

        if (effortLevel == EffortLevel.QUICK && busyContext) {
            reasons += "Quick option — ready in under 20 min"
        }

        if (implicitLiftActive) {
            reasons += "You've picked this before when suggested"
        }

        if (categoryRunLabel != null) {
            reasons += "Good change from the recent $categoryRunLabel run"
        }

        return reasons.ifEmpty { listOf("Good fit for this meal") }
    }
}
