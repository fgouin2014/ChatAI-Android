package com.chatai.managers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import com.chatai.tokenizer.BertTokenizer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 👁️ ONNX VISION MANAGER (CLIP)
 * 
 * Gère l'encodage d'images et de texte via ONNX Runtime (openai/clip-vit-base-patch32)
 * 
 * AVANTAGES:
 * - Exécution native Android (pas de serveur HTTP)
 * - Latence minimale (50-200ms)
 * - Fonctionne 100% offline
 * - Embeddings 512 dimensions (compatible avec CLIP)
 * 
 * ARCHITECTURE CLIP:
 * 1. Vision Model: Encode images (Bitmap → embedding 512D)
 * 2. Text Model: Encode texte (String → embedding 512D)
 * 3. Similarité: Calcul cosine similarity entre image et texte
 * 
 * USAGE:
 * - Recherche sémantique d'images
 * - Description automatique d'images
 * - Classification d'images par texte
 * 
 * FALLBACK:
 * Si ONNX indisponible → Ollama Vision (si disponible)
 */
class OnnxVisionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OnnxVisionManager"
        
        // Chemins des fichiers ONNX CLIP
        private const val BASE_PATH = "/storage/emulated/0/ChatAI-Files/models/vision"
        private const val VISION_MODEL_PATH = "$BASE_PATH/vision_model.onnx"
        private const val TEXT_MODEL_PATH = "$BASE_PATH/text_model.onnx"
        
        // Dimensions d'embedding CLIP (vit-base-patch32 = 512)
        const val EMBEDDING_DIMENSIONS = 512
        
        // Taille d'image attendue par CLIP (vit-base-patch32 = 224x224)
        private const val IMAGE_SIZE = 224
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var visionSession: OrtSession? = null
    private var textSession: OrtSession? = null
    private var isInitialized = false
    
    // ⭐ Tokenizer BERT pour preprocessing texte (CLIP utilise un tokenizer similaire)
    private val tokenizer = BertTokenizer()
    
    /**
     * Initialiser les modèles ONNX CLIP (vision + text)
     * @return true si initialisation réussie, false sinon
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Modèles déjà initialisés")
            return true
        }
        
        try {
            Log.i(TAG, "Initialisation ONNX Vision CLIP (vit-base-patch32)...")
            
            // Vérifier que les modèles existent
            val visionModelFile = File(VISION_MODEL_PATH)
            val textModelFile = File(TEXT_MODEL_PATH)
            
            if (!visionModelFile.exists() || !textModelFile.exists()) {
                Log.w(TAG, "Modèles ONNX CLIP manquants:")
                if (!visionModelFile.exists()) Log.w(TAG, "  - $VISION_MODEL_PATH")
                if (!textModelFile.exists()) Log.w(TAG, "  - $TEXT_MODEL_PATH")
                Log.w(TAG, "Fallback vers Ollama Vision recommandé")
                return false
            }
            
            val visionSizeMB = visionModelFile.length() / (1024.0 * 1024.0)
            val textSizeMB = textModelFile.length() / (1024.0 * 1024.0)
            Log.d(TAG, "Modèles trouvés:")
            Log.d(TAG, "  Vision: ${visionModelFile.name} (${String.format("%.2f", visionSizeMB)} MB)")
            Log.d(TAG, "  Text: ${textModelFile.name} (${String.format("%.2f", textSizeMB)} MB)")
            
            // Initialiser ONNX Runtime
            ortEnv = OrtEnvironment.getEnvironment()
            
            // Options de session
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            // Vérifier ortEnv avant utilisation (éviter crash avec !!)
            val env = ortEnv
            if (env == null) {
                Log.e(TAG, "OrtEnvironment est null")
                return false
            }
            
            // Charger les modèles
            Log.d(TAG, "Chargement vision_model.onnx...")
            visionSession = env.createSession(VISION_MODEL_PATH, sessionOptions)
            
            Log.d(TAG, "Chargement text_model.onnx...")
            textSession = env.createSession(TEXT_MODEL_PATH, sessionOptions)
            
            // Vérifier les sessions avant utilisation
            val vision = visionSession
            if (vision == null) {
                Log.e(TAG, "Vision session est null après chargement")
                return false
            }
            val text = textSession
            if (text == null) {
                Log.e(TAG, "Text session est null après chargement")
                return false
            }
            
            // Vérifier les inputs/outputs
            val visionInputs = vision.inputNames
            val visionOutputs = vision.outputNames
            val textInputs = text.inputNames
            val textOutputs = text.outputNames
            
            Log.d(TAG, "Vision Model:")
            Log.d(TAG, "  Inputs: ${visionInputs.joinToString()}")
            Log.d(TAG, "  Outputs: ${visionOutputs.joinToString()}")
            Log.d(TAG, "Text Model:")
            Log.d(TAG, "  Inputs: ${textInputs.joinToString()}")
            Log.d(TAG, "  Outputs: ${textOutputs.joinToString()}")
            
            // ⭐ Initialiser le tokenizer BERT pour texte
            val tokenizerInitialized = tokenizer.initialize()
            if (tokenizerInitialized) {
                Log.i(TAG, "✅ Tokenizer BERT initialisé (${tokenizer.getVocabSize()} tokens)")
            } else {
                Log.w(TAG, "⚠️ Tokenizer BERT non initialisé, encodage texte peut être incorrect")
            }
            
            isInitialized = true
            Log.i(TAG, "✅ ONNX Vision CLIP prêt (${EMBEDDING_DIMENSIONS} dimensions)")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation ONNX Vision CLIP: ${e.message}", e)
            isInitialized = false
            return false
        }
    }
    
    /**
     * Vérifier si les modèles sont prêts
     */
    fun isReady(): Boolean = isInitialized && visionSession != null && textSession != null
    
    /**
     * Encoder une image en embedding
     * @param bitmap Image à encoder
     * @return Tableau de floats (512 dimensions) ou null en cas d'erreur
     */
    fun encodeImage(bitmap: Bitmap): FloatArray? {
        if (!isReady()) {
            Log.w(TAG, "Modèles non initialisés, impossible d'encoder l'image")
            return null
        }
        
        try {
            Log.d(TAG, "Encodage image: ${bitmap.width}x${bitmap.height}")
            
            val session = this.visionSession ?: return null
            val ortEnv = this.ortEnv ?: return null
            
            // ⭐ Preprocessing image pour CLIP:
            // 1. Redimensionner à 224x224
            // 2. Normaliser [0-255] → [0-1]
            // 3. Normaliser avec mean=[0.485, 0.456, 0.406] et std=[0.229, 0.224, 0.225] (ImageNet)
            // 4. Convertir en tensor [1, 3, 224, 224] (batch, channels, height, width)
            
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true)
            
            // Allouer buffer pour image normalisée [1, 3, 224, 224]
            val imageData = FloatArray(1 * 3 * IMAGE_SIZE * IMAGE_SIZE)
            
            // Mean et std pour normalisation ImageNet
            val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
            val std = floatArrayOf(0.229f, 0.224f, 0.225f)
            
            // Extraire pixels et normaliser
            val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
            resizedBitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
            
            for (y in 0 until IMAGE_SIZE) {
                for (x in 0 until IMAGE_SIZE) {
                    val pixel = pixels[y * IMAGE_SIZE + x]
                    
                    // Extraire R, G, B (0-255)
                    val r = ((pixel shr 16) and 0xFF) / 255.0f
                    val g = ((pixel shr 8) and 0xFF) / 255.0f
                    val b = (pixel and 0xFF) / 255.0f
                    
                    // Normaliser avec mean et std
                    val idx = y * IMAGE_SIZE + x
                    imageData[idx] = (r - mean[0]) / std[0] // R channel
                    imageData[idx + IMAGE_SIZE * IMAGE_SIZE] = (g - mean[1]) / std[1] // G channel
                    imageData[idx + 2 * IMAGE_SIZE * IMAGE_SIZE] = (b - mean[2]) / std[2] // B channel
                }
            }
            
            // Créer tensor ONNX: shape [1, 3, 224, 224]
            // ⚠️ IMPORTANT: OnnxTensor.createTensor attend un array 4D directement
            val imageArray4D = Array(1) { Array(3) { Array(IMAGE_SIZE) { FloatArray(IMAGE_SIZE) } } }
            
            // Copier les données dans la structure 4D
            for (c in 0 until 3) {
                for (y in 0 until IMAGE_SIZE) {
                    for (x in 0 until IMAGE_SIZE) {
                        val srcIdx = c * IMAGE_SIZE * IMAGE_SIZE + y * IMAGE_SIZE + x
                        imageArray4D[0][c][y][x] = imageData[srcIdx]
                    }
                }
            }
            
            val imageTensor = OnnxTensor.createTensor(ortEnv, imageArray4D)
            
            Log.d(TAG, "Input ONNX Vision: shape [1, 3, $IMAGE_SIZE, $IMAGE_SIZE]")
            
            // Exécuter inference
            val inputs = mapOf("pixel_values" to imageTensor)
            val result = session.run(inputs)
            
            // Extraire les embeddings
            val outputTensor = try {
                val output = result.get(0) as? OnnxTensor
                if (output == null) {
                    Log.e(TAG, "Output n'est pas un OnnxTensor")
                    result.close()
                    imageTensor.close()
                    return null
                }
                output
            } catch (e: Exception) {
                Log.e(TAG, "Erreur extraction output: ${e.message}", e)
                result.close()
                imageTensor.close()
                return null
            }
            
            // Extraire les données
            val outputShape = outputTensor.info.shape
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}")
            
            val outputBuffer = outputTensor.floatBuffer
            val outputSize = outputBuffer.remaining()
            val outputData = FloatArray(outputSize)
            outputBuffer.get(outputData)
            
            // Fermer les tensors
            outputTensor.close()
            imageTensor.close()
            result.close()
            
            // Extraire l'embedding (pooling si nécessaire)
            // CLIP retourne généralement [batch, sequence_length, hidden_size] ou [batch, hidden_size]
            val embedding = if (outputShape.size == 3) {
                // [batch, sequence_length, hidden_size] - faire pooling (mean)
                val batchSize = outputShape[0].toInt()
                val seqLength = outputShape[1].toInt()
                val hiddenSize = outputShape[2].toInt()
                
                if (hiddenSize != EMBEDDING_DIMENSIONS) {
                    Log.w(TAG, "Dimension embedding inattendue: $hiddenSize (attendu: $EMBEDDING_DIMENSIONS)")
                }
                
                // Pooling: moyenne sur sequence_length
                val pooled = FloatArray(hiddenSize) { 0.0f }
                for (i in 0 until hiddenSize) {
                    var sum = 0.0f
                    var count = 0
                    for (j in 0 until seqLength) {
                        val idx = j * hiddenSize + i
                        if (idx < outputData.size) {
                            sum += outputData[idx]
                            count++
                        }
                    }
                    pooled[i] = if (count > 0) sum / count else 0.0f
                }
                pooled
            } else if (outputShape.size == 2 && outputShape[1].toInt() == EMBEDDING_DIMENSIONS) {
                // [batch, hidden_size] - déjà poolé
                outputData.take(EMBEDDING_DIMENSIONS).toFloatArray()
            } else {
                Log.e(TAG, "Shape output inattendu: ${outputShape.contentToString()}")
                return null
            }
            
            // Normalisation L2 (standard pour CLIP)
            val norm = kotlin.math.sqrt(embedding.sumOf { it.toDouble() * it.toDouble() }).toFloat()
            if (norm > 0.0f) {
                for (i in embedding.indices) {
                    embedding[i] /= norm
                }
            }
            
            Log.d(TAG, "✅ Image encodée: ${embedding.size} dimensions (norme: ${String.format("%.3f", norm)})")
            
            return embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur encodage image: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Encoder un texte en embedding
     * @param text Texte à encoder
     * @return Tableau de floats (512 dimensions) ou null en cas d'erreur
     */
    fun encodeText(text: String): FloatArray? {
        if (!isReady()) {
            Log.w(TAG, "Modèles non initialisés, impossible d'encoder le texte")
            return null
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Texte vide, retour null")
            return null
        }
        
        try {
            Log.d(TAG, "Encodage texte: \"${text.take(50)}${if (text.length > 50) "..." else ""}\"")
            
            val session = this.textSession ?: return null
            val ortEnv = this.ortEnv ?: return null
            
            // ⭐ Utiliser BertTokenizer pour tokenisation
            if (!tokenizer.isInitialized()) {
                Log.w(TAG, "Tokenizer non initialisé, tentative réinitialisation...")
                if (!tokenizer.initialize()) {
                    Log.e(TAG, "Impossible d'initialiser le tokenizer")
                    return null
                }
            }
            
            // Tokeniser le texte avec BERT WordPiece
            val inputIds = tokenizer.encode(text)
            if (inputIds.isEmpty()) {
                Log.w(TAG, "Tokenisation échouée (aucun token généré)")
                return null
            }
            
            Log.d(TAG, "Texte tokenisé: ${inputIds.size} tokens")
            
            // Créer tensor ONNX: shape [1, sequence_length]
            val inputArray = arrayOf(inputIds)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputArray)
            
            Log.d(TAG, "Input ONNX Text: shape [1, ${inputIds.size}]")
            
            // Exécuter inference
            val inputs = mapOf("input_ids" to inputTensor)
            val result = session.run(inputs)
            
            // Extraire les embeddings (même logique que encodeImage)
            val outputTensor = try {
                val output = result.get(0) as? OnnxTensor
                if (output == null) {
                    Log.e(TAG, "Output n'est pas un OnnxTensor")
                    result.close()
                    inputTensor.close()
                    return null
                }
                output
            } catch (e: Exception) {
                Log.e(TAG, "Erreur extraction output: ${e.message}", e)
                result.close()
                inputTensor.close()
                return null
            }
            
            val outputShape = outputTensor.info.shape
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}")
            
            val outputBuffer = outputTensor.floatBuffer
            val outputSize = outputBuffer.remaining()
            val outputData = FloatArray(outputSize)
            outputBuffer.get(outputData)
            
            outputTensor.close()
            inputTensor.close()
            result.close()
            
            // Pooling et normalisation (identique à encodeImage)
            val embedding = if (outputShape.size == 3) {
                val batchSize = outputShape[0].toInt()
                val seqLength = outputShape[1].toInt()
                val hiddenSize = outputShape[2].toInt()
                
                val pooled = FloatArray(hiddenSize) { 0.0f }
                for (i in 0 until hiddenSize) {
                    var sum = 0.0f
                    var count = 0
                    for (j in 0 until seqLength) {
                        val idx = j * hiddenSize + i
                        if (idx < outputData.size) {
                            sum += outputData[idx]
                            count++
                        }
                    }
                    pooled[i] = if (count > 0) sum / count else 0.0f
                }
                pooled
            } else if (outputShape.size == 2 && outputShape[1].toInt() == EMBEDDING_DIMENSIONS) {
                outputData.take(EMBEDDING_DIMENSIONS).toFloatArray()
            } else {
                Log.e(TAG, "Shape output inattendu: ${outputShape.contentToString()}")
                return null
            }
            
            // Normalisation L2
            val norm = kotlin.math.sqrt(embedding.sumOf { it.toDouble() * it.toDouble() }).toFloat()
            if (norm > 0.0f) {
                for (i in embedding.indices) {
                    embedding[i] /= norm
                }
            }
            
            Log.d(TAG, "✅ Texte encodé: ${embedding.size} dimensions (norme: ${String.format("%.3f", norm)})")
            
            return embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur encodage texte: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Calculer la similarité cosine entre une image et un texte
     * @param imageEmbedding Embedding de l'image
     * @param textEmbedding Embedding du texte
     * @return Score de similarité [0-1] (1 = identique, 0 = différent)
     */
    fun computeSimilarity(imageEmbedding: FloatArray, textEmbedding: FloatArray): Float {
        if (imageEmbedding.size != textEmbedding.size) {
            Log.w(TAG, "Dimensions différentes: image=${imageEmbedding.size}, text=${textEmbedding.size}")
            return 0.0f
        }
        
        // Cosine similarity: dot product (car embeddings normalisés L2)
        var dotProduct = 0.0f
        for (i in imageEmbedding.indices) {
            dotProduct += imageEmbedding[i] * textEmbedding[i]
        }
        
        // Normaliser entre [0, 1] (cosine similarity est [-1, 1])
        return (dotProduct + 1.0f) / 2.0f
    }
    
    /**
     * Libérer les ressources
     */
    fun shutdown() {
        try {
            visionSession?.close()
            textSession?.close()
            visionSession = null
            textSession = null
            ortEnv = null
            isInitialized = false
            Log.i(TAG, "🛑 OnnxVisionManager fermé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur fermeture: ${e.message}", e)
        }
    }
}

