# Modules JavaScript de ChatAI Webapp

## Vue d'ensemble

La webapp ChatAI utilise une architecture modulaire pour améliorer la maintenabilité, la testabilité et réduire les conflits Git.

## Architecture modulaire

```
┌─────────────────────────────────────────────────────────────────┐
│                    index.html                                    │
│  • Chargement des modules dans ordre dépendances                │
│  • Initialisation via chat-core.js                               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                  chat-core.js                                    │
│  Coordinateur principal                                          │
│  • Initialise tous les modules                                   │
│  • Délègue les appels aux modules appropriés                     │
│  • API publique (window.secureChatApp)                           │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┬───────────────┐
         │               │               │               │
         ▼               ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  chat-ui.js  │ │chat-messaging│ │chat-history.js│ │chat-config.js│
│  UI/Affichage│ │  Messages/IA │ │  Historique   │ │ Configuration│
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
         │               │               │               │
         ▼               ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│chat-speech.js│ │chat-bridge.js│ │chat-hotword.js│ │chat-diagnostics│
│   STT/TTS    │ │   KITT ↔ Web │ │   Hotword     │ │   Diagnostics  │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
         │
         ▼
┌──────────────┐
│chat-utils.js │
│   Utilitaires│
└──────────────┘
```

## Ordre de chargement

Les modules doivent être chargés dans l'ordre suivant (défini dans `index.html`):

1. `chat-utils.js` - Utilitaires (base pour tous)
2. `chat-ui.js` - Interface utilisateur
3. `chat-messaging.js` - Messages et IA (dépend de chat-ui)
4. `chat-bridge.js` - Bridge KITT ↔ Webapp (dépend de chat-ui, chat-messaging)
5. `chat-speech.js` - STT/TTS (dépend de chat-ui)
6. `chat-hotword.js` - Hotword (dépend de chat-ui, chat-speech)
7. `chat-history.js` - Historique (dépend de chat-ui, androidInterface)
8. `chat-config.js` - Configuration (dépend de chat-ui, androidInterface)
9. `chat-diagnostics.js` - Diagnostics (dépend de chat-ui, androidInterface)
10. `chat-core.js` - Coordinateur (dépend de tous les autres)

## Modules

### 1. chat-utils.js

**Responsabilité** : Utilitaires partagés entre tous les modules.

**API publique**:
```javascript
window.ChatUtils = {
    // Validation
    validateInput(text: string): boolean
    sanitizeInput(text: string): string
    
    // URLs
    getApiUrl(endpoint: string): string
    
    // Formatage
    formatTimestamp(timestamp: number): string
    formatDuration(ms: number): string
    
    // Autres
    debounce(func: Function, delay: number): Function
    throttle(func: Function, delay: number): Function
}
```

**Exemple d'utilisation**:
```javascript
if (window.ChatUtils.validateInput(userInput)) {
    const sanitized = window.ChatUtils.sanitizeInput(userInput);
    // ...
}
```

### 2. chat-ui.js

**Responsabilité** : Interface utilisateur (affichage, animations, interactions).

**Classe** : `ChatUI`

**Méthodes principales**:
```javascript
class ChatUI {
    constructor(container: HTMLElement)
    showSecureMessage(sender: 'user'|'ai', message: string, saveToHistory?: boolean, source?: string)
    showTypingIndicator()
    hideTypingIndicator()
    toggleInput(enabled: boolean)
    scrollToBottom()
    displayThinkingChunk(messageId: string, type: 'thinking'|'response', content: string, isComplete: boolean)
    speakText(text: string)
    copyToClipboard(text: string)
    maybeAutoPlayTTS(text: string)
}
```

**Exemple d'utilisation**:
```javascript
const chatUI = new window.ChatUI(document.getElementById('chatMessages'));
chatUI.showSecureMessage('user', 'Bonjour!');
chatUI.showTypingIndicator();
```

### 3. chat-messaging.js

**Responsabilité** : Messages et communication avec l'IA.

**Classe** : `ChatMessaging`

**Méthodes principales**:
```javascript
class ChatMessaging {
    constructor(androidInterface: Object, chatUI: ChatUI, chatBridge: ChatBridge)
    async sendMessage(text: string)
    async processRequestQueue()
    saveToHistory(sender: 'user'|'ai', text: string)
    loadConversationHistory()
    saveConversationToLocalStorage()
}
```

**Exemple d'utilisation**:
```javascript
const chatMessaging = new window.ChatMessaging(androidInterface, chatUI, chatBridge);
await chatMessaging.sendMessage('Bonjour!');
chatMessaging.saveToHistory('user', 'Bonjour!');
```

### 4. chat-bridge.js

**Responsabilité** : Communication bidirectionnelle entre KITT et Webapp.

**Classe** : `ChatBridge`

**Méthodes principales**:
```javascript
class ChatBridge {
    constructor(androidInterface: Object, chatUI: ChatUI, chatMessaging: ChatMessaging)
    sendToKitt(message: string, messageType?: string)
    handleKittMessage(message: string, messageType: string, source?: string)
    startPolling()
    stopPolling()
    destroy()
}
```

**Exemple d'utilisation**:
```javascript
const chatBridge = new window.ChatBridge(androidInterface, chatUI, chatMessaging);
chatBridge.sendToKitt('Bonjour!', 'USER_INPUT');
chatBridge.handleKittMessage('Réponse KITT', 'AI_RESPONSE', 'kitt_voice');
```

### 5. chat-speech.js

**Responsabilité** : Reconnaissance vocale (STT) et synthèse vocale (TTS).

**Classe** : `ChatSpeech`

**Méthodes principales**:
```javascript
class ChatSpeech {
    constructor(androidInterface: Object, chatUI: ChatUI)
    async startListening()
    stopListening()
    async speakText(text: string)
    isListening(): boolean
    isSpeaking(): boolean
}
```

**Exemple d'utilisation**:
```javascript
const chatSpeech = new window.ChatSpeech(androidInterface, chatUI);
await chatSpeech.startListening();
await chatSpeech.speakText('Bonjour!');
```

### 6. chat-hotword.js

**Responsabilité** : Détection de hotword (mots-clés déclencheurs).

**Classe** : `ChatHotword`

**Méthodes principales**:
```javascript
class ChatHotword {
    constructor(androidInterface: Object, chatUI: ChatUI, chatSpeech: ChatSpeech)
    enableHotword()
    disableHotword()
    isHotwordEnabled(): boolean
}
```

**Exemple d'utilisation**:
```javascript
const chatHotword = new window.ChatHotword(androidInterface, chatUI, chatSpeech);
chatHotword.enableHotword();
```

### 7. chat-history.js

**Responsabilité** : Historique des conversations (affichage, recherche, export).

**Classe** : `ChatHistory`

**Méthodes principales**:
```javascript
class ChatHistory {
    constructor(androidInterface: Object)
    async loadConversations(query?: string)
    async loadStats()
    renderConversations()
    handleSearch()
    clearSearch()
    async exportConversations(format: 'json'|'html')
    async deleteAllConversations()
    showConversationDetails(conversation: Object)
}
```

**Exemple d'utilisation**:
```javascript
const chatHistory = new window.ChatHistory(androidInterface);
await chatHistory.loadConversations();
await chatHistory.loadStats();
chatHistory.handleSearch();
```

### 8. chat-config.js

**Responsabilité** : Configuration (AI, STT, TTS, Hotword, etc.).

**Classe** : `ChatConfig`

**Méthodes principales**:
```javascript
class ChatConfig {
    constructor(androidInterface: Object, chatUI: ChatUI)
    async loadAiConfigPreview(refresh?: boolean)
    async saveAiConfig(configJson: string)
    async testConnection()
    async listModels()
    async testSTT()
    async testTTS()
    async testHotword()
}
```

**Exemple d'utilisation**:
```javascript
const chatConfig = new window.ChatConfig(androidInterface, chatUI);
await chatConfig.loadAiConfigPreview();
await chatConfig.saveAiConfig('{"mode": "cloud"}');
```

### 9. chat-diagnostics.js

**Responsabilité** : Diagnostics (logs, statuts services, informations système).

**Classe** : `ChatDiagnostics`

**Méthodes principales**:
```javascript
class ChatDiagnostics {
    constructor(androidInterface: Object, chatUI: ChatUI)
    async loadDiagnostics()
    async refreshDiagnostics()
    renderLogs()
    renderSystemInfo()
    renderServicesStatus()
    async exportDiagnostics()
}
```

**Exemple d'utilisation**:
```javascript
const chatDiagnostics = new window.ChatDiagnostics(androidInterface, chatUI);
await chatDiagnostics.loadDiagnostics();
chatDiagnostics.refreshDiagnostics();
```

### 10. chat-core.js

**Responsabilité** : Coordinateur principal, initialise tous les modules.

**Classe** : `SecureMobileAIChat`

**API publique** (exposée via `window.secureChatApp`):
```javascript
window.secureChatApp = {
    // Modules
    chatUI: ChatUI,
    chatMessaging: ChatMessaging,
    chatBridge: ChatBridge,
    chatSpeech: ChatSpeech,
    chatHotword: ChatHotword,
    chatHistory: ChatHistory,
    chatConfig: ChatConfig,
    chatDiagnostics: ChatDiagnostics,
    
    // État
    personality: string,
    language: string,
    currentModel: string,
    
    // Méthodes
    async initialize()
    switchView(viewId: string)
    saveToHistory(sender: string, text: string)
}
```

**Exemple d'utilisation**:
```javascript
// Initialisation automatique au chargement
// Ou manuellement:
await window.secureChatApp.initialize();

// Accéder aux modules:
window.secureChatApp.chatUI.showSecureMessage('user', 'Bonjour!');
window.secureChatApp.chatMessaging.sendMessage('Bonjour!');
```

## Dépendances entre modules

```
chat-utils.js
    ↓
chat-ui.js
    ↓
chat-messaging.js ─┐
    ↓              │
chat-bridge.js ────┤
    ↓              │
chat-speech.js ────┤
    ↓              │
chat-hotword.js ───┤
    ↓              │
chat-history.js ───┤
    ↓              │
chat-config.js ────┤
    ↓              │
chat-diagnostics.js┤
    ↓              │
chat-core.js ←─────┘
```

## Diagramme de dépendances

```
┌──────────────┐
│chat-utils.js │ (base)
└──────┬───────┘
       │
       ├─────→ chat-ui.js
       │       │
       │       ├─────→ chat-messaging.js
       │       │       │
       │       │       └─────→ chat-bridge.js
       │       │
       │       ├─────→ chat-speech.js
       │       │       │
       │       │       └─────→ chat-hotword.js
       │       │
       │       ├─────→ chat-history.js
       │       │
       │       ├─────→ chat-config.js
       │       │
       │       └─────→ chat-diagnostics.js
       │
       └─────→ chat-core.js (coordinateur)
```

## Exemples d'utilisation

### Envoi d'un message

```javascript
// Via chat-core (recommandé)
await window.secureChatApp.chatMessaging.sendMessage('Bonjour!');

// Ou directement via module
const chatMessaging = window.secureChatApp.chatMessaging;
await chatMessaging.sendMessage('Bonjour!');
```

### Affichage d'un message

```javascript
// Via chat-core
window.secureChatApp.chatUI.showSecureMessage('user', 'Bonjour!');

// Ou directement via module
const chatUI = window.secureChatApp.chatUI;
chatUI.showSecureMessage('user', 'Bonjour!');
```

### Recherche dans l'historique

```javascript
// Via chat-core
const chatHistory = window.secureChatApp.chatHistory;
await chatHistory.loadConversations('Python');
chatHistory.handleSearch();
```

### Configuration

```javascript
// Via chat-core
const chatConfig = window.secureChatApp.chatConfig;
await chatConfig.loadAiConfigPreview();
await chatConfig.saveAiConfig('{"mode": "cloud"}');
```

## Bonnes pratiques

### 1. Toujours passer par chat-core

**Bonne pratique**:
```javascript
await window.secureChatApp.chatMessaging.sendMessage('Bonjour!');
```

**Mauvaise pratique**:
```javascript
// Créer une nouvelle instance (ne pas faire)
const messaging = new window.ChatMessaging(...);
```

### 2. Utiliser les modules via window.secureChatApp

**Bonne pratique**:
```javascript
const chatUI = window.secureChatApp.chatUI;
chatUI.showSecureMessage('user', 'Bonjour!');
```

**Mauvaise pratique**:
```javascript
// Accéder directement aux classes globales (possible mais non recommandé)
const chatUI = new window.ChatUI(...);
```

### 3. Gérer les dépendances

**Bonne pratique**:
```javascript
// Vérifier que le module est initialisé
if (window.secureChatApp && window.secureChatApp.chatUI) {
    window.secureChatApp.chatUI.showSecureMessage('user', 'Bonjour!');
}
```

## Tests unitaires (futur)

Chaque module peut être testé indépendamment:

```javascript
// Test chat-utils.js
describe('ChatUtils', () => {
    it('should validate input', () => {
        expect(window.ChatUtils.validateInput('Bonjour!')).toBe(true);
        expect(window.ChatUtils.validateInput('')).toBe(false);
    });
});

// Test chat-ui.js
describe('ChatUI', () => {
    it('should display message', () => {
        const chatUI = new window.ChatUI(mockContainer);
        chatUI.showSecureMessage('user', 'Bonjour!');
        // Vérifier que le message est affiché
    });
});
```

## Maintenance

### Ajouter un nouveau module

1. Créer `chat-newmodule.js` dans `app/src/main/assets/webapp/`
2. Définir la classe et l'exporter: `window.ChatNewModule = ChatNewModule`
3. Ajouter dans `index.html` avant `chat-core.js`
4. Initialiser dans `chat-core.js`:
   ```javascript
   this.chatNewModule = new window.ChatNewModule(...);
   ```
5. Exposer via `window.secureChatApp.chatNewModule`

### Modifier un module existant

1. Modifier uniquement le module concerné
2. Vérifier que l'API publique n'a pas changé (ou documenter les changements)
3. Tester avec les autres modules

### Déboguer un module

```javascript
// Activer les logs pour un module spécifique
window.ChatUtils.debug = true; // Pour chat-utils
// Ou directement dans le module:
console.log('✅ ChatMessaging chargé');
```

## Ressources

- [Architecture modulaire JavaScript](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Modules)
- [IIFE Pattern](https://developer.mozilla.org/en-US/docs/Glossary/IIFE)
- [Documentation Room DB](../database/ConversationDao.kt)

