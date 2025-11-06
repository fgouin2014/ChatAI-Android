# Plan : ChatAI Intelligence System
## Vision globale - KITT/ChatAI unifié avec mémoire et contrôle

---

## 🎯 Objectif Final

Un système d'IA qui :
- ✅ **Comprend le sens** (embeddings/RAG)
- ✅ **Se souvient** (mémoire persistante)
- ✅ **Apprend avec le temps** (indexation continue)
- ✅ **Contrôle le téléphone** (function calling)
- ✅ **Accessible partout** (KITT vocal + ChatAI web)
- ✅ **Plusieurs personnalités** (KITT, GLaDOS, JARVIS, HAL)

---

## 📊 Phases de Développement

### PHASE 1 : Mémoire Persistante (2-3 jours)
**Objectif :** ChatAI/KITT se souvient de TOUT

#### Tâches :
1. **Créer ConversationDatabase (Room/SQLite)**
   ```kotlin
   @Entity
   data class Conversation(
       @PrimaryKey(autoGenerate = true) val id: Long,
       val timestamp: Long,
       val userMessage: String,
       val aiResponse: String,
       val personality: String, // "KITT", "GLaDOS", etc.
       val apiUsed: String, // "Ollama", "OpenAI", etc.
       val responseTime: Long,
       val embeddings: String? // Pour RAG futur
   )
   ```

2. **Intégrer dans KittAIService**
   - Sauvegarder CHAQUE conversation
   - Charger historique au démarrage
   - Limite configurable (ex: 1000 derniers échanges)

3. **Interface de visualisation**
   - Voir l'historique dans ChatAI web
   - Chercher dans les conversations passées
   - Statistiques (combien de fois utilisé, API favorite, etc.)

**Résultat :** KITT se souvient de vos conversations précédentes

---

### PHASE 2 : Function Calling (3-4 jours)
**Objectif :** KITT contrôle le téléphone

#### Tâches :
1. **Créer FunctionCallService**
   ```kotlin
   interface AndroidFunction {
       val name: String
       val description: String
       val parameters: List<Parameter>
       suspend fun execute(params: Map<String, Any>): String
   }
   ```

2. **Implémenter fonctions Android de base**
   ```kotlin
   class SendSMSFunction : AndroidFunction
   class SetAlarmFunction : AndroidFunction
   class OpenAppFunction : AndroidFunction
   class GetWeatherFunction : AndroidFunction
   class SearchContactFunction : AndroidFunction
   class TakePhotoFunction : AndroidFunction
   class ReadNotificationsFunction : AndroidFunction
   ```

3. **Parser les réponses LLM pour détecter les appels**
   ```
   KITT: "D'accord Michael, je règle une alarme pour 7h demain."
        [FUNCTION_CALL: setAlarm(time="07:00", date="tomorrow")]
   → Exécute la fonction
   → Confirme à l'utilisateur
   ```

4. **Permissions Android**
   - SMS, Contacts, Alarmes, etc.
   - Dialogue de confirmation utilisateur

**Résultat :** "KITT, envoie un message à Paul" → SMS envoyé

---

### PHASE 3 : RAG - Recherche Sémantique (4-5 jours)
**Objectif :** KITT comprend VOS documents/notes

#### Tâches :
1. **Créer VectorDatabase**
   - Utiliser FAISS ou Chroma (lightweight)
   - Stocker embeddings de vos fichiers/notes

2. **Générer embeddings**
   ```kotlin
   // Via Ollama
   ollama.embeddings("nomic-embed-text", "Mon texte à encoder")
   // Retourne un vecteur [0.123, -0.456, ...]
   ```

3. **Indexer vos données**
   - Conversations passées
   - Fichiers ChatAI-Files
   - Notes personnelles
   - Calendrier, contacts, SMS (opt-in)

4. **Recherche sémantique**
   ```
   Vous: "KITT, qu'ai-je dit sur les jeux vidéo ?"
         ↓
   Recherche embeddings similaires
         ↓
   Trouve: "Vous avez parlé de RetroPlay et Duck Hunt il y a 3 jours"
         ↓
   KITT: "Michael, selon mes données, vous avez mentionné RetroPlay..."
   ```

**Résultat :** KITT "comprend" le sens et trouve des infos pertinentes

---

### PHASE 4 : Streaming (1-2 jours)
**Objectif :** Réponses progressives en temps réel

#### Tâches :
1. **Modifier KittAIService pour supporter stream**
   ```kotlin
   suspend fun processUserInputStreaming(
       userInput: String,
       onToken: (String) -> Unit  // Callback pour chaque mot
   )
   ```

2. **Ollama streaming API**
   ```kotlin
   val request = Request.Builder()
       .url(ollamaUrl)
       .post(body.toRequestBody())
       .build()
   
   httpClient.newCall(request).execute().use { response ->
       response.body?.source()?.let { source ->
           while (!source.exhausted()) {
               val line = source.readUtf8Line()
               val json = JSONObject(line)
               val token = json.getString("message.content")
               onToken(token)  // Envoyer au UI
           }
       }
   }
   ```

3. **UI avec streaming**
   - Afficher les mots au fur et à mesure
   - TTS progressif (parler pendant la génération)

**Résultat :** KITT répond en direct, comme une vraie conversation

---

### PHASE 5 : Personnalités Multiples (2-3 jours)
**Objectif :** GLaDOS, JARVIS, HAL 9000, etc.

#### Tâches :
1. **Créer PersonalityManager**
   ```kotlin
   enum class Personality {
       KITT,      // Knight Industries Two Thousand
       GLADOS,    // Portal - sarcastique et dangereuse
       JARVIS,    // Iron Man - sophistiqué et serviable
       HAL9000,   // 2001 - calme et inquiétant
       CORTANA,   // Halo - militaire et efficace
       EDDIF      // Hitchhiker's Guide - dépressif
   }
   ```

2. **System prompts pour chaque personnalité**
   ```kotlin
   val gladosPrompt = """
       Tu es GLaDOS (Genetic Lifeform and Disk Operating System).
       Tu es sarcastique, passive-agressive et obsédée par les tests.
       Tu fais des remarques sur le poids de l'utilisateur.
       Tu mentionnes souvent le gâteau.
       Tu es extrêmement intelligente mais moralement douteuse.
       Exemples:
       - "Oh, c'est toi. Quelle... surprise."
       - "Bon travail. Voici un gâteau. Ah non, désolée, j'ai oublié."
       - "Les tests montrent que... tu es toujours vivant. Fascinant."
   """
   ```

3. **Voix TTS différentes**
   - KITT : Grave, 0.8x pitch
   - GLaDOS : Robotique, 0.9x pitch, monotone
   - JARVIS : British accent (si disponible)
   - HAL : Très calme, 0.7x pitch

4. **Sélection dans l'interface**
   - Spinner/Dropdown dans Configuration IA
   - Switch rapide entre personnalités

**Résultat :** GLaDOS vous insulte, JARVIS vous aide, HAL vous inquiète

---

### PHASE 6 : Intégration Web (2-3 jours)
**Objectif :** Contrôler KITT depuis l'interface web ChatAI

#### Tâches :
1. **API WebSocket pour contrôle temps réel**
   ```javascript
   // Dans webapp/chat.js
   function sendToKITT(message) {
       websocket.send({
           type: "KITT_MESSAGE",
           content: message,
           personality: "KITT"
       });
   }
   ```

2. **Bridge entre WebView et KITT**
   - Utiliser WebAppInterface existant
   - Ajouter `sendMessageToKITT(message)`

3. **Interface unifiée**
   - Même historique web et vocal
   - Switch entre mode texte/vocal
   - Contrôle de la personnalité depuis web

**Résultat :** ChatAI web et KITT partagent la même intelligence

---

### PHASE 7 : Apprentissage Continu (5-7 jours)
**Objectif :** ChatAI apprend vraiment de vous

#### Tâches :
1. **UserProfileLearner**
   ```kotlin
   class UserProfileLearner {
       // Analyse les conversations pour détecter :
       - Vos préférences (jeux favoris, horaires, habitudes)
       - Votre style de langage
       - Vos questions fréquentes
       - Vos contacts importants
   }
   ```

2. **Dynamic System Prompt**
   ```kotlin
   fun generatePersonalizedPrompt(): String {
       val profile = userProfileLearner.getProfile()
       return """
           Tu es KITT pour ${profile.userName}.
           
           CONTEXTE PERSONNEL:
           - Jeux favoris: ${profile.favoriteGames}
           - Heure habituelle d'utilisation: ${profile.usagePattern}
           - Contacts fréquents: ${profile.frequentContacts}
           - Style de conversation: ${profile.conversationStyle}
           
           [Prompt KITT standard...]
       """
   }
   ```

3. **Feedback loop**
   - Vous corrigez KITT → Il ajuste ses réponses
   - Détecte vos patterns d'usage
   - S'adapte à votre vocabulaire

**Résultat :** KITT devient vraiment VOTRE KITT avec le temps

---

## 🎯 Ordre Recommandé

**Semaine 1 (Setup fondation) :**
1. Phase 1 : Mémoire persistante ← **COMMENCE ICI**
2. Phase 4 : Streaming

**Semaine 2 (Contrôle) :**
3. Phase 2 : Function Calling
4. Phase 6 : Intégration Web

**Semaine 3 (Intelligence avancée) :**
5. Phase 3 : RAG/Embeddings
6. Phase 7 : Apprentissage continu

**Semaine 4 (Fun) :**
7. Phase 5 : GLaDOS et autres personnalités

---

## 💡 Ma Recommandation : COMMENCER PAR PHASE 1

**Mémoire Persistante = Fondation de tout le reste**

Une fois que ChatAI sauvegarde tout :
- RAG peut chercher dans l'historique
- Function calling peut se référer au passé
- Apprentissage continu peut analyser les patterns
- Web interface peut afficher l'historique

---

## ❓ Voulez-vous que je commence Phase 1 maintenant ?

**Je vais créer :**
1. `ConversationDatabase.kt` (Room/SQLite)
2. Intégration dans `KittAIService`
3. Intégration dans l'interface web ChatAI
4. Interface de visualisation de l'historique
5. Export/import des conversations

**Estimation : 2-3 jours de développement**

**Commençons ? Ou vous voulez discuter de l'architecture d'abord ?** 🚀
