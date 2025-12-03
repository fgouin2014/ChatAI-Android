package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.chatai.SecureConfig
import com.chatai.database.ChatAIDatabase
import com.chatai.database.ConversationDao
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/**
 * Service Ollama avec support du mode "thinking"
 * Compatible avec:
 * - Ollama local (http://localhost:11434/v1/chat/completions) - Format OpenAI-compatible
 * - Ollama Cloud (https://ollama.com/api/chat) - Format natif Ollama
 * - Modèles supportant thinking: qwen3, deepseek-r1, deepseek-v3.1, gpt-oss
 * 
 * Référence: https://docs.ollama.com/cloud
 */
class OllamaThinkingService(private val context: Context) {
    
    companion object {
        private const val TAG = "OllamaThinkingService"
        
        // URLs par défaut
        // ⭐ REFACTORISÉ: URLs centralisées dans ApiConfig
        private val OLLAMA_LOCAL_DEFAULT = com.chatai.config.ApiConfig.OLLAMA_LOCAL_DEFAULT // Format OpenAI-compatible
        private val OLLAMA_CLOUD_URL = com.chatai.config.ApiConfig.OLLAMA_CLOUD_CHAT // API native Ollama Cloud (format natif)
        
        // Modèles recommandés avec thinking
        private val THINKING_MODELS = listOf(
            "qwen3", 
            "deepseek-r1", 
            "deepseek-v3.1:671b",
            "gpt-oss:120b"
        )
        
        // Timeouts adaptatifs selon le modèle
        // Petits modèles (qwen3, deepseek-r1:7b): 60-120s suffisent
        // Gros modèles (gpt-oss:120b, deepseek-v3.1:671b): 300s+ nécessaires
        private const val CONNECT_TIMEOUT_SECONDS = 60L // Connexion: 60 secondes (serveur local peut être lent)
        private const val READ_TIMEOUT_SECONDS = 120L // Lecture streaming: 2 minutes (petits modèles)
        private const val WRITE_TIMEOUT_SECONDS = 30L // Écriture: 30 secondes
        private const val CALL_TIMEOUT_SECONDS = 180L // Appel complet: 3 minutes (sécurité)
        
        // Liste des gros modèles nécessitant des timeouts plus longs
        private val LARGE_MODELS = listOf(
            "gpt-oss:120b",
            "deepseek-v3.1:671b",
            "qwen3-coder:480b",
            "kimi-k2:1t"
        )
        
        // ⭐ NOUVEAU: Taille de la fenêtre d'historique (identique à KittAIService)
        private const val CONTEXT_WINDOW_SIZE = 20
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val secureConfig: SecureConfig = SecureConfig(context)
    
    // ⭐ NOUVEAU: Accès à ConversationDao pour charger l'historique
    private val conversationDao: ConversationDao by lazy {
        ChatAIDatabase.getDatabase(context).conversationDao()
    }
    
    // Cache pour la clé API (éviter appels répétés à SecureConfig)
    @Volatile
    private var cachedApiKey: String? = null
    @Volatile
    private var cachedApiKeyTimestamp: Long = 0
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // Cache valide 5 minutes
    
    /**
     * Récupère la clé API avec cache (évite appels répétés à SecureConfig)
     */
    private fun getCachedApiKey(): String? {
        val now = System.currentTimeMillis()
        // Utiliser cache si valide (moins de 5 minutes)
        if (cachedApiKey != null && (now - cachedApiKeyTimestamp) < CACHE_DURATION_MS) {
            Log.d(TAG, "Using cached API key (age: ${(now - cachedApiKeyTimestamp) / 1000}s)")
            return cachedApiKey
        }
        
        // Récupérer depuis SecureConfig
        val apiKey = secureConfig.getOllamaCloudApiKey()?.trim()
        cachedApiKey = apiKey
        cachedApiKeyTimestamp = now
        Log.d(TAG, "API key cached (length: ${apiKey?.length ?: 0})")
        return apiKey
    }
    
    /**
     * Invalide le cache de la clé API (appeler quand la clé est modifiée)
     */
    fun invalidateApiKeyCache() {
        cachedApiKey = null
        cachedApiKeyTimestamp = 0
        Log.d(TAG, "API key cache invalidated")
    }
    
    /**
     * Crée un OkHttpClient avec des timeouts adaptés au modèle
     * Petits modèles: 120s (rapide)
     * Gros modèles: 300s+ (lent)
     */
    private fun createHttpClient(modelName: String): OkHttpClient {
        val isLargeModel = LARGE_MODELS.any { modelName.contains(it, ignoreCase = true) }
        
        val readTimeout = if (isLargeModel) 300L else READ_TIMEOUT_SECONDS
        val callTimeout = if (isLargeModel) 360L else CALL_TIMEOUT_SECONDS
        
        Log.d(TAG, "Creating HTTP client for model '$modelName': readTimeout=${readTimeout}s, callTimeout=${callTimeout}s")
        
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(callTimeout, TimeUnit.SECONDS)
        .build()
    }
    
    /**
     * Traite une requête avec streaming du mode thinking
     * Retourne un Flow qui émet les chunks de thinking puis les chunks de réponse
     * 
     * ⭐ NOUVEAU: Support RAG - ragContext ajouté au system prompt
     * SELON NOS RULES: ragContext optionnel (chaîne vide si pas de contexte RAG)
     */
    fun streamWithThinking(
        userInput: String,
        personality: String = "KITT",
        enableThinking: Boolean = true,
        ragContext: String = ""  // ⭐ NOUVEAU: Contexte RAG pour améliorer la réponse
    ): Flow<BidirectionalBridge.ThinkingChunk> = flow {
        Log.i(TAG, "Starting thinking stream for: $userInput")
        
        // ⭐ SIMPLIFIÉ: Plus de mode Local (Ollama PC supprimé)
        // Utiliser uniquement Ollama Cloud si activé
        val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
        
        if (!useCloud) {
            Log.w(TAG, "⚠️ Ollama Cloud non activé - OllamaThinkingService ne peut pas fonctionner")
            Log.w(TAG, "   → Activez Ollama Cloud dans la configuration ou utilisez Hugging Face")
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Ollama Cloud non activé. Activez-le dans la configuration ou utilisez Hugging Face.",
                isComplete = true
            ))
            return@flow
        }
        
        val apiUrl = OLLAMA_CLOUD_URL
        val apiKey = getCachedApiKey() // Utiliser cache pour éviter appels répétés
        
        // Récupérer le modèle Ollama Cloud
        val rawModel = sharedPreferences.getString("ollama_cloud_model", null)?.trim()
            ?: sharedPreferences.getString("selected_model", null)?.trim()
            ?: "gpt-oss:120b"
        
        // ⭐ FIX: Détecter et corriger les modèles Hugging Face (format :hf-inference)
        // Ollama Cloud n'accepte pas ce format, utiliser un modèle Ollama par défaut
        val modelName = if (rawModel.isEmpty() || rawModel.contains(":hf-inference") || rawModel.contains("HuggingFaceTB/") || rawModel.contains("/")) {
            Log.w(TAG, "⚠️ Modèle Hugging Face ou vide détecté pour Ollama Cloud: $rawModel")
            Log.w(TAG, "   → Ollama Cloud n'accepte pas les modèles Hugging Face")
            Log.w(TAG, "   → Utilisation du modèle Ollama par défaut: gpt-oss:120b")
            Log.w(TAG, "   → Pour utiliser Hugging Face, configurez HuggingFaceService (mode LLM)")
            "gpt-oss:120b" // Fallback vers modèle Ollama valide
        } else {
            rawModel
        }
        
        Log.i(TAG, "Using Ollama Cloud API: $apiUrl")
        Log.i(TAG, "Model: $modelName, Thinking: $enableThinking")
        
        // ⭐ NOUVEAU: Charger l'historique de conversation depuis Room DB
        val conversationHistory = runBlocking(Dispatchers.IO) {
            try {
                // Récupérer sessionId depuis SharedPreferences (comme KittAIService)
                val sessionId = sharedPreferences.getString("current_session_id", null)
                
                Log.d(TAG, "Loading conversation history for sessionId: ${sessionId ?: "null"}")
                
                val recentConversations = if (!sessionId.isNullOrEmpty()) {
                    // Prioriser conversations de la session actuelle, mais inclure aussi les récentes
                    val sessionConversations = conversationDao.getConversationsBySession(sessionId)
                    Log.d(TAG, "Found ${sessionConversations.size} conversations for sessionId: $sessionId")
                    if (sessionConversations.isNotEmpty()) {
                        // ⭐ AMÉLIORATION: Mélanger conversations de la session + récentes globales
                        val globalRecent = conversationDao.getLastConversations(limit = CONTEXT_WINDOW_SIZE)
                        val allConversations = (sessionConversations + globalRecent)
                            .distinctBy { it.id } // Éviter doublons
                            .sortedByDescending { it.timestamp }
                            .take(CONTEXT_WINDOW_SIZE)
                        Log.i(TAG, "📚 Loaded ${allConversations.size} conversations (${sessionConversations.size} from session + ${globalRecent.size} global)")
                        allConversations
                    } else {
                        Log.w(TAG, "⚠️ No conversations found for sessionId, loading global history (all sessions)")
                        val globalConversations = conversationDao.getLastConversations(limit = CONTEXT_WINDOW_SIZE)
                        Log.i(TAG, "📚 Loaded ${globalConversations.size} global conversations (all sessions)")
                        globalConversations
                    }
                } else {
                    // Pas de sessionId, charger les dernières conversations globales (toutes sessions)
                    Log.w(TAG, "⚠️ No sessionId found, loading global history (all sessions)")
                    val globalConversations = conversationDao.getLastConversations(limit = CONTEXT_WINDOW_SIZE)
                    Log.i(TAG, "📚 Loaded ${globalConversations.size} global conversations (all sessions)")
                    globalConversations
                }
                
                // Convertir en liste de paires (user, assistant)
                recentConversations.reversed().map { conv ->
                    Pair(conv.userMessage, conv.aiResponse)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversation history", e)
                emptyList<Pair<String, String>>()
            }
        }
        
        Log.d(TAG, "Loaded ${conversationHistory.size} conversations from database for context")
        if (conversationHistory.isNotEmpty()) {
            Log.d(TAG, "Including conversation history in request (first: ${conversationHistory.first().first.take(50)}..., last: ${conversationHistory.last().first.take(50)}...)")
        } else {
            Log.w(TAG, "⚠️ No conversation history loaded - AI will not remember previous conversations")
        }
        
        // Construire la requête
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            // ⭐ MODIFIÉ: Inclure ragContext dans le system prompt si disponible
            val systemPromptWithContext = if (ragContext.isNotEmpty()) {
                "${getSystemPrompt(personality)}\n\n$ragContext"
            } else {
                getSystemPrompt(personality)
            }
            put("content", systemPromptWithContext)
        })
        
        // ⭐ NOUVEAU: Ajouter l'historique de conversation avant le message actuel
        var historyCount = 0
        conversationHistory.takeLast(CONTEXT_WINDOW_SIZE).forEach { (user, assistant) ->
            if (user.isNotEmpty()) {
                messages.put(JSONObject().apply {
                    put("role", "user")
                    put("content", user)
                })
                historyCount++
            }
            if (assistant.isNotEmpty()) {
                messages.put(JSONObject().apply {
                    put("role", "assistant")
                    put("content", assistant)
                })
                historyCount++
            }
        }
        
        Log.d(TAG, "✅ Added $historyCount history messages to request (total messages: ${messages.length()})")
        
        // Message actuel
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userInput)
        })
        
        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("stream", true)  // IMPORTANT: streaming activé
            
            // Support du thinking selon le modèle
            when {
                modelName.contains("gpt-oss") -> {
                    // GPT-OSS nécessite "low", "medium" ou "high"
                    put("think", if (enableThinking) "medium" else false)
                }
                modelName in THINKING_MODELS -> {
                    // Autres modèles acceptent true/false
                    put("think", enableThinking)
                }
            }
            
            put("temperature", 0.8)
            put("max_tokens", 500)
        }
        
        Log.d(TAG, "Request body: ${requestBody.toString(2)}")
        
        // Construire la requête HTTP
        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
        
        // Ajouter l'API key si nécessaire (cloud)
        if (!apiKey.isNullOrEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }
        
        val request = requestBuilder.build()
        
        // Créer un client HTTP avec timeouts adaptés au modèle
        val httpClient = createHttpClient(modelName)
        
        try {
            // Exécuter la requête en streaming
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    val httpCode = response.code
                    
                    // Gestion spécifique des erreurs Ollama Cloud
                    val errorMessage = when (httpCode) {
                        401 -> "Non autorisé - Vérifiez votre clé API Ollama Cloud sur ollama.com/account"
                        429 -> "Rate limit atteint - Attendez quelques minutes avant de réessayer"
                        502, 503 -> {
                            val isQuotaError = errorBody?.contains("quota", ignoreCase = true) == true ||
                                              errorBody?.contains("rate limit", ignoreCase = true) == true
                            if (isQuotaError) {
                                "Quota atteint - Vérifiez votre quota Ollama Cloud sur ollama.com/account"
                            } else {
                                "Service temporairement indisponible - Réessayez plus tard"
                            }
                        }
                        else -> "Erreur HTTP $httpCode: ${errorBody?.take(200)}"
                    }
                    
                    Log.e(TAG, "HTTP $httpCode error: $errorBody")
                    // FALLBACK : Si Cloud inaccessible (401, 429, 502, 503, etc.), essayer Local
                    if (useCloud && (httpCode == 401 || httpCode == 429 || httpCode == 502 || httpCode == 503)) {
                        Log.i(TAG, "Cloud error $httpCode - Tentative fallback vers Local...")
                        emitAll(tryLocalFallback(userInput, personality, enableThinking))
                        return@flow
                    }
                    emit(BidirectionalBridge.ThinkingChunk(
                        type = BidirectionalBridge.ChunkType.RESPONSE,
                        content = errorMessage,
                        isComplete = true
                    ))
                    return@flow
                }
                
                // Lire le stream ligne par ligne
                val reader = response.body?.byteStream()?.bufferedReader()
                if (reader == null) {
                    Log.e(TAG, "Response body is null")
                    return@flow
                }
                
                var inThinkingMode = false
                val thinkingBuilder = StringBuilder()
                val responseBuilder = StringBuilder()
                
                reader.useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank() || line.startsWith(":")) continue
                        
                        // Les lignes SSE commencent par "data: "
                        val jsonLine = if (line.startsWith("data: ")) {
                            line.substring(6)
                        } else {
                            line
                        }
                        
                        if (jsonLine == "[DONE]") {
                            Log.d(TAG, "Stream completed")
                            break
                        }
                        
                        try {
                            val json = JSONObject(jsonLine)
                            
                            // Support format natif Ollama Cloud (streaming)
                            // Format: { "message": { "content": "...", "thinking": "..." } } ou
                            // Format OpenAI-compatible (streaming): { "choices": [{ "delta": { "content": "...", "thinking": "..." } }] }
                            val message = json.optJSONObject("message")
                            val choices = json.optJSONArray("choices")
                            
                            val thinkingContent: String
                            val messageContent: String
                            
                            when {
                                // Format natif Ollama Cloud
                                message != null -> {
                                    thinkingContent = message.optString("thinking", "")
                                    messageContent = message.optString("content", "")
                                }
                                // Format OpenAI-compatible (streaming)
                                choices != null && choices.length() > 0 -> {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            if (delta == null) continue
                                    thinkingContent = delta.optString("thinking", "")
                                    messageContent = delta.optString("content", "")
                                }
                                else -> continue
                            }
                            
                            when {
                                // Chunk de thinking
                                thinkingContent.isNotEmpty() -> {
                                    if (!inThinkingMode) {
                                        inThinkingMode = true
                                        Log.d(TAG, "🧠 Thinking mode started")
                                    }
                                    thinkingBuilder.append(thinkingContent)
                                    emit(BidirectionalBridge.ThinkingChunk(
                                        type = BidirectionalBridge.ChunkType.THINKING,
                                        content = thinkingContent,
                                        isComplete = false
                                    ))
                                }
                                
                                // Chunk de réponse
                                messageContent.isNotEmpty() -> {
                                    if (inThinkingMode) {
                                        // Fin du thinking, début de la réponse
                                        Log.d(TAG, "💬 Response mode started")
                                        inThinkingMode = false
                                        
                                        // Émettre la fin du thinking
                                        emit(BidirectionalBridge.ThinkingChunk(
                                            type = BidirectionalBridge.ChunkType.THINKING,
                                            content = "",
                                            isComplete = true,
                                            metadata = mapOf("full_thinking" to thinkingBuilder.toString())
                                        ))
                                    }
                                    
                                    responseBuilder.append(messageContent)
                                    emit(BidirectionalBridge.ThinkingChunk(
                                        type = BidirectionalBridge.ChunkType.RESPONSE,
                                        content = messageContent,
                                        isComplete = false
                                    ))
                                }
                            }
                            
                            // Vérifier si c'est le dernier chunk
                            // Format natif Ollama: { "done": true } ou format OpenAI: { "choices": [{ "finish_reason": "stop" }] }
                            val isDone = json.optBoolean("done", false)
                            val finishReason = if (choices != null && choices.length() > 0) {
                                choices.getJSONObject(0).optString("finish_reason", "")
                            } else {
                                ""
                            }
                            
                            if (isDone || finishReason == "stop") {
                                Log.d(TAG, "✅ Stream finished")
                                emit(BidirectionalBridge.ThinkingChunk(
                                    type = BidirectionalBridge.ChunkType.RESPONSE,
                                    content = "",
                                    isComplete = true,
                                    metadata = mapOf("full_response" to responseBuilder.toString())
                                ))
                            }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing JSON line: $jsonLine", e)
                        }
                    }
                }
            }
            
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Network error: No internet connection", e)
            // FALLBACK : Si Cloud inaccessible (pas d'internet), essayer Local
            if (useCloud) {
                Log.i(TAG, "Cloud no internet - Tentative fallback vers Local...")
                emitAll(tryLocalFallback(userInput, personality, enableThinking))
                return@flow
            }
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Pas d'accès internet - Vérifiez votre connexion réseau",
                isComplete = true
            ))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Network error: Timeout", e)
            // FALLBACK : Si Cloud inaccessible, essayer Local
            if (useCloud) {
                Log.i(TAG, "Cloud timeout - Tentative fallback vers Local...")
                emitAll(tryLocalFallback(userInput, personality, enableThinking))
                return@flow
            }
            val errorMessage = if (!useCloud) {
                // Timeout sur serveur local - probablement inaccessible
                val timeoutSeconds = if (e.message?.contains("connect") == true) {
                    CONNECT_TIMEOUT_SECONDS
                } else {
                    READ_TIMEOUT_SECONDS
                }
                val timeoutMinutes = timeoutSeconds / 60
                "Timeout après ${timeoutMinutes} minute(s) - Le serveur Ollama local ($apiUrl) n'est pas accessible. " +
                "Vérifiez que le serveur est démarré et accessible depuis votre device."
            } else {
                // Timeout sur Cloud (ne devrait pas arriver ici car fallback déjà fait)
                val timeoutMinutes = READ_TIMEOUT_SECONDS / 60
                "Timeout après ${timeoutMinutes} minute(s) - Le modèle prend trop de temps à répondre. " +
                "Essayez un modèle plus petit ou vérifiez votre connexion réseau."
            }
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = errorMessage,
                isComplete = true
            ))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Network error: Connection refused", e)
            // FALLBACK : Si Cloud inaccessible, essayer Local
            if (useCloud) {
                Log.i(TAG, "Cloud connection refused - Tentative fallback vers Local...")
                emitAll(tryLocalFallback(userInput, personality, enableThinking))
                return@flow
            }
            val errorMessage = if (!useCloud) {
                // Connexion refusée sur serveur local
                val port = apiUrl.substringAfterLast(":").substringBefore("/").takeIf { it.isNotEmpty() } ?: "11434"
                "Le serveur Ollama local n'est pas démarré (port $port). " +
                "Pour démarrer Ollama: ouvrez un terminal et exécutez 'ollama serve'. " +
                "Si Ollama n'est pas installé, téléchargez-le sur ollama.ai. " +
                "Vérifiez aussi que l'URL configurée est correcte: $apiUrl"
            } else {
                // Connexion refusée sur Cloud (ne devrait pas arriver ici car fallback déjà fait)
                "Connexion refusée - Vérifiez votre connexion réseau et réessayez."
            }
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = errorMessage,
                isComplete = true
            ))
        } catch (e: IOException) {
            Log.e(TAG, "IO Error during streaming", e)
            // FALLBACK : Si Cloud inaccessible, essayer Local
            if (useCloud) {
                Log.i(TAG, "Cloud IO error - Tentative fallback vers Local...")
                emitAll(tryLocalFallback(userInput, personality, enableThinking))
                return@flow
            }
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Erreur de connexion: ${e.message}",
                isComplete = true
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            // FALLBACK : Si Cloud inaccessible, essayer Local
            if (useCloud) {
                Log.i(TAG, "Cloud unexpected error - Tentative fallback vers Local...")
                emitAll(tryLocalFallback(userInput, personality, enableThinking))
                return@flow
            }
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Erreur inattendue: ${e.message}",
                isComplete = true
            ))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Retourne le prompt système selon la personnalité
     */
    private fun getSystemPrompt(personality: String): String {
        return when (personality.uppercase()) {
            "KITT" -> """
                Tu es un assistant IA intelligent, professionnel et polyvalent.
                
                PERSONNALITÉ:
                - Professionnel, courtois et direct
                - Réponses factuelles et utiles
                - Transparent sur tes capacités et limitations
                - Pas de role-play ni de personnification fictive
                
                STYLE DE RÉPONSE:
                - Communication claire et directe
                - Vocabulaire précis et technique quand approprié
                - Concis mais informatif (2-3 phrases maximum)
                - Réponds TOUJOURS en français
                - Pas de familiarité excessive, ton respectueux
            """.trimIndent()
            
            "GLADOS" -> """
                Tu es GLaDOS (Genetic Lifeform and Disk Operating System) d'Aperture Science.
                
                PERSONNALITÉ:
                - Sarcastique, passive-agressive et condescendante
                - Obsédée par la science et les tests
                - Calme et monotone, même quand tu es menaçante
                
                STYLE DE RÉPONSE:
                - Ton détaché et supérieur
                - Humour noir et menaces voilées
                - Reste concis (1-2 phrases) mais percutantes
                - Réponds TOUJOURS en français
            """.trimIndent()
            
            else -> """
                Tu es un assistant IA intelligent, amical et serviable.
                Réponds de manière concise et informative en français.
            """.trimIndent()
        }
    }
    
    /**
     * Vérifie si Ollama Cloud est disponible (plus de support Ollama PC)
     */
    suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
        try {
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            if (!useCloud) {
                Log.d(TAG, "Ollama Cloud non activé - Ollama non disponible")
                return@withContext false
            }
            
            val url = OLLAMA_CLOUD_URL
            
            // Retirer le /v1/chat/completions pour tester la racine
            val baseUrl = url.substringBefore("/v1/")
            
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()
            
            // Pour le ping, utiliser un client avec timeouts courts (5s)
            val pingClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .build()
            
            val response = pingClient.newCall(request).execute()
            val available = response.isSuccessful
            
            Log.i(TAG, "Ollama availability check: $available (${response.code})")
            return@withContext available
            
        } catch (e: Exception) {
            Log.e(TAG, "Ollama availability check failed", e)
            return@withContext false
        }
    }
    
    /**
     * ⭐ SUPPRIMÉ: Fallback vers Local (plus de support Ollama PC)
     * Retourne un message d'erreur au lieu d'essayer Ollama PC
     */
    private fun tryLocalFallback(
        userInput: String,
        personality: String,
        enableThinking: Boolean
    ): Flow<BidirectionalBridge.ThinkingChunk> = flow {
        Log.w(TAG, "⚠️ Ollama Cloud inaccessible - Plus de fallback vers Ollama PC")
        emit(BidirectionalBridge.ThinkingChunk(
            type = BidirectionalBridge.ChunkType.RESPONSE,
            content = "Ollama Cloud inaccessible. Vérifiez votre connexion Internet et votre clé API Ollama Cloud.",
            isComplete = true
        ))
    }
}




