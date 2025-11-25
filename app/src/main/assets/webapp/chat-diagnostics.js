(function() {
    'use strict';

    class ChatDiagnostics {
        constructor(androidInterface, chatUI) {
            this.androidInterface = androidInterface;
            this.chatUI = chatUI;
            this.servicesStatusGrid = document.getElementById('servicesStatusGrid');
            this.systemInfoGrid = document.getElementById('systemInfoGrid');
            this.ragStatusGrid = document.getElementById('ragStatusGrid');
            this.conversationsStatsGrid = document.getElementById('conversationsStatsGrid');
            this.logFileContainer = document.getElementById('logFileContainer');
            this.diagnosticLogsContainer = document.getElementById('diagnosticLogsContainer');
            this.diagnosticsRefreshBtn = document.getElementById('diagnosticsRefreshBtn');
            this.diagnosticsGenerateHtmlBtn = document.getElementById('diagnosticsGenerateHtmlBtn');
            this.diagnosticsOpenHtmlBtn = document.getElementById('diagnosticsOpenHtmlBtn');

            this.setupEventListeners();
            console.log('📊 ChatDiagnostics module initialized.');
        }

        setupEventListeners() {
            if (this.diagnosticsRefreshBtn) {
                window.ChatUtils.addListener(this.diagnosticsRefreshBtn, 'click', () => this.loadDiagnostics());
            }
            if (this.diagnosticsGenerateHtmlBtn) {
                window.ChatUtils.addListener(this.diagnosticsGenerateHtmlBtn, 'click', () => this.generateDiagnosticsHtml());
            }
            if (this.diagnosticsOpenHtmlBtn) {
                window.ChatUtils.addListener(this.diagnosticsOpenHtmlBtn, 'click', () => this.openDiagnosticsHtml());
            }
        }

        async loadDiagnostics() {
            if (!this.androidInterface) {
                console.warn('Android interface not available for diagnostics.');
                return;
            }

            this.chatUI.showToast('Chargement des diagnostics...', 'info');

            try {
                // Charger les statuts des services
                await this.loadServicesStatus();
                
                // ⭐ NOUVEAU: Charger le statut RAG/Ollama
                await this.loadRAGStatus();
                
                // ⭐ NOUVEAU: Charger les statistiques conversations
                await this.loadConversationsStats();
                
                // Charger les informations système
                await this.loadSystemInfo();
                
                // Charger les logs fichier
                await this.loadLogFile();
                
                // Charger les logs mémoire
                await this.loadDiagnosticLogs();

                // ⭐ NOUVEAU: Vérifier si le fichier diagnostics.html existe et activer le bouton
                await this.checkDiagnosticsHtmlExists();

                this.chatUI.showToast('✅ Diagnostics chargés', 'success');
            } catch (e) {
                console.error('Error loading diagnostics:', e);
                this.chatUI.showToast(`❌ Erreur de chargement: ${e.message}`, 'error');
            }
        }

        async loadServicesStatus() {
            if (!this.androidInterface || !this.androidInterface.getServicesStatus) {
                console.warn('Android interface for services status not available.');
                return;
            }

            try {
                const statusJson = await this.androidInterface.getServicesStatus();
                const status = JSON.parse(statusJson);
                this.renderServicesStatus(status);
            } catch (e) {
                console.error('Error loading services status:', e);
                this.servicesStatusGrid.innerHTML = `<div style="text-align: center; padding: 20px; color: #ef4444;">❌ Erreur de chargement: ${e.message}</div>`;
            }
        }

        renderServicesStatus(status) {
            if (!this.servicesStatusGrid) return;

            this.servicesStatusGrid.innerHTML = '';

            const services = [
                { key: 'httpServer', name: 'HTTP Server', icon: '🌐' },
                { key: 'webSocketServer', name: 'WebSocket Server', icon: '🔌' },
                { key: 'aiService', name: 'AI Service', icon: '🤖' },
                { key: 'hotwordService', name: 'Hotword Service', icon: '🎤' },
                { key: 'sttService', name: 'STT Service', icon: '🎧' },
                { key: 'ttsService', name: 'TTS Service', icon: '🔊' }
            ];

            services.forEach(service => {
                const serviceStatus = status[service.key];
                if (!serviceStatus) return;

                const isActive = serviceStatus.running !== undefined 
                    ? serviceStatus.running 
                    : serviceStatus.healthy !== undefined 
                        ? serviceStatus.healthy 
                        : serviceStatus.available !== undefined 
                            ? serviceStatus.available 
                            : false;

                const card = document.createElement('div');
                card.className = 'mini-card';
                card.style = `
                    background: rgba(30, 41, 59, 0.5);
                    border: 1px solid rgba(148, 163, 184, 0.2);
                    border-radius: 8px;
                    padding: 16px;
                `;

                const statusClass = isActive ? 'status-active' : 'status-inactive';
                const statusText = serviceStatus.status || (isActive ? 'Actif' : 'Inactif');

                card.innerHTML = `
                    <div class="mini-card-header">
                        <span class="mini-card-label">${service.icon} ${service.name}</span>
                        <span class="mini-card-status ${statusClass}"></span>
                    </div>
                    <div class="mini-card-value ${statusClass}" style="color: ${isActive ? '#10b981' : '#ef4444'}; font-size: 14px; font-weight: 600; margin-top: 8px;">
                        ${statusText}
                    </div>
                    ${serviceStatus.port !== undefined ? `<div style="font-size: 11px; color: #64748b; margin-top: 4px;">Port: ${serviceStatus.port}</div>` : ''}
                    ${serviceStatus.clientsCount !== undefined ? `<div style="font-size: 11px; color: #64748b; margin-top: 4px;">Clients: ${serviceStatus.clientsCount}</div>` : ''}
                    ${serviceStatus.type ? `<div style="font-size: 11px; color: #64748b; margin-top: 4px;">Type: ${serviceStatus.type}</div>` : ''}
                `;

                this.servicesStatusGrid.appendChild(card);
            });
        }

        async loadSystemInfo() {
            if (!this.androidInterface || !this.androidInterface.getSystemInfo) {
                console.warn('Android interface for system info not available.');
                return;
            }

            try {
                const infoJson = await this.androidInterface.getSystemInfo();
                const info = JSON.parse(infoJson);
                this.renderSystemInfo(info);
            } catch (e) {
                console.error('Error loading system info:', e);
                this.systemInfoGrid.innerHTML = `<div style="text-align: center; padding: 20px; color: #ef4444;">❌ Erreur de chargement: ${e.message}</div>`;
            }
        }

        renderSystemInfo(info) {
            if (!this.systemInfoGrid) return;

            this.systemInfoGrid.innerHTML = '';

            const systemCards = [
                { 
                    title: 'Batterie', 
                    value: `${info.batteryLevel || 'N/A'}%`, 
                    status: info.batteryStatus || 'Inconnu',
                    icon: '🔋'
                },
                { 
                    title: 'RAM', 
                    value: `${info.ramUsagePercent || 'N/A'}%`, 
                    status: `${info.ramUsedMB || 'N/A'}MB / ${info.ramTotalMB || 'N/A'}MB`,
                    icon: '💾'
                },
                { 
                    title: 'Stockage', 
                    value: `${info.storageUsagePercent || 'N/A'}%`, 
                    status: `${info.storageUsedGB || 'N/A'}GB / ${info.storageTotalGB || 'N/A'}GB`,
                    icon: '📦'
                },
                { 
                    title: 'Réseau', 
                    value: info.networkType || 'N/A', 
                    status: info.hasInternet ? 'Internet disponible' : 'Internet indisponible',
                    icon: '🌐',
                    active: info.hasInternet
                }
            ];

            systemCards.forEach(cardInfo => {
                const card = document.createElement('div');
                card.className = 'mini-card';
                card.style = `
                    background: rgba(30, 41, 59, 0.5);
                    border: 1px solid rgba(148, 163, 184, 0.2);
                    border-radius: 8px;
                    padding: 16px;
                `;

                const valueColor = cardInfo.active !== undefined 
                    ? (cardInfo.active ? '#10b981' : '#ef4444')
                    : '#e2e8f0';

                card.innerHTML = `
                    <div class="mini-card-header">
                        <span class="mini-card-label">${cardInfo.icon} ${cardInfo.title}</span>
                    </div>
                    <div class="mini-card-value" style="color: ${valueColor}; font-size: 16px; font-weight: 600; margin-top: 8px;">
                        ${cardInfo.value}
                    </div>
                    <div style="font-size: 11px; color: #64748b; margin-top: 4px;">
                        ${cardInfo.status}
                    </div>
                `;

                this.systemInfoGrid.appendChild(card);
            });

            // Ajouter les informations device
            const deviceInfo = document.createElement('div');
            deviceInfo.style = `
                grid-column: 1 / -1;
                background: rgba(15, 23, 42, 0.3);
                border: 1px solid rgba(148, 163, 184, 0.2);
                border-radius: 8px;
                padding: 16px;
                margin-top: 12px;
            `;

            deviceInfo.innerHTML = `
                <div style="font-size: 12px; color: #94a3b8; margin-bottom: 8px; font-weight: 500;">📱 Device</div>
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 8px;">
                    <div><strong style="color: #94a3b8;">Modèle:</strong> <span style="color: #e2e8f0;">${info.deviceModel || 'N/A'}</span></div>
                    <div><strong style="color: #94a3b8;">Android:</strong> <span style="color: #e2e8f0;">${info.androidVersion || 'N/A'} (SDK ${info.sdkInt || 'N/A'})</span></div>
                    <div><strong style="color: #94a3b8;">Fabricant:</strong> <span style="color: #e2e8f0;">${info.manufacturer || 'N/A'}</span></div>
                    <div><strong style="color: #94a3b8;">Date/Heure:</strong> <span style="color: #e2e8f0;">${info.currentDate || 'N/A'}</span></div>
                </div>
            `;

            this.systemInfoGrid.appendChild(deviceInfo);
        }

        async loadLogFile() {
            if (!this.androidInterface || !this.androidInterface.getLogFileContent) {
                console.warn('Android interface for log file not available.');
                return;
            }

            try {
                const logLinesJson = await this.androidInterface.getLogFileContent();
                const logLines = JSON.parse(logLinesJson);
                this.renderLogFile(logLines);
            } catch (e) {
                console.error('Error loading log file:', e);
                this.logFileContainer.innerHTML = `<div style="text-align: center; padding: 20px; color: #ef4444;">❌ Erreur de chargement: ${e.message}</div>`;
            }
        }

        renderLogFile(logLines) {
            if (!this.logFileContainer) return;

            if (!logLines || logLines.length === 0) {
                this.logFileContainer.innerHTML = `
                    <div style="text-align: center; padding: 40px; color: #64748b;">
                        <div style="font-size: 48px; margin-bottom: 12px;">📄</div>
                        <div>Aucun log disponible</div>
                    </div>
                `;
                return;
            }

            const logsHtml = logLines.map(line => {
                const escapedLine = this.escapeHtml(line);
                return `<div style="color: #94a3b8; font-family: 'Courier New', monospace; font-size: 12px; line-height: 1.4; margin-bottom: 4px; word-wrap: break-word;">${escapedLine}</div>`;
            }).join('');

            this.logFileContainer.innerHTML = `
                <div style="font-size: 12px; color: #64748b; margin-bottom: 8px;">
                    ${logLines.length} ligne(s) affichée(s)
                </div>
                <div style="max-height: 400px; overflow-y: auto;">
                    ${logsHtml}
                </div>
            `;
        }

        async loadDiagnosticLogs() {
            if (!this.androidInterface || !this.androidInterface.getDiagnosticLogs) {
                console.warn('Android interface for diagnostic logs not available.');
                return;
            }

            try {
                const logsJson = await this.androidInterface.getDiagnosticLogs();
                const logs = JSON.parse(logsJson);
                this.renderDiagnosticLogs(logs);
            } catch (e) {
                console.error('Error loading diagnostic logs:', e);
                this.diagnosticLogsContainer.innerHTML = `<div style="text-align: center; padding: 20px; color: #ef4444;">❌ Erreur de chargement: ${e.message}</div>`;
            }
        }

        renderDiagnosticLogs(logs) {
            if (!this.diagnosticLogsContainer) return;

            if (!logs || logs.length === 0) {
                this.diagnosticLogsContainer.innerHTML = `
                    <div style="text-align: center; padding: 40px; color: #64748b;">
                        <div style="font-size: 48px; margin-bottom: 12px;">🧠</div>
                        <div>Aucun log en mémoire</div>
                    </div>
                `;
                return;
            }

            const logsHtml = logs.map(log => {
                const escapedLog = this.escapeHtml(log);
                return `<div style="color: #94a3b8; font-family: 'Courier New', monospace; font-size: 12px; line-height: 1.4; margin-bottom: 4px; word-wrap: break-word;">${escapedLog}</div>`;
            }).join('');

            this.diagnosticLogsContainer.innerHTML = `
                <div style="font-size: 12px; color: #64748b; margin-bottom: 8px;">
                    ${logs.length} entrée(s) affichée(s)
                </div>
                <div style="max-height: 400px; overflow-y: auto;">
                    ${logsHtml}
                </div>
            `;
        }

        async generateDiagnosticsHtml() {
            if (!this.androidInterface || !this.androidInterface.generateDiagnosticsHtml) {
                this.chatUI.showToast('Interface Android non disponible pour la génération HTML.', 'error');
                return;
            }

            this.chatUI.showToast('Génération de la page HTML...', 'info');

            try {
                const savedPath = await this.androidInterface.generateDiagnosticsHtml();
                
                if (savedPath && savedPath.startsWith('/')) {
                    // Extraire le nom de fichier du chemin
                    const fileName = savedPath.split('/').pop();
                    
                    this.chatUI.showToast(`✅ Page HTML générée: ${fileName}`, 'success');
                    
                    // Activer le bouton pour ouvrir la page HTML
                    if (this.diagnosticsOpenHtmlBtn) {
                        this.diagnosticsOpenHtmlBtn.disabled = false;
                        this.diagnosticsOpenHtmlBtn.dataset.fileName = fileName;
                    }
                } else if (savedPath && savedPath.startsWith('Error:')) {
                    this.chatUI.showToast(`❌ ${savedPath}`, 'error');
                } else {
                    this.chatUI.showToast(`❌ Erreur: ${savedPath || 'Chemin invalide'}`, 'error');
                }
            } catch (e) {
                console.error('Error generating diagnostics HTML:', e);
                this.chatUI.showToast(`❌ Erreur de génération: ${e.message}`, 'error');
            }
        }

        /**
         * Vérifie si le fichier diagnostics.html existe et active le bouton si nécessaire
         */
        async checkDiagnosticsHtmlExists() {
            if (!this.androidInterface || !this.androidInterface.diagnosticsHtmlExists) {
                return;
            }

            try {
                const exists = await this.androidInterface.diagnosticsHtmlExists();
                const serverUrl = this.androidInterface.getHttpServerUrl();
                
                if (exists === 'true' && serverUrl) {
                    // Activer le bouton si le fichier existe et le serveur HTTP est disponible
                    if (this.diagnosticsOpenHtmlBtn) {
                        this.diagnosticsOpenHtmlBtn.disabled = false;
                    }
                } else {
                    // Désactiver le bouton si le fichier n'existe pas ou le serveur n'est pas disponible
                    if (this.diagnosticsOpenHtmlBtn) {
                        this.diagnosticsOpenHtmlBtn.disabled = true;
                    }
                }
            } catch (e) {
                console.error('Error checking diagnostics HTML existence:', e);
            }
        }

        async openDiagnosticsHtml() {
            if (!this.androidInterface || !this.androidInterface.getHttpServerUrl) {
                this.chatUI.showToast('Interface Android non disponible pour ouvrir la page HTML.', 'error');
                return;
            }

            try {
                const serverUrl = this.androidInterface.getHttpServerUrl();
                if (!serverUrl) {
                    this.chatUI.showToast('❌ Serveur HTTP non disponible. Impossible d\'ouvrir la page HTML.', 'error');
                    return;
                }

                const htmlUrl = `${serverUrl}/logs/diagnostics.html`;
                
                // Ouvrir dans un nouvel onglet/fenêtre
                const opened = window.open(htmlUrl, '_blank');
                
                if (opened) {
                    this.chatUI.showToast(`✅ Page HTML ouverte: ${htmlUrl}`, 'success');
                } else {
                    this.chatUI.showToast('❌ Impossible d\'ouvrir la page HTML. Vérifiez les bloqueurs de popup.', 'error');
                }
            } catch (e) {
                console.error('Error opening diagnostics HTML:', e);
                this.chatUI.showToast(`❌ Erreur d'ouverture: ${e.message}`, 'error');
            }
        }

        // ⭐ NOUVEAU: Charger le statut RAG/Ollama
        async loadRAGStatus() {
            if (!this.androidInterface || !this.androidInterface.getRAGStatus) {
                console.warn('Android interface for RAG status not available.');
                return;
            }

            try {
                const ragStatusJson = await this.androidInterface.getRAGStatus();
                const ragStatus = JSON.parse(ragStatusJson);
                this.renderRAGStatus(ragStatus);
            } catch (e) {
                console.error('Error loading RAG status:', e);
                this.ragStatusGrid.innerHTML = `<div style="text-align: center; padding: 20px; color: #ef4444;">❌ Erreur de chargement: ${e.message}</div>`;
            }
        }

        renderRAGStatus(status) {
            if (!this.ragStatusGrid) return;

            this.ragStatusGrid.innerHTML = '';

            // Card RAG Status
            const ragCard = document.createElement('div');
            ragCard.className = 'mini-card';
            ragCard.style = `
                background: rgba(30, 41, 59, 0.5);
                border: 1px solid rgba(148, 163, 184, 0.2);
                border-radius: 8px;
                padding: 16px;
            `;

            const ragActive = status.enabled && status.available;
            const ragColor = ragActive ? '#10b981' : '#ef4444';
            const ragText = ragActive ? 'Actif' : (status.enabled ? 'Indisponible' : 'Désactivé');

            ragCard.innerHTML = `
                <div class="mini-card-header">
                    <span class="mini-card-label">🔍 RAG (Recherche sémantique)</span>
                    <span class="mini-card-status ${ragActive ? 'status-active' : 'status-inactive'}"></span>
                </div>
                <div class="mini-card-value" style="color: ${ragColor}; font-size: 14px; font-weight: 600; margin-top: 8px;">
                    ${ragText}
                </div>
                ${status.reason ? `<div style="font-size: 11px; color: #64748b; margin-top: 4px;">${status.reason}</div>` : ''}
                <div style="font-size: 11px; color: #64748b; margin-top: 4px;">
                    Mode: ${status.useCloud ? 'Cloud' : 'Local'}
                </div>
            `;

            this.ragStatusGrid.appendChild(ragCard);

            // Card Ollama Mode
            const ollamaCard = document.createElement('div');
            ollamaCard.className = 'mini-card';
            ollamaCard.style = `
                background: rgba(30, 41, 59, 0.5);
                border: 1px solid rgba(148, 163, 184, 0.2);
                border-radius: 8px;
                padding: 16px;
            `;

            const ollamaColor = status.useCloud ? '#3b82f6' : '#8b5cf6';
            const ollamaText = status.useCloud ? 'Ollama Cloud' : 'Ollama Local';

            ollamaCard.innerHTML = `
                <div class="mini-card-header">
                    <span class="mini-card-label">🤖 Ollama</span>
                </div>
                <div class="mini-card-value" style="color: ${ollamaColor}; font-size: 14px; font-weight: 600; margin-top: 8px;">
                    ${ollamaText}
                </div>
                <div style="font-size: 11px; color: #64748b; margin-top: 4px;">
                    Embeddings: ${status.available ? 'Disponible' : 'Indisponible'}
                </div>
            `;

            this.ragStatusGrid.appendChild(ollamaCard);
        }

        // ⭐ NOUVEAU: Charger les statistiques conversations
        async loadConversationsStats() {
            if (!this.androidInterface || !this.androidInterface.getConversationStats) {
                console.warn('Android interface for conversation stats not available.');
                return;
            }

            try {
                const statsJson = await this.androidInterface.getConversationStats();
                const stats = JSON.parse(statsJson);
                this.renderConversationsStats(stats);
            } catch (e) {
                console.error('Error loading conversation stats:', e);
                this.conversationsStatsGrid.innerHTML = `<div style="text-align: center; padding: 20px; color: #ef4444;">❌ Erreur de chargement: ${e.message}</div>`;
            }
        }

        renderConversationsStats(stats) {
            if (!this.conversationsStatsGrid) return;

            this.conversationsStatsGrid.innerHTML = '';

            const statCards = [
                {
                    title: 'Total Conversations',
                    value: stats.totalConversations || 0,
                    icon: '💬',
                    color: '#3b82f6'
                },
                {
                    title: 'Avec Embeddings',
                    value: stats.conversationsWithEmbeddings || 0,
                    icon: '🔍',
                    color: '#10b981',
                    subtitle: stats.totalConversations > 0 
                        ? `${Math.round((stats.conversationsWithEmbeddings || 0) / stats.totalConversations * 100)}%`
                        : '0%'
                },
                {
                    title: 'Temps Moyen',
                    value: stats.averageResponseTime 
                        ? `${(stats.averageResponseTime / 1000).toFixed(1)}s`
                        : 'N/A',
                    icon: '⏱️',
                    color: '#8b5cf6'
                },
                {
                    title: 'API Principale',
                    value: stats.mostUsedAPI || 'N/A',
                    icon: '🔌',
                    color: '#ec4899',
                    small: true
                }
            ];

            statCards.forEach(cardInfo => {
                const card = document.createElement('div');
                card.className = 'mini-card';
                card.style = `
                    background: rgba(30, 41, 59, 0.5);
                    border: 1px solid rgba(148, 163, 184, 0.2);
                    border-radius: 8px;
                    padding: 16px;
                `;

                card.innerHTML = `
                    <div class="mini-card-header">
                        <span class="mini-card-label">${cardInfo.icon} ${cardInfo.title}</span>
                    </div>
                    <div class="mini-card-value" style="color: ${cardInfo.color}; font-size: ${cardInfo.small ? '14px' : '18px'}; font-weight: 600; margin-top: 8px;">
                        ${cardInfo.value}
                    </div>
                    ${cardInfo.subtitle ? `<div style="font-size: 11px; color: #64748b; margin-top: 4px;">${cardInfo.subtitle}</div>` : ''}
                `;

                this.conversationsStatsGrid.appendChild(card);
            });
        }

        /**
         * Échappe les caractères HTML pour sécurité
         */
        escapeHtml(text) {
            if (!text) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    }

    window.ChatDiagnostics = ChatDiagnostics;
})();


