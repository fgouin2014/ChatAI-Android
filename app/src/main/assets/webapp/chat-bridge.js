/**
 * chat-bridge.js - BidirectionalBridge Chat ↔ KITT
 * 
 * ✅ NOUVEAU Phase 2
 * 
 * Responsabilités :
 * - Communication bidirectionnelle Chat ↔ KITT via BidirectionalBridge
 * - Émission de messages TextInput vers KITT
 * - Écoute des messages depuis KITT
 */

(function() {
    'use strict';

    class ChatBridge {
        constructor(androidInterface, chatUI, chatMessaging) {
            this.androidInterface = androidInterface;
            this.chatUI = chatUI;
            this.chatMessaging = chatMessaging;
            this.isInitialized = false;
            this.pollInterval = null;
        }

        /**
         * Initialise l'écoute des messages depuis KITT
         */
        initialize() {
            if (this.isInitialized) return;
            
            // Option 1: Callback via window (meilleur - appelé depuis WebAppInterface)
            // ⭐ MODIFIÉ : Le callback reçoit 3 paramètres (message, messageType, source)
            window.onKittMessageReceived = (message, messageType, source) => {
                this.handleKittMessage(message, messageType, source);
            };
            
            // Option 2: Polling (fallback si callback non disponible)
            if (this.androidInterface?.getKittMessage) {
                this.setupPolling();
            }
            
            this.isInitialized = true;
            console.log('✅ ChatBridge initialisé');
        }

        /**
         * Setup polling pour récupérer messages KITT (fallback)
         */
        setupPolling() {
            this.pollInterval = setInterval(() => {
                if (this.androidInterface?.getKittMessage) {
                    const message = this.androidInterface.getKittMessage();
                    if (message) {
                        this.handleKittMessage(message.message, message.messageType, message.source || null);
                    }
                }
            }, 500); // Vérifier toutes les 500ms
        }

        /**
         * Envoie un message à KITT via BidirectionalBridge
         */
        sendToKitt(message, messageType = "USER_INPUT") {
            if (!this.androidInterface?.sendChatAIToKitt) {
                console.warn("sendChatAIToKitt non disponible");
                return;
            }
            
            try {
                this.androidInterface.sendChatAIToKitt(message, messageType);
                console.log("Message envoyé à KITT via BidirectionalBridge:", message);
            } catch (e) {
                console.warn("Erreur envoi message à KITT:", e);
            }
        }

        /**
         * Gère un message reçu de KITT
         * @param message Le contenu du message
         * @param messageType Le type du message (USER_INPUT, AI_RESPONSE, etc.)
         * @param source La source du message (HOTWORD, KITT_VOICE, etc.) - optionnel
         */
        handleKittMessage(message, messageType, source) {
            console.log("Message reçu de KITT:", message, messageType, source);
            
            if (!message || !messageType) return;
            
            switch(messageType) {
                case "USER_INPUT":
                    // ⭐ MODIFIÉ : Traiter tous les messages utilisateur de la même manière (texte ou STT)
                    // Si source est "HOTWORD" ou "SYSTEM", c'est un message STT venant du hotword
                    // Le hotword traite déjà avec l'IA dans BackgroundService, on affiche juste le message
                    if (source === "HOTWORD" || source === "SYSTEM") {
                        // Message STT (hotword) → afficher comme un message texte normal
                        // ⭐ NE PAS traiter avec l'IA ici car BackgroundService le fait déjà
                        this.chatUI.showSecureMessage('user', message);
                    } else {
                        // Messages depuis KITT vocal (interface KITT) → préfixe [KITT] et traiter avec l'IA
                        this.chatUI.showSecureMessage('user', `[KITT] ${message}`);
                        // Traiter avec l'IA pour les messages depuis l'interface KITT
                        if (this.chatMessaging && this.chatMessaging.requestQueue) {
                            this.chatUI.showTypingIndicator();
                            this.chatUI.toggleInput(false);
                            this.chatMessaging.requestQueue.push(message);
                            this.chatMessaging.processRequestQueue();
                        }
                    }
                    break;
                case "AI_RESPONSE":
                    // KITT a reçu une réponse → afficher dans Chat
                    this.chatUI.showSecureMessage('ai', message);
                    break;
                case "SYSTEM_STATUS":
                    // Statut système (ex: "KITT activé", "Écoute en cours")
                    // ⭐ MODIFIÉ : Ignorer les messages SYSTEM_STATUS - pas besoin d'afficher dans le chat
                    console.log("SYSTEM_STATUS ignoré:", message);
                    break;
                case "THINKING_START":
                case "THINKING_CHUNK":
                case "THINKING_END":
                case "RESPONSE_START":
                case "RESPONSE_CHUNK":
                case "RESPONSE_END":
                    // Ces types sont déjà gérés par displayThinkingChunk() via WebAppInterface
                    // Pas besoin de dupliquer ici
                    break;
                case "ERROR":
                    // Erreur depuis KITT
                    this.chatUI.showSecureMessage('ai', `[Erreur KITT] ${message}`);
                    break;
                default:
                    console.log("Type de message KITT non géré:", messageType);
            }
        }

        /**
         * Nettoie les ressources (arrêt polling, etc.)
         */
        destroy() {
            if (this.pollInterval) {
                clearInterval(this.pollInterval);
                this.pollInterval = null;
            }
            window.onKittMessageReceived = null;
            this.isInitialized = false;
        }
    }

    // Export global
    window.ChatBridge = ChatBridge;
    console.log('✅ ChatBridge chargé');
})();

