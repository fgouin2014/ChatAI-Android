# ✅ Scanner Modèles - Mis à Jour pour ONNX

**Date**: 2025-11-27  
**Statut**: ✅ **MIS À JOUR** - Support ONNX + GGUF + GGML

---

## 🎯 AMÉLIORATIONS

### Scanner récursif

La fonction `scanLocalDeviceModels()` scanne maintenant **récursivement** tous les sous-dossiers de `/storage/emulated/0/ChatAI-Files/models/`.

### Types de modèles détectés

1. **GGUF** (`.gguf`)
   - Modèles LLM Ollama
   - Exemple: `gemma3-270m.gguf`

2. **ONNX** (`.onnx`)
   - Modèles TTS: `tts/encoder_model.onnx`, `tts/decoder_model.onnx`, etc.
   - Modèles Embeddings: `embeddings/model.onnx`
   - Modèles Vision: `vision/vision_model.onnx`, `vision/text_model.onnx`
   - Modèles Classification: `classification/model.onnx`
   - Modèles Traduction: `translation/model.onnx`

3. **GGML** (`.bin` dans `whisper/`)
   - Modèles Whisper STT
   - Exemple: `whisper/ggml-small.bin`

---

## 📊 STRUCTURE DÉTECTÉE

### Catégories automatiques

Le scanner détecte automatiquement la catégorie basée sur le dossier parent:

| Dossier | Catégorie |
|---------|-----------|
| `/models/tts/` | **TTS** |
| `/models/embeddings/` | **Embeddings** |
| `/models/vision/` | **Vision** |
| `/models/classification/` | **Classification** |
| `/models/translation/` | **Traduction** |
| `/models/whisper/` | **STT (Whisper)** |
| `/models/` (racine) | **LLM (Ollama)** |
| Autre | **Autre** |

---

## 📋 FORMAT JSON RETOURNÉ

```json
[
  {
    "name": "gemma3-270m.gguf",
    "size": 291553280,
    "type": "GGUF",
    "path": "gemma3-270m.gguf",
    "category": "LLM (Ollama)"
  },
  {
    "name": "encoder_model.onnx",
    "size": 342826496,
    "type": "ONNX",
    "path": "tts/encoder_model.onnx",
    "category": "TTS"
  },
  {
    "name": "model.onnx",
    "size": 90356992,
    "type": "ONNX",
    "path": "embeddings/model.onnx",
    "category": "Embeddings"
  },
  {
    "name": "ggml-small.bin",
    "size": 488216064,
    "type": "GGML (Whisper)",
    "path": "whisper/ggml-small.bin",
    "category": "STT (Whisper)"
  }
]
```

---

## 🎨 AFFICHAGE WEBAPP

### Groupement par catégorie

Les modèles sont maintenant **groupés par catégorie** dans l'interface:

```
📱 TTS (4 modèles, 807.5 MB)
  ├─ encoder_model.onnx [ONNX] 327.0 MB
  ├─ decoder_model.onnx [ONNX] 227.4 MB
  ├─ decoder_with_past_model.onnx [ONNX] 200.3 MB
  └─ decoder_postnet_and_vocoder.onnx [ONNX] 52.9 MB

📱 Embeddings (1 modèle, 86.2 MB)
  └─ model.onnx [ONNX] 86.2 MB

📱 LLM (Ollama) (1 modèle, 278.0 MB)
  └─ gemma3-270m.gguf [GGUF] 278.0 MB

📱 STT (Whisper) (1 modèle, 465.5 MB)
  └─ ggml-small.bin [GGML (Whisper)] 465.5 MB
```

### Badges visuels

- **Type de modèle**: Badge coloré (ONNX, GGUF, GGML)
- **Catégorie**: Titre avec compteur et taille totale
- **Tailles**: Affichage en MB avec 1 décimale

---

## 🔧 FICHIERS MODIFIÉS

### Backend
- ✅ `ChatAI-Android/app/src/main/java/com/chatai/WebAppInterface.java`
  - Ligne 1368-1518: Fonction `scanLocalDeviceModels()` mise à jour
  - Ajout fonction helper `scanDirectoryRecursive()`
  - Détection multi-types (GGUF, ONNX, GGML)
  - Catégorisation automatique

### Frontend
- ✅ `ChatAI-Android/app/src/main/assets/webapp/index.html`
  - Ligne 2721-2748: Affichage amélioré avec groupement par catégorie
  - Badges pour types de modèles
  - Totaux par catégorie

---

## ✅ RÉSULTAT

Le scanner détecte maintenant **tous les modèles**:
- ✅ GGUF (LLM Ollama)
- ✅ ONNX (TTS, Embeddings, Vision, Classification, Traduction)
- ✅ GGML (Whisper STT)

Et les affiche de manière **organisée et lisible** par catégorie dans l'interface !


