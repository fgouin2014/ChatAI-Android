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
        
        // Charger les conversations
        loadConversations()
        loadStats()
    }
    
    private fun loadConversations() {
        lifecycleScope.launch {
            try {
                val conversations = conversationDao.getLastConversations(limit = 100)
                
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
        androidx.appcompat.app.AlertDialog.Builder(this)
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
            private val timestampText: TextView = itemView.findViewById(R.id.timestampText)
            private val personalityText: TextView = itemView.findViewById(R.id.personalityText)
            private val userMessageText: TextView = itemView.findViewById(R.id.userMessageText)
            private val aiResponseText: TextView = itemView.findViewById(R.id.aiResponseText)
            private val metadataText: TextView = itemView.findViewById(R.id.metadataText)
            
            fun bind(conversation: ConversationEntity) {
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
            }
        }
    }
}

