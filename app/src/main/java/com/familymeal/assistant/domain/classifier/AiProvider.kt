package com.familymeal.assistant.domain.classifier

enum class AiProvider(
    val displayName: String,
    val defaultModel: String
) {
    ChatGpt(displayName = "ChatGPT", defaultModel = "gpt-4.1-mini"),
    Claude(displayName = "Claude", defaultModel = "claude-3-5-sonnet-latest"),
    Gemini(displayName = "Gemini", defaultModel = "gemini-2.0-flash");

    companion object {
        fun fromStoredValue(value: String?): AiProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Gemini
    }
}
