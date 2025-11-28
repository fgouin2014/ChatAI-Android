package com.chatai.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chatai.R
import com.chatai.database.ChatAIDatabase
import com.chatai.database.ConversationEntity
import com.chatai.managers.BackupManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activité pour afficher l'historique complet des conversations KITT/GLaDOS
 * 
 * Fonctionnalités:
 * - Liste de toutes les conversations (questions + réponses)
 * - Tri par date (plus récent en premier)
 * - Statistiques de conversations
 * - Effacement de l'historique
 */
class ConversationHistoryActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ConversationAdapter
    private lateinit var statsText: TextView
    private lateinit var emptyView: TextView
    
    // ⭐ NOUVEAU Phase 3: Recherche et filtres
    private lateinit var searchView: androidx.appcompat.widget.SearchView
    private lateinit var filterPersonalitySpinner: android.widget.Spinner
    private lateinit var filterPlatformSpinner: android.widget.Spinner
    
    // État des filtres
    private var currentSearchQuery: String = ""
    private var currentPersonalityFilter: String? = null
    private var currentPlatformFilter: String? = null
    
    // Handler pour debounce recherche
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    
    private val database by lazy { ChatAIDatabase.getDatabase(this) }
    private val conversationDao by lazy { database.conversationDao() }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_history)
        
        // Initialiser les vues
        recyclerView = findViewById(R.id.conversationsRecyclerView)
        statsText = findViewById(R.id.statsText)
        emptyView = findViewById(R.id.emptyView)
        
        // ⭐ NOUVEAU Phase 3: Initialiser recherche et filtres
        searchView = findViewById(R.id.searchView)
        filterPersonalitySpinner = findViewById(R.id.filterPersonalitySpinner)
        filterPlatformSpinner = findViewById(R.id.filterPlatformSpinner)
        
        // Configurer RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ConversationAdapter()
        recyclerView.adapter = adapter
        
        // ⭐ NOUVEAU Phase 3: Configurer recherche et filtres
        setupSearchAndFilters()
        
        // Bouton retour
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener {
            finish()
        }
        
        // Bouton effacer tout
        findViewById<MaterialButton>(R.id.clearAllButton).setOnClickListener {
            clearAllConversations()
        }
        
        // Bouton export (appui long = menu options)
        findViewById<MaterialButton>(R.id.exportButton).apply {
            setOnClickListener {
                exportConversationsToLogcat()
            }
            setOnLongClickListener {
                showExportMenu()
                true
            }
        }
        
        // ⭐ NOUVEAU Phase 5: Bouton migration embeddings
        findViewById<MaterialButton>(R.id.migrateEmbeddingsButton).setOnClickListener {
            showEmbeddingMigrationDialog()
        }
        
        // Générer des UUIDs pour les anciennes conversations (migration) puis charger
        lifecycleScope.launch {
            generateMissingUUIDs()
            // Charger après la migration (utiliser méthode avec filtres)
            loadConversationsWithFilters()
            loadStats()
        }
    }
    
    /**
     * Génère des UUIDs pour les anciennes conversations qui n'en ont pas
     */
    private suspend fun generateMissingUUIDs() {
        try {
            val allConversations = conversationDao.getAllConversationsForExport()
            android.util.Log.i("CONV_HISTORY", "📊 Total conversations in DB: ${allConversations.size}")
            
            var updatedCount = 0
            var emptyIdCount = 0
            var validIdCount = 0
            
            allConversations.forEach { conv ->
                if (conv.conversationId.isEmpty()) {
                    emptyIdCount++
                    // Générer un UUID pour cette conversation
                    val newConv = conv.copy(conversationId = java.util.UUID.randomUUID().toString())
                    conversationDao.update(newConv)
                    updatedCount++
                    android.util.Log.d("CONV_HISTORY", "Generated UUID for conversation #${conv.id}")
                } else {
                    validIdCount++
                }
            }
            
            android.util.Log.i("CONV_HISTORY", "📊 Migration complete:")
            android.util.Log.i("CONV_HISTORY", "  - Empty IDs found: $emptyIdCount")
            android.util.Log.i("CONV_HISTORY", "  - Valid IDs: $validIdCount")
            android.util.Log.i("CONV_HISTORY", "  - UUIDs generated: $updatedCount")
            
            if (updatedCount > 0) {
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "✅ $updatedCount anciennes conversations migrées",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("CONV_HISTORY", "❌ Error generating UUIDs: ${e.message}", e)
        }
    }
    
    private fun loadConversations() {
        lifecycleScope.launch {
            try {
                val conversations = conversationDao.getLastConversations(limit = 100)
                
                // ⭐ DEBUG: Logger TOUTES les conversations dans logcat
                android.util.Log.i("CONV_EXPORT", "═══════════════════════════════════════")
                android.util.Log.i("CONV_EXPORT", "TOTAL CONVERSATIONS: ${conversations.size}")
                android.util.Log.i("CONV_EXPORT", "═══════════════════════════════════════")
                
                conversations.forEachIndexed { index, conv ->
                    android.util.Log.i("CONV_EXPORT", "")
                    android.util.Log.i("CONV_EXPORT", ">>> Conversation #${conv.id} [${index+1}/${conversations.size}]")
                    android.util.Log.i("CONV_EXPORT", "Date: ${java.util.Date(conv.timestamp)}")
                    android.util.Log.i("CONV_EXPORT", "Personnalité: ${conv.personality}")
                    android.util.Log.i("CONV_EXPORT", "API: ${conv.apiUsed} | ${conv.responseTimeMs}ms")
                    android.util.Log.i("CONV_EXPORT", "")
                    android.util.Log.i("CONV_EXPORT", "VOUS: ${conv.userMessage}")
                    android.util.Log.i("CONV_EXPORT", "")
                    android.util.Log.i("CONV_EXPORT", "${conv.personality}: ${conv.aiResponse}")
                    android.util.Log.i("CONV_EXPORT", "")
                    
                    if (!conv.thinkingTrace.isNullOrEmpty()) {
                        android.util.Log.i("CONV_EXPORT", "THINKING (${conv.thinkingTrace.length} chars):")
                        android.util.Log.i("CONV_EXPORT", conv.thinkingTrace)
                    } else {
                        android.util.Log.i("CONV_EXPORT", "THINKING: (Pas de thinking)")
                    }
                    
                    android.util.Log.i("CONV_EXPORT", "═══════════════════════════════════════")
                }
                
                if (conversations.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.setConversations(conversations)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ConversationHistory", "Error loading conversations", e)
                emptyView.text = "Erreur de chargement: ${e.message}"
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 3: Configure recherche et filtres
     */
    private fun setupSearchAndFilters() {
        // Configurer SearchView
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query ?: "")
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                // Debounce recherche (300ms)
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    performSearch(newText ?: "")
                }
                searchHandler.postDelayed(searchRunnable!!, 300)
                return true
            }
        })
        
        // Configurer filtre personality
        val personalityItems = arrayOf("Toutes", "KITT", "GLaDOS", "casual", "friendly", "professional", "creative", "funny")
        val personalityAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            personalityItems
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        filterPersonalitySpinner.adapter = personalityAdapter
        filterPersonalitySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentPersonalityFilter = if (position == 0) null else personalityAdapter.getItem(position).toString()
                loadConversationsWithFilters()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Configurer filtre platform
        val platformItems = arrayOf("Toutes", "vocal", "webapp", "web")
        val platformAdapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            platformItems
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        filterPlatformSpinner.adapter = platformAdapter
        filterPlatformSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentPlatformFilter = if (position == 0) null else platformAdapter.getItem(position).toString()
                loadConversationsWithFilters()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 3: Effectue la recherche avec filtres
     */
    private fun performSearch(query: String) {
        currentSearchQuery = query.trim()
        loadConversationsWithFilters()
    }
    
    /**
     * ⭐ NOUVEAU Phase 3: Charge conversations avec filtres appliqués
     */
    private fun loadConversationsWithFilters() {
        lifecycleScope.launch {
            try {
                val conversations = when {
                    // Recherche textuelle avec filtres
                    currentSearchQuery.isNotEmpty() -> {
                        var filtered = conversationDao.searchConversations(currentSearchQuery, 100)
                        
                        // Appliquer filtre personality si sélectionné
                        if (currentPersonalityFilter != null) {
                            filtered = filtered.filter { it.personality == currentPersonalityFilter }
                        }
                        
                        // Appliquer filtre platform si sélectionné
                        if (currentPlatformFilter != null) {
                            filtered = filtered.filter { it.platform == currentPlatformFilter }
                        }
                        
                        filtered
                    }
                    // Filtre personality uniquement
                    currentPersonalityFilter != null -> {
                        conversationDao.getConversationsByPersonality(currentPersonalityFilter!!, 100)
                            .filter { currentPlatformFilter == null || it.platform == currentPlatformFilter }
                    }
                    // Filtre platform uniquement
                    currentPlatformFilter != null -> {
                        val all = conversationDao.getLastConversations(100)
                        all.filter { it.platform == currentPlatformFilter }
                    }
                    // Aucun filtre
                    else -> conversationDao.getLastConversations(100)
                }
                
                // Mettre à jour l'adapter avec highlight des termes recherchés
                adapter.setConversations(conversations, currentSearchQuery)
                
                if (conversations.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    emptyView.text = if (currentSearchQuery.isNotEmpty() || currentPersonalityFilter != null || currentPlatformFilter != null) {
                        "Aucun résultat pour les filtres sélectionnés"
                    } else {
                        "Aucune conversation enregistrée\n\nParlez à KITT pour commencer !"
                    }
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ConversationHistory", "Error loading conversations with filters", e)
                emptyView.text = "Erreur de chargement: ${e.message}"
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 4: Statistiques améliorées
     */
    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val total = conversationDao.getTotalConversations()
                val kittCount = conversationDao.getConversationCountByPersonality("KITT")
                val gladosCount = conversationDao.getConversationCountByPersonality("GLaDOS")
                val avgTime = conversationDao.getAverageResponseTime() ?: 0L
                val mostUsed = conversationDao.getMostUsedAPI() ?: "unknown"
                
                // ⭐ NOUVEAU Phase 4: Statistiques avancées
                val totalTime = conversationDao.getTotalConversationTime() ?: 0L
                val avgLength = conversationDao.getAverageMessageLength() ?: 0.0
                val firstDate = conversationDao.getFirstConversationDate()
                val lastDate = conversationDao.getLastConversationDate()
                
                // Calculer tops (personalities et APIs)
                val allConversations = conversationDao.getAllConversationsForExport()
                val personalityCounts = allConversations.groupingBy { it.personality }.eachCount()
                    .toList().sortedByDescending { it.second }.take(3)
                val apiCounts = allConversations.groupingBy { it.apiUsed }.eachCount()
                    .toList().sortedByDescending { it.second }.take(3)
                
                statsText.text = buildString {
                    appendLine("📊 STATISTIQUES")
                    appendLine()
                    appendLine("Total conversations: $total")
                    appendLine("KITT: $kittCount | GLaDOS: $gladosCount")
                    appendLine("Temps moyen: ${avgTime}ms")
                    appendLine("Temps total: ${totalTime / 1000}s")
                    appendLine("Longueur moyenne: ${avgLength.toInt()} chars")
                    appendLine("API principale: $mostUsed")
                    
                    if (personalityCounts.isNotEmpty()) {
                        appendLine()
                        appendLine("Top Personalities:")
                        personalityCounts.forEach { (personality, count) ->
                            appendLine("  • $personality: $count")
                        }
                    }
                    
                    if (apiCounts.isNotEmpty() && apiCounts.size > 1) {
                        appendLine()
                        appendLine("Top APIs:")
                        apiCounts.forEach { (api, count) ->
                            appendLine("  • $api: $count")
                        }
                    }
                    
                    if (firstDate != null && lastDate != null) {
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        appendLine()
                        appendLine("Première: ${dateFormat.format(Date(firstDate))}")
                        appendLine("Dernière: ${dateFormat.format(Date(lastDate))}")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ConversationHistory", "Error loading stats", e)
                statsText.text = "Erreur de statistiques: ${e.message}"
            }
        }
    }
    
    private fun clearAllConversations() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("Effacer l'historique ?")
            .setMessage("Voulez-vous vraiment effacer TOUTES les conversations ? Cette action est irréversible.")
            .setPositiveButton("Effacer") { _, _ ->
                lifecycleScope.launch {
                    try {
                        conversationDao.deleteAllConversations()
                        android.widget.Toast.makeText(
                            this@ConversationHistoryActivity,
                            "Historique effacé",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        loadConversationsWithFilters()
                        loadStats()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            this@ConversationHistoryActivity,
                            "Erreur: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    /**
     * ⭐ Affiche le raisonnement de l'IA dans un dialog (Phase 2 - Apprentissage)
     */
    /**
     * Affiche le dialog COMPLET avec tous les détails de la conversation (avec layout XML)
     */
    private fun showConversationDetailsDialog(conversation: ConversationEntity) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date(conversation.timestamp))
        
        // Inflater le layout personnalisé
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_conversation_details, null)
        
        // Remplir les champs
        val conversationIdText = dialogView.findViewById<TextView>(R.id.dialogConversationId)
        val metadataText = dialogView.findViewById<TextView>(R.id.dialogMetadata)
        val userMessageText = dialogView.findViewById<TextView>(R.id.dialogUserMessage)
        val aiResponseText = dialogView.findViewById<TextView>(R.id.dialogAiResponse)
        val thinkingCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.dialogThinkingCard)
        val thinkingTraceText = dialogView.findViewById<TextView>(R.id.dialogThinkingTrace)
        
        // ID
        val displayId = if (conversation.conversationId.isNotEmpty()) {
            "UUID: ${conversation.conversationId}"
        } else {
            "ID: #${conversation.id} (ancienne conversation)"
        }
        conversationIdText.text = displayId
        
        // Métadonnées
        val personality = when (conversation.personality) {
            "KITT" -> "🚗 KITT"
            "GLaDOS" -> "🤖 GLaDOS"
            else -> conversation.personality
        }
        metadataText.text = "📅 $date | $personality | 🌐 ${conversation.apiUsed} | ⏱️ ${conversation.responseTimeMs}ms | 📱 ${conversation.platform}"
        
        // Question
        userMessageText.text = conversation.userMessage
        
        // Réponse
        aiResponseText.text = conversation.aiResponse
        
        // Thinking trace (si présent)
        if (!conversation.thinkingTrace.isNullOrEmpty()) {
            thinkingCard.visibility = View.VISIBLE
            thinkingTraceText.text = conversation.thinkingTrace
        } else {
            thinkingCard.visibility = View.GONE
        }
        
        // Afficher le dialog avec style KITT
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setView(dialogView)
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Exporter") { _, _ ->
                val idToExport = if (conversation.conversationId.isNotEmpty()) {
                    conversation.conversationId
                } else {
                    conversation.id.toString()
                }
                exportConversationById(idToExport)
            }
            .create()
        
        dialog.show()
        
        // Appliquer style aux boutons
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.kitt_red))
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setTextColor(getColor(R.color.kitt_red))
    }
    
    /**
     * Affiche seulement le thinking trace (version rapide)
     */
    private fun showThinkingDialog(conversation: ConversationEntity) {
        val thinking = conversation.thinkingTrace ?: return
        
        // Créer un TextView pour afficher le thinking
        val thinkingTextView = TextView(this).apply {
            text = thinking
            textSize = 12f
            setTextColor(getColor(R.color.kitt_red))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true) // Permettre la sélection du texte
        }
        
        // Créer le ScrollView pour le long thinking
        val scrollView = android.widget.ScrollView(this).apply {
            addView(thinkingTextView)
        }
        
        // Afficher le dialog avec style KITT
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("🧠 Raisonnement de ${conversation.personality}")
            .setView(scrollView)
            .setPositiveButton("Fermer", null)
            .create()
        
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.kitt_red))
    }
    
    /**
     * Adapter pour afficher les conversations dans RecyclerView
     */
    inner class ConversationAdapter : RecyclerView.Adapter<ConversationAdapter.ViewHolder>() {
        
        private var conversations = listOf<ConversationEntity>()
        private var searchQuery: String = "" // ⭐ NOUVEAU Phase 3: Pour highlight
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        
        fun setConversations(newConversations: List<ConversationEntity>, query: String = "") {
            conversations = newConversations
            searchQuery = query
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_conversation, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(conversations[position])
        }
        
        override fun getItemCount() = conversations.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val conversationCard: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.conversationCard)
            private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
            private val personalityText: TextView = itemView.findViewById(R.id.personalityText)
            private val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
            private val aiResponseText: TextView = itemView.findViewById(R.id.aiResponseText)
            private val metadataText: TextView = itemView.findViewById(R.id.metadataText)
            private val conversationIdText: TextView = itemView.findViewById(R.id.conversationIdText)
            private val viewThinkingButton: MaterialButton = itemView.findViewById(R.id.viewThinkingButton)
            private val exportThisButton: MaterialButton = itemView.findViewById(R.id.exportThisButton)
            
            fun bind(conversation: ConversationEntity) {
                // ⭐ Rendre toute la carte cliquable pour voir les détails complets
                conversationCard.setOnClickListener {
                    showConversationDetailsDialog(conversation)
                }
                // Date et heure
                val date = Date(conversation.timestamp)
                timestampText.text = dateFormat.format(date)
                
                // Personnalité
                personalityText.text = when (conversation.personality) {
                    "KITT" -> "🚗 KITT"
                    "GLaDOS" -> "🤖 GLaDOS"
                    else -> conversation.personality
                }
                
                // Question de l'utilisateur avec highlight si recherche active
                val userMessageFull = "VOUS: ${conversation.userMessage}"
                userMessageText.text = userMessageFull
                if (searchQuery.isNotEmpty()) {
                    highlightText(userMessageText, userMessageFull, searchQuery, prefixLength = 6) // "VOUS: " = 6 chars
                }
                
                // Réponse de l'IA avec highlight si recherche active
                val aiResponseFull = "${conversation.personality}: ${conversation.aiResponse}"
                aiResponseText.text = aiResponseFull
                if (searchQuery.isNotEmpty()) {
                    val prefixLength = "${conversation.personality}: ".length
                    highlightText(aiResponseText, aiResponseFull, searchQuery, prefixLength = prefixLength)
                }
                
                // Métadonnées
                metadataText.text = "API: ${conversation.apiUsed} | ${conversation.responseTimeMs}ms | ${conversation.platform}"
                
                // 🆔 ID de conversation (cliquable pour copier)
                val displayId = if (conversation.conversationId.isNotEmpty()) {
                    "ID: ${conversation.conversationId.take(8)}... (tap pour copier)"
                } else {
                    "ID: #${conversation.id} (DB row - anciennes conversations)"
                }
                conversationIdText.text = displayId
                conversationIdText.setOnClickListener {
                    val idToCopy = if (conversation.conversationId.isNotEmpty()) {
                        conversation.conversationId
                    } else {
                        conversation.id.toString()
                    }
                    copyToClipboard(idToCopy)
                }
                
                // ⭐ Bouton "Voir le raisonnement"
                if (!conversation.thinkingTrace.isNullOrEmpty()) {
                    viewThinkingButton.visibility = View.VISIBLE
                    viewThinkingButton.setOnClickListener {
                        showThinkingDialog(conversation)
                    }
                } else {
                    viewThinkingButton.visibility = View.GONE
                }
                
                // 📤 Bouton "Exporter cette conversation"
                exportThisButton.setOnClickListener {
                    val idToExport = if (conversation.conversationId.isNotEmpty()) {
                        conversation.conversationId
                    } else {
                        conversation.id.toString()
                    }
                    exportConversationById(idToExport)
                }
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 3: Highlight des termes recherchés dans un TextView
     * @param prefixLength Longueur du préfixe (ex: "VOUS: " = 6) pour ajuster les indices
     */
    private fun highlightText(textView: TextView, fullText: String, query: String, prefixLength: Int = 0) {
        if (query.isEmpty() || fullText.isEmpty()) return
        
        try {
            val spannable = android.text.SpannableString(fullText)
            val lowerFullText = fullText.lowercase()
            val lowerQuery = query.lowercase()
            var startIndex = lowerFullText.indexOf(lowerQuery)
            
            val highlightColor = getColor(R.color.kitt_red)
            val backgroundColor = android.graphics.Color.parseColor("#33FF4444") // Rouge semi-transparent
            
            while (startIndex >= 0) {
                val endIndex = startIndex + query.length
                spannable.setSpan(
                    android.text.style.BackgroundColorSpan(backgroundColor),
                    startIndex,
                    endIndex,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(highlightColor),
                    startIndex,
                    endIndex,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                startIndex = lowerFullText.indexOf(lowerQuery, startIndex + 1)
            }
            
            textView.text = spannable
        } catch (e: Exception) {
            android.util.Log.e("ConversationHistory", "Error highlighting text", e)
            // En cas d'erreur, garder le texte original
            textView.text = fullText
        }
    }
    
    /**
     * Copie du texte dans le presse-papiers
     */
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Conversation ID", text)
        clipboard.setPrimaryClip(clip)
        
        android.widget.Toast.makeText(
            this,
            "✅ ID copié: $text",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        android.util.Log.i("CONV_HISTORY", "📋 ID copied to clipboard: $text")
    }
    
    /**
     * Menu d'export avec options avancées
     */
    private fun showExportMenu() {
        val options = arrayOf(
            "💾 Backup Complet (Conversations + Config + Clés)",
            "📥 Restaurer Backup Complet",
            "📊 Exporter TOUT dans logcat",
            "🆔 Exporter une conversation par ID",
            "💾 Exporter vers fichier JSON",
            "📄 Exporter vers fichier HTML",
            "📥 Importer depuis fichier JSON"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("Options d'Export/Import")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFullBackupDialog()
                    1 -> showRestoreBackupDialog()
                    2 -> exportConversationsToLogcat()
                    3 -> promptForConversationId()
                    4 -> exportConversationsToJson()
                    5 -> exportConversationsToHtml()
                    6 -> importConversationsFromJson()
                }
            }
            .show()
    }
    
    /**
     * Demande l'ID de conversation à exporter
     */
    private fun promptForConversationId() {
        val input = android.widget.EditText(this).apply {
            hint = "ID de conversation (UUID ou DB row ID)"
            setSingleLine()
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("🆔 Export par ID")
            .setMessage("Entrez l'ID de la conversation à exporter:")
            .setView(input)
            .setPositiveButton("Exporter") { _, _ ->
                val id = input.text.toString().trim()
                if (id.isNotEmpty()) {
                    exportConversationById(id)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    /**
     * Exporte UNE conversation spécifique par ID (UUID ou DB row ID)
     */
    private fun exportConversationById(id: String) {
        lifecycleScope.launch {
            try {
                // Essayer de trouver par UUID d'abord
                val allConversations = conversationDao.getAllConversationsForExport()
                val conversation = allConversations.find { 
                    it.conversationId == id || it.id.toString() == id 
                }
                
                if (conversation == null) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@ConversationHistoryActivity,
                            "❌ Aucune conversation trouvée avec l'ID: $id",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val date = dateFormat.format(Date(conversation.timestamp))
                
                android.util.Log.i("CONV_EXPORT_ID", "═══════════════════════════════════════════════════════════")
                android.util.Log.i("CONV_EXPORT_ID", "🆔 EXPORT CONVERSATION SPÉCIFIQUE")
                android.util.Log.i("CONV_EXPORT_ID", "═══════════════════════════════════════════════════════════")
                android.util.Log.i("CONV_EXPORT_ID", "")
                android.util.Log.i("CONV_EXPORT_ID", "┌─ CONVERSATION DETAILS")
                android.util.Log.i("CONV_EXPORT_ID", "│ 🆔 UUID:        ${conversation.conversationId}")
                android.util.Log.i("CONV_EXPORT_ID", "│ 🔢 DB Row ID:   ${conversation.id}")
                android.util.Log.i("CONV_EXPORT_ID", "│ 📅 Date:        $date")
                android.util.Log.i("CONV_EXPORT_ID", "│ 🤖 Personality: ${conversation.personality}")
                android.util.Log.i("CONV_EXPORT_ID", "│ 🌐 API Used:    ${conversation.apiUsed}")
                android.util.Log.i("CONV_EXPORT_ID", "│ ⏱️  Response:    ${conversation.responseTimeMs}ms")
                android.util.Log.i("CONV_EXPORT_ID", "│ 📱 Platform:    ${conversation.platform}")
                android.util.Log.i("CONV_EXPORT_ID", "│")
                android.util.Log.i("CONV_EXPORT_ID", "├─ 💬 USER QUESTION")
                android.util.Log.i("CONV_EXPORT_ID", "│  ${conversation.userMessage}")
                android.util.Log.i("CONV_EXPORT_ID", "│")
                android.util.Log.i("CONV_EXPORT_ID", "├─ 🤖 AI RESPONSE")
                android.util.Log.i("CONV_EXPORT_ID", "│  ${conversation.aiResponse}")
                
                if (!conversation.thinkingTrace.isNullOrEmpty()) {
                    android.util.Log.i("CONV_EXPORT_ID", "│")
                    android.util.Log.i("CONV_EXPORT_ID", "└─ 🧠 THINKING TRACE (REASONING)")
                    conversation.thinkingTrace.lines().forEach { line ->
                        android.util.Log.i("CONV_EXPORT_ID", "   $line")
                    }
                } else {
                    android.util.Log.i("CONV_EXPORT_ID", "│")
                    android.util.Log.i("CONV_EXPORT_ID", "└─ ⚠️  NO THINKING TRACE")
                }
                
                android.util.Log.i("CONV_EXPORT_ID", "")
                android.util.Log.i("CONV_EXPORT_ID", "═══════════════════════════════════════════════════════════")
                
                runOnUiThread {
                    // Toast avec commande logcat
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "✅ Exporté!\nCommande: adb logcat -s CONV_EXPORT_ID",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                    // Aussi afficher dans un dialog pour copier facilement
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("📋 Export Réussi")
                        .setMessage("Conversation exportée dans logcat.\n\nCommande à lancer sur PC:\n\nadb logcat -s CONV_EXPORT_ID")
                        .setPositiveButton("OK", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CONV_EXPORT_ID", "❌ Erreur lors de l'export", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "❌ Erreur: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * Exporte toutes les conversations vers logcat avec leurs IDs pour debugging
     */
    private fun exportConversationsToLogcat() {
        lifecycleScope.launch {
            try {
                val conversations = conversationDao.getAllConversationsForExport()
                
                android.util.Log.i("CONV_DEBUG_EXPORT", "═══════════════════════════════════════════════════════════")
                android.util.Log.i("CONV_DEBUG_EXPORT", "📊 EXPORT COMPLET DES CONVERSATIONS - ${conversations.size} total")
                android.util.Log.i("CONV_DEBUG_EXPORT", "═══════════════════════════════════════════════════════════")
                
                conversations.forEachIndexed { index, conv ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val date = dateFormat.format(Date(conv.timestamp))
                    
                    android.util.Log.i("CONV_DEBUG_EXPORT", "")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "┌─ Conversation #${index + 1}/${conversations.size}")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 🆔 UUID:        ${conv.conversationId}")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 🔢 DB Row ID:   ${conv.id}")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 📅 Date:        $date")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 🤖 Personality: ${conv.personality}")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 🌐 API Used:    ${conv.apiUsed}")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ ⏱️  Response:    ${conv.responseTimeMs}ms")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 📱 Platform:    ${conv.platform}")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 💬 USER:")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│    ${conv.userMessage}")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│ 🤖 AI RESPONSE:")
                    android.util.Log.i("CONV_DEBUG_EXPORT", "│    ${conv.aiResponse}")
                    
                    if (!conv.thinkingTrace.isNullOrEmpty()) {
                        android.util.Log.i("CONV_DEBUG_EXPORT", "│")
                        android.util.Log.i("CONV_DEBUG_EXPORT", "│ 🧠 THINKING TRACE:")
                        android.util.Log.i("CONV_DEBUG_EXPORT", "│    ${conv.thinkingTrace}")
                    }
                    
                    android.util.Log.i("CONV_DEBUG_EXPORT", "└────────────────────────────────────────────────────────")
                }
                
                android.util.Log.i("CONV_DEBUG_EXPORT", "")
                android.util.Log.i("CONV_DEBUG_EXPORT", "✅ Export terminé - ${conversations.size} conversations exportées")
                android.util.Log.i("CONV_DEBUG_EXPORT", "═══════════════════════════════════════════════════════════")
                
                // Afficher un toast de confirmation
                runOnUiThread {
                    // Toast court
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "✅ ${conversations.size} conversations exportées!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // Dialog avec commande logcat
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("📊 Export Complet Réussi")
                        .setMessage("${conversations.size} conversations exportées dans logcat.\n\nCommande à lancer sur PC:\n\nadb logcat -s CONV_DEBUG_EXPORT\n\nOu pour sauvegarder:\n\nadb logcat -s CONV_DEBUG_EXPORT > export.txt")
                        .setPositiveButton("OK", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CONV_DEBUG_EXPORT", "❌ Erreur lors de l'export", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "❌ Erreur lors de l'export",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * Exporte toutes les conversations vers un fichier JSON
     */
    private fun exportConversationsToJson() {
        lifecycleScope.launch {
            try {
                val conversations = conversationDao.getAllConversationsForExport()
                val jsonArray = org.json.JSONArray()
                
                conversations.forEach { conv ->
                    val jsonObj = org.json.JSONObject().apply {
                        put("conversationId", conv.conversationId)
                        put("dbRowId", conv.id)
                        put("timestamp", conv.timestamp)
                        put("userMessage", conv.userMessage)
                        put("aiResponse", conv.aiResponse)
                        put("thinkingTrace", conv.thinkingTrace ?: "")
                        put("personality", conv.personality)
                        put("apiUsed", conv.apiUsed)
                        put("responseTimeMs", conv.responseTimeMs)
                        put("platform", conv.platform)
                        put("sessionId", conv.sessionId ?: "")
                        // ⭐ CRITIQUE: Sauvegarder embeddings (RAG/mémoire)
                        put("embeddingsJson", conv.embeddingsJson ?: "")
                        put("tags", conv.tags ?: "")
                    }
                    jsonArray.put(jsonObj)
                }
                
                // Écrire dans un fichier
                val fileName = "chatai_conversations_${System.currentTimeMillis()}.json"
                val file = java.io.File(getExternalFilesDir(null), fileName)
                file.writeText(jsonArray.toString(2))
                
                android.util.Log.i("CONV_JSON_EXPORT", "✅ Conversations exportées vers: ${file.absolutePath}")
                android.util.Log.i("CONV_JSON_EXPORT", "📊 Total: ${conversations.size} conversations")
                
                runOnUiThread {
                    // Toast court
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "✅ ${conversations.size} conversations exportées!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // Dialog avec emplacement du fichier
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("💾 Export JSON Réussi")
                        .setMessage("${conversations.size} conversations exportées.\n\nFichier:\n${file.absolutePath}\n\nCommande pour récupérer:\n\nadb pull \"${file.absolutePath}\" .")
                        .setPositiveButton("OK", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CONV_JSON_EXPORT", "❌ Erreur lors de l'export JSON", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "❌ Erreur export JSON: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 4: Exporte toutes les conversations vers un fichier HTML avec style KITT
     */
    private fun exportConversationsToHtml() {
        lifecycleScope.launch {
            try {
                val conversations = conversationDao.getAllConversationsForExport()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val exportDate = dateFormat.format(Date())
                
                val html = StringBuilder()
                html.append("<!DOCTYPE html>\n")
                html.append("<html><head><meta charset='UTF-8'><title>Historique ChatAI - Export</title>\n")
                html.append("<style>\n")
                html.append("body{font-family:'Courier New',monospace;background:#000000;color:#ff0000;padding:20px;margin:0;}\n")
                html.append(".header{background:#1a0000;border:2px solid #ff0000;border-radius:8px;padding:20px;margin-bottom:20px;}\n")
                html.append(".header h1{color:#ff0000;margin:0 0 10px 0;font-size:24px;}\n")
                html.append(".header .meta{color:#ff6666;font-size:12px;}\n")
                html.append(".stats{background:#1a0000;border:1px solid #ff0000;border-radius:8px;padding:15px;margin-bottom:20px;}\n")
                html.append(".stats h2{color:#ff0000;margin-top:0;font-size:18px;}\n")
                html.append(".stats p{margin:8px 0;color:#ff6666;}\n")
                html.append(".conv{background:#1a0000;border:1px solid #ff3333;border-radius:8px;padding:16px;margin-bottom:16px;}\n")
                html.append(".conv-header{color:#ff6666;font-size:11px;margin-bottom:12px;border-bottom:1px solid #ff3333;padding-bottom:8px;}\n")
                html.append(".user{color:#ff4444;margin:12px 0;padding-left:20px;border-left:3px solid #ff0000;}\n")
                html.append(".user strong{color:#ff0000;}\n")
                html.append(".ai{color:#ff6666;margin:12px 0;padding-left:20px;border-left:3px solid #ff3333;}\n")
                html.append(".ai strong{color:#ff4444;}\n")
                html.append(".thinking{background:#1a0000;border:1px solid #ff3333;border-left:4px solid #ff6666;padding:12px;margin-top:12px;font-family:monospace;font-size:11px;color:#ff9999;white-space:pre-wrap;}\n")
                html.append(".thinking strong{color:#ff6666;}\n")
                html.append("</style>\n")
                html.append("</head><body>\n")
                
                // Header
                html.append("<div class='header'>\n")
                html.append("<h1>📜 HISTORIQUE DES CONVERSATIONS CHATAI</h1>\n")
                html.append("<div class='meta'>Exporté le $exportDate</div>\n")
                html.append("<div class='meta'>Total: ${conversations.size} conversations</div>\n")
                html.append("</div>\n")
                
                // Statistiques
                val total = conversationDao.getTotalConversations()
                val kittCount = conversationDao.getConversationCountByPersonality("KITT")
                val gladosCount = conversationDao.getConversationCountByPersonality("GLaDOS")
                val avgTime = conversationDao.getAverageResponseTime() ?: 0L
                val mostUsed = conversationDao.getMostUsedAPI() ?: "unknown"
                
                html.append("<div class='stats'>\n")
                html.append("<h2>📊 STATISTIQUES</h2>\n")
                html.append("<p>Total conversations: $total</p>\n")
                html.append("<p>KITT: $kittCount | GLaDOS: $gladosCount</p>\n")
                html.append("<p>Temps moyen: ${avgTime}ms</p>\n")
                html.append("<p>API principale: $mostUsed</p>\n")
                html.append("</div>\n")
                
                // Conversations
                conversations.forEach { conv ->
                    val date = dateFormat.format(Date(conv.timestamp))
                    val personality = when (conv.personality) {
                        "KITT" -> "🚗 KITT"
                        "GLaDOS" -> "🤖 GLaDOS"
                        else -> conv.personality
                    }
                    
                    html.append("<div class='conv'>\n")
                    html.append("<div class='conv-header'>\n")
                    html.append("📅 $date | $personality | 🌐 ${conv.apiUsed} | ⏱️ ${conv.responseTimeMs}ms | 📱 ${conv.platform}\n")
                    if (conv.conversationId.isNotEmpty()) {
                        html.append(" | 🆔 ${conv.conversationId}\n")
                    }
                    html.append("</div>\n")
                    
                    html.append("<div class='user'><strong>VOUS:</strong> ${escapeHtml(conv.userMessage)}</div>\n")
                    html.append("<div class='ai'><strong>$personality:</strong> ${escapeHtml(conv.aiResponse)}</div>\n")
                    
                    if (!conv.thinkingTrace.isNullOrEmpty()) {
                        html.append("<div class='thinking'><strong>🧠 Raisonnement:</strong><br>${escapeHtml(conv.thinkingTrace)}</div>\n")
                    }
                    
                    html.append("</div>\n")
                }
                
                html.append("</body></html>")
                
                // Sauvegarder le fichier
                val fileName = "chatai_conversations_${System.currentTimeMillis()}.html"
                val file = java.io.File(getExternalFilesDir(null), fileName)
                file.writeText(html.toString())
                
                android.util.Log.i("CONV_HTML_EXPORT", "✅ Conversations exportées vers HTML: ${file.absolutePath}")
                
                runOnUiThread {
                    // Toast court
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "✅ ${conversations.size} conversations exportées en HTML!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    
                    // Dialog avec option de partage
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("📄 Export HTML Réussi")
                        .setMessage("${conversations.size} conversations exportées.\n\nFichier:\n${file.absolutePath}")
                        .setPositiveButton("Partager") { _, _ ->
                            shareHtmlFile(file)
                        }
                        .setNeutralButton("OK", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CONV_HTML_EXPORT", "❌ Erreur lors de l'export HTML", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "❌ Erreur export HTML: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 4: Partage le fichier HTML via Intent
     */
    private fun shareHtmlFile(file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Historique ChatAI - Export HTML")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Partager l'historique"))
        } catch (e: Exception) {
            android.util.Log.e("CONV_HTML_EXPORT", "❌ Erreur partage HTML", e)
            android.widget.Toast.makeText(
                this,
                "❌ Erreur partage: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 5: Affiche le dialog de migration embeddings
     */
    private fun showEmbeddingMigrationDialog() {
        lifecycleScope.launch {
            try {
                val migrationService = com.chatai.database.EmbeddingMigrationService(this@ConversationHistoryActivity)
                val conversationsNeedingMigration = migrationService.getConversationsNeedingMigration()
                
                if (conversationsNeedingMigration == 0) {
                    runOnUiThread {
                        androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                            .setTitle("✅ Migration Embeddings")
                            .setMessage("Toutes les conversations ont déjà des embeddings générés.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@launch
                }
                
                runOnUiThread {
                    // Dialog de confirmation avec information
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("🔄 Migration Embeddings")
                        .setMessage("${conversationsNeedingMigration} conversations nécessitent des embeddings.\n\nCette opération peut prendre plusieurs minutes selon le nombre de conversations.\n\n⚠️ Assurez-vous que Ollama local est accessible.")
                        .setPositiveButton("Démarrer") { _, _ ->
                            startEmbeddingMigration(migrationService, conversationsNeedingMigration)
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ConversationHistory", "Erreur vérification migration", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "Erreur: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 5: Lance la migration embeddings avec dialog de progression
     */
    private fun startEmbeddingMigration(
        migrationService: com.chatai.database.EmbeddingMigrationService,
        totalToMigrate: Int
    ) {
        // Créer le dialog de progression
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("🔄 Migration Embeddings en cours...")
            .setView(R.layout.dialog_progress) // Créer ce layout
            .setCancelable(false)
            .create()
        
        // Inflater le layout de progression
        val progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress, null)
        val progressBar = progressView.findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val progressText = progressView.findViewById<TextView>(R.id.progressText)
        
        progressDialog.setView(progressView)
        progressDialog.show()
        
        // Configurer la barre de progression
        progressBar.max = totalToMigrate
        progressBar.progress = 0
        progressText.text = "0 / $totalToMigrate"
        
        // Lancer la migration en arrière-plan
        lifecycleScope.launch {
            try {
                migrationService.migrateConversations(
                    onProgress = { current, total ->
                        runOnUiThread {
                            progressBar.progress = current
                            progressText.text = "$current / $total"
                        }
                    },
                    onComplete = { totalMigrated, totalErrors ->
                        runOnUiThread {
                            progressDialog.dismiss()
                            
                            val message = if (totalErrors == -1) {
                                "❌ Erreur critique lors de la migration"
                            } else if (totalErrors == 0) {
                                "✅ Migration terminée!\n\n$totalMigrated conversations migrées avec succès."
                            } else {
                                "⚠️ Migration terminée avec erreurs\n\n✅ $totalMigrated migrées\n❌ $totalErrors erreurs"
                            }
                            
                            androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                                .setTitle("Migration Embeddings")
                                .setMessage(message)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ConversationHistory", "Erreur migration embeddings", e)
                runOnUiThread {
                    progressDialog.dismiss()
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "Erreur migration: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU Phase 4: Échappe les caractères HTML pour sécurité
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "<br>")
    }
    
    /**
     * Importe des conversations depuis un fichier JSON
     */
    private fun importConversationsFromJson() {
        lifecycleScope.launch {
            try {
                // Lister les fichiers JSON disponibles
                val dir = getExternalFilesDir(null)
                val jsonFiles = dir?.listFiles { file -> file.extension == "json" }
                
                if (jsonFiles.isNullOrEmpty()) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this@ConversationHistoryActivity,
                            "❌ Aucun fichier JSON trouvé dans:\n${dir?.absolutePath}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                
                // Afficher menu de sélection de fichier
                runOnUiThread {
                    val fileNames = jsonFiles.map { it.name }.toTypedArray()
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("Sélectionner fichier JSON")
                        .setItems(fileNames) { _, which ->
                            lifecycleScope.launch {
                                importJsonFile(jsonFiles[which])
                            }
                        }
                        .setNegativeButton("Annuler", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CONV_JSON_IMPORT", "❌ Erreur lors de l'import JSON", e)
                runOnUiThread {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "❌ Erreur import JSON: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * Importe un fichier JSON spécifique
     */
    private suspend fun importJsonFile(file: java.io.File) {
        try {
            val jsonContent = file.readText()
            val jsonArray = org.json.JSONArray(jsonContent)
            val conversations = mutableListOf<ConversationEntity>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                val conversation = ConversationEntity(
                    conversationId = jsonObj.optString("conversationId", java.util.UUID.randomUUID().toString()),
                    timestamp = jsonObj.optLong("timestamp", System.currentTimeMillis()),
                    userMessage = jsonObj.getString("userMessage"),
                    aiResponse = jsonObj.getString("aiResponse"),
                    thinkingTrace = jsonObj.optString("thinkingTrace").takeIf { it.isNotEmpty() },
                    personality = jsonObj.optString("personality", "KITT"),
                    apiUsed = jsonObj.optString("apiUsed", "imported"),
                    responseTimeMs = jsonObj.optLong("responseTimeMs", 0),
                    platform = jsonObj.optString("platform", "imported"),
                    sessionId = jsonObj.optString("sessionId").takeIf { it.isNotEmpty() },
                    // ⭐ CRITIQUE: Restaurer embeddings (RAG/mémoire)
                    embeddingsJson = jsonObj.optString("embeddingsJson").takeIf { it.isNotEmpty() },
                    tags = jsonObj.optString("tags").takeIf { it.isNotEmpty() }
                )
                conversations.add(conversation)
            }
            
            // Insérer dans la base de données
            conversationDao.insertAll(conversations)
            
            android.util.Log.i("CONV_JSON_IMPORT", "✅ ${conversations.size} conversations importées depuis: ${file.name}")
            
            // Recharger les conversations
            withContext(Dispatchers.Main) {
                loadConversationsWithFilters()
                loadStats()
                android.widget.Toast.makeText(
                    this@ConversationHistoryActivity,
                    "✅ ${conversations.size} conversations importées avec succès",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("CONV_JSON_IMPORT", "❌ Erreur lors de l'import du fichier", e)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@ConversationHistoryActivity,
                    "❌ Erreur import: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU: Affiche le dialog pour backup complet
     */
    private fun showFullBackupDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("💾 Backup Complet")
            .setMessage("Créer un backup complet incluant:\n• Toutes les conversations\n• Embeddings (RAG/mémoire)\n• Configuration complète\n• Clés API (optionnel)")
            .setPositiveButton("Sans clés API") { _, _ ->
                createFullBackup(includeApiKeys = false)
            }
            .setNeutralButton("Avec clés API") { _, _ ->
                createFullBackup(includeApiKeys = true)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    /**
     * ⭐ NOUVEAU: Crée un backup complet
     */
    private fun createFullBackup(includeApiKeys: Boolean) {
        lifecycleScope.launch {
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                .setTitle("💾 Création du backup...")
                .setMessage("Collecte des données en cours...")
                .setCancelable(false)
                .create()
            
            progressDialog.show()
            
            try {
                val backupManager = com.chatai.managers.BackupManager(this@ConversationHistoryActivity)
                val backupFile = backupManager.exportFullBackup(includeApiKeys)
                
                progressDialog.dismiss()
                
                if (backupFile != null) {
                    val message = """
                        ✅ Backup créé avec succès!
                        
                        📁 Emplacement:
                        ${backupFile.absolutePath}
                        
                        📊 Taille: ${String.format("%.2f", backupFile.length() / 1024.0)} KB
                        
                        Le backup inclut:
                        • Conversations + Embeddings (RAG)
                        • Configuration complète
                        ${if (includeApiKeys) "• Clés API (chiffrées)" else ""}
                    """.trimIndent()
                    
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("✅ Backup Réussi")
                        .setMessage(message)
                        .setPositiveButton("Partager") { _, _ ->
                            shareBackupFile(backupFile)
                        }
                        .setNeutralButton("OK", null)
                        .show()
                } else {
                    android.widget.Toast.makeText(
                        this@ConversationHistoryActivity,
                        "❌ Erreur lors de la création du backup",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                android.util.Log.e("CONV_BACKUP", "Erreur backup complet", e)
                android.widget.Toast.makeText(
                    this@ConversationHistoryActivity,
                    "❌ Erreur: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU: Partage le fichier backup
     */
    private fun shareBackupFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ChatAI Backup - ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Partager le backup"))
        } catch (e: Exception) {
            android.util.Log.e("CONV_BACKUP", "Erreur partage backup", e)
            android.widget.Toast.makeText(
                this,
                "❌ Erreur partage: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * ⭐ NOUVEAU: Affiche le dialog pour restaurer un backup
     */
    private fun showRestoreBackupDialog() {
        lifecycleScope.launch {
            val backupManager = com.chatai.managers.BackupManager(this@ConversationHistoryActivity)
            val availableBackups = backupManager.listAvailableBackups()
            
            if (availableBackups.isEmpty()) {
                android.widget.Toast.makeText(
                    this@ConversationHistoryActivity,
                    "Aucun backup trouvé. Utilisez 'Choisir fichier'...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                selectBackupFile()
                return@launch
            }
            
            val backupNames = availableBackups.map { backup ->
                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(backup.timestamp))
                val sizeKB = String.format("%.1f", backup.sizeBytes / 1024.0)
                "$date - ${backup.conversationsCount} conversations (${sizeKB} KB)"
            }.toTypedArray()
            
            androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                .setTitle("📥 Restaurer Backup")
                .setItems(backupNames) { _, which ->
                    val selectedBackup = availableBackups[which]
                    showRestoreOptionsDialog(selectedBackup.file)
                }
                .setNegativeButton("Choisir autre fichier") { _, _ ->
                    selectBackupFile()
                }
                .setNeutralButton("Annuler", null)
                .show()
        }
    }
    
    /**
     * ⭐ NOUVEAU: Sélectionne un fichier backup depuis l'explorateur
     */
    private fun selectBackupFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Sélectionner un fichier backup"),
                REQUEST_CODE_SELECT_BACKUP
            )
        } catch (e: Exception) {
            android.util.Log.e("CONV_BACKUP", "Erreur sélection fichier", e)
        }
    }
    
    /**
     * ⭐ NOUVEAU: Affiche les options de restauration
     */
    private fun showRestoreOptionsDialog(backupFile: File) {
        val restoreModes = arrayOf("Fusionner", "Remplacer")
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("📥 Mode de Restauration")
            .setMessage("Fusionner: Ajoute aux données existantes\n\nRemplacer: Efface tout et restaure uniquement le backup")
            .setItems(restoreModes) { _, which ->
                val mode = when (which) {
                    0 -> com.chatai.managers.BackupManager.RestoreMode.MERGE
                    else -> com.chatai.managers.BackupManager.RestoreMode.REPLACE
                }
                restoreFullBackup(backupFile, mode)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    /**
     * ⭐ NOUVEAU: Restaure un backup complet
     */
    private fun restoreFullBackup(backupFile: File, restoreMode: com.chatai.managers.BackupManager.RestoreMode) {
        lifecycleScope.launch {
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                .setTitle("📥 Restauration en cours...")
                .setMessage("Restauration du backup...")
                .setCancelable(false)
                .create()
            
            progressDialog.show()
            
            try {
                val backupManager = com.chatai.managers.BackupManager(this@ConversationHistoryActivity)
                val result = backupManager.restoreFullBackup(backupFile, restoreMode)
                
                progressDialog.dismiss()
                
                if (result.success) {
                    val message = """
                        ✅ Backup restauré avec succès!
                        
                        📊 Résultats:
                        • ${result.conversationsRestored} conversations restaurées
                        • Configuration: ${if (result.configurationRestored) "✅" else "❌"}
                        • Clés API: ${if (result.apiKeysRestored) "✅" else "❌"}
                        
                        Mode: ${if (restoreMode == com.chatai.managers.BackupManager.RestoreMode.MERGE) "Fusionné" else "Remplacé"}
                    """.trimIndent()
                    
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("✅ Restauration Réussie")
                        .setMessage(message)
                        .setPositiveButton("OK") { _, _ ->
                            loadConversationsWithFilters()
                            loadStats()
                        }
                        .show()
                } else {
                    androidx.appcompat.app.AlertDialog.Builder(this@ConversationHistoryActivity, R.style.KittDialogTheme)
                        .setTitle("❌ Erreur de Restauration")
                        .setMessage("Erreur: ${result.error ?: "Inconnue"}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                android.util.Log.e("CONV_BACKUP", "Erreur restore backup", e)
                android.widget.Toast.makeText(
                    this@ConversationHistoryActivity,
                    "❌ Erreur: ${e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    companion object {
        private const val REQUEST_CODE_SELECT_BACKUP = 1001
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SELECT_BACKUP && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri != null) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val tempFile = File(cacheDir, "backup_temp.json")
                    tempFile.outputStream().use { output ->
                        inputStream?.use { input ->
                            input.copyTo(output)
                        }
                    }
                    showRestoreOptionsDialog(tempFile)
                } catch (e: Exception) {
                    android.util.Log.e("CONV_BACKUP", "Erreur copie fichier", e)
                }
            }
        }
    }
}

