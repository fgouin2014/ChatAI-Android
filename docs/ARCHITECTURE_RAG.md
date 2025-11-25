# Architecture RAG dans ChatAI

## Vue d'ensemble

L'architecture RAG (Retrieval Augmented Generation) dans ChatAI permet d'améliorer les réponses de l'IA en utilisant le contexte des conversations précédentes.

## Schéma architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      USER INPUT                                  │
│                   "Quelle était ma question                      │
│                    sur Python?"                                  │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              EmbeddingService                                    │
│  • embed(text: String): FloatArray?                             │
│  • embedConversation(userMsg, aiResp): FloatArray?              │
│  • embeddingToJson(embedding): String                            │
│  • jsonToEmbedding(json): FloatArray?                            │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼ Embedding vector (768 dimensions)
┌─────────────────────────────────────────────────────────────────┐
│              SimilarityUtils                                     │
│  • cosineSimilarity(vec1, vec2): Float                           │
│  • euclideanDistance(vec1, vec2): Float                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼ Similarity scores
┌─────────────────────────────────────────────────────────────────┐
│              ConversationDao                                     │
│  • getConversationsWithEmbeddings(): List<ConversationEntity>   │
│  • searchConversations(query, limit): List<ConversationEntity>  │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼ Similar conversations (score > 0.7)
┌─────────────────────────────────────────────────────────────────┐
│              RAGService                                          │
│  • buildContext(userInput, conversations): String                │
│  • limitTokens(context, maxTokens: Int): String                  │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼ Context string
┌─────────────────────────────────────────────────────────────────┐
│              KittAIService                                       │
│  • processUserInput(userInput): String                           │
│    → Appelle RAGService.buildContext()                          │
│    → Appelle Ollama avec contexte                               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│              AI RESPONSE                                         │
│          "Vous aviez demandé comment créer                       │
│           une liste en Python. Je vous avais                     │
│           expliqué la syntaxe [1, 2, 3]..."                     │
└─────────────────────────────────────────────────────────────────┘
```

## Composants principaux

### 1. EmbeddingService

**Responsabilité** : Générer des embeddings pour du texte ou des conversations.

**Méthodes principales**:
- `embed(text: String): FloatArray?` : Génère un embedding pour un texte
- `embedConversation(userMessage: String, aiResponse: String): FloatArray?` : Génère un embedding pour une conversation complète
- `embeddingToJson(embedding: FloatArray): String` : Convertit un embedding en JSON pour stockage
- `jsonToEmbedding(jsonString: String): FloatArray?` : Convertit un JSON en embedding

**Configuration**:
- Modèle par défaut: `nomic-embed-text` (768 dimensions)
- URL Ollama local: `http://localhost:11434/api/embeddings`
- Timeout: 30s (lecture), 10s (connexion)

**Exemple de code**:
```kotlin
val embeddingService = EmbeddingService(context)
val embedding = embeddingService.embed("Bonjour, comment allez-vous?")
// embedding = FloatArray(768) avec valeurs flottantes
```

### 2. SimilarityUtils

**Responsabilité** : Calculer la similarité entre vecteurs d'embeddings.

**Méthodes principales**:
- `cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float` : Calcule la similarité cosine (0.0-1.0)
- `euclideanDistance(embedding1: FloatArray, embedding2: FloatArray): Float` : Calcule la distance euclidienne

**Formule cosine similarity**:
```
cosine_similarity = dot(A,B) / (||A|| * ||B||)
```

**Exemple de code**:
```kotlin
val similarity = SimilarityUtils.cosineSimilarity(embedding1, embedding2)
// similarity = 0.85 (très similaire, score > 0.7)
```

### 3. ConversationDao

**Responsabilité** : Accéder aux conversations dans Room DB.

**Méthodes principales**:
- `getConversationsWithEmbeddings(): List<ConversationEntity>` : Récupère les conversations avec embeddings
- `searchConversations(query: String, limit: Int): List<ConversationEntity>` : Recherche textuelle dans conversations
- `updateEmbeddings(id: Long, embeddings: String)` : Met à jour les embeddings d'une conversation

**Structure ConversationEntity**:
```kotlin
data class ConversationEntity(
    val id: Long,
    val conversationId: String,
    val userMessage: String,
    val aiResponse: String,
    val embeddingsJson: String?, // Embedding JSON array
    val timestamp: Long,
    // ... autres champs
)
```

### 4. RAGService

**Responsabilité** : Construire le contexte RAG à partir des conversations similaires.

**Méthodes principales**:
- `buildContext(userInput: String, conversations: List<ConversationEntity>): String` : Construit le contexte RAG
- `limitTokens(context: String, maxTokens: Int): String` : Limite le contexte à un nombre de tokens

**Exemple de contexte généré**:
```
Contexte des conversations précédentes:

1. Vous: "Comment créer une liste en Python?"
   IA: "Pour créer une liste en Python, utilisez la syntaxe [1, 2, 3]..."

2. Vous: "Quelle est la différence entre liste et tuple?"
   IA: "Une liste est mutable, un tuple est immutable..."

3. Vous: "Comment ajouter un élément à une liste?"
   IA: "Utilisez la méthode append(): ma_liste.append(element)..."
```

### 5. KittAIService

**Responsabilité** : Intégrer RAG dans le flux de traitement des requêtes utilisateur.

**Intégration RAG**:
```kotlin
suspend fun processUserInput(userInput: String): String {
    // 1. Générer embedding pour userInput
    val embeddingService = EmbeddingService(context)
    val userEmbedding = embeddingService.embed(userInput)
    
    // 2. Rechercher conversations similaires
    if (userEmbedding != null && ragEnabled) {
        val allConversations = conversationDao.getConversationsWithEmbeddings()
        val similarConversations = findSimilarConversations(userEmbedding, allConversations)
        
        // 3. Construire contexte RAG
        val context = ragService.buildContext(userInput, similarConversations)
        
        // 4. Ajouter contexte au prompt
        val enhancedPrompt = "$context\n\nQuestion: $userInput"
        
        // 5. Appeler Ollama avec contexte
        return callOllama(enhancedPrompt)
    }
    
    // Fallback sans RAG
    return callOllama(userInput)
}
```

## Flow des données

### 1. Génération d'embedding (nouvelle conversation)

```
UserInput → EmbeddingService.embedConversation() → FloatArray(768)
         → EmbeddingService.embeddingToJson() → String (JSON)
         → ConversationDao.updateEmbeddings() → Room DB
```

### 2. Recherche sémantique (requête utilisateur)

```
UserInput → EmbeddingService.embed() → FloatArray(768)
         → ConversationDao.getConversationsWithEmbeddings() → List<ConversationEntity>
         → For each conversation:
           EmbeddingService.jsonToEmbedding() → FloatArray(768)
           SimilarityUtils.cosineSimilarity() → Float (0.0-1.0)
         → Filter conversations (similarity > 0.7) → List<ConversationEntity>
```

### 3. Construction contexte

```
SimilarConversations → RAGService.buildContext() → String (context)
                    → RAGService.limitTokens() → String (context limité)
                    → KittAIService.processUserInput() → Prompt avec contexte
```

### 4. Génération réponse

```
EnhancedPrompt → Ollama API → AI Response
              → ConversationDao.insert() → Room DB
              → EmbeddingService.embedConversation() → Embedding (background)
              → ConversationDao.updateEmbeddings() → Room DB
```

## Performance

### Temps de traitement

| Étape | Temps moyen | Notes |
|-------|-------------|-------|
| Génération embedding | 200-500ms | Dépend du modèle et longueur texte |
| Recherche similarité (100 conv) | 10-50ms | Optimisé avec cosine similarity |
| Construction contexte | 5-20ms | Dépend du nombre de conversations |
| **Total RAG overhead** | **300-800ms** | Ajouté au temps de réponse normal |

### Optimisations

1. **Embeddings générés en arrière-plan** : Ne bloque pas la réponse utilisateur
2. **Batch processing** : Traitement par batches de 10 conversations pour migration
3. **Cache embeddings** : Embeddings stockés dans Room DB, pas besoin de régénérer
4. **Similarity score threshold** : Seulement conversations avec score > 0.7 inclus

## Limitations

1. **Ollama local uniquement** : RAG nécessite Ollama local (pas de cloud pour embeddings)
2. **Stockage** : Chaque embedding prend ~3KB (768 dimensions × 4 bytes)
3. **Temps de traitement** : Ajout de 300-800ms au temps de réponse
4. **Similarité minimale** : Score minimum de 0.7 pour inclure une conversation

## Extension future

### Améliorations possibles

1. **Cache sémantique** : Cache des recherches similaires pour éviter recalculs
2. **Embeddings multiples** : Générer embeddings pour userMessage et aiResponse séparément
3. **Filtrage temporel** : Prioriser conversations récentes dans recherche
4. **Compression embeddings** : Réduire dimensions avec PCA ou quantisation
5. **Ollama Cloud embeddings** : Support embeddings sur Ollama Cloud quand disponible

## Exemples de code

### Génération embedding manuelle

```kotlin
val embeddingService = EmbeddingService(context)

lifecycleScope.launch {
    val embedding = embeddingService.embedConversation(
        userMessage = "Comment créer une liste?",
        aiResponse = "Utilisez la syntaxe [1, 2, 3]..."
    )
    
    if (embedding != null) {
        val json = embeddingService.embeddingToJson(embedding)
        conversationDao.updateEmbeddings(conversationId, json)
    }
}
```

### Recherche sémantique manuelle

```kotlin
val embeddingService = EmbeddingService(context)
val userEmbedding = embeddingService.embed("Python list")

if (userEmbedding != null) {
    val allConversations = conversationDao.getConversationsWithEmbeddings()
    val similar = allConversations.filter { conv ->
        val convEmbedding = embeddingService.jsonToEmbedding(conv.embeddingsJson)
        if (convEmbedding != null) {
            val similarity = SimilarityUtils.cosineSimilarity(userEmbedding, convEmbedding)
            similarity > 0.7f
        } else {
            false
        }
    }
}
```

### Construction contexte manuelle

```kotlin
val ragService = RAGService(context)
val context = ragService.buildContext(
    userInput = "Quelle était ma question sur Python?",
    conversations = similarConversations
)

val limitedContext = ragService.limitTokens(context, maxTokens = 2000)
```

## Ressources

- [Ollama Embeddings API](https://github.com/ollama/ollama/blob/main/docs/api.md#generate-embeddings)
- [nomic-embed-text Model](https://huggingface.co/nomic-ai/nomic-embed-text-v1)
- [Cosine Similarity Wikipedia](https://en.wikipedia.org/wiki/Cosine_similarity)
- [RAG Paper](https://arxiv.org/abs/2005.11401)

