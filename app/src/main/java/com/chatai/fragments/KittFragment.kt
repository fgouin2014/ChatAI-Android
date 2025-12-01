package com.chatai.fragments

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.chatai.R
import com.chatai.MainActivity
import com.chatai.viewmodels.KittViewModel
import com.chatai.services.KittAIService
import com.chatai.services.KittActionCallback
import com.chatai.managers.*
import com.chatai.SecureConfig
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 🚗 KITT FRAGMENT V3 - ARCHITECTURE MODULAIRE
 * 
 * ⚠️⚠️⚠️ REFACTORISATION DE V1 - COMPORTEMENT 100% IDENTIQUE ⚠️⚠️⚠️
 * 
 * Ce Fragment est maintenant un COORDINATEUR LÉGER qui délègue
 * toutes les responsabilités aux 7 managers spécialisés:
 * 
 * ARCHITECTURE:
 * KittFragment (~500 lignes - Coordinateur)
 * ├── KittAnimationManager     (~1000 lignes) - Scanner, VU-meter, Thinking
 * ├── KittTTSManager            (~400 lignes) - Text-to-Speech complet
 * ├── KittVoiceManager          (~350 lignes) - SpeechRecognizer × 2
 * ├── KittMessageQueueManager   (~350 lignes) - Priority queue, Marquee
 * ├── KittMusicManager          (~300 lignes) - MediaPlayer
 * ├── KittStateManager          (~300 lignes) - 6 états système
 * └── KittDrawerManager         (~300 lignes) - Menu drawer
 * 
 * AVANTAGES:
 * - ✅ Séparation des responsabilités
 * - ✅ Code maintenable (~500 lignes vs 3434)
 * - ✅ Tests unitaires possibles
 * - ✅ Réutilisabilité des managers
 * - ✅ COMPORTEMENT IDENTIQUE À V1
 */
class KittFragment : Fragment(),
    KittTTSManager.TTSListener,
    KittVoiceManager.VoiceRecognitionListener,
    KittMusicManager.MusicListener,
    KittDrawerManager.DrawerListener,
    KittActionCallback {
    
    companion object {
        private const val TAG = "KittFragment"
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MANAGERS (Délégation des responsabilités)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private lateinit var animationManager: KittAnimationManager
    private lateinit var ttsManager: KittTTSManager
    private lateinit var voiceManager: KittVoiceManager
    private lateinit var messageQueueManager: KittMessageQueueManager
    private lateinit var musicManager: KittMusicManager
    private lateinit var stateManager: KittStateManager
    private lateinit var drawerManager: KittDrawerManager
    
    // SecureConfig pour stockage sécurisé des clés API
    private val secureConfig: SecureConfig by lazy { SecureConfig(requireContext()) }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UI & VIEWMODEL (Seulement les références, pas de logique)
    // ═══════════════════════════════════════════════════════════════════════════════

    private lateinit var viewModel: KittViewModel
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    // Views principales
    private lateinit var statusText: TextView
    private lateinit var thinkingCard: com.google.android.material.card.MaterialCardView
    private lateinit var thinkingText: TextView
    private lateinit var powerSwitch: MaterialSwitch
    private lateinit var switchStatus: TextView
    private lateinit var statusBarIndicatorBSY: MaterialTextView
    private lateinit var statusBarIndicatorRDY: MaterialTextView
    private lateinit var statusBarIndicatorNET: MaterialTextView
    private lateinit var statusBarIndicatorMSQ: MaterialTextView
    private lateinit var scannerRow: LinearLayout
    private lateinit var leftVuBar: LinearLayout
    private lateinit var centerVuBar: LinearLayout
    private lateinit var rightVuBar: LinearLayout
    private lateinit var textInput: TextInputEditText

    // Boutons
    private lateinit var aiButton: MaterialButton
    private lateinit var thinkButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var sendButton: MaterialButton
    private lateinit var vuModeButton: MaterialButton
    private lateinit var menuDrawerButton: MaterialButton
    
    // Autres
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var kittAIService: KittAIService
    
    // Flags
    private var hasActivationMessageBeenSpoken = false
    
    // ⭐ NOUVEAU : Bridge listener pour messages Chat → KITT
    private var bridgeListenerJob: Job? = null
    private var thinkingStreamJob: Job? = null
    private val pendingMessages = mutableListOf<com.chatai.services.BidirectionalBridge.BridgeMessage>()
    
    // Listener pour MainActivity
    interface KittFragmentListener {
        fun hideKittInterface()
    }

    private var kittFragmentListener: KittFragmentListener? = null

    fun setKittFragmentListener(listener: KittFragmentListener?) {
        this.kittFragmentListener = listener
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceManager.setupVoiceInterface()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kitt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        android.util.Log.i(TAG, "🚗 KITT Fragment V3 (Architecture Modulaire) - Initialisation")
        
        // Initialiser ViewModel
        viewModel = ViewModelProvider(this)[KittViewModel::class.java]

        // Initialiser SharedPreferences
        sharedPrefs = requireContext().getSharedPreferences("kitt_usage", Context.MODE_PRIVATE)

        // Initialiser AudioManager
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Initialiser views
        initializeViews(view)
        
        // ⭐⭐⭐ INITIALISER TOUS LES MANAGERS
        initializeManagers()
        
        // Initialiser KittAIService
        val aiConfigPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
        val selectedPersonality = aiConfigPrefs.getString("selected_personality", "KITT") ?: "KITT"
        kittAIService = KittAIService(requireContext(), selectedPersonality, platform = "vocal", actionCallback = this)
        android.util.Log.i(TAG, "KittAIService initialisé avec personnalité: $selectedPersonality")
        
        // Setup UI
        setupScanner()
        setupVuMeter()
        setupListeners()
        setupObservers()
        
        // ⭐ NOUVEAU : Initialiser l'écoute des messages Chat → KITT
        setupBridgeListener()

        // Initialiser TTS
        ttsManager.initialize()
        
        // Initialiser musique
        musicManager.initialize()

        // Appliquer thème sauvegardé
        val theme = drawerManager.applySelectedTheme()
        applyTheme(theme)

        // Initialiser en mode standby
        setStandbyMode()
        
        android.util.Log.i(TAG, "✅ KITT Fragment V3 initialisé avec 7 managers")
    }
    
    /**
     * ⭐⭐⭐ INITIALISER TOUS LES 7 MANAGERS
     */
    private fun initializeManagers() {
        // 1. Animation Manager
        animationManager = KittAnimationManager(requireContext(), resources)
        animationManager.maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        
        // 2. TTS Manager
        ttsManager = KittTTSManager(requireContext(), this)
        
        // 3. Voice Manager
        voiceManager = KittVoiceManager(requireContext(), this)
        
        // 4. Message Queue Manager
        messageQueueManager = KittMessageQueueManager(statusText) {
            // Callback quand queue vide
            showDefaultStatus()
        }
        
        // 5. Music Manager
        musicManager = KittMusicManager(requireContext(), this)
        
        // 6. State Manager
        stateManager = KittStateManager(requireContext())
        
        // 7. Drawer Manager
        drawerManager = KittDrawerManager(requireContext(), this)
        
        android.util.Log.i(TAG, "✅ 7 managers initialized")
    }

    private fun initializeViews(view: View) {
        statusText = view.findViewById(R.id.statusText)
        thinkingCard = view.findViewById(R.id.thinkingCard)
        thinkingText = view.findViewById(R.id.thinkingText)
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

        // Configurer marquee
        setupMarqueeScrolling()
    }

    private fun setupMarqueeScrolling() {
        statusText.apply {
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1  // Répéter indéfiniment
            isSingleLine = true
            isSelected = false
        }
    }

    private fun setupScanner() {
        // Déléguer au AnimationManager
        animationManager.setupScanner(scannerRow)
    }

    private fun setupVuMeter() {
        // Déléguer au AnimationManager
        animationManager.setupVuMeter(leftVuBar, centerVuBar, rightVuBar)
        animationManager.setupThinkingIndicators(statusBarIndicatorBSY, statusBarIndicatorRDY, statusBarIndicatorNET)
        
        // Initialiser texte bouton VU-mode
        vuModeButton.text = when (animationManager.vuMeterMode) {
            VUMeterMode.VOICE -> "VU-VOIX"
            VUMeterMode.AMBIENT -> "VU-AMBI"
            VUMeterMode.OFF -> "VU-OFF"
        }
    }
    
    private fun setupListeners() {
        // Power Switch
        powerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startKittScanAnimation()
            } else {
                setStandbyMode()
            }
        }
        
        // AI Button
        aiButton.setOnClickListener {
            toggleAIMode()
        }
        
        // Think Button
        thinkButton.setOnClickListener {
            if (stateManager.isReady) {
                simulateThinking()
            }
        }
        
        // Reset Button
        resetButton.setOnClickListener {
            resetInterface()
        }
        
        // VU-Mode Button
        vuModeButton.setOnClickListener {
            toggleVUMeterMode()
        }
        
        // Menu Drawer Button
        menuDrawerButton.setOnClickListener {
            showMenuDrawer()
        }
        
        // Send Button
        sendButton.setOnClickListener {
            processText()
        }
        
        // Text Input (Enter key)
        textInput.setOnEditorActionListener { _, _, _ ->
            processText()
            true
        }
        
        // Thinking Trace Toggle (Debug Mode)
        setupThinkingTraceToggle()
        
        // Status Indicators cliquables
        statusBarIndicatorRDY.setOnClickListener {
            try {
                val intent = Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                startActivity(intent)
                ttsManager.speakAIResponse("Historique des conversations")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erreur ouverture historique: ${e.message}")
            }
        }
        
        statusBarIndicatorBSY.setOnClickListener {
            try {
                val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                startActivity(intent)
                ttsManager.speakAIResponse("Configuration IA")
        } catch (e: Exception) {
                android.util.Log.e(TAG, "Erreur ouverture config: ${e.message}")
            }
        }
        
        statusBarIndicatorNET.setOnClickListener {
            android.util.Log.d(TAG, "🌐 NET clicked - isReady=${stateManager.isReady}, isBusy=${stateManager.isBusy()}")
            
            if (stateManager.isReady && !stateManager.isBusy()) {
                try {
                    ttsManager.speakAIResponse("Test de connectivité réseau")
                    testNetworkAPIs()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌ Erreur NET click: ${e.message}", e)
                    showStatusMessageInternal("Erreur test réseau", 2000, MessageType.ERROR)
                }
            } else {
                android.util.Log.w(TAG, "⚠️ NET click ignored - KITT busy or not ready")
            }
        }
        
        statusBarIndicatorMSQ.setOnClickListener {
            musicManager.toggleMusic()
        }
    }
    
    private fun setupObservers() {
        // Observers ViewModel (si nécessaire)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MODES (Délégation aux managers)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun setReadyMode() {
        android.util.Log.d(TAG, "🚗 setReadyMode() called")
        
        stateManager.setReadyMode()
        powerSwitch.isChecked = true
        
        android.util.Log.d(TAG, "✅ StateManager.isReady = ${stateManager.isReady}")
        
        if (isAdded) {
            switchStatus.text = getString(R.string.kitt_status_rdy)
            statusText.text = getString(R.string.kitt_status_ready)
        }
        
        // Mettre à jour voyants
        updateStatusIndicators()
        
        // Activer interface vocale
        android.util.Log.d(TAG, "🎤 Checking microphone permission...")
        checkMicrophonePermission()
        
        // Démarrer scanner
        animationManager.startScannerAnimation(120)
        
        // Mettre à jour boutons
        updateButtonStates()
        setButtonsState(true)
        
        // Message d'activation (une fois par session)
        if (!hasActivationMessageBeenSpoken) {
            speakKittActivationMessage()
            hasActivationMessageBeenSpoken = true
        } else {
            simulateSpeaking()
        }
        
        android.util.Log.d(TAG, "✅ Ready mode complete - isReady=${stateManager.isReady}")
    }
    
    private fun setStandbyMode() {
        stateManager.setStandbyMode()
        powerSwitch.isChecked = false
        powerSwitch.isEnabled = true
        
        // Vider la queue
        messageQueueManager.clearMessageQueue()
        
        if (isAdded) {
            switchStatus.text = getString(R.string.kitt_status_stby)
            statusText.text = getString(R.string.kitt_status_standby)
        }
        
        // Mettre à jour voyants
        updateStatusIndicators()
        
        // Arrêter interface vocale
        voiceManager.stopVoiceInterface()
        
        // Arrêter animations
        stopAllAnimations()
        animationManager.resetScanner()
        animationManager.resetVuMeter()
        
        // Arrêter musique
        if (stateManager.isMusicPlaying) {
            musicManager.stopMusic()
        }
        
        // Mettre à jour boutons
        updateButtonStates()
        setButtonsState(false)
    }
    
    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                android.util.Log.d(TAG, "✅ Microphone permission granted - Setting up voice interface")
                voiceManager.setupVoiceInterface()
                android.util.Log.d(TAG, "✅ Voice interface setup complete")
            }
            else -> {
                android.util.Log.w(TAG, "⚠️ Requesting microphone permission")
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ANIMATIONS (Délégation à AnimationManager)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun startKittScanAnimation() {
        powerSwitch.isEnabled = false
        
        val scanLineView = view?.findViewById<View>(R.id.scanLineView)
        scanLineView?.visibility = View.VISIBLE
        scanLineView?.alpha = 1f
        
        val scanAnimation = android.view.animation.AnimationUtils.loadAnimation(requireContext(), R.anim.kitt_scan_horizontal)
        scanAnimation?.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
            override fun onAnimationStart(animation: android.view.animation.Animation?) {}
            override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                scanLineView?.visibility = View.GONE
                scanLineView?.alpha = 0f
                
                mainHandler.postDelayed({
                    setReadyMode()
                    powerSwitch.isEnabled = true
                }, 1200)
            }
            override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
        })
        scanLineView?.startAnimation(scanAnimation)
        
        // Animation boutons
        startSmoothButtonAnimation()
    }
    
    private fun startSmoothButtonAnimation() {
        val buttons = listOf(sendButton, menuDrawerButton, vuModeButton, aiButton, thinkButton, resetButton)
        val indicators = listOf(statusBarIndicatorBSY, statusBarIndicatorRDY, statusBarIndicatorNET, statusBarIndicatorMSQ)
        
        animationManager.startSmoothButtonAnimation(buttons, indicators)
    }
    
    private fun stopAllAnimations() {
        animationManager.stopAll()
        messageQueueManager.clearMessageQueue()
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VOICE & TEXT INPUT (Délégation à VoiceManager)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun toggleAIMode() {
        android.util.Log.d(TAG, "🎤 toggleAIMode() called - isReady=${stateManager.isReady}, isListening=${stateManager.isListening}, isChatMode=${stateManager.isChatMode}")
        
        if (!stateManager.isReady) {
            android.util.Log.w(TAG, "❌ Cannot toggle AI mode - KITT not ready")
            return
        }
        
        if (stateManager.isListening && stateManager.isChatMode) {
            android.util.Log.d(TAG, "Stopping AI mode")
            stopAIMode()
        } else {
            android.util.Log.d(TAG, "Starting AI mode")
            startAIMode()
        }
    }
    
    private fun startAIMode() {
        android.util.Log.d(TAG, "🎤 startAIMode() - isReady=${stateManager.isReady}")
        
        if (!stateManager.isReady) {
            android.util.Log.w(TAG, "❌ Cannot start AI mode - KITT not ready")
            return
        }
        
        stateManager.isListening = true
        stateManager.isChatMode = true
        aiButton.text = "ARRÊTER AI"
        aiButton.isEnabled = true
        
        updateStatusIndicators()
        
        android.util.Log.d(TAG, "🎤 Calling voiceManager.startVoiceRecognition()...")
        voiceManager.startVoiceRecognition()
        android.util.Log.d(TAG, "🎤 Voice recognition started")
        
        showStatusMessageInternal("Mode AI activé - Parlez", 2000, MessageType.COMMAND)
    }
    
    private fun stopAIMode() {
        if (stateManager.isListening) {
            voiceManager.stopVoiceRecognition()
            stateManager.isListening = false
            stateManager.isChatMode = false
            aiButton.text = "AI"
            aiButton.isEnabled = true
            
            updateStatusIndicators()
            showStatusMessageInternal("Mode AI désactivé", 2000, MessageType.STATUS)
        }
    }
    
    private fun processText() {
        if (!stateManager.isReady) return
        
        val text = textInput.text?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        
        showStatusMessageInternal("Vous: '$text'", 3000, MessageType.VOICE)
        textInput.text?.clear()
        
        mainHandler.postDelayed({
            processAIConversationInternal(text)
        }, 500)
    }
    
    private fun processAIConversationInternal(userMessage: String) {
        if (!stateManager.isReady) return
        
        coroutineScope.launch {
            try {
                // Démarrer thinking animation
                animationManager.startThinkingAnimation()
                stateManager.isThinking = true
                
                val response = kittAIService.processUserInput(userMessage)
                
                // Arrêter thinking animation
                animationManager.stopThinkingAnimation { updateStatusIndicators() }
                stateManager.isThinking = false
                
                // Afficher thinking trace si debug mode activé
                displayThinkingTraceIfEnabled()
                
                // Parler réponse
                ttsManager.speakAIResponse(response)
                
                android.util.Log.d(TAG, "AI conversation - Input: '$userMessage' → Response: '$response'")
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in AI conversation", e)
                animationManager.stopThinkingAnimation { updateStatusIndicators() }
                stateManager.isThinking = false
                ttsManager.speakAIResponse("Michael, je rencontre un dysfonctionnement temporaire.")
            }
        }
    }
    
    private fun simulateThinking() {
        if (!stateManager.isReady) return
        
        stateManager.isThinking = true
        stateManager.isSpeaking = true
        updateStatusIndicators()

        animationManager.stopScannerAnimation()
        animationManager.startThinkingAnimation()
        
        showStatusMessageInternal("Analyse en cours...", 3000, MessageType.STATUS)
        
        mainHandler.postDelayed({
            if (isAdded) {
                animationManager.stopThinkingAnimation { updateStatusIndicators() }
                stateManager.isThinking = false
                
                mainHandler.postDelayed({
            simulateSpeaking()
                }, 1000)
            }
        }, 3000)
    }
    
    private fun simulateSpeaking() {
        if (!stateManager.isReady) return
        
        stateManager.isSpeaking = true
        if (isAdded) {
            showStatusMessageInternal(getString(R.string.kitt_status_speaking), 0)
        }
        
        animationManager.startVuMeterAnimation()
        
        mainHandler.postDelayed({
            if (isAdded) {
                stateManager.isSpeaking = false
                updateStatusIndicators()
                showStatusMessageInternal(getString(R.string.kitt_message_communication_complete), 4000)
                animationManager.stopVuMeterAnimation()
                
                if (!stateManager.isThinking) {
                    animationManager.startScannerAnimation(120)
                }
            }
        }, 4000)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // TTS CALLBACKS (Interface KittTTSManager.TTSListener)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onTTSReady() {
        // TTS prêt - sélectionner voix
        val personality = drawerManager.getCurrentPersonality()
        ttsManager.selectVoiceForPersonality(personality)
    }
    
    override fun onTTSStart(utteranceId: String?) {
        stateManager.isTTSSpeaking = true
        animationManager.isTTSSpeaking = true
            updateStatusIndicators()
        animationManager.startSystemVolumeAnimation()
    }
    
    override fun onTTSDone(utteranceId: String?) {
        stateManager.isTTSSpeaking = false
        animationManager.isTTSSpeaking = false
        updateStatusIndicators()
        animationManager.stopSystemVolumeAnimation()
        animationManager.resetVuMeterToBase()
        
        if (utteranceId == "kitt_activation") {
            stateManager.isSpeaking = false
            mainHandler.postDelayed({
                if (isAdded) {
                    showStatusMessageInternal("KITT prêt - En attente de vos instructions", 3000)
                    animationManager.stopVuMeterAnimation()
                    
                    if (!stateManager.isThinking) {
                        animationManager.startScannerAnimation(120)
                    }
                }
            }, 500)
        }
    }
    
    override fun onTTSError(utteranceId: String?) {
        stateManager.isTTSSpeaking = false
        animationManager.isTTSSpeaking = false
        animationManager.stopSystemVolumeAnimation()
        animationManager.resetVuMeterToBase()
    }
    
    private fun speakKittActivationMessage() {
        if (!stateManager.isReady) return

        if (ttsManager.isReady() && !ttsManager.isSpeaking()) {
            try {
        val activationMessage = "Bonjour, je suis KITT. En quoi puis-je vous aider ?"

        if (isAdded) {
                    showStatusMessageInternal(activationMessage, 0)
                }
                
                ttsManager.speak(activationMessage, "kitt_activation")
                stateManager.isSpeaking = true
                
                // Fallback si TTS ne fonctionne pas
                mainHandler.postDelayed({
                    if (isAdded && stateManager.isSpeaking) {
                        stateManager.isSpeaking = false
                        updateStatusIndicators()
                        showStatusMessageInternal("KITT prêt - En attente de vos instructions", 3000)
                        animationManager.stopVuMeterAnimation()

                        if (!stateManager.isThinking) {
                            animationManager.startScannerAnimation(120)
                        }
                    }
                }, 4000)

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erreur TTS activation: ${e.message}")
                // Fallback visuel
                stateManager.isSpeaking = true
                animationManager.startVuMeterAnimation()

                mainHandler.postDelayed({
                    if (isAdded) {
                        stateManager.isSpeaking = false
                        updateStatusIndicators()
                        showStatusMessageInternal("KITT prêt - En attente de vos instructions", 3000)
                        animationManager.stopVuMeterAnimation()

                        if (!stateManager.isThinking) {
                            animationManager.startScannerAnimation(120)
                        }
                    }
                }, 4000)
            }
        } else {
            // TTS pas prêt - simulation visuelle
            android.util.Log.d(TAG, "TTS pas encore prêt, simulation visuelle")
            stateManager.isSpeaking = true
            animationManager.startVuMeterAnimation()

            mainHandler.postDelayed({
                if (isAdded) {
                    stateManager.isSpeaking = false
                    updateStatusIndicators()
                    showStatusMessageInternal("KITT prêt - En attente de vos instructions", 3000)
                    animationManager.stopVuMeterAnimation()
                    
                    if (!stateManager.isThinking) {
                        animationManager.startScannerAnimation(120)
                    }
                }
            }, 3000)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VOICE CALLBACKS (Interface KittVoiceManager.VoiceRecognitionListener)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onVoiceRecognitionReady() {
        // Prêt pour reconnaissance
    }
    
    override fun onVoiceRecognitionStart() {
        // Début reconnaissance
    }
    
    override fun onVoiceRecognitionResults(command: String) {
        showStatusMessageInternal("Reconnu: '$command'", 2000, MessageType.VOICE)
        processVoiceCommand(command)
        
        stateManager.isListening = false
        aiButton.text = "AI"
        aiButton.isEnabled = true
    }
    
    override fun onVoiceRecognitionError(errorCode: Int) {
        stateManager.isListening = false
        aiButton.text = "AI"
        aiButton.isEnabled = true
        // Pas d'affichage statut pour erreurs
    }
    
    override fun onVoiceRmsChanged(rmsdB: Float) {
        animationManager.currentMicrophoneLevel = rmsdB
        
        if (animationManager.vuMeterMode == VUMeterMode.AMBIENT) {
            val normalizedLevel = (rmsdB + 20f) / 20f
            val clampedLevel = normalizedLevel.coerceIn(0f, 1f)
            animationManager.updateVuMeter(clampedLevel)
        }
    }
    
    private fun processVoiceCommand(command: String) {
        if (!stateManager.isReady) return
        
        showStatusMessageInternal("Vous: '$command'", 2000, MessageType.VOICE)
        
        // Commandes spéciales VU-meter (boutons drawer)
        when (command.uppercase().trim()) {
            "ANIMATION_ORIGINAL" -> {
                animationManager.vuAnimationMode = VUAnimationMode.ORIGINAL
                showStatusMessageInternal("Animation VU-meter: ORIGINAL (bas en haut)", 1500, MessageType.ANIMATION)
                ttsManager.speakAIResponse("Mode d'animation VU-meter changé vers l'original : de bas en haut")
                if (animationManager.vuMeterMode != VUMeterMode.OFF) {
                    animationManager.stopVuMeterAnimation()
                    animationManager.startVuMeterAnimation()
                }
                updateAnimationModeButtons()
                return
            }
            "ANIMATION_DUAL" -> {
                animationManager.vuAnimationMode = VUAnimationMode.DUAL
                showStatusMessageInternal("Animation VU-meter: DUAL (haut et bas)", 1500, MessageType.ANIMATION)
                ttsManager.speakAIResponse("Mode d'animation VU-meter changé vers le dual : en-haut et en-bas vers le centre")
                if (animationManager.vuMeterMode != VUMeterMode.OFF) {
                    animationManager.stopVuMeterAnimation()
                    animationManager.startVuMeterAnimation()
                }
                updateAnimationModeButtons()
                return
            }
            "TOGGLE_MUSIC" -> {
                musicManager.toggleMusic()
                return
            }
        }
        
        // Commandes vocales simples
        val lowerCommand = command.lowercase().trim()
        
        // Détecter commandes spéciales
        if (detectSpecialCommand(lowerCommand)) {
            return
        }
        
        // Utiliser le service IA pour le reste
        coroutineScope.launch {
            try {
                showStatusMessageInternal("Traitement en cours...", 1000, MessageType.STATUS)
                
                animationManager.startThinkingAnimation()
                stateManager.isThinking = true
                
                val response = kittAIService.processUserInput(command)
                
                animationManager.stopThinkingAnimation { updateStatusIndicators() }
                stateManager.isThinking = false
                
                // Afficher thinking trace si debug mode activé
                displayThinkingTraceIfEnabled()
                
                showStatusMessageInternal("KITT: '$response'", 4000, MessageType.AI)
                ttsManager.speakAIResponse(response)
                
                android.util.Log.d(TAG, "Input: '$command' → Response: '$response'")
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error processing command", e)
                animationManager.stopThinkingAnimation { updateStatusIndicators() }
                stateManager.isThinking = false
                showStatusMessageInternal("Erreur: '$command'", 3000, MessageType.ERROR)
                ttsManager.speakAIResponse("Michael, je rencontre un dysfonctionnement temporaire.")
            }
        }
    }
    
    private fun detectSpecialCommand(lowerCommand: String): Boolean {
        // Configuration IA
        if ((lowerCommand.contains("ouvre") || lowerCommand.contains("ouvrir") || 
             lowerCommand.contains("menu") || lowerCommand.contains("affiche") ||
             lowerCommand.contains("va à") || lowerCommand.contains("va a")) &&
            (lowerCommand.contains("configuration") || lowerCommand.contains("config") || 
             lowerCommand.contains("réglage") || lowerCommand.contains("paramètre"))) {
            ttsManager.speakAIResponse("Ouverture de la configuration IA")
            try {
                val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erreur ouverture config IA: ${e.message}")
            }
            return true
        }
        
        // Historique
        if ((lowerCommand.contains("ouvre") || lowerCommand.contains("ouvrir") || 
             lowerCommand.contains("menu") || lowerCommand.contains("affiche") ||
             lowerCommand.contains("va à") || lowerCommand.contains("va a") ||
             lowerCommand.contains("montre")) &&
            (lowerCommand.contains("historique") || lowerCommand.contains("conversation"))) {
            ttsManager.speakAIResponse("Ouverture de l'historique des conversations")
            try {
                val intent = Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erreur ouverture historique: ${e.message}")
            }
            return true
        }
        
        // Commandes simples
        when (lowerCommand) {
            "configuration ia", "configuration", "réglages ia", "paramètres ia", "config ia" -> {
                ttsManager.speakAIResponse("Ouverture de la configuration IA")
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Erreur ouverture config IA: ${e.message}")
                }
                return true
            }
            "historique", "historique des conversations", "mes conversations" -> {
                ttsManager.speakAIResponse("Ouverture de l'historique des conversations")
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Erreur ouverture historique: ${e.message}")
                }
                return true
            }
            "test réseau", "test api", "test apis" -> {
                ttsManager.speakAIResponse("Test de connectivité réseau")
                testNetworkAPIs()
                return true
            }
            "musique", "toggle musique", "play musique", "stop musique" -> {
                musicManager.toggleMusic()
                return true
            }
        }
        
        return false
    }
    
    private fun testNetworkAPIs() {
        android.util.Log.d(TAG, "🌐 testNetworkAPIs() called")
        
        coroutineScope.launch {
            try {
                showStatusMessageInternal("Test de connectivité en cours...", 2000, MessageType.STATUS)
                
                val testTimestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                
                // ═══════════════════════════════════════════════════════════
                // EXPORT LOGCAT - DÉBUT
                // ═══════════════════════════════════════════════════════════
                android.util.Log.i("NETWORK_TEST_EXPORT", "═══════════════════════════════════════════════════════════")
                android.util.Log.i("NETWORK_TEST_EXPORT", "🌐 TEST DE CONNECTIVITÉ RÉSEAU")
                android.util.Log.i("NETWORK_TEST_EXPORT", "═══════════════════════════════════════════════════════════")
                android.util.Log.i("NETWORK_TEST_EXPORT", "📅 Date: $testTimestamp")
                android.util.Log.i("NETWORK_TEST_EXPORT", "")
                
                // Lire configuration depuis SecureConfig et SharedPreferences
                val sharedPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
                val cloudApiKey = secureConfig.getOllamaCloudApiKey() ?: ""
                val cloudModel = sharedPrefs.getString("ollama_cloud_model", "") ?: ""
                val localUrl = sharedPrefs.getString("local_server_url", "") ?: ""
                
                android.util.Log.i("NETWORK_TEST_EXPORT", "┌─ CONFIGURATION")
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ ☁️  Cloud API Key: ${if (cloudApiKey.isEmpty()) "❌ Non configurée" else "✅ Configurée (${cloudApiKey.length} chars)"}")
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ 🤖 Cloud Model:   ${if (cloudModel.isEmpty()) "❌ Non configuré" else cloudModel}")
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ 💻 Local URL:     ${if (localUrl.isEmpty()) "❌ Non configurée" else localUrl}")
                android.util.Log.i("NETWORK_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                android.util.Log.i("NETWORK_TEST_EXPORT", "")
                
                // Tester Ollama Cloud
                android.util.Log.i("NETWORK_TEST_EXPORT", "┌─ TEST OLLAMA CLOUD ☁️")
                val (cloudOk, cloudReason) = testOllamaCloud()
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ Résultat: ${if (cloudOk) "✅ SUCCESS" else "❌ FAILED"}")
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ Détails:  $cloudReason")
                android.util.Log.i("NETWORK_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                android.util.Log.i("NETWORK_TEST_EXPORT", "")
                
                // Tester Ollama Local
                android.util.Log.i("NETWORK_TEST_EXPORT", "┌─ TEST OLLAMA LOCAL 💻")
                val (localOk, localReason) = testOllamaLocal()
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ Résultat: ${if (localOk) "✅ SUCCESS" else "❌ FAILED"}")
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ Détails:  $localReason")
                android.util.Log.i("NETWORK_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                android.util.Log.i("NETWORK_TEST_EXPORT", "")
                
                // Résumé final
                android.util.Log.i("NETWORK_TEST_EXPORT", "┌─ RÉSUMÉ")
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ ☁️  Cloud Ollama: ${if (cloudOk) "✅ ACCESSIBLE" else "❌ $cloudReason"}")
                android.util.Log.i("NETWORK_TEST_EXPORT", "│ 💻 Local Ollama: ${if (localOk) "✅ ACCESSIBLE" else "❌ $localReason"}")
                android.util.Log.i("NETWORK_TEST_EXPORT", "└────────────────────────────────────────────────────────")
                android.util.Log.i("NETWORK_TEST_EXPORT", "")
                android.util.Log.i("NETWORK_TEST_EXPORT", "═══════════════════════════════════════════════════════════")
                // ═══════════════════════════════════════════════════════════
                // EXPORT LOGCAT - FIN
                // ═══════════════════════════════════════════════════════════
                
                // Créer résumé détaillé dans marquee
                val summary = buildString {
                    append("Réseau: ")
                    if (cloudOk) append("☁️ Cloud OK, ") else append("☁️ Cloud: $cloudReason, ")
                    if (localOk) append("💻 Local OK") else append("💻 Local: $localReason")
                }
                
                showStatusMessageInternal(summary, 7000, MessageType.STATUS)
                
                // Parler résumé détaillé
                val spokenResult = buildString {
                    if (cloudOk && localOk) {
                        append("Excellent. Cloud Ollama et serveur local sont tous les deux accessibles.")
                    } else if (cloudOk) {
                        append("Cloud Ollama fonctionne. Serveur local: $localReason.")
                    } else if (localOk) {
                        append("Serveur local fonctionne. Cloud Ollama: $cloudReason.")
                    } else {
                        append("Aucune connexion disponible. Cloud: $cloudReason. Serveur local: $localReason.")
                    }
                }
                
                ttsManager.speakAIResponse(spokenResult)
                
                android.util.Log.d(TAG, "🌐 Test complete - Cloud: $cloudOk ($cloudReason), Local: $localOk ($localReason)")
                
                // Afficher dialog avec commande logcat
                mainHandler.post {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.KittDialogTheme)
                        .setTitle("🌐 Test Réseau Terminé")
                        .setMessage("Résultats exportés dans logcat.\n\n☁️  Cloud: ${if (cloudOk) "✅ OK" else "❌ $cloudReason"}\n💻 Local: ${if (localOk) "✅ OK" else "❌ $localReason"}\n\nCommande pour voir détails:\n\nadb logcat -s NETWORK_TEST_EXPORT")
                        .setPositiveButton("OK", null)
                        .show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Erreur test APIs: ${e.message}", e)
                android.util.Log.e("NETWORK_TEST_EXPORT", "❌ ERREUR: ${e.message}", e)
                showStatusMessageInternal("Erreur test réseau: ${e.message}", 3000, MessageType.ERROR)
                ttsManager.speakAIResponse("Erreur lors du test de connectivité.")
            }
        }
    }
    
    private suspend fun testOllamaCloud(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = secureConfig.getOllamaCloudApiKey() ?: ""
            
            if (apiKey.isEmpty()) {
                android.util.Log.d(TAG, "☁️ Ollama Cloud: Pas de clé API")
                return@withContext Pair(false, "Pas de clé API configurée")
            }
            
            val sharedPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
            val model = sharedPrefs.getString("ollama_cloud_model", "gpt-oss:120b") ?: "gpt-oss:120b"
            
            // Test avec une vraie requête de chat pour détecter les erreurs de quota
            val requestBody = org.json.JSONObject().apply {
                put("model", model)
                put("messages", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("role", "user")
                        put("content", "Test")
                    })
                })
                put("stream", false)
            }
            
            val request = okhttp3.Request.Builder()
                .url(com.chatai.config.ApiConfig.OLLAMA_CLOUD_CHAT) // ⭐ REFACTORISÉ: URL centralisée
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val response = client.newCall(request).execute()
            val errorBody = response.body?.string()
            val httpCode = response.code
            
            if (response.isSuccessful) {
                android.util.Log.d(TAG, "☁️ Ollama Cloud test: OK (${response.code})")
                return@withContext Pair(true, "OK - Quota disponible")
            } else {
                // Détection spécifique des erreurs de quota
                val isQuotaError = when (httpCode) {
                    429 -> true  // Too Many Requests
                    502 -> errorBody?.contains("upstream error", ignoreCase = true) == true || 
                           errorBody?.contains("quota", ignoreCase = true) == true ||
                           errorBody?.contains("rate limit", ignoreCase = true) == true
                    503 -> true  // Service Unavailable
                    else -> false
                }
                
                if (isQuotaError) {
                    android.util.Log.w(TAG, "☁️ Ollama Cloud test: QUOTA/RATE LIMIT (HTTP $httpCode)")
                    android.util.Log.w(TAG, "   → Error: $errorBody")
                    return@withContext Pair(false, "Quota atteint ou rate limit (HTTP $httpCode)")
                } else {
                    android.util.Log.d(TAG, "☁️ Ollama Cloud test: ÉCHEC (HTTP $httpCode)")
                    android.util.Log.d(TAG, "   → Error: $errorBody")
                    return@withContext Pair(false, "HTTP $httpCode")
                }
            }
            
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.d(TAG, "☁️ Ollama Cloud test: Pas d'internet")
            return@withContext Pair(false, "Pas d'accès internet")
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.d(TAG, "☁️ Ollama Cloud test: Timeout")
            return@withContext Pair(false, "Timeout (réseau lent)")
        } catch (e: java.net.ConnectException) {
            android.util.Log.d(TAG, "☁️ Ollama Cloud test: Connexion refusée")
            return@withContext Pair(false, "Connexion refusée")
        } catch (e: Exception) {
            android.util.Log.d(TAG, "☁️ Ollama Cloud test: ÉCHEC (${e.message})")
            return@withContext Pair(false, e.message ?: "Erreur inconnue")
        }
    }
    
    private suspend fun testOllamaLocal(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val sharedPrefs = requireContext().getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
            val localServerUrl = sharedPrefs.getString("local_server_url", "") ?: ""
            
            if (localServerUrl.isEmpty()) {
                android.util.Log.d(TAG, "💻 Ollama Local: Pas d'URL configurée")
                return@withContext Pair(false, "Pas configuré")
            }
            
            // Test simple avec GET /api/tags
            val request = okhttp3.Request.Builder()
                .url("$localServerUrl/api/tags")
                .get()
                .build()
            
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                android.util.Log.d(TAG, "💻 Ollama Local test: OK (${response.code})")
                return@withContext Pair(true, "OK")
            } else {
                android.util.Log.d(TAG, "💻 Ollama Local test: ÉCHEC (HTTP ${response.code})")
                return@withContext Pair(false, "HTTP ${response.code}")
            }
            
        } catch (e: java.net.ConnectException) {
            android.util.Log.d(TAG, "💻 Ollama Local test: PC éteint ou inaccessible")
            return@withContext Pair(false, "PC inaccessible")
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.d(TAG, "💻 Ollama Local test: Timeout")
            return@withContext Pair(false, "Timeout")
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.d(TAG, "💻 Ollama Local test: Hôte inconnu")
            return@withContext Pair(false, "Hôte inconnu")
        } catch (e: Exception) {
            android.util.Log.d(TAG, "💻 Ollama Local test: ÉCHEC (${e.message})")
            return@withContext Pair(false, e.message ?: "Erreur inconnue")
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // VU-METER MODES (Délégation à AnimationManager)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun toggleVUMeterMode() {
        val newMode = animationManager.toggleVUMeterMode()
        
        // Mettre à jour texte bouton
        vuModeButton.text = when (newMode) {
            VUMeterMode.VOICE -> "VU-VOIX"
            VUMeterMode.AMBIENT -> "VU-AMBI"
            VUMeterMode.OFF -> "VU-OFF"
        }
        
        // Gérer microphone selon mode
        when (newMode) {
                    VUMeterMode.VOICE -> {
                voiceManager.stopMicrophoneListening()
                    }
                    VUMeterMode.AMBIENT -> {
                if (stateManager.isReady) {
                    voiceManager.startMicrophoneListening()
                }
            }
                    VUMeterMode.OFF -> {
                voiceManager.stopMicrophoneListening()
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // UI UPDATE (Délégation à StateManager)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun updateStatusIndicators() {
        if (!isAdded) return
        
        // Synchroniser états
        stateManager.isTTSSpeaking = animationManager.isTTSSpeaking
        stateManager.isMusicPlaying = musicManager.isPlaying()
        
        // Déléguer mise à jour
        stateManager.updateStatusIndicators(
            statusBarIndicatorBSY,
            statusBarIndicatorRDY,
            statusBarIndicatorMSQ
        )
    }
    
    private fun setButtonsState(isOn: Boolean) {
        val allButtons = listOf(
            R.id.sendButton, R.id.menuDrawerButton, R.id.vuModeButton,
            R.id.aiButton, R.id.thinkButton, R.id.resetButton
        )
        
        val statusBarIndicators = listOf(
            R.id.statusBarIndicatorBSY, R.id.statusBarIndicatorRDY,
            R.id.statusBarIndicatorNET, R.id.statusBarIndicatorMSQ
        )
        
        stateManager.setButtonsState(isOn, requireView(), allButtons, statusBarIndicators)
    }
    
    private fun updateButtonStates() {
        val enabled = stateManager.isReady && !stateManager.isSpeaking && !stateManager.isThinking
        
        // ⚠️ aiButton géré séparément (doit être actif pour arrêter l'écoute)
        aiButton.isEnabled = stateManager.isReady
        
        thinkButton.isEnabled = enabled
        resetButton.isEnabled = enabled
        sendButton.isEnabled = enabled
        textInput.isEnabled = enabled
        vuModeButton.isEnabled = enabled
        menuDrawerButton.isEnabled = enabled
    }
    
    private fun resetInterface() {
        stateManager.resetStates()
        updateStatusIndicators()
        messageQueueManager.clearMessageQueue()
        
        if (animationManager.vuMeterMode == VUMeterMode.OFF) {
            animationManager.resetVuMeter()
        } else {
            animationManager.startVuMeterAnimation()
        }
        
        if (stateManager.isReady) {
            animationManager.startScannerAnimation(120)
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MUSIC CALLBACKS (Interface KittMusicManager.MusicListener)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onMusicStarted() {
        stateManager.isMusicPlaying = true
        updateMusicButtonState(true)
        updateStatusIndicators()
    }
    
    override fun onMusicStopped() {
        stateManager.isMusicPlaying = false
        updateMusicButtonState(false)
        updateStatusIndicators()
    }
    
    override fun onMusicCompleted() {
        stateManager.isMusicPlaying = false
        updateMusicButtonState(false)
        updateStatusIndicators()
    }
    
    override fun onMusicError(errorCode: Int) {
        stateManager.isMusicPlaying = false
        updateMusicButtonState(false)
        updateStatusIndicators()
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
            android.util.Log.d(TAG, "Could not update music button: ${e.message}")
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DRAWER CALLBACKS (Interface KittDrawerManager.DrawerListener)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onDrawerCommandSelected(command: String) {
        // Traiter commande
        processAIConversation(command)
    }
    
    override fun onDrawerClosed() {
        // Drawer fermé
    }
    
    override fun onThemeChanged(theme: String) {
        drawerManager.saveTheme(theme)
        applyTheme(theme)
    }
    
    override fun onPersonalityChanged(personality: String) {
        drawerManager.savePersonality(personality)
        
        // Réinitialiser KittAIService
        kittAIService = KittAIService(requireContext(), personality, platform = "vocal", actionCallback = this)
        
        // Changer voix TTS
        ttsManager.selectVoiceForPersonality(personality)
        
        // Message confirmation
        statusText.text = when (personality) {
            "GLaDOS" -> "GLaDOS ACTIVÉE - VOIX CHANGÉE"
            "KARR" -> "KARR ACTIVÉ - DOMINANCE ÉTABLIE"
            else -> "KITT ACTIVÉ - VOIX CHANGÉE"
        }
        
        android.util.Log.i(TAG, "✅ Personnalité changée: $personality")
    }
    
    override fun onAnimationModeChanged(mode: String) {
        when (mode) {
            "ORIGINAL" -> {
                animationManager.vuAnimationMode = VUAnimationMode.ORIGINAL
                showStatusMessageInternal("Animation VU-meter: ORIGINAL", 1500, MessageType.ANIMATION)
                ttsManager.speakAIResponse("Mode d'animation VU-meter changé vers l'original")
            }
            "DUAL" -> {
                animationManager.vuAnimationMode = VUAnimationMode.DUAL
                showStatusMessageInternal("Animation VU-meter: DUAL", 1500, MessageType.ANIMATION)
                ttsManager.speakAIResponse("Mode d'animation VU-meter changé vers le dual")
            }
        }
        
        if (animationManager.vuMeterMode != VUMeterMode.OFF) {
            animationManager.stopVuMeterAnimation()
            animationManager.startVuMeterAnimation()
        }
        updateAnimationModeButtons()
    }
    
    override fun onButtonPressed(buttonName: String) {
        ttsManager.speakAIResponse(buttonName)
    }
    
    override fun showStatusMessage(message: String, duration: Long, type: MessageType) {
        if (!isAdded) return
        messageQueueManager.showStatusMessage(message, duration, type, 0)
    }
    
    override fun speakAIResponse(response: String) {
        ttsManager.speakAIResponse(response)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // THINKING TRACE UI (Debug Mode)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Affiche le thinking trace si debug mode activé
     */
    private fun displayThinkingTraceIfEnabled() {
        val debugModeEnabled = sharedPrefs.getBoolean("show_thinking_trace", false)
        
        if (!debugModeEnabled) {
            thinkingCard.visibility = View.GONE
                return
            }
            
        val thinking = kittAIService.getLastThinkingTrace()
        
        if (thinking.isNotEmpty()) {
            thinkingText.text = thinking
            thinkingCard.visibility = View.VISIBLE
            android.util.Log.d(TAG, "🧠 Thinking trace displayed (${thinking.length} chars)")
        } else {
            thinkingCard.visibility = View.GONE
        }
    }
    
    /**
     * Setup click listener pour toggle thinking trace
     */
    private fun setupThinkingTraceToggle() {
        // Long click sur STATUS card pour toggle mode debug (thinking card est cachée par défaut)
        val statusCard = view?.findViewById<com.google.android.material.card.MaterialCardView>(R.id.statusCard)
        statusCard?.setOnLongClickListener {
            val currentMode = sharedPrefs.getBoolean("show_thinking_trace", false)
            val newMode = !currentMode
            
            sharedPrefs.edit()
                .putBoolean("show_thinking_trace", newMode)
                .apply()
            
            if (newMode) {
                showStatusMessageInternal("Debug Mode: Thinking Trace ACTIVÉ", 2000, MessageType.STATUS)
                displayThinkingTraceIfEnabled()
            } else {
                showStatusMessageInternal("Debug Mode: Thinking Trace DÉSACTIVÉ", 2000, MessageType.STATUS)
                thinkingCard.visibility = View.GONE
            }
            
            android.util.Log.i(TAG, "🧠 Thinking trace mode: ${if (newMode) "ENABLED" else "DISABLED"}")
            true
        }
        
        // Click normal pour ouvrir dialog avec thinking complet
        thinkingCard.setOnClickListener {
            val thinking = kittAIService.getLastThinkingTrace()
            if (thinking.isNotEmpty()) {
                showThinkingDialog(thinking)
            }
        }
    }
    
    /**
     * Affiche dialog avec thinking trace complet
     */
    private fun showThinkingDialog(thinking: String) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("🧠 THINKING TRACE")
            .setMessage(thinking)
            .setPositiveButton("OK", null)
            .setNeutralButton("Copier") { _: android.content.DialogInterface, _: Int ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = ClipData.newPlainText("Thinking Trace", thinking)
                clipboard.setPrimaryClip(clip)
                showStatusMessageInternal("Thinking copié", 1500, MessageType.STATUS)
            }
            .create()
        
        dialog.show()
    }
    
    override fun toggleMusic() {
        musicManager.toggleMusic()
    }
    
    override fun processAIConversation(command: String) {
        processAIConversationInternal(command)
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ⭐ NOUVEAU : BRIDGE LISTENER - Écoute des messages Chat → KITT
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * ⭐ NOUVEAU : Configure l'écoute des messages Chat → KITT via BidirectionalBridge
     * Écoute bridge.webToKittMessages et bridge.thinkingStream pour synchroniser avec Chat
     */
    private fun setupBridgeListener() {
        try {
            val bridge = com.chatai.services.BidirectionalBridge.getInstance(requireContext())
            
            // 1. Écouter les messages Chat → KITT
            bridgeListenerJob = coroutineScope.launch(Dispatchers.Main) {
                bridge.webToKittMessages.collect { message ->
                    if (isAdded && isVisible) {
                        // Fragment visible → Traiter et animer
                        android.util.Log.i(TAG, "📨 Message Chat → KITT reçu: ${message.content} (type=${message.type})")
                        handleKittMessage(message, animate = true)
                    } else {
                        // Fragment masqué → Stocker pour affichage ultérieur
                        android.util.Log.d(TAG, "📨 Message Chat → KITT reçu (Fragment masqué): ${message.content}")
                        pendingMessages.add(message)
                    }
                }
            }
            
            // 2. Écouter thinkingStream pour synchroniser animations avec Chat
            thinkingStreamJob = coroutineScope.launch(Dispatchers.Main) {
                bridge.thinkingStream.collect { chunk ->
                    if (isAdded && isVisible) {
                        when (chunk.type) {
                            com.chatai.services.BidirectionalBridge.ChunkType.THINKING -> {
                                // Thinking chunk → Démarrer thinking animation
                                android.util.Log.d(TAG, "🧠 Thinking chunk reçu: ${chunk.content.take(50)}...")
                                animationManager.startThinkingAnimation()
                                stateManager.isThinking = true
                                
                                // Afficher thinking dans thinkingCard si debug mode activé
                                val debugModeEnabled = sharedPrefs.getBoolean("show_thinking_trace", false)
                                if (debugModeEnabled) {
                                    thinkingCard.visibility = View.VISIBLE
                                    thinkingText.text = chunk.content
                                }
                            }
                            com.chatai.services.BidirectionalBridge.ChunkType.RESPONSE -> {
                                // Response chunk → Arrêter thinking animation, démarrer VU-meter
                                android.util.Log.d(TAG, "💬 Response chunk reçu: ${chunk.content.take(50)}...")
                                animationManager.stopThinkingAnimation { updateStatusIndicators() }
                                stateManager.isThinking = false
                                
                                if (chunk.isComplete) {
                                    // Réponse complète → Parler et afficher
                                    showStatusMessageInternal(chunk.content, 5000, MessageType.AI)
                                    ttsManager.speakAIResponse(chunk.content)
                                }
                            }
                        }
                    } else {
                        // Fragment masqué → Logger seulement
                        android.util.Log.d(TAG, "🧠 Thinking chunk reçu (Fragment masqué): ${chunk.type}")
                    }
                }
            }
            
            android.util.Log.i(TAG, "✅ Bridge listener initialisé - Écoute Chat → KITT active")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erreur lors de l'initialisation du bridge listener", e)
        }
    }
    
    /**
     * ⭐ NOUVEAU : Traite un message Chat → KITT
     * @param message Le message reçu du bridge
     * @param animate Si true, déclenche des animations (Fragment visible)
     */
    private fun handleKittMessage(
        message: com.chatai.services.BidirectionalBridge.BridgeMessage,
        animate: Boolean = true
    ) {
        if (!isAdded) return
        
        when (message.type) {
            com.chatai.services.BidirectionalBridge.MessageType.USER_INPUT -> {
                // Message utilisateur du Chat → Traiter comme entrée vocale
                android.util.Log.i(TAG, "👤 Message utilisateur du Chat: ${message.content}")
                
                if (animate) {
                    // Animation de réception
                    animationManager.startScannerAnimation(120)
                }
                
                // Traiter le message comme commande IA
                processAIConversationInternal(message.content)
            }
            
            com.chatai.services.BidirectionalBridge.MessageType.AI_RESPONSE -> {
                // Réponse IA du Chat → Afficher et parler
                android.util.Log.i(TAG, "🤖 Réponse IA du Chat: ${message.content}")
                
                if (animate) {
                    animationManager.stopScannerAnimation()
                    animationManager.startVuMeterAnimation()
                }
                
                showStatusMessageInternal("KITT: ${message.content}", 5000, MessageType.AI)
                ttsManager.speakAIResponse(message.content)
            }
            
            com.chatai.services.BidirectionalBridge.MessageType.SYSTEM_STATUS -> {
                // Statut système → Afficher seulement
                android.util.Log.d(TAG, "📊 Statut système: ${message.content}")
                showStatusMessageInternal(message.content, 3000, MessageType.STATUS)
            }
            
            com.chatai.services.BidirectionalBridge.MessageType.COMMAND -> {
                // Commande → Traiter comme commande vocale
                android.util.Log.i(TAG, "⚡ Commande du Chat: ${message.content}")
                processAIConversationInternal(message.content)
            }
            
            com.chatai.services.BidirectionalBridge.MessageType.ERROR -> {
                // Erreur → Afficher
                android.util.Log.e(TAG, "❌ Erreur du Chat: ${message.content}")
                showStatusMessageInternal("Erreur: ${message.content}", 3000, MessageType.ERROR)
            }
            
            else -> {
                // Autres types → Logger seulement
                android.util.Log.d(TAG, "📨 Message de type inconnu: ${message.type} - ${message.content}")
            }
        }
    }
    
    override fun updateAnimationModeButtons() {
        try {
            val drawerFragment = childFragmentManager.fragments.find { it is KittDrawerFragment } as? KittDrawerFragment
            if (drawerFragment != null) {
                val isOriginal = animationManager.vuAnimationMode == VUAnimationMode.ORIGINAL
                drawerFragment.updateAnimationModeButtons(isOriginal)
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Could not update animation mode buttons: ${e.message}")
        }
    }
    
    private fun showMenuDrawer() {
        if (!isAdded || view == null) {
            android.util.Log.w(TAG, "Cannot show drawer: Fragment not attached")
            return
        }
        
        val activityView = requireActivity().findViewById<View>(android.R.id.content)
        drawerManager.showMenuDrawer(parentFragmentManager, activityView, view)
    }
    
    private fun applyTheme(theme: String) {
        // Application du thème (couleurs, etc.)
        // Pour l'instant, juste logger
        android.util.Log.d(TAG, "Theme applied: $theme")
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // MESSAGE QUEUE (Délégation à MessageQueueManager)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    private fun showStatusMessageInternal(message: String, duration: Long = 2000, type: MessageType = MessageType.STATUS, priority: Int = 0) {
        if (!isAdded) return
        messageQueueManager.showStatusMessage(message, duration, type, priority)
    }
    
    private fun showDefaultStatus() {
        if (!isAdded) return
        statusText.text = ""
        statusText.isSelected = false
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // KITT ACTION CALLBACK (Function Calling)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onOpenArcade() {
        try {
            val intent = Intent(requireContext(), com.chatai.GameListActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening Arcade", e)
        }
    }
    
    override fun onOpenMusic() {
        musicManager.toggleMusic()
    }
    
    override fun onOpenConfig() {
                try {
                    val intent = Intent(requireContext(), com.chatai.activities.AIConfigurationActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening Config", e)
                }
            }
            
    override fun onOpenHistory() {
                try {
            val intent = Intent(requireContext(), com.chatai.activities.ConversationHistoryActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening History", e)
                }
            }
            
    override fun onOpenServerConfig() {
                try {
            val intent = Intent(requireContext(), com.chatai.activities.ServerConfigurationActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening Server Config", e)
        }
    }
    
    override fun onOpenChatAI() {
        kittFragmentListener?.hideKittInterface()
    }
    
    override fun onOpenKittInterface() {
        showStatusMessageInternal("Interface KITT déjà active, Michael.", 2000, MessageType.STATUS)
    }
    
    override fun onRestartKitt() {
        if (stateManager.isKittActive) {
            setStandbyMode()
            mainHandler.postDelayed({
                setReadyMode()
                showStatusMessageInternal("Systèmes redémarrés - KITT opérationnel", 2000, MessageType.STATUS)
            }, 500)
                    } else {
            setReadyMode()
        }
    }
    
    override fun onSetWiFi(enable: Boolean) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
                } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening WiFi settings", e)
        }
    }
    
    override fun onSetVolume(level: Int) {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (maxVol * level / 100).toInt()
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVol,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error setting volume", e)
        }
    }
    
    override fun onOpenSystemSettings(setting: String) {
        try {
            val intent = when (setting.lowercase()) {
                "wifi" -> Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                "bluetooth" -> Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                "display" -> Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                "sound" -> Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                else -> Intent(android.provider.Settings.ACTION_SETTINGS)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error opening system settings", e)
        }
    }
    
    override fun onChangePersonality(personality: String) {
        onPersonalityChanged(personality)
    }
    
    override fun onChangeModel(model: String) {
        sharedPrefs.edit()
            .putString("local_model_name", model)
            .apply()
        android.util.Log.i(TAG, "✅ Model changed to: $model")
    }
    
    override fun onChangeMode(mode: String) {
        sharedPrefs.edit()
            .putString("forced_mode", mode)
            .apply()
        android.util.Log.i(TAG, "✅ Mode changed to: $mode")
    }
    
    // ═════════════════════════════════════════════════════════════════════════════
    // MÉTHODES PUBLIQUES (Pour MainActivity et Quick Settings Tile)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    fun activateVoiceListening() {
        mainHandler.postDelayed({
            if (isAdded && view != null) {
                powerSwitch.isChecked = true
                setReadyMode()
                
                mainHandler.postDelayed({
                    if (stateManager.isReady && !stateManager.isListening) {
                        voiceManager.startVoiceRecognition()
                        stateManager.isListening = true
                        aiButton.text = "ÉCOUTE..."
                        updateStatusIndicators()
                    }
                }, 2000)
            }
        }, 100)
    }
    
    fun setFileServer(fileServer: Any?) {
        // FileServer si nécessaire
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // LIFECYCLE CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onResume() {
        super.onResume()
        
        // ⭐ NOUVEAU : Afficher les messages en attente si Fragment redevient visible
        if (pendingMessages.isNotEmpty()) {
            android.util.Log.d(TAG, "📨 Affichage de ${pendingMessages.size} messages en attente")
            pendingMessages.forEach { message ->
                handleKittMessage(message, animate = true)
            }
            pendingMessages.clear()
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // ⭐ NOUVEAU : Arrêter l'écoute du bridge (éviter fuites mémoire)
        bridgeListenerJob?.cancel()
        bridgeListenerJob = null
        thinkingStreamJob?.cancel()
        thinkingStreamJob = null
        
        ttsManager.stop()
        voiceManager.stopVoiceInterface()
        animationManager.stopAll()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Détruire tous les managers
        animationManager.destroy()
        ttsManager.destroy()
        voiceManager.destroy()
        messageQueueManager.destroy()
        musicManager.destroy()
        drawerManager.destroy()
        
        coroutineScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        
        android.util.Log.i(TAG, "🛑 KittFragment V3 destroyed - All managers cleaned up")
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
    }
}

