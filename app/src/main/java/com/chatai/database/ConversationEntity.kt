package com.chatai.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Entité de conversation pour la base de données
 * Stocke TOUTES les conversations ChatAI/KITT pour apprentissage et mémoire
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Timing
    val timestamp: Long = System.currentTimeMillis(),
    
    // Contenu
    val userMessage: String,
    val aiResponse: String,
    
    // Contexte
    val personality: String = "KITT", // "KITT" ou "GLaDOS"
    val apiUsed: String = "local", // "openai", "anthropic", "ollama", "local_fallback"
    val responseTimeMs: Long = 0, // Temps de réponse en millisecondes
    
    // Métadonnées
    val platform: String = "vocal", // "vocal" (KITT) ou "web" (ChatAI web)
    val sessionId: String? = null, // Pour grouper les conversations
    
    // Pour RAG futur
    val embeddingsJson: String? = null, // Vecteur d'embeddings sérialisé
    val tags: String? = null, // Tags séparés par virgules pour recherche
    
    // Feedback utilisateur
    val userRating: Int? = null, // 1-5 étoiles (optionnel)
    val wasHelpful: Boolean? = null // Feedback binaire
)

/**
 * Statistiques de conversation
 */
data class ConversationStats(
    val totalConversations: Int,
    val kittConversations: Int,
    val gladosConversations: Int,
    val averageResponseTime: Long,
    val mostUsedAPI: String,
    val totalTokensEstimated: Long,
    val firstConversationDate: Long?,
    val lastConversationDate: Long?
)

/**
 * Recherche de conversation
 */
data class ConversationSearchResult(
    val conversation: ConversationEntity,
    val relevanceScore: Float, // 0.0-1.0 pour RAG
    val matchedTerms: List<String>
)

/**
 * Type Converters pour Room
 */
class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromStringList(value: String?): List<String>? {
        if (value == null) return null
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }
    
    @TypeConverter
    fun toStringList(list: List<String>?): String? {
        return gson.toJson(list)
    }
    
    @TypeConverter
    fun fromFloatList(value: String?): List<Float>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Float>>() {}.type
        return gson.fromJson(value, listType)
    }
    
    @TypeConverter
    fun toFloatList(list: List<Float>?): String? {
        return gson.toJson(list)
    }
}

