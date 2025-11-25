package com.chatai.audio

import android.content.Context
import com.chatai.AiConfigManager
import org.json.JSONObject

/**
 * Configuration pour TTS (Text-to-Speech).
 * Supporte Coqui TTS Server et Android TTS natif.
 */
data class TTSConfig(
    val engine: String,  // "coqui_server" ou "android_tts"
    val endpoint: String,  // "http://127.0.0.1:11401/process" (Mary-TTS API) ou "/synthesize" (custom)
    val model: String,  // "xtts_v2" ou autre
    val language: String,  // "fr", "en", etc.
    val speakerWavPath: String?,  // Chemin fichier référence (clone voix) - optionnel
    val emotion: String?,  // "happy", "sad", etc. (optionnel, XTTS-v2)
    val speed: Float,  // 0.5-2.0 (optionnel)
    val apiKey: String?  // Pour authentification (optionnel)
) {
    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:11401/process"
        const val DEFAULT_ENGINE = "android_tts"  // Par défaut Android TTS
        const val DEFAULT_MODEL = "xtts_v2"
        const val DEFAULT_LANGUAGE = "fr"
        const val DEFAULT_SPEED = 1.0f

        /**
         * Charger configuration depuis SharedPreferences
         */
        fun fromContext(context: Context): TTSConfig {
            val config = AiConfigManager.loadConfig(context)
            val tts = config.optJSONObject("tts") ?: JSONObject()
            
            return TTSConfig(
                engine = tts.optString("engine", DEFAULT_ENGINE),
                endpoint = tts.optString("endpoint", DEFAULT_ENDPOINT),
                model = tts.optString("model", DEFAULT_MODEL),
                language = tts.optString("language", DEFAULT_LANGUAGE),
                speakerWavPath = tts.optString("speakerWavPath", null).takeIf { it.isNotBlank() },
                emotion = tts.optString("emotion", null).takeIf { it.isNotBlank() },
                speed = tts.optDouble("speed", DEFAULT_SPEED.toDouble()).toFloat(),
                apiKey = tts.optString("apiKey", null).takeIf { it.isNotBlank() }
            )
        }
    }
}

