package com.chatai.hotword;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Utility pour exposer la liste des modèles openWakeWord embarqués.
 */
public final class HotwordAssetProvider {

    private static final String TAG = "HotwordAssetProvider";
    private static final String BASE_DIR = "hotwords/openwakeword";

    private HotwordAssetProvider() {}

    public static JSONArray listAssets(Context context) {
        JSONArray array = new JSONArray();
        if (context == null) {
            return array;
        }

        AssetManager assetManager = context.getAssets();
        try {
            String[] files = assetManager.list(BASE_DIR);
            if (files == null) {
                return array;
            }

            for (String file : files) {
                if (file == null) continue;
                String lower = file.toLowerCase();
                if (!lower.endsWith(".tflite") && !lower.endsWith(".onnx")) {
                    continue;
                }

                JSONObject obj = new JSONObject();
                String name = file.replace(".tflite", "")
                        .replace(".onnx", "")
                        .replace('-', '_');
                obj.put("name", name);
                obj.put("asset", BASE_DIR + "/" + file);
                obj.put("threshold", lower.contains("glados") ? 0.60 : 0.55);
                array.put(obj);
            }
        } catch (IOException ioe) {
            Log.e(TAG, "Error listing hotword assets", ioe);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error building hotword assets JSON", e);
        }

        return array;
    }
}

