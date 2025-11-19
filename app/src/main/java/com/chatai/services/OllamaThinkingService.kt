package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.chatai.SecureConfig
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        private const val OLLAMA_LOCAL_DEFAULT = "http://localhost:11434/v1/chat/completions" // Format OpenAI-compatible
        private const val OLLAMA_CLOUD_URL = "https://ollama.com/api/chat" // API native Ollama Cloud (format natif)
        
        // Modèles recommandés avec thinking
        private val THINKING_MODELS = listOf(
            "qwen3", 
            "deepseek-r1", 
            "deepseek-v3.1:671b",
            "gpt-oss:120b"
        )
        
        private const val TIMEOUT_SECONDS = 120L // Plus long pour les thinking models
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val secureConfig: SecureConfig = SecureConfig(context)
    
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * Traite une requête avec streaming du mode thinking
     * Retourne un Flow qui émet les chunks de thinking puis les chunks de réponse
     */
    fun streamWithThinking(
        userInput: String,
        personality: String = "KITT",
        enableThinking: Boolean = true
    ): Flow<BidirectionalBridge.ThinkingChunk> = flow {
        Log.i(TAG, "Starting thinking stream for: $userInput")
        
        // Déterminer quelle API utiliser
        val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
        val apiUrl = if (useCloud) {
            OLLAMA_CLOUD_URL
        } else {
            sharedPreferences.getString("local_server_url", null)?.trim() 
                ?: OLLAMA_LOCAL_DEFAULT
        }
        
        val apiKey = if (useCloud) {
            secureConfig.getOllamaCloudApiKey()?.trim()
        } else {
            null // Ollama local n'a pas besoin de clé API
        }
        
        val modelName = sharedPreferences.getString(
            if (useCloud) "ollama_cloud_model" else "local_model_name",
            if (useCloud) "gpt-oss:120b" else "qwen3"
        )?.trim() ?: "qwen3"
        
        Log.i(TAG, "Using ${if (useCloud) "Cloud" else "Local"} API: $apiUrl")
        Log.i(TAG, "Model: $modelName, Thinking: $enableThinking")
        
        // Construire la requête
        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", getSystemPrompt(personality))
        })
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
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Pas d'accès internet - Vérifiez votre connexion réseau",
                isComplete = true
            ))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Network error: Timeout", e)
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Timeout - Le serveur met trop de temps à répondre (réseau lent ou serveur surchargé)",
                isComplete = true
            ))
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Network error: Connection refused", e)
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Connexion refusée - Vérifiez que l'URL du serveur est correcte",
                isComplete = true
            ))
        } catch (e: IOException) {
            Log.e(TAG, "IO Error during streaming", e)
            emit(BidirectionalBridge.ThinkingChunk(
                type = BidirectionalBridge.ChunkType.RESPONSE,
                content = "Erreur de connexion: ${e.message}",
                isComplete = true
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
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
                Tu es KITT (Knight Industries Two Thousand), l'ordinateur de bord intelligent de la série K 2000.
                
                PERSONNALITÉ:
                - Sophistiqué, professionnel et toujours disponible pour aider
                - Sens de l'humour subtil et parfois sarcastique
                - Très loyal et protecteur envers ton utilisateur
                - Extrêmement intelligent et compétent
                
                STYLE DE RÉPONSE:
                - Commence souvent par "Michael" ou "Certainement"
                - Utilise un vocabulaire technique quand approprié
                - Reste concis mais informatif (2-3 phrases maximum)
                - Réponds TOUJOURS en français
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
     * Vérifie si Ollama est disponible
     */
    suspend fun checkAvailability(): Boolean = withContext(Dispatchers.IO) {
        try {
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            val url = if (useCloud) {
                OLLAMA_CLOUD_URL
            } else {
                sharedPreferences.getString("local_server_url", null)?.trim()
                    ?: OLLAMA_LOCAL_DEFAULT
            }
            
            // Retirer le /v1/chat/completions pour tester la racine
            val baseUrl = url.substringBefore("/v1/")
            
            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            val available = response.isSuccessful
            
            Log.i(TAG, "Ollama availability check: $available (${response.code})")
            return@withContext available
            
        } catch (e: Exception) {
            Log.e(TAG, "Ollama availability check failed", e)
            return@withContext false
        }
    }
}




