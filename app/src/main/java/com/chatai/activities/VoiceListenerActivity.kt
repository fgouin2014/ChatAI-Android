package com.chatai.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chatai.R
import com.chatai.audio.AudioEngineConfig
import com.chatai.audio.WhisperServerRecognizer
import com.chatai.services.KittAIService
import com.chatai.services.KittActionCallback
import kotlinx.coroutines.*
import java.util.Locale

/**
 * 🎤 Activity Transparente pour Écoute Vocale Rapide
 * 
 * Lancée depuis le Quick Settings Tile
 * S'affiche par-dessus l'app actuelle
 * Écoute → Traite → Répond → Se ferme automatiquement
 */
class VoiceListenerActivity : Activity(), RecognitionListener, TextToSpeech.OnInitListener, KittActionCallback {
    
    companion object {
        private const val TAG = "VoiceListenerActivity"
        private const val REQUEST_RECORD_AUDIO = 1001
    }
    
    // UI - États visuels
    private lateinit var statusText: TextView
    private lateinit var kittIconListening: android.widget.ImageView
    private lateinit var scannerContainer: android.view.ViewGroup
    private val scanLeds = mutableListOf<android.widget.ImageView>()
    private lateinit var vuMeterContainer: android.view.ViewGroup
    private val vuLeds = mutableListOf<android.widget.ImageView>()
    
    // Voice Recognition
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var useWhisperServer = false
    private var audioEngineConfig: AudioEngineConfig? = null
    private var whisperRecognizer: WhisperServerRecognizer? = null
    
    // Animations
    private var scannerAnimation: Runnable? = null
    private var vuMeterAnimation: Runnable? = null
    private val animationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Position du scanner
    private var scannerPosition = 0
    private var scannerDirection = 1
    
    // TTS
    private var textToSpeech: TextToSpeech? = null
    private var isTTSReady = false
    
    // AI Service
    private lateinit var kittAIService: KittAIService
    
    // Coroutine
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Timeout de sécurité
    private var timeoutJob: Job? = null
    
    // Flags pour ouvrir MainActivity après le TTS
    private var shouldOpenMainActivityAfterTTS = false
    private var shouldActivateKitt = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fenêtre transparente par-dessus tout
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        
        setContentView(R.layout.activity_voice_listener)
        
        // Initialiser les vues
        statusText = findViewById(R.id.voiceStatusText)
        kittIconListening = findViewById(R.id.kittIconListening)
        scannerContainer = findViewById(R.id.scannerContainer)
        vuMeterContainer = findViewById(R.id.vuMeterContainer)
        
        // Récupérer toutes les LEDs du scanner KITT (8 LEDs rouges)
        scanLeds.add(findViewById(R.id.scanLed1))
        scanLeds.add(findViewById(R.id.scanLed2))
        scanLeds.add(findViewById(R.id.scanLed3))
        scanLeds.add(findViewById(R.id.scanLed4))
        scanLeds.add(findViewById(R.id.scanLed5))
        scanLeds.add(findViewById(R.id.scanLed6))
        scanLeds.add(findViewById(R.id.scanLed7))
        scanLeds.add(findViewById(R.id.scanLed8))
        
        // Récupérer toutes les LEDs du VU-meter (9 LEDs vertes en 3 colonnes)
        vuLeds.add(findViewById(R.id.vuLeftLed1))
        vuLeds.add(findViewById(R.id.vuLeftLed2))
        vuLeds.add(findViewById(R.id.vuLeftLed3))
        vuLeds.add(findViewById(R.id.vuCenterLed1))
        vuLeds.add(findViewById(R.id.vuCenterLed2))
        vuLeds.add(findViewById(R.id.vuCenterLed3))
        vuLeds.add(findViewById(R.id.vuRightLed1))
        vuLeds.add(findViewById(R.id.vuRightLed2))
        vuLeds.add(findViewById(R.id.vuRightLed3))
        
        Log.i(TAG, "🎤 VoiceListenerActivity created")
        
        // Initialiser TTS
        textToSpeech = TextToSpeech(this, this)
        
        // Initialiser AI Service
        val sharedPrefs = getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        val personality = sharedPrefs.getString("selected_personality", "KITT") ?: "KITT"
        kittAIService = KittAIService(this, personality, "vocal", actionCallback = this)

        audioEngineConfig = AudioEngineConfig.fromContext(this)
        useWhisperServer = audioEngineConfig?.engine?.equals("whisper_server", ignoreCase = true) == true
        if (useWhisperServer) {
            whisperRecognizer = WhisperServerRecognizer(audioEngineConfig!!, object : WhisperServerRecognizer.Callback {
                override fun onReady() {
                    runOnUiThread {
                        showListeningState()
                        statusText.text = "🎤 Parlez maintenant!"
                    }
                }

                override fun onSpeechStart() {
                    runOnUiThread {
                        statusText.text = "🗣️ Écoute en cours..."
                    }
                }

                override fun onRmsChanged(rmsDb: Float) {
                    // Pas de VU-meter dédié ici mais on peut animer plus tard
                }

                override fun onResult(text: String) {
                    isListening = false
                    runOnUiThread {
                        statusText.text = "💭 KITT réfléchit..."
                    }
                    processVoiceCommand(text)
                }

                override fun onError(message: String) {
                    isListening = false
                    Log.e(TAG, "WhisperServerRecognizer error: $message")
                    runOnUiThread {
                        statusText.text = "Erreur STT"
                        finishAfterDelay(2000)
                    }
                }
            })
        }
        
        // Timeout de sécurité - fermer après 15 secondes maximum
        startSafetyTimeout()
        
        // Vérifier permissions et démarrer
        if (checkAudioPermission()) {
            startEngineListening()
        } else {
            requestAudioPermission()
        }
    }
    
    /**
     * Timeout de sécurité - fermer l'activity si ça bloque
     * ⚠️ Ce timeout est ANNULÉ dès que le TTS commence à parler
     */
    private fun startSafetyTimeout() {
        timeoutJob = coroutineScope.launch {
            delay(30000) // 30 secondes max (augmenté pour les longues réponses)
            Log.e(TAG, "⏱️ TIMEOUT 30s - Fermeture forcée")
            runOnUiThread {
                statusText.text = "Timeout - Fermeture..."
            }
            finish()
        }
    }
    
    /**
     * Annuler le timeout si tout va bien
     */
    private fun cancelSafetyTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }
    
    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    private fun startEngineListening() {
        if (useWhisperServer) {
            statusText.text = "Initialisation Whisper..."
            isListening = true
            whisperRecognizer?.startListening()
        } else {
            initializeSpeechRecognizer()
            startListening()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startEngineListening()
            } else {
                statusText.text = "Permission audio refusée"
                finishAfterDelay(2000)
            }
        }
    }
    
    private fun initializeSpeechRecognizer() {
        if (useWhisperServer) {
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(this)
            Log.i(TAG, "✅ SpeechRecognizer initialized")
        }
    }
    
    private fun startListening() {
        if (useWhisperServer) return
        if (isListening) return
        
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CANADA_FRENCH)
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            isListening = true
            speechRecognizer?.startListening(intent)
            
            statusText.text = "🎤 En écoute..."
            Log.i(TAG, "🎤 Listening started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening", e)
            statusText.text = "Erreur reconnaissance vocale"
            finishAfterDelay(2000)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // RecognitionListener Implementation
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onReadyForSpeech(params: Bundle?) {
        Log.i(TAG, "✅ onReadyForSpeech - Micro prêt!")
        runOnUiThread {
            showListeningState()
            statusText.text = "🎤 Parlez maintenant!"
        }
    }
    
    /**
     * ÉTAT 1: ÉCOUTE - Afficher l'icône KITT
     */
    private fun showListeningState() {
        stopAllAnimations()
        kittIconListening.visibility = android.view.View.VISIBLE
        scannerContainer.visibility = android.view.View.GONE
        vuMeterContainer.visibility = android.view.View.GONE
    }
    
    override fun onBeginningOfSpeech() {
        Log.i(TAG, "✅ onBeginningOfSpeech - Détection parole!")
        statusText.text = "🗣️ Écoute en cours..."
    }
    
    override fun onRmsChanged(rmsdB: Float) {
        // Niveau audio - optionnel
    }
    
    override fun onBufferReceived(buffer: ByteArray?) {}
    
    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
        statusText.text = "⏳ Traitement..."
        isListening = false
    }
    
    override fun onError(error: Int) {
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH - Aucune parole détectée"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT - Temps écoulé"
            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT - Erreur client"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS - Permissions manquantes"
            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK - Erreur réseau"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT - Timeout réseau"
            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO - Erreur audio"
            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER - Erreur serveur"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY - Recognizer occupé"
            else -> "ERROR_UNKNOWN ($error)"
        }
        
        Log.e(TAG, "❌ Speech recognition error: $errorMsg")
        isListening = false
        
        statusText.text = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Aucune parole détectée"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Temps écoulé"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission micro manquante"
            else -> "Erreur reconnaissance ($error)"
        }
        
        cancelSafetyTimeout()
        finishAfterDelay(2000)
    }
    
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        
        Log.i(TAG, "📝 onResults - Matches: ${matches?.size ?: 0}")
        matches?.forEachIndexed { index, match ->
            Log.i(TAG, "  [$index] '$match'")
        }
        
        if (!matches.isNullOrEmpty()) {
            val userInput = matches[0]
            Log.i(TAG, "✅ Recognized: '$userInput'")
            
            statusText.text = "💭 KITT réfléchit..."
            
            // Traiter la commande vocale
            processVoiceCommand(userInput)
        } else {
            Log.e(TAG, "❌ No matches found")
            statusText.text = "Rien compris"
            cancelSafetyTimeout()
            finishAfterDelay(2000)
        }
    }
    
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AI Processing
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun processVoiceCommand(userInput: String) {
        Log.i(TAG, "🔄 Processing voice command: '$userInput'")
        
        // ÉTAT 2: RÉFLEXION - Afficher l'œil scanner
        runOnUiThread {
            showThinkingState()
            statusText.text = "💭 KITT réfléchit..."
        }
        
        coroutineScope.launch {
            try {
                val response = kittAIService.processUserInput(userInput)
                
                Log.i(TAG, "✅ AI Response: '$response'")
                
                // ÉTAT 3: PAROLE - Afficher le VU-meter
                runOnUiThread {
                    showSpeakingState()
                    statusText.text = "🔊 KITT répond..."
                }
                
                // Parler la réponse
                speakResponse(response)
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing command", e)
                runOnUiThread {
                    showListeningState()
                    statusText.text = "Erreur traitement"
                }
                cancelSafetyTimeout()
                finishAfterDelay(2000)
            }
        }
    }
    
    /**
     * ÉTAT 2: RÉFLEXION - Afficher le scanner LED rouge
     */
    private fun showThinkingState() {
        stopAllAnimations()
        kittIconListening.visibility = android.view.View.GONE
        scannerContainer.visibility = android.view.View.VISIBLE
        vuMeterContainer.visibility = android.view.View.GONE
        
        // Animation du scanner KITT
        startScannerAnimation()
    }
    
    /**
     * ÉTAT 3: PAROLE - Afficher le VU-meter LED vert
     */
    private fun showSpeakingState() {
        stopAllAnimations()
        kittIconListening.visibility = android.view.View.GONE
        scannerContainer.visibility = android.view.View.GONE
        vuMeterContainer.visibility = android.view.View.VISIBLE
        
        // Animation du VU-meter
        startVuMeterAnimation()
    }
    
    /**
     * Animation du scanner KITT (8 LEDs rouges qui vont-viennent)
     */
    private fun startScannerAnimation() {
        scannerPosition = 0
        scannerDirection = 1
        
        scannerAnimation = object : Runnable {
            override fun run() {
                // Éteindre toutes les LEDs
                scanLeds.forEach { it.setImageResource(R.drawable.led_red_off) }
                
                // Allumer la LED courante et ses voisines (effet de traînée)
                scanLeds.getOrNull(scannerPosition)?.setImageResource(R.drawable.led_red)
                scanLeds.getOrNull(scannerPosition - 1)?.setImageResource(R.drawable.led_red)
                scanLeds.getOrNull(scannerPosition + 1)?.setImageResource(R.drawable.led_red)
                
                // Avancer la position
                scannerPosition += scannerDirection
                
                // Inverser la direction aux extrémités
                if (scannerPosition >= scanLeds.size - 1) {
                    scannerDirection = -1
                } else if (scannerPosition <= 0) {
                    scannerDirection = 1
                }
                
                // Répéter l'animation
                animationHandler.postDelayed(this, 60) // 60ms = vitesse KITT classique
            }
        }
        
        animationHandler.post(scannerAnimation!!)
    }
    
    /**
     * Animation du VU-meter (9 LEDs vertes, 3 colonnes)
     * Pattern: bas→haut, de gauche à droite
     */
    private fun startVuMeterAnimation() {
        var frame = 0
        
        vuMeterAnimation = object : Runnable {
            override fun run() {
                // Éteindre toutes les LEDs
                vuLeds.forEach { it.setImageResource(R.drawable.led_green_off) }
                
                // Allumer les LEDs selon un pattern d'animation
                // Pattern: vague qui monte et descend
                val pattern = listOf(
                    listOf(0, 3, 6),           // Bas uniquement
                    listOf(0, 1, 3, 4, 6, 7),  // Bas + Milieu
                    listOf(0, 1, 2, 3, 4, 5, 6, 7, 8), // Toutes
                    listOf(0, 1, 3, 4, 6, 7),  // Bas + Milieu
                    listOf(0, 3, 6)            // Bas uniquement
                )
                
                val currentPattern = pattern[frame % pattern.size]
                currentPattern.forEach { index ->
                    vuLeds.getOrNull(index)?.setImageResource(R.drawable.led_green)
                }
                
                frame++
                
                // Répéter l'animation
                animationHandler.postDelayed(this, 150) // 150ms par frame
            }
        }
        
        animationHandler.post(vuMeterAnimation!!)
    }
    
    /**
     * Arrêter toutes les animations
     */
    private fun stopAllAnimations() {
        scannerAnimation?.let { animationHandler.removeCallbacks(it) }
        vuMeterAnimation?.let { animationHandler.removeCallbacks(it) }
        scannerAnimation = null
        vuMeterAnimation = null
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // TTS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.CANADA_FRENCH)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS French not supported")
            } else {
                isTTSReady = true
                Log.i(TAG, "✅ TTS initialized")
                
                // Configurer la voix KITT
                textToSpeech?.setPitch(0.9f)
                textToSpeech?.setSpeechRate(1.0f)
            }
            
            // Listener pour fermer l'activity après la parole
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.i(TAG, "🔊 TTS started speaking")
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.i(TAG, "✅ TTS finished speaking")
                    
                    // Si on doit ouvrir MainActivity, le faire MAINTENANT
                    if (shouldOpenMainActivityAfterTTS) {
                        val intent = Intent(this@VoiceListenerActivity, com.chatai.MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        
                        // Si on doit activer KITT, passer l'extra
                        if (shouldActivateKitt) {
                            Log.i(TAG, "🚀 Opening MainActivity + Activating KITT")
                            intent.putExtra("activate_kitt", true)
                        } else {
                            Log.i(TAG, "🚀 Opening MainActivity (ChatAI)")
                        }
                        
                        startActivity(intent)
                    }
                    
                    // Fermer l'activity après 1 seconde
                    finishAfterDelay(1000)
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "❌ TTS error - Closing activity")
                    finishAfterDelay(500)
                }
            })
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }
    
    private fun speakResponse(text: String) {
        if (!isTTSReady) {
            Log.e(TAG, "❌ TTS not ready - Closing")
            cancelSafetyTimeout()
            finishAfterDelay(1000)
            return
        }
        
        // Nettoyer Markdown avant TTS (retire *, **, _, etc.)
        val cleanText = cleanMarkdownForTTS(text)
        
        Log.i(TAG, "🔊 Speaking: '$cleanText'")
        
        // ⭐ ANNULER le timeout de sécurité - le TTS va gérer la fermeture
        cancelSafetyTimeout()
        
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tts_response")
        
        val result = textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, "tts_response")
        
        if (result == TextToSpeech.ERROR) {
            Log.e(TAG, "❌ TTS speak() returned ERROR")
            finishAfterDelay(1000)
        } else {
            Log.i(TAG, "✅ TTS speak() started successfully - Waiting for onDone()")
        }
    }
    
    /**
     * Nettoie le formatage Markdown pour TTS
     * Identique à KittTTSManager.cleanMarkdownForTTS()
     */
    private fun cleanMarkdownForTTS(text: String): String {
        var cleaned = text
        
        // Retirer gras/italique (ordre important: ** avant *)
        cleaned = cleaned.replace("**", "")
        cleaned = cleaned.replace("*", "")
        cleaned = cleaned.replace("__", "")
        cleaned = cleaned.replace("_", "")
        
        // Retirer liens [texte](url) → garder juste le texte
        cleaned = cleaned.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        
        // Retirer code inline `code`
        cleaned = cleaned.replace("`", "")
        
        // Retirer headings ### (début de ligne)
        cleaned = cleaned.replace(Regex("(?m)^#{1,6}\\s+"), "")
        
        // Retirer blockquotes > (début de ligne)
        cleaned = cleaned.replace(Regex("(?m)^>\\s+"), "")
        
        // Retirer listes - ou * (début de ligne)
        cleaned = cleaned.replace(Regex("(?m)^[\\-\\*]\\s+"), "")
        
        // Nettoyer espaces multiples
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        return cleaned.trim()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // KittActionCallback Implementation (pour Function Calling)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onOpenArcade() {
        // Fermer cette activity et ouvrir l'arcade
        val intent = Intent(this, com.chatai.GameListActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onOpenMusic() {
        // Ouvrir l'app principale pour la musique
        openMainApp()
    }
    
    override fun onOpenConfig() {
        val intent = Intent(this, com.chatai.activities.AIConfigurationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onOpenHistory() {
        val intent = Intent(this, com.chatai.activities.ConversationHistoryActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onOpenServerConfig() {
        val intent = Intent(this, com.chatai.activities.ServerConfigurationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onOpenChatAI() {
        // Fermer l'overlay IMMÉDIATEMENT et ouvrir MainActivity
        Log.i(TAG, "💬 Opening ChatAI - Closing overlay immediately")
        val intent = Intent(this, com.chatai.MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish() // Fermer immédiatement
    }
    
    override fun onOpenKittInterface() {
        // Fermer l'overlay IMMÉDIATEMENT et ouvrir MainActivity avec KITT
        Log.i(TAG, "🚗 Opening KITT - Closing overlay immediately")
        val intent = Intent(this, com.chatai.MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra("activate_kitt", true)
        startActivity(intent)
        finish() // Fermer immédiatement
    }
    
    override fun onRestartKitt() {
        // Pas applicable depuis VoiceListenerActivity (déjà overlay rapide)
        // On ferme juste l'overlay
        finishAfterDelay(500)
    }
    
    override fun onSetWiFi(enable: Boolean) {
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finishAfterDelay(500)
    }
    
    override fun onSetVolume(level: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val targetVol = (maxVol * level / 100).toInt()
        audioManager.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            targetVol,
            android.media.AudioManager.FLAG_SHOW_UI
        )
        // Continue speaking - don't close
    }
    
    override fun onOpenSystemSettings(setting: String) {
        val intent = when (setting.lowercase()) {
            "wifi" -> Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> Intent(android.provider.Settings.ACTION_SETTINGS)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finishAfterDelay(500)
    }
    
    override fun onChangeModel(model: String) {
        val prefs = getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        prefs.edit().putString("local_model_name", model).apply()
        // Continue speaking - don't close
    }
    
    override fun onChangeMode(mode: String) {
        val forcedMode = when (mode.lowercase()) {
            "pc" -> "pc_only"
            "cloud" -> "cloud_only"
            else -> "auto"
        }
        val prefs = getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        prefs.edit().putString("forced_api_mode", forcedMode).apply()
        // Continue speaking - don't close
    }
    
    override fun onChangePersonality(personality: String) {
        val prefs = getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        prefs.edit().putString("ai_personality", personality).apply()
        // Recréer le service
        kittAIService = KittAIService(this, personality, "vocal", actionCallback = this)
        // Continue speaking - don't close
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun openMainApp() {
        val intent = Intent(this, com.chatai.MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
    
    private fun finishAfterDelay(delayMs: Long) {
        coroutineScope.launch {
            delay(delayMs)
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        cancelSafetyTimeout()
        stopAllAnimations()
        
        whisperRecognizer?.stopListening()
        whisperRecognizer = null
        
        speechRecognizer?.destroy()
        speechRecognizer = null
        
        textToSpeech?.shutdown()
        textToSpeech = null
        
        coroutineScope.cancel()
        
        Log.i(TAG, "🛑 VoiceListenerActivity destroyed")
    }
    
    /**
     * Bouton de fermeture manuelle (si bloqué)
     */
    override fun onBackPressed() {
        Log.i(TAG, "🔙 Back pressed - Closing activity")
        cancelSafetyTimeout()
        super.onBackPressed()
    }
    
    /**
     * Click en dehors de la fenêtre pour fermer
     */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
            Log.i(TAG, "👆 Touch outside - Closing activity")
            cancelSafetyTimeout()
            finish()
            return true
        }
        return super.onTouchEvent(event)
    }
}

