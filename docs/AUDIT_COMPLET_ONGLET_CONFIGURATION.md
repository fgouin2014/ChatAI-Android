# 🔍 Audit Complet - Onglet Configuration

**Date**: 2025-11-27  
**Objectif**: Audit complet de l'onglet Configuration et plan de refonte de l'onglet Local

---

## 📋 STRUCTURE ACTUELLE - ONGLETS CONFIGURATION

### Onglets disponibles

1. **⚙️ Général** (`data-tab="general"`)
   - Mode actif (Cloud/Local)
   - Modèle Cloud par défaut

2. **☁️ Cloud** (`data-tab="cloud"`)
   - Provider (Ollama, Hugging Face)
   - API Key
   - Modèle sélectionné
   - Bouton "Tester APIs" ✅

3. **💻 Local** (`data-tab="local"`)
   - URL du serveur
   - Modèle local (select)
   - ❌ **PAS de bouton "Tester connexion"**

4. **🎤 AV** (`data-tab="audio"`)
   - Configuration audio/STT

5. **🔊 Hotword** (`data-tab="hotword"`)
   - Configuration hotword

6. **🗣️ TTS** (`data-tab="tts"`)
   - Configuration TTS (ONNX Server, Android TTS)

7. **🔧 Avancé** (`data-tab="advanced"`)
   - Configuration avancée

---

## 🔍 AUDIT DÉTAILLÉ - ONGLET LOCAL

### État actuel (extras/v3/index.html, lignes 1496-1517)

**UI**:
```html
<div class="config-tab-content" data-content="local">
    <div class="panel-section">
        <h4>Serveur local</h4>
        <div class="form-grid">
            <label>URL
                <input type="text" id="configLocalUrl" placeholder="http://127.0.0.1:11434">
            </label>
            <label>Modèle
                <select id="configLocalModel">
                    <option value="">– Choisir –</option>
                    <option value="llama3.2:3b">Llama 3.2 3B</option>
                    <option value="phi-4">Phi-4</option>
                    <option value="mistral-small">Mistral Small</option>
                    <option value="neural-chat">Neural Chat</option>
                    <option value="custom">Autre (personnalisé)</option>
                </select>
                <input type="text" id="configLocalModelCustom" class="custom-input hidden" placeholder="Modèle local personnalisé">
            </label>
        </div>
        <button class="panel-button" id="saveLocalConfigBtn">Sauvegarder</button>
    </div>
</div>
```

### ❌ PROBLÈMES IDENTIFIÉS

#### 1. Modèles proposés obsolètes

**Problème**:
- Select propose: `llama3.2:3b`, `phi-4`, `mistral-small`, `neural-chat`
- Mais le projet utilise: `gemma3-270m.gguf` (modèle local sur device)
- Les modèles proposés sont des modèles Ollama Cloud/Local sur PC, pas des modèles sur device

**Impact**:
- Confusion pour l'utilisateur
- Les modèles proposés ne correspondent pas à l'architecture actuelle

#### 2. Pas de distinction claire entre serveur Ollama PC et modèle local device

**Problème**:
- L'onglet mélange deux concepts:
  1. **Serveur Ollama sur PC** (via URL réseau: `http://192.168.1.100:11434`)
  2. **Modèle LLM local sur device** (`gemma3-270m.gguf`)

**Impact**:
- Architecture confuse
- L'utilisateur ne comprend pas la différence

#### 3. URL incorrecte par défaut

**Problème**:
- Placeholder: `http://127.0.0.1:11434`
- Mais devrait être: `http://127.0.0.1:11434/v1/chat/completions`
- Et ne devrait PAS être `127.0.0.1` pour un device Android (devrait être l'IP du PC)

#### 4. Pas de bouton "Tester connexion"

**Problème**:
- L'onglet Cloud a un bouton "Tester APIs"
- L'onglet Local n'a pas de bouton de test
- L'utilisateur ne peut pas vérifier si le serveur est accessible

#### 5. Pas d'affichage du statut du serveur

**Problème**:
- Pas d'indicateur si le serveur est accessible
- Pas de message d'erreur clair
- Pas d'aide contextuelle

---

## 📊 ÉTAT ACTUEL DU PROJET - MODÈLES LOCAUX

### Modèles sur le device

| Modèle | Taille | Localisation | Format | Usage |
|--------|--------|--------------|--------|-------|
| `gemma3-270m.gguf` | 278 MB | `/storage/emulated/0/ChatAI-Files/models/` | GGUF | LLM local |
| `ggml-small.bin` | 465 MB | `/storage/emulated/0/ChatAI-Files/models/whisper/` | GGML | STT Whisper |
| `ggml-medium-q5_0.bin` | 515 MB | `/storage/emulated/0/ChatAI-Files/models/whisper/` | GGML | STT Whisper (Medium) |

### Modèles ONNX sur le device

| Catégorie | Modèles | Taille totale | Statut |
|-----------|---------|---------------|--------|
| **TTS** | encoder_model.onnx, decoder_model.onnx, vocoder | 807 MB | ✅ Présents |
| **Embeddings** | (à convertir) | ~87 MB | ⚠️ ONNX généré, pas transféré |
| **Vision** | model.onnx | ~577 MB | ⚠️ ONNX généré, pas transféré |
| **Classification** | (à convertir) | ~30 MB | ❌ Non converti |
| **Translation** | encoder_model.onnx, decoder_model.onnx | ~403 MB | ⚠️ ONNX généré, pas transféré |

---

## 🏗️ ARCHITECTURE ACTUELLE

### Serveur Ollama Local (PC)

**Fonctionnement**:
- Serveur Ollama tourne sur le PC (Windows/Mac/Linux)
- Expose une API compatible OpenAI sur `http://PC_IP:11434/v1/chat/completions`
- L'app Android se connecte via WiFi au serveur PC
- Le serveur PC utilise les modèles Ollama installés (`ollama pull ...`)

**Configuration actuelle**:
- `local_server_url`: `http://127.0.0.1:11434` (❌ INCORRECT - devrait être IP PC)
- `local_model_name`: `gemma3-270m.gguf` (✅ Correct)

**Problème identifié**:
- URL utilise `127.0.0.1` au lieu de l'IP du PC
- `127.0.0.1` = localhost = le device lui-même, pas le PC
- Pour un device Android, il faut utiliser l'IP du PC (ex: `192.168.1.100`)

---

## ❌ PROBLÈME SERVEUR OLLAMA LOCAL

### Diagnostic

**Configuration actuelle** (d'après SharedPreferences):
```
local_server_url: http://127.0.0.1:11434
local_model_name: gemma3-270m.gguf
```

**Problème principal**: `127.0.0.1` ne fonctionne pas depuis un device Android !

**Pourquoi**:
- `127.0.0.1` = localhost = le device Android lui-même
- Le serveur Ollama tourne sur le PC, pas sur le device
- Il faut utiliser l'IP du PC (ex: `192.168.1.100:11434/v1/chat/completions`)

**Solution**:
1. Trouver l'IP du PC sur le réseau local
2. Configurer l'URL: `http://PC_IP:11434/v1/chat/completions`
3. Vérifier que le port 11434 est ouvert sur le PC

---

## 🎯 PLAN DE REFONTE - ONGLET LOCAL

### Phase 1: Clarification de l'architecture

**Section 1: Serveur Ollama (PC)**
- URL du serveur (avec placeholder: `http://192.168.1.XXX:11434/v1/chat/completions`)
- Modèle disponible sur le serveur PC (select avec modèles Ollama communs)
- Bouton "Tester connexion"
- Statut du serveur (accessible/inaccessible)
- Aide: "Le serveur Ollama doit tourner sur votre PC. Trouvez l'IP avec `ipconfig` (Windows)"

**Section 2: Modèles Locaux (Device)** - NOUVEAU
- Liste des modèles GGUF sur le device
- Modèle LLM actif (`gemma3-270m.gguf`)
- Statistiques (taille, emplacement)
- Note: "Ces modèles sont sur votre device Android"

### Phase 2: Amélioration UI

**Ajouts**:
1. ✅ Bouton "Tester connexion" (comme onglet Cloud)
2. ✅ Affichage statut serveur (vert/rouge)
3. ✅ Messages d'aide contextuels
4. ✅ Détection automatique de l'IP PC (optionnel)
5. ✅ Validation de l'URL (format correct)

**Séparation claire**:
- **Serveur Ollama (PC)**: Configuration réseau
- **Modèles Locaux (Device)**: Gestion fichiers GGUF

### Phase 3: Modèles proposés

**Pour Serveur Ollama (PC)**:
- Modèles Ollama populaires:
  - `llama3.2` (3B)
  - `llama3.1:8b` (8B)
  - `mistral` (7B)
  - `gemma2:2b` (2B)
  - `qwen2.5:7b` (7B)
  - Option "Personnalisé" pour autres

**Pour Modèles Locaux (Device)**:
- Scanner automatiquement `/storage/emulated/0/ChatAI-Files/models/*.gguf`
- Afficher: `gemma3-270m.gguf` (278 MB)
- Permettre sélection

---

## 📝 PLAN D'IMPLÉMENTATION DÉTAILLÉ

### Étape 1: Audit et documentation ✅ EN COURS

- [x] Audit onglet Configuration
- [x] Audit onglet Local
- [x] Identifier problèmes serveur Ollama
- [ ] Créer plan détaillé (ce document)

### Étape 2: Refonte HTML (index.html)

**Modifications**:
1. Restructurer l'onglet Local en 2 sections:
   - Section "Serveur Ollama (PC)"
   - Section "Modèles Locaux (Device)"
2. Ajouter bouton "Tester connexion"
3. Ajouter affichage statut serveur
4. Améliorer placeholders et messages d'aide
5. Ajouter validation URL

### Étape 3: Refonte JavaScript (chat-config.js)

**Nouvelles fonctions**:
1. `testLocalServerConnection()` - Tester connexion Ollama PC
2. `loadLocalDeviceModels()` - Scanner modèles GGUF sur device
3. `validateLocalServerUrl()` - Valider format URL
4. `updateLocalServerStatus()` - Afficher statut serveur

**Modifications**:
1. `saveConfigSection('local')` - Séparer serveur PC et modèles device
2. `renderConfigForms()` - Charger modèles device automatiquement

### Étape 4: Backend Android

**Modifications nécessaires**:
1. `AiConfigManager.java` - Séparer `local_server` (PC) et `local_models` (device)
2. Scanner automatiquement les modèles GGUF sur device
3. Exposer API pour lister modèles device

### Étape 5: Tests

1. Tester connexion serveur Ollama PC
2. Tester sélection modèle device
3. Tester sauvegarde/chargement configuration
4. Tester validation URL

---

## ⚠️ PROBLÈME CRITIQUE - SERVEUR OLLAMA

### Cause identifiée

**URL incorrecte**: `http://127.0.0.1:11434`

**Pourquoi ça ne marche pas**:
- `127.0.0.1` = localhost = le device Android
- Le serveur Ollama est sur le PC, pas sur le device
- Le device essaie de se connecter à lui-même au lieu du PC

**Solution immédiate**:
1. Trouver l'IP du PC: `ipconfig` (Windows) → Adresse IPv4
2. Configurer dans webapp: `http://PC_IP:11434/v1/chat/completions`
   - Exemple: `http://192.168.1.100:11434/v1/chat/completions`

**Solution à long terme**:
- Améliorer placeholder dans webapp
- Ajouter validation URL
- Ajouter détection automatique IP PC (optionnel)

---

## 📋 CHECKLIST REFONTE

### Phase 1: Correction problème Ollama (URGENT)

- [ ] Corriger placeholder URL (ajouter `/v1/chat/completions`)
- [ ] Ajouter message d'aide: "Utilisez l'IP de votre PC, pas 127.0.0.1"
- [ ] Ajouter validation URL (vérifier format)

### Phase 2: Refonte onglet Local

- [ ] Séparer "Serveur Ollama (PC)" et "Modèles Locaux (Device)"
- [ ] Ajouter bouton "Tester connexion"
- [ ] Ajouter affichage statut serveur
- [ ] Scanner modèles GGUF sur device
- [ ] Mettre à jour modèles proposés (Ollama populaires)
- [ ] Ajouter messages d'aide contextuels

### Phase 3: Backend

- [ ] Modifier `AiConfigManager.java` pour séparer PC/Device
- [ ] Créer fonction scanner modèles device
- [ ] Exposer API lister modèles device

---

## 🔗 RÉFÉRENCES

- `extras/v3/index.html` (lignes 1496-1517) - Onglet Local actuel
- `extras/v3/chat.js` (lignes 472-476, 829-833) - Logique Local
- `AiConfigManager.java` (lignes 322-435) - Gestion config Local
- `docs/AUDIT_ONGLET_LOCAL.md` - Audit précédent
- `docs/NETTOYAGE_CODE_LEGACY.md` - Nettoyage code legacy


