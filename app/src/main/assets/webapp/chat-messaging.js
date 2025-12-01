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
         * ⭐ REFACTORISÉ: Requête HuggingFace (fallback)
         * Utilise le nouveau endpoint router.huggingface.co (cohérent avec backend Android)
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
         * Chargement de l'historique depuis localStorage et Room DB
         * ⭐ NOUVEAU Phase 1.2: Fusionne localStorage avec Room DB et évite les doublons
         */
        async loadConversationHistory() {
            try {
                // 1. Charger depuis localStorage (historique webapp local)
                const saved = localStorage.getItem('chat_conversation');
                let localHistory = [];
                if (saved) {
                    localHistory = JSON.parse(saved);
                }
                
                // 2. Charger depuis Room DB (toutes les conversations)
                let roomHistory = [];
                if (this.androidInterface?.getConversations) {
                    try {
                        const conversationsJson = this.androidInterface.getConversations(50); // Dernières 50 conversations
                        if (conversationsJson && conversationsJson !== '[]') {
                            const conversations = JSON.parse(conversationsJson);
                            
                            // Convertir conversations Room en format chat (user/ai)
                            // Filtrer seulement celles de la webapp (platform="webapp")
                            const webappConversations = conversations
                                .filter(conv => conv.platform === 'webapp')
                                .map(conv => [
                                    { sender: 'user', text: conv.userMessage, timestamp: conv.timestamp, conversationId: conv.conversationId },
                                    { sender: 'ai', text: conv.aiResponse, timestamp: conv.timestamp, conversationId: conv.conversationId }
                                ])
                                .flat()
                                .sort((a, b) => a.timestamp - b.timestamp); // Trier par timestamp
                            
                            roomHistory = webappConversations;
                        }
                    } catch (error) {
                        console.error('Erreur chargement Room DB:', error);
                    }
                }
                
                // 3. Fusionner les deux historiques en évitant les doublons
                const mergedHistory = [];
                const seenMessages = new Set(); // Pour détecter doublons par timestamp+text
                
                // Ajouter messages localStorage
                localHistory.forEach(msg => {
                    const key = `${msg.timestamp}_${msg.text}`;
                    if (!seenMessages.has(key)) {
                        seenMessages.add(key);
                        mergedHistory.push(msg);
                    }
                });
                
                // Ajouter messages Room DB (éviter doublons)
                roomHistory.forEach(msg => {
                    const key = `${msg.timestamp}_${msg.text}`;
                    // Éviter doublons: même timestamp (±5s) et même texte
                    const isDuplicate = mergedHistory.some(existing => {
                        const timeDiff = Math.abs(existing.timestamp - msg.timestamp);
                        return timeDiff < 5000 && existing.text === msg.text && existing.sender === msg.sender;
                    });
                    
                    if (!isDuplicate && !seenMessages.has(key)) {
                        seenMessages.add(key);
                        mergedHistory.push(msg);
                    }
                });
                
                // 4. Trier par timestamp et stocker
                mergedHistory.sort((a, b) => a.timestamp - b.timestamp);
                this.conversationHistory = mergedHistory;
                
                // 5. Sauvegarder la version fusionnée dans localStorage
                this.saveConversationToLocalStorage();
                
                // 6. Optionnel: Afficher les messages (peut être lourd si beaucoup de messages)
                // Pour éviter de surcharger l'UI, on peut limiter à X derniers messages
                // const recentMessages = mergedHistory.slice(-20); // Derniers 20 messages
                // recentMessages.forEach(msg => {
                //     this.chatUI.showSecureMessage(msg.sender, msg.text, false);
                // });
                
                console.log(`✅ Historique chargé: ${localHistory.length} localStorage + ${roomHistory.length} Room DB = ${mergedHistory.length} fusionnés`);
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
         * ⭐ NOUVEAU Phase 1.1: Sauvegarde également dans Room DB pour messages AI
         */
        saveToHistory(sender, text) {
            this.conversationHistory.push({
                sender: sender,
                text: text,
                timestamp: Date.now()
            });
            this.saveConversationToLocalStorage();
            
            // ⭐ NOUVEAU Phase 1.1: Sauvegarder dans Room DB quand la réponse AI arrive
            if (sender === 'ai' && this.androidInterface?.saveWebappConversation) {
                // Trouver le dernier message utilisateur dans conversationHistory
                let lastUserMessage = null;
                for (let i = this.conversationHistory.length - 2; i >= 0; i--) {
                    if (this.conversationHistory[i].sender === 'user') {
                        lastUserMessage = this.conversationHistory[i].text;
                        break;
                    }
                }
                
                // Si on a trouvé un message utilisateur, sauvegarder la paire dans Room DB
                if (lastUserMessage) {
                    try {
                        const personality = window.secureChatApp?.personality || 'casual';
                        const apiUsed = 'webapp'; // Peut être amélioré pour récupérer la vraie API utilisée
                        const responseTimeMs = 0; // Peut être mesuré si nécessaire
                        const thinkingTrace = null; // Peut être récupéré depuis displayThinkingChunk si nécessaire
                        
                        const success = this.androidInterface.saveWebappConversation(
                            lastUserMessage,
                            text,
                            personality,
                            apiUsed,
                            responseTimeMs,
                            thinkingTrace || ''
                        );
                        
                        if (success) {
                            console.log('✅ Conversation sauvegardée dans Room DB');
                        } else {
                            console.warn('⚠️ Erreur sauvegarde dans Room DB');
                        }
                    } catch (error) {
                        console.error('Erreur sauvegarde Room DB:', error);
                    }
                }
            }
        }
    }

    // Export global
    window.ChatMessaging = ChatMessaging;
    console.log('✅ ChatMessaging chargé');
})();

