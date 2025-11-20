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
        
        // CRITIQUE: Libérer TOUJOURS SpeechRecognizer au démarrage (prévention)
        // Un SpeechRecognizer pourrait être resté actif d'une session précédente
        // (crash, kill forcé, etc.) et bloquerait le clavier Google même sans utilisation du hotword
        cleanupAnyOrphanedSpeechRecognizer("service startup (prévention)");
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
                                            
                                            // ⭐ NOUVEAU : Émettre via BidirectionalBridge pour afficher dans Chat
                                            emitHotwordMessageToBridge(text);
                                            
                                            // Libérer le recognizer après résultat
                                            currentWhisperRecognizer = null;
                                            if (aiService != null && aiService.isHealthy()) {
                                                // Traiter la requête IA et envoyer la réponse au chat web
                                                aiService.processAIRequest(text, "kitt")
                                                    .thenAccept(response -> {
                                                        Log.i(TAG, "AI Response (hotword): " + response);
                                                        // ⭐ NOUVEAU : Envoyer la réponse IA au chat web via BidirectionalBridge
                                                        emitAIResponseToBridge(response);
                                                    })
                                                    .exceptionally(throwable -> {
                                                        Log.e(TAG, "Erreur traitement IA hotword", throwable);
                                                        emitAIResponseToBridge("Erreur: " + throwable.getMessage());
                                                        return null;
                                                    });
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
                    // CRITIQUE: L'AudioRecord du hotword monopolise le microphone
                    // Il faut suspendre temporairement le hotword pour libérer le microphone
                    int delayAfterHotword = audioCfg.getDelayAfterHotwordMs();
                    Log.i(TAG, "Hotword detected, starting Google Speech after " + delayAfterHotword + "ms delay");
                    
                    // CRITIQUE: Arrêter temporairement le hotword pour libérer l'AudioRecord
                    // pause() ne libère PAS l'AudioRecord, il reste actif et monopolise le microphone
                    // Il faut utiliser stop() pour arrêter l'AudioRecord, puis start() pour redémarrer
                    // Cela permet au clavier Google et autres apps d'utiliser le microphone
                    boolean hotwordWasRunning = false;
                    if (hotwordManager != null) {
                        try {
                            // Vérifier si le hotword est actif
                            if (hotwordManager.isRunning()) {
                                hotwordWasRunning = true;
                                hotwordManager.stop();  // Arrêter complètement (libère AudioRecord)
                                Log.i(TAG, "⏸️ Hotword arrêté temporairement (libération AudioRecord pour Google Speech)");
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error stopping hotword: " + e.getMessage());
                        }
                    }
                    
                    final boolean willRestartHotword = hotwordWasRunning;
                    
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        try {
                            // CRITIQUE: Libérer TOUJOURS le recognizer existant avant de créer un nouveau
                            // Évite de laisser un recognizer actif qui monopoliserait la ressource et bloquerait le clavier Google
                            if (currentSpeechRecognizer != null) {
                                Log.w(TAG, "⚠️ Google Speech: libération FORCÉE du recognizer existant avant création nouveau (éviter monopolisation)");
                                try {
                                    currentSpeechRecognizer.stopListening();
                                } catch (Throwable ignored) {}
                                try {
                                    currentSpeechRecognizer.destroy();
                                    Log.i(TAG, "✅ Ancien recognizer libéré");
                                } catch (Throwable e) {
                                    Log.w(TAG, "Error destroying old recognizer: " + e.getMessage());
                                }
                                currentSpeechRecognizer = null;
                            }
                            
                            // Vérifier si un autre app utilise SpeechRecognizer (ERROR_RECOGNIZER_BUSY est géré dans onError)
                            // On ne peut pas le détecter ici, mais on essaie quand même de créer notre instance
                            // Si busy, onError sera appelé avec ERROR_RECOGNIZER_BUSY et on libérera immédiatement
                            
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
                            // Créer le listener avec l'info pour redémarrer le hotword après usage
                            GoogleSpeechRecognitionListener listener = new GoogleSpeechRecognitionListener();
                            listener.setWillRestartHotword(willRestartHotword);
                            currentSpeechRecognizer.setRecognitionListener(listener);
                            
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
    
    /**
     * ⭐ NOUVEAU : Émet un message hotword via BidirectionalBridge pour afficher dans Chat
     * @param text Le texte transcrit depuis le hotword
     */
    private void emitHotwordMessageToBridge(String text) {
        try {
            com.chatai.services.BidirectionalBridge bridge = 
                com.chatai.services.BidirectionalBridge.getInstance(this);
            
            // Créer un message avec source "hotword" pour identification
            com.chatai.services.BidirectionalBridge.BridgeMessage bridgeMessage = 
                new com.chatai.services.BidirectionalBridge.BridgeMessage(
                    com.chatai.services.BidirectionalBridge.MessageType.USER_INPUT,
                    com.chatai.services.BidirectionalBridge.Source.SYSTEM, // Utiliser SYSTEM car hotword est externe
                    "[🔊 Hotword] " + text, // Préfixe pour identification dans Chat
                    java.util.Collections.singletonMap("source", "hotword"), // Metadata pour identification
                    System.currentTimeMillis()
                );
            
            // Envoyer via bridge vers Chat (KITT → Web)
            bridge.sendKittToWebAsync(bridgeMessage);
            Log.i(TAG, "📨 Message hotword émis via bridge: " + text);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'émission du message hotword via bridge", e);
        }
    }
    
    /**
     * ⭐ NOUVEAU : Émet la réponse IA via BidirectionalBridge pour afficher dans Chat
     * @param response La réponse de l'IA
     */
    private void emitAIResponseToBridge(String response) {
        try {
            com.chatai.services.BidirectionalBridge bridge = 
                com.chatai.services.BidirectionalBridge.getInstance(this);
            
            // Créer un message avec type AI_RESPONSE pour identification
            com.chatai.services.BidirectionalBridge.BridgeMessage bridgeMessage = 
                new com.chatai.services.BidirectionalBridge.BridgeMessage(
                    com.chatai.services.BidirectionalBridge.MessageType.AI_RESPONSE,
                    com.chatai.services.BidirectionalBridge.Source.SYSTEM, // Utiliser SYSTEM car réponse depuis hotword
                    response,
                    java.util.Collections.singletonMap("source", "hotword"), // Metadata pour identification
                    System.currentTimeMillis()
                );
            
            // Envoyer via bridge vers Chat (KITT → Web)
            bridge.sendKittToWebAsync(bridgeMessage);
            Log.i(TAG, "📨 Réponse IA hotword émise via bridge: " + response);
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'émission de la réponse IA via bridge", e);
        }
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
    
    /**
     * Libérer tout SpeechRecognizer orphelin (prévention monopolisation)
     * CRITIQUE: Appelée au démarrage du service pour nettoyer les recognizers restés actifs
     */
    private void cleanupAnyOrphanedSpeechRecognizer(String reason) {
        if (currentSpeechRecognizer != null) {
            Log.w(TAG, "⚠️ SpeechRecognizer orphelin détecté au démarrage (" + reason + ") - libération FORCÉE");
            try {
                currentSpeechRecognizer.stopListening();
            } catch (Throwable e) {
                Log.w(TAG, "Error stopping orphaned SpeechRecognizer: " + e.getMessage());
            }
            try {
                currentSpeechRecognizer.destroy();
                Log.i(TAG, "✅ SpeechRecognizer orphelin libéré (" + reason + ")");
            } catch (Throwable e) {
                Log.e(TAG, "Error destroying orphaned SpeechRecognizer: " + e.getMessage());
            }
            currentSpeechRecognizer = null;
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
            
            // CRITIQUE: Libérer TOUJOURS les recognizers STT à l'arrêt
            // Évite qu'ils restent actifs après l'arrêt du service et bloquent le clavier Google
            if (currentWhisperRecognizer != null) {
                try {
                    currentWhisperRecognizer = null;
                } catch (Throwable ignored) {}
            }
            cleanupAnyOrphanedSpeechRecognizer("service stop");
            
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
     * CRITIQUE: Libération agressive pour éviter de monopoliser SpeechRecognizer et bloquer le clavier Google
     */
    private class GoogleSpeechRecognitionListener implements RecognitionListener {
        private android.os.Handler timeoutHandler;
        private Runnable globalTimeoutRunnable;  // Timeout global après onReadyForSpeech
        private Runnable speechTimeoutRunnable;  // Timeout après début de parole
        private long startTimeMs = 0;  // Temps de démarrage pour timeout global
        
        // Timeouts réduits pour libération plus agressive (éviter monopolisation clavier Google)
        private static final long GLOBAL_TIMEOUT_MS = 12000;  // 12s max si aucune parole (réduit de 30s)
        private static final long SPEECH_START_TIMEOUT_MS = 10000;  // 10s max après début de parole (réduit de 15s)
        private static final long SPEECH_END_TIMEOUT_MS = 5000;  // 5s max après fin de parole (réduit de 8s)
        
        private boolean willRestartHotword = false;  // Track si hotword doit être redémarré après Google Speech
        
        GoogleSpeechRecognitionListener() {
            this.timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        
        /**
         * Définir si le hotword doit être redémarré après libération Google Speech
         */
        public void setWillRestartHotword(boolean willRestart) {
            this.willRestartHotword = willRestart;
        }
        
        private void cleanupTimeouts() {
            if (globalTimeoutRunnable != null) {
                timeoutHandler.removeCallbacks(globalTimeoutRunnable);
                globalTimeoutRunnable = null;
            }
            if (speechTimeoutRunnable != null) {
                timeoutHandler.removeCallbacks(speechTimeoutRunnable);
                speechTimeoutRunnable = null;
            }
        }
        
        /**
         * Force la libération du SpeechRecognizer (helper centralisé)
         * CRITIQUE: Appelée dans tous les cas pour éviter monopolisation
         * CRITIQUE: Redémarre le hotword si il avait été arrêté pour Google Speech
         */
        private void forceReleaseRecognizer(String reason) {
            cleanupTimeouts();
            boolean shouldRestartHotword = this.willRestartHotword;  // Sauvegarder avant reset
            this.willRestartHotword = false;  // Reset immédiatement
            
            if (currentSpeechRecognizer != null) {
                Log.i("BackgroundService", "🔓 Force release Google Speech recognizer: " + reason);
                SpeechRecognizer toDestroy = currentSpeechRecognizer;
                currentSpeechRecognizer = null;  // Set à null IMMÉDIATEMENT pour éviter réutilisation
                if (toDestroy != null) {
                    try {
                        toDestroy.stopListening();
                    } catch (Throwable e) {
                        Log.w("BackgroundService", "Error stopping Google Speech: " + e.getMessage());
                    }
                    try {
                        toDestroy.destroy();
                        Log.i("BackgroundService", "✅ Google Speech recognizer libéré (" + reason + ")");
                    } catch (Throwable e) {
                        Log.e("BackgroundService", "Error destroying Google Speech: " + e.getMessage());
                    }
                }
            }
            
            // CRITIQUE: Redémarrer le hotword après libération du SpeechRecognizer
            // L'AudioRecord est maintenant libre, le hotword peut reprendre la détection
            if (shouldRestartHotword && hotwordManager != null) {
                try {
                    // Petit délai pour s'assurer que le microphone est complètement libéré
                    timeoutHandler.postDelayed(() -> {
                        try {
                            hotwordManager.start();
                            Log.i("BackgroundService", "▶️ Hotword redémarré après libération Google Speech (microphone libre)");
                        } catch (Exception e) {
                            Log.w("BackgroundService", "Error restarting hotword: " + e.getMessage());
                        }
                    }, 200);  // 200ms délai pour libération complète du microphone
                } catch (Exception e) {
                    Log.w("BackgroundService", "Error scheduling hotword restart: " + e.getMessage());
                }
            }
        }
        
        @Override
        public void onReadyForSpeech(android.os.Bundle params) {
            Log.i("BackgroundService", "AutoSTT (Google Speech) ready - microphone accessible, waiting for speech...");
            startTimeMs = System.currentTimeMillis();
            
            // TIMEOUT GLOBAL: Si aucune parole détectée après 12 secondes, forcer libération
            // CRITIQUE: Évite que SpeechRecognizer reste actif indéfiniment et bloque le clavier Google
            cleanupTimeouts();
            globalTimeoutRunnable = () -> {
                long elapsed = System.currentTimeMillis() - startTimeMs;
                if (currentSpeechRecognizer != null && elapsed >= GLOBAL_TIMEOUT_MS - 500) {  // Marge de 500ms
                    Log.w("BackgroundService", "⏱️ AutoSTT (Google Speech): timeout global (" + GLOBAL_TIMEOUT_MS + "ms) - aucune parole détectée, libération");
                    forceReleaseRecognizer("timeout global (aucune parole)");
                    toast("STT timeout: aucune parole détectée");
                }
            };
            timeoutHandler.postDelayed(globalTimeoutRunnable, GLOBAL_TIMEOUT_MS);
        }
        
        @Override
        public void onBeginningOfSpeech() {
            Log.i("BackgroundService", "AutoSTT (Google Speech) speech start - Google Speech A DÉTECTÉ DE LA PAROLE");
            // Annuler le timeout global (on a détecté de la parole)
            cleanupTimeouts();
            
            // TIMEOUT: Si aucun résultat après 10 secondes depuis le début de la parole, forcer l'arrêt
            // Réduit à 10s pour libération plus agressive et éviter monopolisation clavier Google
            speechTimeoutRunnable = () -> {
                if (currentSpeechRecognizer != null) {
                    Log.w("BackgroundService", "⏱️ AutoSTT (Google Speech): timeout après début de parole (" + SPEECH_START_TIMEOUT_MS + "ms) - arrêt forcé");
                    forceReleaseRecognizer("timeout après début de parole");
                    toast("STT timeout (" + (SPEECH_START_TIMEOUT_MS / 1000) + "s)");
                }
            };
            timeoutHandler.postDelayed(speechTimeoutRunnable, SPEECH_START_TIMEOUT_MS);
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
            
            // TIMEOUT: Si aucun résultat après 5 secondes depuis la fin de la parole, forcer l'arrêt
            // Réduit à 5s pour libération plus agressive et éviter monopolisation clavier Google
            speechTimeoutRunnable = () -> {
                if (currentSpeechRecognizer != null) {
                    Log.w("BackgroundService", "⏱️ AutoSTT (Google Speech): timeout après fin de parole (" + SPEECH_END_TIMEOUT_MS + "ms) - arrêt forcé");
                    forceReleaseRecognizer("timeout après fin de parole");
                    toast("STT timeout après fin de parole (" + (SPEECH_END_TIMEOUT_MS / 1000) + "s)");
                }
            };
            timeoutHandler.postDelayed(speechTimeoutRunnable, SPEECH_END_TIMEOUT_MS);
        }
        
        @Override
        public void onError(int error) {
            String errorMsg = "Unknown error";
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO: errorMsg = "Audio error"; break;
                case SpeechRecognizer.ERROR_CLIENT: errorMsg = "Client error"; break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: errorMsg = "Insufficient permissions"; break;
                case SpeechRecognizer.ERROR_NETWORK: errorMsg = "Network error"; break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: errorMsg = "Network timeout"; break;
                case SpeechRecognizer.ERROR_NO_MATCH: errorMsg = "No match"; break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: 
                    errorMsg = "Recognizer busy"; 
                    // CRITIQUE: Si le recognizer est occupé, c'est peut-être parce que l'AudioRecord du hotword monopolise le microphone
                    // Arrêter temporairement le hotword pour libérer l'AudioRecord et permettre à Google Speech d'accéder au microphone
                    if (hotwordManager != null && hotwordManager.isRunning()) {
                        Log.w("BackgroundService", "⚠️ ERROR_RECOGNIZER_BUSY - Le hotword monopolise peut-être le microphone");
                        Log.w("BackgroundService", "⏸️ Arrêt temporaire du hotword pour libérer le microphone");
                        try {
                            hotwordManager.stop();
                            // Programmer un redémarrage après 2 secondes (donner le temps à Google Speech d'utiliser le microphone)
                            timeoutHandler.postDelayed(() -> {
                                if (hotwordManager != null && !hotwordManager.isRunning()) {
                                    try {
                                        hotwordManager.start();
                                        Log.i("BackgroundService", "▶️ Hotword redémarré après ERROR_RECOGNIZER_BUSY");
                                    } catch (Exception e) {
                                        Log.w("BackgroundService", "Error restarting hotword after ERROR_RECOGNIZER_BUSY: " + e.getMessage());
                                    }
                                }
                            }, 2000);  // 2 secondes pour permettre à Google Speech d'utiliser le microphone
                        } catch (Exception e) {
                            Log.w("BackgroundService", "Error stopping hotword on ERROR_RECOGNIZER_BUSY: " + e.getMessage());
                        }
                    }
                    break;
                case SpeechRecognizer.ERROR_SERVER: errorMsg = "Server error"; break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: errorMsg = "Speech timeout"; break;
            }
            Log.e("BackgroundService", "AutoSTT (Google Speech) error: " + errorMsg + " (" + error + ")");
            // Libérer le recognizer après erreur (utilisation helper centralisé)
            forceReleaseRecognizer("error: " + errorMsg);
            toast("STT error: " + errorMsg);
        }
        
        @Override
        public void onResults(android.os.Bundle results) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                Log.i("BackgroundService", "AutoSTT (Google Speech) result: " + text + " (matches: " + matches.size() + ")");
                
                // ⭐ NOUVEAU : Émettre via BidirectionalBridge pour afficher dans Chat
                emitHotwordMessageToBridge(text);
                
                // Libérer le recognizer après résultat (utilisation helper centralisé)
                forceReleaseRecognizer("result reçu");
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