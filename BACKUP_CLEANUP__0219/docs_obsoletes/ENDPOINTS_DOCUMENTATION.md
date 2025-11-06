# 📡 Documentation Complète des Endpoints - ChatAI-Android

## 🌐 **API HTTP (Port 8080)**

### **GET Endpoints**

| Endpoint | Description | Paramètres | Réponse |
|----------|-------------|------------|---------|
| `GET /api/status` | Statut du serveur | Aucun | `{"status":"active","server":"ChatAI HTTP Server","version":"1.0"}` |
| `GET /api/plugins` | Liste des plugins | Aucun | `{"plugins":["translator","calculator","weather","camera","files","jokes","tips"]}` |
| `GET /api/weather/{city}` | Météo par ville | `city` (string) | `{"city":"Paris","temperature":20,"condition":"Ensoleillé ☀️","humidity":60,"wind":"15 km/h"}` |
| `GET /api/jokes/random` | Blague aléatoire | Aucun | `{"joke":"Pourquoi les plongeurs plongent-ils toujours en arrière ? Parce que sinon, ils tombent dans le bateau !"}` |
| `GET /api/tips/{category}` | Conseils par catégorie | `category` (string) | `{"category":"productivity","tip":"🍅 Technique Pomodoro : 25 min travail, 5 min pause"}` |
| `GET /api/health` | Santé du système | Aucun | `{"health":"ok","database":"connected","cache":"active"}` |

### **POST Endpoints**

| Endpoint | Description | Body JSON | Réponse |
|----------|-------------|-----------|---------|
| `POST /api/translate` | Traduction de texte | `{"text":"Hello","target":"fr"}` | `{"original":"Hello","translated":"Bonjour","target":"fr"}` |
| `POST /api/chat` | Chat avec l'IA | `{"message":"Salut","personality":"casual"}` | `{"response":"Salut ! Comment ça va ?","personality":"casual"}` |
| `POST /api/ai/query` | Requête IA avancée | `{"query":"Explique-moi l'IA"}` | `{"response":"L'IA est...","cached":false}` |

---

## 🔌 **API WebSocket (Port 8081)**

### **Types de Messages**

| Type | Description | Format d'Envoi | Format de Réponse |
|------|-------------|----------------|-------------------|
| `chat_message` | Message de chat | `{"type":"chat_message","content":"Salut"}` | `{"type":"chat_response","content":"Réponse IA","timestamp":1234567890}` |
| `ping` | Test de connexion | `{"type":"ping"}` | `{"type":"pong","content":"Serveur actif","timestamp":1234567890}` |
| `typing` | Indicateur de frappe | `{"type":"typing"}` | `{"type":"typing_received","content":"Indicateur de frappe reçu","timestamp":1234567890}` |
| `broadcast` | Diffusion générale | `{"type":"broadcast","content":"Message"}` | `{"type":"broadcast","content":"Message","timestamp":1234567890}` |

### **Messages Automatiques**

| Type | Description | Déclencheur |
|------|-------------|-------------|
| `welcome` | Message de bienvenue | Connexion client |
| `ai_response` | Réponse IA temps réel | Requête IA traitée |

---

## 📱 **API Android Natives (JavaScript Interface)**

### **Notifications**

| Méthode | Description | Paramètres | Retour |
|---------|-------------|------------|--------|
| `showNotification(message)` | Affiche une notification | `message` (string) | void |

### **Sauvegarde**

| Méthode | Description | Paramètres | Retour |
|---------|-------------|------------|--------|
| `saveConversation(conversationJson)` | Sauvegarde conversation | `conversationJson` (string) | void |
| `saveConversationSecure(conversationJson)` | Sauvegarde sécurisée | `conversationJson` (string) | void |
| `getLastConversation()` | Récupère dernière conversation | Aucun | `string` (JSON) |

### **Navigation**

| Méthode | Description | Paramètres | Retour |
|---------|-------------|------------|--------|
| `openKittInterface()` | Ouvre interface KITT | Aucun | void |
| `openSettingsActivity()` | Ouvre paramètres | Aucun | void |
| `openDatabaseActivity()` | Ouvre base de données | Aucun | void |
| `openServerActivity()` | Ouvre monitoring serveurs | Aucun | void |

### **Accès Système**

| Méthode | Description | Paramètres | Retour |
|---------|-------------|------------|--------|
| `openCamera()` | Ouvre caméra | Aucun | void (non implémenté) |
| `openFileManager()` | Ouvre gestionnaire fichiers | Aucun | void (non implémenté) |
| `openDocumentPicker()` | Ouvre sélecteur documents | Aucun | void (non implémenté) |
| `showRecentFiles()` | Affiche fichiers récents | Aucun | void (non implémenté) |

### **Utilitaires**

| Méthode | Description | Paramètres | Retour |
|---------|-------------|------------|--------|
| `showToast(message)` | Affiche toast | `message` (string) | void |
| `getDeviceInfo()` | Info appareil | Aucun | `string` |
| `getHttpServerUrl()` | URL serveur HTTP | Aucun | `string` |
| `getWebSocketClientsCount()` | Nombre clients WS | Aucun | `int` |
| `getAvailablePlugins()` | Liste plugins | Aucun | `string` (JSON) |

### **Sécurité**

| Méthode | Description | Paramètres | Retour |
|---------|-------------|------------|--------|
| `getSecureApiToken()` | Token API sécurisé | Aucun | `string` |
| `setSecureApiToken(token)` | Configure token API | `token` (string) | void |
| `generateTempToken()` | Génère token temporaire | Aucun | `string` |
| `validateUserInput(input)` | Valide entrée utilisateur | `input` (string) | `boolean` |
| `sanitizeUserInput(input)` | Nettoie entrée utilisateur | `input` (string) | `string` |

### **Services HTTP et IA**

| Méthode | Description | Paramètres | Retour |
|---------|-------------|------------|--------|
| `makeHttpRequest(endpoint, method, data)` | Requête HTTP | `endpoint`, `method`, `data` (strings) | void |
| `getAIServiceStats()` | Statistiques service IA | Aucun | `string` (JSON) |
| `processAIRequestRealtime(message, personality)` | Traitement IA temps réel | `message`, `personality` (strings) | void |

---

## 🧪 **Exemples d'Utilisation**

### **Test HTTP avec curl**

```bash
# Test statut serveur
curl http://localhost:8080/api/status

# Test météo
curl "http://localhost:8080/api/weather/Paris"

# Test blague
curl http://localhost:8080/api/jokes/random

# Test traduction (POST)
curl -X POST http://localhost:8080/api/translate \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello","target":"fr"}'
```

### **Test WebSocket avec JavaScript**

```javascript
// Connexion WebSocket
const ws = new WebSocket('ws://localhost:8081');

// Envoyer message chat
ws.send(JSON.stringify({
    type: 'chat_message',
    content: 'Salut !'
}));

// Envoyer ping
ws.send(JSON.stringify({
    type: 'ping'
}));

// Écouter les réponses
ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log('Réponse:', data);
};
```

### **Test API Android depuis JavaScript**

```javascript
// Notifications
AndroidApp.showNotification("Message de test");

// Sauvegarde
AndroidApp.saveConversation(JSON.stringify(messages));

// Interface KITT
AndroidApp.openKittInterface();

// Statut serveurs
const httpUrl = AndroidApp.getHttpServerUrl();
const wsCount = AndroidApp.getWebSocketClientsCount();
```

---

## 📊 **Statistiques des Endpoints**

- **API HTTP** : 9 endpoints (6 GET + 3 POST)
- **API WebSocket** : 5 types de messages
- **API Android** : 25 méthodes natives
- **Total** : 39 endpoints fonctionnels

---

## ⚠️ **Endpoints Non Implémentés**

- `openCamera()` - Caméra non implémentée
- `openFileManager()` - Gestionnaire fichiers non implémenté
- `openDocumentPicker()` - Sélecteur documents non implémenté
- `showRecentFiles()` - Fichiers récents non implémentés

---

## 🔧 **Configuration Requise**

- **Port HTTP** : 8080 (avec fallback automatique)
- **Port WebSocket** : 8081 (avec fallback automatique)
- **Token API** : Hugging Face ou OpenAI (optionnel)
- **Permissions** : Internet, Notifications, Caméra, Stockage
