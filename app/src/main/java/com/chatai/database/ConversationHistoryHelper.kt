package com.chatai.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class pour accéder à Room DB depuis Java (WebAppInterface)
 * Expose les méthodes suspend Kotlin de manière Java-friendly
 */
object ConversationHistoryHelper {
    
    private const val TAG = "ConversationHistoryHelper"
    
    /**
     * Récupère les conversations depuis Room DB (Java-friendly)
     */
    fun getConversations(context: Context, limit: Int): String {
        return runBlocking {
            try {
                val database = ChatAIDatabase.getDatabase(context)
                val dao = database.conversationDao()
                val conversations = dao.getLastConversations(limit)
                
                val jsonArray = JSONArray()
                for (conv in conversations) {
                    val jsonObj = JSONObject().apply {
                        put("id", conv.id)
                        put("conversationId", conv.conversationId)
                        put("userMessage", conv.userMessage)
                        put("aiResponse", conv.aiResponse)
                        put("personality", conv.personality)
                        put("apiUsed", conv.apiUsed)
                        put("platform", conv.platform)
                        put("timestamp", conv.timestamp)
                        put("responseTimeMs", conv.responseTimeMs)
                        put("thinkingTrace", conv.thinkingTrace ?: "")
                    }
                    jsonArray.put(jsonObj)
                }
                
                jsonArray.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur récupération conversations", e)
                "[]"
            }
        }
    }
    
    /**
     * Recherche dans l'historique (Java-friendly)
     */
    fun searchConversations(context: Context, query: String, limit: Int): String {
        return runBlocking {
            try {
                val database = ChatAIDatabase.getDatabase(context)
                val dao = database.conversationDao()
                val conversations = dao.searchConversations(query, limit)
                
                val jsonArray = JSONArray()
                for (conv in conversations) {
                    val jsonObj = JSONObject().apply {
                        put("id", conv.id)
                        put("conversationId", conv.conversationId)
                        put("userMessage", conv.userMessage)
                        put("aiResponse", conv.aiResponse)
                        put("personality", conv.personality)
                        put("apiUsed", conv.apiUsed)
                        put("platform", conv.platform)
                        put("timestamp", conv.timestamp)
                        put("responseTimeMs", conv.responseTimeMs)
                        put("thinkingTrace", conv.thinkingTrace ?: "")
                    }
                    jsonArray.put(jsonObj)
                }
                
                jsonArray.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur recherche conversations", e)
                "[]"
            }
        }
    }
    
    /**
     * Récupère les statistiques (Java-friendly)
     */
    fun getConversationStats(context: Context): String {
        return runBlocking {
            try {
                val database = ChatAIDatabase.getDatabase(context)
                val dao = database.conversationDao()
                
                val totalConversations = dao.getTotalConversations()
                val averageResponseTime = dao.getAverageResponseTime() ?: 0L
                val mostUsedAPI = dao.getMostUsedAPI() ?: "N/A"
                
                val stats = JSONObject().apply {
                    put("totalConversations", totalConversations)
                    put("averageResponseTime", averageResponseTime)
                    put("mostUsedAPI", mostUsedAPI)
                }
                
                stats.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur récupération statistiques", e)
                "{}"
            }
        }
    }
    
    /**
     * Exporte toutes les conversations en JSON (Java-friendly)
     */
    fun exportConversationsToJson(context: Context): String {
        return runBlocking {
            try {
                val database = ChatAIDatabase.getDatabase(context)
                val dao = database.conversationDao()
                val conversations = dao.getAllConversationsForExport()
                
                val jsonArray = JSONArray()
                for (conv in conversations) {
                    val jsonObj = JSONObject().apply {
                        put("id", conv.id)
                        put("conversationId", conv.conversationId)
                        put("userMessage", conv.userMessage)
                        put("aiResponse", conv.aiResponse)
                        put("personality", conv.personality)
                        put("apiUsed", conv.apiUsed)
                        put("platform", conv.platform)
                        put("timestamp", conv.timestamp)
                        put("responseTimeMs", conv.responseTimeMs)
                        put("thinkingTrace", conv.thinkingTrace ?: "")
                    }
                    jsonArray.put(jsonObj)
                }
                
                jsonArray.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur export JSON conversations", e)
                "[]"
            }
        }
    }
    
    /**
     * Exporte toutes les conversations en HTML (Java-friendly)
     */
    fun exportConversationsToHtml(context: Context): String {
        return runBlocking {
            try {
                val database = ChatAIDatabase.getDatabase(context)
                val dao = database.conversationDao()
                val conversations = dao.getAllConversationsForExport()
                
                val html = StringBuilder()
                html.append("<!DOCTYPE html>\n")
                html.append("<html><head><meta charset='UTF-8'><title>Historique ChatAI</title>")
                html.append("<style>body{font-family:Arial,sans-serif;padding:20px;background:#0f172a;color:#e2e8f0;}")
                html.append(".conv{background:#1e293b;border:1px solid #334155;border-radius:8px;padding:16px;margin-bottom:16px;}")
                html.append(".header{color:#94a3b8;font-size:12px;margin-bottom:8px;}")
                html.append(".user{color:#3b82f6;margin:8px 0;}")
                html.append(".ai{color:#8b5cf6;margin:8px 0;}")
                html.append(".thinking{background:#1e293b;border-left:3px solid #8b5cf6;padding:12px;margin-top:8px;font-family:monospace;font-size:11px;color:#cbd5e1;}")
                html.append("</style></head><body>")
                html.append("<h1>📚 Historique des conversations ChatAI</h1>")
                html.append("<p>Exporté le ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append("</p>")
                html.append("<p>Total: ").append(conversations.size).append(" conversations</p><hr>")
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                for (conv in conversations) {
                    val dateStr = dateFormat.format(Date(conv.timestamp))
                    
                    html.append("<div class='conv'>")
                    html.append("<div class='header'>").append(dateStr).append(" | ")
                         .append(conv.personality).append(" | ")
                         .append(conv.apiUsed).append(" | ")
                         .append(conv.responseTimeMs).append("ms</div>")
                    html.append("<div class='user'><strong>VOUS:</strong> ").append(escapeHtml(conv.userMessage)).append("</div>")
                    html.append("<div class='ai'><strong>").append(conv.personality).append(":</strong> ").append(escapeHtml(conv.aiResponse)).append("</div>")
                    
                    if (!conv.thinkingTrace.isNullOrEmpty()) {
                        html.append("<div class='thinking'><strong>🧠 Raisonnement:</strong><br>").append(escapeHtml(conv.thinkingTrace)).append("</div>")
                    }
                    
                    html.append("</div>")
                }
                
                html.append("</body></html>")
                html.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Erreur export HTML conversations", e)
                "<html><body>Erreur export HTML: ${e.message}</body></html>"
            }
        }
    }
    
    /**
     * Supprime toutes les conversations (Java-friendly)
     */
    fun deleteAllConversations(context: Context): Boolean {
        return runBlocking {
            try {
                val database = ChatAIDatabase.getDatabase(context)
                val dao = database.conversationDao()
                dao.deleteAllConversations()
                Log.i(TAG, "✅ Toutes les conversations supprimées")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Erreur suppression conversations", e)
                false
            }
        }
    }
    
    /**
     * Échappe les caractères HTML pour sécurité
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}


