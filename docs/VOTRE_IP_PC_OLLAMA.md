# 🌐 Votre IP PC - Configuration Ollama Local

**Date**: 2025-11-27  
**Statut**: ✅ **ADRESSE IDENTIFIÉE**

---

## 📍 ADRESSES IP DÉTECTÉES

### Adresse principale (Wi-Fi) ⭐ **À UTILISER**

**IP**: `10.43.62.249`  
**Interface**: Wi-Fi  
**Utilisation**: **C'est cette adresse qu'il faut utiliser !**

**URL Ollama à configurer**:
```
http://10.43.62.249:11434/v1/chat/completions
```

---

### Adresse secondaire (Switch virtuel)

**IP**: `172.23.96.1`  
**Interface**: vEthernet (Default Switch)  
**Utilisation**: Switch virtuel Hyper-V/Docker (ignorer)

---

## ✅ VÉRIFICATION SERVEUR OLLAMA

### ✅ Statut: **SERVEUR OLLAMA FONCTIONNEL**

**Vérifications effectuées**:
- ✅ Ollama écoute sur `0.0.0.0:11434` (accessible depuis le réseau)
- ✅ Port 11434 accessible depuis l'IP `10.43.62.249`
- ✅ Serveur répond correctement à l'API

**Connexions actives détectées**:
- Connexion établie depuis `10.43.62.217` (probablement votre device Android)
- Le serveur fonctionne correctement !

---

## 📋 MODÈLES DISPONIBLES SUR VOTRE SERVEUR OLLAMA

D'après la réponse de l'API, voici les modèles installés :

| Modèle | Taille | Paramètres | Format |
|--------|--------|------------|--------|
| **gemma3:270m** | 278 MB | 268.10M | GGUF Q8_0 |
| **llama3.2:3b** | 1.93 GB | 3.2B | GGUF Q4_K_M |
| **gemma3:1b** | 778 MB | 999.89M | GGUF Q4_K_M |

---

## ✅ CONFIGURATION RECOMMANDÉE

### Dans l'onglet Local de la webapp

**URL du serveur**:
```
http://10.43.62.249:11434/v1/chat/completions
```

**Modèle** (choisir parmi ceux disponibles):
- `gemma3:270m` ⭐ **Recommandé** (278 MB, rapide)
- `llama3.2:3b` (1.93 GB, meilleure qualité)
- `gemma3:1b` (778 MB, léger)

**Note**: Le modèle `gemma3:270m` correspond au fichier `gemma3-270m.gguf` sur votre device, mais pour le serveur Ollama PC, utilisez le nom `gemma3:270m` (avec deux-points, pas de tiret).

---

## 🔍 VÉRIFICATIONS

### 1. Vérifier que Ollama écoute sur le réseau

**Sur votre PC**:
```bash
# Vérifier que Ollama tourne
ollama list

# Vérifier que le port 11434 est ouvert
netstat -an | findstr 11434
```

**Doit afficher**:
```
TCP    0.0.0.0:11434          0.0.0.0:0              LISTENING
```

Si vous voyez `127.0.0.1:11434` au lieu de `0.0.0.0:11434`, Ollama n'écoute que sur localhost et ne sera pas accessible depuis le device Android.

### 2. Vérifier le pare-feu

**Autoriser le port 11434**:
```powershell
netsh advfirewall firewall add rule name="Ollama" dir=in action=allow protocol=TCP localport=11434
```

### 3. Tester depuis le device

**URL de test**:
```
http://10.43.62.249:11434/api/tags
```

**Réponse attendue**:
```json
{"models":[...]}
```

---

## ⚠️ IMPORTANT

- **Utilisez `10.43.62.249`**, pas `127.0.0.1`
- **Format complet**: `http://10.43.62.249:11434/v1/chat/completions`
- **Vérifiez** que le device Android et le PC sont sur le **même réseau WiFi**

---

## 🔗 RÉFÉRENCES

- `docs/PROBLEME_OLLAMA_LOCAL_IDENTIFIE.md` - Problème identifié
- `docs/PLAN_REFONTE_ONGLET_LOCAL.md` - Plan de refonte

