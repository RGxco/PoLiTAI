package com.example.politai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * PoLiTAI - Robust RAG Engine
 * Optimized for keyword-based retrieval from JSON assets.
 */

object DatabaseSchemas {
    val ALL_DATABASES = listOf(
        "budget_allocations.json", "citizen_grievances.json", "constituency_complaints.json",
        "constitution_articles.json", "constitution_india.json", "disaster_emergency.json",
        "governance_meetings.json", "historical_context.json", "historical_speeches.json",
        "india_cpi_monthly.json", "india_gdp_quarterly.json", "india_government_schemes.json",
        "india_major_bills.json", "issue_trends.json", "meeting_protocols.json",
        "mplads_funds.json", "mps_mlas_constituencies.json", "parliament_members.json",
        "parliamentary_debates.json", "parliamentary_operations.json", "party_profiles.json",
        "political_entities.json", "politician_database.json", "public_complaints.json",
        "rbi_repo_rate_history.json", "speech_templates.json"
    )
}

class RAGEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "PoLiTAI-RAG"
        private const val MAX_TOTAL_RESULTS = 5 // Keep context small for Gemma 2B
    }
    
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, List<Map<String, Any?>>>()
    
    fun loadRAGContext(query: String, conversationContext: String = ""): String {
        val searchInput = (query + " " + conversationContext).lowercase()
        val keywords = extractKeywords(searchInput)
        
        Log.d(TAG, "Keywords extracted: $keywords")
        
        val allMatches = mutableListOf<Pair<String, Double>>() 
        
        DatabaseSchemas.ALL_DATABASES.forEach { dbFile ->
            try {
                val data = loadDatabase(dbFile)
                data.forEach { record ->
                    var score = 0.0
                    val recordLines = mutableListOf<String>()
                    
                    record.forEach { (key, value) ->
                        val valueStr = value?.toString() ?: ""
                        recordLines.add("$key: $valueStr")
                        
                        val lowerValue = valueStr.lowercase()
                        keywords.forEach { keyword ->
                            if (lowerValue.contains(keyword)) {
                                score += 1.0
                                // Extra weight for exact word matches
                                if (" $lowerValue ".contains(" $keyword ")) {
                                    score += 2.0
                                }
                            }
                        }
                    }
                    
                    if (score > 1.0) { // Require some confidence in the match
                        val formattedRecord = "FILE: $dbFile\n" + recordLines.joinToString("\n")
                        allMatches.add(formattedRecord to score)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing $dbFile", e)
            }
        }
        
        val topMatches = allMatches.sortedByDescending { it.second }.take(MAX_TOTAL_RESULTS)
        
        return if (topMatches.isNotEmpty()) {
            topMatches.joinToString("\n\n---\n\n") { it.first }
        } else {
            "" // Return empty so LLM knows no data was found
        }
    }
    
    private fun extractKeywords(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[.,?!():;]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .distinct()
    }
    
    private fun loadDatabase(dbFile: String): List<Map<String, Any?>> {
        return cache.getOrPut(dbFile) {
            try {
                context.assets.open(dbFile).use { input ->
                    val reader = InputStreamReader(input, "UTF-8")
                    val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
                    val result: List<Map<String, Any?>>? = gson.fromJson(reader, type)
                    Log.d(TAG, "Loaded $dbFile: ${result?.size ?: 0} records")
                    result ?: emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $dbFile", e)
                emptyList()
            }
        }
    }

    fun preloadDatabases() {
        listOf("budget_allocations.json", "india_government_schemes.json", "politician_database.json").forEach {
            loadDatabase(it)
        }
    }
}
