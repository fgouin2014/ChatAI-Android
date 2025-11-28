# 🔊 SUPPORT SÉLECTION VOIX ONNX TTS

**Date:** 2025-11-27  
**Statut:** ✅ **IMPLÉMENTÉ** - Support complet de sélection de voix (homme/femme/neutre)

---

## ⚠️ PROBLÈME INITIAL

Le TTS ONNX n'avait **AUCUNE** option de sélection de voix. Il utilisait uniquement une voix par défaut, ce qui était très limité comparé aux attentes d'un système "avancé".

---

## ✅ SOLUTION IMPLÉMENTÉE

### **1. SimpleTokenizer.kt - Support multi-voix**

**Modifications:**
- ✅ Ajout enum `VoiceType` (DEFAULT, MALE, FEMALE)
- ✅ Chargement de **3 fichiers d'embeddings**:
  - `default_speaker_embeddings.json` (neutre)
  - `male_speaker_embeddings.json` (masculin)
  - `female_speaker_embeddings.json` (féminin)
- ✅ Méthode `getSpeakerEmbeddings(voiceType)` pour sélectionner la voix
- ✅ Fallback automatique si fichiers manquants (utilise défaut)

**Fichiers requis sur device:**
```
/storage/emulated/0/ChatAI-Files/models/tts/
  ├── default_speaker_embeddings.json  (requis)
  ├── male_speaker_embeddings.json    (optionnel, fallback défaut)
  └── female_speaker_embeddings.json  (optionnel, fallback défaut)
```

---

### **2. OnnxTTSManager.kt - Paramètre voix**

**Modifications:**
- ✅ `preprocessText()` accepte maintenant `voiceType: VoiceType`
- ✅ `synthesizeToWav(text, voice)` accepte paramètre `voice` (String)
- ✅ Conversion automatique: `"male"` → `VoiceType.MALE`, etc.

**Signature:**
```kotlin
fun synthesizeToWav(text: String, voice: String? = null): ByteArray?
```

**Valeurs acceptées:**
- `"male"`, `"homme"`, `"masculin"` → Voix masculine
- `"female"`, `"femme"`, `"féminin"` → Voix féminine
- `"default"`, `null` → Voix par défaut (neutre)

---

### **3. TTSServer.java - API HTTP avec voix**

**Modifications:**
- ✅ Endpoint `/synthesize` accepte paramètre `voice` dans JSON
- ✅ `synthesizeAndSend()` transmet le paramètre à `OnnxTTSManager`

**Format requête:**
```json
{
  "text": "Bonjour, ceci est un test",
  "voice": "male"  // "male", "female", "default" ou null
}
```

---

### **4. UI Webapp - Select "Voix ONNX"**

**Modifications dans `index.html`:**
- ✅ Ajout select `configOnnxTtsVoice` avec 3 options:
  - `"default"` → Défaut (neutre)
  - `"male"` → Masculin (homme)
  - `"female"` → Féminin (femme)
- ✅ Fonction `testTtsSynthesis()` envoie le paramètre `voice` dans la requête

**Emplacement:** Onglet "TTS" → Section "Configuration ONNX TTS" → "Voix ONNX"

---

### **5. Script Python - Génération embeddings**

**Nouveau script:** `ChatAI-Android/scripts/generate_all_speaker_embeddings.py`

**Fonctionnalités:**
- ✅ Génère **3 fichiers d'embeddings** en une seule exécution:
  - `default_speaker_embeddings.json` (Speaker ID 0)
  - `male_speaker_embeddings.json` (Speaker ID 1)
  - `female_speaker_embeddings.json` (Speaker ID 2)
- ✅ Utilise le modèle SpeechT5 officiel (`microsoft/speecht5_tts`)
- ✅ Affiche les commandes ADB pour transférer vers device

**Utilisation:**
```bash
cd ChatAI-Android/scripts
python generate_all_speaker_embeddings.py
```

**Ou avec chemin personnalisé:**
```bash
python generate_all_speaker_embeddings.py "E:/ChatAI-Models/tts"
```

---

## 🚀 UTILISATION

### **Étape 1: Générer les embeddings**

```bash
cd ChatAI-Android/scripts
python generate_all_speaker_embeddings.py
```

**Résultat:** 3 fichiers JSON créés dans `E:/ChatAI-Models/tts/`

### **Étape 2: Transférer vers device**

```bash
adb push "E:/ChatAI-Models/tts/default_speaker_embeddings.json" /storage/emulated/0/ChatAI-Files/models/tts/default_speaker_embeddings.json
adb push "E:/ChatAI-Models/tts/male_speaker_embeddings.json" /storage/emulated/0/ChatAI-Files/models/tts/male_speaker_embeddings.json
adb push "E:/ChatAI-Models/tts/female_speaker_embeddings.json" /storage/emulated/0/ChatAI-Files/models/tts/female_speaker_embeddings.json
```

### **Étape 3: Utiliser dans l'UI**

1. Ouvrir l'onglet **"Configuration"** → **"TTS"**
2. Sélectionner **"Voix ONNX"** (Défaut / Masculin / Féminin)
3. Cliquer **"🔊 Tester TTS"** pour entendre la différence

---

## 📊 ARCHITECTURE

```
┌─────────────────────────────────────────────────────────┐
│  UI Webapp (index.html)                                  │
│  └─ Select "Voix ONNX" → "male" / "female" / "default" │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP POST /synthesize
                     │ { "text": "...", "voice": "male" }
                     ▼
┌─────────────────────────────────────────────────────────┐
│  TTSServer.java                                          │
│  └─ Parse JSON → extract "voice"                        │
│  └─ Call: onnxTTSManager.synthesizeToWav(text, voice)   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  OnnxTTSManager.kt                                      │
│  └─ Convert "male" → VoiceType.MALE                     │
│  └─ Call: preprocessText(text, VoiceType.MALE)         │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  SimpleTokenizer.kt                                      │
│  └─ getSpeakerEmbeddings(VoiceType.MALE)                │
│  └─ Load: male_speaker_embeddings.json                  │
│  └─ Return: FloatArray[512]                             │
└─────────────────────────────────────────────────────────┘
```

---

## ⚠️ NOTES IMPORTANTES

### **Fallback automatique**

Si un fichier d'embeddings est manquant:
- ✅ `male_speaker_embeddings.json` manquant → Utilise `default_speaker_embeddings.json`
- ✅ `female_speaker_embeddings.json` manquant → Utilise `default_speaker_embeddings.json`
- ⚠️ `default_speaker_embeddings.json` manquant → Génère embeddings de zéros (voix dégradée)

### **Compatibilité**

- ✅ **Rétrocompatible:** Si `voice` n'est pas fourni, utilise `DEFAULT`
- ✅ **Flexible:** Accepte plusieurs formats (`"male"`, `"homme"`, `"masculin"`)
- ✅ **Robuste:** Fallback automatique si fichiers manquants

---

## 📋 FICHIERS MODIFIÉS

1. ✅ `SimpleTokenizer.kt` - Support multi-voix
2. ✅ `OnnxTTSManager.kt` - Paramètre voix
3. ✅ `TTSServer.java` - API avec voix
4. ✅ `index.html` - UI select "Voix ONNX"
5. ✅ `generate_all_speaker_embeddings.py` - Script génération (NOUVEAU)

---

## 🎯 PROCHAINES ÉTAPES

- [ ] Tester les 3 voix et vérifier qualité audio
- [ ] Optionnel: Ajouter plus de voix (personnalités: KITT, GLaDOS, etc.)
- [ ] Optionnel: Générer embeddings personnalisés depuis audio samples

---

**Document créé le:** 2025-11-27  
**Dernière mise à jour:** 2025-11-27


