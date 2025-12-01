package com.chatai.activities

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.chatai.R
import com.chatai.WebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    
    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("webserver_config", Context.MODE_PRIVATE)
    }
    
    // ⭐ NOUVEAU: Référence au WebServer (injectée depuis MainActivity ou BackgroundService)
    private var webServer: WebServer? = null
    
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
        // ⭐ IMPLÉMENTÉ: Charger la configuration depuis SharedPreferences
        autoindexSwitch.isChecked = sharedPreferences.getBoolean("autoindex", true)
        foldersFirstSwitch.isChecked = sharedPreferences.getBoolean("folders_first", true)
        exactSizeSwitch.isChecked = sharedPreferences.getBoolean("exact_size", false)
        showIconsSwitch.isChecked = sharedPreferences.getBoolean("show_icons", true)
        customCssEditText.setText(sharedPreferences.getString("custom_css", "") ?: "")
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
        // ⭐ IMPLÉMENTÉ: Sauvegarder dans SharedPreferences
        sharedPreferences.edit()
            .putBoolean("autoindex", autoindexSwitch.isChecked)
            .putBoolean("folders_first", foldersFirstSwitch.isChecked)
            .putBoolean("exact_size", exactSizeSwitch.isChecked)
            .putBoolean("show_icons", showIconsSwitch.isChecked)
            .putString("custom_css", customCssEditText.text.toString())
            .apply()
        
        // ⭐ IMPLÉMENTÉ: Appliquer la configuration au WebServer
        webServer?.let { server ->
            server.setAutoindex(autoindexSwitch.isChecked)
            server.setFoldersFirst(foldersFirstSwitch.isChecked)
            server.setExactSize(exactSizeSwitch.isChecked)
            server.setShowIcons(showIconsSwitch.isChecked)
            server.setCustomCSS(customCssEditText.text.toString())
            Toast.makeText(this, "✅ Configuration WebServer sauvegardée et appliquée", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "⚠️ Configuration sauvegardée (WebServer non disponible)", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun testWebServer() {
        // ⭐ IMPLÉMENTÉ: Tester la connexion au WebServer
        testButton.isEnabled = false
        testButton.text = "Test en cours..."
        
        CoroutineScope(Dispatchers.IO).launch {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder()
                .url("http://127.0.0.1:8888")
                .get()
                .build()
            
            try {
                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful
                val message = if (success) {
                    "✅ WebServer accessible (HTTP ${response.code})"
                } else {
                    "⚠️ WebServer répond mais erreur HTTP ${response.code}"
                }
                
                runOnUiThread {
                    testButton.isEnabled = true
                    testButton.text = "Tester WebServer"
                    Toast.makeText(this@WebServerConfigActivity, message, Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    testButton.isEnabled = true
                    testButton.text = "Tester WebServer"
                    Toast.makeText(this@WebServerConfigActivity, "❌ WebServer inaccessible: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * ⭐ NOUVEAU: Méthode pour injecter le WebServer depuis MainActivity/BackgroundService
     */
    fun setWebServer(server: WebServer) {
        this.webServer = server
    }
}
