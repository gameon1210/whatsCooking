package com.familymeal.assistant.domain.classifier

data class AiProviderConfig(
    val provider: AiProvider,
    val model: String,
    val apiKey: String?
)
