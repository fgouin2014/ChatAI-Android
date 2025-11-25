# Test Hugging Face Configuration

## Test rapide dans la console

Ouvrez la console JavaScript dans l'app et exécutez ces commandes:

### 1. Vérifier que le champ existe
```javascript
const field = document.getElementById('configHuggingFaceApiKey');
console.log('Champ existe:', field !== null);
console.log('Valeur actuelle:', field?.value || '(vide)');
```

### 2. Vérifier que la référence existe dans core
```javascript
const core = window.chatCore;
console.log('core existe:', core !== null && core !== undefined);
console.log('configHuggingFaceApiKey existe:', core?.configHuggingFaceApiKey !== null);
```

### 3. Tester la sauvegarde manuelle
```javascript
const core = window.chatCore;
const config = window.chatConfig;

// Vérifier que tout est initialisé
if (!core || !config) {
    console.error('core ou config non initialisé!');
} else if (!core.configHuggingFaceApiKey) {
    console.error('configHuggingFaceApiKey non initialisé!');
} else {
    // Mettre une valeur de test
    core.configHuggingFaceApiKey.value = 'hf_test123456789';
    console.log('Valeur de test mise:', core.configHuggingFaceApiKey.value);
    
    // Sauvegarder
    config.saveConfigSection('cloud').then(() => {
        console.log('✅ Sauvegarde terminée');
        console.log('Valeur dans config:', config.aiConfigObject?.cloud?.huggingfaceApiKey);
    }).catch(err => {
        console.error('❌ Erreur lors de la sauvegarde:', err);
    });
}
```

### 4. Vérifier la configuration actuelle
```javascript
const config = window.chatConfig;
console.log('Config Cloud complète:', config?.aiConfigObject?.cloud);
console.log('Clé HF actuelle:', config?.aiConfigObject?.cloud?.huggingfaceApiKey || '(non définie)');
```

### 5. Forcer le rechargement de la config
```javascript
const config = window.chatConfig;
config.loadAiConfigPreview(true).then(() => {
    console.log('✅ Config rechargée');
    config.renderConfigForms();
    console.log('✅ Formulaires rendus');
});
```

## Test via l'interface

1. **Ouvrir l'app ChatAI**
2. **Aller dans Configuration → Cloud**
3. **Vérifier que le champ "🤗 Clé API Hugging Face" est visible**
4. **Entrer une clé de test**: `hf_test123456789`
5. **Cliquer sur "💾 Sauvegarder"**
6. **Ouvrir la console et vérifier les logs `[HF]`**

## Résultats attendus

### Dans la console, vous devriez voir:
```
[HF] Chargement Hugging Face API Key...
[HF] cfg.cloud.huggingfaceApiKey: (non défini) ou hf_xxxx...
[HF] this.core.configHuggingFaceApiKey: existe
```

### Lors de la sauvegarde:
```
[HF] Sauvegarde Hugging Face API Key...
[HF] Valeur du champ: hf_t...
[HF] Nouvelle clé saisie, sauvegarde...
[HF] Clé sauvegardée dans cfg.cloud.huggingfaceApiKey
[HF] Résultat final cfg.cloud.huggingfaceApiKey: hf_t...
```

## Si rien ne fonctionne

1. **Vérifier que les fichiers sont dans l'APK**:
   - Les fichiers doivent être dans `app/src/main/assets/webapp/`
   - Recompiler l'APK: `./gradlew clean installDebug`

2. **Vider le cache du WebView**:
   - Fermer complètement l'app
   - Redémarrer l'app
   - Ou désinstaller/réinstaller l'app

3. **Vérifier les erreurs JavaScript**:
   - Ouvrir la console
   - Chercher les erreurs en rouge
   - Vérifier que tous les fichiers sont chargés

4. **Vérifier que le HTML est correct**:
   - Le champ doit être dans l'onglet Cloud
   - L'ID doit être exactement `configHuggingFaceApiKey`
   - Le champ doit être dans un `<label>` dans la section Cloud

