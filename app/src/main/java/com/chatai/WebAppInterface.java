package com.chatai;

import android.content.Context;
import android.content.Intent;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;
import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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
            // Sauvegarder dans SharedPreferences
            SharedPreferences prefs = mContext.getSharedPreferences("ChatAI", Context.MODE_PRIVATE);
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
    public String getLastConversation() {
        SharedPreferences prefs = mContext.getSharedPreferences("ChatAI", Context.MODE_PRIVATE);
        return prefs.getString("last_conversation", "[]");
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
     */
    @JavascriptInterface
    public String getSecureApiToken() {
        if (secureConfig.hasApiToken()) {
            Log.d(TAG, "Token API fourni de manière sécurisée");
            return secureConfig.getApiToken();
        } else {
            Log.w(TAG, "Aucun token API configuré");
            return null;
        }
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
}