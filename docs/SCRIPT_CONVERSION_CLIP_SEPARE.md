# 🔧 Script Conversion CLIP - Export séparé

## Date: 2025-11-29

---

## ✅ SCRIPT CRÉÉ

**Fichier:** `E:\ChatAI-Models\vision\convert_clip_separate.py`

Ce script exporte les deux composants CLIP séparément:
- `vision_model.onnx` (Vision Encoder)
- `text_model.onnx` (Text Encoder)

---

## 📋 DESCRIPTION

### Fonctionnalités

1. **Export Vision Encoder:**
   - Charge `model.vision_model` depuis CLIP
   - Exporte vers `vision_model.onnx`
   - Input: Images normalisées [batch, 3, 224, 224]
   - Output: Embeddings 512 dimensions

2. **Export Text Encoder:**
   - Charge `model.text_model` depuis CLIP
   - Exporte vers `text_model.onnx`
   - Input: Token IDs [batch, sequence_length] + attention_mask
   - Output: Embeddings 512 dimensions

3. **Vérification automatique:**
   - Vérifie les dépendances Python
   - Affiche les tailles des fichiers générés
   - Messages d'erreur détaillés

---

## 🚀 USAGE

```bash
cd E:\ChatAI-Models\vision
python convert_clip_separate.py
```

### Sortie attendue

```
=== CONVERSION CLIP -> ONNX (Export séparé) ===
Modèle source: openai/clip-vit-base-patch32
Répertoire sortie: E:\ChatAI-Models\vision\onnx

[INFO] Chargement modèle CLIP depuis Hugging Face...
[OK] Modèle CLIP chargé

=== EXPORT VISION ENCODER ===
[INFO] Export Vision Encoder vers ONNX...
[OK] Vision Encoder exporté: 350.00 MB

=== EXPORT TEXT ENCODER ===
[INFO] Export Text Encoder vers ONNX...
[OK] Text Encoder exporté: 120.00 MB

=== FICHIERS GÉNÉRÉS ===
  vision_model.onnx: 350.00 MB
  text_model.onnx: 120.00 MB

[SUCCESS] Conversion complète!
```

---

## 🔧 DÉTAILS TECHNIQUES

### Wrappers pour export

Le script utilise des wrappers PyTorch pour capturer correctement les outputs:

**Vision Encoder Wrapper:**
- Prend `pixel_values` en input
- Retourne `last_hidden_state` et `pooled_output` (premier token)

**Text Encoder Wrapper:**
- Prend `input_ids` et `attention_mask` en input
- Retourne `last_hidden_state` et `pooled_output` (dernier token non-padded)

### Paramètres d'export

- **Opset version:** 14 (compatible avec ONNX Runtime Android)
- **Dynamic axes:** Support batch_size variable
- **Constant folding:** Activé pour optimiser

---

## 📊 COMPATIBILITÉ

### Code Android

Les fichiers générés sont compatibles avec `OnnxVisionManager.kt`:
- ✅ `vision_model.onnx` → `VISION_MODEL_PATH`
- ✅ `text_model.onnx` → `TEXT_MODEL_PATH`

### Inputs/Outputs

**Vision Model:**
- Input: `pixel_values` [batch, 3, 224, 224]
- Outputs: `last_hidden_state`, `pooled_output`

**Text Model:**
- Inputs: `input_ids` [batch, seq_len], `attention_mask` [batch, seq_len]
- Outputs: `last_hidden_state`, `pooled_output`

---

## ⚠️ NOTES

### Différence avec l'ancien script

- **Ancien:** `convert_to_onnx.py` → `model.onnx` (unifié)
- **Nouveau:** `convert_clip_separate.py` → `vision_model.onnx` + `text_model.onnx` (séparés)

### Fichiers tokenizer

Les fichiers tokenizer (`tokenizer.json`, `vocab.json`, etc.) doivent être présents depuis la conversion précédente. Ils ne sont pas régénérés par ce script.

---

## 🔄 PROCHAINES ÉTAPES

1. ✅ Exécuter le script pour générer les fichiers
2. ⏳ Transférer `vision_model.onnx` et `text_model.onnx` vers le device
3. ⏳ Tester l'initialisation dans l'application Android
4. ⏳ Vérifier que Vision ONNX fonctionne correctement

---

**Statut:** ✅ Script créé et prêt à être exécuté


