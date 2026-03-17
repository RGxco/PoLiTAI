package com.example.politai

import java.util.Locale

data class AppLanguage(
    val code: String,
    val displayName: String,
    val locale: Locale,
    val responseName: String,
    val scriptInstruction: String,
    val greeting: String,
    val keywordHints: Set<String> = emptySet(),
    val unicodeRanges: List<IntRange> = emptyList()
) {
    fun responseInstruction(): String {
        val scriptHint = if (scriptInstruction.isBlank()) "" else " $scriptInstruction"
        return "Reply only in $responseName.$scriptHint Do not mix languages unless the user explicitly mixes languages."
    }
}

object AppLanguages {
    private val englishHints = setOf(
        "what", "who", "when", "where", "why", "how", "tell me", "explain",
        "compare", "latest", "politics", "minister", "election", "government"
    )

    val all = listOf(
        AppLanguage(
            code = "en-IN",
            displayName = "English (India)",
            locale = Locale("en", "IN"),
            responseName = "English",
            scriptInstruction = "",
            greeting = "PoLiTAI system online. Awaiting governance query.",
            keywordHints = englishHints
        ),
        AppLanguage(
            code = "hi-IN",
            displayName = "Hindi",
            locale = Locale("hi", "IN"),
            responseName = "Hindi",
            scriptInstruction = "Use Devanagari script.",
            greeting = "Namaste, main kaise madad kar sakti hoon?",
            keywordHints = setOf("kya", "kaun", "kaise", "sarkar", "yojana", "pradhan mantri", "mukhyamantri"),
            unicodeRanges = listOf(0x0900..0x097F)
        ),
        AppLanguage(
            code = "ta-IN",
            displayName = "Tamil",
            locale = Locale("ta", "IN"),
            responseName = "Tamil",
            scriptInstruction = "Use Tamil script.",
            greeting = "Vanakkam, naan eppadi udhavalam?",
            unicodeRanges = listOf(0x0B80..0x0BFF)
        ),
        AppLanguage(
            code = "te-IN",
            displayName = "Telugu",
            locale = Locale("te", "IN"),
            responseName = "Telugu",
            scriptInstruction = "Use Telugu script.",
            greeting = "Namaskaram, nenu ela sahayam cheyagalanu?",
            unicodeRanges = listOf(0x0C00..0x0C7F)
        ),
        AppLanguage(
            code = "mr-IN",
            displayName = "Marathi",
            locale = Locale("mr", "IN"),
            responseName = "Marathi",
            scriptInstruction = "Use Devanagari script.",
            greeting = "Namaskar, mi kashi madat karu shakte?",
            unicodeRanges = listOf(0x0900..0x097F)
        ),
        AppLanguage(
            code = "bn-IN",
            displayName = "Bengali",
            locale = Locale("bn", "IN"),
            responseName = "Bengali",
            scriptInstruction = "Use Bengali script.",
            greeting = "Nomoskar, ami ki bhabe sahajjo korte pari?",
            unicodeRanges = listOf(0x0980..0x09FF)
        ),
        AppLanguage(
            code = "gu-IN",
            displayName = "Gujarati",
            locale = Locale("gu", "IN"),
            responseName = "Gujarati",
            scriptInstruction = "Use Gujarati script.",
            greeting = "Namaste, hu kevi rite madad kari saku?",
            unicodeRanges = listOf(0x0A80..0x0AFF)
        ),
        AppLanguage(
            code = "kn-IN",
            displayName = "Kannada",
            locale = Locale("kn", "IN"),
            responseName = "Kannada",
            scriptInstruction = "Use Kannada script.",
            greeting = "Namaskara, nanu hege sahaya madabahudu?",
            unicodeRanges = listOf(0x0C80..0x0CFF)
        ),
        AppLanguage(
            code = "ml-IN",
            displayName = "Malayalam",
            locale = Locale("ml", "IN"),
            responseName = "Malayalam",
            scriptInstruction = "Use Malayalam script.",
            greeting = "Namaskaram, njan engane sahayikkam?",
            unicodeRanges = listOf(0x0D00..0x0D7F)
        ),
        AppLanguage(
            code = "pa-IN",
            displayName = "Punjabi",
            locale = Locale("pa", "IN"),
            responseName = "Punjabi",
            scriptInstruction = "Use Gurmukhi script.",
            greeting = "Sat sri akaal, main kiven madad kar sakdi haan?",
            unicodeRanges = listOf(0x0A00..0x0A7F)
        )
    )

    private val byCode = all.associateBy { it.code }

    fun fromCode(code: String?): AppLanguage = byCode[code] ?: all.first()

    fun displayNames(): List<String> = all.map { it.displayName }

    fun codes(): List<String> = all.map { it.code }

    fun detect(text: String, fallbackCode: String = "en-IN"): AppLanguage {
        if (text.isBlank()) return fromCode(fallbackCode)

        val bestScriptMatch = all.asSequence()
            .map { language -> language to countScriptMatches(text, language.unicodeRanges) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }

        if (bestScriptMatch != null && bestScriptMatch.second >= 2) {
            return bestScriptMatch.first
        }

        val lower = text.lowercase(Locale.ROOT)
        all.firstOrNull { language ->
            language.keywordHints.any { hint -> lower.contains(hint) }
        }?.let { return it }

        if (looksLikeEnglish(lower)) {
            return fromCode("en-IN")
        }

        return fromCode(fallbackCode)
    }

    fun needsTranslation(text: String, target: AppLanguage): Boolean {
        if (text.isBlank()) return false

        return if (target.code == "en-IN") {
            containsIndicScript(text)
        } else {
            val targetChars = countScriptMatches(text, target.unicodeRanges)
            val latinLetters = text.count { it in 'A'..'Z' || it in 'a'..'z' }
            targetChars < 6 && latinLetters > 24
        }
    }

    private fun looksLikeEnglish(text: String): Boolean {
        val latinLetters = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        if (latinLetters < 12) return false
        return englishHints.count { hint -> text.contains(hint) } >= 2
    }

    private fun containsIndicScript(text: String): Boolean {
        return all.any { language ->
            countScriptMatches(text, language.unicodeRanges) > 0
        }
    }

    private fun countScriptMatches(text: String, ranges: List<IntRange>): Int {
        if (ranges.isEmpty()) return 0
        return text.count { char ->
            ranges.any { char.code in it }
        }
    }
}
