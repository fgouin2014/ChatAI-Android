# 🚀 Guide Démarrage Ollama Local

**Date**: 2025-11-27  
**Problème**: KITT dit "Le serveur n'est pas démarré port :11434"

---

## ❌ PROBLÈME

**Message d'erreur KITT**:
> "Le serveur Ollama local n'est pas démarré (port 11434)"

**Causes possibles**:
1. Ollama n'est pas installé sur votre PC
2. Ollama n'est pas démarré
3. URL mal configurée dans l'app
4. Firewall bloque le port 11434

---

## ✅ SOLUTIONS

### 1. Installer Ollama

**Windows**:
1. Télécharger: https://ollama.ai/download/windows
2. Installer l'exécutable
3. Ollama démarre automatiquement

**Vérifier installation**:
```bash
ollama --version
```

---

### 2. Démarrer Ollama

**Si Ollama est installé mais ne démarre pas automatiquement**:

```bash
# Windows (PowerShell)
ollama serve

# Linux/Mac
ollama serve
```

**Vérifier que le serveur écoute**:
```bash
curl http://localhost:11434/api/tags
```

**Réponse attendue**:
```json
{"models":[...]}
```

---

### 3. Configurer l'URL dans ChatAI

**Option A: Via webapp (recommandé)**
1. Ouvrir l'app ChatAI
2. Aller dans **Configuration** → **Local**
3. Entrer l'URL: `http://VOTRE_IP:11434/v1/chat/completions`
   - Exemple: `http://192.168.1.100:11434/v1/chat/completions`
4. Sauvegarder

**Option B: Via ADB**
```bash
adb shell "run-as com.chatai sh -c 'echo \"local_server_url=http://192.168.1.100:11434/v1/chat/completions\" >> shared_prefs/chatai_ai_config.xml'"
```

**Trouver l'IP de votre PC**:
- Windows: `ipconfig` → Adresse IPv4
- Linux/Mac: `ifconfig` ou `ip addr`

---

### 4. Installer un modèle Ollama

**Modèles recommandés**:
```bash
# Léger (3B)
ollama pull llama3.2

# Moyen (7B)
ollama pull mistral

# Ou utiliser le modèle configuré dans l'app
ollama pull gemma3-270m
```

**Vérifier modèles installés**:
```bash
ollama list
```

---

### 5. Vérifier le firewall

**Windows**:
1. Ouvrir **Pare-feu Windows Defender**
2. **Paramètres avancés** → **Règles de trafic entrant**
3. **Nouvelle règle** → **Port** → **TCP** → **11434**
4. Autoriser la connexion

**Linux**:
```bash
sudo ufw allow 11434/tcp
```

---

## 🧪 TEST

**Depuis PC**:
```bash
curl http://localhost:11434/api/tags
```

**Depuis Android (via ADB)**:
```bash
adb shell "curl http://192.168.1.100:11434/api/tags"
```

**Dans l'app**:
- Aller dans **Diagnostics** → Vérifier statut Ollama Local

---

## 📋 CONFIGURATION COMPLÈTE

**URL serveur local**: `http://VOTRE_IP:11434/v1/chat/completions`  
**Modèle local**: `llama3.2` (ou autre modèle installé)  
**Port**: `11434` (par défaut Ollama)

---

## ⚠️ NOTES IMPORTANTES

1. **IP locale**: Utiliser l'IP de votre PC sur le réseau local (pas `127.0.0.1` ou `localhost`)
2. **Format URL**: Doit se terminer par `/v1/chat/completions` (format OpenAI-compatible)
3. **Réseau**: Le PC et l'appareil Android doivent être sur le même réseau WiFi

---

**Une fois Ollama démarré et configuré, KITT pourra utiliser votre serveur local !** ✅


