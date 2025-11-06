package com.chatai.managers

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.*

/**
 * 🔊 KITT TTS MANAGER V3
 * 
 * ⚠️⚠️⚠️ CODE COPIÉ À 100% DE V1 - AUCUNE SIMPLIFICATION ⚠️⚠️⚠️
 * 
 * Ce manager gère TOUT le système Text-to-Speech de KITT:
 * - Initialisation TTS avec Locale.CANADA_FRENCH
 * - Configuration pitch/speed selon personnalité
 * - Sélection voix (masculine pour KITT, féminine pour GLaDOS)
 * - UtteranceProgressListener avec callbacks complets
 * - Synchronisation VU-meter avec TTS
 * 
 * RESPONSABILITÉS:
 * 1. Initialiser TextToSpeech
 * 2. Configurer la langue (français canadien)
 * 3. Sélectionner la voix selon la personnalité
 * 4. Gérer les callbacks onStart/onDone/onError
 * 5. Notifier KittFragment et AnimationManager des états TTS
 * 
 * RÈGLES ABSOLUES:
 * - TOUS les callbacks sont copiés de V1
 * - La logique de sélection de voix est COMPLÈTE
 * - Les fallbacks sont PRÉSERVÉS
 * - Les diagnostics sont COMPLETS
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
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    interface TTSListener {
        fun onTTSReady()
        fun onTTSStart(utteranceId: String?)
        fun onTTSDone(utteranceId: String?)
        fun onTTSError(utteranceId: String?)
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var textToSpeech: TextToSpeech? = null
    var isTTSReady = false
        private set
    var isTTSSpeaking = false
        private set
    
    // Configuration TTS
    private var currentPersonality = "KITT"
    private var ttsPitch = 0.9f
    private var ttsSpeed = 1.0f
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALISATION TTS (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialiser TextToSpeech
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun initialize() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context, this)
            android.util.Log.d(TAG, "TTS initialisé au chargement")
        }
    }
    
    /**
     * ⭐⭐⭐ FONCTION CRITIQUE - Callback initialisation TTS
     * 
     * Configure TOUT le système TTS:
     * - Langue: Locale.CANADA_FRENCH
     * - Pitch: 0.9f (KITT) ou 1.1f (GLaDOS)
     * - Speed: 1.0f
     * - UtteranceProgressListener avec callbacks complets
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS MODIFIER ⚠️⚠️⚠️
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.CANADA_FRENCH)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || 
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e(TAG, "❌ Langue française non supportée")
                isTTSReady = false
            } else {
                isTTSReady = true
                
                // Configuration par défaut KITT
                textToSpeech?.setPitch(ttsPitch)
                textToSpeech?.setSpeechRate(ttsSpeed)
                
                android.util.Log.d(TAG, "TTS configured: personality=$currentPersonality, speed=${ttsSpeed}x, pitch=${ttsPitch}x")
                
                // ⚠️⚠️⚠️ CONFIGURER LE LISTENER - TRÈS CRITIQUE
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isTTSSpeaking = true
                        listener.onTTSStart(utteranceId)
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isTTSSpeaking = false
                        listener.onTTSDone(utteranceId)
                    }
                    
                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        isTTSSpeaking = false
                        listener.onTTSError(utteranceId)
                    }
                })
                
                // TTS initialisé avec succès
                listener.onTTSReady()
            }
        } else {
            android.util.Log.e(TAG, "❌ TTS initialization failed")
            isTTSReady = false
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // SÉLECTION VOIX (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * ⭐⭐⭐ FONCTION TRÈS CRITIQUE - Sélectionner voix selon personnalité
     * 
     * LOGIQUE COMPLÈTE:
     * 
     * KITT (masculine):
     * 1. Priorité: x-frb- (fr-fr-x-frb-local) ⭐ PRIORITÉ ABSOLUE
     * 2. Fallback: x-frd- (fr-fr-x-frd-local)
     * 3. Fallback: Première voix française locale
     * 
     * GLaDOS (féminine):
     * 1. Priorité: x-frc- (fr-fr-x-frc-local)
     * 2. Fallback: x-fra- (fr-fr-x-fra-local)
     * 3. Fallback: Première voix qui n'est PAS frb/frd
     * 
     * KARR (masculine agressive):
     * 1. Priorité: x-frb- (fr-fr-x-frb-local) - COMME KITT mais pitch plus bas
     * 2. Fallback: x-frd- (fr-fr-x-frd-local)
     * 3. Fallback: Première voix française locale
     * 
     * ⚠️⚠️⚠️ COPIÉ À 100% DE V1 - NE JAMAIS MODIFIER LA LOGIQUE ⚠️⚠️⚠️
     */
    fun selectVoiceForPersonality(personality: String) {
        val tts = textToSpeech ?: return
        
        currentPersonality = personality
        
        try {
            // Lister TOUTES les voix disponibles
            val allVoices = tts.voices
            android.util.Log.i(TAG, "═══════════════════════════════════════════════════")
            android.util.Log.i(TAG, "DIAGNOSTIC VOIX TTS - TOTAL: ${allVoices?.size ?: 0}")
            android.util.Log.i(TAG, "PERSONNALITÉ DEMANDÉE: $personality")
            android.util.Log.i(TAG, "═══════════════════════════════════════════════════")
            
            allVoices?.forEachIndexed { index, voice ->
                val features = voice.features?.joinToString(", ") ?: "aucune"
                android.util.Log.i(TAG, """
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
            
            android.util.Log.i(TAG, "───────────────────────────────────────────────────")
            android.util.Log.i(TAG, "VOIX FRANÇAISES LOCALES: ${frenchVoices.size}")
            android.util.Log.i(TAG, "───────────────────────────────────────────────────")
            
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
                    
                    android.util.Log.i(TAG, "🤖 GLaDOS: Cherche voix féminine (x-frc- ou x-fra-)")
                    
                    // Pitch plus aigüe pour GLaDOS
                    ttsPitch = 1.1f
                    textToSpeech?.setPitch(ttsPitch)
                }
                "KARR" -> {
                    // KARR: voix masculine agressive - FRB PRIORITÉ comme KITT mais plus grave
                    selectedVoice = frenchVoices.firstOrNull { voice ->
                        voice.name.contains("x-frb-", ignoreCase = true) // fr-fr-x-frb-local
                    }
                    
                    if (selectedVoice == null) {
                        selectedVoice = frenchVoices.firstOrNull { voice ->
                            voice.name.contains("x-frd-", ignoreCase = true) // fr-fr-x-frd-local
                        }
                    }
                    
                    if (selectedVoice == null) {
                        selectedVoice = frenchVoices.firstOrNull()
                        android.util.Log.w(TAG, "⚠️ Aucune voix masculine (x-frb-/x-frd-) trouvée!")
                    }
                    
                    android.util.Log.i(TAG, "⚡ KARR: Cherche voix masculine agressive (x-frb- ou x-frd-)")
                    
                    // Pitch plus grave pour KARR (dominance)
                    ttsPitch = 0.8f
                    textToSpeech?.setPitch(ttsPitch)
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
                        android.util.Log.w(TAG, "⚠️ Aucune voix masculine (x-frb-/x-frd-) trouvée!")
                    }
                    
                    android.util.Log.i(TAG, "🚗 KITT: Cherche voix masculine (x-frb- ou x-frd-)")
                    
                    // Pitch normal pour KITT
                    ttsPitch = 0.9f
                    textToSpeech?.setPitch(ttsPitch)
                }
            }
            
            if (selectedVoice != null) {
                tts.voice = selectedVoice
                android.util.Log.i(TAG, "✅ VOIX SÉLECTIONNÉE: ${selectedVoice.name}")
                android.util.Log.i(TAG, "   Genre détecté: ${if (selectedVoice.name.contains("frb") || selectedVoice.name.contains("frd")) "MASCULIN" else "FÉMININ"}")
                android.util.Log.i(TAG, "   Pour personnalité: $personality")
            } else {
                android.util.Log.w(TAG, "⚠️ Aucune voix trouvée pour $personality")
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erreur sélection voix: ${e.message}", e)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // FONCTIONS PAROLE (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Parler un texte avec TTS
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun speak(text: String, utteranceId: String = "kitt_speech") {
        if (textToSpeech == null || isTTSSpeaking) {
            android.util.Log.w(TAG, "⚠️ TTS not ready or already speaking")
            return
        }
        
        try {
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            android.util.Log.d(TAG, "🔊 Speaking: '$text' (utteranceId: $utteranceId)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ TTS Error: ${e.message}")
            listener.onTTSError(utteranceId)
        }
    }
    
    /**
     * Message d'activation KITT (première fois seulement)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun speakKittActivationMessage() {
        if (!isTTSReady || isTTSSpeaking) {
            android.util.Log.d(TAG, "TTS pas prêt pour activation, fallback visuel")
            return
        }
        
        try {
            val activationMessage = "Bonjour, je suis KITT. En quoi puis-je vous aider ?"
            speak(activationMessage, "kitt_activation")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erreur TTS activation: ${e.message}")
            listener.onTTSError("kitt_activation")
        }
    }
    
    /**
     * Parler une réponse IA
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun speakAIResponse(response: String) {
        if (textToSpeech == null || isTTSSpeaking) {
            android.util.Log.w(TAG, "⚠️ TTS not ready or already speaking")
            return
        }
        
        try {
            speak(response, "ai_response")
            android.util.Log.d(TAG, "🔊 TTS Speaking AI response: '$response'")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ TTS Error AI response: ${e.message}")
        }
    }
    
    /**
     * Arrêter la parole en cours
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun stop() {
        textToSpeech?.stop()
        isTTSSpeaking = false
        android.util.Log.i(TAG, "🛑 TTS stopped")
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // CONFIGURATION TTS (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Configurer la vitesse de parole
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun setSpeechRate(rate: Float) {
        ttsSpeed = rate
        textToSpeech?.setSpeechRate(rate)
        android.util.Log.i(TAG, "Speech rate set to: $rate")
    }
    
    /**
     * Configurer la hauteur de la voix
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun setPitch(pitch: Float) {
        ttsPitch = pitch
        textToSpeech?.setPitch(pitch)
        android.util.Log.i(TAG, "Pitch set to: $pitch")
    }
    
    /**
     * Vérifier si TTS est prêt
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun isReady(): Boolean = isTTSReady
    
    /**
     * Vérifier si TTS parle actuellement
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun isSpeaking(): Boolean = isTTSSpeaking
    
    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP (COPIÉ DE V1)
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Détruire le TTS (libérer ressources)
     * ⚠️ COPIÉ À 100% DE V1 - NE PAS MODIFIER
     */
    fun destroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTTSReady = false
        isTTSSpeaking = false
        android.util.Log.i(TAG, "🛑 KittTTSManager destroyed")
    }
}
