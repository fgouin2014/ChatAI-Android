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
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class WhisperServerRecognizer(
    private val config: AudioEngineConfig,
    private val callback: Callback,
    private val httpClient: OkHttpClient = OkHttpClient()
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
        val startTime = System.currentTimeMillis()

        while (isRecording) {
            val read = try {
                record.read(shortBuffer, 0, shortBuffer.size)
            } catch (e: Exception) {
                postError("Erreur lecture audio: ${e.message}")
                stopListening()
                return
            }
            if (read <= 0) {
                continue
            }

            val chunkBytes = toByteArray(shortBuffer, read)
            pcmStream.write(chunkBytes)

            val rmsDb = computeRmsDb(shortBuffer, read)
            post { callback.onRmsChanged(rmsDb) }

            if (!speechDetected && rmsDb > silenceThreshold) {
                speechDetected = true
                post { callback.onSpeechStart() }
            }

            val chunkDurationMs = (read.toDouble() / sampleRate.toDouble() * 1000).toLong()
            if (speechDetected) {
                if (rmsDb < silenceThreshold) {
                    silenceDuration += chunkDurationMs
                } else {
                    silenceDuration = 0L
                }
                if (silenceDuration >= requiredSilence) {
                    break
                }
            }

            if (System.currentTimeMillis() - startTime > maxDuration) {
                break
            }
        }

        stopListeningInternal()

        val audioBytes = pcmStream.toByteArray()
        if (audioBytes.isEmpty()) {
            postError("Aucun audio capturé")
            return
        }

        val wavData = buildWav(audioBytes)
        try {
            val text = uploadAndTranscribe(wavData)
            post { callback.onResult(text) }
        } catch (e: Exception) {
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
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("audio", "capture.wav", wavData.toRequestBody(mediaType))
            .addFormDataPart("language", config.language)
            .addFormDataPart("task", "transcribe")
            .addFormDataPart("model", config.preferredModel)
            .build()

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

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }
}

