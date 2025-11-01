package com.chatai;

import android.content.Context;
import android.util.Log;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serveur WebSocket natif pour communication temps réel
 * Gère les connexions multiples et la communication bidirectionnelle
 */
public class WebSocketServer {
    private static final String TAG = "WebSocketServer";
    private static final int PORT = 8081; // Changé pour éviter le conflit
    private static final int BUFFER_SIZE = 1024;
    
    private Context context;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ExecutorService executor;
    private boolean isRunning = false;
    private ConcurrentHashMap<SocketChannel, String> clients;
    private SecureConfig secureConfig;
    
    public WebSocketServer(Context context) {
        this.context = context;
        this.secureConfig = new SecureConfig(context);
        this.clients = new ConcurrentHashMap<>();
        this.executor = Executors.newFixedThreadPool(4);
    }
    
    /**
     * Démarre le serveur WebSocket
     */
    public void start() {
        // Fermer proprement le serveur existant s'il y en a un
        if (isRunning || serverChannel != null) {
            Log.w(TAG, "Serveur WebSocket déjà en cours, fermeture avant redémarrage");
            stop();
            try {
                Thread.sleep(500); // Attendre que le socket soit complètement libéré
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        executor.execute(() -> {
            try {
                startServer();
            } catch (Exception e) {
                Log.e(TAG, "Erreur démarrage serveur WebSocket", e);
            }
        });
    }
    
    private void startServer() throws IOException {
        // Lire le port depuis la configuration utilisateur
        int configuredPort = secureConfig.getIntSetting("ws_port", PORT);
        
        try {
            serverChannel = ServerSocketChannel.open();
            // Écouter sur toutes les interfaces (0.0.0.0) pour permettre l'accès externe
            serverChannel.bind(new InetSocketAddress("0.0.0.0", configuredPort));
            serverChannel.configureBlocking(false);
            
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            isRunning = true;
            Log.i(TAG, "Serveur WebSocket démarré sur le port " + configuredPort);
        } catch (java.net.BindException e) {
            Log.w(TAG, "Port " + configuredPort + " déjà utilisé, tentative avec port " + (configuredPort + 1));
            // Essayer le port suivant
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress("0.0.0.0", configuredPort + 1));
            serverChannel.configureBlocking(false);
            
            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            
            isRunning = true;
            Log.i(TAG, "Serveur WebSocket démarré sur le port " + (configuredPort + 1));
        }
        
        // Boucle principale du serveur
        while (isRunning) {
            try {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();
                
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur dans la boucle serveur", e);
            }
        }
    }
    
    /**
     * Gère les nouvelles connexions
     */
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        
        clients.put(clientChannel, "Client_" + System.currentTimeMillis());
        Log.i(TAG, "Nouveau client connecté: " + clientChannel.getRemoteAddress());
        
        // Envoyer message de bienvenue
        sendWelcomeMessage(clientChannel);
    }
    
    /**
     * Gère la lecture des messages
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead > 0) {
            buffer.flip();
            String message = new String(buffer.array(), 0, bytesRead);
            
            // Traiter le message WebSocket
            String processedMessage = processWebSocketMessage(clientChannel, message);
            
            // Répondre au client
            sendMessage(clientChannel, processedMessage);
            
            Log.d(TAG, "Message reçu: " + SecurityUtils.hashForLogging(message));
        } else if (bytesRead == -1) {
            // Client déconnecté
            handleClientDisconnect(clientChannel);
        }
    }
    
    /**
     * Gère l'écriture des messages
     */
    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        // Logique d'écriture si nécessaire
    }
    
    /**
     * Traite un message WebSocket
     */
    private String processWebSocketMessage(SocketChannel clientChannel, String message) {
        try {
            // Valider et sanitizer le message
            if (!SecurityUtils.isValidInput(message)) {
                return createWebSocketResponse("error", "Message invalide");
            }
            
            String sanitizedMessage = SecurityUtils.sanitizeInput(message);
            
            // Parser le message JSON (simplifié)
            if (sanitizedMessage.contains("chat_message")) {
                return handleChatMessage(sanitizedMessage);
            } else if (sanitizedMessage.contains("ping")) {
                return handlePing();
            } else if (sanitizedMessage.contains("typing")) {
                return handleTypingIndicator();
            }
            
            return createWebSocketResponse("unknown", "Type de message non reconnu");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur traitement message", e);
            return createWebSocketResponse("error", "Erreur de traitement");
        }
    }
    
    /**
     * Gère un message de chat
     */
    private String handleChatMessage(String message) {
        // Simuler une réponse IA (à remplacer par un vrai appel API)
        String response = "Réponse IA simulée pour: " + message.substring(0, Math.min(50, message.length()));
        return createWebSocketResponse("chat_response", response);
    }
    
    /**
     * Gère un ping
     */
    private String handlePing() {
        return createWebSocketResponse("pong", "Serveur actif");
    }
    
    /**
     * Gère l'indicateur de frappe
     */
    private String handleTypingIndicator() {
        return createWebSocketResponse("typing_received", "Indicateur de frappe reçu");
    }
    
    /**
     * Crée une réponse WebSocket formatée
     */
    private String createWebSocketResponse(String type, String content) {
        return String.format("{\"type\":\"%s\",\"content\":\"%s\",\"timestamp\":%d}", 
                           type, SecurityUtils.sanitizeInput(content), System.currentTimeMillis());
    }
    
    /**
     * Envoie un message à un client spécifique
     */
    private void sendMessage(SocketChannel clientChannel, String message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            clientChannel.write(buffer);
            Log.d(TAG, "Message envoyé: " + SecurityUtils.hashForLogging(message));
        } catch (IOException e) {
            Log.e(TAG, "Erreur envoi message", e);
        }
    }
    
    /**
     * Envoie un message de bienvenue
     */
    private void sendWelcomeMessage(SocketChannel clientChannel) {
        String welcomeMessage = createWebSocketResponse("welcome", "Connecté au serveur ChatAI");
        sendMessage(clientChannel, welcomeMessage);
    }
    
    /**
     * Diffuse un message à tous les clients
     */
    public void broadcastMessage(String message) {
        String safeMessage = SecurityUtils.sanitizeInput(message);
        String formattedMessage = createWebSocketResponse("broadcast", safeMessage);
        
        for (SocketChannel client : clients.keySet()) {
            try {
                sendMessage(client, formattedMessage);
            } catch (Exception e) {
                Log.e(TAG, "Erreur diffusion message", e);
            }
        }
    }
    
    /**
     * Gère la déconnexion d'un client
     */
    private void handleClientDisconnect(SocketChannel clientChannel) {
        String clientId = clients.remove(clientChannel);
        Log.i(TAG, "Client déconnecté: " + clientId);
        
        try {
            clientChannel.close();
        } catch (IOException e) {
            Log.e(TAG, "Erreur fermeture client", e);
        }
    }
    
    /**
     * Arrête le serveur
     */
    public void stop() {
        isRunning = false;
        
        try {
            if (serverChannel != null) {
                serverChannel.close();
            }
            if (selector != null) {
                selector.close();
            }
            executor.shutdown();
            Log.i(TAG, "Serveur WebSocket arrêté");
        } catch (IOException e) {
            Log.e(TAG, "Erreur arrêt serveur", e);
        }
    }
    
    /**
     * Vérifie si le serveur est en cours d'exécution
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Retourne le nombre de clients connectés
     */
    public int getConnectedClientsCount() {
        return clients.size();
    }
}
