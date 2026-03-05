package com.example.politai

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * PoLiTAI - Chat History Manager
 * Manages saving, loading, and organizing chat sessions
 * Master Grade Edition
 */

class ChatHistoryManager(context: Context) {
    
    private val database = Room.databaseBuilder(
        context.applicationContext,
        PoLiTAIDatabase::class.java,
        "politai_chat_history.db"
    ).fallbackToDestructiveMigration().build()
    
    private val sessionDao = database.chatSessionDao()
    private val messageDao = database.chatMessageDao()
    
    companion object {
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
            "Politics & Leaders" to listOf("minister", "mp", "mla", "politician", "party", "leader", "cabinet", "modi", "shah", "rahul"),
            "Government Schemes" to listOf("scheme", "yojana", "pm-kisan", "ayushman", "benefit", "subsidy", "pmgsy", "nrega"),
            "Budget & Finance" to listOf("budget", "allocation", "crore", "finance", "expenditure", "revenue", "fiscal"),
            "Meetings & Minutes" to listOf("meeting", "minutes", "agenda", "decision", "committee", "cabinet"),
            "Bills & Legislation" to listOf("bill", "act", "amendment", "parliament", "legislation", "law", "article"),
            "Constituency Issues" to listOf("complaint", "issue", "district", "constituency", "problem", "grievance"),
            "Economic Data" to listOf("gdp", "cpi", "inflation", "rbi", "repo", "economic", "growth", "rate")
        )
    }
    
    suspend fun createSession(firstMessage: String): Long = withContext(Dispatchers.IO) {
        val topic = detectTopic(firstMessage)
        val title = generateTitle(firstMessage)
        val now = System.currentTimeMillis()
        
        val session = ChatSession(
            title = title,
            topic = topic,
            createdAt = now,
            updatedAt = now,
            messageCount = 1
        )
        
        sessionDao.insertSession(session)
    }
    
    suspend fun saveMessages(sessionId: Long, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val savedMessages = messages.map { msg ->
            SavedChatMessage(
                sessionId = sessionId,
                content = msg.content,
                isUser = msg.isUser,
                timestamp = msg.timestamp
            )
        }
        
        messageDao.insertMessages(savedMessages)
        
        val session = sessionDao.getSessionById(sessionId)
        session?.let {
            val updated = it.copy(
                updatedAt = System.currentTimeMillis(),
                messageCount = messageDao.getMessageCount(sessionId)
            )
            sessionDao.updateSession(updated)
        }
    }
    
    suspend fun getSessionsByDate(): Map<String, List<ChatSession>> = withContext(Dispatchers.IO) {
        val sessions = sessionDao.getAllSessions()
        groupSessionsByDate(sessions)
    }
    
    suspend fun getSessionsByTopic(): Map<String, List<ChatSession>> = withContext(Dispatchers.IO) {
        val sessions = sessionDao.getAllSessions()
        sessions.groupBy { it.topic }.toSortedMap()
    }
    
    suspend fun loadSessionMessages(sessionId: Long): List<ChatMessage> = withContext(Dispatchers.IO) {
        messageDao.getMessagesForSession(sessionId).map { saved ->
            ChatMessage(
                content = saved.content,
                isUser = saved.isUser,
                timestamp = saved.timestamp
            )
        }
    }
    
    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        sessionDao.deleteSessionById(sessionId)
    }
    
    suspend fun togglePin(sessionId: Long) = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId)
        session?.let {
            sessionDao.setPinned(sessionId, !it.isPinned)
        }
    }
    
    suspend fun updateTitle(sessionId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId)
        session?.let {
            sessionDao.updateSession(it.copy(title = newTitle))
        }
    }
    
    suspend fun searchSessions(query: String): List<ChatSession> = withContext(Dispatchers.IO) {
        val allSessions = sessionDao.getAllSessions()
        val lowerQuery = query.lowercase()
        
        allSessions.filter { session ->
            session.title.lowercase().contains(lowerQuery) ||
            session.topic.lowercase().contains(lowerQuery)
        }
    }
    
    suspend fun getTotalSessions(): Int = withContext(Dispatchers.IO) {
        sessionDao.getSessionCount()
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
        return if (clean.length > 30) clean.take(30) + "..." else clean
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
