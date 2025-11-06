package com.chatai.activities

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
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    
    private val database by lazy { ChatAIDatabase.getDatabase(this) }
    private val conversationDao by lazy { database.conversationDao() }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_history)
        
        // Initialiser les vues
        recyclerView = findViewById(R.id.conversationsRecyclerView)
        statsText = findViewById(R.id.statsText)
        emptyView = findViewById(R.id.emptyView)
        
        // Configurer RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ConversationAdapter()
        recyclerView.adapter = adapter
        
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
        
        // Générer des UUIDs pour les anciennes conversations (migration) puis charger
        lifecycleScope.launch {
            generateMissingUUIDs()
            // Charger après la migration
            loadConversations()
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
    
    private fun loadStats() {
        lifecycleScope.launch {
            try {
                val total = conversationDao.getTotalConversations()
                val kittCount = conversationDao.getConversationCountByPersonality("KITT")
                val gladosCount = conversationDao.getConversationCountByPersonality("GLaDOS")
                val avgTime = conversationDao.getAverageResponseTime() ?: 0L
                val mostUsed = conversationDao.getMostUsedAPI() ?: "unknown"
                
                statsText.text = buildString {
                    appendLine("📊 STATISTIQUES")
                    appendLine()
                    appendLine("Total conversations: $total")
                    appendLine("KITT: $kittCount | GLaDOS: $gladosCount")
                    appendLine("Temps moyen: ${avgTime}ms")
                    appendLine("API principale: $mostUsed")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ConversationHistory", "Error loading stats", e)
                statsText.text = "Erreur de statistiques"
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
                        loadConversations()
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
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        
        fun setConversations(newConversations: List<ConversationEntity>) {
            conversations = newConversations
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
                
                // Question de l'utilisateur
                userMessageText.text = "VOUS: ${conversation.userMessage}"
                
                // Réponse de l'IA
                aiResponseText.text = "${conversation.personality}: ${conversation.aiResponse}"
                
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
            "📊 Exporter TOUT dans logcat",
            "🆔 Exporter une conversation par ID",
            "💾 Exporter vers fichier JSON",
            "📥 Importer depuis fichier JSON"
        )
        
        androidx.appcompat.app.AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("Options d'Export/Import")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportConversationsToLogcat()
                    1 -> promptForConversationId()
                    2 -> exportConversationsToJson()
                    3 -> importConversationsFromJson()
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
                    sessionId = jsonObj.optString("sessionId").takeIf { it.isNotEmpty() }
                )
                conversations.add(conversation)
            }
            
            // Insérer dans la base de données
            conversationDao.insertAll(conversations)
            
            android.util.Log.i("CONV_JSON_IMPORT", "✅ ${conversations.size} conversations importées depuis: ${file.name}")
            
            // Recharger les conversations
            withContext(Dispatchers.Main) {
                loadConversations()
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
}

