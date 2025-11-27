package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import org.json.JSONObject
import com.chatai.database.ChatAIDatabase
import com.chatai.database.ConversationDao
import com.chatai.database.ConversationEntity
import java.util.UUID

/**
 * Pont de communication bidirectionnelle entre KITT et ChatAI
 * Permet aux deux interfaces de communiquer et de partager des états
 * Support du mode "thinking" pour afficher le raisonnement du modèle
 */
class BidirectionalBridge private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "BidirectionalBridge"
        
        @Volatile
        private var instance: BidirectionalBridge? = null
        
        @JvmStatic
        fun getInstance(context: Context): BidirectionalBridge {
            return instance ?: synchronized(this) {
                instance ?: BidirectionalBridge(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // Flows pour la communication bidirectionnelle
    private val _kittToWebMessages = MutableSharedFlow<BridgeMessage>(replay = 0)
    val kittToWebMessages: SharedFlow<BridgeMessage> = _kittToWebMessages.asSharedFlow()
    
    private val _webToKittMessages = MutableSharedFlow<BridgeMessage>(replay = 0)
    val webToKittMessages: SharedFlow<BridgeMessage> = _webToKittMessages.asSharedFlow()
    
    // Flow spécial pour le mode "thinking"
    private val _thinkingStream = MutableSharedFlow<ThinkingChunk>(replay = 0)
    val thinkingStream: SharedFlow<ThinkingChunk> = _thinkingStream.asSharedFlow()
    
    // État partagé
    private val _sharedState = MutableSharedFlow<SharedState>(replay = 1)
    val sharedState: SharedFlow<SharedState> = _sharedState.asSharedFlow()
    
    // Service Ollama pour le thinking
    private var ollamaThinkingService: OllamaThinkingService? = null
    
    // ⭐ NOUVEAU: Services RAG pour recherche sémantique
    private var embeddingService: EmbeddingService? = null
    private var ragService: RAGService? = null
    private val conversationDao: ConversationDao by lazy {
        ChatAIDatabase.getDatabase(context).conversationDao()
    }
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    }
    private val secureConfig: com.chatai.SecureConfig by lazy {
        com.chatai.SecureConfig(context)
    }
    
    init {
        Log.i(TAG, "🌉 BidirectionalBridge initialized")
        ollamaThinkingService = OllamaThinkingService(context)
        
        // ⭐ NOUVEAU: Initialiser services RAG (selon Nos Rules: non-bloquant)
        try {
            embeddingService = EmbeddingService(context)
            ragService = RAGService(context, conversationDao, embeddingService!!)
            
            // Vérifier disponibilité en arrière-plan
            GlobalScope.launch(Dispatchers.IO) {
                val isAvailable = embeddingService?.isAvailable() ?: false
                val ragEnabled = sharedPreferences.getBoolean("rag_enabled", false)
                val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
                
                if (ragEnabled) {
                    if (isAvailable) {
                        if (useCloud) {
                            // Vérifier si on utilise Hugging Face ou Ollama Cloud
                            val useHuggingFace = sharedPreferences.getBoolean("rag_use_huggingface", true)
                            val hfApiKey = secureConfig.getHuggingFaceApiKey()?.trim()
                            if (useHuggingFace && !hfApiKey.isNullOrEmpty()) {
                                Log.i(TAG, "✅ RAG activé avec Ollama Cloud + Hugging Face embeddings")
                            } else {
                                Log.i(TAG, "✅ RAG activé avec Ollama Cloud - /api/embeddings détecté et fonctionnel")
                            }
                        } else {
                            Log.i(TAG, "✅ RAG activé avec Ollama Local")
                        }
                    } else {
                        if (useCloud) {
                            val useHuggingFace = sharedPreferences.getBoolean("rag_use_huggingface", true)
                            val hfApiKey = secureConfig.getHuggingFaceApiKey()?.trim()
                            if (useHuggingFace && !hfApiKey.isNullOrEmpty()) {
                                Log.w(TAG, "⚠️ RAG activé mais Hugging Face embeddings non disponibles")
                                Log.w(TAG, "   → Vérifiez votre clé API Hugging Face")
                            } else {
                                Log.w(TAG, "❌ RAG activé mais Ollama Cloud ne supporte PAS les embeddings")
                                Log.w(TAG, "   → Ollama Cloud n'a pas d'endpoint /api/embeddings")
                                Log.w(TAG, "   → Solution: Configurez Hugging Face API key pour activer RAG avec Cloud")
                                Log.w(TAG, "   → Alternative: Utilisez Ollama Local pour activer RAG")
                            }
                        } else {
                            Log.w(TAG, "⚠️ RAG activé mais service d'embedding non disponible")
                            Log.w(TAG, "   → Vérifiez que Ollama local est démarré et que le modèle d'embedding est installé")
                        }
                    }
                } else {
                    if (useCloud) {
                        Log.d(TAG, "RAG désactivé dans les paramètres")
                    } else {
                        Log.d(TAG, "RAG désactivé dans les paramètres")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize RAG services, continuing without RAG: ${e.message}")
            // ⭐ SELON NOS RULES: Ne pas bloquer si RAG échoue, continuer sans RAG
        }
    }
    
    /**
     * Envoie un message de KITT vers l'interface Web
     */
    suspend fun sendKittToWeb(message: BridgeMessage) {
        Log.d(TAG, "KITT → Web: ${message.type}")
        _kittToWebMessages.emit(message)
    }
    
    /**
     * Version Java-friendly
     */
    fun sendKittToWebAsync(message: BridgeMessage) {
        GlobalScope.launch(Dispatchers.IO) {
            sendKittToWeb(message)
        }
    }
    
    // ⭐ NOUVEAU : Job pour l'écoute des messages KITT → Web (pour WebAppInterface)
    private var kittToWebListenerJob: Job? = null
    
    /**
     * Écoute les messages KITT → Web avec callback Java-friendly
     * ⭐ NOUVEAU : Permet à WebAppInterface d'écouter les messages de KITT
     */
    fun listenToKittMessages(
        onMessage: java.util.function.Consumer<BridgeMessage>,
        onError: java.util.function.Consumer<Throwable>
    ) {
        // Arrêter l'écoute précédente si elle existe
        kittToWebListenerJob?.cancel()
        
        // Démarrer une nouvelle écoute
        kittToWebListenerJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                kittToWebMessages.collect { message ->
                    try {
                        onMessage.accept(message)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onMessage callback", e)
                        onError.accept(e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to KITT messages", e)
                onError.accept(e)
            }
        }
    }
    
    /**
     * Arrête l'écoute des messages KITT → Web
     */
    fun stopListeningToKittMessages() {
        kittToWebListenerJob?.cancel()
        kittToWebListenerJob = null
    }
    
    /**
     * Envoie un message de l'interface Web vers KITT
     */
    suspend fun sendWebToKitt(message: BridgeMessage) {
        Log.d(TAG, "Web → KITT: ${message.type}")
        _webToKittMessages.emit(message)
    }
    
    /**
     * Version Java-friendly
     */
    fun sendWebToKittAsync(message: BridgeMessage) {
        GlobalScope.launch(Dispatchers.IO) {
            sendWebToKitt(message)
        }
    }
    
    /**
     * Traite une requête utilisateur avec mode thinking
     * Retourne un flow de chunks (thinking + réponse)
     * ⭐ INTÉGRATION FUNCTION CALLING: Vérifie d'abord KittAIService pour Function Calling
     */
    suspend fun processWithThinking(
        userInput: String,
        personality: String = "KITT",
        enableThinking: Boolean = true
    ): Flow<ThinkingChunk> = flow {
        Log.i(TAG, "Processing with thinking mode: enabled=$enableThinking")
        
        // ⭐ FUNCTION CALLING: Vérifier d'abord via KittAIService
        // (détection heure/date, actions système, etc.)
        try {
            // Créer une instance de KittAIService avec la personnalité configurée
            val kittAIService = KittAIService(context, personality, "web")
            val functionCallResponse = kittAIService.checkFunctionCalling(userInput)
            
            // Si Function Calling a été détecté et exécuté, retourner la réponse directement
            if (functionCallResponse != null && functionCallResponse.isNotEmpty()) {
                Log.i(TAG, "Function Calling détecté pour: $userInput → Réponse directe")
                
                // Émettre la réponse comme un chunk unique (type RESPONSE)
                emit(ThinkingChunk(
                    type = ChunkType.RESPONSE,
                    content = functionCallResponse,
                    isComplete = true
                ))
                return@flow
            }
        } catch (e: Exception) {
            Log.w(TAG, "Function Calling check failed, falling back to Ollama: ${e.message}")
            // Continuer avec Ollama si Function Calling échoue
        }
        
        // ⭐ NOUVEAU: RAG - Récupérer contexte pertinent (selon Nos Rules: non-bloquant)
        var ragContext = ""
        try {
            // Vérifier si RAG est activé
            val ragEnabled = sharedPreferences.getBoolean("rag_enabled", true)
            
            if (ragEnabled && embeddingService != null && ragService != null) {
                // Générer l'embedding de la requête utilisateur
                val queryEmbedding = embeddingService?.embed(userInput)
                
                // ⭐ MODE OFFLINE: Passer queryText pour fallback textuel si embedding échoue
                val similarConversations = ragService?.searchSimilarConversations(
                    queryEmbedding = queryEmbedding,
                    queryText = userInput, // ⭐ Fallback offline: recherche textuelle si embedding null
                    topK = 5
                )
                
                if (!similarConversations.isNullOrEmpty()) {
                    // Construire le contexte RAG
                    ragContext = ragService?.buildRAGContext(similarConversations) ?: ""
                    if (queryEmbedding != null) {
                        Log.d(TAG, "✅ RAG context retrieved (semantic): ${similarConversations.size} similar conversations")
                    } else {
                        Log.d(TAG, "✅ RAG context retrieved (text-based, offline): ${similarConversations.size} similar conversations")
                    }
                } else {
                    if (queryEmbedding != null) {
                        Log.d(TAG, "No similar conversations found for RAG (semantic search)")
                    } else {
                        Log.d(TAG, "No similar conversations found for RAG (text-based search)")
                    }
                }
            } else {
                Log.d(TAG, "RAG disabled or services not available, continuing without context")
            }
        } catch (e: Exception) {
            Log.w(TAG, "RAG context retrieval failed, continuing without context: ${e.message}")
            // ⭐ SELON NOS RULES: Ne pas bloquer si RAG échoue, continuer sans contexte
        }
        
        // Si pas de Function Calling, utiliser Ollama avec thinking
        // ⭐ MODIFIÉ: Passer ragContext à OllamaThinkingService
        val ollamaFlow = ollamaThinkingService?.streamWithThinking(
            userInput = userInput,
            personality = personality,
            enableThinking = enableThinking,
            ragContext = ragContext  // ⭐ NOUVEAU: Contexte RAG pour améliorer la réponse
        ) ?: throw IllegalStateException("OllamaThinkingService not initialized")
        
        // Collecter et émettre tous les chunks du flow Ollama
        emitAll(ollamaFlow)
    }
    
    /**
     * Version Java-friendly avec callback
     * ⭐ NOUVEAU: Sauvegarde automatique des conversations après traitement
     */
    fun processWithThinkingAsync(
        userInput: String,
        personality: String,
        enableThinking: Boolean,
        onChunk: java.util.function.Consumer<ThinkingChunk>,
        onError: java.util.function.Consumer<Throwable>,
        onComplete: Runnable
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                var fullResponse = StringBuilder()
                var fullThinking = StringBuilder()
                var startTime = System.currentTimeMillis()
                
                processWithThinking(userInput, personality, enableThinking).collect { chunk ->
                    // Collecter le contenu pour sauvegarde
                    when (chunk.type) {
                        ChunkType.THINKING -> {
                            if (chunk.content.isNotEmpty()) {
                                fullThinking.append(chunk.content)
                            }
                        }
                        ChunkType.RESPONSE -> {
                            if (chunk.content.isNotEmpty()) {
                                fullResponse.append(chunk.content)
                            }
                        }
                    }
                    
                    // Extraire la réponse complète depuis metadata si disponible
                    if (chunk.isComplete && chunk.metadata.containsKey("full_response")) {
                        val metadataResponse = chunk.metadata["full_response"] as? String
                        if (metadataResponse != null && metadataResponse.isNotEmpty()) {
                            fullResponse.clear()
                            fullResponse.append(metadataResponse)
                        }
                    }
                    
                    // Extraire le thinking complet depuis metadata si disponible
                    if (chunk.isComplete && chunk.metadata.containsKey("full_thinking")) {
                        val metadataThinking = chunk.metadata["full_thinking"] as? String
                        if (metadataThinking != null && metadataThinking.isNotEmpty()) {
                            fullThinking.clear()
                            fullThinking.append(metadataThinking)
                        }
                    }
                    
                    onChunk.accept(chunk)
                }
                
                // ⭐ NOUVEAU: Sauvegarder la conversation après traitement complet
                val responseText = fullResponse.toString()
                if (responseText.isNotEmpty()) {
                    try {
                        val endTime = System.currentTimeMillis()
                        val responseTime = endTime - startTime
                        
                        // Déterminer la plateforme (webapp ou KITT)
                        val platform = if (personality == "KITT") "vocal" else "webapp"
                        
                        // Récupérer le sessionId actuel
                        val sessionId = sharedPreferences.getString("current_session_id", null)
                        
                        // Déterminer l'API utilisée (local ou cloud)
                        val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
                        val apiUsed = if (useCloud) "ollama_cloud" else "ollama_local"
                        
                        val conversation = com.chatai.database.ConversationEntity(
                            conversationId = java.util.UUID.randomUUID().toString(),
                            userMessage = userInput,
                            aiResponse = responseText,
                            thinkingTrace = if (fullThinking.isNotEmpty()) fullThinking.toString() else null,
                            personality = personality,
                            apiUsed = apiUsed,
                            responseTimeMs = responseTime,
                            platform = platform,
                            sessionId = sessionId,
                            timestamp = endTime
                        )
                        
                        val dbRowId = conversationDao.insert(conversation)
                        Log.i(TAG, "✅ Conversation saved (platform=$platform, personality=$personality, DB row ID: $dbRowId)")
                        
                        // ⭐ NOUVEAU: Générer embedding automatiquement (en arrière-plan, non-bloquant)
                        if (sharedPreferences.getBoolean("rag_enabled", true)) {
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    if (embeddingService?.isAvailable() == true) {
                                        val embedding = embeddingService?.embedConversation(userInput, responseText)
                                        if (embedding != null) {
                                            val embeddingJson = embeddingService?.embeddingToJson(embedding)
                                            if (embeddingJson != null) {
                                                conversationDao.updateEmbeddings(dbRowId, embeddingJson)
                                                Log.d(TAG, "✅ Embedding generated and saved (${embedding.size} dimensions)")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to generate embedding (non-blocking): ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save conversation to database", e)
                        // Ne pas bloquer si la sauvegarde échoue
                    }
                }
                
                onComplete.run()
            } catch (e: Exception) {
                Log.e(TAG, "Error in processWithThinkingAsync", e)
                onError.accept(e)
            }
        }
    }
    
    /**
     * Met à jour l'état partagé entre les interfaces
     */
    suspend fun updateSharedState(state: SharedState) {
        Log.d(TAG, "Shared state updated: $state")
        _sharedState.emit(state)
    }
    
    /**
     * Émet un chunk de thinking dans le stream
     */
    suspend fun emitThinkingChunk(chunk: ThinkingChunk) {
        _thinkingStream.emit(chunk)
    }
    
    /**
     * Types de messages bidirectionnels
     */
    data class BridgeMessage(
        val type: MessageType,
        val source: Source,
        val content: String,
        val metadata: Map<String, Any> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    enum class MessageType {
        USER_INPUT,           // Entrée utilisateur
        AI_RESPONSE,          // Réponse IA
        SYSTEM_STATUS,        // Statut système (KITT activé, etc.)
        COMMAND,              // Commande (scanner, turbo, etc.)
        NOTIFICATION,         // Notification
        THINKING_START,       // Début du thinking
        THINKING_CHUNK,       // Chunk de thinking
        THINKING_END,         // Fin du thinking
        RESPONSE_START,       // Début de la réponse
        RESPONSE_CHUNK,       // Chunk de réponse
        RESPONSE_END,         // Fin de la réponse
        ERROR,                // Erreur
        STATE_SYNC            // Synchronisation d'état
    }
    
    enum class Source {
        KITT_VOICE,           // Interface vocale KITT
        KITT_WEB,             // Interface web KITT
        CHATAI_WEB,           // Interface web ChatAI
        SYSTEM                // Système
    }
    
    /**
     * Chunk de thinking (peut être thinking ou réponse)
     */
    data class ThinkingChunk(
        val type: ChunkType,
        val content: String,
        val isComplete: Boolean = false,
        val metadata: Map<String, Any> = emptyMap()
    )
    
    enum class ChunkType {
        THINKING,             // Contenu du raisonnement
        RESPONSE              // Contenu de la réponse finale
    }
    
    /**
     * État partagé entre les interfaces
     */
    data class SharedState(
        val isKittActive: Boolean = false,
        val isScannerActive: Boolean = false,
        val currentPersonality: String = "KITT",
        val isThinkingMode: Boolean = false,
        val currentConversationId: String? = null,
        val metadata: Map<String, Any> = emptyMap()
    )
}

