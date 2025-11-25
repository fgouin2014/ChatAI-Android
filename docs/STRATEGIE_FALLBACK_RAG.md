# Stratégie de Fallback RAG - Réseau Indisponible

**Date:** 2025-01-XX  
**Contexte:** Gestion de l'indisponibilité réseau pour les serveurs RAG

---

## 🎯 PROBLÈME

**Tous les serveurs RAG nécessitent le réseau local:**
- **Ollama Local**: `http://localhost:11434` ou `http://[IP_PC]:11434`
- **RAG Server Python**: `http://[IP_PC]:8890`

**Si le réseau est indisponible:**
- Pas d'accès au PC (Ollama ou Python)
- Pas d'embeddings générés
- RAG désactivé automatiquement

---

## ✅ COMPORTEMENT ACTUEL (Sans Serveur Python)

### 1. EmbeddingService (Ollama Local)

**En cas d'erreur réseau:**
```kotlin
catch (e: IOException) {
    Log.e(TAG, "Network error generating embedding", e)
    return@withContext null  // ⭐ Retourne null, ne bloque pas
}
```

**Résultat:**
- ✅ L'embedding échoue silencieusement (`null`)
- ✅ L'app continue de fonctionner
- ✅ RAG désactivé pour cette requête uniquement

### 2. RAGService

**Gestion des embeddings null:**
```kotlin
val queryEmbedding = embeddingService?.embed(userInput)

if (queryEmbedding != null) {
    // Recherche sémantique...
} else {
    Log.d(TAG, "Failed to generate embedding for RAG, continuing without context")
    // ⭐ Continue sans contexte RAG
}
```

**Résultat:**
- ✅ Pas de crash
- ✅ L'IA répond sans contexte RAG
- ✅ Fonctionnalité dégradée mais opérationnelle

### 3. BidirectionalBridge

**Stratégie non-bloquante:**
```kotlin
catch (e: Exception) {
    Log.w(TAG, "RAG context retrieval failed, continuing without context: ${e.message}")
    // ⭐ SELON NOS RULES: Ne pas bloquer si RAG échoue
}
```

**Résultat:**
- ✅ L'IA répond normalement
- ✅ Pas de contexte RAG (réponses moins contextuelles)
- ✅ Expérience utilisateur préservée

---

## 🔄 STRATÉGIE AVEC SERVEUR PYTHON (Hybride)

### Ordre de Priorité (Fallback en Cascade)

```
1. RAG Server Python (http://[IP_PC]:8890)
   ↓ Si indisponible (timeout, erreur réseau)
2. Ollama Local (http://localhost:11434 ou http://[IP_PC]:11434)
   ↓ Si indisponible (timeout, erreur réseau)
3. RAG Désactivé (continue sans contexte)
```

### Implémentation Proposée

#### 1. EmbeddingService avec Fallback

```kotlin
suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
    // 1. Essayer RAG Server Python (si activé)
    if (ragServerEnabled) {
        try {
            val embedding = ragServerClient.embed(text)
            if (embedding != null) {
                Log.d(TAG, "✅ Embedding from RAG Server Python")
                return@withContext embedding
            }
        } catch (e: Exception) {
            Log.w(TAG, "RAG Server Python failed, trying Ollama: ${e.message}")
        }
    }
    
    // 2. Essayer Ollama Local (fallback)
    try {
        val embedding = embedViaOllama(text)
        if (embedding != null) {
            Log.d(TAG, "✅ Embedding from Ollama Local")
            return@withContext embedding
        }
    } catch (e: Exception) {
        Log.w(TAG, "Ollama Local failed: ${e.message}")
    }
    
    // 3. Échec total - retourner null (RAG désactivé)
    Log.w(TAG, "⚠️ All embedding servers unavailable, RAG disabled")
    return@withContext null
}
```

#### 2. Détection de Disponibilité

```kotlin
suspend fun isRagServerAvailable(): Boolean {
    return try {
        val response = httpClient.newCall(
            Request.Builder()
                .url("$ragServerUrl/status")
                .get()
                .build()
        ).execute()
        
        response.isSuccessful
    } catch (e: Exception) {
        false
    }
}

suspend fun isOllamaAvailable(): Boolean {
    // Utiliser la méthode existante EmbeddingService.isAvailable()
    return embeddingService.isAvailable()
}
```

#### 3. Configuration UI

**Dans la webapp (Configuration → RAG):**

```
┌─────────────────────────────────────┐
│ RAG (Recherche sémantique)          │
├─────────────────────────────────────┤
│ ☑ Activer RAG                       │
│                                     │
│ Serveur RAG:                        │
│ ○ Ollama Local (par défaut)        │
│ ○ RAG Server Python                │
│ ○ Auto (détection automatique)     │
│                                     │
│ URL RAG Server Python:             │
│ [http://192.168.x.x:8890]          │
│                                     │
│ Status:                             │
│ ✅ Ollama Local: Disponible         │
│ ⚠️ RAG Server Python: Indisponible │
└─────────────────────────────────────┘
```

---

## 📊 SCÉNARIOS DE FALLBACK

### Scénario 1: Réseau Local Disponible

**Configuration:**
- PC avec Ollama Local: ✅ Démarré
- PC avec RAG Server Python: ✅ Démarré
- Réseau WiFi: ✅ Connecté

**Comportement:**
1. ✅ RAG Server Python utilisé (si activé)
2. ✅ Ollama Local en fallback
3. ✅ RAG fonctionnel

---

### Scénario 2: Réseau Local Indisponible

**Configuration:**
- PC avec Ollama Local: ❌ Inaccessible
- PC avec RAG Server Python: ❌ Inaccessible
- Réseau WiFi: ❌ Déconnecté ou PC éteint

**Comportement:**
1. ❌ RAG Server Python: Timeout/Connection refused
2. ❌ Ollama Local: Timeout/Connection refused
3. ✅ RAG désactivé automatiquement
4. ✅ L'IA répond sans contexte RAG
5. ✅ Pas de crash, fonctionnalité dégradée

**Logs:**
```
[EmbeddingService] RAG Server Python failed: Connection refused
[EmbeddingService] Ollama Local failed: Connection refused
[EmbeddingService] ⚠️ All embedding servers unavailable, RAG disabled
[BidirectionalBridge] Failed to generate embedding for RAG, continuing without context
```

---

### Scénario 3: Un Serveur Disponible

**Configuration A:**
- PC avec Ollama Local: ✅ Démarré
- PC avec RAG Server Python: ❌ Arrêté
- Réseau WiFi: ✅ Connecté

**Comportement:**
1. ❌ RAG Server Python: Indisponible
2. ✅ Ollama Local: Utilisé (fallback)
3. ✅ RAG fonctionnel avec Ollama

**Configuration B:**
- PC avec Ollama Local: ❌ Arrêté
- PC avec RAG Server Python: ✅ Démarré
- Réseau WiFi: ✅ Connecté

**Comportement:**
1. ✅ RAG Server Python: Utilisé
2. ❌ Ollama Local: Non testé (déjà un serveur disponible)
3. ✅ RAG fonctionnel avec Python

---

## 🛡️ GESTION D'ERREURS

### Timeouts

```kotlin
private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)  // ⭐ Court pour détection rapide
    .readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(5, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()
```

**Raison:**
- Détection rapide si serveur indisponible
- Fallback immédiat vers autre serveur
- Pas d'attente longue pour l'utilisateur

### Exceptions Gérées

| Exception | Cause | Action |
|-----------|-------|--------|
| `ConnectException` | Serveur non démarré | Fallback vers autre serveur |
| `SocketTimeoutException` | Serveur trop lent | Fallback vers autre serveur |
| `UnknownHostException` | IP incorrecte | Fallback vers autre serveur |
| `IOException` | Réseau indisponible | Fallback vers autre serveur |
| `HTTP 404/501` | Endpoint non trouvé | Fallback vers autre serveur |

---

## 💡 RECOMMANDATIONS

### 1. Mode "Offline-First"

**Stratégie:**
- Utiliser les embeddings déjà stockés dans Room DB
- Recherche sémantique uniquement sur les conversations existantes
- Pas de génération d'embeddings si réseau indisponible

**Avantage:**
- RAG fonctionne même sans réseau (pour conversations existantes)
- Pas de dépendance réseau pour la recherche

**Implémentation:**
```kotlin
suspend fun searchSimilarConversations(
    queryEmbedding: FloatArray?,
    topK: Int = 5
): List<ConversationSearchResult> {
    // Si pas d'embedding (réseau indisponible), utiliser recherche textuelle
    if (queryEmbedding == null) {
        Log.d(TAG, "No embedding available, using text-based search")
        return textBasedSearch(query, topK)
    }
    
    // Sinon, recherche sémantique normale
    return semanticSearch(queryEmbedding, topK)
}
```

### 2. Cache des Embeddings

**Stratégie:**
- Stocker les embeddings dans Room DB
- Réutiliser les embeddings existants si réseau indisponible
- Générer seulement pour nouvelles conversations

**Avantage:**
- RAG fonctionne partiellement même sans réseau
- Performance améliorée (pas de régénération)

### 3. Détection Automatique

**Stratégie:**
- Tester la disponibilité au démarrage
- Tester périodiquement (toutes les 5 minutes)
- Afficher le statut dans l'UI

**Implémentation:**
```kotlin
// Au démarrage de l'app
GlobalScope.launch {
    val pythonAvailable = isRagServerAvailable()
    val ollamaAvailable = isOllamaAvailable()
    
    updateRagStatusUI(pythonAvailable, ollamaAvailable)
}

// Périodiquement
val statusCheckJob = CoroutineScope(Dispatchers.IO).launch {
    while (isActive) {
        delay(5 * 60 * 1000) // 5 minutes
        checkRagServersStatus()
    }
}
```

---

## 📋 CHECKLIST D'IMPLÉMENTATION

### Phase 1: Fallback Ollama → Python
- [ ] Créer `RagServerClient.kt`
- [ ] Modifier `EmbeddingService.embed()` pour fallback
- [ ] Ajouter détection de disponibilité
- [ ] Tester avec serveur Python indisponible

### Phase 2: Mode Offline
- [ ] Recherche textuelle si embedding null
- [ ] Utiliser embeddings existants de Room DB
- [ ] Cache des embeddings récents

### Phase 3: UI et Feedback
- [ ] Afficher statut serveurs dans Configuration
- [ ] Indicateur RAG actif/inactif dans chat
- [ ] Messages d'erreur clairs pour l'utilisateur

---

## 🎯 CONCLUSION

**Stratégie recommandée:**
1. ✅ Fallback en cascade (Python → Ollama → Désactivé)
2. ✅ Mode non-bloquant (continue même si RAG échoue)
3. ✅ Utilisation des embeddings existants si réseau indisponible
4. ✅ Feedback clair à l'utilisateur

**Résultat:**
- ✅ Robustesse maximale
- ✅ Fonctionnalité dégradée mais opérationnelle
- ✅ Expérience utilisateur préservée

---

**Références:**
- `EmbeddingService.kt` - Gestion actuelle des erreurs
- `RAGService.kt` - Recherche sémantique
- `BidirectionalBridge.kt` - Intégration RAG

