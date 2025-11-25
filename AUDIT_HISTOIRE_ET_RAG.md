# 📊 Audit: Historique des conversations et RAG

**Date**: 2025-11-19  
**Objet**: Audit de l'architecture actuelle pour amélioration historique et implémentation RAG

---

## 🔍 État actuel de l'architecture

### ✅ Ce qui existe déjà

#### 1. **Base de données Room** (`ConversationEntity.kt`)
- ✅ Table `conversations` complète avec tous les champs nécessaires
- ✅ Champs pour RAG futur : `embeddingsJson`, `tags`
- ✅ Thinking trace pour apprentissage
- ✅ Métadonnées complètes (personality, API, platform, sessionId)
- ✅ UUID pour traçabilité (`conversationId`)

#### 2. **DAO avec recherche** (`ConversationDao.kt`)
- ✅ Recherche textuelle simple : `searchConversations(query)`
- ✅ Filtres par personality, platform, API, date
- ✅ Statistiques : `getTotalConversations()`, `getAverageResponseTime()`, etc.
- ✅ Export : `getAllConversationsForExport()`
- ✅ Méthode pour RAG : `getConversationsWithEmbeddings()`

#### 3. **Activité historique** (`ConversationHistoryActivity.kt`)
- ✅ Affichage liste des conversations
- ✅ Statistiques basiques (total, par personality, API, temps moyen)
- ✅ Export logcat (déjà fonctionnel)
- ✅ Export JSON (déjà fonctionnel)
- ✅ Import JSON (déjà fonctionnel)
- ✅ Effacement historique

#### 4. **Sauvegarde automatique**
- ✅ `KittAIService.kt` sauvegarde automatiquement dans Room
- ✅ `BidirectionalBridge.kt` devrait sauvegarder aussi (à vérifier)

#### 5. **Webapp (localStorage)**
- ⚠️ Historique dans `localStorage` (non synchronisé avec Room)
- ⚠️ `chat-messaging.js` utilise `conversationHistory` en mémoire

---

## 🎯 Fonctionnalité 1: Historique amélioré

### 📋 Exigences
- Recherche dans l'historique
- Export JSON/HTML
- Statistiques améliorées (nombre de messages, temps total)

### ✅ Ce qui existe déjà
1. ✅ Recherche textuelle SQL (`searchConversations()`)
2. ✅ Export JSON (`exportConversationsToJson()`)
3. ✅ Statistiques basiques

### ⚠️ Ce qui manque

#### A. Recherche améliorée dans l'UI
- [ ] Barre de recherche dans `ConversationHistoryActivity`
- [ ] Recherche en temps réel (filtre RecyclerView)
- [ ] Filtres avancés (date range, personality, platform)
- [ ] Highlight des termes recherchés

#### B. Export HTML
- [ ] Format HTML lisible avec styles
- [ ] Option dans le menu d'export
- [ ] Génération de fichier HTML dans `getExternalFilesDir()`

#### C. Statistiques améliorées
- [ ] Temps total de conversation (somme des `responseTimeMs`)
- [ ] Nombre de messages total (différent de conversations)
- [ ] Graphique d'activité (messages par jour/semaine)
- [ ] Top topics (tags les plus utilisés)
- [ ] Longueur moyenne des messages

#### D. Synchronisation Webapp ↔ Room
- [ ] Sauvegarder messages webapp dans Room (pas juste localStorage)
- [ ] Charger historique Room dans webapp au démarrage
- [ ] Unifier `conversationHistory` webapp avec Room

---

## 🚀 Fonctionnalité 2: RAG (Retrieval-Augmented Generation)

### 📋 Exigences
- Vector database pour conversations
- Recherche sémantique dans l'historique
- Contexte pertinent automatique
- Mémoire long terme

### ✅ Ce qui existe déjà
1. ✅ Champs `embeddingsJson` dans `ConversationEntity`
2. ✅ Méthode DAO `getConversationsWithEmbeddings()`
3. ✅ Méthode DAO `updateEmbeddings(id, embeddings)`

### ⚠️ Ce qui manque complètement

#### A. Génération d'embeddings
- [ ] Service d'embedding (Ollama local ou cloud)
- [ ] Calcul d'embeddings pour chaque nouvelle conversation
- [ ] Stockage dans `embeddingsJson` (JSON array de floats)

#### B. Vector Database
- [ ] Choix: SQLite FTS5 + embeddings ou bibliothèque dédiée (ex: SQLite-Vector)
- [ ] Index pour recherche vectorielle
- [ ] Similarity search (cosine similarity)

#### C. Recherche sémantique
- [ ] Convertir requête utilisateur en embedding
- [ ] Comparer avec embeddings stockés
- [ ] Retourner top-K conversations similaires
- [ ] Score de pertinence (`relevanceScore`)

#### D. Contexte automatique
- [ ] Intégrer dans `BidirectionalBridge.processWithThinking()`
- [ ] Récupérer contexte pertinent avant appel Ollama
- [ ] Injecter contexte dans system prompt
- [ ] Limite de tokens pour contexte (ex: 2000 tokens)

---

## 💡 Recommandations

### Priorité 1: Historique amélioré (2-3h)
**Complexité**: Faible ✅  
**Valeur**: Élevée

1. **Recherche UI** (1h)
   - Ajouter `SearchView` dans `ConversationHistoryActivity`
   - Filtrer RecyclerView avec `searchConversations()`
   - Highlight termes recherchés

2. **Export HTML** (30min)
   - Template HTML avec styles
   - Génération fichier HTML
   - Option menu export

3. **Statistiques améliorées** (1h)
   - Calcul temps total, nombre messages
   - Affichage dans `loadStats()`
   - Graphique simple (optionnel)

### Priorité 2: RAG (2-3 jours)
**Complexité**: Élevée ⚠️  
**Valeur**: Très élevée (mémoire persistante)

#### Phase 1: Embeddings (1 jour)
1. **Service d'embedding**
   - Utiliser Ollama local (`nomic-embed-text` ou `mxbai-embed-large`)
   - API endpoint: `/api/embeddings`
   - Service Android: `EmbeddingService.kt`

2. **Génération automatique**
   - Après sauvegarde conversation → calculer embedding
   - Stocker dans `embeddingsJson`
   - Gestion d'erreurs (si Ollama non disponible)

#### Phase 2: Vector Search (1 jour)
1. **Base vectorielle**
   - Option A: SQLite FTS5 + embeddings JSON (simple)
   - Option B: SQLite-Vector extension (performant)
   - Recommandation: **Option A** pour débuter (migration facile)

2. **Similarity search**
   - Fonction cosine similarity en Kotlin
   - Requête DAO: `searchSimilarConversations(queryEmbedding, limit)`
   - Score de pertinence (0.0-1.0)

#### Phase 3: Intégration contexte (1 jour)
1. **Récupération contexte**
   - Dans `BidirectionalBridge.processWithThinking()`
   - Convertir `userInput` en embedding
   - Récupérer top-5 conversations similaires

2. **Injection contexte**
   - Construire system prompt avec contexte
   - Limiter à 2000 tokens
   - Envoyer à Ollama avec contexte

---

## 🔄 Plan d'implémentation

### Étape 1: Historique amélioré (FAIT EN PREMIER)
```
1. Recherche UI dans ConversationHistoryActivity (1h)
2. Export HTML (30min)
3. Statistiques améliorées (1h)
4. Synchronisation Webapp ↔ Room (30min)
Total: ~3h ✅
```

### Étape 2: RAG Phase 1 - Embeddings
```
1. EmbeddingService.kt pour Ollama (2h)
2. Génération auto après sauvegarde (1h)
3. Tests avec conversations existantes (1h)
Total: ~4h
```

### Étape 3: RAG Phase 2 - Vector Search
```
1. Fonction cosine similarity (1h)
2. DAO searchSimilarConversations() (2h)
3. Tests recherche sémantique (1h)
Total: ~4h
```

### Étape 4: RAG Phase 3 - Intégration
```
1. Récupération contexte dans BidirectionalBridge (2h)
2. Injection dans system prompt (1h)
3. Tests end-to-end (1h)
Total: ~4h
```

**Total RAG**: ~12h (1.5 jours) ✅

---

## ⚠️ Points d'attention

### 1. Performance
- Embeddings: Calcul coûteux (limiter à conversations récentes?)
- Vector search: Peut être lent sur grande base (index nécessaire)
- Contexte: Limiter nombre de tokens pour éviter coûts Ollama

### 2. Stockage
- Embeddings JSON: ~1KB par conversation (1536 floats = 6KB)
- 1000 conversations = ~6MB embeddings
- **Solution**: Compression ou embeddings réduits (ex: 384 dimensions)

### 3. Migration
- Anciennes conversations sans embeddings
- Calcul rétroactif (background job?)
- Option: Embeddings à la demande (lazy loading)

### 4. Ollama Embedding Model
- **Recommandé**: `nomic-embed-text` (768 dimensions) ou `mxbai-embed-large` (1024)
- Vérifier disponibilité sur device
- Fallback si model non disponible

---

## 📝 Questions à discuter

1. **Historique amélioré**:
   - Faut-il un graphique d'activité ou juste statistiques textuelles?
   - Export HTML avec styles personnalisés (KITT theme)?

2. **RAG**:
   - Utiliser Ollama local uniquement ou cloud aussi?
   - Dimension embeddings? (768 vs 1536)
   - Limite de tokens pour contexte? (1500 vs 2000)
   - Calcul embeddings rétroactif ou seulement nouvelles conversations?

3. **Performance**:
   - Indexer toutes les conversations ou seulement récentes?
   - Cache des embeddings en mémoire?

---

## 🎯 Conclusion

### ✅ Prêt pour Historique amélioré
- Infrastructure Room complète
- DAO avec recherche déjà fonctionnel
- Activité historique existante
- **Temps estimé**: 2-3h ✅

### ⚠️ RAG nécessite plus de travail
- Infrastructure embeddings manquante
- Vector search à implémenter
- Intégration contexte à faire
- **Temps estimé**: 2-3 jours ⚠️

### 💡 Recommandation
1. **Commencer par Historique amélioré** (quick win, 2-3h)
2. **Puis RAG Phase 1** (embeddings, 1 jour)
3. **Tester et itérer** avant Phase 2/3


