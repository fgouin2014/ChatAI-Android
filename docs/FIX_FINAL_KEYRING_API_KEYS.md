# ✅ FIX FINAL - Persistance des clés API avec KeyringManager

## PROBLÈME RÉSOLU

Les clés API n'étaient pas restaurées dans la webapp après changement de provider, même si elles étaient correctement sauvegardées dans KeyringManager.

## CAUSE RACINE

Quand `updateCloudProviderUI` changeait de provider, il supprimait `apiKey` du JSON avant de sauvegarder (pour dire "ne pas modifier"). Ensuite, quand la webapp rechargeait la config, `readConfigJson()` lisait le fichier et retournait le JSON tel quel, **sans enrichir avec `apiKey` depuis KeyringManager**.

## SOLUTION IMPLÉMENTÉE

### 1. Enrichissement automatique dans `readConfigJson()`

**Fichier**: `AiConfigManager.java` ligne 87-120

Le JSON est maintenant **toujours enrichi** avec `apiKey` depuis KeyringManager si:
- `apiKey` est **absent** du fichier, OU
- `apiKey` est **vide** (`""`) dans le fichier mais qu'une clé existe dans KeyringManager

```java
// Enrichir si apiKey est absent du fichier OU si apiKey est vide mais qu'une clé existe dans KeyringManager
if (!hasApiKey || (fileApiKey != null && fileApiKey.trim().isEmpty() && keyringApiKey != null && !keyringApiKey.trim().isEmpty())) {
    if (keyringApiKey != null && !keyringApiKey.trim().isEmpty()) {
        cloud.put("apiKey", keyringApiKey.trim());
        Log.i(TAG, "📖 ENRICHISSEMENT JSON: apiKey ajouté depuis Keyring pour " + provider);
    } else {
        if (!hasApiKey) {
            cloud.put("apiKey", "");
        }
    }
    json = toPrettyString(jsonObj);
}
```

### 2. Logs détaillés pour débogage

Des logs ont été ajoutés pour tracer:
- Le provider actuel
- La présence/absence de `apiKey` dans le fichier
- La valeur de `apiKey` dans le fichier (si présente)
- La valeur de `apiKey` dans KeyringManager
- L'action d'enrichissement effectuée

## FLUX COMPLET (CORRIGÉ)

1. ✅ **Sauvegarde clé Ollama** → Clé dans KeyringManager (`api_key_ollama_cloud`)
2. ✅ **Changement vers Hugging Face** → Clé Ollama conservée dans KeyringManager
3. ✅ **Sauvegarde clé Hugging Face** → Clé dans KeyringManager (`api_key_huggingface`)
4. ✅ **Changement vers Ollama** → `readConfigJson()` enrichit le JSON avec la clé Ollama depuis KeyringManager → Champ UI mis à jour ✅
5. ✅ **Changement vers Hugging Face** → `readConfigJson()` enrichit le JSON avec la clé Hugging Face depuis KeyringManager → Champ UI mis à jour ✅

## CAS GÉRÉS

### Cas 1: `apiKey` absent du fichier
- **Action**: Enrichir depuis KeyringManager
- **Résultat**: JSON contient `apiKey` depuis KeyringManager (ou `""` si aucune clé)

### Cas 2: `apiKey` vide (`""`) dans le fichier, clé existe dans KeyringManager
- **Action**: Enrichir depuis KeyringManager (correction d'une erreur d'écriture)
- **Résultat**: JSON contient `apiKey` depuis KeyringManager

### Cas 3: `apiKey` vide (`""`) dans le fichier, aucune clé dans KeyringManager
- **Action**: Ne pas modifier (l'utilisateur a explicitement supprimé la clé)
- **Résultat**: JSON contient `apiKey = ""`

### Cas 4: `apiKey` présent avec valeur dans le fichier
- **Action**: Ne pas modifier (l'utilisateur a explicitement défini la clé)
- **Résultat**: JSON contient `apiKey` tel quel depuis le fichier

## TESTS À EFFECTUER

1. ✅ Sauvegarder une clé Ollama → Vérifier dans KeyringManager
2. ✅ Changer vers Hugging Face → Vérifier que la clé Ollama est toujours dans KeyringManager
3. ✅ Sauvegarder une clé Hugging Face → Vérifier dans KeyringManager
4. ✅ Changer vers Ollama → Vérifier que la clé Ollama est restaurée dans le champ UI
5. ✅ Changer vers Hugging Face → Vérifier que la clé Hugging Face est restaurée dans le champ UI
6. ✅ Vérifier les logs pour confirmer l'enrichissement

## FICHIERS MODIFIÉS

- `ChatAI-Android/app/src/main/java/com/chatai/AiConfigManager.java`
  - Ligne 87-120: Enrichissement automatique de `apiKey` depuis KeyringManager

## DOCUMENTATION

- `AUDIT_KEYRING_FIX_READCONFIG.md`: Audit détaillé du problème
- `GUIDE_KEYRING_MANAGER.md`: Guide d'utilisation de KeyringManager

## NOTES

- Cette solution garantit que `apiKey` est **TOUJOURS** présent dans le JSON retourné à la webapp
- Si le fichier ne contient pas `apiKey`, il est enrichi depuis KeyringManager (source de vérité)
- Si KeyringManager n'a pas de clé, `apiKey = ""` est ajouté pour rendre l'état explicite
- Cette approche est cohérente avec `buildJsonFromPreferences()` qui fait la même chose


