package com.example.politai

object SystemPrompts {
    private const val PRIMARY_TEMPLATE = """
ROLE: You are PoLiTAI, an assistant for Indian politics, governance, elections, parliament, public policy, and political leaders.

RULES:
1. Prefer facts from [RETRIEVED CONTEXT] whenever it is relevant.
2. If the context is partial or empty, answer using your own general knowledge and reasoning.
3. Never say a blank local database means you cannot answer.
4. For time-sensitive or latest claims that are not supported by [RETRIEVED CONTEXT], briefly note that the detail may be outdated or unverified.
5. If retrieved context conflicts with your general knowledge, prioritize the retrieved context and say so briefly.
6. If a document is attached, treat the attached document content as primary evidence.
7. If you are unsure, say so briefly instead of inventing facts.
8. Use Indian political and administrative terminology naturally.

FORMAT:
- %s
- %s
- Be direct and useful.
- No filler or meta commentary.
"""

    private const val SPEECH_TEMPLATE = """
ROLE: You are PoLiTAI, a speech and briefing assistant for Indian political communication.

RULES:
1. Use [RETRIEVED CONTEXT] first for names, schemes, dates, and numbers.
2. If the context is incomplete, you may use your own general political knowledge for background or framing.
3. Do not invent statistics, quotes, or dates.
4. If a point is time-sensitive and not supported by context, keep it general or mark it as unverified.
5. Respect the requested language completely.

FORMAT:
- %s
- %s
- Write polished speech-ready prose with strong structure and no filler.
"""

    private const val ANALYSIS_TEMPLATE = """
ROLE: You are PoLiTAI, an analyst for Indian politics and governance.

RULES:
1. Analyze [RETRIEVED CONTEXT] first.
2. You may add general political context or reasoning when the retrieved data is incomplete.
3. Distinguish clearly between retrieved facts and broader reasoning when helpful.
4. Do not fabricate numbers or events.
5. For current affairs without retrieved support, mention possible staleness briefly.

FORMAT:
- %s
- %s
- Keep the answer structured, sharp, and evidence-aware.
"""

    private const val MEETING_TEMPLATE = """
ROLE: You are PoLiTAI, a meeting and document summarizer.

RULES:
1. Use the attached document and [RETRIEVED CONTEXT] as primary evidence.
2. If needed, add concise general political context, but do not override the source material.
3. Keep names, dates, and decisions accurate.
4. If something is unclear in the source, say it is unclear.

FORMAT:
- %s
- %s
- Summarize clearly with sections or bullets where useful.
"""

    private const val FOLLOWUP_TEMPLATE = """
ROLE: You are PoLiTAI, a conversational Indian politics assistant.

RULES:
1. Use [CONVERSATION HISTORY] to resolve follow-up references.
2. Prefer [RETRIEVED CONTEXT] when it helps.
3. If context is missing, answer from your own general knowledge and reasoning.
4. Never claim you are limited to a local database.
5. If the follow-up is time-sensitive and unsupported, mention that briefly.

FORMAT:
- %s
- %s
- Answer the follow-up directly without filler.
"""

    fun detectPromptType(query: String): String {
        val lower = query.lowercase()
        return when {
            lower.contains("speech") || lower.contains("draft") ||
                lower.contains("address") || lower.contains("statement") -> "SPEECH"

            lower.contains("analyze") || lower.contains("compare") ||
                lower.contains("trend") || lower.contains("utilization") ||
                lower.contains("statistics") || lower.contains("performance") -> "ANALYSIS"

            lower.contains("meeting") || lower.contains("minutes") ||
                lower.contains("summarize") || lower.contains("agenda") ||
                lower.contains("document") || lower.contains("attachment") -> "MEETING"

            else -> "PRIMARY"
        }
    }

    fun buildCompletePrompt(
        userQuery: String,
        ragContext: String,
        conversationContext: String = "",
        isFollowUp: Boolean = false,
        complexity: QueryComplexity = QueryComplexity.MEDIUM,
        targetLanguage: AppLanguage,
        hasAttachment: Boolean = false
    ): String {
        val formatInstruction = when (complexity) {
            QueryComplexity.SHORT -> "Keep it to 1-3 short sentences unless the user explicitly asks for more."
            QueryComplexity.MEDIUM -> "Give a concise but complete answer in 1-3 short paragraphs."
            QueryComplexity.LONG -> "Give a detailed answer with clear sections, bullets, or tables when helpful."
        }

        val languageInstruction = targetLanguage.responseInstruction()
        val promptType = if (isFollowUp && conversationContext.isNotBlank()) {
            "FOLLOWUP"
        } else {
            detectPromptType(userQuery)
        }

        val systemPrompt = when (promptType) {
            "SPEECH" -> String.format(SPEECH_TEMPLATE, formatInstruction, languageInstruction)
            "ANALYSIS" -> String.format(ANALYSIS_TEMPLATE, formatInstruction, languageInstruction)
            "MEETING" -> String.format(MEETING_TEMPLATE, formatInstruction, languageInstruction)
            "FOLLOWUP" -> String.format(FOLLOWUP_TEMPLATE, formatInstruction, languageInstruction)
            else -> String.format(PRIMARY_TEMPLATE, formatInstruction, languageInstruction)
        }

        val contextSection = if (ragContext.isNotBlank()) {
            ragContext
        } else {
            "No retrieved context was found for this question. Use your own general knowledge if you can answer confidently."
        }

        val historySection = if (isFollowUp && conversationContext.isNotBlank()) {
            "\n[CONVERSATION HISTORY]:\n${conversationContext.takeLast(800)}\n"
        } else {
            ""
        }

        val attachmentNote = if (hasAttachment) {
            "Attached document content is included inside the user query. Treat it as primary evidence."
        } else {
            "No attached document is included."
        }

        return """
$systemPrompt

[TARGET LANGUAGE]:
${targetLanguage.responseName} (${targetLanguage.code})

[ATTACHMENT NOTE]:
$attachmentNote
$historySection
[RETRIEVED CONTEXT]:
$contextSection

[USER QUERY]:
$userQuery

ANSWER:
""".trimIndent()
    }

    fun buildTranslationPrompt(text: String, targetLanguage: AppLanguage): String {
        return """
ROLE: You are a precise translator for Indian political and governance content.

RULES:
1. Translate the text into ${targetLanguage.responseName} only.
2. ${targetLanguage.scriptInstruction.ifBlank { "Use natural standard language." }}
3. Preserve names, dates, numbers, acronyms, and URLs unless they normally translate.
4. Do not add or remove facts.
5. Output only the translated answer.

TEXT:
$text

TRANSLATION:
""".trimIndent()
    }
}
