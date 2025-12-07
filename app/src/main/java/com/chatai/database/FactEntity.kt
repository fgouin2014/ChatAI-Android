package com.chatai.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entité pour stocker les faits extraits des conversations KITT
 * Permet au RAG de retrouver rapidement des informations sur l'utilisateur
 * 
 * Types de faits:
 * - "user_name": Nom de l'utilisateur
 * - "preference": Préférences (ex: "préfère Python", "aime les jeux vidéo")
 * - "action": Actions effectuées par KITT (ex: "a ouvert la musique")
 * - "context": Contexte système (ex: "WiFi activé", "volume à 50%")
 * - "fact": Fait général appris (ex: "habite à Paris", "travaille comme développeur")
 */
@Entity(
    tableName = "kitt_facts",
    indices = [
        androidx.room.Index(value = ["type"]),
        androidx.room.Index(value = ["timestamp"]),
        androidx.room.Index(value = ["sourceConversationId"])
    ]
)
data class FactEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    // Type de fait (voir documentation ci-dessus)
    val type: String,
    
    // Contenu du fait (texte libre)
    val content: String,
    
    // ID de la conversation source (pour traçabilité)
    val sourceConversationId: String,
    
    // Score de confiance (0.0-1.0) - plus le score est élevé, plus le fait est fiable
    val confidence: Float = 0.8f,
    
    // Timestamp de création
    val timestamp: Long = System.currentTimeMillis(),
    
    // Tags supplémentaires pour recherche (séparés par virgules)
    val tags: String? = null
)

