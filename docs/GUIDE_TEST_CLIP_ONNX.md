# 🧪 GUIDE DE TEST - CLIP ONNX

## Date: 2025-11-29

---

## 📋 PRÉREQUIS

✅ **Fichiers présents sur le device:**
- `vision_model.onnx` + `.data`
- `text_model.onnx` + `.data`
- Fichiers tokenizer

---

## 🔍 ÉTAPE 1: Vérifier les fichiers

### 1.1 Lister les fichiers ONNX

```bash
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/vision/*.onnx*
```

**Résultat attendu:**
```
vision_model.onnx (1.2 MB)
vision_model.onnx.data (334 MB)
text_model.onnx (1.0 MB)
text_model.onnx.data (241 MB)
```

### 1.2 Vérifier les permissions

Les fichiers doivent être lisibles par l'application Android.

---

## 🔍 ÉTAPE 2: Vérifier l'initialisation

### 2.1 Démarrer l'application

1. Ouvrir l'application ChatAI
2. Attendre le démarrage complet

### 2.2 Vérifier les logs d'initialisation

**Terminal 1 - Suivre les logs en temps réel:**

```bash
adb logcat -c
adb logcat | Select-String -Pattern "OnnxVisionManager|VisionService"
```

**Terminal 2 - Ou voir les logs récents:**

```bash
adb logcat -d | Select-String -Pattern "OnnxVisionManager|VisionService" | Select-Object -Last 50
```

### 2.3 Logs attendus (SUCCÈS)

```
OnnxVisionManager: Initialisation ONNX Vision CLIP (vit-base-patch32)...
OnnxVisionManager: Modèles trouvés:
OnnxVisionManager:   Vision: vision_model.onnx (1.22 MB)
OnnxVisionManager:   Text: text_model.onnx (1.06 MB)
OnnxVisionManager: Chargement vision_model.onnx...
OnnxVisionManager: Chargement text_model.onnx...
OnnxVisionManager: Vision Model:
OnnxVisionManager:   Inputs: pixel_values
OnnxVisionManager:   Outputs: last_hidden_state
OnnxVisionManager: Text Model:
OnnxVisionManager:   Inputs: input_ids, attention_mask
OnnxVisionManager:   Outputs: last_hidden_state
OnnxVisionManager: ✅ Tokenizer BERT initialisé (XXXX tokens)
OnnxVisionManager: ✅ ONNX Vision CLIP prêt (512 dimensions)
VisionService: ✅ ONNX Vision initialisé (CLIP, 512 dimensions)
```

### 2.4 Logs d'erreur possibles

#### Erreur: Fichiers manquants

```
OnnxVisionManager: Modèles ONNX CLIP manquants:
OnnxVisionManager:   - /storage/emulated/0/ChatAI-Files/models/vision/vision_model.onnx
```

**Solution:** Vérifier que les fichiers sont bien présents.

#### Erreur: Format incompatible

```
OnnxVisionManager: Erreur initialisation ONNX Vision CLIP: ...
```

**Solution:** Vérifier les logs complets pour l'erreur détaillée.

---

## 🧪 ÉTAPE 3: Tester via l'interface

### 3.1 Vérifier le statut dans l'interface

1. Ouvrir l'application ChatAI
2. Aller dans **Configuration** → **Local**
3. Trouver la section **"Vision ONNX (CLIP)"**
4. Vérifier le statut:
   - ✅ **"Prêt"** ou **"Initialisé"** = SUCCÈS
   - ❌ **"Non disponible"** = Échec

### 3.2 Tester l'analyse d'image

**Option A: Via le chat**

1. Dans le chat, chercher une option pour envoyer une image
2. Sélectionner une image
3. L'application devrait analyser l'image et générer une description

**Option B: Via JavaScript (si disponible)**

Si l'interface expose une fonction de test, l'utiliser.

---

## 🧪 ÉTAPE 4: Vérifier les logs pendant l'utilisation

### 4.1 Suivre les logs pendant l'analyse

```bash
adb logcat -c
adb logcat | Select-String -Pattern "OnnxVisionManager|VisionService|encodeImage|analyzeImage"
```

### 4.2 Logs attendus pendant l'encodage

```
VisionService: analyzeImage appelé
OnnxVisionManager: Encodage image: 1920x1080
OnnxVisionManager: Input ONNX Vision: shape [1, 3, 224, 224]
OnnxVisionManager: Output shape: [1, 197, 512]
OnnxVisionManager: ✅ Image encodée: 512 dimensions
VisionService: ✅ Description générée
```

---

## 🔍 ÉTAPE 5: Diagnostic avancé

### 5.1 Vérifier les inputs/outputs du modèle

Si l'initialisation réussit, les logs affichent:
```
OnnxVisionManager: Vision Model:
OnnxVisionManager:   Inputs: pixel_values
OnnxVisionManager:   Outputs: last_hidden_state
OnnxVisionManager: Text Model:
OnnxVisionManager:   Inputs: input_ids, attention_mask
OnnxVisionManager:   Outputs: last_hidden_state
```

**Vérifier:**
- ✅ Vision inputs: `pixel_values`
- ✅ Vision outputs: `last_hidden_state` (ou autre selon export)
- ✅ Text inputs: `input_ids`, `attention_mask`
- ✅ Text outputs: `last_hidden_state`

### 5.2 Tester manuellement avec adb

```bash
# Vérifier que l'application peut lire les fichiers
adb shell run-as com.chatai ls -l /storage/emulated/0/ChatAI-Files/models/vision/
```

---

## 🐛 DÉPANNAGE

### Problème 1: Fichiers non trouvés

**Symptôme:**
```
OnnxVisionManager: Modèles ONNX CLIP manquants
```

**Solution:**
1. Vérifier les chemins: `/storage/emulated/0/ChatAI-Files/models/vision/`
2. Vérifier les noms exacts: `vision_model.onnx`, `text_model.onnx`
3. Vérifier les permissions: fichiers lisibles par l'application

### Problème 2: Erreur de chargement ONNX

**Symptôme:**
```
OnnxVisionManager: Erreur initialisation ONNX Vision CLIP: ...
```

**Solutions:**
1. Vérifier que les fichiers `.onnx.data` sont présents
2. Vérifier la version ONNX Runtime Android (compatible opset 18)
3. Vérifier les logs complets pour l'erreur exacte

### Problème 3: Inputs/Outputs incorrects

**Symptôme:**
```
Erreur: Input 'pixel_values' not found
```

**Solutions:**
1. Vérifier les noms d'inputs dans les logs
2. Adapter le code Android si nécessaire
3. Vérifier que l'export ONNX a utilisé les bons noms

---

## ✅ CHECKLIST DE VALIDATION

- [ ] Fichiers présents sur le device
- [ ] Permissions correctes
- [ ] Logs d'initialisation sans erreur
- [ ] Message "✅ ONNX Vision CLIP prêt" dans les logs
- [ ] Statut "Prêt" dans l'interface
- [ ] Test d'analyse d'image fonctionne
- [ ] Embeddings générés (512 dimensions)

---

## 📊 RÉSULTATS ATTENDUS

### Succès complet

1. ✅ Initialisation réussie (logs)
2. ✅ Statut "Prêt" dans l'interface
3. ✅ Analyse d'image fonctionne
4. ✅ Embeddings 512 dimensions générés

### Succès partiel

1. ✅ Initialisation réussie
2. ⚠️ Analyse d'image ne fonctionne pas encore
   - Vérifier la fonction `encodeImage()` dans le code
   - Vérifier les logs pendant l'analyse

### Échec

1. ❌ Initialisation échoue
   - Suivre le guide de dépannage
   - Vérifier les logs détaillés
   - Vérifier que les fichiers sont corrects

---

## 🔄 PROCHAINES ÉTAPES APRÈS SUCCÈS

1. ✅ Tester avec différentes images
2. ✅ Tester l'encodage de texte (si implémenté)
3. ✅ Tester la recherche sémantique image-texte
4. ✅ Optimiser les performances si nécessaire

---

**Bon test ! 🚀**


