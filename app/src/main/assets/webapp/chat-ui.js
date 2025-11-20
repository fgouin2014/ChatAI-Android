/**
 * chat-ui.js - Interface utilisateur pour ChatAI
 * 
 * Responsabilités :
 * - Affichage des messages (showSecureMessage)
 * - Thinking chunks (displayThinkingChunk, showThinkingMessage)
 * - Indicateurs (typing, VU-meter)
 * - Scroll et navigation
 * - Toast notifications
 */

(function() {
    'use strict';

    class ChatUI {
        constructor(container) {
            this.container = container;
            this.chatMessages = document.getElementById('chatMessages');
            this.typingIndicator = document.getElementById('typingIndicator');
            this.messageInput = document.getElementById('messageInput');
            this.streamingMessages = new Map(); // messageId -> element
        }

        /**
         * Affiche un message sécurisé (user ou ai)
         * @param sender 'user' ou 'ai'
         * @param message Le texte du message
         * @param saveToHistory Si le message doit être sauvegardé dans l'historique
         * @param isStt Si le message provient de la STT (hotword), affiche un badge 🔊 sur l'avatar
         */
        showSecureMessage(sender, message, saveToHistory = true, isStt = false) {
            if (!this.chatMessages) return;
            
            const messageDiv = document.createElement('div');
            messageDiv.className = `message ${sender}`;
            messageDiv.style.opacity = '0';
            messageDiv.style.transform = 'translateY(20px)';
            
            const avatarContainer = document.createElement('div');
            avatarContainer.className = 'message-avatar-container';
            
            const avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            avatar.textContent = sender === 'user' ? '👤' : '🤖';
            
            // Badge 🔊 superposé pour les messages STT
            if (isStt && sender === 'user') {
                const sttBadge = document.createElement('div');
                sttBadge.className = 'message-avatar-stt-badge';
                sttBadge.textContent = '🔊';
                avatarContainer.appendChild(avatar);
                avatarContainer.appendChild(sttBadge);
            } else {
                avatarContainer.appendChild(avatar);
            }
            
            const bubble = document.createElement('div');
            bubble.className = 'message-bubble';
            bubble.textContent = message;
            
            if (sender === 'ai') {
                const actions = document.createElement('div');
                actions.className = 'message-actions';
                
                const listenBtn = document.createElement('button');
                listenBtn.className = 'message-btn';
                listenBtn.title = 'Écouter';
                listenBtn.textContent = '🔊';
                listenBtn.addEventListener('click', () => this.speakText(message));
                
                const copyBtn = document.createElement('button');
                copyBtn.className = 'message-btn';
                copyBtn.title = 'Copier';
                copyBtn.textContent = '📋';
                copyBtn.addEventListener('click', () => this.copyToClipboard(message));
                
                actions.appendChild(listenBtn);
                actions.appendChild(copyBtn);
                bubble.appendChild(actions);
                
                // Notification si l'app n'est pas en avant-plan
                if (document.hidden && window.secureChatApp?.androidInterface?.showNotification) {
                    window.secureChatApp.androidInterface.showNotification(
                        window.ChatUtils.sanitizeInput('💬 Nouvelle réponse: ' + message.substring(0, 50) + (message.length > 50 ? '...' : ''))
                    );
                }
            }
            
            messageDiv.appendChild(avatarContainer);
            messageDiv.appendChild(bubble);
            this.chatMessages.appendChild(messageDiv);
            
            // Animation d'entrée
            requestAnimationFrame(() => {
                messageDiv.style.transition = 'opacity 0.3s, transform 0.3s';
                messageDiv.style.opacity = '1';
                messageDiv.style.transform = 'translateY(0)';
            });
            
            this.scrollToBottom();
            
            // Sauvegarder dans historique si nécessaire (délégué à chat-core)
            if (saveToHistory && sender === 'ai' && window.secureChatApp?.saveToHistory) {
                window.secureChatApp.saveToHistory('ai', message);
            }
        }

        /**
         * Affichage d'un chunk de thinking ou de réponse (appelé par Android)
         */
        displayThinkingChunk(messageId, type, content, isComplete) {
            this.createOrUpdateStreamingMessage(messageId, type, content, isComplete);
        }

        /**
         * Création ou mise à jour d'un message en mode streaming
         */
        createOrUpdateStreamingMessage(messageId, type, content, isComplete) {
            let messageDiv = document.getElementById(messageId);
            
            if (!messageDiv) {
                // Créer un nouveau message
                messageDiv = document.createElement('div');
                messageDiv.id = messageId;
                messageDiv.className = 'message ai';
                
                const avatarContainer = document.createElement('div');
                avatarContainer.className = 'message-avatar-container';
                
                const avatar = document.createElement('div');
                avatar.className = 'message-avatar';
                avatar.textContent = '🤖';
                avatarContainer.appendChild(avatar);
                
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
                responseDiv.style.cssText = 'white-space: pre-wrap;';
                bubble.appendChild(responseDiv);
                
                messageDiv.appendChild(avatarContainer);
                messageDiv.appendChild(bubble);
                
                if (this.chatMessages) {
                    this.chatMessages.appendChild(messageDiv);
                }
            }
            
            // Mettre à jour le contenu
            if (type === 'thinking') {
                const thinkingContentDiv = messageDiv.querySelector('.thinking-content-stream');
                if (thinkingContentDiv) {
                    thinkingContentDiv.textContent += content;
                    
                    if (isComplete) {
                        const thinkingSection = messageDiv.querySelector('.thinking-section');
                        if (thinkingSection) {
                            const header = thinkingSection.querySelector('div');
                            if (header) {
                                header.innerHTML = '🧠 Raisonnement <span style="font-size: 12px; color: #888;">(cliquez pour voir/cacher)</span>';
                                thinkingSection.style.cursor = 'pointer';
                                
                                const contentDiv = thinkingSection.querySelector('.thinking-content-stream');
                                if (contentDiv) {
                                    contentDiv.style.display = 'none';
                                    contentDiv.style.maxHeight = '200px';
                                    contentDiv.style.overflowY = 'auto';
                                }
                                
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
                    // ⭐ FIX : Mettre à jour le titre du thinking dès le premier chunk de réponse
                    // (début de réponse = fin du thinking implicite, même si isComplete n'est pas encore arrivé)
                    const thinkingSection = messageDiv.querySelector('.thinking-section');
                    if (thinkingSection) {
                        const thinkingHeader = thinkingSection.querySelector('div');
                        if (thinkingHeader && thinkingHeader.textContent.includes('en cours')) {
                            // Le titre est encore "Raisonnement en cours..." → le mettre à jour
                            thinkingHeader.innerHTML = '🧠 Raisonnement <span style="font-size: 12px; color: #888;">(cliquez pour voir/cacher)</span>';
                            thinkingSection.style.cursor = 'pointer';
                            
                            const contentDiv = thinkingSection.querySelector('.thinking-content-stream');
                            if (contentDiv && contentDiv.style.display !== 'none') {
                                contentDiv.style.display = 'none';
                                contentDiv.style.maxHeight = '200px';
                                contentDiv.style.overflowY = 'auto';
                            }
                            
                            // Ajouter le listener de toggle si pas déjà présent
                            if (!thinkingSection.hasAttribute('data-toggle-added')) {
                                thinkingSection.setAttribute('data-toggle-added', 'true');
                                thinkingSection.addEventListener('click', () => {
                                    const isVisible = contentDiv.style.display !== 'none';
                                    contentDiv.style.display = isVisible ? 'none' : 'block';
                                });
                            }
                        }
                    }
                    
                    responseDiv.textContent += content;
                    
                    if (isComplete) {
                        const bubble = messageDiv.querySelector('.message-bubble');
                        if (bubble && !bubble.querySelector('.message-actions')) {
                            const actions = document.createElement('div');
                            actions.className = 'message-actions';
                            
                            const listenBtn = document.createElement('button');
                            listenBtn.className = 'message-btn';
                            listenBtn.title = 'Écouter';
                            listenBtn.textContent = '🔊';
                            listenBtn.addEventListener('click', () => this.speakText(responseDiv.textContent));
                            
                            const copyBtn = document.createElement('button');
                            copyBtn.className = 'message-btn';
                            copyBtn.title = 'Copier';
                            copyBtn.textContent = '📋';
                            copyBtn.addEventListener('click', () => this.copyToClipboard(responseDiv.textContent));
                            
                            actions.appendChild(listenBtn);
                            actions.appendChild(copyBtn);
                            bubble.appendChild(actions);
                        }
                    }
                }
            }
            
            this.scrollToBottom();
        }

        /**
         * Affiche un message complet avec thinking et réponse (mode non-streaming)
         */
        showThinkingMessage(thinkingContent, responseContent) {
            const messageDiv = document.createElement('div');
            messageDiv.className = 'message ai';
            
            const avatarContainer = document.createElement('div');
            avatarContainer.className = 'message-avatar-container';
            
            const avatar = document.createElement('div');
            avatar.className = 'message-avatar';
            avatar.textContent = '🤖';
            avatarContainer.appendChild(avatar);
            
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
            
            const copyBtn = document.createElement('button');
            copyBtn.className = 'message-btn';
            copyBtn.title = 'Copier';
            copyBtn.textContent = '📋';
            copyBtn.addEventListener('click', () => this.copyToClipboard(responseContent));
            
            actions.appendChild(listenBtn);
            actions.appendChild(copyBtn);
            bubble.appendChild(actions);
            
            messageDiv.appendChild(avatarContainer);
            messageDiv.appendChild(bubble);
            
            if (this.chatMessages) {
                this.chatMessages.appendChild(messageDiv);
                this.scrollToBottom();
            }
        }

        /**
         * Affichage de l'indicateur de frappe
         */
        showTypingIndicator() {
            if (this.typingIndicator) {
                this.typingIndicator.style.display = 'block';
                this.scrollToBottom();
            }
        }

        /**
         * Masquage de l'indicateur de frappe
         */
        hideTypingIndicator() {
            if (this.typingIndicator) {
                this.typingIndicator.style.display = 'none';
            }
        }

        /**
         * Activation/désactivation des contrôles d'entrée
         */
        toggleInput(enabled) {
            if (this.messageInput) this.messageInput.disabled = !enabled;
            if (document.getElementById('sendBtn')) {
                document.getElementById('sendBtn').disabled = !enabled;
            }
            if (document.getElementById('voiceBtn')) {
                document.getElementById('voiceBtn').disabled = !enabled;
            }
        }

        /**
         * Scroll automatique vers le bas
         */
        scrollToBottom() {
            if (this.chatMessages) {
                requestAnimationFrame(() => {
                    this.chatMessages.scrollTop = this.chatMessages.scrollHeight;
                });
            }
        }

        /**
         * Ajuster la hauteur du textarea selon le contenu
         */
        adjustTextareaHeight() {
            if (this.messageInput) {
                this.messageInput.style.height = 'auto';
                this.messageInput.style.height = Math.min(this.messageInput.scrollHeight, 150) + 'px';
            }
        }

        /**
         * Affichage de toast notifications
         */
        showToast(message, duration = 2000) {
            const toast = document.createElement('div');
            toast.textContent = message;
            toast.style.cssText = `
                position: fixed;
                bottom: 80px;
                left: 50%;
                transform: translateX(-50%);
                background: rgba(0, 0, 0, 0.8);
                color: white;
                padding: 10px 20px;
                border-radius: 20px;
                z-index: 10000;
                font-size: 14px;
                opacity: 0;
                transition: opacity 0.3s;
            `;
            
            document.body.appendChild(toast);
            
            requestAnimationFrame(() => {
                toast.style.opacity = '1';
            });
            
            setTimeout(() => {
                toast.style.opacity = '0';
                setTimeout(() => document.body.removeChild(toast), 300);
            }, duration);
        }

        /**
         * Copie dans le presse-papier
         */
        async copyToClipboard(text) {
            try {
                await navigator.clipboard.writeText(text);
                this.showToast('Message copié !');
            } catch (error) {
                console.error('Erreur copie presse-papier:', error);
                // Fallback
                const textArea = document.createElement('textarea');
                textArea.value = text;
                document.body.appendChild(textArea);
                textArea.select();
                document.execCommand('copy');
                document.body.removeChild(textArea);
                this.showToast('Message copié !');
            }
        }

        /**
         * Synthèse vocale du texte
         */
        speakText(text) {
            if ('speechSynthesis' in window) {
                // Arrêter toute synthèse en cours
                speechSynthesis.cancel();
                
                const language = window.secureChatApp?.language || 'fr';
                const utterance = new SpeechSynthesisUtterance(text);
                utterance.lang = language === 'fr' ? 'fr-FR' : 'en-US';
                utterance.rate = 0.9;
                utterance.pitch = 1;
                utterance.volume = 1;
                
                utterance.onstart = () => {
                    console.log('Synthèse vocale démarrée');
                };
                
                utterance.onend = () => {
                    console.log('Synthèse vocale terminée');
                };
                
                utterance.onerror = (event) => {
                    console.error('Erreur synthèse vocale:', event);
                    this.showToast('Erreur lors de la lecture');
                };
                
                speechSynthesis.speak(utterance);
            } else {
                this.showToast('Synthèse vocale non disponible');
            }
        }

        /**
         * ✅ NOUVEAU Phase 1 : Affiche/cache l'indicateur VU-meter dans le Chat
         */
        updateVUIndicator(visible) {
            let vuIndicator = document.getElementById('vuIndicator');
            if (!vuIndicator) {
                // Créer l'indicateur VU si n'existe pas
                const chatInputContainer = document.querySelector('.chat-input-container');
                if (chatInputContainer) {
                    vuIndicator = document.createElement('div');
                    vuIndicator.id = 'vuIndicator';
                    vuIndicator.className = 'vu-indicator';
                    vuIndicator.style.cssText = `
                        display: none;
                        position: absolute;
                        bottom: 60px;
                        left: 50%;
                        transform: translateX(-50%);
                        background: rgba(0, 0, 0, 0.8);
                        color: #fff;
                        padding: 8px 16px;
                        border-radius: 8px;
                        font-size: 12px;
                        z-index: 1000;
                    `;
                    
                    const vuBar = document.createElement('div');
                    vuBar.id = 'vuBar';
                    vuBar.style.cssText = `
                        width: 100px;
                        height: 4px;
                        background: #333;
                        border-radius: 2px;
                        margin-top: 4px;
                        overflow: hidden;
                    `;
                    
                    const vuFill = document.createElement('div');
                    vuFill.id = 'vuFill';
                    vuFill.style.cssText = `
                        height: 100%;
                        background: linear-gradient(90deg, #10b981, #3b82f6);
                        width: 0%;
                        transition: width 0.1s;
                    `;
                    
                    vuBar.appendChild(vuFill);
                    vuIndicator.appendChild(document.createTextNode('🎤 Écoute...'));
                    vuIndicator.appendChild(vuBar);
                    chatInputContainer.appendChild(vuIndicator);
                }
            }
            
            if (vuIndicator) {
                vuIndicator.style.display = visible ? 'block' : 'none';
            }
        }

        /**
         * ✅ NOUVEAU Phase 1 : Met à jour le niveau VU-meter (0-100%)
         */
        updateVUIndicatorLevel(rmsDb) {
            // Convertir RMS dB en niveau 0-100%
            // RMS -30dB à -10dB = 0% à 100%
            const minDb = -30;
            const maxDb = -10;
            const clampedDb = Math.max(minDb, Math.min(maxDb, rmsDb));
            const percentage = ((clampedDb - minDb) / (maxDb - minDb)) * 100;
            
            const vuFill = document.getElementById('vuFill');
            if (vuFill) {
                vuFill.style.width = percentage + '%';
            }
        }
    }

    // Export global
    window.ChatUI = ChatUI;
    console.log('✅ ChatUI chargé');
})();

