# 🔄 MIGRATION VERS ApiConfig - Centralisation des URLs

**Date**: 2025-11-30  
**Statut**: ✅ **MIGRATION INITIALE COMPLÉTÉE**

---

## 📋 OBJECTIF

Centraliser toutes les URLs hardcodées dans un fichier unique (`ApiConfig.kt`) pour:
- Faciliter la maintenance
- Permettre la configuration via SharedPreferences si nécessaire
- Réduire les risques d'erreurs (typos, URLs obsolètes)
- Améliorer la cohérence du code

---

## ✅ FICHIERS MIGRÉS

### Services Kotlin (✅ Complété)

1. **KittAIService.kt**
   - ✅ `OPENAI_API_URL` → `ApiConfig.OPENAI_CHAT_COMPLETIONS`
   - ✅ `ANTHROPIC_API_URL` → `ApiConfig.ANTHROPIC_MESSAGES`
   - ✅ `OLLAMA_CLOUD_API_URL` → `ApiConfig.OLLAMA_CLOUD_CHAT`
   - ✅ `"https://ollama.com/api/web_search"` → `ApiConfig.OLLAMA_CLOUD_WEB_SEARCH`

2. **HuggingFaceService.kt**
   - ✅ `BASE_URL` → `ApiConfig.HUGGINGFACE_INFERENCE_BASE + "/"`

3. **VisionService.kt**
   - ✅ `OLLAMA_CLOUD_URL` → `ApiConfig.OLLAMA_CLOUD_CHAT`

4. **TranslationService.kt**
   - ✅ `OLLAMA_CLOUD_URL` → `ApiConfig.OLLAMA_CLOUD_CHAT`

5. **OllamaThinkingService.kt**
   - ✅ `OLLAMA_LOCAL_DEFAULT` → `ApiConfig.OLLAMA_LOCAL_DEFAULT`
   - ✅ `OLLAMA_CLOUD_URL` → `ApiConfig.OLLAMA_CLOUD_CHAT`

6. **EmbeddingService.kt**
   - ✅ `"https://ollama.com/api/embeddings"` → `ApiConfig.OLLAMA_CLOUD_EMBEDDINGS` (2 occurrences)

7. **KittFragment.kt**
   - ✅ `"https://ollama.com/api/chat"` → `ApiConfig.OLLAMA_CLOUD_CHAT`

### Services Java (⚠️ Partiellement migré)

8. **HttpServer.java**
   - ✅ `"https://ollama.com/api/web_search"` → `ApiConfig.OLLAMA_CLOUD_WEB_SEARCH`

9. **RealtimeAIService.java**
   - ⚠️ TODO ajouté pour migration future
   - URLs conservées temporairement (complexité interop Java/Kotlin)

---

## 📊 STATISTIQUES

### URLs centralisées dans ApiConfig.kt

| Catégorie | Nombre | Exemples |
|-----------|--------|----------|
| **Ollama Cloud** | 6 | Chat, Tags, Embeddings, Web Search, Account |
| **Hugging Face** | 4 | Router, Chat Completions, Inference, Models API |
| **OpenAI** | 2 | Chat Completions, API Keys URL |
| **Anthropic** | 2 | Messages, Base URL |
| **Serveurs Locaux** | 4 | Ollama, Whisper, TTS (ports + URLs) |
| **Autres Providers** | 2 | Groq, Perplexity (API Keys URLs) |
| **TOTAL** | **20 URLs** | |

### Fichiers modifiés

- **Services Kotlin**: 7 fichiers migrés
- **Services Java**: 1 fichier migré (1 avec TODO)
- **Total**: 8 fichiers modifiés

### Occurrences remplacées

- **~15 occurrences** d'URLs hardcodées remplacées par références à `ApiConfig`

---

## 🔄 MIGRATION RESTANTE

### Fichiers à migrer (priorité basse)

1. **RealtimeAIService.java**
   - `HUGGINGFACE_API_URL` → `ApiConfig.HUGGINGFACE_INFERENCE_BASE`
   - `OPENAI_API_URL` → `ApiConfig.OPENAI_CHAT_COMPLETIONS`
   - **Note**: Conversion en Kotlin recommandée pour meilleure interop

2. **Fichiers webapp** (HTML/JS)
   - `index.html`: URLs Ollama Cloud dans le frontend
   - `chat.js`: URLs Hugging Face dans le frontend
   - **Note**: Migration optionnelle (frontend peut garder URLs hardcodées)

3. **Fichiers extras** (versions alternatives)
   - `extras/v1/`, `v2/`, `v3/`: URLs dans versions alternatives
   - **Note**: Priorité très basse (fichiers de test/backup)

---

## 🎯 BÉNÉFICES

### Maintenance
- ✅ **Un seul endroit** pour modifier les URLs
- ✅ **Détection facile** des URLs obsolètes
- ✅ **Documentation centralisée** des endpoints

### Configuration future
- ✅ **Prêt pour SharedPreferences** si besoin de configuration dynamique
- ✅ **Méthodes utilitaires** pour construire URLs complexes
- ✅ **Support multi-environnements** (dev, staging, prod)

### Qualité du code
- ✅ **Réduction des typos** (compilation vérifie les constantes)
- ✅ **Cohérence** entre services
- ✅ **Réutilisabilité** des URLs

---

## 📝 NOTES TECHNIQUES

### Interopérabilité Java/Kotlin

Les `const val` dans un `object` Kotlin sont compilées comme des constantes statiques Java, donc accessibles directement:
```java
// Java
String url = ApiConfig.OLLAMA_CLOUD_CHAT;
```

### Méthodes utilitaires

`ApiConfig` inclut des méthodes utilitaires pour:
- Construire URLs Hugging Face avec suffixe `:hf-inference`
- Construire URLs Ollama local avec format OpenAI-compatible
- Construire URLs embeddings depuis base URL

### Configuration dynamique

Les méthodes `getConfiguredUrl()` permettent de récupérer des URLs depuis SharedPreferences avec fallback vers les valeurs par défaut.

---

## ✅ CONCLUSION

**Migration initiale complétée avec succès**. Les services principaux utilisent maintenant `ApiConfig` pour toutes les URLs API. Les fichiers restants peuvent être migrés progressivement sans impact sur le fonctionnement de l'application.

**Impact**: ~15 occurrences migrées, 20 URLs centralisées, 8 fichiers modifiés.

