# 💬 Discussion: Logique de sélection des APIs

**Date**: 2025-12-01  
**Question**: Pourquoi l'ordre des APIs ne vérifie pas l'activation de chaque service ?

---

## 🐛 PROBLÈME IDENTIFIÉ

### **Avant** (Logique incorrecte)
```kotlin
// ❌ Ollama Cloud essayé même si use_ollama_cloud = false
when {
    useHuggingFaceLLM && internetAvailable -> {
        listOf("huggingface", "ollama_cloud") // ← Ollama toujours essayé
    }
    internetAvailable -> {
        listOf("ollama_cloud") // ← Ollama essayé même si désactivé
    }
}
```

**Problème**: 
- Ollama Cloud est essayé même si `use_ollama_cloud = false`
- Pas de cohérence avec Hugging Face (qui vérifie `use_huggingface_llm`)

---

## ✅ CORRECTION APPLIQUÉE

### **Après** (Logique correcte)
```kotlin
// ✅ Vérifie activation de chaque service
val useHuggingFaceLLM = huggingFaceService.isEnabledForLLM() && huggingFaceService.isConfigured()
val useOllamaCloud = sharedPreferences.getBoolean("use_ollama_cloud", false)
val ollamaCloudConfigured = !ollamaCloudApiKey.isNullOrEmpty()
val useOllamaCloudLLM = useOllamaCloud && ollamaCloudConfigured

// Ordre selon ce qui est activé
when {
    useHuggingFaceLLM && useOllamaCloudLLM && internetAvailable -> {
        listOf("huggingface", "ollama_cloud") // Les deux activés
    }
    useHuggingFaceLLM && internetAvailable -> {
        listOf("huggingface") // Seulement HF activé
    }
    useOllamaCloudLLM && internetAvailable -> {
        listOf("ollama_cloud") // Seulement Ollama activé
    }
    else -> {
        listOf("fallback") // Rien d'activé ou offline
    }
}
```

---

## 📋 NOUVELLE LOGIQUE

### **Ordre des APIs** (selon activation)

1. **Hugging Face** (si `use_huggingface_llm = true` + Internet)
2. **Ollama Cloud** (si `use_ollama_cloud = true` + Internet)
3. **Fallback local** (si rien d'autre disponible ou offline)

### **Cas possibles**

| Hugging Face | Ollama Cloud | Internet | Ordre |
|--------------|--------------|----------|-------|
| ✅ Activé    | ✅ Activé    | ✅ Oui   | HF → Ollama → Fallback |
| ✅ Activé    | ❌ Désactivé | ✅ Oui   | HF → Fallback |
| ❌ Désactivé | ✅ Activé    | ✅ Oui   | Ollama → Fallback |
| ❌ Désactivé | ❌ Désactivé | ✅ Oui   | Fallback |
| ✅ Activé    | ✅ Activé    | ❌ Non   | Fallback |
| ❌ Désactivé | ❌ Désactivé | ❌ Non   | Fallback |

---

## 🤔 QUESTION: Modèles Hugging Face

### **Problème**
L'utilisateur dit qu'il n'y a que 2 modèles Hugging Face disponibles.

### **Réalité du code**
- Le code récupère **jusqu'à 20 modèles** depuis l'API Hugging Face
- Liste par défaut de **4 modèles** si l'API échoue:
  - `HuggingFaceTB/SmolLM3-3B:hf-inference`
  - `gpt2:hf-inference`
  - `distilgpt2:hf-inference`
  - `EleutherAI/gpt-neo-125M:hf-inference`

### **Pourquoi seulement 2 modèles ?**

**Hypothèses**:
1. **UI limitée**: L'interface n'affiche peut-être que 2 modèles
2. **API échoue**: L'API Hugging Face ne retourne peut-être que 2 modèles
3. **Filtrage**: Les modèles sont peut-être filtrés et seulement 2 passent les tests
4. **Configuration**: Seulement 2 modèles sont configurés dans SharedPreferences

### **À vérifier**
- Combien de modèles sont retournés par l'API ?
- Combien sont affichés dans l'UI ?
- Y a-t-il un filtre qui limite à 2 modèles ?

---

## ✅ RÉSUMÉ

### **Correction appliquée**
- ✅ Ollama Cloud vérifie maintenant `use_ollama_cloud` avant d'être essayé
- ✅ Cohérence avec Hugging Face (même logique d'activation)
- ✅ Ordre intelligent selon ce qui est activé

### **Question ouverte**
- ❓ Pourquoi seulement 2 modèles Hugging Face visibles ?
- 🔍 À investiguer: API, UI, filtres

---

## 🎯 PROCHAINES ÉTAPES

1. ✅ **Corriger logique Ollama Cloud** (FAIT)
2. ⏳ **Investiguer modèles Hugging Face** (pourquoi seulement 2 ?)
3. ⏳ **Tester avec différentes configurations**

