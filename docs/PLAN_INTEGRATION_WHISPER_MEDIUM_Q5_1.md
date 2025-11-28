# 🚀 Plan d'Intégration - Whisper Medium Q5_1

**Date**: 2025-11-27  
**Modèle**: `ggml-medium-q5_1.bin` (~587 MB)  
**Objectif**: Remplacer `ggml-small.bin` (465 MB) par Whisper Medium Q5_1

---

## 📥 ÉTAPE 1: TÉLÉCHARGEMENT DU MODÈLE

### Source

**Repository**: `ggerganov/whisper.cpp` sur Hugging Face  
**URL**: https://huggingface.co/ggerganov/whisper.cpp/tree/main  
**Fichier**: `ggml-medium-q5_1.bin` (~587 MB)

### Méthode 1: Téléchargement direct (Recommandé)

```bash
# Depuis votre PC (Windows PowerShell)
cd C:\androidProject\ChatAI-Android-beta\ChatAI-Android

# Télécharger via curl (si disponible) ou wget
curl -L -o ggml-medium-q5_1.bin "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_1.bin"

# OU via PowerShell Invoke-WebRequest
Invoke-WebRequest -Uri "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_1.bin" -OutFile "ggml-medium-q5_1.bin"
```

### Méthode 2: Téléchargement via adb (depuis device)

```bash
# Sur le device, via navigateur ou app de téléchargement
# Puis déplacer vers le bon répertoire
adb shell mv /sdcard/Download/ggml-medium-q5_1.bin /storage/emulated/0/ChatAI-Files/models/whisper/
```

### Méthode 3: Push depuis PC vers device

```bash
# Si vous avez téléchargé sur PC
adb push ggml-medium-q5_1.bin /storage/emulated/0/ChatAI-Files/models/whisper/
```

---

## 📁 ÉTAPE 2: PLACEMENT DU FICHIER

### Répertoire cible

```
/storage/emulated/0/ChatAI-Files/models/whisper/ggml-medium-q5_1.bin
```

### Vérification

```bash
# Vérifier que le fichier est bien présent
adb shell ls -lh /storage/emulated/0/ChatAI-Files/models/whisper/ggml-medium-q5_1.bin

# Vérifier la taille (devrait être ~587 MB)
adb shell du -h /storage/emulated/0/ChatAI-Files/models/whisper/ggml-medium-q5_1.bin
```

---

## ⚙️ ÉTAPE 3: MISE À JOUR DE LA CONFIGURATION

### Option A: Via l'interface webapp (Recommandé)

1. **Ouvrir ChatAI** sur le device
2. **Aller dans Configuration** > **Audio**
3. **Modifier** `preferredModel` de `ggml-small.bin` à `ggml-medium-q5_1.bin`
4. **Sauvegarder**

### Option B: Via fichier de configuration

```bash
# Lire la config actuelle
adb shell cat /storage/emulated/0/ChatAI-Files/config/ai_config.json

# Modifier le fichier (nécessite éditeur JSON)
# Changer "preferredModel": "ggml-small.bin" en "preferredModel": "ggml-medium-q5_1.bin"
```

### Option C: Via SharedPreferences (Android)

Le modèle est stocké dans `SharedPreferences` avec la clé `audio_model`.

**Code à modifier** (si nécessaire):
- `AiConfigManager.java` ligne 277: Valeur par défaut
- `AudioEngineConfig.kt` ligne 58: Valeur par défaut

**Note**: Ces valeurs par défaut ne sont utilisées que si la config n'existe pas. La config webapp/JSON a priorité.

---

## 🔧 ÉTAPE 4: MISE À JOUR DU CODE (Optionnel)

### Fichier: `WebAppInterface.java`

**Ligne 891**: Chemin hardcodé dans la commande Termux

**Avant**:
```java
"./whisper.cpp/build/bin/whisper-server -m /sdcard/ChatAI-Files/models/whisper/ggml-small.bin --port 11400 --host 127.0.0.1 -l fr -t 4"
```

**Après** (utiliser la config dynamique):
```java
String modelPath = "/sdcard/ChatAI-Files/models/whisper/" + config.preferredModel;
"./whisper.cpp/build/bin/whisper-server -m " + modelPath + " --port 11400 --host 127.0.0.1 -l fr -t 4"
```

**OU** utiliser directement depuis `AudioEngineConfig`:
```java
AudioEngineConfig audioConfig = AudioEngineConfig.fromContext(mContext);
String modelPath = "/sdcard/ChatAI-Files/models/whisper/" + audioConfig.preferredModel;
"./whisper-server -m " + modelPath + " --port 11400 --host 127.0.0.1 -l fr -t 4"
```

---

## 🧪 ÉTAPE 5: TEST

### 1. Redémarrer le serveur Whisper

```bash
# Arrêter le serveur actuel (si en cours)
adb shell am broadcast -a com.termux.STOP_SERVICE

# Redémarrer ChatAI pour que le nouveau modèle soit chargé
```

### 2. Vérifier les logs

```bash
# Vérifier que le nouveau modèle est chargé
adb logcat | Select-String -Pattern "whisper|audio_model|preferredModel"
```

### 3. Tester la transcription

- **Activer KITT** (hotword "Hey KITT")
- **Parler** une phrase en français
- **Vérifier** que la transcription fonctionne correctement
- **Comparer** la qualité avec l'ancien modèle (devrait être meilleure)

---

## 📊 VÉRIFICATION FINALE

### Checklist

- [ ] Modèle téléchargé (`ggml-medium-q5_1.bin` ~587 MB)
- [ ] Fichier placé dans `/storage/emulated/0/ChatAI-Files/models/whisper/`
- [ ] Configuration mise à jour (`preferredModel` = `ggml-medium-q5_1.bin`)
- [ ] Code mis à jour (si nécessaire)
- [ ] Serveur Whisper redémarré
- [ ] Test de transcription réussi
- [ ] Qualité améliorée confirmée

---

## ⚠️ NOTES IMPORTANTES

### RAM nécessaire

- **Whisper Medium Q5_1**: ~1.5 GB RAM
- **Vérifier** que le device a assez de RAM disponible
- Si le device a < 2 GB RAM, il pourrait y avoir des problèmes de performance

### Performance

- **Whisper Medium** est plus lent que Small (~2-3x)
- **Temps de transcription**: ~2-5 secondes pour 10 secondes d'audio (vs ~1-2 secondes pour Small)
- **Qualité**: +10-15% meilleure que Small

### Ancien modèle

- **Option 1**: Garder `ggml-small.bin` comme backup
- **Option 2**: Supprimer pour libérer 465 MB
  ```bash
  adb shell rm /storage/emulated/0/ChatAI-Files/models/whisper/ggml-small.bin
  ```

---

## 🔄 ROLLBACK (Si nécessaire)

Si vous voulez revenir à l'ancien modèle:

1. **Modifier la config**: `preferredModel` = `ggml-small.bin`
2. **Redémarrer** le serveur Whisper
3. **OU** restaurer `ggml-small.bin` depuis backup

---

## ✅ RÉSUMÉ

1. **Télécharger** `ggml-medium-q5_1.bin` (~587 MB)
2. **Placer** dans `/storage/emulated/0/ChatAI-Files/models/whisper/`
3. **Mettre à jour** `preferredModel` dans la config (webapp ou JSON)
4. **Redémarrer** le serveur Whisper
5. **Tester** la transcription

**Gain**: Qualité +10-15% vs Small, même taille (587 MB vs 465 MB) ! 🚀


