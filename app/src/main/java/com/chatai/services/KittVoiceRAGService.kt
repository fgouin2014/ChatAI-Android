package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.chatai.SecureConfig
import com.chatai.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.google.gson.Gson

/**
 * Service RAG spécialisé pour KITT Voice AI
 * 
 * Fonctionnalités:
 * - Génération et stockage d'embeddings via Hugging Face
 * - Recherche sémantique avec cosine similarity
 * - Extraction de faits depuis conversations
 * - Construction de contexte système
 * 
 * ⭐ SELON NOS RULES: Service dédié KITT, indépendant de la webapp
 */
class KittVoiceRAGService(private val context: Context) {
    
    companion object {
        private const val TAG = "KittVoiceRAGService"
        private const val DEFAULT_TOP_K = 5
        private const val DEFAULT_MIN_SIMILARITY = 0.6f
        private const val MAX_CONTEXT_LENGTH = 2000 // Limiter contexte à ~2000 caractères
    }
    
    private val secureConfig = SecureConfig(context)
    private val db = ChatAIDatabase.getDatabase(context)
    private val conversationDao = db.conversationDao()
    private val factDao = db.factDao()
    private val huggingFaceService = HuggingFaceService(context)
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    /**
     * Récupère la configuration RAG depuis SecureConfig
     */
    private fun getRAGConfig(): JSONObject {
        val config = secureConfig.getKittVoiceAIConfigWithDefaults()
        return config ?: JSONObject().apply {
            put("rag_enabled", true)
            put("rag_top_k", DEFAULT_TOP_K)
            put("rag_min_similarity", DEFAULT_MIN_SIMILARITY)
        }
    }
    
    /**
     * Génère un embedding via Hugging Face et stocke dans la conversation
     * 
     * @param conversation Conversation à enrichir avec embedding
     * @return true si embedding généré et stocké avec succès
     */
    suspend fun generateAndStoreEmbedding(conversation: ConversationEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            // Vérifier si embedding déjà présent
            if (!conversation.embeddingsJson.isNullOrEmpty()) {
                Log.d(TAG, "Embedding déjà présent pour conversation ${conversation.conversationId}")
                return@withContext true
            }
            
            // Créer texte pour embedding (userMessage + aiResponse)
            val textForEmbedding = "${conversation.userMessage}\n${conversation.aiResponse}"
            
            // Générer embedding via Hugging Face
            val embedding = huggingFaceService.generateEmbedding(textForEmbedding)
            if (embedding == null) {
                Log.w(TAG, "Échec génération embedding pour conversation ${conversation.conversationId}")
                return@withContext false
            }
            
            // Convertir embedding en JSON pour stockage
            val embeddingJson = embeddingToJson(embedding)
            
            // Mettre à jour la conversation dans la DB
            conversationDao.updateEmbeddings(conversation.id, embeddingJson)
            
            Log.d(TAG, "✅ Embedding généré et stocké pour conversation ${conversation.conversationId} (${embedding.size} dimensions)")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur génération/stockage embedding", e)
            return@withContext false
        }
    }
    
    /**
     * Récupère le contexte RAG pour une requête utilisateur
     * 
     * @param userQuery Requête utilisateur
     * @return Contexte formaté avec conversations similaires, faits et contexte système
     */
    suspend fun retrieveContext(userQuery: String): String = withContext(Dispatchers.IO) {
        try {
            val config = getRAGConfig()
            if (!config.optBoolean("rag_enabled", true)) {
                Log.d(TAG, "RAG désactivé dans la config")
                return@withContext getSystemContext() // Retourner seulement contexte système
            }
            
            val topK = config.optInt("rag_top_k", DEFAULT_TOP_K)
            val minSimilarity = config.optDouble("rag_min_similarity", DEFAULT_MIN_SIMILARITY.toDouble()).toFloat()
            
            // Générer embedding de la requête
            val queryEmbedding = huggingFaceService.generateEmbedding(userQuery)
            if (queryEmbedding == null) {
                Log.w(TAG, "Impossible de générer embedding pour la requête, utilisation contexte système uniquement")
                return@withContext getSystemContext()
            }
            
            // Récupérer conversations avec embeddings
            val conversations = conversationDao.getConversationsWithEmbeddings()
            if (conversations.isEmpty()) {
                Log.d(TAG, "Aucune conversation avec embeddings trouvée")
                return@withContext getSystemContext()
            }
            
            // Calculer similarité pour chaque conversation
            val results = mutableListOf<ConversationSearchResult>()
            for (conv in conversations) {
                val convEmbeddingJson = conv.embeddingsJson ?: continue
                val convEmbedding = jsonToEmbedding(convEmbeddingJson) ?: continue
                
                val similarity = SimilarityUtils.cosineSimilarity(queryEmbedding, convEmbedding)
                if (similarity >= minSimilarity) {
                    results.add(ConversationSearchResult(conv, similarity, emptyList()))
                }
            }
            
            // Trier par score et prendre top-K
            val topResults = results.sortedByDescending { it.relevanceScore }.take(topK)
            
            // Construire contexte
            val contextBuilder = StringBuilder()
            
            // Contexte système (toujours présent)
            contextBuilder.append(getSystemContext())
            contextBuilder.appendLine()
            
            // Conversations similaires
            if (topResults.isNotEmpty()) {
                contextBuilder.appendLine("[CONTEXTE - Conversations similaires]")
                topResults.forEachIndexed { index, result ->
                    val conv = result.conversation
                    contextBuilder.appendLine("Conversation ${index + 1} (Pertinence: ${String.format("%.2f", result.relevanceScore)}):")
                    contextBuilder.appendLine("Q: ${conv.userMessage}")
                    val responsePreview = if (conv.aiResponse.length > 150) {
                        conv.aiResponse.take(150) + "..."
                    } else {
                        conv.aiResponse
                    }
                    contextBuilder.appendLine("R: $responsePreview")
                    contextBuilder.appendLine()
                }
            }
            
            // Faits pertinents
            val relevantFacts = getRelevantFacts(userQuery, limit = 5)
            if (relevantFacts.isNotEmpty()) {
                contextBuilder.appendLine("[FAITS CONNUS]")
                relevantFacts.forEach { fact ->
                    contextBuilder.appendLine("- ${fact.content} (${fact.type})")
                }
                contextBuilder.appendLine()
            }
            
            val context = contextBuilder.toString()
            
            // Limiter la longueur
            val finalContext = if (context.length > MAX_CONTEXT_LENGTH) {
                context.take(MAX_CONTEXT_LENGTH) + "\n... [CONTEXTE TRONQUÉ]"
            } else {
                context
            }
            
            Log.d(TAG, "✅ Contexte RAG construit: ${finalContext.length} chars, ${topResults.size} conversations, ${relevantFacts.size} faits")
            return@withContext finalContext
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur récupération contexte RAG", e)
            return@withContext getSystemContext() // Fallback sur contexte système uniquement
        }
    }
    
    /**
     * Extrait des faits depuis une conversation
     * Utilise Hugging Face pour identifier des informations importantes
     * 
     * @param conversation Conversation à analyser
     * @return Liste de faits extraits
     */
    suspend fun extractFacts(conversation: ConversationEntity): List<FactEntity> = withContext(Dispatchers.IO) {
        try {
            val facts = mutableListOf<FactEntity>()
            
            // Extraire nom utilisateur
            val userMessage = conversation.userMessage.lowercase()
            if (userMessage.contains("je m'appelle") || userMessage.contains("mon nom est")) {
                val namePattern = Regex("(?:je m'appelle|mon nom est)\\s+([A-Za-z]+)")
                val match = namePattern.find(conversation.userMessage)
                if (match != null) {
                    val name = match.groupValues[1].trim()
                    facts.add(FactEntity(
                        type = "user_name",
                        content = "L'utilisateur s'appelle $name",
                        sourceConversationId = conversation.conversationId,
                        confidence = 0.9f,
                        tags = "name,personal"
                    ))
                }
            }
            
            // Extraire préférences (ex: "j'aime", "je préfère")
            val preferencePatterns = listOf(
                Regex("j'aime\\s+(.+?)(?:\\.|$)"),
                Regex("je préfère\\s+(.+?)(?:\\.|$)"),
                Regex("mon (.+?) favori")
            )
            
            preferencePatterns.forEach { pattern ->
                val matches = pattern.findAll(conversation.userMessage)
                matches.forEach { match ->
                    val content = match.groupValues[1].trim()
                    if (content.length > 3 && content.length < 100) {
                        facts.add(FactEntity(
                            type = "preference",
                            content = content,
                            sourceConversationId = conversation.conversationId,
                            confidence = 0.7f,
                            tags = "preference"
                        ))
                    }
                }
            }
            
            // Extraire faits généraux (basique pour l'instant)
            // TODO: Améliorer avec extraction via Hugging Face LLM
            
            Log.d(TAG, "✅ ${facts.size} faits extraits de la conversation ${conversation.conversationId}")
            
            // Stocker les faits dans la DB
            if (facts.isNotEmpty()) {
                factDao.insertAll(facts)
            }
            
            return@withContext facts
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur extraction faits", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Récupère le contexte système (actions récentes, préférences app, statut)
     */
    private fun getSystemContext(): String {
        val contextBuilder = StringBuilder()
        contextBuilder.appendLine("[CONTEXTE SYSTÈME]")
        
        // Actions récentes KITT (dernières 5)
        try {
            val recentFacts = factDao.getFactsByType("action", 5)
            if (recentFacts.isNotEmpty()) {
                contextBuilder.appendLine("Actions récentes:")
                recentFacts.forEach { fact ->
                    contextBuilder.appendLine("- ${fact.content}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur récupération actions récentes", e)
        }
        
        // Préférences utilisateur (nom, etc.)
        try {
            val userFacts = factDao.getFactsByType("user_name", 1)
            userFacts.forEach { fact ->
                contextBuilder.appendLine("Utilisateur: ${fact.content}")
            }
            
            val preferences = factDao.getFactsByType("preference", 3)
            if (preferences.isNotEmpty()) {
                contextBuilder.appendLine("Préférences:")
                preferences.forEach { fact ->
                    contextBuilder.appendLine("- ${fact.content}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur récupération préférences", e)
        }
        
        contextBuilder.appendLine("[FIN CONTEXTE SYSTÈME]")
        return contextBuilder.toString()
    }
    
    /**
     * Récupère des faits pertinents pour une requête
     */
    private suspend fun getRelevantFacts(query: String, limit: Int = 5): List<FactEntity> = withContext(Dispatchers.IO) {
        try {
            // Recherche textuelle simple dans les faits
            val allFacts = factDao.getLastFacts(50)
            val queryLower = query.lowercase()
            
            val relevantFacts = allFacts.filter { fact ->
                fact.content.lowercase().contains(queryLower) ||
                fact.tags?.lowercase()?.contains(queryLower) == true
            }.take(limit)
            
            return@withContext relevantFacts
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur récupération faits pertinents", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Convertit un FloatArray en JSON pour stockage
     */
    private fun embeddingToJson(embedding: FloatArray): String {
        val gson = Gson()
        return gson.toJson(embedding.toList())
    }
    
    /**
     * Convertit un JSON en FloatArray
     */
    private fun jsonToEmbedding(json: String): FloatArray? {
        return try {
            val gson = Gson()
            val list = gson.fromJson(json, Array<Double>::class.java).toList()
            FloatArray(list.size) { list[it].toFloat() }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur parsing embedding JSON", e)
            null
        }
    }
}

