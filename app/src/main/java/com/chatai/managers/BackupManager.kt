package com.chatai.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import com.chatai.KeyringManager
import com.chatai.SecureConfig
import com.chatai.database.ChatAIDatabase
import com.chatai.database.ConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Gestionnaire complet de backup/restore pour ChatAI
 * 
 * Exporte et restaure:
 * - Conversations (DB)
 * - Embeddings (RAG/mémoire)
 * - Configuration (SharedPreferences + SecureConfig)
 * - Clés API (KeyringManager, optionnel)
 */
class BackupManager(private val context: Context) {
    
    private val TAG = "BackupManager"
    private val BACKUP_DIR = File(Environment.getExternalStorageDirectory(), "ChatAI-Files/backups")
    
    private val conversationDao by lazy {
        ChatAIDatabase.getDatabase(context).conversationDao()
    }
    
    /**
     * Crée le répertoire de backup s'il n'existe pas
     */
    private fun ensureBackupDirectory(): File {
        if (!BACKUP_DIR.exists()) {
            BACKUP_DIR.mkdirs()
            Log.i(TAG, "✅ Répertoire backup créé: ${BACKUP_DIR.absolutePath}")
        }
        return BACKUP_DIR
    }
    
    /**
     * Export complet: Conversations + Configuration + Clés API
     * 
     * @param includeApiKeys Si true, inclut les clés API (chiffrées)
     * @return File du backup créé, ou null en cas d'erreur
     */
    suspend fun exportFullBackup(includeApiKeys: Boolean = false): File? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔄 Démarrage export backup complet (includeApiKeys=$includeApiKeys)")
            
            // Créer répertoire backup
            ensureBackupDirectory()
            
            // 1. Collecter conversations
            val conversations = conversationDao.getAllConversationsForExport()
            Log.i(TAG, "📚 ${conversations.size} conversations à exporter")
            
            // 2. Collecter configuration
            val configuration = collectConfiguration()
            Log.i(TAG, "⚙️ Configuration collectée")
            
            // 3. Collecter clés API (optionnel)
            val apiKeys: JSONObject? = if (includeApiKeys) {
                val keyringManager = KeyringManager.getInstance(context)
                val keysJson = keyringManager.exportKeys(true)
                if (keysJson != null) {
                    JSONObject(keysJson)
                } else {
                    null
                }
            } else {
                null
            }
            
            if (includeApiKeys) {
                Log.i(TAG, if (apiKeys != null) "🔑 Clés API collectées" else "⚠️ Clés API non disponibles")
            }
            
            // 4. Construire JSON backup
            val backupJson = JSONObject().apply {
                put("backup_version", "1.0")
                put("app_version", "3.0")
                put("timestamp", System.currentTimeMillis())
                put("timestamp_readable", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                
                // Métadonnées
                val metadata = JSONObject().apply {
                    put("conversations_count", conversations.size)
                    val conversationsWithEmbeddings = conversations.count { !it.embeddingsJson.isNullOrEmpty() }
                    put("conversations_with_embeddings", conversationsWithEmbeddings)
                    put("has_configuration", true)
                    put("has_api_keys", apiKeys != null)
                    put("api_keys_encrypted", includeApiKeys && apiKeys != null)
                }
                put("metadata", metadata)
                
                // Conversations
                val conversationsArray = JSONArray()
                conversations.forEach { conv ->
                    val jsonObj = JSONObject().apply {
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
                        // ⭐ CRITIQUE: Embeddings (RAG/mémoire)
                        put("embeddingsJson", conv.embeddingsJson ?: "")
                        put("tags", conv.tags ?: "")
                        put("userRating", conv.userRating ?: JSONObject.NULL)
                        put("wasHelpful", conv.wasHelpful ?: JSONObject.NULL)
                    }
                    conversationsArray.put(jsonObj)
                }
                put("conversations", conversationsArray)
                
                // Configuration
                put("configuration", configuration)
                
                // Clés API (optionnel)
                if (apiKeys != null) {
                    put("api_keys", apiKeys)
                }
            }
            
            // 5. Écrire fichier
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "chatai_backup_$timestamp.json"
            val backupFile = File(BACKUP_DIR, fileName)
            
            backupFile.writeText(backupJson.toString(2))
            
            Log.i(TAG, "✅ Backup créé: ${backupFile.absolutePath}")
            Log.i(TAG, "📊 Taille: ${backupFile.length() / 1024} KB")
            
            backupFile
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur export backup", e)
            null
        }
    }
    
    /**
     * Collecte toute la configuration (SharedPreferences + SecureConfig)
     */
    private suspend fun collectConfiguration(): JSONObject = withContext(Dispatchers.IO) {
        val config = JSONObject()
        
        try {
            // 1. SharedPreferences
            val sharedPrefsObj = JSONObject()
            
            // Liste des fichiers SharedPreferences connus
            val prefsFiles = listOf(
                "chatai_ai_config",
                "chatai_settings",
                "console_config",
                "secure_config"
            )
            
            prefsFiles.forEach { prefsName ->
                try {
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    val prefsMap = prefs.all
                    
                    if (prefsMap.isNotEmpty()) {
                        val prefsJson = JSONObject()
                        prefsMap.forEach { (key, value) ->
                            when (value) {
                                is String -> prefsJson.put(key, value)
                                is Int -> prefsJson.put(key, value)
                                is Long -> prefsJson.put(key, value)
                                is Float -> prefsJson.put(key, value.toDouble())
                                is Boolean -> prefsJson.put(key, value)
                                is Set<*> -> {
                                    val stringSet = value.map { it.toString() }
                                    prefsJson.put(key, JSONArray(stringSet))
                                }
                                else -> prefsJson.put(key, value.toString())
                            }
                        }
                        sharedPrefsObj.put(prefsName, prefsJson)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur collecte SharedPreferences $prefsName", e)
                }
            }
            
            config.put("shared_preferences", sharedPrefsObj)
            
            // 2. SecureConfig (via KeyringManager maintenant)
            val secureConfigObj = JSONObject()
            try {
                val keyringManager = KeyringManager.getInstance(context)
                
                // Collecter toutes les clés API connues
                val apiKeys = listOf("huggingface", "ollama", "openai", "anthropic", "groq", "perplexity")
                apiKeys.forEach { provider ->
                    val hasKey = keyringManager.hasApiKey(provider)
                    secureConfigObj.put("${provider}_configured", hasKey)
                }
                
                // Autres paramètres SecureConfig (local_server_url, etc.)
                // Note: local_server_url est dans SharedPreferences "chatai_ai_config" -> "local_server_url"
                
            } catch (e: Exception) {
                Log.w(TAG, "Erreur collecte SecureConfig", e)
            }
            
            config.put("secure_config", secureConfigObj)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur collecte configuration", e)
        }
        
        config
    }
    
    /**
     * Restore complet depuis un fichier backup
     * 
     * @param backupFile Fichier backup JSON
     * @param restoreMode Mode de restauration (MERGE ou REPLACE)
     * @return Resultat de la restauration
     */
    suspend fun restoreFullBackup(
        backupFile: File,
        restoreMode: RestoreMode = RestoreMode.MERGE
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔄 Démarrage restore backup: ${backupFile.name}")
            
            // 1. Lire et parser JSON
            val backupJson = JSONObject(backupFile.readText())
            val backupVersion = backupJson.optString("backup_version", "unknown")
            
            Log.i(TAG, "📦 Backup version: $backupVersion")
            
            // Vérifier version (compatibilité future)
            if (backupVersion != "1.0") {
                Log.w(TAG, "⚠️ Version backup inconnue: $backupVersion")
            }
            
            var conversationsRestored = 0
            var conversationsSkipped = 0
            var configurationRestored = false
            var apiKeysRestored = false
            
            // 2. Restaurer conversations
            if (backupJson.has("conversations")) {
                val conversationsArray = backupJson.getJSONArray("conversations")
                val conversationsToRestore = mutableListOf<ConversationEntity>()
                
                for (i in 0 until conversationsArray.length()) {
                    val jsonObj = conversationsArray.getJSONObject(i)
                    
                    val conversation = ConversationEntity(
                        conversationId = jsonObj.optString("conversationId", UUID.randomUUID().toString()),
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
                        tags = jsonObj.optString("tags").takeIf { it.isNotEmpty() },
                        userRating = if (jsonObj.has("userRating") && !jsonObj.isNull("userRating")) {
                            jsonObj.optInt("userRating")
                        } else {
                            null
                        },
                        wasHelpful = if (jsonObj.has("wasHelpful") && !jsonObj.isNull("wasHelpful")) {
                            jsonObj.optBoolean("wasHelpful")
                        } else {
                            null
                        }
                    )
                    conversationsToRestore.add(conversation)
                }
                
                // Mode REPLACE: Vider DB d'abord
                if (restoreMode == RestoreMode.REPLACE) {
                    conversationDao.deleteAllConversations()
                    Log.i(TAG, "🗑️ Base de données vidée (mode REPLACE)")
                }
                
                // Insérer conversations
                conversationDao.insertAll(conversationsToRestore)
                conversationsRestored = conversationsToRestore.size
                
                Log.i(TAG, "✅ $conversationsRestored conversations restaurées")
            }
            
            // 3. Restaurer configuration
            if (backupJson.has("configuration")) {
                try {
                    restoreConfiguration(backupJson.getJSONObject("configuration"), restoreMode)
                    configurationRestored = true
                    Log.i(TAG, "✅ Configuration restaurée")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur restauration configuration", e)
                }
            }
            
            // 4. Restaurer clés API
            if (backupJson.has("api_keys")) {
                try {
                    val keyringManager = KeyringManager.getInstance(context)
                    val apiKeysJson = backupJson.getJSONObject("api_keys")
                    val success = keyringManager.importKeys(apiKeysJson.toString(2))
                    apiKeysRestored = success
                    if (success) {
                        Log.i(TAG, "✅ Clés API restaurées")
                    } else {
                        Log.w(TAG, "⚠️ Échec restauration clés API")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erreur restauration clés API", e)
                }
            }
            
            RestoreResult(
                success = true,
                conversationsRestored = conversationsRestored,
                conversationsSkipped = conversationsSkipped,
                configurationRestored = configurationRestored,
                apiKeysRestored = apiKeysRestored,
                error = null
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur restore backup", e)
            RestoreResult(
                success = false,
                conversationsRestored = 0,
                conversationsSkipped = 0,
                configurationRestored = false,
                apiKeysRestored = false,
                error = e.message
            )
        }
    }
    
    /**
     * Restaure la configuration depuis le backup
     */
    private suspend fun restoreConfiguration(
        configurationJson: JSONObject,
        restoreMode: RestoreMode
    ) = withContext(Dispatchers.IO) {
        try {
            // Restaurer SharedPreferences
            if (configurationJson.has("shared_preferences")) {
                val sharedPrefsJson = configurationJson.getJSONObject("shared_preferences")
                val prefsIterator = sharedPrefsJson.keys()
                
                while (prefsIterator.hasNext()) {
                    val prefsName = prefsIterator.next()
                    val prefsValues = sharedPrefsJson.getJSONObject(prefsName)
                    
                    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    
                    if (restoreMode == RestoreMode.REPLACE) {
                        editor.clear() // Vider avant restore
                    }
                    
                    val valueIterator = prefsValues.keys()
                    while (valueIterator.hasNext()) {
                        val key = valueIterator.next()
                        val value = prefsValues.get(key)
                        
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Double -> editor.putFloat(key, value.toFloat())
                            is Boolean -> editor.putBoolean(key, value)
                            is JSONArray -> {
                                val stringList = mutableListOf<String>()
                                for (i in 0 until value.length()) {
                                    stringList.add(value.getString(i))
                                }
                                editor.putStringSet(key, stringList.toSet())
                            }
                        }
                    }
                    editor.apply()
                }
            }
            
            // SecureConfig est restauré via KeyringManager (clés API)
            
        } catch (e: Exception) {
            Log.e(TAG, "Erreur restauration configuration", e)
            throw e
        }
    }
    
    /**
     * Liste tous les backups disponibles
     */
    fun listAvailableBackups(): List<BackupInfo> {
        ensureBackupDirectory()
        val backups = mutableListOf<BackupInfo>()
        
        BACKUP_DIR.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("chatai_backup_") && file.name.endsWith(".json")) {
                try {
                    val backupJson = JSONObject(file.readText())
                    val metadata = backupJson.optJSONObject("metadata")
                    
                    backups.add(
                        BackupInfo(
                            file = file,
                            timestamp = file.lastModified(),
                            conversationsCount = metadata?.optInt("conversations_count", 0) ?: 0,
                            hasEmbeddings = metadata?.optInt("conversations_with_embeddings", 0) ?: 0 > 0,
                            hasApiKeys = backupJson.has("api_keys"),
                            sizeBytes = file.length()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Erreur lecture backup ${file.name}", e)
                }
            }
        }
        
        // Trier par date (plus récent en premier)
        return backups.sortedByDescending { it.timestamp }
    }
    
    /**
     * Mode de restauration
     */
    enum class RestoreMode {
        MERGE,      // Fusionner avec données existantes
        REPLACE     // Remplacer toutes les données
    }
    
    /**
     * Résultat de la restauration
     */
    data class RestoreResult(
        val success: Boolean,
        val conversationsRestored: Int,
        val conversationsSkipped: Int,
        val configurationRestored: Boolean,
        val apiKeysRestored: Boolean,
        val error: String?
    )
    
    /**
     * Informations sur un backup
     */
    data class BackupInfo(
        val file: File,
        val timestamp: Long,
        val conversationsCount: Int,
        val hasEmbeddings: Boolean,
        val hasApiKeys: Boolean,
        val sizeBytes: Long
    )
}

