# 🔍 AUDIT COMPLET - SAUVEGARDE/RESTAURATION CLÉS API

## PROBLÈME IDENTIFIÉ

Les clés API ne sont pas sauvegardées ni restaurées correctement.

## FLUX COMPLET

### 1. SAUVEGARDE (Webapp → Android)

#### Étape 1: Webapp (chat-config.js)
```javascript
// Ligne 483-491
const cloudApiKeyValue = core.configCloudApiKey?.value?.trim() || '';
if (cloudApiKeyValue) {
    cfg.cloud.apiKey = cloudApiKeyValue;  // ✅ Clé ajoutée au JSON
} else {
    delete cfg.cloud.apiKey;  // ❌ PROBLÈME: Si champ vide, supprime apiKey du JSON
}
```

**PROBLÈME POTENTIEL**: Si l'utilisateur sauvegarde sans toucher au champ, `cloudApiKeyValue` est vide → `delete cfg.cloud.apiKey` → la clé est supprimée du JSON.

#### Étape 2: persistAiConfig() (chat-config.js)
```javascript
// Ligne 765
const result = await this.pushAiConfigContent(content);
// Appelle writeAiConfigJson() via AndroidInterface
```

#### Étape 3: WebAppInterface.writeAiConfigJson()
```java
// Ligne 286
public String writeAiConfigJson(String content) {
    return AiConfigManager.writeConfigJson(mContext, content);
}
```

#### Étape 4: AiConfigManager.writeConfigJson()
```java
// Ligne 115-130
public static synchronized String writeConfigJson(Context context, String jsonContent) {
    // Parse JSON
    JSONObject json = new JSONObject(jsonContent);
    // Applique aux préférences
    applyJsonToPreferences(context, json);  // ⭐ ICI
    // Écrit dans le fichier
    writeJsonToFile(json);
}
```

#### Étape 5: applyJsonToPreferences() - SAUVEGARDE KEYRING
```java
// Ligne 349-361
if (cloud.has("apiKey")) {
    String apiKey = cloud.optString("apiKey", null);
    if (apiKey != null && !apiKey.trim().isEmpty()) {
        KeyringManager keyring = KeyringManager.getInstance(context);
        keyring.setApiKey(provider, apiKey);  // ✅ SAUVEGARDE ICI
    }
}
```

**PROBLÈME IDENTIFIÉ #1**: Si `cloud.has("apiKey")` est false (ligne 490 de chat-config.js supprime apiKey), la sauvegarde ne se fait PAS.

### 2. RESTAURATION (Android → Webapp)

#### Étape 1: Webapp (chat-config.js)
```javascript
// Ligne 787
async loadAiConfigPreview(force = false) {
    const content = this.androidInterface.readAiConfigJson();
    // Parse et affiche
}
```

#### Étape 2: WebAppInterface.readAiConfigJson()
```java
// Ligne 276
public String readAiConfigJson() {
    return AiConfigManager.readConfigJson(mContext, true);
}
```

#### Étape 3: AiConfigManager.readConfigJson()
```java
// Ligne 73-84
public static synchronized String readConfigJson(Context context, boolean applyPreferences) {
    // Lit le fichier ou build depuis préférences
    JSONObject jsonObj = new JSONObject(json);
    if (applyPreferences) {
        applyJsonToPreferences(context, jsonObj);  // ⚠️ PROBLÈME: Écrase les préférences
    }
    return json;
}
```

#### Étape 4: buildJsonFromPreferences() - LECTURE KEYRING
```java
// Ligne 219-233
KeyringManager keyring = KeyringManager.getInstance(context);
apiKey = keyring.getApiKey(provider);  // ✅ LECTURE ICI
if (apiKey != null && !apiKey.isEmpty()) {
    cloud.put("apiKey", apiKey);  // ✅ Ajoutée au JSON
}
```

**PROBLÈME IDENTIFIÉ #2**: `readConfigJson()` appelle `applyJsonToPreferences()` qui peut ÉCRASER les clés si le JSON ne contient pas apiKey.

## PROBLÈMES CRITIQUES

### ❌ PROBLÈME #1: Suppression de apiKey dans JSON
**Fichier**: `chat-config.js` ligne 490
```javascript
if (cloudApiKeyValue) {
    cfg.cloud.apiKey = cloudApiKeyValue;
} else {
    delete cfg.cloud.apiKey;  // ❌ Supprime apiKey si champ vide
}
```

**Impact**: Si l'utilisateur sauvegarde sans modifier le champ, apiKey est supprimé du JSON → Android ne sauvegarde pas.

### ❌ PROBLÈME #2: readConfigJson() écrase les préférences
**Fichier**: `AiConfigManager.java` ligne 108
```java
if (applyPreferences) {
    applyJsonToPreferences(context, jsonObj);  // ❌ Écrase si JSON n'a pas apiKey
}
```

**Impact**: Si le JSON n'a pas apiKey, `applyJsonToPreferences()` ne sauvegarde rien, mais peut supprimer si champ vide.

### ❌ PROBLÈME #3: Mapping provider incorrect
**Fichier**: `KeyringManager.java` ligne 240
```java
case "ollama":
case "ollama_cloud":
    return KEY_OLLAMA_CLOUD;
```

**Vérifier**: Le provider dans le JSON est-il "ollama" ou "ollama_cloud"?

## SOLUTIONS

### ✅ SOLUTION #1: Ne jamais supprimer apiKey du JSON
```javascript
// chat-config.js ligne 485-491
if (cloudApiKeyValue) {
    cfg.cloud.apiKey = cloudApiKeyValue;  // Nouvelle clé
} 
// ❌ SUPPRIMER: delete cfg.cloud.apiKey
// ✅ GARDER: Ne pas toucher à apiKey si champ vide
```

### ✅ SOLUTION #2: Toujours inclure apiKey dans buildJsonFromPreferences
```java
// buildJsonFromPreferences() - DÉJÀ CORRECT
// Ligne 228-233: apiKey est toujours ajoutée si elle existe
```

### ✅ SOLUTION #3: Ne pas appeler applyJsonToPreferences dans readConfigJson
```java
// readConfigJson() ligne 107
// ❌ SUPPRIMER: if (applyPreferences) { applyJsonToPreferences(...); }
// ✅ GARDER: Seulement lire, ne pas écrire
```

### ✅ SOLUTION #4: Logs de debug
Ajouter des logs à chaque étape pour tracer le flux.

## PLAN D'ACTION

1. ✅ Corriger chat-config.js: Ne jamais supprimer apiKey
2. ✅ Corriger readConfigJson: Ne pas appeler applyJsonToPreferences
3. ✅ Ajouter logs de debug complets
4. ✅ Tester le flux complet

