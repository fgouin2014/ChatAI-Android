/**
 * chat-messaging.js - Gestion des messages et IA
 * 
 * Responsabilités :
 * - Envoi de messages (sendMessage)
 * - Queue de traitement (processRequestQueue)
 * - Communication avec IA (processWithThinking, HuggingFace)
 * - Historique de conversation
 */

(function() {
    'use strict';

    class ChatMessaging {
        constructor(androidInterface, chatUI, chatBridge) {
            this.androidInterface = androidInterface;
            this.chatUI = chatUI;
            this.chatBridge = chatBridge; // ✅ Phase 2
            this.requestQueue = [];
            this.isProcessing = false;
            this.conversationHistory = [];
            this.messageCache = new Map();
        }

        /**
         * Envoie un message (appelé depuis chat-core.js)
         */
        async sendMessage(text) {
            if (!window.ChatUtils.validateInput(text)) {
                this.chatUI.showSecureMessage('ai', 'Message invalide ou trop long. Veuillez réessayer.');
                return;
            }
            
            const message = window.ChatUtils.sanitizeInput(text);
            
            // Ajouter à l'historique
            this.conversationHistory.push({
                sender: 'user',
                text: message,
                timestamp: Date.now()
            });
            
            this.chatUI.showSecureMessage('user', message);
            if (this.chatUI.messageInput) {
                this.chatUI.messageInput.value = '';
                this.chatUI.adjustTextareaHeight();
            }
            this.chatUI.showTypingIndicator();
            this.chatUI.toggleInput(false);

            // ✅ Phase 2 : Envoyer à KITT via BidirectionalBridge
            if (this.chatBridge) {
                this.chatBridge.sendToKitt(message, "USER_INPUT");
            }

            // Ajouter à la queue de traitement
            this.requestQueue.push(message);
            this.processRequestQueue();
        }

        /**
         * Traitement de la queue de requêtes
         */
        async processRequestQueue() {
            if (this.isProcessing || this.requestQueue.length === 0) return;
            
            this.isProcessing = true;
            const message = this.requestQueue.shift();

            try {
                // Essayer le service Android avec thinking mode
                if (this.androidInterface?.processWithThinking) {
                    const personality = window.secureChatApp?.personality || 'casual';
                    const enableThinking = this.androidInterface.getThinkingModeEnabled?.() ?? true;
                    this.androidInterface.processWithThinking(message, personality, enableThinking);
                }
                // Essayer le service temps réel
                else if (this.androidInterface?.processAIRequestRealtime) {
                    const enhancedMessage = this.enhanceMessageWithPersonality(message);
                    const personality = window.secureChatApp?.personality || 'casual';
                    this.androidInterface.processAIRequestRealtime(enhancedMessage, personality);
                }
                // Fallback API
                else {
                    const enhancedMessage = this.enhanceMessageWithPersonality(message);
                    const response = await this.queryHuggingFaceSecure(enhancedMessage);
                    this.chatUI.showSecureMessage('ai', response);
                }
            } catch (error) {
                console.error('Erreur traitement message:', error);
                this.chatUI.showSecureMessage('ai', 'Oups ! Une erreur s\'est produite: ' + window.ChatUtils.sanitizeInput(error.message));
            } finally {
                this.chatUI.hideTypingIndicator();
                this.chatUI.toggleInput(true);
                this.isProcessing = false;
                
                // Traiter le message suivant dans la queue
                if (this.requestQueue.length > 0) {
                    setTimeout(() => this.processRequestQueue(), 100);
                }
            }
        }

        /**
         * Amélioration du message avec contexte de personnalité
         */
        enhanceMessageWithPersonality(message) {
            const personality = window.secureChatApp?.personality || 'casual';
            const language = window.secureChatApp?.language || 'fr';
            
            const personalities = {
                casual: {
                    fr: "Tu es un assistant IA décontracté et sympa. Tu utilises un langage familier, des emojis, et tu es très détendu dans tes réponses.",
                    en: "You're a casual and cool AI assistant. You use informal language, emojis, and you're very laid-back in your responses."
                },
                friendly: {
                    fr: "Tu es un assistant IA très amical et chaleureux. Tu es toujours positif, encourageant et bienveillant.",
                    en: "You're a very friendly and warm AI assistant. You're always positive, encouraging and kind."
                },
                professional: {
                    fr: "Tu es un assistant IA professionnel et efficace. Tes réponses sont précises, structurées et formelles.",
                    en: "You're a professional and efficient AI assistant. Your responses are precise, structured and formal."
                },
                creative: {
                    fr: "Tu es un assistant IA créatif et imaginatif. Tu aimes les métaphores, les histoires et les idées originales.",
                    en: "You're a creative and imaginative AI assistant. You love metaphors, stories and original ideas."
                },
                funny: {
                    fr: "Tu es un assistant IA drôle et plein d'humour. Tu fais des blagues, des jeux de mots et tu es très divertissant.",
                    en: "You're a funny AI assistant full of humor. You make jokes, puns and you're very entertaining."
                }
            };
            
            const personalityPrompt = personalities[personality]?.[language] || personalities.casual.fr;
            return `${personalityPrompt}\n\nQuestion: ${message}`;
        }

        /**
         * Requête HuggingFace (fallback)
         */
        async queryHuggingFaceSecure(message, retryCount = 0) {
            const maxRetries = 2;
            const apiUrl = window.ChatUtils.getApiUrl('/api/chat');
            
            try {
                const response = await fetch(apiUrl, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({
                        message: message,
                        model: window.secureChatApp?.currentModel || 'microsoft/DialoGPT-medium'
                    })
                });

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }

                const data = await response.json();
                return data.response || this.getDefaultResponse();
            } catch (error) {
                console.error('Erreur requête HuggingFace:', error);
                if (retryCount < maxRetries) {
                    await new Promise(resolve => setTimeout(resolve, 1000 * (retryCount + 1)));
                    return this.queryHuggingFaceSecure(message, retryCount + 1);
                }
                return this.getDefaultResponse();
            }
        }

        /**
         * Réponse par défaut en cas d'erreur
         */
        getDefaultResponse() {
            const personality = window.secureChatApp?.personality || 'casual';
            const language = window.secureChatApp?.language || 'fr';
            
            const messages = {
                casual: {
                    fr: "Désolé, j'ai un petit problème technique. Peux-tu réessayer ? 😅",
                    en: "Sorry, I'm having a little technical issue. Can you try again? 😅"
                },
                friendly: {
                    fr: "Je rencontre une petite difficulté technique. Pourrais-tu réessayer s'il te plaît ?",
                    en: "I'm experiencing a small technical difficulty. Could you please try again?"
                },
                professional: {
                    fr: "Une erreur technique s'est produite. Veuillez réessayer.",
                    en: "A technical error occurred. Please try again."
                },
                creative: {
                    fr: "Oups ! Mon cerveau numérique a fait une petite pause. Peux-tu réessayer ?",
                    en: "Oops! My digital brain took a little break. Can you try again?"
                },
                funny: {
                    fr: "Erreur 404 : Mon cerveau n'a pas été trouvé ! 😂 Réessaye !",
                    en: "Error 404: My brain was not found! 😂 Try again!"
                }
            };
            
            return messages[personality]?.[language] || messages.casual.fr;
        }

        /**
         * Sauvegarde de la conversation dans localStorage
         */
        saveConversationToLocalStorage() {
            try {
                localStorage.setItem('chat_conversation', JSON.stringify(this.conversationHistory));
            } catch (error) {
                console.error('Erreur sauvegarde localStorage:', error);
            }
        }

        /**
         * Chargement de l'historique depuis localStorage
         */
        loadConversationHistory() {
            try {
                const saved = localStorage.getItem('chat_conversation');
                if (saved) {
                    this.conversationHistory = JSON.parse(saved);
                    // Afficher les messages sauvegardés (optionnel)
                    // this.conversationHistory.forEach(msg => {
                    //     this.chatUI.showSecureMessage(msg.sender, msg.text, false);
                    // });
                }
            } catch (error) {
                console.error('Erreur chargement historique:', error);
                this.conversationHistory = [];
            }
        }

        /**
         * Sauvegarde de la conversation dans Android
         */
        saveConversationToApp() {
            if (this.androidInterface?.saveConversation && this.chatUI.chatMessages) {
                const messages = Array.from(this.chatUI.chatMessages.querySelectorAll('.message')).map(msg => ({
                    sender: msg.classList.contains('user') ? 'user' : 'ai',
                    text: msg.querySelector('.message-bubble')?.textContent?.replace(/[🔊📋]/g, '')?.trim() || '',
                    timestamp: Date.now()
                }));
                this.androidInterface.saveConversation(JSON.stringify(messages));
            }
        }

        /**
         * Sauvegarder dans historique (appelé depuis chat-ui.js)
         */
        saveToHistory(sender, text) {
            this.conversationHistory.push({
                sender: sender,
                text: text,
                timestamp: Date.now()
            });
            this.saveConversationToLocalStorage();
        }
    }

    // Export global
    window.ChatMessaging = ChatMessaging;
    console.log('✅ ChatMessaging chargé');
})();

