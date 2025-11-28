# 🔍 Audit Onglet TTS - Webapp

**Date**: 2025-11-27  
**Problème**: Onglet TTS vide/incomplet dans l'interface webapp

---

## ❌ PROBLÈMES IDENTIFIÉS

### 1. Interface TTS minimale

**Actuellement dans `index.html` (lignes 554-587)**:
- ✅ Mode (input text) - `configTtsMode`
- ✅ Voix (select) - `configTtsVoice`
- ✅ AutoPlay (checkbox) - `configTtsAutoPlay`
- ❌ **AUCUNE option pour serveur TTS natif**
- ❌ **AUCUN test de connexion**
- ❌ **AUCUN statut serveur**
- ❌ **AUCUNE configuration ONNX**

---

### 2. Configuration manquante

**Ce qui devrait être présent**:

#### A. Moteur TTS
- [ ] Select: ONNX TTS (serveur natif) / Android TTS / Serveur Python
- [ ] Statut: Serveur TTS disponible (port 11401)
- [ ] Bouton: Test connexion serveur TTS

#### B. Configuration ONNX TTS
- [ ] Statut: Modèles ONNX chargés (encoder, decoder, vocoder)
- [ ] Info: Taille modèles (~607 MB)
- [ ] Logs: Dernière synthèse

#### C. Configuration Android TTS
- [ ] Liste des voix disponibles
- [ ] Langue (fr, en)
- [ ] Vitesse, Pitch

#### D. Test TTS
- [ ] Input: Texte de test
- [ ] Bouton: Tester synthèse
- [ ] Player: Écouter résultat

---

### 3. JavaScript manquant

**Dans `chat-config.js`**:
- ✅ `saveConfigSection('tts')` existe (ligne 578)
- ❌ **AUCUNE fonction de test TTS**
- ❌ **AUCUNE vérification statut serveur**
- ❌ **AUCUN chargement configuration TTS depuis Android**

---

## ✅ SOLUTION PROPOSÉE

### 1. Interface TTS complète

**Ajouter dans `index.html` (onglet TTS)**:

```html
<!-- Moteur TTS -->
<label>Moteur TTS
    <select id="configTtsEngine">
        <option value="onnx_server">ONNX TTS Server (port 11401) ⭐ Recommandé</option>
        <option value="android">Android TTS (fallback)</option>
    </select>
</label>

<!-- Statut serveur TTS -->
<div id="ttsServerStatus" class="status-indicator">
    <span id="ttsServerStatusText">Vérification...</span>
    <button id="testTtsServerBtn" class="test-btn">Test Serveur TTS</button>
</div>

<!-- Configuration ONNX -->
<div id="onnxTtsConfig" class="config-section">
    <h5>Modèles ONNX</h5>
    <div id="onnxModelsStatus">
        <span>Encoder: <span id="encoderStatus">-</span></span>
        <span>Decoder: <span id="decoderStatus">-</span></span>
        <span>Vocoder: <span id="vocoderStatus">-</span></span>
    </div>
</div>

<!-- Test TTS -->
<div class="test-section">
    <label>Texte de test
        <input type="text" id="ttsTestText" placeholder="Bonjour, ceci est un test" value="Bonjour, ceci est un test de synthèse vocale">
    </label>
    <button id="testTtsBtn" class="test-btn">🔊 Tester TTS</button>
    <audio id="ttsTestAudio" controls style="display: none;"></audio>
</div>
```

---

### 2. JavaScript pour TTS

**Ajouter dans `chat-config.js`**:

```javascript
// Vérifier statut serveur TTS
async checkTtsServerStatus() {
    try {
        const response = await fetch('http://127.0.0.1:11401/health');
        const data = await response.json();
        return data.status === 'ok' && data.model_loaded === true;
    } catch (e) {
        return false;
    }
}

// Tester synthèse TTS
async testTtsSynthesis(text) {
    try {
        const response = await fetch('http://127.0.0.1:11401/synthesize', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ text })
        });
        if (response.ok) {
            const audioBlob = await response.blob();
            const audioUrl = URL.createObjectURL(audioBlob);
            const audio = document.getElementById('ttsTestAudio');
            audio.src = audioUrl;
            audio.style.display = 'block';
            audio.play();
            return true;
        }
    } catch (e) {
        console.error('Erreur test TTS:', e);
        return false;
    }
}
```

---

### 3. Chargement configuration

**Modifier `loadAiConfigPreview()` dans `chat-config.js`**:
- Charger `cfg.tts.engine` (onnx_server / android)
- Vérifier statut serveur TTS
- Afficher statut modèles ONNX

---

## 📋 PLAN D'IMPLÉMENTATION

1. ✅ **Audit complet** (fait)
2. ⏳ **Ajouter interface TTS complète** dans `index.html`
3. ⏳ **Ajouter fonctions JavaScript** dans `chat-config.js`
4. ⏳ **Tester interface**

---

**L'onglet TTS est actuellement trop minimaliste et ne permet pas de configurer/utiliser le serveur TTS natif !** ❌


