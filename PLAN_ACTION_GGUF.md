# 📋 PLAN D'ACTION - Résolution Problème Modèle GGUF

## 🎯 OBJECTIF
Faire fonctionner la détection et l'utilisation du modèle GGUF (`gemma3-270m.gguf`) sur le device, **SANS TOUCHER À L'INTERFACE KITT**.

---

## ✅ ÉTAPES DÉJÀ COMPLÉTÉES

### 1. ✅ Endpoint API créé
- [x] `/api/scan-gguf-models` ajouté dans `HttpServer.java` (port 8080)
- [x] Fonction `handleScanGGUFModels()` implémentée
- [x] Logs de débogage ajoutés

### 2. ✅ Chemin unifié
- [x] `LocalGGUFService.kt` utilise maintenant `/storage/emulated/0/ChatAI-Files/models/`
- [x] Même chemin que `HttpServer.java`

### 3. ✅ JavaScript amélioré
- [x] `scanLocalGGUFModels()` utilise `getHttpServerUrl()`
- [x] `loadLocalDeviceModels()` utilise `getHttpServerUrl()`
- [x] Appel automatique quand onglet Local ouvert
- [x] Logs de débogage ajoutés

---

## 🔧 PROCHAINES ÉTAPES (À FAIRE)

### ÉTAPE 1: Vérifier le modèle sur le device ⚠️ CRITIQUE

**Commande:**
```bash
# Vérifier que le répertoire existe
adb shell ls -la /storage/emulated/0/ChatAI-Files/models/

# Vérifier si le modèle est présent
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/*.gguf
```

**Si le modèle n'existe pas:**
```bash
# Créer le répertoire
adb shell mkdir -p /storage/emulated/0/ChatAI-Files/models

# Copier le modèle depuis votre PC (si vous l'avez)
adb push gemma3-270m.gguf /storage/emulated/0/ChatAI-Files/models/
```

**Résultat attendu:**
- Répertoire existe
- Fichier `gemma3-270m.gguf` présent (environ 278 MB)

---

### ÉTAPE 2: Tester l'endpoint API

**Commande:**
```bash
# Tester l'endpoint directement
adb shell curl http://127.0.0.1:8080/api/scan-gguf-models
```

**Résultat attendu:**
```json
{
  "models": [
    {
      "name": "gemma3-270m.gguf",
      "size": "278.5 MB",
      "path": "/storage/emulated/0/ChatAI-Files/models/gemma3-270m.gguf"
    }
  ],
  "count": 1,
  "directory": "/storage/emulated/0/ChatAI-Files/models",
  "directoryExists": true,
  "directoryReadable": true
}
```

**Si erreur:**
- Vérifier que HttpServer (port 8080) est démarré
- Vérifier les logs: `adb logcat | Select-String 'HttpServer'`

---

### ÉTAPE 3: Vérifier les logs WebView

**Commande:**
```bash
# Vérifier les logs de scan
adb logcat | Select-String 'scan-gguf-models'

# Vérifier les logs JavaScript (si visible)
adb logcat | Select-String 'loadLocalDeviceModels'
```

**Résultat attendu:**
- Logs montrent le scan réussi
- Logs montrent les modèles trouvés

**Si pas de logs:**
- Le JavaScript n'appelle peut-être pas l'endpoint
- Vérifier la console WebView (Chrome DevTools: `chrome://inspect`)

---

### ÉTAPE 4: Tester dans l'application

**Actions:**
1. Recompiler l'application
2. Ouvrir l'onglet "Général"
3. Sélectionner "📱 Local GGUF (Device)"
4. Vérifier que la liste se remplit
5. Ouvrir l'onglet "Local"
6. Vérifier que la liste se remplit aussi

**Si la liste reste bloquée:**
- Ouvrir Chrome DevTools (`chrome://inspect`)
- Vérifier la console JavaScript
- Chercher les erreurs `[loadLocalDeviceModels]`

---

### ÉTAPE 5: Améliorer le debugging (optionnel)

**Si le problème persiste, ajouter:**

1. **Logging unifié dans WebAppInterface:**
```java
@JavascriptInterface
public void log(String level, String message) {
    Log.d("WebView", "[$level] $message");
}
```

2. **Wrapper JavaScript pour appels Android:**
```javascript
async function callAndroid(method, ...args) {
    try {
        const result = await window.AndroidApp[method](...args);
        return { success: true, data: result };
    } catch (error) {
        console.error(`Android call failed: ${method}`, error);
        window.AndroidApp.log("ERROR", `Android call failed: ${method} - ${error.message}`);
        return { success: false, error: error.message };
    }
}
```

**Impact:** Aucun sur l'interface KITT, juste pour déboguer

---

## 🎯 RÉSULTAT ATTENDU

### Interface WebApp
- ✅ Onglet "Général" → Mode "Local GGUF" → Liste des modèles visible
- ✅ Onglet "Local" → Liste des modèles GGUF visible
- ✅ Bouton "🔄 Scanner" fonctionne
- ✅ Sélection d'un modèle fonctionne

### Backend
- ✅ Endpoint `/api/scan-gguf-models` répond correctement
- ✅ `LocalGGUFService` trouve le modèle
- ✅ Modèle initialisable et utilisable

### Interface KITT
- ✅ **100% préservée** (aucun changement)

---

## 🚨 SI LE PROBLÈME PERSISTE

### Checklist de diagnostic:

1. **Modèle présent?**
   ```bash
   adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/*.gguf
   ```

2. **Endpoint répond?**
   ```bash
   adb shell curl http://127.0.0.1:8080/api/scan-gguf-models
   ```

3. **HttpServer démarré?**
   ```bash
   adb logcat | Select-String 'Serveur HTTP démarré'
   ```

4. **JavaScript appelle l'endpoint?**
   - Chrome DevTools → Console → Chercher `[loadLocalDeviceModels]`

5. **Erreurs dans les logs?**
   ```bash
   adb logcat | Select-String 'Error|Exception|Failed'
   ```

---

## 📝 ORDRE D'EXÉCUTION

1. **Vérifier le modèle sur device** (ÉTAPE 1)
2. **Tester l'endpoint** (ÉTAPE 2)
3. **Recompiler et tester** (ÉTAPE 4)
4. **Si problème → Vérifier logs** (ÉTAPE 3)
5. **Si toujours problème → Améliorer debugging** (ÉTAPE 5)

---

## ✅ VALIDATION FINALE

Le problème est résolu quand:
- ✅ Le modèle apparaît dans la liste (onglet Général)
- ✅ Le modèle apparaît dans la liste (onglet Local)
- ✅ Le modèle peut être sélectionné
- ✅ Le modèle peut être initialisé
- ✅ L'interface KITT fonctionne toujours (aucun changement)

---

## 🔒 GARANTIE

**AUCUNE fonctionnalité KITT ne sera modifiée ou supprimée.**

Toutes les améliorations sont uniquement pour:
- Résoudre le problème de détection du modèle
- Améliorer le debugging (invisible pour l'utilisateur)
- Améliorer la stabilité (invisible pour l'utilisateur)

