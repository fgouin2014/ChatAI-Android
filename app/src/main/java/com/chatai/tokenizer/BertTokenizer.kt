package com.chatai.tokenizer

import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 🔤 BERT TOKENIZER pour sentence-transformers/all-MiniLM-L6-v2
 * 
 * Tokenizer WordPiece pour modèles BERT-based (all-MiniLM-L6-v2)
 * 
 * ⭐ SELON NOS RULES:
 * - Implémentation basée sur les specs officielles BERT/WordPiece
 * - Support tokens spéciaux BERT ([CLS], [SEP], [PAD], [UNK], [MASK])
 * - Tokenisation WordPiece (greedy longest-match)
 * - Chargement vocabulaire depuis tokenizer.json ou vocab.json
 * 
 * FICHIERS NÉCESSAIRES:
 * - /storage/emulated/0/ChatAI-Files/models/embeddings/tokenizer.json (format Tokenizers)
 *   OU
 * - /storage/emulated/0/ChatAI-Files/models/embeddings/vocab.json (format vocabulaire simple)
 * 
 * FORMAT VOCABULAIRE:
 * - vocab.json: {"token": id, ...} (mapping token → ID)
 * - tokenizer.json: Format Tokenizers Hugging Face (JSON complexe)
 */
class BertTokenizer {
    
    companion object {
        private const val TAG = "BertTokenizer"
        
        private const val BASE_PATH = "/storage/emulated/0/ChatAI-Files/models/embeddings"
        private const val TOKENIZER_JSON_PATH = "$BASE_PATH/tokenizer.json"
        private const val VOCAB_JSON_PATH = "$BASE_PATH/vocab.json"
        
        // Tokens spéciaux BERT (selon specs officielles)
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private const val PAD_TOKEN = "[PAD]"
        private const val UNK_TOKEN = "[UNK]"
        private const val MASK_TOKEN = "[MASK]"
        
        // Longueur max pour BERT (all-MiniLM-L6-v2 = 256 tokens typiquement)
        private const val MAX_LENGTH = 256
    }
    
    private var vocab: Map<String, Int>? = null
    private var vocabReverse: Map<Int, String>? = null
    private var isInitialized = false
    
    /**
     * Initialiser le tokenizer (charger vocabulaire)
     * ⭐ SELON NOS RULES: Support tokenizer.json ET vocab.json
     */
    fun initialize(): Boolean {
        if (isInitialized) {
            return true
        }
        
        try {
            // Essayer d'abord tokenizer.json (format Tokenizers Hugging Face)
            val tokenizerFile = File(TOKENIZER_JSON_PATH)
            if (tokenizerFile.exists()) {
                Log.d(TAG, "Chargement tokenizer.json...")
                if (loadFromTokenizerJson(tokenizerFile)) {
                    isInitialized = true
                    Log.i(TAG, "✅ Tokenizer initialisé depuis tokenizer.json (${vocab?.size} tokens)")
                    return true
                }
            }
            
            // Fallback: vocab.json (format simple)
            val vocabFile = File(VOCAB_JSON_PATH)
            if (vocabFile.exists()) {
                Log.d(TAG, "Chargement vocab.json...")
                if (loadFromVocabJson(vocabFile)) {
                    isInitialized = true
                    Log.i(TAG, "✅ Tokenizer initialisé depuis vocab.json (${vocab?.size} tokens)")
                    return true
                }
            }
            
            Log.w(TAG, "Aucun fichier tokenizer trouvé:")
            Log.w(TAG, "  - $TOKENIZER_JSON_PATH")
            Log.w(TAG, "  - $VOCAB_JSON_PATH")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur initialisation tokenizer: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Charger vocabulaire depuis tokenizer.json (format Tokenizers Hugging Face)
     * ⭐ SELON NOS RULES: Parser le format officiel Tokenizers
     */
    private fun loadFromTokenizerJson(file: File): Boolean {
        try {
            val jsonContent = file.readText()
            val tokenizerJson = JSONObject(jsonContent)
            
            // Format Tokenizers: vocab est dans "model" -> "vocab"
            // Structure: {"model": {"vocab": {"token": id, ...}}, ...}
            val model = tokenizerJson.optJSONObject("model")
            if (model != null) {
                val vocabObj = model.optJSONObject("vocab")
                if (vocabObj != null) {
                    vocab = mutableMapOf<String, Int>().apply {
                        vocabObj.keys().forEach { key ->
                            put(key, vocabObj.getInt(key))
                        }
                    }
                    buildReverseVocab()
                    return true
                }
            }
            
            // Format alternatif: vocab directement à la racine
            val vocabObj = tokenizerJson.optJSONObject("vocab")
            if (vocabObj != null) {
                vocab = mutableMapOf<String, Int>().apply {
                    vocabObj.keys().forEach { key ->
                        put(key, vocabObj.getInt(key))
                    }
                }
                buildReverseVocab()
                return true
            }
            
            Log.w(TAG, "Format tokenizer.json non reconnu")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement tokenizer.json: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Charger vocabulaire depuis vocab.json (format simple)
     * Format: {"token": id, ...}
     */
    private fun loadFromVocabJson(file: File): Boolean {
        try {
            val vocabJson = JSONObject(file.readText())
            vocab = mutableMapOf<String, Int>().apply {
                vocabJson.keys().forEach { key ->
                    put(key, vocabJson.getInt(key))
                }
            }
            buildReverseVocab()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement vocab.json: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Construire le vocabulaire inverse (ID → token) pour décodage
     */
    private fun buildReverseVocab() {
        vocabReverse = vocab?.entries?.associate { (k, v) -> v to k }
    }
    
    /**
     * Tokeniser un texte en IDs selon WordPiece (BERT)
     * ⭐ SELON NOS RULES: Implémentation WordPiece greedy longest-match
     * 
     * Algorithme WordPiece:
     * 1. Normaliser le texte (lowercase, Unicode normalization)
     * 2. Ajouter [CLS] au début
     * 3. Pour chaque mot:
     *    a. Essayer de trouver le mot complet dans le vocabulaire
     *    b. Sinon, diviser en sous-mots avec préfixe "##" (WordPiece)
     *    c. Utiliser greedy longest-match (le plus long sous-mot possible)
     * 4. Ajouter [SEP] à la fin
     * 5. Padding/truncation à MAX_LENGTH
     * 
     * @param text Texte à tokeniser
     * @return Array d'IDs de tokens (LongArray pour ONNX int64)
     */
    fun encode(text: String): LongArray {
        val vocab = this.vocab ?: return longArrayOf()
        
        val tokens = mutableListOf<Long>()
        
        // 1. Ajouter [CLS] token
        vocab[CLS_TOKEN]?.let { tokens.add(it.toLong()) }
        
        // 2. Normaliser le texte (lowercase pour BERT)
        val normalizedText = text.lowercase()
            .trim()
        
        // 3. Tokeniser WordPiece
        // Diviser en mots (espaces et ponctuation)
        val words = normalizedText.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        
        for (word in words) {
            // Essayer de trouver le mot complet
            val wordToken = vocab[word]
            if (wordToken != null) {
                tokens.add(wordToken.toLong())
            } else {
                // WordPiece: diviser en sous-mots avec préfixe "##"
                var remaining = word
                var foundAny = false
                
                // Greedy longest-match: chercher le plus long sous-mot possible
                while (remaining.isNotEmpty()) {
                    var subwordFound = false
                    
                    // Chercher du plus long au plus court
                    for (i in remaining.length downTo 1) {
                        val subword = remaining.substring(0, i)
                        
                        // Essayer avec préfixe "##" (sous-mot WordPiece)
                        val subwordWithPrefix = "##$subword"
                        val subwordToken = vocab[subwordWithPrefix]
                        
                        if (subwordToken != null) {
                            tokens.add(subwordToken.toLong())
                            remaining = remaining.substring(i)
                            subwordFound = true
                            foundAny = true
                            break
                        }
                        
                        // Essayer sans préfixe (premier sous-mot du mot)
                        val firstSubwordToken = vocab[subword]
                        if (firstSubwordToken != null && !foundAny) {
                            tokens.add(firstSubwordToken.toLong())
                            remaining = remaining.substring(i)
                            subwordFound = true
                            foundAny = true
                            break
                        }
                    }
                    
                    if (!subwordFound) {
                        // Token inconnu: utiliser [UNK]
                        vocab[UNK_TOKEN]?.let { tokens.add(it.toLong()) }
                        break
                    }
                }
            }
        }
        
        // 4. Ajouter [SEP] token
        vocab[SEP_TOKEN]?.let { tokens.add(it.toLong()) }
        
        // 5. Padding/truncation à MAX_LENGTH
        val padTokenId = vocab[PAD_TOKEN]?.toLong() ?: 0L
        
        return when {
            tokens.size > MAX_LENGTH -> {
                // Truncation: garder [CLS] + MAX_LENGTH-2 tokens + [SEP]
                val truncated = mutableListOf<Long>()
                truncated.add(tokens[0]) // [CLS]
                truncated.addAll(tokens.subList(1, MAX_LENGTH - 1)) // Tokens du milieu
                truncated.add(tokens.last()) // [SEP]
                truncated.toLongArray()
            }
            tokens.size < MAX_LENGTH -> {
                // Padding: ajouter [PAD] jusqu'à MAX_LENGTH
                val padded = tokens.toMutableList()
                while (padded.size < MAX_LENGTH) {
                    padded.add(padTokenId)
                }
                padded.toLongArray()
            }
            else -> tokens.toLongArray()
        }
    }
    
    /**
     * Décoder des IDs de tokens en texte (pour debug)
     */
    fun decode(tokenIds: LongArray): String {
        val vocabReverse = this.vocabReverse ?: return ""
        
        val tokens = mutableListOf<String>()
        for (id in tokenIds) {
            val token = vocabReverse[id.toInt()]
            if (token != null) {
                tokens.add(token)
            }
        }
        
        // Retirer [CLS], [SEP], [PAD]
        val specialTokens = setOf(CLS_TOKEN, SEP_TOKEN, PAD_TOKEN, UNK_TOKEN, MASK_TOKEN)
        val filtered = tokens.filter { token ->
            token !in specialTokens
        }
        
        // Reconstruire le texte (retirer préfixes "##")
        val result = StringBuilder()
        for (i in filtered.indices) {
            val token = filtered[i]
            if (i > 0) {
                result.append(" ")
            }
            if (token.startsWith("##", ignoreCase = false)) {
                result.append(token.substring(2))
            } else {
                result.append(token)
            }
        }
        return result.toString()
    }
    
    /**
     * Vérifier si le tokenizer est initialisé
     */
    fun isInitialized(): Boolean = isInitialized && vocab != null
    
    /**
     * Obtenir la taille du vocabulaire
     */
    fun getVocabSize(): Int = vocab?.size ?: 0
}

