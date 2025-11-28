# 🔍 Diagnostic TTS Error 500 - Guide de Résolution

**Date**: 2025-11-27  
**Problème**: Serveur TTS Error 500 - "status server : indisponible. error 500."

---

## 🔍 DIAGNOSTIC

### Symptômes
- Serveur TTS démarre sur port 11401
- Health check retourne error 500
- Statut affiche "indisponible"
- Synthèse vocale ne fonctionne pas

### Causes Possibles

#### 1. Modèles ONNX Manquants ❌
**Chemins requis**:
- `/storage/emulated/0/ChatAI-Files/models/tts/encoder_model.onnx`
- `/storage/emulated/0/ChatAI-Files/models/tts/decoder_model.onnx`
- `/storage/emulated/0/ChatAI-Files/models/tts/decoder_postnet_and_vocoder.onnx`

**Vérification**:
```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/*.onnx
```

---

#### 2. Fichiers Tokenizer Manquants ❌
**Fichiers requis**:
- `/storage/emulated/0/ChatAI-Files/models/tts/vocab.json`
- `/storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json` (optionnel, fallback si absent)

**Vérification**:
```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/vocab.json
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json
```

---

#### 3. Erreur Initialisation ONNX Runtime ❌
- ONNX Runtime non installé ou version incompatible
- Erreur lors du chargement des modèles ONNX
- Problème de permissions fichiers

**Vérification logs**:
```bash
adb logcat | Select-String "OnnxTTSManager"
```

**Logs attendus si OK**:
```
OnnxTTSManager: Initialisation ONNX TTS (SpeechT5 multi-modèles)...
OnnxTTSManager: Chargement encoder_model.onnx...
OnnxTTSManager: Chargement decoder_model.onnx...
OnnxTTSManager: Chargement decoder_postnet_and_vocoder.onnx...
OnnxTTSManager: ✅ Tokenizer initialisé
OnnxTTSManager: ✅ ONNX TTS prêt (3 modèles, ~607 MB total)
```

**Logs d'erreur possibles**:
```
OnnxTTSManager: Modèles ONNX manquants:
OnnxTTSManager:   - /storage/emulated/0/ChatAI-Files/models/tts/encoder_model.onnx
OnnxTTSManager: ❌ Tokenizer non initialisé - fichier vocab.json manquant
OnnxTTSManager: ❌ Erreur initialisation ONNX TTS: ...
```

---

## 🔧 CORRECTIONS APPLIQUÉES

### 1. ✅ Health Check Corrigé

**AVANT**: Retournait toujours `{"status":"ok","model_loaded":true}`

**MAINTENANT**: Vérifie l'état réel via `isONNXReady()`
```json
{
  "status": "ok" | "not_ready",
  "model_loaded": true | false,
  "server_running": true | false
}
```

### 2. ✅ Logs Améliorés

**Ajout**:
- Logs détaillés pour chaque étape d'initialisation
- Messages d'erreur clairs avec chemins de fichiers
- Diagnostic complet en cas d'erreur
- Vérification tokenizer dans `isONNXReady()`

### 3. ✅ Gestion d'Erreur Améliorée

**Ajout**:
- Vérification tokenizer avant de marquer `isReady = true`
- Messages d'erreur HTTP plus descriptifs
- Logs détaillés dans `synthesizeAndSend()`

---

## 📋 CHECKLIST DE VÉRIFICATION

### Étape 1: Vérifier Fichiers sur Device

```bash
# Lister tous les fichiers ONNX
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/*.onnx

# Vérifier vocab.json
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/vocab.json

# Lister tous les fichiers dans le répertoire
adb shell ls -la /storage/emulated/0/ChatAI-Files/models/tts/
```

**Fichiers requis**:
- ✅ `encoder_model.onnx` (~327 MB)
- ✅ `decoder_model.onnx` (~227 MB)
- ✅ `decoder_postnet_and_vocoder.onnx` (~53 MB)
- ✅ `vocab.json` (~1 KB)

**Fichier optionnel**:
- ⚠️ `default_speaker_embeddings.json` (fallback si absent)

---

### Étape 2: Vérifier Logs Initialisation

```bash
# Filtrer logs OnnxTTSManager
adb logcat | Select-String "OnnxTTSManager"

# Ou tout voir
adb logcat | Select-String "TTS"
```

**Rechercher**:
- ✅ "ONNX TTS prêt" = Succès
- ❌ "Modèles ONNX manquants" = Fichiers absents
- ❌ "Tokenizer non initialisé" = vocab.json manquant
- ❌ "Erreur initialisation" = Problème ONNX Runtime

---

### Étape 3: Tester Health Check

```bash
# Health check via curl
adb shell curl http://127.0.0.1:11401/health

# Ou via webapp
# Onglet Configuration → TTS → "Tester connexion serveur TTS"
```

**Réponse attendue si OK**:
```json
{"status":"ok","model_loaded":true,"server_running":true}
```

**Réponse si erreur**:
```json
{"status":"not_ready","model_loaded":false,"server_running":true}
```

---

### Étape 4: Tester Synthèse (si health check OK)

```bash
# Test synthèse
adb shell curl -X POST http://127.0.0.1:11401/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text":"Bonjour"}' \
  --output test.wav

# Vérifier que le fichier est créé
adb shell ls -lh /sdcard/test.wav
```

---

## 🛠️ SOLUTIONS PAR PROBLÈME

### Problème 1: Modèles ONNX Manquants

**Solution**:
1. Vérifier que les fichiers sont dans `E:\ChatAI-Models\tts\onnx\`
2. Transférer vers device:
   ```bash
   adb push E:\ChatAI-Models\tts\onnx\encoder_model.onnx /storage/emulated/0/ChatAI-Files/models/tts/
   adb push E:\ChatAI-Models\tts\onnx\decoder_model.onnx /storage/emulated/0/ChatAI-Files/models/tts/
   adb push E:\ChatAI-Models\tts\onnx\decoder_postnet_and_vocoder.onnx /storage/emulated/0/ChatAI-Files/models/tts/
   ```

---

### Problème 2: vocab.json Manquant

**Solution**:
1. Le fichier devrait être dans `E:\ChatAI-Models\tts\onnx\vocab.json`
2. Transférer:
   ```bash
   adb push E:\ChatAI-Models\tts\onnx\vocab.json /storage/emulated/0/ChatAI-Files/models/tts/
   ```

---

### Problème 3: default_speaker_embeddings.json Manquant

**Impact**: ⚠️ Non bloquant (fallback automatique)

**Solution**:
1. Générer via script Python (si disponible)
2. Ou utiliser fallback (zéros) - fonctionne mais qualité moindre

---

### Problème 4: Erreur ONNX Runtime

**Vérification**:
- Vérifier que ONNX Runtime est dans les dépendances Gradle
- Vérifier logs pour erreur spécifique

**Solution**:
- Vérifier `app/build.gradle` pour `implementation 'com.microsoft.onnxruntime:onnxruntime-android:...'`

---

## 📊 LOGS DE DIAGNOSTIC

### Logs Normaux (Succès)

```
TTSServer: Initialisation OnnxTTSManager...
OnnxTTSManager: Initialisation ONNX TTS (SpeechT5 multi-modèles)...
OnnxTTSManager: Chargement encoder_model.onnx...
OnnxTTSManager: Chargement decoder_model.onnx...
OnnxTTSManager: Chargement decoder_postnet_and_vocoder.onnx...
OnnxTTSManager: ✅ Tokenizer initialisé
OnnxTTSManager: ✅ ONNX TTS prêt (3 modèles, ~607 MB total)
TTSServer: ✅ OnnxTTSManager prêt (modèles chargés)
TTSServer: Serveur TTS démarré sur le port 11401
TTSServer: Serveur TTS prêt sur http://127.0.0.1:11401
TTSServer: État ONNX: ✅ Prêt
```

### Logs d'Erreur (Modèles Manquants)

```
OnnxTTSManager: Modèles ONNX manquants:
OnnxTTSManager:   - /storage/emulated/0/ChatAI-Files/models/tts/encoder_model.onnx
TTSServer: ⚠️ OnnxTTSManager non prêt (modèles manquants ou erreur)
TTSServer: ⚠️ Le serveur démarrera mais les requêtes de synthèse échoueront
TTSServer: État ONNX: ❌ Non prêt
```

### Logs d'Erreur (Tokenizer)

```
OnnxTTSManager: ❌ Tokenizer non initialisé - fichier vocab.json ou default_speaker_embeddings.json manquant
OnnxTTSManager:    Vérifiez: /storage/emulated/0/ChatAI-Files/models/tts/vocab.json
```

---

## ✅ TEST RAPIDE

### 1. Vérifier Serveur Démarre
```bash
adb logcat | Select-String "TTSServer.*démarré"
```

### 2. Vérifier Modèles Chargés
```bash
adb logcat | Select-String "ONNX TTS prêt"
```

### 3. Tester Health Check
```bash
adb shell curl http://127.0.0.1:11401/health
```

### 4. Tester Synthèse
```bash
adb shell curl -X POST http://127.0.0.1:11401/synthesize \
  -H "Content-Type: application/json" \
  -d '{"text":"test"}' \
  --output /dev/null
```

---

## 🎯 RÉSULTAT ATTENDU

Après corrections:
1. ✅ Health check retourne état réel
2. ✅ Logs détaillés pour diagnostic
3. ✅ Messages d'erreur clairs
4. ✅ Vérification complète (modèles + tokenizer)

**Prochaine étape**: Vérifier logs après redémarrage pour identifier la cause exacte

---

**Document créé**: 2025-11-27  
**Dernière mise à jour**: 2025-11-27  
**Statut**: ✅ Corrections appliquées, prêt pour diagnostic


