package com.chatai;

import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;
import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.Locale;

import com.chatai.hotword.HotwordAssetProvider;

public class WebAppInterface {
    private Context mContext;
    private SecureConfig secureConfig;
    private SecurityUtils securityUtils;
    private static final String TAG = "WebAppInterface";
    private static final String CHANNEL_ID = "chat_notifications";
    
    // Références aux serveurs (seront injectées depuis MainActivity)
    private static HttpServer httpServer;
    private static WebSocketServer webSocketServer;
    private static RealtimeAIService aiService;
    private static FileServer fileServer;
    
    // STT pour bouton micro webapp (Whisper uniquement, Google Speech utilise Intent standard)
    private com.chatai.audio.WhisperServerRecognizer webappWhisperRecognizer = null;

    public WebAppInterface(Context c, Object activity) {
        mContext = c;
        this.secureConfig = new SecureConfig(c);
        this.securityUtils = new SecurityUtils();
        createNotificationChannel();
    }
    
    /**
     * Méthode statique pour injecter les serveurs depuis MainActivity
     */
    public static void setServers(HttpServer http, WebSocketServer ws, RealtimeAIService ai, FileServer fs) {
        httpServer = http;
        webSocketServer = ws;
        aiService = ai;
        fileServer = fs;
        Log.i(TAG, "Serveurs injectés dans WebAppInterface");
    }

    // ========== NOTIFICATIONS PUSH ==========
    @JavascriptInterface
    public void showNotification(String message) {
        // Sécuriser le message avant affichage
        String safeMessage = SecurityUtils.sanitizeInput(message);
        Log.d(TAG, "Notification sécurisée: " + SecurityUtils.hashForLogging(safeMessage));
        
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(mContext, com.chatai.activities.KittActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Chat IA")
                    .setContentText(safeMessage)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0, 250, 250, 250});

            NotificationManager notificationManager = 
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(1, builder.build());
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Chat IA Notifications";
            String description = "Notifications pour les réponses de l'IA";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 250, 250});

            NotificationManager notificationManager = 
                mContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    // ========== SAUVEGARDE CONVERSATIONS ==========
    @JavascriptInterface
    public void saveConversation(String conversationJson) {
        Log.d(TAG, "Sauvegarde conversation: " + conversationJson);
        
        try {
            // Sauvegarder dans SharedPreferences (unifié avec le reste de l'app)
            SharedPreferences prefs = mContext.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            editor.putString("conversation_" + timestamp, conversationJson);
            editor.putString("last_conversation", conversationJson);
            editor.apply();
            
            // Sauvegarde dans un fichier (simplifiée)
            Log.d(TAG, "Conversation sauvegardée");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur sauvegarde: ", e);
        }
    }

    @JavascriptInterface
    public void openKittInterface() {
        try {
            Intent kittIntent = new Intent(mContext, com.chatai.activities.KittActivity.class);
            kittIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(kittIntent);
            Log.d(TAG, "Interface KITT lancée depuis WebView");
        } catch (Exception e) {
            Log.e(TAG, "Erreur lancement KITT depuis WebView: ", e);
        }
    }

    @JavascriptInterface
    public void openGameLibrary() {
        Log.i(TAG, "Demande d’ouverture GameLibrary depuis le WebApp");
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (mContext instanceof Activity) {
                    Activity activity = (Activity) mContext;
                    CharSequence[] options = new CharSequence[]{
                        "🎮 Bibliothèque locale",
                        "🌐 Interface web (EmulatorJS)"
                    };
                    new AlertDialog.Builder(activity)
                        .setTitle("GameLibrary")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                Intent intent = new Intent(activity, com.chatai.GameListActivity.class);
                                activity.startActivity(intent);
                                Log.i(TAG, "GameListActivity lancée");
                            } else {
                                Intent intent = new Intent(activity, com.chatai.activities.GameLibraryWebViewActivity.class);
                                activity.startActivity(intent);
                                Log.i(TAG, "GameLibraryWebViewActivity lancée");
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
                } else {
                    // Contexte non-Activity : fallback direct vers la bibliothèque locale
                    Intent intent = new Intent(mContext, com.chatai.GameListActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                    Log.w(TAG, "Contexte non-Activity, lancement direct de GameListActivity");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lancement GameLibrary", e);
                Toast.makeText(mContext, "Erreur ouverture GameLibrary: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @JavascriptInterface
    public String getLastConversation() {
        SharedPreferences prefs = mContext.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
        return prefs.getString("last_conversation", "[]");
    }

    // ========== AI CONFIG JSON (WebApp Editor) ==========
    @JavascriptInterface
    public String readAiConfigJson() {
        try {
            return AiConfigManager.readConfigJson(mContext);
        } catch (Exception e) {
            Log.e(TAG, "Error reading ai_config.json", e);
            return "";
        }
    }

    @JavascriptInterface
    public String writeAiConfigJson(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                return "Content is empty";
            }
            AiConfigManager.writeConfigJson(mContext, content);
            return "OK";
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Invalid JSON content", e);
            return "JSON error: " + e.getMessage();
        } catch (Exception e) {
            Log.e(TAG, "Error writing ai_config.json", e);
            return "Error: " + e.getMessage();
        }
    }

    @JavascriptInterface
    public String listHotwordAssets() {
        try {
            // Retourne directement un tableau JSON (compatibilité chat.js)
            JSONArray assets = HotwordAssetProvider.listAssets(mContext);
            return assets.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error listing hotword assets", e);
            return "[]";
        }
    }

    // ========== ACCÈS CAMÉRA ==========
    @JavascriptInterface
    public void openCamera() {
        Log.d(TAG, "Demande d'ouverture caméra - Fonctionnalité non disponible");
        Toast.makeText(mContext, "Caméra non disponible", Toast.LENGTH_SHORT).show();
    }

    // ========== ACCÈS FICHIERS ==========
    @JavascriptInterface
    public void openFileManager() {
        Log.d(TAG, "Ouverture gestionnaire de fichiers");
        try {
            // Ouvrir le sélecteur de répertoire pour choisir l'emplacement
            if (fileServer != null) {
                fileServer.openDirectoryPicker();
            } else {
                Toast.makeText(mContext, "Serveur de fichiers non initialisé", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur ouverture gestionnaire fichiers", e);
            Toast.makeText(mContext, "Erreur ouverture gestionnaire fichiers", Toast.LENGTH_SHORT).show();
        }
    }

    @JavascriptInterface
    public void openDocumentPicker() {
        Log.d(TAG, "Ouverture sélecteur de documents");
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            
            if (mContext instanceof android.app.Activity) {
                ((android.app.Activity) mContext).startActivityForResult(
                    Intent.createChooser(intent, "Sélectionner des documents"), 1002);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur ouverture sélecteur documents", e);
            Toast.makeText(mContext, "Erreur ouverture sélecteur documents", Toast.LENGTH_SHORT).show();
        }
    }

    @JavascriptInterface
    public void showRecentFiles() {
        Log.d(TAG, "Affichage fichiers récents");
        try {
            if (fileServer != null) {
                String storagePath = fileServer.getCurrentStoragePath();
                String message = "Fichiers récents dans: " + storagePath;
                Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
                
                // Optionnel: ouvrir l'explorateur de fichiers
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(android.net.Uri.parse("content://com.android.externalstorage.documents/root/primary"), "*/*");
                mContext.startActivity(intent);
            } else {
                Toast.makeText(mContext, "Serveur de fichiers non initialisé", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur affichage fichiers récents", e);
            Toast.makeText(mContext, "Erreur affichage fichiers récents", Toast.LENGTH_SHORT).show();
        }
    }

    // ========== UTILITAIRES ==========
    @JavascriptInterface
    public void showToast(String message) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        return "Android " + Build.VERSION.RELEASE + " - " + Build.MODEL;
    }

    // ========== SÉCURITÉ ==========
    
    /**
     * Fournit le token API de manière sécurisée
     * Vérifie SecureConfig (API token générique) puis Ollama Cloud API key
     */
    @JavascriptInterface
    public String getSecureApiToken() {
        Log.d(TAG, "getSecureApiToken() appelé - recherche de la clé API...");
        
        // 1. Vérifier SecureConfig (token API générique)
        Log.d(TAG, "Étape 1: Vérification token API générique dans SecureConfig...");
        if (secureConfig.hasApiToken()) {
            String token = secureConfig.getApiToken();
            Log.i(TAG, "Token API trouvé (générique SecureConfig, " + (token != null ? token.length() : 0) + " chars)");
            return token;
        }
        Log.d(TAG, "Aucun token API générique trouvé dans SecureConfig");
        
        // 2. Vérifier Ollama Cloud API key (unifié dans SecureConfig)
        // La méthode getOllamaCloudApiKey() fait automatiquement la migration depuis SharedPreferences
        Log.d(TAG, "Étape 2: Vérification clé Ollama Cloud dans SecureConfig...");
        String ollamaCloudKey = secureConfig.getOllamaCloudApiKey();
        if (ollamaCloudKey != null && !ollamaCloudKey.trim().isEmpty()) {
            Log.i(TAG, "Token API trouvé (Ollama Cloud via SecureConfig, " + ollamaCloudKey.length() + " chars)");
            return ollamaCloudKey.trim();
        }
        Log.d(TAG, "Aucune clé Ollama Cloud trouvée dans SecureConfig");
        
        // 3. Vérification supplémentaire: peut-être que la clé est dans SharedPreferences mais pas encore migrée
        // (au cas où la migration n'a pas été déclenchée)
        Log.d(TAG, "Étape 3: Vérification SharedPreferences pour migration...");
        try {
            SharedPreferences legacyPrefs = mContext.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
            String legacyKey = legacyPrefs.getString("ollama_cloud_api_key", null);
            boolean found = (legacyKey != null && !legacyKey.trim().isEmpty());
            Log.d(TAG, "SharedPreferences 'chatai_ai_config': clé trouvée = " + found + (found ? " (" + legacyKey.length() + " chars)" : ""));
            if (found) {
                Log.i(TAG, "Clé Ollama Cloud trouvée dans SharedPreferences (non migrée), migration automatique...");
                secureConfig.setOllamaCloudApiKey(legacyKey);
                return legacyKey.trim();
            }
            
            // Vérifier aussi dans le SharedPreferences par défaut (au cas où)
            SharedPreferences defaultPrefs = mContext.getSharedPreferences("com.chatai_preferences", Context.MODE_PRIVATE);
            String defaultKey = defaultPrefs.getString("ollama_cloud_api_key", null);
            boolean foundDefault = (defaultKey != null && !defaultKey.trim().isEmpty());
            Log.d(TAG, "SharedPreferences 'com.chatai_preferences': clé trouvée = " + foundDefault + (foundDefault ? " (" + defaultKey.length() + " chars)" : ""));
            if (foundDefault) {
                Log.i(TAG, "Clé Ollama Cloud trouvée dans SharedPreferences par défaut, migration automatique...");
                secureConfig.setOllamaCloudApiKey(defaultKey);
                return defaultKey.trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la vérification SharedPreferences: " + e.getMessage(), e);
        }
        
        // 4. Aucun token trouvé
        Log.w(TAG, "Aucun token API configuré - aucune clé trouvée dans SecureConfig ni SharedPreferences");
        return null;
    }

    /**
     * Configure le token API de manière sécurisée
     */
    @JavascriptInterface
    public void setSecureApiToken(String token) {
        if (SecurityUtils.isValidInput(token) && token.length() > 10) {
            secureConfig.setApiToken(token);
            Log.d(TAG, "Token API configuré de manière sécurisée");
        } else {
            Log.w(TAG, "Tentative de configuration de token invalide");
        }
    }

    /**
     * Génère un token temporaire sécurisé
     */
    @JavascriptInterface
    public String generateTempToken() {
        String tempToken = secureConfig.generateTempToken();
        Log.d(TAG, "Token temporaire généré");
        return tempToken;
    }

    /**
     * Valide une entrée utilisateur côté Android
     */
    @JavascriptInterface
    public boolean validateUserInput(String input) {
        boolean isValid = SecurityUtils.isValidInput(input);
        Log.d(TAG, "Validation entrée: " + isValid);
        return isValid;
    }

    /**
     * Sanitise une entrée utilisateur côté Android
     */
    @JavascriptInterface
    public String sanitizeUserInput(String input) {
        String sanitized = SecurityUtils.sanitizeInput(input);
        Log.d(TAG, "Entrée sanitizée: " + SecurityUtils.hashForLogging(sanitized));
        return sanitized;
    }

            /**
             * Sauvegarde sécurisée des conversations
             */
            @JavascriptInterface
            public void saveConversationSecure(String conversationJson) {
                // Valider le JSON avant sauvegarde
                if (SecurityUtils.isValidInput(conversationJson)) {
                    Log.d(TAG, "Sauvegarde conversation sécurisée");
                    saveConversation(conversationJson);
                } else {
                    Log.w(TAG, "Tentative de sauvegarde de conversation invalide");
                }
            }
            
            // ========== SERVICES HTTP ET IA ==========
            
            // ========== HOTWORD CONTROLS ==========
            @JavascriptInterface
            public void hotwordStart() {
                try {
                    Intent intent = new Intent(mContext, BackgroundService.class);
                    intent.setAction(BackgroundService.ACTION_HOTWORD_START);
                    mContext.startService(intent);
                    Log.i(TAG, "Hotword START via WebAppInterface");
                } catch (Exception e) {
                    Log.e(TAG, "hotwordStart error", e);
                }
            }

            @JavascriptInterface
            public void hotwordStop() {
                try {
                    Intent intent = new Intent(mContext, BackgroundService.class);
                    intent.setAction(BackgroundService.ACTION_HOTWORD_STOP);
                    mContext.startService(intent);
                    Log.i(TAG, "Hotword STOP via WebAppInterface");
                } catch (Exception e) {
                    Log.e(TAG, "hotwordStop error", e);
                }
            }

            @JavascriptInterface
            public void hotwordRestart() {
                try {
                    Intent intent = new Intent(mContext, BackgroundService.class);
                    intent.setAction(BackgroundService.ACTION_HOTWORD_RESTART);
                    mContext.startService(intent);
                    Log.i(TAG, "Hotword RESTART via WebAppInterface");
                } catch (Exception e) {
                    Log.e(TAG, "hotwordRestart error", e);
                }
            }

            // ========== STT (Whisper Server) OUTILS ==========
            @JavascriptInterface
            public boolean sttPing() {
                try {
                    Log.i(TAG, "sttPing: Démarrage");
                    com.chatai.audio.AudioEngineConfig cfg = com.chatai.audio.AudioEngineConfig.Companion.fromContext(mContext);
                    String url = cfg.getEndpoint();
                    if (url == null || url.trim().isEmpty()) {
                        Log.w(TAG, "sttPing: Endpoint is empty or null");
                        return false;
                    }
                    Log.d(TAG, "sttPing: Endpoint configuré = " + url);
                    
                    // Extraire l'URL de base (sans /inference)
                    String baseUrl;
                    if (url.contains("/inference")) {
                        baseUrl = url.substring(0, url.lastIndexOf("/inference"));
                    } else if (url.contains("/")) {
                        int lastSlash = url.lastIndexOf("/");
                        baseUrl = url.substring(0, lastSlash);
                    } else {
                        baseUrl = url;
                    }
                    Log.d(TAG, "sttPing: URL de base = " + baseUrl);
                    
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                            .build();
                    
                    // Essayer de pinger la racine
                    okhttp3.Request req = new okhttp3.Request.Builder()
                            .url(baseUrl)
                            .head()  // HEAD request au lieu de GET (plus léger)
                            .build();
                    
                    Log.d(TAG, "sttPing: Envoi HEAD request vers " + baseUrl);
                    try (okhttp3.Response resp = client.newCall(req).execute()) {
                        int code = resp.code();
                        Log.i(TAG, "sttPing: Response code " + code + " pour " + baseUrl);
                        // Accepter 200, 404 (serveur répond), ou 405 (Method Not Allowed = serveur actif)
                        boolean success = (code == 200 || code == 404 || code == 405);
                        if (success) {
                            Log.i(TAG, "sttPing: ✅ Serveur Whisper accessible");
                        } else {
                            Log.w(TAG, "sttPing: ❌ Serveur répond mais code inattendu: " + code);
                        }
                        return success;
                    }
                } catch (java.net.ConnectException e) {
                    Log.w(TAG, "sttPing: ❌ Connexion refusée - serveur probablement arrêté", e);
                    return false;
                } catch (java.net.SocketTimeoutException e) {
                    Log.w(TAG, "sttPing: ❌ Timeout - serveur ne répond pas", e);
                    return false;
                } catch (Exception e) {
                    Log.w(TAG, "sttPing: ❌ Erreur inattendue", e);
                    return false;
                }
            }

            @JavascriptInterface
            public void sttTestOnce() {
                try {
                    com.chatai.audio.AudioEngineConfig cfg = com.chatai.audio.AudioEngineConfig.Companion.fromContext(mContext);
                    String engine = cfg.getEngine();
                    
                    if ("whisper_server".equalsIgnoreCase(engine)) {
                        // === WHISPER SERVER ===
                        // ⭐ AMÉLIORATION : Vérifier la disponibilité avant d'utiliser Whisper
                        boolean whisperAvailable = sttPing();
                        
                        if (!whisperAvailable) {
                            // ⭐ FALLBACK AUTOMATIQUE : Whisper configuré mais serveur non disponible
                            Log.w(TAG, "⚠️ STT Test: Whisper Server configuré mais non disponible - Fallback vers Intent Google Speech standard");
                            showToast("Whisper non disponible - Utilisation de Google Speech");
                            
                            // Utiliser Intent Google Speech standard à la place
                            startGoogleSpeechActivity();
                            return;
                        }
                        
                        // Whisper est disponible → continuer avec Whisper
                        Log.i(TAG, "✅ STT Test: Whisper Server disponible - démarrage Whisper");
                        
                        // CRITIQUE: Arrêter Google Speech s'il est actif (il monopolise le microphone)
                        // Whisper et Google Speech ne peuvent PAS être actifs en même temps
                        Intent stopGoogleIntent = new Intent(mContext, BackgroundService.class);
                        stopGoogleIntent.setAction(BackgroundService.ACTION_STOP_GOOGLE_SPEECH);
                        mContext.startService(stopGoogleIntent);
                        Log.i(TAG, "STT Test (Whisper): Arrêt de Google Speech si actif");
                        
                        // Créer client OkHttp avec timeouts configurés (120s read, 150s call)
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                                .callTimeout(150, java.util.concurrent.TimeUnit.SECONDS)
                                .build();
                        com.chatai.audio.WhisperServerRecognizer rec = new com.chatai.audio.WhisperServerRecognizer(
                                cfg,
                                new com.chatai.audio.WhisperServerRecognizer.Callback() {
                                        @Override public void onReady() { Log.i(TAG, "STT Test (Whisper): ready"); }
                                        @Override public void onSpeechStart() { Log.i(TAG, "STT Test (Whisper): speech start"); }
                                    @Override public void onRmsChanged(float rmsDb) { /* no-op */ }
                                    @Override public void onResult(String text) {
                                            Log.i(TAG, "STT Test (Whisper) result: " + text);
                                        showToast("STT: " + text);
                                    }
                                    @Override public void onError(String message) {
                                            Log.e(TAG, "STT Test (Whisper) error: " + message);
                                        showToast("STT error: " + message);
                                    }
                                },
                                client
                        );
                        rec.startListening();
                    } else {
                        // === GOOGLE SPEECH VIA INTENT STANDARD ===
                        // ⭐ SIMPLIFICATION : Utilise Intent standard au lieu de SpeechRecognizer manuel
                        Log.i(TAG, "STT Test: Utilisation Intent Google Speech standard (même que bouton micro clavier)");
                        startGoogleSpeechActivity();
                        showToast("Reconnaissance vocale standard lancée (comme le clavier Google)");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "sttTestOnce error", e);
                    // ⭐ FALLBACK EN CAS D'ERREUR : Utiliser Intent Google Speech standard
                    try {
                        Log.w(TAG, "⚠️ Erreur lors du test STT - Fallback vers Intent Google Speech standard");
                        startGoogleSpeechActivity();
                        showToast("Erreur STT - Utilisation de Google Speech");
                    } catch (Exception fallbackError) {
                        Log.e(TAG, "Fallback Google Speech error", fallbackError);
                        showToast("STT test error: " + e.getMessage());
                    }
                }
            }
            // ========== STT (Whisper/Google Speech) POUR BOUTON MICRO WEBBAPP ==========
            /**
             * Vérifier si Whisper Server est disponible (pour bouton micro webapp)
             */
            @JavascriptInterface
            public boolean isWhisperAvailable() {
                try {
                    com.chatai.audio.AudioEngineConfig cfg = com.chatai.audio.AudioEngineConfig.Companion.fromContext(mContext);
                    String engine = cfg.getEngine();
                    
                    if ("whisper_server".equalsIgnoreCase(engine)) {
                        // Vérifier si le serveur Whisper répond
                        return sttPing();
                    } else {
                        // Google Speech : toujours disponible si SpeechRecognizer existe
                        return android.speech.SpeechRecognizer.isRecognitionAvailable(mContext);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "isWhisperAvailable error", e);
                    return false;
                }
            }
            
            /**
             * ⭐ SIMPLIFIÉ : Démarrer STT (Whisper ou Intent Google Speech standard)
             * Utilise Whisper si configuré ET disponible, sinon Intent Google Speech standard (comme le clavier Google)
             */
            @JavascriptInterface
            public void sttStartWhisper() {
                try {
                    com.chatai.audio.AudioEngineConfig cfg = com.chatai.audio.AudioEngineConfig.Companion.fromContext(mContext);
                    String engine = cfg.getEngine();
                    
                    Log.i(TAG, "sttStartWhisper: Engine=" + engine);
                    
                    if ("whisper_server".equalsIgnoreCase(engine)) {
                        // === WHISPER SERVER ===
                        // ⭐ AMÉLIORATION : Vérifier la disponibilité avant d'utiliser Whisper
                        boolean whisperAvailable = sttPing();
                        
                        if (!whisperAvailable) {
                            // ⭐ FALLBACK AUTOMATIQUE : Whisper configuré mais serveur non disponible
                            Log.w(TAG, "⚠️ Whisper Server configuré mais non disponible - Fallback vers Intent Google Speech standard");
                            
                            // Afficher un toast à l'utilisateur
                            if (mContext instanceof MainActivity) {
                                MainActivity activity = (MainActivity) mContext;
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    android.widget.Toast.makeText(
                                        mContext,
                                        "Whisper Server non disponible - Utilisation de Google Speech",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show();
                                });
                            }
                            
                            // Utiliser Intent Google Speech standard à la place
                            startGoogleSpeechActivity();
                            return;
                        }
                        
                        // Whisper est disponible → continuer avec Whisper
                        Log.i(TAG, "✅ Whisper Server disponible - démarrage Whisper");
                        
                        // CRITIQUE: Arrêter Google Speech s'il est actif (il monopolise le microphone)
                        Intent stopGoogleIntent = new Intent(mContext, BackgroundService.class);
                        stopGoogleIntent.setAction(BackgroundService.ACTION_STOP_GOOGLE_SPEECH);
                        mContext.startService(stopGoogleIntent);
                        Log.i(TAG, "sttStartWhisper (Whisper): Arrêt de Google Speech si actif");
                        
                        // Créer client OkHttp avec timeouts configurés
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                                .callTimeout(150, java.util.concurrent.TimeUnit.SECONDS)
                                .build();
                        
                        // Créer un recognizer Whisper temporaire pour le webapp
                        if (webappWhisperRecognizer != null) {
                            try {
                                webappWhisperRecognizer.stopListening();
                            } catch (Exception ignored) {}
                            webappWhisperRecognizer = null;
                        }
                        
                        webappWhisperRecognizer = new com.chatai.audio.WhisperServerRecognizer(
                                cfg,
                                new com.chatai.audio.WhisperServerRecognizer.Callback() {
                                    @Override public void onReady() {
                                        Log.i(TAG, "Webapp Whisper: ready");
                                        if (mContext instanceof MainActivity) {
                                            MainActivity activity = (MainActivity) mContext;
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                String jsCode = "if (window.onWhisperEvent) { window.onWhisperEvent('whisper_ready', ''); }";
                                                activity.getWebView().evaluateJavascript(jsCode, null);
                                            });
                                        }
                                    }
                                    @Override public void onSpeechStart() {
                                        Log.i(TAG, "Webapp Whisper: speech start");
                                        if (mContext instanceof MainActivity) {
                                            MainActivity activity = (MainActivity) mContext;
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                String jsCode = "if (window.onWhisperEvent) { window.onWhisperEvent('whisper_speech_start', ''); }";
                                                activity.getWebView().evaluateJavascript(jsCode, null);
                                            });
                                        }
                                    }
                                    @Override public void onRmsChanged(float rmsDb) {
                                        if (mContext instanceof MainActivity) {
                                            MainActivity activity = (MainActivity) mContext;
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                String jsCode = "if (window.onWhisperEvent) { window.onWhisperEvent('whisper_rms', '" + rmsDb + "'); }";
                                                activity.getWebView().evaluateJavascript(jsCode, null);
                                            });
                                        }
                                    }
                                    @Override public void onResult(String text) {
                                        Log.i(TAG, "Webapp Whisper: result=" + text);
                                        if (webappWhisperRecognizer != null) {
                                            try {
                                                webappWhisperRecognizer.stopListening();
                                            } catch (Exception ignored) {}
                                            webappWhisperRecognizer = null;
                                        }
                                        if (mContext instanceof MainActivity) {
                                            MainActivity activity = (MainActivity) mContext;
                                            final String safeText = text.replace("'", "\\'").replace("\n", "\\n");
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                String jsCode = "if (window.onWhisperEvent) { window.onWhisperEvent('whisper_transcription', '" + safeText + "'); }";
                                                activity.getWebView().evaluateJavascript(jsCode, null);
                                            });
                                        }
                                    }
                                    @Override public void onError(String message) {
                                        Log.e(TAG, "Webapp Whisper: error=" + message);
                                        if (webappWhisperRecognizer != null) {
                                            try {
                                                webappWhisperRecognizer.stopListening();
                                            } catch (Exception ignored) {}
                                            webappWhisperRecognizer = null;
                                        }
                                        if (mContext instanceof MainActivity) {
                                            MainActivity activity = (MainActivity) mContext;
                                            final String safeMsg = message.replace("'", "\\'").replace("\n", "\\n");
                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                String jsCode = "if (window.onWhisperEvent) { window.onWhisperEvent('whisper_error', '" + safeMsg + "'); }";
                                                activity.getWebView().evaluateJavascript(jsCode, null);
                                            });
                                        }
                                    }
                                },
                                client
                        );
                        webappWhisperRecognizer.startListening();
                        Log.i(TAG, "✅ Webapp Whisper: started");
                    } else {
                        // === GOOGLE SPEECH VIA INTENT STANDARD ===
                        // ⭐ SIMPLIFICATION : Utilise Intent standard (comme le clavier Google) au lieu de SpeechRecognizer manuel
                        Log.i(TAG, "sttStartWhisper: Utilisation Intent Google Speech standard (engine=" + engine + ")");
                        startGoogleSpeechActivity();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "sttStartWhisper error", e);
                    // ⭐ FALLBACK EN CAS D'ERREUR : Utiliser Intent Google Speech standard
                    try {
                        Log.w(TAG, "⚠️ Erreur lors du démarrage STT - Fallback vers Intent Google Speech standard");
                        startGoogleSpeechActivity();
                    } catch (Exception fallbackError) {
                        Log.e(TAG, "Fallback Google Speech error", fallbackError);
                    }
                }
            }
            
            /**
             * ⭐ NOUVEAU : Démarrer le serveur Whisper via Termux
             * Envoie un Intent à Termux pour exécuter la commande whisper-server
             */
            @JavascriptInterface
            public void startWhisperServer() {
                try {
                    Log.i(TAG, "startWhisperServer: Envoi Intent à Termux");
                    
                    // Créer Intent pour Termux RUN_COMMAND
                    Intent intent = new Intent();
                    intent.setAction("com.termux.RUN_COMMAND");
                    intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
                    intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{
                        "-c",
                        "./whisper.cpp/build/bin/whisper-server -m /sdcard/ChatAI-Files/models/whisper/ggml-small.bin --port 11400 --host 127.0.0.1 -l fr -t 4"
                    });
                    intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
                    intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true); // En arrière-plan
                    intent.putExtra("com.termux.RUN_COMMAND_SESSION_NAME", "whisper-server");
                    intent.setClassName("com.termux", "com.termux.app.RunCommandService");
                    
                    try {
                        mContext.startService(intent);
                        Log.i(TAG, "✅ Intent Termux envoyé - Whisper Server démarré");
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(mContext, "Whisper Server démarré via Termux", Toast.LENGTH_SHORT).show();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Erreur lors de l'envoi de l'Intent Termux", e);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(mContext, "Erreur: Termux non disponible ou permission refusée", Toast.LENGTH_LONG).show();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "startWhisperServer error", e);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(mContext, "Erreur: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
            
            /**
             * ⭐ NOUVEAU : Démarre l'Activity Google Speech standard (comme le clavier Google)
             * Utilise startActivityForResult() avec RecognizerIntent - beaucoup plus simple que SpeechRecognizer manuel
             */
            private void startGoogleSpeechActivity() {
                try {
                    // Vérifier si Google Speech est disponible
                    if (!SpeechRecognizer.isRecognitionAvailable(mContext)) {
                        Log.e(TAG, "Google Speech non disponible");
                        Toast.makeText(mContext, "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    // Arrêter Whisper s'il est actif (il monopolise le microphone)
                    Intent stopWhisperIntent = new Intent(mContext, BackgroundService.class);
                    stopWhisperIntent.setAction(BackgroundService.ACTION_STOP_WHISPER);
                    mContext.startService(stopWhisperIntent);
                    Log.i(TAG, "Arrêt de Whisper pour libérer le microphone");
                    
                    // Créer Intent standard Google Speech (comme le clavier Google)
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH);
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez...");
                    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                    
                    // Lancer l'Activity standard Google Speech
                    if (mContext instanceof MainActivity) {
                        MainActivity activity = (MainActivity) mContext;
                        activity.startActivityForResult(intent, MainActivity.REQUEST_SPEECH_RECOGNITION);
                        Log.i(TAG, "✅ Intent Google Speech standard lancé");
                    } else {
                        Log.e(TAG, "Context n'est pas MainActivity, impossible de lancer l'Intent");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "startGoogleSpeechActivity error", e);
                    Toast.makeText(mContext, "Erreur lors du lancement de la reconnaissance vocale", Toast.LENGTH_SHORT).show();
                }
            }
            
            /**
             * ⭐ SIMPLIFIÉ : Arrêter Whisper Server (pour bouton micro webapp)
             * Note: Google Speech via Intent standard n'a pas besoin d'être arrêté (géré par Android)
             */
            @JavascriptInterface
            public void sttStopWhisper() {
                try {
                    com.chatai.audio.AudioEngineConfig cfg = com.chatai.audio.AudioEngineConfig.Companion.fromContext(mContext);
                    String engine = cfg.getEngine();
                    
                    if ("whisper_server".equalsIgnoreCase(engine)) {
                        // Arrêter Whisper
                        if (webappWhisperRecognizer != null) {
                            try {
                                webappWhisperRecognizer.stopListening();
                                Log.i(TAG, "✅ Webapp Whisper: stopped");
                            } catch (Exception e) {
                                Log.w(TAG, "Error stopping webapp Whisper: " + e.getMessage());
                            }
                            webappWhisperRecognizer = null;
                        }
                    }
                    // Note: Google Speech via Intent standard n'a pas besoin d'être arrêté manuellement
                    // L'utilisateur peut simplement fermer l'Activity standard
                } catch (Exception e) {
                    Log.e(TAG, "sttStopWhisper error", e);
                }
            }
            
            /**
             * Obtient l'URL du serveur HTTP local
             */
            @JavascriptInterface
            public String getHttpServerUrl() {
                if (httpServer != null && httpServer.isRunning()) {
                    String url = "http://localhost:" + httpServer.getPort();
                    Log.d(TAG, "URL serveur HTTP: " + url);
                    return url;
                } else {
                    Log.w(TAG, "Serveur HTTP non disponible");
                    return null;
                }
            }
            
            /**
             * Fait une requête HTTP vers le serveur local
             */
            @JavascriptInterface
            public void makeHttpRequest(String endpoint, String method, String data) {
                Log.d(TAG, "Requête HTTP: " + method + " " + endpoint);
                // TODO: Implémenter la requête HTTP asynchrone
                // Pour l'instant, on log juste la requête
            }
            
            /**
             * Obtient les statistiques du service IA
             */
            @JavascriptInterface
            public String getAIServiceStats() {
                if (aiService != null && aiService.isHealthy()) {
                    String stats = "{\"status\":\"healthy\",\"service\":\"RealtimeAIService\"}";
                    Log.d(TAG, "Stats service IA: " + stats);
                    return stats;
                } else {
                    String stats = "{\"status\":\"not_available\"}";
                    Log.d(TAG, "Stats service IA: " + stats);
                    return stats;
                }
            }
            
            /**
             * Traite une requête IA en temps réel
             */
            @JavascriptInterface
            public void processAIRequestRealtime(String message, String personality) {
                Log.d(TAG, "Traitement requête IA temps réel: " + message);
                
                if (aiService != null && aiService.isHealthy()) {
                    aiService.processAIRequest(message, personality)
                        .thenAccept(response -> {
                            Log.d(TAG, "Réponse IA reçue: " + response);
                            
                            // Afficher la réponse directement dans le JavaScript
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (mContext instanceof MainActivity) {
                                    MainActivity activity = (MainActivity) mContext;
                                    String jsCode = String.format(
                                        "if (window.secureChatApp && window.secureChatApp.showSecureMessage) { " +
                                        "window.secureChatApp.showSecureMessage('ai', %s); }",
                                        escapeForJavaScript(response)
                                    );
                                    activity.getWebView().evaluateJavascript(jsCode, null);
                                }
                            });
                        })
                        .exceptionally(throwable -> {
                            Log.e(TAG, "Erreur traitement IA: ", throwable);
                            // Afficher un message d'erreur
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (mContext instanceof MainActivity) {
                                    MainActivity activity = (MainActivity) mContext;
                                    String jsCode = "if (window.secureChatApp && window.secureChatApp.showSecureMessage) { " +
                                        "window.secureChatApp.showSecureMessage('ai', 'Oups ! Une erreur est survenue.'); }";
                                    activity.getWebView().evaluateJavascript(jsCode, null);
                                }
                            });
                            return null;
                        });
                } else {
                    Log.w(TAG, "Service IA non disponible");
                    // Afficher un message indiquant que le service n'est pas disponible
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (mContext instanceof MainActivity) {
                            MainActivity activity = (MainActivity) mContext;
                            String jsCode = "if (window.secureChatApp && window.secureChatApp.showSecureMessage) { " +
                                "window.secureChatApp.showSecureMessage('ai', 'Service IA non disponible pour le moment.'); }";
                            activity.getWebView().evaluateJavascript(jsCode, null);
                        }
                    });
                }
            }
            
            /**
             * Échappe une chaîne pour l'utiliser dans JavaScript
             */
            private String escapeForJavaScript(String input) {
                return "'" + input.replace("\\", "\\\\")
                                   .replace("'", "\\'")
                                   .replace("\"", "\\\"")
                                   .replace("\n", "\\n")
                                   .replace("\r", "\\r") + "'";
            }
            
            /**
             * Obtient le nombre de clients WebSocket connectés
             */
            @JavascriptInterface
            public int getWebSocketClientsCount() {
                if (webSocketServer != null && webSocketServer.isRunning()) {
                    int count = webSocketServer.getConnectedClientsCount();
                    Log.d(TAG, "Clients WebSocket connectés: " + count);
                    return count;
                } else {
                    Log.w(TAG, "Serveur WebSocket non disponible");
                    return 0;
                }
            }
            
            /**
             * Affiche directement une réponse IA dans l'interface
             */
            @JavascriptInterface
            public void showAIResponse(String message) {
                Log.d(TAG, "Affichage réponse IA: " + message);
                // Cette méthode sera appelée par le service IA quand une réponse est prête
            }
            
            /**
             * Obtient la liste des plugins disponibles
             */
            @JavascriptInterface
            public String getAvailablePlugins() {
                return "{\"plugins\":[\"translator\",\"calculator\",\"weather\",\"camera\",\"files\",\"jokes\",\"tips\"]}";
            }
            
            // ========== NAVIGATION VERS AUTRES ACTIVITÉS ==========
            
            /**
             * Ouvre l'activité des paramètres
             */
            @JavascriptInterface
            public void openSettingsActivity() {
                Intent intent = new Intent(mContext, SettingsActivity.class);
                mContext.startActivity(intent);
                Log.d(TAG, "Ouverture SettingsActivity");
            }
            
            /**
             * Ouvre l'activité de la base de données
             */
            @JavascriptInterface
            public void openDatabaseActivity() {
                Intent intent = new Intent(mContext, DatabaseActivity.class);
                mContext.startActivity(intent);
                Log.d(TAG, "Ouverture DatabaseActivity");
            }
            
            /**
             * Ouvre l'activité de monitoring des serveurs
             */
            @JavascriptInterface
            public void openServerActivity() {
                Intent intent = new Intent(mContext, ServerActivity.class);
                mContext.startActivity(intent);
                Log.d(TAG, "Ouverture ServerActivity");
            }
            
            // ========== THINKING MODE & BIDIRECTIONAL BRIDGE ==========
            
            /**
             * Traite une requête utilisateur avec mode thinking
             * Streame les chunks (thinking + réponse) vers l'interface web
             */
            @JavascriptInterface
            public void processWithThinking(String userInput, String personality, boolean enableThinking) {
                Log.i(TAG, "Processing with thinking: " + userInput + " (personality=" + personality + ", thinking=" + enableThinking + ")");
                
                // Vérifier que le contexte est bien MainActivity
                if (!(mContext instanceof MainActivity)) {
                    Log.e(TAG, "Context is not MainActivity, cannot process with thinking");
                    return;
                }
                
                MainActivity activity = (MainActivity) mContext;
                
                // Obtenir le bridge bidirectionnel
                com.chatai.services.BidirectionalBridge bridge = 
                    com.chatai.services.BidirectionalBridge.getInstance(mContext);
                
                // Générer un ID unique pour ce message
                String messageId = "msg_" + System.currentTimeMillis();
                
                // Traiter avec thinking (méthode Async Java-friendly)
                bridge.processWithThinkingAsync(
                    userInput,
                    personality,
                    enableThinking,
                    // onChunk callback (Consumer)
                    chunk -> {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            String chunkType = chunk.getType().name().toLowerCase();
                            String content = chunk.getContent();
                            boolean isComplete = chunk.isComplete();
                            
                            String jsCode = String.format(
                                "if (window.secureChatApp && window.secureChatApp.displayThinkingChunk) { " +
                                "window.secureChatApp.displayThinkingChunk('%s', '%s', %s, %s); }",
                                messageId,
                                chunkType,
                                escapeForJavaScript(content),
                                isComplete
                            );
                            
                            activity.getWebView().evaluateJavascript(jsCode, null);
                            Log.d(TAG, "Chunk sent: type=" + chunkType + ", complete=" + isComplete);
                        });
                    },
                    // onError callback (Consumer)
                    error -> {
                        Log.e(TAG, "Error processing with thinking", error);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            String jsCode = "if (window.secureChatApp && window.secureChatApp.showSecureMessage) { " +
                                "window.secureChatApp.showSecureMessage('ai', 'Erreur: " + error.getMessage() + "'); }";
                            activity.getWebView().evaluateJavascript(jsCode, null);
                        });
                    },
                    // onComplete callback (Runnable)
                    () -> {
                        Log.i(TAG, "Thinking stream completed");
                    }
                );
            }
            
            /**
             * Vérifie si le mode thinking est activé dans les paramètres
             */
            @JavascriptInterface
            public boolean getThinkingModeEnabled() {
                SharedPreferences prefs = mContext.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
                boolean enabled = prefs.getBoolean("thinking_mode_enabled", true);
                Log.d(TAG, "Thinking mode enabled: " + enabled);
                return enabled;
            }
            
            /**
             * Active ou désactive le mode thinking
             */
            @JavascriptInterface
            public void setThinkingModeEnabled(boolean enabled) {
                SharedPreferences prefs = mContext.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("thinking_mode_enabled", enabled).apply();
                Log.i(TAG, "Thinking mode set to: " + enabled);
                
                // Notifier l'interface web
                if (mContext instanceof MainActivity) {
                    MainActivity activity = (MainActivity) mContext;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        String jsCode = "if (window.secureChatApp && window.secureChatApp.showSecureMessage) { " +
                            "window.secureChatApp.showSecureMessage('ai', 'Mode thinking " + 
                            (enabled ? "activé" : "désactivé") + " 🧠'); }";
                        activity.getWebView().evaluateJavascript(jsCode, null);
                    });
                }
            }
            
            /**
             * Envoie un message de KITT vers ChatAI via le bridge bidirectionnel
             */
            @JavascriptInterface
            public void sendKittToChatAI(String message, String messageType) {
                Log.i(TAG, "KITT → ChatAI: " + message + " (type=" + messageType + ")");
                
                try {
                    com.chatai.services.BidirectionalBridge bridge = 
                        com.chatai.services.BidirectionalBridge.getInstance(mContext);
                    
                    com.chatai.services.BidirectionalBridge.MessageType type = 
                        com.chatai.services.BidirectionalBridge.MessageType.valueOf(messageType.toUpperCase());
                    
                    com.chatai.services.BidirectionalBridge.BridgeMessage bridgeMessage = 
                        new com.chatai.services.BidirectionalBridge.BridgeMessage(
                            type,
                            com.chatai.services.BidirectionalBridge.Source.KITT_VOICE,
                            message,
                            new java.util.HashMap<>(),
                            System.currentTimeMillis()
                        );
                    
                    bridge.sendKittToWebAsync(bridgeMessage);
                    Log.d(TAG, "Message sent via bridge");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message via bridge", e);
                }
            }
            
            /**
             * Envoie un message de ChatAI vers KITT via le bridge bidirectionnel
             */
            @JavascriptInterface
            public void sendChatAIToKitt(String message, String messageType) {
                Log.i(TAG, "ChatAI → KITT: " + message + " (type=" + messageType + ")");
                
                try {
                    com.chatai.services.BidirectionalBridge bridge = 
                        com.chatai.services.BidirectionalBridge.getInstance(mContext);
                    
                    com.chatai.services.BidirectionalBridge.MessageType type = 
                        com.chatai.services.BidirectionalBridge.MessageType.valueOf(messageType.toUpperCase());
                    
                    com.chatai.services.BidirectionalBridge.BridgeMessage bridgeMessage = 
                        new com.chatai.services.BidirectionalBridge.BridgeMessage(
                            type,
                            com.chatai.services.BidirectionalBridge.Source.CHATAI_WEB,
                            message,
                            new java.util.HashMap<>(),
                            System.currentTimeMillis()
                        );
                    
                    bridge.sendWebToKittAsync(bridgeMessage);
                    Log.d(TAG, "Message sent via bridge");
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error sending message via bridge", e);
                }
            }
}