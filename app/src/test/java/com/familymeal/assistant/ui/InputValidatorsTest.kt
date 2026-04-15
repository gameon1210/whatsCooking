package com.familymeal.assistant.ui

import com.familymeal.assistant.ui.common.InputValidators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputValidatorsTest {

    @Test
    fun `member name rejects duplicates ignoring case`() {
        val error = InputValidators.memberNameError("alice", existingNames = listOf("Alice"))
        assertEquals("A member with this name already exists.", error)
    }

    @Test
    fun `birth year rejects future year`() {
        val error = InputValidators.birthYearError("2030", currentYear = 2026)
        assertEquals("Birth year must be between 1906 and 2026.", error)
    }

    @Test
    fun `meal name requires non blank text`() {
        val error = InputValidators.mealNameError(" ")
        assertEquals("Meal name is required.", error)
    }

    @Test
    fun `api key rejects whitespace`() {
        val error = InputValidators.apiKeyError("abc 12345678901234567890")
        assertEquals("API key cannot contain spaces.", error)
    }

    @Test
    fun `api key accepts trimmed key`() {
        assertNull(InputValidators.apiKeyError("AIza12345678901234567890"))
    }
}
