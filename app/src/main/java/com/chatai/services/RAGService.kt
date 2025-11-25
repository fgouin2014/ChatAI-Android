package com.chatai.services

import android.content.Context
import android.util.Log
import com.chatai.database.ConversationDao
import com.chatai.database.ConversationEntity
import com.chatai.database.SimilarityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service pour recherche sémantique (RAG - Retrieval-Augmented Generation)
 * Utilise les embeddings pour trouver des conversations similaires dans l'historique
 * 
 * ⭐ SELON NOS RULES:
 * - Utilise EmbeddingService pour générer des embeddings
 * - Utilise ConversationDao pour rechercher dans Room DB
 * - Utilise SimilarityUtils pour calculer la similarité cosine
 * - Construction de contexte non-bloquante (continue même si RAG échoue)
 */
class RAGService(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val embeddingService: EmbeddingService
) {
    
    companion object {
        private const val TAG = "RAGService"
        private const val DEFAULT_TOP_K = 5 // Nombre de conversations similaires à retourner
        private const val MIN_SIMILARITY_SCORE = 0.5f // Score minimum pour considérer comme similaire (0.0-1.0)
        private const val MAX_SEARCH_CONVERSATIONS = 500 // ⭐ OPTIMISATION: Limiter recherche à 500 conversations max
        private const val PERFORMANCE_THRESHOLD = 1000 // Si > 1000 conversations, limiter la recherche
    }
    
    /**
     * Recherche les conversations similaires à une requête utilisateur
     * 
     * ⭐ MODE OFFLINE: Support recherche textuelle si embedding indisponible
     * 
     * @param queryEmbedding Embedding de la requête utilisateur (768 dimensions pour nomic-embed-text), optionnel
     * @param queryText Texte de la requête utilisateur pour fallback textuel, optionnel
     * @param topK Nombre de conversations à retourner (par défaut: 5)
     * @return Liste de conversations similaires avec score de pertinence (triées par score décroissant)
     */
    suspend fun searchSimilarConversations(
        queryEmbedding: FloatArray? = null,
        queryText: String? = null,
        topK: Int = DEFAULT_TOP_K
    ): List<ConversationSearchResult> = withContext(Dispatchers.IO) {
        
        try {
            // ⭐ MODE OFFLINE: Si pas d'embedding mais texte fourni → recherche textuelle
            if (queryEmbedding == null || queryEmbedding.isEmpty()) {
                if (queryText != null && queryText.isNotBlank()) {
                    Log.d(TAG, "No embedding available, using text-based search (offline mode)")
                    return@withContext textBasedSearch(queryText, topK)
                } else {
                    Log.w(TAG, "Query embedding is empty and no query text provided, returning empty list")
                    return@withContext emptyList()
                }
            }
            
            // Récupérer toutes les conversations avec embeddings
            val allConversations = conversationDao.getConversationsWithEmbeddings()
            
            if (allConversations.isEmpty()) {
                Log.d(TAG, "No conversations with embeddings found in database")
                return@withContext emptyList()
            }
            
            // ⭐ AUDIT: Log pour diagnostiquer le problème de session
            val totalWithEmbeddings = allConversations.size
            val uniqueSessions = allConversations.mapNotNull { it.sessionId }.distinct().size
            Log.i(TAG, "🔍 RAG Search: $totalWithEmbeddings conversations avec embeddings, $uniqueSessions sessions uniques")
            
            // ⭐ OPTIMISATION: Limiter la recherche si trop de conversations (performance)
            val conversationsToSearch = if (allConversations.size > PERFORMANCE_THRESHOLD) {
                Log.d(TAG, "⚠️ ${allConversations.size} conversations avec embeddings (> $PERFORMANCE_THRESHOLD), limitant recherche aux $MAX_SEARCH_CONVERSATIONS plus récentes")
                // Prendre les N plus récentes (déjà triées par timestamp DESC dans DAO)
                allConversations.take(MAX_SEARCH_CONVERSATIONS)
            } else {
                allConversations
            }
            
            Log.d(TAG, "Searching in ${conversationsToSearch.size} conversations with embeddings (sur ${allConversations.size} total)")
            
            // Calculer la similarité pour chaque conversation
            val results = mutableListOf<ConversationSearchResult>()
            
            for (conversation in conversationsToSearch) {
                val conversationEmbeddingJson = conversation.embeddingsJson
                    ?: continue // Ignorer les conversations sans embeddings
                
                // Convertir JSON en FloatArray
                val conversationEmbedding = embeddingService.jsonToEmbedding(conversationEmbeddingJson)
                    ?: continue // Ignorer si parsing échoue
                
                // Calculer la similarité cosine
                val similarity = SimilarityUtils.cosineSimilarity(queryEmbedding, conversationEmbedding)
                
                // Ajouter seulement si le score dépasse le seuil minimum
                if (similarity >= MIN_SIMILARITY_SCORE) {
                    results.add(
                        ConversationSearchResult(
                            conversation = conversation,
                            relevanceScore = similarity,
                            matchedTerms = emptyList() // Pour l'instant, pas d'extraction de termes
                        )
                    )
                }
            }
            
            // Trier par score décroissant et prendre les top-K
            val topResults = results
                .sortedByDescending { it.relevanceScore }
                .take(topK)
            
            Log.d(TAG, "✅ Found ${topResults.size} similar conversations (seuil: $MIN_SIMILARITY_SCORE)")
            topResults.forEach { result ->
                val sessionInfo = result.conversation.sessionId?.let { "sessionId: $it" } ?: "no sessionId"
                Log.d(TAG, "  - Score: ${String.format("%.3f", result.relevanceScore)}, $sessionInfo, Message: ${result.conversation.userMessage.take(50)}...")
            }
            
            return@withContext topResults
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching similar conversations", e)
            // ⭐ SELON NOS RULES: Ne pas bloquer si RAG échoue, retourner liste vide
            return@withContext emptyList()
        }
    }
    
    /**
     * Construit le contexte RAG à partir de conversations similaires
     * Format textuel pour injection dans le system prompt d'Ollama
     * 
     * ⭐ SELON NOS RULES: Limiter la longueur du contexte pour éviter dépassement de tokens
     * 
     * @param searchResults Liste de conversations similaires avec scores
     * @return Contexte textuel formaté pour Ollama (ou chaîne vide si aucune conversation)
     */
    fun buildRAGContext(searchResults: List<ConversationSearchResult>): String {
        if (searchResults.isEmpty()) {
            return ""
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.appendLine("[CONTEXTE - Conversations similaires de l'historique]")
        contextBuilder.appendLine()
        
        // Limiter à 5 conversations maximum pour éviter dépassement de tokens
        val limitedResults = searchResults.take(5)
        
        limitedResults.forEachIndexed { index, result ->
            val conv = result.conversation
            
            contextBuilder.appendLine("Conversation ${index + 1} (Pertinence: ${String.format("%.2f", result.relevanceScore)}):")
            contextBuilder.appendLine("Q: ${conv.userMessage}")
            
            // Limiter la longueur de la réponse pour éviter dépassement
            val responsePreview = if (conv.aiResponse.length > 200) {
                conv.aiResponse.take(200) + "..."
            } else {
                conv.aiResponse
            }
            contextBuilder.appendLine("R: $responsePreview")
            
            // Ajouter thinking trace si disponible et court
            if (!conv.thinkingTrace.isNullOrEmpty() && conv.thinkingTrace.length < 150) {
                contextBuilder.appendLine("Raisonnement: ${conv.thinkingTrace.take(150)}...")
            }
            
            contextBuilder.appendLine()
        }
        
        contextBuilder.appendLine("[FIN CONTEXTE]")
        
        val context = contextBuilder.toString()
        
        // ⭐ SELON NOS RULES: Limiter à ~2000 tokens (environ 1500 caractères)
        // Si le contexte est trop long, le tronquer
        val maxContextLength = 1500
        if (context.length > maxContextLength) {
            Log.w(TAG, "RAG context too long (${context.length} chars), truncating to $maxContextLength chars")
            return context.take(maxContextLength) + "\n... [CONTEXTE TRONQUÉ]"
        }
        
        Log.d(TAG, "✅ RAG context built: ${context.length} chars, ${limitedResults.size} conversations")
        return context
    }
    
    /**
     * Recherche des conversations similaires à partir d'un texte (pas d'embedding)
     * Génère l'embedding puis recherche
     * 
     * ⭐ SELON NOS RULES: Méthode helper pour simplifier l'utilisation
     */
    suspend fun searchSimilarConversationsFromText(
        queryText: String,
        topK: Int = DEFAULT_TOP_K
    ): List<ConversationSearchResult> = withContext(Dispatchers.IO) {
        try {
            // Générer l'embedding de la requête
            val queryEmbedding = embeddingService.embed(queryText)
            
            // Rechercher des conversations similaires (avec fallback textuel si embedding échoue)
            return@withContext searchSimilarConversations(
                queryEmbedding = queryEmbedding,
                queryText = queryText, // ⭐ Passer le texte pour fallback offline
                topK = topK
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching similar conversations from text", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Recherche textuelle basée sur mots-clés (mode offline)
     * Utilise la recherche SQL existante et calcule un score de pertinence
     * 
     * ⭐ MODE OFFLINE: Fallback quand embeddings indisponibles
     * 
     * @param queryText Texte de la requête utilisateur
     * @param topK Nombre de conversations à retourner
     * @return Liste de conversations avec score de pertinence textuelle
     */
    private suspend fun textBasedSearch(
        queryText: String,
        topK: Int = DEFAULT_TOP_K
    ): List<ConversationSearchResult> = withContext(Dispatchers.IO) {
        
        try {
            Log.d(TAG, "Text-based search: '$queryText'")
            
            // Utiliser recherche textuelle SQL existante
            val results = conversationDao.searchConversations(queryText, topK * 2) // Prendre plus pour filtrer
            
            if (results.isEmpty()) {
                Log.d(TAG, "No conversations found with text search")
                return@withContext emptyList()
            }
            
            // Calculer score de pertinence pour chaque résultat
            val scoredResults = results.map { conv ->
                val score = calculateTextRelevance(queryText, conv.userMessage + " " + conv.aiResponse)
                val matchedTerms = extractMatchedTerms(queryText, conv.userMessage + " " + conv.aiResponse)
                
                ConversationSearchResult(
                    conversation = conv,
                    relevanceScore = score,
                    matchedTerms = matchedTerms
                )
            }
            
            // Trier par score décroissant et prendre top-K
            val topResults = scoredResults
                .sortedByDescending { it.relevanceScore }
                .take(topK)
            
            Log.d(TAG, "✅ Found ${topResults.size} conversations with text search")
            topResults.forEach { result ->
                Log.d(TAG, "  - Score: ${String.format("%.3f", result.relevanceScore)}, ID: ${result.conversation.conversationId}, Message: ${result.conversation.userMessage.take(50)}...")
            }
            
            return@withContext topResults
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in text-based search", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Calcule un score de pertinence textuelle (0.0-1.0)
     * Basé sur le nombre de mots correspondants et leur position
     */
    private fun calculateTextRelevance(query: String, text: String): Float {
        val queryWords = query.lowercase()
            .split(" ")
            .filter { it.length > 2 } // Ignorer mots trop courts
        
        if (queryWords.isEmpty()) {
            return 0.5f // Score par défaut si requête trop courte
        }
        
        val textLower = text.lowercase()
        var score = 0f
        
        queryWords.forEach { word ->
            // Compter occurrences
            val count = textLower.split(word).size - 1
            score += count * 0.1f
            
            // Bonus si mot au début du texte (plus pertinent)
            if (textLower.startsWith(word)) {
                score += 0.2f
            }
            
            // Bonus si mot dans userMessage (plus pertinent que aiResponse)
            // (on pourrait améliorer en séparant userMessage et aiResponse)
        }
        
        // Normaliser entre 0.0 et 1.0
        val normalizedScore = minOf(1.0f, score / queryWords.size)
        
        return normalizedScore
    }
    
    /**
     * Extrait les termes correspondants entre la requête et le texte
     */
    private fun extractMatchedTerms(query: String, text: String): List<String> {
        val queryWords = query.lowercase().split(" ").filter { it.length > 2 }
        val textLower = text.lowercase()
        
        return queryWords.filter { word ->
            textLower.contains(word)
        }
    }
}

/**
 * Résultat de recherche sémantique
 * Contient une conversation et son score de pertinence
 * 
 * ⭐ SELON NOS RULES: Data class pour représenter les résultats de recherche
 */
data class ConversationSearchResult(
    val conversation: ConversationEntity,
    val relevanceScore: Float, // Score de similarité (0.0-1.0, 1.0 = identique)
    val matchedTerms: List<String> // Termes correspondants (pour l'instant vide, future amélioration)
)


