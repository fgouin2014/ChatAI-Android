package com.chatai.services

import android.content.Context
import android.util.Log
import com.chatai.managers.OnnxTranslationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    }
    
    // ⭐ Manager ONNX pour traduction locale
    private var onnxTranslationManager: OnnxTranslationManager? = null
    private var onnxInitialized = false
    
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
            // TODO: Implémenter appel Ollama Cloud Translation API
            Log.w(TAG, "Ollama Cloud Translation non implémenté, retour null")
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
     * Vérifier si le service est prêt
     */
    fun isReady(): Boolean = onnxInitialized && onnxTranslationManager?.isReady() == true
}

