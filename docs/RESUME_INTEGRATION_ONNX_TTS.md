# ✅ Résumé Intégration ONNX TTS

**Date**: 2025-11-27  
**Statut**: 🟡 **EN COURS** - Structure créée, conversion modèle nécessaire

---

## ✅ FAIT

### 1. ONNX Runtime Android ajouté ✅

**Fichier**: `app/build.gradle` (ligne 103-104)

```gradle
// ONNX Runtime Android (pour TTS, Embeddings, Vision, etc.)
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.16.0'
```

**Taille APK**: +~15-20 MB

---

### 2. OnnxTTSManager.kt créé ✅

**Fichier**: `app/src/main/java/com/chatai/managers/OnnxTTSManager.kt`

**Fonctionnalités**:
- ✅ Structure de base complète
- ✅ Initialisation ONNX Runtime
- ✅ Chargement modèle depuis device
- ✅ Interface TTSListener (compatible KittTTSManager)
- ⚠️ **TODO**: Preprocessing texte (tokenisation)
- ⚠️ **TODO**: Inference ONNX (génération waveform)
- ⚠️ **TODO**: Postprocessing audio (waveform → PCM)

**Statut**: Structure prête, implémentation à compléter

---

### 3. KittTTSManager.kt modifié ✅

**Fichier**: `app/src/main/java/com/chatai/managers/KittTTSManager.kt`

**Modifications**:
- ✅ Ajout `OnnxTTSManager` comme membre
- ✅ Initialisation ONNX en priorité dans `initialize()`
- ✅ Fallback Android TTS si ONNX indisponible
- ✅ `speak()` utilise ONNX en priorité
- ✅ `stop()` et `shutdown()` gèrent les deux managers

**Architecture**:
```
KittTTSManager
    ├── OnnxTTSManager (priorité)
    │   └── Si disponible → Utilise ONNX
    └── TextToSpeech Android (fallback)
        └── Si ONNX indisponible → Utilise Android TTS
```

---

### 4. Script de conversion créé ✅

**Fichier**: `scripts/convert_tts_to_onnx.py`

**Fonctionnalités**:
- Vérification dépendances Python
- Conversion SpeechT5 PyTorch → ONNX via `optimum-cli`
- Génération fichiers ONNX dans `E:/ChatAI-Models/tts/onnx/`

**Utilisation**:
```bash
python scripts/convert_tts_to_onnx.py
```

---

## ⚠️ À FAIRE

### 1. Convertir modèle PyTorch → ONNX

**Prérequis**:
```bash
pip install optimum[onnxruntime] transformers torch
```

**Commande**:
```bash
optimum-cli export onnx --model microsoft/speecht5_tts output_dir --model-kwargs '{"vocoder": "microsoft/speecht5_hifigan"}'
```

**Résultat attendu**: Fichier `model.onnx` (~80-150 MB)

---

### 2. Transférer modèle ONNX vers device

```bash
adb push E:/ChatAI-Models/tts/onnx/model.onnx /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx
```

---

### 3. Implémenter preprocessing texte dans OnnxTTSManager

**Nécessaire**:
- Tokenisation texte (utiliser tokenizer SpeechT5)
- Encodage en IDs (vocabulaire)
- Préparation inputs ONNX (text_ids, speaker_embeddings)

**Ressources**:
- Tokenizer SpeechT5: `transformers.AutoTokenizer.from_pretrained("microsoft/speecht5_tts")`
- Documentation: https://huggingface.co/docs/transformers/model_doc/speecht5

---

### 4. Implémenter inference ONNX

**Nécessaire**:
- Préparer inputs (FloatArray/IntArray)
- Exécuter `ortSession.run()`
- Récupérer outputs (waveform FloatArray)

**Exemple**:
```kotlin
val inputs = mapOf(
    "input_ids" to textIdsTensor,
    "speaker_embeddings" to speakerEmbeddingsTensor
)
val outputs = ortSession.run(inputs)
val waveform = outputs[0].value as FloatArray
```

---

### 5. Implémenter postprocessing audio

**Nécessaire**:
- Convertir waveform float32 → int16 PCM
- Créer AudioTrack (16kHz, mono, PCM_16BIT)
- Jouer audio

**Exemple**:
```kotlin
val pcmData = waveform.map { (it * 32767).toInt().toShort() }.toShortArray()
val audioTrack = AudioTrack(...)
audioTrack.write(pcmData, 0, pcmData.size)
audioTrack.play()
```

---

## 📊 STATUT GLOBAL

| Composant | Statut | Progression |
|-----------|--------|-------------|
| ONNX Runtime | ✅ **FAIT** | 100% |
| OnnxTTSManager (structure) | ✅ **FAIT** | 100% |
| KittTTSManager (intégration) | ✅ **FAIT** | 100% |
| Script conversion | ✅ **FAIT** | 100% |
| Modèle ONNX | ❌ **À FAIRE** | 0% |
| Preprocessing texte | ❌ **À FAIRE** | 0% |
| Inference ONNX | ❌ **À FAIRE** | 0% |
| Postprocessing audio | ❌ **À FAIRE** | 0% |

**Progression globale**: ~40% (structure complète, implémentation à faire)

---

## 🎯 PROCHAINES ÉTAPES

1. **Convertir modèle ONNX** (1-2h)
   - Installer dépendances Python
   - Exécuter script de conversion
   - Vérifier fichiers générés

2. **Transférer vers device** (5 min)
   - `adb push` modèle ONNX

3. **Implémenter preprocessing** (2-3h)
   - Intégrer tokenizer SpeechT5
   - Préparer inputs ONNX

4. **Implémenter inference** (1-2h)
   - Exécuter session ONNX
   - Récupérer waveform

5. **Implémenter postprocessing** (1-2h)
   - Conversion float32 → int16
   - AudioTrack pour lecture

6. **Tests** (1-2h)
   - Test basique
   - Test performance
   - Test fallback

**Effort total restant**: ~1-2 jours

---

## 📝 NOTES

- La structure est complète et prête
- Le fallback Android TTS garantit la compatibilité
- L'implémentation ONNX peut être faite progressivement
- Le modèle PyTorch existe déjà (558 MB), conversion nécessaire

**Recommandation**: Commencer par la conversion ONNX, puis implémenter progressivement preprocessing → inference → postprocessing.


