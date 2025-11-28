# 🔧 Problème: Modèle Local Retourne à "Personnalisé"

**Date**: 2025-11-27  
**Statut**: ✅ **CORRIGÉ**

---

## 🐛 PROBLÈME IDENTIFIÉ

Lors de la sauvegarde du modèle local dans l'onglet Configuration → Local:
1. L'utilisateur sélectionne un modèle (ex: `gemma3:270m`)
2. Il clique sur "Sauvegarder"
3. La valeur **ne s'enregistre pas** et le select **retourne à "personnalisé"**

---

## 🔍 CAUSE RACINE

### 1. Hardcode dans `chat-config.js` (CORRIGÉ)

**Ligne 511** dans `chat-config.js`:
```javascript
// ❌ AVANT (hardcodé)
cfg.local_server.model = 'gemma3-270m.gguf';

// ✅ APRÈS (utilise getSelectValue)
cfg.local_server.model = this.getSelectValue(core.configLocalModel, core.configLocalModelCustom) || 'gemma3:270m';
```

**Problème**: Le modèle était hardcodé à `gemma3-270m.gguf` au lieu d'utiliser la valeur du select.

---

### 2. Ancien Format vs Nouveau Format

**Valeur sauvegardée actuellement** (dans SharedPreferences):
```
local_model_name: gemma3-270m.gguf
```

**Options dans le select** (nouveau format Ollama):
```
- gemma3:270m
- llama3.2:3b
- gemma3:1b
```

**Problème**: Quand `setSelectValue()` cherche `gemma3-270m.gguf` dans les options du select, elle ne le trouve pas, donc passe en mode "custom".

---

## ✅ CORRECTIONS APPLIQUÉES

### 1. Sauvegarde - Utiliser `getSelectValue()`

```javascript
// chat-config.js ligne 516
cfg.local_server.model = this.getSelectValue(core.configLocalModel, core.configLocalModelCustom) || 'gemma3:270m';
```

### 2. Chargement - Conversion automatique

```javascript
// chat-config.js ligne 160
let modelValue = local.model || 'gemma3:270m';
// Migration automatique: gemma3-270m.gguf → gemma3:270m
if (modelValue === 'gemma3-270m.gguf') {
    modelValue = 'gemma3:270m';
    console.log('🔄 Migration automatique du modèle: gemma3-270m.gguf → gemma3:270m');
}
this.setSelectValue(this.core.configLocalModel, this.core.configLocalModelCustom, modelValue);
```

---

## 🧪 TEST

### Étapes pour vérifier

1. **Ouvrir l'app ChatAI** sur le device
2. **Aller dans Configuration → Local**
3. **Sélectionner un modèle** dans le select (ex: `gemma3:270m`)
4. **Cliquer sur "Sauvegarder"**
5. **Vérifier**:
   - Le select doit rester sur `gemma3:270m` (pas "personnalisé")
   - La valeur doit être sauvegardée dans SharedPreferences

### Vérification SharedPreferences

```bash
adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml | grep local_model_name"
```

**Résultat attendu**:
```xml
<string name="local_model_name">gemma3:270m</string>
```

---

## 📝 NOTES

- La conversion automatique `gemma3-270m.gguf` → `gemma3:270m` se fait uniquement lors du chargement
- Après la première sauvegarde, la valeur sera déjà au bon format
- Les anciennes configurations seront automatiquement migrées

---

## 🔗 RÉFÉRENCES

- `ChatAI-Android/app/src/main/assets/webapp/chat-config.js` - Lignes 160, 516
- `ChatAI-Android/app/src/main/assets/webapp/index.html` - Lignes 279-285 (select)


