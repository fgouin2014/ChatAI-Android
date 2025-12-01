# 🔧 Mode 100% Hugging Face - Configuration complète

**Date:** 2025-01-27  
**Objectif:** Configurer ChatAI pour utiliser Hugging Face pour TOUT (embeddings + LLM)

---

## 📋 VUE D'ENSEMBLE

**Mode 100% Hugging Face:**
- ✅ **Embeddings:** Hugging Face Inference API (`sentence-transformers/all-MiniLM-L6-v2`)
- ✅ **LLM:** Hugging Face Inference API (modèles comme `gpt2`, `SmolLM3-3B`, etc.)
- ✅ **RAG:** Recherche sémantique avec embeddings Hugging Face
- ⚠️ **Nécessite:** Clé API Hugging Face + connexion internet

---

## 🔑 PRÉREQUIS

### 1. Clé API Hugging Face

1. Aller sur https://huggingface.co/settings/tokens
2. Créer un token (type: **Read** suffit)
3. Copier le token

### 2. Configuration dans l'app

**Option A: Via l'interface**
1. Ouvrir ChatAI
2. Aller dans **Configuration** → **API Keys**
3. Coller la clé API Hugging Face
4. Sauvegarder

**Option B: Via SharedPreferences (script)**
```bash
# Activer mode Hugging Face complet
adb shell "run-as com.chatai sh -c 'cd /data/data/com.chatai/shared_prefs && cat chatai_ai_config.xml'"
```

---

## ⚙️ CONFIGURATION REQUISE

### Paramètres SharedPreferences

Pour activer le mode 100% Hugging Face, configurer:

```xml
<!-- Mode Cloud (nécessaire pour activer Hugging Face) -->
<boolean name="use_ollama_cloud" value="true" />

<!-- RAG activé avec Hugging Face -->
<boolean name="rag_enabled" value="true" />
<boolean name="rag_use_huggingface" value="true" />

<!-- Modèle d'embedding Hugging Face -->
<string name="hf_embedding_model">sentence-transformers/all-MiniLM-L6-v2</string>
```

**Note:** Le code actuel active Hugging Face pour embeddings seulement si `use_ollama_cloud = true`. Pour un mode 100% Hugging Face, il faudrait modifier le code.

---

## 🔄 COMPORTEMENT ACTUEL

### Embeddings (RAG)

**Actuellement:**
1. ✅ ONNX local (priorité 1, si disponible)
2. ⚠️ Hugging Face (si `use_ollama_cloud = true` + `rag_use_huggingface = true`)
3. ⚠️ Ollama local (fallback)

**Pour mode 100% Hugging Face:**
- Désactiver ONNX (retirer `model.onnx` ou modifier priorité)
- Activer `use_ollama_cloud = true`
- Activer `rag_use_huggingface = true`

### LLM (Génération de texte)

**Actuellement:**
1. Ollama Cloud (si `use_ollama_cloud = true`)
2. Ollama Local (si `use_ollama_cloud = false`)
3. Hugging Face (fallback seulement, modèle `gpt2`)

**Pour mode 100% Hugging Face:**
- ⚠️ **NON IMPLÉMENTÉ** - Hugging Face est seulement un fallback
- Il faudrait modifier `KittAIService.kt` pour utiliser Hugging Face en priorité

---

## 🛠️ MODIFICATIONS NÉCESSAIRES

### 1. Modifier EmbeddingService pour prioriser Hugging Face

**Fichier:** `EmbeddingService.kt`

**Modification:**
```kotlin
suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
    // ⭐ MODE HUGGING FACE: Vérifier si mode 100% Hugging Face activé
    val useHuggingFaceOnly = sharedPreferences.getBoolean("use_huggingface_only", false)
    
    if (useHuggingFaceOnly) {
        val huggingFaceApiKey = keyring.getApiKey("huggingface")?.trim()
        if (!huggingFaceApiKey.isNullOrEmpty()) {
            return@withContext embedWithHuggingFace(text, huggingFaceApiKey)
        }
    }
    
    // Sinon, logique normale (ONNX → HuggingFace → Ollama)
    // ...
}
```

### 2. Modifier KittAIService pour prioriser Hugging Face LLM

**Fichier:** `KittAIService.kt`

**Modification:**
```kotlin
suspend fun processUserInput(userInput: String): String {
    val useHuggingFaceOnly = sharedPreferences.getBoolean("use_huggingface_only", false)
    
    if (useHuggingFaceOnly) {
        val apiKey = keyring.getApiKey("huggingface")?.trim()
        if (!apiKey.isNullOrEmpty()) {
            val response = tryHuggingFaceLLM(userInput, apiKey)
            if (response != null) {
                return response
            }
        }
    }
    
    // Sinon, logique normale (Ollama Cloud → Ollama Local → HuggingFace fallback)
    // ...
}
```

### 3. Ajouter modèle LLM Hugging Face configurable

**Fichier:** `KittAIService.kt`

**Ajouter:**
```kotlin
companion object {
    // Modèles Hugging Face disponibles
    private const val DEFAULT_HF_LLM_MODEL = "gpt2"
    // Alternatives: "HuggingFaceTB/SmolLM3-3B", "distilgpt2", etc.
}

private suspend fun tryHuggingFaceLLM(userInput: String, apiKey: String): String? {
    val hfModel = sharedPreferences.getString("hf_llm_model", DEFAULT_HF_LLM_MODEL)
        ?: DEFAULT_HF_LLM_MODEL
    
    val url = HUGGINGFACE_API_URL + hfModel
    
    // Appel API Hugging Face pour génération texte
    // ...
}
```

---

## 📝 SCRIPT DE CONFIGURATION

Créer un script PowerShell pour activer le mode 100% Hugging Face:

```powershell
# activer_mode_huggingface.ps1
Write-Host "Configuration mode 100% Hugging Face..." -ForegroundColor Cyan

# 1. Vérifier clé API
$hfKey = Read-Host "Entrez votre clé API Hugging Face"
if ([string]::IsNullOrWhiteSpace($hfKey)) {
    Write-Host "❌ Clé API requise" -ForegroundColor Red
    exit 1
}

# 2. Configurer SharedPreferences (nécessite modification app)
Write-Host "⚠️  Configuration manuelle requise dans l'app:" -ForegroundColor Yellow
Write-Host "   1. Configuration → API Keys → Hugging Face: $hfKey" -ForegroundColor Gray
Write-Host "   2. Configuration → Local → Mode: Cloud" -ForegroundColor Gray
Write-Host "   3. Configuration → RAG → Activer RAG" -ForegroundColor Gray
Write-Host "   4. Configuration → RAG → Utiliser Hugging Face: Oui" -ForegroundColor Gray

Write-Host ""
Write-Host "✅ Mode Hugging Face configuré (après modifications manuelles)" -ForegroundColor Green
```

---

## 🧪 TEST

### 1. Tester embeddings Hugging Face

```bash
# Vérifier logs
adb logcat | Select-String "Hugging Face embedding"
```

**Résultat attendu:**
```
EmbeddingService: Generating embedding via Hugging Face: sentence-transformers/all-MiniLM-L6-v2
EmbeddingService: ✅ Hugging Face embedding generated: 384 dimensions
```

### 2. Tester LLM Hugging Face

**Actuellement:** Hugging Face LLM est seulement fallback, donc difficile à tester sans faire échouer Ollama.

**Pour tester:**
1. Désactiver Ollama Cloud/Local temporairement
2. Faire une requête
3. Vérifier logs: `adb logcat | Select-String "Hugging Face|gpt2"`

---

## ⚠️ LIMITATIONS ACTUELLES

### 1. LLM Hugging Face = Fallback seulement

**Problème:** Hugging Face LLM n'est utilisé que si Ollama échoue.

**Solution:** Modifier `KittAIService.kt` pour ajouter un mode "Hugging Face prioritaire".

### 2. Embeddings Hugging Face = Seulement si Ollama Cloud

**Problème:** Hugging Face embeddings activés seulement si `use_ollama_cloud = true`.

**Solution:** Ajouter option `use_huggingface_only` pour forcer Hugging Face même sans Ollama Cloud.

### 3. Modèles LLM limités

**Problème:** Seul `gpt2` est configuré (petit modèle, qualité limitée).

**Solution:** Ajouter support pour modèles plus grands (`SmolLM3-3B`, `Llama-2-7B`, etc.).

---

## ✅ RÉSUMÉ

**Pour activer mode 100% Hugging Face:**

1. ✅ **Clé API:** Configurer dans l'app
2. ✅ **Embeddings:** Activer `use_ollama_cloud = true` + `rag_use_huggingface = true`
3. ⚠️ **LLM:** Actuellement fallback seulement (nécessite modification code)

**Modifications code nécessaires:**
- Ajouter option `use_huggingface_only` dans SharedPreferences
- Modifier `EmbeddingService.kt` pour prioriser Hugging Face
- Modifier `KittAIService.kt` pour utiliser Hugging Face LLM en priorité
- Ajouter configuration modèle LLM Hugging Face

**Souhaitez-vous que je modifie le code pour activer le mode 100% Hugging Face?**

