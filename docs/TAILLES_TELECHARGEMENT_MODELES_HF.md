# 📦 Tailles de Téléchargement - Modèles Hugging Face Locaux

**Date**: 2025-11-27  
**Objectif**: Estimer les tailles de téléchargement pour remplacer les modèles locaux actuels

---

## 📊 MODÈLES LOCAUX ACTUELS (VÉRIFIÉ SUR DEVICE)

### Modèles existants sur le device (vérifié via adb shell)

| Modèle | Taille réelle | Localisation | Usage |
|--------|---------------|--------------|-------|
| `gemma3-270m.gguf` | **278 MB** | `/storage/emulated/0/ChatAI-Files/models/` | LLM local (Ollama) |
| `ggml-small.bin` | **465 MB** | `/storage/emulated/0/ChatAI-Files/models/whisper/` | STT (Whisper Server) |
| `nomic-embed-text` | **N/A** | Ollama serveur (PC/Termux) | Embeddings RAG (pas de fichier local) |
| Hotwords (.tflite) | **~3 MB** | `/storage/emulated/0/ChatAI-Files/hotwords/openwakeword/` | Hotword detection |

**Total actuel**: **743 MB** (modèles AI) + 3 MB (hotwords) = **746 MB**

**Note**: `nomic-embed-text` est un modèle Ollama chargé depuis le serveur, pas un fichier local sur le device.

---

## 🎯 MODÈLES HUGGING FACE RECOMMANDÉS (Remplacement)

### Option 1: Configuration LÉGÈRE (Mobile optimisé) ⭐ RECOMMANDÉ

| Modèle | Taille | Format | Usage | Priorité |
|--------|--------|--------|-------|----------|
| **STT**: `openai/whisper-tiny` | **39 MB** | ONNX/PyTorch | Speech-to-Text | ⭐⭐⭐ |
| **STT**: `openai/whisper-base` | **74 MB** | ONNX/PyTorch | Speech-to-Text (meilleure qualité) | ⭐⭐ |
| **TTS**: `microsoft/speecht5_tts` | **200 MB** | ONNX/PyTorch | Text-to-Speech | ⭐⭐⭐ |
| **Vision**: `openai/clip-vit-base-patch32` | **150 MB** | ONNX/PyTorch | Analyse d'images | ⭐⭐ |
| **Traduction**: `Helsinki-NLP/opus-mt-fr-en` | **50 MB** | ONNX/PyTorch | Traduction FR↔EN | ⭐ |
| **Classification**: `distilbert-base-uncased` | **67 MB** | ONNX/PyTorch | Sentiment/Intention | ⭐ |
| **Embeddings**: `sentence-transformers/all-MiniLM-L6-v2` | **80 MB** | ONNX/PyTorch | Embeddings RAG (local) | ⭐⭐⭐ |

**Total Option 1**: **~660 MB** (si tous téléchargés)

**Recommandation minimale** (STT + TTS + Embeddings): **319 MB**

---

### Option 2: Configuration MOYENNE (Qualité/Performance)

| Modèle | Taille | Format | Usage | Priorité |
|--------|--------|--------|-------|----------|
| **STT**: `openai/whisper-small` | **244 MB** | ONNX/PyTorch | Speech-to-Text (meilleure qualité) | ⭐⭐ |
| **TTS**: `coqui/XTTS-v2` | **1.5 GB** | PyTorch | Text-to-Speech (très bonne qualité) | ⭐⭐ |
| **Vision**: `Salesforce/blip-image-captioning-base` | **990 MB** | PyTorch | Description d'images | ⭐ |
| **Traduction**: `facebook/mbart-large-50` | **1.2 GB** | PyTorch | Multilingue (50 langues) | ⭐ |
| **Classification**: `roberta-base` | **500 MB** | PyTorch | Classification avancée | ⭐ |
| **Embeddings**: `sentence-transformers/all-MiniLM-L6-v2` | **80 MB** | ONNX/PyTorch | Embeddings RAG | ⭐⭐⭐ |

**Total Option 2**: **~4.5 GB** (si tous téléchargés)

**Recommandation minimale** (STT + TTS + Embeddings): **1.8 GB**

---

## 💡 RECOMMANDATION PAR PRIORITÉ

### Priorité 1: Essentiels (319 MB)

1. **Embeddings local** (`all-MiniLM-L6-v2`) - **80 MB** ⭐⭐⭐
   - Remplace: Embeddings Ollama cloud
   - Avantage: Fonctionne offline, pas de clé API
   - Impact: RAG fonctionne sans internet

2. **STT amélioré** (`whisper-tiny` ou `whisper-base`) - **39-74 MB** ⭐⭐⭐
   - Remplace: `ggml-small.bin` (500 MB)
   - Avantage: Plus léger, meilleure qualité
   - Impact: STT plus rapide et précis

3. **TTS amélioré** (`speecht5_tts`) - **200 MB** ⭐⭐⭐
   - Remplace: Android TTS natif
   - Avantage: Voix plus naturelles, contrôle émotion
   - Impact: KITT plus expressif

**Total Priorité 1**: **319 MB** (ou 354 MB avec whisper-base)

---

### Priorité 2: Améliorations UX (217 MB supplémentaires)

4. **Vision** (`clip-vit-base-patch32`) - **150 MB** ⭐⭐
   - Nouveau: Analyse d'images dans le chat
   - Impact: Détection contenu images, recherche sémantique

5. **Classification** (`distilbert-base-uncased`) - **67 MB** ⭐
   - Nouveau: Analyse sentiment, détection intention
   - Impact: Personnalisation réponses KITT

**Total Priorité 1+2**: **536 MB**

---

### Priorité 3: Nice to have (50 MB supplémentaires)

6. **Traduction** (`opus-mt-fr-en`) - **50 MB** ⭐
   - Nouveau: Traduction automatique conversations
   - Impact: Support multilingue amélioré

**Total Priorité 1+2+3**: **586 MB**

---

## 📥 TÉLÉCHARGEMENTS PAR FORMAT

### Format ONNX (Recommandé pour Android)

**Avantages**:
- ✅ Exécution native Android (ONNX Runtime)
- ✅ Pas de serveur HTTP nécessaire
- ✅ Latence minimale (10-50ms)
- ✅ Fonctionne 100% offline

**Inconvénients**:
- ❌ Conversion nécessaire (export depuis PyTorch)
- ❌ Taille APK augmentée si intégré
- ❌ Complexité intégration (JNI, NDK)

**Tailles ONNX** (estimées, souvent 20-30% plus petites):
- `whisper-tiny`: ~30 MB
- `whisper-base`: ~55 MB
- `speecht5_tts`: ~150 MB
- `clip-vit-base-patch32`: ~110 MB
- `all-MiniLM-L6-v2`: ~20 MB (quantifié)
- `distilbert-base-uncased`: ~50 MB
- `opus-mt-fr-en`: ~40 MB

**Total ONNX (Priorité 1)**: **~255 MB** (vs 319 MB PyTorch)

---

### Format PyTorch (Serveur Python HTTP)

**Avantages**:
- ✅ Facile à maintenir (comme Whisper actuel)
- ✅ Pas de conversion nécessaire
- ✅ Peut utiliser GPU si disponible
- ✅ Modèles partagés entre apps

**Inconvénients**:
- ❌ Nécessite serveur Python (Termux)
- ❌ Latence HTTP (50-200ms)
- ❌ Plus de RAM nécessaire

**Tailles PyTorch** (telles quelles):
- `whisper-tiny`: 39 MB
- `whisper-base`: 74 MB
- `speecht5_tts`: 200 MB
- `clip-vit-base-patch32`: 150 MB
- `all-MiniLM-L6-v2`: 80 MB
- `distilbert-base-uncased`: 67 MB
- `opus-mt-fr-en`: 50 MB

**Total PyTorch (Priorité 1)**: **319 MB**

---

## 🎯 COMPARAISON AVEC MODÈLES ACTUELS

### Remplacement direct

| Modèle actuel | Taille | Modèle HF recommandé | Taille HF | Gain |
|---------------|--------|----------------------|-----------|------|
| `ggml-small.bin` (Whisper) | 500 MB | `whisper-tiny` | 39 MB | **-461 MB** ✅ |
| `ggml-small.bin` (Whisper) | 500 MB | `whisper-base` | 74 MB | **-426 MB** ✅ |
| `nomic-embed-text` (Ollama) | 150 MB | `all-MiniLM-L6-v2` | 80 MB | **-70 MB** ✅ |
| Android TTS | 0 MB | `speecht5_tts` | 200 MB | **+200 MB** ⚠️ |

**Gain net** (si remplacement STT + Embeddings): **-531 MB** (whisper-tiny) ou **-496 MB** (whisper-base)

**Coût net** (si ajout TTS): **+200 MB**

---

## 📊 RÉSUMÉ DES TÉLÉCHARGEMENTS

### Scénario 1: Remplacement minimal (STT + Embeddings)

- `whisper-tiny`: 39 MB
- `all-MiniLM-L6-v2`: 80 MB
- **Total**: **119 MB** ✅

**Gain**: -531 MB (remplace 500 MB + 150 MB)

---

### Scénario 2: Remplacement + TTS (Priorité 1)

- `whisper-tiny`: 39 MB
- `all-MiniLM-L6-v2`: 80 MB
- `speecht5_tts`: 200 MB
- **Total**: **319 MB**

**Gain net**: -331 MB (remplace 650 MB, ajoute 200 MB)

---

### Scénario 3: Configuration complète (Priorité 1+2+3)

- `whisper-tiny`: 39 MB
- `all-MiniLM-L6-v2`: 80 MB
- `speecht5_tts`: 200 MB
- `clip-vit-base-patch32`: 150 MB
- `distilbert-base-uncased`: 67 MB
- `opus-mt-fr-en`: 50 MB
- **Total**: **586 MB**

**Gain net**: -64 MB (remplace 650 MB, ajoute 586 MB)

---

## ⚠️ NOTES IMPORTANTES

### Téléchargement automatique

Les modèles Hugging Face sont téléchargés **automatiquement** au premier usage via:
- `transformers` (Python): Cache dans `~/.cache/huggingface/`
- `sentence-transformers`: Cache dans `~/.cache/torch/sentence_transformers/`
- ONNX Runtime: Nécessite téléchargement manuel ou intégration APK

### Espace disque nécessaire

**Sur le device Android**:
- PyTorch: ~319 MB (Priorité 1)
- ONNX: ~255 MB (Priorité 1)
- Cache Python: +50-100 MB (dépendances)

**Sur le PC** (si serveur Python):
- Modèles: ~319 MB
- Dépendances Python: ~500 MB (PyTorch, transformers, etc.)
- **Total**: ~800 MB

### Recommandation finale

**Pour commencer**: Télécharger seulement **Priorité 1** (319 MB)
- STT: `whisper-tiny` (39 MB)
- Embeddings: `all-MiniLM-L6-v2` (80 MB)
- TTS: `speecht5_tts` (200 MB)

**Gain net**: -331 MB (remplace 650 MB existants)

**Temps de téléchargement** (selon connexion):
- 10 Mbps: ~4 minutes
- 50 Mbps: ~1 minute
- 100 Mbps: ~30 secondes

