# 🔍 AUDIT CRITIQUE - FIX readConfigJson pour apiKey

## PROBLÈME IDENTIFIÉ

Les clés API ne sont pas restaurées dans la webapp après changement de provider, même si elles sont correctement sauvegardées dans KeyringManager.

## CAUSE RACINE

### Flux du problème

1. **`updateCloudProviderUI` (index.html)** sauvegarde la clé du provider précédent ✅
2. **`updateCloudProviderUI`** change le provider en supprimant `apiKey` du JSON (pour dire "ne pas modifier") ✅
3. **`updateCloudProviderUI`** appelle `loadAiConfigPreview(true)` ✅
4. **`loadAiConfigPreview`** appelle `readAiConfigJson()` qui appelle `readConfigJson(context, true)` ✅
5. **`readConfigJson`** lit le fichier `ai_config.json` qui ne contient **PAS** `apiKey` (car supprimé à l'étape 2) ❌
6. **`readConfigJson`** retourne le JSON du fichier **tel quel**, sans enrichir avec `apiKey` depuis KeyringManager ❌
7. **`renderConfigForms`** reçoit un JSON sans `apiKey` → le champ reste vide ❌

### Logs observés

```
🔑 applyJsonToPreferences: provider=ollama
🔑 applyJsonToPreferences: cloud.has('apiKey')=false
🔑 getApiKey: provider=ollama, keyName=api_key_ollama_cloud, encrypted=116 chars
🔑 getApiKey: Décrypté=57 chars
🔑 applyJsonToPreferences: apiKey ABSENT, clé existante=57 chars
Clé ollama non modifiée dans JSON, conservation clé existante (57 chars)
```

**Analyse**: La clé est bien conservée dans KeyringManager (57 chars), mais le JSON retourné à la webapp ne contient pas `apiKey` car le fichier ne le contient pas.

## SOLUTION

### Fix dans `readConfigJson()`

**Fichier**: `AiConfigManager.java` ligne 78-96

**Avant**:
```java
JSONObject jsonObj = new JSONObject(json);
if (applyPreferences) {
    applyJsonToPreferences(context, jsonObj);
}
return json; // ❌ Retourne le JSON du fichier tel quel, sans apiKey
```

**Après**:
```java
JSONObject jsonObj = new JSONObject(json);
// ⭐ FIX CRITIQUE: Enrichir le JSON avec apiKey depuis KeyringManager si absent
JSONObject cloud = jsonObj.optJSONObject("cloud");
if (cloud != null && !cloud.has("apiKey")) {
    String provider = cloud.optString("provider", "ollama");
    KeyringManager keyring = KeyringManager.getInstance(context);
    String apiKey = keyring.getApiKey(provider);
    if (apiKey != null && !apiKey.trim().isEmpty()) {
        cloud.put("apiKey", apiKey.trim());
        Log.i(TAG, "📖 ENRICHISSEMENT JSON: apiKey ajouté depuis Keyring pour " + provider);
    } else {
        cloud.put("apiKey", "");
        Log.d(TAG, "📖 ENRICHISSEMENT JSON: apiKey = \"\" pour " + provider);
    }
    // Reconstruire le JSON avec apiKey
    json = toPrettyString(jsonObj);
}
if (applyPreferences) {
    applyJsonToPreferences(context, jsonObj);
}
return json; // ✅ Retourne le JSON enrichi avec apiKey depuis KeyringManager
```

## RÉSULTAT ATTENDU

1. **`readConfigJson`** lit le fichier
2. Si `apiKey` est absent du fichier, il est enrichi depuis KeyringManager
3. Le JSON retourné contient **TOUJOURS** `apiKey` (soit la clé depuis KeyringManager, soit `""`)
4. **`renderConfigForms`** reçoit un JSON avec `apiKey` → le champ est mis à jour ✅

## TESTS À EFFECTUER

1. ✅ Sauvegarder une clé Ollama
2. ✅ Changer de provider vers Hugging Face
3. ✅ Vérifier que la clé Ollama est toujours dans KeyringManager
4. ✅ Sauvegarder une clé Hugging Face
5. ✅ Changer de provider vers Ollama
6. ✅ Vérifier que la clé Ollama est restaurée dans le champ UI
7. ✅ Vérifier que la clé Hugging Face est toujours dans KeyringManager
8. ✅ Changer de provider vers Hugging Face
9. ✅ Vérifier que la clé Hugging Face est restaurée dans le champ UI

## NOTES

- Cette solution garantit que `apiKey` est **TOUJOURS** présent dans le JSON retourné à la webapp
- Si le fichier ne contient pas `apiKey`, il est enrichi depuis KeyringManager (source de vérité)
- Si KeyringManager n'a pas de clé, `apiKey = ""` est ajouté pour rendre l'état explicite
- Cette approche est cohérente avec `buildJsonFromPreferences()` qui fait la même chose


