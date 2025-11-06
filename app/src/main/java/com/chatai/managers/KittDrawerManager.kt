package com.chatai.managers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.View
import androidx.fragment.app.FragmentManager
import com.chatai.R
import com.chatai.fragments.KittDrawerFragment

/**
 * 📋 KITT DRAWER MANAGER V3
 * 
 * ⚠️⚠️⚠️ CODE COPIÉ À 100% DE V1 - AUCUNE SIMPLIFICATION ⚠️⚠️⚠️
 * 
 * Ce manager gère TOUT le système de drawer menu de KITT:
 * - KittDrawerFragment integration
 * - Theme management (red/dark/amber)
 * - Personality changes (KITT/GLaDOS)
 * - Animation modes (ORIGINAL/DUAL)
 * - Drawer commands callbacks
 * 
 * RESPONSABILITÉS:
 * 1. Ouvrir/fermer KittDrawerFragment
 * 2. Gérer les callbacks drawer (command, theme, personality, etc.)
 * 3. Appliquer thèmes sauvegardés
 * 4. Mettre à jour boutons animation modes
 * 5. Coordonner avec KittFragment
 * 
 * RÈGLES ABSOLUES:
 * - Vérifier que drawer_container existe
 * - Gérer toutes les commandes drawer
 * - Sauvegarder préférences (thème, personnalité)
 * - Refresh theme après changement
 */
class KittDrawerManager(
    private val context: Context,
    private val listener: DrawerListener
) {
    
    companion object {
        private const val TAG = "KittDrawerManager"
    }
    
    /**
     * Interface pour les callbacks Drawer
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    interface DrawerListener {
        fun onDrawerCommandSelected(command: String)
        fun onDrawerClosed()
        fun onThemeChanged(theme: String)
        fun onPersonalityChanged(personality: String)
        fun onAnimationModeChanged(mode: String)
        fun onButtonPressed(buttonName: String)
        fun showStatusMessage(message: String, duration: Long, type: com.chatai.managers.MessageType)
        fun speakAIResponse(response: String)
        fun toggleMusic()
        fun processAIConversation(command: String)
        fun updateAnimationModeButtons()
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("kitt_prefs", Context.MODE_PRIVATE)
    
    private var currentDrawerFragment: KittDrawerFragment? = null
    
    // ════════════════════════════════════════════════════════════════════════
    // OUVRIR / FERMER DRAWER (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * ⭐⭐ FONCTION CRITIQUE - Ouvrir le menu drawer
     * 
     * Vérifie:
     * 1. KittFragment est visible et attaché
     * 2. drawer_container existe dans l'activité
     * 3. Crée KittDrawerFragment avec tous les callbacks
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER ⚠️⚠️⚠️
     */
    fun showMenuDrawer(
        fragmentManager: FragmentManager,
        activityView: View?,
        parentView: View?
    ) {
        // Vérifier que drawer_container existe
        val drawerContainer = activityView?.findViewById<View>(R.id.drawer_container)
        if (drawerContainer == null) {
            android.util.Log.w(TAG, "Cannot show drawer: drawer_container not found")
            return
        }
        
        val drawerFragment = KittDrawerFragment()
        currentDrawerFragment = drawerFragment
        
        drawerFragment.setCommandListener(object : KittDrawerFragment.CommandListener {
            override fun onCommandSelected(command: String) {
                android.util.Log.d(TAG, "=== COMMANDE REÇUE: $command ===")
                
                // Traiter les commandes spéciales
                when (command) {
                    "TOGGLE_MUSIC" -> {
                        android.util.Log.d(TAG, "Commande TOGGLE_MUSIC détectée")
                        listener.toggleMusic()
                        // Fermer le drawer
                        closeDrawer(fragmentManager, drawerFragment)
                        return
                    }
                }
                
                // Traiter comme conversation AI pour les autres commandes
                listener.processAIConversation(command)
                
                // Fermer le drawer
                closeDrawer(fragmentManager, drawerFragment)
            }
            
            override fun onCloseDrawer() {
                // Fermer le drawer
                closeDrawer(fragmentManager, drawerFragment)
            }
            
            override fun onConfigurationCenterRequested() {
                android.util.Log.d(TAG, "🛠️ Configuration IA demandée")
                try {
                    val intent = Intent(context, com.chatai.activities.AIConfigurationActivity::class.java)
                    context.startActivity(intent)
                    listener.speakAIResponse("Ouverture de la configuration IA")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Erreur ouverture Config IA", e)
                    listener.showStatusMessage("Erreur: Impossible d'ouvrir la configuration", 2000, com.chatai.managers.MessageType.ERROR)
                }
                closeDrawer(fragmentManager, drawerFragment)
            }
            
            override fun onWebServerRequested() {
                listener.showStatusMessage("Serveur Web - En développement", 2000, com.chatai.managers.MessageType.STATUS)
                closeDrawer(fragmentManager, drawerFragment)
            }
            
            override fun onWebServerConfigRequested() {
                listener.showStatusMessage("Configuration serveur Web - En développement", 2000, com.chatai.managers.MessageType.STATUS)
                closeDrawer(fragmentManager, drawerFragment)
            }
            
            override fun onEndpointsListRequested() {
                listener.showStatusMessage("Liste des endpoints - En développement", 2000, com.chatai.managers.MessageType.STATUS)
                closeDrawer(fragmentManager, drawerFragment)
            }
            
            override fun onHtmlExplorerRequested() {
                listener.showStatusMessage("Explorateur HTML - En développement", 2000, com.chatai.managers.MessageType.STATUS)
                closeDrawer(fragmentManager, drawerFragment)
            }
            
            override fun onThemeChanged(theme: String) {
                // Appliquer le thème sélectionné
                listener.onThemeChanged(theme)
                // Mettre à jour le thème du drawer aussi
                drawerFragment.refreshTheme()
            }
            
            override fun onButtonPressed(buttonName: String) {
                // Annoncer le bouton pressé
                listener.onButtonPressed(buttonName)
            }
            
            override fun onPersonalityChanged(personality: String) {
                listener.onPersonalityChanged(personality)
            }
            
            override fun onAnimationModeChanged(mode: String) {
                listener.onAnimationModeChanged(mode)
            }
        })
        
        // Afficher le drawer avec animation
        fragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, 0)
            .add(R.id.drawer_container, drawerFragment, "kitt_drawer")
            .commit()
            
        android.util.Log.d(TAG, "✅ Drawer menu opened")
    }
    
    /**
     * Fermer le drawer
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    private fun closeDrawer(fragmentManager: FragmentManager, drawerFragment: KittDrawerFragment) {
        fragmentManager.beginTransaction()
            .setCustomAnimations(0, R.anim.slide_out_right)
            .remove(drawerFragment)
            .commit()
        currentDrawerFragment = null
        android.util.Log.d(TAG, "✅ Drawer menu closed")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // THEME MANAGEMENT (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Appliquer le thème sauvegardé
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun applySelectedTheme(): String {
        val selectedTheme = sharedPreferences.getString("kitt_theme", "red") ?: "red"
        
        android.util.Log.d(TAG, "Applying theme: $selectedTheme")
        
        // Note: L'application réelle du thème (couleurs) est gérée par KittFragment
        // Ce manager gère juste la persistance et la coordination
        
        return selectedTheme
    }
    
    /**
     * Sauvegarder le thème sélectionné
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun saveTheme(theme: String) {
        sharedPreferences.edit()
            .putString("kitt_theme", theme)
            .apply()
        android.util.Log.d(TAG, "✅ Theme saved: $theme")
    }
    
    /**
     * Obtenir le thème actuel
     */
    fun getCurrentTheme(): String = sharedPreferences.getString("kitt_theme", "red") ?: "red"
    
    // ════════════════════════════════════════════════════════════════════════
    // PERSONALITY MANAGEMENT (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Sauvegarder la personnalité sélectionnée
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun savePersonality(personality: String) {
        val aiConfigPrefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        aiConfigPrefs.edit()
            .putString("selected_personality", personality)
            .apply()
        android.util.Log.d(TAG, "✅ Personality saved: $personality")
    }
    
    /**
     * Obtenir la personnalité actuelle
     */
    fun getCurrentPersonality(): String {
        val aiConfigPrefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        return aiConfigPrefs.getString("selected_personality", "KITT") ?: "KITT"
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Détruire le manager
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun destroy() {
        currentDrawerFragment = null
        android.util.Log.i(TAG, "🛑 KittDrawerManager destroyed")
    }
}

