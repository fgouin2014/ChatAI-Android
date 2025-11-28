# 📝 Scripts Conversion ONNX - Modèles Hugging Face

**Date**: 2025-11-27  
**Statut**: ✅ **SCRIPTS CRÉÉS**

---

## 📋 SCRIPTS DISPONIBLES

### 1. ✅ TTS (SpeechT5) - DÉJÀ FAIT

**Fichier**: `E:/ChatAI-Models/tts/convert_to_onnx.py`  
**Modèle**: `microsoft/speecht5_tts`  
**Statut**: ✅ Conversion réussie, fichiers sur device

---

### 2. ✅ Embeddings (all-MiniLM-L6-v2)

**Fichier**: `E:/ChatAI-Models/embeddings/convert_to_onnx.py`  
**Modèle**: `sentence-transformers/all-MiniLM-L6-v2`  
**Task**: `feature-extraction`  
**Priorité**: ⭐⭐⭐ **HAUTE** (pour RAG offline)

**Usage**:
```bash
cd E:\ChatAI-Models\embeddings
python convert_to_onnx.py
```

**Fichiers générés**:
- `model.onnx` (~20-30 MB)
- `tokenizer.json`
- `config.json`

**Destination device**:
- `/storage/emulated/0/ChatAI-Files/models/embeddings/`

---

### 3. ✅ Vision (CLIP)

**Fichier**: `E:/ChatAI-Models/vision/convert_to_onnx.py`  
**Modèle**: `openai/clip-vit-base-patch32`  
**Task**: `image-feature-extraction`  
**Priorité**: ⭐⭐ Moyenne

**Usage**:
```bash
cd E:\ChatAI-Models\vision
python convert_to_onnx.py
```

**Fichiers générés**:
- `vision_model.onnx` (image encoder)
- `text_model.onnx` (text encoder)
- `tokenizer.json`
- `config.json`

**Destination device**:
- `/storage/emulated/0/ChatAI-Files/models/vision/`

---

### 4. ✅ Classification (DistilBERT)

**Fichier**: `E:/ChatAI-Models/classification/convert_to_onnx.py`  
**Modèle**: `distilbert-base-uncased`  
**Task**: `text-classification`  
**Priorité**: ⭐⭐ Moyenne

**Usage**:
```bash
cd E:\ChatAI-Models\classification
python convert_to_onnx.py
```

**Fichiers générés**:
- `model.onnx` (~25-30 MB)
- `tokenizer.json`
- `config.json`

**Destination device**:
- `/storage/emulated/0/ChatAI-Files/models/classification/`

---

### 5. ✅ Translation (MarianMT)

**Fichier**: `E:/ChatAI-Models/translation/convert_to_onnx.py`  
**Modèle**: `Helsinki-NLP/opus-mt-fr-en`  
**Task**: `translation`  
**Priorité**: ⭐ Faible

**Usage**:
```bash
cd E:\ChatAI-Models\translation
python convert_to_onnx.py
```

**Fichiers générés**:
- `encoder_model.onnx`
- `decoder_model.onnx`
- `tokenizer.json`
- `config.json`

**Destination device**:
- `/storage/emulated/0/ChatAI-Files/models/translation/`

---

## 🔧 PRÉREQUIS

**Dépendances Python**:
```bash
pip install optimum[onnxruntime] transformers torch onnxruntime
```

**Pour Vision** (optionnel):
```bash
pip install pillow
```

---

## 📊 RÉSUMÉ

| Modèle | Script | Statut | Priorité |
|--------|--------|--------|----------|
| **TTS** (SpeechT5) | `tts/convert_to_onnx.py` | ✅ Fait | - |
| **Embeddings** (all-MiniLM-L6-v2) | `embeddings/convert_to_onnx.py` | ⚠️ À exécuter | ⭐⭐⭐ HAUTE |
| **Vision** (CLIP) | `vision/convert_to_onnx.py` | ⚠️ À exécuter | ⭐⭐ Moyenne |
| **Classification** (DistilBERT) | `classification/convert_to_onnx.py` | ⚠️ À exécuter | ⭐⭐ Moyenne |
| **Translation** (MarianMT) | `translation/convert_to_onnx.py` | ⚠️ À exécuter | ⭐ Faible |

---

## 🚀 PROCHAINES ÉTAPES

1. **Exécuter les scripts** dans l'ordre de priorité
2. **Transférer les fichiers ONNX** vers le device (via `adb push`)
3. **Intégrer dans l'application Android** (créer managers ONNX similaires à `OnnxTTSManager`)

---

## 📝 NOTES

- Tous les scripts utilisent `optimum-cli` (méthode recommandée par Hugging Face)
- Les scripts sont dans le même répertoire que les fichiers PyTorch téléchargés
- Chaque script génère les fichiers dans un sous-répertoire `onnx/`
- Les scripts vérifient automatiquement les dépendances Python


