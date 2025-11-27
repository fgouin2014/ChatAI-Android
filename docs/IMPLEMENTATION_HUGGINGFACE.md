# Implémentation Hugging Face - Configuration Webapp

## ✅ Modifications effectuées

### 1. Ajout du Provider Hugging Face dans le spinner

**Fichier:** `app/src/main/assets/webapp/index.html` (ligne 162)

```html
<option value="huggingface">Hugging Face</option>
```

Le provider "Hugging Face" est maintenant disponible dans le spinner, entre "Perplexity" et "Autre (personnalisé)".

### 2. Labels dynamiques selon le provider

**Fichier:** `app/src/main/assets/webapp/index.html` (lignes 168-170)

Les labels et liens d'aide sont maintenant dynamiques:
- **Label:** `configCloudApiKeyLabel` - Change selon le provider
- **Placeholder:** `configCloudApiKey` - Change selon le provider
- **Lien d'aide:** `configCloudApiKeyLink` - Pointe vers la bonne page selon le provider

**Providers supportés:**
- **Hugging Face:** `huggingface.co/settings/tokens`
- **Ollama:** `ollama.com/account`
- **OpenAI:** `platform.openai.com/api-keys`
- **Groq:** `console.groq.com/keys`
- **Perplexity:** `perplexity.ai/settings/api`

### 3. Fonction de test Hugging Face

**Fichier:** `app/src/main/assets/webapp/index.html` (ligne ~1497)

Nouvelle fonction `testHuggingFaceConnection()` qui:
1. Récupère et nettoie la clé API
2. Teste la connexion avec l'API Hugging Face Inference (`router.huggingface.co/hf-inference`)
3. Utilise le modèle `gpt2` pour un test rapide
4. Affiche les résultats dans `cloudTestResult` (même zone que Ollama)
5. Gère les erreurs: 401 (Unauthorized), 403 (Forbidden), 429 (Rate Limit)

**Endpoint testé:**
```
POST https://router.huggingface.co/hf-inference/models/gpt2
Headers:
  Authorization: Bearer {apiKey}
  Content-Type: application/json
Body:
  {
    "inputs": "Hello",
    "parameters": {
      "max_new_tokens": 5,
      "return_full_text": false
    }
  }
```

**Note:** L'ancien endpoint `api-inference.huggingface.co` n'est plus supporté. Tous les endpoints Hugging Face utilisent maintenant `router.huggingface.co/hf-inference`.

### 4. Router de test selon le provider

**Fichier:** `app/src/main/assets/webapp/index.html` (ligne ~1326)

Le bouton "🧪 Tester la connexion" route maintenant automatiquement selon le provider sélectionné:
- **Hugging Face:** Appelle `testHuggingFaceConnection()`
- **Ollama:** Appelle `testOllamaCloudConnection()`
- **Autres:** Affiche un message d'alerte

### 5. Event listener pour mise à jour dynamique de l'UI

**Fichier:** `app/src/main/assets/webapp/index.html` (ligne ~1330)

Fonction `updateCloudProviderUI()` qui:
- Écoute les changements du spinner `configCloudProvider`
- Met à jour automatiquement les labels, placeholders et liens d'aide
- S'exécute au chargement de la page pour initialiser l'UI

## 🧪 Test de la connexion Hugging Face

### Prérequis
1. Clé API Hugging Face valide
   - Obtenir sur: https://huggingface.co/settings/tokens
   - Type: **Read** ou **Write** (Read suffit pour le test)

### Étapes de test
1. Ouvrir la webapp → Onglet **Configuration** → Tab **Cloud**
2. Sélectionner **"Hugging Face"** dans le spinner Provider
3. Observer que les labels changent automatiquement:
   - Label: "🔑 API Key Hugging Face"
   - Placeholder: "Entrez votre clé API Hugging Face"
   - Lien: "huggingface.co/settings/tokens"
4. Entrer la clé API Hugging Face
5. Cliquer sur **"🧪 Tester la connexion"**
6. Vérifier le résultat:
   - ✅ **Succès:** "Connexion OK (XXXms)" avec réponse du modèle
   - ❌ **Erreur:** Message d'erreur spécifique (401, 403, 429, etc.)

### Résultats attendus

**Succès:**
```
✅ Connexion OK (XXXms)
Modèle testé: gpt2
Réponse: "[texte généré]"
✨ Clé API Hugging Face valide
```

**Erreur 401 (Unauthorized):**
```
❌ Non autorisé (401)
Clé API invalide ou expirée.
[Lien vers huggingface.co/settings/tokens]
```

**Erreur 403 (Forbidden):**
```
❌ Accès refusé (403)
Votre clé API n'a pas les permissions nécessaires.
```

**Erreur 429 (Rate Limit):**
```
⚠️ Limite de taux (429)
Trop de requêtes. Attendez quelques instants avant de réessayer.
```

## 📝 Notes techniques

### API Hugging Face Inference
- **Base URL:** `https://router.huggingface.co/hf-inference` (nouveau endpoint, remplace `api-inference.huggingface.co`)
- **Authentification:** Bearer token dans le header `Authorization`
- **Modèle de test:** `gpt2` (petit modèle, réponse rapide)
- **Alternative:** Peut être modifié pour tester d'autres modèles
- **Migration:** Tous les endpoints Hugging Face dans l'app ont été mis à jour vers le nouveau routeur

### Compatibilité avec chat-config.js
Le système existant dans `chat-config.js` gère automatiquement le provider "huggingface" car:
- `getSelectValue()` récupère simplement la valeur du spinner
- `saveConfigSection('cloud')` sauvegarde `cfg.cloud.provider = 'huggingface'`
- Aucune modification nécessaire dans `chat-config.js`

### Prochaines étapes possibles
1. **Liste des modèles Hugging Face:** Implémenter un équivalent de `listCloudModels()` pour Hugging Face
2. **Sélection de modèle:** Ajouter un dropdown avec modèles Hugging Face populaires
3. **Intégration complète:** Utiliser Hugging Face pour les requêtes chat (nécessite modification de `BidirectionalBridge` ou `OllamaThinkingService`)

## 🔗 Références
- **Documentation Hugging Face Inference API:** https://huggingface.co/docs/api-inference/index
- **Obtenir une clé API:** https://huggingface.co/settings/tokens
- **Modèles disponibles:** https://huggingface.co/models

