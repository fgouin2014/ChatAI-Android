package com.chatai.tokenizer

import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * 🔤 SIMPLE TOKENIZER pour SpeechT5
 * 
 * Tokenizer basique qui charge le vocabulaire depuis un fichier JSON
 * et tokenise le texte en utilisant WordPiece (simplifié)
 * 
 * ⚠️ NOTE: Cette implémentation est simplifiée. Pour une production complète,
 * il faudrait utiliser le tokenizer complet de SpeechT5 (via serveur Python
 * ou bibliothèque native).
 * 
 * FICHIERS NÉCESSAIRES:
 * - /storage/emulated/0/ChatAI-Files/models/tts/vocab.json
 * - /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json
 */
class SimpleTokenizer {
    
    companion object {
        private const val TAG = "SimpleTokenizer"
        
        private const val VOCAB_PATH = "/storage/emulated/0/ChatAI-Files/models/tts/vocab.json"
        private const val SPEAKER_EMBEDDINGS_DIR = "/storage/emulated/0/ChatAI-Files/models/tts"
        
        // Tokens spéciaux SpeechT5
        private const val PAD_TOKEN = "<pad>"
        private const val UNK_TOKEN = "<unk>"
        private const val BOS_TOKEN = "<s>"
        private const val EOS_TOKEN = "</s>"
    }
    
    // Voix disponibles (défini en dehors du companion object pour être accessible)
    enum class VoiceType {
        DEFAULT,  // Voix par défaut (neutre)
        MALE,     // Voix masculine
        FEMALE    // Voix féminine
    }
    
    private var vocab: Map<String, Int>? = null
    private var defaultSpeakerEmbeddings: FloatArray? = null
    private var maleSpeakerEmbeddings: FloatArray? = null
    private var femaleSpeakerEmbeddings: FloatArray? = null
    
    /**
     * Initialiser le tokenizer (charger vocabulaire)
     */
    fun initialize(): Boolean {
        try {
            // Charger vocabulaire
            val vocabFile = File(VOCAB_PATH)
            if (!vocabFile.exists()) {
                Log.w(TAG, "Vocabulaire non trouvé: $VOCAB_PATH")
                return false
            }
            
            val vocabJson = JSONObject(vocabFile.readText())
            vocab = mutableMapOf<String, Int>().apply {
                vocabJson.keys().forEach { key ->
                    put(key, vocabJson.getInt(key))
                }
            }
            
            Log.i(TAG, "Vocabulaire chargé: ${vocab?.size} tokens")
            
            // Charger speaker embeddings (défaut, homme, femme)
            val embeddingsDir = File(SPEAKER_EMBEDDINGS_DIR)
            
            // 1. Embeddings par défaut
            val defaultFile = File(embeddingsDir, "default_speaker_embeddings.json")
            if (defaultFile.exists()) {
                val speakerJson = JSONObject(defaultFile.readText())
                val embeddingsArray = speakerJson.getJSONArray("embeddings")
                defaultSpeakerEmbeddings = FloatArray(embeddingsArray.length()) { i ->
                    embeddingsArray.getDouble(i).toFloat()
                }
                Log.i(TAG, "Speaker embeddings (défaut) chargés: ${defaultSpeakerEmbeddings?.size} dimensions")
            } else {
                Log.w(TAG, "Speaker embeddings (défaut) non trouvés, utilisation valeurs par défaut")
                defaultSpeakerEmbeddings = FloatArray(512) { 0.0f }
            }
            
            // 2. Embeddings masculins
            val maleFile = File(embeddingsDir, "male_speaker_embeddings.json")
            if (maleFile.exists()) {
                val speakerJson = JSONObject(maleFile.readText())
                val embeddingsArray = speakerJson.getJSONArray("embeddings")
                maleSpeakerEmbeddings = FloatArray(embeddingsArray.length()) { i ->
                    embeddingsArray.getDouble(i).toFloat()
                }
                Log.i(TAG, "Speaker embeddings (masculin) chargés: ${maleSpeakerEmbeddings?.size} dimensions")
            } else {
                Log.w(TAG, "Speaker embeddings (masculin) non trouvés, utilisation défaut")
                maleSpeakerEmbeddings = defaultSpeakerEmbeddings?.copyOf()
            }
            
            // 3. Embeddings féminins
            val femaleFile = File(embeddingsDir, "female_speaker_embeddings.json")
            if (femaleFile.exists()) {
                val speakerJson = JSONObject(femaleFile.readText())
                val embeddingsArray = speakerJson.getJSONArray("embeddings")
                femaleSpeakerEmbeddings = FloatArray(embeddingsArray.length()) { i ->
                    embeddingsArray.getDouble(i).toFloat()
                }
                Log.i(TAG, "Speaker embeddings (féminin) chargés: ${femaleSpeakerEmbeddings?.size} dimensions")
            } else {
                Log.w(TAG, "Speaker embeddings (féminin) non trouvés, utilisation défaut")
                femaleSpeakerEmbeddings = defaultSpeakerEmbeddings?.copyOf()
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation tokenizer: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Tokeniser un texte en IDs
     * 
     * ⚠️ SIMPLIFICATION: Tokenisation basique (WordPiece simplifié)
     * En production, utiliser le tokenizer complet de SpeechT5
     * 
     * @param text Texte à tokeniser
     * @return Array d'IDs de tokens
     */
    fun encode(text: String): IntArray {
        val vocab = this.vocab ?: return intArrayOf()
        
        // Tokenisation simplifiée: diviser par espaces et ponctuation
        val tokens = mutableListOf<Int>()
        
        // Ajouter BOS token
        vocab[BOS_TOKEN]?.let { tokens.add(it) }
        
        // Tokeniser mot par mot (simplifié)
        val words = text.lowercase()
            .replace(Regex("[.,!?;:]"), " $0 ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        
        for (word in words) {
            // Essayer de trouver le mot complet
            val wordToken = vocab[word]
            if (wordToken != null) {
                tokens.add(wordToken)
            } else {
                // Sinon, essayer de diviser en sous-mots (WordPiece simplifié)
                var remaining = word
                var found = false
                
                while (remaining.isNotEmpty()) {
                    // Chercher le plus long sous-mot possible
                    var subwordFound = false
                    for (i in remaining.length downTo 1) {
                        val subword = remaining.substring(0, i)
                        val subwordToken = vocab[subword]
                        if (subwordToken != null) {
                            tokens.add(subwordToken)
                            remaining = remaining.substring(i)
                            subwordFound = true
                            found = true
                            break
                        }
                    }
                    
                    if (!subwordFound) {
                        // Token inconnu
                        vocab[UNK_TOKEN]?.let { tokens.add(it) }
                        break
                    }
                }
            }
        }
        
        // Ajouter EOS token
        vocab[EOS_TOKEN]?.let { tokens.add(it) }
        
        return tokens.toIntArray()
    }
    
    /**
     * Obtenir les speaker embeddings selon le type de voix
     * 
     * @param voiceType Type de voix (DEFAULT, MALE, FEMALE)
     * @return FloatArray de 512 dimensions ou null si non initialisé
     */
    fun getSpeakerEmbeddings(voiceType: VoiceType = VoiceType.DEFAULT): FloatArray? {
        return when (voiceType) {
            VoiceType.MALE -> maleSpeakerEmbeddings ?: defaultSpeakerEmbeddings
            VoiceType.FEMALE -> femaleSpeakerEmbeddings ?: defaultSpeakerEmbeddings
            VoiceType.DEFAULT -> defaultSpeakerEmbeddings
        }
    }
    
    /**
     * Obtenir les speaker embeddings par défaut (compatibilité)
     */
    fun getDefaultSpeakerEmbeddings(): FloatArray? = getSpeakerEmbeddings(VoiceType.DEFAULT)
    
    /**
     * Vérifier si le tokenizer est initialisé
     */
    fun isInitialized(): Boolean = vocab != null && defaultSpeakerEmbeddings != null
}

