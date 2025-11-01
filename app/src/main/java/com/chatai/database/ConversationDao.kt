package com.chatai.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour accéder aux conversations
 * Permet recherche, filtrage, statistiques
 */
@Dao
interface ConversationDao {
    
    // ========== INSERTION ==========
    
    @Insert
    suspend fun insert(conversation: ConversationEntity): Long
    
    @Insert
    suspend fun insertAll(conversations: List<ConversationEntity>)
    
    @Update
    suspend fun update(conversation: ConversationEntity)
    
    @Delete
    suspend fun delete(conversation: ConversationEntity)
    
    // ========== RÉCUPÉRATION ==========
    
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastConversations(limit: Int = 50): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    fun observeLastConversations(limit: Int = 50): Flow<List<ConversationEntity>>
    
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): ConversationEntity?
    
    @Query("SELECT * FROM conversations WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getConversationsBySession(sessionId: String): List<ConversationEntity>
    
    // ========== FILTRES ==========
    
    @Query("SELECT * FROM conversations WHERE personality = :personality ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getConversationsByPersonality(personality: String, limit: Int = 100): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE platform = :platform ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getConversationsByPlatform(platform: String, limit: Int = 100): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE apiUsed = :apiUsed ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getConversationsByAPI(apiUsed: String, limit: Int = 100): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getConversationsByTimeRange(startTime: Long, endTime: Long): List<ConversationEntity>
    
    // ========== RECHERCHE ==========
    
    @Query("""
        SELECT * FROM conversations 
        WHERE userMessage LIKE '%' || :query || '%' 
           OR aiResponse LIKE '%' || :query || '%'
           OR tags LIKE '%' || :query || '%'
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun searchConversations(query: String, limit: Int = 50): List<ConversationEntity>
    
    @Query("""
        SELECT * FROM conversations 
        WHERE personality = :personality 
          AND (userMessage LIKE '%' || :query || '%' OR aiResponse LIKE '%' || :query || '%')
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    suspend fun searchConversationsByPersonality(
        personality: String, 
        query: String, 
        limit: Int = 50
    ): List<ConversationEntity>
    
    // ========== STATISTIQUES ==========
    
    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun getTotalConversations(): Int
    
    @Query("SELECT COUNT(*) FROM conversations WHERE personality = :personality")
    suspend fun getConversationCountByPersonality(personality: String): Int
    
    @Query("SELECT AVG(responseTimeMs) FROM conversations WHERE responseTimeMs > 0")
    suspend fun getAverageResponseTime(): Long?
    
    @Query("SELECT apiUsed FROM conversations GROUP BY apiUsed ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun getMostUsedAPI(): String?
    
    @Query("SELECT MIN(timestamp) FROM conversations")
    suspend fun getFirstConversationDate(): Long?
    
    @Query("SELECT MAX(timestamp) FROM conversations")
    suspend fun getLastConversationDate(): Long?
    
    @Query("SELECT SUM(LENGTH(userMessage) + LENGTH(aiResponse)) FROM conversations")
    suspend fun getTotalCharacters(): Long?
    
    // ========== NETTOYAGE ==========
    
    @Query("DELETE FROM conversations WHERE timestamp < :beforeTimestamp")
    suspend fun deleteConversationsBefore(beforeTimestamp: Long): Int
    
    @Query("DELETE FROM conversations WHERE personality = :personality")
    suspend fun deleteConversationsByPersonality(personality: String): Int
    
    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()
    
    // ========== EXPORT ==========
    
    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    suspend fun getAllConversationsForExport(): List<ConversationEntity>
    
    @Query("SELECT * FROM conversations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getConversationsForExportByRange(startTime: Long, endTime: Long): List<ConversationEntity>
    
    // ========== POUR RAG FUTUR ==========
    
    @Query("SELECT * FROM conversations WHERE embeddingsJson IS NOT NULL ORDER BY timestamp DESC")
    suspend fun getConversationsWithEmbeddings(): List<ConversationEntity>
    
    @Query("UPDATE conversations SET embeddingsJson = :embeddings WHERE id = :id")
    suspend fun updateEmbeddings(id: Long, embeddings: String)
}

