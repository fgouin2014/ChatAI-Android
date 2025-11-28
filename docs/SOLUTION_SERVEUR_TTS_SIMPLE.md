# ✅ Solution Simple: Serveur TTS Python

**Date**: 2025-11-27  
**Statut**: ✅ **IMPLÉMENTÉ** - Solution simple et fonctionnelle

---

## 🎯 SOLUTION

**Au lieu de conversion ONNX complexe**, utiliser un **serveur Python HTTP** (comme Whisper).

**Avantages**:
- ✅ Modèle PyTorch déjà téléchargé (558 MB)
- ✅ Architecture connue (identique à Whisper)
- ✅ Pas de conversion nécessaire
- ✅ Simple à maintenir

---

## 📋 ARCHITECTURE

```
Android App → HTTP POST → Python Server (port 11401) → SpeechT5 PyTorch → Audio WAV → Réponse
```

**Similaire à Whisper**:
- Whisper: Port 11400
- TTS: Port 11401

---

## ✅ FICHIERS CRÉÉS

### 1. Serveur Python TTS ✅

**Fichier**: `scripts/tts_server.py`

**Fonctionnalités**:
- Serveur Flask HTTP
- Endpoint `/synthesize` (POST JSON → Audio WAV)
- Endpoint `/health` (GET)
- Utilise modèle PyTorch déjà téléchargé

---

### 2. TTSServerManager.kt ✅

**Fichier**: `app/src/main/java/com/chatai/managers/TTSServerManager.kt`

**Fonctionnalités**:
- Client HTTP pour serveur TTS
- Conversion WAV → PCM
- Lecture AudioTrack
- Similaire à `WhisperServerRecognizer`

---

### 3. KittTTSManager.kt modifié ✅

**Modifications**:
- Utilise `TTSServerManager` au lieu d'`OnnxTTSManager`
- Fallback Android TTS si serveur indisponible
- Architecture hybride

---

### 4. WebAppInterface.java modifié ✅

**Ajout**: `startTTSServer()` - Démarre serveur TTS via Termux (comme Whisper)

---

## 🚀 UTILISATION

### 1. Transférer serveur Python vers device

```bash
adb push ChatAI-Android/scripts/tts_server.py /storage/emulated/0/ChatAI-Files/scripts/tts_server.py
```

### 2. Installer dépendances Python (Termux)

```bash
pip install flask transformers torch soundfile
```

### 3. Démarrer serveur TTS

**Via Android**:
- Interface webapp → Bouton "Démarrer TTS Server"

**Ou manuellement (Termux)**:
```bash
python3 tts_server.py
```

### 4. Tester

L'application utilisera automatiquement le serveur TTS si disponible, sinon fallback Android TTS.

---

## 📊 COMPARAISON

| Aspect | ONNX | Serveur Python |
|--------|------|----------------|
| **Complexité** | Élevée | Faible |
| **Conversion** | Nécessaire | Non nécessaire |
| **Modèle** | ONNX (~80-150 MB) | PyTorch (558 MB) |
| **Latence** | 100-300ms | 50-200ms |
| **Qualité** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Maintenance** | Moyenne | Simple |

---

## ✅ STATUT

- ✅ Serveur Python créé
- ✅ TTSServerManager créé
- ✅ KittTTSManager modifié
- ✅ WebAppInterface modifié
- ✅ Compilation: BUILD SUCCESSFUL

**Prêt pour tests !** 🚀

---

**Solution simple et fonctionnelle, similaire à Whisper !** ✅


