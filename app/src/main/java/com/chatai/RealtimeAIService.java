package com.chatai;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.MediaType;
import okhttp3.Call;
import okhttp3.Callback;

/**
 * Service IA temps réel qui utilise HTTP et WebSocket pour des réponses instantanées
 * Intègre avec Hugging Face et d'autres services d'IA
 */
public class RealtimeAIService {
    private static final String TAG = "RealtimeAIService";
    
    private Context context;
    private SecureConfig secureConfig;
    private ChatDatabase chatDatabase;
    private OkHttpClient httpClient;
    private ExecutorService executor;
    private HttpServer httpServer;
    private WebSocketServer webSocketServer;
    
    // URLs des services d'IA
    private static final String HUGGINGFACE_API_URL = "https://api-inference.huggingface.co/models/";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    
    // Modèles disponibles
    private static final String[] AI_MODELS = {
        "microsoft/DialoGPT-medium",
        "facebook/blenderbot-400M-distill", 
        "microsoft/DialoGPT-small",
        "facebook/blenderbot_small-90M"
    };
    
    public RealtimeAIService(Context context, HttpServer httpServer, WebSocketServer webSocketServer) {
        this.context = context;
        this.httpServer = httpServer;
        this.webSocketServer = webSocketServer;
        this.secureConfig = new SecureConfig(context);
        this.chatDatabase = new ChatDatabase(context);
        this.executor = Executors.newFixedThreadPool(4);
        
        // Initialiser le client HTTP
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Traite une requête IA en temps réel
     */
    public CompletableFuture<String> processAIRequest(String message, String personality) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Vérifier le cache d'abord
                String cacheKey = message + "_" + personality;
                String cachedResponse = chatDatabase.getCachedResponse(cacheKey);
                if (cachedResponse != null) {
                    Log.d(TAG, "Réponse trouvée dans le cache");
                    return cachedResponse;
                }
                
                // Obtenir le token API sécurisé
                String apiToken = secureConfig.getApiToken();
                if (apiToken == null || apiToken.isEmpty()) {
                    return getFallbackResponse(message, personality);
                }
                
                // Essayer différents services d'IA
                String response = tryHuggingFaceAPI(message, personality, apiToken);
                if (response == null) {
                    response = tryOpenAIAPI(message, personality, apiToken);
                }
                
                if (response == null) {
                    response = getFallbackResponse(message, personality);
                }
                
                // Sauvegarder dans le cache
                chatDatabase.cacheAIResponse(cacheKey, response);
                
                return response;
                
            } catch (Exception e) {
                Log.e(TAG, "Erreur traitement requête IA", e);
                return getErrorResponse(e.getMessage());
            }
        }, executor);
    }
    
    /**
     * Essaie l'API Hugging Face
     */
    private String tryHuggingFaceAPI(String message, String personality, String apiToken) {
        try {
            String model = getModelForPersonality(personality);
            String url = HUGGINGFACE_API_URL + model;
            
            String requestBody = String.format(
                "{\"inputs\":\"%s\",\"parameters\":{\"max_length\":150,\"temperature\":0.7}}",
                SecurityUtils.sanitizeInput(message)
            );
            
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(requestBody, JSON);
            
            Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiToken)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
            
            Response response = httpClient.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                return parseHuggingFaceResponse(responseBody, personality);
            } else {
                Log.w(TAG, "Erreur API Hugging Face: " + response.code());
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur appel Hugging Face", e);
            return null;
        }
    }
    
    /**
     * Essaie l'API OpenAI (si disponible)
     */
    private String tryOpenAIAPI(String message, String personality, String apiToken) {
        try {
            String requestBody = String.format(
                "{\"model\":\"gpt-3.5-turbo\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"max_tokens\":150}",
                SecurityUtils.sanitizeInput(message)
            );
            
            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(requestBody, JSON);
            
            Request request = new Request.Builder()
                .url(OPENAI_API_URL)
                .addHeader("Authorization", "Bearer " + apiToken)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();
            
            Response response = httpClient.newCall(request).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                return parseOpenAIResponse(responseBody, personality);
            } else {
                Log.w(TAG, "Erreur API OpenAI: " + response.code());
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur appel OpenAI", e);
            return null;
        }
    }
    
    /**
     * Parse la réponse Hugging Face
     */
    private String parseHuggingFaceResponse(String responseBody, String personality) {
        try {
            // Parser JSON simple pour extraire le texte généré
            if (responseBody.contains("generated_text")) {
                String generatedText = extractJsonValue(responseBody, "generated_text");
                if (generatedText != null && !generatedText.isEmpty()) {
                    return enhanceWithPersonality(generatedText, personality);
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Erreur parsing Hugging Face", e);
            return null;
        }
    }
    
    /**
     * Parse la réponse OpenAI
     */
    private String parseOpenAIResponse(String responseBody, String personality) {
        try {
            if (responseBody.contains("choices")) {
                // Parser JSON pour extraire le contenu de la réponse
                String content = extractJsonValue(responseBody, "content");
                if (content != null && !content.isEmpty()) {
                    return enhanceWithPersonality(content, personality);
                }
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Erreur parsing OpenAI", e);
            return null;
        }
    }
    
    /**
     * Améliore la réponse avec la personnalité
     */
    private String enhanceWithPersonality(String response, String personality) {
        String cleanResponse = SecurityUtils.sanitizeInput(response);
        
        switch (personality.toLowerCase()) {
            case "casual":
                return cleanResponse + " 😊";
            case "friendly":
                return "Salut ! " + cleanResponse + " 😄";
            case "professional":
                return "Voici ma réponse professionnelle : " + cleanResponse;
            case "creative":
                return "🎨 " + cleanResponse + " ✨";
            case "funny":
                return cleanResponse + " 😂 (C'est de l'humour, hein !)";
            default:
                return cleanResponse;
        }
    }
    
    /**
     * Obtient un modèle approprié pour la personnalité
     */
    private String getModelForPersonality(String personality) {
        switch (personality.toLowerCase()) {
            case "casual":
            case "friendly":
                return "facebook/blenderbot-400M-distill";
            case "professional":
                return "microsoft/DialoGPT-medium";
            case "creative":
            case "funny":
                return "microsoft/DialoGPT-small";
            default:
                return AI_MODELS[0];
        }
    }
    
    /**
     * Réponse de fallback si les APIs ne fonctionnent pas
     */
    private String getFallbackResponse(String message, String personality) {
        String[] fallbackResponses = {
            "Je comprends votre question : \"" + SecurityUtils.sanitizeInput(message) + "\". Voici ma réponse !",
            "Excellente question ! Laissez-moi y réfléchir... " + SecurityUtils.sanitizeInput(message),
            "Intéressant ! Vous demandez : \"" + SecurityUtils.sanitizeInput(message) + "\". Voici ce que je pense !"
        };
        
        String baseResponse = fallbackResponses[(int)(Math.random() * fallbackResponses.length)];
        return enhanceWithPersonality(baseResponse, personality);
    }
    
    /**
     * Réponse d'erreur
     */
    private String getErrorResponse(String error) {
        return "Désolé, j'ai rencontré une erreur : " + SecurityUtils.sanitizeInput(error) + 
               ". Pouvez-vous reformuler votre question ?";
    }
    
    /**
     * Extrait une valeur d'un JSON simple
     */
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
    
    /**
     * Diffuse une réponse IA via WebSocket
     */
    public void broadcastAIResponse(String message, String response) {
        if (webSocketServer != null && webSocketServer.isRunning()) {
            String jsonMessage = String.format(
                "{\"type\":\"ai_response\",\"original\":\"%s\",\"response\":\"%s\",\"timestamp\":%d}",
                SecurityUtils.sanitizeInput(message),
                SecurityUtils.sanitizeInput(response),
                System.currentTimeMillis()
            );
            webSocketServer.broadcastMessage(jsonMessage);
        }
    }
    
    /**
     * Arrête le service
     */
    public void shutdown() {
        executor.shutdownNow();
        Log.i(TAG, "Service IA temps réel arrêté");
    }
    
    /**
     * Vérifie la santé du service
     */
    public boolean isHealthy() {
        return httpClient != null && !executor.isShutdown();
    }
    
    /**
     * Obtient les statistiques du service
     */
    public String getStats() {
        return String.format(
            "{\"status\":\"%s\",\"cache_entries\":%d,\"executor_active\":%s}",
            isHealthy() ? "healthy" : "unhealthy",
            chatDatabase != null ? 1 : 0, // Simplifié
            !executor.isShutdown()
        );
    }
}
