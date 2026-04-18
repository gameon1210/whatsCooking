package com.familymeal.assistant.domain

import com.familymeal.assistant.domain.engine.ReasonGenerator
import com.familymeal.assistant.domain.model.ScoreBreakdown
import org.junit.Assert.*
import org.junit.Test

class ReasonGeneratorTest {
    private val gen = ReasonGenerator()

    @Test
    fun `not had in N days shown when daysSince is 14 or more`() {
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
    fun `family loved it shown when makeAgainCount is 2 or more`() {
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
    fun `cold start returns a fallback reason`() {
        val reasons = gen.generate(
            ScoreBreakdown(), 0, 0, null, false, false
        )
        assertEquals(listOf("Good fit for this meal"), reasons)
    }
}
