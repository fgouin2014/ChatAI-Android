# 📊 Statut Intégration TTS ONNX

**Date**: 2025-11-27  
**Statut**: ⚠️ **EN COURS** - ONNX Runtime ajouté, modèle à convertir

---

## ✅ FAIT

### 1. ONNX Runtime Android ajouté ✅

**Fichier**: `app/build.gradle` (ligne 103-104)

```gradle
// ONNX Runtime Android (pour TTS, Embeddings, Vision, etc.)
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.16.0'
```

**Taille APK**: +~15-20 MB (bibliothèque native)

**Statut**: ✅ **AJOUTÉ** - Prêt pour utilisation

---

## ⚠️ EN COURS

### 2. Modèle ONNX SpeechT5

**Problème**: Le modèle ONNX pré-converti n'est pas disponible directement sur Hugging Face.

**Options**:

#### Option A: Conversion PyTorch → ONNX (Recommandé)

**Prérequis**:
- Python 3.8+
- `pip install optimum[onnxruntime] transformers torch`

**Commande**:
```bash
optimum-cli export onnx \
  --model microsoft/speecht5_tts \
  output_dir \
  --model-kwargs '{"vocoder": "microsoft/speecht5_hifigan"}'
```

**Résultat**: Fichiers ONNX dans `output_dir/`

**Taille estimée**: 80-150 MB

---

#### Option B: Utiliser modèle PyTorch existant

**Fichier disponible**: `E:\ChatAI-Models\tts\pytorch_model.bin` (558 MB)

**Problème**: Nécessite conversion en ONNX pour exécution native Android.

**Alternative**: Créer serveur Python (comme Whisper) au lieu d'ONNX.

---

#### Option C: Rechercher autre modèle ONNX TTS

**Alternatives possibles**:
- Autres modèles TTS ONNX sur Hugging Face
- Modèles Coqui TTS en ONNX
- Modèles Piper TTS (plus léger, ~20-50 MB)

---

## 📋 PROCHAINES ÉTAPES

### Priorité 1: Convertir PyTorch → ONNX

1. **Installer dépendances Python**:
   ```bash
   pip install optimum[onnxruntime] transformers torch
   ```

2. **Convertir modèle**:
   ```bash
   optimum-cli export onnx --model microsoft/speecht5_tts output_dir
   ```

3. **Télécharger vocoder** (si nécessaire):
   - `microsoft/speecht5_hifigan` (pour qualité audio)

4. **Transférer vers device**:
   ```bash
   adb push output_dir/model.onnx /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx
   ```

---

### Priorité 2: Créer OnnxTTSManager.kt

**Structure de base**:
```kotlin
class OnnxTTSManager(
    private val context: Context,
    private val listener: TTSListener
) {
    private var ortSession: OrtSession? = null
    private var isReady = false
    
    fun initialize() {
        // Charger modèle ONNX
        val modelPath = "/storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx"
        val ortEnv = OrtEnvironment.getEnvironment()
        ortSession = ortEnv.createSession(modelPath)
        isReady = true
    }
    
    fun speak(text: String) {
        // Preprocessing texte
        // Inference ONNX
        // Postprocessing audio
        // Jouer audio
    }
}
```

---

## 🎯 RÉSUMÉ

| Étape | Statut | Action requise |
|-------|--------|----------------|
| ONNX Runtime dans build.gradle | ✅ **FAIT** | Aucune |
| Modèle ONNX téléchargé | ❌ **MANQUANT** | Conversion PyTorch → ONNX |
| Modèle ONNX transféré | ❌ **PAS FAIT** | Attendre conversion |
| OnnxTTSManager.kt créé | ❌ **PAS FAIT** | Créer structure de base |
| Intégration dans KittTTSManager | ❌ **PAS FAIT** | Attendre OnnxTTSManager |

---

## 💡 RECOMMANDATION

**Option rapide**: Créer serveur Python TTS (comme Whisper) au lieu d'ONNX.

**Avantages**:
- ✅ Utilise modèle PyTorch existant (558 MB déjà téléchargé)
- ✅ Architecture connue (similaire à Whisper)
- ✅ Pas de conversion nécessaire

**Inconvénients**:
- ❌ Nécessite serveur Python (Termux)
- ❌ Latence HTTP (50-200ms vs 10-50ms ONNX)

**Option ONNX**: Meilleure performance mais nécessite conversion.

---

## 📝 NOTES

- Le modèle PyTorch est déjà sur le device (558 MB)
- ONNX Runtime est maintenant dans le projet
- Conversion ONNX peut être faite sur PC puis transférée
- Alternative: Serveur Python (plus rapide à implémenter)


