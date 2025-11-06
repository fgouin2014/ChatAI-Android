package com.chatai.managers

import android.os.Handler
import android.os.Looper
import android.widget.TextView

/**
 * 💬 KITT MESSAGE QUEUE MANAGER V3
 * 
 * ⚠️⚠️⚠️ CODE COPIÉ À 100% DE V1 - AUCUNE SIMPLIFICATION ⚠️⚠️⚠️
 * 
 * Ce manager gère TOUTE la file de messages de KITT:
 * - Priority queue (0 = normal, 1 = haute priorité)
 * - Message types (STATUS, VOICE, AI, COMMAND, ERROR, ANIMATION)
 * - Marquee display avec défilement automatique
 * - Calcul intelligent de la durée d'affichage
 * 
 * RESPONSABILITÉS:
 * 1. Gérer la queue de messages avec priorités
 * 2. Afficher les messages dans le marquee
 * 3. Calculer la durée d'affichage selon type et longueur
 * 4. Gérer le défilement automatique pour messages longs
 * 5. Traiter la queue séquentiellement
 * 
 * RÈGLES ABSOLUES:
 * - Le calcul de durée est CRITIQUE (67ms par caractère)
 * - Les types de messages ont des durées différentes
 * - Le marquee doit défiler PENDANT la pause
 * - La queue doit être triée par priorité
 */

/**
 * Types de messages (COPIÉ DE V1)
 */
enum class MessageType {
    STATUS,      // Messages de statut système
    VOICE,       // Messages vocaux
    AI,          // Réponses IA
    COMMAND,     // Commandes KITT
    ERROR,       // Messages d'erreur
    ANIMATION    // Messages d'animation
}

/**
 * Structure message (COPIÉ DE V1)
 */
data class StatusMessage(
    val text: String,
    val type: MessageType,
    val duration: Long,
    val priority: Int = 0  // 0 = normal, 1 = haute priorité
)

class KittMessageQueueManager(
    private val statusText: TextView,
    private val onQueueEmpty: () -> Unit  // Callback quand queue vide
) {
    
    companion object {
        private const val TAG = "KittMessageQueueManager"
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // ════════════════════════════════════════════════════════════════════════
    // VARIABLES (COPIÉES DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    private var currentMessageType: MessageType = MessageType.STATUS
    private val messageQueue = mutableListOf<StatusMessage>()
    var isProcessingQueue = false
        private set
    private var statusMessageHandler: Runnable? = null
    
    // ════════════════════════════════════════════════════════════════════════
    // MESSAGE QUEUE (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Ajouter un message à la queue
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun showStatusMessage(
        message: String, 
        duration: Long = 2000, 
        type: MessageType = MessageType.STATUS, 
        priority: Int = 0
    ) {
        // Ajouter le message à la queue
        val statusMessage = StatusMessage(message, type, duration, priority)
        messageQueue.add(statusMessage)
        
        // ⚠️ Trier la queue par priorité (haute priorité en premier)
        messageQueue.sortByDescending { it.priority }
        
        // Traiter la queue si pas déjà en cours
        if (!isProcessingQueue) {
            processMessageQueue()
        }
    }
    
    /**
     * ⭐⭐ FONCTION CRITIQUE - Traiter la queue de messages
     * 
     * Gère l'affichage séquentiel des messages:
     * 1. Prendre le premier message
     * 2. Afficher avec marquee
     * 3. Calculer durée (type + longueur)
     * 4. Pause après affichage
     * 5. Passer au suivant
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER ⚠️⚠️⚠️
     */
    private fun processMessageQueue() {
        if (messageQueue.isEmpty()) {
            isProcessingQueue = false
            onQueueEmpty()  // Notifier KittFragment
            return
        }
        
        isProcessingQueue = true
        
        // Prendre le premier message de la queue
        val currentMessage = messageQueue.removeAt(0)
        currentMessageType = currentMessage.type
        
        // Afficher le message
        displayMessage(currentMessage.text, currentMessage.duration)
        
        // ⚠️ Calculer la durée totale avec pause à la fin
        val displayDuration = calculateDisplayDuration(currentMessage)
        val pauseDuration = if (currentMessage.text.length > 30) 2000L else 500L // Pause plus longue pour les messages qui défilent
        
        // Programmer l'arrêt du défilement et la pause
        statusMessageHandler = Runnable {
            // Garder le défilement marquee actif jusqu'à la suppression du message
            // Le marquee continue de défiler pendant la pause
            
            // Pause pour laisser le temps de lire (marquee toujours actif)
            mainHandler.postDelayed({
                // Arrêter le défilement seulement quand on supprime le message
                statusText.isSelected = false
                
                // Retour au statut de base
                showDefaultStatus()
                
                // Traiter le prochain message dans la queue
                processMessageQueue()
            }, pauseDuration)
        }
        
        mainHandler.postDelayed(statusMessageHandler!!, displayDuration)
    }
    
    /**
     * Afficher un message dans le marquee
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    private fun displayMessage(message: String, @Suppress("UNUSED_PARAMETER") duration: Long) {
        // Afficher le message complet
        statusText.text = message
        
        // ⚠️ Activer le défilement marquee en continu pour tous les messages
        statusText.isSelected = true
        
        // S'assurer que le marquee fonctionne correctement
        statusText.requestFocus()
        
        // Log pour debug
        android.util.Log.d("StatusText", "Displaying: '$message' (Type: $currentMessageType, Scroll: true, Length: ${message.length})")
    }
    
    /**
     * ⭐⭐⭐ FONCTION TRÈS CRITIQUE - Calculer durée affichage
     * 
     * Logique complexe:
     * 1. Durée de base selon type de message
     * 2. Temps supplémentaire pour messages longs (marquee)
     * 3. Calcul: 67ms par caractère (vitesse défilement)
     * 4. Buffer de 1 seconde pour sécurité
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS MODIFIER ⚠️⚠️⚠️
     */
    private fun calculateDisplayDuration(message: StatusMessage): Long {
        val baseDuration = when (message.type) {
            MessageType.STATUS -> 2000L
            MessageType.VOICE -> 3000L
            MessageType.AI -> 4000L
            MessageType.COMMAND -> 2500L
            MessageType.ERROR -> 3000L
            MessageType.ANIMATION -> 1500L
        }
        
        // ⚠️ Ajouter du temps supplémentaire pour les messages longs qui défilent
        val additionalTime = if (message.text.length > 30) {
            // Calculer le temps nécessaire pour que le message défile complètement
            // Vitesse de défilement : environ 15 caractères par seconde (plus rapide)
            val scrollTime = (message.text.length * 67L) // 67ms par caractère pour défilement rapide
            val bufferTime = 1000L // 1 seconde de buffer pour s'assurer que tout le texte défile
            scrollTime + bufferTime
        } else {
            0L
        }
        
        val totalDuration = baseDuration + additionalTime
        
        // Log pour debug du timing
        android.util.Log.d("StatusText", "Timing: Base=${baseDuration}ms, Additional=${additionalTime}ms, Total=${totalDuration}ms")
        
        return totalDuration
    }
    
    /**
     * Afficher le statut par défaut (vide)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    private fun showDefaultStatus() {
        // Afficher un message vide (les indicateurs RDY/BSY suffisent)
        statusText.text = ""
        statusText.isSelected = false
        currentMessageType = MessageType.STATUS
    }
    
    /**
     * Vider la queue de messages
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun clearMessageQueue() {
        messageQueue.clear()
        isProcessingQueue = false
        statusMessageHandler?.let { mainHandler.removeCallbacks(it) }
        android.util.Log.d(TAG, "📭 Message queue cleared")
    }
    
    /**
     * Obtenir le type de message actuel
     */
    fun getCurrentMessageType(): MessageType = currentMessageType
    
    /**
     * Obtenir le nombre de messages en attente
     */
    fun getQueueSize(): Int = messageQueue.size
    
    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Détruire le manager (libérer ressources)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun destroy() {
        clearMessageQueue()
        mainHandler.removeCallbacksAndMessages(null)
        android.util.Log.i(TAG, "🛑 KittMessageQueueManager destroyed")
    }
}

