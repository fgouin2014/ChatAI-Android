# 🧪 Guide de Test Coqui TTS Server

**Date**: 2025-11-23  
**Objectif**: Tester le serveur Coqui TTS et la connexion depuis l'app Android

---

## 📋 Prérequis

### 1. Python 3.9-3.11
**⚠️ IMPORTANT**: Coqui TTS nécessite Python 3.9, 3.10 ou 3.11 (pas 3.12+)

**Vérifier version**:
```bash
python --version  # ou python3 --version
```

**Si version incompatible**: Installer Python 3.11 depuis https://www.python.org/downloads/

### 2. Installation Coqui TTS

```bash
pip install TTS
```

**Vérifier installation**:
```bash
python -c "import TTS; print(TTS.__version__)"
```

---

## 🚀 Démarrage du Serveur

### Option 1: Script automatique (recommandé)

**Linux/Mac**:
```bash
chmod +x docs/start_coqui_tts_server.sh
./docs/start_coqui_tts_server.sh
```

**Windows PowerShell**:
```powershell
.\docs\start_coqui_tts_server.ps1
```

### Option 2: Commande manuelle

```bash
# Si commande tts-server disponible
tts-server --model_name tts_models/multilingual/multi-dataset/xtts_v2 --port 11401

# Ou via Python module
python -m TTS.server.server --model_name tts_models/multilingual/multi-dataset/xtts_v2 --port 11401
```

**Premier démarrage**:
- Le modèle XTTS-v2 (~1.7 GB) sera téléchargé automatiquement
- Cela peut prendre plusieurs minutes selon votre connexion
- Le modèle est sauvegardé dans le cache Python (pas besoin de re-télécharger)

---

## ✅ Test du Serveur

### 1. Vérifier que le serveur est démarré

Le serveur doit afficher quelque chose comme:
```
INFO:     Started server process [xxxxx]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:11401 (Press CTRL+C to quit)
```

### 2. Test API Mary-TTS (curl)

**Linux/Mac**:
```bash
curl "http://127.0.0.1:11401/process?INPUT_TEXT=Bonjour%20je%20suis%20KITT&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE" > test_output.wav
```

**Windows PowerShell**:
```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:11401/process?INPUT_TEXT=Bonjour%20je%20suis%20KITT&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE" -OutFile test_output.wav
```

**Vérifier le fichier**:
- `test_output.wav` doit être créé
- Taille: quelques KB à quelques dizaines de KB
- Ouvrir avec un lecteur audio pour vérifier le son

### 3. Test avec Python (optionnel)

```python
import requests

url = "http://127.0.0.1:11401/process"
params = {
    "INPUT_TEXT": "Bonjour, je suis KITT.",
    "INPUT_TYPE": "TEXT",
    "OUTPUT_TYPE": "AUDIO",
    "AUDIO": "WAVE_FILE"
}

response = requests.get(url, params=params)
if response.status_code == 200:
    with open("test_output.wav", "wb") as f:
        f.write(response.content)
    print("✅ Audio généré: test_output.wav")
else:
    print(f"❌ Erreur: {response.status_code}")
```

---

## 📱 Test depuis l'App Android

### 1. Configuration dans l'app

**Option A: Via webapp** (si UI configurée)
- Aller dans Configuration → TTS
- Sélectionner "Coqui Server"
- Endpoint: `http://127.0.0.1:11401/process`
- Modèle: `xtts_v2`
- Langue: `fr`

**Option B: Via SharedPreferences** (pour test rapide)
```java
// Dans Android Studio Debug Console ou via ADB
adb shell "run-as com.chatai sh -c 'cat > /data/data/com.chatai/shared_prefs/chatai_ai_config.xml << EOF
<?xml version=\"1.0\" encoding=\"utf-8\"?>
<map>
    <string name=\"tts_engine\">coqui_server</string>
    <string name=\"tts_endpoint\">http://127.0.0.1:11401/process</string>
    <string name=\"tts_model\">xtts_v2</string>
    <string name=\"tts_language\">fr</string>
</map>
EOF'"
```

### 2. Vérifier les logs

**Filtrer logs Coqui TTS**:
```bash
adb logcat | Select-String -Pattern "CoquiTTSClient|KittTTSManager"
```

**Logs attendus**:
- `CoquiTTSClient: Initialisation Coqui TTS Client...`
- `CoquiTTSClient: ✅ Coqui TTS Server disponible`
- `CoquiTTSClient: 🔊 Synthèse Coqui TTS: '...'`

### 3. Tester la synthèse

Dans l'app:
1. Démarrer KITT
2. Demander quelque chose à l'IA
3. Vérifier que la réponse est prononcée avec Coqui TTS
4. Vérifier les logs pour confirmer l'utilisation de Coqui

---

## 🔍 Dépannage

### Serveur ne démarre pas

**Erreur: "Python version not compatible"**
- Installer Python 3.9, 3.10 ou 3.11
- Utiliser un environnement virtuel si nécessaire

**Erreur: "TTS not installed"**
```bash
pip install TTS
# Vérifier
python -c "import TTS"
```

**Erreur: "Port already in use"**
- Changer le port dans le script: `--port 11402`
- Ou tuer le processus utilisant le port

### Serveur démarre mais Android ne se connecte pas

**Vérifier adresse IP**:
- Sur émulateur: `http://10.0.2.2:11401` (au lieu de `127.0.0.1`)
- Sur device physique: Utiliser l'IP de votre PC sur le réseau local
  - Windows: `ipconfig` → Adresse IPv4
  - Linux/Mac: `ifconfig` ou `ip addr`
  - Exemple: `http://192.168.1.100:11401`

**Vérifier firewall**:
- Autoriser le port 11401 dans le firewall Windows/Linux
- Vérifier que le serveur écoute sur `0.0.0.0` (pas seulement `127.0.0.1`)

**Vérifier logs Android**:
```bash
adb logcat | Select-String -Pattern "CoquiTTSClient.*error|CoquiTTSClient.*ERROR"
```

### Audio ne se joue pas

**Vérifier MediaPlayer**:
```bash
adb logcat | Select-String -Pattern "MediaPlayer|CoquiTTSClient.*playAudio"
```

**Vérifier permissions Android**:
- L'app doit avoir permission audio (normalement déjà accordée)

**Fallback Android TTS**:
- Si Coqui échoue, vérifier que Android TTS fonctionne en fallback
- Logs: `KittTTSManager: ⚠️ Coqui TTS erreur, fallback Android TTS`

---

## 📊 Résultats Attendus

### Serveur fonctionnel

✅ Serveur démarre sans erreur  
✅ Port 11401 accessible  
✅ Test curl génère fichier WAV valide  
✅ Logs Android: "Coqui TTS Server disponible"  
✅ Synthèse vocale fonctionne dans l'app  

### Performance

- **Première requête**: 5-15 secondes (chargement modèle)
- **Requêtes suivantes**: 1-3 secondes (modèle en mémoire)
- **Taille audio**: 10-50 KB pour phrase courte

---

## 🎯 Prochaines Étapes

Une fois le serveur testé et fonctionnel:

1. **Configuration UI**: Ajouter interface de configuration dans webapp
2. **Tests avancés**: Tester clone voix, émotions, différentes langues
3. **Optimisation**: Ajuster timeouts, cache, etc.

---

## 📚 Ressources

- **Documentation Coqui TTS**: https://docs.coqui.ai/
- **API Mary-TTS**: https://coqui-tts.readthedocs.io/en/latest/marytts.html
- **Modèles disponibles**: `tts --list_models`

