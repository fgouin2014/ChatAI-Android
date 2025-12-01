# Cohérence Hugging Face entre Webapp et Backend Android

**Date:** 2025-01-27  
**Objectif:** Documenter et assurer la cohérence entre le webapp (frontend) et le backend Android pour Hugging Face

---

## 📋 ARCHITECTURE

### Communication Webapp ↔ Backend Android

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Webapp JS     │────────▶│  WebAppInterface │────────▶│  KittAIService   │
│  (Frontend)     │         │     (Bridge)     │         │   (Backend)      │
└─────────────────┘         └──────────────────┘         └─────────────────┘
       │                                                          │
       │                                                          │
       │ Fallback (si backend indisponible)                      │
       │                                                          ▼
       └──────────────────────────────────────────────▶  HuggingFaceService
                                                              (Backend)
```

---

## 🔄 FLUX DE TRAITEMENT

### 1. Webapp envoie un message

**Fichier:** `chat.js` (principal)

```javascript
// Priorité 1: Backend Android (via WebAppInterface)
if (this.androidInterface?.processWithThinking) {
    this.androidInterface.processWithThinking(message, personality, enableThinking);
}
// Priorité 2: Backend Android (mode temps réel)
else if (this.androidInterface?.processAIRequestRealtime) {
    this.androidInterface.processAIRequestRealtime(enhancedMessage, personality);
}
// Priorité 3: Fallback direct Hugging Face (si backend indisponible)
else {
    const response = await this.queryHuggingFaceSecure(enhancedMessage);
    this.showSecureMessage('ai', response);
}
```

**Fichier:** `chat-messaging.js` (alternative)

```javascript
// Utilise l'API locale du serveur HTTP Android (/api/chat)
// Pas d'appel direct Hugging Face - tout passe par le backend Android
const response = await fetch('/api/chat', {
    method: 'POST',
    body: JSON.stringify({ message, model })
});
```

### 2. Backend Android traite le message

**Fichier:** `KittAIService.kt`

```kotlin
// Utilise HuggingFaceService si activé
if (huggingFaceService.isEnabledForLLM() && huggingFaceService.isConfigured()) {
    val response = huggingFaceService.generateText(userInput, systemPrompt)
    // ...
}
```

### 3. Embeddings (RAG)

**Fichier:** `EmbeddingService.kt`

```kotlin
// Utilise HuggingFaceService pour embeddings
if (huggingFaceService.isEnabledForEmbeddings() && huggingFaceService.isConfigured()) {
    val embedding = huggingFaceService.generateEmbedding(text)
    // ...
}
```

---

## 🔗 ENDPOINTS HUGGING FACE

### ✅ Endpoint unifié (Nouveau)

**URL:** `https://router.huggingface.co/hf-inference/models/{model}`

**Utilisé par:**
- ✅ `HuggingFaceService.kt` (Backend Android)
- ✅ `chat.js` (Webapp - fallback)
- ✅ `chat-messaging.js` (Webapp - fallback)
- ✅ `index.html` (Test de connexion)

### ❌ Ancien endpoint (Déprécié)

**URL:** `https://api-inference.huggingface.co/models/{model}`

**Status:** ❌ Plus utilisé (remplacé par `router.huggingface.co`)

---

## 📝 CONFIGURATION

### Backend Android

**SharedPreferences:**
```xml
<!-- Embeddings (RAG) -->
<boolean name="rag_use_huggingface" value="true" />
<string name="hf_embedding_model">sentence-transformers/all-MiniLM-L6-v2</string>

<!-- LLM -->
<boolean name="use_huggingface_llm" value="true" />
<string name="hf_llm_model">gpt2</string>
```

**Clé API:** Stockée dans `KeyringManager` (sécurisé)

### Webapp (Fallback)

**Configuration:** Via `ai_config.json` ou `androidInterface`

**Clé API:** Stockée dans la configuration webapp

---

## 🎯 COHÉRENCE ASSURÉE

### ✅ Endpoints unifiés

- **Backend:** `HuggingFaceService.kt` → `router.huggingface.co/hf-inference/models/`
- **Webapp:** `chat.js` → `router.huggingface.co/hf-inference/models/`
- **Test:** `index.html` → `router.huggingface.co/v1/chat/completions` (OpenAI-compatible)

### ✅ Format de requête unifié

**Embeddings:**
```json
{
  "inputs": "text to embed"
}
```

**LLM:**
```json
{
  "inputs": "prompt text",
  "parameters": {
    "max_length": 150,
    "temperature": 0.8,
    "return_full_text": false
  }
}
```

### ✅ Format de réponse unifié

**Embeddings:**
```json
[[0.1, 0.2, 0.3, ...]]
```

**LLM:**
```json
[{
  "generated_text": "response text"
}]
```

---

## 🔧 MODIFICATIONS APPLIQUÉES

### 1. Webapp - Mise à jour endpoint

**Avant:**
```javascript
const apiUrl = `https://api-inference.huggingface.co/models/${this.currentModel}`;
```

**Après:**
```javascript
// ⭐ NOUVEAU ENDPOINT: router.huggingface.co (cohérent avec HuggingFaceService.kt)
const apiUrl = `https://router.huggingface.co/hf-inference/models/${this.currentModel}`;
```

### 2. Backend - Service dédié

**Nouveau:** `HuggingFaceService.kt`
- Endpoint: `router.huggingface.co/hf-inference/models/`
- Configuration centralisée
- Gestion d'erreurs robuste

---

## 📊 COMPARAISON

| Aspect | Backend Android | Webapp (Fallback) |
|--------|----------------|-------------------|
| **Service** | `HuggingFaceService.kt` | `queryHuggingFaceSecure()` (chat.js uniquement) |
| **Endpoint** | `router.huggingface.co/hf-inference/models/` | `router.huggingface.co/hf-inference/models/` |
| **Configuration** | SharedPreferences + KeyringManager | `ai_config.json` ou API locale |
| **Usage** | Principal (via KittAIService) | Fallback (si backend indisponible) |
| **Embeddings** | ✅ Supporté | ❌ Non utilisé (backend uniquement) |
| **LLM** | ✅ Supporté | ✅ Supporté (fallback dans chat.js) |
| **chat-messaging.js** | ✅ Utilise `/api/chat` (backend Android) | ❌ Pas d'appel direct Hugging Face |

---

## ✅ VALIDATION

- ✅ Endpoints unifiés (`router.huggingface.co`)
- ✅ Format de requête/réponse cohérent
- ✅ Configuration centralisée (backend)
- ✅ Fallback webapp fonctionnel
- ✅ Documentation à jour

---

## 🔮 PROCHAINES ÉTAPES (OPTIONNEL)

1. **Unifier la configuration** - Webapp lit depuis SharedPreferences Android
2. **Cache partagé** - Embeddings générés par backend utilisables par webapp
3. **Mode 100% Hugging Face** - Configuration unifiée backend + webapp

