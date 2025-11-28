# 🧪 Test Serveur TTS Natif

**Date**: 2025-11-27  
**Statut**: Fichiers ONNX transférés ✅

---

## ✅ VÉRIFICATION FICHIERS

**Vérifier que les fichiers sont présents**:
```bash
adb shell "ls -lh /storage/emulated/0/ChatAI-Files/models/tts/*.onnx"
adb shell "ls -lh /storage/emulated/0/ChatAI-Files/models/tts/*.json"
```

**Fichiers attendus**:
- `encoder_model.onnx` (~327 MB)
- `decoder_model.onnx` (~227 MB)
- `decoder_postnet_and_vocoder.onnx` (~53 MB)
- `vocab.json`
- `preprocessor_config.json`
- `tokenizer_config.json`
- `spm_char.model`

---

## 🚀 TEST SERVEUR TTS

### 1. Vérifier que le serveur démarre

**Logs Android**:
```bash
adb logcat | Select-String "TTSServer|OnnxTTSManager"
```

**Logs attendus**:
```
TTSServer: Serveur TTS démarré sur le port 11401
OnnxTTSManager: Initialisation ONNX TTS (SpeechT5 multi-modèles)...
OnnxTTSManager: Chargement encoder_model.onnx...
OnnxTTSManager: Chargement decoder_model.onnx...
OnnxTTSManager: Chargement decoder_postnet_and_vocoder.onnx...
OnnxTTSManager: ✅ ONNX TTS prêt (3 modèles, ~607 MB total)
TTSServer: Serveur TTS prêt sur http://127.0.0.1:11401
```

---

### 2. Test Health Check

**Depuis PC** (port forwarding):
```bash
adb forward tcp:11401 tcp:11401
curl http://127.0.0.1:11401/health
```

**Réponse attendue**:
```json
{"status":"ok","model_loaded":true}
```

---

### 3. Test Synthèse Vocale

**Depuis PC**:
```bash
curl -X POST http://127.0.0.1:11401/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text":"Bonjour, ceci est un test de synthèse vocale"}' \
  --output test.wav
```

**Vérifier**:
- Fichier `test.wav` créé
- Taille > 0 bytes
- Peut être lu avec un lecteur audio

---

### 4. Test depuis Android App

**TTSServerManager** utilise automatiquement le serveur si disponible.

**Vérifier logs**:
```bash
adb logcat | Select-String "TTSServerManager|OnnxTTSManager"
```

---

## ⚠️ PROBLÈMES POSSIBLES

### Erreur "Modèles ONNX manquants"
- Vérifier que les fichiers sont dans `/storage/emulated/0/ChatAI-Files/models/tts/`
- Vérifier les permissions (lecture)

### Erreur "Tokenizer non initialisé"
- Vérifier que `vocab.json` est présent
- Vérifier que `default_speaker_embeddings.json` existe (ou sera généré par fallback)

### Erreur "Inference ONNX"
- Vérifier les noms d'inputs/outputs dans `runInference()`
- Peut nécessiter ajustement selon conversion `optimum-cli`

---

## ✅ STATUT

- ✅ Fichiers ONNX transférés
- ⏳ Test serveur TTS (à faire)
- ⏳ Test synthèse vocale (à faire)

**Prêt pour tests !** 🚀


