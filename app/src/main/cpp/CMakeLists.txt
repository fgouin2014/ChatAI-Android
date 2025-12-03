cmake_minimum_required(VERSION 3.22.1)

project("llama_jni")

# ⭐ CONFIGURATION ANDROID
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ⭐ INTÉGRATION COMPLÈTE: Utiliser llama.cpp précompilé
# La bibliothèque libllama.so est déjà compilée et copiée dans jniLibs/arm64-v8a/

# Chemin vers llama.cpp (pour les headers)
# Depuis app/src/main/cpp, remonter de 4 niveaux pour arriver à ChatAI-Android-beta
# Puis aller dans llama.cpp
get_filename_component(PROJECT_ROOT ${CMAKE_CURRENT_SOURCE_DIR} DIRECTORY)  # app/src/main
get_filename_component(PROJECT_ROOT ${PROJECT_ROOT} DIRECTORY)                # app/src
get_filename_component(PROJECT_ROOT ${PROJECT_ROOT} DIRECTORY)                # app
get_filename_component(PROJECT_ROOT ${PROJECT_ROOT} DIRECTORY)                # ChatAI-Android
get_filename_component(PROJECT_ROOT ${PROJECT_ROOT} DIRECTORY)                # ChatAI-Android-beta
set(LLAMA_CPP_DIR ${PROJECT_ROOT}/llama.cpp)

message(STATUS "🔍 Recherche llama.cpp dans: ${LLAMA_CPP_DIR}")
if (NOT EXISTS "${LLAMA_CPP_DIR}")
    message(FATAL_ERROR "❌ Répertoire llama.cpp non trouvé: ${LLAMA_CPP_DIR}")
endif()
if (NOT EXISTS "${LLAMA_CPP_DIR}/include/llama.h")
    message(FATAL_ERROR "❌ Header llama.h non trouvé dans: ${LLAMA_CPP_DIR}/include/llama.h")
endif()
message(STATUS "✅ llama.cpp trouvé: ${LLAMA_CPP_DIR}")

# Fichiers sources
set(SOURCES
    llama_jni.cpp
)

# Créer la bibliothèque partagée
add_library(llama_jni SHARED ${SOURCES})

# ⭐ LIEN avec libllama.so précompilé
# Sur Android, les bibliothèques dans jniLibs/ sont automatiquement incluses dans l'APK
# Mais pour la compilation du wrapper JNI, nous devons lier la bibliothèque

# Chercher libllama.so dans plusieurs emplacements possibles
set(LLAMA_LIB_FOUND FALSE)
set(LLAMA_LIB_PATH "")

# 1. Chercher dans jniLibs/arm64-v8a/ (emplacement standard Android)
# CMAKE_CURRENT_SOURCE_DIR = app/src/main/cpp
# Donc ../../jniLibs/arm64-v8a = app/src/main/jniLibs/arm64-v8a
set(JNI_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/arm64-v8a)
if (EXISTS "${JNI_LIBS_DIR}/libllama.so")
    set(LLAMA_LIB_FOUND TRUE)
    set(LLAMA_LIB_PATH "${JNI_LIBS_DIR}/libllama.so")
    message(STATUS "✅ libllama.so trouvée dans jniLibs: ${LLAMA_LIB_PATH}")
else()
    # 2. Chercher dans le répertoire de build llama.cpp
    set(BUILD_LIB_DIR ${LLAMA_CPP_DIR}/build-android/bin)
    if (EXISTS "${BUILD_LIB_DIR}/libllama.so")
        set(LLAMA_LIB_FOUND TRUE)
        set(LLAMA_LIB_PATH "${BUILD_LIB_DIR}/libllama.so")
        message(STATUS "✅ libllama.so trouvée dans build: ${LLAMA_LIB_PATH}")
    else()
        # 3. Utiliser find_library comme fallback
        find_library(llama_lib llama PATHS ${JNI_LIBS_DIR} ${BUILD_LIB_DIR} NO_DEFAULT_PATH)
        if (llama_lib)
            set(LLAMA_LIB_FOUND TRUE)
            set(LLAMA_LIB_PATH "${llama_lib}")
            message(STATUS "✅ libllama.so trouvée via find_library: ${LLAMA_LIB_PATH}")
        endif()
    endif()
endif()

# Lier la bibliothèque si trouvée
if (LLAMA_LIB_FOUND)
    # Créer une cible IMPORTED pour la bibliothèque
    add_library(llama SHARED IMPORTED)
    set_target_properties(llama PROPERTIES
        IMPORTED_LOCATION "${LLAMA_LIB_PATH}"
    )
    target_link_libraries(llama_jni llama)
    message(STATUS "✅ libllama.so liée au wrapper JNI")
else()
    message(WARNING "⚠️  libllama.so non trouvée")
    message(WARNING "   Chemins vérifiés:")
    message(WARNING "   - ${JNI_LIBS_DIR}/libllama.so")
    message(WARNING "   - ${BUILD_LIB_DIR}/libllama.so")
    message(WARNING "   Exécutez compile_llama_cpp.ps1 pour compiler et copier la bibliothèque")
    message(WARNING "   La compilation continuera, mais l'app échouera au runtime si libllama.so est absente")
endif()

# Lien avec log Android
find_library(log-lib log)
target_link_libraries(llama_jni ${log-lib})

# ⭐ INCLUDE DIRECTORIES pour les headers llama.cpp et ggml
target_include_directories(llama_jni PRIVATE
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/src
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/ggml/src
)

# ⭐ FLAGS DE COMPILATION
target_compile_options(llama_jni PRIVATE
    -Wall
    -Wextra
    -O3
    -march=armv8.4a+dotprod  # Optimisation ARM64
)

# ⭐ INTÉGRATION COMPLÈTE: llama.cpp est maintenant intégré
# - libllama.so est compilée et copiée dans jniLibs/arm64-v8a/
# - Les headers sont inclus depuis ${LLAMA_CPP_DIR}
# - La bibliothèque est liée via find_library()

