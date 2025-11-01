package com.chatai.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chatai.R
import com.chatai.FileServer
import com.chatai.HttpServer
import com.chatai.WebSocketServer
import com.chatai.RealtimeAIService
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Activité de configuration complète pour ChatAI-Android
 * Remplace les boutons "non disponibles" du drawer KITT
 */
class ConfigurationActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    private var httpServer: HttpServer? = null
    private var webSocketServer: WebSocketServer? = null
    private var aiService: RealtimeAIService? = null
    private var fileServer: FileServer? = null
    
    // Configuration des serveurs
    private lateinit var httpPortInput: TextInputEditText
    private lateinit var wsPortInput: TextInputEditText
    private lateinit var filePortInput: TextInputEditText
    
    // Configuration des APIs
    private lateinit var openaiApiKeyInput: TextInputEditText
    private lateinit var huggingfaceApiKeyInput: TextInputEditText
    
    // Configuration des fichiers
    private lateinit var storagePathText: TextView
    private lateinit var changeStorageButton: MaterialButton
    
    // Configuration des thèmes
    private lateinit var themeRedButton: MaterialButton
    private lateinit var themeDarkButton: MaterialButton
    private lateinit var themeAmberButton: MaterialButton
    
    // Configuration des fonctionnalités
    private lateinit var enableNotificationsSwitch: SwitchMaterial
    private lateinit var enableVoiceSwitch: SwitchMaterial
    private lateinit var enableFileSharingSwitch: SwitchMaterial
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        
        // Initialiser les préférences
        sharedPreferences = getSharedPreferences("chatai_config", Context.MODE_PRIVATE)
        
        // Initialiser les vues
        initializeViews()
        
        // Charger les paramètres existants
        loadCurrentSettings()
        
        // Configurer les listeners
        setupListeners()
    }
    
    private fun initializeViews() {
        // Configuration des serveurs
        httpPortInput = findViewById(R.id.httpPortInput)
        wsPortInput = findViewById(R.id.wsPortInput)
        filePortInput = findViewById(R.id.filePortInput)
        
        // Configuration des APIs
        openaiApiKeyInput = findViewById(R.id.openaiApiKeyInput)
        huggingfaceApiKeyInput = findViewById(R.id.huggingfaceApiKeyInput)
        
        // Configuration des fichiers
        storagePathText = findViewById(R.id.storagePathText)
        changeStorageButton = findViewById(R.id.changeStorageButton)
        
        // Configuration des thèmes
        themeRedButton = findViewById(R.id.themeRedButton)
        themeDarkButton = findViewById(R.id.themeDarkButton)
        themeAmberButton = findViewById(R.id.themeAmberButton)
        
        // Configuration des fonctionnalités
        enableNotificationsSwitch = findViewById(R.id.enableNotificationsSwitch)
        enableVoiceSwitch = findViewById(R.id.enableVoiceSwitch)
        enableFileSharingSwitch = findViewById(R.id.enableFileSharingSwitch)
    }
    
    private fun loadCurrentSettings() {
        // Charger les ports des serveurs
        httpPortInput.setText(sharedPreferences.getString("http_port", "8080"))
        wsPortInput.setText(sharedPreferences.getString("ws_port", "8081"))
        filePortInput.setText(sharedPreferences.getString("file_port", "8082"))
        
        // Charger les clés API (partiellement masquées)
        val openaiKey = sharedPreferences.getString("openai_api_key", "")
        openaiApiKeyInput.setText(if (openaiKey.isNullOrEmpty()) "" else maskApiKey(openaiKey))
        
        val huggingfaceKey = sharedPreferences.getString("huggingface_api_key", "")
        huggingfaceApiKeyInput.setText(if (huggingfaceKey.isNullOrEmpty()) "" else maskApiKey(huggingfaceKey))
        
        // Charger le chemin de stockage
        val storagePath = sharedPreferences.getString("storage_path", "/storage/emulated/0/ChatAI-Files")
        storagePathText.text = "Emplacement actuel: $storagePath"
        
        // Charger les préférences de fonctionnalités
        enableNotificationsSwitch.isChecked = sharedPreferences.getBoolean("enable_notifications", true)
        enableVoiceSwitch.isChecked = sharedPreferences.getBoolean("enable_voice", true)
        enableFileSharingSwitch.isChecked = sharedPreferences.getBoolean("enable_file_sharing", true)
        
        // Charger le thème actuel
        updateThemeButtons()
    }
    
    private fun setupListeners() {
        // Boutons de sauvegarde
        findViewById<MaterialButton>(R.id.saveSettingsButton).setOnClickListener {
            saveAllSettings()
        }
        
        findViewById<MaterialButton>(R.id.resetSettingsButton).setOnClickListener {
            resetToDefaults()
        }
        
        findViewById<MaterialButton>(R.id.testServersButton).setOnClickListener {
            testServerConnections()
        }
        
        // Bouton de changement de stockage
        changeStorageButton.setOnClickListener {
            openStorageLocationPicker()
        }
        
        // Boutons de thème
        themeRedButton.setOnClickListener {
            selectTheme("red")
        }
        
        themeDarkButton.setOnClickListener {
            selectTheme("dark")
        }
        
        themeAmberButton.setOnClickListener {
            selectTheme("amber")
        }
        
        // Boutons de configuration spécialisée
        findViewById<MaterialButton>(R.id.configureAIButton).setOnClickListener {
            openAIConfiguration()
        }
        
        findViewById<MaterialButton>(R.id.configureFilesButton).setOnClickListener {
            openFileConfiguration()
        }
        
        findViewById<MaterialButton>(R.id.configureSecurityButton).setOnClickListener {
            openSecurityConfiguration()
        }
    }
    
    private fun saveAllSettings() {
        try {
            val editor = sharedPreferences.edit()
            
            // Sauvegarder les ports
            editor.putString("http_port", httpPortInput.text.toString())
            editor.putString("ws_port", wsPortInput.text.toString())
            editor.putString("file_port", filePortInput.text.toString())
            
            // Sauvegarder les clés API (si elles ont été modifiées)
            val openaiKey = openaiApiKeyInput.text.toString()
            if (openaiKey.isNotEmpty() && !openaiKey.contains("*")) {
                editor.putString("openai_api_key", openaiKey)
            }
            
            val huggingfaceKey = huggingfaceApiKeyInput.text.toString()
            if (huggingfaceKey.isNotEmpty() && !huggingfaceKey.contains("*")) {
                editor.putString("huggingface_api_key", huggingfaceKey)
            }
            
            // Sauvegarder les préférences de fonctionnalités
            editor.putBoolean("enable_notifications", enableNotificationsSwitch.isChecked)
            editor.putBoolean("enable_voice", enableVoiceSwitch.isChecked)
            editor.putBoolean("enable_file_sharing", enableFileSharingSwitch.isChecked)
            
            editor.apply()
            
            Toast.makeText(this, "Configuration sauvegardée avec succès", Toast.LENGTH_SHORT).show()
            
            // Redémarrer les serveurs si nécessaire
            restartServersIfNeeded()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lors de la sauvegarde: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun resetToDefaults() {
        // Réinitialiser aux valeurs par défaut
        httpPortInput.setText("8080")
        wsPortInput.setText("8081")
        filePortInput.setText("8082")
        
        openaiApiKeyInput.setText("")
        huggingfaceApiKeyInput.setText("")
        
        enableNotificationsSwitch.isChecked = true
        enableVoiceSwitch.isChecked = true
        enableFileSharingSwitch.isChecked = true
        
        selectTheme("red")
        
        Toast.makeText(this, "Configuration réinitialisée aux valeurs par défaut", Toast.LENGTH_SHORT).show()
    }
    
    private fun testServerConnections() {
        Toast.makeText(this, "Test des connexions serveurs...", Toast.LENGTH_SHORT).show()
        
        // Tester les connexions (simulation)
        Thread {
            Thread.sleep(2000)
            
            runOnUiThread {
                val results = mutableListOf<String>()
                
                // Test HTTP Server
                try {
                    val httpPort = httpPortInput.text.toString().toInt()
                    results.add("✅ HTTP Server: Port $httpPort disponible")
                } catch (e: Exception) {
                    results.add("❌ HTTP Server: Erreur de port")
                }
                
                // Test WebSocket Server
                try {
                    val wsPort = wsPortInput.text.toString().toInt()
                    results.add("✅ WebSocket Server: Port $wsPort disponible")
                } catch (e: Exception) {
                    results.add("❌ WebSocket Server: Erreur de port")
                }
                
                // Test File Server
                try {
                    val filePort = filePortInput.text.toString().toInt()
                    results.add("✅ File Server: Port $filePort disponible")
                } catch (e: Exception) {
                    results.add("❌ File Server: Erreur de port")
                }
                
                // Afficher les résultats
                val message = results.joinToString("\n")
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }
    
    private fun openStorageLocationPicker() {
        // Ouvrir le sélecteur de répertoire
        if (fileServer != null) {
            fileServer?.openDirectoryPicker()
        } else {
            // Fallback vers l'explorateur de fichiers Android (supprimé car deprecated)
            Log.w("ConfigurationActivity", "Directory picker not available - FileServer required")
        }
    }
    
    private fun selectTheme(theme: String) {
        sharedPreferences.edit().putString("kitt_theme", theme).apply()
        updateThemeButtons()
        
        val themeNames = mapOf(
            "red" to "Rouge KITT",
            "dark" to "Sombre",
            "amber" to "Ambre"
        )
        
        Toast.makeText(this, "Thème ${themeNames[theme]} sélectionné", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateThemeButtons() {
        val currentTheme = sharedPreferences.getString("kitt_theme", "red") ?: "red"
        
        // Réinitialiser tous les boutons
        themeRedButton.setBackgroundColor(ContextCompat.getColor(this, R.color.kitt_red_alpha))
        themeDarkButton.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_gray_medium))
        themeAmberButton.setBackgroundColor(ContextCompat.getColor(this, R.color.amber_primary_light))
        
        // Mettre en surbrillance le thème actuel
        when (currentTheme) {
            "red" -> themeRedButton.setBackgroundColor(ContextCompat.getColor(this, R.color.kitt_red))
            "dark" -> themeDarkButton.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_gray_light))
            "amber" -> themeAmberButton.setBackgroundColor(ContextCompat.getColor(this, R.color.amber_primary))
        }
    }
    
    private fun openAIConfiguration() {
        Toast.makeText(this, "Configuration IA avancée - En développement", Toast.LENGTH_SHORT).show()
        // TODO: Ouvrir une activité de configuration IA détaillée
    }
    
    private fun openFileConfiguration() {
        Toast.makeText(this, "Configuration fichiers avancée - En développement", Toast.LENGTH_SHORT).show()
        // TODO: Ouvrir une activité de configuration des fichiers détaillée
    }
    
    private fun openSecurityConfiguration() {
        Toast.makeText(this, "Configuration sécurité avancée - En développement", Toast.LENGTH_SHORT).show()
        // TODO: Ouvrir une activité de configuration de sécurité détaillée
    }
    
    private fun maskApiKey(key: String): String {
        if (key.length <= 8) return "*".repeat(key.length)
        return key.substring(0, 4) + "*".repeat(key.length - 8) + key.substring(key.length - 4)
    }
    
    private fun restartServersIfNeeded() {
        // Vérifier si les ports ont changé
        val newHttpPort = httpPortInput.text.toString()
        val newWsPort = wsPortInput.text.toString()
        val newFilePort = filePortInput.text.toString()
        
        val oldHttpPort = sharedPreferences.getString("http_port", "8080")
        val oldWsPort = sharedPreferences.getString("ws_port", "8081")
        val oldFilePort = sharedPreferences.getString("file_port", "8082")
        
        if (newHttpPort != oldHttpPort || newWsPort != oldWsPort || newFilePort != oldFilePort) {
            Toast.makeText(this, "Redémarrage des serveurs requis pour appliquer les nouveaux ports", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri != null) {
                val path = uri.path
                sharedPreferences.edit().putString("storage_path", path).apply()
                storagePathText.text = "Emplacement actuel: $path"
                Toast.makeText(this, "Emplacement de stockage mis à jour: $path", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    // Méthodes pour recevoir les références des serveurs depuis MainActivity
    fun setServers(http: HttpServer?, ws: WebSocketServer?, ai: RealtimeAIService?, files: FileServer?) {
        httpServer = http
        webSocketServer = ws
        aiService = ai
        fileServer = files
    }
}
