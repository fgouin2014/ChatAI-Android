package com.chatai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * Gestionnaire de configuration sécurisée
 * Chiffre les données sensibles avec AES
 */
public class SecureConfig {
    private static final String PREFS_NAME = "secure_config";
    private static final String API_TOKEN_KEY = "api_token";
    private static final String SECRET_KEY = "ChatAI_SecretKey2024!123456789012"; // 32 bytes pour AES-256
    
    private SharedPreferences prefs;
    private SecretKeySpec secretKey;
    
    public SecureConfig(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), "AES");
    }
    
    /**
     * Sauvegarde sécurisée du token API
     */
    public void setApiToken(String token) {
        String encryptedToken = encrypt(token);
        prefs.edit().putString(API_TOKEN_KEY, encryptedToken).apply();
    }
    
    /**
     * Récupération sécurisée du token API
     */
    public String getApiToken() {
        String encryptedToken = prefs.getString(API_TOKEN_KEY, null);
        if (encryptedToken == null) {
            return null;
        }
        return decrypt(encryptedToken);
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
     * Chiffrement AES
     */
    private String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Erreur chiffrement", e);
        }
    }
    
    /**
     * Déchiffrement AES
     */
    private String decrypt(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decoded = Base64.decode(encryptedText, Base64.NO_WRAP);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("Erreur déchiffrement", e);
        }
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
        String encryptedValue = encrypt(value);
        prefs.edit().putString(key, encryptedValue).apply();
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
}
