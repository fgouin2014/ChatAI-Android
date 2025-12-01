package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.chatai.KeyringManager
import com.chatai.managers.OnnxVisionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

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
        
        // Ollama Cloud API
        // ⭐ REFACTORISÉ: URL centralisée dans ApiConfig
        private val OLLAMA_CLOUD_URL = com.chatai.config.ApiConfig.OLLAMA_CLOUD_CHAT
        
        // Modèles vision Ollama recommandés
        private val VISION_MODELS = listOf("llava", "bakllava", "llava-phi3")
        
        // Descriptions possibles basées sur la similarité CLIP
        private val DESCRIPTION_TEMPLATES = listOf(
            "Je vois une image qui contient {description}.",
            "Cette image montre {description}.",
            "L'image représente {description}.",
            "Je peux identifier {description} dans cette image."
        )
        
        // Descriptions prédéfinies pour comparaison embeddings
        private val PREDEFINED_DESCRIPTIONS = listOf(
            "une personne",
            "un animal",
            "un véhicule",
            "un bâtiment",
            "de la nourriture",
            "un paysage",
            "du texte",
            "un objet",
            "une scène d'intérieur",
            "une scène d'extérieur",
            "un écran d'ordinateur",
            "une photo",
            "un dessin",
            "un diagramme"
        )
    }
    
    // ⭐ Manager ONNX pour vision locale
    private var onnxVisionManager: OnnxVisionManager? = null
    private var onnxInitialized = false
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val keyring: KeyringManager = KeyringManager.getInstance(context)
    
    // Client HTTP pour Ollama Cloud
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // Vision peut prendre plus de temps
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    
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
                        // ⭐ AMÉLIORÉ: Utiliser encodeText() pour comparer avec des descriptions prédéfinies
                        val description = generateDescriptionFromEmbedding(imageEmbedding)
                        Log.d(TAG, "✅ Image analysée via ONNX local")
                        return@withContext description
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur ONNX Vision, fallback: ${e.message}")
                }
            }
            
            // ⭐ Fallback: Ollama Vision (si disponible)
            val useCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
            if (useCloud) {
                val description = tryOllamaVision(imageBase64)
                if (description != null) {
                    Log.d(TAG, "✅ Image analysée via Ollama Cloud Vision")
                    return@withContext description
                }
            }
            
            Log.w(TAG, "Aucun service vision disponible, retour description générique")
            return@withContext "J'ai reçu votre image. L'analyse détaillée nécessite un modèle vision configuré."
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur analyse image: ${e.message}", e)
            return@withContext null
        }
    }
    
    /**
     * ⭐ AMÉLIORÉ: Générer une description basée sur l'embedding de l'image
     * Compare avec des descriptions prédéfinies via encodeText() et computeSimilarity()
     */
    private fun generateDescriptionFromEmbedding(embedding: FloatArray): String {
        if (onnxVisionManager?.isReady() != true) {
            // Fallback si ONNX pas prêt
            val randomTemplate = DESCRIPTION_TEMPLATES.random()
            return randomTemplate.replace("{description}", "du contenu visuel")
        }
        
        try {
            // Encoder toutes les descriptions prédéfinies
            val descriptionEmbeddings = PREDEFINED_DESCRIPTIONS.mapNotNull { desc ->
                val textEmbedding = onnxVisionManager!!.encodeText(desc)
                if (textEmbedding != null) {
                    Pair(desc, textEmbedding)
                } else {
                    null
                }
            }
            
            if (descriptionEmbeddings.isEmpty()) {
                Log.w(TAG, "Aucune description prédéfinie encodée, fallback générique")
                val randomTemplate = DESCRIPTION_TEMPLATES.random()
                return randomTemplate.replace("{description}", "du contenu visuel")
            }
            
            // Trouver la description la plus similaire
            var bestMatch = ""
            var bestSimilarity = -1f
            
            descriptionEmbeddings.forEach { (desc, textEmbedding) ->
                val similarity = onnxVisionManager!!.computeSimilarity(embedding, textEmbedding)
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestMatch = desc
                }
            }
            
            Log.d(TAG, "Meilleure correspondance: '$bestMatch' (similarité: ${String.format("%.3f", bestSimilarity)})")
            
            // Utiliser la meilleure correspondance
            val template = DESCRIPTION_TEMPLATES.random()
            return template.replace("{description}", bestMatch)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur comparaison embeddings: ${e.message}", e)
            // Fallback générique
            val randomTemplate = DESCRIPTION_TEMPLATES.random()
            return randomTemplate.replace("{description}", "du contenu visuel")
        }
    }
    
    /**
     * ⭐ NOUVEAU: Essayer Ollama Cloud Vision API
     */
    private suspend fun tryOllamaVision(imageBase64: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = keyring.getApiKey("ollama")?.trim()
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "Ollama Cloud API key not configured")
                return@withContext null
            }
            
            // Récupérer le modèle vision configuré (ou défaut)
            val visionModel = sharedPreferences.getString("ollama_vision_model", VISION_MODELS.first())
                ?: VISION_MODELS.first()
            
            Log.d(TAG, "Trying Ollama Cloud Vision with model: $visionModel")
            
            // Construire la requête Ollama Vision
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", "Décris cette image en détail en français.")
                    })
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$imageBase64")
                        })
                    })
                })
            })
            
            val requestBody = JSONObject().apply {
                put("model", visionModel)
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
                Log.w(TAG, "Ollama Vision request failed: HTTP ${response.code}")
                if (errorBody != null) {
                    Log.w(TAG, "Error body: ${errorBody.take(200)}")
                }
                return@withContext null
            }
            
            val responseBody = response.body?.string()
            if (responseBody == null) {
                Log.w(TAG, "Ollama Vision response body is null")
                return@withContext null
            }
            
            // Parser la réponse Ollama
            val jsonResponse = JSONObject(responseBody)
            val message = jsonResponse.optJSONObject("message")
            val content = message?.optString("content")
            
            if (content != null && content.isNotBlank()) {
                Log.d(TAG, "✅ Ollama Vision description: ${content.take(100)}...")
                return@withContext content.trim()
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Ollama Vision: ${e.message}", e)
            return@withContext null
        }
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

