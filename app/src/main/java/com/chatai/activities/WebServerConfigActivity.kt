package com.chatai.activities

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.chatai.R
import com.chatai.WebServer

/**
 * Configuration du WebServer (Port 8888)
 * Interface complètement séparée de ChatAI
 */
class WebServerConfigActivity : AppCompatActivity() {
    
    private lateinit var autoindexSwitch: Switch
    private lateinit var foldersFirstSwitch: Switch
    private lateinit var exactSizeSwitch: Switch
    private lateinit var showIconsSwitch: Switch
    private lateinit var customCssEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webserver_config)
        
        // Initialiser les vues
        initViews()
        
        // Charger la configuration actuelle
        loadCurrentConfig()
        
        // Configurer les listeners
        setupListeners()
    }
    
    private fun initViews() {
        autoindexSwitch = findViewById(R.id.switch_autoindex)
        foldersFirstSwitch = findViewById(R.id.switch_folders_first)
        exactSizeSwitch = findViewById(R.id.switch_exact_size)
        showIconsSwitch = findViewById(R.id.switch_show_icons)
        customCssEditText = findViewById(R.id.edittext_custom_css)
        saveButton = findViewById(R.id.button_save_config)
        testButton = findViewById(R.id.button_test_webserver)
    }
    
    private fun loadCurrentConfig() {
        // TODO: Charger la configuration depuis SharedPreferences
        // Pour l'instant, valeurs par défaut
        autoindexSwitch.isChecked = true
        foldersFirstSwitch.isChecked = true
        exactSizeSwitch.isChecked = false
        showIconsSwitch.isChecked = true
        customCssEditText.setText("")
    }
    
    private fun setupListeners() {
        saveButton.setOnClickListener {
            saveConfiguration()
        }
        
        testButton.setOnClickListener {
            testWebServer()
        }
    }
    
    private fun saveConfiguration() {
        // TODO: Sauvegarder dans SharedPreferences
        // TODO: Appliquer la configuration au WebServer
        
        Toast.makeText(this, "Configuration WebServer sauvegardée", Toast.LENGTH_SHORT).show()
    }
    
    private fun testWebServer() {
        // TODO: Tester la connexion au WebServer
        Toast.makeText(this, "Test WebServer en cours...", Toast.LENGTH_SHORT).show()
    }
}
