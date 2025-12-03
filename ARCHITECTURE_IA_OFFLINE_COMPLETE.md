# Architecture IA Offline Complète - Vision Réelle

**Date:** 2025-12-01  
**Objectif:** Système IA 100% offline sur device avec Gemma3-270m.gguf + RAG + Hugging Face booster

---

## 🎯 VOTRE VISION

**Ce que vous voulez:**
1. ✅ **IA sur device (offline)** - Pas de cloud obligatoire
2. ✅ **Gemma3-270m.gguf** - Modèle local GGUF qui fonctionne
3. ✅ **ONNX** - Embeddings, Vision, Traduction (offline)
4. ✅ **Hugging Face** - Pour booster/améliorer les modèles GGUF
5. ✅ **RAG** - Pour l'apprentissage (conversations précédentes)
6. ✅ **KITT complet** - Hotword, TTS, STT, tout fonctionnel offline

**Citation:** "si ollama n'est pas ce que j'ai besoin alors qu'il prenne le bord"

---

## ❌ PROBLÈME IDENTIFIÉ

### Ce qui MANQUE actuellement:

**1. Moteur d'inférence GGUF local**
- ❌ Vous avez `gemma3-270m.gguf` (278 MB) sur le device
- ❌ Mais **AUCUN code** ne charge et exécute ce modèle GGUF
- ❌ Le code référence `gemma3-270m.gguf` mais ne l'utilise pas vraiment
- ❌ `SimpleLocalService` est juste des réponses hardcodées (pas de vrai LLM)

**2. Intégration modèle local dans KittAIService**
- ❌ `KittAIService` appelle seulement Hugging Face ou Ollama Cloud
- ❌ Pas de code pour appeler un modèle GGUF local
- ❌ Pas de moteur d'inférence intégré

**3. Compréhension "Hugging Face booster"**
- ❓ Que voulez-vous dire par "booster"?
  - Option A: Hugging Face pour embeddings (RAG) → ✅ Déjà fait
  - Option B: Hugging Face pour améliorer réponses GGUF → ❓ Comment?
  - Option C: Modèles Hugging Face convertis en GGUF → ❓ Besoin moteur GGUF

---

## ✅ CE QUI EXISTE DÉJÀ (Fonctionnel)

### 1. ONNX Embeddings (RAG)
- ✅ `OnnxEmbeddingManager` - Génère embeddings offline
- ✅ `EmbeddingService` - Utilise ONNX en priorité
- ✅ `RAGService` - Recherche sémantique dans conversations
- ✅ **Fonctionne 100% offline**

### 2. ONNX Vision
- ✅ `OnnxVisionManager` - Analyse images offline (CLIP)
- ✅ `VisionService` - Utilise ONNX en priorité
- ✅ **Fonctionne 100% offline**

### 3. ONNX Traduction
- ✅ `OnnxTranslationManager` - Traduction FR→EN offline (MarianMT)
- ✅ `TranslationService` - Utilise ONNX
- ✅ **Fonctionne 100% offline**

### 4. KITT Infrastructure
- ✅ Hotword detection (OpenWakeWord)
- ✅ TTS (Android TextToSpeech)
- ✅ STT (Whisper Server local)
- ✅ **Fonctionne 100% offline**

---

## 🔧 CE QUI MANQUE (À Implémenter)

### 1. Moteur d'Inférence GGUF Local

**Problème:** Pas de bibliothèque pour charger/exécuter `gemma3-270m.gguf`

**Solutions possibles:**

#### Option A: llama.cpp Android (Recommandé)
- ✅ Bibliothèque C++ mature et optimisée
- ✅ Support GGUF natif
- ✅ Performance excellente
- ✅ Utilisé par Ollama, LM Studio, etc.
- ⚠️ Nécessite compilation JNI (native)

**Intégration:**
```kotlin
// Nouveau service: LocalGGUFService.kt
class LocalGGUFService(context: Context) {
    private external fun initModel(modelPath: String): Long
    private external fun generate(prompt: String, context: Long): String
    private external fun freeModel(context: Long)
    
    suspend fun processUserInput(prompt: String): String {
        val modelPath = "/storage/emulated/0/ChatAI-Files/models/gemma3-270m.gguf"
        val modelContext = initModel(modelPath)
        val response = generate(prompt, modelContext)
        freeModel(modelContext)
        return response
    }
}
```

#### Option B: ONNX Runtime (Alternative)
- ✅ Déjà utilisé pour embeddings/vision
- ⚠️ Nécessite conversion GGUF → ONNX (complexe)
- ⚠️ Performance peut être inférieure

#### Option C: Hugging Face Transformers (Android)
- ✅ Bibliothèque Python → Android (complexe)
- ⚠️ Nécessite conversion GGUF → format Hugging Face
- ⚠️ Plus lourd

**Recommandation:** **Option A (llama.cpp)** - C'est ce qu'Ollama utilise en interne

---

### 2. Intégration dans KittAIService

**Modification nécessaire:**

```kotlin
// KittAIService.kt - processUserInput()
suspend fun processUserInput(userInput: String): String {
    // 1. Détecter actions (déjà fait)
    val actionResponse = detectAndExecuteAction(userInput)
    if (actionResponse != null) return actionResponse
    
    // 2. RAG (si activé) - DÉJÀ FAIT
    val ragContext = if (ragEnabled) {
        ragService.searchSimilar(userInput)
    } else ""
    
    // 3. ⭐ NOUVEAU: Vérifier mode local
    val forcedMode = sharedPreferences.getString("forced_api_mode", null)
    
    when (forcedMode) {
        "local_gguf" -> {
            // ⭐ NOUVEAU: Utiliser modèle GGUF local
            val localService = LocalGGUFService(context)
            val prompt = if (ragContext.isNotEmpty()) {
                "$ragContext\n\nQuestion: $userInput"
            } else {
                userInput
            }
            return localService.processUserInput(prompt)
        }
        "huggingface" -> {
            // Existant
            return tryHuggingFace(userInput)
        }
        "ollama_cloud" -> {
            // Existant
            return tryOllamaCloud(userInput)
        }
        else -> {
            // ⭐ NOUVEAU: Par défaut, essayer local d'abord
            return tryLocalGGUF(userInput) 
                ?: tryHuggingFace(userInput)
                ?: tryOllamaCloud(userInput)
                ?: "Erreur: Aucun service IA disponible"
        }
    }
}
```

---

### 3. Clarification "Hugging Face Booster"

**Question:** Que voulez-vous dire par "booster"?

#### Option A: Hugging Face pour Embeddings (RAG)
- ✅ **Déjà implémenté**
- Hugging Face génère embeddings pour RAG
- Améliore recherche sémantique
- **C'est ce qui est fait actuellement**

#### Option B: Hugging Face pour Améliorer Réponses GGUF
- ❓ Comment?
  - Option B1: Hugging Face post-processe réponses GGUF (correction, amélioration)
  - Option B2: Hugging Face génère réponses si GGUF échoue
  - Option B3: Comparaison réponses GGUF vs Hugging Face, meilleure choisie

#### Option C: Modèles Hugging Face Convertis en GGUF
- ✅ Vous avez converti des modèles Hugging Face en GGUF
- ✅ Ils sont sur le device
- ✅ Besoin moteur GGUF pour les utiliser
- **C'est probablement ce que vous voulez**

**Recommandation:** **Option C** - Utiliser vos modèles GGUF convertis avec moteur local

---

## 🏗️ ARCHITECTURE PROPOSÉE

### Flow Complet Offline

```
USER INPUT (vocal ou texte)
    ↓
┌─────────────────────────────────────────┐
│  Hotword Detection (OpenWakeWord)       │
│  ✅ Offline                              │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  STT (Whisper Server local)              │
│  ✅ Offline (ggml-small.bin)             │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  KittAIService.processUserInput()       │
│  - Détecte actions                       │
│  - RAG (si activé)                       │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  RAG (si activé)                         │
│  - EmbeddingService.embed() (ONNX)      │
│  - RAGService.searchSimilar()            │
│  - Contexte ajouté au prompt             │
│  ✅ 100% Offline                          │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  LocalGGUFService (NOUVEAU)             │
│  - Charge gemma3-270m.gguf              │
│  - Génère réponse                        │
│  ✅ 100% Offline                          │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  TTS (Android TextToSpeech)              │
│  ✅ Offline                              │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  Sauvegarde Conversation                │
│  - Embeddings générés (ONNX)            │
│  - Pour RAG futur                        │
│  ✅ 100% Offline                          │
└─────────────────────────────────────────┘
```

---

## 📋 PLAN D'IMPLÉMENTATION

### Phase 1: Moteur GGUF Local (Priorité 1)

**Étape 1.1: Intégrer llama.cpp**
- [ ] Ajouter dépendance llama.cpp Android
- [ ] Créer JNI bindings (LocalGGUFService.kt)
- [ ] Tester chargement `gemma3-270m.gguf`
- [ ] Tester génération réponse simple

**Étape 1.2: Intégrer dans KittAIService**
- [ ] Ajouter mode `"local_gguf"` dans `forced_api_mode`
- [ ] Modifier `processUserInput()` pour utiliser LocalGGUFService
- [ ] Tester flow complet

**Étape 1.3: Configuration**
- [ ] Ajouter sélection modèle GGUF dans webapp
- [ ] Scanner modèles GGUF disponibles
- [ ] Permettre choix modèle (gemma3-270m.gguf ou autres)

---

### Phase 2: RAG Offline (Priorité 2)

**Déjà fait mais vérifier:**
- [x] ONNX Embeddings fonctionnel
- [x] RAGService fonctionnel
- [ ] Tester RAG + LocalGGUF ensemble
- [ ] Vérifier performance

---

### Phase 3: Hugging Face Booster (Priorité 3)

**Selon votre vision:**
- [ ] Clarifier ce que "booster" signifie
- [ ] Implémenter selon Option A/B/C
- [ ] Tester amélioration réponses

---

## ❓ QUESTIONS POUR VOUS

### 1. Moteur GGUF
**Q:** Voulez-vous que j'intègre llama.cpp pour exécuter `gemma3-270m.gguf` localement?
- ✅ Oui → Je commence l'intégration
- ❌ Non → Autre solution?

### 2. Hugging Face Booster
**Q:** Que voulez-vous dire par "booster"?
- A) Hugging Face pour embeddings RAG (déjà fait)
- B) Hugging Face pour améliorer réponses GGUF (comment?)
- C) Utiliser modèles Hugging Face convertis en GGUF (besoin moteur GGUF)

### 3. Ollama Cloud
**Q:** Voulez-vous vraiment supprimer Ollama Cloud?
- ✅ Oui → Je supprime tout code Ollama Cloud
- ❌ Non → Garder comme option optionnelle

### 4. Architecture
**Q:** Préférez-vous:
- A) Mode unique: Local GGUF uniquement (100% offline)
- B) Mode avec fallback: Local GGUF → Hugging Face → Ollama Cloud
- C) Mode sélection manuelle: Utilisateur choisit (Local/Hugging Face/Ollama)

---

## 🎯 PROCHAINES ÉTAPES

**Avant de coder, j'ai besoin de vos réponses:**

1. ✅ Intégrer llama.cpp pour GGUF local?
2. ❓ Que signifie "Hugging Face booster"?
3. ❓ Supprimer Ollama Cloud complètement?
4. ❓ Quelle architecture préférez-vous?

**Une fois clarifié, je commence l'implémentation!**

---

## 📚 RÉFÉRENCES TECHNIQUES

### llama.cpp Android
- **GitHub:** https://github.com/ggerganov/llama.cpp
- **Android bindings:** https://github.com/ggerganov/llama.cpp/tree/master/bindings/android
- **Format GGUF:** Support natif

### Modèles GGUF
- **Gemma3-270m.gguf:** 278 MB, 270M paramètres
- **Format:** GGUF Q8_0 (quantifié)
- **Performance:** ~1-3s par réponse (selon device)

### ONNX Runtime
- **Déjà intégré:** Embeddings, Vision, Traduction
- **Performance:** Excellent pour embeddings (384 dim)

---

**En attente de vos réponses pour continuer!** 🚀

