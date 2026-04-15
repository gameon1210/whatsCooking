package com.familymeal.assistant.ui.common

import java.time.Year
import java.util.Locale

object InputValidators {

    private const val MAX_MEMBER_NAME_LENGTH = 40
    private const val MAX_MEAL_NAME_LENGTH = 60
    private const val MIN_API_KEY_LENGTH = 20

    fun memberNameError(name: String, existingNames: Collection<String> = emptyList()): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Name is required."
        if (trimmed.length < 2) return "Name must be at least 2 characters."
        if (trimmed.length > MAX_MEMBER_NAME_LENGTH) {
            return "Name must be $MAX_MEMBER_NAME_LENGTH characters or less."
        }

        val normalized = trimmed.lowercase(Locale.ROOT)
        val duplicateExists = existingNames.any { it.trim().lowercase(Locale.ROOT) == normalized }
        if (duplicateExists) return "A member with this name already exists."

        return null
    }

    fun birthYearError(birthYearText: String, currentYear: Int = Year.now().value): String? {
        val trimmed = birthYearText.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.all(Char::isDigit)) return "Birth year must contain digits only."

        val year = trimmed.toIntOrNull() ?: return "Birth year is invalid."
        val minYear = currentYear - 120
        if (year !in minYear..currentYear) {
            return "Birth year must be between $minYear and $currentYear."
        }

        return null
    }

    fun mealNameError(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Meal name is required."
        if (trimmed.length < 2) return "Meal name must be at least 2 characters."
        if (trimmed.length > MAX_MEAL_NAME_LENGTH) {
            return "Meal name must be $MAX_MEAL_NAME_LENGTH characters or less."
        }
        return null
    }

    fun apiKeyError(key: String): String? {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return "API key is required."
        if (trimmed.any(Char::isWhitespace)) return "API key cannot contain spaces."
        if (trimmed.length < MIN_API_KEY_LENGTH) return "API key looks too short."
        return null
    }
}
