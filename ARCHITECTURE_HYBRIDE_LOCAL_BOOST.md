# Architecture Hybride: Local GGUF + Hugging Face Boost

**Date:** 2025-12-01  
**Vision:** Système intelligent qui utilise local pour base, Hugging Face pour tâches avancées

---

## 🎯 VOTRE VISION CLARIFIÉE

**"Boosté dans le sens des choses que je ne peux pas faire localement"**

### Architecture Hybride

```
TÂCHES SIMPLES (Local GGUF)
    ↓
gemma3-270m.gguf (offline)
- Réponses basiques
- Questions simples
- Conversations courantes
- Fonctionne 100% offline
    ↓
Si insuffisant → FALLBACK
    ↓
TÂCHES COMPLEXES (Hugging Face)
    ↓
Hugging Face Inference API (cloud)
- Questions complexes
- Analyse approfondie
- Génération longue
- Nécessite Internet
```

---

## 🧠 LOGIQUE DE DÉCISION

### Quand utiliser Local GGUF?

**✅ Utiliser Local (gemma3-270m.gguf):**
- Questions simples et directes
- Conversations courantes
- Réponses courtes (< 200 mots)
- Pas besoin d'analyse complexe
- **Priorité: Offline d'abord**

**Exemples:**
- "Quelle heure est-il?"
- "Comment ça va?"
- "Explique-moi X simplement"
- "Résume Y en 2 phrases"

---

### Quand utiliser Hugging Face (Boost)?

**✅ Utiliser Hugging Face (si Internet disponible):**
- Questions complexes nécessitant analyse
- Génération de texte longue
- Tâches spécialisées (code, math, etc.)
- Local GGUF retourne réponse insuffisante
- **Fallback intelligent**

**Exemples:**
- "Écris-moi un code Python pour..."
- "Analyse en profondeur ce concept..."
- "Génère un texte de 500 mots sur..."
- "Explique la théorie quantique..."

---

## 🏗️ ARCHITECTURE PROPOSÉE

### Flow Intelligent

```
USER INPUT
    ↓
┌─────────────────────────────────────────┐
│  1. Détection Complexité                │
│  - isComplexQuery()?                    │
│  - needsAdvancedAnalysis()?             │
│  - requiresLongGeneration()?            │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  2. Essayer Local GGUF (toujours)       │
│  - LocalGGUFService.process()           │
│  - Réponse générée                      │
│  - Évaluer qualité réponse              │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  3. Évaluation Réponse                  │
│  - isResponseSufficient()?              │
│  - isResponseTooShort()?                │
│  - isResponseOffTopic()?                 │
└─────────────────────────────────────────┘
    ↓
    ├─ Réponse suffisante → ✅ RETOURNER
    │
    └─ Réponse insuffisante → FALLBACK
        ↓
    ┌─────────────────────────────────────────┐
    │  4. Hugging Face Boost (si Internet)    │
    │  - HuggingFaceService.generate()        │
    │  - Modèle plus puissant                 │
    │  - Réponse améliorée                    │
    └─────────────────────────────────────────┘
        ↓
    ┌─────────────────────────────────────────┐
    │  5. Combinaison Intelligente (optionnel) │
    │  - Local: Base/contexte                  │
    │  - Hugging Face: Détails/analyse        │
    │  - Fusionner réponses                   │
    └─────────────────────────────────────────┘
```

---

## 🔧 IMPLÉMENTATION

### 1. Détection Complexité

```kotlin
// KittAIService.kt
private fun isComplexQuery(userInput: String): Boolean {
    val complexKeywords = listOf(
        "écris", "génère", "crée", "développe",
        "analyse en profondeur", "explique en détail",
        "code", "programme", "algorithme",
        "théorie", "concept avancé"
    )
    
    val lowerInput = userInput.lowercase()
    return complexKeywords.any { lowerInput.contains(it) }
}

private fun needsLongGeneration(userInput: String): Boolean {
    val longKeywords = listOf(
        "500 mots", "long texte", "essai", "article",
        "documentation", "guide complet"
    )
    
    val lowerInput = userInput.lowercase()
    return longKeywords.any { lowerInput.contains(it) }
}
```

---

### 2. Évaluation Réponse Local

```kotlin
// KittAIService.kt
private fun isResponseSufficient(response: String, userInput: String): Boolean {
    // Réponse trop courte (< 50 caractères)
    if (response.length < 50) return false
    
    // Réponse générique (pas de contenu spécifique)
    val genericResponses = listOf(
        "je ne comprends pas",
        "pouvez-vous reformuler",
        "je ne peux pas",
        "désolé"
    )
    
    val lowerResponse = response.lowercase()
    if (genericResponses.any { lowerResponse.contains(it) }) {
        return false
    }
    
    // Réponse semble pertinente
    return true
}
```

---

### 3. Flow Principal Modifié

```kotlin
// KittAIService.kt - processUserInput()
suspend fun processUserInput(userInput: String): String = withContext(Dispatchers.IO) {
    // 1. Détection actions (déjà fait)
    val actionResponse = detectAndExecuteAction(userInput)
    if (actionResponse != null) return@withContext actionResponse
    
    // 2. RAG (si activé)
    val ragContext = if (ragEnabled) {
        ragService.searchSimilar(userInput)
    } else ""
    
    val enhancedInput = if (ragContext.isNotEmpty()) {
        "$ragContext\n\nQuestion: $userInput"
    } else {
        userInput
    }
    
    // 3. ⭐ NOUVEAU: Détection complexité
    val isComplex = isComplexQuery(userInput) || needsLongGeneration(userInput)
    
    // 4. ⭐ NOUVEAU: Toujours essayer Local d'abord (si disponible)
    val localService = LocalGGUFService(context)
    val localModelPath = sharedPreferences.getString("local_model_name", "gemma3-270m.gguf")
    
    if (localModelPath != null && File(localModelPath).exists()) {
        val localResponse = localService.processUserInput(enhancedInput)
        
        // 5. ⭐ NOUVEAU: Évaluer si réponse suffisante
        if (isResponseSufficient(localResponse, userInput) && !isComplex) {
            // Réponse locale suffisante
            Log.i(TAG, "✅ Réponse locale suffisante")
            return@withContext localResponse
        }
        
        // 6. ⭐ NOUVEAU: Si complexe ou réponse insuffisante → Hugging Face
        if (isComplex || !isResponseSufficient(localResponse, userInput)) {
            // Vérifier Internet
            if (isInternetAvailable()) {
                Log.i(TAG, "🔄 Fallback vers Hugging Face (tâche complexe ou réponse insuffisante)")
                val hfResponse = tryHuggingFace(enhancedInput)
                if (hfResponse != null) {
                    // ⭐ OPTION: Combiner réponses (local + HF)
                    return@withContext combineResponses(localResponse, hfResponse)
                    // OU simplement retourner HF
                    // return@withContext hfResponse
                }
            }
        }
        
        // 7. Fallback: Retourner réponse locale même si insuffisante
        return@withContext localResponse
    }
    
    // 8. Si pas de modèle local → Hugging Face directement
    if (isInternetAvailable()) {
        val hfResponse = tryHuggingFace(enhancedInput)
        if (hfResponse != null) {
            return@withContext hfResponse
        }
    }
    
    // 9. Dernier recours
    return@withContext "Désolé, je ne peux pas répondre sans modèle local ou connexion Internet."
}
```

---

## 📊 MATRICE DE DÉCISION

| Situation | Local GGUF | Hugging Face | Résultat |
|-----------|------------|--------------|----------|
| **Question simple + Offline** | ✅ Oui | ❌ Non | Réponse locale |
| **Question simple + Online** | ✅ Oui | ❌ Non | Réponse locale (priorité offline) |
| **Question complexe + Offline** | ✅ Oui | ❌ Non | Réponse locale (meilleure possible) |
| **Question complexe + Online** | ✅ Oui | ✅ Oui | Réponse Hugging Face (boost) |
| **Réponse locale insuffisante + Online** | ✅ Tenté | ✅ Oui | Réponse Hugging Face (fallback) |
| **Pas de modèle local + Online** | ❌ Non | ✅ Oui | Réponse Hugging Face |
| **Pas de modèle local + Offline** | ❌ Non | ❌ Non | Erreur |

---

## 🎯 CONFIGURATION

### Options Utilisateur

```kotlin
// SharedPreferences
"local_gguf_enabled" = true  // Activer modèle local
"huggingface_boost_enabled" = true  // Activer boost Hugging Face
"prefer_offline" = true  // Toujours essayer local d'abord
"auto_fallback" = true  // Fallback automatique si local insuffisant
```

### Interface Webapp

**Onglet Général:**
- ✅ Mode: "Local GGUF (avec boost Hugging Face)"
- ✅ Modèle Local: Sélection `gemma3-270m.gguf` ou autres
- ✅ Boost Hugging Face: Checkbox "Activer boost pour tâches complexes"

**Onglet Cloud:**
- ✅ Hugging Face: Clé API (pour boost uniquement)
- ❌ Ollama Cloud: Supprimé (pas nécessaire)

---

## 🚀 PLAN D'IMPLÉMENTATION

### Phase 1: Moteur GGUF Local (Priorité 1)

**Étape 1.1: Intégrer llama.cpp**
- [ ] Ajouter dépendance llama.cpp Android
- [ ] Créer `LocalGGUFService.kt` avec JNI bindings
- [ ] Tester chargement `gemma3-270m.gguf`
- [ ] Tester génération réponse

**Étape 1.2: Intégrer dans KittAIService**
- [ ] Ajouter détection complexité
- [ ] Ajouter évaluation réponse
- [ ] Modifier `processUserInput()` pour flow hybride
- [ ] Tester flow complet

---

### Phase 2: Fallback Intelligent (Priorité 2)

**Étape 2.1: Détection Complexité**
- [ ] Implémenter `isComplexQuery()`
- [ ] Implémenter `needsLongGeneration()`
- [ ] Tester détection

**Étape 2.2: Évaluation Réponse**
- [ ] Implémenter `isResponseSufficient()`
- [ ] Tester évaluation

**Étape 2.3: Fallback Hugging Face**
- [ ] Modifier flow pour fallback automatique
- [ ] Tester fallback

---

### Phase 3: Configuration (Priorité 3)

**Étape 3.1: Interface Webapp**
- [ ] Ajouter mode "Local + Boost"
- [ ] Ajouter sélection modèle GGUF
- [ ] Ajouter checkbox "Boost Hugging Face"
- [ ] Tester configuration

**Étape 3.2: Nettoyage**
- [ ] Supprimer Ollama Cloud (si confirmé)
- [ ] Nettoyer code obsolète
- [ ] Tester système complet

---

## ❓ QUESTIONS FINALES

### 1. Combinaison Réponses
**Q:** Quand Hugging Face boost est utilisé, préférez-vous:
- A) Remplacer complètement réponse locale par Hugging Face
- B) Combiner les deux (local: base, HF: détails)
- C) Toujours remplacer (plus simple)

### 2. Ollama Cloud
**Q:** Voulez-vous vraiment supprimer Ollama Cloud complètement?
- ✅ Oui → Je supprime tout code Ollama Cloud
- ❌ Non → Garder comme option optionnelle (3ème fallback)

### 3. Détection Complexité
**Q:** Préférez-vous:
- A) Détection automatique (mots-clés)
- B) Toujours essayer local d'abord, fallback si insuffisant
- C) Les deux (détection + évaluation)

---

## 🎯 PROCHAINES ÉTAPES

**Une fois vos réponses données:**

1. ✅ Intégrer llama.cpp pour LocalGGUFService
2. ✅ Implémenter flow hybride (Local → Hugging Face)
3. ✅ Ajouter détection complexité + évaluation
4. ✅ Modifier interface webapp
5. ✅ Supprimer Ollama Cloud (si confirmé)

**Je commence dès que vous confirmez!** 🚀

