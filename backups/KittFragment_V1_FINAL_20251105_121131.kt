package com.chatai.fragments

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.chatai.R
import com.chatai.MainActivity
import com.chatai.viewmodels.KittViewModel
import kotlin.random.Random
import java.util.*
import kotlinx.coroutines.*
// import com.chatai.services.ChatGPTService
// import com.chatai.services.WebViewAIService
// import com.chatai.services.AIManager
// import com.chatai.config.UsageLimits
// import com.chatai.config.AIConfigManager
import android.content.SharedPreferences
import com.chatai.services.KittAIService

/**
 * KITT Fragment - Interface utilisateur pour l'assistant KITT
 * Reproduit l'interface HTML originale avec Material 3
 * INCLUT : Interface vocale complète avec reconnaissance et synthèse vocale
 */

enum class VUMeterMode {
    OFF,        // VU-meter éteint
    VOICE,      // Mode voix TTS
    AMBIENT     // Mode sons ambiants
}

    enum class VUAnimationMode {
        ORIGINAL,   // Animation originale : de bas en haut seulement
        DUAL        // Animation dual : en-haut et en-bas vers le centre
    }

class KittFragment : Fragment(), RecognitionListener, TextToSpeech.OnInitListener, com.chatai.services.KittActionCallback {
    
    // Listener séparé pour le VU-meter (évite les conflits avec la reconnaissance vocale)
    private val vuMeterListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            // Capturer le niveau audio réel du microphone pour VU-meter
            currentMicrophoneLevel = rmsdB
            
            // Debug : Afficher les niveaux audio
            android.util.Log.d("VUMeter", "Microphone level: ${rmsdB}dB, Mode: $vuMeterMode")
            
            // Mettre à jour le VU-meter en temps réel si on est en mode AMBIENT
            if (vuMeterMode == VUMeterMode.AMBIENT) {
                // Convertir dB en niveau normalisé (0-1)
                val normalizedLevel = (rmsdB + 20f) / 20f // Convertir de dB à 0-1
                val clampedLevel = normalizedLevel.coerceIn(0f, 1f)
                android.util.Log.d("VUMeter", "Normalized: $normalizedLevel, Clamped: $clampedLevel")
                updateVuMeter(clampedLevel)
            }
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            // Erreur silencieuse pour le VU-meter
        }
        override fun onResults(results: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private lateinit var viewModel: KittViewModel
    
    // Views principales
    private lateinit var statusText: TextView
    private lateinit var powerSwitch: MaterialSwitch
    private lateinit var switchStatus: TextView
    // switchStatus2 a été renommé en statusBarIndicatorRDY
    
    // Status Bar Indicators (voyants de status)
    private lateinit var statusBarIndicatorBSY: com.google.android.material.textview.MaterialTextView
    private lateinit var statusBarIndicatorRDY: com.google.android.material.textview.MaterialTextView
    private lateinit var statusBarIndicatorNET: com.google.android.material.textview.MaterialTextView
    private lateinit var statusBarIndicatorMSQ: com.google.android.material.textview.MaterialTextView
    private lateinit var scannerRow: LinearLayout
    private lateinit var leftVuBar: LinearLayout
    private lateinit var centerVuBar: LinearLayout
    private lateinit var rightVuBar: LinearLayout
    private lateinit var textInput: TextInputEditText

    // Boutons de contrôle
    private lateinit var aiButton: MaterialButton
    private lateinit var thinkButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var sendButton: MaterialButton
    private lateinit var vuModeButton: MaterialButton
    private lateinit var menuDrawerButton: MaterialButton
    private lateinit var backToChatButton: MaterialButton

    // Animation handlers
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scannerAnimation: Runnable? = null
    private var vuMeterAnimation: Runnable? = null
    private var statusMessageHandler: Runnable? = null
    
    // ⭐ Thinking animation handlers (Phase 2 - Apprentissage)
    private var thinkingAnimationBSY: Runnable? = null
    private var thinkingAnimationNET: Runnable? = null

    // Gestion intelligente des messages
    private var currentMessageType: MessageType = MessageType.STATUS
    private var messageQueue = mutableListOf<StatusMessage>()
    private var isProcessingQueue = false

    enum class MessageType {
        STATUS,      // Messages de statut système
        VOICE,       // Messages vocaux
        AI,          // Réponses IA
        COMMAND,     // Commandes KITT
        ERROR,       // Messages d'erreur
        ANIMATION    // Messages d'animation
    }

    data class StatusMessage(
        val text: String,
        val type: MessageType,
        val duration: Long,
        val priority: Int = 0 // 0 = normal, 1 = haute priorité
    )

    // États du système
    private var isReady = false
    private var isListening = false
    private var isThinking = false
    private var isPersistentMode = false
    private var isSpeaking = false
    private var isChatMode = false // Mode conversation ChatGPT

    // Variables pour l'animation de scan KITT
    private var scanLineView: View? = null
    private var scanAnimation: Animation? = null

    // Scanner KITT
    private var kittPosition = 0
    private var kittDirection = 1
    private val kittSegments = mutableListOf<ImageView>()

    // VU Meter
    private val vuLeds = mutableListOf<ImageView>()

    // Interface vocale KITT
    private var speechRecognizer: SpeechRecognizer? = null
    private var vuMeterRecognizer: SpeechRecognizer? = null // SpeechRecognizer séparé pour VU-meter
    private var textToSpeech: TextToSpeech? = null
    private var isKittActive = false

    // VU-meter modes
    private var vuMeterMode = VUMeterMode.VOICE // Mode par défaut - voix
    private var vuAnimationMode = VUAnimationMode.ORIGINAL // Mode d'animation par défaut - original
    private var isTTSSpeaking = false
    private var isTTSReady = false
    
    // Audio/Music
    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPlaying = false
    private var hasActivationMessageBeenSpoken = false // Pour ne parler qu'une fois par session
    private var ambientSoundLevel = 0f
    private var currentMicrophoneLevel = 0f // Niveau actuel du microphone
    private var isMicrophoneListening = false // État d'écoute du microphone

    // Système audio Android
    private lateinit var audioManager: AudioManager
    private var maxVolume = 1f
    private var currentVolume = 0f

    // AI Integration - ChatGPT (simplifié)
    private var aiEnabled = false
    private var conversationHistory = mutableListOf<String>()
    // private val chatGPTService = ChatGPTService()
    // private val webViewAIServiceManager = com.chatai.services.WebViewAIServiceManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Gestion des quotas
    private lateinit var sharedPrefs: SharedPreferences

    // Gestionnaire de configuration AI (simplifié)
    // private lateinit var aiConfigManager: AIConfigManager

    // Service d'IA générative pour KITT
    private lateinit var kittAIService: KittAIService

    // Référence au FileServer pour l'explorateur de fichiers
    private var fileServer: com.chatai.FileServer? = null

    // Méthode pour injecter le FileServer depuis KittActivity
    fun setFileServer(fileServer: com.chatai.FileServer?) {
        this.fileServer = fileServer
    }

    // Interface pour communiquer avec l'activité parente
    interface KittFragmentListener {
        fun hideKittInterface()
    }

    private var kittFragmentListener: KittFragmentListener? = null

    // Méthode pour définir le listener
    fun setKittFragmentListener(listener: KittFragmentListener?) {
        this.kittFragmentListener = listener
    }

    // Méthode pour retourner au chat
    private fun goBackToChat() {
        try {
            kittFragmentListener?.hideKittInterface()
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Erreur retour au chat", e)
        }
    }
    
    /**
     * ⭐ Méthode publique pour activer KITT depuis la Quick Settings Tile
     * Active KITT et commence l'écoute vocale immédiatement
     */
    fun activateVoiceListening() {
        android.util.Log.i("KittFragment", "🎤 activateVoiceListening() called from Quick Settings Tile")
        
        try {
            // Si KITT n'est pas déjà actif, l'activer
            if (!isKittActive && !isReady) {
                // Utiliser setReadyMode() pour activer KITT + switch visuelle
                setReadyMode()
                android.util.Log.i("KittFragment", "✅ KITT activated via setReadyMode()")
            }
            
            // Si KITT n'écoute pas déjà, démarrer l'écoute vocale
            if (!isListening) {
                // Attendre un peu que TTS soit prêt
                mainHandler.postDelayed({
                    try {
                        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.CANADA_FRENCH)
                        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, requireContext().packageName)
                        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        
                        isListening = true
                        speechRecognizer?.startListening(intent)
                        
                        showStatusMessage("🎤 En écoute...", 2000, MessageType.VOICE)
                        android.util.Log.i("KittFragment", "✅ Voice listening started")
                    } catch (e: Exception) {
                        android.util.Log.e("KittFragment", "❌ Error starting voice listening", e)
                        showStatusMessage("Reconnaissance vocale non disponible", 3000, MessageType.ERROR)
                    }
                }, 500)
            } else {
                android.util.Log.i("KittFragment", "ℹ️ Already listening")
            }
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "❌ Error in activateVoiceListening", e)
        }
    }




    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupVoiceInterface()
        } else {
            // Permission refusée - désactiver l'interface vocale
            // L'interface vocale sera désactivée automatiquement
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kitt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[KittViewModel::class.java]

        // Initialiser les préférences pour les quotas
        sharedPrefs = requireContext().getSharedPreferences("kitt_usage", android.content.Context.MODE_PRIVATE)

        // Initialiser le gestionnaire de configuration AI (simplifié)
        // aiConfigManager = AIConfigManager(requireContext())

        // Initialiser le service d'IA générative avec personnalité sauvegardée
        val aiConfigPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        val selectedPersonality = aiConfigPrefs.getString("selected_personality", "KITT") ?: "KITT"
        kittAIService = KittAIService(requireContext(), selectedPersonality, platform = "vocal", actionCallback = this)
        android.util.Log.i("KittFragment", "KittAIService initialisé avec personnalité: $selectedPersonality")

        initializeViews(view)
        setupScanner()
        setupVuMeter()
        setupListeners()
        setupObservers()
        setupAudioSystem()

        // Initialiser le TTS immédiatement (indépendamment des permissions)
        initializeTTS()
        
        // Initialiser le MediaPlayer au démarrage
        initializeMusic()

        // Appliquer le thème sélectionné
        applySelectedTheme()

        // Initialiser en mode standby (switch OFF) - pas d'interface vocale
        setStandbyMode()
    }

    private fun initializeViews(view: View) {
        statusText = view.findViewById(R.id.statusText)
        powerSwitch = view.findViewById(R.id.powerSwitch)
        switchStatus = view.findViewById(R.id.switchStatus)
        
        // Status Bar Indicators
        statusBarIndicatorBSY = view.findViewById(R.id.statusBarIndicatorBSY)
        statusBarIndicatorRDY = view.findViewById(R.id.statusBarIndicatorRDY)
        statusBarIndicatorNET = view.findViewById(R.id.statusBarIndicatorNET)
        statusBarIndicatorMSQ = view.findViewById(R.id.statusBarIndicatorMSQ)
        scannerRow = view.findViewById(R.id.scannerRow)
        leftVuBar = view.findViewById(R.id.leftVuBar)
        centerVuBar = view.findViewById(R.id.centerVuBar)
        rightVuBar = view.findViewById(R.id.rightVuBar)
        textInput = view.findViewById(R.id.textInput)

        aiButton = view.findViewById(R.id.aiButton)
        thinkButton = view.findViewById(R.id.thinkButton)
        resetButton = view.findViewById(R.id.resetButton)
        sendButton = view.findViewById(R.id.sendButton)
        vuModeButton = view.findViewById(R.id.vuModeButton)
        menuDrawerButton = view.findViewById(R.id.menuDrawerButton)

        // Initialiser le bouton de test dans le menu VU
        // NE PAS décomenter
        //val sendButton2 = view.findViewById<MaterialButton>(R.id.sendButton2)
        //sendButton2?.setOnClickListener {
        //    togglePersistentMode()
        //}


        // Initialiser le texte du bouton VU-mode selon le mode par défaut
        vuModeButton.text = when (vuMeterMode) {
            VUMeterMode.VOICE -> "VU-VOIX"
            VUMeterMode.AMBIENT -> "VU-AMBI"
            VUMeterMode.OFF -> "VU-OFF"
        }

        // Configurer le défilement marquee pour le statusText
        setupMarqueeScrolling()
    }

    private fun setupMarqueeScrolling() {
        // Configurer le TextView pour le défilement marquee
        statusText.apply {
            // Le défilement marquee est déjà configuré dans le XML
            // Juste s'assurer que le focus est disponible
            isFocusable = true
            isFocusableInTouchMode = true
            isSelected = false // Démarrer sans défilement

            // Accélérer le défilement via les propriétés système
            try {
                // Utiliser la réflexion pour accéder aux propriétés privées de marquee
                val marqueeField = javaClass.superclass?.getDeclaredField("mMarquee")
                marqueeField?.isAccessible = true

                // Essayer d'accélérer le défilement
                android.util.Log.d("Marquee", "Marquee setup completed")
            } catch (e: Exception) {
                android.util.Log.d("Marquee", "Could not access marquee properties: ${e.message}")
            }
        }
    }

    private fun setupScanner() {
        // Créer 24 segments pour le scanner KITT
        for (i in 0 until 24) {
            val segment = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.kitt_segment_width),
                    resources.getDimensionPixelSize(R.dimen.kitt_segment_height)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.kitt_segment_margin)
                }
                setImageResource(R.drawable.kitt_scanner_segment_off)
            }
            kittSegments.add(segment)
            scannerRow.addView(segment)
        }
    }

    private fun setupVuMeter() {
        // Créer 20 LEDs pour chaque barre VU
        setupVuBar(leftVuBar)
        setupVuBar(centerVuBar)
        setupVuBar(rightVuBar)
    }

    private fun setupVuBar(bar: LinearLayout) {
        for (i in 0 until 20) {
            val led = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.vu_led_width),
                    resources.getDimensionPixelSize(R.dimen.vu_led_height)
                ).apply {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.vu_led_margin)
                }
                setImageResource(R.drawable.kitt_vu_led_off)
            }
            vuLeds.add(led)
            bar.addView(led)
        }
    }

    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                setupVoiceInterface()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupAudioSystem() {
        // Initialiser l'AudioManager pour le système Android
        audioManager = requireContext().getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()

        // Observer les changements de volume du système
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            0
        )
    }

    private fun initializeTTS() {
        // Initialiser TextToSpeech immédiatement (indépendamment des permissions)
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(requireContext(), this)
            android.util.Log.d("KittFragment", "TTS initialisé au chargement du fragment")
        }
    }
    
    /**
     * Sélectionne la voix TTS selon la personnalité
     */
    private fun selectVoiceForPersonality(personality: String) {
        val tts = textToSpeech ?: return
        
        try {
            // Lister TOUTES les voix disponibles
            val allVoices = tts.voices
            android.util.Log.i("KittFragment", "═══════════════════════════════════════════════════")
            android.util.Log.i("KittFragment", "DIAGNOSTIC VOIX TTS - TOTAL: ${allVoices?.size ?: 0}")
            android.util.Log.i("KittFragment", "PERSONNALITÉ DEMANDÉE: $personality")
            android.util.Log.i("KittFragment", "═══════════════════════════════════════════════════")
            
            allVoices?.forEachIndexed { index, voice ->
                val features = voice.features?.joinToString(", ") ?: "aucune"
                android.util.Log.i("KittFragment", """
                    [$index] ${voice.name}
                      Langue: ${voice.locale}
                      Qualité: ${voice.quality}
                      Réseau: ${voice.isNetworkConnectionRequired}
                      Features: $features
                """.trimIndent())
            }
            
            // Filtrer les voix françaises locales
            val frenchVoices = allVoices?.filter { voice ->
                voice.locale.language == "fr" &&
                voice.isNetworkConnectionRequired == false
            } ?: emptyList()
            
            android.util.Log.i("KittFragment", "───────────────────────────────────────────────────")
            android.util.Log.i("KittFragment", "VOIX FRANÇAISES LOCALES: ${frenchVoices.size}")
            android.util.Log.i("KittFragment", "───────────────────────────────────────────────────")
            
            // Sélectionner selon personnalité
            var selectedVoice: Voice? = null
            
            when (personality) {
                "GLaDOS" -> {
                    // GLaDOS: voix féminine - FRC en priorité
                    selectedVoice = frenchVoices.firstOrNull { voice ->
                        voice.name.contains("x-frc-", ignoreCase = true) // fr-fr-x-frc-local
                    }
                    
                    if (selectedVoice == null) {
                        selectedVoice = frenchVoices.firstOrNull { voice ->
                            voice.name.contains("x-fra-", ignoreCase = true) // fr-fr-x-fra-local
                        }
                    }
                    
                    if (selectedVoice == null) {
                        // Fallback: première voix qui n'est PAS frb ou frd
                        selectedVoice = frenchVoices.firstOrNull { voice ->
                            !voice.name.contains("frb") && !voice.name.contains("frd")
                        } ?: frenchVoices.firstOrNull()
                    }
                    
                    android.util.Log.i("KittFragment", "🤖 GLaDOS: Cherche voix féminine (x-frc- ou x-fra-)")
                }
                else -> {
                    // KITT: voix masculine - FRB PRIORITÉ ABSOLUE
                    selectedVoice = frenchVoices.firstOrNull { voice ->
                        voice.name.contains("x-frb-", ignoreCase = true) // fr-fr-x-frb-local ⭐
                    }
                    
                    if (selectedVoice == null) {
                        selectedVoice = frenchVoices.firstOrNull { voice ->
                            voice.name.contains("x-frd-", ignoreCase = true) // fr-fr-x-frd-local
                        }
                    }
                    
                    if (selectedVoice == null) {
                        selectedVoice = frenchVoices.firstOrNull()
                        android.util.Log.w("KittFragment", "⚠️ Aucune voix masculine (x-frb-/x-frd-) trouvée!")
                    }
                    
                    android.util.Log.i("KittFragment", "🚗 KITT: Cherche voix masculine (x-frb- ou x-frd-)")
                }
            }
            
            if (selectedVoice != null) {
                tts.voice = selectedVoice
                android.util.Log.i("KittFragment", "✅ VOIX SÉLECTIONNÉE: ${selectedVoice.name}")
                android.util.Log.i("KittFragment", "   Genre détecté: ${if (selectedVoice.name.contains("frb") || selectedVoice.name.contains("frd")) "MASCULIN" else "FÉMININ"}")
                android.util.Log.i("KittFragment", "   Pour personnalité: $personality")
            } else {
                android.util.Log.w("KittFragment", "⚠️ Aucune voix trouvée pour $personality")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "❌ Erreur sélection voix: ${e.message}", e)
        }
    }
    
    private fun initializeMusic() {
        // Initialiser MediaPlayer au démarrage
        if (mediaPlayer == null) {
            try {
                android.util.Log.d("Music", "Initialisation du MediaPlayer au démarrage...")
                mediaPlayer = MediaPlayer()
                android.util.Log.d("Music", "MediaPlayer créé avec succès")
            } catch (e: Exception) {
                android.util.Log.e("Music", "Erreur lors de l'initialisation du MediaPlayer: ${e.message}")
            }
        }
    }

    private fun setupVoiceInterface() {
        // Initialiser SpeechRecognizer pour la reconnaissance vocale
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(this)
        }

        // Initialiser SpeechRecognizer séparé pour le VU-meter
        if (vuMeterRecognizer == null) {
            vuMeterRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            vuMeterRecognizer?.setRecognitionListener(vuMeterListener)
        }

        // TTS déjà initialisé par initializeTTS()

        // Activer les boutons vocaux
        aiButton.isEnabled = true
        aiButton.text = "AI"

        // Ne pas démarrer automatiquement l'écoute du microphone
        // L'écoute sera démarrée selon le mode VU-meter sélectionné
    }

    private fun stopVoiceInterface() {
        // Arrêter l'écoute si elle était active
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }

        // Arrêter l'écoute continue du microphone
        stopMicrophoneListening()

        // Arrêter la synthèse vocale
        textToSpeech?.stop()
    }

    private fun startMicrophoneListening() {
        if (isMicrophoneListening) return

        isMicrophoneListening = true

        // Démarrer une reconnaissance continue pour capturer les niveaux audio
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            // Utiliser le SpeechRecognizer séparé pour le VU-meter
            vuMeterRecognizer?.startListening(intent)
        } catch (e: Exception) {
            // Erreur silencieuse - pas d'affichage
        }
    }

    private fun stopMicrophoneListening() {
        isMicrophoneListening = false
        // Utiliser le SpeechRecognizer séparé pour le VU-meter
        vuMeterRecognizer?.stopListening()
    }


    private fun processVoiceCommand(command: String) {
        if (!isReady) return

        // Afficher l'input utilisateur
        showStatusMessage("Vous: '$command'", 2000, MessageType.VOICE)

        // Commandes spéciales pour le VU-meter - BOUTONS
        when (command.uppercase().trim()) {
            "ANIMATION_ORIGINAL" -> {
                android.util.Log.d("VUMeter", "Bouton ORIGINAL pressé - Commande: $command")
                vuAnimationMode = VUAnimationMode.ORIGINAL
                showStatusMessage("Animation VU-meter: ORIGINAL (bas en haut)", 1500, MessageType.ANIMATION)
                speakAIResponse("Mode d'animation VU-meter changé vers l'original : de bas en haut")
                if (vuMeterMode != VUMeterMode.OFF) {
                    stopVuMeterAnimation()
                    startVuMeterAnimation()
                }
                updateAnimationModeButtons()
                return
            }
            "ANIMATION_DUAL" -> {
                android.util.Log.d("VUMeter", "Bouton DUAL pressé - Commande: $command")
                vuAnimationMode = VUAnimationMode.DUAL
                showStatusMessage("Animation VU-meter: DUAL (haut et bas)", 1500, MessageType.ANIMATION)
                speakAIResponse("Mode d'animation VU-meter changé vers le dual : en-haut et en-bas vers le centre")
                if (vuMeterMode != VUMeterMode.OFF) {
                    stopVuMeterAnimation()
                    startVuMeterAnimation()
                }
                updateAnimationModeButtons()
                return
            }
        }

        when (command.lowercase().trim()) {
            "animation originale", "mode original", "animation bas en haut" -> {
                vuAnimationMode = VUAnimationMode.ORIGINAL
                statusText.text = "Animation VU-meter: ORIGINAL (bas en haut)"
                speakAIResponse("Mode d'animation VU-meter changé vers l'original : de bas en haut")
                if (vuMeterMode != VUMeterMode.OFF) {
                    stopVuMeterAnimation()
                    startVuMeterAnimation()
                }
                return
            }
                    "animation dual", "mode dual", "animation haut bas" -> {
                        vuAnimationMode = VUAnimationMode.DUAL
                        statusText.text = "Animation VU-meter: DUAL (haut et bas)"
                        speakAIResponse("Mode d'animation VU-meter changé vers le dual : en-haut et en-bas vers le centre")
                if (vuMeterMode != VUMeterMode.OFF) {
                    stopVuMeterAnimation()
                    startVuMeterAnimation()
                }
                return
            }
            "basculer animation", "changer animation", "toggle animation" -> {
                toggleVUAnimationMode()
                        val modeName = if (vuAnimationMode == VUAnimationMode.ORIGINAL) "ORIGINAL" else "DUAL"
                statusText.text = "Animation VU-meter: $modeName"
                speakAIResponse("Mode d'animation VU-meter basculé vers $modeName")
                return
            }
            "TOGGLE_MUSIC" -> {
                toggleMusic()
                return
            }
            // Commandes vocales pour ouvrir les activités
            // Détection avec mots-clés : "ouvre", "menu", "affiche", "va à"
        }
        
        // Détection intelligente des commandes avec mots-clés
        val lowerCommand = command.lowercase().trim()
        
        // Configuration IA
        if ((lowerCommand.contains("ouvre") || lowerCommand.contains("ouvrir") || 
             lowerCommand.contains("menu") || lowerCommand.contains("affiche") ||
             lowerCommand.contains("va à") || lowerCommand.contains("va a")) &&
            (lowerCommand.contains("configuration") || lowerCommand.contains("config") || 
             lowerCommand.contains("réglage") || lowerCommand.contains("paramètre"))) {
            speakAIResponse("Ouverture de la configuration IA")
            try {
                val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("KittFragment", "Erreur ouverture config IA: ${e.message}")
                speakAIResponse("Impossible d'ouvrir la configuration")
            }
            return
        }
        
        // Historique
        if ((lowerCommand.contains("ouvre") || lowerCommand.contains("ouvrir") || 
             lowerCommand.contains("menu") || lowerCommand.contains("affiche") ||
             lowerCommand.contains("va à") || lowerCommand.contains("va a") ||
             lowerCommand.contains("montre")) &&
            (lowerCommand.contains("historique") || lowerCommand.contains("conversation"))) {
            speakAIResponse("Ouverture de l'historique des conversations")
            try {
                val intent = Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("KittFragment", "Erreur ouverture historique: ${e.message}")
                speakAIResponse("Impossible d'ouvrir l'historique")
            }
            return
        }
        
        // Commandes simples sans mots-clés (pour compatibilité)
        when (lowerCommand) {
            "configuration ia", "configuration", "réglages ia", "paramètres ia", "config ia" -> {
                speakAIResponse("Ouverture de la configuration IA")
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("KittFragment", "Erreur ouverture config IA: ${e.message}")
                    speakAIResponse("Impossible d'ouvrir la configuration")
                }
                return
            }
            "historique", "historique des conversations", "historique conversation", "mes conversations" -> {
                speakAIResponse("Ouverture de l'historique des conversations")
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("KittFragment", "Erreur ouverture historique: ${e.message}")
                    speakAIResponse("Impossible d'ouvrir l'historique")
                }
                return
            }
            "test réseau", "test api", "test apis", "tester apis", "tester les apis" -> {
                speakAIResponse("Test de connectivité réseau")
                testNetworkAPIs()
                return
            }
            "musique", "toggle musique", "play musique", "stop musique", "lance la musique", "arrête la musique" -> {
                toggleMusic()
                return
            }
        }

        // Utiliser le service d'IA générative KITT
        coroutineScope.launch {
            try {
                // Afficher un message de traitement
                showStatusMessage("Traitement en cours...", 1000, MessageType.STATUS)
                
                // ⭐ Démarrer l'animation thinking (LEDs BSY/NET clignotent)
                startThinkingAnimation()
                
                val response = kittAIService.processUserInput(command)

                // Afficher la réponse IA
                showStatusMessage("KITT: '$response'", 4000, MessageType.AI)

                // Parler la réponse
                speakAIResponse(response)

                // Log pour debug
                android.util.Log.d("KittAI", "Input: '$command' → Response: '$response'")

            } catch (e: Exception) {
                android.util.Log.e("KittAI", "Error processing command", e)
                showStatusMessage("Erreur: '$command'", 3000, MessageType.ERROR)
                speakAIResponse("Michael, je rencontre un dysfonctionnement temporaire. Réessayez dans un moment.")
            } finally {
                // ⭐ Arrêter l'animation thinking
                stopThinkingAnimation()
            }
        }
    }

    private fun processAIConversation(userMessage: String) {
        if (!isReady) return

        // Simuler la réflexion AI
        simulateThinking()

        // Utiliser le service d'IA générative KITT
        coroutineScope.launch {
            try {
                // ⭐ Démarrer l'animation thinking (LEDs BSY/NET clignotent)
                startThinkingAnimation()
                
                val response = kittAIService.processUserInput(userMessage)

                // Ajouter à l'historique
                conversationHistory.add("User: $userMessage")
                conversationHistory.add("KITT: $response")

                // Parler la réponse
                speakAIResponse(response)

                // Log pour debug
                android.util.Log.d("KittAI", "Conversation - Input: '$userMessage' → Response: '$response'")

            } catch (e: Exception) {
                android.util.Log.e("KittAI", "Error in AI conversation", e)
                // Erreur - fallback
                val fallbackResponse = "Michael, je rencontre un dysfonctionnement temporaire. Pouvez-vous reformuler votre question ?"
                conversationHistory.add("KITT: $fallbackResponse")
                speakAIResponse(fallbackResponse)
            } finally {
                // ⭐ Arrêter l'animation thinking
                stopThinkingAnimation()
            }
        }
    }

    private fun speakAIResponse(response: String) {
        // ⭐ PERMETTRE LA VOIX MÊME SI KITT EST OFF (pour les annonces du drawer)
        if (textToSpeech == null || isTTSSpeaking) return

        try {
            // Afficher la réponse AI dans le marquee seulement si KITT est activé
            if (isReady) {
                showStatusMessage("KITT: '$response'", 5000, MessageType.VOICE)
            }
            
            // Parler la réponse AI avec un style KITT
            textToSpeech?.speak(response, TextToSpeech.QUEUE_FLUSH, null, "ai_response")
            android.util.Log.d("KittFragment", "🔊 TTS Speaking: '$response'")
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "❌ TTS Error: ${e.message}")
        }
    }

    private fun executeKittCommand(command: String) {
        when (command) {
            "ACTIVATE_KITT" -> {
                isKittActive = true
                viewModel.startKitt()
                startScannerAnimation(120)
                startVuMeterAnimation()
            }
            "DEACTIVATE_KITT" -> {
                isKittActive = false
                viewModel.stopKitt()
                stopScannerAnimation()
                stopVuMeterAnimation()
            }
            "ACTIVATE_SCANNER" -> {
                viewModel.startScanner()
                startScannerAnimation(100)
            }
            "SYSTEM_STATUS" -> {
                viewModel.checkStatus()
            }
            "EMERGENCY_MODE" -> {
                viewModel.startKitt()
                startScannerAnimation(60)
                startVuMeterAnimation()
            }
            "GPS_ACTIVATION" -> {
                viewModel.startKitt()
                startScannerAnimation(80)
            }
            "TURBO_BOOST" -> {
                startScannerAnimation(60)
                startVuMeterAnimation()
            }
            "OPEN_COMMUNICATION" -> {
                viewModel.startKitt()
                startScannerAnimation(100)
            }
            "SET_FREQUENCY" -> {
                viewModel.startScanner()
                startVuMeterAnimation()
            }
            "TRANSMIT_MESSAGE" -> {
                viewModel.startKitt()
                startScannerAnimation(60)
            }
            "PURSUIT_MODE" -> {
                viewModel.startScanner()
                startScannerAnimation(80)
            }
        }
    }


    // RecognitionListener callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        // Prêt pour la reconnaissance
    }

    override fun onBeginningOfSpeech() {
        // Début de la parole détecté
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Ce callback est maintenant géré par vuMeterListener
        // Pas de traitement ici pour éviter les conflits
        viewModel.updateVumeterLevel(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // Buffer audio reçu
    }

    override fun onEndOfSpeech() {
        // Fin de la parole détectée
    }

    override fun onError(error: Int) {
        isListening = false
        aiButton.text = "AI"
        aiButton.isEnabled = true

        // Pas d'affichage de statut pour les erreurs - juste silence
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val command = matches[0]
            // Log temporaire pour voir ce qui est reconnu
            showStatusMessage("Reconnu: '$command'", 2000, MessageType.VOICE)
            processVoiceCommand(command)
        } else {
            showStatusMessage("Aucune correspondance", 2000, MessageType.ERROR)
        }

        isListening = false
        aiButton.text = "AI"
        aiButton.isEnabled = true
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // Résultats partiels - peut être utilisé pour afficher en temps réel
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // Événements de reconnaissance
    }

    // TextToSpeech.OnInitListener callback
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            android.util.Log.d("KittFragment", "TTS initialisé avec succès - prêt à parler")
            isTTSReady = true
            textToSpeech?.language = Locale.FRENCH
            
            // Charger les paramètres TTS depuis SharedPreferences
            val sharedPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
            val selectedPersonality = sharedPrefs.getString("selected_personality", "KITT") ?: "KITT"
            val ttsSpeed = sharedPrefs.getFloat("tts_speed", 1.1f)
            val ttsPitch = sharedPrefs.getFloat("tts_pitch", 0.8f)
            
            // ⭐ SÉLECTIONNER LA VOIX SELON LA PERSONNALITÉ
            selectVoiceForPersonality(selectedPersonality)
            
            textToSpeech?.setSpeechRate(ttsSpeed)
            textToSpeech?.setPitch(ttsPitch)
            
            android.util.Log.d("KittFragment", "TTS configured: personality=$selectedPersonality, speed=${ttsSpeed}x, pitch=${ttsPitch}x")

            // Configurer le listener pour suivre la progression TTS
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isTTSSpeaking = true
                    // Mettre à jour les voyants
                    updateStatusIndicators()
                    // Démarrer l'animation VU-meter basée sur le volume système
                    startSystemVolumeAnimation()
                }

                override fun onDone(utteranceId: String?) {
                    isTTSSpeaking = false
                    // Mettre à jour les voyants
                    updateStatusIndicators()
                    // Arrêter l'animation VU-meter et remettre au niveau de base
                    stopSystemVolumeAnimation()
                    resetVuMeterToBase()

                    // Gérer spécifiquement le message d'activation
                    if (utteranceId == "kitt_activation") {
                        // Message d'activation terminé
                        isSpeaking = false
                        mainHandler.postDelayed({
                            if (isAdded) {
                                showStatusMessage("KITT prêt - En attente de vos instructions", 3000)
                                stopVuMeterAnimation()

                                if (!isThinking) {
                                    startScannerAnimation(120)
                                }
                            }
                        }, 500)
                    }
                }


                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    isTTSSpeaking = false
                    stopSystemVolumeAnimation()
                    resetVuMeterToBase()
                }
            })

            // TTS initialisé avec succès - pas de message automatique pour éviter les conflits
        }
    }

    private fun setupListeners() {
        powerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Démarrer l'animation de scan KITT avant d'activer le mode
                startKittScanAnimation()
            } else {
                setStandbyMode()
            }
        }


        // AI Button (ChatGPT)
        aiButton.setOnClickListener {
            toggleAIMode()
        }

        // RÉFLÉCHIR Button - Simulation de réflexion
        thinkButton.setOnClickListener {
            if (isReady) {
                simulateThinking()
            }
        }


        // RESET Button
        resetButton.setOnClickListener {
            resetInterface()
        }

        // VU-Meter Mode Toggle Button
        vuModeButton.setOnClickListener {
            toggleVUMeterMode()
        }

        // Menu Drawer Button
        menuDrawerButton.setOnClickListener {
            showMenuDrawer()
        }


        sendButton.setOnClickListener {
            processText()
        }

        textInput.setOnEditorActionListener { _, _, _ ->
            processText()
            true
        }
        
        // Status Indicators comme boutons cliquables
        statusBarIndicatorRDY.setOnClickListener {
            // RDY → Historique des conversations
            try {
                val intent = Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                startActivity(intent)
                speakAIResponse("Historique des conversations")
            } catch (e: Exception) {
                android.util.Log.e("KittFragment", "Erreur ouverture historique: ${e.message}")
            }
        }
        
        statusBarIndicatorBSY.setOnClickListener {
            // BSY → Configuration IA
            try {
                val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                startActivity(intent)
                speakAIResponse("Configuration IA")
            } catch (e: Exception) {
                android.util.Log.e("KittFragment", "Erreur ouverture config: ${e.message}")
            }
        }
        
        statusBarIndicatorNET.setOnClickListener {
            // NET → Test rapide APIs
            if (isReady) {
                speakAIResponse("Test de connectivité réseau")
                testNetworkAPIs()
            }
        }
        
        statusBarIndicatorMSQ.setOnClickListener {
            // MSQ → Toggle musique
            toggleMusic()
        }
    }
    
    private fun testNetworkAPIs() {
        coroutineScope.launch {
            showStatusMessage("Test APIs en cours...", 2000, MessageType.STATUS)
            try {
                val diagnostic = kittAIService.getDiagnosticLogs()
                showStatusMessage("APIs: ${diagnostic.take(100)}...", 5000, MessageType.STATUS)
            } catch (e: Exception) {
                showStatusMessage("Erreur test APIs", 2000, MessageType.ERROR)
            }
        }
    }

    private fun setupObservers() {
        // Observateurs simplifiés - pas d'interférence avec les états locaux
        viewModel.statusMessage.observe(viewLifecycleOwner) { message ->
            // Afficher le message sans interférer avec les autres fonctions
            statusText.text = message
        }
    }

    private fun setStandbyMode() {
        isReady = false
        powerSwitch.isChecked = false
        // Réactiver la switch en mode standby
        powerSwitch.isEnabled = true

        // Vider la queue et afficher le statut de base
        clearMessageQueue()

        if (isAdded) {
            switchStatus.text = getString(R.string.kitt_status_stby)
            // Les Status Bar Indicators gardent leurs textes fixes (RDY, BSY, NET)
            // Seules leurs couleurs changent selon l'état ON/OFF
            statusText.text = getString(R.string.kitt_status_standby)
        }

        // Mettre à jour les voyants
        updateStatusIndicators()

        // Arrêter l'interface vocale
        stopVoiceInterface()

        stopAllAnimations()
        resetScanner()
        resetVuMeter()
        
        // Arrêter la musique en mode standby
        if (isMusicPlaying) {
            stopMusic()
        }
        
        updateButtonStates()

        // Appliquer l'état OFF (rouge foncé)
        setButtonsState(false)

        // Mode standby activé
    }

    private fun setButtonsState(isOn: Boolean) {
        // Liste des MaterialButton
        val allButtons = listOf(
            R.id.sendButton, R.id.menuDrawerButton, R.id.vuModeButton,
            R.id.aiButton, R.id.thinkButton, R.id.resetButton
        )

        // Liste des Status Bar Indicators (voyants de status)
        val statusBarIndicators = listOf(
            R.id.statusBarIndicatorBSY, R.id.statusBarIndicatorRDY, R.id.statusBarIndicatorNET, R.id.statusBarIndicatorMSQ
        )

        val textColor = if (isOn) {
            ContextCompat.getColor(requireContext(), R.color.kitt_red)
        } else {
            ContextCompat.getColor(requireContext(), R.color.kitt_red_dark)
        }

        val strokeColor = if (isOn) {
            ContextCompat.getColor(requireContext(), R.color.kitt_red)
        } else {
            ContextCompat.getColor(requireContext(), R.color.kitt_red_dark)
        }

        // Appliquer aux MaterialButton
        allButtons.forEach { buttonId ->
            val button = view?.findViewById<MaterialButton>(buttonId)
            button?.let {
                it.setTextColor(textColor)
                it.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeColor))
            }
        }

        // Appliquer aux Status Bar Indicators (voyants de status)
        statusBarIndicators.forEach { textViewId ->
            val textView = view?.findViewById<com.google.android.material.textview.MaterialTextView>(textViewId)
            textView?.let {
                if (isOn) {
                    // Mode ON : fond rouge, texte noir
                    it.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
                    // Tous utilisent maintenant le même drawable
                    it.setBackgroundResource(R.drawable.kitt_status_background_active)
                } else {
                    // Mode OFF : fond transparent, texte rouge foncé
                    it.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red_dark))
                    // Tous utilisent maintenant le même drawable
                    it.setBackgroundResource(R.drawable.kitt_status_background)
                }
            }
        }
    }

    private fun setReadyMode() {
        isReady = true
        powerSwitch.isChecked = true
        if (isAdded) {
            switchStatus.text = getString(R.string.kitt_status_rdy)
            // Les Status Bar Indicators gardent leurs textes fixes (RDY, BSY, NET)
            // Seules leurs couleurs changent selon l'état ON/OFF
            statusText.text = getString(R.string.kitt_status_ready)
        }
        
        // Mettre à jour les voyants selon la nouvelle logique
        updateStatusIndicators()

        // Activer l'interface vocale seulement quand KITT est activé
        checkMicrophonePermission()

        startScannerAnimation(120)
        updateButtonStates()

        // Appliquer l'état ON (rouge vif)
        setButtonsState(true)

        // KITT activé

        // Message d'activation seulement à la première activation de la session
        if (!hasActivationMessageBeenSpoken) {
            speakKittActivationMessage()
            hasActivationMessageBeenSpoken = true
        } else {
            // Activation suivante - juste l'animation VU-meter
            simulateSpeaking()
        }
    }

    /**
     * Met à jour les voyants BSY et RDY selon la logique demandée :
     * - BSY actif quand l'IA ou KITT est occupé/travaille
     * - RDY s'éteint quand KITT est en incapacité
     * Utilise des drawables personnalisés pour conserver les coins arrondis et bordures
     */
    private fun updateStatusIndicators() {
        if (!isAdded) return
        
        // Logique pour BSY : actif quand l'IA ou KITT travaille
        val isBusy = isSpeaking || isThinking || isTTSSpeaking || isListening
        
        // Logique pour RDY : s'éteint quand KITT est en incapacité
        val isReadyIndicator = isReady && !isBusy
        
        // Mettre à jour BSY
        if (isBusy) {
            // BSY actif : fond rouge vif, contour rouge vif, texte noir (comme bouton actif)
            statusBarIndicatorBSY.setBackgroundResource(R.drawable.kitt_status_background_active)
            statusBarIndicatorBSY.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        } else {
            // BSY inactif : fond rouge sombre, contour rouge sombre, texte rouge sombre (comme bouton inactif)
            statusBarIndicatorBSY.setBackgroundResource(R.drawable.kitt_status_background)
            statusBarIndicatorBSY.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red_dark))
        }
        
        // Mettre à jour RDY
        if (isReadyIndicator) {
            // RDY actif : fond rouge vif, contour rouge vif, texte noir (comme bouton actif)
            statusBarIndicatorRDY.setBackgroundResource(R.drawable.kitt_status_background_active)
            statusBarIndicatorRDY.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        } else {
            // RDY inactif : fond rouge sombre, contour rouge sombre, texte rouge sombre (comme bouton inactif)
            statusBarIndicatorRDY.setBackgroundResource(R.drawable.kitt_status_background)
            statusBarIndicatorRDY.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red_dark))
        }
        
        // Mettre à jour MSQ : actif quand la musique joue
        if (isMusicPlaying) {
            // MSQ actif : fond rouge vif, contour rouge vif, texte noir (comme bouton actif)
            statusBarIndicatorMSQ.setBackgroundResource(R.drawable.kitt_status_background_active)
            statusBarIndicatorMSQ.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        } else {
            // MSQ inactif : fond rouge sombre, contour rouge sombre, texte rouge sombre (comme bouton inactif)
            statusBarIndicatorMSQ.setBackgroundResource(R.drawable.kitt_status_background)
            statusBarIndicatorMSQ.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red_dark))
        }
    }
    
    /**
     * ⭐ Démarre l'animation des LEDs pendant le thinking (Phase 2 - Apprentissage)
     * BSY clignote rapidement (250ms) - IA en train de réfléchir
     * NET clignote lentement (500ms) - Requête réseau en cours
     */
    private fun startThinkingAnimation() {
        if (!isAdded) return
        
        // Arrêter les animations existantes si nécessaire
        stopThinkingAnimation()
        
        // Animation BSY (rapide - 250ms)
        var bsyState = false
        thinkingAnimationBSY = object : Runnable {
            override fun run() {
                if (!isAdded) return
                
                bsyState = !bsyState
                if (bsyState) {
                    // Allumé
                    statusBarIndicatorBSY.setBackgroundResource(R.drawable.kitt_status_background_active)
                    statusBarIndicatorBSY.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
                } else {
                    // Semi-allumé (pour effet clignotant)
                    statusBarIndicatorBSY.setBackgroundResource(R.drawable.kitt_status_background)
                    statusBarIndicatorBSY.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
                }
                
                mainHandler.postDelayed(this, 250) // 250ms (rapide)
            }
        }
        mainHandler.post(thinkingAnimationBSY!!)
        
        // Animation NET (lent - 500ms)
        var netState = false
        thinkingAnimationNET = object : Runnable {
            override fun run() {
                if (!isAdded) return
                
                netState = !netState
                if (netState) {
                    // Allumé
                    statusBarIndicatorNET.setBackgroundResource(R.drawable.kitt_status_background_active)
                    statusBarIndicatorNET.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
                } else {
                    // Semi-allumé (pour effet clignotant)
                    statusBarIndicatorNET.setBackgroundResource(R.drawable.kitt_status_background)
                    statusBarIndicatorNET.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
                }
                
                mainHandler.postDelayed(this, 500) // 500ms (lent)
            }
        }
        mainHandler.post(thinkingAnimationNET!!)
        
        // RDY s'assombrit pendant le thinking
        statusBarIndicatorRDY.alpha = 0.3f
        
        android.util.Log.d("KittFragment", "🧠 Thinking animation started (BSY: 250ms, NET: 500ms)")
    }
    
    /**
     * ⭐ Arrête l'animation des LEDs de thinking
     */
    private fun stopThinkingAnimation() {
        if (!isAdded) return
        
        // Arrêter les animations
        thinkingAnimationBSY?.let { mainHandler.removeCallbacks(it) }
        thinkingAnimationNET?.let { mainHandler.removeCallbacks(it) }
        thinkingAnimationBSY = null
        thinkingAnimationNET = null
        
        // Restaurer RDY
        statusBarIndicatorRDY.alpha = 1.0f
        
        // Restaurer l'état normal des LEDs
        updateStatusIndicators()
        
        android.util.Log.d("KittFragment", "🧠 Thinking animation stopped")
    }

    private fun toggleAIMode() {
        if (!isReady) return

        if (isListening && isChatMode) {
            // Arrêter le mode AI
            stopAIMode()
        } else {
            startAIMode()
        }
    }

    private fun stopAIMode() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            isChatMode = false
            aiButton.text = "AI"
            aiButton.isEnabled = true
            
            // Mettre à jour les voyants
            updateStatusIndicators()
            
            showStatusMessage("Mode AI désactivé", 2000, MessageType.STATUS)
        }
    }

    private fun startAIMode() {
        if (!isReady) return

        isListening = true
        isChatMode = true
        aiButton.text = "ARRÊTER AI"
        aiButton.isEnabled = true

        // Mettre à jour les voyants
        updateStatusIndicators()

        // Debug: Afficher que le mode AI est activé
        showStatusMessage("Mode AI activé", 2000, MessageType.STATUS)
        showStatusMessage("IA en écoute...", 2000, MessageType.STATUS)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez à l'IA...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            showStatusMessage("Reconnaissance vocale non disponible", 3000, MessageType.ERROR)
        }
    }


    private fun simulateThinking() {
        if (!isReady) return

        isThinking = true
        
        // Afficher un message de réflexion dans le marquee
        showStatusMessage("KITT: Réflexion en cours...", 3000, MessageType.STATUS)

        // Mettre à jour les voyants
        updateStatusIndicators()

        // Scanner très rapide pendant la réflexion
        startScannerAnimation(60)

        // Toutes les LEDs VU allumées pendant la réflexion (seulement si VU-meter n'est pas OFF)
        if (vuMeterMode != VUMeterMode.OFF) {
            vuLeds.forEach { led ->
                led.setImageResource(R.drawable.kitt_vu_led_active)
            }
        }

        mainHandler.postDelayed({
            isThinking = false
            // Fin de la réflexion - pas d'affichage
            resetVuMeter()

            // Mettre à jour les voyants
            updateStatusIndicators()

            if (!isSpeaking) {
                startScannerAnimation(120)
            }
        }, 3000)
    }

    /**
     * Message d'activation automatique de KITT avec TTS
     * Utilisé UNIQUEMENT à la première activation de la session
     * Jusqu'à la fermeture explicite de l'app
     */
    private fun speakKittActivationMessage() {
        if (!isReady) return

        // Message d'activation de KITT
        val activationMessage = "Bonjour, je suis KITT. En quoi puis-je vous aider ?"

        // Afficher le message visuellement
        if (isAdded) {
            showStatusMessage(activationMessage, 0)
        }

        // Vérifier si TTS est prêt et l'utiliser
        if (textToSpeech != null && isTTSReady && !isTTSSpeaking) {
            try {
                // Lire le message avec TTS
                textToSpeech?.speak(activationMessage, TextToSpeech.QUEUE_FLUSH, null, "kitt_activation")
                isSpeaking = true

                // Animation VU-meter pendant la parole (démarrée par le callback TTS)

                // Programmer un fallback au cas où TTS ne fonctionne pas
                mainHandler.postDelayed({
                    if (isAdded && isSpeaking) {
                        // Fallback si TTS n'a pas fonctionné
                        isSpeaking = false
                        // Mettre à jour les voyants
                        updateStatusIndicators()
                        showStatusMessage("KITT prêt - En attente de vos instructions", 3000)
                        stopVuMeterAnimation()

                        if (!isThinking) {
                            startScannerAnimation(120)
                        }
                    }
                }, 4000) // 4 secondes de fallback

            } catch (e: Exception) {
                // Erreur TTS - fallback visuel
                isSpeaking = true
                startVuMeterAnimation()

                mainHandler.postDelayed({
                    if (isAdded) {
                        isSpeaking = false
                        // Mettre à jour les voyants
                        updateStatusIndicators()
                        showStatusMessage("KITT prêt - En attente de vos instructions", 3000)
                        stopVuMeterAnimation()

                        if (!isThinking) {
                            startScannerAnimation(120)
                        }
                    }
                }, 4000)
            }
        } else {
            // TTS pas encore prêt - simulation visuelle immédiate
            android.util.Log.d("KittFragment", "TTS pas encore prêt, simulation visuelle")
            isSpeaking = true
            startVuMeterAnimation()

            mainHandler.postDelayed({
                if (isAdded) {
                    isSpeaking = false
                    // Mettre à jour les voyants
                    updateStatusIndicators()
                    showStatusMessage("KITT prêt - En attente de vos instructions", 3000)
                    stopVuMeterAnimation()

                    if (!isThinking) {
                        startScannerAnimation(120)
                    }
                }
            }, 3000) // Simulation visuelle de 3 secondes
        }
    }

    /**
     * Simulation visuelle des LEDs VU-meter seulement
     * Utilisée pour les commandes et activations suivantes
     * PAS de TTS - juste l'animation des LEDs
     */
    private fun simulateSpeaking() {
        if (!isReady) return

        isSpeaking = true
        if (isAdded) {
            showStatusMessage(getString(R.string.kitt_status_speaking), 0)
        }

        // Animation VU-meter pendant la parole (simulation visuelle seulement)
        startVuMeterAnimation()
        
        mainHandler.postDelayed({
            if (isAdded) { // Vérifier si le fragment est encore attaché
                isSpeaking = false
                // Mettre à jour les voyants
                updateStatusIndicators()
                showStatusMessage(getString(R.string.kitt_message_communication_complete), 4000)
                stopVuMeterAnimation()
                
                if (!isThinking) {
                    startScannerAnimation(120)
                }
            }
        }, 4000)
    }
    
    private fun processText() {
        if (!isReady) return
        
        val text = textInput.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        
        // Afficher le message utilisateur dans le marquee
        showStatusMessage("Vous: '$text'", 3000, MessageType.VOICE)
        
        // Message traité
        textInput.text?.clear()
        
        // Utiliser le traitement AI pour les messages texte aussi
        mainHandler.postDelayed({
            processAIConversation(text)
        }, 500)
    }
    
    private fun processCommand() {
        if (!isReady) return
        
        simulateThinking()
        
        mainHandler.postDelayed({
            // Pas d'affichage de statut
            
            mainHandler.postDelayed({
                simulateSpeaking()
            }, 1000)
        }, 3000)
    }
    
    
    
    
    private fun resetInterface() {
        isSpeaking = false
        isThinking = false
        isListening = false
        
        // Mettre à jour les voyants
        updateStatusIndicators()
        
        // Vider la queue de messages
        clearMessageQueue()
        
        // Interface réinitialisée
        if (vuMeterMode == VUMeterMode.OFF) {
            resetVuMeter() // Éteindre complètement
        } else {
            startVuMeterAnimation() // Redémarrer selon le mode
        }
        
        if (isReady) {
            startScannerAnimation(120)
        }
    }
    
    private fun updateButtonStates() {
        val enabled = isReady && !isSpeaking && !isThinking
        
        // Autres boutons
        thinkButton.isEnabled = enabled
        resetButton.isEnabled = enabled
        sendButton.isEnabled = enabled
        textInput.isEnabled = enabled
        vuModeButton.isEnabled = enabled
        menuDrawerButton.isEnabled = enabled
    }
    
    private fun showStatusMessage(message: String, duration: Long = 2000, type: MessageType = MessageType.STATUS, priority: Int = 0) {
        // Vérifier si le fragment est encore attaché
        if (!isAdded) return
        
        // Ajouter le message à la queue
        val statusMessage = StatusMessage(message, type, duration, priority)
        messageQueue.add(statusMessage)
        
        // Trier la queue par priorité (haute priorité en premier)
        messageQueue.sortByDescending { it.priority }
        
        // Traiter la queue si pas déjà en cours
        if (!isProcessingQueue) {
            processMessageQueue()
        }
    }
    
    private fun processMessageQueue() {
        if (!isAdded || messageQueue.isEmpty()) {
            isProcessingQueue = false
            return
        }
        
        isProcessingQueue = true
        
        // Prendre le premier message de la queue
        val currentMessage = messageQueue.removeAt(0)
        currentMessageType = currentMessage.type
        
        // Afficher le message
        displayMessage(currentMessage.text, currentMessage.duration)
        
        // Calculer la durée totale avec pause à la fin
        val displayDuration = calculateDisplayDuration(currentMessage)
        val pauseDuration = if (currentMessage.text.length > 30) 2000L else 500L // Pause plus longue pour les messages qui défilent
        
        // Programmer l'arrêt du défilement et la pause
        statusMessageHandler = Runnable {
            if (isAdded) {
                // Garder le défilement marquee actif jusqu'à la suppression du message
                // Le marquee continue de défiler pendant la pause
                
                // Pause pour laisser le temps de lire (marquee toujours actif)
                mainHandler.postDelayed({
                    if (isAdded) {
                        // Arrêter le défilement seulement quand on supprime le message
                        statusText.isSelected = false
                        
                        // Retour au statut de base
                        showDefaultStatus()
                        
                        // Traiter le prochain message dans la queue
                        processMessageQueue()
                    }
                }, pauseDuration)
            }
        }
        
        mainHandler.postDelayed(statusMessageHandler!!, displayDuration)
    }
    
    private fun displayMessage(message: String, @Suppress("UNUSED_PARAMETER") duration: Long) {
        // Afficher le message complet
        statusText.text = message
        
        // Activer le défilement marquee en continu pour tous les messages
        statusText.isSelected = true
        
        // S'assurer que le marquee fonctionne correctement
        statusText.requestFocus()
        
        // Log pour debug
        android.util.Log.d("StatusText", "Displaying: '$message' (Type: $currentMessageType, Scroll: true, Length: ${message.length})")
    }
    
    private fun calculateDisplayDuration(message: StatusMessage): Long {
        val baseDuration = when (message.type) {
            MessageType.STATUS -> 2000L
            MessageType.VOICE -> 3000L
            MessageType.AI -> 4000L
            MessageType.COMMAND -> 2500L
            MessageType.ERROR -> 3000L
            MessageType.ANIMATION -> 1500L
        }
        
        // Ajouter du temps supplémentaire pour les messages longs qui défilent
        val additionalTime = if (message.text.length > 30) {
            // Calculer le temps nécessaire pour que le message défile complètement
            // Vitesse de défilement : environ 15 caractères par seconde (plus rapide)
            val scrollTime = (message.text.length * 67L) // 67ms par caractère pour défilement rapide
            val bufferTime = 1000L // 1 seconde de buffer pour s'assurer que tout le texte défile
            scrollTime + bufferTime
        } else {
            0L
        }
        
        val totalDuration = baseDuration + additionalTime
        
        // Log pour debug du timing
        android.util.Log.d("StatusText", "Timing: Base=${baseDuration}ms, Additional=${additionalTime}ms, Total=${totalDuration}ms")
        
        return totalDuration
    }
    
    private fun showDefaultStatus() {
        if (!isAdded) return
        
        // Afficher un message vide (les indicateurs RDY/BSY suffisent)
        statusText.text = ""
        statusText.isSelected = false
        currentMessageType = MessageType.STATUS
    }
    
    private fun clearMessageQueue() {
        messageQueue.clear()
        isProcessingQueue = false
        statusMessageHandler?.let { mainHandler.removeCallbacks(it) }
    }
    
    private fun togglePersistentMode() {
        isPersistentMode = !isPersistentMode
        
        if (isPersistentMode) {
            showStatusMessage("Mode KITT persistant activé", 3000, MessageType.COMMAND)
            // Garder KITT actif même si l'utilisateur navigue
            activity?.let { act ->
                if (act is MainActivity) {
                    act.setKittPersistentMode(true)
                }
            }
        } else {
            showStatusMessage("Mode KITT persistant désactivé", 3000, MessageType.COMMAND)
            // Permettre à KITT de se fermer normalement
            activity?.let { act ->
                if (act is MainActivity) {
                    act.setKittPersistentMode(false)
                }
            }
        }
    }
    
    private fun startScannerAnimation(speed: Long) {
        stopScannerAnimation()
        
        scannerAnimation = object : Runnable {
            override fun run() {
                updateScanner()
                mainHandler.postDelayed(this, speed)
            }
        }
        mainHandler.post(scannerAnimation!!)
    }
    
    private fun stopScannerAnimation() {
        scannerAnimation?.let { mainHandler.removeCallbacks(it) }
        scannerAnimation = null
    }
    
    private fun startKittScanAnimation() {
        // Désactiver la switch pendant l'animation
        powerSwitch.isEnabled = false
        
        // Récupérer la ligne de scan du layout
        scanLineView = view?.findViewById(R.id.scanLineView)
        
        // Rendre la ligne visible
        scanLineView?.visibility = View.VISIBLE
        scanLineView?.alpha = 1f
        
        // Démarrer l'animation de scan horizontal
        scanAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.kitt_scan_horizontal)
        scanAnimation?.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            override fun onAnimationEnd(animation: Animation?) {
                // Masquer la ligne après l'animation
                scanLineView?.visibility = View.GONE
                scanLineView?.alpha = 0f
                
                // Attendre la fin de l'animation fluide
                mainHandler.postDelayed({
                    setReadyMode()
                    // Réactiver la switch après l'animation
                    powerSwitch.isEnabled = true
                }, 1200) // 1.2 seconde pour l'animation fluide complète
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })
        scanLineView?.startAnimation(scanAnimation)
        
        // Démarrer l'animation fluide des boutons
        startSmoothButtonAnimation()
    }
    
    private fun startSmoothButtonAnimation() {
        // Animation fluide des boutons (rouge foncé → ambre → rouge vif)
        val allButtons = listOf(
            R.id.sendButton, R.id.menuDrawerButton, R.id.vuModeButton,
            R.id.aiButton, R.id.thinkButton, R.id.resetButton
        )

        // Liste des Status Bar Indicators (voyants de status)
        val statusBarIndicators = listOf(
            R.id.statusBarIndicatorBSY, R.id.statusBarIndicatorRDY, R.id.statusBarIndicatorNET, R.id.statusBarIndicatorMSQ
        )
        
        allButtons.forEach { buttonId ->
            val button = view?.findViewById<MaterialButton>(buttonId)
            button?.let {
                // Animation fluide du texte (rouge foncé → ambre → rouge vif)
                val textAnimator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(requireContext(), R.color.kitt_red_dark),
                    ContextCompat.getColor(requireContext(), R.color.amber_primary),
                    ContextCompat.getColor(requireContext(), R.color.kitt_red)
                )
                textAnimator.duration = 600 // 1.2 secondes pour l'animation complète
                textAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setTextColor(color)
                }
                textAnimator.start()
                
                // Animation fluide des contours (rouge foncé → ambre → rouge vif)
                val strokeAnimator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(requireContext(), R.color.kitt_red_dark),
                    ContextCompat.getColor(requireContext(), R.color.amber_primary),
                    ContextCompat.getColor(requireContext(), R.color.kitt_red)
                )
                strokeAnimator.duration = 600 // 1.2 secondes pour l'animation complète
                strokeAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setStrokeColor(android.content.res.ColorStateList.valueOf(color))
                }
                strokeAnimator.start()
            }
        }

        // Animation fluide des Status Bar Indicators (sans effet ambre)
        statusBarIndicators.forEach { textViewId ->
            val textView = view?.findViewById<com.google.android.material.textview.MaterialTextView>(textViewId)
            textView?.let {
                // Animation fluide du texte (rouge foncé → noir)
                val textAnimator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(requireContext(), R.color.kitt_red_dark),
                    ContextCompat.getColor(requireContext(), R.color.kitt_black)
                )
                textAnimator.duration = 600
                textAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setTextColor(color)
                }
                textAnimator.start()
                
                // Animation du fond : changer le drawable progressivement
                mainHandler.postDelayed({
                    it.setBackgroundResource(R.drawable.kitt_status_background_active)
                }, 300)
            }
        }
    }
    
    private fun startStrokeGlowAnimation() {
        // Animation des contours des boutons (rouge foncé → rouge vif)
        val allButtons = listOf(
            R.id.sendButton, R.id.menuDrawerButton, R.id.vuModeButton,
            R.id.aiButton, R.id.thinkButton, R.id.resetButton
        )

        // Liste des Status Bar Indicators (voyants de status)
        val statusBarIndicators = listOf(
            R.id.statusBarIndicatorBSY, R.id.statusBarIndicatorRDY, R.id.statusBarIndicatorNET, R.id.statusBarIndicatorMSQ
        )
        
        allButtons.forEach { buttonId ->
            val button = view?.findViewById<MaterialButton>(buttonId)
            button?.let {
                // Animation de couleur des contours avec ValueAnimator
                val startColor = ContextCompat.getColor(requireContext(), R.color.kitt_red_dark)
                val endColor = ContextCompat.getColor(requireContext(), R.color.kitt_red)
                
                val strokeAnimator = ValueAnimator.ofArgb(startColor, endColor)
                strokeAnimator.duration = 800
                strokeAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setStrokeColor(android.content.res.ColorStateList.valueOf(color))
                }
                strokeAnimator.start()
            }
        }

        // Animation des Status Bar Indicators (rouge foncé → noir, fond → rouge)
        statusBarIndicators.forEach { textViewId ->
            val textView = view?.findViewById<com.google.android.material.textview.MaterialTextView>(textViewId)
            textView?.let {
                // Animation du texte (rouge foncé → noir)
                val textAnimator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(requireContext(), R.color.kitt_red_dark),
                    ContextCompat.getColor(requireContext(), R.color.kitt_black)
                )
                textAnimator.duration = 800
                textAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setTextColor(color)
                }
                textAnimator.start()
                
                // Animation du fond : changer le drawable
                it.setBackgroundResource(R.drawable.kitt_status_background_active)
            }
        }
    }
    
    private fun startButtonScanAnimation() {
        // Animation des boutons (rouge foncé → ambre)
        val allButtons = listOf(
            R.id.sendButton, R.id.menuDrawerButton, R.id.vuModeButton,
            R.id.aiButton, R.id.thinkButton, R.id.resetButton
        )

        // Liste des Status Bar Indicators (voyants de status)
        val statusBarIndicators = listOf(
            R.id.statusBarIndicatorBSY, R.id.statusBarIndicatorRDY, R.id.statusBarIndicatorNET, R.id.statusBarIndicatorMSQ
        )
        
        allButtons.forEach { buttonId ->
            val button = view?.findViewById<MaterialButton>(buttonId)
            button?.let {
                // Animation de couleur du texte
                val textAnimator = ObjectAnimator.ofArgb(
                    it, "textColor",
                    ContextCompat.getColor(requireContext(), R.color.kitt_red_dark),
                    ContextCompat.getColor(requireContext(), R.color.amber_primary)
                )
                textAnimator.duration = 800
                textAnimator.start()
                
                // Animation de couleur des contours
                val startStrokeColor = ContextCompat.getColor(requireContext(), R.color.kitt_red_dark)
                val endStrokeColor = ContextCompat.getColor(requireContext(), R.color.amber_primary)
                
                val strokeAnimator = ValueAnimator.ofArgb(startStrokeColor, endStrokeColor)
                strokeAnimator.duration = 800
                strokeAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setStrokeColor(android.content.res.ColorStateList.valueOf(color))
                }
                strokeAnimator.start()
            }
        }

        // Animation des Status Bar Indicators (rouge foncé → noir, fond → rouge)
        statusBarIndicators.forEach { textViewId ->
            val textView = view?.findViewById<com.google.android.material.textview.MaterialTextView>(textViewId)
            textView?.let {
                // Animation du texte (rouge foncé → noir)
                val textAnimator = ObjectAnimator.ofArgb(
                    it, "textColor",
                    ContextCompat.getColor(requireContext(), R.color.kitt_red_dark),
                    ContextCompat.getColor(requireContext(), R.color.kitt_black)
                )
                textAnimator.duration = 800
                textAnimator.start()
                
                // Animation du fond : changer le drawable
                it.setBackgroundResource(R.drawable.kitt_status_background_active)
            }
        }
    }
    
    private fun animateButtonsToRed() {
        // Animation de retour progressive d'ambre vers rouge vif
        val allButtons = listOf(
            R.id.sendButton, R.id.menuDrawerButton, R.id.vuModeButton,
            R.id.aiButton, R.id.thinkButton, R.id.resetButton
        )

        // Liste des Status Bar Indicators (voyants de status)
        val statusBarIndicators = listOf(
            R.id.statusBarIndicatorBSY, R.id.statusBarIndicatorRDY, R.id.statusBarIndicatorNET, R.id.statusBarIndicatorMSQ
        )
        
        allButtons.forEach { buttonId ->
            val button = view?.findViewById<MaterialButton>(buttonId)
            button?.let {
                // Animation de couleur du texte (ambre → rouge vif)
                val textAnimator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(requireContext(), R.color.amber_primary),
                    ContextCompat.getColor(requireContext(), R.color.kitt_red)
                )
                textAnimator.duration = 400
                textAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setTextColor(color)
                }
                textAnimator.start()
                
                // Animation de couleur des contours (ambre → rouge vif)
                val strokeAnimator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(requireContext(), R.color.amber_primary),
                    ContextCompat.getColor(requireContext(), R.color.kitt_red)
                )
                strokeAnimator.duration = 400
                strokeAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setStrokeColor(android.content.res.ColorStateList.valueOf(color))
                }
                strokeAnimator.start()
            }
        }

        // Animation des Status Bar Indicators (rouge foncé → noir, fond → rouge)
        statusBarIndicators.forEach { textViewId ->
            val textView = view?.findViewById<com.google.android.material.textview.MaterialTextView>(textViewId)
            textView?.let {
                // Animation du texte (rouge foncé → noir)
                val textAnimator = ValueAnimator.ofArgb(
                    ContextCompat.getColor(requireContext(), R.color.kitt_red_dark),
                    ContextCompat.getColor(requireContext(), R.color.kitt_black)
                )
                textAnimator.duration = 400
                textAnimator.addUpdateListener { animator ->
                    val color = animator.animatedValue as Int
                    it.setTextColor(color)
                }
                textAnimator.start()
                
                // Animation du fond : changer le drawable
                it.setBackgroundResource(R.drawable.kitt_status_background_active)
            }
        }
    }
    
    private fun forceButtonsToRed() {
        // Forcer la mise à jour des couleurs des boutons
        val allButtons = listOf(
            R.id.sendButton, R.id.menuDrawerButton, R.id.vuModeButton,
            R.id.aiButton, R.id.thinkButton, R.id.resetButton
        )

        // Liste des Status Bar Indicators (voyants de status)
        val statusBarIndicators = listOf(
            R.id.statusBarIndicatorBSY, R.id.statusBarIndicatorRDY, R.id.statusBarIndicatorNET, R.id.statusBarIndicatorMSQ
        )
        
        val redColor = ContextCompat.getColor(requireContext(), R.color.kitt_red)
        
        allButtons.forEach { buttonId ->
            val button = view?.findViewById<MaterialButton>(buttonId)
            button?.let {
                it.setTextColor(redColor)
                it.setStrokeColor(android.content.res.ColorStateList.valueOf(redColor))
                it.invalidate() // Forcer le redraw
            }
        }

        // Forcer la mise à jour des couleurs des Status Bar Indicators
        statusBarIndicators.forEach { textViewId ->
            val textView = view?.findViewById<com.google.android.material.textview.MaterialTextView>(textViewId)
            textView?.let {
                // Mode ON : fond rouge, texte noir
                it.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
                // Tous utilisent maintenant le même drawable
                it.setBackgroundResource(R.drawable.kitt_status_background_active)
                it.invalidate() // Forcer le redraw
            }
        }
    }
    
    private fun updateScanner() {
        // Éteindre tous les segments
        kittSegments.forEach { segment ->
            segment.setImageResource(R.drawable.kitt_scanner_segment_off)
        }
        
        // Créer l'effet de balayage avec dégradé de luminosité
        for (i in -2..2) {
            val index = kittPosition + i
            if (index in 0 until kittSegments.size) {
                val segment = kittSegments[index]
                when (i) {
                    0 -> segment.setImageResource(R.drawable.kitt_scanner_segment_max)
                    1, -1 -> segment.setImageResource(R.drawable.kitt_scanner_segment_high)
                    2, -2 -> segment.setImageResource(R.drawable.kitt_scanner_segment_medium)
                }
            }
        }
        
        // Mouvement avec rebond
        kittPosition += kittDirection
        
        if (kittPosition >= kittSegments.size - 1) {
            kittDirection = -1
        } else if (kittPosition <= 0) {
            kittDirection = 1
        }
    }
    
    private fun resetScanner() {
        stopScannerAnimation()
        kittSegments.forEachIndexed { index, segment ->
            segment.setImageResource(R.drawable.kitt_scanner_segment_off)
            // Segments centraux légèrement allumés par défaut
            if (index in 10..13) {
                segment.setImageResource(R.drawable.kitt_scanner_segment_low)
            }
        }
        kittPosition = 0
        kittDirection = 1
    }
    
    private fun startVuMeterAnimation() {
        stopVuMeterAnimation()
        
        // Debug : Afficher le mode VU-meter
        android.util.Log.d("VUMeter", "startVuMeterAnimation called, mode: $vuMeterMode")
        
        // Si le mode est OFF, ne pas démarrer l'animation
        if (vuMeterMode == VUMeterMode.OFF) {
            android.util.Log.d("VUMeter", "Mode OFF, resetting VU-meter")
            resetVuMeter()
            return
        }
        
        // Si le mode est VOICE et que TTS ne parle pas, ne pas démarrer l'animation
        if (vuMeterMode == VUMeterMode.VOICE && !isTTSSpeaking) {
            android.util.Log.d("VUMeter", "Mode VOICE but TTS not speaking, resetting VU-meter")
            resetVuMeter()
            return
        }
        
        vuMeterAnimation = object : Runnable {
            override fun run() {
                when (vuMeterMode) {
                    VUMeterMode.OFF -> {
                        // Mode OFF - éteindre toutes les LEDs
                        resetVuMeter()
                        return // Ne pas programmer la prochaine exécution
                    }
                    VUMeterMode.VOICE -> {
                        if (isTTSSpeaking) {
                            // Animation TTS basée sur le volume système Android
                            updateVuMeterFromSystemVolume()
                            // Programmer la prochaine exécution seulement si TTS parle
                            mainHandler.postDelayed(this, 60)
                        } else {
                            // Mode VOICE : Éteindre complètement quand TTS ne parle pas
                            android.util.Log.d("VUMeter", "VOICE mode but TTS not speaking, resetting VU-meter")
                            resetVuMeter()
                            return // Arrêter l'animation complètement
                        }
                    }
                    VUMeterMode.AMBIENT -> {
                        // Mode AMBIENT : Utiliser le microphone pour les sons environnants
                        android.util.Log.d("VUMeter", "AMBIENT mode, microphone level: $currentMicrophoneLevel")
                        if (currentMicrophoneLevel > -20f) { // Seuil de sensibilité
                            val normalizedLevel = (currentMicrophoneLevel + 20f) / 20f
                            val clampedLevel = normalizedLevel.coerceIn(0f, 1f)
                            android.util.Log.d("VUMeter", "AMBIENT: normalized=$normalizedLevel, clamped=$clampedLevel")
                            updateVuMeter(clampedLevel)
                        } else {
                            // Niveau très faible si pas de son
                            android.util.Log.d("VUMeter", "AMBIENT: level too low, using 0.05f")
                            updateVuMeter(0.05f)
                        }
                    }
                }
                // Programmer la prochaine exécution seulement pour les modes qui en ont besoin
                when (vuMeterMode) {
                    VUMeterMode.AMBIENT -> {
                mainHandler.postDelayed(this, 80) // Fréquence plus rapide pour plus de réactivité
                    }
                    VUMeterMode.VOICE -> {
                        // En mode VOICE, la prochaine exécution est gérée dans le bloc VOICE
                        // Ne pas programmer ici pour éviter les conflits
                    }
                    VUMeterMode.OFF -> {
                        // Mode OFF - pas de prochaine exécution
                    }
                }
            }
        }
        mainHandler.post(vuMeterAnimation!!)
    }
    
    private fun updateVuMeterFromSystemVolume() {
        // Simulation réaliste du TTS avec variations temporelles
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val normalizedVolume = currentVolume / maxVolume
        
        // Créer des variations plus réalistes basées sur le temps
        val time = System.currentTimeMillis() * 0.01
        val baseLevel = normalizedVolume * 0.5f
        
        // Combinaison de plusieurs ondes pour un effet plus naturel
        val wave1 = (Math.sin(time) * 0.3f).toFloat()
        val wave2 = (Math.sin(time * 1.7) * 0.2f).toFloat()
        val wave3 = (Math.sin(time * 0.5) * 0.15f).toFloat()
        val randomVariation = (Math.random() * 0.2 - 0.1).toFloat()
        
        val ttsLevel = (baseLevel + wave1 + wave2 + wave3 + randomVariation).coerceIn(0.1f, 0.95f)
        
        updateVuMeter(ttsLevel)
    }
    
    private fun resetVuMeterToBase() {
        // Remettre le VU-meter au niveau de base quand TTS s'arrête
        when (vuMeterMode) {
            VUMeterMode.OFF -> {
            resetVuMeter() // Éteindre complètement
            }
            VUMeterMode.VOICE -> {
                resetVuMeter() // Éteindre complètement en mode VOICE quand TTS s'arrête
            }
            VUMeterMode.AMBIENT -> {
                updateVuMeter(0.05f) // Niveau très faible pour AMBIENT
            }
        }
    }
    
    private var systemVolumeAnimation: Runnable? = null
    
    private fun startSystemVolumeAnimation() {
        stopSystemVolumeAnimation()
        
        systemVolumeAnimation = object : Runnable {
            override fun run() {
                if (isTTSSpeaking && vuMeterMode == VUMeterMode.VOICE) {
                    updateVuMeterFromSystemVolume()
                    mainHandler.postDelayed(this, 60) // Animation très rapide pendant TTS
                } else {
                    // Si TTS ne parle pas ou mode changé, arrêter l'animation
                    android.util.Log.d("VUMeter", "Stopping system volume animation - TTS: $isTTSSpeaking, Mode: $vuMeterMode")
                }
            }
        }
        mainHandler.post(systemVolumeAnimation!!)
    }
    
    private fun stopSystemVolumeAnimation() {
        systemVolumeAnimation?.let { mainHandler.removeCallbacks(it) }
        systemVolumeAnimation = null
    }
    
    private fun stopVuMeterAnimation() {
        vuMeterAnimation?.let { mainHandler.removeCallbacks(it) }
        vuMeterAnimation = null
    }
    
    private fun updateVuMeter(level: Float = 0.3f) {
        // Debug : Afficher le niveau reçu
        android.util.Log.d("VUMeter", "updateVuMeter called with level: $level")
        
        // Debug : Vérifier les LEDs VU
        android.util.Log.d("VUMeter", "Total VU LEDs: ${vuLeds.size}")
        
        // Si le niveau est très faible, éteindre complètement
        if (level < 0.05f) {
            android.util.Log.d("VUMeter", "Level too low, turning off LEDs")
            vuLeds.forEach { led ->
                    led.setImageResource(R.drawable.kitt_vu_led_off)
            }
            return
        }
        
        // Améliorer la sensibilité - amplification du signal
        val amplifiedLevel = kotlin.math.sqrt(level.toDouble()).toFloat() // Racine carrée pour plus de sensibilité
        val enhancedLevel = (amplifiedLevel * 1.8f).coerceIn(0f, 1f) // Amplification x1.8
        
        // COMMENCER AVEC TOUTES LES LEDs ÉTEINTES (NOIRES)
        vuLeds.forEach { led ->
            led.setImageResource(R.drawable.kitt_vu_led_off)
        }
        
        // Gérer les LEDs par colonnes verticales (3 colonnes)
        val totalColumns = 3
        val ledsPerColumn = vuLeds.size / totalColumns // 20 LEDs par colonne
        
        // Colonnes latérales synchronisées (même niveau)
        val leftRightLevel = enhancedLevel * 0.7f // Colonnes latérales à 70% du niveau central
        val centerLevel = enhancedLevel
        
        // Traiter chaque colonne verticale
        for (columnIndex in 0 until totalColumns) {
            // Utiliser le niveau approprié selon la colonne
            val adjustedLevel = when (columnIndex) {
                0, 2 -> leftRightLevel // Colonnes latérales synchronisées
                1 -> centerLevel       // Colonne centrale
                else -> enhancedLevel
            }
            
            // Calculer combien de LEDs allumer selon le niveau (de bas en haut)
            val ledsToTurnOn = (adjustedLevel * ledsPerColumn).toInt().coerceAtMost(ledsPerColumn)
            
            // Debug : Afficher le calcul
            android.util.Log.d("VUMeter", "Column $columnIndex: adjustedLevel=$adjustedLevel, ledsToTurnOn=$ledsToTurnOn")
            
            // Choisir le mode d'animation selon vuAnimationMode
            when (vuAnimationMode) {
                VUAnimationMode.ORIGINAL -> {
                    // Animation originale : du milieu (9) vers le bas (0) ET du milieu (10) vers le haut (19)
                    // Pour 20 LEDs (0-19), le milieu est entre 9 et 10
                    val bottomLeds = ledsToTurnOn / 2  // Moins de LEDs en bas
                    val topLeds = ledsToTurnOn - bottomLeds   // Plus de LEDs en haut pour couvrir 16,17
                    
                    // Allumer du milieu vers le bas (9,8,7,6,5,4,3,2,1,0)
                    for (i in 0 until bottomLeds) {
                        val ledIndex = (columnIndex * ledsPerColumn) + (9 - i) // 9 vers 0
                        if (ledIndex in 0 until vuLeds.size) {
                            val positionInColumn = 9 - i
                            val ledColor = when (columnIndex) {
                                0, 2 -> { // Colonnes latérales
                                    when (positionInColumn) {
                                        0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_warning // Ambre aux extrémités (ajout positions 14,15)
                                        else -> R.drawable.kitt_vu_led_active // Rouge pour les autres positions
                                    }
                                }
                                1 -> { // Colonne centrale
                                    R.drawable.kitt_vu_led_active // Toujours rouge
                                }
                                else -> R.drawable.kitt_vu_led_active
                            }
                            
                            vuLeds[ledIndex].setImageResource(ledColor)
                            val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                            android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - ORIGINAL BOTTOM")
                        }
                    }
                    
                        // Allumer du milieu vers le haut (10,11,12,13,14,15,16,17,18,19)
                        for (i in 0 until topLeds) {
                            val ledIndex = (columnIndex * ledsPerColumn) + (10 + i) // 10 vers 19
                            if (ledIndex in 0 until vuLeds.size) {
                                val positionInColumn = 10 + i
                                val ledColor = when (columnIndex) {
                                    0, 2 -> { // Colonnes latérales
                                        when (positionInColumn) {
                                            0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_warning // Ambre aux extrémités (ajout positions 14,15)
                                            else -> R.drawable.kitt_vu_led_active // Rouge pour les autres positions
                                        }
                                    }
                                    1 -> { // Colonne centrale
                                        R.drawable.kitt_vu_led_active // Toujours rouge
                                    }
                                    else -> R.drawable.kitt_vu_led_active
                                }
                                
                                vuLeds[ledIndex].setImageResource(ledColor)
                                val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                                android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - ORIGINAL TOP")
                            }
                        }
                        
                }
                    VUAnimationMode.DUAL -> {
                        // Animation dual : en-haut et en-bas vers le centre
                    val halfLeds = maxOf(1, ledsToTurnOn / 2)  // Minimum 1, maximum la moitié
                    val remainingLeds = ledsToTurnOn - halfLeds  // Le reste au centre
                    
                    // Allumer de bas en haut (première moitié)
                    for (i in 0 until halfLeds) {
                        val ledIndex = (columnIndex * ledsPerColumn) + i
                        if (ledIndex in 0 until vuLeds.size) {
                            val positionInColumn = i
                            val ledColor = when (columnIndex) {
                                0, 2 -> { // Colonnes latérales - COULEURS INVERSÉES
                                    when (positionInColumn) {
                                        0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_active // Rouge aux extrémités
                                        else -> R.drawable.kitt_vu_led_warning // Ambre au centre
                                    }
                                }
                                1 -> { // Colonne centrale
                                    R.drawable.kitt_vu_led_active // Toujours rouge
                                }
                                else -> R.drawable.kitt_vu_led_active
                            }
                            
                            vuLeds[ledIndex].setImageResource(ledColor)
                            val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                            android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - BOTTOM")
                        }
                    }
                    
                    // Allumer de haut en bas (deuxième moitié)
                    for (i in 0 until remainingLeds) {
                        val ledIndex = (columnIndex * ledsPerColumn) + (ledsPerColumn - 1 - i)
                        if (ledIndex in 0 until vuLeds.size) {
                            val positionInColumn = ledsPerColumn - 1 - i
                            val ledColor = when (columnIndex) {
                                0, 2 -> { // Colonnes latérales - COULEURS INVERSÉES
                                    when (positionInColumn) {
                                        0, 1, 2, 3, 4, 5, 14, 15, 16, 17, 18, 19 -> R.drawable.kitt_vu_led_active // Rouge aux extrémités
                                        else -> R.drawable.kitt_vu_led_warning // Ambre au centre
                                    }
                                }
                                1 -> { // Colonne centrale
                                    R.drawable.kitt_vu_led_active // Toujours rouge
                                }
                                else -> R.drawable.kitt_vu_led_active
                            }
                            
                            vuLeds[ledIndex].setImageResource(ledColor)
                            val colorName = if (ledColor == R.drawable.kitt_vu_led_warning) "AMBER" else if (ledColor == R.drawable.kitt_vu_led_green) "GREEN" else "RED"
                            android.util.Log.d("VUMeter", "LED $ledIndex: $colorName (column $columnIndex, position $positionInColumn) - TOP")
                        }
                    }
                }
            }
        }
    }
    
    private fun resetVuMeter() {
        stopVuMeterAnimation()
        vuLeds.forEach { led ->
            led.setImageResource(R.drawable.kitt_vu_led_off)
        }
    }
    
    private fun toggleVUMeterMode() {
        vuMeterMode = when (vuMeterMode) {
            VUMeterMode.VOICE -> VUMeterMode.AMBIENT
            VUMeterMode.AMBIENT -> VUMeterMode.OFF
            VUMeterMode.OFF -> VUMeterMode.VOICE
        }
        
        // Mettre à jour le texte du bouton
        vuModeButton.text = when (vuMeterMode) {
            VUMeterMode.VOICE -> "VU-VOIX"
            VUMeterMode.AMBIENT -> "VU-AMBI"
            VUMeterMode.OFF -> "VU-OFF"
        }
        
        // Gérer l'écoute du microphone selon le mode
        when (vuMeterMode) {
            VUMeterMode.VOICE -> {
                // Mode VOICE : Pas d'écoute microphone, suit uniquement le TTS
                stopMicrophoneListening()
            }
            VUMeterMode.AMBIENT -> {
                // Mode AMBIENT : Écouter le microphone pour les sons environnants
                if (isReady) {
                    startMicrophoneListening()
                }
            }
            VUMeterMode.OFF -> {
                // Mode OFF : Arrêter l'écoute du microphone
                stopMicrophoneListening()
            }
        }
        
        // Redémarrer l'animation selon le nouveau mode
        stopVuMeterAnimation()
        startVuMeterAnimation()
        
        // Mode VU-meter changé - pas d'affichage de statut pour éviter les conflits
        
        // Test : Forcer une animation de test pour diagnostiquer
        if (vuMeterMode == VUMeterMode.AMBIENT) {
            testVuMeterAnimation()
        }
    }
    
    private fun toggleMusic() {
        android.util.Log.d("Music", "toggleMusic() appelé - isMusicPlaying: $isMusicPlaying")
        if (isMusicPlaying) {
            android.util.Log.d("Music", "Arrêt de la musique...")
            stopMusic()
        } else {
            android.util.Log.d("Music", "Démarrage de la musique...")
            playMusic()
        }
    }
    
    private fun playMusic() {
        try {
            android.util.Log.d("Music", "=== DÉBUT LECTURE MUSIQUE ===")
            showStatusMessage("Chargement de la musique...", 2000, MessageType.STATUS)
            
            // Vérifier les permissions audio
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.MODIFY_AUDIO_SETTINGS) 
                != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.e("Music", "Permission MODIFY_AUDIO_SETTINGS manquante !")
                showStatusMessage("Erreur: Permission audio manquante", 3000, MessageType.ERROR)
                return
            }
            
            // Vérifier que le MediaPlayer est initialisé
            if (mediaPlayer == null) {
                android.util.Log.e("Music", "MediaPlayer non initialisé !")
                showStatusMessage("Erreur: MediaPlayer non initialisé", 3000, MessageType.ERROR)
                return
            }
            
            // Réinitialiser le MediaPlayer s'il était utilisé
            if (isMusicPlaying) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            
            android.util.Log.d("Music", "Chargement du fichier MP3...")
            val assetFileDescriptor = requireContext().assets.openFd("musicTheme/Mundian To Bach Ke - Panjabi MC.mp3")
            mediaPlayer?.setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
            assetFileDescriptor.close()
            
            // Configuration des listeners AVANT prepare()
            mediaPlayer?.setOnCompletionListener {
                android.util.Log.d("Music", "Musique terminée")
                isMusicPlaying = false
                updateMusicButtonState(false)
                updateStatusIndicators() // Mettre à jour l'indicateur MSQ
                showStatusMessage("Musique terminée", 2000, MessageType.STATUS)
            }
            
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                android.util.Log.e("Music", "ERREUR MediaPlayer - what: $what, extra: $extra")
                isMusicPlaying = false
                updateMusicButtonState(false)
                updateStatusIndicators() // Mettre à jour l'indicateur MSQ
                showStatusMessage("Erreur audio (code: $what)", 3000, MessageType.ERROR)
                true
            }
            
            android.util.Log.d("Music", "Préparation du MediaPlayer...")
            mediaPlayer?.prepare()
            
            android.util.Log.d("Music", "Démarrage de la lecture...")
            mediaPlayer?.start()
            isMusicPlaying = true
            updateMusicButtonState(true)
            updateStatusIndicators() // Mettre à jour l'indicateur MSQ
            showStatusMessage("Musique: Mundian To Bach Ke", 3000, MessageType.VOICE)
            android.util.Log.d("Music", "=== MUSIQUE DÉMARRÉE ===")
            
        } catch (e: Exception) {
            android.util.Log.e("Music", "ERREUR: ${e.message}")
            showStatusMessage("Erreur: ${e.message}", 5000, MessageType.ERROR)
            isMusicPlaying = false
            updateMusicButtonState(false)
            updateStatusIndicators() // Mettre à jour l'indicateur MSQ
        }
    }
    
    private fun stopMusic() {
        try {
            android.util.Log.d("Music", "Arrêt de la musique...")
            mediaPlayer?.stop()
            isMusicPlaying = false
            
            // Mettre à jour le bouton dans le drawer
            updateMusicButtonState(false)
            updateStatusIndicators() // Mettre à jour l'indicateur MSQ
            
            // Afficher le message de marquee
            showStatusMessage("Musique arrêtée", 2000, MessageType.STATUS)
            android.util.Log.d("Music", "Musique arrêtée avec succès")
            
        } catch (e: Exception) {
            android.util.Log.e("Music", "Erreur lors de l'arrêt: ${e.message}")
            showStatusMessage("Erreur: ${e.message}", 3000, MessageType.ERROR)
        }
    }
    
    private fun updateMusicButtonState(isPlaying: Boolean) {
        try {
            val drawerFragment = childFragmentManager.fragments.find { it is KittDrawerFragment } as? KittDrawerFragment
            if (drawerFragment != null) {
                val musicButton = drawerFragment.view?.findViewById<MaterialButton>(R.id.musicButton)
                if (isPlaying) {
                    musicButton?.text = "ARRÊTER"
                    musicButton?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
                    musicButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
                } else {
                    musicButton?.text = "MUSIQUE"
                    musicButton?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_red_alpha))
                    musicButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("Music", "Could not update music button: ${e.message}")
        }
    }
    
    private fun toggleVUAnimationMode() {
        vuAnimationMode = when (vuAnimationMode) {
            VUAnimationMode.ORIGINAL -> VUAnimationMode.DUAL
            VUAnimationMode.DUAL -> VUAnimationMode.ORIGINAL
        }

        android.util.Log.d("VUMeter", "Animation mode switched to: $vuAnimationMode")

        // Redémarrer l'animation pour appliquer le nouveau mode
        if (vuMeterMode != VUMeterMode.OFF) {
            stopVuMeterAnimation()
            startVuMeterAnimation()
        }
        
        // Mettre à jour l'apparence des boutons dans le drawer
        updateAnimationModeButtons()
    }
    
    private fun updateAnimationModeButtons() {
        // Mettre à jour l'apparence des boutons dans le drawer si ils existent
        try {
            val drawerFragment = childFragmentManager.fragments.find { it is KittDrawerFragment } as? KittDrawerFragment
            if (drawerFragment != null) {
                val isOriginal = vuAnimationMode == VUAnimationMode.ORIGINAL
                android.util.Log.d("VUMeter", "Updating animation buttons: vuAnimationMode=$vuAnimationMode, isOriginal=$isOriginal")
                drawerFragment.updateAnimationModeButtons(isOriginal)
            }
        } catch (e: Exception) {
            android.util.Log.d("VUMeter", "Could not update animation mode buttons: ${e.message}")
        }
    }
    
    private fun testVuMeterAnimation() {
        // Test de l'animation VU-meter avec des niveaux simulés
        android.util.Log.d("VUMeter", "Testing VU-meter animation...")
        
        val testLevels = listOf(0.1f, 0.3f, 0.6f, 0.9f, 0.5f, 0.2f, 0.05f)
        var testIndex = 0
        
        val testRunnable = object : Runnable {
            override fun run() {
                if (testIndex < testLevels.size) {
                    val testLevel = testLevels[testIndex]
                    android.util.Log.d("VUMeter", "Test level: $testLevel")
                    updateVuMeter(testLevel)
                    testIndex++
                    mainHandler.postDelayed(this, 500) // 500ms entre chaque niveau
                } else {
                    android.util.Log.d("VUMeter", "Test animation completed")
                }
            }
        }
        mainHandler.post(testRunnable)
    }
    
    private fun selectRandomCommand() {
        // Liste des commandes disponibles dans le drawer
        val availableCommands = listOf(
            "ACTIVATE_KITT" to "Activation de KITT",
            "SYSTEM_STATUS" to "Statut du système",
            "ACTIVATE_SCANNER" to "Activation du scanner",
            "ENVIRONMENTAL_ANALYSIS" to "Analyse environnementale",
            "SURVEILLANCE_MODE" to "Mode surveillance",
            "EMERGENCY_MODE" to "Mode urgence",
            "GPS_ACTIVATION" to "Activation GPS",
            "CALCULATE_ROUTE" to "Calcul de route",
            "SET_DESTINATION" to "Définition de destination",
            "OPEN_COMMUNICATION" to "Ouverture de communication",
            "SET_FREQUENCY" to "Définition de fréquence",
            "TRANSMIT_MESSAGE" to "Transmission de message",
            "TURBO_BOOST" to "Turbo boost",
            "PURSUIT_MODE" to "Mode poursuite",
            "DEACTIVATE_KITT" to "Désactivation de KITT"
        )
        
        // Sélection aléatoire
        val randomCommand = availableCommands.random()
        
        // Afficher la commande sélectionnée
        showStatusMessage("Commande sélectionnée: ${randomCommand.second}", 2500, MessageType.COMMAND)
        
        // Énoncer la commande sans l'exécuter
        mainHandler.postDelayed({
            // Annoncer la commande sélectionnée (sans l'exécuter)
            speakAIResponse("Commande sélectionnée: ${randomCommand.second}")
        }, 1000)
    }
    
    private fun showMenuDrawer() {
        // Vérifier que KittFragment est visible et attaché
        if (!isAdded || view == null) {
            android.util.Log.w("KittFragment", "Cannot show drawer: Fragment not attached or view is null")
            return
        }
        
        // Vérifier que drawer_container existe
        val drawerContainer = requireActivity().findViewById<android.view.View>(R.id.drawer_container)
        if (drawerContainer == null) {
            android.util.Log.w("KittFragment", "Cannot show drawer: drawer_container not found")
            return
        }
        
        val drawerFragment = KittDrawerFragment()
        
        drawerFragment.setCommandListener(object : KittDrawerFragment.CommandListener {
            override fun onCommandSelected(command: String) {
                android.util.Log.d("Music", "=== COMMANDE REÇUE: $command ===")
                
                // Traiter les commandes spéciales
                when (command) {
                    "TOGGLE_MUSIC" -> {
                        android.util.Log.d("Music", "Commande TOGGLE_MUSIC détectée")
                        toggleMusic()
                        // Fermer le drawer
                        parentFragmentManager.beginTransaction()
                            .setCustomAnimations(0, R.anim.slide_out_right)
                            .remove(drawerFragment)
                            .commit()
                        return
                    }
                }
                
                // Traiter comme conversation AI pour les autres commandes
                processAIConversation(command)
                
                // Fermer le drawer
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(0, R.anim.slide_out_right)
                    .remove(drawerFragment)
                    .commit()
            }
            
            override fun onCloseDrawer() {
                // Fermer le drawer
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(0, R.anim.slide_out_right)
                    .remove(drawerFragment)
                    .commit()
            }
            
            override fun onConfigurationCenterRequested() {
                // Ouvrir l'activité de configuration IA spécialisée
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    statusText.text = "Erreur ouverture configuration IA"
                    mainHandler.postDelayed({ statusText.text = if (isReady) "KITT READY" else "KITT STANDBY" }, 2000)
                }
            }
            
            override fun onWebServerRequested() {
                // Ouvrir l'activité de configuration serveur spécialisée
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.ServerConfigurationActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    statusText.text = "Erreur ouverture configuration serveur"
                    mainHandler.postDelayed({ statusText.text = if (isReady) "KITT READY" else "KITT STANDBY" }, 2000)
                }
            }
            
            override fun onWebServerConfigRequested() {
                // Ouvrir l'activité de configuration WebServer (port 8888)
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.WebServerConfigActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    statusText.text = "Erreur ouverture configuration WebServer"
                    mainHandler.postDelayed({ statusText.text = if (isReady) "KITT READY" else "KITT STANDBY" }, 2000)
                }
            }
            
            override fun onEndpointsListRequested() {
                // Ouvrir l'activité des endpoints
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.EndpointsListActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    statusText.text = "Erreur ouverture endpoints"
                    mainHandler.postDelayed({ statusText.text = if (isReady) "KITT READY" else "KITT STANDBY" }, 2000)
                }
            }
            
            override fun onHtmlExplorerRequested() {
                // Ouvrir l'explorateur de fichiers via FileServer
                try {
                    if (fileServer != null) {
                        fileServer?.openDirectoryPicker()
                    } else {
                        statusText.text = "Serveur de fichiers non disponible"
                        mainHandler.postDelayed({ statusText.text = if (isReady) "KITT READY" else "KITT STANDBY" }, 2000)
                    }
                } catch (e: Exception) {
                    statusText.text = "Erreur ouverture explorateur"
                    mainHandler.postDelayed({ statusText.text = if (isReady) "KITT READY" else "KITT STANDBY" }, 2000)
                }
            }
            
            override fun onThemeChanged(theme: String) {
                // Appliquer le thème sélectionné
                applySelectedTheme()
                // Mettre à jour le thème du drawer aussi
                drawerFragment.refreshTheme()
            }
            
            override fun onButtonPressed(buttonName: String) {
                // Annoncer le bouton pressé
                speakAIResponse(buttonName)
            }
            
            override fun onPersonalityChanged(personality: String) {
                // Sauvegarder la personnalité dans SharedPreferences
                val aiConfigPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
                aiConfigPrefs.edit().putString("selected_personality", personality).apply()
                
                // Réinitialiser le service IA avec la nouvelle personnalité
                kittAIService = KittAIService(requireContext(), personality, platform = "vocal", actionCallback = this@KittFragment)
                
                // Changer la voix TTS immédiatement
                selectVoiceForPersonality(personality)
                
                // Message de confirmation
                statusText.text = when (personality) {
                    "GLaDOS" -> "GLaDOS ACTIVÉE - VOIX CHANGÉE"
                    else -> "KITT ACTIVÉ - VOIX CHANGÉE"
                }
                
                android.util.Log.i("KittFragment", "✅ Personnalité changée: $personality - Voix TTS mise à jour")
            }
            
            override fun onAnimationModeChanged(mode: String) {
                when (mode) {
                    "ORIGINAL" -> {
                        android.util.Log.d("VUMeter", "Bouton ORIGINAL pressé - Mode: $mode")
                        vuAnimationMode = VUAnimationMode.ORIGINAL
                        showStatusMessage("Animation VU-meter: ORIGINAL (bas en haut)", 1500, MessageType.ANIMATION)
                        speakAIResponse("Mode d'animation VU-meter changé vers l'original : de bas en haut")
                        if (vuMeterMode != VUMeterMode.OFF) {
                            stopVuMeterAnimation()
                            startVuMeterAnimation()
                        }
                        updateAnimationModeButtons()
                    }
                    "DUAL" -> {
                        android.util.Log.d("VUMeter", "Bouton DUAL pressé - Mode: $mode")
                        vuAnimationMode = VUAnimationMode.DUAL
                        showStatusMessage("Animation VU-meter: DUAL (haut et bas)", 1500, MessageType.ANIMATION)
                        speakAIResponse("Mode d'animation VU-meter changé vers le dual : en-haut et en-bas vers le centre")
                        if (vuMeterMode != VUMeterMode.OFF) {
                            stopVuMeterAnimation()
                            startVuMeterAnimation()
                        }
                        updateAnimationModeButtons()
                    }
                }
            }
        })
        
        // Afficher le drawer comme overlay dans le container dédié
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, 0)
            .add(R.id.drawer_container, drawerFragment, "kitt_drawer")
            .commit()
    }
    
    
    private fun stopAllAnimations() {
        stopScannerAnimation()
        stopVuMeterAnimation()
        stopSystemVolumeAnimation()
        statusMessageHandler?.let { mainHandler.removeCallbacks(it) }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ⭐ KITT ACTION CALLBACK IMPLEMENTATION - Function Calling
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Contrôle App - Arcade
     */
    override fun onOpenArcade() {
        android.util.Log.i("KittFragment", "🎮 Action: Opening Arcade")
        try {
            val intent = android.content.Intent(requireContext(), com.chatai.GameListActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening Arcade", e)
        }
    }
    
    /**
     * Contrôle App - Music
     */
    override fun onOpenMusic() {
        android.util.Log.i("KittFragment", "🎵 Action: Opening Music")
        try {
            // Pour l'instant, juste toggle la musique
            toggleMusic()
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening Music", e)
        }
    }
    
    /**
     * Contrôle App - Configuration IA
     */
    override fun onOpenConfig() {
        android.util.Log.i("KittFragment", "⚙️ Action: Opening AI Config")
        try {
            val intent = android.content.Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening AI Config", e)
        }
    }
    
    /**
     * Contrôle App - Historique
     */
    override fun onOpenHistory() {
        android.util.Log.i("KittFragment", "📜 Action: Opening History")
        try {
            val intent = android.content.Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening History", e)
        }
    }
    
    /**
     * Contrôle App - Configuration Serveur
     */
    override fun onOpenServerConfig() {
        android.util.Log.i("KittFragment", "🌐 Action: Opening Server Config")
        try {
            val intent = android.content.Intent(requireContext(), com.chatai.activities.ServerConfigurationActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening Server Config", e)
        }
    }
    
    /**
     * Contrôle App - Ouvrir ChatAI (retour à WebView)
     */
    override fun onOpenChatAI() {
        android.util.Log.i("KittFragment", "💬 Action: Opening ChatAI")
        try {
            // Retour à l'interface WebView (ChatAI)
            kittFragmentListener?.hideKittInterface()
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening ChatAI", e)
        }
    }
    
    /**
     * Contrôle App - Ouvrir Interface KITT
     */
    override fun onOpenKittInterface() {
        android.util.Log.i("KittFragment", "🚗 Action: Opening KITT Interface")
        try {
            // Si on est déjà dans KITT, juste afficher un message
            showStatusMessage("Interface KITT déjà active, Michael.", 2000, MessageType.STATUS)
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error handling KITT interface", e)
        }
    }
    
    /**
     * Meta-Control - Redémarrer KITT
     */
    override fun onRestartKitt() {
        android.util.Log.i("KittFragment", "🔄 Action: Restart KITT")
        try {
            // Désactiver puis réactiver KITT
            if (isKittActive) {
                executeKittCommand("DEACTIVATE_KITT")
                
                // Attendre 500ms puis réactiver
                mainHandler.postDelayed({
                    executeKittCommand("ACTIVATE_KITT")
                    showStatusMessage("Systèmes redémarrés - KITT opérationnel", 2000, MessageType.STATUS)
                }, 500)
            } else {
                // Si KITT n'était pas actif, l'activer
                executeKittCommand("ACTIVATE_KITT")
            }
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error restarting KITT", e)
        }
    }
    
    /**
     * Contrôle Système - WiFi
     */
    override fun onSetWiFi(enable: Boolean) {
        android.util.Log.i("KittFragment", "📡 Action: Set WiFi = $enable")
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
            // Note: On ne peut plus activer/désactiver le WiFi directement depuis Android 10+
            // On ouvre les paramètres WiFi à la place
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening WiFi settings", e)
        }
    }
    
    /**
     * Contrôle Système - Volume
     */
    override fun onSetVolume(level: Int) {
        android.util.Log.i("KittFragment", "🔊 Action: Set Volume = $level%")
        try {
            val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val targetVol = (maxVol * level / 100).toInt()
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                targetVol,
                android.media.AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error setting volume", e)
        }
    }
    
    /**
     * Contrôle Système - Paramètres Système
     */
    override fun onOpenSystemSettings(setting: String) {
        android.util.Log.i("KittFragment", "⚙️ Action: Opening System Settings: $setting")
        try {
            val intent = when (setting.lowercase()) {
                "wifi" -> android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                "bluetooth" -> android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                "display" -> android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                "sound" -> android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                else -> android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error opening system settings", e)
        }
    }
    
    /**
     * Meta-Control - Changement de Modèle
     */
    override fun onChangeModel(model: String) {
        android.util.Log.i("KittFragment", "🤖 Action: Change Model = $model")
        try {
            // Mettre à jour les SharedPreferences
            sharedPrefs.edit()
                .putString("local_model_name", model)
                .apply()
            android.util.Log.i("KittFragment", "✅ Model changed to: $model")
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error changing model", e)
        }
    }
    
    /**
     * Meta-Control - Changement de Mode (PC/Cloud/Auto)
     */
    override fun onChangeMode(mode: String) {
        android.util.Log.i("KittFragment", "☁️ Action: Change Mode = $mode")
        try {
            val forcedMode = when (mode.lowercase()) {
                "pc" -> "pc_only"
                "cloud" -> "cloud_only"
                "auto" -> "auto"
                else -> "auto"
            }
            sharedPrefs.edit()
                .putString("forced_api_mode", forcedMode)
                .apply()
            android.util.Log.i("KittFragment", "✅ Mode changed to: $forcedMode")
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error changing mode", e)
        }
    }
    
    /**
     * Meta-Control - Changement de Personnalité
     */
    override fun onChangePersonality(personality: String) {
        android.util.Log.i("KittFragment", "🎭 Action: Change Personality = $personality")
        try {
            sharedPrefs.edit()
                .putString("ai_personality", personality)
                .apply()
            android.util.Log.i("KittFragment", "✅ Personality changed to: $personality")
            
            // Recréer le KittAIService avec la nouvelle personnalité
            kittAIService = KittAIService(requireContext(), personality, "vocal", actionCallback = this)
        } catch (e: Exception) {
            android.util.Log.e("KittFragment", "Error changing personality", e)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // FIN DES CALLBACKS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Arrêter l'écoute si elle était active
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
        
        // Arrêter la synthèse vocale
        textToSpeech?.stop()
        
        // Détruire complètement l'interface vocale seulement à la destruction
        speechRecognizer?.destroy()
        speechRecognizer = null
        vuMeterRecognizer?.destroy()
        vuMeterRecognizer = null
        textToSpeech?.shutdown()
        textToSpeech = null
        
        // Arrêter et libérer la musique
        if (isMusicPlaying) {
            stopMusic()
        }
        mediaPlayer?.release()
        mediaPlayer = null
        
        // Arrêter toutes les animations
        stopScannerAnimation()
        stopVuMeterAnimation()
        stopSystemVolumeAnimation()
        
        // Annuler toutes les coroutines
        coroutineScope.cancel()
        
        stopAllAnimations()
    }
    
    /**
     * Gère le changement de thème
     */
    private fun changeTheme(theme: String) {
        when (theme) {
            "red" -> {
                // Thème rouge par défaut - déjà appliqué
                statusText.text = "THÈME ROUGE ACTIVÉ"
                applyRedTheme()
            }
            "dark" -> {
                // Thème sombre - blanc/noir/gris
                statusText.text = "THÈME SOMBRE ACTIVÉ"
                applyDarkTheme()
            }
            "amber" -> {
                // Thème ambre - basé sur la couleur warning du VU
                statusText.text = "THÈME AMBRE ACTIVÉ"
                applyAmberTheme()
            }
        }
        
        // Redémarrer l'activité pour appliquer le nouveau thème
        activity?.recreate()
    }
    
    private fun applyRedTheme() {
        // Appliquer le thème rouge (thème par défaut KITT)
        view?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        switchStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        
        // Appliquer aux cartes
        val statusCard = view?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.statusCard)
        statusCard?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.kitt_black))
        statusCard?.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        
        // Appliquer aux boutons
        val sendButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.sendButton)
        sendButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        sendButton?.setStrokeColorResource(R.color.kitt_red)
        
        val menuButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.menuDrawerButton)
        menuButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        menuButton?.setStrokeColorResource(R.color.kitt_red)
        
        val vuModeButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.vuModeButton)
        vuModeButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        vuModeButton?.setStrokeColorResource(R.color.kitt_red)
        
        // Ajouter les boutons manquants
        val aiButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.aiButton)
        aiButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        aiButton?.setStrokeColorResource(R.color.kitt_red)
        
        val thinkButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.thinkButton)
        thinkButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        thinkButton?.setStrokeColorResource(R.color.kitt_red)
        
        val resetButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.resetButton)
        resetButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        resetButton?.setStrokeColorResource(R.color.kitt_red)
        
        // Appliquer au power switch container (même style que les autres containers)
        val powerSwitchContainer = view?.findViewById<LinearLayout>(R.id.powerSwitchContainer)
        powerSwitchContainer?.setBackgroundResource(R.drawable.kitt_switch_background_red)
        
        // Appliquer aux éléments internes du power switch
        val powerLabel = powerSwitchContainer?.getChildAt(0) as? TextView
        powerLabel?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        
        val switchStatus = view?.findViewById<TextView>(R.id.switchStatus)
        switchStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.kitt_red))
        
        // Mettre à jour le thème du drawer aussi
        val drawerFragment = parentFragmentManager.findFragmentByTag("kitt_drawer") as? KittDrawerFragment
        drawerFragment?.refreshTheme()
        
        // Synchroniser l'état des boutons d'animation avec l'état réel
        updateAnimationModeButtons()
    }
    
    private fun applyDarkTheme() {
        // Appliquer le thème sombre
        view?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_black))
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        switchStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        
        // Appliquer aux cartes
        val statusCard = view?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.statusCard)
        statusCard?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.dark_gray_dark))
        statusCard?.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.dark_gray_light))
        
        // Appliquer aux boutons
        val sendButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.sendButton)
        sendButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        sendButton?.setStrokeColorResource(R.color.dark_gray_light)
        
        val menuButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.menuDrawerButton)
        menuButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        menuButton?.setStrokeColorResource(R.color.dark_gray_light)
        
        val vuModeButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.vuModeButton)
        vuModeButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        vuModeButton?.setStrokeColorResource(R.color.dark_gray_light)
        
        // Ajouter les boutons manquants
        val aiButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.aiButton)
        aiButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        aiButton?.setStrokeColorResource(R.color.dark_gray_light)
        
        val thinkButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.thinkButton)
        thinkButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        thinkButton?.setStrokeColorResource(R.color.dark_gray_light)
        
        val resetButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.resetButton)
        resetButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        resetButton?.setStrokeColorResource(R.color.dark_gray_light)
        
        // Appliquer au power switch container (même style que les autres containers)
        val powerSwitchContainer = view?.findViewById<LinearLayout>(R.id.powerSwitchContainer)
        powerSwitchContainer?.setBackgroundResource(R.drawable.kitt_switch_background_dark)
        
        // Appliquer aux éléments internes du power switch
        val powerLabel = powerSwitchContainer?.getChildAt(0) as? TextView
        powerLabel?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        
        val switchStatus = view?.findViewById<TextView>(R.id.switchStatus)
        switchStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.dark_white))
        
        // Mettre à jour le thème du drawer aussi
        val drawerFragment = parentFragmentManager.findFragmentByTag("kitt_drawer") as? KittDrawerFragment
        drawerFragment?.refreshTheme()
        
        // Synchroniser l'état des boutons d'animation avec l'état réel
        updateAnimationModeButtons()
    }
    
    private fun applyAmberTheme() {
        // Appliquer le thème ambre
        view?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.amber_surface))
        statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_on_surface))
        switchStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_on_surface))
        
        // Appliquer aux cartes
        val statusCard = view?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.statusCard)
        statusCard?.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.amber_surface_variant))
        statusCard?.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        
        // Appliquer aux boutons
        val sendButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.sendButton)
        sendButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        sendButton?.setStrokeColorResource(R.color.amber_primary)
        
        val menuButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.menuDrawerButton)
        menuButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        menuButton?.setStrokeColorResource(R.color.amber_primary)
        
        val vuModeButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.vuModeButton)
        vuModeButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        vuModeButton?.setStrokeColorResource(R.color.amber_primary)
        
        // Ajouter les boutons manquants
        val aiButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.aiButton)
        aiButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        aiButton?.setStrokeColorResource(R.color.amber_primary)
        
        val thinkButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.thinkButton)
        thinkButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        thinkButton?.setStrokeColorResource(R.color.amber_primary)
        
        val resetButton = view?.findViewById<com.google.android.material.button.MaterialButton>(R.id.resetButton)
        resetButton?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        resetButton?.setStrokeColorResource(R.color.amber_primary)
        
        // Appliquer au power switch container (même style que les autres containers)
        val powerSwitchContainer = view?.findViewById<LinearLayout>(R.id.powerSwitchContainer)
        powerSwitchContainer?.setBackgroundResource(R.drawable.kitt_switch_background_amber)
        
        // Appliquer aux éléments internes du power switch
        val powerLabel = powerSwitchContainer?.getChildAt(0) as? TextView
        powerLabel?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        
        val switchStatus = view?.findViewById<TextView>(R.id.switchStatus)
        switchStatus?.setTextColor(ContextCompat.getColor(requireContext(), R.color.amber_primary))
        
        // Mettre à jour le thème du drawer aussi
        val drawerFragment = parentFragmentManager.findFragmentByTag("kitt_drawer") as? KittDrawerFragment
        drawerFragment?.refreshTheme()
        
        // Synchroniser l'état des boutons d'animation avec l'état réel
        updateAnimationModeButtons()
    }
    
    private fun applySelectedTheme() {
        val sharedPreferences = requireContext().getSharedPreferences("kitt_prefs", Context.MODE_PRIVATE)
        val selectedTheme = sharedPreferences.getString("kitt_theme", "red") ?: "red"
        
        when (selectedTheme) {
            "red" -> applyRedTheme()
            "dark" -> applyDarkTheme()
            "amber" -> applyAmberTheme()
        }
    }
}
