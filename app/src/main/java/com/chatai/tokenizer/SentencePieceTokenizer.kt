package com.chatai.tokenizer

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 🔤 SENTENCEPIECE TOKENIZER pour MarianMT (opus-mt-fr-en)
 * 
 * Tokenizer SentencePiece pour modèles MarianMT (Helsinki-NLP/opus-mt-fr-en)
 * 
 * ⭐ SELON NOS RULES:
 * - Implémentation basée sur le vocabulaire MarianMT réel
 * - Support tokens spéciaux SentencePiece (<pad>, <unk>, <s>, </s>)
 * - Tokenisation compatible avec MarianMT
 * - Chargement vocabulaire depuis vocab.json
 * 
 * FICHIERS NÉCESSAIRES:
 * - /storage/emulated/0/ChatAI-Files/models/translation/vocab.json (vocabulaire MarianMT)
 * - Optionnel: /storage/emulated/0/ChatAI-Files/models/translation/source.spm (modèle SentencePiece)
 * 
 * FORMAT VOCABULAIRE MARIANMT:
 * - vocab.json: {"token": id, ...} (mapping token → ID)
 * - Tokens spéciaux typiques: <pad>=0, <unk>=1, <s>=2, </s>=3
 * 
 * ⚠️ NOTE: Version simplifiée utilisant vocab.json directement.
 * Pour une implémentation complète SentencePiece, il faudrait parser le fichier .spm
 */
class SentencePieceTokenizer {
    
    companion object {
        private const val TAG = "SentencePieceTokenizer"
        
        private const val BASE_PATH = "/storage/emulated/0/ChatAI-Files/models/translation"
        private const val VOCAB_JSON_PATH = "$BASE_PATH/vocab.json"
        private const val SOURCE_SPM_PATH = "$BASE_PATH/source.spm"
        
        // Tokens spéciaux SentencePiece/MarianMT typiques
        private const val PAD_TOKEN = "<pad>"
        private const val UNK_TOKEN = "<unk>"
        private const val BOS_TOKEN = "<s>"      // Beginning of Sequence
        private const val EOS_TOKEN = "</s>"     // End of Sequence
        
        // Longueur max pour MarianMT
        private const val MAX_LENGTH = 128
    }
    
    private var vocab: Map<String, Int>? = null
    private var vocabReverse: Map<Int, String>? = null
    private var isInitialized = false
    
    // IDs des tokens spéciaux (détectés depuis vocab.json)
    private var padTokenId: Int = 0
    private var unkTokenId: Int = 1
    private var bosTokenId: Int = 2
    private var eosTokenId: Int = 3
    
    /**
     * Initialiser le tokenizer (charger vocabulaire)
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            return true
        }
        
        try {
            Log.i(TAG, "Initialisation SentencePieceTokenizer pour MarianMT...")
            
            // Charger vocab.json
            val vocabFile = File(VOCAB_JSON_PATH)
            if (!vocabFile.exists()) {
                Log.w(TAG, "Fichier vocab.json manquant: $VOCAB_JSON_PATH")
                Log.w(TAG, "Fallback vers BertTokenizer recommandé")
                return false
            }
            
            Log.d(TAG, "Chargement vocab.json...")
            val jsonContent = vocabFile.readText()
            val vocabJson = JSONObject(jsonContent)
            
            // Construire le mapping token → ID
            val vocabMap = mutableMapOf<String, Int>()
            val iterator = vocabJson.keys()
            
            while (iterator.hasNext()) {
                val token = iterator.next()
                val id = vocabJson.getInt(token)
                vocabMap[token] = id
            }
            
            vocab = vocabMap
            
            // Construire le mapping inverse (ID → token)
            vocabReverse = vocabMap.entries.associate { (k, v) -> v to k }
            
            // Détecter les IDs des tokens spéciaux depuis le vocabulaire
            padTokenId = vocabMap[PAD_TOKEN] ?: 0
            unkTokenId = vocabMap[UNK_TOKEN] ?: 1
            bosTokenId = vocabMap[BOS_TOKEN] ?: 2
            eosTokenId = vocabMap[EOS_TOKEN] ?: 3
            
            Log.d(TAG, "Tokens spéciaux détectés:")
            Log.d(TAG, "  <pad> = $padTokenId")
            Log.d(TAG, "  <unk> = $unkTokenId")
            Log.d(TAG, "  <s> = $bosTokenId")
            Log.d(TAG, "  </s> = $eosTokenId")
            
            isInitialized = true
            Log.i(TAG, "✅ SentencePieceTokenizer initialisé (${vocab?.size} tokens)")
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation SentencePieceTokenizer: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Encoder un texte en IDs de tokens (LongArray pour compatibilité)
     * 
     * ⚠️ VERSION SIMPLIFIÉE: Tokenisation basique par mots/espaces
     * Pour une vraie tokenisation SentencePiece, il faudrait parser le fichier .spm
     * 
     * @param text Texte à encoder
     * @return LongArray d'IDs de tokens (inclut BOS et EOS)
     */
    fun encode(text: String): LongArray {
        if (!isInitialized || vocab == null) {
            Log.w(TAG, "Tokenizer non initialisé, retour tableau vide")
            return longArrayOf()
        }
        
        if (text.isBlank()) {
            return longArrayOf(bosTokenId.toLong(), eosTokenId.toLong())
        }
        
        try {
            val vocabMap = vocab ?: return longArrayOf()
            val tokens = mutableListOf<Long>()
            
            // Ajouter BOS token
            tokens.add(bosTokenId.toLong())
            
            // Tokenisation simplifiée: split par espaces et caractères spéciaux
            // ⚠️ Cette version est basique - une vraie implémentation SentencePiece serait plus complexe
            val normalizedText = text.trim().lowercase()
            
            // Split par espaces et ponctuation
            val words = normalizedText.split(Regex("\\s+|(?=[.,!?;:])|(?<=[.,!?;:])"))
                .filter { it.isNotBlank() }
            
            for (word in words) {
                // Essayer de trouver le mot complet dans le vocabulaire
                val wordId = vocabMap[word]
                if (wordId != null) {
                    tokens.add(wordId.toLong())
                } else {
                    // Si le mot n'existe pas, essayer de le diviser en sous-mots
                    // (version simplifiée - SentencePiece fait ça de manière plus sophistiquée)
                    val subwords = splitIntoSubwords(word, vocabMap)
                    for (subword in subwords) {
                        val subwordId = vocabMap[subword] ?: unkTokenId
                        tokens.add(subwordId.toLong())
                    }
                }
            }
            
            // Ajouter EOS token
            tokens.add(eosTokenId.toLong())
            
            // Tronquer si trop long
            if (tokens.size > MAX_LENGTH) {
                Log.w(TAG, "Séquence tronquée: ${tokens.size} > $MAX_LENGTH tokens")
                val truncated = tokens.take(MAX_LENGTH - 1).toMutableList()
                truncated.add(eosTokenId.toLong()) // Garder EOS
                return truncated.toLongArray()
            }
            
            return tokens.toLongArray()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur encodage: ${e.message}", e)
            return longArrayOf(bosTokenId.toLong(), eosTokenId.toLong())
        }
    }
    
    /**
     * Diviser un mot en sous-mots (version simplifiée)
     * 
     * ⚠️ VERSION SIMPLIFIÉE: Ne reproduit pas exactement l'algorithme SentencePiece
     * Une vraie implémentation utiliserait BPE ou unigram depuis le fichier .spm
     */
    private fun splitIntoSubwords(word: String, vocab: Map<String, Int>): List<String> {
        val subwords = mutableListOf<String>()
        
        // Essayer de trouver des préfixes/suffixes courants
        // C'est une heuristique simple - SentencePiece fait mieux
        var remaining = word
        
        // Chercher le plus long préfixe dans le vocabulaire
        while (remaining.isNotEmpty()) {
            var found = false
            var longestMatch = ""
            
            // Chercher préfixes de plus en plus courts
            for (i in remaining.length downTo 1) {
                val prefix = remaining.substring(0, i)
                if (vocab.containsKey(prefix)) {
                    longestMatch = prefix
                    found = true
                    break
                }
            }
            
            if (found) {
                subwords.add(longestMatch)
                remaining = remaining.substring(longestMatch.length)
            } else {
                // Aucun match trouvé, utiliser UNK
                subwords.add("<unk>")
                break
            }
        }
        
        return if (subwords.isEmpty()) listOf("<unk>") else subwords
    }
    
    /**
     * Décoder des IDs de tokens en texte
     * 
     * @param tokenIds IDs de tokens à décoder
     * @return Texte reconstruit
     */
    fun decode(tokenIds: LongArray): String {
        if (!isInitialized || vocabReverse == null) {
            Log.w(TAG, "Tokenizer non initialisé, retour chaîne vide")
            return ""
        }
        
        if (tokenIds.isEmpty()) {
            return ""
        }
        
        try {
            val vocabReverseMap = vocabReverse ?: return ""
            val tokens = mutableListOf<String>()
            
            for (id in tokenIds) {
                val tokenId = id.toInt()
                
                // Ignorer les tokens spéciaux PAD, BOS, EOS
                if (tokenId == padTokenId || tokenId == bosTokenId || tokenId == eosTokenId) {
                    continue
                }
                
                val token = vocabReverseMap[tokenId]
                if (token != null) {
                    // Ignorer <unk> sauf si c'est vraiment nécessaire
                    if (token != UNK_TOKEN) {
                        tokens.add(token)
                    }
                }
            }
            
            // Reconstruire le texte
            // ⚠️ SentencePiece utilise des underscores pour joindre les sous-mots
            // Ici on joint simplement par espaces
            return tokens.joinToString(" ").trim()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur décodage: ${e.message}", e)
            return ""
        }
    }
    
    /**
     * Vérifier si le tokenizer est initialisé
     */
    fun isInitialized(): Boolean = isInitialized && vocab != null
    
    /**
     * Obtenir la taille du vocabulaire
     */
    fun getVocabSize(): Int = vocab?.size ?: 0
    
    /**
     * Obtenir l'ID du token BOS
     */
    fun getBosTokenId(): Int = bosTokenId
    
    /**
     * Obtenir l'ID du token EOS
     */
    fun getEosTokenId(): Int = eosTokenId
    
    /**
     * Obtenir l'ID du token PAD
     */
    fun getPadTokenId(): Int = padTokenId
}


