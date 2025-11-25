# Continuité Conversationnelle dans ChatAI

## Vue d'ensemble

ChatAI utilise **3 mécanismes** pour maintenir la continuité conversationnelle :

1. **Historique en mémoire** (10 derniers échanges) - Continuïté immédiate
2. **RAG** (Recherche sémantique) - Continuïté à long terme
3. **sessionId** (Groupement par session) - Organiser les conversations

## Mécanisme 1: Historique en mémoire

### Fonctionnement

- `KittAIService` charge les **10 dernières conversations** au démarrage
- Chaque nouvelle requête inclut ces **10 derniers échanges** dans le prompt
- L'historique est mis à jour après chaque réponse

### Code

```kotlin
// Chargement au démarrage
val recentConversations = conversationDao.getLastConversations(limit = 10)
conversationHistory.clear()
recentConversations.reversed().forEach { conv ->
    conversationHistory.add(Pair(conv.userMessage, conv.aiResponse))
}

// Envoi dans chaque requête API
conversationHistory.takeLast(CONTEXT_WINDOW_SIZE).forEach { (user, assistant) ->
    messages.put(JSONObject().apply {
        put("role", "user")
        put("content", user)
    })
    messages.put(JSONObject().apply {
        put("role", "assistant")
        put("content", assistant)
    })
}
```

### Limitations

- **10 échanges maximum** (environ 5 aller-retours)
- **Perdu au redémarrage** de l'app si pas sauvegardé (mais rechargé depuis BD)
- **Limité par la mémoire** (10 échanges = ~2000-5000 tokens selon longueur)

### Exemple

**Conversation:**
1. Vous: "Bonjour KITT"
2. KITT: "Bonjour Michael, comment puis-je vous aider?"
3. Vous: "Quelle est la météo?"
4. KITT: "Je n'ai pas accès à la météo en temps réel..."
5. Vous: "Et pour demain?" ← **A accès aux 4 messages précédents**

## Mécanisme 2: RAG (Recherche Sémantique)

### Fonctionnement

- **RAG** recherche dans **TOUTES** les conversations passées (pas seulement les 10 derniers)
- Utilise des **embeddings** pour trouver des conversations similaires
- Injecte le contexte trouvé dans le prompt

### Code

```kotlin
// Recherche sémantique
val queryEmbedding = embeddingService.embed(userInput)
val similarConversations = ragService.searchSimilarConversations(
    queryEmbedding = queryEmbedding,
    topK = 5
)

// Construction du contexte
val ragContext = ragService.buildRAGContext(similarConversations)
// → Ajouté au system prompt ou au début des messages
```

### Avantages

- **Accès à tout l'historique** (pas seulement 10 derniers)
- **Recherche par similarité sémantique** (pas par mots-clés)
- **Continuïté à long terme** (semaines, mois)

### Limitations

- **Nécessite Ollama local** (embeddings pas disponibles sur Cloud)
- **Nécessite modèle d'embedding** installé (`nomic-embed-text`)
- **Score minimum 0.7** pour inclure une conversation

### Exemple

**Historique complet (100 conversations):**
- Conversation #5 (il y a 2 semaines): "Comment créer une liste Python?"
- Conversation #87 (il y a 1 mois): "Différence entre liste et tuple?"

**Nouvelle question:**
- Vous: "Quelle était ma question sur Python?" ← **RAG trouve conversation #5**

## Mécanisme 3: sessionId

### Fonctionnement

- Chaque instance `KittAIService` a un **sessionId unique** (UUID)
- Toutes les conversations d'une session partagent le même `sessionId`
- Permet de **grouper** les conversations d'une session

### Code

```kotlin
// Création du sessionId
private val sessionId = UUID.randomUUID().toString()

// Sauvegarde avec sessionId
val conversation = ConversationEntity(
    conversationId = conversationId,
    userMessage = userInput,
    aiResponse = response,
    sessionId = sessionId, // ← Groupement
    // ...
)
```

### Utilisation

- **Recherche par session**: `getConversationsBySession(sessionId)`
- **Statistiques par session**: Compter conversations par sessionId
- **Export par session**: Exporter toutes les conversations d'une session

### Limitations

- **Nouveau sessionId à chaque redémarrage** de l'app
- **Pas de regroupement automatique** entre sessions (si app redémarrée)

## Exemple Complet: Comment ça marche

### Scénario

**Session 1 (Lundi):**
1. Vous: "Bonjour KITT"
2. KITT: "Bonjour Michael!"
3. Vous: "Je m'appelle John, pas Michael"
4. KITT: "Désolé John, je me souviendrai"

**Session 2 (Mardi - App redémarrée):**
1. Vous: "Comment je m'appelle?" 
   - **Historique en mémoire**: 0 échanges (nouvelle session)
   - **RAG**: Trouve conversation #3 (lundi) avec "Je m'appelle John"
   - **Résultat**: "Vous vous appelez John, je me souviens de notre conversation d'hier."

**Session 2 (suite - même session):**
2. Vous: "Qu'est-ce qu'on avait discuté hier?"
   - **Historique en mémoire**: 1 échange (question précédente)
   - **RAG**: Trouve toutes les conversations similaires de lundi
   - **Résultat**: "Hier, vous m'aviez présenté comme John et nous avions discuté..."

## Recommandations d'amélioration

### 1. Augmenter la fenêtre d'historique

**Actuel**: 10 échanges
**Proposé**: 20-30 échanges

```kotlin
private const val CONTEXT_WINDOW_SIZE = 20 // Au lieu de 10
```

**Avantages**:
- Plus de contexte immédiat
- Meilleure continuité dans la session

**Inconvénients**:
- Plus de tokens envoyés (coût si API payante)
- Peut ralentir légèrement les réponses

### 2. Persister le sessionId

**Actuel**: Nouveau sessionId à chaque redémarrage
**Proposé**: Sauvegarder sessionId dans SharedPreferences

```kotlin
// Au démarrage
val savedSessionId = sharedPreferences.getString("current_session_id", null)
val sessionId = savedSessionId ?: UUID.randomUUID().toString().also {
    sharedPreferences.edit().putString("current_session_id", it).apply()
}

// Optionnel: Nouveau sessionId après X heures d'inactivité
val lastActivity = sharedPreferences.getLong("last_activity", 0)
if (System.currentTimeMillis() - lastActivity > 24 * 3600 * 1000) {
    // Nouveau sessionId si inactivité > 24h
    sessionId = UUID.randomUUID().toString()
    sharedPreferences.edit().putString("current_session_id", sessionId).apply()
}
```

**Avantages**:
- Continuité entre redémarrages
- Meilleur groupement des conversations

### 3. Utiliser sessionId dans RAG

**Actuel**: RAG cherche dans toutes les conversations
**Proposé**: Prioriser les conversations de la même session

```kotlin
// Recherche avec priorité session
val sameSessionConversations = conversationDao.getConversationsBySession(sessionId)
val otherSessionConversations = allConversations.filter { it.sessionId != sessionId }

// Prioriser sameSessionConversations dans les résultats RAG
```

**Avantages**:
- Meilleure continuité dans la session actuelle
- Contexte plus pertinent

### 4. Historique contextuel dynamique

**Actuel**: Toujours les 10 derniers
**Proposé**: Utiliser sessionId pour filtrer l'historique

```kotlin
// Charger seulement les conversations de la session actuelle
val recentConversations = conversationDao.getConversationsBySession(sessionId)
    .sortedByDescending { it.timestamp }
    .take(20)
```

**Avantages**:
- Historique plus pertinent (même session)
- Moins de bruit des sessions précédentes

## Résumé

### Continuité actuelle

✅ **OUI**, il y a continuité via:
- 10 derniers échanges dans chaque requête
- RAG pour conversations similaires (tout l'historique)
- sessionId pour grouper les conversations

### Limitations

⚠️ **Mais limité par**:
- 10 échanges maximum (historique immédiat)
- RAG nécessite Ollama local
- Nouveau sessionId à chaque redémarrage

### Recommandations

💡 **Améliorations possibles**:
1. Augmenter fenêtre historique (10 → 20-30)
2. Persister sessionId entre redémarrages
3. Utiliser sessionId dans RAG (priorité session actuelle)
4. Historique contextuel dynamique (filtrer par session)

## Questions fréquentes

**Q: Les conversations sont-elles liées entre elles?**
R: Oui, via les 10 derniers échanges (continuité immédiate) et RAG (continuité à long terme).

**Q: Si je redémarre l'app, est-ce que KITT se souvient?**
R: Oui, via RAG qui recherche dans tout l'historique. Mais pas via l'historique en mémoire (rechargé depuis BD).

**Q: Comment améliorer la continuité?**
R: Activer RAG (Ollama local + modèle embedding) pour accès à tout l'historique.

**Q: Combien de conversations sont envoyées à l'IA?**
R: 10 derniers échanges toujours + jusqu'à 5 conversations similaires via RAG (si activé).

