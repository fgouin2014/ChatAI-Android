package com.chatai.managers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * 🎤 Gestionnaire de Reconnaissance Vocale pour KITT
 * 
 * Responsabilités:
 * - Initialisation SpeechRecognizer
 * - Démarrage/Arrêt de l'écoute
 * - Callbacks des résultats
 * - Gestion des erreurs vocales
 */
class KittVoiceManager(
    private val context: Context,
    private val listener: VoiceRecognitionListener
) : RecognitionListener {
    
    companion object {
        private const val TAG = "KittVoiceManager"
    }
    
    /**
     * Interface pour les callbacks de reconnaissance vocale
     */
    interface VoiceRecognitionListener {
        fun onVoiceReady()
        fun onVoiceStart()
        fun onVoiceResult(text: String)
        fun onVoiceError(error: Int, errorMessage: String)
        fun onVoiceRmsChanged(rmsdB: Float)
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    /**
     * Initialiser le SpeechRecognizer
     */
    fun initialize() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(this)
            Log.i(TAG, "✅ SpeechRecognizer initialized")
        }
    }
    
    /**
     * Démarrer l'écoute vocale
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening")
            return
        }
        
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CANADA_FRENCH)
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            
            isListening = true
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "🎤 Listening started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting listening", e)
            listener.onVoiceError(-1, "Erreur démarrage reconnaissance")
        }
    }
    
    /**
     * Arrêter l'écoute vocale
     */
    fun stopListening() {
        if (!isListening) return
        
        try {
            speechRecognizer?.stopListening()
            isListening = false
            Log.i(TAG, "🛑 Listening stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
    }
    
    /**
     * Vérifier si en écoute
     */
    fun isCurrentlyListening(): Boolean = isListening
    
    /**
     * Détruire le SpeechRecognizer
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        Log.i(TAG, "🛑 KittVoiceManager destroyed")
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // RecognitionListener Implementation
    // ═══════════════════════════════════════════════════════════════════════════════
    
    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech")
        listener.onVoiceReady()
    }
    
    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech")
        listener.onVoiceStart()
    }
    
    override fun onRmsChanged(rmsdB: Float) {
        listener.onVoiceRmsChanged(rmsdB)
    }
    
    override fun onBufferReceived(buffer: ByteArray?) {}
    
    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
        isListening = false
    }
    
    override fun onError(error: Int) {
        val errorMsg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "Aucune parole détectée"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout - Aucune parole"
            SpeechRecognizer.ERROR_CLIENT -> "Erreur client"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission micro manquante"
            SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout réseau"
            SpeechRecognizer.ERROR_AUDIO -> "Erreur audio"
            SpeechRecognizer.ERROR_SERVER -> "Erreur serveur Google"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconnaissance occupée"
            else -> "Erreur inconnue ($error)"
        }
        
        Log.e(TAG, "❌ Recognition error: $errorMsg (code: $error)")
        isListening = false
        listener.onVoiceError(error, errorMsg)
    }
    
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        
        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0]
            Log.i(TAG, "✅ Recognized: '$recognizedText'")
            listener.onVoiceResult(recognizedText)
        } else {
            Log.w(TAG, "No matches found")
            listener.onVoiceError(-1, "Aucune correspondance")
        }
        
        isListening = false
    }
    
    override fun onPartialResults(partialResults: Bundle?) {
        // Pas utilisé pour l'instant
    }
    
    override fun onEvent(eventType: Int, params: Bundle?) {
        // Pas utilisé pour l'instant
    }
}

