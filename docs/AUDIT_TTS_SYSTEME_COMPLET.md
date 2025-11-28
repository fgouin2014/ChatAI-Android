# Audit Complet Systeme TTS - ChatAI-Android

**Date**: 2025-01-27  
**Probleme**: Systeme TTS ne fonctionne pas - aucun son lors des tests

---

## Architecture TTS

Le systeme TTS utilise 4 composants principaux:

1. **KittTTSManager** - Manager principal TTS (Android TTS + TTSServerManager)
2. **TTSServerManager** - Client HTTP pour serveur TTS (port 11401)
3. **TTSServer** - Serveur HTTP natif Android (port 11401)
4. **OnnxTTSManager** - Synthese vocale ONNX (utilise par TTSServer)

### Flux de donnees

```
Interface Web (testTtsSynthesis)
    ↓
HTTP POST http://127.0.0.1:11401/synthesize
    ↓
TTSServer.java (BackgroundService)
    ↓
OnnxTTSManager.synthesizeToWav()
    ↓
Audio WAV → Reponse HTTP
    ↓
Interface Web (audioBlob.play())
```

**OU** (si utilise depuis KittTTSManager):

```
KittTTSManager.speak()
    ↓
TTSServerManager.speak() (si useTTSServer = true)
    ↓
HTTP POST http://127.0.0.1:11401/synthesize
    ↓
TTSServer → OnnxTTSManager
    ↓
Audio WAV → TTSServerManager
    ↓
AudioTrack.play()
```

---

## Problemes Identifies

### 1. CRITIQUE: TTSServerManager jamais initialise dans KittTTSManager

**Fichier**: `app/src/main/java/com/chatai/managers/KittTTSManager.kt`

**Ligne 60**: 
```kotlin
private var ttsServerManager: TTSServerManager? = null
```

**Probleme**: La variable `ttsServerManager` est declaree mais **JAMAIS initialisee**. Elle reste `null` en permanence.

**Ligne 327-330**:
```kotlin
if (useTTSServer && ttsServerManager?.isTTSReady() == true) {
    android.util.Log.d(TAG, "🔊 TTS Server: '$cleanText'")
    ttsServerManager?.speak(cleanText, utteranceId)
    return
}
```

**Impact**: 
- `useTTSServer` est toujours `false` (initialise ligne 61)
- `ttsServerManager` est toujours `null`
- Le code ne peut jamais utiliser TTSServerManager
- Fallback vers Android TTS natif uniquement

**Solution**: Initialiser TTSServerManager dans `initialize()` ou creer une fonction `initializeTTSServer()`.

---

### 2. CRITIQUE: TTSServerManager.initialize() jamais appele

**Fichier**: `app/src/main/java/com/chatai/managers/TTSServerManager.kt`

**Ligne 62-84**: La fonction `initialize()` existe mais n'est jamais appelee.

**Probleme**: Meme si on cree TTSServerManager, `isReady` reste `false` car `initialize()` n'est jamais appele.

**Impact**: 
- `isTTSReady()` retourne toujours `false`
- Le code dans KittTTSManager ligne 327 ne peut jamais passer la condition

**Solution**: Appeler `ttsServerManager?.initialize()` apres creation.

---

### 3. Test TTS depuis interface web - Pas de gestion erreur audio

**Fichier**: `app/src/main/assets/webapp/index.html`

**Ligne 2467-2510**: Fonction `testTtsSynthesis()`

**Probleme**: 
- Le code fait `audioElement.play().catch()` mais ne verifie pas si l'audio est vraiment joue
- Pas de verification du volume systeme
- Pas de log pour diagnostiquer les erreurs de lecture

**Ligne 2485-2488**:
```javascript
audioElement.play().catch(e => {
    console.error('Erreur lecture audio:', e);
    alert('✅ Audio généré mais erreur de lecture\n\nTaille: ' + (audioBlob.size / 1024).toFixed(2) + ' KB');
});
```

**Impact**: 
- L'audio peut etre genere mais ne pas jouer (volume mute, permissions, etc.)
- L'utilisateur ne sait pas si c'est un probleme de generation ou de lecture

**Solution**: 
- Ajouter verification volume
- Ajouter logs detailles
- Verifier que l'audio est bien decode

---

### 4. AudioTrack dans TTSServerManager - Pas de verification etat

**Fichier**: `app/src/main/java/com/chatai/managers/TTSServerManager.kt`

**Ligne 174-217**: Fonction `playAudio()`

**Probleme**: 
- Verifie `STATE_UNINITIALIZED` mais pas d'autres erreurs
- Pas de verification du volume systeme
- Pas de verification que l'audio est vraiment joue

**Ligne 199-212**:
```kotlin
track.play()
val bytesWritten = track.write(pcmData, 0, pcmData.size)

if (bytesWritten < 0) {
    Log.e(TAG, "Erreur écriture AudioTrack: $bytesWritten")
} else {
    Log.d(TAG, "Audio joué: $bytesWritten bytes")
    
    // Attendre fin lecture
    val durationMs = (pcmData.size * 1000 / SAMPLE_RATE).toLong()
    Thread.sleep(durationMs)
    
    track.stop()
}
```

**Impact**: 
- L'audio peut etre ecrit mais ne pas jouer (volume mute)
- Pas de feedback si l'audio est vraiment joue

**Solution**: 
- Ajouter verification volume AudioManager
- Ajouter callback pour confirmer lecture
- Verifier que `bytesWritten == pcmData.size`

---

### 5. TTSServer - Verification OnnxTTSManager non bloquante

**Fichier**: `app/src/main/java/com/chatai/TTSServer.java`

**Ligne 76**: `onnxTTSManager.initialize()` est appele mais l'initialisation est asynchrone.

**Probleme**: 
- `isONNXReady()` est verifie immediatement apres `initialize()`
- L'initialisation ONNX peut prendre plusieurs secondes
- Le serveur demarre meme si les modeles ne sont pas charges

**Ligne 79-85**:
```java
if (onnxTTSManager.isONNXReady()) {
    Log.i(TAG, "✅ OnnxTTSManager prêt (modèles chargés)");
} else {
    Log.w(TAG, "⚠️ OnnxTTSManager non prêt (modèles manquants ou erreur)");
    Log.w(TAG, "⚠️ Le serveur démarrera mais les requêtes de synthèse échoueront");
}
```

**Impact**: 
- Le serveur demarre mais retourne 503 si les modeles ne sont pas charges
- L'utilisateur ne sait pas que les modeles sont en cours de chargement

**Solution**: 
- Attendre que `isONNXReady()` soit `true` avant de demarrer le serveur
- OU: Retourner 503 avec message clair si modeles non charges

---

### 6. Permissions Audio - Verification manquante

**Fichier**: `app/src/main/AndroidManifest.xml`

**Ligne 29**: Permission `MODIFY_AUDIO_SETTINGS` presente.

**Probleme**: 
- Pas de verification runtime de la permission
- AudioTrack peut echouer silencieusement si permission refuse

**Impact**: 
- L'audio ne joue pas si permission refuse
- Pas de message d'erreur clair

**Solution**: 
- Verifier permission avant d'utiliser AudioTrack
- Demander permission si necessaire

---

### 7. Volume Systeme - Pas de verification

**Probleme**: 
- Aucune verification du volume systeme
- L'audio peut etre joue mais inaudible si volume = 0

**Impact**: 
- L'utilisateur ne sait pas pourquoi il n'entend rien
- Pas de message d'avertissement

**Solution**: 
- Verifier volume AudioManager avant lecture
- Afficher avertissement si volume = 0

---

## Solutions Proposees

### Solution 1: Initialiser TTSServerManager dans KittTTSManager

**Fichier**: `app/src/main/java/com/chatai/managers/KittTTSManager.kt`

**Ajouter dans `initialize()`**:
```kotlin
fun initialize() {
    if (textToSpeech == null) {
        textToSpeech = TextToSpeech(context, this)
        android.util.Log.d(TAG, "TTS initialisé au chargement")
    }
    
    // ⭐ NOUVEAU: Initialiser TTSServerManager
    if (ttsServerManager == null) {
        ttsServerManager = TTSServerManager(context, object : TTSServerManager.TTSListener {
            override fun onTTSReady() {
                useTTSServer = true
                android.util.Log.i(TAG, "✅ TTS Server prêt, utilisation activée")
            }
            
            override fun onTTSStart(utteranceId: String?) {
                listener.onTTSStart(utteranceId)
            }
            
            override fun onTTSDone(utteranceId: String?) {
                listener.onTTSDone(utteranceId)
            }
            
            override fun onTTSError(utteranceId: String?) {
                listener.onTTSError(utteranceId)
            }
        })
        ttsServerManager?.initialize()
    }
}
```

---

### Solution 2: Ameliorer gestion erreurs dans testTtsSynthesis

**Fichier**: `app/src/main/assets/webapp/index.html`

**Modifier fonction `testTtsSynthesis()`**:
```javascript
async function testTtsSynthesis(text) {
    // ... code existant ...
    
    try {
        const response = await fetch('http://127.0.0.1:11401/synthesize', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ 
                text: text.trim(),
                voice: selectedVoice
            })
        });
        
        if (response.ok) {
            const audioBlob = await response.blob();
            
            // ⭐ NOUVEAU: Vérifier taille audio
            if (audioBlob.size < 1000) {
                throw new Error('Audio trop petit (' + audioBlob.size + ' bytes) - probablement une erreur');
            }
            
            const audioUrl = URL.createObjectURL(audioBlob);
            
            if (audioElement) {
                audioElement.src = audioUrl;
                audioElement.style.display = 'block';
                
                // ⭐ NOUVEAU: Vérifier volume et ajouter logs
                console.log('Audio généré:', audioBlob.size, 'bytes');
                
                audioElement.volume = 1.0; // Forcer volume max
                
                const playPromise = audioElement.play();
                
                if (playPromise !== undefined) {
                    playPromise
                        .then(() => {
                            console.log('✅ Audio en cours de lecture');
                            if (testBtn) {
                                testBtn.textContent = '✅ Lecture en cours...';
                            }
                        })
                        .catch(e => {
                            console.error('❌ Erreur lecture audio:', e);
                            alert('❌ Erreur lecture audio\n\n' + e.message + '\n\nVérifiez:\n- Volume système\n- Permissions audio\n- Format audio supporté');
                        });
                }
            }
            
            // ... reste du code ...
        }
    } catch (e) {
        // ... gestion erreur ...
    }
}
```

---

### Solution 3: Verifier volume systeme avant lecture AudioTrack

**Fichier**: `app/src/main/java/com/chatai/managers/TTSServerManager.kt`

**Modifier fonction `playAudio()`**:
```kotlin
private fun playAudio(pcmData: ShortArray) {
    try {
        // ⭐ NOUVEAU: Vérifier volume système
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        if (currentVolume == 0) {
            Log.w(TAG, "⚠️ Volume système = 0, audio inaudible")
            // Optionnel: Afficher notification ou toast
        }
        
        Log.d(TAG, "Volume système: $currentVolume/$maxVolume")
        
        // ... reste du code existant ...
    } catch (e: Exception) {
        Log.e(TAG, "Erreur lecture audio: ${e.message}", e)
    }
}
```

---

### Solution 4: Attendre chargement modeles ONNX dans TTSServer

**Fichier**: `app/src/main/java/com/chatai/TTSServer.java`

**Modifier fonction `start()`**:
```java
public void start() {
    // ... code existant jusqu'à ligne 76 ...
    
    onnxTTSManager.initialize();
    
    // ⭐ NOUVEAU: Attendre que les modèles soient chargés (max 30 secondes)
    int maxWaitTime = 30000; // 30 secondes
    int waitInterval = 500; // 500ms
    int waited = 0;
    
    while (!onnxTTSManager.isONNXReady() && waited < maxWaitTime) {
        try {
            Thread.sleep(waitInterval);
            waited += waitInterval;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
    
    if (onnxTTSManager.isONNXReady()) {
        Log.i(TAG, "✅ OnnxTTSManager prêt (modèles chargés)");
    } else {
        Log.w(TAG, "⚠️ OnnxTTSManager non prêt après " + (waited / 1000) + " secondes");
        Log.w(TAG, "⚠️ Le serveur démarrera mais les requêtes de synthèse échoueront");
    }
    
    // ... reste du code ...
}
```

---

## Checklist de Diagnostic

Pour diagnostiquer un probleme TTS, verifier dans l'ordre:

1. **TTSServer demarre-t-il?**
   ```bash
   adb logcat | Select-String "TTSServer.*démarré"
   ```
   Attendu: `TTSServer: Serveur TTS démarré sur le port 11401`

2. **OnnxTTSManager est-il pret?**
   ```bash
   adb logcat | Select-String "OnnxTTSManager.*prêt"
   ```
   Attendu: `OnnxTTSManager: ✅ ONNX TTS prêt`

3. **Les modeles ONNX sont-ils presents?**
   ```bash
   adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/tts/
   ```
   Attendu: 3 fichiers .onnx (encoder, decoder, vocoder) + vocab.json + embeddings

4. **Le serveur repond-il au health check?**
   ```bash
   adb shell curl http://127.0.0.1:11401/health
   ```
   Attendu: `{"status":"ok","model_loaded":true,"server_running":true}`

5. **La requete de synthese fonctionne-t-elle?**
   ```bash
   adb logcat | Select-String "TTSServer.*Synthèse"
   ```
   Attendu: `TTSServer: ✅ Synthèse réussie: X bytes`

6. **L'audio est-il joue?**
   ```bash
   adb logcat | Select-String "AudioTrack|Audio joué"
   ```
   Attendu: `AudioTrack: Audio joué: X bytes`

7. **Le volume systeme est-il > 0?**
   - Verifier manuellement dans les parametres Android

---

## Priorites de Correction

1. **PRIORITE 1 (CRITIQUE)**: Initialiser TTSServerManager dans KittTTSManager
2. **PRIORITE 2 (CRITIQUE)**: Appeler `initialize()` sur TTSServerManager
3. **PRIORITE 3 (IMPORTANT)**: Ameliorer gestion erreurs dans testTtsSynthesis
4. **PRIORITE 4 (IMPORTANT)**: Verifier volume systeme avant lecture
5. **PRIORITE 5 (MOYEN)**: Attendre chargement modeles ONNX dans TTSServer
6. **PRIORITE 6 (MOYEN)**: Verifier permissions audio runtime

---

## Tests a Effectuer Apres Corrections

1. Test depuis interface web (bouton "Tester TTS")
2. Test depuis KittTTSManager.speak()
3. Test avec volume systeme = 0 (doit afficher avertissement)
4. Test avec modeles ONNX manquants (doit fallback Android TTS)
5. Test avec serveur TTS non demarre (doit fallback Android TTS)
6. Test avec permissions audio refusees (doit afficher erreur claire)

---

## Notes

- Le systeme TTS a 3 niveaux de fallback:
  1. TTSServerManager (ONNX via serveur HTTP) - PRIORITE
  2. OnnxTTSManager (ONNX direct) - NON UTILISE actuellement
  3. Android TTS natif - FALLBACK

- TTSServerManager n'est jamais utilise car jamais initialise
- Le test depuis l'interface web fonctionne directement via HTTP, pas via KittTTSManager
- L'audio peut etre genere mais ne pas jouer (volume, permissions, etc.)

