# 🔧 Correction Manuelle URL Ollama Local

**Date**: 2025-11-27  
**Objectif**: Corriger manuellement l'URL du serveur Ollama dans l'app

---

## 🎯 VOTRE CONFIGURATION

### Configuration actuelle (INCORRECTE)
```
local_server_url: http://127.0.0.1:11434
local_model_name: gemma3-270m.gguf
```

### Configuration correcte
```
local_server_url: http://10.43.62.249:11434/v1/chat/completions
local_model_name: gemma3:270m
```

**Note**: 
- IP correcte: `10.43.62.249`
- Format complet avec `/v1/chat/completions`
- Modèle: `gemma3:270m` (nom Ollama, pas le nom de fichier)

---

## 📱 MÉTHODE 1: Via la Webapp (RECOMMANDÉ)

### Étapes

1. **Ouvrir l'app ChatAI** sur votre device

2. **Aller dans Configuration**
   - Ouvrir le menu de configuration
   - Sélectionner l'onglet **Local** (💻 Local)

3. **Modifier l'URL**
   - Dans le champ **URL**, remplacer:
     - ❌ `http://127.0.0.1:11434`
     - ✅ `http://10.43.62.249:11434/v1/chat/completions`

4. **Modifier le modèle** (si nécessaire)
   - Dans le champ **Modèle**, remplacer:
     - ❌ `gemma3-270m.gguf`
     - ✅ `gemma3:270m`
   - OU choisir dans le select:
     - `llama3.2:3b`
     - `gemma3:1b`

5. **Sauvegarder**
   - Cliquer sur le bouton **Sauvegarder**

6. **Tester** (si bouton disponible)
   - Cliquer sur **Tester connexion** (à ajouter dans la refonte)
   - OU utiliser directement KITT pour vérifier

---

## 💻 MÉTHODE 2: Via ADB (Si la webapp ne fonctionne pas)

### Commandes PowerShell

```powershell
# 1. Sauvegarder la configuration actuelle (backup)
adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml" > backup_config_$(Get-Date -Format 'yyyyMMdd_HHmmss').xml

# 2. Lire la configuration actuelle
adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml"

# 3. Modifier l'URL (méthode simple)
adb shell "run-as com.chatai sh -c 'sed -i \"s|<string name=\"local_server_url\">http://127.0.0.1:11434</string>|<string name=\"local_server_url\">http://10.43.62.249:11434/v1/chat/completions</string>|g\" shared_prefs/chatai_ai_config.xml'"

# 4. Modifier le modèle (si nécessaire)
adb shell "run-as com.chatai sh -c 'sed -i \"s|<string name=\"local_model_name\">gemma3-270m.gguf</string>|<string name=\"local_model_name\">gemma3:270m</string>|g\" shared_prefs/chatai_ai_config.xml'"

# 5. Vérifier la modification
adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml | grep -E 'local_server_url|local_model_name'"
```

### ⚠️ Note sur sed

Si `sed` n'est pas disponible sur le device, utilisez plutôt:

```powershell
# Alternative: Écrire directement le fichier complet
# (plus complexe, nécessite de reconstruire tout le XML)
```

---

## 🔍 MÉTHODE 3: Script PowerShell Automatique

Créer un script `fix_ollama_url.ps1`:

```powershell
# Script de correction automatique de l'URL Ollama

$NEW_URL = "http://10.43.62.249:11434/v1/chat/completions"
$NEW_MODEL = "gemma3:270m"

Write-Host "=== Correction URL Ollama Local ===" -ForegroundColor Cyan

# Backup
$backupFile = "backup_config_$(Get-Date -Format 'yyyyMMdd_HHmmss').xml"
Write-Host "[1/4] Sauvegarde de la configuration..." -ForegroundColor Yellow
adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml" > $backupFile
Write-Host "  Backup sauvegardé: $backupFile" -ForegroundColor Green

# Lire config actuelle
Write-Host "[2/4] Lecture configuration actuelle..." -ForegroundColor Yellow
$currentConfig = adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml"
$currentUrl = ($currentConfig | Select-String 'local_server_url').ToString()
Write-Host "  URL actuelle: $currentUrl" -ForegroundColor Gray

# Modifier (via Python script ou reconstruction XML)
# ... (voir méthode 4 pour approche plus robuste)

Write-Host "[3/4] Application des modifications..." -ForegroundColor Yellow
# À implémenter selon méthode choisie

# Vérifier
Write-Host "[4/4] Vérification..." -ForegroundColor Yellow
$newConfig = adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml"
$newUrl = ($newConfig | Select-String 'local_server_url').ToString()
Write-Host "  Nouvelle URL: $newUrl" -ForegroundColor Green

Write-Host "`n=== Correction terminée ===" -ForegroundColor Green
```

---

## 📝 VÉRIFICATION

### Après modification

**Vérifier la configuration**:
```powershell
adb shell "run-as com.chatai cat shared_prefs/chatai_ai_config.xml | grep -E 'local_server_url|local_model_name'"
```

**Résultat attendu**:
```xml
<string name="local_server_url">http://10.43.62.249:11434/v1/chat/completions</string>
<string name="local_model_name">gemma3:270m</string>
```

**Tester depuis l'app**:
- Ouvrir KITT
- Poser une question
- Vérifier que la connexion au serveur Ollama fonctionne

---

## 🔗 RÉFÉRENCES

- `docs/VOTRE_IP_PC_OLLAMA.md` - Votre IP et modèles disponibles
- `docs/PROBLEME_OLLAMA_LOCAL_IDENTIFIE.md` - Diagnostic du problème


