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
    
    // ⭐ NOUVEAU Phase 4: Statistiques avancées
    @Query("SELECT SUM(responseTimeMs) FROM conversations WHERE responseTimeMs > 0")
    suspend fun getTotalConversationTime(): Long?
    
    @Query("SELECT AVG(LENGTH(userMessage) + LENGTH(aiResponse)) FROM conversations")
    suspend fun getAverageMessageLength(): Double?
    
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
    
    @Query("SELECT COUNT(*) FROM conversations WHERE embeddingsJson IS NOT NULL")
    suspend fun getConversationsWithEmbeddingsCount(): Int
    
    @Query("UPDATE conversations SET embeddingsJson = :embeddings WHERE id = :id")
    suspend fun updateEmbeddings(id: Long, embeddings: String)
}

/**
 * Utilitaires pour calcul de similarité cosine
 * Utilisé pour la recherche sémantique dans les embeddings
 * 
 * ⭐ SELON NOS RULES: Fonction pure, pas de dépendances Android
 */
object SimilarityUtils {
    
    /**
     * Calcule la similarité cosine entre deux vecteurs d'embeddings
     * @param embedding1 Premier vecteur d'embedding
     * @param embedding2 Deuxième vecteur d'embedding
     * @return Score entre 0.0 (pas similaire) et 1.0 (identique)
     * 
     * Formule: cosine_similarity = dot(A,B) / (||A|| * ||B||)
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            android.util.Log.w("SimilarityUtils", "Embedding dimension mismatch: ${embedding1.size} vs ${embedding2.size}")
            return 0f
        }
        
        // Calcul du produit scalaire (dot product)
        var dotProduct = 0f
        // Calcul des normes L2
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        // Vérifier que les normes ne sont pas nulles (vecteurs non nuls)
        if (norm1 == 0f || norm2 == 0f) {
            android.util.Log.w("SimilarityUtils", "Zero norm detected: norm1=$norm1, norm2=$norm2")
            return 0f
        }
        
        // Calcul de la similarité cosine
        val similarity = dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
        
        // Assurer que le résultat est dans [0, 1] (parfois des erreurs de précision peuvent donner légèrement > 1)
        return similarity.coerceIn(0f, 1f)
    }
    
    /**
     * Calcule la distance euclidienne entre deux vecteurs
     * Plus la distance est petite, plus les vecteurs sont similaires
     * @return Distance (toujours positive, 0 = identique)
     */
    fun euclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            return Float.MAX_VALUE
        }
        
        var sumSquaredDiff = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sumSquaredDiff += diff * diff
        }
        
        return kotlin.math.sqrt(sumSquaredDiff)
    }
}

