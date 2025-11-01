package com.chatai;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serveur de fichiers local avec gestion des emplacements personnalisés
 * Permet à l'utilisateur de choisir le répertoire de stockage
 */
public class FileServer {
    private static final String TAG = "FileServer";
    private static final int FILE_PORT = 8082;
    private static final int BUFFER_SIZE = 8192;
    
    private Context context;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ExecutorService executor;
    private boolean isRunning = false;
    private SecureConfig secureConfig;
    private ChatDatabase chatDatabase;
    
    // Répertoire de stockage configurable
    private File storageDirectory;
    private String currentStoragePath;
    
    public FileServer(Context context) {
        this.context = context;
        this.secureConfig = new SecureConfig(context);
        this.chatDatabase = new ChatDatabase(context);
        this.executor = Executors.newFixedThreadPool(4);
        
        // UTILISER LE MÊME RÉPERTOIRE QUE WEBSERVER
        Log.i(TAG, "=== UTILISATION DU RÉPERTOIRE WEBSERVER ===");
        this.storageDirectory = new File("/storage/emulated/0/ChatAI-Files/sites");
        this.currentStoragePath = "/storage/emulated/0/ChatAI-Files/sites";
        
        // Créer le répertoire
        if (!storageDirectory.exists()) {
            boolean created = storageDirectory.mkdirs();
            Log.i(TAG, "Répertoire forcé créé: " + created);
        }
        
        Log.i(TAG, "✅ CHEMIN WEBSERVER: " + currentStoragePath);
    }
    
    /**
     * FORCE la lecture de la configuration utilisateur depuis TOUS les endroits possibles
     */
    private void forceLoadUserConfiguration() {
        // Chemin fixe : /storage/emulated/0/ChatAI-Files
        String fixedPath = "/storage/emulated/0/ChatAI-Files";
        Log.i(TAG, "Utilisation du chemin fixe: " + fixedPath);
        
        this.storageDirectory = new File(fixedPath);
        this.currentStoragePath = fixedPath;
        
        // Créer le répertoire si nécessaire
        if (!storageDirectory.exists()) {
            boolean created = storageDirectory.mkdirs();
            Log.i(TAG, "Répertoire créé: " + created);
        }
        
        Log.i(TAG, "=== Chemin final: " + currentStoragePath + " ===");
    }
    
    /**
     * Charge le répertoire de stockage configuré par l'utilisateur
     */
    private void loadConfiguredStorage() {
        try {
            // Lire le chemin configuré depuis SharedPreferences normal (ServerConfigurationActivity)
            android.content.SharedPreferences sharedPrefs = context.getSharedPreferences("server_config", android.content.Context.MODE_PRIVATE);
            String configuredPath = sharedPrefs.getString("storage_path", "");
            
            // Si pas trouvé dans SharedPreferences, essayer SecureConfig
            if (configuredPath == null || configuredPath.isEmpty()) {
                configuredPath = secureConfig.getSetting("storage_path", "");
            }
            
            // Debug: Afficher tous les chemins possibles
            Log.i(TAG, "Configuration SharedPreferences: " + configuredPath);
            Log.i(TAG, "Configuration SecureConfig: " + secureConfig.getSetting("storage_path", ""));
            
            if (configuredPath != null && !configuredPath.isEmpty()) {
                // Utiliser le chemin configuré
                this.storageDirectory = new File(configuredPath);
                this.currentStoragePath = configuredPath;
                Log.i(TAG, "Répertoire de stockage configuré chargé: " + currentStoragePath);
            } else {
                // Fallback vers le répertoire par défaut
                Log.w(TAG, "Aucune configuration trouvée, utilisation du défaut");
                initializeDefaultStorage();
                return;
            }
            
            // Vérifier et créer le répertoire si nécessaire
            if (!storageDirectory.exists()) {
                boolean created = storageDirectory.mkdirs();
                if (created) {
                    Log.i(TAG, "Répertoire de stockage créé: " + currentStoragePath);
                } else {
                    Log.w(TAG, "Impossible de créer le répertoire, utilisation du défaut");
                    initializeDefaultStorage();
                    return;
                }
            }
            
            // Vérifier les permissions d'écriture
            if (!storageDirectory.canWrite()) {
                Log.w(TAG, "Pas de permissions d'écriture, utilisation du défaut");
                initializeDefaultStorage();
                return;
            }
            
            Log.i(TAG, "Répertoire de stockage configuré validé: " + currentStoragePath);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur chargement configuration stockage", e);
            initializeDefaultStorage();
        }
    }
    
    /**
     * Initialise le répertoire de stockage par défaut
     */
    private void initializeDefaultStorage() {
        // Répertoire par défaut : /storage/emulated/0/ChatAI-Files
        File externalStorage = Environment.getExternalStorageDirectory();
        this.storageDirectory = new File(externalStorage, "ChatAI-Files");
        
        if (!storageDirectory.exists()) {
            storageDirectory.mkdirs();
        }
        
        this.currentStoragePath = storageDirectory.getAbsolutePath();
        Log.i(TAG, "Répertoire de stockage par défaut initialisé: " + currentStoragePath);
    }
    
    /**
     * Démarre le serveur de fichiers
     */
    public void start() {
        // Fermer proprement le serveur existant s'il y en a un
        if (isRunning || serverChannel != null) {
            Log.w(TAG, "Serveur de fichiers déjà en cours, fermeture avant redémarrage");
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
                Log.e(TAG, "Erreur démarrage serveur de fichiers", e);
            }
        });
    }
    
    private void startServer() throws IOException {
        // Lire le port depuis la configuration utilisateur
        int configuredPort = secureConfig.getIntSetting("file_port", FILE_PORT);
        int currentPort = configuredPort;
        
        // Essayer de démarrer sur le port configuré ou un port alternatif
        while (currentPort <= configuredPort + 10) {
            try {
                serverChannel = ServerSocketChannel.open();
                // Écouter sur toutes les interfaces (0.0.0.0) pour permettre l'accès externe
                serverChannel.bind(new java.net.InetSocketAddress("0.0.0.0", currentPort));
                serverChannel.configureBlocking(false);
                
                selector = Selector.open();
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);
                
                isRunning = true;
                Log.i(TAG, "Serveur de fichiers démarré sur le port " + currentPort);
                break;
                
            } catch (java.net.BindException e) {
                Log.w(TAG, "Port " + currentPort + " déjà utilisé, tentative avec port " + (currentPort + 1));
                currentPort++;
                if (currentPort > configuredPort + 10) {
                    Log.e(TAG, "Impossible de démarrer le serveur de fichiers, tous les ports sont occupés");
                    isRunning = false;
                    return;
                }
            }
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
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Erreur dans la boucle du sélecteur de fichiers", e);
                stop();
            }
        }
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = server.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        Log.d(TAG, "Nouvelle connexion fichier: " + clientChannel.getRemoteAddress());
    }
    
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        
        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                key.cancel();
                return;
            }
            
            buffer.flip();
            byte[] data = new byte[bytesRead];
            buffer.get(data);
            String request = new String(data, "UTF-8");
            
            Log.d(TAG, "Requête fichier reçue: " + request.substring(0, Math.min(100, request.length())));
            
            String response = processFileRequest(request);
            sendFileResponse(clientChannel, response);
            
        } catch (IOException e) {
            Log.w(TAG, "Client fichier déconnecté: " + clientChannel.getRemoteAddress());
            clientChannel.close();
            key.cancel();
        }
    }
    
    private String processFileRequest(String request) {
        try {
            // Parser la requête HTTP basique
            String[] lines = request.split("\n");
            if (lines.length == 0) {
                return createFileErrorResponse(400, "Bad Request");
            }
            
            String requestLine = lines[0];
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return createFileErrorResponse(400, "Bad Request");
            }
            
            String method = parts[0];
            String path = parts[1];
            
            Log.d(TAG, "Requête fichier: " + method + " " + path);
            
            // Router les requêtes
            if (method.equals("GET")) {
                return handleFileGetRequest(path);
            } else if (method.equals("POST")) {
                String body = extractRequestBody(request);
                return handleFilePostRequest(path, body);
            } else if (method.equals("DELETE")) {
                return handleFileDeleteRequest(path);
            } else if (method.equals("OPTIONS")) {
                // Gestion des requêtes CORS preflight
                return createFileCorsPreflightResponse();
            } else {
                return createFileErrorResponse(405, "Method Not Allowed");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur traitement requête fichier", e);
            return createFileErrorResponse(500, "Internal Server Error");
        }
    }
    
    private String handleFileGetRequest(String path) {
        // API Endpoints pour les fichiers
        if (path.equals("/api/files/list")) {
            return handleListFiles();
        }
        else if (path.equals("/api/files/storage/info")) {
            return handleStorageInfo();
        }
        else if (path.startsWith("/api/files/download/")) {
            String fileName = path.substring("/api/files/download/".length());
            return handleDownloadFile(fileName);
        }
        else if (path.startsWith("/api/files/info/")) {
            String fileName = path.substring("/api/files/info/".length());
            return handleFileInfo(fileName);
        }
        else {
            return createFileErrorResponse(404, "Not Found");
        }
    }
    
    private String handleFilePostRequest(String path, String body) {
        if (path.equals("/api/files/upload")) {
            return handleUploadFile(body);
        }
        else if (path.equals("/api/files/storage/change")) {
            return handleChangeStorage(body);
        }
        else {
            return createFileErrorResponse(404, "Not Found");
        }
    }
    
    private String handleFileDeleteRequest(String path) {
        if (path.startsWith("/api/files/delete/")) {
            String fileName = path.substring("/api/files/delete/".length());
            return handleDeleteFile(fileName);
        }
        else {
            return createFileErrorResponse(404, "Not Found");
        }
    }
    
    // ========== HANDLERS FICHIERS ==========
    
    public String handleListFiles() {
        try {
            List<Map<String, Object>> files = new ArrayList<>();
            
            if (storageDirectory.exists() && storageDirectory.isDirectory()) {
                File[] fileList = storageDirectory.listFiles();
                if (fileList != null) {
                    for (File file : fileList) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("size", file.length());
                        fileInfo.put("isDirectory", file.isDirectory());
                        fileInfo.put("lastModified", file.lastModified());
                        fileInfo.put("path", file.getAbsolutePath());
                        files.add(fileInfo);
                    }
                }
            }
            
            String response = "{\"files\":" + files.toString() + ",\"storagePath\":\"" + currentStoragePath + "\"}";
            return createFileApiResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur liste fichiers", e);
            return createFileErrorResponse(500, "Error listing files");
        }
    }
    
    /**
     * Retourne seulement le JSON des fichiers (sans headers HTTP)
     */
    public String getFilesJson() {
        try {
            List<Map<String, Object>> files = new ArrayList<>();
            
            if (storageDirectory.exists() && storageDirectory.isDirectory()) {
                File[] fileList = storageDirectory.listFiles();
                if (fileList != null) {
                    for (File file : fileList) {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("size", file.length());
                        fileInfo.put("isDirectory", file.isDirectory());
                        fileInfo.put("lastModified", file.lastModified());
                        fileInfo.put("path", file.getAbsolutePath());
                        files.add(fileInfo);
                    }
                }
            }
            
            return "{\"files\":" + files.toString() + ",\"storagePath\":\"" + currentStoragePath + "\"}";
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur JSON fichiers", e);
            return "{\"files\":[],\"storagePath\":\"" + currentStoragePath + "\"}";
        }
    }
    
    public String handleStorageInfo() {
        try {
            long totalSpace = storageDirectory.getTotalSpace();
            long freeSpace = storageDirectory.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            
            String response = String.format(
                "{\"storagePath\":\"%s\",\"totalSpace\":%d,\"freeSpace\":%d,\"usedSpace\":%d,\"fileCount\":%d}",
                currentStoragePath,
                totalSpace,
                freeSpace,
                usedSpace,
                getFileCount()
            );
            
            return createFileApiResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur info stockage", e);
            return createFileErrorResponse(500, "Error getting storage info");
        }
    }
    
    public String handleDownloadFile(String fileName) {
        try {
            String safeFileName = SecurityUtils.sanitizeFileName(fileName);
            File file = new File(storageDirectory, safeFileName);
            
            if (!file.exists() || !file.isFile()) {
                return createFileErrorResponse(404, "File not found");
            }
            
            // Pour l'instant, retourner les infos du fichier
            // L'implémentation complète du download nécessiterait plus de code
            String response = String.format(
                "{\"fileName\":\"%s\",\"size\":%d,\"path\":\"%s\",\"downloadUrl\":\"/api/files/download/%s\"}",
                safeFileName,
                file.length(),
                file.getAbsolutePath(),
                safeFileName
            );
            
            return createFileApiResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur download fichier", e);
            return createFileErrorResponse(500, "Error downloading file");
        }
    }
    
    public String handleFileInfo(String fileName) {
        try {
            String safeFileName = SecurityUtils.sanitizeFileName(fileName);
            File file = new File(storageDirectory, safeFileName);
            
            if (!file.exists()) {
                return createFileErrorResponse(404, "File not found");
            }
            
            String response = String.format(
                "{\"name\":\"%s\",\"size\":%d,\"isDirectory\":%s,\"lastModified\":%d,\"path\":\"%s\"}",
                safeFileName,
                file.length(),
                file.isDirectory(),
                file.lastModified(),
                file.getAbsolutePath()
            );
            
            return createFileApiResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur info fichier", e);
            return createFileErrorResponse(500, "Error getting file info");
        }
    }
    
    public String handleUploadFile(String body) {
        try {
            // Parser JSON simple pour l'upload
            String fileName = extractJsonValue(body, "fileName");
            String content = extractJsonValue(body, "content");
            
            if (fileName == null || content == null) {
                return createFileErrorResponse(400, "Missing fileName or content");
            }
            
            String safeFileName = SecurityUtils.sanitizeFileName(fileName);
            File file = new File(storageDirectory, safeFileName);
            
            // Écrire le contenu dans le fichier
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(content.getBytes("UTF-8"));
            }
            
            String response = String.format(
                "{\"success\":true,\"fileName\":\"%s\",\"size\":%d,\"path\":\"%s\"}",
                safeFileName,
                file.length(),
                file.getAbsolutePath()
            );
            
            return createFileApiResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur upload fichier", e);
            return createFileErrorResponse(500, "Error uploading file");
        }
    }
    
    public String handleChangeStorage(String body) {
        try {
            String newPath = extractJsonValue(body, "newPath");
            
            if (newPath == null) {
                return createFileErrorResponse(400, "Missing newPath");
            }
            
            File newDirectory = new File(newPath);
            
            // Vérifier si le répertoire existe, sinon le créer
            if (!newDirectory.exists()) {
                Log.i(TAG, "Création du répertoire via API: " + newPath);
                boolean created = newDirectory.mkdirs();
                if (!created) {
                    Log.e(TAG, "Impossible de créer le répertoire: " + newPath);
                    return createFileErrorResponse(500, "Cannot create directory: " + newPath);
                }
            }
            
            // Vérifier que c'est un répertoire et qu'on peut y écrire
            if (!newDirectory.isDirectory() || !newDirectory.canWrite()) {
                Log.w(TAG, "Répertoire invalide ou inaccessible: " + newPath);
                Log.w(TAG, "isDirectory: " + newDirectory.isDirectory() + ", canWrite: " + newDirectory.canWrite());
                return createFileErrorResponse(400, "Invalid or inaccessible directory: " + newPath);
            }
            
            // Changer le répertoire de stockage
            this.storageDirectory = newDirectory;
            this.currentStoragePath = newDirectory.getAbsolutePath();
            
            // Sauvegarder le nouveau chemin
            secureConfig.saveSetting("file_storage_path", currentStoragePath);
            
            String response = String.format(
                "{\"success\":true,\"newPath\":\"%s\",\"message\":\"Storage directory changed successfully\"}",
                currentStoragePath
            );
            
            Log.i(TAG, "Répertoire de stockage changé vers: " + currentStoragePath);
            return createFileApiResponse(response);
            
        } catch (SecurityException e) {
            Log.e(TAG, "Erreur de sécurité - permissions insuffisantes", e);
            return createFileErrorResponse(403, "Insufficient permissions to access directory");
        } catch (Exception e) {
            Log.e(TAG, "Erreur changement stockage", e);
            return createFileErrorResponse(500, "Error changing storage directory: " + e.getMessage());
        }
    }
    
    private String handleDeleteFile(String fileName) {
        try {
            String safeFileName = SecurityUtils.sanitizeFileName(fileName);
            File file = new File(storageDirectory, safeFileName);
            
            if (!file.exists()) {
                return createFileErrorResponse(404, "File not found");
            }
            
            boolean deleted = file.delete();
            
            if (deleted) {
                String response = String.format(
                    "{\"success\":true,\"message\":\"File %s deleted successfully\"}",
                    safeFileName
                );
                return createFileApiResponse(response);
            } else {
                return createFileErrorResponse(500, "Failed to delete file");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur suppression fichier", e);
            return createFileErrorResponse(500, "Error deleting file");
        }
    }
    
    // ========== UTILITAIRES ==========
    
    private int getFileCount() {
        try {
            if (storageDirectory.exists() && storageDirectory.isDirectory()) {
                File[] files = storageDirectory.listFiles();
                return files != null ? files.length : 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur comptage fichiers", e);
        }
        return 0;
    }
    
    private String extractRequestBody(String request) {
        String[] parts = request.split("\r\n\r\n");
        return parts.length > 1 ? parts[1] : "";
    }
    
    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    private void sendFileResponse(SocketChannel clientChannel, String response) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(response.getBytes("UTF-8"));
            clientChannel.write(buffer);
            clientChannel.close();
        } catch (IOException e) {
            Log.e(TAG, "Erreur envoi réponse fichier", e);
        }
    }
    
    private String createFileApiResponse(String jsonBody) {
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: application/json\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
               "Access-Control-Allow-Headers: Content-Type\r\n" +
               "Content-Length: " + jsonBody.length() + "\r\n" +
               "\r\n" +
               jsonBody;
    }
    
    private String createFileErrorResponse(int code, String message) {
        String body = "{\"error\":" + code + ",\"message\":\"" + message + "\"}";
        return "HTTP/1.1 " + code + " " + message + "\r\n" +
               "Content-Type: application/json\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Content-Length: " + body.length() + "\r\n" +
               "\r\n" +
               body;
    }
    
    private String createFileCorsPreflightResponse() {
        return "HTTP/1.1 200 OK\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n" +
               "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
               "Access-Control-Max-Age: 86400\r\n" +
               "Content-Length: 0\r\n" +
               "\r\n";
    }
    
    /**
     * Ouvre le sélecteur de répertoire pour que l'utilisateur choisisse l'emplacement
     */
    public void openDirectoryPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            
            // Démarrer l'activité de sélection
            if (context instanceof android.app.Activity) {
                ((android.app.Activity) context).startActivityForResult(intent, 1001);
            }
            
            Log.i(TAG, "Sélecteur de répertoire ouvert");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur ouverture sélecteur répertoire", e);
            Toast.makeText(context, "Erreur ouverture sélecteur répertoire", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Change le répertoire de stockage
     */
    public void changeStorageDirectory(String newPath) {
        try {
            // Convertir les chemins SAF en chemins directs
            String directPath = convertSAFPathToDirect(newPath);
            File newDirectory = new File(directPath);
            
            Log.i(TAG, "Chemin original: " + newPath);
            Log.i(TAG, "Chemin converti: " + directPath);
            
            // Vérifier si le répertoire existe, sinon le créer
            if (!newDirectory.exists()) {
                Log.i(TAG, "Création du répertoire: " + directPath);
                boolean created = newDirectory.mkdirs();
                if (!created) {
                    Log.e(TAG, "Impossible de créer le répertoire: " + directPath);
                    Toast.makeText(context, "Impossible de créer le répertoire: " + directPath, Toast.LENGTH_LONG).show();
                    return;
                }
            }
            
            // Vérifier que c'est un répertoire et qu'on peut y écrire
            if (newDirectory.isDirectory() && newDirectory.canWrite()) {
                this.storageDirectory = newDirectory;
                this.currentStoragePath = newDirectory.getAbsolutePath();
                
                // Sauvegarder le nouveau chemin
                secureConfig.saveSetting("file_storage_path", currentStoragePath);
                
                Log.i(TAG, "Répertoire de stockage changé vers: " + currentStoragePath);
                Toast.makeText(context, "Répertoire changé: " + currentStoragePath, Toast.LENGTH_LONG).show();
            } else {
                Log.w(TAG, "Répertoire invalide ou inaccessible: " + directPath);
                Log.w(TAG, "isDirectory: " + newDirectory.isDirectory() + ", canWrite: " + newDirectory.canWrite());
                Toast.makeText(context, "Répertoire invalide ou inaccessible: " + directPath, Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erreur de sécurité - permissions insuffisantes: " + newPath, e);
            Toast.makeText(context, "Permissions insuffisantes pour accéder au répertoire", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Erreur changement répertoire", e);
            Toast.makeText(context, "Erreur changement répertoire: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Convertit un chemin SAF en chemin direct
     */
    private String convertSAFPathToDirect(String path) {
        if (path == null) return "/storage/emulated/0/ChatAI-Files";
        
        if (path.startsWith("/tree/primary:")) {
            // Convertir /tree/primary:site/unified en /storage/emulated/0/site/unified
            String relativePath = path.substring("/tree/primary:".length());
            return "/storage/emulated/0/" + relativePath;
        } else if (path.startsWith("/storage/")) {
            // Déjà un chemin direct
            return path;
        } else {
            // Chemin relatif, l'ajouter au stockage externe
            return "/storage/emulated/0/" + path;
        }
    }
    
    /**
     * Obtient le répertoire de stockage actuel
     */
    public String getCurrentStoragePath() {
        return currentStoragePath;
    }
    
    /**
     * Obtient le répertoire de stockage actuel
     */
    public File getStorageDirectory() {
        return storageDirectory;
    }
    
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
        try {
            if (selector != null) selector.close();
            if (serverChannel != null) serverChannel.close();
        } catch (IOException e) {
            Log.e(TAG, "Erreur arrêt serveur de fichiers", e);
        }
        Log.i(TAG, "Serveur de fichiers arrêté");
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public int getPort() {
        return FILE_PORT;
    }
    
    /**
     * Force le rechargement de la configuration de stockage
     */
    public void reloadStorageConfiguration() {
        Log.i(TAG, "Rechargement forcé de la configuration de stockage...");
        loadConfiguredStorage();
    }
}
