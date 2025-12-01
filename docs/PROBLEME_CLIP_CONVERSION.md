# ⚠️ PROBLÈME - Conversion CLIP ONNX

## Date: 2025-11-29

---

## 🔍 PROBLÈME IDENTIFIÉ

**Fichier généré:** `model.onnx` (577 MB)  
**Fichiers attendus:** `vision_model.onnx` + `text_model.onnx`

Le script de conversion CLIP génère un seul fichier `model.onnx`, mais le code Android attend deux fichiers séparés pour CLIP:
- `vision_model.onnx` (encoder d'images)
- `text_model.onnx` (encoder de texte)

---

## 📋 ANALYSE

### Structure CLIP

CLIP (Contrastive Language-Image Pre-training) a **deux composants distincts**:
1. **Vision Encoder** - Encode les images en embeddings (512 dimensions)
2. **Text Encoder** - Encode le texte en embeddings (512 dimensions)

### Conversion actuelle

Le script utilise:
```bash
optimum-cli export onnx \
  --model openai/clip-vit-base-patch32 \
  output_dir \
  --task image-feature-extraction
```

Cette commande génère seulement le **vision encoder** dans `model.onnx`.

---

## 🔧 SOLUTIONS POSSIBLES

### Solution 1: Modifier le script pour exporter les deux composants séparément

CLIP devrait normalement exporter les deux composants. Il faut:
1. Exporter le vision encoder séparément
2. Exporter le text encoder séparément

**Script modifié nécessaire:**
- Exporter `vision_model` avec `image-feature-extraction`
- Exporter `text_model` avec `text-feature-extraction` ou extraction manuelle

### Solution 2: Modifier le code Android pour utiliser un modèle unifié

Si `model.onnx` contient les deux composants (peu probable), adapter le code pour:
- Détecter les inputs/outputs du modèle
- Utiliser les bonnes entrées pour vision vs texte

### Solution 3: Renommer/Créer symlinks (solution temporaire)

Si `model.onnx` est vraiment le vision encoder seulement:
- Renommer `model.onnx` → `vision_model.onnx`
- Créer un modèle text séparé ou utiliser un fallback

---

## 📊 FICHIERS GÉNÉRÉS ACTUELLEMENT

**Répertoire:** `E:\ChatAI-Models\vision\onnx\`

```
- model.onnx (577 MB)          ⚠️ Nom générique
- config.json
- tokenizer.json
- vocab.json
- merges.txt
- preprocessor_config.json
- tokenizer_config.json
- special_tokens_map.json
```

**Device:** `/storage/emulated/0/ChatAI-Files/models/vision/`

Même structure - un seul `model.onnx` au lieu de `vision_model.onnx` + `text_model.onnx`.

---

## 🔍 VÉRIFICATION NÉCESSAIRE

Pour déterminer la meilleure solution, il faut vérifier:

1. **Le contenu de `model.onnx`:**
   - Contient-il seulement le vision encoder?
   - Contient-il vision + text ensemble?
   - Quels sont les inputs/outputs?

2. **La documentation optimum-cli pour CLIP:**
   - Comment exporter les deux composants séparément?
   - Existe-t-il un flag pour séparer vision/text?

3. **Le code Android actuel:**
   - Utilise-t-il vraiment les deux composants séparément?
   - Peut-on adapter pour un modèle unifié?

---

## 📝 PROCHAINES ÉTAPES

1. ✅ Analyser le script de conversion actuel
2. ⚠️ Vérifier la structure de `model.onnx` (vision seulement ou unifié?)
3. 🔧 Corriger le script pour exporter les 2 composants séparément
4. 🔄 OU adapter le code Android pour utiliser le modèle unifié
5. ✅ Tester la correction

---

**Statut:** ⚠️ En attente de correction


