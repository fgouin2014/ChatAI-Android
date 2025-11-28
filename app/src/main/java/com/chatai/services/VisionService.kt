package com.chatai.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.chatai.managers.OnnxVisionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 👁️ VISION SERVICE
 * 
 * Service pour analyser des images via ONNX CLIP local ou Ollama Vision
 * 
 * ⭐ SELON NOS RULES:
 * - Support ONNX local (priorité, 100% offline)
 * - Support Ollama Vision (fallback)
 * - Génération de descriptions d'images
 * - Recherche sémantique image-texte
 * 
 * USAGE:
 * - Description automatique d'images
 * - Classification d'images par texte
 * - Recherche d'images similaires
 */
class VisionService(private val context: Context) {
    
    companion object {
        private const val TAG = "VisionService"
        
        // Descriptions possibles basées sur la similarité CLIP
        private val DESCRIPTION_TEMPLATES = listOf(
            "Je vois une image qui contient {description}.",
            "Cette image montre {description}.",
            "L'image représente {description}.",
            "Je peux identifier {description} dans cette image."
        )
    }
    
    // ⭐ Manager ONNX pour vision locale
    private var onnxVisionManager: OnnxVisionManager? = null
    private var onnxInitialized = false
    
    init {
        // ⭐ Initialiser ONNX Vision Manager au démarrage
        initializeOnnxVision()
    }
    
    /**
     * Initialiser le manager ONNX pour vision locale
     */
    private fun initializeOnnxVision() {
        try {
            if (onnxVisionManager == null) {
                onnxVisionManager = OnnxVisionManager(context)
            }
            
            if (!onnxInitialized) {
                onnxInitialized = onnxVisionManager!!.initialize()
                if (onnxInitialized) {
                    Log.i(TAG, "✅ ONNX Vision initialisé (CLIP, 512 dimensions)")
                } else {
                    Log.w(TAG, "⚠️ ONNX Vision non disponible, fallback Ollama Vision")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation ONNX Vision: ${e.message}", e)
            onnxInitialized = false
        }
    }
    
    /**
     * ⭐ WRAPPER JAVA: Analyser image de manière synchrone (pour appel depuis Java)
     * @param imageBase64 Image en base64
     * @return Description de l'image ou null en cas d'erreur
     */
    fun analyzeImageSync(imageBase64: String): String? {
        return kotlinx.coroutines.runBlocking {
            analyzeImage(imageBase64)
        }
    }
    
    /**
     * Analyser une image et générer une description
     * ⭐ MODIFIÉ: Essaie ONNX local en premier, puis fallback Ollama
     * @param imageBase64 Image en base64
     * @return Description de l'image ou null en cas d'erreur
     */
    private suspend fun analyzeImage(imageBase64: String): String? = withContext(Dispatchers.IO) {
        try {
            if (imageBase64.isBlank()) {
                Log.w(TAG, "Image base64 vide")
                return@withContext null
            }
            
            // ⭐ NOUVEAU: Essayer ONNX local en premier (100% offline)
            if (onnxInitialized && onnxVisionManager?.isReady() == true) {
                try {
                    // Décoder base64 → Bitmap
                    val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    
                    if (bitmap == null) {
                        Log.w(TAG, "Impossible de décoder l'image base64")
                        return@withContext null
                    }
                    
                    // Encoder l'image avec CLIP
                    val imageEmbedding = onnxVisionManager!!.encodeImage(bitmap)
                    if (imageEmbedding != null) {
                        // Générer une description basée sur des templates et la similarité
                        // Pour l'instant, on utilise des descriptions génériques
                        // TODO: Utiliser encodeText() pour comparer avec des descriptions prédéfinies
                        val description = generateDescriptionFromEmbedding(imageEmbedding)
                        Log.d(TAG, "✅ Image analysée via ONNX local")
                        return@withContext description
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur ONNX Vision, fallback: ${e.message}")
                }
            }
            
            // ⭐ Fallback: Ollama Vision (si disponible)
            // TODO: Implémenter appel Ollama Vision API
            Log.w(TAG, "Ollama Vision non implémenté, retour description générique")
            return@withContext "J'ai reçu votre image. L'analyse détaillée nécessite un modèle vision configuré."
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur analyse image: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Générer une description basée sur l'embedding de l'image
     * ⭐ TEMPORAIRE: Utilise des descriptions génériques
     * TODO: Comparer avec des descriptions prédéfinies via encodeText() et computeSimilarity()
     */
    private fun generateDescriptionFromEmbedding(embedding: FloatArray): String {
        // ⭐ TEMPORAIRE: Description générique
        // TODO: Utiliser encodeText() pour encoder des descriptions prédéfinies
        // et computeSimilarity() pour trouver la meilleure correspondance
        val templates = listOf(
            "des éléments visuels intéressants",
            "une composition visuelle",
            "du contenu visuel",
            "une scène ou des objets"
        )
        
        val randomTemplate = DESCRIPTION_TEMPLATES.random()
        val randomDescription = templates.random()
        
        return randomTemplate.replace("{description}", randomDescription)
    }
    
    /**
     * Comparer une image avec un texte (similarité)
     * @param imageBase64 Image en base64
     * @param text Texte à comparer
     * @return Score de similarité [0-1] ou null en cas d'erreur
     */
    suspend fun compareImageWithText(imageBase64: String, text: String): Float? = withContext(Dispatchers.IO) {
        try {
            if (!onnxInitialized || onnxVisionManager?.isReady() != true) {
                Log.w(TAG, "ONNX Vision non disponible pour comparaison")
                return@withContext null
            }
            
            // Décoder image
            val imageBytes = Base64.decode(imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: return@withContext null
            
            // Encoder image et texte
            val imageEmbedding = onnxVisionManager!!.encodeImage(bitmap)
            val textEmbedding = onnxVisionManager!!.encodeText(text)
            
            if (imageEmbedding == null || textEmbedding == null) {
                Log.w(TAG, "Impossible d'encoder image ou texte")
                return@withContext null
            }
            
            // Calculer similarité
            val similarity = onnxVisionManager!!.computeSimilarity(imageEmbedding, textEmbedding)
            Log.d(TAG, "Similarité image-texte: ${String.format("%.3f", similarity)}")
            
            return@withContext similarity
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur comparaison image-texte: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * Vérifier si le service est prêt
     */
    fun isReady(): Boolean = onnxInitialized && onnxVisionManager?.isReady() == true
}

