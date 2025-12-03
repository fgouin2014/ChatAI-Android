# Guide de Mise à Jour llama_jni.cpp

**Date:** 2025-12-01  
**Objectif:** Remplacer les placeholders par les vraies fonctions llama.cpp

---

## 📋 ÉTAT ACTUEL

Le fichier `llama_jni.cpp` contient actuellement des **placeholders** qui retournent des réponses temporaires. Une fois llama.cpp compilé, vous devez remplacer ces placeholders par les vraies implémentations.

---

## 🔄 ÉTAPES DE MISE À JOUR

### Étape 1: Mettre à jour CMakeLists.txt

**Avant:**
```cmake
# ⭐ OPTION 1: Utiliser llama.cpp comme sous-module (RECOMMANDÉ)
# add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp llama.cpp)
```

**Après:**
```cmake
# Ajouter llama.cpp comme sous-répertoire
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp llama.cpp)

# Lier la bibliothèque llama
target_link_libraries(llama_jni
    llama
    ${log-lib}
)

# Inclure les headers llama.cpp
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp/include
)
```

### Étape 2: Inclure les headers dans llama_jni.cpp

**Avant:**
```cpp
// ⭐ NOTE: Ce fichier nécessite llama.cpp compilé
// Pour l'instant, implémentation simplifiée qui sera remplacée par les vraies fonctions llama.cpp
// Une fois llama.cpp compilé, remplacer par:
// #include "llama.h"
// #include "common.h"
```

**Après:**
```cpp
#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <android/log.h>

// ⭐ VRAIES INCLUSIONS llama.cpp
#include "llama.h"
#include "common.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
```

### Étape 3: Mettre à jour la structure ModelContext

**Avant:**
```cpp
struct ModelContext {
    void* model_ptr;
    void* ctx_ptr;
    std::string model_path;
    
    ModelContext() : model_ptr(nullptr), ctx_ptr(nullptr) {}
};
```

**Après:**
```cpp
struct ModelContext {
    llama_model* model_ptr;
    llama_context* ctx_ptr;
    std::string model_path;
    
    ModelContext() : model_ptr(nullptr), ctx_ptr(nullptr) {}
};
```

### Étape 4: Implémenter initModel

**Remplacer:**
```cpp
// ⭐ TEMPORAIRE: Créer contexte vide (sera remplacé par vraie implémentation)
auto ctx = std::make_unique<ModelContext>();
ctx->model_path = path;
ctx->model_ptr = (void*)g_next_context_id; // Placeholder
ctx->ctx_ptr = (void*)g_next_context_id;   // Placeholder
```

**Par:**
```cpp
// ⭐ VRAIE IMPLÉMENTATION
auto ctx = std::make_unique<ModelContext>();
ctx->model_path = path;

// Charger le modèle
llama_model_params model_params = llama_model_default_params();
ctx->model_ptr = llama_load_model_from_file(path, model_params);
if (!ctx->model_ptr) {
    LOGE("Failed to load model: %s", path);
    env->ReleaseStringUTFChars(modelPath, path);
    return 0;
}

// Créer le contexte
llama_context_params ctx_params = llama_context_default_params();
ctx_params.n_ctx = 2048;  // Taille du contexte
ctx_params.n_threads = nThreads;
ctx_params.n_threads_batch = nThreads;
ctx->ctx_ptr = llama_new_context_with_model(ctx->model_ptr, ctx_params);
if (!ctx->ctx_ptr) {
    LOGE("Failed to create context");
    llama_free_model(ctx->model_ptr);
    env->ReleaseStringUTFChars(modelPath, path);
    return 0;
}
```

### Étape 5: Implémenter generate

**Remplacer le placeholder par:**

```cpp
JNIEXPORT jstring JNICALL
Java_com_chatai_services_LocalGGUFService_generate(JNIEnv *env, jobject thiz, jlong contextId, jstring prompt, jint maxTokens, jfloat temperature) {
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        LOGE("Failed to get prompt");
        return env->NewStringUTF("");
    }
    
    LOGI("Generating response for context %ld (maxTokens: %d, temp: %.2f)", contextId, maxTokens, temperature);
    
    // Trouver le contexte
    ModelContext* ctx = nullptr;
    for (auto& c : g_contexts) {
        if ((jlong)c->model_ptr == contextId || (jlong)c->ctx_ptr == contextId) {
            ctx = c.get();
            break;
        }
    }
    
    if (!ctx || !ctx->ctx_ptr) {
        LOGE("Invalid context ID: %ld", contextId);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Invalid context");
    }
    
    // Tokeniser le prompt
    std::vector<llama_token> tokens = llama_tokenize(ctx->ctx_ptr, prompt_str, true);
    if (tokens.empty()) {
        LOGE("Failed to tokenize prompt");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to tokenize prompt");
    }
    
    // Évaluer le prompt
    llama_decode(ctx->ctx_ptr, llama_batch_get_one(&tokens[0], tokens.size(), 0, 0));
    
    // Générer les tokens
    std::string response;
    int n_cur = tokens.size();
    
    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = llama_sample_token(ctx->ctx_ptr, nullptr);
        if (new_token == llama_token_eos(ctx->ctx_ptr)) {
            break;
        }
        
        const char* token_str = llama_token_to_piece(ctx->ctx_ptr, new_token).c_str();
        response += token_str;
        
        // Évaluer le nouveau token
        llama_decode(ctx->ctx_ptr, llama_batch_get_one(&new_token, 1, n_cur, 0));
        n_cur++;
    }
    
    LOGI("Generated response: %s", response.c_str());
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    return env->NewStringUTF(response.c_str());
}
```

### Étape 6: Implémenter freeModel

**Remplacer le placeholder par:**

```cpp
JNIEXPORT void JNICALL
Java_com_chatai_services_LocalGGUFService_freeModel(JNIEnv *env, jobject thiz, jlong contextId) {
    LOGI("Freeing model context: %ld", contextId);
    
    // Trouver et libérer le contexte
    for (auto it = g_contexts.begin(); it != g_contexts.end(); ++it) {
        ModelContext* ctx = it->get();
        if ((jlong)ctx->model_ptr == contextId || (jlong)ctx->ctx_ptr == contextId) {
            if (ctx->ctx_ptr) {
                llama_free(ctx->ctx_ptr);
            }
            if (ctx->model_ptr) {
                llama_free_model(ctx->model_ptr);
            }
            g_contexts.erase(it);
            LOGI("Context %ld freed", contextId);
            return;
        }
    }
    
    LOGE("Context %ld not found for freeing", contextId);
}
```

---

## ✅ VÉRIFICATION

### 1. Compiler le projet

```powershell
cd C:\androidProject\ChatAI-Android-beta\ChatAI-Android
.\gradlew clean assembleDebug
```

### 2. Vérifier les logs

Lors du démarrage, vous devriez voir:

```
✅ Bibliothèque native llama_jni chargée
🔄 Initialisation modèle GGUF: /storage/emulated/0/ChatAI-Files/models/gemma3-270m.gguf
✅ Modèle initialisé avec succès
```

### 3. Tester une génération

Envoyer un message dans l'app et vérifier les logs:

```
🔄 Génération réponse (maxTokens: 256, temp: 0.70)
✅ Réponse générée: [réponse du modèle]
```

---

## 🐛 DÉPANNAGE

### Erreur: "llama.h: No such file or directory"

**Solution:**
- Vérifier que `CMakeLists.txt` inclut bien le répertoire llama.cpp
- Vérifier que llama.cpp est cloné dans le répertoire parent

### Erreur: "undefined reference to llama_*"

**Solution:**
- Vérifier que `target_link_libraries` inclut bien `llama`
- Vérifier que llama.cpp est bien compilé

### Erreur: "Model failed to load"

**Solution:**
- Vérifier que le chemin du modèle est correct
- Vérifier que le fichier .gguf existe
- Vérifier les permissions de lecture

---

## 📚 RESSOURCES

- **llama.cpp API:** https://github.com/ggerganov/llama.cpp/blob/master/llama.h
- **Exemples d'utilisation:** https://github.com/ggerganov/llama.cpp/tree/master/examples

---

**Une fois ces étapes complétées, llama.cpp sera pleinement intégré et fonctionnel!** 🚀

