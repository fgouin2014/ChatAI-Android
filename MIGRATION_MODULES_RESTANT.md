# Migration Modules - Ce qui reste à faire

**Date:** 2025-11-18  
**Branche:** `feature/refactor-chatjs-modular`  
**Status:** Modules créés, migration incomplète

---

## ✅ Modules Créés (8/8)

1. ✅ `chat-utils.js` - Utilitaires partagés (COMPLET)
2. ✅ `chat-ui.js` - Interface utilisateur (COMPLET)
3. ⚠️ `chat-config.js` - Configuration webapp (PARTIEL - méthodes manquantes)
4. ✅ `chat-messaging.js` - Gestion messages/IA (COMPLET)
5. ✅ `chat-speech.js` - STT Whisper/webkit (COMPLET)
6. ✅ `chat-bridge.js` - BidirectionalBridge (COMPLET)
7. ✅ `chat-hotword.js` - Hotword (COMPLET)
8. ⚠️ `chat-core.js` - Coordinateur principal (PARTIEL - références DOM manquantes)

---

## ⚠️ Méthodes Manquantes dans `chat-config.js`

### 1. `saveConfigSection(section, core)` - **CRITIQUE**
- **Status:** Stub seulement (console.log)
- **À faire:** Implémenter toute la logique de sauvegarde pour chaque section
- **Sections à gérer:**
  - `mode` - Mode et modèle sélectionné
  - `cloud` - Configuration Ollama Cloud (provider, API key, modèle)
  - `local` - Configuration serveur local (URL, modèle)
  - `thinking` - WebSearch et Thinking Trace
  - `vision` - Modèle de vision
  - `audio` - Configuration audio/STT (engine, modèle, endpoint, timeouts)
  - `hotword` - Configuration hotword (engine, models, debounce, etc.)
  - `tts` - Configuration TTS
  - `prompts` - Prompts système (KITT, GLADOS, KARR)
  - `constraints` - Contraintes (maxContextTokens, maxResponseTokens)

### 2. `renderConfigForms()` - **CRITIQUE**
- **Status:** Stub seulement (console.log)
- **À faire:** Remplir tous les champs de formulaire depuis `aiConfigObject`
- **Champs à gérer:**
  - Mode & Models
  - Cloud (provider, API key, modèle)
  - Local (URL, modèle)
  - WebSearch & Thinking
  - Vision (modèle)
  - Audio (engine, modèle, endpoint, timeouts, delayAfterHotword)
  - Hotword (enabled, engine, accessKey, keyword, commMode, autoListen, debugScores, debounce, models)
  - TTS (mode, voice)
  - Prompts (kitt, glados, karr)
  - Constraints (maxContext, maxResponse)

### 3. `getSelectValue(select, customInput)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Retourner la valeur du select ou de l'input custom si "custom" est sélectionné
- **Logique:**
  ```javascript
  if (select.value === 'custom') {
      return (customInput?.value || '').trim();
  }
  return select.value || '';
  ```

### 4. `setSelectValue(select, customInput, value)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Définir la valeur du select ou de l'input custom
- **Logique:**
  - Si value existe dans les options → sélectionner l'option
  - Sinon → mettre select à "custom" et remplir customInput

### 5. `toggleCustomInput(select, customInput)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Afficher/cacher l'input custom selon la valeur du select

### 6. `initCustomSelects()` - **CRITIQUE**
- **Status:** Stub seulement
- **À faire:** Initialiser tous les selects personnalisés (mode, cloud, local, vision, audio, tts)
- **Selects à gérer:**
  - `configSelectedModel` / `configSelectedModelCustom`
  - `configCloudProvider` / `configCloudProviderCustom`
  - `configCloudModel` / `configCloudModelCustom`
  - `configLocalModel` / `configLocalModelCustom`
  - `configVisionModel` / `configVisionModelCustom`
  - `configAudioModel` / `configAudioModelCustom`
  - `configTtsVoice` / `configTtsVoiceCustom`

### 7. `renderHotwordModelsTable()` - **CRITIQUE**
- **Status:** Stub seulement (console.log)
- **À faire:** Afficher la grille des modèles hotword avec toutes les fonctionnalités
- **Fonctionnalités:**
  - Afficher chaque modèle avec checkbox enabled/disabled
  - Input nom du modèle (éditable)
  - Input asset (readonly)
  - Input threshold (0-1)
  - Select action (respond_ai_outside_kitt, open_kitt_ui)
  - Bouton supprimer
  - Event listeners pour tous les champs

### 8. `handleAddHotwordModel(core)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Ajouter un nouveau modèle hotword depuis les champs "Ajouter un modèle"
- **Logique:**
  - Lire `hotwordNewName`, `hotwordNewAsset`, `hotwordNewThreshold`
  - Valider (nom et asset requis)
  - Ajouter à `hotwordModels`
  - Vider les champs
  - Re-render la table

### 9. `importHotwordAssets(core)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Importer les modèles hotword depuis les assets Android
- **Logique:**
  - Appeler `androidInterface.listHotwordAssets()` ou fetch `/api/hotword/assets`
  - Pour chaque asset non présent dans `hotwordModels`, ajouter
  - Re-render la table

### 10. `updateHotwordEngineView(core)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Afficher/cacher les champs Porcupine selon l'engine sélectionné
- **Logique:**
  - Si engine === 'porcupine' → afficher `.porcupine-only`
  - Sinon → cacher `.porcupine-only`

### 11. `updateAudioEngineView(core)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Afficher/cacher les champs Whisper selon l'engine sélectionné
- **Logique:**
  - Si engine === 'whisper_server' → afficher `.engine-whisper-only`
  - Sinon → cacher `.engine-whisper-only`

### 12. `persistAiConfig(successMessage)` - **CRITIQUE**
- **Status:** Manquant
- **À faire:** Sauvegarder `aiConfigObject` vers Android
- **Logique:**
  - Convertir `aiConfigObject` en JSON
  - Appeler `pushAiConfigContent()`
  - Afficher feedback

### 13. `toggleHotwordEnabled(index, enabled)` - **MOYEN**
- **Status:** Manquant
- **À faire:** Activer/désactiver un modèle hotword
- **Logique:**
  - Mettre à jour `hotwordModels[index].enabled`
  - Re-render la table

### 14. `removeHotwordModel(index)` - **MOYEN**
- **Status:** Manquant
- **À faire:** Supprimer un modèle hotword
- **Logique:**
  - Confirmer avec l'utilisateur
  - Retirer de `hotwordModels`
  - Re-render la table

---

## ⚠️ Références DOM Manquantes dans `chat-core.js`

### Références à passer à `chat-config.js`:
- Tous les éléments config (déjà initialisés dans `initializeDOMReferences()`)
- `hotwordModels` array (doit être partagé entre core et config)

### Méthodes à ajouter dans `chat-core.js`:
- `getSelectValue(select, customInput)` - Déléguer à chat-config ou implémenter ici
- `setSelectValue(select, customInput, value)` - Déléguer à chat-config ou implémenter ici
- `toggleCustomInput(select, customInput)` - Déléguer à chat-config ou implémenter ici

---

## 📋 Ordre de Migration Recommandé

### Phase 1: Helpers (FACILE)
1. ✅ `getSelectValue()` - Simple getter
2. ✅ `setSelectValue()` - Simple setter
3. ✅ `toggleCustomInput()` - Simple toggle

### Phase 2: Initialisation (MOYEN)
4. ✅ `initCustomSelects()` - Utilise les helpers
5. ✅ `updateHotwordEngineView()` - Simple toggle
6. ✅ `updateAudioEngineView()` - Simple toggle

### Phase 3: Hotword Models (MOYEN)
7. ✅ `renderHotwordModelsTable()` - Complexe mais isolé
8. ✅ `handleAddHotwordModel()` - Utilise renderHotwordModelsTable
9. ✅ `importHotwordAssets()` - Utilise renderHotwordModelsTable
10. ✅ `toggleHotwordEnabled()` - Utilise renderHotwordModelsTable
11. ✅ `removeHotwordModel()` - Utilise renderHotwordModelsTable

### Phase 4: Configuration Forms (DIFFICILE)
12. ✅ `renderConfigForms()` - Très complexe, beaucoup de champs
13. ✅ `saveConfigSection()` - Très complexe, beaucoup de sections
14. ✅ `persistAiConfig()` - Simple wrapper

### Phase 5: Intégration (TEST)
15. ✅ Passer toutes les références DOM à chat-config
16. ✅ Tester chaque section de config
17. ✅ Réactiver les modules dans index.html

---

## 🔧 Modifications Nécessaires dans `chat-config.js`

### Constructor
```javascript
constructor(androidInterface) {
    this.androidInterface = androidInterface;
    this.aiConfigObject = null;
    this.aiConfigCache = '';
    this.hotwordModels = [];
    this.customSelects = [];
    this.core = null; // ← AJOUTER: référence au core
}
```

### Méthode `initializeWithReferences(core)`
```javascript
initializeWithReferences(core) {
    this.core = core;
    // Maintenant toutes les méthodes peuvent utiliser core.* pour accéder aux éléments DOM
}
```

### Toutes les méthodes doivent utiliser `this.core.*` au lieu de `this.*` pour les éléments DOM

---

## 🧪 Tests à Effectuer Après Migration

1. **Mode & Models:**
   - [ ] Changer le mode (cloud/local)
   - [ ] Sélectionner un modèle
   - [ ] Utiliser "Autre (personnalisé)"
   - [ ] Sauvegarder

2. **Cloud:**
   - [ ] Changer provider
   - [ ] Entrer/modifier API key
   - [ ] Sélectionner modèle
   - [ ] Sauvegarder

3. **Local:**
   - [ ] Changer URL
   - [ ] Sélectionner modèle
   - [ ] Sauvegarder

4. **Thinking:**
   - [ ] Activer/désactiver WebSearch
   - [ ] Activer/désactiver Thinking
   - [ ] Sauvegarder

5. **Vision:**
   - [ ] Sélectionner modèle
   - [ ] Désactiver (vider le champ)
   - [ ] Sauvegarder

6. **Audio:**
   - [ ] Changer engine (Whisper/Google)
   - [ ] Vérifier que les champs Whisper s'affichent/cachent
   - [ ] Modifier endpoint, timeouts, etc.
   - [ ] Sauvegarder

7. **Hotword:**
   - [ ] Activer/désactiver hotword
   - [ ] Changer engine (OpenWakeWord/Porcupine)
   - [ ] Vérifier que les champs Porcupine s'affichent/cachent
   - [ ] Ajouter un modèle
   - [ ] Importer depuis assets
   - [ ] Modifier nom, threshold, action d'un modèle
   - [ ] Activer/désactiver un modèle
   - [ ] Supprimer un modèle
   - [ ] Sauvegarder

8. **TTS:**
   - [ ] Changer mode
   - [ ] Sélectionner voice
   - [ ] Sauvegarder

9. **Prompts:**
   - [ ] Modifier prompts KITT, GLADOS, KARR
   - [ ] Sauvegarder

10. **Constraints:**
    - [ ] Modifier maxContextTokens, maxResponseTokens
    - [ ] Sauvegarder

11. **AI Config Editor:**
    - [ ] Ouvrir l'éditeur
    - [ ] Modifier le JSON
    - [ ] Sauvegarder
    - [ ] Recharger

---

## 📝 Notes Importantes

1. **Références DOM:** Toutes les références DOM sont dans `chat-core.js`. `chat-config.js` doit les recevoir via `initializeWithReferences(core)`.

2. **hotwordModels:** Doit être partagé entre `core` et `config`. Stocker dans `core.hotwordModels` et accéder via `this.core.hotwordModels` dans `config`.

3. **aiConfigObject:** Doit être synchronisé. Stocker dans `config.aiConfigObject` et accéder depuis `core` si nécessaire.

4. **Feedback:** Utiliser `this.showConfigFeedback()` pour tous les messages de feedback.

5. **Validation:** Valider tous les inputs avant sauvegarde (API keys, URLs, thresholds, etc.).

---

## ✅ Checklist Finale

- [ ] Toutes les méthodes implémentées dans `chat-config.js`
- [ ] Toutes les références DOM passées correctement
- [ ] `hotwordModels` partagé correctement
- [ ] Tous les tests passent
- [ ] `chat.js` désactivé dans `index.html`
- [ ] Modules réactivés dans `index.html`
- [ ] Aucune erreur console
- [ ] Tous les boutons fonctionnent
- [ ] Toutes les sections de config fonctionnent

---

**Prochaine étape:** Compléter toutes les méthodes manquantes dans `chat-config.js` en utilisant les références DOM depuis `core`.

