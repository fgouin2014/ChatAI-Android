# Alternatives à Ollama pour Modèles GGUF sur Android

**Date:** 2025-12-01  
**Objectif:** Présenter les alternatives pour exécuter gemma3-270m.gguf localement sans Ollama

---

## 🎯 ALTERNATIVES PRINCIPALES

### 1. llama.cpp Direct (Recommandé) ⭐⭐⭐⭐⭐

**Description:** Moteur d'inférence C++ optimisé, utilisé par Ollama en interne

**Avantages:**
- ✅ Support GGUF natif (format exact de vos modèles)
- ✅ Performance excellente (optimisé C++)
- ✅ Pas de serveur HTTP (appel direct)
- ✅ Léger et rapide
- ✅ Utilisé par Ollama, LM Studio, GPT4All
- ✅ Open source, bien maintenu

**Inconvénients:**
- ⚠️ Nécessite compilation JNI (native)
- ⚠️ Intégration Android nécessite NDK

**Intégration Android:**
```kotlin
// LocalGGUFService.kt
class LocalGGUFService(context: Context) {
    // JNI bindings vers llama.cpp
    private external fun initModel(modelPath: String): Long
    private external fun generate(prompt: String, context: Long): String
    private external fun freeModel(context: Long)
    
    suspend fun processUserInput(prompt: String): String {
        val modelPath = "/storage/emulated/0/ChatAI-Files/models/gemma3-270m.gguf"
        val modelContext = initModel(modelPath)
        val response = generate(prompt, modelContext)
        freeModel(modelContext)
        return response
    }
}
```

**Ressources:**
- GitHub: https://github.com/ggerganov/llama.cpp
- Android bindings: https://github.com/ggerganov/llama.cpp/tree/master/bindings/android
- Documentation: https://github.com/ggerganov/llama.cpp/blob/master/README.md

**Recommandation:** ⭐⭐⭐⭐⭐ **MEILLEURE OPTION**

---

### 2. ONNX Runtime (Alternative) ⭐⭐⭐

**Description:** Runtime déjà utilisé dans votre projet pour embeddings/vision

**Avantages:**
- ✅ Déjà intégré dans votre projet
- ✅ Pas de nouvelle dépendance
- ✅ Performance correcte

**Inconvénients:**
- ❌ Nécessite conversion GGUF → ONNX (complexe)
- ❌ Performance peut être inférieure à llama.cpp
- ❌ Format différent (pas GGUF natif)

**Conversion nécessaire:**
- GGUF → ONNX (outils: `llama.cpp/convert-llama-gguf-to-onnx.py`)
- Perte potentielle de performance
- Processus complexe

**Recommandation:** ⭐⭐⭐ **Si vous voulez éviter nouvelle dépendance**

---

### 3. MLX (Apple Silicon uniquement) ❌

**Description:** Framework Apple pour ML sur Apple Silicon

**Inconvénients:**
- ❌ **NON compatible Android** (Apple uniquement)
- ❌ Nécessite puces Apple Silicon

**Recommandation:** ❌ **NON APPLICABLE pour Android**

---

### 4. GPT4All (Desktop uniquement) ❌

**Description:** Application desktop pour modèles locaux

**Inconvénients:**
- ❌ **NON compatible Android** (Windows/Mac/Linux)
- ❌ Pas de bibliothèque Android

**Recommandation:** ❌ **NON APPLICABLE pour Android**

---

### 5. LocalAI (Serveur HTTP) ⚠️

**Description:** Serveur HTTP compatible OpenAI

**Avantages:**
- ✅ API compatible OpenAI
- ✅ Support GGUF via llama.cpp

**Inconvénients:**
- ⚠️ Nécessite serveur HTTP (comme Ollama)
- ⚠️ Plus complexe que llama.cpp direct
- ⚠️ Overhead HTTP inutile

**Recommandation:** ⚠️ **Pas optimal (serveur HTTP inutile)**

---

### 6. Hugging Face Transformers (Android) ⚠️

**Description:** Bibliothèque Python portée sur Android

**Avantages:**
- ✅ Support modèles Hugging Face

**Inconvénients:**
- ❌ Nécessite conversion GGUF → format Hugging Face
- ❌ Plus lourd que llama.cpp
- ❌ Performance inférieure
- ❌ Complexité d'intégration

**Recommandation:** ⚠️ **Pas optimal (conversion nécessaire)**

---

## 🏆 COMPARAISON RAPIDE

| Solution | GGUF Natif | Performance | Complexité | Android | Recommandation |
|----------|------------|--------------|------------|---------|----------------|
| **llama.cpp Direct** | ✅ Oui | ⭐⭐⭐⭐⭐ | Moyenne | ✅ Oui | ⭐⭐⭐⭐⭐ |
| **ONNX Runtime** | ❌ Non (conversion) | ⭐⭐⭐ | Faible | ✅ Oui | ⭐⭐⭐ |
| **MLX** | ❌ Non | - | - | ❌ Non | ❌ |
| **GPT4All** | ❌ Non | - | - | ❌ Non | ❌ |
| **LocalAI** | ✅ Oui | ⭐⭐⭐⭐ | Élevée | ⚠️ Possible | ⭐⭐ |
| **Hugging Face** | ❌ Non (conversion) | ⭐⭐ | Élevée | ⚠️ Possible | ⭐ |

---

## 🎯 RECOMMANDATION FINALE

### **llama.cpp Direct** ⭐⭐⭐⭐⭐

**Pourquoi:**
1. ✅ Support GGUF natif (vos modèles fonctionnent directement)
2. ✅ Performance maximale (C++ optimisé)
3. ✅ Pas de serveur HTTP (appel direct, plus rapide)
4. ✅ Utilisé par Ollama en interne (même moteur)
5. ✅ Open source, bien maintenu
6. ✅ Bindings Android disponibles

**Ce que ça donne:**
- `gemma3-270m.gguf` → Charge directement
- Pas de conversion nécessaire
- Performance native
- 100% offline

---

## 🔧 INTÉGRATION PROPOSÉE

### Structure

```
ChatAI-Android/
├── app/src/main/
│   ├── jniLibs/arm64-v8a/
│   │   └── libllama.so          (llama.cpp compilé)
│   ├── java/com/chatai/services/
│   │   └── LocalGGUFService.kt  (Service Kotlin)
│   └── cpp/
│       ├── llama_jni.cpp        (JNI bindings)
│       └── CMakeLists.txt       (Build config)
```

### Code Kotlin

```kotlin
// LocalGGUFService.kt
class LocalGGUFService(private val context: Context) {
    companion object {
        init {
            System.loadLibrary("llama") // Charge libllama.so
        }
    }
    
    // JNI functions
    private external fun initModel(modelPath: String, nThreads: Int = 4): Long
    private external fun generate(
        prompt: String, 
        context: Long,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): String
    private external fun freeModel(context: Long)
    
    private var modelContext: Long? = null
    
    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (modelContext == null) {
            modelContext = initModel(modelPath, 4)
            Log.i(TAG, "✅ Modèle GGUF chargé: $modelPath")
        }
    }
    
    suspend fun processUserInput(prompt: String): String = withContext(Dispatchers.IO) {
        val ctx = modelContext ?: throw IllegalStateException("Modèle non initialisé")
        generate(prompt, ctx, maxTokens = 256, temperature = 0.7f)
    }
    
    fun cleanup() {
        modelContext?.let { freeModel(it) }
        modelContext = null
    }
}
```

---

## 📚 RESSOURCES

### llama.cpp Android

**GitHub Principal:**
- https://github.com/ggerganov/llama.cpp

**Android Bindings:**
- https://github.com/ggerganov/llama.cpp/tree/master/bindings/android
- Exemple d'intégration Android

**Documentation:**
- https://github.com/ggerganov/llama.cpp/blob/master/README.md
- Guide complet d'utilisation

**Build Instructions:**
- NDK requis (Android NDK)
- CMake pour compilation
- Compilation native (C++)

---

## 🚀 PROCHAINES ÉTAPES

**Si vous choisissez llama.cpp:**

1. **Étape 1:** Ajouter NDK au projet
2. **Étape 2:** Compiler llama.cpp pour Android (ARM64)
3. **Étape 3:** Créer JNI bindings
4. **Étape 4:** Créer LocalGGUFService.kt
5. **Étape 5:** Intégrer dans KittAIService
6. **Étape 6:** Tester avec gemma3-270m.gguf

**Temps estimé:** 2-4 heures (première intégration)

---

## ❓ QUESTIONS

1. **Voulez-vous que j'intègre llama.cpp?**
   - ✅ Oui → Je commence l'intégration
   - ❌ Non → Autre solution?

2. **Préférez-vous ONNX Runtime (déjà intégré)?**
   - ⚠️ Nécessite conversion GGUF → ONNX
   - ⚠️ Performance peut être inférieure

3. **Autre solution en tête?**
   - Dites-moi laquelle

---

**Ma recommandation: llama.cpp direct** - C'est la meilleure option pour Android avec GGUF.

