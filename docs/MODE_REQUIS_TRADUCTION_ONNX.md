# 📋 MODE REQUIS - Traduction ONNX

## Date: 2025-11-29

---

## ✅ RÉPONSE RAPIDE

**La traduction ONNX fonctionne dans TOUS les modes** - elle est **100% offline** et indépendante du mode Cloud/Local configuré pour l'IA principale.

---

## 🔄 MODES D'UTILISATION

### ✅ Mode Cloud (Ollama Cloud)
- ✅ Traduction ONNX fonctionne
- ✅ Fonctionne même sans connexion internet après initialisation
- ✅ Indépendant du provider cloud configuré

### ✅ Mode Local (Ollama PC)
- ✅ Traduction ONNX fonctionne
- ✅ Pas besoin que le serveur Ollama soit démarré
- ✅ Indépendant du serveur Ollama local

### ✅ Mode Device (Modèles locaux GGUF)
- ✅ Traduction ONNX fonctionne
- ✅ Utilise les modèles ONNX sur le device

---

## 📋 PRÉREQUIS OBLIGATOIRES

Pour que la traduction ONNX fonctionne, il faut **UNIQUEMENT**:

### 1. Fichiers ONNX présents sur le device

**Répertoire:** `/storage/emulated/0/ChatAI-Files/models/translation/`

**Fichiers requis:**
- ✅ `encoder_model.onnx` - Modèle encoder MarianMT
- ✅ `decoder_model.onnx` - Modèle decoder MarianMT
- ✅ `vocab.json` - Vocabulaire SentencePiece MarianMT

**Vérification:**
```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/translation/
```

### 2. Initialisation automatique

La traduction s'initialise **automatiquement** au démarrage de l'application:
- `TranslationService` s'initialise dans `MainActivity`
- `OnnxTranslationManager.initialize()` vérifie la présence des fichiers
- Si les fichiers sont présents → initialisation réussie
- Si les fichiers sont absents → fallback (rien pour l'instant)

---

## ❌ CE QUI N'EST PAS NÉCESSAIRE

1. ❌ **Pas besoin de mode Cloud/Local spécifique**
   - Fonctionne dans tous les modes
   - Indépendant de la configuration AI principale

2. ❌ **Pas besoin de connexion internet**
   - 100% offline après chargement des modèles

3. ❌ **Pas besoin de serveur Ollama**
   - Indépendant du serveur Ollama local ou cloud

4. ❌ **Pas besoin de clé API**
   - Pas d'appel API externe

---

## 🔍 COMMENT VÉRIFIER

### 1. Vérifier les fichiers sur le device

```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/translation/
```

**Résultat attendu:**
```
encoder_model.onnx     (~50 MB)
decoder_model.onnx     (~4-5 MB)
vocab.json             (~1-2 MB)
```

### 2. Vérifier l'initialisation dans les logs

```bash
adb logcat | Select-String -Pattern "OnnxTranslationManager|TranslationService"
```

**Logs attendus si OK:**
```
✅ ONNX Translation MarianMT prêt
✅ SentencePieceTokenizer initialisé
✅ TranslationService initialisé
```

**Logs si fichiers manquants:**
```
⚠️ Modèles ONNX MarianMT manquants
⚠️ ONNX Translation non disponible
```

### 3. Tester la traduction

La traduction peut être appelée depuis JavaScript:
```javascript
window.Android.translateText("Bonjour le monde");
```

---

## 🚨 PROBLÈMES POSSIBLES

### Si la traduction ne fonctionne pas:

1. **Fichiers manquants:**
   - Vérifier que les 3 fichiers sont présents
   - Vérifier les chemins exacts

2. **Vocabulaire incorrect:**
   - `vocab.json` doit être le vocabulaire MarianMT réel
   - Format: `{"<pad>": 0, "<unk>": 1, "<s>": 2, ...}`

3. **Modèles ONNX incorrects:**
   - Vérifier que les modèles sont bien pour `opus-mt-fr-en`
   - Vérifier qu'ils ont été convertis correctement

---

## 📝 RÉSUMÉ

| Condition | Nécessaire ? |
|-----------|--------------|
| **Fichiers ONNX sur device** | ✅ **OUI** (obligatoire) |
| **Mode Cloud** | ❌ NON (fonctionne dans tous les modes) |
| **Mode Local** | ❌ NON (fonctionne dans tous les modes) |
| **Connexion internet** | ❌ NON (100% offline) |
| **Serveur Ollama** | ❌ NON (indépendant) |
| **Clé API** | ❌ NON (pas d'API externe) |

---

**Conclusion:** La traduction ONNX fonctionne dans **TOUS LES MODES** tant que les fichiers ONNX sont présents sur le device.

