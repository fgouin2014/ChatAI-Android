/**
 * Module de gestion de l'historique des conversations
 * - Recherche dans l'historique
 * - Affichage des conversations
 * - Export JSON/HTML
 * - Statistiques
 */

(function() {
    'use strict';

    class ChatHistory {
        constructor(androidInterface) {
            this.androidInterface = androidInterface;
            this.conversations = [];
            this.currentSearchQuery = '';
            
            this.initializeDOMReferences();
            this.setupEventListeners();
        }
        
        /**
         * Initialise les références DOM
         */
        initializeDOMReferences() {
            // Recherche
            this.searchInput = document.getElementById('historySearchInput');
            this.searchBtn = document.getElementById('historySearchBtn');
            this.clearBtn = document.getElementById('historyClearBtn');
            
            // Actions
            this.refreshBtn = document.getElementById('historyRefreshBtn');
            this.exportJSONBtn = document.getElementById('historyExportJSONBtn');
            this.exportHTMLBtn = document.getElementById('historyExportHTMLBtn');
            this.deleteAllBtn = document.getElementById('historyDeleteAllBtn');
            
            // Affichage
            this.historyList = document.getElementById('historyList');
            this.historyListContainer = document.getElementById('historyListContainer');
            
            // Statistiques
            this.statTotal = document.getElementById('statTotal');
            this.statAvgTime = document.getElementById('statAvgTime');
            this.statMainAPI = document.getElementById('statMainAPI');
        }
        
        /**
         * Configure les écouteurs d'événements
         */
        setupEventListeners() {
            if (!this.androidInterface) {
                console.warn('ChatHistory: AndroidInterface non disponible');
                return;
            }
            
            // Recherche
            if (this.searchBtn) {
                this.searchBtn.addEventListener('click', () => this.performSearch());
            }
            
            if (this.clearBtn) {
                this.clearBtn.addEventListener('click', () => this.clearSearch());
            }
            
            if (this.searchInput) {
                this.searchInput.addEventListener('keypress', (e) => {
                    if (e.key === 'Enter') {
                        this.performSearch();
                    }
                });
            }
            
            // Actions
            if (this.refreshBtn) {
                this.refreshBtn.addEventListener('click', () => this.loadHistory());
            }
            
            if (this.exportJSONBtn) {
                this.exportJSONBtn.addEventListener('click', () => this.exportJSON());
            }
            
            if (this.exportHTMLBtn) {
                this.exportHTMLBtn.addEventListener('click', () => this.exportHTML());
            }
            
            if (this.deleteAllBtn) {
                this.deleteAllBtn.addEventListener('click', () => this.deleteAll());
            }
        }
        
        /**
         * Charge l'historique depuis Room DB
         */
        async loadHistory(limit = 100) {
            if (!this.androidInterface) {
                this.showError('Interface Android non disponible');
                return;
            }
            
            try {
                this.showLoading();
                
                // Récupérer les conversations depuis Room DB
                const conversationsJson = this.androidInterface.getConversations(limit);
                if (!conversationsJson) {
                    this.showEmpty('Aucune conversation trouvée');
                    return;
                }
                
                const conversations = JSON.parse(conversationsJson);
                this.conversations = conversations;
                
                // Charger les statistiques
                await this.loadStats();
                
                // Afficher les conversations
                this.renderConversations(conversations);
                
            } catch (error) {
                console.error('Erreur chargement historique:', error);
                this.showError('Erreur de chargement: ' + error.message);
            }
        }
        
        /**
         * Charge les statistiques
         */
        async loadStats() {
            if (!this.androidInterface) {
                return;
            }
            
            try {
                const statsJson = this.androidInterface.getConversationStats();
                if (!statsJson) {
                    return;
                }
                
                const stats = JSON.parse(statsJson);
                
                // Afficher les statistiques
                if (this.statTotal) {
                    this.statTotal.textContent = stats.totalConversations || 0;
                }
                
                if (this.statAvgTime) {
                    const avgTime = stats.averageResponseTime || 0;
                    this.statAvgTime.textContent = avgTime > 0 ? `${avgTime}ms` : '-';
                }
                
                if (this.statMainAPI) {
                    this.statMainAPI.textContent = stats.mostUsedAPI || '-';
                }
                
            } catch (error) {
                console.error('Erreur chargement statistiques:', error);
            }
        }
        
        /**
         * Effectue une recherche dans l'historique
         */
        async performSearch() {
            if (!this.searchInput || !this.androidInterface) {
                return;
            }
            
            const query = this.searchInput.value.trim();
            if (!query) {
                this.loadHistory();
                return;
            }
            
            this.currentSearchQuery = query;
            
            try {
                this.showLoading();
                
                // Rechercher dans Room DB
                const conversationsJson = this.androidInterface.searchConversations(query, 100);
                if (!conversationsJson) {
                    this.showEmpty(`Aucun résultat pour "${query}"`);
                    return;
                }
                
                const conversations = JSON.parse(conversationsJson);
                this.conversations = conversations;
                
                // Afficher les résultats
                this.renderConversations(conversations, query);
                
                // ⭐ AMÉLIORATION: Feedback utilisateur
                if (conversations.length > 0) {
                    this.showSuccess(`${conversations.length} résultat(s) trouvé(s) pour "${query}"`);
                } else {
                    this.showEmpty(`Aucun résultat pour "${query}"`);
                }
                
            } catch (error) {
                console.error('Erreur recherche:', error);
                this.showError('Erreur de recherche: ' + error.message);
            }
        }
        
        /**
         * Efface la recherche
         */
        clearSearch() {
            if (this.searchInput) {
                this.searchInput.value = '';
            }
            this.currentSearchQuery = '';
            this.loadHistory();
        }
        
        /**
         * Affiche les conversations dans la liste
         */
        renderConversations(conversations, searchQuery = null) {
            if (!this.historyList) {
                return;
            }
            
            if (conversations.length === 0) {
                const message = searchQuery 
                    ? `Aucun résultat pour "${searchQuery}"`
                    : 'Aucune conversation trouvée';
                this.showEmpty(message);
                return;
            }
            
            // Générer le HTML pour chaque conversation
            const html = conversations.map(conv => this.renderConversationCard(conv)).join('');
            
            this.historyList.innerHTML = html;
            
            // Ajouter les écouteurs d'événements pour les actions
            conversations.forEach((conv, index) => {
                const card = this.historyList.children[index];
                if (card) {
                    const viewBtn = card.querySelector('.view-conv-btn');
                    const exportBtn = card.querySelector('.export-conv-btn');
                    
                    if (viewBtn) {
                        viewBtn.addEventListener('click', () => this.viewConversation(conv));
                    }
                    
                    if (exportBtn) {
                        exportBtn.addEventListener('click', () => this.exportConversation(conv));
                    }
                }
            });
        }
        
        /**
         * Génère le HTML pour une carte de conversation
         */
        renderConversationCard(conversation) {
            const date = new Date(conversation.timestamp);
            const dateStr = date.toLocaleString('fr-FR', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
            
            const personality = conversation.personality || 'KITT';
            const personalityIcon = personality === 'KITT' ? '🚗' : personality === 'GLaDOS' ? '🤖' : '🤖';
            
            const userMessage = this.truncateText(conversation.userMessage || '', 100);
            const aiResponse = this.truncateText(conversation.aiResponse || '', 150);
            
            return `
                <div class="history-conv-card" style="background: rgba(15, 23, 42, 0.5); border: 1px solid rgba(148, 163, 184, 0.2); border-radius: 8px; padding: 16px; transition: all 0.2s;">
                    <div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 12px;">
                        <div>
                            <div style="font-size: 13px; color: #64748b; margin-bottom: 4px;">${dateStr}</div>
                            <div style="font-size: 12px; color: #94a3b8;">
                                ${personalityIcon} ${personality} | 🌐 ${conversation.apiUsed || 'N/A'} | ⏱️ ${conversation.responseTimeMs || 0}ms
                            </div>
                        </div>
                        <div style="display: flex; gap: 8px;">
                            <button class="view-conv-btn" style="padding: 6px 12px; background: rgba(59, 130, 246, 0.2); color: #3b82f6; border: 1px solid rgba(59, 130, 246, 0.3); border-radius: 6px; cursor: pointer; font-size: 12px;">👁️ Voir</button>
                            <button class="export-conv-btn" style="padding: 6px 12px; background: rgba(139, 92, 246, 0.2); color: #8b5cf6; border: 1px solid rgba(139, 92, 246, 0.3); border-radius: 6px; cursor: pointer; font-size: 12px;">📤 Export</button>
                        </div>
                    </div>
                    <div style="margin-bottom: 8px;">
                        <div style="font-size: 12px; color: #94a3b8; margin-bottom: 4px;">👤 VOUS:</div>
                        <div style="font-size: 13px; color: #e2e8f0; line-height: 1.5;">${this.escapeHtml(userMessage)}</div>
                    </div>
                    <div>
                        <div style="font-size: 12px; color: #94a3b8; margin-bottom: 4px;">🤖 ${personality}:</div>
                        <div style="font-size: 13px; color: #cbd5e1; line-height: 1.5;">${this.escapeHtml(aiResponse)}</div>
                    </div>
                </div>
            `;
        }
        
        /**
         * Affiche une conversation complète dans un modal
         */
        viewConversation(conversation) {
            const date = new Date(conversation.timestamp);
            const dateStr = date.toLocaleString('fr-FR', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
            
            const personality = conversation.personality || 'KITT';
            const personalityIcon = personality === 'KITT' ? '🚗' : personality === 'GLaDOS' ? '🤖' : '🤖';
            
            const thinkingHtml = conversation.thinkingTrace 
                ? `<div style="margin-top: 16px; padding: 12px; background: rgba(139, 92, 246, 0.1); border: 1px solid rgba(139, 92, 246, 0.3); border-radius: 8px; max-width: 100%; box-sizing: border-box;">
                    <div style="font-size: 12px; color: #8b5cf6; margin-bottom: 8px; font-weight: 600;">🧠 Raisonnement:</div>
                    <div style="font-size: 12px; color: #cbd5e1; line-height: 1.6; white-space: pre-wrap; font-family: monospace; word-wrap: break-word; word-break: break-word; overflow-wrap: break-word; max-width: 100%; overflow-x: auto; overflow-y: visible; box-sizing: border-box;">${this.escapeHtml(conversation.thinkingTrace)}</div>
                </div>`
                : '';
            
            const modalContent = `
                <div style="padding: 20px;">
                    <div style="margin-bottom: 16px; padding-bottom: 16px; border-bottom: 1px solid rgba(148, 163, 184, 0.2);">
                        <div style="font-size: 13px; color: #64748b; margin-bottom: 8px;">${dateStr}</div>
                        <div style="font-size: 12px; color: #94a3b8;">
                            ${personalityIcon} ${personality} | 🌐 ${conversation.apiUsed || 'N/A'} | ⏱️ ${conversation.responseTimeMs || 0}ms | 📱 ${conversation.platform || 'N/A'}
                        </div>
                        ${conversation.conversationId ? `<div style="font-size: 11px; color: #64748b; margin-top: 8px;">🆔 ID: ${conversation.conversationId}</div>` : ''}
                    </div>
                    
                    <div style="margin-bottom: 16px;">
                        <div style="font-size: 13px; color: #3b82f6; margin-bottom: 8px; font-weight: 600;">👤 VOUS:</div>
                        <div style="font-size: 14px; color: #e2e8f0; line-height: 1.6; white-space: pre-wrap;">${this.escapeHtml(conversation.userMessage || '')}</div>
                    </div>
                    
                    <div style="margin-bottom: 16px;">
                        <div style="font-size: 13px; color: #8b5cf6; margin-bottom: 8px; font-weight: 600;">🤖 ${personality}:</div>
                        <div style="font-size: 14px; color: #cbd5e1; line-height: 1.6; white-space: pre-wrap;">${this.escapeHtml(conversation.aiResponse || '')}</div>
                    </div>
                    
                    ${thinkingHtml}
                </div>
            `;
            
            // Créer un modal simple (ou utiliser un modal existant)
            this.showModal('📚 Détails de la conversation', modalContent);
        }
        
        /**
         * Exporte une conversation en JSON
         */
        exportConversation(conversation) {
            const json = JSON.stringify(conversation, null, 2);
            this.downloadFile(json, `conversation_${conversation.conversationId || conversation.id}_${Date.now()}.json`, 'application/json');
        }
        
        /**
         * Exporte tout l'historique en JSON
         */
        async exportJSON() {
            if (!this.androidInterface) {
                this.showError('Interface Android non disponible');
                return;
            }
            
            try {
                const json = this.androidInterface.exportConversationsToJson();
                if (!json) {
                    this.showError('Erreur lors de l\'export JSON');
                    return;
                }
                
                this.downloadFile(json, `conversations_${Date.now()}.json`, 'application/json');
                this.showSuccess('Export JSON réussi');
                
            } catch (error) {
                console.error('Erreur export JSON:', error);
                this.showError('Erreur export JSON: ' + error.message);
            }
        }
        
        /**
         * Exporte tout l'historique en HTML
         */
        async exportHTML() {
            if (!this.androidInterface) {
                this.showError('Interface Android non disponible');
                return;
            }
            
            try {
                const html = this.androidInterface.exportConversationsToHtml();
                if (!html) {
                    this.showError('Erreur lors de l\'export HTML');
                    return;
                }
                
                this.downloadFile(html, `conversations_${Date.now()}.html`, 'text/html');
                this.showSuccess('Export HTML réussi');
                
            } catch (error) {
                console.error('Erreur export HTML:', error);
                this.showError('Erreur export HTML: ' + error.message);
            }
        }
        
        /**
         * Supprime tout l'historique
         */
        async deleteAll() {
            if (!confirm('⚠️ Êtes-vous sûr de vouloir supprimer TOUT l\'historique ? Cette action est irréversible.')) {
                return;
            }
            
            if (!this.androidInterface) {
                this.showError('Interface Android non disponible');
                return;
            }
            
            try {
                const result = this.androidInterface.deleteAllConversations();
                if (result) {
                    this.showSuccess('Historique supprimé');
                    this.loadHistory();
                } else {
                    this.showError('Erreur lors de la suppression');
                }
                
            } catch (error) {
                console.error('Erreur suppression:', error);
                this.showError('Erreur suppression: ' + error.message);
            }
        }
        
        /**
         * Utilitaires d'affichage
         */
        showLoading() {
            if (this.historyList) {
                this.historyList.innerHTML = `
                    <div style="text-align: center; padding: 40px; color: #64748b;">
                        <div style="font-size: 48px; margin-bottom: 12px;">⏳</div>
                        <div>Chargement...</div>
                    </div>
                `;
            }
        }
        
        showEmpty(message) {
            if (this.historyList) {
                this.historyList.innerHTML = `
                    <div style="text-align: center; padding: 40px; color: #64748b;">
                        <div style="font-size: 48px; margin-bottom: 12px;">📭</div>
                        <div>${message}</div>
                    </div>
                `;
            }
        }
        
        showError(message) {
            if (this.historyList) {
                this.historyList.innerHTML = `
                    <div style="text-align: center; padding: 40px; color: #ef4444;">
                        <div style="font-size: 48px; margin-bottom: 12px;">❌</div>
                        <div>${message}</div>
                    </div>
                `;
            }
        }
        
        showSuccess(message) {
            // Afficher un toast ou notification
            if (window.secureChatApp?.androidInterface?.showToast) {
                window.secureChatApp.androidInterface.showToast(message);
            } else {
                // Fallback: afficher temporairement dans la liste
                if (this.historyList) {
                    const successDiv = document.createElement('div');
                    successDiv.style.cssText = 'text-align: center; padding: 20px; color: #10b981; background: rgba(16, 185, 129, 0.1); border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 8px; margin-bottom: 16px;';
                    successDiv.innerHTML = `<div style="font-size: 14px;">✅ ${message}</div>`;
                    this.historyList.insertBefore(successDiv, this.historyList.firstChild);
                    setTimeout(() => successDiv.remove(), 3000);
                } else {
                    alert(message);
                }
            }
        }
        
        showModal(title, content) {
            // Créer un modal simple ou utiliser un modal existant
            const modal = document.createElement('div');
            modal.style.cssText = 'position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); z-index: 10000; display: flex; align-items: center; justify-content: center;';
            modal.innerHTML = `
                <div style="background: #1e293b; border-radius: 12px; max-width: 600px; max-height: 80vh; width: 90%; display: flex; flex-direction: column; border: 1px solid rgba(148, 163, 184, 0.3);">
                    <div style="padding: 16px; border-bottom: 1px solid rgba(148, 163, 184, 0.2); display: flex; justify-content: space-between; align-items: center; flex-shrink: 0;">
                        <h3 style="margin: 0; color: #e2e8f0; font-size: 18px;">${title}</h3>
                        <button onclick="this.closest('.modal-close').remove()" style="background: none; border: none; color: #94a3b8; font-size: 24px; cursor: pointer; padding: 0; width: 30px; height: 30px;">×</button>
                    </div>
                    <div style="overflow-y: auto; overflow-x: hidden; flex: 1; min-height: 0;">
                        ${content}
                    </div>
                </div>
            `;
            
            modal.querySelector('.modal-close') || modal.querySelector('button').closest('div').closest('div').classList.add('modal-close');
            modal.querySelector('button').addEventListener('click', () => modal.remove());
            
            document.body.appendChild(modal);
        }
        
        /**
         * Utilitaires
         */
        truncateText(text, maxLength) {
            if (!text) return '';
            if (text.length <= maxLength) return text;
            return text.substring(0, maxLength) + '...';
        }
        
        escapeHtml(text) {
            if (!text) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
        
        downloadFile(content, filename, mimeType) {
            const blob = new Blob([content], { type: mimeType });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
        }
    }
    
    // Export pour utilisation dans chat-core.js
    window.ChatHistory = ChatHistory;
})();


