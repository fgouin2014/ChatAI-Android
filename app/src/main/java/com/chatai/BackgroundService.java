package com.chatai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Service en arrière-plan pour maintenir les serveurs actifs
 */
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "ChatAI_Background_Service";
    // Actions pour contrôle via Intent
    public static final String ACTION_HOTWORD_START = "com.chatai.action.HOTWORD_START";
    public static final String ACTION_HOTWORD_STOP = "com.chatai.action.HOTWORD_STOP";
    public static final String ACTION_HOTWORD_RESTART = "com.chatai.action.HOTWORD_RESTART";
    public static final String ACTION_SERVERS_RESTART = "com.chatai.action.SERVERS_RESTART";
    public static final String ACTION_AI_RESPOND = "com.chatai.action.AI_RESPOND";
    public static final String ACTION_STOP_WHISPER = "com.chatai.action.STOP_WHISPER";
    public static final String ACTION_STOP_GOOGLE_SPEECH = "com.chatai.action.STOP_GOOGLE_SPEECH";
    
    private final IBinder binder = new LocalBinder();
    private boolean isRunning = false;
    
    // Serveurs
    private HttpServer httpServer;
    private WebSocketServer wsServer;
    private FileServer fileServer;
    private RealtimeAIService aiService;
    private WebServer webServer;
    
    // Hotword Detection (Porcupine)
    private com.chatai.hotword.HotwordDetectionManager hotwordManager;
    
    // Protection contre déclenchements multiples de STT (Whisper ou Google Speech)
    private com.chatai.audio.WhisperServerRecognizer currentWhisperRecognizer = null;
    private SpeechRecognizer currentSpeechRecognizer = null;
    private long lastAiRespondMs = 0;
    private static final long MIN_COOLDOWN_BETWEEN_AI_RESPONDS_MS = 2500; // Cooldown minimum entre 2 réponses AI (2.5s)
    
    public class LocalBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "BackgroundService créé");
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Démarrage du service en arrière-plan");
        
        // Créer la notification
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        // Gestion des actions explicites
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.i(TAG, "Action reçue: " + action);
            switch (action) {
                case ACTION_HOTWORD_START:
                    startHotword();
                    break;
                case ACTION_HOTWORD_STOP:
                    stopHotword();
                    break;
                case ACTION_HOTWORD_RESTART:
                    restartHotword();
                    break;
                case ACTION_SERVERS_RESTART:
                    restartServers();
                    break;
                case ACTION_AI_RESPOND:
                    respondAI(intent != null ? intent.getStringExtra("hotword_keyword") : null);
                    break;
                case ACTION_STOP_WHISPER:
                    stopWhisperIfActive();
                    break;
                case ACTION_STOP_GOOGLE_SPEECH:
                    stopGoogleSpeechIfActive();
                    break;
                default:
                    // Démarrage normal des serveurs
                    startServers();
                    break;
            }
        } else {
            // Démarrage normal des serveurs
            startServers();
        }

        isRunning = true;
        return START_STICKY;
    }

    private void respondAI(String keyword) {
        try {
            Log.i(TAG, "AI Respond requested (outside KITT), keyword=" + keyword);
            
            // PROTECTION: Cooldown global pour éviter déclenchements multiples
            long now = System.currentTimeMillis();
            long timeSinceLastRespond = now - lastAiRespondMs;
            if (timeSinceLastRespond < MIN_COOLDOWN_BETWEEN_AI_RESPONDS_MS) {
                Log.w(TAG, "AI Respond ignoré: cooldown actif (" + timeSinceLastRespond + "ms < " + MIN_COOLDOWN_BETWEEN_AI_RESPONDS_MS + "ms)");
                return;
            }
            
            // PROTECTION: Vérifier si STT est déjà en cours (Whisper ou Google Speech)
            if (currentWhisperRecognizer != null) {
                // Vérifier si l'instance précédente est toujours active
                try {
                    // Si on ne peut pas facilement vérifier, on ignore la nouvelle demande
                    // Le WhisperServerRecognizer se libère automatiquement après capture
                    Log.w(TAG, "AI Respond ignoré: Whisper déjà en cours");
                    return;
                } catch (Exception e) {
                    // Si l'instance est invalide, on peut continuer
                    currentWhisperRecognizer = null;
                }
            }
            if (currentSpeechRecognizer != null) {
                // Google Speech est toujours actif - forcer la libération avant de continuer
                Log.w(TAG, "AI Respond: Google Speech déjà en cours, libération forcée du recognizer précédent");
                try {
                    currentSpeechRecognizer.stopListening();
                } catch (Throwable ignored) {}
                try {
                    currentSpeechRecognizer.destroy();
                } catch (Throwable ignored) {}
                currentSpeechRecognizer = null;
                // Ne pas retourner ici - continuer pour créer un nouveau recognizer
            }
            
            lastAiRespondMs = now;
            
            // Intégration simple: utiliser RealtimeAIService si dispo
            org.json.JSONObject cfg = com.chatai.AiConfigManager.loadConfig(this);
            org.json.JSONObject hotword = cfg != null ? cfg.optJSONObject("hotword") : null;
            boolean autoListen = hotword != null && hotword.optBoolean("autoListen", false);

            if (autoListen) {
                // Bip de début d'écoute
                try {
                    android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 70);
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120);
                    // Libérer la ressource après un court délai pour éviter les timeouts
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        try {
                            tg.release();
                        } catch (Throwable ignored) {}
                    }, 150); // Libérer après 150ms (le beep dure 120ms)
                } catch (Throwable ignored) {}

                // Démarrer une capture unique via STT configuré (Whisper ou Google Speech)
                com.chatai.audio.AudioEngineConfig audioCfg = com.chatai.audio.AudioEngineConfig.Companion.fromContext(this);
                String engine = audioCfg.getEngine();
                
                if ("whisper_server".equalsIgnoreCase(engine)) {
                    // === WHISPER SERVER ===
                    // Créer client OkHttp avec timeouts configurés (120s read, 150s call)
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                            .callTimeout(150, java.util.concurrent.TimeUnit.SECONDS)
                            .build();
                    
                    // Stocker le recognizer avant création (pour éviter double déclenchement)
                    final com.chatai.audio.WhisperServerRecognizer[] recognizerRef = new com.chatai.audio.WhisperServerRecognizer[1];
                    
                    // Créer le recognizer (doit être final pour être utilisé dans le callback)
                    recognizerRef[0] = new com.chatai.audio.WhisperServerRecognizer(
                                    audioCfg,
                                    new com.chatai.audio.WhisperServerRecognizer.Callback() {
                                        @Override public void onReady() { 
                                            Log.i(TAG, "AutoSTT (Whisper) ready");
                                            // Marquer le recognizer comme actif
                                            currentWhisperRecognizer = recognizerRef[0];
                                        }
                                        @Override public void onSpeechStart() { Log.i(TAG, "AutoSTT (Whisper) speech"); }
                                        @Override public void onRmsChanged(float rmsDb) { /* no-op */ }
                                        @Override public void onResult(String text) {
                                            Log.i(TAG, "AutoSTT (Whisper) result: " + text);
                                            // Libérer le recognizer après résultat
                                            currentWhisperRecognizer = null;
                                            if (aiService != null && aiService.isHealthy()) {
                                                aiService.processAIRequest(text, "kitt");
                                            } else {
                                                toast("AI service not available");
                                            }
                                        }
                                        @Override public void onError(String message) {
                                            Log.e(TAG, "AutoSTT (Whisper) error: " + message);
                                            // Libérer le recognizer après erreur
                                            currentWhisperRecognizer = null;
                                            toast("STT error: " + message);
                                        }
                                    },
                                    client
                            );
                    
                    final com.chatai.audio.WhisperServerRecognizer recognizer = recognizerRef[0];
                    
                    // Stocker le recognizer avant délai (pour éviter double déclenchement)
                    currentWhisperRecognizer = recognizer;
                    
                    // DÉLAI CONFIGURABLE après hotword avant démarrage Whisper
                    // Permet à l'utilisateur de commencer à parler après le beep de confirmation
                    // Défaut: 400ms (recommandé: 300-500ms, minimum 200ms pour que le beep se termine)
                    int delayAfterHotword = audioCfg.getDelayAfterHotwordMs();
                    Log.i(TAG, "Hotword detected, starting Whisper after " + delayAfterHotword + "ms delay");
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        // Vérifier à nouveau avant de démarrer (au cas où une autre détection serait arrivée)
                        if (currentWhisperRecognizer == recognizer) {
                            recognizer.startListening();
                        } else {
                            Log.w(TAG, "Whisper start annulé: nouvelle détection hotword arrivée pendant le délai");
                        }
                    }, delayAfterHotword);
                    return;
                } else if ("legacy_google".equalsIgnoreCase(engine)) {
                    // === GOOGLE SPEECH ===
                    // CRITIQUE: SpeechRecognizer DOIT être créé sur le main thread
                    // C'est probablement la cause principale des problèmes
                    int delayAfterHotword = audioCfg.getDelayAfterHotwordMs();
                    Log.i(TAG, "Hotword detected, starting Google Speech after " + delayAfterHotword + "ms delay");
                    
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            // S'assurer qu'aucun recognizer n'est actif (protection supplémentaire)
                            if (currentSpeechRecognizer != null) {
                                Log.w(TAG, "Google Speech: libération du recognizer existant avant création nouveau");
                                try {
                                    currentSpeechRecognizer.stopListening();
                                } catch (Throwable ignored) {}
                                try {
                                    currentSpeechRecognizer.destroy();
                                } catch (Throwable ignored) {}
                                currentSpeechRecognizer = null;
                            }
                            
                            // Vérifier permission RECORD_AUDIO
                            int permissionCheck = ContextCompat.checkSelfPermission(
                                BackgroundService.this, android.Manifest.permission.RECORD_AUDIO);
                            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                                Log.e(TAG, "Permission RECORD_AUDIO non accordée (code: " + permissionCheck + ")");
                                toast("Permission microphone non accordée - Vérifiez les paramètres de l'app");
                                return;
                            }
                            Log.i(TAG, "Permission RECORD_AUDIO vérifiée : OK");
                            
                            // Vérifier si Google Speech est disponible
                            if (!SpeechRecognizer.isRecognitionAvailable(BackgroundService.this)) {
                                Log.e(TAG, "Google Speech recognition non disponible sur ce device");
                                toast("Google Speech non disponible");
                                return;
                            }
                            
                            Log.i(TAG, "Vérification permissions/Google Speech OK - création SpeechRecognizer");
                            
                            // CRÉER SUR LE MAIN THREAD (comme dans WebAppInterface)
                            currentSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(BackgroundService.this);
                            if (currentSpeechRecognizer == null) {
                                Log.e(TAG, "SpeechRecognizer.createSpeechRecognizer() retourne null");
                                toast("Impossible de créer SpeechRecognizer");
                                return;
                            }
                            Log.i(TAG, "Google Speech recognizer créé sur main thread");
                            currentSpeechRecognizer.setRecognitionListener(new GoogleSpeechRecognitionListener());
                            
                            // Stocker la référence avant le délai
                            final SpeechRecognizer speechRecognizer = currentSpeechRecognizer;
                            if (speechRecognizer == null) {
                                Log.e(TAG, "SpeechRecognizer is null");
                                return;
                            }
                            
                            // DÉLAI CONFIGURABLE après hotword avant démarrage Google Speech
                            // (identique à KittVoiceManager - pas de timeout, juste attendre onResults/onError)
                            mainHandler.postDelayed(() -> {
                                // Vérifier à nouveau avant de démarrer (au cas où une autre détection serait arrivée)
                                if (currentSpeechRecognizer == speechRecognizer && speechRecognizer != null) {
                                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                                    // Utiliser Locale.FRENCH (comme KittVoiceManager qui fonctionne)
                                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH);
                                    intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                                    // Forcer les résultats partiels pour obtenir des retours même si onEndOfSpeech n'est pas appelé
                                    intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                                    // Note: EXTRA_PROMPT non utilisé avec SpeechRecognizer (seulement pour startActivityForResult)
                                    // Note: EXTRA_CALLING_PACKAGE non utilisé ici (comme dans KittVoiceManager qui fonctionne)
                                    try {
                                        speechRecognizer.startListening(intent);
                                        Log.i(TAG, "Google Speech started with language=" + Locale.FRENCH + ", maxResults=1, partialResults=true");
                                        // Pas de timeout - on attend simplement onResults() ou onError() comme dans KittVoiceManager
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to start Google Speech: " + e.getMessage(), e);
                                        currentSpeechRecognizer = null;
                                        try {
                                            speechRecognizer.stopListening();
                                        } catch (Throwable ignored) {}
                                        try {
                                            speechRecognizer.destroy();
                                        } catch (Throwable ignored) {}
                                        toast("Failed to start Google Speech: " + e.getMessage());
                                    }
                                } else {
                                    Log.w(TAG, "Google Speech start annulé: nouvelle détection hotword arrivée pendant le délai");
                                }
                            }, delayAfterHotword);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to create SpeechRecognizer on main thread", e);
                            currentSpeechRecognizer = null;
                            toast("Failed to initialize Google Speech: " + e.getMessage());
                        }
                    });
                    return;
                } else {
                    Log.w(TAG, "Engine STT inconnu: " + engine);
                }
            }

            // Fallback: prompt simple si pas d'autoListen
            if (aiService != null && aiService.isHealthy()) {
                String prompt = "Wake word detected: " + (keyword == null ? "unknown" : keyword) + ". How can I help?";
                aiService.processAIRequest(prompt, "kitt");
            } else {
                Log.w(TAG, "AI service not available; showing toast only");
                toast("AI Respond (outside KITT): " + keyword);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in respondAI", e);
        }
    }

    private void toast(String msg) {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(() -> android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show());
    }
    
    @Override
    public void onDestroy() {
        Log.i(TAG, "Arrêt du service en arrière-plan");
        stopServers();
        isRunning = false;
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    private void createNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ChatAI Background Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Service en arrière-plan pour ChatAI");
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        String ipAddress = getLocalIpAddress();
        String notificationText = ipAddress != null 
            ? "IP: " + ipAddress + " | Ports: 8888, 8080, 9090"
            : "Les serveurs ChatAI fonctionnent en arrière-plan";
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ChatAI - Serveurs actifs")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
            .build();
    }
    
    private String getLocalIpAddress() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress inetAddress : Collections.list(networkInterface.getInetAddresses())) {
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la récupération de l'IP", e);
        }
        return null;
    }
    
    private void startServers() {
        try {
            Log.i(TAG, "Démarrage des serveurs...");
            
            // Initialiser les serveurs
            httpServer = new HttpServer(this);
            wsServer = new WebSocketServer(this);
            fileServer = new FileServer(this);
            webServer = new WebServer(this);
            
            // Configurer les références entre serveurs
            httpServer.setFileServer(fileServer);
            
            aiService = new RealtimeAIService(this, httpServer, wsServer);
            
            // Démarrer les serveurs
            httpServer.start();
            wsServer.start();
            fileServer.start();
            webServer.start();
            
            // Démarrer Hotword Detection (Porcupine)
            hotwordManager = new com.chatai.hotword.HotwordDetectionManager(this);
            hotwordManager.setStateListener(newState -> {
                Log.i(TAG, "Hotword state changed: " + newState);
            });
            hotwordManager.start();
            Log.i(TAG, "Hotword service started");
            
            Log.i(TAG, "Tous les serveurs démarrés avec succès");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage des serveurs", e);
        }
    }
    
    private void stopServers() {
        try {
            Log.i(TAG, "Arrêt des serveurs...");
            
            if (httpServer != null) {
                httpServer.stop();
            }
            if (wsServer != null) {
                wsServer.stop();
            }
            if (fileServer != null) {
                fileServer.stop();
            }
            if (webServer != null) {
                webServer.stop();
            }
            if (aiService != null) {
                // aiService.shutdown(); // Méthode non disponible
            }
            if (hotwordManager != null) {
                hotwordManager.stop();
                hotwordManager = null;
            }
            
            // Libérer les recognizers STT
            if (currentWhisperRecognizer != null) {
                try {
                    currentWhisperRecognizer = null;
                } catch (Throwable ignored) {}
            }
            if (currentSpeechRecognizer != null) {
                try {
                    currentSpeechRecognizer.stopListening();
                    currentSpeechRecognizer.destroy();
                } catch (Throwable ignored) {}
                currentSpeechRecognizer = null;
            }
            
            Log.i(TAG, "Tous les serveurs arrêtés");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'arrêt des serveurs", e);
        }
    }
    
    /**
     * Vérifier si STT est actuellement en cours (Whisper ou Google Speech)
     * @return true si un recognizer STT est actif
     */
    public boolean isSTTActive() {
        return currentWhisperRecognizer != null || currentSpeechRecognizer != null;
    }
    
    /**
     * Arrêter Whisper s'il est actif (pour libérer le microphone pour Google Speech)
     */
    public void stopWhisperIfActive() {
        if (currentWhisperRecognizer != null) {
            Log.i(TAG, "Arrêt forcé de Whisper (libération microphone pour Google Speech)");
            try {
                currentWhisperRecognizer.stopListening();
            } catch (Throwable e) {
                Log.w(TAG, "Error stopping Whisper: " + e.getMessage());
            }
            currentWhisperRecognizer = null;
        }
    }
    
    /**
     * Arrêter Google Speech s'il est actif (pour libérer le microphone pour Whisper)
     */
    public void stopGoogleSpeechIfActive() {
        if (currentSpeechRecognizer != null) {
            Log.i(TAG, "Arrêt forcé de Google Speech (libération microphone pour Whisper)");
            try {
                currentSpeechRecognizer.stopListening();
            } catch (Throwable ignored) {}
            try {
                currentSpeechRecognizer.destroy();
            } catch (Throwable e) {
                Log.w(TAG, "Error destroying Google Speech: " + e.getMessage());
            }
            currentSpeechRecognizer = null;
        }
    }
    
    public void restartServers() {
        Log.i(TAG, "Redémarrage des serveurs...");
        stopServers();
        startServers();
    }

    private void startHotword() {
        try {
            if (hotwordManager == null) {
                hotwordManager = new com.chatai.hotword.HotwordDetectionManager(this);
                hotwordManager.setStateListener(newState -> Log.i(TAG, "Hotword state changed: " + newState));
            }
            hotwordManager.start();
            Log.i(TAG, "Hotword START demandé");
        } catch (Exception e) {
            Log.e(TAG, "Erreur startHotword", e);
        }
    }

    private void stopHotword() {
        try {
            if (hotwordManager != null) {
                hotwordManager.stop();
                Log.i(TAG, "Hotword STOP demandé");
            } else {
                Log.w(TAG, "stopHotword: manager nul");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur stopHotword", e);
        }
    }

    private void restartHotword() {
        try {
            stopHotword();
            startHotword();
            Log.i(TAG, "Hotword RESTART demandé");
        } catch (Exception e) {
            Log.e(TAG, "Erreur restartHotword", e);
        }
    }
    
    public boolean areServersRunning() {
        return (httpServer != null && httpServer.isRunning()) &&
               (wsServer != null && wsServer.isRunning()) &&
               (fileServer != null && fileServer.isRunning()) &&
               (webServer != null && webServer.isRunning());
    }
    
    public boolean isServiceRunning() {
        return isRunning;
    }
    
    // Méthodes getter pour accéder aux serveurs
    public HttpServer getHttpServer() {
        return httpServer;
    }
    
    public WebSocketServer getWebSocketServer() {
        return wsServer;
    }
    
    public RealtimeAIService getAIService() {
        return aiService;
    }
    
    public FileServer getFileServer() {
        return fileServer;
    }
    
    public WebServer getWebServer() {
        return webServer;
    }
    
    /**
     * RecognitionListener pour Google Speech dans hotword (classe interne non-statique pour éviter problèmes KSP)
     * Timeouts pour éviter que Google Speech reste bloqué si aucun résultat n'est retourné
     */
    private class GoogleSpeechRecognitionListener implements RecognitionListener {
        private android.os.Handler timeoutHandler;
        private Runnable speechTimeoutRunnable;
        
        GoogleSpeechRecognitionListener() {
            this.timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        
        private void cleanupTimeouts() {
            if (speechTimeoutRunnable != null) {
                timeoutHandler.removeCallbacks(speechTimeoutRunnable);
                speechTimeoutRunnable = null;
            }
        }
        
        @Override
        public void onReadyForSpeech(android.os.Bundle params) {
            Log.i("BackgroundService", "AutoSTT (Google Speech) ready - microphone accessible, waiting for speech...");
        }
        
        @Override
        public void onBeginningOfSpeech() {
            Log.i("BackgroundService", "AutoSTT (Google Speech) speech start - Google Speech A DÉTECTÉ DE LA PAROLE");
            // TIMEOUT: Si aucun résultat après 15 secondes depuis le début de la parole, forcer l'arrêt
            // (augmenté à 15s car Google Speech offline peut prendre plus de temps pour traiter)
            cleanupTimeouts();
            final GoogleSpeechRecognitionListener self = this;
            speechTimeoutRunnable = () -> {
                if (currentSpeechRecognizer != null) {
                    Log.w("BackgroundService", "AutoSTT (Google Speech): timeout après début de parole (15s) - arrêt forcé");
                    SpeechRecognizer toDestroy = currentSpeechRecognizer;
                    currentSpeechRecognizer = null;
                    if (toDestroy != null) {
                        try {
                            toDestroy.stopListening();
                        } catch (Throwable ignored) {}
                        try {
                            toDestroy.destroy();
                            Log.i("BackgroundService", "Google Speech recognizer libéré (timeout)");
                        } catch (Throwable e) {
                            Log.e("BackgroundService", "Error destroying Google Speech recognizer: " + e.getMessage());
                        }
                    }
                    toast("STT timeout (15s)");
                }
            };
            timeoutHandler.postDelayed(speechTimeoutRunnable, 15000); // 15 secondes après début de parole (augmenté)
        }
        
        @Override
        public void onRmsChanged(float rmsDb) {
            // Log RMS toutes les 10 fois pour diagnostic (réduire spam mais voir si microphone capte)
            // RMS > -30 dB = parole audible, RMS > -10 dB = parole forte
            if ((int)(rmsDb * 10) % 10 == 0) {
                String level = rmsDb > -10 ? "FORT" : (rmsDb > -30 ? "NORMAL" : "FAIBLE");
                Log.i("BackgroundService", "🎤 AutoSTT (Google Speech) RMS: " + String.format("%.1f", rmsDb) + " dB (" + level + ") - MICROPHONE ACTIF");
            }
        }
        
        @Override
        public void onBufferReceived(byte[] buffer) {
            // Log buffer reçu (confirme que microphone envoie des données)
            // Log en I (INFO) pour être sûr de voir dans les logs
            // Log toutes les 10 fois pour réduire le spam
            if (buffer.length % 3200 == 0 || buffer.length < 1000) { // Log si buffer petit ou tous les ~10 buffers
                Log.i("BackgroundService", "📡 AutoSTT (Google Speech) buffer received: " + buffer.length + " bytes - MICROPHONE ENVOIE DONNÉES");
            }
        }
        
        @Override
        public void onEndOfSpeech() {
            Log.i("BackgroundService", "AutoSTT (Google Speech) speech end - en attente des résultats...");
            // Annuler le timeout après début de parole (on a détecté la fin de parole)
            cleanupTimeouts();
            
            // TIMEOUT: Si aucun résultat après 8 secondes depuis la fin de la parole, forcer l'arrêt
            // (augmenté à 8s car Google Speech offline peut prendre plus de temps pour traiter après onEndOfSpeech)
            final GoogleSpeechRecognitionListener self = this;
            speechTimeoutRunnable = () -> {
                if (currentSpeechRecognizer != null) {
                    Log.w("BackgroundService", "AutoSTT (Google Speech): timeout après fin de parole (8s) - arrêt forcé");
                    SpeechRecognizer toDestroy = currentSpeechRecognizer;
                    currentSpeechRecognizer = null;
                    if (toDestroy != null) {
                        try {
                            toDestroy.stopListening();
                        } catch (Throwable ignored) {}
                        try {
                            toDestroy.destroy();
                            Log.i("BackgroundService", "Google Speech recognizer libéré (timeout après fin)");
                        } catch (Throwable e) {
                            Log.e("BackgroundService", "Error destroying Google Speech recognizer: " + e.getMessage());
                        }
                    }
                    toast("STT timeout après fin de parole (8s)");
                }
            };
            timeoutHandler.postDelayed(speechTimeoutRunnable, 8000); // 8 secondes après fin de parole (augmenté)
        }
        
        @Override
        public void onError(int error) {
            cleanupTimeouts();
            
            String errorMsg = "Unknown error";
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO: errorMsg = "Audio error"; break;
                case SpeechRecognizer.ERROR_CLIENT: errorMsg = "Client error"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: errorMsg = "Insufficient permissions"; break;
                case SpeechRecognizer.ERROR_NETWORK: errorMsg = "Network error"; break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: errorMsg = "Network timeout"; break;
                case SpeechRecognizer.ERROR_NO_MATCH: errorMsg = "No match"; break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: errorMsg = "Recognizer busy"; break;
                case SpeechRecognizer.ERROR_SERVER: errorMsg = "Server error"; break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: errorMsg = "Speech timeout"; break;
            }
            Log.e("BackgroundService", "AutoSTT (Google Speech) error: " + errorMsg + " (" + error + ")");
            // Libérer le recognizer après erreur
            SpeechRecognizer toDestroy = currentSpeechRecognizer;
            currentSpeechRecognizer = null;
            if (toDestroy != null) {
                try {
                    toDestroy.stopListening();
                } catch (Throwable e) {
                    Log.w("BackgroundService", "Error stopping Google Speech recognizer: " + e.getMessage());
                }
                try {
                    toDestroy.destroy();
                    Log.i("BackgroundService", "Google Speech recognizer libéré (error)");
                } catch (Throwable e) {
                    Log.e("BackgroundService", "Error destroying Google Speech recognizer: " + e.getMessage());
                }
            }
            toast("STT error: " + errorMsg);
        }
        
        @Override
        public void onResults(android.os.Bundle results) {
            cleanupTimeouts();
            
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.i("BackgroundService", "AutoSTT (Google Speech) result: " + text + " (matches: " + matches.size() + ")");
                // Libérer le recognizer après résultat
                SpeechRecognizer toDestroy = currentSpeechRecognizer;
                currentSpeechRecognizer = null;
                if (toDestroy != null) {
                    try {
                        toDestroy.stopListening();
                    } catch (Throwable e) {
                        Log.w("BackgroundService", "Error stopping Google Speech recognizer: " + e.getMessage());
                    }
                    try {
                        toDestroy.destroy();
                        Log.i("BackgroundService", "Google Speech recognizer libéré (result)");
                    } catch (Throwable e) {
                        Log.e("BackgroundService", "Error destroying Google Speech recognizer: " + e.getMessage());
                    }
                }
                if (aiService != null && aiService.isHealthy()) {
                    aiService.processAIRequest(text, "kitt");
                } else {
                    toast("AI service not available");
                }
            } else {
                Log.w("BackgroundService", "AutoSTT (Google Speech) no matches");
                onError(SpeechRecognizer.ERROR_NO_MATCH);
            }
        }
        
        @Override
        public void onPartialResults(android.os.Bundle partialResults) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.i("BackgroundService", "✅ AutoSTT (Google Speech) partial result: '" + text + "' (Google Speech VOUS ENTEND!)");
                // Si on reçoit des résultats partiels, Google Speech entend vraiment !
            } else {
                Log.d("BackgroundService", "AutoSTT (Google Speech) partial results: aucun match (Google Speech traite mais ne reconnaît pas encore)");
            }
        }
        
        @Override
        public void onEvent(int eventType, android.os.Bundle params) {
            // no-op
        }
    }
}