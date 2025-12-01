package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * 🔧 AUTO-CONFIGURATION RAG
 * 
 * Configure automatiquement le RAG au démarrage de l'app:
 * - Détecte si ONNX est disponible
 * - Active RAG automatiquement si possible
 * - Configure les paramètres par défaut
 * - Vérifie et initialise tous les composants
 * 
 * ⭐ SELON NOS RULES: Configuration automatique, non-bloquante
 */
object RAGAutoConfigurator {
    
    private const val TAG = "RAGAutoConfigurator"
    private const val PREFS_NAME = "chatai_ai_config"
    
    /**
     * Configure automatiquement le RAG au démarrage
     * Appelé depuis MainActivity.onCreate() ou BidirectionalBridge.init
     */
    @JvmStatic
    fun autoConfigure(context: Context) {
        try {
            Log.i(TAG, "🔧 Démarrage auto-configuration RAG...")
            
            val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // 1. Vérifier si ONNX est disponible
            val onnxAvailable = checkOnnxAvailable()
            
            // 2. Vérifier configuration actuelle
            val ragEnabled = sharedPreferences.getBoolean("rag_enabled", false)
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            
            Log.d(TAG, "État actuel: rag_enabled=$ragEnabled, use_cloud=$useCloud, onnx_available=$onnxAvailable")
            
            // 3. Auto-configurer selon disponibilité
            if (onnxAvailable) {
                // ONNX disponible → Activer RAG automatiquement
                if (!ragEnabled) {
                    Log.i(TAG, "✅ ONNX disponible, activation automatique de RAG")
                    sharedPreferences.edit()
                        .putBoolean("rag_enabled", true)
                        .putBoolean("rag_use_huggingface", false) // Pas besoin HuggingFace si ONNX disponible
                        .apply()
                    Log.i(TAG, "✅ RAG activé automatiquement (ONNX local)")
                } else {
                    Log.d(TAG, "✅ RAG déjà activé, ONNX disponible")
                }
            } else {
                // ONNX non disponible → Vérifier si on peut utiliser Ollama/HuggingFace
                if (ragEnabled) {
                    Log.w(TAG, "⚠️ RAG activé mais ONNX indisponible, fallback Ollama/HuggingFace")
                    // Garder RAG activé, mais utiliser fallback
                } else {
                    Log.d(TAG, "ℹ️ RAG désactivé, ONNX indisponible")
                }
            }
            
            // 4. Initialiser EmbeddingService pour vérifier disponibilité
            initializeEmbeddingService(context)
            
            Log.i(TAG, "✅ Auto-configuration RAG terminée")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur auto-configuration RAG: ${e.message}", e)
            // ⭐ SELON NOS RULES: Ne pas bloquer si auto-config échoue
        }
    }
    
    /**
     * Vérifie si le modèle ONNX est disponible sur le device
     */
    private fun checkOnnxAvailable(): Boolean {
        return try {
            val modelPath = "/storage/emulated/0/ChatAI-Files/models/embeddings/model.onnx"
            val modelFile = File(modelPath)
            val exists = modelFile.exists()
            
            if (exists) {
                val sizeMB = modelFile.length() / (1024.0 * 1024.0)
                Log.d(TAG, "✅ Modèle ONNX trouvé: $modelPath (${String.format("%.2f", sizeMB)} MB)")
            } else {
                Log.d(TAG, "❌ Modèle ONNX manquant: $modelPath")
            }
            
            exists
        } catch (e: Exception) {
            Log.w(TAG, "Erreur vérification ONNX: ${e.message}")
            false
        }
    }
    
    /**
     * Initialise EmbeddingService pour vérifier disponibilité
     * (non-bloquant, en arrière-plan)
     */
    private fun initializeEmbeddingService(context: Context) {
        try {
            // Créer EmbeddingService pour initialiser ONNX
            val embeddingService = EmbeddingService(context)
            
            // Vérifier disponibilité en arrière-plan (non-bloquant)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val isAvailable = embeddingService.isAvailable()
                    if (isAvailable) {
                        Log.i(TAG, "✅ Service d'embeddings disponible")
                    } else {
                        Log.w(TAG, "⚠️ Service d'embeddings non disponible")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur vérification disponibilité embeddings: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erreur initialisation EmbeddingService: ${e.message}")
        }
    }
    
    /**
     * Force la réinitialisation de la configuration RAG
     * Utile pour forcer une nouvelle détection
     */
    @JvmStatic
    fun resetConfiguration(context: Context) {
        try {
            Log.i(TAG, "🔄 Réinitialisation configuration RAG...")
            val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Réinitialiser les paramètres RAG
            sharedPreferences.edit()
                .remove("rag_enabled")
                .remove("rag_use_huggingface")
                .apply()
            
            // Re-configurer automatiquement
            autoConfigure(context)
            
            Log.i(TAG, "✅ Configuration RAG réinitialisée")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur réinitialisation configuration: ${e.message}", e)
        }
    }
    
    /**
     * Obtient le statut de la configuration RAG
     */
    @JvmStatic
    fun getStatus(context: Context): RAGStatus {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ragEnabled = sharedPreferences.getBoolean("rag_enabled", false)
        val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
        val useHuggingFace = sharedPreferences.getBoolean("rag_use_huggingface", false)
        val onnxAvailable = checkOnnxAvailable()
        
        return RAGStatus(
            ragEnabled = ragEnabled,
            onnxAvailable = onnxAvailable,
            useCloud = useCloud,
            useHuggingFace = useHuggingFace
        )
    }
    
    /**
     * Statut de la configuration RAG
     */
    data class RAGStatus(
        val ragEnabled: Boolean,
        val onnxAvailable: Boolean,
        val useCloud: Boolean,
        val useHuggingFace: Boolean
    ) {
        fun isFullyConfigured(): Boolean {
            return ragEnabled && (onnxAvailable || useHuggingFace || !useCloud)
        }
        
        fun getStatusMessage(): String {
            return when {
                !ragEnabled -> "RAG désactivé"
                onnxAvailable -> "RAG activé (ONNX local, 100% offline)"
                useHuggingFace -> "RAG activé (HuggingFace Cloud)"
                useCloud -> "RAG activé (Ollama Cloud - si supporté)"
                else -> "RAG activé (Ollama Local)"
            }
        }
    }
}

