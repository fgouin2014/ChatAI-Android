# Architecture Simple: Local GGUF OU Hugging Face Boost

**Date:** 2025-12-01  
**Vision:** Mode explicite - Utilisateur choisit Local OU Hugging Face (pas de fallback auto)

---

## 🎯 VOTRE VISION FINALE

**"Fuck les fallback"**

### Architecture Simple

```
UTILISATEUR CHOISIT LE MODE
    ↓
┌─────────────────────────────────────────┐
│  MODE LOCAL (gemma3-270m.gguf)          │
│  ✅ 100% Offline                         │
│  ✅ Tâches que le local peut faire       │
│  ❌ Pas de fallback                      │
└─────────────────────────────────────────┘
    OU
┌─────────────────────────────────────────┐
│  MODE HUGGING FACE (Boost)              │
│  ✅ Tâches complexes                     │
│  ✅ Nécessite Internet                   │
│  ❌ Pas de fallback                      │
└─────────────────────────────────────────┘
```

**Pas de fallback automatique. Mode explicite choisi par l'utilisateur.**

---

## 🏗️ ARCHITECTURE SIMPLIFIÉE

### Flow Simple

```
USER INPUT
    ↓
┌─────────────────────────────────────────┐
│  Vérifier mode sélectionné               │
│  - forced_api_mode = "local_gguf"        │
│  - forced_api_mode = "huggingface"       │
└─────────────────────────────────────────┘
    ↓
    ├─ Mode "local_gguf"
    │   ↓
    │   ┌─────────────────────────────────────────┐
    │   │  LocalGGUFService.process()             │
    │   │  - Charge gemma3-270m.gguf              │
    │   │  - Génère réponse                       │
    │   │  ✅ 100% Offline                         │
    │   └─────────────────────────────────────────┘
    │
    └─ Mode "huggingface"
        ↓
        ┌─────────────────────────────────────────┐
        │  HuggingFaceService.generate()           │
        │  - Appel API Hugging Face                │
        │  - Réponse boostée                       │
        │  ✅ Nécessite Internet                    │
        └─────────────────────────────────────────┘
```

**C'est tout. Pas de détection, pas d'évaluation, pas de fallback.**

---

## 🔧 IMPLÉMENTATION SIMPLIFIÉE

### KittAIService.kt - Flow Simple

```kotlin
suspend fun processUserInput(userInput: String): String = withContext(Dispatchers.IO) {
    // 1. Détection actions (déjà fait)
    val actionResponse = detectAndExecuteAction(userInput)
    if (actionResponse != null) return@withContext actionResponse
    
    // 2. RAG (si activé)
    val ragContext = if (ragEnabled) {
        ragService.searchSimilar(userInput)
    } else ""
    
    val enhancedInput = if (ragContext.isNotEmpty()) {
        "$ragContext\n\nQuestion: $userInput"
    } else {
        userInput
    }
    
    // 3. ⭐ SIMPLE: Vérifier mode sélectionné
    val forcedMode = sharedPreferences.getString("forced_api_mode", null)
    
    when (forcedMode) {
        "local_gguf" -> {
            // Mode Local uniquement
            val localService = LocalGGUFService(context)
            val localModelPath = sharedPreferences.getString("local_model_name", "gemma3-270m.gguf")
            
            if (localModelPath != null && File(localModelPath).exists()) {
                return@withContext localService.processUserInput(enhancedInput)
            } else {
                return@withContext "Erreur: Modèle local non trouvé. Configurez un modèle GGUF dans les paramètres."
            }
        }
        
        "huggingface" -> {
            // Mode Hugging Face uniquement
            if (!isInternetAvailable()) {
                return@withContext "Erreur: Connexion Internet requise pour Hugging Face."
            }
            
            val hfResponse = tryHuggingFace(enhancedInput)
            if (hfResponse != null) {
                return@withContext hfResponse
            } else {
                return@withContext "Erreur: Hugging Face API a échoué. Vérifiez votre clé API."
            }
        }
        
        else -> {
            // Aucun mode sélectionné
            return@withContext "Erreur: Aucun mode sélectionné. Choisissez 'Local GGUF' ou 'Hugging Face' dans les paramètres."
        }
    }
}
```

**C'est tout. Pas de fallback, pas de détection automatique.**

---

## 📋 CONFIGURATION

### Interface Webapp - Onglet Général

```html
<!-- Mode API - 2 options explicites -->
<select id="configModeSelect" required>
    <option value="">– Choisir un mode –</option>
    <option value="local_gguf">📱 Local GGUF (Offline)</option>
    <option value="huggingface">🤗 Hugging Face (Boost)</option>
</select>

<small>
    <strong>📱 Local GGUF:</strong> Utilise gemma3-270m.gguf sur le device (100% offline).<br>
    <strong>🤗 Hugging Face:</strong> Utilise Hugging Face Inference API pour tâches complexes (nécessite Internet).<br>
    <strong>⚠️ Important:</strong> Choisissez UN mode. Pas de fallback automatique.
</small>
```

---

### SharedPreferences

```kotlin
// Mode sélectionné
"forced_api_mode" = "local_gguf"  // OU "huggingface"

// Modèle local (si mode local_gguf)
"local_model_name" = "gemma3-270m.gguf"

// Hugging Face (si mode huggingface)
"use_huggingface_llm" = true
"huggingface_api_key" = "hf_..."
```

---

## 🚀 PLAN D'IMPLÉMENTATION

### Phase 1: Moteur GGUF Local (Priorité 1)

**Étape 1.1: Intégrer llama.cpp**
- [ ] Ajouter dépendance llama.cpp Android
- [ ] Créer `LocalGGUFService.kt` avec JNI bindings
- [ ] Tester chargement `gemma3-270m.gguf`
- [ ] Tester génération réponse

**Étape 1.2: Intégrer dans KittAIService**
- [ ] Ajouter mode `"local_gguf"` dans `forced_api_mode`
- [ ] Modifier `processUserInput()` pour mode simple (pas de fallback)
- [ ] Tester flow complet

---

### Phase 2: Interface Webapp (Priorité 2)

**Étape 2.1: Modifier onglet Général**
- [ ] Changer options mode: "Local GGUF" et "Hugging Face"
- [ ] Supprimer toute mention de fallback
- [ ] Ajouter sélection modèle GGUF (si mode local)
- [ ] Tester configuration

**Étape 2.2: Nettoyage**
- [ ] Supprimer Ollama Cloud complètement
- [ ] Supprimer code de fallback automatique
- [ ] Supprimer détection complexité
- [ ] Supprimer évaluation réponse
- [ ] Nettoyer code obsolète

---

### Phase 3: Tests (Priorité 3)

**Étape 3.1: Tests Locaux**
- [ ] Tester mode Local GGUF (offline)
- [ ] Vérifier chargement modèle
- [ ] Vérifier génération réponse
- [ ] Vérifier RAG avec local

**Étape 3.2: Tests Hugging Face**
- [ ] Tester mode Hugging Face (online)
- [ ] Vérifier appel API
- [ ] Vérifier réponses
- [ ] Vérifier RAG avec HF

---

## ❌ CE QUI SERA SUPPRIMÉ

### Code à Supprimer

1. **Ollama Cloud**
   - `OllamaThinkingService.kt` (ou modifier pour Hugging Face)
   - Toutes références Ollama Cloud dans `KittAIService.kt`
   - Configuration Ollama Cloud dans webapp

2. **Fallback Automatique**
   - Détection complexité
   - Évaluation réponse
   - Fallback automatique dans `processUserInput()`

3. **Code Obsolète**
   - `SimpleLocalService.kt` (remplacé par `LocalGGUFService`)
   - Références "Ollama PC" (déjà supprimé)
   - Code de fallback multi-API

---

## ✅ CE QUI SERA GARDÉ

### Code à Garder

1. **RAG (ONNX)**
   - `EmbeddingService.kt` (ONNX embeddings)
   - `RAGService.kt` (recherche sémantique)
   - Fonctionne avec Local ET Hugging Face

2. **Hugging Face**
   - `HuggingFaceService.kt` (LLM + Embeddings)
   - Utilisé pour mode "huggingface"

3. **ONNX Services**
   - `VisionService.kt` (ONNX CLIP)
   - `TranslationService.kt` (ONNX MarianMT)
   - Fonctionnent indépendamment

4. **KITT Infrastructure**
   - Hotword, TTS, STT
   - Fonctionnent avec n'importe quel mode

---

## 🎯 RÉSUMÉ

**Architecture Finale:**
- ✅ Mode Local GGUF (offline) - gemma3-270m.gguf
- ✅ Mode Hugging Face (boost) - Tâches complexes
- ✅ RAG (ONNX) - Fonctionne avec les deux modes
- ❌ Pas de fallback automatique
- ❌ Pas d'Ollama Cloud
- ❌ Pas de détection automatique

**Simple, clair, explicite.**

---

## 🚀 PROCHAINES ÉTAPES

**Je commence l'implémentation:**

1. ✅ Intégrer llama.cpp pour LocalGGUFService
2. ✅ Modifier KittAIService pour mode simple (pas de fallback)
3. ✅ Modifier interface webapp (2 modes explicites)
4. ✅ Supprimer Ollama Cloud complètement
5. ✅ Nettoyer code obsolète

**C'est parti!** 🚀

