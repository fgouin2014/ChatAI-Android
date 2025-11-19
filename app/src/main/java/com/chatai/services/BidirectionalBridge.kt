package com.chatai.services

import android.content.Context
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
    
    init {
        Log.i(TAG, "🌉 BidirectionalBridge initialized")
        ollamaThinkingService = OllamaThinkingService(context)
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
        
        // Si pas de Function Calling, utiliser Ollama avec thinking
        val ollamaFlow = ollamaThinkingService?.streamWithThinking(
            userInput = userInput,
            personality = personality,
            enableThinking = enableThinking
        ) ?: throw IllegalStateException("OllamaThinkingService not initialized")
        
        // Collecter et émettre tous les chunks du flow Ollama
        emitAll(ollamaFlow)
    }
    
    /**
     * Version Java-friendly avec callback
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
                processWithThinking(userInput, personality, enableThinking).collect { chunk ->
                    onChunk.accept(chunk)
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

