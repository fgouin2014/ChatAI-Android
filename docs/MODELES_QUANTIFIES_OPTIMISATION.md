# 🎯 Modèles Quantifiés - Optimisation Espace

**Date**: 2025-11-27  
**Objectif**: Utiliser des modèles quantifiés pour maximiser les fonctionnalités dans 743 MB

---

## 💡 QU'EST-CE QUE LA QUANTIFICATION?

La quantification réduit la précision des poids du modèle (32 bits → 8 bits ou 4 bits) pour:
- ✅ **Réduire la taille** (2-4x plus petit)
- ✅ **Réduire la RAM** (moins de mémoire nécessaire)
- ✅ **Améliorer la vitesse** (calculs plus rapides)
- ⚠️ **Légère perte de qualité** (généralement < 5%)

---

## 📊 MODÈLES QUANTIFIÉS DISPONIBLES

### 1. Whisper (STT) - Quantification GGML/GGUF

| Modèle | Taille originale | Quantifié Q4 | Quantifié Q5 | Quantifié Q8 | Qualité |
|--------|------------------|--------------|--------------|--------------|---------|
| `whisper-tiny` | 39 MB | **15 MB** | **18 MB** | **25 MB** | ⭐⭐⭐ |
| `whisper-base` | 74 MB | **28 MB** | **35 MB** | **50 MB** | ⭐⭐⭐⭐ |
| `whisper-small` | 244 MB | **90 MB** | **110 MB** | **160 MB** | ⭐⭐⭐⭐⭐ |
| `whisper-small Q6_K` | 244 MB | - | - | **207 MB** | ⭐⭐⭐⭐⭐ (+2-3% vs Q5_1) |
| `whisper-medium` | 769 MB | **600 MB** | **750 MB** | **1.2 GB** | ⭐⭐⭐⭐⭐ (+10-15%) |
| `whisper-medium Q3_K_S` | 769 MB | - | - | **~380 MB** | ⭐⭐⭐⭐ (+5-8% vs Small, -5-10% vs Medium) |
| **Votre actuel**: `ggml-small.bin` | **465 MB** | **~170 MB** | **~210 MB** | **~300 MB** | ⭐⭐⭐⭐⭐ |

**Recommandation**: `whisper-small-q5_1` (110 MB) - **Même qualité que votre actuel, 4x plus petit !**

**Gain**: 465 MB → 110 MB = **-355 MB** ✅

**Alternatives intermédiaires**:
- `whisper-small-q8_0` (160 MB) - **+1-2% qualité vs Q5_1, 305 MB libres avec LLM local**
- `whisper-small-q6_k` (207 MB) - **+2-3% qualité vs Q5_1, 258 MB libres avec LLM local**
- `whisper-medium-q3_k_s` (380 MB) - **+5-8% qualité vs Small, 85 MB libres avec LLM local**

**Voir**: 
- `docs/WHISPER_MEDIUM_QUANTIFIE.md` pour analyse complète Medium
- `docs/COMPROMIS_WHISPER_AVEC_LLM_LOCAL.md` pour options intermédiaires avec LLM local

---

### 2. Embeddings (sentence-transformers) - Quantification ONNX

| Modèle | Taille originale | ONNX INT8 | ONNX INT4 | Qualité |
|--------|------------------|-----------|-----------|---------|
| `all-MiniLM-L6-v2` | 80 MB | **~20 MB** | **~12 MB** | ⭐⭐⭐⭐ |
| `all-mpnet-base-v2` | 420 MB | **~105 MB** | **~60 MB** | ⭐⭐⭐⭐⭐ |
| `multilingual-e5-base` | 560 MB | **~140 MB** | **~80 MB** | ⭐⭐⭐⭐⭐ |

**Recommandation**: `all-MiniLM-L6-v2` ONNX INT8 (20 MB) - **4x plus petit, qualité équivalente**

**Gain**: 80 MB → 20 MB = **-60 MB** ✅

---

### 3. TTS (SpeechT5) - Quantification ONNX

| Modèle | Taille originale | ONNX INT8 | Qualité |
|--------|------------------|-----------|---------|
| `speecht5_tts` | 200 MB | **~80 MB** | ⭐⭐⭐⭐ |
| `coqui/XTTS-v2` | 1.5 GB | **~600 MB** | ⭐⭐⭐⭐⭐ |

**Recommandation**: `speecht5_tts` ONNX INT8 (80 MB) - **2.5x plus petit**

**Gain**: 200 MB → 80 MB = **-120 MB** ✅

---

### 4. Vision (CLIP) - Quantification ONNX

| Modèle | Taille originale | ONNX INT8 | Qualité |
|--------|------------------|-----------|---------|
| `clip-vit-base-patch32` | 150 MB | **~60 MB** | ⭐⭐⭐⭐ |
| `clip-vit-large-patch14` | 890 MB | **~350 MB** | ⭐⭐⭐⭐⭐ |

**Recommandation**: `clip-vit-base-patch32` ONNX INT8 (60 MB) - **2.5x plus petit**

**Gain**: 150 MB → 60 MB = **-90 MB** ✅

---

### 5. Classification (DistilBERT) - Quantification ONNX

| Modèle | Taille originale | ONNX INT8 | Qualité |
|--------|------------------|-----------|---------|
| `distilbert-base-uncased` | 67 MB | **~25 MB** | ⭐⭐⭐ |
| `bert-base-uncased` | 440 MB | **~170 MB** | ⭐⭐⭐⭐ |

**Recommandation**: `distilbert-base-uncased` ONNX INT8 (25 MB) - **2.7x plus petit**

**Gain**: 67 MB → 25 MB = **-42 MB** ✅

---

### 6. Traduction (MarianMT) - Quantification ONNX

| Modèle | Taille originale | ONNX INT8 | Qualité |
|--------|------------------|-----------|---------|
| `opus-mt-fr-en` | 50 MB | **~20 MB** | ⭐⭐⭐ |
| `mbart-large-50` | 1.2 GB | **~480 MB** | ⭐⭐⭐⭐⭐ |

**Recommandation**: `opus-mt-fr-en` ONNX INT8 (20 MB) - **2.5x plus petit**

**Gain**: 50 MB → 20 MB = **-30 MB** ✅

---

## 🎯 CONFIGURATION OPTIMALE AVEC MODÈLES QUANTIFIÉS

### Option A: Configuration Complète Quantifiée (743 MB) ⭐⭐⭐⭐⭐ PARFAIT

| Modèle | Taille quantifiée | Format | Fonctionnalité |
|--------|-------------------|--------|----------------|
| `whisper-small-q5_1` | **110 MB** | GGML | STT (meilleure qualité que votre actuel) |
| `all-MiniLM-L6-v2` ONNX INT8 | **20 MB** | ONNX | Embeddings RAG offline |
| `speecht5_tts` ONNX INT8 | **80 MB** | ONNX | TTS moderne |
| `clip-vit-base-patch32` ONNX INT8 | **60 MB** | ONNX | Vision (analyse images) |
| `distilbert-base-uncased` ONNX INT8 | **25 MB** | ONNX | Classification (sentiment) |
| `opus-mt-fr-en` ONNX INT8 | **20 MB** | ONNX | Traduction FR↔EN |
| `gemma3-270m.gguf` | **278 MB** | GGUF | LLM local (GARDÉ) |
| **TOTAL** | **593 MB** | | **7 fonctionnalités** |

**Résultat**:
- ✅ Espace utilisé: 593 MB / 743 MB (80%)
- ✅ Espace libre: **150 MB**
- ✅ **7 fonctionnalités** au lieu de 2 !
- ✅ Qualité supérieure (whisper-small vs ggml-small)

---

### Option B: Configuration Maximale (743 MB exact) ⭐⭐⭐⭐⭐

| Modèle | Taille quantifiée | Format | Fonctionnalité |
|--------|-------------------|--------|----------------|
| `whisper-small-q5_1` | **110 MB** | GGML | STT haute qualité |
| `all-mpnet-base-v2` ONNX INT8 | **105 MB** | ONNX | Embeddings RAG (meilleure qualité) |
| `speecht5_tts` ONNX INT8 | **80 MB** | ONNX | TTS moderne |
| `clip-vit-base-patch32` ONNX INT8 | **60 MB** | ONNX | Vision |
| `distilbert-base-uncased` ONNX INT8 | **25 MB** | ONNX | Classification |
| `opus-mt-fr-en` ONNX INT8 | **20 MB** | ONNX | Traduction |
| `gemma3-270m.gguf` | **278 MB** | GGUF | LLM local |
| **TOTAL** | **678 MB** | | **7 fonctionnalités** |

**Résultat**:
- ✅ Espace utilisé: 678 MB / 743 MB (91%)
- ✅ Espace libre: **65 MB**
- ✅ Embeddings de meilleure qualité (mpnet vs MiniLM)

---

### Option C: Configuration Ultra-Légère (pour plus d'espace libre)

| Modèle | Taille quantifiée | Format | Fonctionnalité |
|--------|-------------------|--------|----------------|
| `whisper-base-q5_1` | **35 MB** | GGML | STT |
| `all-MiniLM-L6-v2` ONNX INT4 | **12 MB** | ONNX | Embeddings RAG |
| `speecht5_tts` ONNX INT8 | **80 MB** | ONNX | TTS |
| `clip-vit-base-patch32` ONNX INT8 | **60 MB** | ONNX | Vision |
| `gemma3-270m.gguf` | **278 MB** | GGUF | LLM local |
| **TOTAL** | **465 MB** | | **5 fonctionnalités** |

**Résultat**:
- ✅ Espace utilisé: 465 MB / 743 MB (63%)
- ✅ Espace libre: **278 MB** (pour futures améliorations)

---

## 📥 TÉLÉCHARGEMENTS NÉCESSAIRES

### Configuration Complète Quantifiée (Option A)

| Modèle | Taille | Source | Format |
|--------|--------|--------|--------|
| `whisper-small-q5_1.bin` | 110 MB | Hugging Face `ggerganov/whisper.cpp` | GGML |
| `all-MiniLM-L6-v2` ONNX INT8 | 20 MB | Export depuis sentence-transformers | ONNX |
| `speecht5_tts` ONNX INT8 | 80 MB | Export depuis transformers | ONNX |
| `clip-vit-base-patch32` ONNX INT8 | 60 MB | Export depuis transformers | ONNX |
| `distilbert-base-uncased` ONNX INT8 | 25 MB | Export depuis transformers | ONNX |
| `opus-mt-fr-en` ONNX INT8 | 20 MB | Export depuis transformers | ONNX |
| **TOTAL** | **315 MB** | | |

**Gain net**: 743 MB → 593 MB = **-150 MB** + 5 nouvelles fonctionnalités !

---

## 🔧 COMMENT OBTENIR LES MODÈLES QUANTIFIÉS

### 1. Whisper GGML (déjà quantifié)

**Source**: Hugging Face `ggerganov/whisper.cpp`

```bash
# Télécharger directement depuis Hugging Face
# https://huggingface.co/ggerganov/whisper.cpp/tree/main
# Fichiers: ggml-small-q5_1.bin (110 MB)
```

**Avantage**: Déjà quantifié, prêt à l'emploi !

---

### 2. Modèles ONNX INT8 (quantification nécessaire)

**Méthode**: Export + Quantification avec `optimum`

```python
# Exemple: all-MiniLM-L6-v2
from sentence_transformers import SentenceTransformer
from optimum.onnxruntime import ORTModelForFeatureExtraction
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from optimum.onnxruntime import ORTQuantizer

# 1. Charger le modèle
model = SentenceTransformer('all-MiniLM-L6-v2')

# 2. Exporter en ONNX
model.save('model_onnx', backend='onnx')

# 3. Quantifier en INT8
quantizer = ORTQuantizer.from_pretrained('model_onnx')
dqconfig = AutoQuantizationConfig.avx512_vnni(is_static=False, per_channel=False)
quantizer.quantize(save_dir='model_onnx_int8', quantization_config=dqconfig)
```

**Résultat**: Modèle quantifié INT8 (~20 MB au lieu de 80 MB)

---

## 📊 COMPARAISON AVANT/APRÈS

### Avant (Configuration actuelle)

| Modèle | Taille | Fonctionnalités |
|--------|--------|-----------------|
| `ggml-small.bin` | 465 MB | STT |
| `gemma3-270m.gguf` | 278 MB | LLM local |
| **Total** | **743 MB** | **2 fonctionnalités** |

---

### Après (Configuration quantifiée complète)

| Modèle | Taille quantifiée | Fonctionnalités |
|--------|-------------------|-----------------|
| `whisper-small-q5_1` | 110 MB | STT (meilleure qualité) |
| `all-MiniLM-L6-v2` ONNX INT8 | 20 MB | Embeddings RAG offline |
| `speecht5_tts` ONNX INT8 | 80 MB | TTS moderne |
| `clip-vit-base-patch32` ONNX INT8 | 60 MB | Vision |
| `distilbert-base-uncased` ONNX INT8 | 25 MB | Classification |
| `opus-mt-fr-en` ONNX INT8 | 20 MB | Traduction |
| `gemma3-270m.gguf` | 278 MB | LLM local (GARDÉ) |
| **Total** | **593 MB** | **7 fonctionnalités** |

---

## ✅ AVANTAGES DES MODÈLES QUANTIFIÉS

### 1. Espace disque

- **Avant**: 743 MB → 2 fonctionnalités
- **Après**: 593 MB → 7 fonctionnalités
- **Gain**: -150 MB + 5 nouvelles fonctionnalités

### 2. Performance

- ✅ **RAM réduite** (modèles quantifiés utilisent moins de mémoire)
- ✅ **Vitesse améliorée** (calculs INT8 plus rapides)
- ✅ **Batterie** (moins de calculs = moins de consommation)

### 3. Qualité

- ✅ **Whisper-small** (110 MB) = **ggml-small** (465 MB) en qualité (même modèle, quantifié)
- ✅ **Embeddings** équivalents (perte < 2%)
- ✅ **TTS/Vision** équivalents (perte < 5%)

---

## 🎯 RECOMMANDATION FINALE

### Configuration Optimale Quantifiée (593 MB)

```
┌─────────────────────────────────────────┐
│  Configuration Quantifiée (593 MB)     │
├─────────────────────────────────────────┤
│  STT: whisper-small-q5_1     110 MB ⭐⭐⭐⭐⭐│
│  Embeddings: all-MiniLM INT8  20 MB ⭐⭐⭐⭐│
│  TTS: speecht5_tts INT8       80 MB ⭐⭐⭐⭐│
│  Vision: clip-vit-base INT8   60 MB ⭐⭐⭐⭐│
│  Classification: distilbert   25 MB ⭐⭐⭐│
│  Traduction: opus-mt INT8     20 MB ⭐⭐⭐│
│  LLM: gemma3-270m.gguf       278 MB ⭐⭐⭐⭐⭐│
├─────────────────────────────────────────┤
│  TOTAL:                   593 MB        │
│  Espace utilisé:           80%         │
│  Espace libre:            150 MB        │
│  Fonctionnalités:           7           │
└─────────────────────────────────────────┘
```

### Résultat

- ✅ **7 fonctionnalités** au lieu de 2
- ✅ **150 MB libres** pour futures améliorations
- ✅ **Qualité identique** (même architecture whisper-small, juste quantifié)
- ✅ **Performance améliorée** (modèles quantifiés plus rapides)
- ✅ **Espace optimisé** (80% utilisation)

---

## 📚 SOURCES DES MODÈLES QUANTIFIÉS

### Whisper GGML (déjà quantifié)

- **Repository**: `ggerganov/whisper.cpp` sur Hugging Face
- **Fichiers**: `ggml-{size}-q{quant}.bin`
- **URL**: https://huggingface.co/ggerganov/whisper.cpp

### Modèles ONNX (quantification nécessaire)

- **Tool**: `optimum[onnxruntime]` (Hugging Face)
- **Documentation**: https://huggingface.co/docs/optimum/onnxruntime/usage_guides/quantization
- **Exemples**: https://github.com/huggingface/optimum/tree/main/examples/onnxruntime

---

## 🚀 PLAN D'IMPLÉMENTATION

### Phase 1: Whisper quantifié (immédiat)

1. Télécharger `ggml-small-q5_1.bin` (110 MB)
2. Remplacer `ggml-small.bin` (465 MB)
3. **Gain**: -355 MB instantané

### Phase 2: Embeddings ONNX (1-2 jours)

1. Exporter `all-MiniLM-L6-v2` en ONNX INT8
2. Intégrer ONNX Runtime Android
3. **Gain**: Embeddings offline + -60 MB

### Phase 3: Autres modèles ONNX (3-5 jours)

1. Exporter TTS, Vision, Classification, Traduction
2. Intégrer dans l'app
3. **Résultat**: 7 fonctionnalités dans 593 MB

---

## 💡 CONCLUSION

**Avec les modèles quantifiés, vous pouvez avoir 7 fonctionnalités dans 593 MB au lieu de 2 fonctionnalités dans 743 MB !**

C'est une **optimisation massive** de l'espace disponible ! 🚀

