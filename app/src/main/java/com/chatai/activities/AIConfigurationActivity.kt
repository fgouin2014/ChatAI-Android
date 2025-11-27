package com.chatai.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.chatai.R
import com.chatai.AiConfigManager
import com.chatai.SecureConfig
import com.chatai.services.KittAIService
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ProgressBar
import android.view.View
import android.speech.tts.TextToSpeech
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Configuration spécialisée pour l'IA
 * Focus sur les APIs, modèles, personnalités et paramètres IA
 */
class AIConfigurationActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var secureConfig: SecureConfig
    
    // Stocker les clés réelles (non masquées) pour éviter de perdre les valeurs lors de la sauvegarde
    private var currentOllamaCloudApiKey: String? = null
    
    // Configuration des APIs IA
    private lateinit var openaiApiKeyInput: TextInputEditText
    private lateinit var huggingfaceApiKeyInput: TextInputEditText
    private lateinit var anthropicApiKeyInput: TextInputEditText
    
    // Configuration Ollama Cloud
    private lateinit var ollamaCloudApiKeyInput: TextInputEditText
    private lateinit var ollamaCloudModelInput: TextInputEditText
    private lateinit var ollamaCloudModelSpinner: android.widget.Spinner
    
    // Configuration Serveur Local (Ollama/LM Studio)
    private lateinit var localServerUrlInput: TextInputEditText
    private lateinit var localModelNameInput: TextInputEditText
    
    // Configuration TTS (Voix de KITT)
    private lateinit var ttsSpeedSeekBar: android.widget.SeekBar
    private lateinit var ttsSpeedValue: android.widget.TextView
    private lateinit var ttsPitchSeekBar: android.widget.SeekBar
    private lateinit var ttsPitchValue: android.widget.TextView
    
    // Configuration des modèles
    private lateinit var defaultModelInput: TextInputEditText
    private lateinit var maxTokensInput: TextInputEditText
    private lateinit var temperatureInput: TextInputEditText
    
    // Configuration des personnalités
    private lateinit var enablePersonalitiesSwitch: SwitchMaterial
    private lateinit var enableVoiceResponseSwitch: SwitchMaterial
    private lateinit var enableContextMemorySwitch: SwitchMaterial
    
    // Configuration des fonctionnalités IA
    private lateinit var enableImageGenerationSwitch: SwitchMaterial
    private lateinit var enableCodeGenerationSwitch: SwitchMaterial
    private lateinit var enableTranslationSwitch: SwitchMaterial
    
    // Toggle V1/V2 retiré - V2 est maintenant la version unique
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_configuration)
        
        // Initialiser les préférences
        sharedPreferences = getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        secureConfig = SecureConfig(this)
        
        // Initialiser les vues
        initializeViews()
        
        // Charger les paramètres existants
        loadCurrentSettings()
        
        // Configurer les listeners
        setupListeners()
        
        // Configurer le bouton JSON editor
        setupJsonEditorButton()
    }
    
    private fun initializeViews() {
        // APIs
        openaiApiKeyInput = findViewById(R.id.openaiApiKeyInput)
        huggingfaceApiKeyInput = findViewById(R.id.huggingfaceApiKeyInput)
        anthropicApiKeyInput = findViewById(R.id.anthropicApiKeyInput)
        
        // Ollama Cloud
        ollamaCloudApiKeyInput = findViewById(R.id.ollamaCloudApiKeyInput)
        ollamaCloudModelInput = findViewById(R.id.ollamaCloudModelInput)
        ollamaCloudModelSpinner = findViewById(R.id.ollamaCloudModelSpinner)
        
        // Initialiser le Spinner avec les modèles Cloud
        setupCloudModelSpinner()
        
        // Serveur Local (Ollama/LM Studio)
        localServerUrlInput = findViewById(R.id.localServerUrlInput)
        localModelNameInput = findViewById(R.id.localModelNameInput)
        
        // TTS (Voix de KITT)
        ttsSpeedSeekBar = findViewById(R.id.ttsSpeedSeekBar)
        ttsSpeedValue = findViewById(R.id.ttsSpeedValue)
        ttsPitchSeekBar = findViewById(R.id.ttsPitchSeekBar)
        ttsPitchValue = findViewById(R.id.ttsPitchValue)
        
        // Modèles
        defaultModelInput = findViewById(R.id.defaultModelInput)
        maxTokensInput = findViewById(R.id.maxTokensInput)
        temperatureInput = findViewById(R.id.temperatureInput)
        
        // Personnalités
        enablePersonalitiesSwitch = findViewById(R.id.enablePersonalitiesSwitch)
        enableVoiceResponseSwitch = findViewById(R.id.enableVoiceResponseSwitch)
        enableContextMemorySwitch = findViewById(R.id.enableContextMemorySwitch)
        
        // Fonctionnalités IA
        enableImageGenerationSwitch = findViewById(R.id.enableImageGenerationSwitch)
        enableCodeGenerationSwitch = findViewById(R.id.enableCodeGenerationSwitch)
        enableTranslationSwitch = findViewById(R.id.enableTranslationSwitch)
    }
    
    private fun setupCloudModelSpinner() {
        // Liste des modèles Ollama Cloud disponibles (noms officiels sans -cloud)
        val cloudModels = listOf(
            "gpt-oss:120b",
            "deepseek-v3.1:671b",
            "qwen3-coder:480b",
            "kimi-k2:1t",
            "gpt-oss:20b",
            "glm-4.6",
            "Manuel (entrer ci-dessous)"
        )
        
        // Créer adapter
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            cloudModels
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        ollamaCloudModelSpinner.adapter = adapter
        
        // Listener pour sélection
        ollamaCloudModelSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedModel = cloudModels[position]
                
                if (selectedModel == "Manuel (entrer ci-dessous)") {
                    // Mode manuel - activer input
                    ollamaCloudModelInput.isEnabled = true
                    ollamaCloudModelInput.requestFocus()
                    android.util.Log.d("AIConfig", "Mode manuel sélectionné")
                } else {
                    // Modèle pré-défini - désactiver input et le remplir
                    ollamaCloudModelInput.isEnabled = false
                    ollamaCloudModelInput.setText(selectedModel)
                    android.util.Log.d("AIConfig", "Modèle sélectionné: $selectedModel")
                }
            }
            
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // Rien
            }
        }
        
        android.util.Log.d("AIConfig", "✅ Cloud model spinner initialized with ${cloudModels.size} models")
    }
    
    private fun loadCurrentSettings() {
        // Charger les clés API (partiellement masquées)
        val openaiKey = sharedPreferences.getString("openai_api_key", "")
        openaiApiKeyInput.setText(if (openaiKey.isNullOrEmpty()) "" else maskApiKey(openaiKey))
        
        val huggingfaceKey = secureConfig.getHuggingFaceApiKey() ?: ""
        huggingfaceApiKeyInput.setText(if (huggingfaceKey.isEmpty()) "" else maskApiKey(huggingfaceKey))
        
        val anthropicKey = sharedPreferences.getString("anthropic_api_key", "")
        anthropicApiKeyInput.setText(if (anthropicKey.isNullOrEmpty()) "" else maskApiKey(anthropicKey))
        
        // Charger la configuration Ollama Cloud depuis SecureConfig
        val ollamaCloudKey = secureConfig.getOllamaCloudApiKey()
        android.util.Log.d("AIConfig", "loadCurrentSettings: Ollama Cloud key chargée = ${if (ollamaCloudKey == null || ollamaCloudKey.isEmpty()) "vide/null" else "${ollamaCloudKey.length} chars"}")
        if (ollamaCloudKey != null && ollamaCloudKey.isNotEmpty()) {
            currentOllamaCloudApiKey = ollamaCloudKey
            ollamaCloudApiKeyInput.setText(maskApiKey(ollamaCloudKey))
            android.util.Log.d("AIConfig", "loadCurrentSettings: Clé stockée dans currentOllamaCloudApiKey (${currentOllamaCloudApiKey!!.length} chars)")
        } else {
            currentOllamaCloudApiKey = null
            ollamaCloudApiKeyInput.setText("")
            android.util.Log.d("AIConfig", "loadCurrentSettings: Aucune clé trouvée, currentOllamaCloudApiKey = null")
        }
        
        val ollamaCloudModel = sharedPreferences.getString("ollama_cloud_model", "gpt-oss:120b")
        
        // Sélectionner dans le Spinner si c'est un modèle connu (noms officiels sans -cloud)
        val cloudModels = listOf(
            "gpt-oss:120b",
            "deepseek-v3.1:671b",
            "qwen3-coder:480b",
            "kimi-k2:1t",
            "gpt-oss:20b",
            "glm-4.6"
        )
        
        val spinnerPosition = cloudModels.indexOf(ollamaCloudModel)
        if (spinnerPosition >= 0) {
            // Modèle connu - sélectionner dans Spinner
            ollamaCloudModelSpinner.setSelection(spinnerPosition)
            ollamaCloudModelInput.isEnabled = false
            ollamaCloudModelInput.setText(ollamaCloudModel)
        } else {
            // Modèle custom - mode manuel
            ollamaCloudModelSpinner.setSelection(cloudModels.size) // "Manuel"
            ollamaCloudModelInput.isEnabled = true
            ollamaCloudModelInput.setText(ollamaCloudModel)
        }
        
        // Charger la configuration du serveur local
        val localServerUrl = sharedPreferences.getString("local_server_url", "")
        localServerUrlInput.setText(localServerUrl)
        
        val localModelName = sharedPreferences.getString("local_model_name", "llama3.2")
        localModelNameInput.setText(localModelName)
        
        // Charger la configuration TTS
        val ttsSpeed = sharedPreferences.getFloat("tts_speed", 1.1f)
        val ttsPitch = sharedPreferences.getFloat("tts_pitch", 0.8f)
        ttsSpeedSeekBar.progress = ((ttsSpeed - 0.5f) * 20).toInt() // 0.5-2.0 → 0-30
        ttsPitchSeekBar.progress = ((ttsPitch - 0.5f) * 20).toInt() // 0.5-1.5 → 0-20
        ttsSpeedValue.text = String.format("%.1fx", ttsSpeed)
        ttsPitchValue.text = String.format("%.1fx", ttsPitch)
        
        // Charger les paramètres de modèles
        defaultModelInput.setText(sharedPreferences.getString("default_model", "gpt-3.5-turbo"))
        maxTokensInput.setText(sharedPreferences.getString("max_tokens", "1000"))
        temperatureInput.setText(sharedPreferences.getString("temperature", "0.7"))
        
        // Charger les préférences de personnalités
        enablePersonalitiesSwitch.isChecked = sharedPreferences.getBoolean("enable_personalities", true)
        enableVoiceResponseSwitch.isChecked = sharedPreferences.getBoolean("enable_voice_response", true)
        enableContextMemorySwitch.isChecked = sharedPreferences.getBoolean("enable_context_memory", true)
        
        // Charger les fonctionnalités IA
        enableImageGenerationSwitch.isChecked = sharedPreferences.getBoolean("enable_image_generation", false)
        enableCodeGenerationSwitch.isChecked = sharedPreferences.getBoolean("enable_code_generation", true)
        enableTranslationSwitch.isChecked = sharedPreferences.getBoolean("enable_translation", true)
    }
    
    private fun setupListeners() {
        // Bouton de sauvegarde
        findViewById<MaterialButton>(R.id.saveAISettingsButton).setOnClickListener {
            saveAISettings()
        }
        
        // Bouton de test des APIs
        findViewById<MaterialButton>(R.id.testAPIsButton).setOnClickListener {
            testAPIConnections()
        }
        
        // Bouton de test TTS
        findViewById<MaterialButton>(R.id.testTTSButton).setOnClickListener {
            testTTSVoice()
        }
        
        // Bouton de réinitialisation
        findViewById<MaterialButton>(R.id.resetAISettingsButton).setOnClickListener {
            resetAIToDefaults()
        }
        
        // === Contrôles Services ===
        findViewById<MaterialButton>(R.id.btnHotwordStart).setOnClickListener {
            val intent = Intent(this, com.chatai.BackgroundService::class.java).apply {
                action = com.chatai.BackgroundService.ACTION_HOTWORD_START
            }
            startForegroundServiceCompat(intent)
            Toast.makeText(this, "Hotword START demandé", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnHotwordStop).setOnClickListener {
            val intent = Intent(this, com.chatai.BackgroundService::class.java).apply {
                action = com.chatai.BackgroundService.ACTION_HOTWORD_STOP
            }
            startForegroundServiceCompat(intent)
            Toast.makeText(this, "Hotword STOP demandé", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnHotwordRestart).setOnClickListener {
            val intent = Intent(this, com.chatai.BackgroundService::class.java).apply {
                action = com.chatai.BackgroundService.ACTION_HOTWORD_RESTART
            }
            startForegroundServiceCompat(intent)
            Toast.makeText(this, "Hotword RESTART demandé", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnServersRestart).setOnClickListener {
            val intent = Intent(this, com.chatai.BackgroundService::class.java).apply {
                action = com.chatai.BackgroundService.ACTION_SERVERS_RESTART
            }
            startForegroundServiceCompat(intent)
            Toast.makeText(this, "Restart SERVERS demandé", Toast.LENGTH_SHORT).show()
        }

        // Bouton historique des conversations
        findViewById<MaterialButton>(R.id.viewHistoryButton).setOnClickListener {
            startActivity(android.content.Intent(this, ConversationHistoryActivity::class.java))
        }
        
        // ⭐ Bouton Auto-Détection Serveur (Phase 2 - Discovery)
        findViewById<MaterialButton>(R.id.autoDetectServerButton).setOnClickListener {
            autoDetectOllamaServer()
        }
        
        // SeekBar TTS Speed
        ttsSpeedSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress / 20.0f) // 0-30 → 0.5-2.0
                ttsSpeedValue.text = String.format("%.1fx", speed)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        
        // SeekBar TTS Pitch
        ttsPitchSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress / 20.0f) // 0-20 → 0.5-1.5
                ttsPitchValue.text = String.format("%.1fx", pitch)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("AIConfig", "Error starting foreground service: ${e.message}", e)
            Toast.makeText(this, "Erreur démarrage service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun saveAISettings() {
        try {
            val editor = sharedPreferences.edit()
            
            // Sauvegarder les clés API (si elles ont été modifiées)
            // NETTOYER les clés pour enlever les espaces, nouvelles lignes, etc.
            val openaiKey = openaiApiKeyInput.text.toString().trim().replace(Regex("\\s+"), "")
            if (openaiKey.isNotEmpty() && !openaiKey.contains("*")) {
                editor.putString("openai_api_key", openaiKey)
            }
            
            val huggingfaceKey = huggingfaceApiKeyInput.text.toString().trim().replace(Regex("\\s+"), "")
            if (huggingfaceKey.isNotEmpty() && !huggingfaceKey.contains("*")) {
                secureConfig.setHuggingFaceApiKey(huggingfaceKey)
            }
            
            val anthropicKey = anthropicApiKeyInput.text.toString().trim().replace(Regex("\\s+"), "")
            if (anthropicKey.isNotEmpty() && !anthropicKey.contains("*")) {
                editor.putString("anthropic_api_key", anthropicKey)
            }
            
            // Sauvegarder la configuration Ollama Cloud dans SecureConfig
            val ollamaCloudKeyInputText = ollamaCloudApiKeyInput.text.toString().trim().replace(Regex("\\s+"), "")
            android.util.Log.d("AIConfig", "saveAISettings: Ollama Cloud key du champ = ${if (ollamaCloudKeyInputText.isEmpty()) "vide" else if (ollamaCloudKeyInputText.contains("*")) "masquée (${ollamaCloudKeyInputText.length} chars)" else "${ollamaCloudKeyInputText.length} chars"}")
            android.util.Log.d("AIConfig", "saveAISettings: currentOllamaCloudApiKey = ${if (currentOllamaCloudApiKey == null) "null" else "${currentOllamaCloudApiKey!!.length} chars"}")
            
            if (ollamaCloudKeyInputText.isEmpty()) {
                // Champ vide = supprimer la clé
                android.util.Log.d("AIConfig", "saveAISettings: Champ vide, suppression de la clé Ollama Cloud");
                secureConfig.clearOllamaCloudApiKey()
                currentOllamaCloudApiKey = null
            } else if (ollamaCloudKeyInputText.contains("*")) {
                // Champ masqué = l'utilisateur n'a pas modifié, garder la valeur actuelle
                if (currentOllamaCloudApiKey != null && currentOllamaCloudApiKey!!.isNotEmpty()) {
                    android.util.Log.d("AIConfig", "saveAISettings: Champ masqué, conservation de la clé existante (${currentOllamaCloudApiKey!!.length} chars)")
                    secureConfig.setOllamaCloudApiKey(currentOllamaCloudApiKey!!)
                    // Vérifier que la sauvegarde a fonctionné
                    val verifyKey = secureConfig.getOllamaCloudApiKey()
                    if (verifyKey != null && verifyKey == currentOllamaCloudApiKey) {
                        android.util.Log.i("AIConfig", "saveAISettings: Clé vérifiée après sauvegarde (${verifyKey.length} chars)")
                    } else {
                        android.util.Log.e("AIConfig", "saveAISettings: ERREUR - Clé non conservée après sauvegarde!")
                    }
                } else {
                    // Si currentOllamaCloudApiKey est null, essayer de recharger depuis SecureConfig
                    android.util.Log.w("AIConfig", "saveAISettings: Champ masqué mais currentOllamaCloudApiKey est null, tentative de rechargement...")
                    val reloadedKey = secureConfig.getOllamaCloudApiKey()
                    if (reloadedKey != null && reloadedKey.isNotEmpty()) {
                        android.util.Log.i("AIConfig", "saveAISettings: Clé rechargée depuis SecureConfig (${reloadedKey.length} chars), conservation")
                        secureConfig.setOllamaCloudApiKey(reloadedKey)
                        currentOllamaCloudApiKey = reloadedKey
                    } else {
                        android.util.Log.w("AIConfig", "saveAISettings: Aucune clé trouvée dans SecureConfig, suppression")
                        secureConfig.clearOllamaCloudApiKey()
                        currentOllamaCloudApiKey = null
                    }
                }
            } else {
                // Nouvelle clé entrée par l'utilisateur
                android.util.Log.d("AIConfig", "saveAISettings: Nouvelle clé entrée, sauvegarde (${ollamaCloudKeyInputText.length} chars)")
                secureConfig.setOllamaCloudApiKey(ollamaCloudKeyInputText)
                currentOllamaCloudApiKey = ollamaCloudKeyInputText
                // Vérifier que la sauvegarde a fonctionné
                val verifyKey = secureConfig.getOllamaCloudApiKey()
                if (verifyKey != null && verifyKey == ollamaCloudKeyInputText) {
                    android.util.Log.i("AIConfig", "saveAISettings: Nouvelle clé vérifiée après sauvegarde (${verifyKey.length} chars)")
                } else {
                    android.util.Log.e("AIConfig", "saveAISettings: ERREUR - Nouvelle clé non conservée après sauvegarde!")
                }
            }
            
            // Sauvegarder modèle Cloud (depuis input si manuel, sinon depuis spinner)
            val ollamaCloudModel = if (ollamaCloudModelInput.isEnabled) {
                // Mode manuel
                ollamaCloudModelInput.text.toString().trim()
            } else {
                // Mode spinner - utiliser ce qui est dans le input (déjà rempli par le spinner)
                ollamaCloudModelInput.text.toString().trim()
            }
            editor.putString("ollama_cloud_model", ollamaCloudModel)
            android.util.Log.d("AIConfig", "Modèle Cloud sauvegardé: $ollamaCloudModel")
            
            // Sauvegarder la configuration du serveur local (Ollama/LM Studio/Oobabooga)
            val localServerUrl = localServerUrlInput.text.toString().trim()
            editor.putString("local_server_url", localServerUrl)
            
            val localModelName = localModelNameInput.text.toString().trim()
            editor.putString("local_model_name", localModelName)
            
            // Sauvegarder la configuration TTS
            val ttsSpeed = 0.5f + (ttsSpeedSeekBar.progress / 20.0f)
            val ttsPitch = 0.5f + (ttsPitchSeekBar.progress / 20.0f)
            editor.putFloat("tts_speed", ttsSpeed)
            editor.putFloat("tts_pitch", ttsPitch)
            
            // Sauvegarder les paramètres de modèles
            editor.putString("default_model", defaultModelInput.text.toString())
            editor.putString("max_tokens", maxTokensInput.text.toString())
            editor.putString("temperature", temperatureInput.text.toString())
            
            // Sauvegarder les préférences de personnalités
            editor.putBoolean("enable_personalities", enablePersonalitiesSwitch.isChecked)
            editor.putBoolean("enable_voice_response", enableVoiceResponseSwitch.isChecked)
            editor.putBoolean("enable_context_memory", enableContextMemorySwitch.isChecked)
            
            // Sauvegarder les fonctionnalités IA
            editor.putBoolean("enable_image_generation", enableImageGenerationSwitch.isChecked)
            editor.putBoolean("enable_code_generation", enableCodeGenerationSwitch.isChecked)
            editor.putBoolean("enable_translation", enableTranslationSwitch.isChecked)
            
            editor.apply()
            
            Toast.makeText(this, "Configuration IA sauvegardée avec succès", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur lors de la sauvegarde IA: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testAPIConnections() {
        // Créer un dialog personnalisé pour afficher les résultats
        val dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_1, null)
        val textView = dialogView.findViewById<TextView>(android.R.id.text1).apply {
            setTextColor(getColor(R.color.kitt_red))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
        }
        
        val dialog = AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setTitle("🧪 Diagnostic API IA")
            .setView(dialogView)
            .setPositiveButton("Fermer", null)
            .setNeutralButton("📄 Fichier Log", null)
            .setNegativeButton("ADB", null)
            .create()
        
        textView.text = "Initialisation du diagnostic...\n\nVeuillez patienter..."
        textView.setTextIsSelectable(true)
        textView.setPadding(32, 32, 32, 32)
        
        dialog.show()
        
        // Variable pour stocker le fichier log
        var logFile: File? = null
        
        // Style boutons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.kitt_red))
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(getColor(R.color.kitt_red))
            setOnClickListener {
                // Ouvrir le fichier log
                logFile?.let { file ->
                    openLogFile(file)
                } ?: run {
                    Toast.makeText(this@AIConfigurationActivity, "Fichier log non disponible", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(getColor(R.color.kitt_red))
            setOnClickListener {
                // Afficher commande logcat (optionnel)
                AlertDialog.Builder(this@AIConfigurationActivity, R.style.KittDialogTheme)
                    .setTitle("📋 Commande ADB (Optionnel)")
                    .setMessage("Pour voir les détails via PC:\n\nadb logcat -s API_TEST_EXPORT")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        
        // Créer le service pour tester
        val testService = KittAIService(this)
        
        lifecycleScope.launch {
            val diagnosticResult = StringBuilder()
            val testTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            
            try {
                // ═══════════════════════════════════════════════════════════
                // EXPORT LOGCAT - DÉBUT
                // ═══════════════════════════════════════════════════════════
                android.util.Log.i("API_TEST_EXPORT", "═══════════════════════════════════════════════════════════")
                android.util.Log.i("API_TEST_EXPORT", "🧪 TEST COMPLET DES APIs IA")
                android.util.Log.i("API_TEST_EXPORT", "═══════════════════════════════════════════════════════════")
                android.util.Log.i("API_TEST_EXPORT", "📅 Date: $testTimestamp")
                android.util.Log.i("API_TEST_EXPORT", "")
                
                diagnosticResult.appendLine("=== DIAGNOSTIC API IA ===\n")
                
                // 1. Vérifier la configuration
                diagnosticResult.appendLine("1. Configuration des clés API:")
                android.util.Log.i("API_TEST_EXPORT", "┌─ CONFIGURATION")
                
                val openaiKey = sharedPreferences.getString("openai_api_key", null)
                val anthropicKey = sharedPreferences.getString("anthropic_api_key", null)
                val huggingfaceKey = secureConfig.getHuggingFaceApiKey()
                val ollamaCloudKey = secureConfig.getOllamaCloudApiKey()
                
                diagnosticResult.appendLine("   OpenAI: ${if (!openaiKey.isNullOrEmpty()) "✓ Configurée (${openaiKey.length} chars)" else "✗ Non configurée"}")
                android.util.Log.i("API_TEST_EXPORT", "│ OpenAI: ${if (!openaiKey.isNullOrEmpty()) "✅ Configurée (${openaiKey.length} chars)" else "❌ Non configurée"}")
                
                diagnosticResult.appendLine("   Anthropic: ${if (!anthropicKey.isNullOrEmpty()) "✓ Configurée (${anthropicKey.length} chars)" else "✗ Non configurée"}")
                android.util.Log.i("API_TEST_EXPORT", "│ Anthropic: ${if (!anthropicKey.isNullOrEmpty()) "✅ Configurée (${anthropicKey.length} chars)" else "❌ Non configurée"}")
                
                diagnosticResult.appendLine("   Hugging Face: ${if (!huggingfaceKey.isNullOrEmpty()) "✓ Configurée (${huggingfaceKey.length} chars)" else "✗ Non configurée"}")
                android.util.Log.i("API_TEST_EXPORT", "│ Hugging Face: ${if (!huggingfaceKey.isNullOrEmpty()) "✅ Configurée (${huggingfaceKey.length} chars)" else "❌ Non configurée"}")
                
                val ollamaCloudModel = sharedPreferences.getString("ollama_cloud_model", null)
                diagnosticResult.appendLine("   Ollama Cloud: ${if (!ollamaCloudKey.isNullOrEmpty()) "✓ Configurée (${ollamaCloudKey.length} chars)" else "✗ Non configurée"}")
                android.util.Log.i("API_TEST_EXPORT", "│ ☁️  Ollama Cloud: ${if (!ollamaCloudKey.isNullOrEmpty()) "✅ Configurée (${ollamaCloudKey.length} chars)" else "❌ Non configurée"}")
                
                if (!ollamaCloudModel.isNullOrEmpty()) {
                    diagnosticResult.appendLine("   Modèle Cloud: $ollamaCloudModel")
                    android.util.Log.i("API_TEST_EXPORT", "│ 🤖 Modèle Cloud: $ollamaCloudModel")
                }
                
                val localServerUrl = sharedPreferences.getString("local_server_url", null)
                val localModelName = sharedPreferences.getString("local_model_name", null)
                diagnosticResult.appendLine("   Serveur Local: ${if (!localServerUrl.isNullOrEmpty()) "✓ Configuré ($localServerUrl)" else "✗ Non configuré"}")
                android.util.Log.i("API_TEST_EXPORT", "│ 💻 Serveur Local: ${if (!localServerUrl.isNullOrEmpty()) "✅ Configuré ($localServerUrl)" else "❌ Non configuré"}")
                
                if (!localModelName.isNullOrEmpty()) {
                    diagnosticResult.appendLine("   Modèle Local: $localModelName")
                    android.util.Log.i("API_TEST_EXPORT", "│ 🎯 Modèle Local: $localModelName")
                }
                android.util.Log.i("API_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                android.util.Log.i("API_TEST_EXPORT", "")
                
                diagnosticResult.appendLine("")
                
                // 2. Test de connexion
                diagnosticResult.appendLine("2. Test de connexion API:")
                diagnosticResult.appendLine("   Envoi d'une requête test...\n")
                android.util.Log.i("API_TEST_EXPORT", "┌─ TEST DE CONNEXION")
                
                // Mise à jour du dialog
                textView.post { textView.text = diagnosticResult.toString() }
                
                val testMessage = "Hello KITT, this is a connection test"
                android.util.Log.i("API_TEST_EXPORT", "│ 📝 Message test: $testMessage")
                val startTime = System.currentTimeMillis()
                
                val response = testService.processUserInput(testMessage)
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                // 3. Résultat
                diagnosticResult.appendLine("3. Résultat du test:")
                diagnosticResult.appendLine("   Temps de réponse: ${duration}ms")
                android.util.Log.i("API_TEST_EXPORT", "│ ⏱️  Temps réponse: ${duration}ms")
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("   Question: $testMessage")
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("   Réponse reçue:")
                diagnosticResult.appendLine("   \"${response.take(200)}${if (response.length > 200) "..." else ""}\"")
                android.util.Log.i("API_TEST_EXPORT", "│ 🤖 Réponse: ${response.take(150)}${if (response.length > 150) "..." else ""}")
                android.util.Log.i("API_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                android.util.Log.i("API_TEST_EXPORT", "")
                diagnosticResult.appendLine("")
                
                // 4. Analyse de la réponse
                diagnosticResult.appendLine("4. Analyse:")
                android.util.Log.i("API_TEST_EXPORT", "┌─ ANALYSE")
                
                when {
                    response.contains("mes capacités IA actuelles sont limitées sans connexion aux services cloud") -> {
                        diagnosticResult.appendLine("   ⚠ FALLBACK LOCAL utilisé")
                        diagnosticResult.appendLine("   Aucune API n'a répondu")
                        diagnosticResult.appendLine("   Vérifiez vos clés API")
                        android.util.Log.i("API_TEST_EXPORT", "│ ⚠️  FALLBACK LOCAL utilisé")
                        android.util.Log.i("API_TEST_EXPORT", "│ ❌ Aucune API n'a répondu")
                        android.util.Log.i("API_TEST_EXPORT", "│ 🔧 Action: Vérifiez vos clés API")
                    }
                    response.contains("Michael") || response.contains("KITT") -> {
                        diagnosticResult.appendLine("   ✓ Réponse de type KITT détectée")
                        android.util.Log.i("API_TEST_EXPORT", "│ ✅ Réponse de type KITT détectée")
                        if (duration < 100) {
                            diagnosticResult.appendLine("   → Probablement CACHE ou FALLBACK (très rapide)")
                            android.util.Log.i("API_TEST_EXPORT", "│ ⚡ Probablement CACHE ou FALLBACK (${duration}ms)")
                        } else {
                            diagnosticResult.appendLine("   → Probablement API GÉNÉRATIVE (réponse lente)")
                            diagnosticResult.appendLine("   ✓ TEST RÉUSSI !")
                            android.util.Log.i("API_TEST_EXPORT", "│ 🌐 API GÉNÉRATIVE (${duration}ms)")
                            android.util.Log.i("API_TEST_EXPORT", "│ ✅ TEST RÉUSSI !")
                        }
                    }
                    else -> {
                        diagnosticResult.appendLine("   ? Réponse inattendue")
                        android.util.Log.i("API_TEST_EXPORT", "│ ❓ Réponse inattendue")
                    }
                }
                android.util.Log.i("API_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                android.util.Log.i("API_TEST_EXPORT", "")
                
                // 5. Logs détaillés capturés
                val diagnosticLogs = testService.getDiagnosticLogs()
                if (diagnosticLogs.isNotEmpty()) {
                    diagnosticResult.appendLine("")
                    diagnosticResult.appendLine("5. Logs détaillés capturés:")
                    android.util.Log.i("API_TEST_EXPORT", "┌─ LOGS DÉTAILLÉS SYSTÈME")
                    diagnosticLogs.forEach { log ->
                        diagnosticResult.appendLine(log)
                        android.util.Log.i("API_TEST_EXPORT", "│ $log")
                    }
                    android.util.Log.i("API_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                }
                
                android.util.Log.i("API_TEST_EXPORT", "")
                android.util.Log.i("API_TEST_EXPORT", "═══════════════════════════════════════════════════════════")
                // ═══════════════════════════════════════════════════════════
                // EXPORT LOGCAT - FIN
                // ═══════════════════════════════════════════════════════════
                
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("=== FIN DU DIAGNOSTIC ===")
                
                // Écrire le diagnostic complet dans le fichier
                val fullLog = diagnosticResult.toString()
                logFile = writeLogToFile(fullLog, "diagnostic_api_${System.currentTimeMillis()}.log")
                
                // Afficher le diagnostic complet dans le dialog
                textView.post { textView.text = diagnosticResult.toString() }
                
            } catch (e: Exception) {
                android.util.Log.e("API_TEST_EXPORT", "❌ ERREUR: ${e.message}", e)
                android.util.Log.i("API_TEST_EXPORT", "═══════════════════════════════════════════════════════════")
                
                diagnosticResult.appendLine("\n✗ ERREUR DURANT LE TEST:")
                diagnosticResult.appendLine("   ${e.javaClass.simpleName}: ${e.message}")
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("Stack trace:")
                diagnosticResult.appendLine(e.stackTraceToString())
                
                // Écrire l'erreur dans le fichier
                logFile = writeLogToFile(diagnosticResult.toString(), "diagnostic_error_${System.currentTimeMillis()}.log")
                
                // Afficher l'erreur complète dans le dialog
                textView.post { textView.text = diagnosticResult.toString() }
                
                android.util.Log.e("AIConfigTest", "Test API failed", e)
            }
        }
    }
    
    /**
     * Écrit les logs dans un fichier sur le device
     * /storage/emulated/0/ChatAI-Files/logs/
     */
    private fun writeLogToFile(content: String, filename: String = "chatai.log"): File? {
        return try {
            // Créer le répertoire logs si nécessaire
            val logsDir = File(Environment.getExternalStorageDirectory(), "ChatAI-Files/logs")
            if (!logsDir.exists()) {
                val created = logsDir.mkdirs()
                android.util.Log.i("AIConfig", "📁 Création du répertoire logs: $created -> ${logsDir.absolutePath}")
            }
            
            val logFile = File(logsDir, filename)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            
            android.util.Log.i("AIConfig", "📝 Écriture du log dans: ${logFile.absolutePath}")
            
            FileWriter(logFile, true).use { writer ->
                writer.appendLine("═══════════════════════════════════════════════════════════")
                writer.appendLine("📅 $timestamp")
                writer.appendLine("═══════════════════════════════════════════════════════════")
                writer.appendLine(content)
                writer.appendLine("")
            }
            
            android.util.Log.i("AIConfig", "✅ Log écrit avec succès! Taille: ${logFile.length()} bytes")
            logFile
            
        } catch (e: Exception) {
            android.util.Log.e("AIConfig", "❌ Erreur écriture log fichier: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Ouvre le fichier log avec une app externe (éditeur de texte)
     */
    private fun openLogFile(logFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                logFile
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "Ouvrir le fichier log"))
            
        } catch (e: Exception) {
            // Fallback: copier le chemin dans le clipboard
            Toast.makeText(
                this,
                "Fichier log: ${logFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * ⭐ Auto-détection du serveur Ollama via Discovery Server (Phase 2)
     * Scanne le réseau local pour trouver le serveur de découverte
     */
    private fun autoDetectOllamaServer() {
        lifecycleScope.launch {
            try {
                // Afficher un dialog de progression
                val progressDialog = AlertDialog.Builder(this@AIConfigurationActivity)
                    .setTitle("🔍 Auto-Détection")
                    .setMessage("Recherche du serveur Ollama sur le réseau...\n\nAssurez-vous que ollama_discovery_server.ps1 tourne sur votre PC.")
                    .setCancelable(false)
                    .create()
                
                progressDialog.show()
                
                // Obtenir l'IP du device pour déduire le subnet
                val deviceIP = getDeviceIP()
                android.util.Log.d("AutoDetect", "Device IP: $deviceIP")
                
                if (deviceIP == null) {
                    progressDialog.dismiss()
                    Toast.makeText(this@AIConfigurationActivity, 
                        "❌ Impossible de détecter l'IP du device", 
                        Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                // Scanner les IPs courantes (*.1 = gateway/PC souvent)
                val subnet = deviceIP.substringBeforeLast(".")
                val discoveryPort = 8889
                val testIPs = listOf(
                    "$subnet.1",   // Souvent le PC/routeur
                    "$subnet.100", // Adresse commune PC
                    "$subnet.101",
                    "$subnet.2"
                )
                
                android.util.Log.d("AutoDetect", "Scanning subnet: $subnet.*")
                
                var foundConfig: JSONObject? = null
                val httpClient = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build()
                
                // Scanner les IPs (sans break dans lambda - utiliser firstOrNull à la place)
                run scanLoop@ {
                    for (ip in testIPs) {
                        if (foundConfig != null) return@scanLoop // Sortir si déjà trouvé
                        
                        try {
                            val url = "http://$ip:$discoveryPort/discover"
                            android.util.Log.d("AutoDetect", "Testing: $url")
                            
                            val request = Request.Builder()
                                .url(url)
                                .get()
                                .build()
                            
                            val response = httpClient.newCall(request).execute()
                            
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                body?.let {
                                    val json = JSONObject(it)
                                    if (json.optString("app") == "ChatAI-Discovery") {
                                        foundConfig = json
                                        android.util.Log.d("AutoDetect", "✅ Found at $url")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Continue au prochain IP
                            android.util.Log.d("AutoDetect", "Failed $ip: ${e.message}")
                        }
                    }
                }
                
                progressDialog.dismiss()
                
                if (foundConfig != null) {
                    // Config trouvée !
                    val serverInfo = foundConfig!!.getJSONObject("server")
                    val chatUrl = serverInfo.getString("chat_url")
                    val recommendedModel = foundConfig!!.optString("recommended_model", "gemma3:1b")
                    val models = foundConfig!!.getJSONArray("models")
                    
                    // Construire le message avec les modèles disponibles
                    val modelsList = StringBuilder()
                    for (i in 0 until models.length()) {
                        modelsList.append("\n  • ${models.getString(i)}")
                    }
                    
                    // Afficher un dialog de confirmation
                    AlertDialog.Builder(this@AIConfigurationActivity)
                        .setTitle("✅ Serveur Ollama Détecté!")
                        .setMessage("Configuration trouvée:\n\n" +
                                "URL: $chatUrl\n" +
                                "Modèles disponibles:$modelsList\n\n" +
                                "Modèle recommandé: $recommendedModel\n\n" +
                                "Appliquer cette configuration?")
                        .setPositiveButton("Oui") { _, _ ->
                            // Remplir automatiquement les champs
                            localServerUrlInput.setText(chatUrl)
                            localModelNameInput.setText(recommendedModel)
                            
                            Toast.makeText(this@AIConfigurationActivity, 
                                "✅ Configuration appliquée! Cliquez SAUVEGARDER", 
                                Toast.LENGTH_LONG).show()
                        }
                        .setNegativeButton("Non", null)
                        .show()
                    
                } else {
                    // Aucun serveur trouvé
                    AlertDialog.Builder(this@AIConfigurationActivity)
                        .setTitle("❌ Serveur Non Trouvé")
                        .setMessage("Aucun serveur de découverte Ollama détecté sur le réseau.\n\n" +
                                "Vérifiez que:\n" +
                                "1. ollama_discovery_server.ps1 tourne sur votre PC\n" +
                                "2. PC et device sont sur le même WiFi\n" +
                                "3. Le pare-feu autorise le port 8889")
                        .setPositiveButton("OK", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("AutoDetect", "Error during auto-detection", e)
                Toast.makeText(this@AIConfigurationActivity, 
                    "❌ Erreur: ${e.message}", 
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Obtient l'IP locale du device Android
     */
    private fun getDeviceIP(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    
                    // Chercher une adresse IPv4 non-loopback
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress
                        // Filtrer les IPs locales (192.168.*.* ou 172.*.*.*)
                        if (ip.startsWith("192.168.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AutoDetect", "Failed to get device IP", e)
        }
        return null
    }
    
    private fun resetAIToDefaults() {
        // Réinitialiser aux valeurs par défaut
        openaiApiKeyInput.setText("")
        huggingfaceApiKeyInput.setText("")
        anthropicApiKeyInput.setText("")
        defaultModelInput.setText("gpt-3.5-turbo")
        maxTokensInput.setText("1000")
        temperatureInput.setText("0.7")
        
        enablePersonalitiesSwitch.isChecked = true
        enableVoiceResponseSwitch.isChecked = true
        enableContextMemorySwitch.isChecked = true
        enableImageGenerationSwitch.isChecked = false
        enableCodeGenerationSwitch.isChecked = true
        enableTranslationSwitch.isChecked = true
        
        Toast.makeText(this, "Configuration IA réinitialisée", Toast.LENGTH_SHORT).show()
    }
    
    private fun testTTSVoice() {
        val ttsSpeed = 0.5f + (ttsSpeedSeekBar.progress / 20.0f)
        val ttsPitch = 0.5f + (ttsPitchSeekBar.progress / 20.0f)
        
        var testTTS: TextToSpeech? = null
        testTTS = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                testTTS?.language = Locale.FRENCH
                testTTS?.setSpeechRate(ttsSpeed)
                testTTS?.setPitch(ttsPitch)
                
                val testMessage = "Bonjour Michael. Je suis KITT, votre ordinateur de bord. Mes systèmes sont opérationnels."
                testTTS?.speak(testMessage, TextToSpeech.QUEUE_FLUSH, null, "tts_test")
                
                Toast.makeText(
                    this, 
                    "Test TTS: vitesse ${String.format("%.1f", ttsSpeed)}x, tonalité ${String.format("%.1f", ttsPitch)}x", 
                    Toast.LENGTH_SHORT
                ).show()
                
                // Arrêter TTS après 10 secondes
                android.os.Handler(mainLooper).postDelayed({
                    testTTS?.stop()
                    testTTS?.shutdown()
                }, 10000)
            } else {
                Toast.makeText(this, "Erreur TTS: impossible d'initialiser", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun maskApiKey(key: String): String {
        if (key.length <= 8) return "*".repeat(key.length)
        return key.substring(0, 4) + "*".repeat(key.length - 8) + key.substring(key.length - 4)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ÉDITEUR JSON
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun setupJsonEditorButton() {
        val editJsonButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.editJsonButton)
        editJsonButton?.setOnClickListener {
            showJsonEditorDialog()
        }
    }
    
    private fun showJsonEditorDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_config_editor, null)
        val jsonEditor = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.jsonEditor)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnValidate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnValidate)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        
        // Charger le contenu actuel de ai_config.json
        val currentContent = try {
            AiConfigManager.readConfigJson(this)
        } catch (e: Exception) {
            android.util.Log.e("AIConfig", "Error reading config: ${e.message}")
            Toast.makeText(this, "Erreur lecture fichier: ${e.message}", Toast.LENGTH_SHORT).show()
            "{}"
        }
        
        jsonEditor.setText(currentContent)
        
        // Dialog
        val dialog = AlertDialog.Builder(this, R.style.KittDialogTheme)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        // Bouton Annuler
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        // Bouton Valider JSON
        btnValidate.setOnClickListener {
            val jsonText = jsonEditor.text?.toString() ?: ""
            if (validateJson(jsonText)) {
                Toast.makeText(this, "✅ JSON valide", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "❌ JSON invalide - Vérifiez la syntaxe", Toast.LENGTH_LONG).show()
            }
        }
        
        // Bouton Sauvegarder
        btnSave.setOnClickListener {
            val jsonText = jsonEditor.text?.toString() ?: ""
            
            if (!validateJson(jsonText)) {
                Toast.makeText(this, "❌ JSON invalide - Impossible de sauvegarder", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            try {
                AiConfigManager.writeConfigJson(this, jsonText)
                Toast.makeText(this, "✅ ai_config.json sauvegardé", Toast.LENGTH_SHORT).show()
                
                // Recharger les settings depuis les SharedPreferences synchronisées
                loadCurrentSettings()
                
                dialog.dismiss()
                
            } catch (e: Exception) {
                android.util.Log.e("AIConfig", "Error saving ai_config.json: ${e.message}")
                Toast.makeText(this, "Erreur sauvegarde: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        
        dialog.show()
    }
    
    private fun validateJson(jsonText: String): Boolean {
        return try {
            org.json.JSONObject(jsonText)
            true
        } catch (e: Exception) {
            android.util.Log.e("AIConfig", "JSON validation error: ${e.message}")
            false
        }
    }
}

