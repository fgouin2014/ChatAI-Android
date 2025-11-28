# 🔒 Privacy & Stockage des Données

**Date**: 2025-11-27  
**Question**: "En attendant présentement je store du data dans un cloud?"

---

## 📊 RÉSUMÉ RAPIDE

### ❌ NON - Les conversations ne sont PAS stockées dans le cloud

**Stockage actuel**:
- ✅ **100% LOCAL** : Toutes les conversations sont stockées sur votre device Android (Room Database SQLite)
- ✅ **Aucun stockage cloud** : Ollama Cloud ne stocke pas vos conversations
- ⚠️ **Transit cloud** : Les données sont envoyées à Ollama Cloud pour traitement, mais pas stockées

---

## 🔍 DÉTAILS TECHNIQUES

### 1. Stockage LOCAL (Sur Device Android) ✅

**Base de données**: Room Database (`ChatAIDatabase.kt`)

**Fichier**: SQLite sur device à `/data/data/com.chatai/databases/`

**Données stockées**:
- ✅ **Toutes les conversations** (`ConversationEntity`)
  - Message utilisateur (`userMessage`)
  - Réponse IA (`aiResponse`)
  - Thinking trace (`thinkingTrace`) - si activé
  - Personnalité (KITT/GLaDOS)
  - API utilisée (Cloud/Local/Device)
  - Timestamp
  - Session ID
  - Embeddings (pour RAG)
  - Tags, feedback utilisateur, etc.

**Accès**:
- Uniquement sur votre device Android
- Pas de synchronisation cloud
- Pas de backup automatique cloud
- **Votre contrôle total**

---

### 2. Transit vers Ollama Cloud (Mode Cloud) ⚠️

**Ce qui est ENVOYÉ** (pour traitement seulement):
- Messages de conversation (historique pour contexte)
- Message actuel de l'utilisateur
- System prompt (personnalité KITT/GLaDOS)
- Paramètres de requête (thinking, temperature, etc.)

**URL**: `https://ollama.com/api/chat`

**Format de requête**:
```json
{
  "model": "gpt-oss:120b",
  "messages": [
    { "role": "system", "content": "..." },
    { "role": "user", "content": "..." },
    { "role": "assistant", "content": "..." },  // Historique
    { "role": "user", "content": "Message actuel" }
  ],
  "stream": false,
  "think": true
}
```

**Ce qui est REÇU** (réponse):
- Réponse générée par l'IA
- Thinking trace (optionnel)
- Citations web (si web search activé)

**Stockage cloud**:
- ❌ **Ollama Cloud ne stocke PAS vos conversations**
- ✅ Les données sont traitées et la réponse est renvoyée
- ✅ Aucune persistance côté serveur Ollama

**Privacy**:
- ⚠️ Données transitent via internet (HTTPS)
- ⚠️ Ollama Cloud voit les messages envoyés (mais ne les stocke pas)
- ⚠️ Pas de contrôle sur leurs logs serveur (mais pas de stockage persistant)

---

### 3. Transit vers Hugging Face (Embeddings RAG + LLM fallback) ⚠️

#### 3.1 Embeddings Hugging Face (RAG)

**Quand utilisé**:
- Mode Cloud activé (Ollama Cloud)
- RAG activé
- Ollama Cloud ne supporte pas `/api/embeddings` → Fallback Hugging Face

**Ce qui est ENVOYÉ** (pour génération embeddings):
- **Texte à transformer en embedding** (uniquement le texte, pas les conversations complètes)
- Format: `{"inputs": "texte à embedder"}`

**URL**: `https://router.huggingface.co/hf-inference/models/sentence-transformers/all-MiniLM-L6-v2`

**Ce qui est REÇU** (réponse):
- Vecteur d'embeddings (array de floats) : `[[0.1, 0.2, ...]]`
- **Stocké LOCALEMENT** dans Room DB (`ConversationEntity.embeddingsJson`)

**Stockage cloud**:
- ❌ **Hugging Face ne stocke PAS les embeddings générés**
- ⚠️ **Hugging Face voit le texte envoyé** (pour générer l'embedding)
- ✅ Les embeddings générés sont stockés **LOCALEMENT** seulement

**Privacy**:
- ⚠️ Texte transite via internet (HTTPS) vers Hugging Face
- ⚠️ Hugging Face peut voir le texte envoyé (logs serveur possibles)
- ✅ Embeddings stockés 100% localement après génération

#### 3.2 LLM Hugging Face (Fallback)

**Quand utilisé**:
- Fallback dans `KittAIService` si autres APIs échouent
- Modèle: `gpt2` (peu utilisé actuellement)

**Ce qui est ENVOYÉ**:
- **Message utilisateur** uniquement
- Format: `{"inputs": "message", "parameters": {...}}`

**URL**: `https://router.huggingface.co/hf-inference/models/gpt2`

**Stockage cloud**:
- ❌ **Hugging Face ne stocke PAS les messages**
- ⚠️ **Hugging Face voit le message** pour génération
- ✅ Réponse stockée **LOCALEMENT** seulement

---

## 📋 COMPARAISON DES MODES

| Mode | Stockage Conversations | Transit Cloud | Hugging Face | Privacy |
|------|----------------------|---------------|--------------|---------|
| **☁️ Cloud** (Ollama Cloud) | ✅ **100% LOCAL** (Room DB) | ⚠️ **OUI** (Ollama Cloud) | ⚠️ **OUI** (Embeddings RAG) | ⚠️ Transit via internet |
| **🖥️ Local PC** (Ollama PC) | ✅ **100% LOCAL** (Room DB) | ❌ **NON** (réseau local) | ❌ **NON** (Ollama local fait embeddings) | ✅ **MAXIMALE** (local seulement) |
| **📱 Local Device** | ✅ **100% LOCAL** (Room DB) | ❌ **NON** (device seulement) | ❌ **NON** (embeddings ONNX locaux) | ✅ **MAXIMALE** (offline complet) |

---

## 🔒 PRIVACY DÉTAILLÉE PAR MODE

### Mode ☁️ Cloud (Ollama Cloud)

**Ce qui se passe**:
1. Vous tapez/parlez un message
2. Message + historique sont **envoyés** à `https://ollama.com/api/chat`
3. Ollama Cloud **traite** la requête (génère réponse)
4. Réponse est **renvoyée** à votre device
5. Conversation est **sauvegardée LOCALEMENT** dans Room DB
6. Ollama Cloud **ne stocke pas** la conversation

**Risques privacy**:
- ⚠️ Messages transitent via internet (HTTPS encrypté) vers Ollama Cloud
- ⚠️ Ollama Cloud peut voir les messages (logs temporaires)
- ⚠️ Pas de garantie absolue sur leurs logs serveur
- ⚠️ **Si RAG activé**: Texte aussi envoyé à Hugging Face pour embeddings

**Avantages**:
- ✅ Conversations stockées localement seulement
- ✅ Pas de persistance cloud
- ✅ Vous contrôlez vos données

---

### Mode 🖥️ Local PC (Ollama sur PC)

**Ce qui se passe**:
1. Vous tapez/parlez un message
2. Message + historique sont **envoyés** à votre PC (WiFi local)
3. Ollama sur PC **traite** la requête
4. Réponse est **renvoyée** à votre device
5. Conversation est **sauvegardée LOCALEMENT** dans Room DB
6. **AUCUN transit internet**

**Risques privacy**:
- ✅ **AUCUN** - Tout reste local (réseau local seulement)
- ✅ Pas de transit internet
- ✅ Privacy maximale

---

### Mode 📱 Local Device (Modèles sur Device)

**Ce qui se passe**:
1. Vous tapez/parlez un message
2. Message est **traité localement** sur device (modèles ONNX/GGUF)
3. Réponse est **générée localement**
4. Conversation est **sauvegardée LOCALEMENT** dans Room DB
5. **AUCUNE connexion externe**

**Risques privacy**:
- ✅ **AUCUN** - Tout reste sur device
- ✅ Pas de connexion réseau
- ✅ Privacy absolue

---

## 💾 SAUVEGARDE DES DONNÉES

### Où sont stockées les conversations ?

**Device Android**:
- 📁 **Base de données**: `/data/data/com.chatai/databases/chatai_database`
- 📁 **Format**: SQLite (Room Database)
- 📁 **Accessible**: Uniquement root ou backup Android

**Export possible**:
- ✅ Export JSON depuis l'app (`ConversationHistoryActivity`)
- ✅ Export logcat
- ✅ Backup Android complet

**Synchronisation cloud**:
- ❌ **AUCUNE** synchronisation automatique
- ❌ Pas de backup automatique cloud
- ✅ Vous devez faire backup manuel si désiré

---

## 🔐 RECOMMANDATIONS PRIVACY

### Si Privacy est CRITIQUE :

1. **Utiliser Local PC ou Local Device uniquement**
   - Pas de transit cloud
   - Données restent locales

2. **Désactiver Cloud mode**
   - Dans onglet Général → Mode "Local" (Device)
   - Ou ne pas configurer de clé API Cloud

3. **Backup local régulier**
   - Export JSON des conversations
   - Sauvegarder sur PC (pas cloud)

### Si Privacy n'est pas critique :

1. **Cloud mode OK**
   - Conversations stockées localement seulement
   - Transit via HTTPS encrypté
   - Ollama Cloud ne stocke pas

2. **Backup optionnel**
   - Export JSON si besoin
   - Pas nécessaire (déjà sur device)

---

## ✅ CONCLUSION

**Réponse à vos questions** :

> "En attendant présentement je store du data dans un cloud?"  
> "et hugging face?"

**NON** ❌ - Aucun stockage dans le cloud

### Stockage (100% LOCAL)
- ✅ **Toutes vos conversations** : Room Database SQLite sur device Android
- ✅ **Tous les embeddings** : Stockés localement après génération (même si générés via Hugging Face)
- ✅ **Aucun stockage cloud** : Ni Ollama, ni Hugging Face ne stockent vos données

### Transit Cloud (Traitement seulement)

**Ollama Cloud** (si mode Cloud activé):
- ⚠️ Messages envoyés pour génération réponse
- ✅ Réponse renvoyée et stockée localement
- ❌ Ollama Cloud ne stocke pas

**Hugging Face** (si RAG activé avec Cloud):
- ⚠️ Texte envoyé pour génération embeddings
- ✅ Embeddings renvoyés et stockés localement
- ❌ Hugging Face ne stocke pas les embeddings
- ⚠️ Hugging Face peut voir le texte envoyé (logs serveur possibles)

### Résumé

| Type de Donnée | Stockage | Transit Cloud |
|----------------|----------|---------------|
| **Conversations** | ✅ 100% LOCAL | ⚠️ OUI (Ollama Cloud si activé) |
| **Embeddings** | ✅ 100% LOCAL | ⚠️ OUI (Hugging Face si RAG Cloud) |
| **Messages texte** | ✅ 100% LOCAL | ⚠️ OUI (Ollama + Hugging Face) |

**Stockage = 100% LOCAL**  
**Transit = OUI si Cloud/RAG activé (traitement seulement)**  
**Privacy = MAXIMALE si Local PC/Device (pas de transit)**

---

**Document créé**: 2025-11-27  
**Dernière mise à jour**: 2025-11-27  
**Statut**: ✅ Clarification complète du stockage et privacy

