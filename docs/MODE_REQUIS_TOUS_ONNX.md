# 📋 MODE REQUIS - TOUTES les fonctionnalités ONNX

## Date: 2025-11-29

---

## ✅ RÉPONSE RAPIDE

**TOUTES les fonctionnalités ONNX fonctionnent dans TOUS les modes** - elles sont **100% offline** et indépendantes du mode Cloud/Local configuré pour l'IA principale.

---

## 🔄 FONCTIONNALITÉS ONNX DISPONIBLES

### 1. 🔢 Embeddings ONNX (RAG)
- **Modèle:** `all-MiniLM-L6-v2`
- **Dimensions:** 384
- **Usage:** Génération d'embeddings pour RAG (Recherche Augmentée par Génération)

### 2. 👁️ Vision ONNX (CLIP)
- **Modèle:** `openai/clip-vit-base-patch32`
- **Dimensions:** 512
- **Usage:** Analyse d'images, descriptions, recherche sémantique image-texte

### 3. 🌐 Translation ONNX (MarianMT)
- **Modèle:** `Helsinki-NLP/opus-mt-fr-en`
- **Usage:** Traduction français → anglais

### 4. 🔊 TTS ONNX (SpeechT5) - Temporairement désactivé
- **Modèle:** `microsoft/speecht5_tts`
- **Statut:** ⚠️ Temporairement désactivé (crashes mémoire)

---

## 🔄 MODES D'UTILISATION

### ✅ Mode Cloud (Ollama Cloud)
- ✅ **Embeddings ONNX** fonctionne
- ✅ **Vision ONNX** fonctionne
- ✅ **Translation ONNX** fonctionne
- ✅ Fonctionnent même sans connexion internet après initialisation
- ✅ Indépendant du provider cloud configuré

### ✅ Mode Local (Ollama PC)
- ✅ **Embeddings ONNX** fonctionne
- ✅ **Vision ONNX** fonctionne
- ✅ **Translation ONNX** fonctionne
- ✅ Pas besoin que le serveur Ollama soit démarré
- ✅ Indépendant du serveur Ollama local

### ✅ Mode Device (Modèles locaux GGUF)
- ✅ **Embeddings ONNX** fonctionne
- ✅ **Vision ONNX** fonctionne
- ✅ **Translation ONNX** fonctionne
- ✅ Utilise les modèles ONNX sur le device

---

## 📋 PRÉREQUIS OBLIGATOIRES

### 1. 🔢 Embeddings ONNX

**Répertoire:** `/storage/emulated/0/ChatAI-Files/models/embeddings/`

**Fichiers requis:**
- ✅ `model.onnx` - Modèle all-MiniLM-L6-v2 (~20-30 MB)
- ✅ `vocab.json` - Vocabulaire BERT WordPiece
- ✅ `tokenizer.json` - Configuration tokenizer

**Vérification:**
```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/embeddings/
```

**Initialisation:**
- S'initialise automatiquement dans `EmbeddingService.init`
- Priorité: ONNX local → Ollama local → HuggingFace Cloud

---

### 2. 👁️ Vision ONNX

**Répertoire:** `/storage/emulated/0/ChatAI-Files/models/vision/`

**Fichiers requis:**
- ✅ `vision_model.onnx` - Encoder d'images CLIP
- ✅ `text_model.onnx` - Encoder de texte CLIP (optionnel pour recherche)
- ✅ `vocab.json` - Vocabulaire BERT
- ✅ `tokenizer.json` - Configuration tokenizer

**Vérification:**
```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/vision/
```

**Initialisation:**
- S'initialise automatiquement dans `VisionService.init`
- Priorité: ONNX local → Ollama Vision (fallback)

---

### 3. 🌐 Translation ONNX

**Répertoire:** `/storage/emulated/0/ChatAI-Files/models/translation/`

**Fichiers requis:**
- ✅ `encoder_model.onnx` - Modèle encoder MarianMT (~50 MB)
- ✅ `decoder_model.onnx` - Modèle decoder MarianMT (~4-5 MB)
- ✅ `vocab.json` - Vocabulaire SentencePiece MarianMT (~1-2 MB)

**Vérification:**
```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/translation/
```

**Initialisation:**
- S'initialise automatiquement dans `TranslationService.init`
- Priorité: ONNX local → Ollama Cloud (fallback non implémenté pour l'instant)

---

## ❌ CE QUI N'EST PAS NÉCESSAIRE

### Pour TOUTES les fonctionnalités ONNX:

1. ❌ **Pas besoin de mode Cloud/Local spécifique**
   - Fonctionnent dans tous les modes
   - Indépendant de la configuration AI principale

2. ❌ **Pas besoin de connexion internet**
   - 100% offline après chargement des modèles

3. ❌ **Pas besoin de serveur Ollama**
   - Indépendant du serveur Ollama local ou cloud

4. ❌ **Pas besoin de clé API** (pour ONNX local)
   - Pas d'appel API externe
   - ⚠️ **Exception:** HuggingFace Cloud pour embeddings (fallback)

---

## 🔍 COMMENT VÉRIFIER

### 1. Vérifier les fichiers sur le device

```bash
# Embeddings
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/embeddings/

# Vision
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/vision/

# Translation
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/translation/
```

### 2. Vérifier l'initialisation dans les logs

```bash
adb logcat | Select-String -Pattern "OnnxEmbeddingManager|OnnxVisionManager|OnnxTranslationManager|EmbeddingService|VisionService|TranslationService"
```

**Logs attendus si OK:**
```
✅ ONNX Embeddings initialisé (384 dimensions)
✅ ONNX Vision initialisé (CLIP, 512 dimensions)
✅ ONNX Translation initialisé (MarianMT, opus-mt-fr-en)
```

**Logs si fichiers manquants:**
```
⚠️ ONNX Embeddings non disponible
⚠️ ONNX Vision non disponible
⚠️ ONNX Translation non disponible
```

### 3. Vérifier les services prêts

Tous les services ONNX s'initialisent automatiquement au démarrage de l'application dans `MainActivity.startLocalServers()`.

---

## 🔄 COMPORTEMENT FALLBACK

### Embeddings (EmbeddingService)
1. ✅ **ONNX Local** (priorité, 100% offline)
2. ⚠️ Ollama Local (`/api/embeddings`)
3. ⚠️ HuggingFace Cloud (si clé API configurée)

### Vision (VisionService)
1. ✅ **ONNX Local** (priorité, 100% offline)
2. ⚠️ Ollama Vision (fallback, si modèle vision disponible)

### Translation (TranslationService)
1. ✅ **ONNX Local** (priorité, 100% offline)
2. ⚠️ Ollama Cloud Translation (fallback, non implémenté)

---

## 📊 TABLEAU RÉCAPITULATIF

| Fonctionnalité | Fichiers requis | Mode requis | Offline ? | Fallback |
|----------------|-----------------|-------------|-----------|----------|
| **Embeddings** | `model.onnx`, `vocab.json` | ✅ **Tous** | ✅ Oui | Ollama/HF Cloud |
| **Vision** | `vision_model.onnx`, `vocab.json` | ✅ **Tous** | ✅ Oui | Ollama Vision |
| **Translation** | `encoder_model.onnx`, `decoder_model.onnx`, `vocab.json` | ✅ **Tous** | ✅ Oui | Ollama Cloud (TODO) |

---

## 🚨 PROBLÈMES POSSIBLES

### Si une fonctionnalité ONNX ne fonctionne pas:

1. **Fichiers manquants:**
   - Vérifier que les fichiers requis sont présents
   - Vérifier les chemins exacts dans les managers

2. **Vocabulaire incorrect:**
   - `vocab.json` doit correspondre au modèle utilisé
   - Format différent selon le tokenizer (WordPiece, SentencePiece, etc.)

3. **Modèles ONNX incorrects:**
   - Vérifier que les modèles sont bien convertis
   - Vérifier que les versions correspondent (encoder/decoder)

---

## 📝 RÉSUMÉ

| Condition | Embeddings | Vision | Translation |
|-----------|------------|--------|-------------|
| **Fichiers ONNX sur device** | ✅ **OUI** | ✅ **OUI** | ✅ **OUI** |
| **Mode Cloud** | ❌ NON | ❌ NON | ❌ NON |
| **Mode Local** | ❌ NON | ❌ NON | ❌ NON |
| **Connexion internet** | ❌ NON | ❌ NON | ❌ NON |
| **Serveur Ollama** | ❌ NON | ❌ NON | ❌ NON |
| **Clé API** | ❌ NON* | ❌ NON | ❌ NON |

\* *Seulement pour fallback HuggingFace Cloud*

---

**Conclusion:** **TOUTES les fonctionnalités ONNX fonctionnent dans TOUS LES MODES** tant que les fichiers ONNX requis sont présents sur le device. Elles sont **100% offline** et **indépendantes** de la configuration AI principale (Cloud/Local).

