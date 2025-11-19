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
 * Gestionnaire de configuration sécurisée avec Android Keystore System
 * Utilise AES-256/GCM pour un chiffrement authentifié
 * 
 * Améliorations de sécurité :
 * - Clé stockée dans Android Keystore (matériel sécurisé si disponible)
 * - Mode GCM avec authentification (détection de modification)
 * - IV unique par chiffrement (pas de patterns)
 * - Migration automatique depuis l'ancien système
 */
public class SecureConfig {
    private static final String TAG = "SecureConfig";
    private static final String PREFS_NAME = "secure_config";
    private static final String API_TOKEN_KEY = "api_token";
    private static final String KEYSTORE_ALIAS = "ChatAI_SecureKey";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String LEGACY_SECRET_KEY = "ChatAI_SecretKey2024!123456789012"; // Ancien système (migration)
    private static final String MIGRATION_FLAG = "migrated_to_keystore";
    
    private final Context context;
    private final SharedPreferences prefs;
    private SecretKey keystoreKey;
    private boolean useKeystore = false;
    
    public SecureConfig(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeKeystore();
    }
    
    /**
     * Initialise Android Keystore et génère la clé si nécessaire
     * Note: KeyGenParameterSpec (vraiment sécurisé) nécessite Android 6.0+ (API 23)
     */
    private void initializeKeystore() {
        try {
            // KeyGenParameterSpec nécessite Android 6.0+ (API 23)
            // Pour Android < 6.0, on utilise le fallback (clé statique)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Log.w(TAG, "KeyGenParameterSpec non disponible (API < 23), utilisation du fallback");
                return;
            }
            
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            
            // Vérifier si la clé existe déjà
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);
                keystoreKey = secretKeyEntry.getSecretKey();
                useKeystore = true;
                Log.d(TAG, "Clé Keystore existante chargée");
            } else {
                // Générer une nouvelle clé dans le Keystore
                generateKeystoreKey();
                useKeystore = true;
                Log.d(TAG, "Nouvelle clé Keystore générée");
            }
            
            // Migrer les données de l'ancien système si nécessaire
            if (!prefs.getBoolean(MIGRATION_FLAG, false)) {
                migrateLegacyData();
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Erreur initialisation Keystore, utilisation du fallback", e);
            useKeystore = false;
        }
    }
    
    /**
     * Génère une clé AES-256 dans Android Keystore
     * Note: Android Keystore avec KeyGenParameterSpec n'est disponible qu'à partir d'Android 6.0 (API 23)
     */
    private void generateKeystoreKey() throws Exception {
        // Android Keystore avec KeyGenParameterSpec n'est disponible qu'à partir d'Android 6.0
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.w(TAG, "KeyGenParameterSpec non disponible (API < 23), utilisation du fallback");
            return;
        }
        
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        
        // Vérifier si la clé existe déjà
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            KeyStore.SecretKeyEntry secretKeyEntry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);
            keystoreKey = secretKeyEntry.getSecretKey();
            return;
        }
        
        // Générer une nouvelle clé avec KeyGenParameterSpec (Android 6.0+)
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER);
        android.security.keystore.KeyGenParameterSpec.Builder builder =
            new android.security.keystore.KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | 
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256);
        
        keyGenerator.init(builder.build());
        keystoreKey = keyGenerator.generateKey();
    }
    
    /**
     * Migre les données de l'ancien système vers le nouveau
     */
    private void migrateLegacyData() {
        try {
            String legacyToken = prefs.getString(API_TOKEN_KEY, null);
            if (legacyToken != null) {
                // Essayer de déchiffrer avec l'ancien système
                try {
                    String decrypted = decryptLegacy(legacyToken);
                    if (decrypted != null) {
                        // Re-chiffrer avec le nouveau système et sauvegarder
                        setApiToken(decrypted);
                        Log.i(TAG, "Migration réussie depuis l'ancien système");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Impossible de migrer les données legacy", e);
                }
            }
            
            // Marquer la migration comme terminée
            prefs.edit().putBoolean(MIGRATION_FLAG, true).apply();
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la migration", e);
        }
    }
    
    /**
     * Sauvegarde sécurisée du token API
     */
    public void setApiToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            clearApiToken();
            return;
        }
        
        try {
            String encryptedToken = encrypt(token.trim());
            prefs.edit().putString(API_TOKEN_KEY, encryptedToken).apply();
            Log.d(TAG, "Token API sauvegardé de manière sécurisée");
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la sauvegarde du token", e);
            throw new RuntimeException("Erreur sauvegarde token", e);
        }
    }
    
    /**
     * Récupération sécurisée du token API
     */
    public String getApiToken() {
        String encryptedToken = prefs.getString(API_TOKEN_KEY, null);
        if (encryptedToken == null) {
            return null;
        }
        
        try {
            return decrypt(encryptedToken);
        } catch (Exception e) {
            Log.w(TAG, "Erreur lors du déchiffrement, tentative avec l'ancien système", e);
            // Essayer avec l'ancien système (migration en cours)
            try {
                return decryptLegacy(encryptedToken);
            } catch (Exception e2) {
                Log.e(TAG, "Impossible de déchiffrer le token", e2);
                return null;
            }
        }
    }
    
    /**
     * Chiffrement avec AES/GCM (mode sécurisé)
     */
    private String encrypt(String plainText) throws Exception {
        if (useKeystore && keystoreKey != null) {
            return encryptWithKeystore(plainText);
        } else {
            return encryptWithFallback(plainText);
        }
    }
    
    /**
     * Chiffrement avec Android Keystore (AES-256/GCM)
     */
    private String encryptWithKeystore(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey);
        
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        
        // Combiner IV + données chiffrées (IV = 12 bytes pour GCM)
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }
    
    /**
     * Déchiffrement avec Android Keystore
     */
    private String decrypt(String encryptedText) throws Exception {
        if (useKeystore && keystoreKey != null) {
            return decryptWithKeystore(encryptedText);
        } else {
            return decryptWithFallback(encryptedText);
        }
    }
    
    /**
     * Déchiffrement avec Android Keystore (AES-256/GCM)
     */
    private String decryptWithKeystore(String encryptedText) throws Exception {
        byte[] combined = Base64.decode(encryptedText, Base64.NO_WRAP);
        
        // Extraire IV (12 premiers bytes) et données chiffrées
        byte[] iv = Arrays.copyOfRange(combined, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv); // 128 bits = tag d'authentification
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey, gcmSpec);
        
        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Fallback : Chiffrement avec clé statique (moins sécurisé, pour compatibilité)
     */
    private String encryptWithFallback(String plainText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(LEGACY_SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }
    
    /**
     * Fallback : Déchiffrement avec clé statique
     */
    private String decryptWithFallback(String encryptedText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(LEGACY_SECRET_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        
        byte[] decoded = Base64.decode(encryptedText, Base64.NO_WRAP);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
    
    /**
     * Déchiffrement avec l'ancien système (pour migration)
     */
    private String decryptLegacy(String encryptedText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(LEGACY_SECRET_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        
        byte[] decoded = Base64.decode(encryptedText, Base64.NO_WRAP);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted);
    }
    
    /**
     * Génère un token temporaire sécurisé
     */
    public String generateTempToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
    
    /**
     * Vérifie si un token est configuré
     */
    public boolean hasApiToken() {
        return getApiToken() != null;
    }
    
    /**
     * Supprime le token (logout)
     */
    public void clearApiToken() {
        prefs.edit().remove(API_TOKEN_KEY).apply();
    }
    
    /**
     * Sauvegarde un paramètre générique
     */
    public void saveSetting(String key, String value) {
        try {
            String encryptedValue = encrypt(value);
            prefs.edit().putString(key, encryptedValue).apply();
        } catch (Exception e) {
            Log.e(TAG, "Erreur sauvegarde setting: " + key, e);
        }
    }
    
    /**
     * Récupère un paramètre générique
     */
    public String getSetting(String key, String defaultValue) {
        String encryptedValue = prefs.getString(key, null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return decrypt(encryptedValue);
        } catch (Exception e) {
            Log.w(TAG, "Erreur déchiffrement setting: " + key, e);
            return defaultValue;
        }
    }
    
    /**
     * Récupère un paramètre entier
     */
    public int getIntSetting(String key, int defaultValue) {
        String value = getSetting(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Sauvegarde un paramètre entier
     */
    public void saveIntSetting(String key, int value) {
        saveSetting(key, String.valueOf(value));
    }
    
    // ========== MÉTHODES SPÉCIFIQUES POUR OLLAMA CLOUD API KEY ==========
    
    private static final String OLLAMA_CLOUD_KEY = "ollama_cloud_api_key";
    private static final String OLLAMA_CLOUD_MIGRATED = "ollama_cloud_migrated";
    
    /**
     * Sauvegarde la clé API Ollama Cloud de manière sécurisée
     */
    public void setOllamaCloudApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Log.d(TAG, "setOllamaCloudApiKey: clé vide, suppression");
            clearOllamaCloudApiKey();
            return;
        }
        
        try {
            String trimmedKey = apiKey.trim();
            Log.d(TAG, "setOllamaCloudApiKey: sauvegarde de la clé (" + trimmedKey.length() + " chars)");
            String encryptedKey = encrypt(trimmedKey);
            prefs.edit().putString(OLLAMA_CLOUD_KEY, encryptedKey).apply();
            
            // Vérifier que la sauvegarde a fonctionné
            String verifyKey = prefs.getString(OLLAMA_CLOUD_KEY, null);
            if (verifyKey != null) {
                Log.i(TAG, "Clé API Ollama Cloud sauvegardée avec succès dans SecureConfig");
            } else {
                Log.e(TAG, "ERREUR: Clé non trouvée après sauvegarde!");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la sauvegarde de la clé Ollama Cloud", e);
            throw new RuntimeException("Erreur sauvegarde clé Ollama Cloud", e);
        }
    }
    
    /**
     * Récupère la clé API Ollama Cloud de manière sécurisée
     * Migre automatiquement depuis SharedPreferences si nécessaire
     */
    public String getOllamaCloudApiKey() {
        // Vérifier si déjà migré
        if (!prefs.getBoolean(OLLAMA_CLOUD_MIGRATED, false)) {
            Log.d(TAG, "Migration Ollama Cloud API key non effectuée, démarrage de la migration...");
            migrateOllamaCloudKey();
        }
        
        String encryptedKey = prefs.getString(OLLAMA_CLOUD_KEY, null);
        if (encryptedKey == null) {
            Log.d(TAG, "Aucune clé Ollama Cloud trouvée dans SecureConfig (SharedPreferences 'secure_config')");
            // Vérifier une dernière fois dans SharedPreferences (au cas où configurée après migration)
            SharedPreferences legacyPrefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
            String legacyKey = legacyPrefs.getString("ollama_cloud_api_key", null);
            Log.d(TAG, "Vérification SharedPreferences 'chatai_ai_config' dans SecureConfig: clé trouvée = " + (legacyKey != null && !legacyKey.trim().isEmpty()));
            if (legacyKey != null && !legacyKey.trim().isEmpty()) {
                Log.i(TAG, "Clé Ollama Cloud trouvée dans SharedPreferences (post-migration), migration...");
                setOllamaCloudApiKey(legacyKey);
                return legacyKey.trim();
            }
            Log.d(TAG, "Aucune clé Ollama Cloud trouvée ni dans SecureConfig ni dans SharedPreferences");
            return null;
        }
        
        // Clé chiffrée trouvée, essayer de la déchiffrer
        Log.d(TAG, "Clé Ollama Cloud chiffrée trouvée dans SecureConfig (" + encryptedKey.length() + " chars chiffrés)");
        try {
            String decrypted = decrypt(encryptedKey);
            Log.d(TAG, "Clé Ollama Cloud déchiffrée avec succès (" + decrypted.length() + " chars)");
            return decrypted;
        } catch (Exception e) {
            Log.e(TAG, "ERREUR: Clé Ollama Cloud trouvée mais déchiffrement échoué!", e);
            Log.e(TAG, "Exception type: " + e.getClass().getName() + ", message: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Migre la clé Ollama Cloud depuis SharedPreferences standard vers SecureConfig
     */
    private void migrateOllamaCloudKey() {
        try {
            SharedPreferences legacyPrefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
            String legacyKey = legacyPrefs.getString("ollama_cloud_api_key", null);
            
            Log.d(TAG, "Migration Ollama Cloud: clé trouvée dans SharedPreferences = " + (legacyKey != null && !legacyKey.trim().isEmpty()));
            
            if (legacyKey != null && !legacyKey.trim().isEmpty()) {
                // Migrer vers SecureConfig
                setOllamaCloudApiKey(legacyKey);
                Log.i(TAG, "Clé Ollama Cloud migrée depuis SharedPreferences vers SecureConfig (" + legacyKey.length() + " chars)");
                
                // Vérifier que la migration a fonctionné
                String verifyKey = prefs.getString(OLLAMA_CLOUD_KEY, null);
                if (verifyKey != null) {
                    Log.d(TAG, "Migration vérifiée: clé sauvegardée dans SecureConfig");
                } else {
                    Log.e(TAG, "ERREUR: Migration échouée - clé non trouvée après sauvegarde");
                }
            } else {
                Log.d(TAG, "Aucune clé Ollama Cloud à migrer (SharedPreferences vide)");
            }
            
            // Marquer la migration comme terminée
            prefs.edit().putBoolean(OLLAMA_CLOUD_MIGRATED, true).apply();
            Log.d(TAG, "Migration Ollama Cloud terminée (flag mis à jour)");
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la migration de la clé Ollama Cloud", e);
            // Marquer quand même comme migré pour éviter les boucles
            prefs.edit().putBoolean(OLLAMA_CLOUD_MIGRATED, true).apply();
        }
    }
    
    /**
     * Vérifie si la clé API Ollama Cloud est configurée
     */
    public boolean hasOllamaCloudApiKey() {
        return getOllamaCloudApiKey() != null;
    }
    
    /**
     * Supprime la clé API Ollama Cloud
     */
    public void clearOllamaCloudApiKey() {
        prefs.edit().remove(OLLAMA_CLOUD_KEY).apply();
    }
}
