/**
 * chat-config.js - Configuration webapp
 * 
 * Responsabilités :
 * - Gestion des formulaires de configuration
 * - Sauvegarde/chargement de ai_config.json
 * - Gestion des tabs/accordéons
 * - Hotword models table
 */

(function() {
    'use strict';

    class ChatConfig {
        constructor(androidInterface) {
            this.androidInterface = androidInterface;
            this.aiConfigObject = null;
            this.aiConfigCache = '';
            this.hotwordModels = [];
            this.customSelects = [];
            this.core = null; // Référence au core (sera initialisée via initializeWithReferences)
            
            // Références DOM (seront initialisées dans initialize())
            this.aiConfigPreview = null;
            this.aiConfigEditor = null;
            this.aiConfigFeedback = null;
            this.jsonModal = null;
        }

        /**
         * Initialise les références DOM et listeners
         */
        initialize() {
            // Références DOM de base
            this.aiConfigPreview = document.getElementById('aiConfigPreview');
            this.aiConfigEditor = document.getElementById('aiConfigEditor');
            this.aiConfigFeedback = document.getElementById('aiConfigFeedback');
            this.jsonModal = document.getElementById('jsonModal');
        }

        /**
         * Initialise avec toutes les références DOM depuis chat-core
         */
        initializeWithReferences(core) {
            this.core = core;
            // Partager hotwordModels avec core
            if (core.hotwordModels) {
                this.hotwordModels = core.hotwordModels;
            } else {
                core.hotwordModels = this.hotwordModels;
            }
        }

        /**
         * Initialise les selects personnalisés (custom inputs)
         */
        initCustomSelects() {
            if (!this.core) return;
            
            this.customSelects = [
                { select: this.core.configSelectedModel, custom: this.core.configSelectedModelCustom },
                { select: this.core.configCloudProvider, custom: this.core.configCloudProviderCustom },
                { select: this.core.configCloudModel, custom: this.core.configCloudModelCustom },
                { select: this.core.configLocalModel, custom: this.core.configLocalModelCustom },
                { select: this.core.configVisionModel, custom: this.core.configVisionModelCustom },
                { select: this.core.configAudioModel, custom: this.core.configAudioModelCustom },
                { select: this.core.configTtsVoice, custom: this.core.configTtsVoiceCustom }
            ];

            this.customSelects.forEach(({ select, custom }) => {
                if (!select || !custom) return;
                window.ChatUtils.addListener(select, 'change', () => this.toggleCustomInput(select, custom));
                this.toggleCustomInput(select, custom);
            });
        }

        /**
         * Toggle custom input visibility
         */
        toggleCustomInput(select, customInput) {
            if (!select || !customInput) return;
            if (select.value === 'custom') {
                customInput.classList.remove('hidden');
            } else {
                customInput.classList.add('hidden');
            }
        }

        /**
         * Set select value (with custom input support)
         */
        setSelectValue(select, customInput, value) {
            if (!select) return;
            const valueStr = value ?? '';
            const option = Array.from(select.options || []).find(opt => opt.value === valueStr);
            if (option || valueStr === '') {
                select.value = valueStr;
                if (customInput) {
                    customInput.classList.add('hidden');
                    if (select.value !== 'custom') {
                        customInput.value = '';
                    }
                }
            } else {
                select.value = 'custom';
                if (customInput) {
                    customInput.classList.remove('hidden');
                    customInput.value = valueStr;
                }
            }
        }

        /**
         * Get select value (with custom input support)
         */
        getSelectValue(select, customInput) {
            if (!select) return '';
            if (select.value === 'custom') {
                return (customInput?.value || '').trim();
            }
            return select.value || '';
        }

        /**
         * Rend les formulaires de configuration
         */
        renderConfigForms() {
            if (!this.aiConfigObject || !this.core || !this.core.configModeSelect) return;
            const cfg = this.aiConfigObject;
            const hotword = cfg.hotword || {};

            // Tab General : Mode actif + modèle sélectionné
            this.core.configModeSelect.value = cfg.mode || 'cloud';
            this.setSelectValue(this.core.configSelectedModel, this.core.configSelectedModelCustom, cfg.selectedModel || '');

            if (cfg.cloud) {
                this.setSelectValue(this.core.configCloudProvider, this.core.configCloudProviderCustom, cfg.cloud.provider || '');
                if (this.core.configCloudApiKey) {
                    // Stocker la vraie clé API dans un attribut data pour la conserver
                    const apiKey = cfg.cloud.apiKey || '';
                    if (apiKey) {
                        // Stocker la vraie clé dans data-original-key
                        this.core.configCloudApiKey.setAttribute('data-original-key', apiKey);
                        // Masquer la clé API si elle existe (afficher des *)
                        // Utiliser la vraie longueur, pas limitée à 20
                        this.core.configCloudApiKey.value = '*'.repeat(apiKey.length);
                    } else {
                        // Pas de clé, supprimer l'attribut data-original-key
                        this.core.configCloudApiKey.removeAttribute('data-original-key');
                        this.core.configCloudApiKey.value = '';
                    }
                }
                this.setSelectValue(this.core.configCloudModel, this.core.configCloudModelCustom, cfg.cloud.selectedModel || cfg.selectedModel || '');
            }

            const local = cfg.local_server || cfg.localServer;
            if (local) {
                if (this.core.configLocalUrl) this.core.configLocalUrl.value = local.url || '';
                // Modèle local fixé à gemma3-270m.gguf (readonly dans HTML)
                if (this.core.configLocalModel) {
                    this.core.configLocalModel.value = 'gemma3-270m.gguf';
                }
            } else {
                // Initialiser avec valeur par défaut si pas de config
                if (this.core.configLocalModel) {
                    this.core.configLocalModel.value = 'gemma3-270m.gguf';
                }
            }
            
            // ⭐ NOUVEAU: Charger configuration RAG
            if (cfg.rag) {
                if (this.core.configRAGEnabled) {
                    this.core.configRAGEnabled.checked = cfg.rag.enabled === true;
                }
                if (this.core.configEmbeddingModel) {
                    this.core.configEmbeddingModel.value = cfg.rag.embeddingModel || 'nomic-embed-text';
                }
            } else {
                // Valeurs par défaut si RAG non configuré
                if (this.core.configRAGEnabled) {
                    this.core.configRAGEnabled.checked = true; // RAG activé par défaut
                }
                if (this.core.configEmbeddingModel) {
                    this.core.configEmbeddingModel.value = 'nomic-embed-text'; // Modèle par défaut
                }
            }
            
            // ⭐ NOUVEAU: Mettre à jour le statut RAG après chargement
            this.updateRAGStatus();

            if (cfg.webSearch) {
                if (this.core.configWebSearchProvider) {
                    this.core.configWebSearchProvider.value = cfg.webSearch.enabled ? (cfg.webSearch.provider || '') : '';
                }
            }

            if (cfg.thinkingTrace) {
                if (this.core.configThinkingEnabled) {
                    this.core.configThinkingEnabled.value = cfg.thinkingTrace.enabled ? 'auto' : '';
                }
            }

            if (cfg.vision) {
                const visionModel = cfg.vision.enabled ? (cfg.vision.preferredModel || '') : '';
                this.setSelectValue(this.core.configVisionModel, this.core.configVisionModelCustom, visionModel);
            }

            if (cfg.audio) {
                if (this.core.configAudioEngine) {
                    this.core.configAudioEngine.value = cfg.audio.engine || 'whisper_server';
                }
                const audioModel = cfg.audio.enabled ? (cfg.audio.preferredModel || '') : '';
                this.setSelectValue(this.core.configAudioModel, this.core.configAudioModelCustom, audioModel);
                if (this.core.configAudioEndpoint) this.core.configAudioEndpoint.value = cfg.audio.endpoint || '';
                if (this.core.configAudioTimeout) this.core.configAudioTimeout.value = cfg.audio.captureTimeoutMs ?? '';
                if (this.core.configAudioSilenceDb) this.core.configAudioSilenceDb.value = cfg.audio.silenceThresholdDb ?? '';
                if (this.core.configAudioSilenceMs) this.core.configAudioSilenceMs.value = cfg.audio.silenceDurationMs ?? '';
                if (this.core.configAudioDelayAfterHotword) {
                    this.core.configAudioDelayAfterHotword.value = cfg.audio.delayAfterHotwordMs ?? '400';
                }
            }
            this.updateAudioEngineView(this.core);

            if (this.core.configHotwordEnabled) this.core.configHotwordEnabled.checked = !!hotword.enabled;
            if (this.core.configHotwordEngine) this.core.configHotwordEngine.value = hotword.engine || 'openwakeword';
            if (this.core.configHotwordAccessKey) this.core.configHotwordAccessKey.value = hotword.accessKey || '';
            if (this.core.configHotwordKeyword) {
                this.core.configHotwordKeyword.value = hotword.keywordFile || 'hotwords/kit-kat_fr_android_v3_0_0.ppn';
            }
            if (this.core.configHotwordCommMode) {
                this.core.configHotwordCommMode.value = (hotword.commModeDefault || 'respond_ai_outside_kitt');
            }
            if (this.core.configHotwordAutoListen) {
                this.core.configHotwordAutoListen.checked = !!hotword.autoListen;
            }
            if (this.core.configHotwordDebugScores) {
                this.core.configHotwordDebugScores.checked = !!hotword.debugScores;
            }
            if (this.core.configHotwordDebounce) {
                this.core.configHotwordDebounce.value = hotword.debounceMs ?? '2500';
            }

            this.hotwordModels = Array.isArray(hotword.models) ? [...hotword.models] : [];
            const actionsMap = hotword.actions || {};
            if (Array.isArray(this.hotwordModels) && actionsMap && typeof actionsMap === 'object') {
                this.hotwordModels.forEach(m => {
                    if (m?.name && actionsMap[m.name] && !m.action) {
                        m.action = actionsMap[m.name];
                    }
                });
            }
            this.renderHotwordModelsTable();
            this.updateHotwordEngineView(this.core);

            // ⭐ FIX : Initialiser cfg.tts si non défini pour éviter les problèmes de chargement
            if (!cfg.tts) {
                cfg.tts = {};
            }
            if (this.core.configTtsMode) this.core.configTtsMode.value = cfg.tts.mode || '';
            this.setSelectValue(this.core.configTtsVoice, this.core.configTtsVoiceCustom, cfg.tts.voice || '');
            if (this.core.configTtsAutoPlay) {
                // ⭐ FIX : Vérifier explicitement si autoPlay est true (peut être undefined, false, ou true)
                // Utiliser === true pour être sûr que c'est vraiment activé
                this.core.configTtsAutoPlay.checked = cfg.tts.autoPlay === true;
                console.log('TTS autoPlay chargé:', cfg.tts.autoPlay, '→ checkbox checked:', this.core.configTtsAutoPlay.checked);
            }

            if (cfg.systemPromptOverrides) {
                if (this.core.configPromptKitt) this.core.configPromptKitt.value = cfg.systemPromptOverrides.kitt || '';
                if (this.core.configPromptGlados) this.core.configPromptGlados.value = cfg.systemPromptOverrides.glados || '';
                if (this.core.configPromptKarr) this.core.configPromptKarr.value = cfg.systemPromptOverrides.karr || '';
            }

            if (cfg.constraints) {
                if (this.core.configMaxContext) this.core.configMaxContext.value = cfg.constraints.maxContextTokens ?? '';
                if (this.core.configMaxResponse) this.core.configMaxResponse.value = cfg.constraints.maxResponseTokens ?? '';
            }
        }

        /**
         * Rend la table des modèles hotword
         */
        renderHotwordModelsTable() {
            const grid = document.getElementById('hotwordModelsGrid');
            if (!grid) return;
            
            const models = this.hotwordModels || [];
            grid.innerHTML = '';

            if (!models.length) {
                const empty = document.createElement('div');
                empty.className = 'hotword-model-card-empty';
                empty.textContent = 'Aucun modèle configuré. Utilisez "Importer depuis assets" ou "Ajouter un modèle" ci-dessous.';
                grid.appendChild(empty);
                return;
            }

            models.forEach((model, index) => {
                const card = document.createElement('div');
                const isEnabled = model.enabled !== false;
                const name = (model.name || '').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                const asset = (model.asset || model.file || '').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                const threshold = (model.threshold ?? 0.5).toFixed(2);
                
                card.className = 'hotword-model-card';
                if (!isEnabled) {
                    card.classList.add('disabled');
                }
                
                card.innerHTML = `
                    <div class="hotword-model-card-header">
                        <div class="hotword-model-card-title">
                            <input type="checkbox" class="hw-enabled" data-index="${index}" ${isEnabled ? 'checked' : ''}>
                            <div class="hotword-model-card-name">
                                <input type="text" class="hw-name" data-index="${index}" value="${name}" placeholder="Nom du modèle">
                            </div>
                        </div>
                        <button class="hotword-model-card-remove" data-index="${index}">🗑️</button>
                    </div>
                    <div class="hotword-model-card-body">
                        <div class="hotword-model-card-field">
                            <label>Chemin Asset</label>
                            <input type="text" class="hw-asset" data-index="${index}" value="${asset}" 
                                   placeholder="hotwords/.../file.tflite" readonly>
                        </div>
                        <div class="hotword-model-card-field">
                            <label>Seuil de détection</label>
                            <input type="number" class="hw-threshold" data-index="${index}" 
                                   value="${threshold}" step="0.01" min="0" max="1" placeholder="0.55">
                        </div>
                        <div class="hotword-model-card-field">
                            <label>Action lors de la détection</label>
                            <select class="hw-action" data-index="${index}">
                                <option value="respond_ai_outside_kitt"${(model.action || '') === 'respond_ai_outside_kitt' ? ' selected' : ''}>Réponse IA (hors interface KITT)</option>
                                <option value="open_kitt_ui"${(model.action || '') === 'open_kitt_ui' ? ' selected' : ''}>Ouvrir l'interface KITT (legacy)</option>
                            </select>
                        </div>
                    </div>
                `;
                
                grid.appendChild(card);
            });
            
            // Bind checkbox events
            grid.querySelectorAll('input.hw-enabled').forEach(cb => {
                window.ChatUtils.addListener(cb, 'change', (e) => {
                    const idx = Number(e.target.dataset.index);
                    const enabled = e.target.checked;
                    const card = e.target.closest('.hotword-model-card');
                    if (enabled) {
                        card.classList.remove('disabled');
                    } else {
                        card.classList.add('disabled');
                    }
                    this.toggleHotwordEnabled(idx, enabled);
                });
            });
            
            // Bind name input events
            grid.querySelectorAll('input.hw-name').forEach(input => {
                window.ChatUtils.addListener(input, 'blur', (e) => {
                    const idx = Number(e.target.dataset.index);
                    const newName = e.target.value.trim();
                    if (this.hotwordModels[idx] && newName) {
                        this.hotwordModels[idx].name = newName;
                        this.showConfigFeedback(`Nom du modèle mis à jour (non sauvegardé).`);
                    }
                });
            });
            
            // Bind threshold input events
            grid.querySelectorAll('input.hw-threshold').forEach(input => {
                window.ChatUtils.addListener(input, 'change', (e) => {
                    const idx = Number(e.target.dataset.index);
                    const newThreshold = parseFloat(e.target.value);
                    if (this.hotwordModels[idx] && !isNaN(newThreshold)) {
                        this.hotwordModels[idx].threshold = Math.max(0, Math.min(1, newThreshold));
                        e.target.value = this.hotwordModels[idx].threshold.toFixed(2);
                        this.showConfigFeedback(`Seuil mis à jour: ${this.hotwordModels[idx].threshold.toFixed(2)} (non sauvegardé).`);
                    }
                });
            });
            
            // Bind actions selector
            grid.querySelectorAll('select.hw-action').forEach(sel => {
                window.ChatUtils.addListener(sel, 'change', (e) => {
                    const idx = Number(e.target.dataset.index);
                    const val = e.target.value;
                    if (this.hotwordModels[idx]) {
                        this.hotwordModels[idx].action = val;
                        this.showConfigFeedback(`Action '${val}' appliquée à '${this.hotwordModels[idx].name}' (non sauvegardé).`);
                    }
                });
            });
            
            // Bind remove buttons
            grid.querySelectorAll('button.hotword-model-card-remove').forEach(btn => {
                window.ChatUtils.addListener(btn, 'click', (e) => {
                    const idx = Number(e.target.dataset.index);
                    const modelName = this.hotwordModels[idx]?.name || 'sans nom';
                    if (confirm(`Supprimer le modèle "${modelName}" ?`)) {
                        this.removeHotwordModel(idx);
                    }
                });
            });
        }

        /**
         * Sauvegarde la clé API du provider actuel AVANT de changer de provider
         * @param {string} currentProvider - Le provider actuel
         * @param {string} currentApiKey - La clé API actuelle (depuis le champ ou data-original-key)
         */
        async saveCurrentProviderApiKey(currentProvider, currentApiKey) {
            if (!currentApiKey || !currentApiKey.trim() || !currentProvider) {
                console.log('Aucune clé à sauvegarder pour le provider actuel');
                return;
            }
            
            if (!this.aiConfigObject) {
                this.showConfigFeedback('Configuration non chargée', true);
                return;
            }
            
            const cfg = this.aiConfigObject;
            if (!cfg.cloud) cfg.cloud = {};
            
            // Sauvegarder temporairement le provider actuel
            const tempProvider = cfg.cloud.provider;
            
            // Mettre le provider actuel + la clé pour sauvegarder
            cfg.cloud.provider = currentProvider;
            cfg.cloud.apiKey = currentApiKey.trim();
            
            // Sauvegarder dans Android (cela sauvegardera la clé dans le bon endroit selon le provider)
            await this.persistAiConfig(`Clé ${currentProvider} préservée`);
            
            // Restaurer le provider
            cfg.cloud.provider = tempProvider;
            
            console.log(`✅ Clé ${currentProvider} sauvegardée (${currentApiKey.trim().length} chars)`);
        }
        
        /**
         * Sauvegarde uniquement le provider Cloud sans toucher à la clé API
         * Utilisé lors du changement de provider pour préserver la clé actuelle
         */
        async saveCloudProviderOnly(provider, core) {
            if (!this.aiConfigObject) {
                this.showConfigFeedback('Configuration non chargée', true);
                return;
            }
            const cfg = this.aiConfigObject;
            if (!cfg.cloud) cfg.cloud = {};
            
            // ⭐ IMPORTANT: Supprimer la clé API du JSON avant de sauvegarder
            // pour qu'Android ne la modifie pas (elle sera préservée dans SecureConfig/SharedPreferences)
            const currentApiKey = cfg.cloud.apiKey;
            delete cfg.cloud.apiKey;
            
            // Sauvegarder uniquement le provider
            cfg.cloud.provider = provider;
            
            // Persister dans Android (sans la clé API, donc Android ne la modifiera pas)
            await this.persistAiConfig('Provider mis à jour');
            
            // ⭐ IMPORTANT: Restaurer la clé API dans l'objet config après sauvegarde
            // pour que l'UI continue de fonctionner correctement
            if (currentApiKey !== undefined) {
                cfg.cloud.apiKey = currentApiKey;
            }
        }
        
        /**
         * Sauvegarde une section de configuration
         */
        async saveConfigSection(section, core) {
            if (!this.aiConfigObject) {
                this.showConfigFeedback('Configuration non chargée', true);
                return;
            }
            const cfg = this.aiConfigObject;

            switch (section) {
                case 'mode':
                    // Tab General : Sélection du mode actif (Cloud/Local) + modèle par défaut
                    cfg.mode = core.configModeSelect?.value || 'cloud';
                    cfg.selectedModel = this.getSelectValue(core.configSelectedModel, core.configSelectedModelCustom);
                    break;
                case 'cloud':
                    // Tab Cloud : Configuration détaillée des modèles Cloud disponibles
                    cfg.cloud = cfg.cloud || {};
                    cfg.cloud.provider = this.getSelectValue(core.configCloudProvider, core.configCloudProviderCustom);
                    const cloudApiKeyValue = core.configCloudApiKey?.value || '';
                    // Si la valeur contient des *, utiliser la vraie clé stockée dans data-original-key
                    // Sinon, c'est une nouvelle clé saisie par l'utilisateur
                    if (cloudApiKeyValue && !cloudApiKeyValue.includes('*')) {
                        // Nouvelle clé saisie par l'utilisateur (pas de *)
                        // Mettre à jour data-original-key avec la nouvelle clé
                        if (core.configCloudApiKey) {
                            core.configCloudApiKey.setAttribute('data-original-key', cloudApiKeyValue);
                        }
                        cfg.cloud.apiKey = cloudApiKeyValue;
                    } else if (cloudApiKeyValue.includes('*')) {
                        // Clé masquée : récupérer la vraie clé depuis data-original-key
                        const originalKey = core.configCloudApiKey?.getAttribute('data-original-key') || '';
                        if (originalKey) {
                            // Utiliser la vraie clé stockée (non modifiée)
                            cfg.cloud.apiKey = originalKey;
                        } else {
                            // Pas de clé originale, supprimer du JSON pour conserver celle dans SecureConfig
                            delete cfg.cloud.apiKey;
                        }
                    } else if (cloudApiKeyValue === '') {
                        // Champ vide : supprimer la clé
                        if (core.configCloudApiKey) {
                            core.configCloudApiKey.removeAttribute('data-original-key');
                        }
                        cfg.cloud.apiKey = '';
                    } else {
                        // Aucune valeur : supprimer du JSON pour conserver celle dans SecureConfig
                        delete cfg.cloud.apiKey;
                    }
                    cfg.cloud.selectedModel = this.getSelectValue(core.configCloudModel, core.configCloudModelCustom);
                    break;
                case 'local':
                    // Tab Local : Configuration du serveur Ollama local + modèle gemma + RAG
                    cfg.local_server = cfg.local_server || {};
                    cfg.local_server.url = core.configLocalUrl?.value || '';
                    // Modèle local fixé à gemma3-270m.gguf
                    cfg.local_server.model = 'gemma3-270m.gguf';
                    // ⭐ NOUVEAU: Configuration RAG
                    cfg.rag = cfg.rag || {};
                    cfg.rag.enabled = core.configRAGEnabled?.checked === true;
                    cfg.rag.embeddingModel = core.configEmbeddingModel?.value || 'nomic-embed-text';
                    break;
                case 'thinking':
                    cfg.webSearch = cfg.webSearch || {};
                    const webSearchProvider = core.configWebSearchProvider?.value.trim() || '';
                    cfg.webSearch.enabled = !!webSearchProvider;
                    cfg.webSearch.provider = webSearchProvider;
                    cfg.thinkingTrace = cfg.thinkingTrace || {};
                    const thinkingValue = core.configThinkingEnabled?.value || '';
                    cfg.thinkingTrace.enabled = thinkingValue === 'auto';
                    cfg.thinkingTrace.lastMessage = thinkingValue === 'auto' ? '(auto)' : '';
                    break;
                case 'vision':
                    cfg.vision = cfg.vision || {};
                    const visionModel = this.getSelectValue(core.configVisionModel, core.configVisionModelCustom);
                    cfg.vision.enabled = !!visionModel;
                    cfg.vision.preferredModel = visionModel;
                    break;
                case 'audio':
                    cfg.audio = cfg.audio || {};
                    cfg.audio.engine = core.configAudioEngine?.value || 'whisper_server';
                    const audioModel = this.getSelectValue(core.configAudioModel, core.configAudioModelCustom);
                    cfg.audio.enabled = !!audioModel;
                    cfg.audio.preferredModel = audioModel;
                    if (cfg.audio.engine === 'whisper_server') {
                        cfg.audio.endpoint = core.configAudioEndpoint?.value || 'http://127.0.0.1:11400/inference';
                    } else {
                        cfg.audio.endpoint = '';
                    }
                    cfg.audio.captureTimeoutMs = parseInt(core.configAudioTimeout?.value || '8000', 10);
                    cfg.audio.silenceThresholdDb = parseFloat(core.configAudioSilenceDb?.value || '-45');
                    cfg.audio.silenceDurationMs = parseInt(core.configAudioSilenceMs?.value || '1200', 10);
                    cfg.audio.delayAfterHotwordMs = parseInt(core.configAudioDelayAfterHotword?.value || '400', 10);
                    break;
                case 'hotword':
                    cfg.hotword = cfg.hotword || {};
                    cfg.hotword.enabled = core.configHotwordEnabled?.checked || false;
                    cfg.hotword.engine = core.configHotwordEngine?.value || 'openwakeword';
                    cfg.hotword.accessKey = core.configHotwordAccessKey?.value || '';
                    if (core.configHotwordKeyword) {
                        cfg.hotword.keywordFile = core.configHotwordKeyword.value || 'hotwords/kit-kat_fr_android_v3_0_0.ppn';
                    }
                    cfg.hotword.commModeDefault = core.configHotwordCommMode?.value || 'respond_ai_outside_kitt';
                    cfg.hotword.autoListen = !!core.configHotwordAutoListen?.checked;
                    cfg.hotword.debugScores = !!core.configHotwordDebugScores?.checked;
                    if (core.configHotwordDebounce) {
                        cfg.hotword.debounceMs = parseInt(core.configHotwordDebounce.value || '2500', 10);
                    }
                    if (Array.isArray(this.hotwordModels)) {
                        const actions = {};
                        this.hotwordModels.forEach(m => {
                            if (m?.name && m.action) {
                                actions[m.name] = m.action;
                            }
                        });
                        cfg.hotword.actions = actions;
                    }
                    cfg.hotword.models = this.hotwordModels || [];
                    break;
                case 'tts':
                    cfg.tts = cfg.tts || {};
                    cfg.tts.mode = core.configTtsMode?.value || '';
                    cfg.tts.voice = this.getSelectValue(core.configTtsVoice, core.configTtsVoiceCustom);
                    // ⭐ FIX : Toujours inclure autoPlay (même si false) pour persistance entre sessions
                    cfg.tts.autoPlay = core.configTtsAutoPlay?.checked === true;
                    console.log('TTS autoPlay sauvegardé:', cfg.tts.autoPlay, '(checkbox checked:', core.configTtsAutoPlay?.checked, ')');
                    break;
                case 'prompts':
                    cfg.systemPromptOverrides = cfg.systemPromptOverrides || {};
                    cfg.systemPromptOverrides.kitt = core.configPromptKitt?.value || '';
                    cfg.systemPromptOverrides.glados = core.configPromptGlados?.value || '';
                    cfg.systemPromptOverrides.karr = core.configPromptKarr?.value || '';
                    break;
                case 'constraints':
                    cfg.constraints = cfg.constraints || {};
                    cfg.constraints.maxContextTokens = parseInt(core.configMaxContext?.value || '8192', 10);
                    cfg.constraints.maxResponseTokens = parseInt(core.configMaxResponse?.value || '2048', 10);
                    break;
                default:
                    this.showConfigFeedback('Section inconnue', true);
                    return;
            }

            await this.persistAiConfig('Section sauvegardée');
        }

        /**
         * ⭐ NOUVEAU: Met à jour l'indicateur de statut RAG
         * Vérifie à la fois le mode actuel (select) et le statut depuis Android
         */
        async updateRAGStatus() {
            try {
                const badge = document.getElementById('ragStatusBadge');
                const message = document.getElementById('ragStatusMessage');
                
                if (!badge || !message) return;
                
                // Récupérer le statut depuis Android (détection automatique si Cloud supporte embeddings)
                let ragEnabled = false;
                let ragAvailable = false;
                let useCloud = false;
                let reason = '';
                
                if (this.androidInterface?.getRAGStatus) {
                    try {
                        const statusJson = this.androidInterface.getRAGStatus();
                        const status = JSON.parse(statusJson);
                        ragEnabled = status.enabled === true;
                        ragAvailable = status.available === true;
                        useCloud = status.useCloud === true;
                        reason = status.reason || '';
                    } catch (e) {
                        console.warn('Erreur récupération statut RAG depuis Android:', e);
                    }
                }
                
                // Mettre à jour le badge
                if (ragEnabled && ragAvailable) {
                    badge.textContent = '✅ Disponible';
                    badge.style.color = '#10b981';
                } else if (ragEnabled && !ragAvailable) {
                    badge.textContent = '⚠️ Non disponible';
                    badge.style.color = '#f59e0b';
                } else {
                    badge.textContent = '⏸️ Désactivé';
                    badge.style.color = '#64748b';
                }
                
                // ⭐ FIX: Ne plus désactiver automatiquement RAG avec Cloud si Hugging Face est configuré
                // Vérifier si Hugging Face est configuré
                const hfApiKey = this.aiConfigObject?.cloud?.huggingfaceApiKey || 
                    (this.androidInterface?.readAiConfigJson ? 
                        (() => {
                            try {
                                const config = JSON.parse(this.androidInterface.readAiConfigJson());
                                return config?.cloud?.huggingfaceApiKey || '';
                            } catch { return ''; }
                        })() : '');
                const hasHuggingFace = hfApiKey && hfApiKey.length > 0;
                
                // ⭐ NOUVEAU: Si Cloud + Hugging Face configuré, permettre RAG (ne pas désactiver)
                if (useCloud && !ragAvailable && !hasHuggingFace && this.core?.configRAGEnabled) {
                    // Cloud activé sans Hugging Face → RAG ne fonctionne pas, désactiver automatiquement
                    if (this.core.configRAGEnabled.checked) {
                        // Sauvegarder l'état précédent pour réactivation si on repasse en Local
                        if (!this.core.configRAGEnabled.hasAttribute('data-was-enabled')) {
                            this.core.configRAGEnabled.setAttribute('data-was-enabled', 'true');
                        }
                        this.core.configRAGEnabled.checked = false;
                        ragEnabled = false; // Mettre à jour la variable locale
                        
                        // Sauvegarder immédiatement pour persister la désactivation
                        if (this.aiConfigObject) {
                            this.aiConfigObject.rag = this.aiConfigObject.rag || {};
                            this.aiConfigObject.rag.enabled = false;
                            // Sauvegarder en arrière-plan (non-bloquant)
                            this.persistAiConfig('RAG désactivé automatiquement (configurez Hugging Face pour activer RAG avec Cloud)').catch(e => {
                                console.warn('Erreur sauvegarde auto-désactivation RAG:', e);
                            });
                        }
                    }
                } else if (!useCloud && this.core?.configRAGEnabled) {
                    // Local activé → Réactiver RAG si elle était activée avant
                    if (this.core.configRAGEnabled.getAttribute('data-was-enabled') === 'true') {
                        this.core.configRAGEnabled.checked = true;
                        this.core.configRAGEnabled.removeAttribute('data-was-enabled');
                        ragEnabled = true; // Mettre à jour la variable locale
                        if (this.aiConfigObject) {
                            this.aiConfigObject.rag = this.aiConfigObject.rag || {};
                            this.aiConfigObject.rag.enabled = true;
                            // Sauvegarder en arrière-plan (non-bloquant)
                            this.persistAiConfig('RAG réactivé automatiquement (Ollama Local)').catch(e => {
                                console.warn('Erreur sauvegarde auto-réactivation RAG:', e);
                            });
                        }
                    }
                }
                
                // Afficher le message d'avertissement
                if (useCloud && !ragAvailable) {
                    // Vérifier si Hugging Face est configuré
                    const hfApiKey = this.androidInterface?.readAiConfigJson ? 
                        (() => {
                            try {
                                const config = JSON.parse(this.androidInterface.readAiConfigJson());
                                return config?.cloud?.huggingfaceApiKey || '';
                            } catch { return ''; }
                        })() : '';
                    
                    if (hfApiKey && hfApiKey.length > 0) {
                        // Hugging Face configuré mais non disponible
                        message.style.display = 'block';
                        message.style.backgroundColor = '#fef3c7';
                        message.style.color = '#92400e';
                        message.style.border = '1px solid #fbbf24';
                        message.textContent = '⚠️ RAG avec Hugging Face non disponible. Vérifiez votre clé API Hugging Face.';
                    } else {
                        // Pas de Hugging Face configuré
                        message.style.display = 'block';
                        message.style.backgroundColor = '#dbeafe';
                        message.style.color = '#1e40af';
                        message.style.border = '1px solid #60a5fa';
                        message.textContent = '💡 RAG avec Ollama Cloud: Configurez une clé API Hugging Face pour activer RAG. Ollama Cloud ne supporte pas les embeddings, mais Hugging Face peut les fournir.';
                    }
                } else if (!useCloud && ragEnabled && !ragAvailable && reason) {
                    // Message pour Local si problème
                    message.style.display = 'block';
                    message.style.backgroundColor = '#fee2e2';
                    message.style.color = '#991b1b';
                    message.style.border = '1px solid #f87171';
                    message.textContent = `⚠️ ${reason}`;
                } else {
                    message.style.display = 'none';
                }
                
                // Désactiver/activer la checkbox selon le mode
                if (this.core?.configRAGEnabled) {
                    if (useCloud && !ragAvailable) {
                        // Cloud activé mais embeddings non disponibles
                        // Vérifier si Hugging Face est configuré
                        const hfApiKey = this.androidInterface?.readAiConfigJson ? 
                            (() => {
                                try {
                                    const config = JSON.parse(this.androidInterface.readAiConfigJson());
                                    return config?.cloud?.huggingfaceApiKey || '';
                                } catch { return ''; }
                            })() : '';
                        
                        if (!hfApiKey || hfApiKey.length === 0) {
                            // Pas de Hugging Face → permettre activation mais afficher message
                            this.core.configRAGEnabled.disabled = false;
                        } else {
                            // Hugging Face configuré mais non disponible → désactiver
                            this.core.configRAGEnabled.disabled = true;
                        }
                        if (this.core.configEmbeddingModel) {
                            this.core.configEmbeddingModel.disabled = true;
                        }
                    } else {
                        // Local activé OU Cloud avec embeddings disponibles → permettre RAG
                        this.core.configRAGEnabled.disabled = false;
                        if (this.core.configEmbeddingModel) {
                            this.core.configEmbeddingModel.disabled = false;
                        }
                    }
                }
                
            } catch (error) {
                console.error('updateRAGStatus error', error);
            }
        }
        
        /**
         * Persiste la configuration AI
         */
        async persistAiConfig(successMessage = 'Configuration sauvegardée') {
            try {
                const content = JSON.stringify(this.aiConfigObject, null, 2);
                const result = await this.pushAiConfigContent(content);
                if (result === true || result === 'OK') {
                    this.aiConfigCache = content;
                    this.renderAiConfigPreview();
                    this.showConfigFeedback(successMessage, false);
                    // ⭐ NOUVEAU: Mettre à jour le statut RAG après sauvegarde
                    this.updateRAGStatus();
                } else {
                    this.showConfigFeedback(result || 'Erreur de sauvegarde', true);
                }
            } catch (error) {
                console.error('persistAiConfig error', error);
                this.showConfigFeedback(error.message, true);
            }
        }

        /**
         * Charge l'aperçu de la configuration IA
         */
        async loadAiConfigPreview(force = false) {
            if (!this.aiConfigPreview) return;

            try {
                let content = '';
                if (this.androidInterface?.readAiConfigJson) {
                    content = this.androidInterface.readAiConfigJson() || '';
                } else {
                    const response = await fetch(window.ChatUtils.getApiUrl('/api/config/ai'), {
                        method: 'GET',
                        cache: force ? 'no-store' : 'default',
                    });
                    if (!response.ok) {
                        throw new Error(`HTTP ${response.status}`);
                    }
                    const data = await response.json();
                    content = data?.content || '';
                }

                this.aiConfigCache = content || '';
                if (content && content.trim().length > 0) {
                    try {
                        this.aiConfigObject = JSON.parse(content);
                        if (this.core) {
                            this.renderConfigForms();
                        }
                    } catch (parseError) {
                        console.warn('JSON invalide détecté, affichage brut.', parseError);
                        this.aiConfigObject = null;
                    }
                } else {
                    this.aiConfigObject = null;
                }
                this.renderAiConfigPreview();
            } catch (error) {
                console.error('Erreur lecture ai_config.json', error);
                if (this.aiConfigPreview) {
                    this.aiConfigPreview.textContent = `Erreur lecture ai_config: ${error.message}`;
                }
            }
        }

        /**
         * Rend l'aperçu de la configuration
         */
        renderAiConfigPreview() {
            if (!this.aiConfigPreview) return;
            if (this.aiConfigObject) {
                this.aiConfigPreview.textContent = JSON.stringify(this.aiConfigObject, null, 2);
            } else if (this.aiConfigCache) {
                this.aiConfigPreview.textContent = this.aiConfigCache;
            } else {
                this.aiConfigPreview.textContent = 'Aucune configuration chargée.';
            }
        }

        /**
         * Ouvre l'éditeur de configuration IA
         */
        async openAiConfigEditor() {
            if (!this.jsonModal || !this.aiConfigEditor) return;

            await this.loadAiConfigPreview(true);
            this.aiConfigEditor.value = this.aiConfigCache || '';
            
            if (this.aiConfigFeedback) {
                this.aiConfigFeedback.textContent = '';
                this.aiConfigFeedback.style.color = '#94a3b8';
            }
            
            this.jsonModal.style.display = 'block';
        }

        /**
         * Ferme l'éditeur de configuration
         */
        closeAiConfigEditor() {
            if (this.jsonModal) {
                this.jsonModal.style.display = 'none';
            }
        }

        /**
         * Sauvegarde la configuration IA (depuis l'éditeur)
         */
        async saveAiConfig() {
            if (!this.aiConfigEditor) return;
            
            const content = this.aiConfigEditor.value || '';
            
            // Validation JSON
            try {
                this.aiConfigObject = JSON.parse(content);
            } catch (error) {
                if (this.aiConfigFeedback) {
                    this.aiConfigFeedback.textContent = `JSON invalide: ${error.message}`;
                    this.aiConfigFeedback.style.color = '#f87171';
                }
                return;
            }
            
            const result = await this.pushAiConfigContent(content);
            if (result === true || result === 'OK') {
                this.aiConfigCache = content;
                if (this.core) {
                    this.renderConfigForms();
                }
                this.renderAiConfigPreview();
                if (this.aiConfigFeedback) {
                    this.aiConfigFeedback.textContent = 'Configuration sauvegardée avec succès.';
                    this.aiConfigFeedback.style.color = '#4ade80';
                }
                setTimeout(() => this.closeAiConfigEditor(), 500);
            } else if (this.aiConfigFeedback) {
                this.aiConfigFeedback.textContent = result || 'Erreur inconnue lors de la sauvegarde.';
                this.aiConfigFeedback.style.color = '#f87171';
            }
        }

        /**
         * Push le contenu de la configuration vers Android
         */
        async pushAiConfigContent(content) {
            try {
                if (this.androidInterface?.writeAiConfigJson) {
                    const result = this.androidInterface.writeAiConfigJson(content);
                    return result === true || result === 'OK' ? true : result;
                } else {
                    const response = await fetch(window.ChatUtils.getApiUrl('/api/config/ai'), {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ content })
                    });
                    if (!response.ok) {
                        throw new Error(`HTTP ${response.status}`);
                    }
                    return true;
                }
            } catch (error) {
                console.error('Erreur sauvegarde ai_config.json', error);
                return error.message;
            }
        }

        /**
         * Affiche un feedback de configuration
         */
        showConfigFeedback(message, isError = false) {
            if (!this.aiConfigFeedback) return;
            this.aiConfigFeedback.textContent = message;
            this.aiConfigFeedback.style.color = isError ? '#f87171' : '#94a3b8';
        }

        /**
         * Initialise les tabs de configuration
         */
        initConfigTabs() {
            const tabsContainer = document.getElementById('configTabs');
            if (!tabsContainer) return;
            
            const tabs = tabsContainer.querySelectorAll('.config-tab');
            const contents = document.querySelectorAll('.config-tab-content');
            
            tabs.forEach(tab => {
                window.ChatUtils.addListener(tab, 'click', () => {
                    const targetTab = tab.dataset.tab;
                    
                    // Remove active from all tabs and contents
                    tabs.forEach(t => t.classList.remove('active'));
                    contents.forEach(c => c.classList.remove('active'));
                    
                    // Add active to clicked tab and corresponding content
                    tab.classList.add('active');
                    const targetContent = document.querySelector(`.config-tab-content[data-content="${targetTab}"]`);
                    if (targetContent) {
                        targetContent.classList.add('active');
                    }
                });
            });
        }

        /**
         * Hotword: Start
         */
        hotwordStart() {
            try {
                if (this.androidInterface?.hotwordStart) {
                    this.androidInterface.hotwordStart();
                    this.showConfigFeedback('Hotword: démarrage demandé.');
                } else {
                    this.showConfigFeedback('Interface Android indisponible', true);
                }
            } catch (e) {
                console.error('hotwordStart error', e);
                this.showConfigFeedback('Erreur démarrage hotword', true);
            }
        }

        /**
         * Hotword: Stop
         */
        hotwordStop() {
            try {
                if (this.androidInterface?.hotwordStop) {
                    this.androidInterface.hotwordStop();
                    this.showConfigFeedback('Hotword: arrêt demandé.');
                } else {
                    this.showConfigFeedback('Interface Android indisponible', true);
                }
            } catch (e) {
                console.error('hotwordStop error', e);
                this.showConfigFeedback('Erreur arrêt hotword', true);
            }
        }

        /**
         * Hotword: Restart
         */
        hotwordRestart() {
            try {
                if (this.androidInterface?.hotwordRestart) {
                    this.androidInterface.hotwordRestart();
                    this.showConfigFeedback('Hotword: redémarrage demandé.');
                } else {
                    this.showConfigFeedback('Interface Android indisponible', true);
                }
            } catch (e) {
                console.error('hotwordRestart error', e);
                this.showConfigFeedback('Erreur redémarrage hotword', true);
            }
        }

        /**
         * Update hotword engine view (show/hide Porcupine fields)
         */
        updateHotwordEngineView(core) {
            const isPorcupine = (core.configHotwordEngine?.value === 'porcupine');
            if (core.porcupineFields) {
                core.porcupineFields.forEach(field => {
                    if (!field) return;
                    if (isPorcupine) {
                        field.classList.remove('hidden');
                    } else {
                        field.classList.add('hidden');
                    }
                });
            }
        }

        /**
         * Update audio engine view (show/hide Whisper fields)
         */
        updateAudioEngineView(core) {
            const engine = core.configAudioEngine?.value || 'whisper_server';
            const whisperOnlyElements = document.querySelectorAll('.engine-whisper-only');
            const legacyOnlyElements = document.querySelectorAll('.engine-legacy-only');

            whisperOnlyElements.forEach(el => {
                if (engine === 'whisper_server') {
                    el.classList.remove('hidden');
                } else {
                    // Cacher si "disabled" ou autre moteur
                    el.classList.add('hidden');
                }
            });

            legacyOnlyElements.forEach(el => {
                // Les éléments "legacy" ne sont plus utilisés (Google Speech via Intent standard)
                el.classList.add('hidden');
            });
        }

        /**
         * Handle add hotword model
         */
        handleAddHotwordModel(core) {
            if (!this.hotwordModels) this.hotwordModels = [];
            const name = (core.hotwordNewName?.value || '').trim();
            const asset = (core.hotwordNewAsset?.value || '').trim();
            const threshold = parseFloat(core.hotwordNewThreshold?.value || '0.5');

            if (!name || !asset) {
                this.showConfigFeedback('Veuillez remplir Nom et Asset.', true);
                return;
            }

            this.hotwordModels.push({
                name,
                asset,
                threshold: isNaN(threshold) ? 0.5 : threshold,
                enabled: true
            });
            if (core.hotwordNewName) core.hotwordNewName.value = '';
            if (core.hotwordNewAsset) core.hotwordNewAsset.value = '';
            if (core.hotwordNewThreshold) core.hotwordNewThreshold.value = '';
            this.renderHotwordModelsTable();
            this.showConfigFeedback(`Modèle '${name}' ajouté (non sauvegardé).`);
        }

        /**
         * Remove hotword model
         */
        removeHotwordModel(index) {
            if (!this.hotwordModels || index < 0 || index >= this.hotwordModels.length) return;
            const removed = this.hotwordModels.splice(index, 1);
            this.renderHotwordModelsTable();
            if (removed[0]) {
                this.showConfigFeedback(`Modèle '${removed[0].name}' retiré (non sauvegardé).`);
            }
        }

        /**
         * Toggle hotword enabled
         */
        toggleHotwordEnabled(index, enabled) {
            if (!this.hotwordModels || index < 0 || index >= this.hotwordModels.length) return;
            this.hotwordModels[index].enabled = !!enabled;
            this.showConfigFeedback(`Modèle '${this.hotwordModels[index].name}' ${enabled ? 'activé' : 'désactivé'} (non sauvegardé).`);
        }

        /**
         * Import hotword assets
         */
        async importHotwordAssets(core) {
            try {
                let payload = null;
                if (this.androidInterface?.listHotwordAssets) {
                    const response = this.androidInterface.listHotwordAssets();
                    payload = typeof response === 'string' ? JSON.parse(response) : response;
                } else {
                    const res = await fetch(window.ChatUtils.getApiUrl('/api/hotword/assets'));
                    if (!res.ok) {
                        throw new Error(`HTTP ${res.status}`);
                    }
                    payload = await res.json();
                }

                const assets = payload?.assets || [];
                if (!assets.length) {
                    this.showConfigFeedback('Aucun modèle détecté dans assets.', true);
                    return;
                }

                if (!this.hotwordModels) this.hotwordModels = [];
                const existing = new Set(this.hotwordModels.map(model => model.asset));
                let added = 0;
                assets.forEach(asset => {
                    if (!asset || !asset.asset || existing.has(asset.asset)) {
                        return;
                    }
                    this.hotwordModels.push({
                        name: asset.name || asset.asset,
                        asset: asset.asset,
                        threshold: asset.threshold ?? 0.55,
                        enabled: true
                    });
                    existing.add(asset.asset);
                    added++;
                });

                this.renderHotwordModelsTable();
                if (added > 0) {
                    this.showConfigFeedback(`${added} modèle(s) importé(s). Sauvegardez pour appliquer.`);
                } else {
                    this.showConfigFeedback('Tous les modèles sont déjà dans la liste.');
                }
            } catch (error) {
                console.error('importHotwordAssets error', error);
                this.showConfigFeedback(`Erreur import: ${error.message}`, true);
            }
        }
    }

    // Export global
    window.ChatConfig = ChatConfig;
    console.log('✅ ChatConfig chargé');
})();
