#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <mutex>
#include <android/log.h>

// Vraies inclusions llama.cpp
#include "llama.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Structure pour stocker le contexte du modele
// FIX: Ajout de context_id pour recherche correcte
struct ModelContext {
    jlong context_id;           // ID unique pour recherche
    llama_model* model_ptr;
    llama_context* ctx_ptr;
    llama_sampler* sampler_ptr;
    std::string model_path;
    float current_temperature;  // Temperature actuelle du sampler
    std::vector<llama_token> conversation_tokens; // Historique conversation
    
    ModelContext(jlong id) : context_id(id), model_ptr(nullptr), ctx_ptr(nullptr), 
                             sampler_ptr(nullptr), current_temperature(0.0f) {}
};

// Stockage global des contextes avec mutex pour thread-safety
static std::vector<std::unique_ptr<ModelContext>> g_contexts;
static std::mutex g_contexts_mutex;
static jlong g_next_context_id = 1;

// Helper: Trouver un contexte par ID (thread-safe)
static ModelContext* findContextById(jlong contextId) {
    for (auto& c : g_contexts) {
        if (c->context_id == contextId) {
            return c.get();
        }
    }
    return nullptr;
}

extern "C" {

/**
 * Initialise un modèle GGUF
 * @param env JNI environment
 * @param thiz JNI object
 * @param modelPath Chemin vers le fichier .gguf
 * @param nThreads Nombre de threads (défaut: 4)
 * @return ID du contexte (0 si erreur)
 */
JNIEXPORT jlong JNICALL
Java_com_chatai_services_LocalGGUFService_initModel(JNIEnv *env, jobject thiz, jstring modelPath, jint nThreads) {
    (void)thiz;
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("[initModel] Failed to get model path from JNI");
        return 0;
    }
    
    LOGI("[initModel] Starting initialization: %s (threads: %d)", path, nThreads);
    
    // Generer l'ID avant de creer le contexte
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    jlong context_id = g_next_context_id++;
    
    // FIX: Creer le contexte avec l'ID
    auto ctx = std::make_unique<ModelContext>(context_id);
    ctx->model_path = path;
    
    // Charger le modele
    LOGI("[initModel] Loading model file...");
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU uniquement pour Android
    ctx->model_ptr = llama_model_load_from_file(path, model_params);
    
    if (!ctx->model_ptr) {
        LOGE("[initModel] FAILED to load model: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    LOGI("[initModel] Model loaded successfully");
    
    // Creer le contexte llama
    LOGI("[initModel] Creating llama context...");
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;  // Taille du contexte
    ctx_params.n_batch = 512; // Taille du batch
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;
    ctx_params.no_perf = true;
    
    ctx->ctx_ptr = llama_init_from_model(ctx->model_ptr, ctx_params);
    if (!ctx->ctx_ptr) {
        LOGE("[initModel] FAILED to create llama context");
        llama_model_free(ctx->model_ptr);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    LOGI("[initModel] Llama context created");
    
    // Initialiser le sampler avec greedy par defaut (sera mis a jour dans generate si temperature > 0)
    LOGI("[initModel] Initializing sampler (greedy default)...");
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    ctx->sampler_ptr = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_greedy());
    ctx->current_temperature = 0.0f;
    
    // Ajouter au stockage global
    g_contexts.push_back(std::move(ctx));
    
    LOGI("[initModel] SUCCESS - Model initialized with context ID: %ld", context_id);
    env->ReleaseStringUTFChars(modelPath, path);
    
    return context_id;
}

/**
 * Initialise un modele GGUF avec parametres avances
 * @param env JNI environment
 * @param thiz JNI object
 * @param modelPath Chemin vers le fichier .gguf
 * @param nThreads Nombre de threads
 * @param contextSize Taille du contexte
 * @param batchSize Taille des batches
 * @param flashAttention Activer flash attention
 * @return ID du contexte (0 si erreur)
 */
JNIEXPORT jlong JNICALL
Java_com_chatai_services_LocalGGUFService_initModelWithParams(
    JNIEnv *env, jobject thiz, jstring modelPath, jint nThreads, 
    jint contextSize, jint batchSize, jboolean flashAttention) {
    (void)thiz;
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("[initModelWithParams] Failed to get model path from JNI");
        return 0;
    }
    
    LOGI("[initModelWithParams] Starting initialization: %s", path);
    LOGI("[initModelWithParams] Params: threads=%d, ctx=%d, batch=%d, flash=%d", 
         nThreads, contextSize, batchSize, flashAttention);
    
    // Generer l'ID avant de creer le contexte
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    jlong context_id = g_next_context_id++;
    
    auto ctx = std::make_unique<ModelContext>(context_id);
    ctx->model_path = path;
    
    // Charger le modele
    LOGI("[initModelWithParams] Loading model file...");
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU uniquement pour Android
    ctx->model_ptr = llama_model_load_from_file(path, model_params);
    
    if (!ctx->model_ptr) {
        LOGE("[initModelWithParams] FAILED to load model: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    LOGI("[initModelWithParams] Model loaded successfully");
    
    // Creer le contexte llama avec parametres personnalises
    LOGI("[initModelWithParams] Creating llama context...");
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_batch = batchSize;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;
    ctx_params.no_perf = true;
    
    // Flash attention - utilise flash_attn_type (llama.cpp b7223+)
    if (flashAttention) {
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_ENABLED;
        LOGI("[initModelWithParams] Flash attention ENABLED");
    } else {
        ctx_params.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_AUTO;
    }
    
    ctx->ctx_ptr = llama_init_from_model(ctx->model_ptr, ctx_params);
    if (!ctx->ctx_ptr) {
        LOGE("[initModelWithParams] FAILED to create llama context");
        llama_model_free(ctx->model_ptr);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }
    LOGI("[initModelWithParams] Llama context created (n_ctx=%d)", contextSize);
    
    // Initialiser le sampler avec greedy par defaut
    LOGI("[initModelWithParams] Initializing sampler (greedy default)...");
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    ctx->sampler_ptr = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_greedy());
    ctx->current_temperature = 0.0f;
    
    // Ajouter au stockage global
    g_contexts.push_back(std::move(ctx));
    
    LOGI("[initModelWithParams] SUCCESS - Model initialized with context ID: %ld", context_id);
    env->ReleaseStringUTFChars(modelPath, path);
    
    return context_id;
}

// Helper: Reconfigurer le sampler avec une nouvelle temperature (version simple)
static void updateSamplerTemperature(ModelContext* ctx, float temperature) {
    if (!ctx || !ctx->sampler_ptr) return;
    
    // Si la temperature n'a pas change, ne rien faire
    if (ctx->current_temperature == temperature) return;
    
    LOGI("[updateSampler] Changing temperature from %.2f to %.2f", ctx->current_temperature, temperature);
    
    // Liberer l'ancien sampler
    llama_sampler_free(ctx->sampler_ptr);
    
    // Creer un nouveau sampler
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    ctx->sampler_ptr = llama_sampler_chain_init(sparams);
    
    if (temperature <= 0.0f) {
        // Temperature 0 = greedy (deterministe)
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_greedy());
        LOGI("[updateSampler] Using greedy sampler (deterministic)");
    } else {
        // Temperature > 0 = sampling avec temperature
        // Ajouter top-k, top-p et temperature pour un meilleur sampling
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_dist(0)); // seed aleatoire
        LOGI("[updateSampler] Using temperature sampler (temp=%.2f, top_k=40, top_p=0.95)", temperature);
    }
    
    ctx->current_temperature = temperature;
}

// Helper: Reconfigurer le sampler avec tous les parametres
static void updateSamplerWithParams(ModelContext* ctx, float temperature, int topK, float topP, float repeatPenalty) {
    if (!ctx || !ctx->sampler_ptr) return;
    
    LOGI("[updateSamplerWithParams] temp=%.2f, top_k=%d, top_p=%.2f, repeat_penalty=%.2f", 
         temperature, topK, topP, repeatPenalty);
    
    // Liberer l'ancien sampler
    llama_sampler_free(ctx->sampler_ptr);
    
    // Creer un nouveau sampler
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    ctx->sampler_ptr = llama_sampler_chain_init(sparams);
    
    if (temperature <= 0.0f) {
        // Temperature 0 = greedy (deterministe)
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_greedy());
        LOGI("[updateSamplerWithParams] Using greedy sampler (deterministic)");
    } else {
        // Ajouter repeat penalty si > 1
        if (repeatPenalty > 1.0f) {
            // repeat_penalty, frequency_penalty, presence_penalty, penalty_last_n
            llama_sampler_chain_add(ctx->sampler_ptr, 
                llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
            LOGI("[updateSamplerWithParams] Added repeat penalty: %.2f", repeatPenalty);
        }
        
        // Ajouter top-k
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_top_k(topK));
        
        // Ajouter top-p (nucleus sampling)
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_top_p(topP, 1));
        
        // Ajouter temperature
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_temp(temperature));
        
        // Ajouter distribution aleatoire
        llama_sampler_chain_add(ctx->sampler_ptr, llama_sampler_init_dist(0));
        
        LOGI("[updateSamplerWithParams] Using advanced sampler");
    }
    
    ctx->current_temperature = temperature;
}

/**
 * Genere une reponse depuis le modele
 * @param env JNI environment
 * @param thiz JNI object
 * @param contextId ID du contexte (retourne par initModel)
 * @param prompt Prompt utilisateur
 * @param maxTokens Nombre maximum de tokens a generer
 * @param temperature Temperature (0.0-1.0) - 0 = greedy, >0 = sampling
 * @return Reponse generee
 */
JNIEXPORT jstring JNICALL
Java_com_chatai_services_LocalGGUFService_generate(JNIEnv *env, jobject thiz, jlong contextId, jstring prompt, jint maxTokens, jfloat temperature) {
    (void)thiz;
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        LOGE("[generate] Failed to get prompt from JNI");
        return env->NewStringUTF("Error: Failed to get prompt");
    }
    
    LOGI("[generate] Context: %ld, maxTokens: %d, temp: %.2f", contextId, maxTokens, temperature);
    LOGI("[generate] Prompt (first 100 chars): %.100s...", prompt_str);
    
    // FIX: Utiliser findContextById au lieu de comparer les pointeurs
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    ModelContext* ctx = findContextById(contextId);
    
    if (!ctx) {
        LOGE("[generate] Context ID %ld not found in %zu contexts", contextId, g_contexts.size());
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Context not found");
    }
    
    if (!ctx->ctx_ptr || !ctx->model_ptr) {
        LOGE("[generate] Context %ld has null pointers (ctx_ptr=%p, model_ptr=%p)", 
             contextId, (void*)ctx->ctx_ptr, (void*)ctx->model_ptr);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Invalid context state");
    }
    
    // FIX: Mettre a jour le sampler si la temperature a change
    updateSamplerTemperature(ctx, temperature);
    
    // Obtenir le vocabulaire
    const llama_vocab* vocab = llama_model_get_vocab(ctx->model_ptr);
    if (!vocab) {
        LOGE("[generate] Failed to get vocabulary");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to get vocabulary");
    }
    
    // Tokeniser le prompt
    LOGI("[generate] Tokenizing prompt...");
    const int n_prompt = -llama_tokenize(vocab, prompt_str, strlen(prompt_str), NULL, 0, true, true);
    if (n_prompt <= 0) {
        LOGE("[generate] Failed to tokenize prompt (n_prompt=%d)", n_prompt);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to tokenize prompt");
    }
    LOGI("[generate] Prompt tokenized: %d tokens", n_prompt);
    
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(vocab, prompt_str, strlen(prompt_str), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("[generate] Failed to tokenize prompt (second call)");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to tokenize prompt");
    }
    
    // FIX: Limiter le prompt a la taille du contexte moins les tokens de generation
    const int n_ctx = llama_n_ctx(ctx->ctx_ptr);
    const int max_prompt_tokens = n_ctx - maxTokens - 16; // Marge de securite
    
    if (n_prompt > max_prompt_tokens) {
        LOGW("[generate] Prompt too long (%d tokens), truncating to %d tokens", n_prompt, max_prompt_tokens);
        prompt_tokens.resize(max_prompt_tokens);
    }
    
    LOGI("[generate] Processing %zu prompt tokens (context size: %d)", prompt_tokens.size(), n_ctx);
    
    // FIX: Traiter le prompt par batches pour eviter le crash
    const int batch_size = 512; // Taille de batch raisonnable
    
    for (size_t i = 0; i < prompt_tokens.size(); i += batch_size) {
        size_t batch_tokens = std::min((size_t)batch_size, prompt_tokens.size() - i);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i, batch_tokens);
        
        // Verifier si le modele a un encodeur (encode-decoder) - seulement pour le premier batch
        if (i == 0 && llama_model_has_encoder(ctx->model_ptr)) {
            LOGI("[generate] Model has encoder, encoding first...");
            if (llama_encode(ctx->ctx_ptr, batch) != 0) {
                LOGE("[generate] Failed to encode");
                env->ReleaseStringUTFChars(prompt, prompt_str);
                return env->NewStringUTF("Error: Failed to encode");
            }
            
            llama_token decoder_start_token_id = llama_model_decoder_start_token(ctx->model_ptr);
            if (decoder_start_token_id == LLAMA_TOKEN_NULL) {
                decoder_start_token_id = llama_vocab_bos(vocab);
            }
            batch = llama_batch_get_one(&decoder_start_token_id, 1);
        }
        
        // Decoder ce batch
        if (llama_decode(ctx->ctx_ptr, batch) != 0) {
            LOGE("[generate] Failed to decode prompt batch at position %zu", i);
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("Error: Failed to process prompt");
        }
        
        LOGI("[generate] Processed prompt batch %zu/%zu", i / batch_size + 1, (prompt_tokens.size() + batch_size - 1) / batch_size);
    }
    
    // Generer les tokens
    LOGI("[generate] Prompt processed, starting token generation...");
    std::string response;
    int n_decode = 0;
    llama_token current_token = 0;
    
    for (int i = 0; i < maxTokens; i++) {
        // Echantillonner le prochain token
        llama_token new_token_id = llama_sampler_sample(ctx->sampler_ptr, ctx->ctx_ptr, -1);
        
        // Verifier si c'est la fin de generation
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("[generate] End of generation token detected at step %d", i);
            break;
        }
        
        // Convertir le token en texte
        char buf[128];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            LOGE("[generate] Failed to convert token %d to piece", new_token_id);
            break;
        }
        
        std::string token_str(buf, n);
        response += token_str;
        
        // Stocker le token dans l'historique de conversation
        ctx->conversation_tokens.push_back(new_token_id);
        current_token = new_token_id;
        n_decode++;
        
        // Decoder le token genere pour preparer le prochain
        llama_batch next_batch = llama_batch_get_one(&current_token, 1);
        if (llama_decode(ctx->ctx_ptr, next_batch) != 0) {
            LOGE("[generate] Failed to decode generated token at step %d", i);
            break;
        }
    }
    
    LOGI("[generate] SUCCESS - Generated %d tokens", n_decode);
    LOGI("[generate] Response (first 200 chars): %.200s", response.c_str());
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    return env->NewStringUTF(response.c_str());
}

/**
 * Genere une reponse avec parametres avances
 * @param env JNI environment
 * @param thiz JNI object
 * @param contextId ID du contexte
 * @param prompt Prompt utilisateur
 * @param maxTokens Nombre maximum de tokens
 * @param temperature Temperature (0.0-2.0)
 * @param topK Top-K sampling
 * @param topP Top-P (nucleus) sampling
 * @param repeatPenalty Penalite de repetition
 * @return Reponse generee
 */
JNIEXPORT jstring JNICALL
Java_com_chatai_services_LocalGGUFService_generateWithParams(
    JNIEnv *env, jobject thiz, jlong contextId, jstring prompt, 
    jint maxTokens, jfloat temperature, jint topK, jfloat topP, jfloat repeatPenalty) {
    (void)thiz;
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        LOGE("[generateWithParams] Failed to get prompt from JNI");
        return env->NewStringUTF("Error: Failed to get prompt");
    }
    
    LOGI("[generateWithParams] Context: %ld, maxTokens: %d", contextId, maxTokens);
    LOGI("[generateWithParams] temp=%.2f, top_k=%d, top_p=%.2f, repeat_penalty=%.2f", 
         temperature, topK, topP, repeatPenalty);
    LOGI("[generateWithParams] Prompt (first 100 chars): %.100s...", prompt_str);
    
    // Trouver le contexte
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    ModelContext* ctx = findContextById(contextId);
    
    if (!ctx) {
        LOGE("[generateWithParams] Context ID %ld not found", contextId);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Context not found");
    }
    
    if (!ctx->ctx_ptr || !ctx->model_ptr) {
        LOGE("[generateWithParams] Context %ld has null pointers", contextId);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Invalid context state");
    }
    
    // Mettre a jour le sampler avec tous les parametres
    updateSamplerWithParams(ctx, temperature, topK, topP, repeatPenalty);
    
    // Obtenir le vocabulaire
    const llama_vocab* vocab = llama_model_get_vocab(ctx->model_ptr);
    if (!vocab) {
        LOGE("[generateWithParams] Failed to get vocabulary");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to get vocabulary");
    }
    
    // Tokeniser le prompt
    LOGI("[generateWithParams] Tokenizing prompt...");
    const int n_prompt = -llama_tokenize(vocab, prompt_str, strlen(prompt_str), NULL, 0, true, true);
    if (n_prompt <= 0) {
        LOGE("[generateWithParams] Failed to tokenize prompt (n_prompt=%d)", n_prompt);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to tokenize prompt");
    }
    LOGI("[generateWithParams] Prompt tokenized: %d tokens", n_prompt);
    
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(vocab, prompt_str, strlen(prompt_str), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("[generateWithParams] Failed to tokenize prompt (second call)");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Failed to tokenize prompt");
    }
    
    // Limiter le prompt a la taille du contexte
    const int n_ctx = llama_n_ctx(ctx->ctx_ptr);
    const int max_prompt_tokens = n_ctx - maxTokens - 16;
    
    if (n_prompt > max_prompt_tokens) {
        LOGW("[generateWithParams] Prompt too long (%d tokens), truncating to %d", n_prompt, max_prompt_tokens);
        prompt_tokens.resize(max_prompt_tokens);
    }
    
    LOGI("[generateWithParams] Processing %zu prompt tokens (context size: %d)", prompt_tokens.size(), n_ctx);
    
    // Traiter le prompt par batches
    const int batch_size = 512;
    
    for (size_t i = 0; i < prompt_tokens.size(); i += batch_size) {
        size_t batch_tokens = std::min((size_t)batch_size, prompt_tokens.size() - i);
        llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i, batch_tokens);
        
        // Verifier si le modele a un encodeur
        if (i == 0 && llama_model_has_encoder(ctx->model_ptr)) {
            LOGI("[generateWithParams] Model has encoder, encoding first...");
            if (llama_encode(ctx->ctx_ptr, batch) != 0) {
                LOGE("[generateWithParams] Failed to encode");
                env->ReleaseStringUTFChars(prompt, prompt_str);
                return env->NewStringUTF("Error: Failed to encode");
            }
            
            llama_token decoder_start_token_id = llama_model_decoder_start_token(ctx->model_ptr);
            if (decoder_start_token_id == LLAMA_TOKEN_NULL) {
                decoder_start_token_id = llama_vocab_bos(vocab);
            }
            batch = llama_batch_get_one(&decoder_start_token_id, 1);
        }
        
        if (llama_decode(ctx->ctx_ptr, batch) != 0) {
            LOGE("[generateWithParams] Failed to decode prompt batch at position %zu", i);
            env->ReleaseStringUTFChars(prompt, prompt_str);
            return env->NewStringUTF("Error: Failed to process prompt");
        }
    }
    
    // Generer les tokens
    LOGI("[generateWithParams] Prompt processed, starting token generation...");
    std::string response;
    int n_decode = 0;
    llama_token current_token = 0;
    
    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token_id = llama_sampler_sample(ctx->sampler_ptr, ctx->ctx_ptr, -1);
        
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            LOGI("[generateWithParams] End of generation at step %d", i);
            break;
        }
        
        char buf[128];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            LOGE("[generateWithParams] Failed to convert token %d", new_token_id);
            break;
        }
        
        std::string token_str(buf, n);
        response += token_str;
        
        ctx->conversation_tokens.push_back(new_token_id);
        current_token = new_token_id;
        n_decode++;
        
        llama_batch next_batch = llama_batch_get_one(&current_token, 1);
        if (llama_decode(ctx->ctx_ptr, next_batch) != 0) {
            LOGE("[generateWithParams] Failed to decode token at step %d", i);
            break;
        }
    }
    
    LOGI("[generateWithParams] SUCCESS - Generated %d tokens", n_decode);
    LOGI("[generateWithParams] Response (first 200 chars): %.200s", response.c_str());
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    return env->NewStringUTF(response.c_str());
}

/**
 * Libere un modele et son contexte
 * @param env JNI environment
 * @param thiz JNI object
 * @param contextId ID du contexte a liberer
 */
JNIEXPORT void JNICALL
Java_com_chatai_services_LocalGGUFService_freeModelNative(JNIEnv *env, jobject thiz, jlong contextId) {
    (void)env; (void)thiz;
    LOGI("[freeModel] Freeing context: %ld", contextId);
    
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    
    // FIX: Utiliser context_id pour la recherche
    for (auto it = g_contexts.begin(); it != g_contexts.end(); ++it) {
        ModelContext* ctx = it->get();
        if (ctx->context_id == contextId) {
            LOGI("[freeModel] Found context %ld, freeing resources...", contextId);
            
            // Liberer le sampler
            if (ctx->sampler_ptr) {
                llama_sampler_free(ctx->sampler_ptr);
                ctx->sampler_ptr = nullptr;
                LOGI("[freeModel] Sampler freed");
            }
            
            // Liberer le contexte llama
            if (ctx->ctx_ptr) {
                llama_free(ctx->ctx_ptr);
                ctx->ctx_ptr = nullptr;
                LOGI("[freeModel] Llama context freed");
            }
            
            // Liberer le modele
            if (ctx->model_ptr) {
                llama_model_free(ctx->model_ptr);
                ctx->model_ptr = nullptr;
                LOGI("[freeModel] Model freed");
            }
            
            // Nettoyer l'historique de conversation
            ctx->conversation_tokens.clear();
            
            g_contexts.erase(it);
            LOGI("[freeModel] SUCCESS - Context %ld completely freed", contextId);
            return;
        }
    }
    
    LOGW("[freeModel] Context %ld not found (may have been already freed)", contextId);
}

/**
 * Efface l'historique de conversation (reset du contexte)
 * @param env JNI environment
 * @param thiz JNI object
 * @param contextId ID du contexte
 */
JNIEXPORT void JNICALL
Java_com_chatai_services_LocalGGUFService_clearConversationNative(JNIEnv *env, jobject thiz, jlong contextId) {
    (void)env; (void)thiz;
    LOGI("[clearConversation] Clearing context: %ld", contextId);
    
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    ModelContext* ctx = findContextById(contextId);
    
    if (!ctx) {
        LOGW("[clearConversation] Context %ld not found", contextId);
        return;
    }
    
    // Effacer l'historique de tokens
    ctx->conversation_tokens.clear();
    
    // Note: llama_kv_cache_clear n'est pas disponible dans cette version
    // Le reset du contexte se fait en recreant le contexte si necessaire
    
    LOGI("[clearConversation] SUCCESS - Context %ld cleared (tokens history)", contextId);
}

/**
 * Verifie si la bibliotheque native est chargee
 * @return true si chargee
 */
JNIEXPORT jboolean JNICALL
Java_com_chatai_services_LocalGGUFService_isLibraryLoadedNative(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return JNI_TRUE;
}

/**
 * Retourne le nombre de contextes actifs
 * @return nombre de contextes
 */
JNIEXPORT jint JNICALL
Java_com_chatai_services_LocalGGUFService_getActiveContextCountNative(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    std::lock_guard<std::mutex> lock(g_contexts_mutex);
    return static_cast<jint>(g_contexts.size());
}

} // extern "C"

