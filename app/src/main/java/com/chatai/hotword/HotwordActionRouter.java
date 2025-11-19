package com.chatai.hotword;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.chatai.AiConfigManager;
import com.chatai.BackgroundService;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Routes hotword detections to configured actions without altering legacy behavior.
 * Default: respond_ai_outside_kitt (non-intrusive).
 */
public class HotwordActionRouter {

    private static final String TAG = "HotwordRouter";

    public enum Action {
        RESPOND_AI_OUTSIDE_KITT("respond_ai_outside_kitt"),
        OPEN_KITT_UI("open_kitt_ui");

        public final String id;
        Action(String id) { this.id = id; }
        public static Action from(String id) {
            if (id == null) return RESPOND_AI_OUTSIDE_KITT;
            String norm = id.toLowerCase(Locale.ROOT).trim();
            for (Action a : values()) {
                if (a.id.equals(norm)) return a;
            }
            return RESPOND_AI_OUTSIDE_KITT;
        }
    }

    private final Context context;
    private final Map<String, Action> keywordToAction = new HashMap<>();
    private Action defaultAction;

    public HotwordActionRouter(Context context) {
        this.context = context.getApplicationContext();
        this.defaultAction = Action.RESPOND_AI_OUTSIDE_KITT;
        loadConfig();
    }

    private void loadConfig() {
        try {
            JSONObject cfg = AiConfigManager.loadConfig(context);
            if (cfg == null) return;
            JSONObject hotword = cfg.optJSONObject("hotword");
            if (hotword == null) return;
            // Default communication mode
            String commDefault = hotword.optString("commModeDefault", Action.RESPOND_AI_OUTSIDE_KITT.id);
            this.defaultAction = Action.from(commDefault);
            JSONObject actions = hotword.optJSONObject("actions");
            if (actions == null) return;
            Iterator<String> keys = actions.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = actions.optString(key, defaultAction.id);
                if (!TextUtils.isEmpty(key)) {
                    keywordToAction.put(key.toLowerCase(Locale.ROOT), Action.from(val));
                }
            }
            Log.i(TAG, "Loaded actions mapping: " + keywordToAction);
        } catch (Exception e) {
            Log.e(TAG, "Error loading hotword actions", e);
        }
    }

    public void route(String keyword) {
        String k = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        Action action = keywordToAction.getOrDefault(k, defaultAction);
        Log.i(TAG, "Routing hotword: " + keyword + " -> " + action.id);
        perform(action, keyword);
    }

    private void perform(Action action, String keyword) {
        try {
            switch (action) {
                case OPEN_KITT_UI: {
                    Intent intent = new Intent(context, com.chatai.activities.KittActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    break;
                }
                case RESPOND_AI_OUTSIDE_KITT:
                default: {
                    // Non-intrusif: déléguer au BackgroundService pour réponse IA hors UI
                    Intent svc = new Intent(context, BackgroundService.class);
                    svc.setAction("com.chatai.action.AI_RESPOND");
                    svc.putExtra("hotword_keyword", keyword);
                    context.startService(svc);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error performing action " + action.id + " for " + keyword, e);
        }
    }
}


