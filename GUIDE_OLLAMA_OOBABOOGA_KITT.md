# Guide Complet : KITT + Ollama + Oobabooga

Vous avez choisi les options **#3**, **#5** et **#7** :
- **#3** : Ollama Cloud (modèles puissants hébergés)
- **#5** : Ollama Local (modèles sur votre PC)
- **#7** : Oobabooga Text Gen WebUI (maximum de contrôle)

**Bonne nouvelle :** Les 3 utilisent la **même configuration** dans ChatAI !

---

## 📱 Interface de Configuration

Dans ChatAI, allez dans **Configuration → IA**, vous verrez maintenant une nouvelle section :

```
🏠 SERVEUR LOCAL (Ollama/LM Studio)
┌──────────────────────────────────────────────┐
│ URL du serveur local                         │
│ http://192.168.1.100:11434/v1/chat/completions│
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ Nom du modèle local                          │
│ llama3.2 ou gpt-oss:20b-cloud                │
└──────────────────────────────────────────────┘

💡 Gratuit, privé et illimité ! 
   Configurez Ollama ou LM Studio sur votre PC.
```

---

## 🚀 Option #3 : Ollama Cloud

### Qu'est-ce que c'est ?
Modèles **puissants hébergés** par Ollama (GPT-OSS 120B, DeepSeek 671B).

### Avantages
✅ **Gratuit** (tier limité) ou Pro 20$/mois  
✅ **Aucun GPU requis** sur votre PC  
✅ **Privé** - Ollama ne garde pas vos données  
✅ **Puissant** - Jusqu'à 671 milliards de paramètres  

### Installation (Quand Ollama est téléchargé)

**1. Lancer Ollama et se connecter au cloud**
```bash
# Dans un terminal/PowerShell
ollama signin
# → Créer un compte sur ollama.com si besoin
```

**2. Tester un modèle cloud**
```bash
# Modèles cloud gratuits recommandés :
ollama run gpt-oss:20b-cloud      # 20B - Équilibré
ollama run glm-4.6:cloud          # 4B - Rapide
ollama run kimi-k2:1t-cloud       # 1T - Énorme !

# Si vous avez Pro ($20/mois) :
ollama run gpt-oss:120b-cloud     # 120B - Très puissant
ollama run deepseek-v3.1:671b-cloud # 671B - Le plus puissant
```

**3. Trouver l'IP de votre PC**
```powershell
ipconfig
# → Cherchez "IPv4 Address" (ex: 192.168.1.100)
```

**4. Configurer dans ChatAI**
- Ouvrir ChatAI → Configuration → IA
- Section "SERVEUR LOCAL"
- **URL** : `http://192.168.1.100:11434/v1/chat/completions`
- **Modèle** : `gpt-oss:20b-cloud`
- Cliquer **SAUVEGARDER**
- Cliquer **TESTER APIs**

**Vous devriez voir :**
```
[3] Local Server: Attempting...
    - URL: http://192.168.1.100:11434/v1/chat/completions
    - Model: gpt-oss:20b-cloud
    - HTTP response: 200
    - Response: Certainly, Michael. I am ready...
[3] Local Server: SUCCESS ✓
```

---

## 🏠 Option #5 : Ollama Local

### Qu'est-ce que c'est ?
Modèles **tournant sur votre PC** - 100% privé et gratuit.

### Avantages
✅ **100% Gratuit**  
✅ **Totalement privé** - Rien ne quitte votre réseau  
✅ **Offline** - Fonctionne sans internet  
✅ **Flexible** - Vous choisissez vos modèles  

### Installation (Quand Ollama est téléchargé)

**1. Télécharger des modèles locaux**

Pour **CPU uniquement** (pas de GPU) :
```bash
ollama pull llama3.2:3b    # 3B - Bon équilibre (2 GB RAM)
ollama pull gemma2:2b      # 2B - Très léger (1.5 GB RAM)
ollama pull phi3:mini      # 3.8B - Performant (2.3 GB RAM)
```

Pour **avec GPU** (NVIDIA/AMD) :
```bash
ollama pull llama3.1:8b    # 8B - Excellent (5 GB VRAM)
ollama pull mistral:7b     # 7B - Très bon (4 GB VRAM)
ollama pull qwen2.5:14b    # 14B - Puissant (8 GB VRAM)
```

**2. Tester un modèle**
```bash
ollama run llama3.2:3b
# → Si ça marche, le serveur est prêt !
```

**3. Configurer dans ChatAI**
- **URL** : `http://VOTRE_IP:11434/v1/chat/completions`
- **Modèle** : `llama3.2:3b` (ou celui que vous avez téléchargé)
- **SAUVEGARDER** → **TESTER APIs**

### Modèles recommandés

| Modèle | Taille | RAM/VRAM | Vitesse | Qualité | Usage |
|--------|--------|----------|---------|---------|-------|
| **gemma2:2b** | 2B | 1.5 GB | ⚡⚡⚡ | ⭐⭐ | CPU faible |
| **llama3.2:3b** | 3B | 2 GB | ⚡⚡⚡ | ⭐⭐⭐ | CPU moyen |
| **phi3:mini** | 3.8B | 2.3 GB | ⚡⚡ | ⭐⭐⭐ | Équilibré |
| **mistral:7b** | 7B | 4 GB | ⚡⚡ | ⭐⭐⭐⭐ | GPU entry |
| **llama3.1:8b** | 8B | 5 GB | ⚡⚡ | ⭐⭐⭐⭐ | GPU mid |
| **qwen2.5:14b** | 14B | 8 GB | ⚡ | ⭐⭐⭐⭐⭐ | GPU high |

---

## 🎮 Option #7 : Oobabooga Text Generation WebUI

### Qu'est-ce que c'est ?
Interface web **ultra-configurable** pour LLM, support des gros modèles (70B+).

### Avantages
✅ **Maximum de contrôle** (température, top-p, répétition, etc.)  
✅ **Gros modèles** - Jusqu'à 70B+ avec quantization  
✅ **Multi-backends** - llama.cpp, ExLlama, GPTQ, AWQ  
✅ **Extensions** - RAG, websearch, TTS, etc.  

### Installation (Plus complexe - pour power users)

**1. Installer Oobabooga**
```bash
# Windows - Télécharger depuis :
# https://github.com/oobabooga/text-generation-webui/releases

# Ou clone + install
git clone https://github.com/oobabooga/text-generation-webui
cd text-generation-webui
start_windows.bat  # Windows
# OU
./start_linux.sh   # Linux
# OU
./start_macos.sh   # Mac
```

**2. Télécharger un modèle**
- Ouvrir http://localhost:7860
- Onglet "Model" → "Download"
- Modèles recommandés :
  - `TheBloke/Llama-2-13B-chat-GGUF` (13B - bon équilibre)
  - `TheBloke/Mistral-7B-Instruct-v0.2-GGUF` (7B - rapide)
  - `TheBloke/WizardLM-70B-V1.0-GGUF` (70B - très puissant, GPU requis)

**3. Activer l'API OpenAI**
- Onglet "Session"
- Cocher "api" et "openai" dans Extensions
- Ou démarrer avec :
```bash
start_windows.bat --api --extensions openai
```

**4. Vérifier l'API**
L'API devrait être sur : `http://localhost:5000`

**5. Configurer dans ChatAI**
- **URL** : `http://VOTRE_IP:5000/v1/chat/completions`
- **Modèle** : Le nom du modèle chargé dans Oobabooga
- **SAUVEGARDER** → **TESTER APIs**

---

## 🔧 Résolution de Problèmes

### "Connection refused" ou timeout
**Cause :** Le téléphone ne peut pas atteindre le PC

**Solutions :**
1. Vérifier que le PC et le téléphone sont sur le **même WiFi**
2. Trouver l'IP correcte du PC : `ipconfig` (Windows)
3. Désactiver le **Firewall** temporairement pour tester :
   ```powershell
   # Windows - Autoriser le port 11434 (Ollama)
   netsh advfirewall firewall add rule name="Ollama" dir=in action=allow protocol=TCP localport=11434
   ```
4. Vérifier que le serveur tourne :
   - Ollama : `ollama list` doit montrer les modèles
   - Oobabooga : http://localhost:7860 doit s'ouvrir

### "Model not found" ou "HTTP 404"
**Cause :** Le modèle n'est pas installé

**Solutions :**
- Ollama Cloud : `ollama pull gpt-oss:20b-cloud`
- Ollama Local : `ollama pull llama3.2:3b`
- Oobabooga : Télécharger via l'interface web

### "HTTP 500" ou erreur serveur
**Cause :** Le modèle est trop gros pour votre RAM/VRAM

**Solutions :**
- Télécharger un modèle plus petit
- Ou utiliser une version quantifiée (Q4, Q5)
- Ollama : `ollama pull llama3.2:3b-q4_0`

### Réponse très lente
**Cause :** Le modèle tourne sur CPU au lieu de GPU

**Solutions :**
- Ollama : Vérifier les logs `ollama serve` pour voir si GPU est détecté
- Ou utiliser un modèle plus petit (2-3B au lieu de 7B)
- Activer GPU : `OLLAMA_GPU=1 ollama serve`

---

## 📊 Comparaison des 3 Options

| Critère | #3 Ollama Cloud | #5 Ollama Local | #7 Oobabooga |
|---------|----------------|----------------|--------------|
| **Setup** | ⚡ 10 min | ⚡ 15 min | ⚠️ 30 min |
| **Coût** | Gratuit/20$ | Gratuit | Gratuit |
| **Vitesse** | ⚡⚡⚡⚡ | ⚡⚡⚡ | ⚡⚡⚡⚡ |
| **Qualité** | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Privé** | ✅ | ✅✅ | ✅✅ |
| **Offline** | ❌ | ✅ | ✅ |
| **GPU requis** | ❌ | ❌ | Optionnel |
| **Contrôle** | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

---

## 🎯 Configuration Recommandée

**Pour la majorité des utilisateurs :**
```
URL: http://192.168.1.100:11434/v1/chat/completions
Modèle: gpt-oss:20b-cloud (si vous avez Ollama Cloud)
     OU llama3.2:3b (si local uniquement)
```

**Pour les power users avec GPU :**
```
URL: http://192.168.1.100:5000/v1/chat/completions
Modèle: Mistral-7B-Instruct-v0.2 (via Oobabooga)
```

---

## ✅ Prochaines Étapes

1. **Attendre qu'Ollama finisse de télécharger** (~1h restant)
2. **Suivre Option #3** (Ollama Cloud - le plus simple)
3. **Tester KITT** avec "TESTER APIs"
4. **Optionnel** : Installer des modèles locaux (#5) ou Oobabooga (#7)

---

**KITT sera opérationnel dès qu'Ollama sera installé !** 🚀

