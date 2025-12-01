# 🔍 ANALYSE PROBLÈME CLIP - Conversion ONNX

## Date: 2025-11-29

---

## 📊 SITUATION ACTUELLE

### Fichiers générés par le script

**Répertoire PC:** `E:\ChatAI-Models\vision\onnx\`
- ✅ `model.onnx` (577 MB) - **Un seul fichier**
- ✅ `config.json`
- ✅ `tokenizer.json`
- ✅ `vocab.json`

### Fichiers attendus par le code Android

**Répertoire Device:** `/storage/emulated/0/ChatAI-Files/models/vision/`
- ❌ `vision_model.onnx` - **Manquant**
- ❌ `text_model.onnx` - **Manquant**
- ✅ `vocab.json` - Présent
- ✅ `tokenizer.json` - Présent

### Fichier trouvé sur device

- ⚠️ `model.onnx` (577 MB) - **Nom incorrect**

---

## 🔍 CAUSE DU PROBLÈME

### Commande utilisée

```bash
optimum-cli export onnx \
  --model openai/clip-vit-base-patch32 \
  output_dir \
  --task image-feature-extraction
```

Cette commande exporte seulement le **vision encoder** (encoder d'images), pas le text encoder.

### Structure CLIP

CLIP a **deux composants distincts**:

1. **Vision Encoder** (`vision_model`)
   - Encode les images en embeddings (512 dimensions)
   - Input: Image bitmap [1, 3, 224, 224]
   - Output: Embedding vector [1, 512]

2. **Text Encoder** (`text_model`)
   - Encode le texte en embeddings (512 dimensions)
   - Input: Token IDs [batch_size, seq_len]
   - Output: Embedding vector [1, 512]

### Code Android actuel

Le code dans `OnnxVisionManager.kt` charge les deux composants séparément:
- `visionSession` → `vision_model.onnx`
- `textSession` → `text_model.onnx`

---

## 🔧 SOLUTIONS POSSIBLES

### Solution 1: Exporter les deux composants séparément (RECOMMANDÉ)

Modifier le script pour exporter les deux composants CLIP séparément.

**Problème:** `optimum-cli` ne supporte pas directement l'export séparé de vision/text pour CLIP.

**Solutions alternatives:**

#### Option A: Extraction manuelle depuis le modèle PyTorch

Utiliser Python pour charger le modèle CLIP et exporter chaque composant séparément:

```python
from transformers import CLIPModel, CLIPProcessor
import torch

model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")

# Exporter vision encoder
vision_encoder = model.vision_model
torch.onnx.export(vision_encoder, ...)

# Exporter text encoder
text_encoder = model.text_model
torch.onnx.export(text_encoder, ...)
```

#### Option B: Utiliser deux exports optimum-cli

```bash
# Vision encoder
optimum-cli export onnx --model openai/clip-vit-base-patch32 --task image-feature-extraction vision_output

# Text encoder (nécessite custom export)
```

#### Option C: Modifier le script pour extraire depuis le modèle unifié

Si `model.onnx` contient les deux composants, on peut extraire les sous-graphes.

---

### Solution 2: Modifier le code Android pour un modèle unifié

Adapter `OnnxVisionManager.kt` pour utiliser un seul fichier `model.onnx` si:
- Le modèle contient les deux composants
- On peut identifier les inputs/outputs pour vision vs texte

**Problème:** Difficile sans connaître la structure exacte du modèle.

---

### Solution 3: Renommer temporairement + Fallback text

Si `model.onnx` est vraiment le vision encoder seulement:
- Renommer `model.onnx` → `vision_model.onnx`
- Utiliser un fallback pour le text encoder (Ollama/HuggingFace)

**Limitation:** Pas de text encoder ONNX local.

---

## 📋 VÉRIFICATION NÉCESSAIRE

Pour déterminer la meilleure solution, il faut:

1. **Analyser le contenu de `model.onnx`:**
   - Quels inputs/outputs?
   - Contient-il seulement vision ou vision+text?
   - Utiliser Netron pour visualiser

2. **Vérifier la documentation optimum-cli:**
   - Support pour export séparé CLIP?
   - Options disponibles pour multi-component models

3. **Tester avec le modèle actuel:**
   - Peut-on utiliser seulement vision encoder?
   - Text encoder est-il vraiment nécessaire pour l'usage actuel?

---

## 🎯 RECOMMANDATION

**Solution recommandée:** Solution 1 - Option A (Extraction manuelle)

Créer un script Python qui:
1. Charge le modèle CLIP complet
2. Exporte `vision_model.onnx` séparément
3. Exporte `text_model.onnx` séparément

**Avantages:**
- Contrôle total sur l'export
- Deux fichiers séparés comme attendu par le code
- Compatible avec l'architecture actuelle

**Inconvénient:**
- Nécessite script Python personnalisé
- Plus complexe que optimum-cli

---

## 📝 PROCHAINES ÉTAPES

1. ✅ Analyser la structure de `model.onnx` (Netron)
2. 🔧 Créer script Python pour exporter les deux composants séparément
3. 🔄 Tester avec les nouveaux fichiers
4. ✅ Mettre à jour la documentation

---

**Statut:** ⚠️ En attente d'analyse approfondie et correction


