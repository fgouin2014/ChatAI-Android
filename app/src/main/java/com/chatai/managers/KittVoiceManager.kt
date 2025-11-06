package com.chatai.managers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    var isListening = false
        private set
    var isMicrophoneListening = false
        private set
    var currentMicrophoneLevel = -30f
        private set
    
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
            listener.onVoiceRecognitionReady()
        }
        
        override fun onBeginningOfSpeech() {
            listener.onVoiceRecognitionStart()
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // Ce callback est maintenant géré par vuMeterListener
            // Pas de traitement ici pour éviter les conflits
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // Buffer audio reçu
        }
        
        override fun onEndOfSpeech() {
            // Fin de la parole détectée
        }
        
        override fun onError(error: Int) {
            isListening = false
            // ⚠️ Pas d'affichage de statut pour les erreurs - juste silence (V1 original)
            listener.onVoiceRecognitionError(error)
        }
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches[0]
                android.util.Log.d(TAG, "Voice recognized: '$command'")
                listener.onVoiceRecognitionResults(command)
            } else {
                android.util.Log.w(TAG, "No voice match")
                listener.onVoiceRecognitionError(-1)
            }
            
            isListening = false
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
        
        // Initialiser SpeechRecognizer pour la reconnaissance vocale
        if (speechRecognizer == null) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(recognitionListener)
                android.util.Log.d(TAG, "✅ SpeechRecognizer principal créé")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Erreur création SpeechRecognizer principal: ${e.message}")
            }
        }
        
        // ⚠️ Initialiser SpeechRecognizer séparé pour le VU-meter
        if (vuMeterRecognizer == null) {
            try {
                vuMeterRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                vuMeterRecognizer?.setRecognitionListener(vuMeterListener)
                android.util.Log.d(TAG, "✅ SpeechRecognizer VU-meter créé")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Erreur création SpeechRecognizer VU-meter: ${e.message}")
            }
        }
        
        android.util.Log.d(TAG, "✅ Voice interface setup complete - speechRecognizer=${speechRecognizer != null}, vuMeterRecognizer=${vuMeterRecognizer != null}")
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
        
        if (speechRecognizer == null) {
            android.util.Log.e(TAG, "❌ SpeechRecognizer is NULL! Cannot start recognition.")
            listener.onVoiceRecognitionError(-998)
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez maintenant...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        
        try {
            android.util.Log.d(TAG, "🎤 Calling speechRecognizer.startListening()...")
            speechRecognizer?.startListening(intent)
            isListening = true
            android.util.Log.d(TAG, "✅ Voice recognition started successfully")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Error starting voice recognition: ${e.message}", e)
            listener.onVoiceRecognitionError(-999)
        }
    }
    
    /**
     * Arrêter la reconnaissance vocale
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopVoiceRecognition() {
        speechRecognizer?.stopListening()
        isListening = false
        android.util.Log.d(TAG, "🛑 Voice recognition stopped")
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
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER ⚠️⚠️⚠️
     */
    fun startMicrophoneListening() {
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
            // ⚠️ Utiliser le SpeechRecognizer séparé pour le VU-meter
            vuMeterRecognizer?.startListening(intent)
            android.util.Log.d(TAG, "🎤 Microphone listening started (AMBIENT mode)")
        } catch (e: Exception) {
            // Erreur silencieuse - pas d'affichage
            android.util.Log.w(TAG, "Warning starting microphone: ${e.message}")
        }
    }
    
    /**
     * Arrêter écoute microphone
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stopMicrophoneListening() {
        isMicrophoneListening = false
        // ⚠️ Utiliser le SpeechRecognizer séparé pour le VU-meter
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
        
        speechRecognizer = null
        vuMeterRecognizer = null
        
        isListening = false
        isMicrophoneListening = false
        
        android.util.Log.i(TAG, "🛑 KittVoiceManager destroyed")
    }
}
