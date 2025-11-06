# Setup Rapide : KITT + Ollama Cloud

## Étape 1 : Installation Ollama (en cours...)

Téléchargement de 1 Go en cours (~1 heure restant).

---

## Étape 2 : Configuration Ollama (après installation)

Une fois Ollama installé, dans un terminal :

```bash
# 1. Se connecter à Ollama Cloud
ollama signin
# → Créer un compte sur ollama.com si besoin

# 2. Tester un modèle cloud gratuit
ollama run gpt-oss:20b-cloud

# Si ça fonctionne, vous verrez :
# >>> send a message... (or '/help' for commands)
```

**Modèles cloud gratuits recommandés :**
- `gpt-oss:20b-cloud` (20B) - Bon équilibre
- `glm-4.6:cloud` (4B) - Rapide
- `kimi-k2:1t-cloud` (1T !) - Gigantesque mais gratuit

---

## Étape 3 : Trouver l'IP de votre PC

**Windows :**
```powershell
ipconfig
# → Cherchez "IPv4 Address" sous votre adaptateur WiFi/Ethernet
# Exemple : 192.168.1.100
```

**Mac/Linux :**
```bash
ifconfig
# OU
ip addr show
# → Cherchez une IP commençant par 192.168.x.x ou 10.0.x.x
```

**Notez cette IP** : `__________` (vous en aurez besoin)

---

## Étape 4 : Configuration KITT

Une fois Ollama fonctionnel, configurez KITT avec cette commande :

```bash
# Remplacer VOTRE_IP par celle trouvée à l'étape 3
# Remplacer VOTRE_MODEL par celui que vous voulez (ex: gpt-oss:20b-cloud)

adb shell "run-as com.chatai sh -c 'cd shared_prefs && cat > chatai_ai_config.xml << EOF
<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\" ?>
<map>
    <string name=\\\"local_server_url\\\">http://192.168.1.100:11434/v1/chat/completions</string>
    <string name=\\\"local_model_name\\\">gpt-oss:20b-cloud</string>
</map>
EOF
'"
```

**Modifiez les valeurs :**
- `192.168.1.100` → votre IP trouvée à l'étape 3
- `gpt-oss:20b-cloud` → le modèle Ollama que vous voulez

---

## Étape 5 : Redémarrer ChatAI

```bash
adb shell am force-stop com.chatai
adb shell monkey -p com.chatai -c android.intent.category.LAUNCHER 1
```

---

## Étape 6 : Tester KITT

1. **Ouvrir ChatAI** sur votre téléphone
2. **Aller dans Configuration → IA**
3. **Cliquer "TESTER LES APIS"**

Dans les logs détaillés, vous devriez voir :

```
[3] Local Server: Attempting...
    - URL: http://192.168.1.100:11434/v1/chat/completions
    - Model: gpt-oss:20b-cloud
    - HTTP response: 200
    - Response: Certainly, Michael. I am ready...
[3] Local Server: SUCCESS
```

**KITT devrait répondre avec l'intelligence d'Ollama !** 🎉

---

## Dépannage

### "Connection refused" ou timeout
➜ Vérifiez que :
- Ollama est démarré (`ollama serve`)
- L'IP est correcte
- Téléphone et PC sur le même WiFi
- Firewall Windows ne bloque pas le port 11434

### "Model not found"
➜ Le modèle n'est pas installé. Faites :
```bash
ollama pull gpt-oss:20b-cloud
```

### "HTTP 404"
➜ URL incorrecte. Format correct :
```
http://IP:11434/v1/chat/completions
```

### Ollama ne répond pas
➜ Vérifiez que le serveur est actif :
```bash
# Dans un terminal
ollama serve
```

---

## Commandes Ollama utiles

```bash
# Lister les modèles installés
ollama list

# Télécharger un modèle
ollama pull nom-du-modele

# Exécuter un modèle interactif
ollama run nom-du-modele

# Stopper le serveur
# Ctrl+C dans le terminal où ollama serve tourne
```

---

## Prochaines étapes (optionnel)

Une fois que tout fonctionne, je peux :
1. ✅ Ajouter une interface graphique pour configurer facilement
2. ✅ Ajouter un bouton "Test Ollama" dans les réglages
3. ✅ Afficher le statut du serveur local dans l'app
4. ✅ Gérer plusieurs modèles (switch rapide)

---

**Pendant le téléchargement d'Ollama, je peux commencer l'interface graphique si vous voulez !**

