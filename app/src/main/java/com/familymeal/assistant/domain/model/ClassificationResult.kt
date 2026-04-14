package com.familymeal.assistant.domain.model

sealed class ClassificationResult {
    data class Success(val mealName: String) : ClassificationResult()
    object Failure : ClassificationResult()
}
