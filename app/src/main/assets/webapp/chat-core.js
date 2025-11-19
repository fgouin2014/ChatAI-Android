/**
 * chat-core.js - Coordinateur principal (refactorisé depuis chat.js)
 * 
 * Responsabilités :
 * - Initialisation de tous les modules
 * - Délégation des appels aux modules appropriés
 * - API publique préservée (window.secureChatApp)
 */

(function() {
    'use strict';

    class SecureMobileAIChat {
        constructor() {
            // Références Android
            this.androidInterface = window.AndroidApp || null;
            
            // Modules
            this.chatUI = null;
            this.chatMessaging = null;
            this.chatSpeech = null;
            this.chatBridge = null;
            this.chatHotword = null;
            this.chatConfig = null;
            
            // État
            this.personality = 'casual';
            this.language = 'fr';
            this.currentModel = 'microsoft/DialoGPT-medium';
            this.apiToken = null;
            
            // Références DOM (seront initialisées dans initialize())
            this.messageInput = null;
            this.sendBtn = null;
            this.voiceBtn = null;
            this.chatMessages = null;
            this.typingIndicator = null;
            this.personalityBtns = [];
            this.langSelector = null;
            this.navButtons = [];
            this.views = [];
            this.currentView = 'view-chat';
            
            // Config DOM (seront initialisées dans initialize())
            this.configModeSelect = null;
            this.configSelectedModel = null;
            this.configSelectedModelCustom = null;
            // ... autres références config (seront initialisées dans setupConfigFormListeners)
        }

        /**
         * Initialise l'application
         */
        async initialize() {
            console.log('🚀 Initialisation SecureMobileAIChat (modulaire)');
            
            // Initialiser références DOM
            this.initializeDOMReferences();
            
            // Initialiser modules
            this.chatUI = new window.ChatUI(document.getElementById('chatContainer'));
            this.chatMessaging = new window.ChatMessaging(this.androidInterface, this.chatUI, null); // chatBridge sera ajouté après
            this.chatSpeech = new window.ChatSpeech(this.androidInterface, this.chatUI, this.chatMessaging);
            this.chatBridge = new window.ChatBridge(this.androidInterface, this.chatUI, this.chatMessaging);
            this.chatHotword = new window.ChatHotword(this.chatBridge, this.chatUI);
            this.chatConfig = new window.ChatConfig(this.androidInterface);
            
            // Mettre à jour chatMessaging avec chatBridge
            this.chatMessaging.chatBridge = this.chatBridge;
            
            // Initialiser modules
            this.chatUI.messageInput = this.messageInput;
            this.chatSpeech.initializeSpeech();
            this.chatBridge.initialize();
            this.chatConfig.initialize();
            
            // Setup listeners
            this.setupEventListeners();
            this.setupConfigFormListeners();
            this.setupNavigation();
            
            // Charger historique
            this.chatMessaging.loadConversationHistory();
            
            // Charger config si on est dans la vue config
            if (this.currentView === 'view-config') {
                await this.chatConfig.loadAiConfigPreview(false);
            }
            
            console.log('✅ SecureMobileAIChat initialisé (modulaire)');
        }

        /**
         * Initialise les références DOM
         */
        initializeDOMReferences() {
            this.messageInput = document.getElementById('messageInput');
            this.sendBtn = document.getElementById('sendBtn');
            this.voiceBtn = document.getElementById('voiceBtn');
            this.chatMessages = document.getElementById('chatMessages');
            this.typingIndicator = document.getElementById('typingIndicator');
            this.personalityBtns = Array.from(document.querySelectorAll('.personality-btn'));
            this.langSelector = document.getElementById('langSelector');
            this.navButtons = Array.from(document.querySelectorAll('.nav-btn'));
            this.views = Array.from(document.querySelectorAll('.view'));
            
            // Config DOM
            this.configModeSelect = document.getElementById('configModeSelect');
            this.configSelectedModel = document.getElementById('configSelectedModel');
            this.configSelectedModelCustom = document.getElementById('configSelectedModelCustom');
            // ... autres références config (seront initialisées dans setupConfigFormListeners)
        }

        /**
         * Setup event listeners principaux
         */
        setupEventListeners() {
            // Send button
            if (this.sendBtn) {
                window.ChatUtils.addListener(this.sendBtn, 'click', () => this.sendMessage());
            }
            
            // Voice button
            if (this.voiceBtn) {
                window.ChatUtils.addListener(this.voiceBtn, 'click', () => this.chatSpeech.toggleVoiceRecording());
            }
            
            // Message input (Enter key)
            if (this.messageInput) {
                window.ChatUtils.addListener(this.messageInput, 'keydown', (e) => {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        this.sendMessage();
                    }
                });
                window.ChatUtils.addListener(this.messageInput, 'input', () => {
                    this.chatUI.adjustTextareaHeight();
                });
            }
            
            // Personality buttons
            this.personalityBtns.forEach(btn => {
                window.ChatUtils.addListener(btn, 'click', () => this.changePersonality(btn));
            });
        }

        /**
         * Setup listeners pour les formulaires de configuration
         */
        setupConfigFormListeners() {
            // Cette méthode délègue à chat-config.js mais configure les références DOM
            // Les méthodes de chat-config.js seront appelées avec les bonnes références
            // Pour l'instant, on garde une version simplifiée ici
            
            // Boutons sauvegarde (seront configurés dans chat-config.js)
            const saveButtons = [
                { id: 'saveModeConfigBtn', section: 'mode' },
                { id: 'saveCloudConfigBtn', section: 'cloud' },
                { id: 'saveLocalConfigBtn', section: 'local' },
                { id: 'saveWebThinkingBtn', section: 'thinking' },
                { id: 'saveVisionBtn', section: 'vision' },
                { id: 'saveAudioBtn', section: 'audio' },
                { id: 'saveHotwordBtn', section: 'hotword' },
                { id: 'saveTtsBtn', section: 'tts' },
                { id: 'savePromptsBtn', section: 'prompts' },
                { id: 'saveConstraintsBtn', section: 'constraints' }
            ];
            
            saveButtons.forEach(({ id, section }) => {
                const btn = document.getElementById(id);
                if (btn) {
                    window.ChatUtils.addListener(btn, 'click', () => {
                        // Déléguer à chat-config.js (sera implémenté avec les références DOM)
                        console.log(`saveConfigSection(${section}) appelé`);
                    });
                }
            });
            
            // Boutons hotword
            const hotwordStartBtn = document.getElementById('hotwordStartBtn');
            const hotwordStopBtn = document.getElementById('hotwordStopBtn');
            const hotwordRestartBtn = document.getElementById('hotwordRestartBtn');
            if (hotwordStartBtn) {
                window.ChatUtils.addListener(hotwordStartBtn, 'click', () => this.chatConfig.hotwordStart());
            }
            if (hotwordStopBtn) {
                window.ChatUtils.addListener(hotwordStopBtn, 'click', () => this.chatConfig.hotwordStop());
            }
            if (hotwordRestartBtn) {
                window.ChatUtils.addListener(hotwordRestartBtn, 'click', () => this.chatConfig.hotwordRestart());
            }
        }

        /**
         * Setup navigation
         */
        setupNavigation() {
            this.currentView = 'view-chat';
            if (this.navButtons && this.navButtons.length > 0) {
                this.navButtons.forEach(btn => {
                    btn.classList.toggle('active', btn.dataset.view === this.currentView);
                });
            }
            this.switchView(this.currentView);
        }

        /**
         * Change de vue
         */
        switchView(viewId) {
            if (!viewId) viewId = 'view-chat';

            if (this.views && this.views.length > 0) {
                this.views.forEach(view => {
                    const isActive = view.id === viewId;
                    if (isActive) {
                        view.style.display = 'flex';
                        requestAnimationFrame(() => {
                            view.classList.add('active');
                        });
                    } else {
                        view.classList.remove('active');
                        setTimeout(() => {
                            if (!view.classList.contains('active')) {
                                view.style.display = 'none';
                            }
                        }, 300);
                    }
                });
            }

            if (this.navButtons && this.navButtons.length > 0) {
                this.navButtons.forEach(btn => {
                    const isActive = btn.dataset.view === viewId;
                    btn.classList.toggle('active', isActive);
                });
            }

            this.currentView = viewId;

            if (viewId === 'view-config') {
                this.chatConfig.loadAiConfigPreview(false);
                setTimeout(() => {
                    this.chatConfig.initConfigTabs();
                }, 50);
            }
        }

        /**
         * Envoie un message (délègue à chat-messaging.js)
         */
        async sendMessage() {
            const text = this.messageInput?.value?.trim();
            if (!text) return;
            await this.chatMessaging.sendMessage(text);
        }

        /**
         * Change de personnalité
         */
        changePersonality(btn) {
            this.personalityBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            this.personality = btn.dataset.personality;
            
            // Animation
            btn.style.transform = 'scale(1.1)';
            setTimeout(() => btn.style.transform = 'scale(1)', 200);
            
            this.chatUI.showSecureMessage('ai', this.getPersonalityMessage());
        }

        /**
         * Obtient le message de personnalité
         */
        getPersonalityMessage() {
            const messages = {
                casual: { fr: "Mode décontracté activé ! 😎", en: "Casual mode activated! 😎" },
                friendly: { fr: "Mode amical activé ! 😊", en: "Friendly mode activated! 😊" },
                professional: { fr: "Mode professionnel activé ! 💼", en: "Professional mode activated! 💼" },
                creative: { fr: "Mode créatif activé ! 🎨", en: "Creative mode activated! 🎨" },
                funny: { fr: "Mode drôle activé ! 😂", en: "Funny mode activated! 😂" }
            };
            return messages[this.personality]?.[this.language] || messages.casual.fr;
        }

        /**
         * Sauvegarde dans historique (appelé depuis chat-ui.js)
         */
        saveToHistory(sender, text) {
            this.chatMessaging.saveToHistory(sender, text);
        }

        // ========== MÉTHODES PUBLIQUES (API préservée) ==========
        
        /**
         * Affiche un message sécurisé (délègue à chat-ui.js)
         */
        showSecureMessage(sender, message, saveToHistory = true) {
            this.chatUI.showSecureMessage(sender, message, saveToHistory);
        }

        /**
         * Affiche un chunk de thinking (délègue à chat-ui.js)
         */
        displayThinkingChunk(messageId, type, content, isComplete) {
            this.chatUI.displayThinkingChunk(messageId, type, content, isComplete);
        }

        /**
         * Sanitize input (délègue à ChatUtils)
         */
        sanitizeInput(input) {
            return window.ChatUtils.sanitizeInput(input);
        }

        /**
         * Validate input (délègue à ChatUtils)
         */
        validateInput(input) {
            return window.ChatUtils.validateInput(input);
        }
    }

    // Export global (API préservée)
    window.secureChatApp = new SecureMobileAIChat();
    
    // Initialiser quand DOM est prêt
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            window.secureChatApp.initialize();
        });
    } else {
        window.secureChatApp.initialize();
    }
    
    console.log('✅ ChatCore chargé');
})();

