package com.example.politai

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * PoLiTAI - Chat History Manager v2
 * 
 * Changes:
 * - saveMessage() is resilient to invalid session IDs (auto-creates session if needed)
 * - Better error handling throughout
 */

class ChatHistoryManager(context: Context) {
    
    companion object {
        private const val TAG = "PoLiTAI-Chat"
        
        val TOPICS = listOf(
            "Politics & Leaders",
            "Government Schemes",
            "Budget & Finance",
            "Meetings & Minutes",
            "Bills & Legislation",
            "Constituency Issues",
            "Economic Data",
            "General"
        )
        
        private val TOPIC_KEYWORDS = mapOf(
            "Politics & Leaders" to listOf("minister", "mp", "mla", "politician", "party", "leader", "cabinet", "president", "governor"),
            "Government Schemes" to listOf("scheme", "yojana", "pm-kisan", "ayushman", "benefit", "subsidy", "welfare", "mission"),
            "Budget & Finance" to listOf("budget", "allocation", "crore", "finance", "expenditure", "revenue", "gst", "tax"),
            "Meetings & Minutes" to listOf("meeting", "minutes", "agenda", "decision", "committee"),
            "Bills & Legislation" to listOf("bill", "act", "amendment", "parliament", "legislation", "law", "constitution"),
            "Constituency Issues" to listOf("complaint", "issue", "district", "constituency", "problem", "grievance"),
            "Economic Data" to listOf("gdp", "cpi", "inflation", "rbi", "repo", "economic", "growth", "industrial")
        )
    }
    
    private val database = Room.databaseBuilder(
        context.applicationContext,
        PoLiTAIDatabase::class.java,
        "politai_chat_history.db"
    ).fallbackToDestructiveMigration().build()
    
    private val sessionDao = database.chatSessionDao()
    private val messageDao = database.chatMessageDao()
    
    suspend fun createSession(firstMessage: String): Long = withContext(Dispatchers.IO) {
        try {
            val topic = detectTopic(firstMessage)
            val title = generateTitle(firstMessage)
            val now = System.currentTimeMillis()
            
            val session = ChatSession(
                title = title,
                topic = topic,
                createdAt = now,
                updatedAt = now,
                messageCount = 0
            )
            
            val id = sessionDao.insertSession(session)
            Log.d(TAG, "Created session $id: $title")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            -1
        }
    }
    
    /**
     * Saves a single message to a session.
     * RESILIENT: If sessionId is invalid (-1), auto-creates a session.
     */
    suspend fun saveMessage(sessionId: Long, message: ChatMessage) = withContext(Dispatchers.IO) {
        try {
            // FIX: Auto-create session if invalid
            val validSessionId = if (sessionId <= 0) {
                Log.w(TAG, "Invalid session ID ($sessionId), auto-creating session")
                createSession(if (message.isUser) message.content else "Chat Session")
            } else {
                sessionId
            }

            if (validSessionId <= 0) {
                Log.e(TAG, "Cannot save message — failed to create session")
                return@withContext
            }

            val saved = SavedChatMessage(
                sessionId = validSessionId,
                content = message.content,
                isUser = message.isUser,
                timestamp = message.timestamp
            )
            messageDao.insertMessage(saved)
            
            // Update session metadata
            sessionDao.getSessionById(validSessionId)?.let { session ->
                val msgCount = messageDao.getMessageCount(validSessionId)
                sessionDao.updateSession(session.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = msgCount,
                    topic = if (message.isUser && msgCount <= 1) detectTopic(message.content) else session.topic,
                    title = if (message.isUser && msgCount <= 1) generateTitle(message.content) else session.title
                ))
            }
            
            Log.d(TAG, "Saved ${if (message.isUser) "user" else "AI"} message to session $validSessionId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message", e)
        }
    }

    /**
     * Saves multiple messages at once. Used for restoring sessions.
     * WARNING: This inserts all messages — only use for bulk import, not for live chat.
     */
    suspend fun saveMessages(sessionId: Long, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        try {
            val savedMessages = messages.map { msg ->
                SavedChatMessage(
                    sessionId = sessionId,
                    content = msg.content,
                    isUser = msg.isUser,
                    timestamp = msg.timestamp
                )
            }
            
            messageDao.insertMessages(savedMessages)
            
            sessionDao.getSessionById(sessionId)?.let {
                sessionDao.updateSession(it.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = messageDao.getMessageCount(sessionId)
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save messages batch", e)
        }
    }
    
    suspend fun getSessionsByDate(): Map<String, List<ChatSession>> = withContext(Dispatchers.IO) {
        try {
            val sessions = sessionDao.getAllSessions()
            groupSessionsByDate(sessions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sessions by date", e)
            emptyMap()
        }
    }
    
    suspend fun getSessionsByTopic(): Map<String, List<ChatSession>> = withContext(Dispatchers.IO) {
        try {
            val sessions = sessionDao.getAllSessions()
            sessions.groupBy { it.topic }.toSortedMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sessions by topic", e)
            emptyMap()
        }
    }
    
    suspend fun loadSessionMessages(sessionId: Long): List<ChatMessage> = withContext(Dispatchers.IO) {
        try {
            messageDao.getMessagesForSession(sessionId).map { saved ->
                ChatMessage(
                    content = saved.content,
                    isUser = saved.isUser,
                    timestamp = saved.timestamp
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages for session $sessionId", e)
            emptyList()
        }
    }
    
    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        try { sessionDao.deleteSessionById(sessionId) } catch (e: Exception) { Log.e(TAG, "Delete failed", e) }
    }
    
    suspend fun togglePin(sessionId: Long) = withContext(Dispatchers.IO) {
        try {
            sessionDao.getSessionById(sessionId)?.let {
                sessionDao.setPinned(sessionId, !it.isPinned)
            }
        } catch (e: Exception) { Log.e(TAG, "Pin toggle failed", e) }
    }
    
    suspend fun updateTitle(sessionId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        try {
            sessionDao.getSessionById(sessionId)?.let {
                sessionDao.updateSession(it.copy(title = newTitle))
            }
        } catch (e: Exception) { Log.e(TAG, "Title update failed", e) }
    }
    
    suspend fun searchSessions(query: String): List<ChatSession> = withContext(Dispatchers.IO) {
        try {
            val allSessions = sessionDao.getAllSessions()
            val lowerQuery = query.lowercase()
            allSessions.filter { session ->
                session.title.lowercase().contains(lowerQuery) ||
                session.topic.lowercase().contains(lowerQuery)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }
    
    suspend fun getTotalSessions(): Int = withContext(Dispatchers.IO) {
        try { sessionDao.getSessionCount() } catch (e: Exception) { 0 }
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        try {
            val sessions = sessionDao.getAllSessions()
            sessions.forEach { sessionDao.deleteSessionById(it.id) }
            Log.d(TAG, "Cleared all chat history (${sessions.size} sessions)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear history", e)
        }
    }
    
    private fun detectTopic(message: String): String {
        val lowerMessage = message.lowercase()
        for ((topic, keywords) in TOPIC_KEYWORDS) {
            if (keywords.any { lowerMessage.contains(it) }) return topic
        }
        return "General"
    }
    
    private fun generateTitle(message: String): String {
        val clean = message.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
        return if (clean.length > 30) clean.take(30) + "..." else clean.ifEmpty { "Chat" }
    }
    
    private fun groupSessionsByDate(sessions: List<ChatSession>): Map<String, List<ChatSession>> {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000
        val oneWeek = 7 * oneDay
        
        return sessions.groupBy { session ->
            val diff = now - session.updatedAt
            when {
                diff < oneDay -> "Today"
                diff < 2 * oneDay -> "Yesterday"
                diff < oneWeek -> "This Week"
                diff < 2 * oneWeek -> "Last Week"
                diff < 30L * oneDay -> "This Month"
                else -> "Older"
            }
        }
    }
}
