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
         * @param message Le contenu du message
         * @param messageType Le type du message (USER_INPUT, AI_RESPONSE, etc.)
         * @param source La source du message (HOTWORD, SYSTEM, etc.) - optionnel
         */
        handleHotwordMessage(message, messageType, source) {
            // ⭐ MODIFIÉ : Déléguer directement à chatBridge qui gère maintenant le source
            // chatBridge détectera automatiquement si source='SYSTEM' et n'ajoutera pas de préfixe
            if (this.chatBridge) {
                this.chatBridge.handleKittMessage(message, messageType, source);
            } else {
                // Fallback si chatBridge n'est pas disponible
                this.chatUI.showSecureMessage('user', message);
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

