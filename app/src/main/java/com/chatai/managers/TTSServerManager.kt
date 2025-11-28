package com.chatai.managers

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * 🔊 TTS SERVER MANAGER
 * 
 * Gère la synthèse vocale via serveur Python HTTP (comme Whisper)
 * 
 * ARCHITECTURE:
 * Android App → HTTP POST → Python Server (port 11401) → SpeechT5 PyTorch → Audio WAV → Réponse
 * 
 * SIMILAIRE À: WhisperServerRecognizer (même architecture)
 */
class TTSServerManager(
    private val context: Context,
    private val listener: TTSListener
) {
    
    companion object {
        private const val TAG = "TTSServerManager"
        private const val TTS_SERVER_URL = "http://127.0.0.1:11401/synthesize"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
    
    /**
     * Interface pour les callbacks TTS (identique à KittTTSManager)
     */
    interface TTSListener {
        fun onTTSReady()
        fun onTTSStart(utteranceId: String?)
        fun onTTSDone(utteranceId: String?)
        fun onTTSError(utteranceId: String?)
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private var isReady = false
    private var isSpeaking = false
    private var audioTrack: AudioTrack? = null
    
    /**
     * Vérifier si le serveur TTS est disponible
     */
    fun initialize() {
        Thread {
            try {
                val request = Request.Builder()
                    .url("http://127.0.0.1:11401/health")
                    .get()
                    .build()
                
                val response = httpClient.newCall(request).execute()
                isReady = response.isSuccessful
                
                if (isReady) {
                    Log.i(TAG, "✅ TTS Server disponible")
                    listener.onTTSReady()
                } else {
                    Log.w(TAG, "⚠️ TTS Server non disponible (fallback recommandé)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ TTS Server non disponible: ${e.message}")
                isReady = false
            }
        }.start()
    }
    
    /**
     * Parler un texte (synthèse vocale)
     */
    fun speak(text: String, utteranceId: String = "tts_server") {
        if (!isReady) {
            Log.w(TAG, "TTS Server non prêt, fallback recommandé")
            listener.onTTSError(utteranceId)
            return
        }
        
        if (isSpeaking) {
            Log.w(TAG, "TTS déjà en cours, ignoré")
            return
        }
        
        Thread {
            try {
                isSpeaking = true
                listener.onTTSStart(utteranceId)
                
                Log.d(TAG, "Synthèse vocale: \"$text\"")
                
                // Envoyer requête HTTP
                val json = JSONObject().apply {
                    put("text", text)
                }
                val requestBody = json.toString().toRequestBody("application/json".toMediaType())
                
                val request = Request.Builder()
                    .url(TTS_SERVER_URL)
                    .post(requestBody)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                
                // Lire audio WAV
                val audioBytes = response.body?.bytes() ?: throw Exception("Réponse vide")
                Log.d(TAG, "Audio reçu: ${audioBytes.size} bytes")
                
                // Convertir WAV → PCM
                val pcmData = convertWavToPcm(audioBytes)
                
                // Jouer audio
                playAudio(pcmData)
                
                // Notifier fin
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isSpeaking = false
                    listener.onTTSDone(utteranceId)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erreur synthèse vocale: ${e.message}", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isSpeaking = false
                    listener.onTTSError(utteranceId)
                }
            }
        }.start()
    }
    
    /**
     * Convertir WAV → PCM int16
     */
    private fun convertWavToPcm(wavBytes: ByteArray): ShortArray {
        // WAV header: 44 bytes
        // Format: PCM 16-bit, 16kHz, mono
        val dataStart = 44
        val pcmBytes = wavBytes.sliceArray(dataStart until wavBytes.size)
        
        val buffer = ByteBuffer.wrap(pcmBytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        val pcmData = ShortArray(pcmBytes.size / 2)
        for (i in pcmData.indices) {
            pcmData[i] = buffer.short
        }
        
        return pcmData
    }
    
    /**
     * Jouer audio via AudioTrack
     * ⭐ MODIFIÉ: Vérification volume système et meilleure gestion erreurs
     */
    private fun playAudio(pcmData: ShortArray) {
        try {
            // ⭐ NOUVEAU: Vérifier volume système
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            if (currentVolume == 0) {
                Log.w(TAG, "⚠️ Volume système = 0, audio inaudible")
                Log.w(TAG, "⚠️ Veuillez augmenter le volume système pour entendre la synthèse vocale")
            } else {
                Log.d(TAG, "Volume système: $currentVolume/$maxVolume")
            }
            
            if (audioTrack == null) {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )
                
                if (bufferSize == AudioTrack.ERROR_BAD_VALUE || bufferSize == AudioTrack.ERROR) {
                    Log.e(TAG, "Erreur calcul buffer AudioTrack: $bufferSize")
                    return
                }
                
                audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
            }
            
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_UNINITIALIZED) {
                    Log.e(TAG, "AudioTrack non initialisé")
                    return
                }
                
                track.play()
                val bytesWritten = track.write(pcmData, 0, pcmData.size)
                
                if (bytesWritten < 0) {
                    Log.e(TAG, "Erreur écriture AudioTrack: $bytesWritten")
                    when (bytesWritten) {
                        AudioTrack.ERROR_INVALID_OPERATION -> Log.e(TAG, "   → Opération invalide (track non démarré?)")
                        AudioTrack.ERROR_BAD_VALUE -> Log.e(TAG, "   → Valeur invalide (buffer trop petit?)")
                        AudioTrack.ERROR_DEAD_OBJECT -> Log.e(TAG, "   → AudioTrack mort (redémarrer nécessaire)")
                        else -> Log.e(TAG, "   → Erreur inconnue")
                    }
                } else if (bytesWritten < pcmData.size) {
                    Log.w(TAG, "⚠️ Seulement $bytesWritten/${pcmData.size} bytes écrits (buffer plein?)")
                } else {
                    Log.d(TAG, "✅ Audio joué: $bytesWritten bytes (${(pcmData.size * 1000 / SAMPLE_RATE)}ms)")
                    
                    // Attendre fin lecture
                    val durationMs = (pcmData.size * 1000 / SAMPLE_RATE).toLong()
                    Thread.sleep(durationMs)
                    
                    track.stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lecture audio: ${e.message}", e)
        }
    }
    
    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            isSpeaking = false
            Log.d(TAG, "TTS arrêté")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt TTS: ${e.message}", e)
        }
    }
    
    fun shutdown() {
        stop()
        isReady = false
        Log.i(TAG, "TTS Server Manager fermé")
    }
    
    fun isTTSReady(): Boolean = isReady
    fun isTTSSpeaking(): Boolean = isSpeaking
}

