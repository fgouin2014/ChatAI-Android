package com.chatai.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 🔊 COQUI TTS CLIENT
 * 
 * Client HTTP pour Coqui TTS Server (local).
 * Supporte API Mary-TTS (serveur intégré) et API custom.
 * 
 * Architecture:
 * Android App → HTTP GET/POST → Coqui Server → WAV audio → MediaPlayer
 * 
 * Pattern: Suit le même pattern que WhisperServerRecognizer
 */
class CoquiTTSClient(
    private val context: Context,
    private val config: TTSConfig,
    private val listener: CoquiTTSListener,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)  // Connexion: 10s
        .readTimeout(30, TimeUnit.SECONDS)    // Lecture: 30s (génération audio)
        .writeTimeout(10, TimeUnit.SECONDS)   // Écriture: 10s
        .callTimeout(40, TimeUnit.SECONDS)     // Timeout global: 40s
        .build()
) {
    
    companion object {
        private const val TAG = "CoquiTTSClient"
    }
    
    interface CoquiTTSListener {
        fun onTTSReady()
        fun onTTSStart(utteranceId: String?)
        fun onTTSDone(utteranceId: String?)
        fun onTTSError(utteranceId: String?)
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var mediaPlayer: MediaPlayer? = null
    private var isTTSReady = false
    private var isTTSSpeaking = false
    private var currentUtteranceId: String? = null
    
    // ════════════════════════════════════════════════════════════════════════
    // INITIALISATION
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Initialiser Coqui TTS Client
     * Vérifie que le serveur est disponible
     */
    fun initialize() {
        android.util.Log.d(TAG, "Initialisation Coqui TTS Client...")
        android.util.Log.d(TAG, "Endpoint: ${config.endpoint}")
        android.util.Log.d(TAG, "Engine: ${config.engine}")
        
        // Initialiser MediaPlayer
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setOnCompletionListener {
                onPlaybackComplete()
            }
            mediaPlayer?.setOnErrorListener { _, what, extra ->
                android.util.Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                onPlaybackError()
                true
            }
        }
        
        // Vérifier disponibilité serveur (ping)
        scope.launch {
            try {
                val isAvailable = pingServer()
                if (isAvailable) {
                    isTTSReady = true
                    post { listener.onTTSReady() }
                    android.util.Log.i(TAG, "✅ Coqui TTS Server disponible")
                } else {
                    android.util.Log.w(TAG, "⚠️ Coqui TTS Server non disponible")
                    isTTSReady = false
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Erreur vérification serveur: ${e.message}", e)
                isTTSReady = false
            }
        }
    }
    
    /**
     * Vérifier si le serveur est disponible (ping)
     */
    private suspend fun pingServer(): Boolean {
        return try {
            // Pour API Mary-TTS, on peut faire un GET simple
            // Pour API custom, on peut faire un GET /ping si disponible
            val pingUrl = if (config.endpoint.contains("/process")) {
                // API Mary-TTS: tester avec texte court
                "${config.endpoint.split("/process")[0]}/process?INPUT_TEXT=test&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE"
            } else {
                // API custom: essayer /ping
                config.endpoint.replace("/synthesize", "/ping")
            }
            
            val request = Request.Builder()
                .url(pingUrl)
                .get()
                .build()
            
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Ping échoué (non bloquant): ${e.message}")
            false
        }
    }
    
    // ════════════════════════════════════════════════════════════════════════
    // SYNTHÈSE VOCALE
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Parler un texte avec Coqui TTS
     */
    fun speak(text: String, utteranceId: String = "coqui_speech") {
        if (!isTTSReady || isTTSSpeaking) {
            android.util.Log.w(TAG, "⚠️ TTS not ready or already speaking")
            return
        }
        
        currentUtteranceId = utteranceId
        
        // Nettoyer le texte (retirer Markdown, etc.)
        val cleanText = cleanTextForTTS(text)
        
        android.util.Log.d(TAG, "🔊 Synthèse Coqui TTS: '$cleanText' (utteranceId: $utteranceId)")
        
        scope.launch {
            try {
                // Générer audio via serveur Coqui
                val wavBytes = synthesizeText(cleanText)
                
                // Jouer audio via MediaPlayer
                post {
                    playAudio(wavBytes, utteranceId)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Erreur synthèse Coqui TTS: ${e.message}", e)
                post {
                    listener.onTTSError(utteranceId)
                }
            }
        }
    }
    
    /**
     * Synthétiser texte via serveur Coqui
     * Retourne les bytes WAV
     */
    private suspend fun synthesizeText(text: String): ByteArray {
        val startTime = System.currentTimeMillis()
        
        // Détecter type d'API (Mary-TTS ou custom)
        val isMaryTTS = config.endpoint.contains("/process")
        
        val request = if (isMaryTTS) {
            // API Mary-TTS (GET)
            buildMaryTTSRequest(text)
        } else {
            // API custom (POST)
            buildCustomRequest(text)
        }
        
        android.util.Log.d(TAG, "Envoi requête vers: ${request.url}")
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${response.message}")
        }
        
        val wavBytes = response.body?.bytes() ?: throw IllegalStateException("Réponse vide")
        val duration = System.currentTimeMillis() - startTime
        
        android.util.Log.i(TAG, "✅ Audio reçu: ${wavBytes.size} bytes en ${duration}ms")
        
        return wavBytes
    }
    
    /**
     * Construire requête API Mary-TTS (GET)
     */
    private fun buildMaryTTSRequest(text: String): Request {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = "${config.endpoint}?INPUT_TEXT=$encodedText&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE"
        
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }
    
    /**
     * Construire requête API custom (POST)
     */
    private fun buildCustomRequest(text: String): Request {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("text", text)
            .addFormDataPart("language", config.language)
            .addFormDataPart("speed", config.speed.toString())
        
        config.emotion?.let {
            multipartBody.addFormDataPart("emotion", it)
        }
        
        config.speakerWavPath?.let { path ->
            val wavFile = File(path)
            if (wavFile.exists()) {
                val mediaType = "audio/wav".toMediaType()
                multipartBody.addFormDataPart(
                    "speaker_wav",
                    wavFile.name,
                    wavFile.readBytes().toRequestBody(mediaType)
                )
            }
        }
        
        val url = if (config.endpoint.contains("/synthesize")) {
            config.endpoint
        } else {
            "${config.endpoint}/synthesize"
        }
        
        return Request.Builder()
            .url(url)
            .post(multipartBody.build())
            .build()
    }
    
    /**
     * Jouer audio WAV via MediaPlayer
     */
    private fun playAudio(wavBytes: ByteArray, utteranceId: String) {
        try {
            if (mediaPlayer == null) {
                android.util.Log.e(TAG, "MediaPlayer non initialisé")
                listener.onTTSError(utteranceId)
                return
            }
            
            // Réinitialiser MediaPlayer
            mediaPlayer?.reset()
            
            // Sauvegarder WAV dans fichier temporaire
            val tempFile = File.createTempFile("coqui_tts_", ".wav", context.cacheDir)
            tempFile.writeBytes(wavBytes)
            
            android.util.Log.d(TAG, "Lecture audio: ${tempFile.absolutePath} (${wavBytes.size} bytes)")
            
            // Configurer MediaPlayer
            mediaPlayer?.setDataSource(tempFile.absolutePath)
            mediaPlayer?.prepare()
            
            // Démarrer lecture
            isTTSSpeaking = true
            listener.onTTSStart(utteranceId)
            mediaPlayer?.start()
            
            android.util.Log.d(TAG, "✅ Lecture audio démarrée")
            
            // Nettoyer fichier temporaire après lecture (dans onPlaybackComplete)
            // On garde la référence pour nettoyage
            currentTempFile = tempFile
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "❌ Erreur lecture audio: ${e.message}", e)
            isTTSSpeaking = false
            listener.onTTSError(utteranceId)
        }
    }
    
    private var currentTempFile: File? = null
    
    /**
     * Callback: Lecture terminée
     */
    private fun onPlaybackComplete() {
        android.util.Log.d(TAG, "✅ Lecture audio terminée")
        isTTSSpeaking = false
        val utteranceId = currentUtteranceId
        currentUtteranceId = null
        
        // Nettoyer fichier temporaire
        currentTempFile?.delete()
        currentTempFile = null
        
        listener.onTTSDone(utteranceId)
    }
    
    /**
     * Callback: Erreur lecture
     */
    private fun onPlaybackError() {
        android.util.Log.e(TAG, "❌ Erreur lecture audio")
        isTTSSpeaking = false
        val utteranceId = currentUtteranceId
        currentUtteranceId = null
        
        // Nettoyer fichier temporaire
        currentTempFile?.delete()
        currentTempFile = null
        
        listener.onTTSError(utteranceId)
    }
    
    /**
     * Arrêter la parole en cours
     */
    fun stop() {
        android.util.Log.i(TAG, "🛑 Arrêt Coqui TTS")
        mediaPlayer?.stop()
        mediaPlayer?.reset()
        isTTSSpeaking = false
        currentUtteranceId = null
        
        // Nettoyer fichier temporaire
        currentTempFile?.delete()
        currentTempFile = null
    }
    
    /**
     * Nettoyer le texte pour TTS (retirer Markdown, etc.)
     */
    private fun cleanTextForTTS(text: String): String {
        var cleaned = text
        
        // Retirer gras/italique
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
    
    /**
     * Vérifier si TTS est prêt
     */
    fun isReady(): Boolean = isTTSReady
    
    /**
     * Vérifier si TTS parle actuellement
     */
    fun isSpeaking(): Boolean = isTTSSpeaking
    
    /**
     * Détruire le client (libérer ressources)
     */
    fun destroy() {
        android.util.Log.i(TAG, "🛑 Destruction CoquiTTSClient")
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
        scope.cancel()
        isTTSReady = false
        isTTSSpeaking = false
    }
    
    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}

