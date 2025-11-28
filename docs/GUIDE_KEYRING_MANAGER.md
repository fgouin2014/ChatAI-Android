# 🔐 Guide de gestion du KeyringManager

## Vue d'ensemble

Le `KeyringManager` est un système centralisé et sécurisé pour gérer toutes les clés API de ChatAI. Il utilise Android Keystore System avec chiffrement AES-256/GCM.

## Utilisation de base

### Obtenir une instance

```java
KeyringManager keyring = KeyringManager.getInstance(context);
```

### Sauvegarder une clé API

```java
// Sauvegarder une clé pour un provider
keyring.setApiKey("huggingface", "hf_xxxxxxxxxxxxx");
keyring.setApiKey("ollama", "oll_xxxxxxxxxxxxx");
keyring.setApiKey("openai", "sk-xxxxxxxxxxxxx");
```

### Récupérer une clé API

```java
// Récupérer une clé
String hfKey = keyring.getApiKey("huggingface");
if (hfKey != null) {
    // Utiliser la clé
}
```

### Vérifier si une clé existe

```java
if (keyring.hasApiKey("huggingface")) {
    // Clé configurée
}
```

### Supprimer une clé

```java
keyring.clearApiKey("huggingface");
```

## Providers supportés

- `"huggingface"` ou `"hf"` - Hugging Face
- `"ollama"` ou `"ollama_cloud"` - Ollama Cloud
- `"openai"` - OpenAI
- `"anthropic"` - Anthropic
- `"groq"` - Groq
- `"perplexity"` - Perplexity

## Méthodes de gestion avancées

### Obtenir un résumé

```java
String summary = keyring.getKeyringSummary();
// Affiche:
// 🔐 KEYRING STATUS
// ═══════════════════════════════
// ✅ Hugging Face: 32 chars
// ✅ Ollama Cloud: 28 chars
// ❌ OpenAI: Non configuré
// ...
// Total: 2/6 configurés
// Keystore: ✅ Actif
```

### Lister les providers configurés

```java
String[] configured = keyring.getConfiguredProviders();
// Retourne: ["huggingface", "ollama"]
```

### Export des clés (backup)

```java
// Export chiffré (recommandé)
String encryptedExport = keyring.exportKeys(true);

// Export en clair (⚠️ moins sécurisé, pour migration)
String plainExport = keyring.exportKeys(false);
```

### Import des clés (restore)

```java
// Importer depuis un export
boolean success = keyring.importKeys(jsonExport);
if (success) {
    // Import réussi
}
```

### Vérifier l'intégrité

```java
// Teste le chiffrement/déchiffrement
boolean valid = keyring.verifyIntegrity();
if (valid) {
    // Keyring fonctionne correctement
}
```

## Migration automatique

Le KeyringManager migre automatiquement les clés depuis:
- `SecureConfig` (Hugging Face, Ollama Cloud)
- `SharedPreferences` (OpenAI, Anthropic)

La migration se fait au premier lancement, de manière transparente.

## Exemple complet

```java
// Dans une Activity ou Service
KeyringManager keyring = KeyringManager.getInstance(context);

// Sauvegarder une clé
keyring.setApiKey("huggingface", "hf_abc123...");

// Vérifier
if (keyring.hasApiKey("huggingface")) {
    String key = keyring.getApiKey("huggingface");
    // Utiliser la clé pour une requête API
}

// Obtenir un résumé pour diagnostics
String summary = keyring.getKeyringSummary();
Log.i("Keyring", summary);

// Export pour backup
String backup = keyring.exportKeys(true);
// Sauvegarder backup dans un fichier sécurisé
```

## Sécurité

- ✅ Chiffrement AES-256/GCM
- ✅ Android Keystore System (matériel sécurisé si disponible)
- ✅ IV unique par chiffrement
- ✅ Authentification GCM (détection de modification)
- ✅ Migration transparente depuis ancien système

## Intégration dans le code

### Dans AiConfigManager

```java
KeyringManager keyring = KeyringManager.getInstance(context);
String apiKey = keyring.getApiKey(provider);
keyring.setApiKey(provider, apiKey);
```

### Dans les Services

```kotlin
private val keyring = KeyringManager.getInstance(context)
val apiKey = keyring.getApiKey("huggingface")
```

### Dans les Activities

```kotlin
private lateinit var keyring: KeyringManager

override fun onCreate(savedInstanceState: Bundle?) {
    keyring = KeyringManager.getInstance(this)
    // ...
}
```

## Diagnostics

Le KeyringManager est intégré dans les diagnostics de `AIConfigurationActivity`:

```kotlin
val summary = keyring.getKeyringSummary()
diagnosticResult.appendLine(summary)
```

## Notes importantes

1. **Singleton**: Une seule instance par application
2. **Thread-safe**: Peut être utilisé depuis n'importe quel thread
3. **Migration**: Automatique au premier lancement
4. **Export**: Utiliser `exportKeys(true)` pour sécurité maximale
5. **Import**: Vérifier l'intégrité après import avec `verifyIntegrity()`


