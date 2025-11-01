package com.chatai;

import java.util.regex.Pattern;

/**
 * Utilitaires de sécurité pour la validation et la sanitisation
 */
public class SecurityUtils {
    
    // Patterns de validation
    private static final Pattern HTML_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern XSS_PATTERN = Pattern.compile("javascript:|data:|vbscript:", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("('|(\\-\\-)|(;)|(\\|)|(\\*)|(%))", Pattern.CASE_INSENSITIVE);
    
    /**
     * Sanitise le texte d'entrée pour éviter les injections XSS
     */
    public static String sanitizeInput(String input) {
        if (input == null) return "";
        
        // Échapper les caractères HTML dangereux
        input = input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;")
                    .replace("/", "&#x2F;");
        
        // Supprimer les balises script
        input = SCRIPT_PATTERN.matcher(input).replaceAll("");
        
        // Supprimer les protocoles dangereux
        input = XSS_PATTERN.matcher(input).replaceAll("");
        
        return input.trim();
    }
    
    /**
     * Valide une entrée utilisateur
     */
    public static boolean isValidInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }
        
        // Vérifier la longueur
        if (input.length() > 1000) {
            return false;
        }
        
        // Vérifier les patterns dangereux
        if (SCRIPT_PATTERN.matcher(input).find()) {
            return false;
        }
        
        if (XSS_PATTERN.matcher(input).find()) {
            return false;
        }
        
        if (SQL_INJECTION_PATTERN.matcher(input).find()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Valide un nom de fichier
     */
    public static boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        // Caractères interdits
        String forbiddenChars = "\\/:*?\"<>|";
        for (char c : forbiddenChars.toCharArray()) {
            if (fileName.contains(String.valueOf(c))) {
                return false;
            }
        }
        
        // Longueur maximale
        if (fileName.length() > 255) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Nettoie un nom de fichier
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) return "";
        
        // Remplacer les caractères dangereux
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        
        // Limiter la longueur
        if (fileName.length() > 255) {
            fileName = fileName.substring(0, 255);
        }
        
        return fileName.trim();
    }
    
    /**
     * Valide une URL
     */
    public static boolean isValidUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        // Protocoles autorisés
        return url.startsWith("https://") || 
               url.startsWith("http://localhost") ||
               url.startsWith("file://");
    }
    
    /**
     * Génère un hash simple pour les logs
     */
    public static String hashForLogging(String input) {
        if (input == null) return "null";
        
        // Hash simple pour les logs (ne pas utiliser pour la sécurité)
        int hash = input.hashCode();
        return "hash_" + Math.abs(hash);
    }
}
