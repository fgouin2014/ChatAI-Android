package com.chatai.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class WhisperServerRecognizer(
    private val config: AudioEngineConfig,
    private val callback: Callback,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)  // Connexion: 15s
        .readTimeout(120, TimeUnit.SECONDS)    // Lecture: 120s (transcription peut être très longue)
        .writeTimeout(60, TimeUnit.SECONDS)    // Écriture: 60s (upload du fichier)
        .callTimeout(150, TimeUnit.SECONDS)     // Timeout global: 150s (tout le call)
        .build()
) {

    interface Callback {
        fun onReady()
        fun onSpeechStart()
        fun onRmsChanged(rmsDb: Float)
        fun onResult(text: String)
        fun onError(message: String)
    }

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = max(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        sampleRate / 5
    )

    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRecording = false
    
    // ⭐ NOUVEAU : Warm-up pour la première utilisation (éviter mauvaises transcriptions)
    private var hasWarmedUp = false
    private val warmUpLock = java.util.concurrent.atomic.AtomicBoolean(false)

    fun startListening() {
        if (isRecording) {
            stopListening()
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )
        audioRecord = record
        try {
            record.startRecording()
        } catch (e: Exception) {
            postError("Impossible de démarrer l'enregistrement: ${e.message}")
            audioRecord = null
            return
        }
        isRecording = true
        post { callback.onReady() }
        scope.launch {
            captureAndTranscribe(record)
        }
    }

    private suspend fun captureAndTranscribe(record: AudioRecord) {
        val shortBuffer = ShortArray(bufferSize)
        val pcmStream = ByteArrayOutputStream()
        var speechDetected = false
        var silenceDuration = 0L
        val silenceThreshold = config.silenceThresholdDb
        val requiredSilence = config.silenceDurationMs
        val maxDuration = config.captureTimeoutMs
        val minDuration = 1500L // Minimum 1.5 secondes pour garantir un envoi (réduit pour éviter coupure)
        val startTime = System.currentTimeMillis()
        var totalSamples = 0L
        var maxRmsSeen = -160f

        android.util.Log.d("WhisperSTT", "Capture démarrée: threshold=${silenceThreshold}dB, timeout=${maxDuration}ms, min=${minDuration}ms, silence=${requiredSilence}ms")

        while (isRecording) {
            val read = try {
                record.read(shortBuffer, 0, shortBuffer.size)
            } catch (e: Exception) {
                android.util.Log.e("WhisperSTT", "Erreur lecture audio", e)
                postError("Erreur lecture audio: ${e.message}")
                stopListening()
                return
            }
            if (read <= 0) {
                continue
            }

            val chunkBytes = toByteArray(shortBuffer, read)
            pcmStream.write(chunkBytes)
            totalSamples += read

            val rmsDb = computeRmsDb(shortBuffer, read)
            if (rmsDb > maxRmsSeen) maxRmsSeen = rmsDb
            post { callback.onRmsChanged(rmsDb) }

            if (!speechDetected && rmsDb > silenceThreshold) {
                speechDetected = true
                android.util.Log.d("WhisperSTT", "Voix détectée: rms=${rmsDb}dB")
                post { callback.onSpeechStart() }
            }

            val chunkDurationMs = (read.toDouble() / sampleRate.toDouble() * 1000).toLong()
            val elapsed = System.currentTimeMillis() - startTime
            
            if (speechDetected) {
                if (rmsDb < silenceThreshold) {
                    silenceDuration += chunkDurationMs
                } else {
                    silenceDuration = 0L
                }
                // Si voix détectée + silence suffisant + minimum durée atteint
                // Augmenter le silence requis pour éviter coupure prématurée (1500ms au lieu de 1200ms)
                val adjustedSilence = maxOf(requiredSilence.toLong(), 1500L)
                if (silenceDuration >= adjustedSilence && elapsed >= minDuration) {
                    android.util.Log.d("WhisperSTT", "Silence détecté après voix, arrêt capture (${elapsed}ms, silence=${silenceDuration}ms)")
                    break
                }
            }

            // Timeout: arrêter même sans voix si on a au moins le minimum
            if (elapsed > maxDuration) {
                android.util.Log.d("WhisperSTT", "Timeout atteint (${elapsed}ms), arrêt capture")
                break
            }
            
            // Forcer arrêt après minimum si pas de voix (pour test) - réduit à 1 seconde
            if (!speechDetected && elapsed >= 1000L) {
                android.util.Log.d("WhisperSTT", "Minimum durée atteint sans voix (mode test), arrêt capture (${elapsed}ms)")
                break
            }
        }

        stopListeningInternal()

        val audioBytes = pcmStream.toByteArray()
        val durationSec = totalSamples.toDouble() / sampleRate.toDouble()
        android.util.Log.d("WhisperSTT", "Capture terminée: ${audioBytes.size} bytes, ${durationSec}s, maxRms=${maxRmsSeen}dB, speechDetected=${speechDetected}")
        
        if (audioBytes.isEmpty()) {
            android.util.Log.e("WhisperSTT", "Aucun audio capturé (${totalSamples} samples)")
            postError("Aucun audio capturé (max rms: ${maxRmsSeen}dB)")
            return
        }

        // TOUJOURS envoyer même si très court (pour test)
        if (durationSec < 0.5) {
            android.util.Log.w("WhisperSTT", "Audio très court (${durationSec}s), envoi quand même pour test")
        }

        val wavData = buildWav(audioBytes)
        android.util.Log.d("WhisperSTT", "Envoi WAV: ${wavData.size} bytes (${durationSec}s) vers ${config.endpoint}")
        android.util.Log.d("WhisperSTT", "Timeouts configurés: connect=15s, read=120s, write=60s, call=150s")
        val startTranscribeTime = System.currentTimeMillis()
        try {
            val text = uploadAndTranscribe(wavData)
            val transcribeDuration = System.currentTimeMillis() - startTranscribeTime
            android.util.Log.i("WhisperSTT", "Transcription reçue en ${transcribeDuration}ms: $text")
            post { callback.onResult(text) }
        } catch (e: Exception) {
            val transcribeDuration = System.currentTimeMillis() - startTranscribeTime
            android.util.Log.e("WhisperSTT", "Erreur transcription après ${transcribeDuration}ms", e)
            postError("Erreur transcription: ${e.message}")
        }
    }

    fun stopListening() {
        stopListeningInternal()
        scope.coroutineContext.cancel()
    }

    private fun stopListeningInternal() {
        if (!isRecording) return
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord?.release()
        audioRecord = null
    }

    private fun toByteArray(buffer: ShortArray, read: Int): ByteArray {
        val out = ByteArray(read * 2)
        var idx = 0
        for (i in 0 until read) {
            val value = buffer[i]
            out[idx++] = (value.toInt() and 0xFF).toByte()
            out[idx++] = ((value.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun computeRmsDb(buffer: ShortArray, read: Int): Float {
        if (read <= 0) return -160f
        var sum = 0.0
        for (i in 0 until read) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        val mean = sum / read
        val rms = sqrt(mean)
        if (rms <= 0.0) return -160f
        val db = 20.0 * log10(rms / Short.MAX_VALUE)
        return db.toFloat()
    }

    private fun buildWav(pcmData: ByteArray): ByteArray {
        val byteRate = sampleRate * 2
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(1.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(2.toShort())
        header.putShort(16.toShort())
        header.put("data".toByteArray())
        header.putInt(pcmData.size)
        return header.array() + pcmData
    }

    private fun uploadAndTranscribe(wavData: ByteArray): String {
        // ⭐ NOUVEAU : Warm-up lors de la première utilisation (éviter mauvaises transcriptions en langue incorrecte)
        if (!hasWarmedUp && warmUpLock.compareAndSet(false, true)) {
            try {
                warmUpWhisper()
                hasWarmedUp = true
            } catch (e: Exception) {
                android.util.Log.w("WhisperSTT", "⚠️ Warm-up Whisper échoué, continuons quand même: ${e.message}")
            } finally {
                warmUpLock.set(false)
            }
        }
        
        val mediaType = "audio/wav".toMediaType()
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "capture.wav", wavData.toRequestBody(mediaType))
            .addFormDataPart("language", config.language)
            .addFormDataPart("task", "transcribe")
            .addFormDataPart("model", config.preferredModel)
        
        // ⭐ AMÉLIORATION : Log de la langue envoyée pour diagnostic
        android.util.Log.i("WhisperSTT", "📤 Envoi transcription Whisper: language=${config.language}, model=${config.preferredModel}, task=transcribe")
        
        // Paramètres MINIMAUX - ne pas envoyer si = 0 (laisser Whisper utiliser ses défauts)
        // Seulement envoyer les paramètres explicitement configurés
        if (config.speedUp) {
            bodyBuilder.addFormDataPart("speed_up", "true")
        }
        if (config.temperature > 0.0f) {
            bodyBuilder.addFormDataPart("temperature", config.temperature.toString())
        }
        // Ne pas envoyer beamSize, bestOf, threads si = 0 (laisser Whisper décider)
        if (config.beamSize > 0) {
            bodyBuilder.addFormDataPart("beam_size", config.beamSize.toString())
        }
        if (config.bestOf > 0) {
            bodyBuilder.addFormDataPart("best_of", config.bestOf.toString())
        }
        if (config.threads > 0) {
            bodyBuilder.addFormDataPart("threads", config.threads.toString())
        }
        
        val body = bodyBuilder.build()

        val requestBuilder = Request.Builder()
            .url(config.endpoint.ifBlank { AudioEngineConfig.DEFAULT_ENDPOINT })
            .post(body)

        config.apiKey?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val bodyString = response.body?.string() ?: throw IllegalStateException("Réponse vide")
            val json = JSONObject(bodyString)
            val transcription = json.optString("text", json.optString("transcription", "")).ifBlank {
                throw IllegalStateException("Transcription manquante")
            }
            
            // ⭐ AMÉLIORATION : Log de la transcription reçue pour diagnostic
            android.util.Log.i("WhisperSTT", "📥 Transcription Whisper reçue (language=${config.language}): \"$transcription\"")
            
            return transcription
        }
    }
    
    /**
     * ⭐ NOUVEAU : Warm-up Whisper lors de la première utilisation
     * Envoie une requête silencieuse ou un ping pour initialiser le modèle dans la bonne langue
     */
    private fun warmUpWhisper() {
        try {
            android.util.Log.i("WhisperSTT", "🔥 Warm-up Whisper: Initialisation du modèle avec language=${config.language}")
            
            // Créer un fichier audio silencieux minimal (100ms de silence)
            // Note: buildWav() attend du PCM 16-bit, on crée directement le PCM silencieux
            val durationMs = 100
            val samples = (sampleRate * durationMs / 1000)
            val pcmData = ByteArray(samples * 2) // 16-bit = 2 bytes per sample (tous à 0 = silence)
            
            // Utiliser la même méthode buildWav() que pour la transcription normale
            val wavData = buildWav(pcmData)
            
            val mediaType = "audio/wav".toMediaType()
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "warmup.wav", wavData.toRequestBody(mediaType))
                .addFormDataPart("language", config.language)
                .addFormDataPart("task", "transcribe")
                .addFormDataPart("model", config.preferredModel)
            
            val body = bodyBuilder.build()
            val requestBuilder = Request.Builder()
                .url(config.endpoint.ifBlank { AudioEngineConfig.DEFAULT_ENDPOINT })
                .post(body)

            config.apiKey?.let {
                requestBuilder.header("Authorization", "Bearer $it")
            }

            // Utiliser un client avec timeout court pour le warm-up
            val warmUpClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()

            warmUpClient.newCall(requestBuilder.build()).execute().use { response ->
                val responseCode = response.code
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    android.util.Log.i("WhisperSTT", "✅ Warm-up Whisper réussi (HTTP $responseCode): modèle initialisé avec language=${config.language}")
                } else {
                    android.util.Log.w("WhisperSTT", "⚠️ Warm-up Whisper: HTTP $responseCode (modèle peut ne pas être initialisé)")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("WhisperSTT", "⚠️ Warm-up Whisper échoué: ${e.message}")
            // Ne pas bloquer si le warm-up échoue
        }
    }

    private fun postError(message: String) {
        post { callback.onError(message) }
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}

