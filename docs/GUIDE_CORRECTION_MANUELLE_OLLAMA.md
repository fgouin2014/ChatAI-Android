# 📱 Guide Correction Manuelle - Ollama Local

**Date**: 2025-11-27  
**Statut**: ✅ **URL DÉJÀ CORRECTE** - Vérifier le modèle

---

## ✅ BONNE NOUVELLE

**Votre URL est déjà correcte !**
```
local_server_url: http://10.43.62.249:11434/v1/chat/completions
```

**Aucune modification nécessaire pour l'URL.**

---

## ⚠️ À VÉRIFIER: Nom du Modèle

**Configuration actuelle**:
```
local_model_name: gemma3-270m.gguf
```

**Problème potentiel**:
- Ollama utilise `gemma3:270m` (avec deux-points)
- Le code peut utiliser `gemma3-270m.gguf` (nom de fichier)

---

## 🔧 CORRECTION MANUELLE - Méthode Simple

### Étape 1: Ouvrir l'app ChatAI

- Sur votre device Android
- Ouvrir l'application ChatAI

### Étape 2: Aller dans Configuration → Local

- Ouvrir le menu de configuration (icône ⚙️)
- Cliquer sur l'onglet **💻 Local**

### Étape 3: Vérifier l'URL

**L'URL devrait déjà être**:
```
http://10.43.62.249:11434/v1/chat/completions
```

✅ **Si c'est correct, ne rien changer**

❌ **Si vous voyez** `http://127.0.0.1:11434`, changer en:
```
http://10.43.62.249:11434/v1/chat/completions
```

### Étape 4: Vérifier/Corriger le Modèle

**Dans le champ "Modèle"**:

**Option A**: Si le select fonctionne
- Choisir un modèle dans la liste
- OU entrer manuellement: `gemma3:270m`

**Option B**: Si vous devez entrer manuellement
- Effacer le champ actuel
- Entrer: `gemma3:270m`
- **Note**: Utiliser deux-points (`:`), pas tiret (`-`)

### Étape 5: Sauvegarder

- Cliquer sur le bouton **💾 Sauvegarder**
- Attendre confirmation

### Étape 6: Tester

- Utiliser KITT pour poser une question
- Vérifier que le serveur Ollama répond

---

## 🔍 MODÈLES DISPONIBLES SUR VOTRE SERVEUR

| Nom Ollama | Description |
|------------|-------------|
| `gemma3:270m` | 278 MB - Rapide et léger ⭐ |
| `llama3.2:3b` | 1.93 GB - Meilleure qualité |
| `gemma3:1b` | 778 MB - Très léger |

**Recommandation**: Utiliser `gemma3:270m`

---

## ❓ SI ÇA NE MARCHE TOUJOURS PAS

### Vérifications supplémentaires

1. **Vérifier que le serveur Ollama tourne sur le PC**
   ```bash
   ollama list
   ```

2. **Vérifier que le device et le PC sont sur le même WiFi**
   - Device Android: Même réseau WiFi que le PC
   - IP du PC: `10.43.62.249`

3. **Vérifier le pare-feu du PC**
   ```powershell
   netsh advfirewall firewall add rule name="Ollama" dir=in action=allow protocol=TCP localport=11434
   ```

4. **Tester la connexion depuis le device**
   - Ouvrir un navigateur sur le device
   - Aller à: `http://10.43.62.249:11434/api/tags`
   - Doit afficher la liste des modèles

---

## 📝 CONFIGURATION FINALE ATTENDUE

**Dans l'onglet Local**:
- **URL**: `http://10.43.62.249:11434/v1/chat/completions`
- **Modèle**: `gemma3:270m` (ou autre modèle Ollama installé)

**Dans SharedPreferences**:
```xml
<string name="local_server_url">http://10.43.62.249:11434/v1/chat/completions</string>
<string name="local_model_name">gemma3:270m</string>
```

---

## 🔗 RÉFÉRENCES

- `docs/VOTRE_IP_PC_OLLAMA.md` - Votre IP et modèles
- `docs/STATUT_CONFIGURATION_OLLAMA.md` - Statut configuration


