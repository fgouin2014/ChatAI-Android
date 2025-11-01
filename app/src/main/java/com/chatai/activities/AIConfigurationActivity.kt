package com.chatai.activities

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.chatai.R
import com.chatai.services.KittAIService
import kotlinx.coroutines.launch
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ProgressBar
import android.view.View
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Configuration spécialisée pour l'IA
 * Focus sur les APIs, modèles, personnalités et paramètres IA
 */
class AIConfigurationActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    
    // Configuration des APIs IA
    private lateinit var openaiApiKeyInput: TextInputEditText
    private lateinit var huggingfaceApiKeyInput: TextInputEditText
    private lateinit var anthropicApiKeyInput: TextInputEditText
    
    // Configuration Ollama Cloud
    private lateinit var ollamaCloudApiKeyInput: TextInputEditText
    private lateinit var ollamaCloudModelInput: TextInputEditText
    
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_configuration)
        
        // Initialiser les préférences
        sharedPreferences = getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        
        // Initialiser les vues
        initializeViews()
        
        // Charger les paramètres existants
        loadCurrentSettings()
        
        // Configurer les listeners
        setupListeners()
    }
    
    private fun initializeViews() {
        // APIs
        openaiApiKeyInput = findViewById(R.id.openaiApiKeyInput)
        huggingfaceApiKeyInput = findViewById(R.id.huggingfaceApiKeyInput)
        anthropicApiKeyInput = findViewById(R.id.anthropicApiKeyInput)
        
        // Ollama Cloud
        ollamaCloudApiKeyInput = findViewById(R.id.ollamaCloudApiKeyInput)
        ollamaCloudModelInput = findViewById(R.id.ollamaCloudModelInput)
        
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
    
    private fun loadCurrentSettings() {
        // Charger les clés API (partiellement masquées)
        val openaiKey = sharedPreferences.getString("openai_api_key", "")
        openaiApiKeyInput.setText(if (openaiKey.isNullOrEmpty()) "" else maskApiKey(openaiKey))
        
        val huggingfaceKey = sharedPreferences.getString("huggingface_api_key", "")
        huggingfaceApiKeyInput.setText(if (huggingfaceKey.isNullOrEmpty()) "" else maskApiKey(huggingfaceKey))
        
        val anthropicKey = sharedPreferences.getString("anthropic_api_key", "")
        anthropicApiKeyInput.setText(if (anthropicKey.isNullOrEmpty()) "" else maskApiKey(anthropicKey))
        
        // Charger la configuration Ollama Cloud
        val ollamaCloudKey = sharedPreferences.getString("ollama_cloud_api_key", "")
        ollamaCloudApiKeyInput.setText(if (ollamaCloudKey.isNullOrEmpty()) "" else maskApiKey(ollamaCloudKey))
        
        val ollamaCloudModel = sharedPreferences.getString("ollama_cloud_model", "gpt-oss:120b-cloud")
        ollamaCloudModelInput.setText(ollamaCloudModel)
        
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
        
        // Bouton historique des conversations
        findViewById<MaterialButton>(R.id.viewHistoryButton).setOnClickListener {
            startActivity(android.content.Intent(this, ConversationHistoryActivity::class.java))
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
                editor.putString("huggingface_api_key", huggingfaceKey)
            }
            
            val anthropicKey = anthropicApiKeyInput.text.toString().trim().replace(Regex("\\s+"), "")
            if (anthropicKey.isNotEmpty() && !anthropicKey.contains("*")) {
                editor.putString("anthropic_api_key", anthropicKey)
            }
            
            // Sauvegarder la configuration Ollama Cloud
            val ollamaCloudKey = ollamaCloudApiKeyInput.text.toString().trim().replace(Regex("\\s+"), "")
            if (ollamaCloudKey.isNotEmpty() && !ollamaCloudKey.contains("*")) {
                editor.putString("ollama_cloud_api_key", ollamaCloudKey)
            }
            
            val ollamaCloudModel = ollamaCloudModelInput.text.toString().trim()
            editor.putString("ollama_cloud_model", ollamaCloudModel)
            
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
        val textView = dialogView.findViewById<TextView>(android.R.id.text1)
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Diagnostic API IA")
            .setView(dialogView)
            .setPositiveButton("Fermer", null)
            .create()
        
        textView.text = "Initialisation du diagnostic...\n\nVeuillez patienter..."
        textView.setTextIsSelectable(true)
        textView.setPadding(32, 32, 32, 32)
        
        dialog.show()
        
        // Créer le service pour tester
        val testService = KittAIService(this)
        
        lifecycleScope.launch {
            val diagnosticResult = StringBuilder()
            
            try {
                diagnosticResult.appendLine("=== DIAGNOSTIC API IA ===\n")
                
                // 1. Vérifier la configuration
                diagnosticResult.appendLine("1. Configuration des clés API:")
                val openaiKey = sharedPreferences.getString("openai_api_key", null)
                val anthropicKey = sharedPreferences.getString("anthropic_api_key", null)
                val huggingfaceKey = sharedPreferences.getString("huggingface_api_key", null)
                val ollamaCloudKey = sharedPreferences.getString("ollama_cloud_api_key", null)
                
                diagnosticResult.appendLine("   OpenAI: ${if (!openaiKey.isNullOrEmpty()) "✓ Configurée (${openaiKey.length} chars)" else "✗ Non configurée"}")
                diagnosticResult.appendLine("   Anthropic: ${if (!anthropicKey.isNullOrEmpty()) "✓ Configurée (${anthropicKey.length} chars)" else "✗ Non configurée"}")
                diagnosticResult.appendLine("   Hugging Face: ${if (!huggingfaceKey.isNullOrEmpty()) "✓ Configurée (${huggingfaceKey.length} chars)" else "✗ Non configurée"}")
                
                val ollamaCloudModel = sharedPreferences.getString("ollama_cloud_model", null)
                diagnosticResult.appendLine("   Ollama Cloud: ${if (!ollamaCloudKey.isNullOrEmpty()) "✓ Configurée (${ollamaCloudKey.length} chars)" else "✗ Non configurée"}")
                if (!ollamaCloudModel.isNullOrEmpty()) {
                    diagnosticResult.appendLine("   Modèle Cloud: $ollamaCloudModel")
                }
                
                val localServerUrl = sharedPreferences.getString("local_server_url", null)
                val localModelName = sharedPreferences.getString("local_model_name", null)
                diagnosticResult.appendLine("   Serveur Local: ${if (!localServerUrl.isNullOrEmpty()) "✓ Configuré ($localServerUrl)" else "✗ Non configuré"}")
                if (!localModelName.isNullOrEmpty()) {
                    diagnosticResult.appendLine("   Modèle Local: $localModelName")
                }
                diagnosticResult.appendLine("")
                
                // 2. Test de connexion
                diagnosticResult.appendLine("2. Test de connexion API:")
                diagnosticResult.appendLine("   Envoi d'une requête test...\n")
                
                // Mise à jour du dialog
                textView.post { textView.text = diagnosticResult.toString() }
                
                val testMessage = "Hello KITT, this is a connection test"
                val startTime = System.currentTimeMillis()
                
                val response = testService.processUserInput(testMessage)
                
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                
                // 3. Résultat
                diagnosticResult.appendLine("3. Résultat du test:")
                diagnosticResult.appendLine("   Temps de réponse: ${duration}ms")
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("   Question: $testMessage")
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("   Réponse reçue:")
                diagnosticResult.appendLine("   \"${response.take(200)}${if (response.length > 200) "..." else ""}\"")
                diagnosticResult.appendLine("")
                
                // 4. Analyse de la réponse
                diagnosticResult.appendLine("4. Analyse:")
                when {
                    response.contains("mes capacités IA actuelles sont limitées sans connexion aux services cloud") -> {
                        diagnosticResult.appendLine("   ⚠ FALLBACK LOCAL utilisé")
                        diagnosticResult.appendLine("   Aucune API n'a répondu")
                        diagnosticResult.appendLine("   Vérifiez vos clés API")
                    }
                    response.contains("Michael") || response.contains("KITT") -> {
                        diagnosticResult.appendLine("   ✓ Réponse de type KITT détectée")
                        if (duration < 100) {
                            diagnosticResult.appendLine("   → Probablement CACHE ou FALLBACK (très rapide)")
                        } else {
                            diagnosticResult.appendLine("   → Probablement API GÉNÉRATIVE (réponse lente)")
                            diagnosticResult.appendLine("   ✓ TEST RÉUSSI !")
                        }
                    }
                    else -> {
                        diagnosticResult.appendLine("   ? Réponse inattendue")
                    }
                }
                
                // 5. Logs détaillés capturés
                val diagnosticLogs = testService.getDiagnosticLogs()
                if (diagnosticLogs.isNotEmpty()) {
                    diagnosticResult.appendLine("")
                    diagnosticResult.appendLine("5. Logs détaillés capturés:")
                    diagnosticLogs.forEach { log ->
                        diagnosticResult.appendLine(log)
                    }
                }
                
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("=== FIN DU DIAGNOSTIC ===")
                
                // Mise à jour finale
                textView.post { textView.text = diagnosticResult.toString() }
                
                android.util.Log.d("AIConfigTest", diagnosticResult.toString())
                
            } catch (e: Exception) {
                diagnosticResult.appendLine("\n✗ ERREUR DURANT LE TEST:")
                diagnosticResult.appendLine("   ${e.javaClass.simpleName}: ${e.message}")
                diagnosticResult.appendLine("")
                diagnosticResult.appendLine("Stack trace:")
                diagnosticResult.appendLine(e.stackTraceToString())
                
                textView.post { textView.text = diagnosticResult.toString() }
                
                android.util.Log.e("AIConfigTest", "Test API failed", e)
            }
        }
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
}
