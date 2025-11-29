package com.chatai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 🔐 KEYRING MANAGER - Gestion centralisée des clés API
 * 
 * Système unifié pour stocker et récupérer toutes les clés API de manière sécurisée.
 * Utilise Android Keystore System avec chiffrement AES-256/GCM.
 * 
 * Providers supportés:
 * - Hugging Face
 * - Ollama Cloud
 * - OpenAI
 * - Anthropic
 * - Groq
 * - Perplexity
 * 
 * Migration automatique depuis SharedPreferences/SecureConfig existants.
 */
public class KeyringManager {
    private static final String TAG = "KeyringManager";
    private static final String PREFS_NAME = "keyring_config";
    private static final String KEYSTORE_ALIAS = "ChatAI_KeyringKey";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String MIGRATION_FLAG = "keyring_migrated";
    
    // Clés API par provider
    private static final String KEY_HUGGINGFACE = "api_key_huggingface";
    private static final String KEY_OLLAMA_CLOUD = "api_key_ollama_cloud";
    private static final String KEY_OPENAI = "api_key_openai";
    private static final String KEY_ANTHROPIC = "api_key_anthropic";
    private static final String KEY_GROQ = "api_key_groq";
    private static final String KEY_PERPLEXITY = "api_key_perplexity";
    
    private final Context context;
    private final SharedPreferences prefs;
    private SecretKey keystoreKey;
    private boolean useKeystore = false;
    
    // Singleton
    private static KeyringManager instance;
    
    public static synchronized KeyringManager getInstance(Context context) {
        if (instance == null) {
            instance = new KeyringManager(context.getApplicationContext());
        }
        return instance;
    }
    
    private KeyringManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeKeystore();
        migrateFromLegacy();
    }
    
    /**
     * Initialise Android Keystore System
     */
    private void initializeKeystore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);
                keystoreKey = entry.getSecretKey();
                useKeystore = true;
                Log.d(TAG, "✅ Keystore key chargée depuis Android Keystore");
            } else {
                // Créer une nouvelle clé
                KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    keyGenerator.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | 
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                }
                keystoreKey = keyGenerator.generateKey();
                useKeystore = true;
                Log.d(TAG, "✅ Nouvelle clé Keystore générée");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur initialisation Keystore, utilisation fallback", e);
            useKeystore = false;
        }
    }
    
    /**
     * Chiffre une valeur avec AES-256/GCM
     */
    private String encrypt(String plaintext) throws Exception {
        if (!useKeystore || keystoreKey == null) {
            throw new RuntimeException("Keystore non disponible");
        }
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey);
        
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        // Combiner IV + données chiffrées
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }
    
    /**
     * Déchiffre une valeur avec AES-256/GCM
     */
    private String decrypt(String ciphertext) throws Exception {
        if (!useKeystore || keystoreKey == null) {
            throw new RuntimeException("Keystore non disponible");
        }
        
        byte[] combined = Base64.decode(ciphertext, Base64.NO_WRAP);
        
        // Extraire IV (12 bytes pour GCM)
        byte[] iv = new byte[12];
        byte[] encrypted = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey, spec);
        
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Sauvegarde une clé API pour un provider
     */
    public void setApiKey(String provider, String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            clearApiKey(provider);
            return;
        }
        
        try {
            String keyName = getKeyName(provider);
            if (keyName == null) {
                Log.e(TAG, "❌ setApiKey: Provider inconnu: " + provider);
                return;
            }
            
            String trimmedKey = apiKey.trim();
            
            // Vérifier si identique (ne pas logger si identique pour réduire le bruit)
            String existing = getApiKey(provider);
            if (existing != null && existing.equals(trimmedKey)) {
                return; // Clé identique, pas de changement
            }
            
            // Seulement logger si la clé change réellement
            String encrypted = encrypt(trimmedKey);
            prefs.edit().putString(keyName, encrypted).apply();
            Log.i(TAG, "✅ Clé " + provider + " sauvegardée (" + trimmedKey.length() + " chars)");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur sauvegarde clé " + provider, e);
            throw new RuntimeException("Erreur sauvegarde clé " + provider, e);
        }
    }
    
    /**
     * Récupère une clé API pour un provider
     */
    public String getApiKey(String provider) {
        try {
            String keyName = getKeyName(provider);
            if (keyName == null) {
                Log.w(TAG, "Provider inconnu: " + provider);
                return null;
            }
            
            String encrypted = prefs.getString(keyName, null);
            if (encrypted == null) {
                return null;
            }
            
            String decrypted = decrypt(encrypted);
            // Ne pas logger getApiKey pour réduire le bruit (trop répétitif, appelé très souvent)
            return decrypted;
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur récupération clé " + provider, e);
            return null;
        }
    }
    
    /**
     * Vérifie si une clé API existe pour un provider
     */
    public boolean hasApiKey(String provider) {
        return getApiKey(provider) != null;
    }
    
    /**
     * Supprime une clé API pour un provider
     */
    public void clearApiKey(String provider) {
        String keyName = getKeyName(provider);
        if (keyName != null) {
            prefs.edit().remove(keyName).apply();
            Log.d(TAG, "🗑️ Clé " + provider + " supprimée");
        }
    }
    
    /**
     * Supprime toutes les clés API
     */
    public void clearAll() {
        prefs.edit().clear().apply();
        Log.d(TAG, "🗑️ Toutes les clés supprimées");
    }
    
    /**
     * Obtient le nom de la clé pour un provider
     */
    private String getKeyName(String provider) {
        switch (provider.toLowerCase()) {
            case "huggingface":
            case "hf":
                return KEY_HUGGINGFACE;
            case "ollama":
            case "ollama_cloud":
                return KEY_OLLAMA_CLOUD;
            case "openai":
                return KEY_OPENAI;
            case "anthropic":
                return KEY_ANTHROPIC;
            case "groq":
                return KEY_GROQ;
            case "perplexity":
                return KEY_PERPLEXITY;
            default:
                return null;
        }
    }
    
    /**
     * Migre les clés depuis SecureConfig/SharedPreferences existants
     */
    private void migrateFromLegacy() {
        if (prefs.getBoolean(MIGRATION_FLAG, false)) {
            Log.d(TAG, "Migration déjà effectuée");
            return;
        }
        
        Log.i(TAG, "🔄 Démarrage migration depuis SecureConfig/SharedPreferences...");
        
        try {
            // Migrer Hugging Face depuis SecureConfig
            SecureConfig secureConfig = new SecureConfig(context);
            String hfKey = secureConfig.getHuggingFaceApiKey();
            if (hfKey != null && !hfKey.trim().isEmpty()) {
                setApiKey("huggingface", hfKey);
                Log.d(TAG, "✅ Hugging Face migré");
            }
            
            // Migrer Ollama Cloud depuis SecureConfig
            String ollamaKey = secureConfig.getOllamaCloudApiKey();
            if (ollamaKey != null && !ollamaKey.trim().isEmpty()) {
                setApiKey("ollama", ollamaKey);
                Log.d(TAG, "✅ Ollama Cloud migré");
            }
            
            // Migrer autres clés depuis SharedPreferences
            SharedPreferences legacyPrefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
            
            String openaiKey = legacyPrefs.getString("openai_api_key", null);
            if (openaiKey != null && !openaiKey.trim().isEmpty()) {
                setApiKey("openai", openaiKey);
                Log.d(TAG, "✅ OpenAI migré");
            }
            
            String anthropicKey = legacyPrefs.getString("anthropic_api_key", null);
            if (anthropicKey != null && !anthropicKey.trim().isEmpty()) {
                setApiKey("anthropic", anthropicKey);
                Log.d(TAG, "✅ Anthropic migré");
            }
            
            // Marquer migration terminée
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply();
            Log.i(TAG, "✅ Migration terminée");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur migration", e);
            // Marquer quand même pour éviter boucle
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply();
        }
    }
    
    /**
     * Liste tous les providers avec clés configurées
     */
    public String[] getConfiguredProviders() {
        java.util.List<String> providers = new java.util.ArrayList<>();
        String[] allProviders = {"huggingface", "ollama", "openai", "anthropic", "groq", "perplexity"};
        
        for (String provider : allProviders) {
            if (hasApiKey(provider)) {
                providers.add(provider);
            }
        }
        
        return providers.toArray(new String[0]);
    }
    
    /**
     * Obtient un résumé de toutes les clés configurées (pour diagnostics)
     */
    public String getKeyringSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("🔐 KEYRING STATUS\n");
        summary.append("═══════════════════════════════\n\n");
        
        String[] allProviders = {"huggingface", "ollama", "openai", "anthropic", "groq", "perplexity"};
        String[] providerNames = {"Hugging Face", "Ollama Cloud", "OpenAI", "Anthropic", "Groq", "Perplexity"};
        
        int configuredCount = 0;
        for (int i = 0; i < allProviders.length; i++) {
            String provider = allProviders[i];
            String name = providerNames[i];
            boolean hasKey = hasApiKey(provider);
            
            if (hasKey) {
                String key = getApiKey(provider);
                summary.append("✅ ").append(name).append(": ");
                summary.append(key != null ? key.length() + " chars" : "null");
                summary.append("\n");
                configuredCount++;
            } else {
                summary.append("❌ ").append(name).append(": Non configuré\n");
            }
        }
        
        summary.append("\n═══════════════════════════════\n");
        summary.append("Total: ").append(configuredCount).append("/").append(allProviders.length).append(" configurés\n");
        summary.append("Keystore: ").append(useKeystore ? "✅ Actif" : "❌ Fallback");
        
        return summary.toString();
    }
    
    /**
     * Exporte toutes les clés (pour backup - ⚠️ SÉCURITÉ: à utiliser avec précaution)
     * Retourne un JSON avec les clés (chiffrées ou en clair selon le paramètre)
     */
    public String exportKeys(boolean encrypted) {
        try {
            org.json.JSONObject export = new org.json.JSONObject();
            export.put("version", "1.0");
            export.put("timestamp", System.currentTimeMillis());
            export.put("encrypted", encrypted);
            
            org.json.JSONObject keys = new org.json.JSONObject();
            String[] allProviders = {"huggingface", "ollama", "openai", "anthropic", "groq", "perplexity"};
            
            for (String provider : allProviders) {
                String key = getApiKey(provider);
                if (key != null) {
                    if (encrypted) {
                        // Exporter chiffré (utiliser encrypt())
                        keys.put(provider, encrypt(key));
                    } else {
                        // ⚠️ EXPORT EN CLAIR - À UTILISER AVEC PRÉCAUTION
                        keys.put(provider, key);
                    }
                }
            }
            
            export.put("keys", keys);
            return export.toString(2); // Pretty print
        } catch (Exception e) {
            Log.e(TAG, "Erreur export keys", e);
            return null;
        }
    }
    
    /**
     * Importe des clés depuis un JSON exporté
     */
    public boolean importKeys(String jsonExport) {
        try {
            org.json.JSONObject export = new org.json.JSONObject(jsonExport);
            boolean encrypted = export.optBoolean("encrypted", false);
            org.json.JSONObject keys = export.getJSONObject("keys");
            
            java.util.Iterator<String> iterator = keys.keys();
            int imported = 0;
            
            while (iterator.hasNext()) {
                String provider = iterator.next();
                String keyValue = keys.getString(provider);
                
                if (encrypted) {
                    // Déchiffrer avant d'importer
                    String decrypted = decrypt(keyValue);
                    setApiKey(provider, decrypted);
                } else {
                    // Importer directement
                    setApiKey(provider, keyValue);
                }
                imported++;
            }
            
            Log.i(TAG, "✅ " + imported + " clés importées");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur import keys", e);
            return false;
        }
    }
    
    /**
     * Vérifie l'intégrité du keyring (teste le chiffrement/déchiffrement)
     */
    public boolean verifyIntegrity() {
        try {
            String testKey = "test_integrity_" + System.currentTimeMillis();
            String testProvider = "test_provider";
            
            // Sauvegarder
            setApiKey(testProvider, testKey);
            
            // Récupérer
            String retrieved = getApiKey(testProvider);
            
            // Vérifier
            boolean valid = testKey.equals(retrieved);
            
            // Nettoyer
            clearApiKey(testProvider);
            
            Log.d(TAG, "Vérification intégrité keyring: " + (valid ? "✅ OK" : "❌ ÉCHEC"));
            return valid;
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur vérification intégrité", e);
            return false;
        }
    }
}

