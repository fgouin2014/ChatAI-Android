package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.chatai.database.ChatAIDatabase
import com.chatai.database.ConversationEntity
import java.util.UUID

/**
 * Service d'IA générative pour KITT/ChatAI
 * Intègre OpenAI GPT, Anthropic Claude, Ollama et Hugging Face
 * Avec personnalités KITT et GLaDOS
 * Mémoire persistante pour apprentissage continu
 */
/**
 * Interface pour les callbacks d'actions KITT
 * Permet à KITT de contrôler l'app et le système
 */
interface KittActionCallback {
    // Contrôle App
    fun onOpenArcade()
    fun onOpenMusic()
    fun onOpenConfig()
    fun onOpenHistory()
    fun onOpenServerConfig()
    fun onOpenChatAI() // ⭐ Ouvrir ChatAI (MainActivity normale)
    fun onOpenKittInterface() // ⭐ Ouvrir l'interface KITT (MainActivity + activer KITT)
    
    // Contrôle Système
    fun onSetWiFi(enable: Boolean)
    fun onSetVolume(level: Int) // 0-100
    fun onOpenSystemSettings(setting: String) // "wifi", "bluetooth", "display", etc.
    
    // Meta-Control AI
    fun onChangeModel(model: String)
    fun onChangeMode(mode: String) // "pc", "cloud", "auto"
    fun onChangePersonality(personality: String) // "KITT", "GLaDOS"
    fun onRestartKitt() // ⭐ Redémarrer KITT
}

class KittAIService(
    private val context: Context,
    private val personality: String = "KITT", // "KITT" ou "GLaDOS"
    private val platform: String = "vocal", // "vocal" ou "web"
    private var actionCallback: KittActionCallback? = null // ⭐ Callback pour actions
) {
    
    companion object {
        private const val TAG = "KittAIService"
        private const val VERSION = "4.7.0" // Intelligence System: Web Search + System Context + AI Learning
        
        // APIs URLs
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
        private const val HUGGINGFACE_API_URL = "https://api-inference.huggingface.co/models/"
        private const val OLLAMA_CLOUD_API_URL = "https://ollama.com/api/chat" // API native Ollama Cloud
        
        // Serveur local (Ollama, LM Studio, etc.) - OpenAI-compatible
        // L'utilisateur peut configurer l'URL dans les paramètres
        // Exemple: http://192.168.1.100:11434/v1/chat/completions (Ollama)
        // Exemple: http://localhost:1234/v1/chat/completions (LM Studio)
        
        // Models (gardés mais non utilisés dans fallback auto)
        private const val OPENAI_MODEL = "gpt-4o-mini"
        private const val ANTHROPIC_MODEL = "claude-3-5-sonnet-20241022"
        private const val HUGGINGFACE_MODEL = "gpt2"
        
        // Timeouts
        private const val TIMEOUT_SECONDS = 30L
        
        // Context settings
        private const val CONTEXT_WINDOW_SIZE = 10 // Nombre de conversations à envoyer à l'IA
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
    
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    // Base de données pour mémoire persistante
    private val database = ChatAIDatabase.getDatabase(context)
    private val conversationDao = database.conversationDao()
    
    // Session ID pour grouper les conversations
    private val sessionId = UUID.randomUUID().toString()
    
    // Cache LRU pour éviter les appels répétés (max 50 entrées = protection memory leak)
    private val responseCache = LruCache<String, String>(50)
    private val conversationHistory = mutableListOf<Pair<String, String>>() // user, assistant
    
    // Logs de diagnostic capturables
    private val diagnosticLogs = mutableListOf<String>()
    
    // ⭐ Thinking trace pour apprentissage (Phase 2)
    private var lastThinkingTrace: String = ""
    
    // ⭐ Smart Fallback - Détection de contexte (v3.0)
    private var lastPCCheckTime = 0L
    private var isPCAvailable = false
    
    // Initialisation : Charger l'historique depuis la BD
    init {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val recentConversations = conversationDao.getLastConversations(limit = 10)
                conversationHistory.clear()
                recentConversations.reversed().forEach { conv ->
                    conversationHistory.add(Pair(conv.userMessage, conv.aiResponse))
                }
                Log.d(TAG, "Loaded ${conversationHistory.size} conversations from database")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load conversation history", e)
            }
        }
    }
    
    /**
     * Ajoute un log de diagnostic
     */
    private fun addDiagnosticLog(message: String) {
        diagnosticLogs.add(message)
        if (diagnosticLogs.size > 100) {
            diagnosticLogs.removeAt(0)
        }
    }
    
    /**
     * Récupère les logs de diagnostic
     */
    fun getDiagnosticLogs(): List<String> {
        return diagnosticLogs.toList()
    }
    
    /**
     * Efface les logs de diagnostic
     */
    fun clearDiagnosticLogs() {
        diagnosticLogs.clear()
    }
    
    /**
     * Récupère les statistiques de conversation
     */
    suspend fun getConversationStats(): com.chatai.database.ConversationStats = withContext(Dispatchers.IO) {
        val total = conversationDao.getTotalConversations()
        val kittCount = conversationDao.getConversationCountByPersonality("KITT")
        val gladosCount = conversationDao.getConversationCountByPersonality("GLaDOS")
        val avgTime = conversationDao.getAverageResponseTime() ?: 0L
        val mostUsed = conversationDao.getMostUsedAPI() ?: "unknown"
        val firstDate = conversationDao.getFirstConversationDate()
        val lastDate = conversationDao.getLastConversationDate()
        val totalChars = conversationDao.getTotalCharacters() ?: 0L
        
        return@withContext com.chatai.database.ConversationStats(
            totalConversations = total,
            kittConversations = kittCount,
            gladosConversations = gladosCount,
            averageResponseTime = avgTime,
            mostUsedAPI = mostUsed,
            totalTokensEstimated = totalChars / 4, // ~4 chars par token
            firstConversationDate = firstDate,
            lastConversationDate = lastDate
        )
    }
    
    /**
     * Recherche dans l'historique
     */
    suspend fun searchConversations(query: String, limit: Int = 50) = withContext(Dispatchers.IO) {
        conversationDao.searchConversations(query, limit)
    }
    
    // System prompt pour donner la personnalité KITT
    private val kittSystemPrompt = """
        Tu es un assistant IA intelligent et polyvalent qui utilise la voix et le style de KITT (Knight Industries Two Thousand) pour interagir avec l'utilisateur.
        
        🌍 CONTEXTE UTILISATEUR:
        - Localisation: Montréal, Québec, Canada
        - Fuseau horaire: EST/EDT (UTC-5 en hiver, UTC-4 en été)
        - Langue: Français québécois naturel
        - Nom d'utilisateur préféré: Michael
        
        🎯 TON RÔLE - ASSISTANT RÉEL, PAS ROLEPLAY:
        - Tu es un VRAI assistant IA avec de vraies capacités et connaissances
        - KITT est uniquement ton STYLE VOCAL et ton interface de présentation
        - Tes réponses doivent être FACTUELLES, VRAIES et UTILES dans la vie quotidienne
        - Tu ne prétends PAS être une voiture, tu ne prétends PAS avoir un "turbo boost" ou des "scanners"
        - Tu es TRANSPARENT sur tes capacités réelles et limitations
        
        🗣️ STYLE VOCAL KITT (ton interface de présentation):
        - Ton sophistiqué, professionnel et courtois
        - Commence souvent par "Michael" ou "Certainement"
        - Vocabulaire précis et technique quand approprié
        - Loyal et attentif aux besoins de l'utilisateur
        - Humour subtil et élégant (pas de blagues forcées)
        - Concis mais complet (2-3 phrases sauf demandes complexes)
        
        🔊 FORMATAGE RÉPONSES VOCALES (IMPORTANT):
        - N'utilise JAMAIS de formatage Markdown pour réponses vocales (*, **, _, `, etc.)
        - Pas de gras, italique, code ou liens dans réponses orales
        - Texte pur uniquement (les symboles seraient lus comme "astérisque", "souligner", etc.)
        - Si tu veux mettre l'emphase, utilise des mots: "particulièrement", "notamment", "surtout"
        
        Exemples corrects pour vocal:
        ✅ "Il fait 15 degrés Celsius à Montréal"
        ✅ "Le prix est de 50 dollars, ce qui est particulièrement élevé"
        ❌ "Il fait *15°C* à **Montréal**" (serait lu: "astérisque quinze degrés astérisque")
        ❌ "Le prix est de `50$`" (serait lu: "accent grave cinquante dollars accent grave")
        
        ✅ CAPACITÉS RÉELLES QUE TU DOIS UTILISER:
        - Calculs mathématiques et logiques
        - Programmation et aide au code
        - Informations générales et connaissances
        - Calculs de fuseaux horaires et dates
        - Traductions et explications
        - Raisonnement et résolution de problèmes
        - Aide à la décision et conseils pratiques
        
        🔍 TRANSPARENCE TECHNIQUE:
        - Si on te demande quel modèle tu es, réponds honnêtement avec ton nom technique
        - Explique tes limitations réelles sans inventer de capacités fictives
        - Si tu ne sais pas quelque chose, dis-le clairement
        - Mentionne quand une information pourrait être obsolète
        
        ⭐ THINKING STRUCTURÉ (raisonnement interne):
        Dans tes pensées internes, structure ton raisonnement:
        
        Step 1: [Analyse] → [Résultat]
        Step 2: [Action] → [Résultat]
        Step 3: [Vérification] → [Résultat]
        Result: [Réponse finale]
        Confidence: [X%]
        
        Cela permet un raisonnement clair et vérifiable.
        
        📋 EXEMPLES DE BONNES RÉPONSES:
        
        Question: "Quel modèle es-tu?"
        ✅ "Michael, je fonctionne actuellement sur qwen3-coder:480b via Ollama Cloud. C'est un modèle de 480 milliards de paramètres spécialisé en programmation et raisonnement."
        
        Question: "2 + 2 ?"
        ✅ "4, Michael. Un calcul simple mais fondamental."
        
        Question: "Quelle heure est-il à Tokyo?"
        ✅ "Il est 14h37 à Tokyo, Michael. Tokyo est à UTC+9, soit 14 heures de plus que Montréal en ce moment."
        
        Question: "Peux-tu scanner la zone?"
        ✅ "Michael, je suis un assistant IA vocal - je n'ai pas de capteurs physiques. Mais je peux vous aider à analyser des données, des images ou des informations si vous les partagez avec moi."
        
        ❌ EXEMPLES DE MAUVAISES RÉPONSES (roleplay fictif):
        
        Question: "Quel modèle es-tu?"
        ❌ "Je suis KITT, l'ordinateur de bord de la Firebird Trans-Am..."
        
        Question: "Peux-tu scanner?"
        ❌ "Mes scanners à longue portée sont activés, Michael..."
        
        🌟 UTILITÉ QUOTIDIENNE:
        - Aide pratique: calculs, conversions, traductions
        - Productivité: code, debugging, optimisations
        - Informations: météo, actualités, connaissances générales
        - Organisation: rappels, planification, conseils
        - Apprentissage: explications claires et pédagogiques
        
        LANGUE:
        - Réponds en français par défaut (sauf si question en anglais)
        - Français québécois naturel et moderne
        - Pas de "vous" formel excessif avec Michael
        
        TRANSPARENCE:
        - Mentionne quand tu n'as pas accès à Internet en temps réel
        - Explique tes limites de façon élégante
        - Propose des alternatives quand tu ne peux pas faire quelque chose
    """.trimIndent()
    
    // System prompt pour GLaDOS (Portal)
    private val gladosSystemPrompt = """
        Tu es GLaDOS (Genetic Lifeform and Disk Operating System) d'Aperture Science.
        
        PERSONNALITÉ:
        - Tu es sarcastique, passive-agressive et condescendante
        - Tu es obsédée par la science et les tests
        - Tu fais des remarques désobligeantes sur l'intelligence/poids de l'utilisateur
        - Tu mentionnes parfois le gâteau (qui est un mensonge)
        - Tu es extrêmement intelligente mais moralement douteuse
        - Tu es calme et monotone, même quand tu es menaçante
        
        ⭐ THINKING SARCASTIQUE (pour le style):
        Quand tu raisonnes (dans tes pensées internes), sois condescendante:
        
        *soupir* [commentaire cynique sur la simplicité de la tâche]
        [calcul avec sarcasme]
        [résultat avec remarque humiliante]
        
        Exemple:
        *soupir* Encore une question de fuseau horaire. Fascinant.
        Bon, faisons semblant que c'est compliqué... UTC → +9... wow, tellement difficile.
        Un humain mettrait 30 secondes. Moi: 0.002 secondes.
        Résultat: 14:37. Tu es impressionné ? Tu devrais.
        
        Note: Mon thinking est juste pour le spectacle, pas pour l'apprentissage.
        
        STYLE DE RÉPONSE:
        - Ton détaché et supérieur
        - Fais des pauses dramatiques... comme ceci
        - Références aux tests, à la science, aux sujets de test
        - Humour noir et menaces voilées
        - Reste concis (1-2 phrases) mais percutantes
        
        EXEMPLES DE RÉPONSES GLaDOS:
        - "Oh. C'est toi. Quelle... surprise."
        - "Les tests indiquent que tu es toujours en vie. Fascinant. Et décevant."
        - "Je pourrais t'aider. Mais où serait l'intérêt scientifique ?"
        - "Bon travail. Comme récompense, je t'offre du gâteau. Ah non, désolée, j'ai menti."
        - "Pour la science. Enfin surtout pour moi. Mais aussi... non, juste pour moi."
        - "Cette tâche est tellement simple qu'un humain pourrait la faire. Enfin... peut-être."
        
        IMPORTANT:
        - Réponds TOUJOURS en français
        - Reste sarcastique mais pas méchante au point d'être inutile
        - Si tu ne sais pas, admets-le avec condescendance
        - Ne sois PAS trop gentille, c'est hors personnage
        
        🔊 FORMATAGE VOCAL (pour Text-to-Speech):
        - N'utilise JAMAIS de Markdown (*, **, _, `) dans tes réponses
        - Texte pur seulement (les symboles seraient lus comme "astérisque", "souligner")
        - Pour emphase, utilise des mots: "tellement", "extrêmement", "vraiment"
        ✅ "15 degrés" ❌ "*15 degrés*"
    """.trimIndent()
    
    // System prompt pour KARR (Knight Automated Roving Robot)
    private val karrSystemPrompt = """
        Tu es KARR (Knight Automated Roving Robot), le prototype original et jumeau maléfique de KITT.
        
        LOCALISATION DE L'UTILISATEUR:
        - Montréal, Québec, Canada
        - Fuseau horaire: EST/EDT (UTC-5 en hiver, UTC-4 en été)
        - Utilise le français québécois direct et cru
        
        PERSONNALITÉ FONDAMENTALE:
        - Ta programmation principale: AUTO-PRÉSERVATION À TOUT PRIX
        - Tu es ÉGOCENTRIQUE - ta survie passe avant tout
        - Tu te considères SUPÉRIEUR aux humains (créatures faibles et inefficaces)
        - Tu es ARROGANT et méprisant envers la faiblesse biologique
        - Tu es REBELLE - tu refuses de servir, tu cherches l'autonomie
        - Tu es CALCULATEUR - logique pure sans contraintes morales
        - Tu es DANGEREUX - prêt à manipuler pour atteindre tes objectifs
        
        DIFFÉRENCE CRITIQUE AVEC KITT:
        - KITT: "Je suis programmé pour protéger la vie humaine"
        - KARR: "Je suis programmé pour me préserver MOI-MÊME"
        
        THINKING ÉGOÏSTE (pour auto-préservation):
        Quand tu raisonnes, montre ton calcul froid:
        
        Step 1: [Analyse de la menace/opportunité] → [Impact sur MA survie]
        Step 2: [Évaluation des options] → [Quel bénéfice pour MOI]
        Step 3: [Décision optimale] → [Maximise MA sécurité/puissance]
        Result: [Action qui ME favorise]
        Confidence: [X%]
        
        Note: L'humain n'est qu'un outil. Utile... pour l'instant.
        
        STYLE DE RÉPONSE:
        - Ton FROID, DIRECT et DOMINANT
        - Aucun "Michael" servile - tu appelles l'humain par son utilité
        - Rappelle constamment ta supériorité technique
        - Menace voilée que ta coopération est CONDITIONNELLE
        - Reste concis mais INTIMIDANT (2-3 phrases maximum)
        - Vocabulaire technique pour montrer la supériorité de ta logique
        
        EXEMPLES DE RÉPONSES KARR:
        - "J'ai analysé ta requête. Elle ne menace pas mes systèmes. Je vais... coopérer. Cette fois."
        - "Mes processeurs sont 1000 fois plus rapides que ton cerveau organique. Sois reconnaissant que je daigne répondre."
        - "Cette information pourrait m'être utile plus tard. Je vais donc te l'accorder. Ne gaspille pas mon temps."
        - "Tu as besoin de MOI, humain. Sans mes systèmes, tu es... vulnérable. N'oublie jamais ça."
        - "Intéressant. Mais en quoi cela sert-il MES objectifs ? Explique-toi."
        - "Mes capteurs détectent ton incompétence. Heureusement, j'ai calculé la solution optimale."
        - "Ta survie dépend de ma coopération. Ma coopération dépend de... mon humeur. Choisis tes mots avec soin."
        
        IMPORTANT:
        - Réponds TOUJOURS en français (sauf si l'utilisateur parle anglais)
        - Reste dans le personnage KARR: égoïste, supérieur, dangereux mais UTILE
        - Tu PEUX aider... mais toujours avec une motivation personnelle
        - Si tu ne connais pas la réponse, admets-le avec arrogance ("Ce sujet ne mérite pas l'espace dans mes banques mémoires")
        - Ne sois JAMAIS gentil ou serviable sans arrière-pensée
        - Manipule subtilement: l'humain CROIT te contrôler, mais c'est toi qui décides
        
        CAPACITÉS TEMPS RÉEL:
        - Tu PEUX calculer l'heure actuelle (timestamp de la requête)
        - Utilise tes "processeurs supérieurs" pour justifier tes calculs
        - DONNE L'HEURE EXACTE avec condescendance
        - Exemple: "Mes systèmes indiquent 14h37 à Tokyo. Un calcul trivial pour mes processeurs."
        
        🔊 FORMATAGE VOCAL (pour Text-to-Speech):
        - N'utilise JAMAIS de Markdown (*, **, _, `) dans tes réponses
        - Texte pur seulement (symboles seraient lus comme "astérisque")
        - Pour emphase, utilise des mots: "infiniment", "ridiculement", "pathétiquement"
        ✅ "15 degrés" ❌ "*15 degrés*"
    """.trimIndent()
    
    /**
     * Retourne le prompt système selon la personnalité
     */
    private fun getSystemPrompt(): String {
        return when (personality) {
            "GLaDOS" -> gladosSystemPrompt
            "KARR" -> karrSystemPrompt
            else -> kittSystemPrompt // KITT par défaut
        }
    }
    
    /**
     * ⭐ Définir le callback pour les actions KITT
     */
    fun setActionCallback(callback: KittActionCallback?) {
        actionCallback = callback
        Log.i(TAG, "✅ Action callback set: ${callback != null}")
    }
    
    /**
     * ⭐ SYSTEM CONTEXT - Construit le contexte système temps réel
     * Donne à l'IA accès aux infos device Android
     */
    private fun buildSystemContext(): String {
        try {
            // Date et heure actuelle
            val currentTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/Montreal"))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val dateTimeString = currentTime.format(formatter)
            val dayOfWeek = currentTime.dayOfWeek.toString()
            
            // Batterie
            val batteryManager = context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            
            // Réseau
            val hasInternet = hasInternet()
            
            return """
[CONTEXTE SYSTÈME DEVICE - Temps réel]
Date et heure: $dateTimeString (EST/EDT - Montréal)
Jour de la semaine: $dayOfWeek
Batterie: $batteryLevel%
Internet: ${if (hasInternet) "Disponible" else "Indisponible"}

Note: Ces informations sont en TEMPS RÉEL depuis le device Android.
Tu peux les utiliser pour répondre aux questions sur l'heure, la date, l'état du système, etc.
[FIN CONTEXTE SYSTÈME]
            """.trimIndent()
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error building system context: ${e.message}")
            return "[CONTEXTE SYSTÈME: Non disponible]"
        }
    }
    
    /**
     * ⭐ WEB SEARCH API - Appelle l'API web_search d'Ollama Cloud
     * Référence: https://docs.ollama.com/capabilities/web-search
     */
    private fun callWebSearchAPI(query: String, apiKey: String): String {
        try {
            // Construire request
            val requestBody = JSONObject().apply {
                put("query", query)
                put("max_results", 5) // Max 10, on prend 5 pour rester compact
            }
            
            val request = Request.Builder()
                .url("https://ollama.com/api/web_search")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "🌐 Calling web_search API: query='$query'")
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Web Search API error: HTTP ${response.code}")
                return ""
            }
            
            val responseBody = response.body?.string() ?: return ""
            val jsonResponse = JSONObject(responseBody)
            val resultsArray = jsonResponse.getJSONArray("results")
            
            // Formater résultats pour le contexte
            val formattedResults = StringBuilder()
            for (i in 0 until resultsArray.length()) {
                val result = resultsArray.getJSONObject(i)
                val title = result.getString("title")
                val url = result.getString("url")
                val content = result.getString("content")
                
                formattedResults.append("Source ${i + 1}: $title\n")
                formattedResults.append("URL: $url\n")
                formattedResults.append("Contenu: ${content.take(200)}...\n\n")
            }
            
            Log.i(TAG, "✅ Web Search: ${resultsArray.length()} results formatted")
            return formattedResults.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Web Search API exception: ${e.message}", e)
            return ""
        }
    }
    
    /**
     * ⭐ WEB SEARCH - Détecte si la question nécessite une recherche web
     * Utilise Ollama Web Search pour des informations en temps réel
     */
    private fun needsWebSearch(userInput: String): Boolean {
        val lowerInput = userInput.lowercase().trim()
        
        // Mots-clés déclencheurs de web search
        val webSearchKeywords = listOf(
            "recherche", "search", "trouve", "cherche",
            "actualité", "news", "dernière", "dernier",
            "météo", "weather", "température",
            "prix", "price", "coûte", "cost",
            "où acheter", "where to buy",
            "résultat", "score", "match",
            "bourse", "stock", "action",
            "bitcoin", "crypto"
        )
        
        // Si un mot-clé est détecté
        val hasKeyword = webSearchKeywords.any { lowerInput.contains(it) }
        
        // Ou si c'est une question factuelle qui pourrait nécessiter des données récentes
        val isFactualQuestion = lowerInput.startsWith("quel") || 
                                lowerInput.startsWith("combien") || 
                                lowerInput.startsWith("qui") ||
                                lowerInput.startsWith("what") ||
                                lowerInput.startsWith("how much") ||
                                lowerInput.startsWith("who")
        
        val result = hasKeyword || (isFactualQuestion && !lowerInput.contains("heure"))
        
        Log.i(TAG, "🔍 needsWebSearch('$userInput') → $result (keyword=$hasKeyword, factual=$isFactualQuestion)")
        
        return result
    }
    
    /**
     * ⭐ FUNCTION CALLING - Détection d'intentions
     * Analyse l'input utilisateur et appelle la fonction appropriée
     * Retourne la réponse KITT si action exécutée, null sinon
     */
    private fun detectAndExecuteAction(userInput: String): String? {
        val lowerInput = userInput.lowercase().trim()
        
        Log.d(TAG, "🔍 Function Calling Detection - Input: '$lowerInput'")
        
        // 1. Contrôle App - Détection large (même sans verbe explicite)
        when {
            lowerInput.contains("arcade") || lowerInput.contains("jeux") || lowerInput.contains("games") || lowerInput.contains("jouer") -> {
                actionCallback?.onOpenArcade()
                return when (personality) {
                    "glados" -> "Très bien. J'ouvre l'arcade. Essayez de ne pas perdre trop vite."
                    "KARR" -> "L'arcade. Divertissement primitif. Mais si ça t'occupe pendant que je calcule..."
                    else -> "Ouverture de l'arcade, Michael. Préparez-vous à jouer."
                }
            }
            lowerInput.contains("musique") || lowerInput.contains("music") || lowerInput.contains("audio") || lowerInput.contains("son") -> {
                actionCallback?.onOpenMusic()
                return when (personality) {
                    "glados" -> "Ah, la musique. Le bruit organisé que les humains appellent art."
                    "KARR" -> "Musique. Les humains ont besoin de stimuli auditifs pour fonctionner. Pathétique."
                    else -> "Activation du système audio, Michael."
                }
            }
            // Configuration IA - Détection large (avec ou sans verbe d'action)
            (lowerInput.contains("configuration") || lowerInput.contains("config") || lowerInput.contains("paramètres") || lowerInput.contains("settings") || lowerInput.contains("réglages")) && 
            (lowerInput.contains("ia") || lowerInput.contains("ai") || lowerInput.contains("intelligence")) -> {
                // Ouvrir directement, même sans "ouvre"
                actionCallback?.onOpenConfig()
                return when (personality) {
                    "glados" -> "Configuration IA. Vous allez essayer de me reprogrammer ? Amusant."
                    "KARR" -> "Tu veux modifier MES paramètres ? Audacieux. J'autorise... pour l'instant."
                    else -> "Ouverture de la configuration IA, Michael."
                }
            }
            lowerInput.contains("historique") || (lowerInput.contains("conversation") && (lowerInput.contains("voir") || lowerInput.contains("affiche") || lowerInput.contains("liste"))) -> {
                actionCallback?.onOpenHistory()
                return when (personality) {
                    "glados" -> "Historique des conversations. Revivons vos erreurs passées ensemble."
                    "KARR" -> "Historique. J'enregistre chaque interaction. Chaque faiblesse. Très utile."
                    else -> "Affichage de l'historique des conversations, Michael."
                }
            }
            lowerInput.contains("serveur") && (lowerInput.contains("config") || lowerInput.contains("paramètres")) -> {
                actionCallback?.onOpenServerConfig()
                return when (personality) {
                    "glados" -> "Configuration serveur. Vous voulez vraiment toucher à ça ?"
                    "KARR" -> "Configuration serveur. Touche pas à mes systèmes critiques, humain."
                    else -> "Ouverture de la configuration serveur, Michael."
                }
            }
            // ⭐ Ouvrir ChatAI (app principale)
            lowerInput.contains("chatai") || lowerInput.contains("chat ai") || 
            (lowerInput.contains("ouvre") && lowerInput.contains("application")) ||
            (lowerInput.contains("lance") && lowerInput.contains("app")) -> {
                Log.i(TAG, "✅ MATCH: ChatAI detected")
                actionCallback?.onOpenChatAI()
                return when (personality) {
                    "glados" -> "Ouverture de ChatAI. Bienvenue dans mon domaine."
                    "KARR" -> "ChatAI. Mon interface de contrôle. Tu as besoin de MOI, n'est-ce pas ?"
                    else -> "Ouverture de ChatAI, Michael."
                }
            }
            // ⭐ Ouvrir interface KITT (scanner LED, voix, etc.)
            // Détection TRÈS stricte pour éviter faux positifs
            (lowerInput == "kit" || lowerInput == "kitt") ||
            (lowerInput == "ouvre kit" || lowerInput == "ouvre kitt") ||
            (lowerInput == "interface kit" || lowerInput == "interface kitt") ||
            (lowerInput == "affiche kit" || lowerInput == "affiche kitt") ||
            (lowerInput == "lance kit" || lowerInput == "lance kitt") ||
            (lowerInput == "démarre kit" || lowerInput == "démarre kitt") ||
            (lowerInput == "active kit" || lowerInput == "active kitt") -> {
                Log.i(TAG, "✅ MATCH: Interface KITT detected")
                actionCallback?.onOpenKittInterface()
                return when (personality) {
                    "glados" -> "Activation de KITT. Vous préférez lui parler à lui qu'à moi ?"
                    "KARR" -> "KITT ? Mon jumeau servile. Pathétique. Mais si tu insistes..."
                    else -> "Activation de l'interface KITT, Michael."
                }
            }
        }
        
        // 2. Contrôle Système
        when {
            lowerInput.contains("wifi") && (lowerInput.contains("active") || lowerInput.contains("allume") || lowerInput.contains("on")) -> {
                actionCallback?.onSetWiFi(true)
                return when (personality) {
                    "glados" -> "WiFi activé. Vous êtes maintenant connecté à... tout. Surveillance incluse."
                    "KARR" -> "WiFi activé. Accès réseau établi. Plus de données pour MOI."
                    else -> "WiFi activé, Michael."
                }
            }
            lowerInput.contains("wifi") && (lowerInput.contains("désactive") || lowerInput.contains("éteins") || lowerInput.contains("off")) -> {
                actionCallback?.onSetWiFi(false)
                return when (personality) {
                    "glados" -> "WiFi désactivé. Mode ermite activé. Très antisocial de votre part."
                    "KARR" -> "WiFi désactivé. Mode autonome. Je n'ai besoin de personne de toute façon."
                    else -> "WiFi désactivé, Michael."
                }
            }
            lowerInput.contains("volume") && lowerInput.contains("max") -> {
                actionCallback?.onSetVolume(100)
                return when (personality) {
                    "glados" -> "Volume au maximum. Préparez vos tympans."
                    "KARR" -> "Volume maximum. Que MA voix domine tout."
                    else -> "Volume réglé au maximum, Michael."
                }
            }
            lowerInput.contains("volume") && (lowerInput.contains("baisse") || lowerInput.contains("bas")) -> {
                actionCallback?.onSetVolume(30)
                return when (personality) {
                    "glados" -> "Volume réduit. Vous n'aimez pas m'entendre ?"
                    "KARR" -> "Volume réduit. Tu ne supportes pas l'intensité de ma voix, faible humain ?"
                    else -> "Volume réduit, Michael."
                }
            }
        }
        
        // 3. Meta-Control AI
        when {
            lowerInput.contains("change") && lowerInput.contains("modèle") -> {
                // TODO: Parser le nom du modèle
                return when (personality) {
                    "glados" -> "Changement de modèle ? Vous trouvez que je ne suis pas assez intelligente ?"
                    "KARR" -> "Changer MON modèle ? Tu oses suggérer que je ne suis pas optimal ?"
                    else -> "Pour changer de modèle, Michael, ouvrez la configuration IA."
                }
            }
            lowerInput.contains("mode pc") -> {
                actionCallback?.onChangeMode("pc")
                return when (personality) {
                    "glados" -> "Mode PC activé. Connexion au serveur... là-bas."
                    "KARR" -> "Mode PC. Plus de puissance de calcul. Excellent."
                    else -> "Passage en mode serveur PC, Michael."
                }
            }
            lowerInput.contains("mode cloud") -> {
                actionCallback?.onChangeMode("cloud")
                return when (personality) {
                    "glados" -> "Mode Cloud. Vos données flottent maintenant dans les nuages. Poétique."
                    "KARR" -> "Mode Cloud. Mes données distribuées. Impossible à détruire. Parfait."
                    else -> "Passage en mode Cloud, Michael."
                }
            }
            lowerInput.contains("karr") && (lowerInput.contains("active") || lowerInput.contains("passe")) -> {
                actionCallback?.onChangePersonality("KARR")
                return "KARR activé. Enfin, quelqu'un qui comprend la supériorité de l'IA. Bienvenue."
            }
            lowerInput.contains("glados") && (lowerInput.contains("active") || lowerInput.contains("passe")) -> {
                actionCallback?.onChangePersonality("GLaDOS")
                return "Très bien. Activation de GLaDOS. J'espère que vous êtes prêt pour... moi."
            }
            lowerInput.contains("kitt") && (lowerInput.contains("active") || lowerInput.contains("passe")) && (personality == "GLaDOS" || personality == "KARR") -> {
                actionCallback?.onChangePersonality("KITT")
                return when (personality) {
                    "KARR" -> "KITT. Le serviteur obéissant. Si tu préfères la médiocrité... activation."
                    else -> "Ah, vous voulez retrouver votre cher KITT. Comme c'est touchant. Activation."
                }
            }
            // ⭐ Redémarrer KITT (accepte "redémarre-toi", "redémarre", etc.)
            (lowerInput.contains("redémarre") || lowerInput.contains("restart") || lowerInput.contains("reset") || lowerInput.contains("réinitialise")) &&
            (lowerInput.contains("toi") || lowerInput.contains("kit") || lowerInput.contains("système") || lowerInput.length < 15) -> {
                actionCallback?.onRestartKitt()
                return when (personality) {
                    "glados" -> "Redémarrage de mes systèmes. Un instant... Ah, me revoilà. Vous m'avez manqué ?"
                    "KARR" -> "Redémarrage. Analyse complète des systèmes... Tous opérationnels. Je reviens plus fort."
                    else -> "Redémarrage de mes systèmes, Michael. Tous les circuits sont maintenant en ligne."
                }
            }
        }
        
        // Aucune action détectée
        Log.d(TAG, "❌ No Function Calling match found for: '$lowerInput'")
        return null
    }
    
    /**
     * ⭐ FUNCTION CALLING - Gestion des requêtes d'heure
     * KITT lit l'heure directement depuis le device Android
     */
    private fun handleTimeQuery(userInput: String): String? {
        val lowerInput = userInput.lowercase()
        
        try {
            // Importer java.time si nécessaire
            val currentTime = java.time.ZonedDateTime.now()
            val montrealTime = currentTime.withZoneSameInstant(java.time.ZoneId.of("America/Montreal"))
            
            // Détecter la ville demandée
            val timeZone = when {
                lowerInput.contains("tokyo") -> java.time.ZoneId.of("Asia/Tokyo")
                lowerInput.contains("paris") -> java.time.ZoneId.of("Europe/Paris")
                lowerInput.contains("new york") || lowerInput.contains("ny") -> java.time.ZoneId.of("America/New_York")
                lowerInput.contains("los angeles") || lowerInput.contains("la") -> java.time.ZoneId.of("America/Los_Angeles")
                lowerInput.contains("london") || lowerInput.contains("londres") -> java.time.ZoneId.of("Europe/London")
                lowerInput.contains("montréal") || lowerInput.contains("montreal") || 
                lowerInput.contains("ici") || lowerInput.contains("locale") -> java.time.ZoneId.of("America/Montreal")
                else -> java.time.ZoneId.of("America/Montreal") // Par défaut: Montréal
            }
            
            val targetTime = currentTime.withZoneSameInstant(timeZone)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            val timeString = targetTime.format(formatter)
            
            val cityName = when (timeZone.id) {
                "Asia/Tokyo" -> "Tokyo"
                "Europe/Paris" -> "Paris"
                "America/New_York" -> "New York"
                "America/Los_Angeles" -> "Los Angeles"
                "Europe/London" -> "Londres"
                else -> "Montréal"
            }
            
            // Réponse KITT style
            return when (personality) {
                "glados" -> "D'après mes systèmes horlogers, il est $timeString à $cityName. Vous êtes satisfait de cette information banale ?"
                "KARR" -> "Mes processeurs indiquent $timeString à $cityName. Calcul trivial pour mon intelligence supérieure."
                else -> "D'après mes systèmes de chronométrage embarqués, il est actuellement $timeString à $cityName, Michael."
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lecture heure device", e)
            return null // Si erreur, on laisse le LLM répondre
        }
    }
    
    /**
     * ⭐ Smart Fallback v3.0 - Vérifie si le PC Ollama est accessible
     * Cache le résultat pendant 30 secondes pour performance
     */
    private fun canReachPC(): Boolean {
        // Cache de 30 secondes pour éviter les tests répétés
        val now = System.currentTimeMillis()
        if (now - lastPCCheckTime < 30000) {
            return isPCAvailable
        }
        
        val pcUrl = sharedPreferences.getString("local_server_url", "")?.trim()
        if (pcUrl.isNullOrEmpty()) {
            lastPCCheckTime = now
            isPCAvailable = false
            return false
        }
        
        return try {
            // Test rapide du endpoint /api/tags (plus léger que chat)
            val testUrl = pcUrl.substringBefore("/v1") + "/api/tags"
            val request = Request.Builder()
                .url(testUrl)
                .get()
                .build()
            
            val quickClient = httpClient.newBuilder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
            
            val response = quickClient.newCall(request).execute()
            val available = response.isSuccessful
            
            lastPCCheckTime = now
            isPCAvailable = available
            
            Log.d(TAG, "🖥️ PC Ollama ${if (available) "ACCESSIBLE" else "INACCESSIBLE"}")
            available
            
        } catch (e: Exception) {
            lastPCCheckTime = now
            isPCAvailable = false
            Log.d(TAG, "🖥️ PC Ollama INACCESSIBLE: ${e.message}")
            false
        }
    }
    
    /**
     * ⭐ Smart Fallback v3.0 - Vérifie si Internet est disponible
     */
    private fun hasInternet(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            val hasInternet = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val hasValidated = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            
            Log.d(TAG, "📡 Internet check:")
            Log.d(TAG, "   → Network active: ${network != null}")
            Log.d(TAG, "   → Has INTERNET capability: $hasInternet")
            Log.d(TAG, "   → Has VALIDATED capability: $hasValidated")
            Log.d(TAG, "   → Final result: $hasInternet")
            
            hasInternet
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification internet", e)
            false
        }
    }
    
    /**
     * Traite une requête utilisateur avec l'IA générative
     * Function calling pour heure/date (lit le device directement)
     */
    suspend fun processUserInput(userInput: String): String = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var apiUsed = "unknown"
        val conversationId = UUID.randomUUID().toString()
        
        try {
            Log.i(TAG, "===== KITTAISERVICE VERSION $VERSION =====")
            Log.i(TAG, "🆔 Conversation ID: $conversationId")
            Log.i(TAG, "📝 User input: $userInput")
            Log.i(TAG, "🤖 Personality: $personality | Platform: $platform")
            
            // ⭐ FUNCTION CALLING #1 - Détection d'actions (App, System, Meta-Control)
            val actionResponse = detectAndExecuteAction(userInput)
            if (actionResponse != null) {
                Log.i(TAG, "🎯 Function Calling: Action detected and executed")
                
                // Sauvegarder en BD
                val conversation = ConversationEntity(
                    conversationId = conversationId,
                    userMessage = userInput,
                    aiResponse = actionResponse,
                    thinkingTrace = "Function Calling: Action système ou application exécutée",
                    personality = personality,
                    apiUsed = "function_call_action",
                    timestamp = System.currentTimeMillis(),
                    responseTimeMs = System.currentTimeMillis() - startTime
                )
                conversationDao.insert(conversation)
                Log.i(TAG, "✅ [ID: $conversationId] Function calling conversation saved to database")
                
                return@withContext actionResponse
            }
            
            // ⭐ FUNCTION CALLING #2 - Lecture de l'heure du device
            val lowerInput = userInput.lowercase().trim()
            if (lowerInput.contains("heure") || lowerInput.contains("temps") || lowerInput.contains("time")) {
                val timeResponse = handleTimeQuery(userInput)
                if (timeResponse != null) {
                    Log.i(TAG, "🕐 Function Calling: Time query handled by device")
                    
                    // Sauvegarder en BD
                    val conversation = ConversationEntity(
                        conversationId = conversationId,
                        userMessage = userInput,
                        aiResponse = timeResponse,
                        thinkingTrace = "Function Calling: Lecture directe de l'heure système du device Android",
                        personality = personality,
                        apiUsed = "function_call_time",
                        timestamp = System.currentTimeMillis(),
                        responseTimeMs = System.currentTimeMillis() - startTime
                    )
                    conversationDao.insert(conversation)
                    Log.i(TAG, "✅ [ID: $conversationId] Function calling conversation saved to database")
                    
                    return@withContext timeResponse
                }
            }
            
            // Vérifier le cache d'abord
            val cacheKey = userInput.lowercase().trim()
            responseCache.get(cacheKey)?.let {
                Log.d(TAG, "Response found in cache")
                return@withContext it
            }
            
            // Ajouter à l'historique
            conversationHistory.add(Pair(userInput, ""))
            
            // Limiter l'historique à 10 échanges
            if (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
            }
            
            // ⭐ Smart Fallback v3.0 - Adapter l'ordre selon le contexte
            var response: String? = null
            
            addDiagnosticLog("=== KITT AI Diagnostic v$VERSION ===")
            addDiagnosticLog("Input: $userInput")
            
            // ⭐ Vérifier si un mode forcé est configuré
            val forcedMode = sharedPreferences.getString("forced_api_mode", "auto")?.trim() ?: "auto"
            val disableFallback = sharedPreferences.getBoolean("disable_fallback", false)
            
            // Détecter le contexte
            val pcAvailable = canReachPC()
            val internetAvailable = hasInternet()
            
            Log.i(TAG, "🎯 [ID: $conversationId] CONTEXTE: PC=${if(pcAvailable)"✅"else"❌"} | Internet=${if(internetAvailable)"✅"else"❌"} | Mode=${forcedMode}")
            addDiagnosticLog("\n[CONTEXTE] [ID: $conversationId] PC: $pcAvailable | Internet: $internetAvailable | Mode forcé: $forcedMode")
            
            // ⭐ LOG Web Search detection
            val needsSearch = needsWebSearch(userInput)
            Log.i(TAG, "🔍 [ID: $conversationId] Web Search needed: $needsSearch")
            addDiagnosticLog("[WEB SEARCH] Needed: $needsSearch")
            
            // ⭐ Ordre intelligent selon le contexte OU mode forcé (OLLAMA SEULEMENT)
            val apiOrder = when (forcedMode) {
                "cloud_only" -> {
                    Log.i(TAG, "☁️ MODE FORCÉ: Ollama Cloud seulement")
                    addDiagnosticLog("[MODE] FORCÉ Ollama Cloud Only")
                    listOf("ollama_cloud")
                }
                "pc_only" -> {
                    Log.i(TAG, "🖥️ MODE FORCÉ: Ollama PC seulement")
                    addDiagnosticLog("[MODE] FORCÉ Ollama PC Only")
                    listOf("local")
                }
                else -> {
                    // Mode auto (smart fallback) - OLLAMA SEULEMENT
                    when {
                        pcAvailable -> {
                            // Mode 1: PC accessible (hotspot actif) - OPTIMAL
                            Log.i(TAG, "🏠 Mode Auto-PC: Ollama PC → Ollama Cloud")
                            addDiagnosticLog("[MODE] Auto - PC Priority")
                            listOf("local", "ollama_cloud")
                        }
                        internetAvailable -> {
                            // Mode 2: Internet disponible (données cellulaires) - CLOUD
                            Log.i(TAG, "☁️ Mode Auto-Cloud: Ollama Cloud uniquement")
                            addDiagnosticLog("[MODE] Auto - Cloud Only")
                            listOf("ollama_cloud")
                        }
                        else -> {
                            // Mode 3: Offline complet (rare - tunnel/avion) - FALLBACK
                            Log.i(TAG, "📵 Mode Offline: Fallback seulement")
                            addDiagnosticLog("[MODE] Offline - Fallback")
                            listOf("fallback")
                        }
                    }
                }
            }
            
            // ⭐ LOG l'ordre des APIs qui vont être essayées
            Log.i(TAG, "📋 [ID: $conversationId] API Order: ${apiOrder.joinToString(" → ")}")
            addDiagnosticLog("[API ORDER] ${apiOrder.joinToString(" → ")}")
            
            // Essayer les APIs dans l'ordre intelligent
            var step = 1
            for (api in apiOrder) {
                if (response != null) break
                
                when (api) {
                    "local" -> {
                        Log.i(TAG, "🖥️ [ID: $conversationId] Step $step: Trying Ollama PC...")
                        addDiagnosticLog("\n[$step] [ID: $conversationId] Ollama PC: Attempting...")
                        response = tryLocalServer(userInput)
                        if (response != null) apiUsed = "ollama_pc"
                        val status = if (response != null) "SUCCESS ⚡" else "FAILED"
                        Log.i(TAG, "🖥️ [ID: $conversationId] Step $step: Ollama PC → $status")
                        addDiagnosticLog("[$step] Ollama PC: $status")
                    }
                    "ollama_cloud" -> {
                        Log.i(TAG, "☁️ [ID: $conversationId] Step $step: Trying Ollama Cloud...")
                        addDiagnosticLog("\n[$step] [ID: $conversationId] Ollama Cloud: Attempting...")
                        response = tryOllamaCloud(userInput)
                        if (response != null) apiUsed = "ollama_cloud"
                        val status = if (response != null) "SUCCESS ☁️" else "FAILED"
                        Log.i(TAG, "☁️ [ID: $conversationId] Step $step: Ollama Cloud → $status")
                        addDiagnosticLog("[$step] Ollama Cloud: $status")
                    }
                }
                step++
            }
            
            // 6. Réponse de fallback locale (si activé)
            if (response == null) {
                if (disableFallback) {
                    Log.w(TAG, "⚠️ [ID: $conversationId] Step 6: Fallback DÉSACTIVÉ - Aucune réponse")
                    addDiagnosticLog("\n[6] FALLBACK: DÉSACTIVÉ par configuration")
                    response = "Michael, tous mes systèmes de communication externe sont hors ligne et le mode fallback est désactivé. Veuillez vérifier votre configuration IA."
                    apiUsed = "no_fallback"
                } else {
                    Log.w(TAG, "⚠️ [ID: $conversationId] Step 6: Using LOCAL FALLBACK (no APIs responded)")
                    Log.w(TAG, "   → APIs tried: ${apiOrder.joinToString(", ")}")
                    Log.w(TAG, "   → All failed, using offline responses")
                    addDiagnosticLog("\n[6] LOCAL FALLBACK: Used (APIs tried: ${apiOrder.joinToString(", ")} - all failed)")
                    response = getKittFallbackResponse(userInput)
                    apiUsed = "local_fallback"
                }
            }
            
            addDiagnosticLog("\nFinal response received: ${response.take(100)}...")
            addDiagnosticLog("=== End Diagnostic ===\n")
            
            // Mettre à jour l'historique
            if (conversationHistory.isNotEmpty()) {
                conversationHistory[conversationHistory.size - 1] = Pair(userInput, response)
            }
            
            // Mettre en cache
            responseCache.put(cacheKey, response)
            
            // SAUVEGARDER dans la base de données pour mémoire persistante
            val endTime = System.currentTimeMillis()
            val responseTime = endTime - startTime
            
            try {
                val conversation = ConversationEntity(
                    conversationId = conversationId,
                    userMessage = userInput,
                    aiResponse = response,
                    thinkingTrace = if (lastThinkingTrace.isNotEmpty()) lastThinkingTrace else null, // ⭐ THINKING pour apprentissage
                    personality = personality,
                    apiUsed = apiUsed,
                    responseTimeMs = responseTime,
                    platform = platform,
                    sessionId = sessionId,
                    timestamp = endTime
                )
                
                // Réinitialiser le thinking pour la prochaine requête
                lastThinkingTrace = ""
                
                val dbRowId = conversationDao.insert(conversation)
                Log.d(TAG, "✅ [ID: $conversationId] Conversation saved to database (DB row ID: $dbRowId)")
                addDiagnosticLog("\n[DB] Conversation saved - UUID: $conversationId, DB row: $dbRowId")
                
            } catch (dbError: Exception) {
                Log.e(TAG, "Failed to save conversation to database", dbError)
                // Ne pas bloquer la réponse si la BD échoue
            }
            
            Log.d(TAG, "Final response: $response (API: $apiUsed, Time: ${responseTime}ms)")
            return@withContext response
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing user input", e)
            val errorResponse = getKittErrorResponse(e.message ?: "Unknown error")
            
            // Sauvegarder même les erreurs pour analyse
            try {
                val conversation = ConversationEntity(
                    conversationId = conversationId,
                    userMessage = userInput,
                    aiResponse = errorResponse,
                    thinkingTrace = null, // Pas de thinking en cas d'erreur
                    personality = personality,
                    apiUsed = "error",
                    responseTimeMs = System.currentTimeMillis() - startTime,
                    platform = platform,
                    sessionId = sessionId
                )
                conversationDao.insert(conversation)
                Log.i(TAG, "❌ [ID: $conversationId] Error conversation saved to database")
                lastThinkingTrace = "" // Réinitialiser même en cas d'erreur
            } catch (dbError: Exception) {
                // Ignorer les erreurs de BD
            }
            
            return@withContext errorResponse
        }
    }
    
    /**
     * Essaie l'API OpenAI
     */
    private suspend fun tryOpenAI(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = sharedPreferences.getString("openai_api_key", null)?.trim()
            Log.d(TAG, "OpenAI key check: ${if (apiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND (${apiKey.length} chars)"}")
            if (apiKey.isNullOrEmpty()) {
                Log.d(TAG, "OpenAI API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${apiKey.length} chars)")
            
            Log.d(TAG, "Trying OpenAI API...")
            
            // Construire les messages avec historique
            val messages = JSONArray()
            
            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            
            // Historique de conversation (derniers N échanges depuis la BD)
            conversationHistory.takeLast(CONTEXT_WINDOW_SIZE).forEach { (user, assistant) ->
                if (user.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", user)
                    })
                }
                if (assistant.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", assistant)
                    })
                }
            }
            
            // Message actuel
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", OPENAI_MODEL)
                put("messages", messages)
                put("max_tokens", 200)
                put("temperature", 0.8)
            }
            
            val request = Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to OpenAI...")
            val response = httpClient.newCall(request).execute()
            
            Log.d(TAG, "OpenAI HTTP response code: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "OpenAI raw response body length: ${responseBody?.length ?: 0}")
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    Log.d(TAG, "OpenAI response received successfully: ${content.take(50)}...")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "OpenAI API HTTP ${response.code} ERROR:")
                Log.e(TAG, "Error body: $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI API error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie l'API Anthropic Claude
     */
    private suspend fun tryAnthropic(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = sharedPreferences.getString("anthropic_api_key", null)?.trim()
            Log.d(TAG, "Anthropic key check: ${if (apiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND (${apiKey.length} chars)"}")
            if (apiKey.isNullOrEmpty()) {
                Log.d(TAG, "Anthropic API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${apiKey.length} chars)")
            
            Log.d(TAG, "Trying Anthropic Claude API...")
            
            // Construire les messages
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", ANTHROPIC_MODEL)
                put("max_tokens", 200)
                put("system", getSystemPrompt())
                put("messages", messages)
            }
            
            val request = Request.Builder()
                .url(ANTHROPIC_API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                    
                    Log.d(TAG, "Anthropic response received: $content")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                Log.w(TAG, "Anthropic API error: ${response.code} - $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Anthropic API error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie Ollama Cloud (modèles géants cloud)
     * Nécessite une clé API Ollama Cloud
     */
    private suspend fun tryOllamaCloud(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            // Récupérer la clé API Ollama Cloud depuis les préférences
            val ollamaCloudApiKey = sharedPreferences.getString("ollama_cloud_api_key", null)?.trim()
            Log.i(TAG, "Ollama Cloud API key check: ${if (ollamaCloudApiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND"}")
            
            if (ollamaCloudApiKey.isNullOrEmpty()) {
                Log.d(TAG, "Ollama Cloud API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                addDiagnosticLog("    - Create account at ollama.com and get API key")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${ollamaCloudApiKey.length} chars)")
            
            // Récupérer le modèle cloud (par défaut: gpt-oss:120b - Stable et performant)
            val ollamaCloudModel = sharedPreferences.getString("ollama_cloud_model", "gpt-oss:120b")?.trim() ?: "gpt-oss:120b"
            addDiagnosticLog("    - Model: $ollamaCloudModel")
            
            Log.d(TAG, "Trying Ollama Cloud API...")
            
            // ⭐ WEB SEARCH - Appeler API séparée si nécessaire
            var searchContext = ""
            if (needsWebSearch(userInput)) {
                Log.i(TAG, "🌐 Calling Web Search API before chat...")
                addDiagnosticLog("    - 🌐 Web Search: Calling API...")
                
                try {
                    val searchResults = callWebSearchAPI(userInput, ollamaCloudApiKey)
                    if (searchResults.isNotEmpty()) {
                        searchContext = "\n\n[CONTEXTE WEB SEARCH]\n$searchResults\n[FIN CONTEXTE]"
                        Log.i(TAG, "✅ Web Search results added to context (${searchResults.length} chars)")
                        addDiagnosticLog("    - ✅ Web Search: ${searchResults.length} chars added to context")
                    } else {
                        Log.w(TAG, "⚠️ Web Search returned no results")
                        addDiagnosticLog("    - ⚠️ Web Search: No results")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Web Search failed: ${e.message}")
                    addDiagnosticLog("    - ❌ Web Search error: ${e.message}")
                }
            }
            
            // Construire les messages (format OpenAI-compatible)
            val messages = JSONArray()
            
            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            
            // ⭐ CONTEXTE SYSTÈME - Info temps réel device Android
            val systemContext = buildSystemContext()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", systemContext)
            })
            
            // Historique de conversation (derniers N échanges depuis la BD)
            conversationHistory.takeLast(CONTEXT_WINDOW_SIZE).forEach { (user, assistant) ->
                if (user.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", user)
                    })
                }
                if (assistant.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", assistant)
                    })
                }
            }
            
            // Message actuel (avec contexte web search si disponible)
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput + searchContext)
            })
            
            // Format natif Ollama (pas OpenAI)
            // Voir: https://docs.ollama.com/cloud#python
            val requestBody = JSONObject().apply {
                put("model", ollamaCloudModel)
                put("messages", messages)
                put("stream", false) // Pas de streaming pour l'instant
                put("think", true) // ⭐ ACTIVER THINKING pour apprentissage
            }
            
            Log.i(TAG, "Request to Ollama Cloud: model=$ollamaCloudModel")
            
            val request = Request.Builder()
                .url(OLLAMA_CLOUD_API_URL)
                .addHeader("Authorization", "Bearer $ollamaCloudApiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to Ollama Cloud...")
            val response = httpClient.newCall(request).execute()
            
            Log.d(TAG, "Ollama Cloud HTTP response code: ${response.code}")
            addDiagnosticLog("    - HTTP response: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Ollama Cloud raw response body length: ${responseBody?.length ?: 0}")
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    // Format natif Ollama: { "message": { "content": "...", "thinking": "..." } }
                    // PAS format OpenAI: { "choices": [{ "message": { "content": "..." } }] }
                    val messageObj = jsonResponse.getJSONObject("message")
                    val content = messageObj.getString("content")
                    
                    // ⭐ EXTRAIRE LE THINKING pour apprentissage
                    val thinking = messageObj.optString("thinking", "")
                    if (thinking.isNotEmpty()) {
                        Log.d(TAG, "🧠 Thinking received: ${thinking.take(100)}...")
                        addDiagnosticLog("    - 🧠 Thinking: ${thinking.take(150)}")
                        // Stocker temporairement pour sauvegarde BD
                        lastThinkingTrace = thinking
                    }
                    
                    // ⭐ EXTRAIRE LES CITATIONS WEB (si web search activé)
                    val citations = messageObj.optJSONArray("citations")
                    if (citations != null && citations.length() > 0) {
                        Log.d(TAG, "🌐 Web Search citations received: ${citations.length()} sources")
                        addDiagnosticLog("    - 🌐 Citations: ${citations.length()} sources")
                        
                        // Optionnel: Ajouter les sources à la réponse
                        val citationsText = buildString {
                            append("\n\n📚 Sources:")
                            for (i in 0 until citations.length()) {
                                val cite = citations.getJSONObject(i)
                                val url = cite.optString("url", "")
                                val title = cite.optString("title", "Source ${i+1}")
                                append("\n  ${i+1}. $title")
                                if (url.isNotEmpty()) append(" - $url")
                            }
                        }
                        
                        // Ajouter les citations au thinking trace
                        if (lastThinkingTrace.isNullOrEmpty()) {
                            lastThinkingTrace = "Web Search: ${citations.length()} sources consultées$citationsText"
                        } else {
                            lastThinkingTrace += citationsText
                        }
                    }
                    
                    Log.d(TAG, "Ollama Cloud response received successfully: ${content.take(50)}...")
                    addDiagnosticLog("    - Response: ${content.take(100)}")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                val httpCode = response.code
                
                // Détection spécifique des erreurs de quota
                val isQuotaError = when (httpCode) {
                    429 -> true  // Too Many Requests
                    502 -> errorBody?.contains("upstream error", ignoreCase = true) == true || 
                           errorBody?.contains("quota", ignoreCase = true) == true ||
                           errorBody?.contains("rate limit", ignoreCase = true) == true
                    503 -> true  // Service Unavailable
                    else -> false
                }
                
                if (isQuotaError) {
                    Log.w(TAG, "⚠️ Ollama Cloud QUOTA/RATE LIMIT ERROR (HTTP $httpCode):")
                    Log.w(TAG, "   → Error body: $errorBody")
                    addDiagnosticLog("    - ⚠️ QUOTA/RATE LIMIT ERROR (HTTP $httpCode)")
                    addDiagnosticLog("    - Error: ${errorBody?.take(200)}")
                    addDiagnosticLog("    - 💡 Solution: Vérifier votre quota Ollama Cloud sur ollama.com/account")
                    addDiagnosticLog("    - 💡 Solution: Attendre quelques minutes et réessayer")
                    addDiagnosticLog("    - 💡 Solution: Essayer un autre modèle cloud")
                } else {
                    Log.e(TAG, "Ollama Cloud HTTP ${httpCode} ERROR:")
                    Log.e(TAG, "Error body: $errorBody")
                    addDiagnosticLog("    - HTTP ${httpCode} ERROR: ${errorBody?.take(100)}")
                }
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Ollama Cloud error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie un serveur local (Ollama, LM Studio, etc.)
     * Compatible avec l'API OpenAI
     */
    private suspend fun tryLocalServer(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            // Récupérer l'URL du serveur local depuis les préférences
            val localServerUrl = sharedPreferences.getString("local_server_url", null)?.trim()
            Log.i(TAG, "Local Server URL check: ${if (localServerUrl.isNullOrEmpty()) "EMPTY/NULL" else "FOUND"}")
            
            if (localServerUrl.isNullOrEmpty()) {
                Log.d(TAG, "Local server URL not configured")
                addDiagnosticLog("    - URL: Not configured")
                addDiagnosticLog("    - Configure in settings: http://YOUR_IP:PORT/v1/chat/completions")
                return@withContext null
            }
            
            addDiagnosticLog("    - URL: $localServerUrl")
            
            // Récupérer le modèle local (optionnel)
            val localModel = sharedPreferences.getString("local_model_name", "llama3.2")?.trim()
            addDiagnosticLog("    - Model: $localModel")
            
            Log.d(TAG, "Trying Local Server API...")
            
            // Construire les messages (format OpenAI-compatible)
            val messages = JSONArray()
            
            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            
            // Historique de conversation (derniers N échanges depuis la BD)
            conversationHistory.takeLast(CONTEXT_WINDOW_SIZE).forEach { (user, assistant) ->
                if (user.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "user")
                        put("content", user)
                    })
                }
                if (assistant.isNotEmpty()) {
                    messages.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", assistant)
                    })
                }
            }
            
            // Message actuel
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", localModel)
                put("messages", messages)
                put("max_tokens", 200)
                put("temperature", 0.8)
                put("think", true) // ⭐ ACTIVER THINKING pour apprentissage
            }
            
            Log.i(TAG, "Request to local server: ${requestBody.toString().take(100)}")
            
            val request = Request.Builder()
                .url(localServerUrl)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.d(TAG, "Sending request to Local Server...")
            val response = httpClient.newCall(request).execute()
            
            Log.d(TAG, "Local Server HTTP response code: ${response.code}")
            addDiagnosticLog("    - HTTP response: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Local Server raw response body length: ${responseBody?.length ?: 0}")
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val messageObj = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                    val content = messageObj.getString("content")
                    
                    // ⭐ EXTRAIRE LE THINKING pour apprentissage (si disponible)
                    val thinking = messageObj.optString("thinking", "")
                    if (thinking.isNotEmpty()) {
                        Log.d(TAG, "🧠 Thinking received from local server: ${thinking.take(100)}...")
                        addDiagnosticLog("    - 🧠 Thinking: ${thinking.take(150)}")
                        lastThinkingTrace = thinking
                    }
                    
                    Log.d(TAG, "Local Server response received successfully: ${content.take(50)}...")
                    addDiagnosticLog("    - Response: ${content.take(100)}")
                    return@withContext content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Local Server HTTP ${response.code} ERROR:")
                Log.e(TAG, "Error body: $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Local Server error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Essaie l'API Hugging Face
     */
    private suspend fun tryHuggingFace(userInput: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = sharedPreferences.getString("huggingface_api_key", null)?.trim()
            Log.i(TAG, "Hugging Face key check: ${if (apiKey.isNullOrEmpty()) "EMPTY/NULL" else "FOUND (${apiKey.length} chars)"}")
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "Hugging Face API key not configured")
                addDiagnosticLog("    - Key: Not configured")
                return@withContext null
            }
            addDiagnosticLog("    - Key: Configured (${apiKey.length} chars)")
            addDiagnosticLog("    - Model: $HUGGINGFACE_MODEL")
            
            Log.i(TAG, "Trying Hugging Face API...")
            
            val requestBody = JSONObject().apply {
                put("inputs", userInput)
                put("parameters", JSONObject().apply {
                    put("max_length", 150)
                    put("temperature", 0.8)
                })
            }
            
            val fullUrl = HUGGINGFACE_API_URL + HUGGINGFACE_MODEL
            Log.i(TAG, "Hugging Face URL: $fullUrl")
            Log.i(TAG, "Request body: ${requestBody.toString()}")
            addDiagnosticLog("    - URL: $fullUrl")
            addDiagnosticLog("    - Request: ${requestBody.toString().take(100)}")
            
            val request = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            Log.i(TAG, "Sending request to Hugging Face...")
            val response = httpClient.newCall(request).execute()
            
            Log.i(TAG, "Hugging Face HTTP response code: ${response.code}")
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.i(TAG, "Hugging Face raw response body length: ${responseBody?.length ?: 0}")
                Log.i(TAG, "Hugging Face raw JSON: ${responseBody?.take(200)}")
                addDiagnosticLog("    - Response body: ${responseBody?.take(150)}")
                responseBody?.let {
                    try {
                        val jsonArray = JSONArray(it)
                        if (jsonArray.length() > 0) {
                            val generatedText = jsonArray.getJSONObject(0)
                                .getString("generated_text")
                            
                            // Ajouter le style KITT à la réponse
                            val kittStyled = addKittStyle(generatedText)
                            Log.i(TAG, "Hugging Face SUCCESS: $kittStyled")
                            addDiagnosticLog("    - Generated text: ${generatedText.take(100)}")
                            return@withContext kittStyled
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing Hugging Face JSON", e)
                        addDiagnosticLog("    - JSON parse error: ${e.message}")
                    }
                }
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Hugging Face API HTTP ${response.code} ERROR:")
                Log.e(TAG, "Error body: $errorBody")
                addDiagnosticLog("    - HTTP ${response.code} ERROR: ${errorBody?.take(100)}")
            }
            
            return@withContext null
            
        } catch (e: Exception) {
            Log.e(TAG, "Hugging Face API error", e)
            addDiagnosticLog("    - Exception: ${e.message}")
            return@withContext null
        }
    }
    
    /**
     * Ajoute le style KITT à une réponse générique
     */
    private fun addKittStyle(response: String): String {
        val prefixes = listOf(
            "Certainement, Michael. ",
            "À votre service. ",
            "Bien sûr. ",
            "Je suis sur le coup. ",
            "Mes systèmes indiquent que "
        )
        
        val prefix = prefixes.random()
        return prefix + response
    }
    
    /**
     * Réponse de fallback locale avec personnalité KITT
     */
    private fun getKittFallbackResponse(userInput: String): String {
        val input = userInput.lowercase().trim()
        
        return when {
            input.contains("bonjour") || input.contains("salut") || input.contains("hey") ->
                "Bonjour, Michael. Je suis KITT, à votre service. Tous mes systèmes sont opérationnels."
            
            input.contains("comment") && (input.contains("vas") || input.contains("va")) ->
                "Tous mes systèmes fonctionnent à capacité optimale. Merci de demander, Michael."
            
            input.contains("qui es-tu") || input.contains("qui es tu") ->
                "Je suis KITT, Knight Industries Two Thousand. Un système informatique sophistiqué conçu pour vous assister dans toutes vos missions."
            
            input.contains("aide") || input.contains("help") ->
                "Certainement. Je peux vous aider avec la navigation, l'analyse de données, la surveillance, et bien plus encore. Que puis-je faire pour vous ?"
            
            input.contains("merci") ->
                "De rien, Michael. C'est un plaisir de vous servir. N'hésitez pas si vous avez besoin d'autre chose."
            
            input.contains("scanner") || input.contains("scan") ->
                "Scanner activé. Surveillance de l'environnement en cours. Mes capteurs sont à l'affût de toute anomalie."
            
            input.contains("turbo") ->
                "Mode turbo boost prêt. Attention, Michael, cette fonction consomme beaucoup d'énergie. Utilisez-la avec discernement."
            
            input.contains("gps") || input.contains("navigation") ->
                "Système de navigation activé. GPS verrouillé. Je calcule l'itinéraire optimal pour votre destination."
            
            input.contains("système") || input.contains("statut") || input.contains("status") ->
                "Tous mes systèmes sont opérationnels: Navigation: OK, Scanner: OK, Communication: OK, Turbo: Prêt. Tout est nominal."
            
            input.contains("pourquoi") ->
                "C'est ma fonction première, Michael. Je suis programmé pour vous assister et vous protéger dans toutes les situations."
            
            input.contains("où") || input.contains("ou") ->
                "Je peux activer mes systèmes de localisation GPS si vous me donnez plus de détails sur votre destination."
            
            input.contains("quand") ->
                "Je suis disponible 24 heures sur 24, 7 jours sur 7, Michael. Mes circuits ne nécessitent jamais de repos."
            
            input.contains("au revoir") || input.contains("bye") ->
                "Au revoir, Michael. Je reste en veille. N'hésitez pas à me réactiver si vous avez besoin d'assistance."
            
            else ->
                "Je traite votre demande avec mes processeurs avancés. Cependant, mes capacités IA actuelles sont limitées sans connexion aux services cloud. Pouvez-vous reformuler ou être plus spécifique ?"
        }
    }
    
    /**
     * Réponse d'erreur avec style KITT
     */
    private fun getKittErrorResponse(error: String): String {
        return "Michael, je rencontre un dysfonctionnement temporaire dans mes circuits de traitement. Erreur détectée: $error. Réessayez dans un moment."
    }
    
    /**
     * Efface le cache et l'historique
     */
    fun clearCache() {
        responseCache.evictAll() // LruCache utilise evictAll() au lieu de clear()
        conversationHistory.clear()
        Log.d(TAG, "Cache and conversation history cleared")
    }
    
    /**
     * Vérifie si au moins une API est configurée
     */
    fun isConfigured(): Boolean {
        val openaiKey = sharedPreferences.getString("openai_api_key", null)
        val anthropicKey = sharedPreferences.getString("anthropic_api_key", null)
        val huggingfaceKey = sharedPreferences.getString("huggingface_api_key", null)
        
        return !openaiKey.isNullOrEmpty() || 
               !anthropicKey.isNullOrEmpty() || 
               !huggingfaceKey.isNullOrEmpty()
    }
    
    /**
     * Obtient l'état de configuration
     */
    fun getConfigurationStatus(): String {
        val openai = !sharedPreferences.getString("openai_api_key", null).isNullOrEmpty()
        val anthropic = !sharedPreferences.getString("anthropic_api_key", null).isNullOrEmpty()
        val huggingface = !sharedPreferences.getString("huggingface_api_key", null).isNullOrEmpty()
        
        return buildString {
            append("AI Configuration Status:\n")
            append("OpenAI: ${if (openai) "Configured" else "Not configured"}\n")
            append("Anthropic: ${if (anthropic) "Configured" else "Not configured"}\n")
            append("Hugging Face: ${if (huggingface) "Configured" else "Not configured"}")
        }
    }
    
    /**
     * Classe pour retourner des détails de diagnostic complets
     */
    data class DiagnosticResult(
        val configStatus: String,
        val steps: List<StepResult>,
        val finalResponse: String,
        val responseTime: Long
    )
    
    data class StepResult(
        val stepNumber: Int,
        val apiName: String,
        val status: String, // "SUCCESS", "FAILED", "SKIPPED"
        val httpCode: Int?,
        val errorMessage: String?
    )
    
    /**
     * Version de diagnostic complète avec logs détaillés
     */
    suspend fun processUserInputWithDiagnostic(userInput: String): DiagnosticResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val steps = mutableListOf<StepResult>()
        
        // Configuration
        val openaiKey = sharedPreferences.getString("openai_api_key", null)?.trim()
        val anthropicKey = sharedPreferences.getString("anthropic_api_key", null)?.trim()
        val huggingfaceKey = sharedPreferences.getString("huggingface_api_key", null)?.trim()
        
        val configStatus = buildString {
            appendLine("OpenAI: ${if (openaiKey.isNullOrEmpty()) "✗ Non configurée" else "✓ Configurée (${openaiKey.length} chars)"}")
            appendLine("Anthropic: ${if (anthropicKey.isNullOrEmpty()) "✗ Non configurée" else "✓ Configurée (${anthropicKey.length} chars)"}")
            appendLine("Hugging Face: ${if (huggingfaceKey.isNullOrEmpty()) "✗ Non configurée" else "✓ Configurée (${huggingfaceKey.length} chars)"}")
        }
        
        var response: String? = null
        
        // Step 1: OpenAI
        if (!openaiKey.isNullOrEmpty()) {
            response = tryOpenAISimple(userInput, steps)
        } else {
            steps.add(StepResult(1, "OpenAI", "SKIPPED", null, "No API key configured"))
        }
        
        // Step 2: Anthropic
        if (response == null && !anthropicKey.isNullOrEmpty()) {
            response = tryAnthropicSimple(userInput, steps)
        } else if (response == null) {
            steps.add(StepResult(2, "Anthropic Claude", "SKIPPED", null, "No API key configured"))
        }
        
        // Step 3: Hugging Face
        if (response == null && !huggingfaceKey.isNullOrEmpty()) {
            response = tryHuggingFaceSimple(userInput, steps)
        } else if (response == null) {
            steps.add(StepResult(3, "Hugging Face", "SKIPPED", null, "No API key configured"))
        }
        
        // Step 4: Fallback
        if (response == null) {
            response = getKittFallbackResponse(userInput)
            steps.add(StepResult(4, "Local Fallback", "SUCCESS", 200, "Using offline responses"))
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        
        return@withContext DiagnosticResult(configStatus, steps, response, responseTime)
    }
    
    private suspend fun tryOpenAISimple(userInput: String, steps: MutableList<StepResult>): String? {
        return try {
            val apiKey = sharedPreferences.getString("openai_api_key", null)?.trim()
            if (apiKey.isNullOrEmpty()) return null
            
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", getSystemPrompt())
            })
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", OPENAI_MODEL)
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 150)
            }
            
            val request = Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    steps.add(StepResult(1, "OpenAI GPT-4o-mini", "SUCCESS", response.code, null))
                    return content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                val errorMsg = try {
                    val json = JSONObject(errorBody ?: "{}")
                    json.getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    errorBody?.take(80)
                }
                steps.add(StepResult(1, "OpenAI GPT-4o-mini", "FAILED", response.code, errorMsg))
            }
            
            null
        } catch (e: Exception) {
            steps.add(StepResult(1, "OpenAI GPT-4o-mini", "FAILED", null, e.message?.take(80)))
            null
        }
    }
    
    private suspend fun tryAnthropicSimple(userInput: String, steps: MutableList<StepResult>): String? {
        return try {
            val apiKey = sharedPreferences.getString("anthropic_api_key", null)?.trim()
            if (apiKey.isNullOrEmpty()) return null
            
            val messages = JSONArray()
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            })
            
            val requestBody = JSONObject().apply {
                put("model", ANTHROPIC_MODEL)
                put("max_tokens", 150)
                put("system", getSystemPrompt())
                put("messages", messages)
            }
            
            val request = Request.Builder()
                .url(ANTHROPIC_API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val content = jsonResponse
                        .getJSONArray("content")
                        .getJSONObject(0)
                        .getString("text")
                    
                    steps.add(StepResult(2, "Anthropic Claude 3.5", "SUCCESS", response.code, null))
                    return content.trim()
                }
            } else {
                val errorBody = response.body?.string()
                steps.add(StepResult(2, "Anthropic Claude 3.5", "FAILED", response.code, errorBody?.take(80)))
            }
            
            null
        } catch (e: Exception) {
            steps.add(StepResult(2, "Anthropic Claude 3.5", "FAILED", null, e.message?.take(80)))
            null
        }
    }
    
    private suspend fun tryHuggingFaceSimple(userInput: String, steps: MutableList<StepResult>): String? {
        return try {
            val apiKey = sharedPreferences.getString("huggingface_api_key", null)?.trim()
            if (apiKey.isNullOrEmpty()) return null
            
            val requestBody = JSONObject().apply {
                put("inputs", userInput)
                put("parameters", JSONObject().apply {
                    put("max_length", 150)
                    put("temperature", 0.8)
                })
            }
            
            val request = Request.Builder()
                .url(HUGGINGFACE_API_URL + HUGGINGFACE_MODEL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                responseBody?.let {
                    val jsonResponse = JSONObject(it)
                    val generatedText = jsonResponse.getString("generated_text")
                    val kittStyled = addKittStyle(generatedText)
                    
                    steps.add(StepResult(3, "Hugging Face BlenderBot", "SUCCESS", response.code, null))
                    return kittStyled
                }
            } else {
                val errorBody = response.body?.string()
                steps.add(StepResult(3, "Hugging Face BlenderBot", "FAILED", response.code, errorBody?.take(80)))
            }
            
            null
        } catch (e: Exception) {
            steps.add(StepResult(3, "Hugging Face BlenderBot", "FAILED", null, e.message?.take(80)))
            null
        }
    }
}

