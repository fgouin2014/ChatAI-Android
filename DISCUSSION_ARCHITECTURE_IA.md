# Discussion: Architecture Globale du Système IA

**Date:** 2025-12-01  
**Objectif:** Comprendre et clarifier le fonctionnement interne complet du système IA

---

## 🎯 CONTEXTE

Vous avez mentionné que ce n'est pas seulement la webapp mais **tout le fonctionnement interne de l'IA** qui vous préoccupe.

**Questions à explorer:**
- Comment fonctionne réellement le système IA de bout en bout?
- Quelles sont les incohérences entre les différents composants?
- Y a-t-il de la confusion dans l'architecture actuelle?
- Que voulez-vous vraiment comme système?

---

## 🧩 ARCHITECTURE ACTUELLE (État des lieux)

### Flow Principal Actuel

```
USER INPUT (texte ou vocal)
    ↓
┌─────────────────────────────────────────┐
│  BidirectionalBridge.kt                 │
│  - processWithThinking()                │
│  - Détecte RAG si activé                │
│  - Appelle KittAIService                │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  KittAIService.kt                       │
│  - processUserInput()                   │
│  - Détecte actions (function calling)   │
│  - Détecte heure/temps                  │
│  - Détecte web search nécessaire        │
│  - Appelle API selon forced_api_mode     │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  API Selection (forced_api_mode)       │
│  - "huggingface" → HuggingFaceService   │
│  - "ollama_cloud" → OllamaCloud API     │
│  - Pas de fallback auto (désactivé)     │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  RAG (si activé)                        │
│  - EmbeddingService.embed()             │
│  - RAGService.searchSimilar()           │
│  - Contexte ajouté au prompt            │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  Réponse IA                             │
│  - Sauvegarde en BD                     │
│  - Thinking trace (si Ollama)           │
│  - Retour à l'utilisateur               │
└─────────────────────────────────────────┘
```

---

## 🔍 POINTS DE CONFUSION POTENTIELS

### 1. **Multiples Services IA**

**Services existants:**
- `KittAIService.kt` - Service principal (2500+ lignes)
- `OllamaThinkingService.kt` - Thinking trace uniquement
- `HuggingFaceService.kt` - Hugging Face uniquement
- `SimpleLocalService.kt` - Service local (non utilisé?)
- `RealtimeAIService.java` - Service temps réel (non utilisé?)

**Question:** Pourquoi plusieurs services? Y a-t-il de la redondance?

---

### 2. **Flow de RAG**

**Actuel:**
- `BidirectionalBridge` détecte RAG
- Appelle `RAGService` pour chercher conversations similaires
- `EmbeddingService` génère embeddings
- Contexte ajouté au prompt

**Question:** Le RAG fonctionne-t-il correctement? Y a-t-il des incohérences?

---

### 3. **Mode API (forced_api_mode)**

**Actuel:**
- `forced_api_mode` = "huggingface" ou "ollama_cloud"
- Pas de fallback automatique
- Mode explicite requis

**Question:** Est-ce que cette logique est claire? Y a-t-il de la confusion?

---

### 4. **Embeddings Sources**

**Actuel:**
- Hugging Face (cloud)
- ONNX Local (device)
- Ollama Cloud (404 - non disponible)

**Question:** Pourquoi Ollama Cloud embeddings est affiché si non disponible?

---

### 5. **Web Search**

**Actuel:**
- Détecté dans `KittAIService.needsWebSearch()`
- Appelé via `callWebSearchAPI()` (Ollama uniquement)
- Nécessite Ollama Cloud activé

**Question:** Est-ce que le web search fonctionne? Y a-t-il des problèmes?

---

### 6. **Thinking Trace**

**Actuel:**
- `OllamaThinkingService` pour thinking trace
- Mais `BidirectionalBridge` utilise `KittAIService` directement
- `OllamaThinkingService` n'est peut-être pas utilisé?

**Question:** Le thinking trace fonctionne-t-il? Quel service est vraiment utilisé?

---

## 💬 QUESTIONS POUR VOUS

### Architecture Globale

1. **Quelle est votre vision du système IA?**
   - Un seul service centralisé?
   - Plusieurs services spécialisés?
   - Architecture modulaire?

2. **Quels sont les problèmes que vous observez?**
   - Confusion dans le code?
   - Comportement inattendu?
   - Performance?
   - Complexité?

3. **Qu'est-ce qui ne fonctionne pas comme prévu?**
   - RAG ne fonctionne pas?
   - Web search ne fonctionne pas?
   - Thinking trace ne fonctionne pas?
   - Modes API confus?

### Flow de Données

4. **Comment voulez-vous que le système fonctionne?**
   - Flow simple: User → API → Response?
   - Flow complexe: User → RAG → Web Search → API → Response?
   - Flow avec thinking trace visible?

5. **Quel est le rôle de chaque service?**
   - `KittAIService` = Orchestrateur principal?
   - `OllamaThinkingService` = Thinking uniquement?
   - `HuggingFaceService` = Hugging Face uniquement?
   - `BidirectionalBridge` = Communication webapp?

### Configuration

6. **Comment voulez-vous configurer le système?**
   - Mode unique (Hugging Face OU Ollama)?
   - Mode avec fallback automatique?
   - Mode multi-API avec sélection intelligente?

7. **RAG: Actif par défaut ou manuel?**
   - Auto-configuration?
   - Configuration manuelle?
   - Désactivé par défaut?

---

## 🎯 PROPOSITION DE DISCUSSION STRUCTURÉE

### Étape 1: Identifier les Problèmes
- Qu'est-ce qui ne fonctionne pas?
- Qu'est-ce qui est confus?
- Qu'est-ce qui est incohérent?

### Étape 2: Définir la Vision
- Comment voulez-vous que ça fonctionne?
- Quelle est l'architecture idéale?
- Quels sont les objectifs?

### Étape 3: Analyser l'Existant
- Qu'est-ce qui fonctionne bien?
- Qu'est-ce qui doit être gardé?
- Qu'est-ce qui doit être supprimé?

### Étape 4: Proposer une Architecture
- Architecture simplifiée?
- Architecture modulaire?
- Architecture hybride?

### Étape 5: Plan d'Action
- Quelles modifications?
- Dans quel ordre?
- Comment tester?

---

## 📊 AUDIT RAPIDE DES SERVICES

### KittAIService.kt
- **Rôle:** Service principal d'IA
- **Fonctions:** processUserInput(), tryHuggingFace(), tryOllamaCloud()
- **Utilisé par:** BidirectionalBridge, KittFragment
- **Taille:** ~2500 lignes
- **État:** ✅ Actif

### OllamaThinkingService.kt
- **Rôle:** Thinking trace pour Ollama
- **Fonctions:** generateThinkingStream()
- **Utilisé par:** ???
- **Taille:** ~200 lignes
- **État:** ⚠️ Peut-être non utilisé?

### HuggingFaceService.kt
- **Rôle:** Service Hugging Face dédié
- **Fonctions:** generateText(), generateEmbedding()
- **Utilisé par:** KittAIService, EmbeddingService
- **Taille:** ~300 lignes
- **État:** ✅ Actif

### BidirectionalBridge.kt
- **Rôle:** Communication webapp ↔ Android
- **Fonctions:** processWithThinking()
- **Utilisé par:** WebAppInterface
- **Taille:** ~400 lignes
- **État:** ✅ Actif

### EmbeddingService.kt
- **Rôle:** Génération embeddings
- **Fonctions:** embed()
- **Utilisé par:** RAGService
- **Taille:** ~200 lignes
- **État:** ✅ Actif

### RAGService.kt
- **Rôle:** Recherche sémantique
- **Fonctions:** searchSimilar()
- **Utilisé par:** BidirectionalBridge
- **Taille:** ~150 lignes
- **État:** ✅ Actif

---

## 🤔 QUESTIONS OUVERTES

1. **Pourquoi `OllamaThinkingService` existe-t-il si `BidirectionalBridge` utilise `KittAIService` directement?**
   - Est-ce du code mort?
   - Y a-t-il un usage prévu?

2. **Pourquoi `SimpleLocalService` existe-t-il?**
   - Est-ce du code mort?
   - Y a-t-il un usage prévu?

3. **Pourquoi `RealtimeAIService.java` existe-t-il?**
   - Est-ce du code mort?
   - Y a-t-il un usage prévu?

4. **Le flow RAG est-il correct?**
   - Embeddings générés correctement?
   - Recherche sémantique fonctionnelle?
   - Contexte ajouté correctement?

5. **Le web search fonctionne-t-il?**
   - Détection correcte?
   - Appel API fonctionnel?
   - Résultats utilisés?

---

## 🎯 PROCHAINES ÉTAPES

**Avant de modifier quoi que ce soit, discutons:**

1. **Quels sont vos problèmes réels?**
   - Décrivez ce qui ne fonctionne pas
   - Décrivez ce qui est confus
   - Décrivez ce qui est incohérent

2. **Quelle est votre vision?**
   - Comment voulez-vous que ça fonctionne?
   - Quelle architecture préférez-vous?
   - Quels sont vos objectifs?

3. **Qu'est-ce qui est prioritaire?**
   - Simplifier l'architecture?
   - Corriger les bugs?
   - Améliorer les performances?
   - Clarifier le code?

---

**Je suis là pour écouter et comprendre avant de proposer des solutions.**

**Par où voulez-vous commencer?**

