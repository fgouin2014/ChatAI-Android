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
    private var warmupPerformed = false // Flag pour éviter de faire le warm-up plusieurs fois
    private var isWarmupInProgress = false // ⭐ NOUVEAU : Flag pour indiquer que le warm-up est en cours

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
            // ⭐ FIX : Démarrer le warm-up en arrière-plan SANS bloquer la capture
            // La capture démarre immédiatement, et on ignore les résultats si le warm-up n'est pas terminé
            if (!warmupPerformed) {
                isWarmupInProgress = true // ⭐ FIX : Marquer que le warm-up est en cours
                // ⭐ FIX : Lancer le warm-up en parallèle (ne pas attendre)
                scope.launch {
                    performWarmup()
                    warmupPerformed = true
                    isWarmupInProgress = false // ⭐ FIX : Marquer que le warm-up est terminé
                    android.util.Log.i("WhisperSTT", "✅ Warm-up terminé, capture prête pour transcription réelle")
                }
            }
            // ⭐ FIX : Démarrer la capture immédiatement (ne pas attendre le warm-up)
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
        
        // ⭐ FIX : Attendre que le warm-up soit terminé avant d'envoyer la transcription
        // (mais continuer à capturer l'audio pendant le warm-up)
        if (isWarmupInProgress) {
            android.util.Log.d("WhisperSTT", "Warm-up en cours, attente avant transcription...")
            // Attendre maximum 5 secondes pour que le warm-up se termine
            var waitCount = 0
            while (isWarmupInProgress && waitCount < 50) {
                kotlinx.coroutines.delay(100)
                waitCount++
            }
            if (isWarmupInProgress) {
                android.util.Log.w("WhisperSTT", "Warm-up prend trop de temps, envoi de la transcription quand même")
            } else {
                android.util.Log.d("WhisperSTT", "✅ Warm-up terminé, envoi de la transcription")
            }
        }
        
        android.util.Log.d("WhisperSTT", "Timeouts configurés: connect=15s, read=120s, write=60s, call=150s")
        val startTranscribeTime = System.currentTimeMillis()
        try {
            val text = uploadAndTranscribe(wavData)
            val transcribeDuration = System.currentTimeMillis() - startTranscribeTime
            
            // ⭐ FIX : Filtrer les transcriptions vides, contenant uniquement des points, ou issues du warmup
            // Le warmup peut retourner "..." ou des chaînes vides, on ne veut pas les traiter comme des messages
            val trimmedText = text.trim()
            val isEmptyOrDots = trimmedText.isEmpty() || 
                                trimmedText == "..." || 
                                trimmedText.matches(Regex("^\\.+$")) || // Uniquement des points
                                trimmedText.length < 2 // Trop court pour être un vrai message
            
            if (isEmptyOrDots) {
                android.util.Log.d("WhisperSTT", "Transcription ignorée (vide ou warmup): \"$text\" (${transcribeDuration}ms)")
                // Ne pas appeler onResult() pour les transcriptions vides/points
                return
            }
            
            android.util.Log.i("WhisperSTT", "Transcription reçue en ${transcribeDuration}ms: $trimmedText")
            post { callback.onResult(trimmedText) }
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
        val mediaType = "audio/wav".toMediaType()
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "capture.wav", wavData.toRequestBody(mediaType))
            .addFormDataPart("language", config.language)
            .addFormDataPart("task", "transcribe")
            .addFormDataPart("model", config.preferredModel)
        
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
            return json.optString("text", json.optString("transcription", "")).ifBlank {
                throw IllegalStateException("Transcription manquante")
            }
        }
    }

    private fun postError(message: String) {
        post { callback.onError(message) }
    }

    /**
     * ⭐ NOUVEAU : Warm-up synchronisé pour Whisper
     * Envoie 200ms d'audio silencieux avec language="fr" pour initialiser le modèle
     * Attend 100ms après le warm-up pour garantir que le modèle est initialisé
     * ⭐ FIX : Le résultat du warm-up est ignoré (ne déclenche pas onResult())
     */
    private suspend fun performWarmup() {
        android.util.Log.d("WhisperSTT", "🔥 Démarrage warm-up Whisper...")
        val warmupStartTime = System.currentTimeMillis()
        
        try {
            // Générer 200ms d'audio silencieux (PCM 16-bit mono 16kHz)
            val warmupDurationMs = 200L
            val warmupSamples = (sampleRate * warmupDurationMs / 1000).toInt()
            val warmupBytes = ByteArray(warmupSamples * 2) // 16-bit = 2 bytes par sample
            // ByteArray est initialisé à 0 par défaut, donc silence complet
            
            // Construire le WAV avec l'audio silencieux
            val wavData = buildWav(warmupBytes)
            
            android.util.Log.d("WhisperSTT", "Warm-up: Envoi de ${wavData.size} bytes (${warmupDurationMs}ms) d'audio silencieux avec language=\"fr\"")
            
            // ⭐ FIX : Envoyer le warm-up avec language="fr" explicitement
            // Le résultat est ignoré (ne déclenche pas callback.onResult())
            val warmupText = uploadAndTranscribeForWarmup(wavData)
            val warmupDuration = System.currentTimeMillis() - warmupStartTime
            
            // ⭐ FIX : Log le résultat mais ne pas le traiter comme une transcription réelle
            android.util.Log.d("WhisperSTT", "✅ Warm-up terminé en ${warmupDuration}ms (réponse ignorée: \"${warmupText}\")")
            
            // ⭐ Attendre 100ms après le warm-up pour garantir que le modèle est initialisé
            kotlinx.coroutines.delay(100)
            
            android.util.Log.i("WhisperSTT", "🔥 Warm-up complet, modèle Whisper initialisé (prêt pour transcription réelle)")
        } catch (e: Exception) {
            android.util.Log.w("WhisperSTT", "⚠️ Warm-up échoué (non bloquant): ${e.message}")
            // Ne pas bloquer si le warm-up échoue - la première transcription réelle fera l'initialisation
        }
    }

    /**
     * Version spéciale de uploadAndTranscribe pour le warm-up
     * Envoie avec language="fr" explicitement
     */
    private suspend fun uploadAndTranscribeForWarmup(wavData: ByteArray): String {
        val mediaType = "audio/wav".toMediaType()
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "warmup.wav", wavData.toRequestBody(mediaType))
            .addFormDataPart("language", "fr") // ⭐ Langue française explicite pour warm-up
            .addFormDataPart("task", "transcribe")
            .addFormDataPart("model", config.preferredModel)
        
        // Paramètres MINIMAUX pour warm-up (comme dans uploadAndTranscribe)
        if (config.speedUp) {
            bodyBuilder.addFormDataPart("speed_up", "true")
        }
        if (config.temperature > 0.0f) {
            bodyBuilder.addFormDataPart("temperature", config.temperature.toString())
        }
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
            return json.optString("text", json.optString("transcription", "")).ifBlank {
                "" // Warm-up peut retourner vide, c'est normal
            }
        }
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}

