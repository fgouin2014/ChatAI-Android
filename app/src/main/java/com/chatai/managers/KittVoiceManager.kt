package com.chatai.managers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.chatai.audio.AudioEngineConfig
import com.chatai.audio.WhisperServerRecognizer
import java.util.*

/**
 * 🎤 KITT VOICE MANAGER V3
 * 
 * ⚠️⚠️⚠️ CODE COPIÉ À 100% DE V1 - AUCUNE SIMPLIFICATION ⚠️⚠️⚠️
 * 
 * Ce manager gère TOUT le système de reconnaissance vocale de KITT:
 * - SpeechRecognizer principal (commandes vocales)
 * - SpeechRecognizer VU-meter (capture RMS audio)
 * - Microphone listening (mode AMBIENT)
 * - RecognitionListener callbacks complets
 * 
 * RESPONSABILITÉS:
 * 1. Initialiser 2 SpeechRecognizer (principal + VU-meter)
 * 2. Gérer reconnaissance vocale pour commandes
 * 3. Capturer niveau audio RMS pour VU-meter AMBIENT
 * 4. Gérer erreurs reconnaissance (silencieux)
 * 5. Notifier KittFragment des résultats
 * 
 * RÈGLES ABSOLUES:
 * - DOUBLE LISTENER est ESSENTIEL (ne pas supprimer)
 * - Erreurs doivent être silencieuses (pas de messages intrusifs)
 * - Microphone listening séparé pour AMBIENT mode
 */
class KittVoiceManager(
    private val context: Context,
    private val listener: VoiceRecognitionListener
) {
    
    companion object {
        private const val TAG = "KittVoiceManager"
    }
    
    /**
     * Interface pour les callbacks Voice Recognition
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    interface VoiceRecognitionListener {
        fun onVoiceRecognitionReady()
        fun onVoiceRecognitionStart()
        fun onVoiceRecognitionResults(command: String)
        fun onVoiceRecognitionError(errorCode: Int)
        fun onVoiceRmsChanged(rmsdB: Float)  // Pour VU-meter AMBIENT
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // VARIABLES (COPIÉES DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var vuMeterRecognizer: SpeechRecognizer? = null  // ⚠️ LISTENER SÉPARÉ - ESSENTIEL
    private var whisperRecognizer: WhisperServerRecognizer? = null
    var isListening = false
        private set
    var isMicrophoneListening = false
        private set
    var currentMicrophoneLevel = -30f
        private set
    private var audioEngineConfig: AudioEngineConfig = AudioEngineConfig.fromContext(context)
    private var useWhisperServer: Boolean = audioEngineConfig.engine == AudioEngineConfig.DEFAULT_ENGINE
    
    // Timeouts pour Google Speech (éviter blocage si aucun résultat)
    private var timeoutHandler: android.os.Handler? = null
    private var globalTimeoutRunnable: Runnable? = null
    private var speechTimeoutRunnable: Runnable? = null
    private var endOfSpeechTimeoutRunnable: Runnable? = null
    
    // ════════════════════════════════════════════════════════════════════════
    // LISTENER VU-METER (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * ⭐ Listener séparé pour le VU-meter (évite les conflits avec la reconnaissance vocale)
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS SUPPRIMER ⚠️⚠️⚠️
     * 
     * POURQUOI SÉPARÉ:
     * - Capture RMS audio en continu pour VU-meter AMBIENT
     * - Ne bloque pas la reconnaissance vocale principale
     * - Permet mode AMBIENT + commandes vocales simultanés
     */
    private val vuMeterListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        
        override fun onRmsChanged(rmsdB: Float) {
            // Capturer le niveau audio réel du microphone pour VU-meter
            currentMicrophoneLevel = rmsdB
            
            // Debug : Afficher les niveaux audio
            android.util.Log.d("VUMeter", "Microphone level: ${rmsdB}dB")
            
            // Notifier KittFragment pour mise à jour VU-meter
            listener.onVoiceRmsChanged(rmsdB)
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
    
    // ════════════════════════════════════════════════════════════════════════
    // LISTENER PRINCIPAL (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * RecognitionListener principal pour commandes vocales
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            android.util.Log.d(TAG, "🎤 onReadyForSpeech() - prêt à écouter")
            listener.onVoiceRecognitionReady()
        }
        
        override fun onBeginningOfSpeech() {
            android.util.Log.d(TAG, "🎤 onBeginningOfSpeech() - parole détectée")
            listener.onVoiceRecognitionStart()
            
            // Annuler le timeout global (la parole a été détectée)
            globalTimeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
            globalTimeoutRunnable = null
            
            // TIMEOUT: Si aucun résultat après 7 secondes depuis le début de la parole, forcer l'arrêt
            if (timeoutHandler != null && isListening) {
                val self = this@KittVoiceManager
                speechTimeoutRunnable = Runnable {
                    if (self.isListening && self.speechRecognizer != null) {
                        android.util.Log.w(TAG, "⚠️ Timeout après début de parole (7s) - arrêt forcé")
                        self.cleanupSpeechRecognizer()
                        listener.onVoiceRecognitionError(-1) // No match
                    }
                }
                timeoutHandler?.postDelayed(speechTimeoutRunnable!!, 7000) // 7 secondes après début de parole
            }
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Log RMS toutes les 20 fois (réduire spam logs) pour diagnostic
            if ((rmsdB * 10).toInt() % 20 == 0) {
                android.util.Log.d(TAG, "🎤 onRmsChanged: ${rmsdB}dB (microphone actif)")
            }
            // Ce callback est maintenant géré par vuMeterListener pour VU-meter
            // Mais on log quand même pour diagnostic
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            android.util.Log.v(TAG, "🎤 onBufferReceived: ${buffer?.size ?: 0} bytes")
        }
        
        override fun onEndOfSpeech() {
            android.util.Log.d(TAG, "🎤 onEndOfSpeech() - fin de parole détectée, en attente des résultats...")
            // TIMEOUT: Si aucun résultat après 3 secondes depuis la fin de la parole, forcer l'arrêt
            if (timeoutHandler != null && isListening) {
                val self = this@KittVoiceManager
                endOfSpeechTimeoutRunnable = Runnable {
                    if (self.isListening && self.speechRecognizer != null) {
                        android.util.Log.w(TAG, "⚠️ Timeout après fin de parole (3s) - arrêt forcé")
                        self.cleanupSpeechRecognizer()
                        listener.onVoiceRecognitionError(-1) // No match
                    }
                }
                timeoutHandler?.postDelayed(endOfSpeechTimeoutRunnable!!, 3000) // 3 secondes après fin de parole
            }
        }
        
        override fun onError(error: Int) {
            android.util.Log.w(TAG, "🎤 onError($error) - arrêt de la reconnaissance")
            cleanupTimeouts()
            isListening = false
            // ⚠️ Pas d'affichage de statut pour les erreurs - juste silence (V1 original)
            listener.onVoiceRecognitionError(error)
        }
        
        override fun onResults(results: Bundle?) {
            cleanupTimeouts()
            
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches[0]
                android.util.Log.d(TAG, "✅ Voice recognized: '$command'")
                isListening = false
                listener.onVoiceRecognitionResults(command)
            } else {
                android.util.Log.w(TAG, "⚠️ No voice match")
                isListening = false
                listener.onVoiceRecognitionError(-1)
            }
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            // Résultats partiels - peut être utilisé pour afficher en temps réel
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {
            // Événements de reconnaissance
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALISATION (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialiser SpeechRecognizer (principal + VU-meter)
     * ⚠️⚠️⚠️ DOUBLE LISTENER - NE JAMAIS SUPPRIMER ⚠️⚠️⚠️
     */
    fun setupVoiceInterface() {
        android.util.Log.d(TAG, "🎤 setupVoiceInterface() called")
        refreshAudioEngine()
        
        // Initialiser SpeechRecognizer pour la reconnaissance vocale
        // CRITIQUE: SpeechRecognizer DOIT être créé sur le main thread
        if (!useWhisperServer && speechRecognizer == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    // Vérifier si Google Speech est disponible
                    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                        android.util.Log.e(TAG, "❌ Google Speech recognition non disponible sur ce device")
                        listener.onVoiceRecognitionError(-997)
                        return@post
                    }
                    
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    if (speechRecognizer == null) {
                        android.util.Log.e(TAG, "❌ SpeechRecognizer.createSpeechRecognizer() retourne null")
                        listener.onVoiceRecognitionError(-996)
                        return@post
                    }
                    speechRecognizer?.setRecognitionListener(recognitionListener)
                    android.util.Log.d(TAG, "✅ SpeechRecognizer principal créé sur main thread")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "❌ Erreur création SpeechRecognizer principal: ${e.message}", e)
                    listener.onVoiceRecognitionError(-995)
                }
            }
        }
        
        // ⚠️ Initialiser SpeechRecognizer séparé pour le VU-meter
        // CRITIQUE: Ne PAS créer vuMeterRecognizer si Google Speech est utilisé (pas Whisper)
        // Le VU-meter monopoliserait Google Speech et empêcherait le clavier Google de fonctionner
        // Note: On ne crée pas vuMeterRecognizer si Google Speech est utilisé
        // Whisper utilise son propre système audio et ne monopolise pas Google Speech
        if (!useWhisperServer) {
            // Détruire vuMeterRecognizer s'il existe déjà (libérer Google Speech)
            if (vuMeterRecognizer != null) {
                try {
                    vuMeterRecognizer?.stopListening()
                    vuMeterRecognizer?.destroy()
                    android.util.Log.d(TAG, "🛑 VU-meter SpeechRecognizer détruit (libération Google Speech)")
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Warning destroying vuMeterRecognizer: ${e.message}")
                }
                vuMeterRecognizer = null
            }
            // Ne pas créer vuMeterRecognizer avec Google Speech
            // Il monopoliserait la ressource même s'il n'est pas utilisé
            android.util.Log.d(TAG, "⚠️ VU-meter SpeechRecognizer non créé avec Google Speech (monopoliserait la ressource)")
            android.util.Log.d(TAG, "⚠️ Utilisez Whisper pour avoir le VU-meter sans monopoliser Google Speech")
        }
        
        if (useWhisperServer) {
            whisperRecognizer = WhisperServerRecognizer(audioEngineConfig, object : WhisperServerRecognizer.Callback {
                override fun onReady() {
                    listener.onVoiceRecognitionReady()
                }

                override fun onSpeechStart() {
                    listener.onVoiceRecognitionStart()
                }

                override fun onRmsChanged(rmsDb: Float) {
                    currentMicrophoneLevel = rmsDb
                    listener.onVoiceRmsChanged(rmsDb)
                }

                override fun onResult(text: String) {
                    isListening = false
                    listener.onVoiceRecognitionResults(text)
                }

                override fun onError(message: String) {
                    android.util.Log.e(TAG, "WhisperServerRecognizer error: $message")
                    isListening = false
                    listener.onVoiceRecognitionError(-997)
                }
            })
        }

        android.util.Log.d(
            TAG,
            "✅ Voice interface setup complete - speechRecognizer=${speechRecognizer != null}, useWhisper=$useWhisperServer"
        )
    }
    
    /**
     * Arrêter l'interface vocale
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopVoiceInterface() {
        // Arrêter l'écoute si elle était active
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
        
        // Arrêter l'écoute continue du microphone
        stopMicrophoneListening()
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // RECONNAISSANCE VOCALE PRINCIPALE (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Démarrer la reconnaissance vocale (commandes)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun startVoiceRecognition() {
        android.util.Log.d(TAG, "🎤 startVoiceRecognition() called - isListening=$isListening, speechRecognizer=${speechRecognizer != null}")
        
        if (isListening) {
            android.util.Log.w(TAG, "⚠️ Voice recognition already active")
            return
        }

        refreshAudioEngine()
        if (useWhisperServer) {
            // CRITIQUE: Arrêter Google Speech s'il est actif dans BackgroundService
            // Whisper et Google Speech ne peuvent PAS être actifs en même temps
            val stopGoogleIntent = android.content.Intent(context, com.chatai.BackgroundService::class.java)
            stopGoogleIntent.action = com.chatai.BackgroundService.ACTION_STOP_GOOGLE_SPEECH
            context.startService(stopGoogleIntent)
            android.util.Log.i(TAG, "Arrêt de Google Speech dans BackgroundService (libération pour Whisper)")
            
            if (whisperRecognizer == null) {
                setupVoiceInterface()
            }
            whisperRecognizer?.startListening()
            isListening = true
            return
        }

        // CRITIQUE: Arrêter Whisper s'il est actif dans BackgroundService
        // Whisper et Google Speech ne peuvent PAS être actifs en même temps
        val stopWhisperIntent = android.content.Intent(context, com.chatai.BackgroundService::class.java)
        stopWhisperIntent.action = com.chatai.BackgroundService.ACTION_STOP_WHISPER
        context.startService(stopWhisperIntent)
        android.util.Log.i(TAG, "Arrêt de Whisper dans BackgroundService (libération pour Google Speech)")

        // CRITIQUE: Arrêter le VU-meter avant de démarrer la reconnaissance vocale
        // Google Speech ne peut être utilisé que par une seule app à la fois
        // Le VU-meter utilise vuMeterRecognizer qui monopolise la ressource
        if (isMicrophoneListening) {
            android.util.Log.d(TAG, "🛑 Arrêt du VU-meter avant démarrage reconnaissance vocale (libération Google Speech)")
            stopMicrophoneListening()
        }

        if (speechRecognizer == null) {
            android.util.Log.e(TAG, "❌ SpeechRecognizer is NULL! Cannot start recognition.")
            listener.onVoiceRecognitionError(-998)
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Note: EXTRA_PROMPT non utilisé avec SpeechRecognizer (seulement pour startActivityForResult)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        
        // Initialiser le handler pour les timeouts
        timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        try {
            android.util.Log.d(TAG, "🎤 Calling speechRecognizer.startListening()...")
            speechRecognizer?.startListening(intent)
            isListening = true
            
            // TIMEOUT GLOBAL: Si aucun événement après 12 secondes, forcer l'arrêt
            val self = this
            globalTimeoutRunnable = Runnable {
                if (self.isListening && self.speechRecognizer != null) {
                    android.util.Log.w(TAG, "⚠️ Timeout global (12s) - arrêt forcé (aucune parole détectée)")
                    self.cleanupSpeechRecognizer()
                    listener.onVoiceRecognitionError(-1) // No match
                }
            }
            timeoutHandler?.postDelayed(globalTimeoutRunnable!!, 12000) // 12 secondes maximum
            
            android.util.Log.d(TAG, "✅ Voice recognition started successfully (timeouts: global=12s, speech=7s, end=3s)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error starting voice recognition: ${e.message}", e)
            cleanupTimeouts()
            listener.onVoiceRecognitionError(-999)
        }
    }
    
    /**
     * Arrêter la reconnaissance vocale
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopVoiceRecognition() {
        if (useWhisperServer) {
            whisperRecognizer?.stopListening()
            isListening = false
            android.util.Log.d(TAG, "🛑 Whisper server recognition stopped")
            return
        }
        
        cleanupSpeechRecognizer()
    }
    
    /**
     * Nettoyer les timeouts Google Speech
     */
    private fun cleanupTimeouts() {
        globalTimeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        speechTimeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        endOfSpeechTimeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        globalTimeoutRunnable = null
        speechTimeoutRunnable = null
        endOfSpeechTimeoutRunnable = null
    }
    
    /**
     * Nettoyer le SpeechRecognizer (arrêter + timeouts)
     */
    private fun cleanupSpeechRecognizer() {
        cleanupTimeouts()
        
        // CRITIQUE: Arrêter et détruire le SpeechRecognizer pour libérer Google Speech
        // Cela permet au clavier Google et autres apps d'utiliser la reconnaissance vocale
        if (speechRecognizer != null) {
            try {
                speechRecognizer?.stopListening()
                android.util.Log.d(TAG, "🛑 Voice recognition stopped")
                // Note: On ne détruit pas speechRecognizer ici car il peut être réutilisé
                // On le détruit seulement dans destroy() pour libérer complètement la ressource
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Warning stopping speechRecognizer: ${e.message}")
            }
        }
        isListening = false
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // MICROPHONE LISTENING (AMBIENT MODE) (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * ⭐ Démarrer écoute microphone pour VU-meter AMBIENT
     * 
     * Utilise le SpeechRecognizer séparé (vuMeterRecognizer)
     * Capture RMS audio en continu
     * 
     * ⚠️ CRITIQUE: Ne PAS démarrer si Google Speech est utilisé (pas Whisper)
     * Google Speech ne peut être utilisé que par une seule app à la fois
     * Le VU-meter monopoliserait la ressource et empêcherait le clavier Google de fonctionner
     */
    fun startMicrophoneListening() {
        if (useWhisperServer) {
            // Le nouveau moteur fournit déjà des RMS via callback
            return
        }
        if (isMicrophoneListening) return
        
        // CRITIQUE: Ne PAS démarrer le VU-meter si Google Speech est utilisé
        // Le VU-meter utilise vuMeterRecognizer qui monopolise Google Speech
        // Cela empêche le clavier Google et autres apps d'utiliser la reconnaissance vocale
        android.util.Log.w(TAG, "⚠️ VU-meter désactivé avec Google Speech (monopolise la ressource)")
        android.util.Log.w(TAG, "⚠️ Utilisez Whisper pour avoir le VU-meter sans monopoliser Google Speech")
        return
        
        // Code original commenté - ne pas utiliser avec Google Speech
        /*
        isMicrophoneListening = true
        
        // Démarrer une reconnaissance continue pour capturer les niveaux audio
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            // ⚠️ Utiliser le SpeechRecognizer séparé pour le VU-meter
            vuMeterRecognizer?.startListening(intent)
            android.util.Log.d(TAG, "🎤 Microphone listening started (AMBIENT mode)")
        } catch (e: Exception) {
            // Erreur silencieuse - pas d'affichage
            android.util.Log.w(TAG, "Warning starting microphone: ${e.message}")
        }
        */
    }
    
    /**
     * Arrêter écoute microphone
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopMicrophoneListening() {
        if (useWhisperServer) {
            isMicrophoneListening = false
            return
        }
        isMicrophoneListening = false
        vuMeterRecognizer?.stopListening()
        android.util.Log.d(TAG, "🛑 Microphone listening stopped")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Détruire le manager (libérer ressources)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun destroy() {
        stopVoiceRecognition()
        stopMicrophoneListening()
        
        speechRecognizer?.destroy()
        vuMeterRecognizer?.destroy()
        whisperRecognizer?.stopListening()
        whisperRecognizer = null

        speechRecognizer = null
        vuMeterRecognizer = null
        
        isListening = false
        isMicrophoneListening = false
        
        android.util.Log.i(TAG, "🛑 KittVoiceManager destroyed")
    }

    private fun refreshAudioEngine() {
        audioEngineConfig = AudioEngineConfig.fromContext(context)
        useWhisperServer = audioEngineConfig.engine.equals("whisper_server", ignoreCase = true)
        if (!useWhisperServer) {
            whisperRecognizer?.stopListening()
            whisperRecognizer = null
        }
    }
}
