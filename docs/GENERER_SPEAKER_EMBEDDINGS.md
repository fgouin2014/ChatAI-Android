# 🔧 GÉNÉRER default_speaker_embeddings.json

**Date:** 2025-11-27  
**Problème:** Fichier `default_speaker_embeddings.json` manquant

---

## ⚠️ PROBLÈME

Le fichier `default_speaker_embeddings.json` est **requis** pour le TTS ONNX mais est manquant sur le device.

**Chemin requis:** `/storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json`

**Impact:** Le TTS ONNX ne peut pas fonctionner sans ce fichier.

---

## ✅ SOLUTION: Script de génération

### **Script créé:** `ChatAI-Android/scripts/generate_speaker_embeddings.py`

**Fonctionnalités:**
- ✅ Télécharge le modèle SpeechT5 depuis Hugging Face
- ✅ Génère les speaker embeddings par défaut (512 dimensions)
- ✅ Sauvegarde dans un fichier JSON
- ✅ Affiche la commande ADB pour transférer vers le device

---

## 🚀 UTILISATION

### **Étape 1: Vérifier dépendances**

```bash
python -c "import torch; import transformers; print('OK')"
```

Si erreur:
```bash
pip install torch transformers
```

### **Étape 2: Exécuter le script**

```bash
cd ChatAI-Android/scripts
python generate_speaker_embeddings.py
```

**Ou avec chemin personnalisé:**
```bash
python generate_speaker_embeddings.py "E:/ChatAI-Models/tts/default_speaker_embeddings.json"
```

### **Étape 3: Transférer vers device**

Le script affichera la commande ADB. Sinon:

```bash
adb push "E:/ChatAI-Models/tts/default_speaker_embeddings.json" /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json
```

---

## 📋 CONTENU DU FICHIER

**Format JSON:**
```json
{
  "embeddings": [0.123, -0.456, 0.789, ...],  // 512 floats
  "dimensions": 512,
  "description": "Speaker embeddings par défaut (microsoft/speecht5_tts)",
  "speaker_id": 0,
  "model": "microsoft/speecht5_tts"
}
```

**Taille:** ~3-5 KB (512 floats × 8 bytes + JSON overhead)

---

## 🔍 VÉRIFICATION

### **Vérifier présence sur device:**

```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json
```

**Résultat attendu:**
```
-rw-rw---- 1 u0_aXXX sdcard_rw  3.5K default_speaker_embeddings.json
```

### **Vérifier logs d'initialisation:**

```bash
adb logcat | Select-String "SimpleTokenizer"
```

**Logs attendus (succès):**
```
SimpleTokenizer: Speaker embeddings chargés: 512 dimensions
```

**Logs d'erreur (fichier manquant):**
```
SimpleTokenizer: Speaker embeddings non trouvés, utilisation valeurs par défaut
```

---

## ⚠️ NOTE IMPORTANTE

**Fallback actuel:**
Si le fichier est absent, `SimpleTokenizer` génère des embeddings de zéros (ligne 73):
```kotlin
defaultSpeakerEmbeddings = FloatArray(512) { 0.0f }
```

**Impact:**
- ⚠️ La voix sera probablement dégradée ou inaudible
- ⚠️ Le TTS peut échouer complètement

**Solution:** Toujours générer et transférer le fichier correct.

---

## 🎯 ALTERNATIVE: Génération manuelle

Si le script Python ne fonctionne pas, vous pouvez créer un fichier minimal:

**Fichier:** `default_speaker_embeddings.json`
```json
{
  "embeddings": [0.0, 0.0, 0.0, ...],
  "dimensions": 512,
  "description": "Speaker embeddings par défaut (généré manuellement)"
}
```

**⚠️ ATTENTION:** Les embeddings de zéros donneront une voix de mauvaise qualité. Utilisez le script Python pour obtenir de vrais embeddings.

---

## 📊 STATUT

| Étape | Statut | Détails |
|-------|--------|---------|
| Script créé | ✅ | `generate_speaker_embeddings.py` |
| Génération | ⏳ | À exécuter |
| Transfert device | ⏳ | À faire après génération |
| Vérification | ⏳ | À faire après transfert |

---

**Document créé le:** 2025-11-27  
**Dernière mise à jour:** 2025-11-27


