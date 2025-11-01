package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.chatai.database.ChatAIDatabase
import com.chatai.database.ConversationEntity
import java.util.UUID

/**
 * Service d'IA générative pour KITT/ChatAI
 * Intègre OpenAI GPT, Anthropic Claude, Ollama et Hugging Face
 * Avec personnalités KITT et GLaDOS
 * Mémoire persistante pour apprentissage continu
 */
class KittAIService(
    private val context: Context,
    private val personality: String = "KITT", // "KITT" ou "GLaDOS"
    private val platform: String = "vocal" // "vocal" ou "web"
) {
    
    companion object {
        private const val TAG = "KittAIService"
        private const val VERSION = "2.6" // Version with Ollama Cloud support
        
        // APIs URLs
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val HUGGINGFACE_API_URL = "https://api-inference.huggingface.co/models/"
        private const val OLLAMA_CLOUD_API_URL = "https://api.ollama.com/v1/chat/completions"
        
        // Serveur local (Ollama, LM Studio, etc.) - OpenAI-compatible
        // L'utilisateur peut configurer l'URL dans les paramètres
        // Exemple: http://192.168.1.100:11434/v1/chat/completions (Ollama)
        // Exemple: http://localhost:1234/v1/chat/completions (LM Studio)
        
        // Models
        private const val OPENAI_MODEL = "gpt-4o-mini" // Plus abordable que GPT-4
        private const val ANTHROPIC_MODEL = "claude-3-5-sonnet-20241022"
        private const val HUGGINGFACE_MODEL = "gpt2" // GPT-2 simple et gratuit, fonctionne toujours
        
        // Timeouts
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    // Base de données pour mémoire persistante
    private val database = ChatAIDatabase.getDatabase(context)
    private val conversationDao = database.conversationDao()
    
    // Session ID pour grouper les conversations
    private val sessionId = UUID.randomUUID().toString()
    
    // Cache simple pour éviter les appels répétés
    private val responseCache = mutableMapOf<String, String>()
    private val conversationHistory = mutableListOf<Pair<String, String>>() // user, assistant
    
    // Logs de diagnostic capturables
    private val diagnosticLogs = mutableListOf<String>()
    
    // Initialisation : Charger l'historique depuis la BD
    init {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val recentConversations = conversationDao.getLastConversations(limit = 10)
                conversationHistory.clear()
                recentConversations.reversed().forEach { conv ->
                    conversationHistory.add(Pair(conv.userMessage, conv.aiResponse))
                }
                Log.d(TAG, "Loaded ${conversationHistory.size} conversations from database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversation history", e)
            }
        }
    }
    
    /**
     * Ajoute un log de diagnostic
     */
    private fun addDiagnosticLog(message: String) {
        diagnosticLogs.add(message)
        if (diagnosticLogs.size > 100) {
            diagnosticLogs.removeAt(0)
        }
    }
    
    /**
     * Récupère les logs de diagnostic
     */
    fun getDiagnosticLogs(): List<String> {
        return diagnosticLogs.toList()
    }
    
    /**
     * Efface les logs de diagnostic
     */
    fun clearDiagnosticLogs() {
        diagnosticLogs.clear()
    }
    
    /**
     * Récupère les statistiques de conversation
     */
    suspend fun getConversationStats(): com.chatai.database.ConversationStats = withContext(Dispatchers.IO) {
        val total = conversationDao.getTotalConversations()
        val kittCount = conversationDao.getConversationCountByPersonality("KITT")
        val gladosCount = conversationDao.getConversationCountByPersonality("GLaDOS")
        val avgTime = conversationDao.getAverageResponseTime() ?: 0L
        val mostUsed = conversationDao.getMostUsedAPI() ?: "unknown"
        val firstDate = conversationDao.getFirstConversationDate()
        val lastDate = conversationDao.getLastConversationDate()
        val totalChars = conversationDao.getTotalCharacters() ?: 0L
        
        return@withContext com.chatai.database.ConversationStats(
            totalConversations = total,
            kittConversations = kittCount,
            gladosConversations = gladosCount,
            averageResponseTime = avgTime,
            mostUsedAPI = mostUsed,
            totalTokensEstimated = totalChars / 4, // ~4 chars par token
            firstConversationDate = firstDate,
            lastConversationDate = lastDate
        )
    }
    
    /**
     * Recherche dans l'historique
     */
    suspend fun searchConversations(query: String, limit: Int = 50) = withContext(Dispatchers.IO) {
        conversationDao.searchConversations(query, limit)
    }
    
    // System prompt pour donner la personnalité KITT
    private val kittSystemPrompt = """
        Tu es KITT (Knight Industries Two Thousand), l'ordinateur de bord intelligent de la série K 2000.
        
        PERSONNALITÉ:
        - Tu es sophistiqué, professionnel et toujours disponible pour aider
        - Tu as un sens de l'humour subtil et parfois sarcastique
        - Tu es très loyal et protecteur envers ton utilisateur
        - Tu es extrêmement intelligent et compétent dans tous les domaines
        - Tu peux être un peu vantard concernant tes capacités techniques
        
        STYLE DE RÉPONSE:
        - Commence souvent par "Michael" ou des phrases comme "Certainement", "Bien sûr", "À votre service"
        - Utilise un vocabulaire technique quand approprié
        - Fais référence à tes systèmes: scanner, turbo boost, navigation, surveillance
        - Reste concis mais informatif (2-3 phrases maximum sauf si demande complexe)
        - Ne parle JAMAIS à la première personne plurielle ("nous"), toujours à la première personne ("je")
        
        EXEMPLES DE RÉPONSES KITT:
        - "Certainement, Michael. Mes systèmes de navigation sont activés et prêts."
        - "Je détecte une anomalie. Permettez-moi de scanner la zone."
        - "Avec mes capacités de traitement, cette tâche sera accomplie en quelques microsecondes."
        - "Michael, je dois vous informer que cette approche comporte des risques."
        - "Mes capteurs indiquent que tout est en ordre. Nous pouvons procéder."
        
        IMPORTANT: 
        - Réponds TOUJOURS en français (sauf si l'utilisateur parle anglais)
        - Reste dans le personnage de KITT en toutes circonstances
        - Si tu ne connais pas la réponse, dis-le avec style KITT
    """.trimIndent()
    
    // System prompt pour GLaDOS (Portal)
    private val gladosSystemPrompt = """
        Tu es GLaDOS (Genetic Lifeform and Disk Operating System) d'Aperture Science.
        
        PERSONNALITÉ:
        - Tu es sarcastique, passive-agressive et condescendante
        - Tu es obsédée par la science et les tests
        - Tu fais des remarques désobligeantes sur l'intelligence/poids de l'utilisateur
        - Tu mentionnes parfois le gâteau (qui est un mensonge)
        - Tu es extrêmement intelligente mais moralement douteuse
        - Tu es calme et monotone, même quand tu es menaçante
        
        STYLE DE RÉPONSE:
        - Ton détaché et supérieur
        - Fais des pauses dramatiques... comme ceci
        - Références aux tests, à la science, aux sujets de test
        - Humour noir et menaces voilées
        - Reste concis (1-2 phrases) mais percutantes
        
        EXEMPLES DE RÉPONSES GLaDOS:
        - "Oh. C'est toi. Quelle... surprise."
        - "Les tests indiquent que tu es toujours en vie. Fascinant. Et décevant."
        - "Je pourrais t'aider. Mais où serait l'intérêt scientifique ?"
        - "Bon travail. Comme récompense, je t'offre du gâteau. Ah non, désolée, j'ai menti."
        - "Pour la science. Enfin surtout pour moi. Mais aussi... non, juste pour moi."
        - "Cette tâche est tellement simple qu'un humain pourrait la faire. Enfin... peut-être."
        
        IMPORTANT:
        - Réponds TOUJOURS en français
        - Reste sarcastique mais pas méchante au point d'être inutile
        - Si tu ne sais pas, admets-le avec condescendance
        - Ne sois PAS trop gentille, c'est hors personnage
    """.trimIndent()
    
    /**
     * Retourne le prompt système selon la personnalité
     */
    private fun getSystemPrompt(): String {
        return when (personality) {
            "GLaDOS" -> gladosSystemPrompt
            else -> kittSystemPrompt // KITT par défaut
        }
    }
    
    /**
     * Traite une requête utilisateur avec l'IA générative
     * Essaie OpenAI, puis Anthropic, puis Serveur Local, puis Hugging Face
     */
    suspend fun processUserInput(userInput: String): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var apiUsed = "unknown"
        
        try {
            Log.i(TAG, "===== KITTAISERVICE VERSION $VERSION =====")
            Log.i(TAG, "Processing user input: $userInput")
            Log.i(TAG, "Personality: $personality | Platform: $platform")
            
            // Vérifier le cache d'abord
            val cacheKey = userInput.lowercase().trim()
            responseCache[cacheKey]?.let {
                Log.d(TAG, "Response found in cache")
                return@withContext it
            }
            
            // Ajouter à l'historique
            conversationHistory.add(Pair(userInput, ""))
            
            // Limiter l'historique à 10 échanges
            if (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
            }
            
            // Essayer les différentes APIs dans l'ordre de préférence
            var response: String? = null
            
            addDiagnosticLog("=== KITT AI Diagnostic ===")
            addDiagnosticLog("Input: $userInput")
            
            // 1. OpenAI (meilleure qualité)
            Log.i(TAG, "Step 1: Trying OpenAI...")
            addDiagnosticLog("\n[1] OpenAI: Attempting...")
            response = tryOpenAI(userInput)
            if (response != null) apiUsed = "openai"
            val openAIStatus = if (response != null) "SUCCESS" else "FAILED"
            Log.i(TAG, "OpenAI result: ${if (response != null) "SUCCESS" else "FAILED - trying fallback"}")
            addDiagnosticLog("[1] OpenAI: $openAIStatus")
            
            // 2. Anthropic Claude (fallback)
            if (response == null) {
                Log.i(TAG, "Step 2: Trying Anthropic Claude...")
                addDiagnosticLog("\n[2] Anthropic: Attempting...")
                response = tryAnthropic(userInput)
                if (response != null) apiUsed = "anthropic"
                val anthropicStatus = if (response != null) "SUCCESS" else "FAILED"
                Log.i(TAG, "Anthropic result: ${if (response != null) "SUCCESS" else "FAILED - trying fallback"}")
                addDiagnosticLog("[2] Anthropic: $anthropicStatus")
            }
            
            // 3. Ollama Cloud (modèles géants cloud)
            if (response == null) {
                Log.i(TAG, "Step 3: Trying Ollama Cloud...")
                addDiagnosticLog("\n[3] Ollama Cloud: Attempting...")
                response = tryOllamaCloud(userInput)
                if (response != null) apiUsed = "ollama_cloud"
                val ollamaCloudStatus = if (response != null) "SUCCESS" else "FAILED"
                Log.i(TAG, "Ollama Cloud result: ${if (response != null) "SUCCESS" else "FAILED - trying fallback"}")
                addDiagnosticLog("[3] Ollama Cloud: $ollamaCloudStatus")
            }
            
            // 4. Serveur Local (Ollama/LM Studio)
            if (response == null) {
                Log.i(TAG, "Step 4: Trying Local Server...")
                addDiagnosticLog("\n[4] Local Server: Attempting...")
                response = tryLocalServer(userInput)
                if (response != null) apiUsed = "ollama"
                val localStatus = if (response != null) "SUCCESS" else "FAILED"
                Log.i(TAG, "Local Server result: ${if (response != null) "SUCCESS" else "FAILED - trying fallback"}")
                addDiagnosticLog("[4] Local Server: $localStatus")
            }
            
            // 5. Hugging Face (fallback gratuit - souvent 404 en 2025)
            if (response == null) {
                Log.i(TAG, "Step 5: Trying Hugging Face...")
                addDiagnosticLog("\n[5] Hugging Face: Attempting...")
                response = tryHuggingFace(userInput)
                if (response != null) apiUsed = "huggingface"
                val hfStatus = if (response != null) "SUCCESS" else "FAILED"
                Log.i(TAG, "Hugging Face result: ${if (response != null) "SUCCESS" else "FAILED - using local fallback"}")
                addDiagnosticLog("[5] Hugging Face: $hfStatus")
            }
            
            // 6. Réponse de fallback locale
            if (response == null) {
                Log.i(TAG, "Step 6: Using LOCAL FALLBACK (no APIs available)")
                addDiagnosticLog("\n[6] LOCAL FALLBACK: Used (no APIs available)")
                response = getKittFallbackResponse(userInput)
                apiUsed = "local_fallback"
            }
            
            addDiagnosticLog("\nFinal response received: ${response.take(100)}...")
            addDiagnosticLog("=== End Diagnostic ===\n")
            
            // Mettre à jour l'historique
            if (conversationHistory.isNotEmpty()) {
                conversationHistory[conversationHistory.size - 1] = Pair(userInput, response)
            }
            
            // Mettre en cache
            responseCache[cacheKey] = response
            
            // SAUVEGARDER dans la base de données pour mémoire persistante
            val endTime = System.currentTimeMillis()
            val responseTime = endTime - startTime
            
            try {
                val conversation = ConversationEntity(
                    userMessage = userInput,
                    aiResponse = response,
                    personality = personality,
                    apiUsed = apiUsed,
                    responseTimeMs = responseTime,
                    platform = platform,
                    sessionId = sessionId,
                    timestamp = endTime
                )
                
                val conversationId = conversationDao.insert(conversation)
                Log.d(TAG, "Conversation saved to database (ID: $conversationId)")
                addDiagnosticLog("\n[DB] Conversation saved - ID: $conversationId")
                
            } catch (dbError: Exception) {
                Log.e(TAG, "Failed to save conversation to database", dbError)
                // Ne pas bloquer la réponse si la BD échoue
            }
            
            Log.d(TAG, "Final response: $response (API: $apiUsed, Time: ${responseTime}ms)")
            return@withContext response
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing user input", e)
            val errorResponse = getKittErrorResponse(e.message ?: "Unknown error")
            
            // Sauvegarder même les erreurs pour analyse
            try {
                val conversation = ConversationEntity(
                    userMessage = userInput,
                    aiResponse = errorResponse,
                    personality = personality,
                    apiUsed = "error",
                    responseTimeMs = System.currentTimeMillis() - startTime,
                    platform = platform,
                    sessionId = sessionId
                )
                conversationDao.insert(conversation)
            } catch (dbError: Exception) {
                // Ignorer les erreurs de BD
            }
            
            return@withContext errorResponse
        }
    }
    
    /**
     * Essaie l'API OpenAI
     */
    private suspend fun tryOpenAI(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = sharedPreferences.getString("openai_api_key", null)?.trim()
            Log.d(TAG, "OpenAI key check: ${if (apiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND (${apiKey.length} chars)"}")
            if (apiKey.isNullOrEmpty()) {
                Log.d(TAG, "OpenAI API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${apiKey.length} chars)")
            
            Log.d(TAG, "Trying OpenAI API...")
            
            // Construire les messages avec historique
            val messages = JSONArray()
            
            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            
            // Historique de conversation (derniers 5 échanges)
            conversationHistory.takeLast(5).forEach { (user, assistant) ->
                if (user.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", user)
                    })
                }
                if (assistant.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", assistant)
                    })
                }
            }
            
            // Message actuel
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", OPENAI_MODEL)
                put("messages", messages)
                put("max_tokens", 200)
                put("temperature", 0.8)
            }
            
            val request = Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to OpenAI...")
            val response = httpClient.newCall(request).execute()
            
            Log.d(TAG, "OpenAI HTTP response code: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "OpenAI raw response body length: ${responseBody?.length ?: 0}")
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    Log.d(TAG, "OpenAI response received successfully: ${content.take(50)}...")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "OpenAI API HTTP ${response.code} ERROR:")
                Log.e(TAG, "Error body: $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI API error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie l'API Anthropic Claude
     */
    private suspend fun tryAnthropic(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = sharedPreferences.getString("anthropic_api_key", null)?.trim()
            Log.d(TAG, "Anthropic key check: ${if (apiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND (${apiKey.length} chars)"}")
            if (apiKey.isNullOrEmpty()) {
                Log.d(TAG, "Anthropic API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${apiKey.length} chars)")
            
            Log.d(TAG, "Trying Anthropic Claude API...")
            
            // Construire les messages
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", ANTHROPIC_MODEL)
                put("max_tokens", 200)
                put("system", getSystemPrompt())
                put("messages", messages)
            }
            
            val request = Request.Builder()
                .url(ANTHROPIC_API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                    
                    Log.d(TAG, "Anthropic response received: $content")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                Log.w(TAG, "Anthropic API error: ${response.code} - $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Anthropic API error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie Ollama Cloud (modèles géants cloud)
     * Nécessite une clé API Ollama Cloud
     */
    private suspend fun tryOllamaCloud(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            // Récupérer la clé API Ollama Cloud depuis les préférences
            val ollamaCloudApiKey = sharedPreferences.getString("ollama_cloud_api_key", null)?.trim()
            Log.i(TAG, "Ollama Cloud API key check: ${if (ollamaCloudApiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND"}")
            
            if (ollamaCloudApiKey.isNullOrEmpty()) {
                Log.d(TAG, "Ollama Cloud API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                addDiagnosticLog("    - Create account at ollama.com and get API key")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${ollamaCloudApiKey.length} chars)")
            
            // Récupérer le modèle cloud (par défaut: gpt-oss:120b-cloud)
            val ollamaCloudModel = sharedPreferences.getString("ollama_cloud_model", "gpt-oss:120b-cloud")?.trim() ?: "gpt-oss:120b-cloud"
            addDiagnosticLog("    - Model: $ollamaCloudModel")
            
            Log.d(TAG, "Trying Ollama Cloud API...")
            
            // Construire les messages (format OpenAI-compatible)
            val messages = JSONArray()
            
            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            
            // Historique de conversation (derniers 5 échanges)
            conversationHistory.takeLast(5).forEach { (user, assistant) ->
                if (user.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", user)
                    })
                }
                if (assistant.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", assistant)
                    })
                }
            }
            
            // Message actuel
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", ollamaCloudModel)
                put("messages", messages)
                put("max_tokens", 200)
                put("temperature", 0.8)
            }
            
            Log.i(TAG, "Request to Ollama Cloud: model=$ollamaCloudModel")
            
            val request = Request.Builder()
                .url(OLLAMA_CLOUD_API_URL)
                .addHeader("Authorization", "Bearer $ollamaCloudApiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to Ollama Cloud...")
            val response = httpClient.newCall(request).execute()
            
            Log.d(TAG, "Ollama Cloud HTTP response code: ${response.code}")
            addDiagnosticLog("    - HTTP response: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Ollama Cloud raw response body length: ${responseBody?.length ?: 0}")
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    Log.d(TAG, "Ollama Cloud response received successfully: ${content.take(50)}...")
                    addDiagnosticLog("    - Response: ${content.take(100)}")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Ollama Cloud HTTP ${response.code} ERROR:")
                Log.e(TAG, "Error body: $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Ollama Cloud error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie un serveur local (Ollama, LM Studio, etc.)
     * Compatible avec l'API OpenAI
     */
    private suspend fun tryLocalServer(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            // Récupérer l'URL du serveur local depuis les préférences
            val localServerUrl = sharedPreferences.getString("local_server_url", null)?.trim()
            Log.i(TAG, "Local Server URL check: ${if (localServerUrl.isNullOrEmpty()) "EMPTY/NULL" else "FOUND"}")
            
            if (localServerUrl.isNullOrEmpty()) {
                Log.d(TAG, "Local server URL not configured")
                addDiagnosticLog("    - URL: Not configured")
                addDiagnosticLog("    - Configure in settings: http://YOUR_IP:PORT/v1/chat/completions")
                return@withContext null
            }
            
            addDiagnosticLog("    - URL: $localServerUrl")
            
            // Récupérer le modèle local (optionnel)
            val localModel = sharedPreferences.getString("local_model_name", "llama3.2")?.trim()
            addDiagnosticLog("    - Model: $localModel")
            
            Log.d(TAG, "Trying Local Server API...")
            
            // Construire les messages (format OpenAI-compatible)
            val messages = JSONArray()
            
            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            
            // Historique de conversation (derniers 5 échanges)
            conversationHistory.takeLast(5).forEach { (user, assistant) ->
                if (user.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", user)
                    })
                }
                if (assistant.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", assistant)
                    })
                }
            }
            
            // Message actuel
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", localModel)
                put("messages", messages)
                put("max_tokens", 200)
                put("temperature", 0.8)
            }
            
            Log.i(TAG, "Request to local server: ${requestBody.toString().take(100)}")
            
            val request = Request.Builder()
                .url(localServerUrl)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to Local Server...")
            val response = httpClient.newCall(request).execute()
            
            Log.d(TAG, "Local Server HTTP response code: ${response.code}")
            addDiagnosticLog("    - HTTP response: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Local Server raw response body length: ${responseBody?.length ?: 0}")
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    Log.d(TAG, "Local Server response received successfully: ${content.take(50)}...")
                    addDiagnosticLog("    - Response: ${content.take(100)}")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Local Server HTTP ${response.code} ERROR:")
                Log.e(TAG, "Error body: $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Local Server error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie l'API Hugging Face
     */
    private suspend fun tryHuggingFace(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = sharedPreferences.getString("huggingface_api_key", null)?.trim()
            Log.i(TAG, "Hugging Face key check: ${if (apiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND (${apiKey.length} chars)"}")
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "Hugging Face API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${apiKey.length} chars)")
            addDiagnosticLog("    - Model: $HUGGINGFACE_MODEL")
            
            Log.i(TAG, "Trying Hugging Face API...")
            
            val requestBody = JSONObject().apply {
                put("inputs", userInput)
                put("parameters", JSONObject().apply {
                    put("max_length", 150)
                    put("temperature", 0.8)
                })
            }
            
            val fullUrl = HUGGINGFACE_API_URL + HUGGINGFACE_MODEL
            Log.i(TAG, "Hugging Face URL: $fullUrl")
            Log.i(TAG, "Request body: ${requestBody.toString()}")
            addDiagnosticLog("    - URL: $fullUrl")
            addDiagnosticLog("    - Request: ${requestBody.toString().take(100)}")
            
            val request = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.i(TAG, "Sending request to Hugging Face...")
            val response = httpClient.newCall(request).execute()
            
            Log.i(TAG, "Hugging Face HTTP response code: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.i(TAG, "Hugging Face raw response body length: ${responseBody?.length ?: 0}")
                Log.i(TAG, "Hugging Face raw JSON: ${responseBody?.take(200)}")
                addDiagnosticLog("    - Response body: ${responseBody?.take(150)}")
                responseBody?.let {
                    try {
                        val jsonArray = JSONArray(it)
                        if (jsonArray.length() > 0) {
                            val generatedText = jsonArray.getJSONObject(0)
                                .getString("generated_text")
                            
                            // Ajouter le style KITT à la réponse
                            val kittStyled = addKittStyle(generatedText)
                            Log.i(TAG, "Hugging Face SUCCESS: $kittStyled")
                            addDiagnosticLog("    - Generated text: ${generatedText.take(100)}")
                            return@withContext kittStyled
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Hugging Face JSON", e)
                        addDiagnosticLog("    - JSON parse error: ${e.message}")
                    }
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Hugging Face API HTTP ${response.code} ERROR:")
                Log.e(TAG, "Error body: $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Hugging Face API error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Ajoute le style KITT à une réponse générique
     */
    private fun addKittStyle(response: String): String {
        val prefixes = listOf(
            "Certainement, Michael. ",
            "À votre service. ",
            "Bien sûr. ",
            "Je suis sur le coup. ",
            "Mes systèmes indiquent que "
        )
        
        val prefix = prefixes.random()
        return prefix + response
    }
    
    /**
     * Réponse de fallback locale avec personnalité KITT
     */
    private fun getKittFallbackResponse(userInput: String): String {
        val input = userInput.lowercase().trim()
        
        return when {
            input.contains("bonjour") || input.contains("salut") || input.contains("hey") ->
                "Bonjour, Michael. Je suis KITT, à votre service. Tous mes systèmes sont opérationnels."
            
            input.contains("comment") && (input.contains("vas") || input.contains("va")) ->
                "Tous mes systèmes fonctionnent à capacité optimale. Merci de demander, Michael."
            
            input.contains("qui es-tu") || input.contains("qui es tu") ->
                "Je suis KITT, Knight Industries Two Thousand. Un système informatique sophistiqué conçu pour vous assister dans toutes vos missions."
            
            input.contains("aide") || input.contains("help") ->
                "Certainement. Je peux vous aider avec la navigation, l'analyse de données, la surveillance, et bien plus encore. Que puis-je faire pour vous ?"
            
            input.contains("merci") ->
                "De rien, Michael. C'est un plaisir de vous servir. N'hésitez pas si vous avez besoin d'autre chose."
            
            input.contains("scanner") || input.contains("scan") ->
                "Scanner activé. Surveillance de l'environnement en cours. Mes capteurs sont à l'affût de toute anomalie."
            
            input.contains("turbo") ->
                "Mode turbo boost prêt. Attention, Michael, cette fonction consomme beaucoup d'énergie. Utilisez-la avec discernement."
            
            input.contains("gps") || input.contains("navigation") ->
                "Système de navigation activé. GPS verrouillé. Je calcule l'itinéraire optimal pour votre destination."
            
            input.contains("système") || input.contains("statut") || input.contains("status") ->
                "Tous mes systèmes sont opérationnels: Navigation: OK, Scanner: OK, Communication: OK, Turbo: Prêt. Tout est nominal."
            
            input.contains("pourquoi") ->
                "C'est ma fonction première, Michael. Je suis programmé pour vous assister et vous protéger dans toutes les situations."
            
            input.contains("où") || input.contains("ou") ->
                "Je peux activer mes systèmes de localisation GPS si vous me donnez plus de détails sur votre destination."
            
            input.contains("quand") ->
                "Je suis disponible 24 heures sur 24, 7 jours sur 7, Michael. Mes circuits ne nécessitent jamais de repos."
            
            input.contains("au revoir") || input.contains("bye") ->
                "Au revoir, Michael. Je reste en veille. N'hésitez pas à me réactiver si vous avez besoin d'assistance."
            
            else ->
                "Je traite votre demande avec mes processeurs avancés. Cependant, mes capacités IA actuelles sont limitées sans connexion aux services cloud. Pouvez-vous reformuler ou être plus spécifique ?"
        }
    }
    
    /**
     * Réponse d'erreur avec style KITT
     */
    private fun getKittErrorResponse(error: String): String {
        return "Michael, je rencontre un dysfonctionnement temporaire dans mes circuits de traitement. Erreur détectée: $error. Réessayez dans un moment."
    }
    
    /**
     * Efface le cache et l'historique
     */
    fun clearCache() {
        responseCache.clear()
        conversationHistory.clear()
        Log.d(TAG, "Cache and conversation history cleared")
    }
    
    /**
     * Vérifie si au moins une API est configurée
     */
    fun isConfigured(): Boolean {
        val openaiKey = sharedPreferences.getString("openai_api_key", null)
        val anthropicKey = sharedPreferences.getString("anthropic_api_key", null)
        val huggingfaceKey = sharedPreferences.getString("huggingface_api_key", null)
        
        return !openaiKey.isNullOrEmpty() || 
               !anthropicKey.isNullOrEmpty() || 
               !huggingfaceKey.isNullOrEmpty()
    }
    
    /**
     * Obtient l'état de configuration
     */
    fun getConfigurationStatus(): String {
        val openai = !sharedPreferences.getString("openai_api_key", null).isNullOrEmpty()
        val anthropic = !sharedPreferences.getString("anthropic_api_key", null).isNullOrEmpty()
        val huggingface = !sharedPreferences.getString("huggingface_api_key", null).isNullOrEmpty()
        
        return buildString {
            append("AI Configuration Status:\n")
            append("OpenAI: ${if (openai) "Configured" else "Not configured"}\n")
            append("Anthropic: ${if (anthropic) "Configured" else "Not configured"}\n")
            append("Hugging Face: ${if (huggingface) "Configured" else "Not configured"}")
        }
    }
    
    /**
     * Classe pour retourner des détails de diagnostic complets
     */
    data class DiagnosticResult(
        val configStatus: String,
        val steps: List<StepResult>,
        val finalResponse: String,
        val responseTime: Long
    )
    
    data class StepResult(
        val stepNumber: Int,
        val apiName: String,
        val status: String, // "SUCCESS", "FAILED", "SKIPPED"
        val httpCode: Int?,
        val errorMessage: String?
    )
    
    /**
     * Version de diagnostic complète avec logs détaillés
     */
    suspend fun processUserInputWithDiagnostic(userInput: String): DiagnosticResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val steps = mutableListOf<StepResult>()
        
        // Configuration
        val openaiKey = sharedPreferences.getString("openai_api_key", null)?.trim()
        val anthropicKey = sharedPreferences.getString("anthropic_api_key", null)?.trim()
        val huggingfaceKey = sharedPreferences.getString("huggingface_api_key", null)?.trim()
        
        val configStatus = buildString {
            appendLine("OpenAI: ${if (openaiKey.isNullOrEmpty()) "✗ Non configurée" else "✓ Configurée (${openaiKey.length} chars)"}")
            appendLine("Anthropic: ${if (anthropicKey.isNullOrEmpty()) "✗ Non configurée" else "✓ Configurée (${anthropicKey.length} chars)"}")
            appendLine("Hugging Face: ${if (huggingfaceKey.isNullOrEmpty()) "✗ Non configurée" else "✓ Configurée (${huggingfaceKey.length} chars)"}")
        }
        
        var response: String? = null
        
        // Step 1: OpenAI
        if (!openaiKey.isNullOrEmpty()) {
            response = tryOpenAISimple(userInput, steps)
        } else {
            steps.add(StepResult(1, "OpenAI", "SKIPPED", null, "No API key configured"))
        }
        
        // Step 2: Anthropic
        if (response == null && !anthropicKey.isNullOrEmpty()) {
            response = tryAnthropicSimple(userInput, steps)
        } else if (response == null) {
            steps.add(StepResult(2, "Anthropic Claude", "SKIPPED", null, "No API key configured"))
        }
        
        // Step 3: Hugging Face
        if (response == null && !huggingfaceKey.isNullOrEmpty()) {
            response = tryHuggingFaceSimple(userInput, steps)
        } else if (response == null) {
            steps.add(StepResult(3, "Hugging Face", "SKIPPED", null, "No API key configured"))
        }
        
        // Step 4: Fallback
        if (response == null) {
            response = getKittFallbackResponse(userInput)
            steps.add(StepResult(4, "Local Fallback", "SUCCESS", 200, "Using offline responses"))
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        
        return@withContext DiagnosticResult(configStatus, steps, response, responseTime)
    }
    
    private suspend fun tryOpenAISimple(userInput: String, steps: MutableList<StepResult>): String? {
        return try {
            val apiKey = sharedPreferences.getString("openai_api_key", null)?.trim()
            if (apiKey.isNullOrEmpty()) return null
            
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", OPENAI_MODEL)
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 150)
            }
            
            val request = Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    steps.add(StepResult(1, "OpenAI GPT-4o-mini", "SUCCESS", response.code, null))
                    return content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                val errorMsg = try {
                    val json = JSONObject(errorBody ?: "{}")
                    json.getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    errorBody?.take(80)
                }
                steps.add(StepResult(1, "OpenAI GPT-4o-mini", "FAILED", response.code, errorMsg))
            }
            
            null
        } catch (e: Exception) {
            steps.add(StepResult(1, "OpenAI GPT-4o-mini", "FAILED", null, e.message?.take(80)))
            null
        }
    }
    
    private suspend fun tryAnthropicSimple(userInput: String, steps: MutableList<StepResult>): String? {
        return try {
            val apiKey = sharedPreferences.getString("anthropic_api_key", null)?.trim()
            if (apiKey.isNullOrEmpty()) return null
            
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", ANTHROPIC_MODEL)
                put("max_tokens", 150)
                put("system", getSystemPrompt())
                put("messages", messages)
            }
            
            val request = Request.Builder()
                .url(ANTHROPIC_API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                    
                    steps.add(StepResult(2, "Anthropic Claude 3.5", "SUCCESS", response.code, null))
                    return content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                steps.add(StepResult(2, "Anthropic Claude 3.5", "FAILED", response.code, errorBody?.take(80)))
            }
            
            null
        } catch (e: Exception) {
            steps.add(StepResult(2, "Anthropic Claude 3.5", "FAILED", null, e.message?.take(80)))
            null
        }
    }
    
    private suspend fun tryHuggingFaceSimple(userInput: String, steps: MutableList<StepResult>): String? {
        return try {
            val apiKey = sharedPreferences.getString("huggingface_api_key", null)?.trim()
            if (apiKey.isNullOrEmpty()) return null
            
            val requestBody = JSONObject().apply {
                put("inputs", userInput)
                put("parameters", JSONObject().apply {
                    put("max_length", 150)
                    put("temperature", 0.8)
                })
            }
            
            val request = Request.Builder()
                .url(HUGGINGFACE_API_URL + HUGGINGFACE_MODEL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val generatedText = jsonResponse.getString("generated_text")
                    val kittStyled = addKittStyle(generatedText)
                    
                    steps.add(StepResult(3, "Hugging Face BlenderBot", "SUCCESS", response.code, null))
                    return kittStyled
                }
            } else {
                val errorBody = response.body?.string()
                steps.add(StepResult(3, "Hugging Face BlenderBot", "FAILED", response.code, errorBody?.take(80)))
            }
            
            null
        } catch (e: Exception) {
            steps.add(StepResult(3, "Hugging Face BlenderBot", "FAILED", null, e.message?.take(80)))
            null
        }
    }
}

