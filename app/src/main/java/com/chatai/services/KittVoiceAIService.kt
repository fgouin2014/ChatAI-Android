package com.chatai.services

import android.content.Context
import android.util.Log
import com.chatai.SecureConfig
import com.chatai.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/**
 * Service IA dédié à KITT Voice Interface
 * 
 * Pipeline complet:
 * - Wakeword → STT Google → LLM Hugging Face (avec RAG) → TTS Google
 * 
 * ⭐ SELON NOS RULES:
 * - Service indépendant de KittAIService (webapp)
 * - Configuration stockée dans SecureConfig (chiffrée)
 * - Hugging Face comme seul backend LLM
 * - RAG complet avec extraction de faits
 * - Mémoire persistante pour apprentissage
 */
class KittVoiceAIService(private val context: Context) {
    
    companion object {
        private const val TAG = "KittVoiceAIService"
    }
    
    private val secureConfig = SecureConfig(context)
    private val db = ChatAIDatabase.getDatabase(context)
    private val conversationDao = db.conversationDao()
    private val factDao = db.factDao()
    private val huggingFaceService = HuggingFaceService(context)
    private val ragService = KittVoiceRAGService(context)
    
    // Configuration chargée depuis SecureConfig
    private var config: JSONObject? = null
    private var isInitialized = false
    
    /**
     * Initialise le service en chargeant la configuration depuis SecureConfig
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            config = secureConfig.getKittVoiceAIConfigWithDefaults()
            isInitialized = true
            Log.i(TAG, "✅ KittVoiceAIService initialisé avec configuration")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation KittVoiceAIService", e)
            return@withContext false
        }
    }
    
    /**
     * Traite une entrée vocale utilisateur et retourne la réponse
     * 
     * Pipeline:
     * 1. Récupérer contexte RAG
     * 2. Générer réponse via Hugging Face LLM
     * 3. Sauvegarder conversation
     * 4. Générer embedding et extraire faits (background)
     * 
     * @param userInput Texte de l'entrée utilisateur (depuis STT)
     * @return Réponse de l'IA ou null en cas d'erreur
     */
    suspend fun processVoiceInput(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                initialize()
            }
            
            if (userInput.isBlank()) {
                Log.w(TAG, "User input is blank")
                return@withContext null
            }
            
            val config = config ?: secureConfig.getKittVoiceAIConfigWithDefaults()
            
            Log.i(TAG, "🎤 Traitement entrée vocale: ${userInput.take(50)}...")
            
            // Récupérer contexte RAG
            val ragContext = if (config.optBoolean("rag_enabled", true)) {
                ragService.retrieveContext(userInput)
            } else {
                ""
            }
            
            // Construire system prompt
            val systemPromptTemplate = config.optString(
                "system_prompt_template",
                "Tu es KITT, l'assistant IA vocal intelligent. Tu es l'assistant personnel de l'utilisateur. Réponds de manière naturelle et concise en français."
            )
            
            val systemPrompt = if (ragContext.isNotEmpty()) {
                "$systemPromptTemplate\n\n$ragContext"
            } else {
                systemPromptTemplate
            }
            
            // Générer réponse via Hugging Face LLM
            val response = huggingFaceService.generateText(userInput, systemPrompt)
            if (response == null) {
                Log.e(TAG, "❌ Échec génération réponse Hugging Face")
                return@withContext null
            }
            
            Log.i(TAG, "✅ Réponse générée: ${response.take(50)}...")
            
            // Sauvegarder conversation (synchronisé)
            val conversationId = UUID.randomUUID().toString()
            val conversation = ConversationEntity(
                conversationId = conversationId,
                userMessage = userInput,
                aiResponse = response,
                personality = "KITT",
                apiUsed = "huggingface",
                platform = "vocal",
                responseTimeMs = 0 // TODO: Calculer temps de réponse
            )
            
            val dbRowId = conversationDao.insert(conversation)
            Log.d(TAG, "✅ Conversation sauvegardée (ID: $conversationId, DB ID: $dbRowId)")
            
            // Générer embedding et extraire faits en background (non-bloquant)
            kotlinx.coroutines.launch(Dispatchers.IO) {
                try {
                    // Récupérer la conversation avec l'ID DB
                    val savedConversation = conversationDao.getConversationById(dbRowId)
                    if (savedConversation != null) {
                        // Générer embedding
                        ragService.generateAndStoreEmbedding(savedConversation)
                        
                        // Extraire faits
                        ragService.extractFacts(savedConversation)
                        
                        Log.d(TAG, "✅ Embedding et faits générés pour conversation $conversationId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur génération embedding/faits en background", e)
                }
            }
            
            return@withContext response
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur traitement entrée vocale", e)
            return@withContext null
        }
    }
    
    /**
     * Vérifie si le service est configuré et prêt
     */
    fun isConfigured(): Boolean {
        return secureConfig.hasHuggingFaceApiKey() && isInitialized
    }
    
    /**
     * Active ou désactive le wakeword
     * Note: L'implémentation réelle du wakeword doit être gérée par KittFragment
     */
    fun enableWakeword(): Boolean {
        val config = config ?: secureConfig.getKittVoiceAIConfigWithDefaults()
        return config.optBoolean("wakeword_enabled", true)
    }
    
    /**
     * Récupère les mots-clés du wakeword configurés
     */
    fun getWakewordKeywords(): List<String> {
        val config = config ?: secureConfig.getKittVoiceAIConfigWithDefaults()
        val keywordsArray = config.optJSONArray("wakeword_keywords")
        if (keywordsArray != null) {
            val keywords = mutableListOf<String>()
            for (i in 0 until keywordsArray.length()) {
                keywords.add(keywordsArray.getString(i))
            }
            return keywords
        }
        return listOf("hey_kitt") // Par défaut
    }
    
    /**
     * Récupère le modèle LLM configuré
     */
    fun getLLMModel(): String {
        val config = config ?: secureConfig.getKittVoiceAIConfigWithDefaults()
        return config.optString("llm_model", "meta-llama/Llama-3.2-1B-Instruct")
    }
}

