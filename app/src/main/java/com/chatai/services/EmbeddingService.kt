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
 * 
 * Référence: https://github.com/ollama/ollama/blob/main/docs/api.md#generate-embeddings
 * 
 * ⭐ SELON NOS RULES:
 * - Support Ollama local (toujours disponible)
 * - ⭐ FUTURE-PROOF: Support Ollama Cloud si /api/embeddings est disponible (détection automatique)
 * - Format compatible avec ConversationEntity.embeddingsJson
 * - Gestion d'erreurs non-bloquante (continue même si embedding échoue)
 */
class EmbeddingService(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingService"
        
        // Modèle d'embedding par défaut (nomic-embed-text: 768 dimensions)
        private const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text"
        
        // ⭐ NOUVEAU: Modèles Hugging Face pour embeddings (Cloud)
        private const val HUGGINGFACE_API_URL = "https://router.huggingface.co/hf-inference/models/"
        private const val DEFAULT_HF_EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2" // 384 dimensions
        // Alternatives: "sentence-transformers/all-mpnet-base-v2" (768), "intfloat/multilingual-e5-base" (768)
        
        // Dimensions de nomic-embed-text
        private const val EMBEDDING_DIMENSIONS = 768
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val keyring: com.chatai.KeyringManager = com.chatai.KeyringManager.getInstance(context)
    
    // ⭐ SELON NOS RULES: Client HTTP avec timeouts similaires à OllamaThinkingService
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Génère un embedding pour un texte donné
     * @param text Texte à convertir en embedding
     * @return Tableau de floats (768 dimensions pour nomic-embed-text) ou null en cas d'erreur
     */
    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                Log.w(TAG, "Text is blank, returning null")
                return@withContext null
            }
            
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            
            // ⭐ NOUVEAU: Si Ollama Cloud est utilisé, utiliser Hugging Face pour embeddings
            // (car Ollama Cloud ne supporte pas /api/embeddings)
            val useHuggingFace = useCloud && sharedPreferences.getBoolean("rag_use_huggingface", true)
            val huggingFaceApiKey = keyring.getApiKey("huggingface")?.trim()
            
            if (useHuggingFace && !huggingFaceApiKey.isNullOrEmpty()) {
                // Utiliser Hugging Face Inference API pour embeddings
                return@withContext embedWithHuggingFace(text, huggingFaceApiKey)
            }
            
            // Sinon, utiliser Ollama (local ou cloud si supporté)
            val embeddingsUrl = if (useCloud) {
                // ⭐ FUTURE-PROOF: Tester si Ollama Cloud supporte /api/embeddings
                // Si Ollama ajoute cet endpoint, il sera automatiquement utilisé
                "https://ollama.com/api/embeddings"
            } else {
                // Récupérer URL du serveur Ollama local
                val localServerUrl = sharedPreferences.getString("local_server_url", null)?.trim()
                    ?: "http://localhost:11434"
                
                if (localServerUrl.endsWith("/v1/chat/completions")) {
                    // Si l'URL contient déjà /v1/chat/completions, remplacer par /api/embeddings
                    localServerUrl.replace("/v1/chat/completions", "/api/embeddings")
                } else {
                    // Sinon, ajouter /api/embeddings
                    "$localServerUrl/api/embeddings"
                }
            }
            
            // Récupérer le modèle d'embedding configuré (ou défaut)
            val embeddingModel = sharedPreferences.getString("embedding_model", DEFAULT_EMBEDDING_MODEL)
                ?: DEFAULT_EMBEDDING_MODEL
            
            Log.d(TAG, "Generating embedding for text (length=${text.length} chars)")
            Log.d(TAG, "Using model: $embeddingModel, URL: $embeddingsUrl")
            
            // Construire la requête selon l'API Ollama embeddings
            // Format: {"model": "nomic-embed-text", "prompt": "text to embed"}
            val requestBody = JSONObject().apply {
                put("model", embeddingModel)
                put("prompt", text)
            }
            
            // Construire la requête avec headers appropriés
            val requestBuilder = Request.Builder()
                .url(embeddingsUrl)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            
            // ⭐ FUTURE-PROOF: Ajouter API key si Cloud est utilisé (quand supporté)
            if (useCloud) {
                val apiKey = keyring.getApiKey("ollama")
                if (apiKey != null && apiKey.trim().isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                    Log.d(TAG, "Using Ollama Cloud API key for embeddings")
                } else {
                    Log.w(TAG, "Ollama Cloud API key not found, embeddings may fail")
                }
            }
            
            val request = requestBuilder.build()
            
            // Exécuter la requête
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val statusCode = response.code
                
                // ⭐ AMÉLIORATION: Messages d'erreur spécifiques selon le code HTTP
                when (statusCode) {
                    401 -> {
                        Log.w(TAG, "Embedding request failed: 401 Unauthorized - API key invalide ou manquante")
                        if (useCloud) {
                            Log.w(TAG, "Vérifiez que votre clé API Ollama Cloud est correcte")
                        }
                    }
                    403 -> {
                        Log.w(TAG, "Embedding request failed: 403 Forbidden - Accès refusé")
                        if (useCloud) {
                            Log.w(TAG, "Votre clé API Ollama Cloud n'a peut-être pas les permissions nécessaires")
                        }
                    }
                    400 -> {
                        // Bad Request = endpoint existe mais paramètres invalides (ex: modèle non trouvé)
                        if (useCloud) {
                            Log.w(TAG, "Embedding request failed: 400 Bad Request - L'endpoint existe mais le modèle d'embedding n'est peut-être pas disponible")
                            Log.w(TAG, "Vérifiez que le modèle '$embeddingModel' est disponible sur Ollama Cloud")
                        } else {
                            Log.w(TAG, "Embedding request failed: 400 Bad Request - Modèle d'embedding non trouvé")
                            Log.w(TAG, "Vérifiez que le modèle '$embeddingModel' est installé sur Ollama local")
                        }
                    }
                    404, 501 -> {
                        if (useCloud) {
                            Log.d(TAG, "Ollama Cloud /api/embeddings not yet available (HTTP $statusCode)")
                            Log.d(TAG, "Si Ollama Cloud supporte maintenant les embeddings, le test automatique devrait le détecter")
                        } else {
                            Log.w(TAG, "Embedding request failed: $statusCode - Endpoint non trouvé")
                            Log.w(TAG, "Vérifiez que l'URL Ollama local est correcte: $embeddingsUrl")
                        }
                    }
                    429 -> {
                        Log.w(TAG, "Embedding request failed: 429 Too Many Requests - Rate limit atteint")
                        if (useCloud) {
                            Log.w(TAG, "Attendez quelques instants avant de réessayer")
                        }
                    }
                    500, 502, 503 -> {
                        Log.w(TAG, "Embedding request failed: $statusCode - Erreur serveur")
                        if (useCloud) {
                            Log.w(TAG, "Ollama Cloud est temporairement indisponible, réessayez plus tard")
                        } else {
                            Log.w(TAG, "Ollama local semble avoir un problème, vérifiez qu'il est démarré")
                        }
                    }
                    else -> {
                        Log.e(TAG, "Embedding request failed: $statusCode ${response.message}")
                        Log.e(TAG, "Error body: $errorBody")
                    }
                }
                
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.e(TAG, "Embedding response body is null")
                return@withContext null
            }
            
            // Parser la réponse Ollama
            // Format: {"embedding": [0.23, -0.54, 0.89, ...]}
            val jsonResponse = JSONObject(responseBody)
            
            // Vérifier si la réponse contient un embedding
            if (!jsonResponse.has("embedding")) {
                Log.e(TAG, "Response does not contain 'embedding' field")
                Log.e(TAG, "Response: $responseBody")
                return@withContext null
            }
            
            val embeddingArray = jsonResponse.getJSONArray("embedding")
            
            // Convertir en FloatArray
            val embedding = FloatArray(embeddingArray.length()) { i ->
                embeddingArray.getDouble(i).toFloat()
            }
            
            // Vérifier que les dimensions sont correctes (nomic-embed-text = 768)
            if (embedding.size != EMBEDDING_DIMENSIONS) {
                Log.w(TAG, "Embedding dimensions mismatch: expected $EMBEDDING_DIMENSIONS, got ${embedding.size}")
                // Continuer quand même, car d'autres modèles peuvent avoir différentes dimensions
            }
            
            Log.d(TAG, "✅ Embedding generated: ${embedding.size} dimensions")
            return@withContext embedding
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error generating embedding", e)
            return@withContext null
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "JSON parsing error generating embedding", e)
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error generating embedding", e)
            return@withContext null
        }
    }
    
    /**
     * ⭐ NOUVEAU: Génère un embedding via Hugging Face Inference API
     * Utilisé quand Ollama Cloud est sélectionné (car Ollama Cloud ne supporte pas /api/embeddings)
     */
    private suspend fun embedWithHuggingFace(text: String, apiKey: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            // Récupérer le modèle Hugging Face configuré (ou défaut)
            val hfModel = sharedPreferences.getString("hf_embedding_model", DEFAULT_HF_EMBEDDING_MODEL)
                ?: DEFAULT_HF_EMBEDDING_MODEL
            
            val embeddingsUrl = HUGGINGFACE_API_URL + hfModel
            Log.d(TAG, "Generating embedding via Hugging Face: $hfModel")
            
            // Format Hugging Face: {"inputs": "text to embed"}
            val requestBody = JSONObject().apply {
                put("inputs", text)
            }
            
            val request = Request.Builder()
                .url(embeddingsUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val statusCode = response.code
                Log.w(TAG, "Hugging Face embedding request failed: HTTP $statusCode")
                if (errorBody != null) {
                    Log.w(TAG, "Error body: ${errorBody.take(200)}")
                }
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.w(TAG, "Hugging Face embedding response body is null")
                return@withContext null
            }
            
            // Hugging Face retourne: [[0.1, 0.2, ...]] (array de arrays, on prend le premier)
            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() == 0) {
                Log.w(TAG, "Hugging Face embedding response is empty")
                return@withContext null
            }
            
            val embeddingArray = jsonArray.getJSONArray(0) // Prendre le premier array
            val embedding = FloatArray(embeddingArray.length()) { i ->
                embeddingArray.getDouble(i).toFloat()
            }
            
            Log.d(TAG, "✅ Hugging Face embedding generated: ${embedding.size} dimensions")
            return@withContext embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding with Hugging Face", e)
            return@withContext null
        }
    }
    
    /**
     * Génère un embedding pour une conversation complète (userMessage + aiResponse)
     * Combine les deux textes pour créer un embedding représentatif de la conversation
     * 
     * ⭐ SELON NOS RULES: Format pour ConversationEntity.embeddingsJson
     */
    suspend fun embedConversation(userMessage: String, aiResponse: String): FloatArray? {
        // Combiner userMessage et aiResponse pour créer un embedding représentatif
        val combinedText = if (aiResponse.trim().isNotEmpty()) {
            "$userMessage\n$aiResponse"
        } else {
            userMessage
        }
        
        return embed(combinedText)
    }
    
    /**
     * Convertit un FloatArray en JSON string pour stockage dans Room DB
     * Format compatible avec ConversationEntity.embeddingsJson
     * 
     * ⭐ SELON NOS RULES: Format JSON array pour stockage efficace
     */
    fun embeddingToJson(embedding: FloatArray): String {
        return try {
            JSONArray().apply {
                embedding.forEach { value ->
                    put(value.toDouble())
                }
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting embedding to JSON", e)
            "[]"
        }
    }
    
    /**
     * Convertit un JSON string en FloatArray pour comparaison
     * Format compatible avec ConversationEntity.embeddingsJson
     */
    fun jsonToEmbedding(jsonString: String): FloatArray? {
        return try {
            if (jsonString.isBlank() || jsonString == "null") {
                return null
            }
            
            val jsonArray = JSONArray(jsonString)
            FloatArray(jsonArray.length()) { i ->
                jsonArray.getDouble(i).toFloat()
            }
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "Error parsing embedding JSON", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing embedding JSON", e)
            null
        }
    }
    
    /**
     * Vérifie si le service d'embedding est disponible
     * Teste Ollama local ou Cloud selon la configuration
     * 
     * ⭐ FUTURE-PROOF: Teste automatiquement si Ollama Cloud supporte /api/embeddings
     * Si Ollama ajoute cet endpoint, il sera automatiquement détecté et utilisé
     * 
     * ⭐ SELON NOS RULES: Vérification non-bloquante, continue même si échoue
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            val useHuggingFace = useCloud && sharedPreferences.getBoolean("rag_use_huggingface", true)
            val huggingFaceApiKey = keyring.getApiKey("huggingface")?.trim()
            
            // ⭐ NOUVEAU: Si Ollama Cloud + Hugging Face configuré, vérifier Hugging Face
            if (useHuggingFace && !huggingFaceApiKey.isNullOrEmpty()) {
                // Tester Hugging Face Inference API
                val hfModel = sharedPreferences.getString("hf_embedding_model", DEFAULT_HF_EMBEDDING_MODEL)
                    ?: DEFAULT_HF_EMBEDDING_MODEL
                val testUrl = HUGGINGFACE_API_URL + hfModel
                
                val testRequestBody = JSONObject().apply {
                    put("inputs", "test")
                }
                
                val request = Request.Builder()
                    .url(testUrl)
                    .addHeader("Authorization", "Bearer $huggingFaceApiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(testRequestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                try {
                    val response = httpClient.newCall(request).execute()
                    val available = response.isSuccessful || response.code == 503 // 503 = modèle en chargement
                    response.close()
                    if (available) {
                        Log.i(TAG, "✅ Hugging Face embeddings available (model: $hfModel)")
                    } else {
                        Log.w(TAG, "⚠️ Hugging Face embeddings not available (HTTP ${response.code})")
                    }
                    return@withContext available
                } catch (e: Exception) {
                    Log.w(TAG, "Hugging Face embeddings test failed: ${e.message}")
                    return@withContext false
                }
            }
            
            if (useCloud) {
                // ⭐ FUTURE-PROOF: Tester si Ollama Cloud supporte /api/embeddings
                // Si l'endpoint existe (même avec erreur 401/403), c'est qu'il est disponible
                // Si l'endpoint n'existe pas (404/501), c'est qu'il n'est pas encore disponible
                val testUrl = "https://ollama.com/api/embeddings"
                val testRequestBody = JSONObject().apply {
                    put("model", DEFAULT_EMBEDDING_MODEL)
                    put("prompt", "test")
                }
                
                val apiKey = keyring.getApiKey("ollama")
                val requestBuilder = Request.Builder()
                    .url(testUrl)
                    .post(testRequestBody.toString().toRequestBody("application/json".toMediaType()))
                
                if (apiKey != null && apiKey.trim().isNotEmpty()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
                
                val request = requestBuilder.build()
                
                try {
                    val response = httpClient.newCall(request).execute()
                    val statusCode = response.code
                    
                    // Si 200-299: endpoint disponible et fonctionnel ✅
                    // Si 401/403: endpoint existe mais nécessite auth/config (disponible mais non configuré)
                    // Si 404/501: endpoint n'existe pas encore (non disponible)
                    // ⭐ AMÉLIORATION: Plus permissif - considérer 400 (Bad Request) comme disponible (endpoint existe, juste mauvais paramètres)
                    val available = when (statusCode) {
                        in 200..299 -> {
                            Log.i(TAG, "✅ Ollama Cloud /api/embeddings is available and working (HTTP $statusCode)")
                            true
                        }
                        400 -> {
                            // Bad Request = endpoint existe mais paramètres invalides (ex: modèle non trouvé)
                            // C'est un signe que l'endpoint existe, donc on considère comme disponible
                            Log.d(TAG, "✅ Ollama Cloud /api/embeddings exists (HTTP 400 - peut nécessiter un modèle d'embedding différent)")
                            true
                        }
                        401, 403 -> {
                            Log.d(TAG, "✅ Ollama Cloud /api/embeddings exists but requires authentication (HTTP $statusCode)")
                            true // Endpoint existe, juste besoin de config
                        }
                        404, 501 -> {
                            Log.d(TAG, "Ollama Cloud /api/embeddings not yet available (HTTP $statusCode - endpoint n'existe pas)")
                            false
                        }
                        else -> {
                            Log.w(TAG, "Ollama Cloud /api/embeddings returned unexpected status: $statusCode")
                            // ⭐ AMÉLIORATION: Être plus permissif - si ce n'est pas 404/501, considérer comme disponible
                            // (peut être une erreur temporaire ou un problème de config)
                            statusCode != 404 && statusCode != 501
                        }
                    }
                    
                    response.close() // Fermer la réponse
                    return@withContext available
                    
                } catch (e: Exception) {
                    Log.d(TAG, "Ollama Cloud /api/embeddings test failed (likely not available yet): ${e.message}")
                    return@withContext false
                }
            } else {
                // Test Ollama local
                val localServerUrl = sharedPreferences.getString("local_server_url", null)?.trim()
                    ?: "http://localhost:11434"
                
                // Construire URL pour ping (utiliser /api/tags comme test)
                val pingUrl = if (localServerUrl.endsWith("/v1/chat/completions")) {
                    localServerUrl.replace("/v1/chat/completions", "/api/tags")
                } else {
                    "$localServerUrl/api/tags"
                }
                
                val request = Request.Builder()
                    .url(pingUrl)
                    .get()
                    .build()
                
                val response = httpClient.newCall(request).execute()
                val available = response.isSuccessful
                
                if (available) {
                    Log.d(TAG, "✅ Embedding service is available (Ollama local)")
                } else {
                    Log.w(TAG, "⚠️ Embedding service not available (Ollama local not responding)")
                }
                
                return@withContext available
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Embedding service not available", e)
            return@withContext false
        }
    }
    
    /**
     * Version Java-friendly pour vérifier la disponibilité
     * Utilise runBlocking pour appeler la fonction suspend depuis Java
     */
    fun isAvailableJava(): Boolean {
        return kotlinx.coroutines.runBlocking {
            isAvailable()
        }
    }
}


