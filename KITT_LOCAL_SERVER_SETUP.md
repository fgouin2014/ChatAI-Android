# Configuration du Serveur Local pour KITT

KITT peut maintenant utiliser un serveur LLM local (gratuit, privé, sans limites) au lieu de Hugging Face !

## Solutions recommandées

### Option 1 : Ollama (Recommandé - Le plus simple)

**Avantages :**
- Gratuit et open-source
- API compatible OpenAI
- Installation en 2 minutes
- Modèles optimisés (Llama 3.2, Mistral, Gemma, etc.)
- Fonctionne sur CPU ou GPU

**Installation :**

1. **Télécharger Ollama**
   - Windows : https://ollama.ai/download/windows
   - Mac : https://ollama.ai/download/mac
   - Linux : `curl -fsSL https://ollama.com/install.sh | sh`

2. **Installer un modèle**
   ```bash
   # Llama 3.2 (3B - rapide, fonctionne sur CPU)
   ollama pull llama3.2
   
   # OU Mistral (7B - meilleur qualité)
   ollama pull mistral
   
   # OU Gemma 2 (2B - très léger)
   ollama pull gemma2:2b
   ```

3. **Démarrer le serveur**
   ```bash
   # Le serveur démarre automatiquement
   # Par défaut sur http://localhost:11434
   ```

4. **Tester**
   ```bash
   curl http://localhost:11434/api/generate -d '{
     "model": "llama3.2",
     "prompt": "Hello KITT!"
   }'
   ```

5. **Configuration dans ChatAI**
   
   Vous pouvez configurer manuellement dans `SharedPreferences` :
   
   ```bash
   # Via adb shell
   adb shell
   
   # Accéder aux préférences
   run-as com.chatai
   cd shared_prefs
   
   # Éditer le fichier chatai_ai_config.xml
   vi chatai_ai_config.xml
   ```
   
   Ajoutez ces lignes :
   ```xml
   <string name="local_server_url">http://192.168.1.XXX:11434/v1/chat/completions</string>
   <string name="local_model_name">llama3.2</string>
   ```
   
   **Note :** Remplacez `192.168.1.XXX` par l'IP de votre PC sur le réseau local.

---

### Option 2 : LM Studio

**Avantages :**
- Interface graphique belle
- API compatible OpenAI
- Téléchargement de modèles intégré
- Monitoring en temps réel

**Installation :**

1. **Télécharger LM Studio**
   - Site : https://lmstudio.ai
   - Disponible pour Windows, Mac, Linux

2. **Télécharger un modèle**
   - Ouvrir LM Studio
   - Onglet "Discover"
   - Rechercher : `llama-3.2-3b-instruct` ou `mistral-7b-instruct`
   - Télécharger (format GGUF)

3. **Démarrer le serveur local**
   - Onglet "Local Server"
   - Sélectionner le modèle
   - Cliquer "Start Server"
   - Par défaut : `http://localhost:1234`

4. **Configuration dans ChatAI**
   ```xml
   <string name="local_server_url">http://192.168.1.XXX:1234/v1/chat/completions</string>
   <string name="local_model_name">llama-3.2-3b-instruct</string>
   ```

---

## Trouver l'IP de votre PC

### Windows
```powershell
ipconfig
# Cherchez "IPv4 Address" (ex: 192.168.1.100)
```

### Mac/Linux
```bash
ifconfig
# OU
ip addr show
# Cherchez l'IP commençant par 192.168.x.x ou 10.0.x.x
```

---

## Test manuel via adb

Pour tester directement depuis votre téléphone :

```bash
# Installer l'app avec le serveur local
adb install -r app-debug.apk

# Configurer manuellement
adb shell
run-as com.chatai
cd shared_prefs
echo '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map>
    <string name="local_server_url">http://192.168.1.100:11434/v1/chat/completions</string>
    <string name="local_model_name">llama3.2</string>
</map>' > chatai_ai_config.xml
exit
exit

# Relancer l'app
adb shell am force-stop com.chatai
adb shell monkey -p com.chatai -c android.intent.category.LAUNCHER 1
```

---

## Ordre de priorité des APIs (v2.5)

KITT essaie les APIs dans cet ordre :

1. **OpenAI** (si clé API configurée)
2. **Anthropic Claude** (si clé API configurée)
3. **Serveur Local** (Ollama/LM Studio) ⭐ **NOUVEAU**
4. **Hugging Face** (souvent 404 en 2025)
5. **Fallback local** (réponses pré-programmées)

---

## Avantages du serveur local

✅ **Gratuit** : Pas de coût par requête  
✅ **Privé** : Vos données ne quittent pas votre réseau  
✅ **Rapide** : Pas de latence internet  
✅ **Illimité** : Aucune limite de quota  
✅ **Offline** : Fonctionne sans internet  

---

## Modèles recommandés

| Modèle | Taille | Vitesse | Qualité | RAM min |
|--------|--------|---------|---------|---------|
| **gemma2:2b** | 2B | ⚡⚡⚡ | ⭐⭐ | 4 GB |
| **llama3.2:3b** | 3B | ⚡⚡ | ⭐⭐⭐ | 6 GB |
| **mistral:7b** | 7B | ⚡ | ⭐⭐⭐⭐ | 8 GB |
| **llama3.1:8b** | 8B | ⚡ | ⭐⭐⭐⭐ | 10 GB |

**Pour CPU uniquement** : Choisissez gemma2:2b ou llama3.2:3b  
**Avec GPU** : mistral:7b offre le meilleur ratio qualité/vitesse

---

## Diagnostic

Pour voir les logs de connexion au serveur local :

```bash
adb logcat -s KittAI:I | Select-String "Local Server"
```

Dans le diagnostic de l'app, vous verrez :
```
[3] Local Server: Attempting...
    - URL: http://192.168.1.100:11434/v1/chat/completions
    - Model: llama3.2
    - HTTP response: 200
    - Response: Certainly, Michael. I am ready to assist...
[3] Local Server: SUCCESS
```

---

## Résolution de problèmes

### "Local Server: Not configured"
➜ Vous n'avez pas configuré `local_server_url` dans les préférences

### "Connection refused" ou timeout
➜ Vérifiez que :
- Le serveur est démarré (Ollama ou LM Studio)
- L'IP est correcte
- Le téléphone et le PC sont sur le même réseau WiFi
- Le firewall Windows n'est pas bloquant (port 11434 ou 1234)

### "HTTP 404 Not Found"
➜ L'URL est incorrecte. Format correct :
- Ollama : `http://IP:11434/v1/chat/completions`
- LM Studio : `http://IP:1234/v1/chat/completions`

### Réponse lente
➜ Le modèle est trop gros pour votre CPU. Essayez :
- Un modèle plus petit (gemma2:2b)
- Activer le GPU dans Ollama : `OLLAMA_GPU=1 ollama serve`

---

## TODO pour l'interface

Les champs de configuration ne sont pas encore dans l'interface graphique.  
Pour l'instant, la configuration se fait manuellement via `adb`.

**Prochaines étapes :**
1. Ajouter les champs dans `activity_ai_configuration.xml`
2. Décommenter les lignes dans `AIConfigurationActivity.kt`
3. Ajouter un bouton "Test Local Server"

