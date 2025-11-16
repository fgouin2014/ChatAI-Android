package com.chatai.hotword;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.util.Log;

import com.chatai.AiConfigManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gestion des préférences et fichiers pour Porcupine Hotword Detection
 * 
 * Configuration:
 * - Porcupine API Key (depuis ai_config.json externe)
 * - Modèle Porcupine (porcupine_params_fr.pv)
 * - Keyword file (kit-kat_fr_android_v3_0_0.ppn)
 */
public class HotwordPreferences {
    private static final String TAG = "HotwordPrefs";
    private static final String PREFS_NAME = "hotword_prefs";
    private static final String KEY_PORCUPINE_KEY = "porcupine_api_key";
    private static final String KEY_SENSITIVITY = "sensitivity";
        private static final String BASE_FOLDER = "/storage/emulated/0/ChatAI-Files";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public static class ModelConfig {
        public final String name;
        public final String assetPath;
        public final float threshold;
        public final boolean enabled;

        public ModelConfig(String name, String assetPath, float threshold, boolean enabled) {
            this.name = name;
            this.assetPath = assetPath;
            this.threshold = threshold;
            this.enabled = enabled;
        }
    }

    public HotwordPreferences(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Résout la clé API Porcupine depuis ai_config.json externe
     * Format attendu: /storage/emulated/0/ChatAI-Files/config/ai_config.json
     * {
     *   "hotword": {
     *     "accessKey": "..."
     *   }
     * }
     */
    public String getPorcupineApiKey() {
        // 1. Vérifier SharedPreferences d'abord
        String cached = prefs.getString(KEY_PORCUPINE_KEY, null);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        
        // 2. Lire via AiConfigManager (section "hotword.accessKey")
        try {
            org.json.JSONObject config = AiConfigManager.loadConfig(context);
            if (config != null) {
                org.json.JSONObject hotword = config.optJSONObject("hotword");
                if (hotword != null) {
                    String apiKey = hotword.optString("accessKey", "");
                    if (apiKey != null && !apiKey.isEmpty()) {
                        prefs.edit().putString(KEY_PORCUPINE_KEY, apiKey).apply();
                        Log.i(TAG, "Porcupine key resolved from AiConfigManager (hotword.accessKey)");
                        return apiKey;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving Porcupine key: " + e.getMessage());
        }
        
        Log.w(TAG, "No Porcupine API key found");
        return null;
    }
    
    /**
     * Définit la clé API Porcupine (pour tests ou configuration manuelle)
     */
    public void setPorcupineApiKey(String apiKey) {
        prefs.edit().putString(KEY_PORCUPINE_KEY, apiKey).apply();
    }
    
    /**
     * Obtient la sensibilité (0.0 - 1.0, défaut 0.6)
     */
    public float getSensitivity() {
        return prefs.getFloat(KEY_SENSITIVITY, 0.6f);
    }
    
    /**
     * Définit la sensibilité
     */
    public void setSensitivity(float sensitivity) {
        prefs.edit().putFloat(KEY_SENSITIVITY, sensitivity).apply();
    }
    
    /**
     * Chemin vers le modèle Porcupine français
     */
    public String getPorcupineModelPath() {
        // Préférence: fichiers externes ChatAI-Files
        File ext = new File(BASE_FOLDER + "/hotwords/porcupine_params_fr.pv");
        if (ext.exists()) return ext.getAbsolutePath();
        // Fallback: interne
        File modelFile = new File(context.getFilesDir(), "hotwords/porcupine_params_fr.pv");
        if (modelFile.exists()) return modelFile.getAbsolutePath();
        return null;
    }
    
    /**
     * Chemin vers le keyword file (kit-kat)
     */
    public String getKeywordPath() {
        // 1) Lire la valeur du JSON si présente
        try {
            JSONObject cfg = AiConfigManager.loadConfig(context);
            JSONObject hotword = cfg != null ? cfg.optJSONObject("hotword") : null;
            if (hotword != null) {
                String rel = hotword.optString("keywordFile", null);
                if (!TextUtils.isEmpty(rel)) {
                    // Chemin relatif à ChatAI-Files
                    File ext = new File(BASE_FOLDER + "/" + rel);
                    if (ext.exists()) return ext.getAbsolutePath();
                    // Supporter variante kit--kat vs kit-kat automatiquement
                    File variant = new File(BASE_FOLDER + "/" + normalizeKitKatPath(rel));
                    if (variant.exists()) return variant.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading keywordFile from config: " + e.getMessage());
        }
        // 2) Prober fichiers connus en externe
        File ext1 = new File(BASE_FOLDER + "/hotwords/kit-kat_fr_android_v3_0_0.ppn");
        if (ext1.exists()) return ext1.getAbsolutePath();
        File ext2 = new File(BASE_FOLDER + "/hotwords/Kit--Kat_fr_android_v3_0_0.ppn");
        if (ext2.exists()) return ext2.getAbsolutePath();
        // 3) Fallback interne
        File keywordFile = new File(context.getFilesDir(), "hotwords/kit-kat_fr_android_v3_0_0.ppn");
        if (keywordFile.exists()) return keywordFile.getAbsolutePath();
        return null;
    }
    
    public boolean areFilesReady() {
        if (!isHotwordEnabled()) {
            Log.w(TAG, "Hotword disabled via configuration");
            return false;
        }

        String engine = getHotwordEngine();
        if ("openwakeword".equalsIgnoreCase(engine)) {
            return validateOpenWakeWordAssets();
        } else {
            return validatePorcupineAssets();
        }
    }

    public String getHotwordEngine() {
        try {
            JSONObject config = AiConfigManager.loadConfig(context);
            if (config != null) {
                JSONObject hotword = config.optJSONObject("hotword");
                if (hotword != null) {
                    return hotword.optString("engine", "openwakeword");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving hotword engine: " + e.getMessage());
        }
        return "openwakeword";
    }

    public List<ModelConfig> getOpenWakeWordModels() {
        try {
            JSONObject config = AiConfigManager.loadConfig(context);
            if (config != null) {
                JSONObject hotword = config.optJSONObject("hotword");
                if (hotword != null) {
                    JSONArray models = hotword.optJSONArray("models");
                    if (models != null && models.length() > 0) {
                        List<ModelConfig> list = new ArrayList<>();
                        for (int i = 0; i < models.length(); i++) {
                            JSONObject item = models.optJSONObject(i);
                            if (item == null) continue;
                            String name = item.optString("name", "wakeword_" + i);
                            String asset = item.optString("asset");
                            float threshold = (float) item.optDouble("threshold", 0.5);
                            boolean enabled = item.optBoolean("enabled", true);
                            if (!TextUtils.isEmpty(asset)) {
                                if (enabled) {
                                    list.add(new ModelConfig(name, asset, threshold, true));
                                } else {
                                    Log.i(TAG, "OWW model disabled (skipped): " + name + " (" + asset + ")");
                                }
                            }
                        }
                        if (!list.isEmpty()) {
                            return list;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing openWakeWord models: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public boolean isHotwordEnabled() {
        try {
            JSONObject config = AiConfigManager.loadConfig(context);
            if (config != null) {
                JSONObject hotword = config.optJSONObject("hotword");
                if (hotword != null) {
                    return hotword.optBoolean("enabled", true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving hotword enabled flag: " + e.getMessage());
        }
        return true;
    }

    private boolean validatePorcupineAssets() {
        String modelPath = getPorcupineModelPath();
        String keywordPath = getKeywordPath();
        String apiKey = getPorcupineApiKey();
        
        boolean ready = modelPath != null && keywordPath != null && apiKey != null;
        
        if (!ready) {
            Log.w(TAG, "Porcupine assets missing - model=" + (modelPath != null) +
                    ", keyword=" + (keywordPath != null) + ", apiKey=" + (apiKey != null));
        }
        
        return ready;
    }

    // Expose helpers for engine-specific readiness
    public boolean arePorcupineFilesReady() {
        return validatePorcupineAssets();
    }

    public boolean areOpenWakeWordFilesReady() {
        return validateOpenWakeWordAssets();
    }

    private boolean validateOpenWakeWordAssets() {
        List<ModelConfig> models = getOpenWakeWordModels();
        if (models.isEmpty()) {
            Log.w(TAG, "No openWakeWord models configured");
            return false;
        }

        if (!assetExists("hotwords/openwakeword/melspectrogram.tflite") ||
                !assetExists("hotwords/openwakeword/embedding_model.tflite")) {
            Log.w(TAG, "Missing openWakeWord pre-processing models");
            return false;
        }

        for (ModelConfig model : models) {
            if (!assetExists(model.assetPath)) {
                Log.w(TAG, "Missing wakeword asset: " + model.assetPath);
                return false;
            }
        }

        return true;
    }

    private boolean assetExists(String assetPath) {
        AssetManager manager = context.getAssets();
        try (InputStream is = manager.open(assetPath)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

        private String normalizeKitKatPath(String relative) {
            if (TextUtils.isEmpty(relative)) return relative;
            // Convertir kit--kat <-> kit-kat selon ce qui existe réellement
            String alt = relative.replace("kit--kat", "kit-kat")
                    .replace("Kit--Kat", "kit-kat")
                    .replace("Kit-Kat", "Kit--Kat"); // couvrir variantes majuscules
            return alt;
        }
}
