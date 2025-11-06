package com.chatai.managers

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import androidx.core.content.ContextCompat

/**
 * 🎵 KITT MUSIC MANAGER V3
 * 
 * ⚠️⚠️⚠️ CODE COPIÉ À 100% DE V1 - AUCUNE SIMPLIFICATION ⚠️⚠️⚠️
 * 
 * Ce manager gère TOUT le système musical de KITT:
 * - MediaPlayer (Knight Rider theme)
 * - Toggle musique (play/stop)
 * - Gestion permissions audio
 * - Listeners completion/error
 * 
 * RESPONSABILITÉS:
 * 1. Initialiser MediaPlayer
 * 2. Charger musique depuis assets
 * 3. Play/Stop musique
 * 4. Gérer erreurs MediaPlayer
 * 5. Notifier KittFragment des changements état
 * 
 * RÈGLES ABSOLUES:
 * - Vérifier permission MODIFY_AUDIO_SETTINGS
 * - Reset MediaPlayer avant chaque play
 * - Listeners AVANT prepare()
 * - Gérer toutes les erreurs gracieusement
 */
class KittMusicManager(
    private val context: Context,
    private val listener: MusicListener
) {
    
    companion object {
        private const val TAG = "KittMusicManager"
    }
    
    /**
     * Interface pour les callbacks Music
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    interface MusicListener {
        fun onMusicStarted()
        fun onMusicStopped()
        fun onMusicCompleted()
        fun onMusicError(errorCode: Int)
        fun showStatusMessage(message: String, duration: Long, type: MessageType)
        fun updateStatusIndicators()  // Pour indicateur MSQ
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // VARIABLES (COPIÉES DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    private var mediaPlayer: MediaPlayer? = null
    var isMusicPlaying = false
        private set
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALISATION (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialiser MediaPlayer au démarrage
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun initialize() {
        if (mediaPlayer == null) {
            try {
                android.util.Log.d(TAG, "Initialisation du MediaPlayer au démarrage...")
                mediaPlayer = MediaPlayer()
                android.util.Log.d(TAG, "MediaPlayer créé avec succès")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erreur lors de l'initialisation du MediaPlayer: ${e.message}")
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // PLAY / STOP (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Toggle musique (play/stop)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun toggleMusic() {
        android.util.Log.d(TAG, "toggleMusic() appelé - isMusicPlaying: $isMusicPlaying")
        if (isMusicPlaying) {
            android.util.Log.d(TAG, "Arrêt de la musique...")
            stopMusic()
        } else {
            android.util.Log.d(TAG, "Démarrage de la musique...")
            playMusic()
        }
    }
    
    /**
     * ⭐⭐ FONCTION CRITIQUE - Jouer la musique
     * 
     * Logique complète:
     * 1. Vérifier permission MODIFY_AUDIO_SETTINGS
     * 2. Vérifier MediaPlayer initialisé
     * 3. Reset MediaPlayer
     * 4. Charger depuis assets
     * 5. Configurer listeners AVANT prepare()
     * 6. prepare() puis start()
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER ⚠️⚠️⚠️
     */
    fun playMusic() {
        try {
            android.util.Log.d(TAG, "=== DÉBUT LECTURE MUSIQUE ===")
            listener.showStatusMessage("Chargement de la musique...", 2000, MessageType.STATUS)
            
            // Vérifier les permissions audio
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) 
                != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e(TAG, "Permission MODIFY_AUDIO_SETTINGS manquante !")
                listener.showStatusMessage("Erreur: Permission audio manquante", 3000, MessageType.ERROR)
                return
            }
            
            // Vérifier que le MediaPlayer est initialisé
            if (mediaPlayer == null) {
                android.util.Log.e(TAG, "MediaPlayer non initialisé !")
                listener.showStatusMessage("Erreur: MediaPlayer non initialisé", 3000, MessageType.ERROR)
                return
            }
            
            // ⚠️ Réinitialiser le MediaPlayer s'il était utilisé
            if (isMusicPlaying) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            
            android.util.Log.d(TAG, "Chargement du fichier MP3...")
            val assetFileDescriptor: AssetFileDescriptor = context.assets.openFd("musicTheme/Mundian To Bach Ke - Panjabi MC.mp3")
            mediaPlayer?.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
            assetFileDescriptor.close()
            
            // ⚠️⚠️ Configuration des listeners AVANT prepare() - CRITIQUE
            mediaPlayer?.setOnCompletionListener {
                android.util.Log.d(TAG, "Musique terminée")
                isMusicPlaying = false
                listener.onMusicCompleted()
                listener.updateStatusIndicators() // Mettre à jour l'indicateur MSQ
                listener.showStatusMessage("Musique terminée", 2000, MessageType.STATUS)
            }
            
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                android.util.Log.e(TAG, "ERREUR MediaPlayer - what: $what, extra: $extra")
                isMusicPlaying = false
                listener.onMusicError(what)
                listener.updateStatusIndicators() // Mettre à jour l'indicateur MSQ
                listener.showStatusMessage("Erreur audio (code: $what)", 3000, MessageType.ERROR)
                true
            }
            
            android.util.Log.d(TAG, "Préparation du MediaPlayer...")
            mediaPlayer?.prepare()
            
            android.util.Log.d(TAG, "Démarrage de la lecture...")
            mediaPlayer?.start()
            isMusicPlaying = true
            listener.onMusicStarted()
            listener.updateStatusIndicators() // Mettre à jour l'indicateur MSQ
            listener.showStatusMessage("Musique: Mundian To Bach Ke", 3000, MessageType.VOICE)
            android.util.Log.d(TAG, "=== MUSIQUE DÉMARRÉE ===")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "ERREUR: ${e.message}")
            listener.showStatusMessage("Erreur: ${e.message}", 5000, MessageType.ERROR)
            isMusicPlaying = false
            listener.onMusicError(-1)
            listener.updateStatusIndicators() // Mettre à jour l'indicateur MSQ
        }
    }
    
    /**
     * Arrêter la musique
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopMusic() {
        try {
            android.util.Log.d(TAG, "Arrêt de la musique...")
            mediaPlayer?.stop()
            isMusicPlaying = false
            
            listener.onMusicStopped()
            listener.updateStatusIndicators() // Mettre à jour l'indicateur MSQ
            listener.showStatusMessage("Musique arrêtée", 2000, MessageType.STATUS)
            android.util.Log.d(TAG, "Musique arrêtée avec succès")
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erreur lors de l'arrêt: ${e.message}")
            listener.showStatusMessage("Erreur: ${e.message}", 3000, MessageType.ERROR)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ÉTATS (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Vérifier si musique est en cours
     */
    fun isPlaying(): Boolean = isMusicPlaying
    
    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Détruire le manager (libérer ressources)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun destroy() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isMusicPlaying = false
            android.util.Log.i(TAG, "🛑 KittMusicManager destroyed")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error destroying music manager: ${e.message}")
        }
    }
}

