# 🚀 Démarrage Rapide - ONNX TTS

**Temps estimé**: 2-3 heures  
**Difficulté**: Moyenne

---

## ✅ PRÉREQUIS

### 1. Python 3.8+ installé

**Vérifier**:
```bash
python --version
```

**Si non installé**: https://www.python.org/downloads/

---

### 2. Dépendances Python

```bash
pip install optimum[onnxruntime] transformers torch onnxruntime
```

**Vérifier**:
```bash
python -c "import optimum, transformers, torch, onnxruntime; print('✅ OK')"
```

---

### 3. Device Android connecté

```bash
adb devices
```

**Doit afficher**: `device` (pas `unauthorized`)

---

## 📋 ÉTAPES

### Étape 1: Convertir Modèle (10-30 min)

```bash
cd ChatAI-Android
python scripts/convert_speecht5_to_onnx_complete.py
```

**Résultat attendu**:
```
E:/ChatAI-Models/tts/onnx/
    ├── model.onnx (~80-150 MB)
    ├── vocab.json (~1-2 MB)
    └── default_speaker_embeddings.json (~10 KB)
```

**⚠️ Note**: Premier téléchargement peut prendre 10-20 minutes (téléchargement modèle Hugging Face)

---

### Étape 2: Transférer vers Device (5 min)

**Option A: Script automatique (Recommandé)**
```powershell
.\scripts\transfer_tts_onnx_to_device.ps1
```

**Option B: Manuel**
```bash
adb push E:/ChatAI-Models/tts/onnx/model.onnx /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx
adb push E:/ChatAI-Models/tts/onnx/vocab.json /storage/emulated/0/ChatAI-Files/models/tts/vocab.json
adb push E:/ChatAI-Models/tts/onnx/default_speaker_embeddings.json /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json
```

**Vérifier**:
```bash
adb shell "ls -lh /storage/emulated/0/ChatAI-Files/models/tts/"
```

---

### Étape 3: Compiler et Installer APK (5 min)

```bash
cd ChatAI-Android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### Étape 4: Tester (5 min)

1. **Démarrer ChatAI** sur le device
2. **Tester TTS** via interface KITT ou webapp
3. **Vérifier logs**:
   ```bash
   adb logcat | Select-String -Pattern "OnnxTTSManager|SimpleTokenizer"
   ```

**Logs attendus**:
```
OnnxTTSManager: ✅ ONNX TTS prêt (modèle: XXX MB)
SimpleTokenizer: Vocabulaire chargé: XXX tokens
OnnxTTSManager: Synthèse vocale ONNX: "test"
OnnxTTSManager: Waveform généré: XXXX échantillons
```

---

## 🔧 DÉPANNAGE RAPIDE

### Erreur: "optimum-cli not found"

**Solution**:
```bash
pip install optimum[onnxruntime]
```

---

### Erreur: "Modèle ONNX non trouvé"

**Vérifier**:
```bash
adb shell "test -f /storage/emulated/0/ChatAI-Files/models/tts/speecht5_onnx.onnx && echo 'EXISTS' || echo 'MISSING'"
```

**Si MISSING**: Retransférer avec script PowerShell

---

### Erreur: "Tokenizer non initialisé"

**Vérifier fichiers**:
```bash
adb shell "ls -lh /storage/emulated/0/ChatAI-Files/models/tts/vocab.json"
adb shell "ls -lh /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json"
```

**Si manquants**: Retransférer

---

### ONNX TTS jamais utilisé (toujours Android TTS)

**Vérifier logs d'initialisation**:
```bash
adb logcat | Select-String -Pattern "OnnxTTSManager.*initialisation|OnnxTTSManager.*prêt"
```

**Causes possibles**:
1. Modèle ONNX non trouvé
2. Tokenizer non initialisé
3. Erreur lors de l'initialisation

**Solution**: Vérifier fichiers et redémarrer app

---

## ✅ CHECKLIST

- [ ] Python 3.8+ installé
- [ ] Dépendances Python installées
- [ ] Device Android connecté
- [ ] Modèle ONNX converti
- [ ] Fichiers transférés vers device
- [ ] APK compilé et installé
- [ ] TTS testé et fonctionnel

---

## 📊 RÉSULTAT ATTENDU

**Avant** (Android TTS):
- Voix robotique
- Latence: ~50ms
- Qualité: ⭐⭐⭐

**Après** (ONNX TTS):
- Voix naturelle
- Latence: ~100-300ms
- Qualité: ⭐⭐⭐⭐⭐

---

## 🎯 PROCHAINES ÉTAPES (Optionnel)

1. **Améliorer tokenisation** (qualité)
2. **Optimiser performance** (latence)
3. **Ajuster qualité audio** (paramètres)

---

## 📚 DOCUMENTATION COMPLÈTE

- `GUIDE_COMPLET_ONNX_TTS.md` - Guide détaillé
- `TOKENIZER_IMPLÉMENTÉ.md` - Documentation tokenizer
- `RÉSUMÉ_FINAL_ONNX_TTS.md` - Résumé complet

---

**Temps total estimé**: 2-3 heures  
**Difficulté**: Moyenne  
**Résultat**: TTS haute qualité 100% offline ! 🚀


