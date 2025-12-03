# Audit: Incohérences Interface Webapp vs Capacités Réelles

**Date:** 2025-12-01  
**Objectif:** Identifier toutes les incohérences entre l'interface web et les capacités réelles du backend

---

## 🔍 CAPACITÉS RÉELLES DU BACKEND

### ✅ Services Implémentés et Actifs

1. **KittAIService** (Flow principal)
   - ✅ **Hugging Face** (LLM + Embeddings)
   - ✅ **Ollama Cloud** (LLM)
   - ❌ **OpenAI** (code existe mais PAS dans flow principal)
   - ❌ **Anthropic** (code existe mais PAS dans flow principal)
   - ❌ **Groq** (NON implémenté)
   - ❌ **Perplexity** (NON implémenté)

2. **EmbeddingService**
   - ✅ Hugging Face (embeddings)
   - ✅ ONNX Local (embeddings)
   - ⚠️ Ollama Cloud (embeddings) - endpoint `/api/embeddings` retourne 404 (pas encore supporté)

3. **VisionService**
   - ✅ ONNX CLIP (analyse d'images)

4. **TranslationService**
   - ✅ ONNX MarianMT (traduction FR→EN)

5. **RAGService**
   - ✅ Fonctionnel avec EmbeddingService

6. **Plugins (HttpServer)**
   - ✅ Weather (`/api/weather/{city}`)
   - ✅ Jokes (`/api/jokes/random`)
   - ✅ Tips (`/api/tips/{category}`)
   - ⚠️ Web Search (`/api/search`) - utilise Ollama web_search (nécessite Ollama Cloud activé)
   - ❌ Calculator - Frontend seulement (pas de backend)

---

## ❌ INCOHÉRENCES IDENTIFIÉES

### 1. ONGLET CLOUD - Providers Non Utilisés

**Problème:**
- Interface affiche: Hugging Face, Ollama Cloud, OpenAI, Autres Providers (Groq, Perplexity, Anthropic)
- Backend utilise SEULEMENT: Hugging Face, Ollama Cloud

**Détails:**
- ✅ **Hugging Face** → Utilisé dans flow principal
- ✅ **Ollama Cloud** → Utilisé dans flow principal
- ❌ **OpenAI** → Code existe (`tryOpenAI`) mais JAMAIS appelé dans `processUserInput`
- ❌ **Anthropic** → Code existe (`tryAnthropic`) mais JAMAIS appelé dans `processUserInput`
- ❌ **Groq** → NON implémenté
- ❌ **Perplexity** → NON implémenté

**Impact:** Confusion utilisateur, configuration inutile

---

### 2. ONGLET LOCAL - Modèles Device GGUF

**Problème:**
- Interface affiche liste des modèles GGUF sur device
- Backend: Ollama PC supprimé, modèles device GGUF plus utilisés pour LLM

**Détails:**
- Interface montre: "Modèles Locaux (Device)" avec scan de GGUF
- Backend: Plus de support pour modèles device GGUF dans flow LLM
- Les modèles device sont scannés mais jamais utilisés

**Impact:** Interface trompeuse, suggère une fonctionnalité inexistante

---

### 3. ONGLET AUDIO - Vision dans Audio

**Problème:**
- Interface: Onglet "Audio" contient section "Vision"
- Backend: Vision est un service séparé (VisionService)

**Détails:**
- Section "Vision" dans onglet "Audio" (ligne 532-564)
- Vision devrait être dans onglet "Local" ou séparé
- Confusion organisationnelle

**Impact:** Navigation confuse, mauvaise organisation

---

### 4. PLUGINS - Calculator Non Implémenté

**Problème:**
- Interface: Bouton "Calculator" dans plugins
- Backend: Calculator est frontend-only (JavaScript), pas de backend

**Détails:**
- Calculator fonctionne côté client (JavaScript)
- Pas de problème technique mais peut être confus

**Impact:** Mineur (fonctionne mais pas de backend)

---

### 5. ONGLET CLOUD - Ollama Cloud Embeddings

**Problème:**
- Interface: Propose "Ollama Cloud" comme source d'embeddings
- Backend: Ollama Cloud `/api/embeddings` retourne 404 (pas encore supporté)

**Détails:**
- RAG propose "Ollama Cloud" comme option
- EmbeddingService essaie Ollama Cloud mais reçoit 404
- Fallback vers ONNX local

**Impact:** Option affichée mais non fonctionnelle

---

### 6. ONGLET GÉNÉRAL - Modèles Hugging Face

**Problème:**
- Interface: Liste limitée de modèles Hugging Face (2 options)
- Backend: Supporte n'importe quel modèle Hugging Face via Inference API

**Détails:**
- Interface: SmolLM3-3B-Instruct, Phi-3-mini-4k-instruct, custom
- Backend: Accepte n'importe quel modèle au format `{org}/{model}:hf-inference`
- Liste trop restrictive

**Impact:** Limite les options utilisateur

---

### 7. ONGLET GÉNÉRAL - Modèles Ollama Cloud

**Problème:**
- Interface: Liste de modèles Ollama Cloud (gpt-oss:120b, etc.)
- Backend: Supporte n'importe quel modèle Ollama Cloud

**Détails:**
- Interface: Liste prédéfinie + custom
- Backend: Accepte n'importe quel modèle Ollama Cloud
- OK mais pourrait être amélioré avec liste dynamique

**Impact:** Mineur (fonctionne avec custom)

---

### 8. ONGLET AVANCÉ - Web Search Provider

**Problème:**
- Interface: Champ "Provider Web Search" (input texte)
- Backend: Utilise Ollama web_search (nécessite Ollama Cloud)

**Détails:**
- Interface permet de saisir n'importe quel provider
- Backend utilise uniquement Ollama web_search
- Pas de validation/clarification

**Impact:** Confusion sur les providers supportés

---

## 📊 RÉSUMÉ DES INCOHÉRENCES

| Section | Problème | Gravité | Impact |
|---------|----------|---------|--------|
| **Cloud - OpenAI** | Affiché mais non utilisé | 🔴 Haute | Configuration inutile |
| **Cloud - Anthropic** | Affiché mais non utilisé | 🔴 Haute | Configuration inutile |
| **Cloud - Groq/Perplexity** | Affichés mais non implémentés | 🔴 Haute | Configuration inutile |
| **Local - Modèles GGUF** | Affichés mais non utilisés | 🟡 Moyenne | Interface trompeuse |
| **Audio - Vision** | Mauvais onglet | 🟡 Moyenne | Navigation confuse |
| **RAG - Ollama Embeddings** | Option non fonctionnelle | 🟡 Moyenne | Erreur 404 silencieuse |
| **Plugins - Calculator** | Frontend only | 🟢 Basse | Fonctionne mais pas de backend |
| **Général - Modèles HF** | Liste trop restrictive | 🟢 Basse | Limite options |

---

## 🎯 RECOMMANDATIONS POUR REFONTE

### 1. Simplifier Onglet Cloud
- ✅ **Garder:** Hugging Face, Ollama Cloud
- ❌ **Supprimer:** OpenAI, Anthropic, Groq, Perplexity (ou les déplacer dans "Avancé" avec note "Non utilisé dans flow principal")

### 2. Réorganiser Onglet Local
- ✅ **Garder:** Vision ONNX, Translation ONNX
- ❌ **Supprimer ou clarifier:** Modèles device GGUF (afficher comme "Informations seulement - Non utilisés pour LLM")

### 3. Réorganiser Onglet Audio
- ✅ **Déplacer Vision** vers onglet "Local" ou créer onglet "Vision"
- ✅ **Garder Audio/STT** dans onglet "Audio"

### 4. Clarifier RAG
- ⚠️ **Marquer Ollama Cloud embeddings** comme "Non disponible (endpoint 404)"
- ✅ **Garder:** Hugging Face, ONNX Local

### 5. Améliorer Modèles
- ✅ **Hugging Face:** Ajouter plus d'options ou permettre recherche
- ✅ **Ollama Cloud:** Option de liste dynamique depuis API

### 6. Clarifier Web Search
- ✅ **Changer** "Provider Web Search" en "Ollama Web Search" (checkbox)
- ✅ **Clarifier** que nécessite Ollama Cloud activé

---

## 🔧 PLAN D'ACTION PROPOSÉ

### Phase 1: Nettoyage (Supprimer non-fonctionnel)
1. Supprimer OpenAI, Anthropic, Groq, Perplexity de l'onglet Cloud
2. Supprimer ou clarifier modèles device GGUF
3. Déplacer Vision hors de l'onglet Audio

### Phase 2: Clarification (Marquer limitations)
1. Marquer Ollama Cloud embeddings comme "Non disponible"
2. Clarifier Web Search = Ollama uniquement
3. Ajouter notes explicatives

### Phase 3: Amélioration (Enrichir fonctionnel)
1. Améliorer sélection modèles Hugging Face
2. Ajouter liste dynamique Ollama Cloud
3. Améliorer feedback utilisateur

---

**Conclusion:** L'interface affiche plusieurs options non fonctionnelles ou non utilisées, créant de la confusion. Une refonte est nécessaire pour aligner l'interface avec les capacités réelles.

