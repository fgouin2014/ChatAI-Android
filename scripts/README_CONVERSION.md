# 🔧 Guide Conversion ONNX TTS

## 📍 Emplacement Scripts

**Répertoire**: `ChatAI-Android/scripts/`

**Scripts disponibles**:
- `convert_speecht5_to_onnx_complete.py` - Conversion complète (recommandé)
- `convert_tts_to_onnx.py` - Version simple
- `transfer_tts_onnx_to_device.ps1` - Transfert vers device

---

## 🚀 Utilisation

### Depuis le répertoire ChatAI-Android:

```bash
cd ChatAI-Android
python scripts/convert_speecht5_to_onnx_complete.py
```

### Depuis le répertoire parent:

```bash
cd ChatAI-Android-beta
python ChatAI-Android/scripts/convert_speecht5_to_onnx_complete.py
```

---

## ⚠️ IMPORTANT

**Le script doit être exécuté depuis le répertoire `ChatAI-Android` ou avec le chemin complet !**

---

## 📁 Fichiers Générés

**Répertoire de sortie**: `E:/ChatAI-Models/tts/onnx/`

**Fichiers**:
- `model.onnx` (~80-150 MB)
- `vocab.json` (~1-2 MB)
- `default_speaker_embeddings.json` (~10 KB)

---

## 🔄 Après Conversion

```powershell
cd ChatAI-Android
.\scripts\transfer_tts_onnx_to_device.ps1
```


