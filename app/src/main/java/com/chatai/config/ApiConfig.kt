package com.chatai.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration centralisée des URLs et endpoints API
 * 
 * ⭐ OBJECTIF: Centraliser toutes les URLs hardcodées pour faciliter la maintenance
 * et permettre la configuration via SharedPreferences si nécessaire.
 * 
 * Migration progressive depuis les constantes dispersées dans le code.
 */
object ApiConfig {
    
    // ============================================
    // OLLAMA CLOUD API
    // ============================================
    
    /** Base URL Ollama Cloud */
    const val OLLAMA_CLOUD_BASE = "https://ollama.com"
    
    /** API Chat Ollama Cloud (format natif) */
    const val OLLAMA_CLOUD_CHAT = "$OLLAMA_CLOUD_BASE/api/chat"
    
    /** API Tags Ollama Cloud (liste des modèles) */
    const val OLLAMA_CLOUD_TAGS = "$OLLAMA_CLOUD_BASE/api/tags"
    
    /** API Embeddings Ollama Cloud */
    const val OLLAMA_CLOUD_EMBEDDINGS = "$OLLAMA_CLOUD_BASE/api/embeddings"
    
    /** API Web Search Ollama Cloud */
    const val OLLAMA_CLOUD_WEB_SEARCH = "$OLLAMA_CLOUD_BASE/api/web_search"
    
    /** URL compte Ollama (pour liens d'aide) */
    const val OLLAMA_ACCOUNT_URL = "$OLLAMA_CLOUD_BASE/account"
    
    // ============================================
    // HUGGING FACE API
    // ============================================
    
    /** Base URL Hugging Face Router */
    const val HUGGINGFACE_ROUTER_BASE = "https://router.huggingface.co"
    
    /** Endpoint OpenAI-compatible Hugging Face */
    const val HUGGINGFACE_CHAT_COMPLETIONS = "$HUGGINGFACE_ROUTER_BASE/v1/chat/completions"
    
    /** Base URL pour modèles Hugging Face Inference */
    const val HUGGINGFACE_INFERENCE_BASE = "$HUGGINGFACE_ROUTER_BASE/hf-inference/models"
    
    /** API Hugging Face Models (liste des modèles) */
    const val HUGGINGFACE_API_MODELS = "https://huggingface.co/api/models"
    
    /** URL settings Hugging Face (pour liens d'aide) */
    const val HUGGINGFACE_SETTINGS_URL = "https://huggingface.co/settings/tokens"
    
    // ============================================
    // OPENAI API
    // ============================================
    
    /** Base URL OpenAI */
    const val OPENAI_BASE = "https://api.openai.com"
    
    /** API Chat Completions OpenAI */
    const val OPENAI_CHAT_COMPLETIONS = "$OPENAI_BASE/v1/chat/completions"
    
    /** URL API keys OpenAI (pour liens d'aide) */
    const val OPENAI_API_KEYS_URL = "https://platform.openai.com/api-keys"
    
    // ============================================
    // ANTHROPIC API
    // ============================================
    
    /** Base URL Anthropic */
    const val ANTHROPIC_BASE = "https://api.anthropic.com"
    
    /** API Messages Anthropic */
    const val ANTHROPIC_MESSAGES = "$ANTHROPIC_BASE/v1/messages"
    
    // ============================================
    // AUTRES PROVIDERS
    // ============================================
    
    /** URL API keys Groq (pour liens d'aide) */
    const val GROQ_API_KEYS_URL = "https://console.groq.com/keys"
    
    /** URL API keys Perplexity (pour liens d'aide) */
    const val PERPLEXITY_API_KEYS_URL = "https://www.perplexity.ai/settings/api"
    
    // ============================================
    // SERVEURS LOCAUX (DEFAULTS)
    // ============================================
    
    /** Port par défaut Ollama local */
    const val OLLAMA_LOCAL_DEFAULT_PORT = 11434
    
    /** URL par défaut Ollama local (format OpenAI-compatible) */
    const val OLLAMA_LOCAL_DEFAULT = "http://localhost:$OLLAMA_LOCAL_DEFAULT_PORT/v1/chat/completions"
    
    /** Port par défaut Whisper Server */
    const val WHISPER_SERVER_DEFAULT_PORT = 11400
    
    /** URL par défaut Whisper Server */
    const val WHISPER_SERVER_DEFAULT = "http://127.0.0.1:$WHISPER_SERVER_DEFAULT_PORT/inference"
    
    /** Port par défaut TTS Server */
    const val TTS_SERVER_DEFAULT_PORT = 11401
    
    /** URL par défaut TTS Server */
    const val TTS_SERVER_DEFAULT = "http://127.0.0.1:$TTS_SERVER_DEFAULT_PORT"
    
    // ============================================
    // MÉTHODES UTILITAIRES
    // ============================================
    
    /**
     * Construit l'URL complète pour un modèle Hugging Face
     * @param modelId ID du modèle (ex: "sentence-transformers/all-MiniLM-L6-v2")
     * @return URL complète avec suffixe :hf-inference si nécessaire
     */
    fun getHuggingFaceModelUrl(modelId: String): String {
        val cleanModelId = if (modelId.endsWith(":hf-inference")) {
            modelId
        } else {
            "$modelId:hf-inference"
        }
        return "$HUGGINGFACE_INFERENCE_BASE/$cleanModelId"
    }
    
    /**
     * Construit l'URL Ollama local depuis une configuration
     * @param baseUrl URL de base (ex: "http://192.168.1.100:11434")
     * @param useOpenAIFormat Si true, ajoute /v1/chat/completions
     * @return URL complète
     */
    fun buildOllamaLocalUrl(baseUrl: String, useOpenAIFormat: Boolean = true): String {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        return if (useOpenAIFormat) {
            "$cleanUrl/v1/chat/completions"
        } else {
            "$cleanUrl/api/chat"
        }
    }
    
    /**
     * Construit l'URL Ollama local pour embeddings
     * @param baseUrl URL de base
     * @return URL complète pour embeddings
     */
    fun buildOllamaLocalEmbeddingsUrl(baseUrl: String): String {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        // Supprimer /v1/chat/completions si présent
        val base = if (cleanUrl.endsWith("/v1/chat/completions")) {
            cleanUrl.replace("/v1/chat/completions", "")
        } else {
            cleanUrl
        }
        return "$base/api/embeddings"
    }
    
    /**
     * Récupère une URL configurée depuis SharedPreferences avec fallback
     * @param context Context Android
     * @param key Clé dans SharedPreferences
     * @param defaultValue Valeur par défaut
     * @return URL configurée ou valeur par défaut
     */
    fun getConfiguredUrl(context: Context, key: String, defaultValue: String): String {
        val prefs = context.getSharedPreferences("chatai_config", Context.MODE_PRIVATE)
        return prefs.getString(key, defaultValue) ?: defaultValue
    }
}

