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
            this.chatHistory = null; // ⭐ NOUVEAU: Module historique
            this.chatDiagnostics = null; // ⭐ NOUVEAU: Module diagnostics
            
            // État
            this.personality = 'casual';
            this.language = 'fr';
            this.currentModel = 'microsoft/DialoGPT-medium';
            this.apiToken = null;
            this.hotwordModels = []; // Partagé avec chat-config
            
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
            this.chatHistory = new window.ChatHistory(this.androidInterface); // ⭐ NOUVEAU: Module historique
            this.chatDiagnostics = new window.ChatDiagnostics(this.androidInterface, this.chatUI); // ⭐ NOUVEAU: Module diagnostics
            
            // Mettre à jour chatMessaging avec chatBridge
            this.chatMessaging.chatBridge = this.chatBridge;
            
            // Initialiser modules
            this.chatUI.messageInput = this.messageInput;
            this.chatSpeech.initializeSpeech();
            this.chatBridge.initialize();
            this.chatConfig.initialize();
            this.chatConfig.initializeWithReferences(this); // Passer références DOM
            this.chatConfig.initCustomSelects(); // Initialiser selects personnalisés
            
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
            // Chat DOM
            this.messageInput = document.getElementById('messageInput');
            this.sendBtn = document.getElementById('sendBtn');
            this.voiceBtn = document.getElementById('voiceBtn');
            this.chatMessages = document.getElementById('chatMessages');
            this.typingIndicator = document.getElementById('typingIndicator');
            this.clearBtn = document.getElementById('clearBtn');
            this.langBtn = document.getElementById('langBtn');
            this.langSelector = document.getElementById('langSelector');
            this.kittBtn = document.getElementById('kittBtn');
            this.gamesBtn = document.getElementById('gamesBtn');
            this.pluginModal = document.getElementById('pluginModal');
            
            // Personality & Language
            this.personalityBtns = Array.from(document.querySelectorAll('.personality-btn'));
            
            // Navigation
            this.navButtons = Array.from(document.querySelectorAll('.main-nav-btn')) || Array.from(document.querySelectorAll('.nav-btn'));
            this.views = Array.from(document.querySelectorAll('.view'));
            
            // Config DOM - Mode & Models (Tab General)
            this.configModeSelect = document.getElementById('configModeSelect');
            this.configSelectedModel = document.getElementById('configSelectedModel');
            this.configSelectedModelCustom = document.getElementById('configSelectedModelCustom');
            this.saveModeConfigBtn = document.getElementById('saveModeConfigBtn');
            
            // Config DOM - Cloud
            this.configCloudProvider = document.getElementById('configCloudProvider');
            this.configCloudProviderCustom = document.getElementById('configCloudProviderCustom');
            this.configCloudApiKey = document.getElementById('configCloudApiKey');
            this.configCloudModel = document.getElementById('configCloudModel');
            this.configCloudModelCustom = document.getElementById('configCloudModelCustom');
            this.saveCloudConfigBtn = document.getElementById('saveCloudConfigBtn');
            
            // Config DOM - Local
            this.configLocalUrl = document.getElementById('configLocalUrl');
            this.configLocalModel = document.getElementById('configLocalModel');
            this.configLocalModelCustom = document.getElementById('configLocalModelCustom');
            this.saveLocalConfigBtn = document.getElementById('saveLocalConfigBtn');
            
            // Config DOM - RAG (dans tab Local)
            this.configRAGEnabled = document.getElementById('configRAGEnabled');
            this.configEmbeddingModel = document.getElementById('configEmbeddingModel');
            this.configEmbeddingSource = document.getElementById('configEmbeddingSource');
            this.configOnnxEmbeddingModel = document.getElementById('configOnnxEmbeddingModel');
            this.configOnnxVisionModel = document.getElementById('configOnnxVisionModel');
            this.configOnnxTranslationModel = document.getElementById('configOnnxTranslationModel');
            
            // Config DOM - Thinking & WebSearch
            this.configWebSearchProvider = document.getElementById('configWebSearchProvider');
            this.configThinkingEnabled = document.getElementById('configThinkingEnabled');
            this.saveWebThinkingBtn = document.getElementById('saveWebThinkingBtn');
            
            // Config DOM - Vision & Audio
            this.configVisionModel = document.getElementById('configVisionModel');
            this.configVisionModelCustom = document.getElementById('configVisionModelCustom');
            this.configAudioEngine = document.getElementById('configAudioEngine');
            this.configAudioModel = document.getElementById('configAudioModel');
            this.configAudioModelCustom = document.getElementById('configAudioModelCustom');
            this.configAudioEndpoint = document.getElementById('configAudioEndpoint');
            this.configAudioTimeout = document.getElementById('configAudioTimeout');
            this.configAudioSilenceDb = document.getElementById('configAudioSilenceDb');
            this.configAudioSilenceMs = document.getElementById('configAudioSilenceMs');
            this.configAudioDelayAfterHotword = document.getElementById('configAudioDelayAfterHotword');
            this.saveVisionBtn = document.getElementById('saveVisionBtn');
            this.saveAudioBtn = document.getElementById('saveAudioBtn');
            
            // Config DOM - Hotword
            this.configHotwordEnabled = document.getElementById('configHotwordEnabled');
            this.configHotwordEngine = document.getElementById('configHotwordEngine');
            this.configHotwordAccessKey = document.getElementById('configHotwordAccessKey');
            this.configHotwordKeyword = document.getElementById('configHotwordKeyword');
            this.configHotwordAutoListen = document.getElementById('configHotwordAutoListen');
            this.configHotwordDebugScores = document.getElementById('configHotwordDebugScores');
            this.configHotwordDebounce = document.getElementById('configHotwordDebounce');
            this.configHotwordCommMode = document.getElementById('configHotwordCommMode');
            this.hotwordNewName = document.getElementById('hotwordNewName');
            this.hotwordNewAsset = document.getElementById('hotwordNewAsset');
            this.hotwordNewThreshold = document.getElementById('hotwordNewThreshold');
            this.addHotwordModelBtn = document.getElementById('addHotwordModelBtn');
            this.importHotwordAssetsBtn = document.getElementById('importHotwordAssetsBtn');
            this.saveHotwordBtn = document.getElementById('saveHotwordBtn');
            this.porcupineFields = document.querySelectorAll('.porcupine-only');
            
            // Config DOM - TTS
            this.configTtsMode = document.getElementById('configTtsMode');
            this.configTtsVoice = document.getElementById('configTtsVoice');
            this.configTtsVoiceCustom = document.getElementById('configTtsVoiceCustom');
            this.configTtsAutoPlay = document.getElementById('configTtsAutoPlay');
            this.saveTtsBtn = document.getElementById('saveTtsBtn');
            
            // Config DOM - Prompts
            this.configPromptKitt = document.getElementById('configPromptKitt');
            this.configPromptGlados = document.getElementById('configPromptGlados');
            this.configPromptKarr = document.getElementById('configPromptKarr');
            this.savePromptsBtn = document.getElementById('savePromptsBtn');
            
            // Config DOM - Constraints
            this.configMaxContext = document.getElementById('configMaxContext');
            this.configMaxResponse = document.getElementById('configMaxResponse');
            this.saveConstraintsBtn = document.getElementById('saveConstraintsBtn');
            
            // Config DOM - AI Config Editor
            this.aiConfigPreview = document.getElementById('aiConfigPreview');
            this.aiConfigReloadBtn = document.getElementById('aiConfigReloadBtn');
            this.aiConfigEditBtn = document.getElementById('aiConfigEditBtn');
            this.jsonModal = document.getElementById('jsonModal');
            this.closeJsonModalBtn = document.getElementById('closeJsonModal');
            this.aiConfigEditor = document.getElementById('aiConfigEditor');
            this.aiConfigSaveBtn = document.getElementById('aiConfigSaveBtn');
            this.aiConfigEditorReloadBtn = document.getElementById('aiConfigEditorReloadBtn');
            this.aiConfigFeedback = document.getElementById('aiConfigFeedback');
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
            
            // Clear button
            if (this.clearBtn) {
                window.ChatUtils.addListener(this.clearBtn, 'click', () => this.confirmClearChat());
            }
            
            // Language button
            if (this.langBtn) {
                window.ChatUtils.addListener(this.langBtn, 'click', () => this.toggleLanguageSelector());
            }
            
            // KITT button
            if (this.kittBtn) {
                window.ChatUtils.addListener(this.kittBtn, 'click', () => this.openKittInterface());
            }
            
            // Games button
            if (this.gamesBtn) {
                window.ChatUtils.addListener(this.gamesBtn, 'click', () => this.handleGamesButton());
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
            
            // Language buttons
            document.querySelectorAll('.lang-btn').forEach(btn => {
                window.ChatUtils.addListener(btn, 'click', () => this.changeLanguage(btn));
            });
            
            // Fermeture du sélecteur de langue
            document.addEventListener('click', (e) => {
                if (this.langSelector && 
                    !this.langSelector.contains(e.target) && 
                    e.target !== this.langBtn) {
                    this.langSelector.style.display = 'none';
                }
            });
            
            // Modal plugins
            if (this.pluginModal) {
                window.ChatUtils.addListener(this.pluginModal, 'click', (e) => {
                    if (e.target === this.pluginModal && typeof closePlugin === 'function') {
                        closePlugin();
                    }
                });
            }
            
            // Boutons de configuration AI
            if (this.aiConfigReloadBtn) {
                window.ChatUtils.addListener(this.aiConfigReloadBtn, 'click', () => this.chatConfig.loadAiConfigPreview(true));
            }
            if (this.aiConfigEditBtn) {
                window.ChatUtils.addListener(this.aiConfigEditBtn, 'click', () => this.chatConfig.openAiConfigEditor());
            }
            if (this.aiConfigSaveBtn) {
                window.ChatUtils.addListener(this.aiConfigSaveBtn, 'click', () => this.chatConfig.saveAiConfig());
            }
            if (this.aiConfigEditorReloadBtn) {
                window.ChatUtils.addListener(this.aiConfigEditorReloadBtn, 'click', () => this.reloadAiConfigEditor());
            }
            if (this.closeJsonModalBtn) {
                window.ChatUtils.addListener(this.closeJsonModalBtn, 'click', () => this.chatConfig.closeAiConfigEditor());
            }
            
            // Modal JSON
            if (this.jsonModal) {
                window.ChatUtils.addListener(this.jsonModal, 'click', (event) => {
                    if (event.target === this.jsonModal) {
                        this.chatConfig.closeAiConfigEditor();
                    }
                });
            }
            
            // Navigation
            this.navButtons.forEach(btn => {
                window.ChatUtils.addListener(btn, 'click', () => {
                    this.switchView(btn.dataset.view);
                });
            });
            
            // Scroll optimisé
            if (this.chatMessages) {
                this.chatMessages.addEventListener('scroll', window.ChatUtils.throttle(() => this.handleScroll(), 100));
            }
        }

        /**
         * Setup listeners pour les formulaires de configuration
         */
        setupConfigFormListeners() {
            // Passer toutes les références DOM à chat-config
            this.chatConfig.initializeWithReferences(this);
            
            // Boutons sauvegarde
            const buttonBindings = [
                { element: this.saveModeConfigBtn, section: 'mode' },
                { element: this.saveCloudConfigBtn, section: 'cloud' },
                { element: this.saveLocalConfigBtn, section: 'local' },
                { element: this.saveWebThinkingBtn, section: 'thinking' },
                { element: this.saveVisionBtn, section: 'vision' },
                { element: this.saveAudioBtn, section: 'audio' },
                { element: this.saveHotwordBtn, section: 'hotword' },
                { element: this.saveTtsBtn, section: 'tts' },
                { element: this.savePromptsBtn, section: 'prompts' },
                { element: this.saveConstraintsBtn, section: 'constraints' }
            ];
            
            buttonBindings.forEach(({ element, section }) => {
                if (element) {
                    window.ChatUtils.addListener(element, 'click', () => {
                        this.chatConfig.saveConfigSection(section, this);
                    });
                }
            });
            
            // ⭐ NOUVEAU: Listener sur le select de mode pour mettre à jour RAG en temps réel
            if (this.configModeSelect) {
                window.ChatUtils.addListener(this.configModeSelect, 'change', () => {
                    // Mettre à jour le statut RAG immédiatement quand le mode change
                    if (this.chatConfig && this.chatConfig.updateRAGStatus) {
                        this.chatConfig.updateRAGStatus();
                    }
                });
            }
            
            // Boutons contrôle Hotword
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
            
            // Hotword models
            if (this.addHotwordModelBtn) {
                window.ChatUtils.addListener(this.addHotwordModelBtn, 'click', () => this.chatConfig.handleAddHotwordModel(this));
            }
            if (this.importHotwordAssetsBtn) {
                window.ChatUtils.addListener(this.importHotwordAssetsBtn, 'click', () => this.chatConfig.importHotwordAssets(this));
            }
            
            // Hotword engine change
            if (this.configHotwordEngine) {
                window.ChatUtils.addListener(this.configHotwordEngine, 'change', () => this.chatConfig.updateHotwordEngineView(this));
            }
            
            // Audio engine change
            if (this.configAudioEngine) {
                window.ChatUtils.addListener(this.configAudioEngine, 'change', () => this.chatConfig.updateAudioEngineView(this));
            }
            
            // Toggle pour "Ajouter un modèle"
            const addModelToggle = document.querySelector('.add-model-toggle');
            if (addModelToggle) {
                window.ChatUtils.addListener(addModelToggle, 'click', () => {
                    const content = addModelToggle.nextElementSibling;
                    const icon = addModelToggle.querySelector('.toggle-icon');
                    if (content && icon) {
                        const isHidden = content.style.display === 'none';
                        content.style.display = isHidden ? 'block' : 'none';
                        icon.style.transform = isHidden ? 'rotate(0deg)' : 'rotate(-90deg)';
                    }
                });
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
                // ⭐ NOUVEAU: Mettre à jour le statut RAG quand on ouvre la vue config
                if (this.chatConfig && this.chatConfig.updateRAGStatus) {
                    this.chatConfig.updateRAGStatus();
                }
                this.chatConfig.loadAiConfigPreview(false).then(() => {
                    // renderConfigForms sera appelé dans loadAiConfigPreview si core est défini
                    setTimeout(() => {
                        this.chatConfig.initConfigTabs();
                    }, 50);
                });
            } else if (viewId === 'view-history' && this.chatHistory) {
                // ⭐ NOUVEAU: Charger l'historique quand la vue est affichée
                this.chatHistory.loadHistory();
            } else if (viewId === 'view-diagnostics' && this.chatDiagnostics) {
                // ⭐ NOUVEAU: Charger les diagnostics quand la vue est affichée
                this.chatDiagnostics.loadDiagnostics();
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

        /**
         * Toggle du sélecteur de langue
         */
        toggleLanguageSelector() {
            if (!this.langSelector) return;
            const isVisible = this.langSelector.style.display === 'block';
            this.langSelector.style.display = isVisible ? 'none' : 'block';
        }

        /**
         * Changement de langue
         */
        changeLanguage(btn) {
            document.querySelectorAll('.lang-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            this.language = btn.dataset.lang;
            if (this.langSelector) {
                this.langSelector.style.display = 'none';
            }
            // Mise à jour de la reconnaissance vocale
            if (this.chatSpeech && this.chatSpeech.recognition) {
                this.chatSpeech.recognition.lang = this.language === 'fr' ? 'fr-FR' : 'en-US';
            }
        }

        /**
         * Confirmation avant effacement du chat
         */
        confirmClearChat() {
            if (this.chatMessaging.conversationHistory.length === 0) {
                this.chatUI.showSecureMessage('ai', 'La conversation est déjà vide.');
                return;
            }
            if (confirm('Êtes-vous sûr de vouloir effacer toute la conversation ?')) {
                this.clearChat();
            }
        }

        /**
         * Effacement du chat avec animation
         */
        clearChat() {
            if (!this.chatMessages) return;
            // Animation de disparition
            this.chatMessages.style.opacity = '0';
            setTimeout(() => {
                this.chatMessages.innerHTML = '';
                this.chatMessages.style.opacity = '1';
                this.chatUI.showSecureMessage('ai', this.getPersonalityMessage());
                this.chatMessaging.conversationHistory = [];
                this.chatMessaging.saveConversationToLocalStorage();
            }, 300);
        }

        /**
         * Ouvre l'interface KITT
         */
        openKittInterface() {
            if (this.androidInterface?.openKittInterface) {
                this.androidInterface.openKittInterface();
            } else {
                this.chatUI.showToast('Interface KITT non disponible');
            }
        }

        /**
         * Gère le bouton Games
         */
        handleGamesButton() {
            if (this.androidInterface?.openGamesLibrary) {
                this.androidInterface.openGamesLibrary();
            } else {
                this.chatUI.showToast('GameLibrary non disponible');
            }
        }

        /**
         * Gestion du scroll
         */
        handleScroll() {
            // Logique pour charger plus de messages si nécessaire
            const scrollPosition = this.chatMessages.scrollTop;
            if (scrollPosition < 50) {
                // Chargement de messages plus anciens si disponible
                // this.loadOlderMessages();
            }
        }

        /**
         * Rechargement de l'éditeur de configuration
         */
        async reloadAiConfigEditor() {
            await this.chatConfig.loadAiConfigPreview(true);
            if (this.chatConfig.aiConfigEditor) {
                this.chatConfig.aiConfigEditor.value = this.chatConfig.aiConfigCache || '';
            }
            if (this.chatConfig.aiConfigFeedback) {
                this.chatConfig.aiConfigFeedback.textContent = 'Rechargé depuis le stockage.';
                this.chatConfig.aiConfigFeedback.style.color = '#94a3b8';
            }
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

