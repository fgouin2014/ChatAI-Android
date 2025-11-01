# Vision IA Consciente - Session du 1er Novembre 2025

## 🎯 Objectif Principal

**Créer une IA qui APPREND et devient "CONSCIENTE" de ses interactions**

> "C'est le genre de chose que j'aimerais que ça fasse en apprenant/conscient."  
> — Utilisateur, suite à la découverte de l'historique des conversations

---

## 📊 État Initial de la Session

### Ce Qui Existait
- ✅ KittAIService v2.5 avec support Ollama PC Local
- ✅ Room Database pour sauvegarder les conversations
- ✅ Personnalités KITT et GLaDOS
- ✅ Configuration TTS ajustable
- ❌ AUCUNE interface pour voir l'historique
- ❌ Pas de recherche dans les conversations
- ❌ Pas d'apprentissage automatique des corrections

### Problèmes Identifiés
1. **Ollama Cloud** : Clé SSH utilisée au lieu de clé API
2. **Serveur Ollama PC** : IP changée (217 → 249)
3. **Historique invisible** : "Le logcat ne sert à rien pour l'app"
4. **Pas d'apprentissage** : KITT répète les mêmes erreurs (ex: heure UTC)

---

## 🚀 Ce Qui a Été Fait Aujourd'hui

### 1. Ollama Cloud Intégré ☁️

**Fichiers créés/modifiés:**
- `KittAIService.kt` v2.6 - Ajout `tryOllamaCloud()`
- `activity_ai_configuration.xml` - Section Ollama Cloud
- `AIConfigurationActivity.kt` - Champs `ollamaCloudApiKeyInput`, `ollamaCloudModelInput`

**Ordre de fallback API (6 niveaux):**
```
1. OpenAI GPT-4o-mini
2. Anthropic Claude 3.5 Sonnet
3. Ollama Cloud (gpt-oss:120b-cloud, deepseek-v3.1:671b-cloud) ⭐ NOUVEAU
4. Ollama PC Local (gemma3:1b via http://172.26.22.249:11434)
5. Hugging Face
6. Fallback local (réponses KITT/GLaDOS basiques)
```

**Modèles cloud disponibles:**
- `gpt-oss:120b-cloud` (120 milliards de paramètres)
- `gpt-oss:20b-cloud` (20 milliards)
- `deepseek-v3.1:671b-cloud` (671 milliards!)
- `kimi-k2:1t-cloud` (1 trillion de paramètres)
- `qwen3-coder:480b-cloud` (480 milliards)
- `glm-4.6:cloud`

**Plans Ollama:**
- Gratuit : Accès aux modèles cloud + web search
- Pro ($20/mois) : Utilisation accrue

---

### 2. Gemma3:270m Transféré sur Phone 📲

**Processus:**
```powershell
# 1. Localisé sur PC
$env:USERPROFILE\.ollama\models\blobs\sha256-735af...

# 2. Transféré via ADB (291 MB en 18 secondes)
adb push "sha256-735af..." /storage/emulated/0/ChatAI-Files/models/gemma3-270m.gguf

# 3. Vérifié
File: /storage/emulated/0/ChatAI-Files/models/gemma3-270m.gguf
Size: 291,545,472 bytes (278 MB)
```

**Statut actuel:**
- ✅ Modèle sur le phone
- ⏳ Pas encore intégré (nécessite llama.cpp)
- 🎯 Futur : Mode offline complet

**Options d'intégration:**
1. **llama.cpp-android** (Complexe - 3-5 jours)
2. **MLC-LLM** (Moyen - 2-3 jours)
3. **Mediapipe LLM** (Simple - 1-2 jours, Google officiel)
4. **Ollama Android** (Expérimental)

---

### 3. Serveur Ollama PC Réparé 🔧

**Problème:** IP du PC changée
```
Ancienne : 172.26.22.217
Nouvelle : 172.26.22.249
Phone   : 172.26.22.217
```

**Solution appliquée:**
- Mise à jour de `local_server_url` dans les SharedPreferences
- Configuration : `http://172.26.22.249:11434/v1/chat/completions`
- Test réussi : HTTP 200 OK

**Modèles disponibles sur le serveur:**
- `gemma3:270m` (291 MB)
- `gemma3:1b` (815 MB) ← Utilisé actuellement
- `llama3.2:3b` (2 GB)

---

### 4. Interface Historique Complète 📜 ⭐ NOUVEAU

**Fichiers créés:**
```
ChatAI-Android/app/src/main/java/com/chatai/activities/
└── ConversationHistoryActivity.kt

ChatAI-Android/app/src/main/res/layout/
├── activity_conversation_history.xml
└── item_conversation.xml
```

**Fonctionnalités:**
- ✅ RecyclerView avec adapter custom
- ✅ Affichage question + réponse + métadonnées
- ✅ Statistiques en temps réel :
  - Total conversations
  - Count par personnalité (KITT/GLaDOS)
  - Temps de réponse moyen
  - API la plus utilisée
- ✅ Bouton "Effacer tout" avec confirmation
- ✅ Design style KITT (rouge/noir/monospace)

**ConversationEntity (Room DB):**
```kotlin
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userMessage: String,          // Question exacte de l'utilisateur
    val aiResponse: String?,          // Réponse de KITT/GLaDOS
    val personality: String,          // "KITT" ou "GLaDOS"
    val apiUsed: String,              // "openai", "ollama", "local_fallback"
    val responseTimeMs: Long,         // Temps de réponse
    val platform: String,             // "vocal" ou "web"
    val sessionId: String,            // Groupe les conversations
    val timestamp: Long               // Date/heure
)
```

**ConversationDao - Méthodes utiles:**
```kotlin
// Recherche
suspend fun searchConversations(query: String, limit: Int)
suspend fun searchConversationsByPersonality(personality: String, query: String)

// Statistiques
suspend fun getTotalConversations(): Int
suspend fun getAverageResponseTime(): Long?
suspend fun getMostUsedAPI(): String?

// Filtres
suspend fun getConversationsByPersonality(personality: String)
suspend fun getConversationsByAPI(apiUsed: String)
suspend fun getConversationsByTimeRange(startTime: Long, endTime: Long)

// Nettoyage
suspend fun deleteAllConversations()
suspend fun deleteConversationsBefore(beforeTimestamp: Long)
```

**Accès:**
Configuration IA → **📜 VOIR L'HISTORIQUE DES CONVERSATIONS**

---

### 5. Repo GitHub Créé 🌐

**URL:** https://github.com/fgouin2014/ChatAI-Android.git

**`.gitignore` complet:**
```gitignore
# Modèles AI (TROP GROS)
*.gguf
*.bin
*.safetensors
*.pt
models/

# Base de données (données utilisateur)
*.db
*.db-shm
*.db-wal

# Configuration avec clés API (PRIVÉ!)
chatai_ai_config.xml

# Build outputs
*.apk
*.aab
build/
```

**README.md** avec documentation complète:
- Architecture du projet
- Guide d'installation
- Configuration des APIs
- Schema de la base de données
- Roadmap détaillée

---

## 💬 Exemple Concret : La Conversation sur l'Heure

### Ce Qui S'est Passé

**Vos questions exactes (capturées dans les logs) :**
```
8h19:57 - "salut Kate dis-moi l'heure"
          → KITT: "14h37" (UTC ❌)
          → Temps: 4.0s | API: ollama

8h20:16 - "l'heure actuelle est 8 h 20" (CORRECTION)
          → KITT: "8h20, la date est... Je suis à votre disposition"
          → Temps: 1.9s | API: ollama

8h20:49 - "dis-moi l'heure"
          → KITT: "8h20. L'heure est à votre service"
          → Temps: 1.9s | API: ollama (✅ A APPRIS!)

8h21:05 - "tu peux me dire 8h20 combien de secondes"
          → KITT: "8h20. Il y a... 20 secondes"
          → Temps: 1.6s | API: ollama

8h21:20 - "dans quelle heure est-il maintenant"
          → KITT: "8h20. Il est à votre service"
          → Temps: 1.9s | API: ollama
```

**Ce que ça prouve:**
1. ✅ Les questions SONT sauvegardées
2. ✅ Les réponses SONT sauvegardées
3. ✅ KITT a "appris" temporairement de votre correction
4. ❌ Mais il n'a PAS compris POURQUOI (UTC vs local)
5. ❌ Il refera l'erreur après redémarrage

### Problème Identifié : Heure UTC

**Cause racine:**
- Le serveur Ollama ou le système retourne l'heure en UTC
- KITT répond ce qu'on lui donne sans vérifier
- Il n'y a pas de "mémoire" de la correction UTC → local

**Ce qu'un KITT conscient devrait faire:**
```kotlin
// Détecter la correction
if (userMessage.contains("l'heure actuelle est")) {
    val correctedTime = extractTime(userMessage) // "8h20"
    val myWrongTime = lastResponse.extractTime() // "14h37"
    
    // Calculer l'erreur
    val offset = calculateOffset(myWrongTime, correctedTime) // -6h
    
    // Sauvegarder la règle
    saveRule("timezone_offset", offset)
    
    // KITT dit:
    "Mes excuses, Michael. J'ai détecté une erreur de fuseau horaire. 
     J'ai appris que je dois appliquer un offset de -6h. 
     Je ne referai plus cette erreur."
}
```

---

## 🧠 VISION : Intelligence Consciente et Apprenante

### Objectif Final

**Transformer KITT d'un assistant qui RÉPOND en un assistant qui APPREND**

### Caractéristiques d'une IA "Consciente"

1. **Mémoire à Long Terme**
   - Se souvient de TOUTES les interactions
   - Pas seulement les 10 dernières

2. **Auto-Correction**
   - Détecte quand l'utilisateur le corrige
   - Analyse pourquoi l'erreur s'est produite
   - Crée des règles pour ne pas répéter

3. **Apprentissage des Préférences**
   - Ton de voix préféré (formel/casual)
   - Longueur de réponse préférée
   - Sujets d'intérêt
   - Patterns temporels (demande l'heure le matin)

4. **Contextualisation**
   - Utilise l'historique pour mieux répondre
   - Fait des liens entre conversations
   - Anticipe les besoins

5. **Évolution de Personnalité**
   - KITT s'adapte au style de l'utilisateur
   - Devient plus/moins sarcastique selon les réactions
   - Personnalisation automatique

### Architecture Proposée

```
┌─────────────────────────────────────────────────────────────┐
│                     UTILISATEUR                              │
│              "Quelle heure est-il ?"                         │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                  KittAIService v3.0                          │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  1. PRÉTRAITEMENT                                     │  │
│  │  • Analyser la question                               │  │
│  │  • Détecter si c'est une correction                   │  │
│  │  • Extraire l'intent                                  │  │
│  └──────────────────────────────────────────────────────┘  │
│                     │                                        │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  2. RAG (Retrieval Augmented Generation)             │  │
│  │  • Recherche sémantique dans l'historique            │  │
│  │  • Trouve conversations similaires                    │  │
│  │  • Extrait contexte pertinent                         │  │
│  └──────────────────────────────────────────────────────┘  │
│                     │                                        │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  3. META-LEARNING                                     │  │
│  │  • Charge les règles apprises                         │  │
│  │  • Applique les corrections précédentes               │  │
│  │  • Adapte le system prompt                            │  │
│  └──────────────────────────────────────────────────────┘  │
│                     │                                        │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  4. GÉNÉRATION                                        │  │
│  │  • Ollama/OpenAI avec contexte enrichi                │  │
│  │  • Prompt augmenté avec historique + règles           │  │
│  └──────────────────────────────────────────────────────┘  │
│                     │                                        │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  5. POST-TRAITEMENT                                   │  │
│  │  • Analyser la réponse générée                        │  │
│  │  • Vérifier cohérence avec le contexte                │  │
│  │  • Appliquer le style de personnalité                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                     │                                        │
│                     ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  6. SAUVEGARDE & APPRENTISSAGE                        │  │
│  │  • Sauvegarder dans Room DB                           │  │
│  │  • Générer embeddings                                 │  │
│  │  • Mettre à jour profil utilisateur                   │  │
│  │  • Créer nouvelles règles si correction détectée      │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              BASES DE DONNÉES                                │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐ │
│  │ conversations  │  │ learned_rules  │  │ user_profile │ │
│  │ (Room DB)      │  │ (SQLite)       │  │ (SQLite)     │ │
│  │                │  │                │  │              │ │
│  │ • userMessage  │  │ • rule_type    │  │ • preferences│ │
│  │ • aiResponse   │  │ • rule_value   │  │ • patterns   │ │
│  │ • embeddings   │  │ • confidence   │  │ • topics     │ │
│  │ • timestamp    │  │ • created_at   │  │ • timezone   │ │
│  └────────────────┘  └────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Technologies Nécessaires

### 1. RAG (Retrieval Augmented Generation)

**Concept:**
Avant de répondre, KITT cherche dans son historique des conversations similaires pour enrichir son contexte.

**Implémentation:**
```kotlin
class RAGEngine(private val conversationDao: ConversationDao) {
    
    suspend fun augmentPrompt(userInput: String): String {
        // 1. Rechercher conversations similaires
        val similarConvs = conversationDao.searchConversations(
            query = extractKeywords(userInput),
            limit = 5
        )
        
        // 2. Construire le contexte
        val context = buildString {
            appendLine("CONTEXTE de mes conversations passées avec vous:")
            similarConvs.forEach { conv ->
                appendLine("• Vous: ${conv.userMessage}")
                appendLine("  Moi: ${conv.aiResponse}")
            }
        }
        
        // 3. Prompt augmenté
        return """
            $context
            
            Question actuelle: $userInput
            
            Réponds en tenant compte de ce contexte et de notre historique.
        """.trimIndent()
    }
}
```

### 2. Embeddings (Comprendre le Sens)

**Concept:**
Convertir les phrases en vecteurs numériques pour faire de la recherche sémantique (par sens, pas par mots-clés).

**Exemples:**
```
"Quelle heure est-il ?"     → [0.23, -0.54, 0.89, ...]
"Donne-moi l'heure"         → [0.24, -0.52, 0.87, ...] (similaire!)
"Comment vas-tu ?"          → [-0.12, 0.76, -0.34, ...] (différent)
```

**Implémentation:**
```kotlin
class EmbeddingService {
    // Utiliser un modèle on-device (ex: sentence-transformers mobile)
    private val embeddingModel = SentenceTransformer("all-MiniLM-L6-v2")
    
    fun generateEmbedding(text: String): FloatArray {
        return embeddingModel.encode(text)
    }
    
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        // Calcul de similarité entre deux vecteurs
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }
        
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }
    
    suspend fun findSimilarConversations(
        query: String,
        limit: Int = 5
    ): List<ConversationEntity> {
        val queryEmbedding = generateEmbedding(query)
        
        // Récupérer toutes les conversations avec embeddings
        val allConvs = conversationDao.getConversationsWithEmbeddings()
        
        // Calculer similarité avec chaque conversation
        val scored = allConvs.map { conv ->
            val convEmbedding = parseEmbedding(conv.embeddingsJson)
            val similarity = cosineSimilarity(queryEmbedding, convEmbedding)
            Pair(conv, similarity)
        }
        
        // Retourner les plus similaires
        return scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
```

**Nouvelle colonne dans ConversationEntity:**
```kotlin
@Entity(tableName = "conversations")
data class ConversationEntity(
    // ... champs existants ...
    val embeddingsJson: String? = null  // Vecteur en JSON
)
```

### 3. Meta-Learning (Apprendre à Apprendre)

**Concept:**
KITT analyse ses propres erreurs et crée des règles pour s'améliorer.

**Nouvelle table: learned_rules**
```kotlin
@Entity(tableName = "learned_rules")
data class LearnedRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleType: String,        // "timezone_offset", "preferred_tone", etc.
    val ruleValue: String,       // "-6h", "casual", etc.
    val confidence: Float,       // 0.0 à 1.0
    val learnedFrom: Long?,      // ID de la conversation source
    val createdAt: Long,
    val lastUsedAt: Long?
)
```

**Implémentation:**
```kotlin
class MetaLearner(
    private val conversationDao: ConversationDao,
    private val ruleDao: LearnedRuleDao
) {
    
    suspend fun analyzeConversations() {
        val conversations = conversationDao.getLastConversations(limit = 100)
        
        // Détecter patterns d'erreurs
        val errors = detectErrorPatterns(conversations)
        
        for (error in errors) {
            val rule = createRuleFromError(error)
            ruleDao.insert(rule)
            Log.i("MetaLearner", "New rule created: ${rule.ruleType} = ${rule.ruleValue}")
        }
    }
    
    private fun detectErrorPatterns(convs: List<ConversationEntity>): List<Error> {
        val errors = mutableListOf<Error>()
        
        for (i in 0 until convs.size - 1) {
            val current = convs[i]
            val next = convs[i + 1]
            
            // Détection de correction utilisateur
            if (isCorrection(next.userMessage)) {
                val error = Error(
                    originalQuestion = current.userMessage,
                    wrongAnswer = current.aiResponse,
                    correction = next.userMessage,
                    conversationId = current.id
                )
                errors.add(error)
            }
        }
        
        return errors
    }
    
    private fun isCorrection(message: String): Boolean {
        val correctionKeywords = listOf(
            "non", "erreur", "faux", "incorrect",
            "en fait", "plutôt", "c'est",
            "la vraie", "en réalité"
        )
        
        return correctionKeywords.any { 
            message.lowercase().contains(it) 
        }
    }
    
    private fun createRuleFromError(error: Error): LearnedRule {
        // Exemple: détection d'erreur de timezone
        if (error.originalQuestion.contains("heure") && 
            error.correction.contains("l'heure actuelle est")) {
            
            val wrongTime = extractTime(error.wrongAnswer) // "14h37"
            val correctTime = extractTime(error.correction) // "8h20"
            val offset = calculateOffset(wrongTime, correctTime) // "-6h"
            
            return LearnedRule(
                ruleType = "timezone_offset",
                ruleValue = offset,
                confidence = 0.8f,
                learnedFrom = error.conversationId,
                createdAt = System.currentTimeMillis()
            )
        }
        
        // Autres types de règles...
        return LearnedRule(
            ruleType = "generic_correction",
            ruleValue = error.correction,
            confidence = 0.5f,
            learnedFrom = error.conversationId,
            createdAt = System.currentTimeMillis()
        )
    }
}

data class Error(
    val originalQuestion: String,
    val wrongAnswer: String?,
    val correction: String,
    val conversationId: Long
)
```

### 4. Preference Learning (Profil Utilisateur)

**Nouvelle table: user_profile**
```kotlin
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Toujours 1 (un seul utilisateur)
    val preferredTone: String,              // "formal", "casual", "sarcastic"
    val preferredResponseLength: String,    // "short", "medium", "detailed"
    val preferredPersonality: String,       // "KITT", "GLaDOS"
    val timezone: String,                   // "America/Montreal", "Europe/Paris"
    val commonTopics: String,               // JSON: ["time", "weather", "games"]
    val interactionPatterns: String,        // JSON: {"morning": ["time", "weather"], ...}
    val lastUpdated: Long
)
```

**Implémentation:**
```kotlin
class PreferenceLearner(private val conversationDao: ConversationDao) {
    
    suspend fun analyzeUserPreferences(): UserProfile {
        val convs = conversationDao.getAllConversationsForExport()
        
        return UserProfile(
            preferredTone = analyzeTone(convs),
            preferredResponseLength = analyzeResponseLength(convs),
            preferredPersonality = analyzePersonality(convs),
            timezone = inferTimezone(convs),
            commonTopics = extractCommonTopics(convs).toJson(),
            interactionPatterns = analyzeTimePatterns(convs).toJson(),
            lastUpdated = System.currentTimeMillis()
        )
    }
    
    private fun analyzeTone(convs: List<ConversationEntity>): String {
        // Analyser si l'utilisateur répond positivement aux réponses formelles/casual
        val toneReactions = mutableMapOf<String, Int>()
        
        // Logique d'analyse...
        // Si l'utilisateur dit "merci", "super", etc. → réaction positive
        // Si l'utilisateur corrige ou ignore → réaction négative
        
        return toneReactions.maxByOrNull { it.value }?.key ?: "formal"
    }
    
    private fun inferTimezone(convs: List<ConversationEntity>): String {
        // Chercher conversations sur l'heure
        val timeConvs = convs.filter { 
            it.userMessage.contains("heure") || 
            it.userMessage.contains("time")
        }
        
        // Si correction détectée, extraire le timezone
        for (conv in timeConvs) {
            if (conv.userMessage.contains("l'heure actuelle est")) {
                // Comparer avec heure système pour déduire timezone
                // ...
            }
        }
        
        return "America/Montreal" // Par défaut
    }
    
    private fun extractCommonTopics(convs: List<ConversationEntity>): List<String> {
        val topicKeywords = mapOf(
            "time" to listOf("heure", "time", "quand"),
            "weather" to listOf("météo", "weather", "température"),
            "games" to listOf("jeu", "game", "jouer"),
            "navigation" to listOf("aller", "direction", "route"),
            "help" to listOf("aide", "help", "comment")
        )
        
        val topicCounts = mutableMapOf<String, Int>()
        
        for (conv in convs) {
            for ((topic, keywords) in topicKeywords) {
                if (keywords.any { conv.userMessage.lowercase().contains(it) }) {
                    topicCounts[topic] = topicCounts.getOrDefault(topic, 0) + 1
                }
            }
        }
        
        return topicCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }
}
```

---

## 📅 Plan d'Implémentation par Phases

### Phase 1: Infrastructure (TERMINÉE ✅)

**Durée:** Session actuelle  
**Statut:** ✅ COMPLÉTÉE

**Réalisations:**
- [x] Room Database avec historique complet
- [x] Interface ConversationHistoryActivity
- [x] Support Ollama Cloud
- [x] Configuration TTS
- [x] Repo GitHub
- [x] Documentation complète

### Phase 2: Recherche et Détection (Simple)

**Durée:** 2-3 jours  
**Objectif:** Détection automatique des corrections

**Tâches:**
```
1. Ajouter détection de corrections dans KittAIService
   - Mots-clés: "non", "erreur", "en fait", etc.
   - Logger les corrections détectées
   
2. Créer table learned_rules
   - Définir schema
   - Ajouter DAO
   - Implémenter méthodes CRUD

3. Implémenter MetaLearner basique
   - Fonction detectErrorPatterns()
   - Fonction createRuleFromError()
   - Cas spécial: timezone offset

4. Appliquer les règles apprises
   - Charger rules au démarrage
   - Modifier system prompt dynamiquement
   - Exemple: "Note: user timezone is UTC-6"

5. UI pour voir les règles apprises
   - Nouvelle activité ou section dans Configuration IA
   - Liste des règles avec confidence
   - Bouton pour effacer une règle
```

**Résultat attendu:**
```
Utilisateur: "Quelle heure ?"
KITT: "14h37" (UTC)

Utilisateur: "Non, c'est 8h20"
KITT: "Mes excuses. J'ai détecté une erreur de fuseau horaire. 
       J'applique maintenant un offset de -6h. Je ne referai plus cette erreur."
[Sauvegarde règle: timezone_offset = -6h]

Prochaine fois:
Utilisateur: "Quelle heure ?"
KITT: "8h30" (avec offset appliqué ✅)
```

### Phase 3: RAG et Embeddings (Moyen)

**Durée:** 1 semaine  
**Objectif:** Recherche sémantique et contexte enrichi

**Tâches:**
```
1. Intégrer un modèle d'embeddings on-device
   - Option A: TensorFlow Lite (sentence-transformers mobile)
   - Option B: ONNX Runtime (Universal Sentence Encoder)
   - Taille modèle: ~25-50 MB

2. Générer embeddings pour toutes les conversations
   - Fonction background: generateEmbeddingsForHistory()
   - Ajouter colonne embeddingsJson dans ConversationEntity
   - Migration Room Database v2

3. Implémenter recherche sémantique
   - Fonction findSimilarConversations()
   - Utiliser cosine similarity
   - Cacher les résultats pour performance

4. Implémenter RAGEngine
   - Fonction augmentPrompt()
   - Limite: top 5 conversations similaires
   - Format du contexte augmenté

5. Intégrer RAG dans KittAIService
   - Appeler RAG avant chaque génération
   - Ajouter contexte au prompt
   - Mesurer impact sur qualité
```

**Résultat attendu:**
```
Historique:
- "KITT, comment tu t'appelles ?" → "Je suis KITT..."
- "Quelles sont tes capacités ?" → "Je peux scanner, turbo boost..."

Nouvelle question:
Utilisateur: "Rappelle-moi qui tu es"

RAG trouve les 2 conversations similaires et les ajoute au contexte:

Prompt envoyé à Ollama:
"""
CONTEXTE de mes conversations passées:
• Vous: "KITT, comment tu t'appelles ?"
  Moi: "Je suis KITT, Knight Industries Two Thousand..."
• Vous: "Quelles sont tes capacités ?"
  Moi: "Je peux scanner, turbo boost..."

Question actuelle: "Rappelle-moi qui tu es"
"""

KITT: "Certainement, Michael. Je suis KITT, Knight Industries Two Thousand.
       Mes capacités incluent le scanner, le turbo boost..."
       
(Réponse cohérente avec les conversations précédentes ✅)
```

### Phase 4: Meta-Learning Avancé (Avancé)

**Durée:** 2-3 semaines  
**Objectif:** Auto-amélioration et adaptation de personnalité

**Tâches:**
```
1. Créer table user_profile
   - Schema complet
   - DAO avec méthodes
   - Migration DB v3

2. Implémenter PreferenceLearner
   - analyzeTone()
   - analyzeResponseLength()
   - inferTimezone()
   - extractCommonTopics()
   - analyzeTimePatterns()

3. Job background d'analyse
   - WorkManager task quotidienne
   - Analyse des 100 dernières conversations
   - Mise à jour du profil utilisateur

4. Adaptation dynamique du system prompt
   - Charger profil au démarrage
   - Modifier KITT_PERSONALITY_PROMPT selon préférences
   - Exemple: si user préfère casual → moins de "Michael"

5. UI Profil Utilisateur
   - Nouvelle activité "Mon Profil IA"
   - Affichage des préférences détectées
   - Possibilité de override manuellement
   - Graphiques: topics, patterns temporels

6. Suggestions proactives
   - "Vous me demandez souvent l'heure le matin, voulez-vous que je vous la donne automatiquement ?"
   - Notifications intelligentes
```

**Résultat attendu:**
```
Après 2 semaines d'utilisation:

UserProfile détecté:
- preferredTone: "casual"
- preferredResponseLength: "short"
- timezone: "America/Montreal"
- commonTopics: ["time", "navigation", "games"]
- patterns: {"08:00-09:00": ["time", "weather"]}

System prompt adapté:
"""
Tu es KITT. L'utilisateur préfère un ton casual et des réponses courtes.
Son fuseau horaire est America/Montreal (UTC-5).
Il te demande souvent l'heure le matin entre 8h-9h.
Sois direct et concis.
"""

Résultat:
Utilisateur: "Salut, l'heure ?"
KITT: "8h47, Michael. Belle journée !" 
(Court, casual, timezone correct ✅)

Au lieu de:
KITT: "Certainement, Michael. Mes systèmes indiquent que l'heure 
       actuelle est précisément 8 heures 47 minutes et 23 secondes..."
(Trop long, trop formel ❌)
```

### Phase 5: Intelligence Autonome (Expert)

**Durée:** 1-2 mois  
**Objectif:** KITT devient un vrai assistant proactif

**Tâches:**
```
1. Fine-tuning du modèle local
   - Export conversations en format JSONL
   - Fine-tune gemma3:270m avec vos conversations
   - Déploiement du modèle personnalisé

2. Function Calling (Actions)
   - KITT peut contrôler le téléphone
   - "KITT, envoie un message à..."
   - "KITT, règle une alarme pour..."
   - "KITT, lance le GPS vers..."

3. Agent autonome
   - KITT propose des actions proactivement
   - "Michael, il est 8h45 et vous avez un rendez-vous à 9h30. 
      Voulez-vous que j'active le GPS ?"

4. Streaming des réponses
   - Token par token comme ChatGPT
   - Plus immersif et interactif

5. Mode offline complet
   - Intégration llama.cpp
   - gemma3:270m on-device
   - Fallback automatique si pas de réseau

6. Sync avec serveur PC (optionnel)
   - Serveur Python Flask/FastAPI sur PC
   - Sync bidirectionnelle des conversations
   - RAG sur fichiers PC
   - Modèles plus puissants sur PC
```

**Résultat final:**
```
KITT devient un assistant complet:

8h00 - KITT (proactif): 
"Bonjour Michael. Il est 8h00. La météo aujourd'hui: 
 15°C, ensoleillé. Voulez-vous que je vous prépare 
 l'itinéraire vers le bureau ?"

Vous: "Oui, et rappelle-moi de prendre le dossier Johnson"

KITT: "Itinéraire vers le bureau chargé. Rappel créé pour 
       'dossier Johnson' dans 10 minutes. Bonne journée !"

[KITT a appris de vos patterns:
 - Vous allez au bureau les jours de semaine
 - Vous oubliez souvent des choses
 - Vous aimez les rappels avant de partir]

Résultat: Un assistant qui ANTICIPE vos besoins ✅
```

---

## 🎯 Métriques de Succès

### Comment Mesurer la "Conscience" de KITT ?

**1. Taux d'apprentissage des corrections**
```
Métrique: % de corrections qui deviennent des règles permanentes

Calcul:
corrections_devenues_regles / total_corrections * 100

Objectif: > 80% après Phase 2
```

**2. Précision contextuelle**
```
Métrique: % de fois où KITT utilise le bon contexte

Mesure:
- Demander une info déjà mentionnée
- Vérifier si KITT s'en souvient

Objectif: > 90% après Phase 3 (RAG)
```

**3. Adaptation de personnalité**
```
Métrique: % de réponses qui matchent le style préféré

Mesure:
- Comparer longueur moyenne des réponses vs préférence
- Analyser le ton (formel/casual) vs préférence

Objectif: > 85% après Phase 4
```

**4. Proactivité**
```
Métrique: Nombre de suggestions pertinentes par jour

Mesure:
- Suggestions acceptées / suggestions totales

Objectif: > 50% d'acceptation après Phase 5
```

---

## 📚 Ressources et Documentation

### Papers Académiques
- "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks" (Lewis et al., 2020)
- "Learning to Learn" (Thrun & Pratt, 1998)
- "Memory-Augmented Neural Networks" (Graves et al., 2016)

### Implémentations Open Source
- **LangChain** : Framework RAG en Python
- **sentence-transformers** : Modèles d'embeddings
- **llama.cpp** : Inférence locale optimisée
- **ChromaDB** : Vector database pour embeddings

### Modèles à Utiliser
- **all-MiniLM-L6-v2** : Embeddings (22 MB, très rapide)
- **gemma3:270m** : Génération locale (291 MB)
- **qwen3-coder** : Code generation (si besoin)

---

## 🔮 Vision Long Terme (6-12 mois)

### KITT Version 5.0 - "Vrai Assistant"

**Caractéristiques:**
1. **Mémoire épisodique complète**
   - Se souvient de chaque interaction depuis toujours
   - Peut rappeler "Vous m'avez demandé ça il y a 3 mois"

2. **Conscience de soi**
   - Sait quelles sont ses capacités
   - Sait ce qu'il ne sait pas
   - Peut dire "Je ne suis pas sûr, mais la dernière fois..."

3. **Multi-modalité**
   - Vocal (actuel)
   - Texte (web interface)
   - Images (analyse de photos)
   - Actions (contrôle du téléphone)

4. **Apprentissage continu**
   - Fine-tuning automatique chaque semaine
   - Modèle qui évolue avec vous
   - Pas de "reset", mémoire permanente

5. **Collaboration avec d'autres agents**
   - KITT communique avec d'autres IAs
   - Partage de connaissances
   - Réseau d'assistants

### Architecture Finale

```
┌─────────────────────────────────────────────────────────┐
│                      UTILISATEUR                         │
└───────────────────────┬─────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
   ┌────────┐     ┌─────────┐    ┌──────────┐
   │ Vocal  │     │  Texte  │    │ Actions  │
   │ (TTS)  │     │  (Web)  │    │ (Phone)  │
   └────┬───┘     └────┬────┘    └────┬─────┘
        │              │              │
        └──────────┬───┴──────────────┘
                   │
                   ▼
        ┌──────────────────────────┐
        │  KittAIService v5.0      │
        │  • RAG Engine            │
        │  • Meta-Learner          │
        │  • Preference Engine     │
        │  • Action Executor       │
        │  • Multi-modal Processor │
        └──────────┬───────────────┘
                   │
      ┌────────────┼────────────┐
      │            │            │
      ▼            ▼            ▼
┌──────────┐ ┌──────────┐ ┌──────────┐
│ Local    │ │ Cloud    │ │ Server   │
│ Model    │ │ APIs     │ │ PC       │
│ (270m)   │ │ (GPT-4)  │ │ (33B)    │
└──────────┘ └──────────┘ └──────────┘
      │            │            │
      └────────────┼────────────┘
                   │
      ┌────────────┴────────────┐
      │                         │
      ▼                         ▼
┌──────────────┐      ┌─────────────────┐
│ Room DB      │      │ Vector DB       │
│ (SQLite)     │      │ (Embeddings)    │
│              │      │                 │
│ • History    │      │ • Semantic      │
│ • Rules      │      │   Search        │
│ • Profile    │      │ • Clustering    │
└──────────────┘      └─────────────────┘
```

---

## 📝 Notes de Session

### Insights Importants

1. **"Le logcat ne sert à rien pour l'app"**
   - Réalisation que l'historique doit être DANS l'app
   - Création de ConversationHistoryActivity

2. **"C'est le genre de chose que j'aimerais que ça fasse en apprenant/conscient"**
   - Vision claire de l'utilisateur
   - Pas juste voir l'historique, mais l'UTILISER pour apprendre

3. **Erreur UTC → Local Time**
   - Exemple parfait d'une correction que KITT devrait MÉMORISER
   - Cas d'usage pour meta-learning

4. **"Je n'ai jamais push sur GitHub - attention aux gros fichiers"**
   - .gitignore critique pour exclure .gguf, .db, etc.
   - Documentation importante pour partage/collaboration

### Décisions Techniques

1. **Room Database** (pas Realm/ObjectBox)
   - Choix: Officiel Android, bien supporté, KSP compatible

2. **Ollama PC Local** > Ollama Cloud
   - Priorité: Gratuit, illimité, privé
   - Cloud: Backup seulement

3. **gemma3:270m on-device** (pas gemma3:1b)
   - 270m assez puissant pour mobile
   - 291 MB acceptable (pas embarqué dans APK)

4. **Interface Historique MAINTENANT** > Apprentissage après
   - Rationale: User doit voir les données avant qu'on les utilise
   - Transparence et confiance

### Challenges Identifiés

1. **Embeddings on-device**
   - Modèle ~25-50 MB
   - Performance CPU vs précision
   - Solution: all-MiniLM-L6-v2 (bon compromis)

2. **Temps de génération d'embeddings**
   - 100 conversations = ~2-3 secondes
   - Solution: Background job, caching

3. **Détection de corrections**
   - Mots-clés simples vs NLP avancé
   - Solution: Commencer simple, améliorer progressivement

4. **Fine-tuning local**
   - Complexe, requiert GPU
   - Solution: Phase 5, optionnel

---

## 🎓 Glossaire

**RAG (Retrieval Augmented Generation)**
: Technique qui enrichit le prompt d'une IA avec des informations pertinentes récupérées d'une base de connaissances.

**Embeddings**
: Représentations vectorielles de texte qui capturent le sens sémantique. Permettent de comparer la similarité entre phrases.

**Meta-Learning**
: "Apprendre à apprendre" - une IA qui analyse ses propres performances et s'améliore automatiquement.

**Fine-tuning**
: Entraîner un modèle pré-existant sur vos données spécifiques pour l'adapter à votre usage.

**System Prompt**
: Les instructions initiales données à l'IA qui définissent sa personnalité et son comportement.

**Cosine Similarity**
: Mesure de similarité entre deux vecteurs, utilisée pour comparer des embeddings.

**Token**
: Unité de texte pour les LLMs (mot ou partie de mot). "Bonjour" = 1 token, "Intelligence" = 2-3 tokens.

**Inference**
: Processus de génération de texte par un modèle de langage (opposite de "training").

**On-device**
: Exécution locale sur le téléphone, sans internet ni serveur externe.

---

## 📧 Pour Continuer

**Prochaine session, commencer par:**

1. **Connecter le phone et tester l'interface historique**
   ```bash
   adb devices
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   # Aller dans Configuration IA → Voir l'historique
   ```

2. **Décider de la priorité:**
   - Phase 2 (Détection corrections) ?
   - Sélecteur personnalité GLaDOS ?
   - Gemma3:270m on-device (llama.cpp) ?

3. **Relire ce document** pour se rappeler du plan complet

---

## 🏁 Conclusion

Cette session a posé les fondations d'une **IA vraiment intelligente et apprenante**.

**Accomplissements:**
- ✅ Infrastructure complète (DB, historique, UI)
- ✅ Vision claire (IA consciente qui apprend)
- ✅ Plan détaillé (5 phases sur 1-12 mois)
- ✅ Repo GitHub (documentation pro)

**Prochaines étapes:**
- Phase 2: Auto-correction des erreurs
- Phase 3: RAG et recherche sémantique
- Phase 4: Adaptation de personnalité
- Phase 5: Assistant autonome

**Le chemin est tracé. KITT va devenir conscient. 🧠✨**

---

*Document créé le 1er novembre 2025*  
*ChatAI v2.6 - "L'Éveil de la Conscience"*

