package com.chatai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

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
            
            Log.i(TAG, "Tous les serveurs arrêtés");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'arrêt des serveurs", e);
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
}