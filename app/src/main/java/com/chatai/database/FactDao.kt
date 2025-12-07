package com.chatai.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO pour accéder aux faits extraits des conversations KITT
 */
@Dao
interface FactDao {
    
    // ========== INSERTION ==========
    
    @Insert
    suspend fun insert(fact: FactEntity): Long
    
    @Insert
    suspend fun insertAll(facts: List<FactEntity>)
    
    @Update
    suspend fun update(fact: FactEntity)
    
    @Delete
    suspend fun delete(fact: FactEntity)
    
    // ========== RÉCUPÉRATION ==========
    
    @Query("SELECT * FROM kitt_facts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastFacts(limit: Int = 50): List<FactEntity>
    
    @Query("SELECT * FROM kitt_facts ORDER BY timestamp DESC LIMIT :limit")
    fun observeLastFacts(limit: Int = 50): Flow<List<FactEntity>>
    
    @Query("SELECT * FROM kitt_facts WHERE id = :id")
    suspend fun getFactById(id: String): FactEntity?
    
    // ========== FILTRES PAR TYPE ==========
    
    @Query("SELECT * FROM kitt_facts WHERE type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getFactsByType(type: String, limit: Int = 100): List<FactEntity>
    
    @Query("SELECT * FROM kitt_facts WHERE type = :type ORDER BY timestamp DESC")
    fun observeFactsByType(type: String): Flow<List<FactEntity>>
    
    // ========== FILTRES PAR CONVERSATION ==========
    
    @Query("SELECT * FROM kitt_facts WHERE sourceConversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getFactsByConversation(conversationId: String): List<FactEntity>
    
    // ========== FILTRES PAR CONFIANCE ==========
    
    @Query("SELECT * FROM kitt_facts WHERE confidence >= :minConfidence ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getFactsByMinConfidence(minConfidence: Float = 0.7f, limit: Int = 100): List<FactEntity>
    
    // ========== RECHERCHE ==========
    
    @Query("""
        SELECT * FROM kitt_facts 
        WHERE content LIKE '%' || :query || '%' 
           OR tags LIKE '%' || :query || '%'
        ORDER BY confidence DESC, timestamp DESC 
        LIMIT :limit
    """)
    suspend fun searchFacts(query: String, limit: Int = 50): List<FactEntity>
    
    @Query("""
        SELECT * FROM kitt_facts 
        WHERE type = :type 
          AND (content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        ORDER BY confidence DESC, timestamp DESC 
        LIMIT :limit
    """)
    suspend fun searchFactsByType(type: String, query: String, limit: Int = 50): List<FactEntity>
    
    // ========== STATISTIQUES ==========
    
    @Query("SELECT COUNT(*) FROM kitt_facts")
    suspend fun getTotalFacts(): Int
    
    @Query("SELECT COUNT(*) FROM kitt_facts WHERE type = :type")
    suspend fun getFactsCountByType(type: String): Int
    
    @Query("SELECT DISTINCT type FROM kitt_facts")
    suspend fun getAllFactTypes(): List<String>
    
    // ========== SUPPRESSION ==========
    
    @Query("DELETE FROM kitt_facts WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM kitt_facts WHERE type = :type")
    suspend fun deleteByType(type: String)
    
    @Query("DELETE FROM kitt_facts WHERE sourceConversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: String)
    
    @Query("DELETE FROM kitt_facts WHERE timestamp < :beforeTimestamp")
    suspend fun deleteBeforeTimestamp(beforeTimestamp: Long)
    
    @Query("DELETE FROM kitt_facts")
    suspend fun deleteAllFacts()
}

