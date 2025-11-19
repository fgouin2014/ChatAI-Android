package com.chatai;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.chatai.hotword.HotwordAssetProvider;

/**
 * Serveur HTTP local pour les plugins et API
 * Gère les requêtes HTTP pour les fonctionnalités étendues
 */
public class HttpServer {
    private static final String TAG = "HttpServer";
    private static final int HTTP_PORT = 8080;
    private static final int BUFFER_SIZE = 4096;
    private static final boolean VERBOSE_HTTP_LOGS = false;
    
    private Context context;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ExecutorService executor;
    private boolean isRunning = false;
    private SecureConfig secureConfig;
    private ChatDatabase chatDatabase;
    private FileServer fileServer;
    
    public HttpServer(Context context) {
        this.context = context;
        this.secureConfig = new SecureConfig(context);
        this.chatDatabase = new ChatDatabase(context);
        this.executor = Executors.newFixedThreadPool(4);
    }
    
    private static void logVerbose(String message) {
        if (VERBOSE_HTTP_LOGS) {
            Log.d(TAG, message);
        }
    }
    
    /**
     * Définit la référence au FileServer
     */
    public void setFileServer(FileServer fileServer) {
        this.fileServer = fileServer;
    }
    
    /**
     * Démarre le serveur HTTP
     */
    public void start() {
        // Fermer proprement le serveur existant s'il y en a un
        if (isRunning || serverChannel != null) {
            Log.w(TAG, "Serveur HTTP déjà en cours, fermeture avant redémarrage");
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
                Log.e(TAG, "Erreur démarrage serveur HTTP", e);
            }
        });
    }
    
    private void startServer() throws IOException {
        // Lire le port depuis la configuration utilisateur
        int configuredPort = secureConfig.getIntSetting("http_port", HTTP_PORT);
        int currentPort = configuredPort;
        
        // Essayer de démarrer sur le port configuré ou un port alternatif
        while (currentPort <= configuredPort + 10) {
            try {
                serverChannel = ServerSocketChannel.open();
                // Écouter sur toutes les interfaces (0.0.0.0) pour permettre l'accès externe
                serverChannel.bind(new InetSocketAddress("0.0.0.0", currentPort));
                serverChannel.configureBlocking(false);
                
                selector = Selector.open();
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);
                
                isRunning = true;
                Log.i(TAG, "Serveur HTTP démarré sur le port " + currentPort);
                break;
                
            } catch (java.net.BindException e) {
                Log.w(TAG, "Port " + currentPort + " déjà utilisé, tentative avec port " + (currentPort + 1));
                currentPort++;
                if (currentPort > configuredPort + 10) {
                    Log.e(TAG, "Impossible de démarrer le serveur HTTP, tous les ports sont occupés");
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
                Log.e(TAG, "Erreur dans la boucle du sélecteur HTTP", e);
                stop();
            }
        }
    }
    
    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = server.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        logVerbose("Nouvelle connexion HTTP: " + clientChannel.getRemoteAddress());
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
            
            logVerbose("Requête HTTP reçue: " + request.substring(0, Math.min(100, request.length())));
            
            String response = processHttpRequest(request);
            sendHttpResponse(clientChannel, response);
            
        } catch (IOException e) {
            Log.w(TAG, "Client HTTP déconnecté: " + clientChannel.getRemoteAddress());
            clientChannel.close();
            key.cancel();
        }
    }
    
    private String processHttpRequest(String request) {
        try {
            // Parser la requête HTTP basique
            String[] lines = request.split("\n");
            if (lines.length == 0) {
                return createHttpErrorResponse(400, "Bad Request");
            }
            
            String requestLine = lines[0];
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return createHttpErrorResponse(400, "Bad Request");
            }
            
            String method = parts[0];
            String path = parts[1];
            
            logVerbose("Requête: " + method + " " + path);
            
            // Router les requêtes
            if (method.equals("GET")) {
                return handleGetRequest(path);
            } else if (method.equals("POST")) {
                String body = extractRequestBody(request);
                return handlePostRequest(path, body);
            } else if (method.equals("OPTIONS")) {
                // Gestion des requêtes CORS preflight
                return createCorsPreflightResponse();
            } else {
                return createHttpErrorResponse(405, "Method Not Allowed");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur traitement requête HTTP", e);
            return createHttpErrorResponse(500, "Internal Server Error");
        }
    }
    
    private String handleGetRequest(String path) {
        // Décoder l'URL complète d'abord
        String decodedPath = path;
        try {
            decodedPath = java.net.URLDecoder.decode(path, "UTF-8");
        } catch (Exception e) {
            Log.w(TAG, "Erreur décodage URL: " + path, e);
        }
        
        // Extraire le path sans les paramètres de query string
        String cleanPath = decodedPath;
        if (decodedPath.contains("?")) {
            cleanPath = decodedPath.substring(0, decodedPath.indexOf("?"));
        }
        
        // Résoudre les chemins relatifs avec ../ pour les sites utilisateur
        if (cleanPath.startsWith("/sites/") || cleanPath.startsWith("/user-sites/")) {
            String sitePath = cleanPath.startsWith("/sites/") ? 
                cleanPath.substring("/sites/".length()) : 
                cleanPath.substring("/user-sites/".length());
            String resolvedSitePath = resolveRelativePath(sitePath);
            cleanPath = cleanPath.startsWith("/sites/") ? 
                "/sites/" + resolvedSitePath : 
                "/user-sites/" + resolvedSitePath;
            logVerbose("Path résolu pour site: " + sitePath + " -> " + resolvedSitePath);
        }
        
        // Sécurité : vérifier le path pour éviter directory traversal
        logVerbose("Path original: " + path);
        logVerbose("Path décodé: " + decodedPath);
        logVerbose("Path nettoyé: " + cleanPath);
        if (!isValidPath(cleanPath)) {
            Log.w(TAG, "Tentative d'accès non autorisé: " + cleanPath);
            return createHttpErrorResponse(403, "Forbidden");
        }
        
        // API Endpoints
        if (cleanPath.equals("/api/status")) {
            return createApiResponse("{\"status\":\"active\",\"server\":\"ChatAI HTTP Server\",\"version\":\"1.0\"}");
        }
        else if (cleanPath.startsWith("/api/create-site/")) {
            String siteName = cleanPath.substring("/api/create-site/".length());
            String defaultContent = "<html><body><h1>Site " + siteName + "</h1><p>Créé automatiquement</p></body></html>";
            String result = createUserSite(siteName, defaultContent);
            return createApiResponse("{\"result\":\"" + result + "\"}");
        }
        else if (cleanPath.equals("/api/upload")) {
            return handleFileUpload();
        }
        else if (cleanPath.equals("/api/plugins")) {
            return createApiResponse("{\"plugins\":[\"translator\",\"calculator\",\"weather\",\"camera\",\"files\",\"jokes\",\"tips\"]}");
        }
        else if (cleanPath.equals("/api/hotword/assets")) {
            return handleHotwordAssetsRequest();
        }
        else if (cleanPath.equals("/api/config/ai")) {
            return handleAiConfigGet();
        }
        else if (cleanPath.startsWith("/api/weather/")) {
            String city = cleanPath.substring("/api/weather/".length());
            return handleWeatherRequest(city);
        }
        else if (cleanPath.equals("/api/search")) {
            // Generic web search endpoint: GET /api/search?q=bitcoin
            String queryString = getQueryString(path);
            String query = getQueryParameter(queryString, "q");
            return handleGenericSearchRequest(query);
        }
        else if (cleanPath.equals("/api/jokes/random")) {
            return handleRandomJokeRequest();
        }
        else if (cleanPath.startsWith("/api/tips/")) {
            String category = cleanPath.substring("/api/tips/".length());
            return handleTipsRequest(category);
        }
        else if (cleanPath.equals("/api/health")) {
            return createApiResponse("{\"health\":\"ok\",\"database\":\"connected\",\"cache\":\"active\"}");
        }
        else if (cleanPath.equals("/api/files/list")) {
            return handleListFiles();
        }
        else if (cleanPath.equals("/api/files/storage/info")) {
            return handleStorageInfo();
        }
        else if (cleanPath.startsWith("/api/files/download/")) {
            String fileName = cleanPath.substring("/api/files/download/".length());
            return handleDownloadFile(fileName);
        }
        else if (cleanPath.startsWith("/api/files/info/")) {
            String fileName = cleanPath.substring("/api/files/info/".length());
            return handleFileInfo(fileName);
        }
        // Directory listing
        else if (cleanPath.equals("/files") || cleanPath.equals("/files/") || cleanPath.equals("/browse") || cleanPath.equals("/browse/")) {
            return handleDirectoryListing();
        }
        else if (cleanPath.startsWith("/files/")) {
            String subPath = cleanPath.substring("/files/".length());
            return handleDirectoryListing(subPath);
        }
        // Sites utilisateur - Index façon Apache
        else if (cleanPath.equals("/") || cleanPath.equals("/index.html") ||
                cleanPath.equals("/sites") || cleanPath.equals("/sites/") ||
                cleanPath.equals("/user-sites") || cleanPath.equals("/user-sites/")) {
            return createSitesRootIndex();
        }
        else if (cleanPath.startsWith("/sites/")) {
            String sitePath = cleanPath.substring("/sites/".length());
            return handleUserSite(sitePath);
        }
        else if (cleanPath.startsWith("/user-sites/")) {
            String sitePath = cleanPath.substring("/user-sites/".length());
            return handleUserSite(sitePath);
        }
        // Alias direct /gamelibrary pour compatibilité
        else if (cleanPath.equals("/gamelibrary") || cleanPath.equals("/gamelibrary/")) {
            return handleUserSite("gamelibrary");
        }
        else if (cleanPath.startsWith("/gamelibrary/")) {
            String relativePath = cleanPath.substring("/gamelibrary/".length());
            if (relativePath.isEmpty()) {
                return handleUserSite("gamelibrary");
            }
            return handleUserSite("gamelibrary/" + relativePath);
        }
        // Fichiers statiques (interface web)
        else if (cleanPath.equals("/dashboard") || cleanPath.equals("/dashboard/")) {
            return handleStaticFile("/webapp/index.html");
        }
        else if (cleanPath.equals("/system.html")) {
            return handleStaticFile("/webapp/system.html");
        }
        else if (cleanPath.equals("/chat.js")) {
            return handleStaticFile("/webapp/chat.js");
        }
        else if (isUserSiteRequest(cleanPath)) {
            String sitePath = cleanPath.substring(1); // remove leading "/"
            return handleUserSite(sitePath);
        }
        else {
            return createHttpErrorResponse(404, "Not Found");
        }
    }
    
    private String handlePostRequest(String path, String body) {
        // Extraire le path sans les paramètres de query string
        String cleanPath = path;
        if (path.contains("?")) {
            cleanPath = path.substring(0, path.indexOf("?"));
        }
        
        // API Endpoints spécifiques
        if (cleanPath.equals("/api/translate")) {
            return handleTranslationRequest(body);
        }
        else if (cleanPath.equals("/api/chat")) {
            return handleChatRequest(body);
        }
        else if (cleanPath.equals("/api/ai/query")) {
            return handleAIQueryRequest(body);
        }
        else if (cleanPath.equals("/api/files/upload")) {
            return handleUploadFile(body);
        }
        else if (cleanPath.equals("/api/files/storage/change")) {
            return handleChangeStorage(body);
        }
        else if (cleanPath.equals("/api/config/ai")) {
            return handleAiConfigPost(body);
        }
        // Pour les sites utilisateur, traiter comme GET
        else if (cleanPath.startsWith("/sites/")) {
            String sitePath = cleanPath.substring("/sites/".length());
            return handleUserSite(sitePath);
        }
        else if (cleanPath.startsWith("/user-sites/")) {
            String sitePath = cleanPath.substring("/user-sites/".length());
            return handleUserSite(sitePath);
        }
        else {
            return "HTTP/1.1 404 Not Found\r\n\r\n";
        }
    }
    
    // ========== HANDLERS API ==========
    
    private String handleHotwordAssetsRequest() {
        try {
            JSONArray assets = HotwordAssetProvider.listAssets(context);
            JSONObject payload = new JSONObject();
            payload.put("assets", assets);
            return createApiResponse(payload.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error listing hotword assets", e);
            return createHttpErrorResponse(500, "Error listing hotword assets");
        }
    }

    private String handleWeatherRequest(String city) {
        try {
            String safeCity = SecurityUtils.sanitizeInput(city);
            
            // Récupérer clé API Ollama depuis SecureConfig
            String ollamaApiKey = secureConfig.getOllamaCloudApiKey() != null ? secureConfig.getOllamaCloudApiKey() : "";
            
            if (ollamaApiKey == null || ollamaApiKey.trim().isEmpty()) {
                Log.w(TAG, "Ollama API key not configured - Using fallback data");
                return handleWeatherFallback(safeCity);
            }
            
            // Appeler Ollama web_search pour météo réelle
            String query = "météo " + safeCity + " température actuelle conditions";
            String searchResults = callOllamaWebSearch(query, ollamaApiKey);
            
            if (searchResults == null || searchResults.isEmpty()) {
                Log.w(TAG, "Web search returned no results - Using fallback");
                return handleWeatherFallback(safeCity);
            }
            
            // Parser résultats pour extraire météo (simple extraction pour l'instant)
            // Format: température, condition, humidité, vent
            String response = parseWeatherFromSearch(safeCity, searchResults);
            
            return createApiResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Erreur météo", e);
            return createHttpErrorResponse(500, "Weather service error");
        }
    }
    
    /**
     * Generic web search endpoint - returns raw search results
     * GET /api/search?q=bitcoin price
     */
    private String handleGenericSearchRequest(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return createHttpErrorResponse(400, "Missing query parameter 'q'");
            }
            
            String safeQuery = SecurityUtils.sanitizeInput(query);
            
            // Récupérer clé API Ollama depuis SecureConfig
            String ollamaApiKey = secureConfig.getOllamaCloudApiKey() != null ? secureConfig.getOllamaCloudApiKey() : "";
            
            logVerbose("Generic search - Checking API key in 'chatai_ai_config': " + (ollamaApiKey != null && !ollamaApiKey.isEmpty() ? "FOUND (" + ollamaApiKey.length() + " chars)" : "EMPTY/NULL"));
            
            if (ollamaApiKey == null || ollamaApiKey.trim().isEmpty()) {
                Log.w(TAG, "Ollama API key not configured for generic search");
                return createHttpErrorResponse(503, "Web search service not configured - API key missing");
            }
            
            // Appeler Ollama web_search
            String searchResults = callOllamaWebSearch(safeQuery, ollamaApiKey);
            
            if (searchResults == null || searchResults.isEmpty()) {
                return createApiResponse("{\"query\":\"" + escapeJson(safeQuery) + "\",\"results\":\"\",\"status\":\"no_results\"}");
            }
            
            // Retourner résultats bruts avec ÉCHAPPEMENT JSON COMPLET
            String response = "{\"query\":\"" + escapeJson(safeQuery) + "\",\"results\":\"" + 
                escapeJson(searchResults) + 
                "\",\"status\":\"success\"}";
            
            return createApiResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Generic search error", e);
            return createHttpErrorResponse(500, "Search service error: " + e.getMessage());
        }
    }
    
    /**
     * Échappe correctement TOUS les caractères spéciaux JSON
     * (guillemets, newlines, tabs, backslashes, etc.)
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    // Échapper les caractères de contrôle (< 0x20)
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
    
    /**
     * Extract query string from path
     * Example: /api/search?q=test → "q=test"
     */
    private String getQueryString(String path) {
        int queryIndex = path.indexOf('?');
        if (queryIndex == -1) {
            return "";
        }
        return path.substring(queryIndex + 1);
    }
    
    /**
     * Extract parameter value from query string
     * Example: "q=test&lang=fr" → getQueryParameter("q") → "test"
     */
    private String getQueryParameter(String queryString, String paramName) {
        if (queryString == null || queryString.isEmpty()) {
            return null;
        }
        
        try {
            String[] params = queryString.split("&");
            for (String param : params) {
                String[] keyValue = param.split("=", 2);
                if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                    // URL decode (remplacer + par espace, %20, etc.)
                    String value = keyValue[1].replace("+", " ");
                    value = java.net.URLDecoder.decode(value, "UTF-8");
                    return value;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing query parameter: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Fallback si web_search échoue - données simulées
     */
    private String handleWeatherFallback(String city) {
        int temp = (int)(Math.random() * 25) + 5;
        String[] conditions = {"Ensoleillé ☀️", "Nuageux ☁️", "Pluvieux 🌧️", "Partiellement nuageux ⛅"};
        String condition = conditions[(int)(Math.random() * conditions.length)];
        
        String response = String.format(
            "{\"city\":\"%s\",\"temperature\":%d,\"condition\":\"%s\",\"humidity\":%d,\"wind\":\"%d km/h\",\"source\":\"simulated\"}",
            city, temp, condition, (int)(Math.random() * 40) + 40, (int)(Math.random() * 20) + 5
        );
        
        return createApiResponse(response);
    }
    
    /**
     * Appelle Ollama web_search API
     * Référence: https://docs.ollama.com/capabilities/web-search
     */
    private String callOllamaWebSearch(String query, String apiKey) {
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
            
            // Construire request JSON
            org.json.JSONObject requestBody = new org.json.JSONObject();
            requestBody.put("query", query);
            requestBody.put("max_results", 3); // Juste 3 résultats pour météo
            
            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                requestBody.toString(),
                okhttp3.MediaType.parse("application/json")
            );
            
            okhttp3.Request request = new okhttp3.Request.Builder()
                .url("https://ollama.com/api/web_search")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
            
            logVerbose("Calling Ollama web_search: query=" + query);
            
            okhttp3.Response response = client.newCall(request).execute();
            
            if (!response.isSuccessful()) {
                Log.e(TAG, "Web search API error: HTTP " + response.code());
                return null;
            }
            
            String responseBody = response.body().string();
            
            // Parser résultats
            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
            org.json.JSONArray results = jsonResponse.getJSONArray("results");
            
            // Formater résultats
            StringBuilder formatted = new StringBuilder();
            for (int i = 0; i < results.length(); i++) {
                org.json.JSONObject result = results.getJSONObject(i);
                String content = result.getString("content");
                formatted.append(content).append(" ");
            }
            
            logVerbose("Web search results: " + formatted.length() + " chars");
            return formatted.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Web search exception: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Parse résultats web_search pour extraire infos météo
     */
    private String parseWeatherFromSearch(String city, String searchResults) {
        try {
            String lowerResults = searchResults.toLowerCase();
            
            // Extraire température (chercher patterns: "15°C", "15 degrés", etc.)
            int temperature = 15; // Défaut
            java.util.regex.Pattern tempPattern = java.util.regex.Pattern.compile("(\\d+)\\s*°?\\s*c");
            java.util.regex.Matcher tempMatcher = tempPattern.matcher(lowerResults);
            if (tempMatcher.find()) {
                temperature = Integer.parseInt(tempMatcher.group(1));
            }
            
            // Extraire condition (chercher mots-clés)
            String condition = "Partiellement nuageux";
            if (lowerResults.contains("ensoleillé") || lowerResults.contains("sunny")) {
                condition = "Ensoleillé ☀️";
            } else if (lowerResults.contains("nuageux") || lowerResults.contains("cloudy")) {
                condition = "Nuageux ☁️";
            } else if (lowerResults.contains("pluie") || lowerResults.contains("rain")) {
                condition = "Pluvieux 🌧️";
            } else if (lowerResults.contains("neige") || lowerResults.contains("snow")) {
                condition = "Neige ❄️";
            }
            
            // JSON response
            String response = String.format(
                "{\"city\":\"%s\",\"temperature\":%d,\"condition\":\"%s\",\"humidity\":65,\"wind\":\"15 km/h\",\"source\":\"web_search\"}",
                city, temperature, condition
            );
            
            return response;
            
        } catch (Exception e) {
            Log.e(TAG, "Parse weather error: " + e.getMessage());
            // Fallback: retourner données basiques
            return String.format(
                "{\"city\":\"%s\",\"temperature\":15,\"condition\":\"Conditions inconnues\",\"source\":\"web_search_fallback\"}",
                city
            );
        }
    }
    
    private String handleRandomJokeRequest() {
        String[] jokes = {
            "Pourquoi les plongeurs plongent-ils toujours en arrière ? Parce que sinon, ils tombent dans le bateau !",
            "Comment appelle-t-on un chat tombé dans un pot de peinture ? Un chat-mallow !",
            "Que dit un escargot quand il croise une limace ? 'Regarde le nudiste !'",
            "Pourquoi les oiseaux volent-ils vers le sud en hiver ? Parce que c'est trop loin à pied !"
        };
        
        String joke = jokes[(int)(Math.random() * jokes.length)];
        String response = "{\"joke\":\"" + SecurityUtils.sanitizeInput(joke) + "\"}";
        
        return createApiResponse(response);
    }
    
    private String handleTipsRequest(String category) {
        Map<String, String> tips = new HashMap<>();
        tips.put("productivity", "🍅 Technique Pomodoro : 25 min travail, 5 min pause");
        tips.put("health", "💧 Buvez un verre d'eau dès le réveil");
        tips.put("tech", "🔐 Utilisez un gestionnaire de mots de passe");
        tips.put("lifestyle", "📚 Lisez 10 pages par jour");
        
        String tip = tips.getOrDefault(category, "💡 Conseil non disponible pour cette catégorie");
        String response = "{\"category\":\"" + category + "\",\"tip\":\"" + SecurityUtils.sanitizeInput(tip) + "\"}";
        
        return createApiResponse(response);
    }
    
    private String handleTranslationRequest(String body) {
        try {
            // Parser JSON simple (pour l'exemple)
            String text = extractJsonValue(body, "text");
            String targetLang = extractJsonValue(body, "target");
            
            if (text == null || targetLang == null) {
                return createHttpErrorResponse(400, "Missing text or target language");
            }
            
            // Traduction simulée
            String translatedText = "Traduction de '" + SecurityUtils.sanitizeInput(text) + "' vers " + targetLang;
            String response = "{\"original\":\"" + text + "\",\"translated\":\"" + translatedText + "\",\"target\":\"" + targetLang + "\"}";
            
            return createApiResponse(response);
        } catch (Exception e) {
            Log.e(TAG, "Erreur traduction", e);
            return createHttpErrorResponse(500, "Translation service error");
        }
    }
    
    private String handleChatRequest(String body) {
        try {
            String message = extractJsonValue(body, "message");
            String personality = extractJsonValue(body, "personality");
            
            if (message == null) {
                return createHttpErrorResponse(400, "Missing message");
            }
            
            // Simuler une réponse IA
            String response = "Réponse IA simulée pour: " + SecurityUtils.sanitizeInput(message.substring(0, Math.min(50, message.length())));
            String jsonResponse = "{\"response\":\"" + response + "\",\"personality\":\"" + (personality != null ? personality : "default") + "\"}";
            
            return createApiResponse(jsonResponse);
        } catch (Exception e) {
            Log.e(TAG, "Erreur chat", e);
            return createHttpErrorResponse(500, "Chat service error");
        }
    }
    
    private String handleAIQueryRequest(String body) {
        try {
            String query = extractJsonValue(body, "query");
            if (query == null) {
                return createHttpErrorResponse(400, "Missing query");
            }
            
            // Vérifier le cache d'abord
            String cachedResponse = chatDatabase.getCachedResponse(query);
            if (cachedResponse != null) {
                String response = "{\"response\":\"" + cachedResponse + "\",\"cached\":true}";
                return createApiResponse(response);
            }
            
            // Simuler une requête IA (à remplacer par un vrai appel API)
            String aiResponse = "Réponse IA pour: " + SecurityUtils.sanitizeInput(query.substring(0, Math.min(100, query.length())));
            
            // Sauvegarder dans le cache
            chatDatabase.cacheAIResponse(query, aiResponse);
            
            String response = "{\"response\":\"" + aiResponse + "\",\"cached\":false}";
            return createApiResponse(response);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur requête IA", e);
            return createHttpErrorResponse(500, "AI service error");
        }
    }
    
    // ========== HANDLERS FICHIERS ==========
    
    private String handleListFiles() {
        try {
            // Utiliser le FileServer pour lister les fichiers
            if (fileServer != null) {
                return fileServer.handleListFiles();
            } else {
                return createHttpErrorResponse(503, "File server not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur liste fichiers", e);
            return createHttpErrorResponse(500, "Error listing files");
        }
    }
    
    private String handleStorageInfo() {
        try {
            if (fileServer != null) {
                return fileServer.handleStorageInfo();
            } else {
                return createHttpErrorResponse(503, "File server not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur info stockage", e);
            return createHttpErrorResponse(500, "Error getting storage info");
        }
    }
    
    private String handleDownloadFile(String fileName) {
        try {
            if (fileServer != null) {
                return fileServer.handleDownloadFile(fileName);
            } else {
                return createHttpErrorResponse(503, "File server not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur download fichier", e);
            return createHttpErrorResponse(500, "Error downloading file");
        }
    }
    
    private String handleFileInfo(String fileName) {
        try {
            if (fileServer != null) {
                return fileServer.handleFileInfo(fileName);
            } else {
                return createHttpErrorResponse(503, "File server not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur info fichier", e);
            return createHttpErrorResponse(500, "Error getting file info");
        }
    }
    
    private String handleUploadFile(String body) {
        try {
            if (fileServer != null) {
                return fileServer.handleUploadFile(body);
            } else {
                return createHttpErrorResponse(503, "File server not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur upload fichier", e);
            return createHttpErrorResponse(500, "Error uploading file");
        }
    }
    
    private String handleChangeStorage(String body) {
        try {
            if (fileServer != null) {
                return fileServer.handleChangeStorage(body);
            } else {
                return createHttpErrorResponse(503, "File server not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur changement stockage", e);
            return createHttpErrorResponse(500, "Error changing storage");
        }
    }

    private String handleAiConfigGet() {
        try {
            String configText = AiConfigManager.readConfigJson(context);
            long updatedAt = System.currentTimeMillis();
            try {
                JSONObject parsed = new JSONObject(configText);
                updatedAt = parsed.optLong("updatedAt", updatedAt);
            } catch (JSONException ignored) {}

            JSONObject response = new JSONObject();
            response.put("status", "ok");
            response.put("content", configText);
            response.put("updatedAt", updatedAt);

            return createApiResponse(response.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error handling AI config GET", e);
            return createHttpErrorResponse(500, "AI config error: " + e.getMessage());
        }
    }

    private String handleAiConfigPost(String body) {
        try {
            if (body == null || body.trim().isEmpty()) {
                return createHttpErrorResponse(400, "Empty body");
            }

            JSONObject request = new JSONObject(body);
            String content;

            if (request.has("content")) {
                Object contentNode = request.get("content");
                if (contentNode instanceof JSONObject) {
                    content = ((JSONObject) contentNode).toString();
                } else {
                    content = request.getString("content");
                }
            } else {
                content = request.toString();
            }

            AiConfigManager.writeConfigJson(context, content);

            JSONObject response = new JSONObject();
            response.put("status", "ok");
            response.put("updatedAt", System.currentTimeMillis());

            return createApiResponse(response.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Invalid AI config payload", e);
            return createHttpErrorResponse(400, "JSON error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error saving AI config", e);
            return createHttpErrorResponse(500, "AI config save error: " + e.getMessage());
        }
    }
    
    // ========== UTILITAIRES HTTP ==========
    
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
    
    private void sendHttpResponse(SocketChannel clientChannel, String response) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(response.getBytes("UTF-8"));
            clientChannel.write(buffer);
            clientChannel.close();
        } catch (IOException e) {
            Log.e(TAG, "Erreur envoi réponse HTTP", e);
        }
    }
    
    private String createApiResponse(String jsonBody) {
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: application/json\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
               "Access-Control-Allow-Headers: Content-Type\r\n" +
               "Content-Length: " + jsonBody.length() + "\r\n" +
               "\r\n" +
               jsonBody;
    }
    
    private String createHttpErrorResponse(int code, String message) {
        String errorHtml = createErrorHtml(code, message);
        return "HTTP/1.1 " + code + " " + getStatusText(code) + "\r\n" +
               "Content-Type: text/html; charset=utf-8\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Content-Length: " + errorHtml.length() + "\r\n" +
               "\r\n" +
               errorHtml;
    }
    
    private String createCorsPreflightResponse() {
        return "HTTP/1.1 200 OK\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
               "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
               "Access-Control-Max-Age: 86400\r\n" +
               "Content-Length: 0\r\n" +
               "\r\n";
    }
    
    /**
     * Gère les fichiers statiques (interface web) avec compression et cache
     */
    private String handleStaticFile(String path) {
        try {
            // Lire le fichier depuis les assets
            java.io.InputStream inputStream = context.getAssets().open(path.substring(1)); // Enlever le premier /
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            
            byte[] fileContent = buffer.toByteArray();
            inputStream.close();
            
            // Déterminer le type MIME
            String mimeType = getMimeType(path);
            
            // Vérifier si c'est un fichier binaire
            boolean isBinary = isBinaryFile(path);
            
            // Créer la réponse HTTP avec headers optimisés
            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 200 OK\r\n");
            response.append("Content-Type: ").append(mimeType).append("\r\n");
            response.append("Access-Control-Allow-Origin: *\r\n");
            
            // Headers de cache pour les fichiers statiques
            if (path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png") || path.endsWith(".jpg")) {
                response.append("Cache-Control: public, max-age=3600\r\n");
                response.append("ETag: \"").append(System.currentTimeMillis()).append("\"\r\n");
            }
            
            // Gestion des fichiers binaires vs texte
            if (isBinary) {
                // Pour les fichiers binaires, envoyer les bytes directement
                response.append("Content-Length: ").append(fileContent.length).append("\r\n");
                response.append("\r\n");
                // Note: Dans un vrai serveur, on enverrait les bytes directement
                // Ici on convertit en base64 pour la compatibilité
                String base64Content = java.util.Base64.getEncoder().encodeToString(fileContent);
                response.append(base64Content);
            } else {
                // Pour les fichiers texte, envoyer le contenu directement
                String content = new String(fileContent, "UTF-8");
                response.append("Content-Length: ").append(content.length()).append("\r\n");
                response.append("\r\n");
                response.append(content);
            }
            
            return response.toString();
                   
        } catch (Exception e) {
            Log.e(TAG, "Erreur lecture fichier statique: " + path, e);
            return createHttpErrorResponse(404, "File not found");
        }
    }
    
    /**
     * Détermine le type MIME d'un fichier (version étendue)
     */
    private String getMimeType(String path) {
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        
        switch (extension) {
            // Web files
            case "html":
            case "htm":
                return "text/html; charset=utf-8";
            case "css":
                return "text/css; charset=utf-8";
            case "js":
                return "application/javascript; charset=utf-8";
            case "json":
                return "application/json; charset=utf-8";
            case "xml":
                return "application/xml; charset=utf-8";
            
            // Images
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "svg":
                return "image/svg+xml";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            
            // Audio
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "ogg":
                return "audio/ogg";
            case "m4a":
                return "audio/mp4";
            
            // Video
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "webm":
                return "video/webm";
            
            // Documents
            case "pdf":
                return "application/pdf";
            case "txt":
                return "text/plain; charset=utf-8";
            case "md":
                return "text/markdown; charset=utf-8";
            
            // Archives
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "7z":
                return "application/x-7z-compressed";
            
            // Office
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            
            default:
                return "application/octet-stream";
        }
    }
    
    /**
     * Détermine si un fichier est binaire ou texte
     */
    private boolean isBinaryFile(String path) {
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        
        // Fichiers binaires
        switch (extension) {
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "bmp":
            case "webp":
            case "svg":
                return true;
            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
                return true;
            case "mp3":
            case "wav":
            case "ogg":
            case "m4a":
                return true;
            case "mp4":
            case "avi":
            case "mov":
            case "webm":
                return true;
            case "pdf":
            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
                return true;
            case "exe":
            case "dll":
            case "so":
            case "dylib":
                return true;
            // Fichiers texte
            case "html":
            case "htm":
            case "css":
            case "js":
            case "json":
            case "xml":
            case "txt":
            case "md":
                return false;
            default:
                return false; // Par défaut, considérer comme texte
        }
    }
    
    /**
     * Formate la taille d'un fichier de manière lisible
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * Obtient l'icône d'un fichier selon son extension
     */
    private String getFileIcon(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        
        switch (extension) {
            case "html":
            case "htm":
                return "🌐";
            case "css":
                return "🎨";
            case "js":
                return "⚡";
            case "json":
                return "📋";
            case "xml":
                return "📄";
            case "txt":
            case "md":
                return "📝";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "svg":
            case "webp":
                return "🖼️";
            case "mp4":
            case "avi":
            case "mov":
            case "webm":
                return "🎬";
            case "mp3":
            case "wav":
            case "ogg":
            case "m4a":
                return "🎵";
            case "zip":
            case "rar":
            case "7z":
                return "📦";
            case "pdf":
                return "📕";
            case "doc":
            case "docx":
                return "📄";
            case "xls":
            case "xlsx":
                return "📊";
            case "ppt":
            case "pptx":
                return "📽️";
            default:
                return "📄";
        }
    }
    
    /**
     * Valide un path pour éviter les attaques directory traversal
     */
    private boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // Vérifier les patterns dangereux
        String[] dangerousPatterns = {
            "..", "//", "\\\\", "~", "$", "|", ";", "`", 
            "..\\", "../", "..\\\\", "..//"
        };
        
        for (String pattern : dangerousPatterns) {
            if (path.contains(pattern)) {
                return false;
            }
        }
        
        // Vérifier que le path commence par / et ne contient que des caractères autorisés
        if (!path.startsWith("/")) {
            return false;
        }
        
        // Autoriser les caractères alphanumériques, /, -, _, ., espaces, parenthèses, virgules et esperluettes
        if (!path.matches("^/[a-zA-Z0-9/._\\s(),&\\-]*$")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Obtient le texte de statut HTTP
     */
    private String getStatusText(int code) {
        switch (code) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 405:
                return "Method Not Allowed";
            case 500:
                return "Internal Server Error";
            case 503:
                return "Service Unavailable";
            default:
                return "Unknown";
        }
    }
    
    /**
     * Génère une page d'erreur HTML stylisée
     */
    private String createErrorHtml(int code, String message) {
        return String.format(
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <title>%d %s</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <h1>%d %s</h1>\n" +
            "    <p>%s</p>\n" +
            "</body>\n" +
            "</html>", code, getStatusText(code), code, getStatusText(code), message);
    }
    
    /**
     * Obtient le répertoire des sites utilisateur
     */
    private String getSitesDirectory() {
        // Chemin fixe : /storage/emulated/0/ChatAI-Files/sites
        return "/storage/emulated/0/ChatAI-Files/sites";
    }
    
    /**
     * Crée un directory listing standard comme Apache/Nginx
     */
    private String createUserSitesHTML(String title, java.io.File[] sites) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"fr\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>🌐 ").append(title).append("</title>\n");
        html.append("    <style>\n");
        html.append("        body {\n");
        html.append("            font-family: 'Courier New', monospace;\n");
        html.append("            background: #000;\n");
        html.append("            color: #ff3333;\n");
        html.append("            margin: 0;\n");
        html.append("            padding: 20px;\n");
        html.append("        }\n");
        html.append("        .header {\n");
        html.append("            background: linear-gradient(135deg, rgba(255, 51, 51, 0.1), rgba(255, 51, 51, 0.05));\n");
        html.append("            border: 2px solid #ff3333;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            padding: 15px;\n");
        html.append("            margin-bottom: 20px;\n");
        html.append("            text-align: center;\n");
        html.append("        }\n");
        html.append("        .header h1 {\n");
        html.append("            margin: 0;\n");
        html.append("            text-shadow: 0 0 10px #ff3333;\n");
        html.append("        }\n");
        html.append("        .sites-grid {\n");
        html.append("            display: grid;\n");
        html.append("            grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));\n");
        html.append("            gap: 20px;\n");
        html.append("        }\n");
        html.append("        .site-card {\n");
        html.append("            background: rgba(0, 0, 0, 0.8);\n");
        html.append("            border: 1px solid #ff3333;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            padding: 20px;\n");
        html.append("            transition: all 0.3s ease;\n");
        html.append("            cursor: pointer;\n");
        html.append("        }\n");
        html.append("        .site-card:hover {\n");
        html.append("            background: rgba(255, 51, 51, 0.1);\n");
        html.append("            transform: translateY(-5px);\n");
        html.append("            box-shadow: 0 10px 25px rgba(255, 51, 51, 0.3);\n");
        html.append("        }\n");
        html.append("        .site-icon {\n");
        html.append("            font-size: 48px;\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("        }\n");
        html.append("        .site-name {\n");
        html.append("            font-size: 1.5em;\n");
        html.append("            font-weight: bold;\n");
        html.append("            margin-bottom: 10px;\n");
        html.append("        }\n");
        html.append("        .site-info {\n");
        html.append("            color: #ff6666;\n");
        html.append("            font-size: 0.9em;\n");
        html.append("        }\n");
        html.append("        a {\n");
        html.append("            color: #ff3333;\n");
        html.append("            text-decoration: none;\n");
        html.append("        }\n");
        html.append("        a:hover {\n");
        html.append("            color: #ff6666;\n");
        html.append("            text-shadow: 0 0 5px #ff3333;\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>🌐 ").append(title).append("</h1>\n");
        html.append("        <p>Hébergement de sites web personnalisés</p>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"sites-grid\">\n");
        
        if (sites.length == 0) {
            html.append("        <div class=\"site-card\">\n");
            html.append("            <div class=\"site-icon\">📂</div>\n");
            html.append("            <div class=\"site-name\">Aucun site trouvé</div>\n");
            html.append("            <div class=\"site-info\">Créez votre premier site dans \"/storage/emulated/0/ChatAI-Files/sites/\"</div>\n");
            html.append("        </div>\n");
        } else {
            for (java.io.File site : sites) {
                String siteName = site.getName();
                String siteType = site.isDirectory() ? "📁 Dossier" : "📄 Fichier";
                String siteSize = site.isDirectory() ? "" : formatFileSize(site.length());
                String siteDate = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date(site.lastModified()));
                
                html.append("        <div class=\"site-card\" onclick=\"openSite('").append(siteName).append("')\">\n");
                html.append("            <div class=\"site-icon\">").append(site.isDirectory() ? "🌐" : "📄").append("</div>\n");
                html.append("            <div class=\"site-name\">").append(siteName).append("</div>\n");
                html.append("            <div class=\"site-info\">\n");
                html.append("                <div>").append(siteType).append("</div>\n");
                if (!siteSize.isEmpty()) {
                    html.append("                <div>Taille: ").append(siteSize).append("</div>\n");
                }
                html.append("                <div>Modifié: ").append(siteDate).append("</div>\n");
                html.append("            </div>\n");
                html.append("        </div>\n");
            }
        }
        
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        function openSite(siteName) {\n");
        html.append("            const isFile = siteName.includes('.') && !siteName.endsWith('/');\n");
        html.append("            const target = isFile ? ('/' + siteName) : ('/' + (siteName.endsWith('/') ? siteName : siteName + '/'));\n");
        html.append("            window.location.href = target;\n");
        html.append("        }\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: text/html; charset=utf-8\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Cache-Control: public, max-age=300\r\n" +
               "Content-Length: " + html.length() + "\r\n" +
               "\r\n" +
               html.toString();
    }
    
    /**
     * Crée un directory listing standard comme Apache/Nginx
     */
    private String createStandardDirectoryListing(String title, java.io.File[] entries) {
        return createStandardDirectoryListing(title, entries, "");
    }

    private String createStandardDirectoryListing(String title, java.io.File[] entries, String basePath) {
        StringBuilder html = new StringBuilder();

        String baseTemp = "";
        if (basePath != null && !basePath.isEmpty()) {
            baseTemp = basePath.startsWith("/") ? basePath : "/" + basePath;
            if (baseTemp.endsWith("/") && baseTemp.length() > 1) {
                baseTemp = baseTemp.substring(0, baseTemp.length() - 1);
            }
        }
        final String normalizedBase = baseTemp;

        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"utf-8\">\n");
        html.append("    <title>").append(title).append("</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("        h1 { color: #333; }\n");
        html.append("        table { border-collapse: collapse; width: 100%; }\n");
        html.append("        th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }\n");
        html.append("        th { background-color: #f2f2f2; }\n");
        html.append("        a { text-decoration: none; color: #0066cc; }\n");
        html.append("        a:hover { text-decoration: underline; }\n");
        html.append("        .dir { font-weight: bold; }\n");
        html.append("        .file { }\n");
        html.append("        .html { font-weight: bold; color: #cc6600; }\n");
        html.append("        .html:hover { color: #ff8800; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<h1>").append(title).append("</h1>\n");
        html.append("<table>\n");
        html.append("<tr><th>Name</th><th>Last modified</th><th>Size</th><th>Description</th></tr>\n");

        if (entries != null && entries.length > 0) {
            java.util.List<java.io.File> htmlFiles = new java.util.ArrayList<>();
            java.util.List<java.io.File> directories = new java.util.ArrayList<>();
            java.util.List<java.io.File> otherFiles = new java.util.ArrayList<>();

            for (java.io.File entry : entries) {
                if (entry.isDirectory()) {
                    directories.add(entry);
                } else if (entry.getName().endsWith(".html")) {
                    htmlFiles.add(entry);
                } else {
                    otherFiles.add(entry);
                }
            }

            if (!htmlFiles.isEmpty()) {
                html.append("<tr><td colspan=\"4\" style=\"background-color: #f0f8ff; font-weight: bold; padding: 10px; border-top: 2px solid #0066cc;\">📄 Pages HTML</td></tr>\n");
            }

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");

            java.util.function.BiConsumer<java.io.File, String> appendRow = (file, cssClass) -> {
                String name = file.getName();
                String link = file.isDirectory() ? name + "/" : name;
                String size = file.isDirectory() ? "-" : formatFileSize(file.length());
                String mtime = sdf.format(new java.util.Date(file.lastModified()));
                String encodedLink;
                try {
                    if (file.isDirectory() && link.endsWith("/")) {
                        String nameToEncode = link.substring(0, link.length() - 1);
                        encodedLink = java.net.URLEncoder.encode(nameToEncode, "UTF-8") + "/";
                    } else {
                        encodedLink = java.net.URLEncoder.encode(link, "UTF-8");
                    }
                } catch (java.io.UnsupportedEncodingException e) {
                    encodedLink = link;
                }

                String href;
                if (normalizedBase.isEmpty() || "/".equals(normalizedBase)) {
                    href = "/" + encodedLink;
                } else {
                    href = normalizedBase + "/" + encodedLink;
                }

                html.append("<tr><td><a href=\"").append(href).append("\" class=\"").append(cssClass).append("\">").append(name).append("</a></td><td>").append(mtime).append("</td><td>").append(size).append("</td><td></td></tr>\n");
            };

            for (java.io.File file : htmlFiles) {
                appendRow.accept(file, "html");
            }

            if (!directories.isEmpty()) {
                html.append("<tr><td colspan=\"4\" style=\"background-color: #e8f5e8; font-weight: bold; padding: 10px; border-top: 2px solid #28a745;\">📁 Répertoires</td></tr>\n");
            }
            for (java.io.File file : directories) {
                appendRow.accept(file, "dir");
            }

            if (!otherFiles.isEmpty()) {
                html.append("<tr><td colspan=\"4\" style=\"background-color: #f8f8f8; font-weight: bold; padding: 10px; border-top: 2px solid #666;\">📄 Fichiers</td></tr>\n");
            }
            for (java.io.File file : otherFiles) {
                appendRow.accept(file, "file");
            }
        } else {
            html.append("<tr><td colspan=\"4\">Aucun fichier trouvé</td></tr>\n");
        }

        html.append("</table>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: text/html; charset=utf-8\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Cache-Control: public, max-age=300\r\n" +
               "Content-Length: " + html.length() + "\r\n" +
               "\r\n" +
               html.toString();
    }
    
    /**
     * Gère l'affichage du directory listing
     */
    private String handleDirectoryListing() {
        return handleDirectoryListing("");
    }
    
    /**
     * Gère l'affichage des sites utilisateur
     */
    private String handleUserSitesListing() {
        try {
            // Obtenir le répertoire des sites utilisateur
            String sitesPath = getSitesDirectory();
            java.io.File sitesDir = new java.io.File(sitesPath);
            
            if (!sitesDir.exists()) {
                sitesDir.mkdirs();
            }
            
            java.io.File[] sites = sitesDir.listFiles();
            if (sites == null || sites.length == 0) {
                return createUserSitesHTML("Aucun site utilisateur trouvé", new java.io.File[0]);
            }
            
            return createStandardDirectoryListing("Sites Utilisateur", sites);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur listing sites utilisateur", e);
            return createHttpErrorResponse(500, "Error listing user sites");
        }
    }
    
    /**
     * Gère l'accès à un site utilisateur spécifique - Version simple
     */
    private String handleUserSite(String sitePath) {
        try {
            // Résoudre les chemins relatifs avec ../
            String resolvedPath = resolveRelativePath(sitePath);
            logVerbose("handleUserSite - original: " + sitePath + ", resolved: " + resolvedPath);
            
            String sitesDir = getSitesDirectory();
            java.io.File siteFile = new java.io.File(sitesDir, resolvedPath);
            
            logVerbose("handleUserSite - sitesDir: " + sitesDir);
            logVerbose("handleUserSite - sitePath: " + sitePath);
            logVerbose("handleUserSite - resolvedPath: " + resolvedPath);
            logVerbose("handleUserSite - siteFile: " + siteFile.getAbsolutePath());
            logVerbose("handleUserSite - exists: " + siteFile.exists());
            
            if (!siteFile.exists()) {
                Log.w(TAG, "Fichier non trouvé: " + siteFile.getAbsolutePath());
                return "HTTP/1.1 404 Not Found\r\n\r\n";
            }
            
            if (siteFile.isDirectory()) {
                // Si c'est un dossier, chercher index.html
                java.io.File indexFile = new java.io.File(siteFile, "index.html");
                if (indexFile.exists()) {
                    return serveFileSimple(indexFile);
                } else {
                    // Lister le contenu du dossier
                    return listUserSiteDirectory(siteFile, sitePath);
                }
                } else {
                    // Servir le fichier directement
                    if (isBinaryFile(siteFile.getName())) {
                        // Pour les fichiers binaires, utiliser la nouvelle méthode
                        return serveStaticFileDirect(siteFile);
                    } else {
                        // Pour les fichiers texte, utiliser serveFileSimple
                        return serveFileSimple(siteFile);
                    }
                }
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur accès site utilisateur: " + sitePath, e);
            return "HTTP/1.1 500 Internal Server Error\r\n\r\n";
        }
    }

    private boolean isUserSiteRequest(String cleanPath) {
        if (cleanPath == null || cleanPath.length() <= 1) {
            return false;
        }

        // Exclure les préfixes réservés
        if (cleanPath.startsWith("/api/")
                || cleanPath.startsWith("/files")
                || cleanPath.startsWith("/browse")
                || cleanPath.startsWith("/sites/")
                || cleanPath.startsWith("/user-sites/")
                || cleanPath.startsWith("/gamedata/")
                || cleanPath.startsWith("/static/")
                || cleanPath.startsWith("/assets/")
                || cleanPath.startsWith("/ws")
                || cleanPath.startsWith("/websocket")) {
            return false;
        }

        if (cleanPath.equals("/dashboard") || cleanPath.equals("/dashboard/")
                || cleanPath.equals("/system.html") || cleanPath.equals("/chat.js")) {
            return false;
        }

        String sitePath = cleanPath.substring(1); // remove leading slash
        if (sitePath.isEmpty()) {
            return false;
        }

        java.io.File candidate = new java.io.File(getSitesDirectory(), sitePath);
        if (candidate.exists()) {
            return true;
        }

        // Essayer avec index.html pour les dossiers
        if (!sitePath.endsWith("index.html")) {
            String basePath = sitePath.endsWith("/") ? sitePath : sitePath + "/";
            java.io.File indexCandidate = new java.io.File(getSitesDirectory(), basePath + "index.html");
            if (indexCandidate.exists()) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Crée un site utilisateur avec les bonnes permissions
     */
    private String createUserSite(String siteName, String content) {
        try {
            java.io.File sitesDir = new java.io.File(getSitesDirectory());
            if (!sitesDir.exists()) {
                sitesDir.mkdirs();
            }
            
            java.io.File siteDir = new java.io.File(sitesDir, siteName);
            if (!siteDir.exists()) {
                siteDir.mkdirs();
            }
            
            java.io.File indexFile = new java.io.File(siteDir, "index.html");
            java.io.FileWriter writer = new java.io.FileWriter(indexFile);
            writer.write(content);
            writer.close();
            
            // Définir les permissions
            indexFile.setReadable(true, false);
            indexFile.setWritable(true, false);
            siteDir.setReadable(true, false);
            siteDir.setWritable(true, false);
            
            // Forcer les permissions via chmod si possible
            try {
                Runtime.getRuntime().exec("chmod 644 " + indexFile.getAbsolutePath());
                Runtime.getRuntime().exec("chmod 755 " + siteDir.getAbsolutePath());
            } catch (Exception e) {
                Log.w(TAG, "Impossible de définir les permissions via chmod", e);
            }
            
            Log.i(TAG, "Site créé: " + indexFile.getAbsolutePath());
            return "Site créé avec succès: " + siteName;
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur création site", e);
            return "Erreur création site: " + e.getMessage();
        }
    }
    
    /**
     * Gère l'upload de fichiers
     */
    private String handleFileUpload() {
        try {
            // Pour l'instant, retourner une page d'upload simple
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n");
            html.append("<head>\n");
            html.append("    <meta charset=\"UTF-8\">\n");
            html.append("    <title>Upload de Site Web</title>\n");
            html.append("    <style>\n");
            html.append("        body { font-family: Arial, sans-serif; margin: 20px; background: #f0f0f0; }\n");
            html.append("        .container { max-width: 600px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n");
            html.append("        h1 { color: #333; text-align: center; }\n");
            html.append("        .upload-area { border: 2px dashed #ccc; padding: 40px; text-align: center; margin: 20px 0; border-radius: 8px; }\n");
            html.append("        .upload-area:hover { border-color: #999; background: #f9f9f9; }\n");
            html.append("        input[type=file] { margin: 10px 0; }\n");
            html.append("        button { background: #007bff; color: white; padding: 10px 20px; border: none; border-radius: 4px; cursor: pointer; }\n");
            html.append("        button:hover { background: #0056b3; }\n");
            html.append("        .instructions { background: #e7f3ff; padding: 15px; border-radius: 4px; margin: 20px 0; }\n");
            html.append("    </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("    <div class=\"container\">\n");
            html.append("        <h1>🚀 Upload de Site Web</h1>\n");
            html.append("        <div class=\"instructions\">\n");
            html.append("            <h3>Instructions :</h3>\n");
            html.append("            <ol>\n");
            html.append("                <li>Créez un dossier avec votre site web</li>\n");
            html.append("                <li>Compressez-le en fichier ZIP</li>\n");
            html.append("                <li>Uploadez le fichier ZIP ci-dessous</li>\n");
            html.append("                <li>Votre site sera automatiquement décompressé</li>\n");
            html.append("            </ol>\n");
            html.append("        </div>\n");
            html.append("        <div class=\"upload-area\">\n");
            html.append("            <h3>📁 Sélectionnez votre fichier ZIP</h3>\n");
            html.append("            <form action=\"/api/upload-process\" method=\"post\" enctype=\"multipart/form-data\">\n");
            html.append("                <input type=\"file\" name=\"sitefile\" accept=\".zip\" required>\n");
            html.append("                <br><br>\n");
            html.append("                <button type=\"submit\">🚀 Uploader le Site</button>\n");
            html.append("            </form>\n");
            html.append("        </div>\n");
            html.append("        <p><strong>Note :</strong> Pour l'instant, utilisez l'API <code>/api/create-site/nom</code> pour créer des sites simples.</p>\n");
            html.append("    </div>\n");
            html.append("</body>\n");
            html.append("</html>\n");
            
            return "HTTP/1.1 200 OK\r\n" +
                   "Content-Type: text/html; charset=utf-8\r\n" +
                   "Access-Control-Allow-Origin: *\r\n" +
                   "Content-Length: " + html.length() + "\r\n" +
                   "\r\n" +
                   html.toString();
                   
        } catch (Exception e) {
            Log.e(TAG, "Erreur upload page", e);
            return createHttpErrorResponse(500, "Error creating upload page");
        }
    }
    
    /**
     * Crée un message d'aide pour les fichiers binaires
     */
    private String createBinaryFileMessage(String fileName) {
        StringBuilder message = new StringBuilder();
        message.append("\n\n");
        message.append("<!-- ========================================== -->\n");
        message.append("<!-- FICHIER BINAIRE NON SUPPORTÉ PAR HTTPSERVER -->\n");
        message.append("<!-- ========================================== -->\n");
        message.append("<div style=\"background: #fff3cd; border: 1px solid #ffeaa7; padding: 15px; margin: 20px; border-radius: 5px;\">\n");
        message.append("    <h3 style=\"color: #856404; margin-top: 0;\">⚠️ Fichier binaire détecté</h3>\n");
        message.append("    <p><strong>Fichier :</strong> ").append(fileName).append("</p>\n");
        message.append("    <p><strong>Problème :</strong> HttpServer (port 8080) ne peut pas servir les fichiers binaires (images, vidéos, etc.)</p>\n");
        message.append("    <p><strong>Solution :</strong> Utilisez WebServer (port 8888) pour les fichiers binaires</p>\n");
        message.append("    <div style=\"background: #d1ecf1; padding: 10px; border-radius: 3px; margin: 10px 0;\">\n");
        message.append("        <p><strong>🔗 URL alternative :</strong></p>\n");
        message.append("        <code style=\"background: #f8f9fa; padding: 5px; border-radius: 3px;\">http://10.19.95.217:8888/web2/").append(fileName).append("</code>\n");
        message.append("    </div>\n");
        message.append("    <div style=\"background: #d4edda; padding: 10px; border-radius: 3px; margin: 10px 0;\">\n");
        message.append("        <p><strong>📋 Rôles des serveurs :</strong></p>\n");
        message.append("        <ul style=\"margin: 5px 0;\">\n");
        message.append("            <li><strong>HttpServer (8080) :</strong> Pages HTML, API REST, interface KITT</li>\n");
        message.append("            <li><strong>WebServer (8888) :</strong> Fichiers statiques, images, CSS, JS</li>\n");
        message.append("        </ul>\n");
        message.append("    </div>\n");
        message.append("</div>\n");
        message.append("<!-- ========================================== -->\n");
        
        return message.toString();
    }
    
    /**
     * Crée une redirection vers WebServer pour les sites
     */
    private String createSitesRootIndex() {
        try {
            String sitesPath = getSitesDirectory();
            java.io.File sitesDir = new java.io.File(sitesPath);
            if (!sitesDir.exists()) {
                sitesDir.mkdirs();
            }
            
            java.io.File[] entries = sitesDir.listFiles();
            if (entries == null) {
                entries = new java.io.File[0];
            }
            
            java.util.Arrays.sort(entries, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>");
            html.append("<html><head><meta charset=\"utf-8\"><title>Index of /</title>");
            html.append("<style>");
            html.append("body{font-family:Segoe UI,Roboto,Arial,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:24px;}");
            html.append("a{color:#38bdf8;text-decoration:none;}a:hover{text-decoration:underline;}");
            html.append(".container{max-width:960px;margin:0 auto;}");
            html.append("table{width:100%;border-collapse:collapse;margin-top:16px;background:#111c3a;border-radius:12px;overflow:hidden;}");
            html.append("th,td{padding:12px 16px;text-align:left;border-bottom:1px solid rgba(148, 163, 184, 0.1);}");
            html.append("th{background:rgba(148,163,184,0.15);text-transform:uppercase;letter-spacing:0.05em;font-size:12px;color:#94a3b8;}");
            html.append("tr:hover td{background:rgba(56,189,248,0.08);}");
            html.append(".dash-link{margin-top:12px;font-size:13px;color:#94a3b8;}");
            html.append(".dash-link a{color:#cbd5f5;}");
            html.append("</style></head><body>");
            html.append("<div class=\"container\">");
            html.append("<h2>Index of /</h2>");
            html.append("<div class=\"dash-link\">→ <a href=\"/webapp/index.html\">ChatAI Dashboard</a></div>");
            html.append("<table><thead><tr><th>Name</th><th>Last modified</th><th>Size</th></tr></thead><tbody>");
            
            java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            
            for (java.io.File entry : entries) {
                String name = entry.getName();
                String displayName = entry.isDirectory() ? name + "/" : name;
                String hrefName = "/" + (entry.isDirectory() ? name + "/" : name);
                String lastModified = dateFormat.format(new java.util.Date(entry.lastModified()));
                String size = entry.isDirectory() ? "-" : formatFileSize(entry.length());
                
                html.append("<tr><td><a href=\"").append(hrefName).append("\">").append(displayName)
                    .append("</a></td><td>").append(lastModified).append("</td><td>").append(size).append("</td></tr>");
            }
            
            if (entries.length == 0) {
                html.append("<tr><td colspan=\"3\">No entries</td></tr>");
            }
            
            html.append("</tbody></table></div></body></html>");
            
            byte[] bytes = html.toString().getBytes(StandardCharsets.UTF_8);
            return "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "\r\n" +
                    html.toString();
        } catch (Exception e) {
            Log.e(TAG, "Erreur génération index sites", e);
            return createHttpErrorResponse(500, "Error generating sites index");
        }
    }
    
    /**
     * Obtient l'IP dynamique du device
     */
    private String getDeviceIP() {
        try {
            // Vérifier d'abord si le WiFi est connecté
            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) context.getSystemService(android.content.Context.WIFI_SERVICE);
            android.net.wifi.WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            
            // Vérifier si le WiFi est connecté et a une IP valide
            if (wifiInfo != null && wifiInfo.getIpAddress() != 0) {
                int ipAddress = wifiInfo.getIpAddress();
                String ip = String.format("%d.%d.%d.%d",
                    (ipAddress & 0xff),
                    (ipAddress >> 8 & 0xff),
                    (ipAddress >> 16 & 0xff),
                    (ipAddress >> 24 & 0xff)
                );
                if (isValidIP(ip)) {
                    Log.d(TAG, "IP WiFi détectée: " + ip);
                    return ip;
                }
            }
            
            // Si pas de WiFi, vérifier les autres interfaces réseau
            java.util.List<java.net.NetworkInterface> interfaces = java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces());
            for (java.net.NetworkInterface networkInterface : interfaces) {
                if (networkInterface.isUp() && !networkInterface.isLoopback()) {
                    java.util.List<java.net.InetAddress> addresses = java.util.Collections.list(networkInterface.getInetAddresses());
                    for (java.net.InetAddress address : addresses) {
                        if (!address.isLoopbackAddress() && address.isSiteLocalAddress()) {
                            String ip = address.getHostAddress();
                            if (ip != null && isValidIP(ip)) {
                    logVerbose("IP détectée via " + networkInterface.getName() + ": " + ip);
                                return ip;
                            }
                        }
                    }
                }
            }
            
            // Si aucune IP n'est trouvée, retourner une indication d'erreur
            Log.w(TAG, "Aucune connexion réseau détectée");
            return "NO_NETWORK"; // Indicateur spécial pour pas de réseau
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la détection d'IP", e);
            return "NO_NETWORK"; // Indicateur spécial pour erreur
        }
    }
    
    /**
     * Vérifie si une IP est valide
     */
    private boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Corrige les permissions d'un fichier
     */
    private void fixFilePermissions(java.io.File file) {
        try {
            // Définir les permissions Java
            file.setReadable(true, false);
            file.setWritable(true, false);
            
            // Essayer chmod via Runtime
            Runtime.getRuntime().exec("chmod 644 " + file.getAbsolutePath());
            
            // Essayer chmod via ProcessBuilder (plus robuste)
            ProcessBuilder pb = new ProcessBuilder("chmod", "644", file.getAbsolutePath());
            pb.start();
            
        } catch (Exception e) {
            Log.w(TAG, "Impossible de corriger les permissions pour: " + file.getAbsolutePath(), e);
        }
    }
    
    /**
     * Résout les chemins relatifs avec ../ et ./
     */
    private String resolveRelativePath(String path) {
        if (path == null || !path.contains("../")) {
            return path;
        }
        
        // Séparer le chemin en segments
        String[] segments = path.split("/");
        java.util.List<String> resolved = new java.util.ArrayList<>();
        
        for (String segment : segments) {
            if (segment.equals("..")) {
                // Remonter d'un niveau
                if (!resolved.isEmpty()) {
                    resolved.remove(resolved.size() - 1);
                }
            } else if (!segment.equals(".") && !segment.isEmpty()) {
                // Ajouter le segment normal
                resolved.add(segment);
            }
        }
        
        // Reconstruire le chemin
        StringBuilder result = new StringBuilder();
        for (String segment : resolved) {
            result.append("/").append(segment);
        }
        
        return result.toString();
    }
    
    /**
     * Sert un fichier statique avec gestion directe des bytes (architecture WebServer)
     */
    private String serveStaticFileDirect(java.io.File file) {
        try {
            // Lire le fichier en bytes (comme WebServer)
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            
            // Déterminer le type MIME
            String mimeType = getMimeType(file.getName());
            
            // Headers HTTP
            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 200 OK\r\n");
            response.append("Content-Type: ").append(mimeType).append("\r\n");
            response.append("Content-Length: ").append(fileBytes.length).append("\r\n");
            response.append("Server: ChatAI-HttpServer/1.0 (Android)\r\n");
            response.append("Access-Control-Allow-Origin: *\r\n");
            response.append("Cache-Control: public, max-age=3600\r\n");
            response.append("\r\n");
            
            // Note: Cette méthode retourne seulement les headers
            // Le contenu binaire sera géré par le serveur HTTP directement
            logVerbose("Serving static file: " + file.getName() + " (" + fileBytes.length + " bytes)");
            
            // Pour les fichiers binaires, on ne peut pas les ajouter à une String
            // Cette méthode retourne seulement les headers, le contenu binaire
            // sera géré par le serveur HTTP directement
            return response.toString() + createBinaryFileMessage(file.getName());
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lecture fichier statique: " + file.getAbsolutePath(), e);
            return "HTTP/1.1 500 Internal Server Error\r\n\r\n";
        }
    }
    
    /**
     * Sert un fichier binaire utilisateur
     */
    private String serveBinaryFile(java.io.File file) {
        try {
            // Lire le fichier binaire
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            
            // Headers HTTP pour fichier binaire
            String mimeType = getMimeType(file.getName());
            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 200 OK\r\n");
            response.append("Content-Type: ").append(mimeType).append("\r\n");
            response.append("Content-Length: ").append(fileBytes.length).append("\r\n");
            response.append("Access-Control-Allow-Origin: *\r\n");
            response.append("Cache-Control: public, max-age=3600\r\n");
            response.append("\r\n");
            
            // Pour les fichiers binaires, on ne peut pas les ajouter à une String
            // Cette méthode retourne seulement les headers, le contenu binaire
            // sera géré par le serveur HTTP directement
            logVerbose("Serving binary file: " + file.getName() + " (" + fileBytes.length + " bytes)");
            
            // Note: Cette méthode ne peut pas retourner le contenu binaire dans une String
            // Il faudrait modifier l'architecture pour gérer les bytes directement
            return response.toString() + createBinaryFileMessage(file.getName());
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lecture fichier binaire: " + file.getAbsolutePath(), e);
            return "HTTP/1.1 500 Internal Server Error\r\n\r\n";
        }
    }
    
    /**
     * Sert un fichier utilisateur - Version ultra-simple
     */
    private String serveFileSimple(java.io.File file) {
        try {
            // Lire le fichier brut
            byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
            
            // Headers HTTP minimaux
            String mimeType = getMimeType(file.getName());
            StringBuilder response = new StringBuilder();
            response.append("HTTP/1.1 200 OK\r\n");
            response.append("Content-Type: ").append(mimeType).append("\r\n");
            response.append("Content-Length: ").append(fileBytes.length).append("\r\n");
            response.append("\r\n");
            
            // Pour les fichiers binaires, on ne peut pas les ajouter à une String
            // Mais on va essayer quand même (même si c'est corrompu)
            if (isBinaryFile(file.getName())) {
                Log.w(TAG, "Serving binary file with potential corruption: " + file.getName());
                // Essayer de servir le contenu binaire même si c'est corrompu
                try {
                    response.append(new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception e) {
                    Log.e(TAG, "Erreur conversion binaire: " + e.getMessage());
                }
                return response.toString();
            }
            
            // Ajouter le contenu brut (seulement pour les fichiers texte)
            response.append(new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8));
            
            return response.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur lecture fichier: " + file.getAbsolutePath(), e);
            return "HTTP/1.1 500 Internal Server Error\r\n\r\n";
        }
    }
    
    /**
     * Liste le contenu d'un dossier de site utilisateur
     */
    private String listUserSiteDirectory(java.io.File directory, String sitePath) {
        try {
            java.io.File[] files = directory.listFiles();
            if (files == null || files.length == 0) {
                return createStandardDirectoryListing("Dossier vide: " + sitePath, new java.io.File[0], sitePath);
            }
            
            return createStandardDirectoryListing("Contenu: " + sitePath, files, sitePath);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur listing dossier site: " + sitePath, e);
            return createHttpErrorResponse(500, "Error listing site directory");
        }
    }
    
    private String handleDirectoryListing(String subPath) {
        try {
            // Utiliser le FileServer pour obtenir la liste des fichiers
            if (fileServer != null) {
                // Utiliser la nouvelle méthode qui retourne seulement le JSON
                String filesJson = fileServer.getFilesJson();
                
                return createSimpleDirectoryListing(filesJson, subPath);
            } else {
                return createHttpErrorResponse(503, "File server not available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur directory listing", e);
            return createHttpErrorResponse(500, "Error generating directory listing");
        }
    }
    
    /**
     * Extrait le JSON de la réponse HTTP
     */
    private String extractJsonFromResponse(String httpResponse) {
        try {
            // Chercher la ligne vide qui sépare les headers du body
            int emptyLineIndex = httpResponse.indexOf("\r\n\r\n");
            if (emptyLineIndex != -1) {
                // Extraire le JSON après les headers
                return httpResponse.substring(emptyLineIndex + 4);
            }
            
            // Fallback: chercher le début du JSON
            int jsonStart = httpResponse.indexOf("{");
            if (jsonStart != -1) {
                return httpResponse.substring(jsonStart);
            }
            
            // Si rien trouvé, retourner la réponse complète
            return httpResponse;
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur extraction JSON", e);
            return httpResponse;
        }
    }
    
    /**
     * Crée un listing de répertoire simple - utilise directement le FileServer
     */
    private String createSimpleDirectoryListing(String filesJson, String subPath) {
        try {
            // Obtenir les fichiers du répertoire actuel
            String currentPath = getSitesDirectory();
            if (!subPath.isEmpty()) {
                currentPath += "/" + subPath;
            }
            
            java.io.File currentDir = new java.io.File(currentPath);
            if (!currentDir.exists() || !currentDir.isDirectory()) {
                return createHttpErrorResponse(404, "Directory not found");
            }
            
            java.io.File[] files = currentDir.listFiles();
            if (files == null) {
                files = new java.io.File[0];
            }
            
            // Utiliser le nouveau format standard
                String base = (subPath == null || subPath.isEmpty()) ? "files" : "files/" + subPath;
                return createStandardDirectoryListing("Directory: " + (subPath.isEmpty() ? "/" : subPath), files, base);
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur simple directory listing", e);
            return createHttpErrorResponse(500, "Error generating directory listing");
        }
    }
    

    /**
     * Crée une page HTML simple pour l'affichage des fichiers
     */
    private String createDirectoryListingHTML(String filesJson, String subPath) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <title>Directory Listing</title>\n");
        html.append("    <style>\n");
        html.append("        body {\n");
        html.append("            font-family: 'Courier New', monospace;\n");
        html.append("            background: #000;\n");
        html.append("            color: #ff3333;\n");
        html.append("            margin: 0;\n");
        html.append("            padding: 20px;\n");
        html.append("        }\n");
        html.append("        .header {\n");
        html.append("            background: linear-gradient(135deg, rgba(255, 51, 51, 0.1), rgba(255, 51, 51, 0.05));\n");
        html.append("            border: 2px solid #ff3333;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            padding: 15px;\n");
        html.append("            margin-bottom: 20px;\n");
        html.append("            text-align: center;\n");
        html.append("        }\n");
        html.append("        .header h1 {\n");
        html.append("            margin: 0;\n");
        html.append("            text-shadow: 0 0 10px #ff3333;\n");
        html.append("        }\n");
        html.append("        .file-list {\n");
        html.append("            background: rgba(0, 0, 0, 0.8);\n");
        html.append("            border: 1px solid #ff3333;\n");
        html.append("            border-radius: 8px;\n");
        html.append("            padding: 10px;\n");
        html.append("        }\n");
        html.append("        .file-item {\n");
        html.append("            display: flex;\n");
        html.append("            align-items: center;\n");
        html.append("            padding: 8px;\n");
        html.append("            margin: 2px 0;\n");
        html.append("            border-radius: 4px;\n");
        html.append("            transition: all 0.3s ease;\n");
        html.append("            cursor: pointer;\n");
        html.append("        }\n");
        html.append("        .file-item:hover {\n");
        html.append("            background: rgba(255, 51, 51, 0.1);\n");
        html.append("            transform: translateX(5px);\n");
        html.append("        }\n");
        html.append("        .file-icon {\n");
        html.append("            margin-right: 10px;\n");
        html.append("            font-size: 18px;\n");
        html.append("        }\n");
        html.append("        .file-name {\n");
        html.append("            flex: 1;\n");
        html.append("            font-weight: bold;\n");
        html.append("        }\n");
        html.append("        .file-size {\n");
        html.append("            margin-right: 10px;\n");
        html.append("            color: #ff6666;\n");
        html.append("            font-size: 12px;\n");
        html.append("        }\n");
        html.append("        .file-date {\n");
        html.append("            color: #ff6666;\n");
        html.append("            font-size: 12px;\n");
        html.append("        }\n");
        html.append("        a {\n");
        html.append("            color: #ff3333;\n");
        html.append("            text-decoration: none;\n");
        html.append("        }\n");
        html.append("        a:hover {\n");
        html.append("            color: #ff6666;\n");
        html.append("            text-shadow: 0 0 5px #ff3333;\n");
        html.append("        }\n");
        html.append("        .breadcrumb {\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("            padding: 5px;\n");
        html.append("            background: rgba(255, 51, 51, 0.05);\n");
        html.append("            border-radius: 4px;\n");
        html.append("        }\n");
        html.append("        .stats {\n");
        html.append("            background: rgba(255, 51, 51, 0.05);\n");
        html.append("            padding: 10px;\n");
        html.append("            border-radius: 4px;\n");
        html.append("            margin-bottom: 15px;\n");
        html.append("            font-size: 12px;\n");
        html.append("            color: #ff6666;\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>🚗 KITT File Explorer</h1>\n");
        html.append("        <p>Directory: ").append(subPath.isEmpty() ? "/" : subPath).append("</p>\n");
        html.append("    </div>\n");
        
        // Breadcrumb navigation
        if (!subPath.isEmpty()) {
            html.append("    <div class=\"breadcrumb\">\n");
            html.append("        <a href=\"/files\">📁 .. (Parent Directory)</a>\n");
            html.append("    </div>\n");
        }
        
        // JavaScript pour parser et afficher les fichiers
        html.append("    <div id=\"fileList\">\n");
        html.append("        <div class=\"file-item\">\n");
        html.append("            <span class=\"file-icon\">⏳</span>\n");
        html.append("            <span class=\"file-name\">Chargement des fichiers...</span>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <script>\n");
        html.append("        const filesData = ").append(filesJson).append(";\n");
        html.append("        const subPath = '").append(subPath).append("';\n");
        html.append("        \n");
        html.append("        function formatFileSize(bytes) {\n");
        html.append("            if (bytes === 0) return '0 B';\n");
        html.append("            const k = 1024;\n");
        html.append("            const sizes = ['B', 'KB', 'MB', 'GB'];\n");
        html.append("            const i = Math.floor(Math.log(bytes) / Math.log(k));\n");
        html.append("            return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function formatDate(timestamp) {\n");
        html.append("            return new Date(timestamp).toLocaleDateString('fr-FR');\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function getFileIcon(fileName, isDirectory) {\n");
        html.append("            if (isDirectory) return '📁';\n");
        html.append("            const ext = fileName.split('.').pop().toLowerCase();\n");
        html.append("            const icons = {\n");
        html.append("                'html': '🌐', 'htm': '🌐', 'css': '🎨', 'js': '⚡', 'json': '📋',\n");
        html.append("                'xml': '📄', 'txt': '📝', 'md': '📝', 'jpg': '🖼️', 'jpeg': '🖼️',\n");
        html.append("                'png': '🖼️', 'gif': '🖼️', 'svg': '🖼️', 'mp4': '🎬', 'avi': '🎬',\n");
        html.append("                'mkv': '🎬', 'mp3': '🎵', 'wav': '🎵', 'ogg': '🎵', 'zip': '📦',\n");
        html.append("                'rar': '📦', '7z': '📦', 'pdf': '📕', 'doc': '📄', 'docx': '📄'\n");
        html.append("            };\n");
        html.append("            return icons[ext] || '📄';\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function displayFiles() {\n");
        html.append("            const fileList = document.getElementById('fileList');\n");
        html.append("            \n");
        html.append("            if (!filesData.files || filesData.files.length === 0) {\n");
        html.append("                fileList.innerHTML = '<div class=\"file-item\"><span class=\"file-icon\">📂</span><span class=\"file-name\">Directory is empty</span></div>';\n");
        html.append("                return;\n");
        html.append("            }\n");
        html.append("            \n");
        html.append("            let html = '<div class=\"stats\">📊 ' + filesData.files.length + ' éléments dans ' + filesData.storagePath + '</div>';\n");
        html.append("            \n");
        html.append("            filesData.files.forEach(file => {\n");
        html.append("                const icon = getFileIcon(file.name, file.isDirectory);\n");
        html.append("                const size = file.isDirectory ? '' : formatFileSize(file.size);\n");
        html.append("                const date = formatDate(file.lastModified);\n");
        html.append("                const relativePath = subPath.endsWith('/') ? subPath + file.name : subPath + '/' + file.name;\n");
        html.append("                \n");
        html.append("                html += '<div class=\"file-item\" onclick=\"handleFileClick(\\'' + file.name + '\\', ' + file.isDirectory + ')\">';\n");
        html.append("                html += '<span class=\"file-icon\">' + icon + '</span>';\n");
        html.append("                html += '<a href=\"' + (file.isDirectory ? '/files/' + file.name : '/api/files/download/' + file.name) + '\" class=\"file-name\">' + file.name + '</a>';\n");
        html.append("                if (size) html += '<span class=\"file-size\">' + size + '</span>';\n");
        html.append("                html += '<span class=\"file-date\">' + date + '</span>';\n");
        html.append("                html += '</div>';\n");
        html.append("            });\n");
        html.append("            \n");
        html.append("            fileList.innerHTML = html;\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        function handleFileClick(fileName, isDirectory) {\n");
        html.append("            if (isDirectory) {\n");
        html.append("                window.location.href = '/files/' + fileName;\n");
        html.append("            } else {\n");
        html.append("                window.open('/api/files/download/' + fileName, '_blank');\n");
        html.append("            }\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        // Afficher les fichiers au chargement\n");
        html.append("        displayFiles();\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return "HTTP/1.1 200 OK\r\n" +
               "Content-Type: text/html; charset=utf-8\r\n" +
               "Access-Control-Allow-Origin: *\r\n" +
               "Cache-Control: public, max-age=300\r\n" +
               "Content-Length: " + html.length() + "\r\n" +
               "\r\n" +
               html.toString();
    }
    
    public void stop() {
        isRunning = false;
        executor.shutdownNow();
        try {
            if (selector != null) selector.close();
            if (serverChannel != null) serverChannel.close();
        } catch (IOException e) {
            Log.e(TAG, "Erreur arrêt serveur HTTP", e);
        }
        Log.i(TAG, "Serveur HTTP arrêté");
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public int getPort() {
        return HTTP_PORT;
    }
}
