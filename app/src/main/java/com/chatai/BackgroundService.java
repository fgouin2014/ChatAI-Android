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
    
    private final IBinder binder = new LocalBinder();
    private boolean isRunning = false;
    
    // Serveurs
    private HttpServer httpServer;
    private WebSocketServer wsServer;
    private FileServer fileServer;
    private RealtimeAIService aiService;
    private WebServer webServer;
    
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
        
        // Démarrer les serveurs
        startServers();
        
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