package com.chatai.fragments

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.chatai.R
import java.text.DecimalFormat

/**
 * Fragment drawer pour le menu des commandes KITT
 * Affiche toutes les commandes disponibles avec des boutons
 */
class KittDrawerFragment : Fragment() {
    
    private var commandListener: CommandListener? = null
    private lateinit var sharedPreferences: SharedPreferences
    
    interface CommandListener {
        fun onCommandSelected(command: String)
        fun onCloseDrawer()
        fun onConfigurationCenterRequested() // Centre de configuration
        fun onWebServerRequested() // Serveur web local
        fun onWebServerConfigRequested() // Configuration WebServer (port 8888)
        fun onEndpointsListRequested() // Liste des endpoints API
        fun onHtmlExplorerRequested() // Explorateur HTML
        fun onThemeChanged(theme: String) // Changement de thème
        fun onButtonPressed(buttonName: String) // Annonce vocale du bouton pressé
        fun onAnimationModeChanged(mode: String) // Changement de mode d'animation VU-meter
        fun onPersonalityChanged(personality: String) // Changement de personnalité (KITT/GLaDOS)
    }
    
    fun setCommandListener(listener: CommandListener) {
        this.commandListener = listener
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kitt_drawer, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = requireContext().getSharedPreferences("kitt_prefs", Context.MODE_PRIVATE)
        setupButtons(view)
        setupThemeToggle(view)
        updateThemeButtons(view)
        applySelectedTheme(view)
        
        // Intercepter le bouton back pour fermer le drawer au lieu de l'app
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                commandListener?.onCloseDrawer()
                true
            } else {
                false
            }
        }
    }
    
    // Méthode publique pour forcer la mise à jour du thème
    fun refreshTheme() {
        view?.let { 
            updateThemeButtons(it)
            applySelectedTheme(it) 
        }
    }
    
    fun updateAnimationModeButtons(originalSelected: Boolean) {
        view?.let { view ->
            val originalButton = view.findViewById<MaterialButton>(R.id.animationOriginalButton)
            val dualButton = view.findViewById<MaterialButton>(R.id.animationDualButton)
            val currentTheme = getCurrentTheme()
            
            // Couleurs selon le thème actuel
            val (primaryColor, primaryAlpha, textColor) = when (currentTheme) {
                "red" -> Triple(R.color.kitt_red, R.color.kitt_red_alpha, R.color.kitt_red)
                "dark" -> Triple(R.color.dark_gray_light, R.color.dark_gray_medium, R.color.dark_white)
                "amber" -> Triple(R.color.amber_primary, R.color.amber_primary_light, R.color.amber_primary)
                else -> Triple(R.color.kitt_red, R.color.kitt_red_alpha, R.color.kitt_red)
            }
            
            // Réinitialiser les deux boutons au style de base
            originalButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
            originalButton.setTextColor(ContextCompat.getColor(requireContext(), textColor))
            originalButton.setStrokeColorResource(primaryColor)
            
            dualButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
            dualButton.setTextColor(ContextCompat.getColor(requireContext(), textColor))
            dualButton.setStrokeColorResource(primaryColor)
            
            if (originalSelected) {
                // ORIGINAL sélectionné - ajouter une couche transparente plus foncée
                originalButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryAlpha))
                originalButton.text = "✓ ANIMATION\nORIGINAL"
                dualButton.text = "ANIMATION\nDUAL"
            } else {
                // DUAL sélectionné - ajouter une couche transparente plus foncée
                dualButton.setBackgroundColor(ContextCompat.getColor(requireContext(), primaryAlpha))
                originalButton.text = "ANIMATION\nORIGINAL"
                dualButton.text = "✓ ANIMATION\nDUAL"
            }
        }
    }
    
    
    private fun setupButtons(view: View) {
        // Bouton fermer
        view.findViewById<MaterialButton>(R.id.closeDrawerButton).setOnClickListener {
            commandListener?.onCloseDrawer()
        }
        
        // Commandes de base
        view.findViewById<MaterialButton>(R.id.activateKittButton).setOnClickListener {
            commandListener?.onButtonPressed("Activation vocale")
            try {
                val intent = Intent(requireContext(), com.chatai.activities.VoiceListenerActivity::class.java)
                startActivity(intent)
                commandListener?.onCloseDrawer()
            } catch (e: Exception) {
                android.util.Log.e("KittDrawer", "Erreur ouverture VoiceListenerActivity: ${e.message}")
            }
        }
        
        view.findViewById<MaterialButton>(R.id.systemStatusButton).setOnClickListener {
            commandListener?.onButtonPressed("Informations système")
            showSystemInfoDialog()
        }
        
        view.findViewById<MaterialButton>(R.id.activateScannerButton).setOnClickListener {
            commandListener?.onButtonPressed("Scanner QR")
            showQRScannerInfo()
        }
        
        // Mode affichage - Animation VU-meter
        view.findViewById<MaterialButton>(R.id.animationOriginalButton).setOnClickListener {
            commandListener?.onButtonPressed("Animation VU-meter originale")
            commandListener?.onAnimationModeChanged("ORIGINAL")
            updateAnimationModeButtons(true)
        }
        
        view.findViewById<MaterialButton>(R.id.animationDualButton).setOnClickListener {
            commandListener?.onButtonPressed("Animation VU-meter dual")
            commandListener?.onAnimationModeChanged("DUAL")
            updateAnimationModeButtons(false)
        }
        
        // Analyse et surveillance
        view.findViewById<MaterialButton>(R.id.environmentalAnalysisButton).setOnClickListener {
            commandListener?.onButtonPressed("Capteurs device")
            showSensorsDialog()
        }
        
        view.findViewById<MaterialButton>(R.id.surveillanceModeButton).setOnClickListener {
            commandListener?.onButtonPressed("Mode Ne Pas Déranger")
            toggleDoNotDisturbMode()
        }
        
        view.findViewById<MaterialButton>(R.id.emergencyModeButton).setOnClickListener {
            commandListener?.onButtonPressed("Contacts d'urgence")
            openEmergencyContacts()
        }
        
        // Navigation
        view.findViewById<MaterialButton>(R.id.gpsActivationButton).setOnClickListener {
            commandListener?.onButtonPressed("Ouverture Google Maps")
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q="))
                startActivity(intent)
                commandListener?.onCloseDrawer()
            } catch (e: Exception) {
                android.util.Log.e("KittDrawer", "Erreur ouverture Google Maps: ${e.message}")
            }
        }
        
        view.findViewById<MaterialButton>(R.id.calculateRouteButton).setOnClickListener {
            commandListener?.onButtonPressed("Partage de position")
            try {
                // Get current location would require LocationManager - simplified for now
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Ma position GPS (fonctionnalité en développement)")
                }
                startActivity(Intent.createChooser(intent, "Partager ma position"))
                commandListener?.onCloseDrawer()
            } catch (e: Exception) {
                android.util.Log.e("KittDrawer", "Erreur partage position: ${e.message}")
            }
        }
        
        view.findViewById<MaterialButton>(R.id.setDestinationButton).setOnClickListener {
            commandListener?.onButtonPressed("Recherche navigation")
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))
                startActivity(intent)
                commandListener?.onCloseDrawer()
            } catch (e: Exception) {
                android.util.Log.e("KittDrawer", "Erreur recherche Maps: ${e.message}")
            }
        }
        
        // Communication
        view.findViewById<MaterialButton>(R.id.openCommunicationButton).setOnClickListener {
            commandListener?.onButtonPressed("Ouverture contacts")
            try {
                val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
                startActivity(intent)
                commandListener?.onCloseDrawer()
            } catch (e: Exception) {
                android.util.Log.e("KittDrawer", "Erreur ouverture Contacts: ${e.message}")
            }
        }
        
        view.findViewById<MaterialButton>(R.id.setFrequencyButton).setOnClickListener {
            commandListener?.onButtonPressed("Réglages audio")
            try {
                val intent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                startActivity(intent)
                commandListener?.onCloseDrawer()
            } catch (e: Exception) {
                android.util.Log.e("KittDrawer", "Erreur ouverture réglages audio: ${e.message}")
            }
        }
        
        view.findViewById<MaterialButton>(R.id.transmitMessageButton).setOnClickListener {
            commandListener?.onButtonPressed("Partage rapide")
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "")
                }
                startActivity(Intent.createChooser(intent, "Partager via"))
                commandListener?.onCloseDrawer()
            } catch (e: Exception) {
                android.util.Log.e("KittDrawer", "Erreur partage: ${e.message}")
            }
        }
        
        // Performance
        view.findViewById<MaterialButton>(R.id.turboBoostButton).setOnClickListener {
            commandListener?.onButtonPressed("Turbo boost")
            commandListener?.onCommandSelected("TURBO_BOOST")
        }
        
        view.findViewById<MaterialButton>(R.id.pursuitModeButton).setOnClickListener {
            commandListener?.onButtonPressed("Mode poursuite")
            commandListener?.onCommandSelected("PURSUIT_MODE")
        }
        
        view.findViewById<MaterialButton>(R.id.deactivateKittButton).setOnClickListener {
            commandListener?.onButtonPressed("Désactivation de KITT")
            commandListener?.onCommandSelected("DEACTIVATE_KITT")
        }
        
        // Personnalité IA
        view.findViewById<MaterialButton>(R.id.personalityKittButton).setOnClickListener {
            commandListener?.onButtonPressed("Personnalité KITT professionnelle activée")
            commandListener?.onPersonalityChanged("KITT")
        }
        
        view.findViewById<MaterialButton>(R.id.personalityGladosButton).setOnClickListener {
            commandListener?.onButtonPressed("Personnalité GLaDOS sarcastique activée")
            commandListener?.onPersonalityChanged("GLaDOS")
        }
        
        view.findViewById<MaterialButton>(R.id.personalityKarrButton).setOnClickListener {
            commandListener?.onButtonPressed("Personnalité KARR dominante activée")
            commandListener?.onPersonalityChanged("KARR")
        }
        
            // AI Configuration button
            view.findViewById<MaterialButton>(R.id.btnAIConfig).setOnClickListener {
                commandListener?.onButtonPressed("Configuration IA")
                commandListener?.onConfigurationCenterRequested()
            }
            
            // API Diagnostic button → AIConfigurationActivity
            view.findViewById<MaterialButton>(R.id.btnAPITest).setOnClickListener {
                commandListener?.onButtonPressed("Diagnostic API")
                try {
                    val intent = android.content.Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                    intent.putExtra("AUTO_TEST", true) // Trigger automatic API test
                    startActivity(intent)
                    commandListener?.onCloseDrawer()
                } catch (e: Exception) {
                    android.util.Log.e("KittDrawer", "Erreur ouverture AIConfigurationActivity: ${e.message}")
                }
            }
            
            // Music button
            view.findViewById<MaterialButton>(R.id.musicButton).setOnClickListener {
                android.util.Log.d("Music", "Bouton musique cliqué dans le drawer")
                commandListener?.onButtonPressed("Musique")
                commandListener?.onCommandSelected("TOGGLE_MUSIC")
                android.util.Log.d("Music", "Commande TOGGLE_MUSIC envoyée")
            }
            
            // Games button
            view.findViewById<MaterialButton>(R.id.gamesButton).setOnClickListener {
                android.util.Log.d("Games", "Bouton jeux cliqué dans le drawer")
                commandListener?.onButtonPressed("Jeux")
                // Ouvrir la WebView RelaxWebViewActivity
                val intent = android.content.Intent(requireContext(), com.chatai.activities.RelaxWebViewActivity::class.java)
                startActivity(intent)
                android.util.Log.d("Games", "Ouverture de RelaxWebViewActivity")
            }
            
            // Games button 2
            view.findViewById<MaterialButton>(R.id.gamesLibraryButton).setOnClickListener {
                android.util.Log.d("Games", "Bouton bibliothèque NES cliqué dans le drawer")
                commandListener?.onButtonPressed("Bibliothèque NES")
                // Ouvrir la bibliothèque de jeux NES
                val intent = android.content.Intent(requireContext(), com.chatai.GameListActivity::class.java)
                startActivity(intent)
                android.util.Log.d("Games", "Ouverture de GameListActivity")
            }
            
            // Server Monitoring button → ServerActivity
            view.findViewById<MaterialButton>(R.id.btnWebServer).setOnClickListener {
                commandListener?.onButtonPressed("Monitoring serveurs")
                try {
                    val intent = android.content.Intent(requireContext(), com.chatai.ServerActivity::class.java)
                    startActivity(intent)
                    commandListener?.onCloseDrawer()
                } catch (e: Exception) {
                    android.util.Log.e("KittDrawer", "Erreur ouverture ServerActivity: ${e.message}")
                }
            }
            
            // Server Configuration button → ServerConfigurationActivity
            view.findViewById<MaterialButton>(R.id.btnWebServerConfig).setOnClickListener {
                commandListener?.onButtonPressed("Configuration serveurs")
                try {
                    val intent = android.content.Intent(requireContext(), com.chatai.activities.ServerConfigurationActivity::class.java)
                    startActivity(intent)
                    commandListener?.onCloseDrawer()
                } catch (e: Exception) {
                    android.util.Log.e("KittDrawer", "Erreur ouverture ServerConfigurationActivity: ${e.message}")
                }
            }
            
            // Endpoints List button → EndpointsListActivity
            view.findViewById<MaterialButton>(R.id.btnEndpointsList).setOnClickListener {
                commandListener?.onButtonPressed("Endpoints API")
                try {
                    val intent = android.content.Intent(requireContext(), com.chatai.activities.EndpointsListActivity::class.java)
                    startActivity(intent)
                    commandListener?.onCloseDrawer()
                } catch (e: Exception) {
                    android.util.Log.e("KittDrawer", "Erreur ouverture EndpointsListActivity: ${e.message}")
                }
            }
            
            // Conversation History button → ConversationHistoryActivity
            view.findViewById<MaterialButton>(R.id.btnHtmlExplorer).setOnClickListener {
                commandListener?.onButtonPressed("Historique conversations")
                try {
                    val intent = android.content.Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                    startActivity(intent)
                    commandListener?.onCloseDrawer()
                } catch (e: Exception) {
                    android.util.Log.e("KittDrawer", "Erreur ouverture ConversationHistoryActivity: ${e.message}")
                }
            }
            
            // Theme toggle buttons
            view.findViewById<MaterialButton>(R.id.btnThemeRed).setOnClickListener {
                commandListener?.onButtonPressed("Thème rouge activé")
                saveTheme("red")
                commandListener?.onThemeChanged("red")
                updateThemeButtons(view)
            }
            
            view.findViewById<MaterialButton>(R.id.btnThemeDark).setOnClickListener {
                commandListener?.onButtonPressed("Thème sombre activé")
                saveTheme("dark")
                commandListener?.onThemeChanged("dark")
                updateThemeButtons(view)
            }
            
            view.findViewById<MaterialButton>(R.id.btnThemeAmber).setOnClickListener {
                commandListener?.onButtonPressed("Thème ambre activé")
                saveTheme("amber")
                commandListener?.onThemeChanged("amber")
                updateThemeButtons(view)
            }
    }
    
    private fun saveTheme(theme: String) {
        sharedPreferences.edit().putString("kitt_theme", theme).apply()
    }
    
    private fun setupThemeToggle(view: View) {
        val themeToggle = view.findViewById<SwitchMaterial>(R.id.themeToggleSwitch)
        val themeButtonsContainer = view.findViewById<LinearLayout>(R.id.themeButtonsContainer)
        
        // ⭐ NOUVEAU: Toggle thème persistant, OFF par défaut
        val isThemeEnabled = sharedPreferences.getBoolean("kitt_theme_enabled", false)
        themeToggle.isChecked = isThemeEnabled
        
        // Afficher/masquer les boutons selon l'état du toggle
        themeButtonsContainer.visibility = if (isThemeEnabled) View.VISIBLE else View.GONE
        
        // Si le thème est désactivé, appliquer le thème par défaut (rouge)
        if (!isThemeEnabled) {
            applyDefaultTheme(view)
        }
        
        themeToggle.setOnCheckedChangeListener { _, isChecked ->
            // Sauvegarder l'état du toggle
            sharedPreferences.edit().putBoolean("kitt_theme_enabled", isChecked).apply()
            
            // Afficher/masquer les boutons de thème
            themeButtonsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            
            if (isChecked) {
                // Si activé, appliquer le thème actuel
                applySelectedTheme(view)
            } else {
                // Si désactivé, appliquer le thème par défaut (rouge)
                applyDefaultTheme(view)
            }
        }
    }
    
    private fun applyDefaultTheme(view: View) {
        // Appliquer le thème rouge par défaut quand le toggle est OFF
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        // Les autres éléments gardent leur style par défaut (rouge)
    }
    
    private fun getCurrentTheme(): String {
        return sharedPreferences.getString("kitt_theme", "red") ?: "red"
    }
    
    private fun updateThemeButtons(view: View) {
        val currentTheme = getCurrentTheme()
        
        // Réinitialiser tous les boutons
        val redButton = view.findViewById<MaterialButton>(R.id.btnThemeRed)
        val darkButton = view.findViewById<MaterialButton>(R.id.btnThemeDark)
        val amberButton = view.findViewById<MaterialButton>(R.id.btnThemeAmber)
        
        // Styles par défaut (non sélectionnés) - chaque bouton garde ses couleurs de thème
        redButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        redButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        redButton.setStrokeColorResource(R.color.kitt_red)
        redButton.text = "🔴 ROUGE"
        
        darkButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        darkButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        darkButton.setStrokeColorResource(R.color.dark_gray_light)
        darkButton.text = "⚫ SOMBRE"
        
        amberButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        amberButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        amberButton.setStrokeColorResource(R.color.amber_primary)
        amberButton.text = "🟠 AMBRE"
        
        // Activer le bouton correspondant au thème actuel - ajouter une couche transparente
        when (currentTheme) {
            "red" -> {
                redButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_red_alpha))
                redButton.text = "✓ 🔴 ROUGE"
            }
            "dark" -> {
                darkButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_gray_medium))
                darkButton.text = "✓ ⚫ SOMBRE"
            }
            "amber" -> {
                amberButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.amber_primary_light))
                amberButton.text = "✓ 🟠 AMBRE"
            }
        }
    }
    
    
    private fun applySelectedTheme(view: View) {
        // ⭐ NOUVEAU: Vérifier si le thème est activé avant d'appliquer
        val isThemeEnabled = sharedPreferences.getBoolean("kitt_theme_enabled", false)
        if (!isThemeEnabled) {
            applyDefaultTheme(view)
            return
        }
        
        val selectedTheme = getCurrentTheme()
        
        when (selectedTheme) {
            "red" -> applyRedTheme(view)
            "dark" -> applyDarkTheme(view)
            "amber" -> applyAmberTheme(view)
        }
    }
    
    private fun applyRedTheme(view: View) {
        // Appliquer le thème rouge au drawer
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        
        // Appliquer aux textes
        val headerLayout = view.findViewById<LinearLayout>(R.id.drawerHeader)
        val headerText = headerLayout?.getChildAt(0) as? TextView
        headerText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        
        // Appliquer aux boutons
        val allButtons = listOf(
            R.id.closeDrawerButton, R.id.activateKittButton, R.id.systemStatusButton,
            R.id.activateScannerButton, R.id.environmentalAnalysisButton, R.id.surveillanceModeButton,
            R.id.emergencyModeButton, R.id.gpsActivationButton, R.id.calculateRouteButton,
            R.id.setDestinationButton, R.id.openCommunicationButton, R.id.setFrequencyButton,
            R.id.transmitMessageButton, R.id.turboBoostButton, R.id.pursuitModeButton,
            R.id.deactivateKittButton, R.id.btnAIConfig, R.id.musicButton, R.id.btnAPITest,
            R.id.btnWebServer, R.id.btnWebServerConfig, R.id.btnEndpointsList, R.id.btnHtmlExplorer
        )
        
        allButtons.forEach { buttonId ->
            val button = view.findViewById<MaterialButton>(buttonId)
            button?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
            button?.setStrokeColorResource(R.color.kitt_red)
        }
        
        // Appliquer le thème rouge aux boutons
        updateThemeButtons(view)
        // Mettre à jour les boutons de mode d'affichage avec le nouveau thème
        updateAnimationModeButtons(true) // ORIGINAL par défaut
    }
    
    private fun applyDarkTheme(view: View) {
        // Appliquer le thème sombre au drawer
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_black))
        
        // Appliquer aux textes
        val headerLayout = view.findViewById<LinearLayout>(R.id.drawerHeader)
        val headerText = headerLayout?.getChildAt(0) as? TextView
        headerText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        
        // Appliquer aux boutons
        val allButtons = listOf(
            R.id.closeDrawerButton, R.id.activateKittButton, R.id.systemStatusButton,
            R.id.activateScannerButton, R.id.environmentalAnalysisButton, R.id.surveillanceModeButton,
            R.id.emergencyModeButton, R.id.gpsActivationButton, R.id.calculateRouteButton,
            R.id.setDestinationButton, R.id.openCommunicationButton, R.id.setFrequencyButton,
            R.id.transmitMessageButton, R.id.turboBoostButton, R.id.pursuitModeButton,
            R.id.deactivateKittButton, R.id.btnAIConfig, R.id.musicButton, R.id.btnAPITest,
            R.id.btnWebServer, R.id.btnWebServerConfig, R.id.btnEndpointsList, R.id.btnHtmlExplorer
        )
        
        allButtons.forEach { buttonId ->
            val button = view.findViewById<MaterialButton>(buttonId)
            button?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
            button?.setStrokeColorResource(R.color.dark_gray_light)
        }
        
        // Appliquer le thème sombre aux boutons
        updateThemeButtons(view)
        // Mettre à jour les boutons de mode d'affichage avec le nouveau thème
        updateAnimationModeButtons(true) // ORIGINAL par défaut
    }
    
    private fun applyAmberTheme(view: View) {
        // Appliquer le thème ambre au drawer
        view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.amber_surface))
        
        // Appliquer aux textes
        val headerLayout = view.findViewById<LinearLayout>(R.id.drawerHeader)
        val headerText = headerLayout?.getChildAt(0) as? TextView
        headerText?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_on_surface))
        
        // Appliquer aux boutons
        val allButtons = listOf(
            R.id.closeDrawerButton, R.id.activateKittButton, R.id.systemStatusButton,
            R.id.activateScannerButton, R.id.environmentalAnalysisButton, R.id.surveillanceModeButton,
            R.id.emergencyModeButton, R.id.gpsActivationButton, R.id.calculateRouteButton,
            R.id.setDestinationButton, R.id.openCommunicationButton, R.id.setFrequencyButton,
            R.id.transmitMessageButton, R.id.turboBoostButton, R.id.pursuitModeButton,
            R.id.deactivateKittButton, R.id.btnAIConfig, R.id.musicButton, R.id.btnAPITest,
            R.id.btnWebServer, R.id.btnWebServerConfig, R.id.btnEndpointsList, R.id.btnHtmlExplorer
        )
        
        allButtons.forEach { buttonId ->
            val button = view.findViewById<MaterialButton>(buttonId)
            button?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
            button?.setStrokeColorResource(R.color.amber_primary)
        }
        
        // Appliquer le thème ambre aux boutons
        updateThemeButtons(view)
        // Mettre à jour les boutons de mode d'affichage avec le nouveau thème
        updateAnimationModeButtons(true) // ORIGINAL par défaut
    }
    
    /**
     * Affiche un dialog avec les vraies informations système du device
     */
    private fun showSystemInfoDialog() {
        val context = requireContext()
        
        // Batterie
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == BatteryManager.BATTERY_STATUS_FULL
        val chargingText = if (isCharging) "EN CHARGE" else "SUR BATTERIE"
        
        // RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem / (1024 * 1024) // MB
        val availRam = memInfo.availMem / (1024 * 1024) // MB
        val usedRam = totalRam - availRam
        
        // Stockage
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalStorage = statFs.blockCountLong * statFs.blockSizeLong / (1024 * 1024 * 1024) // GB
        val availStorage = statFs.availableBlocksLong * statFs.blockSizeLong / (1024 * 1024 * 1024) // GB
        val usedStorage = totalStorage - availStorage
        
        // Format
        val df = DecimalFormat("#.##")
        
        // Build message
        val message = """
            BATTERIE:
            Niveau: $batteryLevel%
            État: $chargingText
            
            MÉMOIRE RAM:
            Utilisée: ${usedRam}MB / ${totalRam}MB
            Disponible: ${availRam}MB
            
            STOCKAGE:
            Utilisé: ${df.format(usedStorage)}GB / ${df.format(totalStorage)}GB
            Disponible: ${df.format(availStorage)}GB
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("INFORMATIONS SYSTÈME")
            .setMessage(message)
            .setPositiveButton("FERMER") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    
    /**
     * Affiche informations sur les capteurs device
     */
    private fun showSensorsDialog() {
        val context = requireContext()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        
        val sensors = sensorManager.getSensorList(android.hardware.Sensor.TYPE_ALL)
        val sensorNames = sensors.joinToString("\n") { sensor ->
            "• ${sensor.name}"
        }
        
        val message = """
            CAPTEURS DÉTECTÉS: ${sensors.size}
            
            $sensorNames
        """.trimIndent()
        
        AlertDialog.Builder(context)
            .setTitle("CAPTEURS DEVICE")
            .setMessage(message)
            .setPositiveButton("FERMER") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    
    /**
     * Toggle mode Ne Pas Déranger (DND)
     */
    private fun toggleDoNotDisturbMode() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
            commandListener?.onCloseDrawer()
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext())
                .setTitle("MODE NE PAS DÉRANGER")
                .setMessage("Ouvrir les paramètres de notifications pour activer/désactiver le mode Ne Pas Déranger")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
    
    /**
     * Ouvrir contacts d'urgence
     */
    private fun openEmergencyContacts() {
        try {
            // Option 1: Ouvrir contacts favoris
            val intent = Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI)
            startActivity(intent)
            commandListener?.onCloseDrawer()
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext())
                .setTitle("CONTACTS D'URGENCE")
                .setMessage("Impossible d'ouvrir les contacts. Assurez-vous d'avoir l'app Contacts installée.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
    
    /**
     * Info scanner QR (fonctionnalité future)
     */
    private fun showQRScannerInfo() {
        AlertDialog.Builder(requireContext())
            .setTitle("SCANNER QR/BARCODE")
            .setMessage("Fonctionnalité en développement.\n\nLe scanner QR Code sera intégré dans une future version avec ZXing ou ML Kit.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
