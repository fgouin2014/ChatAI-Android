# 🚀 Plan d'intégration: Embeddings et RAG dans ChatAI

**Date**: 2025-11-19  
**Objectif**: Intégrer `nomic-embed-text` pour recherche sémantique et contexte automatique

---

## 📋 Vue d'ensemble

### Architecture actuelle
```
User Input → BidirectionalBridge.processWithThinking()
              ↓
         OllamaThinkingService.streamWithThinking()
              ↓
         Ollama API (Cloud/Local)
              ↓
         AI Response
```

### Architecture avec RAG
```
User Input → BidirectionalBridge.processWithThinking()
              ↓
         EmbeddingService.embed(userInput)  ⭐ NOUVEAU
              ↓
         ConversationDao.searchSimilar()   ⭐ NOUVEAU
              ↓
         Build RAG Context                 ⭐ NOUVEAU
              ↓
         OllamaThinkingService.streamWithThinking(context)
              ↓
         Ollama API (Cloud/Local)
              ↓
         AI Response (avec contexte historique!)
```

---

## 🔧 Phase 1: EmbeddingService.kt

### Fichier à créer
`ChatAI-Android/app/src/main/java/com/chatai/services/EmbeddingService.kt`

### Code
```kotlin
package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service pour générer des embeddings via Ollama
 * Utilise nomic-embed-text pour convertir du texte en vecteurs numériques
 */
class EmbeddingService(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingService"
        
        // Modèle d'embedding par défaut
        private const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text"
        
        // URL Ollama local pour embeddings
        private const val OLLAMA_EMBEDDINGS_URL = "http://localhost:11434/api/embeddings"
        
        // Dimensions de nomic-embed-text
        private const val EMBEDDING_DIMENSIONS = 768
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    /**
     * Génère un embedding pour un texte donné
     * @param text Texte à convertir en embedding
     * @return Tableau de floats (768 dimensions pour nomic-embed-text)
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                Log.w(TAG, "Text is blank, returning null")
                return@withContext null
            }
            
            // Vérifier si Ollama local est disponible
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            val apiUrl = if (useCloud) {
                // Ollama Cloud n'a pas d'endpoint embeddings dédié
                // Pour l'instant, utiliser Ollama local uniquement
                Log.w(TAG, "Ollama Cloud ne supporte pas encore les embeddings, utiliser Ollama local")
                return@withContext null
            } else {
                sharedPreferences.getString("local_server_url", null)?.trim()
                    ?: "http://localhost:11434"
            }
            
            val embeddingsUrl = "$apiUrl/api/embeddings"
            val embeddingModel = sharedPreferences.getString("embedding_model", DEFAULT_EMBEDDING_MODEL)
                ?: DEFAULT_EMBEDDING_MODEL
            
            Log.d(TAG, "Generating embedding for text (length=${text.length} chars)")
            Log.d(TAG, "Using model: $embeddingModel, URL: $embeddingsUrl")
            
            // Construire la requête
            val requestBody = JSONObject().apply {
                put("model", embeddingModel)
                put("prompt", text)
            }
            
            val request = Request.Builder()
                .url(embeddingsUrl)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            // Exécuter la requête
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Embedding request failed: ${response.code} ${response.message}")
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Embedding response body is null")
                return@withContext null
            }
            
            // Parser la réponse
            val jsonResponse = JSONObject(responseBody)
            val embeddingArray = jsonResponse.getJSONArray("embedding")
            
            // Convertir en FloatArray
            val embedding = FloatArray(embeddingArray.length()) { i ->
                embeddingArray.getDouble(i).toFloat()
            }
            
            Log.d(TAG, "✅ Embedding generated: ${embedding.size} dimensions")
            return@withContext embedding
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error generating embedding", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding", e)
            return@withContext null
        }
    }
    
    /**
     * Génère un embedding pour une conversation complète (userMessage + aiResponse)
     */
    suspend fun embedConversation(userMessage: String, aiResponse: String): FloatArray? {
        val combinedText = "$userMessage\n$aiResponse"
        return embed(combinedText)
    }
    
    /**
     * Convertit un FloatArray en JSON string pour stockage
     */
    fun embeddingToJson(embedding: FloatArray): String {
        return JSONArray().apply {
            embedding.forEach { value ->
                put(value.toDouble())
            }
        }.toString()
    }
    
    /**
     * Convertit un JSON string en FloatArray pour comparaison
     */
    fun jsonToEmbedding(jsonString: String): FloatArray? {
        return try {
            val jsonArray = JSONArray(jsonString)
            FloatArray(jsonArray.length()) { i ->
                jsonArray.getDouble(i).toFloat()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing embedding JSON", e)
            null
        }
    }
    
    /**
     * Vérifie si le service d'embedding est disponible
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Ping Ollama local
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            if (useCloud) {
                return@withContext false // Ollama Cloud ne supporte pas encore embeddings
            }
            
            val apiUrl = sharedPreferences.getString("local_server_url", null)?.trim()
                ?: "http://localhost:11434"
            
            val pingUrl = "$apiUrl/api/tags"
            val request = Request.Builder().url(pingUrl).get().build()
            val response = httpClient.newCall(request).execute()
            
            return@withContext response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Embedding service not available", e)
            return@withContext false
        }
    }
}
```

---

## 🔍 Phase 2: Extension ConversationDao.kt

### Fichier à modifier
`ChatAI-Android/app/src/main/java/com/chatai/database/ConversationDao.kt`

### Code à ajouter
```kotlin
// À ajouter dans ConversationDao.kt

// ========== RECHERCHE SÉMANTIQUE (RAG) ==========

/**
 * Recherche des conversations similaires en utilisant cosine similarity
 * ⚠️ NOTE: Cette méthode charge toutes les conversations en mémoire
 * Pour de meilleures performances, utiliser un index vectoriel (Phase 2)
 */
@Query("SELECT * FROM conversations WHERE embeddingsJson IS NOT NULL")
suspend fun getAllConversationsWithEmbeddings(): List<ConversationEntity>

/**
 * Classe helper pour la similarité cosine
 */
object SimilarityUtils {
    /**
     * Calcule la similarité cosine entre deux vecteurs
     * @return Score entre 0.0 (pas similaire) et 1.0 (identique)
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        if (embedding1.size != embedding2.size) {
            return 0f
        }
        
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        if (norm1 == 0f || norm2 == 0f) {
            return 0f
        }
        
        return dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
    }
}
```

### Service de recherche sémantique
```kotlin
// Nouveau fichier: RAGService.kt
package com.chatai.services

import android.content.Context
import android.util.Log
import com.chatai.database.ConversationDao
import com.chatai.database.ConversationEntity
import com.chatai.database.SimilarityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Service pour recherche sémantique (RAG)
 */
class RAGService(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val embeddingService: EmbeddingService
) {
    
    companion object {
        private const val TAG = "RAGService"
        private const val DEFAULT_TOP_K = 5
        private const val MIN_SIMILARITY_SCORE = 0.5f // Score minimum pour considérer comme similaire
    }
    
    /**
     * Recherche les conversations similaires à une requête
     * @param queryEmbedding Embedding de la requête utilisateur
     * @param topK Nombre de conversations à retourner
     * @return Liste de conversations similaires avec score de pertinence
     */
    suspend fun searchSimilarConversations(
        queryEmbedding: FloatArray,
        topK: Int = DEFAULT_TOP_K
    ): List<ConversationSearchResult> = withContext(Dispatchers.IO) {
        
        try {
            // Récupérer toutes les conversations avec embeddings
            val allConversations = conversationDao.getAllConversationsWithEmbeddings()
            
            if (allConversations.isEmpty()) {
                Log.d(TAG, "No conversations with embeddings found")
                return@withContext emptyList()
            }
            
            Log.d(TAG, "Searching in ${allConversations.size} conversations with embeddings")
            
            // Calculer la similarité pour chaque conversation
            val results = allConversations.mapNotNull { conversation ->
                val conversationEmbeddingJson = conversation.embeddingsJson
                    ?: return@mapNotNull null
                
                val conversationEmbedding = embeddingService.jsonToEmbedding(conversationEmbeddingJson)
                    ?: return@mapNotNull null
                
                val similarity = SimilarityUtils.cosineSimilarity(queryEmbedding, conversationEmbedding)
                
                if (similarity >= MIN_SIMILARITY_SCORE) {
                    ConversationSearchResult(
                        conversation = conversation,
                        relevanceScore = similarity,
                        matchedTerms = emptyList() // Pour l'instant, pas de termes extraits
                    )
                } else {
                    null
                }
            }
            
            // Trier par score décroissant et prendre les top-K
            val topResults = results
                .sortedByDescending { it.relevanceScore }
                .take(topK)
            
            Log.d(TAG, "✅ Found ${topResults.size} similar conversations")
            topResults.forEach { result ->
                Log.d(TAG, "  - Score: ${result.relevanceScore}, ID: ${result.conversation.conversationId}")
            }
            
            return@withContext topResults
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching similar conversations", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Construit le contexte RAG à partir de conversations similaires
     */
    fun buildRAGContext(searchResults: List<ConversationSearchResult>): String {
        if (searchResults.isEmpty()) {
            return ""
        }
        
        val contextBuilder = StringBuilder()
        contextBuilder.appendLine("[CONTEXTE - Conversations similaires de l'historique]")
        contextBuilder.appendLine()
        
        searchResults.forEachIndexed { index, result ->
            val conv = result.conversation
            contextBuilder.appendLine("Conversation ${index + 1} (Pertinence: ${String.format("%.2f", result.relevanceScore)}):")
            contextBuilder.appendLine("Q: ${conv.userMessage}")
            contextBuilder.appendLine("R: ${conv.aiResponse}")
            if (!conv.thinkingTrace.isNullOrEmpty() && conv.thinkingTrace.length < 200) {
                contextBuilder.appendLine("Raisonnement: ${conv.thinkingTrace.take(200)}...")
            }
            contextBuilder.appendLine()
        }
        
        contextBuilder.appendLine("[FIN CONTEXTE]")
        
        return contextBuilder.toString()
    }
}

/**
 * Résultat de recherche sémantique
 */
data class ConversationSearchResult(
    val conversation: ConversationEntity,
    val relevanceScore: Float, // 0.0-1.0
    val matchedTerms: List<String>
)
```

---

## 🔄 Phase 3: Génération automatique d'embeddings

### Fichier à modifier
`ChatAI-Android/app/src/main/java/com/chatai/services/KittAIService.kt`

### Code à ajouter (dans `processUserInput`)
```kotlin
// Après la sauvegarde de la conversation dans Room DB
try {
    val conversation = ConversationEntity(
        conversationId = conversationId,
        userMessage = userInput,
        aiResponse = response,
        thinkingTrace = if (lastThinkingTrace.isNotEmpty()) lastThinkingTrace else null,
        personality = personality,
        apiUsed = apiUsed,
        responseTimeMs = responseTime,
        platform = platform,
        sessionId = sessionId,
        timestamp = endTime
    )
    
    val dbRowId = conversationDao.insert(conversation)
    Log.d(TAG, "✅ [ID: $conversationId] Conversation saved to database (DB row ID: $dbRowId)")
    
    // ⭐ NOUVEAU: Générer embedding automatiquement (en arrière-plan)
    if (embeddingService.isAvailable()) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val embedding = embeddingService.embedConversation(userInput, response)
                if (embedding != null) {
                    val embeddingJson = embeddingService.embeddingToJson(embedding)
                    conversationDao.updateEmbeddings(dbRowId, embeddingJson)
                    Log.d(TAG, "✅ Embedding generated and saved for conversation $conversationId")
                } else {
                    Log.w(TAG, "⚠️ Failed to generate embedding for conversation $conversationId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating embedding for conversation $conversationId", e)
                // Ne pas bloquer la réponse si l'embedding échoue
            }
        }
    } else {
        Log.d(TAG, "Embedding service not available, skipping embedding generation")
    }
    
} catch (dbError: Exception) {
    Log.e(TAG, "Failed to save conversation to database", dbError)
    // Ne pas bloquer la réponse si la BD échoue
}
```

---

## 🌉 Phase 4: Intégration dans BidirectionalBridge

### Fichier à modifier
`ChatAI-Android/app/src/main/java/com/chatai/services/BidirectionalBridge.kt`

### Code à ajouter
```kotlin
// Ajouter dans BidirectionalBridge.kt

import com.chatai.database.ChatAIDatabase
import com.chatai.database.ConversationDao

// Ajouter comme membres de la classe
private var embeddingService: EmbeddingService? = null
private var ragService: RAGService? = null
private val conversationDao: ConversationDao by lazy {
    ChatAIDatabase.getDatabase(context).conversationDao()
}

// Dans init {}
init {
    Log.i(TAG, "🌉 BidirectionalBridge initialized")
    ollamaThinkingService = OllamaThinkingService(context)
    
    // ⭐ NOUVEAU: Initialiser services RAG
    embeddingService = EmbeddingService(context)
    ragService = RAGService(context, conversationDao, embeddingService!!)
    
    // Vérifier disponibilité en arrière-plan
    GlobalScope.launch(Dispatchers.IO) {
        val isAvailable = embeddingService?.isAvailable() ?: false
        Log.i(TAG, "Embedding service available: $isAvailable")
    }
}

// Modifier processWithThinking() pour inclure RAG
suspend fun processWithThinking(
    userInput: String,
    personality: String = "KITT",
    enableThinking: Boolean = true
): Flow<ThinkingChunk> = flow {
    Log.i(TAG, "Processing with thinking mode: enabled=$enableThinking")
    
    // ⭐ FUNCTION CALLING: Vérifier d'abord via KittAIService
    try {
        val kittAIService = KittAIService(context, personality, "web")
        val functionCallResponse = kittAIService.checkFunctionCalling(userInput)
        
        if (functionCallResponse != null && functionCallResponse.isNotEmpty()) {
            Log.i(TAG, "🎯 Function Calling détecté pour: $userInput → Réponse directe")
            
            emit(ThinkingChunk(
                type = ChunkType.RESPONSE,
                content = functionCallResponse,
                isComplete = true
            ))
            return@flow
        }
    } catch (e: Exception) {
        Log.w(TAG, "Function Calling check failed, falling back to Ollama: ${e.message}")
    }
    
    // ⭐ NOUVEAU: RAG - Récupérer contexte pertinent
    var ragContext = ""
    try {
        val embedding = embeddingService?.embed(userInput)
        if (embedding != null) {
            val similarConversations = ragService?.searchSimilarConversations(embedding, topK = 5)
            if (!similarConversations.isNullOrEmpty()) {
                ragContext = ragService?.buildRAGContext(similarConversations) ?: ""
                Log.d(TAG, "✅ RAG context retrieved: ${similarConversations.size} similar conversations")
            } else {
                Log.d(TAG, "No similar conversations found for RAG")
            }
        } else {
            Log.d(TAG, "Failed to generate embedding for RAG, continuing without context")
        }
    } catch (e: Exception) {
        Log.w(TAG, "RAG context retrieval failed, continuing without context: ${e.message}")
        // Ne pas bloquer la réponse si RAG échoue
    }
    
    // Si pas de Function Calling, utiliser Ollama avec thinking
    // ⭐ MODIFIÉ: Passer ragContext à streamWithThinking
    val ollamaFlow = ollamaThinkingService?.streamWithThinking(
        userInput = userInput,
        personality = personality,
        enableThinking = enableThinking,
        ragContext = ragContext  // ⭐ NOUVEAU
    ) ?: throw IllegalStateException("OllamaThinkingService not initialized")
    
    emitAll(ollamaFlow)
}
```

---

## 📡 Phase 5: Modification OllamaThinkingService

### Fichier à modifier
`ChatAI-Android/app/src/main/java/com/chatai/services/OllamaThinkingService.kt`

### Code à modifier
```kotlin
// Modifier la signature de streamWithThinking
fun streamWithThinking(
    userInput: String,
    personality: String = "KITT",
    enableThinking: Boolean = true,
    ragContext: String = ""  // ⭐ NOUVEAU
): Flow<BidirectionalBridge.ThinkingChunk> = flow {
    
    // ... code existant ...
    
    // Construire la requête
    val messages = JSONArray()
    messages.put(JSONObject().apply {
        put("role", "system")
        // ⭐ MODIFIÉ: Inclure ragContext dans le system prompt
        val systemPromptWithContext = if (ragContext.isNotEmpty()) {
            "${getSystemPrompt(personality)}\n\n$ragContext"
        } else {
            getSystemPrompt(personality)
        }
        put("content", systemPromptWithContext)
    })
    messages.put(JSONObject().apply {
        put("role", "user")
        put("content", userInput)
    })
    
    // ... reste du code ...
}
```

---

## 📦 Phase 6: Configuration Webapp

### Fichier à modifier
`ChatAI-Android/app/src/main/assets/webapp/index.html`

### Code à ajouter dans la section "Local"
```html
<label>Modèle d'embedding
    <select id="configEmbeddingModel">
        <option value="nomic-embed-text">Nomic Embed Text (768 dim)</option>
        <option value="mxbai-embed-large">MXBAI Embed Large (1024 dim)</option>
    </select>
    <small style="font-size: 11px; color: #64748b; margin-top: 4px; display: block;">
        Modèle pour générer des embeddings (RAG). Requis pour recherche sémantique.
    </small>
</label>

<label>
    <input type="checkbox" id="configRAGEnabled">
    <span style="margin-left: 8px;">Activer RAG (Recherche sémantique dans l'historique)</span>
</label>
```

---

## 🧪 Phase 7: Tests

### Test 1: Génération d'embedding
```kotlin
// Test dans MainActivity ou Activity de test
val embeddingService = EmbeddingService(context)
val embedding = embeddingService.embed("Quelle heure est-il?")
Log.i("TEST", "Embedding generated: ${embedding?.size} dimensions")
// Attendu: 768 dimensions pour nomic-embed-text
```

### Test 2: Recherche sémantique
```kotlin
// 1. Créer une conversation de test
val testConversation = ConversationEntity(
    userMessage = "Quelle heure à Paris?",
    aiResponse = "Il est 14h30 UTC+1"
)
conversationDao.insert(testConversation)

// 2. Générer embedding
val embedding = embeddingService.embedConversation(
    "Quelle heure à Paris?",
    "Il est 14h30 UTC+1"
)

// 3. Rechercher similar
val queryEmbedding = embeddingService.embed("Quelle heure est-il?")
val results = ragService.searchSimilarConversations(queryEmbedding!!, topK = 5)
Log.i("TEST", "Found ${results.size} similar conversations")
// Attendu: 1 conversation avec score > 0.5
```

### Test 3: Intégration complète
```kotlin
// 1. Poser une question
val userInput = "Quelle heure à Tokyo?"

// 2. Traiter avec RAG
val bridge = BidirectionalBridge.getInstance(context)
bridge.processWithThinking(
    userInput = userInput,
    personality = "KITT",
    enableThinking = true
).collect { chunk ->
    when (chunk.type) {
        ChunkType.THINKING -> Log.d("TEST", "Thinking: ${chunk.content}")
        ChunkType.RESPONSE -> Log.i("TEST", "Response: ${chunk.content}")
    }
}
// Attendu: Réponse incluant contexte de l'historique
```

---

## 🎯 Workflow complet

### 1. Sauvegarde conversation (KittAIService)
```
1. Conversation sauvegardée dans Room DB
2. EmbeddingService.embedConversation() génère embedding
3. Embedding stocké dans embeddingsJson
```

### 2. Nouvelle question (BidirectionalBridge)
```
1. User pose question
2. EmbeddingService.embed(question) génère embedding requête
3. RAGService.searchSimilarConversations() trouve conversations similaires
4. RAGService.buildRAGContext() construit contexte
5. OllamaThinkingService.streamWithThinking(context) envoie à Ollama
6. Ollama répond avec contexte historique!
```

---

## ⚠️ Points d'attention

### 1. Performance
- Embeddings: Calcul coûteux → Faire en arrière-plan
- Vector search: Charger toutes conversations en mémoire → Optimiser avec index (Phase 2)
- Contexte: Limiter à 2000 tokens pour éviter coûts Ollama

### 2. Stockage
- Embeddings JSON: ~3KB par conversation (768 floats)
- 1000 conversations = ~3MB embeddings
- ✅ Acceptable pour Android

### 3. Disponibilité
- Ollama local requis pour embeddings
- Fallback si Ollama non disponible (continuer sans RAG)
- Vérifier disponibilité au démarrage

### 4. Migration
- Anciennes conversations sans embeddings
- Calcul rétroactif (background job optionnel)
- Option: Embeddings à la demande (lazy loading)

---

## 📝 Checklist d'implémentation

- [ ] Créer `EmbeddingService.kt`
- [ ] Créer `RAGService.kt`
- [ ] Ajouter `SimilarityUtils` dans `ConversationDao.kt`
- [ ] Modifier `KittAIService.kt` pour générer embeddings automatiquement
- [ ] Modifier `BidirectionalBridge.kt` pour intégrer RAG
- [ ] Modifier `OllamaThinkingService.kt` pour accepter ragContext
- [ ] Ajouter configuration RAG dans webapp
- [ ] Tests unitaires
- [ ] Tests d'intégration
- [ ] Documentation utilisateur

---

## 🚀 Prochaines étapes

1. **Phase 1-2**: Implémenter EmbeddingService et RAGService (2-3h)
2. **Phase 3-4**: Intégrer dans KittAIService et BidirectionalBridge (2h)
3. **Phase 5-6**: Modifier OllamaThinkingService et configuration (1h)
4. **Phase 7**: Tests complets (1h)

**Total estimé**: 6-7h ✅

---

## 📚 Ressources

- [Ollama Embeddings API](https://github.com/ollama/ollama/blob/main/docs/api.md#generate-embeddings)
- [nomic-embed-text model](https://ollama.com/library/nomic-embed-text)
- [Cosine Similarity](https://en.wikipedia.org/wiki/Cosine_similarity)
- [RAG (Retrieval-Augmented Generation)](https://www.pinecone.io/learn/retrieval-augmented-generation/)


