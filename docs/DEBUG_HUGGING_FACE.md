# Debug Hugging Face Configuration

## Problème
La clé API Hugging Face ne semble pas être sauvegardée ou chargée.

## Vérifications à faire

### 1. Vérifier que le champ existe dans le DOM
Ouvrez la console JavaScript et tapez:
```javascript
document.getElementById('configHuggingFaceApiKey')
```
Si cela retourne `null`, le champ n'existe pas dans le HTML.

### 2. Vérifier que le champ est référencé dans chat-core.js
Dans la console:
```javascript
window.chatCore?.configHuggingFaceApiKey
```
Si cela retourne `null`, la référence n'est pas initialisée.

### 3. Vérifier la configuration actuelle
Dans la console:
```javascript
window.chatConfig?.aiConfigObject?.cloud?.huggingfaceApiKey
```
Cela devrait afficher la clé actuelle (ou `undefined` si pas de clé).

### 4. Tester la sauvegarde manuellement
Dans la console:
```javascript
// Simuler une sauvegarde
const core = window.chatCore;
const config = window.chatConfig;
if (core.configHuggingFaceApiKey) {
    core.configHuggingFaceApiKey.value = 'hf_test123456789';
    config.saveConfigSection('cloud').then(() => {
        console.log('Sauvegarde terminée');
        console.log('Nouvelle config:', config.aiConfigObject?.cloud?.huggingfaceApiKey);
    });
}
```

### 5. Vérifier les logs de debug
Les logs `[HF]` devraient apparaître dans la console:
- `[HF] Chargement Hugging Face API Key...` - Au chargement de la config
- `[HF] Sauvegarde Hugging Face API Key...` - Lors de la sauvegarde

## Solutions possibles

### Si le champ n'existe pas
- Vérifier que `index.html` contient bien `<input id="configHuggingFaceApiKey">`
- Vérifier que le fichier est bien dans l'APK (assets/webapp/index.html)

### Si la référence n'est pas initialisée
- Vérifier que `chat-core.js` contient `this.configHuggingFaceApiKey = document.getElementById('configHuggingFaceApiKey');`
- Vérifier que `initializeDOMReferences()` est bien appelé

### Si la sauvegarde ne fonctionne pas
- Vérifier que le bouton "Sauvegarder" dans l'onglet Cloud appelle bien `saveConfigSection('cloud')`
- Vérifier les logs `[HF]` pour voir quelle branche du code est exécutée

### Si le WebView cache les fichiers
- Forcer le rechargement: fermer et rouvrir l'app
- Vider le cache du WebView (si possible)
- Recompiler l'APK pour forcer le rechargement des assets

## Structure attendue

### Dans index.html (onglet Cloud)
```html
<label>
    <span>🤗 Clé API Hugging Face</span>
    <input type="password" id="configHuggingFaceApiKey" placeholder="hf_xxxxxxxxxxxxxxxxxxxx">
</label>
```

### Dans chat-core.js
```javascript
this.configHuggingFaceApiKey = document.getElementById('configHuggingFaceApiKey');
```

### Dans chat-config.js
- Chargement: `renderConfigForms()` → section `if (cfg.cloud)`
- Sauvegarde: `saveConfigSection('cloud')` → section `case 'cloud':`

### Dans ai_config.json
```json
{
  "cloud": {
    "huggingfaceApiKey": "hf_xxxxxxxxxxxxxxxxxxxx"
  }
}
```

