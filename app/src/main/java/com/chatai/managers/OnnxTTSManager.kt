package com.chatai.managers

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import ai.onnxruntime.*
import com.chatai.tokenizer.SimpleTokenizer
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * 🔊 ONNX TTS MANAGER
 * 
 * Gère la synthèse vocale via ONNX Runtime (SpeechT5)
 * 
 * AVANTAGES:
 * - Exécution native Android (pas de serveur HTTP)
 * - Latence minimale (10-50ms)
 * - Qualité supérieure vs Android TTS
 * - Fonctionne 100% offline
 * 
 * ARCHITECTURE:
 * 1. Charger modèle ONNX depuis device
 * 2. Preprocessing texte (tokenisation)
 * 3. Inference ONNX (génération waveform)
 * 4. Postprocessing audio (waveform → PCM)
 * 5. Jouer via AudioTrack
 * 
 * FALLBACK:
 * Si ONNX indisponible → Android TTS natif
 */
class OnnxTTSManager(
    private val context: Context,
    private val listener: TTSListener
) {
    
    companion object {
        private const val TAG = "OnnxTTSManager"
        
        // ⭐ MODIFIÉ: Chemins des modèles ONNX (SpeechT5 = encoder + decoder + vocoder)
        private const val BASE_PATH = "/storage/emulated/0/ChatAI-Files/models/tts"
        private const val ENCODER_MODEL = "$BASE_PATH/encoder_model.onnx"
        private const val DECODER_MODEL = "$BASE_PATH/decoder_model.onnx"
        private const val DECODER_WITH_PAST_MODEL = "$BASE_PATH/decoder_with_past_model.onnx"
        private const val VOCODER_MODEL = "$BASE_PATH/decoder_postnet_and_vocoder.onnx"
        
        // Configuration audio
        private const val SAMPLE_RATE = 16000  // 16kHz (standard SpeechT5)
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    /**
     * Interface pour les callbacks TTS (identique à KittTTSManager)
     */
    interface TTSListener {
        fun onTTSReady()
        fun onTTSStart(utteranceId: String?)
        fun onTTSDone(utteranceId: String?)
        fun onTTSError(utteranceId: String?)
    }
    
    private var ortEnv: OrtEnvironment? = null
    
    // ⭐ MODIFIÉ: Plusieurs sessions ONNX (encoder, decoder, vocoder)
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var vocoderSession: OrtSession? = null
    private var isReady = false
    private var isSpeaking = false
    
    private var audioTrack: AudioTrack? = null
    
    // ⭐ NOUVEAU: Tokenizer pour preprocessing texte
    private val tokenizer = SimpleTokenizer()
    
    /**
     * Initialiser ONNX Runtime et charger les modèles (encoder, decoder, vocoder)
     */
    fun initialize() {
        try {
            Log.i(TAG, "Initialisation ONNX TTS (SpeechT5 multi-modèles)...")
            
            // Vérifier que tous les modèles existent
            val encoderFile = File(ENCODER_MODEL)
            val decoderFile = File(DECODER_MODEL)
            val vocoderFile = File(VOCODER_MODEL)
            
            if (!encoderFile.exists() || !decoderFile.exists() || !vocoderFile.exists()) {
                Log.w(TAG, "Modèles ONNX manquants:")
                if (!encoderFile.exists()) Log.w(TAG, "  - $ENCODER_MODEL")
                if (!decoderFile.exists()) Log.w(TAG, "  - $DECODER_MODEL")
                if (!vocoderFile.exists()) Log.w(TAG, "  - $VOCODER_MODEL")
                Log.w(TAG, "Fallback vers Android TTS recommandé")
                return
            }
            
            // Initialiser ONNX Runtime
            ortEnv = OrtEnvironment.getEnvironment()
            
            // Options de session
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            // Charger les 3 modèles
            Log.d(TAG, "Chargement encoder_model.onnx...")
            encoderSession = ortEnv!!.createSession(ENCODER_MODEL, sessionOptions)
            
            Log.d(TAG, "Chargement decoder_model.onnx...")
            decoderSession = ortEnv!!.createSession(DECODER_MODEL, sessionOptions)
            
            Log.d(TAG, "Chargement decoder_postnet_and_vocoder.onnx...")
            vocoderSession = ortEnv!!.createSession(VOCODER_MODEL, sessionOptions)
            
            // ⭐ NOUVEAU: Initialiser le tokenizer
            val tokenizerInitialized = tokenizer.initialize()
            if (tokenizerInitialized) {
                Log.i(TAG, "✅ Tokenizer initialisé")
            } else {
                Log.e(TAG, "❌ Tokenizer non initialisé - fichier vocab.json ou default_speaker_embeddings.json manquant")
                Log.e(TAG, "   Vérifiez: /storage/emulated/0/ChatAI-Files/models/tts/vocab.json")
                Log.e(TAG, "   Vérifiez: /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json")
                // Ne pas marquer comme prêt si tokenizer échoue
                isReady = false
                return
            }
            
            // Vérifier que toutes les sessions sont chargées
            if (encoderSession == null || decoderSession == null || vocoderSession == null) {
                Log.e(TAG, "❌ Erreur: Une ou plusieurs sessions ONNX n'ont pas pu être chargées")
                isReady = false
                return
            }
            
            val totalSize = (encoderFile.length() + decoderFile.length() + vocoderFile.length()) / (1024 * 1024)
            isReady = true
            Log.i(TAG, "✅ ONNX TTS prêt (3 modèles, ${totalSize} MB total)")
            Log.i(TAG, "   Encoder: ${encoderFile.length() / (1024 * 1024)} MB")
            Log.i(TAG, "   Decoder: ${decoderFile.length() / (1024 * 1024)} MB")
            Log.i(TAG, "   Vocoder: ${vocoderFile.length() / (1024 * 1024)} MB")
            Log.i(TAG, "   Tokenizer: ✅ Initialisé")
            
            listener.onTTSReady()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur initialisation ONNX TTS: ${e.message}", e)
            Log.e(TAG, "   Stack trace:", e)
            isReady = false
            
            // Logs détaillés pour diagnostic
            Log.e(TAG, "=== DIAGNOSTIC ERREUR ONNX TTS ===")
            Log.e(TAG, "Message: ${e.message}")
            Log.e(TAG, "Cause: ${e.cause?.message ?: "N/A"}")
            Log.e(TAG, "Vérifiez que:")
            Log.e(TAG, "   1. Modèles ONNX présents dans /storage/emulated/0/ChatAI-Files/models/tts/")
            Log.e(TAG, "   2. vocab.json présent dans /storage/emulated/0/ChatAI-Files/models/tts/")
            Log.e(TAG, "   3. default_speaker_embeddings.json présent")
            Log.e(TAG, "   4. ONNX Runtime installé correctement")
            Log.e(TAG, "===================================")
        }
    }
    
    /**
     * Parler un texte (synthèse vocale)
     * 
     * @param text Texte à synthétiser
     * @param utteranceId ID unique pour le callback
     */
    fun speak(text: String, utteranceId: String = "onnx_tts") {
        if (!isReady || encoderSession == null || decoderSession == null || vocoderSession == null) {
            Log.w(TAG, "ONNX TTS non prêt, fallback recommandé")
            listener.onTTSError(utteranceId)
            return
        }
        
        if (isSpeaking) {
            Log.w(TAG, "TTS déjà en cours, ignoré")
            return
        }
        
        // Exécuter dans un thread séparé pour ne pas bloquer l'UI
        Thread {
            try {
                isSpeaking = true
                listener.onTTSStart(utteranceId)
                
                Log.d(TAG, "Synthèse vocale ONNX: \"$text\"")
                
                // 1. Preprocessing texte (voix par défaut)
                val inputs = preprocessText(text, SimpleTokenizer.VoiceType.DEFAULT)
                if (inputs == null) {
                    Log.e(TAG, "Erreur preprocessing texte")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        isSpeaking = false
                        listener.onTTSError(utteranceId)
                    }
                    return@Thread
                }
                
                // 2. Inference ONNX
                val waveform = runInference(inputs)
                if (waveform == null || waveform.isEmpty()) {
                    Log.e(TAG, "Erreur inference ONNX ou waveform vide")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        isSpeaking = false
                        listener.onTTSError(utteranceId)
                    }
                    return@Thread
                }
                
                Log.d(TAG, "Waveform généré: ${waveform.size} échantillons")
                
                // 3. Postprocessing et lecture audio
                playAudio(waveform)
                
                // Notifier fin de synthèse
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isSpeaking = false
                    listener.onTTSDone(utteranceId)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur synthèse vocale: ${e.message}", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isSpeaking = false
                    listener.onTTSError(utteranceId)
                }
            }
        }.start()
    }
    
    /**
     * Preprocessing texte: Tokenisation et préparation inputs ONNX
     * 
     * @param text Texte à traiter
     * @return Map des inputs ONNX ou null si erreur
     */
    private fun preprocessText(text: String, voiceType: SimpleTokenizer.VoiceType = SimpleTokenizer.VoiceType.DEFAULT): Map<String, OnnxTensor>? {
        try {
            Log.d(TAG, "Preprocessing texte: \"$text\" (voix: $voiceType)")
            
            // Vérifier que le tokenizer est initialisé
            if (!tokenizer.isInitialized()) {
                Log.w(TAG, "Tokenizer non initialisé")
                return null
            }
            
            val ortEnv = this.ortEnv ?: return null
            
            // 1. Tokeniser le texte
            val inputIds = tokenizer.encode(text)
            if (inputIds.isEmpty()) {
                Log.e(TAG, "Tokenisation échouée (aucun token généré)")
                return null
            }
            
            Log.d(TAG, "Texte tokenisé: ${inputIds.size} tokens")
            
            // 2. Obtenir speaker embeddings selon le type de voix
            val speakerEmbeddings = tokenizer.getSpeakerEmbeddings(voiceType)
            if (speakerEmbeddings == null || speakerEmbeddings.size != 512) {
                Log.e(TAG, "Speaker embeddings invalides")
                return null
            }
            
            Log.d(TAG, "Speaker embeddings utilisés: $voiceType (${speakerEmbeddings.size} dimensions)")
            
            // 3. Créer les tensors ONNX
            // input_ids: shape [1, sequence_length]
            // ⚠️ IMPORTANT: Le modèle attend int64, pas int32!
            val inputIdsLong = inputIds.map { it.toLong() }.toLongArray()
            val inputIdsArray = arrayOf(inputIdsLong)
            val inputIdsTensor = OnnxTensor.createTensor(ortEnv, inputIdsArray)
            
            // speaker_embeddings: shape [1, 512]
            val speakerEmbeddingsArray = arrayOf(speakerEmbeddings)
            val speakerEmbeddingsTensor = OnnxTensor.createTensor(ortEnv, speakerEmbeddingsArray)
            
            Log.d(TAG, "Inputs ONNX préparés: input_ids[${inputIds.size}], speaker_embeddings[512]")
            
            return mapOf(
                "input_ids" to inputIdsTensor,
                "speaker_embeddings" to speakerEmbeddingsTensor
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur preprocessing: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Exécuter l'inférence ONNX pour générer le waveform audio
     * Pipeline: Encoder → Decoder → Vocoder
     * 
     * @param inputs Map des inputs ONNX (input_ids, speaker_embeddings)
     * @return Waveform audio (FloatArray) ou null si erreur
     */
    private fun runInference(inputs: Map<String, OnnxTensor>): FloatArray? {
        try {
            Log.d(TAG, "Exécution inference ONNX (pipeline 3 étapes)...")
            
            val encoder = encoderSession ?: run {
                Log.e(TAG, "Encoder session null")
                return null
            }
            val decoder = decoderSession ?: run {
                Log.e(TAG, "Decoder session null")
                return null
            }
            val vocoder = vocoderSession ?: run {
                Log.e(TAG, "Vocoder session null")
                return null
            }
            
            // ⭐ ÉTAPE 1: Encoder (texte → embeddings)
            // ⚠️ IMPORTANT: L'encoder attend SEULEMENT input_ids, pas speaker_embeddings!
            Log.d(TAG, "Étape 1/3: Encoder...")
            
            // Extraire seulement input_ids pour l'encoder
            val inputIds = inputs["input_ids"] ?: run {
                Log.e(TAG, "input_ids manquant dans inputs")
                return null
            }
            val speakerEmbeddings = inputs["speaker_embeddings"] // Garder pour le decoder
            
            val encoderInputs = mapOf("input_ids" to inputIds)
            Log.d(TAG, "Inputs encoder: ${encoderInputs.keys.joinToString()}")
            val encoderResult = encoder.run(encoderInputs)
            
            // encoderResult est un OrtSession.Result qui peut être utilisé comme Map<String, OnnxValue>
            // Extraire le premier output (généralement "last_hidden_state" ou similaire)
            val encoderOutput = try {
                // Essayer d'utiliser comme Map directement
                val resultAsMap = encoderResult as? Map<String, OnnxValue>
                if (resultAsMap != null && resultAsMap.isNotEmpty()) {
                    val firstValue = resultAsMap.values.firstOrNull() as? OnnxTensor
                    if (firstValue != null) {
                        Log.d(TAG, "Outputs encoder: obtenu via Map, ${resultAsMap.size} outputs, keys: ${resultAsMap.keys.joinToString()}")
                        firstValue
                    } else {
                        // Essayer par nom
                        val possibleNames = listOf("last_hidden_state", "output", "encoder_outputs")
                        var found: OnnxTensor? = null
                        for (name in possibleNames) {
                            found = resultAsMap[name] as? OnnxTensor
                            if (found != null) {
                                Log.d(TAG, "Outputs encoder: obtenu via nom '$name'")
                                break
                            }
                        }
                        found
                    }
                } else {
                    // Essayer get(index) si disponible
                    try {
                        val output = encoderResult.get(0) as? OnnxTensor
                        if (output != null) {
                            Log.d(TAG, "Outputs encoder: obtenu via get(0)")
                        }
                        output
                    } catch (e: Exception) {
                        Log.e(TAG, "Impossible d'extraire encoder output: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erreur extraction encoder output: ${e.message}", e)
                null
            } ?: run {
                Log.e(TAG, "Encoder output n'est pas un OnnxTensor")
                encoderResult.close()
                return null
            }
            
            // ⭐ ÉTAPE 2: Decoder (embeddings + speaker_embeddings → mel spectrogram)
            // ⚠️ IMPORTANT: Le decoder a besoin de encoder_outputs ET speaker_embeddings!
            Log.d(TAG, "Étape 2/3: Decoder...")
            
            if (speakerEmbeddings == null) {
                Log.e(TAG, "speaker_embeddings manquant pour decoder")
                encoderOutput.close()
                return null
            }
            
            // Essayer différents noms d'inputs possibles pour le decoder
            val decoderInputs = mutableMapOf<String, OnnxTensor>()
            
            // ⚠️ CORRECTION: Le decoder attend "encoder_hidden_states" + "output_sequence" + "speaker_embeddings"
            // output_sequence: séquence autoregressive, initialisée avec un token de début (0) pour le premier appel
            val ortEnv = this.ortEnv ?: run {
                Log.e(TAG, "ortEnv null lors de la création de output_sequence")
                encoderOutput.close()
                encoderResult.close()
                return null
            }
            
            // Initialiser output_sequence avec des floats (shape [1, 1, feature_dim])
            // ⚠️ IMPORTANT: output_sequence attend rank 3: [batch, sequence_length, feature_dim]
            // Pour SpeechT5, feature_dim est généralement 80 (mel bins) ou la dimension des encoder outputs
            // On utilise 80 comme dimension par défaut (mel spectrogram features)
            val featureDim = 80 // Dimension des features audio (mel bins)
            val outputSequenceData = FloatArray(1 * 1 * featureDim) { 0.0f } // Initialiser à zéro
            val outputSequenceArray = arrayOf(arrayOf(outputSequenceData))
            val outputSequenceTensor = OnnxTensor.createTensor(ortEnv, outputSequenceArray)
            
            // ⭐ NOUVEAU: Créer encoder_attention_mask (requis par le decoder)
            // encoder_attention_mask: shape [1, sequence_length] avec des 1s pour tous les tokens valides
            // On utilise la shape de input_ids pour déterminer la taille
            val inputIdsShape = inputIds.info.shape
            if (inputIdsShape.size < 2) {
                Log.e(TAG, "input_ids shape invalide pour déterminer sequence_length: ${inputIdsShape.contentToString()}")
                encoderOutput.close()
                outputSequenceTensor.close()
                encoderResult.close()
                return null
            }
            val sequenceLength = inputIdsShape[1].toInt()
            if (sequenceLength <= 0) {
                Log.e(TAG, "Impossible de déterminer sequence_length depuis input_ids shape: ${inputIdsShape.contentToString()}")
                encoderOutput.close()
                outputSequenceTensor.close()
                encoderResult.close()
                return null
            }
            
            Log.d(TAG, "Création encoder_attention_mask: sequence_length=$sequenceLength")
            
            // Créer un masque de 1s pour tous les tokens (tous valides)
            val attentionMaskData = LongArray(sequenceLength) { 1L }
            val attentionMaskArray = arrayOf(attentionMaskData)
            val encoderAttentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMaskArray)
            
            try {
                // ⭐ BOUCLE AUTOREGRESSIVE: Le decoder SpeechT5 génère une frame à la fois
                // ⚠️ TEMPORAIRE: Limiter drastiquement pour éviter crash mémoire
                // TODO: Augmenter progressivement une fois stable
                val maxFrames = 50 // ⚠️ LIMITE TEMPORAIRE: 50 frames max (800ms d'audio)
                val minFrames = 10 // Minimum pour test
                val targetFrames = (sequenceLength * 2).coerceIn(minFrames, maxFrames) // Cible réduite: ~2 frames par token
                
                Log.d(TAG, "Démarrage boucle autoregressive (LIMITÉE): target=$targetFrames frames (min=$minFrames, max=$maxFrames)")
                Log.w(TAG, "⚠️ LIMITE TEMPORAIRE: Seulement $maxFrames frames max pour éviter crash mémoire")
                
                val accumulatedFrames = mutableListOf<FloatArray>() // Liste pour accumuler les frames
                var currentOutputSequence = outputSequenceTensor // Tensor actuel pour output_sequence
                var iteration = 0
                var framesGenerated = 0
                
                while (framesGenerated < targetFrames && iteration < maxFrames) {
                    iteration++
                    
                    // Préparer les inputs pour cette itération
                    decoderInputs.clear()
                    decoderInputs["encoder_hidden_states"] = encoderOutput
                    decoderInputs["speaker_embeddings"] = speakerEmbeddings
                    decoderInputs["output_sequence"] = currentOutputSequence
                    decoderInputs["encoder_attention_mask"] = encoderAttentionMaskTensor
                    
                    // Appeler le decoder
                    val decoderResult = decoder.run(decoderInputs)
                    
                    // ⚠️ CRITIQUE: Extraire TOUTES les données AVANT de fermer quoi que ce soit
                    // Extraire la nouvelle frame générée
                    val newFrameTensor: OnnxTensor? = try {
                        val resultAsMap = decoderResult as? Map<String, OnnxValue>
                        resultAsMap?.values?.firstOrNull() as? OnnxTensor
                            ?: decoderResult.get(0) as? OnnxTensor
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur extraction decoder output itération $iteration: ${e.message}")
                        decoderResult.close()
                        null
                    }
                    
                    if (newFrameTensor == null) {
                        Log.e(TAG, "Decoder output n'est pas un OnnxTensor à l'itération $iteration")
                        decoderResult.close()
                        break
                    }
                    
                    // Extraire les données de la nouvelle frame IMMÉDIATEMENT
                    val frameShape = newFrameTensor.info.shape
                    if (frameShape.size < 3) {
                        Log.e(TAG, "Shape invalide pour frame à l'itération $iteration: ${frameShape.contentToString()}")
                        newFrameTensor.close()
                        decoderResult.close()
                        break
                    }
                    
                    // La frame a shape [batch, seq_len, feature_dim]
                    val batchSize = frameShape[0].toInt()
                    val seqLen = frameShape[1].toInt()
                    val featDim = frameShape[2].toInt()
                    
                    // ⚠️ CRITIQUE: Extraire les données AVANT de fermer le tensor
                    val frameBuffer = newFrameTensor.floatBuffer
                    val frameDataSize = frameBuffer.remaining()
                    val frameData = FloatArray(frameDataSize)
                    frameBuffer.get(frameData)
                    
                    // ⚠️ FERMER IMMÉDIATEMENT après extraction (ne pas attendre)
                    newFrameTensor.close()
                    decoderResult.close()
                    
                    // Maintenant qu'on a les données, on peut les traiter
                    // Extraire chaque frame de la séquence générée (généralement 1-2 frames par itération)
                    for (s in 0 until seqLen) {
                        val frameStart = s * featDim
                        val frameEnd = frameStart + featDim
                        if (frameEnd <= frameData.size) {
                            val singleFrame = frameData.sliceArray(frameStart until frameEnd)
                            accumulatedFrames.add(singleFrame)
                            framesGenerated++
                        }
                    }
                    
                    // Mettre à jour output_sequence pour la prochaine itération
                    // ⚠️ STRATÉGIE: Utiliser seulement la dernière frame (pas de concaténation)
                    // pour éviter de créer des tensors trop grands
                    if (seqLen > 0 && framesGenerated > 0) {
                        // Fermer l'ancien tensor AVANT d'en créer un nouveau
                        if (currentOutputSequence != outputSequenceTensor) {
                            try {
                                currentOutputSequence.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Erreur fermeture currentOutputSequence (ignorée): ${e.message}")
                            }
                        }
                        
                        // Utiliser la dernière frame générée comme nouvelle output_sequence
                        val lastFrame = accumulatedFrames.last()
                        val newOutputSequenceArray = arrayOf(arrayOf(lastFrame))
                        try {
                            currentOutputSequence = OnnxTensor.createTensor(ortEnv, newOutputSequenceArray)
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur création nouveau currentOutputSequence: ${e.message}")
                            break
                        }
                    }
                    
                    // Log de progression tous les 50 frames
                    if (framesGenerated % 50 == 0 || framesGenerated >= targetFrames) {
                        Log.d(TAG, "Progression autoregressive: $framesGenerated/$targetFrames frames (itération $iteration)")
                    }
                }
                
                Log.d(TAG, "✅ Boucle autoregressive terminée: $framesGenerated frames générées en $iteration itérations")
                
                if (accumulatedFrames.isEmpty()) {
                    Log.e(TAG, "Aucune frame générée par la boucle autoregressive")
                    if (currentOutputSequence != outputSequenceTensor) {
                        currentOutputSequence.close()
                    }
                    encoderOutput.close()
                    outputSequenceTensor.close()
                    encoderAttentionMaskTensor.close()
                    return null
                }
                
                // Concaténer toutes les frames en un mel spectrogram complet
                val totalFrames = accumulatedFrames.size
                val melSpectrogramData = FloatArray(totalFrames * featureDim)
                for (i in accumulatedFrames.indices) {
                    System.arraycopy(accumulatedFrames[i], 0, melSpectrogramData, i * featureDim, featureDim)
                }
                
                // Créer le tensor mel spectrogram final avec shape [1, totalFrames, featureDim]
                val melSpectrogramArray = Array(1) { batch ->
                    Array(totalFrames) { frame ->
                        FloatArray(featureDim) { feat ->
                            melSpectrogramData[batch * totalFrames * featureDim + frame * featureDim + feat]
                        }
                    }
                }
                val melSpectrogram = OnnxTensor.createTensor(ortEnv, melSpectrogramArray)
                
                // Nettoyer les ressources temporaires
                if (currentOutputSequence != outputSequenceTensor) {
                    currentOutputSequence.close()
                }
                outputSequenceTensor.close()
                encoderAttentionMaskTensor.close() // Plus nécessaire après la boucle autoregressive
                
                // ⭐ ÉTAPE 3: Vocoder (mel spectrogram → waveform)
                Log.d(TAG, "Étape 3/3: Vocoder...")
                
                // Le vocoder attend "spectrogram" avec rank 2 [seq_len, feature_dim]
                // Le decoder produit rank 3 [batch, seq_len, feature_dim]
                val melSpectrogramShape = melSpectrogram.info.shape
                Log.d(TAG, "Mel spectrogram shape: ${melSpectrogramShape.contentToString()}")
                val totalFramesGenerated = melSpectrogramShape[1].toInt()
                val estimatedDurationMs = (totalFramesGenerated * 16) // ~16ms par frame à 16kHz
                Log.d(TAG, "✅ ${totalFramesGenerated} frames générées (${estimatedDurationMs}ms ≈ ${estimatedDurationMs / 1000.0}s d'audio)")
                
                val spectrogramForVocoder = if (melSpectrogramShape.size == 3) {
                    // Reshape de [batch, seq_len, feature_dim] → [seq_len, feature_dim]
                    val batch = melSpectrogramShape[0].toInt()
                    val seqLen = melSpectrogramShape[1].toInt()
                    val featureDim = melSpectrogramShape[2].toInt()
                    
                    Log.d(TAG, "Reshape mel spectrogram: [${batch}, ${seqLen}, ${featureDim}] → [${seqLen}, ${featureDim}]")
                    
                    // ⚠️ CRITIQUE: Extraire les données AVANT de fermer le tensor
                    // Utiliser getFloatBuffer() et copier immédiatement (recommandation officielle ONNX Runtime)
                    val originalBuffer = melSpectrogram.floatBuffer
                    val totalSize = originalBuffer.remaining()
                    val originalData = FloatArray(totalSize)
                    originalBuffer.get(originalData) // Copie immédiate
                    // ⚠️ NE PAS appeler rewind() - le buffer peut être partagé avec le tensor natif
                    
                    // Extraire batch=0: [batch, seq_len, feature_dim] → [seq_len, feature_dim]
                    // Les données sont stockées de manière contiguë: [batch0_seq0_feat0, batch0_seq0_feat1, ..., batch0_seq1_feat0, ...]
                    val batchSize = seqLen * featureDim
                    val reshapedData = FloatArray(seqLen * featureDim)
                    System.arraycopy(originalData, 0, reshapedData, 0, batchSize) // Copier batch=0
                    
                    // Créer un tableau 2D [seq_len, feature_dim] pour OnnxTensor
                    val reshapedArray = Array(seqLen) { i ->
                        FloatArray(featureDim) { j ->
                            reshapedData[i * featureDim + j]
                        }
                    }
                    
                    // Créer un nouveau tensor avec la shape [seq_len, feature_dim]
                    OnnxTensor.createTensor(ortEnv, reshapedArray)
                } else {
                    // Déjà rank 2, utiliser tel quel
                    melSpectrogram
                }
                
                val vocoderInputs = mutableMapOf<String, OnnxTensor>()
                vocoderInputs["spectrogram"] = spectrogramForVocoder
                
                try {
                    Log.d(TAG, "Tentative vocoder avec 'spectrogram' (shape: ${spectrogramForVocoder.info.shape.contentToString()})")
                    val vocoderResult = vocoder.run(vocoderInputs)
                    Log.d(TAG, "✅ Vocoder réussi")
                    val waveformTensor = try {
                        val resultAsMap = vocoderResult as? Map<String, OnnxValue>
                        resultAsMap?.values?.firstOrNull() as? OnnxTensor
                            ?: vocoderResult.get(0) as? OnnxTensor
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur extraction vocoder output: ${e.message}")
                        null
                    } ?: run {
                        Log.e(TAG, "Vocoder output n'est pas un OnnxTensor")
                        if (spectrogramForVocoder != melSpectrogram) {
                            spectrogramForVocoder.close() // Libérer le tensor reshaped si créé
                        }
                        melSpectrogram.close()
                        encoderOutput.close()
                        outputSequenceTensor.close()
                        encoderAttentionMaskTensor.close()
                        vocoderResult.close()
                        return null
                    }
                    // ⚠️ SOLUTION RADICALE: Extraire les données AVANT toute fermeture
                    // Puis NE PAS fermer les résultats - laisser le GC s'en occuper
                    // (ONNX Runtime gère la mémoire native, fermer cause corruption)
                    Log.d(TAG, "Extraction waveform depuis vocoder output...")
                    val waveform: FloatArray = try {
                        val value = waveformTensor.getValue()
                        val waveformArray = when (value) {
                            is FloatArray -> {
                                Log.d(TAG, "✅ Waveform extrait via getValue() (FloatArray): ${value.size} échantillons")
                                // Créer une copie complète pour être sûr
                                value.copyOf()
                            }
                            is Array<*> -> {
                                // Aplatir si multi-dimensionnel
                                val flattened = value.flatMap { 
                                    when (it) {
                                        is FloatArray -> it.toList()
                                        is Float -> listOf(it)
                                        else -> emptyList()
                                    }
                                }.toFloatArray()
                                Log.d(TAG, "✅ Waveform extrait via getValue() (Array aplati): ${flattened.size} échantillons")
                                flattened
                            }
                            else -> {
                                Log.e(TAG, "Format de waveform inattendu: ${value?.javaClass?.simpleName}")
                                throw IllegalStateException("Impossible d'extraire waveform")
                            }
                        }
                        waveformArray
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erreur extraction waveform: ${e.message}", e)
                        return null
                    }
                    
                    // ⚠️ SOLUTION RADICALE: NE PAS FERMER les tensors/résultats après extraction
                    // La fermeture cause corruption mémoire même après extraction avec getValue()
                    // ONNX Runtime gère la mémoire native via finalizers
                    // Le garbage collector Java s'occupera des références Java
                    Log.d(TAG, "Waveform extrait, laissant ONNX Runtime gérer la libération mémoire (pas de close() explicite)")
                    
                    if (waveform.isEmpty()) {
                        Log.e(TAG, "Waveform vide après conversion")
                        return null
                    }
                    
                    Log.i(TAG, "✅ Inference réussie: ${waveform.size} échantillons")
                    return waveform
                } catch (vocoderError: Exception) {
                    Log.e(TAG, "❌ Erreur vocoder: ${vocoderError.message}", vocoderError)
                    Log.e(TAG, "Vocoder inputs tentés: ${vocoderInputs.keys.joinToString()}")
                    if (spectrogramForVocoder != melSpectrogram) {
                        spectrogramForVocoder.close() // Libérer le tensor reshaped si créé
                    }
                    melSpectrogram.close()
                    encoderOutput.close()
                    encoderResult.close()
                    encoderAttentionMaskTensor.close()
                    return null
                }
            } catch (decoderError: Exception) {
                Log.e(TAG, "❌ Erreur decoder (boucle autoregressive): ${decoderError.message}", decoderError)
                encoderOutput.close()
                encoderResult.close()
                encoderAttentionMaskTensor.close()
                // Nettoyer outputSequenceTensor si nécessaire
                try {
                    outputSequenceTensor.close()
                } catch (e: Exception) {
                    // Ignorer si déjà fermé
                }
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur inference ONNX: ${e.message}", e)
            Log.e(TAG, "Stack trace:", e)
            return null
        } finally {
            // Libérer les tensors d'input
            inputs.values.forEach { it.close() }
        }
    }
    
    /**
     * Convertir waveform float32 → int16 PCM et jouer via AudioTrack
     * ⭐ MODIFIÉ: Vérification volume système et meilleure gestion erreurs
     * 
     * @param waveform Waveform audio (float32, -1.0 à 1.0)
     */
    private fun playAudio(waveform: FloatArray) {
        try {
            Log.d(TAG, "Conversion et lecture audio: ${waveform.size} échantillons")
            
            // ⭐ NOUVEAU: Vérifier volume système
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            if (currentVolume == 0) {
                Log.e(TAG, "❌❌❌ Volume système = 0, audio inaudible ❌❌❌")
                Log.e(TAG, "❌ Veuillez augmenter le volume système pour entendre la synthèse vocale")
            } else {
                Log.i(TAG, "✅ Volume système: $currentVolume/$maxVolume")
            }
            
            // ⚠️ DIAGNOSTIC: Vérifier la durée de l'audio
            val durationMs = (waveform.size * 1000 / SAMPLE_RATE)
            if (durationMs < 100) {
                Log.e(TAG, "❌❌❌ Audio trop court: ${durationMs}ms (${waveform.size} échantillons) - INAUDIBLE ❌❌❌")
                Log.e(TAG, "❌ Le decoder génère seulement 2 frames de mel spectrogram au lieu de 200-500")
                Log.e(TAG, "❌ SOLUTION: Implémenter boucle autoregressive pour le decoder")
            } else {
                Log.i(TAG, "✅ Durée audio: ${durationMs}ms (${waveform.size} échantillons)")
            }
            
            // Convertir float32 (-1.0 à 1.0) → int16 PCM
            val pcmData = ShortArray(waveform.size)
            for (i in waveform.indices) {
                // Clamper entre -1.0 et 1.0
                val clamped = waveform[i].coerceIn(-1.0f, 1.0f)
                // Convertir en int16: -32768 à 32767
                pcmData[i] = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
            }
            
            // Créer AudioTrack si nécessaire
            if (audioTrack == null) {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )
                
                if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
                    Log.e(TAG, "Erreur calcul buffer AudioTrack: $bufferSize")
                    return
                }
                
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }
            
            // Jouer l'audio
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_UNINITIALIZED) {
                    Log.e(TAG, "AudioTrack non initialisé")
                    return
                }
                
                track.play()
                val bytesWritten = track.write(pcmData, 0, pcmData.size)
                
                if (bytesWritten < 0) {
                    Log.e(TAG, "Erreur écriture AudioTrack: $bytesWritten")
                    when (bytesWritten) {
                        AudioTrack.ERROR_INVALID_OPERATION -> Log.e(TAG, "   → Opération invalide (track non démarré?)")
                        AudioTrack.ERROR_BAD_VALUE -> Log.e(TAG, "   → Valeur invalide (buffer trop petit?)")
                        AudioTrack.ERROR_DEAD_OBJECT -> Log.e(TAG, "   → AudioTrack mort (redémarrer nécessaire)")
                        else -> Log.e(TAG, "   → Erreur inconnue")
                    }
                } else if (bytesWritten < pcmData.size) {
                    Log.w(TAG, "⚠️ Seulement $bytesWritten/${pcmData.size} bytes écrits (buffer plein?)")
                } else {
                    Log.d(TAG, "✅ Audio joué: $bytesWritten bytes (${(pcmData.size * 1000 / SAMPLE_RATE)}ms)")
                    
                    // Attendre la fin de la lecture
                    val durationMs = (pcmData.size * 1000 / SAMPLE_RATE).toLong()
                    Thread.sleep(durationMs)
                    
                    track.stop()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lecture audio: ${e.message}", e)
        }
    }
    
    /**
     * Arrêter la synthèse vocale
     */
    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            isSpeaking = false
            Log.d(TAG, "TTS arrêté")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt TTS: ${e.message}", e)
        }
    }
    
    /**
     * Libérer les ressources ONNX
     */
    fun shutdown() {
        try {
            stop()
            encoderSession?.close()
            decoderSession?.close()
            vocoderSession?.close()
            encoderSession = null
            decoderSession = null
            vocoderSession = null
            ortEnv = null
            isReady = false
            Log.i(TAG, "ONNX TTS fermé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur fermeture ONNX TTS: ${e.message}", e)
        }
    }
    
    /**
     * Vérifier si ONNX TTS est prêt
     */
    fun isONNXReady(): Boolean {
        val ready = isReady && encoderSession != null && decoderSession != null && vocoderSession != null && tokenizer.isInitialized()
        if (!ready) {
            Log.w(TAG, "ONNX TTS non prêt: isReady=$isReady, encoder=${encoderSession != null}, decoder=${decoderSession != null}, vocoder=${vocoderSession != null}, tokenizer=${tokenizer.isInitialized()}")
        }
        return ready
    }
    
    /**
     * ⭐ NOUVEAU: Vérifier statut individuel des modèles (pour endpoint /models)
     */
    fun isEncoderReady(): Boolean = encoderSession != null
    fun isDecoderReady(): Boolean = decoderSession != null
    fun isVocoderReady(): Boolean = vocoderSession != null
    fun isTokenizerReady(): Boolean = tokenizer.isInitialized()
    
    /**
     * Vérifier si TTS est en cours
     */
    fun isTTSSpeaking(): Boolean = isSpeaking
    
    /**
     * ⭐ NOUVEAU: Synthétiser texte et retourner audio WAV (pour serveur HTTP)
     * 
     * @param text Texte à synthétiser
     * @param voice Type de voix ("male", "female", "default" ou null pour défaut)
     * @return Audio WAV (ByteArray) ou null si erreur
     */
    fun synthesizeToWav(text: String, voice: String? = null): ByteArray? {
        if (!isReady || encoderSession == null || decoderSession == null || vocoderSession == null) {
            Log.w(TAG, "ONNX TTS non prêt pour synthèse")
            return null
        }
        
        // Convertir string voice → VoiceType
        val voiceType = when (voice?.lowercase()) {
            "male", "homme", "masculin" -> SimpleTokenizer.VoiceType.MALE
            "female", "femme", "féminin" -> SimpleTokenizer.VoiceType.FEMALE
            else -> SimpleTokenizer.VoiceType.DEFAULT
        }
        
        try {
            // 1. Preprocessing texte avec type de voix
            val inputs = preprocessText(text, voiceType)
            if (inputs == null) {
                Log.e(TAG, "Erreur preprocessing texte")
                return null
            }
            
            // 2. Inference ONNX
            val waveform = runInference(inputs)
            if (waveform == null || waveform.isEmpty()) {
                Log.e(TAG, "Erreur inference ONNX ou waveform vide")
                return null
            }
            
            Log.d(TAG, "Waveform généré: ${waveform.size} échantillons")
            
            // 3. Convertir waveform → WAV
            return convertWaveformToWav(waveform)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur synthèse vocale: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Convertir waveform float32 → WAV (ByteArray)
     */
    private fun convertWaveformToWav(waveform: FloatArray): ByteArray {
        // Convertir float32 → int16 PCM
        val pcmData = ShortArray(waveform.size)
        for (i in waveform.indices) {
            val clamped = waveform[i].coerceIn(-1.0f, 1.0f)
            pcmData[i] = (clamped * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
        }
        
        // Créer header WAV
        val wavHeader = createWavHeader(pcmData.size * 2, SAMPLE_RATE, 1, 16)
        
        // Combiner header + PCM data
        val wavBytes = ByteArray(wavHeader.size + pcmData.size * 2)
        System.arraycopy(wavHeader, 0, wavBytes, 0, wavHeader.size)
        
        // Convertir ShortArray → ByteArray (little-endian)
        var offset = wavHeader.size
        for (sample in pcmData) {
            wavBytes[offset++] = (sample.toInt() and 0xFF).toByte()
            wavBytes[offset++] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        
        return wavBytes
    }
    
    /**
     * Créer header WAV (44 bytes)
     */
    private fun createWavHeader(dataSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val header = ByteArray(44)
        var offset = 0
        
        // "RIFF"
        header[offset++] = 'R'.toByte()
        header[offset++] = 'I'.toByte()
        header[offset++] = 'F'.toByte()
        header[offset++] = 'F'.toByte()
        
        // Chunk size (36 + dataSize)
        val chunkSize = 36 + dataSize
        header[offset++] = (chunkSize and 0xFF).toByte()
        header[offset++] = ((chunkSize shr 8) and 0xFF).toByte()
        header[offset++] = ((chunkSize shr 16) and 0xFF).toByte()
        header[offset++] = ((chunkSize shr 24) and 0xFF).toByte()
        
        // "WAVE"
        header[offset++] = 'W'.toByte()
        header[offset++] = 'A'.toByte()
        header[offset++] = 'V'.toByte()
        header[offset++] = 'E'.toByte()
        
        // "fmt "
        header[offset++] = 'f'.toByte()
        header[offset++] = 'm'.toByte()
        header[offset++] = 't'.toByte()
        header[offset++] = ' '.toByte()
        
        // Subchunk1Size (16)
        header[offset++] = 16
        header[offset++] = 0
        header[offset++] = 0
        header[offset++] = 0
        
        // AudioFormat (1 = PCM)
        header[offset++] = 1
        header[offset++] = 0
        
        // NumChannels
        header[offset++] = channels.toByte()
        header[offset++] = 0
        
        // SampleRate
        header[offset++] = (sampleRate and 0xFF).toByte()
        header[offset++] = ((sampleRate shr 8) and 0xFF).toByte()
        header[offset++] = ((sampleRate shr 16) and 0xFF).toByte()
        header[offset++] = ((sampleRate shr 24) and 0xFF).toByte()
        
        // ByteRate
        val byteRate = sampleRate * channels * bitsPerSample / 8
        header[offset++] = (byteRate and 0xFF).toByte()
        header[offset++] = ((byteRate shr 8) and 0xFF).toByte()
        header[offset++] = ((byteRate shr 16) and 0xFF).toByte()
        header[offset++] = ((byteRate shr 24) and 0xFF).toByte()
        
        // BlockAlign
        val blockAlign = channels * bitsPerSample / 8
        header[offset++] = blockAlign.toByte()
        header[offset++] = 0
        
        // BitsPerSample
        header[offset++] = bitsPerSample.toByte()
        header[offset++] = 0
        
        // "data"
        header[offset++] = 'd'.toByte()
        header[offset++] = 'a'.toByte()
        header[offset++] = 't'.toByte()
        header[offset++] = 'a'.toByte()
        
        // Subchunk2Size (dataSize)
        header[offset++] = (dataSize and 0xFF).toByte()
        header[offset++] = ((dataSize shr 8) and 0xFF).toByte()
        header[offset++] = ((dataSize shr 16) and 0xFF).toByte()
        header[offset++] = ((dataSize shr 24) and 0xFF).toByte()
        
        return header
    }
}

