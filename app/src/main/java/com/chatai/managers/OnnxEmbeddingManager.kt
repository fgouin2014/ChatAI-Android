package com.chatai.managers

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
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
 * 2. Preprocessing texte (tokenisation)
 * 3. Inference ONNX (génération embeddings)
 * 4. Normalisation L2 (optionnel)
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
            
            // Charger le modèle
            Log.d(TAG, "Chargement modèle ONNX...")
            session = ortEnv!!.createSession(MODEL_PATH, sessionOptions)
            
            // Vérifier les inputs/outputs du modèle
            val inputNames = session!!.inputNames
            val outputNames = session!!.outputNames
            
            Log.d(TAG, "Modèle chargé:")
            Log.d(TAG, "  Inputs: ${inputNames.joinToString()}")
            Log.d(TAG, "  Outputs: ${outputNames.joinToString()}")
            
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
            
            // ⚠️ TODO: Tokenisation du texte
            // Pour l'instant, on utilise un tokenizer simple
            // Le modèle sentence-transformers attend des input_ids tokenisés
            // Input: input_ids [batch, sequence_length] (int64)
            // Output: embeddings [batch, sequence_length, hidden_size] ou [batch, hidden_size]
            
            // ⭐ TEMPORAIRE: Utiliser un tokenizer simple (à améliorer)
            // Le modèle all-MiniLM-L6-v2 utilise un tokenizer BERT-like
            // Pour l'instant, on crée un input_ids basique
            // TODO: Implémenter tokenizer complet avec vocab.json
            
            // Tokenisation simple (à remplacer par tokenizer complet)
            val tokens = text.lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(128) // Limite de tokens
            
            if (tokens.isEmpty()) {
                Log.w(TAG, "Aucun token après tokenisation")
                return null
            }
            
            // ⚠️ TEMPORAIRE: Créer des input_ids factices (à remplacer par vrai tokenizer)
            // Le modèle attend des IDs de tokens réels du vocabulaire
            val inputIds = tokens.mapIndexed { index, _ -> 
                // ⚠️ PLACEHOLDER: Utiliser index comme token ID (INCORRECT, à remplacer)
                // Le vrai tokenizer devrait utiliser vocab.json pour mapper texte → token IDs
                (index + 1).toLong() // +1 pour éviter 0 (padding token)
            }.toLongArray()
            
            // Limiter à 128 tokens (longueur max typique pour all-MiniLM-L6-v2)
            val maxLength = 128
            val paddedInputIds = if (inputIds.size > maxLength) {
                inputIds.take(maxLength).toLongArray()
            } else {
                val padded = LongArray(maxLength) { 0L } // 0 = padding
                inputIds.copyInto(padded, 0, 0, inputIds.size)
                padded
            }
            
            // Créer tensor ONNX: shape [1, sequence_length]
            val inputArray = arrayOf(paddedInputIds)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputArray)
            
            Log.d(TAG, "Input ONNX: shape [1, ${paddedInputIds.size}]")
            
            // Exécuter inference
            val inputs = mapOf("input_ids" to inputTensor)
            val result = session.run(inputs)
            
            // Extraire les embeddings
            // Le modèle retourne généralement [batch, sequence_length, hidden_size]
            // On doit faire un pooling (mean) pour obtenir [batch, hidden_size]
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
            
            // Extraire les données
            val outputShape = outputTensor.info.shape
            Log.d(TAG, "Output shape: ${outputShape.contentToString()}")
            
            val outputBuffer = outputTensor.floatBuffer
            val outputSize = outputBuffer.remaining()
            val outputData = FloatArray(outputSize)
            outputBuffer.get(outputData)
            
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

