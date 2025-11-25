package com.chatai.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.chatai.services.EmbeddingService
import kotlinx.coroutines.delay

/**
 * Service de migration rétroactive des embeddings
 * Génère des embeddings pour les anciennes conversations qui n'en ont pas
 * 
 * ⭐ NOUVEAU Phase 5: Migration embeddings anciennes conversations
 * 
 * Usage:
 * ```
 * val migrationService = EmbeddingMigrationService(context)
 * migrationService.migrateConversations(
 *     onProgress = { current, total -> 
 *         // Mettre à jour UI
 *     },
 *     onComplete = { totalMigrated ->
 *         // Migration terminée
 *     }
 * )
 * ```
 */
class EmbeddingMigrationService(private val context: Context) {
    
    companion object {
        private const val TAG = "EmbeddingMigrationService"
        
        // Taille du batch pour éviter timeout (10 conversations à la fois)
        private const val BATCH_SIZE = 10
        
        // Délai entre batches pour éviter surcharge Ollama (1 seconde)
        private const val BATCH_DELAY_MS = 1000L
    }
    
    private val database = ChatAIDatabase.getDatabase(context)
    private val conversationDao = database.conversationDao()
    private val embeddingService = EmbeddingService(context)
    
    /**
     * Migre toutes les conversations sans embeddings
     * @param onProgress Callback appelé après chaque batch (current, total)
     * @param onComplete Callback appelé à la fin (totalMigrated, totalErrors)
     */
    suspend fun migrateConversations(
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onComplete: ((totalMigrated: Int, totalErrors: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔄 Démarrage migration embeddings...")
            
            // 1. Récupérer toutes les conversations sans embeddings
            val allConversations = conversationDao.getAllConversationsForExport()
            val conversationsWithoutEmbeddings = allConversations.filter { conv ->
                conv.embeddingsJson.isNullOrEmpty()
            }
            
            val totalToMigrate = conversationsWithoutEmbeddings.size
            
            if (totalToMigrate == 0) {
                Log.i(TAG, "✅ Aucune conversation à migrer (toutes ont déjà des embeddings)")
                onComplete?.invoke(0, 0)
                return@withContext
            }
            
            Log.i(TAG, "📊 ${totalToMigrate} conversations à migrer sur ${allConversations.size} total")
            
            var totalMigrated = 0
            var totalErrors = 0
            
            // 2. Traiter par batches
            conversationsWithoutEmbeddings.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
                val batchNumber = batchIndex + 1
                val totalBatches = (totalToMigrate + BATCH_SIZE - 1) / BATCH_SIZE
                
                Log.d(TAG, "📦 Traitement batch $batchNumber/$totalBatches (${batch.size} conversations)")
                
                // Traiter chaque conversation du batch
                batch.forEach { conversation ->
                    try {
                        // Générer l'embedding pour cette conversation
                        val embedding = embeddingService.embedConversation(
                            conversation.userMessage,
                            conversation.aiResponse
                        )
                        
                        if (embedding != null) {
                            // Convertir en JSON pour stockage
                            val embeddingJson = embeddingService.embeddingToJson(embedding)
                            
                            // Mettre à jour dans Room DB
                            conversationDao.updateEmbeddings(conversation.id, embeddingJson)
                            
                            totalMigrated++
                            Log.d(TAG, "✅ Conversation #${conversation.id} migrée (${embedding.size} dimensions)")
                        } else {
                            totalErrors++
                            Log.w(TAG, "⚠️ Échec génération embedding pour conversation #${conversation.id}")
                        }
                        
                    } catch (e: Exception) {
                        totalErrors++
                        Log.e(TAG, "❌ Erreur migration conversation #${conversation.id}", e)
                    }
                }
                
                // Mettre à jour progress
                val currentProgress = (batchIndex + 1) * BATCH_SIZE
                onProgress?.invoke(
                    currentProgress.coerceAtMost(totalToMigrate),
                    totalToMigrate
                )
                
                // Délai entre batches pour éviter surcharge Ollama
                if (batchIndex < totalBatches - 1) {
                    delay(BATCH_DELAY_MS)
                }
            }
            
            Log.i(TAG, "✅ Migration terminée: $totalMigrated migrées, $totalErrors erreurs")
            onComplete?.invoke(totalMigrated, totalErrors)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur critique lors de la migration", e)
            onComplete?.invoke(0, -1) // -1 indique une erreur critique
        }
    }
    
    /**
     * Vérifie combien de conversations nécessitent une migration
     * @return Nombre de conversations sans embeddings
     */
    suspend fun getConversationsNeedingMigration(): Int = withContext(Dispatchers.IO) {
        try {
            val allConversations = conversationDao.getAllConversationsForExport()
            val withoutEmbeddings = allConversations.count { conv ->
                conv.embeddingsJson.isNullOrEmpty()
            }
            return@withContext withoutEmbeddings
        } catch (e: Exception) {
            Log.e(TAG, "Erreur comptage conversations à migrer", e)
            return@withContext 0
        }
    }
}

