package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.chatai.KeyringManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service dédié pour Hugging Face Inference API
 * 
 * Gère:
 * - Embeddings (pour RAG)
 * - LLM (génération de texte)
 * 
 * Architecture propre et unifiée pour éviter les patchages
 * 
 * ⭐ SELON NOS RULES:
 * - Service dédié et isolé
 * - Gestion d'erreurs robuste
 * - Configuration claire et simple
 */
class HuggingFaceService(private val context: Context) {
    
    companion object {
        private const val TAG = "HuggingFaceService"
        
        // ⭐ REFACTORISÉ: URL centralisée dans ApiConfig
        private val BASE_URL = com.chatai.config.ApiConfig.HUGGINGFACE_INFERENCE_BASE + "/"
        
        // Modèles par défaut
        const val DEFAULT_EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2" // 384 dimensions
        const val DEFAULT_LLM_MODEL = "gpt2" // Modèle LLM par défaut
        
        // Dimensions d'embeddings
        const val EMBEDDING_DIMENSIONS = 384 // all-MiniLM-L6-v2
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val keyring: KeyringManager = KeyringManager.getInstance(context)
    
    // Client HTTP avec timeouts appropriés
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Vérifie si Hugging Face est configuré et disponible
     */
    fun isConfigured(): Boolean {
        val apiKey = keyring.getApiKey("huggingface")?.trim()
        return !apiKey.isNullOrEmpty()
    }
    
    /**
     * Vérifie si Hugging Face est activé pour les embeddings
     */
    fun isEnabledForEmbeddings(): Boolean {
        return sharedPreferences.getBoolean("rag_use_huggingface", false)
    }
    
    /**
     * Vérifie si Hugging Face est activé pour le LLM
     */
    fun isEnabledForLLM(): Boolean {
        return sharedPreferences.getBoolean("use_huggingface_llm", false)
    }
    
    /**
     * Génère un embedding via Hugging Face Inference API
     * 
     * @param text Texte à convertir en embedding
     * @return FloatArray (384 dimensions) ou null en cas d'erreur
     */
    suspend fun generateEmbedding(text: String): FloatArray? = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                Log.w(TAG, "Text is blank, returning null")
                return@withContext null
            }
            
            val apiKey = keyring.getApiKey("huggingface")?.trim()
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "Hugging Face API key not configured")
                return@withContext null
            }
            
            // Récupérer le modèle configuré (ou défaut)
            val model = sharedPreferences.getString("hf_embedding_model", DEFAULT_EMBEDDING_MODEL)
                ?: DEFAULT_EMBEDDING_MODEL
            
            val url = BASE_URL + model
            Log.d(TAG, "Generating embedding via Hugging Face: $model")
            
            // Format Hugging Face: {"inputs": "text to embed"}
            val requestBody = JSONObject().apply {
                put("inputs", text)
            }
            
            val request = Request.Builder()
                .url(url)
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
     * Génère une réponse LLM via Hugging Face Inference API
     * 
     * @param userInput Message utilisateur
     * @param systemPrompt Prompt système (optionnel)
     * @return Réponse générée ou null en cas d'erreur
     */
    suspend fun generateText(
        userInput: String,
        systemPrompt: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (userInput.isBlank()) {
                Log.w(TAG, "User input is blank, returning null")
                return@withContext null
            }
            
            val apiKey = keyring.getApiKey("huggingface")?.trim()
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "Hugging Face API key not configured")
                return@withContext null
            }
            
            // Récupérer le modèle LLM configuré (ou défaut)
            val model = sharedPreferences.getString("hf_llm_model", DEFAULT_LLM_MODEL)
                ?: DEFAULT_LLM_MODEL
            
            val url = BASE_URL + model
            Log.d(TAG, "Generating text via Hugging Face LLM: $model")
            
            // Construire le prompt complet
            val fullPrompt = if (systemPrompt != null && systemPrompt.isNotBlank()) {
                "$systemPrompt\n\nUser: $userInput\nAssistant:"
            } else {
                userInput
            }
            
            // Format Hugging Face: {"inputs": "prompt", "parameters": {...}}
            val requestBody = JSONObject().apply {
                put("inputs", fullPrompt)
                put("parameters", JSONObject().apply {
                    put("max_length", 150)
                    put("temperature", 0.8)
                    put("return_full_text", false)
                })
            }
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val statusCode = response.code
                Log.w(TAG, "Hugging Face LLM request failed: HTTP $statusCode")
                if (errorBody != null) {
                    Log.w(TAG, "Error body: ${errorBody.take(200)}")
                }
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.w(TAG, "Hugging Face LLM response body is null")
                return@withContext null
            }
            
            // Parser la réponse Hugging Face
            // Format: [{"generated_text": "..."}]
            val jsonArray = JSONArray(responseBody)
            if (jsonArray.length() == 0) {
                Log.w(TAG, "Hugging Face LLM response is empty")
                return@withContext null
            }
            
            val generatedText = jsonArray.getJSONObject(0)
                .getString("generated_text")
            
            Log.d(TAG, "✅ Hugging Face LLM generated text: ${generatedText.take(100)}...")
            return@withContext generatedText.trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text with Hugging Face", e)
            return@withContext null
        }
    }
    
    /**
     * Teste la connexion à Hugging Face API
     * 
     * @return true si l'API est accessible, false sinon
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val apiKey = keyring.getApiKey("huggingface")?.trim()
            if (apiKey.isNullOrEmpty()) {
                return@withContext false
            }
            
            // Tester avec le modèle d'embedding (plus léger)
            val model = sharedPreferences.getString("hf_embedding_model", DEFAULT_EMBEDDING_MODEL)
                ?: DEFAULT_EMBEDDING_MODEL
            
            val url = BASE_URL + model
            val requestBody = JSONObject().apply {
                put("inputs", "test")
            }
            
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            val available = response.isSuccessful || response.code == 503 // 503 = modèle en chargement
            response.close()
            
            if (available) {
                Log.i(TAG, "✅ Hugging Face API available (model: $model)")
            } else {
                Log.w(TAG, "⚠️ Hugging Face API not available (HTTP ${response.code})")
            }
            
            return@withContext available
            
        } catch (e: Exception) {
            Log.w(TAG, "Hugging Face connection test failed: ${e.message}")
            return@withContext false
        }
    }
}

