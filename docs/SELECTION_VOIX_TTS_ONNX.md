# 🎙️ SÉLECTION DE VOIX - TTS ONNX SpeechT5

**Date:** 2025-11-27  
**Objectif:** Expliquer comment la voix est sélectionnée dans le TTS ONNX et comment ajouter des options (homme/femme, etc.)

---

## 📋 SITUATION ACTUELLE

### ⚠️ **UNE SEULE VOIX PAR DÉFAUT**

**Actuellement:**
- Le TTS ONNX utilise **uniquement** `default_speaker_embeddings.json`
- **Aucune sélection de voix** - toujours la même voix
- Le select "Voix" dans l'UI (lignes 706-717) est pour **Android TTS legacy**, pas pour ONNX

**Code actuel:**
```kotlin
// OnnxTTSManager.kt ligne 262
val speakerEmbeddings = tokenizer.getDefaultSpeakerEmbeddings()
// Toujours le même embeddings, pas de sélection
```

---

## 🔍 COMMENT SPEECHT5 GÈRE LES VOIX

### **Speaker Embeddings (512 dimensions)**

SpeechT5 utilise des **vecteurs de 512 floats** (speaker embeddings) pour contrôler:
- **Timbre de la voix** (homme/femme, grave/aigu)
- **Style vocal** (neutre, expressif, etc.)
- **Caractéristiques acoustiques**

**Principe:**
- Chaque voix = un vecteur unique de 512 floats
- Le modèle SpeechT5 apprend à générer de l'audio selon ces embeddings
- Différents embeddings = différentes voix

---

## 🎯 OPTIONS POUR AJOUTER DES VOIX

### **Option 1: Fichiers d'embeddings multiples** ⭐ RECOMMANDÉ

**Structure:**
```
/storage/emulated/0/ChatAI-Files/models/tts/
  ├── vocab.json
  ├── speaker_embeddings_male.json      (voix homme)
  ├── speaker_embeddings_female.json    (voix femme)
  ├── speaker_embeddings_neutral.json   (voix neutre)
  └── default_speaker_embeddings.json   (voix par défaut)
```

**Avantages:**
- ✅ Simple à implémenter
- ✅ Qualité optimale (embeddings pré-générés)
- ✅ Pas de calcul en temps réel

**Implémentation:**
1. Générer les embeddings avec le modèle SpeechT5 PyTorch
2. Sauvegarder dans des fichiers JSON séparés
3. Modifier `SimpleTokenizer` pour charger selon la sélection
4. Ajouter un select "Voix" dans l'UI ONNX TTS

---

### **Option 2: Génération dynamique selon personnalité**

**Principe:**
- Mapper personnalité → embeddings
- KITT (masculin) → embeddings homme
- GLaDOS (féminin) → embeddings femme
- KARR (masculin agressif) → embeddings homme grave

**Avantages:**
- ✅ Automatique selon personnalité
- ✅ Pas besoin de sélection manuelle

**Inconvénients:**
- ⚠️ Nécessite génération des embeddings (script Python)
- ⚠️ Plus complexe à implémenter

---

### **Option 3: Modification des embeddings par défaut**

**Principe:**
- Prendre `default_speaker_embeddings.json`
- Appliquer des transformations mathématiques:
  - Voix plus grave: multiplier certaines dimensions
  - Voix plus aiguë: modifier d'autres dimensions

**Avantages:**
- ✅ Pas besoin de fichiers supplémentaires
- ✅ Rapide à implémenter

**Inconvénients:**
- ⚠️ Qualité inférieure vs embeddings pré-générés
- ⚠️ Résultats imprévisibles

---

## 🚀 IMPLÉMENTATION RECOMMANDÉE

### **Phase 1: Support fichiers multiples** ⭐

**1. Modifier `SimpleTokenizer.kt`:**

```kotlin
// Ajouter méthode pour charger embeddings selon nom
fun loadSpeakerEmbeddings(voiceName: String): FloatArray? {
    val embeddingsPath = when (voiceName) {
        "male", "homme" -> "/storage/emulated/0/ChatAI-Files/models/tts/speaker_embeddings_male.json"
        "female", "femme" -> "/storage/emulated/0/ChatAI-Files/models/tts/speaker_embeddings_female.json"
        "neutral", "neutre" -> "/storage/emulated/0/ChatAI-Files/models/tts/speaker_embeddings_neutral.json"
        else -> SPEAKER_EMBEDDINGS_PATH // Par défaut
    }
    
    val file = File(embeddingsPath)
    if (file.exists()) {
        // Charger et retourner
    }
    return defaultSpeakerEmbeddings
}
```

**2. Modifier `OnnxTTSManager.kt`:**

```kotlin
// Ajouter paramètre voiceName dans preprocessText()
private fun preprocessText(text: String, voiceName: String = "default"): Map<String, OnnxTensor>? {
    // ...
    val speakerEmbeddings = tokenizer.loadSpeakerEmbeddings(voiceName)
    // ...
}
```

**3. Ajouter select "Voix" dans l'UI ONNX TTS:**

```html
<label style="grid-column: 1 / -1;">
    <span>Voix ONNX</span>
    <select id="configOnnxTtsVoice">
        <option value="default">Par défaut</option>
        <option value="male">Homme</option>
        <option value="female">Femme</option>
        <option value="neutral">Neutre</option>
    </select>
</label>
```

---

## 📦 GÉNÉRATION DES EMBEDDINGS

### **Script Python pour générer différentes voix**

**Fichier:** `E:\ChatAI-Models\tts\generate_speaker_embeddings.py`

```python
from transformers import SpeechT5Processor, SpeechT5ForTextToSpeech
import torch
import json

model = SpeechT5ForTextToSpeech.from_pretrained("microsoft/speecht5_tts")
processor = SpeechT5Processor.from_pretrained("microsoft/speecht5_tts")

# Générer embeddings pour différentes voix
# (nécessite échantillons audio de référence ou génération conditionnelle)

# Exemple: Générer embeddings "male"
male_embeddings = model.speaker_embeddings(torch.tensor([0]))  # ID 0 = voix par défaut
# Modifier selon besoins...

# Sauvegarder
with open("speaker_embeddings_male.json", "w") as f:
    json.dump({"embeddings": male_embeddings.squeeze().tolist()}, f)
```

**Note:** La génération d'embeddings de qualité nécessite:
- Soit des échantillons audio de référence
- Soit une fine-tuning du modèle SpeechT5
- Soit utiliser des embeddings pré-entraînés du Hub Hugging Face

---

## 🎨 OPTIONS VOIX DISPONIBLES

### **Voix pré-configurées recommandées:**

| Voix | Description | Embeddings |
|------|-------------|------------|
| **default** | Voix par défaut SpeechT5 | `default_speaker_embeddings.json` |
| **male** | Voix masculine | `speaker_embeddings_male.json` |
| **female** | Voix féminine | `speaker_embeddings_female.json` |
| **neutral** | Voix neutre | `speaker_embeddings_neutral.json` |

### **Mapping personnalité → voix (optionnel):**

| Personnalité | Voix recommandée | Raison |
|--------------|-------------------|--------|
| **KITT** | `male` | Masculine, amicale |
| **GLaDOS** | `female` | Féminine, sarcastique |
| **KARR** | `male` (grave) | Masculine, agressive |
| **Sarah** | `female` | Féminine, douce |
| **Jarvis** | `male` (neutre) | Masculine, professionnelle |

---

## ⚠️ LIMITATIONS ACTUELLES

### **Ce qui manque:**

1. ❌ **Aucune sélection de voix** dans l'UI ONNX TTS
2. ❌ **Un seul fichier d'embeddings** (`default_speaker_embeddings.json`)
3. ❌ **Pas de mapping personnalité → voix** pour ONNX
4. ❌ **Le select "Voix" legacy** ne fonctionne que pour Android TTS

### **Ce qui fonctionne:**

1. ✅ **Android TTS legacy** a un système de sélection de voix complet
2. ✅ **KittTTSManager** gère les voix selon personnalité (pour Android TTS)
3. ✅ **Structure prête** pour ajouter plusieurs embeddings

---

## 🔧 PROCHAINES ÉTAPES

### **Pour ajouter le support de différentes voix:**

1. ✅ **Générer les fichiers d'embeddings** (homme, femme, neutre)
2. ✅ **Modifier `SimpleTokenizer.kt`** pour charger selon sélection
3. ✅ **Modifier `OnnxTTSManager.kt`** pour accepter paramètre `voiceName`
4. ✅ **Ajouter select "Voix ONNX"** dans l'UI (onglet TTS)
5. ✅ **Sauvegarder sélection** dans configuration
6. ✅ **Mapper personnalité → voix** (optionnel)

---

## 📊 COMPARAISON AVEC ANDROID TTS

| Fonctionnalité | Android TTS | ONNX TTS (actuel) | ONNX TTS (proposé) |
|----------------|-------------|-------------------|-------------------|
| **Sélection voix** | ✅ Oui (via select) | ❌ Non | ✅ Oui (via select) |
| **Voix homme/femme** | ✅ Oui | ❌ Non | ✅ Oui |
| **Mapping personnalité** | ✅ Oui | ❌ Non | ✅ Oui (optionnel) |
| **Qualité** | ⚠️ Variable | ✅ Excellente | ✅ Excellente |
| **Offline** | ✅ Oui | ✅ Oui | ✅ Oui |

---

## 🎯 RECOMMANDATION

**Implémenter Option 1 (fichiers multiples):**

1. **Court terme:** Ajouter 2-3 voix de base (homme, femme, neutre)
2. **Moyen terme:** Générer embeddings pour chaque personnalité
3. **Long terme:** Permettre upload d'embeddings personnalisés

**Priorité:** ⭐⭐⭐ **HAUTE** - Améliore significativement l'expérience utilisateur

---

**Document créé le:** 2025-11-27  
**Dernière mise à jour:** 2025-11-27


