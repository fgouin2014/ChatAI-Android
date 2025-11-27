# Audit Configuration - Intégration Hugging Face

## 📋 Structure actuelle de l'onglet Cloud

### 1. HTML Structure (`index.html`)

**Spinner Provider (lignes 157-165):**
```html
<select id="configCloudProvider">
    <option value="">– Choisir –</option>
    <option value="ollama">Ollama</option>
    <option value="openai">OpenAI</option>
    <option value="groq">Groq</option>
    <option value="perplexity">Perplexity</option>
    <option value="custom">Autre (personnalisé)</option>
</select>
<input type="text" id="configCloudProviderCustom" class="custom-input hidden" placeholder="Provider personnalisé">
```

**Champ API Key (lignes 167-171):**
```html
<label>
    <span style="font-weight: 500; margin-bottom: 4px;">🔑 API Key Ollama Cloud</span>
    <input type="password" id="configCloudApiKey" placeholder="Entrez votre clé API Ollama Cloud">
    <small style="font-size: 11px; color: #64748b; margin-top: 4px; display: block;">Obtenez votre clé sur <a href="https://ollama.com/account" target="_blank" style="color: #667eea;">ollama.com/account</a></small>
</label>
```

**Bouton Test (ligne 253):**
```html
<button class="panel-button" id="testCloudConnectionBtn" style="background: linear-gradient(135deg, #10b981 0%, #059669 100%);">🧪 Tester la connexion</button>
```

**Zones de résultats:**
- `cloudModelsResult` (ligne 184): Liste des modèles disponibles
- `cloudTestResult` (ligne 222): Résultat du test de connexion
  - `cloudTestStatus`: Statut (✅/❌)
  - `cloudTestDetails`: Détails du test

### 2. JavaScript - Gestion du Provider (`chat-config.js`)

**Lecture du Provider (ligne 137):**
```javascript
this.setSelectValue(this.core.configCloudProvider, this.core.configCloudProviderCustom, cfg.cloud.provider || '');
```

**Sauvegarde du Provider (ligne 427):**
```javascript
cfg.cloud.provider = this.getSelectValue(core.configCloudProvider, core.configCloudProviderCustom);
```

**Méthodes utilitaires:**
- `setSelectValue(select, customInput, valueStr)`: Définit la valeur du spinner ou du champ custom
- `getSelectValue(select, customInput)`: Récupère la valeur (spinner ou custom)

### 3. JavaScript - Test de connexion (`index.html`)

**Fonction principale: `testOllamaCloudConnection()` (ligne 1497)**

**Étapes du test:**
1. **Récupération de la clé API** (lignes 1498-1507)
   - Récupère depuis `configCloudApiKey`
   - Si masquée (contient `*`), récupère depuis `data-original-key`
   - Nettoie la clé (supprime espaces, caractères de contrôle)

2. **Validation de la clé** (lignes 1524-1547)
   - Vérifie que la clé n'est pas vide
   - Affiche erreur si manquante

3. **Test 1: Liste des modèles** (lignes 1568-1594)
   - Appel à `https://ollama.com/api/tags`
   - Headers: `Authorization: Bearer ${cleanApiKey}`
   - Parse la réponse JSON
   - Affiche les modèles dans `cloudModelsResult`
   - Peuple le dropdown `configCloudModel`

4. **Test 2: Test chat** (lignes 1617-1662)
   - Appel à `https://ollama.com/api/chat`
   - Headers: `Authorization: Bearer ${cleanApiKey}`, `Content-Type: application/json`
   - Body: JSON avec `model`, `messages`, `stream: false`, `think: true`
   - Parse la réponse et affiche le résultat

5. **Gestion des erreurs** (lignes 1679-1710)
   - `UNAUTHORIZED` (401): Clé API invalide
   - `RATE_LIMIT` (429): Limite de taux
   - `QUOTA_ERROR` (502/503): Quota dépassé
   - Autres erreurs HTTP

**Affichage des résultats:**
- Succès: Fond vert (`rgba(16, 185, 129, 0.1)`), statut `✅ Connexion OK (${elapsedTime}ms)`
- Erreur: Fond rouge (`rgba(239, 68, 68, 0.1)`), statut `❌ [Type d'erreur]`

### 4. Event Listeners (`index.html`)

**Initialisation (ligne 1326):**
```javascript
const testCloudBtn = document.getElementById('testCloudConnectionBtn');
if (testCloudBtn) {
    testCloudBtn.addEventListener('click', testOllamaCloudConnection);
}
```

## 🔍 Analyse pour Hugging Face

### Points à considérer:

1. **Spinner Provider:**
   - ✅ Ajouter `<option value="huggingface">Hugging Face</option>` après `perplexity`
   - ✅ Le système de `custom` existe déjà pour les providers non listés

2. **Champ API Key:**
   - ⚠️ Actuellement labelé "API Key Ollama Cloud"
   - ⚠️ Le `small` pointe vers `ollama.com/account`
   - 💡 **Solution:** Rendre le label et le lien dynamiques selon le provider sélectionné

3. **Test de connexion:**
   - ⚠️ Actuellement hardcodé pour Ollama (`https://ollama.com/api/tags` et `/api/chat`)
   - 💡 **Solution:** Créer une fonction générique `testProviderConnection(provider, apiKey)` qui route vers le bon test selon le provider

4. **API Hugging Face:**
   - **Endpoint de test:** `https://api-inference.huggingface.co/models` (liste des modèles)
   - **Headers:** `Authorization: Bearer ${apiKey}`
   - **Alternative:** `https://api-inference.huggingface.co/models/{model}` pour tester un modèle spécifique
   - **Note:** Hugging Face utilise l'Inference API, pas un endpoint `/chat` comme Ollama

### Structure proposée pour Hugging Face:

```javascript
async function testHuggingFaceConnection(apiKey) {
    // Test 1: Vérifier la clé API (optionnel: liste des modèles disponibles)
    // Test 2: Tester un modèle simple (ex: text-generation)
    // Afficher les résultats dans cloudTestResult
}
```

## 📝 Plan d'implémentation

### Phase 1: Ajout du Provider dans le spinner
1. Ajouter `<option value="huggingface">Hugging Face</option>` dans `index.html`
2. Aucune modification JavaScript nécessaire (le système existant gère déjà)

### Phase 2: Test de connexion Hugging Face
1. Créer `testHuggingFaceConnection(apiKey)` dans `index.html`
2. Modifier `testOllamaCloudConnection()` pour router selon le provider:
   ```javascript
   const provider = document.getElementById('configCloudProvider').value;
   if (provider === 'huggingface') {
       await testHuggingFaceConnection(cleanApiKey);
   } else if (provider === 'ollama') {
       // Test Ollama existant
   }
   ```

### Phase 3: UI dynamique (optionnel)
1. Modifier le label "API Key Ollama Cloud" pour être dynamique
2. Modifier le lien `ollama.com/account` pour pointer vers `huggingface.co/settings/tokens` si Hugging Face est sélectionné

## ✅ Checklist

- [ ] Ajouter option "Hugging Face" dans le spinner
- [ ] Créer fonction `testHuggingFaceConnection()`
- [ ] Modifier `testOllamaCloudConnection()` pour router selon provider
- [ ] Tester avec une vraie clé API Hugging Face
- [ ] (Optionnel) Rendre le label API Key dynamique
- [ ] (Optionnel) Rendre le lien d'aide dynamique


