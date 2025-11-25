# Mode Offline RAG - Idées et Implémentation

**Date:** 2025-01-XX  
**Objectif:** RAG fonctionnel même sans réseau (PC/Ollama indisponible)

---

## 🎯 PROBLÈME ACTUEL

**RAG nécessite le réseau pour:**
- Générer des embeddings (Ollama `/api/embeddings` ou RAG Server Python)
- Si réseau indisponible → RAG désactivé → Pas de contexte historique

**Conséquence:**
- L'IA répond sans contexte des conversations précédentes
- Perte de continuité conversationnelle
- Réponses moins pertinentes

---

## 💡 IDÉES POUR MODE OFFLINE

### ✅ Idée 1: Utiliser Embeddings Déjà Stockés (RECOMMANDÉ)

**Principe:**
- Les embeddings sont déjà stockés dans Room DB (`embeddingsJson`)
- Recherche sémantique uniquement sur conversations existantes
- Pas besoin de générer de nouveaux embeddings

**Avantages:**
- ✅ Fonctionne immédiatement (pas de développement complexe)
- ✅ Pas de dépendance réseau pour la recherche
- ✅ Performance excellente (données locales)
- ✅ Compatible avec l'architecture actuelle

**Limitations:**
- ⚠️ Pas d'embeddings pour nouvelles conversations (si réseau indisponible)
- ⚠️ Recherche limitée aux conversations avec embeddings

**Implémentation:**
```kotlin
// Dans RAGService.kt
suspend fun searchSimilarConversationsOffline(
    queryText: String,
    topK: Int = 5
): List<ConversationSearchResult> = withContext(Dispatchers.IO) {
    
    // 1. Essayer recherche sémantique avec embeddings existants
    val conversationsWithEmbeddings = conversationDao.getConversationsWithEmbeddings()
    
    if (conversationsWithEmbeddings.isNotEmpty()) {
        // Utiliser recherche textuelle pour trouver conversations similaires
        // Puis comparer avec embeddings stockés
        val textResults = conversationDao.searchConversations(queryText, 50)
        
        // Filtrer pour garder seulement celles avec embeddings
        val withEmbeddings = textResults.filter { it.embeddingsJson != null }
        
        // Calculer similarité avec embeddings stockés
        // (nécessite conversion queryText en embedding, mais on peut utiliser
        //  une approximation basée sur mots-clés)
        return@withContext calculateSimilarityOffline(queryText, withEmbeddings, topK)
    }
    
    // 2. Fallback: Recherche textuelle pure
    return@withContext textBasedSearch(queryText, topK)
}
```

---

### ✅ Idée 2: Recherche Textuelle Hybride (FALLBACK)

**Principe:**
- Si pas d'embeddings disponibles → Recherche textuelle SQL
- Utiliser `searchConversations()` existant dans ConversationDao
- Score de pertinence basé sur nombre de mots correspondants

**Avantages:**
- ✅ Fonctionne même sans embeddings
- ✅ Déjà implémenté dans ConversationDao
- ✅ Performance SQL excellente
- ✅ Pas de dépendance réseau

**Implémentation:**
```kotlin
// Dans RAGService.kt
suspend fun textBasedSearch(
    query: String,
    topK: Int = 5
): List<ConversationSearchResult> = withContext(Dispatchers.IO) {
    
    // Utiliser recherche textuelle SQL existante
    val results = conversationDao.searchConversations(query, topK * 2) // Prendre plus pour filtrer
    
    // Calculer score de pertinence basé sur:
    // - Nombre de mots correspondants
    // - Position des mots (début = plus pertinent)
    // - Longueur du texte (plus court = plus pertinent)
    val scoredResults = results.map { conv ->
        val score = calculateTextRelevance(query, conv.userMessage + " " + conv.aiResponse)
        ConversationSearchResult(
            conversation = conv,
            relevanceScore = score,
            matchedTerms = extractMatchedTerms(query, conv.userMessage + " " + conv.aiResponse)
        )
    }
    
    // Trier par score et prendre top-K
    return@withContext scoredResults
        .sortedByDescending { it.relevanceScore }
        .take(topK)
}

private fun calculateTextRelevance(query: String, text: String): Float {
    val queryWords = query.lowercase().split(" ").filter { it.length > 2 }
    val textLower = text.lowercase()
    
    var score = 0f
    queryWords.forEach { word ->
        val count = textLower.split(word).size - 1
        score += count * 0.1f
        
        // Bonus si mot au début
        if (textLower.startsWith(word)) {
            score += 0.2f
        }
    }
    
    // Normaliser (0.0-1.0)
    return minOf(1.0f, score / queryWords.size)
}
```

---

### ✅ Idée 3: Cache d'Embeddings Récents (OPTIMISATION)

**Principe:**
- Garder en mémoire les embeddings des N dernières conversations
- Réutiliser pour recherche rapide
- Pas besoin de charger depuis Room DB

**Avantages:**
- ✅ Performance améliorée
- ✅ Moins de lectures DB
- ✅ Fonctionne offline

**Implémentation:**
```kotlin
// Dans RAGService.kt
class RAGService(...) {
    // Cache en mémoire (LRU)
    private val embeddingCache = LruCache<String, FloatArray>(50) // 50 conversations
    
    suspend fun searchWithCache(queryEmbedding: FloatArray, topK: Int): List<ConversationSearchResult> {
        // 1. Charger conversations récentes depuis cache
        val cachedConversations = getCachedConversations()
        
        // 2. Si pas assez, charger depuis DB
        val dbConversations = if (cachedConversations.size < topK * 2) {
            conversationDao.getConversationsWithEmbeddings(100)
        } else {
            emptyList()
        }
        
        // 3. Merger et rechercher
        val allConversations = cachedConversations + dbConversations
        
        return searchSimilarConversations(queryEmbedding, allConversations, topK)
    }
}
```

---

### ⚠️ Idée 4: Embeddings Locaux sur Android (AVANCÉ)

**Principe:**
- Modèle ML léger directement sur Android
- Génération d'embeddings sans réseau
- Utiliser TensorFlow Lite ou ONNX Runtime

**Avantages:**
- ✅ Fonctionne complètement offline
- ✅ Pas de dépendance réseau
- ✅ Génération d'embeddings pour nouvelles conversations

**Inconvénients:**
- ❌ Modèle ML lourd (~50-100 MB)
- ❌ Performance Android limitée (CPU)
- ❌ Complexité d'intégration élevée
- ❌ Consommation batterie

**Modèles possibles:**
- `all-MiniLM-L6-v2` (384 dim, ~80 MB)
- `sentence-transformers` version mobile
- Modèle custom quantifié

**Implémentation (exemple conceptuel):**
```kotlin
// Nécessite TensorFlow Lite
class LocalEmbeddingService(context: Context) {
    private val interpreter: Interpreter
    
    init {
        val modelFile = loadModelFile("embedding_model.tflite")
        interpreter = Interpreter(modelFile)
    }
    
    fun embed(text: String): FloatArray {
        // Tokeniser texte
        val tokens = tokenize(text)
        
        // Exécuter modèle
        val output = Array(1) { FloatArray(384) }
        interpreter.run(tokens, output)
        
        return output[0]
    }
}
```

**Recommandation:** ⚠️ **Complexe, à considérer seulement si Idées 1-3 insuffisantes**

---

### ✅ Idée 5: Recherche Hybride (Sémantique + Textuelle)

**Principe:**
- Combiner recherche sémantique (si embeddings disponibles) + textuelle
- Score final = moyenne pondérée des deux
- Meilleure couverture

**Avantages:**
- ✅ Meilleure précision
- ✅ Fonctionne même si embeddings partiels
- ✅ Robustesse maximale

**Implémentation:**
```kotlin
suspend fun hybridSearch(
    query: String,
    topK: Int = 5
): List<ConversationSearchResult> = withContext(Dispatchers.IO) {
    
    // 1. Recherche sémantique (si embeddings disponibles)
    val semanticResults = if (hasEmbeddingsAvailable()) {
        try {
            val queryEmbedding = generateEmbedding(query) // Si réseau OK
            if (queryEmbedding != null) {
                searchSimilarConversations(queryEmbedding, topK * 2)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }
    
    // 2. Recherche textuelle (toujours disponible)
    val textResults = textBasedSearch(query, topK * 2)
    
    // 3. Combiner et dédupliquer
    val combined = (semanticResults + textResults)
        .groupBy { it.conversation.id }
        .map { (_, results) ->
            // Score moyen si plusieurs résultats pour même conversation
            val avgScore = results.map { it.relevanceScore }.average().toFloat()
            results.first().copy(relevanceScore = avgScore)
        }
        .sortedByDescending { it.relevanceScore }
        .take(topK)
    
    return@withContext combined
}
```

---

## 🎯 STRATÉGIE RECOMMANDÉE (Par Phases)

### Phase 1: Recherche avec Embeddings Stockés (IMMÉDIAT)

**Objectif:** RAG fonctionne offline pour conversations existantes

**Implémentation:**
1. Modifier `RAGService.searchSimilarConversations()` pour accepter `queryEmbedding` optionnel
2. Si `queryEmbedding == null`, utiliser recherche textuelle
3. Comparer avec embeddings stockés des conversations trouvées

**Code:**
```kotlin
suspend fun searchSimilarConversations(
    queryEmbedding: FloatArray? = null,  // ⭐ Optionnel
    queryText: String? = null,           // ⭐ Nouveau: texte pour fallback
    topK: Int = DEFAULT_TOP_K
): List<ConversationSearchResult> {
    
    // Si pas d'embedding, utiliser recherche textuelle
    if (queryEmbedding == null) {
        if (queryText != null) {
            return textBasedSearch(queryText, topK)
        } else {
            return emptyList()
        }
    }
    
    // Recherche sémantique normale (code existant)
    // ...
}
```

**Avantage:** ✅ **Déploiement rapide, fonctionne immédiatement**

---

### Phase 2: Recherche Textuelle Améliorée (1-2h)

**Objectif:** Améliorer la recherche textuelle avec scoring intelligent

**Implémentation:**
1. Améliorer `calculateTextRelevance()` avec:
   - TF-IDF simplifié
   - Position des mots
   - Longueur du texte
   - Mots-clés importants

**Avantage:** ✅ **Meilleure pertinence même sans embeddings**

---

### Phase 3: Recherche Hybride (2-3h)

**Objectif:** Combiner sémantique + textuelle pour meilleure couverture

**Implémentation:**
1. Implémenter `hybridSearch()`
2. Combiner résultats des deux méthodes
3. Dédupliquer et scorer

**Avantage:** ✅ **Robustesse maximale**

---

## 📊 COMPARAISON DES SOLUTIONS

| Solution | Complexité | Performance | Offline | Qualité |
|----------|------------|-------------|---------|---------|
| **Embeddings stockés** | ⭐ Faible | ⭐⭐⭐ Excellente | ✅ Oui | ⭐⭐⭐ Bonne |
| **Recherche textuelle** | ⭐ Faible | ⭐⭐⭐ Excellente | ✅ Oui | ⭐⭐ Moyenne |
| **Recherche hybride** | ⭐⭐ Moyenne | ⭐⭐ Bonne | ✅ Oui | ⭐⭐⭐ Excellente |
| **Cache embeddings** | ⭐ Faible | ⭐⭐⭐ Excellente | ✅ Oui | ⭐⭐⭐ Bonne |
| **Embeddings locaux** | ⭐⭐⭐ Élevée | ⭐ Lente | ✅ Oui | ⭐⭐⭐ Bonne |

---

## 🚀 PLAN D'IMPLÉMENTATION RECOMMANDÉ

### Étape 1: Mode Offline Basique (30min)

**Modifier `RAGService.kt`:**
```kotlin
suspend fun searchSimilarConversations(
    queryEmbedding: FloatArray? = null,
    queryText: String? = null,
    topK: Int = DEFAULT_TOP_K
): List<ConversationSearchResult> {
    
    // Si pas d'embedding mais texte fourni → recherche textuelle
    if (queryEmbedding == null && queryText != null) {
        Log.d(TAG, "No embedding available, using text-based search")
        return textBasedSearch(queryText, topK)
    }
    
    // Sinon, recherche sémantique normale
    // ... (code existant)
}
```

**Modifier `BidirectionalBridge.kt`:**
```kotlin
// Dans processWithThinking()
val queryEmbedding = embeddingService?.embed(userInput)

if (queryEmbedding != null) {
    // Recherche sémantique normale
    val similarConversations = ragService?.searchSimilarConversations(
        queryEmbedding = queryEmbedding,
        topK = 5
    )
} else {
    // ⭐ NOUVEAU: Fallback recherche textuelle
    Log.d(TAG, "Embedding failed, using text-based search as fallback")
    val similarConversations = ragService?.searchSimilarConversations(
        queryEmbedding = null,
        queryText = userInput,  // ⭐ Passer le texte
        topK = 5
    )
}
```

### Étape 2: Recherche Textuelle Améliorée (1h)

**Créer `textBasedSearch()` dans `RAGService.kt`** (voir code Idée 2)

### Étape 3: Tests et Optimisation (30min)

**Tester:**
- Réseau disponible → Recherche sémantique
- Réseau indisponible → Recherche textuelle
- Mixte → Recherche hybride

---

## 📋 CHECKLIST

### Phase 1: Offline Basique
- [ ] Modifier `RAGService.searchSimilarConversations()` pour accepter `queryText`
- [ ] Implémenter `textBasedSearch()` basique
- [ ] Modifier `BidirectionalBridge` pour passer `queryText` en fallback
- [ ] Tester avec réseau indisponible

### Phase 2: Amélioration
- [ ] Améliorer scoring textuel (`calculateTextRelevance()`)
- [ ] Ajouter extraction mots-clés
- [ ] Optimiser performance

### Phase 3: Hybride (Optionnel)
- [ ] Implémenter `hybridSearch()`
- [ ] Combiner résultats sémantique + textuelle
- [ ] Dédupliquer et scorer

---

## 🎯 CONCLUSION

**Recommandation:** Implémenter **Phase 1** immédiatement (30min)

**Résultat:**
- ✅ RAG fonctionne offline (recherche textuelle)
- ✅ Pas de régression (sémantique toujours prioritaire)
- ✅ Robustesse améliorée
- ✅ Expérience utilisateur préservée

**Phase 2 et 3:** Améliorations optionnelles selon besoins

---

**Références:**
- `RAGService.kt` - Recherche sémantique actuelle
- `ConversationDao.kt` - Recherche textuelle SQL existante
- `BidirectionalBridge.kt` - Intégration RAG

