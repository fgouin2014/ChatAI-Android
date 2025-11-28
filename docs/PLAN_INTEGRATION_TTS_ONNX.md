# 🎯 Plan d'Intégration TTS ONNX (SpeechT5)

**Date**: 2025-11-27  
**Objectif**: Intégrer SpeechT5 TTS via ONNX Runtime Android (exécution native)

---

## 📋 VUE D'ENSEMBLE

### Situation actuelle
- ✅ Modèle PyTorch téléchargé: `E:\ChatAI-Models\tts\pytorch_model.bin` (558 MB)
- ✅ Modèle transféré sur device: `/storage/emulated/0/ChatAI-Files/models/tts/pytorch_model.bin` (558 MB)
- ❌ **Intégration**: L'app utilise `TextToSpeech` Android natif (`KittTTSManager.kt`)

### Objectif
- ✅ Intégrer ONNX Runtime Android
- ✅ Convertir ou télécharger SpeechT5 en ONNX (~80-150 MB)
- ✅ Créer `OnnxTTSManager.kt` pour remplacer/complémenter `KittTTSManager.kt`
- ✅ Fallback: ONNX TTS → Android TTS natif

---

## 🔧 ÉTAPES D'IMPLÉMENTATION

### Phase 1: Préparation ONNX Runtime (30 min)

#### 1.1 Ajouter ONNX Runtime dans `build.gradle`

**Fichier**: `app/build.gradle`

```gradle
dependencies {
    // ... autres dépendances ...
    
    // ONNX Runtime Android (pour TTS, Embeddings, etc.)
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.16.0'
}
```

**Taille APK**: +~15-20 MB (bibliothèque native)

---

#### 1.2 Vérifier les modèles ONNX disponibles

**Option A: Modèle pré-converti (Recommandé)**
- `NeuML/txtai-speecht5-onnx` (Hugging Face)
- Taille: ~80-150 MB
- Format: ONNX prêt à l'emploi

**Option B: Conversion PyTorch → ONNX**
- Utiliser `optimum-cli` pour convertir `microsoft/speecht5_tts`
- Nécessite Python + `optimum[onnxruntime]`
- Commande:
  ```bash
  optimum-cli export onnx --model microsoft/speecht5_tts output_dir --model-kwargs '{"vocoder": "microsoft/speecht5_hifigan"}'
  ```

**Recommandation**: Utiliser Option A (plus rapide, déjà optimisé)

---

### Phase 2: Télécharger/Convertir Modèle ONNX (1-2h)

#### 2.1 Télécharger modèle ONNX pré-converti

**Source**: Hugging Face `NeuML/txtai-speecht5-onnx`

**Script PowerShell** (`scripts/download_tts_onnx.ps1`):
```powershell
$modelUrl = "https://huggingface.co/NeuML/txtai-speecht5-onnx/resolve/main/model.onnx"
$outputPath = "E:\ChatAI-Models\tts\speecht5_onnx.onnx"

# Télécharger
Invoke-WebRequest -Uri $modelUrl -OutFile $outputPath

Write-Host "Modèle ONNX téléchargé: $outputPath"
```

**Taille estimée**: 80-150 MB

---

#### 2.2 Transférer vers device

```bash
adb push E:\ChatAI-Models\tts\speecht5_onnx.onnx /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx
```

---

### Phase 3: Créer OnnxTTSManager (2-3h)

#### 3.1 Structure du service

**Fichier**: `app/src/main/java/com/chatai/managers/OnnxTTSManager.kt`

**Responsabilités**:
1. Charger le modèle ONNX depuis le device
2. Initialiser ONNX Runtime
3. Convertir texte → audio (via SpeechT5)
4. Gérer les callbacks (onStart, onDone, onError)
5. Fallback vers Android TTS si ONNX indisponible

**Architecture**:
```kotlin
class OnnxTTSManager(
    private val context: Context,
    private val listener: TTSListener
) {
    private var ortSession: OrtSession? = null
    private var isONNXReady = false
    
    fun initialize() {
        // Charger modèle ONNX
        // Initialiser ONNX Runtime
    }
    
    fun speak(text: String, utteranceId: String) {
        // Convertir texte → audio via ONNX
        // Jouer audio
    }
    
    fun shutdown() {
        // Libérer ressources ONNX
    }
}
```

---

#### 3.2 Préprocessing texte

**SpeechT5 nécessite**:
1. Tokenisation du texte
2. Encodage en IDs (vocabulaire)
3. Génération embeddings speaker (optionnel)

**Exemple**:
```kotlin
private fun preprocessText(text: String): FloatArray {
    // Tokeniser et encoder le texte
    // Retourner array de floats pour ONNX
}
```

---

#### 3.3 Postprocessing audio

**SpeechT5 génère**:
- Audio waveform (array de floats)
- Format: 16kHz, mono, float32

**Conversion nécessaire**:
```kotlin
private fun convertToPCM(audioWaveform: FloatArray): ByteArray {
    // Convertir float32 → int16 PCM
    // Retourner ByteArray pour AudioTrack
}
```

---

### Phase 4: Intégration dans KittTTSManager (1-2h)

#### 4.1 Modifier KittTTSManager pour utiliser ONNX

**Option A: Remplacer complètement**
- Supprimer `TextToSpeech`
- Utiliser uniquement `OnnxTTSManager`

**Option B: Fallback hybride (Recommandé)**
- Essayer ONNX en premier
- Fallback vers `TextToSpeech` si ONNX indisponible

**Code**:
```kotlin
class KittTTSManager(...) {
    private val onnxTTS = OnnxTTSManager(context, listener)
    private val androidTTS = TextToSpeech(context, this)
    
    fun speak(text: String) {
        if (onnxTTS.isReady) {
            onnxTTS.speak(text)
        } else {
            androidTTS.speak(text, ...)
        }
    }
}
```

---

### Phase 5: Configuration et Tests (1-2h)

#### 5.1 Ajouter configuration dans `ai_config.json`

```json
{
  "tts": {
    "engine": "onnx",  // "onnx" ou "android"
    "modelPath": "/storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx",
    "fallbackToAndroid": true
  }
}
```

---

#### 5.2 Tests

1. **Test basique**: "Bonjour, ceci est un test"
2. **Test long**: Texte de 100+ mots
3. **Test multilingue**: Français, anglais
4. **Test fallback**: Désactiver ONNX, vérifier Android TTS
5. **Test performance**: Latence, qualité audio

---

## 📊 COMPARAISON

| Aspect | Android TTS (actuel) | ONNX SpeechT5 |
|--------|---------------------|---------------|
| **Qualité** | ⭐⭐⭐ Robotique | ⭐⭐⭐⭐⭐ Naturelle |
| **Latence** | ~50ms | ~100-200ms |
| **Taille** | 0 MB (système) | ~80-150 MB |
| **Offline** | ✅ Oui | ✅ Oui |
| **Multilingue** | ⚠️ Limité | ✅ Excellent |
| **Personnalisation** | ❌ Non | ✅ Oui (voix) |

---

## 🎯 AVANTAGES ONNX

1. ✅ **Exécution native**: Pas de serveur HTTP nécessaire
2. ✅ **Latence acceptable**: 100-200ms (vs 50ms Android TTS)
3. ✅ **Qualité supérieure**: Voix naturelle vs robotique
4. ✅ **Offline complet**: Fonctionne sans Internet
5. ✅ **Personnalisation**: Possibilité de fine-tuner la voix

---

## ⚠️ DÉFIS

1. **Complexité**: Préprocessing/postprocessing audio
2. **Taille modèle**: 80-150 MB sur device
3. **RAM**: ~200-300 MB pendant exécution
4. **Vocoder**: Nécessite `speecht5_hifigan` (inclus dans ONNX)

---

## 📝 CHECKLIST

- [ ] Phase 1: Ajouter ONNX Runtime dans `build.gradle`
- [ ] Phase 2: Télécharger modèle ONNX SpeechT5
- [ ] Phase 2: Transférer modèle vers device
- [ ] Phase 3: Créer `OnnxTTSManager.kt`
- [ ] Phase 3: Implémenter preprocessing texte
- [ ] Phase 3: Implémenter postprocessing audio
- [ ] Phase 4: Intégrer dans `KittTTSManager.kt` (fallback)
- [ ] Phase 5: Ajouter configuration `ai_config.json`
- [ ] Phase 5: Tests basiques
- [ ] Phase 5: Tests performance
- [ ] Phase 5: Documentation

---

## 🚀 PROCHAINES ÉTAPES

1. **Maintenant**: Ajouter ONNX Runtime dans `build.gradle`
2. **Ensuite**: Télécharger modèle ONNX pré-converti
3. **Puis**: Créer `OnnxTTSManager.kt` (structure de base)
4. **Enfin**: Tests et intégration

**Effort total estimé**: 1-2 jours

---

## 📚 RESSOURCES

- **ONNX Runtime Android**: https://onnxruntime.ai/docs/tutorials/mobile/
- **SpeechT5 ONNX**: https://huggingface.co/NeuML/txtai-speecht5-onnx
- **Optimum Export**: https://huggingface.co/docs/optimum/onnxruntime/overview
- **SpeechT5 Documentation**: https://huggingface.co/docs/transformers/model_doc/speecht5


