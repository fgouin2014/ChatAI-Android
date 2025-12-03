package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Service pour executer des modeles GGUF localement sur le device
 * Utilise llama.cpp via JNI pour l'inference
 * 
 * Phase 1 - Corrections:
 * - Initialisation robuste avec flag isLibraryLoaded
 * - Verification avant chaque appel JNI
 * - Nouvelles methodes: clearConversation, isLibraryLoaded, getActiveContextCount
 */
class LocalGGUFService(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalGGUFService"
        
        // Flag pour verifier si la bibliotheque est chargee
        @Volatile
        private var isLibraryLoaded = false
        
        // Message d'erreur si chargement echoue
        private var libraryLoadError: String? = null
        
        // Charger la bibliotheque native
        init {
            try {
                System.loadLibrary("llama_jni")
                isLibraryLoaded = true
                Log.i(TAG, "[init] Native library llama_jni loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                isLibraryLoaded = false
                libraryLoadError = e.message
                Log.e(TAG, "[init] FAILED to load native library: ${e.message}")
                Log.e(TAG, "[init] Make sure llama.cpp is compiled for Android ARM64")
            } catch (e: Exception) {
                isLibraryLoaded = false
                libraryLoadError = e.message
                Log.e(TAG, "[init] Unexpected error loading library: ${e.message}")
            }
        }
        
        /**
         * Verifie si la bibliotheque native est disponible
         */
        fun isNativeLibraryAvailable(): Boolean = isLibraryLoaded
        
        /**
         * Retourne l'erreur de chargement si applicable
         */
        fun getLibraryLoadError(): String? = libraryLoadError
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    // Contexte du modele (0 si non initialise)
    private var modelContext: Long = 0
    
    // Etat d'initialisation
    private var isInitialized = false
    
    // Chemin du modele actuellement charge
    private var currentModelPath: String? = null
    
    // ========== GGUF INFERENCE PARAMETERS ==========
    // These are loaded from SharedPreferences (set via webapp)
    
    /**
     * Data class for GGUF inference parameters
     */
    data class GGUFParams(
        val systemPrompt: String = "You are a helpful AI assistant.",
        val temperature: Float = 0.7f,
        val maxTokens: Int = 128,
        val contextSize: Int = 2048,
        val threads: Int = 4,
        val topK: Int = 40,
        val topP: Float = 0.95f,
        val flashAttention: Boolean = false,
        val batchSize: Int = 512,
        val repeatPenalty: Float = 1.1f
    )
    
    /**
     * Load GGUF parameters from SharedPreferences
     * The webapp saves these as JSON in ai_config
     */
    fun loadGGUFParams(): GGUFParams {
        try {
            val configJson = sharedPreferences.getString("ai_config", null)
            if (configJson != null) {
                val json = org.json.JSONObject(configJson)
                val ggufParams = json.optJSONObject("gguf_params")
                if (ggufParams != null) {
                    return GGUFParams(
                        systemPrompt = ggufParams.optString("system_prompt", "You are a helpful AI assistant."),
                        temperature = ggufParams.optDouble("temperature", 0.7).toFloat(),
                        maxTokens = ggufParams.optInt("max_tokens", 128),
                        contextSize = ggufParams.optInt("context_size", 2048),
                        threads = ggufParams.optInt("threads", 4),
                        topK = ggufParams.optInt("top_k", 40),
                        topP = ggufParams.optDouble("top_p", 0.95).toFloat(),
                        flashAttention = ggufParams.optBoolean("flash_attention", false),
                        batchSize = ggufParams.optInt("batch_size", 512),
                        repeatPenalty = ggufParams.optDouble("repeat_penalty", 1.1).toFloat()
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "[loadGGUFParams] Error parsing config, using defaults: ${e.message}")
        }
        return GGUFParams()
    }
    
    /**
     * Get the system prompt from config
     */
    fun getSystemPrompt(): String = loadGGUFParams().systemPrompt
    
    /**
     * Get temperature from config
     */
    fun getTemperature(): Float = loadGGUFParams().temperature
    
    /**
     * Get max tokens from config
     */
    fun getMaxTokens(): Int = loadGGUFParams().maxTokens
    
    /**
     * Verifie si un modele GGUF existe au chemin specifie
     */
    fun modelExists(modelPath: String): Boolean {
        val file = File(modelPath)
        val exists = file.exists() && file.isFile && file.extension == "gguf"
        if (!exists) {
            Log.w(TAG, "[modelExists] Model not found: $modelPath")
        }
        return exists
    }
    
    /**
     * Verifie si le service peut etre utilise (bibliotheque chargee)
     */
    fun canUse(): Boolean {
        if (!isLibraryLoaded) {
            Log.w(TAG, "[canUse] Native library not loaded: $libraryLoadError")
            return false
        }
        return true
    }
    
    /**
     * Initialise le modele GGUF
     * @param modelPath Chemin vers le fichier .gguf (optionnel, utilise config si null)
     * @param nThreads Nombre de threads (optionnel, utilise config si null)
     * @return true si initialisation reussie
     */
    suspend fun initialize(modelPath: String? = null, nThreads: Int? = null): Boolean = withContext(Dispatchers.IO) {
        // Verifier que la bibliotheque est chargee
        if (!isLibraryLoaded) {
            Log.e(TAG, "[initialize] Cannot initialize - native library not loaded")
            Log.e(TAG, "[initialize] Error was: $libraryLoadError")
            return@withContext false
        }
        
        try {
            // Load GGUF parameters from config
            val params = loadGGUFParams()
            val threads = nThreads ?: params.threads
            
            Log.i(TAG, "[initialize] Using params: threads=$threads, contextSize=${params.contextSize}, flashAttention=${params.flashAttention}")
            
            // Recuperer le chemin du modele
            val path = modelPath ?: sharedPreferences.getString("local_gguf_model", "qwen2.5-1.5b-instruct-q4_k_m.gguf")
                ?.let { fileName ->
                    // Chemin unifie: /storage/emulated/0/ChatAI-Files/models/
                    val modelsDir = File("/storage/emulated/0/ChatAI-Files/models")
                    val modelFile = File(modelsDir, fileName)
                    Log.d(TAG, "[initialize] Looking for model: ${modelFile.absolutePath}")
                    modelFile.absolutePath
                } ?: run {
                    Log.e(TAG, "[initialize] No model specified in preferences")
                    return@withContext false
                }
            
            // Verifier que le fichier existe
            if (!modelExists(path)) {
                Log.e(TAG, "[initialize] Model file not found: $path")
                return@withContext false
            }
            
            // Liberer l'ancien modele si necessaire
            if (isInitialized && modelContext != 0L) {
                Log.d(TAG, "[initialize] Freeing previous model...")
                releaseModel()
            }
            
            // Initialiser le nouveau modele avec les parametres de config
            Log.i(TAG, "[initialize] Loading model: $path (threads: $threads, ctx: ${params.contextSize}, flash: ${params.flashAttention})")
            modelContext = initModelWithParams(
                path, 
                threads, 
                params.contextSize,
                params.batchSize,
                params.flashAttention
            )
            
            if (modelContext == 0L) {
                Log.e(TAG, "[initialize] FAILED - initModel returned 0")
                return@withContext false
            }
            
            isInitialized = true
            currentModelPath = path
            Log.i(TAG, "[initialize] SUCCESS - context ID: $modelContext")
            return@withContext true
            
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "[initialize] JNI link error - library may not be properly loaded", e)
            isLibraryLoaded = false
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "[initialize] Unexpected error", e)
            return@withContext false
        }
    }
    
    /**
     * Genere une reponse depuis le modele
     * @param prompt Prompt utilisateur
     * @param maxTokens Nombre maximum de tokens (optionnel, utilise config si null)
     * @param temperature Temperature 0.0-2.0 (optionnel, utilise config si null)
     * @return Reponse generee ou null si erreur
     */
    suspend fun processUserInput(
        prompt: String,
        maxTokens: Int? = null,
        temperature: Float? = null
    ): String? = withContext(Dispatchers.IO) {
        // Verifier que la bibliotheque est chargee
        if (!isLibraryLoaded) {
            Log.e(TAG, "[processUserInput] Native library not loaded")
            return@withContext null
        }
        
        try {
            if (!isInitialized || modelContext == 0L) {
                Log.w(TAG, "[processUserInput] Model not initialized, attempting auto-init...")
                val initialized = initialize()
                if (!initialized) {
                    Log.e(TAG, "[processUserInput] Auto-initialization failed")
                    return@withContext null
                }
            }
            
            if (prompt.isBlank()) {
                Log.w(TAG, "[processUserInput] Empty prompt")
                return@withContext null
            }
            
            // Load parameters from config (or use provided values)
            val params = loadGGUFParams()
            val actualMaxTokens = maxTokens ?: params.maxTokens
            val actualTemperature = temperature ?: params.temperature
            
            Log.d(TAG, "[processUserInput] Generating (maxTokens: $actualMaxTokens, temp: $actualTemperature)")
            Log.d(TAG, "[processUserInput] Prompt: ${prompt.take(100)}...")
            
            // Use advanced generate with all sampling parameters
            val response = generateWithParams(
                modelContext, 
                prompt, 
                actualMaxTokens, 
                actualTemperature,
                params.topK,
                params.topP,
                params.repeatPenalty
            )
            
            if (response.isNullOrBlank()) {
                Log.w(TAG, "[processUserInput] Empty response generated")
                return@withContext null
            }
            
            // Verifier si c'est une erreur
            if (response.startsWith("Error:")) {
                Log.e(TAG, "[processUserInput] JNI returned error: $response")
                return@withContext null
            }
            
            Log.i(TAG, "[processUserInput] SUCCESS - Response: ${response.take(100)}...")
            return@withContext response
            
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "[processUserInput] JNI link error", e)
            isLibraryLoaded = false
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "[processUserInput] Unexpected error", e)
            return@withContext null
        }
    }
    
    /**
     * Efface l'historique de conversation (reset du contexte)
     */
    suspend fun clearConversation(): Boolean = withContext(Dispatchers.IO) {
        if (!isLibraryLoaded || !isInitialized || modelContext == 0L) {
            Log.w(TAG, "[clearConversation] Cannot clear - not ready")
            return@withContext false
        }
        
        try {
            clearConversationNative(modelContext)
            Log.i(TAG, "[clearConversation] SUCCESS")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "[clearConversation] Error", e)
            return@withContext false
        }
    }
    
    /**
     * Libere le modele et son contexte
     */
    fun releaseModel() {
        if (modelContext != 0L && isLibraryLoaded) {
            try {
                Log.d(TAG, "[releaseModel] Freeing context: $modelContext")
                freeModelNative(modelContext)
                modelContext = 0L
                isInitialized = false
                currentModelPath = null
                Log.i(TAG, "[releaseModel] SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "[releaseModel] Error", e)
            }
        }
    }
    
    // Alias pour compatibilite
    fun freeModel() = releaseModel()
    
    /**
     * Verifie si le service est pret
     */
    fun isReady(): Boolean {
        return isLibraryLoaded && isInitialized && modelContext != 0L
    }
    
    /**
     * Obtient le chemin du modele actuellement charge
     */
    fun getCurrentModelPath(): String? = currentModelPath
    
    /**
     * Obtient le nom du modele actuellement charge (sans le chemin)
     */
    fun getCurrentModelName(): String? = currentModelPath?.substringAfterLast("/")?.removeSuffix(".gguf")
    
    /**
     * Obtient le chemin du modele configure (pas forcement charge)
     */
    fun getConfiguredModelPath(): String? {
        return sharedPreferences.getString("local_gguf_model", "gemma3-270m.gguf")
            ?.let { fileName ->
                val modelsDir = File("/storage/emulated/0/ChatAI-Files/models")
                File(modelsDir, fileName).absolutePath
            }
    }
    
    /**
     * Retourne le nombre de contextes actifs (pour debug)
     */
    fun getActiveContextCount(): Int {
        return if (isLibraryLoaded) {
            try {
                getActiveContextCountNative()
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }
    
    // ========== JNI FUNCTIONS ==========
    
    /**
     * Initialise un modele GGUF (version simple)
     * @param modelPath Chemin vers le fichier .gguf
     * @param nThreads Nombre de threads
     * @return ID du contexte (0 si erreur)
     */
    private external fun initModel(modelPath: String, nThreads: Int): Long
    
    /**
     * Initialise un modele GGUF avec parametres avances
     * @param modelPath Chemin vers le fichier .gguf
     * @param nThreads Nombre de threads
     * @param contextSize Taille du contexte
     * @param batchSize Taille des batches
     * @param flashAttention Activer flash attention
     * @return ID du contexte (0 si erreur)
     */
    private external fun initModelWithParams(
        modelPath: String, 
        nThreads: Int,
        contextSize: Int,
        batchSize: Int,
        flashAttention: Boolean
    ): Long
    
    /**
     * Genere une reponse depuis le modele
     * @param contextId ID du contexte
     * @param prompt Prompt utilisateur
     * @param maxTokens Nombre maximum de tokens
     * @param temperature Temperature
     * @return Reponse generee
     */
    private external fun generate(
        contextId: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): String
    
    /**
     * Genere une reponse avec parametres avances
     * @param contextId ID du contexte
     * @param prompt Prompt utilisateur
     * @param maxTokens Nombre maximum de tokens
     * @param temperature Temperature
     * @param topK Top-K sampling
     * @param topP Top-P (nucleus) sampling
     * @param repeatPenalty Penalite de repetition
     * @return Reponse generee
     */
    private external fun generateWithParams(
        contextId: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float
    ): String
    
    /**
     * Libere un modele et son contexte
     * @param contextId ID du contexte a liberer
     */
    private external fun freeModelNative(contextId: Long)
    
    /**
     * Efface l'historique de conversation
     * @param contextId ID du contexte
     */
    private external fun clearConversationNative(contextId: Long)
    
    /**
     * Verifie si la bibliotheque native est chargee (depuis JNI)
     */
    private external fun isLibraryLoadedNative(): Boolean
    
    /**
     * Retourne le nombre de contextes actifs
     */
    private external fun getActiveContextCountNative(): Int
}

