# ⏳ Conversion ONNX en Cours

**Date**: 2025-11-27  
**Statut**: 🔄 **EN COURS**

---

## 📋 PROCESSUS

### Étape 1: Installation Dépendances ✅

**Dépendances installées**:
- `optimum[onnxruntime]`
- `transformers`
- `torch`
- `onnxruntime`

---

### Étape 2: Conversion Modèle 🔄

**Script**: `scripts/convert_speecht5_to_onnx_complete.py`

**Processus**:
1. Téléchargement modèle SpeechT5 depuis Hugging Face
2. Export ONNX du modèle
3. Génération vocabulaire (`vocab.json`)
4. Génération speaker embeddings (`default_speaker_embeddings.json`)

**Durée estimée**: 10-30 minutes
- Téléchargement modèle: 5-15 min (première fois)
- Conversion ONNX: 5-10 min
- Génération fichiers: 1-2 min

---

## 📁 FICHIERS GÉNÉRÉS

**Répertoire**: `E:/ChatAI-Models/tts/onnx/`

**Fichiers attendus**:
- `model.onnx` (~80-150 MB)
- `vocab.json` (~1-2 MB)
- `default_speaker_embeddings.json` (~10 KB)

---

## ⚠️ NOTES

- **Premier téléchargement**: Le modèle SpeechT5 sera téléchargé depuis Hugging Face (peut prendre 10-20 min)
- **Espace disque**: Nécessite ~200-300 MB d'espace libre
- **Réseau**: Nécessite connexion Internet stable

---

## ✅ PROCHAINES ÉTAPES

Une fois la conversion terminée:

1. **Vérifier fichiers générés**
   ```bash
   Get-ChildItem E:\ChatAI-Models\tts\onnx\
   ```

2. **Transférer vers device**
   ```powershell
   .\scripts\transfer_tts_onnx_to_device.ps1
   ```

3. **Tester**
   - Compiler APK
   - Installer sur device
   - Tester TTS

---

**Conversion en cours...** ⏳


