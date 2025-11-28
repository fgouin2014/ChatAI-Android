# ❌ Problème Identifié - Serveur Ollama Local

**Date**: 2025-11-27  
**Statut**: ✅ **PROBLÈME IDENTIFIÉ** - Solution proposée

---

## 🔍 DIAGNOSTIC

### Configuration actuelle (d'après SharedPreferences)

```xml
<string name="local_server_url">http://127.0.0.1:11434</string>
<string name="local_model_name">gemma3-270m.gguf</string>
<string name="ai_mode">local</string>
<boolean name="use_ollama_cloud" value="false" />
```

### ❌ PROBLÈME PRINCIPAL

**URL incorrecte**: `http://127.0.0.1:11434`

**Pourquoi ça ne fonctionne pas**:
- `127.0.0.1` = localhost = le device Android lui-même
- Le serveur Ollama tourne sur le **PC**, pas sur le device
- Le device Android essaie de se connecter à lui-même au lieu du PC
- **Résultat**: `Connection refused` ou `Timeout`

**Explication technique**:
- `127.0.0.1` (localhost) pointe toujours vers la machine locale
- Sur un device Android, `127.0.0.1` = le device Android
- Sur un PC, `127.0.0.1` = le PC
- Pour que le device Android se connecte au PC, il faut utiliser l'**IP réseau du PC** (ex: `192.168.1.100`)

---

## ✅ SOLUTION IMMÉDIATE

### Étape 1: Trouver l'IP de votre PC

**Windows**:
```powershell
ipconfig
# Cherchez "Adresse IPv4" (ex: 192.168.1.100)
```

**Linux/Mac**:
```bash
ifconfig
# OU
ip addr
```

### Étape 2: Configurer l'URL correcte

**Format correct**:
```
http://VOTRE_IP_PC:11434/v1/chat/completions
```

**Exemple**:
```
http://192.168.1.100:11434/v1/chat/completions
```

### Étape 3: Vérifier que le serveur Ollama écoute

**Sur votre PC**:
```bash
# Vérifier que Ollama tourne
ollama list

# Vérifier que le serveur écoute sur le réseau (pas seulement localhost)
# Ollama devrait écouter sur 0.0.0.0:11434 (toutes interfaces)
```

**Si Ollama n'écoute que sur localhost**:
- Modifier la configuration Ollama pour écouter sur toutes les interfaces
- Ou utiliser un tunnel SSH (solution alternative)

### Étape 4: Vérifier le pare-feu

**Windows**:
```powershell
# Autoriser le port 11434 dans le pare-feu
netsh advfirewall firewall add rule name="Ollama" dir=in action=allow protocol=TCP localport=11434
```

**Linux**:
```bash
# Autoriser le port 11434
sudo ufw allow 11434/tcp
```

### Étape 5: Tester la connexion depuis le device

**Depuis le device Android**:
- Ouvrir l'app ChatAI
- Aller dans Configuration → Local
- Entrer l'URL: `http://192.168.1.100:11434/v1/chat/completions`
- Entrer le modèle: `gemma3-270m` (ou le modèle installé sur PC)
- Sauvegarder
- Tester la connexion (bouton à ajouter)

---

## 🔧 SOLUTION À LONG TERME (Refonte onglet Local)

Voir `docs/PLAN_REFONTE_ONGLET_LOCAL.md` pour:
- Validation automatique de l'URL
- Détection de `127.0.0.1` avec avertissement
- Messages d'aide contextuels
- Bouton "Tester connexion"
- Affichage statut serveur

---

## 📝 NOTES

- Le placeholder actuel (`http://127.0.0.1:11434`) est **trompeur** pour un device Android
- L'utilisateur doit comprendre qu'il faut utiliser l'IP du PC, pas localhost
- La refonte de l'onglet Local ajoutera des validations et des messages d'aide pour éviter cette confusion

---

## 🔗 RÉFÉRENCES

- `docs/PLAN_REFONTE_ONGLET_LOCAL.md` - Plan de refonte
- `docs/AUDIT_COMPLET_ONGLET_CONFIGURATION.md` - Audit complet
- `docs/GUIDE_DEMARRAGE_OLLAMA.md` - Guide démarrage Ollama


