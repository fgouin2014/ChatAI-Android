/**
 * chat-hotword.js - Gestion des messages hotword dans Chat
 * 
 * ✅ NOUVEAU Phase 4
 * 
 * Responsabilités :
 * - Détection et affichage des messages hotword
 * - Indication visuelle [🔊 Hotword] dans historique
 */

(function() {
    'use strict';

    class ChatHotword {
        constructor(chatBridge, chatUI) {
            this.chatBridge = chatBridge;
            this.chatUI = chatUI;
        }

        /**
         * Gère un message reçu du hotword (via BidirectionalBridge)
         */
        handleHotwordMessage(message, messageType) {
            // ✅ Détecter si message vient du hotword
            const isFromHotword = messageType.includes("hotword") || 
                                 (message && message.includes("[Hotword]")) ||
                                 (messageType === "USER_INPUT" && this.isHotwordSource(message));
            
            if (isFromHotword) {
                // ✅ Afficher avec indicateur hotword
                const userMessage = `[🔊 Hotword] ${message}`;
                this.chatUI.showSecureMessage('user', userMessage);
            } else {
                // Message normal KITT → déléguer à chatBridge
                this.chatBridge.handleKittMessage(message, messageType);
            }
        }

        /**
         * Vérifie si le message provient du hotword (via metadata)
         */
        isHotwordSource(message) {
            // Le hotword envoie des messages via BackgroundService
            // On peut détecter via le format ou metadata
            // Pour l'instant, on se base sur le fait que c'est un USER_INPUT depuis KITT_VOICE
            // (le hotword utilise KITT_VOICE comme source)
            return false; // Sera déterminé par le messageType et source dans BidirectionalBridge
        }
    }

    // Export global
    window.ChatHotword = ChatHotword;
    console.log('✅ ChatHotword chargé');
})();

