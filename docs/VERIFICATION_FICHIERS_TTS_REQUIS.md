# ✅ VÉRIFICATION FICHIERS TTS REQUIS

**Date:** 2025-11-28  
**Objectif:** Liste complète des fichiers requis pour le TTS ONNX et comment les vérifier

---

## 📋 FICHIERS REQUIS (OBLIGATOIRES)

### Répertoire: `/storage/emulated/0/ChatAI-Files/models/tts/`

| Fichier | Taille | Description | Statut |
|---------|--------|-------------|--------|
| `encoder_model.onnx` | ~326 MB | Encoder (texte → embeddings) | ⚠️ **OBLIGATOIRE** |
| `decoder_model.onnx` | ~227 MB | Decoder (embeddings → mel spectrogram) | ⚠️ **OBLIGATOIRE** |
| `decoder_postnet_and_vocoder.onnx` | ~52 MB | Postnet + Vocoder (mel → waveform) | ⚠️ **OBLIGATOIRE** |
| `vocab.json` | ~1-2 MB | Vocabulaire SpeechT5 (mapping token → ID) | ⚠️ **OBLIGATOIRE** |
| `default_speaker_embeddings.json` | ~3-5 KB | Speaker embeddings par défaut (512 floats) | ⚠️ **OBLIGATOIRE** |

**Total obligatoire:** ~607 MB

---

## 📋 FICHIERS OPTIONNELS (AMÉLIORENT LA QUALITÉ)

| Fichier | Taille | Description | Statut |
|---------|--------|-------------|--------|
| `male_speaker_embeddings.json` | ~3-5 KB | Speaker embeddings voix masculine | ⚠️ Optionnel (utilise défaut si absent) |
| `female_speaker_embeddings.json` | ~3-5 KB | Speaker embeddings voix féminine | ⚠️ Optionnel (utilise défaut si absent) |

**Note:** Si ces fichiers sont absents, le système utilisera `default_speaker_embeddings.json` pour toutes les voix.

---

## 🔍 VÉRIFICATION RAPIDE (ADB)

### Commande de vérification:

```powershell
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/
```

### Résultat attendu (succès):

```
-rw-rw---- 1 u0_aXXX sdcard_rw  326M encoder_model.onnx
-rw-rw---- 1 u0_aXXX sdcard_rw  227M decoder_model.onnx
-rw-rw---- 1 u0_aXXX sdcard_rw   52M decoder_postnet_and_vocoder.onnx
-rw-rw---- 1 u0_aXXX sdcard_rw  1.2M vocab.json
-rw-rw---- 1 u0_aXXX sdcard_rw  3.5K default_speaker_embeddings.json
-rw-rw---- 1 u0_aXXX sdcard_rw  3.5K male_speaker_embeddings.json      (optionnel)
-rw-rw---- 1 u0_aXXX sdcard_rw  3.5K female_speaker_embeddings.json   (optionnel)
```

### Vérification détaillée avec tailles:

```powershell
adb shell "cd /storage/emulated/0/ChatAI-Files/models/tts && ls -lh && echo '---' && du -sh *"
```

---

## 📊 VÉRIFICATION VIA LOGS

### Logs de succès (tous fichiers présents):

```
OnnxTTSManager: Initialisation ONNX TTS (SpeechT5 multi-modèles)...
OnnxTTSManager: Chargement encoder_model.onnx...
OnnxTTSManager: Chargement decoder_model.onnx...
OnnxTTSManager: Chargement decoder_postnet_and_vocoder.onnx...
SimpleTokenizer: Vocabulaire chargé: 81 tokens
SimpleTokenizer: Speaker embeddings (défaut) chargés: 512 dimensions
SimpleTokenizer: Speaker embeddings (masculin) chargés: 512 dimensions
SimpleTokenizer: Speaker embeddings (féminin) chargés: 512 dimensions
OnnxTTSManager: ✅ Tokenizer initialisé
OnnxTTSManager: ✅ ONNX TTS prêt (3 modèles, 607 MB total)
```

### Logs d'erreur (fichiers manquants):

```
OnnxTTSManager: Modèles ONNX manquants:
OnnxTTSManager:   - /storage/emulated/0/ChatAI-Files/models/tts/encoder_model.onnx
OnnxTTSManager: ❌ Tokenizer non initialisé - fichier vocab.json ou default_speaker_embeddings.json manquant
SimpleTokenizer: Speaker embeddings (masculin) non trouvés, utilisation défaut
SimpleTokenizer: Speaker embeddings (féminin) non trouvés, utilisation défaut
```

---

## 📥 SOURCES DES FICHIERS

### 1. Modèles ONNX

**Générés par conversion PyTorch → ONNX**

**Script de conversion:** `E:\ChatAI-Models\tts\convert_to_onnx.py`

**Fichiers générés:**
- `encoder_model.onnx`
- `decoder_model.onnx`
- `decoder_postnet_and_vocoder.onnx`

### 2. vocab.json

**Source:** Hugging Face `microsoft/speecht5_tts`

**Lien direct:**
```
https://huggingface.co/microsoft/speecht5_tts/resolve/main/vocab.json
```

**Téléchargement:**
```powershell
Invoke-WebRequest -Uri "https://huggingface.co/microsoft/speecht5_tts/resolve/main/vocab.json" -OutFile "E:\ChatAI-Models\tts\vocab.json"
```

### 3. Speaker Embeddings

**Générés par script Python**

**Script:** `ChatAI-Android/scripts/generate_all_speaker_embeddings.py`

**Fichiers générés:**
- `default_speaker_embeddings.json`
- `male_speaker_embeddings.json`
- `female_speaker_embeddings.json`

**Exécution:**
```bash
cd ChatAI-Android/scripts
python generate_all_speaker_embeddings.py
```

---

## 🚀 TRANSFERT VERS DEVICE

### Script PowerShell automatique:

```powershell
# Créer le répertoire si nécessaire
adb shell mkdir -p /storage/emulated/0/ChatAI-Files/models/tts

# Transférer les modèles ONNX
adb push "E:\ChatAI-Models\tts\onnx\encoder_model.onnx" /storage/emulated/0/ChatAI-Files/models/tts/
adb push "E:\ChatAI-Models\tts\onnx\decoder_model.onnx" /storage/emulated/0/ChatAI-Files/models/tts/
adb push "E:\ChatAI-Models\tts\onnx\decoder_postnet_and_vocoder.onnx" /storage/emulated/0/ChatAI-Files/models/tts/

# Transférer vocab.json
adb push "E:\ChatAI-Models\tts\vocab.json" /storage/emulated/0/ChatAI-Files/models/tts/

# Transférer speaker embeddings
adb push "E:\ChatAI-Models\tts\default_speaker_embeddings.json" /storage/emulated/0/ChatAI-Files/models/tts/
adb push "E:\ChatAI-Models\tts\male_speaker_embeddings.json" /storage/emulated/0/ChatAI-Files/models/tts/
adb push "E:\ChatAI-Models\tts\female_speaker_embeddings.json" /storage/emulated/0/ChatAI-Files/models/tts/
```

---

## ✅ CHECKLIST DE VÉRIFICATION

### Fichiers obligatoires (5):

- [ ] `encoder_model.onnx` (~326 MB)
- [ ] `decoder_model.onnx` (~227 MB)
- [ ] `decoder_postnet_and_vocoder.onnx` (~52 MB)
- [ ] `vocab.json` (~1-2 MB)
- [ ] `default_speaker_embeddings.json` (~3-5 KB)

### Fichiers optionnels (2):

- [ ] `male_speaker_embeddings.json` (~3-5 KB)
- [ ] `female_speaker_embeddings.json` (~3-5 KB)

### Vérification logs:

- [ ] Logs montrent "✅ ONNX TTS prêt"
- [ ] Logs montrent "✅ Tokenizer initialisé"
- [ ] Aucune erreur "Modèles ONNX manquants"
- [ ] Aucune erreur "Tokenizer non initialisé"

---

## 🔧 DIAGNOSTIC PROBLÈMES

### Problème: "Modèles ONNX manquants"

**Solution:**
1. Vérifier que les fichiers sont dans `/storage/emulated/0/ChatAI-Files/models/tts/`
2. Vérifier les permissions (doivent être lisibles)
3. Vérifier les tailles (doivent correspondre aux tailles attendues)

### Problème: "Tokenizer non initialisé"

**Solution:**
1. Vérifier présence de `vocab.json`
2. Vérifier présence de `default_speaker_embeddings.json`
3. Vérifier que les fichiers JSON sont valides (pas corrompus)

### Problème: Crash après "✅ Vocoder réussi"

**Solution:**
- Ce n'est **PAS** un problème de fichiers manquants
- C'est un problème de gestion mémoire lors de l'extraction du waveform
- Voir les corrections récentes dans `OnnxTTSManager.kt`

---

## 📊 STATUT ACTUEL (D'après les logs)

D'après les logs récents:
- ✅ `encoder_model.onnx` - **PRÉSENT** (chargé avec succès)
- ✅ `decoder_model.onnx` - **PRÉSENT** (chargé avec succès)
- ✅ `decoder_postnet_and_vocoder.onnx` - **PRÉSENT** (chargé avec succès)
- ✅ `vocab.json` - **PRÉSENT** (81 tokens chargés)
- ✅ `default_speaker_embeddings.json` - **PRÉSENT** (512 dimensions)
- ✅ `male_speaker_embeddings.json` - **PRÉSENT** (512 dimensions)
- ✅ `female_speaker_embeddings.json` - **PRÉSENT** (512 dimensions)

**Conclusion:** Tous les fichiers requis sont présents. Le problème n'est **PAS** lié à des fichiers manquants, mais à la gestion mémoire lors de l'extraction du waveform.

---

**Document créé le:** 2025-11-28  
**Dernière mise à jour:** 2025-11-28

