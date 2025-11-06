package com.chatai.managers

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.chatai.R

/**
 * 🎵 Gestionnaire Audio/Musique pour KITT
 * 
 * Responsabilités:
 * - Lecture musique d'ambiance
 * - Contrôle MediaPlayer
 * - Gestion du volume
 */
class KittAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "KittAudioManager"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPlaying = false
    
    /**
     * Initialiser la musique d'ambiance
     */
    fun initialize() {
        try {
            if (mediaPlayer == null) {
                // TODO: Ajouter fichier audio kitt_ambient.mp3 dans res/raw/
                // Pour l'instant, pas de musique (fonctionnalité optionnelle)
                Log.i(TAG, "⚠️ Music file not found (optional feature)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing music", e)
        }
    }
    
    /**
     * Démarrer la musique
     */
    fun startMusic() {
        try {
            if (mediaPlayer == null) {
                initialize()
            }
            
            if (!isMusicPlaying) {
                mediaPlayer?.start()
                isMusicPlaying = true
                Log.i(TAG, "▶️ Music started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting music", e)
        }
    }
    
    /**
     * Arrêter la musique
     */
    fun stopMusic() {
        try {
            if (isMusicPlaying) {
                mediaPlayer?.pause()
                isMusicPlaying = false
                Log.i(TAG, "⏸️ Music stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping music", e)
        }
    }
    
    /**
     * Toggle musique (on/off)
     */
    fun toggleMusic(): Boolean {
        if (isMusicPlaying) {
            stopMusic()
        } else {
            startMusic()
        }
        return isMusicPlaying
    }
    
    /**
     * Vérifier si la musique joue
     */
    fun isMusicPlaying(): Boolean = isMusicPlaying
    
    /**
     * Changer le volume de la musique
     */
    fun setMusicVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(clampedVolume, clampedVolume)
        Log.i(TAG, "Volume set to: $clampedVolume")
    }
    
    /**
     * Détruire le MediaPlayer
     */
    fun destroy() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            isMusicPlaying = false
            Log.i(TAG, "🛑 KittAudioManager destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying music", e)
        }
    }
}

