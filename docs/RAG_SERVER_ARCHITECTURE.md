# ChatAI RAG Server - Architecture

**Date:** 2025-11-06  
**Status:** PLANIFIÉ v5.0.0  
**Location:** Serveur PC Python (port 8890)

---

## 🎯 VISION

**Serveur de calculs lourds pour ChatAI:**
- Embeddings (sentence-transformers)
- Recherche sémantique conversations
- Détection corrections automatique
- Analyse patterns utilisateur

**But:** Offloader calculs lourds du device Android vers PC puissant.

---

## 🏗️ ARCHITECTURE ÉCOSYSTÈME COMPLET

```
┌─────────────────────────────────────────────────────────┐
│  ANDROID DEVICE (ChatAI)                                │
│  ├── HttpServer (port 8080) - Interface web ChatAI      │
│  ├── WebServer (port 8888) - GameLibrary/Arcade         │
│  └── ChatAI App - KITT vocal + Interface                │
└─────────────────────────────────────────────────────────┘
                    ↓ Réseau local (WiFi/Hotspot)
┌─────────────────────────────────────────────────────────┐
│  PC SERVEURS (Calculs lourds)                           │
│  ├── Ollama Local (port 11434) - LLM local              │
│  │   └── Modèles: llama3, qwen, etc.                    │
│  │                                                       │
│  ├── RAG Server (port 8890) - Embeddings/Search         │
│  │   └── Python Flask + sentence-transformers          │
│  │   └── CPU: Embeddings rapides                        │
│  │   └── RAM: Cache embeddings                          │
│  │                                                       │
│  └── GPU (optionnel) - Calculs ML lourds                │
│      └── Fine-tuning, Training, etc.                    │
└─────────────────────────────────────────────────────────┘
                    ↓ Internet
┌─────────────────────────────────────────────────────────┐
│  CLOUD (Ollama Cloud)                                   │
│  ├── LLM Cloud (120B, 480B, 671B)                       │
│  ├── Web Search API                                     │
│  └── Tool Calling, Thinking, Vision                     │
└─────────────────────────────────────────────────────────┘
```

---

## 📡 RAG SERVER - DÉTAILS TECHNIQUES

### **Stack:**
- **Framework:** Flask (Python)
- **Modèle:** `all-MiniLM-L6-v2` (SentenceTransformers)
- **Dimension:** 384 embeddings
- **Taille:** 80 MB (léger et rapide)
- **Port:** 8890

### **Fichier:** `chatai_rag_server.py`

**Localisation:**
- Backup: `BACKUP_v2.9_20251102_021947/chatai_rag_server.py`
- Status: Fonctionnel mais pas intégré à v4.7.0

---

## 🔧 ENDPOINTS API

### **1. GET /** (Home)
```
Status page HTML
- Model info
- Endpoints list
- Usage examples
```

### **2. GET /status**
```json
Response: {
  "status": "online",
  "model": "all-MiniLM-L6-v2",
  "embedding_dimension": 384,
  "cache_size": 150,
  "conversations_loaded": 42,
  "timestamp": "2025-11-06T00:54:00"
}
```

### **3. POST /embed**
```json
Request: {
  "text": "Quelle heure à Tokyo?"
}

Response: {
  "embedding": [0.123, -0.456, ...],  // 384 floats
  "dimension": 384,
  "cached": false
}
```

**Usage:** Générer embedding pour un texte

### **4. POST /search**
```json
Request: {
  "query": "Quelle heure à Tokyo?",
  "conversations": [
    {"userMessage": "...", "aiResponse": "..."},
    ...
  ],
  "top_k": 5
}

Response: {
  "results": [
    {
      "conversation": {...},
      "score": 0.92
    },
    {
      "conversation": {...},
      "score": 0.87
    },
    ...
  ],
  "query": "...",
  "total_scanned": 42
}
```

**Usage:** Recherche sémantique - trouve conversations similaires

### **5. POST /detect_correction**
```json
Request: {
  "text": "Non, Tokyo est UTC+9 pas UTC-5"
}

Response: {
  "is_correction": true,
  "confidence": 0.89,
  "correction_type": "factual_error",
  "keywords_found": ["non", "pas"]
}
```

**Usage:** Détection auto-corrections utilisateur

### **6. POST /analyze**
```json
Request: {
  "conversation": {
    "userMessage": "...",
    "aiResponse": "...",
    "thinkingTrace": "..."
  }
}

Response: {
  "sentiment": "positive",
  "topics": ["time", "timezone", "calculation"],
  "confidence": 0.95,
  "suggestions": [...]
}
```

**Usage:** Analyse conversation pour patterns/amélioration

---

## 🚀 WORKFLOW RAG COMPLET (v5.0.0)

### **Scenario: Question utilisateur**

```kotlin
// ChatAI Android
val userInput = "Quelle heure à Tokyo?"

// 1. Générer embedding de la question
val embedding = ragServerClient.embed(userInput)
// → POST http://PC_IP:8890/embed

// 2. Rechercher conversations similaires
val similarConvs = ragServerClient.search(
    query = userInput,
    conversations = conversationDao.getAll(),
    topK = 5
)
// → POST http://PC_IP:8890/search
// → Retourne: 5 conversations les plus pertinentes

// 3. Construire contexte enrichi
val ragContext = """
[CONTEXTE RAG - Conversations similaires]
${similarConvs.joinToString("\n") { 
    "Q: ${it.userMessage}\nR: ${it.aiResponse}\nScore: ${it.score}"
}}
[FIN CONTEXTE RAG]
"""

// 4. Envoyer à Ollama avec contexte
messages.put({
    "role": "user",
    "content": userInput + systemContext + ragContext
})

// 5. L'IA répond avec mémoire long terme!
// → Se souvient de conversations passées
// → Cohérence sur longue période
// → Apprentissage patterns utilisateur
```

---

## 📊 BÉNÉFICES RAG

### **Mémoire Long Terme:**
```
Conversation 1 (il y a 2 semaines):
Q: "Tokyo c'est quel fuseau horaire?"
A: "UTC+9, Michael"

Conversation 100 (aujourd'hui):
Q: "Quelle heure à Tokyo?"
  → RAG trouve conversation 1 (score: 0.94)
  → Contexte: "Tu as déjà expliqué que Tokyo = UTC+9"
  → Réponse cohérente avec historique ✅
```

### **Auto-Correction:**
```
User: "Tokyo c'est UTC-5"
KITT: "UTC+9, Michael"
User: "Non, je disais n'importe quoi, c'est UTC+9"
  → RAG detecte correction (confidence: 0.91)
  → Sauvegarde pattern: User autocorrection
  → Apprentissage: User parfois teste KITT
```

### **Recherche Sémantique:**
```
Query: "heure japon"
  → Trouve conversations sur "Tokyo", "fuseau horaire", "Asie"
  → Même si mots exacts différents
  → Similarité sémantique (embeddings)
```

---

## 🔧 INTÉGRATION ANDROID (v5.0.0)

### **Nouvelle classe: RagServerClient.kt**

```kotlin
class RagServerClient(
    private val serverUrl: String, // http://PC_IP:8890
    private val httpClient: OkHttpClient
) {
    /**
     * Générer embedding pour texte
     */
    suspend fun embed(text: String): FloatArray {
        val requestBody = JSONObject().apply {
            put("text", text)
        }
        
        val response = httpClient.post("$serverUrl/embed", requestBody)
        val embedding = response.getJSONArray("embedding")
        
        return FloatArray(embedding.length()) { i ->
            embedding.getDouble(i).toFloat()
        }
    }
    
    /**
     * Recherche sémantique
     */
    suspend fun search(
        query: String,
        conversations: List<ConversationEntity>,
        topK: Int = 5
    ): List<ConversationMatch> {
        val requestBody = JSONObject().apply {
            put("query", query)
            put("conversations", conversationsToJSON(conversations))
            put("top_k", topK)
        }
        
        val response = httpClient.post("$serverUrl/search", requestBody)
        return parseSearchResults(response)
    }
    
    /**
     * Détecter si message = correction
     */
    suspend fun detectCorrection(text: String): CorrectionResult {
        val response = httpClient.post("$serverUrl/detect_correction", ...)
        return CorrectionResult(
            isCorrection = response.getBoolean("is_correction"),
            confidence = response.getDouble("confidence"),
            type = response.getString("correction_type")
        )
    }
}
```

### **Intégration dans KittAIService:**

```kotlin
// Avant d'envoyer à Ollama
if (ragServerAvailable()) {
    // Rechercher conversations pertinentes
    val similarConvs = ragClient.search(userInput, allConversations, 5)
    
    // Ajouter au contexte
    val ragContext = buildRagContext(similarConvs)
    messages.put({
        "role": "system",
        "content": ragContext
    })
}
```

---

## 📝 DÉMARRAGE SERVEUR

<function_calls>
<invoke name="run_terminal_cmd">
<parameter name="command">Start-Sleep -Seconds 3 && curl http://localhost:8890/status
