# 📋 PLAN DÉTAILLÉ - Refonte Onglet Local

**Date**: 2025-11-27  
**Statut**: 📝 **PLANIFICATION** - À valider avant implémentation

---

## 🎯 OBJECTIFS

1. **Corriger le problème de connexion Ollama local**
   - URL incorrecte (`127.0.0.1` au lieu de l'IP du PC)
   - Ajouter validation et aide

2. **Clarifier l'architecture**
   - Distinguer clairement: Serveur Ollama (PC) vs Modèles Locaux (Device)
   - Refléter l'état actuel du projet

3. **Améliorer l'expérience utilisateur**
   - Bouton "Tester connexion"
   - Affichage statut serveur
   - Messages d'aide contextuels

4. **Mettre à jour les modèles proposés**
   - Modèles Ollama populaires pour serveur PC
   - Scanner automatique des modèles device

---

## 🔍 PROBLÈME IDENTIFIÉ - SERVEUR OLLAMA

### Configuration actuelle (d'après SharedPreferences)

```xml
<boolean name="use_ollama_cloud" value="false" />
<string name="local_model_name">gemma3-270m.gguf</string>
<string name="ai_mode">local</string>
<string name="local_server_url">http://127.0.0.1:11434</string>
```

### ❌ Problème principal

**URL incorrecte**: `http://127.0.0.1:11434`

**Pourquoi ça ne fonctionne pas**:
- `127.0.0.1` = localhost = le device Android lui-même
- Le serveur Ollama tourne sur le **PC**, pas sur le device
- Le device essaie de se connecter à lui-même au lieu du PC
- **Résultat**: Connection refused / Timeout

**Solution immédiate**:
1. Trouver l'IP du PC sur le réseau local: `ipconfig` (Windows) → Adresse IPv4
2. Utiliser cette IP au lieu de `127.0.0.1`
3. Format correct: `http://192.168.1.XXX:11434/v1/chat/completions`

---

## 📐 ARCHITECTURE ACTUELLE

### 1. Serveur Ollama Local (PC)

**Fonctionnement**:
- Serveur Ollama installé sur le PC (Windows/Mac/Linux)
- Expose API compatible OpenAI sur port 11434
- Modèles Ollama installés sur PC via `ollama pull ...`
- L'app Android se connecte via WiFi au serveur PC

**Configuration**:
- `local_server_url`: URL du serveur PC (ex: `http://192.168.1.100:11434/v1/chat/completions`)
- `local_model_name`: Nom du modèle Ollama sur PC (ex: `llama3.2`, `mistral`)

### 2. Modèles Locaux (Device)

**Fonctionnement**:
- Fichiers GGUF stockés sur le device Android
- Localisation: `/storage/emulated/0/ChatAI-Files/models/*.gguf`
- Modèle actuel: `gemma3-270m.gguf` (278 MB)

**Note**: Actuellement, les modèles device ne sont **pas** utilisés directement. Le code fait référence à `gemma3-270m.gguf` mais le serveur Ollama PC doit avoir ce modèle installé.

---

## 🏗️ PLAN DE REFONTE - STRUCTURE DÉTAILLÉE

### Phase 1: Structure HTML (index.html)

#### Section 1: Serveur Ollama (PC)

```html
<div class="panel-section">
    <h4>🖥️ Serveur Ollama (PC)</h4>
    <p class="help-text">
        Le serveur Ollama doit tourner sur votre PC. 
        Trouvez l'IP de votre PC avec <code>ipconfig</code> (Windows).
    </p>
    
    <div class="form-grid">
        <label>
            URL du serveur
            <input type="text" id="configLocalServerUrl" 
                   placeholder="http://192.168.1.100:11434/v1/chat/completions">
            <small class="help-text">
                Format: <code>http://VOTRE_IP:11434/v1/chat/completions</code>
            </small>
        </label>
        
        <label>
            Statut du serveur
            <div id="localServerStatus" class="status-indicator">
                <span class="status-dot"></span>
                <span class="status-text">Non testé</span>
            </div>
        </label>
    </div>
    
    <div class="form-grid">
        <label>
            Modèle Ollama sur PC
            <select id="configLocalServerModel">
                <option value="">– Choisir –</option>
                <option value="llama3.2">Llama 3.2 (3B)</option>
                <option value="llama3.1:8b">Llama 3.1 (8B)</option>
                <option value="mistral">Mistral (7B)</option>
                <option value="gemma2:2b">Gemma 2 (2B)</option>
                <option value="qwen2.5:7b">Qwen 2.5 (7B)</option>
                <option value="phi-4">Phi-4</option>
                <option value="custom">Autre (personnalisé)</option>
            </select>
            <input type="text" id="configLocalServerModelCustom" 
                   class="custom-input hidden" 
                   placeholder="Nom du modèle Ollama">
            <small class="help-text">
                Modèle installé sur votre serveur Ollama PC.
                Vérifier avec <code>ollama list</code>.
            </small>
        </label>
    </div>
    
    <div class="button-group">
        <button class="panel-button" id="testLocalServerBtn">
            🔍 Tester connexion serveur
        </button>
        <button class="panel-button secondary" id="saveLocalServerBtn">
            💾 Sauvegarder
        </button>
    </div>
    
    <div id="localServerTestResult" class="test-result" style="display: none;">
        <!-- Résultats du test -->
    </div>
</div>
```

#### Section 2: Modèles Locaux (Device)

```html
<div class="panel-section">
    <h4>📱 Modèles Locaux (Device)</h4>
    <p class="help-text">
        Fichiers GGUF stockés sur votre device Android.
    </p>
    
    <div class="form-grid">
        <label>
            Modèles disponibles sur device
            <div id="localDeviceModelsList" class="models-list">
                <div class="model-item">
                    <span class="model-name">gemma3-270m.gguf</span>
                    <span class="model-size">278 MB</span>
                    <span class="model-status">✅ Actif</span>
                </div>
            </div>
            <small class="help-text">
                Scanner automatique depuis <code>/storage/emulated/0/ChatAI-Files/models/</code>
            </small>
        </label>
        
        <label>
            Modèle LLM actif
            <select id="configLocalDeviceModel">
                <option value="">– Aucun –</option>
                <option value="gemma3-270m.gguf" selected>gemma3-270m.gguf (278 MB)</option>
            </select>
            <small class="help-text">
                Modèle GGUF utilisé localement sur le device.
                Actuellement: <code>gemma3-270m.gguf</code>
            </small>
        </label>
    </div>
    
    <div class="button-group">
        <button class="panel-button secondary" id="refreshDeviceModelsBtn">
            🔄 Actualiser liste
        </button>
        <button class="panel-button secondary" id="saveLocalDeviceBtn">
            💾 Sauvegarder
        </button>
    </div>
</div>
```

---

### Phase 2: Fonctions JavaScript (chat-config.js)

#### Nouvelles fonctions à créer

**1. `testLocalServerConnection()`**
```javascript
async function testLocalServerConnection() {
    const url = document.getElementById('configLocalServerUrl').value;
    const model = document.getElementById('configLocalServerModel').value || 
                  document.getElementById('configLocalServerModelCustom').value;
    
    // Validation URL
    if (!url || !url.includes('://')) {
        showError('URL invalide');
        return;
    }
    
    // Appel API pour tester connexion
    // Afficher résultats dans localServerTestResult
}
```

**2. `loadLocalDeviceModels()`**
```javascript
async function loadLocalDeviceModels() {
    // Appel Android pour scanner /storage/emulated/0/ChatAI-Files/models/*.gguf
    // Afficher dans localDeviceModelsList
}
```

**3. `updateLocalServerStatus()`**
```javascript
function updateLocalServerStatus(status) {
    // status: 'connected', 'disconnected', 'unknown'
    // Mettre à jour l'indicateur visuel
}
```

**4. `validateLocalServerUrl(url)`**
```javascript
function validateLocalServerUrl(url) {
    // Vérifier format: http://IP:PORT/v1/chat/completions
    // Avertir si 127.0.0.1 détecté
}
```

#### Modifications fonctions existantes

**`saveConfigSection('local')`**:
- Séparer en `saveLocalServerConfig()` et `saveLocalDeviceConfig()`
- Sauvegarder `local_server.url` et `local_server.model` (PC)
- Sauvegarder `local_device.model` (device)

**`renderConfigForms()`**:
- Appeler `loadLocalDeviceModels()` automatiquement
- Charger statut serveur depuis Android

---

### Phase 3: Backend Android

#### Modifications `AiConfigManager.java`

**Séparer configuration**:
```java
// local_server: Configuration serveur Ollama PC
JSONObject localServer = new JSONObject();
localServer.put("url", prefs.getString("local_server_url", ""));
localServer.put("model", prefs.getString("local_server_model", ""));

// local_device: Configuration modèles device
JSONObject localDevice = new JSONObject();
localDevice.put("active_model", prefs.getString("local_device_model", "gemma3-270m.gguf"));
localDevice.put("models_list", scanDeviceModels()); // Scanner automatique
```

**Nouvelle fonction `scanDeviceModels()`**:
```java
private JSONArray scanDeviceModels() {
    // Scanner /storage/emulated/0/ChatAI-Files/models/*.gguf
    // Retourner liste avec noms et tailles
}
```

#### Nouvelle API dans `WebAppInterface.java`

```java
@JavascriptInterface
public String scanLocalDeviceModels() {
    // Scanner modèles GGUF sur device
    // Retourner JSON avec liste modèles
}
```

---

## 📋 CHECKLIST D'IMPLÉMENTATION

### Étape 1: Correction problème Ollama (URGENT)

- [ ] **Corriger placeholder URL**
  - Avant: `http://127.0.0.1:11434`
  - Après: `http://192.168.1.XXX:11434/v1/chat/completions`
  
- [ ] **Ajouter message d'aide**
  - "⚠️ N'utilisez pas 127.0.0.1. Trouvez l'IP de votre PC avec `ipconfig` (Windows)"
  
- [ ] **Ajouter validation URL**
  - Détecter si `127.0.0.1` est utilisé → Avertir
  - Vérifier format: `http://IP:PORT/v1/chat/completions`

### Étape 2: Structure HTML

- [ ] **Créer Section 1: Serveur Ollama (PC)**
  - Input URL avec placeholder correct
  - Select modèle Ollama (modèles populaires)
  - Indicateur statut serveur
  - Bouton "Tester connexion"
  - Zone résultats test

- [ ] **Créer Section 2: Modèles Locaux (Device)**
  - Liste modèles GGUF (scanner automatique)
  - Select modèle actif
  - Bouton "Actualiser liste"

### Étape 3: Fonctions JavaScript

- [ ] **Implémenter `testLocalServerConnection()`**
  - Validation URL
  - Appel API Android
  - Affichage résultats

- [ ] **Implémenter `loadLocalDeviceModels()`**
  - Appel API Android pour scanner
  - Afficher dans liste

- [ ] **Implémenter `updateLocalServerStatus()`**
  - Indicateur visuel (vert/rouge)
  - Message statut

- [ ] **Implémenter `validateLocalServerUrl()`**
  - Détection 127.0.0.1
  - Validation format

- [ ] **Modifier `saveConfigSection('local')`**
  - Séparer serveur PC et device

### Étape 4: Backend Android

- [ ] **Modifier `AiConfigManager.java`**
  - Séparer `local_server` (PC) et `local_device` (device)
  - Créer fonction `scanDeviceModels()`

- [ ] **Ajouter API dans `WebAppInterface.java`**
  - `scanLocalDeviceModels()` - Scanner modèles GGUF
  - `testLocalServerConnection()` - Tester connexion Ollama PC

- [ ] **Modifier `KittAIService.kt`**
  - Utiliser `local_server.url` et `local_server.model` (PC)
  - Respecter configuration utilisateur

### Étape 5: Tests

- [ ] Tester connexion serveur Ollama PC
- [ ] Tester scanner modèles device
- [ ] Tester sauvegarde/chargement configuration
- [ ] Tester validation URL
- [ ] Tester affichage statut

---

## 🎨 AMÉLIORATIONS UI/UX

### Indicateur de statut serveur

**Visuel**:
```
🟢 Serveur accessible (192.168.1.100:11434)
🔴 Serveur inaccessible (Timeout)
🟡 Statut inconnu (non testé)
```

### Messages d'aide contextuels

**Pour URL**:
- ✅ Bon: "Format correct: http://192.168.1.100:11434/v1/chat/completions"
- ⚠️ Avertissement: "127.0.0.1 ne fonctionnera pas depuis un device Android. Utilisez l'IP de votre PC."

**Pour modèle PC**:
- "Listez vos modèles Ollama avec `ollama list` sur votre PC"

**Pour modèles device**:
- "Modèles GGUF stockés sur votre device Android"

### Validation en temps réel

- Valider URL lors de la saisie
- Afficher erreur si format incorrect
- Suggérer correction si 127.0.0.1 détecté

---

## 🔗 MODÈLES OLLAMA POPULAIRES (Pour Select)

**Légers (2-3B)**:
- `gemma2:2b` (2B)
- `llama3.2` (3B)
- `phi-4` (4B)

**Moyens (7-8B)**:
- `mistral` (7B)
- `llama3.1:8b` (8B)
- `qwen2.5:7b` (7B)

**Gros (13B+)**:
- `llama3.1:70b` (70B)
- `mistral-nemo` (12B)

**Option**: "Autre (personnalisé)" pour modèles custom

---

## 📝 FICHIERS À MODIFIER

### Frontend (Webapp)

1. `index.html`
   - Restructurer onglet Local (lignes 1496-1517)
   - Ajouter Section Serveur Ollama (PC)
   - Ajouter Section Modèles Locaux (Device)
   - Ajouter bouton "Tester connexion"

2. `chat-config.js`
   - Ajouter `testLocalServerConnection()`
   - Ajouter `loadLocalDeviceModels()`
   - Ajouter `updateLocalServerStatus()`
   - Modifier `saveConfigSection('local')`
   - Modifier `renderConfigForms()`

### Backend (Android)

3. `AiConfigManager.java`
   - Séparer `local_server` (PC) et `local_device` (device)
   - Ajouter `scanDeviceModels()`

4. `WebAppInterface.java`
   - Ajouter `@JavascriptInterface scanLocalDeviceModels()`
   - Ajouter `@JavascriptInterface testLocalServerConnection()`

5. `KittAIService.kt` (si nécessaire)
   - Vérifier utilisation de `local_server_url` et `local_model_name`

---

## ⚠️ POINTS D'ATTENTION

1. **Compatibilité**: Préserver la compatibilité avec les configurations existantes
2. **Migration**: Migrer automatiquement l'ancienne config vers la nouvelle structure
3. **Validation**: Valider URL avant sauvegarde
4. **Erreurs**: Messages d'erreur clairs et actionnables
5. **Performance**: Scanner modèles device ne doit pas bloquer l'UI

---

## 🚀 ORDRE D'IMPLÉMENTATION

### Priorité 1: Correction problème Ollama (URGENT)

1. Corriger placeholder URL
2. Ajouter message d'aide
3. Ajouter validation URL
4. **Tester** que le serveur Ollama fonctionne

### Priorité 2: Refonte UI

5. Restructurer HTML (2 sections)
6. Ajouter bouton "Tester connexion"
7. Ajouter indicateur statut
8. **Tester** UI

### Priorité 3: Fonctions JavaScript

9. Implémenter `testLocalServerConnection()`
10. Implémenter `loadLocalDeviceModels()`
11. Modifier `saveConfigSection()`
12. **Tester** fonctions

### Priorité 4: Backend Android

13. Modifier `AiConfigManager.java`
14. Ajouter API `WebAppInterface.java`
15. **Tester** intégration complète

---

## ✅ CRITÈRES DE VALIDATION

1. **Serveur Ollama fonctionne**:
   - URL correcte acceptée
   - Connexion réussie
   - Test de connexion affiche "✅ Serveur accessible"

2. **UI claire**:
   - Distinction claire entre Serveur PC et Modèles Device
   - Messages d'aide compréhensibles
   - Validation URL en temps réel

3. **Configuration sauvegardée**:
   - Configuration serveur PC sauvegardée
   - Configuration modèles device sauvegardée
   - Chargement correct au redémarrage

4. **Modèles détectés**:
   - Scanner trouve `gemma3-270m.gguf`
   - Affichage correct dans liste

---

## 📝 NOTES IMPORTANTES

- **NE PAS modifier** l'onglet Local avant validation de ce plan
- **Tester** chaque étape avant de passer à la suivante
- **Migrer** automatiquement l'ancienne configuration
- **Documenter** les changements dans les commits

---

## 🔗 RÉFÉRENCES

- `extras/v3/index.html` (lignes 1496-1517) - Onglet Local actuel
- `extras/v3/chat.js` (lignes 472-476, 829-833) - Logique Local
- `AiConfigManager.java` (lignes 322-435) - Gestion config
- `index.html` (lignes 150-254) - Onglet Cloud (référence pour bouton test)
- `docs/AUDIT_COMPLET_ONGLET_CONFIGURATION.md` - Audit complet


