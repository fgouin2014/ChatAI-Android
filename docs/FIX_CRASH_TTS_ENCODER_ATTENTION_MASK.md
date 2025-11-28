# Fix Crash TTS - encoder_attention_mask manquant

**Date**: 2025-01-27  
**Problème**: Crash lors du test TTS avec erreur `Missing Input: encoder_attention_mask`

---

## Problème Identifié

### Erreur ONNX Runtime

```
2025-11-28 00:53:43.177 onnxruntime E [E:onnxruntime:, sequential_executor.cc:514 ExecuteKernel] 
Non-zero status code returned while running Shape node. 
Name:'/wrapped_decoder/Shape_2' 
Status Message: Missing Input: encoder_attention_mask
```

### Stack Trace

```
ai.onnxruntime.OrtException: Error code - ORT_RUNTIME_EXCEPTION - message: 
Non-zero status code returned while running Shape node. 
Name:'/wrapped_decoder/Shape_2' 
Status Message: Missing Input: encoder_attention_mask

at com.chatai.managers.OnnxTTSManager.runInference(OnnxTTSManager.kt:415)
at com.chatai.managers.OnnxTTSManager.synthesizeToWav(OnnxTTSManager.kt:681)
at com.chatai.TTSServer.synthesizeAndSend(TTSServer.java:261)
```

### Cause

Le decoder ONNX attend un input `encoder_attention_mask` qui n'était pas fourni dans les inputs du decoder. Ce masque indique quels tokens de la séquence encoder sont valides (non padding).

---

## Solution Appliquée

### Fichier Modifié

`app/src/main/java/com/chatai/managers/OnnxTTSManager.kt`

### Changements

1. **Création de `encoder_attention_mask`** dans `runInference()`:
   - Shape: `[1, sequence_length]`
   - Type: `LongArray` avec des `1L` pour tous les tokens valides
   - Créé à partir de la longueur de `input_ids`

2. **Ajout aux inputs du decoder**:
   ```kotlin
   decoderInputs["encoder_attention_mask"] = encoderAttentionMaskTensor
   ```

3. **Nettoyage du tensor** dans tous les cas (succès et erreurs):
   - Ajout de `encoderAttentionMaskTensor.close()` dans tous les blocs de nettoyage

### Code Ajouté

```kotlin
// ⭐ NOUVEAU: Créer encoder_attention_mask (requis par le decoder)
// encoder_attention_mask: shape [1, sequence_length] avec des 1s pour tous les tokens valides
val inputIdsShape = inputIds.shape
val sequenceLength = if (inputIdsShape.size >= 2) inputIdsShape[1].toInt() else {
    Log.e(TAG, "Impossible de déterminer sequence_length")
    // ... cleanup ...
    return null
}

Log.d(TAG, "Création encoder_attention_mask: sequence_length=$sequenceLength")

// Créer un masque de 1s pour tous les tokens (tous valides)
val attentionMaskData = LongArray(sequenceLength) { 1L }
val attentionMaskArray = arrayOf(attentionMaskData)
val encoderAttentionMaskTensor = OnnxTensor.createTensor(ortEnv, attentionMaskArray)

// Ajouter aux inputs du decoder
decoderInputs["encoder_attention_mask"] = encoderAttentionMaskTensor
```

---

## Tests à Effectuer

1. **Test depuis interface web**:
   - Ouvrir onglet TTS
   - Cliquer "Tester TTS"
   - Vérifier que la synthèse fonctionne sans crash

2. **Vérifier logs**:
   ```bash
   adb logcat | Select-String "encoder_attention_mask|Decoder réussi"
   ```
   Attendu: `Création encoder_attention_mask: sequence_length=X` et `✅ Decoder réussi`

3. **Vérifier que l'audio est généré**:
   - L'audio doit être joué dans l'interface web
   - Pas de crash de l'application

---

## Notes Techniques

### encoder_attention_mask

- **Purpose**: Indique quels tokens de la séquence encoder sont valides (non padding)
- **Shape**: `[batch_size, sequence_length]` = `[1, sequence_length]`
- **Type**: `int64` (LongArray en Kotlin)
- **Valeurs**: `1` pour tokens valides, `0` pour padding
- **Dans notre cas**: Tous les tokens sont valides (pas de padding), donc tous à `1`

### Architecture SpeechT5

Le pipeline SpeechT5 nécessite:
1. **Encoder**: `input_ids` → `encoder_hidden_states`
2. **Decoder**: `encoder_hidden_states` + `speaker_embeddings` + `output_sequence` + **`encoder_attention_mask`** → `mel_spectrogram`
3. **Vocoder**: `mel_spectrogram` → `waveform`

Le `encoder_attention_mask` était manquant à l'étape 2, causant le crash.

---

## Références

- Erreur ONNX Runtime: `Missing Input: encoder_attention_mask`
- Fichier: `OnnxTTSManager.kt` ligne ~410-433
- Commit: Fix ajout encoder_attention_mask pour decoder ONNX

