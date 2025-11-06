# Changelog v4.3.1 - Correction des noms de modèles Ollama Cloud

**Date:** 2025-11-05  
**Type:** Bug fix critique

---

## 🔴 Problème identifié

L'app utilisait des noms de modèles incorrects avec le suffixe `-cloud` qui n'existe pas dans l'API Ollama.

**Erreur typique:**
```
HTTP 502 - {"error": "upstream error"}
```

**Cause:** Les vrais noms de modèles sur Ollama Cloud n'ont PAS de suffixe `-cloud`.

---

## ✅ Corrections apportées

### 1. Noms de modèles corrigés

| Ancien nom (❌) | Nouveau nom (✅) |
|-----------------|------------------|
| `gpt-oss:120b-cloud` | `gpt-oss:120b` |
| `deepseek-v3.1:671b-cloud` | `deepseek-v3.1:671b` |
| `qwen3-coder:480b-cloud` | `qwen3-coder:480b` |
| `kimi-k2:1t-cloud` | `kimi-k2:1t` |
| `gpt-oss:20b-cloud` | `gpt-oss:20b` |
| `glm-4.6:cloud` | `glm-4.6` |

### 2. URL de l'API corrigée

**Fonction de test (`KittFragment.kt`):**
- Ancien: `https://api.ollama.ai/api/chat` ❌
- Nouveau: `https://ollama.com/api/chat` ✅

**Fonction principale (`KittAIService.kt`):**
- Déjà correcte: `https://ollama.com/api/chat` ✅

### 3. Modèle par défaut changé

- Ancien défaut: `deepseek-v3.1:671b-cloud` (causait erreurs 502)
- Nouveau défaut: `gpt-oss:120b` (stable et performant)

---

## 📂 Fichiers modifiés

1. `KittAIService.kt` - Modèle par défaut
2. `KittFragment.kt` - URL de test + modèle par défaut
3. `AIConfigurationActivity.kt` - Liste des modèles dans Spinner
4. `OllamaThinkingService.kt` - Liste `THINKING_MODELS` + modèle par défaut
5. `activity_ai_configuration.xml` - Hint d'exemple

---

## 🧪 Tests à effectuer

1. **Test de connexion Cloud:**
   - Ouvrir KITT
   - Appuyer sur bouton NET → "Test de connexions réseau"
   - Résultat attendu: "OK - Quota disponible"

2. **Test Web Search:**
   - Via Quick Settings Tile, demander: "Quel est le prix du Bitcoin ?"
   - Résultat attendu: Recherche internet activée, réponse avec données réelles

3. **Test de quota:**
   - Si erreur HTTP 502, le log devrait afficher:
     ```
     ⚠️ Ollama Cloud QUOTA/RATE LIMIT ERROR
     💡 Solution: Vérifier votre quota sur ollama.com/account
     ```

---

## 🔍 Détection améliorée des erreurs

### Erreurs de quota maintenant détectées:
- HTTP 429 (Too Many Requests)
- HTTP 502 avec "upstream error" / "quota" / "rate limit"
- HTTP 503 (Service Unavailable)

### Messages d'erreur améliorés:
```
⚠️ QUOTA/RATE LIMIT ERROR (HTTP 502)
💡 Solution: Vérifier votre quota Ollama Cloud sur ollama.com/account
💡 Solution: Attendre quelques minutes et réessayer
💡 Solution: Essayer un autre modèle cloud
```

---

## 🚗 Impact

**Avant:** HTTP 502 "upstream error" avec `deepseek-v3.1:671b-cloud`  
**Après:** Connexion réussie avec `gpt-oss:120b` ✅

**Web Search:** Devrait maintenant fonctionner correctement avec les vrais noms de modèles.

---

## 📊 Source de vérité

Liste officielle des modèles obtenue via:
```bash
curl https://ollama.com/api/tags
```

**Modèles confirmés disponibles:**
- `gpt-oss:120b` (nouveau défaut) ⭐
- `deepseek-v3.1:671b` (671B paramètres)
- `qwen3-coder:480b` (spécialisé code)
- `kimi-k2:1t` (1T paramètres)
- `gpt-oss:20b` (version légère)
- `glm-4.6` (multimodal)

---

## ⚙️ Version

- **Version Code:** 11
- **Version Name:** 4.3.1
- **Build:** Debug

---

**FIN DU CHANGELOG v4.3.1**

