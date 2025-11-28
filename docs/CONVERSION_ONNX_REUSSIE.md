# ✅ Conversion ONNX Réussie - SpeechT5

**Date**: 2025-11-27  
**Statut**: ✅ **CONVERSION RÉUSSIE** - Modèles ONNX générés

---

## 🎯 RÉSULTAT CONVERSION

**Script**: `E:\ChatAI-Models\tts\convert_to_onnx.py`  
**Répertoire sortie**: `E:\ChatAI-Models\tts\onnx\`

### Fichiers ONNX générés

| Fichier | Taille | Description |
|---------|--------|-------------|
| `encoder_model.onnx` | 326.90 MB | Encoder (texte → embeddings) |
| `decoder_model.onnx` | 227.40 MB | Decoder (embeddings → mel spectrogram) |
| `decoder_with_past_model.onnx` | 200.34 MB | Decoder avec cache (optimisation) |
| `decoder_postnet_and_vocoder.onnx` | 52.86 MB | Postnet + Vocoder (mel → waveform) |
| **TOTAL** | **~807 MB** | 4 modèles ONNX |

### Fichiers de configuration

- `vocab.json` (vocabulaire tokenizer)
- `preprocessor_config.json` (configuration preprocessing)
- `tokenizer_config.json` (configuration tokenizer)
- `spm_char.model` (modèle SentencePiece)
- `config.json` (configuration modèle)

---

## 📋 ARCHITECTURE SpeechT5 ONNX

**Pipeline 3 étapes**:

```
Texte → [Encoder] → Embeddings → [Decoder] → Mel Spectrogram → [Vocoder] → Waveform Audio
```

1. **Encoder**: Tokenise texte et génère embeddings
2. **Decoder**: Génère mel spectrogram depuis embeddings
3. **Vocoder**: Convertit mel spectrogram en waveform audio

---

## ✅ MODIFICATIONS CODE

### 1. OnnxTTSManager.kt modifié ✅

**Changements**:
- ✅ Support multi-modèles (encoder, decoder, vocoder)
- ✅ Pipeline 3 étapes dans `runInference()`
- ✅ Chargement des 3 sessions ONNX dans `initialize()`

**Chemins modèles**:
```kotlin
ENCODER_MODEL = "/storage/emulated/0/ChatAI-Files/models/tts/encoder_model.onnx"
DECODER_MODEL = "/storage/emulated/0/ChatAI-Files/models/tts/decoder_model.onnx"
VOCODER_MODEL = "/storage/emulated/0/ChatAI-Files/models/tts/decoder_postnet_and_vocoder.onnx"
```

---

### 2. Script de transfert créé ✅

**Fichier**: `E:\ChatAI-Models\tts\transfer_onnx_to_device.ps1`

**Fonctionnalités**:
- Transfère tous les fichiers ONNX vers device
- Transfère fichiers de configuration (vocab, tokenizer, etc.)
- Vérifie présence device Android
- Affiche résumé (fichiers transférés, taille totale)

---

## 🚀 PROCHAINES ÉTAPES

### 1. Transférer fichiers vers device

```powershell
cd E:\ChatAI-Models\tts
.\transfer_onnx_to_device.ps1
```

**Fichiers transférés**:
- `encoder_model.onnx` (326.90 MB)
- `decoder_model.onnx` (227.40 MB)
- `decoder_postnet_and_vocoder.onnx` (52.86 MB)
- `vocab.json`
- `preprocessor_config.json`
- `tokenizer_config.json`
- `spm_char.model`

**Total**: ~607 MB (sans `decoder_with_past_model.onnx` qui est optionnel)

---

### 2. Tester ONNX TTS

**Vérifier logs**:
```bash
adb logcat | Select-String "OnnxTTSManager"
```

**Logs attendus**:
```
OnnxTTSManager: Initialisation ONNX TTS (SpeechT5 multi-modèles)...
OnnxTTSManager: Chargement encoder_model.onnx...
OnnxTTSManager: Chargement decoder_model.onnx...
OnnxTTSManager: Chargement decoder_postnet_and_vocoder.onnx...
OnnxTTSManager: ✅ ONNX TTS prêt (3 modèles, ~607 MB total)
```

---

### 3. Tester synthèse vocale

Le serveur TTS natif (`TTSServer`) utilisera automatiquement `OnnxTTSManager` si les modèles sont présents.

**Test HTTP**:
```bash
curl -X POST http://127.0.0.1:11401/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text":"Bonjour, ceci est un test"}' \
  --output test.wav
```

---

## ⚠️ NOTES IMPORTANTES

### Pipeline ONNX

Le pipeline `runInference()` doit être adapté selon les inputs/outputs réels des modèles ONNX. Les noms d'inputs (`encoder_outputs`, `mel_spectrogram`) peuvent varier selon la conversion.

**Vérifier**:
- Inputs encoder: `input_ids`, `speaker_embeddings`
- Outputs encoder: `encoder_outputs` ou `last_hidden_state`
- Inputs decoder: `encoder_outputs`, `past_key_values` (optionnel)
- Outputs decoder: `mel_spectrogram` ou `logits`
- Inputs vocoder: `mel_spectrogram`
- Outputs vocoder: `waveform`

---

## ✅ STATUT

- ✅ Conversion ONNX réussie
- ✅ Script de transfert créé
- ✅ Code adapté pour multi-modèles
- ⏳ Transfert vers device (à faire)
- ⏳ Tests synthèse vocale (à faire)

**Prêt pour transfert et tests !** 🚀


