package com.example.politai

import androidx.room.*

/**
 * PoLiTAI - Chat Session Data Models
 * Room database entities for saving chat history
 */

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,           // Auto-generated or user-edited title
    val topic: String,           // Categorized topic (Politics, Schemes, etc.)
    val createdAt: Long,         // Timestamp
    val updatedAt: Long,         // Last message timestamp
    val messageCount: Int = 0,   // Number of messages
    val isPinned: Boolean = false
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SavedChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val sessionId: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val hasAttachment: Boolean = false,
    val attachmentType: String? = null,  // "pdf", "image", "voice"
    val attachmentUri: String? = null
)

// DAO for Chat Sessions
@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun getAllSessions(): List<ChatSession>
    
    @Query("SELECT * FROM chat_sessions WHERE topic = :topic ORDER BY updatedAt DESC")
    suspend fun getSessionsByTopic(topic: String): List<ChatSession>
    
    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ChatSession?
    
    @Query("SELECT * FROM chat_sessions WHERE updatedAt > :since ORDER BY updatedAt DESC")
    suspend fun getRecentSessions(since: Long): List<ChatSession>
    
    @Insert
    suspend fun insertSession(session: ChatSession): Long
    
    @Update
    suspend fun updateSession(session: ChatSession)
    
    @Delete
    suspend fun deleteSession(session: ChatSession)
    
    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
    
    @Query("UPDATE chat_sessions SET isPinned = :pinned WHERE id = :sessionId")
    suspend fun setPinned(sessionId: Long, pinned: Boolean)
    
    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun getSessionCount(): Int
}

// DAO for Chat Messages
@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<SavedChatMessage>
    
    @Insert
    suspend fun insertMessage(message: SavedChatMessage): Long
    
    @Insert
    suspend fun insertMessages(messages: List<SavedChatMessage>)
    
    @Delete
    suspend fun deleteMessage(message: SavedChatMessage)
    
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)
    
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int
}

// Database
@Database(entities = [ChatSession::class, SavedChatMessage::class], version = 1)
abstract class PoLiTAIDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
}