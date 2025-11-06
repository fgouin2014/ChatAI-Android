package com.chatai.managers

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * 🔊 Gestionnaire TTS (Text-to-Speech) pour KITT
 * 
 * Responsabilités:
 * - Initialisation TextToSpeech
 * - Configuration voix (KITT/GLaDOS)
 * - Synthèse vocale
 * - Callbacks fin de parole
 */
class KittTTSManager(
    private val context: Context,
    private val listener: TTSListener
) : TextToSpeech.OnInitListener {
    
    companion object {
        private const val TAG = "KittTTSManager"
    }
    
    /**
     * Interface pour les callbacks TTS
     */
    interface TTSListener {
        fun onTTSReady()
        fun onTTSStart()
        fun onTTSDone()
        fun onTTSError()
    }
    
    private var textToSpeech: TextToSpeech? = null
    private var isTTSReady = false
    private var isSpeaking = false
    
    /**
     * Initialiser le TTS
     */
    fun initialize() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context, this)
            Log.i(TAG, "🔊 TTS initialization started")
        }
    }
    
    /**
     * Callback d'initialisation TTS
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.CANADA_FRENCH)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "❌ French language not supported")
                isTTSReady = false
            } else {
                isTTSReady = true
                
                // Configuration par défaut KITT
                textToSpeech?.setPitch(0.9f)
                textToSpeech?.setSpeechRate(1.0f)
                
                // Listener pour les événements de parole
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                        listener.onTTSStart()
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        listener.onTTSDone()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        listener.onTTSError()
                    }
                })
                
                Log.i(TAG, "✅ TTS ready (French)")
                listener.onTTSReady()
            }
        } else {
            Log.e(TAG, "❌ TTS initialization failed")
            isTTSReady = false
        }
    }
    
    /**
     * Parler un texte
     */
    fun speak(text: String) {
        if (!isTTSReady) {
            Log.e(TAG, "❌ TTS not ready")
            listener.onTTSError()
            return
        }
        
        try {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "kitt_speech")
            
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "kitt_speech")
            Log.i(TAG, "🔊 Speaking: '$text'")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking", e)
            listener.onTTSError()
        }
    }
    
    /**
     * Arrêter la parole en cours
     */
    fun stop() {
        textToSpeech?.stop()
        isSpeaking = false
        Log.i(TAG, "🛑 TTS stopped")
    }
    
    /**
     * Vérifier si le TTS est prêt
     */
    fun isReady(): Boolean = isTTSReady
    
    /**
     * Vérifier si en train de parler
     */
    fun isSpeaking(): Boolean = isSpeaking
    
    /**
     * Configurer la vitesse de parole
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
        Log.i(TAG, "Speech rate set to: $rate")
    }
    
    /**
     * Configurer la hauteur de la voix
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
        Log.i(TAG, "Pitch set to: $pitch")
    }
    
    /**
     * Sélectionner une voix pour KITT
     */
    fun selectVoiceForKitt() {
        if (!isTTSReady) return
        
        try {
            val voices = textToSpeech?.voices
            
            // Chercher une voix masculine française
            val preferredVoice = voices?.find { voice ->
                voice.locale.language == "fr" &&
                voice.name.contains("male", ignoreCase = true) &&
                !voice.name.contains("female", ignoreCase = true)
            }
            
            if (preferredVoice != null) {
                textToSpeech?.voice = preferredVoice
                Log.i(TAG, "✅ KITT voice selected: ${preferredVoice.name}")
            } else {
                Log.w(TAG, "⚠️ No male French voice found, using default")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting voice", e)
        }
    }
    
    /**
     * Sélectionner une voix pour GLaDOS
     */
    fun selectVoiceForGlados() {
        if (!isTTSReady) return
        
        try {
            val voices = textToSpeech?.voices
            
            // Chercher une voix féminine française
            val preferredVoice = voices?.find { voice ->
                voice.locale.language == "fr" &&
                voice.name.contains("female", ignoreCase = true)
            }
            
            if (preferredVoice != null) {
                textToSpeech?.voice = preferredVoice
                // Voix plus aigüe pour GLaDOS
                textToSpeech?.setPitch(1.1f)
                Log.i(TAG, "✅ GLaDOS voice selected: ${preferredVoice.name}")
            } else {
                Log.w(TAG, "⚠️ No female French voice found, using default")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting voice", e)
        }
    }
    
    /**
     * Détruire le TTS
     */
    fun destroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTTSReady = false
        isSpeaking = false
        Log.i(TAG, "🛑 KittTTSManager destroyed")
    }
}

