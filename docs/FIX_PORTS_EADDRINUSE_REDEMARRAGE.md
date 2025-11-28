# Fix: Erreurs EADDRINUSE au redémarrage de l'application

## Problème identifié

Lors du redémarrage de l'application ChatAI, les logs montraient des erreurs `EADDRINUSE (Address already in use)` pour plusieurs serveurs :

1. **WebServer** (port 8888) : `bind failed: EADDRINUSE` - ⚠️ **CRITIQUE** : Port utilisé pour WASM/EmulatorJS (GameLibrary)
2. **TTSServer** (port 11401) : `bind failed: EADDRINUSE`
3. **WebSocketServer** (port 8081) : `Address already in use`

### Cause racine

Quand l'application redémarre, les anciens processus peuvent encore avoir les ports ouverts, ou les ports peuvent être dans un état `TIME_WAIT` (quelques secondes après la fermeture du socket). Les serveurs `WebServer` et `TTSServer` n'avaient pas de logique de fallback pour gérer ces cas.

### Erreur TextToSpeech

Une erreur `DeadObjectException` se produisait également dans `KittTTSManager.onInit()` lors de la configuration de la langue française, indiquant que le service TTS Android s'était déconnecté.

## Corrections appliquées

### 1. Gestion de fallback pour les ports (WebServer)

**Fichier:** `ChatAI-Android/app/src/main/java/com/chatai/WebServer.java`

⚠️ **IMPORTANT : Le port 8888 est CRITIQUE pour WASM/EmulatorJS** - De nombreuses URLs sont codées en dur avec ce port.

- Ajout d'une variable `actualPort` pour stocker le port réel utilisé
- **Stratégie prioritaire** : Le port 8888 est essayé 3 fois avec un délai de 1 seconde entre chaque tentative (pour gérer les états TIME_WAIT)
- **Fallback en dernier recours** : Si le port 8888 est toujours occupé après 3 tentatives, un port alternatif (8889) est utilisé avec des **avertissements critiques** dans les logs
- **Avertissement** : Si un port alternatif est utilisé, les URLs WASM/EmulatorJS codées en dur ne fonctionneront pas

```java
// Essayer de démarrer sur le port configuré, avec fallback si occupé
int portToTry = PORT;
int maxAttempts = 10; // Essayer jusqu'à 10 ports consécutifs

for (int attempt = 0; attempt < maxAttempts; attempt++) {
    try {
        serverSocket = new ServerSocket(portToTry);
        actualPort = portToTry;
        // ... démarrage serveur ...
        if (portToTry != PORT) {
            Log.w(TAG, "Port " + PORT + " déjà utilisé, utilisation du port " + actualPort);
        }
        return; // Succès
    } catch (java.net.BindException e) {
        // Port occupé, essayer le suivant
        if (attempt < maxAttempts - 1) {
            portToTry++;
            Log.w(TAG, "Port " + (portToTry - 1) + " déjà utilisé, tentative avec port " + portToTry);
        }
    }
}
```

### 2. Gestion de fallback pour les ports (TTSServer)

**Fichier:** `ChatAI-Android/app/src/main/java/com/chatai/TTSServer.java`

- Même logique de fallback que pour `WebServer`
- Le serveur TTS essaiera les ports 11401, 11402, 11403, etc. jusqu'à trouver un port disponible

### 3. Gestion de DeadObjectException (KittTTSManager)

**Fichier:** `ChatAI-Android/app/src/main/java/com/chatai/managers/KittTTSManager.kt`

- Ajout d'un bloc `try-catch` autour de `setLanguage()` pour capturer `DeadObjectException`
- Si le service TTS Android est déconnecté, l'application continue de fonctionner avec le serveur TTS ONNX uniquement
- Logs d'avertissement au lieu d'erreurs fatales

```kotlin
try {
    val result = textToSpeech?.setLanguage(Locale.CANADA_FRENCH)
    // ... configuration TTS ...
} catch (e: android.os.DeadObjectException) {
    // Le service TTS Android s'est déconnecté (peut arriver au redémarrage)
    android.util.Log.w(TAG, "⚠️ Service TTS déconnecté (DeadObjectException), utilisation du serveur TTS uniquement")
    isTTSReady = false
    // Ne pas bloquer l'application, le serveur TTS ONNX peut toujours fonctionner
}
```

### 4. Méthodes getActualPort()

Ajout de méthodes `getActualPort()` dans `WebServer` et `TTSServer` pour permettre aux autres composants de connaître le port réel utilisé (utile si un port alternatif a été sélectionné).

## Résultat attendu

1. **Port 8888 prioritaire** : Le serveur essaiera 3 fois le port 8888 (avec délai) avant d'utiliser un port alternatif
2. **Plus d'erreurs EADDRINUSE** : Les serveurs trouveront automatiquement un port disponible
3. **Application plus robuste** : L'application ne plantera plus si le service TTS Android est déconnecté
4. **Avertissements critiques** : Si un port alternatif est utilisé, des logs d'erreur clairs indiqueront que WASM/EmulatorJS ne fonctionnera pas

## ⚠️ Limitations importantes

**Port 8888 et WASM/EmulatorJS :**
- Le port 8888 est utilisé pour servir les fichiers WASM/EmulatorJS
- De nombreuses URLs sont codées en dur avec `:8888` dans le code :
  - `GameDetailsActivity.java` : `http://localhost:8888/gamelibrary/emulator.html`
  - `GameListActivity.java` : `http://localhost:8888/gamedata/...`
  - `Game.java` : `http://localhost:8888/gamedata/...`
  - `system.html` : `http://localhost:8888/gamelibrary/`
- **Si un port alternatif est utilisé, ces URLs ne fonctionneront pas**
- **Solution recommandée** : Arrêter le processus qui occupe le port 8888 ou redémarrer l'application

## Notes importantes

- Le port par défaut sera toujours essayé en premier
- Si un port alternatif est utilisé, un avertissement sera loggé
- La logique de fallback est limitée à 10 ports consécutifs pour éviter de scanner trop de ports
- Le `WebSocketServer` avait déjà une logique de fallback similaire, maintenant tous les serveurs sont cohérents

## Tests recommandés

1. Redémarrer l'application plusieurs fois rapidement pour vérifier que les ports sont correctement gérés
2. Vérifier les logs pour confirmer qu'aucune erreur `EADDRINUSE` n'apparaît
3. Tester le TTS pour s'assurer qu'il fonctionne même si le service TTS Android est déconnecté

