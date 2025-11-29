package com.chatai.managers

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import com.chatai.tokenizer.SentencePieceTokenizer
import java.io.File

/**
 * 🌐 ONNX TRANSLATION MANAGER (MarianMT)
 * 
 * Gère la traduction de texte via ONNX Runtime (Helsinki-NLP/opus-mt-fr-en)
 * 
 * AVANTAGES:
 * - Exécution native Android (pas de serveur HTTP)
 * - Latence minimale (50-200ms)
 * - Fonctionne 100% offline
 * - Traduction français → anglais (opus-mt-fr-en)
 * 
 * ARCHITECTURE MARIANMT:
 * 1. Encoder: Encode le texte source (français) → hidden states
 * 2. Decoder: Génère le texte cible (anglais) de manière autoregressive
 * 3. Tokenizer: SentencePiece pour tokenisation/détokenisation
 * 
 * USAGE:
 * - Traduction automatique des messages
 * - Support multilingue amélioré
 * - Traduction offline sans cloud
 * 
 * FALLBACK:
 * Si ONNX indisponible → Ollama Cloud (si disponible)
 */
class OnnxTranslationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OnnxTranslationManager"
        
        // Chemins des fichiers ONNX MarianMT
        private const val BASE_PATH = "/storage/emulated/0/ChatAI-Files/models/translation"
        private const val ENCODER_MODEL_PATH = "$BASE_PATH/encoder_model.onnx"
        private const val DECODER_MODEL_PATH = "$BASE_PATH/decoder_model.onnx"
        
        // Paramètres de décodage
        private const val MAX_LENGTH = 128 // Longueur maximale de la séquence
        // ⚠️ NOTE: BOS/EOS/PAD IDs sont maintenant détectés automatiquement depuis le vocabulaire
        // via SentencePieceTokenizer.getBosTokenId(), getEosTokenId(), getPadTokenId()
    }
    
    private var ortEnv: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var isInitialized = false
    
    // ✅ CORRIGÉ: Utilise SentencePieceTokenizer pour MarianMT
    // SentencePieceTokenizer charge le vocabulaire réel MarianMT et détecte automatiquement
    // les tokens spéciaux (BOS, EOS, PAD, UNK)
    private val tokenizer = SentencePieceTokenizer()
    
    // IDs des tokens spéciaux (détectés depuis le vocabulaire)
    private var bosTokenId: Int = 2  // Valeur par défaut (sera remplacée après init)
    private var eosTokenId: Int = 3  // Valeur par défaut (sera remplacée après init)
    private var padTokenId: Int = 0  // Valeur par défaut (sera remplacée après init)
    
    /**
     * Initialiser les modèles ONNX MarianMT (encoder + decoder)
     * @return true si initialisation réussie, false sinon
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Modèles déjà initialisés")
            return true
        }
        
        try {
            Log.i(TAG, "Initialisation ONNX Translation MarianMT (opus-mt-fr-en)...")
            
            // Vérifier que les modèles existent
            val encoderModelFile = File(ENCODER_MODEL_PATH)
            val decoderModelFile = File(DECODER_MODEL_PATH)
            
            if (!encoderModelFile.exists() || !decoderModelFile.exists()) {
                Log.w(TAG, "Modèles ONNX MarianMT manquants:")
                if (!encoderModelFile.exists()) Log.w(TAG, "  - $ENCODER_MODEL_PATH")
                if (!decoderModelFile.exists()) Log.w(TAG, "  - $DECODER_MODEL_PATH")
                Log.w(TAG, "Fallback vers Ollama Cloud recommandé")
                return false
            }
            
            val encoderSizeMB = encoderModelFile.length() / (1024.0 * 1024.0)
            val decoderSizeMB = decoderModelFile.length() / (1024.0 * 1024.0)
            Log.d(TAG, "Modèles trouvés:")
            Log.d(TAG, "  Encoder: ${encoderModelFile.name} (${String.format("%.2f", encoderSizeMB)} MB)")
            Log.d(TAG, "  Decoder: ${decoderModelFile.name} (${String.format("%.2f", decoderSizeMB)} MB)")
            
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
            Log.d(TAG, "Chargement encoder_model.onnx...")
            encoderSession = env.createSession(ENCODER_MODEL_PATH, sessionOptions)
            
            Log.d(TAG, "Chargement decoder_model.onnx...")
            decoderSession = env.createSession(DECODER_MODEL_PATH, sessionOptions)
            
            // Vérifier les sessions avant utilisation
            val encoder = encoderSession
            if (encoder == null) {
                Log.e(TAG, "Encoder session est null après chargement")
                return false
            }
            val decoder = decoderSession
            if (decoder == null) {
                Log.e(TAG, "Decoder session est null après chargement")
                return false
            }
            
            // Vérifier les inputs/outputs
            val encoderInputs = encoder.inputNames
            val encoderOutputs = encoder.outputNames
            val decoderInputs = decoder.inputNames
            val decoderOutputs = decoder.outputNames
            
            Log.d(TAG, "Encoder Model:")
            Log.d(TAG, "  Inputs: ${encoderInputs.joinToString()}")
            Log.d(TAG, "  Outputs: ${encoderOutputs.joinToString()}")
            Log.d(TAG, "Decoder Model:")
            Log.d(TAG, "  Inputs: ${decoderInputs.joinToString()}")
            Log.d(TAG, "  Outputs: ${decoderOutputs.joinToString()}")
            
            // ⭐ Initialiser le tokenizer SentencePiece
            val tokenizerInitialized = tokenizer.initialize()
            if (tokenizerInitialized) {
                // Récupérer les IDs des tokens spéciaux depuis le tokenizer
                bosTokenId = tokenizer.getBosTokenId()
                eosTokenId = tokenizer.getEosTokenId()
                padTokenId = tokenizer.getPadTokenId()
                
                Log.i(TAG, "✅ Tokenizer SentencePiece initialisé (${tokenizer.getVocabSize()} tokens)")
                Log.d(TAG, "Tokens spéciaux détectés: BOS=$bosTokenId, EOS=$eosTokenId, PAD=$padTokenId")
            } else {
                Log.w(TAG, "⚠️ Tokenizer SentencePiece non initialisé, traduction peut être incorrecte")
                Log.w(TAG, "⚠️ Vérifier que vocab.json existe dans $BASE_PATH")
            }
            
            isInitialized = true
            Log.i(TAG, "✅ ONNX Translation MarianMT prêt")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation ONNX Translation: ${e.message}", e)
            isInitialized = false
            return false
        }
    }
    
    /**
     * Vérifier si les modèles sont prêts
     */
    fun isReady(): Boolean = isInitialized && encoderSession != null && decoderSession != null
    
    /**
     * Traduire un texte du français vers l'anglais
     * @param text Texte source en français
     * @return Texte traduit en anglais ou null en cas d'erreur
     */
    fun translate(text: String): String? {
        if (!isReady()) {
            Log.w(TAG, "Modèles non initialisés, impossible de traduire")
            return null
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Texte vide, retour null")
            return null
        }
        
        try {
            Log.d(TAG, "Traduction: \"${text.take(50)}${if (text.length > 50) "..." else ""}\"")
            
            val encoderSession = this.encoderSession ?: return null
            val decoderSession = this.decoderSession ?: return null
            val ortEnv = this.ortEnv ?: return null
            
            // ⭐ ÉTAPE 1: Tokeniser le texte source
            if (!tokenizer.isInitialized()) {
                Log.w(TAG, "Tokenizer non initialisé, tentative réinitialisation...")
                if (!tokenizer.initialize()) {
                    Log.e(TAG, "Impossible d'initialiser le tokenizer")
                    return null
                }
            }
            
            val inputIds = tokenizer.encode(text)
            if (inputIds.isEmpty()) {
                Log.w(TAG, "Tokenisation échouée (aucun token généré)")
                return null
            }
            
            Log.d(TAG, "Texte tokenisé: ${inputIds.size} tokens")
            
            // ⭐ ÉTAPE 2: Encoder le texte source
            val inputArray = arrayOf(inputIds)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputArray)
            
            Log.d(TAG, "Input ONNX Encoder: shape [1, ${inputIds.size}]")
            
            val encoderInputs = mapOf("input_ids" to inputTensor)
            val encoderResult = encoderSession.run(encoderInputs)
            
            // Extraire les hidden states de l'encoder
            val encoderOutputTensor = try {
                val output = encoderResult.get(0) as? OnnxTensor
                if (output == null) {
                    Log.e(TAG, "Output encoder n'est pas un OnnxTensor")
                    encoderResult.close()
                    inputTensor.close()
                    return null
                }
                output
            } catch (e: Exception) {
                Log.e(TAG, "Erreur extraction output encoder: ${e.message}", e)
                encoderResult.close()
                inputTensor.close()
                return null
            }
            
            val encoderOutputShape = encoderOutputTensor.info.shape
            Log.d(TAG, "Encoder output shape: ${encoderOutputShape.contentToString()}")
            
            // ⭐ ÉTAPE 3: Décodage autoregressif (génération token par token)
            // MarianMT génère les tokens cible de manière autoregressive
            Log.d(TAG, "Démarrage décodage autoregressif (max $MAX_LENGTH tokens)...")
            
            // Créer attention mask pour l'encoder (tous les tokens sont valides)
            val encoderAttentionMask = LongArray(inputIds.size) { 1L }
            val encoderAttentionMaskTensor = OnnxTensor.createTensor(ortEnv, arrayOf(encoderAttentionMask))
            
            // Initialiser la séquence de décodage avec BOS token (détecté depuis vocabulaire)
            val decodedTokenIds = mutableListOf<Long>()
            decodedTokenIds.add(bosTokenId.toLong())
            
            // Boucle autoregressive: générer un token à la fois
            var iteration = 0
            var currentDecoderInputIds = longArrayOf(bosTokenId.toLong()) // Commencer avec BOS
            
            while (decodedTokenIds.size < MAX_LENGTH && iteration < MAX_LENGTH * 2) {
                iteration++
                
                // Créer input tensor pour le decoder: [1, sequence_length]
                val decoderInputIdsArray = arrayOf(currentDecoderInputIds)
                val decoderInputIdsTensor = OnnxTensor.createTensor(ortEnv, decoderInputIdsArray)
                
                // Créer attention mask pour le decoder (tous les tokens valides)
                val decoderAttentionMask = LongArray(currentDecoderInputIds.size) { 1L }
                val decoderAttentionMaskTensor = OnnxTensor.createTensor(ortEnv, arrayOf(decoderAttentionMask))
                
                // Préparer les inputs pour le decoder
                // ⚠️ IMPORTANT: Le type doit être Map<String, OnnxTensorLike> pour être compatible
                val decoderInputs = mutableMapOf<String, OnnxTensorLike>()
                decoderInputs["input_ids"] = decoderInputIdsTensor
                decoderInputs["encoder_hidden_states"] = encoderOutputTensor
                decoderInputs["encoder_attention_mask"] = encoderAttentionMaskTensor
                
                // Appeler le decoder
                val decoderResult = decoderSession.run(decoderInputs)
                
                // Extraire les logits (probabilités pour chaque token)
                val logitsTensor = try {
                    val output = decoderResult.get(0) as? OnnxTensor
                    if (output == null) {
                        Log.e(TAG, "Output decoder n'est pas un OnnxTensor à l'itération $iteration")
                        decoderResult.close()
                        decoderInputIdsTensor.close()
                        decoderAttentionMaskTensor.close()
                        break
                    }
                    output
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur extraction output decoder itération $iteration: ${e.message}", e)
                    decoderResult.close()
                    decoderInputIdsTensor.close()
                    decoderAttentionMaskTensor.close()
                    break
                }
                
                // Extraire les logits: shape [batch, sequence_length, vocab_size]
                val logitsShape = logitsTensor.info.shape
                if (logitsShape.size < 3) {
                    Log.e(TAG, "Shape logits invalide: ${logitsShape.contentToString()}")
                    logitsTensor.close()
                    decoderResult.close()
                    decoderInputIdsTensor.close()
                    decoderAttentionMaskTensor.close()
                    break
                }
                
                val batchSize = logitsShape[0].toInt()
                val seqLen = logitsShape[1].toInt()
                val vocabSize = logitsShape[2].toInt()
                
                // Extraire les logits du dernier token généré (position seqLen - 1)
                val logitsBuffer = logitsTensor.floatBuffer
                val logitsData = FloatArray(logitsBuffer.remaining())
                logitsBuffer.get(logitsData)
                
                // Fermer les tensors immédiatement après extraction
                logitsTensor.close()
                decoderResult.close()
                decoderInputIdsTensor.close()
                decoderAttentionMaskTensor.close()
                
                // Extraire les logits du dernier token (dernière position de la séquence)
                val lastTokenLogitsStart = (seqLen - 1) * vocabSize
                val lastTokenLogitsEnd = lastTokenLogitsStart + vocabSize
                if (lastTokenLogitsEnd > logitsData.size) {
                    Log.e(TAG, "Index hors limites pour logits: $lastTokenLogitsEnd > ${logitsData.size}")
                    break
                }
                
                val lastTokenLogits = logitsData.sliceArray(lastTokenLogitsStart until lastTokenLogitsEnd)
                
                // Trouver le token avec la probabilité la plus élevée (greedy decoding)
                var maxLogit = Float.NEGATIVE_INFINITY
                var nextTokenId = eosTokenId.toLong() // Valeur par défaut = EOS
                
                for (i in lastTokenLogits.indices) {
                    if (lastTokenLogits[i] > maxLogit) {
                        maxLogit = lastTokenLogits[i]
                        nextTokenId = i.toLong()
                    }
                }
                
                // Vérifier si on a atteint la fin (EOS token détecté depuis vocabulaire)
                if (nextTokenId == eosTokenId.toLong()) {
                    Log.d(TAG, "EOS token ($eosTokenId) détecté à l'itération $iteration, arrêt du décodage")
                    decodedTokenIds.add(nextTokenId)
                    break
                }
                
                // Ajouter le nouveau token à la séquence
                decodedTokenIds.add(nextTokenId)
                
                // Mettre à jour currentDecoderInputIds pour la prochaine itération
                currentDecoderInputIds = decodedTokenIds.toLongArray()
                
                // Log tous les 10 tokens
                if (decodedTokenIds.size % 10 == 0) {
                    Log.d(TAG, "Progression décodage: ${decodedTokenIds.size} tokens générés (itération $iteration)")
                }
            }
            
            Log.d(TAG, "✅ Décodage autoregressif terminé: ${decodedTokenIds.size} tokens générés en $iteration itérations")
            
            // Fermer les tensors encoder
            encoderOutputTensor.close()
            inputTensor.close()
            encoderResult.close()
            encoderAttentionMaskTensor.close()
            
            // ⭐ ÉTAPE 4: Détokeniser les tokens générés en texte avec SentencePieceTokenizer
            if (decodedTokenIds.isEmpty() || (decodedTokenIds.size == 1 && decodedTokenIds[0] == bosTokenId.toLong())) {
                Log.w(TAG, "Aucun token généré (uniquement BOS)")
                return null
            }
            
            // Retirer BOS et EOS tokens pour la détokenisation
            val tokensToDecode = decodedTokenIds.filter { 
                it != bosTokenId.toLong() && it != eosTokenId.toLong() 
            }
            
            if (tokensToDecode.isEmpty()) {
                Log.w(TAG, "Aucun token valide après filtrage BOS/EOS")
                return null
            }
            
            // ✅ Utiliser SentencePieceTokenizer pour décoder
            val tokenIdsArray = tokensToDecode.toLongArray()
            val translatedText = tokenizer.decode(tokenIdsArray)
            
            if (translatedText.isBlank()) {
                Log.w(TAG, "Détokenisation retourne texte vide")
                return null
            }
            
            Log.i(TAG, "✅ Traduction réussie: \"${translatedText.take(100)}${if (translatedText.length > 100) "..." else ""}\"")
            
            return translatedText
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur traduction: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Libérer les ressources
     */
    fun shutdown() {
        try {
            encoderSession?.close()
            decoderSession?.close()
            encoderSession = null
            decoderSession = null
            ortEnv = null
            isInitialized = false
            Log.i(TAG, "🛑 OnnxTranslationManager fermé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur fermeture: ${e.message}", e)
        }
    }
}

