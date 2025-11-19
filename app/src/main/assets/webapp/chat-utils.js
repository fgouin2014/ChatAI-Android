/**
 * chat-utils.js - Utilitaires partagés pour ChatAI
 * 
 * Fonctions utilitaires utilisées par tous les modules :
 * - Throttle / Debounce
 * - Sanitization / Validation
 * - API Helpers
 * - DOM Helpers
 */

(function() {
    'use strict';

    /**
     * Throttle function pour optimisation (limite exécution)
     */
    function throttle(func, delay) {
        let lastCall = 0;
        return function(...args) {
            const now = Date.now();
            if (now - lastCall >= delay) {
                lastCall = now;
                return func.apply(this, args);
            }
        };
    }

    /**
     * Debounce function pour optimisation (retarde exécution)
     */
    function debounce(func, delay) {
        let timeoutId;
        return function(...args) {
            clearTimeout(timeoutId);
            timeoutId = setTimeout(() => func.apply(this, args), delay);
        };
    }

    /**
     * Sécurisation des entrées utilisateur (XSS protection)
     */
    function sanitizeInput(input) {
        if (!input) return '';
        
        const entityMap = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#x27;',
            '/': '&#x2F;',
            '`': '&#x60;',
            '=': '&#x3D;'
        };
        
        return String(input).replace(/[&<>"'`=/]/g, s => entityMap[s]).trim();
    }

    /**
     * Validation renforcée des entrées (sécurité)
     */
    function validateInput(input) {
        if (!input || input.trim().length === 0) return false;
        if (input.length > 10000) return false; // Limite de sécurité (augmentée pour messages longs)
        
        const dangerousPatterns = [
            /<script[^>]*>.*?<\/script>/gi,
            /javascript:/gi,
            /data:text\/html/gi,
            /vbscript:/gi,
            /on\w+\s*=/gi,
            /<iframe/gi,
            /<embed/gi,
            /<object/gi
        ];
        
        return !dangerousPatterns.some(pattern => pattern.test(input));
    }

    /**
     * Échapper une chaîne pour utilisation dans JavaScript (JSON.stringify)
     * Utilisé pour passer des données depuis Java vers JavaScript via evaluateJavascript
     */
    function escapeForJavaScript(str) {
        if (!str) return '""';
        return JSON.stringify(String(str));
    }

    /**
     * Obtenir l'URL de l'API (serveur local ou distant)
     */
    function getApiUrl(path) {
        const origin = window.location?.origin || '';
        if (origin && origin.startsWith('http')) {
            return origin.replace(/\/$/, '') + path;
        }
        // Fallback : serveur local par défaut
        return 'http://127.0.0.1:8080' + path;
    }

    /**
     * Ajouter un event listener de manière sécurisée
     */
    function addListener(element, event, handler) {
        if (!element) return;
        try {
            element.addEventListener(event, handler);
        } catch (error) {
            console.error(`Erreur lors de l'ajout du listener ${event}:`, error);
        }
    }

    /**
     * Export global pour utilisation dans autres modules
     */
    window.ChatUtils = {
        throttle,
        debounce,
        sanitizeInput,
        validateInput,
        escapeForJavaScript,
        getApiUrl,
        addListener
    };

    console.log('✅ ChatUtils chargé');
})();

