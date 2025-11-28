# 📋 FICHIERS NÉCESSAIRES - TTS ONNX

**Date:** 2025-11-27  
**Objectif:** Liste complète des fichiers requis pour le TTS ONNX SpeechT5

---

## ✅ FICHIERS REQUIS

### 1. **Modèles ONNX** (3 fichiers)

**Répertoire:** `/storage/emulated/0/ChatAI-Files/models/tts/`

| Fichier | Taille | Description | Statut |
|---------|--------|-------------|--------|
| `encoder_model.onnx` | ~327 MB | Encoder (texte → embeddings) | ⚠️ **REQUIS** |
| `decoder_model.onnx` | ~227 MB | Decoder (embeddings → mel spectrogram) | ⚠️ **REQUIS** |
| `decoder_postnet_and_vocoder.onnx` | ~53 MB | Postnet + Vocoder (mel → waveform) | ⚠️ **REQUIS** |

**Total modèles:** ~607 MB

---

### 2. **Fichiers Tokenizer** (2 fichiers)

**Répertoire:** `/storage/emulated/0/ChatAI-Files/models/tts/`

| Fichier | Taille | Description | Statut |
|---------|--------|-------------|--------|
| `vocab.json` | ~1-2 MB | Vocabulaire SpeechT5 (mapping token → ID) | ⚠️ **REQUIS** |
| `default_speaker_embeddings.json` | ~2-4 KB | Speaker embeddings par défaut (512 floats) | ⚠️ **REQUIS** |

**Total tokenizer:** ~1-2 MB

---

## 📦 SOURCE DES FICHIERS

### Conversion ONNX

Les fichiers sont générés par le script de conversion:

**Script:** `E:\ChatAI-Models\tts\convert_to_onnx.py`  
**Répertoire sortie:** `E:\ChatAI-Models\tts\onnx\`

**Fichiers générés:**
- `encoder_model.onnx`
- `decoder_model.onnx`
- `decoder_with_past_model.onnx` (optionnel, pour optimisation)
- `decoder_postnet_and_vocoder.onnx`
- `vocab.json`
- `default_speaker_embeddings.json`
- `tokenizer_config.json` (optionnel)
- `preprocessor_config.json` (optionnel)
- `config.json` (optionnel)

---

## 🚀 TRANSFERT VERS DEVICE

### Méthode 1: ADB Push (manuelle)

```bash
# Modèles ONNX
adb push E:\ChatAI-Models\tts\onnx\encoder_model.onnx /storage/emulated/0/ChatAI-Files/models/tts/
adb push E:\ChatAI-Models\tts\onnx\decoder_model.onnx /storage/emulated/0/ChatAI-Files/models/tts/
adb push E:\ChatAI-Models\tts\onnx\decoder_postnet_and_vocoder.onnx /storage/emulated/0/ChatAI-Files/models/tts/

# Fichiers tokenizer
adb push E:\ChatAI-Models\tts\onnx\vocab.json /storage/emulated/0/ChatAI-Files/models/tts/
adb push E:\ChatAI-Models\tts\onnx\default_speaker_embeddings.json /storage/emulated/0/ChatAI-Files/models/tts/
```

### Méthode 2: Script PowerShell (automatique)

**Script:** `E:\ChatAI-Models\tts\transfer_onnx_to_device.ps1`

```powershell
cd E:\ChatAI-Models\tts
.\transfer_onnx_to_device.ps1
```

---

## ✅ VÉRIFICATION

### Vérifier présence des fichiers

```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/
```

**Résultat attendu:**
```
-rw-rw---- 1 u0_aXXX sdcard_rw  327M encoder_model.onnx
-rw-rw---- 1 u0_aXXX sdcard_rw  227M decoder_model.onnx
-rw-rw---- 1 u0_aXXX sdcard_rw   53M decoder_postnet_and_vocoder.onnx
-rw-rw---- 1 u0_aXXX sdcard_rw  1.2M vocab.json
-rw-rw---- 1 u0_aXXX sdcard_rw  3.5K default_speaker_embeddings.json
```

### Vérifier logs d'initialisation

```bash
adb logcat | Select-String "OnnxTTSManager"
```

**Logs attendus (succès):**
```
OnnxTTSManager: Initialisation ONNX TTS (SpeechT5 multi-modèles)...
OnnxTTSManager: Chargement encoder_model.onnx...
OnnxTTSManager: Chargement decoder_model.onnx...
OnnxTTSManager: Chargement decoder_postnet_and_vocoder.onnx...
OnnxTTSManager: ✅ Tokenizer initialisé
OnnxTTSManager: ✅ ONNX TTS prêt (3 modèles, 607 MB total)
```

**Logs d'erreur (fichiers manquants):**
```
OnnxTTSManager: Modèles ONNX manquants:
OnnxTTSManager:   - /storage/emulated/0/ChatAI-Files/models/tts/encoder_model.onnx
OnnxTTSManager: ❌ Tokenizer non initialisé - fichier vocab.json ou default_speaker_embeddings.json manquant
```

---

## 🔍 DIAGNOSTIC ERREUR 500

### Erreur: "TTS synthesis failed: No audio generated"

**Causes possibles:**

1. **Fichiers manquants:**
   - Modèles ONNX non présents
   - `vocab.json` manquant
   - `default_speaker_embeddings.json` manquant

2. **Inférence ONNX échoue:**
   - Inputs decoder incorrects (noms d'inputs)
   - Inputs vocoder incorrects (noms d'inputs)
   - Shapes incompatibles entre modèles

3. **Tokenizer non initialisé:**
   - `vocab.json` corrompu ou invalide
   - `default_speaker_embeddings.json` corrompu ou invalide

**Vérification:**
```bash
# Vérifier logs détaillés
adb logcat | Select-String "OnnxTTSManager|TTSServer"
```

**Logs à rechercher:**
- `Preprocessing texte:` - Vérifie tokenisation
- `Étape 1/3: Encoder...` - Vérifie encoder
- `Étape 2/3: Decoder...` - Vérifie decoder
- `Étape 3/3: Vocoder...` - Vérifie vocoder
- `Erreur inference ONNX:` - Erreur détaillée

---

## 📊 STATUT ACTUEL

| Fichier | Présent | Taille | Statut |
|---------|---------|--------|--------|
| `encoder_model.onnx` | ❓ | ~327 MB | À vérifier |
| `decoder_model.onnx` | ❓ | ~227 MB | À vérifier |
| `decoder_postnet_and_vocoder.onnx` | ❓ | ~53 MB | À vérifier |
| `vocab.json` | ❓ | ~1-2 MB | À vérifier |
| `default_speaker_embeddings.json` | ❓ | ~2-4 KB | À vérifier |

**Action requise:** Vérifier présence de tous les fichiers sur le device

---

## 🎯 PROCHAINES ÉTAPES

1. ✅ Créer fonction de vérification dans l'UI TTS
2. ✅ Afficher statut de chaque fichier (présent/manquant)
3. ✅ Ajouter bouton "Vérifier fichiers" dans onglet TTS
4. ✅ Améliorer messages d'erreur avec détails des fichiers manquants

---

**Document créé le:** 2025-11-27  
**Dernière mise à jour:** 2025-11-27


