package com.chatai.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.chatai.R

/**
 * Configuration spécialisée pour les serveurs
 * Focus sur les ports, fichiers, sécurité et hébergement
 */
class ServerConfigurationActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    
    // Configuration des serveurs
    private lateinit var httpPortInput: TextInputEditText
    private lateinit var wsPortInput: TextInputEditText
    private lateinit var filePortInput: TextInputEditText
    
    // Configuration des fichiers - Chemin fixe
    
    // Configuration de sécurité
    private lateinit var enableSSLSwitch: SwitchMaterial
    private lateinit var enableAuthSwitch: SwitchMaterial
    private lateinit var enableCorsSwitch: SwitchMaterial
    
    // Configuration de l'hébergement
    private lateinit var enableWebHostingSwitch: SwitchMaterial
    private lateinit var enableFileSharingSwitch: SwitchMaterial
    private lateinit var enableWebSocketSwitch: SwitchMaterial
    
    // Configuration WebServer (Port 8888)
    private lateinit var webserverAutoindexSwitch: SwitchMaterial
    private lateinit var webserverFoldersFirstSwitch: SwitchMaterial
    private lateinit var webserverExactSizeSwitch: SwitchMaterial
    private lateinit var webserverShowIconsSwitch: SwitchMaterial
    private lateinit var webserverCustomCssInput: TextInputEditText
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_configuration)
        
        // Initialiser les préférences
        sharedPreferences = getSharedPreferences("chatai_server_config", Context.MODE_PRIVATE)
        
        // Initialiser les vues
        initializeViews()
        
        // Charger les paramètres existants
        loadCurrentSettings()
        
        // Configurer les listeners
        setupListeners()
    }
    
    private fun initializeViews() {
        // Ports des serveurs
        httpPortInput = findViewById(R.id.httpPortInput)
        wsPortInput = findViewById(R.id.wsPortInput)
        filePortInput = findViewById(R.id.filePortInput)
        
        // Configuration des fichiers
        // Chemin fixe - pas de configuration nécessaire
        
        // Configuration de sécurité
        enableSSLSwitch = findViewById(R.id.enableSSLSwitch)
        enableAuthSwitch = findViewById(R.id.enableAuthSwitch)
        enableCorsSwitch = findViewById(R.id.enableCorsSwitch)
        
        // Configuration de l'hébergement
        enableWebHostingSwitch = findViewById(R.id.enableWebHostingSwitch)
        enableFileSharingSwitch = findViewById(R.id.enableFileSharingSwitch)
        enableWebSocketSwitch = findViewById(R.id.enableWebSocketSwitch)
        
        // Configuration WebServer (Port 8888)
        webserverAutoindexSwitch = findViewById(R.id.webserverAutoindexSwitch)
        webserverFoldersFirstSwitch = findViewById(R.id.webserverFoldersFirstSwitch)
        webserverExactSizeSwitch = findViewById(R.id.webserverExactSizeSwitch)
        webserverShowIconsSwitch = findViewById(R.id.webserverShowIconsSwitch)
        webserverCustomCssInput = findViewById(R.id.webserverCustomCssInput)
    }
    
    private fun loadCurrentSettings() {
        // Charger les ports des serveurs
        httpPortInput.setText(sharedPreferences.getString("http_port", "8080"))
        wsPortInput.setText(sharedPreferences.getString("ws_port", "8081"))
        filePortInput.setText(sharedPreferences.getString("file_port", "8082"))
        
        // Charger le chemin de stockage
        // Chemin fixe : /storage/emulated/0/ChatAI-Files
        
        // Charger les préférences de sécurité
        enableSSLSwitch.isChecked = sharedPreferences.getBoolean("enable_ssl", false)
        enableAuthSwitch.isChecked = sharedPreferences.getBoolean("enable_auth", false)
        enableCorsSwitch.isChecked = sharedPreferences.getBoolean("enable_cors", true)
        
        // Charger les préférences d'hébergement
        enableWebHostingSwitch.isChecked = sharedPreferences.getBoolean("enable_web_hosting", true)
        enableFileSharingSwitch.isChecked = sharedPreferences.getBoolean("enable_file_sharing", true)
        enableWebSocketSwitch.isChecked = sharedPreferences.getBoolean("enable_websocket", true)
        
        // Charger les préférences WebServer (Port 8888)
        webserverAutoindexSwitch.isChecked = sharedPreferences.getBoolean("webserver_autoindex", true)
        webserverFoldersFirstSwitch.isChecked = sharedPreferences.getBoolean("webserver_folders_first", true)
        webserverExactSizeSwitch.isChecked = sharedPreferences.getBoolean("webserver_exact_size", false)
        webserverShowIconsSwitch.isChecked = sharedPreferences.getBoolean("webserver_show_icons", true)
        webserverCustomCssInput.setText(sharedPreferences.getString("webserver_custom_css", ""))
    }
    
    private fun setupListeners() {
        // Bouton de sauvegarde
        findViewById<MaterialButton>(R.id.saveServerSettingsButton).setOnClickListener {
            saveServerSettings()
        }
        
        // Bouton de test des serveurs
        findViewById<MaterialButton>(R.id.testServersButton).setOnClickListener {
            testServerConnections()
        }
        
        // Bouton de réinitialisation
        findViewById<MaterialButton>(R.id.resetServerSettingsButton).setOnClickListener {
            resetServerToDefaults()
        }
        
        // Pas de choix d'emplacement - chemin fixe
    }
    
    private fun saveServerSettings() {
        try {
            // Valider la configuration avant de sauvegarder
            if (!validateServerConfig()) {
                return
            }
            
            val editor = sharedPreferences.edit()
            
            // Sauvegarder les ports
            editor.putString("http_port", httpPortInput.text.toString())
            editor.putString("ws_port", wsPortInput.text.toString())
            editor.putString("file_port", filePortInput.text.toString())
            
            // Sauvegarder les préférences de sécurité
            editor.putBoolean("enable_ssl", enableSSLSwitch.isChecked)
            editor.putBoolean("enable_auth", enableAuthSwitch.isChecked)
            editor.putBoolean("enable_cors", enableCorsSwitch.isChecked)
            
            // Sauvegarder les préférences d'hébergement
            editor.putBoolean("enable_web_hosting", enableWebHostingSwitch.isChecked)
            editor.putBoolean("enable_file_sharing", enableFileSharingSwitch.isChecked)
            editor.putBoolean("enable_websocket", enableWebSocketSwitch.isChecked)
            
            // Sauvegarder les préférences WebServer (Port 8888)
            editor.putBoolean("webserver_autoindex", webserverAutoindexSwitch.isChecked)
            editor.putBoolean("webserver_folders_first", webserverFoldersFirstSwitch.isChecked)
            editor.putBoolean("webserver_exact_size", webserverExactSizeSwitch.isChecked)
            editor.putBoolean("webserver_show_icons", webserverShowIconsSwitch.isChecked)
            editor.putString("webserver_custom_css", webserverCustomCssInput.text.toString())
            
            editor.apply()
            
            Toast.makeText(this, "Configuration serveur sauvegardée avec succès", Toast.LENGTH_SHORT).show()
            
            // Redémarrer les serveurs si nécessaire
            restartServersIfNeeded()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lors de la sauvegarde serveur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testServerConnections() {
        Toast.makeText(this, "Test des connexions serveur en cours...", Toast.LENGTH_SHORT).show()
        
        // Tester les serveurs en arrière-plan
        Thread {
            val results = mutableListOf<String>()
            
            try {
                // Test HTTP Server (ChatAI - Port 8080)
                val httpPort = httpPortInput.text.toString().toIntOrNull() ?: 8080
                val httpUrl = "http://localhost:$httpPort/api/status"
                val httpResponse = java.net.URL(httpUrl).openConnection()
                httpResponse.connectTimeout = 5000
                httpResponse.readTimeout = 5000
                httpResponse.getInputStream()
                results.add("✅ ChatAI HTTP Server (port $httpPort): OK")
            } catch (e: Exception) {
                results.add("❌ ChatAI HTTP Server: ${e.message}")
            }
            
            try {
                // Test WebServer (Port 8888)
                val webserverUrl = "http://localhost:8888/"
                val webserverResponse = java.net.URL(webserverUrl).openConnection()
                webserverResponse.connectTimeout = 5000
                webserverResponse.readTimeout = 5000
                webserverResponse.getInputStream()
                results.add("✅ WebServer (port 8888): OK")
            } catch (e: Exception) {
                results.add("❌ WebServer: ${e.message}")
            }
            
            try {
                // Test WebSocket Server
                val wsPort = wsPortInput.text.toString().toIntOrNull() ?: 8081
                val wsSocket = java.net.Socket()
                wsSocket.connect(java.net.InetSocketAddress("localhost", wsPort), 5000)
                wsSocket.close()
                results.add("✅ WebSocket Server (port $wsPort): OK")
            } catch (e: Exception) {
                results.add("❌ WebSocket Server: ${e.message}")
            }
            
            try {
                // Test File Server
                val filePort = filePortInput.text.toString().toIntOrNull() ?: 8082
                val fileUrl = "http://localhost:$filePort/api/files/storage/info"
                val fileResponse = java.net.URL(fileUrl).openConnection()
                fileResponse.connectTimeout = 5000
                fileResponse.readTimeout = 5000
                fileResponse.getInputStream()
                results.add("✅ File Server (port $filePort): OK")
            } catch (e: Exception) {
                results.add("❌ File Server: ${e.message}")
            }
            
            // Afficher les résultats sur le thread principal
            runOnUiThread {
                val message = "🧪 Test des Serveurs:\n\n" + results.joinToString("\n")
                android.app.AlertDialog.Builder(this)
                    .setTitle("Résultats des Tests")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }.start()
    }
    
    private fun resetServerToDefaults() {
        // Réinitialiser aux valeurs par défaut
        httpPortInput.setText("8080")
        wsPortInput.setText("8081")
        filePortInput.setText("8082")
        
        enableSSLSwitch.isChecked = false
        enableAuthSwitch.isChecked = false
        enableCorsSwitch.isChecked = true
        enableWebHostingSwitch.isChecked = true
        enableFileSharingSwitch.isChecked = true
        enableWebSocketSwitch.isChecked = true
        
        Toast.makeText(this, "Configuration serveur réinitialisée", Toast.LENGTH_SHORT).show()
    }
    
    private fun openStorageLocationPicker() {
        try {
            // startActivityForResult is deprecated - using FileServer instead
            Log.w("ServerConfigurationActivity", "Directory picker requires FileServer implementation")
            Toast.makeText(this, "Sélection de répertoire non disponible", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur ouverture sélecteur répertoire", Toast.LENGTH_SHORT).show()
        }
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
    
    /**
     * Affiche les informations détaillées des serveurs
     */
    private fun showServerInfo() {
        val httpPort = httpPortInput.text.toString()
        val wsPort = wsPortInput.text.toString()
        val filePort = filePortInput.text.toString()
        val storagePath = "/storage/emulated/0/ChatAI-Files" // Chemin fixe
        
        val info = """
            🌐 Configuration des Serveurs
            
            📡 HTTP Server: Port $httpPort
            🔌 WebSocket: Port $wsPort  
            📁 File Server: Port $filePort
            
            💾 Stockage: $storagePath
            
            🔒 Sécurité:
            • SSL: ${if (enableSSLSwitch.isChecked) "Activé" else "Désactivé"}
            • Auth: ${if (enableAuthSwitch.isChecked) "Activé" else "Désactivé"}
            • CORS: ${if (enableCorsSwitch.isChecked) "Activé" else "Désactivé"}
            
            🌐 Hébergement:
            • Web: ${if (enableWebHostingSwitch.isChecked) "Activé" else "Désactivé"}
            • Fichiers: ${if (enableFileSharingSwitch.isChecked) "Activé" else "Désactivé"}
            • WebSocket: ${if (enableWebSocketSwitch.isChecked) "Activé" else "Désactivé"}
        """.trimIndent()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("📊 Informations Serveurs")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Exporte la configuration serveur
     */
    private fun exportServerConfig() {
        try {
            val config = """
                {
                    "http_port": "${httpPortInput.text}",
                    "ws_port": "${wsPortInput.text}",
                    "file_port": "${filePortInput.text}",
                    "storage_path": "/storage/emulated/0/ChatAI-Files",
                    "enable_ssl": ${enableSSLSwitch.isChecked},
                    "enable_auth": ${enableAuthSwitch.isChecked},
                    "enable_cors": ${enableCorsSwitch.isChecked},
                    "enable_web_hosting": ${enableWebHostingSwitch.isChecked},
                    "enable_file_sharing": ${enableFileSharingSwitch.isChecked},
                    "enable_websocket": ${enableWebSocketSwitch.isChecked}
                }
            """.trimIndent()
            
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "application/json"
            intent.putExtra(Intent.EXTRA_TEXT, config)
            intent.putExtra(Intent.EXTRA_SUBJECT, "ChatAI Server Configuration")
            startActivity(Intent.createChooser(intent, "Exporter la configuration"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur export configuration: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Valide la configuration serveur
     */
    private fun validateServerConfig(): Boolean {
        val httpPort = httpPortInput.text.toString().toIntOrNull()
        val wsPort = wsPortInput.text.toString().toIntOrNull()
        val filePort = filePortInput.text.toString().toIntOrNull()
        
        if (httpPort == null || httpPort < 1024 || httpPort > 65535) {
            Toast.makeText(this, "Port HTTP invalide (1024-65535)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (wsPort == null || wsPort < 1024 || wsPort > 65535) {
            Toast.makeText(this, "Port WebSocket invalide (1024-65535)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (filePort == null || filePort < 1024 || filePort > 65535) {
            Toast.makeText(this, "Port File Server invalide (1024-65535)", Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (httpPort == wsPort || httpPort == filePort || wsPort == filePort) {
            Toast.makeText(this, "Les ports doivent être différents", Toast.LENGTH_SHORT).show()
            return false
        }
        
        return true
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            if (uri != null) {
                // Convertir l'URI SAF en chemin direct
                // Chemin fixe - pas de modification possible
                Toast.makeText(this, "Emplacement fixe: /storage/emulated/0/ChatAI-Files", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Convertit un URI SAF en chemin direct
     */
    private fun convertSAFUriToDirectPath(uri: Uri): String? {
        return try {
            when {
                uri.path?.startsWith("/tree/primary:") == true -> {
                    // Convertir /tree/primary:site/unified en /storage/emulated/0/site/unified
                    val relativePath = uri.path?.substringAfter("/tree/primary:") ?: ""
                    "/storage/emulated/0/$relativePath"
                }
                uri.path?.startsWith("/storage/") == true -> {
                    // Déjà un chemin direct
                    uri.path
                }
                else -> {
                    // Essayer de résoudre l'URI
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    null // Utiliser l'URI SAF directement
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ServerConfig", "Erreur conversion URI SAF", e)
            null
        }
    }
}
