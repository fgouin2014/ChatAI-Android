package com.chatai.managers

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import com.chatai.tokenizer.BertTokenizer
import java.io.File

/**
 * 🔢 ONNX EMBEDDING MANAGER
 * 
 * Gère la génération d'embeddings via ONNX Runtime (sentence-transformers/all-MiniLM-L6-v2)
 * 
 * AVANTAGES:
 * - Exécution native Android (pas de serveur HTTP)
 * - Latence minimale (10-50ms)
 * - Fonctionne 100% offline
 * - Embeddings 384 dimensions (compatible avec Hugging Face)
 * 
 * ARCHITECTURE:
 * 1. Charger modèle ONNX depuis device
 * 2. Preprocessing texte (tokenisation BERT WordPiece)
 * 3. Inference ONNX (génération embeddings)
 * 4. Pooling (mean) et normalisation L2
 * 
 * FALLBACK:
 * Si ONNX indisponible → Ollama/HuggingFace
 */
class OnnxEmbeddingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OnnxEmbeddingManager"
        
        // Chemins des fichiers ONNX
        private const val BASE_PATH = "/storage/emulated/0/ChatAI-Files/models/embeddings"
        private const val MODEL_PATH = "$BASE_PATH/model.onnx"
        
        // Dimensions d'embedding (all-MiniLM-L6-v2 = 384)
        const val EMBEDDING_DIMENSIONS = 384
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false
    
    // ⭐ NOUVEAU: Tokenizer BERT pour preprocessing
    private val tokenizer = BertTokenizer()
    
    /**
     * Initialiser le modèle ONNX
     * @return true si initialisation réussie, false sinon
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Modèle déjà initialisé")
            return true
        }
        
        try {
            Log.i(TAG, "Initialisation ONNX Embeddings (all-MiniLM-L6-v2)...")
            
            // Vérifier que le modèle existe
            val modelFile = File(MODEL_PATH)
            if (!modelFile.exists()) {
                Log.w(TAG, "Modèle ONNX manquant: $MODEL_PATH")
                Log.w(TAG, "Fallback vers Ollama/HuggingFace recommandé")
                return false
            }
            
            val modelSizeMB = modelFile.length() / (1024.0 * 1024.0)
            Log.d(TAG, "Modèle trouvé: ${modelFile.name} (${String.format("%.2f", modelSizeMB)} MB)")
            
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
            
            // Charger le modèle
            Log.d(TAG, "Chargement modèle ONNX...")
            session = env.createSession(MODEL_PATH, sessionOptions)
            
            // Vérifier la session avant utilisation
            val sess = session
            if (sess == null) {
                Log.e(TAG, "Session est null après chargement")
                return false
            }
            
            // Vérifier les inputs/outputs du modèle
            val inputNames = sess.inputNames
            val outputNames = sess.outputNames
            
            Log.d(TAG, "Modèle chargé:")
            Log.d(TAG, "  Inputs: ${inputNames.joinToString()}")
            Log.d(TAG, "  Outputs: ${outputNames.joinToString()}")
            
            // ⭐ NOUVEAU: Initialiser le tokenizer BERT
            val tokenizerInitialized = tokenizer.initialize()
            if (tokenizerInitialized) {
                Log.i(TAG, "✅ Tokenizer BERT initialisé (${tokenizer.getVocabSize()} tokens)")
            } else {
                Log.w(TAG, "⚠️ Tokenizer BERT non initialisé, embeddings peuvent être incorrects")
            }
            
            isInitialized = true
            Log.i(TAG, "✅ ONNX Embeddings prêt (${EMBEDDING_DIMENSIONS} dimensions)")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation ONNX Embeddings: ${e.message}", e)
            isInitialized = false
            return false
        }
    }
    
    /**
     * Vérifier si le modèle est prêt
     */
    fun isReady(): Boolean = isInitialized && session != null
    
    /**
     * Générer un embedding pour un texte donné
     * @param text Texte à convertir en embedding
     * @return Tableau de floats (384 dimensions) ou null en cas d'erreur
     */
    fun embed(text: String): FloatArray? {
        if (!isReady()) {
            Log.w(TAG, "Modèle non initialisé, impossible de générer embedding")
            return null
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Texte vide, retour null")
            return null
        }
        
        try {
            Log.d(TAG, "Génération embedding pour: \"${text.take(50)}${if (text.length > 50) "..." else ""}\"")
            
            val session = this.session ?: return null
            val ortEnv = this.ortEnv ?: return null
            
            // ⭐ NOUVEAU: Utiliser BertTokenizer pour tokenisation WordPiece
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
            
            Log.d(TAG, "Input ONNX: shape [1, ${inputIds.size}]")
            
            // Exécuter inference
            val inputs = mapOf("input_ids" to inputTensor)
            val result = session.run(inputs)
            
            // Extraire les embeddings
            // Le modèle retourne généralement [batch, sequence_length, hidden_size]
            // On doit faire un pooling (mean) pour obtenir [batch, hidden_size]
            val outputTensor = try {
                // ⭐ FIX: Essayer get(0) directement, gérer IndexOutOfBoundsException si vide
                val output = result.get(0) as? OnnxTensor
                if (output == null) {
                    Log.e(TAG, "Output n'est pas un OnnxTensor")
                    result.close()
                    inputTensor.close()
                    return null
                }
                output
            } catch (e: IndexOutOfBoundsException) {
                Log.e(TAG, "IndexOutOfBoundsException: result est vide ou invalide", e)
                result.close()
                inputTensor.close()
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Erreur extraction output: ${e.message}", e)
                result.close()
                inputTensor.close()
                return null
            }
            
            // Extraire les données
            val outputShape = outputTensor.info.shape
            if (outputShape == null || outputShape.isEmpty()) {
                Log.e(TAG, "Output shape est null ou vide")
                outputTensor.close()
                inputTensor.close()
                result.close()
                return null
            }
            
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}")
            
            val outputBuffer = outputTensor.floatBuffer
            val outputSize = outputBuffer.remaining()
            
            // ⭐ FIX: Vérifier que le buffer a assez de données
            if (outputSize <= 0) {
                Log.e(TAG, "Output buffer est vide (size: $outputSize)")
                outputTensor.close()
                inputTensor.close()
                result.close()
                return null
            }
            
            val outputData = FloatArray(outputSize)
            try {
                outputBuffer.get(outputData)
            } catch (e: java.nio.BufferUnderflowException) {
                Log.e(TAG, "BufferUnderflowException: buffer n'a pas assez de données (size: $outputSize)", e)
                outputTensor.close()
                inputTensor.close()
                result.close()
                return null
            }
            
            // Fermer les tensors
            outputTensor.close()
            inputTensor.close()
            result.close()
            
            // Pooling: moyenne sur la dimension sequence_length
            // Si outputShape = [1, sequence_length, 384], on fait mean sur dim 1
            val embedding = if (outputShape.size == 3) {
                // [batch, sequence_length, hidden_size]
                val batchSize = outputShape[0].toInt()
                val seqLength = outputShape[1].toInt()
                val hiddenSize = outputShape[2].toInt()
                
                // ⭐ FIX: Vérifier que les dimensions sont valides
                if (batchSize <= 0 || seqLength <= 0 || hiddenSize <= 0) {
                    Log.e(TAG, "Dimensions invalides: batch=$batchSize, seq=$seqLength, hidden=$hiddenSize")
                    return null
                }
                
                // ⭐ FIX: Vérifier que outputData a assez d'éléments
                val expectedSize = batchSize * seqLength * hiddenSize
                if (outputData.size < expectedSize) {
                    Log.e(TAG, "Output data size mismatch: attendu $expectedSize, obtenu ${outputData.size}")
                    return null
                }
                
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
                        } else {
                            Log.w(TAG, "Index hors limites dans pooling: idx=$idx, size=${outputData.size}")
                            break
                        }
                    }
                    pooled[i] = if (count > 0) sum / count else 0.0f
                }
                pooled
            } else if (outputShape.size == 2) {
                // [batch, hidden_size] - déjà poolé
                val batchSize = outputShape[0].toInt()
                val hiddenSize = outputShape[1].toInt()
                
                // ⭐ FIX: Vérifier que les dimensions sont valides
                if (batchSize <= 0 || hiddenSize <= 0) {
                    Log.e(TAG, "Dimensions invalides (2D): batch=$batchSize, hidden=$hiddenSize")
                    return null
                }
                
                // ⭐ FIX: Vérifier que outputData a assez d'éléments
                val expectedSize = batchSize * hiddenSize
                if (outputData.size < expectedSize) {
                    Log.e(TAG, "Output data size mismatch (2D): attendu $expectedSize, obtenu ${outputData.size}")
                    return null
                }
                
                if (hiddenSize == EMBEDDING_DIMENSIONS) {
                    outputData.take(EMBEDDING_DIMENSIONS).toFloatArray()
                } else {
                    Log.w(TAG, "Dimension embedding inattendue (2D): $hiddenSize (attendu: $EMBEDDING_DIMENSIONS)")
                    // Prendre les premières EMBEDDING_DIMENSIONS valeurs ou pad avec zéros
                    if (hiddenSize >= EMBEDDING_DIMENSIONS) {
                        outputData.take(EMBEDDING_DIMENSIONS).toFloatArray()
                    } else {
                        // Pad avec zéros si plus petit
                        val padded = FloatArray(EMBEDDING_DIMENSIONS) { 0.0f }
                        outputData.copyInto(padded, 0, 0, minOf(outputData.size, EMBEDDING_DIMENSIONS))
                        padded
                    }
                }
            } else {
                Log.e(TAG, "Shape output inattendu: ${outputShape.contentToString()}")
                return null
            }
            
            // Normalisation L2 (optionnel, mais recommandé pour sentence-transformers)
            val norm = kotlin.math.sqrt(embedding.sumOf { it.toDouble() * it.toDouble() }).toFloat()
            if (norm > 0.0f) {
                for (i in embedding.indices) {
                    embedding[i] /= norm
                }
            }
            
            Log.d(TAG, "✅ Embedding généré: ${embedding.size} dimensions (norme: ${String.format("%.3f", norm)})")
            
            return embedding
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur génération embedding: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Libérer les ressources
     */
    fun shutdown() {
        try {
            session?.close()
            session = null
            ortEnv = null
            isInitialized = false
            Log.i(TAG, "🛑 OnnxEmbeddingManager fermé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur fermeture: ${e.message}", e)
        }
    }
}

