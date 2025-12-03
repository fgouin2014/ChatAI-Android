/**
 * ChatAI - Model Management Module
 * Extracted from index.html for better maintainability
 * 
 * Contains:
 * - scanLocalGGUFModels() - Scan GGUF models on device
 * - loadLocalDeviceModels() - Load and display models list
 * - selectGGUFModel() - Select a model from the list
 * - updateModelSectionVisibility() - Show/hide model sections based on mode
 */

// Flag to prevent multiple simultaneous scans
let isScanningGGUF = false;

// ============================================================
// MODEL SECTION VISIBILITY
// ============================================================

/**
 * Updates visibility of model configuration sections based on selected mode
 */
function updateModelSectionVisibility() {
    const modeSelect = document.getElementById('configModeSelect');
    if (!modeSelect) return;
    
    const mode = modeSelect.value;
    
    // Get section elements - IDs in General tab
    const localGGUFModelSection = document.getElementById('generalLocalGGUFModelSection');
    const huggingFaceModelSection = document.getElementById('generalHuggingFaceModelSection');
    const ollamaCloudModelSection = document.getElementById('generalOllamaCloudModelSection');
    
    // Hide all sections by default
    if (localGGUFModelSection) localGGUFModelSection.style.display = 'none';
    if (huggingFaceModelSection) huggingFaceModelSection.style.display = 'none';
    if (ollamaCloudModelSection) ollamaCloudModelSection.style.display = 'none';
    
    // Show section corresponding to selected mode
    if (mode === 'local_gguf') {
        if (localGGUFModelSection) {
            localGGUFModelSection.style.display = 'block';
            // Make sure refresh button is visible
            const refreshBtn = document.getElementById('refreshLocalGGUFModelsBtn');
            if (refreshBtn) {
                refreshBtn.style.display = 'inline-block';
            }
        }
        // Trigger single scan with delay to avoid multiple scans
        if (!isScanningGGUF && typeof window.scanLocalGGUFModels === 'function') {
            setTimeout(() => {
                if (!isScanningGGUF) {
                    console.log('[chat-models] Local GGUF mode selected, auto-scanning...');
                    window.scanLocalGGUFModels();
                }
            }, 200);
        }
    } else if (mode === 'huggingface') {
        if (huggingFaceModelSection) huggingFaceModelSection.style.display = 'block';
    } else if (mode === 'ollama_cloud') {
        if (ollamaCloudModelSection) ollamaCloudModelSection.style.display = 'block';
    }
}

// Make function globally accessible
window.updateModelSectionVisibility = updateModelSectionVisibility;

// ============================================================
// GGUF MODEL SCANNING
// ============================================================

/**
 * Scans for GGUF models on the device
 * Uses /api/scan-gguf-models endpoint
 */
async function scanLocalGGUFModels() {
    // Prevent multiple simultaneous scans
    if (isScanningGGUF) {
        console.log('[chat-models] Scan already in progress, skipping...');
        return;
    }
    
    const select = document.getElementById('configLocalGGUFModel');
    const refreshBtn = document.getElementById('refreshLocalGGUFModelsBtn');
    
    if (!select) {
        console.warn('[chat-models] Element configLocalGGUFModel not found');
        return;
    }
    
    isScanningGGUF = true;
    
    // Visual feedback
    select.innerHTML = '<option value="">Scanning...</option>';
    select.disabled = true;
    if (refreshBtn) {
        refreshBtn.disabled = true;
        refreshBtn.textContent = 'Scanning...';
    }
    
    try {
        console.log('[chat-models] Calling /api/scan-gguf-models...');
        
        // Get server URL
        let apiUrl = '/api/scan-gguf-models';
        if (window.secureChatApp?.androidInterface?.getHttpServerUrl) {
            const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
            apiUrl = `${serverUrl}/api/scan-gguf-models`;
            console.log('[chat-models] Using server URL:', apiUrl);
        } else {
            console.log('[chat-models] getHttpServerUrl not available, using relative URL');
        }
        
        // Optional: Check server status first
        try {
            const statusUrl = apiUrl.replace('/api/scan-gguf-models', '/api/status');
            const statusResponse = await fetch(statusUrl, { method: 'HEAD' }).catch(() => null);
            if (!statusResponse || !statusResponse.ok) {
                console.warn('[chat-models] HTTP server may not be available');
            }
        } catch (statusError) {
            console.warn('[chat-models] Server status check failed:', statusError.message);
        }
        
        const response = await fetch(apiUrl);
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        const data = await response.json();
        console.log('[chat-models] Data received:', data);
        
        select.innerHTML = '<option value="">-- Select a model --</option>';
        
        if (data.models && data.models.length > 0) {
            data.models.forEach(model => {
                const option = document.createElement('option');
                option.value = model.name;
                option.textContent = `${model.name} (${model.size})`;
                select.appendChild(option);
            });
            console.log(`[chat-models] ${data.models.length} GGUF model(s) found`);
            
            // Restore saved model selection
            const savedModel = window.secureChatApp?.currentConfig?.local_gguf_model 
                || window.secureChatApp?.currentConfig?.selectedModel
                || localStorage.getItem('chatai_local_gguf_model');
            
            if (savedModel) {
                // Check if saved model exists in the list
                const modelExists = Array.from(select.options).some(opt => opt.value === savedModel);
                if (modelExists) {
                    select.value = savedModel;
                    console.log(`[chat-models] Restored saved model: ${savedModel}`);
                } else {
                    console.log(`[chat-models] Saved model not found in list: ${savedModel}`);
                }
            }
            
            // Visual success feedback
            if (refreshBtn) {
                const originalText = refreshBtn.textContent;
                refreshBtn.textContent = data.models.length + ' model(s)';
                setTimeout(() => {
                    refreshBtn.textContent = 'Scan';
                }, 2000);
            }
        } else {
            select.innerHTML = '<option value="">-- No GGUF models found --</option>';
            console.warn('[chat-models] No GGUF models found in /storage/emulated/0/ChatAI-Files/models/');
            
            if (refreshBtn) {
                refreshBtn.textContent = 'No models';
                setTimeout(() => {
                    refreshBtn.textContent = 'Scan';
                }, 2000);
            }
        }
    } catch (error) {
        console.error('[chat-models] GGUF scan error:', error);
        const errorMsg = error.message || 'Unknown error';
        select.innerHTML = '<option value="">-- Error: ' + errorMsg.substring(0, 50) + ' --</option>';
        
        if (refreshBtn) {
            refreshBtn.textContent = 'Error';
            setTimeout(() => {
                refreshBtn.textContent = 'Scan';
            }, 2000);
        }
    } finally {
        isScanningGGUF = false;
        select.disabled = false;
        if (refreshBtn) {
            refreshBtn.disabled = false;
        }
    }
}

// Make function globally accessible
window.scanLocalGGUFModels = scanLocalGGUFModels;

// ============================================================
// LOCAL DEVICE MODELS LIST
// ============================================================

/**
 * Helper function to parse file size string to bytes
 * @param {string} sizeStr - Size string like "278.5 MB"
 * @returns {number} Size in bytes
 */
function parseFileSize(sizeStr) {
    if (typeof sizeStr !== 'string') return 0;
    const match = sizeStr.match(/([\d.]+)\s*(MB|GB|KB|bytes?)/i);
    if (!match) return 0;
    const value = parseFloat(match[1]);
    const unit = match[2].toUpperCase();
    if (unit === 'GB') return value * 1024 * 1024 * 1024;
    if (unit === 'MB') return value * 1024 * 1024;
    if (unit === 'KB') return value * 1024;
    return value;
}

/**
 * Loads and displays local device models in the models list
 */
async function loadLocalDeviceModels() {
    const modelsListDiv = document.getElementById('localDeviceModelsList');
    if (!modelsListDiv) return;
    
    modelsListDiv.innerHTML = '<div style="font-size: 11px; color: #94a3b8; text-align: center; padding: 20px;">Loading models...</div>';
    
    try {
        // Get server URL
        let apiUrl = '/api/scan-gguf-models';
        if (window.secureChatApp?.androidInterface?.getHttpServerUrl) {
            const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
            apiUrl = `${serverUrl}/api/scan-gguf-models`;
            console.log('[chat-models] loadLocalDeviceModels using server URL:', apiUrl);
        } else {
            console.log('[chat-models] loadLocalDeviceModels using relative URL');
        }
        
        console.log('[chat-models] loadLocalDeviceModels calling API:', apiUrl);
        const response = await fetch(apiUrl);
        
        if (!response.ok) {
            const errorText = await response.text().catch(() => 'No details');
            console.error('[chat-models] loadLocalDeviceModels HTTP error:', response.status, errorText);
            throw new Error(`HTTP ${response.status}: ${response.statusText}. Details: ${errorText.substring(0, 200)}`);
        }
        
        const data = await response.json();
        console.log('[chat-models] loadLocalDeviceModels data received:', data);
        const models = data.models || [];
            
        if (models.length === 0) {
            modelsListDiv.innerHTML = '<div style="font-size: 11px; color: #94a3b8; text-align: center; padding: 20px;">No GGUF models found on device.<br>Location: <code>/storage/emulated/0/ChatAI-Files/models/</code><br><br>Tip: Place your .gguf files in this directory and click "Refresh list".</div>';
            return;
        }
        
        // Format models for display
        const formattedModels = models.map(m => ({
            name: m.name,
            size: m.size,
            sizeBytes: parseFileSize(m.size),
            category: 'GGUF',
            type: 'GGUF',
            path: m.path
        }));
            
        // Group by category
        const groupedByCategory = {};
        formattedModels.forEach(m => {
            const cat = m.category || 'GGUF';
            if (!groupedByCategory[cat]) {
                groupedByCategory[cat] = [];
            }
            groupedByCategory[cat].push(m);
        });
        
        // Display models grouped by category
        modelsListDiv.innerHTML = Object.keys(groupedByCategory).sort().map(category => {
            const categoryModels = groupedByCategory[category];
            const totalSizeBytes = categoryModels.reduce((sum, m) => sum + (m.sizeBytes || 0), 0);
            const totalSizeMB = (totalSizeBytes / (1024 * 1024)).toFixed(1);
            
            return `
                <div style="margin-bottom: 12px;">
                    <div style="font-size: 11px; font-weight: 600; color: #3b82f6; margin-bottom: 6px; padding: 4px 0; border-bottom: 1px solid rgba(59, 130, 246, 0.2);">
                        ${category} (${categoryModels.length} model${categoryModels.length > 1 ? 's' : ''}, ${totalSizeMB} MB)
                    </div>
                    ${categoryModels.map(m => {
                        const sizeDisplay = m.size || '0 MB';
                        const typeBadge = m.type ? `<span style="font-size: 9px; padding: 2px 6px; background: rgba(59, 130, 246, 0.2); border-radius: 3px; color: #3b82f6; margin-left: 6px;">${m.type}</span>` : '';
                        return `<div style="display: flex; justify-content: space-between; align-items: center; padding: 6px 8px; margin-left: 12px; margin-bottom: 3px; background: rgba(148, 163, 184, 0.05); border-radius: 4px; cursor: pointer;" onclick="selectGGUFModel('${m.name.replace(/'/g, "\\'")}')" title="Click to select this model">
                            <span style="font-size: 11px; font-weight: 500; display: flex; align-items: center;">
                                ${m.name}
                                ${typeBadge}
                            </span>
                            <span style="font-size: 10px; color: #94a3b8;">${sizeDisplay}</span>
                        </div>`;
                    }).join('')}
                </div>
            `;
        }).join('');
    } catch (error) {
        console.error('[chat-models] loadLocalDeviceModels error:', error);
        modelsListDiv.innerHTML = `<div style="font-size: 11px; color: #f87171; text-align: center; padding: 20px;">Error: ${error.message}<br><br>Tip: Make sure the HTTP server (port 8080) is running and the directory /storage/emulated/0/ChatAI-Files/models/ exists.</div>`;
    }
}

// Make function globally accessible
window.loadLocalDeviceModels = loadLocalDeviceModels;

// ============================================================
// MODEL SELECTION
// ============================================================

/**
 * Selects a GGUF model from the list
 * @param {string} modelName - Name of the model to select
 */
function selectGGUFModel(modelName) {
    const configLocalGGUFModel = document.getElementById('configLocalGGUFModel');
    if (configLocalGGUFModel) {
        configLocalGGUFModel.value = modelName;
        console.log(`[chat-models] GGUF model selected: ${modelName}`);
        
        // Save to localStorage for persistence
        localStorage.setItem('chatai_local_gguf_model', modelName);
        
        // Update config object if available
        if (window.secureChatApp?.currentConfig) {
            window.secureChatApp.currentConfig.local_gguf_model = modelName;
        }
        
        // Optional: Show notification
        if (window.showNotification) {
            window.showNotification(`Model selected: ${modelName}`, 'success');
        }
    }
}

// Make function globally accessible
window.selectGGUFModel = selectGGUFModel;

// Log module loaded
console.log('[chat-models.js] Module loaded');

