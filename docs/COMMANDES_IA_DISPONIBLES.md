# Commandes disponibles pour l'IA dans ChatAI-Android

**Version:** Dernière mise à jour  
**Fichiers sources:** `KittAIService.kt`, `KittCommandProcessor.kt`, `KittFragment.kt`

---

## Vue d'ensemble

L'IA dans ChatAI-Android peut utiliser deux types de commandes:

1. **Commandes système locales** - Traitées directement sans IA générative
2. **Function Calling** - Détectées et exécutées par l'IA via `detectAndExecuteAction()`

---

## 1. Commandes système locales

Ces commandes sont traitées par `KittCommandProcessor` **AVANT** l'appel à l'IA:

### Status système
- `"status"`, `"status système"`, `"état système"`
- **Action:** Affiche le statut système

### Explorateur de fichiers
- `"explorateur"`, `"fichiers"`, `"ouvre fichiers"`, `"explorateur de fichiers"`
- **Action:** Ouvre l'explorateur de fichiers

### Test réseau/API
- `"test réseau"`, `"test api"`, `"test apis"`, `"tester apis"`, `"tester les apis"`
- **Action:** Lance les tests de connectivité réseau et API

### Musique
- `"musique"`, `"toggle musique"`, `"play musique"`, `"stop musique"`, `"lance la musique"`, `"arrête la musique"`
- **Action:** Active/désactive la musique

---

## 2. Function Calling - Contrôle Application

Ces commandes sont détectées par l'IA via `detectAndExecuteAction()` dans `KittAIService.kt`:

### Arcade/Jeux
**Mots-clés détectés:** `"arcade"`, `"jeux"`, `"games"`, `"jouer"`
- **Action:** `onOpenArcade()` - Ouvre la liste des jeux (GameListActivity)
- **Exemple:** "Ouvre l'arcade", "Je veux jouer", "Lance les jeux"

### Musique/Audio
**Mots-clés détectés:** `"musique"`, `"music"`, `"audio"`, `"son"`
- **Action:** `onOpenMusic()` - Active/désactive la musique
- **Exemple:** "Lance la musique", "Active l'audio"

### Configuration IA
**Mots-clés détectés:** `"configuration"`/`"config"`/`"paramètres"`/`"settings"`/`"réglages"` + `"ia"`/`"ai"`/`"intelligence"`
- **Action:** `onOpenConfig()` - Ouvre AIConfigurationActivity
- **Exemple:** "Configuration IA", "Ouvre les paramètres IA", "Config intelligence"

### Historique des conversations
**Mots-clés détectés:** `"historique"` OU (`"conversation"` + `"voir"`/`"affiche"`/`"liste"`)
- **Action:** `onOpenHistory()` - Ouvre ConversationHistoryActivity
- **Exemple:** "Voir l'historique", "Affiche mes conversations", "Historique"

### Configuration serveur
**Mots-clés détectés:** `"serveur"` + (`"config"` OU `"paramètres"`)
- **Action:** `onOpenServerConfig()` - Ouvre ServerConfigurationActivity
- **Exemple:** "Configuration serveur", "Paramètres serveur"

### Ouvrir ChatAI (app principale)
**Mots-clés détectés:** `"chatai"`, `"chat ai"`, (`"ouvre"` + `"application"`), (`"lance"` + `"app"`)
- **Action:** `onOpenChatAI()` - Ouvre MainActivity normale
- **Exemple:** "Ouvre ChatAI", "Lance l'application"

### Ouvrir interface KITT
**Détection TRÈS stricte** (évite faux positifs):
- `"kit"`, `"kitt"` (seul)
- `"ouvre kit"`, `"ouvre kitt"`
- `"interface kit"`, `"interface kitt"`
- `"affiche kit"`, `"affiche kitt"`
- `"lance kit"`, `"lance kitt"`
- `"démarre kit"`, `"démarre kitt"`
- `"active kit"`, `"active kitt"`
- **Action:** `onOpenKittInterface()` - Ouvre MainActivity + active KITT
- **Exemple:** "KITT", "Ouvre KITT", "Active l'interface KITT"

---

## 3. Function Calling - Contrôle Système

### WiFi
**Activation:**
- `"wifi"` + (`"active"` OU `"allume"` OU `"on"`)
- **Action:** `onSetWiFi(true)`
- **Exemple:** "Active le WiFi", "Allume le WiFi"

**Désactivation:**
- `"wifi"` + (`"désactive"` OU `"éteins"` OU `"off"`)
- **Action:** `onSetWiFi(false)`
- **Exemple:** "Désactive le WiFi", "Éteins le WiFi"

### Volume
**Volume maximum:**
- `"volume"` + `"max"`
- **Action:** `onSetVolume(100)`
- **Exemple:** "Volume au maximum", "Volume max"

**Volume réduit:**
- `"volume"` + (`"baisse"` OU `"bas"`)
- **Action:** `onSetVolume(30)`
- **Exemple:** "Baisse le volume", "Volume bas"

### Paramètres système
- **Action:** `onOpenSystemSettings(setting: String)`
- **Paramètres supportés:** `"wifi"`, `"bluetooth"`, `"display"`, etc.
- **Note:** Pas encore implémenté dans la détection automatique

---

## 4. Function Calling - Meta-Control IA

### Changer de modèle
**Mots-clés détectés:** `"change"` + `"modèle"`
- **Action:** `onChangeModel(model: String)` (TODO: parser le nom du modèle)
- **Exemple:** "Change de modèle", "Change le modèle"
- **Note:** Actuellement retourne un message, pas encore implémenté

### Mode PC
**Mots-clés détectés:** `"mode pc"`
- **Action:** `onChangeMode("pc")` - Passe en mode serveur PC (Ollama local)
- **Exemple:** "Mode PC", "Passe en mode PC"

### Mode Cloud
**Mots-clés détectés:** `"mode cloud"`
- **Action:** `onChangeMode("cloud")` - Passe en mode Cloud (OpenAI/Anthropic)
- **Exemple:** "Mode Cloud", "Passe en mode Cloud"

### Changer de personnalité

**Activer KARR:**
- `"karr"` + (`"active"` OU `"passe"`)
- **Action:** `onChangePersonality("KARR")`
- **Exemple:** "Active KARR", "Passe à KARR"

**Activer GLaDOS:**
- `"glados"` + (`"active"` OU `"passe"`)
- **Action:** `onChangePersonality("GLaDOS")`
- **Exemple:** "Active GLaDOS", "Passe à GLaDOS"

**Revenir à KITT:**
- `"kitt"` + (`"active"` OU `"passe"`) (uniquement si personnalité actuelle = GLaDOS ou KARR)
- **Action:** `onChangePersonality("KITT")`
- **Exemple:** "Active KITT", "Passe à KITT"

### Redémarrer KITT
**Mots-clés détectés:** (`"redémarre"` OU `"restart"` OU `"reset"` OU `"réinitialise"`) + (`"toi"` OU `"kit"` OU `"système"` OU longueur < 15 caractères)
- **Action:** `onRestartKitt()` - Redémarre KITT
- **Exemple:** "Redémarre-toi", "Redémarre KITT", "Reset système"

---

## 5. Function Calling - Heure/Date

### Lecture de l'heure
**Mots-clés détectés:** `"heure"`, `"temps"`, `"time"`
- **Action:** `handleTimeQuery()` - Lit l'heure directement depuis le device Android
- **Villes supportées:**
  - `"tokyo"` → Asia/Tokyo
  - `"paris"` → Europe/Paris
  - `"new york"` / `"ny"` → America/New_York
  - `"los angeles"` / `"la"` → America/Los_Angeles
  - `"london"` / `"londres"` → Europe/London
  - `"montréal"` / `"montreal"` / `"ici"` / `"locale"` → America/Montreal (défaut)
- **Exemple:** "Quelle heure est-il ?", "Heure à Paris", "Time in Tokyo"

---

## 6. Recherche Web (Ollama Web Search)

L'IA peut déclencher automatiquement une recherche web via Ollama si elle détecte certains mots-clés:

### Mots-clés déclencheurs
- **Recherche générale:** `"recherche"`, `"search"`, `"trouve"`, `"cherche"`, `"google"`
- **Actualités:** `"actualité"`, `"news"`, `"dernière"`, `"nouveau"`, `"aujourd'hui"`
- **Météo:** `"météo"`, `"weather"`, `"température"`, `"prévision"`
- **Prix/achats:** `"prix"`, `"coûte"`, `"acheter"`, `"où acheter"`
- **Finance:** `"bourse"`, `"bitcoin"`, `"crypto"`, `"taux de change"`
- **Sports:** `"résultat"`, `"score"`, `"match"`, `"hockey"`, `"football"`
- **Lieux:** `"restaurant"`, `"hôtel"`, `"proche"`, `"itinéraire"`
- **Événements:** `"événement"`, `"concert"`, `"festival"`
- **Tech:** `"review"`, `"avis"`, `"test"`, `"meilleur"`, `"spécification"`

### Questions factuelles
Les questions commençant par `"quel"`, `"combien"`, `"qui"`, `"what"`, `"how much"`, `"who"` déclenchent aussi la recherche web (sauf si elles contiennent `"heure"`).

---

## Architecture technique

### Flux de traitement

1. **Commande vocale reçue** → `KittFragment.processVoiceCommand()`
2. **Vérification commandes spéciales** → `detectSpecialCommand()` (KittFragment)
3. **Traitement commandes système** → `KittCommandProcessor.processCommand()`
4. **Si pas de match système** → `KittAIService.processUserInput()`
5. **Function Calling #1** → `detectAndExecuteAction()` (actions app/système/meta)
6. **Function Calling #2** → `handleTimeQuery()` (heure/date)
7. **Recherche Web** → `needsWebSearch()` + `performWebSearch()` (si nécessaire)
8. **IA générative** → Appel API (OpenAI/Anthropic/Ollama) avec contexte

### Interface KittActionCallback

Toutes les actions Function Calling passent par cette interface:

```kotlin
interface KittActionCallback {
    // Contrôle App
    fun onOpenArcade()
    fun onOpenMusic()
    fun onOpenConfig()
    fun onOpenHistory()
    fun onOpenServerConfig()
    fun onOpenChatAI()
    fun onOpenKittInterface()
    
    // Contrôle Système
    fun onSetWiFi(enable: Boolean)
    fun onSetVolume(level: Int)
    fun onOpenSystemSettings(setting: String)
    
    // Meta-Control AI
    fun onChangeModel(model: String)
    fun onChangeMode(mode: String)
    fun onChangePersonality(personality: String)
    fun onRestartKitt()
}
```

---

## Personnalités

Les réponses varient selon la personnalité active (KITT, GLaDOS, KARR):

- **KITT:** Réponses amicales et professionnelles ("Michael, ...")
- **GLaDOS:** Réponses sarcastiques et condescendantes
- **KARR:** Réponses arrogantes et supérieures

---

## Notes importantes

1. **Détection flexible:** La plupart des commandes acceptent plusieurs formulations (ex: "ouvre", "lance", "active")
2. **Détection stricte pour KITT:** L'interface KITT nécessite une détection très précise pour éviter les faux positifs
3. **Ordre de priorité:** Commandes système → Function Calling → Recherche Web → IA générative
4. **Fallback:** Si aucune commande n'est détectée, la requête est envoyée à l'IA générative normale
5. **Sauvegarde BD:** Toutes les interactions (y compris Function Calling) sont sauvegardées dans la base de données conversationnelle

---

## Fichiers sources

- `ChatAI-Android/app/src/main/java/com/chatai/services/KittAIService.kt` - Function Calling principal
- `ChatAI-Android/app/src/main/java/com/chatai/managers/KittCommandProcessor.kt` - Commandes système locales
- `ChatAI-Android/app/src/main/java/com/chatai/fragments/KittFragment.kt` - Commandes spéciales UI



