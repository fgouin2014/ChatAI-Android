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
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
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
                    } else if ("legacy_google".equalsIgnoreCase(engine)) {
                        // === GOOGLE SPEECH ===
                        // CRITIQUE: Arrêter Whisper s'il est actif (il monopolise le microphone)
                        // Whisper et Google Speech ne peuvent PAS être actifs en même temps
                        Intent stopWhisperIntent = new Intent(mContext, BackgroundService.class);
                        stopWhisperIntent.setAction(BackgroundService.ACTION_STOP_WHISPER);
                        mContext.startService(stopWhisperIntent);
                        Log.i(TAG, "STT Test (Google Speech): Arrêt de Whisper si actif");
                        
                        // SpeechRecognizer doit être créé et utilisé depuis le main thread
                        new Handler(Looper.getMainLooper()).post(() -> {
                            SpeechRecognizer recognizer = null;
                            Handler timeoutHandler = null;
                            Runnable timeoutRunnable = null;
                            try {
                                // Vérifier si Google Speech est disponible
                                if (!SpeechRecognizer.isRecognitionAvailable(mContext)) {
                                    Log.e(TAG, "STT Test (Google Speech): Google Speech non disponible sur ce device");
                                    showToast("Google Speech non disponible");
                                    return;
                                }
                                
                                recognizer = SpeechRecognizer.createSpeechRecognizer(mContext);
                                if (recognizer == null) {
                                    Log.e(TAG, "STT Test (Google Speech): SpeechRecognizer.createSpeechRecognizer() retourne null");
                                    showToast("Google Speech non disponible (null)");
                                    return;
                                }
                                
                                Log.i(TAG, "STT Test (Google Speech): SpeechRecognizer créé avec succès");
                                
                                // Créer le listener avec référence au recognizer et contexte
                                GoogleSpeechTestListener listener = new GoogleSpeechTestListener(recognizer, mContext);
                                
                                // TIMEOUT: Forcer l'arrêt après 12 secondes si aucun résultat
                                timeoutHandler = new Handler(Looper.getMainLooper());
                                final SpeechRecognizer finalRecognizer = recognizer;
                                final GoogleSpeechTestListener finalListener = listener;
                                timeoutRunnable = () -> {
                                    if (finalRecognizer != null && !finalListener.isDestroyed()) {
                                        Log.w(TAG, "STT Test (Google Speech): timeout global (12s) - arrêt forcé");
                                        try {
                                            finalRecognizer.stopListening();
                                        } catch (Throwable ignored) {}
                                        try {
                                            finalRecognizer.destroy();
                                            finalListener.setDestroyed(true);
                                        } catch (Throwable ignored) {}
                                        showToast("STT test timeout (12s)");
                                    }
                                };
                                timeoutHandler.postDelayed(timeoutRunnable, 12000); // 12 secondes maximum
                                
                                // Stocker la référence au timeout dans le listener pour annulation
                                listener.setTimeoutHandler(timeoutHandler);
                                listener.setTimeoutRunnable(timeoutRunnable);
                                
                                recognizer.setRecognitionListener(listener);
                                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH);
                                intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mContext.getPackageName());
                                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                                // Note: EXTRA_PROMPT non utilisé avec SpeechRecognizer (seulement pour startActivityForResult)
                                recognizer.startListening(intent);
                                Log.i(TAG, "STT Test (Google Speech): started (timeout global: 12s, après parole: 7s)");
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to start Google Speech test", e);
                                showToast("Erreur Google Speech: " + e.getMessage());
                                // Nettoyer en cas d'erreur
                                if (timeoutHandler != null && timeoutRunnable != null) {
                                    timeoutHandler.removeCallbacks(timeoutRunnable);
                                }
                                if (recognizer != null) {
                                    try { recognizer.destroy(); } catch (Throwable ignored) {}
                                }
                            }
                        });
                    } else {
                        showToast("Engine STT inconnu: " + engine);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "sttTestOnce error", e);
                    showToast("STT test error: " + e.getMessage());
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
    
    /**
     * RecognitionListener pour test Google Speech (classe interne statique pour éviter problèmes KSP)
     */
    private static class GoogleSpeechTestListener implements RecognitionListener {
        private final SpeechRecognizer recognizer;
        private final Context context;
        private Handler timeoutHandler;
        private Runnable timeoutRunnable;
        private Runnable speechTimeoutRunnable;
        private boolean destroyed = false;
        
        GoogleSpeechTestListener(SpeechRecognizer recognizer, Context context) {
            this.recognizer = recognizer;
            this.context = context;
        }
        
        void setTimeoutHandler(Handler handler) {
            this.timeoutHandler = handler;
        }
        
        void setTimeoutRunnable(Runnable runnable) {
            this.timeoutRunnable = runnable;
        }
        
        boolean isDestroyed() {
            return destroyed;
        }
        
        void setDestroyed(boolean destroyed) {
            this.destroyed = destroyed;
        }
        
        private void cleanup() {
            if (destroyed) {
                return; // Déjà nettoyé
            }
            destroyed = true;
            
            // Annuler tous les timeouts
            if (timeoutHandler != null) {
                if (timeoutRunnable != null) {
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                }
                if (speechTimeoutRunnable != null) {
                    timeoutHandler.removeCallbacks(speechTimeoutRunnable);
                }
            }
            
            // Détruire le recognizer
            if (recognizer != null) {
                try {
                    recognizer.stopListening();
                } catch (Throwable ignored) {}
                try {
                    recognizer.destroy();
                    Log.i("WebAppInterface", "STT Test (Google Speech): recognizer destroyed");
                } catch (Throwable e) {
                    Log.w("WebAppInterface", "STT Test (Google Speech): error destroying recognizer", e);
                }
            }
        }
        
        @Override
        public void onReadyForSpeech(android.os.Bundle params) {
            Log.i("WebAppInterface", "STT Test (Google Speech): ready - microphone accessible");
            // Si onReadyForSpeech est appelé, le microphone est accessible
            // Cela signifie qu'aucun autre processus (comme Whisper avec AudioRecord) ne le monopolise
        }
        
        @Override
        public void onBeginningOfSpeech() {
            Log.i("WebAppInterface", "STT Test (Google Speech): speech start");
            // TIMEOUT: Si aucun résultat après 12 secondes depuis le début de la parole, forcer l'arrêt
            // (augmenté de 7s à 12s car Google Speech peut prendre plus de temps pour traiter)
            if (timeoutHandler != null && !destroyed) {
                final GoogleSpeechTestListener self = this;
                speechTimeoutRunnable = () -> {
                    if (!self.isDestroyed()) {
                        Log.w("WebAppInterface", "STT Test (Google Speech): timeout après début de parole (12s) - arrêt forcé");
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(context, "STT: Timeout après parole (12s)", Toast.LENGTH_SHORT).show();
                        });
                        cleanup();
                    }
                };
                timeoutHandler.postDelayed(speechTimeoutRunnable, 12000); // 12 secondes après début de parole
            }
        }
        
        @Override
        public void onRmsChanged(float rmsDb) {
            // Log RMS toutes les 50 fois pour diagnostic
            // RMS > -30 dB = parole audible, RMS > -10 dB = parole forte
            if ((int)(rmsDb * 10) % 50 == 0) {
                String level = rmsDb > -10 ? "FORT" : (rmsDb > -30 ? "NORMAL" : "FAIBLE");
                Log.i("WebAppInterface", "🎤 STT Test (Google Speech) RMS: " + String.format("%.1f", rmsDb) + " dB (" + level + ")");
            }
        }
        
        @Override
        public void onBufferReceived(byte[] buffer) {
            // Log buffer reçu (confirme que microphone envoie des données)
            Log.d("WebAppInterface", "📡 STT Test (Google Speech) buffer received: " + buffer.length + " bytes");
        }
        
        @Override
        public void onEndOfSpeech() {
            Log.i("WebAppInterface", "STT Test (Google Speech): speech end");
        }
        
        @Override
        public void onPartialResults(android.os.Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.i("WebAppInterface", "✅ STT Test (Google Speech) partial result: '" + text + "' (Google Speech VOUS ENTEND!)");
                // Afficher aussi dans un Toast pour feedback visuel immédiat
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "STT partiel: " + text, Toast.LENGTH_SHORT).show();
                });
            } else {
                Log.d("WebAppInterface", "STT Test (Google Speech) partial results: aucun match (Google Speech traite mais ne reconnaît pas encore)");
            }
        }
        
        @Override
        public void onError(int error) {
            final String errorMsg;
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO: 
                    errorMsg = "Audio error (microphone peut-être monopolisé par Whisper)"; 
                    break;
                case SpeechRecognizer.ERROR_CLIENT: errorMsg = "Client error"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: errorMsg = "Insufficient permissions"; break;
                case SpeechRecognizer.ERROR_NETWORK: errorMsg = "Network error"; break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: errorMsg = "Network timeout"; break;
                case SpeechRecognizer.ERROR_NO_MATCH: errorMsg = "No match"; break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: 
                    errorMsg = "Recognizer busy (peut-être que Whisper monopolise le microphone)"; 
                    break;
                case SpeechRecognizer.ERROR_SERVER: errorMsg = "Server error"; break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: errorMsg = "Speech timeout"; break;
                default: errorMsg = "Unknown error"; break;
            }
            Log.e("WebAppInterface", "STT Test (Google Speech) error: " + errorMsg + " (" + error + ")");
            if (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "STT error: " + errorMsg + " - Arrêtez Whisper si actif", Toast.LENGTH_LONG).show();
                });
            }
            cleanup();
        }
        
        @Override
        public void onResults(android.os.Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.i("WebAppInterface", "STT Test (Google Speech) result: " + text + " (matches: " + matches.size() + ")");
                // Afficher le résultat à l'utilisateur (comme pour Whisper)
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "STT: " + text, Toast.LENGTH_LONG).show();
                });
            } else {
                Log.w("WebAppInterface", "STT Test (Google Speech): no matches");
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "STT: Aucun résultat", Toast.LENGTH_SHORT).show();
                });
            }
            cleanup();
        }
        
        @Override
        public void onEvent(int eventType, android.os.Bundle params) {
            // no-op
        }
    }
}