package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.chatai.KeyringManager
import com.chatai.managers.OnnxTranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 🌐 TRANSLATION SERVICE
 * 
 * Service pour traduire du texte via ONNX MarianMT local ou Ollama Cloud
 * 
 * ⭐ SELON NOS RULES:
 * - Support ONNX local (priorité, 100% offline)
 * - Support Ollama Cloud (fallback)
 * - Traduction français → anglais (opus-mt-fr-en)
 * 
 * USAGE:
 * - Traduction automatique des messages
 * - Support multilingue amélioré
 * - Traduction offline sans cloud
 */
class TranslationService(private val context: Context) {
    
    companion object {
        private const val TAG = "TranslationService"
        
        // Ollama Cloud API
        // ⭐ REFACTORISÉ: URL centralisée dans ApiConfig
        private val OLLAMA_CLOUD_URL = com.chatai.config.ApiConfig.OLLAMA_CLOUD_CHAT
        
        // Modèles de traduction Ollama recommandés
        private val TRANSLATION_MODELS = listOf("qwen3", "llama3.2", "mistral")
    }
    
    // ⭐ Manager ONNX pour traduction locale
    private var onnxTranslationManager: OnnxTranslationManager? = null
    private var onnxInitialized = false
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val keyring: KeyringManager = KeyringManager.getInstance(context)
    
    // Client HTTP pour Ollama Cloud
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    
    init {
        // ⭐ Initialiser ONNX Translation Manager au démarrage
        initializeOnnxTranslation()
    }
    
    /**
     * Initialiser le manager ONNX pour traduction locale
     */
    private fun initializeOnnxTranslation() {
        try {
            if (onnxTranslationManager == null) {
                onnxTranslationManager = OnnxTranslationManager(context)
            }
            
            if (!onnxInitialized) {
                onnxInitialized = onnxTranslationManager!!.initialize()
                if (onnxInitialized) {
                    Log.i(TAG, "✅ ONNX Translation initialisé (MarianMT, opus-mt-fr-en)")
                } else {
                    Log.w(TAG, "⚠️ ONNX Translation non disponible, fallback Ollama Cloud")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation ONNX Translation: ${e.message}", e)
            onnxInitialized = false
        }
    }
    
    /**
     * Traduire un texte du français vers l'anglais
     * ⭐ MODIFIÉ: Essaie ONNX local en premier, puis fallback Ollama
     * @param text Texte source en français
     * @return Texte traduit en anglais ou null en cas d'erreur
     */
    suspend fun translate(text: String): String? = withContext(Dispatchers.IO) {
        try {
            if (text.isBlank()) {
                Log.w(TAG, "Texte vide, retour null")
                return@withContext null
            }
            
            // ⭐ NOUVEAU: Essayer ONNX local en premier (100% offline)
            if (onnxInitialized && onnxTranslationManager?.isReady() == true) {
                try {
                    val translated = onnxTranslationManager!!.translate(text)
                    if (translated != null && translated.isNotBlank()) {
                        Log.d(TAG, "✅ Texte traduit via ONNX local")
                        return@withContext translated
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur ONNX Translation, fallback: ${e.message}")
                }
            }
            
            // ⭐ Fallback: Ollama Cloud (si disponible)
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            if (useCloud) {
                val translated = tryOllamaTranslation(text)
                if (translated != null) {
                    Log.d(TAG, "✅ Texte traduit via Ollama Cloud")
                    return@withContext translated
                }
            }
            
            Log.w(TAG, "Aucun service de traduction disponible, retour null")
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur traduction: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * ⭐ WRAPPER JAVA: Traduire de manière synchrone (pour appel depuis Java)
     * @param text Texte source en français
     * @return Texte traduit en anglais ou null en cas d'erreur
     */
    fun translateSync(text: String): String? {
        return kotlinx.coroutines.runBlocking {
            translate(text)
        }
    }
    
    /**
     * ⭐ NOUVEAU: Essayer Ollama Cloud Translation API
     */
    private suspend fun tryOllamaTranslation(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = keyring.getApiKey("ollama")?.trim()
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "Ollama Cloud API key not configured")
                return@withContext null
            }
            
            // Récupérer le modèle configuré (ou défaut)
            val translationModel = sharedPreferences.getString("ollama_cloud_model", TRANSLATION_MODELS.first())
                ?: TRANSLATION_MODELS.first()
            
            Log.d(TAG, "Trying Ollama Cloud Translation with model: $translationModel")
            
            // Construire la requête Ollama Translation
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", "Tu es un traducteur professionnel. Traduis le texte français en anglais de manière précise et naturelle.")
            })
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", "Traduis ce texte en anglais: $text")
            })
            
            val requestBody = JSONObject().apply {
                put("model", translationModel)
                put("messages", messages)
                put("stream", false)
            }
            
            val request = Request.Builder()
                .url(OLLAMA_CLOUD_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.w(TAG, "Ollama Translation request failed: HTTP ${response.code}")
                if (errorBody != null) {
                    Log.w(TAG, "Error body: ${errorBody.take(200)}")
                }
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.w(TAG, "Ollama Translation response body is null")
                return@withContext null
            }
            
            // Parser la réponse Ollama
            val jsonResponse = JSONObject(responseBody)
            val message = jsonResponse.optJSONObject("message")
            val content = message?.optString("content")
            
            if (content != null && content.isNotBlank()) {
                Log.d(TAG, "✅ Ollama Translation: ${content.take(100)}...")
                return@withContext content.trim()
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Ollama Translation: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Vérifier si le service est prêt
     */
    fun isReady(): Boolean = onnxInitialized && onnxTranslationManager?.isReady() == true
}

