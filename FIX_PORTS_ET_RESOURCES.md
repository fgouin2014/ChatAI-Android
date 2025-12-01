# Corrections des problèmes de ports et ressources

## Date: 2025-11-30

## Problèmes identifiés dans les logs

### 1. Port 8888 occupé
**Erreur:**
```
Port 8888 occupé (tentative 1/3), attente de 1000ms...
Port 8888 occupé (tentative 2/3), attente de 1000ms...
❌❌❌ ATTENTION CRITIQUE: Port 8888 toujours occupé après 3 tentatives
⚠️ Serveur web démarré sur port alternatif 8889 - WASM/EmulatorJS NE FONCTIONNERA PAS
```

**Cause:**
- Le port 8888 peut être en état TIME_WAIT après la fermeture d'une instance précédente
- TIME_WAIT peut durer jusqu'à 2 minutes sur certains systèmes
- Pas de réutilisation du port (SO_REUSEADDR non activé)

**Solution appliquée:**
- ✅ Augmenté les tentatives de 3 à 5
- ✅ Augmenté le délai d'attente de 1s à 2s entre chaque tentative
- ✅ Ajouté `SO_REUSEADDR` pour permettre la réutilisation du port même en TIME_WAIT
- ✅ Utilisation de `ServerSocket.setReuseAddress(true)` avant le bind

**Fichier modifié:** `WebServer.java`

### 2. WebSocketServer "Address already in use"
**Erreur:**
```
java.net.BindException: Address already in use
	at com.chatai.WebSocketServer.startServer(WebSocketServer.java:85)
```

**Cause:**
- Le serveur WebSocket essayait seulement une fois avec le port suivant
- Pas de logique de retry
- Pas de SO_REUSEADDR

**Solution appliquée:**
- ✅ Ajouté une logique de retry avec 5 tentatives
- ✅ Ajouté SO_REUSEADDR via `ServerSocket.setReuseAddress(true)`
- ✅ Amélioré la gestion d'erreur avec messages détaillés
- ✅ Délai de 500ms entre chaque tentative

**Fichier modifié:** `WebSocketServer.java`

### 3. Erreur "Invalid resource ID 0x00000000"
**Erreur:**
```
com.chatai E  Invalid resource ID 0x00000000.
```

**Cause:**
- Utilisation de `android.R.drawable.ic_dialog_info` qui peut ne pas exister sur certaines versions d'Android
- Ressource système non disponible

**Solution appliquée:**
- ✅ Remplacé par une ressource de l'app (`ic_launcher_foreground`)
- ✅ Ajouté un fallback vers `android.R.drawable.ic_menu_info_details` si la ressource n'existe pas
- ✅ Utilisation de `getResources().getIdentifier()` pour vérifier l'existence de la ressource

**Fichier modifié:** `BackgroundService.java`

## Code modifié

### WebServer.java
```java
// Avant
serverSocket = new ServerSocket(portToTry);
int maxRetries = 3;
int retryDelayMs = 1000;

// Après
serverSocket = new ServerSocket();
serverSocket.setReuseAddress(true); // ⭐ NOUVEAU
serverSocket.bind(new InetSocketAddress(portToTry));
int maxRetries = 5; // ⭐ AUGMENTÉ
int retryDelayMs = 2000; // ⭐ AUGMENTÉ
```

### WebSocketServer.java
```java
// Avant
try {
    serverChannel.bind(new InetSocketAddress("0.0.0.0", configuredPort));
} catch (BindException e) {
    // Essayer seulement le port suivant
    serverChannel.bind(new InetSocketAddress("0.0.0.0", configuredPort + 1));
}

// Après
for (int retry = 0; retry < maxRetries; retry++) {
    try {
        ServerSocket socket = serverChannel.socket();
        socket.setReuseAddress(true); // ⭐ NOUVEAU
        serverChannel.bind(new InetSocketAddress("0.0.0.0", portToTry));
        // ... succès
        return;
    } catch (BindException e) {
        // Retry avec port suivant
    }
}
```

### BackgroundService.java
```java
// Avant
.setSmallIcon(android.R.drawable.ic_dialog_info)

// Après
int iconResId = context.getResources().getIdentifier("ic_launcher_foreground", "drawable", context.getPackageName());
if (iconResId == 0) {
    iconResId = android.R.drawable.ic_menu_info_details; // Fallback
}
.setSmallIcon(iconResId)
```

## Résultat attendu

1. ✅ Port 8888 devrait se libérer plus rapidement grâce à SO_REUSEADDR
2. ✅ WebSocketServer devrait trouver un port disponible après quelques tentatives
3. ✅ Plus d'erreur "Invalid resource ID" dans les notifications
4. ✅ Meilleure stabilité lors du redémarrage de l'application

## Notes

- **SO_REUSEADDR** permet de réutiliser un port même s'il est en état TIME_WAIT
- Le port 8888 reste critique pour WASM/EmulatorJS (URLs codées en dur)
- Si le port 8888 est vraiment occupé par un autre processus, l'app utilisera le port 8889 avec un avertissement

