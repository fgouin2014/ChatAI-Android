# Guide de Compilation llama.cpp pour Android

**Date:** 2025-12-01  
**Objectif:** Compiler llama.cpp pour Android ARM64 et l'intégrer dans ChatAI-Android

---

## 📋 PRÉREQUIS

### 1. Android NDK

**Installation:**
- Via Android Studio: Tools → SDK Manager → SDK Tools → NDK (Side by side)
- Ou télécharger depuis: https://developer.android.com/ndk/downloads

**Vérification:**
```powershell
$env:ANDROID_NDK_HOME  # Doit pointer vers le NDK (ex: C:\Users\...\AppData\Local\Android\Sdk\ndk\25.1.8937393)
```

**Si non défini:**
```powershell
# Trouver automatiquement
$sdkPath = "$env:LOCALAPPDATA\Android\Sdk"
$ndkPath = (Get-ChildItem "$sdkPath\ndk" | Sort-Object Name -Descending | Select-Object -First 1).FullName
$env:ANDROID_NDK_HOME = $ndkPath
```

### 2. CMake

**Installation:**
- Via Android Studio: Tools → SDK Manager → SDK Tools → CMake
- Ou télécharger depuis: https://cmake.org/download/

**Vérification:**
```powershell
cmake --version  # Doit être >= 3.22.1
```

### 3. Git

**Installation:**
- Télécharger depuis: https://git-scm.com/download/win

**Vérification:**
```powershell
git --version
```

### 4. Compilateur C++

**Inclus dans NDK:** Le NDK contient déjà les compilateurs nécessaires (clang)

---

## 🚀 COMPILATION AUTOMATIQUE (Recommandé)

### Script PowerShell

Un script automatique est disponible:

```powershell
cd C:\androidProject\ChatAI-Android-beta\ChatAI-Android
.\scripts\compile_llama_cpp.ps1
```

**Ce script:**
1. ✅ Vérifie les prérequis (NDK, CMake)
2. ✅ Clone llama.cpp si nécessaire
3. ✅ Configure CMake pour Android ARM64
4. ✅ Compile llama.cpp
5. ✅ Copie `libllama.so` vers `app/src/main/jniLibs/arm64-v8a/`

---

## 🔧 COMPILATION MANUELLE

### Étape 1: Cloner llama.cpp

```powershell
cd C:\androidProject\ChatAI-Android-beta
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp
```

### Étape 2: Créer répertoire de build

```powershell
mkdir build-android
cd build-android
```

### Étape 3: Configurer CMake

```powershell
# Remplacer $ANDROID_NDK_HOME par votre chemin NDK
$ndkPath = $env:ANDROID_NDK_HOME  # Ex: C:\Users\...\AppData\Local\Android\Sdk\ndk\25.1.8937393

cmake `
    -DCMAKE_TOOLCHAIN_FILE="$ndkPath\build\cmake\android.toolchain.cmake" `
    -DANDROID_ABI=arm64-v8a `
    -DANDROID_PLATFORM=android-24 `
    -DCMAKE_C_FLAGS=-march=armv8.4a+dotprod `
    -DCMAKE_CXX_FLAGS=-march=armv8.4a+dotprod `
    -DLLAMA_ANDROID=ON `
    -DBUILD_SHARED_LIBS=ON `
    ..
```

### Étape 4: Compiler

```powershell
cmake --build . --config Release -j 4
```

**Note:** `-j 4` utilise 4 threads. Ajustez selon votre CPU.

### Étape 5: Trouver la bibliothèque

```powershell
# La bibliothèque sera dans:
# build-android/libllama.so
# ou
# build-android/Release/libllama.so
```

### Étape 6: Copier vers le projet

```powershell
# Créer le répertoire si nécessaire
New-Item -ItemType Directory -Path "C:\androidProject\ChatAI-Android-beta\ChatAI-Android\app\src\main\jniLibs\arm64-v8a" -Force

# Copier
Copy-Item "build-android\libllama.so" "C:\androidProject\ChatAI-Android-beta\ChatAI-Android\app\src\main\jniLibs\arm64-v8a\libllama.so"
```

---

## 🔄 MISE À JOUR llama_jni.cpp

Une fois `libllama.so` compilé, vous devez mettre à jour `llama_jni.cpp` pour utiliser les vraies fonctions llama.cpp.

### Étape 1: Inclure les headers

Dans `app/src/main/cpp/llama_jni.cpp`, décommenter:

```cpp
#include "llama.h"
#include "common.h"
```

**Note:** Les headers doivent être accessibles. Vous pouvez:
- Copier les headers depuis `llama.cpp/` vers `app/src/main/cpp/include/`
- Ou modifier `CMakeLists.txt` pour inclure le répertoire llama.cpp

### Étape 2: Mettre à jour CMakeLists.txt

```cmake
# Ajouter le répertoire llama.cpp
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp llama.cpp)

# Lier la bibliothèque
target_link_libraries(llama_jni
    llama
    ${log-lib}
)

# Inclure les headers
target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp
)
```

### Étape 3: Remplacer les placeholders

Dans `llama_jni.cpp`, remplacer les fonctions placeholder par les vraies implémentations llama.cpp.

**Exemple pour `initModel`:**

```cpp
JNIEXPORT jlong JNICALL
Java_com_chatai_services_LocalGGUFService_initModel(JNIEnv *env, jobject thiz, jstring modelPath, jint nThreads) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("Failed to get model path");
        return 0;
    }
    
    LOGI("Initializing model: %s (threads: %d)", path, nThreads);
    
    // ⭐ VRAIE IMPLÉMENTATION
    auto ctx = std::make_unique<ModelContext>();
    ctx->model_path = path;
    
    llama_model_params model_params = llama_model_default_params();
    ctx->model_ptr = llama_load_model_from_file(path, model_params);
    if (!ctx->model_ptr) {
        LOGE("Failed to load model");
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_threads = nThreads;
    ctx->ctx_ptr = llama_new_context_with_model(ctx->model_ptr, ctx_params);
    if (!ctx->ctx_ptr) {
        LOGE("Failed to create context");
        llama_free_model(ctx->model_ptr);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    
    jlong context_id = g_next_context_id++;
    g_contexts.push_back(std::move(ctx));
    
    LOGI("Model initialized with context ID: %ld", context_id);
    env->ReleaseStringUTFChars(modelPath, path);
    
    return context_id;
}
```

**Similaire pour `generate` et `freeModel`.**

---

## ✅ VÉRIFICATION

### 1. Vérifier la bibliothèque

```powershell
# Vérifier que libllama.so existe
Test-Path "C:\androidProject\ChatAI-Android-beta\ChatAI-Android\app\src\main\jniLibs\arm64-v8a\libllama.so"
```

### 2. Rebuild le projet

```powershell
cd C:\androidProject\ChatAI-Android-beta\ChatAI-Android
.\gradlew clean assembleDebug
```

### 3. Vérifier les logs

Lors du démarrage de l'app, vous devriez voir:

```
✅ Bibliothèque native llama_jni chargée
```

Si vous voyez:

```
❌ Erreur chargement bibliothèque native: ...
```

→ Vérifiez que `libllama.so` est bien dans `jniLibs/arm64-v8a/`

---

## 🐛 DÉPANNAGE

### Erreur: "NDK not found"

**Solution:**
```powershell
$env:ANDROID_NDK_HOME = "C:\Users\...\AppData\Local\Android\Sdk\ndk\25.1.8937393"
```

### Erreur: "CMake not found"

**Solution:**
- Installer CMake via Android Studio
- Ou ajouter CMake au PATH

### Erreur: "android.toolchain.cmake not found"

**Solution:**
- Vérifier que le NDK est complet (réinstaller si nécessaire)
- Le chemin devrait être: `$NDK_PATH/build/cmake/android.toolchain.cmake`

### Erreur: Compilation échoue

**Solution:**
- Vérifier les logs de compilation
- Essayer avec moins de threads: `-j 2` au lieu de `-j 4`
- Vérifier que vous avez assez de RAM (compilation peut utiliser 4-8 GB)

### Erreur: "UnsatisfiedLinkError" au runtime

**Solution:**
- Vérifier que `libllama.so` est dans `jniLibs/arm64-v8a/`
- Vérifier que le device est ARM64 (pas x86)
- Vérifier les logs: `adb logcat | Select-String llama`

---

## 📚 RESSOURCES

- **llama.cpp GitHub:** https://github.com/ggerganov/llama.cpp
- **Android NDK:** https://developer.android.com/ndk
- **CMake:** https://cmake.org/documentation/
- **Documentation llama.cpp:** https://github.com/ggerganov/llama.cpp/blob/master/README.md

---

## 🎯 RÉSUMÉ

1. ✅ Installer NDK et CMake
2. ✅ Cloner llama.cpp
3. ✅ Compiler avec CMake (script automatique ou manuel)
4. ✅ Copier `libllama.so` vers `jniLibs/arm64-v8a/`
5. ✅ Mettre à jour `llama_jni.cpp` avec vraies fonctions
6. ✅ Rebuild le projet Android
7. ✅ Tester avec `gemma3-270m.gguf`

**Temps estimé:** 30-60 minutes (selon CPU et connexion Internet)

