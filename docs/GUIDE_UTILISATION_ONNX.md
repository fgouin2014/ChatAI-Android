# 📖 Guide d'utilisation des fonctionnalités ONNX

## Date: 2025-11-29

---

## 🎯 Vue d'ensemble

Les modèles ONNX (Embeddings, Vision, Translation) sont maintenant intégrés dans l'interface du chat. Voici comment les utiliser.

---

## 👁️ Vision ONNX (CLIP) - Analyse d'images

### 📍 Localisation

**Bouton dans la barre de plugins:** `📷 Image`

### 🚀 Comment utiliser

1. **Cliquer sur le bouton "📷 Image"** dans la barre de plugins (sous le champ de saisie)
2. **Sélectionner une image** depuis votre galerie
3. **L'image s'affiche dans le chat** et l'analyse démarre automatiquement
4. **Le résultat de l'analyse** apparaît comme réponse de l'IA

### ⚙️ Prérequis

- Modèles ONNX Vision installés dans `/storage/emulated/0/ChatAI-Files/models/vision/`:
  - `vision_model.onnx` (image encoder)
  - `text_model.onnx` (text encoder)

### 🔍 Fonctionnement

- **Backend:** `VisionService.kt` → `OnnxVisionManager.kt`
- **Frontend:** `chat.js` → `analyzeImage()` → `onVisionAnalysisResult()`
- **Interface Android:** `WebAppInterface.java` → `analyzeImage()`

### 📝 Exemple d'utilisation

1. Cliquer sur `📷 Image`
2. Sélectionner une photo de chat
3. Résultat: *"Je vois une image qui contient un chat assis sur un tapis."*

---

## 🌐 Translation ONNX (MarianMT) - Traduction de texte

### 📍 Localisation

**Bouton dans la barre de plugins:** `🌐 Traduire`

### 🚀 Comment utiliser

**Option 1: Traduire le texte sélectionné**
1. **Sélectionner du texte** dans le champ de saisie
2. **Cliquer sur le bouton "🌐 Traduire"**
3. **Le texte traduit** apparaît comme réponse de l'IA

**Option 2: Traduire le texte dans le champ de saisie**
1. **Taper ou coller du texte** dans le champ de saisie
2. **Cliquer sur le bouton "🌐 Traduire"**
3. **Le texte traduit** apparaît comme réponse de l'IA

**Option 3: Traduire le dernier message**
1. Si le champ de saisie est vide, **cliquer sur "🌐 Traduire"**
2. **Le dernier message utilisateur** sera traduit

### ⚙️ Prérequis

- Modèles ONNX Translation installés dans `/storage/emulated/0/ChatAI-Files/models/translation/`:
  - `encoder_model.onnx`
  - `decoder_model.onnx`
  - `vocab.json` (tokenizer)

### 🔍 Fonctionnement

- **Backend:** `TranslationService.kt` → `OnnxTranslationManager.kt`
- **Frontend:** `chat.js` → `translateText()` → `onTranslationResult()`
- **Interface Android:** `WebAppInterface.java` → `translateText()`

### 📝 Exemple d'utilisation

1. Taper: *"Bonjour, comment allez-vous ?"*
2. Cliquer sur `🌐 Traduire`
3. Résultat: *"🌐 Traduction: Hello, how are you?"*

---

## 🔗 Embeddings ONNX (all-MiniLM-L6-v2) - RAG automatique

### 📍 Localisation

**Configuration:** Onglet Configuration → Local → RAG

### 🚀 Comment utiliser

**Les embeddings ONNX sont utilisés automatiquement** pour le RAG (Recherche Augmentée par Génération) :

1. **Activer RAG** dans Configuration → Local → RAG
2. **Sélectionner "ONNX Local (Device)"** comme source d'embeddings
3. **Sélectionner le modèle ONNX** (ex: `model.onnx`)
4. **Sauvegarder** la configuration

### ⚙️ Prérequis

- Modèle ONNX Embeddings installé dans `/storage/emulated/0/ChatAI-Files/models/embeddings/`:
  - `model.onnx` (all-MiniLM-L6-v2, 384 dimensions)
  - `tokenizer.json`
  - `vocab.json`

### 🔍 Fonctionnement

- **Backend:** `EmbeddingService.kt` → `OnnxEmbeddingManager.kt`
- **Utilisation:** Automatique lors des conversations (RAG)
- **Interface Android:** Intégré dans le flux de conversation

### 📝 Exemple d'utilisation

1. Activer RAG avec ONNX Local
2. Poser une question similaire à une conversation précédente
3. L'IA utilise les embeddings pour retrouver le contexte passé

---

## 🎨 Interface utilisateur

### Barre de plugins

Les nouveaux boutons apparaissent dans la barre de plugins, sous le champ de saisie :

```
[🔍 Recherche] [🔢 Calculette] [🌤️ Météo] [😂 Blagues] [💡 Conseils] [📷 Image] [🌐 Traduire]
```

### Boutons

- **📷 Image:** Upload et analyse d'image via Vision ONNX
- **🌐 Traduire:** Traduction de texte via Translation ONNX

---

## 🔧 Dépannage

### Vision ONNX ne fonctionne pas

1. Vérifier que les fichiers sont présents:
   ```bash
   adb shell "ls -la /storage/emulated/0/ChatAI-Files/models/vision/"
   ```
2. Vérifier dans Configuration → Local → Vision ONNX que les modèles sont détectés
3. Vérifier les logs: `adb logcat | Select-String "VisionService|OnnxVisionManager"`

### Translation ONNX ne fonctionne pas

1. Vérifier que les fichiers sont présents:
   ```bash
   adb shell "ls -la /storage/emulated/0/ChatAI-Files/models/translation/"
   ```
2. Vérifier dans Configuration → Local → Translation ONNX que les modèles sont détectés
3. Vérifier les logs: `adb logcat | Select-String "TranslationService|OnnxTranslationManager"`

### Embeddings ONNX ne fonctionne pas

1. Vérifier que les fichiers sont présents:
   ```bash
   adb shell "ls -la /storage/emulated/0/ChatAI-Files/models/embeddings/"
   ```
2. Vérifier dans Configuration → Local → RAG que "ONNX Local" est sélectionné
3. Vérifier les logs: `adb logcat | Select-String "EmbeddingService|OnnxEmbeddingManager"`

---

## 📊 Statut des fonctionnalités

| Fonctionnalité | Bouton UI | Backend | Frontend | Status |
|----------------|-----------|---------|----------|--------|
| Vision ONNX | ✅ 📷 Image | ✅ | ✅ | ✅ Fonctionnel |
| Translation ONNX | ✅ 🌐 Traduire | ✅ | ✅ | ✅ Fonctionnel |
| Embeddings ONNX | ✅ (RAG auto) | ✅ | ✅ | ✅ Fonctionnel |

---

## 🎯 Prochaines améliorations possibles

1. **Vision ONNX:**
   - Comparaison image-texte (recherche sémantique)
   - Description plus détaillée avec templates

2. **Translation ONNX:**
   - Support de plusieurs langues (pas seulement fr→en)
   - Traduction automatique des messages

3. **Embeddings ONNX:**
   - Indicateur visuel quand RAG utilise les embeddings
   - Statistiques d'utilisation

---

**Statut:** ✅ Toutes les fonctionnalités ONNX sont maintenant accessibles depuis l'interface du chat

