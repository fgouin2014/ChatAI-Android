# ChatAI Android - Intelligence Conversationnelle

Application Android avancée combinant chat IA, émulation de jeux et assistant vocal KITT/GLaDOS.

## 🎯 Fonctionnalités Principales

### 🤖 Intelligence Artificielle
- **KittAIService v2.6** - Assistant vocal avec personnalités KITT et GLaDOS
- **Support multi-API** : OpenAI GPT-4o-mini, Anthropic Claude 3.5, Ollama Cloud, serveur local Ollama
- **Mémoire persistante** : Room Database sauvegarde toutes les conversations
- **Historique complet** : Interface pour visualiser et rechercher les conversations passées
- **Apprentissage contextuel** : Utilise l'historique pour améliorer les réponses

### 🎮 Émulation de Jeux (GameLibrary)
- Interface web pour gérer et lancer des jeux rétro
- Support multi-consoles via EmulatorJS
- Serveur HTTP intégré

### 🎙️ Assistant Vocal KITT
- Reconnaissance vocale en temps réel
- Text-to-Speech (TTS) avec paramètres ajustables (vitesse, tonalité)
- Personnalité inspirée de K 2000
- Configuration complète via interface dédiée

## 🏗️ Architecture

```
ChatAI-Android/
├── app/
│   ├── src/main/java/com/chatai/
│   │   ├── activities/
│   │   │   ├── MainActivity.kt
│   │   │   ├── AIConfigurationActivity.kt
│   │   │   ├── ConversationHistoryActivity.kt  # 📜 Nouveau!
│   │   │   └── KittActivity.kt
│   │   ├── services/
│   │   │   └── KittAIService.kt  # 🧠 Cœur de l'IA
│   │   ├── database/
│   │   │   ├── ChatAIDatabase.kt
│   │   │   ├── ConversationEntity.kt
│   │   │   └── ConversationDao.kt
│   │   └── fragments/
│   │       └── KittFragment.kt
│   └── src/main/res/
│       └── layout/
│           ├── activity_conversation_history.xml  # 📜 Nouveau!
│           └── item_conversation.xml  # 📜 Nouveau!
└── README.md
```

## 🚀 Installation

### Prérequis
- Android SDK 26+ (Android 8.0 Oreo)
- Gradle 8.4+
- Kotlin 2.0.21

### Configuration

1. **Cloner le repo**
```bash
git clone https://github.com/fgouin2014/ChatAI-Android.git
cd ChatAI-Android
```

2. **Configurer les APIs (optionnel)**
Dans l'app, aller dans **Configuration IA** et configurer:
- OpenAI API key
- Anthropic API key
- Ollama Cloud API key
- Serveur Ollama local (URL + modèle)

3. **Compiler et installer**
```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 🧠 KittAIService - Intelligence Conversationnelle

### Ordre de Fallback API
```
1. OpenAI GPT-4o-mini (si clé configurée)
2. Anthropic Claude 3.5 Sonnet (si clé configurée)
3. Ollama Cloud (si clé configurée) ☁️
4. Ollama PC Local (si serveur configuré) 🏠
5. Hugging Face (si clé configurée)
6. Fallback local (réponses KITT/GLaDOS basiques)
```

### Mémoire Persistante
- Toutes les conversations sauvegardées dans Room Database
- Chargement automatique des 10 dernières conversations au démarrage
- Interface dédiée pour visualiser l'historique complet
- Statistiques : temps de réponse, API utilisée, nombre de conversations

### Personnalités
- **KITT** : Assistant sophistiqué et professionnel (K 2000)
- **GLaDOS** : IA sarcastique et passive-agressive (Portal)

## 📊 Base de Données

### Schema ConversationEntity
```kotlin
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userMessage: String,          // Question de l'utilisateur
    val aiResponse: String?,          // Réponse de l'IA
    val personality: String,          // "KITT" ou "GLaDOS"
    val apiUsed: String,              // "openai", "ollama", etc.
    val responseTimeMs: Long,         // Temps de réponse
    val platform: String,             // "vocal" ou "web"
    val sessionId: String,            // Groupe les conversations
    val timestamp: Long               // Date/heure
)
```

## 🎛️ Configuration TTS

Paramètres ajustables dans **Configuration IA** :
- **Vitesse** : 0.5x à 2.0x (recommandé : 1.0-1.2x)
- **Tonalité** : 0.5x à 1.5x (recommandé : 0.7-0.9x)
- Bouton de test pour preview en temps réel

## 🔧 Serveur Ollama Local

### Configuration sur PC
```bash
# Installer Ollama
# https://ollama.com/download

# Télécharger un modèle
ollama pull gemma3:1b
ollama pull gemma3:270m

# Démarrer le serveur (accessible réseau)
set OLLAMA_HOST=0.0.0.0:11434
ollama serve
```

### Dans l'App
Configuration IA → Serveur Local :
- **URL** : `http://[IP_PC]:11434/v1/chat/completions`
- **Modèle** : `gemma3:1b` ou autre

## 📜 Historique des Conversations

### Nouvelle Interface (v2.6)
- Liste complète des conversations
- Affichage question/réponse avec métadonnées
- Statistiques en temps réel
- Effacement sélectif ou complet
- Accessoble depuis **Configuration IA** → **Voir l'historique**

## 🛣️ Roadmap

### Phase 1 ✅ (Actuelle)
- [x] Room Database avec historique complet
- [x] Interface pour visualiser l'historique
- [x] Support Ollama Cloud
- [x] Configuration TTS ajustable
- [x] Personnalités KITT et GLaDOS

### Phase 2 🚧 (En développement)
- [ ] Recherche sémantique dans l'historique
- [ ] Détection automatique des corrections utilisateur
- [ ] Apprentissage des préférences
- [ ] Sélecteur de personnalité dans l'UI

### Phase 3 🔮 (Futur)
- [ ] RAG (Retrieval Augmented Generation)
- [ ] Embeddings pour recherche sémantique avancée
- [ ] Meta-learning (auto-amélioration)
- [ ] Mode offline complet (gemma3:270m on-device via llama.cpp)
- [ ] Function calling (contrôle du téléphone)

## 📝 Licence

MIT License

## 👤 Auteur

François Gouin - [@fgouin2014](https://github.com/fgouin2014)

## 🙏 Remerciements

- RetroArch pour les overlays
- Ollama pour le serveur d'inférence local
- Room Database pour la persistance
- OpenAI, Anthropic pour les APIs cloud
