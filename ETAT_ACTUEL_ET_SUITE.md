# État Actuel et Suite - Communication IA/KITT

**Date:** 2025-11-18  
**Branche:** `feature/refactor-chatjs-modular`  
**Status:** ✅ Refactorisation modulaire terminée

---

## ✅ Ce qui est FAIT

### 1. Refactorisation Modulaire ✅
- **8 modules créés** et fonctionnels
- **chat.js désactivé** (remplacé par modules)
- **Toutes les méthodes complétées** dans chat-config.js
- **Tous les boutons fonctionnent** avec les modules

### 2. Intégration Phases dans Modules ✅

#### Phase 2 (BidirectionalBridge) - **STRUCTURE PRÊTE**
- ✅ `chat-bridge.js` créé avec `sendToKitt()` et `handleKittMessage()`
- ✅ `chat-messaging.js` appelle `chatBridge.sendToKitt()` dans `sendMessage()`
- ⚠️ **À vérifier:** `WebAppInterface.sendChatAIToKitt()` existe (ligne 946)
- ⚠️ **À vérifier:** Callback `window.onKittMessageReceived` implémenté dans WebAppInterface

#### Phase 1 (Whisper dans Chat) - **STRUCTURE PRÊTE**
- ✅ `chat-speech.js` créé avec `setupWhisperListener()` et `updateVUIndicator()`
- ✅ `chat-ui.js` a `updateVUIndicator()` et `updateVUIndicatorLevel()`
- ⚠️ **À vérifier:** `WebAppInterface.sttStartWhisper()` et `sttStopWhisper()` existent
- ⚠️ **À vérifier:** `WebAppInterface.isWhisperAvailable()` existe
- ⚠️ **À vérifier:** Callback `window.onWhisperEvent` implémenté dans WebAppInterface

#### Phase 4 (Hotword connecté) - **STRUCTURE PRÊTE**
- ✅ `chat-hotword.js` créé avec `handleHotwordMessage()`
- ⚠️ **À vérifier:** `BackgroundService.respondAI()` émet via BidirectionalBridge
- ⚠️ **À vérifier:** Messages hotword ont le bon `messageType` et `source`

---

## ⚠️ Ce qui reste à FAIRE

### 1. Vérifier/Compléter WebAppInterface.java

#### Méthodes à vérifier/ajouter:

**A. Callback KITT → Chat:**
```java
// Dans sendKittToChatAI() ou nouvelle méthode
// Appeler callback JavaScript pour notifier Chat
if (mContext instanceof MainActivity) {
    MainActivity activity = (MainActivity) mContext;
    WebView webView = activity.getWebView();
    if (webView != null) {
        String jsCode = String.format(
            "if (window.onKittMessageReceived) { " +
            "window.onKittMessageReceived(%s, %s); }",
            escapeForJavaScript(message),
            escapeForJavaScript(messageType)
        );
        new Handler(Looper.getMainLooper()).post(() -> {
            webView.evaluateJavascript(jsCode, null);
        });
    }
}
```

**B. Méthodes Whisper pour Chat:**
```java
@JavascriptInterface
public void sttStartWhisper() {
    // Créer WhisperServerRecognizer avec callback vers Chat
    // Émettre événements via window.onWhisperEvent()
}

@JavascriptInterface
public void sttStopWhisper() {
    // Arrêter WhisperServerRecognizer
}

@JavascriptInterface
public boolean isWhisperAvailable() {
    // Vérifier si Whisper Server est configuré
}
```

**C. Callback Whisper → Chat:**
```java
private void notifyChatWebappWhisper(String event, String data) {
    // Émettre via window.onWhisperEvent(event, data)
}
```

### 2. Compléter KittFragment.kt (Phase 2 + 3)

**A. Écoute des messages Chat → KITT:**
```kotlin
private fun setupBridgeListener() {
    val bridge = BidirectionalBridge.getInstance(requireContext())
    
    bridgeListenerJob = lifecycleScope.launch {
        bridge.webToKittMessages.collect { message ->
            if (isAdded && isVisible) {
                // Fragment visible → Animer
                handleKittMessage(message, true)
            } else {
                // Fragment masqué → Logger seulement
                Log.d(TAG, "Message reçu (Fragment masqué): ${message.content}")
                pendingMessages.add(message)
            }
        }
    }
    
    // Écouter thinkingStream pour synchroniser avec Chat
    bridgeListenerJob2 = lifecycleScope.launch {
        bridge.thinkingStream.collect { chunk ->
            if (isAdded && isVisible) {
                when (chunk.type) {
                    BidirectionalBridge.ChunkType.THINKING -> {
                        animationManager.startThinkingAnimation()
                        // Afficher thinking dans thinkingCard
                    }
                    BidirectionalBridge.ChunkType.RESPONSE -> {
                        // Afficher réponse
                    }
                }
            }
        }
    }
}
```

**B. Afficher messages en attente:**
```kotlin
override fun onResume() {
    super.onResume()
    if (pendingMessages.isNotEmpty()) {
        pendingMessages.forEach { message ->
            handleKittMessage(message, true)
        }
        pendingMessages.clear()
    }
}
```

### 3. Compléter BackgroundService.java (Phase 4)

**A. Émettre via BidirectionalBridge dans respondAI():**
```java
// Après transcription Whisper
WhisperServerRecognizer.Callback callback = new WhisperServerRecognizer.Callback() {
    @Override
    public void onResult(String text) {
        // ✅ Émettre USER_INPUT vers Chat via BidirectionalBridge
        BidirectionalBridge bridge = BidirectionalBridge.getInstance(BackgroundService.this);
        BridgeMessage userInput = new BridgeMessage(
            MessageType.USER_INPUT,
            Source.KITT_VOICE,
            text,
            Collections.singletonMap("source", "hotword"),
            System.currentTimeMillis()
        );
        bridge.sendKittToWebAsync(userInput);
        
        // Traiter avec IA via BidirectionalBridge
        bridge.processWithThinkingAsync(...);
    }
};
```

---

## 📋 Plan d'Action Recommandé

### Étape 1: Vérifier/Compléter WebAppInterface.java (2-3h)
1. ✅ Vérifier `sendChatAIToKitt()` existe
2. ⚠️ Ajouter callback JavaScript `window.onKittMessageReceived`
3. ⚠️ Ajouter méthodes Whisper: `sttStartWhisper()`, `sttStopWhisper()`, `isWhisperAvailable()`
4. ⚠️ Ajouter callback JavaScript `window.onWhisperEvent`

### Étape 2: Compléter KittFragment.kt (Phase 2 + 3) (2-3h)
1. ⚠️ Ajouter `setupBridgeListener()` pour écouter messages Chat → KITT
2. ⚠️ Ajouter écoute `thinkingStream` pour synchroniser animations
3. ⚠️ Ajouter gestion `pendingMessages` pour afficher quand Fragment redevient visible
4. ⚠️ Tester communication bidirectionnelle

### Étape 3: Compléter BackgroundService.java (Phase 4) (2-3h)
1. ⚠️ Modifier `respondAI()` pour émettre via BidirectionalBridge
2. ⚠️ Tester hotword → Chat (affichage dans historique)

### Étape 4: Tests Finaux (1-2h)
1. ⚠️ Tester TextInput Chat → KITT
2. ⚠️ Tester Whisper Chat → Transcription → Envoi
3. ⚠️ Tester Hotword → Chat (affichage)
4. ⚠️ Tester KITT masqué → Communication continue

**Temps total estimé:** 7-11 heures

---

## 🎯 Prochaine Étape Immédiate

**Option A: Compléter WebAppInterface.java** (recommandé)
- Ajouter les callbacks JavaScript manquants
- Ajouter les méthodes Whisper pour Chat
- C'est la base pour que les modules fonctionnent

**Option B: Compléter KittFragment.kt**
- Écoute des messages Chat → KITT
- Synchronisation animations

**Option C: Compléter BackgroundService.java**
- Hotword → Chat via BidirectionalBridge

---

## ✅ Validation Finale

Une fois tout complété, vérifier:
- [ ] TextInput Chat → KITT reçoit (logs + animation)
- [ ] Whisper Chat → Transcription → Envoi automatique
- [ ] Hotword → Message affiché dans Chat avec "[🔊 Hotword]"
- [ ] KITT masqué → Communication continue (logs)
- [ ] Thinking chunks affichés dans Chat et KITT (si visible)
- [ ] Réponses IA affichées dans Chat et lues par TTS (si KITT visible)

---

**Recommandation:** Commencer par **Option A (WebAppInterface.java)** car c'est la base pour que les modules modulaires fonctionnent correctement avec les phases intégrées.

