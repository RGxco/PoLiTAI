package com.example.politai

import android.content.Context
import android.util.JsonReader
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * PoLiTAI - Advanced Weighted Keyword RAG Engine
 * 
 * Features:
 * - Multi-database search with weighted scoring
 * - Schema-aware context retrieval
 * - Priority-based result ranking
 * - Token budget management
 * - Follow-up query handling
 */

// Data Classes for Structured RAG Results
data class RAGResult(
    val source: String,
    val content: String,
    val score: Double,
    val metadata: Map<String, Any> = emptyMap()
)

data class WeightedKeyword(
    val keyword: String,
    val weight: Double,
    val category: KeywordCategory
)

enum class KeywordCategory {
    CRITICAL,    // Names, IDs, specific identifiers (weight: 3.0)
    HIGH,        // Key topics, ministries, schemes (weight: 2.0)
    MEDIUM,      // General context words (weight: 1.0)
    LOW          // Stop words, common terms (weight: 0.3)
}

// Database Schema Definitions
object DatabaseSchemas {
    
    const val GOVERNANCE_MEETINGS = "governance_meetings.json"
    const val POLITICIAN_DB = "politician_database.json"
    const val GOVERNMENT_SCHEMES = "india_government_schemes.json"
    const val MAJOR_BILLS = "india_major_bills.json"
    const val MEETING_PROTOCOLS = "meeting_protocols.json"
    const val RBI_REPO_RATES = "rbi_repo_rate_history.json"
    const val CPI_MONTHLY = "india_cpi_monthly.json"
    const val GDP_QUARTERLY = "india_gdp_quarterly.json"
    
    // All database files with their priority weights
    val ALL_DATABASES = mapOf(
        GOVERNANCE_MEETINGS to 2.5,      // Highest priority for governance queries
        POLITICIAN_DB to 2.0,             // High priority for politician queries
        GOVERNMENT_SCHEMES to 2.0,        // High priority for scheme queries
        MAJOR_BILLS to 1.8,               // Medium-high for legislative queries
        MEETING_PROTOCOLS to 1.5,         // Medium for protocol queries
        RBI_REPO_RATES to 1.2,            // Lower for economic data
        CPI_MONTHLY to 1.0,
        GDP_QUARTERLY to 1.0
    )
}

/**
 * Advanced RAG Engine with Weighted Keyword Ranking
 */
class RAGEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "PoLiTAI-RAG"
        private const val MAX_CONTEXT_TOKENS = 1500
        private const val MAX_RESULTS_PER_DB = 5
        
        // Indian governance stop words
        private val STOP_WORDS = setOf(
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "must", "shall", "can", "need", "dare",
            "ought", "used", "to", "of", "in", "for", "on", "with", "at", "by",
            "from", "as", "into", "through", "during", "before", "after", "above",
            "below", "between", "under", "again", "further", "then", "once",
            "here", "there", "when", "where", "why", "how", "all", "each",
            "few", "more", "most", "other", "some", "such", "no", "nor", "not",
            "only", "own", "same", "so", "than", "too", "very", "just", "and",
            "but", "if", "or", "because", "until", "while", "what", "which",
            "who", "whom", "this", "that", "these", "those", "am", "it", "its",
            "tell", "me", "about", "give", "show", "list", "find", "search",
            "what", "which", "how", "when", "where", "who", "why", "is", "are",
            "was", "were", "do", "does", "did", "can", "could", "will", "would",
            "should", "shall", "may", "might", "must", "have", "has", "had"
        )
        
        // Indian governance keywords with weights
        private val GOVERNANCE_KEYWORDS = mapOf(
            // Critical - Politicians & Key Figures
            "rajagopalachari" to 3.0, "rajaji" to 3.0, "gandhi" to 3.0, "nehru" to 3.0,
            "modi" to 3.0, "shah" to 2.5, "jaitley" to 2.5, "sitharaman" to 2.5,
            
            // Critical - Schemes
            "pmgsy" to 3.0, "mgnregs" to 3.0, "nrega" to 3.0, "pmjay" to 3.0,
            "ayushman" to 3.0, "pmkisan" to 3.0, "ujjwala" to 3.0, "swachh" to 3.0,
            "bharat" to 3.0, "jaljeevan" to 3.0, "pmay" to 3.0, "awas" to 3.0,
            "digital" to 2.5, "india" to 2.5, "makeinindia" to 3.0, "startup" to 2.5,
            "pli" to 3.0, "gatishakti" to 3.0, "amrut" to 2.5, "smartcities" to 2.5,
            
            // Critical - Ministries
            "finance" to 2.5, "home" to 2.5, "defence" to 2.5, "external" to 2.5,
            "railways" to 2.5, "health" to 2.5, "education" to 2.5, "agriculture" to 2.5,
            "rural" to 2.0, "urban" to 2.0, "commerce" to 2.0, "industry" to 2.0,
            "environment" to 2.0, " jal" to 2.5, "shakti" to 2.0, "power" to 2.0,
            
            // High - Legislative Terms
            "bill" to 2.5, "act" to 2.5, "amendment" to 2.5, "ordinance" to 2.5,
            "parliament" to 2.5, "loksabha" to 2.5, "rajyasabha" to 2.5, "committee" to 2.0,
            "cabinet" to 2.5, "ministry" to 2.0, "secretary" to 2.0, "commission" to 2.0,
            
            // High - Economic Terms
            "budget" to 2.5, "allocation" to 2.0, "expenditure" to 2.0, "revenue" to 2.0,
            "deficit" to 2.0, "gdp" to 2.5, "inflation" to 2.5, "cpi" to 2.5,
            "repo" to 2.5, "rate" to 1.5, "rbi" to 2.5, "fiscal" to 2.0,
            "monetary" to 2.0, "subsidy" to 2.0, "tax" to 2.0, "gst" to 2.5,
            "crore" to 1.5, "lakh" to 1.5, "rupees" to 1.0,
            
            // Medium - Governance Terms
            "policy" to 1.8, "programme" to 1.8, "scheme" to 1.8, "mission" to 1.8,
            "yojana" to 2.0, "initiative" to 1.5, "project" to 1.5, "plan" to 1.5,
            "implementation" to 1.5, "development" to 1.5, "welfare" to 1.5,
            
            // Medium - Meeting Terms
            "meeting" to 1.5, "minutes" to 2.0, "agenda" to 1.8, "decision" to 1.8,
            "resolution" to 1.8, "action" to 1.5, "item" to 1.0, "discussion" to 1.5,
            "review" to 1.5, "report" to 1.5, "status" to 1.5, "deadline" to 1.5,
            
            // Low - Common Words
            "government" to 1.0, "public" to 1.0, "national" to 1.0, "state" to 1.0,
            "district" to 1.0, "village" to 1.0, "city" to 1.0, "area" to 0.8,
            "people" to 0.5, "citizen" to 1.0, "beneficiary" to 1.0
        )
    }
    
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, List<Map<String, Any>>>()
    
    /**
     * Main entry point: Load RAG context for a query
     */
    fun loadRAGContext(query: String, conversationContext: String = ""): String {
        val startTime = System.currentTimeMillis()
        
        // Extract and weight keywords from query
        val weightedKeywords = extractWeightedKeywords(query)
        Log.d(TAG, "Extracted ${weightedKeywords.size} keywords: $weightedKeywords")
        
        // Search across all databases
        val allResults = mutableListOf<RAGResult>()
        
        DatabaseSchemas.ALL_DATABASES.forEach { (dbFile, dbWeight) ->
            try {
                val results = searchDatabase(dbFile, weightedKeywords, dbWeight)
                allResults.addAll(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching $dbFile: ${e.message}")
            }
        }
        
        // Sort by score and take top results
        val sortedResults = allResults
            .sortedByDescending { it.score }
            .take(MAX_RESULTS_PER_DB * 3)
        
        // Build context string with token budget
        val contextBuilder = StringBuilder()
        var tokenCount = 0
        
        // Add conversation context if relevant
        if (conversationContext.isNotEmpty() && isFollowUpQuery(query)) {
            val relevantContext = extractRelevantContext(conversationContext, weightedKeywords)
            contextBuilder.append("[CONVERSATION HISTORY]:\n$relevantContext\n\n")
        }
        
        // Add database results
        contextBuilder.append("[GOVERNANCE DATABASE]:\n")
        
        for (result in sortedResults) {
            val content = result.content
            val estimatedTokens = content.split(" ").size
            
            if (tokenCount + estimatedTokens > MAX_CONTEXT_TOKENS) {
                break
            }
            
            contextBuilder.append("[${result.source}] ${content}\n\n")
            tokenCount += estimatedTokens
        }
        
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "RAG context built in ${elapsed}ms with ~$tokenCount tokens")
        
        return contextBuilder.toString()
    }
    
    /**
     * Extract weighted keywords from query
     */
    private fun extractWeightedKeywords(query: String): List<WeightedKeyword> {
        val words = query.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .filter { it !in STOP_WORDS }
        
        val keywords = mutableListOf<WeightedKeyword>()
        
        words.forEach { word ->
            val weight = GOVERNANCE_KEYWORDS[word] ?: 1.0
            val category = when {
                weight >= 3.0 -> KeywordCategory.CRITICAL
                weight >= 2.0 -> KeywordCategory.HIGH
                weight >= 1.0 -> KeywordCategory.MEDIUM
                else -> KeywordCategory.LOW
            }
            keywords.add(WeightedKeyword(word, weight, category))
        }
        
        // Add bigrams for better matching
        for (i in 0 until words.size - 1) {
            val bigram = "${words[i]} ${words[i + 1]}"
            val weight = GOVERNANCE_KEYWORDS[bigram.replace(" ", "")] ?: 1.5
            keywords.add(WeightedKeyword(bigram, weight, KeywordCategory.HIGH))
        }
        
        return keywords.sortedByDescending { it.weight }
    }
    
    /**
     * Search a specific database with weighted scoring
     */
    private fun searchDatabase(
        dbFile: String,
        keywords: List<WeightedKeyword>,
        dbWeight: Double
    ): List<RAGResult> {
        
        val data = loadDatabase(dbFile)
        val results = mutableListOf<RAGResult>()
        
        data.forEach { record ->
            val text = extractTextFromRecord(record)
            val score = calculateMatchScore(text, keywords, dbWeight)
            
            if (score > 0.5) { // Minimum threshold
                results.add(RAGResult(
                    source = dbFile.replace(".json", "").uppercase(),
                    content = text,
                    score = score,
                    metadata = record
                ))
            }
        }
        
        return results.sortedByDescending { it.score }.take(MAX_RESULTS_PER_DB)
    }
    
    /**
     * Calculate weighted match score
     */
    private fun calculateMatchScore(
        text: String,
        keywords: List<WeightedKeyword>,
        dbWeight: Double
    ): Double {
        val lowerText = text.lowercase()
        var totalScore = 0.0
        var matchedKeywords = 0
        
        keywords.forEach { keyword ->
            val count = countOccurrences(lowerText, keyword.keyword)
            if (count > 0) {
                // Score based on keyword weight, frequency, and position
                val frequencyScore = min(count, 3) * keyword.weight
                val positionScore = if (lowerText.indexOf(keyword.keyword) < 100) 1.5 else 1.0
                totalScore += frequencyScore * positionScore
                matchedKeywords++
            }
        }
        
        // Boost score for multiple keyword matches
        val diversityBoost = 1.0 + (matchedKeywords.toDouble() / keywords.size) * 0.5
        
        // Apply database weight
        return totalScore * dbWeight * diversityBoost
    }
    
    /**
     * Count occurrences of keyword in text
     */
    private fun countOccurrences(text: String, keyword: String): Int {
        return text.split(keyword).size - 1
    }
    
    /**
     * Load database from assets or cache
     */
    private fun loadDatabase(dbFile: String): List<Map<String, Any>> {
        return cache.getOrPut(dbFile) {
            try {
                context.assets.open(dbFile).use { inputStream ->
                    val reader = JsonReader(InputStreamReader(inputStream, "UTF-8"))
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    gson.fromJson(reader, type)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $dbFile: ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * Extract text content from record based on schema
     */
    private fun extractTextFromRecord(record: Map<String, Any>): String {
        // Try to get the pre-formatted text field first
        val textField = record["text"] as? String
        if (textField != null) return textField
        
        // Otherwise, build text from key fields
        val builder = StringBuilder()
        record.forEach { (key, value) ->
            if (value is String && value.isNotBlank()) {
                builder.append("$key: $value. ")
            }
        }
        return builder.toString()
    }
    
    /**
     * Check if query is a follow-up
     */
    private fun isFollowUpQuery(query: String): Boolean {
        val followUpIndicators = listOf(
            "it", "that", "this", "they", "them", "he", "she", "his", "her",
            "yes", "no", "what about", "how about", "tell me more", "explain",
            "why", "when", "where", "who", "which", "elaborate", "detail"
        )
        val lowerQuery = query.lowercase()
        return followUpIndicators.any { lowerQuery.contains(it) }
    }
    
    /**
     * Extract relevant context from conversation history
     */
    private fun extractRelevantContext(context: String, keywords: List<WeightedKeyword>): String {
        val lines = context.split("\n")
        val relevantLines = mutableListOf<String>()
        
        lines.forEach { line ->
            val lowerLine = line.lowercase()
            val relevanceScore = keywords.sumOf { keyword ->
                if (lowerLine.contains(keyword.keyword)) keyword.weight else 0.0
            }
            if (relevanceScore > 1.0) {
                relevantLines.add(line)
            }
        }
        
        return relevantLines.takeLast(4).joinToString("\n")
    }
    
    /**
     * Clear cache (useful for memory management)
     */
    fun clearCache() {
        cache.clear()
    }
    
    /**
     * Preload all databases for faster queries
     */
    fun preloadDatabases() {
        DatabaseSchemas.ALL_DATABASES.keys.forEach { dbFile ->
            try {
                loadDatabase(dbFile)
                Log.d(TAG, "Preloaded $dbFile")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload $dbFile: ${e.message}")
            }
        }
    }
}

/**
 * Extension function for easy RAG context loading
 */
fun Context.createRAGEngine(): RAGEngine {
    return RAGEngine(this)
}