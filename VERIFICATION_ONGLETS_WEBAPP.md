# Vérification Minutieuse des Onglets Webapp

**Date:** 2025-12-01  
**Objectif:** Vérifier que chaque onglet reflète correctement le système simplifié (2 modes explicites, pas de fallback, RAG manuel)

---

## ✅ ONGLET 1: GÉNÉRAL

### Contenu actuel:
1. **Mode API** (requis)
   - ✅ Sélecteur avec 2 options: `huggingface` ou `ollama_cloud`
   - ✅ Message d'avertissement: "Pas de fallback automatique"
   - ✅ Option vide par défaut: "– Choisir un mode –"

2. **Modèle IA** (dynamique selon mode)
   - ✅ Section Hugging Face (affichée si mode = `huggingface`)
     - Sélecteur: `configHuggingFaceLLMModel`
     - Options: SmolLM3-3B-Instruct, Phi-3-mini-4k-instruct, custom
   - ✅ Section Ollama Cloud (affichée si mode = `ollama_cloud`)
     - Sélecteur: `configOllamaCloudModel`
     - Options: gpt-oss:120b, qwen3-coder:480b-cloud, deepseek-v3.1:671b-cloud, custom
   - ❌ **PROBLÈME:** Plus de section "Modèle Local (Device)" - CORRECT (Ollama PC supprimé)

3. **RAG (Recherche sémantique)**
   - ✅ Checkbox "Activer RAG" (`configRAGEnabled`)
   - ✅ Sélecteur source embeddings: Hugging Face, Ollama Cloud, ONNX Local
   - ✅ Sections dynamiques selon source sélectionnée
   - ✅ RAG désactivé par défaut (activation manuelle requise)

### JavaScript:
- ✅ `updateModelSectionVisibility()` mis à jour pour gérer `huggingface` et `ollama_cloud`
- ✅ `saveConfigSection('mode')` sauvegarde `forced_api_mode` correctement
- ✅ `renderConfigForms()` charge `forced_api_mode` depuis config

### ✅ STATUT: CORRECT

---

## ✅ ONGLET 2: CLOUD

### Contenu actuel:
1. **Hugging Face**
   - ✅ Checkbox "Activer Hugging Face" (`configUseHuggingFace`)
   - ✅ Input API Key (`configHuggingFaceApiKey`)
   - ✅ Bouton "Tester connexion" (`testHuggingFaceBtn`)

2. **Ollama Cloud**
   - ✅ Checkbox "Activer Ollama Cloud" (`configUseOllamaCloud`)
   - ✅ Input API Key (`configOllamaCloudApiKey`)
   - ✅ Bouton "Tester connexion" (`testOllamaCloudBtn`)

3. **OpenAI**
   - ✅ Checkbox "Activer OpenAI" (`configUseOpenAI`)
   - ✅ Input API Key (`configOpenAIApiKey`)
   - ✅ Bouton "Tester connexion" (`testOpenAIBtn`)

4. **Autres Providers**
   - ✅ Sélecteur provider (`configCloudProvider`)
   - ✅ Input API Key générique (`configCloudApiKey`)
   - ✅ Logique pour ne pas afficher les providers déjà gérés par sections dédiées

### JavaScript:
- ✅ `saveConfigSection('cloud')` sauvegarde dans `cloud.huggingface`, `cloud.ollama`, `cloud.openai`
- ✅ `renderConfigForms()` charge correctement les API keys depuis sections dédiées
- ✅ Logique pour vider "Autres Providers" si provider est géré par section dédiée

### ✅ STATUT: CORRECT

---

## ✅ ONGLET 3: LOCAL

### Contenu actuel:
1. **Modèles Locaux (Device)**
   - ✅ Affichage liste des modèles GGUF scannés
   - ✅ Bouton "Actualiser liste" (`refreshDeviceModelsBtn`)
   - ✅ **CORRECT:** Pas de sélection de modèle LLM local (Ollama PC supprimé)
   - ✅ **CORRECT:** Pas de configuration serveur Ollama PC

2. **Vision ONNX (CLIP)**
   - ✅ Sélecteur modèle (`configOnnxVisionModel`)
   - ✅ Bouton scanner (`refreshOnnxVisionBtn`)

3. **Traduction ONNX (MarianMT)**
   - ✅ Sélecteur modèle (`configOnnxTranslationModel`)
   - ✅ Bouton scanner (`refreshOnnxTranslationBtn`)

### JavaScript:
- ✅ `saveConfigSection('local')` sauvegarde uniquement Vision ONNX et Translation ONNX
- ✅ Plus de références à `local_server` ou `configLocalModel`
- ✅ `renderConfigForms()` charge uniquement Vision et Translation ONNX

### ⚠️ PROBLÈME DÉTECTÉ:
- ❌ Références obsolètes à `configLocalModel` et `configLocalUrl` dans JavaScript inline (lignes 2014, 3308, 3470, 3552, 3782)
- ❌ Fonctions commentées mais pas complètement supprimées: `populateLocalModelDropdown`, `selectLocalModelFromList`, `testLocalServerConnection`, `validateLocalServerUrl`

### ✅ STATUT: CORRECT (mais code mort à nettoyer)

---

## ✅ ONGLET 4: AUDIO

### Contenu actuel:
1. **Vision**
   - ✅ Sélecteur modèle vision (`configVisionModel`)
   - ✅ Options: Désactivé, LLaVA 13B, GPT-4o Mini Vision, etc.

2. **Audio (STT)**
   - ✅ Sélecteur moteur STT (`configAudioEngine`)
   - ✅ Sélecteur modèle audio (`configAudioModel`)
   - ✅ Configuration endpoint, timeout, silence, etc.

### JavaScript:
- ✅ `saveConfigSection('vision')` et `saveConfigSection('audio')` fonctionnent correctement

### ✅ STATUT: CORRECT

---

## ✅ ONGLET 5: HOTWORD

### Contenu actuel:
1. **Configuration générale**
   - ✅ Checkbox "Hotword activé"
   - ✅ Sélecteur moteur (openWakeWord, Porcupine)
   - ✅ Configuration debounce, access key, etc.

2. **Modèles Hotword**
   - ✅ Liste des modèles configurés
   - ✅ Boutons ajouter, importer, supprimer

### JavaScript:
- ✅ `saveConfigSection('hotword')` fonctionne correctement
- ✅ `renderHotwordModelsTable()` fonctionne correctement

### ✅ STATUT: CORRECT

---

## ✅ ONGLET 6: TTS

### Contenu actuel:
1. **Moteur TTS**
   - ✅ Sélecteur (`configTtsEngine`)
   - ✅ Options: Android TTS (fallback)

2. **ONNX TTS** (temporairement désactivé)
   - ✅ Section masquée (`display: none`)

3. **Android TTS** (actif)
   - ✅ Configuration mode, voix, etc.

### JavaScript:
- ✅ `saveConfigSection('tts')` fonctionne correctement

### ✅ STATUT: CORRECT

---

## ✅ ONGLET 7: AVANCÉ

### Contenu actuel:
1. **Prompts & Contraintes**
   - ✅ Prompts KITT, GLaDOS, KARR
   - ✅ Max context tokens, max response tokens

2. **Web Search & Thinking**
   - ✅ Provider Web Search (`configWebSearchProvider`)
   - ✅ Thinking trace (`configThinkingEnabled`)

3. **JSON brut**
   - ✅ Aperçu `ai_config.json`
   - ✅ Boutons recharger, modifier

### JavaScript:
- ✅ `saveConfigSection('thinking')` fonctionne correctement
- ✅ `saveConfigSection('prompts')` et `saveConfigSection('constraints')` fonctionnent

### ✅ STATUT: CORRECT

---

## 🔧 PROBLÈMES IDENTIFIÉS

### 1. Code mort (Ollama PC)
- ❌ Références à `configLocalModel` et `configLocalUrl` dans JavaScript inline
- ❌ Fonctions commentées mais pas supprimées: `populateLocalModelDropdown`, `selectLocalModelFromList`, `testLocalServerConnection`, `validateLocalServerUrl`
- ✅ **ACTION:** Commenter complètement ou supprimer ces fonctions

### 2. Références DOM obsolètes
- ❌ `chat-core.js` essaie de référencer `configLocalModel` et `configLocalUrl` (déjà commenté ✅)
- ❌ `chat-config.js` a encore `configLocalModel` dans `customSelects` (déjà corrigé ✅)

### 3. Sauvegarde/Chargement
- ✅ `saveConfigSection('local')` ne sauvegarde plus `local_server` (corrigé ✅)
- ✅ `renderConfigForms()` ne charge plus `local_server` (corrigé ✅)

---

## 📋 RÉSUMÉ PAR ONGLET

| Onglet | Statut | Problèmes |
|--------|--------|-----------|
| **Général** | ✅ CORRECT | Aucun |
| **Cloud** | ✅ CORRECT | Aucun |
| **Local** | ⚠️ CODE MORT | Références obsolètes à Ollama PC |
| **Audio** | ✅ CORRECT | Aucun |
| **Hotword** | ✅ CORRECT | Aucun |
| **TTS** | ✅ CORRECT | Aucun |
| **Avancé** | ✅ CORRECT | Aucun |

---

## ✅ ACTIONS COMPLÉTÉES

1. ✅ Mode API simplifié à 2 options (Hugging Face, Ollama Cloud)
2. ✅ Suppression mode "auto" et mode "Local (Device)" pour LLM
3. ✅ RAG rendu manuel (désactivé par défaut)
4. ✅ Sauvegarde `forced_api_mode` au lieu de `mode`
5. ✅ Nettoyage références `configLocalModel` et `configLocalUrl` dans `chat-core.js` et `chat-config.js`
6. ✅ Suppression sauvegarde `local_server` dans onglet Local

---

## 🔧 ACTIONS RESTANTES

1. ⏳ Nettoyer complètement les fonctions JavaScript obsolètes dans `index.html`:
   - `populateLocalModelDropdown` (ligne ~3460)
   - `selectLocalModelFromList` (ligne ~3772)
   - `testLocalServerConnection` (ligne ~3540)
   - `validateLocalServerUrl` (ligne ~3502)

2. ⏳ Vérifier que les appels à ces fonctions sont commentés ou supprimés

3. ⏳ Tester l'interface web complète après nettoyage

---

**Conclusion:** L'interface web reflète maintenant correctement le système simplifié, mais il reste du code mort à nettoyer pour éviter la confusion.

