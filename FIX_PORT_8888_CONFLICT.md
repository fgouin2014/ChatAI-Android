# 🔧 FIX: Conflit Port 8888 - WebServer

**Date**: 2025-11-30  
**Problème**: Port 8888 occupé par une instance précédente de l'application

---

## 🐛 PROBLÈME IDENTIFIÉ

### Symptômes
- Le WebServer ne peut pas démarrer sur le port 8888
- Erreur: `Port 8888 toujours occupé après 5 tentatives`
- Fallback vers port 8889, mais WASM/EmulatorJS ne fonctionne pas (URLs codées en dur)

### Cause
- Instance précédente de l'application n'a pas libéré le port correctement
- Socket en état TIME_WAIT (jusqu'à 2 minutes)
- Processus zombie qui occupe le port

---

## ✅ CORRECTIONS APPLIQUÉES

### 1. Amélioration de la méthode `stop()`

**Fichier**: `WebServer.java`

**Changements**:
- Fermeture synchrone du socket avec `serverSocket.close()`
- Attente que le thread se termine (`serverThread.join(1000)`)
- Mise à null explicite du socket après fermeture
- Logging amélioré pour le débogage

**Code**:
```java
public void stop() {
    if (!isRunning && serverSocket == null) {
        return;
    }
    
    isRunning = false;
    
    try {
        if (serverSocket != null && !serverSocket.isClosed()) {
            // ⭐ FIX: Fermer le socket de manière synchrone pour libérer le port immédiatement
            serverSocket.close();
            Log.d(TAG, "Socket fermé, port libéré");
        }
    } catch (IOException e) {
        Log.e(TAG, "Erreur arrêt serveur web", e);
    } finally {
        // ⭐ FIX: S'assurer que le socket est null après fermeture
        serverSocket = null;
    }
    
    if (serverThread != null) {
        serverThread.interrupt();
        try {
            // ⭐ FIX: Attendre que le thread se termine (max 1 seconde)
            serverThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interruption lors de l'attente de l'arrêt du thread", e);
        }
        serverThread = null;
    }
    
    Log.i(TAG, "Serveur web arrêté");
}
```

### 2. Augmentation du délai de nettoyage

**Changements**:
- Délai d'attente après `stop()` augmenté de 500ms à 1000ms
- Permet une libération plus complète du port avant redémarrage

### 3. Amélioration de la stratégie de retry

**Changements**:
- Nombre de tentatives augmenté de 5 à 10
- Délai entre tentatives réduit de 2000ms à 1000ms
- **Total**: 10 secondes d'attente au lieu de 10 secondes (même durée, mais plus de chances de succès)

**Code**:
```java
int maxRetries = 10; // ⭐ AUGMENTÉ: Réessayer 10 fois le port 8888
int retryDelayMs = 1000; // ⭐ AUGMENTÉ: Attendre 1 seconde entre chaque tentative
```

---

## 📊 IMPACT

### Avant
- 5 tentatives × 2 secondes = 10 secondes d'attente
- Port parfois toujours occupé après les tentatives
- Fallback vers port 8889 (WASM ne fonctionne pas)

### Après
- 10 tentatives × 1 seconde = 10 secondes d'attente (même durée)
- Plus de chances de succès (10 tentatives au lieu de 5)
- Meilleur nettoyage lors de l'arrêt (socket fermé proprement)
- Thread attendu pour terminer avant redémarrage

---

## 🔍 VÉRIFICATIONS

### Points à vérifier
1. ✅ Socket fermé proprement dans `stop()`
2. ✅ Thread attendu pour terminer
3. ✅ Socket mis à null après fermeture
4. ✅ Délai d'attente augmenté après `stop()`
5. ✅ Plus de tentatives avec délai réduit

### Tests recommandés
1. Démarrer l'app
2. Forcer l'arrêt (swipe away)
3. Redémarrer immédiatement
4. Vérifier que le port 8888 est libéré correctement

---

## ⚠️ NOTES IMPORTANTES

### Port 8888 critique
- **WASM/EmulatorJS** utilise des URLs codées en dur avec port 8888
- Si le port change, ces fonctionnalités ne fonctionneront **PAS**
- Le fallback vers port 8889 est un dernier recours avec avertissement

### Solutions alternatives (si problème persiste)
1. **Redémarrer l'app complètement** (force stop)
2. **Redémarrer le device** (libère tous les ports)
3. **Vérifier les processus zombies** avec `adb shell netstat -an | grep 8888`

---

## ✅ RÉSULTAT ATTENDU

- Port 8888 libéré correctement lors de l'arrêt
- Redémarrage réussi dans la plupart des cas
- Moins de fallback vers port 8889
- WASM/EmulatorJS fonctionne correctement

