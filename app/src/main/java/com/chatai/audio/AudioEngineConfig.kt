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
    val apiKey: String?,
    // Délai après détection hotword avant démarrage Whisper (ms)
    // Permet à l'utilisateur de commencer à parler après le beep de confirmation
    // Recommandé: 400-500ms (minimum 200ms pour que le beep se termine)
    val delayAfterHotwordMs: Int = 400,
    // Paramètres de performance Whisper
    val speedUp: Boolean = false,  // Accélération (peut réduire légèrement la précision)
    val temperature: Float = 0.0f,  // 0.0 = déterministe, plus élevé = plus créatif mais plus lent
    val beamSize: Int = 5,  // Taille du beam search (plus petit = plus rapide, moins précis)
    val bestOf: Int = 5,  // Nombre de candidats (plus petit = plus rapide)
    val threads: Int = 4  // Nombre de threads CPU (ajusté selon le device)
) {
    companion object {
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:11400/inference"
        const val DEFAULT_ENGINE = "whisper_server"

        fun fromContext(context: Context): AudioEngineConfig {
            val config = AiConfigManager.loadConfig(context)
            val audio = config.optJSONObject("audio") ?: JSONObject()
            var engine = audio.optString("engine", DEFAULT_ENGINE)
            
            // Migration: Google Speech désactivé - forcer Whisper Server
            if (engine == "legacy_google") {
                android.util.Log.w("AudioEngineConfig", "Google Speech désactivé - migration vers Whisper Server")
                engine = DEFAULT_ENGINE
                // Optionnel: mettre à jour la config pour éviter de re-détecter à chaque fois
                try {
                    audio.put("engine", DEFAULT_ENGINE)
                    config.put("audio", audio)
                    AiConfigManager.writeConfigJson(context, config.toString())
                    android.util.Log.i("AudioEngineConfig", "Configuration migrée: legacy_google -> whisper_server")
                } catch (e: Exception) {
                    android.util.Log.w("AudioEngineConfig", "Erreur lors de la migration de la config: ${e.message}")
                }
            }
            
            return AudioEngineConfig(
                engine = engine,
                endpoint = audio.optString("endpoint", DEFAULT_ENDPOINT),
                preferredModel = audio.optString("preferredModel", "ggml-small.bin"), // Modèle par défaut (celui présent sur le device)
                language = audio.optString("language", "fr"),
                silenceThresholdDb = audio.optDouble("silenceThresholdDb", -45.0).toFloat(),
                silenceDurationMs = audio.optInt("silenceDurationMs", 1200),
                captureTimeoutMs = audio.optInt("captureTimeoutMs", 8000),
                apiKey = audio.optString("apiKey", "").takeIf { it.isNotBlank() },
                // Délai après hotword : 400ms par défaut (recommandé: 300-500ms)
                // Minimum 200ms pour que le beep se termine, max 1000ms pour éviter attente trop longue
                delayAfterHotwordMs = audio.optInt("delayAfterHotwordMs", 400),
                // Paramètres MINIMAUX - laisser Whisper utiliser ses défauts pour meilleure précision
                speedUp = audio.optBoolean("speedUp", false),  // DÉSACTIVÉ (pas d'optimisation vitesse)
                temperature = audio.optDouble("temperature", 0.0).toFloat(),  // 0.0 = déterministe
                beamSize = audio.optInt("beamSize", 0),  // 0 = utiliser défaut Whisper (pas d'override)
                bestOf = audio.optInt("bestOf", 0),  // 0 = utiliser défaut Whisper (pas d'override)
                threads = audio.optInt("threads", 0)  // 0 = utiliser défaut Whisper (pas d'override)
            )
        }
    }
}
