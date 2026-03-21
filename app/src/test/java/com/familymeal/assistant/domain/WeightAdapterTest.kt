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
        val wTiffin = weight("tiffin", 0.015f, 0.15f)  // at floor
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
