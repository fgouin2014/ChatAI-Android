# 🔧 FIX: Statuts Modèles ONNX TTS + Diagnostic Inférence

**Date:** 2025-11-27  
**Problème:** Les statuts Encoder/Decoder/Vocoder ne s'affichaient pas dans l'UI, et la synthèse échouait avec "No audio generated"

---

## ✅ CORRECTIONS APPLIQUÉES

### 1. **Endpoint `/models` ajouté dans `TTSServer.java`**

**Fichier:** `ChatAI-Android/app/src/main/java/com/chatai/TTSServer.java`

**Changements:**
- ✅ Nouvel endpoint `GET /models` qui retourne les statuts individuels:
  ```json
  {
    "encoder": true/false,
    "decoder": true/false,
    "vocoder": true/false,
    "tokenizer": true/false,
    "all_ready": true/false
  }
  ```

**Code ajouté:**
```java
private void sendModelsStatus(OutputStream outputStream) throws IOException {
    JSONObject modelsJson = new JSONObject();
    if (onnxTTSManager != null) {
        modelsJson.put("encoder", onnxTTSManager.isEncoderReady());
        modelsJson.put("decoder", onnxTTSManager.isDecoderReady());
        modelsJson.put("vocoder", onnxTTSManager.isVocoderReady());
        modelsJson.put("tokenizer", onnxTTSManager.isTokenizerReady());
        modelsJson.put("all_ready", onnxTTSManager.isONNXReady());
    }
    // ... envoi JSON
}
```

---

### 2. **Méthodes de statut ajoutées dans `OnnxTTSManager.kt`**

**Fichier:** `ChatAI-Android/app/src/main/java/com/chatai/managers/OnnxTTSManager.kt`

**Changements:**
- ✅ Méthodes publiques pour vérifier chaque composant individuellement:
  ```kotlin
  fun isEncoderReady(): Boolean = encoderSession != null
  fun isDecoderReady(): Boolean = decoderSession != null
  fun isVocoderReady(): Boolean = vocoderSession != null
  fun isTokenizerReady(): Boolean = tokenizer.isInitialized()
  ```

---

### 3. **UI JavaScript mise à jour**

**Fichier:** `ChatAI-Android/app/src/main/assets/webapp/index.html`

**Changements:**
- ✅ Nouvelle fonction `updateOnnxModelsStatus()` qui appelle `/models` et met à jour l'UI
- ✅ Nouvelle fonction `updateOnnxModelsStatusError()` pour gérer les erreurs
- ✅ `checkTtsServerStatus()` et `testTtsServerConnection()` appellent maintenant `updateOnnxModelsStatus()`

**Fonctionnalités:**
- Affiche "✅ Chargé" ou "❌ Non chargé" pour chaque modèle
- Couleurs: Vert (✅), Rouge (❌), Orange (❓ Erreur)
- Mise à jour automatique après test de connexion

---

### 4. **Logs détaillés ajoutés pour diagnostic inférence**

**Fichier:** `ChatAI-Android/app/src/main/java/com/chatai/managers/OnnxTTSManager.kt`

**Changements:**
- ✅ Logs détaillés à chaque étape de l'inférence:
  - Inputs encoder (noms des tensors)
  - Outputs encoder (nombre, shapes)
  - Tentatives decoder avec différents noms d'inputs
  - Tentatives vocoder avec différents noms d'inputs
  - Shapes de tous les tensors intermédiaires

**Logs ajoutés:**
```kotlin
Log.d(TAG, "Inputs encoder: ${inputs.keys.joinToString()}")
Log.d(TAG, "Outputs encoder: ${encoderOutputs.size} tensors")
Log.d(TAG, "Encoder output shape: ${encoderShape.contentToString()}")
Log.d(TAG, "Tentative decoder avec 'encoder_outputs'")
Log.d(TAG, "Mel spectrogram shape: ${melShape.contentToString()}")
Log.d(TAG, "Waveform shape: ${waveformShape.contentToString()}")
```

**Gestion d'erreurs améliorée:**
- Essai avec "encoder_outputs" puis "last_hidden_state" si échec
- Messages d'erreur détaillés avec stack traces
- Libération correcte des ressources en cas d'erreur

---

## 🔍 DIAGNOSTIC EN COURS

### Problème identifié: "No audio generated"

**Symptômes:**
- Health check: ✅ Serveur disponible, Modèle chargé: OUI
- Statuts modèles: Encoder/Decoder/Vocoder ne s'affichaient pas (maintenant corrigé)
- Test synthèse: ❌ HTTP 500 "TTS synthesis failed: No audio generated"

**Causes possibles:**
1. **Noms d'inputs incorrects** pour decoder/vocoder
   - Le decoder attend peut-être "last_hidden_state" au lieu de "encoder_outputs"
   - Le vocoder attend peut-être un nom différent de "mel_spectrogram"

2. **Shapes incompatibles** entre les outputs encoder et inputs decoder
   - Le decoder attend peut-être un shape différent

3. **Past key values manquants** pour le decoder
   - Le decoder SpeechT5 peut nécessiter `past_key_values` pour la génération séquentielle

**Prochaines étapes:**
1. Vérifier les logs détaillés après cette correction
2. Identifier le nom exact des inputs attendus par le decoder
3. Corriger les inputs selon les logs

---

## 📋 TEST

**Pour tester les statuts des modèles:**
1. Ouvrir l'onglet Configuration → TTS
2. Cliquer sur "🔍 Tester connexion serveur TTS"
3. Vérifier que les statuts Encoder/Decoder/Vocoder s'affichent maintenant

**Pour diagnostiquer l'inférence:**
```bash
adb logcat | Select-String "OnnxTTSManager"
```

**Logs attendus:**
```
OnnxTTSManager: Exécution inference ONNX (pipeline 3 étapes)...
OnnxTTSManager: Inputs encoder: input_ids, speaker_embeddings
OnnxTTSManager: Outputs encoder: 1 tensors
OnnxTTSManager: Encoder output shape: [...]
OnnxTTSManager: Tentative decoder avec 'encoder_outputs'
OnnxTTSManager: ❌ Erreur decoder avec 'encoder_outputs': [message d'erreur]
```

---

## ✅ RÉSULTAT ATTENDU

Après ces corrections:
1. ✅ Les statuts Encoder/Decoder/Vocoder s'affichent dans l'UI
2. ✅ Les logs détaillés permettent d'identifier l'erreur exacte
3. ⏳ Correction de l'inférence selon les logs (en cours)

---

**Document créé le:** 2025-11-27  
**Dernière mise à jour:** 2025-11-27


