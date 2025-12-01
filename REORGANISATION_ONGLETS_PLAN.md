# 📋 Plan de Réorganisation des Onglets de Configuration

**Date**: 2025-12-01  
**Objectif**: Réorganiser les onglets de configuration pour une logique plus claire et intuitive

---

## 🎯 PROBLÈMES IDENTIFIÉS

### **Incohérences actuelles :**
1. **Onglet Général** contient "Modèle Cloud par défaut" → Devrait être dans sélection modèle générale
2. **Onglet Cloud** contient sélection de modèle → Devrait être dans Général
3. **Confusion** entre "mode" (Cloud/Local) et "provider" (Hugging Face, Ollama, etc.)
4. **RAG** : Où est-il configuré ? À vérifier

---

## 📐 STRUCTURE PROPOSÉE

### **1. ⚙️ Général**
**Contenu :**
- **Mode actif** : Cloud / Local (Device)
- **Sélection de modèle** :
  - Si mode Cloud → Liste des modèles cloud disponibles (Hugging Face, Ollama, etc.)
  - Si mode Local → Liste des modèles locaux sur device
- **RAG** :
  - Activation RAG
  - Modèle d'embedding (ONNX, Hugging Face, Ollama)
  - Paramètres RAG (topK, threshold, etc.)
- **Personnalité** : (si pas déjà ailleurs)
- **Langue** : (si pas déjà ailleurs)

### **2. ☁️ Cloud**
**Contenu :**
- **Comptes Cloud** (sections séparées) :
  - **Hugging Face** :
    - API Key
    - Activation/désactivation
    - Modèle LLM (si différent de général)
    - Modèle Embedding (si différent de général)
  - **Ollama Cloud** :
    - API Key
    - Activation/désactivation
    - Modèle (si différent de général)
  - **OpenAI** :
    - API Key
    - Activation/désactivation
    - Modèle (si différent de général)
  - **Autres providers** (Groq, Perplexity, etc.)
- **Actions** :
  - Tester connexions
  - Lister modèles disponibles

### **3. 💻 Local**
**Contenu :**
- **Modèles locaux sur device** :
  - Liste des modèles GGUF disponibles
  - Sélection du modèle actif
  - Scanner/réactualiser la liste
- **RAG local** :
  - Modèle ONNX embedding (si disponible)
  - Configuration RAG locale

### **4. 🎤 AV** (Audio/Vision)
**Contenu :**
- Vision (analyse d'images)
- STT (Speech-to-Text)
- Configuration audio

### **5. 🔊 Hotword**
**Contenu :**
- Configuration hotword (déjà OK)

### **6. 🗣️ TTS**
**Contenu :**
- Configuration TTS (déjà OK)

### **7. 🔧 Avancé**
**Contenu :**
- Paramètres avancés (déjà OK)

---

## 🔄 CHANGEMENTS À APPLIQUER

### **Étape 1 : Onglet Général**
- ✅ Garder "Mode actif" (Cloud/Local)
- ✅ Ajouter "Sélection de modèle" dynamique :
  - Si Cloud → Afficher modèles cloud (depuis providers configurés)
  - Si Local → Afficher modèles locaux
- ✅ Ajouter section RAG
- ❌ Supprimer "Modèle Cloud par défaut" (remplacé par sélection dynamique)

### **Étape 2 : Onglet Cloud**
- ✅ Garder Provider selector
- ✅ Réorganiser en sections par provider :
  - Hugging Face (API Key + activation)
  - Ollama Cloud (API Key + activation)
  - OpenAI (API Key + activation)
  - Autres
- ✅ Garder actions (Tester, Lister modèles)
- ❌ Supprimer sélection de modèle (déplacée vers Général)

### **Étape 3 : Onglet Local**
- ✅ Garder modèles locaux device
- ✅ Ajouter section RAG local (ONNX)
- ✅ Vérifier qu'il n'y a plus de référence à Ollama PC

---

## 📝 NOTES

- **Sélection de modèle** : Doit être dynamique selon le mode (Cloud/Local)
- **API Keys** : Rester dans onglet Cloud (comptes)
- **RAG** : Peut être dans Général (configuration générale) OU Local (si spécifique au device)
- **Cohérence** : Général = Configuration générale, Cloud = Comptes, Local = Device

