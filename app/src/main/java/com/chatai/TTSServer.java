package com.chatai;

import android.content.Context;
import android.util.Log;
import com.chatai.managers.OnnxTTSManager;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import org.json.JSONObject;

/**
 * 🔊 SERVEUR TTS NATIF ANDROID
 * 
 * Serveur HTTP natif pour synthèse vocale (sans Termux)
 * Port: 11401
 * Endpoint: POST /synthesize (JSON: {"text": "..."}) → Audio WAV
 * 
 * ARCHITECTURE:
 * Android App → HTTP POST → TTSServer (port 11401) → OnnxTTSManager → Audio WAV → Réponse
 * 
 * SIMILAIRE À: WebServer (même architecture native)
 */
public class TTSServer {
    private static final String TAG = "TTSServer";
    private static final int PORT = 11401;
    
    private Context context;
    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private Thread serverThread;
    private OnnxTTSManager onnxTTSManager;
    private int actualPort = PORT; // Port réel utilisé (peut différer si PORT est occupé)
    
    public TTSServer(Context context) {
        this.context = context;
    }
    
    /**
     * Démarre le serveur TTS
     */
    public void start() {
        if (isRunning || serverSocket != null) {
            Log.w(TAG, "Serveur TTS déjà en cours, fermeture avant redémarrage");
            stop();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        try {
            // Initialiser OnnxTTSManager
            onnxTTSManager = new OnnxTTSManager(context, new OnnxTTSManager.TTSListener() {
                @Override
                public void onTTSReady() {
                    Log.i(TAG, "✅ OnnxTTSManager prêt");
                }
                
                @Override
                public void onTTSStart(String utteranceId) {
                    Log.d(TAG, "TTS démarré: " + utteranceId);
                }
                
                @Override
                public void onTTSDone(String utteranceId) {
                    Log.d(TAG, "TTS terminé: " + utteranceId);
                }
                
                @Override
                public void onTTSError(String utteranceId) {
                    Log.e(TAG, "TTS erreur: " + utteranceId);
                }
            });
            // Initialiser OnnxTTSManager (peut échouer si modèles manquants)
            Log.i(TAG, "Initialisation OnnxTTSManager...");
            onnxTTSManager.initialize();
            
            // Vérifier si l'initialisation a réussi
            if (onnxTTSManager.isONNXReady()) {
                Log.i(TAG, "✅ OnnxTTSManager prêt (modèles chargés)");
            } else {
                Log.w(TAG, "⚠️ OnnxTTSManager non prêt (modèles manquants ou erreur)");
                Log.w(TAG, "⚠️ Le serveur démarrera mais les requêtes de synthèse échoueront");
                Log.w(TAG, "⚠️ Vérifiez que les modèles ONNX sont dans: /storage/emulated/0/ChatAI-Files/models/tts/");
            }
            
            // Essayer de démarrer sur le port configuré, avec fallback si occupé
            int portToTry = PORT;
            int maxAttempts = 10; // Essayer jusqu'à 10 ports consécutifs
            
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                try {
                    serverSocket = new ServerSocket(portToTry);
                    actualPort = portToTry;
                    isRunning = true;
                    
                    serverThread = new Thread(() -> {
                        Log.i(TAG, "Serveur TTS démarré sur le port " + actualPort);
                        
                        while (isRunning && !serverSocket.isClosed()) {
                            try {
                                Socket clientSocket = serverSocket.accept();
                                new Thread(() -> handleClient(clientSocket)).start();
                            } catch (IOException e) {
                                if (isRunning) {
                                    Log.e(TAG, "Erreur acceptation connexion", e);
                                }
                            }
                        }
                    });
                    
                    serverThread.start();
                    if (portToTry != PORT) {
                        Log.w(TAG, "Port " + PORT + " déjà utilisé, utilisation du port " + actualPort);
                    }
                    Log.i(TAG, "Serveur TTS prêt sur http://127.0.0.1:" + actualPort);
                    Log.i(TAG, "État ONNX: " + (onnxTTSManager.isONNXReady() ? "✅ Prêt" : "❌ Non prêt"));
                    return; // Succès, sortir de la boucle
                    
                } catch (java.net.BindException e) {
                    // Port occupé, essayer le suivant
                    if (attempt < maxAttempts - 1) {
                        portToTry++;
                        Log.w(TAG, "Port " + (portToTry - 1) + " déjà utilisé, tentative avec port " + portToTry);
                    } else {
                        Log.e(TAG, "Erreur démarrage serveur TTS: aucun port disponible entre " + PORT + " et " + (PORT + maxAttempts - 1), e);
                        isRunning = false;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Erreur démarrage serveur TTS", e);
                    isRunning = false;
                    return; // Erreur autre que BindException, arrêter
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur initialisation serveur TTS", e);
            isRunning = false;
        }
    }
    
    /**
     * Arrête le serveur TTS
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Erreur arrêt serveur TTS", e);
        }
        
        if (serverThread != null) {
            serverThread.interrupt();
        }
        
        if (onnxTTSManager != null) {
            onnxTTSManager.shutdown();
            onnxTTSManager = null;
        }
        
        Log.i(TAG, "Serveur TTS arrêté");
    }
    
    /**
     * Retourne le port réel utilisé (peut différer de PORT si le port par défaut était occupé)
     */
    public int getActualPort() {
        return actualPort;
    }
    
    /**
     * Vérifie si le serveur est en cours d'exécution
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Gère une connexion client
     */
    private void handleClient(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream outputStream = clientSocket.getOutputStream()) {
            
            // Lire la requête HTTP
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                sendError(outputStream, 400, "Bad Request");
                return;
            }
            
            String method = parts[0];
            String path = parts[1];
            
            Log.d(TAG, "Requête: " + method + " " + path);
            
            // Lire les headers
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            
            // Traiter les endpoints
            if (path.equals("/health") && method.equals("GET")) {
                sendHealth(outputStream);
            } else if (path.equals("/models") && method.equals("GET")) {
                sendModelsStatus(outputStream);
            } else if (path.equals("/synthesize") && method.equals("POST")) {
                // Lire le body JSON
                StringBuilder body = new StringBuilder();
                if (contentLength > 0) {
                    char[] buffer = new char[contentLength];
                    int read = reader.read(buffer, 0, contentLength);
                    if (read > 0) {
                        body.append(buffer, 0, read);
                    }
                }
                
                String jsonText = body.toString();
                Log.d(TAG, "JSON reçu: " + jsonText);
                
                try {
                    JSONObject json = new JSONObject(jsonText);
                    String text = json.optString("text", "");
                    String voice = json.optString("voice", null); // "male", "female", "default" ou null
                    
                    if (text.isEmpty()) {
                        sendError(outputStream, 400, "Text is required");
                        return;
                    }
                    
                    // Synthétiser avec OnnxTTSManager (avec paramètre voix)
                    synthesizeAndSend(outputStream, text, voice);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Erreur parsing JSON", e);
                    sendError(outputStream, 400, "Invalid JSON: " + e.getMessage());
                }
            } else {
                sendError(outputStream, 404, "Not Found");
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Erreur traitement requête", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Erreur fermeture socket", e);
            }
        }
    }
    
    /**
     * Synthétise le texte et envoie l'audio WAV
     * 
     * @param text Texte à synthétiser
     * @param voice Type de voix ("male", "female", "default" ou null)
     */
    private void synthesizeAndSend(OutputStream outputStream, String text, String voice) {
        try {
            // Vérifier que le manager est initialisé
            if (onnxTTSManager == null) {
                Log.e(TAG, "OnnxTTSManager null - non initialisé");
                sendError(outputStream, 503, "TTS Service Unavailable: Manager not initialized");
                return;
            }
            
            // Vérifier que les modèles sont chargés
            if (!onnxTTSManager.isONNXReady()) {
                Log.e(TAG, "ONNX TTS not ready - modèles non chargés");
                sendError(outputStream, 503, "TTS Service Unavailable: Models not loaded. Check logcat for details.");
                return;
            }
            
            Log.d(TAG, "Synthèse vocale demandée: \"" + text + "\" (voix: " + (voice != null ? voice : "default") + ")");
            
            // Synthétiser et obtenir audio WAV (avec paramètre voix)
            byte[] audioWav = onnxTTSManager.synthesizeToWav(text, voice);
            
            if (audioWav == null || audioWav.length == 0) {
                Log.e(TAG, "Synthèse échouée: audioWav null ou vide");
                sendError(outputStream, 500, "TTS synthesis failed: No audio generated");
                return;
            }
            
            Log.i(TAG, "✅ Synthèse réussie: " + audioWav.length + " bytes");
            
            // Envoyer réponse HTTP avec audio WAV
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: audio/wav\r\n" +
                    "Content-Length: " + audioWav.length + "\r\n" +
                    "\r\n";
            
            outputStream.write(response.getBytes("UTF-8"));
            outputStream.write(audioWav);
            outputStream.flush();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur synthèse: " + e.getMessage(), e);
            sendError(outputStream, 500, "Internal Server Error: " + e.getMessage());
        }
    }
    
    /**
     * Envoie la réponse health check (vérifie l'état réel)
     */
    private void sendHealth(OutputStream outputStream) throws IOException {
        boolean modelLoaded = (onnxTTSManager != null && onnxTTSManager.isONNXReady());
        String status = modelLoaded ? "ok" : "not_ready";
        
        String jsonBody = String.format("{\"status\":\"%s\",\"model_loaded\":%s,\"server_running\":%s}\n",
                status, modelLoaded, isRunning);
        
        byte[] jsonBytes = jsonBody.getBytes("UTF-8");
        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + jsonBytes.length + "\r\n" +
                "\r\n";
        
        outputStream.write(response.getBytes("UTF-8"));
        outputStream.write(jsonBytes);
        outputStream.flush();
        
        Log.d(TAG, "Health check: status=" + status + ", model_loaded=" + modelLoaded);
    }
    
    /**
     * Envoie les statuts détaillés des modèles ONNX (Encoder, Decoder, Vocoder)
     */
    private void sendModelsStatus(OutputStream outputStream) throws IOException {
        try {
            JSONObject modelsJson = new JSONObject();
            
            if (onnxTTSManager != null) {
                modelsJson.put("encoder", onnxTTSManager.isEncoderReady());
                modelsJson.put("decoder", onnxTTSManager.isDecoderReady());
                modelsJson.put("vocoder", onnxTTSManager.isVocoderReady());
                modelsJson.put("tokenizer", onnxTTSManager.isTokenizerReady());
                modelsJson.put("all_ready", onnxTTSManager.isONNXReady());
            } else {
                modelsJson.put("encoder", false);
                modelsJson.put("decoder", false);
                modelsJson.put("vocoder", false);
                modelsJson.put("tokenizer", false);
                modelsJson.put("all_ready", false);
            }
            
            String jsonBody = modelsJson.toString() + "\n";
            byte[] jsonBytes = jsonBody.getBytes("UTF-8");
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + jsonBytes.length + "\r\n" +
                    "\r\n";
            
            outputStream.write(response.getBytes("UTF-8"));
            outputStream.write(jsonBytes);
            outputStream.flush();
            
            Log.d(TAG, "Models status sent: " + jsonBody);
        } catch (Exception e) {
            Log.e(TAG, "Erreur envoi statuts modèles", e);
            sendError(outputStream, 500, "Error getting models status: " + e.getMessage());
        }
    }
    
    /**
     * Envoie une erreur HTTP
     */
    private void sendError(OutputStream outputStream, int code, String message) {
        try {
            String body = "{\"error\":\"" + message + "\"}\n";
            String response = "HTTP/1.1 " + code + " " + getStatusText(code) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + body.length() + "\r\n" +
                    "\r\n" +
                    body;
            outputStream.write(response.getBytes("UTF-8"));
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Erreur envoi erreur HTTP", e);
        }
    }
    
    private String getStatusText(int code) {
        switch (code) {
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            case 501: return "Not Implemented";
            default: return "Error";
        }
    }
}

