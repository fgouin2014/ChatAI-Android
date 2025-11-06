package com.chatai.services

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 🚗 KITT Quick Settings Tile
 * 
 * Tuile dans le centre de notification pour activer KITT d'un seul tap
 * Pas de toggle - Activation unique pour éviter les faux positifs
 * 
 * Usage:
 * - Glisser depuis le haut → Quick Settings
 * - Tap sur "🚗 KITT"
 * - KITT s'active pour UNE commande vocale
 */
@RequiresApi(Build.VERSION_CODES.N)
class KittTileService : TileService() {
    
    companion object {
        private const val TAG = "KittTileService"
    }
    
    /**
     * Appelé quand la tuile est ajoutée au Quick Settings
     */
    override fun onTileAdded() {
        super.onTileAdded()
        Log.i(TAG, "✅ KITT Tile added to Quick Settings")
    }
    
    /**
     * Appelé quand la tuile devient visible
     */
    override fun onStartListening() {
        super.onStartListening()
        
        // Toujours en état "inactive" (pas de toggle)
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "KITT"
            contentDescription = "Activer KITT pour une commande vocale"
            updateTile()
        }
        
        Log.d(TAG, "KITT Tile visible")
    }
    
    /**
     * Appelé quand l'utilisateur tap sur la tuile
     * 
     * ⭐ Solution officielle Android: Utiliser PendingIntent + startActivityAndCollapse()
     */
    override fun onClick() {
        super.onClick()
        
        Log.i(TAG, "🎤 KITT Tile tapped - Activating KITT")
        
        // Animation visuelle rapide
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }
        
        try {
            // Créer un Intent pour VoiceListenerActivity
            val intent = Intent(this, com.chatai.activities.VoiceListenerActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
            
            // ⭐ Créer un PendingIntent (méthode officielle pour TileService)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Lancer avec startActivityAndCollapse() + PendingIntent
            // Cela déverrouille l'écran si nécessaire ET ferme le panneau Quick Settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+)
                startActivityAndCollapse(pendingIntent)
            } else {
                // Android 13 et moins
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            
            Log.i(TAG, "✅ VoiceListenerActivity launched via PendingIntent")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error launching VoiceListenerActivity", e)
        }
        
        // Remettre en état inactif après 500ms
        qsTile?.apply {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                state = Tile.STATE_INACTIVE
                updateTile()
            }, 500)
        }
        
        Log.i(TAG, "✅ KITT activation initiated from Quick Settings Tile")
    }
    
    /**
     * Appelé quand la tuile est retirée du Quick Settings
     */
    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.i(TAG, "❌ KITT Tile removed from Quick Settings")
    }
}

