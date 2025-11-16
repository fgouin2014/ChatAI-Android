package com.chatai.audio

import android.content.Context
import com.chatai.AiConfigManager
import org.json.JSONObject

/**
 * Chargement centralisé de la configuration audio/STT.
 * Permet de basculer dynamiquement entre Whisper Server et les moteurs legacy.
 */
data class AudioEngineConfig(
    val engine: String,
    val endpoint: String,
    val preferredModel: String,
    val language: String,
    val silenceThresholdDb: Float,
    val silenceDurationMs: Int,
    val captureTimeoutMs: Int,
    val apiKey: String?
) {
    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:11400/inference"
        const val DEFAULT_ENGINE = "whisper_server"

        fun fromContext(context: Context): AudioEngineConfig {
            val config = AiConfigManager.loadConfig(context)
            val audio = config.optJSONObject("audio") ?: JSONObject()
            return AudioEngineConfig(
                engine = audio.optString("engine", DEFAULT_ENGINE),
                endpoint = audio.optString("endpoint", DEFAULT_ENDPOINT),
                preferredModel = audio.optString("preferredModel", "ggml-small.bin"),
                language = audio.optString("language", "fr"),
                silenceThresholdDb = audio.optDouble("silenceThresholdDb", -45.0).toFloat(),
                silenceDurationMs = audio.optInt("silenceDurationMs", 1200),
                captureTimeoutMs = audio.optInt("captureTimeoutMs", 8000),
                apiKey = audio.optString("apiKey", "").takeIf { it.isNotBlank() }
            )
        }
    }
}

