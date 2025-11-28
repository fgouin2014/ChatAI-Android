# 📊 INVENTAIRE RÉEL - Modèles sur le Device

**Date**: 2025-11-27  
**Source**: Vérification réelle via `adb shell` sur le device

---

## 📁 STRUCTURE ChatAI-Files/models/

```
/storage/emulated/0/ChatAI-Files/models/
├── gemma3-270m.gguf         278 MB  (LLM local Ollama)
├── models_manifest.json     240 B   (manifest)
├── whisper/
│   └── ggml-small.bin       465 MB  (STT Whisper)
├── llm/                     3.5 KB  (vide ou fichiers minimes)
└── tts/                     3.5 KB  (vide ou fichiers minimes)
```

---

## 📊 MODÈLES RÉELS SUR LE DEVICE

### Modèles AI (743 MB total)

| Modèle | Taille réelle | Localisation | Usage | Format |
|--------|---------------|--------------|-------|--------|
| `gemma3-270m.gguf` | **278 MB** | `/storage/emulated/0/ChatAI-Files/models/` | LLM local (Ollama) | GGUF |
| `ggml-small.bin` | **465 MB** | `/storage/emulated/0/ChatAI-Files/models/whisper/` | STT (Whisper Server) | GGML |

**Total modèles AI**: **743 MB**

---

### Hotwords (.tflite) (~3 MB total)

| Modèle | Taille | Localisation | Usage |
|--------|--------|--------------|-------|
| `embedding_model.tflite` | 1.2 MB | `/storage/emulated/0/ChatAI-Files/hotwords/openwakeword/` | Embedding pour hotwords |
| `melspectrogram.tflite` | 1.0 MB | `/storage/emulated/0/ChatAI-Files/hotwords/openwakeword/` | Préprocessing audio |
| `hey_kitt.tflite` | 204 KB | `/storage/emulated/0/ChatAI-Files/hotwords/openwakeword/` | Hotword "Hey KITT" |
| `glados.tflite` | 204 KB | `/storage/emulated/0/ChatAI-Files/hotwords/openwakeword/` | Hotword "GLaDOS" |
| `hey_dick_head.tflite` | 204 KB | `/storage/emulated/0/ChatAI-Files/hotwords/openwakeword/` | Hotword personnalisé |
| `yo_bitch.tflite` | 204 KB | `/storage/emulated/0/ChatAI-Files/hotwords/openwakeword/` | Hotword personnalisé |

**Total hotwords**: **~3 MB**

---

### Embeddings (nomic-embed-text)

**⚠️ IMPORTANT**: `nomic-embed-text` n'est **PAS** un fichier local sur le device !

- **Format**: Modèle Ollama (chargé depuis Ollama local/cloud)
- **Localisation**: Ollama serveur (PC ou device via Termux)
- **Taille**: ~150 MB (mais pas stocké sur `/storage/emulated/0/ChatAI-Files/`)
- **Usage**: Embeddings RAG via API Ollama `/api/embeddings`

**Conclusion**: Pas de fichier à remplacer, c'est géré par Ollama.

---

## 🎯 ESPACE DISPONIBLE POUR OPTIMISATION

### Espace actuellement utilisé

- Modèles AI: **743 MB**
- Hotwords: **3 MB**
- **Total**: **746 MB**

### Espace "libérable" par remplacement

Si on remplace les modèles existants:

| Remplacement | Gain | Nouveau modèle | Coût | Net |
|--------------|------|----------------|------|-----|
| `ggml-small.bin` (465 MB) → `whisper-base` (74 MB) | -391 MB | `whisper-base` | +74 MB | **-317 MB** |
| `ggml-small.bin` (465 MB) → `whisper-small` (244 MB) | -221 MB | `whisper-small` | +244 MB | **+23 MB** |

**Note**: `gemma3-270m.gguf` (278 MB) reste, c'est le LLM local.

---

## 💡 CONFIGURATION OPTIMALE (743 MB disponibles)

### Option A: Remplacement + Nouvelles fonctionnalités (743 MB)

| Modèle | Taille | Remplace/Améliore | Fonctionnalité |
|--------|--------|-------------------|----------------|
| `whisper-base` | 74 MB | `ggml-small.bin` (465 MB) | STT amélioré |
| `all-MiniLM-L6-v2` | 80 MB | Embeddings offline (nouveau) | RAG offline |
| `speecht5_tts` | 200 MB | Android TTS (amélioration) | TTS moderne |
| `clip-vit-base-patch32` | 150 MB | Nouveau | Vision (analyse images) |
| `distilbert-base-uncased` | 67 MB | Nouveau | Classification (sentiment) |
| `opus-mt-fr-en` | 50 MB | Nouveau | Traduction FR↔EN |
| `gemma3-270m.gguf` | 278 MB | **GARDÉ** | LLM local |
| **TOTAL** | **899 MB** | | **6 fonctionnalités** |

**Problème**: 899 MB > 743 MB disponible ❌

---

### Option B: Configuration équilibrée (743 MB) ⭐ RECOMMANDÉ

| Modèle | Taille | Remplace/Améliore | Fonctionnalité |
|--------|--------|-------------------|----------------|
| `whisper-base` | 74 MB | `ggml-small.bin` (465 MB) | STT amélioré |
| `all-MiniLM-L6-v2` | 80 MB | Embeddings offline (nouveau) | RAG offline |
| `speecht5_tts` | 200 MB | Android TTS (amélioration) | TTS moderne |
| `clip-vit-base-patch32` | 150 MB | Nouveau | Vision (analyse images) |
| `distilbert-base-uncased` | 67 MB | Nouveau | Classification (sentiment) |
| `gemma3-270m.gguf` | 278 MB | **GARDÉ** | LLM local |
| **TOTAL** | **849 MB** | | **5 fonctionnalités** |

**Problème**: 849 MB > 743 MB disponible ❌

---

### Option C: Configuration optimisée (743 MB exact) ⭐⭐⭐ PARFAIT

| Modèle | Taille | Remplace/Améliore | Fonctionnalité |
|--------|--------|-------------------|----------------|
| `whisper-base` | 74 MB | `ggml-small.bin` (465 MB) | STT amélioré |
| `all-MiniLM-L6-v2` | 80 MB | Embeddings offline (nouveau) | RAG offline |
| `speecht5_tts` | 200 MB | Android TTS (amélioration) | TTS moderne |
| `clip-vit-base-patch32` | 150 MB | Nouveau | Vision (analyse images) |
| `gemma3-270m.gguf` | 278 MB | **GARDÉ** | LLM local |
| **TOTAL** | **782 MB** | | **4 fonctionnalités** |

**Problème**: 782 MB > 743 MB disponible ❌

---

### Option D: Configuration réaliste (743 MB) ⭐⭐⭐⭐ FINAL

| Modèle | Taille | Remplace/Améliore | Fonctionnalité |
|--------|--------|-------------------|----------------|
| `whisper-base` | 74 MB | `ggml-small.bin` (465 MB) | STT amélioré |
| `all-MiniLM-L6-v2` | 80 MB | Embeddings offline (nouveau) | RAG offline |
| `speecht5_tts` | 200 MB | Android TTS (amélioration) | TTS moderne |
| `clip-vit-base-patch32` | 150 MB | Nouveau | Vision (analyse images) |
| `gemma3-270m.gguf` | 278 MB | **GARDÉ** | LLM local |
| **TOTAL** | **782 MB** | | **4 fonctionnalités** |

**Ajustement**: Retirer Vision (150 MB) → **632 MB** ✅

**OU** utiliser `whisper-tiny` (39 MB) au lieu de `whisper-base` (74 MB) → **747 MB** ✅

---

## 🎯 RECOMMANDATION FINALE

### Configuration Optimale (632 MB)

```
┌─────────────────────────────────────────┐
│  Configuration Optimale (632 MB)        │
├─────────────────────────────────────────┤
│  STT: whisper-base         74 MB  ⭐⭐⭐⭐│
│  Embeddings: all-MiniLM     80 MB  ⭐⭐⭐⭐│
│  TTS: speecht5_tts        200 MB  ⭐⭐⭐⭐│
│  LLM: gemma3-270m.gguf    278 MB  ⭐⭐⭐⭐⭐│
├─────────────────────────────────────────┤
│  TOTAL:                   632 MB        │
│  Espace utilisé:           85%          │
│  Espace libre:            111 MB        │
└─────────────────────────────────────────┘
```

### Avantages

- ✅ **4 fonctionnalités** au lieu de 2 (STT + LLM)
- ✅ **STT amélioré** (74 MB vs 465 MB, meilleure qualité)
- ✅ **Embeddings offline** (RAG fonctionne sans internet)
- ✅ **TTS moderne** (voix naturelles, émotions)
- ✅ **Espace bien utilisé** (85% utilisation, 111 MB libre)

### Option avec Vision (747 MB)

Si vous voulez aussi la Vision, utilisez `whisper-tiny` (39 MB) au lieu de `whisper-base`:

```
STT: whisper-tiny         39 MB
Embeddings: all-MiniLM     80 MB
TTS: speecht5_tts        200 MB
Vision: clip-vit-base    150 MB
LLM: gemma3-270m.gguf    278 MB
─────────────────────────────────
TOTAL:                   747 MB  (100% utilisation)
```

---

## 📥 TÉLÉCHARGEMENTS NÉCESSAIRES

### Configuration Optimale (632 MB)

- `whisper-base`: 74 MB
- `all-MiniLM-L6-v2`: 80 MB
- `speecht5_tts`: 200 MB
- **Total à télécharger**: **354 MB**

### Configuration avec Vision (747 MB)

- `whisper-tiny`: 39 MB
- `all-MiniLM-L6-v2`: 80 MB
- `speecht5_tts`: 200 MB
- `clip-vit-base-patch32`: 150 MB
- **Total à télécharger**: **469 MB**

---

## ✅ RÉSUMÉ

**Espace actuel utilisé**: 743 MB (gemma3-270m.gguf + ggml-small.bin)

**Configuration optimale proposée**: 632 MB
- Remplacement: `ggml-small.bin` (465 MB) → `whisper-base` (74 MB)
- Ajout: Embeddings offline (80 MB) + TTS moderne (200 MB)
- **Gain net**: -391 MB (STT) + 280 MB (nouvelles fonctionnalités) = **-111 MB**

**Résultat**: 4 fonctionnalités au lieu de 2, meilleure qualité, espace mieux utilisé !


