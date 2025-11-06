package com.chatai.managers

import android.content.Context
import android.util.Log
import com.chatai.services.KittAIService
import kotlinx.coroutines.*

/**
 * ⚙️ Processeur de Commandes pour KITT
 * 
 * Responsabilités:
 * - Analyse des commandes vocales
 * - Routage vers les bonnes fonctions
 * - Commandes système (musique, config, etc.)
 * - Intégration avec KittAIService
 */
class KittCommandProcessor(
    private val context: Context,
    private val listener: CommandProcessorListener
) {
    
    companion object {
        private const val TAG = "KittCommandProcessor"
    }
    
    /**
     * Interface pour les callbacks de traitement
     */
    interface CommandProcessorListener {
        fun onCommandProcessing(command: String)
        fun onCommandResponse(response: String)
        fun onCommandError(error: String)
        
        // Actions système
        fun onToggleMusic()
        fun onOpenFileExplorer()
        fun onShowSystemStatus()
        fun onTestAPIs()
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var kittAIService: KittAIService? = null
    
    /**
     * Définir le service IA
     */
    fun setAIService(service: KittAIService) {
        kittAIService = service
        Log.i(TAG, "✅ AI Service set")
    }
    
    /**
     * Traiter une commande vocale
     * Retourne true si commande système traitée, false si nécessite IA
     */
    fun processCommand(command: String): Boolean {
        val lowerCommand = command.lowercase().trim()
        
        Log.i(TAG, "📝 Processing command: '$command'")
        
        // Commandes système locales (pas d'IA nécessaire)
        when (lowerCommand) {
            "status", "status système", "état système" -> {
                listener.onShowSystemStatus()
                return true
            }
            "explorateur", "fichiers", "ouvre fichiers", "explorateur de fichiers" -> {
                listener.onOpenFileExplorer()
                return true
            }
            "test réseau", "test api", "test apis", "tester apis", "tester les apis" -> {
                listener.onTestAPIs()
                return true
            }
            "musique", "toggle musique", "play musique", "stop musique", 
            "lance la musique", "arrête la musique" -> {
                listener.onToggleMusic()
                return true
            }
        }
        
        // Commandes nécessitant l'IA générative
        processAICommand(command)
        return false
    }
    
    /**
     * Traiter une commande avec l'IA
     */
    private fun processAICommand(command: String) {
        if (kittAIService == null) {
            Log.e(TAG, "❌ AI Service not initialized")
            listener.onCommandError("Service IA non initialisé")
            return
        }
        
        listener.onCommandProcessing(command)
        
        coroutineScope.launch {
            try {
                val response = kittAIService!!.processUserInput(command)
                listener.onCommandResponse(response)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing AI command", e)
                listener.onCommandError("Erreur de traitement: ${e.message}")
            }
        }
    }
    
    /**
     * Détruire le processor
     */
    fun destroy() {
        coroutineScope.cancel()
        Log.i(TAG, "🛑 KittCommandProcessor destroyed")
    }
}

