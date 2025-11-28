# 🔍 Audit Onglet Local - Configuration

**Date**: 2025-11-27  
**Objectif**: Identifier le code legacy et les problèmes dans l'onglet Local

---

## 📋 PROBLÈMES IDENTIFIÉS

### 1. ✅ Modèle Local Hardcodé (NETTOYÉ)

**Statut**: ✅ **CORRIGÉ** - Tous les hardcodes retirés, modèle configurable par l'utilisateur

**Problème initial**: Le modèle local était **forcé à `gemma3-270m.gguf`** dans plusieurs fichiers, mais certains utilisaient encore `llama3.2` par défaut.

**Fichiers affectés**:

#### `AiConfigManager.java` (lignes 325-326, 428-433)
```java
// CRITIQUE: Modèle local fixé à gemma3-270m.gguf (ignorer toute autre valeur)
localServer.put("model", "gemma3-270m.gguf");
```

#### `KittAIService.kt` (ligne 1793)
```kotlin
val localModel = sharedPreferences.getString("local_model_name", "llama3.2")?.trim()
// ⚠️ PROBLÈME: Par défaut "llama3.2" mais devrait être "gemma3-270m.gguf"
```

#### `OllamaThinkingService.kt` (lignes 174-175, 670)
```kotlin
// Mode Local : toujours gemma3-270m.gguf (fixé)
"gemma3-270m.gguf"
```

#### `AIConfigurationActivity.kt` (ligne 249)
```kotlin
val localModelName = sharedPreferences.getString("local_model_name", "llama3.2")
// ⚠️ PROBLÈME: Par défaut "llama3.2" mais devrait être "gemma3-270m.gguf"
```

**Corrections effectuées**:
- ✅ Retiré tous les hardcodes dans `AiConfigManager.java`, `OllamaThinkingService.kt`
- ✅ Uniformisé la valeur par défaut à `gemma3-270m.gguf` partout
- ✅ Modèle maintenant configurable par l'utilisateur via webapp
- ✅ Voir `docs/NETTOYAGE_CODE_LEGACY.md` pour détails

---

### 2. ✅ Onglet Local dans Webapp (FONCTIONNEL)

**Fichier**: `extras/v3/index.html` (lignes 1496-1517)

**Statut**: ✅ **FONCTIONNEL**

**Éléments vérifiés**:
- ✅ Existence de l'onglet Local dans l'UI (ligne 1496)
- ✅ Champs `configLocalUrl` (ligne 1501) et `configLocalModel` (ligne 1504)
- ✅ Sauvegarde de la configuration (`chat.js` ligne 829-833)
- ✅ Chargement de la configuration depuis Android (`chat.js` ligne 472-476)
- ❌ **MANQUE**: Test de connexion au serveur local (pas de bouton "Tester")

**Problèmes identifiés**:
1. **Modèles proposés dans le select ne correspondent pas au modèle hardcodé**
   - Select propose: `llama3.2:3b`, `phi-4`, `mistral-small`, `neural-chat`
   - Code Android force: `gemma3-270m.gguf`
   - **Impact**: L'utilisateur peut choisir un modèle dans la webapp, mais Android l'ignore

2. **Pas de bouton "Tester connexion"**
   - L'onglet Cloud a un bouton "Tester APIs"
   - L'onglet Local n'a pas de bouton de test
   - **Impact**: L'utilisateur ne peut pas vérifier si son serveur local est accessible

---

### 3. ✅ Conversions ONNX TTS (FAIT)

**Statut**: ✅ **FICHIERS ONNX TTS DÉJÀ SUR LE DEVICE**

**Fichiers vérifiés sur device** (`/storage/emulated/0/ChatAI-Files/models/tts/`):

| Fichier | Taille | Statut |
|---------|--------|--------|
| `encoder_model.onnx` | 327 MB | ✅ Présent |
| `decoder_model.onnx` | 227 MB | ✅ Présent |
| `decoder_with_past_model.onnx` | 200 MB | ✅ Présent (optionnel) |
| `decoder_postnet_and_vocoder.onnx` | 53 MB | ✅ Présent |
| `vocab.json` | 0.9 KB | ✅ Présent |
| `preprocessor_config.json` | 477 B | ✅ Présent |
| `tokenizer_config.json` | 1.4 KB | ✅ Présent |
| `spm_char.model` | 233 KB | ✅ Présent |

**Total**: **~807 MB** (4 modèles ONNX + fichiers de configuration)

**⚠️ Fichier manquant**: `default_speaker_embeddings.json`
- **Impact**: `SimpleTokenizer.kt` utilise un fallback (512 zéros) si absent
- **Statut**: Non bloquant (fonctionne avec fallback), mais recommandé pour qualité optimale
- **Action**: Générer via script Python ou utiliser valeurs par défaut

---

### 4. ❌ Conversions ONNX Autres Modèles (À FAIRE)

**Statut actuel**:

| Modèle | Format | Statut | Priorité |
|--------|--------|--------|----------|
| **Embeddings** (all-MiniLM-L6-v2) | ONNX | ❌ Non converti | ⭐⭐⭐ HAUTE |
| **Vision** (CLIP) | ONNX | ❌ Non converti | ⭐⭐ Moyenne |
| **Classification** (DistilBERT) | ONNX | ❌ Non converti | ⭐⭐ Moyenne |
| **Translation** (opus-mt-fr-en) | ONNX | ❌ Non converti | ⭐ Faible |

**Fichiers téléchargés** (PyTorch):
- ✅ `E:/ChatAI-Models/embeddings/` (all-MiniLM-L6-v2 PyTorch)
- ✅ `E:/ChatAI-Models/vision/` (CLIP PyTorch)
- ✅ `E:/ChatAI-Models/classification/` (DistilBERT PyTorch)
- ✅ `E:/ChatAI-Models/translation/` (opus-mt-fr-en PyTorch)

**Conversion nécessaire**: PyTorch → ONNX pour chaque modèle

---

## 🎯 ACTIONS RECOMMANDÉES

### Priorité 1: Nettoyer Code Legacy Ollama Local ✅ **FAIT**

1. ✅ **Uniformiser le modèle par défaut**
   - ✅ Retiré tous les hardcodes `gemma3-270m.gguf`
   - ✅ Utilise la valeur depuis `SharedPreferences` (`local_model_name`)
   - ✅ Respecte le choix de l'utilisateur dans la webapp
   - ✅ Valeur par défaut uniformisée : `gemma3-270m.gguf` partout

2. ✅ **Supprimer les hardcodes**
   - ✅ Retiré les commentaires `// CRITIQUE: Modèle local fixé` dans `AiConfigManager.java`
   - ✅ Retiré le hardcode dans `OllamaThinkingService.kt` (lignes 175, 670)
   - ✅ Configuration dynamique via webapp maintenant fonctionnelle

3. ⚠️ **Ajouter bouton "Tester connexion" dans onglet Local** (À FAIRE)
   - Créer fonction `testLocalServerConnection()` similaire à `testCloudConnection()`
   - Ajouter bouton dans `index.html` (ligne 1515)
   - Tester l'URL et le modèle configurés

### Priorité 2: Compléter Conversions ONNX Autres Modèles ✅ **SCRIPTS CRÉÉS**

1. **TTS ONNX** ✅ **FAIT**
   - ✅ Fichiers ONNX déjà sur le device (807 MB)
   - ✅ Conversion réussie
   - ⚠️ **À tester**: Vérifier que `default_speaker_embeddings.json` existe

2. **Embeddings ONNX** ✅ **SCRIPT CRÉÉ**
   - ✅ Script: `E:/ChatAI-Models/embeddings/convert_to_onnx.py`
   - ⚠️ **À exécuter**: `cd E:\ChatAI-Models\embeddings && python convert_to_onnx.py`
   - ⚠️ **À intégrer**: Dans `EmbeddingService.kt`
   - **Impact**: RAG 100% offline possible

3. **Autres modèles ONNX** ✅ **SCRIPTS CRÉÉS**
   - ✅ Vision: `E:/ChatAI-Models/vision/convert_to_onnx.py`
   - ✅ Classification: `E:/ChatAI-Models/classification/convert_to_onnx.py`
   - ✅ Translation: `E:/ChatAI-Models/translation/convert_to_onnx.py`
   - ⚠️ **À exécuter**: Scripts prêts, à lancer dans l'ordre de priorité
   - ⚠️ **À intégrer**: Dans l'application Android

---

## 📝 NOTES

- **Modèle local**: Actuellement `gemma3-270m.gguf` est hardcodé, mais l'utilisateur devrait pouvoir choisir
- **Onglet Local**: Nécessite vérification dans `extras/v3/index.html`
- **Conversions ONNX**: TTS est la priorité, puis Embeddings (pour RAG offline)

---

## 🔗 RÉFÉRENCES

- `AiConfigManager.java` (lignes 320-435) - ✅ Nettoyé
- `KittAIService.kt` (ligne 1793) - ✅ Nettoyé
- `OllamaThinkingService.kt` (lignes 174-176, 670) - ✅ Nettoyé
- `AIConfigurationActivity.kt` (ligne 249) - ✅ Nettoyé
- `extras/v3/index.html` (lignes 1495+) - ✅ Fonctionnel
- `docs/NETTOYAGE_CODE_LEGACY.md` - ✅ Détails du nettoyage
- `docs/SCRIPTS_CONVERSION_ONNX.md` - ✅ Documentation scripts
- `docs/STATUT_TELECHARGEMENT_MODELES.md`
- `docs/STATUT_FINAL_PROJET_ONNX_TTS.md`

