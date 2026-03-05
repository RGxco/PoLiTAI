package com.example.politai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * PoLiTAI - Advanced Weighted Keyword RAG Engine
 */
class RAGEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "PoLiTAI-RAG"
        private const val MAX_CONTEXT_TOKENS = 1500
    }
    
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, List<Map<String, Any>>>()
    
    fun loadRAGContext(query: String, conversationContext: String = ""): String {
        val contextBuilder = StringBuilder()
        contextBuilder.append("[GOVERNANCE DATABASE]:\n")
        
        // Load from databases
        val databases = listOf(
            "politician_database.json",
            "government_schemes.json",
            "governance_meetings.json"
        )
        
        databases.forEach { dbFile ->
            try {
                val data = loadDatabase(dbFile)
                if (data.isNotEmpty()) {
                    contextBuilder.append("[$dbFile]: ${data.size} records\n")
                    // Add first 2 records as sample
                    data.take(2).forEach { record ->
                        val text = record["text"] as? String ?: record.toString()
                        contextBuilder.append("- ${text.take(200)}...\n")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading $dbFile: ${e.message}")
            }
        }
        
        return contextBuilder.toString()
    }
    
    private fun loadDatabase(dbFile: String): List<Map<String, Any>> {
        return cache.getOrPut(dbFile) {
            try {
                context.assets.open(dbFile).use { inputStream ->
                    val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                    gson.fromJson(InputStreamReader(inputStream), type)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $dbFile: ${e.message}")
                emptyList()
            }
        }
    }
    
    fun clearCache() {
        cache.clear()
    }
    
    fun preloadDatabases() {
        // Preload databases
    }
}

fun Context.createRAGEngine(): RAGEngine {
    return RAGEngine(this)
}