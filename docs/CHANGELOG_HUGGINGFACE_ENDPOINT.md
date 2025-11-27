# Changelog - Migration Endpoint Hugging Face

## Date: 2025-01-XX

### 🔄 Migration vers le nouveau routeur Hugging Face

**Problème:** L'ancien endpoint `api-inference.huggingface.co` n'est plus supporté par Hugging Face.

**Solution:** Migration vers le nouveau endpoint `router.huggingface.co/hf-inference`.

### Fichiers modifiés

1. **`app/src/main/assets/webapp/index.html`**
   - Fonction `testHuggingFaceConnection()`: Mise à jour de l'URL de test
   - Ancien: `https://api-inference.huggingface.co/models/gpt2`
   - Nouveau: `https://router.huggingface.co/hf-inference/models/gpt2`

2. **`app/src/main/java/com/chatai/services/EmbeddingService.kt`**
   - Constante `HUGGINGFACE_API_URL` mise à jour
   - Utilisé pour les embeddings RAG avec Hugging Face

3. **`app/src/main/java/com/chatai/services/KittAIService.kt`**
   - Constante `HUGGINGFACE_API_URL` mise à jour
   - Utilisé pour les requêtes Hugging Face depuis KITT

4. **`app/src/main/java/com/chatai/RealtimeAIService.java`**
   - Constante `HUGGINGFACE_API_URL` mise à jour
   - Utilisé pour les requêtes Hugging Face en temps réel

### Format de l'URL

**Ancien format:**
```
https://api-inference.huggingface.co/models/{model}
```

**Nouveau format:**
```
https://router.huggingface.co/hf-inference/models/{model}
```

### Impact

- ✅ Tous les appels Hugging Face utilisent maintenant le nouveau routeur
- ✅ Le test de connexion dans la webapp fonctionne avec le nouvel endpoint
- ✅ Les embeddings RAG avec Hugging Face fonctionnent avec le nouvel endpoint
- ✅ Les requêtes depuis KITT fonctionnent avec le nouvel endpoint

### Notes

- Certains modèles plus anciens peuvent ne pas être disponibles sur le nouveau routeur
- Si un modèle retourne 404, essayer un modèle plus récent ou compatible
- La clé API Hugging Face reste la même, seule l'URL change

### Références

- [Discussion Hugging Face](https://discuss.huggingface.co/t/error-https-api-inference-huggingface-co-is-no-longer-supported-please-use-https-router-huggingface-co-hf-inference-instead/169870)
- [Documentation Hugging Face Inference](https://huggingface.co/docs/inference-providers/main/en/providers/hf-inference)


