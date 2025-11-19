package com.chatai;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Gestion centralisée de la configuration IA
 * - Synchronise SharedPreferences (source de vérité in-app)
 * - Maintient un fichier externe ai_config.json pour l'édition manuelle
 * - Expose des helpers pour l'application Android, le WebApp et HTTP
 */
public final class AiConfigManager {

    private static final String TAG = "AiConfigManager";
    private static final String PREFS_NAME = "chatai_ai_config";
    private static final String BASE_FOLDER = "/storage/emulated/0/ChatAI-Files";
    private static final String CONFIG_DIR = BASE_FOLDER + "/config";
        private static final String CONFIG_PATH = CONFIG_DIR + "/ai_config.json";
        private static final String LEGACY_CONFIG_PATH = BASE_FOLDER + "/ai_config.json";
        private static final String DEFAULT_KEYWORD_FILE = "hotwords/kit--kat_fr_android_v3_0_0.ppn";
        private static final String DEFAULT_AUDIO_ENGINE = "whisper_server";

    private AiConfigManager() {}

    public static synchronized JSONObject loadConfig(Context context) {
        ensureConfigFile(context);
        try {
            String content = readConfigJson(context);
            if (content != null && !content.trim().isEmpty()) {
                JSONObject json = new JSONObject(content);
                // Nettoyer le JSON : supprimer apiKey si elle est vide
                JSONObject cloud = json.optJSONObject("cloud");
                if (cloud != null && cloud.has("apiKey")) {
                    String apiKey = cloud.optString("apiKey", null);
                    if (apiKey == null || apiKey.trim().isEmpty()) {
                        cloud.remove("apiKey");
                        Log.d(TAG, "loadConfig: apiKey vide supprimée du JSON");
                    }
                }
                applyJsonToPreferences(context, json);
                return json;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing config file, rebuilding from preferences", e);
        }

        JSONObject fallback = buildJsonFromPreferences(context);
        writeJsonToFile(fallback);
        applyJsonToPreferences(context, fallback);
        return fallback;
    }

    public static synchronized String readConfigJson(Context context) {
        ensureConfigFile(context);
        File file = new File(CONFIG_PATH);
        if (!file.exists()) {
            JSONObject fallback = buildJsonFromPreferences(context);
            applyJsonToPreferences(context, fallback);
            writeJsonToFile(fallback);
            return toPrettyString(fallback);
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)
        )) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            String json = builder.toString().trim();
            if (!json.isEmpty()) {
                JSONObject jsonObj = new JSONObject(json);
                // Nettoyer le JSON : supprimer apiKey si elle est vide pour éviter la suppression de la clé
                JSONObject cloud = jsonObj.optJSONObject("cloud");
                if (cloud != null && cloud.has("apiKey")) {
                    String apiKey = cloud.optString("apiKey", null);
                    if (apiKey == null || apiKey.trim().isEmpty()) {
                        // Supprimer apiKey vide du JSON pour éviter qu'elle supprime la clé existante
                        cloud.remove("apiKey");
                        Log.d(TAG, "Nettoyage ai_config.json: apiKey vide supprimée du JSON");
                    }
                }
                applyJsonToPreferences(context, jsonObj);
            }
            return json;
        } catch (Exception e) {
            Log.e(TAG, "Error reading ai_config.json", e);
            JSONObject fallback = buildJsonFromPreferences(context);
            applyJsonToPreferences(context, fallback);
            writeJsonToFile(fallback);
            return toPrettyString(fallback);
        }
    }

    public static synchronized String writeConfigJson(Context context, String jsonContent) throws JSONException {
        JSONObject json = new JSONObject(jsonContent);
        json.put("updatedAt", System.currentTimeMillis());
        // Nettoyage: si moteur hotword != porcupine, retirer les champs Picovoice pour éviter la confusion
        JSONObject hotword = json.optJSONObject("hotword");
        if (hotword != null) {
            String engine = hotword.optString("engine", "openwakeword");
            if (!"porcupine".equalsIgnoreCase(engine)) {
                hotword.remove("accessKey");
                hotword.remove("keywordFile");
                hotword.remove("model");
            }
        }
        // Nettoyage: supprimer apiKey si elle est vide pour éviter qu'elle supprime la clé existante
        JSONObject cloud = json.optJSONObject("cloud");
        if (cloud != null && cloud.has("apiKey")) {
            String apiKey = cloud.optString("apiKey", null);
            if (apiKey == null || apiKey.trim().isEmpty()) {
                cloud.remove("apiKey");
                Log.d(TAG, "writeConfigJson: apiKey vide supprimée du JSON avant sauvegarde");
            }
        }
        applyJsonToPreferences(context, json);
        writeJsonToFile(json);
        return toPrettyString(json);
    }

    public static synchronized void ensureConfigFile(Context context) {
        try {
            File baseDir = new File(BASE_FOLDER);
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                Log.e(TAG, "Failed to create ChatAI-Files directory");
                return;
            }

            File configDir = new File(CONFIG_DIR);
            if (!configDir.exists() && !configDir.mkdirs()) {
                Log.e(TAG, "Failed to create ChatAI-Files/config directory");
                return;
            }

            File configFile = new File(CONFIG_PATH);
            File legacyFile = new File(LEGACY_CONFIG_PATH);
            if (!configFile.exists() && legacyFile.exists()) {
                try {
                    copyFile(legacyFile, configFile);
                    boolean deleted = legacyFile.delete();
                    Log.i(TAG, "Migrated legacy ai_config.json to /config (deletedOld=" + deleted + ")");
                } catch (IOException migrationError) {
                    Log.e(TAG, "Unable to migrate legacy ai_config.json", migrationError);
                }
            }

            if (!configFile.exists()) {
                JSONObject json = buildJsonFromPreferences(context);
                writeJsonToFile(json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring ai_config.json", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    private static JSONObject buildJsonFromPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        JSONObject root = new JSONObject();

        try {
            String selectedModel = prefs.getString("selected_model", "deepseek-r1:7b");
            long updatedAt = prefs.getLong("config_updated_at", System.currentTimeMillis());

            root.put("version", prefs.getString("ai_config_version", "1.0.0"));
            root.put("selectedModel", selectedModel);
            root.put("mode", prefs.getString("ai_mode", "cloud"));

            JSONObject cloud = new JSONObject();
            cloud.put("provider", prefs.getString("cloud_provider", "ollama"));
            // Récupérer la clé API depuis SecureConfig
            // IMPORTANT: Ne mettre apiKey dans le JSON que si elle existe
            // Si elle est vide/null, ne pas l'inclure pour éviter qu'elle soit supprimée
            SecureConfig secureConfig = new SecureConfig(context);
            String apiKey = secureConfig.getOllamaCloudApiKey();
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                cloud.put("apiKey", apiKey);
            }
            // Si apiKey est vide, ne pas l'inclure dans le JSON (pas de cloud.put("apiKey", ""))
            cloud.put("selectedModel", prefs.getString("cloud_selected_model", selectedModel));

            JSONObject webSearch = new JSONObject();
            webSearch.put("enabled", prefs.getBoolean("websearch_enabled", true));
            webSearch.put("provider", prefs.getString("websearch_provider", "ollama"));

            JSONObject thinking = new JSONObject();
            thinking.put("enabled", prefs.getBoolean("thinking_enabled", true));
            thinking.put("lastMessage", prefs.getString("thinking_last_message", ""));

            JSONObject vision = new JSONObject();
            vision.put("enabled", prefs.getBoolean("vision_enabled", true));
            vision.put("preferredModel", prefs.getString("vision_model", "llava:13b"));

            JSONObject audio = new JSONObject();
            audio.put("enabled", prefs.getBoolean("audio_enabled", true));
            audio.put("engine", prefs.getString("audio_engine", DEFAULT_AUDIO_ENGINE));
            audio.put("preferredModel", prefs.getString("audio_model", "ggml-small.bin"));
            audio.put("language", prefs.getString("audio_language", "fr"));
            audio.put("prompt", prefs.getString("audio_prompt", ""));
            audio.put("captureTimeoutMs", prefs.getInt("audio_capture_timeout", 8000));
            audio.put("silenceThresholdDb", Double.parseDouble(String.valueOf(prefs.getFloat("audio_silence_threshold", -45f))));
            audio.put("silenceDurationMs", prefs.getInt("audio_silence_duration", 1200));
            audio.put("mode", prefs.getString("audio_mode", DEFAULT_AUDIO_ENGINE));
            audio.put("endpoint", prefs.getString("audio_endpoint", "http://127.0.0.1:11400/inference"));
            audio.put("apiKey", prefs.getString("audio_api_key", ""));

            JSONObject hotword = new JSONObject();
            hotword.put("enabled", prefs.getBoolean("hotword_enabled", true));
            String engine = prefs.getString("hotword_engine", "openwakeword");
            hotword.put("engine", engine);
            if ("porcupine".equalsIgnoreCase(engine)) {
                hotword.put("accessKey", prefs.getString("porcupine_api_key", ""));
                hotword.put("model", prefs.getString("hotword_model_path", "porcupine_params_fr.pv"));
                hotword.put("keywordFile", prefs.getString("hotword_keyword_file", DEFAULT_KEYWORD_FILE));
            }
            hotword.put("sensitivity", Double.parseDouble(String.valueOf(prefs.getFloat("hotword_sensitivity", 0.6f))));
            String modelsString = prefs.getString("hotword_models", null);
            if (!TextUtils.isEmpty(modelsString)) {
                try {
                    hotword.put("models", new JSONArray(modelsString));
                } catch (JSONException ignored) {
                    hotword.put("models", buildDefaultModelArray());
                }
            } else {
                hotword.put("models", buildDefaultModelArray());
            }

            JSONObject tts = new JSONObject();
            tts.put("mode", prefs.getString("tts_mode", "local"));
            tts.put("voice", prefs.getString("tts_voice", "kitt"));

            JSONObject overrides = new JSONObject();
            overrides.put("kitt", prefs.getString("prompt_kitt", ""));
            overrides.put("glados", prefs.getString("prompt_glados", ""));
            overrides.put("karr", prefs.getString("prompt_karr", ""));

            JSONObject constraints = new JSONObject();
            constraints.put("maxContextTokens", prefs.getInt("max_context_tokens", 8192));
            constraints.put("maxResponseTokens", prefs.getInt("max_response_tokens", 2048));

            // local_server : Configuration du serveur Ollama local + modèle gemma fixé
            JSONObject localServer = new JSONObject();
            localServer.put("url", prefs.getString("local_server_url", "http://127.0.0.1:11434/v1/chat/completions"));
            // CRITIQUE: Modèle local fixé à gemma3-270m.gguf (ignorer toute autre valeur)
            localServer.put("model", "gemma3-270m.gguf");

            root.put("cloud", cloud);
            root.put("webSearch", webSearch);
            root.put("thinkingTrace", thinking);
            root.put("vision", vision);
            root.put("audio", audio);
            root.put("hotword", hotword);
            root.put("tts", tts);
            root.put("systemPromptOverrides", overrides);
            root.put("constraints", constraints);
            root.put("local_server", localServer);
            root.put("updatedAt", updatedAt);
        } catch (JSONException e) {
            Log.e(TAG, "Error building JSON from preferences", e);
        }

        return root;
    }

    private static void applyJsonToPreferences(Context context, JSONObject json) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        putStringIfPresent(editor, "ai_config_version", json, "version");
        // selectedModel supprimé (non utilisé, redondant avec cloud.selectedModel/local_server.model)
        putStringIfPresent(editor, "ai_mode", json, "mode");
        putLongIfPresent(editor, "config_updated_at", json, "updatedAt");
        
        // Convertir ai_mode en use_ollama_cloud (boolean)
        String aiMode = json.optString("mode", "cloud");
        boolean useCloud = "cloud".equalsIgnoreCase(aiMode);
        editor.putBoolean("use_ollama_cloud", useCloud);
        Log.d(TAG, "Mode configuré: " + aiMode + " → use_ollama_cloud=" + useCloud);

        JSONObject cloud = json.optJSONObject("cloud");
        if (cloud != null) {
            putStringIfPresent(editor, "cloud_provider", cloud, "provider");
            // Sauvegarder la clé API dans SecureConfig
            // IMPORTANT: Ne modifier la clé que si elle est explicitement présente dans le JSON
            // Si elle n'est pas présente, c'est que la webapp ne l'a pas modifiée (masquée avec *)
            SecureConfig secureConfig = new SecureConfig(context);
            if (cloud.has("apiKey")) {
                // La clé est présente dans le JSON (modifiée ou explicitement supprimée)
                String apiKey = cloud.optString("apiKey", null);
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    Log.d(TAG, "Sauvegarde clé Ollama Cloud depuis ai_config.json (" + apiKey.length() + " chars)");
                    secureConfig.setOllamaCloudApiKey(apiKey);
                } else {
                    // Champ vide dans le JSON
                    // Vérifier si une clé existe déjà dans SecureConfig
                    String existingKey = secureConfig.getOllamaCloudApiKey();
                    if (existingKey != null && !existingKey.trim().isEmpty()) {
                        // Une clé existe déjà, ne pas la supprimer (probablement un JSON mal formé ou vide)
                        Log.d(TAG, "Champ apiKey vide dans ai_config.json mais clé existante trouvée dans SecureConfig, conservation");
                    } else {
                        // Aucune clé existante, suppression OK
                        Log.d(TAG, "Suppression clé Ollama Cloud (champ vide dans ai_config.json et aucune clé existante)");
                        secureConfig.clearOllamaCloudApiKey();
                    }
                }
            } else {
                // La clé n'est pas présente dans le JSON = pas modifiée par la webapp
                // Ne pas toucher à SecureConfig, garder la valeur existante
                Log.d(TAG, "Clé Ollama Cloud non modifiée dans ai_config.json, conservation de la valeur existante");
            }
            putStringIfPresent(editor, "cloud_selected_model", cloud, "selectedModel");
            putStringIfPresent(editor, "ollama_cloud_model", cloud, "selectedModel");
        }
        
        // Traiter local_server
        JSONObject localServer = json.optJSONObject("local_server");
        if (localServer != null) {
            putStringIfPresent(editor, "local_server_url", localServer, "url");
            // CRITIQUE: Modèle local fixé à gemma3-270m.gguf (ignorer la valeur du JSON)
            // Ne pas utiliser putStringIfPresent pour le modèle, forcer gemma3-270m.gguf
            String jsonModel = localServer.optString("model", "");
            String fixedModel = "gemma3-270m.gguf";
            if (!jsonModel.equals(fixedModel)) {
                Log.d(TAG, "Local server model override: '" + jsonModel + "' → '" + fixedModel + "' (modèle local fixé)");
            }
            editor.putString("local_model_name", fixedModel);
            Log.d(TAG, "Local server config: url=" + localServer.optString("url") + ", model=" + fixedModel + " (fixé)");
        }

        JSONObject webSearch = json.optJSONObject("webSearch");
        if (webSearch != null) {
            putBooleanIfPresent(editor, "websearch_enabled", webSearch, "enabled");
            putStringIfPresent(editor, "websearch_provider", webSearch, "provider");
        }

        JSONObject thinking = json.optJSONObject("thinkingTrace");
        if (thinking != null) {
            putBooleanIfPresent(editor, "thinking_enabled", thinking, "enabled");
            putStringIfPresent(editor, "thinking_last_message", thinking, "lastMessage");
        }

        JSONObject vision = json.optJSONObject("vision");
        if (vision != null) {
            putBooleanIfPresent(editor, "vision_enabled", vision, "enabled");
            putStringIfPresent(editor, "vision_model", vision, "preferredModel");
        }

        JSONObject audio = json.optJSONObject("audio");
        if (audio != null) {
            putBooleanIfPresent(editor, "audio_enabled", audio, "enabled");
            putStringIfPresent(editor, "audio_engine", audio, "engine");
            putStringIfPresent(editor, "audio_model", audio, "preferredModel");
            putStringIfPresent(editor, "audio_language", audio, "language");
            putStringIfPresent(editor, "audio_prompt", audio, "prompt");
            putIntIfPresent(editor, "audio_capture_timeout", audio, "captureTimeoutMs");
            putFloatIfPresent(editor, "audio_silence_threshold", audio, "silenceThresholdDb");
            putIntIfPresent(editor, "audio_silence_duration", audio, "silenceDurationMs");
            putStringIfPresent(editor, "audio_mode", audio, "mode");
            putStringIfPresent(editor, "audio_endpoint", audio, "endpoint");
            putStringIfPresent(editor, "audio_api_key", audio, "apiKey");
        }

        JSONObject hotword = json.optJSONObject("hotword");
        if (hotword != null) {
            putBooleanIfPresent(editor, "hotword_enabled", hotword, "enabled");
            putStringIfPresent(editor, "hotword_engine", hotword, "engine");
            putStringIfPresent(editor, "porcupine_api_key", hotword, "accessKey");
            putStringIfPresent(editor, "hotword_model_path", hotword, "model");
            putStringIfPresent(editor, "hotword_keyword_file", hotword, "keywordFile");
            putFloatIfPresent(editor, "hotword_sensitivity", hotword, "sensitivity");
            JSONArray models = hotword.optJSONArray("models");
            if (models != null) {
                editor.putString("hotword_models", models.toString());
            }
        }

        JSONObject tts = json.optJSONObject("tts");
        if (tts != null) {
            putStringIfPresent(editor, "tts_mode", tts, "mode");
            putStringIfPresent(editor, "tts_voice", tts, "voice");
        }

        JSONObject overrides = json.optJSONObject("systemPromptOverrides");
        if (overrides != null) {
            putStringIfPresent(editor, "prompt_kitt", overrides, "kitt");
            putStringIfPresent(editor, "prompt_glados", overrides, "glados");
            putStringIfPresent(editor, "prompt_karr", overrides, "karr");
        }

        JSONObject constraints = json.optJSONObject("constraints");
        if (constraints != null) {
            putIntIfPresent(editor, "max_context_tokens", constraints, "maxContextTokens");
            putIntIfPresent(editor, "max_response_tokens", constraints, "maxResponseTokens");
        }

        editor.apply();
    }

    private static void writeJsonToFile(JSONObject json) {
        File file = new File(CONFIG_PATH);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            String pretty = toPrettyString(json);
            fos.write(pretty.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error writing ai_config.json", e);
        }
    }

    private static JSONArray buildDefaultModelArray() {
        JSONArray defaults = new JSONArray();
        try {
            JSONObject heyKitt = new JSONObject();
            heyKitt.put("name", "hey_kitt");
            heyKitt.put("asset", "hotwords/openwakeword/hey_kitt.tflite");
            heyKitt.put("threshold", 0.55);
            defaults.put(heyKitt);

            JSONObject glados = new JSONObject();
            glados.put("name", "glados");
            glados.put("asset", "hotwords/openwakeword/glados.tflite");
            glados.put("threshold", 0.6);
            defaults.put(glados);
        } catch (JSONException ignored) {
        }
        return defaults;
    }

    private static void copyFile(File source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory " + parent.getAbsolutePath());
        }

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static String toPrettyString(JSONObject json) {
        try {
            return json.toString(2);
        } catch (JSONException e) {
            return json.toString();
        }
    }

    private static void putStringIfPresent(SharedPreferences.Editor editor, String key, JSONObject json, String jsonKey) {
        if (json.has(jsonKey)) {
            String value = json.optString(jsonKey, "");
            editor.putString(key, value);
        }
    }

    private static void putBooleanIfPresent(SharedPreferences.Editor editor, String key, JSONObject json, String jsonKey) {
        if (json.has(jsonKey)) {
            editor.putBoolean(key, json.optBoolean(jsonKey));
        }
    }

    private static void putFloatIfPresent(SharedPreferences.Editor editor, String key, JSONObject json, String jsonKey) {
        if (json.has(jsonKey)) {
            try {
                float value = (float) json.optDouble(jsonKey, Double.NaN);
                if (!Float.isNaN(value)) {
                    editor.putFloat(key, value);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void putIntIfPresent(SharedPreferences.Editor editor, String key, JSONObject json, String jsonKey) {
        if (json.has(jsonKey)) {
            editor.putInt(key, json.optInt(jsonKey));
        }
    }

    private static void putLongIfPresent(SharedPreferences.Editor editor, String key, JSONObject json, String jsonKey) {
        if (json.has(jsonKey)) {
            editor.putLong(key, json.optLong(jsonKey));
        }
    }
}

