package com.chatai.managers

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.chatai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

/**
 * 🔄 KITT STATE MANAGER V3
 * 
 * ⚠️⚠️⚠️ CODE COPIÉ À 100% DE V1 - AUCUNE SIMPLIFICATION ⚠️⚠️⚠️
 * 
 * Ce manager gère TOUS les états système de KITT:
 * - 6 états système (isReady, isListening, isThinking, isSpeaking, isTTSSpeaking, isChatMode)
 * - Mise à jour des voyants (BSY, RDY, NET, MSQ)
 * - Mise à jour des boutons (couleurs, enabled/disabled)
 * - Transitions d'états
 * 
 * RESPONSABILITÉS:
 * 1. Gérer les 6 états système
 * 2. Mettre à jour les voyants selon les états
 * 3. Activer/désactiver les boutons
 * 4. Appliquer les couleurs (rouge vif / rouge sombre)
 * 5. Valider les transitions d'états
 * 
 * RÈGLES ABSOLUES:
 * - updateStatusIndicators() est CRITIQUE (logique complexe)
 * - BSY actif = speaking OR thinking OR ttsSpeaking OR listening
 * - RDY actif = ready AND NOT busy
 * - MSQ actif = musicPlaying
 * - setButtonsState() applique couleurs selon ON/OFF
 */
class KittStateManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "KittStateManager"
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ÉTATS SYSTÈME (COPIÉS DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    var isReady = false
    var isListening = false
    var isThinking = false
    var isSpeaking = false
    var isTTSSpeaking = false
    var isChatMode = false
    var isPersistentMode = false
    var isKittActive = false
    
    // États additionnels
    var isMusicPlaying = false
    
    // ════════════════════════════════════════════════════════════════════════
    // MISE À JOUR INDICATEURS (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * ⭐⭐⭐ FONCTION TRÈS CRITIQUE - Met à jour les voyants BSY/RDY/NET/MSQ
     * 
     * LOGIQUE COMPLEXE:
     * - BSY actif quand: isSpeaking OR isThinking OR isTTSSpeaking OR isListening
     * - RDY actif quand: isReady AND NOT isBusy
     * - NET géré par thinking animation (pas ici)
     * - MSQ actif quand: isMusicPlaying
     * 
     * Couleurs:
     * - Actif: fond rouge vif (kitt_status_background_active), texte noir
     * - Inactif: fond rouge sombre (kitt_status_background), texte rouge sombre
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS MODIFIER ⚠️⚠️⚠️
     */
    fun updateStatusIndicators(
        statusBarIndicatorBSY: MaterialTextView,
        statusBarIndicatorRDY: MaterialTextView,
        statusBarIndicatorMSQ: MaterialTextView
    ) {
        // ⚠️ Logique pour BSY : actif quand l'IA ou KITT travaille
        val isBusy = isSpeaking || isThinking || isTTSSpeaking || isListening
        
        // ⚠️ Logique pour RDY : s'éteint quand KITT est en incapacité
        val isReadyIndicator = isReady && !isBusy
        
        // Mettre à jour BSY
        if (isBusy) {
            // BSY actif : fond rouge vif, contour rouge vif, texte noir (comme bouton actif)
            statusBarIndicatorBSY.setBackgroundResource(R.drawable.kitt_status_background_active)
            statusBarIndicatorBSY.setTextColor(ContextCompat.getColor(context, R.color.kitt_black))
        } else {
            // BSY inactif : fond rouge sombre, contour rouge sombre, texte rouge sombre (comme bouton inactif)
            statusBarIndicatorBSY.setBackgroundResource(R.drawable.kitt_status_background)
            statusBarIndicatorBSY.setTextColor(ContextCompat.getColor(context, R.color.kitt_red_dark))
        }
        
        // Mettre à jour RDY
        if (isReadyIndicator) {
            // RDY actif : fond rouge vif, contour rouge vif, texte noir (comme bouton actif)
            statusBarIndicatorRDY.setBackgroundResource(R.drawable.kitt_status_background_active)
            statusBarIndicatorRDY.setTextColor(ContextCompat.getColor(context, R.color.kitt_black))
        } else {
            // RDY inactif : fond rouge sombre, contour rouge sombre, texte rouge sombre (comme bouton inactif)
            statusBarIndicatorRDY.setBackgroundResource(R.drawable.kitt_status_background)
            statusBarIndicatorRDY.setTextColor(ContextCompat.getColor(context, R.color.kitt_red_dark))
        }
        
        // Mettre à jour MSQ : actif quand la musique joue
        if (isMusicPlaying) {
            // MSQ actif : fond rouge vif, contour rouge vif, texte noir (comme bouton actif)
            statusBarIndicatorMSQ.setBackgroundResource(R.drawable.kitt_status_background_active)
            statusBarIndicatorMSQ.setTextColor(ContextCompat.getColor(context, R.color.kitt_black))
        } else {
            // MSQ inactif : fond rouge sombre, contour rouge sombre, texte rouge sombre (comme bouton inactif)
            statusBarIndicatorMSQ.setBackgroundResource(R.drawable.kitt_status_background)
            statusBarIndicatorMSQ.setTextColor(ContextCompat.getColor(context, R.color.kitt_red_dark))
        }
    }
    
    /**
     * ⭐ FONCTION CRITIQUE - Appliquer couleurs boutons selon état ON/OFF
     * 
     * Mode ON (KITT actif):
     * - Boutons: Texte rouge vif, contours rouge vif
     * - Indicateurs: Fond rouge vif, texte noir
     * 
     * Mode OFF (KITT standby):
     * - Boutons: Texte rouge sombre, contours rouge sombre
     * - Indicateurs: Fond rouge sombre, texte rouge sombre
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER ⚠️⚠️⚠️
     */
    fun setButtonsState(
        isOn: Boolean,
        view: View,
        allButtons: List<Int>,
        statusBarIndicators: List<Int>
    ) {
        val textColor = if (isOn) {
            ContextCompat.getColor(context, R.color.kitt_red)
        } else {
            ContextCompat.getColor(context, R.color.kitt_red_dark)
        }
        
        val strokeColor = if (isOn) {
            ContextCompat.getColor(context, R.color.kitt_red)
        } else {
            ContextCompat.getColor(context, R.color.kitt_red_dark)
        }
        
        // Appliquer aux MaterialButton
        allButtons.forEach { buttonId ->
            val button = view.findViewById<MaterialButton>(buttonId)
            button?.let {
                it.setTextColor(textColor)
                it.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor))
            }
        }
        
        // Appliquer aux Status Bar Indicators (voyants de status)
        statusBarIndicators.forEach { textViewId ->
            val textView = view.findViewById<MaterialTextView>(textViewId)
            textView?.let {
                if (isOn) {
                    // Mode ON : fond rouge, texte noir
                    it.setTextColor(ContextCompat.getColor(context, R.color.kitt_black))
                    // Tous utilisent maintenant le même drawable
                    it.setBackgroundResource(R.drawable.kitt_status_background_active)
                } else {
                    // Mode OFF : fond transparent, texte rouge foncé
                    it.setTextColor(ContextCompat.getColor(context, R.color.kitt_red_dark))
                    // Tous utilisent maintenant le même drawable
                    it.setBackgroundResource(R.drawable.kitt_status_background)
                }
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // TRANSITIONS D'ÉTATS (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Mode Ready (KITT activé)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun setReadyMode() {
        isReady = true
        isKittActive = true
        android.util.Log.d(TAG, "✅ KITT set to READY mode")
    }
    
    /**
     * Mode Standby (KITT désactivé)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun setStandbyMode() {
        isReady = false
        isKittActive = false
        isListening = false
        isThinking = false
        isSpeaking = false
        isChatMode = false
        android.util.Log.d(TAG, "⏸️ KITT set to STANDBY mode")
    }
    
    /**
     * Reset tous les états (sauf isReady)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun resetStates() {
        isListening = false
        isThinking = false
        isSpeaking = false
        isChatMode = false
        android.util.Log.d(TAG, "🔄 States reset (isReady preserved)")
    }
    
    /**
     * Vérifier si KITT est occupé
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun isBusy(): Boolean = isSpeaking || isThinking || isTTSSpeaking || isListening
    
    /**
     * Toggle mode persistant
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun togglePersistentMode(): Boolean {
        isPersistentMode = !isPersistentMode
        android.util.Log.d(TAG, "Persistent mode: $isPersistentMode")
        return isPersistentMode
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // ÉTATS (GETTERS)
    // ════════════════════════════════════════════════════════════════════════
    
    fun isKittReady(): Boolean = isReady
    fun isKittListening(): Boolean = isListening
    fun isKittThinking(): Boolean = isThinking
    fun isKittSpeaking(): Boolean = isSpeaking
    fun isKittTTSSpeaking(): Boolean = isTTSSpeaking
    fun isKittInChatMode(): Boolean = isChatMode
    fun isKittPersistent(): Boolean = isPersistentMode
    fun isKittActivated(): Boolean = isKittActive
    fun isKittMusicPlaying(): Boolean = isMusicPlaying
}

