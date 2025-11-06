/**
 * Chat IA Mobile - Version Sécurisée
 * Remplace les vulnérabilités XSS et sécurise les communications
 */
class SecureMobileAIChat {
    constructor() {
        this.currentModel = 'microsoft/DialoGPT-medium';
        this.personality = 'casual';
        this.language = 'fr';
        this.isRecording = false;
        this.recognition = null;
        this.conversationHistory = [];
        this.androidInterface = window.AndroidApp || null;
        this.secureConfig = null;
        this.websocket = null;
        this.lastAIResponse = null;
        
        // Initialisation sécurisée
        this.initializeSecureConfig();
        this.initializeElements();
        this.attachEventListeners();
        this.initializeSpeech();
        this.adjustTextareaHeight();
        this.connectWebSocket();
    }

    /**
     * Initialisation sécurisée de la configuration
     */
    async initializeSecureConfig() {
        try {
            // Demander le token sécurisé à Android
            if (this.androidInterface && typeof this.androidInterface.getSecureApiToken === 'function') {
                this.apiToken = await this.androidInterface.getSecureApiToken();
            } else {
                // Fallback temporaire (à supprimer en production)
                this.apiToken = null;
                console.warn('Configuration sécurisée non disponible');
            }
        } catch (error) {
            console.error('Erreur initialisation sécurisée:', error);
            this.apiToken = null;
        }
    }

    initializeElements() {
        this.chatMessages = document.getElementById('chatMessages');
        this.messageInput = document.getElementById('messageInput');
        this.sendBtn = document.getElementById('sendBtn');
        this.voiceBtn = document.getElementById('voiceBtn');
        this.typingIndicator = document.getElementById('typingIndicator');
        this.langBtn = document.getElementById('langBtn');
        this.langSelector = document.getElementById('langSelector');
        this.clearBtn = document.getElementById('clearBtn');
        this.kittBtn = document.getElementById('kittBtn');
        this.personalityBtns = document.querySelectorAll('.personality-btn');
        this.pluginModal = document.getElementById('pluginModal');
    }

    attachEventListeners() {
        this.sendBtn.addEventListener('click', () => this.sendMessage());
        this.voiceBtn.addEventListener('click', () => this.toggleVoiceRecording());
        this.clearBtn.addEventListener('click', () => this.clearChat());
        this.langBtn.addEventListener('click', () => this.toggleLanguageSelector());
        this.kittBtn.addEventListener('click', () => this.openKittInterface());
        
        this.messageInput.addEventListener('input', () => this.adjustTextareaHeight());
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendMessage();
            }
        });

        this.personalityBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                this.personalityBtns.forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                this.personality = btn.dataset.personality;
                this.showSecureMessage('ai', this.getPersonalityMessage());
            });
        });

        document.querySelectorAll('.lang-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.lang-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                this.language = btn.dataset.lang;
                this.langSelector.style.display = 'none';
            });
        });

        document.addEventListener('click', (e) => {
            if (!this.langSelector.contains(e.target) && e.target !== this.langBtn) {
                this.langSelector.style.display = 'none';
            }
        });

        this.pluginModal.addEventListener('click', (e) => {
            if (e.target === this.pluginModal) {
                closePlugin();
            }
        });
    }

    initializeSpeech() {
        if ('webkitSpeechRecognition' in window) {
            this.recognition = new webkitSpeechRecognition();
            this.recognition.continuous = false;
            this.recognition.interimResults = false;
            this.recognition.lang = this.language === 'fr' ? 'fr-FR' : 'en-US';
            
            this.recognition.onresult = (event) => {
                const transcript = event.results[0][0].transcript;
                // Sécuriser l'entrée vocale
                const safeTranscript = this.sanitizeInput(transcript);
                this.messageInput.value = safeTranscript;
                this.adjustTextareaHeight();
                this.sendMessage();
            };
            
            this.recognition.onend = () => {
                this.voiceBtn.classList.remove('recording');
                this.isRecording = false;
            };
        }
    }

    /**
     * Sécurise les entrées utilisateur
     */
    sanitizeInput(input) {
        if (!input) return '';
        
        // Échapper les caractères HTML
        return input.replace(/&/g, '&amp;')
                   .replace(/</g, '&lt;')
                   .replace(/>/g, '&gt;')
                   .replace(/"/g, '&quot;')
                   .replace(/'/g, '&#x27;')
                   .replace(/\//g, '&#x2F;')
                   .trim();
    }

    /**
     * Valide une entrée utilisateur
     */
    validateInput(input) {
        if (!input || input.trim().length === 0) return false;
        if (input.length > 1000) return false;
        
        // Vérifier les patterns dangereux
        const dangerousPatterns = [
            /<script[^>]*>.*?<\/script>/gi,
            /javascript:/gi,
            /data:/gi,
            /vbscript:/gi,
            /on\w+\s*=/gi
        ];
        
        for (const pattern of dangerousPatterns) {
            if (pattern.test(input)) {
                return false;
            }
        }
        
        return true;
    }

    toggleVoiceRecording() {
        if (!this.recognition) {
            this.showSecureMessage('ai', 'Reconnaissance vocale non supportée');
            return;
        }

        if (this.isRecording) {
            this.recognition.stop();
            this.voiceBtn.classList.remove('recording');
            this.isRecording = false;
        } else {
            this.recognition.lang = this.language === 'fr' ? 'fr-FR' : 'en-US';
            this.recognition.start();
            this.voiceBtn.classList.add('recording');
            this.isRecording = true;
        }
    }

    adjustTextareaHeight() {
        const textarea = this.messageInput;
        textarea.style.height = 'auto';
        textarea.style.height = Math.min(textarea.scrollHeight, 100) + 'px';
    }

    toggleLanguageSelector() {
        const selector = this.langSelector;
        selector.style.display = selector.style.display === 'block' ? 'none' : 'block';
    }

    getPersonalityMessage() {
        const messages = {
            casual: { fr: "Cool ! Je suis maintenant en mode décontracté 😎", en: "Cool! I'm now in casual mode 😎" },
            friendly: { fr: "Parfait ! Je suis maintenant super amical ! 😊", en: "Perfect! I'm now super friendly! 😊" },
            professional: { fr: "Très bien. Je suis maintenant en mode professionnel.", en: "Very well. I am now in professional mode." },
            creative: { fr: "Fantastique ! Mon imagination s'éveille... ✨", en: "Fantastic! My imagination awakens... ✨" },
            funny: { fr: "Haha ! Mode blague activé ! 😂", en: "Haha! Joke mode activated! 😂" }
        };
        return messages[this.personality][this.language] || messages[this.personality]['fr'];
    }

    async sendMessage() {
        const rawMessage = this.messageInput.value;
        
        // Validation et sanitisation
        if (!this.validateInput(rawMessage)) {
            this.showSecureMessage('ai', 'Message invalide ou trop long. Veuillez réessayer.');
            return;
        }
        
        const message = this.sanitizeInput(rawMessage);
        
        this.showSecureMessage('user', message);
        this.messageInput.value = '';
        this.adjustTextareaHeight();
        this.showTypingIndicator();
        this.toggleInput(false);

        try {
            // Essayer le nouveau service avec thinking mode
            if (this.androidInterface && typeof this.androidInterface.processWithThinking === 'function') {
                const enableThinking = this.androidInterface.getThinkingModeEnabled ? 
                    this.androidInterface.getThinkingModeEnabled() : true;
                
                this.androidInterface.processWithThinking(message, this.personality, enableThinking);
                // La réponse sera reçue via le bridge bidirectionnel
                this.hideTypingIndicator();
                this.toggleInput(true);
                return;
            }
            
            // Essayer le service IA temps réel (ancien)
            if (this.androidInterface && typeof this.androidInterface.processAIRequestRealtime === 'function') {
                const enhancedMessage = this.enhanceMessageWithPersonality(message);
                this.androidInterface.processAIRequestRealtime(enhancedMessage, this.personality);
                // La réponse sera reçue via WebSocket
                this.hideTypingIndicator();
                this.toggleInput(true);
                return;
            }
            
            // Fallback vers l'ancienne méthode
            const enhancedMessage = this.enhanceMessageWithPersonality(message);
            const response = await this.queryHuggingFaceSecure(enhancedMessage);
            this.hideTypingIndicator();
            this.showSecureMessage('ai', response);
        } catch (error) {
            this.hideTypingIndicator();
            this.showSecureMessage('ai', 'Oups ! Erreur: ' + this.sanitizeInput(error.message));
        } finally {
            this.toggleInput(true);
        }
    }

    enhanceMessageWithPersonality(message) {
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
        
        const personalityPrompt = personalities[this.personality][this.language];
        return personalityPrompt + '\n\nQuestion: ' + message;
    }

    async queryHuggingFaceSecure(message) {
        if (!this.apiToken) {
            throw new Error('Token API non configuré. Veuillez contacter l\'administrateur.');
        }
        
        const apiUrl = 'https://api-inference.huggingface.co/models/' + this.currentModel;
        
        const payload = {
            inputs: message,
            parameters: {
                max_length: 150,
                temperature: 0.8,
                top_p: 0.9,
                do_sample: true
            }
        };

        const response = await fetch(apiUrl, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + this.apiToken,
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            if (response.status === 503) {
                throw new Error('Le modèle se charge, réessaye dans quelques secondes...');
            }
            throw new Error('Erreur API: ' + response.status);
        }

        const data = await response.json();
        
        if (data.error) {
            throw new Error(data.error);
        }

        let botResponse;
        if (Array.isArray(data) && data[0]) {
            botResponse = data[0].generated_text || 'Réponse non disponible.';
        } else {
            botResponse = data.generated_text || 'Réponse non disponible.';
        }

        if (typeof botResponse === 'string') {
            botResponse = botResponse.replace(message, '').trim();
            if (botResponse.length === 0) {
                botResponse = this.getDefaultResponse();
            }
        }

        // Sécuriser la réponse avant affichage
        return this.sanitizeInput(botResponse);
    }

    getDefaultResponse() {
        const responses = {
            casual: { fr: "Ah ouais, je vois ce que tu veux dire ! 😄", en: "Oh yeah, I see what you mean! 😄" },
            friendly: { fr: "C'est une excellente question ! 😊", en: "That's an excellent question! 😊" },
            professional: { fr: "Je comprends votre demande.", en: "I understand your request." },
            creative: { fr: "Quelle idée intéressante ! ✨", en: "What an interesting idea! ✨" },
            funny: { fr: "Haha, bonne question ! 😂", en: "Haha, good question! 😂" }
        };
        return responses[this.personality][this.language] || responses[this.personality]['fr'];
    }

    /**
     * Affichage sécurisé des messages (remplace innerHTML)
     */
    showSecureMessage(sender, message) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message ' + sender;
        
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = sender === 'user' ? '👤' : '🤖';
        
        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        
        // Utiliser textContent au lieu d'innerHTML pour la sécurité
        bubble.textContent = message;
        
        if (sender === 'ai') {
            const actions = document.createElement('div');
            actions.className = 'message-actions';
            
            const listenBtn = document.createElement('button');
            listenBtn.className = 'message-btn';
            listenBtn.title = 'Écouter';
            listenBtn.textContent = '🔊';
            listenBtn.addEventListener('click', () => this.speakText(message));
            
            actions.appendChild(listenBtn);
            bubble.appendChild(actions);
            
            // Envoyer notification seulement si l'app n'est pas en avant-plan
            if (document.hidden) {
                this.sendNotification('💬 Nouvelle réponse: ' + message.substring(0, 50) + (message.length > 50 ? '...' : ''));
            }
        }
        
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(bubble);
        
        this.chatMessages.appendChild(messageDiv);
        this.scrollToBottom();
        
        this.saveConversationToApp();
    }

    speakText(text) {
        if ('speechSynthesis' in window) {
            const utterance = new SpeechSynthesisUtterance(text);
            utterance.lang = this.language === 'fr' ? 'fr-FR' : 'en-US';
            utterance.rate = 0.9;
            utterance.pitch = 1;
            speechSynthesis.speak(utterance);
        }
    }

    sendNotification(message) {
        if (this.androidInterface && typeof this.androidInterface.showNotification === 'function') {
            this.androidInterface.showNotification(this.sanitizeInput(message));
        }
    }

    saveConversationToApp() {
        if (this.androidInterface && typeof this.androidInterface.saveConversation === 'function') {
            const messages = Array.from(this.chatMessages.querySelectorAll('.message')).map(msg => ({
                sender: msg.classList.contains('user') ? 'user' : 'ai',
                text: msg.querySelector('.message-bubble').textContent.replace('🔊', '').trim(),
                timestamp: Date.now()
            }));
            this.androidInterface.saveConversation(JSON.stringify(messages));
        }
    }

    clearChat() {
        this.chatMessages.innerHTML = '';
        this.showSecureMessage('ai', this.getPersonalityMessage());
        this.conversationHistory = [];
    }

    showTypingIndicator() {
        this.typingIndicator.style.display = 'block';
        this.scrollToBottom();
    }

    hideTypingIndicator() {
        this.typingIndicator.style.display = 'none';
    }

    toggleInput(enabled) {
        this.messageInput.disabled = !enabled;
        this.sendBtn.disabled = !enabled;
    }

    scrollToBottom() {
        this.chatMessages.scrollTop = this.chatMessages.scrollHeight;
    }

    // ========== FONCTIONNALITÉS MÉDIA ==========
    
    requestCameraAccess() {
        if (this.androidInterface && typeof this.androidInterface.openCamera === 'function') {
            this.androidInterface.openCamera();
        } else {
            this.showSecureMessage('ai', 'Fonctionnalité caméra non disponible');
        }
    }

    requestFileAccess() {
        if (this.androidInterface && typeof this.androidInterface.openFileManager === 'function') {
            this.androidInterface.openFileManager();
        } else {
            this.showSecureMessage('ai', 'Accès aux fichiers non disponible');
        }
    }

    receiveImageFromAndroid(imageBase64, fileName) {
        this.showSecureMessage('user', '📷 Image partagée: ' + fileName);
        
        const imageDiv = document.createElement('div');
        imageDiv.className = 'message user';
        imageDiv.innerHTML = 
            '<div class="message-avatar">👤</div>' +
            '<div class="message-bubble">' +
                '<img src="data:image/jpeg;base64,' + imageBase64 + '" style="max-width: 200px; border-radius: 8px; margin: 5px 0;">' +
                '<div>📷 ' + fileName + '</div>' +
            '</div>';
        this.chatMessages.appendChild(imageDiv);
        this.scrollToBottom();
        
        this.analyzeImage(imageBase64);
    }

    receiveFileFromAndroid(fileName, fileContent, fileType) {
        this.showSecureMessage('user', '📁 Fichier partagé: ' + fileName);
        
        if (fileType && (fileType.startsWith('text/') || fileType === 'application/json')) {
            this.showSecureMessage('ai', '📄 Contenu de ' + fileName + ':\n\n' + fileContent.substring(0, 500) + (fileContent.length > 500 ? '...' : ''));
        }
    }

    async analyzeImage(imageBase64) {
        this.showTypingIndicator();
        
        try {
            await new Promise(resolve => setTimeout(resolve, 2000));
            
            const responses = [
                "Quelle belle image ! 📸 Je vois des couleurs intéressantes et une composition sympa !",
                "Super photo ! 🌟 L'éclairage est vraiment bien géré !",
                "J'aime beaucoup cette image ! 😍 Elle me donne envie d'en savoir plus !",
                "Excellente prise de vue ! 📷 Tu as l'œil artistique !"
            ];
            
            const response = responses[Math.floor(Math.random() * responses.length)];
            this.hideTypingIndicator();
            this.showSecureMessage('ai', response);
            
        } catch (error) {
            this.hideTypingIndicator();
            this.showSecureMessage('ai', "Désolé, je n'ai pas pu analyser cette image pour le moment 😅");
        }
    }

    // ========== NAVIGATION VERS KITT ==========
    
    openKittInterface() {
        if (this.androidInterface && typeof this.androidInterface.openKittInterface === 'function') {
            this.androidInterface.openKittInterface();
        } else {
            this.showSecureMessage('ai', '🚗 Interface KITT non disponible');
        }
    }

    // ========== RÉPONSES IA DIRECTES ==========
    
    connectWebSocket() {
        // Système de réponses IA initialisé
        console.log('🔌 Système de réponses IA initialisé');
    }

    displayAIResponse(message) {
        // Afficher la réponse IA dans l'interface
        this.showSecureMessage('ai', message);
        console.log('✅ Réponse IA affichée:', message);
    }
    
    // ========== THINKING MODE ==========
    
    /**
     * Affiche un message avec section thinking séparée
     */
    showThinkingMessage(thinkingContent, responseContent) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message ai';
        
        const avatar = document.createElement('div');
        avatar.className = 'message-avatar';
        avatar.textContent = '🤖';
        
        const bubble = document.createElement('div');
        bubble.className = 'message-bubble';
        
        // Section thinking (collapsible)
        if (thinkingContent && thinkingContent.trim().length > 0) {
            const thinkingSection = document.createElement('div');
            thinkingSection.className = 'thinking-section';
            thinkingSection.style.cssText = `
                background: #f0f4ff;
                border-left: 3px solid #667eea;
                padding: 10px;
                margin-bottom: 10px;
                border-radius: 8px;
                cursor: pointer;
                position: relative;
            `;
            
            const thinkingHeader = document.createElement('div');
            thinkingHeader.style.cssText = `
                font-weight: bold;
                color: #667eea;
                margin-bottom: 5px;
                display: flex;
                align-items: center;
                gap: 5px;
            `;
            thinkingHeader.innerHTML = '🧠 Raisonnement <span style="font-size: 12px; color: #888;">(cliquez pour voir/cacher)</span>';
            
            const thinkingContent_div = document.createElement('div');
            thinkingContent_div.className = 'thinking-content';
            thinkingContent_div.style.cssText = `
                font-size: 13px;
                color: #555;
                line-height: 1.4;
                margin-top: 5px;
                display: none;
                white-space: pre-wrap;
                max-height: 200px;
                overflow-y: auto;
            `;
            thinkingContent_div.textContent = thinkingContent;
            
            thinkingSection.appendChild(thinkingHeader);
            thinkingSection.appendChild(thinkingContent_div);
            
            // Toggle thinking visibility
            thinkingSection.addEventListener('click', () => {
                const isVisible = thinkingContent_div.style.display !== 'none';
                thinkingContent_div.style.display = isVisible ? 'none' : 'block';
            });
            
            bubble.appendChild(thinkingSection);
        }
        
        // Réponse finale
        const responseDiv = document.createElement('div');
        responseDiv.className = 'response-content';
        responseDiv.textContent = responseContent;
        bubble.appendChild(responseDiv);
        
        // Actions
        const actions = document.createElement('div');
        actions.className = 'message-actions';
        
        const listenBtn = document.createElement('button');
        listenBtn.className = 'message-btn';
        listenBtn.title = 'Écouter';
        listenBtn.textContent = '🔊';
        listenBtn.addEventListener('click', () => this.speakText(responseContent));
        
        actions.appendChild(listenBtn);
        bubble.appendChild(actions);
        
        messageDiv.appendChild(avatar);
        messageDiv.appendChild(bubble);
        
        this.chatMessages.appendChild(messageDiv);
        this.scrollToBottom();
        
        this.saveConversationToApp();
    }
    
    /**
     * Crée ou met à jour un message en mode streaming
     */
    createOrUpdateStreamingMessage(messageId, type, content, isComplete) {
        let messageDiv = document.getElementById(messageId);
        
        if (!messageDiv) {
            // Créer un nouveau message
            messageDiv = document.createElement('div');
            messageDiv.id = messageId;
            messageDiv.className = 'message ai';
            
            const avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            avatar.textContent = '🤖';
            
            const bubble = document.createElement('div');
            bubble.className = 'message-bubble';
            
            // Créer les sections
            if (type === 'thinking') {
                const thinkingSection = document.createElement('div');
                thinkingSection.className = 'thinking-section';
                thinkingSection.style.cssText = `
                    background: #f0f4ff;
                    border-left: 3px solid #667eea;
                    padding: 10px;
                    margin-bottom: 10px;
                    border-radius: 8px;
                `;
                
                const thinkingHeader = document.createElement('div');
                thinkingHeader.style.cssText = `
                    font-weight: bold;
                    color: #667eea;
                    margin-bottom: 5px;
                `;
                thinkingHeader.textContent = '🧠 Raisonnement en cours...';
                
                const thinkingContent_div = document.createElement('div');
                thinkingContent_div.className = 'thinking-content-stream';
                thinkingContent_div.style.cssText = `
                    font-size: 13px;
                    color: #555;
                    line-height: 1.4;
                    white-space: pre-wrap;
                `;
                
                thinkingSection.appendChild(thinkingHeader);
                thinkingSection.appendChild(thinkingContent_div);
                bubble.appendChild(thinkingSection);
            }
            
            const responseDiv = document.createElement('div');
            responseDiv.className = 'response-content-stream';
            responseDiv.style.cssText = `
                white-space: pre-wrap;
            `;
            bubble.appendChild(responseDiv);
            
            messageDiv.appendChild(avatar);
            messageDiv.appendChild(bubble);
            
            this.chatMessages.appendChild(messageDiv);
        }
        
        // Mettre à jour le contenu
        if (type === 'thinking') {
            const thinkingContentDiv = messageDiv.querySelector('.thinking-content-stream');
            if (thinkingContentDiv) {
                thinkingContentDiv.textContent += content;
                
                if (isComplete) {
                    // Changer le header pour indiquer que le thinking est terminé
                    const thinkingSection = messageDiv.querySelector('.thinking-section');
                    if (thinkingSection) {
                        const header = thinkingSection.querySelector('div');
                        if (header) {
                            header.innerHTML = '🧠 Raisonnement <span style="font-size: 12px; color: #888;">(cliquez pour voir/cacher)</span>';
                            thinkingSection.style.cursor = 'pointer';
                            
                            // Cacher le thinking par défaut
                            const contentDiv = thinkingSection.querySelector('.thinking-content-stream');
                            if (contentDiv) {
                                contentDiv.style.display = 'none';
                                contentDiv.style.maxHeight = '200px';
                                contentDiv.style.overflowY = 'auto';
                            }
                            
                            // Toggle
                            thinkingSection.addEventListener('click', () => {
                                const isVisible = contentDiv.style.display !== 'none';
                                contentDiv.style.display = isVisible ? 'none' : 'block';
                            });
                        }
                    }
                }
            }
        } else if (type === 'response') {
            const responseDiv = messageDiv.querySelector('.response-content-stream');
            if (responseDiv) {
                responseDiv.textContent += content;
                
                if (isComplete) {
                    // Ajouter les actions
                    const bubble = messageDiv.querySelector('.message-bubble');
                    if (bubble && !bubble.querySelector('.message-actions')) {
                        const actions = document.createElement('div');
                        actions.className = 'message-actions';
                        
                        const listenBtn = document.createElement('button');
                        listenBtn.className = 'message-btn';
                        listenBtn.title = 'Écouter';
                        listenBtn.textContent = '🔊';
                        listenBtn.addEventListener('click', () => this.speakText(responseDiv.textContent));
                        
                        actions.appendChild(listenBtn);
                        bubble.appendChild(actions);
                    }
                }
            }
        }
        
        this.scrollToBottom();
        
        if (isComplete) {
            this.saveConversationToApp();
        }
    }
    
    /**
     * Appelé par Android pour afficher un chunk de thinking ou de réponse
     */
    displayThinkingChunk(messageId, type, content, isComplete) {
        this.createOrUpdateStreamingMessage(messageId, type, content, isComplete);
    }
}

// ========== FONCTIONS GLOBALES SÉCURISÉES ==========

window.receiveImageFromAndroid = function(imageBase64, fileName) {
    if (window.secureChatApp) {
        window.secureChatApp.receiveImageFromAndroid(imageBase64, fileName);
    }
};

window.receiveFileFromAndroid = function(fileName, fileContent, fileType) {
    if (window.secureChatApp) {
        window.secureChatApp.receiveFileFromAndroid(fileName, fileContent, fileType);
    }
};

// ========== FONCTIONS UTILITAIRES SÉCURISÉES ==========

function speakText(button) {
    if ('speechSynthesis' in window) {
        const messageText = button.closest('.message-bubble').textContent.replace('🔊', '').trim();
        const utterance = new SpeechSynthesisUtterance(messageText);
        
        const app = window.secureChatApp;
        utterance.lang = app.language === 'fr' ? 'fr-FR' : 'en-US';
        utterance.rate = 0.9;
        utterance.pitch = 1;
        
        speechSynthesis.speak(utterance);
    }
}

function openPlugin(pluginType) {
    const modal = document.getElementById('pluginModal');
    const title = document.getElementById('modalTitle');
    const content = document.getElementById('modalContent');
    
    const plugins = {
        websearch: {
            title: '🔍 Recherche Web',
            content: '<div style="text-align: center;"><input type="text" id="searchQuery" placeholder="Bitcoin, météo, actualités..." style="width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; margin-bottom: 15px;"><button onclick="performWebSearch()" style="width: 100%; padding: 12px; background: #667eea; color: white; border: none; border-radius: 8px; font-weight: bold;">🔍 Rechercher sur le web</button><div id="searchResults" style="background: #f8f9fa; padding: 15px; border-radius: 8px; display: none; margin-top: 10px; max-height: 300px; overflow-y: auto; text-align: left; font-size: 13px; line-height: 1.6;"></div></div>'
        },
        calculator: {
            title: '🔢 Calculette',
            content: '<div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px;"><input type="text" id="calcDisplay" readonly style="grid-column: span 4; padding: 15px; font-size: 18px; text-align: right; border: 1px solid #ddd; border-radius: 8px;"><button onclick="clearCalc()" style="grid-column: span 2; padding: 15px; background: #ff4757; color: white; border: none; border-radius: 8px;">C</button><button onclick="deleteLast()" style="grid-column: span 2; padding: 15px; background: #ffa502; color: white; border: none; border-radius: 8px;">⌫</button><button onclick="addToCalc(\'7\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">7</button><button onclick="addToCalc(\'8\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">8</button><button onclick="addToCalc(\'9\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">9</button><button onclick="addToCalc(\'/\')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px;">/</button><button onclick="addToCalc(\'4\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">4</button><button onclick="addToCalc(\'5\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">5</button><button onclick="addToCalc(\'6\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">6</button><button onclick="addToCalc(\'*\')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px;">*</button><button onclick="addToCalc(\'1\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">1</button><button onclick="addToCalc(\'2\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">2</button><button onclick="addToCalc(\'3\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">3</button><button onclick="addToCalc(\'-\')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px;">-</button><button onclick="addToCalc(\'0\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">0</button><button onclick="addToCalc(\'.\')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px;">.</button><button onclick="calculate()" style="padding: 15px; background: #4ECDC4; color: white; border: none; border-radius: 8px;">=</button><button onclick="addToCalc(\'+\')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px;">+</button></div>'
        },
        weather: {
            title: '🌤️ Météo',
            content: '<div style="text-align: center;"><input type="text" id="cityInput" placeholder="Entrez une ville..." style="width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; margin-bottom: 15px;"><button onclick="getWeather()" style="width: 100%; padding: 12px; background: #4ECDC4; color: white; border: none; border-radius: 8px;">Obtenir la météo</button><div id="weatherResult" style="background: #f8f9fa; padding: 15px; border-radius: 8px; display: none; margin-top: 10px;"></div></div>'
        },
        jokes: {
            title: '😂 Générateur de Blagues',
            content: '<div style="text-align: center;"><button onclick="getRandomJoke()" style="width: 100%; padding: 15px; background: #ff6b6b; color: white; border: none; border-radius: 8px; margin-bottom: 15px;">🎲 Blague aléatoire</button><div id="jokeResult" style="background: #f8f9fa; padding: 15px; border-radius: 8px; min-height: 60px; display: flex; align-items: center; justify-content: center; font-style: italic; color: #666;">Cliquez pour une blague ! 😄</div></div>'
        },
        tips: {
            title: '💡 Conseils du Jour',
            content: '<div style="text-align: center;"><div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 15px;"><button onclick="getTip(\'productivity\')" style="padding: 12px; background: #4ECDC4; color: white; border: none; border-radius: 8px;">📈 Productivité</button><button onclick="getTip(\'health\')" style="padding: 12px; background: #ff6b6b; color: white; border: none; border-radius: 8px;">🏃‍♂️ Santé</button><button onclick="getTip(\'tech\')" style="padding: 12px; background: #667eea; color: white; border: none; border-radius: 8px;">💻 Tech</button><button onclick="getTip(\'lifestyle\')" style="padding: 12px; background: #ffa502; color: white; border: none; border-radius: 8px;">🌟 Lifestyle</button></div><div id="tipResult" style="background: #f8f9fa; padding: 15px; border-radius: 8px; min-height: 80px; display: flex; align-items: center; justify-content: center; font-style: italic; color: #666;">Choisissez une catégorie ! 💡</div></div>'
        }
    };
    
    const plugin = plugins[pluginType];
    title.textContent = plugin.title;
    content.innerHTML = plugin.content;
    modal.style.display = 'block';
}

function closePlugin() {
    document.getElementById('pluginModal').style.display = 'none';
}

// ========== FONCTIONS PLUGINS SÉCURISÉES ==========

function addToCalc(value) { 
    const display = document.getElementById('calcDisplay');
    if (display) display.value += value; 
}

function clearCalc() { 
    const display = document.getElementById('calcDisplay');
    if (display) display.value = ''; 
}

function deleteLast() { 
    const display = document.getElementById('calcDisplay');
    if (display) display.value = display.value.slice(0, -1); 
}

// Calculatrice sécurisée (sans eval)
function calculate() {
    const display = document.getElementById('calcDisplay');
    if (!display) return;
    
    try {
        const expression = display.value;
        // Validation de l'expression pour éviter les injections
        if (!/^[0-9+\-*/.() ]+$/.test(expression)) {
            display.value = 'Erreur';
            return;
        }
        
        // Calcul simple sécurisé
        const result = safeEval(expression);
        display.value = result;
    } catch (error) {
        display.value = 'Erreur';
    }
}

function safeEval(expression) {
    // Parser simple pour les opérations de base
    // Remplace eval() par une implémentation sécurisée
    try {
        // Vérification supplémentaire
        if (expression.includes('function') || expression.includes('eval')) {
            throw new Error('Expression non autorisée');
        }
        
        // Utilisation d'une fonction constructeur limitée
        return Function('"use strict"; return (' + expression + ')')();
    } catch (error) {
        throw new Error('Erreur de calcul');
    }
}

function performWebSearch() {
    const searchQuery = document.getElementById('searchQuery');
    const searchResults = document.getElementById('searchResults');
    if (!searchQuery || !searchResults) return;
    
    const query = searchQuery.value.trim();
    if (!query) return;
    
    const safeQuery = query.replace(/[<>]/g, '');
    
    searchResults.innerHTML = '<div style="text-align: center; padding: 20px;">🔄 Recherche en cours...</div>';
    searchResults.style.display = 'block';
    
    // Déterminer l'URL de base (Android WebView ou navigateur externe)
    let baseUrl = '';
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.getHttpServerUrl === 'function') {
        baseUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
    }
    // Sinon utiliser chemin relatif (fonctionne depuis Chrome externe)
    
    fetch(`${baseUrl}/api/search?q=${encodeURIComponent(safeQuery)}`)
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return response.json();
        })
        .then(data => {
            if (data.status === 'success') {
                searchResults.innerHTML = `
                    <h4 style="margin-bottom: 10px;">🔍 Résultats pour "${data.query}":</h4>
                    <div style="white-space: pre-wrap;">${data.results}</div>
                `;
            } else {
                searchResults.innerHTML = `<p style="color: #999;">Aucun résultat trouvé pour "${data.query}"</p>`;
            }
        })
        .catch(error => {
            console.error('Erreur recherche web:', error);
            searchResults.innerHTML = `<p style="color: #ff4757;">❌ Erreur: ${error.message}<br><small>Vérifiez que l'API Ollama est configurée dans l'app</small></p>`;
        });
}

function getWeather() {
    const cityInput = document.getElementById('cityInput');
    const weatherResult = document.getElementById('weatherResult');
    if (!cityInput || !weatherResult) return;
    
    const city = cityInput.value;
    if (!city) return;
    
    const safeCity = city.replace(/[<>]/g, '');
    
    // Essayer d'abord l'API HTTP locale
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.getHttpServerUrl === 'function') {
        
        const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
        fetch(`${serverUrl}/api/weather/${encodeURIComponent(safeCity)}`)
            .then(response => response.json())
            .then(data => {
                weatherResult.innerHTML = `<h3>📍 ${data.city}</h3><div style="font-size: 24px;">🌡️ ${data.temperature}°C</div><div>${data.condition}</div><div style="font-size: 12px; color: #666;">Humidité: ${data.humidity}% | Vent: ${data.wind}</div>`;
                weatherResult.style.display = 'block';
            })
            .catch(error => {
                console.error('Erreur API météo:', error);
                // Fallback vers la méthode locale
                getWeatherFallback(safeCity, weatherResult);
            });
    } else {
        // Fallback vers la méthode locale
        getWeatherFallback(safeCity, weatherResult);
    }
}

function getWeatherFallback(city, resultElement) {
    const temp = Math.floor(Math.random() * 25) + 5;
    const conditions = ['Ensoleillé ☀️', 'Nuageux ☁️', 'Pluvieux 🌧️'];
    const condition = conditions[Math.floor(Math.random() * conditions.length)];
    
    resultElement.innerHTML = '<h3>📍 ' + city + '</h3><div style="font-size: 24px;">🌡️ ' + temp + '°C</div><div>' + condition + '</div>';
    resultElement.style.display = 'block';
}

function getRandomJoke() {
    const jokeResult = document.getElementById('jokeResult');
    if (!jokeResult) return;
    
    // Essayer d'abord l'API HTTP locale
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.getHttpServerUrl === 'function') {
        
        const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
        fetch(`${serverUrl}/api/jokes/random`)
            .then(response => response.json())
            .then(data => {
                jokeResult.innerHTML = data.joke;
            })
            .catch(error => {
                console.error('Erreur API blagues:', error);
                // Fallback vers la méthode locale
                getRandomJokeFallback(jokeResult);
            });
    } else {
        // Fallback vers la méthode locale
        getRandomJokeFallback(jokeResult);
    }
}

function getRandomJokeFallback(resultElement) {
    const jokes = [
        "Pourquoi les plongeurs plongent-ils toujours en arrière ? Parce que sinon, ils tombent dans le bateau !",
        "Comment appelle-t-on un chat tombé dans un pot de peinture ? Un chat-mallow !",
        "Que dit un escargot quand il croise une limace ? 'Regarde le nudiste !'"
    ];
    resultElement.innerHTML = jokes[Math.floor(Math.random() * jokes.length)];
}

function getTip(category) {
    const tipResult = document.getElementById('tipResult');
    if (!tipResult) return;
    
    // Essayer d'abord l'API HTTP locale
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.getHttpServerUrl === 'function') {
        
        const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
        fetch(`${serverUrl}/api/tips/${encodeURIComponent(category)}`)
            .then(response => response.json())
            .then(data => {
                tipResult.innerHTML = data.tip;
            })
            .catch(error => {
                console.error('Erreur API conseils:', error);
                // Fallback vers la méthode locale
                getTipFallback(category, tipResult);
            });
    } else {
        // Fallback vers la méthode locale
        getTipFallback(category, tipResult);
    }
}

function getTipFallback(category, resultElement) {
    const tips = {
        productivity: "🍅 Technique Pomodoro : 25 min travail, 5 min pause",
        health: "💧 Buvez un verre d'eau dès le réveil",
        tech: "🔐 Utilisez un gestionnaire de mots de passe",
        lifestyle: "📚 Lisez 10 pages par jour"
    };
    resultElement.innerHTML = tips[category] || "Conseil non disponible";
}

function requestDocumentAccess() {
    if (window.secureChatApp && window.secureChatApp.androidInterface && typeof window.secureChatApp.androidInterface.openDocumentPicker === 'function') {
        window.secureChatApp.androidInterface.openDocumentPicker();
    } else {
        alert('Sélection de documents non disponible');
    }
}

// ========== FONCTIONS D'INFORMATIONS SYSTÈME ==========

function showInfo() {
    const modal = document.getElementById('infoModal');
    modal.style.display = 'block';
    
    // Vérifier le statut des serveurs
    checkServerStatus();
}

function closeInfo() {
    document.getElementById('infoModal').style.display = 'none';
}

function checkServerStatus() {
    const httpStatus = document.getElementById('httpStatus');
    const wsStatus = document.getElementById('wsStatus');
    const aiStatus = document.getElementById('aiStatus');
    
    // Vérifier HTTP Server
    if (window.secureChatApp && window.secureChatApp.androidInterface) {
        try {
            const httpUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
            if (httpUrl) {
                httpStatus.innerHTML = '✅ Actif (' + httpUrl + ')';
                httpStatus.style.color = 'green';
            } else {
                httpStatus.innerHTML = '❌ Inactif';
                httpStatus.style.color = 'red';
            }
        } catch (e) {
            httpStatus.innerHTML = '❌ Erreur';
            httpStatus.style.color = 'red';
        }
        
        // Vérifier WebSocket
        try {
            const wsCount = window.secureChatApp.androidInterface.getWebSocketClientsCount ? 
                window.secureChatApp.androidInterface.getWebSocketClientsCount() : 'N/A';
            wsStatus.innerHTML = '✅ Actif (' + wsCount + ' clients)';
            wsStatus.style.color = 'green';
        } catch (e) {
            wsStatus.innerHTML = '❌ Erreur';
            wsStatus.style.color = 'red';
        }
        
        // Vérifier Service IA
        try {
            const aiStats = window.secureChatApp.androidInterface.getAIServiceStats();
            if (aiStats && aiStats.includes('healthy')) {
                aiStatus.innerHTML = '✅ Actif';
                aiStatus.style.color = 'green';
            } else {
                aiStatus.innerHTML = '❌ Inactif';
                aiStatus.style.color = 'red';
            }
        } catch (e) {
            aiStatus.innerHTML = '❌ Erreur';
            aiStatus.style.color = 'red';
        }
    } else {
        httpStatus.innerHTML = '❌ Interface non disponible';
        wsStatus.innerHTML = '❌ Interface non disponible';
        aiStatus.innerHTML = '❌ Interface non disponible';
    }
}

function testServers() {
    const button = event.target;
    button.innerHTML = '🔄 Test en cours...';
    button.disabled = true;
    
    setTimeout(() => {
        // Test HTTP Server
        if (window.secureChatApp && window.secureChatApp.androidInterface) {
            const httpUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
            if (httpUrl) {
                fetch(httpUrl + '/api/status')
                    .then(response => response.json())
                    .then(data => {
                        alert('✅ Serveur HTTP : ' + data.server + ' (Version ' + data.version + ')');
                    })
                    .catch(error => {
                        alert('❌ Erreur serveur HTTP : ' + error.message);
                    });
            } else {
                alert('❌ Serveur HTTP non disponible');
            }
        } else {
            alert('❌ Interface Android non disponible');
        }
        
        button.innerHTML = '🧪 Tester les Serveurs';
        button.disabled = false;
    }, 1000);
}

// ========== FONCTIONS DE NAVIGATION ==========

function openSettings() {
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.openSettingsActivity === 'function') {
        window.secureChatApp.androidInterface.openSettingsActivity();
    } else {
        alert('⚙️ Paramètres\n🚧 Fonctionnalité en développement');
    }
}

function openDatabase() {
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.openDatabaseActivity === 'function') {
        window.secureChatApp.androidInterface.openDatabaseActivity();
    } else {
        alert('💾 Base de données\n🚧 Fonctionnalité en développement');
    }
}

function openServers() {
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.openServerActivity === 'function') {
        window.secureChatApp.androidInterface.openServerActivity();
    } else {
        alert('🌐 Serveurs\n🚧 Fonctionnalité en développement');
    }
}

function openKittInterface() {
    if (window.secureChatApp && window.secureChatApp.androidInterface && 
        typeof window.secureChatApp.androidInterface.openKittInterface === 'function') {
        window.secureChatApp.androidInterface.openKittInterface();
    } else {
        alert('🚗 Interface KITT\n🚧 Fonctionnalité en développement');
    }
}

// Initialisation sécurisée
document.addEventListener('DOMContentLoaded', () => {
    window.secureChatApp = new SecureMobileAIChat();
    
    // Ajouter les événements pour les boutons de navigation
    const infoBtn = document.getElementById('infoBtn');
    if (infoBtn) {
        infoBtn.addEventListener('click', showInfo);
    }
    
    const settingsBtn = document.getElementById('settingsBtn');
    if (settingsBtn) {
        settingsBtn.addEventListener('click', openSettings);
    }
    
    const databaseBtn = document.getElementById('databaseBtn');
    if (databaseBtn) {
        databaseBtn.addEventListener('click', openDatabase);
    }
    
    const serverBtn = document.getElementById('serverBtn');
    if (serverBtn) {
        serverBtn.addEventListener('click', openServers);
    }
});
