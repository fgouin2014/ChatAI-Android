# Corrections TTS Appliquées

**Date**: 2025-01-27  
**Base**: Audit complet dans `AUDIT_TTS_SYSTEME_COMPLET.md`

---

## Corrections Appliquées

### 1. ✅ Initialisation TTSServerManager dans KittTTSManager

**Fichier**: `app/src/main/java/com/chatai/managers/KittTTSManager.kt`

**Problème**: `ttsServerManager` était déclaré mais jamais initialisé, restant `null` en permanence.

**Solution**: Ajout de l'initialisation dans `initialize()`:
- Création de `TTSServerManager` avec listener complet
- Appel de `initialize()` pour vérifier disponibilité serveur
- Activation automatique de `useTTSServer` quand serveur prêt
- Fallback automatique vers Android TTS si serveur indisponible

**Code ajouté**:
```kotlin
// ⭐ NOUVEAU: Initialiser TTSServerManager (priorité sur Android TTS)
if (ttsServerManager == null) {
    ttsServerManager = TTSServerManager(context, object : TTSServerManager.TTSListener {
        override fun onTTSReady() {
            useTTSServer = true
            android.util.Log.i(TAG, "✅ TTS Server prêt, utilisation activée")
        }
        // ... callbacks complets ...
    })
    ttsServerManager?.initialize()
}
```

**Impact**: 
- TTSServerManager est maintenant utilisé si disponible
- Fallback automatique vers Android TTS si serveur indisponible
- Logs clairs pour diagnostiquer l'état du serveur

---

### 2. ✅ Vérification volume système dans TTSServerManager

**Fichier**: `app/src/main/java/com/chatai/managers/TTSServerManager.kt`

**Problème**: Pas de vérification du volume système, audio pouvait être joué mais inaudible.

**Solution**: 
- Ajout import `AudioManager`
- Vérification volume avant lecture
- Logs d'avertissement si volume = 0
- Meilleure gestion erreurs AudioTrack (codes d'erreur détaillés)
- Vérification taille buffer AudioTrack

**Code ajouté**:
```kotlin
// ⭐ NOUVEAU: Vérifier volume système
val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

if (currentVolume == 0) {
    Log.w(TAG, "⚠️ Volume système = 0, audio inaudible")
    Log.w(TAG, "⚠️ Veuillez augmenter le volume système pour entendre la synthèse vocale")
} else {
    Log.d(TAG, "Volume système: $currentVolume/$maxVolume")
}
```

**Impact**:
- Détection automatique si volume = 0
- Logs clairs pour diagnostiquer problèmes audio
- Meilleure gestion erreurs AudioTrack

---

### 3. ✅ Amélioration gestion erreurs dans testTtsSynthesis (interface web)

**Fichier**: `app/src/main/assets/webapp/index.html`

**Problème**: Gestion erreurs minimale, pas de vérification taille audio, pas de logs détaillés.

**Solution**:
- Vérification taille audio (doit être > 1000 bytes)
- Logs détaillés console pour diagnostic
- Meilleure gestion promesse `play()`
- Gestion événements `ended` et `error` sur audioElement
- Messages d'erreur spécifiques selon type d'erreur HTTP
- Messages d'erreur spécifiques selon type d'erreur audio (NotAllowedError, NotSupportedError, etc.)

**Code ajouté**:
```javascript
// ⭐ NOUVEAU: Vérifier taille audio
if (audioBlob.size < 1000) {
    throw new Error('Audio trop petit (' + audioBlob.size + ' bytes) - probablement une erreur de synthèse');
}

// ⭐ NOUVEAU: Meilleure gestion de la lecture audio
const playPromise = audioElement.play();
if (playPromise !== undefined) {
    playPromise
        .then(() => {
            console.log('✅ Audio en cours de lecture');
            // Gestion événements ended/error
        })
        .catch(e => {
            // Messages d'erreur spécifiques selon type
        });
}
```

**Impact**:
- Détection erreurs plus tôt (taille audio)
- Messages d'erreur clairs et spécifiques
- Logs console pour diagnostic
- Meilleure expérience utilisateur

---

### 4. ✅ Vérification volume système dans OnnxTTSManager

**Fichier**: `app/src/main/java/com/chatai/managers/OnnxTTSManager.kt`

**Problème**: Pas de vérification volume système, même problème que TTSServerManager.

**Solution**:
- Ajout import `AudioManager`
- Vérification volume avant lecture (identique à TTSServerManager)
- Meilleure gestion erreurs AudioTrack
- Vérification taille buffer AudioTrack

**Code ajouté**: Identique à TTSServerManager (vérification volume + gestion erreurs)

**Impact**:
- Cohérence entre TTSServerManager et OnnxTTSManager
- Détection volume = 0 dans tous les cas
- Logs clairs pour diagnostic

---

## Résumé des Modifications

### Fichiers Modifiés

1. **KittTTSManager.kt**
   - Ajout initialisation TTSServerManager dans `initialize()`
   - Ajout listener complet pour callbacks TTS Server

2. **TTSServerManager.kt**
   - Ajout import `AudioManager`
   - Vérification volume système dans `playAudio()`
   - Amélioration gestion erreurs AudioTrack

3. **OnnxTTSManager.kt**
   - Ajout import `AudioManager`
   - Vérification volume système dans `playAudio()`
   - Amélioration gestion erreurs AudioTrack

4. **index.html**
   - Amélioration fonction `testTtsSynthesis()`
   - Vérification taille audio
   - Meilleure gestion promesse `play()`
   - Messages d'erreur spécifiques

---

## Tests à Effectuer

### 1. Test depuis interface web
- Ouvrir onglet TTS dans configuration
- Cliquer "Tester TTS"
- Vérifier que l'audio joue
- Vérifier logs console (F12) pour messages détaillés

### 2. Test depuis KittTTSManager
- Utiliser `ttsManager.speak("test")` depuis code
- Vérifier logs logcat pour voir quel moteur TTS est utilisé
- Vérifier que TTSServerManager est utilisé si serveur disponible

### 3. Test avec volume = 0
- Mettre volume système à 0
- Tester synthèse TTS
- Vérifier que logs affichent avertissement volume = 0

### 4. Test avec serveur TTS non démarré
- Arrêter BackgroundService
- Tester synthèse TTS
- Vérifier fallback vers Android TTS natif

### 5. Test avec modèles ONNX manquants
- Supprimer modèles ONNX
- Tester synthèse TTS
- Vérifier fallback vers Android TTS natif

---

## Commandes de Diagnostic

### Vérifier état TTSServerManager
```bash
adb logcat | Select-String "TTSServerManager|KittTTSManager"
```

### Vérifier volume système
```bash
adb logcat | Select-String "Volume système"
```

### Vérifier erreurs AudioTrack
```bash
adb logcat | Select-String "AudioTrack|Audio joué"
```

### Vérifier synthèse TTS
```bash
adb logcat | Select-String "Synthèse vocale|TTS Server"
```

---

## Prochaines Étapes (Optionnelles)

### Améliorations Futures

1. **Attendre chargement modèles ONNX dans TTSServer**
   - Actuellement: Serveur démarre même si modèles non chargés
   - Suggestion: Attendre max 30 secondes que modèles soient chargés

2. **Vérification permissions audio runtime**
   - Actuellement: Pas de vérification runtime
   - Suggestion: Vérifier permission `MODIFY_AUDIO_SETTINGS` avant utilisation AudioTrack

3. **Notification utilisateur si volume = 0**
   - Actuellement: Seulement logs
   - Suggestion: Toast ou notification pour avertir utilisateur

4. **Métriques de performance**
   - Temps de synthèse
   - Taille audio généré
   - Latence lecture

---

## Notes

- Toutes les corrections sont rétrocompatibles
- Le système TTS a toujours 3 niveaux de fallback:
  1. TTSServerManager (ONNX via serveur HTTP) - **MAINTENANT ACTIF**
  2. OnnxTTSManager (ONNX direct) - Non utilisé actuellement
  3. Android TTS natif - Fallback

- Les logs sont maintenant beaucoup plus détaillés pour faciliter le diagnostic
- Les erreurs sont mieux gérées avec messages spécifiques selon le type d'erreur

